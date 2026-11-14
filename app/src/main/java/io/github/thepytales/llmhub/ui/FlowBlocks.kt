package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.docs.CellRich
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.text.ParaKind
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.docs.text.WordKeys
import kotlin.math.roundToInt

/**
 * Bilder und Tabellen anfassen (AP-125 d), like Word and Google Docs: a picture is selected by a click – a frame with
 * handles to drag its size (the aspect ratio stays), a bar for Ausrichtung, Bildunterschrift, Alternativtext, Ersetzen
 * and Löschen, its own Rechtsklick-Menü; a table is a real grid across the text width with column widths to drag, Tab
 * from cell to cell (in the last one a new row) and a Rechtsklick-Menü for rows and columns. Both move with
 * Alt+Umschalt+↑/↓ like paragraphs (a row inside a table).
 */

// ── Moving blocks ───────────────────────────────────────────────────────────────────────────────────

/**
 * Alt+Umschalt+↑/↓ on a picture or table: it changes place with the paragraph above or below it (or with the block
 * there when no text is between them).
 */
fun FlowState.moveBlock(seg: FlowSeg.Raw, up: Boolean): Boolean {
    val i = segs.indexOfFirst { it.id == seg.id }
    if (i < 0) return false
    val before = segs.getOrNull(i - 1) as? FlowSeg.Text
    val after = segs.getOrNull(i + 1) as? FlowSeg.Text
    val from = if (up) before else after
    val to = if (up) after else before
    if (from == null) return true
    if (from.rich.text.isEmpty()) {
        // Only an empty line between this block and the next one: they change places.
        val j = if (up) i - 2 else i + 2
        val other = segs.getOrNull(j) as? FlowSeg.Raw ?: return true
        checkpoint()
        segs[j] = segs[i].also { segs[i] = other }
        changed()
        return true
    }
    checkpoint()
    val parts = paragraphsOf(from.rich)
    val moved = if (up) parts.last() else parts.first()
    from.rich = joined(if (up) parts.dropLast(1) else parts.drop(1))
    if (to == null) {
        // No text on the other side yet (the block is the last one): it gets a text of its own.
        val t = FlowSeg.Text(newId(), moved)
        segs.add(if (up) i + 1 else i, t)
    } else {
        to.rich = if (to.rich.text.isEmpty()) moved else joined(if (up) listOf(moved) + paragraphsOf(to.rich) else paragraphsOf(to.rich) + moved)
    }
    changed()
    return true
}

/** Removes the block [seg] (Entf on a selected picture, *Tabelle löschen*). */
fun FlowState.removeBlock(seg: FlowSeg.Raw) {
    if (picked == seg.id) picked = null
    setRaw(seg, "")
}

// ── Bilder ──────────────────────────────────────────────────────────────────────────────────────────

