package imb.tzalarmclock.alarm.ringing

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
import imb.tzalarmclock.alarm.AlarmProvider
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns every actively-ringing alarm's playback, vibration, volume escalation,
 * the ongoing notification, and the repository/scheduler writes the spec's
 * snooze/dismiss workflow requires.
 *
 * More than one alarm can legitimately be ringing at once (two independently
 * scheduled alarms landing close together, most notably), so — mirroring
 * `imb.tzalarmclock.timer.ringing.TimerRingingService`'s equivalent fix —
 * this service tracks the whole set of currently-ringing alarm ids rather
 * than a single one. Only one sound plays at a time regardless: a second
 * alarm joining an already-ringing cycle doesn't restart or overlap
 * playback, just adds itself to the ring and to the shared
 * notification/screen. Both Snooze and Dismiss on the Ring screen act on
 * every ringing alarm together ([snoozeAll]/[dismissAll]); each alarm still
 * gets its own [AppSettings.ringTimeoutMinutes] countdown, though, so an
 * unacknowledged alarm snoozes-or-dismisses *itself* on timeout without
 * waiting for, or disturbing, any other still-ringing alarm.
 *
 * A foreground service rather than logic in `AlarmReceiver` directly, because
 * playback has to keep running well past the few seconds a
 * `BroadcastReceiver` is allowed to run for.
 *
 * [ACTION_SNOOZE] and [ACTION_DISMISS] (per-alarm) are plain `startService`
 * intents fired directly at one specific alarm — the snoozed notification's
 * own Dismiss action, most notably, which must only ever affect the one
 * alarm it's for, never whatever else happens to be ringing at that moment.
 * [ACTION_SNOOZE_ALL]/[ACTION_DISMISS_ALL] are what the Ring screen's
 * buttons fire instead, per this class's own doc above.
 */
class RingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var escalationJob: Job? = null

    /** Per-alarm [AppSettings.ringTimeoutMinutes] countdown — see [startTimeout]. */
    private val timeoutJobs = ConcurrentHashMap<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE_ALL -> {
                snoozeAll()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_ALL -> {
                dismissAll()
                return START_NOT_STICKY
            }
        }
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, Alarm.NO_ID) ?: Alarm.NO_ID
        if (alarmId == Alarm.NO_ID) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_SNOOZE -> snoozeOne(alarmId)
            ACTION_DISMISS -> dismissOne(alarmId, dueToTimeout = false)
            else -> ring(alarmId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        synchronized(ringingAlarmIds) { ringingAlarmIds.clear() }
        scope.cancel()
        super.onDestroy()
    }

    private fun ring(alarmId: Long) {
        val isFirstRingingAlarm = addRingingId(alarmId)
        if (isFirstRingingAlarm) {
            // Posted immediately, before the alarm/settings reads below: the
            // OS kills the service if startForeground() doesn't follow
            // startForegroundService() within a few seconds. Replaced once
            // the real alarm has loaded — same reasoning as the placeholder
            // `Alarm(time = LocalTime.MIDNIGHT)` this used to post directly.
            // A *second* (or later) alarm joining an already-foreground ring
            // cycle has no such deadline, so it skips straight to the real
            // refresh below instead of posting its own placeholder first.
            postForegroundNotification(alarmId, emptyList())
        }
        // This ring may be a snooze coming due, in which case the snoozed
        // notification is still posted and is now stale.
        RingingNotifications.cancelSnoozed(this, alarmId)
        acquireWakeLock()

        scope.launch {
            val alarm = DataProvider.alarmRepository(this@RingingService).getAlarm(alarmId)
            if (alarm == null || !alarm.enabled) {
                Log.w(TAG, "Alarm $alarmId fired but is gone or disabled")
                removeRingingId(alarmId)
                timeoutJobs.remove(alarmId)?.cancel()
                finishRingIfEmpty()
                return@launch
            }
            val settings = DataProvider.settingsRepository(this@RingingService).getSettings()
            postForegroundNotification(alarmId, loadAlarmsForNotification(currentlyRingingAlarmIds()))

            if (isFirstRingingAlarm) {
                // Same background-activity-launch caveat as before: only
                // reaches the screen directly when the app is already
                // foreground. The notification's full-screen intent posted
                // above is what actually puts the ringing screen over the
                // lock screen otherwise. Only done for the *first* ringing
                // alarm — a later one joining updates the same notification
                // and the same already-showing screen in place (see
                // RingingViewModel's polling) rather than stacking a second
                // ringing activity on top of the first.
                try {
                    startActivity(ringingActivityIntent(alarmId))
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't start the ringing activity directly", e)
                }
                startPlayback(alarm, settings)
                startVibration(alarm, settings)
            }
            startTimeout(alarmId, settings)
        }
    }

    private fun postForegroundNotification(latestAlarmId: Long, alarms: List<Alarm>) {
        ServiceCompat.startForeground(
            this,
            RingingNotifications.RINGING_NOTIFICATION_ID,
            RingingNotifications.build(
                this,
                alarms,
                ringingActivityPendingIntent(latestAlarmId),
                ringingActivityPendingIntent(latestAlarmId),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private suspend fun loadAlarmsForNotification(alarmIds: List<Long>): List<Alarm> {
        val alarms = DataProvider.alarmRepository(this@RingingService)
        return alarmIds.mapNotNull { alarms.getAlarm(it) }
    }

    /**
     * Snoozes every currently-ringing alarm that still has a snooze left,
     * the same as tapping Snooze — or dismisses it if it doesn't, the same
     * as [dismissOne] would, mirroring the decision [startTimeout] makes for
     * a single unacknowledged alarm. What the Ring screen's Snooze button
     * calls.
     */
    private fun snoozeAll() {
        scope.launch {
            val settings = DataProvider.settingsRepository(this@RingingService).getSettings()
            currentlyRingingAlarmIds().forEach { alarmId ->
                val snoozeCount = SnoozeRegistry(this@RingingService).snoozeCount(alarmId)
                if (snoozeCount < settings.maxSnoozeCount) {
                    snoozeOne(alarmId)
                } else {
                    dismissOne(alarmId, dueToTimeout = false)
                }
            }
        }
    }

    /** Dismisses every currently-ringing alarm together — what the Ring screen's Dismiss button calls. */
    private fun dismissAll() {
        currentlyRingingAlarmIds().forEach { dismissOne(it, dueToTimeout = false) }
    }

    private fun snoozeOne(alarmId: Long) {
        removeRingingId(alarmId)
        timeoutJobs.remove(alarmId)?.cancel()
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
                notifyIfAllowed(
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
            finishRingIfEmpty()
        }
    }

    /**
     * @param dueToTimeout `true` when this dismiss is [RingingService] acting
     *   on the user's behalf after [AppSettings.ringTimeoutMinutes] of no
     *   response with no snooze left to give (see [startTimeout]), rather
     *   than an explicit Dismiss tap or notification action — the only
     *   difference is that it also posts the "canceled" notification below,
     *   so a user who wasn't there to see the alarm ring still finds out it
     *   went unacknowledged.
     *
     *   Also what dismisses an alarm from its *snoozed* notification's own
     *   Dismiss action — [alarmId] need not currently be ringing at all for
     *   that case, which is why the repository/scheduler work below always
     *   runs regardless of whether this alarm was actually in the ringing
     *   set; only the shared playback teardown in [finishRingIfEmpty] is
     *   conditional on the set actually being affected.
     */
    private fun dismissOne(alarmId: Long, dueToTimeout: Boolean) {
        removeRingingId(alarmId)
        timeoutJobs.remove(alarmId)?.cancel()
        RingingNotifications.cancelSnoozed(this, alarmId)
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
                notifyIfAllowed(
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
            finishRingIfEmpty()
        }
    }

    /**
     * Tears down the shared playback/notification and stops the service once
     * nothing is ringing anymore, or refreshes the shared notification with
     * whoever's left otherwise. Called at the end of every path that can
     * shrink [ringingAlarmIds] — [ring]'s gone-or-disabled branch, [snoozeOne],
     * and [dismissOne] — so it also correctly no-ops (finds the set already
     * empty, tears down nothing new) for a snoozed-notification dismiss that
     * was never part of the ringing set in the first place.
     */
    private suspend fun finishRingIfEmpty() {
        val remaining = currentlyRingingAlarmIds()
        if (remaining.isEmpty()) {
            stopRinging()
            RingingNotifications.cancelRinging(this)
            stopSelf()
        } else {
            postForegroundNotification(remaining.last(), loadAlarmsForNotification(remaining))
        }
    }

    private fun startTimeout(alarmId: Long, settings: AppSettings) {
        timeoutJobs[alarmId] = scope.launch {
            delay(settings.ringTimeoutMinutes * MINUTES_TO_MILLIS)
            val snoozeCount = SnoozeRegistry(this@RingingService).snoozeCount(alarmId)
            if (snoozeCount < settings.maxSnoozeCount) {
                snoozeOne(alarmId)
            } else {
                dismissOne(alarmId, dueToTimeout = true)
            }
        }
    }

    /**
     * `NotificationManagerCompat.notify()`, but explicitly checked against
     * `POST_NOTIFICATIONS` first (required from API 33) rather than trusting
     * it's granted — lint flags the raw call as `MissingPermission` for
     * exactly this reason. The check has to be inlined directly around the
     * `notify()` call in the same function, not factored out (tried first,
     * to both overloads below and to `SchedulingHealth.areNotificationsAllowed`
     * before that): lint's dataflow analysis doesn't trace a permission check
     * through a call to another function to see that it guards this one, no
     * matter how directly — it only recognizes the check written right here.
     *
     * A skip, not a failure: this service's own playback/vibration keep
     * running either way (its whole reason for existing, per the class doc),
     * so a denied notification permission must never crash — or even
     * interrupt — the ring workflow around it. `SchedulingHealth`'s startup
     * warning is what tells the user their alarm is silently going
     * notification-less; this just has to not blow up when that's the case.
     */
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

    /** [notifyIfAllowed] for a tagged notification — see [RingingNotifications.SNOOZED_TAG]. */
    private fun notifyIfAllowed(tag: String, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(tag, id, notification)
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
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    /**
     * (Re-)acquires the shared wake lock, releasing any previously-held one
     * first — called on every [ring], including a later alarm joining an
     * already-ringing cycle, which simply extends the hold rather than
     * leaking the old one.
     */
    private fun acquireWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
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

    /** Adds [alarmId] to [ringingAlarmIds]. @return `true` if the set was empty beforehand. */
    private fun addRingingId(alarmId: Long): Boolean = synchronized(ringingAlarmIds) {
        val wasEmpty = ringingAlarmIds.isEmpty()
        ringingAlarmIds.add(alarmId)
        wasEmpty
    }

    private fun removeRingingId(alarmId: Long) {
        synchronized(ringingAlarmIds) { ringingAlarmIds.remove(alarmId) }
    }

    companion object {
        const val ACTION_RING = "imb.tzalarmclock.alarm.action.RING"
        const val ACTION_SNOOZE = "imb.tzalarmclock.alarm.action.SNOOZE"
        const val ACTION_DISMISS = "imb.tzalarmclock.alarm.action.DISMISS"
        const val ACTION_SNOOZE_ALL = "imb.tzalarmclock.alarm.action.SNOOZE_ALL"
        const val ACTION_DISMISS_ALL = "imb.tzalarmclock.alarm.action.DISMISS_ALL"
        const val EXTRA_ALARM_ID = "imb.tzalarmclock.alarm.extra.ALARM_ID"

        /** Can't reference `AlarmRingingActivity` directly: it lives in the `app` module, which depends on this one. */
        private const val RINGING_ACTIVITY_CLASS = "imb.tzalarmclock.AlarmRingingActivity"

        private const val TAG = "RingingService"
        private const val ESCALATION_START_VOLUME = 0.15f
        private const val ESCALATION_DURATION_MILLIS = 75_000L
        private const val ESCALATION_STEP_MILLIS = 500L
        private const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L
        private const val OPEN_APP_REQUEST_CODE = 100
        private const val MINUTES_TO_MILLIS = 60_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500)

        /**
         * Every alarm id this (process-wide singleton) service is actively
         * ringing right now, insertion-ordered (oldest fire first). In-memory,
         * not persisted: it exists purely so [isRinging] can tell
         * [imb.tzalarmclock.ui.ringing.RingingViewModel] when to finish the
         * ringing screen after this service ends a ring cycle on its own —
         * an unacknowledged-ring timeout auto-snoozing or auto-dismissing,
         * most notably — with nobody having tapped anything in the UI to
         * trigger it. Losing this on process death is fine: the screen dies
         * with the process too. Guarded by `synchronized` rather than
         * `@Volatile` (unlike the single-`Long` version this replaces) since
         * membership is now a compound, mutated-from-multiple-threads set:
         * [ring], [snoozeAll]/[dismissAll], and a per-alarm [startTimeout]
         * job can all race — the last from a `Job` completing on a different
         * thread than the `onStartCommand`-driven calls.
         */
        private val ringingAlarmIds = linkedSetOf<Long>()

        fun ringIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ALARM_ID, alarmId)

        fun snoozeIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_ALARM_ID, alarmId)

        fun dismissIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ALARM_ID, alarmId)

        /** Snoozes/dismisses every currently-ringing alarm together — see the class doc. */
        fun snoozeAllIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_SNOOZE_ALL)

        fun dismissAllIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_DISMISS_ALL)

        /**
         * Whether this service is actively ringing [alarmId] right now — polled
         * by the ringing screen so it can finish itself once a ring cycle ends
         * without the user's own tap being what ended it.
         */
        fun isRinging(alarmId: Long): Boolean = synchronized(ringingAlarmIds) { alarmId in ringingAlarmIds }

        /** Every alarm id currently ringing, oldest-fired first — the Ring screen's source of truth. */
        fun currentlyRingingAlarmIds(): List<Long> = synchronized(ringingAlarmIds) { ringingAlarmIds.toList() }

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
