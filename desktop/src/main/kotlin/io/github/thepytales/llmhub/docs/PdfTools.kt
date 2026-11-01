package io.github.thepytales.llmhub.docs

import android.content.Context
import io.github.thepytales.llmhub.ui.tr
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSFloat
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.apache.pdfbox.util.Matrix
import java.io.File

/** Pages of a PDF: count, displayed size (points, rotation applied) and rotation. */
data class PdfInfo(val pages: Int, val sizes: List<Pair<Float, Float>>, val rotations: List<Int>)

/**
 * PDF-Werkzeugkasten (AP-11), Windows version with PDFBox 3. The Android version (same path under app/) uses
 * pdfbox-android with the same code; only loading the file and the standard fonts differ.
 */
@Suppress("UNUSED_PARAMETER")
object PdfTools {
    internal fun init(context: Context?) = Unit

    internal fun load(file: File): PDDocument = Loader.loadPDF(file)

    internal fun font(bold: Boolean = false): PDFont =
        PDType1Font(if (bold) Standard14Fonts.FontName.HELVETICA_BOLD else Standard14Fonts.FontName.HELVETICA)

    // ---- Pages -------------------------------------------------------------------------------------------------

    fun info(context: Context?, file: File): PdfInfo {
        init(context)
        return load(file).use { doc ->
            val pages = doc.pages.toList()
            PdfInfo(pages.size, pages.map { displayed(it) }, pages.map { norm(it.rotation) })
        }
    }

    /** All [files] one after another in [out]. */
    fun merge(context: Context?, files: List<File>, out: File) {
        init(context)
        require(files.isNotEmpty()) { tr("Keine PDFs zum Zusammenfügen.") }
        val docs = files.map { load(it) }
        try {
            val target = PDDocument()
            target.use { t ->
                val merger = PDFMergerUtility()
                docs.forEach { merger.appendDocument(t, it) }
                save(t, out)
            }
        } finally {
            docs.forEach { it.close() }
        }
    }

    /**
     * The pages [order] (0-based indexes, in this order; missing pages are removed) with the extra rotations [rotate]
     * (index → degrees) – sorting, deleting and rotating in one step. Annotations and form fields stay.
     */
    fun rearrange(context: Context?, file: File, order: List<Int>, rotate: Map<Int, Int> = emptyMap(), out: File) {
        init(context)
        require(order.isNotEmpty()) { tr("Mindestens eine Seite muss bleiben.") }
        load(file).use { doc ->
            val pages = doc.pages.toList()
            rotate.forEach { (i, deg) -> pages.getOrNull(i)?.let { it.rotation = norm(it.rotation + deg) } }
            pages.forEach { doc.removePage(it) }
            order.forEach { i -> pages.getOrNull(i)?.let { doc.addPage(it) } }
            save(doc, out)
        }
    }

    /** The pages [pages] (0-based) as a new PDF. */
    fun extract(context: Context?, file: File, pages: List<Int>, out: File) {
        init(context)
        require(pages.isNotEmpty()) { tr("Keine Seiten gewählt.") }
        load(file).use { src ->
            PDDocument().use { t ->
                pages.forEach { i -> src.pages.toList().getOrNull(i)?.let { t.importPage(it) } }
                save(t, out)
            }
        }
    }

    /** One PDF per group of pages, named „<base> (Teil n).pdf“ in [dir]. */
    fun split(context: Context?, file: File, groups: List<List<Int>>, dir: File, base: String): List<File> {
        dir.mkdirs()
        return groups.filter { it.isNotEmpty() }.mapIndexed { i, g ->
            File(dir, "$base (Teil ${i + 1}).pdf").also { extract(context, file, g, it) }
        }
    }

