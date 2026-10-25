package io.github.thepytales.llmhub.transcribe

import io.github.thepytales.llmhub.data.ChatMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/**
 * Chapters and summary by the language model. The model gets the transcript with a time stamp per sentence and
 * answers with JSON; the times are checked against the transcript (only times of real segment or word starts are
 * kept, so a chapter never starts somewhere the model invented). Pure Kotlin (tested).
 */
object TranscriptChapters {
    fun prompt(t: Transcript): List<ChatMessage> {
        val system = "Du gliederst Transkripte (Interviews, Unterrichtsgespräche, Besprechungen) für die empirische Unterrichtsforschung. " +
            "Antworte ausschließlich mit JSON ohne Code-Block: {\"summary\": \"…\", \"chapters\": [{\"start\": \"m:ss\", \"title\": \"…\"}]}. " +
            "summary: 3 bis 6 sachliche Sätze auf Deutsch, nur was im Transkript steht, keine Bewertung. chapters: Themenabschnitte in " +
            "zeitlicher Reihenfolge, kurze Titel (2 bis 6 Wörter), Startzeit genau aus einer Zeitmarke des Transkripts; je nach Länge 2 bis 12 Kapitel."
        val text = TranscriptExport.plainText(t, ExportOptions(TranscriptEdit.Stamps.SENTENCE, comments = false))
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = "Transkript „${t.title}“ (Dauer ${TranscriptExport.clock(t.duration)}):\n\n$text"),
        )
    }

    /** "1:23" or "1:02:03" in seconds. */
    fun seconds(clock: String): Double? {
        val p = clock.trim().split(':').map { it.trim().toDoubleOrNull() ?: return null }
        return when (p.size) { 2 -> p[0] * 60 + p[1]; 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 1 -> p[0]; else -> null }
    }

    /** Times a chapter may start at: segment starts and measured word starts. */
    private fun anchors(t: Transcript): List<Double> =
        (t.segments.map { it.start } + t.segments.flatMap { s -> s.words.filter { it.measured }.map { it.start } }).sorted()

    /** Reads the answer; chapter times snap to the nearest real start (the clock in the text has whole seconds). */
    fun parse(answer: String, t: Transcript): Pair<String?, List<Chapter>> {
        val from = answer.indexOf('{')
        val to = answer.lastIndexOf('}')
        if (from < 0 || to <= from) return null to emptyList()
        val o = runCatching { SpeechJson.parseToJsonElement(answer.substring(from, to + 1)) as JsonObject }.getOrNull() ?: return null to emptyList()
        val anchors = anchors(t)
        val chapters = runCatching { o["chapters"]!!.jsonArray }.getOrNull().orEmpty().mapNotNull { el ->
            val c = el as? JsonObject ?: return@mapNotNull null
            val title = c.str("title")?.trim().orEmpty()
            val at = c.str("start")?.let(::seconds) ?: c.num("start") ?: return@mapNotNull null
            if (title.isEmpty() || at > t.duration + 1) return@mapNotNull null
            val snapped = anchors.filter { it >= at - 0.01 && it < at + 1 }.minOrNull() ?: anchors.minByOrNull { Math.abs(it - at) } ?: at
            Chapter(snapped, title)
        }.distinctBy { it.time }.sortedBy { it.time }
        return o.str("summary")?.trim()?.takeIf { it.isNotEmpty() } to chapters
    }
}
