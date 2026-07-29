package imb.tzalarmclock.alarm.schedule

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The arming plan is the whole decision the Stage 3 scheduler makes, and it is
 * pure, so it is tested here rather than on a device. What the device tests
 * cover is only that `AlarmManager` is handed what this produces.
 */
class AlarmArmingTest {

    private val toronto = ZoneId.of("America/Toronto")
    private val berlin = ZoneId.of("Europe/Berlin")

    /** Wednesday 2026-07-29, 09:00 in Toronto. */
    private val now = ZonedDateTime.of(
        LocalDate.of(2026, 7, 29),
        LocalTime.of(9, 0),
        toronto,
    )

    private fun alarm(
        id: Long = 1L,
        time: LocalTime = LocalTime.of(7, 0),
        zone: ZoneId? = null,
        schedule: AlarmSchedule = AlarmSchedule.NextOccurrence,
        enabled: Boolean = true,
    ) = Alarm(id = id, time = time, zone = zone, schedule = schedule, enabled = enabled)

    @Test
    fun `disabled alarms are not armed`() {
        val plan = armingPlan(listOf(alarm(enabled = false)), now)

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `unsaved alarms are not armed`() {
        val plan = armingPlan(listOf(alarm(id = Alarm.NO_ID)), now)

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `an alarm with no occurrence left is not armed`() {
        val past = alarm(schedule = AlarmSchedule.OnDate(LocalDate.of(2020, 1, 1)))

        assertTrue(armingPlan(listOf(past), now).isEmpty())
    }

    @Test
    fun `a time-only alarm arms for the next occurrence of that time`() {
        // 07:00 has already passed today, so tomorrow.
        val plan = armingPlan(listOf(alarm(time = LocalTime.of(7, 0))), now)

        assertEquals(1, plan.size)
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 7, 30), LocalTime.of(7, 0), toronto).toInstant(),
            plan.single().ringsAt.toInstant(),
        )
    }

    @Test
    fun `a floating alarm follows the device zone`() {
        val floating = alarm(time = LocalTime.of(7, 0), zone = null)

        val inToronto = armingPlan(listOf(floating), now).single()
        val inBerlin = armingPlan(listOf(floating), now.withZoneSameInstant(berlin)).single()

        // Same wall-clock time, different zones, so different instants. This is
        // the app's entire premise: re-running the plan after a zone change is
        // what moves the alarm.
        assertTrue(inToronto.triggerAtMillis != inBerlin.triggerAtMillis)
    }

    @Test
    fun `a zone-locked alarm ignores the device zone`() {
        val locked = alarm(time = LocalTime.of(7, 0), zone = berlin)

        val inToronto = armingPlan(listOf(locked), now).single()
        val inBerlin = armingPlan(listOf(locked), now.withZoneSameInstant(berlin)).single()

        assertEquals(inToronto.triggerAtMillis, inBerlin.triggerAtMillis)
    }

    @Test
    fun `weekly alarms arm for the next matching day`() {
        val friday = alarm(
            time = LocalTime.of(6, 30),
            schedule = AlarmSchedule.Weekly(setOf(DayOfWeek.FRIDAY)),
        )

        val armed = armingPlan(listOf(friday), now).single()

        assertEquals(DayOfWeek.FRIDAY, armed.ringsAt.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 31), armed.ringsAt.toLocalDate())
    }

    @Test
    fun `the plan is ordered soonest first`() {
        val alarms = listOf(
            alarm(id = 1, time = LocalTime.of(23, 0)),
            alarm(id = 2, time = LocalTime.of(10, 0)),
            alarm(id = 3, time = LocalTime.of(17, 0)),
        )

        val plan = armingPlan(alarms, now)

        assertEquals(listOf(2L, 3L, 1L), plan.map { it.alarmId })
    }

    @Test
    fun `only the nearest occurrence of each alarm is armed`() {
        val daily = alarm(
            schedule = AlarmSchedule.Weekly(DayOfWeek.entries.toSet()),
            time = LocalTime.of(10, 0),
        )

        val plan = armingPlan(listOf(daily), now)

        // One pending intent per alarm; the following occurrence is armed when
        // this one fires.
        assertEquals(1, plan.size)
        assertEquals(LocalDate.of(2026, 7, 29), plan.single().ringsAt.toLocalDate())
    }

    @Test
    fun `trigger millis matches the computed instant`() {
        val armed = armingPlan(listOf(alarm(time = LocalTime.of(10, 0))), now).single()

        assertEquals(armed.ringsAt.toInstant().toEpochMilli(), armed.triggerAtMillis)
    }
}
