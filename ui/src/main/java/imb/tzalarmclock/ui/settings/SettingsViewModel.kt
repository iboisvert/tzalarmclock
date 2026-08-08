package imb.tzalarmclock.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.repository.SettingsRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen with an editable [SettingsUiState].
 *
 * [save] is `suspend` rather than fire-and-forget for the same reason as
 * `DetailsViewModel`: the screen calls it from a composition-scoped
 * coroutine (not [androidx.lifecycle.viewModelScope]) and waits for it to
 * finish before navigating away, so the spec's "saved whenever the settings
 * page is closed" holds even though this ViewModel is torn down as soon as
 * the screen leaves the back stack.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository = DataProvider.settingsRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var hasStartedLoad = false

    /** Loads the current settings. Idempotent. */
    fun load() {
        if (hasStartedLoad) return
        hasStartedLoad = true
        viewModelScope.launch {
            _uiState.value = buildSettingsUiState(settingsRepository.getSettings())
        }
    }

    fun onHomeZoneChanged(zone: ZoneId?) = update { it.copy(homeZone = zone) }
    fun onSnoozePeriodChanged(minutes: Int) =
        update { it.copy(snoozePeriodMinutes = minutes.coerceIn(MIN_SNOOZE_PERIOD_MINUTES, MAX_SNOOZE_PERIOD_MINUTES)) }
    fun onMaxSnoozeCountChanged(count: Int) =
        update { it.copy(maxSnoozeCount = count.coerceIn(MIN_SNOOZE_COUNT, MAX_SNOOZE_COUNT)) }
    fun onRingtoneChanged(uri: String?) = update { it.copy(defaultRingtoneUri = uri) }
    fun onAlarmVolumeChanged(volume: Float) = update { it.copy(alarmVolume = volume.coerceIn(0f, 1f)) }
    fun onVolumeEscalationChanged(enabled: Boolean) = update { it.copy(volumeEscalation = enabled) }
    fun onDefaultVibrateChanged(enabled: Boolean) = update { it.copy(defaultVibrate = enabled) }
    fun onUse24HourFormatChanged(enabled: Boolean) = update { it.copy(use24HourFormat = enabled) }
    fun onRingTimeoutChanged(minutes: Int) =
        update { it.copy(ringTimeoutMinutes = minutes.coerceIn(MIN_RING_TIMEOUT_MINUTES, MAX_RING_TIMEOUT_MINUTES)) }

    /** No-ops if [load] never finished, so an early back-press can't persist blank settings. */
    suspend fun save() {
        val state = _uiState.value
        if (state.isLoading) return
        settingsRepository.save(state.toAppSettings())
    }

    private fun update(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private companion object {
        const val MIN_SNOOZE_PERIOD_MINUTES = 1
        const val MAX_SNOOZE_PERIOD_MINUTES = 60
        const val MIN_SNOOZE_COUNT = 0
        const val MAX_SNOOZE_COUNT = 10
        const val MIN_RING_TIMEOUT_MINUTES = 1
        const val MAX_RING_TIMEOUT_MINUTES = 60
    }
}
