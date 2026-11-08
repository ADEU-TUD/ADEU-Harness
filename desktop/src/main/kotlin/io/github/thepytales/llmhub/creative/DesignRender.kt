package io.github.thepytales.llmhub.creative

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.App
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignBrand
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.DesignPage
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.TudBrand
import io.nayuki.qrcodegen.QrCode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Drawing of the designs (Erstellen › Gestalten, AP-24): one function draws a page of a [Design] into a [DrawScope] – the editor's
 * canvas, the previews and the PNG export use it, so everything looks the same. Text is laid out once at a fixed
 * resolution ([REF] px per mm, independent of the zoom) with the house font Noto Sans; the PDF and PowerPoint exports
 * take the same line breaks from [DesignText.layout].
 */
object DesignFonts {
    private fun bytes(name: String): ByteArray =
        (DesignFonts::class.java.getResourceAsStream("/fonts/$name") ?: error("Schrift $name fehlt")).use { it.readBytes() }

    val regular: ByteArray by lazy { bytes("NotoSans-Regular.ttf") }
    val bold: ByteArray by lazy { bytes("NotoSans-Bold.ttf") }
    val italic: ByteArray by lazy { bytes("NotoSans-Italic.ttf") }
    val boldItalic: ByteArray by lazy { bytes("NotoSans-BoldItalic.ttf") }
    /** Noto Sans Medium: headings in the CD of TU Dresden. */
    val medium: ByteArray by lazy { bytes("NotoSans-Medium.ttf") }
    val mediumItalic: ByteArray by lazy { bytes("NotoSans-MediumItalic.ttf") }

    fun bytes(bold: Boolean, italic: Boolean, medium: Boolean = false): ByteArray = when {
        bold && italic -> boldItalic
        bold -> this.bold
        medium && italic -> mediumItalic
        medium -> this.medium
        italic -> this.italic
        else -> regular
    }

    /** Noto Sans in four styles; falls back to the system's sans serif if the files are missing. */
    val family: FontFamily by lazy {
        runCatching {
            FontFamily(
                Font("NotoSans-Regular", regular, FontWeight.Normal, FontStyle.Normal),
                Font("NotoSans-Bold", bold, FontWeight.Bold, FontStyle.Normal),
                Font("NotoSans-Italic", italic, FontWeight.Normal, FontStyle.Italic),
                Font("NotoSans-BoldItalic", boldItalic, FontWeight.Bold, FontStyle.Italic),
                Font("NotoSans-Medium", medium, FontWeight.Medium, FontStyle.Normal),
                Font("NotoSans-MediumItalic", mediumItalic, FontWeight.Medium, FontStyle.Italic),
            )
        }.getOrDefault(FontFamily.SansSerif)
    }
}

/** Text boxes: layout at [REF] px per mm, shrinking to fit for [DesignElement.fit]. */
object DesignText {
    /** Pixels per mm of the text layout (the drawing scales it to the zoom). */
    const val REF = 8f

