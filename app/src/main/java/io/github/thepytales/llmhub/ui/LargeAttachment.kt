package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.files.AttachmentBudget

/**
 * Große Anhänge (AP-85): a file does not fit the chosen model – instead of cutting it silently the app asks: send it
 * whole with a model that takes it (GLM-5.3 Flash), only the passages that fit the question, or a section by section
 * summary.
 */
@Composable
internal fun LargeAttachmentDialog(c: AppContainer, file: FileAttachment, all: List<FileAttachment>, model: String, onChoose: (AttachmentBudget.Mode, String?) -> Unit, onDismiss: () -> Unit) {
    val total = all.sumOf { it.text.length }
    // Lange Texte lokal (AP-108): with a model on this device each way says how long it takes here.
    fun time(mode: AttachmentBudget.Mode) = AttachmentDigest.estimateSeconds(c, total, model, mode)?.let { " (" + io.github.thepytales.llmhub.local.LongText.duration(it) + ")" } ?: ""
    val budget = AttachmentBudget.budgetChars(model)
    val fit = AttachmentBudget.suggestModel(total)?.takeIf { it != model && !model.startsWith(io.github.thepytales.llmhub.data.Privacy.LOCAL_PREFIX) }
    val pages = file.pages?.let { tr("{0} Seiten", it) } ?: tr("{0}k Zeichen", file.text.length / 1000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("„{0}“ ist groß", file.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("{0} – {1} fasst davon etwa {2} %. Wie soll die Datei mitgehen?", pages, ModelCatalog.info(model).displayName, (budget * 100L / total.coerceAtLeast(1)).coerceIn(1, 99)))
                val onDevice = model.startsWith(io.github.thepytales.llmhub.data.Privacy.LOCAL_PREFIX)
                if (onDevice && total <= AttachmentBudget.wholeChars(model)) OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.FULL, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Ganz lesen auf diesem Gerät") + time(AttachmentBudget.Mode.FULL))
                } else if (!onDevice && fit != null) OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.FULL, fit) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Ganz senden mit {0}", ModelCatalog.info(fit).displayName))
                }
                OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.PASSAGES, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Nur die zur Frage passenden Stellen") + time(AttachmentBudget.Mode.PASSAGES))
                }
                OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.SUMMARY, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Abschnittsweise zusammenfassen") + time(AttachmentBudget.Mode.SUMMARY))
                }
                if (model.startsWith(io.github.thepytales.llmhub.data.Privacy.LOCAL_PREFIX)) HintText(tr("Das Modell läuft auf diesem Gerät – die Zeiten sind die Vorhersage für dieses Gerät. Nichts verlässt das Gerät."))
                HintText(tr("Ganz: am genauesten, braucht ein Modell mit großem Kontext. Passende Stellen: schnell, gut für gezielte Fragen. Zusammenfassen: für Überblicksfragen; dauert einen Moment und braucht einige Anfragen."))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Später entscheiden")) } },
    )
}

/**
 * Abschnittsweise zusammenfassen (AP-85, AP-108): the file in pieces that fit [model], each summarized with its page
 * marks, then joined into an overview at the top (Zusammenführung; in groups first if the summaries do not fit one
 * request). Progress with the remaining time goes to [io.github.thepytales.llmhub.local.LongText.progress].
 */
object AttachmentDigest {
    private const val SECTION_PROMPT = "Du fasst einen Abschnitt eines langen Dokuments sachlich und dicht zusammen: Kernaussagen, Definitionen, " +
        "Zahlen, Ergebnisse, Fristen und Namen von Konzepten bleiben; Seitenangaben [Seite n] übernimmst du zu jeder Aussage. Keine Einleitung, keine Wertung. Deutsch."
    private const val JOIN_PROMPT = "Du führst Zusammenfassungen von Abschnitten eines Dokuments zu einem Überblick zusammen: Worum geht es, die " +
        "wichtigsten Aussagen und Ergebnisse, Zahlen und Fristen – jeweils mit den Seitenangaben [Seite n] aus den Abschnitten. Keine Einleitung. Deutsch."

    /** Piece size for [model]: half of what its context takes (local models: after the memory of this device, AP-108). */
    fun pieceChars(model: String): Int = (AttachmentBudget.budgetChars(model) / 2).coerceIn(12_000, 400_000)

