package imb.tzalarmclock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Timer queries.
 *
 * Rows are ordered by creation so results are stable and readable in tests;
 * the user-facing order (running/paused first by remaining time, then
 * stopped/expired by creation order) needs the Stage 14 engine and so is
 * applied above this layer.
 */
@Dao
interface TimerDao {

    @Query("SELECT * FROM ${TimerEntity.TABLE_NAME} ORDER BY created_at_millis ASC, id ASC")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM ${TimerEntity.TABLE_NAME} WHERE id = :id")
    fun observeById(id: Long): Flow<TimerEntity?>

    @Query("SELECT * FROM ${TimerEntity.TABLE_NAME} WHERE id = :id")
    suspend fun getById(id: Long): TimerEntity?

    @Query("SELECT * FROM ${TimerEntity.TABLE_NAME} WHERE state = 'RUNNING'")
    suspend fun getRunning(): List<TimerEntity>

    /** @return the newly assigned row id. */
    @Insert
    suspend fun insert(timer: TimerEntity): Long

    @Update
    suspend fun update(timer: TimerEntity)

    @Query("DELETE FROM ${TimerEntity.TABLE_NAME} WHERE id = :id")
    suspend fun deleteById(id: Long)
}
