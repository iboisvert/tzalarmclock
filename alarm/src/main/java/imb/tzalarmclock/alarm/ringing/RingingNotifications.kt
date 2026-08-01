package imb.tzalarmclock.alarm.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import imb.tzalarmclock.alarm.R
import imb.tzalarmclock.domain.model.Alarm

/**
 * The notification that keeps [RingingService] a valid foreground service and
 * (via [Notification.Builder.setFullScreenIntent]) launches the ringing
 * activity over the lock screen.
 *
 * Silent, unlike Stage 3's interim `alarm_fired_v1` channel: [RingingService]
 * owns playback and vibration directly, so it can honour a per-alarm ringtone,
 * volume escalation, and the vibration setting, none of which a channel-level
 * sound can do. Channel settings are immutable once created, hence the new
 * [CHANNEL_ID] rather than reusing the old one.
 */
object RingingNotifications {

    const val CHANNEL_ID = "alarm_ringing_v2"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the ongoing "alarm ringing" notification for [alarm].
     *
     * @param fullScreenIntent launches the ringing activity over the lock
     *   screen (or as a heads-up notification if the device declines
     *   full-screen intents, e.g. `USE_FULL_SCREEN_INTENT` revoked by the user).
     * @param contentIntent what tapping the notification body does — the same
     *   destination as [fullScreenIntent], for when it was shown as heads-up.
     */
    fun build(
        context: Context,
        alarm: Alarm,
        fullScreenIntent: PendingIntent,
        contentIntent: PendingIntent,
    ): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.name.ifBlank { context.getString(R.string.alarm_default_name) })
            .setContentText(context.getString(R.string.alarm_fired_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)
            .build()
    }

    /** One notification per alarm, so two ringing close together don't collide. */
    fun notificationId(alarmId: Long): Int = alarmId.hashCode()

    fun cancel(context: Context, alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(alarmId))
    }
}
