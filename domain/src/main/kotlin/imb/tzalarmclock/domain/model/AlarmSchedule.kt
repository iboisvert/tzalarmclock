package imb.tzalarmclock.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which days an alarm rings on.
 *
 * The spec allows an alarm to be defined by time alone, on a specific date, or
 * recurring — but never a combination — so the variants are modelled as a
 * closed hierarchy rather than independent nullable fields.
 */
sealed interface AlarmSchedule {

    val type: ScheduleType

    /**
     * No date and no recurrence: rings once at the next occurrence of the
     * alarm's time. This is the spec's baseline case ("the only temporal field
     * required to define an alarm is time").
     */
    data object NextOccurrence : AlarmSchedule {
        override val type: ScheduleType get() = ScheduleType.NEXT_OCCURRENCE
    }

    /** Rings once, on [date]. */
    data class OnDate(val date: LocalDate) : AlarmSchedule {
        override val type: ScheduleType get() = ScheduleType.ONE_TIME_DATE
    }

    /** Recurs on the given days of every week. */
    data class Weekly(val days: Set<DayOfWeek>) : AlarmSchedule {
        init {
            require(days.isNotEmpty()) { "A weekly schedule needs at least one day" }
        }

        override val type: ScheduleType get() = ScheduleType.WEEKLY
    }

    /**
     * Recurs on the given days of every month. Days a given month doesn't have
     * (e.g. the 31st in April) are skipped for that month — see the Stage 2
     * assumption in the development plan.
     */
    data class Monthly(val daysOfMonth: Set<Int>) : AlarmSchedule {
        init {
            require(daysOfMonth.isNotEmpty()) { "A monthly schedule needs at least one day" }
            require(daysOfMonth.all { it in MIN_DAY_OF_MONTH..MAX_DAY_OF_MONTH }) {
                "Days of month must be in $MIN_DAY_OF_MONTH..$MAX_DAY_OF_MONTH, was $daysOfMonth"
            }
        }

        override val type: ScheduleType get() = ScheduleType.MONTHLY

        companion object {
            const val MIN_DAY_OF_MONTH = 1
            const val MAX_DAY_OF_MONTH = 31
        }
    }

    /** True when the alarm can ring more than once. */
    val isRecurring: Boolean
        get() = when (this) {
            is NextOccurrence, is OnDate -> false
            is Weekly, is Monthly -> true
        }
}

/**
 * Storage discriminator for [AlarmSchedule]. Kept separate from the sealed
 * hierarchy so the persisted representation is a stable name rather than a
 * class identity.
 */
enum class ScheduleType {
    NEXT_OCCURRENCE,
    ONE_TIME_DATE,
    WEEKLY,
    MONTHLY,
}
