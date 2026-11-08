package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.net.JSON
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Diagramme und Whiteboard (AP-61): flowcharts, mind maps, org charts and timelines whose connectors stick to the shapes
 * (an edge names its two nodes, it is drawn from border to border wherever they stand), arranged automatically or by
 * hand, and a whiteboard (free placement, sticky notes, freehand). Mermaid text goes in and out (flowchart/graph,
 * mindmap, timeline). In documents a diagram is a ```mermaid block (arranged automatically) or a ```diagram block
 * (JSON, with hand-placed nodes, notes and strokes); both are drawn by [layout] – on screen, as SVG in the PDF and as a
 * picture in Word.
 */
@Serializable
data class DNode(
    val id: String,
    val text: String,
    /** "box", "round", "stadium", "diamond", "circle", "note" (sticky note). */
    val shape: String = "box",
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    /** Fill colour "#RRGGBB" ("" = by shape). */
    val color: String = "",
)

@Serializable
data class DEdge(val from: String, val to: String, val label: String = "", val dashed: Boolean = false, val arrow: Boolean = true)

/** A freehand line on the whiteboard: x, y pairs. */
@Serializable
data class DStroke(val points: List<Float>, val color: String = "#00305D", val width: Float = 3f)

@Serializable
data class Diagram(
    /** "flow", "org", "mind", "timeline" or "board" (whiteboard). */
    val kind: String = "flow",
    /** "TD" (top down) or "LR" (left to right) for flow and org. */
    val direction: String = "TD",
    val title: String = "",
    val nodes: List<DNode> = emptyList(),
    val edges: List<DEdge> = emptyList(),
    val strokes: List<DStroke> = emptyList(),
    /** Placed by hand: [layout] keeps the positions instead of arranging. */
    val manual: Boolean = false,
) {
    fun node(id: String) = nodes.firstOrNull { it.id == id }
    val isEmpty: Boolean get() = nodes.isEmpty() && strokes.isEmpty()

    companion object {
        val KINDS = listOf("flow", "mind", "org", "timeline", "board")
    }
}

object Diagrams {
    const val TUD_BLUE = "#00305D"
    private const val FILL = "#E8EEF5"
    private const val DECISION = "#FFF4D6"
    private const val NOTE = "#FFF3A6"

    // ── Mermaid ──────────────────────────────────────────────────────────────────────────────────────

    private val NODE = Regex("""^([A-Za-z0-9_À-ž.\-]+)\s*(\(\(.*?\)\)|\(\[.*?]\)|\[\[.*?]]|\[.*?]|\(.*?\)|\{.*?\}|>.*?])?""")
    private val ARROW = Regex("""\s*(-->|---|-\.->|-\.-|==>|===|--\s+[^->]+?\s+-->|--\s+[^->]+?\s+---)\s*(\|([^|]*)\|)?\s*""")

    private fun shapeOf(bracket: String): Pair<String, String> {
        fun inner(s: String, l: Int, r: Int) = s.substring(l, s.length - r).trim().trim('"')
        return when {
            bracket.startsWith("((") -> "circle" to inner(bracket, 2, 2)
            bracket.startsWith("([") -> "stadium" to inner(bracket, 2, 2)
            bracket.startsWith("[[") -> "box" to inner(bracket, 2, 2)
            bracket.startsWith("[") -> "box" to inner(bracket, 1, 1)
            bracket.startsWith("(") -> "round" to inner(bracket, 1, 1)
            bracket.startsWith("{") -> "diamond" to inner(bracket, 1, 1)
            bracket.startsWith(">") -> "box" to inner(bracket, 1, 1)
            else -> "box" to bracket
        }
    }

    private fun clean(t: String) = t.replace("<br>", "\n").replace("<br/>", "\n").replace("\\n", "\n").replace("&quot;", "\"").trim()

    /** A diagram from Mermaid text (flowchart/graph, mindmap, timeline); null if it is none of these. */
    fun fromMermaid(text: String): Diagram? {
        val lines = text.lines().map { it.substringBefore("%%").trimEnd() }.filter { it.isNotBlank() }
        val head = lines.firstOrNull()?.trim()?.lowercase() ?: return null
        return when {
            head.startsWith("flowchart") || head.startsWith("graph") -> flowchart(lines)
            head.startsWith("mindmap") -> mindmap(lines.drop(1))
            head.startsWith("timeline") -> timeline(lines.drop(1))
            else -> null
        }
    }

