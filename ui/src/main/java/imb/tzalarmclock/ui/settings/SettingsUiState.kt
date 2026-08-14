package imb.tzalarmclock.ui.settings

import imb.tzalarmclock.domain.model.AppSettings
import java.time.ZoneId

/** Editable snapshot of the app-wide settings, backing the Settings form. */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val homeZone: ZoneId? = null,
    val snoozePeriodMinutes: Int = AppSettings.DEFAULT_SNOOZE_PERIOD_MINUTES,
    val maxSnoozeCount: Int = AppSettings.DEFAULT_MAX_SNOOZE_COUNT,
    val defaultRingtoneUri: String? = null,
    val alarmVolume: Float = AppSettings.DEFAULT_ALARM_VOLUME,
    val volumeEscalation: Boolean = false,
    val defaultVibrate: Boolean = true,
    val use24HourFormat: Boolean = true,
    val ringTimeoutMinutes: Int = AppSettings.DEFAULT_RING_TIMEOUT_MINUTES,
    val defaultTimerRingtoneUri: String? = null,
)

fun buildSettingsUiState(settings: AppSettings): SettingsUiState = SettingsUiState(
    isLoading = false,
    homeZone = settings.homeZone,
    snoozePeriodMinutes = settings.snoozePeriodMinutes,
    maxSnoozeCount = settings.maxSnoozeCount,
    defaultRingtoneUri = settings.defaultRingtoneUri,
    alarmVolume = settings.alarmVolume,
    volumeEscalation = settings.volumeEscalation,
    defaultVibrate = settings.defaultVibrate,
    use24HourFormat = settings.use24HourFormat,
    ringTimeoutMinutes = settings.ringTimeoutMinutes,
    defaultTimerRingtoneUri = settings.defaultTimerRingtoneUri,
)

/** Converts the current form state back into [AppSettings] for persistence. */
fun SettingsUiState.toAppSettings(): AppSettings = AppSettings(
    homeZone = homeZone,
    snoozePeriodMinutes = snoozePeriodMinutes,
    maxSnoozeCount = maxSnoozeCount,
    defaultRingtoneUri = defaultRingtoneUri,
    alarmVolume = alarmVolume,
    volumeEscalation = volumeEscalation,
    defaultVibrate = defaultVibrate,
    use24HourFormat = use24HourFormat,
    ringTimeoutMinutes = ringTimeoutMinutes,
    defaultTimerRingtoneUri = defaultTimerRingtoneUri,
)
