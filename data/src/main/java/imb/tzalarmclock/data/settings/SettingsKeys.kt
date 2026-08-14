package imb.tzalarmclock.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore keys backing [imb.tzalarmclock.domain.model.AppSettings] — the
 * spec's original eight settings, plus [RING_TIMEOUT_MINUTES] and
 * [DEFAULT_TIMER_RINGTONE_URI].
 *
 * A key that is absent means "never set", and the corresponding
 * `AppSettings` default applies. The nullable settings — home zone and the
 * two ringtones — therefore store absence rather than a sentinel value.
 */
internal object SettingsKeys {
    val HOME_ZONE_ID = stringPreferencesKey("home_zone_id")
    val SNOOZE_PERIOD_MINUTES = intPreferencesKey("snooze_period_minutes")
    val MAX_SNOOZE_COUNT = intPreferencesKey("max_snooze_count")
    val DEFAULT_RINGTONE_URI = stringPreferencesKey("default_ringtone_uri")
    val ALARM_VOLUME = floatPreferencesKey("alarm_volume")
    val VOLUME_ESCALATION = booleanPreferencesKey("volume_escalation")
    val DEFAULT_VIBRATE = booleanPreferencesKey("default_vibrate")
    val USE_24_HOUR_FORMAT = booleanPreferencesKey("use_24_hour_format")
    val RING_TIMEOUT_MINUTES = intPreferencesKey("ring_timeout_minutes")
    val DEFAULT_TIMER_RINGTONE_URI = stringPreferencesKey("default_timer_ringtone_uri")

    /** File name of the settings DataStore, without the `.preferences_pb` suffix. */
    const val DATA_STORE_NAME = "settings"
}
