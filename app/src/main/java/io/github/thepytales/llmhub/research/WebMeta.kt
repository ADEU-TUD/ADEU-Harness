package io.github.thepytales.llmhub.research

import java.net.URI
import java.time.LocalDate

/**
 * A web page as the browser of the phone reads it (AP-63, WebView): address, title, visible text and its meta tags
 * (name → contents, lower case). The citation tags of journals and repositories (Highwire/Google Scholar, Dublin Core,
 * Open Graph) give authors, year, journal, DOI and PDF – as on the PC (desktop/WebPage, there through jsoup).
 */
data class WebMeta(val url: String, val title: String, val text: String, val meta: Map<String, List<String>> = emptyMap()) {
    private fun m(vararg names: String): List<String> = names.flatMap { meta[it.lowercase()].orEmpty() }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    val host: String get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    /** The DOI of the work: a meta tag, the address or the first DOI in the text. */
    val doi: String? get() =
        (m("citation_doi", "dc.identifier", "prism.doi", "bepress_citation_doi").firstNotNullOfOrNull { DOI.find(it)?.value }
            ?: DOI.find(url)?.value ?: DOI.find(text.take(4000))?.value)?.trimEnd('.', ',', ';', ')')

    val isPdf: Boolean get() = url.substringBefore('?').substringBefore('#').lowercase().endsWith(".pdf")

    /** The work's PDF: the page itself or citation_pdf_url. */
    val pdfUrl: String? get() = if (isPdf) url else m("citation_pdf_url", "eprints.document_url").firstOrNull()?.let { href ->
        runCatching { URI(url).resolve(href).toString() }.getOrDefault(href)
    }

    /** The work behind the page for the literature; without citation tags the page itself as a web source. */
    fun reference(today: LocalDate = LocalDate.now()): Reference {
        val journal = m("citation_journal_title", "prism.publicationname").firstOrNull().orEmpty()
        val book = m("citation_inbook_title", "citation_book_title").firstOrNull().orEmpty()
        val date = m("citation_publication_date", "citation_date", "citation_online_date", "dc.date", "article:published_time").firstOrNull().orEmpty()
        val type = if (journal.isNotBlank()) RefType.ARTICLE else RefType.WEB
        val first = m("citation_firstpage").firstOrNull().orEmpty()
        val last = m("citation_lastpage").firstOrNull().orEmpty()
        return Reference(
            type = type,
            authors = m("citation_author", "dc.creator", "author").mapNotNull { Person.parse(it) },
            year = Regex("(19|20)\\d{2}").find(date)?.value.orEmpty(),
            title = m("citation_title", "dc.title", "og:title").firstOrNull() ?: title.ifBlank { host },
            container = journal.ifBlank { book.ifBlank { if (type == RefType.WEB) m("og:site_name").firstOrNull() ?: host else "" } },
            volume = m("citation_volume").firstOrNull().orEmpty(),
            issue = m("citation_issue").firstOrNull().orEmpty(),
            pages = listOf(first, last).filter { it.isNotBlank() }.joinToString("–"),
            publisher = m("citation_publisher", "dc.publisher").firstOrNull().orEmpty(),
            doi = doi.orEmpty(),
            url = url,
            issn = m("citation_issn").firstOrNull().orEmpty(),
            accessed = if (type == RefType.WEB) today.toString() else "",
            abstract = m("citation_abstract", "dc.description", "description").firstOrNull().orEmpty().take(2000),
            openAccessUrl = pdfUrl.orEmpty(),
            source = "Webseite",
        )
    }

    /** The page as attachment text for the chat: where it comes from, then the text (cut at [max] characters). */
    fun asAttachment(max: Int = 120_000, today: LocalDate = LocalDate.now()): Pair<String, Boolean> {
        val head = "Webseite: ${title.ifBlank { host }}\nAdresse: $url\nAbgerufen: $today\n\n"
        val body = text.trim()
        return if (body.length > max) (head + body.take(max)) to true else (head + body) to false
    }

    companion object {
        private val DOI = Regex("10\\.\\d{4,9}/[^\\s\"<>]+")

        /** Script for the WebView: title, visible text and meta tags of the page as JSON. */
        const val READ_SCRIPT = "(function(){var m={};Array.prototype.forEach.call(document.querySelectorAll('meta[name],meta[property]'),function(e){" +
            "var k=(e.getAttribute('name')||e.getAttribute('property')||'').toLowerCase();if(!k)return;(m[k]=m[k]||[]).push(e.getAttribute('content')||'');});" +
            "return JSON.stringify({title:document.title||'',text:document.body?document.body.innerText:'',meta:m});})()"

        /** Reads what [READ_SCRIPT] returned (the WebView gives it as a JSON string literal). */
        fun fromScript(url: String, result: String?): WebMeta? = runCatching {
            val json = kotlinx.serialization.json.Json
            val inner = json.parseToJsonElement(result ?: return null).let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString() }
            val o = json.parseToJsonElement(inner) as kotlinx.serialization.json.JsonObject
            fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val meta = (o["meta"] as? kotlinx.serialization.json.JsonObject).orEmpty().mapValues { (_, v) ->
                (v as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            }
            WebMeta(url, str("title"), str("text"), meta)
        }.getOrNull()
    }
}
