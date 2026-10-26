package io.github.thepytales.llmhub.research

import io.github.thepytales.llmhub.net.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Search filters shared by all sources. */
data class SearchFilter(
    val fromYear: Int? = null,
    val toYear: Int? = null,
    /** Only articles, books … (null = all). */
    val types: Set<RefType> = emptySet(),
    /** ISO 639-1 codes, e.g. de, en (empty = all). */
    val languages: Set<String> = emptySet(),
    val openAccessOnly: Boolean = false,
    /** relevance (default) or citations. */
    val sortByCitations: Boolean = false,
)

/** Databases the literature search and the completion of reference lists use. */
enum class LitSource(val label: String, val description: String) {
    OPENALEX("OpenAlex", "Über 250 Mio. wissenschaftliche Arbeiten aller Fächer, mit Abstracts und Zitationen"),
    CROSSREF("Crossref", "Alle Werke mit DOI: Zeitschriften, Sammelbände, Tagungsbände"),
    ERIC("ERIC", "Bildungsforschung (englischsprachig), Zeitschriften und graue Literatur"),
    DNB("Deutsche Nationalbibliothek", "Deutschsprachige Bücher, Sammelbände und Hochschulschriften"),
    SLUB("SLUB Dresden", "Katalog der Sächsischen Landes- und Universitätsbibliothek: Bestand vor Ort, E-Books und lizenzierte Artikel der TU Dresden"),
    EUROPEPMC("PubMed / Europe PMC", "Biologie, Medizin, Psychologie und Lebenswissenschaften (PubMed und Preprints), mit Abstracts"),
    ARXIV("arXiv", "Preprints aus Informatik, Mathematik, Physik und Statistik"),
    DOAJ("DOAJ", "Artikel aus frei zugänglichen Open-Access-Zeitschriften aller Fächer"),
    ;

    companion object {
        fun parse(s: String): LitSource? = s.trim().lowercase().let { n ->
            entries.firstOrNull { it.name.lowercase() == n || it.label.lowercase() == n } ?: when {
                n.startsWith("open") -> OPENALEX
                n.startsWith("cross") -> CROSSREF
                n.startsWith("eric") -> ERIC
                n.startsWith("dnb") || n.contains("national") -> DNB
                n.startsWith("slub") || n.contains("dresden") -> SLUB
                n.contains("pubmed") || n.contains("pmc") -> EUROPEPMC
                n.startsWith("arxiv") -> ARXIV
                n.startsWith("doaj") -> DOAJ
                else -> null
            }
        }
    }
}

/**
 * Clients for the open literature databases. [allowed] decides whether a host may be reached (environments with
 * restricted network access); [openAlexKey] is the free OpenAlex API key (without one all users behind the same
 * IP share a small daily budget).
 */
