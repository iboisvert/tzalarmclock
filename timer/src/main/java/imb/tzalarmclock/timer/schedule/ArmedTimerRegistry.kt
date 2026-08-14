package imb.tzalarmclock.timer.schedule

import android.content.Context

/**
 * Which timer ids we last handed to `AlarmManager`.
 *
 * Mirrors `imb.tzalarmclock.alarm.schedule.ArmedAlarmRegistry`: needed
 * because the OS won't tell us, and to cancel the alarm for a row that has
 * since been deleted we have to reconstruct its pending intent, which needs
 * its id, which is no longer in the database. On disk rather than in memory
 * so it survives the process being killed and restarted between one sync
 * and the next.
 */
internal class ArmedTimerRegistry(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun armedIds(): Set<Long> =
        prefs.getStringSet(KEY_ARMED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun replace(ids: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_ARMED_IDS, ids.mapTo(mutableSetOf()) { it.toString() })
            .apply()
    }

    private companion object {
        const val FILE_NAME = "armed_timers"
        const val KEY_ARMED_IDS = "armed_ids"
    }
}