    private fun flowchart(lines: List<String>): Diagram {
        val dir = lines.first().trim().split(Regex("\\s+")).getOrNull(1)?.uppercase().let { if (it == "LR" || it == "RL") "LR" else "TD" }
        val nodes = LinkedHashMap<String, DNode>()
        val edges = mutableListOf<DEdge>()
        fun define(token: String): String? {
            val m = NODE.find(token.trim()) ?: return null
            val id = m.groupValues[1]
            val br = m.groupValues[2]
            if (br.isNotEmpty()) {
                val (shape, label) = shapeOf(br)
                nodes[id] = DNode(id, clean(label), shape)
            } else if (id !in nodes) nodes[id] = DNode(id, id)
            return id
        }
        for (raw in lines.drop(1)) {
            val line = raw.trim().removeSuffix(";")
            val low = line.lowercase()
            if (low.startsWith("subgraph") || low == "end" || low.startsWith("classdef") || low.startsWith("class ") || low.startsWith("style") || low.startsWith("linkstyle") || low.startsWith("click") || low.startsWith("direction")) continue
            // A chain A --> B -->|x| C: split at the arrows, keeping their labels.
            val parts = mutableListOf<String>()
            val arrows = mutableListOf<Pair<String, String>>()
            var rest = line
            while (true) {
                val m = ARROW.find(rest) ?: break
                parts += rest.substring(0, m.range.first)
                val a = m.groupValues[1]
                val inline = Regex("""^--\s+(.+?)\s+--[->]$""").find(a)?.groupValues?.get(1)
                arrows += a to (m.groupValues[3].ifBlank { inline.orEmpty() })
                rest = rest.substring(m.range.last + 1)
            }
            parts += rest
            val ids = parts.map { p -> p.split('&').mapNotNull { define(it) } }
            for (k in arrows.indices) {
                val (a, label) = arrows[k]
                for (from in ids.getOrElse(k) { emptyList() }) for (to in ids.getOrElse(k + 1) { emptyList() }) {
                    edges += DEdge(from, to, clean(label), dashed = a.contains('.'), arrow = a.endsWith(">"))
                }
            }
        }
        return Diagram(kind = "flow", direction = dir, nodes = nodes.values.toList(), edges = edges)
    }

    private fun mindmap(lines: List<String>): Diagram {
        val nodes = mutableListOf<DNode>()
        val edges = mutableListOf<DEdge>()
        val stack = mutableListOf<Pair<Int, String>>()
        for ((i, raw) in lines.withIndex()) {
            val indent = raw.length - raw.trimStart().length
            var t = raw.trim()
            if (t.startsWith("::")) continue
            // root((Text)), id[Text] or plain text
            val m = Regex("""^([A-Za-z0-9_]*)\s*(\(\(.*?\)\)|\(\[.*?]\)|\[.*?]|\(.*?\)|\{\{.*?}}|\)\).*?\(\(|\).*?\()$""").find(t)
            var shape = "round"
            if (m != null && m.groupValues[2].isNotEmpty()) {
                val br = m.groupValues[2]
                val (s, label) = if (br.startsWith("{{")) "box" to br.substring(2, br.length - 2) else shapeOf(br)
                shape = s; t = label
            }
            val id = "n${i + 1}"
            while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeAt(stack.lastIndex)
            val parent = stack.lastOrNull()?.second
            nodes += DNode(id, clean(t), if (parent == null) "circle" else shape)
            if (parent != null) edges += DEdge(parent, id, arrow = false)
            stack += indent to id
        }
        return Diagram(kind = "mind", nodes = nodes, edges = edges)
    }

