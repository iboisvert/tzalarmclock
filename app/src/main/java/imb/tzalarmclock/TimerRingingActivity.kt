package imb.tzalarmclock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import imb.tzalarmclock.timer.ringing.TimerRingingService
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme
import imb.tzalarmclock.ui.timerringing.TimerRingingScreen

/**
 * Hosts the Timer Ringing screen over the lock screen or on top of other
 * apps — mirrors [RingingActivity]'s role for alarms.
 */
class TimerRingingActivity : ComponentActivity() {

    /**
     * Lazy because it needs `window`, which isn't set until [onCreate] calls
     * `super.onCreate()`.
     */
    private val insetsController: WindowInsetsControllerCompat by lazy {
        WindowInsetsControllerCompat(window, window.decorView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val timerId = intent.getLongExtra(TimerRingingService.EXTRA_TIMER_ID, NO_TIMER_ID)
        if (timerId == NO_TIMER_ID) {
            finish()
            return
        }

        setContent {
            TzAlarmClockTheme {
                TimerRingingScreen(
                    timerId = timerId,
                    onFinish = ::finish,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    /**
     * Re-hides the navigation/status bars whenever this window regains
     * focus — same reasoning as [RingingActivity].
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private companion object {
        const val NO_TIMER_ID = -1L
    }
}
