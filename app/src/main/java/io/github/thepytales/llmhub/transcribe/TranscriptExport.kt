package io.github.thepytales.llmhub.transcribe

import java.util.Locale
import kotlin.math.roundToLong

/** Export formats of a transcript. */
enum class TranscriptFormat(val label: String, val ext: String, val description: String) {
    DOCX("Word", "docx", "Mit Sprechern und Zeitmarken, zum Weiterbearbeiten."),
    PDF("PDF", "pdf", "Zum Lesen, Drucken und Weitergeben."),
    DRESING_PEHL("Transkript nach Dresing/Pehl", "docx", "Einfache Transkriptionsregeln: ein Absatz pro Beitrag, Kürzel (I, B1 …), Pausen (...) und Zeitmarke am Absatzende."),
    MAXQDA("MAXQDA / f4 (Text mit Zeitmarken)", "txt", "Zeitmarken #00:01:23-4# – MAXQDA verknüpft sie beim Import mit der Audiodatei."),
    SRT("Untertitel SRT", "srt", "Für Lehrvideos (OPAL, YouTube, Videoplayer): höchstens zwei Zeilen mit je 42 Zeichen."),
    VTT("Untertitel WebVTT", "vtt", "Untertitel für Webseiten und Videoplattformen, mit Sprechernamen."),
    TXT("Nur Text", "txt", "Reiner Text ohne Zeitmarken."),
    MARKDOWN("Markdown", "md", "Für Notizen und Wikis."),
}

/**
 * Turns a transcript into text formats. Pure Kotlin (tested); Word and PDF are made from [markdown] by the document
 * writer of the app.
 */
object TranscriptExport {
    /** Consecutive segments of the same speaker, merged into one contribution. */
    data class Turn(val speaker: Int?, val start: Double, val end: Double, val segments: List<Segment>)

    fun turns(t: Transcript): List<Turn> {
        val out = mutableListOf<Turn>()
        var cur = mutableListOf<Segment>()
        for (s in t.segments) {
            if (cur.isNotEmpty() && (cur.last().speaker != s.speaker || !t.diarized && s.start - cur.last().end > 4.0)) {
                out += Turn(cur.first().speaker, cur.first().start, cur.last().end, cur)
                cur = mutableListOf()
            }
            cur += s
        }
        if (cur.isNotEmpty()) out += Turn(cur.first().speaker, cur.first().start, cur.last().end, cur)
        return out
    }

