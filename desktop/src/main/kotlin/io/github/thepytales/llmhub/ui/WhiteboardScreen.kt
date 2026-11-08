package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.docs.DiagramRaster
import io.github.thepytales.llmhub.docs.Diagram
import io.github.thepytales.llmhub.docs.Diagrams
import io.github.thepytales.llmhub.net.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** A diagram or whiteboard of „Erstellen“ (AP-61): `whiteboards/<id>.json` in the data folder. */
@Serializable
data class Board(val id: String = UUID.randomUUID().toString(), val title: String, val diagram: Diagram, val updatedAt: Long = System.currentTimeMillis())

object Whiteboards {
    private var dir: File? = null
    private val _items = MutableStateFlow<List<Board>>(emptyList())
    val items: StateFlow<List<Board>> = _items.asStateFlow()

    @Synchronized
    fun init(files: File) {
        if (dir != null) return
        val d = File(files, "whiteboards").apply { mkdirs() }
        dir = d
        _items.value = d.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { f ->
            runCatching { JSON.decodeFromString(Board.serializer(), AtomicFiles.read(f) ?: return@mapNotNull null) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(b: Board): Board {
        val next = b.copy(updatedAt = System.currentTimeMillis())
        dir?.let { AtomicFiles.write(File(it, "${next.id}.json"), JSON.encodeToString(Board.serializer(), next)) }
        _items.value = (_items.value.filter { it.id != next.id } + next).sortedByDescending { it.updatedAt }
        return next
    }

    @Synchronized
    fun delete(id: String) {
        dir?.let { File(it, "$id.json").delete() }
        _items.value = _items.value.filter { it.id != id }
    }
}

private val STARTS = listOf("flow" to trLater("Flussdiagramm"), "mind" to trLater("Mindmap"), "org" to trLater("Organigramm"), "timeline" to trLater("Zeitstrahl"), "board" to trLater("Whiteboard"))

/** „Diagramme & Whiteboard“ in Erstellen (AP-61): new ones of every kind and the ones made so far. */
internal fun LazyListScope.diagramSection(c: AppContainer) {
    item(key = "diagrams") { DiagramsRow(c) }
}

@Composable
private fun DiagramsRow(c: AppContainer) {
    remember { Whiteboards.init(c.appContext.filesDir) }
    val boards by Whiteboards.items.collectAsState()
    fun open(b: Board) { Navigation.openRoute.value = "whiteboard/${b.id}" }
    Column {
        // Compact: title and the kinds in one line; the boards made so far below, only when there are some.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Spot("create.diagrams") { Text(tr("Diagramme & Whiteboard"), style = MaterialTheme.typography.titleMedium) }
                Text(tr("Flussdiagramme, Mindmaps, Organigramme und Zeitstrahlen – Verbinder haften an den Formen, Lora zeichnet aus einer Beschreibung; dazu eine freie Tafel für Notizzettel und Freihand."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STARTS.forEach { (kind, label) ->
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            val d = if (kind == "board") Diagram(kind = "board", manual = true) else starterDiagram(kind)
                            open(Whiteboards.save(Board(title = tr(label), diagram = d)))
                        },
                        label = { Text(tr(label)) },
                        leadingIcon = { Icon(Icons.Outlined.AccountTree, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
        if (boards.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            boards.take(12).forEach { b ->
                Surface(shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.width(190.dp).height(110.dp).clip(RoundedCornerShape(12.dp)).clickable { open(b) }) {
                    Column(Modifier.padding(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) { if (!b.diagram.isEmpty) DiagramCanvas(b.diagram, Modifier.height(62.dp)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                Whiteboards.delete(b.id)
                                Notices.undo("„${b.title}“ " + tr("gelöscht"), restore = { Whiteboards.save(b) })
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A diagram or whiteboard full screen (AP-61): the editor, saved as it changes; into a new document, as PNG, or the
 * Mermaid text onto the clipboard.
 */
@Composable
fun WhiteboardScreen(c: AppContainer, id: String, onBack: () -> Unit) {
    remember { Whiteboards.init(c.appContext.filesDir) }
    val board = remember(id) { Whiteboards.get(id) }
    if (board == null) { LaunchedEffect(id) { onBack() }; return }
    var current by remember(id) { mutableStateOf(board) }
    var version by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(version) { if (version > 0) { delay(600); withContext(Dispatchers.IO) { current = Whiteboards.save(current) } } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Whiteboards.save(current); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück")) }
            OutlinedTextField(current.title, { t -> current = current.copy(title = t); version++ }, singleLine = true, modifier = Modifier.width(320.dp))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val doc = DocsHandoff.open(c, current.title, "# ${current.title}\n\n" + Diagrams.block(current.diagram))
                Notices.info(tr("Dokument „{0}“ mit dem Diagramm angelegt.", doc.title))
            }) { Text(tr("In ein neues Dokument")) }
            Diagrams.toMermaid(current.diagram)?.let { m -> TextButton(onClick = { clipboard.setText(AnnotatedString(m)); Notices.info(tr("Mermaid-Text kopiert.")) }) { Text(tr("Mermaid kopieren")) } }
            TextButton(onClick = {
                scope.launch {
                    val f = withContext(Dispatchers.IO) { DesktopActions.pickSaveFile(tr("Als Bild speichern"), current.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Diagramm" } + ".png", "png") } ?: return@launch
                    withContext(Dispatchers.IO) { f.writeBytes(DiagramRaster.png(current.diagram)) }
                    Notices.show(Notices.Notice(tr("Gespeichert:") + " " + f.name, action = tr("Öffnen"), onAction = { DesktopActions.open(f) }))
                }
            }) { Text(tr("Als PNG")) }
        }
        HorizontalDivider()
        DiagramEditor(board.diagram, Modifier.weight(1f).fillMaxWidth()) { d -> current = current.copy(diagram = d); version++ }
    }
}
