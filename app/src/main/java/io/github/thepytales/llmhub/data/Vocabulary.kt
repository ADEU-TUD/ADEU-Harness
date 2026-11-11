package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Eigene Wörter und Wissenspakete (AP-78): Lora versteht die Sprache der Professur. A [Term] is a word with its
 * explanation and other ways to say it – own entries (Einstellungen › Lora › Eigene Wörter) and the entries of installed
 * [KnowledgePacks]. The command model gets the canonical word for a synonym, the search and Strg+K find through them,
 * and language models get the matching ones as a short context (with source and date for pack entries).
 */
@Serializable
data class Term(
    @SerialName("begriff") val term: String,
    @SerialName("erklaerung") val explanation: String = "",
    @SerialName("synonyme") val synonyms: List<String> = emptyList(),
    /** Kind of entry in a pack („Einrichtung“, „Studiengang“ …); empty for own words. */
    @SerialName("art") val kind: String = "",
    /** https address of the source (pack entries must have one). */
    @SerialName("quelle") val source: String = "",
    /** Date of the source (YYYY-MM-DD). */
    @SerialName("stand") val date: String = "",
) {
    /** All spellings, the word itself first. */
    val forms: List<String> get() = (listOf(term) + synonyms).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** A term found in a text, and where it comes from (null = own word, else the title of its pack). */
data class TermHit(val term: Term, val form: String, val pack: String?)

object OwnWords {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun file(c: AppContainer) = File(c.appContext.filesDir, "eigene-woerter.json")
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    @Volatile private var cache: List<Term>? = null

    @Synchronized
    fun all(c: AppContainer): List<Term> = cache ?: runCatching { json.decodeFromString<List<Term>>(file(c).readText()) }
        .getOrDefault(emptyList()).also { cache = it }

    @Synchronized
    fun save(c: AppContainer, terms: List<Term>) {
        val clean = terms.map { t -> t.copy(term = t.term.trim(), explanation = t.explanation.trim(), synonyms = t.synonyms.map(String::trim).filter(String::isNotEmpty).distinct()) }
            .filter { it.term.isNotEmpty() }.distinctBy { it.term.lowercase() }.sortedBy { it.term.lowercase() }
        runCatching {
            val f = file(c)
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(clean))
        }
        cache = clean
        _version.value++
    }

    /** Adds or replaces the entry for [term.term]. */
    fun put(c: AppContainer, term: Term, replacing: String? = null) =
        save(c, all(c).filterNot { it.term.equals(replacing ?: term.term, true) || it.term.equals(term.term, true) } + term)

    fun remove(c: AppContainer, term: String) = save(c, all(c).filterNot { it.term.equals(term, true) })
}

object Vocabulary {
    /** Own words first (they win), then the entries of the installed packs. */
    fun all(c: AppContainer): List<Pair<Term, String?>> =
        OwnWords.all(c).map { it to null } + KnowledgePacks.installed(c).flatMap { p -> p.entries.map { it to p.title } }

    /**
     * The terms in [text]: whole words only; short abbreviations in capitals (≤ 5 letters, „ZLSB“, „NC“) only in that
     * spelling, everything else without regard to case. Each term once, in the order of [all].
     */
    fun find(text: String, terms: List<Pair<Term, String?>>): List<TermHit> {
        if (text.isBlank()) return emptyList()
        val seen = HashSet<String>()
        val out = mutableListOf<TermHit>()
        for ((t, pack) in terms) {
            val key = t.term.lowercase()
            if (key in seen) continue
            val form = t.forms.firstOrNull { occurs(it, text) } ?: continue
            seen += key
            out += TermHit(t, form, pack)
        }
        return out
    }

    private fun abbreviation(form: String) = form.length <= 5 && form.none { it.isLowerCase() } && form.any { it.isLetter() }

