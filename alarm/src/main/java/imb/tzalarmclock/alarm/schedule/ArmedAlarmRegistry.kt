package imb.tzalarmclock.alarm.schedule

import android.content.Context

/**
 * Which alarm ids we last handed to `AlarmManager`.
 *
 * Needed because the OS won't tell us: to cancel the alarm for a row that has
 * since been deleted we have to reconstruct its pending intent, which needs its
 * id, which is no longer in the database. Keeping the set on disk rather than
 * in memory means it also survives the process being killed and restarted
 * between one sync and the next — pending intents outlive our process, so an
 * in-memory record would leak alarms for deleted rows.
 */
internal class ArmedAlarmRegistry(context: Context) {

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
        const val FILE_NAME = "armed_alarms"
        const val KEY_ARMED_IDS = "armed_ids"
    }
}
