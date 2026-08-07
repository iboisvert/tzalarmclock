@file:OptIn(ExperimentalMaterial3Api::class)

package imb.tzalarmclock.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import imb.tzalarmclock.ui.common.EditableRow
import imb.tzalarmclock.ui.common.RingtonePickerRow
import imb.tzalarmclock.ui.common.TimeZonePickerDialog
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    versionInfo: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val saveAndBack: () -> Unit = {
        scope.launch {
            viewModel.save()
            onBack()
        }
    }
    SettingsScreen(
        uiState = uiState,
        onBack = saveAndBack,
        onHomeZoneChanged = viewModel::onHomeZoneChanged,
        onSnoozePeriodChanged = viewModel::onSnoozePeriodChanged,
        onMaxSnoozeCountChanged = viewModel::onMaxSnoozeCountChanged,
        onRingtoneChanged = viewModel::onRingtoneChanged,
        onAlarmVolumeChanged = viewModel::onAlarmVolumeChanged,
        onVolumeEscalationChanged = viewModel::onVolumeEscalationChanged,
        onDefaultVibrateChanged = viewModel::onDefaultVibrateChanged,
        onUse24HourFormatChanged = viewModel::onUse24HourFormatChanged,
        versionInfo = versionInfo,
        modifier = modifier,
    )
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onHomeZoneChanged: (ZoneId?) -> Unit,
    onSnoozePeriodChanged: (Int) -> Unit,
    onMaxSnoozeCountChanged: (Int) -> Unit,
    onRingtoneChanged: (String?) -> Unit,
    onAlarmVolumeChanged: (Float) -> Unit,
    onVolumeEscalationChanged: (Boolean) -> Unit,
    onDefaultVibrateChanged: (Boolean) -> Unit,
    onUse24HourFormatChanged: (Boolean) -> Unit,
    versionInfo: String,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var showZonePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            EditableRow(
                label = "Home time zone",
                value = uiState.homeZone?.id ?: "Follows device (no home zone)",
                onEdit = { showZonePicker = true },
                onClear = if (uiState.homeZone != null) ({ onHomeZoneChanged(null) }) else null,
            )

            HorizontalDivider()

            StepperRow(
                label = "Snooze period",
                value = uiState.snoozePeriodMinutes,
                suffix = "min",
                min = MIN_SNOOZE_PERIOD_MINUTES,
                max = MAX_SNOOZE_PERIOD_MINUTES,
                onValueChanged = onSnoozePeriodChanged,
            )

            StepperRow(
                label = "Max snooze count",
                value = uiState.maxSnoozeCount,
                suffix = if (uiState.maxSnoozeCount == 1) "snooze" else "snoozes",
                min = MIN_SNOOZE_COUNT,
                max = MAX_SNOOZE_COUNT,
                onValueChanged = onMaxSnoozeCountChanged,
            )

            HorizontalDivider()

            RingtonePickerRow(
                label = "Default ringtone",
                ringtoneUri = uiState.defaultRingtoneUri,
                fallbackUri = null,
                onRingtoneChanged = onRingtoneChanged,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Alarm volume", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = uiState.alarmVolume,
                    onValueChange = onAlarmVolumeChanged,
                    valueRange = 0f..1f,
                )
            }

            SwitchRow(
                label = "Escalate volume while ringing",
                checked = uiState.volumeEscalation,
                onCheckedChange = onVolumeEscalationChanged,
            )

            HorizontalDivider()

            SwitchRow(
                label = "Vibrate by default",
                checked = uiState.defaultVibrate,
                onCheckedChange = onDefaultVibrateChanged,
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Time format", style = MaterialTheme.typography.labelMedium)
                TimeFormatSelector(
                    use24HourFormat = uiState.use24HourFormat,
                    onUse24HourFormatChanged = onUse24HourFormatChanged,
                )
            }

            Text(
                text = versionInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showZonePicker) {
        TimeZonePickerDialog(
            onSelect = {
                onHomeZoneChanged(it)
                showZonePicker = false
            },
            onDismiss = { showZonePicker = false },
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    onValueChanged: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$value $suffix", style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = { onValueChanged(value - 1) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.headlineSmall)
        }
        TextButton(onClick = { onValueChanged(value + 1) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimeFormatSelector(use24HourFormat: Boolean, onUse24HourFormatChanged: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !use24HourFormat,
            onClick = { onUse24HourFormatChanged(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("12-hour") },
        )
        SegmentedButton(
            selected = use24HourFormat,
            onClick = { onUse24HourFormatChanged(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("24-hour") },
        )
    }
}

private const val MIN_SNOOZE_PERIOD_MINUTES = 1
private const val MAX_SNOOZE_PERIOD_MINUTES = 60
private const val MIN_SNOOZE_COUNT = 0
private const val MAX_SNOOZE_COUNT = 10

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TzAlarmClockTheme {
        SettingsScreen(
            uiState = SettingsUiState(isLoading = false),
            onBack = {},
            onHomeZoneChanged = {},
            onSnoozePeriodChanged = {},
            onMaxSnoozeCountChanged = {},
            onRingtoneChanged = {},
            onAlarmVolumeChanged = {},
            onVolumeEscalationChanged = {},
            onDefaultVibrateChanged = {},
            onUse24HourFormatChanged = {},
            versionInfo = "1.0.0-beta • built 2026-08-07 14:23 UTC",
        )
    }
}
