package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Area
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.CommandModelInfo
import io.github.thepytales.llmhub.data.PiiModelInfo
import io.github.thepytales.llmhub.data.Places
import io.github.thepytales.llmhub.data.Places.SetupStep
import io.github.thepytales.llmhub.local.DeviceBench
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalLlm
import io.github.thepytales.llmhub.local.LocalModel
import io.github.thepytales.llmhub.local.Purpose
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * „Alles lokal“ einrichten (AP-112): a setup that takes one by the hand – measure the device (AP-105), choose the
 * purpose (AP-80/AP-106), one model per step with size, speed and purpose, loading with progress, a short try
 * („Frag Lora etwas“), on to the next area. *Später* keeps the step ([AppSettings.localSetupStep]) and the setup
 * continues there; at the end an overview of what now runs here, and the profile „Alles auf diesem Gerät“.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSetupScreen(c: AppContainer, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val s by c.settings.settings.collectAsState()
    placesTick(c)
    val steps = SetupStep.entries
    val index = (s.localSetupStep - 1).coerceIn(0, steps.size - 1)
    val step = steps[index]
    fun go(i: Int) = c.settings.update { it.copy(localSetupStep = i.coerceIn(0, steps.size - 1) + 1) }
    fun later() { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(::later) },
                title = { Text(tr("Alles lokal einrichten")) },
                actions = { TextButton(onClick = ::later) { Text(tr("Später")) } },
            )
        },
    ) { pad ->
        ReadableColumn(pad) {
            Section(tr("Schritt {0} von {1}: {2}", index + 1, steps.size, tr(step.title))) {
                LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
                when (step) {
                    SetupStep.DEVICE -> DeviceStep(c)
                    SetupStep.PURPOSE -> PurposeStep(c)
                    SetupStep.LANGUAGE -> LanguageStep(c)
                    SetupStep.HELPERS -> HelpersStep(c)
                    SetupStep.SEARCH -> SimpleStep(
                        c, Area.SEARCH,
                        tr("Lora findet in der Wissenssammlung und in Projekten, was zur Frage passt – auch mit anderen Worten. Das Suchmodell multilingual-e5-small (123 MB, MIT) rechnet auf diesem Gerät, etwa 40 ms je Abschnitt."),
                        installed = c.localEmbedding.installed(), progress = c.localEmbedding.state.collectAsState().value.progress,
                        load = c.localEmbedding::install,
                    )
                    SetupStep.SPEECH_IN -> SpeechInStep(c)
                    SetupStep.VOICE -> SimpleStep(
                        c, Area.SPEECH,
                        tr("Eine natürliche deutsche Stimme (Piper „Thorsten“, 64 MB, CC0) liest Antworten vor und spricht im Sprachmodus – ohne Server. Ohne sie liest die Stimme des Systems."),
                        installed = c.localVoice.installed(), progress = c.localVoice.state.collectAsState().value.progress,
                        load = { c.localVoice.install(); c.settings.update { it.copy(ttsEngine = "LOCAL") } },
                    )
                    SetupStep.VISION -> VisionStep(c)
                    SetupStep.DONE -> DoneStep(c)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) TextButton(onClick = { go(index - 1) }) { Text(tr("Zurück")) }
                Spacer(Modifier.weight(1f))
                if (step != SetupStep.DONE) {
                    TextButton(onClick = { go(index + 1) }) { Text(tr("Überspringen")) }
                    Button(onClick = { go(index + 1) }) { Text(tr("Weiter")) }
                } else {
                    Button(onClick = {
                        Places.apply(c, Places.Profile.ALL_LOCAL)
                        c.settings.update { it.copy(localSetupStep = 0) }
                        onNavigate("settings/places")
                    }) { Text(tr("Alles auf diesem Gerät nutzen")) }
                }
            }
            HintText(tr("„Später“ merkt sich diesen Schritt – unter Einstellungen › Wo Lora arbeitet geht es hier weiter. Downloads laufen im Hintergrund weiter."))
        }
    }
}

