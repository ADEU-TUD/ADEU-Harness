package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.ui.tr
import java.util.Locale

/**
 * Rohschnitt aus dem Transkript (AP-25): a clip becomes the parts in which something is said – long pauses shrink,
 * filler words („äh“, „ähm“) go where the transcript has measured word times. „Mit Lora kürzen“ lets the language
 * model choose the passages for a wish („nur die Erklärung zu X, höchstens 5 Minuten“). Subtitles come from the same
 * transcript.
 */
object RoughCut {
    val FILLERS = setOf("äh", "ähm", "öhm", "öh", "ähh", "äähm", "hm", "hmm", "ehm", "em", "mhm", "uh", "uhm", "um", "er", "erm")

    private fun norm(w: String) = w.lowercase(Locale.GERMAN).trim().trim(',', '.', '!', '?', '…', ';', ':', '-', '–', '"', '„', '“')

    fun isFiller(w: String) = norm(w) in FILLERS

    /**
     * Parts of the source between [from] and [to] (seconds of the source file) to keep: speech with pauses longer than
     * [maxPause] taken out (keeping [pad] around the speech), filler words left out if [fillers] and the transcript
     * has measured word times.
     */
    fun keep(t: Transcript, from: Double, to: Double, maxPause: Double = 0.8, pad: Double = 0.15, fillers: Boolean = true): List<Pair<Double, Double>> {
        val units = mutableListOf<Pair<Double, Double>>()
        // Filler words that are cut out: a cut there even when the pause around them is short.
        val dropped = mutableListOf<Pair<Double, Double>>()
        for (s in t.segments) {
            if (s.end <= from || s.start >= to) continue
            val measured = s.words.isNotEmpty() && s.words.all { it.measured }
            if (measured) {
                s.words.forEach { w -> if (fillers && isFiller(w.text)) dropped += w.start to w.end else units += w.start to w.end }
            } else {
                val words = s.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isEmpty()) continue
                if (fillers && words.all(::isFiller)) dropped += s.start to s.end else units += s.start to s.end
            }
        }
        val sorted = units.map { (a, b) -> a.coerceIn(from, to) to b.coerceIn(from, to) }.filter { it.second > it.first }.sortedBy { it.first }
        fun fillerBetween(a: Double, b: Double) = dropped.any { it.first >= a - 0.01 && it.second <= b + 0.01 }
        val merged = mutableListOf<Pair<Double, Double>>()
        for (u in sorted) {
            val last = merged.lastOrNull()
            if (last != null && u.first - last.second <= maxPause && !fillerBetween(last.second, u.first)) merged[merged.lastIndex] = last.first to maxOf(last.second, u.second)
            else merged += u
        }
        // A little room before and after every part – not into a cut-out filler and not over the neighbours.
        return merged.mapIndexed { i, (a, b) ->
            val prevEnd = if (i > 0) merged[i - 1].second else from
            val nextStart = if (i + 1 < merged.size) merged[i + 1].first else to
            val fillerBefore = dropped.filter { it.second <= a + 0.01 }.maxOfOrNull { it.second } ?: from
            val fillerAfter = dropped.filter { it.first >= b - 0.01 }.minOfOrNull { it.first } ?: to
            maxOf(from, a - pad, (a + prevEnd) / 2, minOf(fillerBefore, a)) to minOf(to, b + pad, (b + nextStart) / 2, maxOf(fillerAfter, b))
        }.filter { it.second - it.first >= 0.2 }
    }

    /** Clip [index] of [p] replaced by the [parts] of its source (each a clip with a hard cut). */
    fun apply(p: VideoProject, index: Int, parts: List<Pair<Double, Double>>): VideoProject {
        val c = p.clips.getOrNull(index) ?: return p
        if (parts.isEmpty()) return p
        val pieces = parts.mapIndexed { k, (a, b) -> c.copy(id = newVideoId(), inSec = a, outSec = b, transition = if (k == 0) c.transition else "none") }
        return p.copy(clips = p.clips.toMutableList().apply { removeAt(index); addAll(index, pieces) })
    }

    /** Seconds saved by [parts] compared with [from]..[to]. */
    fun saved(from: Double, to: Double, parts: List<Pair<Double, Double>>) = (to - from) - parts.sumOf { it.second - it.first }

    /**
     * „Mit Lora kürzen“: the model gets the numbered passages of the clip with their times and the wish, and answers with
     * the numbers to keep. Returns the parts in source seconds (passages next to each other joined).
     */
    suspend fun withLora(c: AppContainer, t: Transcript, from: Double, to: Double, wish: String): List<Pair<Double, Double>> {
        val segs = t.segments.filter { it.end > from && it.start < to && it.text.isNotBlank() }
        require(segs.isNotEmpty()) { tr("Im gewählten Clip ist nichts gesprochen.") }
        val list = segs.mapIndexed { i, s -> "${i + 1} [${clock(s.start)}–${clock(s.end)}] ${s.text.trim()}" }.joinToString("\n")
        val prompt = "Du schneidest ein Lehrvideo. Unten stehen die gesprochenen Abschnitte des Videos, nummeriert, mit Zeit. " +
            "Wähle die Abschnitte, die bleiben sollen, nach diesem Wunsch: „${wish.trim()}“. Behalte ganze Gedanken, schneide nicht mitten in eine " +
            "Erklärung, die Reihenfolge bleibt. Antworte NUR mit den Nummern, durch Kommas getrennt (Bereiche wie 3-7 erlaubt), ohne weiteren Text.\n\n$list"
        val model = c.settings.current.defaultModel
        val result = c.llm.streamChat(model = model, messages = listOf(ChatMessage(role = "user", content = prompt)), tools = null, thinking = ThinkingLevel.OFF) { }
        val keep = numbers(result.content, segs.size)
        require(keep.isNotEmpty()) { tr("Lora hat keine Abschnitte ausgewählt.") }
        val parts = keep.sorted().map { i -> segs[i - 1].start.coerceAtLeast(from) to segs[i - 1].end.coerceAtMost(to) }
        val merged = mutableListOf<Pair<Double, Double>>()
        for (pp in parts) {
            val last = merged.lastOrNull()
            if (last != null && pp.first - last.second <= 1.0) merged[merged.lastIndex] = last.first to pp.second else merged += pp
        }
        return merged.map { (a, b) -> (a - 0.15).coerceAtLeast(from) to (b + 0.2).coerceAtMost(to) }
    }

    /** "1, 3-5, 8" → {1, 3, 4, 5, 8} within 1..[max]. */
    internal fun numbers(text: String, max: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        Regex("(\\d+)\\s*[-–]\\s*(\\d+)|(\\d+)").findAll(text).forEach { m ->
            if (m.groupValues[3].isNotEmpty()) out += m.groupValues[3].toInt()
            else (m.groupValues[1].toInt()..m.groupValues[2].toInt()).forEach { out += it }
        }
        return out.filter { it in 1..max }.toSet()
    }

    private fun clock(sec: Double): String {
        val s = sec.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    /**
     * Subtitles of [p] from the transcripts of its source files: the cues within each clip, moved to where the clip
     * sits on the timeline.
     */
    fun subtitles(p: VideoProject, transcripts: Map<String, Transcript>): List<SubtitleCue> {
        val starts = p.starts()
        val out = mutableListOf<SubtitleCue>()
        p.clips.forEachIndexed { i, c ->
            val t = transcripts[c.path] ?: return@forEachIndexed
            TranscriptExport.cues(t).forEach { cue ->
                // A frozen or reversed clip (AP-54) has no words in their order; with a speed they come faster or slower.
                if (c.fx.freeze || c.fx.reverse) return@forEachIndexed
                val a = maxOf(cue.start, c.inSec)
                val b = minOf(cue.end, c.outSec)
                if (b - a >= 0.3) out += SubtitleCue(starts[i] + (a - c.inSec) / c.speed, starts[i] + (b - c.inSec) / c.speed, cue.lines.joinToString("\n"))
            }
        }
        return out.sortedBy { it.startSec }.let { list ->
            list.mapIndexed { k, s -> if (k + 1 < list.size && s.endSec > list[k + 1].startSec) s.copy(endSec = list[k + 1].startSec) else s }
        }
    }
}
