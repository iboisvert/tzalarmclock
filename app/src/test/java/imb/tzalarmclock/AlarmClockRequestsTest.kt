package imb.tzalarmclock

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmClockRequestsTest {

    private val now = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, ZoneId.of("America/Toronto"))

    @Test
    fun `set request without days is a floating one-shot alarm`() {
        val alarm = alarmForSetRequest(7, 30, "  Gym  ", null, vibrate = false, ringtoneUri = null)!!

        assertEquals(LocalTime.of(7, 30), alarm.time)
        assertEquals("Gym", alarm.name)
        assertNull(alarm.zone)
        assertEquals(AlarmSchedule.NextOccurrence, alarm.schedule)
        assertEquals(false, alarm.vibrate)
        assertEquals(Alarm.NO_ID, alarm.id)
    }

    @Test
    fun `set request with days is weekly and drops invalid days`() {
        val alarm = alarmForSetRequest(
            hour = 6,
            minute = 0,
            message = null,
            calendarDays = listOf(Calendar.MONDAY, Calendar.FRIDAY, 0, 9),
            vibrate = null,
            ringtoneUri = null,
        )!!

        assertEquals(AlarmSchedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), alarm.schedule)
        assertEquals("", alarm.name)
    }

    @Test
    fun `set request with only invalid days falls back to one-shot`() {
        val alarm = alarmForSetRequest(6, 0, null, listOf(42), null, null)!!

        assertEquals(AlarmSchedule.NextOccurrence, alarm.schedule)
    }

    @Test
    fun `set request out of range is rejected`() {
        assertNull(alarmForSetRequest(24, 0, null, null, null, null))
        assertNull(alarmForSetRequest(-1, 0, null, null, null, null))
        assertNull(alarmForSetRequest(7, 60, null, null, null, null))
    }

    @Test
    fun `calendar days map to the matching day of week`() {
        assertEquals(DayOfWeek.SUNDAY, calendarDayToDayOfWeek(Calendar.SUNDAY))
        assertEquals(DayOfWeek.MONDAY, calendarDayToDayOfWeek(Calendar.MONDAY))
        assertEquals(DayOfWeek.SATURDAY, calendarDayToDayOfWeek(Calendar.SATURDAY))
        assertNull(calendarDayToDayOfWeek(0))
        assertNull(calendarDayToDayOfWeek(8))
    }

    @Test
    fun `equivalent alarm matches name ignoring case but not zone-locked ones`() {
        val requested = alarmForSetRequest(7, 0, "gym", null, null, null)!!
        val zoneLocked = Alarm(id = 1, name = "Gym", time = LocalTime.of(7, 0), zone = ZoneId.of("Asia/Tokyo"))
        val floating = Alarm(id = 2, name = "Gym", time = LocalTime.of(7, 0), enabled = false)

        assertEquals(floating, findEquivalentAlarm(listOf(zoneLocked, floating), requested))
        assertNull(findEquivalentAlarm(listOf(zoneLocked), requested))
    }

    @Test
    fun `12-hour values resolve with is-pm and others pass through`() {
        assertEquals(19, to24Hour(7, isPm = true))
        assertEquals(7, to24Hour(7, isPm = false))
        assertEquals(12, to24Hour(12, isPm = true))
        assertEquals(0, to24Hour(12, isPm = false))
        assertEquals(19, to24Hour(19, isPm = true))
        assertEquals(7, to24Hour(7, isPm = null))
    }

    @Test
    fun `dismiss targets select only enabled alarms`() {
        val sevenGym = Alarm(id = 1, name = "Gym", time = LocalTime.of(7, 0))
        val eightWork = Alarm(id = 2, name = "Work", time = LocalTime.of(8, 0))
        val sevenOff = Alarm(id = 3, name = "gym", time = LocalTime.of(7, 0), enabled = false)
        val alarms = listOf(sevenGym, eightWork, sevenOff)

        assertEquals(listOf(sevenGym, eightWork), alarmsToDismiss(DismissTarget.All, alarms, now, emptyMap()))
        assertEquals(listOf(sevenGym), alarmsToDismiss(DismissTarget.Label(" GYM "), alarms, now, emptyMap()))
        assertEquals(listOf(eightWork), alarmsToDismiss(DismissTarget.Time(8, 0), alarms, now, emptyMap()))
    }

    @Test
    fun `next dismiss target is the soonest to ring, counting snoozes`() {
        val inOneHour = Alarm(id = 1, time = LocalTime.of(13, 0))
        val inTwoHours = Alarm(id = 2, time = LocalTime.of(14, 0))
        val alarms = listOf(inTwoHours, inOneHour)

        assertEquals(listOf(inOneHour), alarmsToDismiss(DismissTarget.Next, alarms, now, emptyMap()))

        val snoozedSoon = mapOf(2L to now.plusMinutes(5).toInstant())
        assertEquals(listOf(inTwoHours), alarmsToDismiss(DismissTarget.Next, alarms, now, snoozedSoon))
    }

    @Test
    fun `next dismiss target is empty when nothing will ring`() {
        assertEquals(emptyList<Alarm>(), alarmsToDismiss(DismissTarget.Next, emptyList(), now, emptyMap<Long, Instant>()))
    }
}
