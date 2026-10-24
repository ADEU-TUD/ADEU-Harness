package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PowerPoint presentations (16:9) in the corporate design of TU Dresden, written as Office Open XML without
 * external libraries. The model writes Markdown: slides separated by "---", "# Titel" per slide, lists, tables,
 * quotes, two columns split by "|||" and speaker notes after "Notizen:". The first slide is the title slide
 * (blue with the white logo); a slide with only a heading becomes a section slide.
 */
object PptxWriter {
    enum class Kind { TITLE, SECTION, CONTENT, QUOTE }

    internal data class Slide(
        val kind: Kind,
        val title: String,
        val subtitle: String = "",
        val blocks: List<Block> = emptyList(),
        val right: List<Block> = emptyList(),
        val notes: String = "",
    )

    private const val W = 12192000L // 33.867 cm
    private const val H = 6858000L // 19.05 cm
    private fun cm(v: Double) = (v * 360000).toLong()

    private val SEPARATOR = Regex("^\\s*-{3,}\\s*$")
    private val NOTES = Regex("^\\s*(Notizen|Notiz|Sprechernotizen|Sprechertext|Notes)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val COLUMNS = Regex("^\\s*(\\|\\|\\||::: *rechts|:::\\s*right)\\s*$", RegexOption.IGNORE_CASE)

    private val BLUE = TudBrand.BLUE.removePrefix("#")
    private val LIGHT = TudBrand.LIGHT_BLUE.removePrefix("#")
    private val GREY = TudBrand.GREY.removePrefix("#")
    private const val LINE = "BBC1C6"
    private const val TINT = "E5F1FF"
    private const val FONT = "Noto Sans"

    /** Splits the Markdown into slides (outside of code blocks at "---"). */
    internal fun parse(markdown: String): List<Slide> {
        val chunks = mutableListOf(StringBuilder())
        var inCode = false
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (!inCode && SEPARATOR.matches(line)) chunks += StringBuilder() else chunks.last().append(line).append('\n')
        }
        val texts = chunks.map { it.toString().trim() }.filter { it.isNotEmpty() }
        return texts.mapIndexed { i, t -> slide(t, first = i == 0) }
    }

    private fun slide(text: String, first: Boolean): Slide {
        val lines = text.lines()
        val notesAt = lines.indexOfFirst { NOTES.containsMatchIn(it) }
        val notes = if (notesAt < 0) "" else (listOf(lines[notesAt].replace(NOTES, "")) + lines.drop(notesAt + 1)).joinToString("\n").trim()
        val main = if (notesAt < 0) lines else lines.take(notesAt)
        val split = main.indexOfFirst { COLUMNS.matches(it) }
        val left = parseBlocks((if (split < 0) main else main.take(split)).joinToString("\n"))
        val right = if (split < 0) emptyList() else parseBlocks(main.drop(split + 1).joinToString("\n"))
        val headIdx = left.indexOfFirst { it is Block.Heading }
        val title = (left.getOrNull(headIdx) as? Block.Heading)?.text?.trim().orEmpty()
        var rest = if (headIdx < 0) left else left.filterIndexed { i, _ -> i != headIdx }
        if (first) {
            val sub = rest.firstOrNull { it is Block.Heading } as? Block.Heading
            if (sub != null) rest = rest - sub
            return Slide(Kind.TITLE, title, sub?.text?.trim().orEmpty(), rest, notes = notes)
        }
        if (right.isEmpty() && rest.size == 1 && rest[0] is Block.Quote) return Slide(Kind.QUOTE, title, blocks = rest, notes = notes)
        val short = rest.isEmpty() || rest.size == 1 && rest[0] is Block.Paragraph && (rest[0] as Block.Paragraph).text.length <= 140
        if (right.isEmpty() && short && title.isNotEmpty()) {
            return Slide(Kind.SECTION, title, (rest.firstOrNull() as? Block.Paragraph)?.text.orEmpty(), notes = notes)
        }
        return Slide(Kind.CONTENT, title, blocks = rest, right = right, notes = notes)
    }

