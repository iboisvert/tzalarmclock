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
import java.time.ZoneOffset

/**
 * Daylight-saving behaviour, anchored to two real transitions in
 * `America/Toronto` during 2026:
 *
 * - **Sunday 8 March**, when 02:00 EST becomes 03:00 EDT, so wall-clock times
 *   from 02:00 to 02:59 do not happen at all that day.
 * - **Sunday 1 November**, when 02:00 EDT becomes 01:00 EST, so wall-clock
 *   times from 01:00 to 01:59 happen twice.
 *
 * Both dates are asserted below so a tzdb update that moved them would fail
 * loudly rather than quietly invalidating the rest of the file.
 */
class NextOccurrenceDstTest {

    private val toronto = ZoneId.of("America/Toronto")
    private val springForward = LocalDate.parse("2026-03-08")
    private val fallBack = LocalDate.parse("2026-11-01")

    @Test
    fun `the transitions really are where these tests assume`() {
        assertEquals(DayOfWeek.SUNDAY, springForward.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, fallBack.dayOfWeek)
        assertEquals(
            "02:30 does not exist on the spring-forward date",
            emptyList<ZoneOffset>(),
            toronto.rules.getValidOffsets(springForward.atTime(2, 30)),
        )
        assertEquals(
            "01:30 happens twice on the fall-back date",
            2,
            toronto.rules.getValidOffsets(fallBack.atTime(1, 30)).size,
        )
    }

    // -- Spring-forward gap: skip the occurrence entirely -------------------

    @Test
    fun `a time-only alarm in the spring-forward gap skips to the next day`() {
        val alarm = Alarm(time = LocalTime.of(2, 30))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-03-08T00:00"))

        assertEquals(torontoAt("2026-03-09T02:30"), next)
    }

    @Test
    fun `a weekly alarm in the gap skips that week, not just that hour`() {
        val alarm = Alarm(
            time = LocalTime.of(2, 30),
            schedule = AlarmSchedule.Weekly(setOf(DayOfWeek.SUNDAY)),
        )

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-03-08T00:00"))

        assertEquals(torontoAt("2026-03-15T02:30"), next)
    }

    @Test
    fun `a monthly alarm in the gap skips to the following month`() {
        val alarm = Alarm(
            time = LocalTime.of(2, 30),
            schedule = AlarmSchedule.Monthly(setOf(8)),
        )

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-03-08T00:00"))

        assertEquals(torontoAt("2026-04-08T02:30"), next)
    }

    @Test
    fun `a dated alarm in the gap never rings, since it has no other day`() {
        val alarm = Alarm(
            time = LocalTime.of(2, 30),
            schedule = AlarmSchedule.OnDate(springForward),
        )

        assertNull(alarm.nextOccurrenceAfter(torontoAt("2026-03-08T00:00")))
    }

    @Test
    fun `a time just outside the gap is unaffected`() {
        val before = Alarm(time = LocalTime.of(1, 30))
        val after = Alarm(time = LocalTime.of(3, 30))

        assertEquals(
            torontoAt("2026-03-08T01:30"),
            before.nextOccurrenceAfter(torontoAt("2026-03-08T00:00")),
        )
        assertEquals(
            torontoAt("2026-03-08T03:30"),
            after.nextOccurrenceAfter(torontoAt("2026-03-08T00:00")),
        )
    }

    @Test
    fun `the gap only affects the zone that is transitioning`() {
        // Paris springs forward on 29 March, not 8 March, so 02:30 exists there.
        val alarm = Alarm(time = LocalTime.of(2, 30), zone = ZoneId.of("Europe/Paris"))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-03-07T20:00"))

        assertEquals(LocalTime.of(2, 30), next?.withZoneSameInstant(alarm.zone)?.toLocalTime())
        assertEquals(
            LocalDate.parse("2026-03-08"),
            next?.withZoneSameInstant(alarm.zone)?.toLocalDate(),
        )
    }

    // -- Fall-back overlap: ring on the first pass --------------------------

    @Test
    fun `an alarm in the fall-back overlap rings on the first pass`() {
        val alarm = Alarm(time = LocalTime.of(1, 30))

        val next = alarm.nextOccurrenceAfter(torontoAt("2026-11-01T00:00"))

        // 01:30 EDT, an hour before the repeat at 01:30 EST.
        assertEquals(ZoneOffset.ofHours(-4), next?.offset)
        assertEquals("2026-11-01T05:30:00Z", next?.toInstant().toString())
    }

    @Test
    fun `the second pass through the overlap is not offered as a separate ring`() {
        val alarm = Alarm(time = LocalTime.of(1, 30))
        val firstPass = alarm.nextOccurrenceAfter(torontoAt("2026-11-01T00:00"))!!

        val afterFirstPass = alarm.nextOccurrenceAfter(firstPass)

        // The next ring is the following day, not 01:30 EST an hour later.
        assertEquals(torontoAt("2026-11-02T01:30"), afterFirstPass)
    }

    @Test
    fun `a zone-locked alarm keeps its own zone's transitions`() {
        val alarm = Alarm(time = LocalTime.of(1, 30), zone = toronto)

        // Device is in Paris, which is not transitioning on this date.
        val next = alarm.nextOccurrenceAfter(
            LocalDateTime.parse("2026-11-01T04:00").atZone(ZoneId.of("Europe/Paris")),
        )

        assertEquals("2026-11-01T05:30:00Z", next?.toInstant().toString())
    }

    private fun torontoAt(localIso: String) = LocalDateTime.parse(localIso).atZone(toronto)
}
