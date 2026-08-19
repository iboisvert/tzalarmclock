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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
 *
 * The ringing notification itself is shared across every currently-ringing
 * alarm (see [RINGING_NOTIFICATION_ID]) — its Snooze/Dismiss buttons live on
 * the Ring screen, not as notification actions, and both now apply to every
 * ringing alarm together. The snoozed/canceled notifications stay per-alarm:
 * each represents one specific alarm's own post-ring status, so multiple of
 * them showing at once (one per alarm) is correct, unlike the ringing state.
 */
object RingingNotifications {

    const val CHANNEL_ID = "alarm_ringing_v2"

    /**
     * Fixed rather than derived from an alarm id (contrast [notificationId],
     * still used for the per-alarm snoozed/canceled notifications): this one
     * represents *every* currently-ringing alarm, so posting under a stable
     * id is what makes a second alarm firing while the first still rings
     * update the existing notification in place instead of stacking a second
     * one beside it.
     */
    val RINGING_NOTIFICATION_ID: Int = "alarm_ringing_group".hashCode()

    /**
     * Separate from [CHANNEL_ID]: a snoozed alarm is an informational, ignorable
     * state (unlike an actively-ringing one), so it gets its own lower-importance
     * channel rather than reusing the high-importance ringing one.
     */
    const val SNOOZED_CHANNEL_ID = "alarm_snoozed_v1"

    /**
     * Makes the snoozed notification a *separate* notification from the ringing
     * one rather than a replacement posted under the same id.
     *
     * This is what keeps a snooze re-fire visible over the lock screen. SystemUI
     * only launches a [Notification.Builder.setFullScreenIntent] when the
     * notification is **added** to its collection; updating one already posted
     * under the same key silently skips the launch. Since the snoozed
     * notification outlives its service (see [RingingService]'s snooze
     * handling), re-posting the ringing notification under that same key on the
     * re-fire read as an update, and the ringing screen never appeared — the
     * user got a notification and had to unlock the device to reach the alarm.
     * A tag makes the two notifications distinct keys, so the re-fire is an add.
     *
     * Only the snoozed notification is tagged: the ringing one is a foreground
     * service notification, and `startForeground` takes an id with no tag.
     */
    const val SNOOZED_TAG = "snoozed"

    /**
     * Separate from [CHANNEL_ID] and [SNOOZED_CHANNEL_ID] for the same reason
     * as the snoozed channel: "this alarm was canceled" is informational, not
     * something demanding the ringing channel's urgency.
     */
    const val CANCELED_CHANNEL_ID = "alarm_canceled_v1"

    /** Distinct key from both [SNOOZED_TAG] and the untagged ringing notification. */
    const val CANCELED_TAG = "canceled"

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

