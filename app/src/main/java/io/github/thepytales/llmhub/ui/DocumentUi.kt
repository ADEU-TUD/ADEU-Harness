package io.github.thepytales.llmhub.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Share
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
import androidx.core.content.FileProvider
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.docs.CalendarFiles
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.MailFiles
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.data.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)

fun openDocument(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(context, file), DocFormat.ofFile(file).mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine App zum Öffnen von ${file.extension.uppercase()}-Dateien gefunden.", Toast.LENGTH_LONG).show()
    }
}

/** Opens the mail app with the draft filled in (the user sends it there). */
fun openMailDraft(context: Context, file: File) {
    val d = MailFiles.read(file) ?: return Toast.makeText(context, "Entwurf nicht lesbar.", Toast.LENGTH_SHORT).show()
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, d.to.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
        .putExtra(Intent.EXTRA_CC, d.cc.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
        .putExtra(Intent.EXTRA_SUBJECT, d.subject)
        .putExtra(Intent.EXTRA_TEXT, d.body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Keine Mail-App gefunden.", Toast.LENGTH_LONG).show()
    }
}

/** Opens the calendar app with the event filled in. */
fun addToCalendar(context: Context, file: File) {
    val e = CalendarFiles.read(file) ?: return Toast.makeText(context, "Termin nicht lesbar.", Toast.LENGTH_SHORT).show()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, CalendarFiles.millis(e.start))
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, CalendarFiles.millis(if (e.allDay) e.end.plusDays(if (e.end == e.start) 1 else 0) else e.end))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
        .putExtra(CalendarContract.Events.TITLE, e.title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.location)
        .putExtra(CalendarContract.Events.DESCRIPTION, e.description)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (ex: ActivityNotFoundException) {
        openDocument(context, file)
    }
}

/** Readable kind of a file in the document card. */
fun fileKindLabel(file: File, format: DocFormat) = when (file.extension.lowercase()) {
    "eml" -> "E-Mail-Entwurf"
    "ics" -> "Kalendertermin"
    else -> format.label
}

fun shareDocument(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(DocFormat.ofFile(file).mime)
        .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun copyTo(context: Context, file: File, target: Uri) {
    val ok = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(target)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        }.isSuccess
    }
    Toast.makeText(context, if (ok) "Gespeichert: ${file.name}" else "Speichern fehlgeschlagen.", Toast.LENGTH_SHORT).show()
}

/** Remembers a "save as" launcher that copies a file to a location the user picks. */
@Composable
private fun rememberSaveLauncher(mime: String, onPicked: (Uri) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> if (uri != null) onPicked(uri) }

private fun formatSize(bytes: Long) = if (bytes < 1024 * 1024) "${(bytes + 1023) / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)

/** Card for a document created by the model, with open/share/save actions. */
@Composable
fun DocumentCard(path: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }
    val format = DocFormat.ofFile(file)
    val save = rememberSaveLauncher(format.mime) { uri -> scope.launch { copyTo(context, file, uri) } }
    /** Raised when open placeholders were filled in and the file was rendered again. */
    var version by remember(path) { mutableIntStateOf(0) }
    val exists = remember(path, version) { file.exists() }
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
                        if (exists) "${fileKindLabel(file, format)} · ${formatSize(remember(path, version) { file.length() })}" else "Datei nicht mehr vorhanden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (exists) {
                Row {
                    val ext = file.extension.lowercase()
                    TextButton(onClick = {
                        when (ext) {
                            "eml" -> openMailDraft(context, file)
                            "ics" -> addToCalendar(context, file)
                            else -> openDocument(context, file)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(when (ext) { "eml" -> "In Mail-App"; "ics" -> "Zum Kalender"; else -> "Öffnen" })
                    }
                    TextButton(onClick = { shareDocument(context, file) }) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Teilen")
                    }
                    TextButton(onClick = { save.launch(file.name) }) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Speichern")
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

    var pendingSave by remember { mutableStateOf<File?>(null) }
    val save = rememberSaveLauncher(format.mime) { uri ->
        pendingSave?.let { f -> scope.launch { copyTo(context, f, uri) } }
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
                TextButton(enabled = !busy, onClick = { withFile { shareDocument(context, it) } }) { Text("Teilen") }
                TextButton(enabled = !busy, onClick = {
                    withFile {
                        pendingSave = it
                        save.launch(it.name)
                    }
                }) { Text("Speichern") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
