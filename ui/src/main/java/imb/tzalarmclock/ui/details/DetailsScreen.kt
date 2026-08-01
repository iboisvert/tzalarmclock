@file:OptIn(ExperimentalMaterial3Api::class)

package imb.tzalarmclock.ui.details

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import imb.tzalarmclock.domain.model.ScheduleType
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    alarmId: Long? = null,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = viewModel(),
) {
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val saveAndBack: () -> Unit = {
        scope.launch {
            viewModel.save()
            onBack()
        }
    }
    val deleteAndBack: () -> Unit = {
        scope.launch {
            viewModel.delete()
            onBack()
        }
    }
    DetailsScreen(
        uiState = uiState,
        onBack = saveAndBack,
        onDelete = deleteAndBack,
        onNameChanged = viewModel::onNameChanged,
        onTimeChanged = viewModel::onTimeChanged,
        onZoneChanged = viewModel::onZoneChanged,
        onScheduleTypeChanged = viewModel::onScheduleTypeChanged,
        onDateChanged = viewModel::onDateChanged,
        onWeekdayToggled = viewModel::onWeekdayToggled,
        onMonthDayToggled = viewModel::onMonthDayToggled,
        onRingtoneChanged = viewModel::onRingtoneChanged,
        onVibrateChanged = viewModel::onVibrateChanged,
        modifier = modifier,
    )
}

@Composable
private fun DetailsScreen(
    uiState: DetailsUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onNameChanged: (String) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
    onZoneChanged: (ZoneId?) -> Unit,
    onScheduleTypeChanged: (ScheduleType) -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onWeekdayToggled: (DayOfWeek) -> Unit,
    onMonthDayToggled: (Int) -> Unit,
    onRingtoneChanged: (String?) -> Unit,
    onVibrateChanged: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showZonePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "New Alarm" else "Edit Alarm") },
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
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            EditableRow(
                label = "Time",
                value = uiState.time.format(timeFormatter(uiState.use24HourFormat)),
                onEdit = { showTimePicker = true },
            )

            EditableRow(
                label = "Time zone",
                value = uiState.zone?.id ?: "Follows device (no zone)",
                onEdit = { showZonePicker = true },
                onClear = if (uiState.zone != null) ({ onZoneChanged(null) }) else null,
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Schedule", style = MaterialTheme.typography.labelLarge)
                ScheduleTypeSelector(selected = uiState.scheduleType, onSelect = onScheduleTypeChanged)
                when (uiState.scheduleType) {
                    ScheduleType.NEXT_OCCURRENCE -> Text(
                        "Rings once, at the next occurrence of this time.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ScheduleType.ONE_TIME_DATE -> EditableRow(
                        label = "Date",
                        value = uiState.date.format(DATE_FORMATTER),
                        onEdit = { showDatePicker = true },
                    )
                    ScheduleType.WEEKLY -> WeekdaySelector(
                        selected = uiState.weekdays,
                        onToggle = onWeekdayToggled,
                    )
                    ScheduleType.MONTHLY -> MonthDaySelector(
                        selected = uiState.monthDays,
                        onToggle = onMonthDayToggled,
                    )
                }
            }

            HorizontalDivider()

            RingtoneRow(
                ringtoneUri = uiState.ringtoneUri,
                defaultRingtoneUri = uiState.defaultRingtoneUri,
                onRingtoneChanged = onRingtoneChanged,
            )

            VibrationRow(
                vibrate = uiState.vibrate,
                defaultVibrate = uiState.defaultVibrate,
                onVibrateChanged = onVibrateChanged,
            )

            if (!uiState.isNew) {
                HorizontalDivider()
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete Alarm")
                }
            }
        }
    }

    if (showTimePicker) {
        AlarmTimePickerDialog(
            initialTime = uiState.time,
            is24Hour = uiState.use24HourFormat,
            onConfirm = {
                onTimeChanged(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
    if (showDatePicker) {
        AlarmDatePickerDialog(
            initialDate = uiState.date,
            onConfirm = {
                onDateChanged(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showZonePicker) {
        TimeZonePickerDialog(
            onSelect = {
                onZoneChanged(it)
                showZonePicker = false
            },
            onDismiss = { showZonePicker = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this alarm?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EditableRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit $label")
        }
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear $label")
            }
        }
    }
}

@Composable
private fun ScheduleTypeSelector(selected: ScheduleType, onSelect: (ScheduleType) -> Unit) {
    val options = ScheduleType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(type.displayLabel()) },
            )
        }
    }
}

private fun ScheduleType.displayLabel(): String = when (this) {
    ScheduleType.NEXT_OCCURRENCE -> "Once"
    ScheduleType.ONE_TIME_DATE -> "Date"
    ScheduleType.WEEKLY -> "Weekly"
    ScheduleType.MONTHLY -> "Monthly"
}

@Composable
private fun WeekdaySelector(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@Composable
private fun MonthDaySelector(selected: Set<Int>, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..31).forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(day.toString()) },
            )
        }
    }
}