/** A picture of the running text: at its width and place, selected with handles, its bar and its menu. */
@Composable
internal fun FlowImageBlock(state: FlowState, seg: FlowSeg.Raw, img: DocMarkup.Image, host: FlowHost, onMarkdown: () -> Unit) {
    val look = LocalChatLook.current
    val density = LocalDensity.current
    val bitmap = rememberDocImage(img.src)
    val picked = state.picked == seg.id
    val focus = remember { FocusRequester() }
    var menu by remember { mutableStateOf<Offset?>(null) }
    var describe by remember { mutableStateOf(false) }
    var captionFocus by remember { mutableStateOf(false) }
    val captionRequester = remember { FocusRequester() }
    /** While a handle is dragged: the width in percent shown (it is saved when the handle is let go). */
    var dragPct by remember(seg.markdown) { mutableStateOf<Float?>(null) }
    fun write(next: DocMarkup.Image) = state.setRaw(seg, next.markdown())
    fun pick() {
        state.picked = seg.id
        state.spanAnchor = null
        runCatching { focus.requestFocus() }
    }
    // Textumbruch (AP-125 e): the picture lies over the start of the text after it, which runs on beside it.
    val next = state.segs.getOrNull(state.segs.indexOfFirst { it.id == seg.id } + 1) as? FlowSeg.Text
    val wrapping = img.wrap != null && next != null
    DisposableEffect(next?.id, wrapping) { onDispose { next?.let { state.wraps.remove(it.id) } } }
    if (!wrapping && next != null && state.wraps.containsKey(next.id)) state.wraps.remove(next.id)
    val gapPx = with(density) { 14.dp.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .then(if (wrapping) Modifier.layout { m, c -> val p = m.measure(c); layout(p.width, 0) { p.place(0, 0) } } else Modifier)
            .padding(vertical = if (wrapping) 2.dp else 6.dp),
    ) {
        val full = maxWidth
        val aspect = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (4f / 3f)
        val pct = (dragPct ?: (img.width ?: 100).toFloat()).coerceIn(5f, 100f)
        var w = full * (pct / 100f)
        var h = w / aspect
        if (h > 520.dp) { h = 520.dp; w = h * aspect }
        val align = when (img.wrap ?: img.align) { "left" -> Alignment.Start; "right" -> Alignment.End; else -> Alignment.CenterHorizontally }
        val widthPx = with(density) { w.coerceAtLeast(if (img.alt.isNotBlank() || picked) 160.dp else 0.dp).toPx() }
        Column(
            Modifier.fillMaxWidth().onSizeChanged { size ->
                if (wrapping && next != null) {
                    val room = ImageWrap(img.wrap!!, widthPx + gapPx, size.height + gapPx / 2)
                    if (state.wraps[next.id] != room) state.wraps[next.id] = room
                }
            },
            horizontalAlignment = align,
        ) {
            Box(
                Modifier
                    .size(w, h)
                    .focusRequester(focus)
                    .focusable()
                    .semantics { contentDescription = img.altText.ifBlank { tr("Bild") } }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown || !picked) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.Delete || e.key == Key.Backspace -> { state.removeBlock(seg); true }
                            e.key == Key.Escape -> { state.picked = null; true }
                            e.isAltPressed && e.isShiftPressed && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> state.moveBlock(seg, e.key == Key.DirectionUp)
                            e.key == Key.Menu || e.isShiftPressed && e.key == Key.F10 -> { menu = Offset.Zero; true }
                            else -> false
                        }
                    }
                    .pointerInput(seg.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                    e.changes.forEach { it.consume() }
                                    pick()
                                    menu = e.changes.firstOrNull()?.position ?: Offset.Zero
                                }
                            }
                        }
                    }
                    .pointerInput(seg.id) { detectTapGestures(onTap = { pick() }, onDoubleTap = { pick(); captionFocus = true; runCatching { captionRequester.requestFocus() } }) },
            ) {
                if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { HintText(tr("Bild nicht gefunden: {0}", img.src.substringAfterLast('/'))) }
                }
                if (picked) {
                    Box(Modifier.fillMaxSize().border(2.dp, look.brand))
                    // Handles at the corners: dragging changes the width (the height follows the picture).
                    val fullPx = with(density) { full.toPx() }
                    listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { corner ->
                        val right = corner == Alignment.TopEnd || corner == Alignment.BottomEnd
                        Box(
                            Modifier
                                .align(corner)
                                .offset(if (right) 5.dp else (-5).dp, if (corner == Alignment.TopStart || corner == Alignment.TopEnd) (-5).dp else 5.dp)
                                .size(10.dp)
                                .background(Color.White)
                                .border(1.5.dp, look.brand)
                                .pointerHoverIcon(DiagonalResizeIcon)
                                .semantics { contentDescription = tr("Größe ändern") }
                                .pointerInput(seg.id, fullPx) {
                                    var px = 0f
                                    detectDragGestures(
                                        onDragStart = { px = with(density) { w.toPx() } },
                                        onDragEnd = {
                                            dragPct?.let { p -> write(img.copy(width = p.roundToInt().coerceIn(5, 100).takeIf { it < 100 })) }
                                            dragPct = null
                                        },
                                        onDragCancel = { dragPct = null },
                                    ) { change, drag ->
                                        change.consume()
                                        // A centred picture grows on both sides, so the handle stays under the mouse.
                                        val factor = if (img.align == null) 2f else 1f
                                        px += (if (right) drag.x else -drag.x) * factor
                                        dragPct = (px / fullPx * 100f).coerceIn(5f, 100f)
                                    }
                                },
                        )
                    }
                    // The width while dragging, like Word's size tip.
                    dragPct?.let { p ->
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.align(Alignment.Center)) {
                            Text("${p.roundToInt()} %", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                // The bar floats over the picture's upper edge, over the text, so nothing moves and the caption stays free.
                if (picked && dragPct == null) androidx.compose.ui.window.Popup(popupPositionProvider = AboveAnchor(with(density) { 8.dp.roundToPx() }), properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                    ImageBar(img, onAlign = { write(placed(img, it)) }, onWrap = { write(wrapped(img, img.wrap == null)) }, onCaption = { captionFocus = true; runCatching { captionRequester.requestFocus() } }, onDescribe = { describe = true }, onReplace = { replaceImage(state, seg, img, host) }, onDelete = { state.removeBlock(seg) })
                }
                menu?.let { at ->
                    Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) {
                        ImageMenu(state, seg, img, host, onCaption = { captionFocus = true; runCatching { captionRequester.requestFocus() } }, onDescribe = { describe = true }, onMarkdown = onMarkdown, onWrite = ::write, onDismiss = { menu = null })
                    }
                }
            }
            ImageCaption(img, picked || captionFocus, w, captionRequester, onFocus = { captionFocus = it; if (it) state.picked = seg.id }) { text -> write(img.copy(alt = text)) }
        }
    }
    if (describe) TextPrompt(tr("Alternativtext"), img.description ?: "", onDismiss = { describe = false }) { text ->
        describe = false
        write(img.copy(description = text.trim().takeIf { it.isNotEmpty() && it != img.alt }))
    }
}

