package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocComment
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.text.DocOutline
import kotlin.math.roundToInt

/**
 * The parts of the Fließtext-Editor around the text (AP-44/46): Gliederung on the left, Suchen und Ersetzen, the
 * Lineal with margins and indents, comments at the margin and Lora's small menu over a selection (AP-47).
 */

// ── Gliederung (AP-46) ──────────────────────────────────────────────────────────────────────────

/**
 * The headings as a tree: a click jumps there, the handle moves the whole section (drag it up or down), the arrow
 * folds the levels below.
 */
@Composable
internal fun FlowOutline(state: FlowState, onJump: (Int) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    // The headings are read anew only when one of them changed (not on every key in a long document).
    val sig = remember(state.version) {
        state.segs.map { s ->
            if (s is FlowSeg.Text) s.rich.paras.indices.filter { s.rich.paras[it].kind.heading || s.rich.paras[it].kind == io.github.thepytales.llmhub.docs.text.ParaKind.TITLE }
                .map { s.rich.paras[it].kind to s.rich.paraText(it) } else (s as FlowSeg.Raw).markdown.take(0)
        }
    }
    val heads = remember(sig) { DocOutline.headings(state.markdown()) }
    var folded by remember(heads.size) { mutableStateOf(setOf<Int>()) }
    val density = LocalDensity.current
    val rowPx = with(density) { 30.dp.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    // Shown rows: headings whose parents are not folded.
    val shown = remember(heads, folded) {
        val out = mutableListOf<Int>()
        var hideBelow: Int? = null
        heads.forEachIndexed { i, h ->
            if (hideBelow != null && h.level > hideBelow!!) return@forEachIndexed
            hideBelow = if (i in folded) h.level else null
            out += i
        }
        out
    }
    Column(modifier.fillMaxHeight().padding(start = 8.dp, end = 4.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Gliederung"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, contentDescription = tr("Gliederung schließen"), modifier = Modifier.size(16.dp)) }
        }
        if (heads.isEmpty()) {
            HintText(tr("Überschriften erscheinen hier. Mit „Überschrift 1–4“ (Strg+Alt+1…4) entsteht die Gliederung."), Modifier.padding(top = 6.dp))
            return@Column
        }
        HintText(tr("Klicken springt hin, ⠿ ziehen verschiebt den ganzen Abschnitt."), Modifier.padding(bottom = 6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            shown.forEachIndexed { row, i ->
                val h = heads[i]
                val hasChildren = heads.getOrNull(i + 1)?.let { it.level > h.level } == true
                val isDragged = dragging == i
                var menu by remember(i) { mutableStateOf(false) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .then(if (isDragged) Modifier.offset { IntOffset(0, dragDy.roundToInt()) }.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)) else Modifier)
                        .padding(start = ((h.level - 1).coerceAtLeast(0) * 12).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator, contentDescription = tr("Abschnitt verschieben"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).pointerInput(i, shown) {
                            detectDragGestures(
                                onDragStart = { dragging = i; dragDy = 0f },
                                onDragEnd = {
                                    val steps = (dragDy / rowPx).roundToInt()
                                    val targetRow = (row + if (steps > 0) steps + 1 else steps).coerceIn(0, shown.size)
                                    val target = if (targetRow >= shown.size) heads.size else shown[targetRow]
                                    dragging = null
                                    dragDy = 0f
                                    if (steps != 0) state.applyMarkdown(DocOutline.move(state.markdown(), i, target))
                                },
                                onDragCancel = { dragging = null; dragDy = 0f },
                            ) { change, amount -> change.consume(); dragDy += amount.y }
                        },
                    )
                    if (hasChildren) Icon(
                        if (i in folded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = if (i in folded) tr("Aufklappen") else tr("Zuklappen"),
                        modifier = Modifier.size(18.dp).clickable { folded = if (i in folded) folded - i else folded + i },
                    ) else Spacer(Modifier.width(18.dp))
                    Text(
                        h.text.ifBlank { tr("(ohne Titel)") }, maxLines = 1,
                        style = if (h.level <= 1) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).clickable { onJump(i) }.padding(horizontal = 4.dp),
                    )
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr"), modifier = Modifier.size(14.dp)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, null) }, text = { Text(tr("Abschnitt nach oben")) }, onClick = {
                                menu = false; state.applyMarkdown(DocOutline.step(state.markdown(), i, up = true))
                            })
                            DropdownMenuItem(leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) }, text = { Text(tr("Abschnitt nach unten")) }, onClick = {
                                menu = false; state.applyMarkdown(DocOutline.step(state.markdown(), i, up = false))
                            })
                        }
                    }
                }
            }
        }
    }
}

