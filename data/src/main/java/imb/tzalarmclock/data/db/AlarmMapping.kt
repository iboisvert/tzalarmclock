package imb.tzalarmclock.data.db

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AlarmSchedule
import imb.tzalarmclock.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Converts a domain [Alarm] into its stored form. */
internal fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    name = name,
    // Alarms are minute-granular; any sub-minute component is dropped.
    minuteOfDay = time.hour * MINUTES_PER_HOUR + time.minute,
    zoneId = zone?.id,
    scheduleType = schedule.type.name,
    scheduleDate = (schedule as? AlarmSchedule.OnDate)?.date?.toEpochDay(),
    scheduleWeekdays = (schedule as? AlarmSchedule.Weekly)?.days?.toWeekdayMask(),
    scheduleMonthDays = (schedule as? AlarmSchedule.Monthly)?.daysOfMonth?.toMonthDaysCsv(),
    enabled = enabled,
    ringtoneUri = ringtoneUri,
    vibrate = vibrate,
)

/** Converts a stored alarm back into its domain form. */
internal fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    name = name,
    time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR),
    // An alarm can outlive its zone id if the platform's tzdb drops it. Degrading
    // to a floating alarm keeps the rest of the list readable; failing the whole
    // query would not.
    zone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() },
    schedule = toSchedule(),
    enabled = enabled,
    ringtoneUri = ringtoneUri,
    vibrate = vibrate,
)

private fun AlarmEntity.toSchedule(): AlarmSchedule =
    when (val type = ScheduleType.valueOf(scheduleType)) {
        ScheduleType.NEXT_OCCURRENCE -> AlarmSchedule.NextOccurrence

        ScheduleType.ONE_TIME_DATE -> AlarmSchedule.OnDate(
            LocalDate.ofEpochDay(scheduleDate.required(type, "schedule_date")),
        )

        ScheduleType.WEEKLY -> AlarmSchedule.Weekly(
            scheduleWeekdays.required(type, "schedule_weekdays").toWeekdays(),
        )

        ScheduleType.MONTHLY -> AlarmSchedule.Monthly(
            scheduleMonthDays.required(type, "schedule_month_days").toMonthDays(),
        )
    }

private fun <T : Any> T?.required(type: ScheduleType, column: String): T =
    checkNotNull(this) { "Alarm with schedule type $type has no $column" }

private fun Set<DayOfWeek>.toWeekdayMask(): Int =
    fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun Int.toWeekdays(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(linkedSetOf()) { this and (1 shl (it.value - 1)) != 0 }

private fun Set<Int>.toMonthDaysCsv(): String = sorted().joinToString(separator = ",")

private fun String.toMonthDays(): Set<Int> =
    split(",").mapTo(linkedSetOf()) { it.trim().toInt() }

private const val MINUTES_PER_HOUR = 60
