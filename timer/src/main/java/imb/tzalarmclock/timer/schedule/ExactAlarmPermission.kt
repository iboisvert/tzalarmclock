package imb.tzalarmclock.timer.schedule

import android.app.AlarmManager
import android.content.Context

/**
 * Whether the OS will let us schedule exact alarms at all.
 *
 * A small duplicate of `imb.tzalarmclock.alarm.permission.ExactAlarmPermission`'s
 * `isGranted` check, not a dependency on it: the `timer` module mirrors
 * `alarm`'s internal shape rather than depending on it (see the dev plan's
 * timer-module guiding decision), and this one check is the only piece
 * [AndroidTimerScheduler] actually needs — the settings-intent half of the
 * startup-warning UI stays a one-time, alarm-scoped concern in the `app`
 * module.
 */
internal object ExactAlarmPermission {

    fun isGranted(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