@Composable
private fun StepText(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Progress(p: Double?, onCancel: (() -> Unit)? = null) {
    if (p == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Wird geladen … {0} %", (p * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            if (onCancel != null) TextButton(onClick = onCancel) { Text(tr("Abbrechen")) }
        }
    }
}

/** Step 1: what this device is, and the Kurzmessung (AP-105). */
@Composable
private fun DeviceStep(c: AppContainer) {
    val hw by c.localLlm.hardware.collectAsState()
    val stored by DeviceBench.stored(c).collectAsState()
    val scope = rememberCoroutineScope()
    var line by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    StepText(tr("Lora schaut sich dieses Gerät an und misst kurz sein Tempo. Danach sagt sie für jedes Modell vorher, wie schnell es hier schreibt und liest – und schlägt nur vor, was hier gut läuft. Nichts verlässt das Gerät."))
    Text(hw?.describe() ?: tr("Gerät wird geprüft …"), style = MaterialTheme.typography.titleSmall)
    hw?.memDetail?.takeIf { it.isNotBlank() }?.let { HintText(it) }
    val measured = hw?.let { DeviceBench.here(c, it).at } ?: 0L
    if (measured > 0) HintText(tr("Gemessen am {0}.", java.text.SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(java.util.Date(measured))))
    else if (stored != null) HintText(tr("Noch nicht gemessen. Ohne Sprachmodell misst Lora erst den Speicher; das Tempo folgt nach dem ersten Modell von selbst."))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy && hw != null, onClick = {
            busy = true
            scope.launch {
                runCatching { DeviceBench.run(c) { line = it } }.onFailure { line = ErrorHelp.explain(it).text }
                busy = false
            }
        }) { Text(if (measured > 0) tr("Neu messen") else tr("Gerät messen (≈ 20 s)")) }
        line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Places.languageMissing(hw)?.let { SettingsHint(tr("Hinweis: {0} Lora Befehle, Namensschutz, Suche und Sprache gehen trotzdem.", it)) }
}

/** Step 2: what Lora is used for – the allrounder is always there, specialists only when needed (AP-106). */
@Composable
private fun PurposeStep(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    val purposes = LocalCatalog.purposes(s.localPurposes, s.roles)
    StepText(tr("Wofür nutzt du Lora vor allem? Der Allrounder ist immer dabei. Für Texte, Code oder das Postfach schlägt Lora einen Spezialisten vor, der nur bei Bedarf geladen wird – ein Wechsel kostet 10 bis 30 Sekunden."))
    PurposeChips(c, purposes)
    purposes.filter { it != Purpose.ALLROUND }.forEach { HintText(tr(it.label) + ": " + tr(it.hint)) }
}

/** Step 3: the language model for Lora, Chat, Postfach and Dokumente – with size, speed and purpose – and a short try. */
@Composable
private fun LanguageStep(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    val hw by c.localLlm.hardware.collectAsState()
    val local by c.localLlm.state.collectAsState()
    val h = hw
    if (h == null) { StepText(tr("Gerät wird geprüft …")); return }
    Places.languageMissing(h)?.let { StepText(it); return }
    val purposes = LocalCatalog.purposes(s.localPurposes, s.roles)
    val picks = remember(h, purposes, local.installed) { LocalCatalog.picks(h, purposes) { DeviceBench.forecast(c, it, h) } }
    StepText(tr("Ein Sprachmodell auf diesem Gerät beantwortet Lora, Chats, das Postfach und die Hilfe in Dokumenten. Lora empfiehlt das stärkste Modell, das hier flüssig schreibt (mindestens 8 Wörter je Sekunde)."))
    picks.forEach { p -> ModelChoice(c, p.model, h, tr(p.purpose.label), p.fastEnough) }
    // Smaller or larger: the other models that fit, for who wants to choose.
    var more by remember { mutableStateOf(false) }
    TextButton(onClick = { more = !more }) { Text(if (more) tr("Weniger anzeigen") else tr("Andere Modelle für dieses Gerät")) }
    if (more) LocalCatalog.models.filter { m -> picks.none { it.model.id == m.id } && LocalCatalog.fit(m, h) != io.github.thepytales.llmhub.local.Fit.TOO_BIG }
        .sortedBy { it.needGb }.forEach { m -> ModelChoice(c, m, h, tr(m.purposes.first().label), null) }
    if (c.localLlm.generalModels().isNotEmpty()) {
        HorizontalDivider()
        TryLora(c)
    }
}

