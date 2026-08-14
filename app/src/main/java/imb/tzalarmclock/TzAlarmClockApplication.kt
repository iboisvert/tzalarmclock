package imb.tzalarmclock

import android.app.Application
import imb.tzalarmclock.alarm.AlarmProvider
import imb.tzalarmclock.alarm.ringing.RingingNotifications
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.timer.TimerProvider
import imb.tzalarmclock.timer.ringing.TimerRingingNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the OS's armed alarms and timers in step with the database for as
 * long as the process lives.
 *
 * Collecting each repository's `Flow` rather than re-arming from each call
 * site means every path that can change an alarm or timer — the Details
 * page, a toggle on the Summary page, a timer's own Start/Pause/Resume/Reset
 * controls, either one retiring itself after it fires — re-arms for free,
 * and there is no way to add a new one that forgets to.
 *
 * This is not a background service and does no polling: Room emits only when a
 * row actually changes. While the app isn't running the broadcast receivers in
 * the `alarm`/`timer` modules carry the same responsibility, and the first
 * emission here is what re-arms everything after a force-stop, which clears
 * the OS's alarms and stops broadcasts reaching us until the user launches
 * the app again.
 */
class TzAlarmClockApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        RingingNotifications.ensureChannel(this)
        TimerRingingNotifications.ensureChannel(this)

        val alarmScheduler = AlarmProvider.scheduler(this)
        val alarms = DataProvider.alarmRepository(this)
        scope.launch {
            alarms.observeAlarms().collect { alarmScheduler.sync(it) }
        }

        val timerScheduler = TimerProvider.scheduler(this)
        val timers = DataProvider.timerRepository(this)
        scope.launch {
            timers.observeTimers().collect { timerScheduler.sync(it) }
        }
    }
}
