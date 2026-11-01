package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.net.JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import io.github.thepytales.llmhub.ui.trLater

/**
 * Checks literature references against Crossref (journal articles, books and chapters with DOI), OpenAlex and
 * Google Books (books without DOI): does the work exist, and do authors, year and title match? Language models
 * invent plausible-looking references; this is the check before such a reference ends up in a paper.
 */
object ReferenceCheck {
    enum class Status(val symbol: String, val label: String) {
        VERIFIED("✓", trLater("bestätigt")),
        DIFFERS("≈", trLater("gefunden, Angaben weichen ab")),
        NOT_FOUND("✗", trLater("nicht gefunden")),
        UNCHECKED("?", trLater("nicht prüfbar")),
    }

    data class Work(
        val title: String,
        val authors: List<String>,
        val year: Int?,
        val venue: String,
        val doi: String?,
        val url: String?,
        val source: String,
    ) {
        fun cite(): String = buildString {
            append(authors.take(3).joinToString(", ").ifEmpty { "o. A." })
            if (authors.size > 3) append(" et al.")
            append(" (").append(year?.toString() ?: "o. J.").append("). ")
            append(title)
            if (venue.isNotBlank()) append(". ").append(venue)
            doi?.let { append(". https://doi.org/").append(it) }
        }
    }

    data class Result(val input: String, val status: Status, val work: Work?, val note: String)

    /** What could be read from a reference: DOI, year, surnames and the probable title. */
    data class Parsed(val doi: String?, val year: Int?, val surnames: List<String>, val title: String)

    private val DOI = Regex("10\\.\\d{4,9}/[^\\s\"<>]+", RegexOption.IGNORE_CASE)
    private val YEAR_PAREN = Regex("\\((\\d{4})[a-z]?(?:,[^)]*)?\\)")
    private val YEAR = Regex("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)")
    private val SURNAME = Regex("([\\p{Lu}][\\p{L}'’-]+(?:\\s(?:von|van|de|der|den)\\s[\\p{Lu}][\\p{L}'’-]+)?),\\s*(?:[\\p{Lu}]\\.\\s*-?)+")
    private val APA_TITLE = Regex("\\(\\d{4}[a-z]?(?:,[^)]*)?\\)\\.?\\s*(.+?)(?:[.?!](?:\\s|$)|$)")
    private val LIST_MARKER = Regex("^\\s*(?:[-*•]|\\d+[.)]|\\[\\d+])\\s+")
    private val AUTHOR_HINT = Regex("[\\p{Lu}][\\p{L}'’-]+,\\s*[\\p{Lu}]\\.|et al\\.|\\bu\\. a\\.|&")

    /** References in a text: lines of a reference list, or lines that look like a full reference (author, year, title). */
    fun extract(text: String): List<String> {
        val lines = text.lines().map { it.replace(LIST_MARKER, "").replace(Regex("[*_]{1,2}"), "").trim() }.filter { it.isNotEmpty() }
        val refs = lines.filter { l ->
            DOI.containsMatchIn(l) || l.length >= 35 && (YEAR_PAREN.containsMatchIn(l) || YEAR.containsMatchIn(l)) &&
                AUTHOR_HINT.containsMatchIn(l) && (". " in l || ": " in l)
        }
        return refs.distinct().take(MAX_REFERENCES)
    }

    fun parse(ref: String): Parsed {
        val doi = DOI.find(ref)?.value?.trimEnd('.', ',', ';', ')', ']')
        val year = (YEAR_PAREN.find(ref)?.groupValues?.get(1) ?: YEAR.find(ref)?.value)?.toIntOrNull()
        val beforeYear = YEAR_PAREN.find(ref)?.range?.first?.let { ref.substring(0, it) } ?: ref.take(120)
        val surnames = SURNAME.findAll(beforeYear).map { it.groupValues[1] }.toList()
        val title = APA_TITLE.find(ref)?.groupValues?.get(1)?.trim()
            ?: ref.replace(DOI, "").replace(YEAR_PAREN, "").replace(SURNAME, "").trim(' ', '.', ',')
        return Parsed(doi, year, surnames, title.take(300))
    }

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace("ß", "ss").replace(Regex("[^a-z0-9 ]"), " ")

    private fun tokens(s: String) = normalize(s).split(' ').filter { it.length >= 3 && it !in STOP }.toSet()

    /** Share of the shorter title's words that appear in the other one (0…1). */
    fun titleSimilarity(a: String, b: String): Double {
        val x = tokens(a)
        val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / minOf(x.size, y.size)
    }

    private fun authorsMatch(p: Parsed, w: Work): Boolean? {
        if (p.surnames.isEmpty() || w.authors.isEmpty()) return null
        val family = w.authors.map { normalize(it.substringAfterLast(' ')) }.toSet() + w.authors.map { normalize(it) }
        return p.surnames.any { s -> val n = normalize(s).trim(); family.any { f -> f.trim() == n || f.endsWith(" $n") } }
    }

