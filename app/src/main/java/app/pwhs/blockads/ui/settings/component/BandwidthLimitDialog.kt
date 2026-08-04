package app.pwhs.blockads.ui.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.data.datastore.AppPreferences

/**
 * Global bandwidth throttle input. Download and upload are set
 * independently, in KB/s, via the number keyboard. Leaving a field
 * empty or entering 0 means "unlimited" for that direction — see
 * [app.pwhs.blockads.data.datastore.AppPreferences.setBandwidthLimitKbps].
 *
 * NOTE: this limits the WHOLE tunnel (every app combined), not a single
 * app. It only takes effect in the default (non-WireGuard) routing mode.
 */
@Composable
fun BandwidthLimitDialog(
    currentDownKbps: Int,
    currentUpKbps: Int,
    onApply: (downKbps: Int, upKbps: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Empty string shown for "unlimited" (0) so the field starts blank
    // instead of showing a confusing "0".
    var downText by remember {
        mutableStateOf(
            if (currentDownKbps <= AppPreferences.BANDWIDTH_LIMIT_UNLIMITED) "" else currentDownKbps.toString()
        )
    }
    var upText by remember {
        mutableStateOf(
            if (currentUpKbps <= AppPreferences.BANDWIDTH_LIMIT_UNLIMITED) "" else currentUpKbps.toString()
        )
    }

    // Only digits allowed, capped at 6 characters (up to 999999 KB/s)
    // so a stray long paste can't produce a nonsensical value.
    fun sanitize(input: String): String = input.filter { it.isDigit() }.take(6)

    AlertDialog(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text("Bant Genişliği Sınırı") },
        text = {
            Column {
                Text(
                    "İndirme ve yükleme için ayrı ayrı KB/s sınırı gir. Boş bırak ya da 0 yaz = sınırsız. VPN'i yeniden başlatır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = downText,
                        onValueChange = { downText = sanitize(it) },
                        label = { Text("İndirme (KB/s)") },
                        placeholder = { Text("Sınırsız") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = upText,
                        onValueChange = { upText = sanitize(it) },
                        label = { Text("Yükleme (KB/s)") },
                        placeholder = { Text("Sınırsız") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val down = downText.toIntOrNull() ?: AppPreferences.BANDWIDTH_LIMIT_UNLIMITED
                val up = upText.toIntOrNull() ?: AppPreferences.BANDWIDTH_LIMIT_UNLIMITED
                onApply(down, up)
            }) {
                Text("Uygula")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
