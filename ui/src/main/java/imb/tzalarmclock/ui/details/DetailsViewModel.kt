package imb.tzalarmclock.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.ScheduleType
import imb.tzalarmclock.domain.repository.AlarmRepository
import imb.tzalarmclock.domain.repository.SettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Details screen with an editable [DetailsUiState].
 *
 * [save] and [delete] are `suspend` rather than fire-and-forget: the screen
 * calls them from a composition-scoped coroutine (not [viewModelScope]) and
 * waits for them to finish before navigating away, so the spec's "back button
 * saves changes" holds even though this ViewModel is torn down as soon as its
 * destination leaves the back stack.
 *
 * No explicit call into [imb.tzalarmclock.alarm.AlarmProvider]'s scheduler is
 * needed here, for the same reason [imb.tzalarmclock.ui.summary.SummaryViewModel]
 * doesn't make one: `TzAlarmClockApplication` collects
 * `AlarmRepository.observeAlarms()` for the whole process and re-syncs the
 * scheduler on every change, so writing through [alarmRepository] is enough.
 */
class DetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmRepository: AlarmRepository = DataProvider.alarmRepository(application)
    private val settingsRepository: SettingsRepository = DataProvider.settingsRepository(application)

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var hasStartedLoad = false

    /** Loads the alarm to edit, or seeds a brand-new one when [alarmId] is `null`. Idempotent. */
    fun load(alarmId: Long?) {
        if (hasStartedLoad) return
        hasStartedLoad = true
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val alarm = alarmId?.let { alarmRepository.getAlarm(it) } ?: Alarm(time = LocalTime.now())
            _uiState.value = buildDetailsUiState(alarm, settings, LocalDate.now())
        }
    }

    fun onNameChanged(name: String) = update { it.copy(name = name) }
    fun onTimeChanged(time: LocalTime) = update { it.copy(time = time) }
    fun onZoneChanged(zone: ZoneId?) = update { it.copy(zone = zone) }
    fun onScheduleTypeChanged(type: ScheduleType) = update { it.copy(scheduleType = type) }
    fun onDateChanged(date: LocalDate) = update { it.copy(date = date) }
    fun onWeekdayToggled(day: DayOfWeek) = update { it.withWeekdayToggled(day) }
    fun onMonthDayToggled(day: Int) = update { it.withMonthDayToggled(day) }
    fun onRingtoneChanged(uri: String?) = update { it.copy(ringtoneUri = uri) }
    fun onVibrateChanged(vibrate: Boolean?) = update { it.copy(vibrate = vibrate) }

    /** No-ops if [load] never finished, so an early back-press can't persist a blank alarm. */
    suspend fun save() {
        val state = _uiState.value
        if (state.isLoading) return
        alarmRepository.save(state.toAlarm())
    }

    /** No-ops for a not-yet-saved alarm or before [load] finishes. */
    suspend fun delete() {
        val state = _uiState.value
        if (state.isLoading || state.isNew) return
        alarmRepository.delete(state.alarmId)
    }

    private fun update(transform: (DetailsUiState) -> DetailsUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
