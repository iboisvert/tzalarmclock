package imb.tzalarmclock.domain.model

import java.time.LocalTime
import java.time.ZoneId

/**
 * A single alarm as the user defined it.
 *
 * Deliberately holds no "next ring instant": for a floating alarm that answer
 * depends on the device's current zone, so it is computed on demand by the
 * Stage 2 engine rather than stored.
 *
 * @param id database id, or [NO_ID] for an alarm that hasn't been saved yet.
 * @param name user-visible label; may be blank.
 * @param time wall-clock time of day, with no date component.
 * @param zone the alarm's own time zone, or `null` for a *floating* alarm that
 *   rings at [time] in whatever zone the device is currently in. This
 *   distinction is the reason the app exists.
 * @param schedule which days the alarm rings on.
 * @param enabled a disabled alarm never rings.
 * @param ringtoneUri alarm-specific ringtone, or `null` to fall back to
 *   [AppSettings.defaultRingtoneUri]. Held as a string so the domain module
 *   stays free of Android types.
 * @param vibrate alarm-specific vibration setting, or `null` to fall back to
 *   [AppSettings.defaultVibrate].
 */
data class Alarm(
    val id: Long = NO_ID,
    val name: String = "",
    val time: LocalTime,
    val zone: ZoneId? = null,
    val schedule: AlarmSchedule = AlarmSchedule.NextOccurrence,
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val vibrate: Boolean? = null,
) {
    /** True when this alarm's ring instant is fixed regardless of device zone. */
    val isZoneLocked: Boolean get() = zone != null

    companion object {
        /** Id of an alarm that has not been persisted yet. */
        const val NO_ID: Long = 0L
    }
}
