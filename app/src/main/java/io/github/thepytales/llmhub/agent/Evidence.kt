package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.ChatMessage
import java.text.Normalizer

/** A text the answer may cite: an attached file, a web page, a passage of the knowledge collection, a transcript. */
data class EvidenceSource(
    val label: String,
    val text: String,
    val kind: Kind,
    /** Local copy (attached PDF) – a click on the source opens it at the page. */
    val path: String? = null,
    val url: String? = null,
) {
    enum class Kind { FILE, WEB, KNOWLEDGE, TRANSCRIPT, LITERATURE }
}

/** One entry of the answer's list of evidence: „[2] Bericht.pdf, S. 4 – „wörtliches Zitat““. */
data class Citation(
    val n: Int,
    val raw: String,
    /** File name, title or address as the model wrote it. */
    val source: String,
    val page: Int? = null,
    val quote: String? = null,
    val url: String? = null,
    val doi: String? = null,
    /** Time in a transcript (mm:ss or hh:mm:ss). */
    val time: String? = null,
)

enum class CitationStatus(val label: String) {
    VERIFIED("Zitat in der Quelle gefunden"),
    PAGE_MISMATCH("Zitat gefunden, aber auf einer anderen Seite"),
    NOT_FOUND("Zitat steht nicht in der Quelle"),
    NO_QUOTE("Ohne wörtliches Zitat – nicht prüfbar"),
    UNCHECKABLE("Quelle liegt im Chat nicht vor – nicht prüfbar"),
}

data class CheckedCitation(
    val citation: Citation,
    val status: CitationStatus,
    /** The source the quote was found in (or the one the citation names). */
    val source: EvidenceSource? = null,
    /** Pages where the quote actually stands. */
    val foundPages: List<Int> = emptyList(),
) {
    val ok get() = status == CitationStatus.VERIFIED
    val wrong get() = status == CitationStatus.NOT_FOUND || status == CitationStatus.PAGE_MISMATCH
}

/** Result of the check after an answer in the Quellenmodus (AP-08). */
data class EvidenceReport(
    val citations: List<CheckedCitation>,
    /** Statements without any marker [n]. */
    val unsupported: List<String>,
    /** Markers in the text that have no entry in the list. */
    val dangling: List<Int>,
    val statements: Int,
) {
    val verified get() = citations.count { it.ok }
    val wrong get() = citations.count { it.wrong }
    val empty get() = citations.isEmpty() && unsupported.isEmpty()

    /** One line for the card: „5 Belege · 4 geprüft · 1 falsch · 2 Sätze ohne Beleg“. */
    fun summary(): String = buildList {
        add(if (citations.size == 1) "1 Beleg" else "${citations.size} Belege")
        if (verified > 0) add("$verified bestätigt")
        if (wrong > 0) add("$wrong falsch")
        val unchecked = citations.size - verified - wrong
        if (unchecked > 0) add("$unchecked nicht prüfbar")
        if (unsupported.isNotEmpty()) add(if (unsupported.size == 1) "1 Aussage ohne Beleg" else "${unsupported.size} Aussagen ohne Beleg")
    }.joinToString(" · ")

    /** Belegprotokoll as Markdown (export, „Als Dokument“). */
    fun markdown(title: String = "Belegprotokoll"): String = buildString {
        append("# ").append(title).append("\n\n")
        append(summary()).append("\n\n")
        append("| Nr. | Quelle | Stelle | Zitat | Ergebnis |\n|---|---|---|---|---|\n")
        citations.forEach { c ->
            val where = listOfNotNull(c.citation.page?.let { "S. $it" }, c.citation.time, c.citation.doi?.let { "doi:$it" }).joinToString(", ")
            val result = c.status.label + if (c.status == CitationStatus.PAGE_MISMATCH && c.foundPages.isNotEmpty()) " (S. ${c.foundPages.joinToString()})" else ""
            append("| ${c.citation.n} | ${cell(c.source?.label ?: c.citation.source)} | ${cell(where)} | ${cell(c.citation.quote.orEmpty())} | $result |\n")
        }
        if (unsupported.isNotEmpty()) {
            append("\n## Aussagen ohne Beleg\n\n")
            unsupported.forEach { append("- ").append(it).append('\n') }
        }
        if (dangling.isNotEmpty()) append("\nMarker ohne Eintrag in der Liste: ").append(dangling.joinToString { "[$it]" }).append('\n')
    }

    private fun cell(s: String) = s.replace('|', '/').replace('\n', ' ').take(160)
}

