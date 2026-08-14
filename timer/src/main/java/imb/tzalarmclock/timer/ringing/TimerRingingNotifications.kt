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
 * service.
 *
 * Stage 15 scope only: a single ringing notification with a Dismiss action,
 * no full-screen intent yet (that needs `TimerRingingActivity`, which Stage
 * 17 adds in the `app` module) and no separate snoozed/canceled channels,
 * since timers have neither concept.
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

    fun build(context: Context, dismissIntent: PendingIntent): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.timer_channel_name))
            .setContentText(context.getString(R.string.timer_fired_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.timer_dismiss_action), dismissIntent)
            .build()
    }

    fun notificationId(timerId: Long): Int = timerId.hashCode()

    fun cancel(context: Context, timerId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(timerId))
    }
}
