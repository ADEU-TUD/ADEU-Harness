package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.agent.DayJournal
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * „Alles rückgängig seit heute Morgen“ (AP-77): Lora's actions of the day, newest first – one by one or all at once
 * zurücknehmen. Lines from before a restart of the app say that they can no longer be taken back.
 */
@Composable
internal fun DayUndoCard() {
    val version by DayJournal.version.collectAsStateWithLifecycle()
    var tick by remember { mutableStateOf(0) }
    val entries = remember(version, tick) { DayJournal.today() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    val time = remember { DateTimeFormatter.ofPattern("HH:mm") }
    ResultCard(icon = Icons.Outlined.History, title = tr("Heute erledigt"), subtitle = tr("{0} Aktion(en) seit heute Morgen", entries.size)) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            if (entries.isEmpty()) HintText(tr("Heute hat Lora noch nichts in den Werkzeugen erledigt."))
            entries.take(30).forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(time.format(e.at) + "  " + e.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        when {
                            e.undone != null -> HintText(tr("Zurückgenommen: {0}", e.undone!!))
                            e.result == null -> HintText(tr("Vor dem Neustart der App – lässt sich nicht mehr zurücknehmen."))
                            e.result.undo == null -> HintText(
                                if (e.actionId in io.github.thepytales.llmhub.agent.DryRun.IN_TOOL_UNDO) tr("Nur im Werkzeug mit Strg+Z zurücknehmbar.")
                                else tr("Hat nichts verändert, das zurückzunehmen wäre."),
                            )
                        }
                    }
                    if (e.canUndo) TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch { try { DayJournal.undo(e) } finally { busy = false; tick++ } }
                    }) { Text(tr("Rückgängig")) }
                }
            }
            val open = entries.count { it.canUndo }
            if (open > 1) {
                FilledTonalButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val (done, rest) = DayJournal.undoAll(entries)
                            summary = if (rest.isEmpty()) tr("{0} Aktion(en) zurückgenommen.", done)
                            else tr("{0} Aktion(en) zurückgenommen; {1} lassen sich nicht mehr zurücknehmen (siehe oben).", done, rest.size)
                        } finally { busy = false; tick++ }
                    }
                }) { Text(tr("Alle {0} zurücknehmen", open)) }
            }
            summary?.let { HintText(it) }
        }
    }
}
