package io.github.thepytales.llmhub.research

import io.github.thepytales.llmhub.agent.CitationStatus
import io.github.thepytales.llmhub.agent.Evidence
import io.github.thepytales.llmhub.agent.EvidenceSource
import io.github.thepytales.llmhub.ui.trLater

/** Kind of text: decides the fields of the excerpt (AP-09). */
enum class ExcerptSchema(val id: String, val label: String, val fields: List<String>) {
    EMPIRICAL(
        "empirisch", trLater("Empirische Studie"),
        listOf("Bibliografie", "Fragestellung", "Theorie", "Methode", "Stichprobe", "Zentrale Befunde", "Wörtliche Zitate", "Begriffe", "Eigene Bewertung", "Anschlussfragen"),
    ),
    THEORETICAL(
        "theoretisch", trLater("Theoretischer Text"),
        listOf("Bibliografie", "Fragestellung", "Theorie", "Zentrale Thesen und Argumentation", "Wörtliche Zitate", "Begriffe", "Eigene Bewertung", "Anschlussfragen"),
    ),
    REVIEW(
        "review", trLater("Review / Metaanalyse"),
        listOf("Bibliografie", "Fragestellung", "Methode (Suche, Einschluss, Auswertung)", "Stichprobe (Zahl und Art der Studien)", "Zentrale Befunde", "Forschungslücken", "Wörtliche Zitate", "Begriffe", "Eigene Bewertung", "Anschlussfragen"),
    ),
    CHAPTER(
        "kapitel", trLater("Buchkapitel"),
        listOf("Bibliografie", "Fragestellung", "Einordnung im Buch", "Theorie", "Zentrale Thesen", "Wörtliche Zitate", "Begriffe", "Eigene Bewertung", "Anschlussfragen"),
    );

    companion object {
        /** The standard fields of the plan (empirical texts). */
        val STANDARD get() = EMPIRICAL.fields

        fun parse(s: String?): ExcerptSchema? {
            val n = s?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == n || it.name.lowercase() == n || it.label.lowercase() == n || it.label.lowercase().startsWith(n) && n.length >= 4 }
        }
    }
}

/** What [Excerpts.check] found: the quotes with page numbers and whether they stand on that page. */
data class ExcerptCheck(val quotes: Int, val verified: Int, val problems: List<String>, val unchecked: Int) {
    val ok get() = problems.isEmpty()

    fun summary(): String = buildString {
        append(if (quotes == 1) "1 Zitat" else "$quotes Zitate")
        if (quotes > 0) append(", davon $verified mit bestätigter Seite")
        if (unchecked > 0) append(", $unchecked nicht prüfbar (kein PDF-Text)")
        if (problems.isNotEmpty()) append(", ${problems.size} falsch")
    }
}

/**
 * Exzerpt-Agent (AP-09): the excerpt follows a schema per kind of text, every quote carries its page, and the app checks
 * the pages against the text of the PDF (its [Seite N] marks) before the excerpt is attached to the literature entry
 * and saved as a document. Builds on the TU Vorlage „Literatur exzerpieren“; own schemas come as TU Vorlagen.
 */
object Excerpts {
    const val TEMPLATE_ID = "b_res_excerpt"

    val MODE_PROMPT: String get() = "EXZERPT-MODUS: Du erstellst ein Exzerpt des angehängten oder genannten Textes.\n" +
        "1. Bestimme die Textart: " + ExcerptSchema.entries.joinToString("; ") { "${it.id} (${it.label}): ${it.fields.joinToString(", ")}" } + ".\n" +
        "2. Schreibe das Exzerpt in Markdown: Überschrift mit Kurzbeleg, dann je Feld ein Abschnitt „## Feld“. Unter Bibliografie die " +
        "vollständige Angabe (APA 7, mit DOI). Wörtliche Zitate als Liste, jedes so: > „Zitat“ (S. 12) – exakt aus dem Text, die Seite " +
        "steht im Text als [Seite N]. Paraphrasen ebenfalls mit Seitenangabe (S. N). Eigene Bewertung klar als eigene Einschätzung.\n" +
        "3. Rufe dann save_excerpt auf (title, schema, markdown, doi wenn bekannt, reference_id wenn im Auftrag genannt). Die App prüft " +
        "jede Seitenangabe gegen das PDF; meldet sie Fehler, korrigiere Zitat oder Seite und rufe save_excerpt erneut auf.\n" +
        "4. Mehrere Texte: nacheinander, je Text ein save_excerpt. Fehlt der Text, bitte darum, das PDF anzuhängen."