/** Places a popup [gap] px above its anchor (the caption below stays free), centred on it; below it when there is no room. */
private class AboveAnchor(private val gap: Int) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (above >= 0) above else (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/** The picture at [side] ("left", "right" or null: centred): beside the text when it runs around it, else in its line. */
internal fun placed(img: DocMarkup.Image, side: String?): DocMarkup.Image = when {
    img.wrap != null && side != null -> img.copy(wrap = side, align = null)
    img.wrap != null -> img.copy(wrap = null, align = null)
    else -> img.copy(align = side)
}

/**
 * Textumbruch on or off (AP-125 e): on, the picture goes to its edge (right when it was centred) and, when it was wider
 * than 60 %, to 40 % so the text has room beside it; off, it keeps its edge as its place in a line of its own.
 */
internal fun wrapped(img: DocMarkup.Image, on: Boolean): DocMarkup.Image =
    if (on) img.copy(wrap = img.wrap ?: img.align ?: "right", align = null, width = (img.width ?: 100).let { if (it > 60) 40 else it })
    else img.copy(wrap = null, align = img.wrap)

/** *Bild ersetzen …*: the next picture chosen in the file dialog takes this one's place (width, place and caption stay). */
private fun replaceImage(state: FlowState, seg: FlowSeg.Raw, img: DocMarkup.Image, host: FlowHost) {
    host.imageInto = { src -> state.setRaw(seg, img.copy(src = src).markdown()) }
    host.onImage()
}

/** The floating bar of a selected picture: Ausrichtung, Bildunterschrift, Alternativtext, Ersetzen, Löschen. */
@Composable
private fun ImageBar(img: DocMarkup.Image, onAlign: (String?) -> Unit, onWrap: () -> Unit, onCaption: () -> Unit, onDescribe: () -> Unit, onReplace: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun pick(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String?) {
                val on = (img.wrap ?: img.align) == value
                IconButton(onClick = { onAlign(value) }, modifier = Modifier.size(34.dp).then(if (on) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)) else Modifier)) {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
                }
            }
            pick(Icons.Outlined.FormatAlignLeft, tr("Bild links"), "left")
            pick(Icons.Outlined.FormatAlignCenter, tr("Bild zentriert"), null)
            pick(Icons.Outlined.FormatAlignRight, tr("Bild rechts"), "right")
            // Textumbruch (AP-125 e): the text runs on beside the picture.
            val wrapOn = img.wrap != null
            TextButton(onClick = onWrap, modifier = if (wrapOn) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)) else Modifier) {
                Icon(Icons.AutoMirrored.Outlined.WrapText, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Text daneben"))
            }
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
            TextButton(onClick = onCaption) { Icon(Icons.Outlined.Title, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Bildunterschrift")) }
            TextButton(onClick = onDescribe) { Icon(Icons.AutoMirrored.Outlined.ShortText, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Alternativtext")) }
            TextButton(onClick = onReplace) { Icon(Icons.Outlined.Image, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Ersetzen")) }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Bild löschen"), modifier = Modifier.size(18.dp)) }
        }
    }
}

/** The caption under the picture, edited in place (like Word's Beschriftung); empty and selected: an invitation. */
@Composable
private fun ImageCaption(img: DocMarkup.Image, show: Boolean, width: Dp, requester: FocusRequester, onFocus: (Boolean) -> Unit, onDone: (String) -> Unit) {
    var value by remember(img.alt) { mutableStateOf(TextFieldValue(img.alt, TextRange(img.alt.length))) }
    var focused by remember { mutableStateOf(false) }
    if (!show && img.alt.isBlank() && !focused) return
    val style = MaterialTheme.typography.bodySmall.copy(
        fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = when (img.align) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center },
    )
    Box(Modifier.width(width.coerceAtLeast(160.dp)).padding(top = 4.dp)) {
        BasicTextField(
            value, { value = it.copy(text = it.text.replace("\n", " ")) },
            textStyle = style, singleLine = true, cursorBrush = SolidColor(LocalChatLook.current.brand),
            modifier = Modifier.fillMaxWidth().focusRequester(requester)
                .semantics { contentDescription = tr("Bildunterschrift") }
                // Enter takes the caption, Esc goes back to what it was.
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> { if (value.text.trim() != img.alt) onDone(value.text.trim()); true }
                        Key.Escape -> { value = TextFieldValue(img.alt, TextRange(img.alt.length)); true }
                        else -> false
                    }
                }
                .onFocusChanged {
                    if (focused && !it.isFocused && value.text.trim() != img.alt) onDone(value.text.trim())
                    focused = it.isFocused
                    onFocus(it.isFocused)
                },
        )
        if (value.text.isEmpty()) Text(tr("Bildunterschrift hinzufügen"), style = style.copy(color = MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth())
    }
}

