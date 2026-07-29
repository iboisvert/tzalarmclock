package imb.tzalarmclock.alarm.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Everything the OS could be doing to stop an alarm from reaching the user.
 *
 * Gathered into one value because the app needs to answer a single question on
 * launch — "can I be trusted right now?" — and because two of the three are
 * silent failures: a revoked exact-alarm permission or a denied notification
 * permission produces no error, just an alarm that never goes off.
 *
 * @param exactAlarmsAllowed alarms cannot be scheduled at all without this.
 * @param notificationsAllowed the alarm still fires, but nothing is shown.
 * @param batteryOptimized advisory: `setAlarmClock` fires through Doze anyway,
 *   but OEM power managers are a known source of missed alarms.
 */
data class SchedulingHealth(
    val exactAlarmsAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val batteryOptimized: Boolean,
) {
    /** True when nothing needs saying to the user. */
    val allClear: Boolean
        get() = exactAlarmsAllowed && notificationsAllowed && !batteryOptimized

    companion object {

        fun check(context: Context): SchedulingHealth = SchedulingHealth(
            exactAlarmsAllowed = ExactAlarmPermission.isGranted(context),
            notificationsAllowed = areNotificationsAllowed(context),
            batteryOptimized = !BatteryOptimization.isExempt(context),
        )

        /**
         * Below API 33 `POST_NOTIFICATIONS` doesn't exist and notifications are
         * granted at install, so there is nothing to check or ask for.
         */
        fun areNotificationsAllowed(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
    }
}
