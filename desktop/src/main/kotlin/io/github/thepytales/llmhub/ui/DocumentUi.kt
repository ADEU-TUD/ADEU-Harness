package io.github.thepytales.llmhub.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.desktop.DesktopActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Opens [file] with the program Windows uses for it (PDF viewer, Word …). */
fun openDocument(context: Context, file: File) {
    if (!DesktopActions.open(file)) {
        Toast.makeText(context, "Kein Programm zum Öffnen von ${file.extension.uppercase()}-Dateien gefunden.", Toast.LENGTH_LONG).show()
    }
}

/** Shows the file in Explorer – from there it can be dragged into an e-mail, Teams … */
fun shareDocument(context: Context, file: File) {
    if (!DesktopActions.showInFolder(file)) Toast.makeText(context, file.absolutePath, Toast.LENGTH_LONG).show()
}

/** "Speichern unter": copies [file] to a place the user picks (Documents\ADEU LLM by default). */
private suspend fun saveAs(context: Context, file: File) {
    val target = withContext(Dispatchers.IO) { DesktopActions.pickSaveFile("Speichern unter", file.name, file.extension) } ?: return
    val ok = withContext(Dispatchers.IO) { runCatching { file.copyTo(target, overwrite = true) }.isSuccess }
    Toast.makeText(context, if (ok) "Gespeichert: ${target.absolutePath}" else "Speichern fehlgeschlagen.", Toast.LENGTH_LONG).show()
}

private fun formatSize(bytes: Long) = if (bytes < 1024 * 1024) "${(bytes + 1023) / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)

/** Card for a document created by the model, with open/share/save actions. */
@Composable
fun DocumentCard(path: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }
    val format = DocFormat.ofFile(file)
    val exists = remember(path) { file.exists() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = exists) { openDocument(context, file) },
            ) {
                Icon(
                    if (format == DocFormat.PDF) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (exists) "${format.label} · ${formatSize(file.length())}" else "Datei nicht mehr vorhanden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (exists) {
                Row {
                    TextButton(onClick = { openDocument(context, file) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Öffnen")
                    }
                    TextButton(onClick = { shareDocument(context, file) }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Im Ordner zeigen")
                    }
                    TextButton(onClick = { scope.launch { saveAs(context, file) } }) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Speichern")
                    }
                }
            }
        }
    }
}

/** Converts the visible chat (without reasoning and tool output) into Markdown. */
fun chatToMarkdown(conv: Conversation): String = buildString {
    for (m in conv.thread()) {
        when (m.role) {
            "user" -> {
                append("## Du\n\n")
                if (m.content.isNotBlank()) append(m.content.trim()).append("\n\n")
                if (m.images.isNotEmpty()) append("_(${m.images.size} Bild(er) angehängt)_\n\n")
                if (m.files.isNotEmpty()) append("_(Dateien: ${m.files.joinToString(", ") { it.name }})_\n\n")
            }
            "assistant" -> {
                val answer = splitThinking(m.content).second.trim()
                if (m.error || answer.isEmpty()) continue
                append("## ${ModelCatalog.info(m.model ?: conv.model).displayName}\n\n").append(answer).append("\n\n")
            }
        }
    }
}

/** Lets the user export [markdown] as PDF, Word, HTML, Markdown or text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(c: AppContainer, title: String, markdown: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(DocFormat.PDF) }
    var style by remember { mutableStateOf(DocStyle.parse(c.settings.current.docStyle) ?: DocStyle.MODERN) }
    var busy by remember { mutableStateOf(false) }
    val created = remember { mutableStateMapOf<Pair<DocFormat, DocStyle>, File>() }

    fun withFile(action: (File) -> Unit) {
        val key = format to style
        created[key]?.let { return action(it) }
        busy = true
        scope.launch {
            val result = runCatching {
                DocumentWriter.create(
                    context, File(context.cacheDir, "exports"), title, markdown, key.first,
                    theme = c.documentTools.settingsTheme(key.second),
                ).file
            }
            busy = false
            result.onSuccess {
                created[key] = it
                action(it)
            }.onFailure {
                Toast.makeText(context, "Export fehlgeschlagen: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Als Dokument exportieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DocFormat.entries.forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { format = f },
                    ) {
                        RadioButton(selected = format == f, onClick = { format = f })
                        Text(f.label)
                    }
                }
                if (format == DocFormat.PDF || format == DocFormat.DOCX || format == DocFormat.HTML) {
                    Text("Gestaltung", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DocStyle.entries.forEach { st ->
                            FilterChip(selected = style == st, onClick = { style = st }, label = { Text(st.label) })
                        }
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = !busy, onClick = { withFile { openDocument(context, it) } }) { Text("Öffnen") }
                TextButton(enabled = !busy, onClick = { withFile { scope.launch { saveAs(context, it) } } }) { Text("Speichern unter …") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
