package imb.tzalarmclock.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.repository.TimerRepository
import imb.tzalarmclock.domain.schedule.pause
import imb.tzalarmclock.domain.schedule.reset
import imb.tzalarmclock.domain.schedule.resume
import imb.tzalarmclock.domain.schedule.start
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Timers Summary screen with a live, Flow-driven view of the
 * timer list.
 *
 * Mirrors `imb.tzalarmclock.ui.summary.SummaryViewModel`'s combine-with-a-
 * ticker shape, but on a faster (~1s, not 30s) tick: seconds-level precision
 * matters for a live countdown in a way it doesn't for an alarm's fuzzy one.
 *
 * State-changing calls write straight to [TimerRepository] and return —
 * `TzAlarmClockApplication`'s repository collector is what re-syncs
 * `imb.tzalarmclock.timer.schedule.TimerScheduler` afterward, the same
 * division of responsibility `SummaryViewModel.setEnabled` relies on for
 * alarms.
 */
class TimersViewModel(application: Application) : AndroidViewModel(application) {

    private val timerRepository: TimerRepository = DataProvider.timerRepository(application)

    val uiState: StateFlow<TimersUiState> = combine(
        timerRepository.observeTimers(),
        tick(),
    ) { timers, _ -> buildTimersUiState(timers, Instant.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimersUiState())

    fun startTimer(id: Long) = act(id) { it.start(Instant.now()) }
    fun pauseTimer(id: Long) = act(id) { it.pause(Instant.now()) }
    fun resumeTimer(id: Long) = act(id) { it.resume(Instant.now()) }
    fun resetTimer(id: Long) = act(id) { it.reset() }

    fun deleteTimer(id: Long) {
        viewModelScope.launch { timerRepository.delete(id) }
    }

    /** Creates a new [imb.tzalarmclock.domain.model.TimerState.STOPPED] timer at [duration]. */
    fun addTimer(duration: Duration) {
        viewModelScope.launch {
            timerRepository.save(Timer(configuredDuration = duration, createdAt = Instant.now()))
        }
    }

    private fun act(id: Long, transform: (Timer) -> Timer) {
        viewModelScope.launch {
            val timer = timerRepository.getTimer(id) ?: return@launch
            timerRepository.save(transform(timer))
        }
    }

    private fun tick(periodMillis: Long = TICK_PERIOD_MILLIS): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TICK_PERIOD_MILLIS = 1_000L
    }
}
