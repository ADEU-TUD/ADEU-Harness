package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.research.BibEntry
import io.github.thepytales.llmhub.research.Bibliography
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.ui.inlineSegments
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Words, characters and reading time of a text (the statistics bar of the editor). */
data class DocStats(val words: Int, val chars: Int, val charsNoSpaces: Int) {
    /** Reading time in minutes at about 200 words per minute (at least one). */
    val minutes: Int get() = ((words + 199) / 200).coerceAtLeast(1)

    fun label(): String = "$words Wörter · $chars Zeichen ($charsNoSpaces ohne Leerzeichen) · ca. $minutes Min. Lesezeit"

    companion object {
        /** Counts what a reader sees: without Markdown marks, comment lines and footnote marks. */
        fun of(markdown: String): DocStats {
            val text = plain(markdown)
            val words = text.split(Regex("\\s+")).count { w -> w.any(Char::isLetterOrDigit) }
            return DocStats(words, text.length, text.count { !it.isWhitespace() })
        }

        fun plain(markdown: String): String = markdown.replace("​", "").lines()
            .filterNot { DocMarkup.isComment(it) || DocMarkup.isPageBreak(it) || it.trim().matches(Regex("^\\|?\\s*:?-{2,}.*")) || it.trimStart().startsWith("```") }
            .joinToString("\n") { line ->
                val img = DocMarkup.image(line)
                if (img != null) img.alt else inlineSegments(DocBlocks.plainOf(line).replace(DocMarkup.NOTE_REF, "").replace(Regex("^\\[\\^[^\\]]+]:\\s*"), "")).joinToString("") { it.text }.replace("|", " ")
            }.trim()
    }
}

/** The spelling and grammar check of the model: its suggestions and how they are applied. */
object SpellCheck {
    /** One suggestion: [original] as it stands in the text, the [korrektur] and the reason ([grund]). */
    data class Correction(val original: String, val korrektur: String, val grund: String)

    const val INSTRUCTION = "Prüfe den Text auf Fehler in Rechtschreibung, Grammatik und Zeichensetzung nach der amtlichen " +
        "deutschen Rechtschreibung (bei englischem Text nach englischer). Antworte ausschließlich mit einem JSON-Array, ohne " +
        "Erklärung davor oder danach: [{\"original\": \"…\", \"korrektur\": \"…\", \"grund\": \"…\"}]. „original“ ist die " +
        "fehlerhafte Stelle wörtlich und zeichengenau aus dem Text (ein Wort oder eine kurze Wortgruppe, so eindeutig wie nötig), " +
        "„korrektur“ ersetzt genau diese Stelle, „grund“ erklärt kurz auf Deutsch. Markdown-Zeichen (**, #, -, |) bleiben " +
        "unverändert. Ändere keinen Stil und keinen Inhalt. Ohne Fehler: []."

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads the model's answer (also with code fences or text around the array); unusable entries are dropped. */
    fun parse(answer: String): List<Correction> {
        val start = answer.indexOf('[')
        val end = answer.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val array = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(vararg keys: String) = keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val original = s("original", "falsch")
            val fixed = s("korrektur", "correction", "richtig")
            if (original.isBlank() || original == fixed) null else Correction(original, fixed, s("grund", "reason", "erklaerung"))
        }.distinct()
    }

    /** Only suggestions whose passage really is in [text]. */
    fun applicable(text: String, list: List<Correction>): List<Correction> = list.filter { it.original in text }

    /** [text] with [c] applied at its first place. */
    fun apply(text: String, c: Correction): String {
        val at = text.indexOf(c.original)
        return if (at < 0) text else text.substring(0, at) + c.korrektur + text.substring(at + c.original.length)
    }

    fun applyAll(text: String, list: List<Correction>): String = list.fold(text) { t, c -> apply(t, c) }
}

/**
 * What the model changed in a document, block by block, so each change can be accepted or rejected: a block that
 * replaced or was inserted after the previous version, and blocks it removed.
 */
