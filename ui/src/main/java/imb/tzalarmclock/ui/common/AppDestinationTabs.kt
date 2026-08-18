package imb.tzalarmclock.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The three top-level pages [AppDestinationTabs] switches between. */
enum class AppDestination {
    ALARMS,
    TIMERS,
    SETTINGS,
}

/**
 * The Alarms/Timers/Settings switcher shared by [imb.tzalarmclock.ui.summary.SummaryScreen]'s
 * and [imb.tzalarmclock.ui.timers.TimersScreen]'s title bars.
 *
 * Prototype: styled as a row of tab-like buttons — a filled pill for the
 * current page, an outline for the others — rather than plain icon buttons
 * that gave no visual hint which page was showing. Every tab stays tappable,
 * including the current page's own (a no-op re-navigation), same as before.
 */
@Composable
fun AppDestinationTabs(
    current: AppDestination,
    onOpenAlarms: () -> Unit,
    onOpenTimers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DestinationTab(
            icon = Icons.Filled.Alarm,
            contentDescription = "Alarms",
            selected = current == AppDestination.ALARMS,
            onClick = onOpenAlarms,
        )
        DestinationTab(
            icon = Icons.Outlined.Timer,
            contentDescription = "Timers",
            selected = current == AppDestination.TIMERS,
            onClick = onOpenTimers,
        )
        DestinationTab(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            selected = current == AppDestination.SETTINGS,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun DestinationTab(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(25),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            LocalContentColor.current
        },
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(10.dp),
        )
    }
}
