package imb.tzalarmclock.ui.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import imb.tzalarmclock.domain.model.TimerState
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme
import java.time.Duration

@Composable
fun TimersScreen(
    onOpenAlarms: () -> Unit,
    onOpenTimers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimersViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    TimersScreen(
        uiState = uiState,
        onOpenAlarms = onOpenAlarms,
        onOpenTimers = onOpenTimers,
        onOpenSettings = onOpenSettings,
        onStart = viewModel::startTimer,
        onPause = viewModel::pauseTimer,
        onResume = viewModel::resumeTimer,
        onReset = viewModel::resetTimer,
        onDelete = viewModel::deleteTimer,
        onAddTimer = viewModel::addTimer,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimersScreen(
    uiState: TimersUiState,
    onOpenAlarms: () -> Unit,
    onOpenTimers: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: (Long) -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onReset: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAddTimer: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddTimer by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Timers") },
                actions = {
                    // Always visible and tappable, including this page's own
                    // icon (a no-op re-navigation) — see the dev plan's
                    // active-nav-icon assumption.
                    IconButton(onClick = onOpenAlarms) {
                        Icon(Icons.Filled.Alarm, contentDescription = "Alarms")
                    }
                    IconButton(onClick = onOpenTimers) {
                        Icon(Icons.Filled.Timer, contentDescription = "Timers")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTimer = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add timer")
            }
        },
    ) { innerPadding ->
        if (uiState.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No timers yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(uiState.timers, key = { it.id }) { timer ->
                    TimerRow(
                        timer = timer,
                        onStart = { onStart(timer.id) },
                        onPause = { onPause(timer.id) },
                        onResume = { onResume(timer.id) },
                        onReset = { onReset(timer.id) },
                        onDelete = { onDelete(timer.id) },
                    )
                }
            }
        }
    }

    if (showAddTimer) {
        AddTimerDialog(
            onAdd = { duration ->
                onAddTimer(duration)
                showAddTimer = false
            },
            onDismiss = { showAddTimer = false },
        )
    }
}

@Composable
private fun TimerRow(
    timer: TimerRowUi,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timer.remainingLabel,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        when (timer.state) {
            TimerState.STOPPED -> IconButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Start")
            }
            TimerState.RUNNING -> IconButton(onClick = onPause) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause")
            }
            TimerState.PAUSED -> IconButton(onClick = onResume) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
            }
            TimerState.EXPIRED -> Unit
        }
        // Always available regardless of state, per Stage 14's "usable from
        // any state" reset design.
        IconButton(onClick = onReset) {
            Icon(Icons.Filled.Replay, contentDescription = "Reset")
        }
        // No delete confirmation, deliberately inconsistent with alarm
        // deletion — see the dev plan's assumption log.
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun AddTimerDialog(onAdd: (Duration) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableStateOf(DEFAULT_HOURS) }
    var minutes by remember { mutableStateOf(DEFAULT_MINUTES) }
    var seconds by remember { mutableStateOf(DEFAULT_SECONDS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Timer") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DurationField(
                    label = "h",
                    value = hours,
                    onValueChange = { hours = it },
                    modifier = Modifier.weight(1f),
                )
                DurationField(
                    label = "min",
                    value = minutes,
                    onValueChange = { minutes = it },
                    modifier = Modifier.weight(1f),
                )
                DurationField(
                    label = "s",
                    value = seconds,
                    onValueChange = { seconds = it },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val duration = Duration.ofHours(hours.toLong())
                        .plusMinutes(minutes.toLong())
                        .plusSeconds(seconds.toLong())
                    onAdd(duration)
                },
            ) {
                Text("Add")
            }
        },
        // No dismiss button by design (see the dev plan's Add Timer
        // assumption) - tapping outside or the system back button abandons
        // the form with no side effects, same as onDismissRequest above.
    )
}

/**
 * A single h/min/s field for [AddTimerDialog].
 *
 * Selects its entire existing value the moment it gains focus, so the
 * user's next keystroke replaces it rather than appending, per the spec's
 * Adding a Timer section. An empty field reads as zero.
 */
@Composable
private fun DurationField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value.takeIf { it != 0 }?.toString().orEmpty())) }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { new ->
            val digits = new.text.filter(Char::isDigit).take(MAX_DIGITS)
            fieldValue = new.copy(text = digits)
            onValueChange(digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
            }
        },
    )
}

private const val MAX_DIGITS = 3
private const val DEFAULT_HOURS = 0
private const val DEFAULT_MINUTES = 5
private const val DEFAULT_SECONDS = 0

@Preview(showBackground = true)
@Composable
private fun TimersScreenEmptyPreview() {
    TzAlarmClockTheme {
        TimersScreen(
            uiState = TimersUiState(),
            onOpenAlarms = {},
            onOpenTimers = {},
            onOpenSettings = {},
            onStart = {},
            onPause = {},
            onResume = {},
            onReset = {},
            onDelete = {},
            onAddTimer = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimersScreenPopulatedPreview() {
    TzAlarmClockTheme {
        TimersScreen(
            uiState = TimersUiState(
                timers = listOf(
                    TimerRowUi(id = 1, remainingLabel = "4:32", state = TimerState.RUNNING),
                    TimerRowUi(id = 2, remainingLabel = "10:00", state = TimerState.PAUSED),
                    TimerRowUi(id = 3, remainingLabel = "5:00", state = TimerState.STOPPED),
                ),
            ),
            onOpenAlarms = {},
            onOpenTimers = {},
            onOpenSettings = {},
            onStart = {},
            onPause = {},
            onResume = {},
            onReset = {},
            onDelete = {},
            onAddTimer = {},
        )
    }
}
