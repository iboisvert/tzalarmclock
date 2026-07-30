package imb.tzalarmclock.ui.summary

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.summary.AlarmGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Anchored to Monday 27 July 2026, 08:00 in Toronto. */
class SummaryUiStateTest {

    private val toronto = ZoneId.of("America/Toronto")
    private val now = LocalDateTime.parse("2026-07-27T08:00").atZone(toronto)

    // Time/date labels are locale-formatted; pin the default so assertions are deterministic.
    @Before
    fun fixLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `no alarms means an empty state`() {
        val state = buildSummaryUiState(emptyList(), AppSettings.DEFAULTS, now)

        assertTrue(state.isEmpty)
        assertEquals(emptyList<SummarySectionUi>(), state.sections)
    }

    @Test
    fun `enabled alarm carries its next-ring date and countdown`() {
        val alarm = Alarm(id = 1, name = "Wake", time = LocalTime.of(10, 0))

        val entry = buildSummaryUiState(listOf(alarm), AppSettings.DEFAULTS, now)
            .sections.single { it.group == AlarmGroup.TODAY }
            .entries.single()

        assertEquals("Wake", entry.name)
        assertEquals("10:00", entry.localTimeLabel)
        assertEquals("Jul 27", entry.nextRingDateLabel)
        assertEquals("2 h", entry.countdownLabel)
    }

    @Test
    fun `disabled alarm has no date or countdown`() {
        val alarm = Alarm(id = 1, name = "Off", time = LocalTime.of(10, 0), enabled = false)

        val entry = buildSummaryUiState(listOf(alarm), AppSettings.DEFAULTS, now)
            .sections.single().entries.single()

        assertNull(entry.nextRingDateLabel)
        assertNull(entry.countdownLabel)
    }

    @Test
    fun `time label respects the 24-hour setting`() {
        val alarm = Alarm(id = 1, name = "Wake", time = LocalTime.of(13, 5))
        val settings24h = AppSettings.DEFAULTS.copy(use24HourFormat = true)
        val settings12h = AppSettings.DEFAULTS.copy(use24HourFormat = false)

        val label24h = buildSummaryUiState(listOf(alarm), settings24h, now)
            .sections.single().entries.single().localTimeLabel
        val label12h = buildSummaryUiState(listOf(alarm), settings12h, now)
            .sections.single().entries.single().localTimeLabel

        assertEquals("13:05", label24h)
        assertEquals("1:05 PM", label12h)
    }

    @Test
    fun `a blank name still produces a row with no crash`() {
        val alarm = Alarm(id = 1, name = "", time = LocalTime.of(10, 0))

        val entry = buildSummaryUiState(listOf(alarm), AppSettings.DEFAULTS, now)
            .sections.single().entries.single()

        assertEquals("", entry.name)
    }
}