    /**
     * Writes the presentation. [unit] is the organizational unit (one level per line), shown on the title slide;
     * its last line also appears in the footer.
     */
    fun write(file: File, title: String, markdown: String, unit: String, footer: String = "") {
        val slides = parse(markdown).ifEmpty { listOf(Slide(Kind.TITLE, title)) }
        val deckTitle = slides.first().title.ifBlank { title }
        val unitLines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val foot = footer.ifBlank { listOfNotNull("TU Dresden", unitLines.lastOrNull(), deckTitle.takeIf { it.isNotBlank() }).joinToString(" · ") }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun bytes(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            fun entry(name: String, content: String) = bytes(name, content.toByteArray(Charsets.UTF_8))
            entry("[Content_Types].xml", contentTypes(slides))
            entry("_rels/.rels", ROOT_RELS)
            entry("docProps/core.xml", core(deckTitle))
            entry("docProps/app.xml", app(slides.size))
            entry("ppt/presentation.xml", presentation(slides.size))
            entry("ppt/_rels/presentation.xml.rels", presentationRels(slides.size))
            entry("ppt/presProps.xml", PRES_PROPS)
            entry("ppt/viewProps.xml", VIEW_PROPS)
            entry("ppt/tableStyles.xml", TABLE_STYLES)
            entry("ppt/theme/theme1.xml", theme("TUD"))
            entry("ppt/theme/theme2.xml", theme("TUD Notizen"))
            entry("ppt/slideMasters/slideMaster1.xml", MASTER)
            entry("ppt/slideMasters/_rels/slideMaster1.xml.rels", MASTER_RELS)
            entry("ppt/slideLayouts/slideLayout1.xml", LAYOUT)
            entry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", LAYOUT_RELS)
            entry("ppt/notesMasters/notesMaster1.xml", NOTES_MASTER)
            entry("ppt/notesMasters/_rels/notesMaster1.xml.rels", NOTES_MASTER_RELS)
            bytes("ppt/media/logo-blau.png", TudBrand.logoBlue)
            bytes("ppt/media/logo-weiss.png", TudBrand.logoWhite)
            slides.forEachIndexed { i, s ->
                val n = i + 1
                entry("ppt/slides/slide$n.xml", slideXml(s, n, slides.size, unitLines, foot))
                entry("ppt/slides/_rels/slide$n.xml.rels", slideRels(n, s.notes.isNotBlank()))
                if (s.notes.isNotBlank()) {
                    entry("ppt/notesSlides/notesSlide$n.xml", notesXml(s.notes))
                    entry("ppt/notesSlides/_rels/notesSlide$n.xml.rels", notesRels(n))
                }
            }
        }
    }

    // ── Slides ──────────────────────────────────────────────────────────────────────────────────────────────

    private class Shapes {
        private var id = 2
        val xml = StringBuilder()
        fun next() = id++
    }