    private fun timeline(lines: List<String>): Diagram {
        var title = ""
        val nodes = mutableListOf<DNode>()
        for (raw in lines) {
            val t = raw.trim()
            when {
                t.startsWith("title ", true) -> title = t.substring(6).trim()
                t.startsWith("section ", true) -> {}
                t.startsWith(":") && nodes.isNotEmpty() -> { val last = nodes.removeAt(nodes.lastIndex); nodes += last.copy(text = last.text + "\n" + t.removePrefix(":").trim()) }
                t.contains(':') -> {
                    val parts = t.split(':').map { it.trim() }.filter { it.isNotEmpty() }
                    nodes += DNode("t${nodes.size + 1}", parts.joinToString("\n"), "round")
                }
            }
        }
        val edges = nodes.zipWithNext { a, b -> DEdge(a.id, b.id, arrow = false) }
        return Diagram(kind = "timeline", title = title, nodes = nodes, edges = edges)
    }

    private fun safeId(id: String) = id.replace(Regex("[^A-Za-z0-9_]"), "_").ifBlank { "n" }
    private fun quoted(t: String) = "\"" + t.replace("\"", "&quot;").replace("\n", "<br>") + "\""

    /** Mermaid text of [d] (flow, org, mind, timeline); null for a whiteboard (positions and strokes have no Mermaid form). */
    fun toMermaid(d: Diagram): String? = when (d.kind) {
        "flow", "org" -> buildString {
            append("flowchart ").append(if (d.direction == "LR") "LR" else "TD").append('\n')
            for (n in d.nodes) {
                val id = safeId(n.id)
                val t = quoted(n.text)
                append("    ").append(when (n.shape) {
                    "round" -> "$id($t)"; "stadium" -> "$id([$t])"; "diamond" -> "$id{$t}"; "circle" -> "$id(($t))"
                    else -> "$id[$t]"
                }).append('\n')
            }
            for (e in d.edges) {
                val a = when { e.dashed && e.arrow -> "-.->"; e.dashed -> "-.-"; e.arrow -> "-->"; else -> "---" }
                append("    ").append(safeId(e.from)).append(' ').append(a).append(if (e.label.isNotBlank()) "|${e.label.replace("|", "/")}|" else "").append(' ').append(safeId(e.to)).append('\n')
            }
        }.trimEnd()
        "mind" -> buildString {
            append("mindmap\n")
            val children = d.edges.groupBy { it.from }.mapValues { (_, l) -> l.map { it.to } }
            val root = roots(d).firstOrNull() ?: d.nodes.firstOrNull()?.id
            val seen = HashSet<String>()
            fun walk(id: String, depth: Int) {
                if (!seen.add(id)) return
                val n = d.node(id) ?: return
                val t = n.text.replace("\n", " ")
                append("  ".repeat(depth + 1)).append(if (depth == 0) "root(($t))" else t).append('\n')
                children[id].orEmpty().forEach { walk(it, depth + 1) }
            }
            root?.let { walk(it, 0) }
        }.trimEnd()
        "timeline" -> buildString {
            append("timeline\n")
            if (d.title.isNotBlank()) append("    title ").append(d.title).append('\n')
            orderAlongEdges(d).forEach { n -> append("    ").append(n.text.lines().joinToString(" : ")).append('\n') }
        }.trimEnd()
        else -> null
    }

    // ── Blocks in documents ─────────────────────────────────────────────────────────────────────────

    /** The diagram of a code block (```mermaid or ```diagram), or null. */
    fun ofBlock(lang: String, code: String): Diagram? = when (lang.lowercase()) {
        "mermaid" -> fromMermaid(code)
        "diagram", "diagramm", "whiteboard" -> runCatching { JSON.decodeFromString(Diagram.serializer(), code) }.getOrNull()
        else -> null
    }

    fun isDiagramLang(lang: String) = lang.lowercase() in setOf("mermaid", "diagram", "diagramm", "whiteboard")

    /** The block for a document: ```mermaid while it is arranged automatically, else ```diagram with all positions. */
    fun block(d: Diagram): String {
        val mermaid = if (!d.manual && d.strokes.isEmpty() && d.nodes.none { it.shape == "note" }) toMermaid(d) else null
        return if (mermaid != null) "```mermaid\n$mermaid\n```" else "```diagram\n" + JSON.encodeToString(Diagram.serializer(), d) + "\n```"
    }

    /** The diagram block a Markdown block holds (lang and code), or null. */
    fun parseBlock(markdown: String): Pair<String, String>? {
        val m = Regex("""^```([A-Za-z]+)[ \t]*\n([\s\S]*?)\n?```\s*$""").find(markdown.trim()) ?: return null
        return if (isDiagramLang(m.groupValues[1])) m.groupValues[1] to m.groupValues[2] else null
    }