    private val measurer by lazy { TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, 32) }

    /** A laid-out text box: [layout] in REF pixels, [fontSize] the size actually used (pt), [top] the offset in mm. */
    class Laid(val layout: TextLayoutResult, val fontSize: Float, val top: Float, val lineHeightMm: Float)

    private data class Key(val text: String, val size: Float, val bold: Boolean, val medium: Boolean, val italic: Boolean, val w: Float, val h: Float, val lh: Float, val align: String, val valign: String, val fit: Boolean, val pad: Float)

    private val cache = object : LinkedHashMap<Key, Laid>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Laid>?) = size > 300
    }

    private fun style(sizePt: Float, e: DesignElement): TextStyle {
        val px = sizePt * DesignFormat.MM_PER_PT * REF
        return TextStyle(
            fontFamily = DesignFonts.family,
            fontSize = px.sp,
            lineHeight = (px * e.lineHeight).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontWeight = if (e.bold) FontWeight.Bold else if (e.medium) FontWeight.Medium else FontWeight.Normal,
            fontStyle = if (e.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = when (e.align) { "center" -> TextAlign.Center; "right" -> TextAlign.End; else -> TextAlign.Start },
        )
    }

    private fun measure(text: String, sizePt: Float, e: DesignElement, widthPx: Int): TextLayoutResult =
        measurer.measure(text, style(sizePt, e), constraints = Constraints(minWidth = widthPx, maxWidth = widthPx))

    /** Width of the longest word at [sizePt] in REF px (a word must not be broken across lines). */
    private fun longestWord(text: String, sizePt: Float, e: DesignElement): Float {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.sortedByDescending { it.length }.take(3)
        return words.maxOfOrNull { w -> measurer.measure(w, style(sizePt, e).copy(textAlign = TextAlign.Start), softWrap = false).size.width.toFloat() } ?: 0f
    }

    /** Height (mm) the box needs for its text at its own font size (text boxes grow downwards while typing). */
    fun neededHeight(e: DesignElement): Float =
        layout(e.copy(fit = false, h = 100_000f, valign = "top")).layout.size.height / REF + 2 * e.padding

    @Synchronized
    fun layout(e: DesignElement): Laid {
        val key = Key(e.text, e.fontSize, e.bold, e.medium, e.italic, e.w, e.h, e.lineHeight, e.align, e.valign, e.fit, e.padding)
        cache[key]?.let { return it }
        val text = e.text.ifEmpty { " " }
        val innerW = (e.w - 2 * e.padding).coerceAtLeast(1f)
        val innerH = (e.h - 2 * e.padding).coerceAtLeast(1f)
        val widthPx = (innerW * REF).toInt().coerceAtLeast(1)
        var size = e.fontSize.coerceIn(2f, 1000f)
        var result = measure(text, size, e, widthPx)
        fun fits(r: TextLayoutResult, s: Float) = r.size.height <= innerH * REF + 0.5f && longestWord(text, s, e) <= widthPx + 0.5f
        if (e.fit && !fits(result, size)) {
            // Largest size that fits (bisection down to 2 pt, 0.25 pt steps).
            var lo = 2f
            var hi = size
            var best = measure(text, lo, e, widthPx) to lo
            while (hi - lo > 0.25f) {
                val mid = (lo + hi) / 2
                val r = measure(text, mid, e, widthPx)
                if (fits(r, mid)) { lo = mid; best = r to mid } else hi = mid
            }
            result = best.first
            size = best.second
        }
        val heightMm = result.size.height / REF
        val top = e.padding + when (e.valign) {
            "middle" -> (innerH - heightMm) / 2
            "bottom" -> innerH - heightMm
            else -> 0f
        }
        return Laid(result, size, top, size * DesignFormat.MM_PER_PT * e.lineHeight).also { cache[key] = it }
    }
}

/** Symbols of the editor: Material icons with German names (stored by their English name). */
object DesignIcons {
    val ALL: List<Triple<String, String, ImageVector>> = listOf(
        Triple("School", "Hochschule", Icons.Filled.School),
        Triple("MenuBook", "Buch", Icons.Filled.MenuBook),
        Triple("Science", "Forschung", Icons.Filled.Science),
        Triple("Biotech", "Labor", Icons.Filled.Biotech),
        Triple("Psychology", "Kopf", Icons.Filled.Psychology),
        Triple("Lightbulb", "Idee", Icons.Filled.Lightbulb),
        Triple("Event", "Datum", Icons.Filled.Event),
        Triple("Schedule", "Uhrzeit", Icons.Filled.Schedule),
        Triple("Place", "Ort", Icons.Filled.Place),
        Triple("Person", "Person", Icons.Filled.Person),
        Triple("Groups", "Gruppe", Icons.Filled.Groups),
        Triple("Mail", "Mail", Icons.Filled.Mail),
        Triple("Phone", "Telefon", Icons.Filled.Phone),
        Triple("Language", "Web", Icons.Filled.Language),
        Triple("Info", "Info", Icons.Filled.Info),
        Triple("CheckCircle", "Haken", Icons.Filled.CheckCircle),
        Triple("Star", "Stern", Icons.Filled.Star),
        Triple("Favorite", "Herz", Icons.Filled.Favorite),
        Triple("EmojiEvents", "Pokal", Icons.Filled.EmojiEvents),
        Triple("WorkspacePremium", "Auszeichnung", Icons.Filled.WorkspacePremium),
        Triple("Campaign", "Ankündigung", Icons.Filled.Campaign),
        Triple("Forum", "Diskussion", Icons.Filled.Forum),
        Triple("Mic", "Vortrag", Icons.Filled.Mic),
        Triple("Videocam", "Video", Icons.Filled.Videocam),
        Triple("Computer", "Computer", Icons.Filled.Computer),
        Triple("BarChart", "Diagramm", Icons.Filled.BarChart),
        Triple("PieChart", "Torte", Icons.Filled.PieChart),
        Triple("TrendingUp", "Anstieg", Icons.Filled.TrendingUp),
        Triple("Public", "Welt", Icons.Filled.Public),
        Triple("Eco", "Nachhaltig", Icons.Filled.Eco),
        Triple("Accessibility", "Barrierefrei", Icons.Filled.Accessibility),
        Triple("Restaurant", "Essen", Icons.Filled.Restaurant),
        Triple("LocalCafe", "Kaffee", Icons.Filled.LocalCafe),
        Triple("DirectionsBus", "Anreise", Icons.Filled.DirectionsBus),
        Triple("ArrowForward", "Pfeil", Icons.Filled.ArrowForward),
        Triple("QrCode2", "QR", Icons.Filled.QrCode2),
        Triple("Image", "Bild", Icons.Filled.Image),
    )

