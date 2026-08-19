package imb.tzalarmclock.ui.ringing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.repository.AlarmRepository
import imb.tzalarmclock.domain.repository.SettingsRepository
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Ringing screen: polls [RingingService.currentlyRingingAlarmIds]
 * on a 1-second tick so the displayed clock stays live and so a *second*
 * alarm joining an already-showing ring cycle updates this screen in place
 * — unlike a plain one-alarm load, since more than one alarm can be ringing
 * at once (see [RingingService]'s class doc).
 *
 * [onSnooze] and [onDismiss] only fire an intent at [RingingService] — the
 * repository/scheduler writes happen there, not here, so they aren't lost
 * when the ringing activity finishes and this ViewModel is cleared right
 * after the tap. Both now act on every ringing alarm together, not just the
 * one whose fire launched this screen.
 */
class RingingViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application
    private val alarmRepository: AlarmRepository = DataProvider.alarmRepository(context)
    private val settingsRepository: SettingsRepository = DataProvider.settingsRepository(context)

    private val _uiState = MutableStateFlow(RingingUiState())
    val uiState: StateFlow<RingingUiState> = _uiState.asStateFlow()

    private var hasStartedLoad = false

    /**
     * Starts polling every currently-ringing alarm — not just [alarmId], the
     * one whose fire launched this screen. [alarmId] only gates idempotency;
     * it plays no part in what's displayed. Idempotent.
     */
    fun load(alarmId: Long) {
        if (hasStartedLoad) return
        hasStartedLoad = true
        viewModelScope.launch {
            while (true) {
                val ringingIds = RingingService.currentlyRingingAlarmIds()
                if (ringingIds.isEmpty()) {
                    _uiState.value = _uiState.value.copy(stillRinging = false)
                    break
                }
                val alarms = ringingIds.mapNotNull { alarmRepository.getAlarm(it) }
                val settings = settingsRepository.getSettings()
                val snoozeCounts = ringingIds.associateWith { RingingService.snoozeCount(context, it) }
                _uiState.value = buildRingingUiState(alarms, settings, ZonedDateTime.now(), snoozeCounts)
                    .copy(stillRinging = true)
                delay(TICK_PERIOD_MILLIS)
            }
        }
    }

    fun onSnooze() {
        context.startService(RingingService.snoozeAllIntent(context))
    }

    fun onDismiss() {
        context.startService(RingingService.dismissAllIntent(context))
    }

    private companion object {
        const val TICK_PERIOD_MILLIS = 1_000L
    }
}
