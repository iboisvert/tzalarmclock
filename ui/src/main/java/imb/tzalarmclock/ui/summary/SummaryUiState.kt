package imb.tzalarmclock.ui.summary

import imb.tzalarmclock.domain.format.FuzzyCountdown
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.model.AppSettings
import imb.tzalarmclock.domain.summary.AlarmGroup
import imb.tzalarmclock.domain.summary.summarizeAlarms
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the Summary screen renders, derived from the alarm list and settings. */
data class SummaryUiState(
    val sections: List<SummarySectionUi> = emptyList(),
) {
    val isEmpty: Boolean get() = sections.isEmpty()
}

data class SummarySectionUi(
    val group: AlarmGroup,
    val entries: List<SummaryEntryUi>,
)

/**
 * One alarm row as the Summary screen needs it.
 *
 * @param nextRingDateLabel the alarm's next-ring date, or `null` when disabled
 *   (mirrors [imb.tzalarmclock.domain.summary.AlarmSummaryEntry.nextOccurrence]).
 * @param countdownLabel the fuzzy countdown to that date, or `null` alongside it.
 */
data class SummaryEntryUi(
    val id: Long,
    val name: String,
    val localTimeLabel: String,
    val enabled: Boolean,
    val nextRingDateLabel: String?,
    val countdownLabel: String?,
)

/**
 * Builds the Summary screen's state from the current alarms, settings and
 * instant.
 *
 * @param snoozedUntil currently-snoozed alarms and the instant they're
 *   snoozed until (`RingingService.allSnoozedUntilMillis`), so a snoozed
 *   alarm's displayed group/date/countdown matches what's actually armed
 *   instead of its normally-computed next occurrence.
 */
fun buildSummaryUiState(
    alarms: List<Alarm>,
    settings: AppSettings,
    now: ZonedDateTime,
    snoozedUntil: Map<Long, Instant> = emptyMap(),
): SummaryUiState {
    val timeFormatter = timeFormatter(settings.use24HourFormat)
    val sections = summarizeAlarms(alarms, now, snoozedUntil = snoozedUntil).map { section ->
        SummarySectionUi(
            group = section.group,
            entries = section.entries.map { entry ->
                SummaryEntryUi(
                    id = entry.alarm.id,
                    name = entry.alarm.name,
                    localTimeLabel = entry.localTime.format(timeFormatter),
                    enabled = entry.alarm.enabled,
                    nextRingDateLabel = entry.nextOccurrence?.toLocalDate()?.format(DATE_FORMATTER),
                    countdownLabel = entry.nextOccurrence?.let {
                        FuzzyCountdown.format(now.toInstant(), it.toInstant())
                    },
                )
            },
        )
    }
    return SummaryUiState(sections)
}

/** Display label for a [AlarmGroup] section header. */
fun AlarmGroup.displayLabel(): String = when (this) {
    AlarmGroup.TODAY -> "Today"
    AlarmGroup.TOMORROW -> "Tomorrow"
    AlarmGroup.THIS_WEEK -> "This Week"
    AlarmGroup.LATER -> "Later"
}

private fun timeFormatter(use24HourFormat: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24HourFormat) "HH:mm" else "h:mm a", Locale.getDefault())

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
