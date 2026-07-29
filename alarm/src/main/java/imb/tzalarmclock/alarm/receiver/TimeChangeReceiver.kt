package imb.tzalarmclock.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import imb.tzalarmclock.alarm.AlarmProvider

/**
 * Re-evaluates every alarm when the device's idea of the time changes.
 *
 * This is the app's reason to exist. A floating alarm is defined as a
 * wall-clock time in *whatever zone the device is in*, so flying from Toronto
 * to Berlin has to move its armed instant by six hours — and nothing else
 * tells us that happened. Re-running the whole sync (rather than trying to
 * work out which alarms are affected) also picks up zone-locked alarms, whose
 * instants don't move but whose countdown displays do.
 *
 * `TIME_SET` and `DATE_CHANGED` are here for the same reason: an alarm armed
 * against a clock that has since been corrected is armed for the wrong moment.
 *
 * These are all on the platform's implicit-broadcast exemption list, so a
 * manifest registration still receives them on API 26+ without the app running.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val app = context.applicationContext
        goAsyncWork(TAG) {
            AlarmProvider.scheduler(app).syncAll()
        }
    }

    private companion object {
        const val TAG = "TimeChangeReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )
    }
}
