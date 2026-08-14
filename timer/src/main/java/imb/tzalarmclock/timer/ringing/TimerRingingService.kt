package imb.tzalarmclock.timer.ringing

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.reset
import imb.tzalarmclock.timer.TimerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns an actively-ringing timer's playback and ongoing notification.
 *
 * Stage 15 scope only: plays the system default alarm sound (Stage 18's
 * default-timer-ringtone setting doesn't exist yet) at a fixed volume, with
 * a plain Dismiss notification action and no full-screen ringing UI — Stage
 * 17 replaces this with the full `TimerRingingActivity`-backed experience
 * (vibration, volume escalation, hold-to-dismiss), mirroring how
 * `imb.tzalarmclock.alarm.ringing.RingingService` itself grew from Stage 3's
 * interim version into Stage 6's full one.
 *
 * A foreground service rather than logic in `TimerReceiver` directly, for
 * the same reason as the alarm equivalent: playback has to keep running well
 * past the few seconds a `BroadcastReceiver` is allowed to run for.
 */
class TimerRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

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
            TimerRingingNotifications.build(this, dismissPendingIntent(timerId)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        acquireWakeLock()
        startPlayback()
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

    private fun startPlayback() {
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
        }
        player.setDataSource(this, fallbackUri)
        player.setOnPreparedListener { it.start() }
        player.prepareAsync()
        mediaPlayer = player
    }

    private fun stopRinging() {
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:TimerRingingWakeLock")
            .apply { acquire(MAX_RING_DURATION_MILLIS) }
    }

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

        private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L

        /**
         * The timer id this (process-wide singleton) service is actively
         * ringing, or [Timer.NO_ID] between ring cycles. In-memory, same
         * reasoning as `RingingService.activeAlarmId` — losing it on process
         * death is fine, since the ringing UI (Stage 17) dies with the
         * process too.
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
