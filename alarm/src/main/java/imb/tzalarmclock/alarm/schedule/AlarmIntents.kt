package imb.tzalarmclock.alarm.schedule

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import imb.tzalarmclock.alarm.receiver.AlarmReceiver

/**
 * The pending intents `AlarmManager` fires back at us, and how to find them
 * again later in order to cancel them.
 *
 * Two intents are only "the same" to `AlarmManager` when they match under
 * [Intent.filterEquals], which ignores extras but *does* compare data URIs.
 * Each alarm therefore carries a distinct `tzalarmclock://alarm/<id>` URI, so
 * re-arming an alarm replaces its own pending intent and never another's. The
 * id is repeated as an extra because that's the part the receiver can read.
 */
internal object AlarmIntents {

    const val ACTION_ALARM_FIRED = "imb.tzalarmclock.action.ALARM_FIRED"
    const val EXTRA_ALARM_ID = "imb.tzalarmclock.extra.ALARM_ID"

    /** The pending intent for [alarmId], creating it if it doesn't exist yet. */
    fun arm(context: Context, alarmId: Long): PendingIntent =
        pendingIntent(context, alarmId, PendingIntent.FLAG_UPDATE_CURRENT)!!

    /**
     * The existing pending intent for [alarmId], or `null` if there isn't one.
     *
     * [PendingIntent.FLAG_NO_CREATE] is what makes this a lookup rather than a
     * create — cancelling an alarm that was never armed should be a no-op, not
     * a fresh registration.
     */
    fun existing(context: Context, alarmId: Long): PendingIntent? =
        pendingIntent(context, alarmId, PendingIntent.FLAG_NO_CREATE)

    /** Reads back the id [arm] wrote, or [NO_ALARM_ID] if this isn't our intent. */
    fun alarmIdOf(intent: Intent): Long =
        intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM_ID)

    const val NO_ALARM_ID: Long = -1L

    private fun pendingIntent(context: Context, alarmId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
            data = "tzalarmclock://alarm/$alarmId".toUri()
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Shared by every alarm: the data URI already distinguishes them, and a
     * constant keeps the mapping from a `Long` id to the `Int` request code
     * `PendingIntent` requires from ever having to collide.
     */
    private const val REQUEST_CODE = 0
}
