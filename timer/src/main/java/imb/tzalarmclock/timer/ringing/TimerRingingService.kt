package imb.tzalarmclock.timer.ringing

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.format.TimerCountdown
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.reset
import imb.tzalarmclock.timer.TimerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns an actively-ringing timer's playback, vibration, volume escalation,
 * and ongoing notification — mirrors
 * `imb.tzalarmclock.alarm.ringing.RingingService`'s role for alarms, grown
 * from Stage 15's interim version into Stage 17's full one, with Stage 18's
 * `AppSettings.defaultTimerRingtoneUri` now wired into playback.
 *
 * Plays [AppSettings.defaultTimerRingtoneUri], falling back to the system
 * default alarm sound when it's unset or fails to resolve (e.g. the app
 * that owned it was uninstalled) — same fallback shape as `RingingService`.
 * Volume, escalation, and vibrate-if-capable all reuse the existing
 * alarm-level [AppSettings] fields as-is — the spec adds only a timer
 * *ringtone* setting, nothing else timer-specific (see the dev plan's
 * assumption log).
 *
 * A foreground service rather than logic in `TimerReceiver` directly, for
 * the same reason as the alarm equivalent: playback has to keep running well
 * past the few seconds a `BroadcastReceiver` is allowed to run for.
 */
class TimerRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var escalationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timerId = intent?.getLongExtra(EXTRA_TIMER_ID, Timer.NO_ID) ?: Timer.NO_ID
        if (timerId == Timer.NO_ID) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_DISMISS -> dismiss(timerId)
            else -> ring(timerId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        activeTimerId = Timer.NO_ID
        scope.cancel()
        super.onDestroy()
    }

    private fun ring(timerId: Long) {
        // Only one ring session at a time, same reasoning as RingingService:
        // this service is a process-wide singleton.
        stopRinging()
        activeTimerId = timerId

        ServiceCompat.startForeground(
            this,
            TimerRingingNotifications.notificationId(timerId),
            TimerRingingNotifications.build(
                this,
                ringingActivityPendingIntent(timerId),
                ringingActivityPendingIntent(timerId),
                dismissPendingIntent(timerId),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        acquireWakeLock()

        scope.launch {
            val settings = DataProvider.settingsRepository(this@TimerRingingService).getSettings()
            val timer = DataProvider.timerRepository(this@TimerRingingService).getTimer(timerId)
            if (timer != null) {
                // Replaces the placeholder posted above, now that the
                // timer's configured duration is known — same reasoning as
                // RingingService re-posting once the real alarm name loads.
                notifyIfAllowed(
                    TimerRingingNotifications.notificationId(timerId),
                    TimerRingingNotifications.build(
                        this@TimerRingingService,
                        ringingActivityPendingIntent(timerId),
                        ringingActivityPendingIntent(timerId),
                        dismissPendingIntent(timerId),
                        configuredDurationLabel = TimerCountdown.format(timer.configuredDuration),
                    ),
                )
            }
            // Same background-activity-launch caveat as RingingService: only
            // reaches the screen directly when the app is already
            // foreground. The notification's full-screen intent posted
            // above is what actually puts the ringing screen over the lock
            // screen otherwise.
            try {
                startActivity(ringingActivityIntent(timerId))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start the timer ringing activity directly", e)
            }
            startPlayback(settings)
            startVibration(settings)
        }
    }

    /** [NotificationManagerCompat.notify], permission-checked — mirrors `RingingService.notifyIfAllowed`. */
    private fun notifyIfAllowed(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    private fun dismiss(timerId: Long) {
        stopRinging()
        activeTimerId = Timer.NO_ID
        TimerRingingNotifications.cancel(this, timerId)
        scope.launch {
            val timers = DataProvider.timerRepository(this@TimerRingingService)
            val timer = timers.getTimer(timerId)
            // Dismissing returns the timer to a fresh, restartable state at
            // its full original duration, ready to be started again - not
            // left pinned at EXPIRED.
            if (timer != null && timer.state == TimerState.EXPIRED) {
                timers.save(timer.reset())
            }
            TimerProvider.scheduler(this@TimerRingingService).syncAll()
            stopSelf()
        }
    }

    private fun startPlayback(settings: AppSettings) {
        val requestedUri = settings.defaultTimerRingtoneUri?.let(Uri::parse)
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val startVolume = if (settings.volumeEscalation) ESCALATION_START_VOLUME else settings.alarmVolume

        var player = newMediaPlayer(startVolume)
        try {
            player.setDataSource(this, requestedUri ?: fallbackUri)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't play $requestedUri, falling back to default alarm sound", e)
            player.release()
            player = newMediaPlayer(startVolume)
            player.setDataSource(this, fallbackUri)
        }
        player.setOnPreparedListener { it.start() }
        player.prepareAsync()
        mediaPlayer = player

        if (settings.volumeEscalation) {
            escalationJob = scope.launch { escalateVolume(settings.alarmVolume) }
        }
    }

    private fun newMediaPlayer(volume: Float): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        isLooping = true
        setVolume(volume, volume)
    }

    /** Mirrors `RingingService.escalateVolume` exactly. */
    private suspend fun escalateVolume(targetVolume: Float) {
        val steps = (ESCALATION_DURATION_MILLIS / ESCALATION_STEP_MILLIS).toInt()
        for (step in 1..steps) {
            delay(ESCALATION_STEP_MILLIS)
            val fraction = step.toFloat() / steps
            val volume = ESCALATION_START_VOLUME + (targetVolume - ESCALATION_START_VOLUME) * fraction
            mediaPlayer?.setVolume(volume, volume)
        }
    }

    private fun startVibration(settings: AppSettings) {
        if (!settings.defaultVibrate) return
        val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attributes)
        } else {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            vibrator.vibrate(effect, attributes)
        }
    }

    private fun stopRinging() {
        escalationJob?.cancel()
        escalationJob = null
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TimerRingingWakeLock")
            .apply { acquire(MAX_RING_DURATION_MILLIS) }
    }

    private fun ringingActivityIntent(timerId: Long): Intent =
        Intent()
            .setClassName(packageName, RINGING_ACTIVITY_CLASS)
            .putExtra(EXTRA_TIMER_ID, timerId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun ringingActivityPendingIntent(timerId: Long): PendingIntent =
        PendingIntent.getActivity(
            this,
            timerId.hashCode(),
            ringingActivityIntent(timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun dismissPendingIntent(timerId: Long): PendingIntent =
        PendingIntent.getService(
            this,
            timerId.hashCode(),
            dismissIntent(this, timerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_RING = "imb.tzalarmclock.timer.action.RING"
        const val ACTION_DISMISS = "imb.tzalarmclock.timer.action.DISMISS"
        const val EXTRA_TIMER_ID = "imb.tzalarmclock.timer.extra.TIMER_ID"

        /** Can't reference `TimerRingingActivity` directly: it lives in the `app` module, which depends on this one. */
        private const val RINGING_ACTIVITY_CLASS = "imb.tzalarmclock.TimerRingingActivity"

        private const val TAG = "TimerRingingService"
        private const val ESCALATION_START_VOLUME = 0.15f
        private const val ESCALATION_DURATION_MILLIS = 75_000L
        private const val ESCALATION_STEP_MILLIS = 500L
        private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500)

        /**
         * The timer id this (process-wide singleton) service is actively
         * ringing, or [Timer.NO_ID] between ring cycles. In-memory, same
         * reasoning as `RingingService.activeAlarmId` — losing it on process
         * death is fine, since the ringing screen dies with the process too.
         */
        @Volatile
        private var activeTimerId: Long = Timer.NO_ID

        fun ringIntent(context: Context, timerId: Long): Intent =
            Intent(context, TimerRingingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_TIMER_ID, timerId)

        fun dismissIntent(context: Context, timerId: Long): Intent =
            Intent(context, TimerRingingService::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_TIMER_ID, timerId)

        /** Whether this service is actively ringing [timerId] right now. */
        fun isRinging(timerId: Long): Boolean = activeTimerId == timerId
    }
}
