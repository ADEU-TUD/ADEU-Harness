package io.github.thepytales.llmhub.docs

import android.content.Context
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class DocFormat(val ext: String, val mime: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (.docx)"),
    HTML("html", "text/html", "HTML"),
    MD("md", "text/markdown", "Markdown"),
    TXT("txt", "text/plain", "Text"),
    ;

    companion object {
        fun parse(name: String?): DocFormat {
            val n = name?.trim()?.lowercase()?.removePrefix(".").orEmpty()
            return entries.firstOrNull { it.ext == n || it.name.equals(n, ignoreCase = true) }
                ?: when (n) {
                    "word", "doc" -> DOCX
                    "markdown" -> MD
                    "text", "plain" -> TXT
                    "htm" -> HTML
                    else -> PDF
                }
        }

        fun ofFile(file: File) = entries.firstOrNull { it.ext == file.extension.lowercase() } ?: TXT
    }
}

/** Letter layout: sender block next to the logo, address field and subject (the title). Lines are separated by \n. */
data class Letter(val sender: String, val recipient: String)

/** [designed] is false when the PDF had to fall back to the simple renderer. */
data class CreatedDocument(val file: File, val format: DocFormat, val pages: Int?, val designed: Boolean = true)

/** Creates documents from Markdown or HTML in the app's private storage. */
object DocumentWriter {
    fun safeName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").replace(Regex("\\s+"), " ").take(80).trim('.', ' ')
            .ifBlank { "Dokument" }

    /** Whether [content] is a complete HTML document (with its own design) instead of Markdown. */
    fun isHtml(content: String): Boolean {
        val t = content.trimStart().take(400).lowercase()
        return t.startsWith("<!doctype html") || t.startsWith("<html") || ("<body" in content.lowercase() && "</" in content)
    }

    /**
     * Writes the document into [folder]; an existing file with the same name is not overwritten.
     * [content] is Markdown, or for PDF/HTML also a full HTML page with CSS.
     */
    suspend fun create(
        context: Context,
        folder: File,
        title: String,
        content: String,
        format: DocFormat,
        fileName: String? = null,
        theme: DocTheme = DocTheme(),
        letter: Letter? = null,
    ): CreatedDocument {
        @Suppress("NAME_SHADOWING") val theme = TudBrand.apply(theme)
        val base = safeName(fileName?.substringBeforeLast('.')?.ifBlank { null } ?: title)
        val file = withContext(Dispatchers.IO) {
            folder.mkdirs()
            var f = File(folder, "$base.${format.ext}")
            var n = 2
            while (f.exists()) f = File(folder, "$base ($n).${format.ext}").also { n++ }
            f
        }
        val html = isHtml(content)
        // Word, Markdown and text cannot keep an HTML design; they get its text structure.
        val markdown = when {
            html -> WebTools.htmlToText(content)
            letter != null -> letterAsMarkdown(letter, content, theme)
            else -> content
        }
        var pages: Int? = null
        var designed = true
        when (format) {
            DocFormat.PDF -> {
                val page = if (html) content else toHtml(title, content, theme, print = true, letter = letter)
                pages = try {
                    HtmlPdf.write(context, file, page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // WebView printing is not available on every device: use the built-in renderer.
                    designed = false
                    withContext(Dispatchers.IO) { MarkdownPdf.write(file, title, markdown, theme) }
                }
            }
            DocFormat.DOCX -> withContext(Dispatchers.IO) { MarkdownDocx.write(file, title, markdown, theme) }
            DocFormat.HTML -> withContext(Dispatchers.IO) { file.writeText(if (html) content else toHtml(title, content, theme, print = false, letter = letter)) }
            DocFormat.MD -> withContext(Dispatchers.IO) { file.writeText(withTitle(title, markdown)) }
            DocFormat.TXT -> withContext(Dispatchers.IO) { file.writeText(toText(title, markdown)) }
        }
        return CreatedDocument(file, format, pages, designed)
    }

    /** Sender, address and date in front of the text, for formats without the letter layout (Word, text). */
    private fun letterAsMarkdown(letter: Letter, body: String, theme: DocTheme): String = buildString {
        fun lines(t: String) = t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  \n")
        if (letter.sender.isNotBlank()) append(lines(letter.sender)).append("\n\n")
        if (theme.style == DocStyle.TUD) append("*").append(TudBrand.POSTAL).append("*\n\n")
        if (letter.recipient.isNotBlank()) append(lines(letter.recipient)).append("\n\n")
        append("Dresden, ").append(DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())).append("\n\n")
        append(body)
    }

