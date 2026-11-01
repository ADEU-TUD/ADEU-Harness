package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.ui.tr
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/** E-books (.epub): title, author and the chapters in reading order (OPF spine), XHTML as text. */
object EpubText {
    private const val MAX_CHARS = 400_000

    fun text(bytes: ByteArray): String {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var total = 0L
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val data = zip.readBytes()
                    total += data.size
                    if (total > 200L * 1024 * 1024) throw IllegalArgumentException(tr("Das E-Book ist entpackt zu groß."))
                    files[e.name] = data
                }
                e = zip.nextEntry
            }
        }
        val container = files["META-INF/container.xml"]?.let(OdfText::parse)
        val opfPath = container?.let { c -> elements(c, "rootfile").firstOrNull()?.getAttribute("full-path") }?.takeIf { it.isNotBlank() }
            ?: files.keys.firstOrNull { it.endsWith(".opf") } ?: throw IllegalArgumentException(tr("Kein gültiges E-Book (OPF fehlt)."))
        val opf = files[opfPath]?.let(OdfText::parse) ?: throw IllegalArgumentException(tr("Kein gültiges E-Book (OPF fehlt)."))
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = elements(opf, "item").associate { it.getAttribute("id") to it.getAttribute("href") }
        val spine = elements(opf, "itemref").mapNotNull { manifest[it.getAttribute("idref")] }
            .ifEmpty { manifest.values.filter { it.endsWith(".xhtml") || it.endsWith(".html") || it.endsWith(".htm") } }
        val title = elements(opf, "title").firstOrNull()?.textContent?.trim()
        val authors = elements(opf, "creator").map { it.textContent.trim() }.filter { it.isNotEmpty() }
        val out = StringBuilder()
        title?.let { out.append("# ").append(it).append("\n\n") }
        if (authors.isNotEmpty()) out.append("Von ").append(authors.joinToString(", ")).append("\n\n")
        for ((i, href) in spine.withIndex()) {
            if (out.length > MAX_CHARS) { out.append("\n\n[… weitere Kapitel gekürzt]"); break }
            val path = normalize(base + runCatching { URLDecoder.decode(href.substringBefore('#'), "UTF-8") }.getOrDefault(href))
            val html = files[path] ?: continue
            val text = WebTools.htmlToText(TextCodec.text(html)).trim()
            if (text.isEmpty()) continue
            if (i > 0 && out.isNotEmpty()) out.append("\n\n---\n\n")
            out.append(text)
        }
        return out.toString().trim()
    }

    private fun normalize(path: String): String {
        val parts = ArrayDeque<String>()
        for (p in path.split('/')) when (p) {
            "", "." -> {}
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(p)
        }
        return parts.joinToString("/")
    }

    private fun elements(root: Element, local: String): List<Element> {
        val list = root.getElementsByTagNameNS("*", local)
        return (0 until list.length).mapNotNull { list.item(it) as? Element }
    }
}
