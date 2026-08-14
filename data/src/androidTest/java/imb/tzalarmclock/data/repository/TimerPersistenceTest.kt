package imb.tzalarmclock.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import imb.tzalarmclock.data.db.TzAlarmClockDatabase
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * Stage 13 exit criterion: timer data survives an app restart.
 *
 * Mirrors [AlarmPersistenceTest]: an on-disk database, closed between writing
 * and reading, which is what the app's process being killed and restarted
 * looks like to Room.
 */
@RunWith(AndroidJUnit4::class)
class TimerPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun aRunningTimerWrittenBeforeARestartIsStillThereAfterwards() = runTest {
        val timer = Timer(
            configuredDuration = Duration.ofMinutes(5),
            state = TimerState.RUNNING,
            endInstant = Instant.ofEpochMilli(60_000),
            createdAt = Instant.ofEpochMilli(1_000),
        )

        val id = openDatabase().use { database ->
            RoomTimerRepository(database.timerDao()).save(timer)
        }

        val restored = openDatabase().use { database ->
            RoomTimerRepository(database.timerDao()).getTimer(id)
        }

        assertEquals(timer.copy(id = id), restored)
    }

    @Test
    fun editsMadeAfterARestartPersistAcrossTheNextOne() = runTest {
        val id = openDatabase().use { database ->
            RoomTimerRepository(database.timerDao())
                .save(Timer(configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochMilli(1_000)))
        }

        openDatabase().use { database ->
            val repository = RoomTimerRepository(database.timerDao())
            repository.save(
                repository.getTimer(id)!!.copy(
                    state = TimerState.PAUSED,
                    remainingAtPause = Duration.ofSeconds(90),
                ),
            )
        }

        val restored = openDatabase().use { database ->
            RoomTimerRepository(database.timerDao()).getTimer(id)
        }

        assertEquals(TimerState.PAUSED, restored?.state)
        assertEquals(Duration.ofSeconds(90), restored?.remainingAtPause)
    }

    private fun openDatabase(): TzAlarmClockDatabase = Room.databaseBuilder(
        context,
        TzAlarmClockDatabase::class.java,
        DB_NAME,
    ).build()

    private inline fun <R> TzAlarmClockDatabase.use(block: (TzAlarmClockDatabase) -> R): R =
        try {
            block(this)
        } finally {
            close()
        }

    private companion object {
        const val DB_NAME = "timer-persistence-test.db"
    }
}