// ── Suchen und Ersetzen (AP-44) ─────────────────────────────────────────────────────────────────

/** The bar for Suchen und Ersetzen in the running text: hits highlighted, one by one or all, also formatting them. */
@Composable
internal fun FlowFindBar(state: FlowState, onShow: (FlowSeg.Text, IntRange) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    val hits = state.hits()
    val at = if (hits.isEmpty()) 0 else state.findAt.coerceIn(0, hits.lastIndex)
    fun go(i: Int) {
        if (hits.isEmpty()) return
        val k = ((i % hits.size) + hits.size) % hits.size
        state.findAt = k
        val (seg, range) = hits[k]
        onShow(seg, range)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(state.find, { state.find = it; state.findAt = 0 }, singleLine = true, placeholder = { Text(tr("Suchen")) }, modifier = Modifier.weight(1f))
            OutlinedTextField(state.replaceWith, { state.replaceWith = it }, singleLine = true, placeholder = { Text(tr("Ersetzen durch")) }, modifier = Modifier.weight(1f))
            Text(if (hits.isEmpty()) tr("0 Treffer") else tr("{0} von {1}", at + 1, hits.size), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { go(at - 1) }, enabled = hits.isNotEmpty()) { Text("‹") }
            TextButton(onClick = { go(at + 1) }, enabled = hits.isNotEmpty()) { Text("›") }
            TextButton(onClick = { state.replaceHit(at); go(at) }, enabled = hits.isNotEmpty()) { Text(tr("Ersetzen")) }
            TextButton(onClick = { val n = state.replaceAll(); Notices.info(tr("{0} Stelle(n) ersetzt", n)) }, enabled = hits.isNotEmpty()) { Text(tr("Alle ersetzen")) }
            var more by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { more = true }) { Text(tr("Optionen …")) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Checkbox(state.matchCase, null) }, text = { Text(tr("Groß-/Kleinschreibung beachten")) },
                        onClick = { state.matchCase = !state.matchCase; state.findAt = 0 },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Checkbox(state.wholeWord, null) }, text = { Text(tr("Nur ganze Wörter")) },
                        onClick = { state.wholeWord = !state.wholeWord; state.findAt = 0 },
                    )
                    HorizontalDivider()
                    Text(tr("Alle Treffer formatieren"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    listOf<Pair<String, (io.github.thepytales.llmhub.docs.text.CharFormat) -> io.github.thepytales.llmhub.docs.text.CharFormat>>(
                        "Fett" to { it.copy(bold = true) },
                        "Kursiv" to { it.copy(italic = true) },
                        "Unterstrichen" to { it.copy(underline = true) },
                        "Gelb hervorheben" to { it.copy(highlight = "FFF59D") },
                        "Rot" to { it.copy(color = "C00000") },
                        "Formatierung entfernen" to { io.github.thepytales.llmhub.docs.text.CharFormat.PLAIN },
                    ).forEach { (label, change) ->
                        DropdownMenuItem(text = { Text(tr(label)) }, enabled = hits.isNotEmpty(), onClick = {
                            more = false
                            val n = state.formatHits(change)
                            Notices.info(tr("{0} Stelle(n) formatiert", n))
                        })
                    }
                }
            }
            IconButton(onClick = { state.findOpen = false }) { Icon(Icons.Outlined.Close, contentDescription = tr("Suche schließen")) }
        }
    }
}

// ── Lineal (AP-44) ──────────────────────────────────────────────────────────────────────────────

/**
 * The ruler above the page: centimetres, the margins (grey; drag the edge to change them for the whole document) and
 * the indents of the paragraph at the cursor (▽ first line, △ left indent).
 */
