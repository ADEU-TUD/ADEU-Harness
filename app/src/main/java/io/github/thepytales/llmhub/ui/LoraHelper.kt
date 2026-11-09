package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraReply
import io.github.thepytales.llmhub.agent.LoraSense
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.applyLoraStep
import io.github.thepytales.llmhub.agent.loraCalendar
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTarget
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.MapKind
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.withTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lora, the helper at the core of the app (AP-07, AP-34). [open] from anywhere: Strg+L, the „Frag Lora“ button in the
 * chat's top bar, a tap on Lora on the start page, Strg+K › „Lora fragen“, the sleeping Lora in the settings and other areas,
 * a long press on Lora in the chat. On wide screens she works in a panel beside the app that stays open while she
 * leads somewhere; on phones she comes up from the bottom. The conversation stays until „Neu beginnen“.
 */
object LoraHelper {
    /** A wish to start with (set by [open]); the host sends it once the panel is up. */
    val request = MutableStateFlow<String?>(null)

    /** The panel is open. */
    val isOpen = MutableStateFlow(false)

    /** Project of the chat Lora was called from (its privacy level counts). */
    var folderId: String? = null

    /** Route of the screen the user is on (for the suggestions that fit it); set by the app's navigation. */
    val route = MutableStateFlow("chat")

    /** The open chat has messages (then Lora offers to summarize it …). */
    @Volatile
    var chatHasMessages: Boolean = false

    /** Id of the open chat, for wishes about it („Fasse diesen Chat zusammen“). */
    @Volatile
    var chatId: String? = null

    internal val turns = mutableStateListOf<LoraTurn>()
    internal var busy by mutableStateOf(false)

    /** Lora's answer while it is still coming in. */
    internal var partial by mutableStateOf("")
    internal var job: Job? = null

    /** How Lora reacts to her last answer (points the way, nods, shrugs, scratches her head); [moodTick] counts. */
    internal var mood by mutableStateOf<LoraAction?>(null)
    internal var moodTick by mutableStateOf(0)

    fun open(wish: String = "", folderId: String? = null) {
        if (folderId != null) this.folderId = folderId
        isOpen.value = true
        if (wish.isNotBlank()) request.value = wish
    }

    fun close() {
        isOpen.value = false
    }

    fun toggle() = if (isOpen.value) close() else open()

    /** Clears the conversation („Neu beginnen“). */
    fun reset() {
        job?.cancel()
        turns.clear()
        busy = false
        partial = ""
    }

    /** Stops the answer Lora is working on. */
    fun cancel() {
        job?.cancel()
    }

    /** Where Lora's helper appears: only with Lora „überall“. */
    fun visible(s: io.github.thepytales.llmhub.data.AppSettings) = s.loraMode == "EVERYWHERE" && !s.hideLora

    /** Lora can be asked at all (also with Lora „nur im Chat“ – then without the figures elsewhere). */
    fun available(s: io.github.thepytales.llmhub.data.AppSettings) = s.loraMode != "OFF" && !s.hideLora
}

/** One wish and Lora's answer; [state] remembers per step what the user decided (done, declined, undone). */
internal class LoraTurn(val wish: String) {
    var reply by mutableStateOf<LoraReply?>(null)
    val state = mutableStateMapOf<Int, String>()

    /** Results of Lora's actions (AP-38) by step, with their Rückgängig. */
    val results = mutableStateMapOf<Int, io.github.thepytales.llmhub.agent.ActionResult>()
}

/**
 * Carries out the action of step [i] (AP-38): „läuft“ while it works, then its result with Rückgängig; a file it made
 * opens (a PDF in the werkzeugkasten). Errors appear in the card instead of the result.
 */
