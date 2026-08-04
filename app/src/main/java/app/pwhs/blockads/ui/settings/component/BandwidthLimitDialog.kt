package app.pwhs.blockads.ui.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.data.datastore.AppPreferences

/**
 * Global bandwidth throttle picker. Applies the same cap to both
 * download and upload for simplicity — see
 * [app.pwhs.blockads.data.datastore.AppPreferences.setBandwidthLimitKbps]
 * if independent down/up limits are ever needed.
 *
 * NOTE: this limits the WHOLE tunnel (every app combined), not a single
 * app. It only takes effect in the default (non-WireGuard) routing mode.
 */

// KB/s presets shown in the picker. 0 = unlimited.
private val BANDWIDTH_PRESETS_KBPS = listOf(
    AppPreferences.BANDWIDTH_LIMIT_UNLIMITED,
    32,
    64,
    128,
    256
)

private fun formatKbps(kbps: Int): String {
    if (kbps <= AppPreferences.BANDWIDTH_LIMIT_UNLIMITED) return "Sınırsız"
    return "$kbps KB/s"
}

@Composable
fun BandwidthLimitDialog(
    currentKbps: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text("Bant Genişliği Sınırı") },
        text = {
            Column {
                Text(
                    "Tüm uygulamalar için ortak bir hız limiti uygular. VPN'i yeniden başlatır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BANDWIDTH_PRESETS_KBPS.forEach { kbps ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(kbps) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatKbps(kbps),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentKbps == kbps) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
