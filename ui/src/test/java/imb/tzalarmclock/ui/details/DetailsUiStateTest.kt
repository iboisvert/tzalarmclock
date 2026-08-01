package imb.tzalarmclock.ui.details

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class DetailsUiStateTest {

    private val today = LocalDate.parse("2026-07-27") // a Monday

    @Test
    fun `a brand-new alarm builds as isNew`() {
        val alarm = Alarm(time = LocalTime.of(7, 0))

        val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

        assertTrue(state.isNew)
        assertEquals(Alarm.NO_ID, state.alarmId)
        assertFalse(state.isLoading)
    }

    @Test
    fun `an existing alarm builds as not new`() {
        val alarm = Alarm(id = 42, time = LocalTime.of(7, 0))

        val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

        assertFalse(state.isNew)
        assertEquals(42L, state.alarmId)
    }

    @Test
    fun `every schedule type round-trips through toAlarm`() {
        val schedules = listOf(
            AlarmSchedule.NextOccurrence,
            AlarmSchedule.OnDate(LocalDate.parse("2026-12-25")),
            AlarmSchedule.Weekly(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            AlarmSchedule.Monthly(setOf(1, 15)),
        )

        for (schedule in schedules) {
            val alarm = Alarm(id = 1, time = LocalTime.of(6, 30), schedule = schedule)
            val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

            assertEquals(schedule, state.toAlarm().schedule)
        }
    }

    @Test
    fun `weekly and monthly editors default to today when the alarm isn't already that type`() {
        val alarm = Alarm(time = LocalTime.of(6, 30))

        val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

        assertEquals(setOf(DayOfWeek.MONDAY), state.weekdays)
        assertEquals(setOf(27), state.monthDays)
        assertEquals(today, state.date)
    }

    @Test
    fun `toggling a weekday adds or removes it`() {
        val state = buildDetailsUiState(Alarm(time = LocalTime.of(6, 30)), AppSettings.DEFAULTS, today)
            .copy(scheduleType = ScheduleType.WEEKLY)

        val added = state.withWeekdayToggled(DayOfWeek.FRIDAY)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), added.weekdays)

        val removed = added.withWeekdayToggled(DayOfWeek.MONDAY)
        assertEquals(setOf(DayOfWeek.FRIDAY), removed.weekdays)
    }

    @Test
    fun `the last remaining weekday cannot be toggled off`() {
        val state = buildDetailsUiState(Alarm(time = LocalTime.of(6, 30)), AppSettings.DEFAULTS, today)
            .copy(scheduleType = ScheduleType.WEEKLY)

        val result = state.withWeekdayToggled(DayOfWeek.MONDAY)

        assertEquals(setOf(DayOfWeek.MONDAY), result.weekdays)
    }

    @Test
    fun `the last remaining month day cannot be toggled off`() {
        val state = buildDetailsUiState(Alarm(time = LocalTime.of(6, 30)), AppSettings.DEFAULTS, today)

        val result = state.withMonthDayToggled(27)

        assertEquals(setOf(27), result.monthDays)
    }

    @Test
    fun `null ringtone and vibrate resolve against settings defaults for display but stay null on save`() {
        val settings = AppSettings.DEFAULTS.copy(defaultRingtoneUri = "content://default", defaultVibrate = false)
        val alarm = Alarm(time = LocalTime.of(6, 30))

        val state = buildDetailsUiState(alarm, settings, today)

        assertNull(state.ringtoneUri)
        assertEquals("content://default", state.defaultRingtoneUri)
        assertNull(state.vibrate)
        assertFalse(state.defaultVibrate)
        assertNull(state.toAlarm().ringtoneUri)
        assertNull(state.toAlarm().vibrate)
    }

    @Test
    fun `an alarm-specific ringtone and vibrate override survive the round trip`() {
        val alarm = Alarm(time = LocalTime.of(6, 30), ringtoneUri = "content://mine", vibrate = true)

        val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

        assertEquals("content://mine", state.toAlarm().ringtoneUri)
        assertEquals(true, state.toAlarm().vibrate)
    }

    @Test
    fun `a blank name is trimmed on save`() {
        val alarm = Alarm(time = LocalTime.of(6, 30), name = "  Wake  ")

        val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

        assertEquals("Wake", state.toAlarm().name)
    }

    @Test
    fun `zone is preserved through the round trip`() {
        val zone = java.time.ZoneId.of("America/Toronto")
        val alarm = Alarm(time = LocalTime.of(6, 30), zone = zone)

        val state = buildDetailsUiState(alarm, AppSettings.DEFAULTS, today)

        assertEquals(zone, state.toAlarm().zone)
    }
}
