package imb.tzalarmclock.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 13 exit criterion: the v1 -> v2 migration (adding [TimerEntity])
 * leaves existing [AlarmEntity] rows untouched.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TzAlarmClockDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrating1To2PreservesExistingAlarms() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO alarms " +
                    "(id, name, minute_of_day, zone_id, schedule_type, enabled, ringtone_uri, vibrate) " +
                    "VALUES (1, 'Wake up', 405, NULL, 'NEXT_OCCURRENCE', 1, NULL, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migrated.query("SELECT id, name, minute_of_day FROM alarms").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("Wake up", cursor.getString(1))
            assertEquals(405, cursor.getInt(2))
            assertEquals(false, cursor.moveToNext())
        }
        migrated.query("SELECT COUNT(*) FROM timers").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
