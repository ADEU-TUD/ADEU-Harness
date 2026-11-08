package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.creative.CanvasTextEditor
import io.github.thepytales.llmhub.creative.DesignEditorState
import io.github.thepytales.llmhub.creative.DesignGeometry
import io.github.thepytales.llmhub.creative.Handle
import io.github.thepytales.llmhub.creative.SlideCanvas
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.SlideElement
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val CanvasAccent = Color(0xFF2F57B2)
private val GuideColor = Color(0xFFE6007E)

/** What a drag on the slide canvas does. */
private sealed interface SlideDrag {
    data class Move(val start: List<DesignElement>, val from: Offset) : SlideDrag
    data class Resize(val start: DesignElement, val handle: Int) : SlideDrag
    data class Rotate(val start: DesignElement) : SlideDrag
    data class Marquee(val from: Offset, val to: Offset) : SlideDrag
}

/**
 * The free elements of a slide on the canvas of Erstellen › Gestalten (AP-50): the gestures run on a
 * [DesignEditorState] over the slide (one page of 338,67 × 190,5 mm, [SlideCanvas]), so moving with Einrasten at the
 * edges, the middle and the other elements, resizing at eight handles, turning at the handle above the frame (the
 * TUD-Shape in 45° steps), Umschalt+click and the drag rectangle for several elements and groups behave exactly as in
 * Gestalten. A double click on a text box types right on the slide, with formatting inside the box.
 *
 * [selection] holds the indices of the selected elements; [onLive] gets the elements while a drag runs (the slide
 * underneath shows them live) and null at its end; [onEditing] the text box being typed in (the slide leaves its text
 * out meanwhile); [onChange] the elements after a gesture, a key or a pause in typing.
 */
