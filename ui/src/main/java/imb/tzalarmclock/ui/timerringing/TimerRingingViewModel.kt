package imb.tzalarmclock.ui.timerringing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.repository.TimerRepository
import imb.tzalarmclock.timer.ringing.TimerRingingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Timer Ringing screen. Mirrors
 * [imb.tzalarmclock.ui.ringing.RingingViewModel]: polls
 * [TimerRingingService.currentlyRingingTimerIds] on a tick so the screen can
 * finish itself if the service ends the ring cycle on its own, and — unlike
 * the alarm equivalent, which only ever tracks one alarm — so a *second*
 * timer joining an already-showing ring cycle updates this screen in place
 * rather than requiring a fresh launch.
 *
 * [onDismiss] only fires an intent at [TimerRingingService] — the
 * repository/scheduler writes happen there, not here, same reasoning as the
 * alarm equivalent.
 */
class TimerRingingViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application
    private val timerRepository: TimerRepository = DataProvider.timerRepository(context)

    private val _uiState = MutableStateFlow(TimerRingingUiState())
    val uiState: StateFlow<TimerRingingUiState> = _uiState.asStateFlow()

    private var hasStartedLoad = false

    /**
     * Starts polling every currently-ringing timer — not just [timerId], the
     * one whose fire launched this screen. [timerId] only gates
     * idempotency; it plays no part in what's displayed. Idempotent.
     */
    fun load(timerId: Long) {
        if (hasStartedLoad) return
        hasStartedLoad = true
        viewModelScope.launch {
            while (true) {
                val ringingIds = TimerRingingService.currentlyRingingTimerIds()
                if (ringingIds.isEmpty()) {
                    _uiState.value = _uiState.value.copy(stillRinging = false)
                    break
                }
                val timers = ringingIds.mapNotNull { timerRepository.getTimer(it) }
                _uiState.value = buildTimerRingingUiState(timers).copy(stillRinging = true)
                delay(TICK_PERIOD_MILLIS)
            }
        }
    }

    fun onDismiss() {
        context.startService(TimerRingingService.dismissAllIntent(context))
    }

    private companion object {
        const val TICK_PERIOD_MILLIS = 1_000L
    }
}
