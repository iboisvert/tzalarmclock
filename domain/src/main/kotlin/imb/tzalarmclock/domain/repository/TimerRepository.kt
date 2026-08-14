package imb.tzalarmclock.domain.repository

import imb.tzalarmclock.domain.model.Timer
import kotlinx.coroutines.flow.Flow

/**
 * Storage for timers.
 *
 * Mirrors [AlarmRepository]'s Flow-based observe-and-write shape so the
 * Timers Summary page (Stage 16) can observe reactively and the timer
 * scheduler (Stage 15) can re-sync as timers are started, paused, and reset.
 */
interface TimerRepository {

    /** All timers, emitting again whenever any of them changes. */
    fun observeTimers(): Flow<List<Timer>>

    /** A single timer, emitting `null` once it is deleted. */
    fun observeTimer(id: Long): Flow<Timer?>

    /** One-shot read, for callers that aren't observing (e.g. boot re-arming). */
    suspend fun getTimer(id: Long): Timer?

    /** One-shot read of every currently-running timer. */
    suspend fun getRunningTimers(): List<Timer>

    /**
     * Inserts [timer] if its id is [Timer.NO_ID], otherwise replaces the
     * existing row.
     *
     * @return the timer's id, which for an insert is newly assigned.
     */
    suspend fun save(timer: Timer): Long

    /** Deletes the timer, if it exists. */
    suspend fun delete(id: Long)
}