@Composable
internal fun FlowRuler(state: FlowState, pageWidth: Dp, marginMm: Int, zoom: Float) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    val density = LocalDensity.current
    val pxPerMm = with(density) { (3.7795f * zoom).dp.toPx() }
    var dragMargin by remember { mutableStateOf<Float?>(null) }
    var dragLeft by remember { mutableStateOf<Float?>(null) }
    var dragFirst by remember { mutableStateOf<Float?>(null) }
    // Tabstopps (AP-44): the kind a click sets (button in the margin, like Word), the stop being dragged.
    var tabKind by remember { mutableStateOf(io.github.thepytales.llmhub.docs.text.TabKind.LEFT) }
    var dragTab by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var tabOff by remember { mutableStateOf(false) }
    val p = state.paraHere()
    val margin = dragMargin ?: marginMm.toFloat()
    val left = dragLeft ?: p.left.toFloat()
    val first = dragFirst ?: (p.left + p.first).toFloat()
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val shade = MaterialTheme.colorScheme.surfaceVariant
    val paper = MaterialTheme.colorScheme.surface
    val accent = LocalChatLook.current.brand
    val label = androidx.compose.ui.text.rememberTextMeasurer()
    Box(
        Modifier.width(pageWidth).height(22.dp)
            .semantics { contentDescription = tr("Lineal: Seitenränder {0} mm", margin.roundToInt()) }
            .drawBehind {
                drawRect(paper)
                val m = margin * pxPerMm
                drawRect(shade, size = androidx.compose.ui.geometry.Size(m, size.height))
                drawRect(shade, topLeft = Offset(size.width - m, 0f), size = androidx.compose.ui.geometry.Size(m, size.height))
                // Centimetres from the left margin, as in Word.
                var mm = -((margin / 10).toInt() * 10).toFloat()
                while (m + mm * pxPerMm < size.width) {
                    val x = m + mm * pxPerMm
                    if (x >= 0) {
                        val whole = (mm.roundToInt() % 10) == 0
                        drawLine(tick, Offset(x, size.height - if (whole) 8f else 4f), Offset(x, size.height), strokeWidth = 1f)
                        if (whole && mm.roundToInt() != 0) {
                            val t = label.measure(kotlin.math.abs(mm.roundToInt() / 10).toString(), androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), color = tick))
                            if (x - t.size.width / 2 > 0 && x + t.size.width / 2 < size.width) drawText(t, topLeft = Offset(x - t.size.width / 2, 1f))
                        }
                    }
                    mm += 5f
                }
                // Indent marks of the paragraph at the cursor.
                fun tri(x: Float, down: Boolean) {
                    val path = Path().apply {
                        if (down) { moveTo(x - 6f, 0f); lineTo(x + 6f, 0f); lineTo(x, 8f) } else { moveTo(x - 6f, size.height); lineTo(x + 6f, size.height); lineTo(x, size.height - 8f) }
                        close()
                    }
                    drawPath(path, accent)
                }
                tri(m + first * pxPerMm, down = true)
                tri(m + left * pxPerMm, down = false)
                // Tabstopps of the paragraph: └ left, ┴ centred, ┘ right.
                fun tab(x: Float, kind: io.github.thepytales.llmhub.docs.text.TabKind, faint: Boolean) {
                    val c = if (faint) accent.copy(alpha = 0.35f) else accent
                    val y0 = size.height - 9f
                    val y1 = size.height - 2f
                    drawLine(c, Offset(x, y0), Offset(x, y1), strokeWidth = 2f)
                    when (kind) {
                        io.github.thepytales.llmhub.docs.text.TabKind.LEFT -> drawLine(c, Offset(x, y1), Offset(x + 6f, y1), strokeWidth = 2f)
                        io.github.thepytales.llmhub.docs.text.TabKind.RIGHT -> drawLine(c, Offset(x - 6f, y1), Offset(x, y1), strokeWidth = 2f)
                        io.github.thepytales.llmhub.docs.text.TabKind.CENTER -> drawLine(c, Offset(x - 5f, y1), Offset(x + 5f, y1), strokeWidth = 2f)
                    }
                }
                p.tabs.forEachIndexed { i, t ->
                    val mm = dragTab?.takeIf { it.first == i }?.second ?: t.mm.toFloat()
                    tab(m + mm * pxPerMm, t.kind, faint = dragTab?.first == i && tabOff)
                }
                // The kind a click sets, in the top left corner of the margin.
                tab(9f, tabKind, faint = false)
            }
            .pointerInput(marginMm, p, pxPerMm, tabKind) {
                detectTapGestures { pos ->
                    val m = marginMm * pxPerMm
                    if (pos.x < 20f && m > 22f) {
                        tabKind = io.github.thepytales.llmhub.docs.text.TabKind.entries[(tabKind.ordinal + 1) % 3]
                        return@detectTapGestures
                    }
                    // A click into the text area sets a Tabstopp there (whole millimetres).
                    if (pos.x <= m || pos.x >= size.width - m || pos.y < size.height / 3) return@detectTapGestures
                    val mm = ((pos.x - m) / pxPerMm).roundToInt()
                    if (p.tabs.any { kotlin.math.abs(it.mm - mm) < 2 }) return@detectTapGestures
                    state.paragraph { q -> q.copy(tabs = (q.tabs + io.github.thepytales.llmhub.docs.text.TabStop(mm, tabKind)).sortedBy { it.mm }) }
                }
            }
            .pointerInput(marginMm, p, pxPerMm) {
                var mode = 0
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        val m = marginMm * pxPerMm
                        val xFirst = m + (p.left + p.first) * pxPerMm
                        val xLeft = m + p.left * pxPerMm
                        val tabAt = p.tabs.indexOfFirst { kotlin.math.abs(pos.x - (m + it.mm * pxPerMm)) < 7f }
                        mode = when {
                            tabAt >= 0 && pos.y >= size.height / 2 -> 4
                            kotlin.math.abs(pos.x - xFirst) < 10f && pos.y < size.height / 2 -> 2
                            kotlin.math.abs(pos.x - xLeft) < 10f -> 1
                            kotlin.math.abs(pos.x - m) < 10f || kotlin.math.abs(pos.x - (size.width - m)) < 10f -> 3
                            else -> 0
                        }
                        when (mode) {
                            1 -> dragLeft = p.left.toFloat()
                            2 -> dragFirst = (p.left + p.first).toFloat()
                            3 -> dragMargin = marginMm.toFloat()
                            4 -> { dragTab = tabAt to p.tabs[tabAt].mm.toFloat(); tabOff = false }
                        }
                    },
                    onDragEnd = {
                        when (mode) {
                            1 -> dragLeft?.let { l -> state.paragraph { q -> q.copy(left = l.roundToInt().coerceIn(0, 150), first = (q.left + q.first - l.roundToInt()).coerceIn(-80, 80)) } }
                            2 -> dragFirst?.let { f -> state.paragraph { q -> q.copy(first = (f.roundToInt() - q.left).coerceIn(-80, 80)) } }
                            3 -> dragMargin?.let { mm -> state.setMargins(mm.roundToInt()) }
                            // A Tabstopp moves, or goes when it was dragged off the ruler (like in Word).
                            4 -> dragTab?.let { (i, mm) ->
                                state.paragraph { q ->
                                    val rest = q.tabs.filterIndexed { k, _ -> k != i }
                                    q.copy(tabs = if (tabOff) rest else (rest + q.tabs[i].copy(mm = mm.roundToInt().coerceAtLeast(0))).distinctBy { it.mm }.sortedBy { it.mm })
                                }
                            }
                        }
                        dragLeft = null; dragFirst = null; dragMargin = null; dragTab = null; tabOff = false; mode = 0
                    },
                    onDragCancel = { dragLeft = null; dragFirst = null; dragMargin = null; dragTab = null; tabOff = false; mode = 0 },
                ) { change, dx ->
                    change.consume()
                    when (mode) {
                        1 -> dragLeft = ((dragLeft ?: 0f) + dx / pxPerMm).coerceIn(0f, 150f)
                        2 -> dragFirst = ((dragFirst ?: 0f) + dx / pxPerMm).coerceIn(-80f, 150f)
                        // The left edge drags to the right, the right edge to the left: both make the margins wider.
                        3 -> dragMargin = ((dragMargin ?: 20f) + (if (change.position.x > size.width / 2) -dx else dx) / pxPerMm).coerceIn(5f, 60f)
                        4 -> {
                            dragTab = dragTab?.let { (i, mm) -> i to (mm + dx / pxPerMm).coerceIn(0f, 400f) }
                            tabOff = change.position.y > size.height + 24f || change.position.y < -24f
                        }
                    }
                }
            },
    )
}

