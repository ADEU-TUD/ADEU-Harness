package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.research.Person
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import org.jsoup.Jsoup
import java.net.URI
import java.time.LocalDate

/**
 * A page of the browser beside the chat (AP-14) as the app uses it: address, title, visible text and what its HTML says
 * about the work behind it – the citation meta tags of journals and repositories (Highwire/Google Scholar, Dublin Core,
 * Open Graph) give authors, year, journal, DOI and the PDF.
 */
data class WebPage(
    val url: String,
    val title: String,
    val text: String,
    val html: String = "",
) {
    private val doc by lazy { if (html.isBlank()) null else runCatching { Jsoup.parse(html, url) }.getOrNull() }

    private fun meta(vararg names: String): List<String> {
        val d = doc ?: return emptyList()
        return names.flatMap { n ->
            d.select("meta[name=\"$n\"], meta[property=\"$n\"], meta[name=\"${n.lowercase()}\"]").map { it.attr("content").trim() }
        }.filter { it.isNotBlank() }.distinct()
    }

    /** The DOI of the work (meta tag, a doi.org address or the first DOI in the text). */
    val doi: String? by lazy {
        val fromMeta = meta("citation_doi", "dc.identifier", "DC.identifier", "prism.doi", "bepress_citation_doi").firstNotNullOfOrNull { DOI.find(it)?.value }
        (fromMeta ?: DOI.find(url)?.value ?: DOI.find(text.take(4000))?.value)?.trimEnd('.', ',', ';', ')')
    }

    /** Address of the work's PDF: the page itself, a citation_pdf_url, or null. */
    val pdfUrl: String? by lazy {
        if (isPdf) url else meta("citation_pdf_url", "eprints.document_url").firstOrNull()?.let { absolute(it) }
    }

    /** The page is a PDF (shown by Chromium's viewer). */
    val isPdf: Boolean get() = url.substringBefore('?').substringBefore('#').lowercase().endsWith(".pdf")

    private fun absolute(href: String): String = runCatching { URI(url).resolve(href).toString() }.getOrDefault(href)

    /** The work behind the page for the literature: from the meta tags, else the page itself as a web source. */
    fun reference(today: LocalDate = LocalDate.now()): Reference {
        val authors = meta("citation_author", "dc.creator", "DC.creator", "author").mapNotNull { Person.parse(it) }
        val journal = meta("citation_journal_title", "prism.publicationName").firstOrNull().orEmpty()
        val book = meta("citation_inbook_title", "citation_book_title").firstOrNull().orEmpty()
        val date = meta("citation_publication_date", "citation_date", "citation_online_date", "dc.date", "DC.date", "article:published_time").firstOrNull().orEmpty()
        val year = Regex("(19|20)\\d{2}").find(date)?.value.orEmpty()
        val title = meta("citation_title", "dc.title", "DC.title", "og:title").firstOrNull() ?: this.title.ifBlank { host }
        val first = meta("citation_firstpage").firstOrNull().orEmpty()
        val last = meta("citation_lastpage").firstOrNull().orEmpty()
        val type = when {
            journal.isNotBlank() -> RefType.ARTICLE
            meta("citation_dissertation_institution").isNotEmpty() -> RefType.OTHER
            else -> RefType.WEB
        }
        return Reference(
            type = type,
            authors = authors,
            year = year,
            title = title,
            container = journal.ifBlank { book.ifBlank { if (type == RefType.WEB) meta("og:site_name").firstOrNull() ?: host else "" } },
            volume = meta("citation_volume").firstOrNull().orEmpty(),
            issue = meta("citation_issue").firstOrNull().orEmpty(),
            pages = listOf(first, last).filter { it.isNotBlank() }.joinToString("–"),
            publisher = meta("citation_publisher", "dc.publisher", "DC.publisher").firstOrNull().orEmpty(),
            doi = doi.orEmpty(),
            url = url,
            issn = meta("citation_issn").firstOrNull().orEmpty(),
            isbn = meta("citation_isbn").firstOrNull().orEmpty(),
            accessed = if (type == RefType.WEB) today.toString() else "",
            abstract = meta("citation_abstract", "dc.description", "description").firstOrNull().orEmpty().take(2000),
            language = meta("citation_language", "dc.language").firstOrNull().orEmpty(),
            openAccessUrl = pdfUrl.orEmpty(),
            source = "Webseite",
        )
    }

    /** Host of the address without „www.“. */
    val host: String get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    /** The page as an attachment text for the chat: where it comes from, then the text (cut at [max] characters). */
    fun asAttachment(max: Int = 120_000, today: LocalDate = LocalDate.now()): Pair<String, Boolean> {
        val head = "Webseite: ${title.ifBlank { host }}\nAdresse: $url\nAbgerufen: $today\n\n"
        val body = text.trim()
        return if (body.length > max) (head + body.take(max)) to true else (head + body) to false
    }

    companion object {
        private val DOI = Regex("10\\.\\d{4,9}/[^\\s\"<>]+")

        /** The visible text of [html] (when the browser could not give it). */
        fun textOf(html: String, url: String): String = runCatching { Jsoup.parse(html, url).body()?.text().orEmpty() }.getOrDefault("")
    }
}
