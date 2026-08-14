package imb.tzalarmclock.ui.timers

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimersUiStateTest {

    private val now = Instant.ofEpochSecond(1_000_000)

    @Test
    fun `no timers means an empty state`() {
        val state = buildTimersUiState(emptyList(), now)

        assertTrue(state.isEmpty)
        assertEquals(emptyList<TimerRowUi>(), state.timers)
    }

    @Test
    fun `a stopped timer shows its full configured duration`() {
        val timer = Timer(id = 1, configuredDuration = Duration.ofMinutes(5), createdAt = now)

        val row = buildTimersUiState(listOf(timer), now).timers.single()

        assertEquals(1L, row.id)
        assertEquals("5:00", row.remainingLabel)
        assertEquals(TimerState.STOPPED, row.state)
    }

    @Test
    fun `a running timer shows its live remaining time`() {
        val timer = Timer(id = 1, configuredDuration = Duration.ofMinutes(5), createdAt = now).start(now)

        val row = buildTimersUiState(listOf(timer), now.plusSeconds(60)).timers.single()

        assertEquals("4:00", row.remainingLabel)
        assertEquals(TimerState.RUNNING, row.state)
    }

    @Test
    fun `rows are ordered by the Stage 14 summary placeholder`() {
        val running = Timer(id = 1, configuredDuration = Duration.ofMinutes(5), createdAt = now).start(now)
        val stopped = Timer(id = 2, configuredDuration = Duration.ofMinutes(1), createdAt = now)

        val rows = buildTimersUiState(listOf(stopped, running), now).timers

        assertEquals(listOf(1L, 2L), rows.map { it.id })
    }
}
