package imb.tzalarmclock.domain.summary

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Anchored to Monday 27 July 2026 in Toronto, with weeks starting on Monday. */
class AlarmSummaryTest {

    private val toronto = ZoneId.of("America/Toronto")
    private val paris = ZoneId.of("Europe/Paris")
    private val now = LocalDateTime.parse("2026-07-27T08:00").atZone(toronto)

    @Test
    fun `alarms land in the group their next ring falls in`() {
        val today = alarm(name = "Today", at = LocalTime.of(9, 0))
        val tomorrow = alarm(name = "Tomorrow", at = LocalTime.of(7, 0))
        val thisWeek = alarm(
            name = "This week",
            at = LocalTime.of(9, 0),
            on = AlarmSchedule.Weekly(setOf(DayOfWeek.FRIDAY)),
        )
        val later = alarm(
            name = "Later",
            at = LocalTime.of(9, 0),
            on = AlarmSchedule.Monthly(setOf(15)),
        )

        val sections = summarize(today, tomorrow, thisWeek, later)

        assertEquals(
            listOf(
                AlarmGroup.TODAY to listOf("Today"),
                AlarmGroup.TOMORROW to listOf("Tomorrow"),
                AlarmGroup.THIS_WEEK to listOf("This week"),
                AlarmGroup.LATER to listOf("Later"),
            ),
            sections.asNameLists(),
        )
    }

    @Test
    fun `groups come back in display order and empty ones are omitted`() {
        val sections = summarize(alarm(name = "Only one", at = LocalTime.of(9, 0)))

        assertEquals(listOf(AlarmGroup.TODAY), sections.map { it.group })
    }

    @Test
    fun `no alarms means no sections`() {
        assertEquals(emptyList<AlarmSummarySection>(), summarize())
    }

    @Test
    fun `alarms within a group are ordered by time to ring`() {
        val sections = summarize(
            alarm(name = "Latest", at = LocalTime.of(23, 0)),
            alarm(name = "Soonest", at = LocalTime.of(9, 0)),
            alarm(name = "Middle", at = LocalTime.of(17, 0)),
        )

        assertEquals(
            listOf(AlarmGroup.TODAY to listOf("Soonest", "Middle", "Latest")),
            sections.asNameLists(),
        )
    }

    @Test
    fun `a zone-locked alarm is placed and ordered by its time in the device zone`() {
        // 20:00 in Paris is 14:00 in Toronto, so it rings after the 09:00 local one.
        val sections = summarize(
            alarm(name = "Paris 20:00", at = LocalTime.of(20, 0), zone = paris),
            alarm(name = "Local 09:00", at = LocalTime.of(9, 0)),
        )

        assertEquals(
            listOf(AlarmGroup.TODAY to listOf("Local 09:00", "Paris 20:00")),
            sections.asNameLists(),
        )
        assertEquals(LocalTime.of(14, 0), sections.single().entries.last().localTime)
    }

    // -- Disabled alarms ----------------------------------------------------

    @Test
    fun `disabled alarms are appended to Later with no countdown`() {
        val sections = summarize(
            alarm(name = "Enabled", at = LocalTime.of(9, 0)),
            alarm(name = "Disabled", at = LocalTime.of(9, 0), enabled = false),
        )

        assertEquals(
            listOf(
                AlarmGroup.TODAY to listOf("Enabled"),
                AlarmGroup.LATER to listOf("Disabled"),
            ),
            sections.asNameLists(),
        )
        assertNull(sections.last().entries.single().nextOccurrence)
    }

    @Test
    fun `a disabled alarm still reports its time in the device zone`() {
        val sections = summarize(
            alarm(name = "Paris", at = LocalTime.of(9, 0), zone = paris, enabled = false),
        )

        // 09:00 in Paris is 03:00 in Toronto.
        assertEquals(LocalTime.of(3, 0), sections.single().entries.single().localTime)
    }

    @Test
    fun `disabled alarms sort after enabled ones within Later, by time of day`() {
        val sections = summarize(
            alarm(name = "Late disabled", at = LocalTime.of(22, 0), enabled = false),
            alarm(name = "Early disabled", at = LocalTime.of(6, 0), enabled = false),
            alarm(
                name = "Enabled later",
                at = LocalTime.of(9, 0),
                on = AlarmSchedule.Monthly(setOf(15)),
            ),
        )

        assertEquals(
            listOf(
                AlarmGroup.LATER to listOf("Enabled later", "Early disabled", "Late disabled"),
            ),
            sections.asNameLists(),
        )
    }

