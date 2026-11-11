package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.PDFStreamEngine
import org.apache.pdfbox.contentstream.operator.DrawObject
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.contentstream.operator.state.Concatenate
import org.apache.pdfbox.contentstream.operator.state.Restore
import org.apache.pdfbox.contentstream.operator.state.Save
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import org.apache.pdfbox.contentstream.operator.state.SetMatrix
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.multipdf.LayerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.io.File

/**
 * Druck-Lotse (AP-97), PC: makes PDFs ready to print – booklet with saddle stitch ([Imposition.booklet]), several
 * pages per sheet, handouts from slides, double-sided with a binding margin, bleed and crop marks for the print shop –
 * and checks them before (Preflight): resolution of the pictures where they are drawn, colour spaces, fonts that are
 * not embedded (AP-91), bleed. Every page is placed as a form (vector, nothing is rasterised).
 */
object PrintPilot {
    const val AVAILABLE = true

    private const val MM = 72.0 / 25.4
    private val A4 = PDRectangle.A4

    private fun load(file: File): PDDocument = Loader.loadPDF(file)

    // ── Check ──

    private fun fontsOf(res: PDResources?, out: MutableMap<String, Boolean>, depth: Int = 0) {
        res ?: return
        if (depth > 4) return
        for (name in res.fontNames) {
            val f = runCatching { res.getFont(name) }.getOrNull() ?: continue
            val clean = f.name.orEmpty().substringAfter('+').ifBlank { name.name }
            out[clean] = (out[clean] ?: false) || f.isEmbedded
        }
        for (x in res.xObjectNames) {
            val o = runCatching { res.getXObject(x) }.getOrNull()
            if (o is PDFormXObject) fontsOf(o.resources, out, depth + 1)
        }
    }

    fun fonts(file: File): List<PdfFontInfo> = load(file).use { doc ->
        val out = LinkedHashMap<String, Boolean>()
        doc.pages.forEach { fontsOf(it.resources, out) }
        out.map { PdfFontInfo(it.key, it.value) }
    }

    /** Collects the pictures of a page with the size they are drawn at. */
    private class ImageFinder(val pageNo: Int) : PDFStreamEngine() {
        val found = mutableListOf<PrintImage>()

        init {
            addOperator(Concatenate(this)); addOperator(DrawObject(this)); addOperator(SetGraphicsStateParameters(this))
            addOperator(Save(this)); addOperator(Restore(this)); addOperator(SetMatrix(this))
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            if (operator.name == "Do") {
                val name = operands.firstOrNull() as? COSName
                val x = name?.let { runCatching { resources.getXObject(it) }.getOrNull() }
                if (x is PDImageXObject) {
                    val ctm = graphicsState.currentTransformationMatrix
                    val wIn = Math.abs(ctm.scalingFactorX) / 72.0
                    val hIn = Math.abs(ctm.scalingFactorY) / 72.0
                    if (wIn > 0.05 && hIn > 0.05) {
                        val dpi = minOf(x.width / wIn, x.height / hIn).toInt()
                        found += PrintImage(pageNo, dpi, runCatching { x.colorSpace.name }.getOrDefault("?"), x.width, x.height)
                    }
                    return
                }
            }
            super.processOperator(operator, operands)
        }
    }

