package imb.tzalarmclock.domain.schedule

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import java.time.Duration
import java.time.Instant

/**
 * How much time is left on [this] timer at [now].
 *
 * Pure arithmetic derived from whichever of [Timer.endInstant] or
 * [Timer.remainingAtPause] is meaningful for the current [Timer.state] — see
 * their kdoc. A [TimerState.RUNNING] timer's remaining time is clamped to
 * zero rather than going negative once [now] passes [Timer.endInstant]: the
 * scheduler (Stage 15) is what actually flips the timer to
 * [TimerState.EXPIRED] when it fires, and until that write lands this keeps
 * a live display from counting past zero.
 */
fun Timer.remaining(now: Instant): Duration = when (state) {
    TimerState.RUNNING -> {
        val left = Duration.between(now, checkNotNull(endInstant) { "RUNNING timer has no endInstant" })
        if (left.isNegative) Duration.ZERO else left
    }
    TimerState.PAUSED -> checkNotNull(remainingAtPause) { "PAUSED timer has no remainingAtPause" }
    TimerState.STOPPED -> configuredDuration
    TimerState.EXPIRED -> Duration.ZERO
}

/**
 * Starts a [TimerState.STOPPED] (or [TimerState.EXPIRED]/reset-adjacent)
 * timer counting down from its full [Timer.configuredDuration].
 *
 * Only valid from [TimerState.STOPPED] — starting a [TimerState.PAUSED] timer
 * is [resume], not this, since starting fresh would discard progress a user
 * paused specifically to keep.
 */
fun Timer.start(now: Instant): Timer {
    require(state == TimerState.STOPPED) { "Cannot start a timer in state $state" }
    return copy(
        state = TimerState.RUNNING,
        endInstant = now.plus(configuredDuration),
        remainingAtPause = null,
    )
}

/** Pauses a [TimerState.RUNNING] timer, freezing its remaining time. */
fun Timer.pause(now: Instant): Timer {
    require(state == TimerState.RUNNING) { "Cannot pause a timer in state $state" }
    return copy(
        state = TimerState.PAUSED,
        remainingAtPause = remaining(now),
        endInstant = null,
    )
}

/**
 * Resumes a [TimerState.PAUSED] timer.
 *
 * Recomputes [Timer.endInstant] as `now + remainingAtPause` — mirroring how
 * an alarm snooze recomputes its instant from "now" rather than replaying an
 * original schedule — instead of trying to reconstruct what the original end
 * instant "should" have been.
 */
fun Timer.resume(now: Instant): Timer {
    require(state == TimerState.PAUSED) { "Cannot resume a timer in state $state" }
    val remaining = checkNotNull(remainingAtPause) { "PAUSED timer has no remainingAtPause" }
    return copy(
        state = TimerState.RUNNING,
        endInstant = now.plus(remaining),
        remainingAtPause = null,
    )
}

/**
 * Moves a [TimerState.RUNNING] timer that has reached its [Timer.endInstant]
 * to [TimerState.EXPIRED], pinned at zero.
 *
 * Called by the Stage 15 scheduler when a timer's armed OS alarm fires; kept
 * here as pure logic so the `STOPPED -> RUNNING -> EXPIRED` lifecycle is
 * unit-testable without any Android dependency.
 */
fun Timer.expire(): Timer {
    require(state == TimerState.RUNNING) { "Cannot expire a timer in state $state" }
    return copy(state = TimerState.EXPIRED, endInstant = null)
}

/**
 * Returns this timer to a fresh [TimerState.STOPPED] state at its full
 * configured duration, discarding any in-progress or expired countdown.
 *
 * Usable from any state — [TimerState.RUNNING], [TimerState.PAUSED], or
 * [TimerState.EXPIRED] alike — since "start over" is always a valid action
 * regardless of where a timer currently stands.
 */
fun Timer.reset(): Timer = copy(
    state = TimerState.STOPPED,
    remainingAtPause = null,
    endInstant = null,
)
