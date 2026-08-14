package imb.tzalarmclock.data.db

import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.model.TimerState
import java.time.Duration
import java.time.Instant

/** Converts a domain [Timer] into its stored form. */
internal fun Timer.toEntity(): TimerEntity = TimerEntity(
    id = id,
    configuredDurationSeconds = configuredDuration.seconds,
    state = state.name,
    remainingAtPauseSeconds = remainingAtPause?.seconds,
    endInstantMillis = endInstant?.toEpochMilli(),
    createdAtMillis = createdAt.toEpochMilli(),
)

/** Converts a stored timer back into its domain form. */
internal fun TimerEntity.toDomain(): Timer = Timer(
    id = id,
    configuredDuration = Duration.ofSeconds(configuredDurationSeconds),
    state = TimerState.valueOf(state),
    remainingAtPause = remainingAtPauseSeconds?.let(Duration::ofSeconds),
    endInstant = endInstantMillis?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)