    /** Adds the title as top-level heading unless the Markdown already starts with one. */
    internal fun withTitle(title: String, markdown: String): String =
        if (title.isBlank() || markdown.trimStart().startsWith("# ")) markdown else "# $title\n\n$markdown"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Runs of 4+ underscores are fill-in lines of forms; protected from the Markdown parser by private-use marks. */
    private val FILL_LINE = Regex("_{4,}")
    private val FILL_MARK = Regex("\uE000(\\d+)\uE001")

    private fun htmlInline(text: String) =
        inlineSegments(text.replace(FILL_LINE) { "\uE000${it.value.length}\uE001" })
            .joinToString("") { seg -> htmlSeg(seg) }
            .replace(FILL_MARK) { "<span class=\"fill\" style=\"width:${(it.groupValues[1].toInt() * 0.5).coerceIn(3.0, 30.0)}em\"></span>" }

    private val CHECKBOX = Regex("^\\[([ xX])]\\s+")

    private fun htmlSeg(seg: Segment): String {
        var out = esc(seg.text)
        if (seg.code) out = "<code>$out</code>"
        if (seg.bold) out = "<strong>$out</strong>"
        if (seg.italic) out = "<em>$out</em>"
        if (seg.strike) out = "<del>$out</del>"
        if (seg.link != null) out = "<a href=\"${esc(seg.link)}\">$out</a>"
        return out
    }

    private enum class Callout(val label: String, val color: String) {
        NOTE("Hinweis", "#2563EB"), TIP("Tipp", "#15803D"), IMPORTANT("Wichtig", "#7C3AED"),
        WARNING("Achtung", "#B45309"), CAUTION("Vorsicht", "#B91C1C"),
    }

