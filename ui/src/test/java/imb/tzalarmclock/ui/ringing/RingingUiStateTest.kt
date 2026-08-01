package imb.tzalarmclock.ui.ringing

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class RingingUiStateTest {

    private val toronto = ZoneId.of("America/Toronto")
    private val now = LocalDateTime.parse("2026-07-27T07:00").atZone(toronto)

    @Before
    fun fixLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `shows the alarm name, falling back when blank`() {
        val alarm = Alarm(name = "Wake up", time = LocalTime.of(7, 0))

        val state = buildRingingUiState(alarm, AppSettings.DEFAULTS, now, snoozeCount = 0)

        assertEquals("Wake up", state.alarmName)
    }

    @Test
    fun `a blank alarm name falls back to a default`() {
        val alarm = Alarm(name = "", time = LocalTime.of(7, 0))

        val state = buildRingingUiState(alarm, AppSettings.DEFAULTS, now, snoozeCount = 0)

        assertEquals("Alarm", state.alarmName)
    }

    @Test
    fun `time label respects the 24-hour setting`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))
        val settings24h = AppSettings.DEFAULTS.copy(use24HourFormat = true)
        val settings12h = AppSettings.DEFAULTS.copy(use24HourFormat = false)

        assertEquals("07:00", buildRingingUiState(alarm, settings24h, now, 0).timeLabel)
        assertEquals("7:00 AM", buildRingingUiState(alarm, settings12h, now, 0).timeLabel)
    }

    @Test
    fun `zone label reflects the current device zone, not the alarm's own zone`() {
        val alarm = Alarm(time = LocalTime.of(7, 0), zone = ZoneId.of("Europe/Berlin"))

        val state = buildRingingUiState(alarm, AppSettings.DEFAULTS, now, snoozeCount = 0)

        assertEquals("America/Toronto", state.zoneLabel)
    }

    @Test
    fun `snoozes remaining counts down from the max`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))
        val settings = AppSettings.DEFAULTS.copy(maxSnoozeCount = 3)

        val state = buildRingingUiState(alarm, settings, now, snoozeCount = 1)

        assertEquals(2, state.snoozesRemaining)
        assertTrue(state.canSnooze)
    }

    @Test
    fun `snooze is unavailable once the max is reached`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))
        val settings = AppSettings.DEFAULTS.copy(maxSnoozeCount = 3)

        val state = buildRingingUiState(alarm, settings, now, snoozeCount = 3)

        assertEquals(0, state.snoozesRemaining)
        assertFalse(state.canSnooze)
    }

    @Test
    fun `snoozes remaining never goes negative`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))
        val settings = AppSettings.DEFAULTS.copy(maxSnoozeCount = 1)

        val state = buildRingingUiState(alarm, settings, now, snoozeCount = 5)

        assertEquals(0, state.snoozesRemaining)
        assertFalse(state.canSnooze)
    }

    @Test
    fun `a settings max of zero snoozes offers no snooze at all`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))
        val settings = AppSettings.DEFAULTS.copy(maxSnoozeCount = 0)

        val state = buildRingingUiState(alarm, settings, now, snoozeCount = 0)

        assertFalse(state.canSnooze)
    }
}