    @Test
    fun `an expired one-time alarm is treated like a disabled one`() {
        val expired = alarm(
            name = "Expired",
            at = LocalTime.of(9, 0),
            on = AlarmSchedule.OnDate(LocalDate.parse("2026-01-01")),
        )

        val entry = summarize(expired).single().entries.single()

        assertEquals(AlarmGroup.LATER, summarize(expired).single().group)
        assertNull(entry.nextOccurrence)
        assertEquals(LocalTime.of(9, 0), entry.localTime)
    }

    // -- Week boundaries ----------------------------------------------------

    @Test
    fun `This Week covers the rest of the calendar week`() {
        // Week starts Monday 27 July, so it runs through Sunday 2 August.
        val sunday = alarm(
            name = "Sunday",
            at = LocalTime.of(9, 0),
            on = AlarmSchedule.Weekly(setOf(DayOfWeek.SUNDAY)),
        )
        val nextMonday = alarm(
            name = "Next Monday",
            at = LocalTime.of(7, 0),
            on = AlarmSchedule.Weekly(setOf(DayOfWeek.MONDAY)),
        )

        assertEquals(AlarmGroup.THIS_WEEK, summarize(sunday).single().group)
        assertEquals(AlarmGroup.LATER, summarize(nextMonday).single().group)
    }

    @Test
    fun `the first day of the week is respected`() {
        val sunday = alarm(
            name = "Sunday",
            at = LocalTime.of(9, 0),
            on = AlarmSchedule.Weekly(setOf(DayOfWeek.SUNDAY)),
        )

        // Sunday 2 August closes the week that starts on Monday 27 July, but
        // opens the one that would have started on Sunday 26 July.
        assertEquals(
            AlarmGroup.THIS_WEEK,
            summarizeAlarms(listOf(sunday), now, DayOfWeek.MONDAY).single().group,
        )
        assertEquals(
            AlarmGroup.LATER,
            summarizeAlarms(listOf(sunday), now, DayOfWeek.SUNDAY).single().group,
        )
    }

    @Test
    fun `on the last day of the week Tomorrow still wins and This Week is empty`() {
        val sundayNow = LocalDateTime.parse("2026-08-02T08:00").atZone(toronto)
        val tomorrow = alarm(name = "Tomorrow", at = LocalTime.of(7, 0))
        val later = alarm(
            name = "Later",
            at = LocalTime.of(9, 0),
            on = AlarmSchedule.Weekly(setOf(DayOfWeek.WEDNESDAY)),
        )

        val sections = summarizeAlarms(listOf(tomorrow, later), sundayNow, DayOfWeek.MONDAY)

        assertEquals(
            listOf(
                AlarmGroup.TOMORROW to listOf("Tomorrow"),
                AlarmGroup.LATER to listOf("Later"),
            ),
            sections.asNameLists(),
        )
        assertTrue(sections.none { it.group == AlarmGroup.THIS_WEEK })
    }

    // -- Device zone changes ------------------------------------------------

    @Test
    fun `moving the device regroups a floating alarm`() {
        val floating = alarm(name = "Wake", at = LocalTime.of(10, 0))

        // The same instant is 08:00 in Toronto and 14:00 in Paris, so 10:00 is
        // still to come today in one and already gone in the other.
        assertEquals(AlarmGroup.TODAY, summarize(floating).single().group)
        assertEquals(AlarmGroup.TOMORROW, summarizeInParis(floating).single().group)
    }

    @Test
    fun `moving the device does not change when a zone-locked alarm rings`() {
        val locked = alarm(name = "Wake", at = LocalTime.of(10, 0), zone = toronto)

        assertEquals(
            summarize(locked).single().entries.single().nextOccurrence?.toInstant(),
            summarizeInParis(locked).single().entries.single().nextOccurrence?.toInstant(),
        )
    }

    private fun summarizeInParis(vararg alarms: Alarm) =
        summarizeAlarms(alarms.toList(), now.withZoneSameInstant(paris), DayOfWeek.MONDAY)

    private fun summarize(vararg alarms: Alarm) =
        summarizeAlarms(alarms.toList(), now, DayOfWeek.MONDAY)

    private fun List<AlarmSummarySection>.asNameLists() =
        map { section -> section.group to section.entries.map { it.alarm.name } }

    private fun alarm(
        name: String,
        at: LocalTime,
        zone: ZoneId? = null,
        on: AlarmSchedule = AlarmSchedule.NextOccurrence,
        enabled: Boolean = true,
    ) = Alarm(name = name, time = at, zone = zone, schedule = on, enabled = enabled)
}
