package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.DesignTemplate
import io.github.thepytales.llmhub.data.DesignTemplates
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

/**
 * „Erstellen“ on Windows (AP-24): above the TU Vorlagen the design templates (poster, flyer, social media …) – each to
 * design yourself or to hand to Lora – and „Meine Designs“. A design opens in the [DesignEditor] (route design/<id>).
 */
internal fun LazyListScope.designSection(c: AppContainer) {
    item(key = "design-templates") { DesignTemplatesRow(c) }
    item(key = "my-designs") { MyDesignsRow(c) }
}

/** Opens design [id] in the editor (the app root navigates there). */
fun openDesign(id: String) {
    DesignHandoff.request.value = id
}

@Composable
private fun SectionHead(title: String, hint: String?, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actions()
    }
}

@Composable
private fun DesignTemplatesRow(c: AppContainer) {
    var blank by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun posterFromPaper() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Paper auswählen (PDF, Word …)"), multiple = false).firstOrNull() } ?: return@launch
        val r = AttachmentReader.read(f)
        val file = r.file ?: run { Notices.error(r.error ?: tr("Aus der Datei ließ sich kein Text lesen.")); return@launch }
        ChatHandoff.pending.value = ChatHandoff.Content(
            tr("Mach aus dem angehängten Paper ein Tagungsposter (A1 hoch) mit Einleitung, Methode, Ergebnissen und Fazit. QR-Code zur DOI, falls vorhanden."),
            listOf(file), mode = ChatModes.DESIGN.id, send = true,
        )
        ChatHandoff.open.value = ""
    }
    Column {
        SectionHead(tr("Gestalten"), tr("Poster, Flyer, Aushänge, Social Media und mehr im TUD-Design – selbst gestalten oder Lora machen lassen.")) {
            TextButton(onClick = { posterFromPaper() }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Poster aus einem Paper")) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignTemplates.all.forEach { t -> TemplateCard(c, t) }
            Surface(
                shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(),
                modifier = Modifier.width(150.dp).height(232.dp).clip(RoundedCornerShape(12.dp)).clickable { blank = true },
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(tr("Leeres Design"), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Format wählen"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (blank) BlankDesignDialog(onDismiss = { blank = false }) { f ->
        blank = false
        openDesign(c.designs.save(DesignTemplates.blank(f)).id)
    }
}

@Composable
private fun TemplateCard(c: AppContainer, t: DesignTemplate) {
    val preview = remember(t.id) { t.create() }
    Surface(shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.width(170.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(6.dp)).clickable { openDesign(c.designs.save(t.create()).id) },
                contentAlignment = Alignment.Center,
            ) { DesignThumbnail(preview, Modifier.height(120.dp), widthPx = 260, placeholders = true) }
            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.format.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { openDesign(c.designs.save(t.create()).id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(tr("Selbst gestalten"), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = {
                    ChatHandoff.pending.value = ChatHandoff.Content(tr("Gestalte ${t.name.lowercase().let { if (it.startsWith("social")) "einen ${t.name}" else "ein(e) ${t.name}" }} (Vorlage ${t.id}): "), emptyList(), mode = ChatModes.DESIGN.id)
                    ChatHandoff.open.value = ""
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Lora machen lassen"), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun MyDesignsRow(c: AppContainer) {
    val designs by c.designs.items.collectAsStateWithLifecycle()
    if (designs.isEmpty()) return
    Column {
        SectionHead(tr("Meine Designs") + "  ${designs.size}", null)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            designs.forEach { d -> MyDesignCard(c, d) }
        }
    }
}

@Composable
private fun MyDesignCard(c: AppContainer, d: Design) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(),
        modifier = Modifier.width(170.dp).clip(RoundedCornerShape(12.dp)).clickable { openDesign(d.id) },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { DesignThumbnail(d, Modifier.height(120.dp), widthPx = 260) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(DesignFormat.byId(d.format)?.label?.substringBefore(" ("), DATE.format(Date(d.updatedAt))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Bearbeiten")) }, onClick = { menu = false; openDesign(d.id) })
                        DropdownMenuItem(text = { Text(tr("Umbenennen")) }, onClick = { menu = false; rename = true })
                        DropdownMenuItem(text = { Text(tr("Verdoppeln")) }, onClick = { menu = false; c.designs.duplicate(d.id) })
                        DropdownMenuItem(text = { Text(tr("Löschen")) }, onClick = {
                            menu = false
                            val folder = c.designs.folder(d.id)
                            val backup = kotlin.io.path.createTempDirectory("design").toFile()
                            runCatching { folder.copyRecursively(backup, overwrite = true) }
                            c.designs.delete(d.id)
                            Notices.undo("„${d.title}“ " + tr("gelöscht"), restore = {
                                runCatching { backup.copyRecursively(folder, overwrite = true) }
                                c.designs.save(d, touch = false)
                            }, commit = { backup.deleteRecursively() })
                        })
                    }
                }
            }
        }
    }
    if (rename) {
        var name by remember { mutableStateOf(d.title) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(tr("Umbenennen")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.width(360.dp)) },
            confirmButton = { TextButton(onClick = { rename = false; if (name.isNotBlank()) c.designs.save(d.copy(title = name.trim())) }) { Text(tr("Speichern")) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun BlankDesignDialog(onDismiss: () -> Unit, onPick: (DesignFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Leeres Design – Format wählen")) },
        text = {
            Column(Modifier.width(420.dp)) {
                DesignFormat.ALL.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(f) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.label, modifier = Modifier.weight(1f))
                        Text(f.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
