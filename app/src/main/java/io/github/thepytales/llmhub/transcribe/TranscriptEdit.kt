package io.github.thepytales.llmhub.transcribe

import de.lokal.whisper.Speech
import de.lokal.whisper.WordAlign

/** A word of a segment: its place in the text and its measured time (null if it has none). */
data class TextWord(val from: Int, val to: Int, val word: Word?)

/** A place in a segment's text with a time stamp (inline "(0:03)"). */
data class InlineMark(val offset: Int, val time: Double)

/** Speaking time and contributions of one speaker. */
data class SpeakerStats(
    val speaker: Int?,
    val name: String,
    val seconds: Double,
    val share: Double,
    val words: Int,
    val turns: Int,
    val longestTurn: Double,
) {
    val wordsPerMinute get() = if (seconds < 1) 0.0 else words / (seconds / 60)
    val averageTurn get() = if (turns == 0) 0.0 else seconds / turns
}

/** Hit of the search in a transcript. */
data class SearchHit(val segment: Int, val from: Int, val to: Int)

/**
 * Editing a transcript without losing the measured word times: words are re-aligned after a text change, splitting
 * uses the measured time of the first word after the cursor, and nothing is estimated – a word without measured time
 * stays without time (see de.lokal.whisper.WordAlign). Pure Kotlin (tested).
 */
object TranscriptEdit {
    private val WORD = Regex("\\S+")
    private val SENTENCE_END = Regex(".*[.!?…][\"“”»«)']*$")

    /** Time stamp modes of the view and the export. */
    enum class Stamps(val key: String, val label: String, val seconds: Int = 0) {
        OFF("off", "Aus"),
        PARAGRAPH("paragraph", "Je Absatz"),
        SENTENCE("sentence", "Je Satz"),
        EVERY_10("10", "Alle 10 s", 10),
        EVERY_30("30", "Alle 30 s", 30),
        EVERY_60("60", "Alle 60 s", 60),
        ;

        companion object {
            fun of(key: String) = entries.firstOrNull { it.key == key } ?: PARAGRAPH
        }
    }

    /** Words of [s] (split at white space) with their measured time, if the word times fit the text. */
    fun textWords(s: Segment): List<TextWord> {
        val ranges = WORD.findAll(s.text).map { it.range.first to it.range.last + 1 }.toList()
        val fits = s.words.size == ranges.size &&
            s.words.indices.all { i -> WordAlign.dist(s.words[i].text, s.text.substring(ranges[i].first, ranges[i].second)) <= 0.5 }
        return ranges.mapIndexed { i, (a, b) -> TextWord(a, b, if (fits) s.words[i] else null) }
    }

    private fun toSpeech(w: Word) = Speech.Word(w.start, w.end, w.text, w.quality)
    private fun fromSpeech(w: Speech.Word) = Word(w.start, w.end, w.text, w.q)

    /** Word times for a new text of [s]: unchanged words keep their measured time, new ones lie between (quality 'i'). */
    fun realign(s: Segment, newText: String): List<Word> {
        val ref = s.words.filter { it.measured }
        if (ref.isEmpty()) return emptyList()
        return WordAlign.align(newText, ref.map(::toSpeech), s.start, s.end).map(::fromSpeech)
    }

    /** Segment with a new text; the word times are kept where the words are the same. */
    fun withText(s: Segment, text: String): Segment {
        val t = text.trim()
        return if (t == s.text) s else s.copy(text = t, words = realign(s, t))
    }

    private fun renumber(segments: List<Segment>) = segments.mapIndexed { i, s -> s.copy(id = i) }

