package imb.tzalarmclock.domain.summary

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.remaining
import java.time.Instant

/**
 * Orders [Timer]s for the Timers Summary page.
 *
 * The spec defines Summary sort order for alarms explicitly (ascending
 * time-to-ring) but says nothing for timers. Placeholder, flagged for
 * product decision like the alarm equivalent
 * ([imb.tzalarmclock.domain.summary.summarizeAlarms]'s no-time-to-ring
 * group): running/paused timers first, soonest-to-fire first, then
 * stopped/expired timers by creation order.
 *
 * @param now the current instant, used to resolve each running timer's
 *   remaining time.
 */
fun List<Timer>.sortedForSummary(now: Instant): List<Timer> =
    sortedWith(
        compareBy(
            { it.state != TimerState.RUNNING && it.state != TimerState.PAUSED },
            { if (it.state == TimerState.RUNNING || it.state == TimerState.PAUSED) it.remaining(now) else null },
            { it.createdAt },
            { it.id },
        ),
    )
