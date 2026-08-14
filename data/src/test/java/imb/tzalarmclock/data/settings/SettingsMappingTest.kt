package imb.tzalarmclock.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import imb.tzalarmclock.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * Reading side of the settings store, exercised without a device since
 * `Preferences` itself is plain Kotlin.
 */
class SettingsMappingTest {

    @Test
    fun `an empty store reads as the fresh-install defaults`() {
        assertEquals(AppSettings.DEFAULTS, emptyPreferences().toAppSettings())
    }

    @Test
    fun `a partially populated store keeps defaults for the untouched settings`() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.SNOOZE_PERIOD_MINUTES to 5,
        )

        val settings = prefs.toAppSettings()

        assertEquals(5, settings.snoozePeriodMinutes)
        assertEquals(AppSettings.DEFAULTS.maxSnoozeCount, settings.maxSnoozeCount)
        assertEquals(AppSettings.DEFAULTS.use24HourFormat, settings.use24HourFormat)
        assertEquals(AppSettings.DEFAULTS.ringTimeoutMinutes, settings.ringTimeoutMinutes)
    }

    @Test
    fun `every setting reads back from the store`() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.HOME_ZONE_ID to "America/Toronto",
            SettingsKeys.SNOOZE_PERIOD_MINUTES to 7,
            SettingsKeys.MAX_SNOOZE_COUNT to 2,
            SettingsKeys.DEFAULT_RINGTONE_URI to "content://ringtone/1",
            SettingsKeys.ALARM_VOLUME to 0.4f,
            SettingsKeys.VOLUME_ESCALATION to true,
            SettingsKeys.DEFAULT_VIBRATE to false,
            SettingsKeys.USE_24_HOUR_FORMAT to false,
            SettingsKeys.RING_TIMEOUT_MINUTES to 8,
            SettingsKeys.DEFAULT_TIMER_RINGTONE_URI to "content://ringtone/2",
        )

        assertEquals(
            AppSettings(
                homeZone = ZoneId.of("America/Toronto"),
                snoozePeriodMinutes = 7,
                maxSnoozeCount = 2,
                defaultRingtoneUri = "content://ringtone/1",
                alarmVolume = 0.4f,
                volumeEscalation = true,
                defaultVibrate = false,
                use24HourFormat = false,
                ringTimeoutMinutes = 8,
                defaultTimerRingtoneUri = "content://ringtone/2",
            ),
            prefs.toAppSettings(),
        )
    }

    @Test
    fun `a home zone the platform no longer knows falls back to the device zone`() {
        val prefs = mutablePreferencesOf(SettingsKeys.HOME_ZONE_ID to "Mars/Olympus_Mons")

        assertNull(prefs.toAppSettings().homeZone)
    }
}
