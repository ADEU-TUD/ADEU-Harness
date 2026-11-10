package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.FileItem
import io.github.thepytales.llmhub.docs.FormatConvert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Formatwandler mit Verlustanzeige (AP-90): pick a target, see before converting what goes missing or changes – with a
 * better target where there is one –, then „Umwandeln“ (or „Trotzdem umwandeln“). The new file goes next to the original.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatConvertDialog(c: AppContainer, item: FileItem, onDismiss: () -> Unit, onDone: (File) -> Unit = {}) {
    val path = item.path ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targets = remember(item.name) { FormatConvert.targets(item.name) }
    var target by remember { mutableStateOf(targets.first()) }
    var busy by remember { mutableStateOf(false) }
    val bytes by produceState<ByteArray?>(null, path) { value = withContext(Dispatchers.IO) { runCatching { File(path).readBytes() }.getOrNull() } }
    val plan = remember(bytes, target) { bytes?.let { FormatConvert.analyze(item.name, it, target) } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("„{0}“ umwandeln", item.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.forEach { t -> FilterChip(selected = t == target, onClick = { target = t }, label = { Text(tr(FormatConvert.LABELS[t] ?: t.uppercase())) }) }
                }
                when {
                    plan == null -> CircularProgressIndicator(Modifier.size(20.dp))
                    plan.losses.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text(tr("Dabei geht nichts verloren."), style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> {
                        Text(tr("Das geht verloren oder ändert sich:"), style = MaterialTheme.typography.titleSmall)
                        plan.losses.forEach { l ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (l.severe) Icons.Outlined.WarningAmber else Icons.Outlined.Info, null, Modifier.size(18.dp),
                                    tint = if (l.severe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column {
                                    Text(l.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(l.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        plan.better?.let { b ->
                            TextButton(onClick = { target = b }) { Text(tr("Besser: {0} – {1}", tr(FormatConvert.LABELS[b] ?: b), plan.betterWhy.orEmpty())) }
                        }
                    }
                }
                SettingsHint(tr("Das Original bleibt erhalten; die neue Datei kommt in denselben Ordner."))
            }
        },
        confirmButton = {
            Button(enabled = !busy && plan != null, onClick = {
                busy = true
                scope.launch {
                    runCatching { FormatConvert.convert(context, File(path), item.name, target, File(path).parentFile ?: File(path)) }
                        .onSuccess { f ->
                            onDone(f)
                            Notices.info(tr("Umgewandelt: „{0}“", f.name))
                            onDismiss()
                        }
                        .onFailure { Notices.error(it.message ?: tr("Das hat nicht geklappt")) }
                    busy = false
                }
            }) { Text(if (plan?.losses?.any { it.severe } == true) tr("Trotzdem umwandeln") else tr("Umwandeln")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
