package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    // Opening the settings offers the recognition model once (AP-67), like the first pseudonymized request.
    LaunchedEffect(Unit) { c.piiModel.offerIfNeeded() }
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
        if (names.isEmpty()) Notices.error(tr("In „{0}“ wurden keine Namen gefunden (eine Spalte Name/Vorname/Nachname oder ein Name je Zeile).", file.name))
        else {
            c.settings.update { it.copy(pseudonymTerms = NameList.merge(it.pseudonymTerms, names)) }
            Notices.info(tr("{0} Namen aus „{1}“ übernommen.", names.size, file.name))
        }
    }
    val export = rememberFileExport("text/plain", onDone = { ok -> if (ok) Notices.info(tr("Liste gespeichert.")) else Notices.error(tr("Speichern fehlgeschlagen.")) }) { out ->
        val text = buildString {
            append(c.settings.current.pseudonymTerms.trim()).append('\n')
            c.settings.current.pseudonymIgnore.trim().takeIf { it.isNotEmpty() }?.let { append("\n# Nie ersetzen\n").append(it).append('\n') }
        }
        out.write(text.toByteArray())
    }

    SettingsCard(tr("Pseudonymisierung")) {
        Spot("privacy.pseudonymize") { ToggleRow(
            tr("Personenbezogene Daten ersetzen"),
            tr("Vor jeder Anfrage ersetzt die App Namen, Kontaktdaten, Nummern, Geburtsdaten und Anschriften auf dem Gerät durch " +
                "Platzhalter wie [Name 1]. Der Server sieht nur die Platzhalter; in der Antwort, in Dokumenten und E-Mail-Entwürfen " +
                "setzt die App die echten Angaben wieder ein."),
            s.pseudonymize,
        ) { v -> c.settings.update { it.copy(pseudonymize = v) } } }
        PiiModelSettings(c)
        ToggleRow(
            tr("Namen automatisch erkennen"),
            tr("Namen nach Anrede oder Titel (Frau, Herr, Dr., Prof.), häufige Vor- und Nachnamen und – mit den Schaltern unten – Namen " +
                "nach Rollen, in Grußformeln und Tabellen. Ohne diesen Schalter nur die Angaben mit festem Muster und die eigene Liste."),
            s.pseudonymAuto,
        ) { v -> c.settings.update { it.copy(pseudonymAuto = v) } }
        ToggleRow(
            tr("Öffentliche Quellen nicht ersetzen"),
            tr("Ergebnisse der Web- und Literatursuche, Bücher und Paper (erkennbar an DOI oder ISBN), die Autor:innen der Literatur-" +
                "sammlung und im Text zitierte Autor:innen („Müller (2020)“) bleiben, wie sie sind – sonst entstehen falsche Antworten. " +
                "Anhänge lassen sich am Anhang selbst als öffentlich oder vertraulich markieren. Die eigene Liste geht immer vor."),
            s.pseudonymPublic,
        ) { v -> c.settings.update { it.copy(pseudonymPublic = v) } }
        val local = c.localLlm.installedModels().isNotEmpty()
        ToggleRow(
            tr("Gründlich: zusätzlich ein lokales Sprachmodell"),
            tr("Schneller ↔ gründlicher: Ein Sprachmodell auf diesem Gerät liest jeden Text einmal und findet Namen, Schulen und Orte, " +
                "die Regeln und Lora Namensschutz übersehen, auch indirekte Hinweise. Kostet Rechenzeit (einige Sekunden je längerem " +
                "Text), nichts verlässt dabei das Gerät.") +
                if (local) "" else tr(" Dafür unter Einstellungen › Modelle auf diesem Gerät ein Modell laden."),
            s.pseudonymThorough,
        ) { v -> c.settings.update { it.copy(pseudonymThorough = v) } }
        if (!s.pseudonymize) SettingsHint(tr("Die Pseudonymisierung ist aus – die Schalter unten wirken erst, wenn sie an ist."))
    }

    // Postfach nur mit lokalem Modell (AP-68): a fixed rule, no switch.
    SettingsCard(tr("Postfach")) {
        SettingsHint(
            tr("Mails, Ordner, Termine und Aufgaben (klassisches Outlook und Mailserver) liest nur ein Modell auf diesem Gerät. Der " +
                "Uni-Server und externe Anbieter sind im Postfach fest gesperrt – dafür gibt es keinen Schalter. Ergebnisse des lokalen " +
                "Modells gehen nur über „An Chat übergeben“ weiter: erst als Karte zum Prüfen, pseudonymisiert, ohne Mails, Anhänge " +
                "und Mail-Kennungen. Aus Postfach-Chats lernt das Gedächtnis nichts."),
        )
    }

    PiiFeature.entries.groupBy { it.group }.forEach { (group, features) ->
        SettingsCard(group) {
            features.forEach { f -> ToggleRow(f.title, f.detail, f !in off) { v -> setFeature(f, v) } }
        }
    }

    SettingsCard(tr("Eigene Namen und Begriffe")) {
        OutlinedTextField(
            value = s.pseudonymTerms,
            onValueChange = { v -> c.settings.update { it.copy(pseudonymTerms = v) } },
            label = { Text(tr("Werden immer ersetzt")) },
            placeholder = { Text(tr("Je Zeile einer, z.B.\nJonte Vogelsang\nGymnasium Dresden-Plauen")) },
            supportingText = {
                Text(
                    tr("Klassenlisten, Studierende, Schulen. Bei Personen erkennt die App auch Vor- und Nachnamen einzeln, gebeugt und in " +
                        "anderer Schreibweise. Nur mit dieser Liste ist die Erkennung von Namen vollständig zuverlässig.")
                )
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = import) { Text(tr("Aus Datei übernehmen …")) }
            OutlinedButton(onClick = { export("Namensliste Pseudonymisierung.txt") }, enabled = s.pseudonymTerms.isNotBlank()) { Text(tr("Liste speichern …")) }
        }
        SettingsHint(tr("Übernehmen liest Klassen- und Teilnehmerlisten (Excel, CSV, Word, Text): Spalten Vorname/Nachname oder Name, sonst ein Name je Zeile. Die gespeicherte Liste lässt sich an Kolleg/innen weitergeben."))
        Spot("privacy.never") { OutlinedTextField(
            value = s.pseudonymIgnore,
            onValueChange = { v -> c.settings.update { it.copy(pseudonymIgnore = v) } },
            label = { Text(tr("Nie ersetzen")) },
            placeholder = { Text(tr("Je Zeile eines, z.B.\nFrank\nMartin-Luther-Gymnasium")) },
            supportingText = { Text(tr("Wörter, die wie Namen aussehen, aber keine sind. Die eigene Liste oben geht vor.")) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        ) }
    }

    SettingsCard(tr("Ausprobieren")) {
        SettingsHint(tr("So sähe der Server diesen Text. Nichts wird gesendet oder gespeichert."))
        OutlinedTextField(value = sample, onValueChange = { sample = it }, label = { Text(tr("Beispieltext")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Text(
                remember(sample, s) { c.privacy.preview(sample) },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(12.dp),
            )
        }
        // One click corrects the recognition; it holds for all future chats (AP-03).
        PseudonymCorrections(c, listOf(sample))
    }

    SettingsCard(tr("Zuordnungen")) {
        val entries = remember(version, s) { c.privacy.all().filter { !it.alias } }
        val chats = remember(version, s) { c.privacy.chats() }
        SettingsHint(
            tr("{0} Zuordnung(en) auf diesem Gerät", entries.size) + (if (chats > 0) tr(", dazu eigene Platzhalter in {0} Chat(s)", chats) else "") +
                tr(". Sie bleiben nur hier, damit dieselbe Person denselben Platzhalter behält.")
        )
        if (entries.isNotEmpty()) {
            OutlinedTextField(value = filter, onValueChange = { filter = it }, label = { Text(tr("Suchen")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                            Notices.info(tr("„{0}“ wird nicht mehr ersetzt.", e.value))
                        }) { Icon(Icons.Outlined.Block, contentDescription = tr("Nie ersetzen")) }
                        IconButton(onClick = { c.privacy.remove(e.value); version++ }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Zuordnung löschen")) }
                    }
                }
                if (shown.size > 12 && !showAll) TextButton(onClick = { showAll = true }) { Text(tr("Alle {0} zeigen", shown.size)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Alle Zuordnungen"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty() || chats > 0) { Text(tr("Löschen")) }
        }
    }

    SettingsCard(tr("Gut zu wissen")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsHint(tr("• Bilder, Scans und Spracheingaben lassen sich nicht pseudonymisieren – mit „Warnung bei Bildern“ fragt die App vor dem Senden nach. Vorlesen mit der KI-Stimme schickt den Antworttext an den Uni-Server."))
            SettingsHint(tr("• Ohne Pseudonymisierung warnt die App, wenn eine Nachricht mit Namen oder Kontaktdaten an einen Anbieter außerhalb der TU Dresden (z.B. Google Gemini) ginge."))
            SettingsHint(tr("• Was ersetzt wurde und wie der Server die Nachricht sah, zeigt das Agent-Protokoll unter der Antwort (Verlaufssymbol)."))
            SettingsHint(tr("• Modelle auf diesem Gerät bekommen die echten Angaben – dort verlässt nichts das Gerät."))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            tr("Zuordnungen löschen?"),
            tr("Die App vergisst, welcher Platzhalter zu welcher Angabe gehört. Platzhalter in älteren Chats und Protokollen lassen sich " +
                "danach nicht mehr zurückübersetzen; laufende Antworten können Platzhalter statt Namen enthalten."),
            tr("Löschen"),
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
        Text(tr("Name?"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        suspects.forEach { w ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        c.settings.update { it.copy(pseudonymTerms = NameList.merge(it.pseudonymTerms, listOf(w))) }
                        Notices.info(tr("„{0}“ wird ab jetzt ersetzt.", w))
                    }) { Text(tr("{0} ersetzen", w)) }
                    IconButton(onClick = { c.settings.update { it.copy(pseudonymIgnore = NameList.merge(it.pseudonymIgnore, listOf(w))) } }) {
                        Icon(Icons.Outlined.Close, contentDescription = tr("Kein Name"))
                    }
                }
            }
        }
    }
}


/**
 * Ein-Klick-Korrektur (AP-03): the details replaced in [texts] with „nicht ersetzen“, and words that might be names but
 * stayed with „auch ersetzen“. The choice goes to the lists under Einstellungen › Datenschutz and holds for every
 * future chat. Shown in the preview of the settings and in the agent protocol.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PseudonymCorrections(c: AppContainer, texts: List<String>, scope: String? = null) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!s.pseudonymize) return
    var version by remember { mutableStateOf(0) }
    val joined = texts.joinToString("\n\n")
    val replaced = remember(joined, s, version) { runCatching { c.privacy.replacements(joined, scope) }.getOrDefault(emptyList()) }
    val missed = remember(joined, s, version) { runCatching { c.privacy.missed(joined) }.getOrDefault(emptyList()).take(8) }
    if (replaced.isEmpty() && missed.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (replaced.isNotEmpty()) {
            Text(tr("Ersetzt – antippen, wenn es kein privater Name ist:"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                replaced.take(24).forEach { (value, placeholder) ->
                    AssistChip(
                        onClick = {
                            c.privacy.neverReplace(value) { t -> c.settings.update(t) }
                            version++
                            Notices.info("„$value“ " + tr("wird nicht mehr ersetzt."))
                        },
                        label = { Text("$value → $placeholder · " + tr("nicht ersetzen"), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
        if (missed.isNotEmpty()) {
            Text(tr("Nicht ersetzt – vielleicht ein Name?"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                missed.forEach { w ->
                    AssistChip(
                        onClick = {
                            c.privacy.alwaysReplace(w) { t -> c.settings.update(t) }
                            version++
                            Notices.info("„$w“ " + tr("wird ab jetzt ersetzt."))
                        },
                        label = { Text("$w · " + tr("auch ersetzen"), maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
}
