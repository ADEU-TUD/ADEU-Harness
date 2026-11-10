package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.LoraCommands
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.LoraCorrections

/**
 * Einstellungen › Lora › „Was Lora aus Korrekturen gelernt hat“ (AP-72): the switch, the list of the pairs *wish →
 * meant action* on this device with „Löschen“ per pair and for all, and the export for the next training of the command
 * model – after the user looked through the pairs and unticked what should not go out.
 */
@Composable
fun LoraCorrectionsSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    var entries by remember { mutableStateOf(LoraCorrections.all(c)) }
    var review by remember { mutableStateOf(false) }
    val targets = remember { AppMap.build(android = IS_ANDROID) }
    fun title(label: String) = LoraCommands.title(label, targets, IS_ANDROID) ?: label

    Section(tr("Was Lora aus Korrekturen gelernt hat")) {
        ToggleRow(
            tr("Aus Korrekturen lernen"),
            tr("Versteht Lora etwas falsch und du korrigierst sie – ein Knopf bei „Meinst du … oder …?“, „nein, ich meinte …“, ein Tipp auf die richtige Karte oder „Rückgängig“ direkt nach einer Aktion –, merkt sie sich das auf diesem Gerät und macht es beim nächsten Mal richtig. Im Tresor merkt sie sich nichts; nichts davon verlässt das Gerät von selbst."),
            s.loraLearn,
        ) { v -> c.settings.update { it.copy(loraLearn = v) } }
        if (entries.isEmpty()) HintText(tr("Noch nichts gelernt."))
        else {
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entries.sortedByDescending { it.at }.forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("„${e.wish}“", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            HintText(
                                if (e.wrong) tr("nicht ohne Rückfrage: {0}", title(e.label))
                                else tr("→ {0}", title(e.label)) + if (e.used > 0) " · " + tr("{0} × genutzt", e.used) else "",
                            )
                        }
                        IconButton(onClick = { LoraCorrections.remove(c, e); entries = LoraCorrections.all(c) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { review = true }) { Text(tr("Exportieren …")) }
                TextButton(onClick = { LoraCorrections.clear(c); entries = emptyList() }) { Text(tr("Alle löschen")) }
            }
        }
    }
    if (review) CorrectionsExport(entries.filterNot { it.wrong }, ::title) { review = false }
}

/**
 * Before the export (AP-72): every pair with a tick – untick what should not go out (names, anything private). The file
 * has one line per pair as the training data of tools/befehle (`text`, `bereich`, `label`).
 */
@Composable
private fun CorrectionsExport(entries: List<LoraCorrections.Entry>, title: (String) -> String, onDone: () -> Unit) {
    val chosen = remember { mutableStateListOf<LoraCorrections.Entry>().apply { addAll(entries) } }
    val export = rememberFileExport("application/x-ndjson", onDone = { ok ->
        if (ok) Notices.info(tr("Korrekturen gespeichert.")) else Notices.error(tr("Speichern fehlgeschlagen."))
        onDone()
    }) { out -> out.write(LoraCorrections.export(chosen.toList()).toByteArray(Charsets.UTF_8)) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Korrekturen exportieren")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HintText(tr("Für das nächste Training von Lora Befehle. Sieh die Wünsche durch und nimm alles heraus, was Namen oder Privates enthält – die Datei enthält genau die angehakten Zeilen."))
                if (entries.isEmpty()) HintText(tr("Nichts zu exportieren."))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    entries.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = e in chosen, onCheckedChange = { on -> if (on) chosen += e else chosen -= e })
                            Column {
                                Text("„${e.wish}“", style = MaterialTheme.typography.bodyMedium)
                                HintText(tr("→ {0}", title(e.label)))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { export("lora-korrekturen.jsonl") }, enabled = chosen.isNotEmpty()) { Text(tr("Speichern …")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("Abbrechen")) } },
    )
}

/**
 * Einstellungen › Lora › „Verlauf“ (AP-75): the switch for the last wishes, how many there are, the pinned commands with
 * „Lösen“, and „Verlauf löschen“.
 */
@Composable
fun LoraHistorySettings(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    val version by io.github.thepytales.llmhub.data.LoraHistory.version.collectAsState()
    val entries = remember(version) { io.github.thepytales.llmhub.data.LoraHistory.all(c) }
    Section(tr("Verlauf und eigene Befehle")) {
        ToggleRow(
            tr("Letzte Befehle merken"),
            tr("Die letzten 20 Wünsche an Lora und Strg+K – mit Pfeil hoch im Eingabefeld wieder holen, ändern, „Nochmal“ oder anheften. Angeheftete Befehle stehen unter ihrem Namen in Strg+K. Nur auf diesem Gerät, aus dem Tresor nichts."),
            s.loraHistory,
        ) { v -> c.settings.update { it.copy(loraHistory = v) } }
        val pinned = entries.filter { it.pinned }
        HintText(tr("{0} im Verlauf · {1} angeheftet", entries.count { !it.pinned }, pinned.size))
        pinned.sortedBy { it.name!!.lowercase() }.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name!!, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HintText("„${e.text}“")
                }
                TextButton(onClick = { io.github.thepytales.llmhub.data.LoraHistory.unpin(c, e.text) }) { Text(tr("Lösen")) }
            }
        }
        if (entries.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { io.github.thepytales.llmhub.data.LoraHistory.clear(c) }) { Text(tr("Verlauf löschen")) }
                if (pinned.isNotEmpty()) TextButton(onClick = { io.github.thepytales.llmhub.data.LoraHistory.clear(c, pinnedToo = true) }) { Text(tr("Alles löschen")) }
            }
        }
    }
}
