package io.github.thepytales.llmhub.docs

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Opens Word, PowerPoint and Excel files in the editor: .docx keeps headings, lists, tables, bold and italic;
 * .pptx becomes slides with titles, bullet points, tables and speaker notes; .xlsx one tab per sheet, formulas
 * included. Text and Markdown files are taken as they are.
 */
object OfficeImport {
    /** [media]: pictures of the file by the path the Markdown uses ("Bilder/…"), to be saved next to the document. */
    data class Imported(val kind: DocKind, val title: String, val tabs: List<Pair<String, String>>, val media: Map<String, ByteArray> = emptyMap())

    private val TEXT = setOf("md", "markdown", "txt", "text", "html", "htm", "rtf", "tex", "csv", "tsv")

    fun canOpen(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in TEXT + setOf("docx", "pptx", "xlsx", "xlsm", "odt", "ods", "odp")

    /** Reads [bytes] of the file [name]; null if the format is not supported. */
    fun read(name: String, bytes: ByteArray): Imported? {
        val ext = name.substringAfterLast('.', "").lowercase()
        val base = name.substringBeforeLast('.').ifBlank { name }
        return when (ext) {
            "docx" -> {
                val media = LinkedHashMap<String, ByteArray>()
                val md = docx(bytes, media)
                Imported(DocKind.DOC, DocBlocks.titleOf(md) ?: base, listOf("Tab 1" to md), media)
            }
            "pptx" -> {
                val media = LinkedHashMap<String, ByteArray>()
                Imported(DocKind.SLIDES, base, listOf("Folien" to pptx(bytes, media)), media)
            }
            "xlsx", "xlsm" -> Imported(DocKind.SHEET, base, xlsx(bytes).ifEmpty { listOf("Tabelle 1" to SheetEngine.blank()) })
            // OpenDocument as a source (AP-90).
            "odt" -> OdfImport.odt(bytes).let { md -> Imported(DocKind.DOC, DocBlocks.titleOf(md) ?: base, listOf("Tab 1" to md)) }
            "ods" -> Imported(DocKind.SHEET, base, OdfImport.ods(bytes).ifEmpty { listOf("Tabelle 1" to SheetEngine.blank()) })
            "odp" -> Imported(DocKind.SLIDES, base, listOf("Folien" to OdfImport.odp(bytes)))
            // CSV-Lotse (AP-93): encoding, separator, numbers and dates are recognised.
            "csv", "tsv" -> Imported(DocKind.SHEET, base, listOf("Tabelle 1" to CsvPilot.read(bytes, name)))
            in TEXT -> {
                val text = bytes.toString(Charsets.UTF_8).removePrefix("﻿")
                val md = if (ext == "html" || ext == "htm") htmlToText(text) else text
                Imported(if (ext == "md" || ext == "markdown") DocKind.MARKDOWN else DocKind.DOC, DocBlocks.titleOf(md) ?: base, listOf("Tab 1" to md))
            }
            else -> null
        }
    }

    /** Creates a living document from [imported]. */
    fun toLiveDoc(imported: Imported, author: String = "Sie", chatId: String? = null): LiveDoc {
        val doc = LiveDoc.create(imported.title, imported.tabs.first().second, author, chatId, kind = imported.kind)
        val tabs = imported.tabs.mapIndexed { i, (t, md) -> if (i == 0) doc.tabs[0].copy(title = t, markdown = md) else DocTab(title = t, markdown = md) }
        return doc.copy(tabs = tabs, versions = listOf(doc.versions.first().copy(note = "Importiert", tabs = tabs)))
    }

    // ── Zip and XML helpers ──

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels"))) out[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return out
    }

    /** Like [entries], plus the pictures in word/media and ppt/media. */
    private fun entriesWithMedia(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val media = e.name.contains("/media/") && e.size <= 30L * 1024 * 1024
                if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels") || media)) out[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return out
    }

    /** Relationship id → target of a part's .rels (targets resolved against [dir], e.g. "word/"). */
    private fun rels(files: Map<String, ByteArray>, relsPath: String, dir: String): Map<String, String> =
        xml(files[relsPath])?.children()?.associate { r ->
            val t = r.attr("Target")
            r.attr("Id") to normalize(if (t.startsWith("/")) t.removePrefix("/") else dir + t)
        }.orEmpty()

