package imb.tzalarmclock

import android.Manifest
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
                    TzAlarmClockNavHost()
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
}