    fun ensureSnoozedChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SNOOZED_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            SNOOZED_CHANNEL_ID,
            context.getString(R.string.alarm_snoozed_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alarm_snoozed_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun ensureCanceledChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CANCELED_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CANCELED_CHANNEL_ID,
            context.getString(R.string.alarm_canceled_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alarm_canceled_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the ongoing "alarm ringing" notification for every
     * currently-ringing alarm together.
     *
     * @param alarms every alarm currently ringing, oldest-fired first — empty
     *   for the placeholder notification `RingingService` posts via
     *   `startForeground()` before it has loaded the first one.
     * @param fullScreenIntent launches the ringing activity over the lock
     *   screen (or as a heads-up notification if the device declines
     *   full-screen intents, e.g. `USE_FULL_SCREEN_INTENT` revoked by the user).
     * @param contentIntent what tapping the notification body does — the same
     *   destination as [fullScreenIntent], for when it was shown as heads-up.
     */
    fun build(
        context: Context,
        alarms: List<Alarm>,
        fullScreenIntent: PendingIntent,
        contentIntent: PendingIntent,
    ): Notification {
        ensureChannel(context)
        fun displayName(alarm: Alarm) = alarm.name.ifBlank { context.getString(R.string.alarm_default_name) }
        val title = if (alarms.size == 1) displayName(alarms.single()) else context.getString(R.string.alarm_channel_name)
        val text = when (alarms.size) {
            0, 1 -> context.getString(R.string.alarm_fired_text)
            else -> context.getString(
                R.string.alarm_fired_text_multiple,
                alarms.size,
                alarms.joinToString(", ") { displayName(it) },
            )
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(contentIntent)
            .build()
    }

    /**
     * Builds the "alarm snoozed" notification for [alarm].
     *
     * Not ongoing, unlike the ringing notification: swiping it away just hides
     * it, since the snooze itself stays armed with `AlarmManager` (via
     * `SnoozeRegistry`) independently of whether this notification is visible
     * — an accidental swipe must never silently cancel the snooze the way an
     * accidental tap must never dismiss a ringing alarm.
     *
     * @param dismissIntent triggers [RingingService]'s dismiss handling
     *   directly, the same as the Ringing screen's own hold-to-dismiss gesture,
     *   so the user isn't forced to wait for the alarm to ring again just to
     *   turn it off.
     * @param contentIntent what tapping the notification body (rather than the
     *   Dismiss action) does — opens the app.
     */
    fun buildSnoozed(
        context: Context,
        alarm: Alarm,
        snoozedUntil: Instant,
        use24HourFormat: Boolean,
        dismissIntent: PendingIntent,
        contentIntent: PendingIntent?,
    ): Notification {
        ensureSnoozedChannel(context)
        val untilLabel = snoozedUntil.atZone(ZoneId.systemDefault()).format(timeFormatter(use24HourFormat))
        return NotificationCompat.Builder(context, SNOOZED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.name.ifBlank { context.getString(R.string.alarm_default_name) })
            .setContentText(context.getString(R.string.alarm_snoozed_text, untilLabel))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.alarm_snoozed_dismiss_action),
                    dismissIntent,
                ).build(),
            )
            .build()
    }

    /**
     * Builds the "alarm canceled" notification for [alarm]: shown when
     * [RingingService] auto-dismisses a ring nobody acknowledged within
     * `AppSettings.ringTimeoutMinutes` *and* there's no snooze left to give
     * instead (an unacknowledged ring with a snooze still available auto-
     * snoozes instead — silently, from this notification's point of view,
     * since that already posts its own "Snoozed until…" notification). So
     * the user finds out about a real cancellation they weren't there to see,
     * rather than assuming the alarm is still armed.
     *
     * Not ongoing, and auto-cancels on tap — same reasoning as the snoozed
     * notification, just with nothing left armed to accidentally clear: by
     * the time this posts, the ring cycle is already over.
     */
    fun buildCanceled(
        context: Context,
        alarm: Alarm,
        use24HourFormat: Boolean,
        contentIntent: PendingIntent?,
    ): Notification {
        ensureCanceledChannel(context)
        val timeLabel = alarm.time.format(timeFormatter(use24HourFormat))
        return NotificationCompat.Builder(context, CANCELED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.name.ifBlank { context.getString(R.string.alarm_default_name) })
            .setContentText(context.getString(R.string.alarm_canceled_text, timeLabel))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    /** One id per alarm for its snoozed/canceled notifications, so two don't collide. */
    fun notificationId(alarmId: Long): Int = alarmId.hashCode()

    /** Clears the shared ringing notification — see [RINGING_NOTIFICATION_ID]. */
    fun cancelRinging(context: Context) {
        NotificationManagerCompat.from(context).cancel(RINGING_NOTIFICATION_ID)
    }

    /**
     * Clears only [alarmId]'s snoozed notification, leaving a ringing one alone.
     *
     * Called as the alarm rings again: "snoozed until 07:10" is stale the moment
     * that instant arrives, and leaving it posted would stack it under the
     * ringing notification.
     */
    fun cancelSnoozed(context: Context, alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(SNOOZED_TAG, notificationId(alarmId))
    }

    private fun timeFormatter(use24HourFormat: Boolean): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (use24HourFormat) "HH:mm" else "h:mm a", Locale.getDefault())
}
