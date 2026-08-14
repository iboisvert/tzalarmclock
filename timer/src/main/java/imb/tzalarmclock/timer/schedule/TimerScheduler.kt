package imb.tzalarmclock.timer.schedule

import imb.tzalarmclock.domain.model.Timer

/**
 * Keeps the OS's set of pending timers in step with the stored timers.
 *
 * Mirrors [imb.tzalarmclock.alarm.schedule.AlarmScheduler]'s "make the OS
 * match this list" shape rather than schedule/cancel pairs — the same
 * `AlarmManager.setAlarmClock()` primitive backs both, so the same
 * idempotent-sync reasoning applies.
 */
interface TimerScheduler {

    /** Arms [timers], cancelling anything previously armed that isn't in the list. */
    suspend fun sync(timers: List<Timer>)

    /** [sync] against a fresh read of storage, for callers holding no list. */
    suspend fun syncAll()
}
