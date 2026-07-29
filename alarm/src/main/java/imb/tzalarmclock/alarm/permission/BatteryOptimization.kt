package imb.tzalarmclock.alarm.permission

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Whether the app is exempt from Doze-style battery optimisation.
 *
 * `setAlarmClock` alarms fire during Doze regardless, so this is belt and
 * braces rather than load-bearing — but OEM power managers (the target device
 * is a Motorola) layer their own restrictions on top of stock Doze and are a
 * known cause of missed alarms, so the exemption is worth offering.
 *
 * Offered, not forced: the request is surfaced as something the user can choose
 * from the startup warning, since an unprompted system dialog on first launch
 * is hostile and the app still works without it.
 */
object BatteryOptimization {

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )
}
