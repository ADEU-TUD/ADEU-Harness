package io.github.thepytales.llmhub.docs

import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The Word-like extras of the document Markdown, shared by the editor and all exporters:
 *
 * - Footnotes: `[^1]` in the text, the note as a line `[^1]: Text` (anywhere, usually at the end).
 * - Images: a line `![Bildunterschrift](Bilder/foto.png){width=50%}` – path relative to the document, absolute or a
 *   data URL; the caption is also the alternative text.
 * - Page break: a line `\newpage`.
 * - Landscape pages: `<!-- quer -->` starts them, `<!-- hoch -->` returns to portrait.
 * - Page setup as comment lines: `<!-- seitenzahlen: aus -->`, `<!-- datum: an -->`, `<!-- kopfzeile: Text -->`.
 *
 * Other `<!-- … -->` lines are notes for the editor (e.g. the literature list) and never exported.
 */
object DocMarkup {
    // ── Page setup ─────────────────────────────────────────────────────────────────────────────────────────

    /** Header and footer: "Seite X von Y" ([pageNumbers]), today's date and a header line. */
    data class PageSetup(val pageNumbers: Boolean = true, val date: Boolean = false, val header: String = "") {
        val hasHeader: Boolean get() = date || header.isNotBlank()
    }

    private val SETTING = Regex("^\\s*<!--\\s*(seitenzahlen|datum|kopfzeile)\\s*:\\s*(.*?)\\s*-->\\s*$", RegexOption.IGNORE_CASE)

    private fun on(v: String) = v.trim().lowercase() !in setOf("aus", "nein", "off", "false", "0", "no")

    fun pageSetup(markdown: String): PageSetup {
        var s = PageSetup()
        for (line in markdown.lineSequence()) {
            val m = SETTING.find(line) ?: continue
            val v = m.groupValues[2]
            s = when (m.groupValues[1].lowercase()) {
                "seitenzahlen" -> s.copy(pageNumbers = on(v))
                "datum" -> s.copy(date = on(v))
                else -> s.copy(header = v.trim())
            }
        }
        return s
    }

    /** [markdown] with [setup] as setting lines at the end (only what differs from the default). */
    fun withPageSetup(markdown: String, setup: PageSetup): String {
        val body = markdown.lines().filterNot { SETTING.matches(it) }.joinToString("\n").trimEnd()
        val lines = buildList {
            if (!setup.pageNumbers) add("<!-- seitenzahlen: aus -->")
            if (setup.date) add("<!-- datum: an -->")
            if (setup.header.isNotBlank()) add("<!-- kopfzeile: ${setup.header.replace("-->", "").replace("\n", " ").trim()} -->")
        }
        return if (lines.isEmpty()) "$body\n" else body + "\n\n" + lines.joinToString("\n\n") + "\n"
    }

