package imb.tzalarmclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AlarmScheduleTest {

    @Test
    fun `each variant reports its storage discriminator`() {
        assertEquals(ScheduleType.NEXT_OCCURRENCE, AlarmSchedule.NextOccurrence.type)
        assertEquals(
            ScheduleType.ONE_TIME_DATE,
            AlarmSchedule.OnDate(LocalDate.of(2026, 8, 1)).type,
        )
        assertEquals(ScheduleType.WEEKLY, AlarmSchedule.Weekly(setOf(DayOfWeek.MONDAY)).type)
        assertEquals(ScheduleType.MONTHLY, AlarmSchedule.Monthly(setOf(1)).type)
    }

    @Test
    fun `only weekly and monthly schedules recur`() {
        assertFalse(AlarmSchedule.NextOccurrence.isRecurring)
        assertFalse(AlarmSchedule.OnDate(LocalDate.of(2026, 8, 1)).isRecurring)
        assertTrue(AlarmSchedule.Weekly(setOf(DayOfWeek.FRIDAY)).isRecurring)
        assertTrue(AlarmSchedule.Monthly(setOf(15)).isRecurring)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `weekly schedule rejects an empty day set`() {
        AlarmSchedule.Weekly(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `monthly schedule rejects an empty day set`() {
        AlarmSchedule.Monthly(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `monthly schedule rejects day zero`() {
        AlarmSchedule.Monthly(setOf(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `monthly schedule rejects day beyond 31`() {
        AlarmSchedule.Monthly(setOf(32))
    }

    @Test
    fun `monthly schedule accepts the 31st, which later stages skip in short months`() {
        assertEquals(setOf(31), AlarmSchedule.Monthly(setOf(31)).daysOfMonth)
    }
}
