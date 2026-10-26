package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.docs.XlsxWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Changes Word, PowerPoint and Excel files in place, so that their formatting stays: text replacement inside the XML
 * parts (also when Word split the text into several runs) and setting cells of an Excel sheet.
 */
object OfficeEdit {
    /** Entries of a zip file in their order. */
    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val data = zip.readBytes()
                    total += data.size
                    if (total > 300L * 1024 * 1024) throw IllegalArgumentException("Die Datei ist entpackt zu groß.")
                    out[e.name] = data
                }
                e = zip.nextEntry
            }
        }
        if (out.isEmpty()) throw IllegalArgumentException("Die Datei ist kein gültiges Office-Dokument (kein ZIP).")
        return out
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    // ------------------------------------------------------------------ text replacement

    data class Replaced(val bytes: ByteArray, val count: Int, val parts: List<String>)

    private class Kind(val paragraph: String, val text: String, val preserveSpace: Boolean, val newline: String?)

    private val WORD = Kind("w:p", "w:t", true, "</w:t><w:br/><w:t xml:space=\"preserve\">")
    private val SLIDE = Kind("a:p", "a:t", false, null)
    private val CELL_TEXT = Kind("si|is", "t", true, null)

    /**
     * Replaces [old] by [new] in the text of [ext] (docx, pptx, xlsx). Fails if [old] is missing or – without
     * [all] – occurs more than once. [slide] (1-based) limits a presentation to one slide.
     */
    fun replace(ext: String, bytes: ByteArray, old: String, new: String, all: Boolean = false, slide: Int? = null): Replaced {
        if (old.isEmpty()) throw IllegalArgumentException("old_text fehlt.")
        val files = entries(bytes)
        val (kind, parts) = when (ext) {
            "docx", "docm", "dotx" -> WORD to files.keys.filter { Regex("word/(document|header\\d*|footer\\d*|footnotes|endnotes|comments)\\.xml").matches(it) }
                .sortedBy { if (it == "word/document.xml") 0 else 1 }
            "pptx", "pptm", "potx" -> SLIDE to files.keys.filter { n ->
                val m = Regex("ppt/(slides/slide|notesSlides/notesSlide)(\\d+)\\.xml").matchEntire(n) ?: return@filter false
                slide == null || m.groupValues[1] == "slides/slide" && m.groupValues[2].toInt() == slide
            }.sortedBy { Regex("\\d+").find(it.substringAfterLast('/'))?.value?.toInt() ?: 0 }
            "xlsx", "xlsm" -> CELL_TEXT to files.keys.filter { it == "xl/sharedStrings.xml" || Regex("xl/worksheets/sheet\\d+\\.xml").matches(it) }
            else -> throw IllegalArgumentException("edit_office_file kann .docx, .pptx und .xlsx ändern, nicht .$ext.")
        }
        if (ext.startsWith("ppt") && slide != null && parts.isEmpty()) throw IllegalArgumentException("Folie $slide gibt es nicht.")
        // First count all matches, so nothing is written if the text is ambiguous.
        val found = parts.sumOf { p -> replaceIn(files.getValue(p).toString(Charsets.UTF_8), kind, old, new, dryRun = true).second }
        if (found == 0) throw IllegalArgumentException(
            "„${old.take(80)}“ kommt im Text nicht vor. Mit read_local_file nachsehen; Treffer über Absatz- oder Zellgrenzen hinweg gehen nicht – dann kleinere Stücke ersetzen.",
        )
        if (found > 1 && !all) throw IllegalArgumentException("„${old.take(80)}“ kommt $found-mal vor – mehr Kontext angeben oder replace_all=true.")
        val changed = mutableListOf<String>()
        for (p in parts) {
            val (xml, n) = replaceIn(files.getValue(p).toString(Charsets.UTF_8), kind, old, new, dryRun = false)
            if (n > 0) { files[p] = xml.toByteArray(Charsets.UTF_8); changed += p }
        }
        return Replaced(zip(files), found, changed)
    }

    private class TextNode(val start: Int, val end: Int, val openTag: String, val text: String)

    private fun decode(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        .replace("&amp;", "&")

    private fun encode(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    /**
     * Replacement in one XML part. The text elements of each paragraph are joined; for a match the replacement goes
     * into the first affected element and the matched text is removed from the others – their run properties stay.
     */
    private fun replaceIn(xml: String, kind: Kind, old: String, new: String, dryRun: Boolean): Pair<String, Int> {
        val paraNames = kind.paragraph.split('|')
        val token = Regex(
            paraNames.joinToString("|") { "<$it(?=[\\s>/])[^>]*?/>|<$it(?=[\\s>])[^>]*>|</$it>" } +
                "|<${kind.text}(?=[\\s>])[^>]*>([^<]*)</${kind.text}>",
        )
        // Paragraphs as lists of text nodes (nested paragraphs, e.g. in text boxes, get their own list).
        val stack = ArrayDeque<MutableList<TextNode>>()
        val paragraphs = mutableListOf<List<TextNode>>()
        for (m in token.findAll(xml)) {
            val v = m.value
            when {
                v.endsWith("/>") && !v.startsWith("<${kind.text}") -> {}
                v.startsWith("</") -> stack.removeLastOrNull()?.let { if (it.isNotEmpty()) paragraphs += it }
                m.groups[1] != null -> {
                    val open = v.substring(0, v.indexOf('>') + 1)
                    stack.lastOrNull()?.add(TextNode(m.range.first, m.range.last + 1, open, decode(m.groupValues[1])))
                }
                else -> stack.addLast(mutableListOf())
            }
        }
        var count = 0
        // New content per text node (by start offset).
        val replacement = HashMap<Int, String>()
        for (nodes in paragraphs) {
            val joined = nodes.joinToString("") { it.text }
            var from = 0
            val texts = nodes.map { it.text }.toMutableList()
            val offsets = IntArray(nodes.size + 1)
            for (i in nodes.indices) offsets[i + 1] = offsets[i] + nodes[i].text.length
            val matches = mutableListOf<Int>()
            while (true) {
                val at = joined.indexOf(old, from)
                if (at < 0) break
                matches += at
                from = at + old.length
            }
            if (matches.isEmpty()) continue
            count += matches.size
            if (dryRun) continue
            // Apply from the back so earlier offsets stay valid.
            for (at in matches.asReversed()) {
                val end = at + old.length
                val first = (0 until nodes.size).first { offsets[it + 1] > at || it == nodes.size - 1 && offsets[it + 1] >= at }
                val last = (first until nodes.size).first { offsets[it + 1] >= end }
                for (i in first..last) {
                    val s = (maxOf(at, offsets[i]) - offsets[i]).coerceIn(0, texts[i].length)
                    val e = (minOf(end, offsets[i + 1]) - offsets[i]).coerceIn(0, texts[i].length)
                    // Positions in texts[i] may have moved by later (already applied) matches in the same node only
                    // behind e, so s and e are still right.
                    texts[i] = texts[i].substring(0, s) + (if (i == first) new else "") + texts[i].substring(e)
                }
            }
            nodes.forEachIndexed { i, n -> if (texts[i] != n.text) replacement[n.start] = render(n, texts[i], kind) }
        }
        if (dryRun || replacement.isEmpty()) return xml to count
        val sb = StringBuilder()
        var pos = 0
        for (n in paragraphs.flatten().sortedBy { it.start }) {
            val r = replacement[n.start] ?: continue
            sb.append(xml, pos, n.start).append(r)
            pos = n.end
        }
        sb.append(xml, pos, xml.length)
        return sb.toString() to count
    }

    private fun render(n: TextNode, text: String, kind: Kind): String {
        var open = n.openTag
        val needsSpace = kind.preserveSpace && (text.startsWith(" ") || text.endsWith(" ") || "  " in text || '\n' in text)
        if (needsSpace && "xml:space" !in open) open = open.dropLast(1) + " xml:space=\"preserve\">"
        val close = "</${kind.text}>"
        val body = if (kind.newline != null) text.split('\n').joinToString(kind.newline) { encode(it) }
        else encode(if (kind === SLIDE) text.replace('\n', ' ') else text)
        return open + body + close
    }

    // ------------------------------------------------------------------ Excel cells

    data class CellsSet(val bytes: ByteArray, val sheet: String, val cells: Int)

    private val CELL_REF = Regex("^([A-Za-z]{1,3})(\\d{1,7})$")

    private fun colIndex(letters: String): Int = letters.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun attr(tag: String, name: String): String? = Regex("\\s$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    /**
     * Sets [values] ("B3" → "12", "=SUM(B1:B3)", "Text"; "" clears) in [sheet] (name or 1-based number, default:
     * first sheet). Numbers become numbers, "=…" formulas without cached value (Excel recalculates when opening),
     * everything else inline text; a leading ' forces text. Styles of existing cells and all other parts stay.
     */
    fun setCells(bytes: ByteArray, sheet: String?, values: Map<String, String>): CellsSet {
        if (values.isEmpty()) throw IllegalArgumentException("cells ist leer.")
        val files = entries(bytes)
        val workbook = files["xl/workbook.xml"]?.toString(Charsets.UTF_8) ?: throw IllegalArgumentException("Keine Excel-Arbeitsmappe (.xlsx).")
        val rels = files["xl/_rels/workbook.xml.rels"]?.toString(Charsets.UTF_8).orEmpty()
        val sheets = Regex("<sheet\\s[^>]*?/?>").findAll(workbook).map { m ->
            Triple(attr(m.value, "name")?.let(::decodeAttr).orEmpty(), attr(m.value, "r:id") ?: Regex("\\s[\\w]+:id=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1).orEmpty(), m.value)
        }.toList()
        if (sheets.isEmpty()) throw IllegalArgumentException("Die Arbeitsmappe hat keine Tabellenblätter.")
        val wanted = sheet?.trim()?.takeIf { it.isNotEmpty() }
        val chosen = when {
            wanted == null -> sheets.first()
            else -> sheets.firstOrNull { it.first.equals(wanted, true) } ?: wanted.toIntOrNull()?.let { sheets.getOrNull(it - 1) }
                ?: throw IllegalArgumentException("Blatt „$wanted“ gibt es nicht. Blätter: ${sheets.joinToString { "„${it.first}“" }}.")
        }
        val target = Regex("<Relationship\\s[^>]*>").findAll(rels).map { it.value }.firstOrNull { attr(it, "Id") == chosen.second }?.let { attr(it, "Target") }
            ?: "worksheets/sheet${sheets.indexOf(chosen) + 1}.xml"
        val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
        var xml = files[path]?.toString(Charsets.UTF_8) ?: throw IllegalArgumentException("Blatt „${chosen.first}“ ($path) fehlt in der Datei.")

        val cells = values.entries.map { (ref, v) ->
            val m = CELL_REF.matchEntire(ref.trim()) ?: throw IllegalArgumentException("„$ref“ ist keine Zelle (z.B. B3).")
            Triple(m.groupValues[2].toInt(), colIndex(m.groupValues[1]), v)
        }
        // sheetData with its rows.
        if (Regex("<sheetData\\s*/>").containsMatchIn(xml)) xml = xml.replace(Regex("<sheetData\\s*/>"), "<sheetData></sheetData>")
        val dataMatch = Regex("<sheetData>([\\s\\S]*?)</sheetData>").find(xml) ?: Regex("<sheetData\\s[^>]*>([\\s\\S]*?)</sheetData>").find(xml)
            ?: throw IllegalArgumentException("Das Blatt hat keinen Datenbereich (sheetData).")
        val rowRegex = Regex("<row(?=[\\s>/])[^>]*?(/>|>[\\s\\S]*?</row>)")
        val rows = java.util.TreeMap<Int, String>()
        var implicit = 0
        for (m in rowRegex.findAll(dataMatch.groupValues[1])) {
            val open = m.value.substring(0, m.value.indexOf('>') + 1)
            val r = attr(open, "r")?.toIntOrNull() ?: (implicit + 1)
            implicit = r
            rows[r] = m.value
        }
        for ((r, cellsOfRow) in cells.groupBy { it.first }) {
            rows[r] = setInRow(rows[r] ?: "<row r=\"$r\"></row>", r, cellsOfRow.map { it.second to it.third })
        }
        val newData = rows.values.joinToString("")
        val dataOpen = dataMatch.value.substring(0, dataMatch.value.indexOf('>') + 1)
        xml = xml.substring(0, dataMatch.range.first) + dataOpen + newData + "</sheetData>" + xml.substring(dataMatch.range.last + 1)
        // Used range.
        val maxRow = maxOf(rows.lastKey(), 1)
        val maxCol = rows.values.flatMap { row -> Regex("<c\\s[^>]*?r=\"([A-Z]+)\\d+\"").findAll(row).map { colIndex(it.groupValues[1]) }.toList() }.maxOrNull() ?: 1
        val minRow = rows.firstKey()
        xml = xml.replace(Regex("<dimension\\s+ref=\"[^\"]*\"\\s*/>"), "<dimension ref=\"A$minRow:${colName(maxCol)}$maxRow\"/>")
        files[path] = xml.toByteArray(Charsets.UTF_8)
        // Excel rebuilds the calculation chain and recalculates all formulas when opening.
        if (files.remove("xl/calcChain.xml") != null) {
            files["[Content_Types].xml"]?.let { ct -> files["[Content_Types].xml"] = ct.toString(Charsets.UTF_8).replace(Regex("<Override[^>]*calcChain[^>]*/>"), "").toByteArray(Charsets.UTF_8) }
            files["xl/_rels/workbook.xml.rels"]?.let { r -> files["xl/_rels/workbook.xml.rels"] = r.toString(Charsets.UTF_8).replace(Regex("<Relationship[^>]*calcChain[^>]*/>"), "").toByteArray(Charsets.UTF_8) }
        }
        val wb = when {
            Regex("<calcPr[^>]*fullCalcOnLoad").containsMatchIn(workbook) -> workbook
            Regex("<calcPr\\b").containsMatchIn(workbook) -> workbook.replace(Regex("<calcPr\\b"), "<calcPr fullCalcOnLoad=\"1\"")
            else -> workbook.replace("</workbook>", "<calcPr fullCalcOnLoad=\"1\"/></workbook>")
        }
        files["xl/workbook.xml"] = fixCalcPrOrder(wb).toByteArray(Charsets.UTF_8)
        return CellsSet(zip(files), chosen.first, cells.size)
    }

    /** calcPr must come right after sheets/functionGroups/externalReferences/definedNames in the workbook schema. */
    private fun fixCalcPrOrder(wb: String): String {
        val calc = Regex("<calcPr[^>]*/>").find(wb) ?: return wb
        val without = wb.removeRange(calc.range)
        val anchors = listOf("</definedNames>", "</externalReferences>", "</functionGroups>", "</sheets>")
        val anchor = anchors.firstNotNullOfOrNull { a -> without.indexOf(a).takeIf { it >= 0 }?.let { it + a.length } } ?: return wb
        return without.substring(0, anchor) + calc.value + without.substring(anchor)
    }

    private fun decodeAttr(s: String) = decode(s)

    private fun setInRow(rowXml: String, r: Int, values: List<Pair<Int, String>>): String {
        val selfClosing = rowXml.trimEnd().endsWith("/>") && !rowXml.contains("</row>")
        val open = rowXml.substring(0, rowXml.indexOf('>') + 1).let { if (selfClosing) it.dropLast(2).trimEnd() + ">" else it }
            .replace(Regex("\\sspans=\"[^\"]*\""), "")
        val inner = if (selfClosing) "" else rowXml.substring(rowXml.indexOf('>') + 1, rowXml.lastIndexOf("</row>"))
        val cellRegex = Regex("<c(?=[\\s>/])[^>]*?(/>|>[\\s\\S]*?</c>)")
        val cells = java.util.TreeMap<Int, String>()
        var implicit = 0
        for (m in cellRegex.findAll(inner)) {
            val tag = m.value.substring(0, m.value.indexOf('>') + 1)
            val col = attr(tag, "r")?.let { CELL_REF.matchEntire(it)?.groupValues?.get(1) }?.let(::colIndex) ?: (implicit + 1)
            implicit = col
            cells[col] = m.value
        }
        for ((col, value) in values) {
            val ref = colName(col) + r
            val old = cells[col]
            val style = old?.let { attr(it.substring(0, it.indexOf('>') + 1), "s") }
            if (old != null && Regex("<f[^>]*\\st=\"shared\"[^>]*\\sref=").containsMatchIn(old)) {
                throw IllegalArgumentException("$ref ist die Ausgangszelle einer geteilten Formel (für mehrere Zellen) – diese Zelle lieber in Excel ändern.")
            }
            cells[col] = cell(ref, style, value)
        }
        return open + cells.values.joinToString("") + "</row>"
    }

    private fun cell(ref: String, style: String?, value: String): String {
        val s = style?.let { " s=\"$it\"" }.orEmpty()
        val v = value
        return when {
            v.isEmpty() -> "<c r=\"$ref\"$s/>"
            v.startsWith("'") -> "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${encode(v.drop(1))}</t></is></c>"
            v.startsWith("=") && v.length > 1 -> "<c r=\"$ref\"$s><f>${encode(v.drop(1))}</f></c>"
            v.trim().equals("TRUE", true) || v.trim().equals("WAHR", true) -> "<c r=\"$ref\"$s t=\"b\"><v>1</v></c>"
            v.trim().equals("FALSE", true) || v.trim().equals("FALSCH", true) -> "<c r=\"$ref\"$s t=\"b\"><v>0</v></c>"
            else -> {
                val t = v.trim()
                // "12", "-3.5", "1e5", German "3,5" and "1.234,5"; not "0351 123" (leading zero, spaces).
                val leadingZero = Regex("^-?0\\d").containsMatchIn(t)
                val number = if (leadingZero) null
                else Regex("^-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$").matchEntire(t)?.let { t.toDoubleOrNull() }
                    ?: if (Regex("^-?\\d{1,3}(\\.\\d{3})*(,\\d+)?$|^-?\\d+,\\d+$").matches(t)) XlsxWriter.number(t) else null
                if (number != null) "<c r=\"$ref\"$s><v>${plain(number)}</v></c>"
                else "<c r=\"$ref\"$s t=\"inlineStr\"><is><t xml:space=\"preserve\">${encode(v)}</t></is></c>"
            }
        }
    }

    private fun plain(d: Double): String = if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()
}
