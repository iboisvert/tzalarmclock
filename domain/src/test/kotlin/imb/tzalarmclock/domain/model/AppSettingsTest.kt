package imb.tzalarmclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `fresh install defaults`() {
        val defaults = AppSettings.DEFAULTS

        assertNull("home zone follows the device until set", defaults.homeZone)
        assertEquals(10, defaults.snoozePeriodMinutes)
        assertEquals(3, defaults.maxSnoozeCount)
        assertNull("ringtone falls back to the system alarm sound", defaults.defaultRingtoneUri)
        assertEquals(1.0f, defaults.alarmVolume, 0.0f)
        assertFalse(defaults.volumeEscalation)
        assertTrue(defaults.defaultVibrate)
        assertTrue(defaults.use24HourFormat)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `snooze period must be positive`() {
        AppSettings(snoozePeriodMinutes = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `max snooze count cannot be negative`() {
        AppSettings(maxSnoozeCount = -1)
    }

    @Test
    fun `max snooze count of zero means dismiss only`() {
        assertEquals(0, AppSettings(maxSnoozeCount = 0).maxSnoozeCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `alarm volume above the maximum is rejected`() {
        AppSettings(alarmVolume = 1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `alarm volume below the minimum is rejected`() {
        AppSettings(alarmVolume = -0.1f)
    }
}
