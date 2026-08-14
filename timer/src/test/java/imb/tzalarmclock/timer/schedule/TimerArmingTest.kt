package imb.tzalarmclock.timer.schedule

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The arming plan is the whole decision the Stage 15 scheduler makes, and it
 * is pure, so it is tested here rather than on a device — same reasoning as
 * `imb.tzalarmclock.alarm.schedule.AlarmArmingTest`.
 */
class TimerArmingTest {

    private fun timer(
        id: Long = 1L,
        state: TimerState = TimerState.RUNNING,
        endInstant: Instant? = Instant.ofEpochMilli(10_000),
    ) = Timer(
        id = id,
        configuredDuration = Duration.ofMinutes(5),
        state = state,
        endInstant = endInstant,
        createdAt = Instant.ofEpochMilli(1_000),
    )

    @Test
    fun `a running timer is armed for its end instant`() {
        val plan = timerArmingPlan(listOf(timer(endInstant = Instant.ofEpochMilli(9_000))))

        assertEquals(1, plan.size)
        assertEquals(Instant.ofEpochMilli(9_000), plan.single().endInstant)
    }

    @Test
    fun `a stopped timer is not armed`() {
        assertTrue(timerArmingPlan(listOf(timer(state = TimerState.STOPPED, endInstant = null))).isEmpty())
    }

    @Test
    fun `a paused timer is not armed`() {
        assertTrue(timerArmingPlan(listOf(timer(state = TimerState.PAUSED, endInstant = null))).isEmpty())
    }

    @Test
    fun `an expired timer is not armed`() {
        assertTrue(timerArmingPlan(listOf(timer(state = TimerState.EXPIRED, endInstant = null))).isEmpty())
    }

    @Test
    fun `an unsaved timer is not armed`() {
        assertTrue(timerArmingPlan(listOf(timer(id = Timer.NO_ID))).isEmpty())
    }

    @Test
    fun `the plan is ordered soonest first`() {
        val timers = listOf(
            timer(id = 1, endInstant = Instant.ofEpochMilli(30_000)),
            timer(id = 2, endInstant = Instant.ofEpochMilli(10_000)),
            timer(id = 3, endInstant = Instant.ofEpochMilli(20_000)),
        )

        val plan = timerArmingPlan(timers)

        assertEquals(listOf(2L, 3L, 1L), plan.map { it.timerId })
    }

    @Test
    fun `trigger millis matches the end instant`() {
        val armed = timerArmingPlan(listOf(timer(endInstant = Instant.ofEpochMilli(42_000)))).single()

        assertEquals(42_000L, armed.triggerAtMillis)
    }
}
