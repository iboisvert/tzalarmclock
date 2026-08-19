package imb.tzalarmclock.ui.ringing

import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One ringing alarm's own display info, alongside however many others are also ringing — see [RingingUiState.alarms]. */
data class RingingAlarmUi(
    val alarmName: String,
    val snoozesRemaining: Int,
)

/**
 * What the Ringing screen displays, refreshed on every clock tick while ringing.
 *
 * The current time/date/zone are environmental, not per-alarm, so they stay
 * single fields even when [alarms] holds more than one — see
 * `imb.tzalarmclock.alarm.ringing.RingingService`'s class doc for why more
 * than one alarm can be ringing at once.
 *
 * @param canSnooze true if *any* ringing alarm still has a snooze left —
 *   tapping Snooze then snoozes whichever ones do and dismisses whichever
 *   ones don't, the same decision an individual alarm's own unacknowledged-
 *   ring timeout makes.
 * @param stillRinging `false` once [imb.tzalarmclock.alarm.ringing.RingingService]
 *   has ended every ring cycle on its own — an unacknowledged-ring timeout,
 *   most notably — rather than through a tap on this screen. Defaults `true`
 *   so the screen never flashes "finished" before the ViewModel's first
 *   real read of it.
 */
data class RingingUiState(
    val timeLabel: String = "",
    val dateLabel: String = "",
    val zoneLabel: String = "",
    val alarms: List<RingingAlarmUi> = emptyList(),
    val canSnooze: Boolean = false,
    val stillRinging: Boolean = true,
)

/**
 * Builds the Ringing screen's state.
 *
 * @param now the current instant, in the *device's* current zone — the spec's
 *   "current time, time zone, date" describes the environment the user is in
 *   right now, not necessarily a zone-locked alarm's own zone.
 * @param snoozeCounts each ringing alarm's own snooze count so far this ring
 *   cycle ([imb.tzalarmclock.alarm.ringing.SnoozeRegistry]'s count, keyed by
 *   alarm id), compared against [AppSettings.maxSnoozeCount] to decide
 *   whether Snooze is still offered for it.
 */
fun buildRingingUiState(
    alarms: List<Alarm>,
    settings: AppSettings,
    now: ZonedDateTime,
    snoozeCounts: Map<Long, Int>,
): RingingUiState {
    val alarmUis = alarms.map { alarm ->
        val remaining = (settings.maxSnoozeCount - (snoozeCounts[alarm.id] ?: 0)).coerceAtLeast(0)
        RingingAlarmUi(alarmName = alarm.name.ifBlank { "Alarm" }, snoozesRemaining = remaining)
    }
    return RingingUiState(
        timeLabel = now.toLocalTime().format(timeFormatter(settings.use24HourFormat)),
        dateLabel = now.toLocalDate().format(DATE_FORMATTER),
        zoneLabel = now.zone.id,
        alarms = alarmUis,
        canSnooze = alarmUis.any { it.snoozesRemaining > 0 },
    )
}

private fun timeFormatter(use24HourFormat: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24HourFormat) "HH:mm" else "h:mm a", Locale.getDefault())

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
