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
    /** Blau 2 at 25 %. */
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
        // „Titel der Präsentation • Vortragende“ as in the CD (the first line under the title, if there is one).
        val presenter = slides.first().takeIf { it.layout == SlideDeck.Layout.TITLE }?.body?.lines()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        val foot = footer.ifBlank { listOfNotNull(deckTitle.takeIf { it.isNotBlank() }, presenter).joinToString(" • ").ifBlank { "TU Dresden" } }
        val six = perPage >= 6
        val pages = slides.indices.chunked(if (six) 6 else 3)
        val slideW = if (six) 86.0 else 98.0
        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"de\"><head><meta charset=\"utf-8\"><title>${esc(deckTitle)} – Handout</title><style>")
            append(TudBrand.fontFaces)
            append("@page{size:A4;margin:14mm 14mm 16mm 14mm;@bottom-right{content:\"Seite \" counter(page) \" von \" counter(pages);font:8pt sans-serif;color:#777}}")
            append("body{margin:0;font-family:'Noto Sans',Arial,sans-serif;color:#1a1a1a}")
            append(".slide .m{font-weight:500}")
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
        val dark = s.layout == SlideDeck.Layout.TITLE || s.layout == SlideDeck.Layout.END || s.layout == SlideDeck.Layout.SECTION
        val sb = StringBuilder()
        fun box(x: Double, y: Double, w: Double, h: Double, style: String = "", content: String) {
            sb.append("<div style=\"left:${mm(x)};top:${mm(y)};width:${mm(w)};height:${mm(h)};$style\">$content</div>")
        }
        fun text(size: Number, color: String, medium: Boolean = false, italic: Boolean = false, align: String = "left", bold: Boolean = false) =
            "font-size:${pt(size)};line-height:1.2;color:$color;${if (bold) "font-weight:600;" else if (medium) "font-weight:500;" else ""}${if (italic) "font-style:italic;" else ""}text-align:$align;"
        fun logo(white: Boolean, x: Double, y: Double, h: Double) {
            val bytes = if (white) TudBrand.logoWhite else TudBrand.logoBlue
            box(x, y, h * 1190 / 400, h, content = "<img src=\"${DocMarkup.dataUrl(bytes)}\" alt=\"TU Dresden\" style=\"width:100%;height:100%\">")
        }
        // The Shape in Blau 2, cut off at the top right (section and closing slides).
        fun shape() {
            val k = 10 * f
            sb.append(
                "<svg style=\"position:absolute;left:0;top:0\" width=\"${mm(TudSlide.W)}\" height=\"${mm(TudSlide.H)}\" viewBox=\"0 0 ${TudSlide.W * k} ${TudSlide.H * k}\" aria-hidden=\"true\">" +
                    TudBrand.shapeSvgPolygons(TudSlide.SHAPE_X * k, TudSlide.SHAPE_Y * k, TudSlide.SHAPE_SIDE * k, LIGHT) + "</svg>",
            )
        }
        // Footer of the CD: short logo „TUD“ at the bottom left, then „Titel • Vortragende • Seite N“ in Brillantblau.
        fun chrome() {
            val bytes = if (dark) TudBrand.logoShortWhite else TudBrand.logoShortBlue
            box(TudSlide.X, TudSlide.FOOT_Y, TudSlide.FOOT_LOGO_H * TudBrand.LOGO_SHORT_RATIO, TudSlide.FOOT_LOGO_H, content = "<img src=\"${DocMarkup.dataUrl(bytes)}\" alt=\"\" style=\"width:100%;height:100%;display:block\">")
            box(TudSlide.FOOT_TEXT_X, TudSlide.FOOT_Y - 0.05, TudSlide.FOOT_TEXT_W, 0.6, text(TudSlide.FOOT_PT, if (dark) "#fff" else BLUE) + "white-space:nowrap;display:flex;align-items:center", esc(TudSlide.footer(footer, i + 1)))
        }
        fun heading(t: String) = box(
            TudSlide.X, TudSlide.TITLE_Y, TudSlide.CONTENT_W, TudSlide.TITLE_H,
            text(if (t.length > 55) TudSlide.TITLE_LONG_PT else TudSlide.TITLE_PT, "#000", medium = true), "<span>${inline(t)}</span>",
        )
        fun picture(src: String, alt: String, x: Double, y: Double, w: Double, h: Double) {
            val bytes = DocMarkup.imageBytes(src, base) ?: return
            box(x, y, w, h, "text-align:center", "<img src=\"${DocMarkup.dataUrl(bytes)}\" alt=\"${esc(alt)}\" style=\"max-width:100%;max-height:100%\">")
        }
        fun body(md: String, half: Boolean, x: Double, y: Double, w: Double, h: Double) = box(x, y, w, h, content = content(md, half, ::pt))
        val top = TudSlide.BODY_TOP
        val bodyH = TudSlide.BODY_BOTTOM - TudSlide.BODY_TOP
        val colW = 14.8
        val col2 = TudSlide.X + TudSlide.CONTENT_W - colW
        when (s.layout) {
            SlideDeck.Layout.TITLE, SlideDeck.Layout.END -> {
                logo(true, TudSlide.X, TudSlide.LOGO_Y, TudSlide.LOGO_H)
                val lines = s.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (s.layout == SlideDeck.Layout.TITLE) {
                    // Subtitle above the title, unit at the bottom (Titelfolie_Blau of the CD).
                    if (lines.isNotEmpty()) box(
                        TudSlide.X, TudSlide.SUBTITLE_Y, TudSlide.CONTENT_W, TudSlide.SUBTITLE_H,
                        text(if (lines.size > 3) 16 else 20, "#fff") + "display:flex;flex-direction:column;justify-content:flex-end",
                        "<span>" + lines.take(5).joinToString("<br>") { inline(it) } + "</span>",
                    )
                    box(TudSlide.X, TudSlide.COVER_TITLE_Y, TudSlide.CONTENT_W, TudSlide.COVER_TITLE_H, text(if (s.title.length > 60) 36 else TudSlide.COVER_TITLE_PT, "#fff", medium = true), inline(s.title))
                    if (unit.isNotEmpty()) box(TudSlide.X, TudSlide.COVER_FOOT_Y, 21.76, 0.71, text(12, "#fff") + "white-space:nowrap", esc(unit.joinToString(" · ")))
                } else {
                    shape()
                    box(TudSlide.X, 4.82, TudSlide.CONTENT_W, 4.5, text(TudSlide.COVER_TITLE_PT, "#fff", medium = true) + "display:flex;align-items:flex-end", "<span>${inline(s.title.ifBlank { "Vielen Dank!" })}</span>")
                    box(TudSlide.X, TudSlide.COVER_TITLE_Y, TudSlide.CONTENT_W, TudSlide.COVER_TITLE_H, text(20, "#fff"), lines.joinToString("<br>") { inline(it.removePrefix("- ")) })
                }
            }
            SlideDeck.Layout.SECTION -> {
                shape()
                box(TudSlide.X, 7.9, TudSlide.CONTENT_W, 5.0, text(36, "#fff", medium = true) + "display:flex;align-items:flex-end", "<span>${inline(s.title)}</span>")
                if (s.body.isNotBlank()) box(TudSlide.X, 13.8, TudSlide.CONTENT_W, 2.43, text(20, "#fff"), inline(s.body.lines().joinToString(" ")))
                chrome()
            }
            SlideDeck.Layout.QUOTE -> {
                if (s.title.isNotBlank()) heading(s.title)
                val q = s.body.lines().map { it.trim().removePrefix(">").trim() }.filter { it.isNotEmpty() }
                val source = q.lastOrNull()?.takeIf { q.size > 1 && (it.startsWith("—") || it.startsWith("–") || it.startsWith("-")) }
                val quote = (if (source != null) q.dropLast(1) else q).joinToString(" ").trim().trim('„', '“', '"')
                val qTop = if (s.title.isNotBlank()) top else 2.43
                box(
                    TudSlide.X, qTop, TudSlide.CONTENT_W, TudSlide.BODY_BOTTOM - qTop, text(if (quote.length > 220) 24 else 32, "#000", italic = true),
                    "„${inline(quote)}“" + (source?.let { "<p style=\"" + text(14, "#000") + "font-style:normal;margin-top:${pt(14)}\">${inline(it)}</p>" } ?: ""),
                )
                chrome()
            }
            SlideDeck.Layout.IMAGE_TEXT -> {
                heading(s.title)
                val (ix, tx) = if (s.imageLeft) TudSlide.X to col2 else col2 to TudSlide.X
                picture(s.image, s.imageAlt, ix, top, colW, bodyH)
                body(s.body, true, tx, top, colW, bodyH)
                chrome()
            }
            SlideDeck.Layout.IMAGE -> {
                val withTitle = s.title.isNotBlank()
                if (withTitle) heading(s.title)
                val caption = s.imageAlt.isNotBlank() && s.imageAlt != s.title
                val pTop = if (withTitle) top else 1.12
                picture(s.image, s.imageAlt, TudSlide.X, pTop, TudSlide.CONTENT_W, (if (caption) 15.4 else TudSlide.BODY_BOTTOM) - pTop)
                if (caption) box(TudSlide.X, 15.5, TudSlide.CONTENT_W, 0.7, text(12, GREY), inline(s.imageAlt))
                chrome()
            }
            SlideDeck.Layout.COMPARISON -> {
                heading(s.title)
                listOf(TudSlide.X to s.body, col2 to s.right).forEach { (x, col) ->
                    val (head, rest) = SlideDeck.columnHead(col)
                    var y = top
                    if (head.isNotBlank()) {
                        box(x, y, colW, 1.3, "background:$TINT;" + text(20, BLUE, medium = true) + "padding-left:${mm(0.4)};display:flex;align-items:center", inline(head))
                        y += 1.7
                    }
                    body(rest, true, x, y, colW, TudSlide.BODY_BOTTOM - y)
                }
                chrome()
            }
            SlideDeck.Layout.CHART -> {
                heading(s.title)
                val spec = SlideDeck.chartOf(s.body)
                if (spec != null) box(TudSlide.X, top, TudSlide.CONTENT_W, bodyH, "text-align:center", ChartLayout.svg(spec).replace("width=\"100%\"", "width=\"100%\" height=\"100%\""))
                chrome()
            }
            SlideDeck.Layout.AGENDA -> {
                heading(s.title.ifBlank { "Agenda" })
                val items = SlideDeck.agenda(slides, i)
                box(TudSlide.X, top, TudSlide.CONTENT_W, bodyH, text(if (items.size > 7) 20 else 24, "#000"), "<ol>" + items.joinToString("") { "<li style=\"margin-top:${pt(10)}\">${inline(it)}</li>" } + "</ol>")
                chrome()
            }
            SlideDeck.Layout.TWO_COLUMNS -> {
                heading(s.title)
                body(s.body, true, TudSlide.X, top, colW, bodyH)
                body(s.right, true, col2, top, colW, bodyH)
                chrome()
            }
            SlideDeck.Layout.CONTENT, SlideDeck.Layout.TABLE -> {
                heading(s.title)
                body(s.body, false, TudSlide.X, top, TudSlide.CONTENT_W, bodyH)
                chrome()
            }
            SlideDeck.Layout.FREE -> {
                if (s.title.isNotBlank()) heading(s.title)
                if (s.body.isNotBlank()) body(s.body, false, TudSlide.X, top, TudSlide.CONTENT_W, bodyH)
                chrome()
            }
        }
        // Frei gestalten (AP-16): elements over the layout.
        s.elements.forEach { el ->
            val color = el.color.ifBlank { "#00305D" }
            // Shared canvas (AP-50): turned around the middle, gradients, formatting inside the box, its own elements.
            val turn = if (el.rotation != 0.0) "transform:rotate(${"%.1f".format(java.util.Locale.ROOT, el.rotation)}deg);" else ""
            fun paint(fill: String) = "background:$fill;" + if (el.fill2.isNotBlank()) "background:linear-gradient($fill,${el.fill2});" else ""
            fun runs(): String = io.github.thepytales.llmhub.creative.FieldRich.lines(el.text, el.rich).joinToString("<br>") { line ->
                line.joinToString("") { (t, f) ->
                    var out = esc(t)
                    if (f.bold) out = "<b>$out</b>"
                    if (f.italic) out = "<i>$out</i>"
                    if (f.underline) out = "<u>$out</u>"
                    if (f.strike) out = "<s>$out</s>"
                    f.color?.let { out = "<span style=\"color:#$it\">$out</span>" }
                    out
                }
            }
            when (el.kind) {
                "bild" -> picture(el.src, el.text, el.x, el.y, el.w, el.h)
                "tudshape" -> {
                    val side = minOf(el.w, el.h)
                    val flip = if (el.flipX) " transform=\"translate(${el.w},0) scale(-1,1)\"" else ""
                    box(el.x, el.y, el.w, el.h, turn, "<svg viewBox=\"0 0 ${el.w} ${el.h}\" style=\"width:100%;height:100%\"><g$flip>" +
                        TudBrand.shapeSvgPolygons((el.w - side) / 2, (el.h - side) / 2, side, el.fill.ifBlank { TudBrand.LIGHT_BLUE }) + "</g></svg>")
                }
                "symbol", "qr" -> {
                    val d = io.github.thepytales.llmhub.creative.SlideCanvas.toDesign(el, "x")
                    val png = io.github.thepytales.llmhub.creative.CanvasArt.png(d, 12f) ?: return@forEach
                    box(el.x, el.y, el.w, el.h, turn, "<img src=\"${DocMarkup.dataUrl(png)}\" alt=\"${esc(if (el.kind == "qr") "QR-Code: " + el.text else "Symbol")}\" style=\"width:100%;height:100%\">")
                }
                "tabelle" -> {
                    val t = io.github.thepytales.llmhub.creative.CanvasTable
                    val probe = io.github.thepytales.llmhub.creative.SlideCanvas.toDesign(el, "x")
                    val html = t.normalized(el.rows).mapIndexed { r, row ->
                        val bg = t.fill(probe, r).takeIf { it.isNotBlank() }?.let { "background:$it;" }.orEmpty()
                        "<tr style=\"$bg\">" + row.joinToString("") { cell ->
                            "<td style=\"border:0.2mm solid ${t.LINE};padding:0.6mm 1mm;color:${t.textColor(probe, r)};${if (t.bold(probe, r)) "font-weight:600;" else ""}\">${inline(cell)}</td>"
                        } + "</tr>"
                    }.joinToString("")
                    box(el.x, el.y, el.w, el.h, turn + text(el.size, color), "<table style=\"position:static;width:100%;height:100%;border-collapse:collapse\">$html</table>")
                }
                "form" -> {
                    val fill = el.fill.ifBlank { "#00305D" }
                    val radius = when (el.shape) { "ellipse" -> "border-radius:50%;"; "abgerundet" -> "border-radius:12%;"; else -> "" }
                    val styleCss = if (el.shape == "linie") "border-top:2px solid $color;" else paint(fill) + radius
                    box(el.x, el.y, el.w, el.h, turn + styleCss + text(el.size, if (el.color.isBlank()) "#fff" else color, bold = el.bold), if (el.rich) runs() else inline(el.text))
                }
                else -> box(el.x, el.y, el.w, el.h, turn + (if (el.fill.isNotBlank()) paint(el.fill) else "") + text(el.size, color, bold = el.bold), if (el.rich) runs() else el.text.lines().joinToString("<br>") { inline(it) })
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
                    is Block.Heading -> append("<p style=\"margin-top:${pt(10)};color:$BLUE;font-weight:500\">${inline(b.text)}</p>")
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
