package io.github.thepytales.llmhub.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.core.content.FileProvider
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocumentWriter
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
            }
        }
    }
}

/** Converts the visible chat (without reasoning and tool output) into Markdown. */
fun chatToMarkdown(conv: Conversation): String = buildString {
    for (m in conv.messages) {
        when (m.role) {
            "user" -> {
                append("## Du\n\n")
                if (m.content.isNotBlank()) append(m.content.trim()).append("\n\n")
                if (m.images.isNotEmpty()) append("_(${m.images.size} Bild(er) angehängt)_\n\n")
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
@Composable
fun ExportDialog(title: String, markdown: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(DocFormat.PDF) }
    var busy by remember { mutableStateOf(false) }
    val created = remember { mutableStateMapOf<DocFormat, File>() }

    fun withFile(action: (File) -> Unit) {
        val f = format
        created[f]?.let { return action(it) }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocumentWriter.create(File(context.cacheDir, "exports"), title, markdown, f).file }
            }
            busy = false
            result.onSuccess {
                created[f] = it
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
