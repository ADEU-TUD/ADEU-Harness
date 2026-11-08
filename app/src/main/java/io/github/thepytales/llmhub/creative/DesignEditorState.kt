package io.github.thepytales.llmhub.creative

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignPage
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.data.newDesignId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** Geometry of elements: rotation around the centre, hit tests, bounds (all in mm of the page). */
object DesignGeometry {
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

    /** A point of the page in the element's own box (before rotation). */
    fun toLocal(e: DesignElement, p: Offset): Offset {
        val a = rad(-e.rotation)
        val dx = p.x - e.cx
        val dy = p.y - e.cy
        return Offset((dx * cos(a) - dy * sin(a)).toFloat() + e.w / 2, (dx * sin(a) + dy * cos(a)).toFloat() + e.h / 2)
    }

    /** A point of the element's own box on the page. */
    fun toPage(e: DesignElement, l: Offset): Offset {
        val a = rad(e.rotation)
        val dx = l.x - e.w / 2
        val dy = l.y - e.h / 2
        return Offset((dx * cos(a) - dy * sin(a)).toFloat() + e.cx, (dx * sin(a) + dy * cos(a)).toFloat() + e.cy)
    }

    fun isLine(e: DesignElement) = e.kind == ElementKind.LINE || e.kind == ElementKind.ARROW

    /** Corners on the page (top left, top right, bottom right, bottom left). */
    fun corners(e: DesignElement): List<Offset> = listOf(Offset(0f, 0f), Offset(e.w, 0f), Offset(e.w, e.h), Offset(0f, e.h)).map { toPage(e, it) }

    /** Axis-parallel bounds of the (rotated) element. */
    fun bounds(e: DesignElement): Rect {
        val c = corners(e)
        return Rect(c.minOf { it.x }, c.minOf { it.y }, c.maxOf { it.x }, c.maxOf { it.y })
    }

    fun bounds(list: List<DesignElement>): Rect? = list.map(::bounds).reduceOrNull { a, b -> Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom)) }

    /** Whether [p] hits [e]; [tolerance] in mm (lines need a little room). */
    fun hits(e: DesignElement, p: Offset, tolerance: Float): Boolean {
        val l = toLocal(e, p)
        if (isLine(e)) {
            val len2 = e.w * e.w + e.h * e.h
            val t = if (len2 <= 0f) 0f else ((l.x * e.w + l.y * e.h) / len2).coerceIn(0f, 1f)
            return hypot(l.x - t * e.w, l.y - t * e.h) <= max(tolerance, e.strokeWidth)
        }
        return l.x >= -tolerance && l.y >= -tolerance && l.x <= e.w + tolerance && l.y <= e.h + tolerance
    }

    /** The topmost element under [p] (locked ones only if nothing else is there). */
    fun hit(page: DesignPage, p: Offset, tolerance: Float): DesignElement? {
        val hits = page.elements.asReversed().filter { hits(it, p, tolerance) }
        return hits.firstOrNull { !it.locked } ?: hits.firstOrNull()
    }
}

/** A handle of the selection frame: 0–7 clockwise from the top left corner, [ROTATE] above the frame, [START]/[END] of lines. */
object Handle {
    const val ROTATE = 8
    const val START = 9
    const val END = 10

    /** Position of handle [h] in the element's own box. */
    fun local(e: DesignElement, h: Int): Offset = when (h) {
        0 -> Offset(0f, 0f)
        1 -> Offset(e.w / 2, 0f)
        2 -> Offset(e.w, 0f)
        3 -> Offset(e.w, e.h / 2)
        4 -> Offset(e.w, e.h)
        5 -> Offset(e.w / 2, e.h)
        6 -> Offset(0f, e.h)
        7 -> Offset(0f, e.h / 2)
        START -> Offset(0f, 0f)
        END -> Offset(e.w, e.h)
        else -> Offset(e.w / 2, 0f)
    }

    /** Elements whose proportions stay when resized at a corner. */
    fun keepsRatio(e: DesignElement) = e.kind == ElementKind.IMAGE || e.kind == ElementKind.ICON || e.kind == ElementKind.QR || e.kind == ElementKind.TUD_SHAPE
}

/** A guide line shown while moving: vertical at [x] or horizontal at [y] (mm). */
data class Guide(val vertical: Boolean, val at: Float)

/**
 * State of the design editor (AP-24): the design, the page and the selection, undo/redo, and the operations of the
 * canvas and the panels. All changes go through [edit], which keeps the history (coalescing typing in one field).
 */
