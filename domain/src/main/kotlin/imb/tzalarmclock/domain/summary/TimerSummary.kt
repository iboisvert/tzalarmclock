package imb.tzalarmclock.domain.summary

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.domain.schedule.remaining
import java.time.Instant

/**
 * Orders [Timer]s for the Timers Summary page.
 * Sort by create time and id
 */
fun List<Timer>.sortedForSummary(): List<Timer> =
    sortedWith(
        compareBy(
            { it.createdAt },
            { it.id },
        ),
    )