/** Rechtsklick on a picture (like Word): Bild ersetzen, Bildunterschrift, Alternativtext, Größe, Ausrichtung, Löschen. */
@Composable
private fun ImageMenu(
    state: FlowState, seg: FlowSeg.Raw, img: DocMarkup.Image, host: FlowHost,
    onCaption: () -> Unit, onDescribe: () -> Unit, onMarkdown: () -> Unit, onWrite: (DocMarkup.Image) -> Unit, onDismiss: () -> Unit,
) {
    @Composable
    fun entry(label: String, keys: String = "", enabled: Boolean = true, onClick: () -> Unit) = MenuRow(label, keys, enabled) { onDismiss(); onClick() }
    DropdownMenu(true, onDismiss) {
        entry(tr("Bild ersetzen …")) { replaceImage(state, seg, img, host) }
        entry(tr("Bildunterschrift bearbeiten")) { onCaption() }
        entry(tr("Alternativtext …")) { onDescribe() }
        HorizontalDivider()
        listOf(25, 50, 75, 100).forEach { p ->
            entry((if ((img.width ?: 100) == p) "✓ " else "") + tr("Breite {0} %", p)) { onWrite(img.copy(width = p.takeIf { it < 100 })) }
        }
        HorizontalDivider()
        // Textumbruch like Word: in a line of its own (with its place) or at an edge with the text beside it.
        entry((if (img.wrap == null && img.align == "left") "✓ " else "") + tr("Mit Text in Zeile: links")) { onWrite(img.copy(wrap = null, align = "left")) }
        entry((if (img.wrap == null && img.align == null) "✓ " else "") + tr("Mit Text in Zeile: zentriert")) { onWrite(img.copy(wrap = null, align = null)) }
        entry((if (img.wrap == null && img.align == "right") "✓ " else "") + tr("Mit Text in Zeile: rechts")) { onWrite(img.copy(wrap = null, align = "right")) }
        entry((if (img.wrap == "left") "✓ " else "") + tr("Links, Text rechts daneben")) { onWrite(wrapped(img.copy(align = "left"), true)) }
        entry((if (img.wrap == "right") "✓ " else "") + tr("Rechts, Text links daneben")) { onWrite(wrapped(img.copy(align = "right"), true)) }
        HorizontalDivider()
        entry(tr("Nach oben verschieben"), tr("Alt+Umschalt+↑")) { state.moveBlock(seg, up = true) }
        entry(tr("Nach unten verschieben"), tr("Alt+Umschalt+↓")) { state.moveBlock(seg, up = false) }
        entry(tr("Als Markdown bearbeiten …")) { onMarkdown() }
        entry(tr("Bild löschen"), tr("Entf")) { state.removeBlock(seg) }
    }
}

// ── Tabellen ────────────────────────────────────────────────────────────────────────────────────────

/** Spaltenbreiten in percent for [cols] columns: those of the table or all equal. */
internal fun columnWidths(widths: List<Int>?, cols: Int): List<Float> =
    widths?.takeIf { it.size == cols }?.map { it.toFloat() } ?: List(cols) { 100f / cols }

