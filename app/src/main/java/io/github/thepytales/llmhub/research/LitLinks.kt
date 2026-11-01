package io.github.thepytales.llmhub.research

import java.net.URLEncoder
import io.github.thepytales.llmhub.ui.trLater

/**
 * Links for literature: the best place to open a work, and searches in catalogues and portals that have no open
 * interface (Google Scholar, ResearchGate, BASE, FIS Bildung …) – these open in the browser instead.
 */
object LitLinks {
    data class Portal(val label: String, val description: String, val search: (String) -> String)

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    /** Portals to search by hand: the app opens the search with the query filled in. */
    val PORTALS = listOf(
        Portal("SLUB-Katalog", trLater("Bestand der SLUB Dresden mit Ausleihe, E-Books und lizenzierten Zugängen der TU Dresden")) { "https://katalog.slub-dresden.de/?tx_find_find%5Bq%5D%5Bdefault%5D=${enc(it)}" },
        Portal(trLater("Google Scholar"), trLater("Sehr breite Suche, auch Zitationen; keine Schnittstelle für Apps")) { "https://scholar.google.com/scholar?q=${enc(it)}" },
        Portal("ResearchGate", trLater("Netzwerk, in dem Forschende ihre Arbeiten hochladen – gut für Volltexte, keine Datenbank für systematische Suchen")) { "https://www.researchgate.net/search/publication?q=${enc(it)}" },
        Portal("BASE", trLater("Bielefeld Academic Search Engine: Open-Access-Dokumente aus Repositorien")) { "https://www.base-search.net/Search/Results?lookfor=${enc(it)}" },
        Portal(trLater("FIS Bildung / Fachportal Pädagogik"), trLater("Deutschsprachige Bildungsforschung (Literaturdatenbank des DIPF)")) { "https://www.fachportal-paedagogik.de/literatur/ergebnisse.html?FT=${enc(it)}" },
        Portal("pedocs", trLater("Open-Access-Volltexte der Erziehungswissenschaft")) { "https://www.pedocs.de/suche/ergebnis.php?la=de&suchfeld1=freitext&bool1=and&suchwert1=${enc(it)}" },
        Portal(trLater("Semantic Scholar"), trLater("KI-gestützte Suche mit Zitationsgraphen")) { "https://www.semanticscholar.org/search?q=${enc(it)}" },
        Portal("PubPsych", trLater("Psychologische Literatur, auch deutschsprachig (ZPID)")) { "https://pubpsych.zpid.de/pubpsych/Search.action?q=${enc(it)}" },
    )

    /** Where the work can be opened: free full text, then DOI, then the catalogue entry, then a search for its title. */
    fun best(r: Reference): String = when {
        r.openAccessUrl.isNotBlank() -> r.openAccessUrl
        r.doi.isNotBlank() -> r.doiUrl
        r.url.isNotBlank() -> r.url
        r.isbn.isNotBlank() -> "https://katalog.slub-dresden.de/?tx_find_find%5Bq%5D%5Bdefault%5D=${enc(r.isbn)}"
        else -> scholar(r)
    }

    /** The landing page (DOI or catalogue) when it differs from the full text. */
    fun landing(r: Reference): String = when {
        r.doi.isNotBlank() -> r.doiUrl
        r.url.isNotBlank() -> r.url
        else -> ""
    }

    private fun titleQuery(r: Reference) = listOf(r.title.ifBlank { r.raw }, r.creators.firstOrNull()?.family.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")

    /** Is the work at the SLUB (borrow, e-book, licensed access)? */
    fun slub(r: Reference): String = "https://katalog.slub-dresden.de/?tx_find_find%5Bq%5D%5Bdefault%5D=" +
        enc(if (r.isbn.isNotBlank()) r.isbn else titleQuery(r))

    fun scholar(r: Reference): String = "https://scholar.google.com/scholar?q=" + enc(if (r.doi.isNotBlank()) r.doi else "\"${r.title}\" ${r.creators.firstOrNull()?.family.orEmpty()}")

    fun researchGate(r: Reference): String = "https://www.researchgate.net/search/publication?q=" + enc(r.title.ifBlank { r.raw })
}
