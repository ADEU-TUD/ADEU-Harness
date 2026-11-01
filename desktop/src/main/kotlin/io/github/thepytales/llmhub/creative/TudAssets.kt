package io.github.thepytales.llmhub.creative

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import org.json.JSONObject
import java.util.zip.GZIPInputStream

/**
 * Assets of the CD package 2025 of TU Dresden (prepared by desktop/tools/tud_cd_assets.py): the official logos as
 * vector paths, the TUD icon sets and the Shape. Everything is drawn as vectors – sharp on posters and in the PDF.
 */
object TudLogos {
    /** The paths of logo [variant] (e.g. "horizontal-blau-de") moved to the origin, and their size without the protection zone. */
    class Vector(val paths: List<Path>, val size: Size)

    private val cache = HashMap<String, Vector?>()

    @Synchronized
    fun vector(variant: String): Vector? = cache.getOrPut(variant) {
        val svg = TudLogos::class.java.getResourceAsStream("/tud/logos/$variant.svg")?.use { it.readBytes().decodeToString() } ?: return@getOrPut null
        val paths = SvgShapes.parse(svg)
        if (paths.isEmpty()) return@getOrPut null
        val bounds = paths.map { it.getBounds() }.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        paths.forEach { it.translate(Offset(-bounds.left, -bounds.top)) }
        Vector(paths, bounds.size)
    }
}

/** Filled paths of a simple SVG (path, polygon, rect), without defs, clip paths, text and fill:none. */
internal object SvgShapes {
    private val NUM = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")

    fun parse(svg: String): List<Path> {
        val body = svg.replace(Regex("<defs>.*?</defs>|<clipPath.*?</clipPath>|<text.*?</text>", RegexOption.DOT_MATCHES_ALL), "")
        val none = Regex("\\.([\\w-]+)\\s*\\{[^}]*fill:\\s*none").findAll(svg).map { it.groupValues[1] }.toSet()
        val out = mutableListOf<Path>()
        Regex("<(path|polygon|polyline|rect)\\b([^>]*)>").findAll(body).forEach { m ->
            val attrs = m.groupValues[2]
            val cls = Regex("class=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)?.split(' ').orEmpty()
            if (cls.any { it in none } || "transform=" in attrs) return@forEach
            val d = when (m.groupValues[1]) {
                "path" -> Regex("\\sd=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                "rect" -> {
                    fun v(n: String) = Regex("\\b$n=\"(${NUM.pattern})\"").find(attrs)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    "M${v("x")},${v("y")}h${v("width")}v${v("height")}h${-v("width")}Z"
                }
                else -> Regex("points=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)?.let { pts ->
                    val n = NUM.findAll(pts).map { it.value }.toList()
                    "M" + n.chunked(2).filter { it.size == 2 }.joinToString(" ") { "${it[0]},${it[1]}" } + "Z"
                }
            } ?: return@forEach
            runCatching { out += PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.NonZero } }
        }
        return out
    }
}

/**
 * The Shape of the CD: the two components of the logo (from the official SVG, 600 × 600). It may be rotated in 45°
 * steps, mirrored, scaled, coloured in TUD colours, cut off at the edge and filled with a photo – never distorted,
 * never as an outline.
 */
object TudShape {
    private val polys = io.github.thepytales.llmhub.docs.TudBrand.SHAPE

    /** The Shape in a square of [side] whose top left corner is at [origin]. */
    fun path(side: Float, origin: Offset = Offset.Zero): Path = Path().apply {
        val k = side / 600f
        for (poly in polys) {
            moveTo(origin.x + poly[0] * k, origin.y + poly[1] * k)
            for (i in 2 until poly.size step 2) lineTo(origin.x + poly[i] * k, origin.y + poly[i + 1] * k)
            close()
        }
    }

    /** The Shape centred in a box of [w] × [h] (it keeps its proportions). */
    fun inBox(w: Float, h: Float): Path {
        val side = minOf(w, h)
        return path(side, Offset((w - side) / 2, (h - side) / 2))
    }

    /** The two polygons in a box of [w] × [h], as points 0..1 of the box (for PowerPoint's own geometry). */
    fun polygons(w: Float, h: Float): List<List<Pair<Float, Float>>> {
        val side = minOf(w, h)
        val ox = (w - side) / 2
        val oy = (h - side) / 2
        return polys.map { poly -> (poly.indices step 2).map { i -> (ox + poly[i] * side / 600f) / w to (oy + poly[i + 1] * side / 600f) / h } }
    }
}

/** The TUD icon sets (Forschung, Lehre, Personen …) – numbered per set, as the sheets of the CD package have no names. */
object TudIcons {
    class Icon(val id: String, val set: String, val number: Int, val d: String, val box: Rect)
    class IconSet(val name: String, val icons: List<Icon>)

    val sets: List<IconSet> by lazy {
        runCatching {
            val text = GZIPInputStream(TudIcons::class.java.getResourceAsStream("/tud/icons.json.gz")!!).use { it.readBytes().decodeToString() }
            val o = JSONObject(text).getJSONArray("sets")
            (0 until o.length()).map { i ->
                val s = o.getJSONObject(i)
                val name = s.getString("name")
                val arr = s.getJSONArray("icons")
                IconSet(name, (0 until arr.length()).map { k ->
                    val ic = arr.getJSONObject(k)
                    val b = ic.getJSONArray("box")
                    val x = b.getDouble(0).toFloat()
                    val y = b.getDouble(1).toFloat()
                    Icon("tud:$name/${k + 1}", name, k + 1, ic.getString("d"), Rect(x, y, x + b.getDouble(2).toFloat(), y + b.getDouble(3).toFloat()))
                })
            }
        }.getOrDefault(emptyList())
    }

    fun byId(id: String): Icon? {
        if (!id.startsWith("tud:")) return null
        val set = id.removePrefix("tud:").substringBeforeLast('/')
        val n = id.substringAfterLast('/').toIntOrNull() ?: return null
        return sets.firstOrNull { it.name == set }?.icons?.getOrNull(n - 1)
    }

    private val paths = HashMap<String, Path>()

    /** The icon's path moved to the origin (its size is [Icon.box]). */
    @Synchronized
    fun path(icon: Icon): Path = paths.getOrPut(icon.id) {
        PathParser().parsePathString(icon.d).toPath().apply { translate(Offset(-icon.box.left, -icon.box.top)) }
    }
}