    fun vector(name: String): ImageVector? = ALL.firstOrNull { it.first.equals(name, ignoreCase = true) }?.third

    private val paths = HashMap<String, List<Path>>()

    /** The icon's paths in its viewport (24 × 24 for Material symbols, the icon's own box for TUD icons). */
    @Synchronized
    fun paths(name: String): List<Path> = paths.getOrPut(name) {
        TudIcons.byId(name)?.let { return@getOrPut listOf(TudIcons.path(it)) }
        val v = vector(name) ?: return@getOrPut emptyList()
        val out = mutableListOf<Path>()
        fun walk(g: VectorGroup) {
            for (n in g) when (n) {
                is VectorPath -> out += PathParser().addPathNodes(n.pathData).toPath().apply { fillType = n.pathFillType }
                is VectorGroup -> walk(n)
            }
        }
        walk(v.root)
        out
    }

    fun viewport(name: String): Size = TudIcons.byId(name)?.box?.size ?: vector(name)?.let { Size(it.viewportWidth, it.viewportHeight) } ?: Size(24f, 24f)
}

/** Pictures of designs, decoded once (logos of the app, own logos, pictures in the design's folder). */
object DesignImages {
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 48
    }

    /** Encoded bytes of [src] for design [designId] (PNG/JPEG …), or null. */
    fun bytes(designId: String, src: String): ByteArray? = when {
        src.isBlank() -> null
        src == DesignBrand.LOGO_BLUE -> TudBrand.logoBlue
        src == DesignBrand.LOGO_WHITE -> TudBrand.logoWhite
        src.startsWith("logo:") -> runCatching {
            val c = App.container
            c.logos.get(src.removePrefix("logo:"))?.let { File(it.path).readBytes() }
        }.getOrNull()
        else -> runCatching { App.container.designs.asset(designId, src)?.readBytes() }.getOrNull()
    }

    @Synchronized
    fun image(designId: String, src: String): ImageBitmap? {
        if (src.isBlank()) return null
        val key = if (src.startsWith("logo:")) src else "$designId/$src"
        if (cache.containsKey(key)) return cache[key]
        val img = bytes(designId, src)?.let { b -> runCatching { Image.makeFromEncoded(b).toComposeImageBitmap() }.getOrNull() }
        cache[key] = img
        return img
    }

    @Synchronized
    fun forget(designId: String, src: String) {
        cache.remove(if (src.startsWith("logo:")) src else "$designId/$src")
    }
}

/** QR codes (error correction M) as module grids. */
object DesignQr {
    private val cache = object : LinkedHashMap<String, QrCode?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QrCode?>?) = size > 32
    }

    @Synchronized
    fun code(text: String): QrCode? = cache.getOrPut(text) { runCatching { QrCode.encodeText(text.ifBlank { " " }, QrCode.Ecc.MEDIUM) }.getOrNull() }

    /** Quiet zone in modules around the code (inside the element's box). */
    const val QUIET = 2

    /** The dark modules as one path in the element's box ([w] × [h] mm, square in the middle). */
    fun path(text: String, w: Float, h: Float): Path {
        val qr = code(text) ?: return Path()
        val side = min(w, h)
        val n = qr.size + 2 * QUIET
        val m = side / n
        val ox = (w - side) / 2 + QUIET * m
        val oy = (h - side) / 2 + QUIET * m
        val p = Path()
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.getModule(x, y)) p.addRect(Rect(ox + x * m - 0.001f, oy + y * m - 0.001f, ox + (x + 1) * m + 0.001f, oy + (y + 1) * m + 0.001f))
        }
        return p
    }
}