    fun check(file: File, minDpi: Int = 250): PrintCheck = load(file).use { doc ->
        val fonts = LinkedHashMap<String, Boolean>()
        val images = mutableListOf<PrintImage>()
        doc.pages.forEachIndexed { i, page ->
            fontsOf(page.resources, fonts)
            runCatching { ImageFinder(i + 1).also { it.processPage(page) }.found }.getOrNull()?.let { images += it }
        }
        val first = doc.pages.firstOrNull()
        val box = first?.mediaBox ?: A4
        val trim = first?.trimBox
        val bleed = first?.bleedBox
        val hasBleed = trim != null && bleed != null && bleed.width > trim.width + 2 && bleed.height > trim.height + 2
        val issues = mutableListOf<PrintIssue>()
        val loose = fonts.filter { !it.value }.keys
        if (loose.isNotEmpty()) issues += PrintIssue(tr("Schriften nicht eingebettet"), tr("{0} – auf einem anderen Rechner oder in der Druckerei sieht der Text anders aus. Mit „Schriften prüfen“ ersetzen bzw. beim Export einbetten.", loose.joinToString(", ")), true)
        val low = images.filter { it.dpi < minDpi }
        if (low.isNotEmpty()) {
            val worst = low.minBy { it.dpi }
            issues += PrintIssue(
                tr("Bilder mit zu wenig Auflösung"),
                tr("{0} Bild(er) unter {1} dpi, das schwächste {2} dpi auf Seite {3} – im Druck unscharf. Für den Druck 300 dpi.", low.size, minDpi, worst.dpi, worst.page),
                worst.dpi < 150,
            )
        }
        val rgb = images.count { it.colorSpace.contains("RGB", true) || it.colorSpace == "ICCBased" }
        if (rgb > 0) issues += PrintIssue(tr("Farbraum RGB"), tr("{0} Bild(er) in RGB. Die meisten Druckereien wandeln selbst in CMYK um; bei kräftigen Farben vorher fragen.", rgb), false)
        if (!hasBleed) issues += PrintIssue(tr("Ohne Beschnitt"), tr("Für randlosen Druck in der Druckerei „Beschnitt und Schnittmarken“ wählen; für den Bürodrucker nicht nötig."), false)
        if (doc.numberOfPages % 4 != 0) issues += PrintIssue(tr("Seitenzahl"), tr("{0} Seiten – für eine Broschüre werden leere Seiten bis {1} ergänzt.", doc.numberOfPages, Imposition.padded(doc.numberOfPages)), false)
        PrintCheck(doc.numberOfPages, box.width / MM, box.height / MM, issues, fonts.map { PdfFontInfo(it.key, it.value) }, images, hasBleed)
    }

    // ── Placing pages ──

    private class Placer(val src: PDDocument, val out: PDDocument) {
        private val layer = LayerUtility(out)
        private val forms = HashMap<Int, PDFormXObject>()

        fun form(index: Int): PDFormXObject = forms.getOrPut(index) { layer.importPageAsForm(src, index) }

        /** Draws page [index] (0-based) scaled into the box (x, y, w, h), centred, keeping its proportions. */
        fun draw(cs: PDPageContentStream, index: Int, x: Float, y: Float, w: Float, h: Float, frame: Boolean = false) {
            if (index < 0 || index >= src.numberOfPages) return
            val f = form(index)
            val b = f.bBox
            val s = minOf(w / b.width, h / b.height)
            val dx = x + (w - b.width * s) / 2 - b.lowerLeftX * s
            val dy = y + (h - b.height * s) / 2 - b.lowerLeftY * s
            cs.saveGraphicsState()
            cs.transform(Matrix(s, 0f, 0f, s, dx, dy))
            cs.drawForm(f)
            cs.restoreGraphicsState()
            if (frame) {
                cs.setStrokingColor(Color(0.6f, 0.6f, 0.6f))
                cs.setLineWidth(0.5f)
                cs.addRect(x + (w - b.width * s) / 2, y + (h - b.height * s) / 2, b.width * s, b.height * s)
                cs.stroke()
            }
        }
    }

    private fun portrait(doc: PDDocument): Boolean = doc.pages.firstOrNull()?.let { it.mediaBox.height >= it.mediaBox.width } ?: true

