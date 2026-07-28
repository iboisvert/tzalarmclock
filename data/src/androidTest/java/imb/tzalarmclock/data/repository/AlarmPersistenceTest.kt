package imb.tzalarmclock.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import imb.tzalarmclock.data.db.TzAlarmClockDatabase
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/**
 * Stage 1 exit criterion: alarms survive an app restart.
 *
 * Uses an on-disk database and closes it between writing and reading, which is
 * what the app's process being killed and restarted looks like to Room.
 */
@RunWith(AndroidJUnit4::class)
class AlarmPersistenceTest {

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
    fun alarmsWrittenBeforeARestartAreStillThereAfterwards() = runTest {
        val alarm = Alarm(
            name = "Flight to Toronto",
            time = LocalTime.of(4, 30),
            zone = ZoneId.of("Europe/Paris"),
            schedule = AlarmSchedule.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            enabled = false,
            ringtoneUri = "content://media/internal/audio/media/7",
            vibrate = true,
        )

        val id = openDatabase().use { database ->
            RoomAlarmRepository(database.alarmDao()).save(alarm)
        }

        val restored = openDatabase().use { database ->
            RoomAlarmRepository(database.alarmDao()).getAlarm(id)
        }

        assertEquals(alarm.copy(id = id), restored)
    }

    @Test
    fun editsMadeAfterARestartPersistAcrossTheNextOne() = runTest {
        val id = openDatabase().use { database ->
            RoomAlarmRepository(database.alarmDao()).save(Alarm(time = LocalTime.of(6, 0)))
        }

        openDatabase().use { database ->
            val repository = RoomAlarmRepository(database.alarmDao())
            repository.save(repository.getAlarm(id)!!.copy(name = "Renamed after restart"))
        }

        val restored = openDatabase().use { database ->
            RoomAlarmRepository(database.alarmDao()).getAlarm(id)
        }

        assertEquals("Renamed after restart", restored?.name)
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
        const val DB_NAME = "alarm-persistence-test.db"
    }
}