class DesignEditorState(initial: Design) {
    var design by mutableStateOf(initial)
        private set
    var page by mutableIntStateOf(0)
    var selection by mutableStateOf<Set<String>>(emptySet())
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    /** Size of the canvas in pixels, set by the canvas; needed to zoom around the middle and keep the page in view. */
    var viewSize = Size.Zero

    private val pageSize get() = Size(design.width, design.height)

    /** Zooms by [factor] around [anchor] (canvas pixels; null = middle of the canvas), see [DesignViewport]. */
    fun zoomBy(factor: Float, anchor: Offset? = null) {
        val (z, p) = DesignViewport.zoomAt(viewSize, pageSize, zoom, pan, factor, anchor)
        zoom = z
        pan = p
    }

    /** Moves the page to [to] (shift from the centred position), keeping part of it in view. */
    fun panTo(to: Offset) {
        pan = DesignViewport.clampPan(viewSize, pageSize, zoom, to)
    }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
    }
    var guides by mutableStateOf<List<Guide>>(emptyList())
    /** Increased to put the cursor into the text field of the panel (double click on a text). */
    var focusText by mutableIntStateOf(0)
    /** The text box being typed in right on the canvas (AP-50), or null. */
    var inlineText by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    /** Changes since the last save. */
    var version by mutableIntStateOf(0)
        private set

    private val undoStack = ArrayDeque<Design>()
    private val redoStack = ArrayDeque<Design>()
    private var lastKey: String? = null
    private var lastAt = 0L

    /** Elements copied with Strg+C. */
    var clipboard: List<DesignElement> = emptyList()

    val current: DesignPage get() = design.page(page)
    val selected: List<DesignElement> get() = current.elements.filter { it.id in selection }
    val single: DesignElement? get() = selected.singleOrNull()

    private fun flags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** Takes [d] from outside (the AI changed the design while it was open). */
    fun replace(d: Design) {
        design = d
        page = page.coerceIn(0, d.pages.size - 1)
        selection = selection.filter { id -> d.page(page).elements.any { it.id == id } }.toSet()
    }

    /**
     * Changes the design. [key] coalesces repeated changes of the same thing (typing, dragging a slider) into one
     * step of the history; [record] = false for the moves of a running drag (its start was recorded).
     */
    fun edit(key: String? = null, record: Boolean = true, transform: (Design) -> Design) {
        val next = transform(design)
        if (next == design) return
        val now = System.currentTimeMillis()
        if (record && !(key != null && key == lastKey && now - lastAt < 1500)) {
            undoStack.addLast(design)
            if (undoStack.size > 200) undoStack.removeFirst()
            redoStack.clear()
        }
        lastKey = key
        lastAt = now
        design = next
        version++
        flags()
    }

    /** Records the state before a drag; the moves then use record = false. */
    fun beginGesture() {
        undoStack.addLast(design)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        lastKey = null
        flags()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(design)
        design = prev
        page = page.coerceIn(0, design.pages.size - 1)
        selection = selection.filter { id -> current.elements.any { it.id == id } }.toSet()
        lastKey = null
        version++
        flags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(design)
        design = next
        page = page.coerceIn(0, design.pages.size - 1)
        lastKey = null
        version++
        flags()
    }

    private fun mapPage(d: Design, transform: (DesignPage) -> DesignPage) = d.copy(pages = d.pages.mapIndexed { i, p -> if (i == page) transform(p) else p })

    fun updateElements(ids: Set<String>, key: String? = null, record: Boolean = true, transform: (DesignElement) -> DesignElement) =
        edit(key, record) { d -> mapPage(d) { p -> p.copy(elements = p.elements.map { if (it.id in ids) transform(it) else it }) } }

    fun updateSelected(key: String? = null, transform: (DesignElement) -> DesignElement) = updateElements(selection, key, transform = transform)

    fun setBackground(color: String) = edit("bg") { d -> mapPage(d) { it.copy(background = color) } }

    /** Adds [e] in the middle of the visible page (unless placed) and selects it. */
    fun add(e: DesignElement, centered: Boolean = true) {
        val placed = if (centered) e.copy(x = (design.width - e.w) / 2, y = (design.height - e.h) / 2) else e
        edit { d -> mapPage(d) { it.copy(elements = it.elements + placed) } }
        selection = setOf(placed.id)
    }

    fun delete(ids: Set<String> = selection) {
        if (ids.isEmpty()) return
        edit { d -> mapPage(d) { p -> p.copy(elements = p.elements.filter { it.id !in ids }) } }
        selection = emptySet()
    }

    fun duplicate(ids: Set<String> = selection) {
        val copies = current.elements.filter { it.id in ids }.map { it.copy(id = newDesignId(), x = it.x + 5f, y = it.y + 5f, slot = "", locked = false) }
        if (copies.isEmpty()) return
        edit { d -> mapPage(d) { it.copy(elements = it.elements + copies) } }
        selection = copies.map { it.id }.toSet()
    }

    fun copy() {
        clipboard = selected
    }

    fun paste() {
        if (clipboard.isEmpty()) return
        val copies = clipboard.map { it.copy(id = newDesignId(), x = it.x + 5f, y = it.y + 5f, slot = "") }
        edit { d -> mapPage(d) { it.copy(elements = it.elements + copies) } }
        selection = copies.map { it.id }.toSet()
        clipboard = copies
    }

    /** Layer order: [toFront] = to the very top/bottom, otherwise one step. */
    fun reorder(up: Boolean, all: Boolean) {
        val ids = withGroups(selection)
        if (ids.isEmpty()) return
        edit { d -> mapPage(d) { p -> p.copy(elements = CanvasOps.reordered(p.elements, ids, up, all)) } }
    }

    fun move(dx: Float, dy: Float, key: String = "nudge") =
        updateElements(selected.filter { !it.locked }.map { it.id }.toSet(), key) { it.copy(x = it.x + dx, y = it.y + dy) }

    /**
     * Aligns the selection: with one element to the page, with several to their common bounds. [mode]: left, hcenter,
     * right, top, vcenter, bottom; distributeH/distributeV spread three or more evenly.
     */
    fun align(mode: String) {
        val ids = selected.filter { !it.locked }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        val next = CanvasOps.aligned(current.elements, ids, mode, design.width, design.height)
        if (next == current.elements) return
        val byId = next.associateBy { it.id }
        updateElements(ids) { byId[it.id] ?: it }
    }

    /** Groups the selection (two or more elements): they move, align and get selected together. */
    fun group() {
        if (selection.size < 2) return
        val id = newDesignId()
        updateElements(selection) { it.copy(group = id) }
    }

    /** Takes the selected groups apart. */
    fun ungroup() = updateElements(selection) { it.copy(group = "") }

    /** [ids] with all members of their groups (a click on one element of a group selects the whole group). */
    fun withGroups(ids: Set<String>): Set<String> = CanvasOps.withGroups(current.elements, ids)

    // ── Pages ──────────────────────────────────────────────────────────────────────────────────────────

    fun addPage(copyCurrent: Boolean) {
        val p = if (copyCurrent) current.copy(id = newDesignId(), elements = current.elements.map { it.copy(id = newDesignId()) }) else DesignPage(background = current.background)
        edit { d -> d.copy(pages = d.pages.toMutableList().apply { add(page + 1, p) }) }
        page += 1
        selection = emptySet()
    }

    fun deletePage() {
        if (design.pages.size <= 1) return
        edit { d -> d.copy(pages = d.pages.filterIndexed { i, _ -> i != page }) }
        page = page.coerceAtMost(design.pages.size - 1)
        selection = emptySet()
    }

    fun movePage(by: Int) {
        val to = page + by
        if (to !in design.pages.indices) return
        edit { d -> d.copy(pages = d.pages.toMutableList().apply { add(to, removeAt(page)) }) }
        page = to
    }

    /** Changes the format: positions and sizes scale with the page, fonts with the smaller factor. */
    fun changeFormat(id: String, width: Float, height: Float) {
        val sx = width / design.width
        val sy = height / design.height
        val sf = min(sx, sy)
        edit { d ->
            d.copy(format = id, width = width, height = height).mapElements { e ->
                e.copy(x = e.x * sx, y = e.y * sy, w = e.w * sx, h = e.h * sy, fontSize = e.fontSize * sf, strokeWidth = e.strokeWidth * sf, radius = e.radius * sf, padding = e.padding * sf)
            }
        }
    }

    // ── Gestures ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Moves the elements of [start] (their state when the drag began) by [delta] mm, snapping the bounds of the group
     * to the page's edges and middle and to the other elements within [snap] mm; shows the guides.
     */
    fun dragMove(start: List<DesignElement>, delta: Offset, snap: Float, free: Boolean) {
        val movable = start.filter { !it.locked }
        if (movable.isEmpty()) return
        var dx = delta.x
        var dy = delta.y
        val shown = mutableListOf<Guide>()
        if (!free) {
            val b = DesignGeometry.bounds(movable)!!.translate(dx, dy)
            val ids = movable.map { it.id }.toSet()
            val others = current.elements.filter { it.id !in ids }.map(DesignGeometry::bounds)
            val xs = listOf(0f, design.width / 2, design.width) + others.flatMap { listOf(it.left, it.center.x, it.right) }
            val ys = listOf(0f, design.height / 2, design.height) + others.flatMap { listOf(it.top, it.center.y, it.bottom) }
            fun best(edges: List<Float>, targets: List<Float>): Pair<Float, Float>? =
                edges.flatMap { e -> targets.map { t -> (t - e) to t } }.filter { abs(it.first) <= snap }.minByOrNull { abs(it.first) }
            best(listOf(b.left, b.center.x, b.right), xs)?.let { (d, at) -> dx += d; shown += Guide(true, at) }
            best(listOf(b.top, b.center.y, b.bottom), ys)?.let { (d, at) -> dy += d; shown += Guide(false, at) }
        }
        guides = shown
        val byId = movable.associateBy { it.id }
        updateElements(byId.keys, record = false) { e -> byId.getValue(e.id).let { s -> e.copy(x = s.x + dx, y = s.y + dy) } }
    }

    /** Resizes [start] by dragging handle [handle] to page point [p]; [keepRatio] holds the proportions. */
    fun dragResize(start: DesignElement, handle: Int, p: Offset, keepRatio: Boolean) {
        val next = resized(start, handle, p, keepRatio)
        updateElements(setOf(start.id), record = false) { next }
    }

    /** Turns [start] so that its top points to [p]; snaps to 45° (within 3°) or, with [steps], to 15° steps. */
    fun dragRotate(start: DesignElement, p: Offset, steps: Boolean) {
        // The TUD-Shape turns in 45° steps only (rules of the CD).
        val angle = if (start.kind == ElementKind.TUD_SHAPE) Math.round(rotationTowards(start, p, false) / 45f) * 45f % 360f else rotationTowards(start, p, steps)
        updateElements(setOf(start.id), record = false) { it.copy(rotation = angle) }
    }

    fun endGesture() {
        guides = emptyList()
        version++
    }

    companion object {
        const val MIN = 1f

        fun rotationTowards(e: DesignElement, p: Offset, steps: Boolean): Float {
            var a = Math.toDegrees(atan2((p.y - e.cy).toDouble(), (p.x - e.cx).toDouble())).toFloat() + 90f
            a = ((a % 360f) + 360f) % 360f
            a = if (steps) round(a / 15f) * 15f else round(a / 45f).times(45f).takeIf { abs(it - a) <= 3f } ?: round(a * 10f) / 10f
            return if (a >= 360f) a - 360f else a
        }

        /** [e] with handle [handle] moved to page point [p]; the opposite side stays where it is, also when rotated. */
        fun resized(e: DesignElement, handle: Int, p: Offset, keepRatio: Boolean): DesignElement {
            if (DesignGeometry.isLine(e) && (handle == Handle.START || handle == Handle.END)) {
                val a = DesignGeometry.toPage(e, Offset(0f, 0f))
                val b = DesignGeometry.toPage(e, Offset(e.w, e.h))
                val (s, t) = if (handle == Handle.START) p to b else a to p
                return e.copy(x = s.x, y = s.y, w = t.x - s.x, h = t.y - s.y, rotation = 0f)
            }
            val l = DesignGeometry.toLocal(e, p)
            var left = 0f
            var top = 0f
            var right = e.w
            var bottom = e.h
            when (handle) {
                0 -> { left = l.x; top = l.y }
                1 -> top = l.y
                2 -> { right = l.x; top = l.y }
                3 -> right = l.x
                4 -> { right = l.x; bottom = l.y }
                5 -> bottom = l.y
                6 -> { left = l.x; bottom = l.y }
                7 -> left = l.x
            }
            if (right - left < MIN) { if (handle in setOf(0, 6, 7)) left = right - MIN else right = left + MIN }
            if (bottom - top < MIN) { if (handle in setOf(0, 1, 2)) top = bottom - MIN else bottom = top + MIN }
            if (keepRatio && handle % 2 == 0 && e.w > 0f && e.h > 0f) {
                val ratio = e.w / e.h
                val w = right - left
                val h = bottom - top
                if (w / h > ratio) {
                    val nh = w / ratio
                    if (handle == 0 || handle == 2) top = bottom - nh else bottom = top + nh
                } else {
                    val nw = h * ratio
                    if (handle == 0 || handle == 6) left = right - nw else right = left + nw
                }
            }
            val w = right - left
            val h = bottom - top
            val center = DesignGeometry.toPage(e, Offset((left + right) / 2, (top + bottom) / 2))
            return e.copy(x = center.x - w / 2, y = center.y - h / 2, w = w, h = h)
        }
    }
}
