package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraReply
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.applyLoraStep
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.withTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Lora als Navigator (AP-07), entry points: [open] from anywhere (sleeping Lora in the settings, start page, Strg+K ›
 * „Lora fragen“, long press on Lora in the chat). [LoraHelperHost] at the root of the app shows the conversation.
 */
object LoraHelper {
    /** null: closed; otherwise the wish to start with ("" = just open). */
    val request = MutableStateFlow<String?>(null)

    /** Project of the chat Lora was called from (its privacy level counts). */
    var folderId: String? = null

    fun open(wish: String = "", folderId: String? = null) {
        this.folderId = folderId
        request.value = wish
    }

    /** Where Lora's helper appears: only with Lora „überall“. */
    fun visible(s: io.github.thepytales.llmhub.data.AppSettings) = s.loraMode == "EVERYWHERE" && !s.hideLora
}

/**
 * Hosts Lora's conversation and the two questions of the first start. [go] opens a route: "chat…" routes as a new chat,
 * everything else as a screen.
 */
@Composable
fun LoraHelperHost(c: AppContainer, go: (String) -> Unit) {
    val wish by LoraHelper.request.collectAsStateWithLifecycle()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    LoraMotion.calm = s.loraCalm
    wish?.let { start -> LoraHelperDialog(c, start, go, onDismiss = { LoraHelper.request.value = null }) }
    if (!s.loraIntroDone && s.hasAnyApiKey && wish == null) LoraIntroDialog(c)
}

/** Carries out a step that needs no confirmation; returns false for steps the user confirms first. */
internal fun runLoraStep(step: LoraStep, go: (String) -> Unit): Boolean = when (step) {
    is LoraStep.Navigate -> {
        openTarget(step.target, go)
        true
    }
    is LoraStep.OpenMode -> {
        openTarget(step.target, go, step.text)
        true
    }
    is LoraStep.Handoff -> {
        val text = step.request + if (step.summary.isNotBlank() && step.summary != step.request) "\n\n(Aus dem Gespräch mit Lora: ${step.summary})" else ""
        ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), send = true)
        go(chatRoute(model = step.model))
        true
    }
    else -> false
}

/** Opens a place of the app map: a screen (with the glowing frame), a mode or a TU Vorlage. */
internal fun openTarget(t: AppTarget, go: (String) -> Unit, text: String = "") {
    when {
        t.route.startsWith(MODE_ROUTE) -> {
            ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = t.route.removePrefix(MODE_ROUTE))
            go("chat")
        }
        t.route.startsWith("template:") -> go(chatRoute(persona = t.route.removePrefix("template:")))
        else -> {
            Spotlight.show(t.spot)
            go(t.route)
        }
    }
}

