package io.github.thepytales.llmhub.transcribe

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PiiKind
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Anonymized copies of transcripts for passing on (research data, theses): names, contact details, addresses
 * and the user's own list (Einstellungen › Datenschutz) become placeholders, consistently in the whole
 * transcript; speakers with real names become „Person A“, „Person B“. The original stays unchanged.
 */
object TranscriptAnon {
    data class Result(val transcript: Transcript, val mapping: List<Pair<String, String>>)

    /** Speaker labels that are already anonymous (I, B1, Interviewer, Sprecher 2 …). */
    private val NEUTRAL = Regex("^(I\\d*|B\\d*|IP\\d*|Interviewer(in)?|Befragte[rn]?\\s*\\d*|Sprecher(in)?\\s*\\d*|Person\\s+[A-Z]|L|S\\d*|Lehrkraft|Schüler(in)?\\s*\\d*|Moderation)$", RegexOption.IGNORE_CASE)

    fun anonymize(t: Transcript, ownTerms: List<String>, extra: List<String> = emptyList()): Result {
        val texts = t.segments.flatMap { listOfNotNull(it.text, it.comment) } + t.title
        val hits = texts.flatMap { PiiDetector.find(it, ownTerms + extra, automatic = true) }
        // Speakers with real names count as names, too.
        val speakerNames = t.speakers.values.filter { !NEUTRAL.matches(it.trim()) && it.isNotBlank() }
        val counters = mutableMapOf<PiiKind, Int>()
        val mapping = linkedMapOf<String, String>()
        speakerNames.forEachIndexed { i, name -> mapping[name] = "Person ${'A' + i}" }
        hits.sortedByDescending { it.value.length }.forEach { h ->
            if (h.value in mapping) return@forEach
            // Parts of a speaker's name (e.g. only the first name in the text) get that speaker's label.
            val speaker = speakerNames.firstOrNull { name -> name.split(Regex("\\s+")).any { part -> part.length > 2 && h.value.equals(part, true) } }
            mapping[h.value] = speaker?.let { mapping[it] } ?: "[${h.kind.label} ${counters.merge(h.kind, 1, Int::plus)}]"
        }
        speakerNames.forEach { name ->
            name.split(Regex("\\s+")).filter { it.length > 2 }.forEach { part -> mapping.putIfAbsent(part, mapping.getValue(name)) }
        }
        val pattern = if (mapping.isEmpty()) null else Regex(
            mapping.keys.sortedByDescending { it.length }.joinToString("|") { "(?<![\\p{L}\\d])" + Regex.escape(it) + "(?![\\p{L}\\d])" },
        )
        fun replace(s: String) = pattern?.replace(s) { m -> mapping[m.value] ?: mapping.entries.firstOrNull { it.key.equals(m.value, true) }?.value ?: m.value } ?: s
        val segments = t.segments.map { s ->
            val text = replace(s.text)
            val comment = s.comment?.let(::replace)
            // Unchanged words keep their measured times.
            if (text == s.text && comment == s.comment) s else s.copy(text = text, words = TranscriptEdit.realign(s, text), comment = comment)
        }
        val speakers = t.speakers.mapValues { (_, v) -> mapping[v] ?: v }
        val anon = t.copy(
            title = replace(t.title) + " (anonymisiert)", segments = segments, speakers = speakers,
            raw = JsonObject(t.raw - "original" - "coding"), summary = t.summary?.let(::replace),
            chapters = t.chapters.map { it.copy(title = replace(it.title)) },
        )
        return Result(anon, mapping.entries.map { it.key to it.value })
    }

    /** The mapping as table, to be kept apart from the anonymized transcript. */
    fun mappingTable(title: String, mapping: List<Pair<String, String>>): String = buildString {
        append("# Zuordnungsliste – $title\n\n")
        append("> [!WARNING]\n> Getrennt vom anonymisierten Transkript und geschützt aufbewahren; nach Abschluss der Auswertung löschen.\n\n")
        append("| Original | Ersetzt durch |\n| --- | --- |\n")
        mapping.forEach { (a, b) -> append("| ${a.replace("|", "/")} | $b |\n") }
    }
}

/** Translation of a transcript passage by passage: times, speakers and passages stay as they are (for subtitles). */
object TranscriptTranslate {
    val LANGUAGES = listOf("Englisch", "Deutsch", "Französisch", "Spanisch", "Italienisch", "Polnisch", "Tschechisch", "Ukrainisch", "Russisch", "Arabisch", "Türkisch", "Leichte Sprache")

    private val LINE = Regex("^\\s*(\\d+)\\s*[\\t|:.)-]\\s*(.*)$")

    /** Parses „<Nummer>\t<Übersetzung>“ lines; missing passages are null. */
    fun parse(answer: String, count: Int): List<String?> {
        val out = arrayOfNulls<String>(count)
        answer.lines().forEach { l ->
            val m = LINE.find(l) ?: return@forEach
            val i = m.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
            if (i in 0 until count && m.groupValues[2].isNotBlank()) out[i] = m.groupValues[2].trim()
        }
        return out.toList()
    }

    suspend fun translate(c: AppContainer, t: Transcript, language: String, onProgress: (Int, Int) -> Unit): Transcript {
        val model = c.settings.current.defaultModel
        val segs = t.segments
        val result = arrayOfNulls<String>(segs.size)
        val batch = 40
        var done = 0
        for (from in segs.indices step batch) {
            val part = segs.subList(from, minOf(segs.size, from + batch))
            val prompt = part.mapIndexed { i, s -> "${i + 1}\t${s.text.trim().replace('\n', ' ')}" }.joinToString("\n")
            val answer = c.llm.streamChat(
                model = model,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "Du übersetzt Abschnitte eines Transkripts ins $language. Jede Zeile beginnt mit einer Nummer und einem " +
                            "Tabulator. Antworte mit genau denselben Nummern, je Zeile „Nummer<Tab>Übersetzung“, ohne Zeilen zusammenzufassen " +
                            "oder aufzuteilen und ohne Kommentar. Füllwörter und Abbrüche sinngemäß übertragen, Namen nicht übersetzen.",
                    ),
                    ChatMessage(role = "user", content = prompt),
                ),
                tools = null,
                thinking = ThinkingLevel.OFF,
            ) { }.content
            parse(io.github.thepytales.llmhub.ui.splitThinking(answer).second, part.size).forEachIndexed { i, s -> result[from + i] = s }
            done += part.size
            onProgress(done, segs.size)
        }
        val missing = result.count { it == null }
        return t.copy(
            id = UUID.randomUUID().toString().replace("-", "").take(24),
            title = "${t.title} ($language)",
            segments = segs.mapIndexed { i, s -> s.copy(text = result[i] ?: s.text, words = emptyList()) },
            language = null,
            audio = null,
            raw = JsonObject(t.raw - "original" - "coding" - "word_timing" + ("translated_from" to JsonPrimitive(t.id))),
            notice = if (missing > 0) "$missing Abschnitte blieben unübersetzt (Originaltext)." else "Maschinell übersetzt – bitte prüfen.",
            status = TranscriptStatus.RAW,
        )
    }
}
