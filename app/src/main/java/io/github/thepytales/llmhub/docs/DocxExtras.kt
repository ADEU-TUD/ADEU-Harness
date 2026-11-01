package io.github.thepytales.llmhub.docs

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The .docx of an editor document (AP-16 Stufe 1): the text with title, pictures and formulas, the open suggestions of
 * „Änderungen nachverfolgen“ as Word revisions (accept or reject them in Word) and the open comments as Word
 * comments. Reading a .docx brings both back: its comments become comments of the editor, its revisions suggestions.
 */
object DocxExtras {
    private const val EMPTY = "​"
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val COMMENTS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments"
    private const val COMMENTS_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"

    /** The Markdown Word gets: title on top, pictures resolved, charts as tables (formulas as Word formulas, see LatexOmml). */
    fun markdown(doc: LiveDoc, base: File?, text: String = doc.markdown()): String =
        ChartBlocks.asTables(DocMarkup.resolveImages(DocumentWriter.withTitle(doc.title, text.replace(EMPTY, "")), base))

    /** The text of the base of „Änderungen nachverfolgen“ (all tabs like [LiveDoc.markdown]). */
    private fun baseText(doc: LiveDoc): String? {
        val bases = doc.trackBase ?: return null
        val tabs = doc.tabs.map { t -> t.copy(markdown = bases[t.id] ?: t.markdown) }
        return doc.copy(tabs = tabs).markdown()
    }

    /** Whether [doc] has suggestions that must be written as revisions. */
    fun hasRevisions(doc: LiveDoc): Boolean = baseText(doc)?.let { it.replace(EMPTY, "") != doc.markdown().replace(EMPTY, "") } == true

    /** Writes the .docx of [doc]; returns the Word ids given to its comments (comment id → Word id). */
    fun write(file: File, doc: LiveDoc, theme: DocTheme, base: File?): Map<String, String> {
        val now = markdown(doc, base)
        val old = baseText(doc)?.let { markdown(doc, base, it) }
        if (old != null && old != now) MarkdownDocx.writeTracked(file, doc.title, old, now, theme, author = "Lora")
        else MarkdownDocx.write(file, doc.title, now, theme)
        val open = doc.comments.filter { !it.resolved }
        if (open.isEmpty()) return emptyMap()
        val (bytes, ids) = withComments(file.readBytes(), open)
        file.writeBytes(bytes)
        return ids
    }

    // ── Comments ──────────────────────────────────────────────────────────────────────────────────────────

    data class WordComment(val id: String, val author: String, val text: String, val quote: String)

