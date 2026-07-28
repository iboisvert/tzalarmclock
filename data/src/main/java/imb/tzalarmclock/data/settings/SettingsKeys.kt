package imb.tzalarmclock.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore keys for the eight settings in the spec.
 *
 * A key that is absent means "never set", and the corresponding
 * `AppSettings` default applies. The two nullable settings — home zone and
 * default ringtone — therefore store absence rather than a sentinel value.
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

    /** File name of the settings DataStore, without the `.preferences_pb` suffix. */
    const val DATA_STORE_NAME = "settings"
}
