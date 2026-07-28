package imb.tzalarmclock.domain.schedule

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When this alarm next rings, or `null` if it never will again.
 *
 * The answer is deliberately computed rather than stored. For a *floating*
 * alarm (no zone of its own) the evaluation zone is `now`'s zone, so simply
 * asking again after the device has moved gives the updated instant — which is
 * the whole reason this app exists. A *zone-locked* alarm is evaluated in its
 * own zone, so the instant it returns is unaffected by where the device is.
 *
 * The result is expressed in `now`'s zone, which is what the UI displays,
 * regardless of which zone it was computed in.
 *
 * Enabled state is ignored: this is pure schedule arithmetic, and callers that
 * care about whether an alarm is armed check [Alarm.enabled] themselves.
 *
 * @param now the current instant, carrying the device's zone.
 * @return the next ring time strictly after [now], or `null` for an alarm
 *   whose only occurrence has passed.
 */
fun Alarm.nextOccurrenceAfter(now: ZonedDateTime): ZonedDateTime? {
    val zone = this.zone ?: now.zone

    // A dated alarm has exactly one candidate day, so there is nothing to search.
    if (schedule is AlarmSchedule.OnDate) {
        return resolve(schedule.date, time, zone)?.takeIfAfter(now)?.inZoneOf(now)
    }

    val start = now.withZoneSameInstant(zone).toLocalDate()
    var date = start
    val limit = start.plusDays(MAX_SEARCH_DAYS)
    while (!date.isAfter(limit)) {
        if (schedule.matches(date)) {
            resolve(date, time, zone)?.takeIfAfter(now)?.let { return it.inZoneOf(now) }
        }
        date = date.plusDays(1)
    }
    return null
}

private fun AlarmSchedule.matches(date: LocalDate): Boolean = when (this) {
    is AlarmSchedule.NextOccurrence -> true
    is AlarmSchedule.OnDate -> date == this.date
    is AlarmSchedule.Weekly -> date.dayOfWeek in days
    // A day the month doesn't have simply never comes up as a candidate date,
    // which is the "skip, don't clamp" behaviour the plan calls for.
    is AlarmSchedule.Monthly -> date.dayOfMonth in daysOfMonth
}

/**
 * Places [time] on [date] in [zone], or returns `null` if that wall-clock time
 * doesn't exist there.
 *
 * Two daylight-saving edge cases, both resolved as the plan specifies:
 * - **Spring-forward gap** — the local time has no valid offset, so the
 *   occurrence is skipped rather than shifted to a time the user didn't set.
 * - **Fall-back overlap** — the local time has two valid offsets, and
 *   [java.time.LocalDateTime.atZone] takes the earlier one, so the alarm rings
 *   on the first pass through that wall-clock time.
 */
private fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime? {
    val local = date.atTime(time)
    if (zone.rules.getValidOffsets(local).isEmpty()) return null
    return local.atZone(zone)
}

/** Strictly after, so an instant that has just fired isn't returned again. */
private fun ZonedDateTime.takeIfAfter(now: ZonedDateTime): ZonedDateTime? =
    takeIf { it.toInstant().isAfter(now.toInstant()) }

private fun ZonedDateTime.inZoneOf(now: ZonedDateTime): ZonedDateTime =
    withZoneSameInstant(now.zone)

/**
 * How far ahead to look for a matching day.
 *
 * A monthly alarm on the 31st can skip two months in a row (January to March),
 * which is the widest real gap at 59 days. A year is a generous bound that also
 * guarantees the search terminates on any input.
 */
private const val MAX_SEARCH_DAYS = 366L
