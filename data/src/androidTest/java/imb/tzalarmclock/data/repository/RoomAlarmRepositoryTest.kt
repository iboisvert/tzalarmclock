package imb.tzalarmclock.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import imb.tzalarmclock.data.db.TzAlarmClockDatabase
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** CRUD round-trips through Room, against an in-memory database. */
@RunWith(AndroidJUnit4::class)
class RoomAlarmRepositoryTest {

    private lateinit var database: TzAlarmClockDatabase
    private lateinit var repository: AlarmRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TzAlarmClockDatabase::class.java,
        ).build()
        repository = RoomAlarmRepository(database.alarmDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingANewAlarmAssignsAnIdAndReadsBackUnchanged() = runTest {
        val alarm = Alarm(name = "Wake up", time = LocalTime.of(6, 45))

        val id = repository.save(alarm)

        assertTrue("insert should assign a real id", id != Alarm.NO_ID)
        assertEquals(alarm.copy(id = id), repository.getAlarm(id))
    }

    @Test
    fun everyScheduleTypeSurvivesARoundTrip() = runTest {
        val schedules = listOf(
            AlarmSchedule.NextOccurrence,
            AlarmSchedule.OnDate(LocalDate.of(2026, 12, 25)),
            AlarmSchedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            AlarmSchedule.Monthly(setOf(1, 15)),
        )

        for (schedule in schedules) {
            val id = repository.save(Alarm(time = LocalTime.of(9, 0), schedule = schedule))

            assertEquals(schedule, repository.getAlarm(id)?.schedule)
        }
    }

    @Test
    fun aZoneLockedAlarmKeepsItsZone() = runTest {
        val zone = ZoneId.of("Europe/Paris")

        val id = repository.save(Alarm(time = LocalTime.of(4, 30), zone = zone))

        assertEquals(zone, repository.getAlarm(id)?.zone)
    }

    @Test
    fun nullRingtoneAndVibrationAreStoredAsAbsentNotAsDefaults() = runTest {
        val id = repository.save(Alarm(time = LocalTime.of(7, 0)))

        val saved = repository.getAlarm(id)!!
        assertNull(saved.ringtoneUri)
        assertNull(saved.vibrate)
    }

    @Test
    fun alarmLevelRingtoneAndVibrationOverridesRoundTrip() = runTest {
        val id = repository.save(
            Alarm(
                time = LocalTime.of(7, 0),
                ringtoneUri = "content://media/internal/audio/media/42",
                vibrate = false,
            ),
        )

        val saved = repository.getAlarm(id)!!
        assertEquals("content://media/internal/audio/media/42", saved.ringtoneUri)
        assertEquals(false, saved.vibrate)
    }

    @Test
    fun savingAnExistingAlarmUpdatesItInPlace() = runTest {
        val id = repository.save(Alarm(name = "Wake up", time = LocalTime.of(6, 45)))
        val edited = repository.getAlarm(id)!!.copy(
            name = "Wake up later",
            time = LocalTime.of(7, 30),
            schedule = AlarmSchedule.Weekly(setOf(DayOfWeek.SATURDAY)),
        )

        val returnedId = repository.save(edited)

        assertEquals(id, returnedId)
        assertEquals(edited, repository.getAlarm(id))
        assertEquals(1, repository.observeAlarms().first().size)
    }

    @Test
    fun togglingEnabledLeavesTheRestOfTheAlarmAlone() = runTest {
        val id = repository.save(
            Alarm(
                name = "Gym",
                time = LocalTime.of(9, 0),
                schedule = AlarmSchedule.Monthly(setOf(1, 15)),
            ),
        )

        repository.setEnabled(id, enabled = false)

        val disabled = repository.getAlarm(id)!!
        assertFalse(disabled.enabled)
        assertEquals("Gym", disabled.name)
        assertEquals(AlarmSchedule.Monthly(setOf(1, 15)), disabled.schedule)

        repository.setEnabled(id, enabled = true)
        assertTrue(repository.getAlarm(id)!!.enabled)
    }

    @Test
    fun onlyEnabledAlarmsAreReturnedForScheduling() = runTest {
        val enabledId = repository.save(Alarm(name = "On", time = LocalTime.of(6, 0)))
        repository.save(Alarm(name = "Off", time = LocalTime.of(7, 0), enabled = false))

        val enabled = repository.getEnabledAlarms()

        assertEquals(listOf(enabledId), enabled.map { it.id })
    }

    @Test
    fun deletingAnAlarmRemovesIt() = runTest {
        val id = repository.save(Alarm(time = LocalTime.of(6, 0)))

        repository.delete(id)

        assertNull(repository.getAlarm(id))
        assertTrue(repository.observeAlarms().first().isEmpty())
    }

    @Test
    fun deletingAnAlarmThatIsNotThereIsNotAnError() = runTest {
        repository.delete(id = 404L)

        assertTrue(repository.observeAlarms().first().isEmpty())
    }

    @Test
    fun observingTheListReflectsInsertsUpdatesAndDeletes() = runTest {
        assertTrue(repository.observeAlarms().first().isEmpty())

        val id = repository.save(Alarm(name = "Wake up", time = LocalTime.of(6, 45)))
        assertEquals(listOf("Wake up"), repository.observeAlarms().first().map { it.name })

        repository.save(repository.getAlarm(id)!!.copy(name = "Renamed"))
        assertEquals(listOf("Renamed"), repository.observeAlarms().first().map { it.name })

        repository.delete(id)
        assertTrue(repository.observeAlarms().first().isEmpty())
    }

    @Test
    fun observingASingleAlarmEmitsNullOnceItIsDeleted() = runTest {
        val id = repository.save(Alarm(name = "Wake up", time = LocalTime.of(6, 45)))
        assertEquals("Wake up", repository.observeAlarm(id).first()?.name)

        repository.delete(id)

        assertNull(repository.observeAlarm(id).first())
    }
}
