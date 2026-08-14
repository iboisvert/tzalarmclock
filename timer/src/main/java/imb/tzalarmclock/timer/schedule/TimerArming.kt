package imb.tzalarmclock.timer.schedule

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import java.time.Instant

/**
 * One timer and the instant it should currently be armed for.
 *
 * Mirrors `imb.tzalarmclock.alarm.schedule.ArmedAlarm` — one pending intent
 * per timer, holding its own fixed [endInstant].
 */
data class ArmedTimer(
    val timerId: Long,
    val endInstant: Instant,
) {
    val triggerAtMillis: Long get() = endInstant.toEpochMilli()
}

/**
 * Which timers should be armed right now, and for when.
 *
 * Deliberately pure, same reasoning as
 * `imb.tzalarmclock.alarm.schedule.armingPlan`: unlike an alarm's next
 * occurrence, a timer's arming instant needs no schedule arithmetic — a
 * [TimerState.RUNNING] timer is armed for its own stored [Timer.endInstant],
 * verbatim. Every other state (including an unsaved timer) has nothing to
 * arm.
 *
 * @return the armed set, soonest first.
 */
fun timerArmingPlan(timers: List<Timer>): List<ArmedTimer> =
    timers.asSequence()
        .filter { it.state == TimerState.RUNNING && it.id != Timer.NO_ID }
        .mapNotNull { timer -> timer.endInstant?.let { ArmedTimer(timer.id, it) } }
        .sortedBy { it.triggerAtMillis }
        .toList()
