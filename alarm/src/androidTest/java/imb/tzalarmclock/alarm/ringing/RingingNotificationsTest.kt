package imb.tzalarmclock.alarm.ringing

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime

/**
 * Regression coverage for the #1 non-functional requirement (Stage 9): a
 * fired alarm must reach the user through Do Not Disturb / silent mode. That
 * guarantee rests on two Android mechanisms this test pins down so a future
 * change can't silently regress them: the ringing channel is high-importance
 * with `CATEGORY_ALARM`, and both it and the built notification opt into the
 * platform's alarm-bypass path. [RingingService]'s `MediaPlayer`/`Vibrator`
 * use `AudioAttributes`/`VibrationAttributes` with `USAGE_ALARM`, which is
 * the half of the bypass this test can't reach without actually playing
 * sound — that half is covered by the manual DND checklist instead.
 */
@RunWith(AndroidJUnit4::class)
class RingingNotificationsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val alarm = Alarm(name = "Test alarm", time = LocalTime.of(7, 0))

    private fun dummyPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(RingingNotificationsTest::class.java.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Test
    fun ringingChannelIsHighImportanceForDndBypass() {
        RingingNotifications.ensureChannel(context)

        val channel = manager.getNotificationChannel(RingingNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun ringingNotificationIsCategorizedAsAlarmAtMaxPriority() {
        val notification = RingingNotifications.build(
            context,
            listOf(alarm),
            dummyPendingIntent(1),
            dummyPendingIntent(2),
        )

        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
        assertEquals(NotificationCompat.PRIORITY_MAX, notification.priority)
        assertTrue(
            "ringing notification must stay ongoing",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }

    @Test
    fun snoozedChannelIsLowerImportanceThanTheRingingChannel() {
        RingingNotifications.ensureSnoozedChannel(context)

        val channel = manager.getNotificationChannel(RingingNotifications.SNOOZED_CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)

        val notification = RingingNotifications.buildSnoozed(
            context = context,
            alarm = alarm,
            snoozedUntil = Instant.now(),
            use24HourFormat = true,
            dismissIntent = dummyPendingIntent(3),
            contentIntent = null,
        )
        assertFalse(
            "an accidental swipe must not clear the notification-only state as ongoing",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }

    @Test
    fun canceledNotificationNamesTheAlarmAndItsTime() {
        RingingNotifications.ensureCanceledChannel(context)

        val channel = manager.getNotificationChannel(RingingNotifications.CANCELED_CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)

        val notification = RingingNotifications.buildCanceled(
            context = context,
            alarm = alarm.copy(time = LocalTime.of(7, 0)),
            use24HourFormat = true,
            contentIntent = null,
        )
        assertFalse(
            "an accidental swipe must not clear the canceled state as ongoing",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
        assertTrue(
            "must name the alarm's scheduled time so the user knows which ring this was",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("07:00"),
        )
    }

    /**
     * The snoozed and ringing notifications must stay two *separate*
     * notifications, cancelable independently of each other.
     *
     * They shared an id until this was fixed, which made a snooze re-fire post
     * the ringing notification on top of the still-posted snoozed one. SystemUI
     * launches a full-screen intent only when a notification is added to its
     * collection, never when one is updated, so the ringing screen silently
     * stopped appearing over the lock screen and the alarm could only be
     * reached by unlocking the device. The ringing notification is now shared
     * across every ringing alarm (see [RingingNotifications.RINGING_NOTIFICATION_ID])
     * rather than keyed per alarm, but the same distinct-key requirement holds.
     */
    @Test
    fun snoozedNotificationDoesNotReplaceTheRingingOne() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )
        val alarmId = 90_210L
        val snoozedId = RingingNotifications.notificationId(alarmId)
        RingingNotifications.cancelRinging(context)
        RingingNotifications.cancelSnoozed(context, alarmId)

        manager.notify(
            RingingNotifications.RINGING_NOTIFICATION_ID,
            RingingNotifications.build(context, listOf(alarm), dummyPendingIntent(4), dummyPendingIntent(5)),
        )
        manager.notify(
            RingingNotifications.SNOOZED_TAG,
            snoozedId,
            RingingNotifications.buildSnoozed(
                context = context,
                alarm = alarm,
                snoozedUntil = Instant.now(),
                use24HourFormat = true,
                dismissIntent = dummyPendingIntent(6),
                contentIntent = null,
            ),
        )

        awaitCondition { manager.activeNotifications.any { it.id == RingingNotifications.RINGING_NOTIFICATION_ID } }
        awaitCondition { manager.activeNotifications.any { it.id == snoozedId && it.tag == RingingNotifications.SNOOZED_TAG } }

        RingingNotifications.cancelSnoozed(context, alarmId)
        awaitCondition { manager.activeNotifications.none { it.tag == RingingNotifications.SNOOZED_TAG } }
        assertTrue(
            "cancelling only the snoozed notification must leave the ringing one alone",
            manager.activeNotifications.any { it.id == RingingNotifications.RINGING_NOTIFICATION_ID },
        )

        RingingNotifications.cancelRinging(context)
        awaitCondition { manager.activeNotifications.none { it.id == RingingNotifications.RINGING_NOTIFICATION_ID } }
    }

    /**
     * Polls until [condition] is true or the wait times out.
     *
     * Posting and cancelling are both handed to the notification service's
     * own handler thread, so [NotificationManager.getActiveNotifications] can
     * still report the previous state for a moment after the call returns.
     */
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
