package imb.tzalarmclock.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's SQLite database.
 *
 * Schemas are exported to `data/schemas` so the migrations later stages need
 * can be written against a checked-in baseline.
 */
@Database(
    entities = [AlarmEntity::class, TimerEntity::class],
    version = TzAlarmClockDatabase.VERSION,
    exportSchema = true,
)
abstract class TzAlarmClockDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

    abstract fun timerDao(): TimerDao

    companion object {
        const val VERSION = 2
        const val NAME = "tzalarmclock.db"
    }
}
