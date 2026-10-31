package imb.tzalarmclock

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import imb.tzalarmclock.alarm.AlarmProvider
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.data.DataProvider
import imb.tzalarmclock.domain.format.TimerCountdown
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.schedule.start
import imb.tzalarmclock.timer.TimerProvider
import imb.tzalarmclock.timer.ringing.TimerRingingService
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles the standard `android.provider.AlarmClock` intents, which is how
 * voice assistants and other apps set, show, dismiss and snooze alarms and
 * timers in whichever clock app the user picks.
 *
 * Has no UI of its own: it does the request, then either hands off to
 * [MainActivity] (when the request asks to show something, or doesn't carry
 * enough to act on without the user) or confirms with a toast, and finishes.
 * The request-to-alarm mapping lives in `AlarmClockRequests.kt`.
 *
 * Everything it writes goes through the same repositories and services the UI
 * uses, so arming, ringing and snooze bookkeeping behave exactly as if the
 * user had done it by hand.
 */
class AlarmClockIntentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A recreation means the first pass was interrupted part-way; redoing
        // it could set the same alarm or timer twice.
        if (savedInstanceState != null) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                handle(intent)
            } finally {
                finish()
            }
        }
    }

    private suspend fun handle(intent: Intent) {
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> setAlarm(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> startActivity(MainActivity.alarmsIntent(this))
            AlarmClock.ACTION_DISMISS_ALARM -> dismissAlarm(intent)
            AlarmClock.ACTION_SNOOZE_ALARM -> snoozeAlarm()
            AlarmClock.ACTION_SET_TIMER -> setTimer(intent)
            AlarmClock.ACTION_SHOW_TIMERS -> startActivity(MainActivity.timersIntent(this))
            AlarmClock.ACTION_DISMISS_TIMER -> dismissTimer()
        }
    }

    /**
     * With no hour there's nothing to set, so the user gets the new-alarm
     * page to fill in. Otherwise the alarm is saved straight away and, unless
     * the caller asked to skip the UI, opened for review.
     */
    private suspend fun setAlarm(intent: Intent) {
        if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            startActivity(MainActivity.alarmIntent(this, alarmId = null))
            return
        }
        val requested = alarmForSetRequest(
            hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1),
            minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0),
            message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE),
            calendarDays = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS),
            vibrate = intent.optionalBoolean(AlarmClock.EXTRA_VIBRATE),
            ringtoneUri = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)
                ?.takeIf { it != AlarmClock.VALUE_RINGTONE_SILENT },
        )
        if (requested == null) {
            toast(R.string.intent_invalid_time)
            return
        }

        val alarms = DataProvider.alarmRepository(this)
        val equivalent = findEquivalentAlarm(alarms.observeAlarms().first(), requested)
        val alarmId = if (equivalent != null) {
            alarms.setEnabled(equivalent.id, true)
            equivalent.id
        } else {
            alarms.save(requested)
        }
        // The application's own Flow collector would arm it too, but this
        // process may be gone moments after a skip-UI request finishes.
        AlarmProvider.scheduler(this).syncAll()

        if (intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            val use24HourFormat = DataProvider.settingsRepository(this).getSettings().use24HourFormat
            val formatter = DateTimeFormatter.ofPattern(if (use24HourFormat) "HH:mm" else "h:mm a", Locale.getDefault())
            toast(getString(R.string.intent_alarm_set, requested.time.format(formatter)))
        } else {
            startActivity(MainActivity.alarmIntent(this, alarmId))
        }
    }

    /**
     * A ringing alarm is what "dismiss" most often means, so that wins. With
     * nothing ringing, the search mode picks which upcoming alarms to turn
     * off; with no search mode, snoozed alarms are the only ones it can mean.
     * Anything still ambiguous goes to the Alarms page for the user to choose.
     *
     * A snoozed alarm is dismissed the same way its notification's Dismiss
     * action does it; any other match is switched off, since there is no
     * "skip just the next occurrence" here.
     */
    private suspend fun dismissAlarm(intent: Intent) {
        if (RingingService.currentlyRingingAlarmIds().isNotEmpty()) {
            startService(RingingService.dismissAllIntent(this))
            toast(R.string.intent_alarm_dismissed)
            return
        }

        val now = ZonedDateTime.now()
        val snoozedUntil = RingingService.allSnoozedUntilMillis(this)
            .mapValues { Instant.ofEpochMilli(it.value) }
            .filterValues { it.isAfter(now.toInstant()) }
        val repository = DataProvider.alarmRepository(this)
        val alarms = repository.observeAlarms().first()
        val target = dismissTarget(intent)
        val matched = if (target == null) {
            alarms.filter { it.id in snoozedUntil }
        } else {
            alarmsToDismiss(target, alarms, now, snoozedUntil)
        }
        if (matched.isEmpty()) {
            startActivity(MainActivity.alarmsIntent(this))
            return
        }

        matched.forEach { alarm ->
            if (alarm.id in snoozedUntil) {
                startService(RingingService.dismissIntent(this, alarm.id))
            } else {
                repository.setEnabled(alarm.id, false)
            }
        }
        AlarmProvider.scheduler(this).syncAll()
        toast(resources.getQuantityString(R.plurals.intent_alarms_turned_off, matched.size, matched.size))
    }

    private fun dismissTarget(intent: Intent): DismissTarget? =
        when (intent.getStringExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE)) {
            AlarmClock.ALARM_SEARCH_MODE_ALL -> DismissTarget.All
            AlarmClock.ALARM_SEARCH_MODE_NEXT -> DismissTarget.Next
            AlarmClock.ALARM_SEARCH_MODE_LABEL ->
                intent.getStringExtra(AlarmClock.EXTRA_MESSAGE)?.let { DismissTarget.Label(it) }
            AlarmClock.ALARM_SEARCH_MODE_TIME -> {
                if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
                    DismissTarget.Time(
                        hour = to24Hour(
                            intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1),
                            intent.optionalBoolean(AlarmClock.EXTRA_IS_PM),
                        ),
                        minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0),
                    )
                } else {
                    null
                }
            }
            else -> null
        }

    /**
     * Snoozes every ringing alarm, the same as the Ring screen's Snooze
     * button. `EXTRA_ALARM_SNOOZE_DURATION` is ignored: the snooze period is
     * the app-wide setting, which the snooze count limit is built around.
     */
    private fun snoozeAlarm() {
        if (RingingService.currentlyRingingAlarmIds().isEmpty()) {
            toast(R.string.intent_no_alarm_ringing)
            return
        }
        startService(RingingService.snoozeAllIntent(this))
        toast(R.string.intent_alarm_snoozed)
    }

    /**
     * Starts a new timer counting down right away. With no usable length
     * there's nothing to start, so the user gets the Timers page instead.
     * `EXTRA_MESSAGE` is ignored: timers have no name.
     */
    private suspend fun setTimer(intent: Intent) {
        val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
        if (seconds !in 1..MAX_TIMER_SECONDS) {
            startActivity(MainActivity.timersIntent(this))
            return
        }
        val now = Instant.now()
        val duration = Duration.ofSeconds(seconds.toLong())
        DataProvider.timerRepository(this).save(Timer(configuredDuration = duration, createdAt = now).start(now))
        TimerProvider.scheduler(this).syncAll()

        if (intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
            toast(getString(R.string.intent_timer_started, TimerCountdown.format(duration)))
        } else {
            startActivity(MainActivity.timersIntent(this))
        }
    }

    private fun dismissTimer() {
        if (TimerRingingService.currentlyRingingTimerIds().isEmpty()) {
            toast(R.string.intent_no_timer_ringing)
            return
        }
        startService(TimerRingingService.dismissAllIntent(this))
        toast(R.string.intent_timer_dismissed)
    }

    /** A boolean extra, or `null` when the caller didn't send one. */
    private fun Intent.optionalBoolean(name: String): Boolean? =
        if (hasExtra(name)) getBooleanExtra(name, false) else null

    private fun toast(@StringRes text: Int) = toast(getString(text))

    /** Application context, since this activity finishes while the toast is still showing. */
    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()

    private companion object {
        /** `AlarmClock.EXTRA_LENGTH`'s documented upper bound: 24 hours. */
        const val MAX_TIMER_SECONDS = 24 * 60 * 60
    }
}
