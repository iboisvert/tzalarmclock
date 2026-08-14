package imb.tzalarmclock.data.db

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerMappingTest {

    @Test
    fun `round-trips a fresh stopped timer`() {
        val timer = Timer(
            id = 1,
            configuredDuration = Duration.ofMinutes(5),
            createdAt = Instant.ofEpochMilli(1_000),
        )

        assertEquals(timer, timer.toEntity().toDomain())
    }

    @Test
    fun `round-trips a running timer's end instant`() {
        val timer = Timer(
            id = 2,
            configuredDuration = Duration.ofSeconds(90),
            state = TimerState.RUNNING,
            endInstant = Instant.ofEpochMilli(5_000),
            createdAt = Instant.ofEpochMilli(1_000),
        )

        assertEquals(timer, timer.toEntity().toDomain())
    }

    @Test
    fun `round-trips a paused timer's remaining duration`() {
        val timer = Timer(
            id = 3,
            configuredDuration = Duration.ofMinutes(10),
            state = TimerState.PAUSED,
            remainingAtPause = Duration.ofSeconds(42),
            createdAt = Instant.ofEpochMilli(1_000),
        )

        assertEquals(timer, timer.toEntity().toDomain())
    }

    @Test
    fun `round-trips an expired timer`() {
        val timer = Timer(
            id = 4,
            configuredDuration = Duration.ofSeconds(30),
            state = TimerState.EXPIRED,
            createdAt = Instant.ofEpochMilli(1_000),
        )

        assertEquals(timer, timer.toEntity().toDomain())
    }

    @Test
    fun `only the paused-state column is populated while paused`() {
        val entity = Timer(
            configuredDuration = Duration.ofMinutes(1),
            state = TimerState.PAUSED,
            remainingAtPause = Duration.ofSeconds(20),
            createdAt = Instant.ofEpochMilli(1_000),
        ).toEntity()

        assertEquals(20L, entity.remainingAtPauseSeconds)
        assertNull(entity.endInstantMillis)
    }

    @Test
    fun `only the running-state column is populated while running`() {
        val entity = Timer(
            configuredDuration = Duration.ofMinutes(1),
            state = TimerState.RUNNING,
            endInstant = Instant.ofEpochMilli(9_000),
            createdAt = Instant.ofEpochMilli(1_000),
        ).toEntity()

        assertEquals(9_000L, entity.endInstantMillis)
        assertNull(entity.remainingAtPauseSeconds)
    }
}
