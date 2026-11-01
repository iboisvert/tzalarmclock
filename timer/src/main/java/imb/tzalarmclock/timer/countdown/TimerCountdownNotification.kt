package imb.tzalarmclock.timer.countdown

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import imb.tzalarmclock.domain.format.TimerCountdown
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.timer.R
import java.time.Instant

/**
 * The silent, ongoing notification counting down to whichever running timer
 * will fire next.
 *
 * Exactly one is posted no matter how many timers are running (see
 * [NOTIFICATION_ID]), always for the soonest — the one the user is actually
 * waiting on. Its countdown is the platform's own chronometer
 * ([NotificationCompat.Builder.setChronometerCountDown]) rather than a
 * notification we re-post every second: the system ticks it, so there's no
 * service or polling loop to keep alive, and it keeps counting correctly
 * while the app process is dead. It only needs re-posting when *which*
 * timer is next changes, which is exactly when `AndroidTimerScheduler.sync`
 * runs anyway.
 */
object TimerCountdownNotification {

    const val CHANNEL_ID = "timer_countdown_v1"

    val NOTIFICATION_ID: Int = "timer_countdown".hashCode()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        // Low importance: a countdown the user just started needs no sound,
        // vibration, or heads-up — only to be there when they look.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_countdown_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.timer_countdown_channel_description)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts (or replaces) the countdown for [next].
     *
     * @param next the running timer that will fire soonest.
     * @param otherRunningCount how many other timers are also running.
     */
    fun show(context: Context, next: Timer, otherRunningCount: Int, now: Instant = Instant.now()) {
        val endInstant = checkNotNull(next.endInstant) { "Running timer has no endInstant" }
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(
                context.getString(R.string.timer_countdown_title, TimerCountdown.format(next.configuredDuration)),
            )
            .setWhen(endInstant.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            // Gone at zero rather than ticking on into negative time while
            // the fire path (receiver -> expire -> sync) catches up; that
            // sync posts the next timer's countdown, if there is one.
            .setTimeoutAfter((endInstant.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(1L))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(timersPagePendingIntent(context))
        if (otherRunningCount > 0) {
            builder.setContentText(
                context.resources.getQuantityString(
                    R.plurals.timer_countdown_others,
                    otherRunningCount,
                    otherRunningCount,
                ),
            )
        }

        // Inlined permission check, not factored out — see
        // `imb.tzalarmclock.alarm.ringing.RingingService.notifyIfAllowed` for
        // why lint needs it written right here. A skip, not a failure: the
        // timer itself is armed either way.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Opens the app on the Timers page. Built from the launch intent plus
     * `MainActivity`'s destination extra rather than `MainActivity.timersIntent`
     * itself: `MainActivity` lives in the `app` module, which depends on this
     * one. `CLEAR_TOP` matches `timersIntent`, so an already-running instance
     * switches to the Timers page instead of just coming forward.
     */
    private fun timersPagePendingIntent(context: Context): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            OPEN_TIMERS_REQUEST_CODE,
            launch
                .putExtra(EXTRA_DESTINATION, DESTINATION_TIMERS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Must match `imb.tzalarmclock.MainActivity`'s private constants of the same names. */
    private const val EXTRA_DESTINATION = "imb.tzalarmclock.extra.DESTINATION"
    private const val DESTINATION_TIMERS = "timers"

    private const val OPEN_TIMERS_REQUEST_CODE = 3
}