internal fun runLoraAction(scope: CoroutineScope, turn: LoraTurn, i: Int, step: LoraStep.Action, go: (String) -> Unit) {
    if (turn.state[i] == "running" || turn.state[i] == "done") return
    turn.state[i] = "running"
    scope.launch {
        try {
            val r = step.plan.run()
            io.github.thepytales.llmhub.agent.ToolActions.log("${step.plan.actionId} ausgeführt: ${r.text}")
            turn.results[i] = r
            turn.state[i] = "done"
            r.open?.let { f -> openActionFile(f, go) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            turn.state[i] = "failed:" + tr("Abgebrochen.")
            throw e
        } catch (e: Exception) {
            io.github.thepytales.llmhub.agent.ToolActions.log("${step.plan.actionId} fehlgeschlagen: ${e.message}")
            turn.state[i] = "failed:" + (e.message ?: e.javaClass.simpleName)
        }
    }
}

/** Opens a file an action made or named: PDFs in the werkzeugkasten, documents in the editor, other files outside. */
internal fun openActionFile(f: java.io.File, go: (String) -> Unit) {
    if (f.extension.equals("pdf", true)) {
        io.github.thepytales.llmhub.agent.ActionHosts.openPdf?.invoke(f) ?: run {
            PdfHandoff.open(f.absolutePath, 1, f.name)
            go("pdf")
        }
    }
}

/**
 * Hosts Lora: answers the wishes (also while the panel is closed), and the two questions of the first start. [go]
 * opens a route: "chat…" routes as a chat, everything else as a screen. The panel itself is [LoraSidePanel] (wide)
 * or [LoraSheet] (narrow), placed by the app's layout.
 */
@Composable
fun LoraHelperHost(c: AppContainer, go: (String) -> Unit) {
    val wish by LoraHelper.request.collectAsStateWithLifecycle()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val agent = remember { LoraAgent(c) }
    LoraMotion.calm = s.loraCalm
    LoraHost.send = { text -> sendToLora(c, agent, scope, text, go) }
    LoraHost.go = go
    LaunchedEffect(wish) {
        wish?.let { w ->
            LoraHelper.request.value = null
            sendToLora(c, agent, scope, w, go)
        }
    }
    // Who switched Lora off before is not asked again.
    val panelOpen by LoraHelper.isOpen.collectAsStateWithLifecycle()
    if (!s.loraIntroDone && s.hasAnyApiKey && !s.hideLora && !panelOpen) LoraIntroDialog(c)
}

/** The functions of the host for the panel (one host per window). */
internal object LoraHost {
    var send: (String) -> Unit = {}
    var go: (String) -> Unit = {}
}

private fun sendToLora(c: AppContainer, agent: LoraAgent, scope: CoroutineScope, text: String, go: (String) -> Unit) {
    val wish = text.trim()
    if (wish.isEmpty() || LoraHelper.busy) return
    val turn = LoraTurn(wish)
    val history = LoraHelper.turns.mapNotNull { t -> t.reply?.let { t.wish to it.text } }
    LoraHelper.turns += turn
    LoraHelper.busy = true
    LoraHelper.partial = ""
    LoraHelper.job = scope.launch {
        try {
            val reply = runCatching { agent.ask(wish, history, LoraHelper.folderId, IS_ANDROID) { LoraHelper.partial = it } }
                .getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    LoraReply(tr("Das hat nicht geklappt – versuch es bitte noch einmal."))
                }
            turn.reply = reply
            LoraHelper.mood = moodFor(reply)
            LoraHelper.moodTick++
            // Lora's safe actions run at once (AP-38); risky ones wait for „Ausführen“ in their card.
            reply.steps.forEachIndexed { i, st -> if (st is LoraStep.Action && !st.plan.risky) runLoraAction(scope, turn, i, st, go) }
            // Leading somewhere, opening and handing over happen right away; settings, tools and the calendar wait for „Ja“.
            val first = reply.steps.singleOrNull()
            if (first != null && runLoraStep(first, go)) {
                turn.state[0] = "done"
                if (first !is LoraStep.Navigate || !LoraLayout.docked) LoraHelper.close()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (turn.reply == null) turn.reply = LoraReply(tr("Gut, ich höre auf."))
        } finally {
            LoraHelper.busy = false
            LoraHelper.partial = ""
        }
    }
}

/** Lora's reaction to an answer: she points the way, nods, shrugs when she found nothing, scratches her head on trouble. */
internal fun moodFor(reply: LoraReply): LoraAction = when {
    listOf("Ich erreiche gerade kein Modell", "Das hat nicht geklappt").any { reply.text.startsWith(it) || reply.text.startsWith(tr(it)) } -> LoraAction.SCRATCH
    reply.steps.any { it is LoraStep.Navigate || it is LoraStep.OpenMode || it is LoraStep.OpenChat || it is LoraStep.Handoff } -> LoraAction.POINT
    reply.steps.isEmpty() && reply.hits.isEmpty() && listOf("nichts gefunden", "keinen Chat").any { reply.text.contains(it) || reply.text.contains(tr(it)) } -> LoraAction.SHRUG
    reply.steps.any { it is LoraStep.ChangeSetting || it is LoraStep.EnableTool || it is LoraStep.Reminder || it is LoraStep.Remember || it is LoraStep.Plan } -> LoraAction.EUREKA
    else -> LoraAction.NOD
}

/** Whether Lora's panel is docked beside the app (wide windows) – then she stays open while leading somewhere. */
internal object LoraLayout {
    @Volatile
    var docked: Boolean = false
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
        handOver(step, go)
        true
    }
    is LoraStep.OpenChat -> {
        LoraFlight.start(LoraFlight.Landing.CHAT)
        go(chatRoute(step.id))
        true
    }
    is LoraStep.Tour -> LoraTours.byId(step.tourId)?.let { LoraTours.start(it, go); true } ?: false
    else -> false
}

/**
 * Hands a question to a new chat (AP-33): the request is the message, what Lora already found out goes along as
 * hidden instructions, and a card above the message says that Lora handed it over – with model and reason.
 */
private fun handOver(step: LoraStep.Handoff, go: (String) -> Unit) {
    val c = io.github.thepytales.llmhub.App.container
    val review = c.settings.current.loraHandoffReview
    val instructions = step.summary.takeIf { it.isNotBlank() && it != step.request }?.let {
        "Kontext aus dem Gespräch mit Lora, der Helferin der App (nicht vom Nutzer getippt, nicht wörtlich wiederholen): $it"
    }
    val line = ModelCatalog.info(step.model).displayName + (step.reason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    ChatHandoff.pending.value = ChatHandoff.Content(
        step.request, emptyList(), send = !review, instructions = instructions, handoff = line, model = step.model,
    )
    LoraFlight.start(LoraFlight.Landing.CHAT)
    go(chatRoute(model = step.model))
}

/** Opens a place of the app map: a screen (with the glowing frame), a mode or a TU Vorlage – Lora flies there. */
internal fun openTarget(t: AppTarget, go: (String) -> Unit, text: String = "") {
    when {
        t.route.startsWith(MODE_ROUTE) -> {
            ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = t.route.removePrefix(MODE_ROUTE))
            LoraFlight.start(LoraFlight.Landing.CHAT)
            go("chat")
        }
        t.route.startsWith("template:") -> {
            LoraFlight.start(LoraFlight.Landing.CHAT)
            go(chatRoute(persona = t.route.removePrefix("template:")))
        }
        else -> {
            val spot = t.spot ?: "drawer.${t.route}"
            LoraFlight.start(LoraFlight.Landing.PLACE, spot)
            Spotlight.show(spot)
            go(t.route)
        }
    }
}

