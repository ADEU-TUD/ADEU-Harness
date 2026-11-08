package io.github.thepytales.llmhub.creative

import androidx.compose.ui.geometry.Rect
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.SlideElement

/**
 * The operations of the shared canvas (AP-50) as pure functions on the elements of a page: aligning and distributing,
 * groups and layers. Erstellen › Gestalten ([DesignEditorState]) and the slides ([SlideCanvas]) both use them, so an
 * element behaves the same in both.
 */
object CanvasOps {
    /**
     * [elements] with [ids] aligned: one element (or one group) to the page of [pageW] × [pageH] mm, several to their
     * common bounds. [mode]: left, hcenter, right, top, vcenter, bottom; distributeH/distributeV spread three or more
     * evenly (a group counts as one block).
     */
    fun aligned(elements: List<DesignElement>, ids: Set<String>, mode: String, pageW: Float, pageH: Float): List<DesignElement> {
        val moving = elements.filter { it.id in ids && !it.locked }
        if (moving.isEmpty()) return elements
        // Blocks: a group moves as one, other elements alone.
        val blocks = moving.groupBy { it.group.ifEmpty { "#" + it.id } }.values.toList()
        val boundsOf = blocks.associateWith { DesignGeometry.bounds(it)!! }
        val area = if (blocks.size == 1) Rect(0f, 0f, pageW, pageH) else DesignGeometry.bounds(moving) ?: return elements
        val shift = HashMap<String, Pair<Float, Float>>()
        if (mode == "distributeH" || mode == "distributeV") {
            if (blocks.size < 3) return elements
            val horizontal = mode == "distributeH"
            val sorted = blocks.sortedBy { if (horizontal) boundsOf.getValue(it).left else boundsOf.getValue(it).top }
            val total = sorted.sumOf { (if (horizontal) boundsOf.getValue(it).width else boundsOf.getValue(it).height).toDouble() }.toFloat()
            val gap = ((if (horizontal) area.width else area.height) - total) / (sorted.size - 1)
            var pos = if (horizontal) area.left else area.top
            sorted.forEach { b ->
                val r = boundsOf.getValue(b)
                val d = pos - (if (horizontal) r.left else r.top)
                b.forEach { e -> shift[e.id] = if (horizontal) d to 0f else 0f to d }
                pos += (if (horizontal) r.width else r.height) + gap
            }
        } else {
            blocks.forEach { b ->
                val r = boundsOf.getValue(b)
                val d = when (mode) {
                    "left" -> area.left - r.left to 0f
                    "hcenter" -> area.center.x - r.center.x to 0f
                    "right" -> area.right - r.right to 0f
                    "top" -> 0f to area.top - r.top
                    "vcenter" -> 0f to area.center.y - r.center.y
                    "bottom" -> 0f to area.bottom - r.bottom
                    else -> 0f to 0f
                }
                b.forEach { e -> shift[e.id] = d }
            }
        }
        return elements.map { e -> shift[e.id]?.let { (dx, dy) -> e.copy(x = e.x + dx, y = e.y + dy) } ?: e }
    }

    /** [ids] with all members of their groups. */
    fun withGroups(elements: List<DesignElement>, ids: Set<String>): Set<String> {
        val groups = elements.filter { it.id in ids && it.group.isNotEmpty() }.map { it.group }.toSet()
        return if (groups.isEmpty()) ids else ids + elements.filter { it.group in groups }.map { it.id }
    }

    /** [ids] grouped under [groupId] (at least two elements, otherwise unchanged). */
    fun grouped(elements: List<DesignElement>, ids: Set<String>, groupId: String): List<DesignElement> =
        if (elements.count { it.id in ids } < 2) elements else elements.map { if (it.id in ids) it.copy(group = groupId) else it }

    fun ungrouped(elements: List<DesignElement>, ids: Set<String>): List<DesignElement> = elements.map { if (it.id in ids) it.copy(group = "") else it }

