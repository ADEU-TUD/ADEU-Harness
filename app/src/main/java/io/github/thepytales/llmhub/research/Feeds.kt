package io.github.thepytales.llmhub.research

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Entries of RSS 2.0, RSS 1.0 (RDF) and Atom feeds, e.g. the tables of contents journals publish. */
object Feeds {
    data class Item(val title: String, val link: String, val date: String, val summary: String)

    fun parse(xml: String): List<Item> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // No external entities (feeds come from the internet).
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = runCatching { factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.trim().toByteArray(Charsets.UTF_8))) }.getOrNull() ?: return emptyList()
        fun Element.child(vararg names: String): String = names.firstNotNullOfOrNull { n ->
            val list = getElementsByTagName(n)
            (0 until list.length).map { list.item(it) as Element }.firstOrNull { it.parentNode == this }?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
        fun clean(s: String) = LiteratureSources.stripTags(s)
        val items = doc.getElementsByTagName("item").let { l -> (0 until l.length).map { l.item(it) as Element } }
        if (items.isNotEmpty()) return items.map { e ->
            Item(clean(e.child("title")), e.child("link").ifBlank { e.getAttribute("rdf:about") }, e.child("pubDate", "dc:date"), clean(e.child("description", "content:encoded")))
        }
        val entries = doc.getElementsByTagName("entry").let { l -> (0 until l.length).map { l.item(it) as Element } }
        return entries.map { e ->
            val links = e.getElementsByTagName("link")
            val link = (0 until links.length).map { links.item(it) as Element }.let { ls -> ls.firstOrNull { it.getAttribute("rel").isBlank() || it.getAttribute("rel") == "alternate" } ?: ls.firstOrNull() }?.getAttribute("href").orEmpty()
            Item(clean(e.child("title")), link, e.child("updated", "published"), clean(e.child("summary", "content")))
        }
    }
}
