package imb.tzalarmclock.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room representation of a timer.
 *
 * Durations and instants are stored as plain seconds/millis columns rather
 * than a serialized blob, matching [AlarmEntity]'s reasoning: it keeps the
 * schema queryable and diffable across migrations.
 */
@Entity(tableName = TimerEntity.TABLE_NAME)
data class TimerEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** The duration this timer counts down from, in whole seconds. */
    @ColumnInfo(name = "configured_duration_seconds")
    val configuredDurationSeconds: Long,

    /** Name of a `TimerState` constant. */
    @ColumnInfo(name = "state")
    val state: String,

    /** Remaining seconds as of the last pause. Set only while `PAUSED`. */
    @ColumnInfo(name = "remaining_at_pause_seconds")
    val remainingAtPauseSeconds: Long? = null,

    /** Epoch millis this timer next fires at. Set only while `RUNNING`. */
    @ColumnInfo(name = "end_instant_millis")
    val endInstantMillis: Long? = null,

    /** Epoch millis this timer was created at. */
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
) {
    companion object {
        const val TABLE_NAME = "timers"
    }
}