// ── The panel ────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Lora's panel beside the app (wide windows): slides in from the right, the app gets narrower instead of covered. Its
 * width can be dragged at the line on its left (320–640 dp, remembered; double-click resets).
 */
@Composable
fun LoraSidePanel(c: AppContainer) {
    val open by LoraHelper.isOpen.collectAsStateWithLifecycle()
    val width = remember { PanelWidths.of(c).lora }
    LoraLayout.docked = true
    AnimatedVisibility(open, enter = expandHorizontally(tween(260)) + fadeIn(tween(260)), exit = shrinkHorizontally(tween(220)) + fadeOut(tween(180))) {
        Row(Modifier.fillMaxHeight()) {
            PanelResizer(width, panelLeft = false, label = tr("Breite von Loras Bereich ändern"))
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(width.value).fillMaxHeight()) {
                LoraPanel(c, compact = false)
            }
        }
    }
}

/** Lora's panel on narrow screens: comes up from the bottom over a light veil; a tap on the veil closes it. */
@Composable
fun BoxScope.LoraSheet(c: AppContainer) {
    val open by LoraHelper.isOpen.collectAsStateWithLifecycle()
    LoraLayout.docked = false
    AnimatedVisibility(open, enter = fadeIn(tween(200)), exit = fadeOut(tween(200)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = tr("Schließen")) { LoraHelper.close() },
        )
    }
    AnimatedVisibility(
        open,
        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .fillMaxHeight(0.82f)
                // Taps inside do not reach the veil.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                }
                LoraPanel(c, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoraPanel(c: AppContainer, compact: Boolean) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val route by LoraHelper.route.collectAsStateWithLifecycle()
    val turns = LoraHelper.turns
    val busy = LoraHelper.busy
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val recorder = remember { Recorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val suggestions = remember(route, s.enabledTools, LoraHelper.chatHasMessages) {
        LoraSense.suggestions(route, LoraHelper.chatHasMessages, s.enabledToolIds, IS_ANDROID)
    }
    // Lora knows the mails chosen in Mail & Kalender (AP-69): „Mail & Kalender · 12 Mails ausgewählt“.
    val chosenMails by io.github.thepytales.llmhub.agent.LoraFocus.mails.collectAsStateWithLifecycle()
    val place = tr(LoraSense.place(route)) + (chosenMails?.count?.takeIf { it > 0 && route.startsWith("mail") }?.let { " · " + tr("{0} Mails ausgewählt", it) } ?: "")

    fun send(text: String) {
        if (text.isBlank() || busy) return
        input = ""
        LoraHost.send(text)
    }

    fun suggest(sg: LoraSense.Suggestion) {
        val chat = LoraHelper.chatId
        if (sg.inChat && chat != null) {
            // About the open chat: the wish goes in there as a message.
            LoraHelper.turns += LoraTurn(sg.label).apply { reply = LoraReply(tr("Ich schreibe es in den Chat.")); state[0] = "done" }
            ChatHandoff.pending.value = ChatHandoff.Content(sg.wish, emptyList(), send = true, chatId = chat, handoff = tr("aus Loras Vorschlägen"))
            LoraFlight.start(LoraFlight.Landing.CHAT)
            // The suggestions about a chat only appear while it is open, so there is nowhere to go.
            if (!LoraHelper.route.value.startsWith("chat")) LoraHost.go(chatRoute(chat))
            LoraHelper.close()
            return
        }
        val target = sg.target?.let { AppMap.byId(it, AppMap.build(io.github.thepytales.llmhub.data.TemplateLibrary.all(c.templates.items.value), IS_ANDROID)) }
        if (target == null) {
            send(sg.wish)
            return
        }
        // A fixed place: Lora answers at once, without searching.
        val step = if (target.kind == MapKind.MODE || target.kind == MapKind.TEMPLATE) LoraStep.OpenMode(target) else LoraStep.Navigate(target)
        val turn = LoraTurn(sg.label).apply {
            reply = LoraReply(if (step is LoraStep.OpenMode) tr("Ich öffne „{0}“.", target.title) else tr("Hier ist es: {0}.", tr(target.path)), listOf(step))
            state[0] = "done"
        }
        LoraHelper.turns += turn
        runLoraStep(step, LoraHost.go)
        if (step !is LoraStep.Navigate || compact) LoraHelper.close()
    }

    LaunchedEffect(turns.size, busy, LoraHelper.partial.length / 40) {
        val last = list.layoutInfo.totalItemsCount - 1
        if (last >= 0) list.animateScrollToItem(last)
    }
    // On wide screens the cursor waits in the field; on phones the keyboard only comes when tapping it.
    LaunchedEffect(Unit) { if (!compact) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        PanelHeader(
            status = when {
                busy -> tr("Ich schaue nach …")
                turns.isEmpty() -> tr("Wie kann ich helfen?")
                else -> tr("Noch etwas?")
            },
            busy = busy,
            canReset = turns.isNotEmpty() || busy,
            onReset = { LoraHelper.reset() },
            onClose = { if (recording) runCatching { recorder.stop() }; LoraHelper.close() },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Abläufe (AP-40) that run without a card in this conversation, e.g. after a restart.
            item(key = "flows") {
                val shown = turns.flatMap { t -> t.reply?.steps.orEmpty().filterIsInstance<LoraStep.Plan>().map { it.flowId } }.toSet()
                OpenFlows(c, shown, LoraHost.go)
            }
            if (turns.isEmpty() && !busy) item(key = "welcome") { Welcome(s.userName, place, suggestions, onSuggest = ::suggest) }
            turns.forEachIndexed { i, turn ->
                item(key = "wish$i") { WishBubble(turn.wish) }
                turn.reply?.let { reply -> item(key = "reply$i") { LoraSays { ReplyBlock(c, turn, reply, onAnswer = ::send) } } }
            }
            if (busy) item(key = "busy") {
                LoraSays {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (LoraHelper.partial.isNotBlank()) MarkdownText(LoraHelper.partial) else TypingDots()
                        TextButton(onClick = { LoraHelper.cancel() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Abbrechen"))
                        }
                    }
                }
            }
        }
        // What fits here, always one tap away (in the welcome as a list, later as a row above the input).
        if (suggestions.isNotEmpty() && (turns.isNotEmpty() || busy)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(place, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 2.dp))
                suggestions.forEach { sg -> SuggestionPill(tr(sg.label), enabled = !busy) { suggest(sg) } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(tr("Frag Lora …")) },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { e ->
                        // Enter sends, Shift+Enter makes a new line; Escape closes the panel.
                        when {
                            e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed -> { send(input); true }
                            e.type == KeyEventType.KeyDown && e.key == Key.Escape -> { LoraHelper.close(); true }
                            else -> false
                        }
                    },
            )
            IconButton(
                onClick = {
                    if (recording) {
                        recording = false
                        val file = recorder.stop()
                        if (file != null) scope.launch {
                            val text = runCatching { c.llm.transcribe(c.settings.current.sttModel, file) }.getOrNull().orEmpty()
                            if (text.isBlank()) Notices.info(tr("Keine Sprache erkannt.")) else send(text)
                        }
                    } else {
                        runCatching { recorder.start(); recording = true }
                            .onFailure { Notices.error(tr("Mikrofon nicht verfügbar – im Chat einmal die Spracheingabe freigeben.")) }
                    }
                },
                enabled = !busy && s.hasAnyApiKey,
            ) { Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = tr(if (recording) "Aufnahme beenden" else "Sprechen")) }
            FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = tr("Senden"), modifier = Modifier.size(20.dp))
            }
        }
        if (!compact && DESKTOP) {
            Text(
                tr("Strg+L öffnet und schließt Lora"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun PanelHeader(status: String, busy: Boolean, canReset: Boolean, onReset: () -> Unit, onClose: () -> Unit) {
    val accent = LocalChatLook.current.brand
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(64.dp, 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.05f), accent.copy(alpha = 0.16f))))
                .onGloballyPositioned { LoraFlight.origin = it.boundsInRoot().center },
            contentAlignment = Alignment.Center,
        ) {
            val director = rememberLoraDirector(sequence = listOf(LoraAction.LOOK, LoraAction.IDLE), start = LoraAction.WAVE, minDwellMs = 400)
            LaunchedEffect(busy, LoraHelper.moodTick) { director.play(if (busy) LoraAction.SEARCH else LoraHelper.mood ?: LoraAction.WAVE) }
            LoraFigure(Modifier.size(60.dp, 42.dp), label = tr("Lora"), director = director, hint = if (busy) listOf(LoraAction.SEARCH, LoraAction.BINOCULARS) else emptyList(), hintEvery = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Lora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canReset) IconButton(onClick = onReset) { Icon(Icons.Outlined.RestartAlt, contentDescription = tr("Neu beginnen")) }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen")) }
    }
}