object AiReview {
    /** A changed block [index] of the new text and what stood there before ([before] null = inserted). */
    data class Change(val index: Int, val before: String?)

    /** A block the model removed; it belonged before block [at] of the new text. */
    data class Removed(val at: Int, val text: String)

    data class Result(val changes: List<Change>, val removed: List<Removed>)

    fun compare(old: String, new: String): Result {
        val a = DocBlocks.split(old)
        val b = DocBlocks.split(new)
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val changes = mutableListOf<Change>()
        val removed = mutableListOf<Removed>()
        var i = 0
        var j = 0
        // Runs of removed and added blocks between unchanged ones: pairwise replacements, the rest inserted/removed.
        val gone = mutableListOf<String>()
        val added = mutableListOf<Int>()
        fun flush() {
            added.forEachIndexed { k, idx -> changes += Change(idx, gone.getOrNull(k)) }
            gone.drop(added.size).forEach { removed += Removed(j, it) }
            gone.clear()
            added.clear()
        }
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { flush(); i++; j++ }
                j < b.size && (i >= a.size || dp[i][j + 1] >= dp[i + 1][j]) -> { added += j; j++ }
                else -> { gone += a[i]; i++ }
            }
        }
        flush()
        return Result(changes, removed)
    }

    /** [blocks] with the change at [index] taken back. */
    fun reject(blocks: List<String>, change: Change): List<String> = blocks.toMutableList().apply {
        if (change.index !in indices) return@apply
        if (change.before == null) removeAt(change.index) else set(change.index, change.before)
    }

    fun restore(blocks: List<String>, r: Removed): List<String> = blocks.toMutableList().apply { add(r.at.coerceIn(0, size), r.text) }
}

/**
 * Änderungen nachverfolgen (AP-16): the changes since the document's base ([LiveDoc.trackBase]) are suggestions. Accepting
 * one moves it into the base, rejecting takes it back in the text – every other suggestion stays.
 */
object Tracking {
    private const val EMPTY = "\u200B"

    fun review(base: String, current: String): AiReview.Result = AiReview.compare(base.replace(EMPTY, ""), current.replace(EMPTY, ""))

    /** The base after accepting [accepted] or letting the removal [dropped] go. */
    fun baseAfter(base: String, current: String, accepted: AiReview.Change? = null, dropped: AiReview.Removed? = null): String {
        val r = review(base, current)
        val cur = DocBlocks.split(current.replace(EMPTY, ""))
        val out = mutableListOf<String>()
        for (i in 0..cur.size) {
            r.removed.filter { it.at == i && it != dropped }.forEach { out += it.text }
            if (i == cur.size) break
            val ch = r.changes.firstOrNull { it.index == i }
            when {
                ch == null || ch == accepted -> out += cur[i]
                ch.before != null -> out += ch.before
            }
        }
        return DocBlocks.join(out)
    }

    /** All suggestions taken back: the text of the base. */
    fun rejectAll(base: String): String = base

    /** Whether [doc] has open suggestions in any tab. */
    fun pending(doc: LiveDoc): Int = doc.trackBase?.let { bases ->
        doc.tabs.sumOf { t -> bases[t.id]?.let { b -> review(b, t.markdown).let { it.changes.size + it.removed.size } } ?: 0 }
    } ?: 0
}

/**
 * Citations from the literature collection in a document: the in-text citation in the chosen style and an automatic
 * "## Literatur" at the end. The cited works are remembered in a comment line below that heading
 * (`<!-- literatur: APA7_DE; id1, id2 -->`), which the exports leave out.
 */
object DocCitations {
    private val MARKER = Regex("^\\s*<!--\\s*literatur:\\s*([A-Z0-9_]*)\\s*;?\\s*(.*?)\\s*-->\\s*$", RegexOption.MULTILINE)
    const val HEADING = "## Literatur"

    data class Cited(val style: CitationStyle?, val ids: List<String>)

