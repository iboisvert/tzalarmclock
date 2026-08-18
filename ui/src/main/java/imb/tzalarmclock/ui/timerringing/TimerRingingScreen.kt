package imb.tzalarmclock.ui.timerringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

/**
 * The page shown when a timer expires — mirrors
 * [imb.tzalarmclock.ui.ringing.RingingScreen]'s role for alarms, but
 * dismiss-only: the spec never mentions snoozing a ringing timer (see the
 * dev plan's assumption log), and a [imb.tzalarmclock.domain.model.Timer]
 * has no name/time/zone/date to show.
 *
 * Unlike the alarm Ringing screen, Dismiss here is a single tap rather than
 * [imb.tzalarmclock.ui.common.HoldToDismissButton]'s hold-to-confirm: a
 * stray tap silencing a timer is a much lower-stakes mistake than silencing
 * an alarm, so the "difficult to accidentally stop" requirement doesn't
 * carry over.
 *
 * If more than one timer is ringing at once (see
 * `imb.tzalarmclock.timer.ringing.TimerRingingService`'s class doc), this
 * still shows a single screen with one Dismiss button — dismissing ends
 * every ringing timer together, not just whichever one launched this screen.
 */
@Composable
fun TimerRingingScreen(
    timerId: Long,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerRingingViewModel = viewModel(),
) {
    LaunchedEffect(timerId) { viewModel.load(timerId) }
    val uiState by viewModel.uiState.collectAsState()
    // Closes the screen once TimerRingingService ends this ring cycle
    // without a tap here having caused it - onDismiss below already calls
    // onFinish() directly for the tap-driven case, so this is a no-op then.
    LaunchedEffect(uiState.stillRinging) {
        if (!uiState.stillRinging) onFinish()
    }
    TimerRingingScreen(
        uiState = uiState,
        onDismiss = {
            viewModel.onDismiss()
            onFinish()
        },
        modifier = modifier,
    )
}

@Composable
private fun TimerRingingScreen(
    uiState: TimerRingingUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // TimerRingingActivity hides the system bars for this
                // screen, same reasoning as RingingScreen's own safety net.
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Time's Up!", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                // Ordinarily just the one label ("5:00 timer"); more than one
                // means more than one timer is ringing at once (see the class
                // doc) — stacked as separate lines rather than crammed into
                // one sentence.
                uiState.configuredDurationLabels.ifEmpty { listOf("") }.forEach { label ->
                    Text(text = "$label timer", fontSize = 20.sp, color = Color.LightGray)
                }
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Text(
                    if (uiState.configuredDurationLabels.size > 1) "Dismiss All" else "Dismiss",
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerRingingScreenPreview() {
    TzAlarmClockTheme {
        TimerRingingScreen(
            uiState = TimerRingingUiState(configuredDurationLabels = listOf("5:00")),
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerRingingScreenMultiplePreview() {
    TzAlarmClockTheme {
        TimerRingingScreen(
            uiState = TimerRingingUiState(configuredDurationLabels = listOf("5:00", "2:00", "0:15")),
            onDismiss = {},
        )
    }
}