    /** Booklet: two pages per sheet side on A4 landscape (an A5 document at its real size), in folding order. */
    fun booklet(file: File, out: File) {
        load(file).use { src ->
            PDDocument().use { dst ->
                val p = Placer(src, dst)
                val sheet = PDRectangle(A4.height, A4.width)
                for ((left, right) in Imposition.booklet(src.numberOfPages)) {
                    val page = PDPage(sheet)
                    dst.addPage(page)
                    PDPageContentStream(dst, page).use { cs ->
                        p.draw(cs, left - 1, 0f, 0f, sheet.width / 2, sheet.height)
                        p.draw(cs, right - 1, sheet.width / 2, 0f, sheet.width / 2, sheet.height)
                    }
                }
                dst.save(out)
            }
        }
    }

    /** [perSheet] pages (2, 4, 6, 9) on each A4 sheet, in reading order, with a thin frame. */
    fun nUp(file: File, out: File, perSheet: Int) {
        val (cols, rows, landscape) = Imposition.grid(perSheet)
        load(file).use { src ->
            val wide = !portrait(src)
            // Landscape pages (slides) two per sheet go one above the other on portrait paper.
            val (c, r, land) = if (wide && perSheet == 2) Triple(1, 2, false) else if (wide && perSheet in setOf(6)) Triple(2, 3, false) else Triple(cols, rows, landscape)
            PDDocument().use { dst ->
                val p = Placer(src, dst)
                val sheet = if (land) PDRectangle(A4.height, A4.width) else A4
                val margin = (10 * MM).toFloat()
                val gap = (4 * MM).toFloat()
                val cellW = (sheet.width - 2 * margin - (c - 1) * gap) / c
                val cellH = (sheet.height - 2 * margin - (r - 1) * gap) / r
                for (start in 0 until src.numberOfPages step c * r) {
                    val page = PDPage(sheet)
                    dst.addPage(page)
                    PDPageContentStream(dst, page).use { cs ->
                        for (k in 0 until c * r) {
                            val col = k % c
                            val row = k / c
                            p.draw(cs, start + k, margin + col * (cellW + gap), sheet.height - margin - (row + 1) * cellH - row * gap, cellW, cellH, frame = true)
                        }
                    }
                }
                dst.save(out)
            }
        }
    }

    /** Handout: three slides per A4 page on the left, lines for notes on the right. */
    fun handout(file: File, out: File) {
        load(file).use { src ->
            PDDocument().use { dst ->
                val p = Placer(src, dst)
                val margin = (15 * MM).toFloat()
                val slideW = ((A4.width - 2 * margin) * 0.55f)
                val cellH = (A4.height - 2 * margin) / 3
                for (start in 0 until src.numberOfPages step 3) {
                    val page = PDPage(A4)
                    dst.addPage(page)
                    PDPageContentStream(dst, page).use { cs ->
                        for (k in 0 until 3) {
                            val top = A4.height - margin - k * cellH
                            if (start + k >= src.numberOfPages) break
                            p.draw(cs, start + k, margin, top - cellH + 8, slideW, cellH - 16, frame = true)
                            cs.setStrokingColor(Color(0.75f, 0.75f, 0.75f))
                            cs.setLineWidth(0.4f)
                            val x0 = margin + slideW + (6 * MM).toFloat()
                            val lineGap = (7 * MM).toFloat()
                            var y = top - 14
                            while (y > top - cellH + 12) {
                                cs.moveTo(x0, y); cs.lineTo(A4.width - margin, y); cs.stroke()
                                y -= lineGap
                            }
                        }
                    }
                }
                dst.save(out)
            }
        }
    }

    /** Double-sided with a binding margin: odd pages move right, even pages left by half of [gutterMm]. */
    fun duplex(file: File, out: File, gutterMm: Double = 10.0) {
        load(file).use { src ->
            PDDocument().use { dst ->
                val p = Placer(src, dst)
                val shift = (gutterMm / 2 * MM).toFloat()
                for (i in 0 until src.numberOfPages) {
                    val box = src.getPage(i).mediaBox
                    val page = PDPage(PDRectangle(box.width, box.height))
                    dst.addPage(page)
                    PDPageContentStream(dst, page).use { cs ->
                        val dx = if (i % 2 == 0) shift else -shift
                        val f = p.form(i)
                        cs.saveGraphicsState()
                        cs.transform(Matrix(1f, 0f, 0f, 1f, dx - f.bBox.lowerLeftX, -f.bBox.lowerLeftY))
                        cs.drawForm(f)
                        cs.restoreGraphicsState()
                    }
                }
                dst.save(out)
            }
        }
    }