    /**
     * Splits segment [index] before the word at [offset]. The time comes from the measured start of that word; without
     * one, [at] (e.g. the playback position) is used. Returns null if there is no time or nothing to split.
     */
    fun split(t: Transcript, index: Int, offset: Int, at: Double? = null): Transcript? {
        val s = t.segments.getOrNull(index) ?: return null
        val tw = textWords(s)
        val k = tw.indexOfFirst { it.from >= offset }
        if (k <= 0) return null
        val time = tw[k].word?.takeIf { it.measured }?.start ?: at?.takeIf { it > s.start && it < s.end } ?: return null
        val first = tw.subList(0, k)
        val second = tw.subList(k, tw.size)
        val a = s.copy(
            text = s.text.substring(0, first.last().to).trim(),
            end = first.last().word?.takeIf { it.measured }?.end?.coerceAtMost(time) ?: time,
            words = first.mapNotNull { it.word }.takeIf { it.size == first.size }.orEmpty(),
        )
        val b = s.copy(
            text = s.text.substring(second.first().from).trim(),
            start = time,
            words = second.mapNotNull { it.word }.takeIf { it.size == second.size }.orEmpty(),
            comment = null,
        )
        return t.copy(segments = renumber(t.segments.take(index) + a + b + t.segments.drop(index + 1)))
    }

    /** Merges segment [index] with the next one; the word times of both are kept. */
    fun mergeWithNext(t: Transcript, index: Int): Transcript? {
        val a = t.segments.getOrNull(index) ?: return null
        val b = t.segments.getOrNull(index + 1) ?: return null
        return t.copy(segments = renumber(t.segments.take(index) + merge(a, b) + t.segments.drop(index + 2)))
    }

    private fun merge(a: Segment, b: Segment): Segment {
        val text = (a.text.trim() + " " + b.text.trim()).trim()
        val ref = (a.words + b.words).filter { it.measured }
        val words = if (ref.isEmpty()) emptyList() else WordAlign.align(text, ref.map(::toSpeech), a.start, b.end).map(::fromSpeech)
        val comment = listOfNotNull(a.comment, b.comment).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
        return a.copy(text = text, end = maxOf(a.end, b.end), words = words, comment = comment)
    }

    /** Moves the start of segment [index] (e.g. to the playback position), within its neighbours. */
    fun setStart(t: Transcript, index: Int, time: Double): Transcript? {
        val s = t.segments.getOrNull(index) ?: return null
        val min = t.segments.getOrNull(index - 1)?.start ?: 0.0
        if (time <= min || time >= s.end) return null
        return t.copy(segments = t.segments.mapIndexed { i, x -> if (i == index) x.copy(start = time) else x })
    }

    /**
     * New segmentation: segments with measured word times are split into sentences, then sentences of the same speaker
     * are joined up to [maxSeconds] (0 = one sentence per segment). Segments without measured times stay as they are.
     */
    fun resegment(t: Transcript, maxSeconds: Int): Transcript {
        val pieces = mutableListOf<Segment>()
        for (s in t.segments) {
            val tw = textWords(s)
            if (tw.isEmpty() || tw.any { it.word == null || !it.word.measured }) { pieces += s; continue }
            var from = 0
            var comment = s.comment
            for (k in tw.indices) {
                val end = k == tw.lastIndex || SENTENCE_END.matches(s.text.substring(tw[k].from, tw[k].to))
                if (!end) continue
                val part = tw.subList(from, k + 1)
                pieces += s.copy(
                    text = s.text.substring(part.first().from, part.last().to),
                    start = if (from == 0) s.start else part.first().word!!.start,
                    end = if (k == tw.lastIndex) s.end else part.last().word!!.end,
                    words = part.map { it.word!! },
                    comment = comment,
                )
                comment = null
                from = k + 1
            }
        }
        if (maxSeconds <= 0) return t.copy(segments = renumber(pieces))
        val out = mutableListOf<Segment>()
        for (p in pieces) {
            val last = out.lastOrNull()
            if (last != null && last.speaker == p.speaker && p.start - last.end <= 1.5 && p.end - last.start <= maxSeconds) out[out.lastIndex] = merge(last, p)
            else out += p
        }
        return t.copy(segments = renumber(out))
    }

