package imb.tzalarmclock.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimerCountdownTest {

    @Test
    fun `under an hour reads MM colon SS`() {
        assertEquals("4:59", TimerCountdown.format(Duration.ofSeconds(4 * 60 + 59)))
        assertEquals("0:07", TimerCountdown.format(Duration.ofSeconds(7)))
    }

    @Test
    fun `an hour or more reads H colon MM colon SS`() {
        assertEquals("1:00:00", TimerCountdown.format(Duration.ofHours(1)))
        assertEquals("2:05:09", TimerCountdown.format(Duration.ofHours(2).plusMinutes(5).plusSeconds(9)))
    }

    @Test
    fun `zero reads 0 colon 00`() {
        assertEquals("0:00", TimerCountdown.format(Duration.ZERO))
    }

    @Test
    fun `no rounding - exact seconds are preserved`() {
        assertEquals("0:59", TimerCountdown.format(Duration.ofSeconds(59)))
        assertEquals("1:00", TimerCountdown.format(Duration.ofSeconds(60)))
    }

    @Test
    fun `a negative duration clamps to zero rather than throwing`() {
        assertEquals("0:00", TimerCountdown.format(Duration.ofSeconds(-5)))
    }
}
