package imb.tzalarmclock.domain.format

import imb.tzalarmclock.domain.model.AlarmSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceLabelTest {

    @Test
    fun `weekly lists days in Monday-first order regardless of input order`() {
        val schedule = AlarmSchedule.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))

        assertEquals("Weekly M, W, F", RecurrenceLabel.format(schedule))
    }

    @Test
    fun `weekly single day`() {
        assertEquals("Weekly M", RecurrenceLabel.format(AlarmSchedule.Weekly(setOf(DayOfWeek.MONDAY))))
    }

    @Test
    fun `monthly lists days ascending regardless of input order`() {
        val schedule = AlarmSchedule.Monthly(setOf(15, 1))

        assertEquals("Monthly 1, 15", RecurrenceLabel.format(schedule))
    }

    @Test
    fun `non-recurring schedules have no label`() {
        assertNull(RecurrenceLabel.format(AlarmSchedule.NextOccurrence))
        assertNull(RecurrenceLabel.format(AlarmSchedule.OnDate(LocalDate.parse("2026-01-01"))))
    }
}
