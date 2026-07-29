package imb.tzalarmclock.alarm.permission

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Whether the OS will let us schedule exact alarms at all.
 *
 * The app declares `USE_EXACT_ALARM` on API 33+, which the platform grants at
 * install time to apps whose core function is an alarm clock, and falls back to
 * `SCHEDULE_EXACT_ALARM` on API 31–32 where `USE_EXACT_ALARM` doesn't exist.
 * On those two versions the user can revoke it from system settings, at which
 * point every alarm silently stops firing — hence the startup check.
 *
 * [AlarmManager.canScheduleExactAlarms] is the authority either way, so this
 * stays correct without version branching whichever permission is in effect.
 */
object ExactAlarmPermission {

    fun isGranted(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Sends the user to the system screen that grants it. There is no runtime
     * permission dialog for this one — a settings trip is the only route.
     */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:${context.packageName}".toUri(),
        )
}
