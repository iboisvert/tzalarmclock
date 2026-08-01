package imb.tzalarmclock.alarm.ringing

import android.content.Context

/**
 * Per-alarm snooze state: whether the current ring cycle is snoozed, until
 * when, and how many times it's been snoozed so far.
 *
 * On disk rather than in memory for the same reason as `ArmedAlarmRegistry`:
 * the process can die between a snooze and the alarm ringing again, and the
 * snoozed-until instant has to survive that to keep [AndroidAlarmScheduler]
 * honouring it on every resync. [snoozeCount] is what [maxSnoozeCount] in
 * `AppSettings` is compared against to decide whether Snooze is still offered;
 * it resets to zero on [clear], which the ringing flow calls on dismiss (a
 * completed ring cycle), never on fire — so a snooze re-fire keeps counting
 * from where it left off, and a genuinely new occurrence starts fresh.
 */
internal class SnoozeRegistry(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun snoozedUntilMillis(alarmId: Long): Long? =
        prefs.getLong(untilKey(alarmId), NO_VALUE).takeIf { it != NO_VALUE }

    fun snoozeCount(alarmId: Long): Int = prefs.getInt(countKey(alarmId), 0)

    /** Records a snooze: bumps [snoozeCount] and sets the new snoozed-until instant. */
    fun recordSnooze(alarmId: Long, untilMillis: Long) {
        prefs.edit()
            .putLong(untilKey(alarmId), untilMillis)
            .putInt(countKey(alarmId), snoozeCount(alarmId) + 1)
            .apply()
    }

    /** Ends the current ring cycle's snooze tracking: called on dismiss. */
    fun clear(alarmId: Long) {
        prefs.edit()
            .remove(untilKey(alarmId))
            .remove(countKey(alarmId))
            .apply()
    }

    /** Every currently-snoozed alarm id and the instant it's snoozed until, in millis. */
    fun allSnoozedUntilMillis(): Map<Long, Long> =
        prefs.all.keys
            .filter { it.startsWith(UNTIL_PREFIX) }
            .mapNotNull { key -> key.removePrefix(UNTIL_PREFIX).toLongOrNull() }
            .associateWith { snoozedUntilMillis(it) }
            .filterValues { it != null }
            .mapValues { it.value!! }

    private fun untilKey(alarmId: Long) = "$UNTIL_PREFIX$alarmId"
    private fun countKey(alarmId: Long) = "$COUNT_PREFIX$alarmId"

    private companion object {
        const val FILE_NAME = "snoozed_alarms"
        const val UNTIL_PREFIX = "until_"
        const val COUNT_PREFIX = "count_"
        const val NO_VALUE = -1L
    }
}