    /** 1:23 or 1:02:03 */
    fun clock(sec: Double): String {
        val s = sec.coerceAtLeast(0.0).toLong()
        return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    /** f4/MAXQDA time mark: #00:01:23-4# (hours:minutes:seconds-tenths). */
    fun f4Mark(sec: Double): String {
        val tenths = (sec.coerceAtLeast(0.0) * 10).roundToLong()
        val s = tenths / 10
        return String.format(Locale.ROOT, "#%02d:%02d:%02d-%d#", s / 3600, (s % 3600) / 60, s % 60, tenths % 10)
    }

    private fun srtTime(sec: Double, sep: Char): String {
        val ms = (sec.coerceAtLeast(0.0) * 1000).roundToLong()
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", ms / 3_600_000, (ms % 3_600_000) / 60_000, (ms % 60_000) / 1000, sep, ms % 1000)
    }

    /** Text of a turn; pauses of three seconds or more inside it become "(...)" (Dresing/Pehl). */
    private fun turnText(turn: Turn, pauses: Boolean): String = buildString {
        turn.segments.forEachIndexed { i, s ->
            if (i > 0) {
                val gap = s.start - turn.segments[i - 1].end
                append(if (pauses && gap >= 3.0) " (...) " else " ")
            }
            append(s.text.trim())
        }
    }

    /** Short labels for Dresing/Pehl: the speaker's name if it is short (I, B1, LK …), else I / B1, B2 … */
    fun shortLabels(t: Transcript): Map<Int, String> {
        val ids = t.segments.mapNotNull { it.speaker }.distinct()
        val custom = ids.associateWith { t.speakerName(it) }
        if (custom.values.all { it.length <= 4 && !it.startsWith("Sprecher") }) return custom
        return ids.mapIndexed { i, id -> id to (if (i == 0) "I" else "B$i") }.toMap()
    }

    fun plainText(t: Transcript): String = turns(t).joinToString("\n\n") { turn ->
        (if (t.diarized) t.speakerName(turn.speaker) + ": " else "") + turnText(turn, pauses = false)
    }

    /** Readable Markdown: title, details, then one paragraph per contribution with time and speaker. */
    fun markdown(t: Transcript): String = buildString {
        append("# ").append(t.title).append("\n\n")
        append(details(t)).append("\n\n")
        for (turn in turns(t)) {
            append("**[").append(clock(turn.start)).append("]")
            if (t.diarized) append(" ").append(t.speakerName(turn.speaker))
            append("**  \n")
            append(turnText(turn, pauses = false)).append("\n\n")
        }
    }

    /** Dresing/Pehl (simplified rules) as Markdown for Word: abbreviation, text, pauses, time mark at the end. */
    fun dresingPehl(t: Transcript): String = buildString {
        val labels = shortLabels(t)
        append("# ").append(t.title).append("\n\n")
        append(details(t)).append("  \n")
        append("Transkriptionsregeln: einfaches Transkript nach Dresing & Pehl. ")
        if (t.diarized) append("Kürzel: ").append(labels.entries.joinToString(", ") { "${it.value} = ${t.speakerName(it.key)}" }).append(". ")
        append("Pausen ab drei Sekunden: (...). Zeitmarke am Ende jedes Beitrags.\n\n")
        for (turn in turns(t)) {
            if (t.diarized) append("**").append(labels[turn.speaker] ?: "?").append(":** ")
            append(turnText(turn, pauses = true)).append(" ").append(f4Mark(turn.end)).append("\n\n")
        }
    }

    /** Plain text for MAXQDA/f4: "I: text #00:01:23-4#" per contribution, blank line between. */
    fun maxqda(t: Transcript): String = turns(t).joinToString("\r\n\r\n") { turn ->
        val labels = shortLabels(t)
        (if (t.diarized) (labels[turn.speaker] ?: "?") + ": " else "") + turnText(turn, pauses = true) + " " + f4Mark(turn.end)
    } + "\r\n"

    /** Subtitle cues: at most two lines of [maxLine] characters, at least ~1 s each; long segments are split. */
    data class Cue(val start: Double, val end: Double, val lines: List<String>, val speaker: Int?)

    fun cues(t: Transcript, maxLine: Int = 42): List<Cue> {
        val out = mutableListOf<Cue>()
        for (s in t.segments) {
            val words = s.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            // Words into lines, lines into cues of two.
            val lines = mutableListOf<MutableList<Int>>()
            var cur = mutableListOf<Int>()
            var len = 0
            words.forEachIndexed { i, w ->
                if (cur.isNotEmpty() && len + 1 + w.length > maxLine) { lines += cur; cur = mutableListOf(); len = 0 }
                cur += i
                len += (if (len > 0) 1 else 0) + w.length
            }
            if (cur.isNotEmpty()) lines += cur
            val groups = lines.chunked(2)
            val timed = s.words.size == words.size
            val dur = (s.end - s.start).coerceAtLeast(0.5)
            groups.forEach { g ->
                val first = g.first().first()
                val last = g.last().last()
                val start = if (timed) s.words[first].start else s.start + dur * first / words.size
                val end = if (timed) s.words[last].end else s.start + dur * (last + 1) / words.size
                out += Cue(start, maxOf(end, start + 1.0).coerceAtMost(maxOf(s.end, start + 1.0)), g.map { line -> line.joinToString(" ") { words[it] } }, s.speaker)
            }
        }
        // No overlaps: a cue ends when the next one starts.
        return out.mapIndexed { i, c -> if (i + 1 < out.size && c.end > out[i + 1].start) c.copy(end = maxOf(c.start + 0.3, out[i + 1].start)) else c }
    }

    /** SRT; a change of speaker starts with "– " (common subtitle convention, SRT has no speaker field). */
    fun srt(t: Transcript): String {
        val cues = cues(t)
        return cues.mapIndexed { i, c ->
            val change = t.diarized && i > 0 && cues[i - 1].speaker != c.speaker
            "${i + 1}\n${srtTime(c.start, ',')} --> ${srtTime(c.end, ',')}\n${if (change) "– " else ""}${c.lines.joinToString("\n")}"
        }.joinToString("\n\n") + "\n"
    }

    fun vtt(t: Transcript): String = buildString {
        append("WEBVTT\n\n")
        cues(t).forEach { c ->
            append(srtTime(c.start, '.')).append(" --> ").append(srtTime(c.end, '.')).append("\n")
            val text = c.lines.joinToString("\n")
            if (t.diarized && c.speaker != null) append("<v ").append(t.speakerName(c.speaker).replace(">", "")).append(">").append(text) else append(text)
            append("\n\n")
        }
    }

    /** "Aufnahme vom 21.09.2026 · 0:58 · Whisper Turbo · 3 Sprecher" */
    fun details(t: Transcript): String {
        val parts = mutableListOf<String>()
        t.created.takeIf { it.length >= 10 }?.let { d -> parts += "Aufnahme vom ${d.substring(8, 10)}.${d.substring(5, 7)}.${d.substring(0, 4)}" }
        parts += "Dauer ${clock(t.duration)}"
        if (t.modelName.isNotBlank()) parts += "erkannt mit ${t.modelName}"
        if (t.diarized) parts += "${t.speakers.size} Sprecher"
        return "*" + parts.joinToString(" · ") + "*"
    }

    /** Text content of a format (Word and PDF: the Markdown the document writer renders). */
    fun content(t: Transcript, f: TranscriptFormat): String = when (f) {
        TranscriptFormat.DOCX, TranscriptFormat.PDF, TranscriptFormat.MARKDOWN -> markdown(t)
        TranscriptFormat.DRESING_PEHL -> dresingPehl(t)
        TranscriptFormat.MAXQDA -> maxqda(t)
        TranscriptFormat.SRT -> srt(t)
        TranscriptFormat.VTT -> vtt(t)
        TranscriptFormat.TXT -> plainText(t)
    }
}
