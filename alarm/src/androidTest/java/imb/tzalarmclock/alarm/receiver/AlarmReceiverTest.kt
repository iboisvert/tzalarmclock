package imb.tzalarmclock.alarm.receiver

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.alarm.schedule.AlarmIntents
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The fire path, driven through a real broadcast rather than by calling
 * `onReceive` directly — [android.content.BroadcastReceiver.goAsync] only works
 * inside an actual dispatch, and the asynchronous hand-off is exactly the part
 * worth testing.
 *
 * The distinction under test is the one that would otherwise loop forever: an
 * alarm defined by time alone must switch itself off once it has rung, because
 * "the next occurrence of 07:00" is always tomorrow.
 */
@RunWith(AndroidJUnit4::class)
class AlarmReceiverTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alarms: AlarmRepository = DataProvider.alarmRepository(context)
    private val created = mutableListOf<Long>()

    @After
    fun deleteCreatedAlarms() = runBlocking {
        created.forEach { alarms.delete(it) }
    }

    private suspend fun save(schedule: AlarmSchedule): Long {
        val id = alarms.save(
            Alarm(time = LocalTime.of(7, 0), schedule = schedule, enabled = true),
        )
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

    /**
     * The receiver finishes on a background dispatcher, so results are awaited.
     *
     * These tests use `runBlocking` rather than `runTest` precisely for this:
     * `runTest`'s virtual clock would skip the timeout instantly while the real
     * broadcast was still in flight.
     */
    private suspend fun awaitAlarm(id: Long, predicate: (Alarm?) -> Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            while (!predicate(alarms.getAlarm(id))) delay(POLL_MILLIS)
        }
    }

    @Test
    fun retiresAnAlarmThatWillNeverRingAgain() = runBlocking {
        val id = save(AlarmSchedule.NextOccurrence)

        fire(id)

        awaitAlarm(id) { it?.enabled == false }
        assertFalse(AlarmIntents.existing(context, id) != null)
    }

    @Test
    fun leavesARecurringAlarmEnabledAndArmsItAgain() = runBlocking {
        val id = save(AlarmSchedule.Weekly(DayOfWeek.entries.toSet()))

        fire(id)

        awaitAlarm(id) { it != null && AlarmIntents.existing(context, id) != null }
        assertTrue(alarms.getAlarm(id)!!.enabled)
        assertNotNull(AlarmIntents.existing(context, id))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