    // ── Layout ───────────────────────────────────────────────────────────────────────────────────────

    /** Text of a node broken into lines of about [chars] characters. */
    fun wrap(text: String, chars: Int = 24): List<String> = text.lines().flatMap { line ->
        val words = line.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return@flatMap listOf("")
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            if (cur.isNotEmpty() && cur.length + 1 + w.length > chars) { out += cur.toString(); cur = StringBuilder() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(w)
        }
        if (cur.isNotEmpty()) out += cur.toString()
        out
    }

    /** Size of a node from its text (when it has none). */
    fun measure(n: DNode): DNode {
        if (n.w > 0f && n.h > 0f) return n
        val lines = wrap(n.text, if (n.shape == "note") 20 else 24)
        val textW = (lines.maxOfOrNull { it.length } ?: 1) * 7.4f
        var w = (textW + 32f).coerceIn(if (n.shape == "note") 130f else 100f, 260f)
        var h = 26f + lines.size * 18f
        when (n.shape) {
            "diamond" -> { w *= 1.35f; h *= 1.45f }
            "circle" -> { val s = max(w, h) * 1.05f; w = s; h = s }
            "note" -> h = max(h, 110f)
        }
        return n.copy(w = w, h = h)
    }

    private fun roots(d: Diagram): List<String> {
        val incoming = d.edges.map { it.to }.toSet()
        return d.nodes.map { it.id }.filter { it !in incoming }.ifEmpty { d.nodes.take(1).map { it.id } }
    }

    private fun orderAlongEdges(d: Diagram): List<DNode> {
        val next = d.edges.associate { it.from to it.to }
        val out = mutableListOf<DNode>()
        val seen = HashSet<String>()
        for (r in roots(d)) {
            var cur: String? = r
            while (cur != null && seen.add(cur)) { d.node(cur)?.let { out += it }; cur = next[cur] }
        }
        d.nodes.filter { it.id !in seen }.forEach { out += it }
        return out
    }

    /** [d] arranged (sizes measured, positions computed) unless it is placed by hand or a whiteboard. */
    fun arrange(d: Diagram, force: Boolean = false): Diagram {
        val measured = d.copy(nodes = d.nodes.map(::measure))
        if (!force && (d.manual || d.kind == "board")) return measured
        return when (d.kind) {
            "mind" -> radial(measured)
            "timeline" -> timelineLayout(measured)
            else -> layered(measured)
        }.copy(manual = false)
    }

    /** Layers by longest path from the roots (back edges ignored), ordered by the mean position of the parents. */
    private fun layered(d: Diagram): Diagram {
        if (d.nodes.isEmpty()) return d
        val out = d.edges.groupBy { it.from }
        val rank = HashMap<String, Int>()
        val state = HashMap<String, Int>()
        val forward = mutableListOf<DEdge>()
        fun dfs(id: String) {
            state[id] = 1
            for (e in out[id].orEmpty()) {
                when (state[e.to]) {
                    null -> { forward += e; dfs(e.to) }
                    2 -> forward += e
                    else -> {} // a back edge (a loop): not used for the layers
                }
            }
            state[id] = 2
        }
        (roots(d) + d.nodes.map { it.id }).forEach { if (state[it] == null) dfs(it) }
        // Longest path over the forward edges (topological by repeated relaxation; small graphs).
        d.nodes.forEach { rank[it.id] = 0 }
        repeat(d.nodes.size) { var changed = false; for (e in forward) { val r = (rank[e.from] ?: 0) + 1; if (r > (rank[e.to] ?: 0)) { rank[e.to] = r; changed = true } }; if (!changed) return@repeat }
        val layers = d.nodes.groupBy { rank[it.id] ?: 0 }.toSortedMap().values.map { it.toMutableList() }
        // Order: by the average index of the parents in the layer above (two sweeps).
        val parents = forward.groupBy { it.to }.mapValues { (_, l) -> l.map { it.from } }
        repeat(2) {
            for (li in 1 until layers.size) {
                val pos = layers[li - 1].withIndex().associate { it.value.id to it.index.toFloat() }
                layers[li].sortBy { n -> parents[n.id].orEmpty().mapNotNull { pos[it] }.average().takeIf { !it.isNaN() } ?: Float.MAX_VALUE.toDouble() }
            }
        }
        val lr = d.direction == "LR"
        val gapMain = 70f
        val gapCross = 40f
        val placed = HashMap<String, DNode>()
        var main = 0f
        val crossTotals = layers.map { l -> l.sumOf { (if (lr) it.h else it.w).toDouble() }.toFloat() + gapCross * (l.size - 1) }
        val widest = crossTotals.maxOrNull() ?: 0f
        for ((li, layer) in layers.withIndex()) {
            var cross = (widest - crossTotals[li]) / 2
            val thick = layer.maxOf { if (lr) it.w else it.h }
            for (n in layer) {
                placed[n.id] = if (lr) n.copy(x = main + (thick - n.w) / 2, y = cross) else n.copy(x = cross, y = main + (thick - n.h) / 2)
                cross += (if (lr) n.h else n.w) + gapCross
            }
            main += thick + gapMain
        }
        return d.copy(nodes = d.nodes.map { placed[it.id] ?: it })
    }