    /** Layers: [ids] one step (or [all] the way) to the front ([up]) or back. */
    fun reordered(elements: List<DesignElement>, ids: Set<String>, up: Boolean, all: Boolean): List<DesignElement> {
        val moving = elements.filter { it.id in ids }
        if (moving.isEmpty()) return elements
        val rest = elements.filter { it.id !in ids }
        if (all) return if (up) rest + moving else moving + rest
        val list = elements.toMutableList()
        val indices = if (up) list.indices.reversed() else list.indices
        for (i in indices) {
            if (list[i].id !in ids) continue
            val j = if (up) i + 1 else i - 1
            if (j in list.indices && list[j].id !in ids) { val t = list[j]; list[j] = list[i]; list[i] = t }
        }
        return list
    }
}

/**
 * The free elements of a slide (text boxes, pictures, shapes in cm, AP-16) as elements of the shared canvas (mm), so
 * the slides use the same operations as Gestalten ([CanvasOps]).
 */
object SlideCanvas {
    /** Size of a 16:9 slide in mm (33,867 × 19,05 cm). */
    const val W = 338.67f
    const val H = 190.5f

    fun toDesign(list: List<SlideElement>): List<DesignElement> = list.mapIndexed { i, s -> toDesign(s, "s$i") }

    fun toDesign(s: SlideElement, id: String): DesignElement {
        val kind = when (s.kind) {
            "bild" -> ElementKind.IMAGE
            "form" -> when (s.shape) { "ellipse" -> ElementKind.ELLIPSE; "pfeil" -> ElementKind.ARROW; "linie" -> ElementKind.LINE; else -> ElementKind.RECT }
            else -> ElementKind.TEXT
        }
        return DesignElement(
            id = id, kind = kind, x = (s.x * 10).toFloat(), y = (s.y * 10).toFloat(), w = (s.w * 10).toFloat(), h = (s.h * 10).toFloat(),
            rotation = s.rotation.toFloat(), fill = s.fill, color = s.color, text = s.text, src = s.src, fontSize = s.size.toFloat(),
            bold = s.bold, align = when (s.align) { "mitte" -> "center"; "rechts" -> "right"; else -> "left" },
            radius = if (s.shape == "abgerundet") 3f else 0f, group = s.group, fill2 = s.fill2,
        )
    }

    /** [design] (from [toDesign]) back onto the slide elements [old]: position, size, rotation, group, order. */
    fun fromDesign(old: List<SlideElement>, design: List<DesignElement>): List<SlideElement> = design.mapNotNull { e ->
        val i = e.id.removePrefix("s").toIntOrNull() ?: return@mapNotNull null
        val s = old.getOrNull(i) ?: return@mapNotNull null
        fun cm(v: Float) = Math.round(v * 10.0) / 100.0
        s.copy(x = cm(e.x), y = cm(e.y), w = cm(e.w), h = cm(e.h), rotation = e.rotation.toDouble(), group = e.group, fill2 = e.fill2)
    }

    /** The slide elements with [indices] aligned like in Gestalten (to the slide when it is one element or one group). */
    fun align(list: List<SlideElement>, indices: Set<Int>, mode: String): List<SlideElement> {
        val d = toDesign(list)
        val ids = CanvasOps.withGroups(d, indices.map { "s$it" }.toSet())
        return fromDesign(list, CanvasOps.aligned(d, ids, mode, W, H))
    }

    /** Layers of the slide elements like in Gestalten. */
    fun reorder(list: List<SlideElement>, indices: Set<Int>, up: Boolean, all: Boolean): List<SlideElement> {
        val d = toDesign(list)
        return fromDesign(list, CanvasOps.reordered(d, CanvasOps.withGroups(d, indices.map { "s$it" }.toSet()), up, all))
    }

    /** Indices of the elements in the group of element [index] (itself if it is in none). */
    fun groupOf(list: List<SlideElement>, index: Int): Set<Int> {
        val g = list.getOrNull(index)?.group.orEmpty()
        return if (g.isEmpty()) setOf(index) else list.indices.filter { list[it].group == g }.toSet()
    }
}
