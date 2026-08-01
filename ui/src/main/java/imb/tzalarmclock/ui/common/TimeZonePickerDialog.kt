package imb.tzalarmclock.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.ZoneId

/** Searchable picker over every available [ZoneId]. */
@Composable
internal fun TimeZonePickerDialog(onSelect: (ZoneId) -> Unit, onDismiss: () -> Unit) {
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