    /** Writes page numbers onto every page. */
    fun addPageNumbers(context: Context?, file: File, numbering: PdfNumbering, out: File) {
        init(context)
        load(file).use { doc ->
            val f = font()
            val total = doc.numberOfPages
            doc.pages.forEachIndexed { i, page ->
                val text = numbering.text(i + 1, total)
                val (w, h) = displayed(page)
                val tw = f.getStringWidth(text) / 1000f * numbering.size
                val margin = 28f
                val (dx, dy) = when (numbering.position) {
                    PdfNumbering.Position.BOTTOM_CENTER -> (w - tw) / 2 to h - margin
                    PdfNumbering.Position.BOTTOM_RIGHT -> w - margin - tw to h - margin
                    PdfNumbering.Position.TOP_RIGHT -> w - margin - tw to margin + numbering.size
                }
                val (x, y) = toUser(page, dx / w, dy / h)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(f, numbering.size)
                    cs.setNonStrokingColor(0.25f, 0.25f, 0.25f)
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(norm(page.rotation).toDouble()), x, y))
                    cs.showText(text)
                    cs.endText()
                }
            }
            save(doc, out)
        }
    }

    // ---- Annotations -------------------------------------------------------------------------------------------

    /** Adds highlights, underlines, strike-throughs, freehand lines, notes and stamps as real PDF annotations. */
    fun annotate(context: Context?, file: File, marks: List<PdfMark>, out: File, author: String = "ADEU LLM") {
        init(context)
        load(file).use { doc ->
            val pages = doc.pages.toList()
            marks.forEach { m -> pages.getOrNull(m.page)?.let { page -> addMark(doc, page, m, author) } }
            save(doc, out)
        }
    }

    private fun addMark(doc: PDDocument, page: PDPage, m: PdfMark, author: String) {
        val rgb = floatArrayOf(((m.color shr 16) and 255) / 255f, ((m.color shr 8) and 255) / 255f, (m.color and 255) / 255f)
        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        val subtype = when (m.kind) {
            PdfMarkKind.HIGHLIGHT -> "Highlight"
            PdfMarkKind.UNDERLINE -> "Underline"
            PdfMarkKind.STRIKE -> "StrikeOut"
            PdfMarkKind.INK -> "Ink"
            PdfMarkKind.NOTE -> "Text"
            PdfMarkKind.STAMP -> "Stamp"
        }
        dict.setName(COSName.SUBTYPE, subtype)
        val annot = PDAnnotationMarkup(dict)
        annot.color = PDColor(rgb, PDDeviceRGB.INSTANCE)
        annot.contents = m.text.ifBlank { m.kind.label }
        annot.isPrinted = true
        dict.setString(COSName.T, author)
        val stream = PDAppearanceStream(doc)
        stream.resources = PDResources()
        when (m.kind) {
            PdfMarkKind.HIGHLIGHT, PdfMarkKind.UNDERLINE, PdfMarkKind.STRIKE -> {
                val rects = m.boxes.map { rect(page, it) }
                if (rects.isEmpty()) return
                val all = union(rects)
                annot.rectangle = all
                val quads = COSArray()
                rects.forEach { r ->
                    listOf(r.lowerLeftX, r.upperRightY, r.upperRightX, r.upperRightY, r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.lowerLeftY)
                        .forEach { quads.add(COSFloat(it)) }
                }
                dict.setItem(COSName.getPDFName("QuadPoints"), quads)
                stream.bBox = all
                PDPageContentStream(doc, stream).use { cs ->
                    if (m.kind == PdfMarkKind.HIGHLIGHT) {
                        val gs = PDExtendedGraphicsState()
                        gs.nonStrokingAlphaConstant = 0.4f
                        cs.setGraphicsStateParameters(gs)
                        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                        rects.forEach { r -> cs.addRect(r.lowerLeftX, r.lowerLeftY, r.width, r.height) }
                        cs.fill()
                    } else {
                        cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
                        rects.forEach { r ->
                            val y = if (m.kind == PdfMarkKind.UNDERLINE) r.lowerLeftY + r.height * 0.08f else r.lowerLeftY + r.height * 0.45f
                            cs.setLineWidth((r.height * 0.07f).coerceIn(0.8f, 2.5f))
                            cs.moveTo(r.lowerLeftX, y)
                            cs.lineTo(r.upperRightX, y)
                        }
                        cs.stroke()
                    }
                }
            }
            PdfMarkKind.INK -> {
                val strokes = m.strokes.filter { it.size >= 2 }.map { s -> s.map { p -> toUser(page, p.x, p.y) } }
                if (strokes.isEmpty()) return
                val xs = strokes.flatten().map { it.first }
                val ys = strokes.flatten().map { it.second }
                val all = PDRectangle(xs.min() - 3, ys.min() - 3, xs.max() - xs.min() + 6, ys.max() - ys.min() + 6)
                annot.rectangle = all
                val ink = COSArray()
                strokes.forEach { s -> ink.add(COSArray().apply { s.forEach { (x, y) -> add(COSFloat(x)); add(COSFloat(y)) } }) }
                dict.setItem(COSName.getPDFName("InkList"), ink)
                stream.bBox = all
                PDPageContentStream(doc, stream).use { cs ->
                    cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
                    cs.setLineWidth(1.6f)
                    strokes.forEach { s ->
                        cs.moveTo(s[0].first, s[0].second)
                        s.drop(1).forEach { (x, y) -> cs.lineTo(x, y) }
                    }
                    cs.stroke()
                }
            }
            PdfMarkKind.NOTE -> {
                val b = m.boxes.firstOrNull() ?: return
                val (x, y) = toUser(page, b.x, b.y)
                val r = PDRectangle(x, y - 18, 18f, 18f)
                annot.rectangle = r
                dict.setName(COSName.NAME, "Comment")
                stream.bBox = r
                PDPageContentStream(doc, stream).use { cs ->
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                    cs.addRect(r.lowerLeftX, r.lowerLeftY, r.width, r.height)
                    cs.fill()
                    cs.setStrokingColor(0.3f, 0.3f, 0.3f)
                    cs.setLineWidth(0.8f)
                    for (k in 1..3) {
                        cs.moveTo(r.lowerLeftX + 4, r.lowerLeftY + r.height - 4.5f * k)
                        cs.lineTo(r.upperRightX - 4, r.lowerLeftY + r.height - 4.5f * k)
                    }
                    cs.stroke()
                }
            }
            PdfMarkKind.STAMP -> {
                val b = m.boxes.firstOrNull() ?: return
                val text = m.text.ifBlank { "GEPRÜFT" }.uppercase()
                val f = font(bold = true)
                val size = 16f
                val tw = f.getStringWidth(safe(text)) / 1000f * size
                val (x, y) = toUser(page, b.x, b.y)
                val r = PDRectangle(x, y - size - 12, tw + 20, size + 12)
                annot.rectangle = r
                dict.setName(COSName.NAME, "Approved")
                stream.bBox = r
                PDPageContentStream(doc, stream).use { cs ->
                    cs.setStrokingColor(rgb[0], rgb[1], rgb[2])
                    cs.setLineWidth(2f)
                    cs.addRect(r.lowerLeftX + 1, r.lowerLeftY + 1, r.width - 2, r.height - 2)
                    cs.stroke()
                    cs.beginText()
                    cs.setFont(f, size)
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                    cs.newLineAtOffset(r.lowerLeftX + 10, r.lowerLeftY + 7)
                    cs.showText(safe(text))
                    cs.endText()
                }
            }
        }
        annot.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        page.annotations = page.annotations + annot
    }

    /** Text the standard font can show (WinAnsi); other characters become „?“. */
    private fun safe(s: String): String = s.map { ch -> if (ch.code in 32..255 || ch in "€„“‚‘–—…") ch else '?' }.joinToString("")

    /** Comments, highlights and notes of the PDF (for the list and the export). */
    fun comments(context: Context?, file: File): List<PdfComment> {
        init(context)
        val kinds = mapOf(
            "Text" to "Notiz", "Highlight" to "Hervorhebung", "Underline" to "Unterstreichung", "StrikeOut" to "Durchstreichung",
            "Squiggly" to "Wellenlinie", "Ink" to "Freihand", "Stamp" to "Stempel", "FreeText" to "Textkommentar",
        )
        return load(file).use { doc ->
            doc.pages.flatMapIndexed { i, page ->
                page.annotations.mapNotNull { a ->
                    val kind = kinds[a.cosObject.getNameAsString(COSName.SUBTYPE)] ?: return@mapNotNull null
                    PdfComment(i + 1, kind, a.contents.orEmpty(), a.cosObject.getString(COSName.T).orEmpty())
                }
            }
        }
    }

    /** The comments as Markdown for the export. */
    fun commentsMarkdown(name: String, comments: List<PdfComment>): String = buildString {
        append("# Kommentare: ").append(name).append("\n\n")
        if (comments.isEmpty()) append("Keine Kommentare.\n")
        comments.groupBy { it.page }.toSortedMap().forEach { (page, list) ->
            append("## Seite ").append(page).append("\n\n")
            list.forEach { c -> append("- **").append(c.kind).append("**").append(if (c.text.isNotBlank()) ": " + c.text else "").append(if (c.author.isNotBlank()) " _(${c.author})_" else "").append('\n') }
            append('\n')
        }
    }

    // ---- Reading -----------------------------------------------------------------------------------------------

    /** Outline (bookmarks) with the page each entry leads to. */
    fun outline(context: Context?, file: File): List<PdfOutlineEntry> {
        init(context)
        return load(file).use { doc ->
            val out = mutableListOf<PdfOutlineEntry>()
            fun walk(node: PDOutlineNode, level: Int) {
                for (item in node.children()) {
                    val page = runCatching { item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } }.getOrNull() ?: -1
                    out += PdfOutlineEntry(item.title.orEmpty(), page + 1, level)
                    if (out.size < 500) walk(item, level + 1)
                }
            }
            doc.documentCatalog.documentOutline?.let { walk(it, 0) }
            out
        }
    }

    /** Pages that contain [query] (case does not matter) with the text around it. */
    fun search(context: Context?, file: File, query: String, limit: Int = 200): List<PdfSearchHit> {
        init(context)
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return load(file).use { doc ->
            val out = mutableListOf<PdfSearchHit>()
            val stripper = PDFTextStripper()
            for (p in 1..doc.numberOfPages) {
                stripper.startPage = p
                stripper.endPage = p
                val text = stripper.getText(doc).replace(Regex("\\s+"), " ")
                var i = text.indexOf(q, ignoreCase = true)
                while (i >= 0 && out.size < limit) {
                    out += PdfSearchHit(p, "…" + text.substring(maxOf(0, i - 40), minOf(text.length, i + q.length + 40)) + "…")
                    i = text.indexOf(q, i + q.length, ignoreCase = true)
                }
                if (out.size >= limit) break
            }
            out
        }
    }

    /** Text of the PDF as lines with their positions (displayed page, points from the top left). */
    fun textRuns(context: Context?, file: File): Pair<List<PdfTextRun>, List<Pair<Float, Float>>> {
        init(context)
        return load(file).use { doc ->
            val sizes = doc.pages.map { displayed(it) }
            val runs = mutableListOf<PdfTextRun>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, positions: MutableList<TextPosition>) {
                    if (positions.isEmpty() || text.isBlank()) return
                    // Split a line at wide gaps, so „Name: ____   Datum: ____“ gives two runs.
                    var start = 0
                    for (k in 1..positions.size) {
                        val gap = k < positions.size && positions[k].xDirAdj - (positions[k - 1].xDirAdj + positions[k - 1].widthDirAdj) > positions[k - 1].fontSizeInPt * 1.8f
                        if (k == positions.size || gap) {
                            val part = positions.subList(start, k)
                            val first = part.first()
                            val last = part.last()
                            val h = part.maxOf { it.heightDir }.coerceAtLeast(first.fontSizeInPt * 0.7f)
                            runs += PdfTextRun(
                                currentPageNo - 1, first.xDirAdj, first.yDirAdj - h, last.xDirAdj + last.widthDirAdj - first.xDirAdj, h,
                                part.joinToString("") { it.unicode },
                            )
                            start = k
                        }
                    }
                }
            }
            stripper.sortByPosition = true
            stripper.getText(doc)
            runs to sizes
        }
    }

    // ---- Forms -------------------------------------------------------------------------------------------------

    /** Where entries belong on pages without form fields (see [PdfFormDetect]). */
    fun detectFields(context: Context?, file: File): List<PdfFieldSpec> {
        val (runs, sizes) = textRuns(context, file)
        return runs.groupBy { it.page }.flatMap { (page, list) ->
            val (w, h) = sizes.getOrNull(page) ?: return@flatMap emptyList()
            PdfFormDetect.detect(list, w, h)
        }
    }

    /** The text fields and check boxes of the PDF with their place (for the field editor). */
    fun formFields(context: Context?, file: File): List<PdfFieldSpec> {
        init(context)
        return load(file).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return@use emptyList()
            val pages = doc.pages.toList()
            form.fieldTree.filterIsInstance<PDTerminalField>().filter { it is PDTextField || it is PDCheckBox }.mapNotNull { f ->
                val w = f.widgets.firstOrNull() ?: return@mapNotNull null
                val page = w.page?.let { p -> pages.indexOfFirst { it.cosObject == p.cosObject } }?.takeIf { it >= 0 }
                    ?: pages.indexOfFirst { p -> p.annotations.any { it.cosObject == w.cosObject } }.takeIf { it >= 0 } ?: return@mapNotNull null
                val type = when {
                    f is PDCheckBox -> PdfFieldType.CHECKBOX
                    (f as PDTextField).isMultiline -> PdfFieldType.MULTILINE
                    else -> PdfFieldType.TEXT
                }
                PdfFieldSpec(f.partialName ?: f.fullyQualifiedName, page, fromUser(pages[page], w.rectangle), type, f.alternateFieldName.orEmpty())
            }
        }
    }

    /**
     * Replaces the text fields and check boxes of the PDF by [fields] (other kinds of fields stay), so the field editor
     * can move, resize and retype them. Fields get a visible frame and the tooltip of their label.
     */
    fun setFormFields(context: Context?, file: File, fields: List<PdfFieldSpec>, out: File) {
        init(context)
        load(file).use { doc ->
            val catalog = doc.documentCatalog
            val form = catalog.acroForm ?: PDAcroForm(doc).also { catalog.acroForm = it }
            val pages = doc.pages.toList()
            // Remove the old text fields and check boxes with their widgets.
            val old = form.fieldTree.filterIsInstance<PDTerminalField>().filter { it is PDTextField || it is PDCheckBox }
            val oldWidgets = old.flatMap { it.widgets }.map { it.cosObject }.toSet()
            pages.forEach { p -> if (p.annotations.any { it.cosObject in oldWidgets }) p.annotations = p.annotations.filter { it.cosObject !in oldWidgets } }
            form.fields = form.fields.filter { top -> old.none { it.cosObject == top.cosObject } }

            val f = font()
            val resources = form.defaultResources ?: PDResources().also { form.defaultResources = it }
            val fontName = resources.add(f).name
            form.defaultAppearance = "/$fontName 0 Tf 0 g"
            form.needAppearances = true
            val used = HashSet<String>()
            fields.forEach { spec ->
                val page = pages.getOrNull(spec.page) ?: return@forEach
                var name = spec.name.replace('.', ' ').trim().ifBlank { "Feld" }
                var n = 2
                val base = name
                while (!used.add(name)) name = "$base ${n++}"
                val r = rect(page, spec.box)
                val field: PDTerminalField = if (spec.type == PdfFieldType.CHECKBOX) PDCheckBox(form) else PDTextField(form).apply {
                    isMultiline = spec.type == PdfFieldType.MULTILINE
                    defaultAppearance = "/$fontName ${if (spec.type == PdfFieldType.MULTILINE) 10 else 0} Tf 0 g"
                }
                field.partialName = name
                if (spec.label.isNotBlank()) field.alternateFieldName = spec.label
                val widget: PDAnnotationWidget = field.widgets.first()
                widget.rectangle = r
                widget.page = page
                widget.isPrinted = true
                widget.appearanceCharacteristics = PDAppearanceCharacteristicsDictionary(COSDictionary()).apply {
                    borderColour = PDColor(floatArrayOf(0.35f, 0.55f, 0.85f), PDDeviceRGB.INSTANCE)
                }
                if (field is PDCheckBox) checkBoxAppearance(doc, widget, r) else frameAppearance(doc, widget, r)
                page.annotations = page.annotations + widget
                form.fields = form.fields + field
            }
            save(doc, out)
        }
    }

    private fun frameAppearance(doc: PDDocument, widget: PDAnnotationWidget, r: PDRectangle) {
        val stream = PDAppearanceStream(doc)
        stream.resources = PDResources()
        stream.bBox = PDRectangle(r.width, r.height)
        PDPageContentStream(doc, stream).use { cs ->
            cs.setStrokingColor(0.35f, 0.55f, 0.85f)
            cs.setLineWidth(0.8f)
            cs.addRect(0.4f, 0.4f, r.width - 0.8f, r.height - 0.8f)
            cs.stroke()
        }
        widget.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
    }

    private fun checkBoxAppearance(doc: PDDocument, widget: PDAnnotationWidget, r: PDRectangle) {
        fun box(checked: Boolean) = PDAppearanceStream(doc).also { s ->
            s.resources = PDResources()
            s.bBox = PDRectangle(r.width, r.height)
            PDPageContentStream(doc, s).use { cs ->
                cs.setStrokingColor(0.35f, 0.55f, 0.85f)
                cs.setLineWidth(0.8f)
                cs.addRect(0.4f, 0.4f, r.width - 0.8f, r.height - 0.8f)
                cs.stroke()
                if (checked) {
                    cs.setStrokingColor(0f, 0f, 0f)
                    cs.setLineWidth((r.width * 0.12f).coerceIn(1f, 2.5f))
                    cs.moveTo(r.width * 0.2f, r.height * 0.5f)
                    cs.lineTo(r.width * 0.42f, r.height * 0.25f)
                    cs.lineTo(r.width * 0.8f, r.height * 0.78f)
                    cs.stroke()
                }
            }
        }
        val states = COSDictionary()
        states.setItem(COSName.getPDFName("Yes"), box(true))
        states.setItem(COSName.Off, box(false))
        val ap = PDAppearanceDictionary()
        ap.cosObject.setItem(COSName.N, states)
        widget.appearance = ap
        widget.cosObject.setName(COSName.AS, "Off")
        widget.cosObject.setName(COSName.V, "Off")
    }

    // ---- Geometry ----------------------------------------------------------------------------------------------

    internal fun norm(deg: Int) = ((deg % 360) + 360) % 360

    /** Width and height as the page is shown (rotation applied). */
    internal fun displayed(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox
        return if (norm(page.rotation) % 180 == 90) box.height to box.width else box.width to box.height
    }

    /** A point given as fractions of the displayed page → PDF user space. */
    internal fun toUser(page: PDPage, fx: Float, fy: Float): Pair<Float, Float> {
        val b = page.cropBox
        val (w, h) = b.width to b.height
        return when (norm(page.rotation)) {
            90 -> b.lowerLeftX + fy * w to b.lowerLeftY + fx * h
            180 -> b.lowerLeftX + (1 - fx) * w to b.lowerLeftY + fy * h
            270 -> b.lowerLeftX + (1 - fy) * w to b.lowerLeftY + (1 - fx) * h
            else -> b.lowerLeftX + fx * w to b.lowerLeftY + (1 - fy) * h
        }
    }

    /** PDF user space → fractions of the displayed page. */
    private fun fromUserPoint(page: PDPage, x: Float, y: Float): Pair<Float, Float> {
        val b = page.cropBox
        val u = (x - b.lowerLeftX) / b.width
        val v = (y - b.lowerLeftY) / b.height
        return when (norm(page.rotation)) {
            90 -> v to u
            180 -> 1 - u to v
            270 -> 1 - v to 1 - u
            else -> u to 1 - v
        }
    }

    private fun rect(page: PDPage, box: PdfBox): PDRectangle {
        val (x1, y1) = toUser(page, box.x, box.y)
        val (x2, y2) = toUser(page, box.right, box.bottom)
        return PDRectangle(minOf(x1, x2), minOf(y1, y2), kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
    }

    private fun fromUser(page: PDPage, r: PDRectangle): PdfBox {
        val (u1, v1) = fromUserPoint(page, r.lowerLeftX, r.lowerLeftY)
        val (u2, v2) = fromUserPoint(page, r.upperRightX, r.upperRightY)
        return PdfBox(minOf(u1, u2), minOf(v1, v2), kotlin.math.abs(u2 - u1), kotlin.math.abs(v2 - v1))
    }

    private fun union(rects: List<PDRectangle>): PDRectangle {
        val x1 = rects.minOf { it.lowerLeftX }
        val y1 = rects.minOf { it.lowerLeftY }
        return PDRectangle(x1, y1, rects.maxOf { it.upperRightX } - x1, rects.maxOf { it.upperRightY } - y1)
    }

    /** Saves to a temporary file first, so [out] may be the file that was read. */
    private fun save(doc: PDDocument, out: File) {
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".tmp")
        doc.save(tmp)
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
    }
}
