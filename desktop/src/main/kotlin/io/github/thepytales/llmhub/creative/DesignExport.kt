package io.github.thepytales.llmhub.creative

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.DocMarkup
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import org.jetbrains.skia.PathVerb
import org.jetbrains.skia.Point
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Exports of the designs (Erstellen › Gestalten, AP-24): PNG (one file per page, as ZIP for several pages), PDF for printing (vector
 * shapes and text with embedded Noto Sans, optionally 3 mm bleed and crop marks) and PowerPoint (real, editable
 * shapes and text boxes; QR codes and symbols as pictures). All three take position, size and line breaks from the
 * same model and text layout as the editor.
 */
object DesignExport {
    private const val PT_PER_MM = 72f / 25.4f

    /** Pixels per mm for a PNG: screen formats at their size (×[factor]), print formats at [dpi], at most ~60 megapixels. */
    fun pngScale(d: Design, dpi: Int = 150, factor: Int = 1): Float {
        val wanted = if (d.screen) factor / DesignFormat.MM_PER_PX else dpi / 25.4f
        val maxPixels = 60_000_000f
        val limit = kotlin.math.sqrt(maxPixels / (d.width * d.height))
        return min(wanted, limit)
    }

    /** One PNG of page [index]. */
    fun png(d: Design, index: Int, pxPerMm: Float): ByteArray = renderDesignPage(d, index, (d.width * pxPerMm).toInt()).pngBytes()

