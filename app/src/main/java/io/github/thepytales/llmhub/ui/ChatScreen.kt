package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.SmallFloatingActionButton
import io.github.thepytales.llmhub.data.Conversation
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocsLive
import io.github.thepytales.llmhub.data.label
import io.github.thepytales.llmhub.data.thread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    c: AppContainer,
    vm: ChatViewModel,
    /** Opens the side menu; null when it is always visible (wide screens). */
    onOpenDrawer: (() -> Unit)?,
    onNewChat: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val conv by vm.state.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val building by vm.building.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val transcribing by vm.transcribing.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val modelsState by c.models.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val playingId by c.speaker.playingId.collectAsStateWithLifecycle()
    val environments by c.environments.items.collectAsStateWithLifecycle()
    val templates by c.templates.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    /** Prompt template whose fields are being filled in, and the one whose instructions go with the next message. */
    var fillingId by rememberSaveable { mutableStateOf<String?>(null) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    var startTemplateShown by rememberSaveable { mutableStateOf(false) }
    val activeTemplate = templateId?.let { TemplateLibrary.find(it, templates) }
    val images = remember { mutableStateListOf<String>() }
    var imageWarning by remember { mutableStateOf(false) }
    /** The chat follows the newest message until the user scrolls up (see [ChatAutoScroll]). */
    val follow = remember { mutableStateOf(true) }
    /** Text of a message the user wants to remember („Merken …“). */
    var rememberFrom by remember { mutableStateOf<String?>(null) }
    var showMemory by remember { mutableStateOf(false) }
    /** Automatic model choice: the suggested model, waiting for the user's yes or no (and whether pictures go along). */
    var autoAsk by remember { mutableStateOf<Pair<io.github.thepytales.llmhub.agent.ModelRouter.Choice, Boolean>?>(null) }
    /** Hands-free conversation (Sprachmodus). */
    var voiceMode by remember { mutableStateOf(false) }
    val files = remember { mutableStateListOf<FileAttachment>() }
    var loadingFiles by remember { mutableIntStateOf(0) }
    var showModelPicker by remember { mutableStateOf(false) }
    var regenerateFor by remember { mutableStateOf<ChatMessage?>(null) }
    var showRepoPicker by remember { mutableStateOf(false) }
    var showEnvPicker by remember { mutableStateOf(false) }
    var showSystemPrompt by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showPlus by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var export by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val recorder = remember { Recorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var improving by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var showFromApp by remember { mutableStateOf(false) }
    var quoteFrom by remember { mutableStateOf<String?>(null) }
    var ratingFor by remember { mutableStateOf<ChatMessage?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHit by remember { mutableIntStateOf(0) }
    var chipsOpen by remember { mutableStateOf(false) }
    val snippets by c.snippets.items.collectAsStateWithLifecycle()
    val exportJson = rememberFileExport("application/json", onDone = { ok -> if (!ok) Notices.error("Export fehlgeschlagen") }) { out ->
        vm.state.value?.let { out.write(ChatJson.write(it).toByteArray(Charsets.UTF_8)) }
    }

    // Documents sent from the editor ("In Chat senden") wait in the input field of their chat.
    val handedOver by ChatHandoff.pending.collectAsStateWithLifecycle()
    LaunchedEffect(vm.chatId, handedOver) {
        ChatHandoff.takeFor(vm.chatId)?.let { h ->
            files.addAll(h.files)
            if (h.text.isNotBlank() && input.isBlank()) input = h.text
        }
    }

    // The unsent text stays with its chat (also after closing the app).
    LaunchedEffect(vm.chatId) {
        if (input.isBlank()) c.chatDrafts.get(vm.chatId)?.text?.let { input = it }
    }
    LaunchedEffect(input) {
        kotlinx.coroutines.delay(600)
        if (vm.state.value?.temporary == true) return@LaunchedEffect
        if (input.isBlank()) { if (c.chatDrafts.get(vm.chatId) != null) c.chatDrafts.delete(vm.chatId) }
        else if (c.chatDrafts.get(vm.chatId)?.text != input) c.chatDrafts.upsert(io.github.thepytales.llmhub.data.ChatDraft(vm.chatId, input))
    }

    LaunchedEffect(vm) {
        vm.toasts.collect { Notices.info(it) }
    }
    LaunchedEffect(Unit) {
        c.speaker.errors.collect { Notices.error(it) }
    }
    LaunchedEffect(deleted) {
        if (deleted) onNewChat()
    }

    fun useTemplate(t: Template) {
        when {
            t.kind == TemplateKind.PERSONA -> vm.setSystemPrompt(t.content, t.name)
            t.fields.isNotEmpty() -> fillingId = t.id
            else -> {
                input = if (input.isBlank()) t.content else t.content + "\n\n" + input
                templateId = t.id.takeIf { t.instructions.isNotBlank() }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Handed over from another screen (e.g. a transcript): text into the input field, files as attachments.
        if (vm.state.value?.messages.isNullOrEmpty()) {
            ChatHandoff.take()?.let { h ->
                h.mode?.let { vm.setMode(it) }
                if (h.send && h.text.isNotBlank()) {
                    vm.send(h.text, h.images, h.files)
                } else {
                    if (h.text.isNotBlank()) input = if (input.isBlank()) h.text else input + "\n\n" + h.text
                    files.addAll(h.files)
                    images.addAll(h.images)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // A chat started from the template library opens with that template.
        if (!startTemplateShown) {
            startTemplateShown = true
            vm.startTemplate?.let(::useTemplate)
        }
    }

    val platform = rememberChatPlatform(
        isNewChat = { vm.state.value?.messages.isNullOrEmpty() },
        images = images,
        files = files,
        onText = { text -> input = if (input.isBlank()) text else input + "\n\n" + text },
        onLoading = { loadingFiles += it },
    )

    fun startRecording() {
        try {
            recorder.start()
            recording = true
        } catch (e: Exception) {
            Notices.error("Aufnahme nicht möglich: ${e.message}")
        }
    }

    val current = conv
    /** Turns (by their first answer) whose folded steps the user opened. */
    val openSteps = remember(current?.id) { mutableStateMapOf<String, Boolean>() }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // No notification for the chat on screen; opening it marks its result as read.
    DisposableEffect(current.id) {
        c.runs.visibleChatId = current.id
        onDispose { if (c.runs.visibleChatId == current.id) c.runs.visibleChatId = null }
    }
    LifecycleResumeEffect(current.id) {
        vm.markRead()
        onPauseOrDispose { }
    }
    val modelInfo = ModelCatalog.info(current.model)
    val env = current.envId?.let { id -> environments.firstOrNull { it.id == id } }
    val items = remember(current.messages, current.selected) { chatItems(current) }
    val results = remember(current.messages) { current.messages.filter { it.role == "tool" }.associateBy { it.toolCallId } }
    val repoLabel = when {
        !current.githubEnabled -> "Aus"
        else -> current.linkedRepos.label() ?: "An – kein festes Repository"
    }

    val hasMessages = current.messages.isNotEmpty()
    /** Sends the typed message; [withImages] false leaves the attached pictures out (after the privacy question). */
    fun sendNow(withImages: Boolean) {
        val pictures = if (withImages) images.toList() else emptyList()
        if (pictures.isNotEmpty() && modelInfo.vision == false) {
            Notices.info("Hinweis: ${modelInfo.displayName} versteht keine Bilder.")
        }
        follow.value = true
        vm.send(input, pictures, files.toList(), activeTemplate)
        input = ""
        c.chatDrafts.delete(vm.chatId)
        templateId = null
        images.clear()
        files.clear()
    }
    /**
     * Sends, with the automatic model choice: if another model fits the request better, the app asks first
     * (Einstellungen › Chat & Werkzeuge › Vor dem Wechsel fragen) – or switches and says so.
     */
    fun send(withImages: Boolean) {
        if (current.autoModel) {
            val choice = io.github.thepytales.llmhub.agent.ModelRouter.suggest(
                input, if (withImages) images.size else 0, files.toList(), current.githubEnabled && current.linkedRepos.isNotEmpty(), settings.defaultModel,
            )
            val usable = Privacy.allows(c, Privacy.level(c, current.folderId), choice.model) && modelsState.liveIds?.contains(choice.model) != false
            if (usable && choice.model != current.model) {
                if (settings.autoModelAsk) {
                    autoAsk = choice to withImages
                    return
                }
                vm.setModel(choice.model)
                Notices.info(tr("Automatisch") + ": ${ModelCatalog.info(choice.model).displayName} – ${choice.reason}")
            }
        }
        sendNow(withImages)
    }
    // Documents open beside the chat on wide screens (like Claude Docs); on phones they get their own screen.
    var docPanel by rememberSaveable(current.id) { mutableStateOf<String?>(null) }
    val drafts by DocsLive.drafts.collectAsStateWithLifecycle()
    val writingDoc = drafts[current.id]
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val sideBySide = maxWidth >= 1000.dp
    DisposableEffect(sideBySide, current.id) {
        val host: ((String) -> Boolean)? = if (sideBySide) ({ id -> docPanel = id; true }) else null
        DocsHandoff.sidePanel = host
        onDispose { if (DocsHandoff.sidePanel === host) DocsHandoff.sidePanel = null }
    }
    // The model starts writing a document: it opens beside the chat and fills up there.
    LaunchedEffect(writingDoc != null, sideBySide) {
        if (writingDoc != null && sideBySide && docPanel == null) docPanel = "chat:" + current.id
    }
    Row(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (onOpenDrawer != null) IconButton(onClick = onOpenDrawer) { Icon(Icons.Outlined.Menu, contentDescription = "Menü") }
                },
                title = {
                    if (searchOpen) {
                        ChatSearchField(searchQuery, { searchQuery = it; searchHit = 0 }, matchCount(current, searchQuery), searchHit,
                            onPrev = { searchHit++ }, onNext = { searchHit = (searchHit - 1).coerceAtLeast(0) },
                            onClose = { searchOpen = false; searchQuery = "" })
                    } else if (hasMessages) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { menuOpen = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                current.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp),
                            )
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                actions = {
                    if (!hasMessages) {
                        // Temporary chat: nothing is saved (like the incognito mode of other apps).
                        IconButton(onClick = {
                            vm.setTemporary(!current.temporary)
                            Notices.info(if (!current.temporary) "Temporärer Chat: wird nicht gespeichert." else "Chat wird wieder gespeichert.")
                        }) {
                            Icon(
                                if (current.temporary) Icons.Filled.VisibilityOff else Icons.Outlined.VisibilityOff,
                                contentDescription = if (current.temporary) "Temporären Chat beenden" else "Temporärer Chat",
                                tint = if (current.temporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // On a fresh, ordinary chat a "new chat" button would only start the same again.
                    if (hasMessages || current.folderId != null || current.temporary) {
                        IconButton(onClick = onNewChat) {
                            Icon(Icons.Filled.AddComment, contentDescription = "Neuer Chat", modifier = Modifier.size(26.dp))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                        ChatMenu(
                            open = menuOpen,
                            title = if (hasMessages) current.title else "Neuer Chat",
                            hasMessages = hasMessages,
                            pinned = current.pinned,
                            onDismiss = { menuOpen = false },
                            onShare = { shareText(context, chatToMarkdown(current), current.title) },
                            onRename = { showRename = true },
                            onPin = { vm.setPinned(!current.pinned) },
                            onFolder = { showFolder = true },
                            onHomeScreen = platform.pinShortcut?.let { pin -> { pin(current) } },
                            onExport = { export = current.title to chatToMarkdown(current) },
                            onPersona = { showSystemPrompt = true },
                            onEnvironment = { showEnvPicker = true },
                            autoApprove = current.autoApprove,
                            onAutoApprove = { vm.setAutoApprove(!current.autoApprove) },
                            onDelete = { showDelete = true },
                            onSearch = { searchOpen = true },
                            onDuplicate = { vm.fork(null)?.let { id -> onNavigate(chatRoute(id)) } },
                            archived = current.archived,
                            onArchive = {
                                vm.setArchived(!current.archived)
                                if (!current.archived) { Notices.undo("Chat archiviert", restore = { vm.setArchived(false) }); onNewChat() }
                            },
                            onExportJson = { exportJson(io.github.thepytales.llmhub.docs.DocumentWriter.safeName(current.title) + ".json") },
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Wide screens: the chat stays readable in a centered column.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = CONTENT_WIDTH)) {
            Composer(
                input = input,
                onInput = { input = it },
                placeholder = tr(if (hasMessages) "Antworten …" else "Wie kann ich helfen?"),
                modelLabel = if (current.autoModel) tr("Automatisch") + " · " + modelInfo.displayName else modelInfo.displayName,
                onModel = { showModelPicker = true },
                images = images,
                files = files,
                loadingFiles = loadingFiles,
                onRemoveImage = { images.remove(it) },
                onRemoveFile = { files.remove(it) },
                running = running,
                recording = recording,
                transcribing = transcribing,
                onPlus = { showPlus = true },
                onMic = {
                    if (recording) {
                        recording = false
                        recorder.stop()?.let { file -> vm.transcribe(file) { text -> input = if (input.isBlank()) text else "$input $text" } }
                    } else {
                        platform.withMicrophone(::startRecording)
                    }
                },
                onSend = {
                    // Pictures cannot be pseudonymized: ask first (Einstellungen › Datenschutz › Warnung bei Bildern).
                    if (images.isNotEmpty() && settings.pii(io.github.thepytales.llmhub.data.PiiFeature.IMAGE_WARNING) &&
                        !io.github.thepytales.llmhub.data.Privacy.isLocal(current.model)
                    ) imageWarning = true
                    else send(true)
                },
                onStop = { vm.stop() },
                onPaste = platform.paste,
                onImprove = {
                    val before = input
                    improving = true
                    scope.launch {
                        runCatching { vm.improvePrompt(before) }
                            .onSuccess { better ->
                                input = better
                                Notices.undo("Anfrage verbessert", restore = { input = before })
                            }
                            .onFailure { Notices.error("Verbessern nicht möglich: ${it.message}") }
                        improving = false
                    }
                },
                improving = improving,
                suggestions = {
                    SuggestionList(
                        remember(input, templates, snippets) {
                            inputSuggestions(
                                c, input, templates, snippets,
                                replace = { input = it },
                                useTemplate = { useTemplate(it) },
                                attach = { f -> files.add(f) },
                                startLiterature = {
                                    vm.setMode(Conversation.MODE_LITERATURE)
                                    Notices.info("Literatursuchmodus: Thema und Kontext eingeben – der Agent sucht systematisch.")
                                },
                                openDocs = { onNavigate("docs") },
                            )
                        }
                    )
                    PrivacySuggestions(c, input)
                    MemoryLearnBar(c, current.id)
                },
                footnote = if (hasMessages) tr("KI kann Fehler machen. Wichtiges bitte prüfen.") else null,
                onVoice = { platform.withMicrophone { voiceMode = true } },
            ) {
                if (current.temporary) ContextChip(Icons.Outlined.VisibilityOff, "Temporär – wird nicht gespeichert", { }, highlighted = true)
                // Always visible while active: the selected repository, environment and persona.
                if (current.mode == Conversation.MODE_LITERATURE) {
                    ContextChip(Icons.Outlined.ManageSearch, "Literatursuche  ✕", {
                        vm.setMode(null)
                        Notices.info("Literatursuchmodus beendet – normaler Chat.")
                    })
                }
                if (current.githubEnabled && settings.hasGitHub) {
                    // Quiet while nothing is chosen; highlighted once a repository is linked.
                    val repos = current.linkedRepos.label()
                    ContextChip(Icons.Outlined.Code, repos ?: "GitHub · Repo wählen", { showRepoPicker = true }, highlighted = repos != null)
                }
                if (env != null) ContextChip(Icons.Outlined.Tune, env.name, { showEnvPicker = true }, highlighted = env.id != settings.defaultEnvId)
                activeTemplate?.let { t ->
                    ContextChip(Icons.Outlined.Bookmarks, "Vorlage: ${t.name}  ✕", { templateId = null })
                }
                if (current.autoApprove) {
                    ContextChip(Icons.Outlined.DoneAll, "Ohne Rückfrage", {
                        vm.setAutoApprove(false)
                        Notices.info("Aktionen in diesem Chat werden wieder bestätigt.")
                    })
                }
                if (!current.systemPrompt.isNullOrBlank()) {
                    ContextChip(Icons.Outlined.Person, current.persona ?: "System-Prompt", { showSystemPrompt = true })
                }
                // The memory goes along automatically; the chip shows how much and switches it off for this chat.
                MemoryChip(c, current) { showMemory = true }
                // Shows that names and contact details leave the device only as placeholders.
                if (settings.pseudonymize) {
                    ContextChip(Icons.Outlined.Shield, "Pseudonymisiert", { onNavigate("settings/privacy") }, highlighted = false)
                }
                // The project folder (Windows): one click opens it; created files land in its "Ausgabe".
                current.folderId?.let { c.folders.get(it) }?.takeIf { it.workDir.isNotBlank() && FolderAccess.SUPPORTED }?.let { project ->
                    ContextChip(
                        Icons.Outlined.FolderOpen,
                        if (project.workDirAccess) "Projektordner" else "Projektordner · nur ${io.github.thepytales.llmhub.agent.WorkFolder.OUTPUT}",
                        { FolderAccess.open(project.workDir) },
                        highlighted = false,
                    )
                }
                // The project's data protection level (models and tools are restricted accordingly).
                Privacy.level(c, current.folderId).takeIf { it != PrivacyLevel.OPEN }?.let { level ->
                    ContextChip(Icons.Outlined.Lock, level.short, { }, highlighted = true)
                }
            }
            }
            }
        },
    ) { pad ->
        if (items.isEmpty() && !running) {
            EmptyChat(
                hasApiKey = settings.hasAnyApiKey,
                userName = settings.userName,
                model = modelInfo,
                onModel = { showModelPicker = true },
                padding = pad,
                suggestions = TemplateLibrary.suggestions(settings.favoriteTemplates, templates),
                onTemplate = { useTemplate(it) },
                onApiKey = { onNavigate("settings/api") },
                onAllTemplates = { onNavigate("templates") },
                onOpen = onNavigate,
                typing = input.isNotBlank(),
                hideLora = settings.hideLora,
                extra = { MemoryIntroCard(c) },
                onExample = { e ->
                    when {
                        e.route != null -> onNavigate(e.route)
                        else -> {
                            if (e.literature) vm.setMode(Conversation.MODE_LITERATURE)
                            input = e.text
                        }
                    }
                },
            )
        } else {
            val last = current.thread().lastOrNull()
            // Arbeitsschritte einklappen: thinking and tools of a turn fold away; while the agent works, the figure shows.
            val collapse = settings.collapseSteps
            val runningHead = (items.lastOrNull() as? ChatItem.Assistant)?.head?.id.takeIf { running } ?: if (running) "new" else null
            val lastAnswerBlank = last == null || last.role != "assistant" || splitThinking(last.content).second.isBlank()
            val typing = running && if (collapse) lastAnswerBlank
                else (last == null || last.role != "assistant" || (last.content.isEmpty() && last.reasoning.isNullOrEmpty() && last.toolCalls.isEmpty()))
            val steps = remember(items) {
                val out = mutableMapOf<String, Pair<Boolean, Int>>()
                items.filterIsInstance<ChatItem.Assistant>().forEach { a ->
                    val (thinking, tools) = out[a.head.id] ?: (false to 0)
                    out[a.head.id] = (thinking || !a.m.reasoning.isNullOrBlank() || splitThinking(a.m.content).first != null) to tools + a.m.toolCalls.size
                }
                out
            }
            fun stepsOpen(head: String?) = head != null && openSteps[head] == true
            val shown = if (!collapse) items else items.filter { item ->
                if (item !is ChatItem.Assistant || stepsOpen(item.head.id)) return@filter true
                val turnRunning = item.head.id == runningHead
                val line = item.m.id == item.head.id && !(turnRunning && typing) && steps[item.head.id]?.let { it.first || it.second > 0 } == true
                line || (item.turnEnd && !(running && item.isLast)) || showsWithoutSteps(item.m, results, running)
            }
            val itemCount = shown.size + (if (typing) 1 else 0) + (if (building) 1 else 0)
            // Opened at the end; oldest message first, so the text being read stays put while an answer grows below it.
            val listState = remember(current.id) { LazyListState(firstVisibleItemIndex = (itemCount - 1).coerceAtLeast(0), firstVisibleItemScrollOffset = END) }
            val hits = remember(shown, searchQuery) { if (searchQuery.isBlank()) emptyList() else shown.indices.filter { i -> itemText(shown[i]).contains(searchQuery, ignoreCase = true) }.asReversed() }
            val currentHit = hits.getOrNull(searchHit.coerceAtMost((hits.size - 1).coerceAtLeast(0)))
            LaunchedEffect(currentHit) { currentHit?.let { follow.value = false; listState.animateScrollToItem(it) } }
            LaunchedEffect(current.id) { follow.value = true }
            ChatAutoScroll(listState, follow, remember(shown, typing, building, last?.content?.length, last?.toolCalls?.size, last?.reasoning?.length) { Any() }, itemCount)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_WIDTH).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(shown, key = { it.key }, contentType = { if (it is ChatItem.User) "user" else "assistant" }) { item ->
                    val hitHere = currentHit != null && shown.getOrNull(currentHit) === item
                    Box(
                        if (hitHere) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f), RoundedCornerShape(12.dp)).padding(4.dp)
                        else Modifier
                    ) {
                    when (item) {
                        is ChatItem.User -> UserBubble(
                            item = item,
                            running = running,
                            editing = editingId == item.m.id,
                            onStartEdit = { editingId = item.m.id },
                            onCancelEdit = { editingId = null },
                            onSubmitEdit = { text, keptFiles, keptImages -> editingId = null; vm.editMessage(item.m, text, keptFiles, keptImages) },
                            onSelectVersion = { vm.showVersion(it) },
                            onFork = { vm.fork(item.m)?.let { id -> onNavigate(chatRoute(id)) } },
                            onRemember = { text -> rememberFrom = text },
                        )
                        is ChatItem.Assistant -> AssistantMessage(
                            item = item,
                            results = results,
                            running = running,
                            streaming = running && item.m.id == last?.id,
                            playing = playingId == item.head.id,
                            onSpeak = { vm.speak(item.head.id, item.turnText) },
                            onExport = { text -> export = current.title to text },
                            onOpenInEditor = { text -> DocsHandoff.open(c, DocBlocks.titleOf(text) ?: current.title, text, current.id) },
                            onBibliography = { text ->
                                LiteratureHandoff.pending.value = LiteratureHandoff.Request(2, text)
                                onNavigate("literature")
                            },
                            onOpenLiterature = { list ->
                                LiteratureHandoff.pending.value = LiteratureHandoff.Request(1, list = list)
                                onNavigate("literature")
                            },
                            chatId = current.id,
                            onRegenerate = { item.question?.let { vm.regenerate(it) } },
                            onRegenerateWithModel = { regenerateFor = item.question },
                            onSelectVersion = { vm.showVersion(it) },
                            onRetry = { vm.retry() },
                            onRate = { value -> if (value == -1) ratingFor = item.head else vm.rate(item.head, value) },
                            onContinue = { vm.continueAnswer() },
                            onQuote = { text -> quoteFrom = text },
                            onFork = { vm.fork(item.head)?.let { id -> onNavigate(chatRoute(id)) } },
                            onRemember = { text -> rememberFrom = text },
                            fold = collapse && !stepsOpen(item.head.id),
                            stepsLine = steps[item.head.id]?.takeIf { collapse && item.m.id == item.head.id && (it.first || it.second > 0) && !(item.head.id == runningHead && typing && !stepsOpen(item.head.id)) }?.let { (thinking, tools) ->
                                { StepsLine(thinking, tools, stepsOpen(item.head.id)) { openSteps[item.head.id] = !stepsOpen(item.head.id) } }
                            },
                        )
                    }
                }
                    }
                if (building) item(key = "building") { ThinkingIndicator("Dokument wird erstellt …") }
                if (typing) item(key = "typing") {
                    if (collapse) {
                        val pendingCall = last?.toolCalls?.lastOrNull { results[it.id] == null }
                        val thinking = pendingCall == null && last?.role == "assistant" &&
                            (!last.reasoning.isNullOrBlank() || splitThinking(last.content).first != null)
                        val step = when {
                            pendingCall != null -> tr(toolLabel(pendingCall.name)) + " …"
                            thinking -> tr("Denkt nach …")
                            else -> null
                        }
                        val head = runningHead ?: "new"
                        WorkingCard(step, pendingCall?.name, thinking, steps[head]?.second ?: 0, stepsOpen(head), settings.hideLora) {
                            openSteps[head] = !stepsOpen(head)
                        }
                    } else ThinkingIndicator()
                }
            }
            // Back to the newest message (more than about one message above the end).
            val away by remember {
                derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < listState.layoutInfo.totalItemsCount - 2 }
            }
            if (away) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { scrollToEnd(listState); follow.value = true } },
                    containerColor = LocalChatLook.current.card,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = pad.calculateBottomPadding() + 12.dp),
                ) {
                    BadgedBox(badge = { if (running) Badge() }) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Zur neuesten Nachricht") }
                }
            }
            }
        }
    }
    }
    docPanel?.takeIf { sideBySide }?.let { id ->
        VerticalDivider()
        Box(Modifier.weight(1f)) { DocPane(c, id, onClose = { docPanel = null }, inSidePanel = true) }
    }
    }
    }

    if (imageWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { imageWarning = false },
            title = { Text("Bilder werden nicht pseudonymisiert") },
            text = {
                Text(
                    "In Bildern, Fotos und gescannten Seiten kann die App keine Namen ersetzen – der Server sieht sie so, wie sie sind. " +
                        "Ohne die Bilder geht nur der Text, pseudonymisiert. (Abschaltbar unter Einstellungen › Datenschutz.)"
                )
            },
            confirmButton = { TextButton(onClick = { imageWarning = false; send(true) }) { Text("Mit Bildern senden") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { imageWarning = false }) { Text("Abbrechen") }
                    TextButton(onClick = { imageWarning = false; send(false) }, enabled = input.isNotBlank() || files.isNotEmpty()) { Text("Ohne Bilder") }
                }
            },
        )
    }

    if (showPlus) {
        PlusSheet(
            repoLabel = repoLabel.takeIf { settings.hasGitHub },
            envLabel = env?.name ?: "Keine",
            personaLabel = current.persona ?: if (current.systemPrompt.isNullOrBlank()) "Keine" else "Eigener System-Prompt",
            onCamera = platform.primaryAttach,
            onPhotos = {
                if (modelInfo.vision == false) {
                    Notices.info("Hinweis: ${modelInfo.displayName} versteht keine Bilder.")
                }
                platform.pickPhotos()
            },
            onFiles = platform.pickFiles,
            onTemplates = { showTemplates = true },
            onPersona = { showSystemPrompt = true },
            onRepo = { showRepoPicker = true },
            onEnvironment = { showEnvPicker = true },
            onDismiss = { showPlus = false },
            onFromApp = { showFromApp = true },
            onSnippets = { showSnippets = true },
            onClipboard = {
                if (!platform.paste()) Notices.info("In der Zwischenablage ist kein Bild.")
                else if (modelInfo.vision == false) Notices.info("Hinweis: ${modelInfo.displayName} versteht keine Bilder.")
            },
        )
    }
    if (showSnippets) SnippetsSheet(c, onInsert = { t -> input = if (input.isBlank()) t else "$input\n$t" }, onDismiss = { showSnippets = false })
    if (showFromApp) FromAppSheet(c, onFile = { files.add(it) }, onImage = { images.add(it) }, onDismiss = { showFromApp = false })
    quoteFrom?.let { text -> QuoteDialog(text, onDismiss = { quoteFrom = null }) { quote -> input = quote + input; quoteFrom = null } }
    ratingFor?.let { m -> RatingNoteDialog(onDismiss = { ratingFor = null }) { note -> vm.rate(m, -1, note); ratingFor = null } }
    if (voiceMode) VoiceModeDialog(c, vm, platform, onClose = { voiceMode = false })
    autoAsk?.let { (choice, withImages) ->
        val suggested = ModelCatalog.info(choice.model).displayName
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { autoAsk = null },
            title = { Text(tr("Anderes Modell verwenden?")) },
            text = {
                Text(
                    tr("Für diese Anfrage passt") + " $suggested " + tr("besser") + " (${choice.reason}). " +
                        tr("Eingestellt ist gerade") + " ${modelInfo.displayName}."
                )
            },
            confirmButton = {
                TextButton(onClick = { autoAsk = null; vm.setModel(choice.model); sendNow(withImages) }) { Text(tr("Ja,") + " $suggested") }
            },
            dismissButton = {
                TextButton(onClick = { autoAsk = null; sendNow(withImages) }) { Text(tr("Nein,") + " ${modelInfo.displayName}") }
            },
        )
    }
    rememberFrom?.let { text -> MemoryEditDialog(c, null, text, current.folderId, onDismiss = { rememberFrom = null }) }
    if (showMemory) {
        MemoryChatDialog(
            c, current,
            onMemoryOff = { off -> vm.setMemoryOff(off) },
            onSettings = { onNavigate("settings/memory") },
            onDismiss = { showMemory = false },
        )
    }

    if (showTemplates) {
        TemplatePickerSheet(
            c = c,
            onPick = { useTemplate(it) },
            onManage = { onNavigate("templates") },
            onDismiss = { showTemplates = false },
        )
    }

    fillingId?.let { id -> TemplateLibrary.find(id, templates) }?.let { t ->
        TemplateFillDialog(
            c, t,
            onInsert = { text ->
                input = if (input.isBlank()) text else text + "\n\n" + input
                templateId = t.id
            },
            onSend = { text, values ->
                if (t.direct != null) {
                    if (running) {
                        c.drafts.upsert(io.github.thepytales.llmhub.data.TemplateDraft(t.id, values))
                        Notices.info("Die Antwort läuft noch – Ihre Angaben sind gespeichert, bitte gleich erneut erstellen.")
                    } else vm.sendDirect(text, values, t)
                } else if (running) {
                    // An answer is still being written: keep the text (and attachments) for later.
                    input = if (input.isBlank()) text else text + "\n\n" + input
                    templateId = t.id
                    Notices.info("Die Antwort läuft noch – der Text steht im Eingabefeld.")
                } else {
                    vm.send(text, images.toList(), files.toList(), t)
                    images.clear()
                    files.clear()
                }
            },
            onDismiss = { fillingId = null },
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = Privacy.allowedModels(c, current.folderId, modelsState.ofType(ModelType.CHAT)),
            selected = current.model,
            onSelect = { vm.setModel(it); vm.setAutoModel(false) },
            onDismiss = { showModelPicker = false },
            defaultModel = settings.defaultModel,
            note = "Gilt für diesen Chat. Mit welchem Modell neue Chats starten, legst du in den Einstellungen unter „Chat & Werkzeuge“ fest.",
            auto = current.autoModel,
            onAuto = { vm.setAutoModel(true) },
        )
    }

    regenerateFor?.let { question ->
        ModelPickerSheet(
            models = Privacy.allowedModels(c, current.folderId, modelsState.ofType(ModelType.CHAT)),
            selected = current.model,
            onSelect = { vm.regenerate(question, it) },
            onDismiss = { regenerateFor = null },
            defaultModel = settings.defaultModel,
            title = "Neu generieren mit …",
        )
    }

    if (showRepoPicker) {
        RepoPickerSheet(
            c = c,
            enabled = current.githubEnabled,
            selected = current.linkedRepos,
            hasToken = settings.hasGitHub,
            onApply = { enabled, repos -> vm.setGitHub(enabled, repos) },
            onDismiss = { showRepoPicker = false },
        )
    }

    if (showEnvPicker) {
        EnvironmentPickerDialog(
            c = c,
            current = current.envId,
            onPick = { vm.setEnvironment(it) },
            onManage = { onNavigate("environments") },
            onDismiss = { showEnvPicker = false },
        )
    }

    if (showSystemPrompt) {
        SystemPromptDialog(
            c = c,
            current = current.systemPrompt,
            onSave = { prompt, persona -> vm.setSystemPrompt(prompt, persona) },
            onDismiss = { showSystemPrompt = false },
        )
    }

    if (showRename) {
        TextInputDialog("Chat umbenennen", current.title, "Titel", onConfirm = { vm.rename(it) }, onDismiss = { showRename = false })
    }

    if (showFolder) {
        FolderPickerDialog(c, current.folderId, onPick = { vm.moveToFolder(it) }, onDismiss = { showFolder = false })
    }

    export?.let { (title, text) ->
        ExportDialog(c = c, title = title, markdown = text, onDismiss = { export = null })
    }

    if (showDelete) {
        ConfirmDialog("Chat löschen?", "„${current.title}“ kommt in den Papierkorb (30 Tage, Einstellungen › Daten).", "Löschen", onConfirm = { vm.deleteChat() }, onDismiss = { showDelete = false })
    }

    pending?.let { p ->
        ApprovalDialog(p, current.linkedRepos, onAnswer = { allow, always -> vm.answerPending(allow, always) })
    }
}

/** Maximum width of the messages and the input on wide screens (tablets, Windows), like the Claude desktop app. */
private val CONTENT_WIDTH = 860.dp