// ── Kommentare am Rand (AP-44) ──────────────────────────────────────────────────────────────────

/**
 * The open comments of this tab beside the page, each next to the text it refers to (as far as there is room);
 * a click marks the text, ✓ resolves it.
 */
@Composable
internal fun FlowMarginComments(c: AppContainer, doc: LiveDoc, state: FlowState, offset: Float, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    val list = state.comments
    if (list.isEmpty()) return
    val density = LocalDensity.current
    Layout(
        modifier = modifier.width(230.dp),
        content = {
            list.forEach { cm ->
                Surface(
                    shape = RoundedCornerShape(8.dp), tonalElevation = 1.dp, shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).clickable { state.showQuote(cm.quote) },
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (cm.author == "Lora") Icons.Outlined.AutoAwesome else Icons.Outlined.AddComment, null, Modifier.size(14.dp), tint = LocalChatLook.current.brand)
                            Spacer(Modifier.width(4.dp))
                            Text(if (cm.author == "Sie") tr("Sie") else cm.author, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = {
                                c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(resolved = true) else it }) }
                            }, modifier = Modifier.size(22.dp)) { Icon(Icons.Outlined.Check, contentDescription = tr("Erledigt"), modifier = Modifier.size(14.dp)) }
                        }
                        Text(cm.text, style = MaterialTheme.typography.bodySmall, maxLines = 6)
                        if (cm.replies.isNotEmpty()) Text(tr("{0} Antworten", cm.replies.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onOpen))
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val gap = with(density) { 8.dp.roundToPx() }
        val ys = IntArray(placeables.size)
        var bottom = 0
        list.forEachIndexed { i, cm ->
            val want = (state.quoteY(cm.quote)?.plus(offset) ?: bottom.toFloat()).roundToInt().coerceAtLeast(0)
            ys[i] = maxOf(want, bottom)
            bottom = ys[i] + placeables[i].height + gap
        }
        layout(constraints.maxWidth, bottom.coerceAtLeast(0)) {
            placeables.forEachIndexed { i, pl -> pl.place(0, ys[i]) }
        }
    }
}

