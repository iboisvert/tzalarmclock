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
 * separate snoozed/canceled channels — timers have neither concept.
 */
object TimerRingingNotifications {

    const val CHANNEL_ID = "timer_ringing_v1"

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
     * @param configuredDurationLabel the fired timer's original configured
     *   duration (e.g. "5:00"), formatted the same way as the Timer Ringing
     *   screen's own "5:00 timer" label — `null` for the placeholder
     *   notification `TimerRingingService` posts via `startForeground()`
     *   before it has loaded the timer, same reasoning as
     *   `imb.tzalarmclock.alarm.ringing.RingingNotifications.build`'s
     *   placeholder `Alarm`.
     */
    fun build(
        context: Context,
        fullScreenIntent: PendingIntent,
        contentIntent: PendingIntent,
        dismissIntent: PendingIntent,
        configuredDurationLabel: String? = null,
    ): Notification {
        ensureChannel(context)
        val contentText = if (configuredDurationLabel != null) {
            context.getString(R.string.timer_fired_text_with_duration, configuredDurationLabel)
        } else {
            context.getString(R.string.timer_fired_text)
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
            .addAction(0, context.getString(R.string.timer_dismiss_action), dismissIntent)
            .build()
    }

    fun notificationId(timerId: Long): Int = timerId.hashCode()

    fun cancel(context: Context, timerId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(timerId))
    }
}
