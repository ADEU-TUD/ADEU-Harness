package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.trLater
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Dokumenten-Scanner (AP-62): photos of paper pages become a clean, searchable PDF. Everything here is plain Kotlin on
 * ARGB pixels, the same on Android and Windows (and testable without a device): finding the page in the photo
 * ([DocScanner.detect]), straightening it ([DocScanner.warp]), contrast ([DocScanner.enhance]) and putting the pages
 * together as PDF with a text layer ([DocScanner.save]). Reading and writing JPEG files is in ScanImages (one per
 * platform).
 */

/** Pixels of a picture, ARGB like Android's Bitmap and Java's BufferedImage. */
class ScanRaster(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Bildgröße passt nicht" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
    operator fun set(x: Int, y: Int, argb: Int) {
        pixels[y * width + x] = argb
    }

    /** Turned by [quarter] × 90° clockwise. */
    fun rotated(quarter: Int): ScanRaster {
        val q = ((quarter % 4) + 4) % 4
        if (q == 0) return this
        val w = if (q == 2) width else height
        val h = if (q == 2) height else width
        val out = ScanRaster(w, h)
        for (y in 0 until height) for (x in 0 until width) {
            val p = this[x, y]
            when (q) {
                1 -> out[height - 1 - y, x] = p
                2 -> out[width - 1 - x, height - 1 - y] = p
                else -> out[y, width - 1 - x] = p
            }
        }
        return out
    }

    /** Smaller copy whose longer side is at most [maxSide] (averaging the pixels); this one if it is small enough. */
    fun scaledDown(maxSide: Int): ScanRaster {
        val long = max(width, height)
        if (long <= maxSide) return this
        val f = long.toDouble() / maxSide
        val w = max(1, (width / f).roundToInt())
        val h = max(1, (height / f).roundToInt())
        val out = ScanRaster(w, h)
        for (y in 0 until h) {
            val y0 = (y * f).toInt()
            val y1 = min(height, max(y0 + 1, ((y + 1) * f).toInt()))
            for (x in 0 until w) {
                val x0 = (x * f).toInt()
                val x1 = min(width, max(x0 + 1, ((x + 1) * f).toInt()))
                var r = 0L; var g = 0L; var b = 0L; var n = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val p = this[xx, yy]
                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                out[x, y] = argb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
            }
        }
        return out
    }

    companion object {
        fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        fun luma(p: Int): Int = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }
}

/** A point as fraction of the picture (0…1 from the left and from the top). */
data class ScanPoint(val x: Float, val y: Float)

/** The four corners of the page in the photo, clockwise from the top left (fractions of the picture). */
data class ScanQuad(val tl: ScanPoint, val tr: ScanPoint, val br: ScanPoint, val bl: ScanPoint) {
    val points: List<ScanPoint> get() = listOf(tl, tr, br, bl)

    fun with(index: Int, p: ScanPoint): ScanQuad = when (index) {
        0 -> copy(tl = p)
        1 -> copy(tr = p)
        2 -> copy(br = p)
        else -> copy(bl = p)
    }

    /** Area as fraction of the picture (shoelace formula). */
    val area: Float get() {
        val p = points
        var s = 0f
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2f
    }

    companion object {
        /** The whole picture (nothing cut away). */
        val FULL = ScanQuad(ScanPoint(0f, 0f), ScanPoint(1f, 0f), ScanPoint(1f, 1f), ScanPoint(0f, 1f))

        /** The four points in the order top left, top right, bottom right, bottom left (around their centre). */
        fun ordered(points: List<ScanPoint>): ScanQuad {
            require(points.size == 4)
            val cx = points.sumOf { it.x.toDouble() } / 4
            val cy = points.sumOf { it.y.toDouble() } / 4
            // Clockwise on screen (y down) starting at the top left: sort by angle, rotate so the one nearest the top left is first.
            val sorted = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
            val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            val r = List(4) { sorted[(start + it) % 4] }
            return ScanQuad(r[0], r[1], r[2], r[3])
        }
    }
}

