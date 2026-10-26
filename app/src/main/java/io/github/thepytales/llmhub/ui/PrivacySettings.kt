package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.NameList
import io.github.thepytales.llmhub.data.PiiFeature

/**
 * Einstellungen › Datenschutz: pseudonymization with a switch for each part, the own list (import/export), words that
 * are never replaced, a preview and the stored pairs.
 */
@Composable
fun PrivacySection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var sample by remember {
        mutableStateOf(
            "Frau Dr. Anna Kaltenbach (a.kaltenbach@example.org, Tel. 0351 463-12345) bittet um ein Gutachten für Lukas Schmidt, " +
                "Matrikelnummer 4712345, wohnhaft Bergstraße 12, 01069 Dresden. Die Schülerin Jolina hat Annas Heft."
        )
    }
    var version by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val off = s.pseudonymOffFeatures

    fun setFeature(f: PiiFeature, on: Boolean) = c.settings.update { st ->
        val next = if (on) st.pseudonymOffFeatures - f else st.pseudonymOffFeatures + f
        st.copy(pseudonymOff = next.joinToString("\n") { it.name })
    }

    val import = rememberDocumentPicker { file ->
        val names = NameList.parse(file.text)
        if (names.isEmpty()) Notices.error("In „${file.name}“ wurden keine Namen gefunden (eine Spalte Name/Vorname/Nachname oder ein Name je Zeile).")
        else {
            c.settings.update { it.copy(pseudonymTerms = NameList.merge(it.pseudonymTerms, names)) }
            Notices.info("${names.size} Namen aus „${file.name}“ übernommen.")
        }
    }
    val export = rememberFileExport("text/plain", onDone = { ok -> if (ok) Notices.info("Liste gespeichert.") else Notices.error("Speichern fehlgeschlagen.") }) { out ->
        val text = buildString {
            append(c.settings.current.pseudonymTerms.trim()).append('\n')
            c.settings.current.pseudonymIgnore.trim().takeIf { it.isNotEmpty() }?.let { append("\n# Nie ersetzen\n").append(it).append('\n') }
        }
        out.write(text.toByteArray())
    }

    SettingsCard("Pseudonymisierung") {
        ToggleRow(
            "Personenbezogene Daten ersetzen",
            "Vor jeder Anfrage ersetzt die App Namen, Kontaktdaten, Nummern, Geburtsdaten und Anschriften auf dem Gerät durch " +
                "Platzhalter wie [Name 1]. Der Server sieht nur die Platzhalter; in der Antwort, in Dokumenten und E-Mail-Entwürfen " +
                "setzt die App die echten Angaben wieder ein.",
            s.pseudonymize,
        ) { v -> c.settings.update { it.copy(pseudonymize = v) } }
        ToggleRow(
            "Namen automatisch erkennen",
            "Namen nach Anrede oder Titel (Frau, Herr, Dr., Prof.), häufige Vor- und Nachnamen und – mit den Schaltern unten – Namen " +
                "nach Rollen, in Grußformeln und Tabellen. Ohne diesen Schalter nur die Angaben mit festem Muster und die eigene Liste.",
            s.pseudonymAuto,
        ) { v -> c.settings.update { it.copy(pseudonymAuto = v) } }
        if (!s.pseudonymize) SettingsHint("Die Pseudonymisierung ist aus – die Schalter unten wirken erst, wenn sie an ist.")
    }

    PiiFeature.entries.groupBy { it.group }.forEach { (group, features) ->
        SettingsCard(group) {
            features.forEach { f -> ToggleRow(f.title, f.detail, f !in off) { v -> setFeature(f, v) } }
        }
    }

    SettingsCard("Eigene Namen und Begriffe") {
        OutlinedTextField(
            value = s.pseudonymTerms,
            onValueChange = { v -> c.settings.update { it.copy(pseudonymTerms = v) } },
            label = { Text("Werden immer ersetzt") },
            placeholder = { Text("Je Zeile einer, z.B.\nJonte Vogelsang\nGymnasium Dresden-Plauen") },
            supportingText = {
                Text(
                    "Klassenlisten, Studierende, Schulen. Bei Personen erkennt die App auch Vor- und Nachnamen einzeln, gebeugt und in " +
                        "anderer Schreibweise. Nur mit dieser Liste ist die Erkennung von Namen vollständig zuverlässig."
                )
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = import) { Text("Aus Datei übernehmen …") }
            OutlinedButton(onClick = { export("Namensliste Pseudonymisierung.txt") }, enabled = s.pseudonymTerms.isNotBlank()) { Text("Liste speichern …") }
        }
        SettingsHint("Übernehmen liest Klassen- und Teilnehmerlisten (Excel, CSV, Word, Text): Spalten Vorname/Nachname oder Name, sonst ein Name je Zeile. Die gespeicherte Liste lässt sich an Kolleg/innen weitergeben.")
        OutlinedTextField(
            value = s.pseudonymIgnore,
            onValueChange = { v -> c.settings.update { it.copy(pseudonymIgnore = v) } },
            label = { Text("Nie ersetzen") },
            placeholder = { Text("Je Zeile eines, z.B.\nFrank\nMartin-Luther-Gymnasium") },
            supportingText = { Text("Wörter, die wie Namen aussehen, aber keine sind. Die eigene Liste oben geht vor.") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SettingsCard("Ausprobieren") {
        SettingsHint("So sähe der Server diesen Text. Nichts wird gesendet oder gespeichert.")
        OutlinedTextField(value = sample, onValueChange = { sample = it }, label = { Text("Beispieltext") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Text(
                remember(sample, s) { c.privacy.preview(sample) },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    SettingsCard("Zuordnungen") {
        val entries = remember(version, s) { c.privacy.all().filter { !it.alias } }
        val chats = remember(version, s) { c.privacy.chats() }
        SettingsHint(
            "${entries.size} Zuordnung(en) auf diesem Gerät" + (if (chats > 0) ", dazu eigene Platzhalter in $chats Chat(s)" else "") +
                ". Sie bleiben nur hier, damit dieselbe Person denselben Platzhalter behält."
        )
        if (entries.isNotEmpty()) {
            OutlinedTextField(value = filter, onValueChange = { filter = it }, label = { Text("Suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val shown = entries.filter { filter.isBlank() || it.value.contains(filter, true) || it.placeholder.contains(filter, true) }.asReversed()
            Column {
                shown.take(if (showAll) 500 else 12).forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.placeholder, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(0.42f))
                        Text(e.value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.58f))
                        IconButton(onClick = {
                            c.settings.update { it.copy(pseudonymIgnore = NameList.merge(it.pseudonymIgnore, listOf(e.value))) }
                            c.privacy.remove(e.value)
                            version++
                            Notices.info("„${e.value}“ wird nicht mehr ersetzt.")
                        }) { Icon(Icons.Outlined.Block, contentDescription = "Nie ersetzen") }
                        IconButton(onClick = { c.privacy.remove(e.value); version++ }) { Icon(Icons.Outlined.Delete, contentDescription = "Zuordnung löschen") }
                    }
                }
                if (shown.size > 12 && !showAll) TextButton(onClick = { showAll = true }) { Text("Alle ${shown.size} zeigen") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alle Zuordnungen", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty() || chats > 0) { Text("Löschen") }
        }
    }

    SettingsCard("Gut zu wissen") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsHint("• Bilder, Scans und Spracheingaben lassen sich nicht pseudonymisieren – mit „Warnung bei Bildern“ fragt die App vor dem Senden nach. Vorlesen mit der KI-Stimme schickt den Antworttext an den Uni-Server.")
            SettingsHint("• Ohne Pseudonymisierung warnt die App, wenn eine Nachricht mit Namen oder Kontaktdaten an einen Anbieter außerhalb der TU Dresden (z.B. Google Gemini) ginge.")
            SettingsHint("• Was ersetzt wurde und wie der Server die Nachricht sah, zeigt das Agent-Protokoll unter der Antwort (Verlaufssymbol).")
            SettingsHint("• Modelle auf diesem Gerät bekommen die echten Angaben – dort verlässt nichts das Gerät.")
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            "Zuordnungen löschen?",
            "Die App vergisst, welcher Platzhalter zu welcher Angabe gehört. Platzhalter in älteren Chats und Protokollen lassen sich " +
                "danach nicht mehr zurückübersetzen; laufende Antworten können Platzhalter statt Namen enthalten.",
            "Löschen",
            onConfirm = {
                c.privacy.clear()
                version++
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/**
 * Under the chat input: words that might be names but have no clear clue ("Schüler Jolin", "Anna Kowalczyk").
 * One tap puts a word on the own list, the ✕ on the list of words that are never replaced.
 */
@Composable
fun PrivacySuggestions(c: AppContainer, input: String) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!s.pii(PiiFeature.SUGGESTIONS) || input.length < 4) return
    // Recomputed only when a word is finished, not on every letter.
    val words = input.substringBeforeLast(' ', "")
    val suspects = remember(words, s.pseudonymTerms, s.pseudonymIgnore) {
        runCatching {
            val known = c.privacy.detect(words).map { it.value } + s.pseudonymTerms.lines().flatMap { it.trim().split(' ') }
            io.github.thepytales.llmhub.data.PiiDetector.suspects(words, known, s.pseudonymIgnore.lines().map { it.trim() }).take(4)
        }.getOrDefault(emptyList())
    }
    if (suspects.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Name?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        suspects.forEach { w ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        c.settings.update { it.copy(pseudonymTerms = NameList.merge(it.pseudonymTerms, listOf(w))) }
                        Notices.info("„$w“ wird ab jetzt ersetzt.")
                    }) { Text("$w ersetzen") }
                    IconButton(onClick = { c.settings.update { it.copy(pseudonymIgnore = NameList.merge(it.pseudonymIgnore, listOf(w))) } }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Kein Name")
                    }
                }
            }
        }
    }
}
