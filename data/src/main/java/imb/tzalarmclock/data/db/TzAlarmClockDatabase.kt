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
    entities = [AlarmEntity::class],
    version = TzAlarmClockDatabase.VERSION,
    exportSchema = true,
)
abstract class TzAlarmClockDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao

    companion object {
        const val VERSION = 1
        const val NAME = "tzalarmclock.db"
    }
}