    private val CALLOUT = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION|HINWEIS|TIPP|WICHTIG|ACHTUNG|VORSICHT)]\\s*", RegexOption.IGNORE_CASE)

    private fun callout(text: String): Pair<Callout, String>? {
        val m = CALLOUT.find(text) ?: return null
        val kind = when (m.groupValues[1].uppercase()) {
            "NOTE", "HINWEIS" -> Callout.NOTE
            "TIP", "TIPP" -> Callout.TIP
            "IMPORTANT", "WICHTIG" -> Callout.IMPORTANT
            "WARNING", "ACHTUNG" -> Callout.WARNING
            else -> Callout.CAUTION
        }
        return kind to text.substring(m.range.last + 1)
    }

    private fun cssString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    /** Stylesheet for a document style; [a] is the accent color. */
    private fun css(theme: DocTheme, print: Boolean): String {
        val a = theme.accent
        val tint = a + "14"
        val serif = "'Noto Serif', Georgia, 'Times New Roman', serif"
        val sans = "Roboto, 'Helvetica Neue', Arial, sans-serif"
        val s = theme.style
        val tudFont = "'Open Sans', 'Segoe UI', Roboto, Arial, sans-serif"
        val bodyFont = when (s) {
            DocStyle.CLASSIC -> serif
            DocStyle.TUD -> tudFont
            else -> sans
        }
        val headFont = when (s) {
            DocStyle.CLASSIC, DocStyle.ELEGANT -> serif
            DocStyle.TUD -> tudFont
            else -> sans
        }
        val headColor = when (s) {
            DocStyle.CLASSIC, DocStyle.MINIMAL -> "#111"
            else -> a
        }
        return buildString {
            if (print) {
                append("@page{size:A4;margin:18mm 17mm 20mm 17mm;")
                append("@bottom-right{content:counter(page) \" / \" counter(pages);font:8pt $sans;color:#8a8f98}")
                if (theme.footer.isNotBlank()) append("@bottom-left{content:${cssString(theme.footer)};font:8pt $sans;color:#8a8f98}")
                append("}")
            } else {
                append("body{max-width:800px;margin:2rem auto;padding:0 1.2rem}")
            }
            append("html{-webkit-print-color-adjust:exact;print-color-adjust:exact}")
            append("body{font-family:$bodyFont;font-size:10.5pt;line-height:1.55;color:#1f2328}")
            append("h1,h2,h3,h4{font-family:$headFont;line-height:1.25;break-after:avoid;page-break-after:avoid}")
            append("h2{font-size:15pt;margin:20pt 0 6pt;color:$headColor}")
            append("h3{font-size:12.5pt;margin:14pt 0 4pt;color:${if (s == DocStyle.BOLD) a else "#1f2328"}}")
            append("h4{font-size:11pt;margin:12pt 0 4pt}")
            append("h1{font-size:20pt;margin:18pt 0 8pt;color:$headColor}")
            append("p{margin:0 0 8pt}ul,ol{margin:0 0 8pt;padding-left:18pt}li{margin:2pt 0}")
            append("a{color:$a;text-decoration:none}strong{font-weight:700}")
            append("code{font-family:'Roboto Mono',monospace;font-size:9pt;background:#f2f4f7;padding:1pt 3pt;border-radius:3pt}")
            append("pre{background:#f6f8fa;border:1px solid #e5e7eb;border-radius:6pt;padding:8pt 10pt;white-space:pre-wrap;font-size:8.5pt;break-inside:avoid}")
            append("pre code{background:none;padding:0}")
            append("table{border-collapse:collapse;width:100%;margin:8pt 0 12pt;font-size:9.5pt;break-inside:auto}")
            append("tr{break-inside:avoid}th,td{padding:5pt 7pt;text-align:left;vertical-align:top}")
            append("hr{border:none;border-top:1px solid #d9dde3;margin:14pt 0}")
            append("blockquote{margin:8pt 0;padding:6pt 12pt;border-left:3pt solid ${if (s == DocStyle.MINIMAL || s == DocStyle.CLASSIC) "#999" else a};background:${if (s == DocStyle.MINIMAL) "transparent" else tint};color:#374151}")
            append(".callout{margin:10pt 0;padding:8pt 12pt;border-radius:6pt;border:1px solid;break-inside:avoid}")
            append(".callout .label{font-weight:700;margin-bottom:2pt;font-size:9.5pt;text-transform:uppercase;letter-spacing:.04em}")
            append(".header{display:flex;align-items:center;justify-content:space-between;gap:16pt;margin-bottom:16pt}")
            append(".header img{max-height:52pt;max-width:170pt;object-fit:contain}")
            append(".meta{color:#6b7280;font-size:9pt;margin-top:3pt}")
            append(".title{font-family:$headFont;font-size:24pt;font-weight:700;line-height:1.2;margin:0}")
            // Forms: checkboxes, fill-in lines and empty table cells to write in.
            append("li.task{list-style:none;margin-left:-14pt}")
            append(".cb{display:inline-block;width:8.5pt;height:8.5pt;border:1pt solid #333;border-radius:1.5pt;margin-right:6pt;vertical-align:-1pt;text-align:center;line-height:8.5pt;font-size:8pt}")
            append(".fill{display:inline-block;border-bottom:0.8pt solid #555;height:1.1em;vertical-align:bottom;max-width:100%}")
            append("td:empty{height:20pt}td:only-child:empty{height:70pt}")
            // Letters
            append(".letter-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16pt;margin-bottom:10pt}")
            append(".letter-head img{max-height:44pt;max-width:170pt;object-fit:contain}")
            append(".sender{text-align:right;font-size:8.5pt;line-height:1.45;color:#333}")
            append(".address{width:85mm;min-height:40mm;margin:16pt 0 18pt;font-size:10.5pt;line-height:1.4}")
            append(".postal{font-size:7pt;color:#666;border-bottom:0.5pt solid #999;padding-bottom:2pt;margin-bottom:6pt}")
            append(".letter-date{text-align:right;margin-bottom:16pt}")
            append(".subject{font-weight:700;font-size:11pt;margin-bottom:16pt}")
            when (s) {
                DocStyle.MODERN -> {
                    append(".bar{height:5pt;background:$a;border-radius:3pt;margin-bottom:14pt}")
                    append(".title{color:#111827}")
                    append("h2{padding-bottom:3pt;border-bottom:1.5pt solid $tint}")
                    append("th{background:$a;color:#fff;font-weight:600}td{border-bottom:1px solid #e5e7eb}tr:nth-child(even) td{background:#f9fafb}")
                }
                DocStyle.BOLD -> {
                    append(".hero{background:$a;color:#fff;border-radius:10pt;padding:18pt 20pt;margin-bottom:18pt;display:flex;align-items:center;justify-content:space-between;gap:16pt}")
                    append(".hero .title{color:#fff}.hero .meta{color:rgba(255,255,255,.8)}")
                    append(".hero img{max-height:48pt;max-width:150pt;background:#fff;border-radius:8pt;padding:6pt}")
                    append("h2{background:$tint;border-left:4pt solid $a;padding:4pt 8pt;border-radius:0 4pt 4pt 0}")
                    append("th{background:$a;color:#fff}td{border:1px solid #e5e7eb}tr:nth-child(even) td{background:$tint}")
                }
                DocStyle.ELEGANT -> {
                    append(".title{color:$a;font-weight:400;letter-spacing:.02em;font-size:26pt}")
                    append(".rule{height:1px;background:$a;margin:6pt 0 18pt}")
                    append("h2{font-weight:400;letter-spacing:.02em}")
                    append("th{border-bottom:1.5pt solid $a;color:$a;font-weight:600}td{border-bottom:1px solid #e5e7eb}")
                }
                DocStyle.CLASSIC -> {
                    append(".classic{text-align:center;margin-bottom:18pt}.classic img{max-height:56pt;max-width:180pt;margin-bottom:8pt}")
                    append(".classic .title{font-size:22pt}.double{border-top:3px double #333;margin-top:10pt}")
                    append("h2{font-variant:small-caps;letter-spacing:.03em}")
                    append("th{border-bottom:1.5pt solid #333;font-weight:700}td{border-bottom:1px solid #ccc}")
                }
                DocStyle.MINIMAL -> {
                    append("th{border-bottom:1px solid #333;font-weight:600}td{border-bottom:1px solid #e5e7eb}")
                }
                DocStyle.TUD -> {
                    append(".tud-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16pt;margin-bottom:12pt}")
                    append(".tud-head img{height:42pt;max-width:180pt;object-fit:contain}")
                    append(".tud-rule{height:2.2pt;background:$a;margin:0 0 16pt}")
                    append(".title{color:$a;font-size:21pt}.meta{color:#555}")
                    append("h2{font-size:13.5pt;padding-bottom:3pt;border-bottom:1pt solid ${a}33}h3{color:$a}")
                    append("th{background:$a;color:#fff;font-weight:600}td{border:0.8pt solid #c9cbe3}")
                    append("tr:nth-child(even) td{background:#f5f5fb}.cb{border-color:$a}.sender{color:$a}")
                    append(".subject{color:$a}")
                }
            }
        }
    }

    private fun linesHtml(text: String) =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("<br>") { htmlInline(it) }

    private fun letterBlock(title: String, theme: DocTheme, letter: Letter): String {
        val logo = theme.logoDataUrl?.let { "<img src=\"$it\" alt=\"Logo\">" }.orEmpty()
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())
        val postal = if (theme.style == DocStyle.TUD) "<div class=\"postal\">${esc(TudBrand.POSTAL)}</div>" else ""
        return "<div class=\"letter-head\">$logo<div class=\"sender\">${linesHtml(letter.sender)}</div></div>" +
            (if (theme.style == DocStyle.TUD) "<div class=\"tud-rule\"></div>" else "") +
            "<div class=\"address\">$postal${linesHtml(letter.recipient)}</div>" +
            "<div class=\"letter-date\">Dresden, $date</div>" +
            (if (title.isBlank()) "" else "<div class=\"subject\">${htmlInline(title)}</div>")
    }

    private fun titleBlock(title: String, theme: DocTheme): String {
        if (title.isBlank() && theme.logo == null) return ""
        val logo = theme.logoDataUrl?.let { "<img src=\"$it\" alt=\"Logo\">" }.orEmpty()
        val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.GERMANY).format(Date())
        val t = if (title.isBlank()) "" else "<div class=\"title\">${htmlInline(title)}</div>"
        val meta = "<div class=\"meta\">$date</div>"
        return when (theme.style) {
            DocStyle.BOLD -> "<div class=\"hero\"><div>$t$meta</div>$logo</div>"
            DocStyle.CLASSIC -> "<div class=\"classic\">$logo$t$meta<div class=\"double\"></div></div>"
            DocStyle.ELEGANT -> "<div class=\"header\"><div>$t$meta</div>$logo</div><div class=\"rule\"></div>"
            DocStyle.MODERN -> "<div class=\"bar\"></div><div class=\"header\"><div>$t$meta</div>$logo</div>"
            DocStyle.MINIMAL -> "<div class=\"header\"><div>$t$meta</div>$logo</div>"
            DocStyle.TUD -> "<div class=\"tud-head\">$logo</div><div class=\"tud-rule\"></div>$t$meta<div style=\"height:10pt\"></div>"
        }
    }

    /** Converts Markdown into a styled HTML page (also the source for PDFs). */
    fun toHtml(
        title: String,
        markdown: String,
        theme: DocTheme = DocTheme(),
        print: Boolean = false,
        letter: Letter? = null,
    ): String = buildString {
        @Suppress("NAME_SHADOWING") val theme = TudBrand.apply(theme)
        // A leading "# Heading" becomes the title of the document.
        var body = markdown.trimStart()
        var docTitle = title
        if (letter == null) Regex("^#\\s+(.+)").find(body.lineSequence().firstOrNull().orEmpty())?.let {
            docTitle = it.groupValues[1].trim()
            body = body.substringAfter('\n', "")
        }
        append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>${esc(docTitle.ifBlank { "Dokument" })}</title>\n<style>\n")
        append(css(theme, print))
        append("\n</style>\n</head>\n<body>\n")
        append(if (letter != null) letterBlock(docTitle, theme, letter) else titleBlock(docTitle, theme))
        val blocks = parseBlocks(body)
        var openList: String? = null
        fun closeList() {
            openList?.let { append("</$it>\n") }
            openList = null
        }
        for (b in blocks) {
            if (b !is Block.Item) closeList()
            when (b) {
                is Block.Heading -> append("<h${b.level}>${htmlInline(b.text)}</h${b.level}>\n")
                is Block.Paragraph -> append("<p>${b.text.lines().joinToString("<br>\n") { htmlInline(it) }}</p>\n")
                is Block.Item -> {
                    val tag = if (b.marker.first().isDigit()) "ol" else "ul"
                    if (openList != tag) {
                        closeList()
                        append("<$tag>\n")
                        openList = tag
                    }
                    val style = if (b.indent > 0) " style=\"margin-left:${b.indent * 1.5}em\"" else ""
                    val box = CHECKBOX.find(b.text)
                    if (box == null) append("<li$style>${htmlInline(b.text)}</li>\n")
                    else {
                        val mark = if (box.groupValues[1].isBlank()) "" else "✓"
                        append("<li class=\"task\"$style><span class=\"cb\">$mark</span>${htmlInline(b.text.substring(box.range.last + 1))}</li>\n")
                    }
                }
                is Block.Quote -> {
                    val c = callout(b.text)
                    val lines = (c?.second ?: b.text).lines().joinToString("<br>\n") { htmlInline(it) }
                    if (c == null || theme.style == DocStyle.MINIMAL) append("<blockquote>$lines</blockquote>\n")
                    else {
                        val color = c.first.color
                        append("<div class=\"callout\" style=\"border-color:${color}55;background:${color}12\">")
                        append("<div class=\"label\" style=\"color:$color\">${c.first.label}</div>$lines</div>\n")
                    }
                }
                is Block.Code -> append("<pre><code>${esc(b.code)}</code></pre>\n")
                is Block.Table -> {
                    append("<table>\n")
                    b.rows.forEachIndexed { r, row ->
                        val cell = if (r == 0) "th" else "td"
                        append("<tr>")
                        row.forEach { append("<$cell>${htmlInline(it)}</$cell>") }
                        append("</tr>\n")
                    }
                    append("</table>\n")
                }
                Block.Rule -> append("<hr>\n")
            }
        }
        closeList()
        if (!print && theme.footer.isNotBlank()) append("<hr><p class=\"meta\">${esc(theme.footer)}</p>\n")
        append("</body>\n</html>\n")
    }

    private fun plainInline(text: String) = inlineSegments(text).joinToString("") { s ->
        if (s.link != null && s.link != s.text) "${s.text} (${s.link})" else s.text
    }

    fun toText(title: String, markdown: String): String = buildString {
        var prev: Block? = null
        for (b in parseBlocks(withTitle(title, markdown))) {
            if (prev is Block.Item && b !is Block.Item) append('\n')
            prev = b
            when (b) {
                is Block.Heading -> {
                    val t = plainInline(b.text)
                    append(t).append('\n')
                    if (b.level <= 2) append((if (b.level == 1) "=" else "-").repeat(t.length.coerceAtMost(60))).append('\n')
                }
                is Block.Paragraph -> append(b.text.lines().joinToString("\n") { plainInline(it) }).append('\n')
                is Block.Item -> append("  ".repeat(b.indent)).append(b.marker).append(' ').append(plainInline(b.text)).append('\n')
                is Block.Quote -> append(b.text.lines().joinToString("\n") { "> " + plainInline(it) }).append('\n')
                is Block.Code -> append(b.code.lines().joinToString("\n") { "    $it" }).append('\n')
                is Block.Table -> b.rows.forEach { row -> append(row.joinToString(" | ") { plainInline(it) }).append('\n') }
                Block.Rule -> append("-".repeat(40)).append('\n')
            }
            if (b !is Block.Item) append('\n')
        }
    }.trimEnd() + "\n"
}