/** [rows] with a new row at [at] (as many cells as columns). */
internal fun withRow(rows: List<List<String>>, at: Int): List<List<String>> {
    val cols = rows.maxOfOrNull { it.size } ?: 1
    return rows.toMutableList().apply { add(at.coerceIn(0, size), List(cols) { "" }) }
}

/** [rows] and [widths] with a new column at [at]; it takes its room from the widest columns. */
internal fun withColumn(rows: List<List<String>>, widths: List<Float>, at: Int): Pair<List<List<String>>, List<Float>> {
    val cols = widths.size
    val k = at.coerceIn(0, cols)
    val r = rows.map { row -> (0 until cols).map { row.getOrElse(it) { "" } }.toMutableList().apply { add(k, "") } }
    val share = 100f / (cols + 1)
    val w = widths.map { it * (100f - share) / 100f }.toMutableList().apply { add(k, share) }
    return r to w
}

/** [rows] and [widths] without column [at]; its room goes to the others. */
internal fun withoutColumn(rows: List<List<String>>, widths: List<Float>, at: Int): Pair<List<List<String>>, List<Float>> {
    if (widths.size <= 1) return rows to widths
    val r = rows.map { row -> row.filterIndexed { i, _ -> i != at } }
    val rest = widths.filterIndexed { i, _ -> i != at }
    val sum = rest.sum().coerceAtLeast(1f)
    return r to rest.map { it * 100f / sum }
}

/** The widths to store (null when equal), rounded to whole percent that add up to 100. */
internal fun storedWidths(w: List<Float>): List<Int>? = DocBlocks.widthsOf(w.map { (it * 10).roundToInt() })

/**
 * A table of the running text as a real grid across the text width: header row shaded, cells edited in place with
 * their formatting (bold, italic … as in the cell, AP-48), column borders to drag, Tab to the next cell.
 */
