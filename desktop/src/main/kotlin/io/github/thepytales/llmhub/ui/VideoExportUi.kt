package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.net.WebDavClient
import io.github.thepytales.llmhub.video.Encoders
import io.github.thepytales.llmhub.video.ExportQueue
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Connects the queue with the app: Nextcloud uploads and a notice with „Öffnen“ when an entry is done. */
internal fun wireExportQueue(c: AppContainer) {
    if (ExportQueue.uploader == null) ExportQueue.uploader = { f, folder -> c.nextcloud.saveLarge(f, folder) }
    if (ExportQueue.onFinished == null) ExportQueue.onFinished = { e ->
        when (e.status) {
            ExportQueue.Status.DONE -> e.files.firstOrNull()?.let { f ->
                runCatching { c.drive.changed() }
                Notices.show(Notices.Notice(tr("Export fertig:") + " " + f.name + (e.note?.let { " – $it" } ?: ""), action = tr("Öffnen"), onAction = { DesktopActions.open(f) }))
            }
            ExportQueue.Status.FAILED -> Notices.error(tr("Export fehlgeschlagen:") + " " + e.out.name + "\n" + (e.error ?: "").take(300))
            else -> {}
        }
    }
}

/**
 * Exportieren (AP-25, AP-57): one or more targets at once (OPAL, YouTube, as the project, only the sound, subtitle files,
 * chapters for YouTube), into the Lora-Ordner („Erstellt“), a folder of one's own or also to Nextcloud; the graphics
 * card encodes where there is one. The exports go into the queue and run in the background.
 */
@Composable
internal fun ExportDialog(c: AppContainer, p: VideoProject, onDismiss: () -> Unit) {
    val prefs = remember { c.appContext.getSharedPreferences("ui_state", 0) }
    val remembered = remember { io.github.thepytales.llmhub.agent.LoraPrefs.default(c, "video.export", "vorgabe")?.let { v -> VideoRender.PRESETS.firstOrNull { it.id == v }?.id } }
    var presets by remember { mutableStateOf(setOf(remembered ?: VideoRender.PRESETS.first().id)) }
    var audio by remember { mutableStateOf(false) }
    var subs by remember { mutableStateOf(p.subtitles.isNotEmpty() && !p.burnSubtitles) }
    val chapters = remember(p) { VideoEdit.chapters(p) }
    var chapterFile by remember { mutableStateOf(chapters.isNotEmpty()) }
    var target by remember { mutableStateOf(prefs.getString("videoExportTarget", "lora") ?: "lora") }
    var folder by remember { mutableStateOf(prefs.getString("videoExportFolder", null)?.let(::File)) }
    var hardware by remember { mutableStateOf(prefs.getBoolean("videoHardware", true)) }
    val gpus by produceState<List<Encoders.Encoder>?>(null) { value = Encoders.available() }
    val scope = rememberCoroutineScope()
    val nextcloud = c.nextcloud.configured
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Video exportieren")) },
        text = {
            Column(Modifier.width(560.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("Was?"), style = MaterialTheme.typography.labelLarge)
                VideoRender.PRESETS.forEach { pr ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { presets = if (pr.id in presets) presets - pr.id else presets + pr.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pr.id in presets, { on -> presets = if (on) presets + pr.id else presets - pr.id })
                        Column {
                            Text(tr(pr.label) + " · " + VideoRender.size(p, pr).let { (w, hh) -> "$w × $hh" })
                            HintText(tr(pr.hint))
                        }
                    }
                }
                CheckRow(tr("Nur Ton (M4A) – z. B. als Podcast"), audio) { audio = it }
                if (p.subtitles.isNotEmpty()) CheckRow(tr("Untertitel als SRT und VTT"), subs) { subs = it }
                if (chapters.isNotEmpty()) CheckRow(tr("Kapitel für die YouTube-Beschreibung ({0} Kapitel, im MP4 sind sie immer)", chapters.size), chapterFile) { chapterFile = it }
                else HintText(tr("Marker mit Häkchen „Kapitel“ werden zu Kapiteln im MP4 und für YouTube."))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(tr("Wohin?"), style = MaterialTheme.typography.labelLarge)
                RadioRow(tr("Lora-Ordner › Erstellt"), target == "lora") { target = "lora" }
                RadioRow(tr("Eigener Ordner") + (folder?.let { ": " + it.absolutePath } ?: " …"), target == "folder") {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { DesktopActions.pickFolder(tr("Ordner für die Exporte")) }
                        if (f != null) { folder = f; target = "folder" }
                    }
                }
                if (nextcloud) RadioRow(tr("Lora-Ordner und Nextcloud ({0})", WebDavClient.join(c.nextcloud.resultFolder, "Videos")), target == "nextcloud") { target = "nextcloud" }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Memory, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    val g = gpus
                    Text(
                        when {
                            g == null -> tr("Suche die Grafikkarte …")
                            g.isEmpty() -> tr("Keine Grafikkarte zum Rechnen gefunden – der Prozessor rechnet (x264).")
                            else -> tr("Grafikkarte: {0} – deutlich schneller als der Prozessor.", g.first().label)
                        },
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    if (!g.isNullOrEmpty()) Switch(hardware, { hardware = it })
                }
                HintText(tr("Die Exporte laufen nacheinander im Hintergrund – du kannst weiterarbeiten. Länge {0}.", clock(p.duration)))
            }
        },
        confirmButton = {
            val kinds = buildSet {
                if (presets.isNotEmpty()) add(ExportQueue.Kind.VIDEO)
                if (audio) add(ExportQueue.Kind.AUDIO)
                if (subs) add(ExportQueue.Kind.SUBTITLES)
                if (chapterFile) add(ExportQueue.Kind.CHAPTERS)
            }
            Button(enabled = kinds.isNotEmpty() && (target != "folder" || folder != null), onClick = {
                prefs.edit().putString("videoExportTarget", target).putString("videoExportFolder", folder?.absolutePath).putBoolean("videoHardware", hardware).apply()
                wireExportQueue(c)
                val dir = if (target == "folder") folder!! else c.drive.created
                val upload = if (target == "nextcloud") WebDavClient.join(c.nextcloud.resultFolder, "Videos") else null
                val added = ExportQueue.add(p, VideoRender.PRESETS.map { it.id }.filter { it in presets }, kinds, dir, hardware, upload)
                Notices.info(tr("{0} Export(e) in der Warteschlange – du kannst weiterarbeiten.", added.size))
                onDismiss()
            }) { Text(tr("Exportieren")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun CheckRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onChange)
        Text(label)
    }
}

