package imb.tzalarmclock.timer.ringing

import android.Manifest
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns every actively-ringing timer's playback, vibration, volume
 * escalation, and ongoing notification — mirrors
 * `imb.tzalarmclock.alarm.ringing.RingingService`'s role for alarms, grown
 * from Stage 15's interim version into Stage 17's full one, with Stage 18's
 * `AppSettings.defaultTimerRingtoneUri` now wired into playback.
 *
 * Unlike `RingingService` (one alarm at a time is the spec's own model —
 * snoozing exists precisely so a second alarm never has to interrupt a
 * first), more than one timer *can* legitimately be ringing at once, so this
 * service tracks the whole set of currently-ringing timer ids rather than a
 * single one. Only one sound plays at a time regardless — a second timer
 * joining an already-ringing cycle doesn't restart or overlap playback, just
 * adds itself to the ring and to the shared notification/screen — and the
 * explicit Dismiss action (notification or Ring screen) clears every timer
 * in the set together. Each timer still gets its own
 * [AppSettings.ringTimeoutMinutes] countdown, though: an unacknowledged
 * timer dismisses *itself* on timeout without waiting for, or disturbing,
 * any other still-ringing timer.
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

    /** Per-timer [AppSettings.ringTimeoutMinutes] countdown — see [startTimeout]. */
    private val timeoutJobs = ConcurrentHashMap<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS_ALL) {
            dismissAll()
            return START_NOT_STICKY
        }
        val timerId = intent?.getLongExtra(EXTRA_TIMER_ID, Timer.NO_ID) ?: Timer.NO_ID
        if (timerId == Timer.NO_ID) {
            stopSelf()
        } else {
            ring(timerId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        synchronized(ringingTimerIds) { ringingTimerIds.clear() }
        scope.cancel()
        super.onDestroy()
    }

    private fun ring(timerId: Long) {
        val isFirstRingingTimer = addRingingId(timerId)
        if (isFirstRingingTimer) {
            // Posted immediately, before the settings/timer reads below: the
            // OS kills the service if startForeground() doesn't follow
            // startForegroundService() within a few seconds. Replaced once
            // the real duration labels have loaded — same reasoning as
            // RingingService posting a placeholder Alarm first. A *second*
            // (or later) timer joining an already-foreground ring cycle has
            // no such deadline, so it skips straight to the real refresh
            // below instead of posting its own placeholder first.
            postForegroundNotification(timerId, emptyList())
        }
        acquireWakeLock()

        scope.launch {
            val settings = DataProvider.settingsRepository(this@TimerRingingService).getSettings()
            postForegroundNotification(timerId, loadDurationLabels(currentlyRingingTimerIds()))

            if (isFirstRingingTimer) {
                // Same background-activity-launch caveat as RingingService:
                // only reaches the screen directly when the app is already
                // foreground. The notification's full-screen intent posted
                // above is what actually puts the ringing screen over the
                // lock screen otherwise. Only done for the *first* ringing
                // timer — a later one joining updates the same notification
                // and the same already-showing screen in place (see
                // TimerRingingViewModel's polling) rather than stacking a
                // second ringing activity on top of the first.
                try {
                    startActivity(ringingActivityIntent(timerId))
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't start the timer ringing activity directly", e)
                }
                startPlayback(settings)
                startVibration(settings)
            }
            startTimeout(timerId, settings.ringTimeoutMinutes)
        }
    }

    private fun postForegroundNotification(latestTimerId: Long, configuredDurationLabels: List<String>) {
        ServiceCompat.startForeground(
            this,
            TimerRingingNotifications.NOTIFICATION_ID,
            TimerRingingNotifications.build(
                this,
                ringingActivityPendingIntent(latestTimerId),
                ringingActivityPendingIntent(latestTimerId),
                dismissAllPendingIntent(),
                configuredDurationLabels = configuredDurationLabels,
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private suspend fun loadDurationLabels(timerIds: List<Long>): List<String> {
        val timers = DataProvider.timerRepository(this@TimerRingingService)
        return timerIds.mapNotNull { timers.getTimer(it) }.map { TimerCountdown.format(it.configuredDuration) }
    }

    /**
     * Ends the ring cycle for every currently-ringing timer together — what
     * the notification's Dismiss action and the Ring screen's Dismiss button
     * both do. A convenience "clear everything I can see" action, distinct
     * from [dismissTimeout]'s narrower per-timer auto-dismiss.
     */
    private fun dismissAll() {
        val dismissedIds = synchronized(ringingTimerIds) { ringingTimerIds.toList().also { ringingTimerIds.clear() } }
        dismissedIds.forEach { timeoutJobs.remove(it)?.cancel() }
        stopRinging()
        TimerRingingNotifications.cancel(this)
        scope.launch {
            resetExpired(dismissedIds)
            TimerProvider.scheduler(this@TimerRingingService).syncAll()
            stopSelf()
        }
    }

    /**
     * The per-timer [AppSettings.ringTimeoutMinutes] safety net: dismisses
     * only [timerId], leaving any other still-ringing timer's own ring cycle
     * (sound, notification, screen) untouched — unlike [dismissAll], this
     * never waits for or disturbs a different timer just because it also
     * happens to be ringing right now.
     */
    private fun dismissTimeout(timerId: Long) {
        timeoutJobs.remove(timerId)
        val wasRinging = synchronized(ringingTimerIds) { ringingTimerIds.remove(timerId) }
        if (!wasRinging) return // already cleared by dismissAll() (or a prior timeout) in the meantime
        scope.launch {
            resetExpired(listOf(timerId))
            TimerProvider.scheduler(this@TimerRingingService).syncAll()
            val stillRinging = currentlyRingingTimerIds()
            if (stillRinging.isEmpty()) {
                stopRinging()
                TimerRingingNotifications.cancel(this@TimerRingingService)
                stopSelf()
            } else {
                postForegroundNotification(stillRinging.last(), loadDurationLabels(stillRinging))
            }
        }
    }

    /**
     * Returns each dismissed timer to a fresh, restartable state at its full
     * original duration, ready to be started again — not left pinned at
     * EXPIRED.
     */
    private suspend fun resetExpired(timerIds: List<Long>) {
        val timers = DataProvider.timerRepository(this@TimerRingingService)
        timerIds.forEach { timerId ->
            val timer = timers.getTimer(timerId)
            if (timer != null && timer.state == TimerState.EXPIRED) {
                timers.save(timer.reset())
            }
        }
    }

    private fun startTimeout(timerId: Long, ringTimeoutMinutes: Int) {
        timeoutJobs[timerId] = scope.launch {
            delay(ringTimeoutMinutes * MINUTES_TO_MILLIS)
            dismissTimeout(timerId)
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

    /**
     * (Re-)acquires the shared wake lock, releasing any previously-held one
     * first — called on every [ring], including a later timer joining an
     * already-ringing cycle, which simply extends the hold rather than
     * leaking the old one.
     */
    private fun acquireWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
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

    private fun dismissAllPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            DISMISS_ALL_REQUEST_CODE,
            dismissAllIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Adds [timerId] to [ringingTimerIds]. @return `true` if the set was empty beforehand. */
    private fun addRingingId(timerId: Long): Boolean = synchronized(ringingTimerIds) {
        val wasEmpty = ringingTimerIds.isEmpty()
        ringingTimerIds.add(timerId)
        wasEmpty
    }

    companion object {
        const val ACTION_RING = "imb.tzalarmclock.timer.action.RING"
        const val ACTION_DISMISS_ALL = "imb.tzalarmclock.timer.action.DISMISS_ALL"
        const val EXTRA_TIMER_ID = "imb.tzalarmclock.timer.extra.TIMER_ID"

        /** Can't reference `TimerRingingActivity` directly: it lives in the `app` module, which depends on this one. */
        private const val RINGING_ACTIVITY_CLASS = "imb.tzalarmclock.TimerRingingActivity"

        private const val TAG = "TimerRingingService"
        private const val ESCALATION_START_VOLUME = 0.15f
        private const val ESCALATION_DURATION_MILLIS = 75_000L
        private const val ESCALATION_STEP_MILLIS = 500L
        private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L
        private const val MINUTES_TO_MILLIS = 60_000L
        private const val DISMISS_ALL_REQUEST_CODE = 1
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500)

        /**
         * Every timer id this (process-wide singleton) service is actively
         * ringing right now, insertion-ordered (oldest fire first). In-memory,
         * same reasoning as `RingingService.activeAlarmId` — losing it on
         * process death is fine, since the ringing screen dies with the
         * process too. Guarded by `synchronized` rather than `@Volatile`
         * (unlike the single-`Long` version this replaces) since membership
         * is now a compound, mutated-from-multiple-threads set: [ring],
         * [dismissAll], and [dismissTimeout] can all race — the last from a
         * timeout `Job` completing on a different thread than the
         * `onStartCommand`-driven calls.
         */
        private val ringingTimerIds = linkedSetOf<Long>()

        /** Whether this service is actively ringing [timerId] right now. */
        fun isRinging(timerId: Long): Boolean = synchronized(ringingTimerIds) { timerId in ringingTimerIds }

        /** Every timer id currently ringing, oldest-fired first — the Ring screen's source of truth. */
        fun currentlyRingingTimerIds(): List<Long> = synchronized(ringingTimerIds) { ringingTimerIds.toList() }

        fun ringIntent(context: Context, timerId: Long): Intent =
            Intent(context, TimerRingingService::class.java)
                .setAction(ACTION_RING)
                .putExtra(EXTRA_TIMER_ID, timerId)

        /** Dismisses every currently-ringing timer together — see the class doc. */
        fun dismissAllIntent(context: Context): Intent =
            Intent(context, TimerRingingService::class.java).setAction(ACTION_DISMISS_ALL)
    }
}