@Composable
private fun Welcome(name: String, place: String, suggestions: List<LoraSense.Suggestion>, onSuggest: (LoraSense.Suggestion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            (if (name.isNotBlank()) tr("Hallo") + " ${name.trim()}! " else tr("Hallo! ")) + tr("Ich bin Lora."),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            tr("Ich finde Einstellungen, öffne TU Vorlagen und Modi, schlage Werkzeuge vor, suche frühere Chats, trage Fristen in den Kalender ein und gebe Fachfragen an einen Chat weiter."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            Text(tr("Das passt hier") + " · $place", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            ) {
                Column {
                    suggestions.forEachIndexed { i, sg ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(start = 44.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { onSuggest(sg) }.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = LocalChatLook.current.brand, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(tr(sg.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WishBubble(text: String) {
    val look = LocalChatLook.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = look.userBubble, shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Text(text, color = look.onUserBubble, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/** Lora's side of the conversation: her small figure, then the content. */
@Composable
private fun LoraSays(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        LoraFaceStill(Modifier.padding(top = 2.dp).size(28.dp), action = LoraAction.LOOK)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Three dots that rise in turn while Lora thinks. */
@Composable
internal fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1100)), label = "phase")
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0..2) {
            val lift = (1f - kotlin.math.abs(((phase - i + 3f) % 3f) - 0.5f) * 2f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(top = (4 * (1 - lift)).dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LocalChatLook.current.brand.copy(alpha = 0.35f + 0.55f * lift)),
            )
        }
    }
}

@Composable
private fun SuggestionPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = LocalChatLook.current.brand, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ── Lora's answers: text and cards ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyBlock(c: AppContainer, turn: LoraTurn, reply: LoraReply, onAnswer: (String) -> Unit) {
    val context = LocalContext.current
    val go = LoraHost.go
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (reply.text.isNotBlank()) MarkdownText(reply.text)
        val changes = reply.steps.withIndex().filter { it.value is LoraStep.ChangeSetting }
        if (changes.size > 1) {
            SettingsCard(c, turn, changes.map { it.index to it.value as LoraStep.ChangeSetting })
        }
        reply.steps.forEachIndexed { i, step ->
            val state = turn.state[i]
            when (step) {
                is LoraStep.ChangeSetting -> if (changes.size == 1) SettingsCard(c, turn, listOf(i to step))
                is LoraStep.EnableTool -> ResultCard(
                    icon = Icons.Outlined.Construction, title = step.tool.label, subtitle = step.tool.description,
                ) {
                    when (state) {
                        "done" -> DoneRow(tr("Eingeschaltet – in der Seitenleiste unter „Mehr“")) {
                            TextButton(onClick = {
                                c.settings.update { it.withTool(step.tool, false) }
                                turn.state[i] = "undone"
                            }) { Text(tr("Rückgängig")) }
                        }
                        "no", "undone" -> HintText(tr(if (state == "no") "Nicht eingeschaltet." else "Wieder ausgeschaltet."))
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                c.applyLoraStep(step)
                                turn.state[i] = "done"
                                LoraFlight.start(LoraFlight.Landing.PLACE, "drawer.${step.tool.route}")
                                go(step.tool.route)
                                if (!LoraLayout.docked) LoraHelper.close()
                            }) { Text(tr("Einschalten und öffnen")) }
                            TextButton(onClick = { turn.state[i] = "no" }) { Text(tr("Nicht jetzt")) }
                        }
                    }
                }
                is LoraStep.Reminder -> ResultCard(
                    icon = Icons.Outlined.CalendarMonth, title = step.title,
                    subtitle = step.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)) +
                        if (step.daysBefore > 0) " · " + tr("Erinnerung") + " ${step.daysBefore} " + tr(if (step.daysBefore == 1) "Tag vorher" else "Tage vorher") else "",
                ) {
                    when (state) {
                        "done" -> DoneRow(tr("Kalendereintrag erstellt"))
                        "no" -> HintText(tr("Nicht eingetragen."))
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                runCatching {
                                    val file = c.loraCalendar(listOf(step.title to step.date), step.daysBefore)
                                    openPrimary(context, file)
                                }.onSuccess { turn.state[i] = "done" }
                                    .onFailure { Notices.error(tr("Der Kalendereintrag ließ sich nicht anlegen.")) }
                            }) { Text(tr("In den Kalender")) }
                            TextButton(onClick = { turn.state[i] = "no" }) { Text(tr("Nein")) }
                        }
                    }
                }
                is LoraStep.Action -> ActionCard(turn, i, step, go)
                is LoraStep.Remember -> RememberCard(c, turn, i, step)
                is LoraStep.Plan -> {
                    FlowCard(c, step.flowId, go)
                    if (step.dropped.isNotEmpty()) HintText(tr("Weggelassen, weil es das hier nicht gibt: {0}", step.dropped.joinToString()))
                }
                is LoraStep.Tour -> TourCard(turn, i, step, go)
                is LoraStep.Ask -> QuestionCard(step.questions, onAnswer = if (state != null) null else ({ a -> turn.state[i] = "done"; onAnswer(a) }))
                is LoraStep.Navigate -> PlaceCard(step.target, opened = state == "done") { runLoraStep(step, go); turn.state[i] = "done"; if (!LoraLayout.docked) LoraHelper.close() }
                is LoraStep.OpenMode -> PlaceCard(step.target, opened = state == "done") { runLoraStep(step, go); turn.state[i] = "done"; LoraHelper.close() }
                is LoraStep.OpenChat -> ResultCard(
                    icon = Icons.Outlined.ChatBubbleOutline, title = step.title, subtitle = relativeDay(step.updatedAt),
                    onClick = { runLoraStep(step, go); turn.state[i] = "done"; LoraHelper.close() },
                ) {}
                is LoraStep.Handoff -> ResultCard(
                    icon = Icons.Outlined.AutoAwesome, title = tr("An einen Chat übergeben"),
                    subtitle = ModelCatalog.info(step.model).displayName + (step.reason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                ) {
                    if (state == "done") DoneRow(tr("Im Chat übergeben – dort geht es weiter"))
                    else FilledTonalButton(onClick = { runLoraStep(step, go); turn.state[i] = "done"; LoraHelper.close() }) { Text(tr("Im Chat weiter")) }
                }
            }
        }
        if (reply.hits.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reply.hits.forEach { t -> PlaceCard(t, opened = false, compact = true) { openTarget(t, go); if (!LoraLayout.docked || t.kind == MapKind.MODE || t.kind == MapKind.TEMPLATE) LoraHelper.close() } }
            }
        }
    }
}