/** How the page looks after scanning. */
enum class ScanMode(val label: String) {
    /** Colours kept, paper made white, more contrast. */
    COLOR(trLater("Farbe")),
    /** Grey, shadows evened out – the usual choice for text. */
    GRAY(trLater("Graustufen")),
    /** Only black and white (small files, crisp text; for forms and letters). */
    BW(trLater("Schwarz-Weiß")),
    /** As photographed, only straightened. */
    ORIGINAL(trLater("Original")),
}

/** One page of a scan: the photo, where the paper is in it, look and turn. */
data class ScanPage(
    val source: File,
    val quad: ScanQuad = ScanQuad.FULL,
    val mode: ScanMode = ScanMode.COLOR,
    /** Quarter turns clockwise after straightening. */
    val rotation: Int = 0,
    /** Whether [quad] was found in the photo (false: the whole picture, nothing recognized). */
    val detected: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
)

object DocScanner {
    /** Longer side of the straightened page: about 200 dpi on A4 – enough for text recognition and printing. */
    const val PAGE_SIDE = 2340
    /** Size the page is looked for in. */
    private const val DETECT_SIDE = 320
    private const val A4 = 1.41421356

    // ── Finding the page ────────────────────────────────────────────────────────────────────────────────────

    /**
     * Where the sheet of paper lies in [photo]; null if none stands out (the whole picture is used then). Looks for the
     * largest bright area (paper on a darker desk, Otsu threshold), or else the largest dark one, and fits the
     * quadrilateral of largest area into its convex hull – also for tilted and slanted photos.
     */
    fun detect(photo: ScanRaster): ScanQuad? {
        val small = photo.scaledDown(DETECT_SIDE)
        val w = small.width
        val h = small.height
        val gray = blur(IntArray(w * h) { ScanRaster.luma(small.pixels[it]) }, w, h, 2)
        val t = otsu(gray)
        val candidates = listOf(true, false).mapNotNull { bright ->
            val mask = BooleanArray(w * h) { if (bright) gray[it] > t else gray[it] <= t }
            val comp = largestComponent(mask, w, h) ?: return@mapNotNull null
            val fraction = comp.size.toFloat() / (w * h)
            if (fraction < 0.12f || fraction > 0.97f) return@mapNotNull null
            val hull = convexHull(boundary(comp, w, h))
            if (hull.size < 4) return@mapNotNull null
            val quad = largestQuad(if (hull.size > 90) hull.filterIndexed { i, _ -> i % (hull.size / 90 + 1) == 0 } else hull)
            val area = polygonArea(quad)
            // The paper fills its hull nearly completely (a sheet), not a ragged shape (a shadow, a hand).
            val solidity = comp.size / max(1.0, polygonArea(hull))
            val rect = area / max(1.0, polygonArea(hull))
            if (solidity < 0.75 || rect < 0.85) return@mapNotNull null
            Triple(bright, quad, area * if (bright) 1.15 else 1.0)
        }
        val best = candidates.maxByOrNull { it.third } ?: return null
        val pts = best.second.map { (x, y) -> ScanPoint(((x + 0.5) / w).toFloat().coerceIn(0f, 1f), ((y + 0.5) / h).toFloat().coerceIn(0f, 1f)) }
        val quad = ScanQuad.ordered(pts)
        // Nearly the whole picture: the photo shows only the page.
        if (quad.area > 0.95f) return null
        return quad
    }

