package imb.tzalarmclock.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import imb.tzalarmclock.domain.summary.AlarmGroup
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

@Composable
fun SummaryScreen(
    onAddAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SummaryScreen(
        uiState = uiState,
        onAddAlarm = onAddAlarm,
        onOpenSettings = onOpenSettings,
        onEditAlarm = onEditAlarm,
        onSetEnabled = viewModel::setEnabled,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryScreen(
    uiState: SummaryUiState,
    onAddAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onSetEnabled: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Alarms") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Filled.Add, contentDescription = "Add alarm")
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
                Text("No alarms yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                uiState.sections.forEach { section ->
                    item(key = "header-${section.group}") {
                        SectionHeader(section.group)
                    }
                    items(section.entries, key = { it.id }) { entry ->
                        AlarmRow(
                            entry = entry,
                            onClick = { onEditAlarm(entry.id) },
                            onSetEnabled = { enabled -> onSetEnabled(entry.id, enabled) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(group: AlarmGroup) {
    Text(
        text = group.displayLabel(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AlarmRow(
    entry: SummaryEntryUi,
    onClick: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name.ifBlank { "Alarm" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = entry.localTimeLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (entry.enabled && entry.nextRingDateLabel != null && entry.countdownLabel != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(text = entry.nextRingDateLabel, style = MaterialTheme.typography.bodySmall)
                Text(text = entry.countdownLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = entry.enabled, onCheckedChange = onSetEnabled)
    }
}

@Preview(showBackground = true)
@Composable
private fun SummaryScreenEmptyPreview() {
    TzAlarmClockTheme {
        SummaryScreen(
            uiState = SummaryUiState(),
            onAddAlarm = {},
            onOpenSettings = {},
            onEditAlarm = {},
            onSetEnabled = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SummaryScreenPopulatedPreview() {
    TzAlarmClockTheme {
        SummaryScreen(
            uiState = SummaryUiState(
                sections = listOf(
                    SummarySectionUi(
                        group = AlarmGroup.TODAY,
                        entries = listOf(
                            SummaryEntryUi(
                                id = 1,
                                name = "Morning",
                                localTimeLabel = "07:00",
                                enabled = true,
                                nextRingDateLabel = "Jul 30",
                                countdownLabel = "2 h",
                            ),
                        ),
                    ),
                    SummarySectionUi(
                        group = AlarmGroup.LATER,
                        entries = listOf(
                            SummaryEntryUi(
                                id = 2,
                                name = "Disabled",
                                localTimeLabel = "09:00",
                                enabled = false,
                                nextRingDateLabel = null,
                                countdownLabel = null,
                            ),
                        ),
                    ),
                ),
            ),
            onAddAlarm = {},
            onOpenSettings = {},
            onEditAlarm = {},
            onSetEnabled = { _, _ -> },
        )
    }
}
