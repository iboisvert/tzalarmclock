package imb.tzalarmclock.domain.schedule

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerTransitionsTest {

    private val t0 = Instant.ofEpochSecond(1_000_000)

    @Test
    fun `a fresh timer is stopped at its full configured duration`() {
        val timer = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0)

        assertEquals(TimerState.STOPPED, timer.state)
        assertEquals(Duration.ofMinutes(5), timer.remaining(t0))
    }

    @Test
    fun `starting a stopped timer arms its end instant`() {
        val timer = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0)

        val started = timer.start(t0)

        assertEquals(TimerState.RUNNING, started.state)
        assertEquals(t0.plus(Duration.ofMinutes(5)), started.endInstant)
        assertNull(started.remainingAtPause)
    }

    @Test
    fun `remaining time on a running timer counts down as now advances`() {
        val started = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0).start(t0)

        assertEquals(Duration.ofMinutes(3), started.remaining(t0.plus(Duration.ofMinutes(2))))
    }

    @Test
    fun `remaining time on a running timer clamps to zero once past its end instant`() {
        val started = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0).start(t0)

        assertEquals(Duration.ZERO, started.remaining(t0.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun `pausing a running timer freezes its remaining time and clears the end instant`() {
        val running = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0).start(t0)

        val paused = running.pause(t0.plus(Duration.ofMinutes(2)))

        assertEquals(TimerState.PAUSED, paused.state)
        assertEquals(Duration.ofMinutes(3), paused.remainingAtPause)
        assertNull(paused.endInstant)
        assertEquals(Duration.ofMinutes(3), paused.remaining(t0.plus(Duration.ofMinutes(2))))
    }

    @Test
    fun `resuming preserves the exact remaining duration across a real time gap`() {
        val running = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0).start(t0)
        val paused = running.pause(t0.plus(Duration.ofMinutes(2)))
        // A large gap between pause and resume - the paused remaining duration
        // must survive it untouched, unlike a running timer's remaining time.
        val resumeAt = t0.plus(Duration.ofHours(3))

        val resumed = paused.resume(resumeAt)

        assertEquals(TimerState.RUNNING, resumed.state)
        assertEquals(resumeAt.plus(Duration.ofMinutes(3)), resumed.endInstant)
        assertNull(resumed.remainingAtPause)
        assertEquals(Duration.ofMinutes(3), resumed.remaining(resumeAt))
    }

    @Test
    fun `a running timer expires once its end instant is reached`() {
        val running = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0).start(t0)

        val expired = running.expire()

        assertEquals(TimerState.EXPIRED, expired.state)
        assertNull(expired.endInstant)
        assertEquals(Duration.ZERO, expired.remaining(t0.plus(Duration.ofMinutes(5))))
    }

    @Test
    fun `reset restores the full configured duration from running, paused, or expired`() {
        val configured = Duration.ofMinutes(5)
        val running = Timer(configuredDuration = configured, createdAt = t0).start(t0)
        val paused = running.pause(t0.plus(Duration.ofMinutes(1)))
        val expired = running.expire()

        for (timer in listOf(running, paused, expired)) {
            val reset = timer.reset()
            assertEquals(TimerState.STOPPED, reset.state)
            assertNull(reset.endInstant)
            assertNull(reset.remainingAtPause)
            assertEquals(configured, reset.remaining(t0))
        }
    }

    @Test
    fun `starting a timer that is not stopped is rejected`() {
        val running = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0).start(t0)

        assertThrows(IllegalArgumentException::class.java) { running.start(t0) }
    }

    @Test
    fun `pausing a timer that is not running is rejected`() {
        val stopped = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0)

        assertThrows(IllegalArgumentException::class.java) { stopped.pause(t0) }
    }

    @Test
    fun `resuming a timer that is not paused is rejected`() {
        val stopped = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0)

        assertThrows(IllegalArgumentException::class.java) { stopped.resume(t0) }
    }

    @Test
    fun `expiring a timer that is not running is rejected`() {
        val stopped = Timer(configuredDuration = Duration.ofMinutes(5), createdAt = t0)

        assertThrows(IllegalArgumentException::class.java) { stopped.expire() }
    }
}
