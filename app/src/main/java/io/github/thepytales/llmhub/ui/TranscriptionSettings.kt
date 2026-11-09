package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.transcribe.SpeechHost
import io.github.thepytales.llmhub.transcribe.SpeechModel
import io.github.thepytales.llmhub.transcribe.TranscriptionMode
import kotlinx.coroutines.launch

/** Einstellungen › Transkription: where by default, recognition models, defaults, replacement list, storage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptionSettings(c: AppContainer) {
    val t = c.transcription
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by t.settings.collectAsStateWithLifecycle()
    val state by t.state.collectAsStateWithLifecycle()
    val available by t.available.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf<SpeechModel?>(null) }
    LaunchedEffect(Unit) { t.checkAvailable() }
    LaunchedEffect(Unit) { t.messages.collect { Notices.info(it) } }

    val importModel = rememberMediaImport(anyFile = true) { name, _, input ->
        runCatching { t.importModelFile(name, input) }
            .onSuccess { Notices.info(it) }
            .onFailure { Notices.error(it.message ?: tr("Fehler")) }
    }

    GroupLabel(tr("Standard"))
    val mode = enumValues<TranscriptionMode>().firstOrNull { it.name == s.mode } ?: TranscriptionMode.LOCAL
    Segmented(TranscriptionMode.entries.map { it to it.label }, mode, { m -> t.updateSettings { it.copy(mode = m.name) } }, enabled = { it == TranscriptionMode.LOCAL || c.settings.current.hasApiKey })
    HintText(mode.description + tr(" Projekte mit der Datenschutzstufe „Nur auf diesem Gerät“ transkribieren immer hier."))

    GroupLabel(tr("Erkennungsmodelle"))
    if (available == false) HintText(t.unavailableReason ?: tr("Auf diesem Gerät nicht verfügbar."))
    AppCard {
        Column {
            state.models.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ModelRow(m, recommended = m.id == "parakeet", onDownload = { t.download(m.id) }, onCancel = { t.cancelDownload(m.id) }, onDelete = { t.deleteModel(m.id) }, onManual = { manual = m })
            }
            if (state.models.isEmpty()) HintText(tr("Die Modelle erscheinen, sobald die Spracherkennung gestartet ist."), Modifier.padding(16.dp))
        }
    }
    HintText(
        tr("Modelle werden einmalig geladen (am besten im WLAN) und laufen danach komplett offline. Für die Sprechererkennung " +
            "kommt einmalig ein kleines Zusatzmodell (26 MB) dazu. Parakeet ist auf Rechnern ohne Grafikkarte am schnellsten, " +
            "Whisper Turbo erkennt am genauesten."),
    )
    OutlinedButton(onClick = importModel) { Text(tr("Modelldateien einspielen (auch ZIP)")) }

    GroupLabel(tr("Voreinstellungen"))
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceField(tr("Sprache"), SPEECH_LANGUAGES, s.language, { v -> t.updateSettings { it.copy(language = v) } })
            Text(tr("Sprecher erkennen"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SPEAKER_OPTIONS.forEach { (v, l) -> FilterChip(selected = s.speakers == v, onClick = { t.updateSettings { it.copy(speakers = v) } }, label = { Text(l) }) }
            }
            ChoiceField(
                tr("Empfindlichkeit der Sprecher-Automatik"),
                listOf("fewer" to tr("Weniger Sprecher"), "normal" to tr("Normal"), "more" to tr("Mehr Sprecher")),
                s.sensitivity, { v -> t.updateSettings { it.copy(sensitivity = v) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(tr("Live-Text bei Aufnahmen"), tr("Schon während der Aufnahme transkribieren (auf dem Gerät) – nach dem Stoppen ist der Text fast sofort fertig."), Modifier.weight(1f))
                Switch(s.live, { v -> t.updateSettings { it.copy(live = v) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(
                    tr("Wortzeiten messen"),
                    tr("Nach Whisper oder dem Uni-Server misst Parakeet v3 auf dem Gerät, wann jedes Wort gesprochen wird (im Median 50 ms genau, " +
                        "Whisper selbst liegt öfter 0,25 s und mehr daneben). Grundlage für Wortmarkierung, Zeitstempel je Satz und Untertitel.") +
                        if (state.models.none { it.id == "parakeet" && it.installed }) tr(" Dafür oben Parakeet v3 laden.") else "",
                    Modifier.weight(1f),
                )
                Switch(s.measureWords, { v -> t.updateSettings { it.copy(measureWords = v) } })
            }
        }
    }

    GroupLabel(if (SpeechHost.IS_PC) tr("Leistung & Mikrofon") else tr("Leistung & Mikrofon"))
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val cores = Runtime.getRuntime().availableProcessors()
            ChoiceField(
                tr("Rechenkerne"),
                listOf(0 to tr("Automatisch ({0})", state.threads.takeIf { it > 0 } ?: "–")) + (1..cores.coerceAtMost(16)).map { it to it.toString() },
                s.threads, { v -> t.updateSettings { it.copy(threads = v) } },
            )
            if (SpeechHost.IS_PC) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleBlock(tr("Schonend rechnen"), tr("Niedrige Priorität, damit Office und Browser flüssig bleiben."), Modifier.weight(1f))
                    Switch(s.gentle, { v -> t.updateSettings { it.copy(gentle = v) } })
                }
                val mics = remember { SpeechHost.microphones() }
                ChoiceField(tr("Mikrofon"), listOf("" to tr("Standard (Windows)")) + mics.map { it to it }, s.micDevice, { v -> t.updateSettings { it.copy(micDevice = v) } })
                HintText(tr("Kein Mikrofon gefunden? Windows-Einstellungen › Datenschutz › Mikrofon › „Desktop-Apps den Zugriff erlauben“."))
            } else {
                ChoiceField(
                    tr("Mikrofon-Modus"),
                    listOf("speech" to tr("Für Spracherkennung (empfohlen)"), "standard" to tr("Standard")),
                    s.micMode, { v -> t.updateSettings { it.copy(micMode = v) } },
                )
            }
        }
    }

    GroupLabel(tr("Ersetzungsliste"))
    ReplacementList(c)

    GroupLabel(tr("Speicher"))
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Transkripte und Aufnahmen: {0} · Modelle: {1}", formatBytes(state.storage), formatBytes(state.modelsSize)), style = MaterialTheme.typography.bodyMedium)
            if (state.modelsDir.isNotBlank()) HintText(state.modelsDir)
            if (state.calibrated) TextButton(onClick = { t.recalibrate() }) { Text(tr("Sprecher-Automatik neu einrichten")) }
        }
    }

    manual?.let { m -> ManualDownloadDialog(m, onImport = { manual = null; importModel() }, onDismiss = { manual = null }) }
}

@Composable
private fun ModelRow(m: SpeechModel, recommended: Boolean, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    if (recommended) StatusPill(tr("Empfohlen"))
                    if (m.installed) StatusPill(tr("bereit"), PillTone.SUCCESS)
                }
                HintText(
                    m.description + " · " + formatBytes(m.size) +
                        (m.speed?.let { tr(" · hier ca. {0} min pro Stunde Audio", (it * 60).toInt().coerceAtLeast(1)) } ?: ""),
                )
            }
            when {
                m.installed -> IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), tint = MaterialTheme.colorScheme.error) }
                m.downloading != null -> TextButton(onClick = onCancel) { Text(tr("Stopp")) }
                else -> OutlinedButton(onClick = onDownload) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Laden"))
                }
            }
        }
        m.downloading?.let { p ->
            LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
            HintText("${(p * 100).toInt()} %")
        }
        if (!m.installed && m.downloading == null) {
            Text(
                tr("Download gesperrt? Dateien selbst laden und einspielen"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onManual),
            )
        }
    }
}

/** For company networks: the file addresses to load in a browser (or on another PC) and import afterwards. */
@Composable
private fun ManualDownloadDialog(m: SpeechModel, onImport: () -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("{0} von Hand laden", m.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText(tr("Die Dateien im Browser (oder an einem anderen Rechner) herunterladen und dann mit „Einspielen“ übernehmen. Jede Datei wird mit ihrer Prüfsumme kontrolliert."))
                m.files.forEach { (name, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text(tr("Link kopieren")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onImport) { Text(tr("Einspielen …")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** Names and technical terms that are always recognized wrongly; applied to new transcripts automatically. */
@Composable
private fun ReplacementList(c: AppContainer) {
    val t = c.transcription
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(t.replacements()) } }
    fun persist() = t.saveReplacements(rows.toList())
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HintText(tr("Namen und Fachbegriffe, die immer gleich falsch erkannt werden (z. B. „Maier“ → „Meyer“), werden in neuen Transkripten automatisch korrigiert."))
            rows.forEachIndexed { i, (from, to) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(from, { v -> rows[i] = v to rows[i].second; persist() }, label = { Text(tr("Erkannt")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { v -> rows[i] = rows[i].first to v; persist() }, label = { Text(tr("Richtig")) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rows.removeAt(i); persist() }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Entfernen")) }
                }
            }
            Row {
                TextButton(onClick = { rows.add("" to "") }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Eintrag"))
                }
                Spacer(Modifier.weight(1f))
                if (rows.any { it.first.isNotBlank() }) TextButton(onClick = {
                    scope.launch {
                        val n = t.applyReplacementsToAll()
                        Notices.info(if (n == 0) tr("Keine Änderungen nötig.") else tr("{0} Transkript(e) korrigiert.", n))
                    }
                }) { Text(tr("Auf vorhandene anwenden")) }
            }
        }
    }
}