@Composable
private fun RingtoneRow(
    ringtoneUri: String?,
    defaultRingtoneUri: String?,
    onRingtoneChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        onRingtoneChanged(uri?.toString())
    }
    val effectiveUri = ringtoneUri ?: defaultRingtoneUri
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
    val title = remember(effectiveUri) {
        effectiveUri?.let {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull()
        } ?: "Unknown"
    }
    EditableRow(
        label = "Ringtone",
        value = title,
        onEdit = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, effectiveUri?.let(Uri::parse))
            }
            launcher.launch(intent)
        },
        onClear = if (ringtoneUri != null) ({ onRingtoneChanged(null) }) else null,
    )
}

@Composable
private fun VibrationRow(vibrate: Boolean?, defaultVibrate: Boolean, onVibrateChanged: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vibration", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = vibrate == null,
                onClick = { onVibrateChanged(null) },
                label = { Text("Default (${if (defaultVibrate) "On" else "Off"})") },
            )
            FilterChip(selected = vibrate == true, onClick = { onVibrateChanged(true) }, label = { Text("On") })
            FilterChip(selected = vibrate == false, onClick = { onVibrateChanged(false) }, label = { Text("Off") })
        }
    }
}

@Composable
private fun AlarmTimePickerDialog(
    initialTime: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = is24Hour,
    )
    // usePlatformDefaultWidth = false: the default caps dialog width at a portrait-sized value,
    // which clips TimePickerDefaults.layoutType()'s Horizontal layout (the one it auto-switches
    // to on short/landscape screens, laying the clock beside the digital display instead of
    // above it). Letting the dialog size to its content fits that layout instead of fighting it.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun AlarmDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimeZonePickerDialog(onSelect: (ZoneId) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val zoneIds = remember { ZoneId.getAvailableZoneIds().sorted() }
    val filtered = remember(query) {
        if (query.isBlank()) zoneIds else zoneIds.filter { it.contains(query, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(16.dp).heightIn(max = 480.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search time zone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it }) { id ->
                        Text(
                            text = id,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(ZoneId.of(id)) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun timeFormatter(use24HourFormat: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24HourFormat) "HH:mm" else "h:mm a", Locale.getDefault())

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun DetailsScreenNewPreview() {
    TzAlarmClockTheme {
        DetailsScreen(
            uiState = DetailsUiState(isLoading = false, time = LocalTime.of(7, 0)),
            onBack = {},
            onDelete = {},
            onNameChanged = {},
            onTimeChanged = {},
            onZoneChanged = {},
            onScheduleTypeChanged = {},
            onDateChanged = {},
            onWeekdayToggled = {},
            onMonthDayToggled = {},
            onRingtoneChanged = {},
            onVibrateChanged = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DetailsScreenEditPreview() {
    TzAlarmClockTheme {
        DetailsScreen(
            uiState = DetailsUiState(
                alarmId = 1,
                isNew = false,
                isLoading = false,
                name = "Morning",
                time = LocalTime.of(7, 0),
                zone = ZoneId.of("America/Toronto"),
                scheduleType = ScheduleType.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
            onBack = {},
            onDelete = {},
            onNameChanged = {},
            onTimeChanged = {},
            onZoneChanged = {},
            onScheduleTypeChanged = {},
            onDateChanged = {},
            onWeekdayToggled = {},
            onMonthDayToggled = {},
            onRingtoneChanged = {},
            onVibrateChanged = {},
        )
    }
}
