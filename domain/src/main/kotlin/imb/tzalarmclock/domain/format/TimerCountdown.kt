package imb.tzalarmclock.domain.format

import java.time.Duration

/**
 * The Timers Summary page's live countdown display.
 *
 * Deliberately a *different* formatter from [FuzzyCountdown]: the spec's
 * fuzzy d/h/min rounding rule is explicitly scoped to the Alarms Summary
 * page, and a live-decrementing timer needs second-level precision that
 * formatter discards by design.
 *
 * Reads `H:MM:SS` once an hour or more is left, `MM:SS` under that — plain,
 * exact, no rounding. A negative duration (shouldn't normally reach this
 * formatter — [imb.tzalarmclock.domain.schedule.remaining] already clamps to
 * zero — but a display formatter should never throw on bad input) is
 * clamped to zero the same way.
 */
object TimerCountdown {

    fun format(duration: Duration): String {
        val totalSeconds = if (duration.isNegative) 0L else duration.seconds
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
}
