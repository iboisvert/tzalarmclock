package imb.tzalarmclock.timer.schedule

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import imb.tzalarmclock.timer.receiver.TimerReceiver

/**
 * The pending intents `AlarmManager` fires back at us, and how to find them
 * again later in order to cancel them.
 *
 * Mirrors `imb.tzalarmclock.alarm.schedule.AlarmIntents`: each timer carries
 * a distinct `tzalarmclock://timer/<id>` data URI, so two intents are only
 * "the same" to `AlarmManager` — under [Intent.filterEquals] — when they're
 * for the same timer.
 */
internal object TimerIntents {

    const val ACTION_TIMER_FIRED = "imb.tzalarmclock.timer.action.TIMER_FIRED"
    const val EXTRA_TIMER_ID = "imb.tzalarmclock.timer.extra.TIMER_ID"

    /** The pending intent for [timerId], creating it if it doesn't exist yet. */
    fun arm(context: Context, timerId: Long): PendingIntent =
        pendingIntent(context, timerId, PendingIntent.FLAG_UPDATE_CURRENT)!!

    /**
     * The existing pending intent for [timerId], or `null` if there isn't one.
     *
     * [PendingIntent.FLAG_NO_CREATE] is what makes this a lookup rather than a
     * create — cancelling a timer that was never armed should be a no-op, not
     * a fresh registration.
     */
    fun existing(context: Context, timerId: Long): PendingIntent? =
        pendingIntent(context, timerId, PendingIntent.FLAG_NO_CREATE)

    /** Reads back the id [arm] wrote, or [NO_TIMER_ID] if this isn't our intent. */
    fun timerIdOf(intent: Intent): Long =
        intent.getLongExtra(EXTRA_TIMER_ID, NO_TIMER_ID)

    const val NO_TIMER_ID: Long = -1L

    private fun pendingIntent(context: Context, timerId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = ACTION_TIMER_FIRED
            data = "tzalarmclock://timer/$timerId".toUri()
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Shared by every timer: the data URI already distinguishes them, and a
     * constant keeps the mapping from a `Long` id to the `Int` request code
     * `PendingIntent` requires from ever having to collide.
     */
    private const val REQUEST_CODE = 0
}
