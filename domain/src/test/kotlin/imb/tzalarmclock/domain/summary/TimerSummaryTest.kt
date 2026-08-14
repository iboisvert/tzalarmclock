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
    fun `running and paused timers sort ahead of stopped and expired ones`() {
        val stopped = Timer(id = 1, configuredDuration = Duration.ofMinutes(5), createdAt = now)
        val running = Timer(id = 2, configuredDuration = Duration.ofMinutes(5), createdAt = now).start(now)

        val sorted = listOf(stopped, running).sortedForSummary(now)

        assertEquals(listOf(running, stopped), sorted)
    }

    @Test
    fun `running and paused timers sort by ascending remaining time`() {
        val soon = Timer(id = 1, configuredDuration = Duration.ofMinutes(1), createdAt = now).start(now)
        val later = Timer(id = 2, configuredDuration = Duration.ofMinutes(5), createdAt = now).start(now)

        val sorted = listOf(later, soon).sortedForSummary(now)

        assertEquals(listOf(soon, later), sorted)
    }

    @Test
    fun `stopped and expired timers sort by creation order`() {
        val older = Timer(
            id = 1,
            configuredDuration = Duration.ofMinutes(5),
            state = TimerState.EXPIRED,
            createdAt = now,
        )
        val newer = Timer(
            id = 2,
            configuredDuration = Duration.ofMinutes(5),
            createdAt = now.plusSeconds(60),
        )

        val sorted = listOf(newer, older).sortedForSummary(now)

        assertEquals(listOf(older, newer), sorted)
    }
}