/** Colour of "#RRGGBB" (null for empty or invalid values). */
fun designColor(hex: String, alpha: Float = 1f): Color? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v).copy(alpha = alpha.coerceIn(0f, 1f))
}

/** Geometry of shapes in the element's own box (mm, before rotation). */
object DesignShapes {
    fun path(e: DesignElement): Path = Path().apply {
        val w = e.w
        val h = e.h
        when (e.kind) {
            ElementKind.RECT -> if (e.radius > 0f) addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(min(e.radius, min(w, h) / 2)))) else addRect(Rect(0f, 0f, w, h))
            ElementKind.ELLIPSE -> addOval(Rect(0f, 0f, w, h))
            ElementKind.TRIANGLE -> { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }
            ElementKind.STAR -> {
                val cx = w / 2
                val cy = h / 2
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 1f else 0.42f
                    val a = -PI / 2 + i * PI / 5
                    val px = cx + (w / 2) * r * cos(a).toFloat()
                    val py = cy + (h / 2) * r * sin(a).toFloat() + h * 0.04f
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            ElementKind.ARROW -> {
                // Arrow head as a filled triangle at the end of the line (from top left to bottom right of the box).
                val len = kotlin.math.hypot(w, h).coerceAtLeast(0.01f)
                val ux = w / len
                val uy = h / len
                val head = max(e.strokeWidth * 4f, 2.5f).coerceAtMost(len * 0.6f)
                val bx = w - ux * head
                val by = h - uy * head
                moveTo(w, h)
                lineTo(bx - uy * head * 0.55f, by + ux * head * 0.55f)
                lineTo(bx + uy * head * 0.55f, by - ux * head * 0.55f)
                close()
            }
            else -> addRect(Rect(0f, 0f, w, h))
        }
    }

    /** End of the arrow's line, where its head begins. */
    fun arrowLineEnd(e: DesignElement): Offset {
        val len = kotlin.math.hypot(e.w, e.h).coerceAtLeast(0.01f)
        val head = max(e.strokeWidth * 4f, 2.5f).coerceAtMost(len * 0.6f)
        return Offset(e.w - e.w / len * head * 0.8f, e.h - e.h / len * head * 0.8f)
    }
}

/** The TUD-Shape for a picture placed into it (mirrored with [DesignElement.flipX]). */
internal fun scaledShape(e: DesignElement): Path {
    val p = TudShape.inBox(e.w, e.h)
    if (!e.flipX) return p
    val m = androidx.compose.ui.graphics.Matrix().apply { translate(e.w, 0f); scale(-1f, 1f) }
    p.transform(m)
    return p
}

/** Source rectangle (image pixels) and target rectangle (mm in the element's box) of a picture. */
data class PicturePlacement(val src: Rect, val dst: Rect)

fun placePicture(e: DesignElement, imageW: Int, imageH: Int): PicturePlacement {
    val cx = e.cropX.coerceIn(0f, 0.99f)
    val cy = e.cropY.coerceIn(0f, 0.99f)
    val cw = e.cropW.coerceIn(0.01f, 1f - cx)
    val ch = e.cropH.coerceIn(0.01f, 1f - cy)
    var src = Rect(cx * imageW, cy * imageH, (cx + cw) * imageW, (cy + ch) * imageH)
    val box = Rect(0f, 0f, e.w, e.h)
    if (e.w <= 0f || e.h <= 0f || src.width <= 0f || src.height <= 0f) return PicturePlacement(src, box)
    val srcRatio = src.width / src.height
    val boxRatio = e.w / e.h
    return if (e.fitMode == "contain") {
        val dst = if (srcRatio > boxRatio) {
            val h = e.w / srcRatio
            Rect(0f, (e.h - h) / 2, e.w, (e.h + h) / 2)
        } else {
            val w = e.h * srcRatio
            Rect((e.w - w) / 2, 0f, (e.w + w) / 2, e.h)
        }
        PicturePlacement(src, dst)
    } else {
        // Cover: the middle part of the crop with the box's aspect ratio.
        src = if (srcRatio > boxRatio) {
            val w = src.height * boxRatio
            Rect(src.center.x - w / 2, src.top, src.center.x + w / 2, src.bottom)
        } else {
            val h = src.width / boxRatio
            Rect(src.left, src.center.y - h / 2, src.right, src.center.y + h / 2)
        }
        PicturePlacement(src, box)
    }
}

