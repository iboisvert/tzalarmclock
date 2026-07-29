package imb.tzalarmclock.alarm.schedule

import imb.tzalarmclock.domain.model.Alarm

/**
 * Keeps the OS's set of pending alarms in step with the stored alarms.
 *
 * The whole surface is "make the OS match this list" rather than
 * schedule/cancel pairs: callers (the app on launch, the boot receiver, the
 * time-zone receiver, an alarm that has just fired) all want the same thing,
 * and a single idempotent operation removes any chance of the two sets drifting
 * apart.
 */
interface AlarmScheduler {

    /** Arms [alarms], cancelling anything previously armed that isn't in the list. */
    suspend fun sync(alarms: List<Alarm>)

    /** [sync] against a fresh read of storage, for callers holding no list. */
    suspend fun syncAll()
}
