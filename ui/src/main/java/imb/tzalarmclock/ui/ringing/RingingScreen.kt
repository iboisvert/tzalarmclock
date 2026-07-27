package imb.tzalarmclock.ui.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import imb.tzalarmclock.ui.theme.TzAlarmClockTheme

/**
 * Placeholder for the full-screen ringing UI built in Stage 6: current time/zone/date,
 * alarm name, and the deliberately-asymmetric dismiss/snooze controls.
 */
@Composable
fun RingingScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Alarm ringing",
                style = MaterialTheme.typography.headlineMedium,
            )
            Button(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RingingScreenPreview() {
    TzAlarmClockTheme {
        RingingScreen(onDismiss = {})
    }
}
