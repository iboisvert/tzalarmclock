package imb.tzalarmclock.alarm.receiver

import android.app.ActivityManager
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.alarm.schedule.AlarmIntents
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * The fire path, driven through a real broadcast rather than by calling
 * `onReceive` directly — the hand-off to [RingingService] is exactly the part
 * worth testing.
 *
 * The repository/scheduler side effects that used to happen here (retiring a
 * non-recurring alarm, re-arming a recurring one) now happen inside
 * [RingingService] on dismiss, not on fire — see `RingingServiceTest`.
 */
@RunWith(AndroidJUnit4::class)
class AlarmReceiverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alarms: AlarmRepository = DataProvider.alarmRepository(context)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val created = mutableListOf<Long>()

    @After
    fun cleanUp() = runBlocking {
        created.forEach {
            context.stopService(RingingService.dismissIntent(context, it))
            alarms.delete(it)
        }
    }

    private suspend fun save(schedule: AlarmSchedule = AlarmSchedule.NextOccurrence, enabled: Boolean = true): Long {
        val id = alarms.save(Alarm(time = LocalTime.of(7, 0), schedule = schedule, enabled = enabled))
        created += id
        return id
    }

    private fun fire(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
            data = "tzalarmclock://alarm/$alarmId".toUri()
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        context.sendBroadcast(intent)
    }

    // Not the ongoing notification's visibility: that also depends on the
    // POST_NOTIFICATIONS runtime permission being granted, which is a
    // separate, already-covered concern (SchedulingHealth) and isn't
    // guaranteed to be granted to this test's own package.
    private fun isRingingServiceRunning(): Boolean =
        activityManager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == RingingService::class.java.name }

    private suspend fun awaitRingingService() {
        withTimeout(TIMEOUT_MILLIS) {
            while (!isRingingServiceRunning()) delay(POLL_MILLIS)
        }
    }

    @Test
    fun firingAnEnabledAlarmStartsTheRingingService() = runBlocking {
        val id = save()

        fire(id)

        awaitRingingService()
        assertTrue(isRingingServiceRunning())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
