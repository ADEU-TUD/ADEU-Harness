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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.PiiModelInfo
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.NameList
import io.github.thepytales.llmhub.data.PiiFeature

/**
 * Einstellungen › Datenschutz (AP-103): one calm page. On top the status card (protection on/off, what is active, the
 * fixed rules), then *Erkennung*, *Meine Listen* and *Ausprobieren*, the single switches under *Feineinstellungen*,
 * the hints before sending and *So schützt Lora deine Daten*. Everything but the status card and *Erkennung* is
 * folded; which part is open is kept in [AppSettings.privacyOpen]. With the protection off the parts are greyed out.
 */
@Composable
fun PrivacySection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val model by c.piiModel.state.collectAsStateWithLifecycle()
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
    val dim = if (s.pseudonymize) Modifier else Modifier.alpha(PrivacyPage.DIMMED)

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

    // ── Status: is the protection on, what is active, the fixed rules ──
    SettingsCard(tr("Pseudonymisierung")) {
        Spot("privacy.pseudonymize") { ToggleRow(
            tr("Personenbezogene Daten ersetzen"),
            if (s.pseudonymize) tr("An – der Server sieht nur Platzhalter wie [Name 1].")
            else tr("Aus – Namen, Kontaktdaten und Nummern gehen unverändert an den Server."),
            s.pseudonymize,
        ) { v -> c.settings.update { it.copy(pseudonymize = v) } } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = if (s.pseudonymize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(PrivacyPage.status(s, model.installed), style = MaterialTheme.typography.bodyMedium)
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RuleMark(Icons.Outlined.Lock, tr("Postfach nur auf diesem Gerät"))
            RuleMark(Icons.Outlined.Shield, tr("Tresor für sensible Forschung"))
        }
    }

    // ── Erkennung: what is found ──
    Box(dim) { SettingsCard(tr("Erkennung")) {
        PiiModelSettings(c)
        ToggleRow(
            tr("Namen automatisch erkennen"),
            tr("Nach Anrede, Titel, Rolle und Grußformel, in Tabellen und häufige Vor- und Nachnamen. Aus: nur feste Muster und die eigene Liste."),
            s.pseudonymAuto,
        ) { v -> c.settings.update { it.copy(pseudonymAuto = v) } }
        ToggleRow(
            tr("Öffentliche Quellen nicht ersetzen"),
            tr("Web- und Literatursuche, Paper mit DOI oder ISBN und zitierte Autor:innen („Müller (2020)“) bleiben, wie sie sind."),
            s.pseudonymPublic,
        ) { v -> c.settings.update { it.copy(pseudonymPublic = v) } }
        val local = c.localLlm.installedModels().isNotEmpty()
        ToggleRow(
            tr("Gründlich: zusätzlich ein lokales Sprachmodell"),
            tr("Findet auch indirekte Hinweise; einige Sekunden je längerem Text, nichts verlässt das Gerät.") +
                if (local) "" else tr(" Braucht ein Modell auf diesem Gerät."),
            s.pseudonymThorough,
        ) { v -> c.settings.update { it.copy(pseudonymThorough = v) } }
    } }

    // ── Meine Listen: always / never replace, import and export, the stored pairs ──
    val entries = remember(version, s) { c.privacy.all().filter { !it.alias } }
    val chats = remember(version, s) { c.privacy.chats() }
    PrivacyFold(
        c, PrivacyPage.LISTS, tr("Meine Listen"),
        tr("{0} immer ersetzen · {1} nie ersetzen · {2} Zuordnungen", PrivacyPage.count(s.pseudonymTerms), PrivacyPage.count(s.pseudonymIgnore), entries.size),
        dim,
    ) {
        OutlinedTextField(
            value = s.pseudonymTerms,
            onValueChange = { v -> c.settings.update { it.copy(pseudonymTerms = v) } },
            label = { Text(tr("Werden immer ersetzt")) },
            placeholder = { Text(tr("Je Zeile einer, z.B.\nJonte Vogelsang\nGymnasium Dresden-Plauen")) },
            supportingText = { Text(tr("Klassenlisten, Studierende, Schulen – auch Vor- und Nachnamen einzeln, gebeugt und anders geschrieben.")) },
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
        Text(tr("Zuordnungen"), style = MaterialTheme.typography.titleSmall)
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

    // ── Ausprobieren ──
    PrivacyFold(c, PrivacyPage.TRY, tr("Ausprobieren"), tr("So sähe der Server einen Text – nichts wird gesendet oder gespeichert."), dim) {
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

    // ── Feineinstellungen: the single switches, one line per group; the hints before sending as a small part of their own ──
    PrivacyFold(c, PrivacyPage.DETAILS, tr("Feineinstellungen"), tr("{0} von {1} Einzelschaltern an", PiiFeature.entries.count { it !in off }, PiiFeature.entries.size), dim) {
        PiiFeature.entries.filter { it !in PrivacyPage.HINTS }.groupBy { it.group }.forEach { (group, features) ->
            PrivacyGroup(c, group, features, off) { f, v -> setFeature(f, v) }
        }
        // Not part of the replacing, but of the same protection: they warn before something is sent.
        PrivacyGroup(c, PrivacyPage.SENDING_GROUP, PrivacyPage.HINTS, off) { f, v -> setFeature(f, v) }
    }

    // ── So schützt Lora deine Daten ──
    PrivacyFold(c, PrivacyPage.ABOUT, tr("So schützt Lora deine Daten"), tr("Postfach, Bilder, lokale Modelle und das Agent-Protokoll in Kürze.")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsHint(tr("• Postfach: Mails, Termine und Aufgaben liest nur ein Modell auf diesem Gerät – Uni-Server und externe Anbieter sind dort fest gesperrt. Weiter geht nur, was du mit „An Chat übergeben“ prüfst, pseudonymisiert und ohne Mails und Anhänge."))
            SettingsHint(tr("• Bilder, Scans und Spracheingaben lassen sich nicht pseudonymisieren – mit „Warnung bei Bildern“ fragt die App vor dem Senden nach. Vorlesen mit der KI-Stimme schickt den Antworttext an den Uni-Server."))
            SettingsHint(tr("• Ohne Pseudonymisierung warnt die App, wenn eine Nachricht mit Namen oder Kontaktdaten an einen Anbieter außerhalb der TU Dresden (z.B. Google Gemini) ginge."))
            SettingsHint(tr("• Modelle auf diesem Gerät bekommen die echten Angaben – dort verlässt nichts das Gerät."))
            SettingsHint(tr("• Was ersetzt wurde und wie der Server die Nachricht sah, zeigt das Agent-Protokoll unter der Antwort (Verlaufssymbol)."))
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

/** What the page shows and keeps (AP-103), apart from the composables so tests can check it. */
internal object PrivacyPage {
    const val LISTS = "lists"
    const val TRY = "try"
    const val DETAILS = "details"
    /** The group of [HINTS] in Feineinstellungen. */
    val SENDING_GROUP = trLater("Hinweise beim Senden")
    const val ABOUT = "about"
    const val DIMMED = 0.45f

    /** Switches that warn before sending rather than replace anything – their own small part of the page. */
    val HINTS = listOf(PiiFeature.IMAGE_WARNING, PiiFeature.SUGGESTIONS)

    /** Names of the groups of [PiiFeature] (they are shown with tr). */
    val GROUPS = listOf(trLater("Kontaktdaten"), trLater("Nummern und Daten"), trLater("Namen"), trLater("Platzhalter"), trLater("Hinweise"))

    /** Spots inside a folded part: Lora's frame (AP-41) opens the part first. */
    val SPOT_FOLDS = mapOf("privacy.never" to LISTS)

    fun open(s: AppSettings): Set<String> = s.privacyOpen.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun toggle(s: AppSettings, key: String): AppSettings {
        val now = open(s)
        return s.copy(privacyOpen = (if (key in now) now - key else now + key).sorted().joinToString(","))
    }

    /** Lines of a list that are not empty. */
    fun count(list: String) = list.lines().count { it.isNotBlank() && !it.trimStart().startsWith("#") }

    /** One line under the main switch: what is active right now („Regeln · Lora Namensschutz · Gründlich aus“). */
    fun status(s: AppSettings, modelInstalled: Boolean): String {
        if (!s.pseudonymize) return tr("Schutz aus – nichts wird ersetzt.")
        val parts = buildList {
            add(tr("Regeln"))
            if (s.pseudonymAuto) add(tr("Namen automatisch"))
            if (s.pseudonymModel) add(if (modelInstalled) PiiModelInfo.NAME else tr("{0} nicht geladen", PiiModelInfo.NAME))
            if (s.pseudonymPublic) add(tr("öffentliche Quellen bleiben"))
            add(if (s.pseudonymThorough) tr("Gründlich an") else tr("Gründlich aus"))
        }
        return tr("Aktiv: {0}", parts.joinToString(" · "))
    }
}

/** A part of the privacy page that opens on a click; Lora's frame on a spot inside opens it too. */
@Composable
private fun PrivacyFold(c: AppContainer, key: String, title: String, summary: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val target by Spotlight.target.collectAsStateWithLifecycle()
    val open = key in PrivacyPage.open(s) || PrivacyPage.SPOT_FOLDS[target] == key
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LocalChatLook.current.card)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) tr("Zuklappen") else tr("Aufklappen")) {
                    c.settings.update { PrivacyPage.toggle(it, key) }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) tr("Zuklappen") else tr("Aufklappen"))
            }
            if (open) content()
        }
    }
}

/** One group of the single switches: a line „Kontaktdaten · 4 von 4 an“ that opens its switches. */
@Composable
private fun PrivacyGroup(c: AppContainer, group: String, features: List<PiiFeature>, off: Set<PiiFeature>, onChange: (PiiFeature, Boolean) -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val key = "group:$group"
    val open = key in PrivacyPage.open(s)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) tr("Zuklappen") else tr("Aufklappen")) {
                c.settings.update { PrivacyPage.toggle(it, key) }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(group), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(tr("{0} von {1} an", features.count { it !in off }, features.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) tr("Zuklappen") else tr("Aufklappen"))
        }
        if (open) features.forEach { f -> ToggleRow(tr(f.title), tr(f.detail), f !in off) { v -> onChange(f, v) } }
    }
}

/** A fixed rule as a small mark in the status card. */
@Composable
private fun RuleMark(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
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
