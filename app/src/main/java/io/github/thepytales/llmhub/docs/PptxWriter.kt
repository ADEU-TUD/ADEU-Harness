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
    enum class Kind { TITLE, SECTION, CONTENT, QUOTE, IMAGE_TEXT, IMAGE, COMPARISON, TABLE, CHART, AGENDA, END }

    internal data class Slide(
        val kind: Kind,
        val title: String,
        val subtitle: String = "",
        val blocks: List<Block> = emptyList(),
        val right: List<Block> = emptyList(),
        val notes: String = "",
        /** Picture: source (absolute path or data URL), description, on the left of the text. */
        val image: String = "",
        val imageAlt: String = "",
        val imageLeft: Boolean = false,
        val chart: ChartSpec? = null,
        /** Headings of the two columns of a comparison. */
        val leftHead: String = "",
        val rightHead: String = "",
        /** Items of an agenda slide. */
        val agenda: List<String> = emptyList(),
        /** Übergang ("fade", "push") and paragraph build of the text (AP-51). */
        val transition: String = "",
        val build: Boolean = false,
    )

    private const val W = 12192000L // 33.867 cm
    private const val H = 6858000L // 19.05 cm
    private fun cm(v: Double) = (v * 360000).toLong()

    private val SEPARATOR = Regex("^\\s*-{3,}\\s*$")
    private val NOTES = Regex("^\\s*(Notizen|Notiz|Sprechernotizen|Sprechertext|Notes)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val COLUMNS = Regex("^\\s*(\\|\\|\\||::: *rechts|:::\\s*right)\\s*$", RegexOption.IGNORE_CASE)
    private val LAYOUT_MARK = Regex("^\\s*<!--\\s*layout\\s*:\\s*([a-zäöü-]+)\\s*-->\\s*$", RegexOption.IGNORE_CASE)

    private val BLUE = TudBrand.BLUE.removePrefix("#")
    private val LIGHT = TudBrand.LIGHT_BLUE.removePrefix("#")
    private val GREY = TudBrand.GREY.removePrefix("#")
    private const val LINE = "BBC1C6"
    /** Blau 2 at 25 % (table heads, column heads). */
    private const val TINT = "E5F1FF"
    private const val FONT = TudBrand.BODY_FONT
    /** Headings of the CD: Noto Sans Medium (the theme's major font). */
    private const val HEAD_FONT = TudBrand.HEAD_FONT
    private const val INK = "000000"
    private fun c(v: Double) = cm(v)
    /** Two columns of the content area (gap 0.88 cm). */
    private const val COL_W = 14.8
    private const val COL2_X = TudSlide.X + TudSlide.CONTENT_W - COL_W

    /** Splits the Markdown into slides (outside of code blocks at "---"). */
    internal fun parse(markdown: String): List<Slide> {
        val chunks = mutableListOf(StringBuilder())
        var inCode = false
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (!inCode && SEPARATOR.matches(line)) chunks += StringBuilder() else chunks.last().append(line).append('\n')
        }
        val texts = chunks.map { it.toString().trim() }.filter { it.isNotEmpty() }
        val slides = texts.mapIndexed { i, t -> SlideDeck.animationOf(t).let { a -> slide(t, first = i == 0).copy(transition = a.transition, build = a.build) } }
        // Agendas without own items list the section slides (or all titles).
        return slides.mapIndexed { i, s ->
            if (s.kind != Kind.AGENDA || s.agenda.isNotEmpty()) s
            else {
                val rest = slides.drop(1).filter { it.kind != Kind.AGENDA && it.kind != Kind.END && it.title.isNotBlank() }
                s.copy(agenda = rest.filter { it.kind == Kind.SECTION }.ifEmpty { rest }.map { it.title }.distinct().take(10))
            }
        }
    }

    private fun slide(text: String, first: Boolean): Slide {
        // Elements and Masterfolie of free design (AP-16) are added after the layout (SlideExtras).
        var lines = text.lines().filterNot { SlideDeck.isDesignLine(it) }
        val marked = lines.firstNotNullOfOrNull { l -> LAYOUT_MARK.find(l)?.groupValues?.get(1)?.let(SlideDeck::layoutOf) }
        lines = lines.filterNot { LAYOUT_MARK.matches(it) }
        val imageAt = lines.indexOfFirst { DocMarkup.image(it) != null }
        val img = if (imageAt >= 0) DocMarkup.image(lines[imageAt]) else null
        if (imageAt >= 0) lines = lines.filterIndexed { i, _ -> i != imageAt }
        val chart = if (marked?.first == SlideDeck.Layout.CHART) SlideDeck.chartOf(lines.joinToString("\n")) else null
        // Charts on other slides show their data as table.
        if (chart == null) lines = ChartBlocks.asTables(lines.joinToString("\n")).lines()
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
        val image = img?.src.orEmpty()
        val alt = img?.alt.orEmpty()
        when (marked?.first) {
            SlideDeck.Layout.IMAGE_TEXT -> return Slide(Kind.IMAGE_TEXT, title, blocks = rest + right, notes = notes, image = image, imageAlt = alt, imageLeft = marked?.second == true)
            SlideDeck.Layout.IMAGE -> return Slide(Kind.IMAGE, title, blocks = rest, notes = notes, image = image, imageAlt = alt)
            SlideDeck.Layout.COMPARISON -> {
                fun head(list: List<Block>) = (list.firstOrNull() as? Block.Heading)?.text?.trim().orEmpty()
                fun body(list: List<Block>) = if (list.firstOrNull() is Block.Heading) list.drop(1) else list
                return Slide(Kind.COMPARISON, title, blocks = body(rest), right = body(right), notes = notes, leftHead = head(rest), rightHead = head(right))
            }
            SlideDeck.Layout.TABLE -> return Slide(Kind.TABLE, title, blocks = rest + right, notes = notes)
            SlideDeck.Layout.CHART -> return Slide(Kind.CHART, title, blocks = rest.filter { it !is Block.Code }, notes = notes, chart = chart)
            SlideDeck.Layout.AGENDA -> return Slide(
                Kind.AGENDA, title.ifBlank { "Agenda" }, notes = notes,
                agenda = rest.flatMap { b -> if (b is Block.Item) listOf(b.text) else if (b is Block.Paragraph) b.text.lines() else emptyList() }.map { it.trim() }.filter { it.isNotEmpty() },
            )
            SlideDeck.Layout.END -> return Slide(Kind.END, title, blocks = rest, notes = notes)
            SlideDeck.Layout.FREE -> return Slide(Kind.CONTENT, title, blocks = rest + right, notes = notes)
            else -> {}
        }
        if (img != null) {
            return if (rest.isEmpty() && right.isEmpty()) Slide(Kind.IMAGE, title, notes = notes, image = image, imageAlt = alt)
            else Slide(Kind.IMAGE_TEXT, title, blocks = rest + right, notes = notes, image = image, imageAlt = alt)
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
    fun write(file: File, title: String, markdown: String, unit: String, footer: String = "", base: File? = null) {
        writeLayouts(file, title, markdown, unit, SlideDeck.masterOf(markdown)?.footer?.takeIf { it.isNotBlank() } ?: footer)
        // Frei gestalten (AP-16): freely placed elements and the Masterfolie on top.
        runCatching { SlideExtras.apply(file, markdown, base) }
    }

    private fun writeLayouts(file: File, title: String, markdown: String, unit: String, footer: String) {
        val slides = parse(markdown).ifEmpty { listOf(Slide(Kind.TITLE, title)) }
        val deckTitle = slides.first().title.ifBlank { title }
        val unitLines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // „Titel der Präsentation • Vortragende“ (the first line under the title, if there is one) as in the CD.
        val presenter = slides.first().takeIf { it.kind == Kind.TITLE }?.let { t -> (listOf(t.subtitle) + t.blocks.flatMap { blockLines(it) }).firstOrNull { it.isNotBlank() } }
        val foot = footer.ifBlank { listOfNotNull(deckTitle.takeIf { it.isNotBlank() }, presenter).joinToString(" • ").ifBlank { "TU Dresden" } }
        // Pictures of the slides (unreadable ones are left out, the slide keeps its text).
        val pictures = slides.map { s -> s.image.takeIf { it.isNotBlank() }?.let { DocMarkup.imageBytes(it) } }
        val media = pictures.mapIndexed { i, b -> b?.let { "bild${i + 1}.${DocMarkup.imageType(it) ?: "png"}" } }
        // Echte PowerPoint-Diagramme (AP-49): a chart slide gets a chart part with its data in an embedded workbook
        // („Daten bearbeiten“ in PowerPoint); box plots, which Office has no classic chart for, stay drawn with shapes.
        val charts = slides.mapIndexed { i, s ->
            if (s.kind != Kind.CHART || s.chart == null || s.chart.type == ChartSpec.Type.BOX) null
            else runCatching { OfficeCharts.embedded(s.chart, "../embeddings/Microsoft_Excel_Worksheet${i + 1}.xlsx") }.getOrNull()
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun bytes(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            fun entry(name: String, content: String) = bytes(name, content.toByteArray(Charsets.UTF_8))
            entry("[Content_Types].xml", contentTypes(slides, media.filterNotNull().map { it.substringAfterLast('.') }.toSet(), charts.mapIndexedNotNull { i, c -> if (c != null) i + 1 else null }))
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
            bytes("ppt/media/logo-kurz-blau.png", TudBrand.logoShortBlue)
            bytes("ppt/media/logo-kurz-weiss.png", TudBrand.logoShortWhite)
            slides.forEachIndexed { i, s ->
                val n = i + 1
                val picture = pictures[i]
                if (picture != null) bytes("ppt/media/${media[i]}", picture)
                entry("ppt/slides/slide$n.xml", slideXml(s, n, slides.size, unitLines, foot, picture?.let { DocMarkup.imageSize(it) ?: (4 to 3) }, nativeChart = charts[i] != null))
                entry("ppt/slides/_rels/slide$n.xml.rels", slideRels(n, s.notes.isNotBlank(), media[i], chart = charts[i] != null))
                charts[i]?.let { c ->
                    entry("ppt/charts/chart$n.xml", c.chartXml)
                    entry("ppt/charts/_rels/chart$n.xml.rels", c.relsXml)
                    bytes("ppt/embeddings/Microsoft_Excel_Worksheet$n.xlsx", c.workbook)
                }
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
        /** Text boxes written so far: shape id and number of paragraphs (for the build of the points). */
        val texts = mutableListOf<Pair<Int, Int>>()
    }

    /** Übergang of a slide as PowerPoint writes it. */
    private fun transitionXml(kind: String): String = when (kind) {
        "fade" -> "<p:transition spd=\"med\"><p:fade/></p:transition>"
        "push" -> "<p:transition spd=\"med\"><p:push dir=\"l\"/></p:transition>"
        else -> ""
    }

    /** Paragraphs of the text boxes [targets] (shape id, paragraphs) appear one per click (PowerPoint's „Erscheinen“, by paragraph). */
    private fun buildXml(targets: List<Pair<Int, Int>>): String {
        if (targets.isEmpty()) return ""
        var id = 2
        val clicks = StringBuilder()
        targets.forEach { (spid, paragraphs) ->
            for (p in 0 until paragraphs) {
                val a = ++id
                val b = ++id
                val c = ++id
                val d = ++id
                clicks.append(
                    "<p:par><p:cTn id=\"$a\" fill=\"hold\"><p:stCondLst><p:cond delay=\"indefinite\"/></p:stCondLst><p:childTnLst>" +
                        "<p:par><p:cTn id=\"$b\" fill=\"hold\"><p:stCondLst><p:cond delay=\"0\"/></p:stCondLst><p:childTnLst>" +
                        "<p:par><p:cTn id=\"$c\" presetID=\"1\" presetClass=\"entr\" presetSubtype=\"0\" fill=\"hold\" grpId=\"0\" nodeType=\"clickEffect\">" +
                        "<p:stCondLst><p:cond delay=\"0\"/></p:stCondLst><p:childTnLst><p:set><p:cBhvr><p:cTn id=\"$d\" dur=\"1\" fill=\"hold\"><p:stCondLst><p:cond delay=\"0\"/></p:stCondLst></p:cTn>" +
                        "<p:tgtEl><p:spTgt spid=\"$spid\"><p:txEl><p:pRg st=\"$p\" end=\"$p\"/></p:txEl></p:spTgt></p:tgtEl><p:attrNameLst><p:attrName>style.visibility</p:attrName></p:attrNameLst></p:cBhvr>" +
                        "<p:to><p:strVal val=\"visible\"/></p:to></p:set></p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par></p:childTnLst></p:cTn></p:par>",
                )
            }
        }
        return "<p:timing><p:tnLst><p:par><p:cTn id=\"1\" dur=\"indefinite\" restart=\"never\" nodeType=\"tmRoot\"><p:childTnLst>" +
            "<p:seq concurrent=\"1\" nextAc=\"seek\"><p:cTn id=\"2\" dur=\"indefinite\" nodeType=\"mainSeq\"><p:childTnLst>$clicks</p:childTnLst></p:cTn>" +
            "<p:prevCondLst><p:cond evt=\"onPrev\" delay=\"0\"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:prevCondLst>" +
            "<p:nextCondLst><p:cond evt=\"onNext\" delay=\"0\"><p:tgtEl><p:sldTgt/></p:tgtEl></p:cond></p:nextCondLst></p:seq>" +
            "</p:childTnLst></p:cTn></p:par></p:tnLst><p:bldLst>" + targets.joinToString("") { "<p:bldP spid=\"${it.first}\" grpId=\"0\" build=\"p\"/>" } + "</p:bldLst></p:timing>"
    }

    private fun slideXml(s: Slide, n: Int, total: Int, unit: List<String>, footer: String, picture: Pair<Int, Int>? = null, nativeChart: Boolean = false): String {
        val sh = Shapes()
        // Text boxes of the content (for the build of the points).
        var bodyFrom = 0
        var bodyTo = 0
        val dark = s.kind == Kind.TITLE || s.kind == Kind.END || s.kind == Kind.SECTION
        when (s.kind) {
            Kind.TITLE -> {
                // „Titelfolie_Blau“ of the CD: white logo top left, subtitle above the title, unit at the bottom.
                picture(sh, "rIdLogoW", c(TudSlide.X), c(TudSlide.LOGO_Y), c(TudSlide.LOGO_H))
                val above = listOf(s.subtitle).filter { it.isNotBlank() } + s.blocks.flatMap { blockLines(it) }.filter { it.isNotBlank() }
                if (above.isNotEmpty()) {
                    val size = if (above.size > 3) 16 else 20
                    text(sh, "Untertitel", c(TudSlide.X), c(TudSlide.SUBTITLE_Y), c(TudSlide.CONTENT_W), c(TudSlide.SUBTITLE_H), above.take(5).map { para(it, size = size, color = "FFFFFF") }, anchor = "b")
                }
                text(
                    sh, "Titel", c(TudSlide.X), c(TudSlide.COVER_TITLE_Y), c(TudSlide.CONTENT_W), c(TudSlide.COVER_TITLE_H),
                    listOf(para(s.title, size = if (s.title.length > 60) 36 else TudSlide.COVER_TITLE_PT, color = "FFFFFF", medium = true)), placeholder = "ctrTitle",
                )
                if (unit.isNotEmpty()) text(sh, "Einrichtung", c(TudSlide.X), c(TudSlide.COVER_FOOT_Y), c(21.76), c(0.71), listOf(para(unit.joinToString(" · "), size = 12, color = "FFFFFF")))
            }
            Kind.SECTION -> {
                // „Abschnittsbeginn_blau“: Brillantblau with a Shape in Blau 2, title and description in white.
                tudShape(sh, c(TudSlide.SHAPE_X), c(TudSlide.SHAPE_Y), c(TudSlide.SHAPE_SIDE), LIGHT)
                text(sh, "Abschnitt", c(TudSlide.X), c(7.9), c(TudSlide.CONTENT_W), c(5.0), listOf(para(s.title, size = 36, color = "FFFFFF", medium = true)), anchor = "b", placeholder = "title")
                if (s.subtitle.isNotBlank()) text(sh, "Beschreibung", c(TudSlide.X), c(13.8), c(TudSlide.CONTENT_W), c(2.43), listOf(para(s.subtitle, size = 20, color = "FFFFFF")))
                chrome(sh, n, footer, dark = true)
            }
            Kind.QUOTE -> {
                // „Zitat_Weiß“: the quote large and left-aligned, the source below it.
                if (s.title.isNotBlank()) title(sh, s.title) else hiddenTitle(sh, "Zitat")
                val q = (s.blocks.first() as Block.Quote).text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val source = q.lastOrNull()?.takeIf { q.size > 1 && (it.startsWith("—") || it.startsWith("–") || it.startsWith("-")) }
                val quote = (if (source != null) q.dropLast(1) else q).joinToString(" ")
                val top = if (s.title.isNotBlank()) TudSlide.BODY_TOP else 2.43
                // The source right below the quote.
                text(
                    sh, "Zitat", c(TudSlide.X), c(top), c(TudSlide.CONTENT_W), c(TudSlide.BODY_BOTTOM - top),
                    listOf(para("„$quote“", size = if (quote.length > 220) 24 else 32, color = INK, italic = true)) +
                        listOfNotNull(source?.let { para(it, size = 14, color = INK, spaceBefore = 14) }),
                )
                chrome(sh, n, footer)
            }
            Kind.CONTENT -> {
                if (s.title.isNotBlank()) title(sh, s.title)
                else hiddenTitle(sh, s.blocks.flatMap { blockLines(it) }.firstOrNull()?.take(60) ?: "Folie $n")
                bodyFrom = sh.texts.size
                val top = c(TudSlide.BODY_TOP)
                val bottom = c(TudSlide.BODY_BOTTOM)
                if (s.right.isEmpty()) content(sh, s.blocks, c(TudSlide.X), top, c(TudSlide.CONTENT_W), bottom - top, half = false)
                else {
                    content(sh, s.blocks, c(TudSlide.X), top, c(COL_W), bottom - top, half = true)
                    content(sh, s.right, c(COL2_X), top, c(COL_W), bottom - top, half = true)
                }
                bodyTo = sh.texts.size
                chrome(sh, n, footer)
            }
            Kind.IMAGE_TEXT -> {
                if (s.title.isNotBlank()) title(sh, s.title) else hiddenTitle(sh, s.imageAlt.ifBlank { "Folie $n" })
                val top = c(TudSlide.BODY_TOP)
                val h = c(TudSlide.BODY_BOTTOM - TudSlide.BODY_TOP)
                val imgX = if (s.imageLeft) c(TudSlide.X) else c(COL2_X)
                val textX = if (s.imageLeft) c(COL2_X) else c(TudSlide.X)
                if (picture != null) image(sh, picture, s.imageAlt, imgX, top, c(COL_W), h)
                content(sh, s.blocks, textX, top, c(COL_W), h, half = true)
                chrome(sh, n, footer)
            }
            Kind.IMAGE -> {
                val withTitle = s.title.isNotBlank()
                if (withTitle) title(sh, s.title) else hiddenTitle(sh, s.imageAlt.ifBlank { "Bild" })
                val caption = s.imageAlt.isNotBlank() && s.imageAlt != s.title
                val top = if (withTitle) c(TudSlide.BODY_TOP) else c(1.12)
                val bottom = if (caption) c(15.4) else c(TudSlide.BODY_BOTTOM)
                if (picture != null) image(sh, picture, s.imageAlt, c(TudSlide.X), top, c(TudSlide.CONTENT_W), bottom - top)
                if (caption) text(sh, "Bildunterschrift", c(TudSlide.X), c(15.5), c(TudSlide.CONTENT_W), c(0.7), listOf(para(s.imageAlt, size = 12, color = GREY)))
                chrome(sh, n, footer)
            }
            Kind.COMPARISON -> {
                if (s.title.isNotBlank()) title(sh, s.title) else hiddenTitle(sh, "Vergleich")
                val top = c(TudSlide.BODY_TOP)
                listOf(Triple(c(TudSlide.X), s.leftHead, s.blocks), Triple(c(COL2_X), s.rightHead, s.right)).forEach { (x, head, blocks) ->
                    var y = top
                    if (head.isNotBlank()) {
                        rect(sh, x, y, c(COL_W), cm(1.3), TINT)
                        text(sh, "Spaltenkopf", x + cm(0.4), y, c(COL_W - 0.8), cm(1.3), listOf(para(head, size = 20, color = BLUE, medium = true)), anchor = "ctr")
                        y += cm(1.7)
                    }
                    content(sh, blocks, x, y, c(COL_W), c(TudSlide.BODY_BOTTOM) - y, half = true)
                }
                chrome(sh, n, footer)
            }
            Kind.TABLE -> {
                if (s.title.isNotBlank()) title(sh, s.title) else hiddenTitle(sh, "Tabelle")
                content(sh, s.blocks, c(TudSlide.X), c(TudSlide.BODY_TOP), c(TudSlide.CONTENT_W), c(TudSlide.BODY_BOTTOM - TudSlide.BODY_TOP), half = false)
                chrome(sh, n, footer)
            }
            Kind.CHART -> {
                if (s.title.isNotBlank()) title(sh, s.title) else hiddenTitle(sh, s.chart?.title?.ifBlank { null } ?: "Diagramm")
                val withText = s.blocks.isNotEmpty()
                val h = c(TudSlide.BODY_BOTTOM - TudSlide.BODY_TOP)
                val w = if (withText) cm(20.0) else c(TudSlide.CONTENT_W)
                if (s.chart != null && nativeChart) chartFrame(sh, OfficeCharts.altText(s.chart), c(TudSlide.X), c(TudSlide.BODY_TOP), w, h)
                else if (s.chart != null) chart(sh, s.chart, c(TudSlide.X), c(TudSlide.BODY_TOP), w, h)
                if (withText) content(sh, s.blocks, c(22.37), c(TudSlide.BODY_TOP), c(9.8), h, half = true)
                chrome(sh, n, footer)
            }
            Kind.AGENDA -> {
                title(sh, s.title.ifBlank { "Agenda" })
                val size = if (s.agenda.size > 7) 20 else 24
                text(sh, "Agenda", c(TudSlide.X), c(TudSlide.BODY_TOP), c(TudSlide.CONTENT_W), c(TudSlide.BODY_BOTTOM - TudSlide.BODY_TOP), s.agenda.map { para(it, size = size, bullet = "num", spaceBefore = 10) })
                chrome(sh, n, footer)
            }
            Kind.END -> {
                picture(sh, "rIdLogoW", c(TudSlide.X), c(TudSlide.LOGO_Y), c(TudSlide.LOGO_H))
                tudShape(sh, c(TudSlide.SHAPE_X), c(TudSlide.SHAPE_Y), c(TudSlide.SHAPE_SIDE), LIGHT)
                text(sh, "Titel", c(TudSlide.X), c(4.82), c(TudSlide.CONTENT_W), c(4.5), listOf(para(s.title.ifBlank { "Vielen Dank!" }, size = TudSlide.COVER_TITLE_PT, color = "FFFFFF", medium = true)), anchor = "b", placeholder = "title")
                val lines = s.blocks.flatMap { blockLines(it) }.filter { it.isNotBlank() }
                if (lines.isNotEmpty()) text(sh, "Kontakt", c(TudSlide.X), c(TudSlide.COVER_TITLE_Y), c(TudSlide.CONTENT_W), c(TudSlide.COVER_TITLE_H), lines.map { para(it, size = 20, color = "FFFFFF", spaceBefore = 4) })
            }
        }
        val bg = if (dark) "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"$BLUE\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>" else ""
        val build = if (s.build) buildXml(sh.texts.subList(bodyFrom, bodyTo).filter { it.second > 0 }) else ""
        return "$XML<p:sld $NS><p:cSld>$bg<p:spTree>$GROUP${sh.xml}</p:spTree></p:cSld>" +
            "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>" + transitionXml(s.transition) + build + "</p:sld>"
    }

    /** Footer of the CD: the short logo „TUD“ at the bottom left, then „Titel • Vortragende • Seite N“. */
    private fun chrome(sh: Shapes, n: Int, footer: String, dark: Boolean = false) {
        // The short logo repeats on every slide: decorative for screen readers.
        picture(sh, if (dark) "rIdLogoKW" else "rIdLogoK", c(TudSlide.X), c(TudSlide.FOOT_Y), c(TudSlide.FOOT_LOGO_H), TudBrand.LOGO_SHORT_RATIO, decorative = true)
        text(
            sh, "Fußzeile", c(TudSlide.FOOT_TEXT_X), c(TudSlide.FOOT_Y - 0.05), c(TudSlide.FOOT_TEXT_W), c(0.6),
            listOf(para(TudSlide.footer(footer, n), size = TudSlide.FOOT_PT, color = if (dark) "FFFFFF" else BLUE)), anchor = "ctr",
        )
    }

    private fun title(sh: Shapes, title: String) {
        text(
            sh, "Titel", c(TudSlide.X), c(TudSlide.TITLE_Y), c(TudSlide.CONTENT_W), c(TudSlide.TITLE_H),
            listOf(para(title, size = if (title.length > 55) TudSlide.TITLE_LONG_PT else TudSlide.TITLE_PT, color = INK, medium = true)), placeholder = "title",
        )
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
        is Block.Heading -> listOf(para(b.text, size = size, color = BLUE, medium = true, spaceBefore = 10))
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
        medium: Boolean = false,
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
                "<a:latin typeface=\"${if (mono || seg.code) "Consolas" else if (medium) HEAD_FONT else FONT}\"/><a:cs typeface=\"${if (mono || seg.code) "Consolas" else if (medium) HEAD_FONT else FONT}\"/>" +
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
        sh.texts += id to paragraphs.size
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

    /** The Shape of the CD (both components) as PowerPoint's own geometry, decorative for screen readers. */
    private fun tudShape(sh: Shapes, x: Long, y: Long, side: Long, color: String) {
        val id = sh.next()
        val paths = TudBrand.SHAPE.joinToString("") { poly ->
            "<a:path w=\"600\" h=\"600\"><a:moveTo><a:pt x=\"${poly[0].toInt()}\" y=\"${poly[1].toInt()}\"/></a:moveTo>" +
                (2 until poly.size step 2).joinToString("") { i -> "<a:lnTo><a:pt x=\"${poly[i].toInt()}\" y=\"${poly[i + 1].toInt()}\"/></a:lnTo>" } +
                "<a:close/></a:path>"
        }
        sh.xml.append(
            "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Shape $id\">$DECORATIVE</p:cNvPr><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$side\" cy=\"$side\"/></a:xfrm>" +
                "<a:custGeom><a:avLst/><a:gdLst/><a:ahLst/><a:cxnLst/><a:rect l=\"0\" t=\"0\" r=\"r\" b=\"b\"/><a:pathLst>$paths</a:pathLst></a:custGeom>" +
                "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>"
        )
    }

    /** Logo with the aspect ratio of the bundled image (1190 × 400 px, the short logo [TudBrand.LOGO_SHORT_RATIO]). */
    private fun picture(sh: Shapes, rel: String, x: Long, y: Long, h: Long, ratio: Double = 1190.0 / 400.0, decorative: Boolean = false) {
        val id = sh.next()
        val w = (h * ratio).toLong()
        sh.xml.append(
            "<p:pic><p:nvPicPr>" +
                (if (decorative) "<p:cNvPr id=\"$id\" name=\"Logo $id\" descr=\"TUD\">$DECORATIVE</p:cNvPr>" else "<p:cNvPr id=\"$id\" name=\"Logo $id\" descr=\"Technische Universität Dresden\"/>") +
                "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                "<p:blipFill><a:blip r:embed=\"$rel\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"
        )
    }

    /** A picture of the slide, as large as fits into the area, centred, with its description as alternative text. */
    private fun image(sh: Shapes, size: Pair<Int, Int>, alt: String, x: Long, y: Long, w: Long, h: Long) {
        val (pw, ph) = size
        var cw = w
        var ch = w * ph / pw
        if (ch > h) { ch = h; cw = h * pw / ph }
        val id = sh.next()
        sh.xml.append(
            "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Bild $id\" descr=\"${esc(alt.ifBlank { "Bild" })}\"/>" +
                "<p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                "<p:blipFill><a:blip r:embed=\"rIdImg\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                "<p:spPr><a:xfrm><a:off x=\"${x + (w - cw) / 2}\" y=\"${y + (h - ch) / 2}\"/><a:ext cx=\"$cw\" cy=\"$ch\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"
        )
    }

    /** Fill in DrawingML: colour (#RRGGBB) with opacity. */
    private fun fillOf(color: String, opacity: Float = 1f) = "<a:solidFill><a:srgbClr val=\"${color.removePrefix("#").uppercase()}\">" +
        (if (opacity < 1f) "<a:alpha val=\"${(opacity * 100000).toInt()}\"/>" else "") + "</a:srgbClr></a:solidFill>"

    private fun shape(sh: Shapes, name: String, x: Long, y: Long, w: Long, h: Long, geom: String, fill: String, line: String = "<a:ln><a:noFill/></a:ln>") {
        val id = sh.next()
        sh.xml.append(
            "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name $id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"${w.coerceAtLeast(1)}\" cy=\"${h.coerceAtLeast(1)}\"/></a:xfrm>$geom$fill$line</p:spPr></p:sp>"
        )
    }

    private fun line(sh: Shapes, x1: Long, y1: Long, x2: Long, y2: Long, color: String, width: Long) {
        val id = sh.next()
        val flip = (if (x2 < x1) " flipH=\"1\"" else "") + (if (y2 < y1) " flipV=\"1\"" else "")
        sh.xml.append(
            "<p:cxnSp><p:nvCxnSpPr><p:cNvPr id=\"$id\" name=\"Linie $id\"/><p:cNvCxnSpPr/><p:nvPr/></p:nvCxnSpPr>" +
                "<p:spPr><a:xfrm$flip><a:off x=\"${minOf(x1, x2)}\" y=\"${minOf(y1, y2)}\"/><a:ext cx=\"${kotlin.math.abs(x2 - x1)}\" cy=\"${kotlin.math.abs(y2 - y1)}\"/></a:xfrm>" +
                "<a:prstGeom prst=\"line\"><a:avLst/></a:prstGeom><a:ln w=\"$width\">${fillOf(color)}</a:ln></p:spPr></p:cxnSp>"
        )
    }

    /** The frame of a real PowerPoint chart (part `rIdChart` of the slide). */
    private fun chartFrame(sh: Shapes, alt: String, x: Long, y: Long, w: Long, h: Long) {
        val id = sh.next()
        sh.xml.append(
            "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"$id\" name=\"Diagramm $id\" descr=\"${esc(alt)}\"/>" +
                "<p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>" +
                "<p:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></p:xfrm>" + OfficeCharts.graphic("rIdChart") + "</p:graphicFrame>"
        )
    }

    /** A chart of the slide drawn with shapes from [ChartLayout] – the same drawing as in the editor and the PDF. */
    private fun chart(sh: Shapes, spec: ChartSpec, x: Long, y: Long, w: Long, h: Long) {
        val scale = minOf(w / ChartLayout.W.toDouble(), h / ChartLayout.H.toDouble())
        val ox = x + ((w - ChartLayout.W * scale) / 2).toLong()
        val oy = y + ((h - ChartLayout.H * scale) / 2).toLong()
        fun px(v: Float) = ox + (v * scale).toLong()
        fun py(v: Float) = oy + (v * scale).toLong()
        fun len(v: Float) = (v * scale).toLong()
        val rect = "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>"
        for (s in ChartLayout.layout(spec)) when (s) {
            is ChartLayout.Rect -> shape(sh, "Balken", px(s.x), py(s.y), len(s.w), len(s.h), rect, fillOf(s.color, s.opacity))
            is ChartLayout.Line -> line(sh, px(s.x1), py(s.y1), px(s.x2), py(s.y2), s.color, (s.width * scale).toLong().coerceAtLeast(3175))
            is ChartLayout.Poly -> s.points.zipWithNext().forEach { (a, b) -> line(sh, px(a.first), py(a.second), px(b.first), py(b.second), s.color, (s.width * scale).toLong()) }
            is ChartLayout.Dot -> shape(sh, "Punkt", px(s.x - s.r), py(s.y - s.r), len(2 * s.r), len(2 * s.r), "<a:prstGeom prst=\"ellipse\"><a:avLst/></a:prstGeom>", fillOf(s.color))
            is ChartLayout.Wedge -> {
                val geom = if (s.sweep >= 359.9f) "<a:prstGeom prst=\"ellipse\"><a:avLst/></a:prstGeom>" else {
                    fun angle(a: Float) = (((a % 360f) + 360f) % 360f * 60000).toLong()
                    "<a:prstGeom prst=\"pie\"><a:avLst><a:gd name=\"adj1\" fmla=\"val ${angle(s.start)}\"/><a:gd name=\"adj2\" fmla=\"val ${angle(s.start + s.sweep)}\"/></a:avLst></a:prstGeom>"
                }
                shape(sh, "Segment", px(s.cx - s.r), py(s.cy - s.r), len(2 * s.r), len(2 * s.r), geom, fillOf(s.color), "<a:ln w=\"12700\"><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill></a:ln>")
            }
            is ChartLayout.Label -> {
                val size = (s.size * scale / 12700).coerceIn(6.0, 40.0)
                val boxW = len((s.text.length * s.size * 0.62f + 8f).coerceAtMost(ChartLayout.W))
                val boxH = len(s.size * 1.5f)
                val align = when (s.anchor) { "start" -> "l"; "end" -> "r"; else -> "ctr" }
                val left = when (s.anchor) { "start" -> px(s.x); "end" -> px(s.x) - boxW; else -> px(s.x) - boxW / 2 }
                val id = sh.next()
                val rot = if (s.vertical) " rot=\"-5400000\"" else ""
                // Rotated labels turn around their centre: the box is centred on the anchor point.
                val bx = if (s.vertical) px(s.x) - boxW / 2 else left
                val by = if (s.vertical) py(s.y) - boxH / 2 else py(s.y) - (boxH * 0.75).toLong()
                sh.xml.append(
                    "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Beschriftung $id\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
                        "<p:spPr><a:xfrm$rot><a:off x=\"$bx\" y=\"$by\"/><a:ext cx=\"$boxW\" cy=\"$boxH\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>" +
                        "<p:txBody><a:bodyPr wrap=\"none\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" anchor=\"ctr\"/><a:lstStyle/>" +
                        "<a:p><a:pPr algn=\"$align\"/><a:r><a:rPr lang=\"de-DE\" sz=\"${(size * 100).toInt()}\"" + (if (s.bold) " b=\"1\"" else "") + " dirty=\"0\">" +
                        fillOf(s.color) + "<a:latin typeface=\"$FONT\"/></a:rPr><a:t>${esc(s.text)}</a:t></a:r></a:p></p:txBody></p:sp>"
                )
            }
        }
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

    private fun contentTypes(slides: List<Slide>, imageTypes: Set<String> = emptySet(), charts: List<Int> = emptyList()) = buildString {
        append(XML).append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        (imageTypes - "png").forEach { append("<Default Extension=\"$it\" ContentType=\"image/$it\"/>") }
        if (charts.isNotEmpty()) append("<Default Extension=\"xlsx\" ContentType=\"${OfficeCharts.XLSX_TYPE}\"/>")
        charts.forEach { append("<Override PartName=\"/ppt/charts/chart$it.xml\" ContentType=\"${OfficeCharts.CONTENT_TYPE}\"/>") }
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
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${esc(title)}</dc:title><dc:language>de-DE</dc:language><dc:creator>Lora</dc:creator>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified>" +
            "</cp:coreProperties>"
    }

    private fun app(count: Int) = XML + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
        "<Application>Lora</Application><PresentationFormat>Breitbild</PresentationFormat><Slides>$count</Slides></Properties>"

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

    private fun slideRels(n: Int, notes: Boolean, image: String? = null, chart: Boolean = false) = XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        (if (image != null) "<Relationship Id=\"rIdImg\" Type=\"$REL/image\" Target=\"../media/$image\"/>" else "") +
        (if (chart) "<Relationship Id=\"rIdChart\" Type=\"${OfficeCharts.REL_CHART}\" Target=\"../charts/chart$n.xml\"/>" else "") +
        "<Relationship Id=\"rIdLayout\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>" +
        "<Relationship Id=\"rIdLogo\" Type=\"$REL/image\" Target=\"../media/logo-blau.png\"/>" +
        "<Relationship Id=\"rIdLogoW\" Type=\"$REL/image\" Target=\"../media/logo-weiss.png\"/>" +
        "<Relationship Id=\"rIdLogoK\" Type=\"$REL/image\" Target=\"../media/logo-kurz-blau.png\"/>" +
        "<Relationship Id=\"rIdLogoKW\" Type=\"$REL/image\" Target=\"../media/logo-kurz-weiss.png\"/>" +
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

    /** Office theme of the CD: colours „TUD_2025_Blau-Türkis“, Noto Sans Medium for headings and Noto Sans for text. */
    private fun theme(name: String) = XML + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"$name\"><a:themeElements>" +
        TudBrand.OFFICE_COLOR_SCHEME + TudBrand.OFFICE_FONT_SCHEME +
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
