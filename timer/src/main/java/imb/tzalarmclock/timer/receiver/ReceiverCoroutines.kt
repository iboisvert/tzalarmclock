package imb.tzalarmclock.timer.receiver

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs [block] past the end of [BroadcastReceiver.onReceive].
 *
 * A duplicate of `imb.tzalarmclock.alarm.receiver.goAsyncWork`, not a shared
 * dependency on it — see the dev plan's timer-module guiding decision. Every
 * receiver here has to touch the database, which is suspending, and
 * `onReceive` runs on the main thread and must return quickly. [goAsync]
 * keeps the process alive and holds a wake lock until `finish()` is called.
 *
 * Failures are logged rather than thrown: an exception escaping here would
 * kill the app process during boot, and a missed re-arm is recovered by the
 * next sync.
 */
internal fun BroadcastReceiver.goAsyncWork(tag: String, block: suspend () -> Unit) {
    val result = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(tag, "Failed to handle broadcast", e)
        } finally {
            result.finish()
        }
    }
}
