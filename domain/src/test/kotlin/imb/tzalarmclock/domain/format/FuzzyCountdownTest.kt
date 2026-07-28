package imb.tzalarmclock.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The spec pins this format down precisely, so the tests are a table rather
 * than a handful of examples.
 */
class FuzzyCountdownTest {

    @Test
    fun `the spec's own examples`() {
        assertEquals("2 d, 5 h", format(days = 2, hours = 5))
        assertEquals("3 h", format(hours = 3))
        assertEquals("45 min", format(minutes = 45))
    }

    @Test
    fun `under an hour reads in minutes`() {
        assertEquals("0 min", format())
        assertEquals("1 min", format(minutes = 1))
        assertEquals("45 min", format(minutes = 45))
        assertEquals("59 min", format(minutes = 59))
    }

    @Test
    fun `minutes round half to even`() {
        assertEquals("0 min", format(seconds = 30))
        assertEquals("2 min", format(minutes = 1, seconds = 30))
        assertEquals("2 min", format(minutes = 2, seconds = 30))
        assertEquals("4 min", format(minutes = 3, seconds = 30))
        assertEquals("4 min", format(minutes = 4, seconds = 30))
    }

    @Test
    fun `an hour is the boundary into the hours form`() {
        assertEquals("59 min", format(minutes = 59, seconds = 29))
        // Still under an hour, so still minutes — the branch uses the exact
        // period, not the rounded value.
        assertEquals("60 min", format(minutes = 59, seconds = 59))
        assertEquals("1 h", format(hours = 1))
    }

    @Test
    fun `hours round half to even`() {
        assertEquals("2 h", format(hours = 1, minutes = 30))
        assertEquals("2 h", format(hours = 2, minutes = 30))
        assertEquals("4 h", format(hours = 3, minutes = 30))
        assertEquals("4 h", format(hours = 4, minutes = 30))
        assertEquals("2 h", format(hours = 1, minutes = 31))
        assertEquals("1 h", format(hours = 1, minutes = 29))
    }

    @Test
    fun `a day is the boundary into the days form`() {
        assertEquals("24 h", format(hours = 23, minutes = 59))
        assertEquals("1 d", format(days = 1))
    }

    @Test
    fun `the hours part is omitted when it is zero`() {
        assertEquals("1 d", format(days = 1))
        assertEquals("1 d", format(days = 1, minutes = 29))
        assertEquals("1 d", format(days = 1, minutes = 30))
        assertEquals("10 d", format(days = 10))
    }

    @Test
    fun `days and hours are reported together`() {
        assertEquals("1 d, 1 h", format(days = 1, hours = 1))
        assertEquals("2 d, 5 h", format(days = 2, hours = 5))
        assertEquals("6 d, 23 h", format(days = 6, hours = 23))
    }

    @Test
    fun `the hours part rounds half to even too`() {
        assertEquals("1 d, 2 h", format(days = 1, hours = 1, minutes = 30))
        assertEquals("1 d, 2 h", format(days = 1, hours = 2, minutes = 30))
        assertEquals("1 d, 4 h", format(days = 1, hours = 3, minutes = 30))
    }

    @Test
    fun `hours rounding up to a full day carries into the day count`() {
        assertEquals("2 d", format(days = 1, hours = 23, minutes = 40))
        assertEquals("1 d, 23 h", format(days = 1, hours = 23, minutes = 20))
    }

    @Test
    fun `a period that has already elapsed reads as zero`() {
        assertEquals("0 min", FuzzyCountdown.format(Duration.ofMinutes(-5)))
        assertEquals("0 min", FuzzyCountdown.format(Duration.ofDays(-2)))
    }

    @Test
    fun `formatting between two instants matches formatting the duration`() {
        val from = Instant.parse("2026-07-27T06:00:00Z")
        val until = Instant.parse("2026-07-29T11:00:00Z")

        assertEquals("2 d, 5 h", FuzzyCountdown.format(from, until))
    }

    private fun format(days: Long = 0, hours: Long = 0, minutes: Long = 0, seconds: Long = 0) =
        FuzzyCountdown.format(
            Duration.ofDays(days)
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds),
        )
}
