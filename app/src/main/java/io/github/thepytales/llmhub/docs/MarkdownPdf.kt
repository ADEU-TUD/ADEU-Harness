package io.github.thepytales.llmhub.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File

/**
 * Renders Markdown into an A4 PDF with Android's [PdfDocument] (fallback when WebView printing fails): footnotes at the
 * end, pictures, page breaks, landscape pages and "Seite X von Y".
 */
object MarkdownPdf {
    private const val PAGE_W = 595 // A4 in PostScript points
    private const val PAGE_H = 842
    private const val MARGIN = 56f
    private const val TEXT = 0xFF1A1A1A.toInt()
    private const val MUTED = 0xFF666666.toInt()
    private const val CODE_BG = 0xFFF1F3F5.toInt()
    private const val LINK = 0xFF1D4ED8.toInt()
    private const val LINE = 0xFFCCCCCC.toInt()

    /** Writes the PDF and returns the number of pages. */
    fun write(file: File, title: String, markdown: String, theme: DocTheme = DocTheme()): Int {
        val setup = DocMarkup.pageSetup(markdown)
        val notes = DocMarkup.footnotes(DocMarkup.isolate(DocumentWriter.withTitle(title, markdown)))
        val blocks = parseBlocks(notes.body)
        // First pass only counts the pages for "Seite X von Y".
        val total = if (setup.pageNumbers) render(blocks, notes, theme, setup, 0, null) else 0
        return render(blocks, notes, theme, setup, total, file)
    }

    private fun render(blocks: List<Block>, notes: DocMarkup.Footnotes, theme: DocTheme, setup: DocMarkup.PageSetup, total: Int, file: File?): Int {
        val doc = PdfDocument()
        try {
            val w = Writer(doc, theme, setup, notes.numbers, total)
            for (b in blocks) w.block(b)
            w.notes(notes.notes)
            w.finish()
            if (file != null) file.outputStream().use { doc.writeTo(it) }
            return w.pageNo
        } finally {
            doc.close()
        }
    }

    private fun paint(size: Float, bold: Boolean = false, mono: Boolean = false, color: Int = TEXT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when {
                mono -> Typeface.MONOSPACE
                bold -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
        }

    private fun spans(text: String, numbers: Map<String, Int> = emptyMap()): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (seg in inlineSegments(text)) {
            for ((piece, note) in if (seg.code) listOf(seg.text to null) else DocMarkup.splitNotes(seg.text, numbers)) {
                val start = sb.length
                sb.append(note?.toString() ?: piece)
                val end = sb.length
                if (start == end) continue
                fun span(what: Any) = sb.setSpan(what, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (note != null) {
                    span(SuperscriptSpan())
                    span(RelativeSizeSpan(0.7f))
                    continue
                }
                when {
                    seg.bold && seg.italic -> span(StyleSpan(Typeface.BOLD_ITALIC))
                    seg.bold -> span(StyleSpan(Typeface.BOLD))
                    seg.italic -> span(StyleSpan(Typeface.ITALIC))
                }
                if (seg.code) {
                    span(TypefaceSpan("monospace"))
                    span(BackgroundColorSpan(CODE_BG))
                }
                if (seg.strike) span(StrikethroughSpan())
                if (seg.link != null) {
                    span(ForegroundColorSpan(LINK))
                    span(UnderlineSpan())
                }
            }
        }
        return sb
    }

    private class Writer(
        private val doc: PdfDocument,
        private val theme: DocTheme,
        private val setup: DocMarkup.PageSetup,
        private val numbers: Map<String, Int>,
        private val total: Int,
    ) {
        private val accent = if (theme.style == DocStyle.CLASSIC || theme.style == DocStyle.MINIMAL) TEXT else theme.accentArgb
        private val logo: Bitmap? = theme.logo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val left = MARGIN

        /** Landscape pages between <!-- quer --> and <!-- hoch -->. */
        private var landscape = false
        private val pageW get() = if (landscape) PAGE_H else PAGE_W
        private val pageH get() = if (landscape) PAGE_W else PAGE_H
        private val width get() = pageW - 2 * MARGIN
        private val bottom get() = pageH - MARGIN
        private val canvas: Canvas get() = page!!.canvas

        private val body = paint(11f)
        private val small = paint(8f, color = MUTED)
        private val fill = Paint().apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = LINE }

        private fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = MARGIN
            if (pageNo == 1 && logo != null) {
                val h = 40f
                val w = (h * logo.width / logo.height).coerceAtMost(160f)
                val hh = w * logo.height / logo.width
                canvas.drawBitmap(logo, null, RectF(left + width - w, MARGIN - 16f, left + width, MARGIN - 16f + hh), Paint(Paint.FILTER_BITMAP_FLAG))
                y = MARGIN - 16f + hh + 12f
            }
            if (setup.header.isNotBlank()) canvas.drawText(setup.header.take(90), MARGIN, MARGIN / 2, small)
            if (setup.date) DocMarkup.today().let { canvas.drawText(it, pageW - MARGIN - small.measureText(it), MARGIN / 2, small) }
        }

