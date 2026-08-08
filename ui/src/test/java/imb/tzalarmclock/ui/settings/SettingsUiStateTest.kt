package imb.tzalarmclock.ui.settings

import imb.tzalarmclock.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneId

class SettingsUiStateTest {

    @Test
    fun `a fresh install's defaults build with isLoading false`() {
        val state = buildSettingsUiState(AppSettings.DEFAULTS)

        assertFalse(state.isLoading)
        assertEquals(AppSettings.DEFAULTS, state.toAppSettings())
    }

    @Test
    fun `every field round-trips through toAppSettings`() {
        val settings = AppSettings(
            homeZone = ZoneId.of("America/Toronto"),
            snoozePeriodMinutes = 15,
            maxSnoozeCount = 5,
            defaultRingtoneUri = "content://some/ringtone",
            alarmVolume = 0.6f,
            volumeEscalation = true,
            defaultVibrate = false,
            use24HourFormat = false,
            ringTimeoutMinutes = 20,
        )

        val state = buildSettingsUiState(settings)

        assertEquals(settings, state.toAppSettings())
    }

    @Test
    fun `a null home zone means the app follows the device`() {
        val state = buildSettingsUiState(AppSettings.DEFAULTS.copy(homeZone = null))

        assertEquals(null, state.homeZone)
        assertEquals(null, state.toAppSettings().homeZone)
    }
}