/**
 * Belegpflicht und Quellenmodus (AP-08): answers carry a marker [n] for every statement and end with a list of
 * evidence (file and page, DOI, URL, transcript time) with a literal quote. After the answer the app checks every quote
 * against the sources in the chat (attachments, web pages read, passages of the knowledge collection, transcripts) and
 * marks statements without evidence – on the device, without another request.
 */
object Evidence {
    const val PROMPT = "QUELLENMODUS – BELEGPFLICHT: Jede inhaltliche Aussage braucht einen Beleg.\n" +
        "1. Nutze nur die vorliegenden Quellen (Anhänge, Projektordner, Wissenssammlung mit search_knowledge, Literatur, Web mit " +
        "web_search/fetch_url). Lies die Stellen, bevor du sie belegst.\n" +
        "2. Setze hinter jede Aussage einen Marker wie [1] oder [1, 3]. Aussagen ohne Beleg lässt du weg oder kennzeichnest sie " +
        "ausdrücklich als eigene Einschätzung („Einschätzung:“).\n" +
        "3. Ende mit dem Abschnitt „## Belege“, je Zeile ein Eintrag in genau dieser Form:\n" +
        "[1] Dateiname oder Titel, S. 12 – „wörtliches Zitat aus der Quelle“\n" +
        "[2] Titel – https://adresse – „wörtliches Zitat“\n" +
        "[3] Transkript.txt, 00:12:30 – „wörtliches Zitat“\n" +
        "Bei Büchern und Artikeln zusätzlich doi:10.… wenn bekannt. Das Zitat ist exakt aus der Quelle kopiert (höchstens zwei " +
        "Sätze), die Seite steht in der Quelle als [Seite N]. Erfinde keine Zitate oder Seiten – die App prüft jedes Zitat."

    private val LIST_HEADING = Regex("(?im)^\\s*(#{1,4}\\s*|\\*\\*)\\s*(belege|quellen|fundstellen|nachweise|sources|references)\\b.*$")
    private val ENTRY = Regex("^\\s*\\[(\\d{1,3})]\\s*:?\\s*(.+)$")
    private val MARKER = Regex("\\[(\\d{1,3}(?:\\s*[,;–-]\\s*\\d{1,3})*)](?!\\()")
    private val QUOTE = Regex("[„“\"«»‚‘']([^„“\"«»]{8,})[“”\"»«‘’']")
    private val PAGE = Regex("(?i)\\b(?:S\\.|Seite|p\\.|pp\\.|page)\\s*(\\d{1,4})")
    private val URL = Regex("https?://[^\\s<>()\\[\\]„“\"]*[^\\s<>()\\[\\].,;:!?\"'„“]")
    private val DOI = Regex("(?i)\\b(?:doi:\\s*|https?://(?:dx\\.)?doi\\.org/)?(10\\.\\d{4,9}/[^\\s\"<>„“]+[^\\s\"<>.,;„“])")
    private val TIME = Regex("\\b(\\d{1,2}:\\d{2}(?::\\d{2})?)\\b")

    /** The answer without the list of evidence, and the entries of the list. */
    fun parse(answer: String): Pair<String, List<Citation>> {
        val heading = LIST_HEADING.findAll(answer).lastOrNull()
        val (body, list) = if (heading != null) {
            answer.substring(0, heading.range.first) to answer.substring(heading.range.last + 1)
        } else {
            // Without a heading: the entries at the very end of the answer.
            val lines = answer.lines()
            var start = lines.size
            while (start > 0 && (lines[start - 1].isBlank() || ENTRY.matches(lines[start - 1]))) start--
            lines.take(start).joinToString("\n") to lines.drop(start).joinToString("\n")
        }
        val citations = list.lines().mapNotNull { line ->
            val m = ENTRY.find(line) ?: return@mapNotNull null
            parseEntry(m.groupValues[1].toInt(), m.groupValues[2].trim())
        }.distinctBy { it.n }
        return body.trimEnd() to citations
    }