@Composable
internal fun SlideCanvasOverlay(
    elements: List<SlideElement>,
    selection: Set<Int>,
    onSelect: (Set<Int>) -> Unit,
    onLive: (List<SlideElement>?) -> Unit,
    onEditing: (Int?) -> Unit,
    onChange: (List<SlideElement>) -> Unit,
) {
    val state = remember { DesignEditorState(SlideCanvas.design(elements)) }
    val latest by rememberUpdatedState(elements)
    val latestSelection by rememberUpdatedState(selection)
    var drag by remember { mutableStateOf<SlideDrag?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var lastClick by remember { mutableStateOf(0L to Offset.Zero) }
    val focus = remember { FocusRequester() }
    // Typed text is saved after a short pause (like the fields of the slide) and when the typing ends.
    var pending by remember { mutableStateOf<Triple<Int, String, Boolean>?>(null) }
    fun flush() {
        val (i, raw, rich) = pending ?: return
        pending = null
        onChange(latest.mapIndexed { j, x -> if (j == i) x.copy(text = raw, rich = rich) else x })
    }
    LaunchedEffect(pending) { if (pending != null) { kotlinx.coroutines.delay(400); flush() } }
    LaunchedEffect(editing) { onEditing(editing) }
    // Changes from outside (panel, undo, the model) replace the canvas – never in the middle of a drag.
    LaunchedEffect(elements) { if (drag == null) state.replace(SlideCanvas.design(elements)) }
    LaunchedEffect(selection) { state.selection = SlideCanvas.ids(selection) }
    fun select(ids: Set<String>) {
        state.selection = ids
        onSelect(SlideCanvas.indices(ids))
    }
    fun commit() = onChange(SlideCanvas.fromDesign(latest, state.current.elements))

    BoxWithConstraints(
        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown || editing != null) return@onKeyEvent false
            val sel = latestSelection
            if (sel.isEmpty()) return@onKeyEvent false
            val step = if (ev.isShiftPressed) 10f else 1f
            fun nudge(dx: Float, dy: Float): Boolean {
                state.selection = SlideCanvas.ids(sel)
                state.move(dx, dy)
                commit()
                return true
            }
            when {
                ev.isCmd && ev.key == Key.G -> {
                    onChange(SlideCanvas.group(latest, sel, io.github.thepytales.llmhub.data.newDesignId(), apart = ev.isShiftPressed)); true
                }
                ev.key == Key.Delete || ev.key == Key.Backspace -> {
                    onChange(latest.filterIndexed { i, _ -> i !in sel }); onSelect(emptySet()); true
                }
                ev.key == Key.Escape -> { onSelect(emptySet()); true }
                ev.key == Key.DirectionLeft -> nudge(-step, 0f)
                ev.key == Key.DirectionRight -> nudge(step, 0f)
                ev.key == Key.DirectionUp -> nudge(0f, -step)
                ev.key == Key.DirectionDown -> nudge(0f, step)
                else -> false
            }
        },
    ) {
        val density = LocalDensity.current
        val viewW = constraints.maxWidth.toFloat()
        /** Pixels per millimetre of the slide. */
        val scale = viewW / SlideCanvas.W
        val handlePx = with(density) { 6f * this.density }
        fun toMm(p: Offset) = p / scale
        fun handleAt(p: Offset): Int? {
            val e = state.single ?: return null
            if (e.locked) return null
            return ((0..7).toList() + Handle.ROTATE).firstOrNull { h ->
                val pos = if (h == Handle.ROTATE) DesignGeometry.toPage(e, Offset(e.w / 2, -24f / scale)) else DesignGeometry.toPage(e, Handle.local(e, h))
                abs(pos.x * scale - p.x) <= handlePx && abs(pos.y * scale - p.y) <= handlePx
            }
        }
        Canvas(
            Modifier.fillMaxSize().pointerInput(scale) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flush()
                    editing = null
                    runCatching { focus.requestFocus() }
                    state.replace(SlideCanvas.design(latest))
                    state.selection = SlideCanvas.ids(latestSelection)
                    val shift = currentEvent.keyboardModifiers.isShiftPressed
                    val p = down.position
                    val mm = toMm(p)
                    val now = System.currentTimeMillis()
                    val double = now - lastClick.first < 400 && (lastClick.second - p).getDistance() < 6f
                    lastClick = now to p
                    val handle = handleAt(p)
                    val hit = DesignGeometry.hit(state.current, mm, 4f / scale)
                    drag = when {
                        handle == Handle.ROTATE -> state.single?.let { state.beginGesture(); SlideDrag.Rotate(it) }
                        handle != null -> state.single?.let { state.beginGesture(); SlideDrag.Resize(it, handle) }
                        hit != null -> {
                            // A click on one element of a group takes the whole group, as in Gestalten.
                            val members = state.withGroups(setOf(hit.id))
                            if (shift) select(if (hit.id in state.selection) state.selection - members else state.selection + members)
                            else if (hit.id !in state.selection) select(members)
                            // Double click on a text box: type right on the slide.
                            if (double && hit.kind == ElementKind.TEXT) {
                                select(setOf(hit.id))
                                editing = SlideCanvas.indices(setOf(hit.id)).firstOrNull()
                            }
                            val moving = state.selected.filter { !it.locked }
                            if (moving.isEmpty() || editing != null) null else { state.beginGesture(); SlideDrag.Move(moving, mm) }
                        }
                        else -> {
                            if (!shift) select(emptySet())
                            SlideDrag.Marquee(mm, mm)
                        }
                    }
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val pos = toMm(ch.position)
                        if ((ch.position - p).getDistance() > 2f) moved = true
                        when (val g = drag) {
                            // Strg moves freely (no Einrasten), Umschalt turns in 15° steps or keeps the proportions.
                            is SlideDrag.Move -> if (moved) state.dragMove(g.start, pos - g.from, 7f / scale, free = ev.keyboardModifiers.isCmd)
                            is SlideDrag.Resize -> state.dragResize(g.start, g.handle, pos, Handle.keepsRatio(g.start) != ev.keyboardModifiers.isShiftPressed)
                            is SlideDrag.Rotate -> state.dragRotate(g.start, pos, ev.keyboardModifiers.isShiftPressed)
                            is SlideDrag.Marquee -> drag = g.copy(to = pos)
                            null -> Unit
                        }
                        if (moved && drag !is SlideDrag.Marquee && drag != null) onLive(SlideCanvas.fromDesign(latest, state.current.elements))
                        ch.consume()
                    }
                    (drag as? SlideDrag.Marquee)?.let { g ->
                        if (moved) {
                            val r = Rect(min(g.from.x, g.to.x), min(g.from.y, g.to.y), max(g.from.x, g.to.x), max(g.from.y, g.to.y))
                            val inside = state.current.elements.filter { !it.locked && r.contains(DesignGeometry.bounds(it).topLeft) && r.contains(DesignGeometry.bounds(it).bottomRight) }
                            select(state.withGroups((if (shift) state.selection else emptySet()) + inside.map { it.id }))
                        }
                    }
                    val live = drag != null && drag !is SlideDrag.Marquee && moved
                    if (live) commit()
                    state.endGesture()
                    if (live) onLive(null)
                    drag = null
                }
            },
        ) {
            // Faint frames show where the free elements are; the selection gets frames and handles as in Gestalten.
            state.current.elements.forEach { e ->
                val c = DesignGeometry.corners(e).map { it * scale }
                val frame = Path().apply { moveTo(c[0].x, c[0].y); c.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                val selected = e.id in state.selection
                if (selected) drawPath(frame, CanvasAccent, style = Stroke(1.5f)) else drawPath(frame, CanvasAccent.copy(alpha = 0.2f), style = Stroke(1f))
            }
            state.single?.takeIf { !it.locked && editing == null }?.let { e ->
                (0..7).forEach { h ->
                    val pos = DesignGeometry.toPage(e, Handle.local(e, h)) * scale
                    drawRect(Color.White, pos - Offset(5f, 5f), Size(10f, 10f))
                    drawRect(CanvasAccent, pos - Offset(5f, 5f), Size(10f, 10f), style = Stroke(1.5f))
                }
                val top = DesignGeometry.toPage(e, Offset(e.w / 2, 0f)) * scale
                val rot = DesignGeometry.toPage(e, Offset(e.w / 2, -24f / scale)) * scale
                drawLine(CanvasAccent, top, rot, 1.2f)
                drawCircle(Color.White, 7f, rot)
                drawCircle(CanvasAccent, 7f, rot, style = Stroke(1.5f))
            }
            state.guides.forEach { g ->
                if (g.vertical) drawLine(GuideColor, Offset(g.at * scale, 0f), Offset(g.at * scale, size.height), 1f)
                else drawLine(GuideColor, Offset(0f, g.at * scale), Offset(size.width, g.at * scale), 1f)
            }
            (drag as? SlideDrag.Marquee)?.let { g ->
                val tl = Offset(min(g.from.x, g.to.x), min(g.from.y, g.to.y)) * scale
                val br = Offset(max(g.from.x, g.to.x), max(g.from.y, g.to.y)) * scale
                drawRect(CanvasAccent.copy(alpha = 0.08f), tl, Size(br.x - tl.x, br.y - tl.y))
                drawRect(CanvasAccent, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(1f))
            }
        }
        // Typing right on the slide (AP-50): in the box's font, size, colour and rotation, with formatting inside it.
        editing?.let { i ->
            val el = elements.getOrNull(i)?.let { e -> pending?.takeIf { it.first == i }?.let { e.copy(text = it.second, rich = it.third) } ?: e }
            if (el == null || el.kind != "text") LaunchedEffect(i) { editing = null }
            else {
                val cmPx = viewW / (SlideCanvas.W / 10f)
                val pt = with(density) { (cmPx * el.size * 0.03528f).toSp() }
                val color = parseHexColor(el.color) ?: Color(0xFF00008C)
                with(density) {
                    CanvasTextEditor(
                        raw = el.text, rich = el.rich,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif, fontSize = pt, lineHeight = pt * 1.2f, color = color,
                            fontWeight = if (el.bold) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = when (el.align) { "mitte" -> TextAlign.Center; "rechts" -> TextAlign.End; else -> TextAlign.Start },
                        ),
                        modifier = Modifier
                            .offset { IntOffset((el.x * cmPx).toInt(), (el.y * cmPx).toInt()) }
                            .size((el.w * cmPx).toFloat().toDp(), (el.h * cmPx).toFloat().toDp())
                            .graphicsLayer { rotationZ = el.rotation.toFloat() }
                            // The same inner margin as the text box on the slide.
                            .padding(2.dp),
                        onChange = { raw, rich -> pending = Triple(i, raw, rich) },
                        onDone = { flush(); editing = null; runCatching { focus.requestFocus() } },
                        accent = CanvasAccent,
                    )
                }
            }
        }
    }
}
