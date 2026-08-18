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
class AlarmRingingActivity : ComponentActivity() {

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

    /**
     * Re-hides the navigation/status bars whenever this window regains
     * focus, since the OS drops immersive mode on its own the moment focus
     * is lost (e.g. a system dialog, or the user swiping the bars back into
     * view) and never restores it automatically.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Hides the status and navigation bars so the on-screen nav buttons
     * can't sit on top of — and intercept taps meant for — Snooze/Dismiss at
     * the bottom of [RingingScreen]. `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`
     * still lets a swipe-in from the edge reveal them temporarily (as an
     * overlay, not a layout change) if the user needs them for something
     * else, e.g. reaching Quick Settings.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private companion object {
        const val NO_ALARM_ID = -1L
    }
}