    internal fun occurs(form: String, text: String): Boolean {
        if (form.length < 2) return false
        val opts = if (abbreviation(form)) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(form) + "(?![\\p{L}\\p{N}])", opts).containsMatchIn(text)
    }

    /**
     * For the command model and the rules: synonyms of own words become the word itself („öffne das MHB“ with the own word
     * „Modulhandbuch“ = MHB → „öffne das Modulhandbuch“). Pack entries are left as they are (they are no app places).
     */
    fun canonical(text: String, own: List<Term>): String {
        var out = text
        for (t in own) for (s in t.synonyms) {
            if (s.length < 2 || !occurs(s, out)) continue
            val opts = if (abbreviation(s)) emptySet() else setOf(RegexOption.IGNORE_CASE)
            out = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(s) + "(?![\\p{L}\\p{N}])", opts).replace(out, Regex.escapeReplacement(t.term))
        }
        return out
    }

    /** Search words: the text plus the word and spellings of every term in it (Strg+K, file search). */
    fun expand(text: String, terms: List<Pair<Term, String?>>): String {
        val hits = find(text, terms)
        if (hits.isEmpty()) return text
        return text + " " + hits.joinToString(" ") { h -> h.term.forms.joinToString(" ") }
    }

    /**
     * A short context for a language model: at most [max] terms found in [text], one line each – own words without
     * source, pack entries with source and date. Null when none occurs.
     */
    fun context(text: String, terms: List<Pair<Term, String?>>, max: Int = 8): String? {
        val hits = find(text, terms).take(max)
        if (hits.isEmpty()) return null
        return "Begriffe aus den eigenen Wörtern und Wissenspaketen der Nutzerin bzw. des Nutzers (zur Einordnung; nicht ungefragt erklären):\n" +
            hits.joinToString("\n") { h ->
                val t = h.term
                "- ${t.term}" + (t.synonyms.takeIf { it.isNotEmpty() }?.let { " (auch: ${it.joinToString()})" } ?: "") +
                    (t.explanation.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") +
                    (if (t.source.isNotBlank()) " [Quelle: ${t.source}, Stand ${t.date}]" else "")
            }
    }

    fun context(c: AppContainer, text: String): String? = runCatching {
        val terms = all(c)
        context(text, terms) ?: meaning(text, terms)?.let { context(it.joinToString("\n") { t -> t.term }, terms, 3) }
    }.getOrNull()

    /**
     * Suche nach Bedeutung (AP-107): entries of the Wissenspakete whose meaning fits [text] although their word is not in
     * it („Wer zahlt meine Dienstreise?“ → Reisekostenstelle); at most three, only clearly fitting ones; null without the
     * model on this device.
     */
    fun meaning(text: String, terms: List<Pair<Term, String?>>): List<Term>? {
        if (text.length < 12 || !SemanticSearch.active) return null
        val packs = terms.filter { it.second != null && it.first.explanation.isNotBlank() }.map { it.first }.take(500)
        if (packs.isEmpty()) return null
        val cos = SemanticSearch.similarity(text.take(1_000), packs.map { "${it.term}: ${it.explanation}" }, limit = 200) ?: return null
        return packs.indices.filter { (cos[it] ?: 0.0) > 0.84 }.sortedByDescending { cos[it] }.take(3).map { packs[it] }.ifEmpty { null }
    }

    /** For word searches (AP-78): the other one-word spellings of a word, lower case („mhb“ → „modulhandbuch“). */
    fun forms(terms: List<Pair<Term, String?>>): (String) -> List<String> {
        val map = HashMap<String, MutableSet<String>>()
        for ((t, _) in terms) {
            val words = t.forms.filter { ' ' !in it.trim() }.map { it.lowercase() }
            for (w in words) map.getOrPut(w) { linkedSetOf() } += words - w
        }
        return { w -> map[w.lowercase()]?.toList().orEmpty() }
    }

    fun forms(c: AppContainer): (String) -> List<String> = runCatching { forms(all(c)) }.getOrDefault { emptyList() }
}

/**
 * Wissenspakete zum Nachladen (AP-78): small databases like „Lehramt an der TU Dresden“, delivered like the models – a
 * file of this repository at a fixed commit, checked by SHA-256 –, installed into the data folder, updated when the
 * catalog has a newer version, deleted when not needed. Shared Lora-Pakete (AP-83) use the same format and appear here
 * too (without a download address).
 */
