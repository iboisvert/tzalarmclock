package imb.tzalarmclock.domain.summary

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.schedule.nextOccurrenceAfter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The four buckets the Summary page presents alarms in, in display order. */
enum class AlarmGroup {
    TODAY,
    TOMORROW,
    THIS_WEEK,
    LATER,
}

/**
 * One alarm as the Summary page needs it.
 *
 * @param localTime the alarm's time rendered in the device's zone. Equal to
 *   `alarm.time` for a floating alarm; for a zone-locked one this is the
 *   converted time, which is the value the Summary page shows for every alarm.
 * @param nextOccurrence when the alarm next rings, in the device's zone, or
 *   `null` when it is disabled or has no future occurrence. The spec shows the
 *   next-ring date and countdown only for enabled alarms, so `null` is exactly
 *   the signal to omit both.
 */
data class AlarmSummaryEntry(
    val alarm: Alarm,
    val localTime: LocalTime,
    val nextOccurrence: ZonedDateTime?,
)

/** A non-empty [AlarmGroup] and its alarms, already ordered. */
data class AlarmSummarySection(
    val group: AlarmGroup,
    val entries: List<AlarmSummaryEntry>,
)

/**
 * Groups [alarms] for the Summary page and orders each group by time-to-ring.
 *
 * Groups are mutually exclusive and resolved in the device's zone: *Today* and
 * *Tomorrow* are calendar-day matches, *This Week* is the rest of the current
 * calendar week, and *Later* is everything beyond it. When today is the last
 * day of the week, tomorrow belongs to the next one but still groups as
 * *Tomorrow*, leaving *This Week* empty.
 *
 * Alarms that have no time-to-ring — disabled ones, and one-time alarms whose
 * date has passed — are appended to *Later*, ordered by time of day. This is
 * the placeholder the development plan flags for a product decision, not a
 * settled answer.
 *
 * Empty groups are omitted, so the result contains between zero and four
 * sections in [AlarmGroup] order.
 *
 * @param now the current instant, carrying the device's zone.
 * @param firstDayOfWeek which day the calendar week starts on; defaults to the
 *   device locale's.
 * @param snoozedUntil alarms currently snoozed, and the instant they're
 *   snoozed until — used as that alarm's next-ring instant instead of its
 *   normally-computed next occurrence, so a snoozed alarm's displayed
 *   group/date/countdown matches what's actually armed with `AlarmManager`
 *   (see `AndroidAlarmScheduler`'s arming plan, which honours the same
 *   registry). Entries already in the past are ignored, same as there.
 */
fun summarizeAlarms(
    alarms: List<Alarm>,
    now: ZonedDateTime,
    firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    snoozedUntil: Map<Long, Instant> = emptyMap(),
): List<AlarmSummarySection> {
    val today = now.toLocalDate()
    val tomorrow = today.plusDays(1)
    val endOfWeek = today
        .with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        .plusDays(DAYS_PER_WEEK - 1)

    val entries = alarms.map { alarm ->
        // Computed for disabled alarms too: it is what renders their time in the
        // device's zone, even though their countdown is withheld.
        val snoozed = snoozedUntil[alarm.id]?.takeIf { it.isAfter(now.toInstant()) }
        val occurrence = snoozed?.atZone(now.zone) ?: alarm.nextOccurrenceAfter(now)
        AlarmSummaryEntry(
            alarm = alarm,
            localTime = occurrence?.toLocalTime() ?: alarm.time,
            nextOccurrence = occurrence.takeIf { alarm.enabled },
        )
    }

    return entries
        .groupBy { entry ->
            val date = entry.nextOccurrence?.toLocalDate() ?: return@groupBy AlarmGroup.LATER
            when {
                date == today -> AlarmGroup.TODAY
                date == tomorrow -> AlarmGroup.TOMORROW
                !date.isAfter(endOfWeek) -> AlarmGroup.THIS_WEEK
                else -> AlarmGroup.LATER
            }
        }
        .toSortedMap()
        .map { (group, groupEntries) ->
            AlarmSummarySection(group, groupEntries.sortedWith(BY_TIME_TO_RING))
        }
}

/**
 * Soonest first. Entries with no time-to-ring sort last, among themselves by
 * time of day, then by name and id so the order is stable across re-renders.
 */
private val BY_TIME_TO_RING: Comparator<AlarmSummaryEntry> = compareBy(
    { it.nextOccurrence == null },
    { it.nextOccurrence?.toInstant() },
    { it.localTime },
    { it.alarm.name },
    { it.alarm.id },
)

private const val DAYS_PER_WEEK = 7L