    private val QUOTE_WITH_PAGE = Regex("[„“\"»]([^„“\"«»]{8,}?)[“”\"«]\\s*\\(\\s*(?:S\\.|Seite|p\\.)\\s*(\\d{1,4})(?:\\s*[f–-]+\\s*\\d{0,4})?\\s*\\)")

    /** The quotes of [markdown] with the page given behind them. */
    fun quotes(markdown: String): List<Pair<String, Int>> = QUOTE_WITH_PAGE.findAll(markdown).map { it.groupValues[1].trim() to it.groupValues[2].toInt() }.toList()

    /**
     * Checks the page of every quote in [markdown] against [sources] (attached PDFs with page marks). A quote that stands
     * on another page or not at all is a problem the model has to fix.
     */
    fun check(markdown: String, sources: List<EvidenceSource>): ExcerptCheck {
        val quotes = quotes(markdown)
        val withPages = sources.filter { it.text.contains("[Seite ") }
        if (withPages.isEmpty()) return ExcerptCheck(quotes.size, 0, emptyList(), quotes.size)
        var verified = 0
        val problems = mutableListOf<String>()
        quotes.forEach { (quote, page) ->
            val q = Evidence.norm(quote)
            val found = withPages.firstNotNullOfOrNull { s -> Evidence.pagesWith(s.text, q) }
            when {
                found == null -> problems += "„${quote.take(80)}“ (S. $page) steht nicht im Text."
                found.isEmpty() || page in found || (page - 1) in found -> verified++
                else -> problems += "„${quote.take(80)}“ steht nicht auf S. $page, sondern auf S. ${found.joinToString()}."
            }
        }
        return ExcerptCheck(quotes.size, verified, problems, 0)
    }

    /** The excerpt with a short note of the check at the end (kept at the literature entry). */
    fun withCheckNote(markdown: String, check: ExcerptCheck): String =
        markdown.trimEnd() + "\n\n---\n*Seitenangaben geprüft: ${check.summary()}.*" +
            if (check.problems.isNotEmpty()) "\n\n" + check.problems.joinToString("\n") { "- ⚠ $it" } else ""

    /** The literature entry an excerpt belongs to: by id, DOI or (nearly) the same title. */
    fun findReference(refs: List<Reference>, id: String?, doi: String?, title: String?): Reference? {
        id?.takeIf { it.isNotBlank() }?.let { i -> refs.firstOrNull { it.id == i }?.let { return it } }
        doi?.trim()?.lowercase()?.removePrefix("https://doi.org/")?.removePrefix("doi:")?.takeIf { it.isNotBlank() }?.let { d ->
            refs.firstOrNull { it.doi.lowercase().removePrefix("https://doi.org/") == d }?.let { return it }
        }
        val t = title?.let { Evidence.norm(it) }?.takeIf { it.length >= 8 } ?: return null
        return refs.firstOrNull { Evidence.norm(it.title) == t } ?: refs.firstOrNull { r -> val n = Evidence.norm(r.title); n.length >= 12 && (n.startsWith(t) || t.startsWith(n)) }
    }

    /** Status of a checked quote in the evidence terms of AP-08. */
    fun status(found: List<Int>?, page: Int): CitationStatus = when {
        found == null -> CitationStatus.NOT_FOUND
        found.isEmpty() || page in found -> CitationStatus.VERIFIED
        else -> CitationStatus.PAGE_MISMATCH
    }
}
