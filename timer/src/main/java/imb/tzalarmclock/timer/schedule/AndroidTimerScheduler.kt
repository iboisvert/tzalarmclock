package imb.tzalarmclock.timer.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.repository.TimerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [TimerScheduler] backed by `AlarmManager.setAlarmClock()`.
 *
 * Same primitive and rationale as
 * `imb.tzalarmclock.alarm.schedule.AndroidAlarmScheduler`: it is the one
 * scheduling call the platform never defers for Doze or battery
 * optimisation.
 */
internal class AndroidTimerScheduler(
    context: Context,
    private val timers: TimerRepository,
    private val registry: ArmedTimerRegistry,
) : TimerScheduler {

    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)

    /** Same reasoning as `AndroidAlarmScheduler.lock`: syncs must not overlap. */
    private val lock = Mutex()

    override suspend fun syncAll() = sync(timers.getRunningTimers())

    override suspend fun sync(timers: List<Timer>) = lock.withLock {
        val plan = timerArmingPlan(timers)
        val wanted = plan.mapTo(mutableSetOf()) { it.timerId }

        // Cancel first: an id that has left the plan is a timer that was
        // deleted, paused, reset, or has already fired, and re-arming the
        // rest must not depend on it.
        (registry.armedIds() - wanted).forEach(::cancel)

        val armed = plan.filter(::arm).mapTo(mutableSetOf()) { it.timerId }
        registry.replace(armed)
    }

    /** @return true if the OS accepted the timer. */
    private fun arm(target: ArmedTimer): Boolean {
        if (!ExactAlarmPermission.isGranted(context)) {
            Log.w(TAG, "Not arming timer ${target.timerId}: exact alarms not permitted")
            return false
        }
        val info = AlarmManager.AlarmClockInfo(target.triggerAtMillis, showIntent())
        alarmManager.setAlarmClock(info, TimerIntents.arm(context, target.timerId))
        Log.i(TAG, "Armed timer ${target.timerId} for ${target.endInstant}")
        return true
    }

    private fun cancel(timerId: Long) {
        TimerIntents.existing(context, timerId)?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.i(TAG, "Cancelled timer $timerId")
        }
    }

    /** Where the system's "upcoming alarm" affordance sends the user. */
    private fun showIntent(): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            SHOW_INTENT_REQUEST_CODE,
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "TimerScheduler"
        const val SHOW_INTENT_REQUEST_CODE = 2
    }
}
