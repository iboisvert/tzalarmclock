package imb.tzalarmclock.alarm

import android.content.Context
import imb.tzalarmclock.alarm.schedule.AlarmScheduler
import imb.tzalarmclock.alarm.schedule.AndroidAlarmScheduler
import imb.tzalarmclock.alarm.schedule.ArmedAlarmRegistry
import imb.tzalarmclock.data.DataProvider

/**
 * Process-wide singleton for the scheduler, mirroring `DataProvider`.
 *
 * A singleton rather than a per-call instance because [ArmedAlarmRegistry] is
 * the app's only record of what the OS currently holds, and two schedulers
 * writing it concurrently — a broadcast receiver and the app's own sync, say —
 * would let them disagree.
 */
object AlarmProvider {

    @Volatile
    private var scheduler: AlarmScheduler? = null

    fun scheduler(context: Context): AlarmScheduler {
        val app = context.applicationContext
        return scheduler ?: synchronized(this) {
            scheduler ?: AndroidAlarmScheduler(
                context = app,
                alarms = DataProvider.alarmRepository(app),
                registry = ArmedAlarmRegistry(app),
            ).also { scheduler = it }
        }
    }
}
