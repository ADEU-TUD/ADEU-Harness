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
    data class Imported(val kind: DocKind, val title: String, val tabs: List<Pair<String, String>>)

    private val TEXT = setOf("md", "markdown", "txt", "text", "html", "htm", "rtf", "tex", "csv", "tsv")

    fun canOpen(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in TEXT + setOf("docx", "pptx", "xlsx", "xlsm")

    /** Reads [bytes] of the file [name]; null if the format is not supported. */
    fun read(name: String, bytes: ByteArray): Imported? {
        val ext = name.substringAfterLast('.', "").lowercase()
        val base = name.substringBeforeLast('.').ifBlank { name }
        return when (ext) {
            "docx" -> docx(bytes).let { md -> Imported(DocKind.DOC, DocBlocks.titleOf(md) ?: base, listOf("Tab 1" to md)) }
            "pptx" -> Imported(DocKind.SLIDES, base, listOf("Folien" to pptx(bytes)))
            "xlsx", "xlsm" -> Imported(DocKind.SHEET, base, xlsx(bytes).ifEmpty { listOf("Tabelle 1" to SheetEngine.blank()) })
            "csv", "tsv" -> Imported(DocKind.SHEET, base, listOf("Tabelle 1" to csv(bytes.toString(Charsets.UTF_8).removePrefix("﻿"), if (ext == "tsv") '\t' else null)))
            in TEXT -> {
                val text = bytes.toString(Charsets.UTF_8).removePrefix("﻿")
                val md = if (ext == "html" || ext == "htm") htmlToText(text) else text
                Imported(DocKind.DOC, DocBlocks.titleOf(md) ?: base, listOf("Tab 1" to md))
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

    fun docx(bytes: ByteArray): String {
        val files = entries(bytes)
        val body = xml(files["word/document.xml"])?.all("body")?.firstOrNull() ?: return ""
        val bullets = numberingFormats(xml(files["word/numbering.xml"]))
        val styles = styleNames(xml(files["word/styles.xml"]))
        val blocks = mutableListOf<String>()
        val counters = HashMap<String, Int>()
        for (el in body.children()) {
            when (el.localName) {
                "p" -> {
                    val text = runs(el).trim()
                    val pPr = el.child("pPr")
                    val styleId = pPr?.child("pStyle")?.attr("val").orEmpty()
                    val style = (styles[styleId] ?: styleId).lowercase().replace(" ", "")
                    val num = pPr?.child("numPr")
                    if (text.isEmpty()) continue
                    // Bullets typed as characters ("• Punkt") are list items too.
                    val typed = Regex("^[•◦▪–·]\\s+").find(text)
                    if (typed != null && num == null) {
                        val line = "- " + text.substring(typed.range.last + 1)
                        if (blocks.isNotEmpty() && DocBlocks.kindOf(blocks.last()) == DocBlocks.Kind.BULLETS) blocks[blocks.lastIndex] = blocks.last() + "\n" + line
                        else blocks += line
                        continue
                    }
                    val heading = Regex("(heading|überschrift|berschrift)(\\d)").find(style)?.groupValues?.get(2)?.toIntOrNull()
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
                        else -> blocks += text
                    }
                }
                "tbl" -> {
                    val rows = el.children().filter { it.localName == "tr" }.map { tr ->
                        tr.children().filter { it.localName == "tc" }.map { tc ->
                            tc.children().filter { it.localName == "p" }.joinToString(" ") { runs(it).trim() }.trim()
                                .let { cell -> if (cell.length > 4 && cell.startsWith("**") && cell.endsWith("**")) cell.substring(2, cell.length - 2) else cell }
                        }
                    }.filter { it.isNotEmpty() }
                    if (rows.isNotEmpty()) blocks += DocBlocks.table(rows)
                }
            }
        }
        // A document without a heading-styled title starts with its first short line as title.
        return DocBlocks.join(fixTitle(blocks))
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

    /** Text of a paragraph with **bold** and *italic*. */
    private fun runs(p: Element): String = buildString {
        fun run(r: Element) {
            val rPr = r.child("rPr")
            val bold = rPr?.child("b")?.let { it.attr("val") != "0" && it.attr("val") != "false" } == true
            val italic = rPr?.child("i")?.let { it.attr("val") != "0" && it.attr("val") != "false" } == true
            val text = buildString {
                r.children().forEach { e ->
                    when (e.localName) {
                        "t" -> append(e.textContent)
                        "tab" -> append(' ')
                        "br", "cr" -> append(' ')
                    }
                }
            }
            if (text.isBlank()) { append(text); return }
            val lead = text.takeWhile { it == ' ' }
            val trail = text.takeLastWhile { it == ' ' }
            val core = text.trim()
            append(lead)
            append(when { bold && italic -> "***$core***"; bold -> "**$core**"; italic -> "*$core*"; else -> core })
            append(trail)
        }
        p.children().forEach { e ->
            when (e.localName) {
                "r" -> run(e)
                "hyperlink", "ins", "smartTag", "fldSimple" -> e.children().filter { it.localName == "r" }.forEach(::run)
            }
        }
    }.replace("****", "").replace(Regex("\\*\\*\\s*\\*\\*"), " ")

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

    fun pptx(bytes: ByteArray): String {
        val files = entries(bytes)
        val slideNames = files.keys.mapNotNull { k -> Regex("^ppt/slides/slide(\\d+)\\.xml$").find(k)?.groupValues?.get(1)?.toInt()?.let { it to k } }.sortedBy { it.first }
        val slides = slideNames.mapIndexed { index, (n, key) ->
            val root = xml(files[key]) ?: return@mapIndexed SlideDeck.Slide("Folie ${index + 1}")
            var title = ""
            var subtitle = ""
            val body = mutableListOf<String>()
            root.all("sp").forEach { sp ->
                val ph = sp.all("ph").firstOrNull()?.attr("type").orEmpty()
                val paras = sp.all("p").map { p ->
                    val lvl = p.child("pPr")?.attr("lvl")?.toIntOrNull() ?: 0
                    lvl to p.all("t").joinToString("") { it.textContent }.trim()
                }.filter { it.second.isNotEmpty() }
                when (ph) {
                    "title", "ctrTitle" -> title = paras.joinToString(" ") { it.second }
                    "subTitle" -> subtitle = paras.joinToString(" ") { it.second }
                    "sldNum", "dt", "ftr" -> {}
                    else -> paras.forEach { (lvl, t) -> body += "  ".repeat(lvl) + "- " + t }
                }
            }
            root.all("tbl").forEach { tbl ->
                val rows = tbl.all("tr").map { tr -> tr.all("tc").map { tc -> tc.all("t").joinToString(" ") { it.textContent }.trim() } }
                if (rows.isNotEmpty()) body += "\n" + DocBlocks.table(rows) + "\n"
            }
            val notes = notesFor(files, n)
            if (index == 0) SlideDeck.Slide(title.ifBlank { body.firstOrNull()?.removePrefix("- ").orEmpty() }, subtitle, notes = notes, layout = SlideDeck.Layout.TITLE)
            else SlideDeck.Slide(title.ifBlank { "Folie ${index + 1}" }, body.joinToString("\n").trim(), notes = notes, layout = if (body.isEmpty()) SlideDeck.Layout.SECTION else SlideDeck.Layout.CONTENT)
        }
        return SlideDeck.markdown(slides.ifEmpty { listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE)) })
    }

    private fun notesFor(files: Map<String, ByteArray>, slide: Int): String {
        val rels = xml(files["ppt/slides/_rels/slide$slide.xml.rels"]) ?: return ""
        val target = rels.children().firstOrNull { it.attr("Type").endsWith("/notesSlide") }?.attr("Target") ?: return ""
        val notes = xml(files["ppt/notesSlides/" + target.substringAfterLast('/')]) ?: return ""
        return notes.all("sp").filter { sp -> sp.all("ph").firstOrNull()?.attr("type") == "body" }
            .flatMap { sp -> sp.all("p").map { p -> p.all("t").joinToString("") { it.textContent } } }
            .filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    // ── Excel ──

    fun xlsx(bytes: ByteArray): List<Pair<String, String>> {
        val files = entries(bytes)
        val shared = xml(files["xl/sharedStrings.xml"])?.children()?.filter { it.localName == "si" }?.map { si -> si.all("t").joinToString("") { it.textContent } }.orEmpty()
        val rels = xml(files["xl/_rels/workbook.xml.rels"])?.children()?.associate { it.attr("Id") to it.attr("Target") }.orEmpty()
        val sheets = xml(files["xl/workbook.xml"])?.all("sheet").orEmpty()
        return sheets.mapNotNull { s ->
            val target = rels[s.attr("id")] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val root = xml(files[path]) ?: return@mapNotNull null
            val cells = HashMap<Pair<Int, Int>, String>()
            root.all("c").forEach { c ->
                val ref = SheetEngine.parseRef(c.attr("r")) ?: return@forEach
                val f = c.child("f")?.textContent?.takeIf { it.isNotBlank() }
                val v = c.child("v")?.textContent.orEmpty()
                cells[ref] = when {
                    f != null -> "=" + fromExcel(f)
                    c.attr("t") == "s" -> shared.getOrNull(v.toIntOrNull() ?: -1).orEmpty()
                    c.attr("t") == "inlineStr" -> c.all("t").joinToString("") { it.textContent }
                    c.attr("t") == "b" -> if (v == "1") "WAHR" else "FALSCH"
                    else -> v.toDoubleOrNull()?.let(SheetEngine::formatNumber) ?: v
                }
            }
            if (cells.isEmpty()) return@mapNotNull s.attr("name") to SheetEngine.blank()
            val rows = cells.keys.maxOf { it.first } + 1
            val cols = cells.keys.maxOf { it.second } + 1
            s.attr("name") to SheetEngine.markdown((0 until rows.coerceAtMost(2000)).map { r -> (0 until cols.coerceAtMost(200)).map { c -> cells[r to c].orEmpty() } })
        }
    }

    /** Excel notation ("," between arguments, "." decimal point) → German as typed in the editor. */
    fun fromExcel(formula: String): String {
        val out = StringBuilder()
        var inString = false
        formula.forEachIndexed { i, ch ->
            when {
                ch == '"' -> { inString = !inString; out.append(ch) }
                inString -> out.append(ch)
                ch == ',' -> out.append(';')
                ch == '.' && formula.getOrNull(i - 1)?.isDigit() == true && formula.getOrNull(i + 1)?.isDigit() == true -> out.append(',')
                else -> out.append(ch)
            }
        }
        return out.toString()
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