/** One model with size, forecast and purpose, and Laden / progress / „bereit“. */
@Composable
private fun ModelChoice(c: AppContainer, m: LocalModel, hw: io.github.thepytales.llmhub.local.Hardware, purpose: String, fast: Boolean?) {
    val local by c.localLlm.state.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr(m.name), style = MaterialTheme.typography.bodyLarge)
                StatusPill(purpose, if (fast == true) PillTone.SUCCESS else PillTone.NEUTRAL)
            }
            Text(
                String.format(Locale.GERMANY, "%.1f GB · ", m.size / 1e9) + forecastLine(c, m, hw) + if (fast == false) " · " + tr("langsam auf diesem Gerät") else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Progress(local.downloads[m.id]) { c.localLlm.cancelInstall(m.id) }
        }
        when {
            m.id in local.installed -> StatusPill(tr("bereit"), PillTone.SUCCESS)
            local.downloads.containsKey(m.id) -> {}
            else -> OutlinedButton(onClick = { c.localLlm.install(m.id) }) { Text(tr("Laden")) }
        }
    }
    if (local.retry == m.id) local.message?.let { SettingsHint(it) }
}

/** „Frag Lora etwas“: a short question to the model on this device, with the time it took. */
@Composable
private fun TryLora(c: AppContainer) {
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf(tr("Erkläre in zwei Sätzen, was du auf diesem Gerät für mich tun kannst.")) }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Text(tr("Kurz ausprobieren: Frag Lora etwas"), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy && question.isNotBlank(), onClick = {
            busy = true
            answer = ""
            scope.launch {
                val t0 = System.currentTimeMillis()
                val model = c.localLlm.preferredModel()
                answer = if (model == null) tr("Noch kein Sprachmodell geladen.") else runCatching {
                    val r = c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = question)), null, thinking = ThinkingLevel.OFF) { u ->
                        answer = splitThinking(u.content).second
                    }
                    splitThinking(r.content).second.trim() + "\n\n" + tr("({0} auf diesem Gerät)", duration(System.currentTimeMillis() - t0))
                }.getOrElse { e -> if (e is kotlinx.coroutines.CancellationException) throw e; ErrorHelp.explain(e).text }
                busy = false
            }
        }) { Text(if (busy) tr("Lora denkt …") else tr("Fragen")) }
        if (busy) TextButton(onClick = { c.llm.abortLocal() }) { Text(tr("Stopp")) }
    }
    if (answer.isNotBlank()) Text(answer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 640.dp))
}

/** Step 4: the two small helper models – commands and the recognition of names. */
@Composable
private fun HelpersStep(c: AppContainer) {
    val cmd by c.commandModel.state.collectAsState()
    val pii by c.piiModel.state.collectAsState()
    StepText(tr("Zwei kleine Modelle nur für Lora: Lora Befehle erkennt in Millisekunden, was du meinst – einen Bereich öffnen, eine Einstellung umstellen. Lora Namensschutz findet Namen, Schulen und Orte, bevor etwas das Gerät doch verlässt."))
    HelperLine(tr("{0}: Befehle verstehen ({1} MB)", CommandModelInfo.NAME, CommandModelInfo.MEGABYTES), c.commandModel.installed(), cmd.progress, c.commandModel.available) { c.commandModel.install() }
    HelperLine(tr("{0}: Namen schützen ({1} MB)", PiiModelInfo.NAME, PiiModelInfo.MEGABYTES), c.piiModel.installed(), pii.progress, true) { c.piiModel.install() }
}

@Composable
private fun HelperLine(title: String, installed: Boolean, progress: Double?, available: Boolean, load: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Progress(progress)
        }
        when {
            installed -> StatusPill(tr("bereit"), PillTone.SUCCESS)
            progress != null -> {}
            !available -> StatusPill(tr("noch nicht verfügbar"), PillTone.NEUTRAL)
            else -> OutlinedButton(onClick = load) { Text(tr("Laden")) }
        }
    }
}