    /** How well [w] fits the reference, and what differs. */
    fun judge(p: Parsed, w: Work, viaDoi: Boolean): Pair<Status, String> {
        val sim = titleSimilarity(p.title, w.title)
        val authors = authorsMatch(p, w)
        val yearOk = p.year == null || w.year == null || kotlin.math.abs(p.year - w.year) <= 1
        val differences = buildList {
            if (sim < 0.6) add("Titel")
            if (authors == false) add("Autor:innen")
            if (!yearOk) add("Jahr (gefunden: ${w.year})")
        }
        return when {
            viaDoi && sim < 0.35 -> Status.DIFFERS to "Die DOI gehört zu einem anderen Werk: „${w.title}“."
            differences.isEmpty() -> Status.VERIFIED to (if (p.doi == null && w.doi != null) "DOI ergänzen: ${w.doi}" else "")
            sim >= 0.6 || viaDoi -> Status.DIFFERS to "Abweichend: ${differences.joinToString(", ")}."
            else -> Status.NOT_FOUND to "Kein passendes Werk gefunden."
        }
    }

    /**
     * Checks [refs] one by one (four at a time). [allowed] decides whether a host may be reached (environments with
     * restricted network access).
     */
    suspend fun check(http: OkHttpClient, refs: List<String>, allowed: (HttpUrl) -> Boolean = { true }): List<Result> = coroutineScope {
        val gate = Semaphore(4)
        refs.take(MAX_REFERENCES).map { ref -> async { gate.withPermit { checkOne(http, ref, allowed) } } }.awaitAll()
    }

