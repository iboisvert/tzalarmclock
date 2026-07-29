package imb.tzalarmclock.alarm.schedule

import android.app.AlarmManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers the half of the scheduler that `AlarmArmingTest` can't: that a plan
 * actually reaches `AlarmManager`, and — the part with real bug potential —
 * that alarms which drop out of the plan get cancelled rather than left armed.
 *
 * Presence is asserted through [AlarmIntents.existing], which is a
 * `FLAG_NO_CREATE` lookup and so answers "does the OS still hold a pending
 * intent for this alarm?" without creating one.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAlarmSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val now = ZonedDateTime.of(
        LocalDate.of(2026, 7, 29),
        LocalTime.of(9, 0),
        ZoneId.of("America/Toronto"),
    )

    private lateinit var registry: ArmedAlarmRegistry

    private fun scheduler(repository: AlarmRepository = FakeAlarmRepository()) =
        AndroidAlarmScheduler(context, repository, registry) { now }

    private fun alarm(id: Long, enabled: Boolean = true) = Alarm(
        id = id,
        time = LocalTime.of(23, 0),
        schedule = AlarmSchedule.NextOccurrence,
        enabled = enabled,
    )

    @Before
    fun clearPreviouslyArmedAlarms() = runTest {
        registry = ArmedAlarmRegistry(context)
        // Other tests in this class leave alarms armed in the OS, and pending
        // intents outlive the process, so start from a known-empty state.
        scheduler().sync(emptyList())
    }

    @Test
    fun armsEveryEnabledAlarm() = runTest {
        scheduler().sync(listOf(alarm(1), alarm(2)))

        assertNotNull(AlarmIntents.existing(context, 1))
        assertNotNull(AlarmIntents.existing(context, 2))
        assertEquals(setOf(1L, 2L), registry.armedIds())
    }

    @Test
    fun doesNotArmDisabledAlarms() = runTest {
        scheduler().sync(listOf(alarm(1, enabled = false)))

        assertNull(AlarmIntents.existing(context, 1))
        assertEquals(emptySet<Long>(), registry.armedIds())
    }

    @Test
    fun cancelsAlarmsThatLeaveThePlan() = runTest {
        scheduler().sync(listOf(alarm(1), alarm(2)))

        scheduler().sync(listOf(alarm(1)))

        assertNotNull(AlarmIntents.existing(context, 1))
        assertNull(AlarmIntents.existing(context, 2))
        assertEquals(setOf(1L), registry.armedIds())
    }

    @Test
    fun cancelsAlarmsThatWereDisabled() = runTest {
        scheduler().sync(listOf(alarm(1)))

        scheduler().sync(listOf(alarm(1, enabled = false)))

        assertNull(AlarmIntents.existing(context, 1))
    }

    @Test
    fun cancelsUsingTheRegistryAfterAProcessRestart() = runTest {
        scheduler().sync(listOf(alarm(1)))

        // A fresh scheduler with a fresh registry stands in for the process
        // having been killed between the two syncs: only what was persisted
        // tells the new instance that alarm 1 is still armed in the OS.
        AndroidAlarmScheduler(context, FakeAlarmRepository(), ArmedAlarmRegistry(context)) { now }
            .sync(emptyList())

        assertNull(AlarmIntents.existing(context, 1))
    }

    @Test
    fun theArmedInstantReachesTheSystemAlarmClock() = runTest {
        scheduler().sync(listOf(alarm(1)))

        // getNextAlarmClock() is the OS's own view, so this asserts the alarm
        // was really registered at the right instant rather than that we
        // merely built a pending intent for it.
        val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock
        val expected = armingPlan(listOf(alarm(1)), now).single().triggerAtMillis

        assertNotNull("No alarm clock registered with the system", next)
        assertEquals(expected, next!!.triggerTime)
    }

    @Test
    fun syncAllReadsEnabledAlarmsFromStorage() = runTest {
        val repository = FakeAlarmRepository(listOf(alarm(7), alarm(8, enabled = false)))

        scheduler(repository).syncAll()

        assertNotNull(AlarmIntents.existing(context, 7))
        assertNull(AlarmIntents.existing(context, 8))
    }

    /** Only the two reads the scheduler makes are meaningful here. */
    private class FakeAlarmRepository(
        private val alarms: List<Alarm> = emptyList(),
    ) : AlarmRepository {

        override suspend fun getEnabledAlarms(): List<Alarm> = alarms.filter { it.enabled }

        override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms)

        override fun observeAlarm(id: Long): Flow<Alarm?> = flowOf(alarms.find { it.id == id })

        override suspend fun getAlarm(id: Long): Alarm? = alarms.find { it.id == id }

        override suspend fun save(alarm: Alarm): Long = alarm.id

        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit

        override suspend fun delete(id: Long) = Unit
    }
}
