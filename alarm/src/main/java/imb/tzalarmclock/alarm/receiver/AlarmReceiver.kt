package imb.tzalarmclock.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.alarm.schedule.AlarmIntents

/**
 * Runs when an armed alarm reaches its instant.
 *
 * Everything about actually ringing — checking the alarm still exists and is
 * enabled, playback, vibration, the ongoing notification, and the
 * snooze/dismiss-driven repository and scheduler writes — belongs to
 * [RingingService], which needs to keep running long after this receiver is
 * allowed to. This just hands off to it, so no `goAsync` coroutine work is
 * needed here at all.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_ALARM_FIRED) return
        val alarmId = AlarmIntents.alarmIdOf(intent)
        if (alarmId == AlarmIntents.NO_ALARM_ID) return

        ContextCompat.startForegroundService(context, RingingService.ringIntent(context, alarmId))
    }
}
