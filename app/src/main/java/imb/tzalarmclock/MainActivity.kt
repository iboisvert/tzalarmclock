package imb.tzalarmclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import imb.tzalarmclock.ui.nav.TzAlarmClockNavHost
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TzAlarmClockTheme {
                TzAlarmClockNavHost()
            }
        }
    }
}
