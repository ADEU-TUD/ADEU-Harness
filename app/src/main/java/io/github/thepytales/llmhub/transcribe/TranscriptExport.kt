package io.github.thepytales.llmhub.transcribe

import java.util.Locale
import kotlin.math.roundToLong
import io.github.thepytales.llmhub.ui.trLater

/** Export formats of a transcript. */
enum class TranscriptFormat(val label: String, val ext: String, val description: String) {
    DOCX("Word", "docx", trLater("Mit Sprechern, Zeitmarken und Kommentaren, zum Weiterbearbeiten.")),
    PDF("PDF", "pdf", trLater("Zum Lesen, Drucken und Weitergeben.")),
    DRESING_PEHL(trLater("Transkript nach Dresing/Pehl"), "docx", trLater("Einfache Transkriptionsregeln: ein Absatz pro Beitrag, Kürzel (I, B1 …), Pausen (...) und Zeitmarke am Absatzende.")),
    MAXQDA(trLater("MAXQDA / f4 (Text mit Zeitmarken)"), "txt", trLater("Zeitmarken #00:01:23-4# – MAXQDA verknüpft sie beim Import mit der Audiodatei.")),
    SRT(trLater("Untertitel SRT"), "srt", trLater("Für Lehrvideos (OPAL, YouTube, Videoplayer): höchstens zwei Zeilen mit je 42 Zeichen.")),
    VTT(trLater("Untertitel WebVTT"), "vtt", trLater("Untertitel für Webseiten und Videoplattformen, mit Sprechernamen.")),
    TXT(trLater("Nur Text"), "txt", trLater("Reiner Text; Zeitmarken und Kommentare wie oben gewählt.")),
    MARKDOWN("Markdown", "md", trLater("Für Notizen und Wikis.")),
}

/** What goes into Word, PDF, Markdown and text: time stamps (as in the view) and comments. */
data class ExportOptions(val stamps: TranscriptEdit.Stamps = TranscriptEdit.Stamps.PARAGRAPH, val comments: Boolean = true)

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
    private fun turnText(turn: Turn, pauses: Boolean, marks: Map<Segment, List<InlineMark>> = emptyMap(), comments: Boolean = false): String = buildString {
        turn.segments.forEachIndexed { i, s ->
            if (i > 0) {
                val gap = s.start - turn.segments[i - 1].end
                append(if (pauses && gap >= 3.0) " (...) " else " ")
            }
            val m = marks[s].orEmpty().filter { !(i == 0 && it.offset == 0) }
            var pos = 0
            for (mark in m) {
                append(s.text.substring(pos, mark.offset))
                append("(").append(clock(mark.time)).append(") ")
                pos = mark.offset
            }
            append(s.text.substring(pos).trim())
            if (comments && !s.comment.isNullOrBlank()) append(" [Kommentar: ").append(s.comment.trim()).append("]")
        }
    }.replace(Regex(" {2,}"), " ").trim()

    private fun marksBySegment(t: Transcript, stamps: TranscriptEdit.Stamps): Map<Segment, List<InlineMark>> =
        java.util.IdentityHashMap<Segment, List<InlineMark>>().apply { t.segments.zip(TranscriptEdit.inlineMarks(t, stamps)).forEach { (s, m) -> put(s, m) } }

    /** Short labels for Dresing/Pehl: the speaker's name if it is short (I, B1, LK …), else I / B1, B2 … */
    fun shortLabels(t: Transcript): Map<Int, String> {
        val ids = t.segments.mapNotNull { it.speaker }.distinct()
        val custom = ids.associateWith { t.speakerName(it) }
        if (custom.values.all { it.length <= 4 && !it.startsWith("Sprecher") }) return custom
        return ids.mapIndexed { i, id -> id to (if (i == 0) "I" else "B$i") }.toMap()
    }

    fun plainText(t: Transcript, opts: ExportOptions = ExportOptions(TranscriptEdit.Stamps.OFF, comments = false)): String {
        val marks = marksBySegment(t, opts.stamps)
        return turns(t).joinToString("\n\n") { turn ->
            (if (opts.stamps != TranscriptEdit.Stamps.OFF) "[" + clock(turn.start) + "] " else "") +
                (if (t.diarized) t.speakerName(turn.speaker) + ": " else "") + turnText(turn, pauses = false, marks, opts.comments)
        }
    }

    /** Readable Markdown: title, details, then one paragraph per contribution with time and speaker. */
    fun markdown(t: Transcript, opts: ExportOptions = ExportOptions()): String = buildString {
        val marks = marksBySegment(t, opts.stamps)
        append("# ").append(t.title).append("\n\n")
        append(details(t)).append("\n\n")
        t.summary?.let { append("## Zusammenfassung\n\n").append(it.trim()).append("\n\n") }
        if (t.chapters.isNotEmpty()) {
            append("## Kapitel\n\n")
            t.chapters.forEach { append("- ").append(clock(it.time)).append(" – ").append(it.title).append("\n") }
            append("\n## Transkript\n\n")
        }
        for (turn in turns(t)) {
            if (opts.stamps != TranscriptEdit.Stamps.OFF || t.diarized) {
                append("**")
                if (opts.stamps != TranscriptEdit.Stamps.OFF) append("[").append(clock(turn.start)).append("]")
                if (t.diarized) append(if (opts.stamps != TranscriptEdit.Stamps.OFF) " " else "").append(t.speakerName(turn.speaker))
                append("**  \n")
            }
            append(turnText(turn, pauses = false, marks, opts.comments)).append("\n\n")
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
            // Measured word times where there are some; subtitles need a time for every line, so without them the
            // segment is divided by the number of words (only then).
            val tw = TranscriptEdit.textWords(s)
            val timed = tw.size == words.size && tw.all { it.word?.measured == true }
            val dur = (s.end - s.start).coerceAtLeast(0.5)
            groups.forEach { g ->
                val first = g.first().first()
                val last = g.last().last()
                val start = if (timed) tw[first].word!!.start else s.start + dur * first / words.size
                val end = if (timed) tw[last].word!!.end else s.start + dur * (last + 1) / words.size
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
        if (t.measuredShare > 0) parts += "Wortzeiten gemessen" + (t.wordTiming?.source?.takeIf { it.isNotBlank() }?.let { " mit $it" } ?: "") +
            " (${Math.round(t.measuredShare * 100)} % der Wörter)"
        if (t.status != TranscriptStatus.RAW) parts += t.status.label
        return "*" + parts.joinToString(" · ") + "*"
    }

    /** Text content of a format (Word and PDF: the Markdown the document writer renders). */
    fun content(t: Transcript, f: TranscriptFormat, opts: ExportOptions = ExportOptions()): String = when (f) {
        TranscriptFormat.DOCX, TranscriptFormat.PDF, TranscriptFormat.MARKDOWN -> markdown(t, opts)
        TranscriptFormat.DRESING_PEHL -> dresingPehl(t)
        TranscriptFormat.MAXQDA -> maxqda(t)
        TranscriptFormat.SRT -> srt(t)
        TranscriptFormat.VTT -> vtt(t)
        TranscriptFormat.TXT -> plainText(t, opts)
    }

    /** Formats that take [ExportOptions] (the others follow fixed rules). */
    fun takesOptions(f: TranscriptFormat) = f in setOf(TranscriptFormat.DOCX, TranscriptFormat.PDF, TranscriptFormat.MARKDOWN, TranscriptFormat.TXT)
}
