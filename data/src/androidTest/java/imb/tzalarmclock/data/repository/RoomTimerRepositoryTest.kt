package imb.tzalarmclock.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import imb.tzalarmclock.data.db.TzAlarmClockDatabase
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.repository.TimerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/** CRUD and state round-trips through Room, against an in-memory database. */
@RunWith(AndroidJUnit4::class)
class RoomTimerRepositoryTest {

    private lateinit var database: TzAlarmClockDatabase
    private lateinit var repository: TimerRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TzAlarmClockDatabase::class.java,
        ).build()
        repository = RoomTimerRepository(database.timerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingANewTimerAssignsAnIdAndReadsBackUnchanged() = runTest {
        val timer = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochMilli(1_000))

        val id = repository.save(timer)

        assertTrue("insert should assign a real id", id != Timer.NO_ID)
        assertEquals(timer.copy(id = id), repository.getTimer(id))
    }

    @Test
    fun everyStateSurvivesARoundTrip() = runTest {
        val base = Timer(configuredDuration = Duration.ofMinutes(3), createdAt = Instant.ofEpochMilli(1_000))
        val cases = listOf(
            base.copy(state = TimerState.STOPPED),
            base.copy(state = TimerState.RUNNING, endInstant = Instant.ofEpochMilli(9_000)),
            base.copy(state = TimerState.PAUSED, remainingAtPause = Duration.ofSeconds(42)),
            base.copy(state = TimerState.EXPIRED),
        )

        for (timer in cases) {
            val id = repository.save(timer)
            assertEquals(timer.copy(id = id), repository.getTimer(id))
        }
    }

    @Test
    fun savingAnExistingTimerUpdatesItInPlace() = runTest {
        val id = repository.save(Timer(configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochMilli(1_000)))
        val edited = repository.getTimer(id)!!.copy(
            state = TimerState.RUNNING,
            endInstant = Instant.ofEpochMilli(20_000),
        )

        val returnedId = repository.save(edited)

        assertEquals(id, returnedId)
        assertEquals(edited, repository.getTimer(id))
        assertEquals(1, repository.observeTimers().first().size)
    }

    @Test
    fun onlyRunningTimersAreReturnedForScheduling() = runTest {
        val runningId = repository.save(
            Timer(
                configuredDuration = Duration.ofMinutes(1),
                state = TimerState.RUNNING,
                endInstant = Instant.ofEpochMilli(9_000),
                createdAt = Instant.ofEpochMilli(1_000),
            ),
        )
        repository.save(Timer(configuredDuration = Duration.ofMinutes(1), createdAt = Instant.ofEpochMilli(1_000)))

        val running = repository.getRunningTimers()

        assertEquals(listOf(runningId), running.map { it.id })
    }

    @Test
    fun deletingATimerRemovesIt() = runTest {
        val id = repository.save(Timer(configuredDuration = Duration.ofMinutes(1), createdAt = Instant.ofEpochMilli(1_000)))

        repository.delete(id)

        assertNull(repository.getTimer(id))
        assertTrue(repository.observeTimers().first().isEmpty())
    }

    @Test
    fun observingTheListReflectsInsertsUpdatesAndDeletes() = runTest {
        assertTrue(repository.observeTimers().first().isEmpty())

        val id = repository.save(Timer(configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochMilli(1_000)))
        assertEquals(1, repository.observeTimers().first().size)

        repository.save(repository.getTimer(id)!!.copy(state = TimerState.EXPIRED))
        assertEquals(TimerState.EXPIRED, repository.observeTimers().first().single().state)

        repository.delete(id)
        assertTrue(repository.observeTimers().first().isEmpty())
    }

    @Test
    fun observingASingleTimerEmitsNullOnceItIsDeleted() = runTest {
        val id = repository.save(Timer(configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochMilli(1_000)))
        assertEquals(id, repository.observeTimer(id).first()?.id)

        repository.delete(id)

        assertNull(repository.observeTimer(id).first())
    }
}
