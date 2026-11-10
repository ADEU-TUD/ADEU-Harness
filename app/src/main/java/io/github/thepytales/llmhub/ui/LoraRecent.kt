package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.LoraHistory

/**
 * „Zuletzt“ in Lora's panel (AP-75): the last wishes – a tap puts one into the field to change it, „Nochmal“ sends it
 * as it was, the pin keeps it under a name for Strg+K.
 */
@Composable
internal fun LoraRecent(c: AppContainer, entries: List<LoraHistory.Entry>, onEdit: (String) -> Unit, onAgain: (String) -> Unit) {
    if (entries.isEmpty()) return
    var all by remember { mutableStateOf(false) }
    var pinning by remember { mutableStateOf<LoraHistory.Entry?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tr("Zuletzt"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (if (all) entries else entries.take(5)).forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onEdit(e.text) }.padding(vertical = 6.dp)) {
                    Text(e.name ?: e.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.name != null) HintText("„${e.text}“")
                }
                IconButton(onClick = { onAgain(e.text) }) {
                    Icon(Icons.Outlined.Replay, contentDescription = tr("Nochmal"), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { if (e.pinned) LoraHistory.unpin(c, e.text) else pinning = e }) {
                    Icon(
                        Icons.Outlined.PushPin, contentDescription = if (e.pinned) tr("Lösen") else tr("Anheften"), modifier = Modifier.size(18.dp),
                        tint = if (e.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!all && entries.size > 5) TextButton(onClick = { all = true }) { Text(tr("Alle {0} anzeigen", entries.size)) }
    }
    pinning?.let { e -> PinDialog(e.text, onDismiss = { pinning = null }) { name -> LoraHistory.pin(c, e.text, name); pinning = null } }
}

/** Name for a pinned command: it appears in Strg+K under this name. */
@Composable
internal fun PinDialog(text: String, onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var name by remember { mutableStateOf(text.take(40)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Befehl anheften")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText(tr("Unter diesem Namen steht „{0}“ in Strg+K.", text))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(tr("Name")) })
            }
        },
        confirmButton = { TextButton(onClick = { onPin(name) }, enabled = name.isNotBlank()) { Text(tr("Anheften")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
