package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.agent.AskUserTool
import io.github.thepytales.llmhub.agent.UserQuestion

/**
 * The card of a question round (AP-06): each question with its answer buttons (one or several), a field for an own
 * answer, „Antworten“ sends everything as one message, „Überspringen“ lets the model go on with assumptions. Once
 * answered ([onAnswer] null) it shows the questions quietly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(questions: List<UserQuestion>, onAnswer: ((String) -> Unit)?) {
    if (questions.isEmpty()) return
    val chosen = remember(questions) { mutableStateListOf<Set<String>>().apply { repeat(questions.size) { add(emptySet()) } } }
    val free = remember(questions) { mutableStateListOf<String>().apply { repeat(questions.size) { add("") } } }
    val active = onAnswer != null
    AccentBox {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    tr(if (active) "Kurze Rückfrage" else "Rückfrage beantwortet"),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                )
            }
            questions.forEachIndexed { i, q ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(q.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (q.options.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            q.options.forEach { option ->
                                val selected = option in chosen[i]
                                FilterChip(
                                    selected = selected,
                                    enabled = active,
                                    onClick = {
                                        chosen[i] = when {
                                            q.multi && selected -> chosen[i] - option
                                            q.multi -> chosen[i] + option
                                            selected -> emptySet()
                                            else -> setOf(option)
                                        }
                                    },
                                    label = { Text(option) },
                                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                                )
                            }
                        }
                        if (q.multi && active) HintText(tr("Mehrere Antworten möglich"))
                    }
                    if (active) {
                        OutlinedTextField(
                            value = free[i],
                            onValueChange = { free[i] = it },
                            placeholder = { Text(tr(if (q.options.isEmpty()) "Antwort" else "Oder eigene Antwort …")) },
                            singleLine = false,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (onAnswer != null) {
                val any = chosen.any { it.isNotEmpty() } || free.any { it.isNotBlank() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onAnswer(AskUserTool.answerText(questions, chosen.toList(), free.toList())) }, enabled = any) { Text(tr("Antworten")) }
                    TextButton(onClick = { onAnswer(AskUserTool.SKIP_TEXT) }) { Text(tr("Überspringen")) }
                }
            }
        }
    }
}