    /** Today in German ("26.10.2026"), for header and footer. */
    fun today(): String = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.GERMANY).format(Date())

    // ── Markers ────────────────────────────────────────────────────────────────────────────────────────────

    private val COMMENT = Regex("^\\s*<!--.*-->\\s*$")
    private val LANDSCAPE = Regex("^\\s*<!--\\s*(quer|querformat|landscape)\\s*-->\\s*$", RegexOption.IGNORE_CASE)
    private val PORTRAIT = Regex("^\\s*<!--\\s*(hoch|hochformat|portrait)\\s*-->\\s*$", RegexOption.IGNORE_CASE)

    /** A line (or block) that is only an HTML comment: never shown in exported documents. */
    fun isComment(text: String): Boolean = text.isNotBlank() && text.lines().all { it.isBlank() || COMMENT.matches(it) }

    fun isPageBreak(text: String): Boolean = text.trim().let { it == "\\newpage" || it == "\\pagebreak" || it.equals("<!-- seitenumbruch -->", true) }

    /** true = landscape starts here, false = portrait again, null = no orientation marker. */
    fun orientation(text: String): Boolean? = when {
        LANDSCAPE.matches(text) -> true
        PORTRAIT.matches(text) -> false
        else -> null
    }

    const val PAGE_BREAK = "\\newpage"
    const val LANDSCAPE_START = "<!-- quer -->"
    const val LANDSCAPE_END = "<!-- hoch -->"

    private val FENCE = Regex("^\\s*```")

    /**
     * Puts marker lines, image lines and footnote definitions into blocks of their own, so the Markdown parser does
     * not glue them to the text around them.
     */
    fun isolate(markdown: String): String {
        val out = StringBuilder()
        var code = false
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (FENCE.containsMatchIn(line)) code = !code
            val special = !code && (COMMENT.matches(line) || isPageBreak(line) || image(line) != null)
            if (special) out.append('\n').append(line.trim()).append("\n\n") else out.append(line).append('\n')
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n")
    }

    /** The Markdown without editor notes (comment lines); markers for page breaks and orientation stay. */
    fun stripComments(markdown: String): String = markdown.lines()
        .filter { !COMMENT.matches(it) || orientation(it) != null || isPageBreak(it) }.joinToString("\n")

    // ── Footnotes ──────────────────────────────────────────────────────────────────────────────────────────

    data class Note(val label: String, val number: Int, val text: String)

    /** [body] is the Markdown without the definitions; [notes] are numbered in the order of their first use. */
    data class Footnotes(val body: String, val notes: List<Note>) {
        val numbers: Map<String, Int> by lazy { notes.associate { it.label to it.number } }
    }

    val NOTE_REF = Regex("\\[\\^([^\\]\\s]+)](?!:)")
    private val NOTE_DEF = Regex("^\\s{0,3}\\[\\^([^\\]\\s]+)]:\\s?(.*)$")

    fun footnotes(markdown: String): Footnotes {
        val defs = LinkedHashMap<String, String>()
        val body = StringBuilder()
        var code = false
        var current: String? = null
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (FENCE.containsMatchIn(line)) { code = !code; current = null }
            val def = if (code) null else NOTE_DEF.find(line)
            when {
                def != null -> {
                    current = def.groupValues[1]
                    defs[current] = def.groupValues[2].trim()
                }
                // Indented lines continue the note above.
                current != null && line.isNotBlank() && (line.startsWith("  ") || line.startsWith("\t")) ->
                    defs[current] = (defs[current].orEmpty() + " " + line.trim()).trim()
                else -> {
                    if (line.isNotBlank()) current = null
                    body.append(line).append('\n')
                }
            }
        }
        if (defs.isEmpty()) return Footnotes(markdown, emptyList())
        val order = mutableListOf<String>()
        code = false
        for (line in body.lines()) {
            if (FENCE.containsMatchIn(line)) code = !code
            if (!code) NOTE_REF.findAll(line).forEach { m -> val l = m.groupValues[1]; if (l in defs && l !in order) order += l }
        }
        defs.keys.filter { it !in order }.forEach { order += it }
        val notes = order.mapIndexed { i, l -> Note(l, i + 1, defs[l].orEmpty()) }
        return Footnotes(body.toString().trimEnd() + "\n", notes)
    }

    /** Numbers of the footnotes of [markdown] (label → number), for the editor. */
    fun noteNumbers(markdown: String): Map<String, Int> = footnotes(markdown).numbers

    /** Splits [text] at footnote references: plain pieces (number null) and references (with their number). */
    fun splitNotes(text: String, numbers: Map<String, Int>): List<Pair<String, Int?>> {
        if (numbers.isEmpty() || "[^" !in text) return listOf(text to null)
        val out = mutableListOf<Pair<String, Int?>>()
        var last = 0
        for (m in NOTE_REF.findAll(text)) {
            val n = numbers[m.groupValues[1]] ?: continue
            if (m.range.first > last) out += text.substring(last, m.range.first) to null
            out += "" to n
            last = m.range.last + 1
        }
        if (last < text.length) out += text.substring(last) to null
        return out
    }

    /** Next free numeric label for a new footnote. */
    fun nextNoteLabel(markdown: String): String {
        val used = (NOTE_REF.findAll(markdown).map { it.groupValues[1] } + NOTE_DEF.findAll(markdown).map { it.groupValues[1] }).toSet()
        var n = 1
        while ("$n" in used) n++
        return "$n"
    }

    /** "¹²³" for the editor preview. */
    fun superscript(n: Int): String = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

    // ── Images ─────────────────────────────────────────────────────────────────────────────────────────────

    /** An image line: caption (= alternative text), source and width in percent of the text width. */
    data class Image(val alt: String, val src: String, val width: Int? = null) {
        fun markdown(): String = "![${alt.replace("]", ")").replace("\n", " ")}]($src)" + (width?.let { "{width=$it%}" } ?: "")
    }

    private val IMAGE = Regex("^\\s*!\\[([^\\]]*)]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)\\s*(\\{[^}]*\\})?\\s*$")
    private val WIDTH = Regex("width\\s*=\\s*\"?(\\d{1,3})\\s*%?", RegexOption.IGNORE_CASE)

    /** The image if [text] (a line or block) is exactly one image. */
    fun image(text: String): Image? {
        if ('\n' in text.trim()) return null
        val m = IMAGE.find(text) ?: return null
        val width = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { WIDTH.find(it)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 100) }
        return Image(m.groupValues[1].trim(), m.groupValues[2], width)
    }

    private const val MAX_IMAGE = 30L * 1024 * 1024

    /** Bytes of an image (data URL, absolute path or path relative to [base]); null unless it is a real image. */
    fun imageBytes(src: String, base: File? = null): ByteArray? {
        val bytes = runCatching {
            when {
                src.startsWith("data:", true) -> {
                    val comma = src.indexOf(',')
                    if (comma < 0 || !src.substring(0, comma).contains("base64", true)) null
                    else java.util.Base64.getMimeDecoder().decode(src.substring(comma + 1))
                }
                src.startsWith("http://", true) || src.startsWith("https://", true) -> null
                else -> {
                    val path = java.net.URLDecoder.decode(src.removePrefix("file://").replace("+", "%2B"), "UTF-8")
                    val f = File(path).let { if (it.isAbsolute) it else if (base != null) File(base, path) else null }
                    f?.takeIf { it.isFile && it.length() in 1..MAX_IMAGE }?.readBytes()
                }
            }
        }.getOrNull() ?: return null
        return bytes.takeIf { imageType(it) != null }
    }

    /** "png", "jpeg", "gif" or "bmp" from the first bytes; null for anything else. */
    fun imageType(b: ByteArray): String? = when {
        b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> "png"
        b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "jpeg"
        b.size > 6 && String(b, 0, 4, Charsets.ISO_8859_1) == "GIF8" -> "gif"
        b.size > 26 && b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
        else -> null
    }

    fun mime(type: String) = "image/$type"

    /** Width and height in pixels from the header (PNG, JPEG, GIF, BMP). */
    fun imageSize(b: ByteArray): Pair<Int, Int>? = runCatching {
        fun u16be(i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun u16le(i: Int) = ((b[i + 1].toInt() and 0xFF) shl 8) or (b[i].toInt() and 0xFF)
        fun i32be(i: Int) = (u16be(i) shl 16) or u16be(i + 2)
        fun i32le(i: Int) = (u16le(i + 2) shl 16) or u16le(i)
        when (imageType(b)) {
            "png" -> i32be(16) to i32be(20)
            "gif" -> u16le(6) to u16le(8)
            "bmp" -> i32le(18) to kotlin.math.abs(i32le(22))
            "jpeg" -> {
                var i = 2
                var found: Pair<Int, Int>? = null
                while (i + 9 < b.size && found == null) {
                    if (b[i] != 0xFF.toByte()) { i++; continue }
                    val marker = b[i + 1].toInt() and 0xFF
                    if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) found = u16be(i + 7) to u16be(i + 5)
                    else if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) i += 2
                    else i += 2 + u16be(i + 2)
                }
                found
            }
            else -> null
        }
    }.getOrNull()?.takeIf { it.first > 0 && it.second > 0 }

    fun dataUrl(bytes: ByteArray): String = "data:${mime(imageType(bytes) ?: "png")};base64," + java.util.Base64.getEncoder().encodeToString(bytes)

    /** Folder (relative to the document) where the editor keeps inserted and imported pictures. */
    const val IMAGE_DIR = "Bilder"

    /**
     * Saves picture [bytes] in "[base]/Bilder" (named after their content, so the same picture is stored once) and
     * returns the path to write into the Markdown.
     */
    fun storeImage(base: File, bytes: ByteArray): String {
        val type = imageType(bytes) ?: "png"
        val hash = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val name = "$IMAGE_DIR/bild-$hash.${if (type == "jpeg") "jpg" else type}"
        val f = File(base, name)
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        return name
    }

    /** Image paths relative to [base] made absolute, so the exporters find the files. */
    fun resolveImages(markdown: String, base: File?): String {
        if (base == null || "![" !in markdown) return markdown
        return markdown.lines().joinToString("\n") { line ->
            val img = image(line) ?: return@joinToString line
            if (img.src.startsWith("data:", true) || File(img.src).isAbsolute || img.src.contains("://")) return@joinToString line
            val f = File(base, img.src)
            if (f.isFile) img.copy(src = f.absolutePath).markdown() else line
        }
    }

    /** Relative paths of all pictures [markdown] uses (to copy them along when a document moves). */
    fun imagePaths(markdown: String): List<String> = markdown.lines().mapNotNull { image(it)?.src }
        .filter { !it.startsWith("data:", true) && !File(it).isAbsolute && !it.contains("://") }.distinct()
}
