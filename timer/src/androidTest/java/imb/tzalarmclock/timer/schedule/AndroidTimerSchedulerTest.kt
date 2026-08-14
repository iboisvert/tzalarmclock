package imb.tzalarmclock.timer.schedule

import android.app.AlarmManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.repository.TimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Covers the half of the scheduler that `TimerArmingTest` can't: that a plan
 * actually reaches `AlarmManager`, and that timers which drop out of the
 * plan get cancelled rather than left armed. Mirrors
 * `imb.tzalarmclock.alarm.schedule.AndroidAlarmSchedulerTest`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTimerSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var registry: ArmedTimerRegistry

    private fun scheduler(repository: TimerRepository = FakeTimerRepository()) =
        AndroidTimerScheduler(context, repository, registry)

    private fun timer(id: Long, state: TimerState = TimerState.RUNNING, endInstant: Instant? = FAR_FUTURE) = Timer(
        id = id,
        configuredDuration = Duration.ofMinutes(5),
        state = state,
        endInstant = endInstant,
        createdAt = Instant.ofEpochMilli(1_000),
    )

    @Before
    fun clearPreviouslyArmedTimers() = runTest {
        registry = ArmedTimerRegistry(context)
        // Other tests in this class leave timers armed in the OS, and pending
        // intents outlive the process, so start from a known-empty state.
        scheduler().sync(emptyList())
    }

    @Test
    fun armsEveryRunningTimer() = runTest {
        scheduler().sync(listOf(timer(1), timer(2)))

        assertNotNull(TimerIntents.existing(context, 1))
        assertNotNull(TimerIntents.existing(context, 2))
        assertEquals(setOf(1L, 2L), registry.armedIds())
    }

    @Test
    fun doesNotArmStoppedTimers() = runTest {
        scheduler().sync(listOf(timer(1, state = TimerState.STOPPED, endInstant = null)))

        assertNull(TimerIntents.existing(context, 1))
        assertEquals(emptySet<Long>(), registry.armedIds())
    }

    @Test
    fun cancelsTimersThatLeaveThePlan() = runTest {
        scheduler().sync(listOf(timer(1), timer(2)))

        scheduler().sync(listOf(timer(1)))

        assertNotNull(TimerIntents.existing(context, 1))
        assertNull(TimerIntents.existing(context, 2))
        assertEquals(setOf(1L), registry.armedIds())
    }

    @Test
    fun cancelsTimersThatWerePaused() = runTest {
        scheduler().sync(listOf(timer(1)))

        scheduler().sync(listOf(timer(1, state = TimerState.PAUSED, endInstant = null)))

        assertNull(TimerIntents.existing(context, 1))
    }

    @Test
    fun cancelsUsingTheRegistryAfterAProcessRestart() = runTest {
        scheduler().sync(listOf(timer(1)))

        // A fresh scheduler with a fresh registry stands in for the process
        // having been killed between the two syncs.
        AndroidTimerScheduler(context, FakeTimerRepository(), ArmedTimerRegistry(context)).sync(emptyList())

        assertNull(TimerIntents.existing(context, 1))
    }

    @Test
    fun theArmedInstantReachesTheSystemAlarmClock() = runTest {
        val endInstant = Instant.ofEpochMilli(System.currentTimeMillis() + Duration.ofDays(1).toMillis())
        scheduler().sync(listOf(timer(1, endInstant = endInstant)))

        val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock

        assertNotNull("No alarm clock registered with the system", next)
        assertEquals(endInstant.toEpochMilli(), next!!.triggerTime)
    }

    @Test
    fun syncAllReadsRunningTimersFromStorage() = runTest {
        val repository = FakeTimerRepository(
            listOf(timer(7), timer(8, state = TimerState.STOPPED, endInstant = null)),
        )

        scheduler(repository).syncAll()

        assertNotNull(TimerIntents.existing(context, 7))
        assertNull(TimerIntents.existing(context, 8))
    }

    /** Only the two reads the scheduler makes are meaningful here. */
    private class FakeTimerRepository(
        private val timers: List<Timer> = emptyList(),
    ) : TimerRepository {

        override suspend fun getRunningTimers(): List<Timer> = timers.filter { it.state == TimerState.RUNNING }

        override fun observeTimers(): Flow<List<Timer>> = flowOf(timers)

        override fun observeTimer(id: Long): Flow<Timer?> = flowOf(timers.find { it.id == id })

        override suspend fun getTimer(id: Long): Timer? = timers.find { it.id == id }

        override suspend fun save(timer: Timer): Long = timer.id

        override suspend fun delete(id: Long) = Unit
    }

    private companion object {
        val FAR_FUTURE: Instant = Instant.ofEpochMilli(System.currentTimeMillis() + Duration.ofDays(1).toMillis())
    }
}
