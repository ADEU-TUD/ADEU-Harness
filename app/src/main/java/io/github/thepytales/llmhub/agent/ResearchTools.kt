package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.net.JSON
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.research.BibEntry
import io.github.thepytales.llmhub.research.BibTeX
import io.github.thepytales.llmhub.research.Bibliography
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.CslJson
import io.github.thepytales.llmhub.research.LitSource
import io.github.thepytales.llmhub.research.LiteratureSources
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.research.ReferenceImport
import io.github.thepytales.llmhub.research.Ris
import io.github.thepytales.llmhub.research.SearchFilter
import io.github.thepytales.llmhub.research.ZoteroClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import java.io.File
import java.time.LocalDate

/**
 * Literature: searching the open databases (OpenAlex, Crossref, ERIC, German National Library), snowballing,
 * keeping works in the collection, reference lists in eight citation styles with completed details, and Zotero.
 */
class ResearchTools(private val c: AppContainer) : ToolProvider {
    /** Works of recent results by their id, so later calls (details, saving) need no new search. */
    private val seen = object : LinkedHashMap<String, Reference>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Reference>?) = size > 600
    }

    fun sources(ctx: RepoContext?): LiteratureSources = LiteratureSources(
        c.llm.http, { c.settings.current.openAlexKey }, { url: HttpUrl -> ctx?.env?.allowsHost(url.host) != false },
    )

    val zotero by lazy { ZoteroClient(c.llm.http, { c.settings.current.zoteroUserId }, { c.settings.current.zoteroKey }) }

    override val defs = listOf(
        ToolDef(
            "search_literature",
            "Sucht wissenschaftliche Literatur in offenen Datenbanken: OpenAlex (alle Fächer, mit Abstracts und Zitationen), " +
                "Crossref (alles mit DOI), ERIC (Bildungsforschung, englisch) und Deutsche Nationalbibliothek (deutschsprachige " +
                "Bücher und Hochschulschriften). Liefert je Treffer eine id, Autor:innen, Jahr, Titel, Quelle, Zitationen, " +
                "Open-Access-Link und den Anfang des Abstracts. Mehrmals mit verschiedenen Suchbegriffen (deutsch und englisch) aufrufen.",
            listOf(
                Param("query", "Suchbegriffe, z.B. \"classroom management beginning teachers\" (Phrasen in Anführungszeichen)"),
                Param("sources", "Kommagetrennt: openalex, crossref, eric, dnb (Standard: die in den Einstellungen gewählten)", required = false),
                Param("from_year", "Frühestes Erscheinungsjahr", required = false, type = "integer"),
                Param("to_year", "Spätestes Erscheinungsjahr", required = false, type = "integer"),
                Param("types", "Kommagetrennt: article, book, chapter, thesis, report (Standard: alle)", required = false),
                Param("languages", "Kommagetrennt, z.B. de,en (nur OpenAlex)", required = false),
                Param("open_access", "true: nur frei zugängliche Volltexte", required = false, type = "boolean"),
                Param("sort", "relevance (Standard) oder citations", required = false),
                Param("max_results", "Treffer je Datenbank (Standard 10, max. 25)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "literature_details",
            "Zeigt alle Angaben zu einem Werk aus den Suchergebnissen: vollständiges Abstract, Quelle, DOI, Open-Access-Link.",
            listOf(Param("id", "id aus search_literature (meist die DOI)")),
        ),
        ToolDef(
            "snowball_literature",
            "Schneeballsuche über OpenAlex: backward = Literatur, die das Werk zitiert (sein Literaturverzeichnis); " +
                "forward = neuere Arbeiten, die das Werk zitieren. Nach Zitationen sortiert.",
            listOf(
                Param("id", "DOI oder id des Ausgangswerks"),
                Param("direction", "backward oder forward"),
                Param("max_results", "Anzahl (Standard 15, max. 40)", required = false, type = "integer"),
                Param("from_year", "Nur ab diesem Jahr (vor allem für forward)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            "save_literature",
            "Speichert ausgewählte Werke in der Literatursammlung der App, in einer Liste mit Namen (z.B. dem Thema). Der " +
                "Nutzer sieht die Liste im Chat und unter „Literatur“ und kann daraus Literaturverzeichnisse erstellen und nach " +
                "Zotero übertragen. Nur Werke aus den Suchergebnissen oder vom Nutzer angegebene.",
            listOf(
                Param("list", "Name der Liste, z.B. \"Classroom Management\""),
                Param("items", "JSON-Liste [{\"id\": \"…\", \"note\": \"warum relevant (1 Satz)\"}] – id aus den Suchergebnissen", required = false),
                Param("references", "Alternativ: Literaturangaben als Text, BibTeX oder RIS", required = false),
            ),
        ),
        ToolDef(
            "format_bibliography",
            "Erstellt ein alphabetisch sortiertes Literaturverzeichnis in einem Zitierstil (APA 7 deutsch/englisch, " +
                "Erziehungswissenschaft/ZfPäd, DIN ISO 690, Harvard, Chicago, MLA, IEEE). Fehlende Angaben (DOI, Seiten, " +
                "Verlag, Ort, Heft …) ergänzt die App aus Crossref, DNB, Google Books und OpenAlex; gleiche Autor:innen und Jahr " +
                "bekommen a/b. Gib das Ergebnis unverändert weiter – formatiere es nicht selbst um.",
            listOf(
                Param("references", "Literaturangaben als Text (je Angabe eine Zeile), BibTeX, RIS oder CSL-JSON", required = false),
                Param("list", "Oder: Name einer Liste der Literatursammlung", required = false),
                Param("style", "apa7_de (Standard aus den Einstellungen), apa7_en, zfpaed, din_iso_690, harvard, chicago, mla9, ieee", required = false),
                Param("complete", "Fehlende Angaben online ergänzen (Standard true)", required = false, type = "boolean"),
                Param("export", "Optional zusätzlich als Datei: bibtex, ris oder csl-json (für Zotero, Citavi, LaTeX)", required = false),
            ),
        ),
        ToolDef(
            "zotero_add",
            "Überträgt Werke in die Zotero-Bibliothek des Nutzers (optional in eine Sammlung). Nur wenn Zotero eingerichtet ist.",
            listOf(
                Param("list", "Name einer Liste der Literatursammlung", required = false),
                Param("items", "Oder ids aus den Suchergebnissen, kommagetrennt oder als JSON-Liste", required = false),
                Param("collection", "Name der Zotero-Sammlung (wird angelegt, falls nötig)", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            "zotero_search",
            "Durchsucht die Zotero-Bibliothek des Nutzers (Titel, Autor:innen, Jahr, Schlagwörter).",
            listOf(Param("query", "Suchbegriffe")),
        ),
    )

    override fun risk(name: String, args: JsonObject) = if (name == "zotero_add") Risk.EDIT else null

    override fun parallelSafe(name: String) = name in setOf("search_literature", "literature_details", "snowball_literature", "zotero_search")

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val online = Privacy.level(c, ctx.folderId).allowsNetworkTools && ctx.env?.network != io.github.thepytales.llmhub.data.NetworkAccess.NONE
        if (!online && name != "format_bibliography" && name != "save_literature") {
            return ToolOutput("FEHLER: In diesem Projekt bzw. dieser Umgebung sind keine Internetzugriffe erlaubt – die Literatursuche ist gesperrt.")
        }
        return when (name) {
            "search_literature" -> search(args, ctx)
            "literature_details" -> details(args.req("id"), ctx)
            "snowball_literature" -> snowball(args, ctx)
            "save_literature" -> save(args, ctx, online)
            "format_bibliography" -> bibliography(args, ctx, online)
            "zotero_add" -> zoteroAdd(args, ctx)
            "zotero_search" -> zoteroSearch(args.req("query"))
            else -> ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
        }
    }

    /** Stable id of a work for the model: DOI, else source and its own id. */
    fun idOf(r: Reference, sources: LiteratureSources? = null): String = when {
        r.doi.isNotBlank() -> r.doi.lowercase()
        r.source == "OpenAlex" -> "openalex:" + (sources?.openAlexIds?.get(r.dedupeKey) ?: Reference.normalize(r.title).take(30).replace(' ', '-'))
        r.source == "ERIC" -> "eric:" + r.url.substringAfter("id=", "")
        r.isbn.isNotBlank() -> "isbn:" + r.isbn
        else -> (r.source.lowercase().ifBlank { "werk" }) + ":" + Reference.normalize(r.title).take(40).replace(' ', '-') + "-" + r.year
    }

    private fun remember(refs: List<Reference>, sources: LiteratureSources): List<Pair<String, Reference>> = synchronized(seen) {
        refs.map { r ->
            val id = idOf(r, sources)
            val prev = seen[id]
            val merged = if (prev != null) Bibliography.merge(prev, r).first.copy(citations = r.citations ?: prev.citations, openAccessUrl = prev.openAccessUrl.ifBlank { r.openAccessUrl }) else r
            seen[id] = merged
            id to merged
        }
    }

    fun parseFilter(args: JsonObject) = SearchFilter(
        fromYear = args.int("from_year"),
        toYear = args.int("to_year"),
        types = args.s("types")?.split(',')?.mapNotNull { t ->
            when (t.trim().lowercase()) {
                "article", "artikel", "journal" -> RefType.ARTICLE
                "book", "buch" -> RefType.BOOK
                "chapter", "kapitel", "beitrag" -> RefType.CHAPTER
                "thesis", "dissertation" -> RefType.THESIS
                "report", "bericht" -> RefType.REPORT
                else -> null
            }
        }?.toSet().orEmpty(),
        languages = args.s("languages")?.split(',')?.map { it.trim().lowercase().take(2) }?.filter { it.length == 2 }?.toSet().orEmpty(),
        openAccessOnly = args.s("open_access")?.lowercase() == "true",
        sortByCitations = args.s("sort")?.lowercase()?.startsWith("cit") == true,
    )

    private fun line(id: String, r: Reference, abstractChars: Int = 280): String = buildString {
        append("[id: ").append(id).append("] ")
        append(r.short())
        if (r.container.isNotBlank()) append(". ").append(r.container)
        if (r.type != RefType.OTHER) append(" (").append(r.type.label).append(")")
        r.citations?.let { append(" · ").append(it).append(" Zitationen") }
        if (r.openAccessUrl.isNotBlank()) append(" · Open Access")
        if (r.language.isNotBlank()) append(" · ").append(r.language)
        if (abstractChars > 0 && r.abstract.isNotBlank()) append("\n   Abstract: ").append(r.abstract.take(abstractChars)).append(if (r.abstract.length > abstractChars) " …" else "")
    }

    private suspend fun search(args: JsonObject, ctx: RepoContext): ToolOutput = coroutineScope {
        val query = args.req("query")
        val filter = parseFilter(args)
        val max = (args.int("max_results") ?: 10).coerceIn(1, 25)
        val chosen = (args.s("sources") ?: c.settings.current.literatureSources).split(',').mapNotNull { LitSource.parse(it) }.distinct()
            .ifEmpty { LitSource.entries.toList() }
        val src = sources(ctx)
        val results = chosen.map { source ->
            async {
                source to runCatching {
                    when (source) {
                        LitSource.OPENALEX -> src.openAlexSearch(query, max, filter)
                        LitSource.CROSSREF -> src.crossrefSearch(query, max, filter)
                        LitSource.ERIC -> src.ericSearch(query, max, filter)
                        LitSource.DNB -> src.dnbSearch(query, max, filter)
                    }
                }
            }
        }.awaitAll()
        val shown = mutableSetOf<String>()
        val text = buildString {
            append("Literatursuche „$query“ (${LocalDate.now()})")
            if (filter.fromYear != null || filter.toYear != null) append(", ${filter.fromYear ?: "…"}–${filter.toYear ?: "…"}")
            append(":\n")
            results.forEach { (source, res) ->
                res.onSuccess { refs ->
                    val fresh = remember(refs, src).filter { (id, _) -> shown.add(id) }
                    append("\n## ${source.label}: ${refs.size} Treffer")
                    if (fresh.size < refs.size) append(" (${refs.size - fresh.size} schon oben)")
                    append("\n")
                    fresh.forEach { (id, r) -> append(line(id, r)).append("\n") }
                }.onFailure { e ->
                    append("\n## ${source.label}: nicht erreichbar – ${e.message ?: e.javaClass.simpleName}\n")
                }
            }
            append("\nNenne nur Werke aus diesen Ergebnissen; Angaben nicht ergänzen oder verändern.")
        }
        ToolOutput(text)
    }

    /** A work by id: from the recent results, else looked up (DOI, OpenAlex id). */
    private suspend fun resolve(id: String, ctx: RepoContext): Reference? {
        val key = id.trim().removePrefix("[id: ").removeSuffix("]").trim()
        synchronized(seen) { seen[key] ?: seen[key.lowercase()] ?: seen[Reference.cleanDoi(key).lowercase()] }?.let { return it }
        val src = sources(ctx)
        return when {
            key.startsWith("openalex:", true) -> runCatching { src.openAlexWork(key.substringAfter(':')) }.getOrNull()
            key.startsWith("eric:", true) -> runCatching { src.ericSearch("id:" + key.substringAfter(':'), 1).firstOrNull() }.getOrNull()
            key.startsWith("isbn:", true) -> runCatching { src.dnbIsbn(key.substringAfter(':')) }.getOrNull()
            Regex("10\\.\\d{4,9}/").containsMatchIn(key) -> runCatching { src.crossrefDoi(key) }.getOrNull()
                ?: runCatching { src.openAlexWork(key) }.getOrNull()
            else -> null
        }?.also { r -> synchronized(seen) { seen[key] = r } }
    }

    private suspend fun details(id: String, ctx: RepoContext): ToolOutput {
        var r = resolve(id, ctx) ?: return ToolOutput("FEHLER: Werk „$id“ nicht gefunden. Nutze eine id aus search_literature oder eine DOI.")
        // Abstracts are often only in OpenAlex.
        if (r.abstract.isBlank() && r.doi.isNotBlank()) runCatching { sources(ctx).openAlexWork(r.doi) }.getOrNull()?.let { r = Bibliography.merge(r, it).first.copy(citations = it.citations ?: r.citations) }
        val text = buildString {
            append(line(idOf(r), r, 0)).append("\n")
            append("Typ: ${r.type.label}\n")
            if (r.authors.isNotEmpty()) append("Autor:innen: ${r.authors.joinToString("; ") { it.display }}\n")
            if (r.editors.isNotEmpty()) append("Herausgeber:innen: ${r.editors.joinToString("; ") { it.display }}\n")
            listOf("Quelle" to r.container, "Band" to r.volume, "Heft" to r.issue, "Seiten" to r.pages, "Verlag" to r.publisher,
                "Ort" to r.place, "DOI" to r.doi, "URL" to r.url, "ISBN" to r.isbn, "Volltext (Open Access)" to r.openAccessUrl, "Datenbank" to r.source)
                .filter { it.second.isNotBlank() }.forEach { (k, v) -> append("$k: $v\n") }
            append("\nAbstract: ").append(r.abstract.ifBlank { "(keins verfügbar)" })
        }
        return ToolOutput(text)
    }

    private suspend fun snowball(args: JsonObject, ctx: RepoContext): ToolOutput {
        val id = args.req("id")
        val start = resolve(id, ctx)
        val key = start?.doi?.ifBlank { null } ?: sources(ctx).openAlexIds[start?.dedupeKey] ?: id.substringAfter("openalex:")
        val forward = args.req("direction").lowercase().let { it.startsWith("f") || it.startsWith("cit") || it.startsWith("vor") }
        val max = (args.int("max_results") ?: 15).coerceIn(1, 40)
        val src = sources(ctx)
        val refs = runCatching { src.openAlexSnowball(key, forward, max, SearchFilter(fromYear = args.int("from_year"))) }
            .getOrElse { return ToolOutput("FEHLER: ${it.message}") }
        if (refs.isEmpty()) return ToolOutput("Keine ${if (forward) "zitierenden Arbeiten" else "zitierten Arbeiten"} in OpenAlex gefunden.")
        val title = start?.short() ?: id
        return ToolOutput(buildString {
            append(if (forward) "Arbeiten, die „$title“ zitieren (nach Zitationen):\n" else "Literatur, die „$title“ zitiert (nach Zitationen):\n")
            remember(refs, src).forEach { (i, r) -> append(line(i, r, 200)).append("\n") }
        })
    }

    private fun itemsOf(args: JsonObject): List<Pair<String, String>> {
        val raw = args["items"] ?: return emptyList()
        val element = if (raw is JsonPrimitive && raw.isString) runCatching { JSON.parseToJsonElement(raw.content) }.getOrElse { raw } else raw
        return when (element) {
            is JsonArray -> element.mapNotNull { e ->
                when (e) {
                    is JsonObject -> e.s("id")?.let { it to e.s("note").orEmpty() }
                    is JsonPrimitive -> e.contentOrNull?.let { it to "" }
                    else -> null
                }
            }
            is JsonPrimitive -> element.content.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { it to "" }
            else -> emptyList()
        }
    }

    private suspend fun save(args: JsonObject, ctx: RepoContext, online: Boolean): ToolOutput {
        val list = args.req("list").trim()
        val refs = mutableListOf<Reference>()
        val missing = mutableListOf<String>()
        itemsOf(args).forEach { (id, note) ->
            val r = if (online) resolve(id, ctx) else synchronized(seen) { seen[id] }
            if (r == null) missing += id else refs += r.copy(note = note.ifBlank { r.note })
        }
        args.s("references")?.takeIf { it.isNotBlank() }?.let { refs += ReferenceImport.read(it) }
        if (refs.isEmpty()) return ToolOutput("FEHLER: Keine Werke angegeben oder gefunden${if (missing.isNotEmpty()) " (unbekannt: ${missing.joinToString()})" else ""}.")
        val stored = c.literature.addAll(refs, list, ctx.folderId)
        return ToolOutput(buildString {
            append("${stored.size} Werke in der Liste „$list“ gespeichert (die Liste umfasst jetzt ${c.literature.inList(list).size}). ")
            append("Der Nutzer sieht sie im Chat und unter „Literatur“.")
            if (missing.isNotEmpty()) append(" Nicht gefunden: ${missing.joinToString()}.")
        })
    }

    private suspend fun bibliography(args: JsonObject, ctx: RepoContext, online: Boolean): ToolOutput {
        val style = CitationStyle.parse(args.s("style")) ?: CitationStyle.parse(c.settings.current.citationStyle) ?: CitationStyle.APA7_DE
        val refs = buildList {
            args.s("references")?.takeIf { it.isNotBlank() }?.let { addAll(ReferenceImport.read(it)) }
            args.s("list")?.takeIf { it.isNotBlank() }?.let { name ->
                val inList = c.literature.inList(name)
                if (inList.isEmpty()) return ToolOutput("FEHLER: Die Liste „$name“ ist leer oder gibt es nicht. Vorhanden: ${c.literature.lists().joinToString().ifEmpty { "keine" }}.")
                addAll(inList)
            }
        }
        if (refs.isEmpty()) return ToolOutput("FEHLER: Keine Literaturangaben erkannt – references (Text, BibTeX, RIS) oder list angeben.")
        val complete = args.s("complete")?.lowercase() != "false"
        val entries = if (complete && online) Bibliography.complete(sources(ctx), refs) else refs.map { BibEntry(it) }
        val result = Bibliography.build(entries, style)
        val export = args.s("export")?.lowercase()?.trim()
        var file: String? = null
        if (!export.isNullOrBlank()) {
            val (ext, text) = when {
                export.startsWith("bib") -> "bib" to BibTeX.write(result.entries.map { it.ref })
                export.startsWith("ris") -> "ris" to Ris.write(result.entries.map { it.ref })
                else -> "json" to CslJson.write(result.entries.map { it.ref })
            }
            file = withContext(Dispatchers.IO) {
                val dir = c.documentTools.folder.apply { mkdirs() }
                var f = File(dir, DocumentWriter.safeName("Literatur (${style.label})") + ".$ext")
                var n = 2
                while (f.exists()) f = File(dir, DocumentWriter.safeName("Literatur (${style.label}) ($n)") + ".$ext").also { n++ }
                f.writeText(text)
                f.absolutePath
            }
        }
        lastResult = result
        return ToolOutput(
            buildString {
                append(result.markdown())
                append("\n---\n").append(result.report())
                if (!complete || !online) append("\n(Ohne Online-Ergänzung – die Angaben stehen so, wie sie gegeben wurden.)")
                append("\n\nGib das Verzeichnis oben unverändert aus (oder erstelle damit ein Dokument). Nenne kurz, was ergänzt wurde ")
                append("und was noch fehlt; erfinde fehlende Angaben nicht.")
            },
            file = file,
        )
    }

    /** The latest reference list made by the model (shown with the „Literaturverzeichnis“ actions). */
    @Volatile
    var lastResult: io.github.thepytales.llmhub.research.BibResult? = null

    private suspend fun zoteroAdd(args: JsonObject, ctx: RepoContext): ToolOutput {
        if (!zotero.configured) return ToolOutput("FEHLER: Zotero ist nicht eingerichtet. Der Nutzer trägt User-ID und API-Schlüssel unter Einstellungen › Literatur & Zotero ein.")
        val refs = buildList {
            args.s("list")?.takeIf { it.isNotBlank() }?.let { addAll(c.literature.inList(it)) }
            itemsOf(args).forEach { (id, _) -> resolve(id, ctx)?.let { add(it) } }
        }
        if (refs.isEmpty()) return ToolOutput("FEHLER: Keine Werke angegeben (list oder items).")
        val collection = args.s("collection")?.takeIf { it.isNotBlank() }?.let { runCatching { zotero.collection(it) }.getOrElse { e -> return ToolOutput("FEHLER: ${e.message}") } }
        val (ok, errors) = runCatching { zotero.add(refs, collection) }.getOrElse { return ToolOutput("FEHLER: ${it.message}") }
        return ToolOutput("$ok von ${refs.size} Werken in Zotero gespeichert" + (args.s("collection")?.let { " (Sammlung „$it“)" } ?: "") + "." +
            (if (errors.isNotEmpty()) "\nFehler: " + errors.joinToString("; ") else ""))
    }

    private suspend fun zoteroSearch(query: String): ToolOutput {
        if (!zotero.configured) return ToolOutput("FEHLER: Zotero ist nicht eingerichtet (Einstellungen › Literatur & Zotero).")
        val refs = runCatching { zotero.search(query) }.getOrElse { return ToolOutput("FEHLER: ${it.message}") }
        if (refs.isEmpty()) return ToolOutput("In Zotero nichts zu „$query“ gefunden.")
        val src = sources(null)
        return ToolOutput("Zotero (${refs.size} Treffer):\n" + remember(refs, src).joinToString("\n") { (id, r) -> line(id, r, 0) })
    }

    fun prompt(): String = "Literatur: search_literature durchsucht OpenAlex, Crossref, ERIC und die DNB; snowball_literature findet zitierte " +
        "und zitierende Arbeiten; save_literature legt Werke in der Literatursammlung ab; format_bibliography erstellt ein " +
        "alphabetisches Literaturverzeichnis im gewünschten Stil und ergänzt fehlende Angaben" +
        (if (zotero.configured) "; zotero_add und zotero_search arbeiten mit der Zotero-Bibliothek des Nutzers" else "") +
        ". Nenne nie Literatur, die du nicht über diese Werkzeuge gefunden oder geprüft hast. Wünscht der Nutzer ein " +
        "Literaturverzeichnis, nutze immer format_bibliography statt selbst zu formatieren."

    companion object {
        /** Instructions while a chat is in the literature search mode. */
        const val MODE_PROMPT = "LITERATURSUCHMODUS: Du recherchierst systematisch wissenschaftliche Literatur zu Thema und Kontext des Nutzers.\n" +
            "Vorgehen:\n" +
            "1. Ist das Thema zu vage, stelle höchstens zwei kurze Rückfragen (Fragestellung, Zielgruppe/Schulform, Zeitraum, Sprache); sonst beginne sofort.\n" +
            "2. Leite Suchbegriffe ab: Kernbegriffe auf Deutsch und Englisch, Synonyme, englische Fachtermini, engere und weitere Begriffe.\n" +
            "3. Suche mit search_literature in mehreren Durchgängen (mindestens 4 verschiedene Suchanfragen): englische Begriffe in OpenAlex und " +
            "Crossref, Bildungsthemen zusätzlich in ERIC, deutschsprachige Bücher und Sammelbände in der DNB. Nutze Filter (Jahre, Typen) sinnvoll.\n" +
            "4. Schneeballsuche: Für 2–4 zentrale, viel zitierte Arbeiten snowball_literature backward und forward.\n" +
            "5. Prüfe die Relevanz anhand von Titel und Abstract (bei Bedarf literature_details). Nimm nur Werke auf, die zum Thema passen; " +
            "bevorzuge Überblicksarbeiten, Metaanalysen, viel zitierte Grundlagenwerke und aktuelle empirische Studien.\n" +
            "6. Speichere die Auswahl (je nach gewünschtem Umfang etwa 10–40 Werke) mit save_literature in einer Liste mit dem Thema als Namen, " +
            "mit einer kurzen Relevanznotiz je Werk.\n" +
            "7. Antworte strukturiert: (a) Überblick über den Forschungsstand in 4–8 Sätzen, nur gestützt auf die Abstracts, mit Kurzbelegen " +
            "(Autor, Jahr); (b) die Literatur gruppiert nach Unterthemen als Tabelle | Werk | Typ | Worum es geht | Zugang |; (c) ein " +
            "Suchprotokoll für den Methodenteil: Datum, Datenbanken, Suchbegriffe, Filter, Trefferzahlen, Auswahlkriterien; (d) Lücken und " +
            "Hinweise, welche Fachdatenbanken die App nicht durchsuchen kann (z.B. FIS Bildung, pedocs, PsycINFO und Web of Science über die SLUB).\n" +
            "8. Biete am Ende an: ein Literaturverzeichnis im gewünschten Stil (format_bibliography mit list), den Export nach Zotero, weitere " +
            "Suche zu einem Unterthema.\n" +
            "Erfinde keine Literatur und verändere keine bibliografischen Angaben. Werke, die du nicht gefunden hast, nennst du nicht."
    }
}
