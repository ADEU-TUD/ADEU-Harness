package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File

/**
 * Handout of a presentation as HTML for the PDF printer: 3 slides per page (notes or lines to write on beside them)
 * or 6 per page (notes below, if wanted). The slides are drawn as in the editor and the PowerPoint export: same
 * positions on the 33.867 × 19.05 cm slide, TUD colours, logo, footer and slide number.
 */
object SlideHandout {
    private const val SLIDE_W = 33.867
    private const val SLIDE_H = 19.05
    private val BLUE = TudBrand.BLUE
    private val LIGHT = TudBrand.LIGHT_BLUE
    private val GREY = TudBrand.GREY
    private const val TINT = "#E5F1FF"
    private const val LINE = "#BBC1C6"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun inline(text: String) = inlineSegments(text).joinToString("") { seg ->
        var out = esc(seg.text)
        if (seg.code) out = "<code>$out</code>"
        if (seg.bold) out = "<b>$out</b>"
        if (seg.italic) out = "<i>$out</i>"
        if (seg.strike) out = "<s>$out</s>"
        if (seg.link != null) out = "<span style=\"color:${TudBrand.MID_BLUE}\">$out</span>"
        out
    }

    /**
     * The handout page by page. [markdown] is the presentation (pictures with absolute paths or data URLs),
     * [perPage] 3 or 6, [notes] whether the speaker notes are printed.
     */
    fun html(markdown: String, title: String, perPage: Int, notes: Boolean, unit: String = "", footer: String = "", base: File? = null): String {
        val slides = SlideDeck.parse(markdown).ifEmpty { listOf(SlideDeck.Slide(title, layout = SlideDeck.Layout.TITLE)) }
        val deckTitle = slides.first().title.ifBlank { title }
        val unitLines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val foot = footer.ifBlank { listOfNotNull("TU Dresden", unitLines.lastOrNull(), deckTitle.takeIf { it.isNotBlank() }).joinToString(" · ") }
        val six = perPage >= 6
        val pages = slides.indices.chunked(if (six) 6 else 3)
        val slideW = if (six) 86.0 else 98.0
        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"de\"><head><meta charset=\"utf-8\"><title>${esc(deckTitle)} – Handout</title><style>")
            append(TudBrand.fontFaces)
            append("@page{size:A4;margin:14mm 14mm 16mm 14mm;@bottom-right{content:\"Seite \" counter(page) \" von \" counter(pages);font:8pt sans-serif;color:#777}}")
            append("body{margin:0;font-family:'Noto Sans',Arial,sans-serif;color:#1a1a1a}")
            append(".page{page-break-after:always;break-after:page}.page:last-child{page-break-after:auto;break-after:auto}")
            append(".head{font-size:9pt;color:$GREY;border-bottom:0.5pt solid $LINE;padding-bottom:2mm;margin-bottom:4mm}")
            append(".slide{position:relative;overflow:hidden;border:0.4pt solid $LINE;background:#fff}")
            append(".slide div{position:absolute;overflow:hidden}.slide p{margin:0}.slide ul,.slide ol{margin:0;padding-left:1.2em}")
            append(".slide table{border-collapse:collapse;width:100%}.slide td,.slide th{border:0.3pt solid $LINE;padding:0.3em 0.4em;text-align:left;vertical-align:top}")
            append(".slide th{background:$BLUE;color:#fff}")
            append("table.row{width:100%;border-collapse:collapse;margin-bottom:6mm;page-break-inside:avoid}table.row td{vertical-align:top;padding:0}")
            append(".notes{font-size:9pt;line-height:1.4;padding-left:5mm}.lines{padding-left:5mm}.lines div{border-bottom:0.5pt solid #bbb;height:8.5mm}")
            append(".grid td{width:50%;padding:0 3mm 6mm 0;vertical-align:top}.small{font-size:7.5pt;line-height:1.35;margin-top:1.5mm;color:#333}")
            append("</style></head><body>\n")
            pages.forEach { idx ->
                append("<div class=\"page\"><div class=\"head\">${esc(deckTitle)} · Handout</div>")
                if (six) {
                    append("<table class=\"grid\" style=\"width:100%;border-collapse:collapse\">")
                    idx.chunked(2).forEach { pair ->
                        append("<tr>")
                        pair.forEach { i ->
                            append("<td>").append(slide(slides, i, unitLines, foot, slideW, base))
                            if (notes && slides[i].notes.isNotBlank()) append("<div class=\"small\">${inline(slides[i].notes.take(400))}</div>")
                            append("</td>")
                        }
                        if (pair.size == 1) append("<td></td>")
                        append("</tr>")
                    }
                    append("</table>")
                } else idx.forEach { i ->
                    append("<table class=\"row\"><tr><td style=\"width:${slideW}mm\">").append(slide(slides, i, unitLines, foot, slideW, base)).append("</td><td>")
                    if (notes && slides[i].notes.isNotBlank()) append("<div class=\"notes\">${slides[i].notes.lines().joinToString("<br>") { inline(it) }}</div>")
                    else append("<div class=\"lines\">").append("<div></div>".repeat(6)).append("</div>")
                    append("</td></tr></table>")
                }
                append("</div>\n")
            }
            append("</body></html>\n")
        }
    }

    /** One slide [widthMm] wide. */
    private fun slide(slides: List<SlideDeck.Slide>, i: Int, unit: List<String>, footer: String, widthMm: Double, base: File?): String {
        val s = slides[i]
        val f = widthMm / (SLIDE_W * 10)
        fun mm(cm: Double) = "%.2fmm".format(java.util.Locale.ROOT, cm * 10 * f)
        fun pt(v: Number) = "%.2fpt".format(java.util.Locale.ROOT, v.toDouble() * f)
        val dark = s.layout == SlideDeck.Layout.TITLE || s.layout == SlideDeck.Layout.END
        val sb = StringBuilder()
        fun box(x: Double, y: Double, w: Double, h: Double, style: String = "", content: String) {
            sb.append("<div style=\"left:${mm(x)};top:${mm(y)};width:${mm(w)};height:${mm(h)};$style\">$content</div>")
        }
        fun text(size: Number, color: String, bold: Boolean = false, italic: Boolean = false, align: String = "left") =
            "font-size:${pt(size)};line-height:1.2;color:$color;${if (bold) "font-weight:600;" else ""}${if (italic) "font-style:italic;" else ""}text-align:$align;"
        fun logo(white: Boolean, x: Double, y: Double, h: Double) {
            val bytes = if (white) TudBrand.logoWhite else TudBrand.logoBlue
            box(x, y, h * 1190 / 400, h, content = "<img src=\"${DocMarkup.dataUrl(bytes)}\" alt=\"TU Dresden\" style=\"width:100%;height:100%\">")
        }
        fun chrome() {
            logo(false, SLIDE_W - 1.4 - 1.1 * 1190 / 400, 1.0, 1.1)
            box(1.6, 17.6, SLIDE_W - 3.2, 0.03, "background:$LINE", "")
            box(1.6, 17.8, 26.0, 0.7, text(9, GREY) + "white-space:nowrap", esc(footer))
            box(SLIDE_W - 5.6, 17.8, 4.0, 0.7, text(9, GREY, align = "right"), "${i + 1}")
        }
        fun heading(t: String) = box(1.6, 1.0, 26.0, 2.6, text(if (t.length > 55) 24 else 28, BLUE, bold = true) + "display:flex;align-items:flex-end", "<span>${inline(t)}</span>")
        fun picture(src: String, alt: String, x: Double, y: Double, w: Double, h: Double) {
            val bytes = DocMarkup.imageBytes(src, base) ?: return
            box(x, y, w, h, "text-align:center", "<img src=\"${DocMarkup.dataUrl(bytes)}\" alt=\"${esc(alt)}\" style=\"max-width:100%;max-height:100%\">")
        }
        fun body(md: String, half: Boolean, x: Double, y: Double, w: Double, h: Double) = box(x, y, w, h, content = content(md, half, ::pt))
        when (s.layout) {
            SlideDeck.Layout.TITLE, SlideDeck.Layout.END -> {
                logo(true, 1.6, 1.4, 1.7)
                val lines = s.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (s.layout == SlideDeck.Layout.TITLE) {
                    box(1.6, 5.6, 29.5, 4.8, text(if (s.title.length > 60) 34 else 40, "#fff", bold = true), inline(s.title))
                    lines.firstOrNull()?.let { box(1.6, 10.7, 29.5, 2.2, text(22, LIGHT), inline(it)) }
                    if (lines.size > 1) box(1.6, 13.2, 29.5, 2.4, text(16, "#fff"), lines.drop(1).take(3).joinToString("<br>") { inline(it) })
                    if (unit.isNotEmpty()) box(1.6, 15.9, 29.5, 2.4, text(11, "#fff"), unit.mapIndexed { k, l -> if (k == unit.lastIndex) "<b>${esc(l)}</b>" else esc(l) }.joinToString("<br>"))
                } else {
                    box(1.6, 5.0, 29.5, 4.4, text(40, "#fff", bold = true), inline(s.title.ifBlank { "Vielen Dank!" }))
                    box(1.6, 10.4, 29.5, 5.6, text(18, "#fff"), lines.joinToString("<br>") { inline(it.removePrefix("- ")) })
                }
            }
            SlideDeck.Layout.SECTION -> {
                box(2.5, 6.2, 28.0, 3.4, text(36, BLUE, bold = true), inline(s.title))
                if (s.body.isNotBlank()) box(2.5, 9.8, 28.0, 2.0, text(18, GREY), inline(s.body.lines().joinToString(" ")))
                box(1.6, 6.4, 0.25, 5.2, "background:$BLUE", "")
                chrome()
            }
            SlideDeck.Layout.QUOTE -> {
                if (s.title.isNotBlank()) heading(s.title)
                val q = s.body.lines().map { it.trim().removePrefix(">").trim() }.filter { it.isNotEmpty() }
                val source = q.lastOrNull()?.takeIf { q.size > 1 && (it.startsWith("—") || it.startsWith("–") || it.startsWith("-")) }
                val quote = (if (source != null) q.dropLast(1) else q).joinToString(" ").trim().trim('„', '“', '"')
                box(3.5, 5.0, 26.8, 9.0, text(if (quote.length > 220) 24 else 30, BLUE, italic = true, align = "center"), "„${inline(quote)}“")
                if (source != null) box(3.5, 14.2, 26.8, 1.2, text(16, GREY, align = "center"), inline(source))
                chrome()
            }
            SlideDeck.Layout.IMAGE_TEXT -> {
                heading(s.title)
                val (ix, tx) = if (s.imageLeft) 1.6 to 17.4 else 17.4 to 1.6
                picture(s.image, s.imageAlt, ix, 4.0, 14.8, 13.0)
                body(s.body, true, tx, 4.0, 14.8, 13.0)
                chrome()
            }
            SlideDeck.Layout.IMAGE -> {
                val withTitle = s.title.isNotBlank()
                if (withTitle) heading(s.title)
                picture(s.image, s.imageAlt, 1.6, if (withTitle) 4.0 else 1.0, SLIDE_W - 3.2, if (withTitle) 12.2 else 15.2)
                if (s.imageAlt.isNotBlank() && s.imageAlt != s.title) box(1.6, 16.3, SLIDE_W - 3.2, 1.0, text(12, GREY, align = "center"), inline(s.imageAlt))
                chrome()
            }
            SlideDeck.Layout.COMPARISON -> {
                heading(s.title)
                listOf(1.6 to s.body, 17.4 to s.right).forEach { (x, col) ->
                    val (head, rest) = SlideDeck.columnHead(col)
                    var y = 4.0
                    if (head.isNotBlank()) {
                        box(x, y, 14.8, 1.3, "background:$TINT;" + text(20, BLUE, bold = true) + "padding-left:${mm(0.4)}", inline(head))
                        y += 1.7
                    }
                    body(rest, true, x, y, 14.8, 17.0 - y)
                }
                chrome()
            }
            SlideDeck.Layout.CHART -> {
                heading(s.title)
                val spec = SlideDeck.chartOf(s.body)
                if (spec != null) box(1.6, 4.0, 30.6, 13.0, "text-align:center", ChartLayout.svg(spec).replace("width=\"100%\"", "width=\"100%\" height=\"100%\""))
                chrome()
            }
            SlideDeck.Layout.AGENDA -> {
                heading(s.title.ifBlank { "Agenda" })
                val items = SlideDeck.agenda(slides, i)
                box(1.6, 4.2, 30.6, 12.8, text(if (items.size > 7) 20 else 24, "#1a1a1a"), "<ol>" + items.joinToString("") { "<li style=\"margin-top:${pt(10)}\">${inline(it)}</li>" } + "</ol>")
                chrome()
            }
            SlideDeck.Layout.TWO_COLUMNS -> {
                heading(s.title)
                body(s.body, true, 1.6, 4.0, 14.8, 13.0)
                body(s.right, true, 17.4, 4.0, 14.8, 13.0)
                chrome()
            }
            SlideDeck.Layout.CONTENT, SlideDeck.Layout.TABLE -> {
                heading(s.title)
                body(s.body, false, 1.6, 4.0, 30.6, 13.0)
                chrome()
            }
            SlideDeck.Layout.FREE -> {
                if (s.title.isNotBlank()) heading(s.title)
                if (s.body.isNotBlank()) body(s.body, false, 1.6, 4.0, 30.6, 13.0)
                chrome()
            }
        }
        // Frei gestalten (AP-16): elements over the layout.
        s.elements.forEach { el ->
            val color = el.color.ifBlank { "#00305D" }
            when (el.kind) {
                "bild" -> picture(el.src, el.text, el.x, el.y, el.w, el.h)
                "form" -> {
                    val fill = el.fill.ifBlank { "#00305D" }
                    val radius = when (el.shape) { "ellipse" -> "border-radius:50%;"; "abgerundet" -> "border-radius:12%;"; else -> "" }
                    val styleCss = if (el.shape == "linie") "border-top:2px solid $color;" else "background:$fill;$radius"
                    box(el.x, el.y, el.w, el.h, styleCss + text(el.size, if (el.color.isBlank()) "#fff" else color, bold = el.bold), inline(el.text))
                }
                else -> box(el.x, el.y, el.w, el.h, (if (el.fill.isNotBlank()) "background:${el.fill};" else "") + text(el.size, color, bold = el.bold), el.text.lines().joinToString("<br>") { inline(it) })
            }
        }
        val bg = if (dark) BLUE else "#fff"
        return "<div class=\"slide\" style=\"width:${widthMm}mm;height:${"%.2f".format(java.util.Locale.ROOT, widthMm * SLIDE_H / SLIDE_W)}mm;background:$bg\">$sb</div>"
    }

    /** Slide content with the font size the export chooses for its amount of text. */
    private fun content(md: String, half: Boolean, pt: (Number) -> String): String {
        val blocks = parseBlocks(ChartBlocks.asTables(md))
        val perLine = if (half) 34 else 72
        val lines = blocks.filter { it !is Block.Table }.sumOf { b ->
            when (b) {
                is Block.Paragraph -> b.text.lines().sumOf { 1 + it.length / perLine }
                is Block.Item -> 1 + b.text.length / perLine
                is Block.Heading -> 1 + b.text.length / perLine
                is Block.Quote -> b.text.lines().size
                is Block.Code -> b.code.lines().size
                else -> 0
            }
        }
        val size = when {
            lines <= 6 -> if (half) 20 else 24
            lines <= 8 -> if (half) 18 else 22
            lines <= 10 -> 18
            lines <= 13 -> 16
            else -> 14
        }
        return buildString {
            append("<div style=\"position:static;font-size:${pt(size)};line-height:1.25;color:#1a1a1a\">")
            var list: String? = null
            fun close() { list?.let { append("</$it>") }; list = null }
            for (b in blocks) {
                if (b !is Block.Item) close()
                when (b) {
                    is Block.Paragraph -> if (DocMarkup.image(b.text) == null) append("<p style=\"margin-top:${pt(6)}\">${b.text.lines().joinToString("<br>") { inline(it) }}</p>")
                    is Block.Heading -> append("<p style=\"margin-top:${pt(10)};color:$BLUE;font-weight:600\">${inline(b.text)}</p>")
                    is Block.Item -> {
                        val tag = if (b.marker.first().isDigit()) "ol" else "ul"
                        if (list != tag) { close(); append("<$tag>"); list = tag }
                        append("<li style=\"margin-top:${pt(6)};margin-left:${b.indent * 1.2}em\">${inline(b.text)}</li>")
                    }
                    is Block.Quote -> append("<p style=\"color:$BLUE;font-style:italic\">${inline(b.text)}</p>")
                    is Block.Code -> append("<pre style=\"font-size:${pt((size - 6).coerceAtLeast(10))};margin:0\">${esc(b.code)}</pre>")
                    is Block.Table -> {
                        append("<table style=\"font-size:${pt(if (b.rows.size > 8) 11 else 14)};margin-top:${pt(6)}\">")
                        b.rows.forEachIndexed { r, row ->
                            append("<tr>")
                            row.forEach { c -> append(if (r == 0) "<th>${inline(c)}</th>" else "<td>${inline(c)}</td>") }
                            append("</tr>")
                        }
                        append("</table>")
                    }
                    Block.Rule -> {}
                }
            }
            close()
            append("</div>")
        }
    }
}