    fun cited(markdown: String): Cited {
        val m = MARKER.find(markdown) ?: return Cited(null, emptyList())
        return Cited(CitationStyle.parse(m.groupValues[1]), m.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    private fun names(r: Reference, style: CitationStyle): String {
        val fam = r.creators.map { it.family }
        val german = style.german
        val and = when (style) {
            CitationStyle.APA7_DE, CitationStyle.APA7_EN -> " & "
            CitationStyle.ZFPAED -> "/"
            CitationStyle.HARVARD, CitationStyle.MLA9 -> " and "
            else -> if (german) " und " else " and "
        }
        val etAl = if (style == CitationStyle.ZFPAED) " u. a." else " et al."
        return when {
            fam.isEmpty() -> r.title.ifBlank { r.raw }.split(Regex("\\s+")).take(3).joinToString(" ")
            fam.size == 1 -> fam[0]
            fam.size == 2 -> fam[0] + and + fam[1]
            style == CitationStyle.HARVARD && fam.size == 3 -> fam[0] + ", " + fam[1] + " and " + fam[2]
            else -> fam[0] + etAl
        }
    }

    /** "(Müller & Schmidt, 2020)", "(Müller 2020)", "[3]" … – [number] is the position in the list (IEEE). */
    fun inText(r: Reference, style: CitationStyle, number: Int = 1): String {
        val year = r.year.ifBlank { if (style.german) "o. J." else "n.d." }
        return when (style) {
            CitationStyle.IEEE -> "[$number]"
            CitationStyle.MLA9 -> "(${names(r, style)})"
            CitationStyle.APA7_DE, CitationStyle.APA7_EN, CitationStyle.HARVARD -> "(${names(r, style)}, $year)"
            else -> "(${names(r, style)} $year)"
        }
    }

    /**
     * [markdown] with the reference list for [refs] (in [style], sorted as the style wants) at the end, replacing the
     * list made before.
     */
    fun withBibliography(markdown: String, refs: List<Reference>, style: CitationStyle): String {
        val body = removeBibliography(markdown).trimEnd()
        if (refs.isEmpty()) return "$body\n"
        val result = Bibliography.build(refs.map { BibEntry(it) }, style)
        val ids = result.entries.joinToString(", ") { it.ref.id }
        return "$body\n\n$HEADING\n\n<!-- literatur: ${style.name}; $ids -->\n\n" + result.lines.joinToString("\n\n") + "\n"
    }

    /** Position of [ref] in the sorted list (for numbered styles). */
    fun number(refs: List<Reference>, ref: Reference, style: CitationStyle): Int =
        Bibliography.build(refs.map { BibEntry(it) }, style).entries.indexOfFirst { it.ref.id == ref.id } + 1

    /** Removes the generated list: from its heading (the one above the marker) to the next heading of that level. */
    fun removeBibliography(markdown: String): String {
        val lines = markdown.lines()
        val marker = lines.indexOfFirst { MARKER.matches(it) }
        if (marker < 0) return markdown
        val head = (marker downTo 0).firstOrNull { lines[it].startsWith("#") } ?: marker
        val level = lines[head].takeWhile { it == '#' }.length.coerceAtLeast(1)
        val end = (marker + 1 until lines.size).firstOrNull { l ->
            val t = lines[l]
            t.startsWith("#") && t.takeWhile { it == '#' }.length <= level || DocMarkup.orientation(t) != null || SETTING.matches(t)
        } ?: lines.size
        // Footnotes written below the list stay.
        val notes = lines.subList(head, end).filter { NOTE.containsMatchIn(it) }.flatMap { listOf("", it) }
        return (lines.take(head) + notes + lines.drop(end)).joinToString("\n")
    }

    private val SETTING = Regex("^\\s*<!--\\s*(seitenzahlen|datum|kopfzeile)\\s*:.*-->\\s*$", RegexOption.IGNORE_CASE)
    private val NOTE = Regex("^\\s{0,3}\\[\\^[^\\]\\s]+]:")
}
