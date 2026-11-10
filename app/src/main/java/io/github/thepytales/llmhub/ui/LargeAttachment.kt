package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
internal fun LargeAttachmentDialog(file: FileAttachment, all: List<FileAttachment>, model: String, onChoose: (AttachmentBudget.Mode, String?) -> Unit, onDismiss: () -> Unit) {
    val total = all.sumOf { it.text.length }
    val budget = AttachmentBudget.budgetChars(model)
    val fit = AttachmentBudget.suggestModel(total)?.takeIf { it != model }
    val pages = file.pages?.let { tr("{0} Seiten", it) } ?: tr("{0}k Zeichen", file.text.length / 1000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("„{0}“ ist groß", file.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("{0} – {1} fasst davon etwa {2} %. Wie soll die Datei mitgehen?", pages, ModelCatalog.info(model).displayName, (budget * 100L / total.coerceAtLeast(1)).coerceIn(1, 99)))
                if (fit != null) OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.FULL, fit) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Ganz senden mit {0}", ModelCatalog.info(fit).displayName))
                }
                OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.PASSAGES, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Nur die zur Frage passenden Stellen"))
                }
                OutlinedButton(onClick = { onChoose(AttachmentBudget.Mode.SUMMARY, null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Abschnittsweise zusammenfassen"))
                }
                HintText(tr("Ganz: am genauesten, braucht ein Modell mit großem Kontext. Passende Stellen: schnell, gut für gezielte Fragen. Zusammenfassen: für Überblicksfragen; dauert einen Moment und braucht einige Anfragen."))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Später entscheiden")) } },
    )
}

/** Abschnittsweise zusammenfassen (AP-85): the file in pieces that fit [model], each summarized, joined with page hints. */
object AttachmentDigest {
    suspend fun make(c: AppContainer, file: FileAttachment, model: String, progress: (Int, Int) -> Unit = { _, _ -> }): String {
        val piece = (AttachmentBudget.budgetChars(model) / 2).coerceIn(20_000, 400_000)
        val parts = file.text.chunked(piece)
        val out = StringBuilder()
        parts.forEachIndexed { i, part ->
            progress(i + 1, parts.size)
            val r = c.llm.streamChat(
                model, listOf(
                    ChatMessage(role = "system", content = "Du fasst einen Abschnitt eines langen Dokuments sachlich und dicht zusammen: Kernaussagen, Definitionen, " +
                        "Zahlen, Ergebnisse, Fristen und Namen von Konzepten bleiben; Seitenangaben [Seite n] übernimmst du. Keine Einleitung, keine Wertung. Deutsch."),
                    ChatMessage(role = "user", content = "Abschnitt ${i + 1} von ${parts.size} aus „${file.name}“:\n\n$part"),
                ), null, thinking = io.github.thepytales.llmhub.net.ThinkingLevel.OFF,
            ) { }
            out.append("## ").append(tr("Abschnitt {0} von {1}", i + 1, parts.size)).append("\n\n").append(r.content.trim()).append("\n\n")
        }
        return out.toString().trim()
    }
}
