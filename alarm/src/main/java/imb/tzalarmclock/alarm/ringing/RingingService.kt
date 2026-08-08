package imb.tzalarmclock.alarm.ringing

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import imb.tzalarmclock.alarm.AlarmProvider
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Owns everything about an actively-ringing alarm: playback, vibration,
 * volume escalation, the ongoing notification, and the repository/scheduler
 * writes the spec's snooze/dismiss workflow requires.
 *
 * A foreground service rather than logic in `AlarmReceiver` directly, because
 * playback has to keep running well past the few seconds a
 * `BroadcastReceiver` is allowed to run for.
 *
 * [ACTION_SNOOZE] and [ACTION_DISMISS] are plain `startService` intents the
 * ringing activity fires and then finishes immediately — no binding — since
 * the repository/scheduler work only needs to outlive this service, not the
 * UI showing it.
 */
class RingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var escalationJob: Job? = null
    private var timeoutJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, Alarm.NO_ID) ?: Alarm.NO_ID
        if (alarmId == Alarm.NO_ID) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_SNOOZE -> snooze(alarmId)
            ACTION_DISMISS -> dismiss(alarmId, dueToTimeout = false)
            else -> ring(alarmId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        activeAlarmId = Alarm.NO_ID
        scope.cancel()
        super.onDestroy()
    }

    private fun ring(alarmId: Long) {
        // Only one ring session at a time: this service is a process-wide
        // singleton, so a second alarm firing while the first is still
        // ringing would otherwise overwrite mediaPlayer/wakeLock without
        // releasing them. Tearing down the first is a safe (if imperfect —
        // its sound stops) default until this app needs to actually queue
        // concurrent alarms.
        stopRinging()
        // Set before anything else can fail: RingingViewModel polls this to
        // know when to finish the ringing screen, and the screen may already
        // be showing (relaunched via its own full-screen intent) by the time
        // this method's coroutine below gets around to loading the alarm.
        activeAlarmId = alarmId

        // Posted immediately, before the alarm/settings reads below: the OS
        // kills the service if startForeground() doesn't follow
        // startForegroundService() within a few seconds, and this notification
        // is replaced in place once the real alarm name has loaded.
        ServiceCompat.startForeground(
            this,
            RingingNotifications.notificationId(alarmId),
            RingingNotifications.build(
                this,
                Alarm(time = LocalTime.MIDNIGHT),
                ringingActivityPendingIntent(alarmId),
                ringingActivityPendingIntent(alarmId),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        // This ring may be a snooze coming due, in which case the snoozed
        // notification is still posted and is now stale.
        RingingNotifications.cancelSnoozed(this, alarmId)
        acquireWakeLock()

        scope.launch {
            val alarm = DataProvider.alarmRepository(this@RingingService).getAlarm(alarmId)
            if (alarm == null || !alarm.enabled) {
                Log.w(TAG, "Alarm $alarmId fired but is gone or disabled")
                stopRinging()
                activeAlarmId = Alarm.NO_ID
                stopSelf()
                return@launch
            }
            val settings = DataProvider.settingsRepository(this@RingingService).getSettings()
            // Acts on the user's behalf exactly like a tap would, just without
            // one, once a ring goes unacknowledged for too long: snoozes it,
            // the same as tapping Snooze, if there's a snooze left to give;
            // otherwise dismisses it, the same as a hold-to-dismiss — see
            // dismiss()'s dueToTimeout parameter. Mirrors canSnooze's own
            // `remaining > 0` check in RingingUiState, just without the
            // coerceAtLeast(0) that formatting a display count needs and this
            // boolean doesn't. stopRinging() (called from both snooze() and
            // dismiss()) cancels this the moment the user acts first.
            timeoutJob = scope.launch {
                delay(settings.ringTimeoutMinutes * MINUTES_TO_MILLIS)
                val snoozeCount = SnoozeRegistry(this@RingingService).snoozeCount(alarmId)
                if (snoozeCount < settings.maxSnoozeCount) {
                    snooze(alarmId)
                } else {
                    dismiss(alarmId, dueToTimeout = true)
                }
            }

            NotificationManagerCompat.from(this@RingingService).notify(
                RingingNotifications.notificationId(alarmId),
                RingingNotifications.build(
                    this@RingingService,
                    alarm,
                    ringingActivityPendingIntent(alarmId),
                    ringingActivityPendingIntent(alarmId),
                ),
            )
            // Only reaches the screen when the app is already in the foreground,
            // where it beats the heads-up notification the platform would show
            // instead. From the background — the case that matters, and the one
            // this service is normally in — Android's background-activity-launch
            // rules reject it outright ("Background activity launch blocked!" in
            // logcat, no exception thrown, so the catch below stays quiet). The
            // notification's full-screen intent posted above is what actually
            // puts the ringing screen over the lock screen, not this.
            try {
                startActivity(ringingActivityIntent(alarmId))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start the ringing activity directly", e)
            }
            startPlayback(alarm, settings)
            startVibration(alarm, settings)
        }
    }

    private fun snooze(alarmId: Long) {
        stopRinging()
        activeAlarmId = Alarm.NO_ID
        scope.launch {
            val alarmRepository = DataProvider.alarmRepository(this@RingingService)
            val settings = DataProvider.settingsRepository(this@RingingService).getSettings()
            val alarm = alarmRepository.getAlarm(alarmId)
            val until = Instant.now().plus(settings.snoozePeriodMinutes.toLong(), ChronoUnit.MINUTES)

            SnoozeRegistry(this@RingingService).recordSnooze(alarmId, until.toEpochMilli())
            AlarmProvider.scheduler(this@RingingService).syncAll()

            if (alarm != null) {
                // A separate notification from the ringing one (hence the tag),
                // not a replacement posted under the same id: the ringing
                // notification has to be able to come back as a *new* one when
                // the snooze comes due, or its full-screen intent won't fire and
                // the ringing screen won't appear over the lock screen. See
                // RingingNotifications.SNOOZED_TAG.
                NotificationManagerCompat.from(this@RingingService).notify(
                    RingingNotifications.SNOOZED_TAG,
                    RingingNotifications.notificationId(alarmId),
                    RingingNotifications.buildSnoozed(
                        context = this@RingingService,
                        alarm = alarm,
                        snoozedUntil = until,
                        use24HourFormat = settings.use24HourFormat,
                        dismissIntent = dismissPendingIntent(alarmId),
                        contentIntent = mainActivityPendingIntent(),
                    ),
                )
            }
            // Takes the ringing notification down with the service. The snoozed
            // one posted above survives it, having never been the foreground
            // notification — no STOP_FOREGROUND_DETACH needed to keep it.
            ServiceCompat.stopForeground(this@RingingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * @param dueToTimeout `true` when this dismiss is [RingingService] acting
     *   on the user's behalf after [AppSettings.ringTimeoutMinutes] of no
     *   response with no snooze left to give (see [ring]'s `timeoutJob`),
     *   rather than an explicit hold-to-dismiss or notification action — the
     *   only difference is that it also posts the "canceled" notification
     *   below, so a user who wasn't there to see the alarm ring still finds
     *   out it went unacknowledged.
     */
    private fun dismiss(alarmId: Long, dueToTimeout: Boolean) {
        stopRinging()
        activeAlarmId = Alarm.NO_ID
        RingingNotifications.cancel(this, alarmId)
        scope.launch {
            SnoozeRegistry(this@RingingService).clear(alarmId)
            val alarms = DataProvider.alarmRepository(this@RingingService)
            val alarm = alarms.getAlarm(alarmId)
            // A non-recurring alarm has no future occurrence once dismissed, so
            // it must be switched off here or "the next occurrence of 07:00"
            // would just find tomorrow and re-arm forever.
            if (alarm != null && !alarm.schedule.isRecurring) {
                alarms.setEnabled(alarmId, false)
            }
            AlarmProvider.scheduler(this@RingingService).syncAll()
            if (dueToTimeout && alarm != null) {
                val use24HourFormat = DataProvider.settingsRepository(this@RingingService)
                    .getSettings().use24HourFormat
                NotificationManagerCompat.from(this@RingingService).notify(
                    RingingNotifications.CANCELED_TAG,
                    RingingNotifications.notificationId(alarmId),
                    RingingNotifications.buildCanceled(
                        context = this@RingingService,
                        alarm = alarm,
                        use24HourFormat = use24HourFormat,
                        contentIntent = mainActivityPendingIntent(),
                    ),
                )
            }
            stopSelf()
        }
    }

    private fun startPlayback(alarm: Alarm, settings: AppSettings) {
        val requestedUri = (alarm.ringtoneUri ?: settings.defaultRingtoneUri)?.let(Uri::parse)
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

    /**
     * Ramps from [ESCALATION_START_VOLUME] up to [targetVolume] over
     * [ESCALATION_DURATION_MILLIS]. Cancelling [escalationJob] (in
     * [stopRinging]) simply lets the next [delay] throw and end the loop —
     * no explicit liveness check needed.
     */
    private suspend fun escalateVolume(targetVolume: Float) {
        val steps = (ESCALATION_DURATION_MILLIS / ESCALATION_STEP_MILLIS).toInt()
        for (step in 1..steps) {
            delay(ESCALATION_STEP_MILLIS)
            val fraction = step.toFloat() / steps
            val volume = ESCALATION_START_VOLUME + (targetVolume - ESCALATION_START_VOLUME) * fraction
            mediaPlayer?.setVolume(volume, volume)
        }
    }

    private fun startVibration(alarm: Alarm, settings: AppSettings) {
        if (!(alarm.vibrate ?: settings.defaultVibrate)) return
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
        timeoutJob?.cancel()
        timeoutJob = null
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
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:RingingWakeLock")
            .apply { acquire(MAX_RING_DURATION_MILLIS) }
    }

    private fun ringingActivityIntent(alarmId: Long): Intent =
        Intent()
            .setClassName(packageName, RINGING_ACTIVITY_CLASS)
            .putExtra(alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun ringingActivityPendingIntent(alarmId: Long): PendingIntent =
        PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            ringingActivityIntent(alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Fires this service's own dismiss handling directly from a notification action tap. */
    private fun dismissPendingIntent(alarmId: Long): PendingIntent =
        PendingIntent.getService(
            this,
            alarmId.hashCode(),
            dismissIntent(this, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What tapping the snoozed notification's body (rather than its Dismiss action) does. */
    private fun mainActivityPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Intent.putExtra(alarmId: Long): Intent = putExtra(EXTRA_ALARM_ID, alarmId)

    companion object {
        const val ACTION_RING = "imb.tzalarmclock.alarm.action.RING"
        const val ACTION_SNOOZE = "imb.tzalarmclock.alarm.action.SNOOZE"
        const val ACTION_DISMISS = "imb.tzalarmclock.alarm.action.DISMISS"
        const val EXTRA_ALARM_ID = "imb.tzalarmclock.alarm.extra.ALARM_ID"

        /** Can't reference `RingingActivity` directly: it lives in the `app` module, which depends on this one. */
        private const val RINGING_ACTIVITY_CLASS = "imb.tzalarmclock.RingingActivity"

        private const val TAG = "RingingService"
        private const val ESCALATION_START_VOLUME = 0.15f
        private const val ESCALATION_DURATION_MILLIS = 75_000L
        private const val ESCALATION_STEP_MILLIS = 500L
        private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L
        private const val OPEN_APP_REQUEST_CODE = 100
        private const val MINUTES_TO_MILLIS = 60_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500)

        /**
         * The alarm id this (process-wide singleton) service is actively
         * ringing, or [Alarm.NO_ID] between ring cycles.
         *
         * In-memory, not persisted: it exists purely so [isRinging] can tell
         * [imb.tzalarmclock.ui.ringing.RingingViewModel] when to finish the
         * ringing screen after this service ends a ring cycle on its own —
         * an unacknowledged-ring timeout auto-snoozing or auto-dismissing,
         * most notably — with nobody having tapped anything in the UI to
         * trigger it. Losing this on process death is fine: the screen dies
         * with the process too.
         */
        @Volatile
        private var activeAlarmId: Long = Alarm.NO_ID

        fun ringIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ALARM_ID, alarmId)

        fun snoozeIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_ALARM_ID, alarmId)

        fun dismissIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ALARM_ID, alarmId)

        /**
         * Whether this service is actively ringing [alarmId] right now — polled
         * by the ringing screen so it can finish itself once a ring cycle ends
         * without the user's own tap being what ended it.
         */
        fun isRinging(alarmId: Long): Boolean = activeAlarmId == alarmId

        /** How many times the current ring cycle for [alarmId] has been snoozed. */
        fun snoozeCount(context: Context, alarmId: Long): Int = SnoozeRegistry(context).snoozeCount(alarmId)

        /**
         * Every currently-snoozed alarm id and the instant it's snoozed until, in
         * millis — the same registry [imb.tzalarmclock.alarm.schedule.AndroidAlarmScheduler]
         * consults when arming, exposed so other snooze-aware displays (e.g. the
         * Summary page) can match what's actually armed.
         */
        fun allSnoozedUntilMillis(context: Context): Map<Long, Long> = SnoozeRegistry(context).allSnoozedUntilMillis()
    }
}
