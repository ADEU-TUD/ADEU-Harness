package io.github.thepytales.llmhub.research

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Collator
import java.util.Locale
import io.github.thepytales.llmhub.ui.trLater

/** How an entry of the reference list was checked online. */
enum class EntryStatus(val symbol: String, val label: String) {
    FOUND("✓", trLater("gefunden, Angaben stimmen")),
    COMPLETED("✚", trLater("gefunden und ergänzt")),
    CORRECTED("≈", trLater("gefunden, Angaben abweichend")),
    NOT_FOUND("✗", trLater("nicht gefunden")),
    UNCHECKED("·", trLater("nicht geprüft")),
}

/** One entry after checking: the reference, what was added and what is still missing. */
data class BibEntry(
    val ref: Reference,
    val status: EntryStatus = EntryStatus.UNCHECKED,
    /** Labels of the fields filled in from the databases ("DOI", "Seiten" …). */
    val added: List<String> = emptyList(),
    /** Labels of fields the style needs that are still empty. */
    val missing: List<String> = emptyList(),
    /** Differences or problems found ("Jahr laut Crossref 2019"). */
    val notes: List<String> = emptyList(),
    /** Where the completion came from (Crossref, DNB …). */
    val via: String = "",
)

/** A finished reference list: entries in order and the formatted lines. */
data class BibResult(val style: CitationStyle, val entries: List<BibEntry>, val lines: List<String>) {
    fun markdown(heading: String? = if (style.german) "Literaturverzeichnis" else "References"): String = buildString {
        if (heading != null) append("# ").append(heading).append("\n\n")
        lines.forEach { append(it).append("\n\n") }
    }.trimEnd() + "\n"

    /** Plain text without Markdown asterisks (for pasting into Word with own formatting). */
    fun plain(): String = lines.joinToString("\n") { it.replace("*", "") }

    /** Check report for the model or the user. */
    fun report(): String = buildString {
        val counts = entries.groupingBy { it.status }.eachCount()
        append("${entries.size} Einträge, ${style.label}, alphabetisch sortiert. ")
        append(EntryStatus.entries.filter { (counts[it] ?: 0) > 0 }.joinToString(", ") { "${counts[it]} ${it.label}" })
        append(".")
        val details = entries.filter { it.added.isNotEmpty() || it.missing.isNotEmpty() || it.notes.isNotEmpty() || it.status == EntryStatus.NOT_FOUND }
        if (details.isNotEmpty()) {
            append("\n\nPrüfbericht:")
            details.forEach { e ->
                append("\n- ").append(e.status.symbol).append(" ").append(e.ref.short().take(110))
                if (e.added.isNotEmpty()) append(" – ergänzt (${e.via}): ").append(e.added.joinToString(", "))
                if (e.missing.isNotEmpty()) append(" – fehlt noch: ").append(e.missing.joinToString(", "))
                if (e.notes.isNotEmpty()) append(" – ").append(e.notes.joinToString("; "))
                if (e.status == EntryStatus.NOT_FOUND) append(" – in keiner Datenbank gefunden, bitte von Hand prüfen")
            }
        }
    }
}

object Bibliography {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
    private val PARTICLE = Regex("^(von|van|de|der|den|zu)\\s+", RegexOption.IGNORE_CASE)
    private val LEADING_ARTICLE = Regex("^(der|die|das|ein|eine|the|a|an|le|la|les)\\s+", RegexOption.IGNORE_CASE)

    /** Alphabetical key: first author's surname (without particles like "von"), then the further authors, year, title. */
    fun sortKey(r: Reference): String {
        val names = r.creators.joinToString(" ") { p ->
            val fam = if (p.literal) p.family else p.family.replace(PARTICLE, "")
            "$fam ${p.given}"
        }
        val title = (r.title.ifBlank { r.raw.replace(PARTICLE, "") }).replace(LEADING_ARTICLE, "")
        return (if (names.isBlank()) title else names) + " \u0001" + r.year.ifBlank { "9999" } + " " + title
    }

