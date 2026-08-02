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
            alarm,
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
}