    /** The root in the middle, the branches around it, each subtree in its own sector (by its number of leaves). */
    private fun radial(d: Diagram): Diagram {
        if (d.nodes.isEmpty()) return d
        val children = d.edges.groupBy { it.from }.mapValues { (_, l) -> l.map { it.to } }
        val root = roots(d).first()
        val leaves = HashMap<String, Int>()
        val seen = HashSet<String>()
        fun count(id: String): Int { if (!seen.add(id)) return 0; val k = children[id].orEmpty().sumOf { count(it) }; return max(1, k).also { leaves[id] = it } }
        count(root)
        val placed = HashMap<String, DNode>()
        val byId = d.nodes.associateBy { it.id }
        fun put(id: String, cx: Float, cy: Float) { byId[id]?.let { n -> placed[id] = n.copy(x = cx - n.w / 2, y = cy - n.h / 2) } }
        put(root, 0f, 0f)
        val done = hashSetOf(root)
        fun place(id: String, depth: Int, from: Double, to: Double) {
            val kids = children[id].orEmpty().filter { done.add(it) }
            val total = kids.sumOf { leaves[it] ?: 1 }.coerceAtLeast(1)
            var a = from
            for (k in kids) {
                val span = (to - from) * (leaves[k] ?: 1) / total
                val mid = a + span / 2
                val r = 190.0 + (depth - 1) * 170.0
                put(k, (cos(mid) * r).toFloat(), (sin(mid) * r * 0.8).toFloat())
                place(k, depth + 1, a, a + span)
                a += span
            }
        }
        place(root, 1, -PI / 2, 3 * PI / 2)
        // Shift into positive coordinates.
        val all = d.nodes.map { placed[it.id] ?: it }
        val minX = all.minOf { it.x }; val minY = all.minOf { it.y }
        return d.copy(nodes = all.map { it.copy(x = it.x - minX, y = it.y - minY) })
    }

    /** On a line from left to right, the entries above and below it in turn. */
    private fun timelineLayout(d: Diagram): Diagram {
        val ordered = orderAlongEdges(d)
        val step = (ordered.maxOfOrNull { it.w } ?: 140f) * 0.7f + 30f
        val top = ordered.maxOfOrNull { it.h } ?: 60f
        val pos = ordered.withIndex().associate { (i, n) ->
            n.id to n.copy(x = 20f + i * step, y = if (i % 2 == 0) 0f else top + 80f)
        }
        return d.copy(nodes = d.nodes.map { pos[it.id] ?: it })
    }

    // ── Drawing ─────────────────────────────────────────────────────────────────────────────────────

    sealed interface Shape
    data class Node(val x: Float, val y: Float, val w: Float, val h: Float, val shape: String, val fill: String, val stroke: String, val lines: List<String>, val textColor: String, val id: String) : Shape
    data class Edge(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val arrow: Boolean, val dashed: Boolean, val label: String) : Shape
    data class Stroke(val points: List<Float>, val color: String, val width: Float) : Shape
    data class Axis(val x1: Float, val y: Float, val x2: Float) : Shape
    data class Title(val x: Float, val y: Float, val text: String) : Shape