    internal fun parseEntry(n: Int, raw: String): Citation {
        val quote = QUOTE.findAll(raw).maxByOrNull { it.groupValues[1].length }?.groupValues?.get(1)?.trim()
        val url = URL.find(raw)?.value?.takeIf { !it.contains("doi.org/") }
        val doi = DOI.find(raw)?.groupValues?.get(1)
        val withoutQuote = if (quote != null) raw.substringBefore(quote).trimEnd('„', '“', '"', ' ', '–', '-', ':', '«') else raw
        val page = PAGE.find(withoutQuote)?.groupValues?.get(1)?.toIntOrNull()
        val time = TIME.find(withoutQuote)?.value?.takeIf { page == null }
        val source = withoutQuote
            .replace(URL, "").replace(DOI, "").replace(PAGE, "").replace(TIME, "")
            .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
            .trim().trim(',', '–', '-', ':', ';', ' ', '(', ')').trim()
        return Citation(n, raw, source, page, quote, url, doi, time)
    }

    /** Statements of [body]: sentences of paragraphs and list items, without headings, code and tables. */
    internal fun statements(body: String): List<String> {
        val out = mutableListOf<String>()
        var code = false
        for (line in body.lines()) {
            val t = line.trim()
            if (t.startsWith("```")) { code = !code; continue }
            if (code || t.isEmpty() || t.startsWith("#") || t.startsWith("|") || t.startsWith(">")) continue
            val text = t.removePrefix("- ").removePrefix("* ").replace(Regex("^\\d+[.)]\\s+"), "")
            text.split(Regex("(?<=[.!?])\\s+(?=[A-ZÄÖÜ„\"(])")).map { it.trim() }.filter { it.length >= 25 }.forEach(out::add)
        }
        return out
    }

    /** Statements that need evidence but have none: no marker and not marked as own assessment. */
    private fun needsEvidence(s: String): Boolean {
        val n = norm(s)
        if (MARKER.containsMatchIn(s)) return false
        if (n.startsWith("einschatzung") || n.startsWith("eigene einschatzung") || n.startsWith("hinweis") || n.startsWith("fazit") && s.length < 60) return false
        // Questions to the user, offers and lead-ins are not statements about the sources.
        if (s.trimEnd().endsWith("?") || s.trimEnd().endsWith(":")) return false
        if (Regex("^(soll ich|mochtest du|wenn du|gern|ich kann|ich habe|hier (ist|sind|die|das)|im folgenden|zusammengefasst)").containsMatchIn(n)) return false
        return true
    }

    /** The marker numbers used in [body]. */
    internal fun markers(body: String): Set<Int> = MARKER.findAll(body).flatMap { m ->
        val parts = m.groupValues[1].split(Regex("\\s*[,;]\\s*"))
        parts.flatMap { p ->
            val range = p.split(Regex("\\s*[–-]\\s*")).mapNotNull { it.trim().toIntOrNull() }
            if (range.size == 2 && range[1] in range[0]..range[0] + 20) (range[0]..range[1]).toList() else range
        }
    }.toSet()

    /** Checks the answer's evidence against [sources]. */
    fun check(answer: String, sources: List<EvidenceSource>): EvidenceReport {
        val (body, citations) = parse(answer)
        val statements = statements(body)
        val used = markers(body)
        val checked = citations.map { checkOne(it, sources) }
        return EvidenceReport(
            citations = checked,
            unsupported = statements.filter(::needsEvidence),
            dangling = (used - citations.map { it.n }.toSet()).sorted(),
            statements = statements.size,
        )
    }

