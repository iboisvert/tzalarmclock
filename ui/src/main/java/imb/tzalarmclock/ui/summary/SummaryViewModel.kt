package imb.tzalarmclock.ui.summary

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.repository.AlarmRepository
import imb.tzalarmclock.domain.repository.SettingsRepository
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Summary screen with a live, Flow-driven view of the alarm list.
 *
 * Alarm and settings changes update [uiState] as soon as they're written.
 * [tick] also re-emits on a timer so the group membership and countdown stay
 * correct as time passes even when nothing in the data changed — the spec's
 * "live-updating" requirement. The same tick is also what picks up a snooze:
 * `SnoozeRegistry` isn't `Flow`-observable, so [RingingService.allSnoozedUntilMillis]
 * is re-read fresh on every tick/alarm/settings emission rather than cached.
 */
class SummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application
    private val alarmRepository: AlarmRepository = DataProvider.alarmRepository(context)
    private val settingsRepository: SettingsRepository = DataProvider.settingsRepository(context)

    val uiState: StateFlow<SummaryUiState> = combine(
        alarmRepository.observeAlarms(),
        settingsRepository.observeSettings(),
        tick(),
    ) { alarms, settings, _ ->
        val snoozedUntil = RingingService.allSnoozedUntilMillis(context).mapValues { Instant.ofEpochMilli(it.value) }
        buildSummaryUiState(alarms, settings, ZonedDateTime.now(), snoozedUntil)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SummaryUiState())

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { alarmRepository.setEnabled(id, enabled) }
    }

    private fun tick(periodMillis: Long = TICK_PERIOD_MILLIS): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_PERIOD_MILLIS = 30_000L
    }
}
