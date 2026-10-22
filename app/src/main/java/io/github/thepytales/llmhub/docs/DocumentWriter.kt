package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.Segment
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.File

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

data class CreatedDocument(val file: File, val format: DocFormat, val pages: Int?)

/** Creates documents from Markdown in the app's private storage. */
object DocumentWriter {
    fun safeName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").replace(Regex("\\s+"), " ").take(80).trim('.', ' ')
            .ifBlank { "Dokument" }

    /** Writes the document into [folder]; an existing file with the same name is not overwritten. */
    fun create(folder: File, title: String, markdown: String, format: DocFormat, fileName: String? = null): CreatedDocument {
        val base = safeName(fileName?.substringBeforeLast('.')?.ifBlank { null } ?: title)
        folder.mkdirs()
        var file = File(folder, "$base.${format.ext}")
        var n = 2
        while (file.exists()) file = File(folder, "$base ($n).${format.ext}").also { n++ }
        var pages: Int? = null
        when (format) {
            DocFormat.PDF -> pages = MarkdownPdf.write(file, title, markdown)
            DocFormat.DOCX -> MarkdownDocx.write(file, title, markdown)
            DocFormat.HTML -> file.writeText(toHtml(title, markdown))
            DocFormat.MD -> file.writeText(withTitle(title, markdown))
            DocFormat.TXT -> file.writeText(toText(title, markdown))
        }
        return CreatedDocument(file, format, pages)
    }

    /** Adds the title as top-level heading unless the Markdown already starts with one. */
    internal fun withTitle(title: String, markdown: String): String =
        if (title.isBlank() || markdown.trimStart().startsWith("# ")) markdown else "# $title\n\n$markdown"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun htmlInline(text: String) = inlineSegments(text).joinToString("") { seg -> htmlSeg(seg) }

    private fun htmlSeg(seg: Segment): String {
        var out = esc(seg.text)
        if (seg.code) out = "<code>$out</code>"
        if (seg.bold) out = "<strong>$out</strong>"
        if (seg.italic) out = "<em>$out</em>"
        if (seg.strike) out = "<del>$out</del>"
        if (seg.link != null) out = "<a href=\"${esc(seg.link)}\">$out</a>"
        return out
    }

    fun toHtml(title: String, markdown: String): String = buildString {
        append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>${esc(title.ifBlank { "Dokument" })}</title>\n<style>\n")
        append("body{font-family:system-ui,sans-serif;max-width:780px;margin:2rem auto;padding:0 1rem;line-height:1.55;color:#1a1a1a}\n")
        append("pre{background:#f3f4f6;padding:.8rem;border-radius:6px;overflow-x:auto}code{font-family:monospace;background:#f3f4f6;padding:0 .2rem}\n")
        append("pre code{padding:0}table{border-collapse:collapse;margin:1rem 0}td,th{border:1px solid #ccc;padding:.3rem .6rem}\n")
        append("th{background:#f3f4f6}blockquote{border-left:3px solid #ccc;margin:0;padding-left:1rem;color:#555}\n")
        append("</style>\n</head>\n<body>\n")
        val blocks = parseBlocks(withTitle(title, markdown))
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
                    append("<li$style>${htmlInline(b.text)}</li>\n")
                }
                is Block.Quote -> append("<blockquote>${b.text.lines().joinToString("<br>\n") { htmlInline(it) }}</blockquote>\n")
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
