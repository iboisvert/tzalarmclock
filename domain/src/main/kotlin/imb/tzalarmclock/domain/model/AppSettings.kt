package imb.tzalarmclock.domain.model

import java.time.ZoneId

/**
 * The app-wide settings, as a single immutable snapshot.
 *
 * The spec says settings are written when the settings page closes, so the
 * whole object is read and written at once rather than field by field.
 *
 * Every property has a default, which is what a fresh install sees before the
 * user has ever opened the settings page.
 *
 * @param homeZone the user's home time zone, or `null` to follow the device.
 * @param snoozePeriodMinutes how long a snooze lasts.
 * @param maxSnoozeCount snoozes allowed per ring before only Dismiss is offered.
 * @param defaultRingtoneUri fallback for [Alarm.ringtoneUri]; `null` means the
 *   system default alarm sound.
 * @param alarmVolume playback volume in `0.0..1.0`, relative to the device's
 *   alarm stream.
 * @param volumeEscalation ramp up to [alarmVolume] instead of starting there.
 * @param defaultVibrate fallback for [Alarm.vibrate].
 * @param use24HourFormat how times are rendered throughout the app.
 * @param ringTimeoutMinutes how long an alarm rings unacknowledged before
 *   `RingingService` acts on the user's behalf: snoozes it, the same as an
 *   explicit tap on Snooze, if [maxSnoozeCount] allows one more; otherwise
 *   dismisses it, the same as an explicit hold-to-dismiss (non-recurring
 *   alarms disable, snooze state clears) — either way, without a tap. Also
 *   `TimerRingingService`'s per-timer auto-dismiss timeout — timers have no
 *   snooze concept, so an unacknowledged timer just dismisses once this many
 *   minutes pass, the same as an explicit tap on Dismiss.
 * @param defaultTimerRingtoneUri ring tone `TimerRingingService` plays for
 *   every timer; `null` means the system default alarm sound. Timers have
 *   no per-timer override the way [Alarm.ringtoneUri] does — the spec adds
 *   only this one global setting for timers.
 */
data class AppSettings(
    val homeZone: ZoneId? = null,
    val snoozePeriodMinutes: Int = DEFAULT_SNOOZE_PERIOD_MINUTES,
    val maxSnoozeCount: Int = DEFAULT_MAX_SNOOZE_COUNT,
    val defaultRingtoneUri: String? = null,
    val alarmVolume: Float = DEFAULT_ALARM_VOLUME,
    val volumeEscalation: Boolean = false,
    val defaultVibrate: Boolean = true,
    val use24HourFormat: Boolean = true,
    val ringTimeoutMinutes: Int = DEFAULT_RING_TIMEOUT_MINUTES,
    val defaultTimerRingtoneUri: String? = null,
) {
    init {
        require(snoozePeriodMinutes > 0) {
            "Snooze period must be positive, was $snoozePeriodMinutes"
        }
        require(maxSnoozeCount >= 0) {
            "Max snooze count cannot be negative, was $maxSnoozeCount"
        }
        require(alarmVolume in MIN_ALARM_VOLUME..MAX_ALARM_VOLUME) {
            "Alarm volume must be in $MIN_ALARM_VOLUME..$MAX_ALARM_VOLUME, was $alarmVolume"
        }
        require(ringTimeoutMinutes > 0) {
            "Ring timeout must be positive, was $ringTimeoutMinutes"
        }
    }

    companion object {
        const val DEFAULT_SNOOZE_PERIOD_MINUTES = 10
        const val DEFAULT_MAX_SNOOZE_COUNT = 3
        const val DEFAULT_ALARM_VOLUME = 1.0f
        const val MIN_ALARM_VOLUME = 0.0f
        const val MAX_ALARM_VOLUME = 1.0f
        const val DEFAULT_RING_TIMEOUT_MINUTES = 3

        /** What a fresh install starts with. */
        val DEFAULTS = AppSettings()
    }
}
