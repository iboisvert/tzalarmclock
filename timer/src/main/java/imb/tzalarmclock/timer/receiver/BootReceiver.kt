package imb.tzalarmclock.timer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import imb.tzalarmclock.timer.TimerProvider
import imb.tzalarmclock.timer.ringing.TimerRingingNotifications

/**
 * Re-arms every running timer after the OS has thrown our pending alarms
 * away.
 *
 * Mirrors `imb.tzalarmclock.alarm.receiver.BootReceiver`: a reboot (and an
 * app update, via `ACTION_MY_PACKAGE_REPLACED`) clears every registered
 * alarm, so without this a timer that was running before a restart simply
 * never fires. Its persisted `endInstant` (Stage 13) needs no adjustment for
 * how long the device was off — the concrete mechanism behind the spec's
 * "a running timer continues counting down correctly across such an event."
 *
 * Not `ACTION_LOCKED_BOOT_COMPLETED`, same reasoning as the alarm receiver:
 * the timer database lives in credential-protected storage.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val app = context.applicationContext
        goAsyncWork(TAG) {
            TimerRingingNotifications.ensureChannel(app)
            TimerProvider.scheduler(app).syncAll()
        }
    }

    private companion object {
        const val TAG = "TimerBootReceiver"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
