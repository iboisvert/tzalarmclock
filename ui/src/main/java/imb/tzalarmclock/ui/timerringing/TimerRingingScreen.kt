package imb.tzalarmclock.ui.timerringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import imb.tzalarmclock.ui.common.HoldToDismissButton
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

/**
 * The page shown when a timer expires — mirrors
 * [imb.tzalarmclock.ui.ringing.RingingScreen]'s role for alarms, but
 * dismiss-only: the spec never mentions snoozing a ringing timer (see the
 * dev plan's assumption log), and a [imb.tzalarmclock.domain.model.Timer]
 * has no name/time/zone/date to show.
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
                Text(
                    text = "${uiState.configuredDurationLabel} timer",
                    fontSize = 20.sp,
                    color = Color.LightGray,
                )
            }
            HoldToDismissButton(
                onDismiss = onDismiss,
                contentDescription = "Dismiss timer",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimerRingingScreenPreview() {
    TzAlarmClockTheme {
        TimerRingingScreen(
            uiState = TimerRingingUiState(configuredDurationLabel = "5:00"),
            onDismiss = {},
        )
    }
}
