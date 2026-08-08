package imb.tzalarmclock.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import imb.tzalarmclock.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId

/**
 * Settings round-trips, including the Stage 1 exit criterion that they survive
 * an app restart: the DataStore is torn down and rebuilt over the same file,
 * which is what a process restart looks like to DataStore.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var file: File
    private var open: OpenStore? = null

    @Before
    fun setUp() {
        file = File(context.filesDir, "settings-test.preferences_pb")
        file.delete()
    }

    @After
    fun tearDown() {
        runBlocking { shutDown() }
        file.delete()
    }

    @Test
    fun aFreshInstallReadsTheDefaults() = runTest {
        assertEquals(AppSettings.DEFAULTS, repository().getSettings())
    }

    @Test
    fun everySettingRoundTrips() = runTest {
        val repository = repository()
        val settings = AppSettings(
            homeZone = ZoneId.of("America/Toronto"),
            snoozePeriodMinutes = 7,
            maxSnoozeCount = 2,
            defaultRingtoneUri = "content://media/internal/audio/media/3",
            alarmVolume = 0.6f,
            volumeEscalation = true,
            defaultVibrate = false,
            use24HourFormat = false,
            ringTimeoutMinutes = 8,
        )

        repository.save(settings)

        assertEquals(settings, repository.getSettings())
    }

    @Test
    fun clearingTheHomeZoneAndRingtoneRestoresTheFallbackBehaviour() = runTest {
        val repository = repository()
        repository.save(
            AppSettings(
                homeZone = ZoneId.of("America/Toronto"),
                defaultRingtoneUri = "content://media/internal/audio/media/3",
            ),
        )

        repository.save(AppSettings(homeZone = null, defaultRingtoneUri = null))

        val settings = repository.getSettings()
        assertNull("home zone should follow the device again", settings.homeZone)
        assertNull("ringtone should fall back to the system default", settings.defaultRingtoneUri)
    }

    @Test
    fun observingSettingsEmitsAgainAfterASave() = runTest {
        val repository = repository()
        assertEquals(AppSettings.DEFAULTS, repository.observeSettings().first())

        repository.save(AppSettings.DEFAULTS.copy(snoozePeriodMinutes = 15))

        assertEquals(15, repository.observeSettings().first().snoozePeriodMinutes)
    }

    @Test
    fun settingsWrittenBeforeARestartAreStillThereAfterwards() = runTest {
        val settings = AppSettings(
            homeZone = ZoneId.of("Europe/Paris"),
            snoozePeriodMinutes = 20,
            maxSnoozeCount = 1,
            alarmVolume = 0.25f,
            volumeEscalation = true,
            defaultVibrate = false,
            use24HourFormat = false,
            ringTimeoutMinutes = 15,
        )
        repository().save(settings)

        shutDown()

        assertEquals(settings, repository().getSettings())
    }

    private fun repository(): DataStoreSettingsRepository {
        val store = open ?: OpenStore(file).also { open = it }
        return DataStoreSettingsRepository(store.dataStore)
    }

    /**
     * Releases the DataStore over [file]. DataStore refuses to have two live
     * instances over one file, so the scope must be fully joined — not merely
     * cancelled — before the next instance is created.
     */
    private suspend fun shutDown() {
        open?.scope?.coroutineContext?.job?.cancelAndJoin()
        open = null
    }

    private class OpenStore(file: File) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }
}