    /** All pages: one PNG, or a ZIP with one PNG per page. Returns the written file. */
    fun writePng(d: Design, target: File, pxPerMm: Float): File {
        if (d.pages.size == 1) {
            target.writeBytes(png(d, 0, pxPerMm))
            return target
        }
        val zip = if (target.extension.lowercase() == "zip") target else File(target.parentFile, target.nameWithoutExtension + ".zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            d.pages.indices.forEach { i ->
                z.putNextEntry(ZipEntry("${target.nameWithoutExtension}-Seite-${i + 1}.png"))
                z.write(png(d, i, pxPerMm))
                z.closeEntry()
            }
        }
        return zip
    }

    // ── PDF ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Writes the design as PDF. [bleed] adds 3 mm around every page (elements touching the edge are extended into it
     * by the page background only) and crop marks, as print shops ask for.
     */
    fun writePdf(d: Design, target: File, bleed: Boolean = false) {
        val extra = if (bleed) 3f else 0f
        val marks = if (bleed) 8f else 0f
        PDDocument().use { doc ->
            val fonts = HashMap<Int, PDType0Font>()
            fun font(e: DesignElement): PDType0Font = fonts.getOrPut((if (e.bold) 1 else 0) + (if (e.italic) 2 else 0) + (if (e.medium && !e.bold) 4 else 0)) {
                PDType0Font.load(doc, ByteArrayInputStream(DesignFonts.bytes(e.bold, e.italic, e.medium)), true)
            }
            val images = HashMap<String, PDImageXObject?>()
            doc.documentInformation.title = d.title
            doc.documentInformation.creator = "Lora"
            d.pages.forEach { page ->
                val wMm = d.width + 2 * (extra + marks)
                val hMm = d.height + 2 * (extra + marks)
                val pdPage = PDPage(PDRectangle(wMm * PT_PER_MM, hMm * PT_PER_MM))
                if (bleed) {
                    val o = (marks) * PT_PER_MM
                    pdPage.bleedBox = PDRectangle(o, o, (d.width + 2 * extra) * PT_PER_MM, (d.height + 2 * extra) * PT_PER_MM)
                    pdPage.trimBox = PDRectangle(o + extra * PT_PER_MM, o + extra * PT_PER_MM, d.width * PT_PER_MM, d.height * PT_PER_MM)
                }
                doc.addPage(pdPage)
                PDPageContentStream(doc, pdPage).use { cs ->
                    // mm with the origin at the top left of the trimmed page, y downwards.
                    cs.transform(Matrix(PT_PER_MM, 0f, 0f, -PT_PER_MM, (marks + extra) * PT_PER_MM, hMm * PT_PER_MM - (marks + extra) * PT_PER_MM))
                    designColor(page.background)?.let { bg ->
                        cs.setNonStrokingColor(bg.red, bg.green, bg.blue)
                        cs.addRect(-extra, -extra, d.width + 2 * extra, d.height + 2 * extra)
                        cs.fill()
                    }
                    page.elements.forEach { e ->
                        runCatching { element(doc, cs, d, e, ::font, images) }
                            .onFailure { io.github.thepytales.llmhub.AppLog.e("PDF-Export: ${e.kind} ${e.id}", it) }
                    }
                    if (bleed) cropMarks(cs, d, extra)
                }
            }
            doc.save(target)
        }
    }

    private fun cropMarks(cs: PDPageContentStream, d: Design, extra: Float) {
        cs.setStrokingColor(0f, 0f, 0f)
        cs.setLineWidth(0.1f)
        val len = 5f
        val gap = extra + 1f
        for ((x, y) in listOf(0f to 0f, d.width to 0f, 0f to d.height, d.width to d.height)) {
            val sx = if (x == 0f) -1f else 1f
            val sy = if (y == 0f) -1f else 1f
            cs.moveTo(x + sx * gap, y); cs.lineTo(x + sx * (gap + len), y)
            cs.moveTo(x, y + sy * gap); cs.lineTo(x, y + sy * (gap + len))
        }
        cs.stroke()
    }

    private fun alphaState(doc: PDDocument, cs: PDPageContentStream, alpha: Float) {
        if (alpha >= 0.999f) return
        val gs = PDExtendedGraphicsState()
        gs.nonStrokingAlphaConstant = alpha
        gs.strokingAlphaConstant = alpha
        cs.setGraphicsStateParameters(gs)
    }

    private fun PDPageContentStream.fillColor(hex: String): Boolean {
        val c = designColor(hex) ?: return false
        setNonStrokingColor(c.red, c.green, c.blue)
        return true
    }

    private fun PDPageContentStream.strokeColor(hex: String): Boolean {
        val c = designColor(hex) ?: return false
        setStrokingColor(c.red, c.green, c.blue)
        return true
    }

    /** Adds a Compose path (Skia segments) to the content stream; quadratic and conic curves become cubic ones. */
    private fun PDPageContentStream.path(p: Path) {
        fun quad(p0: Point, p1: Point, p2: Point) {
            curveTo(p0.x + 2f / 3f * (p1.x - p0.x), p0.y + 2f / 3f * (p1.y - p0.y), p2.x + 2f / 3f * (p1.x - p2.x), p2.y + 2f / 3f * (p1.y - p2.y), p2.x, p2.y)
        }
        for (seg in p.asSkiaPath()) {
            seg ?: continue
            val p0 = seg.p0
            val p1 = seg.p1
            val p2 = seg.p2
            val p3 = seg.p3
            when (seg.verb) {
                PathVerb.MOVE -> p0?.let { moveTo(it.x, it.y) }
                PathVerb.LINE -> p1?.let { lineTo(it.x, it.y) }
                PathVerb.QUAD -> if (p0 != null && p1 != null && p2 != null) quad(p0, p1, p2)
                PathVerb.CONIC -> if (p0 != null && p1 != null && p2 != null) {
                    val pts = org.jetbrains.skia.Path.convertConicToQuads(p0, p1, p2, seg.conicWeight, 2)
                    var i = 0
                    while (i + 2 < pts.size) { quad(pts[i], pts[i + 1], pts[i + 2]); i += 2 }
                }
                PathVerb.CUBIC -> if (p1 != null && p2 != null && p3 != null) curveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                PathVerb.CLOSE -> closePath()
                else -> Unit
            }
        }
    }

    private fun element(
        doc: PDDocument, cs: PDPageContentStream, d: Design, e: DesignElement,
        font: (DesignElement) -> PDType0Font, images: HashMap<String, PDImageXObject?>,
    ) {
        cs.saveGraphicsState()
        try {
            alphaState(doc, cs, e.opacity.coerceIn(0f, 1f))
            cs.transform(Matrix.getTranslateInstance(e.x, e.y))
            if (e.rotation != 0f) {
                cs.transform(Matrix.getTranslateInstance(e.w / 2, e.h / 2))
                cs.transform(Matrix.getRotateInstance(Math.toRadians(e.rotation.toDouble()), 0f, 0f))
                cs.transform(Matrix.getTranslateInstance(-e.w / 2, -e.h / 2))
            }
            when (e.kind) {
                ElementKind.TEXT -> text(cs, e, font)
                ElementKind.LINE -> if (cs.strokeColor(e.stroke.ifBlank { e.color })) {
                    cs.setLineWidth(e.strokeWidth.coerceAtLeast(0.1f)); cs.setLineCapStyle(1)
                    cs.moveTo(0f, 0f); cs.lineTo(e.w, e.h); cs.stroke()
                }
                ElementKind.ARROW -> {
                    val col = e.stroke.ifBlank { e.color }
                    if (cs.strokeColor(col)) {
                        cs.setLineWidth(e.strokeWidth.coerceAtLeast(0.1f)); cs.setLineCapStyle(1)
                        val end = DesignShapes.arrowLineEnd(e)
                        cs.moveTo(0f, 0f); cs.lineTo(end.x, end.y); cs.stroke()
                    }
                    if (cs.fillColor(col)) { cs.path(DesignShapes.path(e)); cs.fill() }
                }
                ElementKind.RECT, ElementKind.ELLIPSE, ElementKind.TRIANGLE, ElementKind.STAR -> {
                    val p = DesignShapes.path(e)
                    val fill = cs.fillColor(e.fill)
                    val stroke = e.strokeWidth > 0f && cs.strokeColor(e.stroke)
                    if (stroke) { cs.setLineWidth(e.strokeWidth); cs.setLineJoinStyle(1) }
                    if (fill || stroke) {
                        cs.path(p)
                        when { fill && stroke -> cs.fillAndStroke(); fill -> cs.fill(); else -> cs.stroke() }
                    }
                }
                ElementKind.TUD_SHAPE -> if (cs.fillColor(e.fill.ifBlank { e.color })) {
                    if (e.flipX) cs.transform(Matrix(-1f, 0f, 0f, 1f, e.w, 0f))
                    cs.path(TudShape.inBox(e.w, e.h)); cs.fill()
                }
                ElementKind.IMAGE -> {
                    val logo = io.github.thepytales.llmhub.data.DesignBrand.logo(e.src)?.let { TudLogos.vector(it.variant)?.let { v -> it to v } }
                    if (logo != null) {
                        // The official logo as vector paths (sharp in print).
                        val (l, v) = logo
                        cs.fillColor(when { "weiss" in l.variant -> "#FFFFFF"; "schwarz" in l.variant -> "#000000"; else -> io.github.thepytales.llmhub.docs.TudBrand.BLUE })
                        val k = min(e.w / v.size.width, e.h / v.size.height)
                        cs.transform(Matrix(k, 0f, 0f, k, (e.w - v.size.width * k) / 2, (e.h - v.size.height * k) / 2))
                        v.paths.forEach { p -> cs.path(p); cs.fill() }
                    } else image(doc, cs, d, e, images)
                }
                ElementKind.ICON -> if (cs.fillColor(e.color)) {
                    val vp = DesignIcons.viewport(e.icon)
                    val k = min(e.w / vp.width, e.h / vp.height)
                    cs.transform(Matrix(k, 0f, 0f, k, (e.w - vp.width * k) / 2, (e.h - vp.height * k) / 2))
                    DesignIcons.paths(e.icon).forEach { p -> cs.path(p); cs.fill() }
                }
                ElementKind.QR -> {
                    if (cs.fillColor(e.fill.ifBlank { "#FFFFFF" })) { cs.addRect(0f, 0f, e.w, e.h); cs.fill() }
                    if (cs.fillColor(e.color)) { cs.path(DesignQr.path(e.text, e.w, e.h)); cs.fill() }
                }
            }
        } finally {
            cs.restoreGraphicsState()
        }
    }

    /** Characters the font can draw (others, e.g. emoji, are left out instead of breaking the export). */
    private fun printable(f: PDType0Font, s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            if (runCatching { f.encode(ch) }.isSuccess) sb.append(ch)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun text(cs: PDPageContentStream, e: DesignElement, font: (DesignElement) -> PDType0Font) {
        val r = e.radius.coerceAtMost(min(e.w, e.h) / 2)
        val box = Path().apply {
            if (r > 0f) addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, e.w, e.h, androidx.compose.ui.geometry.CornerRadius(r)))
            else addRect(androidx.compose.ui.geometry.Rect(0f, 0f, e.w, e.h))
        }
        if (cs.fillColor(e.fill)) { cs.path(box); cs.fill() }
        if (e.strokeWidth > 0f && cs.strokeColor(e.stroke)) { cs.setLineWidth(e.strokeWidth); cs.path(box); cs.stroke() }
        if (e.text.isBlank()) return
        val laid = DesignText.layout(e)
        val layout = laid.layout
        val f = font(e)
        val sizeMm = laid.fontSize * DesignFormat.MM_PER_PT
        val innerW = e.w - 2 * e.padding
        cs.fillColor(e.color.ifBlank { "#000000" })
        for (i in 0 until layout.lineCount) {
            val raw = e.text.ifEmpty { " " }.substring(layout.getLineStart(i), layout.getLineEnd(i, visibleEnd = true))
            val line = printable(f, raw.trimEnd('\n', '\r').trimEnd())
            if (line.isEmpty()) continue
            val width = f.getStringWidth(line) / 1000f * sizeMm
            val x = e.padding + when (e.align) {
                "center" -> (innerW - width) / 2
                "right" -> innerW - width
                else -> 0f
            }
            val baseline = laid.top + layout.getLineBaseline(i) / DesignText.REF
            cs.beginText()
            cs.setFont(f, 1f)
            // Text space: the font size in mm, flipped back upright (the page runs y downwards).
            cs.setTextMatrix(Matrix(sizeMm, 0f, 0f, -sizeMm, x, baseline))
            cs.showText(line)
            cs.endText()
        }
    }

    private fun image(doc: PDDocument, cs: PDPageContentStream, d: Design, e: DesignElement, images: HashMap<String, PDImageXObject?>) {
        val bytes = DesignImages.bytes(d.id, e.src)
        if (bytes == null) {
            // A Shape waiting for its photo stays a solid Shape in its colour.
            if (e.mask == "shape" && cs.fillColor(e.fill)) { cs.path(scaledShape(e)); cs.fill() }
            return
        }
        val x = images.getOrPut(e.src) {
            runCatching {
                if (DocMarkup.imageType(bytes) == "jpg" || DocMarkup.imageType(bytes) == "jpeg") JPEGFactory.createFromByteArray(doc, bytes)
                else {
                    val buffered = ImageIO.read(ByteArrayInputStream(bytes))
                        ?: ImageIO.read(ByteArrayInputStream(org.jetbrains.skia.Image.makeFromEncoded(bytes).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes))
                    LosslessFactory.createFromImage(doc, buffered)
                }
            }.getOrNull()
        } ?: return
        val place = placePicture(e, x.width, x.height)
        // The whole picture so that its source part lands on the target part; the rest is clipped away.
        val kx = place.dst.width / place.src.width
        val ky = place.dst.height / place.src.height
        val left = place.dst.left - place.src.left * kx
        val top = place.dst.top - place.src.top * ky
        cs.saveGraphicsState()
        val r = e.radius.coerceAtMost(min(e.w, e.h) / 2)
        cs.path(if (e.mask == "shape") scaledShape(e) else Path().apply {
            if (r > 0f) addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, e.w, e.h, androidx.compose.ui.geometry.CornerRadius(r)))
            else addRect(androidx.compose.ui.geometry.Rect(0f, 0f, e.w, e.h))
        })
        cs.clip()
        cs.drawImage(x, Matrix(x.width * kx, 0f, 0f, -x.height * ky, left, top + x.height * ky))
        cs.restoreGraphicsState()
        if (e.strokeWidth > 0f && cs.strokeColor(e.stroke)) {
            cs.setLineWidth(e.strokeWidth)
            cs.addRect(0f, 0f, e.w, e.h)
            cs.stroke()
        }
    }

    // ── PowerPoint ──────────────────────────────────────────────────────────────────────────────────────

    private const val EMU_PER_MM = 36000.0
    private fun emu(mm: Float) = Math.round(mm * EMU_PER_MM)
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it >= ' ' }
    private fun hex(c: String) = c.trim().removePrefix("#").uppercase().takeIf { it.length == 6 }
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CT = "application/vnd.openxmlformats-officedocument.presentationml"
    private const val GROUP = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"

    /** PowerPoint slides allow 1–56 inches per side: bigger designs (A0) are scaled down evenly. */
    private fun pptScale(d: Design): Float {
        val maxMm = 56f * 25.4f
        val minMm = 25.4f
        val big = maxOf(d.width, d.height)
        return when {
            big > maxMm -> maxMm / big
            minOf(d.width, d.height) < minMm -> minMm / minOf(d.width, d.height)
            else -> 1f
        }
    }

    private fun solid(hex: String, alpha: Float): String {
        val h = hex(hex) ?: return "<a:noFill/>"
        val a = if (alpha < 0.999f) "<a:alpha val=\"${(alpha * 100000).toInt()}\"/>" else ""
        return "<a:solidFill><a:srgbClr val=\"$h\">$a</a:srgbClr></a:solidFill>"
    }

    private fun xfrm(e: DesignElement, k: Float): String {
        // Lines may run up or to the left (negative size): PowerPoint wants a positive box with flips.
        val x = min(e.x, e.x + e.w)
        val y = min(e.y, e.y + e.h)
        val flip = (if (e.w < 0) " flipH=\"1\"" else "") + (if (e.h < 0) " flipV=\"1\"" else "")
        val rot = if (e.rotation != 0f) " rot=\"${Math.round(((e.rotation % 360 + 360) % 360) * 60000.0)}\"" else ""
        return "<a:xfrm$rot$flip><a:off x=\"${emu(x * k)}\" y=\"${emu(y * k)}\"/>" +
            "<a:ext cx=\"${emu(maxOf(kotlin.math.abs(e.w) * k, 0.01f))}\" cy=\"${emu(maxOf(kotlin.math.abs(e.h) * k, 0.01f))}\"/></a:xfrm>"
    }

    /** Writes the design as .pptx: one slide per page in the size of the design. */
    fun writePptx(d: Design, target: File) {
        val k = pptScale(d)
        val media = LinkedHashMap<String, ByteArray>()
        val slides = d.pages.mapIndexed { i, page ->
            val rels = StringBuilder()
            val shapes = StringBuilder()
            var id = 2
            page.elements.forEach { e ->
                val n = id++
                val name = esc(ElementKind.label(e.kind) + " " + n)
                when (e.kind) {
                    ElementKind.TEXT -> {
                        val laid = DesignText.layout(e)
                        val size = Math.round(laid.fontSize * k * 100).coerceIn(100, 400000)
                        val spacing = Math.round(laid.fontSize * k * e.lineHeight * 100).coerceIn(100, 158400)
                        val algn = when (e.align) { "center" -> "ctr"; "right" -> "r"; else -> "l" }
                        val anchor = when (e.valign) { "middle" -> "ctr"; "bottom" -> "b"; else -> "t" }
                        val pad = emu(e.padding * k)
                        val face = if (e.medium && !e.bold) "Noto Sans Medium" else "Noto Sans"
                        val runProps = "<a:rPr lang=\"de-DE\" sz=\"$size\"${if (e.bold) " b=\"1\"" else ""}${if (e.italic) " i=\"1\"" else ""} dirty=\"0\">" +
                            solid(e.color.ifBlank { "#000000" }, e.opacity) + "<a:latin typeface=\"$face\"/><a:cs typeface=\"$face\"/></a:rPr>"
                        val paras = e.text.split('\n').joinToString("") { line ->
                            "<a:p><a:pPr algn=\"$algn\"><a:lnSpc><a:spcPts val=\"$spacing\"/></a:lnSpc><a:spcBef><a:spcPts val=\"0\"/></a:spcBef><a:spcAft><a:spcPts val=\"0\"/></a:spcAft></a:pPr>" +
                                (if (line.isEmpty()) "" else "<a:r>$runProps<a:t>${esc(line)}</a:t></a:r>") +
                                "<a:endParaRPr lang=\"de-DE\" sz=\"$size\" dirty=\"0\"/></a:p>"
                        }
                        val geom = if (e.radius > 0f && e.fill.isNotBlank()) "roundRect" else "rect"
                        shapes.append(
                            "<p:sp><p:nvSpPr><p:cNvPr id=\"$n\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
                                "<p:spPr>${xfrm(e, k)}<a:prstGeom prst=\"$geom\"><a:avLst/></a:prstGeom>${solid(e.fill, e.opacity)}" +
                                (if (e.strokeWidth > 0f && hex(e.stroke) != null) "<a:ln w=\"${emu(e.strokeWidth * k)}\">${solid(e.stroke, e.opacity)}</a:ln>" else "<a:ln><a:noFill/></a:ln>") +
                                "</p:spPr><p:txBody><a:bodyPr wrap=\"square\" lIns=\"$pad\" tIns=\"$pad\" rIns=\"$pad\" bIns=\"$pad\" anchor=\"$anchor\" rtlCol=\"0\"><a:noAutofit/></a:bodyPr>" +
                                "<a:lstStyle/>$paras</p:txBody></p:sp>",
                        )
                    }
                    ElementKind.RECT, ElementKind.ELLIPSE, ElementKind.TRIANGLE, ElementKind.STAR -> {
                        val geom = when (e.kind) {
                            ElementKind.RECT -> if (e.radius > 0f) "roundRect" else "rect"
                            ElementKind.ELLIPSE -> "ellipse"
                            ElementKind.TRIANGLE -> "triangle"
                            else -> "star5"
                        }
                        val av = if (geom == "roundRect") "<a:gd name=\"adj\" fmla=\"val ${(e.radius / min(e.w, e.h).coerceAtLeast(0.01f) * 100000).toInt().coerceIn(0, 50000)}\"/>" else ""
                        shapes.append(
                            "<p:sp><p:nvSpPr><p:cNvPr id=\"$n\" name=\"$name\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                                "<p:spPr>${xfrm(e, k)}<a:prstGeom prst=\"$geom\"><a:avLst>$av</a:avLst></a:prstGeom>${solid(e.fill, e.opacity)}" +
                                (if (e.strokeWidth > 0f && hex(e.stroke) != null) "<a:ln w=\"${emu(e.strokeWidth * k)}\">${solid(e.stroke, e.opacity)}</a:ln>" else "<a:ln><a:noFill/></a:ln>") +
                                "</p:spPr></p:sp>",
                        )
                    }
                    ElementKind.TUD_SHAPE -> {
                        // The Shape as PowerPoint's own geometry: movable, recolourable, never distorted.
                        val wE = emu(e.w * k)
                        val hE = emu(e.h * k)
                        val paths = TudShape.polygons(e.w, e.h).joinToString("") { poly ->
                            "<a:path w=\"$wE\" h=\"$hE\">" + poly.mapIndexed { idx, (px, py) ->
                                val pt = "<a:pt x=\"${Math.round(px * wE)}\" y=\"${Math.round(py * hE)}\"/>"
                                if (idx == 0) "<a:moveTo>$pt</a:moveTo>" else "<a:lnTo>$pt</a:lnTo>"
                            }.joinToString("") + "<a:close/></a:path>"
                        }
                        val frame = xfrm(e, k).replace("<a:xfrm", "<a:xfrm" + if (e.flipX) " flipH=\"1\"" else "")
                        shapes.append(
                            "<p:sp><p:nvSpPr><p:cNvPr id=\"$n\" name=\"TUD-Shape $n\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                                "<p:spPr>$frame<a:custGeom><a:avLst/><a:gdLst/><a:ahLst/><a:cxnLst/><a:rect l=\"0\" t=\"0\" r=\"r\" b=\"b\"/><a:pathLst>$paths</a:pathLst></a:custGeom>" +
                                solid(e.fill.ifBlank { e.color }, e.opacity) + "<a:ln><a:noFill/></a:ln></p:spPr></p:sp>",
                        )
                    }
                    ElementKind.LINE, ElementKind.ARROW -> {
                        val col = e.stroke.ifBlank { e.color }
                        val head = if (e.kind == ElementKind.ARROW) "<a:tailEnd type=\"triangle\" w=\"med\" len=\"med\"/>" else ""
                        shapes.append(
                            "<p:cxnSp><p:nvCxnSpPr><p:cNvPr id=\"$n\" name=\"$name\"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr>" +
                                "<p:spPr>${xfrm(e, k)}<a:prstGeom prst=\"line\"><a:avLst/></a:prstGeom>" +
                                "<a:ln w=\"${emu(e.strokeWidth.coerceAtLeast(0.1f) * k)}\" cap=\"rnd\">${solid(col, e.opacity)}$head</a:ln></p:spPr></p:cxnSp>",
                        )
                    }
                    ElementKind.IMAGE, ElementKind.ICON, ElementKind.QR -> {
                        val bytes: ByteArray
                        var crop = ""
                        if (e.kind == ElementKind.IMAGE && (io.github.thepytales.llmhub.data.DesignBrand.logo(e.src) != null || e.mask == "shape")) {
                            // Official logos and pictures in the Shape: drawn by the app (sharp, with transparency).
                            bytes = renderElement(d, e.copy(opacity = 1f), maxOf(24f, 3000f / maxOf(e.w, e.h))).pngBytes()
                        } else if (e.kind == ElementKind.IMAGE) {
                            bytes = DesignImages.bytes(d.id, e.src) ?: return@forEach
                            val img = DesignImages.image(d.id, e.src) ?: return@forEach
                            val place = placePicture(e, img.width, img.height)
                            if (e.fitMode == "contain") {
                                // Placed exactly where it is shown (no crop of a logo).
                                val inner = e.copy(x = e.x + place.dst.left, y = e.y + place.dst.top, w = place.dst.width, h = place.dst.height)
                                crop = "@" + xfrm(inner, k)
                            }
                            fun pc(v: Float) = (v * 100000).toInt().coerceIn(0, 100000)
                            val l = pc(place.src.left / img.width)
                            val t = pc(place.src.top / img.height)
                            val r = pc(1f - place.src.right / img.width)
                            val b = pc(1f - place.src.bottom / img.height)
                            if (crop.isEmpty() && (l + t + r + b) > 0) crop = "<a:srcRect l=\"$l\" t=\"$t\" r=\"$r\" b=\"$b\"/>"
                        } else {
                            bytes = renderElement(d, e.copy(opacity = 1f), 24f).pngBytes()
                        }
                        val type = DocMarkup.imageType(bytes)?.let { if (it == "jpeg") "jpg" else it } ?: "png"
                        val file = "design${i + 1}_$n.$type"
                        media[file] = bytes
                        val rid = "rIdImg$n"
                        rels.append("<Relationship Id=\"$rid\" Type=\"$REL/image\" Target=\"../media/$file\"/>")
                        val frame = if (crop.startsWith("@")) crop.removePrefix("@") else xfrm(e, k)
                        val src = if (crop.startsWith("@")) "" else crop
                        val alpha = if (e.opacity < 0.999f) "<a:alphaModFix amt=\"${(e.opacity * 100000).toInt()}\"/>" else ""
                        val round = if (e.kind == ElementKind.IMAGE && e.radius > 0f) "roundRect" else "rect"
                        val av = if (round == "roundRect") "<a:gd name=\"adj\" fmla=\"val ${(e.radius / min(e.w, e.h).coerceAtLeast(0.01f) * 100000).toInt().coerceIn(0, 50000)}\"/>" else ""
                        shapes.append(
                            "<p:pic><p:nvPicPr><p:cNvPr id=\"$n\" name=\"$name\" descr=\"${esc(e.alt.ifBlank { if (e.kind == ElementKind.QR) "QR-Code: " + e.text else ElementKind.label(e.kind) })}\"/>" +
                                "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                                "<p:blipFill><a:blip r:embed=\"$rid\">$alpha</a:blip>$src<a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                                "<p:spPr>$frame<a:prstGeom prst=\"$round\"><a:avLst>$av</a:avLst></a:prstGeom></p:spPr></p:pic>",
                        )
                    }
                }
            }
            val bg = hex(page.background)?.let { "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"$it\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>" }.orEmpty()
            val xml = XML + "<p:sld $NS><p:cSld>$bg<p:spTree>$GROUP$shapes</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
            val relXml = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$rels" +
                "<Relationship Id=\"rIdLayout\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/></Relationships>"
            xml to relXml
        }
        val cx = emu(d.width * k)
        val cy = emu(d.height * k)
        ZipOutputStream(target.outputStream()).use { zip ->
            fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            fun put(name: String, text: String) = put(name, text.toByteArray(Charsets.UTF_8))
            val types = media.keys.map { it.substringAfterLast('.') }.toSet()
            put("[Content_Types].xml", buildString {
                append(XML).append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                types.forEach { t -> append("<Default Extension=\"$t\" ContentType=\"image/${if (t == "jpg") "jpeg" else t}\"/>") }
                append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"$CT.presentation.main+xml\"/>")
                append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"$CT.slideMaster+xml\"/>")
                append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"$CT.slideLayout+xml\"/>")
                append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
                append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
                append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
                slides.indices.forEach { append("<Override PartName=\"/ppt/slides/slide${it + 1}.xml\" ContentType=\"$CT.slide+xml\"/>") }
                append("</Types>")
            })
            put("_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"ppt/presentation.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"$REL/extended-properties\" Target=\"docProps/app.xml\"/></Relationships>")
            val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
            put("docProps/core.xml", XML + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "<dc:title>${esc(d.title)}</dc:title><dc:language>de-DE</dc:language><dc:creator>Lora</dc:creator>" +
                "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified></cp:coreProperties>")
            put("docProps/app.xml", XML + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>Lora</Application><Slides>${slides.size}</Slides></Properties>")
            put("ppt/presentation.xml", buildString {
                append(XML).append("<p:presentation $NS saveSubsetFonts=\"1\">")
                append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rIdMaster\"/></p:sldMasterIdLst><p:sldIdLst>")
                slides.indices.forEach { append("<p:sldId id=\"${256 + it}\" r:id=\"rIdSlide${it + 1}\"/>") }
                append("</p:sldIdLst><p:sldSz cx=\"$cx\" cy=\"$cy\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/>")
                append("<p:defaultTextStyle><a:defPPr><a:defRPr lang=\"de-DE\"/></a:defPPr></p:defaultTextStyle></p:presentation>")
            })
            put("ppt/_rels/presentation.xml.rels", buildString {
                append(XML).append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                append("<Relationship Id=\"rIdMaster\" Type=\"$REL/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
                append("<Relationship Id=\"rIdTheme\" Type=\"$REL/theme\" Target=\"theme/theme1.xml\"/>")
                slides.indices.forEach { append("<Relationship Id=\"rIdSlide${it + 1}\" Type=\"$REL/slide\" Target=\"slides/slide${it + 1}.xml\"/>") }
                append("</Relationships>")
            })
            put("ppt/slideMasters/slideMaster1.xml", XML + "<p:sldMaster $NS><p:cSld><p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg><p:spTree>$GROUP</p:spTree></p:cSld>" +
                "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
                "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rIdLayout\"/></p:sldLayoutIdLst></p:sldMaster>")
            put("ppt/slideMasters/_rels/slideMaster1.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rIdLayout\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
                "<Relationship Id=\"rIdTheme\" Type=\"$REL/theme\" Target=\"../theme/theme1.xml\"/></Relationships>")
            put("ppt/slideLayouts/slideLayout1.xml", XML + "<p:sldLayout $NS type=\"blank\" preserve=\"1\"><p:cSld name=\"Leer\"><p:spTree>$GROUP</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>")
            put("ppt/slideLayouts/_rels/slideLayout1.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rIdMaster\" Type=\"$REL/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>")
            put("ppt/theme/theme1.xml", THEME)
            slides.forEachIndexed { i, (xml, rels) ->
                put("ppt/slides/slide${i + 1}.xml", xml)
                put("ppt/slides/_rels/slide${i + 1}.xml.rels", rels)
            }
            media.forEach { (name, bytes) -> put("ppt/media/$name", bytes) }
        }
    }

    /** Office theme „TUD_2025“ (the colours of the CD package) with Noto Sans. */
    private val THEME = XML + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"TUD\"><a:themeElements>" +
        "<a:clrScheme name=\"TUD_2025\"><a:dk1><a:srgbClr val=\"000000\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"566371\"/></a:dk2><a:lt2><a:srgbClr val=\"E5F1FF\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"00008C\"/></a:accent1><a:accent2><a:srgbClr val=\"2F57B2\"/></a:accent2>" +
        "<a:accent3><a:srgbClr val=\"97C6FF\"/></a:accent3><a:accent4><a:srgbClr val=\"001450\"/></a:accent4>" +
        "<a:accent5><a:srgbClr val=\"65B32E\"/></a:accent5><a:accent6><a:srgbClr val=\"EE7F00\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"2F57B2\"/></a:hlink><a:folHlink><a:srgbClr val=\"566371\"/></a:folHlink></a:clrScheme>" +
        "<a:fontScheme name=\"TUD\"><a:majorFont><a:latin typeface=\"Noto Sans\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
        "<a:minorFont><a:latin typeface=\"Noto Sans\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
        "<a:fmtScheme name=\"Office\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w=\"6350\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" +
        "<a:ln w=\"12700\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln><a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst>" +
        "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>" +
        "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme>" +
        "</a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>"
}
