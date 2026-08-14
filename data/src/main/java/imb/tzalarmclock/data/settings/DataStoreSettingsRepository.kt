package imb.tzalarmclock.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.ZoneId

/** [SettingsRepository] backed by a Preferences DataStore. */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> = dataStore.data
        // A corrupt or unreadable settings file must not stop the app from
        // starting; the user sees defaults and can set them again.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { it.toAppSettings() }

    override suspend fun getSettings(): AppSettings = observeSettings().first()

    override suspend fun save(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs.setOrRemove(SettingsKeys.HOME_ZONE_ID, settings.homeZone?.id)
            prefs[SettingsKeys.SNOOZE_PERIOD_MINUTES] = settings.snoozePeriodMinutes
            prefs[SettingsKeys.MAX_SNOOZE_COUNT] = settings.maxSnoozeCount
            prefs.setOrRemove(SettingsKeys.DEFAULT_RINGTONE_URI, settings.defaultRingtoneUri)
            prefs[SettingsKeys.ALARM_VOLUME] = settings.alarmVolume
            prefs[SettingsKeys.VOLUME_ESCALATION] = settings.volumeEscalation
            prefs[SettingsKeys.DEFAULT_VIBRATE] = settings.defaultVibrate
            prefs[SettingsKeys.USE_24_HOUR_FORMAT] = settings.use24HourFormat
            prefs[SettingsKeys.RING_TIMEOUT_MINUTES] = settings.ringTimeoutMinutes
            prefs.setOrRemove(SettingsKeys.DEFAULT_TIMER_RINGTONE_URI, settings.defaultTimerRingtoneUri)
        }
    }
}

private fun <T : Any> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
    if (value == null) remove(key) else set(key, value)
}

internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings.DEFAULTS
    return AppSettings(
        // An unrecognised zone id (dropped from the platform tzdb) falls back to
        // following the device zone rather than failing every read.
        homeZone = this[SettingsKeys.HOME_ZONE_ID]
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() },
        snoozePeriodMinutes = this[SettingsKeys.SNOOZE_PERIOD_MINUTES]
            ?: defaults.snoozePeriodMinutes,
        maxSnoozeCount = this[SettingsKeys.MAX_SNOOZE_COUNT] ?: defaults.maxSnoozeCount,
        defaultRingtoneUri = this[SettingsKeys.DEFAULT_RINGTONE_URI],
        alarmVolume = this[SettingsKeys.ALARM_VOLUME] ?: defaults.alarmVolume,
        volumeEscalation = this[SettingsKeys.VOLUME_ESCALATION] ?: defaults.volumeEscalation,
        defaultVibrate = this[SettingsKeys.DEFAULT_VIBRATE] ?: defaults.defaultVibrate,
        use24HourFormat = this[SettingsKeys.USE_24_HOUR_FORMAT] ?: defaults.use24HourFormat,
        ringTimeoutMinutes = this[SettingsKeys.RING_TIMEOUT_MINUTES] ?: defaults.ringTimeoutMinutes,
        defaultTimerRingtoneUri = this[SettingsKeys.DEFAULT_TIMER_RINGTONE_URI],
    )
}