object KnowledgePacks {
    @Serializable
    data class Pack(
        val format: Int = 1,
        val id: String,
        @SerialName("titel") val title: String,
        @SerialName("beschreibung") val description: String = "",
        val version: Int = 1,
        @SerialName("stand") val date: String = "",
        @SerialName("eintraege") val entries: List<Term> = emptyList(),
        /** Shared Lora-Paket (AP-83): own commands, own words, templates and house rules of a colleague. */
        @SerialName("befehle") val commands: List<LoraHistory.Entry> = emptyList(),
        @SerialName("vorlagen") val templates: List<Template> = emptyList(),
        @SerialName("hausstil") val houseRules: List<HouseRule> = emptyList(),
        /** „geteilt“ for packs imported from a file (AP-83); empty for downloaded ones. */
        @SerialName("herkunft") val origin: String = "",
    )

    /** A pack the app can download. */
    data class Offer(val id: String, val title: String, val description: String, val version: Int, val url: String, val size: Long, val sha256: String)

    /** Commit of tools/wissen/ the packs are taken from (see tools/wissen/README.md). */
    const val COMMIT = "da9c3179fcb2277a30f2f03e63e7ae4179125b2f"

    val CATALOG = listOf(
        Offer(
            "lehramt-tud", "Lehramt an der TU Dresden",
            "Abkürzungen, Einrichtungen, Studiengänge, Praktika, Prüfungen und Ansprechstellen rund um das Lehramtsstudium – jede Angabe mit Quelle und Stand.",
            1, "https://raw.githubusercontent.com/ADEU-TUD/Lora/$COMMIT/tools/wissen/lehramt-tud.json", 16_548L,
            "e1649488b26fab903e936f7011088dab720a47fc458bb57bb63ac4b7af5d6b8b",
        ),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun dir(c: AppContainer) = File(c.appContext.filesDir, "wissen").apply { mkdirs() }
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version
    @Volatile private var cache: List<Pack>? = null

    /** The installed packs (downloaded and imported). */
    @Synchronized
    fun installed(c: AppContainer): List<Pack> = cache ?: dir(c).listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f -> runCatching { parse(f.readText()) }.getOrNull() }.sortedBy { it.title.lowercase() }.also { cache = it }

    /** Reads a pack and drops entries without source or date (nothing unsourced gets in). */
    fun parse(text: String): Pack {
        val p = json.decodeFromString<Pack>(text)
        require(p.id.matches(Regex("[a-z0-9-]{2,60}"))) { tr("Das Paket hat keinen gültigen Namen.") }
        val shared = p.origin == "geteilt"
        return p.copy(entries = p.entries.filter { e -> e.term.isNotBlank() && (shared || sourced(e)) })
    }

    /** A pack entry needs an https source and a date. */
    fun sourced(e: Term): Boolean = e.source.startsWith("https://") && Regex("\\d{4}-\\d{2}-\\d{2}").matches(e.date)

    fun offer(id: String) = CATALOG.firstOrNull { it.id == id }

    /** Whether a newer version than the installed one is offered. */
    fun updatable(c: AppContainer, id: String): Boolean {
        val have = installed(c).firstOrNull { it.id == id } ?: return false
        return have.origin.isEmpty() && (offer(id)?.version ?: 0) > have.version
    }

    /** Downloads [offer] (blocking, on an IO thread), checks it and installs it. */
    fun download(c: AppContainer, offer: Offer, progress: (Double) -> Unit = {}) {
        val part = File(dir(c), offer.id + ".part")
        FileDownload.fetch(offer.url, part, offer.size, { false }, progress)
        if (part.length() != offer.size || FileDownload.sha256(part) != offer.sha256) {
            part.delete()
            error(tr("Das Paket ist beschädigt angekommen (Prüfsumme stimmt nicht) – bitte später erneut versuchen."))
        }
        install(c, part.readText())
        part.delete()
    }

    /** Installs a pack from its text (download or import); replaces one with the same id. */
    fun install(c: AppContainer, text: String): Pack {
        val p = parse(text)
        File(dir(c), p.id + ".json").writeText(json.encodeToString(p))
        changed()
        return p
    }

    fun delete(c: AppContainer, id: String) {
        File(dir(c), "$id.json").delete()
        changed()
    }

    private fun changed() {
        cache = null
        _version.value++
    }
}
