package imb.tzalarmclock.domain.format

import imb.tzalarmclock.domain.model.AlarmSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Summary page's recurrence label for a recurring alarm, e.g.
 * `"Weekly Mon, Wed, Fri"` or `"Monthly 1, 15"`.
 *
 * Not part of the spec's Summary page field list — an addition, not an
 * ambiguity resolution. Weekly labels list days in calendar order starting
 * Monday, regardless of the device locale's first day of week, so the label
 * reads the same everywhere, using the platform's short weekday name
 * (`TextStyle.SHORT`, e.g. "Mon", "Tue") rather than the single-letter narrow
 * form, since narrow abbreviations collide (Tuesday/Thursday both narrow to
 * "T", Saturday/Sunday both narrow to "S"). Monthly labels list days of month
 * ascending.
 *
 * ⚠ This label is English-only, unlike the spec's explicitly
 * locale-independent countdown labels.
 */
object RecurrenceLabel {

    /** `null` for a non-recurring schedule. */
    fun format(schedule: AlarmSchedule): String? = when (schedule) {
        is AlarmSchedule.Weekly ->
            "$WEEKLY ${schedule.days.sortedBy { it.value }.joinToString(", ") { it.short() }}"
        is AlarmSchedule.Monthly ->
            "$MONTHLY ${schedule.daysOfMonth.sorted().joinToString(", ")}"
        AlarmSchedule.NextOccurrence, is AlarmSchedule.OnDate -> null
    }

    private fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private const val WEEKLY = "Weekly"
    private const val MONTHLY = "Monthly"
}
