package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.ui.tr
import java.util.Locale
import kotlin.math.abs

/**
 * Textbasierter Schnitt (AP-55): the transcript of the clips on V1 as text beside the timeline – every word knows where
 * it is on the timeline (a click jumps there), deleting words cuts them out of the video, moving a paragraph moves its
 * clip, filler words and long pauses are marked for removal, and Lora proposes passages to cut for a wish („Kürze auf
 * 10 Minuten, behalte die Beispiele“) which are accepted or rejected one by one. Pure functions on [VideoProject]: the
 * editor (through its history, so Strg+Z works), Lora and the tests use the same ones.
 */
object TextCut {
    /** Room kept around the speech next to a cut (seconds of the source), as in the rough cut. */
    const val PAD = 0.15

    /** Shortest piece of a clip that is left standing; less is cut away with the rest. */
    private const val MIN = 0.2

    enum class Kind { WORD, PAUSE }

    /**
     * A word (or a long pause) of the transcript in clip [clipId] of V1: [srcStart]..[srcEnd] in its source file,
     * [start]..[end] on the timeline. [paragraph] is the index into [Doc.paragraphs].
     */
    data class Token(
        val clipId: String,
        val clip: Int,
        val paragraph: Int,
        val srcStart: Double,
        val srcEnd: Double,
        val start: Double,
        val end: Double,
        val text: String,
        val kind: Kind = Kind.WORD,
        val filler: Boolean = false,
        val speaker: Int? = null,
    )

    /** A paragraph: one segment of the transcript inside one clip, tokens [first]..[last]. */
    data class Paragraph(val clipId: String, val clip: Int, val first: Int, val last: Int, val speaker: String?, val start: Double, val end: Double)

    /** The text of the video: [tokens] in timeline order, grouped into [paragraphs]. [missing]: clips without a transcript. */
    data class Doc(val tokens: List<Token>, val paragraphs: List<Paragraph>, val missing: List<Int> = emptyList()) {
        val words: Int get() = tokens.count { it.kind == Kind.WORD }

        /** Index of the token being spoken at timeline time [t] (or the last one before it), -1 if none. */
        fun at(t: Double): Int {
            if (tokens.isEmpty()) return -1
            var lo = 0
            var hi = tokens.lastIndex
            var best = -1
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                if (tokens[mid].start <= t + 1e-6) { best = mid; lo = mid + 1 } else hi = mid - 1
            }
            return best
        }

        fun fillers(): List<Int> = tokens.indices.filter { tokens[it].filler }
        fun pauses(): List<Int> = tokens.indices.filter { tokens[it].kind == Kind.PAUSE }