@Composable
internal fun FlowTableBlock(state: FlowState, seg: FlowSeg.Raw, paging: TablePaging?, zoom: Float, onMarkdown: () -> Unit) {
    val rows = remember(seg.markdown) { DocBlocks.tableRows(seg.markdown).ifEmpty { listOf(listOf("")) } }
    val cols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val saved = remember(seg.markdown) { columnWidths(DocBlocks.tableWidths(seg.markdown), cols) }
    /** While a column border is dragged: the widths shown (saved when it is let go). */
    var dragWidths by remember(seg.markdown) { mutableStateOf<List<Float>?>(null) }
    val widths = dragWidths ?: saved
    val density = LocalDensity.current
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val headBg = MaterialTheme.colorScheme.surfaceVariant
    val body = state.sheet.of(ParaKind.NORMAL)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = ptSp(body.size, zoom))
    val focus = remember(seg.id) { mutableStateMapOf<Pair<Int, Int>, FocusRequester>() }
    fun req(r: Int, c: Int) = focus.getOrPut(r to c) { FocusRequester() }
    /** The cell that takes the focus once it is shown (after Tab in the last cell, a new row or column). */
    var focusCell by remember(seg.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var here by remember(seg.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var menu by remember(seg.id) { mutableStateOf<Pair<Pair<Int, Int>, Offset>?>(null) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val gap = 4.dp
    val gapPx = with(density) { gap.toPx() }
    /** Height of the rows (px), for the borders to drag. */
    var gridHeight by remember(seg.id) { mutableStateOf(0) }

    fun write(next: List<List<String>>, w: List<Float> = widths, group: Boolean = false) =
        state.setRaw(seg, DocBlocks.table(next, storedWidths(w)), group = group)

    fun go(r: Int, c: Int) {
        val target = r to c
        if (focus[target] != null) runCatching { focus[target]!!.requestFocus() } else focusCell = target
    }

    /** Leaves the table into the text before or after it. */
    fun leave(after: Boolean) {
        val i = state.segs.indexOfFirst { it.id == seg.id }
        val t = (if (after) state.segs.drop(i + 1) else state.segs.take(i).reversed()).firstOrNull { it is FlowSeg.Text } as? FlowSeg.Text ?: return
        state.focusPiece(t, TextRange(if (after) 0 else t.rich.text.length), keepSpan = false)
    }

    LaunchedEffect(rows.size, cols, focusCell) {
        val f = focusCell ?: return@LaunchedEffect
        focusCell = null
        runCatching { focus[f]?.requestFocus() }
    }

    @Composable
    fun cell(r: Int, c: Int, row: List<String>, readOnly: Boolean, width: Dp) {
        val raw = row.getOrElse(c) { "" }
        var value by remember(seg.id, r, c, readOnly) { mutableStateOf(TextFieldValue(raw)) }
        if (value.text != raw) value = TextFieldValue(raw, TextRange(raw.length.coerceAtMost(value.selection.end.coerceAtMost(raw.length))))
        fun set(v: TextFieldValue) {
            val fixed = SheetRichText.fixEdit(value, v)
            value = fixed
            if (fixed.text != raw) write(rows.mapIndexed { i, rr -> if (i == r) (0 until cols).map { k -> if (k == c) fixed.text.replace("\n", " ").replace("|", "/") else rr.getOrElse(k) { "" } } else rr }, group = true)
        }
        /** Strg+B/I/U in a cell: the marked part, or the word at the cursor (as in the running text). */
        fun mark(m: CellRich.Mark): Boolean {
            val p = CellRich.parse(value.text)
            var a = p.plainOffset(value.selection.min)
            var b = p.plainOffset(value.selection.max)
            if (a == b) WordKeys.wordAt(p.plain, a).let { if (!it.isEmpty()) { a = it.first; b = it.last + 1 } else { a = 0; b = p.plain.length } }
            val next = CellRich.toggle(value.text, a, b, m)
            set(TextFieldValue(next, TextRange(CellRich.parse(next).rawOffset(a), CellRich.parse(next).rawOffset(b))))
            return true
        }
        val head = r == 0
        Box(
            Modifier
                .width(width)
                .fillMaxHeight()
                .heightIn(min = 30.dp)
                .then(if (head) Modifier.background(headBg) else Modifier)
                .drawBehind {
                    // The right and lower border of the cell; the table draws the upper and left one.
                    val s = 1.dp.toPx()
                    drawLine(line, Offset(size.width - s / 2, 0f), Offset(size.width - s / 2, size.height), s)
                    drawLine(line, Offset(0f, size.height - s / 2), Offset(size.width, size.height - s / 2), s)
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            // Text at the top of the cell, as in Word.
            contentAlignment = Alignment.TopStart,
        ) {
            OwnTextMenu {
                BasicTextField(
                    value, ::set,
                    readOnly = readOnly,
                    textStyle = if (head) textStyle.copy(fontWeight = FontWeight.SemiBold) else textStyle,
                    cursorBrush = SolidColor(LocalChatLook.current.brand),
                    visualTransformation = SheetRichText.Transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (readOnly) Modifier else Modifier.focusRequester(req(r, c)))
                        .semantics { contentDescription = tr("Zelle {0}, {1}", r + 1, c + 1) }
                        .onFocusChanged { if (it.isFocused) { here = r to c; state.picked = null; state.spanAnchor = null } }
                        .pointerInput(r, c) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                        e.changes.forEach { it.consume() }
                                        go(r, c)
                                        menu = (r to c) to (e.changes.firstOrNull()?.position ?: Offset.Zero)
                                    }
                                }
                            }
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || readOnly) return@onPreviewKeyEvent false
                            val cmd = e.isCtrlPressed || e.isMetaPressed
                            val atStart = value.selection.collapsed && value.selection.start == 0
                            val atEnd = value.selection.collapsed && value.selection.start == value.text.length
                            when {
                                // Tab and Umschalt+Tab from cell to cell; Tab in the last cell adds a row (like Word).
                                e.key == Key.Tab && !cmd && !e.isAltPressed -> {
                                    if (e.isShiftPressed) { if (c > 0) go(r, c - 1) else if (r > 0) go(r - 1, cols - 1) }
                                    else if (c + 1 < cols) go(r, c + 1)
                                    else if (r + 1 < rows.size) go(r + 1, 0)
                                    else { write(withRow(rows, rows.size)); focusCell = rows.size to 0 }
                                    true
                                }
                                e.key == Key.Enter || e.key == Key.NumPadEnter -> { if (r + 1 < rows.size) go(r + 1, c) else leave(after = true); true }
                                e.key == Key.DirectionDown && !e.isShiftPressed && !e.isAltPressed -> { if (r + 1 < rows.size) go(r + 1, c) else leave(after = true); true }
                                e.key == Key.DirectionUp && !e.isShiftPressed && !e.isAltPressed -> { if (r > 0) go(r - 1, c) else leave(after = false); true }
                                e.key == Key.DirectionRight && atEnd && !e.isShiftPressed && !cmd -> { if (c + 1 < cols) { go(r, c + 1); true } else false }
                                e.key == Key.DirectionLeft && atStart && !e.isShiftPressed && !cmd -> { if (c > 0) { go(r, c - 1); true } else false }
                                // Alt+Umschalt+↑/↓ moves the row (like Word); the header row stays on top.
                                e.isAltPressed && e.isShiftPressed && e.key == Key.DirectionUp -> { if (r > 1) { write(rows.toMutableList().apply { add(r - 1, removeAt(r)) }); focusCell = (r - 1) to c }; true }
                                e.isAltPressed && e.isShiftPressed && e.key == Key.DirectionDown -> { if (r in 1 until rows.size - 1) { write(rows.toMutableList().apply { add(r + 1, removeAt(r)) }); focusCell = (r + 1) to c }; true }
                                cmd && !e.isAltPressed && !e.isShiftPressed && e.key == Key.B -> mark(CellRich.Mark.BOLD)
                                cmd && !e.isAltPressed && !e.isShiftPressed && e.key == Key.I -> mark(CellRich.Mark.ITALIC)
                                cmd && !e.isAltPressed && !e.isShiftPressed && e.key == Key.U -> mark(CellRich.Mark.UNDERLINE)
                                e.key == Key.Menu || e.isShiftPressed && e.key == Key.F10 -> { menu = (r to c) to Offset.Zero; true }
                                else -> false
                            }
                        },
                )
            }
            menu?.takeIf { it.first == (r to c) }?.let { (_, at) ->
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) {
                    TableMenu(state, seg, rows, widths, r, c, value, onValue = ::set, write = { nr, nw, f -> write(nr, nw); focusCell = f }, onMarkdown = onMarkdown, onDismiss = { menu = null })
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().hoverable(hover)) {
        val full = maxWidth
        val fullPx = with(density) { full.toPx() }
        val colDp = widths.map { full * (it / 100f) }

        @Composable
        fun rowOf(r: Int, row: List<String>, readOnly: Boolean) = Row(
            // All cells of a row as high as its highest one, so the lines of the grid meet.
            Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min).drawBehind {
                val s = 1.dp.toPx()
                drawLine(line, Offset(s / 2, 0f), Offset(s / 2, size.height), s)
                if (r == 0 || readOnly) drawLine(line, Offset(0f, s / 2), Offset(size.width, s / 2), s)
            },
        ) { (0 until cols).forEach { c -> cell(r, c, row, readOnly, colDp[c]) } }

        /** Room before row [r] on a new page (Seitenansicht), with the header row repeated at its end when asked. */
        @Composable
        fun room(r: Int) {
            val p = paging ?: return
            val push = p.push(r)
            if (push <= 0f) return
            val header = if (p.repeats(r)) p.headerHeight() else 0f
            Spacer(Modifier.height(with(density) { (push - header).coerceAtLeast(0f).toDp() }))
            if (header > 0f) Box(Modifier.height(with(density) { header.toDp() }).semantics { contentDescription = tr("Wiederholte Kopfzeile") }) { rowOf(0, rows[0], readOnly = true) }
        }

        Column {
            Column(Modifier.onGloballyPositioned { gridHeight = it.size.height }) {
            rows.forEachIndexed { r, row ->
                Column {
                    room(r)
                    Box(if (paging != null) Modifier.onGloballyPositioned { paging.measuredRow(r, rows.size, it.size.height + if (r == rows.lastIndex) gapPx else 0f) } else Modifier) {
                        rowOf(r, row, readOnly = false)
                    }
                }
            }
            }
            Column {
                room(rows.size)
                // „+“ am Rand: a row or a column more – visible while the mouse is over the table or a cell is in use.
                val shown = hovered || here != null
                Row(
                    Modifier.padding(top = gap).height(28.dp).then(if (paging != null) Modifier.onGloballyPositioned { paging.measuredBar(it.size.height.toFloat()) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (shown) {
                        @Composable
                        fun add(label: String, onClick: () -> Unit) = Text(
                            label, style = MaterialTheme.typography.labelMedium, color = LocalChatLook.current.brand,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                        add(tr("+ Zeile")) { write(withRow(rows, rows.size)); focusCell = rows.size to 0 }
                        add(tr("+ Spalte")) { val (nr, nw) = withColumn(rows, widths, cols); write(nr, nw); focusCell = 0 to cols }
                    }
                }
            }
        }
        // Column borders to drag (like Word): the columns left and right of it share their room.
        var x = 0.dp
        for (k in 0 until cols - 1) {
            x += colDp[k]
            val at = x
            Box(
                Modifier
                    .offset(at - 4.dp, 0.dp)
                    .width(8.dp)
                    .height(with(density) { gridHeight.toDp() })
                    .pointerHoverIcon(ColumnResizeIcon)
                    .semantics { contentDescription = tr("Spaltenbreite ändern") }
                    .pointerInput(k, fullPx, cols) {
                        detectDragGestures(
                            onDragEnd = { dragWidths?.let { w -> write(rows, w) }; dragWidths = null },
                            onDragCancel = { dragWidths = null },
                        ) { change, drag ->
                            change.consume()
                            val now = (dragWidths ?: saved).toMutableList()
                            val d = drag.x / fullPx * 100f
                            val min = 5f
                            val a = (now[k] + d).coerceIn(min, now[k] + now[k + 1] - min)
                            now[k + 1] = now[k] + now[k + 1] - a
                            now[k] = a
                            dragWidths = now
                        }
                    },
            )
        }
    }
}

/** Rechtsklick in a table cell (like Word): cut/copy/paste, rows and columns, widths, the whole table. */
@Composable
private fun TableMenu(
    state: FlowState, seg: FlowSeg.Raw, rows: List<List<String>>, widths: List<Float>, r: Int, c: Int,
    value: TextFieldValue, onValue: (TextFieldValue) -> Unit,
    write: (List<List<String>>, List<Float>, Pair<Int, Int>?) -> Unit, onMarkdown: () -> Unit, onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val cols = widths.size
    @Composable
    fun entry(label: String, keys: String = "", enabled: Boolean = true, onClick: () -> Unit) = MenuRow(label, keys, enabled) { onDismiss(); onClick() }
    val sel = value.selection
    DropdownMenu(true, onDismiss) {
        entry(tr("Ausschneiden"), tr("Strg+X"), enabled = !sel.collapsed) {
            clipboard.setText(AnnotatedString(value.text.substring(sel.min, sel.max)))
            onValue(TextFieldValue(value.text.removeRange(sel.min, sel.max), TextRange(sel.min)))
        }
        entry(tr("Kopieren"), tr("Strg+C"), enabled = !sel.collapsed) { clipboard.setText(AnnotatedString(value.text.substring(sel.min, sel.max))) }
        entry(tr("Einfügen"), tr("Strg+V")) {
            val t = clipboard.getText()?.text?.replace("\n", " ") ?: return@entry
            onValue(TextFieldValue(value.text.replaceRange(sel.min, sel.max, t), TextRange(sel.min + t.length)))
        }
        HorizontalDivider()
        entry(tr("Zeile oberhalb einfügen"), enabled = r > 0) { write(withRow(rows, r), widths, r to c) }
        entry(tr("Zeile unterhalb einfügen")) { write(withRow(rows, r + 1), widths, (r + 1) to c) }
        entry(tr("Spalte links einfügen")) { val (nr, nw) = withColumn(rows, widths, c); write(nr, nw, r to c) }
        entry(tr("Spalte rechts einfügen")) { val (nr, nw) = withColumn(rows, widths, c + 1); write(nr, nw, r to (c + 1)) }
        HorizontalDivider()
        entry(tr("Zeile löschen"), enabled = r > 0) { write(rows.filterIndexed { i, _ -> i != r }, widths, (r - 1).coerceAtLeast(0) to c) }
        entry(tr("Spalte löschen"), enabled = cols > 1) { val (nr, nw) = withoutColumn(rows, widths, c); write(nr, nw, r to (c - 1).coerceAtLeast(0)) }
        entry(tr("Spaltenbreiten angleichen"), enabled = storedWidths(widths) != null) { write(rows, List(cols) { 100f / cols }, r to c) }
        HorizontalDivider()
        entry(tr("Zeile nach oben"), tr("Alt+Umschalt+↑"), enabled = r > 1) { write(rows.toMutableList().apply { add(r - 1, removeAt(r)) }, widths, (r - 1) to c) }
        entry(tr("Zeile nach unten"), tr("Alt+Umschalt+↓"), enabled = r in 1 until rows.size - 1) { write(rows.toMutableList().apply { add(r + 1, removeAt(r)) }, widths, (r + 1) to c) }
        entry(tr("Tabelle nach oben verschieben")) { state.moveBlock(seg, up = true) }
        entry(tr("Tabelle nach unten verschieben")) { state.moveBlock(seg, up = false) }
        HorizontalDivider()
        entry(tr("Als Markdown bearbeiten …")) { onMarkdown() }
        entry(tr("Tabelle löschen")) { state.removeBlock(seg) }
    }
}
