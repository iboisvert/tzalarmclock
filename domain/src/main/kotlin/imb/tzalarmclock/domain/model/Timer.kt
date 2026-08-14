package imb.tzalarmclock.domain.model

import java.time.Duration
import java.time.Instant

/**
 * A single countdown timer as the user defined it.
 *
 * Unlike [Alarm], a timer has no name, time zone, or recurrence — just a
 * configured duration and a state machine (see
 * `imb.tzalarmclock.domain.schedule.TimerTransitions`, Stage 14) that walks it
 * through running, pausing, and expiring.
 *
 * @param id database id, or [NO_ID] for a timer that hasn't been saved yet.
 * @param configuredDuration the duration this timer counts down from —
 *   entered via the Add Timer h/min/s fields, but stored as a single value
 *   since the three-field split is an entry-form UX, not a schema need.
 * @param state which phase of the state machine this timer is in.
 * @param remainingAtPause how much time was left when this timer was last
 *   paused. Only meaningful while [state] is [TimerState.PAUSED]; `null`
 *   otherwise.
 * @param endInstant the fixed wall-clock instant this timer will next fire.
 *   Only meaningful while [state] is [TimerState.RUNNING]; `null` otherwise.
 *   Persisting the instant itself, not just a relative remaining duration, is
 *   what lets a running timer survive a reboot or hard-stop with no
 *   adjustment for how long the device was off — the same reasoning
 *   `SnoozeRegistry` documents for alarms.
 * @param createdAt when this timer was first created, used to order
 *   stopped/expired timers on the Timers Summary page (Stage 14) once they
 *   have no remaining-time ordering left to sort by.
 */
data class Timer(
    val id: Long = NO_ID,
    val configuredDuration: Duration,
    val state: TimerState = TimerState.STOPPED,
    val remainingAtPause: Duration? = null,
    val endInstant: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        /** Id of a timer that has not been persisted yet. */
        const val NO_ID: Long = 0L
    }
}

/** Which phase of a [Timer]'s countdown it's currently in. */
enum class TimerState {
    /** Fresh or reset: not counting down, full [Timer.configuredDuration] left. */
    STOPPED,

    /** Counting down toward [Timer.endInstant]. */
    RUNNING,

    /** Counting down paused, with [Timer.remainingAtPause] left. */
    PAUSED,

    /** Reached zero and is ringing; pinned at zero until reset. */
    EXPIRED,
}
