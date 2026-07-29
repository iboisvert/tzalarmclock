package imb.tzalarmclock.alarm.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import imb.tzalarmclock.alarm.permission.ExactAlarmPermission
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * [AlarmScheduler] backed by `AlarmManager.setAlarmClock()`.
 *
 * `setAlarmClock` rather than `setExactAndAllowWhileIdle` because it is the one
 * scheduling primitive the platform never defers for Doze or battery
 * optimisation, and because it surfaces the alarm in the status bar as an
 * upcoming alarm — both of which this app's non-functional requirements ask
 * for.
 *
 * @param clock injectable so tests can drive the plan from a fixed instant;
 *   defaults to the device's real now, carrying the device's current zone,
 *   which is what makes floating alarms follow the device.
 */
internal class AndroidAlarmScheduler(
    context: Context,
    private val alarms: AlarmRepository,
    private val registry: ArmedAlarmRegistry,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now,
) : AlarmScheduler {

    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)

    /**
     * Syncs run one at a time.
     *
     * They arrive concurrently in normal use — a firing alarm's receiver and
     * the app's own repository collector both react to the same change — and
     * each one is a read of the registry followed by a write. Overlapping them
     * would let the later write record a set of armed ids the earlier one had
     * already invalidated, stranding an alarm the OS still holds.
     */
    private val lock = Mutex()

    override suspend fun syncAll() = sync(alarms.getEnabledAlarms())

    override suspend fun sync(alarms: List<Alarm>) = lock.withLock {
        val plan = armingPlan(alarms, clock())
        val wanted = plan.mapTo(mutableSetOf()) { it.alarmId }

        // Cancel first: an id that has left the plan is an alarm that was
        // deleted or disabled, and re-arming the rest must not depend on it.
        (registry.armedIds() - wanted).forEach(::cancel)

        val armed = plan.filter(::arm).mapTo(mutableSetOf()) { it.alarmId }
        registry.replace(armed)
    }

    /** @return true if the OS accepted the alarm. */
    private fun arm(target: ArmedAlarm): Boolean {
        // Without the permission setAlarmClock throws, and there is nothing
        // useful to do about it here: the startup check surfaces it to the
        // user, so this path just declines to arm rather than crashing a
        // broadcast receiver.
        if (!ExactAlarmPermission.isGranted(context)) {
            Log.w(TAG, "Not arming alarm ${target.alarmId}: exact alarms not permitted")
            return false
        }
        val info = AlarmManager.AlarmClockInfo(target.triggerAtMillis, showIntent())
        alarmManager.setAlarmClock(info, AlarmIntents.arm(context, target.alarmId))
        Log.i(TAG, "Armed alarm ${target.alarmId} for ${target.ringsAt}")
        return true
    }

    private fun cancel(alarmId: Long) {
        AlarmIntents.existing(context, alarmId)?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.i(TAG, "Cancelled alarm $alarmId")
        }
    }

    /**
     * Where the system's "upcoming alarm" affordance sends the user.
     *
     * Resolved through the package manager rather than by naming an activity,
     * because the launcher activity lives in the app module and this one must
     * not depend on it.
     */
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
        const val TAG = "AlarmScheduler"
        const val SHOW_INTENT_REQUEST_CODE = 1
    }
}
