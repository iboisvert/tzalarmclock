package imb.tzalarmclock.data.db

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AlarmMappingTest {

    @Test
    fun `round-trips an alarm defined by time alone`() {
        val alarm = Alarm(id = 1, name = "Wake up", time = LocalTime.of(6, 45))

        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `round-trips a one-time dated alarm`() {
        val alarm = Alarm(
            id = 2,
            name = "Flight",
            time = LocalTime.of(4, 30),
            zone = ZoneId.of("Europe/Paris"),
            schedule = AlarmSchedule.OnDate(LocalDate.of(2026, 12, 25)),
        )

        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `round-trips a weekly alarm`() {
        val alarm = Alarm(
            id = 3,
            name = "Gym",
            time = LocalTime.of(9, 0),
            schedule = AlarmSchedule.Weekly(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        )

        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `round-trips a monthly alarm`() {
        val alarm = Alarm(
            id = 4,
            name = "Rent",
            time = LocalTime.of(13, 0),
            schedule = AlarmSchedule.Monthly(setOf(1, 15)),
            enabled = false,
            ringtoneUri = "content://media/internal/audio/media/42",
            vibrate = false,
        )

        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `weekday bitmask uses bit 0 for Monday and bit 6 for Sunday`() {
        val monday = Alarm(time = LocalTime.MIDNIGHT, schedule = weeklyOf(DayOfWeek.MONDAY))
        val sunday = Alarm(time = LocalTime.MIDNIGHT, schedule = weeklyOf(DayOfWeek.SUNDAY))

        assertEquals(0b000_0001, monday.toEntity().scheduleWeekdays)
        assertEquals(0b100_0000, sunday.toEntity().scheduleWeekdays)
    }

    @Test
    fun `weekdays come back in calendar order regardless of insertion order`() {
        val schedule = weeklyOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)
        val alarm = Alarm(time = LocalTime.NOON, schedule = schedule)

        val restored = alarm.toEntity().toDomain().schedule as AlarmSchedule.Weekly

        assertEquals(
            listOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
            restored.days.toList(),
        )
    }

    @Test
    fun `days of month are stored sorted ascending`() {
        val alarm = Alarm(
            time = LocalTime.NOON,
            schedule = AlarmSchedule.Monthly(linkedSetOf(28, 3, 15)),
        )

        assertEquals("3,15,28", alarm.toEntity().scheduleMonthDays)
    }

    @Test
    fun `only the payload column for the schedule type is populated`() {
        val entity = Alarm(
            time = LocalTime.of(8, 0),
            schedule = AlarmSchedule.Weekly(setOf(DayOfWeek.THURSDAY)),
        ).toEntity()

        assertEquals(ScheduleType.WEEKLY.name, entity.scheduleType)
        assertNull(entity.scheduleDate)
        assertNull(entity.scheduleMonthDays)
    }

    @Test
    fun `sub-minute precision is dropped, matching minute-granular alarms`() {
        val alarm = Alarm(time = LocalTime.of(7, 30, 59))

        assertEquals(LocalTime.of(7, 30), alarm.toEntity().toDomain().time)
    }

    @Test
    fun `minute of day covers both ends of the day`() {
        assertEquals(0, Alarm(time = LocalTime.MIDNIGHT).toEntity().minuteOfDay)
        assertEquals(1439, Alarm(time = LocalTime.of(23, 59)).toEntity().minuteOfDay)
    }

    @Test
    fun `an alarm whose zone the platform no longer knows degrades to floating`() {
        val entity = Alarm(time = LocalTime.of(7, 0), zone = ZoneId.of("America/Toronto"))
            .toEntity()
            .copy(zoneId = "Mars/Olympus_Mons")

        assertNull(entity.toDomain().zone)
    }

    @Test(expected = IllegalStateException::class)
    fun `a dated alarm with no stored date is rejected rather than silently defaulted`() {
        AlarmEntity(
            name = "Corrupt",
            minuteOfDay = 420,
            zoneId = null,
            scheduleType = ScheduleType.ONE_TIME_DATE.name,
            enabled = true,
        ).toDomain()
    }

    @Test(expected = IllegalStateException::class)
    fun `a weekly alarm with no stored mask is rejected`() {
        AlarmEntity(
            name = "Corrupt",
            minuteOfDay = 420,
            zoneId = null,
            scheduleType = ScheduleType.WEEKLY.name,
            enabled = true,
        ).toDomain()
    }

    private fun weeklyOf(vararg days: DayOfWeek) = AlarmSchedule.Weekly(linkedSetOf(*days))
}
