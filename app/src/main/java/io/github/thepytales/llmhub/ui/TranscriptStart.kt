package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.transcribe.IncomingFile
import io.github.thepytales.llmhub.transcribe.StartOptions
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptionMode

/** Languages offered for Whisper (Parakeet recognizes the language itself). */
val SPEECH_LANGUAGES = listOf(
    "de" to trLater("Deutsch"), "en" to trLater("Englisch"), "auto" to trLater("Automatisch erkennen"), "fr" to trLater("Französisch"), "es" to trLater("Spanisch"),
    "it" to trLater("Italienisch"), "pl" to trLater("Polnisch"), "cs" to trLater("Tschechisch"), "nl" to trLater("Niederländisch"), "ru" to trLater("Russisch"),
    "uk" to trLater("Ukrainisch"), "tr" to trLater("Türkisch"), "ar" to trLater("Arabisch"), "pt" to trLater("Portugiesisch"),
)

val SPEAKER_OPTIONS = listOf("off" to trLater("Keine"), "auto" to trLater("Automatisch"), "2" to "2", "3" to "3", "4" to "4", "5" to "5", "6" to "6")

/**
 * The choices before a transcription: where (device / Uni server, as far as the project's data protection level
 * allows), model, language, speakers, title and project.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartOptionsForm(c: AppContainer, opts: StartOptions, onChange: (StartOptions) -> Unit, showTitle: Boolean = true) {
    val t = c.transcription
    val state by t.state.collectAsStateWithLifecycle()
    val folders by c.folders.items.collectAsStateWithLifecycle()
    val installed = state.models.filter { it.installed }
    val serverOk = t.serverAllowed(opts.folder)
    val privacy = t.privacy(opts.folder)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) {
            OutlinedTextField(opts.title, { onChange(opts.copy(title = it)) }, label = { Text(tr("Titel (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        ChoiceField(
            tr("Projekt"),
            listOf<Pair<String?, String>>(null to tr("Kein Projekt")) + folders.sortedBy { it.name.lowercase() }.map { it.id to it.name + if (it.privacy != PrivacyLevel.OPEN) " · ${it.privacy.short}" else "" },
            opts.folder,
            { f ->
                val next = opts.copy(folder = f)
                onChange(if (next.mode == TranscriptionMode.SERVER && !t.serverAllowed(f)) next.copy(mode = TranscriptionMode.LOCAL) else next)
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Transkribieren"), style = MaterialTheme.typography.labelLarge)
            Segmented(
                TranscriptionMode.entries.map { it to it.label },
                opts.mode,
                { onChange(opts.copy(mode = it)) },
                enabled = { it == TranscriptionMode.LOCAL || serverOk },
            )
            HintText(
                when {
                    opts.mode == TranscriptionMode.SERVER -> TranscriptionMode.SERVER.description
                    !privacy.allowsUniServer -> tr("Datenschutzstufe des Projekts „{0}“: Die Aufnahme verlässt das Gerät nicht.", privacy.label)
                    !c.settings.current.hasApiKey -> tr("Für den Uni-Server fehlt der API-Key (Einstellungen › API-Zugang).")
                    else -> TranscriptionMode.LOCAL.description
                },
            )
        }
        if (opts.mode == TranscriptionMode.LOCAL) {
            if (installed.isEmpty()) {
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        Text(tr("Noch kein Erkennungsmodell geladen"), style = MaterialTheme.typography.titleSmall)
                        HintText(tr("Unter Einstellungen › Transkription ein Modell laden (einmalig, z. B. Parakeet v3 mit 670 MB)."))
                    }
                }
            } else {
                ChoiceField(
                    tr("Erkennungsmodell"),
                    installed.map { m -> m.id to m.name + (m.speed?.let { s -> tr(" · ca. {0} min pro Stunde", (s * 60).toInt().coerceAtLeast(1)) } ?: "") },
                    opts.model.takeIf { id -> installed.any { it.id == id } } ?: installed.first().id,
                    { onChange(opts.copy(model = it)) },
                )
            }
        }
        val parakeet = opts.mode == TranscriptionMode.LOCAL && opts.model == "parakeet"
        ChoiceField(tr("Sprache"), SPEECH_LANGUAGES, opts.language, { onChange(opts.copy(language = it)) }, enabled = !parakeet)
        if (parakeet) HintText(tr("Parakeet erkennt die Sprache selbst (25 europäische Sprachen)."))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Sprecher erkennen"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SPEAKER_OPTIONS.forEach { (v, l) -> FilterChip(selected = opts.speakers == v, onClick = { onChange(opts.copy(speakers = v)) }, label = { Text(l) }) }
            }
            if (!state.speakerModelReady && opts.speakers != "off") HintText(tr("Beim ersten Mal wird ein kleines Sprechermodell geladen (26 MB)."))
        }
        val canTranslate = opts.mode == TranscriptionMode.LOCAL && installed.firstOrNull { it.id == opts.model }?.canTranslate == true
        if (canTranslate) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(tr("Ins Englische übersetzen"), tr("Whisper übersetzt direkt beim Erkennen."), Modifier.weight(1f))
                Switch(opts.translate, { onChange(opts.copy(translate = it)) })
            }
        }
        // Whisper and the server give no reliable word times: they are measured afterwards with Parakeet
        if (!parakeet && !(canTranslate && opts.translate)) {
            val pkReady = state.models.any { it.id == "parakeet" && it.installed }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(
                    tr("Wortzeiten messen"),
                    if (pkReady) tr("Danach mit Parakeet v3 Wort für Wort messen (im Median 50 ms genau) – für Zeitstempel, Wortmarkierung und Untertitel. Kostet etwas Rechenzeit.")
                    else tr("Braucht Parakeet v3 (670 MB, Einstellungen › Transkription). Ohne gibt es Zeiten nur je Abschnitt."),
                    Modifier.weight(1f),
                )
                Switch(opts.measureWords && pkReady, { onChange(opts.copy(measureWords = it)) }, enabled = pkReady)
            }
        }
    }
}

/** Start dialog for a file that was picked, shared or rescued after a crash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSheet(c: AppContainer, file: IncomingFile, folderId: String?, onDone: () -> Unit) {
    val t = c.transcription
    var opts by remember(file.token) {
        mutableStateOf(t.defaultOptions(folderId).copy(title = file.name.substringBeforeLast('.').takeIf { file.recovered }.orEmpty()))
    }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet, containerColor = LocalChatLook.current.sheet) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (file.recovered) tr("Aufnahme wiederhergestellt") else tr("Datei transkribieren"), style = MaterialTheme.typography.titleLarge)
            HintText(
                file.name + (file.duration?.let { " · " + TranscriptExport.clock(it) } ?: "") + (if (file.size > 0) " · " + formatBytes(file.size) else "") +
                    if (file.recovered) tr("\nDie App wurde während einer Aufnahme beendet. Die Aufnahme ist repariert.") else "",
            )
            StartOptionsForm(c, opts, { opts = it })
            val ready = opts.mode == TranscriptionMode.SERVER || t.state.value.models.any { it.installed }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { t.discardIncoming(file.token); onDone() }) { Text(tr("Verwerfen")) }
                Spacer(Modifier.weight(1f))
                Button(enabled = ready, onClick = {
                    rememberDefaults(c, opts)
                    t.start(file.token, opts)
                    onDone()
                }) { Text(tr("Transkribieren")) }
            }
        }
    }
}

/** The choices of the last start become the defaults (where, model, language, speakers). */
fun rememberDefaults(c: AppContainer, opts: StartOptions) {
    c.transcription.updateSettings {
        it.copy(mode = opts.mode.name, model = opts.model, language = opts.language, speakers = opts.speakers, measureWords = opts.measureWords)
    }
}

