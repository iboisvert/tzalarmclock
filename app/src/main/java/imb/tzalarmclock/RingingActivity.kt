package imb.tzalarmclock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import imb.tzalarmclock.alarm.ringing.RingingService
import imb.tzalarmclock.ui.ringing.RingingScreen
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

/**
 * Hosts the ringing screen over the lock screen or on top of other apps.
 *
 * A dedicated Activity rather than a destination in `MainActivity`'s NavHost:
 * [RingingService] needs to show this regardless of whether `MainActivity`
 * (or the app at all) is already running, and it needs window flags —
 * show-over-lock-screen, turn-the-screen-on — that make no sense for the rest
 * of the app.
 */
class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmId = intent.getLongExtra(RingingService.EXTRA_ALARM_ID, NO_ALARM_ID)
        if (alarmId == NO_ALARM_ID) {
            finish()
            return
        }

        setContent {
            TzAlarmClockTheme {
                RingingScreen(
                    alarmId = alarmId,
                    onFinish = ::finish,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private companion object {
        const val NO_ALARM_ID = -1L
    }
}
