package imb.tzalarmclock.ui.common

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * A [EditableRow] wired to the system ringtone picker.
 *
 * @param ringtoneUri this field's own value, or `null` if unset.
 * @param fallbackUri what `null` resolves to for display/pre-selection
 *   purposes — another level of fallback (e.g. the app setting, for a
 *   per-alarm override), or `null` if this *is* the last fallback, in which
 *   case the system's default alarm sound is used.
 */
@Composable
internal fun RingtonePickerRow(
    label: String,
    ringtoneUri: String?,
    fallbackUri: String?,
    onRingtoneChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        onRingtoneChanged(uri?.toString())
    }
    val effectiveUri = ringtoneUri ?: fallbackUri
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
    val title = remember(effectiveUri) {
        effectiveUri?.let {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull()
        } ?: "Unknown"
    }
    EditableRow(
        label = label,
        value = title,
        onEdit = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, effectiveUri?.let(Uri::parse))
            }
            launcher.launch(intent)
        },
        onClear = if (ringtoneUri != null) ({ onRingtoneChanged(null) }) else null,
    )
}