/** A step with one download: what it does, the label of its area, Laden with progress. */
@Composable
private fun SimpleStep(c: AppContainer, area: Area, text: String, installed: Boolean, progress: Double?, load: () -> Unit) {
    StepText(text)
    PlaceNote(c, area)
    when {
        installed -> StatusPill(tr("bereit"), PillTone.SUCCESS)
        progress != null -> Progress(progress)
        else -> OutlinedButton(onClick = load) { Text(tr("Laden")) }
    }
}

/** Step 6: a recognition model for Diktat, Spracheingabe and Transkription. */
@Composable
private fun SpeechInStep(c: AppContainer) {
    val st by c.transcription.state.collectAsState()
    StepText(tr("Ein Erkennungsmodell macht aus Sprache Text – für Diktat, Spracheingabe im Chat und bei Lora und für Transkriptionen. Kleinere sind schneller, größere genauer."))
    PlaceNote(c, Area.DICTATION)
    val models = st.models.sortedBy { it.size }
    if (models.isEmpty()) HintText(tr("Die Spracherkennung startet … einen Moment."))
    models.forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(m.name + String.format(Locale.GERMANY, " (%d MB)", m.size / 1_000_000), style = MaterialTheme.typography.bodyLarge)
                Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Progress(m.downloading)
            }
            when {
                m.installed -> StatusPill(tr("bereit"), PillTone.SUCCESS)
                m.downloading != null -> {}
                else -> OutlinedButton(onClick = { c.transcription.download(m.id) }) { Text(tr("Laden")) }
            }
        }
    }
}

/** Step 8: the image part of the language model (AP-110) – or why it is not worth it on this device. */
@Composable
private fun VisionStep(c: AppContainer) {
    val local by c.localLlm.state.collectAsState()
    val hw by c.localLlm.hardware.collectAsState()
    StepText(tr("Mit dem Bildteil versteht das Sprachmodell Bilder im Chat, Screenshots, Diagramme und Scans – auch die Texterkennung auf dem Handy läuft dann hier."))
    PlaceNote(c, Area.VISION)
    val h = hw ?: return
    val sec = Places.secondsPerImage(c, h)
    if (sec != null && sec > Places.VISION_TOO_SLOW_S) {
        SettingsHint(tr("Auf diesem Gerät dauert ein Bild etwa {0} s – zu lange für den Alltag. Bilder verstehen bleibt hier besser beim Server (oder ein Gerät mit Grafikkarte bzw. Apple-Chip).", sec.toInt()))
        return
    }
    val models = c.localLlm.generalModels().filter { it.projector != null }
    if (models.isEmpty()) { HintText(tr("Erst ein Sprachmodell laden (Schritt 3) – der Bildteil gehört zu ihm.")); return }
    models.forEach { m ->
        val key = LocalLlm.VISION + m.id
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tr("Bildteil für {0} ({1} MB)", tr(m.name), m.projector!!.size / 1_000_000), style = MaterialTheme.typography.bodyLarge)
                Progress(local.downloads[key]) { c.localLlm.cancelInstall(key) }
            }
            when {
                m.id in local.vision -> StatusPill(tr("bereit"), PillTone.SUCCESS)
                local.downloads.containsKey(key) -> {}
                else -> OutlinedButton(onClick = { c.localLlm.installVision(m.id) }) { Text(tr("Laden")) }
            }
        }
    }
}

/** The end: what now runs on this device, and what does not (with the reason). */
@Composable
private fun DoneStep(c: AppContainer) {
    StepText(tr("Das läuft jetzt auf diesem Gerät. Mit „Alles auf diesem Gerät nutzen“ stellt Lora jeden Bereich, der eine Wahl hat, auf Lokal – ändern lässt sich das jederzeit unter Einstellungen › Wo Lora arbeitet."))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Area.entries.forEach { a ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr(a.title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 160.dp, max = 220.dp))
                PlaceNote(c, a, Modifier.weight(1f))
            }
        }
    }
}