    /** The pieces of [text], each beginning with the page mark of its start so the summaries keep their pages. */
    internal fun pieces(text: String, size: Int): List<String> {
        val marks = Regex("\\[Seite (\\d+)]").findAll(text).map { it.range.first to it.groupValues[1] }.toList()
        return text.chunked(size).mapIndexed { i, part ->
            val start = i * size
            val page = marks.lastOrNull { it.first <= start }?.second
            if (page != null && !part.trimStart().startsWith("[Seite")) "[Seite $page]\n$part" else part
        }
    }

    suspend fun make(c: AppContainer, file: FileAttachment, model: String, progress: (Int, Int) -> Unit = { _, _ -> }): String {
        val parts = pieces(file.text, pieceChars(model))
        val key = io.github.thepytales.llmhub.local.LongText.key(file.name, file.text.length)
        val estimate = estimateSeconds(c, file.text.length, model, AttachmentBudget.Mode.SUMMARY) ?: 0.0
        val started = System.currentTimeMillis()
        suspend fun ask(system: String, user: String) = c.llm.streamChat(
            model, listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user)), null,
            thinking = io.github.thepytales.llmhub.net.ThinkingLevel.OFF,
        ) { c.localLlm.touch() }.content.trim()
        try {
            val sections = parts.mapIndexed { i, part ->
                progress(i + 1, parts.size)
                io.github.thepytales.llmhub.local.LongText.update(key, io.github.thepytales.llmhub.local.LongText.Progress(i, parts.size, started, estimate))
                tr("Abschnitt {0} von {1}", i + 1, parts.size) to ask(SECTION_PROMPT, "Abschnitt ${i + 1} von ${parts.size} aus „${file.name}“:\n\n$part")
            }
            val body = sections.joinToString("\n\n") { (title, text) -> "## $title\n\n$text" }
            if (sections.size == 1) return body
            // Zusammenführung (AP-108): the summaries – in groups if they do not fit one request – become an overview.
            val room = (AttachmentBudget.budgetChars(model) * 0.8).toInt().coerceAtLeast(8_000)
            var level = sections.map { it.second }
            while (level.sumOf { it.length } > room && level.size > 1) {
                val groups = mutableListOf<MutableList<String>>(mutableListOf())
                for (t in level) { if (groups.last().sumOf { it.length } + t.length > room && groups.last().isNotEmpty()) groups += mutableListOf<String>(); groups.last() += t }
                if (groups.size == level.size) break
                level = groups.map { g -> ask(JOIN_PROMPT, g.joinToString("\n\n")) }
            }
            io.github.thepytales.llmhub.local.LongText.update(key, io.github.thepytales.llmhub.local.LongText.Progress(parts.size - 1, parts.size, started, estimate))
            val overview = ask(JOIN_PROMPT, "Abschnitte aus „${file.name}“:\n\n" + level.joinToString("\n\n").take(room))
            return "## " + tr("Überblick") + "\n\n" + overview + "\n\n" + body
        } finally {
            io.github.thepytales.llmhub.local.LongText.update(key, null)
        }
    }

    /**
     * Lange Texte lokal (AP-108): how long [mode] takes for [chars] characters with the model on this device – null for
     * server models (they are fast) or while the device is unknown.
     */
    fun estimateSeconds(c: AppContainer, chars: Int, model: String, mode: AttachmentBudget.Mode): Double? {
        val m = io.github.thepytales.llmhub.local.LocalCatalog.get(model)?.takeIf { model.startsWith(io.github.thepytales.llmhub.data.Privacy.LOCAL_PREFIX) } ?: return null
        val hw = c.localLlm.hardware.value ?: return null
        val f = io.github.thepytales.llmhub.local.DeviceBench.forecast(c, m, hw)
        val lt = io.github.thepytales.llmhub.local.LongText
        val plan = when (mode) {
            AttachmentBudget.Mode.FULL -> lt.whole(chars)
            AttachmentBudget.Mode.PASSAGES -> lt.passages(chars, AttachmentBudget.budgetChars(model))
            AttachmentBudget.Mode.SUMMARY -> lt.sections(chars, pieceChars(model), AttachmentBudget.contextTokens(model))
        }
        return lt.seconds(plan, f)
    }
}