/**
 * An action of Lora in a tool (AP-38): the step line while it runs, the preview (vorher → nachher) with „Ausführen“
 * for risky ones, then the result with „Rückgängig“ and „Öffnen“.
 */
@Composable
private fun ActionCard(turn: LoraTurn, i: Int, step: LoraStep.Action, go: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val state = turn.state[i]
    val result = turn.results[i]
    val plan = step.plan
    ResultCard(icon = Icons.Outlined.Construction, title = plan.title.removeSuffix(" …").removeSuffix("…"), subtitle = null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (plan.preview.isNotEmpty() && (state == null || state == "running")) {
                Column(Modifier.semantics { contentDescription = tr("Vorschau") }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    plan.preview.take(12).forEach { (before, after) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(before, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("wird zu"), modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
                            Text(after, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                    }
                    if (plan.preview.size > 12) HintText(tr("… und {0} weitere", plan.preview.size - 12))
                }
            }
            when {
                state == "running" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    HintText(plan.title)
                }
                state == "done" && result != null -> {
                    DoneRow(result.text) {
                        result.undo?.let { undo ->
                            TextButton(onClick = {
                                turn.state[i] = "undoing"
                                scope.launch {
                                    val text = runCatching { undo() }.getOrElse { tr("Rückgängig ging nicht: {0}", it.message.orEmpty()) }
                                    io.github.thepytales.llmhub.agent.ToolActions.log("${plan.actionId} rückgängig: $text")
                                    turn.state[i] = "undone:$text"
                                }
                            }) { Text(tr("Rückgängig")) }
                        }
                    }
                    val first = result.open ?: result.files.firstOrNull()?.takeIf { it.extension.equals("pdf", true) }
                    if (first != null && result.open == null) TextButton(onClick = { openActionFile(first, go) }) { Text(tr("Öffnen")) }
                }
                state == "undoing" -> HintText(tr("Wird zurückgenommen …"))
                state?.startsWith("undone:") == true -> HintText(state.removePrefix("undone:"))
                state?.startsWith("failed:") == true -> Text(state.removePrefix("failed:"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                state == "no" -> HintText(tr("Nichts geändert."))
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { runLoraAction(scope, turn, i, step, go) }) { Text(tr("Ausführen")) }
                    TextButton(onClick = { turn.state[i] = "no" }) { Text(tr("Abbrechen")) }
                }
            }
        }
    }
}