/**
 * Full screen while recording: time, level, live text (on the device), pause, marker, stop. Stopping asks how to
 * transcribe ([StartOptionsForm]) or discards the recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(c: AppContainer, folderId: String?, onMinimize: () -> Unit) {
    val t = c.transcription
    val state by t.state.collectAsStateWithLifecycle()
    val rec = state.recording
    var stopping by remember { mutableStateOf(false) }
    var opts by remember { mutableStateOf(t.defaultOptions(folderId)) }
    val listState = rememberLazyListState()
    LaunchedEffect(rec.liveText.size) { if (rec.liveText.isNotEmpty()) listState.animateScrollToItem(rec.liveText.size - 1) }
    val level by animateFloatAsState(if (rec.paused) 0f else rec.level.coerceIn(0f, 1f), label = "level")
    val record = Color(0xFFE5484D)
    // Back keeps recording in the background instead of leaving the transcription.
    PlatformBackHandler(onBack = onMinimize)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMinimize) { Text(tr("Minimieren")) }
                Spacer(Modifier.weight(1f))
                Text(tr("Aufnahme"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(96.dp))
            }
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(50), color = (if (rec.paused) MaterialTheme.colorScheme.outline else record).copy(alpha = 0.12f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (rec.paused) MaterialTheme.colorScheme.outline else record))
                    Spacer(Modifier.width(7.dp))
                    Text(if (rec.paused) tr("Pausiert") else tr("Aufnahme läuft"), color = if (rec.paused) MaterialTheme.colorScheme.outline else record, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                TranscriptExport.clock(rec.seconds),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 64.sp),
                modifier = Modifier.padding(top = 12.dp),
            )
            // Level: a bar that follows the loudness.
            Box(Modifier.padding(top = 8.dp).width(220.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth((0.04f + level * 0.96f).coerceAtMost(1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
            }
            if (rec.marks.isNotEmpty()) HintText(tr("{0} Markierung(en) · zuletzt „{1}“", rec.marks.size, rec.marks.last().label), Modifier.padding(top = 10.dp))
            Box(Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 20.dp, vertical = 16.dp)) {
                if (rec.liveText.isEmpty()) {
                    HintText(
                        if (rec.liveModel != null) tr("Der Text erscheint hier schon während der Aufnahme ({0}).", rec.liveModel)
                        else tr("Nach dem Stoppen wird transkribiert."),
                        Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rec.liveText) { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            Row(Modifier.padding(bottom = 28.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Outlined.BookmarkAdd, tr("Markierung")) { t.addMark() }
                Surface(
                    onClick = { stopping = true },
                    shape = CircleShape,
                    color = record,
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(76.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Stop, contentDescription = tr("Stopp"), modifier = Modifier.size(34.dp)) } }
                RoundAction(if (rec.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (rec.paused) tr("Weiter") else tr("Pause")) { t.pauseRecording(!rec.paused) }
            }
        }
    }

    if (stopping) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { stopping = false }, sheetState = sheet, containerColor = LocalChatLook.current.sheet) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(tr("Aufnahme beenden"), style = MaterialTheme.typography.titleLarge)
                HintText(tr("Dauer {0}", TranscriptExport.clock(rec.seconds)) + if (rec.liveText.isNotEmpty()) tr(" · der Text ist schon fast fertig") else "")
                StartOptionsForm(c, opts, { opts = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { t.stopRecording(false, opts); stopping = false; onMinimize() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("Verwerfen"))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        rememberDefaults(c, opts)
                        t.stopRecording(true, opts)
                        stopping = false
                        onMinimize()
                    }) { Text(tr("Transkribieren")) }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = LocalChatLook.current.card, border = LocalChatLook.current.cardBorder, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
