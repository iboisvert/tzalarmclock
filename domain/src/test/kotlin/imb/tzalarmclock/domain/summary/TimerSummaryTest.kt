package imb.tzalarmclock.domain.summary

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.start
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerSummaryTest {

    private val now = Instant.ofEpochSecond(1_000_000)

    @Test
    fun `timers sorted by create time`() {
        val stopped = Timer(id = 3, configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochSecond(1))
        val running1 = Timer(id = 1, configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochSecond(2)).start(now)
        val running2 = Timer(id = 2, configuredDuration = Duration.ofMinutes(1), createdAt = Instant.ofEpochSecond(3)).start(now)

        val sorted = listOf(stopped, running1, running2).sortedForSummary()

        assertEquals(listOf(stopped, running1, running2), sorted)
    }

    @Test
    fun `timer order independent of running state`() {
        val stopped = Timer(id = 1, configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochSecond(1))
        val running1 = Timer(id = 2, configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochSecond(2)).start(now)

        val sorted = listOf(stopped, running1).sortedForSummary()

        assertEquals(listOf(stopped, running1), sorted)
    }

    @Test
    fun `timer order independent of remaining time`() {
        val running1 = Timer(id = 2, configuredDuration = Duration.ofMinutes(5), createdAt = Instant.ofEpochSecond(2)).start(now)
        val running2 = Timer(id = 3, configuredDuration = Duration.ofMinutes(1), createdAt = Instant.ofEpochSecond(3)).start(now)

        val sorted = listOf(running1, running2).sortedForSummary()

        assertEquals(listOf(running1, running2), sorted)
    }
}