    private fun slideXml(s: Slide, n: Int, total: Int, unit: List<String>, footer: String): String {
        val sh = Shapes()
        val dark = s.kind == Kind.TITLE
        when (s.kind) {
            Kind.TITLE -> {
                text(sh, "Titel", cm(1.6), cm(5.6), cm(29.5), cm(4.8), listOf(para(s.title, size = if (s.title.length > 60) 34 else 40, color = "FFFFFF")), anchor = "b", placeholder = "ctrTitle")
                if (s.subtitle.isNotBlank()) text(sh, "Untertitel", cm(1.6), cm(10.7), cm(29.5), cm(2.2), listOf(para(s.subtitle, size = 22, color = LIGHT)))
                val meta = s.blocks.flatMap { blockLines(it) }
                if (meta.isNotEmpty()) text(sh, "Angaben", cm(1.6), cm(13.2), cm(29.5), cm(2.4), meta.map { para(it, size = 16, color = "FFFFFF") })
                if (unit.isNotEmpty()) {
                    text(
                        sh, "Einrichtung", cm(1.6), cm(15.9), cm(29.5), cm(2.4),
                        unit.mapIndexed { i, l -> para(l, size = 11, color = "FFFFFF", bold = i == unit.lastIndex) }, anchor = "b",
                    )
                }
                picture(sh, "rIdLogoW", cm(1.6), cm(1.4), cm(1.7))
            }
            Kind.SECTION -> {
                text(sh, "Abschnitt", cm(2.5), cm(6.2), cm(28.0), cm(3.4), listOf(para(s.title, size = 36, color = BLUE)), anchor = "b", placeholder = "title")
                if (s.subtitle.isNotBlank()) text(sh, "Beschreibung", cm(2.5), cm(9.8), cm(28.0), cm(2.0), listOf(para(s.subtitle, size = 18, color = GREY)))
                rect(sh, cm(1.6), cm(6.4), cm(0.25), cm(5.2), BLUE)
                chrome(sh, n, footer)
            }
            Kind.QUOTE -> {
                if (s.title.isNotBlank()) title(sh, s.title) else hiddenTitle(sh, "Zitat")
                val q = (s.blocks.first() as Block.Quote).text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val source = q.lastOrNull()?.takeIf { q.size > 1 && (it.startsWith("—") || it.startsWith("–") || it.startsWith("-")) }
                val quote = (if (source != null) q.dropLast(1) else q).joinToString(" ")
                text(
                    sh, "Zitat", cm(3.5), cm(5.0), cm(26.8), cm(9.0),
                    listOf(para("„$quote“", size = if (quote.length > 220) 24 else 30, color = BLUE, italic = true, align = "ctr")), anchor = "ctr",
                )
                if (source != null) text(sh, "Quelle", cm(3.5), cm(14.2), cm(26.8), cm(1.2), listOf(para(source, size = 16, color = GREY, align = "ctr")))
                chrome(sh, n, footer)
            }
            Kind.CONTENT -> {
                if (s.title.isNotBlank()) title(sh, s.title)
                else hiddenTitle(sh, s.blocks.flatMap { blockLines(it) }.firstOrNull()?.take(60) ?: "Folie $n")
                val top = cm(4.0)
                val bottom = cm(17.0)
                if (s.right.isEmpty()) content(sh, s.blocks, cm(1.6), top, cm(30.6), bottom - top, half = false)
                else {
                    content(sh, s.blocks, cm(1.6), top, cm(14.8), bottom - top, half = true)
                    content(sh, s.right, cm(17.4), top, cm(14.8), bottom - top, half = true)
                }
                chrome(sh, n, footer)
            }
        }
        val bg = if (dark) "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"$BLUE\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>" else ""
        return "$XML<p:sld $NS><p:cSld>$bg<p:spTree>$GROUP${sh.xml}</p:spTree></p:cSld>" +
            "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
    }

    /** Logo at the top right, footer line with the unit and the slide number. */
    private fun chrome(sh: Shapes, n: Int, footer: String) {
        val h = cm(1.1)
        val w = h * 1190 / 400
        picture(sh, "rIdLogo", W - cm(1.4) - w, cm(1.0), h)
        rect(sh, cm(1.6), cm(17.6), W - cm(3.2), cm(0.03), LINE)
        text(sh, "Fußzeile", cm(1.6), cm(17.8), cm(26.0), cm(0.7), listOf(para(footer, size = 9, color = GREY)))
        text(sh, "Foliennummer", W - cm(5.6), cm(17.8), cm(4.0), cm(0.7), listOf(para("$n", size = 9, color = GREY, align = "r")))
    }

    private fun title(sh: Shapes, title: String) {
        text(sh, "Titel", cm(1.6), cm(1.0), cm(26.0), cm(2.6), listOf(para(title, size = if (title.length > 55) 24 else 28, color = BLUE)), anchor = "b", placeholder = "title")
    }

    /** Slides without a visible title get one above the slide: screen readers announce it, the audience does not see it. */
    private fun hiddenTitle(sh: Shapes, title: String) {
        text(sh, "Titel (ausgeblendet)", cm(1.6), -cm(3.0), cm(26.0), cm(2.0), listOf(para(title, size = 20, color = BLUE)), placeholder = "title")
    }