    private fun checkOne(c: Citation, sources: List<EvidenceSource>): CheckedCitation {
        val named = matchSource(c, sources)
        val quote = c.quote ?: return CheckedCitation(c, if (named == null && sources.isEmpty()) CitationStatus.UNCHECKABLE else CitationStatus.NO_QUOTE, named)
        val q = norm(quote)
        if (q.length < 8) return CheckedCitation(c, CitationStatus.NO_QUOTE, named)
        // The named source first, then all others (the model may have named the file a bit differently).
        val order = listOfNotNull(named) + sources.filter { it != named }
        for (s in order) {
            val pages = pagesWith(s.text, q)
            if (pages != null) {
                val status = when {
                    c.page == null || pages.isEmpty() -> CitationStatus.VERIFIED
                    c.page in pages || pages.any { it == c.page - 1 && spansPages(s.text, q, it) } -> CitationStatus.VERIFIED
                    else -> CitationStatus.PAGE_MISMATCH
                }
                return CheckedCitation(c, status, s, pages)
            }
        }
        if (named == null && sources.none { it.kind == EvidenceSource.Kind.WEB && c.url != null }) {
            // Nothing in the chat to compare with (e.g. a book only named by DOI).
            if (sources.isEmpty() || (c.url != null || c.doi != null) && sources.none { s -> s.url != null && sameUrl(s.url, c.url) }) {
                return CheckedCitation(c, CitationStatus.UNCHECKABLE)
            }
        }
        return CheckedCitation(c, CitationStatus.NOT_FOUND, named)
    }

    /** The source a citation names: same address, file name or title. */
    internal fun matchSource(c: Citation, sources: List<EvidenceSource>): EvidenceSource? {
        c.url?.let { u -> sources.firstOrNull { it.url != null && sameUrl(it.url, u) }?.let { return it } }
        val name = norm(c.source)
        if (name.isBlank()) return null
        return sources.firstOrNull { norm(it.label) == name }
            ?: sources.firstOrNull { s -> val l = norm(s.label.substringBeforeLast('.')); l.length >= 4 && (name.contains(l) || l.contains(name)) }
            ?: sources.maxByOrNull { s -> overlap(name, norm(s.label)) }?.takeIf { s -> overlap(name, norm(s.label)) >= 0.6 }
    }

    private fun overlap(a: String, b: String): Double {
        val wa = a.split(' ').filter { it.length > 2 }.toSet()
        val wb = b.split(' ').filter { it.length > 2 }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return wa.intersect(wb).size.toDouble() / minOf(wa.size, wb.size)
    }

    private fun sameUrl(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        fun clean(u: String) = u.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        return clean(a) == clean(b)
    }

    private val PAGE_MARK = Regex("\\[Seite (\\d+)]")

    /**
     * Pages of [text] on which the normalized quote [q] stands: empty list = found in a text without page marks, null =
     * not found. A quote may run over the end of a page.
     */
    internal fun pagesWith(text: String, q: String): List<Int>? {
        val marks = PAGE_MARK.findAll(text).toList()
        if (marks.isEmpty()) return if (contains(norm(text), q)) emptyList() else null
        val pages = marks.mapIndexed { i, m ->
            val end = marks.getOrNull(i + 1)?.range?.first ?: text.length
            m.groupValues[1].toInt() to norm(text.substring(m.range.last + 1, end))
        }
        val hits = pages.filter { (_, t) -> contains(t, q) }.map { it.first }
        if (hits.isNotEmpty()) return hits
        // Over a page break: the quote stands in two neighbouring pages together.
        val spans = pages.zipWithNext().filter { (a, b) -> contains(a.second.takeLast(q.length * 2) + " " + b.second.take(q.length * 2), q) }.map { it.first.first }
        return spans.ifEmpty { if (contains(norm(PAGE_MARK.replace(text, " ")), q)) emptyList() else null }
    }

    private fun spansPages(text: String, q: String, page: Int): Boolean = pagesWith(text, q)?.contains(page) == true