/**
 * Draws page [page] of [design] at [scale] px per mm with the page's top left at the origin. [editing] shows empty
 * picture placeholders; [hidden] elements are left out (the one being edited as text, for instance).
 */
fun DrawScope.drawDesignPage(design: Design, page: DesignPage, scale: Float, editing: Boolean = false, hidden: Set<String> = emptySet()) {
    designColor(page.background)?.let { drawRect(it, Offset.Zero, Size(design.width * scale, design.height * scale)) }
    page.elements.forEach { e -> if (e.id !in hidden) drawDesignElement(design, e, scale, editing) }
}

fun DrawScope.drawDesignElement(design: Design, e: DesignElement, scale: Float, editing: Boolean = false) {
    withTransform({
        translate(e.x * scale, e.y * scale)
        if (e.rotation != 0f) rotate(e.rotation, Offset(e.w * scale / 2, e.h * scale / 2))
        scale(scale, scale, Offset.Zero)
    }) {
        // From here on 1 unit = 1 mm in the element's box.
        drawElementLocal(design, e, editing)
    }
}

private fun DrawScope.drawElementLocal(design: Design, e: DesignElement, editing: Boolean) {
    val alpha = e.opacity.coerceIn(0f, 1f)
    when (e.kind) {
        ElementKind.TEXT -> {
            val corner = CornerRadius(min(e.radius, min(e.w, e.h) / 2))
            gradientOf(e, alpha)?.let { drawRoundRect(it, Offset.Zero, Size(e.w, e.h), corner) }
                ?: designColor(e.fill, alpha)?.let { bg -> drawRoundRect(bg, Offset.Zero, Size(e.w, e.h), corner) }
            designColor(e.stroke, alpha)?.takeIf { e.strokeWidth > 0f }?.let { s ->
                drawRoundRect(s, Offset.Zero, Size(e.w, e.h), CornerRadius(min(e.radius, min(e.w, e.h) / 2)), style = Stroke(e.strokeWidth))
            }
            if (e.text.isNotEmpty()) {
                val laid = DesignText.layout(e)
                translate(e.padding, laid.top) {
                    scale(1f / DesignText.REF, 1f / DesignText.REF, Offset.Zero) {
                        drawText(laid.layout, color = designColor(e.color) ?: Color.Black, alpha = alpha)
                    }
                }
            }
        }
        ElementKind.LINE -> {
            val c = designColor(e.stroke.ifBlank { e.color }, alpha) ?: return
            drawLine(c, Offset.Zero, Offset(e.w, e.h), e.strokeWidth.coerceAtLeast(0.1f), StrokeCap.Round)
        }
        ElementKind.ARROW -> {
            val c = designColor(e.stroke.ifBlank { e.color }, alpha) ?: return
            drawLine(c, Offset.Zero, DesignShapes.arrowLineEnd(e), e.strokeWidth.coerceAtLeast(0.1f), StrokeCap.Round)
            drawPath(DesignShapes.path(e), c, style = Fill)
        }
        ElementKind.RECT, ElementKind.ELLIPSE, ElementKind.TRIANGLE, ElementKind.STAR -> {
            val p = DesignShapes.path(e)
            gradientOf(e, alpha)?.let { drawPath(p, it, style = Fill) } ?: designColor(e.fill, alpha)?.let { drawPath(p, it, style = Fill) }
            designColor(e.stroke, alpha)?.takeIf { e.strokeWidth > 0f }?.let { drawPath(p, it, style = Stroke(e.strokeWidth, join = StrokeJoin.Round)) }
        }
        ElementKind.TUD_SHAPE -> {
            // Always solid (the CD forbids outlines), mirrored left–right on request.
            val c = designColor(e.fill.ifBlank { e.color }, alpha) ?: return
            scale(if (e.flipX) -1f else 1f, 1f, Offset(e.w / 2, e.h / 2)) { drawPath(TudShape.inBox(e.w, e.h), c) }
        }
        ElementKind.IMAGE -> {
            // Official TUD logos: vector paths of the CD package.
            DesignBrand.logo(e.src)?.let { logo ->
                TudLogos.vector(logo.variant)?.let { v ->
                    val k = min(e.w / v.size.width, e.h / v.size.height)
                    translate((e.w - v.size.width * k) / 2, (e.h - v.size.height * k) / 2) {
                        scale(k, k, Offset.Zero) {
                            val c = when { "weiss" in logo.variant -> Color.White; "schwarz" in logo.variant -> Color.Black; else -> designColor(TudBrand.BLUE)!! }.copy(alpha = alpha)
                            v.paths.forEach { drawPath(it, c) }
                        }
                    }
                    return
                }
            }
            val img = DesignImages.image(design.id, e.src)
            val clip = when {
                e.mask == "shape" -> scaledShape(e)
                e.radius > 0f -> Path().apply { addRoundRect(RoundRect(0f, 0f, e.w, e.h, CornerRadius(min(e.radius, min(e.w, e.h) / 2)))) }
                else -> Path().apply { addRect(Rect(0f, 0f, e.w, e.h)) }
            }
            if (img == null) {
                // A Shape waiting for its photo stays a solid Shape in its colour (also in the export).
                if (e.mask == "shape" && !editing) designColor(e.fill, alpha)?.let { drawPath(clip, it) }
                if (editing) {
                    drawPath(clip, designColor(e.fill.ifBlank { "#E5F1FF" }) ?: Color.LightGray)
                    val s = min(e.w, e.h) * 0.3f
                    val icon = DesignIcons.paths("Image")
                    if (icon.isEmpty()) drawRect(Color(0x33000000), Offset(e.w / 2 - s / 2, e.h / 2 - s / 2), Size(s, s), style = Stroke(s * 0.08f))
                    else translate(e.w / 2 - s / 2, e.h / 2 - s / 2) { scale(s / 24f, s / 24f, Offset.Zero) { icon.forEach { drawPath(it, Color(0x55000000)) } } }
                }
                return
            }
            val place = placePicture(e, img.width, img.height)
            clipPath(clip) {
                // drawImage takes whole units: the target is given in 1/IMG_REF mm under an extra scale.
                scale(1f / IMG_REF, 1f / IMG_REF, Offset.Zero) {
                    if (e.blur > 0f) {
                        // Weichzeichnen: Skia's blur (and the colour filters) on the picture.
                        drawIntoCanvas { cv ->
                            val paint = org.jetbrains.skia.Paint().apply {
                                imageFilter = org.jetbrains.skia.ImageFilter.makeBlur(e.blur * IMG_REF / 2, e.blur * IMG_REF / 2, org.jetbrains.skia.FilterTileMode.CLAMP)
                                filterOf(e)?.let { colorFilter = it.asSkiaColorFilter() }
                                this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                            }
                            cv.nativeCanvas.drawImageRect(
                                Image.makeFromBitmap(img.asSkiaBitmap()),
                                org.jetbrains.skia.Rect.makeLTRB(place.src.left, place.src.top, place.src.right, place.src.bottom),
                                org.jetbrains.skia.Rect.makeLTRB(place.dst.left * IMG_REF, place.dst.top * IMG_REF, place.dst.right * IMG_REF, place.dst.bottom * IMG_REF),
                                paint,
                            )
                        }
                    } else drawImage(
                        img,
                        srcOffset = androidx.compose.ui.unit.IntOffset(place.src.left.toInt(), place.src.top.toInt()),
                        srcSize = IntSize(place.src.width.toInt().coerceAtLeast(1), place.src.height.toInt().coerceAtLeast(1)),
                        dstOffset = androidx.compose.ui.unit.IntOffset((place.dst.left * IMG_REF).toInt(), (place.dst.top * IMG_REF).toInt()),
                        dstSize = IntSize((place.dst.width * IMG_REF).toInt().coerceAtLeast(1), (place.dst.height * IMG_REF).toInt().coerceAtLeast(1)),
                        alpha = alpha,
                        colorFilter = filterOf(e),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    )
                }
            }
            designColor(e.stroke, alpha)?.takeIf { e.strokeWidth > 0f }?.let { drawPath(clip, it, style = Stroke(e.strokeWidth)) }
        }
        ElementKind.ICON -> {
            val paths = DesignIcons.paths(e.icon)
            val vp = DesignIcons.viewport(e.icon)
            val k = min(e.w / vp.width, e.h / vp.height)
            val c = designColor(e.color, alpha) ?: Color.Black
            translate((e.w - vp.width * k) / 2, (e.h - vp.height * k) / 2) {
                scale(k, k, Offset.Zero) { paths.forEach { drawPath(it, c) } }
            }
        }
        ElementKind.QR -> {
            designColor(e.fill.ifBlank { "#FFFFFF" }, alpha)?.let { drawRect(it, Offset.Zero, Size(e.w, e.h)) }
            val p = DesignQr.path(e.text, e.w, e.h).apply { fillType = PathFillType.NonZero }
            drawPath(p, designColor(e.color, alpha) ?: Color.Black)
        }
    }
}