    /** Text blocks (and at most one table below them) in the given area. */
    private fun content(sh: Shapes, blocks: List<Block>, x: Long, y: Long, w: Long, h: Long, half: Boolean) {
        val table = blocks.firstOrNull { it is Block.Table } as? Block.Table
        val textBlocks = blocks.filter { it !is Block.Table }
        val perLine = if (half) 34 else 72
        val lines = textBlocks.sumOf { b -> blockLines(b).sumOf { 1 + it.length / perLine } }
        val size = when {
            lines <= 6 -> if (half) 20 else 24
            lines <= 8 -> if (half) 18 else 22
            lines <= 10 -> 18
            lines <= 13 -> 16
            else -> 14
        }
        var textHeight = h
        if (table != null) {
            val rows = table.rows.size
            val rowH = cm(if (rows > 8) 0.8 else 1.0)
            val tableH = (rowH * rows).coerceAtMost(h)
            // Text above the table takes the room it needs (about 1.5 × the font size per line), the table follows.
            val needed = (lines * size * 1.5 * 12700).toLong() + cm(0.3)
            val textH = if (textBlocks.isEmpty()) 0L else needed.coerceIn(cm(1.2), (h - tableH - cm(0.4)).coerceAtLeast(cm(1.2)))
            textHeight = textH
            table(sh, table, x, y + (if (textH > 0) textH + cm(0.4) else 0L), w, rowH, if (rows > 8) 11 else 14)
        }
        if (textBlocks.isNotEmpty() && textHeight > 0) {
            text(sh, "Inhalt", x, y, w, textHeight, textBlocks.flatMap { paragraphs(it, size) })
        }
    }

    private fun blockLines(b: Block): List<String> = when (b) {
        is Block.Paragraph -> b.text.lines()
        is Block.Item -> listOf(b.text)
        is Block.Heading -> listOf(b.text)
        is Block.Quote -> b.text.lines()
        is Block.Code -> b.code.lines()
        is Block.Table -> b.rows.map { it.joinToString(" ") }
        Block.Rule -> emptyList()
    }

