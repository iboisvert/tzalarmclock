package imb.tzalarmclock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import imb.tzalarmclock.alarm.permission.BatteryOptimization
import imb.tzalarmclock.alarm.permission.ExactAlarmPermission
import imb.tzalarmclock.alarm.permission.SchedulingHealth
import imb.tzalarmclock.ui.nav.Routes
import imb.tzalarmclock.ui.nav.TzAlarmClockNavHost
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

class MainActivity : ComponentActivity() {

    /**
     * Re-read in [onResume] rather than only at startup: two of the three
     * fixes send the user out to system settings, and coming back is the only
     * signal we get that they did anything there.
     */
    private var health by mutableStateOf(
        SchedulingHealth(
            exactAlarmsAllowed = true,
            notificationsAllowed = true,
            batteryOptimized = false,
        ),
    )

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshHealth()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TzAlarmClockTheme {
                Column(Modifier.fillMaxSize()) {
                    SchedulingWarnings(
                        health = health,
                        onFixExactAlarms = {
                            startActivity(ExactAlarmPermission.settingsIntent(this@MainActivity))
                        },
                        onFixNotifications = ::askForNotifications,
                        onFixBattery = {
                            startActivity(BatteryOptimization.requestIntent(this@MainActivity))
                        },
                    )
                    TzAlarmClockNavHost(
                        versionInfo = "${BuildConfig.VERSION_NAME} • built ${BuildConfig.BUILD_TIMESTAMP}",
                        initialRoute = initialRoute(),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHealth()
    }

    private fun refreshHealth() {
        health = SchedulingHealth.check(this)
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * The page [AlarmClockIntentActivity] asked to open, if any. Mapped from
     * a fixed set of extras rather than taking a route string, since this
     * activity is exported and any app can send it extras.
     */
    private fun initialRoute(): String? = when (intent.getStringExtra(EXTRA_DESTINATION)) {
        DESTINATION_TIMERS -> Routes.Timers.route
        DESTINATION_ALARM -> Routes.Details.buildRoute(
            intent.getLongExtra(EXTRA_ALARM_ID, Routes.Details.NO_ALARM_ID)
                .takeIf { it != Routes.Details.NO_ALARM_ID },
        )
        else -> null
    }

    companion object {
        // EXTRA_DESTINATION and DESTINATION_TIMERS are duplicated in the
        // `timer` module's TimerCountdownNotification, which can't see these.
        private const val EXTRA_DESTINATION = "imb.tzalarmclock.extra.DESTINATION"
        private const val EXTRA_ALARM_ID = "imb.tzalarmclock.extra.ALARM_ID"
        private const val DESTINATION_TIMERS = "timers"
        private const val DESTINATION_ALARM = "alarm"

        /**
         * Opens the app on the Alarms page. `CLEAR_TOP` replaces whatever
         * page an already-running instance was on, so the requested page is
         * the one the user lands on.
         */
        fun alarmsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun timersIntent(context: Context): Intent =
            alarmsIntent(context).putExtra(EXTRA_DESTINATION, DESTINATION_TIMERS)

        /** The Details page for [alarmId], or for a new alarm when it's `null`. */
        fun alarmIntent(context: Context, alarmId: Long?): Intent =
            alarmsIntent(context)
                .putExtra(EXTRA_DESTINATION, DESTINATION_ALARM)
                .putExtra(EXTRA_ALARM_ID, alarmId ?: Routes.Details.NO_ALARM_ID)
    }
}