private class LoraTurn(val wish: String, val reply: LoraReply?, var handled: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoraHelperDialog(c: AppContainer, start: String, go: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val agent = remember { LoraAgent(c) }
    val turns = remember { mutableStateListOf<LoraTurn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val recorder = remember { Recorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }

    fun close() {
        if (recording) runCatching { recorder.stop() }
        onDismiss()
    }

    fun send(text: String) {
        val wish = text.trim()
        if (wish.isEmpty() || busy) return
        input = ""
        busy = true
        scope.launch {
            val history = turns.mapNotNull { t -> t.reply?.let { t.wish to it.text } }
            val reply = runCatching { agent.ask(wish, history, LoraHelper.folderId, IS_ANDROID) }
                .getOrElse { e -> if (e is kotlinx.coroutines.CancellationException) throw e else LoraReply("Das hat nicht geklappt: ${e.message}") }
            busy = false
            val turn = LoraTurn(wish, reply)
            turns += turn
            // Navigating, opening and handing over happen right away; settings and tools wait for „Ja“.
            val first = reply.steps.firstOrNull()
            if (first != null && reply.steps.size == 1 && runLoraStep(first, go)) {
                turn.handled = true
                close()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (start.isNotBlank()) send(start) else runCatching { focus.requestFocus() }
    }

    Dialog(onDismissRequest = ::close) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val director = rememberLoraDirector(sequence = listOf(io.github.thepytales.llmhub.ui.LoraAction.WAVE, io.github.thepytales.llmhub.ui.LoraAction.LOOK))
                    LaunchedEffect(busy) { director.play(if (busy) io.github.thepytales.llmhub.ui.LoraAction.SEARCH else io.github.thepytales.llmhub.ui.LoraAction.WAVE) }
                    LoraFigure(Modifier.size(56.dp, 56.dp / LORA_ASPECT * 1.4f), label = tr("Lora"), director = director)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tr("Was möchtest du machen oder einstellen?"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen")) }
                }
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (turns.isEmpty() && !busy) {
                        HintText(tr("Zum Beispiel: „Dunkles Design“, „Ich will ein PDF zusammenfügen“, „Wo stelle ich die Stimme um?“ oder „Erklär mir Exzerpte“."))
                    }
                    turns.forEach { turn ->
                        Text(turn.wish, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.End))
                        turn.reply?.let { reply -> ReplyBlock(c, reply, go, onClose = ::close, onAnswer = { send(it) }) }
                    }
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(tr("Lora sucht …"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(tr("Frag Lora …")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    IconButton(
                        onClick = {
                            if (recording) {
                                recording = false
                                val file = recorder.stop()
                                if (file != null) scope.launch {
                                    busy = true
                                    val text = runCatching { c.llm.transcribe(c.settings.current.sttModel, file) }.getOrNull().orEmpty()
                                    busy = false
                                    if (text.isBlank()) Notices.info(tr("Keine Sprache erkannt.")) else send(text)
                                }
                            } else {
                                runCatching { recorder.start(); recording = true }
                                    .onFailure { Notices.error(tr("Mikrofon nicht verfügbar – im Chat einmal die Spracheingabe freigeben.")) }
                            }
                        },
                        enabled = !busy && c.settings.current.hasAnyApiKey,
                    ) { Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = tr(if (recording) "Aufnahme beenden" else "Sprechen")) }
                    IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = tr("Senden"))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyBlock(c: AppContainer, reply: LoraReply, go: (String) -> Unit, onClose: () -> Unit, onAnswer: (String) -> Unit) {
    var done by remember(reply) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(reply.text, style = MaterialTheme.typography.bodyMedium)
        reply.steps.forEach { step ->
            when (step) {
                is LoraStep.ChangeSetting -> if (!done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        c.applyLoraStep(step)
                        done = true
                        Notices.info("„${step.setting.label}“: ${step.setting.labelOf(step.value)}")
                        LoraAgent.SETTING_SPOTS[step.setting.id]?.let { spot ->
                            io.github.thepytales.llmhub.data.AppMap.byId(spot)?.let { openTarget(it, go) }
                        }
                        onClose()
                    }) { Text(tr("Ja, umstellen")) }
                    TextButton(onClick = { done = true }) { Text(tr("Nein")) }
                }
                is LoraStep.EnableTool -> if (!done) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        c.applyLoraStep(step)
                        done = true
                        Notices.info("„${step.tool.label}“ " + tr("ist eingeschaltet – in der Seitenleiste unter „Mehr“."))
                        go(step.tool.route)
                        onClose()
                    }) { Text(tr("Einschalten und öffnen")) }
                    TextButton(onClick = { done = true }) { Text(tr("Nicht jetzt")) }
                }
                is LoraStep.Ask -> QuestionCard(step.questions, onAnswer = if (done) null else ({ a -> done = true; onAnswer(a) }))
                is LoraStep.Navigate, is LoraStep.OpenMode, is LoraStep.Handoff -> if (reply.steps.size > 1) {
                    AssistChip(onClick = { runLoraStep(step, go); onClose() }, label = { Text(stepLabel(step)) })
                }
            }
        }
        if (reply.hits.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reply.hits.forEach { t ->
                    AssistChip(onClick = { openTarget(t, go); onClose() }, label = { Text(t.title + " · " + tr(t.kind.label)) })
                }
            }
        }
    }
}

private fun stepLabel(step: LoraStep): String = when (step) {
    is LoraStep.Navigate -> step.target.title
    is LoraStep.OpenMode -> step.target.title
    is LoraStep.Handoff -> tr("Im Chat weiter")
    else -> ""
}

/**
 * Sleeping Lora at the bottom right of an area (settings, TU Vorlagen …): a tap wakes her – „Was möchtest du machen oder
 * einstellen?“. Only with Lora „überall“.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoraHelperButton(c: AppContainer, modifier: Modifier = Modifier) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!LoraHelper.visible(s)) return
    val director = rememberLoraDirector(sequence = listOf(io.github.thepytales.llmhub.ui.LoraAction.SLEEP), start = io.github.thepytales.llmhub.ui.LoraAction.SLEEP)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LocalChatLook.current.card,
        border = boxBorder(),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .semantics { contentDescription = tr("Lora fragen") }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = tr("Lora fragen"),
                    onClick = { LoraHelper.open() },
                )
                .padding(6.dp),
        ) {
            LoraFigure(Modifier.size(52.dp, 52.dp / LORA_ASPECT * 1.4f), label = tr("Lora schläft – antippen zum Fragen"), director = director)
        }
    }
}

/**
 * Erster Start, kurz (AP-07): at most two questions – where Lora appears, and which tools are visible. Skippable; the
 * answers can be changed under Einstellungen › Lora and › Werkzeuge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoraIntroDialog(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(s.loraMode) }
    val tools = remember { AppTool.available(IS_ANDROID) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(s.enabledToolIds) } }
    fun finish(save: Boolean) {
        c.settings.update { cur ->
            var next = cur.copy(loraIntroDone = true)
            if (save) {
                next = next.copy(loraMode = mode, hideLora = mode == "OFF")
                tools.forEach { t -> next = next.withTool(t, t.id in chosen) }
            }
            next
        }
    }
    Dialog(onDismissRequest = { finish(false) }) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, modifier = Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoraStill(Modifier.size(48.dp, 34.dp), action = io.github.thepytales.llmhub.ui.LoraAction.WAVE)
                    Spacer(Modifier.size(10.dp))
                    Text(tr("Hallo, ich bin Lora!"), style = MaterialTheme.typography.titleLarge)
                }
                Text(tr("Zwei kurze Fragen, dann geht es los."), style = MaterialTheme.typography.bodyMedium)
                Text(tr("Wo soll ich erscheinen?"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LORA_MODES.forEach { (id, label) -> FilterChip(selected = mode == id, onClick = { mode = id }, label = { Text(tr(label)) }) }
                }
                if (tools.isNotEmpty()) {
                    Text(tr("Welche Werkzeuge sollen sichtbar sein?"), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tools.forEach { t ->
                            FilterChip(
                                selected = t.id in chosen,
                                onClick = { if (t.id in chosen) chosen -= t.id else chosen += t.id },
                                label = { Text(t.label) },
                            )
                        }
                    }
                    HintText(tr("Alles lässt sich später unter Einstellungen › Lora und › Werkzeuge ändern."))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { finish(false) }) { Text(tr("Überspringen")) }
                    Button(onClick = { finish(true) }) { Text(tr("Fertig")) }
                }
            }
        }
    }
}

/** Where Lora appears (Einstellungen › Lora, first start). */
val LORA_MODES = listOf("EVERYWHERE" to "Überall", "CHAT" to "Nur im Chat", "OFF" to "Aus")