    private fun paragraphs(b: Block, size: Int): List<String> = when (b) {
        is Block.Paragraph -> b.text.lines().map { para(it, size = size, spaceBefore = 6) }
        is Block.Heading -> listOf(para(b.text, size = size, color = BLUE, bold = true, spaceBefore = 10))
        is Block.Item -> {
            val numbered = b.marker.first().isDigit()
            val box = Regex("^\\[([ xX])]\\s+").find(b.text)
            val text = if (box != null) (if (box.groupValues[1].isBlank()) "☐ " else "☑ ") + b.text.substring(box.range.last + 1) else b.text
            listOf(para(text, size = if (b.indent > 0) size - 2 else size, level = b.indent.coerceAtMost(2), bullet = if (numbered) "num" else "•", spaceBefore = 6))
        }
        is Block.Quote -> b.text.lines().map { para(it, size = size, color = BLUE, italic = true, spaceBefore = 6) }
        is Block.Code -> b.code.lines().map { para(it, size = (size - 6).coerceAtLeast(10), mono = true) }
        is Block.Table, Block.Rule -> emptyList()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

    /** One paragraph with Markdown inline formatting (**fett**, *kursiv*, `Code`, Links). */
    private fun para(
        text: String,
        size: Int,
        color: String = "000000",
        bold: Boolean = false,
        italic: Boolean = false,
        align: String = "l",
        level: Int = 0,
        bullet: String? = null,
        spaceBefore: Int = 0,
        mono: Boolean = false,
    ): String {
        val pPr = buildString {
            append("<a:pPr algn=\"$align\"")
            if (bullet != null) {
                // Numbers need more room than bullet points.
                val hang = if (bullet == "num") 457200L else 285750L
                val mar = hang + 342900L * level
                append(" marL=\"$mar\" indent=\"-$hang\"")
            } else append(" marL=\"0\" indent=\"0\"")
            append(">")
            if (spaceBefore > 0) append("<a:spcBef><a:spcPts val=\"${spaceBefore * 100}\"/></a:spcBef>")
            when {
                bullet == "num" -> append("<a:buClr><a:srgbClr val=\"$BLUE\"/></a:buClr><a:buFont typeface=\"+mj-lt\"/><a:buAutoNum type=\"arabicPeriod\"/>")
                bullet != null -> append("<a:buClr><a:srgbClr val=\"$BLUE\"/></a:buClr><a:buFont typeface=\"Arial\"/><a:buChar char=\"${if (level > 0) "–" else "•"}\"/>")
                else -> append("<a:buNone/>")
            }
            append("</a:pPr>")
        }
        val runs = inlineSegments(text).joinToString("") { seg ->
            val b = bold || seg.bold
            val i = italic || seg.italic
            "<a:r><a:rPr lang=\"de-DE\" sz=\"${size * 100}\"" + (if (b) " b=\"1\"" else "") + (if (i) " i=\"1\"" else "") +
                (if (seg.strike) " strike=\"sngStrike\"" else "") + " dirty=\"0\">" +
                "<a:solidFill><a:srgbClr val=\"${if (seg.link != null) TudBrand.MID_BLUE.removePrefix("#") else color}\"/></a:solidFill>" +
                "<a:latin typeface=\"${if (mono || seg.code) "Consolas" else FONT}\"/><a:cs typeface=\"${if (mono || seg.code) "Consolas" else FONT}\"/>" +
                "</a:rPr><a:t>${esc(seg.text)}</a:t></a:r>"
        }
        return "<a:p>$pPr$runs<a:endParaRPr lang=\"de-DE\" sz=\"${size * 100}\" dirty=\"0\"/></a:p>"
    }

    /**
     * A text box; with [placeholder] ("title", "ctrTitle") it is the slide title, which screen readers announce and
     * PowerPoint's accessibility check expects on every slide.
     */
    private fun text(sh: Shapes, name: String, x: Long, y: Long, w: Long, h: Long, paragraphs: List<String>, anchor: String = "t", placeholder: String? = null) {
        val id = sh.next()
        val nv = if (placeholder == null) "<p:cNvSpPr txBox=\"1\"/><p:nvPr/>"
        else "<p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"$placeholder\"/></p:nvPr>"
        sh.xml.append(
            "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name $id\"/>$nv</p:nvSpPr>" +
                "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>" +
                "<p:txBody><a:bodyPr wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" anchor=\"$anchor\"><a:normAutofit/></a:bodyPr><a:lstStyle/>" +
                paragraphs.ifEmpty { listOf("<a:p><a:endParaRPr lang=\"de-DE\"/></a:p>") }.joinToString("") +
                "</p:txBody></p:sp>"
        )
    }

    private fun rect(sh: Shapes, x: Long, y: Long, w: Long, h: Long, color: String) {
        val id = sh.next()
        sh.xml.append(
            "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Linie $id\">$DECORATIVE</p:cNvPr><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>" +
                "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>"
        )
    }

    /** Logo with the aspect ratio of the bundled image (1190 × 400 px). */
    private fun picture(sh: Shapes, rel: String, x: Long, y: Long, h: Long) {
        val id = sh.next()
        val w = h * 1190 / 400
        sh.xml.append(
            "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Logo $id\" descr=\"Technische Universität Dresden\"/>" +
                "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                "<p:blipFill><a:blip r:embed=\"$rel\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"
        )
    }

    private fun table(sh: Shapes, t: Block.Table, x: Long, y: Long, w: Long, rowH: Long, size: Int) {
        val id = sh.next()
        val cols = t.rows.maxOf { it.size }
        val colW = w / cols
        fun border(tag: String) = "<a:$tag w=\"6350\"><a:solidFill><a:srgbClr val=\"$LINE\"/></a:solidFill></a:$tag>"
        val rows = t.rows.mapIndexed { r, row ->
            val cells = (0 until cols).joinToString("") { c ->
                val head = r == 0
                "<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>${para(row.getOrNull(c).orEmpty(), size = size, bold = head)}</a:txBody>" +
                    "<a:tcPr marL=\"72000\" marR=\"72000\" marT=\"36000\" marB=\"36000\" anchor=\"ctr\">" +
                    border("lnL") + border("lnR") + border("lnT") + border("lnB") +
                    (if (head) "<a:solidFill><a:srgbClr val=\"$TINT\"/></a:solidFill>" else "<a:noFill/>") + "</a:tcPr></a:tc>"
            }
            "<a:tr h=\"$rowH\">$cells</a:tr>"
        }.joinToString("")
        sh.xml.append(
            "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"$id\" name=\"Tabelle $id\"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>" +
                "<p:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"${colW * cols}\" cy=\"${rowH * t.rows.size}\"/></p:xfrm>" +
                "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl><a:tblPr firstRow=\"1\"/>" +
                "<a:tblGrid>${"<a:gridCol w=\"$colW\"/>".repeat(cols)}</a:tblGrid>$rows</a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
        )
    }

