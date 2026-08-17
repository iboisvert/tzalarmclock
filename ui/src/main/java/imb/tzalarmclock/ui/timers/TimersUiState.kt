package imb.tzalarmclock.ui.timers

import imb.tzalarmclock.domain.format.TimerCountdown
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.remaining
import imb.tzalarmclock.domain.summary.sortedForSummary
import java.time.Instant

/** What the Timers Summary screen renders, derived from the timer list. */
data class TimersUiState(
    val timers: List<TimerRowUi> = emptyList(),
) {
    val isEmpty: Boolean get() = timers.isEmpty()
}

/**
 * One timer row as the Timers Summary screen needs it.
 *
 * @param remainingLabel [TimerCountdown.format] of this timer's remaining
 *   time as of the instant the state was built — live-ticking because
 *   [TimersViewModel] rebuilds this state on a 1-second tick.
 */
data class TimerRowUi(
    val id: Long,
    val remainingLabel: String,
    val state: TimerState,
)

/** Builds the Timers Summary screen's state from the current timers and instant. */
fun buildTimersUiState(timers: List<Timer>, now: Instant): TimersUiState =
    TimersUiState(
        timers = timers.sortedForSummary().map { timer ->
            TimerRowUi(
                id = timer.id,
                remainingLabel = TimerCountdown.format(timer.remaining(now)),
                state = timer.state,
            )
        },
    )
