package imb.tzalarmclock

import imb.tzalarmclock.alarm.schedule.armingPlan
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The pure half of [AlarmClockIntentActivity]: turning the extras of an
 * `android.provider.AlarmClock` request into alarms, kept free of `Intent` so
 * it's covered by JVM unit tests.
 */

/**
 * The alarm an `ACTION_SET_ALARM` request describes, or `null` when [hour] or
 * [minute] is out of range.
 *
 * Always *floating*: the AlarmClock contract has no time-zone extra, and
 * "wake me at 7" from a voice assistant means 7 wherever the device is.
 *
 * @param calendarDays `java.util.Calendar` day constants (`SUNDAY` = 1 ..
 *   `SATURDAY` = 7), as `AlarmClock.EXTRA_DAYS` carries them. Values outside
 *   that range are dropped; none left means a one-shot alarm.
 * @param ringtoneUri a content URI, or `null` for the default sound. The
 *   contract's "silent" value has no equivalent here, so the caller passes
 *   `null` for it.
 */
internal fun alarmForSetRequest(
    hour: Int,
    minute: Int,
    message: String?,
    calendarDays: List<Int>?,
    vibrate: Boolean?,
    ringtoneUri: String?,
): Alarm? {
    if (hour !in 0..23 || minute !in 0..59) return null
    val days = calendarDays.orEmpty().mapNotNull(::calendarDayToDayOfWeek).toSet()
    return Alarm(
        name = message?.trim().orEmpty(),
        time = LocalTime.of(hour, minute),
        schedule = if (days.isEmpty()) AlarmSchedule.NextOccurrence else AlarmSchedule.Weekly(days),
        vibrate = vibrate,
        ringtoneUri = ringtoneUri,
    )
}

/**
 * An existing alarm [requested] would duplicate: same name, time and schedule,
 * and floating like every alarm a request creates. Re-enabling it instead of
 * adding a copy keeps "set an alarm for 7" said every evening from piling up
 * identical rows.
 */
internal fun findEquivalentAlarm(existing: List<Alarm>, requested: Alarm): Alarm? =
    existing.firstOrNull {
        it.zone == null &&
            it.time == requested.time &&
            it.schedule == requested.schedule &&
            it.name.equals(requested.name, ignoreCase = true)
    }

/** Which alarms an `ACTION_DISMISS_ALARM` request's search mode selects. */
internal sealed interface DismissTarget {
    /** `ALARM_SEARCH_MODE_ALL`: every enabled alarm. */
    data object All : DismissTarget

    /** `ALARM_SEARCH_MODE_NEXT`: the enabled alarm that will ring soonest. */
    data object Next : DismissTarget

    /** `ALARM_SEARCH_MODE_LABEL`: enabled alarms named [label], ignoring case. */
    data class Label(val label: String) : DismissTarget

    /** `ALARM_SEARCH_MODE_TIME`: enabled alarms set for [hour]:[minute]. */
    data class Time(val hour: Int, val minute: Int) : DismissTarget
}

/**
 * The alarms [target] selects.
 *
 * @param now the current instant in the device's zone, which "next" is
 *   relative to.
 * @param snoozedUntil currently-snoozed alarms, so a snoozed alarm counts as
 *   next by its snooze instant — the same thing the scheduler arms.
 */
internal fun alarmsToDismiss(
    target: DismissTarget,
    alarms: List<Alarm>,
    now: ZonedDateTime,
    snoozedUntil: Map<Long, Instant>,
): List<Alarm> {
    val enabled = alarms.filter { it.enabled }
    return when (target) {
        DismissTarget.All -> enabled
        DismissTarget.Next -> {
            val nextId = armingPlan(enabled, now, snoozedUntil).firstOrNull()?.alarmId
            enabled.filter { it.id == nextId }
        }
        is DismissTarget.Label -> enabled.filter { it.name.trim().equals(target.label.trim(), ignoreCase = true) }
        is DismissTarget.Time -> enabled.filter { it.time.hour == target.hour && it.time.minute == target.minute }
    }
}

/**
 * Resolves a request's hour to 0..23. `AlarmClock.EXTRA_IS_PM` only applies to
 * a 12-hour value (1..12); without it, or for an hour that's already 24-hour,
 * [hour] is taken as-is.
 */
internal fun to24Hour(hour: Int, isPm: Boolean?): Int = when {
    isPm == null || hour !in 1..12 -> hour
    isPm -> hour % 12 + 12
    else -> hour % 12
}

/** `Calendar.SUNDAY` (1) .. `Calendar.SATURDAY` (7) to [DayOfWeek], or `null` out of range. */
internal fun calendarDayToDayOfWeek(calendarDay: Int): DayOfWeek? =
    if (calendarDay in 1..7) DayOfWeek.of((calendarDay + 5) % 7 + 1) else null
