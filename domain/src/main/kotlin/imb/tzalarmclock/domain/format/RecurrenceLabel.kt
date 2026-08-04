package imb.tzalarmclock.domain.format

import imb.tzalarmclock.domain.model.AlarmSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Summary page's recurrence label for a recurring alarm, e.g.
 * `"Weekly M, W, F"` or `"Monthly 1, 15"`.
 *
 * Not part of the spec's Summary page field list — an addition, not an
 * ambiguity resolution. Weekly labels list days in calendar order starting
 * Monday, regardless of the device locale's first day of week, so the label
 * reads the same everywhere. Monthly labels list days of month ascending.
 *
 * ⚠ Single-letter weekday abbreviations aren't unique in English (Tuesday and
 * Thursday both narrow to "T", Saturday and Sunday both narrow to "S"), but
 * are used anyway to match the literal example given for this feature; this
 * label is also English-only, unlike the spec's explicitly locale-independent
 * countdown labels.
 */
object RecurrenceLabel {

    /** `null` for a non-recurring schedule. */
    fun format(schedule: AlarmSchedule): String? = when (schedule) {
        is AlarmSchedule.Weekly ->
            "$WEEKLY ${schedule.days.sortedBy { it.value }.joinToString(", ") { it.narrow() }}"
        is AlarmSchedule.Monthly ->
            "$MONTHLY ${schedule.daysOfMonth.sorted().joinToString(", ")}"
        AlarmSchedule.NextOccurrence, is AlarmSchedule.OnDate -> null
    }

    private fun DayOfWeek.narrow(): String = getDisplayName(TextStyle.NARROW, Locale.ENGLISH)

    private const val WEEKLY = "Weekly"
    private const val MONTHLY = "Monthly"
}
