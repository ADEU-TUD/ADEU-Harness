package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** E-book (EPUB 3) from Markdown: one chapter per top-level heading, with table of contents. */
object EpubWriter {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

    private fun inline(text: String): String = inlineSegments(text).joinToString("") { s ->
        var t = esc(s.text).replace("\n", "<br/>")
        if (s.code) t = "<code>$t</code>"
        if (s.bold) t = "<strong>$t</strong>"
        if (s.italic) t = "<em>$t</em>"
        if (s.strike) t = "<del>$t</del>"
        if (s.link != null) t = "<a href=\"${esc(s.link)}\">$t</a>"
        t
    }

    private fun html(blocks: List<Block>): String = buildString {
        var i = 0
        while (i < blocks.size) {
            when (val b = blocks[i]) {
                is Block.Heading -> append("<h${b.level}>").append(inline(b.text)).append("</h${b.level}>\n")
                is Block.Paragraph -> append("<p>").append(inline(b.text.replace(Regex(" {2}\n"), "\n"))).append("</p>\n")
                is Block.Quote -> append("<blockquote><p>").append(inline(b.text)).append("</p></blockquote>\n")
                is Block.Code -> append("<pre><code>").append(esc(b.code)).append("</code></pre>\n")
                is Block.Rule -> append("<hr/>\n")
                is Block.Table -> {
                    append("<table>\n")
                    b.rows.forEachIndexed { r, row ->
                        val tag = if (r == 0) "th" else "td"
                        append("<tr>").append(row.joinToString("") { "<$tag>${inline(it)}</$tag>" }).append("</tr>\n")
                    }
                    append("</table>\n")
                }
                is Block.Item -> {
                    // Consecutive items; nesting by indentation.
                    val stack = ArrayDeque<String>()
                    var open = false
                    while (i < blocks.size && blocks[i] is Block.Item) {
                        val item = blocks[i] as Block.Item
                        val tag = if (item.marker.first().isDigit()) "ol" else "ul"
                        val depth = item.indent + 1
                        if (stack.size >= depth && open) { append("</li>\n"); open = false }
                        while (stack.size > depth) { append("</").append(stack.removeLast()).append("></li>\n") }
                        while (stack.size < depth) { append("<").append(tag).append(">\n"); stack.addLast(tag) }
                        append("<li>").append(inline(item.text))
                        open = true
                        i++
                    }
                    if (open) append("</li>\n")
                    while (stack.isNotEmpty()) { append("</").append(stack.removeLast()).append(">\n"); if (stack.isNotEmpty()) append("</li>\n") }
                    i--
                }
            }
            i++
        }
    }

    private fun page(title: String, body: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"de\" lang=\"de\">\n" +
            "<head><meta charset=\"UTF-8\"/><title>${esc(title)}</title><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head>\n<body>\n$body</body>\n</html>\n"

    fun write(title: String, markdown: String): ByteArray {
        val blocks = parseBlocks(ChartBlocks.asTables(DocumentWriter.withTitle(title, markdown)))
        // Chapters: split at level-1 headings (or level 2 if there is at most one level-1 heading).
        val top = if (blocks.count { it is Block.Heading && it.level == 1 } > 1) 1 else 2
        val chapters = mutableListOf<MutableList<Block>>(mutableListOf())
        for (b in blocks) {
            val onlyTitle = chapters.size == 1 && chapters.last().all { it is Block.Heading && it.level < top }
            if (b is Block.Heading && b.level <= top && chapters.last().isNotEmpty() && !onlyTitle) chapters += mutableListOf<Block>()
            chapters.last() += b
        }
        val parts = chapters.filter { it.isNotEmpty() }.ifEmpty { listOf(mutableListOf<Block>(Block.Heading(1, title))) }
        val names = parts.mapIndexed { i, c -> ((c.firstOrNull { it is Block.Heading } as? Block.Heading)?.text?.replace(Regex("[*_`]"), "") ?: "Kapitel ${i + 1}") }
        val id = "urn:uuid:" + UUID.randomUUID()
        val modified = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            val mime = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            z.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = mime.size.toLong(); crc = CRC32().apply { update(mime) }.value
            })
            z.write(mime)
            z.closeEntry()
            fun entry(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            entry(
                "META-INF/container.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">" +
                    "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
            )
            entry(
                "OEBPS/content.opf",
                buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\" xml:lang=\"de\">")
                    append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"bookid\">$id</dc:identifier>")
                    append("<dc:title>${esc(title)}</dc:title><dc:language>de</dc:language><meta property=\"dcterms:modified\">$modified</meta></metadata><manifest>")
                    append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
                    append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>")
                    parts.indices.forEach { append("<item id=\"c${it + 1}\" href=\"kapitel${it + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>") }
                    append("</manifest><spine>")
                    parts.indices.forEach { append("<itemref idref=\"c${it + 1}\"/>") }
                    append("</spine></package>")
                },
            )
            entry(
                "OEBPS/nav.xhtml",
                page(
                    "Inhalt",
                    "<nav epub:type=\"toc\" id=\"toc\"><h1>Inhalt</h1><ol>\n" +
                        names.mapIndexed { i, n -> "<li><a href=\"kapitel${i + 1}.xhtml\">${esc(n)}</a></li>\n" }.joinToString("") + "</ol></nav>\n",
                ),
            )
            entry(
                "OEBPS/style.css",
                "body { font-family: serif; line-height: 1.5; margin: 0 1em; }\nh1, h2, h3 { font-family: sans-serif; }\n" +
                    "table { border-collapse: collapse; margin: 1em 0; }\nth, td { border: 1px solid #888; padding: 0.2em 0.5em; }\n" +
                    "pre { white-space: pre-wrap; font-size: 0.9em; background: #f4f4f4; padding: 0.5em; }\nblockquote { font-style: italic; margin-left: 1.5em; }\n",
            )
            parts.forEachIndexed { i, c -> entry("OEBPS/kapitel${i + 1}.xhtml", page(names[i], html(c))) }
        }
        return bos.toByteArray()
    }
}