/** The linear gradient from [DesignElement.fill] to [DesignElement.fill2] across the box (null: solid). */
fun gradientOf(e: DesignElement, alpha: Float = 1f): androidx.compose.ui.graphics.Brush? {
    val a = designColor(e.fill, alpha) ?: return null
    val b = designColor(e.fill2, alpha) ?: return null
    val rad = Math.toRadians(e.gradientAngle.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val half = (kotlin.math.abs(e.w * dx) + kotlin.math.abs(e.h * dy)) / 2
    val c = Offset(e.w / 2, e.h / 2)
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, b), start = c - Offset(dx * half, dy * half), end = c + Offset(dx * half, dy * half))
}

/** Brightness, contrast, saturation and grey scale of a picture as colour matrix (null: unchanged). */
fun filterOf(e: DesignElement): androidx.compose.ui.graphics.ColorFilter? {
    if (e.brightness == 0f && e.contrast == 0f && e.saturation == 0f && !e.grayscale) return null
    val sat = androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(if (e.grayscale) 0f else (1f + e.saturation).coerceIn(0f, 3f)) }
    val k = (1f + e.contrast).coerceIn(0f, 3f)
    val o = 128f * (1f - k) + 255f * e.brightness.coerceIn(-1f, 1f)
    val cb = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, o, 0f, k, 0f, 0f, o, 0f, 0f, k, 0f, o, 0f, 0f, 0f, 1f, 0f))
    cb.timesAssign(sat)
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(cb)
}