/** The open comments of [tabId], in the order of the text. */
internal fun openComments(doc: LiveDoc, tabId: String?): List<DocComment> =
    doc.comments.filter { !it.resolved && (tabId == null || it.tabId == tabId) && it.quote.isNotBlank() }

// ── Lora über der Markierung (AP-47) ────────────────────────────────────────────────────────────

/** Lora's small menu over a selection in the running text: quick revisions as suggestions, or a comment. */
@Composable
internal fun FlowSelectionMenu(onRevise: (String?) -> Unit, onComment: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), shadowElevation = 4.dp, tonalElevation = 2.dp, modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onRevise(null) }) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = LocalChatLook.current.brand)
                Spacer(Modifier.width(4.dp))
                Text(tr("Überarbeiten …"), style = MaterialTheme.typography.labelMedium)
            }
            listOf(
                "Kürzer" to "Kürzer fassen, ohne Inhalt zu verlieren",
                "Formeller" to "Formeller und sachlicher formulieren",
                "Einfacher" to "Einfacher und verständlicher formulieren",
            ).forEach { (label, instruction) ->
                TextButton(onClick = { onRevise(tr(instruction)) }) { Text(tr(label), style = MaterialTheme.typography.labelMedium) }
            }
            IconButton(onClick = onComment, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.AddComment, contentDescription = tr("Kommentar"), modifier = Modifier.size(16.dp)) }
        }
    }
}

/** Seitenumbruch in the Seitenansicht (AP-43): the rest of the page stays empty, the text goes on on the next one. */
@Composable
internal fun PageBreakFill(heightPx: Float) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(with(density) { heightPx.coerceAtLeast(0f).toDp() }.coerceAtLeast(18.dp))) {
        MarkerLine(tr("Seitenumbruch"))
    }
}

/** Shown text of the page-setup lines the editor does not draw (Formatvorlagen). */
internal fun isHiddenSetting(markdown: String): Boolean =
    markdown.lines().filter { it.isNotBlank() }.let { ls -> ls.isNotEmpty() && ls.all { io.github.thepytales.llmhub.docs.text.Styles.isSetting(it) } }