    /** Width and height of what [shapes] cover (with a margin). */
    data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val w get() = maxX - minX
        val h get() = maxY - minY
    }

    /** Where the line from the centre of [n] towards ([tx], [ty]) leaves its outline. */
    fun border(n: DNode, tx: Float, ty: Float): Pair<Float, Float> {
        val cx = n.x + n.w / 2; val cy = n.y + n.h / 2
        val dx = tx - cx; val dy = ty - cy
        if (abs(dx) < 1e-3 && abs(dy) < 1e-3) return cx to cy
        val hw = n.w / 2; val hh = n.h / 2
        val t = when (n.shape) {
            "circle" -> { val len = sqrt(dx * dx / (hw * hw) + dy * dy / (hh * hh)); 1f / len }
            "diamond" -> 1f / (abs(dx) / hw + abs(dy) / hh)
            else -> min(if (dx != 0f) hw / abs(dx) else Float.MAX_VALUE, if (dy != 0f) hh / abs(dy) else Float.MAX_VALUE)
        }
        return (cx + dx * t) to (cy + dy * t)
    }

    /** What to draw for [d] (arranged first): nodes, connectors from border to border, strokes, the timeline's axis. */
    fun layout(d0: Diagram): List<Shape> {
        val d = arrange(d0)
        val out = mutableListOf<Shape>()
        if (d.kind == "timeline" && d.nodes.isNotEmpty()) {
            val top = d.nodes.filter { it.y == 0f }.maxOfOrNull { it.h } ?: 60f
            out += Axis(0f, top + 40f, d.nodes.maxOf { it.x + it.w } + 20f)
        }
        val byId = d.nodes.associateBy { it.id }
        for (e in d.edges) {
            val a = byId[e.from] ?: continue
            val b = byId[e.to] ?: continue
            if (d.kind == "timeline") continue
            val (x1, y1) = border(a, b.x + b.w / 2, b.y + b.h / 2)
            val (x2, y2) = border(b, a.x + a.w / 2, a.y + a.h / 2)
            out += Edge(x1, y1, x2, y2, e.arrow, e.dashed, e.label)
        }
        val rootId = if (d.kind == "mind") roots(d).firstOrNull() else null
        for (n in d.nodes) {
            val fill = n.color.ifBlank {
                when {
                    n.id == rootId -> TUD_BLUE
                    n.shape == "note" -> NOTE
                    n.shape == "diamond" -> DECISION
                    else -> FILL
                }
            }
            val dark = isDark(fill)
            out += Node(n.x, n.y, n.w, n.h, n.shape, fill, if (n.shape == "note") "#E0C200" else TUD_BLUE, wrap(n.text, if (n.shape == "note") 20 else 24), if (dark) "#FFFFFF" else "#1A1A1A", n.id)
        }
        if (d.kind == "timeline") {
            val axisY = (out.firstOrNull { it is Axis } as? Axis)?.y ?: 0f
            for (n in d.nodes) out += Edge(n.x + n.w / 2, if (n.y == 0f) n.y + n.h else n.y, n.x + n.w / 2, axisY, arrow = false, dashed = true, label = "")
        }
        d.strokes.forEach { s -> out += Stroke(s.points, s.color, s.width) }
        if (d.title.isNotBlank()) out.add(0, Title(0f, -16f, d.title))
        return out
    }

    fun isDark(hex: String): Boolean {
        val c = hex.removePrefix("#").toLongOrNull(16) ?: return false
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) < 140
    }

    fun bounds(shapes: List<Shape>, margin: Float = 16f): Bounds {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        fun add(x: Float, y: Float) { minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y) }
        for (s in shapes) when (s) {
            is Node -> { add(s.x, s.y); add(s.x + s.w, s.y + s.h) }
            is Edge -> { add(s.x1, s.y1); add(s.x2, s.y2) }
            is Stroke -> s.points.chunked(2).forEach { if (it.size == 2) add(it[0], it[1]) }
            is Axis -> { add(s.x1, s.y); add(s.x2, s.y) }
            is Title -> { add(s.x, s.y - 14f); add(s.x + s.text.length * 8f, s.y) }
        }
        if (minX == Float.MAX_VALUE) return Bounds(0f, 0f, 200f, 100f)
        return Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }

    /** Short text alternative (for Word and screen readers): what is connected with what. */
    fun altText(d: Diagram): String {
        val kind = when (d.kind) { "mind" -> "Mindmap"; "org" -> "Organigramm"; "timeline" -> "Zeitstrahl"; "board" -> "Whiteboard"; else -> "Flussdiagramm" }
        val names = d.nodes.associate { it.id to it.text.replace("\n", " ") }
        val links = d.edges.take(30).joinToString("; ") { e -> "${names[e.from]} → ${names[e.to]}" + if (e.label.isNotBlank()) " (${e.label})" else "" }
        return (kind + (if (d.title.isNotBlank()) " „${d.title}“" else "") + ": " + links.ifBlank { d.nodes.joinToString(", ") { it.text.replace("\n", " ") } }).take(900)
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** The diagram as SVG (PDF export over HTML, and the picture for Word). */
    fun svg(d: Diagram): String {
        val shapes = layout(d)
        val b = bounds(shapes)
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"${b.minX} ${b.minY} ${b.w} ${b.h}\" width=\"${b.w.toInt()}\" height=\"${b.h.toInt()}\" role=\"img\" aria-label=\"${esc(altText(d))}\" font-family=\"Open Sans, Arial, sans-serif\" font-size=\"13\">")
        sb.append("<defs><marker id=\"pfeil\" viewBox=\"0 0 10 10\" refX=\"10\" refY=\"5\" markerWidth=\"8\" markerHeight=\"8\" orient=\"auto-start-reverse\"><path d=\"M0,0 L10,5 L0,10 z\" fill=\"$TUD_BLUE\"/></marker></defs>")
        for (s in shapes) when (s) {
            is Title -> sb.append("<text x=\"${s.x}\" y=\"${s.y}\" font-size=\"15\" font-weight=\"bold\" fill=\"$TUD_BLUE\">${esc(s.text)}</text>")
            is Axis -> sb.append("<line x1=\"${s.x1}\" y1=\"${s.y}\" x2=\"${s.x2}\" y2=\"${s.y}\" stroke=\"$TUD_BLUE\" stroke-width=\"3\"/>")
            is Edge -> {
                sb.append("<line x1=\"${s.x1}\" y1=\"${s.y1}\" x2=\"${s.x2}\" y2=\"${s.y2}\" stroke=\"$TUD_BLUE\" stroke-width=\"1.6\"")
                if (s.dashed) sb.append(" stroke-dasharray=\"5 4\"")
                if (s.arrow) sb.append(" marker-end=\"url(#pfeil)\"")
                sb.append("/>")
                if (s.label.isNotBlank()) {
                    val mx = (s.x1 + s.x2) / 2; val my = (s.y1 + s.y2) / 2
                    sb.append("<rect x=\"${mx - s.label.length * 3.6f - 4}\" y=\"${my - 10}\" width=\"${s.label.length * 7.2f + 8}\" height=\"18\" fill=\"#FFFFFF\"/>")
                    sb.append("<text x=\"$mx\" y=\"${my + 4}\" text-anchor=\"middle\" fill=\"#333333\" font-size=\"12\">${esc(s.label)}</text>")
                }
            }
            is Node -> {
                val common = "fill=\"${s.fill}\" stroke=\"${s.stroke}\" stroke-width=\"1.4\""
                when (s.shape) {
                    "diamond" -> sb.append("<polygon points=\"${s.x + s.w / 2},${s.y} ${s.x + s.w},${s.y + s.h / 2} ${s.x + s.w / 2},${s.y + s.h} ${s.x},${s.y + s.h / 2}\" $common/>")
                    "circle" -> sb.append("<ellipse cx=\"${s.x + s.w / 2}\" cy=\"${s.y + s.h / 2}\" rx=\"${s.w / 2}\" ry=\"${s.h / 2}\" $common/>")
                    "round", "stadium" -> sb.append("<rect x=\"${s.x}\" y=\"${s.y}\" width=\"${s.w}\" height=\"${s.h}\" rx=\"${if (s.shape == "stadium") s.h / 2 else 12f}\" $common/>")
                    "note" -> sb.append("<rect x=\"${s.x}\" y=\"${s.y}\" width=\"${s.w}\" height=\"${s.h}\" rx=\"2\" $common/>")
                    else -> sb.append("<rect x=\"${s.x}\" y=\"${s.y}\" width=\"${s.w}\" height=\"${s.h}\" rx=\"3\" $common/>")
                }
                val lineH = 17f
                val start = s.y + s.h / 2 - (s.lines.size - 1) * lineH / 2 + 4.5f
                s.lines.forEachIndexed { i, line ->
                    sb.append("<text x=\"${s.x + s.w / 2}\" y=\"${start + i * lineH}\" text-anchor=\"middle\" fill=\"${s.textColor}\">${esc(line)}</text>")
                }
            }
            is Stroke -> sb.append("<polyline points=\"${s.points.chunked(2).filter { it.size == 2 }.joinToString(" ") { "${it[0]},${it[1]}" }}\" fill=\"none\" stroke=\"${s.color}\" stroke-width=\"${s.width}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
        }
        sb.append("</svg>")
        return sb.toString()
    }

    /** Renders [d] as PNG (set by the Windows app for Word; null where there is no renderer, Word then gets the text). */
    @Volatile
    var raster: ((Diagram) -> ByteArray?)? = null

    // ── Editing helpers (editor and Lora) ─────────────────────────────────────────────────────────────

    /** A new id not used in [d]. */
    fun newId(d: Diagram): String { var k = d.nodes.size + 1; while (d.nodes.any { it.id == "n$k" }) k++; return "n$k" }

    /** [d] without node [id] and its connectors. */
    fun remove(d: Diagram, id: String): Diagram = d.copy(nodes = d.nodes.filter { it.id != id }, edges = d.edges.filter { it.from != id && it.to != id })

    /** [d] with a connector from [from] to [to] (none twice, none to itself). */
    fun connect(d: Diagram, from: String, to: String, label: String = ""): Diagram {
        if (from == to || d.edges.any { it.from == from && it.to == to }) return d
        return d.copy(edges = d.edges + DEdge(from, to, label, arrow = d.kind != "mind" && d.kind != "timeline"))
    }

    /** The node at ([x], [y]) (topmost), or null. */
    fun nodeAt(d: Diagram, x: Float, y: Float): DNode? = d.nodes.lastOrNull { x >= it.x && x <= it.x + it.w && y >= it.y && y <= it.y + it.h }

    /** Mermaid out of a model answer: the text of a ```mermaid block, or the answer itself when it starts like Mermaid. */
    fun mermaidIn(answer: String): String? {
        Regex("""```(?:mermaid)?\s*\n([\s\S]*?)\n```""").find(answer)?.groupValues?.get(1)?.let { if (fromMermaid(it) != null) return it.trim() }
        val start = answer.lines().indexOfFirst { l -> l.trim().lowercase().let { it.startsWith("flowchart") || it.startsWith("graph") || it.startsWith("mindmap") || it.startsWith("timeline") } }
        if (start < 0) return null
        return answer.lines().drop(start).joinToString("\n").trim().takeIf { fromMermaid(it) != null }
    }

    /** The request to the model for a diagram of [kind] from [description]. */
    fun prompt(kind: String, description: String): String {
        val what = when (kind) {
            "mind" -> "eine Mindmap (Mermaid „mindmap“, Einrückung = Ebene, die Wurzel als root((Thema)))"
            "org" -> "ein Organigramm (Mermaid „flowchart TD“, oben die Leitung)"
            "timeline" -> "einen Zeitstrahl (Mermaid „timeline“, je Zeile „Zeitpunkt : Ereignis“)"
            else -> "ein Flussdiagramm (Mermaid „flowchart TD“; Entscheidungen als Raute {Frage?} mit beschrifteten Pfeilen -->|Ja| und -->|Nein|; Start und Ende als ([Start]))"
        }
        return "Zeichne $what aus dieser Beschreibung. Kurze deutsche Beschriftungen (höchstens 6 Wörter je Kasten), keine Farben, kein classDef, keine Erklärung. " +
            "Antworte NUR mit dem Mermaid-Text in einem ```mermaid-Block.\n\nBeschreibung:\n${description.trim()}"
    }
}