    private fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) out[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }
        return out
    }

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

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun unesc(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private val PARAGRAPH = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
    private val TEXT = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)

    private fun plain(xml: String) = TEXT.findAll(xml).joinToString("") { unesc(it.groupValues[1]) }

    private fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim().lowercase()

    /**
     * [docx] with [comments] as Word comments, each anchored at the paragraph that contains its quoted text (else the
     * first one). Comments already in the file stay. Returns the bytes and the Word id of each comment.
     */
    fun withComments(docx: ByteArray, comments: List<DocComment>): Pair<ByteArray, Map<String, String>> {
        val files = entries(docx)
        var document = files["word/document.xml"]?.toString(Charsets.UTF_8) ?: return docx to emptyMap()
        val existing = files["word/comments.xml"]?.toString(Charsets.UTF_8)
        var next = (Regex("w:id=\"(\\d+)\"").findAll(existing.orEmpty()).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 99) + 1
        val ids = LinkedHashMap<String, String>()
        val xml = StringBuilder()
        val date = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        for (c in comments) {
            val id = (next++).toString()
            val quote = norm(DocBlocks.plainOf(c.quote)).take(80)
            val paragraphs = PARAGRAPH.findAll(document).toList()
            val target = paragraphs.firstOrNull { quote.isNotEmpty() && norm(plain(it.value)).contains(quote) }
                ?: paragraphs.firstOrNull { plain(it.value).isNotBlank() } ?: continue
            val p = target.value
            val openEnd = p.indexOf("</w:pPr>").takeIf { it >= 0 }?.let { it + "</w:pPr>".length } ?: (p.indexOf('>') + 1)
            val anchored = p.substring(0, openEnd) + "<w:commentRangeStart w:id=\"$id\"/>" + p.substring(openEnd, p.length - "</w:p>".length) +
                "<w:commentRangeEnd w:id=\"$id\"/><w:r><w:commentReference w:id=\"$id\"/></w:r></w:p>"
            document = document.substring(0, target.range.first) + anchored + document.substring(target.range.last + 1)
            val initials = c.author.split(' ', '·').mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifEmpty { "L" }
            xml.append("<w:comment w:id=\"$id\" w:author=\"${esc(c.author)}\" w:date=\"$date\" w:initials=\"${esc(initials)}\">")
            fun para(t: String) = xml.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(t)).append("</w:t></w:r></w:p>")
            c.text.lines().forEach { para(it) }
            c.replies.forEach { r -> para("${r.author}: ${r.text}") }
            xml.append("</w:comment>")
            ids[c.id] = id
        }
        if (ids.isEmpty()) return docx to emptyMap()
        files["word/document.xml"] = document.toByteArray()
        files["word/comments.xml"] = (
            if (existing != null) existing.replace("</w:comments>", "$xml</w:comments>")
            else "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:comments xmlns:w=\"$W\">$xml</w:comments>"
            ).toByteArray()
        val rels = files["word/_rels/document.xml.rels"]?.toString(Charsets.UTF_8)
        if (rels != null && COMMENTS_REL !in rels) {
            files["word/_rels/document.xml.rels"] = rels.replace("</Relationships>", "<Relationship Id=\"rIdLoraComments\" Type=\"$COMMENTS_REL\" Target=\"comments.xml\"/></Relationships>").toByteArray()
        }
        val types = files["[Content_Types].xml"]?.toString(Charsets.UTF_8)
        if (types != null && "/word/comments.xml" !in types) {
            files["[Content_Types].xml"] = types.replace("</Types>", "<Override PartName=\"/word/comments.xml\" ContentType=\"$COMMENTS_TYPE\"/></Types>").toByteArray()
        }
        return zip(files) to ids
    }

    /** The comments of a .docx with the text they are anchored to. */
    fun readComments(docx: ByteArray): List<WordComment> = runCatching {
        val files = entries(docx)
        val comments = files["word/comments.xml"]?.toString(Charsets.UTF_8) ?: return@runCatching emptyList()
        val document = files["word/document.xml"]?.toString(Charsets.UTF_8).orEmpty()
        Regex("<w:comment\\b([^>]*)>(.*?)</w:comment>", RegexOption.DOT_MATCHES_ALL).findAll(comments).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val id = Regex("w:id=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val author = Regex("w:author=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)?.let(::unesc).orEmpty().ifBlank { "Word" }
            val text = PARAGRAPH.findAll(m.groupValues[2]).joinToString("\n") { plain(it.value) }.trim()
            val start = document.indexOf("<w:commentRangeStart w:id=\"$id\"/>")
            val end = document.indexOf("<w:commentRangeEnd w:id=\"$id\"/>")
            val quote = if (start in 0 until end) plain(document.substring(start, end)).trim() else ""
            if (text.isEmpty()) null else WordComment(id, author, text, quote)
        }.toList()
    }.getOrDefault(emptyList())

    // ── Revisions ─────────────────────────────────────────────────────────────────────────────────────────

    private val INS = Regex("<w:ins\\b[^>]*?(?:/>|>.*?</w:ins>)", RegexOption.DOT_MATCHES_ALL)

    /** Whether the .docx has revisions (inserted or deleted text). */
    fun hasTrackedChanges(docx: ByteArray): Boolean = runCatching {
        val d = entries(docx)["word/document.xml"]?.toString(Charsets.UTF_8).orEmpty()
        "<w:ins " in d || "<w:del " in d
    }.getOrDefault(false)

    /** The .docx as it was before its revisions: insertions left out, deletions kept as text. */
    fun beforeRevisions(docx: ByteArray): ByteArray {
        val files = entries(docx)
        val d = files["word/document.xml"]?.toString(Charsets.UTF_8) ?: return docx
        val before = INS.replace(d, "")
            .replace(Regex("<w:del\\b[^>]*>"), "").replace("</w:del>", "")
            .replace("<w:delText", "<w:t").replace("</w:delText>", "</w:t>")
        files["word/document.xml"] = before.toByteArray()
        return zip(files)
    }

    /**
     * What a .docx brings into its document: Word comments as comments (not twice), revisions as suggestions of
     * „Änderungen nachverfolgen“ (the text before them becomes the base).
     */
    fun adopt(doc: LiveDoc, docx: ByteArray): LiveDoc {
        var d = doc
        val tab = d.tabs.firstOrNull() ?: return d
        val known = d.comments.mapNotNull { it.wordId }.toSet()
        val fresh = readComments(docx).filter { it.id !in known && d.comments.none { c -> c.text == it.text && c.quote.contains(it.quote) } }
        if (fresh.isNotEmpty()) d = d.copy(comments = d.comments + fresh.map { w -> DocComment(tabId = tab.id, quote = w.quote, text = w.text, author = w.author, wordId = w.id) })
        if (hasTrackedChanges(docx)) {
            val base = runCatching { OfficeImport.docx(beforeRevisions(docx)) }.getOrNull()
            if (base != null && base != tab.markdown) d = d.copy(trackBase = mapOf(tab.id to base))
        }
        return d
    }
}

/** The .pptx of a presentation of the editor (all tabs as one deck). */
object SlideFiles {
    fun write(file: File, doc: LiveDoc, theme: DocTheme, base: File? = null) {
        PptxWriter.write(file, doc.title, doc.tabs.joinToString("\n\n---\n\n") { it.markdown }, theme.unit, theme.footer, base)
    }
}