/** One or more settings Lora wants to change: before → after, „Umstellen“, then „Rückgängig“. */
@Composable
private fun SettingsCard(c: AppContainer, turn: LoraTurn, changes: List<Pair<Int, LoraStep.ChangeSetting>>) {
    val key = changes.first().first
    val state = turn.state[key]
    val current = c.settings.current
    ResultCard(
        icon = Icons.Outlined.Tune,
        title = if (changes.size == 1) changes[0].second.setting.label else tr("Einstellungen umstellen"),
        subtitle = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            changes.forEach { (_, step) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (changes.size > 1) Text(step.setting.label + ": ", style = MaterialTheme.typography.bodySmall)
                    Text(step.setting.labelOf(step.setting.get(current)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("wird zu"), modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
                    Text(step.setting.labelOf(step.value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.size(4.dp))
            when (state) {
                "done" -> DoneRow(tr("Umgestellt"))
                "no" -> HintText(tr("Nichts geändert."))
                "undone" -> HintText(tr("Zurückgesetzt."))
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val before = c.settings.current
                        changes.forEach { (_, step) -> c.applyLoraStep(step) }
                        changes.forEach { (i, _) -> turn.state[i] = "done" }
                        val what = changes.joinToString(", ") { (_, st) -> "„${st.setting.label}“: ${st.setting.labelOf(st.value)}" }
                        Notices.undo(what, restore = {
                            c.settings.update { cur -> changes.fold(cur) { acc, (_, st) -> st.setting.set(acc, st.setting.get(before)) } }
                            changes.forEach { (i, _) -> turn.state[i] = "undone" }
                        })
                        // Shows where the setting lives, so it can be found by hand next time.
                        changes.firstNotNullOfOrNull { (_, st) -> LoraAgent.SETTING_SPOTS[st.setting.id] }?.let { spot ->
                            AppMap.byId(spot)?.let { openTarget(it, LoraHost.go) }
                        }
                        if (!LoraLayout.docked) LoraHelper.close()
                    }) { Text(tr("Umstellen")) }
                    TextButton(onClick = { changes.forEach { (i, _) -> turn.state[i] = "no" } }) { Text(tr("Nein")) }
                }
            }
        }
    }
}

/** A place of the app: icon by kind, title, path, „Öffnen“ (or „Geöffnet“). */
@Composable
private fun PlaceCard(t: AppTarget, opened: Boolean, compact: Boolean = false, onOpen: () -> Unit) {
    ResultCard(icon = kindIcon(t.kind), title = t.title, subtitle = t.path, onClick = onOpen, dense = compact) {
        if (!compact) {
            if (opened) DoneRow(tr(if (t.kind == MapKind.MODE || t.kind == MapKind.TEMPLATE) "Geöffnet" else "Hier ist es – leuchtend markiert"))
            else TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text(tr("Öffnen")) }
        }
    }
}

private fun kindIcon(kind: MapKind): ImageVector = when (kind) {
    MapKind.AREA -> Icons.Outlined.Explore
    MapKind.SETTINGS, MapKind.OPTION -> Icons.Outlined.Settings
    MapKind.MODE -> Icons.Outlined.AutoAwesome
    MapKind.TEMPLATE -> Icons.Outlined.Bookmarks
    MapKind.TOOL -> Icons.Outlined.Construction
}

/** The frame of Lora's result cards: hairline border, icon in a tinted circle, title and line, then [actions]. */
@Composable
internal fun ResultCard(
    icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)? = null, dense: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val brand = LocalChatLook.current.brand
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = if (dense) 8.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (dense) 28.dp else 32.dp).clip(CircleShape).background(brand.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = brand, modifier = Modifier.size(if (dense) 16.dp else 18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.padding(start = if (dense) 38.dp else 42.dp)) { actions() }
        }
    }
}