        /** The tokens [from]..[to] as text (pauses left out). */
        fun text(from: Int, to: Int): String = (from..to).mapNotNull { tokens.getOrNull(it)?.takeIf { t -> t.kind == Kind.WORD }?.text }.joinToString(" ")
    }

    /**
     * The text of [p]: for every clip on V1 with a transcript (a frozen or reversed clip has no words in their order
     * and is left out) the words between its In and Out, placed on the timeline with the clip's speed. Words without
     * measured times are spread over their segment by length. Pauses longer than [maxPause] between two words become
     * tokens of their own.
     */
    fun build(p: VideoProject, transcripts: Map<String, Transcript>, maxPause: Double = 0.8): Doc {
        val starts = p.starts()
        val tokens = mutableListOf<Token>()
        val paragraphs = mutableListOf<Paragraph>()
        val missing = mutableListOf<Int>()
        p.clips.forEachIndexed { i, c ->
            val t = transcripts[c.path]
            if (t == null || c.fx.freeze || c.fx.reverse) { missing += i; return@forEachIndexed }
            fun tl(src: Double) = starts[i] + (src - c.inSec) / c.speed
            var lastEnd: Double? = null
            for (s in t.segments) {
                if (s.end <= c.inSec || s.start >= c.outSec || s.text.isBlank()) continue
                val words = words(s.text, s.start, s.end, s.words.map { Triple(it.start, it.end, it.text) })
                    .filter { (a, b, _) -> (a + b) / 2 >= c.inSec - 1e-6 && (a + b) / 2 <= c.outSec + 1e-6 }
                if (words.isEmpty()) continue
                // A pause before the first word belongs to the end of the paragraph before (a paragraph starts with a word).
                val before = lastEnd
                val a0 = words.first().first.coerceIn(c.inSec, c.outSec)
                if (before != null && a0 - before > maxPause && paragraphs.isNotEmpty() && paragraphs.last().clipId == c.id) {
                    val last = paragraphs.last()
                    tokens += Token(c.id, i, paragraphs.lastIndex, before, a0, tl(before), tl(a0), pauseLabel((a0 - before) / c.speed), Kind.PAUSE, speaker = tokens.last().speaker)
                    paragraphs[paragraphs.lastIndex] = last.copy(last = tokens.lastIndex, end = tl(a0))
                    lastEnd = a0
                }
                val first = tokens.size
                for ((a, b, w) in words) {
                    val sa = a.coerceIn(c.inSec, c.outSec)
                    val sb = b.coerceIn(sa, c.outSec)
                    val prev = lastEnd
                    if (prev != null && sa - prev > maxPause) {
                        tokens += Token(c.id, i, paragraphs.size, prev, sa, tl(prev), tl(sa), pauseLabel((sa - prev) / c.speed), Kind.PAUSE, speaker = s.speaker)
                    }
                    tokens += Token(c.id, i, paragraphs.size, sa, sb, tl(sa), tl(sb), w, Kind.WORD, RoughCut.isFiller(w, t.language), s.speaker)
                    lastEnd = sb
                }
                paragraphs += Paragraph(c.id, i, first, tokens.lastIndex, s.speaker?.let { t.speakerName(it) }?.takeIf { t.diarized }, tokens[first].start, tokens.last().end)
                val pi = paragraphs.lastIndex
                for (k in first..tokens.lastIndex) if (tokens[k].paragraph != pi) tokens[k] = tokens[k].copy(paragraph = pi)
            }
        }
        return Doc(tokens, paragraphs, missing)
    }

    /** Words of a segment with times: the measured ones, else the text spread over [start]..[end] by length. */
    internal fun words(text: String, start: Double, end: Double, measured: List<Triple<Double, Double, String>>): List<Triple<Double, Double, String>> {
        if (measured.isNotEmpty()) return measured.filter { it.third.isNotBlank() }.map { Triple(it.first, maxOf(it.first, it.second), it.third.trim()) }
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        val weights = parts.map { it.length + 1.0 }
        val total = weights.sum()
        val span = (end - start).coerceAtLeast(0.01)
        var t = start
        return parts.mapIndexed { k, w ->
            val d = span * weights[k] / total
            Triple(t, t + d * 0.92, w).also { t += d }
        }
    }

    private fun pauseLabel(sec: Double) = String.format(Locale.GERMANY, "⏸ %.1f s", sec)

    // ── Cutting ──

    /**
     * The pieces of the sources to cut for the tokens [from]..[to] (a selection in the text): per clip the source
     * range from just after the word before (keeping [PAD]) to just before the word after. A selection of pauses only
     * shortens them to [PAD] on both sides. Returns (clip id, start, end) in source seconds.
     */
    fun ranges(doc: Doc, from: Int, to: Int): List<Triple<String, Double, Double>> {
        if (doc.tokens.isEmpty()) return emptyList()
        val a = from.coerceIn(0, doc.tokens.lastIndex)
        val b = to.coerceIn(a, doc.tokens.lastIndex)
        val out = mutableListOf<Triple<String, Double, Double>>()
        (a..b).groupBy { doc.tokens[it].clipId }.forEach { (clipId, idx) ->
            val first = doc.tokens[idx.first()]
            val last = doc.tokens[idx.last()]
            val prev = doc.tokens.getOrNull(idx.first() - 1)?.takeIf { it.clipId == clipId }
            val next = doc.tokens.getOrNull(idx.last() + 1)?.takeIf { it.clipId == clipId }
            val onlyPauses = idx.all { doc.tokens[it].kind == Kind.PAUSE }
            // A pause at the edge of the selection keeps PAD next to the word beyond it; a pause just outside the
            // selection goes with it (the words around the cut then stand 2 × PAD apart).
            val start = when {
                onlyPauses || first.kind == Kind.PAUSE -> first.srcStart + PAD
                prev != null && prev.kind == Kind.WORD -> prev.srcEnd + minOf(first.srcStart - prev.srcEnd, 2 * PAD) / 2
                prev != null -> prev.srcStart + PAD
                else -> first.srcStart - PAD
            }
            val end = when {
                onlyPauses || last.kind == Kind.PAUSE -> last.srcEnd - PAD
                next != null && next.kind == Kind.WORD -> next.srcStart - minOf(next.srcStart - last.srcEnd, 2 * PAD) / 2
                next != null -> next.srcEnd - PAD
                else -> last.srcEnd + PAD
            }
            if (end - start > 0.04) out += Triple(clipId, start, end)
        }
        return out
    }

    /** Cuts the [ranges] (clip id, source start, source end) out of [p]; see [removeFromClip]. */
    fun cut(p: VideoProject, ranges: List<Triple<String, Double, Double>>): VideoProject {
        if (p.track("V1").locked) return p
        // Latest first, so the ranges before stay where they are; overlapping ones of a clip are joined.
        val merged = ranges.groupBy { it.first }.flatMap { (id, rs) ->
            val sorted = rs.map { it.second to it.third }.sortedBy { it.first }
            val joined = mutableListOf<Pair<Double, Double>>()
            for (r in sorted) {
                val last = joined.lastOrNull()
                if (last != null && r.first <= last.second + 1e-6) joined[joined.lastIndex] = last.first to maxOf(last.second, r.second) else joined += r
            }
            joined.map { Triple(id, it.first, it.second) }
        }
        val order = p.clips.withIndex().associate { it.value.id to it.index }
        var q = p
        for ((id, a, b) in merged.sortedWith(compareByDescending<Triple<String, Double, Double>> { order[it.first] ?: -1 }.thenByDescending { it.second })) {
            q = removeFromClip(q, id, a, b)
        }
        return q
    }

    /**
     * Cuts [a]..[b] (source seconds) out of V1 clip [id]: a piece in the middle splits the clip (the right part with a
     * new id and a hard cut, sound linked to the clip goes with both parts), a piece at an edge trims it, less than
     * [MIN] left on either side goes as well. The clips after it close up; with the magnet everything else after the cut
     * moves along. Frozen and reversed clips are left alone.
     */
    fun removeFromClip(p: VideoProject, id: String, a: Double, b: Double): VideoProject {
        val i = p.clips.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return p
        val c = p.clips[i]
        if (c.fx.freeze || c.fx.reverse || p.track("V1").locked) return p
        val sa = a.coerceIn(c.inSec, c.outSec)
        val sb = b.coerceIn(sa, c.outSec)
        if (sb - sa < 0.04) return p
        val start = p.starts()[i]
        val ta = start + (sa - c.inSec) / c.speed
        val tb = start + (sb - c.inSec) / c.speed
        val left = sa - c.inSec
        val right = c.outSec - sb
        if (left < MIN && right < MIN) return VideoEdit.delete(p, VideoEdit.Clip(id))
        val q = when {
            left < MIN -> p.copy(clips = p.clips.toMutableList().apply { set(i, c.copy(inSec = sb)) })
            right < MIN -> p.copy(clips = p.clips.toMutableList().apply { set(i, c.copy(outSec = sa)) })
            else -> {
                val r = c.copy(id = newVideoId(), inSec = sb, transition = "none")
                val withClips = p.copy(clips = p.clips.toMutableList().apply { set(i, c.copy(outSec = sa)); add(i + 1, r) })
                withClips.copy(audio = withClips.audio.flatMap { s -> if (s.linkedTo != c.id) listOf(s) else listOf(s, s.copy(id = newVideoId(), linkedTo = r.id)) })
            }
        }
        val removed = if (left < MIN) (sb - c.inSec) / c.speed else if (right < MIN) (c.outSec - sa) / c.speed else tb - ta
        val oldEnd = start + c.length
        return if (p.magnet) VideoEdit.rippleAfter(q, if (right < MIN) oldEnd else tb, -removed) else q.linked()
    }

    /** Removes the tokens [from]..[to] (the text selection) from the video. */
    fun delete(p: VideoProject, doc: Doc, from: Int, to: Int): VideoProject = cut(p, ranges(doc, from, to))

    /** Cuts all filler words out (each with the room around it as far as the neighbours allow). */
    fun removeFillers(p: VideoProject, doc: Doc): VideoProject = cut(p, doc.fillers().flatMap { ranges(doc, it, it) })

    /** Shortens all long pauses to [PAD] on both sides. */
    fun shortenPauses(p: VideoProject, doc: Doc): VideoProject = cut(p, doc.pauses().flatMap { ranges(doc, it, it) })

    /** Seconds of the timeline the [ranges] take (for „spart 1:20“). */
    fun seconds(p: VideoProject, ranges: List<Triple<String, Double, Double>>): Double =
        ranges.sumOf { (id, a, b) -> p.clips.firstOrNull { it.id == id }?.let { c -> (b.coerceIn(c.inSec, c.outSec) - a.coerceIn(c.inSec, c.outSec)) / c.speed } ?: 0.0 }

    // ── Paragraphs ──

    /**
     * Makes paragraph [k] a clip of its own (cuts at the middle of the pauses before and after it) and returns the
     * project and that clip's id; the timeline does not change.
     */
    fun isolate(p: VideoProject, doc: Doc, k: Int): Pair<VideoProject, String>? {
        val para = doc.paragraphs.getOrNull(k) ?: return null
        if (p.clips.none { it.id == para.clipId }) return null
        val next = doc.tokens.getOrNull(para.last + 1)?.takeIf { it.clipId == para.clipId }
        // The cut after the paragraph first, then the one before it (the left part keeps the id, the right gets a new one).
        val q = if (next != null) VideoEdit.split(p, VideoEdit.Clip(para.clipId), between(doc.tokens[para.last], next))?.first ?: p else p
        return cutBefore(q, doc, k)
    }

    /** Cuts V1 just before paragraph [k] (middle of the pause before it); returns the project and the id of the clip it now starts. */
    private fun cutBefore(p: VideoProject, doc: Doc, k: Int): Pair<VideoProject, String>? {
        val para = doc.paragraphs.getOrNull(k) ?: return null
        val prev = doc.tokens.getOrNull(para.first - 1)?.takeIf { it.clipId == para.clipId } ?: return p to para.clipId
        return VideoEdit.split(p, VideoEdit.Clip(para.clipId), between(prev, doc.tokens[para.first])) ?: (p to para.clipId)
    }

    /** Timeline time in the middle of the silence between [a] and [b] (a pause token is that silence). */
    private fun between(a: Token, b: Token): Double = when {
        a.kind == Kind.PAUSE -> (a.start + a.end) / 2
        b.kind == Kind.PAUSE -> (b.start + b.end) / 2
        else -> (a.end + b.start) / 2
    }

    /** Moves V1 clip [id] to position [to] (0 = first); linked sound goes with it, the rest of V1 closes up. */
    fun moveClip(p: VideoProject, id: String, to: Int): VideoProject {
        if (p.track("V1").locked) return p
        val i = p.clips.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return p
        val target = to.coerceIn(0, p.clips.lastIndex)
        if (target == i) return p
        val list = p.clips.toMutableList()
        val c = list.removeAt(i)
        list.add(target, c.copy(transition = "none"))
        // Whatever follows the moved clip, and what now follows the gap it left, begins with a hard cut.
        val after = list.getOrNull(target + 1)?.id
        val gap = list.getOrNull(if (i > target) i + 1 else i)?.id
        return p.copy(clips = list.map { if (it.id == after || it.id == gap) it.copy(transition = "none") else it }).linked()
    }

    /**
     * Moves paragraph [k] before paragraph [before] (or to the end with `paragraphs.size`): both places are cut into
     * clips of their own, then the clip is moved – „Absätze verschieben = Clips verschieben“.
     */
    fun moveParagraph(p: VideoProject, transcripts: Map<String, Transcript>, k: Int, before: Int): VideoProject {
        if (k == before || k + 1 == before) return p
        val doc = build(p, transcripts)
        val (q, id) = isolate(p, doc, k) ?: return p
        if (before >= doc.paragraphs.size) return moveClip(q, id, q.clips.lastIndex)
        val target = doc.tokens.getOrNull(doc.paragraphs.getOrNull(before)?.first ?: return p) ?: return p
        val path = p.clips[target.clip].path
        val doc2 = build(q, transcripts)
        val tk = doc2.paragraphs.indexOfFirst { para -> doc2.tokens[para.first].let { q.clips[it.clip].path == path && abs(it.srcStart - target.srcStart) < 1e-6 } }
        if (tk < 0) return p
        val (r, beforeId) = cutBefore(q, doc2, tk) ?: return p
        val from = r.clips.indexOfFirst { it.id == id }
        val to = r.clips.indexOfFirst { it.id == beforeId }
        if (from < 0 || to < 0) return p
        return moveClip(r, id, if (from < to) to - 1 else to)
    }

    // ── Lora as editing assistant ──

    /** A passage Lora proposes to cut: tokens [from]..[to] with a short reason. */
    data class Proposal(val id: String = newVideoId(), val from: Int, val to: Int, val reason: String = "")

    /**
     * Lora reads the paragraphs (numbered, with times and the length of the video) and answers with the numbers of
     * those to cut for [wish]; each run of numbers becomes a [Proposal]. Nothing is cut yet.
     */
    suspend fun propose(c: AppContainer, doc: Doc, duration: Double, wish: String): List<Proposal> {
        require(doc.paragraphs.isNotEmpty()) { tr("Im Video ist nichts gesprochen – oder den Clips fehlt ein Transkript.") }
        val prompt = prompt(doc, duration, wish)
        val model = c.settings.current.defaultModel
        val result = c.llm.streamChat(model = model, messages = listOf(ChatMessage(role = "user", content = prompt)), tools = null, thinking = ThinkingLevel.OFF) { }
        return parse(result.content, doc)
    }

    /** The request to the model (also shown in tests). */
    internal fun prompt(doc: Doc, duration: Double, wish: String): String {
        val list = doc.paragraphs.mapIndexed { k, para ->
            val who = para.speaker?.let { "$it: " }.orEmpty()
            "${k + 1} [${clock(para.start)}–${clock(para.end)}] $who${doc.text(para.first, para.last)}"
        }.joinToString("\n")
        return "Du bist Schnitt-Assistentin für ein Lehrvideo (Länge ${clock(duration)}). Unten stehen die gesprochenen Absätze, " +
            "nummeriert, mit Zeit auf der Zeitleiste. Wunsch: „${wish.trim()}“. Nenne die Absätze, die HERAUSGESCHNITTEN werden " +
            "sollen, damit der Wunsch erfüllt ist. Schneide keine halben Gedanken, die Reihenfolge bleibt. Antworte NUR mit Zeilen " +
            "der Form „Nummer oder Bereich: kurzer Grund“, z. B. „4-6: Organisatorisches“. Gibt es nichts zu schneiden, antworte „keine“.\n\n$list"
    }

    /** "4-6: Organisatorisches\n9: Wiederholung" → proposals over the tokens of those paragraphs (neighbours joined). */
    internal fun parse(text: String, doc: Doc): List<Proposal> {
        val n = doc.paragraphs.size
        val picked = sortedMapOf<Int, String>()
        Regex("""(\d+)\s*(?:[-–]\s*(\d+))?\s*[:.)]?\s*([^\n,;]*)""").findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toIntOrNull() ?: a
            val reason = m.groupValues[3].trim()
            for (k in minOf(a, b)..maxOf(a, b)) if (k in 1..n) picked.putIfAbsent(k, reason)
        }
        val out = mutableListOf<Proposal>()
        var runStart = -1
        var runEnd = -1
        var reason = ""
        fun flush() { if (runStart > 0) out += Proposal(from = doc.paragraphs[runStart - 1].first, to = doc.paragraphs[runEnd - 1].last, reason = reason) }
        for ((k, r) in picked) {
            if (runStart > 0 && k == runEnd + 1 && (r.isEmpty() || r == reason)) { runEnd = k; continue }
            flush(); runStart = k; runEnd = k; reason = r
        }
        flush()
        return out
    }

    /** Applies the accepted [proposals] in one go. */
    fun accept(p: VideoProject, doc: Doc, proposals: List<Proposal>): VideoProject = cut(p, proposals.flatMap { ranges(doc, it.from, it.to) })

    private fun clock(sec: Double): String {
        val s = sec.toInt().coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
