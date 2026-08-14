package imb.tzalarmclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import imb.tzalarmclock.data.db.MIGRATION_1_2
import imb.tzalarmclock.data.db.TzAlarmClockDatabase
import imb.tzalarmclock.data.repository.RoomAlarmRepository
import imb.tzalarmclock.data.repository.RoomTimerRepository
import imb.tzalarmclock.data.settings.DataStoreSettingsRepository
import imb.tzalarmclock.data.settings.SettingsKeys
import imb.tzalarmclock.domain.repository.AlarmRepository
import imb.tzalarmclock.domain.repository.SettingsRepository
import imb.tzalarmclock.domain.repository.TimerRepository

/**
 * Process-wide singletons for the persistence layer.
 *
 * A hand-rolled holder rather than a DI framework: Stage 1 has exactly two
 * things to construct, and both Room and DataStore require single instances
 * per process (multiple DataStores over one file throw at runtime). This is
 * the seam to replace if a DI framework is introduced later.
 */
object DataProvider {

    @Volatile
    private var database: TzAlarmClockDatabase? = null

    @Volatile
    private var settingsDataStore: DataStore<Preferences>? = null

    fun alarmRepository(context: Context): AlarmRepository =
        RoomAlarmRepository(database(context).alarmDao())

    fun timerRepository(context: Context): TimerRepository =
        RoomTimerRepository(database(context).timerDao())

    fun settingsRepository(context: Context): SettingsRepository =
        DataStoreSettingsRepository(settingsDataStore(context))

    private fun database(context: Context): TzAlarmClockDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                TzAlarmClockDatabase::class.java,
                TzAlarmClockDatabase.NAME,
            ).addMigrations(MIGRATION_1_2).build().also { database = it }
        }

    private fun settingsDataStore(context: Context): DataStore<Preferences> =
        settingsDataStore ?: synchronized(this) {
            settingsDataStore ?: PreferenceDataStoreFactory.create(
                produceFile = {
                    context.applicationContext
                        .preferencesDataStoreFile(SettingsKeys.DATA_STORE_NAME)
                },
            ).also { settingsDataStore = it }
        }
}