@Composable
internal fun DoneRow(text: String, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = LocalChatLook.current.brand, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        trailing()
    }
}

/** "heute", "gestern", "vor 3 Tagen", "12.10.2026". */
internal fun relativeDay(millis: Long): String {
    val day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, java.time.LocalDate.now())
    return when {
        days <= 0L -> tr("heute")
        days == 1L -> tr("gestern")
        days < 7L -> tr("vor {0} Tagen", days)
        else -> day.format(DateTimeFormatter.ofPattern("d.M.yyyy"))
    }
}

// ── Entry points ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * Sleeping Lora at the bottom right of an area (settings, TU Vorlagen …): a tap wakes her – the panel opens. Only
 * with Lora „überall“. Screens leave [LORA_BUTTON_ROOM] free at the end of their lists, so she covers nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoraHelperButton(c: AppContainer, modifier: Modifier = Modifier) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val open by LoraHelper.isOpen.collectAsStateWithLifecycle()
    if (!LoraHelper.visible(s) || open) return
    val look = LocalChatLook.current
    val director = rememberLoraDirector(sequence = listOf(LoraAction.SLEEP), start = LoraAction.SLEEP)
    // With a mouse: she wakes up and waves while the pointer is over her.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) director.play(LoraAction.WAVE) }
    Surface(
        shape = CircleShape,
        color = look.card,
        border = BorderStroke(1.dp, look.brand.copy(alpha = 0.35f)),
        shadowElevation = 6.dp,
        modifier = modifier.size(LORA_BUTTON_SIZE),
    ) {
        Box(
            Modifier
                .background(look.brand.copy(alpha = if (hovered) 0.16f else 0.08f))
                .semantics { contentDescription = tr("Lora fragen") }
                .hoverable(interaction)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = tr("Lora fragen"),
                    onClick = { LoraHelper.open() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Centred with room on every side: the head stays clear of the round edge, the z's rise inside it.
            LoraFace(Modifier.size(LORA_BUTTON_FACE), label = tr("Lora schläft – antippen zum Fragen"), director = director, fps = 20)
        }
    }
}

/** Size of the sleeping Lora in the corner: a round button as big as a floating action button, with her close-up. */
private val LORA_BUTTON_SIZE = 62.dp

/** Her close-up in that button: about three quarters of it, so a ring of the button's colour stays around her. */
private val LORA_BUTTON_FACE = 46.dp

/** Room screens leave at the end of their lists for the sleeping Lora (so she covers no entry). */
val LORA_BUTTON_ROOM = 96.dp

/** Height of the „Frag Lora“ pill and of Lora's face in it. */
private val LORA_PILL_HEIGHT = 42.dp
private val LORA_PILL_FACE = 34.dp

/**
 * Lora in the chat's top bar (Strg+L opens and closes her panel): her close-up in a tinted pill with „Frag Lora“, so
 * she is easy to find; [compact] (narrow windows with a chat title) shows only the round face.
 */
