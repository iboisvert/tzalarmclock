package imb.tzalarmclock.alarm.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import imb.tzalarmclock.alarm.R
import imb.tzalarmclock.alarm.permission.SchedulingHealth
import imb.tzalarmclock.domain.model.Alarm

/**
 * The notification that tells the user an alarm has gone off.
 *
 * **Interim by design.** Stage 6 replaces this with a full-screen-intent
 * notification driving a dedicated ringing activity and a foreground service
 * that owns playback (so it can honour per-alarm ringtone, volume escalation
 * and vibration). Until then this exists so Stage 3's own exit criteria —
 * "an alarm set for +2 minutes rings after a reboot / force-stop / zone change"
 * — is something you can actually stand in a room and verify.
 *
 * The channel therefore carries the sound itself. Stage 6 will want a silent
 * channel, since its service does the playing; because channel settings are
 * immutable once created it will need a new [CHANNEL_ID] rather than an edit to
 * this one.
 */
object AlarmNotifications {

    const val CHANNEL_ID = "alarm_fired_v1"

    /**
     * Creates the channel if it isn't there. Cheap and idempotent, so it is
     * called from every entry point rather than only from `Application`: a
     * broadcast receiver can be the thing that starts the process.
     */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            // USAGE_ALARM is what places this outside Do Not Disturb's reach
            // under the default "allow alarms" exception, which is the single
            // complaint this app exists to fix.
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the "alarm ringing" notification for [alarm].
     *
     * Silently does nothing when `POST_NOTIFICATIONS` was denied — a receiver
     * is the wrong place to ask for a permission, and the startup check already
     * warns about it.
     */
    fun notifyFired(context: Context, alarm: Alarm) {
        if (!SchedulingHealth.areNotificationsAllowed(context)) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.name.ifBlank { context.getString(R.string.alarm_default_name) })
            .setContentText(context.getString(R.string.alarm_fired_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(alarm.id), notification)
    }

    /**
     * One notification per alarm, so two alarms going off close together don't
     * overwrite each other.
     */
    private fun notificationId(alarmId: Long): Int = alarmId.hashCode()

    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val OPEN_APP_REQUEST_CODE = 2
}
