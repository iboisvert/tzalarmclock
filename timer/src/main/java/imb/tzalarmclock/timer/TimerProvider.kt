package imb.tzalarmclock.timer

import android.content.Context
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.timer.schedule.AndroidTimerScheduler
import imb.tzalarmclock.timer.schedule.ArmedTimerRegistry
import imb.tzalarmclock.timer.schedule.TimerScheduler

/**
 * Process-wide singleton for the timer scheduler, mirroring `AlarmProvider`.
 *
 * A singleton for the same reason as `AlarmProvider`: [ArmedTimerRegistry] is
 * the app's only record of what the OS currently holds for timers, and two
 * schedulers writing it concurrently would let them disagree.
 */
object TimerProvider {

    @Volatile
    private var scheduler: TimerScheduler? = null

    fun scheduler(context: Context): TimerScheduler {
        val app = context.applicationContext
        return scheduler ?: synchronized(this) {
            scheduler ?: AndroidTimerScheduler(
                context = app,
                timers = DataProvider.timerRepository(app),
                registry = ArmedTimerRegistry(app),
            ).also { scheduler = it }
        }
    }
}