    private suspend fun checkOne(http: OkHttpClient, ref: String, allowed: (HttpUrl) -> Boolean): Result {
        val p = parse(ref)
        return try {
            if (p.doi != null) {
                val w = crossrefDoi(http, p.doi, allowed)
                    ?: return Result(ref, Status.NOT_FOUND, null, "Die DOI ${p.doi} ist bei Crossref nicht registriert.")
                val (status, note) = judge(p, w, viaDoi = true)
                return Result(ref, status, w, note)
            }
            if (p.title.length < 8) return Result(ref, Status.UNCHECKED, null, "Zu wenig Angaben (Titel fehlt).")
            var best: Pair<Work, Pair<Status, String>>? = null
            for (search in listOf(::crossrefSearch, ::openAlexSearch, ::googleBooksSearch)) {
                val works = runCatching { search(http, ref, p, allowed) }.getOrDefault(emptyList())
                for (w in works) {
                    val j = judge(p, w, viaDoi = false)
                    if (best == null || j.first.ordinal < best.second.first.ordinal) best = w to j
                }
                if (best?.second?.first == Status.VERIFIED) break
            }
            best?.let { (w, j) -> if (j.first == Status.NOT_FOUND) Result(ref, Status.NOT_FOUND, null, j.second) else Result(ref, j.first, w, j.second) }
                ?: Result(ref, Status.NOT_FOUND, null, "Weder Crossref noch OpenAlex noch Google Books kennen das Werk.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(ref, Status.UNCHECKED, null, "Prüfung nicht möglich: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun getJson(http: OkHttpClient, url: HttpUrl, allowed: (HttpUrl) -> Boolean): JsonObject? {
        if (!allowed(url)) throw IllegalStateException("Zugriff auf ${url.host} ist in dieser Umgebung nicht erlaubt")
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
            http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> null
                    !resp.isSuccessful -> throw IllegalStateException("${url.host} antwortet mit HTTP ${resp.code}")
                    else -> JSON.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                }
            }
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.firstText(key: String): String? =
        ((this[key] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: text(key)

    private fun crossrefWork(o: JsonObject): Work {
        val authors = (o["author"] as? JsonArray).orEmpty().mapNotNull { a ->
            val obj = a as? JsonObject ?: return@mapNotNull null
            listOfNotNull(obj.text("given"), obj.text("family") ?: obj.text("name")).joinToString(" ").ifBlank { null }
        }
        val year = listOf("issued", "published-print", "published-online", "created").firstNotNullOfOrNull { k ->
            (((o[k] as? JsonObject)?.get("date-parts") as? JsonArray)?.firstOrNull() as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.intOrNull }
        }
        val doi = o.text("DOI")
        return Work(
            title = listOfNotNull(o.firstText("title"), o.firstText("subtitle")).joinToString(": ").ifBlank { "(ohne Titel)" },
            authors = authors,
            year = year,
            venue = o.firstText("container-title") ?: o.text("publisher").orEmpty(),
            doi = doi,
            url = doi?.let { "https://doi.org/$it" },
            source = "Crossref",
        )
    }

    private suspend fun crossrefDoi(http: OkHttpClient, doi: String, allowed: (HttpUrl) -> Boolean): Work? {
        val url = "https://api.crossref.org/works/".toHttpUrl().newBuilder().addPathSegment(doi).build()
        val msg = getJson(http, url, allowed)?.get("message") as? JsonObject ?: return null
        return crossrefWork(msg)
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun crossrefSearch(http: OkHttpClient, ref: String, p: Parsed, allowed: (HttpUrl) -> Boolean): List<Work> {
        val url = "https://api.crossref.org/works".toHttpUrl().newBuilder()
            .addQueryParameter("query.bibliographic", ref.take(400))
            .addQueryParameter("rows", "5")
            .addQueryParameter("select", "DOI,title,subtitle,author,issued,published-print,container-title,publisher,type")
            .build()
        val items = ((getJson(http, url, allowed)?.get("message") as? JsonObject)?.get("items") as? JsonArray).orEmpty()
        return items.mapNotNull { (it as? JsonObject)?.let(::crossrefWork) }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun openAlexSearch(http: OkHttpClient, ref: String, p: Parsed, allowed: (HttpUrl) -> Boolean): List<Work> {
        val url = "https://api.openalex.org/works".toHttpUrl().newBuilder()
            .addQueryParameter("search", p.title.take(200))
            .addQueryParameter("per-page", "5")
            .build()
        val results = (getJson(http, url, allowed)?.get("results") as? JsonArray).orEmpty()
        return results.mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            val authors = (o["authorships"] as? JsonArray).orEmpty().mapNotNull { a ->
                ((a as? JsonObject)?.get("author") as? JsonObject)?.text("display_name")
            }
            val doi = o.text("doi")?.removePrefix("https://doi.org/")
            val venue = (((o["primary_location"] as? JsonObject)?.get("source")) as? JsonObject)?.text("display_name").orEmpty()
            Work(o.text("title") ?: o.text("display_name") ?: return@mapNotNull null, authors, (o["publication_year"] as? JsonPrimitive)?.intOrNull, venue, doi, doi?.let { "https://doi.org/$it" } ?: o.text("id"), "OpenAlex")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun googleBooksSearch(http: OkHttpClient, ref: String, p: Parsed, allowed: (HttpUrl) -> Boolean): List<Work> {
        val q = buildString {
            append("intitle:").append(p.title.take(120))
            p.surnames.firstOrNull()?.let { append(" inauthor:").append(it) }
        }
        val url = "https://www.googleapis.com/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", q).addQueryParameter("maxResults", "5").build()
        val items = (getJson(http, url, allowed)?.get("items") as? JsonArray).orEmpty()
        return items.mapNotNull { item ->
            val info = (item as? JsonObject)?.get("volumeInfo") as? JsonObject ?: return@mapNotNull null
            val authors = (info["authors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val title = listOfNotNull(info.text("title"), info.text("subtitle")).joinToString(": ")
            val year = info.text("publishedDate")?.take(4)?.toIntOrNull()
            Work(title, authors, year, info.text("publisher").orEmpty(), null, info.text("infoLink"), "Google Books")
        }
    }

    /** Result table for the model (and the user). */
    fun report(results: List<Result>): String = buildString {
        val counts = results.groupingBy { it.status }.eachCount()
        append("Quellen-Check (${results.size} Angaben): ")
        append(Status.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(", ") { "${counts[it]} ${it.label}" })
        append("\n\n")
        results.forEachIndexed { i, r ->
            append("${i + 1}. ${r.status.symbol} ${r.status.label}: ${r.input.take(220)}\n")
            r.work?.let { append("   Gefunden (${it.source}): ${it.cite()}\n") }
            if (r.note.isNotBlank()) append("   ${r.note}\n")
        }
        append(
            "\nNenne dem Nutzer das Ergebnis knapp. Verwende nur bestätigte Quellen; korrigiere abweichende Angaben nach dem " +
                "gefundenen Eintrag und kennzeichne nicht gefundene deutlich als nicht auffindbar (möglicherweise erfunden). " +
                "Bücher ohne DOI und graue Literatur sind manchmal nicht verzeichnet – dann als „nicht prüfbar“ benennen."
        )
    }

    const val MAX_REFERENCES = 40
    private const val USER_AGENT = "ADEU-LLM/1.0 (Quellen-Check; https://github.com/ADEU-TUD)"
    private val STOP = setOf(
        "the", "and", "for", "with", "from", "into", "der", "die", "das", "und", "ein", "eine", "einer", "eines", "von", "zur",
        "zum", "den", "dem", "des", "mit", "auf", "fur", "uber", "aus", "bei", "als", "wie", "its", "their", "on", "of",
    )
}
