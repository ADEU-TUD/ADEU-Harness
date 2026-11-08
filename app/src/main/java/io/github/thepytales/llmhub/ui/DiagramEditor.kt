package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thepytales.llmhub.App
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.docs.DEdge
import io.github.thepytales.llmhub.docs.DNode
import io.github.thepytales.llmhub.docs.DStroke
import io.github.thepytales.llmhub.docs.Diagram
import io.github.thepytales.llmhub.docs.Diagrams
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.launch

/** What a drag on the diagram does (AP-61). */
internal enum class DiagramTool(val label: String) { SELECT(trLater("Auswählen")), CONNECT(trLater("Verbinden")), PEN(trLater("Stift")), NOTE(trLater("Notizzettel")) }

internal val KIND_LABELS = listOf("flow" to trLater("Flussdiagramm"), "mind" to trLater("Mindmap"), "org" to trLater("Organigramm"), "timeline" to trLater("Zeitstrahl"), "board" to trLater("Whiteboard"))

/** Asks the standard model for Mermaid of [kind] from [description] and reads it (Lora zeichnet, AP-61). */
suspend fun diagramFromDescription(c: io.github.thepytales.llmhub.AppContainer, kind: String, description: String): Diagram {
    val model = c.settings.current.defaultModel
    val result = c.llm.streamChat(model = model, messages = listOf(ChatMessage(role = "user", content = Diagrams.prompt(kind, description))), tools = null, thinking = ThinkingLevel.OFF) { }
    val mermaid = Diagrams.mermaidIn(result.content) ?: error(tr("Lora konnte daraus kein Diagramm machen – bitte den Ablauf genauer beschreiben."))
    val d = Diagrams.fromMermaid(mermaid) ?: error(tr("Lora konnte daraus kein Diagramm machen – bitte den Ablauf genauer beschreiben."))
    return Diagrams.arrange(if (kind == "org") d.copy(kind = "org") else d)
}