    private fun notesXml(notes: String): String {
        val paras = notes.lines().joinToString("") { l -> "<a:p><a:r><a:rPr lang=\"de-DE\" dirty=\"0\"/><a:t>${esc(l)}</a:t></a:r></a:p>" }
        return "$XML<p:notes $NS><p:cSld><p:spTree>$GROUP" +
            "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Folienbild\"/><p:cNvSpPr><a:spLocks noGrp=\"1\" noRot=\"1\" noChangeAspect=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"sldImg\"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>" +
            "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Notizen\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"body\" idx=\"3\"/></p:nvPr></p:nvSpPr><p:spPr/>" +
            "<p:txBody><a:bodyPr/><a:lstStyle/>$paras</p:txBody></p:sp>" +
            "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>"
    }

    // ── Package parts ───────────────────────────────────────────────────────────────────────────────────────

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    /** Marks lines and areas as decorative, so screen readers skip them (Office 2019+). */
    private const val DECORATIVE = "<a:extLst><a:ext uri=\"{C183D7F6-B498-43B3-948B-1728B52AA6E4}\">" +
        "<adec:decorative xmlns:adec=\"http://schemas.microsoft.com/office/drawing/2017/decorative\" val=\"1\"/></a:ext></a:extLst>"
    private const val NS = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
    private const val GROUP = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CT = "application/vnd.openxmlformats-officedocument.presentationml"