    /** "ppt/slides/../media/x.png" → "ppt/media/x.png". */
    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        path.split('/').forEach { p -> when (p) { ".." -> out.removeLastOrNull(); ".", "" -> {}; else -> out.addLast(p) } }
        return out.joinToString("/")
    }

    /** Keeps a picture of the file for the document; returns its Markdown path (null if it is no supported picture). */
    private fun keepImage(bytes: ByteArray?, media: MutableMap<String, ByteArray>): String? {
        bytes ?: return null
        val type = DocMarkup.imageType(bytes) ?: return null
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val path = "${DocMarkup.IMAGE_DIR}/bild-$hash.${if (type == "jpeg") "jpg" else type}"
        media[path] = bytes
        return path
    }

    private fun xml(bytes: ByteArray?): Element? = bytes?.let {
        runCatching {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { f.isExpandEntityReferences = false }
            f.newDocumentBuilder().parse(ByteArrayInputStream(it)).documentElement
        }.getOrNull()
    }

    private fun Node.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.child(local: String): Element? = children().firstOrNull { it.localName == local }

    private fun Element.all(local: String): List<Element> {
        val list = getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.attr(local: String): String = (0 until attributes.length).map { attributes.item(it) }
        .firstOrNull { it.localName == local || it.nodeName == local }?.nodeValue.orEmpty()

    // ── Word ──

    /**
     * Word as Markdown; footnotes become [^n] with their text at the end, pictures lines "![…](Bilder/…)" whose files
     * go into [media].
     */
    fun docx(bytes: ByteArray, media: MutableMap<String, ByteArray> = LinkedHashMap()): String {
        val files = entriesWithMedia(bytes)
        val body = xml(files["word/document.xml"])?.all("body")?.firstOrNull() ?: return ""
        val docRels = rels(files, "word/_rels/document.xml.rels", "word/")
        // Footnotes (without the separators) in the order they are used.
        val noteTexts = xml(files["word/footnotes.xml"])?.children()?.filter { it.localName == "footnote" && it.attr("type").isEmpty() }
            ?.associate { n -> n.attr("id") to n.children().filter { it.localName == "p" }.joinToString(" ") { runs(it).trim() }.replace('\t', ' ').trim() }.orEmpty()
        val noteNumbers = LinkedHashMap<String, Int>()
        val allRuns = body.all("rPr")
        val usualSize = allRuns.mapNotNull { it.child("sz")?.attr("val")?.ifBlank { null } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val usualFont = allRuns.mapNotNull { it.child("rFonts")?.attr("ascii")?.ifBlank { null } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val ctx = DocxRefs(noteTexts, noteNumbers, docRels, files, media, usualSize, usualFont)
        val bullets = numberingFormats(xml(files["word/numbering.xml"]))
        val styles = styleNames(xml(files["word/styles.xml"]))
        val blocks = mutableListOf<String>()
        val counters = HashMap<String, Int>()
        // Abschnitte (AP-43/45): a paragraph with w:sectPr ends a section; the next one may have its own header/footer.
        val elements = flatBody(body)
        val sects = elements.filter { it.localName == "p" }.mapNotNull { it.child("pPr")?.child("sectPr") } +
            listOfNotNull(body.children().lastOrNull { it.localName == "sectPr" })
        val own = isOwn(files)
        var ended = 0
        var pending = false
        for (el in elements) {
            if (pending) {
                pending = false
                sectionBreak(sects.getOrNull(++ended), files, docRels, own)?.let { if (blocks.isNotEmpty()) blocks += it }
            }
            when (el.localName) {
                "p" -> {
                    if (el.child("pPr")?.child("sectPr") != null) pending = true
                    // Pictures of the paragraph become blocks of their own.
                    pictures(el, ctx).forEach { blocks += it }
                    // Textfelder (AP-45, read only): their paragraphs after the one they are anchored in.
                    val boxes = textBoxes(el, ctx)
                    if (el.all("br").any { it.attr("type") == "page" } && blocks.isNotEmpty()) blocks += DocMarkup.PAGE_BREAK
                    val text = runs(el, ctx).trim()
                    val pPr = el.child("pPr")
                    val styleId = pPr?.child("pStyle")?.attr("val").orEmpty()
                    val style = (styles[styleId] ?: styleId).lowercase().replace(" ", "")
                    val num = pPr?.child("numPr")
                    // An Inhaltsverzeichnis (AP-16) comes back as its marker line, not as a copy of the headings.
                    if (Regex("^(toc|verzeichnis)\\d").containsMatchIn(style) || styleId.lowercase().startsWith("toc")) {
                        if (blocks.lastOrNull() != DocMarkup.TOC) {
                            if (blocks.lastOrNull()?.replace("*", "")?.trim()?.lowercase() in setOf("inhaltsverzeichnis", "inhalt", "contents", "table of contents")) blocks.removeAt(blocks.lastIndex)
                            blocks += DocMarkup.TOC
                        }
                        continue
                    }
                    if (text.isEmpty()) { blocks += boxes; continue }
                    // Bullets typed as characters ("• Punkt") are list items too.
                    val typed = Regex("^[•◦▪–·]\\s+").find(text)
                    if (typed != null && num == null) {
                        val line = "- " + text.substring(typed.range.last + 1)
                        if (blocks.isNotEmpty() && DocBlocks.kindOf(blocks.last()) == DocBlocks.Kind.BULLETS) blocks[blocks.lastIndex] = blocks.last() + "\n" + line
                        else blocks += line
                        continue
                    }
                    val heading = Regex("(heading|überschrift|berschrift)(\\d)").find(style)?.groupValues?.get(2)?.toIntOrNull()
                    // Formatvorlagen of the Fließtext-Editor (AP-46) and the paragraph settings it knows.
                    val kind = when {
                        style == "subtitle" || style == "untertitel" -> io.github.thepytales.llmhub.docs.text.ParaKind.SUBTITLE
                        style == "caption" || style.startsWith("beschriftung") -> io.github.thepytales.llmhub.docs.text.ParaKind.CAPTION
                        style == "bibliography" || style.startsWith("literatur") -> io.github.thepytales.llmhub.docs.text.ParaKind.LITERATURE
                        style.contains("code") || style == "htmlpreformatted" || style == "htmlvorformatiert" -> io.github.thepytales.llmhub.docs.text.ParaKind.CODE
                        else -> io.github.thepytales.llmhub.docs.text.ParaKind.NORMAL
                    }
                    when {
                        style == "title" || style == "titel" -> blocks += "# " + text.replace("**", "")
                        heading != null -> blocks += "#".repeat((heading + 1).coerceAtMost(6)) + " " + text.replace("**", "")
                        num != null || style.startsWith("listparagraph") || style.startsWith("listenabsatz") -> {
                            val numId = num?.child("numId")?.attr("val").orEmpty()
                            val level = num?.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0
                            val bullet = bullets["$numId:$level"] ?: true
                            val mark = if (bullet) "-" else "${counters.merge("$numId:$level", 1, Int::plus)}."
                            val line = "  ".repeat(level) + "$mark $text"
                            if (blocks.isNotEmpty() && DocBlocks.kindOf(blocks.last()).let { it == DocBlocks.Kind.BULLETS || it == DocBlocks.Kind.NUMBERS }) {
                                blocks[blocks.lastIndex] = blocks.last() + "\n" + line
                            } else blocks += line
                        }
                        style.contains("quote") || style.contains("zitat") -> blocks += "> $text"
                        // Word-Treue (AP-45): alignment, line spacing and indents of the paragraph stay.
                        else -> blocks += paraFormat(pPr, kind).marker() + text
                    }
                    blocks += boxes
                }
                "tbl" -> {
                    val rows = el.children().filter { it.localName == "tr" }.map { tr ->
                        tr.children().filter { it.localName == "tc" }.map { tc ->
                            tc.children().filter { it.localName == "p" }.joinToString(" ") { runs(it).trim() }.replace('\t', ' ').trim()
                                .let { cell -> if (cell.length > 4 && cell.startsWith("**") && cell.endsWith("**")) cell.substring(2, cell.length - 2) else cell }
                        }
                    }.filter { it.isNotEmpty() }
                    if (rows.isNotEmpty()) blocks += DocBlocks.table(rows)
                }
            }
        }
        noteNumbers.forEach { (id, n) -> blocks += "[^$n]: " + noteTexts[id].orEmpty().ifBlank { " " } }
        // A document without a heading-styled title starts with its first short line as title.
        val md = DocBlocks.join(fixTitle(blocks))
        // Kopf-/Fußzeile and margins of the first section (AP-45; the only one in most files) as the page setup.
        val setup = sects.firstOrNull()?.let { pageSetup(it, files, docRels) } ?: return md
        return DocMarkup.withPageSetup(md, setup)
    }

    /** The body's paragraphs and tables, with content controls (w:sdt, used a lot in templates) opened up. */
    private fun flatBody(container: Element): List<Element> = container.children().flatMap { e ->
        when (e.localName) {
            "sdt" -> e.child("sdtContent")?.let { flatBody(it) }.orEmpty()
            "customXml", "smartTag" -> flatBody(e)
            else -> listOf(e)
        }
    }

    /** Whether [e] lies inside the fallback of an mc:AlternateContent (the same content once more, for old Word). */
    private fun inFallback(e: Element): Boolean {
        var n: Node? = e.parentNode
        while (n != null) {
            if (n.localName == "Fallback") return true
            n = n.parentNode
        }
        return false
    }

    /** The text of the Textfelder anchored in paragraph [p], one block per box (marked, so the writer leaves them). */
    private fun textBoxes(p: Element, ctx: DocxRefs): List<String> = p.all("txbxContent").filter { !inFallback(it) }.mapNotNull { box ->
        val lines = box.all("p").map { runs(it, ctx).trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) null else DocMarkup.TEXTBOX + "\n" + lines.joinToString("  \n")
    }

    /** Alignment, line spacing (from 1,4 lines on) and indents of a Word paragraph. */
    private fun paraFormat(pPr: Element?, kind: io.github.thepytales.llmhub.docs.text.ParaKind): io.github.thepytales.llmhub.docs.text.ParaFormat {
        val align = when (pPr?.child("jc")?.attr("val")) {
            "center" -> io.github.thepytales.llmhub.docs.text.Align.CENTER
            "right", "end" -> io.github.thepytales.llmhub.docs.text.Align.RIGHT
            "both", "distribute" -> io.github.thepytales.llmhub.docs.text.Align.JUSTIFY
            else -> io.github.thepytales.llmhub.docs.text.Align.LEFT
        }
        val sp = pPr?.child("spacing")
        val spacing = sp?.takeIf { it.attr("lineRule").let { r -> r.isEmpty() || r == "auto" } }?.attr("line")?.toIntOrNull()
            ?.let { (it / 240f * 20).toInt() / 20f }?.takeIf { it >= 1.4f && it <= 3f }
        val ind = pPr?.child("ind")
        fun mm(v: String) = v.toIntOrNull()?.let { (it / 56.693f).toInt() } ?: 0
        val left = mm(ind?.attr("left")?.ifBlank { ind.attr("start") }.orEmpty()).coerceIn(0, 150)
        val first = (mm(ind?.attr("firstLine").orEmpty()) - mm(ind?.attr("hanging").orEmpty())).coerceIn(-80, 80)
        // Tabstopps (AP-44): left, centred and right ones (decimal ones as left, bar and cleared ones not).
        val tabs = pPr?.child("tabs")?.children()?.filter { it.localName == "tab" }?.mapNotNull { t ->
            val kind = when (t.attr("val")) {
                "left", "start", "decimal", "num" -> io.github.thepytales.llmhub.docs.text.TabKind.LEFT
                "center" -> io.github.thepytales.llmhub.docs.text.TabKind.CENTER
                "right", "end" -> io.github.thepytales.llmhub.docs.text.TabKind.RIGHT
                else -> return@mapNotNull null
            }
            t.attr("pos").toIntOrNull()?.takeIf { it >= 0 }?.let { io.github.thepytales.llmhub.docs.text.TabStop(kotlin.math.round(it / 56.693f).toInt().coerceIn(0, 400), kind) }
        }?.distinctBy { it.mm }?.sortedBy { it.mm }.orEmpty()
        return io.github.thepytales.llmhub.docs.text.ParaFormat(kind = kind, align = align, spacing = spacing, left = if (left >= 2) left else 0, first = if (kotlin.math.abs(first) >= 2) first else 0, tabs = tabs)
    }

    /** Lora's own Word files (their header and footer come from the page setup, with date and page numbers). */
    private fun isOwn(files: Map<String, ByteArray>) = files["docProps/core.xml"]?.toString(Charsets.UTF_8)?.contains("<dc:creator>Lora</dc:creator>") == true

    /**
     * The marker of a section break before section [next]: its own header and footer when it has references to them
     * (else it takes over those before), null for a continuous section without them (no new page, nothing to keep).
     */
    private fun sectionBreak(next: Element?, files: Map<String, ByteArray>, rels: Map<String, String>, own: Boolean): String? {
        next ?: return null
        val hasHeader = next.children().any { it.localName == "headerReference" }
        val hasFooter = next.children().any { it.localName == "footerReference" }
        val continuous = next.child("type")?.attr("val") == "continuous"
        if (continuous && !hasHeader && !hasFooter) return null
        // Lora's own files: header and footer parts also hold date, footer line and page numbers – not read back.
        val header = if (hasHeader && !own) headerText(next, "headerReference", "default", files, rels) else null
        val footer = if (hasFooter && !own) headerText(next, "footerReference", "default", files, rels) else null
        return DocMarkup.sectionMarker(DocMarkup.Section(header, footer))
    }

    /** The text of the header or footer part of [sect] ([kind] reference of [type]), without page numbers. */
    private fun headerText(sect: Element, kind: String, type: String, files: Map<String, ByteArray>, rels: Map<String, String>): String {
        val refs = sect.children().filter { it.localName == kind }
        val ref = refs.firstOrNull { it.attr("type") == type } ?: (if (type == "default") refs.firstOrNull { it.attr("type").isBlank() } ?: refs.firstOrNull() else null) ?: return ""
        val root = xml(files[rels[ref.attr("id")]]) ?: return ""
        return root.all("p").filter { !inFallback(it) }.joinToString(" · ") { pp ->
            buildString { pp.all("t").filter { t -> t.parentNode?.localName == "r" }.forEach { append(it.textContent) } }.trim()
        }.split(" · ").filter { it.isNotBlank() }.joinToString(" · ")
            // „Seite 1 von 3“ is the page number of the export, not text.
            .replace(Regex("(?i)\\b(seite|page)\\b\\s*\\d+\\s*((von|of|/)\\s*\\d+)?|^\\s*\\d+\\s*(von|of|/)\\s*\\d+\\s*$"), "").replace(Regex("^\\s*·\\s*|\\s*·\\s*$"), "").trim()
            .take(160)
    }

    /** Header, footer and margins of section [sect]; null if it has none worth keeping. */
    private fun pageSetup(sect: Element, files: Map<String, ByteArray>, rels: Map<String, String>): DocMarkup.PageSetup? {
        fun part(kind: String, type: String = "default"): String = headerText(sect, kind, type, files, rels)
        // Lora's own files carry date, footer line and page numbers there from the page setup and the settings: not again.
        val own = isOwn(files)
        val header = if (own) "" else part("headerReference")
        val footer = if (own) "" else part("footerReference")
        val mar = sect.child("pgMar")?.attr("left")?.toIntOrNull()?.let { (it / 56.693f).roundToIntSafe() }
        val margins = mar?.let { DocMarkup.marginsFor(it.coerceIn(5, 60)) } ?: "normal"
        // Erste Seite anders (w:titlePg) and gerade Seiten anders (w:evenAndOddHeaders in the settings), AP-45.
        val titlePg = sect.child("titlePg")?.let { it.attr("val") !in setOf("0", "false", "off") } == true
        val evenOdd = files["word/settings.xml"]?.toString(Charsets.UTF_8)?.let { Regex("<w:evenAndOddHeaders(?![^>]*w:val=\"(0|false|off)\")").containsMatchIn(it) } == true
        val setup = DocMarkup.PageSetup(
            header = header, footer = footer, margins = margins,
            titlePage = titlePg,
            headerFirst = if (titlePg && !own) part("headerReference", "first") else "",
            footerFirst = if (titlePg && !own) part("footerReference", "first") else "",
            headerEven = if (evenOdd && !own) part("headerReference", "even") else "",
            footerEven = if (evenOdd && !own) part("footerReference", "even") else "",
        )
        return setup.takeIf { it != DocMarkup.PageSetup() }
    }

    private fun Float.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

    /** What reading the runs of a .docx needs to resolve footnotes and pictures. */
    private class DocxRefs(
        val notes: Map<String, String>,
        val numbers: MutableMap<String, Int>,
        val rels: Map<String, String>,
        val files: Map<String, ByteArray>,
        val media: MutableMap<String, ByteArray>,
        /** Word-Treue (AP-45): the usual size (half points) and font of the body; only different ones are kept. */
        val usualSize: String? = null,
        val usualFont: String? = null,
    )

    /** Pictures (w:drawing with a:blip) of a paragraph as Markdown image lines; the caption is the alternative text. */
    private fun pictures(p: Element, ctx: DocxRefs): List<String> = p.all("drawing").mapNotNull { d ->
        // A Word chart (AP-49) comes back as ```chart block with the values Word cached.
        d.all("chart").firstOrNull { it.namespaceURI == OfficeCharts.NS_C }?.let { c ->
            val rid = (0 until c.attributes.length).map { c.attributes.item(it) }.firstOrNull { it.localName == "id" }?.nodeValue
            val spec = OfficeCharts.read(ctx.files[ctx.rels[rid]])?.spec()
            return@mapNotNull spec?.let { "```chart\n" + it.toJson() + "\n```" }
        }
        val embed = d.all("blip").firstOrNull()?.attributes?.let { a -> (0 until a.length).map { a.item(it) }.firstOrNull { it.localName == "embed" }?.nodeValue } ?: return@mapNotNull null
        val path = keepImage(ctx.files[ctx.rels[embed]], ctx.media) ?: return@mapNotNull null
        val alt = d.all("docPr").firstOrNull()?.let { it.attr("descr").ifBlank { it.attr("title") } }.orEmpty()
        DocMarkup.Image(alt.replace(Regex("\\s+"), " ").trim(), path).markdown()
    }

    private fun fixTitle(blocks: List<String>): List<String> {
        if (blocks.any { it.startsWith("# ") }) return blocks
        // Without a "Title" paragraph, "Heading 1" is the top level.
        if (blocks.any { it.startsWith("## ") }) return blocks.map { if (it.startsWith("##")) it.removePrefix("#") else it }
        val first = blocks.firstOrNull() ?: return blocks
        return if (first.length in 3..90 && '\n' !in first && !first.startsWith("#") && !first.startsWith("|") && !first.startsWith("-")) {
            listOf("# " + first.replace("**", "")) + blocks.drop(1)
        } else blocks
    }

    /** Text of a paragraph with **bold** and *italic* (and footnote marks [^n] when [ctx] knows the notes). */
    private fun runs(p: Element, ctx: DocxRefs? = null): String = buildString {
        val heading = p.child("pPr")?.child("pStyle")?.attr("val").orEmpty().lowercase().let { Regex("(heading|berschrift|title|titel)").containsMatchIn(it) }
        fun run(r: Element, inLink: Boolean = false) {
            r.children().firstOrNull { it.localName == "footnoteReference" }?.let { ref ->
                val id = ref.attr("id")
                if (ctx != null && id in ctx.notes) {
                    val n = ctx.numbers.getOrPut(id) { ctx.numbers.size + 1 }
                    append("[^$n]")
                }
                return
            }
            val rPr = r.child("rPr")
            val bold = rPr?.child("b")?.let { it.attr("val") != "0" && it.attr("val") != "false" } == true
            val italic = rPr?.child("i")?.let { it.attr("val") != "0" && it.attr("val") != "false" } == true
            val text = buildString {
                r.children().forEach { e ->
                    when (e.localName) {
                        "t" -> append(e.textContent)
                        "tab" -> append('\t')
                        "br", "cr" -> append(' ')
                    }
                }
            }
            if (text.isBlank()) { append(text); return }
            val lead = text.takeWhile { it == ' ' }
            val trail = text.takeLastWhile { it == ' ' }
            val core = text.trim()
            append(lead)
            // Word-Treue (AP-45): what the Fließtext-Editor knows comes along (not in headings, whose look is the style's).
            val extra = if (ctx == null || heading || rPr == null) null else runFormat(rPr, ctx, inLink)
            if (extra != null && extra != io.github.thepytales.llmhub.docs.text.CharFormat.PLAIN) {
                val f = extra.copy(bold = bold, italic = italic)
                append(io.github.thepytales.llmhub.docs.text.RichText.inline(core, List(core.length) { f }))
            } else append(when { bold && italic -> "***$core***"; bold -> "**$core**"; italic -> "*$core*"; else -> core })
            append(trail)
        }
        p.children().forEach { e ->
            when (e.localName) {
                "r" -> run(e)
                "hyperlink" -> e.children().filter { it.localName == "r" }.forEach { run(it, inLink = true) }
                "ins", "smartTag", "fldSimple" -> e.children().filter { it.localName == "r" }.forEach { run(it) }
                // Content controls inside the paragraph (fields of templates: name, date …).
                "sdt" -> e.child("sdtContent")?.children()?.forEach { c -> if (c.localName == "r") run(c) else if (c.localName == "hyperlink") c.children().filter { it.localName == "r" }.forEach { run(it, inLink = true) } }
                // Word formulas (AP-16) as LaTeX.
                "oMath" -> append("$" + LatexOmml.toLatex(e) + "$")
                "oMathPara" -> e.children().filter { it.localName == "oMath" }.forEach { append("$$" + LatexOmml.toLatex(it) + "$$") }
            }
        }
    }.replace("****", "").replace(Regex("\\*\\*\\s*\\*\\*"), " ")

    private val HIGHLIGHT = mapOf(
        "yellow" to "FFFF00", "green" to "00FF00", "cyan" to "00FFFF", "magenta" to "FF00FF", "blue" to "0000FF", "red" to "FF0000",
        "darkBlue" to "000080", "darkCyan" to "008080", "darkGreen" to "008000", "darkMagenta" to "800080", "darkRed" to "800000",
        "darkYellow" to "808000", "darkGray" to "808080", "lightGray" to "C0C0C0", "black" to "000000",
    )

    /** Underline, strike-through, colour, highlight, raised/lowered, a different size or font of a Word run (AP-45). */
    private fun runFormat(rPr: Element, ctx: DocxRefs, inLink: Boolean): io.github.thepytales.llmhub.docs.text.CharFormat {
        fun on(name: String) = rPr.child(name)?.let { it.attr("val") !in setOf("0", "false", "none") } == true
        val font = rPr.child("rFonts")?.attr("ascii")?.ifBlank { null }
        val mono = font != null && Regex("(?i)courier|consolas|mono").containsMatchIn(font)
        val color = rPr.child("color")?.attr("val")?.uppercase()?.takeIf { Regex("^[0-9A-F]{6}$").matches(it) && it != "000000" }
        val shade = rPr.child("shd")?.attr("fill")?.uppercase()?.takeIf { Regex("^[0-9A-F]{6}$").matches(it) && it != "FFFFFF" }
        val highlight = rPr.child("highlight")?.attr("val")?.let { HIGHLIGHT[it] } ?: shade
        val vert = rPr.child("vertAlign")?.attr("val")
        val size = rPr.child("sz")?.attr("val")?.takeIf { it != ctx.usualSize }?.toIntOrNull()?.let { it / 2 }
        return io.github.thepytales.llmhub.docs.text.CharFormat(
            underline = !inLink && on("u"),
            strike = on("strike") || on("dstrike"),
            code = mono,
            color = if (inLink || color == "1D4ED8" || color == "0563C1") null else color,
            highlight = if (mono && highlight == "F1F3F5") null else highlight,
            sup = vert == "superscript",
            sub = vert == "subscript",
            size = if (mono) null else size,
            font = font?.takeIf { !mono && it != ctx.usualFont },
        )
    }

    /** "numId:level" → true for bullets, false for numbered lists. */
    private fun numberingFormats(root: Element?): Map<String, Boolean> {
        root ?: return emptyMap()
        val abstract = root.children().filter { it.localName == "abstractNum" }.associate { a ->
            a.attr("abstractNumId") to a.children().filter { it.localName == "lvl" }.associate { l -> l.attr("ilvl") to (l.child("numFmt")?.attr("val") == "bullet") }
        }
        val out = HashMap<String, Boolean>()
        root.children().filter { it.localName == "num" }.forEach { n ->
            val levels = abstract[n.child("abstractNumId")?.attr("val")] ?: return@forEach
            levels.forEach { (lvl, bullet) -> out["${n.attr("numId")}:$lvl"] = bullet }
        }
        return out
    }

    /** Style id → name ("berschrift1" → "heading 1"). */
    private fun styleNames(root: Element?): Map<String, String> = root?.children()?.filter { it.localName == "style" }
        ?.associate { it.attr("styleId") to (it.child("name")?.attr("val") ?: it.attr("styleId")) }.orEmpty()

    // ── PowerPoint ──

    /**
     * PowerPoint as slides: Lora's own decks by their TUD shapes, foreign ones (also the TUD-Präsentationsvorlage)
     * with masters, layouts, placeholders, theme colours, groups, tables, charts and pictures; see [PptxReader].
     */
    fun pptx(bytes: ByteArray, media: MutableMap<String, ByteArray> = LinkedHashMap()): String = PptxReader.read(bytes, media)

    // ── Excel ──

    /** Sheets of an Excel workbook (title → Markdown with formats); see [XlsxReader]. */
    fun xlsx(bytes: ByteArray): List<Pair<String, String>> = XlsxReader.read(bytes)

    /** Cell formats of styles.xml (by index of cellXfs): bold, italic, colors, number format and alignment. */
    fun xlsxCellStyles(root: Element?): List<CellStyle?> {
        root ?: return emptyList()
        fun color(e: Element?): String? = e?.attr("rgb")?.takeIf { it.length >= 6 }?.let { "#" + it.takeLast(6).uppercase() }
        val codes = root.child("numFmts")?.children()?.associate { it.attr("numFmtId") to it.attr("formatCode") }.orEmpty()
        val fonts = root.child("fonts")?.children().orEmpty().map { f ->
            Triple(
                f.child("b")?.let { it.attr("val") != "0" && it.attr("val") != "false" } == true,
                f.child("i")?.let { it.attr("val") != "0" && it.attr("val") != "false" } == true,
                color(f.child("color"))?.takeIf { it != "#000000" },
            )
        }
        val fills = root.child("fills")?.children().orEmpty().map { f ->
            f.child("patternFill")?.takeIf { it.attr("patternType") == "solid" }?.let { color(it.child("fgColor")) }?.takeIf { it != "#FFFFFF" }
        }
        fun nf(id: String): String? {
            val n = id.toIntOrNull() ?: return null
            val code = codes[id].orEmpty()
            return when {
                n == 0 || n == 49 -> null
                n == 1 || n == 3 -> "0"
                n == 2 -> "0.00"
                n == 4 -> "#,##0.00"
                n == 9 -> "0%"
                n == 10 -> "0.00%"
                n in 14..22 || n in 27..36 || n in 45..47 || n in 50..58 -> "date"
                n in 5..8 || n in 37..44 -> "eur"
                code.isEmpty() -> null
                '€' in code || "EUR" in code -> "eur"
                '%' in code -> if (Regex("0[.,]0").containsMatchIn(code)) "0.0%" else "0%"
                Regex("(?i)[dmy]").containsMatchIn(code.replace(Regex("\"[^\"]*\"|\\[[^\\]]*\\]"), "")) -> "date"
                Regex("^[#0][#0.,]*$").matches(code) -> code
                code.contains("#,##0.00") -> "#,##0.00"
                code.contains("0.00") -> "0.00"
                else -> null
            }
        }
        return root.child("cellXfs")?.children().orEmpty().map { x ->
            val font = fonts.getOrNull(x.attr("fontId").toIntOrNull() ?: 0)
            val align = when (x.child("alignment")?.attr("horizontal")) { "left" -> "l"; "center", "centerContinuous" -> "c"; "right" -> "r"; else -> null }
            CellStyle(
                b = font?.first == true, i = font?.second == true, color = font?.third,
                fill = fills.getOrNull(x.attr("fillId").toIntOrNull() ?: 0), nf = nf(x.attr("numFmtId")), align = align,
            ).takeIf { !it.isEmpty }
        }
    }

    /** Excel notation ("," between arguments, "." decimal point) → German as typed in the editor. */
    fun fromExcel(formula: String): String {
        val out = StringBuilder()
        var inString = false
        val clean = formula.replace(Regex("_xlfn\\.|_xlws\\.|_xlpm\\."), "")
        clean.forEachIndexed { i, ch ->
            when {
                ch == '"' -> { inString = !inString; out.append(ch) }
                inString -> out.append(ch)
                ch == ',' -> out.append(';')
                ch == '.' && clean.getOrNull(i - 1)?.isDigit() == true && clean.getOrNull(i + 1)?.isDigit() == true -> out.append(',')
                else -> out.append(ch)
            }
        }
        // Excel's WEEKNUM without type counts from 1 January (type 1); the editor's default is ISO weeks.
        return SheetEngine.withDefaultArg(out.toString(), setOf("WEEKNUM"), "1", ';')
    }

    /** CSV (";" or "," – whichever the first line uses more) → table. */
    fun csv(text: String, separator: Char? = null): String {
        val lines = text.replace("\r\n", "\n").lines().filter { it.isNotEmpty() }
        val sep = separator ?: lines.firstOrNull().orEmpty().let { if (it.count { c -> c == ';' } >= it.count { c -> c == ',' }) ';' else ',' }
        val rows = lines.map { line ->
            val cells = mutableListOf<String>()
            val cur = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                    ch == '"' -> quoted = !quoted
                    ch == sep && !quoted -> { cells += cur.toString(); cur.clear() }
                    else -> cur.append(ch)
                }
                i++
            }
            cells += cur.toString()
            cells
        }
        return SheetEngine.markdown(rows.ifEmpty { listOf(listOf("")) })
    }

    private fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        .replace(Regex("(?i)<h([1-6])[^>]*>"), "\n\n").replace(Regex("(?i)</h[1-6]>"), "\n\n")
        .replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("(?i)</p>|</div>|</li>"), "\n\n")
        .replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\n{3,}"), "\n\n").trim()
}
