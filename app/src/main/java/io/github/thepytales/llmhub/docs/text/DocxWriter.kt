package io.github.thepytales.llmhub.docs.text

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.ui.ALIGN_MARK
import io.github.thepytales.llmhub.ui.inlineSegments
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Word-Treue (AP-45): writes the editor's text back into the original .docx instead of making a new file.
 *
 * Every paragraph and table of the original that still says the same is copied unchanged – with its formatting,
 * styles, bookmarks, fields, content controls and whatever else the editor does not know. Only new or changed blocks
 * are written from the editor; what lies between them in the original without text of its own (section breaks,
 * empty paragraphs, content controls, the Inhaltsverzeichnis …) stays where it was. Styles, numbering, headers and
 * footers, pictures and all other parts of the package are not touched.
 *
 * Returns null when this is not safe (a text block of the original cannot be found again, or a new block needs parts
 * of its own such as pictures) – then the caller writes the file anew as before.
 */
object DocxWriter {
    fun merge(original: ByteArray, baseMarkdown: String, newMarkdown: String, theme: DocTheme = DocTheme()): ByteArray? {
        // Abschnitte live in the paragraphs of the original (w:sectPr): changed section breaks need a file written anew.
        if (DocMarkup.sections(baseMarkdown) != DocMarkup.sections(newMarkdown)) return null
        val files = unzip(original) ?: return null
        val docXml = files["word/document.xml"]?.toString(Charsets.UTF_8) ?: return null
        val open = Regex("<w:body(\\s[^>]*)?>").find(docXml) ?: return null
        val close = docXml.lastIndexOf("</w:body>").takeIf { it > open.range.last } ?: return null
        val all = topLevel(docXml.substring(open.range.last + 1, close)) ?: return null
        val sect = all.lastOrNull()?.takeIf { it.startsWith("<w:sectPr") }
        val els = if (sect != null) all.dropLast(1) else all
        // List marks typed as text (• , 1. , ☐ – how the app and many templates write lists) are not part of the item.
        val texts = els.map { norm(plainXml(it).replace(LIST_MARK, "")) }

        val oldBlocks = DocBlocks.split(baseMarkdown).map { it.trim() }.filter { it.isNotBlank() }
        val newBlocks = DocBlocks.split(newMarkdown).map { it.trim() }.filter { it.isNotBlank() }

        // Which elements of the original each old block came from.
        val owner = IntArray(els.size) { -1 }
        val ranges = arrayOfNulls<IntRange>(oldBlocks.size)
        var from = 0
        for ((bi, b) in oldBlocks.withIndex()) {
            val image = DocMarkup.image(b) != null
            val pageBreak = DocMarkup.isPageBreak(b)
            // Setting lines (page setup, Formatvorlagen) and Word text boxes (read only) have no paragraph of their own.
            if (DocMarkup.isComment(b) || DocMarkup.isTextBox(b)) continue
            val want = norm(plainBlock(b))
            val found = when {
                image -> (from until els.size).firstOrNull { "<w:drawing" in els[it] || "<w:pict" in els[it] }?.let { it..it }
                pageBreak -> (from until els.size).firstOrNull { "w:type=\"page\"" in els[it] && texts[it].isEmpty() }?.let { it..it }
                want.isEmpty() -> null
                else -> findRun(texts, from, want)
            }
            if (found == null) {
                // A text block that cannot be found again: merging is not safe.
                if (want.isNotEmpty() && !image && !pageBreak && !DocMarkup.isToc(b) && !b.startsWith("[^")) return null
                continue
            }
            ranges[bi] = found
            for (k in found) owner[k] = bi
            from = found.last + 1
        }

        // Footnotes (AP-45): the reader numbered them in the order of use; a changed paragraph with a mark refers to the
        // same note of the original by its id.
        val noteIds = Regex("<w:footnoteReference w:id=\"(\\d+)\"").findAll(docXml).map { it.groupValues[1].toInt() }.distinct().toList()

        // Old and new blocks: the longest common sequence stays as it was.
        val keep = lcs(oldBlocks, newBlocks)
        val styleIds = headingStyles(files["word/styles.xml"]?.toString(Charsets.UTF_8))
        val out = StringBuilder()
        var emitted = -1 // last element of the original that was written
        val oldUsed = BooleanArray(oldBlocks.size)
        fun keepUnowned(until: Int) {
            for (k in emitted + 1 until until) if (owner[k] == -1) out.append(els[k])
            emitted = maxOf(emitted, until - 1)
        }
        for ((nj, nb) in newBlocks.withIndex()) {
            val oi = keep[nj]
            val r = oi?.let { ranges[it] }
            when {
                oi != null && r != null -> {
                    keepUnowned(r.first)
                    for (k in r) out.append(els[k])
                    emitted = maxOf(emitted, r.last)
                    oldUsed[oi] = true
                }
                // Unchanged blocks without an element of their own (footnotes, Inhaltsverzeichnis marker): already in the file.
                oi != null -> oldUsed[oi] = true
                // Text boxes stay in the paragraph they are anchored in (AP-45: read only); setting lines are no text.
                DocMarkup.isTextBox(nb) || DocMarkup.isComment(nb) -> {}
                else -> {
                    val xml = MarkdownDocx.fragment(nb, theme, noteIds) ?: return null
                    out.append(restyle(xml, styleIds))
                }
            }
        }
        keepUnowned(els.size)
        sect?.let(out::append)
        val newDoc = docXml.substring(0, open.range.last + 1) + out + docXml.substring(close)
        files["word/document.xml"] = newDoc.toByteArray(Charsets.UTF_8)
        return zip(files)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────────

    /** The elements directly inside the body, as their exact XML; null if the XML cannot be split safely. */
    internal fun topLevel(xml: String): List<String>? {
        val out = mutableListOf<String>()
        var i = 0
        var depth = 0
        var start = -1
        while (i < xml.length) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            when {
                xml.startsWith("<!--", lt) -> { i = xml.indexOf("-->", lt).takeIf { it >= 0 }?.plus(3) ?: return null; continue }
                xml.startsWith("<?", lt) -> { i = xml.indexOf("?>", lt).takeIf { it >= 0 }?.plus(2) ?: return null; continue }
                xml.startsWith("<![CDATA[", lt) -> { i = xml.indexOf("]]>", lt).takeIf { it >= 0 }?.plus(3) ?: return null; continue }
            }
            val gt = tagEnd(xml, lt) ?: return null
            val tag = xml.substring(lt, gt + 1)
            when {
                tag.startsWith("</") -> {
                    depth--
                    if (depth < 0) return null
                    if (depth == 0) { out += xml.substring(start, gt + 1); start = -1 }
                }
                tag.endsWith("/>") -> if (depth == 0) out += tag
                else -> {
                    if (depth == 0) start = lt
                    depth++
                }
            }
            i = gt + 1
        }
        return if (depth == 0) out else null
    }