/**
 * Einstellungen › Lora (AP-07): where Lora appears (the old switch „Lora ausblenden“ is „Aus“ here), her model, calmer
 * movements and one-time hints.
 */
@Composable
fun LoraSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    Section(tr("Wo Lora erscheint")) {
        Spot("lora.mode") {
            Segmented(LORA_MODES.map { it.first to tr(it.second) }, s.loraMode, { m -> c.settings.update { it.copy(loraMode = m, hideLora = m == "OFF") } })
        }
        HintText(
            when (s.loraMode) {
                "CHAT" -> tr("Lora arbeitet nur im Chat mit. Den Helfer erreichst du weiter über Strg+K › „Lora fragen“.")
                "OFF" -> tr("Lora erscheint nirgends – weder auf der Startseite noch im Chat oder im Sprachmodus; dort steht dann das Symbol der App. Die Ideen auf der Startseite bleiben.")
                else -> tr("Lora ist auf der Startseite, im Chat und schläft unten rechts in den Einstellungen und weiteren Bereichen – ein Tipp weckt sie.")
            },
        )
    }
    Section(tr("Lora als Navigatorin")) {
        HintText(
            tr("Lora findet Einstellungen, öffnet Modi und TU Vorlagen, schlägt Werkzeuge vor und gibt Fachfragen an einen Chat weiter. Einfache Wünsche löst sie auf dem Gerät, ohne Anfrage; nur für echte Gespräche fragt sie ein Modell. Sie läuft nur auf Abruf und liest nie im Hintergrund mit. In Projekten „Nur auf diesem Gerät“ sucht sie nur lokal."),
        )
        val models by c.models.state.collectAsStateWithLifecycle()
        val options = (listOf(io.github.thepytales.llmhub.data.ModelCatalog.FAST_CHAT) + models.ofType(io.github.thepytales.llmhub.data.ModelType.CHAT).map { it.id }).distinct()
        DropdownField(
            label = tr("Modell"),
            value = io.github.thepytales.llmhub.data.ModelCatalog.info(s.loraModel).displayName,
            options = options.map { io.github.thepytales.llmhub.data.ModelCatalog.info(it).displayName },
            onSelect = { label -> options.firstOrNull { io.github.thepytales.llmhub.data.ModelCatalog.info(it).displayName == label }?.let { id -> c.settings.update { it.copy(loraModel = id) } } },
        )
        HintText(tr("Empfohlen: GLM-5.3 Flash – kurze Antworten, ohne Denkphase, schnell."))
        OutlinedButton(onClick = { LoraHelper.open() }) { Text(tr("Lora jetzt fragen")) }
    }
    Section(tr("Verhalten")) {
        SwitchRow(tr("Ruhigere Bewegungen"), tr("Lora läuft, tanzt und dreht sich nicht – sie liest, schreibt und denkt nur."), s.loraCalm, spot = "lora.calm") { v ->
            c.settings.update { it.copy(loraCalm = v) }
        }
        SwitchRow(tr("Hinweise erlaubt"), tr("Lora darf einmal je Bereich auf etwas hinweisen, z. B. dass es für PDFs ein Werkzeug gibt. Sie unterbricht nie."), s.loraHints) { v ->
            c.settings.update { it.copy(loraHints = v) }
        }
        TextButton(onClick = { c.settings.update { it.copy(loraIntroDone = false) } }) { Text(tr("Die zwei Fragen vom ersten Start erneut stellen")) }
    }
}
