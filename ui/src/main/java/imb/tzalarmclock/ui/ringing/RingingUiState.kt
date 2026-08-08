package imb.tzalarmclock.ui.ringing

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the Ringing screen displays, refreshed on every clock tick while ringing.
 *
 * @param stillRinging `false` once [imb.tzalarmclock.alarm.ringing.RingingService]
 *   has ended this ring cycle on its own — an unacknowledged-ring timeout,
 *   most notably — rather than through a tap on this screen. Defaults `true`
 *   so the screen never flashes "finished" before the ViewModel's first
 *   real read of it.
 */
data class RingingUiState(
    val alarmName: String = "",
    val timeLabel: String = "",
    val dateLabel: String = "",
    val zoneLabel: String = "",
    val canSnooze: Boolean = true,
    val snoozesRemaining: Int = 0,
    val stillRinging: Boolean = true,
)

/**
 * Builds the Ringing screen's state.
 *
 * @param now the current instant, in the *device's* current zone — the spec's
 *   "current time, time zone, date" describes the environment the user is in
 *   right now, not necessarily a zone-locked alarm's own zone.
 * @param snoozeCount how many times this ring cycle has already been snoozed
 *   ([imb.tzalarmclock.alarm.ringing.SnoozeRegistry]'s count), compared
 *   against [AppSettings.maxSnoozeCount] to decide whether Snooze is still
 *   offered.
 */
fun buildRingingUiState(
    alarm: Alarm,
    settings: AppSettings,
    now: ZonedDateTime,
    snoozeCount: Int,
): RingingUiState {
    val remaining = (settings.maxSnoozeCount - snoozeCount).coerceAtLeast(0)
    return RingingUiState(
        alarmName = alarm.name.ifBlank { "Alarm" },
        timeLabel = now.toLocalTime().format(timeFormatter(settings.use24HourFormat)),
        dateLabel = now.toLocalDate().format(DATE_FORMATTER),
        zoneLabel = now.zone.id,
        canSnooze = remaining > 0,
        snoozesRemaining = remaining,
    )
}

private fun timeFormatter(use24HourFormat: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24HourFormat) "HH:mm" else "h:mm a", Locale.getDefault())

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