/**
 * Diagramm-Editor und Whiteboard (AP-61): nodes are dragged (the connectors stay on them), a double click on the empty
 * surface makes a node, on a node edits its text; „Verbinden“ draws a connector from node to node; the pen draws
 * freehand, „Notizzettel“ puts a sticky note. The surface pans (drag on empty ground) and zooms (mouse wheel, buttons).
 * „Automatisch anordnen“ lays out flow, org, mind map and timeline; the Mermaid text beside it goes both ways; Lora
 * draws from a description. Strg+Z / Strg+Y, Entf deletes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagramEditor(initial: Diagram, modifier: Modifier = Modifier, onChange: (Diagram) -> Unit = {}) {
    var d by remember { mutableStateOf(Diagrams.arrange(initial)) }
    val undo = remember { mutableStateListOf<Diagram>() }
    val redo = remember { mutableStateListOf<Diagram>() }
    var tool by remember { mutableStateOf(DiagramTool.SELECT) }
    var selected by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(40f, 40f)) }
    var editText by remember { mutableStateOf<String?>(null) }
    var mermaidOpen by remember { mutableStateOf(false) }
    var mermaidText by remember { mutableStateOf("") }
    var mermaidError by remember { mutableStateOf(false) }
    var wish by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    val pen = remember { mutableStateListOf<Float>() }
    var linkFrom by remember { mutableStateOf<String?>(null) }
    var linkTo by remember { mutableStateOf<Offset?>(null) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val focus = remember { FocusRequester() }

    fun set(next: Diagram, record: Boolean = true) {
        if (next == d) return
        if (record) { undo += d; if (undo.size > 100) undo.removeAt(0); redo.clear() }
        d = next
        onChange(next)
        if (!mermaidOpen || !record) return
        Diagrams.toMermaid(next)?.let { mermaidText = it; mermaidError = false }
    }
    fun toModel(p: Offset) = Offset((p.x - pan.x) / zoom, (p.y - pan.y) / zoom)
    fun addNode(at: Offset, note: Boolean = false) {
        val id = Diagrams.newId(d)
        val n = Diagrams.measure(DNode(id, if (note) tr("Notiz") else tr("Neuer Schritt"), if (note) "note" else if (d.kind == "mind") "round" else "box"))
        set(d.copy(nodes = d.nodes + n.copy(x = at.x - n.w / 2, y = at.y - n.h / 2), manual = true))
        selected = id
        editText = id
    }
    fun deleteSelected() { selected?.let { id -> set(Diagrams.remove(d, id)); selected = null } }

    Column(modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || editText != null) return@onPreviewKeyEvent false
        when {
            (e.isCtrlPressed || e.isMetaPressed) && e.key == Key.Z -> { undo.removeLastOrNull()?.let { redo += d; d = it; onChange(it) }; true }
            (e.isCtrlPressed || e.isMetaPressed) && e.key == Key.Y -> { redo.removeLastOrNull()?.let { undo += d; d = it; onChange(it) }; true }
            e.key == Key.Delete || e.key == Key.Backspace -> { deleteSelected(); true }
            else -> false
        }
    }.focusRequester(focus).focusable()) {
        FlowRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KIND_LABELS.forEach { (k, label) ->
                FilterChip(selected = d.kind == k, onClick = { set(Diagrams.arrange(d.copy(kind = k), force = k != "board")) }, label = { Text(tr(label)) })
            }
            VerticalDivider(Modifier.size(1.dp, 24.dp))
            DiagramTool.entries.forEach { t -> FilterChip(selected = tool == t, onClick = { tool = t; runCatching { focus.requestFocus() } }, label = { Text(tr(t.label)) }) }
            VerticalDivider(Modifier.size(1.dp, 24.dp))
            OutlinedButton(enabled = d.kind != "board", onClick = { set(Diagrams.arrange(d, force = true)) }) { Text(tr("Automatisch anordnen")) }
            if (d.kind == "flow" || d.kind == "org") TextButton(onClick = { set(Diagrams.arrange(d.copy(direction = if (d.direction == "LR") "TD" else "LR"), force = true)) }) { Text(if (d.direction == "LR") tr("Von oben nach unten") else tr("Von links nach rechts")) }
            TextButton(onClick = { mermaidOpen = !mermaidOpen; if (mermaidOpen) mermaidText = Diagrams.toMermaid(d) ?: "" }) { Text(if (mermaidOpen) tr("Mermaid ausblenden") else tr("Mermaid-Text")) }
            TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(3f) }) { Text("+") }
            TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.3f) }) { Text("−") }
            TextButton(enabled = undo.isNotEmpty(), onClick = { undo.removeLastOrNull()?.let { redo += d; d = it; onChange(it) } }) { Text(tr("Rückgängig")) }
            selected?.let { id ->
                VerticalDivider(Modifier.size(1.dp, 24.dp))
                listOf("box" to trLater("Rechteck"), "round" to trLater("Abgerundet"), "diamond" to trLater("Raute"), "circle" to trLater("Kreis"), "note" to trLater("Notiz")).forEach { (shape, label) ->
                    FilterChip(selected = d.node(id)?.shape == shape, onClick = { set(d.copy(nodes = d.nodes.map { if (it.id == id) Diagrams.measure(it.copy(shape = shape, w = 0f, h = 0f)).let { m -> m.copy(x = it.x + (it.w - m.w) / 2, y = it.y + (it.h - m.h) / 2) } else it })) }, label = { Text(tr(label)) })
                }
                TextButton(onClick = { editText = id }) { Text(tr("Text …")) }
                TextButton(onClick = ::deleteSelected) { Text(tr("Löschen")) }
            }
        }
        // Lora zeichnet (AP-61).
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(wish, { wish = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text(tr("Lora zeichnet: Ablauf oder Thema beschreiben, z. B. „Antrag stellen, prüfen, bei Fehlern zurück, sonst genehmigen“")) })
            Spacer(Modifier.size(6.dp))
            Button(enabled = wish.isNotBlank() && !asking, onClick = {
                asking = true
                scope.launch {
                    val kind = if (d.kind == "board") "flow" else d.kind
                    runCatching { diagramFromDescription(App.container, kind, wish) }
                        .onSuccess { set(it); selected = null; mermaidText = Diagrams.toMermaid(it).orEmpty() }
                        .onFailure { Notices.error(it.message ?: it.javaClass.simpleName) }
                    asking = false
                }
            }) { if (asking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(tr("Zeichnen")) }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            val shapes = remember(d) { Diagrams.layout(d) }
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF7F8FA))) {
                Canvas(
                    Modifier.fillMaxSize().semantics { contentDescription = tr("Diagramm-Fläche") + ": " + Diagrams.altText(d) }
                        .pointerInput(tool, d) {
                            detectTapGestures(
                                onDoubleTap = { p ->
                                    val m = toModel(p)
                                    val hit = Diagrams.nodeAt(d, m.x, m.y)
                                    if (hit != null) { selected = hit.id; editText = hit.id } else if (tool != DiagramTool.PEN) addNode(m)
                                },
                                onTap = { p ->
                                    runCatching { focus.requestFocus() }
                                    val m = toModel(p)
                                    val hit = Diagrams.nodeAt(d, m.x, m.y)
                                    when {
                                        tool == DiagramTool.NOTE && hit == null -> addNode(m, note = true)
                                        tool == DiagramTool.CONNECT && hit != null && selected != null && selected != hit.id -> { set(Diagrams.connect(d, selected!!, hit.id).let { if (it.kind != "board") it else it }); selected = hit.id }
                                        else -> selected = hit?.id
                                    }
                                },
                            )
                        }
                        .pointerInput(tool, d, zoom, pan) {
                            var dragging: String? = null
                            var startNodes: List<DNode> = emptyList()
                            var acc = Offset.Zero
                            detectDragGestures(
                                onDragStart = { p ->
                                    val m = toModel(p)
                                    val hit = Diagrams.nodeAt(d, m.x, m.y)
                                    acc = Offset.Zero
                                    when {
                                        tool == DiagramTool.PEN -> { pen.clear(); pen += m.x; pen += m.y }
                                        tool == DiagramTool.CONNECT && hit != null -> { linkFrom = hit.id; linkTo = m }
                                        hit != null -> { dragging = hit.id; selected = hit.id; startNodes = d.nodes }
                                        else -> dragging = null
                                    }
                                },
                                onDrag = { ch, delta ->
                                    ch.consume()
                                    val m = toModel(ch.position)
                                    when {
                                        tool == DiagramTool.PEN -> { pen += m.x; pen += m.y }
                                        linkFrom != null -> linkTo = m
                                        dragging != null -> {
                                            acc += delta / zoom
                                            val id = dragging
                                            d = d.copy(nodes = startNodes.map { if (it.id == id) it.copy(x = it.x + acc.x, y = it.y + acc.y) else it }, manual = true)
                                        }
                                        else -> pan += delta
                                    }
                                },
                                onDragEnd = {
                                    when {
                                        tool == DiagramTool.PEN -> { if (pen.size >= 4) set(d.copy(strokes = d.strokes + DStroke(pen.toList()), manual = true)); pen.clear() }
                                        linkFrom != null -> {
                                            val target = linkTo?.let { Diagrams.nodeAt(d, it.x, it.y) }
                                            if (target != null) set(Diagrams.connect(d, linkFrom!!, target.id))
                                            linkFrom = null; linkTo = null
                                        }
                                        dragging != null -> {
                                            val moved = d
                                            d = d.copy(nodes = startNodes)
                                            set(moved)
                                            dragging = null
                                        }
                                    }
                                },
                                onDragCancel = { pen.clear(); linkFrom = null; linkTo = null; if (dragging != null) { d = d.copy(nodes = startNodes); dragging = null } },
                            )
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.type == PointerEventType.Scroll) {
                                        val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        val at = e.changes.firstOrNull()?.position ?: Offset.Zero
                                        val before = zoom
                                        zoom = (zoom * if (dy > 0) 1f / 1.1f else 1.1f).coerceIn(0.3f, 3f)
                                        pan = at - (at - pan) * (zoom / before)
                                        e.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
                ) {
                    drawDiagram(shapes, measurer, zoom, Offset(-pan.x / zoom, -pan.y / zoom), selected)
                    val from = linkFrom?.let { d.node(it) }
                    val to = linkTo
                    if (from != null && to != null) drawLine(Color(0xFFF5A200), Offset((from.x + from.w / 2) * zoom + pan.x, (from.y + from.h / 2) * zoom + pan.y), Offset(to.x * zoom + pan.x, to.y * zoom + pan.y), strokeWidth = 2f)
                    if (pen.size >= 4) {
                        val pts = pen.chunked(2).filter { it.size == 2 }.map { Offset(it[0] * zoom + pan.x, it[1] * zoom + pan.y) }
                        pts.zipWithNext { a, b -> drawLine(diagramColor(Diagrams.TUD_BLUE), a, b, strokeWidth = 3f * zoom) }
                    }
                }
                Text(
                    tr(when (tool) {
                        DiagramTool.SELECT -> "Doppelklick: neuer Kasten bzw. Text ändern · Ziehen: verschieben · auf leerer Fläche ziehen: Ansicht verschieben · Mausrad: Zoom"
                        DiagramTool.CONNECT -> "Von einem Kasten zum anderen ziehen (oder erst einen, dann den zweiten anklicken) – der Verbinder bleibt an beiden haften."
                        DiagramTool.PEN -> "Freihand zeichnen."
                        DiagramTool.NOTE -> "Auf die Fläche klicken: ein Notizzettel."
                    }),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                )
            }
            if (mermaidOpen) {
                VerticalDivider()
                Column(Modifier.width(320.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Mermaid-Text"), style = MaterialTheme.typography.titleSmall)
                    if (Diagrams.toMermaid(d) == null) HintText(tr("Ein Whiteboard hat keinen Mermaid-Text (Notizzettel und Freihand)."))
                    OutlinedTextField(
                        mermaidText,
                        { t ->
                            mermaidText = t
                            val parsed = Diagrams.fromMermaid(t)
                            mermaidError = parsed == null
                            if (parsed != null) set(Diagrams.arrange(if (d.kind == "org" && parsed.kind == "flow") parsed.copy(kind = "org") else parsed), record = true)
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        isError = mermaidError,
                    )
                    if (mermaidError) Text(tr("Kein gültiges Mermaid (flowchart, graph, mindmap oder timeline)."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    editText?.let { id ->
        val node = d.node(id)
        if (node == null) { editText = null; return@let }
        var text by remember(id) { mutableStateOf(node.text) }
        AlertDialog(
            onDismissRequest = { editText = null },
            title = { Text(tr("Text des Kastens")) },
            text = { OutlinedTextField(text, { text = it }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    set(d.copy(nodes = d.nodes.map { if (it.id == id) Diagrams.measure(it.copy(text = text.trim(), w = 0f, h = 0f)).let { m -> m.copy(x = it.x + (it.w - m.w) / 2, y = it.y + (it.h - m.h) / 2) } else it }))
                    editText = null
                }) { Text(tr("Übernehmen")) }
            },
            dismissButton = { TextButton(onClick = { editText = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

/** The editor in a large dialog: „Übernehmen“ gives the diagram back (a document block, the whiteboard). */
@Composable
fun DiagramEditorDialog(initial: Diagram, title: String = tr("Diagramm"), onDismiss: () -> Unit, onSave: (Diagram) -> Unit) {
    var current by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(0.94f), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) }
                    Spacer(Modifier.size(6.dp))
                    Button(enabled = !current.isEmpty, onClick = { onSave(current) }) { Text(tr("Übernehmen")) }
                }
                HorizontalDivider()
                DiagramEditor(initial, Modifier.weight(1f).fillMaxWidth()) { current = it }
            }
        }
    }
}

/** A diagram to start with: two connected steps of [kind]. */
fun starterDiagram(kind: String = "flow"): Diagram = Diagrams.arrange(
    Diagram(
        kind = kind,
        nodes = listOf(DNode("n1", tr("Start"), if (kind == "mind") "circle" else "stadium"), DNode("n2", tr("Nächster Schritt"), if (kind == "mind") "round" else "box")),
        edges = listOf(DEdge("n1", "n2", arrow = kind != "mind")),
    ),
    force = true,
)