    /** Exact, or nearly exact: at least 90 % of the quote's words in a row in the text (OCR, small typos, ellipses). */
    internal fun contains(text: String, q: String): Boolean {
        if (text.contains(q)) return true
        // „…“ in a quote: every part must stand in the text, in this order.
        val parts = q.split(Regex("\\s*(\\.\\.\\.|…)\\s*")).filter { it.length >= 8 }
        if (parts.size > 1) {
            var from = 0
            for (p in parts) {
                val i = text.indexOf(p, from)
                if (i < 0) return false
                from = i + p.length
            }
            return true
        }
        val qw = q.split(' ').filter { it.isNotEmpty() }
        if (qw.size < 5) return false
        val first = qw.take(3).joinToString(" ")
        val last = qw.takeLast(3).joinToString(" ")
        // Start or end must match exactly; then compare word by word in that window.
        val starts = Regex(Regex.escape(first)).findAll(text).map { it.range.first }.toList() + Regex(Regex.escape(last)).findAll(text).map { maxOf(0, it.range.first - q.length) }.toList()
        for (s in starts) {
            val window = text.substring(s, minOf(text.length, s + q.length + 40)).split(' ')
            val hits = qw.count { it in window }
            if (hits >= qw.size * 0.9) return true
        }
        return false
    }

    /** Lower case, plain quotes and dashes, hyphenation at line ends removed, spaces collapsed. */
    internal fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
        .replace(Regex("(\\w)-\\s*\\n\\s*(\\w)"), "$1$2")
        .replace('­', ' ')
        .lowercase()
        .replace(Regex("[„“”\"«»‚‘’'`´]"), "")
        .replace(Regex("[–—−]"), "-")
        .replace("ß", "ss").replace("ä", "a").replace("ö", "o").replace("ü", "u")
        .replace(Regex("[^\\p{L}\\p{N}.…\\- ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * The sources an answer of this chat could cite: attached files of the thread and the results of reading tools
     * (web pages, knowledge passages, transcripts, literature) – the texts the model actually saw.
     */
    fun sourcesOf(thread: List<ChatMessage>): List<EvidenceSource> {
        val out = mutableListOf<EvidenceSource>()
        thread.flatMap { it.files }.distinctBy { it.name }.forEach { f ->
            out += EvidenceSource(f.name, f.text, EvidenceSource.Kind.FILE, path = f.path)
        }
        val calls = thread.flatMap { it.toolCalls }.associateBy { it.id }
        thread.filter { it.role == "tool" && !it.content.startsWith("FEHLER") }.forEach { m ->
            val call = calls[m.toolCallId]
            val args = call?.let { parseArgs(it.arguments) }
            when (m.toolName ?: call?.name) {
                "fetch_url" -> {
                    val url = args?.get("url")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    out += EvidenceSource(url ?: "Webseite", m.content, EvidenceSource.Kind.WEB, url = url)
                }
                "web_search", "news_search" -> out += EvidenceSource("Websuche", m.content, EvidenceSource.Kind.WEB)
                "search_knowledge" -> out += EvidenceSource("Wissenssammlung", m.content, EvidenceSource.Kind.KNOWLEDGE)
                "read_transcript", "search_transcripts" -> out += EvidenceSource("Transkript", m.content, EvidenceSource.Kind.TRANSCRIPT)
                "read_local_file", "read_project_file", "read_file", "read_attachment", "read_document" -> {
                    val name = args?.get("path")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.substringAfterLast('/')?.substringAfterLast('\\')
                    out += EvidenceSource(name ?: "Datei", m.content, EvidenceSource.Kind.FILE)
                }
                else -> if ((m.toolName ?: "").contains("literature") || (m.toolName ?: "").startsWith("zotero")) {
                    out += EvidenceSource("Literatur", m.content, EvidenceSource.Kind.LITERATURE)
                }
            }
        }
        return out
    }

    /**
     * The answer with marks for the reader: a warning sign behind statements without evidence and behind markers whose
     * quote was not found in the source.
     */
    fun mark(answer: String, report: EvidenceReport): String {
        var out = answer
        report.unsupported.forEach { s ->
            val i = out.indexOf(s)
            if (i >= 0) out = out.substring(0, i + s.length) + " ⚠ *ohne Beleg*" + out.substring(i + s.length)
        }
        return out
    }
}