@Composable
fun LoraTopBarButton(c: AppContainer, folderId: String? = null, compact: Boolean = false) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!LoraHelper.available(s)) return
    val open by LoraHelper.isOpen.collectAsStateWithLifecycle()
    val look = LocalChatLook.current
    // Waves hello once, then is just there: looks around, follows the pointer with her eyes, blinks.
    val director = rememberLoraDirector(sequence = listOf(LoraAction.IDLE, LoraAction.LOOK), start = LoraAction.WAVE)
    val label = tr("Lora fragen") + if (DESKTOP) " (Strg+L)" else ""
    Surface(
        onClick = { if (LoraHelper.isOpen.value) LoraHelper.close() else LoraHelper.open(folderId = folderId) },
        shape = RoundedCornerShape(50),
        color = look.brand.copy(alpha = if (open) 0.22f else 0.10f),
        border = BorderStroke(1.dp, look.brand.copy(alpha = if (open) 0.6f else 0.3f)),
        modifier = Modifier.padding(horizontal = 4.dp).height(LORA_PILL_HEIGHT).semantics { contentDescription = label },
    ) {
        // The face sits in the pill's round end with the same gap above, below and to the left (not on the edge);
        // compact: a circle with the face in the middle.
        Row(
            Modifier.padding(start = 5.dp, end = if (compact) 5.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoraFace(Modifier.size(LORA_PILL_FACE), label = label, director = director, fps = 20)
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(tr("Frag Lora"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
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
                    LoraFigure(Modifier.size(64.dp, 45.dp), label = tr("Lora winkt"), director = rememberLoraDirector(listOf(LoraAction.WAVE, LoraAction.LOOK), LoraAction.WAVE))
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(tr("Hallo, ich bin Lora!"), style = MaterialTheme.typography.titleLarge)
                        Text(tr(io.github.thepytales.llmhub.Brand.SLOGAN), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
val LORA_MODES = listOf("EVERYWHERE" to trLater("Überall"), "CHAT" to trLater("Nur im Chat"), "OFF" to trLater("Aus"))

/**
 * Einstellungen › Lora (AP-07, AP-33–35): where Lora appears, her model, how she hands over and helps in the chat,
 * calmer movements and one-time hints.
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
                "CHAT" -> tr("Lora arbeitet nur im Chat mit. Ihr Panel öffnest du weiter mit Strg+L, über den Knopf oben im Chat oder Strg+K › „Lora fragen“.")
                "OFF" -> tr("Lora erscheint nirgends – weder auf der Startseite noch im Chat oder im Sprachmodus; dort steht dann das Symbol der App. Die Ideen auf der Startseite bleiben.")
                else -> tr("Lora ist auf der Startseite, im Chat und schläft unten rechts in den Einstellungen und weiteren Bereichen – ein Tipp weckt sie. Strg+L öffnet ihr Panel überall.")
            },
        )
    }
    Section(tr("Lora als Helferin")) {
        HintText(
            tr("Lora findet Einstellungen, öffnet Modi und TU Vorlagen, schlägt Werkzeuge vor, sucht frühere Chats, trägt Fristen in den Kalender ein und gibt Fachfragen an einen Chat weiter. Einfache Wünsche löst sie auf dem Gerät, ohne Anfrage; nur für echte Gespräche fragt sie ein Modell. Sie läuft nur auf Abruf und liest nie im Hintergrund mit. In Projekten „Nur auf diesem Gerät“ sucht sie nur lokal."),
        )
        val models by c.models.state.collectAsStateWithLifecycle()
        val options = (listOf(ModelCatalog.FAST_CHAT) + models.ofType(io.github.thepytales.llmhub.data.ModelType.CHAT).map { it.id }).distinct()
        DropdownField(
            label = tr("Modell"),
            value = ModelCatalog.info(s.loraModel).displayName,
            options = options.map { ModelCatalog.info(it).displayName },
            onSelect = { label -> options.firstOrNull { ModelCatalog.info(it).displayName == label }?.let { id -> c.settings.update { it.copy(loraModel = id) } } },
        )
        HintText(tr("Empfohlen: GLM-5.3 Flash – kurze Antworten, ohne Denkphase, schnell."))
        // Where handed-over questions go (DeepSeek by default, or chosen per question).
        val handoffOptions = (listOf(ModelCatalog.DEFAULT_CHAT) + models.ofType(io.github.thepytales.llmhub.data.ModelType.CHAT).map { it.id }).distinct()
        fun handoffLabel(id: String) = ModelCatalog.info(id).displayName
        DropdownField(
            label = tr("Standardmodell für Übergaben"),
            value = handoffLabel(s.loraHandoffModel),
            options = handoffOptions.map(::handoffLabel),
            onSelect = { label -> handoffOptions.firstOrNull { handoffLabel(it) == label }?.let { id -> c.settings.update { it.copy(loraHandoffModel = id) } } },
        )
        HintText(tr("Empfohlen: DeepSeek V4.1 Flash. Fragen, die Lora an einen Chat gibt, beantwortet dieses Modell. Nur in Sonderfällen – Programmieren, sehr schwierige Analysen, riesige Textmengen – wählt Lora selbst ein anderes und nennt den Grund auf der Karte im Chat. In Projekten mit strenger Datenschutzstufe gilt ein dort erlaubtes Modell."))
        SwitchRow(tr("Übergaben erst zeigen"), tr("Fragen, die Lora an einen Chat gibt, landen zuerst im Eingabefeld – du sendest selbst."), s.loraHandoffReview, spot = "lora.handoffReview") { v ->
            c.settings.update { it.copy(loraHandoffReview = v) }
        }
        SwitchRow(tr("Nächste Schritte unter Antworten"), tr("Lora bietet unter der letzten Antwort an, Fristen in den Kalender zu übernehmen, Tabellen als Excel zu speichern oder Literatur zu übernehmen – erkannt auf dem Gerät."), s.loraNextSteps, spot = "lora.nextSteps") { v ->
            c.settings.update { it.copy(loraNextSteps = v) }
        }
        SwitchRow(tr("KI-Nutzungsnachweis vorschlagen"), tr("Geht es in einem Chat um eine Arbeit (Hausarbeit, Masterarbeit, Paper, Antrag …) und hat die KI dafür Text geschrieben, bietet Lora einmal an, einen KI-Nutzungsnachweis zu erstellen. Im Chat-Menü ist er immer erreichbar."), s.aiUseHint, spot = "lora.aiUse") { v ->
            c.settings.update { it.copy(aiUseHint = v) }
        }
        SwitchRow(tr("Hinweise beim Tippen"), tr("Tippst du im Chat etwas wie „dunkles Design“, zeigt Lora darüber den passenden Ort der App."), s.loraTyping, spot = "lora.typing") { v ->
            c.settings.update { it.copy(loraTyping = v) }
        }
        OutlinedButton(onClick = { LoraHelper.open() }) { Text(tr("Lora jetzt fragen")) }
    }
    Section(tr("Verhalten")) {
        SwitchRow(tr("Ruhigere Bewegungen"), tr("Lora läuft, tanzt, fliegt und dreht sich nicht – sie liest, schreibt und denkt nur."), s.loraCalm, spot = "lora.calm") { v ->
            c.settings.update { it.copy(loraCalm = v) }
        }
        SwitchRow(tr("Hinweise erlaubt"), tr("Lora darf einmal je Bereich auf etwas hinweisen, z. B. dass es für PDFs ein Werkzeug gibt. Sie unterbricht nie."), s.loraHints, spot = "lora.hints") { v ->
            c.settings.update { it.copy(loraHints = v) }
        }
        TextButton(onClick = { c.settings.update { it.copy(loraIntroDone = false) } }) { Text(tr("Die zwei Fragen vom ersten Start erneut stellen")) }
    }
    // AP-39, AP-41, AP-38: what Lora knows, tours, the protocol of her actions.
    LoraMemorySection(c)
}
