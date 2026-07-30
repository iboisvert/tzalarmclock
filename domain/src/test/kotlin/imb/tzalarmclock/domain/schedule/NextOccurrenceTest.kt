package imb.tzalarmclock.domain.schedule

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextOccurrenceTest {

    // 2026-07-27 is a Monday, which every weekday expectation below is anchored to.
    private val toronto = ZoneId.of("America/Toronto")
    private val paris = ZoneId.of("Europe/Paris")
    private val utcZoneId = ZoneId.of("UTC")
    private val losAngelesZoneId = ZoneId.of("America/Los_Angeles")
    private val kolkataZoneId = ZoneId.of("Asia/Kolkata")
    private val newYorkZoneId = ZoneId.of("America/New_York")
    private val singaporeZoneId = ZoneId.of("Asia/Singapore")

    private fun zonedDateTime(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, zoneId: ZoneId): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(year, month, dayOfMonth, hour, minute), zoneId)
    private fun localDateTime(year: Int, month: Int, dayOfMonth: Int): LocalDate =
        LocalDate.of(year, month, dayOfMonth)

    // -- Time-only alarms ---------------------------------------------------

    @Test
    fun `alarm rings later the same day in the same tz`() {
// - if alarm time is T13:00Z and current time is 2026-02-02T12:59Z then first valid day would be 2026-02-02
        val alarm = alarm(at = LocalTime.of(13, 0))
        val now = zonedDateTime(2026, 2, 2, 12, 59, utcZoneId)
        val expected = localDateTime(2026, 2, 2)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `alarm same time as now in the same tz`() {
// - if alarm time is T13:00Z and current time is 2026-02-02T13:00Z then first valid day would be 2026-02-03
        val alarm = alarm(at = LocalTime.of(13, 0))
        val now = zonedDateTime(2026, 2, 2, 13, 0, utcZoneId)
        val expected = localDateTime(2026, 2, 3)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `alarm rings later the same day in different tz`() {
// - if alarm time is T08:00+05:30(02:30Z) and current time is 2026-02-02T18:29-08(02:29Z) then first valid day would be 2026-02-02
        val alarm = alarm(at = LocalTime.of(8, 0), zone = kolkataZoneId)
        val now = zonedDateTime(2026, 2, 2, 18, 29, losAngelesZoneId)
        val expected = localDateTime(2026, 2, 2)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `alarm same time as now in different tz`() {
// - if alarm time is T08:00+05:30(02:30Z) and current time is 2026-02-02T18:30-08(02:30Z) then first valid day would be 2026-02-03
        val alarm = alarm(at = LocalTime.of(8, 0), zone = kolkataZoneId)
        val now = zonedDateTime(2026, 2, 2, 18, 30, losAngelesZoneId)
        val expected = localDateTime(2026, 2, 3)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `alarm same time as now in different tz 2`() {
// - if alarm time is T13:00-05(18:00Z) and current time is 2026-02-02T02:00+08(18:00Z) then first valid day would be 2026-02-02
        val alarm = alarm(at = LocalTime.of(13, 0), zone = newYorkZoneId)
        val now = zonedDateTime(2026, 2, 2, 2, 0, singaporeZoneId)
        val expected = localDateTime(2026, 2, 3)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `alarm same time as now in different tz 3`() {
// - if alarm time is T13:00-05(18:00Z) and current time is 2026-02-02T01:59+08(17:59Z) then first valid day would be 2026-02-02
        val alarm = alarm(at = LocalTime.of(13, 0), zone = newYorkZoneId)
        val now = zonedDateTime(2026, 2, 2, 1, 59, singaporeZoneId)
        val expected = localDateTime(2026, 2, 2)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `recurring alarm first valid day next week`() {
// - If today is 2026-02-02 (Monday) but first eligible day is Sunday then expected day is 2026-02-08 (Sunday)
        val alarm = alarm(at = LocalTime.of(13, 0), on = AlarmSchedule.Weekly(setOf(DayOfWeek.SUNDAY)))
        val now = zonedDateTime(2026, 2, 2, 12, 59, utcZoneId)
        val expected = localDateTime(2026, 2, 8)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `recurring alarm first valid day this week`() {
// - If today is 2026-02-01 (Sunday) but first eligible day is Monday then expected day is 2026-02-02 (Monday)
        val alarm = alarm(at = LocalTime.of(13, 0), on = AlarmSchedule.Weekly(setOf(DayOfWeek.MONDAY)))
        val now = zonedDateTime(2026, 2, 1, 12, 59, utcZoneId)
        val expected = localDateTime(2026, 2, 2)
        val actual = alarm.nextOccurrenceAfter(now)
        assertEquals(expected, actual?.toLocalDate())
    }

    @Test
    fun `a time-only alarm rings later the same day`() {
        val alarm = alarm(at = LocalTime.of(9, 0))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T08:00"))

        assertEquals(torontoAt("2026-07-27T09:00"), next)
    }

    @Test
    fun `a time-only alarm whose time has passed rings tomorrow`() {
        val alarm = alarm(at = LocalTime.of(9, 0))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T10:00"))

        assertEquals(torontoAt("2026-07-28T09:00"), next)
    }

    @Test
    fun `an alarm due exactly now has already fired and rings tomorrow`() {
        val alarm = alarm(at = LocalTime.of(9, 0))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T09:00"))

        assertEquals(torontoAt("2026-07-28T09:00"), next)
    }

    // -- Floating versus zone-locked ---------------------------------------

    @Test
    fun `a floating alarm follows the device to a new zone`() {
        val alarm = alarm(at = LocalTime.of(7, 0))

        val inToronto = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T00:00"))
        val inParis = alarm.nextOccurrenceAfter(parisAt("2026-07-27T00:00"))

        assertEquals("2026-07-27T11:00:00Z", inToronto?.toInstant().toString())
        assertEquals("2026-07-27T05:00:00Z", inParis?.toInstant().toString())
    }

    @Test
    fun `a zone-locked alarm rings at the same instant wherever the device is`() {
        val alarm = alarm(at = LocalTime.of(7, 0), zone = toronto)

        val seenFromToronto = alarm.nextOccurrenceAfter(torontoAt("2026-07-26T20:00"))
        val seenFromParis = alarm.nextOccurrenceAfter(parisAt("2026-07-27T00:00"))

        assertEquals(seenFromToronto?.toInstant(), seenFromParis?.toInstant())
        assertEquals("2026-07-27T11:00:00Z", seenFromToronto?.toInstant().toString())
    }

    @Test
    fun `the answer is expressed in the device zone, not the alarm zone`() {
        val alarm = alarm(at = LocalTime.of(7, 0), zone = toronto)

        val next = alarm.nextOccurrenceAfter(parisAt("2026-07-27T00:00"))

        assertEquals(paris, next?.zone)
        assertEquals(LocalTime.of(13, 0), next?.toLocalTime())
    }

    // -- One-time dated alarms ---------------------------------------------

    @Test
    fun `a dated alarm rings on its date`() {
        val alarm = alarm(at = LocalTime.of(4, 30), on = AlarmSchedule.OnDate(date("2026-12-25")))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T08:00"))

        assertEquals(torontoAt("2026-12-25T04:30"), next)
    }

    @Test
    fun `a dated alarm still to come today rings today`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.OnDate(date("2026-07-27")))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T08:00"))

        assertEquals(torontoAt("2026-07-27T09:00"), next)
    }

    @Test
    fun `a dated alarm never rings again once its date has passed`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.OnDate(date("2026-07-27")))

        assertNull(alarm.nextOccurrenceAfter(torontoAt("2026-07-27T10:00")))
        assertNull(alarm.nextOccurrenceAfter(torontoAt("2027-01-01T00:00")))
    }

    @Test
    fun `a dated alarm further out than the search window still resolves`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.OnDate(date("2035-01-01")))

        assertEquals(
            torontoAt("2035-01-01T09:00"),
            alarm.nextOccurrenceAfter(torontoAt("2026-07-27T08:00")),
        )
    }

    // -- Weekly recurrence --------------------------------------------------

    @Test
    fun `a weekly alarm rings today when today is one of its days`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = weekly(DayOfWeek.MONDAY))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T08:00"))

        assertEquals(torontoAt("2026-07-27T09:00"), next)
    }

    @Test
    fun `a weekly alarm whose time has passed waits a full week`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = weekly(DayOfWeek.MONDAY))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T10:00"))

        assertEquals(torontoAt("2026-08-03T09:00"), next)
    }

    @Test
    fun `a weekly alarm picks the nearest of its days`() {
        val alarm = alarm(
            at = LocalTime.of(9, 0),
            on = weekly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T10:00"))

        assertEquals(torontoAt("2026-07-29T09:00"), next)
    }

    @Test
    fun `a weekly alarm on a later day this week waits for it`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = weekly(DayOfWeek.SATURDAY))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-07-27T10:00"))

        assertEquals(torontoAt("2026-08-01T09:00"), next)
    }

    // -- Monthly recurrence -------------------------------------------------

    @Test
    fun `a monthly alarm picks the nearest of its days`() {
        val alarm = alarm(at = LocalTime.of(13, 0), on = AlarmSchedule.Monthly(setOf(1, 15)))

        assertEquals(
            torontoAt("2026-08-01T13:00"),
            alarm.nextOccurrenceAfter(torontoAt("2026-07-27T08:00")),
        )
        assertEquals(
            torontoAt("2026-08-15T13:00"),
            alarm.nextOccurrenceAfter(torontoAt("2026-08-02T08:00")),
        )
    }

    @Test
    fun `the 31st is skipped in months that do not have one, not clamped to month end`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.Monthly(setOf(31)))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-04-01T08:00"))

        assertEquals(torontoAt("2026-05-31T09:00"), next)
    }

    @Test
    fun `the 30th skips February entirely`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.Monthly(setOf(30)))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-02-01T08:00"))

        assertEquals(torontoAt("2026-03-30T09:00"), next)
    }

    @Test
    fun `the 31st can skip two months in a row`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.Monthly(setOf(31)))

        // February and April have no 31st, so January's is followed by March's.
        val next = alarm.nextOccurrenceAfter(torontoAt("2026-02-01T08:00"))

        assertEquals(torontoAt("2026-03-31T09:00"), next)
    }

    @Test
    fun `the 29th is skipped in a non-leap February`() {
        val alarm = alarm(at = LocalTime.of(9, 0), on = AlarmSchedule.Monthly(setOf(29)))

        // 2026 is not a leap year.
        val next = alarm.nextOccurrenceAfter(torontoAt("2026-02-01T08:00"))

        assertEquals(torontoAt("2026-03-29T09:00"), next)
    }

    private fun alarm(
        at: LocalTime,
        zone: ZoneId? = null,
        on: AlarmSchedule = AlarmSchedule.NextOccurrence,
    ) = Alarm(time = at, zone = zone, schedule = on)

    private fun weekly(vararg days: DayOfWeek) = AlarmSchedule.Weekly(days.toSet())

    private fun date(iso: String) = LocalDate.parse(iso)

    private fun torontoAt(localIso: String) = LocalDateTime.parse(localIso).atZone(toronto)

    private fun parisAt(localIso: String) = LocalDateTime.parse(localIso).atZone(paris)
}
