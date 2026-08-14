package imb.tzalarmclock.timer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.expire
import imb.tzalarmclock.timer.ringing.TimerRingingService
import imb.tzalarmclock.timer.schedule.TimerIntents

/**
 * Runs when an armed timer reaches its instant.
 *
 * Unlike `imb.tzalarmclock.alarm.receiver.AlarmReceiver`, this receiver does
 * make one repository write itself — flipping the timer to
 * [TimerState.EXPIRED] — rather than leaving every write to the ringing
 * service. A timer's dismiss action (Stage 17) resets it back to `STOPPED`,
 * symmetrical with this receiver setting `EXPIRED` on fire, so the
 * "currently ringing" state is visible in storage (and hence on the Timers
 * Summary page) for as long as the ring is actually up — not just an
 * in-memory flag the way `RingingService.isRinging` tracks alarms.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerIntents.ACTION_TIMER_FIRED) return
        val timerId = TimerIntents.timerIdOf(intent)
        if (timerId == TimerIntents.NO_TIMER_ID) return

        val app = context.applicationContext
        goAsyncWork(TAG) {
            val timers = DataProvider.timerRepository(app)
            val timer = timers.getTimer(timerId)
            if (timer == null || timer.state != TimerState.RUNNING) return@goAsyncWork
            timers.save(timer.expire())
            ContextCompat.startForegroundService(app, TimerRingingService.ringIntent(app, timerId))
        }
    }

    private companion object {
        const val TAG = "TimerReceiver"
    }
}
