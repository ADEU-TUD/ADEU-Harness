package io.github.thepytales.llmhub.ui

import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog

/** One line of the agent protocol. */
data class ProtocolStep(val label: String, val detail: String, val ms: Long?, val error: Boolean = false)

/** Summary and steps of one answer: model requests, tool calls, waiting times and fallbacks. */
data class Protocol(val steps: List<ProtocolStep>, val totalMs: Long, val tokensIn: Long, val tokensOut: Long, val requests: Int, val tools: Int)

fun protocolOf(turn: List<ChatMessage>): Protocol {
    val steps = mutableListOf<ProtocolStep>()
    val results = turn.filter { it.role == "tool" }.associateBy { it.toolCallId }
    for (m in turn) {
        if (m.role != "assistant") continue
        val model = m.model?.let { ModelCatalog.info(it).displayName } ?: "Modell"
        m.events.forEach { steps += ProtocolStep("Hinweis", it, null) }
        if (m.durationMs != null) {
            val tokens = listOfNotNull(m.tokensIn?.let { "$it Tokens rein" }, m.tokensOut?.let { "$it raus" }).joinToString(", ")
            steps += ProtocolStep("Anfrage an $model", tokens, m.durationMs, m.error)
        } else if (m.toolCalls.isNotEmpty() && m.model == null) {
            steps += ProtocolStep("Von der App erstellt", "ohne Modell", null)
        }
        m.toolCalls.forEach { call ->
            val r = results[call.id]
            val failed = r?.content?.startsWith("FEHLER") == true
            steps += ProtocolStep(
                "Werkzeug ${call.name}",
                if (failed) r!!.content.take(160) else shortArgs(call.arguments).take(120),
                r?.durationMs,
                failed,
            )
        }
    }
    val assistant = turn.filter { it.role == "assistant" }
    return Protocol(
        steps,
        turn.sumOf { it.durationMs ?: 0 },
        assistant.sumOf { it.tokensIn ?: 0 },
        assistant.sumOf { it.tokensOut ?: 0 },
        assistant.count { it.durationMs != null },
        turn.count { it.role == "tool" },
    )
}

private fun seconds(ms: Long) = if (ms < 1000) "$ms ms" else "%.1f s".format(ms / 1000.0)

@Composable
fun AgentProtocolDialog(turn: List<ChatMessage>, onDismiss: () -> Unit, question: ChatMessage? = null, chatId: String? = null) {
    val p = protocolOf(turn)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agent-Protokoll") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${seconds(p.totalMs)} · ${p.requests} Anfrage(n) · ${p.tools} Werkzeug-Aufruf(e) · ${p.tokensIn} Tokens rein, ${p.tokensOut} raus",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsHint("Tokens sind Angaben des Servers, sonst geschätzt. Werkzeuge einer Antwort laufen teilweise gleichzeitig.")
                HorizontalDivider()
                // What the pseudonymization replaced in this turn, with one-click corrections (AP-03).
                val c = io.github.thepytales.llmhub.App.container
                if (c.settings.current.pseudonymize) {
                    val texts = remember(turn, question) {
                        listOfNotNull(question?.content) + (question?.files.orEmpty().map { it.text }) +
                            turn.filter { it.role == "tool" && it.toolName !in io.github.thepytales.llmhub.data.Pseudonymizer.PUBLIC_TOOLS }.map { it.content }
                    }
                    PseudonymCorrections(c, texts, chatId)
                    HorizontalDivider()
                }
                p.steps.forEach { s ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (s.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            if (s.detail.isNotBlank()) {
                                Text(
                                    s.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = if (s.label.startsWith("Werkzeug")) FontFamily.Monospace else null,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            s.ms?.let { seconds(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp).width(56.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