/** Pictures are drawn with whole-unit target rectangles, given in 1/[IMG_REF] mm (see [drawElementLocal]). */
private const val IMG_REF = 100f

/** Renders page [index] of [design] into a bitmap [widthPx] wide (previews, PNG export, PowerPoint pictures). */
fun renderDesignPage(design: Design, index: Int, widthPx: Int, transparent: Boolean = false, only: DesignElement? = null, placeholders: Boolean = false): ImageBitmap {
    val scale = widthPx / design.width
    val w = widthPx.coerceAtLeast(1)
    val h = (design.height * scale).toInt().coerceAtLeast(1)
    val bmp = ImageBitmap(w, h)
    val canvas = Canvas(bmp)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        if (only != null) drawDesignElement(design, only, scale)
        else {
            val page = design.page(index)
            if (transparent) page.elements.forEach { drawDesignElement(design, it, scale) } else drawDesignPage(design, page, scale, editing = placeholders)
        }
    }
    return bmp
}

/** One element alone on a transparent bitmap of its own box (PowerPoint pictures of QR codes and symbols). */
fun renderElement(design: Design, e: DesignElement, pxPerMm: Float): ImageBitmap {
    val w = (e.w * pxPerMm).toInt().coerceAtLeast(1)
    val h = (e.h * pxPerMm).toInt().coerceAtLeast(1)
    val bmp = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
        drawDesignElement(design, e.copy(x = 0f, y = 0f, rotation = 0f), pxPerMm)
    }
    return bmp
}

fun ImageBitmap.pngBytes(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