    /**
     * Bleed and crop marks for the print shop: each page gets [bleedMm] around it (TrimBox and BleedBox set) and marks
     * in the corners. A page without bleed of its own is enlarged slightly so its edges reach into the bleed; returns
     * true then (the edges should be checked).
     */
    fun bleed(file: File, out: File, bleedMm: Double = 3.0, marks: Boolean = true): Boolean {
        var enlarged = false
        load(file).use { src ->
            PDDocument().use { dst ->
                val p = Placer(src, dst)
                val b = (bleedMm * MM).toFloat()
                val m = if (marks) (8 * MM).toFloat() else 0f
                for (i in 0 until src.numberOfPages) {
                    val sp = src.getPage(i)
                    val trim = sp.trimBox ?: sp.mediaBox
                    val own = sp.bleedBox?.takeIf { it.width >= trim.width + 2 * b - 1 }
                    val w = trim.width
                    val h = trim.height
                    val page = PDPage(PDRectangle(w + 2 * (b + m), h + 2 * (b + m)))
                    page.trimBox = PDRectangle(b + m, b + m, w, h)
                    page.bleedBox = PDRectangle(m, m, w + 2 * b, h + 2 * b)
                    dst.addPage(page)
                    PDPageContentStream(dst, page).use { cs ->
                        val f = p.form(i)
                        val bb = f.bBox
                        if (own != null) {
                            // The page brings its bleed: place it so its trim sits on the new trim.
                            cs.saveGraphicsState()
                            cs.transform(Matrix(1f, 0f, 0f, 1f, b + m - trim.lowerLeftX, b + m - trim.lowerLeftY))
                            cs.drawForm(f)
                            cs.restoreGraphicsState()
                        } else {
                            enlarged = true
                            val s = maxOf((w + 2 * b) / bb.width, (h + 2 * b) / bb.height)
                            cs.saveGraphicsState()
                            cs.transform(Matrix(s, 0f, 0f, s, m + (w + 2 * b - bb.width * s) / 2 - bb.lowerLeftX * s, m + (h + 2 * b - bb.height * s) / 2 - bb.lowerLeftY * s))
                            cs.drawForm(f)
                            cs.restoreGraphicsState()
                        }
                        if (marks) {
                            // White behind the marks, then the marks (registration black) from the corners outwards.
                            cs.setNonStrokingColor(Color.WHITE)
                            val W = page.mediaBox.width
                            val H = page.mediaBox.height
                            cs.addRect(0f, 0f, W, m); cs.addRect(0f, H - m, W, m); cs.addRect(0f, 0f, m, H); cs.addRect(W - m, 0f, m, H); cs.fill()
                            cs.setStrokingColor(Color.BLACK)
                            cs.setLineWidth(0.25f)
                            val x1 = b + m
                            val x2 = b + m + w
                            val y1 = b + m
                            val y2 = b + m + h
                            for ((x, y) in listOf(x1 to y1, x2 to y1, x1 to y2, x2 to y2)) {
                                val outX = if (x == x1) -1 else 1
                                val outY = if (y == y1) -1 else 1
                                cs.moveTo(x, y + outY * (b + 1)); cs.lineTo(x, y + outY * (b + m)); cs.stroke()
                                cs.moveTo(x + outX * (b + 1), y); cs.lineTo(x + outX * (b + m), y); cs.stroke()
                            }
                        }
                    }
                }
                dst.save(out)
            }
        }
        return enlarged
    }
}
