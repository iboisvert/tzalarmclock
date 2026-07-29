package imb.tzalarmclock.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import imb.tzalarmclock.alarm.AlarmProvider
import imb.tzalarmclock.alarm.notify.AlarmNotifications
import imb.tzalarmclock.alarm.schedule.AlarmIntents
import imb.tzalarmclock.data.DataProvider

/**
 * Runs when an armed alarm reaches its instant.
 *
 * Three things happen, in this order: the user is told, the alarm is retired if
 * it will never ring again, and the OS is re-synced so the *next* occurrence is
 * armed. Only one occurrence is ever pending per alarm, so this last step is
 * what keeps a recurring alarm recurring.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_ALARM_FIRED) return
        val alarmId = AlarmIntents.alarmIdOf(intent)
        if (alarmId == AlarmIntents.NO_ALARM_ID) return

        val app = context.applicationContext
        goAsyncWork(TAG) {
            val alarms = DataProvider.alarmRepository(app)
            val alarm = alarms.getAlarm(alarmId)
            if (alarm == null) {
                Log.w(TAG, "Alarm $alarmId fired but no longer exists")
            } else {
                AlarmNotifications.notifyFired(app, alarm)

                // A one-off alarm has to be switched off here, not just left
                // unarmed: an alarm defined by time alone would otherwise find
                // "the next occurrence of 07:00" tomorrow and re-arm forever.
                // Stage 6 moves this to the moment the user dismisses it, once
                // there is a ringing screen to dismiss from.
                if (!alarm.schedule.isRecurring) {
                    alarms.setEnabled(alarmId, false)
                }
            }
            AlarmProvider.scheduler(app).syncAll()
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
