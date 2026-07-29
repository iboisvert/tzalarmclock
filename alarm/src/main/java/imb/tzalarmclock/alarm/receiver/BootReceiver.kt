package imb.tzalarmclock.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import imb.tzalarmclock.alarm.AlarmProvider
import imb.tzalarmclock.alarm.notify.AlarmNotifications

/**
 * Re-arms everything after the OS has thrown our pending alarms away.
 *
 * A reboot clears every registered alarm, and so does the app being replaced by
 * an update — both are silent, so without this an alarm set before a restart
 * simply never rings. `ACTION_MY_PACKAGE_REPLACED` also covers the "survives OS
 * updates" requirement in the practical case, since a platform update
 * necessarily reboots.
 *
 * Not `ACTION_LOCKED_BOOT_COMPLETED`: the alarm database lives in
 * credential-protected storage and isn't readable until the user has unlocked
 * the device once after boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val app = context.applicationContext
        goAsyncWork(TAG) {
            AlarmNotifications.ensureChannel(app)
            AlarmProvider.scheduler(app).syncAll()
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