    /** The end of the tag starting at [lt] (quotes in attributes respected). */
    private fun tagEnd(xml: String, lt: Int): Int? {
        var q: Char? = null
        for (k in lt + 1 until xml.length) {
            val ch = xml[k]
            if (q != null) { if (ch == q) q = null; continue }
            if (ch == '"' || ch == '\'') q = ch else if (ch == '>') return k
        }
        return null
    }

    private val LIST_MARK = Regex("^\\s*([•◦▪–·☐☒]|\\d+[.)])\\s+")

    private val T = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:delText[^>]*>[^<]*</w:delText>")

    private val BOXES = Regex("<w:txbxContent\\b.*?</w:txbxContent>|<mc:Fallback\\b.*?</mc:Fallback>", RegexOption.DOT_MATCHES_ALL)

    /**
     * The visible text of an element (Word's text runs, tabs as spaces; deleted text of revisions left out). Text boxes
     * do not count: the reader makes blocks of their own of them.
     */
    internal fun plainXml(xml: String): String = T.findAll(if ("<w:txbxContent" in xml || "<mc:Fallback" in xml) BOXES.replace(xml, "") else xml).joinToString("") { m ->
        when {
            m.value.startsWith("<w:tab") -> " "
            m.value.startsWith("<w:delText") -> ""
            else -> unesc(m.groupValues[1])
        }
    }

    /** The visible text of a Markdown block, as the reader made it from Word. */
    internal fun plainBlock(block: String): String {
        val lines = block.lines().map { l ->
            var t = l.trim()
            if (t.startsWith("|")) return@map t.trim('|').split('|').filter { !it.trim().matches(Regex(":?-{2,}:?")) }.joinToString("") { inlinePlain(it.trim()) }
            t = t.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^>\\s?"), "").replace(Regex("^([-*+]|\\d+[.)])\\s+(\\[[ xX]]\\s+)?"), "")
            // Marks typed as text (☒ , • ) are left out on both sides, as for the elements of the original.
            LIST_MARK.replace(inlinePlain(t), "")
        }
        return lines.joinToString("")
    }

    private fun inlinePlain(s: String) = inlineSegments(ALIGN_MARK.replace(s, "")).joinToString("") { it.text }
        .replace(Regex("\\[\\^[^\\]]+]"), "").replace(Regex("\\$[^$]+\\$"), "")

    private fun norm(s: String) = s.replace(' ', ' ').replace(Regex("\\s+"), "")

    /** Consecutive elements from [from] whose text together is [want] (elements without text may lie between). */
    private fun findRun(texts: List<String>, from: Int, want: String): IntRange? {
        for (s in from until texts.size) {
            if (texts[s].isEmpty() || !want.startsWith(texts[s])) continue
            var acc = ""
            var k = s
            while (k < texts.size && acc.length < want.length) {
                if (texts[k].isNotEmpty()) {
                    if (!want.startsWith(acc + texts[k])) break
                    acc += texts[k]
                }
                if (acc == want) return s..k
                k++
            }
        }
        return null
    }

    /** For each new block the old block it is equal to in the longest common sequence. */
    private fun lcs(a: List<String>, b: List<String>): Array<Int?> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = arrayOfNulls<Int>(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out[j] = i; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return out
    }

    /** The style ids of the original for „heading 1“ … „heading 4“ and „Title“ (German Word: „berschrift1“ …). */
    private fun headingStyles(styles: String?): Map<String, String> {
        styles ?: return emptyMap()
        val out = HashMap<String, String>()
        Regex("<w:style\\b[^>]*w:styleId=\"([^\"]+)\"[^>]*>.*?<w:name w:val=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL).findAll(styles).forEach { m ->
            val name = m.groupValues[2].lowercase()
            Regex("^heading ([1-6])$").find(name)?.let { out["Heading${it.groupValues[1]}"] = m.groupValues[1] }
            if (name == "title") out["Title"] = m.groupValues[1]
        }
        return out
    }

    /** New paragraphs use the original's heading styles (so they look like the rest and appear in its Inhaltsverzeichnis). */
    private fun restyle(xml: String, ids: Map<String, String>): String = if (ids.isEmpty()) xml
    else Regex("<w:pStyle w:val=\"(Heading[1-6]|Title)\"/>").replace(xml) { m -> ids[m.groupValues[1]]?.let { "<w:pStyle w:val=\"$it\"/>" } ?: m.value }

    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray>? = runCatching {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var total = 0L
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val data = z.readBytes()
                    total += data.size
                    if (total > 300L * 1024 * 1024) return null
                    out[e.name] = data
                }
                e = z.nextEntry
            }
        }
        out.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
