package imb.tzalarmclock.timer.ringing

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 19 DND-bypass audit, mirroring
 * `imb.tzalarmclock.alarm.ringing.RingingNotificationsTest`: pins the two
 * Android mechanisms the #1 non-functional requirement rests on for the
 * timer ringing channel — high importance and `CATEGORY_ALARM` at max
 * priority. [TimerRingingService]'s `MediaPlayer`/`Vibrator` use
 * `AudioAttributes`/`VibrationAttributes` with `USAGE_ALARM`, which is the
 * half this test can't reach without actually playing sound.
 */
@RunWith(AndroidJUnit4::class)
class TimerRingingNotificationsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun dummyPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(TimerRingingNotificationsTest::class.java.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Test
    fun ringingChannelIsHighImportanceForDndBypass() {
        TimerRingingNotifications.ensureChannel(context)

        val channel = manager.getNotificationChannel(TimerRingingNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun ringingNotificationIsCategorizedAsAlarmAtMaxPriority() {
        val notification = TimerRingingNotifications.build(
            context,
            dummyPendingIntent(1),
            dummyPendingIntent(2),
            dummyPendingIntent(3),
        )

        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
        assertEquals(NotificationCompat.PRIORITY_MAX, notification.priority)
        assertTrue(
            "ringing notification must stay ongoing",
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }
}
