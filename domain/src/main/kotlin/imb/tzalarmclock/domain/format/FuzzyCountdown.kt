package imb.tzalarmclock.domain.format

import java.time.Duration
import java.time.Instant

/**
 * The Summary page's "fuzzy" countdown to an alarm.
 *
 * The spec fixes the algorithm exactly:
 * - a period of at least a day reads `"N d, M h"`, with `M` omitted when zero;
 * - at least an hour reads `"N h"`;
 * - anything shorter reads `"N min"`.
 *
 * `N` and `M` are integers rounded half-to-even ("bankers rounding"), so 2.5 h
 * reads `"2 h"` and 3.5 h reads `"4 h"`. The unit labels are deliberately not
 * localized: `d`, `h` and `min` are accepted non-SI units per NIST SP 330.
 *
 * Which branch applies is decided by the exact period, not the rounded one, so
 * 59 min 59 s is still a minutes value — `"60 min"`, not `"1 h"`.
 *
 * ⚠ One deliberate deviation from that literal algorithm: when the minutes
 * value itself would round to zero, this reads `"< 1 min"` rather than a bare
 * `"0 min"` — a genuinely-imminent alarm reading "0 min" is easily misread as
 * "no time left" rather than "seconds away".
 */
object FuzzyCountdown {

    /** Formats the period from [from] until [until]. */
    fun format(from: Instant, until: Instant): String =
        format(Duration.between(from, until))

    /** Formats [duration]. A period that has already elapsed reads `"< 1 min"`, same as an imminent one. */
    fun format(duration: Duration): String {
        val seconds = if (duration.isNegative) 0L else duration.seconds
        return when {
            seconds >= SECONDS_PER_DAY -> formatDays(seconds)
            seconds >= SECONDS_PER_HOUR -> "${round(seconds, SECONDS_PER_HOUR)} $HOURS"
            else -> formatMinutes(seconds)
        }
    }

    private fun formatMinutes(seconds: Long): String {
        val minutes = round(seconds, SECONDS_PER_MINUTE)
        return if (minutes == 0L) LESS_THAN_ONE_MINUTE else "$minutes $MINUTES"
    }

    private fun formatDays(seconds: Long): String {
        var days = seconds / SECONDS_PER_DAY
        var hours = round(seconds - days * SECONDS_PER_DAY, SECONDS_PER_HOUR)
        // Rounding the remainder can reach a full day, e.g. 1 d 23 h 40 min.
        if (hours == HOURS_PER_DAY) {
            days += 1
            hours = 0
        }
        return if (hours == 0L) "$days $DAYS" else "$days $DAYS, $hours $HOURS"
    }

    /** [seconds] expressed in units of [secondsPerUnit], rounded half-to-even. */
    private fun round(seconds: Long, secondsPerUnit: Long): Long =
        Math.rint(seconds.toDouble() / secondsPerUnit).toLong()

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
    private const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
    private const val HOURS_PER_DAY = 24L

    private const val DAYS = "d"
    private const val HOURS = "h"
    private const val MINUTES = "min"
    private const val LESS_THAN_ONE_MINUTE = "< 1 min"
}
