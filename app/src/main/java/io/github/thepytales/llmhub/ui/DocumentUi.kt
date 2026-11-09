package io.github.thepytales.llmhub.ui

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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch
import java.io.File

/** Readable kind of a file in the document card. */
fun fileKindLabel(file: File, format: DocFormat) = when (file.extension.lowercase()) {
    "eml" -> "E-Mail-Entwurf"
    "ics" -> "Kalendertermin"
    else -> format.label
}

private fun formatSize(bytes: Long) = if (bytes < 1024 * 1024) "${(bytes + 1023) / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)

/** Card for a document created by the model, with open/share/save actions. */
@Composable
fun DocumentCard(path: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }
    val format = DocFormat.ofFile(file)
    val saveAs = rememberSaveAs(format.mime)
    // Nextcloud (AP-22): results straight into the cloud, once it is set up.
    val toNextcloud = rememberNextcloudSave(io.github.thepytales.llmhub.App.container)
    /** Raised when open placeholders were filled in and the file was rendered again. */
    var version by remember(path) { mutableIntStateOf(0) }
    val exists = remember(path, version) { file.exists() }
    var accessibility by remember(path) { mutableStateOf(false) }
    if (accessibility) AccessibilityDialog(io.github.thepytales.llmhub.App.container, file, onDismiss = { accessibility = false })
    // Same card as attachments and links in the chat (AP-01): fine frame, accent bar, icon on a light tint.
    AccentBox {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = exists) { openDocument(context, file) },
            ) {
                IconTile(if (format == DocFormat.PDF) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description, size = 38.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (exists) "${fileKindLabel(file, format)} · ${formatSize(remember(path, version) { file.length() })}" else tr("Datei nicht mehr vorhanden"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (exists && file.extension.lowercase() in setOf("pdf", "docx", "pptx", "html")) {
                    IconButton(onClick = { accessibility = true }) {
                        Icon(Icons.Outlined.AccessibilityNew, contentDescription = tr("Barrierefreiheit prüfen"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (exists) {
                Row {
                    TextButton(onClick = { openPrimary(context, file) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(primaryLabel(file))
                    }
                    // A PDF (e.g. made fillable by the agent) opens in the PDF-Werkzeugkasten when it is switched on (AP-11).
                    if (file.extension.equals("pdf", ignoreCase = true) && io.github.thepytales.llmhub.App.container.settings.current.toolOn("pdf")) {
                        TextButton(onClick = { PdfHandoff.open(file.absolutePath, 1, file.name) }) { Text(tr("Werkzeugkasten")) }
                    }
                    TextButton(onClick = { shareDocument(context, file) }) {
                        Icon(ShareDoc.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(ShareDoc.label)
                    }
                    TextButton(onClick = { saveAs(file) }) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(SAVE_AS_LABEL)
                    }
                }
                if (toNextcloud != null) TextButton(onClick = { toNextcloud(file) }) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(tr("In Nextcloud speichern"))
                }
                // Markdown documents can be edited and commented in the document editor.
                val docs by io.github.thepytales.llmhub.App.container.docs.items.collectAsState()
                val live = docs.firstOrNull { it.file == path }
                if (live != null) {
                    TextButton(onClick = { DocsHandoff.show(live.id) }) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(tr("Im Editor bearbeiten") + if (live.openComments > 0) tr(" · {0} Kommentare", live.openComments) else "")
                    }
                } else if (io.github.thepytales.llmhub.docs.OfficeImport.canOpen(file.name)) {
                    // Word, PowerPoint, Excel, Markdown … from elsewhere: open in the editor to keep working with the model.
                    TextButton(onClick = {
                        scope.launch {
                            val doc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { DocsHandoff.importFile(io.github.thepytales.llmhub.App.container, file) }
                            if (doc != null) DocsHandoff.show(doc.id) else Notices.error(tr("„{0}“ ließ sich nicht im Editor öffnen.", file.name))
                        }
                    }) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(tr("Im Editor öffnen"))
                    }
                }
                DocumentGaps(file, version) { version++ }
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
                if (m.images.isNotEmpty()) append(tr("_({0} Bild(er) angehängt)_", m.images.size)).append("\n\n")
                if (m.files.isNotEmpty()) append(tr("_(Dateien: {0})_", m.files.joinToString(", ") { it.name })).append("\n\n")
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
fun ExportDialog(
    c: AppContainer,
    title: String,
    markdown: String,
    onDismiss: () -> Unit,
    onCreated: (File) -> Unit = {},
    initial: DocFormat = DocFormat.PDF,
    /** Writes some formats itself (tables of the editor with their formulas); null = the usual way. */
    custom: ((DocFormat, File) -> Boolean)? = null,
    /** Look the document itself asks for (e.g. TU Dresden); null = the one from the settings. */
    initialStyle: DocStyle? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(initial) }
    var style by remember { mutableStateOf(initialStyle ?: DocStyle.parse(c.settings.current.docStyle) ?: DocStyle.MODERN) }
    var busy by remember { mutableStateOf(false) }
    val created = remember { mutableStateMapOf<Pair<DocFormat, DocStyle>, File>() }

    fun withFile(action: (File) -> Unit) {
        val key = format to style
        created[key]?.let { return action(it) }
        busy = true
        scope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val own = custom?.let { write ->
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        File(dir, DocumentWriter.safeName(title) + "." + key.first.ext).takeIf { write(key.first, it) }
                    }
                    own ?: DocumentWriter.create(
                        context, File(context.cacheDir, "exports"), title, markdown, key.first,
                        theme = c.documentTools.settingsTheme(key.second),
                    ).file
                }
            }
            busy = false
            result.onSuccess {
                created[key] = it
                onCreated(it)
                action(it)
            }.onFailure {
                Notices.error(tr("Export fehlgeschlagen: {0}", it.message))
            }
        }
    }

    val saveAs = rememberSaveAs(format.mime)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Als Dokument exportieren")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DocFormat.entries.filter { it.chatExport }.forEach { f ->
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
                    Text(tr("Gestaltung"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
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
                TextButton(enabled = !busy, onClick = { withFile { openDocument(context, it) } }) { Text(tr("Öffnen")) }
                if (ShareDoc.inExport) TextButton(enabled = !busy, onClick = { withFile { shareDocument(context, it) } }) { Text(ShareDoc.label) }
                TextButton(enabled = !busy, onClick = { withFile(saveAs) }) { Text(SAVE_AS_LABEL) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}
