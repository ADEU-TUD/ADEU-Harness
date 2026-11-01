package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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

private val DATE = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY)

/**
 * Kreativbereich (AP-24): own designs and the templates of the TUD corporate design; a design opens in the
 * [DesignEditor]. „Poster aus einem Paper“ starts a chat in the Gestalten mode with the paper attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreativeScreen(c: AppContainer, onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val requested by DesignHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        requested?.let { open = it; DesignHandoff.request.value = null }
    }
    open?.let { id ->
        DesignEditor(c, id, onClose = { open = null })
        return
    }
    val designs by c.designs.items.collectAsStateWithLifecycle()
    var blank by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun fromTemplate(t: DesignTemplate) {
        open = c.designs.save(t.create()).id
    }
    fun posterFromPaper() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Paper auswählen (PDF, Word …)"), multiple = false).firstOrNull() } ?: return@launch
        val r = AttachmentReader.read(f)
        val file = r.file
        if (file == null) {
            Notices.error(r.error ?: tr("Aus der Datei ließ sich kein Text lesen."))
            return@launch
        }
        ChatHandoff.pending.value = ChatHandoff.Content(
            tr("Mach aus dem angehängten Paper ein Tagungsposter (A1 hoch) mit Einleitung, Methode, Ergebnissen und Fazit. QR-Code zur DOI, falls vorhanden."),
            listOf(file), mode = ChatModes.DESIGN.id,
        )
        ChatHandoff.open.value = ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Kreativbereich")) },
                actions = {
                    TextButton(onClick = { posterFromPaper() }) { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.size(4.dp)); Text(tr("Poster aus einem Paper")) }
                    TextButton(onClick = { blank = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(4.dp)); Text(tr("Leeres Design")) }
                },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(190.dp),
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoraFigure(Modifier.size(width = 86.dp, height = 60.dp), label = "Lora", director = rememberLoraDirector(listOf(io.github.thepytales.llmhub.ui.LoraAction.PAINT, io.github.thepytales.llmhub.ui.LoraAction.LOOK), io.github.thepytales.llmhub.ui.LoraAction.PAINT))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Neu aus einer Vorlage"), style = MaterialTheme.typography.titleMedium)
                        HintText(tr("Alle Vorlagen im Corporate Design der TU Dresden: TUD-Blau, Noto Sans und Logo. Oder im Chat beschreiben – im Modus „Gestalten“ füllt Lora die Vorlage, z. B. aus einem Paper."))
                    }
                }
            }
            items(DesignTemplates.all, key = { "t-" + it.id }) { t -> TemplateCard(t) { fromTemplate(t) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(tr("Meine Designs") + if (designs.isNotEmpty()) "  ${designs.size}" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            if (designs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { HintText(tr("Noch keine Designs. Eine Vorlage oben anklicken oder „Leeres Design“ wählen.")) }
            }
            items(designs, key = { it.id }) { d -> DesignCardLarge(c, d, onOpen = { open = d.id }) }
        }
    }
    if (blank) BlankDesignDialog(onDismiss = { blank = false }) { f ->
        blank = false
        open = c.designs.save(DesignTemplates.blank(f)).id
    }
}

@Composable
private fun TemplateCard(t: DesignTemplate, onClick: () -> Unit) {
    val preview = remember(t.id) { t.create() }
    Surface(shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { DesignThumbnail(preview, Modifier.height(150.dp), widthPx = 300) }
            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.format.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DesignCardLarge(c: AppContainer, d: Design, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { DesignThumbnail(d, Modifier.height(150.dp), widthPx = 300) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(DesignFormat.byId(d.format)?.label, DATE.format(Date(d.updatedAt))).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Öffnen")) }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text(tr("Umbenennen")) }, onClick = { menu = false; rename = true })
                        DropdownMenuItem(text = { Text(tr("Verdoppeln")) }, onClick = { menu = false; c.designs.duplicate(d.id) })
                        DropdownMenuItem(text = { Text(tr("Löschen")) }, onClick = {
                            menu = false
                            val folder = c.designs.folder(d.id)
                            val backup = kotlin.io.path.createTempDirectory("design").toFile()
                            folder.copyRecursively(backup, overwrite = true)
                            c.designs.delete(d.id)
                            Notices.undo("„${d.title}“ " + tr("gelöscht"), restore = {
                                backup.copyRecursively(folder, overwrite = true)
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