        private fun finishPage() {
            val p = page ?: return
            if (setup.pageNumbers) {
                val label = if (total > 0) "Seite $pageNo von $total" else "Seite $pageNo"
                p.canvas.drawText(label, pageW - MARGIN - small.measureText(label), pageH - MARGIN / 2, small)
            }
            if (theme.footer.isNotBlank()) p.canvas.drawText(theme.footer.take(110), MARGIN, pageH - MARGIN / 2, small)
            doc.finishPage(p)
            page = null
        }

        fun finish() {
            if (page == null) newPage()
            finishPage()
        }

        /** Makes sure [h] points are left on the page (if they can fit on a page at all). */
        private fun ensure(h: Float) {
            if (page == null || (y + h > bottom && y > MARGIN)) newPage()
        }

        private fun layout(text: CharSequence, paint: TextPaint, w: Float): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, w.toInt().coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()

        /** Draws text, breaking it across pages line by line. */
        private fun text(text: CharSequence, paint: TextPaint, indent: Float = 0f, pad: Float = 0f, bg: Int? = null, bar: Boolean = false) {
            val inner = width - indent - 2 * pad
            val l = layout(text, paint, inner)
            var line = 0
            while (line < l.lineCount) {
                ensure(l.getLineBottom(line) - l.getLineTop(line) + 2 * pad)
                val top = l.getLineTop(line)
                val avail = bottom - y - 2 * pad
                var end = line
                while (end < l.lineCount && l.getLineBottom(end) - top <= avail) end++
                if (end == line) end = line + 1 // a single line taller than a page
                val h = (l.getLineBottom(end - 1) - top).toFloat()
                val x = left + indent
                if (bg != null) {
                    fill.color = bg
                    canvas.drawRect(x, y, left + width, y + h + 2 * pad, fill)
                }
                if (bar) {
                    fill.color = LINE
                    canvas.drawRect(x - 8f, y, x - 5f, y + h + 2 * pad, fill)
                }
                canvas.save()
                canvas.translate(x + pad, y + pad - top)
                canvas.clipRect(0f, top.toFloat(), inner, top + h)
                l.draw(canvas)
                canvas.restore()
                y += h + 2 * pad
                line = end
            }
        }