    /**
     * Inline time stamps of the view and the export, per segment. Only measured times are used: segment starts
     * (voice activity detection) and measured word starts – without measured word times there are fewer stamps,
     * never estimated ones.
     */
    fun inlineMarks(t: Transcript, mode: Stamps): List<List<InlineMark>> {
        if (mode == Stamps.OFF || mode == Stamps.PARAGRAPH) return t.segments.map { emptyList() }
        var next = 0.0
        return t.segments.map { s ->
            val tw = textWords(s)
            val out = mutableListOf<InlineMark>()
            if (mode == Stamps.SENTENCE) {
                out += InlineMark(0, s.start)
                for (k in 1 until tw.size) {
                    val prev = s.text.substring(tw[k - 1].from, tw[k - 1].to)
                    val w = tw[k].word
                    if (SENTENCE_END.matches(prev) && w != null && w.measured) out += InlineMark(tw[k].from, w.start)
                }
            } else {
                if (s.start >= next) { out += InlineMark(0, s.start); next = (Math.floor(s.start / mode.seconds) + 1) * mode.seconds }
                for (x in tw.drop(1)) {
                    val w = x.word ?: continue
                    if (w.measured && w.start >= next) { out += InlineMark(x.from, w.start); next = (Math.floor(w.start / mode.seconds) + 1) * mode.seconds }
                }
            }
            out
        }
    }

    /** Speaking time, words and contributions per speaker (without speakers: one row for the whole recording). */
    fun stats(t: Transcript): List<SpeakerStats> {
        val turns = TranscriptExport.turns(t)
        val total = t.segments.sumOf { it.end - it.start }.coerceAtLeast(0.001)
        return t.segments.map { it.speaker }.distinct().sortedBy { it ?: -1 }.map { k ->
            val segs = t.segments.filter { it.speaker == k }
            val sec = segs.sumOf { it.end - it.start }
            val mine = turns.filter { it.speaker == k }
            SpeakerStats(
                speaker = k,
                name = if (k == null) "Alle" else t.speakerName(k),
                seconds = sec,
                share = sec / total,
                words = segs.sumOf { s -> WORD.findAll(s.text).count() },
                turns = mine.size,
                longestTurn = mine.maxOfOrNull { it.end - it.start } ?: 0.0,
            )
        }
    }

    /** Number of changes of speaker. */
    fun speakerChanges(t: Transcript): Int = TranscriptExport.turns(t).zipWithNext().count { (a, b) -> a.speaker != b.speaker }

    /** All places of [query] in the text (case-insensitive). */
    fun search(t: Transcript, query: String): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val re = Regex(Regex.escape(query.trim()), RegexOption.IGNORE_CASE)
        return t.segments.flatMapIndexed { i, s -> re.findAll(s.text).map { SearchHit(i, it.range.first, it.range.last + 1) }.toList() }
    }

    /** Adds a speaker by hand (also to a transcript without speaker detection, then all segments get speaker 1). */
    fun addSpeaker(t: Transcript, name: String): Transcript {
        val next = (t.speakers.keys.maxOrNull() ?: -1) + 1
        return if (!t.diarized) t.copy(
            speakers = mapOf(0 to "Sprecher 1", 1 to name.ifBlank { "Sprecher 2" }),
            segments = t.segments.map { it.copy(speaker = 0) },
        ) else t.copy(speakers = t.speakers + (next to name.ifBlank { "Sprecher ${next + 1}" }))
    }

    /** Merges speaker [from] into [into]. */
    fun mergeSpeaker(t: Transcript, from: Int, into: Int): Transcript =
        t.copy(speakers = t.speakers - from, segments = t.segments.map { if (it.speaker == from) it.copy(speaker = into) else it })

    /** Removes the speakers again (one person only). */
    fun removeSpeakers(t: Transcript): Transcript = t.copy(speakers = emptyMap(), segments = t.segments.map { it.copy(speaker = null) })
}
