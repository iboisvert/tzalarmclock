package imb.tzalarmclock.ui.timerringing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.repository.TimerRepository
import imb.tzalarmclock.timer.ringing.TimerRingingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Timer Ringing screen. Mirrors
 * [imb.tzalarmclock.ui.ringing.RingingViewModel]: loads the fired timer
 * once, then polls [TimerRingingService.isRinging] on a tick so the screen
 * can finish itself if the service ends the ring cycle on its own.
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

    private var timerId: Long = Timer.NO_ID
    private var hasStartedLoad = false

    /** Loads the timer and starts polling for [TimerRingingUiState.stillRinging]. Idempotent. */
    fun load(timerId: Long) {
        if (hasStartedLoad) return
        hasStartedLoad = true
        this.timerId = timerId
        viewModelScope.launch {
            val timer = timerRepository.getTimer(timerId) ?: return@launch
            val base = buildTimerRingingUiState(timer)
            while (true) {
                val stillRinging = TimerRingingService.isRinging(timerId)
                _uiState.value = base.copy(stillRinging = stillRinging)
                if (!stillRinging) break
                delay(TICK_PERIOD_MILLIS)
            }
        }
    }

    fun onDismiss() {
        context.startService(TimerRingingService.dismissIntent(context, timerId))
    }

    private companion object {
        const val TICK_PERIOD_MILLIS = 1_000L
    }
}