    /** Box blur with radius [r] (twice, close to a Gaussian). */
    private fun blur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        var a = src
        repeat(2) {
            val tmp = IntArray(w * h)
            for (y in 0 until h) {
                var sum = 0
                for (x in -r..r) sum += a[y * w + x.coerceIn(0, w - 1)]
                for (x in 0 until w) {
                    tmp[y * w + x] = sum / (2 * r + 1)
                    sum += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)]
                }
            }
            val out = IntArray(w * h)
            for (x in 0 until w) {
                var sum = 0
                for (y in -r..r) sum += tmp[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    out[y * w + x] = sum / (2 * r + 1)
                    sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
                }
            }
            a = out
        }
        return a
    }

    /** Threshold between dark and bright that separates them best (Otsu). */
    internal fun otsu(gray: IntArray): Int {
        val hist = IntArray(256)
        gray.forEach { hist[it.coerceIn(0, 255)]++ }
        val total = gray.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0.0
        var best = 0.0
        var threshold = 127
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                threshold = i
            }
        }
        return threshold
    }

    /** Indices of the largest 4-connected area of [mask]. */
    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): IntArray? {
        val label = IntArray(w * h)
        var bestStart = -1
        var bestSize = 0
        var next = 1
        val stack = IntArray(w * h)
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            var sp = 0
            stack[sp++] = start
            label[start] = next
            var size = 0
            while (sp > 0) {
                val i = stack[--sp]
                size++
                val x = i % w
                val y = i / w
                if (x > 0 && mask[i - 1] && label[i - 1] == 0) { label[i - 1] = next; stack[sp++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && label[i + 1] == 0) { label[i + 1] = next; stack[sp++] = i + 1 }
                if (y > 0 && mask[i - w] && label[i - w] == 0) { label[i - w] = next; stack[sp++] = i - w }
                if (y < h - 1 && mask[i + w] && label[i + w] == 0) { label[i + w] = next; stack[sp++] = i + w }
            }
            if (size > bestSize) {
                bestSize = size
                bestStart = next
            }
            next++
        }
        if (bestStart < 0) return null
        val out = IntArray(bestSize)
        var k = 0
        for (i in label.indices) if (label[i] == bestStart) out[k++] = i
        return out
    }

    /** Pixels of the area [comp] that touch its outside (or the picture's edge), as points. */
    private fun boundary(comp: IntArray, w: Int, h: Int): List<Pair<Double, Double>> {
        val inside = BooleanArray(w * h)
        comp.forEach { inside[it] = true }
        return comp.filter { i ->
            val x = i % w
            val y = i / w
            x == 0 || y == 0 || x == w - 1 || y == h - 1 || !inside[i - 1] || !inside[i + 1] || !inside[i - w] || !inside[i + w]
        }.map { (it % w).toDouble() to (it / w).toDouble() }
    }

    /** Convex hull (Andrew's monotone chain), counter-clockwise in maths terms. */
    internal fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val p = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (p.size < 3) return p
        fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Double, Double>>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], pt) <= 0) lower.removeAt(lower.size - 1)
            lower += pt
        }
        val upper = ArrayList<Pair<Double, Double>>()
        for (pt in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], pt) <= 0) upper.removeAt(upper.size - 1)
            upper += pt
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun triangle(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>) =
        abs((b.first - a.first) * (c.second - a.second) - (c.first - a.first) * (b.second - a.second)) / 2

    internal fun polygonArea(p: List<Pair<Double, Double>>): Double {
        var s = 0.0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            s += a.first * b.second - b.first * a.second
        }
        return abs(s) / 2
    }

    /** The four hull points spanning the largest quadrilateral: for every diagonal (i, k) the best point on each side. */
    internal fun largestQuad(hull: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val n = hull.size
        if (n <= 4) return hull
        var best = 0.0
        var quad = hull.take(4)
        for (i in 0 until n) for (k in i + 2 until n) {
            if (i == 0 && k == n - 1) continue
            var left = 0.0; var lj = -1
            for (j in i + 1 until k) triangle(hull[i], hull[j], hull[k]).let { if (it > left) { left = it; lj = j } }
            var right = 0.0; var rl = -1
            for (l in (k + 1 until n) + (0 until i)) triangle(hull[k], hull[l], hull[i]).let { if (it > right) { right = it; rl = l } }
            if (lj >= 0 && rl >= 0 && left + right > best) {
                best = left + right
                quad = listOf(hull[i], hull[lj], hull[k], hull[rl])
            }
        }
        return quad
    }

    // ── Straightening ───────────────────────────────────────────────────────────────────────────────────────

    /** Width and height of the straightened page: the quadrilateral's sides, A4 if it is nearly A4, at most [PAGE_SIDE]. */
    fun outputSize(photo: ScanRaster, quad: ScanQuad, maxSide: Int = PAGE_SIDE): Pair<Int, Int> {
        fun d(a: ScanPoint, b: ScanPoint) = hypot(((a.x - b.x) * photo.width).toDouble(), ((a.y - b.y) * photo.height).toDouble())
        var w = max(d(quad.tl, quad.tr), d(quad.bl, quad.br))
        var h = max(d(quad.tl, quad.bl), d(quad.tr, quad.br))
        // Photos taken at a slant shorten the far side: a page close to A4 becomes exactly A4.
        val ratio = max(w, h) / max(1.0, min(w, h))
        if (abs(ratio - A4) / A4 < 0.12) {
            if (h >= w) h = w * A4 else w = h * A4
        }
        val scale = min(1.0, maxSide / max(w, h))
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    /**
     * The page inside [quad] as a straight rectangle (perspective corrected): every pixel of the result comes from its
     * place in the photo (homography, bilinear).
     */
    fun warp(photo: ScanRaster, quad: ScanQuad, maxSide: Int = PAGE_SIDE, inset: Float = 0.004f): ScanRaster {
        val (w, h) = outputSize(photo, quad, maxSide)
        // A hair inside the edges, so no thin line of the desk stays at the border.
        val cx = quad.points.sumOf { it.x.toDouble() } / 4
        val cy = quad.points.sumOf { it.y.toDouble() } / 4
        val src = quad.points.map {
            val x = it.x + (cx - it.x) * inset * 2
            val y = it.y + (cy - it.y) * inset * 2
            (x * (photo.width - 1)) to (y * (photo.height - 1))
        }
        val dst = listOf(0.0 to 0.0, (w - 1).toDouble() to 0.0, (w - 1).toDouble() to (h - 1).toDouble(), 0.0 to (h - 1).toDouble())
        val m = homography(dst, src)
        val out = ScanRaster(w, h)
        val pw = photo.width
        val ph = photo.height
        val px = photo.pixels
        for (y in 0 until h) {
            for (x in 0 until w) {
                val z = m[6] * x + m[7] * y + 1.0
                val u = ((m[0] * x + m[1] * y + m[2]) / z).coerceIn(0.0, pw - 1.0)
                val v = ((m[3] * x + m[4] * y + m[5]) / z).coerceIn(0.0, ph - 1.0)
                val x0 = u.toInt(); val y0 = v.toInt()
                val x1 = min(x0 + 1, pw - 1); val y1 = min(y0 + 1, ph - 1)
                val fx = u - x0; val fy = v - y0
                val p00 = px[y0 * pw + x0]; val p10 = px[y0 * pw + x1]; val p01 = px[y1 * pw + x0]; val p11 = px[y1 * pw + x1]
                fun ch(shift: Int): Int {
                    val a = (p00 shr shift) and 0xFF; val b = (p10 shr shift) and 0xFF
                    val c = (p01 shr shift) and 0xFF; val d = (p11 shr shift) and 0xFF
                    return ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy).roundToInt()
                }
                out.pixels[y * w + x] = ScanRaster.argb(ch(16), ch(8), ch(0))
            }
        }
        return out
    }

    /** The projective map taking the four points [from] to [to] (a…h of x' = (ax+by+c)/(gx+hy+1), y' = (dx+ey+f)/(gx+hy+1)). */
    internal fun homography(from: List<Pair<Double, Double>>, to: List<Pair<Double, Double>>): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = from[i]
            val (u, v) = to[i]
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
        }
        // Gauss–Jordan with pivoting.
        for (col in 0 until 8) {
            val pivot = (col until 8).maxBy { abs(a[it][col]) }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val p = a[col][col]
            require(abs(p) > 1e-12) { "Die Ecken liegen auf einer Linie." }
            for (k in col..8) a[col][k] /= p
            for (r in 0 until 8) if (r != col) {
                val f = a[r][col]
                if (f != 0.0) for (k in col..8) a[r][k] -= f * a[col][k]
            }
        }
        return DoubleArray(8) { a[it][8] }
    }

    // ── Contrast ────────────────────────────────────────────────────────────────────────────────────────────

    /** The look of [mode]: white paper, dark text; shadows from the hand or lamp evened out for grey and black-and-white. */
    fun enhance(page: ScanRaster, mode: ScanMode): ScanRaster = when (mode) {
        ScanMode.ORIGINAL -> page
        ScanMode.COLOR -> color(page)
        ScanMode.GRAY -> gray(page)
        ScanMode.BW -> blackWhite(page)
    }

    private fun percentile(hist: IntArray, total: Int, p: Double): Int {
        var acc = 0
        val target = (total * p).toInt()
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= target) return i
        }
        return 255
    }

    /** White balance from the paper (the brightest tenth) and stretched levels. */
    private fun color(page: ScanRaster): ScanRaster {
        val px = page.pixels
        val hist = IntArray(256)
        px.forEach { hist[ScanRaster.luma(it)]++ }
        val white = percentile(hist, px.size, 0.90)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (p in px) if (ScanRaster.luma(p) >= white) {
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
        }
        if (n == 0) return page
        val wr = max(1.0, r.toDouble() / n); val wg = max(1.0, g.toDouble() / n); val wb = max(1.0, b.toDouble() / n)
        val black = percentile(hist, px.size, 0.01).toDouble()
        val out = ScanRaster(page.width, page.height)
        fun level(v: Int, paper: Double): Int {
            val bl = black * paper / 255.0
            val x = ((v - bl) / max(1.0, paper * 0.97 - bl)).coerceIn(0.0, 1.0)
            // A little more contrast in the mid tones (text stays dark, paper white).
            return (255 * (x * x * (3 - 2 * x) * 0.35 + x * 0.65)).roundToInt()
        }
        for (i in px.indices) {
            val p = px[i]
            out.pixels[i] = ScanRaster.argb(level((p shr 16) and 0xFF, wr), level((p shr 8) and 0xFF, wg), level(p and 0xFF, wb))
        }
        return out
    }

    /** Integral image of [v] (one row and column more). */
    private fun integral(v: IntArray, w: Int, h: Int): LongArray {
        val s = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += v[y * w + x]
                s[(y + 1) * (w + 1) + x + 1] = s[y * (w + 1) + x + 1] + row
            }
        }
        return s
    }

    private fun boxMean(s: LongArray, w: Int, h: Int, x: Int, y: Int, r: Int): Double {
        val x0 = max(0, x - r); val y0 = max(0, y - r); val x1 = min(w, x + r + 1); val y1 = min(h, y + r + 1)
        val sum = s[y1 * (w + 1) + x1] - s[y0 * (w + 1) + x1] - s[y1 * (w + 1) + x0] + s[y0 * (w + 1) + x0]
        return sum.toDouble() / ((x1 - x0) * (y1 - y0))
    }

    /** The paper's brightness around every pixel: the local mean without the dark text (two passes). */
    private fun background(luma: IntArray, w: Int, h: Int): DoubleArray {
        val r = max(8, max(w, h) / 24)
        val first = integral(luma, w, h)
        // Second pass: dark pixels (text) replaced by their surroundings, so letters do not darken the paper estimate.
        val light = IntArray(luma.size) { i ->
            val m = boxMean(first, w, h, i % w, i / w, r)
            if (luma[i] < m * 0.85) m.toInt() else luma[i]
        }
        val second = integral(light, w, h)
        return DoubleArray(luma.size) { i -> max(1.0, boxMean(second, w, h, i % w, i / w, r)) }
    }

    /** Grey with even paper: every pixel divided by the paper's local brightness. */
    private fun gray(page: ScanRaster): ScanRaster {
        val w = page.width; val h = page.height
        val luma = IntArray(w * h) { ScanRaster.luma(page.pixels[it]) }
        val bg = background(luma, w, h)
        val out = ScanRaster(w, h)
        for (i in luma.indices) {
            val x = (luma[i] / (bg[i] * 0.96)).coerceIn(0.0, 1.0)
            // Darker text: gamma above 1 pulls the grey of thin strokes towards black.
            val v = (255 * Math.pow(x, 1.6)).roundToInt()
            out.pixels[i] = ScanRaster.argb(v, v, v)
        }
        return out
    }

    /** Black and white with a threshold that follows the paper (shadows do not turn black). */
    private fun blackWhite(page: ScanRaster): ScanRaster {
        val w = page.width; val h = page.height
        val luma = IntArray(w * h) { ScanRaster.luma(page.pixels[it]) }
        val bg = background(luma, w, h)
        val out = ScanRaster(w, h)
        for (i in luma.indices) {
            val v = if (luma[i] < bg[i] * 0.78) 0 else 255
            out.pixels[i] = ScanRaster.argb(v, v, v)
        }
        return out
    }

    // ── Putting the PDF together ────────────────────────────────────────────────────────────────────────────

    /** The finished picture of [page]: read, straightened, turned, with its look. */
    fun process(page: ScanPage, maxSide: Int = PAGE_SIDE, photo: ScanRaster? = null): ScanRaster {
        val src = photo ?: ScanImages.read(page.source, maxOf(maxSide * 3 / 2, 1600))
        val straight = if (page.quad == ScanQuad.FULL) src.scaledDown(maxSide) else warp(src, page.quad, maxSide)
        return enhance(straight.rotated(page.rotation), page.mode)
    }

    /** Name of a new scan: „Scan 2026-11-08 14.30“. */
    fun defaultName(now: Long = System.currentTimeMillis()): String = "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.GERMANY).format(Date(now))

    /** Text recognition for the saved PDF: the pages (0-based) to read → what was recognized and by which engine. */
    fun interface Ocr {
        suspend fun recognize(pdf: File, pages: List<Int>, progress: (Int, Int) -> Unit): Pair<List<OcrPage>, String>
    }

    data class Saved(val file: File, val pages: Int, val textPages: Int, val engine: String?, val ocrError: String? = null)

    /**
     * Saves [pages] as PDF [out]: one A4 page per scan (JPEG, black-and-white pages as PNG), then – with [ocr] – the
     * recognized text laid invisibly over the pages, so the PDF can be searched and copied. Without text recognition (or
     * if it fails) the PDF is still saved.
     */
    suspend fun save(
        context: android.content.Context?,
        pages: List<ScanPage>,
        out: File,
        ocr: Ocr?,
        progress: (String, Int, Int) -> Unit = { _, _, _ -> },
    ): Saved {
        require(pages.isNotEmpty()) { "Keine Seiten" }
        out.parentFile?.mkdirs()
        val images = pages.mapIndexed { i, p ->
            progress("pages", i, pages.size)
            val r = process(p)
            val png = p.mode == ScanMode.BW
            "seite${i + 1}." + (if (png) "png" else "jpg") to if (png) ScanImages.png(r) else ScanImages.jpeg(r, 85)
        }
        progress("pages", pages.size, pages.size)
        val plain = File(out.parentFile, out.nameWithoutExtension + ".scan.tmp")
        PdfExtras.imagesToPdf(context, images, plain)
        if (ocr == null) {
            moveOver(plain, out)
            return Saved(out, pages.size, 0, null)
        }
        val result = runCatching { ocr.recognize(plain, pages.indices.toList()) { d, t -> progress("ocr", d, t) } }
        val found = result.getOrNull()?.first.orEmpty().filter { it.lines.isNotEmpty() }
        if (found.isEmpty()) {
            moveOver(plain, out)
            return Saved(out, pages.size, 0, result.getOrNull()?.second, result.exceptionOrNull()?.message ?: "Kein Text erkannt")
        }
        PdfExtras.addTextLayer(context, plain, found, out)
        plain.delete()
        return Saved(out, pages.size, found.size, result.getOrNull()?.second)
    }

    private fun moveOver(from: File, to: File) {
        to.delete()
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    /** A free name for [name].pdf in [dir]. */
    fun target(dir: File, name: String): File {
        val base = name.trim().removeSuffix(".pdf").ifBlank { defaultName() }.replace(Regex("[\\\\/:*?\"<>|]"), "-")
        var f = File(dir, "$base.pdf")
        var n = 2
        while (f.exists()) f = File(dir, "$base ($n).pdf").also { n++ }
        return f
    }

    /** Average distance of two quadrilaterals' corners (fractions) – for tests and to tell whether the user moved them. */
    fun distance(a: ScanQuad, b: ScanQuad): Float =
        a.points.zip(b.points).map { (p, q) -> sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y)) }.average().toFloat()
}
