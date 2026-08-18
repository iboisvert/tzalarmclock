package imb.tzalarmclock.timer.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import imb.tzalarmclock.timer.R

/**
 * The notification that keeps [TimerRingingService] a valid foreground
 * service and (via [NotificationCompat.Builder.setFullScreenIntent])
 * launches `TimerRingingActivity` over the lock screen.
 *
 * Mirrors `imb.tzalarmclock.alarm.ringing.RingingNotifications`, minus the
 * separate snoozed/canceled channels — timers have neither concept. Unlike
 * the alarm equivalent, there's exactly one of these posted at a time no
 * matter how many timers are ringing (see [NOTIFICATION_ID]) — its Dismiss
 * action ends every ringing timer together, so a per-timer notification with
 * its own independent dismiss would be a second, contradictory way to clear
 * just one of them.
 */
object TimerRingingNotifications {

    const val CHANNEL_ID = "timer_ringing_v1"

    /**
     * Fixed rather than derived from a timer id (contrast
     * `imb.tzalarmclock.alarm.ringing.RingingNotifications.notificationId`,
     * which is per-alarm): this notification represents *every* currently-
     * ringing timer, so posting under a stable id is what makes a second
     * timer joining the ring update the existing notification in place
     * instead of stacking a second one beside it.
     */
    val NOTIFICATION_ID: Int = "timer_ringing_group".hashCode()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.timer_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * @param fullScreenIntent launches `TimerRingingActivity` over the lock
     *   screen (or as a heads-up notification if the device declines
     *   full-screen intents).
     * @param contentIntent what tapping the notification body does — the
     *   same destination as [fullScreenIntent], for when it was shown as
     *   heads-up.
     * @param dismissIntent dismisses every currently-ringing timer — see the
     *   class doc.
     * @param configuredDurationLabels every currently-ringing timer's
     *   original configured duration (e.g. "5:00"), formatted the same way
     *   as the Timer Ringing screen's own "5:00 timer" label, oldest-fired
     *   first — empty for the placeholder notification `TimerRingingService`
     *   posts via `startForeground()` before it has loaded any of them, same
     *   reasoning as
     *   `imb.tzalarmclock.alarm.ringing.RingingNotifications.build`'s
     *   placeholder `Alarm`.
     */
    fun build(
        context: Context,
        fullScreenIntent: PendingIntent,
        contentIntent: PendingIntent,
        dismissIntent: PendingIntent,
        configuredDurationLabels: List<String> = emptyList(),
    ): Notification {
        ensureChannel(context)
        val contentText = when (configuredDurationLabels.size) {
            0 -> context.getString(R.string.timer_fired_text)
            1 -> context.getString(R.string.timer_fired_text_with_duration, configuredDurationLabels.single())
            else -> context.getString(
                R.string.timer_fired_text_multiple,
                configuredDurationLabels.size,
                configuredDurationLabels.joinToString(", "),
            )
        }
        val dismissActionLabel = if (configuredDurationLabels.size > 1) {
            R.string.timer_dismiss_all_action
        } else {
            R.string.timer_dismiss_action
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.timer_channel_name))
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(dismissActionLabel), dismissIntent)
            .build()
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
