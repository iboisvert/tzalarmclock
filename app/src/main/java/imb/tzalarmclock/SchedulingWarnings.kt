package imb.tzalarmclock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import imb.tzalarmclock.alarm.permission.SchedulingHealth

/**
 * Tells the user when the OS is going to stop their alarms from working.
 *
 * An alarm clock that silently doesn't ring is worse than one that refuses to
 * pretend, so each of these is stated plainly with the one action that fixes
 * it. Severity is split deliberately: the first two mean alarms will not reach
 * the user at all and use the error colours, while battery optimisation is
 * advisory — `setAlarmClock` fires through Doze regardless — and is toned down
 * so it doesn't cry wolf.
 */
@Composable
fun SchedulingWarnings(
    health: SchedulingHealth,
    onFixExactAlarms: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (health.allClear) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (!health.exactAlarmsAllowed) {
            Warning(
                text = stringResource(R.string.warning_exact_alarms),
                actionLabel = stringResource(R.string.warning_action_grant),
                onAction = onFixExactAlarms,
                critical = true,
            )
        }
        if (!health.notificationsAllowed) {
            Warning(
                text = stringResource(R.string.warning_notifications),
                actionLabel = stringResource(R.string.warning_action_allow),
                onAction = onFixNotifications,
                critical = true,
            )
        }
        if (health.batteryOptimized) {
            Warning(
                text = stringResource(R.string.warning_battery),
                actionLabel = stringResource(R.string.warning_action_allow),
                onAction = onFixBattery,
                critical = false,
            )
        }
    }
}

@Composable
private fun Warning(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    critical: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (critical) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (critical) colors.onErrorContainer else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onAction,
                // Inherit the Surface's content colour rather than taking the
                // theme's primary, which has no guaranteed contrast against an
                // error container.
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalContentColor.current,
                ),
            ) {
                Text(actionLabel)
            }
        }
    }
}
