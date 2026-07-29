package imb.tzalarmclock.alarm.schedule

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.schedule.nextOccurrenceAfter
import java.time.ZonedDateTime

/**
 * One alarm and the single instant it should currently be armed for.
 *
 * Only ever the *nearest* future occurrence: `AlarmManager` holds one pending
 * intent per alarm, and the occurrence after this one is armed when this one
 * fires. Nothing further ahead is scheduled, so a device time-zone change only
 * has to move one instant per alarm.
 */
data class ArmedAlarm(
    val alarmId: Long,
    val ringsAt: ZonedDateTime,
) {
    /** Wall-clock-independent trigger value, which is what `AlarmManager` takes. */
    val triggerAtMillis: Long get() = ringsAt.toInstant().toEpochMilli()
}

/**
 * Which alarms should be armed right now, and for when.
 *
 * Deliberately pure: this is the whole decision the scheduler makes, so keeping
 * it free of Android types means the interesting half of Stage 3 is covered by
 * JVM unit tests rather than only by instrumented ones.
 *
 * Alarms are dropped when they are disabled, unsaved, or have no occurrence
 * left at all (a dated alarm whose date has passed) — all three mean "nothing
 * to arm" rather than an error.
 *
 * @param now the current instant, carrying the device's zone. Floating alarms
 *   resolve against this zone, so passing a fresh value after a zone change is
 *   what makes the alarm move.
 * @return the armed set, soonest first.
 */
fun armingPlan(alarms: List<Alarm>, now: ZonedDateTime): List<ArmedAlarm> =
    alarms.asSequence()
        .filter { it.enabled && it.id != Alarm.NO_ID }
        .mapNotNull { alarm ->
            alarm.nextOccurrenceAfter(now)?.let { ArmedAlarm(alarm.id, it) }
        }
        .sortedBy { it.triggerAtMillis }
        .toList()
