package imb.tzalarmclock.ui.ringing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.repository.AlarmRepository
import imb.tzalarmclock.domain.repository.SettingsRepository
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Ringing screen: loads the firing alarm once, then re-renders
 * [uiState] on a 1-second tick so the displayed clock stays live.
 *
 * [onSnooze] and [onDismiss] only fire an intent at [RingingService] — the
 * repository/scheduler writes happen there, not here, so they aren't lost
 * when the ringing activity finishes and this ViewModel is cleared right
 * after the tap.
 */
class RingingViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application
    private val alarmRepository: AlarmRepository = DataProvider.alarmRepository(context)
    private val settingsRepository: SettingsRepository = DataProvider.settingsRepository(context)

    private val _uiState = MutableStateFlow(RingingUiState())
    val uiState: StateFlow<RingingUiState> = _uiState.asStateFlow()

    private var alarmId: Long = Alarm.NO_ID
    private var hasStartedLoad = false

    /** Loads the alarm and starts the display tick. Idempotent. */
    fun load(alarmId: Long) {
        if (hasStartedLoad) return
        hasStartedLoad = true
        this.alarmId = alarmId
        viewModelScope.launch {
            val alarm = alarmRepository.getAlarm(alarmId) ?: return@launch
            val settings = settingsRepository.getSettings()
            while (true) {
                val snoozeCount = RingingService.snoozeCount(context, alarmId)
                _uiState.value = buildRingingUiState(alarm, settings, ZonedDateTime.now(), snoozeCount)
                delay(TICK_PERIOD_MILLIS)
            }
        }
    }

    fun onSnooze() {
        context.startService(RingingService.snoozeIntent(context, alarmId))
    }

    fun onDismiss() {
        context.startService(RingingService.dismissIntent(context, alarmId))
    }

    private companion object {
        const val TICK_PERIOD_MILLIS = 1_000L
    }
}
