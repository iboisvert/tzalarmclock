package imb.tzalarmclock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Alarm queries.
 *
 * Rows are ordered by time of day purely so results are stable and readable in
 * tests; the user-facing order is by time-to-ring, which needs the Stage 2
 * engine and so is applied above this layer.
 */
@Dao
interface AlarmDao {

    @Query("SELECT * FROM ${AlarmEntity.TABLE_NAME} ORDER BY minute_of_day ASC, id ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM ${AlarmEntity.TABLE_NAME} WHERE id = :id")
    fun observeById(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM ${AlarmEntity.TABLE_NAME} WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query(
        "SELECT * FROM ${AlarmEntity.TABLE_NAME} WHERE enabled = 1 " +
            "ORDER BY minute_of_day ASC, id ASC",
    )
    suspend fun getEnabled(): List<AlarmEntity>

    /** @return the newly assigned row id. */
    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("UPDATE ${AlarmEntity.TABLE_NAME} SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM ${AlarmEntity.TABLE_NAME} WHERE id = :id")
    suspend fun deleteById(id: Long)
}
