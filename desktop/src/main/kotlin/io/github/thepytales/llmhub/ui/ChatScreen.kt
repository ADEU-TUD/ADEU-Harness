package io.github.thepytales.llmhub.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.data.label
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.DroppedFiles
import io.github.thepytales.llmhub.desktop.Images
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    c: AppContainer,
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit,
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

    LaunchedEffect(vm) {
        vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        c.speaker.errors.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
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
                if (h.text.isNotBlank()) input = if (input.isBlank()) h.text else input + "\n\n" + h.text
                files.addAll(h.files)
                images.addAll(h.images)
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

    val imageExtensions = listOf("png", "jpg", "jpeg", "gif", "bmp")

    /** Pictures go to the vision model, everything else is read as document. */
    fun attach(picked: List<File>) {
        picked.forEach { file ->
            loadingFiles++
            scope.launch {
                if (file.extension.lowercase() in imageExtensions) {
                    val img = encodeImage(file)
                    loadingFiles--
                    if (img != null) images.add(img) else Toast.makeText(context, "„${file.name}“ konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
                } else {
                    val r = AttachmentReader.read(file)
                    loadingFiles--
                    r.file?.let { files.add(it) }
                    images.addAll(r.images)
                    (r.error ?: r.note)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    fun pickImages() = scope.launch {
        val picked = withContext(Dispatchers.IO) { DesktopActions.pickFiles("Bilder auswählen", extensions = imageExtensions) }
        attach(picked.take(6))
    }

    fun pickFiles() = scope.launch {
        attach(withContext(Dispatchers.IO) { DesktopActions.pickFiles("Dateien auswählen") })
    }

    /** Screenshot or copied files from the clipboard; false if there is neither. */
    fun pasteFromClipboard(): Boolean {
        val img = DesktopActions.clipboardImage()
        if (img != null) {
            images.add(Images.jpegDataUrl(img))
            return true
        }
        val copied = DesktopActions.clipboardFiles()
        if (copied.isNotEmpty()) {
            attach(copied)
            return true
        }
        return false
    }

    LaunchedEffect(Unit) {
        DroppedFiles.files.collect { attach(it) }
    }

    fun startRecording() {
        try {
            recorder.start()
            recording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Aufnahme nicht möglich: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val current = conv
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // No notification for the chat on screen; opening it marks its result as read.
    DisposableEffect(current.id) {
        c.runs.visibleChatId = current.id
        onDispose { if (c.runs.visibleChatId == current.id) c.runs.visibleChatId = null }
    }
    LaunchedEffect(current.id) {
        vm.markRead()
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Outlined.Menu, contentDescription = "Menü") }
                },
                title = {
                    if (hasMessages) {
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
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Filled.AddComment, contentDescription = "Neuer Chat", modifier = Modifier.size(26.dp))
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
                            onExport = { export = current.title to chatToMarkdown(current) },
                            onPersona = { showSystemPrompt = true },
                            onEnvironment = { showEnvPicker = true },
                            autoApprove = current.autoApprove,
                            onAutoApprove = { vm.setAutoApprove(!current.autoApprove) },
                            onDelete = { showDelete = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Wide windows: the chat stays readable in a centered column, like the Claude desktop app.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = CONTENT_WIDTH)) {
            Composer(
                input = input,
                onInput = { input = it },
                placeholder = if (hasMessages) "Antworten …" else "Wie kann ich helfen?",
                modelLabel = modelInfo.displayName,
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
                        startRecording()
                    }
                },
                onSend = {
                    if (images.isNotEmpty() && modelInfo.vision == false) {
                        Toast.makeText(context, "Hinweis: ${modelInfo.displayName} versteht keine Bilder.", Toast.LENGTH_LONG).show()
                    }
                    vm.send(input, images.toList(), files.toList(), activeTemplate)
                    input = ""
                    templateId = null
                    images.clear()
                    files.clear()
                },
                onStop = { vm.stop() },
                onPaste = { pasteFromClipboard() },
            ) {
                // Always visible while active: the selected repository, environment and persona.
                if (current.githubEnabled && settings.hasGitHub) {
                    ContextChip(Icons.Outlined.Code, current.linkedRepos.label() ?: "GitHub · Repo wählen", { showRepoPicker = true })
                }
                if (env != null) ContextChip(Icons.Outlined.Tune, env.name, { showEnvPicker = true })
                activeTemplate?.let { t ->
                    ContextChip(Icons.Outlined.Bookmarks, "Vorlage: ${t.name}  ✕", { templateId = null })
                }
                if (current.autoApprove) {
                    ContextChip(Icons.Outlined.DoneAll, "Ohne Rückfrage", {
                        vm.setAutoApprove(false)
                        Toast.makeText(context, "Aktionen in diesem Chat werden wieder bestätigt.", Toast.LENGTH_SHORT).show()
                    })
                }
                if (!current.systemPrompt.isNullOrBlank()) {
                    ContextChip(Icons.Outlined.Person, current.persona ?: "System-Prompt", { showSystemPrompt = true })
                }
                // Shows that names and contact details leave the device only as placeholders.
                if (settings.pseudonymize) {
                    ContextChip(Icons.Outlined.Shield, "Pseudonymisiert", { onNavigate("settings/privacy") }, highlighted = false)
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
            )
        } else {
            val listState = rememberLazyListState()
            val reversed = remember(items) { items.asReversed() }
            val last = current.thread().lastOrNull()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxHeight().widthIn(max = CONTENT_WIDTH).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (running && (last == null || last.role != "assistant" ||
                        (last.content.isEmpty() && last.reasoning.isNullOrEmpty() && last.toolCalls.isEmpty()))
                ) {
                    item(key = "typing") { ThinkingIndicator() }
                }
                if (building) item(key = "building") { ThinkingIndicator("Dokument wird erstellt …") }
                items(reversed, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.User -> UserBubble(
                            item = item,
                            running = running,
                            editing = editingId == item.m.id,
                            onStartEdit = { editingId = item.m.id },
                            onCancelEdit = { editingId = null },
                            onSubmitEdit = { text -> editingId = null; vm.editMessage(item.m, text) },
                            onSelectVersion = { vm.showVersion(it) },
                        )
                        is ChatItem.Assistant -> AssistantMessage(
                            item = item,
                            results = results,
                            running = running,
                            streaming = running && item.m.id == last?.id,
                            playing = playingId == item.head.id,
                            onSpeak = { vm.speak(item.head.id, item.turnText) },
                            onExport = { text -> export = current.title to text },
                            onRegenerate = { item.question?.let { vm.regenerate(it) } },
                            onRegenerateWithModel = { regenerateFor = item.question },
                            onSelectVersion = { vm.showVersion(it) },
                            onRetry = { vm.retry() },
                        )
                    }
                }
            }
            }
        }
    }

    if (showPlus) {
        PlusSheet(
            repoLabel = repoLabel.takeIf { settings.hasGitHub },
            envLabel = env?.name ?: "Keine",
            personaLabel = current.persona ?: if (current.systemPrompt.isNullOrBlank()) "Keine" else "Eigener System-Prompt",
            onCamera = {
                if (!pasteFromClipboard()) {
                    Toast.makeText(context, "Die Zwischenablage enthält kein Bild und keine Dateien.", Toast.LENGTH_SHORT).show()
                }
            },
            onPhotos = {
                if (modelInfo.vision == false) {
                    Toast.makeText(context, "Hinweis: ${modelInfo.displayName} versteht keine Bilder.", Toast.LENGTH_SHORT).show()
                }
                pickImages()
            },
            onFiles = { pickFiles() },
            onTemplates = { showTemplates = true },
            onPersona = { showSystemPrompt = true },
            onRepo = { showRepoPicker = true },
            onEnvironment = { showEnvPicker = true },
            onDismiss = { showPlus = false },
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
                        Toast.makeText(context, "Die Antwort läuft noch – Ihre Angaben sind gespeichert, bitte gleich erneut erstellen.", Toast.LENGTH_LONG).show()
                    } else vm.sendDirect(text, values, t)
                } else if (running) {
                    // An answer is still being written: keep the text (and attachments) for later.
                    input = if (input.isBlank()) text else text + "\n\n" + input
                    templateId = t.id
                    Toast.makeText(context, "Die Antwort läuft noch – der Text steht im Eingabefeld.", Toast.LENGTH_SHORT).show()
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
            onSelect = { vm.setModel(it) },
            onDismiss = { showModelPicker = false },
            defaultModel = settings.defaultModel,
            note = "Gilt für diesen Chat. Mit welchem Modell neue Chats starten, legst du in den Einstellungen unter „Chat & Werkzeuge“ fest.",
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

private fun greeting(name: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        in 18..22 -> "Guten Abend"
        else -> "Hallo"
    }
    return if (name.isBlank()) hello else "$hello, ${name.trim()}"
}

/** Start of a new chat like in the Claude app: greeting, the chosen model and suggestions. */
@Composable
private fun EmptyChat(
    hasApiKey: Boolean,
    userName: String,
    model: ModelInfo,
    onModel: () -> Unit,
    padding: PaddingValues,
    suggestions: List<io.github.thepytales.llmhub.data.Template>,
    onTemplate: (io.github.thepytales.llmhub.data.Template) -> Unit,
    onApiKey: () -> Unit,
    onAllTemplates: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val look = LocalChatLook.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = look.brand, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(14.dp))
        Text(
            greeting(userName),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = look.titleFont),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            model.displayName + (model.useCase.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onModel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (!hasApiKey) {
            Spacer(Modifier.size(20.dp))
            WelcomeCard(onApiKey = onApiKey, onNavigate = onOpen)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { t ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = look.card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .widthIn(min = 150.dp, max = 200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTemplate(t) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(
                            t.description.ifBlank { t.content },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // The whole library is one tap away, not only the favourites.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = look.card,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .widthIn(min = 150.dp, max = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onAllTemplates),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Alle Vorlagen ›", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(
                        "Lehre, Forschung, Betreuung, TUD-Dokumente",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.heightIn(min = 52.dp),
    )
}

/** Divider between the groups of the chat menu, optionally with a small heading. */
@Composable
private fun MenuGroup(title: String?) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    if (title != null) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
        )
    }
}

/** Chat menu like the Claude app: title on top, big entries, "Löschen" in red. */
@Composable
private fun ChatMenu(
    open: Boolean,
    title: String,
    hasMessages: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onFolder: () -> Unit,
    onExport: () -> Unit,
    onPersona: () -> Unit,
    onEnvironment: () -> Unit,
    autoApprove: Boolean,
    onAutoApprove: () -> Unit,
    onDelete: () -> Unit,
) {
    fun pick(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = LocalChatLook.current.card,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 260.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
        )
        MenuEntry(Icons.Outlined.IosShare, "Kopieren (zum Teilen)", hasMessages, onClick = pick(onShare))
        MenuEntry(Icons.Outlined.Description, "Als PDF/Word exportieren", hasMessages, onClick = pick(onExport))
        MenuGroup(null)
        MenuEntry(Icons.Outlined.Edit, "Umbenennen", hasMessages, onClick = pick(onRename))
        MenuEntry(Icons.Outlined.PushPin, if (pinned) "Lösen" else "Anheften", hasMessages, onClick = pick(onPin))
        MenuEntry(Icons.Outlined.DriveFileMove, "In Projekt verschieben", hasMessages, onClick = pick(onFolder))
        MenuGroup("Für diesen Chat")
        MenuEntry(Icons.Outlined.Person, "Anweisungen & Persona", onClick = pick(onPersona))
        MenuEntry(Icons.Outlined.Tune, "Umgebung", onClick = pick(onEnvironment))
        MenuEntry(
            Icons.Outlined.DoneAll,
            if (autoApprove) "Aktionen wieder bestätigen" else "Aktionen ohne Rückfrage",
            onClick = pick(onAutoApprove),
        )
        MenuGroup(null)
        MenuEntry(Icons.Outlined.Delete, "Löschen", hasMessages, danger = true, onClick = pick(onDelete))
    }
}

/** Maximum width of the messages and the input on wide windows. */
private val CONTENT_WIDTH = 860.dp
