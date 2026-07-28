package imb.tzalarmclock.domain.repository

import imb.tzalarmclock.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Storage for alarms.
 *
 * Reads are exposed as [Flow] so the Summary page (Stage 4) re-renders on any
 * change without polling, and so the scheduler (Stage 3) can re-arm alarms as
 * they are edited.
 */
interface AlarmRepository {

    /** All alarms, emitting again whenever any of them changes. */
    fun observeAlarms(): Flow<List<Alarm>>

    /** A single alarm, emitting `null` once it is deleted. */
    fun observeAlarm(id: Long): Flow<Alarm?>

    /** One-shot read, for callers that aren't observing (e.g. boot re-arming). */
    suspend fun getAlarm(id: Long): Alarm?

    /** One-shot read of every enabled alarm. */
    suspend fun getEnabledAlarms(): List<Alarm>

    /**
     * Inserts [alarm] if its id is [Alarm.NO_ID], otherwise replaces the
     * existing row.
     *
     * @return the alarm's id, which for an insert is newly assigned.
     */
    suspend fun save(alarm: Alarm): Long

    /** Flips the enabled flag without touching the rest of the alarm. */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Deletes the alarm, if it exists. */
    suspend fun delete(id: Long)
}
