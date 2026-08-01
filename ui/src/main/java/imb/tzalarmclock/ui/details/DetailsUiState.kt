package imb.tzalarmclock.ui.details

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Editable snapshot of a single alarm, backing the Details form.
 *
 * Unlike [imb.tzalarmclock.ui.summary.SummaryUiState] this is mutated in place
 * as the user edits fields, then converted back to an [Alarm] via [toAlarm]
 * on save. [date], [weekdays] and [monthDays] are all kept populated
 * regardless of [scheduleType] so switching between schedule types doesn't
 * discard whatever the user already picked for the others.
 */
data class DetailsUiState(
    val alarmId: Long = Alarm.NO_ID,
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val name: String = "",
    val time: LocalTime = LocalTime.MIDNIGHT,
    val use24HourFormat: Boolean = true,
    val zone: ZoneId? = null,
    val scheduleType: ScheduleType = ScheduleType.NEXT_OCCURRENCE,
    val date: LocalDate = LocalDate.MIN,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val monthDays: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val defaultRingtoneUri: String? = null,
    val vibrate: Boolean? = null,
    val defaultVibrate: Boolean = true,
)

/**
 * Builds the Details form's initial state from a loaded (or brand-new)
 * [alarm] and the current [settings]. [today] seeds the schedule-editor
 * fields that [alarm] doesn't otherwise specify (e.g. a floating alarm has no
 * weekday set yet, but the Weekly editor needs a non-empty starting point).
 */
fun buildDetailsUiState(alarm: Alarm, settings: AppSettings, today: LocalDate): DetailsUiState {
    val schedule = alarm.schedule
    return DetailsUiState(
        alarmId = alarm.id,
        isNew = alarm.id == Alarm.NO_ID,
        isLoading = false,
        name = alarm.name,
        time = alarm.time,
        use24HourFormat = settings.use24HourFormat,
        zone = alarm.zone,
        scheduleType = schedule.type,
        date = (schedule as? AlarmSchedule.OnDate)?.date ?: today,
        weekdays = (schedule as? AlarmSchedule.Weekly)?.days ?: setOf(today.dayOfWeek),
        monthDays = (schedule as? AlarmSchedule.Monthly)?.daysOfMonth ?: setOf(today.dayOfMonth),
        enabled = alarm.enabled,
        ringtoneUri = alarm.ringtoneUri,
        defaultRingtoneUri = settings.defaultRingtoneUri,
        vibrate = alarm.vibrate,
        defaultVibrate = settings.defaultVibrate,
    )
}

/** Converts the current form state back into an [Alarm] for persistence. */
fun DetailsUiState.toAlarm(): Alarm = Alarm(
    id = alarmId,
    name = name.trim(),
    time = time,
    zone = zone,
    schedule = when (scheduleType) {
        ScheduleType.NEXT_OCCURRENCE -> AlarmSchedule.NextOccurrence
        ScheduleType.ONE_TIME_DATE -> AlarmSchedule.OnDate(date)
        ScheduleType.WEEKLY -> AlarmSchedule.Weekly(weekdays)
        ScheduleType.MONTHLY -> AlarmSchedule.Monthly(monthDays)
    },
    enabled = enabled,
    ringtoneUri = ringtoneUri,
    vibrate = vibrate,
)

/**
 * Toggles [day] in [DetailsUiState.weekdays], refusing to clear the last
 * remaining day so [toAlarm] can never attempt to build an empty
 * [AlarmSchedule.Weekly] (which throws).
 */
fun DetailsUiState.withWeekdayToggled(day: DayOfWeek): DetailsUiState {
    val toggled = if (day in weekdays) weekdays - day else weekdays + day
    return if (toggled.isEmpty()) this else copy(weekdays = toggled)
}

/**
 * Toggles [day] in [DetailsUiState.monthDays], refusing to clear the last
 * remaining day so [toAlarm] can never attempt to build an empty
 * [AlarmSchedule.Monthly] (which throws).
 */
fun DetailsUiState.withMonthDayToggled(day: Int): DetailsUiState {
    val toggled = if (day in monthDays) monthDays - day else monthDays + day
    return if (toggled.isEmpty()) this else copy(monthDays = toggled)
}
