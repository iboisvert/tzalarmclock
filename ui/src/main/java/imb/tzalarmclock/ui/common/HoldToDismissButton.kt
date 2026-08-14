package imb.tzalarmclock.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Requires holding for [HOLD_DURATION_MILLIS] to confirm, with a progress
 * ring filling as visual feedback, rather than a single tap — the spec's
 * "difficult to accidentally stop" requirement for both Ringing pages'
 * (alarm and timer) least-undoable control.
 *
 * Shared by `imb.tzalarmclock.ui.ringing.RingingScreen` and
 * `imb.tzalarmclock.ui.timerringing.TimerRingingScreen` — unlike the
 * `alarm`/`timer` Android modules, the UI layer already has a `ui.common`
 * package for exactly this kind of cross-screen reuse (see
 * `EditableRow`/`RingtonePickerRow`), so this one isn't duplicated per
 * screen the way the module-level scheduler/receiver shape is.
 */
@Composable
internal fun HoldToDismissButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Hold to Dismiss",
    contentDescription: String = "Dismiss",
) {
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
            onLongClick(label = contentDescription) {
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
            Text(label, fontSize = 20.sp)
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
