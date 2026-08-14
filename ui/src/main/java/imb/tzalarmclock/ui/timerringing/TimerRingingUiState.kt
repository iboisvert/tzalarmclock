package imb.tzalarmclock.ui.timerringing

import imb.tzalarmclock.domain.format.TimerCountdown
import imb.tzalarmclock.domain.model.Timer

/**
 * What the Timer Ringing screen displays.
 *
 * Unlike [imb.tzalarmclock.ui.ringing.RingingUiState], there's no name/time/
 * zone/date to show — a [Timer] has none of those — so this just carries the
 * original configured duration (e.g. "a 5:00 timer is up") and whether the
 * ring is still active.
 *
 * @param stillRinging `false` once `TimerRingingService` has ended this ring
 *   cycle — mirrors [imb.tzalarmclock.ui.ringing.RingingUiState.stillRinging].
 *   Defaults `true` so the screen never flashes "finished" before the
 *   ViewModel's first real read of it.
 */
data class TimerRingingUiState(
    val configuredDurationLabel: String = "",
    val stillRinging: Boolean = true,
)

fun buildTimerRingingUiState(timer: Timer): TimerRingingUiState =
    TimerRingingUiState(configuredDurationLabel = TimerCountdown.format(timer.configuredDuration))