class LiteratureSources(
    private val http: OkHttpClient,
    private val openAlexKey: () -> String = { "" },
    private val allowed: (HttpUrl) -> Boolean = { true },
) {
    private suspend fun get(url: HttpUrl, accept: String = "application/json"): String? {
        if (!allowed(url)) throw IllegalStateException("Zugriff auf ${url.host} ist in dieser Umgebung nicht erlaubt")
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", accept).build()
            http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null
                    resp.code == 429 -> throw RateLimited(url.host)
                    !resp.isSuccessful -> throw IllegalStateException("${url.host} antwortet mit HTTP ${resp.code}")
                    else -> resp.body?.string()
                }
            }
        }
    }

    class RateLimited(host: String) : IllegalStateException(
        if ("openalex" in host) "OpenAlex: Tageskontingent ohne API-Schlüssel aufgebraucht – kostenlosen Schlüssel unter " +
            "Einstellungen › Literatur & Zotero eintragen" else "$host: zu viele Anfragen, bitte kurz warten"
    )

    private suspend fun json(url: HttpUrl): JsonObject? = get(url)?.let { runCatching { JSON.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    // ── Crossref ────────────────────────────────────────────────────────────────────────────────────────────

    suspend fun crossrefDoi(doi: String): Reference? {
        val url = "https://api.crossref.org/works/".toHttpUrl().newBuilder().addPathSegment(Reference.cleanDoi(doi)).build()
        val msg = json(url)?.get("message") as? JsonObject ?: return null
        return crossref(msg)
    }

    suspend fun crossrefSearch(query: String, rows: Int = 10, filter: SearchFilter = SearchFilter(), bibliographic: Boolean = false): List<Reference> {
        val b = "https://api.crossref.org/works".toHttpUrl().newBuilder()
            .addQueryParameter(if (bibliographic) "query.bibliographic" else "query", query.take(400))
            .addQueryParameter("rows", rows.coerceIn(1, 40).toString())
        val filters = buildList {
            filter.fromYear?.let { add("from-pub-date:$it") }
            filter.toYear?.let { add("until-pub-date:$it-12-31") }
            filter.types.flatMap { crossrefTypes(it) }.forEach { add("type:$it") }
        }
        if (filters.isNotEmpty()) b.addQueryParameter("filter", filters.joinToString(","))
        if (filter.sortByCitations) b.addQueryParameter("sort", "is-referenced-by-count").addQueryParameter("order", "desc")
        val items = ((json(b.build())?.get("message") as? JsonObject)?.get("items") as? JsonArray).orEmpty()
        return items.mapNotNull { (it as? JsonObject)?.let(::crossref) }
    }

    private fun crossrefTypes(t: RefType) = when (t) {
        RefType.ARTICLE -> listOf("journal-article")
        RefType.BOOK, RefType.EDITED_BOOK -> listOf("book", "monograph", "edited-book")
        RefType.CHAPTER -> listOf("book-chapter")
        RefType.CONFERENCE -> listOf("proceedings-article")
        RefType.THESIS -> listOf("dissertation")
        RefType.REPORT -> listOf("report")
        else -> emptyList()
    }

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.first(key: String): String =
        ((this[key] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: text(key)

    private fun crossref(o: JsonObject): Reference {
        fun people(key: String) = (o[key] as? JsonArray).orEmpty().mapNotNull { a ->
            val p = a as? JsonObject ?: return@mapNotNull null
            val family = p.text("family")
            when {
                family.isNotBlank() -> Person(family, p.text("given"))
                p.text("name").isNotBlank() -> Person(p.text("name"), literal = true)
                else -> null
            }
        }
        val year = listOf("issued", "published-print", "published-online", "created").firstNotNullOfOrNull { k ->
            (((o[k] as? JsonObject)?.get("date-parts") as? JsonArray)?.firstOrNull() as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.intOrNull }
        }
        val authors = people("author")
        val editors = people("editor")
        val type = when (o.text("type")) {
            "journal-article" -> RefType.ARTICLE
            "book", "monograph", "reference-book" -> if (authors.isEmpty() && editors.isNotEmpty()) RefType.EDITED_BOOK else RefType.BOOK
            "edited-book" -> RefType.EDITED_BOOK
            "book-chapter", "book-section", "book-part", "reference-entry" -> RefType.CHAPTER
            "proceedings-article" -> RefType.CONFERENCE
            "dissertation" -> RefType.THESIS
            "report", "report-component" -> RefType.REPORT
            else -> RefType.OTHER
        }
        val title = listOf(o.first("title"), o.first("subtitle")).filter { it.isNotBlank() }
            .let { if (it.size == 2 && it[0].endsWith("?")) it.joinToString(" ") else it.joinToString(": ") }
        val doi = o.text("DOI")
        return Reference(
            type = type,
            authors = authors,
            editors = editors,
            year = year?.toString().orEmpty(),
            title = stripTags(title),
            container = stripTags(o.first("container-title")),
            volume = o.text("volume"),
            issue = o.text("issue"),
            pages = o.text("page").replace("-", "–"),
            edition = o.text("edition-number"),
            publisher = o.text("publisher"),
            place = o.text("publisher-location"),
            doi = doi,
            url = o.text("URL"),
            isbn = o.first("ISBN"),
            issn = o.first("ISSN"),
            institution = ((o["institution"] as? JsonArray)?.firstOrNull() as? JsonObject)?.text("name").orEmpty(),
            abstract = stripTags(o.text("abstract")),
            language = o.text("language"),
            citations = (o["is-referenced-by-count"] as? JsonPrimitive)?.intOrNull,
            source = "Crossref",
        )
    }

    /** Journals by name (Crossref), to find their ISSN. */
    suspend fun crossrefJournals(name: String): List<Pair<String, String>> {
        val url = "https://api.crossref.org/journals".toHttpUrl().newBuilder().addQueryParameter("query", name.take(200)).addQueryParameter("rows", "5").build()
        val items = ((json(url)?.get("message") as? JsonObject)?.get("items") as? JsonArray).orEmpty()
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val issn = (o["ISSN"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
            o.text("title") to issn
        }
    }

    /** Newest articles of a journal (by ISSN) since [fromDate] (yyyy-mm-dd). */
    suspend fun journalArticles(issn: String, fromDate: String, rows: Int = 20): List<Reference> {
        val url = "https://api.crossref.org/journals/".toHttpUrl().newBuilder().addPathSegment(issn.trim()).addPathSegment("works")
            .addQueryParameter("filter", "from-pub-date:$fromDate")
            .addQueryParameter("sort", "published").addQueryParameter("order", "desc")
            .addQueryParameter("rows", rows.coerceIn(1, 50).toString()).build()
        val items = ((json(url)?.get("message") as? JsonObject)?.get("items") as? JsonArray).orEmpty()
        return items.mapNotNull { (it as? JsonObject)?.let(::crossref) }
    }

    // ── OpenAlex ────────────────────────────────────────────────────────────────────────────────────────────

    private fun openAlex(path: String): HttpUrl.Builder = "https://api.openalex.org/$path".toHttpUrl().newBuilder().apply {
        addQueryParameter("mailto", MAILTO)
        openAlexKey().trim().takeIf { it.isNotEmpty() }?.let { addQueryParameter("api_key", it) }
    }

    suspend fun openAlexSearch(query: String, perPage: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        val b = openAlex("works").addQueryParameter("search", query.take(300)).addQueryParameter("per-page", perPage.coerceIn(1, 50).toString())
        val filters = openAlexFilters(filter)
        if (filters.isNotEmpty()) b.addQueryParameter("filter", filters.joinToString(","))
        if (filter.sortByCitations) b.addQueryParameter("sort", "cited_by_count:desc")
        return (json(b.build())?.get("results") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::openAlexWork) }
    }

    private fun openAlexFilters(filter: SearchFilter) = buildList {
        filter.fromYear?.let { add("from_publication_date:$it-01-01") }
        filter.toYear?.let { add("to_publication_date:$it-12-31") }
        val types = filter.types.flatMap {
            when (it) {
                RefType.ARTICLE -> listOf("article", "review")
                RefType.BOOK, RefType.EDITED_BOOK -> listOf("book")
                RefType.CHAPTER -> listOf("book-chapter")
                RefType.THESIS -> listOf("dissertation")
                RefType.REPORT -> listOf("report")
                else -> emptyList()
            }
        }.distinct()
        if (types.isNotEmpty()) add("type:" + types.joinToString("|"))
        if (filter.languages.isNotEmpty()) add("language:" + filter.languages.joinToString("|"))
        if (filter.openAccessOnly) add("is_oa:true")
    }

    /** One work by DOI or OpenAlex id (W123…). */
    suspend fun openAlexWork(id: String): Reference? = openAlexRaw(id)?.let(::openAlexWork)

    private suspend fun openAlexRaw(id: String): JsonObject? {
        val key = when {
            id.startsWith("W", ignoreCase = true) && id.drop(1).all { it.isDigit() } -> id.uppercase()
            id.contains("openalex.org/") -> id.substringAfterLast('/')
            else -> "doi:" + Reference.cleanDoi(id)
        }
        return json(openAlex("works/$key").build())
    }

    /** Works cited by [id] (backward snowballing) or citing it (forward). */
    suspend fun openAlexSnowball(id: String, citing: Boolean, perPage: Int = 20, filter: SearchFilter = SearchFilter()): List<Reference> {
        val work = openAlexRaw(id) ?: return emptyList()
        val oaId = work.text("id").substringAfterLast('/')
        val filters = openAlexFilters(filter).toMutableList()
        if (citing) {
            filters += "cites:$oaId"
        } else {
            val refs = (work["referenced_works"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/') }
            if (refs.isEmpty()) return emptyList()
            filters += "openalex:" + refs.take(50).joinToString("|")
        }
        val b = openAlex("works").addQueryParameter("filter", filters.joinToString(","))
            .addQueryParameter("per-page", perPage.coerceIn(1, 50).toString())
            .addQueryParameter("sort", "cited_by_count:desc")
        return (json(b.build())?.get("results") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::openAlexWork) }
    }

    private fun openAlexWork(o: JsonObject): Reference {
        val authors = (o["authorships"] as? JsonArray).orEmpty().mapNotNull { a ->
            val name = ((a as? JsonObject)?.get("author") as? JsonObject)?.text("display_name") ?: return@mapNotNull null
            Person.parse(name)
        }
        val doi = o.text("doi").removePrefix("https://doi.org/")
        val location = o["primary_location"] as? JsonObject
        val source = location?.get("source") as? JsonObject
        val biblio = o["biblio"] as? JsonObject
        val first = biblio?.text("first_page").orEmpty()
        val last = biblio?.text("last_page").orEmpty()
        val type = when (o.text("type")) {
            "article", "review", "letter", "editorial" -> RefType.ARTICLE
            "book" -> RefType.BOOK
            "book-chapter" -> RefType.CHAPTER
            "dissertation" -> RefType.THESIS
            "report" -> RefType.REPORT
            else -> if (source?.text("type") == "journal") RefType.ARTICLE else RefType.OTHER
        }
        val oa = o["open_access"] as? JsonObject
        return Reference(
            type = type,
            authors = authors,
            year = (o["publication_year"] as? JsonPrimitive)?.intOrNull?.toString().orEmpty(),
            title = stripTags(o.text("title").ifBlank { o.text("display_name") }),
            container = source?.text("display_name").orEmpty(),
            volume = biblio?.text("volume").orEmpty(),
            issue = biblio?.text("issue").orEmpty(),
            pages = if (first.isNotBlank() && last.isNotBlank() && first != last) "$first–$last" else first,
            publisher = source?.text("host_organization_name").orEmpty().takeIf { type != RefType.ARTICLE }.orEmpty(),
            doi = doi,
            url = if (doi.isBlank()) o.text("id") else "",
            issn = (source?.get("issn_l") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            abstract = abstractOf(o["abstract_inverted_index"]),
            language = o.text("language"),
            citations = (o["cited_by_count"] as? JsonPrimitive)?.intOrNull,
            openAccessUrl = oa?.text("oa_url").orEmpty(),
            source = "OpenAlex",
        ).also { openAlexIds[it.dedupeKey] = o.text("id").substringAfterLast('/') }
    }

    /** OpenAlex ids of works seen in results, so snowballing also works for works without DOI. */
    val openAlexIds = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** OpenAlex stores abstracts as word → positions. */
    private fun abstractOf(e: JsonElement?): String {
        val index = e as? JsonObject ?: return ""
        val words = sortedMapOf<Int, String>()
        index.forEach { (word, positions) ->
            (positions as? JsonArray).orEmpty().forEach { p -> (p as? JsonPrimitive)?.intOrNull?.let { words[it] = word } }
        }
        return words.values.joinToString(" ")
    }

    // ── ERIC (Institute of Education Sciences) ──────────────────────────────────────────────────────────────

    suspend fun ericSearch(query: String, rows: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        var q = query.take(300)
        if (filter.fromYear != null || filter.toYear != null) q += " publicationdateyear:[${filter.fromYear ?: 1900} TO ${filter.toYear ?: 2100}]"
        if (filter.openAccessOnly) q += " e_fulltextauth:T"
        val url = "https://api.ies.ed.gov/eric/".toHttpUrl().newBuilder()
            .addQueryParameter("search", q)
            .addQueryParameter("rows", rows.coerceIn(1, 50).toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("fields", "id,title,author,description,publicationdateyear,source,publisher,url,e_fulltextauth,isbn,issn,publicationtype,language,peerreviewed")
            .build()
        val docs = ((json(url)?.get("response") as? JsonObject)?.get("docs") as? JsonArray).orEmpty()
        return docs.mapNotNull { d ->
            val o = d as? JsonObject ?: return@mapNotNull null
            val id = o.text("id")
            val types = (o["publicationtype"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val authors = (o["author"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(Person::parse) }
            val src = o.text("source")
            // "Journal of X, v12 n3 p45-67 2020"
            val vol = Regex("\\bv(\\d+)").find(src)?.groupValues?.get(1).orEmpty()
            val no = Regex("\\bn(\\d+)").find(src)?.groupValues?.get(1).orEmpty()
            val pages = Regex("\\bp(\\d+-\\d+|\\d+)").find(src)?.groupValues?.get(1)?.replace("-", "–").orEmpty()
            val journal = src.substringBefore(", v").substringBefore(" v").trim()
            val type = when {
                types.any { "Journal" in it } -> RefType.ARTICLE
                types.any { "Book" in it } -> RefType.BOOK
                types.any { "Dissertation" in it || "Theses" in it } -> RefType.THESIS
                types.any { "Report" in it } -> RefType.REPORT
                types.any { "Speeches" in it || "Conference" in it } -> RefType.CONFERENCE
                else -> RefType.OTHER
            }
            val ft = (o["e_fulltextauth"] as? JsonPrimitive)?.contentOrNull == "1" || o.text("e_fulltextauth") == "T"
            Reference(
                type = type,
                authors = authors,
                year = (o["publicationdateyear"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                title = o.text("title").trimEnd('.'),
                container = if (type == RefType.ARTICLE) journal else "",
                volume = vol, issue = no, pages = pages,
                publisher = o.text("publisher").substringBefore(".").takeIf { type != RefType.ARTICLE }.orEmpty(),
                url = "https://eric.ed.gov/?id=$id",
                isbn = (o["isbn"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                issn = (o["issn"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }?.removePrefix("ISSN-").orEmpty(),
                abstract = o.text("description"),
                openAccessUrl = if (ft && id.startsWith("ED")) "https://files.eric.ed.gov/fulltext/$id.pdf" else "",
                source = "ERIC",
                genre = "",
            )
        }
    }

    // ── Deutsche Nationalbibliothek (SRU, MARC 21) ──────────────────────────────────────────────────────────

    suspend fun dnbSearch(query: String, rows: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.length > 1 }.take(8)
        if (terms.isEmpty()) return emptyList()
        var cql = terms.joinToString(" and ") { "woe=${it.replace("\"", "")}" }
        filter.fromYear?.let { cql += " and jhr>=$it" }
        filter.toYear?.let { cql += " and jhr<=$it" }
        return dnb(cql, rows)
    }

    suspend fun dnbIsbn(isbn: String): Reference? = dnb("num=" + isbn.filter { it.isDigit() || it == 'X' }, 1).firstOrNull()

    /** Books by title words and author; with [year] the edition of that year comes first. */
    suspend fun dnbTitle(title: String, author: String?, year: String = ""): Reference? {
        val words = title.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 3 }.take(6)
        if (words.isEmpty()) return null
        var cql = words.joinToString(" and ") { "tit=$it" }
        if (!author.isNullOrBlank()) cql += " and per=" + author.replace(Regex("[^\\p{L}-]"), "")
        val found = dnb(cql, 10)
        return found.firstOrNull { year.isNotBlank() && it.year == year } ?: found.firstOrNull()
    }

    private suspend fun dnb(cql: String, rows: Int): List<Reference> {
        val url = "https://services.dnb.de/sru/dnb".toHttpUrl().newBuilder()
            .addQueryParameter("version", "1.1").addQueryParameter("operation", "searchRetrieve")
            .addQueryParameter("query", cql).addQueryParameter("recordSchema", "MARC21-xml")
            .addQueryParameter("maximumRecords", rows.coerceIn(1, 50).toString())
            .build()
        val xml = get(url, "application/xml") ?: return emptyList()
        return withContext(Dispatchers.Default) { Marc.parse(xml) }
    }

    // ── SLUB Dresden (katalog.slub-dresden.de, JSON of its app interface) ────────────────────────────────────

    suspend fun slubSearch(query: String, rows: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        val url = "https://katalog.slub-dresden.de/".toHttpUrl().newBuilder()
            .addQueryParameter("tx_find_find[q][default]", query.take(300))
            .addQueryParameter("tx_find_find[count]", (rows * 2).coerceIn(5, 60).toString())
            .addQueryParameter("tx_find_find[format]", "data")
            .addQueryParameter("tx_find_find[data-format]", "app")
            .addQueryParameter("type", "1369315142")
            .build()
        val docs = (json(url)?.get("docs") as? JsonArray).orEmpty()
        val years = filter.fromYear != null || filter.toYear != null
        return docs.mapNotNull { (it as? JsonObject)?.let(::slubDoc) }
            .filter { r ->
                val y = r.year.toIntOrNull()
                if (y == null) !years else (filter.fromYear == null || y >= filter.fromYear) && (filter.toYear == null || y <= filter.toYear)
            }
            .filter { r -> filter.types.isEmpty() || r.type in filter.types || (r.type == RefType.BOOK && RefType.EDITED_BOOK in filter.types) }
            .take(rows)
    }

    private fun slubDoc(o: JsonObject): Reference? {
        val id = o.text("id").ifBlank { return null }
        val authors = (o["author"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(Person::parse) }
        val formats = (o["format"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(" ")
        val type = when {
            "Article" in formats -> RefType.ARTICLE
            "Thesis" in formats || "Hochschulschrift" in formats -> RefType.THESIS
            "Book" in formats -> RefType.BOOK
            else -> RefType.OTHER
        }
        // The catalogue appends the statement of responsibility to the title ("… Paul Westrich", "[Autor: …]").
        var title = o.text("title").replace(Regex("\\s*\\[[^\\]]*]\\s*$"), "").trim()
        authors.forEach { a ->
            listOf("${a.given} ${a.family}".trim(), a.family).forEach { n -> if (n.isNotBlank() && title.endsWith(" $n")) title = title.removeSuffix(" $n").trim() }
        }
        val imprint = ((o["imprint"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
        val year = o.text("creationDate").takeIf { it.isNotBlank() && it != "null" } ?: Regex("\\d{4}").find(imprint)?.value.orEmpty()
        val book = type != RefType.ARTICLE && ':' in imprint
        return Reference(
            type = type, authors = authors, year = year.take(4), title = title,
            publisher = if (book) imprint.substringAfter(':').substringBeforeLast(',').trim().removeSurrounding("[", "]") else "",
            place = if (book) imprint.substringBefore(':').trim() else "",
            url = "https://katalog.slub-dresden.de/id/$id", source = "SLUB",
        )
    }

    // ── Europe PMC (PubMed, PMC, preprints) ─────────────────────────────────────────────────────────────────

    suspend fun europePmcSearch(query: String, rows: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        var q = query.take(300)
        if (filter.fromYear != null || filter.toYear != null) q += " AND PUB_YEAR:[${filter.fromYear ?: 1800} TO ${filter.toYear ?: 2100}]"
        if (filter.openAccessOnly) q += " AND OPEN_ACCESS:y"
        val b = "https://www.ebi.ac.uk/europepmc/webservices/rest/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", q).addQueryParameter("format", "json").addQueryParameter("resultType", "core")
            .addQueryParameter("pageSize", rows.coerceIn(1, 50).toString())
        if (filter.sortByCitations) b.addQueryParameter("sort", "CITED desc")
        val list = ((json(b.build())?.get("resultList") as? JsonObject)?.get("result") as? JsonArray).orEmpty()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val authors = ((o["authorList"] as? JsonObject)?.get("author") as? JsonArray).orEmpty().mapNotNull { a ->
                val p = a as? JsonObject ?: return@mapNotNull null
                when {
                    p.text("lastName").isNotBlank() -> Person(p.text("lastName"), p.text("firstName"))
                    p.text("collectiveName").isNotBlank() -> Person(p.text("collectiveName"), literal = true)
                    else -> null
                }
            }
            val journal = o["journalInfo"] as? JsonObject
            val types = ((o["pubTypeList"] as? JsonObject)?.get("pubType") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val urls = ((o["fullTextUrlList"] as? JsonObject)?.get("fullTextUrl") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val oa = urls.firstOrNull { it.text("availabilityCode") == "OA" || it.text("availabilityCode") == "F" }?.text("url").orEmpty()
            val pmid = o.text("pmid")
            val pmcid = o.text("pmcid")
            val doi = o.text("doi")
            val preprint = o.text("source") == "PPR" || types.any { "preprint" in it.lowercase() }
            Reference(
                type = if (preprint) RefType.OTHER else RefType.ARTICLE,
                authors = authors,
                year = o.text("pubYear"),
                title = stripTags(o.text("title")).trimEnd('.'),
                container = (journal?.get("journal") as? JsonObject)?.text("title").orEmpty(),
                volume = journal?.text("volume").orEmpty(),
                issue = journal?.text("issue").orEmpty(),
                pages = o.text("pageInfo").replace("-", "–"),
                genre = if (preprint) "Preprint" else "",
                doi = doi,
                url = when {
                    pmid.isNotBlank() -> "https://pubmed.ncbi.nlm.nih.gov/$pmid/"
                    else -> "https://europepmc.org/article/${o.text("source")}/${o.text("id")}"
                },
                abstract = stripTags(o.text("abstractText")),
                language = when (o.text("language")) { "eng" -> "en"; "ger" -> "de"; else -> o.text("language") },
                citations = (o["citedByCount"] as? JsonPrimitive)?.intOrNull,
                openAccessUrl = oa.ifBlank { if (pmcid.isNotBlank()) "https://europepmc.org/article/PMC/$pmcid" else "" },
                source = "Europe PMC",
            )
        }
    }

    // ── arXiv (Atom feed) ───────────────────────────────────────────────────────────────────────────────────

    suspend fun arxivSearch(query: String, rows: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(8)
        if (terms.isEmpty()) return emptyList()
        val url = "https://export.arxiv.org/api/query".toHttpUrl().newBuilder()
            .addQueryParameter("search_query", terms.joinToString(" AND ") { "all:" + it.replace("\"", "") })
            .addQueryParameter("max_results", rows.coerceIn(1, 50).toString())
            .addQueryParameter("sortBy", "relevance")
            .build()
        val xml = get(url, "application/atom+xml") ?: return emptyList()
        return withContext(Dispatchers.Default) { Atom.arxiv(xml) }.filter { r ->
            val y = r.year.toIntOrNull() ?: return@filter true
            (filter.fromYear == null || y >= filter.fromYear) && (filter.toYear == null || y <= filter.toYear)
        }
    }

    // ── DOAJ (Directory of Open Access Journals) ────────────────────────────────────────────────────────────

    suspend fun doajSearch(query: String, rows: Int = 15, filter: SearchFilter = SearchFilter()): List<Reference> {
        var q = query.take(250)
        if (filter.fromYear != null || filter.toYear != null) q += " AND bibjson.year:[${filter.fromYear ?: 1900} TO ${filter.toYear ?: 2100}]"
        val url = "https://doaj.org/api/search/articles/".toHttpUrl().newBuilder().addPathSegment(q)
            .addQueryParameter("pageSize", rows.coerceIn(1, 50).toString()).build()
        val results = (json(url)?.get("results") as? JsonArray).orEmpty()
        return results.mapNotNull { e ->
            val bib = (e as? JsonObject)?.get("bibjson") as? JsonObject ?: return@mapNotNull null
            val journal = bib["journal"] as? JsonObject
            val ids = (bib["identifier"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val doi = ids.firstOrNull { it.text("type").equals("doi", true) }?.text("id").orEmpty()
            val link = (bib["link"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { it.text("url").startsWith("http") }?.text("url").orEmpty()
            val first = bib.text("start_page")
            val last = bib.text("end_page")
            Reference(
                type = RefType.ARTICLE,
                authors = (bib["author"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("name")?.let(Person::parse) },
                year = bib.text("year"),
                title = stripTags(bib.text("title")),
                container = journal?.text("title").orEmpty(),
                volume = journal?.text("volume").orEmpty(),
                issue = journal?.text("number").orEmpty(),
                pages = if (first.isNotBlank() && last.isNotBlank() && first != last) "$first–$last" else first,
                doi = doi,
                url = if (doi.isBlank()) link else "",
                issn = ((journal?.get("issns") as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty(),
                abstract = stripTags(bib.text("abstract")),
                language = ((journal?.get("language") as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull?.lowercase().orEmpty(),
                openAccessUrl = link,
                source = "DOAJ",
            )
        }
    }

    // ── Google Books (completion only) ──────────────────────────────────────────────────────────────────────

    suspend fun googleBooks(title: String, author: String?): List<Reference> {
        val q = buildString {
            append("intitle:").append(title.take(120))
            if (!author.isNullOrBlank()) append(" inauthor:").append(author)
        }
        val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("maxResults", "5").build()
        val items = (json(url)?.get("items") as? JsonArray).orEmpty()
        return items.mapNotNull { item ->
            val info = (item as? JsonObject)?.get("volumeInfo") as? JsonObject ?: return@mapNotNull null
            val authors = (info["authors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(Person::parse) }
            val isbn = (info["industryIdentifiers"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .sortedByDescending { it.text("type") == "ISBN_13" }.firstOrNull()?.text("identifier").orEmpty()
            Reference(
                type = RefType.BOOK,
                authors = authors,
                year = info.text("publishedDate").take(4),
                title = listOf(info.text("title"), info.text("subtitle")).filter { it.isNotBlank() }.joinToString(": "),
                publisher = info.text("publisher"),
                isbn = isbn,
                language = info.text("language"),
                source = "Google Books",
            )
        }
    }

    companion object {
        const val USER_AGENT = "ADEU-LLM/1.0 (Literatur; https://github.com/ADEU-TUD)"
        const val MAILTO = "adeu-llm@users.noreply.github.com"

        fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()
    }
}

/** The Atom feed of arXiv to references (preprints). */
object Atom {
    fun arxiv(xml: String): List<Reference> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = runCatching { factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))) }.getOrNull() ?: return emptyList()
        val entries = doc.getElementsByTagName("entry")
        return (0 until entries.length).mapNotNull { i ->
            val e = entries.item(i) as? Element ?: return@mapNotNull null
            fun first(tag: String) = (e.getElementsByTagName(tag).item(0) as? Element)?.textContent?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val names = e.getElementsByTagName("name")
            val links = e.getElementsByTagName("link")
            val pdf = (0 until links.length).mapNotNull { links.item(it) as? Element }.firstOrNull { it.getAttribute("title") == "pdf" }?.getAttribute("href").orEmpty()
            Reference(
                type = RefType.OTHER,
                authors = (0 until names.length).mapNotNull { Person.parse(names.item(it).textContent.trim()) },
                year = first("published").take(4),
                title = first("title"),
                container = "arXiv",
                genre = "Preprint",
                doi = first("arxiv:doi"),
                url = first("id").replace("http://", "https://"),
                abstract = first("summary"),
                openAccessUrl = pdf.replace("http://", "https://"),
                source = "arXiv",
            )
        }
    }
}

/** MARC 21 XML records of the German National Library to references. */
object Marc {
    fun parse(xml: String): List<Reference> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = runCatching { factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))) }.getOrNull() ?: return emptyList()
        val records = doc.getElementsByTagName("record")
        val out = mutableListOf<Reference>()
        for (i in 0 until records.length) {
            val rec = records.item(i) as? Element ?: continue
            // The SRU envelope is also called "record"; only the innermost one is the MARC record.
            if (rec.getElementsByTagName("datafield").length == 0 || rec.getElementsByTagName("record").length > 0) continue
            out += record(rec)
        }
        return out
    }

    private fun record(rec: Element): Reference {
        val fields = rec.getElementsByTagName("datafield")
        fun all(tag: String): List<Element> = (0 until fields.length).mapNotNull { fields.item(it) as? Element }.filter { it.getAttribute("tag") == tag }
        fun sub(e: Element, code: String): String {
            val subs = e.getElementsByTagName("subfield")
            return (0 until subs.length).mapNotNull { subs.item(it) as? Element }.firstOrNull { it.getAttribute("code") == code }?.textContent?.trim().orEmpty()
        }
        fun subs(e: Element, code: String): List<String> {
            val s = e.getElementsByTagName("subfield")
            return (0 until s.length).mapNotNull { s.item(it) as? Element }.filter { it.getAttribute("code") == code }.map { it.textContent.trim() }
        }
        fun clean(s: String) = s.trim().trimEnd('/', ':', ';', ',', '.', ' ').trim()
        // Further persons count only with their role: author (aut) or editor (edt) – not dedicatees, translators, illustrators.
        fun role(e: Element): String {
            val codes = subs(e, "4")
            val term = sub(e, "e").lowercase()
            return when {
                "edt" in codes || "hrsg" in term || "herausgeber" in term -> "edt"
                "aut" in codes || "verfasser" in term || "autor" in term -> "aut"
                else -> ""
            }
        }
        val authors = (all("100") + all("700").filter { role(it) == "aut" }).mapNotNull { Person.parse(clean(sub(it, "a"))) }
        val editors = all("700").filter { role(it) == "edt" }.mapNotNull { Person.parse(clean(sub(it, "a"))) }
        val corporate = all("110").map { clean(sub(it, "a")) }.filter { it.isNotBlank() }.map { Person(it, literal = true) }
        val t = all("245").firstOrNull()
        // The subtitle ends where a dedication or statement of responsibility begins ("; Franz Weinert gewidmet").
        val title = t?.let { listOf(clean(sub(it, "a")), clean(sub(it, "b").substringBefore(" ; ").substringBefore(";"))).filter { s -> s.isNotBlank() }.joinToString(": ") }.orEmpty()
        val pub = (all("264") + all("260")).firstOrNull()
        val year = pub?.let { Regex("\\d{4}").find(sub(it, "c"))?.value }.orEmpty()
            .ifBlank { all("008").firstOrNull()?.textContent?.substring(7, 11).orEmpty() }
        val thesis = all("502").firstOrNull()?.let { listOf(sub(it, "a"), sub(it, "b"), sub(it, "c")).filter { s -> s.isNotBlank() }.joinToString(", ") }.orEmpty()
        val isbn = all("020").map { sub(it, "a").substringBefore(" ") }.firstOrNull { it.isNotBlank() }.orEmpty()
        val type = when {
            thesis.isNotBlank() -> RefType.THESIS
            authors.isEmpty() && editors.isNotEmpty() -> RefType.EDITED_BOOK
            else -> RefType.BOOK
        }
        return Reference(
            type = type,
            authors = authors.ifEmpty { if (editors.isEmpty()) corporate else emptyList() },
            editors = editors,
            year = year,
            title = title,
            edition = all("250").firstOrNull()?.let { Regex("\\d+").find(sub(it, "a"))?.value }.orEmpty(),
            publisher = pub?.let { clean(sub(it, "b")) }.orEmpty().removeSurrounding("[", "]"),
            place = pub?.let { clean(sub(it, "a")) }.orEmpty().removeSurrounding("[", "]"),
            isbn = isbn,
            institution = if (type == RefType.THESIS) thesis.split(",").getOrNull(1)?.trim().orEmpty() else "",
            genre = if (type == RefType.THESIS) thesis.substringBefore(",").trim() else "",
            // Only links to the full text, not to tables of contents or other catalogues.
            url = all("856").filter { e -> sub(e, "3").contains("Volltext", ignoreCase = true) || sub(e, "x").contains("Langzeitarchivierung", ignoreCase = true) }
                .map { sub(it, "u") }.firstOrNull { it.startsWith("http") }.orEmpty(),
            language = all("041").firstOrNull()?.let { sub(it, "a") }.orEmpty(),
            source = "DNB",
        )
    }
}
