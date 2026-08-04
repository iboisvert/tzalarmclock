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
 * Edits are a draft held only in [uiState] — nothing touches [alarmRepository]
 * until [add] (new alarm) or [saveEdit] (existing alarm) is called. This is a
 * deliberate deviation from the spec's "back button always saves" (see the
 * development plan's Stage 11 / assumption #22): the screen now shows an
 * explicit Add/Cancel or Cancel/Delete button row, with Android back remapped
 * to behave like Cancel, and an exit-confirmation dialog whenever the form is
 * [isDirty].
 *
 * [add], [saveEdit] and [delete] are `suspend` rather than fire-and-forget:
 * the screen calls them from a composition-scoped coroutine (not
 * [viewModelScope]) and waits for them to finish before navigating away,
 * since this ViewModel is torn down as soon as its destination leaves the
 * back stack.
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

    /** The alarm as loaded (or, for a new one, its seeded default) — [isDirty]'s comparison point. */
    private var originalAlarm: Alarm? = null

    /** Loads the alarm to edit, or seeds a brand-new one when [alarmId] is `null`. Idempotent. */
    fun load(alarmId: Long?) {
        if (hasStartedLoad) return
        hasStartedLoad = true
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val alarm = alarmId?.let { alarmRepository.getAlarm(it) } ?: Alarm(time = LocalTime.now())
            originalAlarm = alarm
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

    /** True once the current draft differs from [originalAlarm] — the exit-dialog trigger. */
    fun isDirty(): Boolean {
        val state = _uiState.value
        return !state.isLoading && state.toAlarm() != originalAlarm
    }

    /** Persists a brand-new alarm. No-ops if [load] never finished or this isn't a new alarm. */
    suspend fun add() {
        val state = _uiState.value
        if (state.isLoading || !state.isNew) return
        alarmRepository.save(state.toAlarm())
    }

    /** Persists an edit to an existing alarm. No-ops for a new alarm or before [load] finishes. */
    suspend fun saveEdit() {
        val state = _uiState.value
        if (state.isLoading || state.isNew) return
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