    /** Fields the reference needs in [style] that are still empty. */
    fun missing(r: Reference, style: CitationStyle): List<String> {
        if (r.unparsed) return listOf("Angaben nicht lesbar")
        val out = mutableListOf<String>()
        fun need(ok: Boolean, label: String) { if (!ok) out += label }
        val place = style.usesPlace
        when (r.type) {
            RefType.ARTICLE -> {
                need(r.authors.isNotEmpty(), "Autor:innen"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
                need(r.container.isNotBlank(), "Zeitschrift"); need(r.volume.isNotBlank(), "Jahrgang/Band"); need(r.pages.isNotBlank(), "Seiten")
            }
            RefType.BOOK -> {
                need(r.authors.isNotEmpty(), "Autor:innen"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
                need(r.publisher.isNotBlank(), "Verlag"); if (place) need(r.place.isNotBlank(), "Verlagsort")
            }
            RefType.EDITED_BOOK -> {
                need(r.editors.isNotEmpty(), "Herausgeber:innen"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
                need(r.publisher.isNotBlank(), "Verlag"); if (place) need(r.place.isNotBlank(), "Verlagsort")
            }
            RefType.CHAPTER -> {
                need(r.authors.isNotEmpty(), "Autor:innen"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
                need(r.editors.isNotEmpty(), "Herausgeber:innen"); need(r.container.isNotBlank(), "Titel des Sammelbands")
                need(r.pages.isNotBlank(), "Seiten"); need(r.publisher.isNotBlank(), "Verlag"); if (place) need(r.place.isNotBlank(), "Verlagsort")
            }
            RefType.CONFERENCE -> {
                need(r.authors.isNotEmpty(), "Autor:innen"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
                need(r.container.isNotBlank(), "Tagungsband")
            }
            RefType.THESIS -> {
                need(r.authors.isNotEmpty(), "Autor:in"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
                need(r.institution.isNotBlank(), "Hochschule")
            }
            RefType.REPORT -> {
                need(r.creators.isNotEmpty() || r.institution.isNotBlank() || r.publisher.isNotBlank(), "Autor:innen oder herausgebende Stelle")
                need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
            }
            RefType.WEB -> {
                need(r.title.isNotBlank(), "Titel"); need(r.url.isNotBlank() || r.doi.isNotBlank(), "URL")
                need(r.accessed.isNotBlank() || style == CitationStyle.APA7_DE || style == CitationStyle.APA7_EN, "Abrufdatum")
            }
            RefType.OTHER -> {
                need(r.creators.isNotEmpty(), "Autor:innen"); need(r.year.isNotBlank(), "Jahr"); need(r.title.isNotBlank(), "Titel")
            }
        }
        return out
    }

    /** Sorts, removes duplicates, marks same author and year with a/b and formats. */
    fun build(entries: List<BibEntry>, style: CitationStyle): BibResult {
        val unique = entries.distinctBy { if (it.ref.unparsed) "raw:" + it.ref.raw else it.ref.dedupeKey }
        val sorted = unique.sortedWith { a, b -> collator.compare(sortKey(a.ref), sortKey(b.ref)) }
        val suffixes = mutableMapOf<String, String>()
        if (style.authorYear) {
            sorted.groupBy { e -> e.ref.creators.joinToString("|") { Reference.normalize(it.family) } + "#" + e.ref.year }
                .filter { (key, group) -> group.size > 1 && !key.startsWith("#") }
                .forEach { (_, group) ->
                    group.sortedWith { a, b -> collator.compare(a.ref.title.replace(LEADING_ARTICLE, ""), b.ref.title.replace(LEADING_ARTICLE, "")) }
                        .forEachIndexed { i, e -> suffixes[e.ref.id] = ('a' + i).toString() }
                }
        }
        val finalEntries = sorted.map { it.copy(missing = missing(it.ref, style)) }
        val lines = finalEntries.mapIndexed { i, e -> CitationFormatter.format(e.ref, style, suffixes[e.ref.id].orEmpty(), i + 1) }
        return BibResult(style, finalEntries, lines)
    }

    private val FIELD_LABELS = linkedMapOf<String, (Reference) -> String>(
        "Autor:innen" to { r -> r.authors.joinToString { it.family } },
        "Vornamen" to { r -> r.authors.joinToString { it.given } },
        "Herausgeber:innen" to { r -> r.editors.joinToString { it.family } },
        "Jahr" to { it.year },
        "Titel" to { it.title },
        "Zeitschrift/Sammelband" to { it.container },
        "Jahrgang/Band" to { it.volume },
        "Heft" to { it.issue },
        "Seiten" to { it.pages },
        "Auflage" to { it.edition },
        "Verlag" to { it.publisher },
        "Verlagsort" to { it.place },
        "DOI" to { it.doi },
        "ISBN" to { it.isbn },
        "ISSN" to { it.issn },
        "Hochschule" to { it.institution },
    )

    /** Fills empty fields of [mine] from [found]; returns the merged reference and the labels of what was added. */
    fun merge(given: Reference, found: Reference): Pair<Reference, List<String>> {
        // Free text that could not be read takes everything from the database record.
        val mine = if (given.unparsed) given.copy(type = RefType.OTHER, authors = emptyList(), editors = emptyList(), year = "", title = "", doi = "") else given
        val keepType = mine.type != RefType.OTHER && !(mine.type == RefType.BOOK && found.type == RefType.EDITED_BOOK && mine.authors.isEmpty())
        val merged = mine.copy(
            type = if (keepType) mine.type else found.type,
            // "Müller et al." or only the first author: the full list of the record, if it starts with the same person.
            authors = if (mine.authors.isNotEmpty() && found.authors.size > mine.authors.size &&
                Reference.normalize(found.authors.first().family) == Reference.normalize(mine.authors.first().family)) found.authors
            else mine.authors.ifEmpty { found.authors },
            editors = mine.editors.ifEmpty { found.editors },
            year = mine.year.ifBlank { found.year },
            title = mine.title.ifBlank { found.title },
            container = mine.container.ifBlank { found.container },
            volume = mine.volume.ifBlank { found.volume },
            issue = mine.issue.ifBlank { found.issue },
            pages = mine.pages.ifBlank { found.pages },
            edition = mine.edition.ifBlank { found.edition },
            // Journals are cited without publisher and place.
            publisher = mine.publisher.ifBlank { if (mine.type == RefType.ARTICLE) "" else found.publisher },
            place = mine.place.ifBlank { if (mine.type == RefType.ARTICLE) "" else found.place },
            doi = mine.doi.ifBlank { found.doi },
            url = mine.url.ifBlank { if (found.doi.isBlank()) found.url else "" },
            isbn = mine.isbn.ifBlank { found.isbn },
            issn = mine.issn.ifBlank { found.issn },
            institution = mine.institution.ifBlank { found.institution },
            genre = mine.genre.ifBlank { found.genre },
            abstract = mine.abstract.ifBlank { found.abstract },
            language = mine.language.ifBlank { found.language },
            raw = "",
        )
        val added = FIELD_LABELS.filter { (label, get) ->
            get(mine).isBlank() && get(merged).isNotBlank() || label == "Autor:innen" && mine.authors.isNotEmpty() && merged.authors.size > mine.authors.size
        }.keys.toList().map { if (it == "Autor:innen" && mine.authors.isNotEmpty()) "weitere Autor:innen" else it }
        return merged to (if (given.unparsed) listOf("alle Angaben aus der Datenbank") else added)
    }

    /** How well a database record fits the given reference (0…1) and what differs. */
    fun similarity(mine: Reference, found: Reference): Pair<Double, List<String>> {
        val titleA = mine.title.ifBlank { mine.raw }
        val sim = io.github.thepytales.llmhub.agent.ReferenceCheck.titleSimilarity(titleA, found.title)
        val differences = mutableListOf<String>()
        val myNames = mine.creators.map { Reference.normalize(it.family) }.filter { it.isNotBlank() }
        val theirNames = found.creators.map { Reference.normalize(it.family) }
        val authorsOk = myNames.isEmpty() || theirNames.isEmpty() || myNames.any { n -> theirNames.any { it == n || it.endsWith(" $n") || n.endsWith(" $it") } } ||
            mine.unparsed && theirNames.any { Reference.normalize(mine.raw).contains(it) }
        if (!authorsOk) differences += "Autor:innen laut Datenbank: " + found.creators.take(3).joinToString(", ") { it.family }
        val y1 = mine.year.toIntOrNull()
        val y2 = found.year.toIntOrNull()
        if (y1 != null && y2 != null && kotlin.math.abs(y1 - y2) > 1) differences += "Jahr laut Datenbank: $y2"
        val score = sim * (if (authorsOk) 1.0 else 0.6) * (if (differences.any { it.startsWith("Jahr") }) 0.5 else 1.0)
        return score to differences
    }

    /**
     * Checks every reference against the databases and fills in what is missing: DOI first (Crossref), then a
     * bibliographic search (Crossref), for books the German National Library and Google Books, finally OpenAlex.
     * Nothing is invented: only fields of a record that clearly matches are taken over.
     */
    suspend fun complete(sources: LiteratureSources, refs: List<Reference>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<BibEntry> = coroutineScope {
        val gate = Semaphore(4)
        var done = 0
        refs.map { r ->
            async {
                gate.withPermit {
                    val e = runCatching { completeOne(sources, r) }.getOrElse { err ->
                        if (err is CancellationException) throw err
                        BibEntry(r, EntryStatus.UNCHECKED, notes = listOf("Prüfung nicht möglich: ${err.message ?: err.javaClass.simpleName}"))
                    }
                    synchronized(this@coroutineScope) { done++; onProgress(done, refs.size) }
                    e
                }
            }
        }.awaitAll()
    }

    private suspend fun completeOne(s: LiteratureSources, r: Reference): BibEntry {
        // 1. DOI → Crossref
        if (r.doi.isNotBlank()) {
            val found = runCatching { s.crossrefDoi(r.doi) }.getOrNull()
            if (found != null) {
                val (score, diffs) = similarity(r, found)
                if (r.title.isNotBlank() && score < 0.35) {
                    return BibEntry(r, EntryStatus.CORRECTED, notes = listOf("Die DOI gehört zu einem anderen Werk: „${found.title.take(90)}“"), via = "Crossref")
                }
                return finish(r, found, diffs, "Crossref")
            }
        }
        // Free text is compared by what could be read from it (title, authors, year).
        val probe = if (r.unparsed) FreeText.parse(r.raw).let { p -> if (p.title.isBlank()) r else p.copy(raw = "") } else r
        val title = probe.title.ifBlank { r.raw }
        val firstAuthor = probe.creators.firstOrNull()?.family
        val query = if (r.unparsed) r.raw else listOfNotNull(firstAuthor, r.year.ifBlank { null }, title, r.container.ifBlank { null }).joinToString(" ")
        val candidates = mutableListOf<Pair<Reference, String>>()
        val bookLike = r.type in setOf(RefType.BOOK, RefType.EDITED_BOOK, RefType.THESIS) || r.type == RefType.OTHER && r.container.isBlank()
        // 2. Crossref bibliographic search
        runCatching { s.crossrefSearch(query, 5, bibliographic = true) }.getOrNull()?.forEach { candidates += it to "Crossref" }
        var best = pick(probe, candidates)
        // 3. Books: German National Library (place and publisher), then Google Books
        if (bookLike && (best == null || best.first.place.isBlank()) && title.length >= 4) {
            val dnb = runCatching { if (r.isbn.isNotBlank()) s.dnbIsbn(r.isbn) else s.dnbTitle(title, firstAuthor, probe.year) }.getOrNull()
            if (dnb != null) {
                val cand = listOf(dnb to "DNB")
                val fromDnb = pick(probe, cand)
                if (best == null) best = fromDnb
                else if (fromDnb != null) {
                    // Place and publisher of German books are better in the DNB.
                    val (merged, _) = merge(best.first, fromDnb.first)
                    best = Triple(merged, best.second + " und DNB", best.third)
                }
            }
            if (best == null) {
                runCatching { s.googleBooks(title, firstAuthor) }.getOrNull()?.forEach { candidates += it to "Google Books" }
                best = pick(probe, candidates)
            }
        }
        // 4. OpenAlex (also grey literature and works without DOI)
        if (best == null && title.length >= 8) {
            runCatching { s.openAlexSearch(title, 5) }.getOrNull()?.forEach { candidates += it to "OpenAlex" }
            best = pick(probe, candidates)
        }
        best ?: return BibEntry(r, EntryStatus.NOT_FOUND)
        return finish(r, best.first, best.third, best.second)
    }

    /** The best matching candidate, or null if none matches clearly enough. */
    private fun pick(r: Reference, candidates: List<Pair<Reference, String>>): Triple<Reference, String, List<String>>? {
        val scored = candidates.map { (c, src) -> Triple(c, src, similarity(r, c)) }.maxByOrNull { it.third.first } ?: return null
        val (c, src, sim) = scored
        return if (sim.first >= 0.75) Triple(c, src, sim.second) else null
    }

    private fun finish(r: Reference, found: Reference, diffs: List<String>, via: String): BibEntry {
        val (merged, added) = merge(r, found)
        val status = when {
            diffs.isNotEmpty() -> EntryStatus.CORRECTED
            added.isNotEmpty() -> EntryStatus.COMPLETED
            else -> EntryStatus.FOUND
        }
        return BibEntry(merged.copy(source = merged.source.ifBlank { via }), status, added, notes = diffs, via = via)
    }
}
