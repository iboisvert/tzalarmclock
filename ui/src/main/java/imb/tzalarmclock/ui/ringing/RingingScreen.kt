package imb.tzalarmclock.ui.ringing

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme
import kotlinx.coroutines.delay

/**
 * The highest-stakes screen in the app: legible in low light, and resistant
 * to fat-fingering "stop" when the user meant "snooze" — Dismiss requires a
 * deliberate hold (see [HoldToDismissButton]) while Snooze is a single tap,
 * inverted from the usual convention on purpose, per the spec's explicit
 * priority.
 */
@Composable
fun RingingScreen(
    alarmId: Long,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RingingViewModel = viewModel(),
) {
    LaunchedEffect(alarmId) { viewModel.load(alarmId) }
    val uiState by viewModel.uiState.collectAsState()
    // Closes the screen when RingingService ends this ring cycle without a
    // tap here having caused it — an unacknowledged-ring timeout, most
    // notably. onSnooze/onDismiss below already call onFinish() directly for
    // the tap-driven cases; this is a no-op then, since finishing an already-
    // finishing Activity is safe.
    LaunchedEffect(uiState.stillRinging) {
        if (!uiState.stillRinging) onFinish()
    }
    RingingScreen(
        uiState = uiState,
        onSnooze = {
            viewModel.onSnooze()
            onFinish()
        },
        onDismiss = {
            viewModel.onDismiss()
            onFinish()
        },
        modifier = modifier,
    )
}

@Composable
private fun RingingScreen(
    uiState: RingingUiState,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // RingingActivity hides the system bars for this screen, so
                // this is normally a no-op — a safety net for the moment a
                // swipe-in transiently reveals them (or, on an OEM that
                // doesn't honour immersive mode at all, the only thing
                // keeping the buttons below out from under the nav bar).
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(1.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = uiState.timeLabel, fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = uiState.dateLabel, fontSize = 20.sp, color = Color.White)
                Text(text = uiState.zoneLabel, fontSize = 16.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = uiState.alarmName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.canSnooze) {
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Text("Snooze (${uiState.snoozesRemaining} left)", fontSize = 20.sp)
                    }
                }
                HoldToDismissButton(
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                )
            }
        }
    }
}

/**
 * Requires holding for [HOLD_DURATION_MILLIS] to confirm, with a progress
 * ring filling as visual feedback, rather than a single tap — the spec's
 * "difficult to accidentally stop" requirement for the one control on this
 * screen that can't be undone.
 */
@Composable
private fun HoldToDismissButton(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPressed) {
        if (!isPressed) {
            progress = 0f
            return@LaunchedEffect
        }
        val stepMillis = 16L
        while (progress < 1f) {
            delay(stepMillis)
            progress = (progress + stepMillis.toFloat() / HOLD_DURATION_MILLIS).coerceAtMost(1f)
        }
        onDismiss()
    }

    Box(
        modifier = modifier.semantics {
            // The visual/touch interaction is a real-time hold, which an
            // accessibility service's synthesized click can't perform — its
            // long-click action (TalkBack's double-tap-and-hold, or a Switch
            // Access long-press action) dismisses immediately instead, as the
            // discoverable accessible equivalent.
            onLongClick(label = "Dismiss alarm") {
                onDismiss()
                true
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = {},
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxSize(),
        ) {
            Text("Hold to Dismiss", fontSize = 20.sp)
        }
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                color = Color.White,
                strokeWidth = 4.dp,
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

private const val HOLD_DURATION_MILLIS = 1_500L

@Preview(showBackground = true)
@Composable
private fun RingingScreenPreview() {
    TzAlarmClockTheme {
        RingingScreen(
            uiState = RingingUiState(
                alarmName = "Morning",
                timeLabel = "07:00",
                dateLabel = "Monday, Jan 5",
                zoneLabel = "America/Toronto",
                canSnooze = true,
                snoozesRemaining = 3,
            ),
            onSnooze = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RingingScreenMaxSnoozePreview() {
    TzAlarmClockTheme {
        RingingScreen(
            uiState = RingingUiState(
                alarmName = "Morning",
                timeLabel = "07:10",
                dateLabel = "Monday, Jan 5",
                zoneLabel = "America/Toronto",
                canSnooze = false,
                snoozesRemaining = 0,
            ),
            onSnooze = {},
            onDismiss = {},
        )
    }
}
