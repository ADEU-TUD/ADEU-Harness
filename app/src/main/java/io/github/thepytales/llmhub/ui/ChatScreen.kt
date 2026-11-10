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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MenuBook
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    val allChats by c.chats.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    /** A question Lora prepared (Übergaben erst zeigen): its context and card go along with the next message. */
    var loraHandoff by remember { mutableStateOf<ChatHandoff.Content?>(null) }
    /** A mode handed over from the palette, the start page or another screen; started once the screen is ready (AP-05). */
    var pendingMode by remember { mutableStateOf<String?>(null) }
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
    var showAiNote by remember { mutableStateOf(false) }
    var showFolder by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var export by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val recorder = remember { Recorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var improving by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var showFromApp by remember { mutableStateOf(false) }
    var showNextcloud by remember { mutableStateOf(false) }
    // „+“ › Ordner: the folder being attached or changed (its right) – null while no dialog is open.
    var folderDialog by remember { mutableStateOf<io.github.thepytales.llmhub.data.LocalFolder?>(null) }
    var quoteFrom by remember { mutableStateOf<String?>(null) }
    var ratingFor by remember { mutableStateOf<ChatMessage?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHit by remember { mutableIntStateOf(0) }
    var chipsOpen by remember { mutableStateOf(false) }
    val snippets by c.snippets.items.collectAsStateWithLifecycle()
    val exportJson = rememberFileExport("application/json", onDone = { ok -> if (!ok) Notices.error(tr("Export fehlgeschlagen")) }) { out ->
        vm.state.value?.let { out.write(ChatJson.write(it).toByteArray(Charsets.UTF_8)) }
    }

    // Documents sent from the editor ("In Chat senden") wait in the input field of their chat.
    val handedOver by ChatHandoff.pending.collectAsStateWithLifecycle()
    LaunchedEffect(vm.chatId, handedOver) {
        ChatHandoff.takeFor(vm.chatId)?.let { h ->
            files.addAll(h.files)
            // A wish about this chat from Lora („Fasse diesen Chat zusammen“) is sent right away.
            if (h.send && h.text.isNotBlank() && !vm.running.value) {
                follow.value = true
                vm.send(h.text, emptyList(), h.files, instructions = h.instructions, handoff = h.handoff)
            } else if (h.text.isNotBlank() && input.isBlank()) input = h.text
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
        // „Literatur exzerpieren“ runs in the Exzerpt mode (AP-09): pages are checked, the excerpt lands at the literature entry.
        if (t.id == io.github.thepytales.llmhub.research.Excerpts.TEMPLATE_ID && vm.state.value?.mode == null) pendingMode = io.github.thepytales.llmhub.agent.ChatModes.EXCERPT.id
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
                h.mode?.let { pendingMode = it }
                // Postfach (AP-68): the mode and its local model are set before anything is sent.
                val deviceOnly = io.github.thepytales.llmhub.agent.ChatModes.byId(h.mode)?.onDevice == true
                if (deviceOnly) vm.enterMode(h.mode)
                // Lora chose the model: the chat keeps it (no automatic switch afterwards).
                h.model?.takeIf { m -> vm.state.value?.let { conv -> Privacy.allows(c, Privacy.level(c, conv), m) } != false }?.let { m ->
                    vm.setModel(m)
                    vm.setAutoModel(false)
                }
                // Without a model on this device the Postfach waits: the text stays in the input, the mode line offers a model.
                val waitForModel = deviceOnly && vm.state.value?.let { io.github.thepytales.llmhub.agent.MailAccess.onDevice(it.model) } != true
                if (h.send && h.text.isNotBlank() && !waitForModel) {
                    vm.send(h.text, h.images, h.files, instructions = h.instructions, handoff = h.handoff)
                } else {
                    if (h.text.isNotBlank()) input = if (input.isBlank()) h.text else input + "\n\n" + h.text
                    files.addAll(h.files)
                    images.addAll(h.images)
                    // „Übergaben erst zeigen“: the context goes along when the user sends.
                    if (h.handoff != null) loraHandoff = h
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

    /** Starts [mode] (AP-05): the Sprachmodus opens, other modes stay on for the chat (with their model in a new chat). */
    fun startMode(mode: io.github.thepytales.llmhub.agent.ChatMode) {
        val conv = vm.state.value ?: return
        if (mode.action) {
            if (mode.id == io.github.thepytales.llmhub.agent.ChatModes.VOICE.id) platform.withMicrophone { voiceMode = true }
            return
        }
        // A mode on this device only (Postfach, AP-68) brings the local model along.
        vm.enterMode(mode.id)
        mode.defaultModel?.takeIf { conv.messages.isEmpty() && Privacy.allows(c, Privacy.level(c, conv.folderId), it) }?.let { vm.setModel(it) }
    }
    LaunchedEffect(pendingMode) {
        pendingMode?.let { id -> io.github.thepytales.llmhub.agent.ChatModes.byId(id)?.let(::startMode) ?: vm.setMode(id) }
        pendingMode = null
    }

    fun startRecording() {
        try {
            recorder.start()
            recording = true
        } catch (e: Exception) {
            Notices.error(tr("Aufnahme nicht möglich: {0}", e.message))
        }
    }

    val current = conv
    /** Turns (by their first answer) whose folded steps the user opened. */
    val openSteps = remember(current?.id) { mutableStateMapOf<String, Boolean>() }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Tresor (AP-10): a vault chat shows nothing while the vault is locked; activity keeps it open.
    if (current.vault) {
        val vaultState by c.vault.state.collectAsStateWithLifecycle()
        if (vaultState != io.github.thepytales.llmhub.data.VaultStore.State.UNLOCKED) {
            VaultLockedNotice(onUnlock = { onNavigate("vault") })
            return
        }
        LaunchedEffect(input, current.messages.size, running) { c.vault.touch() }
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
    // Quellenmodus (AP-08): the evidence of every finished answer, checked against the sources of the chat.
    val evidence = remember(items, current.mode, running) { if (running) emptyMap() else evidenceReports(current, items) }
    val repoLabel = when {
        !current.githubEnabled -> tr("Aus")
        else -> current.linkedRepos.label() ?: tr("An – kein festes Repository")
    }

    val hasMessages = current.messages.isNotEmpty()
    // Lora knows which chat is open (for „Fasse diesen Chat zusammen“ and the privacy level of its project).
    LaunchedEffect(current.id, hasMessages, current.folderId) {
        LoraHelper.chatId = current.id
        LoraHelper.chatHasMessages = hasMessages
        LoraHelper.folderId = current.folderId
    }
    /** Sends the typed message; [withImages] false leaves the attached pictures out (after the privacy question). */
    fun sendNow(withImages: Boolean) {
        val pictures = if (withImages) images.toList() else emptyList()
        if (pictures.isNotEmpty() && modelInfo.vision == false) {
            Notices.info(tr("Hinweis: {0} versteht keine Bilder.", modelInfo.displayName))
        }
        follow.value = true
        val prepared = loraHandoff
        vm.send(input, pictures, files.toList(), activeTemplate, instructions = prepared?.instructions, handoff = prepared?.handoff)
        loraHandoff = null
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
            val github = current.githubEnabled && current.linkedRepos.isNotEmpty()
            // Mischmodus (AP-71): a short, simple task runs on this device when a model is installed that runs well
            // here – decided on the device; everything else gets the server model that fits, as before.
            val simple = c.commandModel.recognize(input)?.let { it.sure && it.label == io.github.thepytales.llmhub.agent.LoraCommands.FREE_SIMPLE } == true
            val localGood = io.github.thepytales.llmhub.agent.LoraCommands.localModel(c, good = true)
            val onDevice = localGood != null && !github && current.messages.isEmpty() && io.github.thepytales.llmhub.agent.ModelRouter.place(
                io.github.thepytales.llmhub.agent.ModelRouter.Mode.of(settings.loraPlace), simple, input, if (withImages) images.size else 0, files.size, localGood, localGood, serverOk = true,
            ).place == io.github.thepytales.llmhub.agent.ModelRouter.Place.LOCAL && io.github.thepytales.llmhub.agent.ModelRouter.Mode.of(settings.loraPlace) == io.github.thepytales.llmhub.agent.ModelRouter.Mode.MIXED
            val choice = if (onDevice) io.github.thepytales.llmhub.agent.ModelRouter.Choice(localGood!!, tr("kurze Aufgabe – läuft auf diesem Gerät"))
            else io.github.thepytales.llmhub.agent.ModelRouter.suggest(input, if (withImages) images.size else 0, files.toList(), github, settings.defaultModel)
            val usable = Privacy.allows(c, Privacy.level(c, current), choice.model) && (onDevice || modelsState.liveIds?.contains(choice.model) != false)
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
    val narrow = maxWidth < 640.dp
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
                    if (onOpenDrawer != null) IconButton(onClick = onOpenDrawer) { Icon(Icons.Outlined.Menu, contentDescription = tr("Menü")) }
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
                    // Lora, the helper (Strg+L): her panel with suggestions for this chat – „Frag Lora“ with her face; only the
                    // face on narrow windows with a chat title.
                    LoraTopBarButton(c, current.folderId, compact = hasMessages && narrow)
                    if (!hasMessages) {
                        // Temporary chat: nothing is saved (like the incognito mode of other apps).
                        IconButton(onClick = {
                            vm.setTemporary(!current.temporary)
                            Notices.info(if (!current.temporary) tr("Temporärer Chat: wird nicht gespeichert.") else tr("Chat wird wieder gespeichert."))
                        }) {
                            Icon(
                                if (current.temporary) Icons.Filled.VisibilityOff else Icons.Outlined.VisibilityOff,
                                contentDescription = if (current.temporary) tr("Temporären Chat beenden") else tr("Temporärer Chat"),
                                tint = if (current.temporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // On a fresh, ordinary chat a "new chat" button would only start the same again.
                    if (hasMessages || current.folderId != null || current.temporary) {
                        IconButton(onClick = onNewChat) {
                            Icon(Icons.Filled.AddComment, contentDescription = tr("Neuer Chat"), modifier = Modifier.size(26.dp))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
                        ChatMenu(
                            open = menuOpen,
                            title = if (hasMessages) current.title else tr("Neuer Chat"),
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
                                if (!current.archived) { Notices.undo(tr("Chat archiviert"), restore = { vm.setArchived(false) }); onNewChat() }
                            },
                            onAiNote = { showAiNote = true },
                            onFiles = conv?.takeIf { cv -> !cv.vault && cv.messages.any { m -> m.file != null || m.files.isNotEmpty() || m.images.isNotEmpty() } }?.let { cv ->
                                { FilesHandoff.chat.value = cv.id; onNavigate("files") }
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
            Box(Modifier.widthIn(max = CONTENT_WIDTH).onGloballyPositioned { LoraFlight.composer = it.boundsInRoot().center }) {
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
                onToggleFilePublic = { f ->
                    val i = files.indexOf(f)
                    if (i >= 0) files[i] = f.copy(public = !io.github.thepytales.llmhub.data.Pseudonymizer.isPublic(f))
                },
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
                                Notices.undo(tr("Anfrage verbessert"), restore = { input = before })
                            }
                            .onFailure { Notices.error(tr("Verbessern nicht möglich: {0}", it.message)) }
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
                                startMode = { m -> startMode(m) },
                                openDocs = { onNavigate("files") },
                            )
                        }
                    )
                    // The frame of the active mode (AP-05): chip with ✕ and one line „what happens · Daten“.
                    io.github.thepytales.llmhub.agent.ChatModes.byId(current.mode)?.let { m -> ModeBanner(m) { vm.setMode(null) } }
                    // Postfach nur mit lokalem Modell (AP-68): which model reads the mails, or one to download.
                    if (io.github.thepytales.llmhub.agent.MailAccess.locked(current)) {
                        MailModelNotice(c, current.model, onModel = { vm.setModel(it); vm.setAutoModel(false) }, onOpenModels = { onNavigate("local-models") })
                    }
                    // Prepared by Lora (Übergaben erst zeigen): what goes along, with ✕ to leave it out.
                    loraHandoff?.let { h -> LoraPreparedBanner(h.handoff.orEmpty()) { loraHandoff = null } }
                    // While typing: the place of the app that fits („dunkles Design“ → Darstellung › Helligkeit).
                    LoraTypingHint(c, input, onUsed = { input = "" })
                    PrivacySuggestions(c, input)
                    ToolSuggestion(c, input, files.map { it.name })
                    MemoryLearnBar(c, current.id)
                    // KI-Nutzungsnachweis (AP-19): offered once a chat about a work has text written by the AI.
                    AiUseNoteSuggestion(c, current, running) { showAiNote = true }
                },
                footnote = if (hasMessages) tr("KI kann Fehler machen. Wichtiges bitte prüfen.") else null,
                onVoice = { platform.withMicrophone { voiceMode = true } },
            ) {
                if (current.temporary) ContextChip(Icons.Outlined.VisibilityOff, tr("Temporär – wird nicht gespeichert"), { }, highlighted = true)
                // Always visible while active: the selected repository, environment and persona.
                if (current.githubEnabled && settings.hasGitHub) {
                    // Quiet while nothing is chosen; highlighted once a repository is linked.
                    val repos = current.linkedRepos.label()
                    ContextChip(Icons.Outlined.Code, repos ?: tr("GitHub · Repo wählen"), { showRepoPicker = true }, highlighted = repos != null)
                }
                if (env != null) ContextChip(Icons.Outlined.Tune, env.name, { showEnvPicker = true }, highlighted = env.id != settings.defaultEnvId)
                activeTemplate?.let { t ->
                    ContextChip(Icons.Outlined.Bookmarks, tr("Vorlage: {0}  ✕", tr(t.name)), { templateId = null })
                }
                if (current.autoApprove) {
                    ContextChip(Icons.Outlined.DoneAll, tr("Ohne Rückfrage"), {
                        vm.setAutoApprove(false)
                        Notices.info(tr("Aktionen in diesem Chat werden wieder bestätigt."))
                    })
                }
                if (!current.systemPrompt.isNullOrBlank()) {
                    ContextChip(Icons.Outlined.Person, current.persona ?: tr("System-Prompt"), { showSystemPrompt = true })
                }
                // Folders attached to the chat: their right at a glance, a tap changes it or removes the folder.
                current.folders.forEach { f ->
                    ContextChip(Icons.Outlined.FolderOpen, f.name + " · " + tr(if (f.write) "liest und schreibt" else "nur lesen"), { folderDialog = f }, highlighted = f.write)
                }
                // The memory goes along automatically; the chip shows how much and switches it off for this chat.
                MemoryChip(c, current) { showMemory = true }
                // Shows that names and contact details leave the device only as placeholders.
                if (settings.pseudonymize) {
                    ContextChip(Icons.Outlined.Shield, tr("Pseudonymisiert"), { onNavigate("settings/privacy") }, highlighted = false)
                }
                // The project folder: one click opens it; created files land in its "Ausgabe".
                current.folderId?.let { c.folders.get(it) }?.takeIf { it.workDir.isNotBlank() && PROJECT_FOLDERS }?.let { project ->
                    ContextChip(
                        Icons.Outlined.FolderOpen,
                        if (project.workDirAccess) tr("Projektordner · liest mit") else tr("Projektordner · nur {0}", io.github.thepytales.llmhub.agent.WorkFolder.OUTPUT),
                        { openProjectFolder(c.appContext, project.workDir) },
                        highlighted = false,
                    )
                }
                // The project's data protection level (models and tools are restricted accordingly).
                // A Postfach chat (AP-68) is fixed to „Nur auf diesem Gerät“.
                (if (io.github.thepytales.llmhub.agent.MailAccess.locked(current)) PrivacyLevel.LOCAL else Privacy.level(c, current.folderId)).takeIf { it != PrivacyLevel.OPEN }?.let { level ->
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
                actions = startActions(
                    onInput = { text -> input = text },
                    onNavigate = onNavigate,
                    onFiles = { prompt -> platform.pickFiles(); input = prompt },
                    onMode = { id, text -> io.github.thepytales.llmhub.agent.ChatModes.byId(id)?.let(::startMode); input = text },
                    templates = TemplateLibrary.suggestions(settings.favoriteTemplates, templates),
                    onTemplate = { useTemplate(it) },
                ),
                recent = remember(allChats) { allChats.filter { !it.archived && it.id != current.id }.sortedByDescending { it.updatedAt }.take(3) },
                favorites = remember(settings.favoriteTemplates, templates) {
                    settings.favoriteTemplates.mapNotNull { TemplateLibrary.find(it, templates) }.take(4)
                },
                onOpenChat = { id -> onNavigate(chatRoute(id)) },
                mode = io.github.thepytales.llmhub.agent.ChatModes.byId(current.mode),
                onModeExample = { text -> input = text },
                onMode = { m -> startMode(m) },
                onExample = { e ->
                    when {
                        e.route != null -> onNavigate(e.route)
                        else -> {
                            e.mode?.let { id -> io.github.thepytales.llmhub.agent.ChatModes.byId(id)?.let(::startMode) }
                            input = tr(e.text)
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
                            // An Chat übergeben (AP-68): what the local model wrote, checked first on a card.
                            onHandoff = if (io.github.thepytales.llmhub.agent.MailAccess.locked(current)) {
                                { text -> io.github.thepytales.llmhub.agent.MailHandoff.offer(c, text, current.id) }
                            } else null,
                            onAnswer = { text -> follow.value = true; vm.send(text, emptyList(), emptyList()) },
                            evidence = evidence[item.m.id],
                            fold = collapse && !stepsOpen(item.head.id),
                            stepsLine = steps[item.head.id]?.takeIf { collapse && item.m.id == item.head.id && (it.first || it.second > 0) && !(item.head.id == runningHead && typing && !stepsOpen(item.head.id)) }?.let { (thinking, tools) ->
                                { StepsLine(thinking, tools, stepsOpen(item.head.id)) { openSteps[item.head.id] = !stepsOpen(item.head.id) } }
                            },
                        )
                    }
                }
                    }
                if (building) item(key = "building") { ThinkingIndicator(tr("Dokument wird erstellt …")) }
                if (typing) item(key = "typing") {
                    if (collapse) {
                        val pendingCall = last?.toolCalls?.lastOrNull { results[it.id] == null }
                        val thinking = pendingCall == null && last?.role == "assistant" &&
                            (!last.reasoning.isNullOrBlank() || splitThinking(last.content).first != null)
                        // A readable line for the step (AP-35): „Sucht im Web: „…““ instead of the bare tool name.
                        val step = when {
                            pendingCall != null -> io.github.thepytales.llmhub.agent.LoraSense.stepLine(pendingCall.name, pendingCall.arguments, tr(toolLabel(pendingCall.name)))
                            thinking -> tr("Denkt nach …")
                            last?.role == "assistant" && splitThinking(last.content).second.isNotBlank() -> tr("Schreibt die Antwort …")
                            (steps[runningHead ?: "new"]?.second ?: 0) > 0 -> tr("Wertet die Ergebnisse aus …")
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
                    BadgedBox(badge = { if (running) Badge() }) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = tr("Zur neuesten Nachricht")) }
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
            title = { Text(tr("Bilder werden nicht pseudonymisiert")) },
            text = {
                Text(
                    tr("In Bildern, Fotos und gescannten Seiten kann die App keine Namen ersetzen – der Server sieht sie so, wie sie sind. " +
                        "Ohne die Bilder geht nur der Text, pseudonymisiert. (Abschaltbar unter Einstellungen › Datenschutz.)")
                )
            },
            confirmButton = { TextButton(onClick = { imageWarning = false; send(true) }) { Text(tr("Mit Bildern senden")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { imageWarning = false }) { Text(tr("Abbrechen")) }
                    TextButton(onClick = { imageWarning = false; send(false) }, enabled = input.isNotBlank() || files.isNotEmpty()) { Text(tr("Ohne Bilder")) }
                }
            },
        )
    }

    if (showPlus) {
        PlusSheet(
            repoLabel = repoLabel.takeIf { settings.hasGitHub },
            envLabel = env?.name ?: tr("Keine"),
            personaLabel = current.persona ?: if (current.systemPrompt.isNullOrBlank()) tr("Keine") else tr("Eigener System-Prompt"),
            onCamera = platform.primaryAttach,
            onPhotos = {
                if (modelInfo.vision == false) {
                    Notices.info(tr("Hinweis: {0} versteht keine Bilder.", modelInfo.displayName))
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
            onNextcloud = if (settings.hasNextcloud && !current.vault) ({ showNextcloud = true }) else null,
            onFolder = if (FolderAccess.SUPPORTED && !current.vault) ({
                scope.launch {
                    val path = FolderAccess.pick(tr("Ordner an den Chat anhängen")) ?: return@launch
                    if (current.folders.any { java.io.File(it.path).absoluteFile == java.io.File(path).absoluteFile }) Notices.info(tr("Dieser Ordner hängt schon am Chat."))
                    else folderDialog = io.github.thepytales.llmhub.data.LocalFolder(id = "chat-" + java.util.UUID.randomUUID().toString().take(6), name = chatFolderName(current.folders, path), path = path, write = false)
                }
            }) else null,
            onClipboard = {
                if (!platform.paste()) Notices.info(tr("In der Zwischenablage ist kein Bild."))
                else if (modelInfo.vision == false) Notices.info(tr("Hinweis: {0} versteht keine Bilder.", modelInfo.displayName))
            },
        )
    }
    folderDialog?.let { f ->
        ChatFolderDialog(f, isNew = current.folders.none { it.id == f.id }, onDismiss = { folderDialog = null }, onRemove = {
            vm.setChatFolders(current.folders.filter { it.id != f.id })
            Notices.undo(tr("Ordner „{0}“ vom Chat gelöst (der Ordner selbst bleibt)", f.name), restore = { vm.setChatFolders(current.folders + f) })
        }) { changed ->
            vm.setChatFolders(if (current.folders.any { it.id == changed.id }) current.folders.map { if (it.id == changed.id) changed else it } else current.folders + changed)
            folderDialog = null
        }
    }
    if (showSnippets) SnippetsSheet(c, onInsert = { t -> input = if (input.isBlank()) t else "$input\n$t" }, onDismiss = { showSnippets = false })
    if (showFromApp) FromAppSheet(c, onFile = { files.add(it) }, onImage = { images.add(it) }, onDismiss = { showFromApp = false })
    if (showNextcloud) NextcloudAttachDialog(c, onFile = { files.add(it) }, onDismiss = { showNextcloud = false })
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
                    tr("Für diese Anfrage passt {0} besser ({1}). Eingestellt ist gerade {2}.", suggested, choice.reason, modelInfo.displayName)
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
                        Notices.info(tr("Die Antwort läuft noch – Ihre Angaben sind gespeichert, bitte gleich erneut erstellen."))
                    } else vm.sendDirect(text, values, t)
                } else if (running) {
                    // An answer is still being written: keep the text (and attachments) for later.
                    input = if (input.isBlank()) text else text + "\n\n" + input
                    templateId = t.id
                    Notices.info(tr("Die Antwort läuft noch – der Text steht im Eingabefeld."))
                } else {
                    vm.send(text, images.toList(), files.toList(), t)
                    images.clear()
                    files.clear()
                }
            },
            onDismiss = { fillingId = null },
            onInterview = { tpl, known ->
                if (running) Notices.info(tr("Die Antwort läuft noch – bitte gleich noch einmal."))
                else {
                    follow.value = true
                    vm.send(
                        io.github.thepytales.llmhub.agent.TemplateInterview.request(tpl), images.toList(), files.toList(),
                        instructions = io.github.thepytales.llmhub.agent.TemplateInterview.instructions(tpl, known),
                    )
                    images.clear()
                    files.clear()
                }
            },
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = modelsState.ofType(ModelType.CHAT).filter { Privacy.allows(c, Privacy.level(c, current), it.id) },
            selected = current.model,
            onSelect = { vm.setModel(it); vm.setAutoModel(false) },
            onDismiss = { showModelPicker = false },
            defaultModel = settings.defaultModel,
            note = tr("Gilt für diesen Chat. Mit welchem Modell neue Chats starten, legst du in den Einstellungen unter „Chat & Werkzeuge“ fest."),
            auto = current.autoModel,
            onAuto = { vm.setAutoModel(true) },
        )
    }

    regenerateFor?.let { question ->
        ModelPickerSheet(
            models = modelsState.ofType(ModelType.CHAT).filter { Privacy.allows(c, Privacy.level(c, current), it.id) },
            selected = current.model,
            onSelect = { vm.regenerate(question, it) },
            onDismiss = { regenerateFor = null },
            defaultModel = settings.defaultModel,
            title = tr("Neu generieren mit …"),
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

    if (showAiNote) AiUseNoteDialog(c, current) { showAiNote = false }

    if (showRename) {
        TextInputDialog(tr("Chat umbenennen"), current.title, tr("Titel"), onConfirm = { vm.rename(it) }, onDismiss = { showRename = false })
    }

    if (showFolder) {
        FolderPickerDialog(c, current.folderId, onPick = { vm.moveToFolder(it) }, onDismiss = { showFolder = false })
    }

    export?.let { (title, text) ->
        ExportDialog(c = c, title = title, markdown = text, onDismiss = { export = null })
    }

    if (showDelete) {
        ConfirmDialog(tr("Chat löschen?"), tr("„{0}“ kommt in den Papierkorb (30 Tage, Einstellungen › Daten).", current.title), tr("Löschen"), onConfirm = { vm.deleteChat() }, onDismiss = { showDelete = false })
    }

    pending?.let { p ->
        ApprovalDialog(p, current.linkedRepos, onAnswer = { allow, always -> vm.answerPending(allow, always) })
    }
}

/**
 * The activities of the start page (AP-02) with concrete starts: what one can actually ask, so nobody has to think up a
 * first sentence. A start fills the input (the user completes and sends it) or opens the place.
 */
internal fun startActions(
    onInput: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onFiles: (String) -> Unit,
    onMode: (String, String) -> Unit,
    templates: List<io.github.thepytales.llmhub.data.Template>,
    onTemplate: (io.github.thepytales.llmhub.data.Template) -> Unit,
) = listOf(
    StartAction(
        tr("Schreiben"), Icons.Outlined.EditNote,
        listOf(
            StartExample(tr("E-Mail formulieren")) { onInput(tr("Formuliere eine freundliche E-Mail an ")) },
            StartExample(tr("Text kürzen und verständlicher machen")) { onInput(tr("Mach diesen Text kürzer und verständlicher:\n")) },
            StartExample(tr("Ins Englische übersetzen")) { onInput(tr("Übersetze ins Englische:\n")) },
            StartExample(tr("Ideen sammeln")) { onInput(tr("Sammle Ideen für ")) },
        ),
    ),
    StartAction(
        tr("Dokument erstellen"), Icons.Outlined.Description,
        listOf(
            StartExample(tr("Protokoll aus Notizen")) { onInput(tr("Erstelle ein Protokoll aus diesen Notizen:\n")) },
            StartExample(tr("Handout oder Merkblatt")) { onInput(tr("Entwirf ein Handout zum Thema ")) },
            StartExample(tr("Präsentation")) { onInput(tr("Erstelle eine Präsentation zum Thema ")) },
            StartExample(tr("Diagramm aus Daten")) { onInput(tr("Erstelle ein Diagramm aus diesen Daten:\n")) },
        ),
    ),
    StartAction(
        tr("Dateien auswerten"), Icons.Outlined.AttachFile,
        listOf(
            StartExample(tr("Zusammenfassen")) { onFiles(tr("Fasse die angehängten Dateien zusammen.")) },
            StartExample(tr("Fragen zu den Dateien stellen")) { onFiles(tr("Beantworte anhand der angehängten Dateien: ")) },
            StartExample(tr("Tabelle auswerten")) { onFiles(tr("Werte die angehängte Tabelle aus: ")) },
            StartExample(tr("Dokumente vergleichen")) { onFiles(tr("Vergleiche die angehängten Dokumente: ")) },
        ),
    ),
    StartAction(
        tr("Literatur finden"), Icons.Outlined.MenuBook,
        listOf(
            StartExample(tr("Aktuelle Literatur zu einem Thema")) { onMode("literature", tr("Finde aktuelle Literatur zum Thema: ")) },
            StartExample(tr("Antwort nur mit Belegen (Quellenmodus)")) { onMode("sources", "") },
            StartExample(tr("Quellenangaben eines Textes prüfen")) { onInput(tr("Prüfe die Quellenangaben in diesem Text:\n")) },
            StartExample(tr("Literaturverzeichnis ordnen")) { onNavigate("literature") },
        ),
    ),
    StartAction(
        tr("TU Vorlagen"), Icons.Outlined.Bookmarks,
        templates.take(3).map { t -> StartExample(tr(t.name)) { onTemplate(t) } } + StartExample(tr("Alle TU Vorlagen ansehen")) { onNavigate("templates") },
    ),
)

/** Maximum width of the messages and the input on wide screens (tablets, Windows), like the Claude desktop app. */
private val CONTENT_WIDTH = 860.dp
