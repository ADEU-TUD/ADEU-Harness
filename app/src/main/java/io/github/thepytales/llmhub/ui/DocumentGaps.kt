package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Open placeholders like [Datum] of a generated document, with "Ausfüllen": the user types the missing details
 * and the document is rendered again in place – no model involved. Shown inside the document card.
 */
@Composable
fun DocumentGaps(file: File, version: Int, onUpdated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gaps by produceState(emptyList<String>(), file, version) {
        value = withContext(Dispatchers.IO) { runCatching { App.container.documentTools.openPlaceholders(file) }.getOrDefault(emptyList()) }
    }
    var open by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    if (gaps.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Text(
            "${if (gaps.size == 1) "1 Angabe" else "${gaps.size} Angaben"} offen: ${gaps.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (working) CircularProgressIndicator(Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
        else TextButton(onClick = { open = true }) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Ausfüllen")
        }
    }
    if (open) {
        val values = remember(gaps) { mutableStateMapOf<String, String>() }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Angaben ausfüllen") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Was Sie eintragen, ersetzt die Lücke im Dokument. Leere Felder bleiben offen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    gaps.forEach { g ->
                        OutlinedTextField(
                            value = values[g].orEmpty(),
                            onValueChange = { values[g] = it },
                            label = { Text(g) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = values.values.any { it.isNotBlank() }, onClick = {
                    open = false
                    working = true
                    val entered = values.toMap()
                    scope.launch {
                        try {
                            App.container.documentTools.fillPlaceholders(file, entered)
                            onUpdated()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Notices.error("Ausfüllen fehlgeschlagen: ${e.message}")
                        } finally {
                            working = false
                        }
                    }
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } },
        )
    }
}
