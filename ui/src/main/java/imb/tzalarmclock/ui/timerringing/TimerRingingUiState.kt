package imb.tzalarmclock.ui.timerringing

import imb.tzalarmclock.domain.format.TimerCountdown
import imb.tzalarmclock.domain.model.Timer

/**
 * What the Timer Ringing screen displays.
 *
 * Unlike [imb.tzalarmclock.ui.ringing.RingingUiState], there's no name/time/
 * zone/date to show — a [Timer] has none of those — so this just carries
 * every currently-ringing timer's original configured duration (e.g. "a
 * 5:00 timer is up") and whether the ring is still active. More than one
 * label means more than one timer fired while this ring cycle was already
 * up — see `imb.tzalarmclock.timer.ringing.TimerRingingService`'s class doc
 * — not that a single timer somehow has several durations.
 *
 * @param stillRinging `false` once `TimerRingingService` has ended this ring
 *   cycle — mirrors [imb.tzalarmclock.ui.ringing.RingingUiState.stillRinging].
 *   Defaults `true` so the screen never flashes "finished" before the
 *   ViewModel's first real read of it.
 */
data class TimerRingingUiState(
    val configuredDurationLabels: List<String> = emptyList(),
    val stillRinging: Boolean = true,
)

fun buildTimerRingingUiState(timers: List<Timer>): TimerRingingUiState =
    TimerRingingUiState(configuredDurationLabels = timers.map { TimerCountdown.format(it.configuredDuration) })