        private fun image(img: DocMarkup.Image) {
            val bytes = DocMarkup.imageBytes(img.src)
            val bmp = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            if (bmp == null) {
                text("[Bild: ${img.alt.ifBlank { img.src.substringAfterLast('/') }}]", paint(10f, color = MUTED))
                y += 7f
                return
            }
            var w = if (img.width != null) width * img.width / 100f else (bmp.width * 0.75f).coerceAtMost(width)
            var h = w * bmp.height / bmp.width
            val maxH = (pageH - 2 * MARGIN) * 0.8f
            if (h > maxH) { h = maxH; w = h * bmp.width / bmp.height }
            ensure(h + 24f)
            val x = left + (width - w) / 2
            canvas.drawBitmap(bmp, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 4f
            if (img.alt.isNotBlank()) {
                val cap = paint(9f, color = MUTED)
                val l = layout(spans(img.alt), cap, width)
                ensure(l.height.toFloat())
                canvas.save()
                canvas.translate(left + (width - l.getLineWidth(0).coerceAtMost(width)) / 2, y)
                l.draw(canvas)
                canvas.restore()
                y += l.height
            }
            y += 9f
        }

        /** The footnotes, numbered, after the text. */
        fun notes(list: List<DocMarkup.Note>) {
            if (list.isEmpty()) return
            ensure(40f)
            y += 10f
            canvas.drawLine(left, y, left + width * 0.3f, y, stroke)
            y += 6f
            val p = paint(8.5f)
            for (n in list) {
                text(SpannableStringBuilder("${n.number}  ").append(spans(n.text)), p)
                y += 2f
            }
        }

        fun block(b: Block) {
            when (b) {
                is Block.Heading -> {
                    val size = when (b.level) { 1 -> 20f; 2 -> 16f; 3 -> 13.5f; else -> 12f }
                    y += if (b.level <= 2) 10f else 6f
                    // Keep headings together with the start of the following block.
                    ensure(size * 1.4f + 40f)
                    text(spans(b.text, numbers), paint(size, bold = true, color = if (b.level <= 2) accent else TEXT))
                    if (b.level == 1) {
                        y += 3f
                        canvas.drawLine(left, y, left + width, y, stroke)
                        y += 4f
                    }
                    y += 4f
                }
                is Block.Paragraph -> {
                    val turn = DocMarkup.orientation(b.text)
                    val img = DocMarkup.image(b.text)
                    when {
                        turn != null -> if (turn != landscape) {
                            finishPage()
                            landscape = turn
                        }
                        DocMarkup.isPageBreak(b.text) -> finishPage()
                        DocMarkup.isComment(b.text) -> {}
                        img != null -> image(img)
                        else -> {
                            text(spans(b.text, numbers), body)
                            y += 7f
                        }
                    }
                }
                is Block.Item -> {
                    val indent = 14f * b.indent
                    val marker = "${b.marker}  "
                    val sb = SpannableStringBuilder(marker).append(spans(b.text, numbers))
                    sb.setSpan(LeadingMarginSpan.Standard(0, body.measureText(marker).toInt()), 0, sb.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                    text(sb, body, indent = indent + 6f)
                    y += 3f
                }
                is Block.Quote -> {
                    text(spans(b.text, numbers), paint(11f, color = MUTED), indent = 12f, bar = true)
                    y += 7f
                }
                is Block.Code -> {
                    text(b.code.ifEmpty { " " }, paint(9f, mono = true), pad = 7f, bg = CODE_BG)
                    y += 8f
                }
                is Block.Table -> table(b.rows)
                Block.Rule -> {
                    ensure(12f)
                    y += 5f
                    canvas.drawLine(left, y, left + width, y, stroke)
                    y += 7f
                }
            }
        }

        private fun table(rows: List<List<String>>) {
            if (rows.isEmpty()) return
            val cols = rows.maxOf { it.size }
            val pad = 4f
            // Column widths proportional to the longest cell, but at least a fair share.
            val lengths = (0 until cols).map { c -> rows.maxOf { it.getOrNull(c)?.length ?: 0 }.coerceIn(4, 60).toFloat() }
            val total = lengths.sum()
            val widths = lengths.map { (it / total * 0.7f + 0.3f / cols) * width }
            val bold = paint(9.5f, bold = true)
            val normal = paint(9.5f)
            rows.forEachIndexed { r, row ->
                val layouts = (0 until cols).map { c ->
                    layout(spans(row.getOrNull(c).orEmpty(), numbers), if (r == 0) bold else normal, widths[c] - 2 * pad)
                }
                val h = layouts.maxOf { it.height } + 2 * pad
                ensure(h)
                var x = left
                if (r == 0) {
                    fill.color = CODE_BG
                    canvas.drawRect(left, y, left + widths.sum(), y + h, fill)
                }
                layouts.forEachIndexed { c, l ->
                    canvas.drawRect(x, y, x + widths[c], y + h, stroke)
                    canvas.save()
                    canvas.translate(x + pad, y + pad)
                    l.draw(canvas)
                    canvas.restore()
                    x += widths[c]
                }
                y += h
            }
            y += 9f
        }
    }
}
