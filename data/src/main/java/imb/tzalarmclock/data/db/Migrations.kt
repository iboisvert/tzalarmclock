package imb.tzalarmclock.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The project's first real schema migration: adds the [TimerEntity] table
 * introduced by Stage 13, leaving the existing `alarms` table untouched.
 *
 * The `CREATE TABLE` here must match what Room itself would generate for
 * [TimerEntity] — it's copied from the exported
 * `data/schemas/.../2.json` baseline rather than hand-written, so
 * [MigrationTest] (which validates against that same baseline) is exercising
 * a real, checked drift risk rather than a foregone conclusion.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `${TimerEntity.TABLE_NAME}` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`configured_duration_seconds` INTEGER NOT NULL, " +
                "`state` TEXT NOT NULL, " +
                "`remaining_at_pause_seconds` INTEGER, " +
                "`end_instant_millis` INTEGER, " +
                "`created_at_millis` INTEGER NOT NULL)",
        )
    }
}