@Composable
private fun RadioRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(on, onClick)
        Text(label, maxLines = 2)
    }
}

/** The queue in the top bar (AP-57): „Exporte 1/3 · 45 %“; a click shows the list. Nothing while it is empty. */
@Composable
internal fun ExportQueueChip() {
    val entries by ExportQueue.entries.collectAsState()
    if (entries.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val active = entries.filter { !it.finished }
    val run = entries.firstOrNull { it.status == ExportQueue.Status.RUNNING }
    AssistChip(
        onClick = { open = true },
        leadingIcon = { Icon(Icons.Outlined.Queue, null, Modifier.size(16.dp)) },
        label = {
            Text(
                if (active.isEmpty()) tr("Exporte fertig ({0})", entries.size)
                else tr("Exporte {0}/{1}", entries.size - active.size + 1, entries.size) + (run?.let { " · ${(it.progress * 100).toInt()} %" } ?: ""),
            )
        },
        modifier = Modifier.padding(end = 6.dp),
    )
    if (open) ExportQueueDialog { open = false }
}

@Composable
internal fun ExportQueueDialog(onDismiss: () -> Unit) {
    val entries by ExportQueue.entries.collectAsState()
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Export-Warteschlange")) },
        text = {
            LazyColumn(Modifier.width(600.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.id }) { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.project.title + " · " + e.label, style = MaterialTheme.typography.titleSmall)
                        val status = when (e.status) {
                            ExportQueue.Status.WAITING -> tr("wartet")
                            ExportQueue.Status.RUNNING -> tr("läuft") + " · ${(e.progress * 100).toInt()} %"
                            ExportQueue.Status.DONE -> tr("fertig") + (e.seconds?.let { " " + tr("in {0}", clock(it)) } ?: "") + (e.encoder?.let { " · " + tr(Encoders.byId(it).label) } ?: "")
                            ExportQueue.Status.FAILED -> tr("fehlgeschlagen") + ": " + (e.error ?: "").take(200)
                            ExportQueue.Status.CANCELLED -> tr("abgebrochen")
                        }
                        Text(status + (e.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = if (e.status == ExportQueue.Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (e.status == ExportQueue.Status.RUNNING) LinearProgressIndicator(progress = { e.progress }, modifier = Modifier.fillMaxWidth())
                        Text(e.out.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        if (e.remote.isNotEmpty()) Text(tr("In Nextcloud:") + " " + e.remote.joinToString(), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!e.finished) TextButton(onClick = { ExportQueue.cancel(e.id) }) { Text(tr("Abbrechen")) }
                            if (e.status == ExportQueue.Status.DONE) {
                                e.files.firstOrNull()?.let { f -> TextButton(onClick = { DesktopActions.open(f) }) { Text(tr("Öffnen")) } }
                                e.files.firstOrNull()?.parentFile?.let { d -> TextButton(onClick = { DesktopActions.open(d) }) { Text(tr("Ordner öffnen")) } }
                                if (e.kind == ExportQueue.Kind.CHAPTERS) TextButton(onClick = { clipboard.setText(AnnotatedString(VideoEdit.youtubeChapters(e.project))) }) { Text(tr("Kapitel kopieren")) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
        dismissButton = { TextButton(onClick = { ExportQueue.clearFinished() }, enabled = entries.any { it.finished }) { Text(tr("Erledigte entfernen")) } },
    )
}