    private fun contentTypes(slides: List<Slide>) = buildString {
        append(XML).append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"$CT.presentation.main+xml\"/>")
        append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"$CT.slideMaster+xml\"/>")
        append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"$CT.slideLayout+xml\"/>")
        append("<Override PartName=\"/ppt/notesMasters/notesMaster1.xml\" ContentType=\"$CT.notesMaster+xml\"/>")
        append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        append("<Override PartName=\"/ppt/theme/theme2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        append("<Override PartName=\"/ppt/presProps.xml\" ContentType=\"$CT.presProps+xml\"/>")
        append("<Override PartName=\"/ppt/viewProps.xml\" ContentType=\"$CT.viewProps+xml\"/>")
        append("<Override PartName=\"/ppt/tableStyles.xml\" ContentType=\"$CT.tableStyles+xml\"/>")
        append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
        slides.forEachIndexed { i, s ->
            append("<Override PartName=\"/ppt/slides/slide${i + 1}.xml\" ContentType=\"$CT.slide+xml\"/>")
            if (s.notes.isNotBlank()) append("<Override PartName=\"/ppt/notesSlides/notesSlide${i + 1}.xml\" ContentType=\"$CT.notesSlide+xml\"/>")
        }
        append("</Types>")
    }

    private const val ROOT_RELS = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"ppt/presentation.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
        "<Relationship Id=\"rId3\" Type=\"$REL/extended-properties\" Target=\"docProps/app.xml\"/>" +
        "</Relationships>"

    private fun core(title: String): String {
        val now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        return XML + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${esc(title)}</dc:title><dc:language>de-DE</dc:language><dc:creator>ADEU LLM</dc:creator>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified>" +
            "</cp:coreProperties>"
    }

    private fun app(count: Int) = XML + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
        "<Application>ADEU LLM</Application><PresentationFormat>Breitbild</PresentationFormat><Slides>$count</Slides></Properties>"

    private fun presentation(count: Int) = buildString {
        append(XML).append("<p:presentation $NS saveSubsetFonts=\"1\">")
        append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rIdMaster\"/></p:sldMasterIdLst>")
        append("<p:notesMasterIdLst><p:notesMasterId r:id=\"rIdNotesMaster\"/></p:notesMasterIdLst>")
        append("<p:sldIdLst>")
        for (i in 1..count) append("<p:sldId id=\"${255 + i}\" r:id=\"rIdSlide$i\"/>")
        append("</p:sldIdLst>")
        append("<p:sldSz cx=\"$W\" cy=\"$H\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/>")
        append("<p:defaultTextStyle><a:defPPr><a:defRPr lang=\"de-DE\"/></a:defPPr></p:defaultTextStyle>")
        append("</p:presentation>")
    }

    private fun presentationRels(count: Int) = buildString {
        append(XML).append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        append("<Relationship Id=\"rIdMaster\" Type=\"$REL/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
        append("<Relationship Id=\"rIdNotesMaster\" Type=\"$REL/notesMaster\" Target=\"notesMasters/notesMaster1.xml\"/>")
        append("<Relationship Id=\"rIdTheme\" Type=\"$REL/theme\" Target=\"theme/theme1.xml\"/>")
        append("<Relationship Id=\"rIdPresProps\" Type=\"$REL/presProps\" Target=\"presProps.xml\"/>")
        append("<Relationship Id=\"rIdViewProps\" Type=\"$REL/viewProps\" Target=\"viewProps.xml\"/>")
        append("<Relationship Id=\"rIdTableStyles\" Type=\"$REL/tableStyles\" Target=\"tableStyles.xml\"/>")
        for (i in 1..count) append("<Relationship Id=\"rIdSlide$i\" Type=\"$REL/slide\" Target=\"slides/slide$i.xml\"/>")
        append("</Relationships>")
    }

    private fun slideRels(n: Int, notes: Boolean) = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rIdLayout\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
        "<Relationship Id=\"rIdLogo\" Type=\"$REL/image\" Target=\"../media/logo-blau.png\"/>" +
        "<Relationship Id=\"rIdLogoW\" Type=\"$REL/image\" Target=\"../media/logo-weiss.png\"/>" +
        (if (notes) "<Relationship Id=\"rIdNotes\" Type=\"$REL/notesSlide\" Target=\"../notesSlides/notesSlide$n.xml\"/>" else "") +
        "</Relationships>"

    private fun notesRels(n: Int) = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rIdNotesMaster\" Type=\"$REL/notesMaster\" Target=\"../notesMasters/notesMaster1.xml\"/>" +
        "<Relationship Id=\"rIdSlide\" Type=\"$REL/slide\" Target=\"../slides/slide$n.xml\"/>" +
        "</Relationships>"

    private const val PRES_PROPS = XML + "<p:presentationPr $NS/>"
    private const val VIEW_PROPS = XML + "<p:viewPr $NS><p:normalViewPr><p:restoredLeft sz=\"15620\"/><p:restoredTop sz=\"94660\"/></p:normalViewPr><p:gridSpacing cx=\"72008\" cy=\"72008\"/></p:viewPr>"
    private const val TABLE_STYLES = XML + "<a:tblStyleLst xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" def=\"{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}\"/>"

    private const val CLR_MAP = "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" " +
        "accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>"

    /** Title placeholder of master and layout, so the slide titles have something to inherit from. */
    private const val TITLE_PH = "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Titel\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>" +
        "<p:nvPr><p:ph type=\"title\"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"576000\" y=\"360000\"/><a:ext cx=\"9360000\" cy=\"936000\"/></a:xfrm>" +
        "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr><p:txBody><a:bodyPr anchor=\"b\"/><a:lstStyle/><a:p><a:endParaRPr lang=\"de-DE\"/></a:p></p:txBody></p:sp>"

    private const val MASTER = XML + "<p:sldMaster $NS><p:cSld><p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg>" +
        "<p:spTree>$GROUP$TITLE_PH</p:spTree></p:cSld>$CLR_MAP" +
        "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rIdLayout\"/></p:sldLayoutIdLst>" +
        "<p:txStyles><p:titleStyle><a:lvl1pPr><a:defRPr sz=\"2800\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mj-lt\"/></a:defRPr></a:lvl1pPr></p:titleStyle>" +
        "<p:bodyStyle><a:lvl1pPr><a:defRPr sz=\"2400\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/></a:defRPr></a:lvl1pPr></p:bodyStyle>" +
        "<p:otherStyle><a:lvl1pPr><a:defRPr sz=\"1800\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/></a:defRPr></a:lvl1pPr></p:otherStyle></p:txStyles>" +
        "</p:sldMaster>"

    private const val MASTER_RELS = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rIdLayout\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
        "<Relationship Id=\"rIdTheme\" Type=\"$REL/theme\" Target=\"../theme/theme1.xml\"/>" +
        "</Relationships>"

    private const val LAYOUT = XML + "<p:sldLayout $NS type=\"titleOnly\" preserve=\"1\"><p:cSld name=\"Nur Titel\"><p:spTree>$GROUP$TITLE_PH</p:spTree></p:cSld>" +
        "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"

    private const val LAYOUT_RELS = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rIdMaster\" Type=\"$REL/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/>" +
        "</Relationships>"

    private const val NOTES_MASTER = XML + "<p:notesMaster $NS><p:cSld><p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg><p:spTree>$GROUP" +
        "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Folienbild\"/><p:cNvSpPr><a:spLocks noGrp=\"1\" noRot=\"1\" noChangeAspect=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"sldImg\" idx=\"2\"/></p:nvPr></p:nvSpPr>" +
        "<p:spPr><a:xfrm><a:off x=\"381000\" y=\"685800\"/><a:ext cx=\"6096000\" cy=\"3429000\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln w=\"12700\"><a:solidFill><a:prstClr val=\"black\"/></a:solidFill></a:ln></p:spPr></p:sp>" +
        "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Notizen\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"body\" sz=\"quarter\" idx=\"3\"/></p:nvPr></p:nvSpPr>" +
        "<p:spPr><a:xfrm><a:off x=\"685800\" y=\"4400550\"/><a:ext cx=\"5486400\" cy=\"3600450\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr>" +
        "<p:txBody><a:bodyPr vert=\"horz\" lIns=\"91440\" tIns=\"45720\" rIns=\"91440\" bIns=\"45720\" rtlCol=\"0\"/><a:lstStyle/><a:p><a:pPr lvl=\"0\"/><a:r><a:rPr lang=\"de-DE\"/><a:t>Notizen</a:t></a:r></a:p></p:txBody></p:sp>" +
        "</p:spTree></p:cSld>$CLR_MAP" +
        "<p:notesStyle><a:lvl1pPr marL=\"0\" algn=\"l\" defTabSz=\"914400\" rtl=\"0\" eaLnBrk=\"1\" latinLnBrk=\"0\" hangingPunct=\"1\"><a:defRPr sz=\"1200\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/><a:ea typeface=\"+mn-ea\"/><a:cs typeface=\"+mn-cs\"/></a:defRPr></a:lvl1pPr></p:notesStyle>" +
        "</p:notesMaster>"

    private const val NOTES_MASTER_RELS = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rIdTheme\" Type=\"$REL/theme\" Target=\"../theme/theme2.xml\"/>" +
        "</Relationships>"

    /** Office theme with the colours of „TUD_2025“ and Noto Sans as heading and body font. */
    private fun theme(name: String) = XML + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"$name\"><a:themeElements>" +
        "<a:clrScheme name=\"TUD_2025\"><a:dk1><a:srgbClr val=\"000000\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"$GREY\"/></a:dk2><a:lt2><a:srgbClr val=\"$TINT\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"$BLUE\"/></a:accent1><a:accent2><a:srgbClr val=\"${TudBrand.MID_BLUE.removePrefix("#")}\"/></a:accent2>" +
        "<a:accent3><a:srgbClr val=\"$LIGHT\"/></a:accent3><a:accent4><a:srgbClr val=\"${TudBrand.NIGHT_BLUE.removePrefix("#")}\"/></a:accent4>" +
        "<a:accent5><a:srgbClr val=\"65B32E\"/></a:accent5><a:accent6><a:srgbClr val=\"EE7F00\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"${TudBrand.MID_BLUE.removePrefix("#")}\"/></a:hlink><a:folHlink><a:srgbClr val=\"$GREY\"/></a:folHlink></a:clrScheme>" +
        "<a:fontScheme name=\"TUD\"><a:majorFont><a:latin typeface=\"$FONT\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
        "<a:minorFont><a:latin typeface=\"$FONT\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
        "<a:fmtScheme name=\"Office\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"50000\"/></a:schemeClr></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:shade val=\"80000\"/></a:schemeClr></a:solidFill></a:fillStyleLst>" +
        "<a:lnStyleLst><a:ln w=\"6350\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" +
        "<a:ln w=\"12700\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>" +
        "<a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst>" +
        "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>" +
        "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"95000\"/></a:schemeClr></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:shade val=\"90000\"/></a:schemeClr></a:solidFill></a:bgFillStyleLst></a:fmtScheme>" +
        "</a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>"

    /** Short description for the tool result. */
    fun summary(markdown: String): String {
        val slides = parse(markdown)
        val notes = slides.count { it.notes.isNotBlank() }
        return "${slides.size} Folien" + if (notes > 0) ", $notes mit Sprechernotizen" else ""
    }
}
