package io.github.thepytales.llmhub.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.files.AttachmentReader
import kotlinx.coroutines.launch
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

    var input by rememberSaveable { mutableStateOf(vm.prefill.orEmpty()) }
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
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(vm) {
        vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(Unit) {
        c.speaker.errors.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(deleted) {
        if (deleted) onNewChat()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        scope.launch { uris.forEach { uri -> encodeImage(context, uri)?.let { images.add(it) } } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) scope.launch { encodeImage(context, uri)?.let { images.add(it) } }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            loadingFiles++
            scope.launch {
                val r = AttachmentReader.read(context, uri)
                loadingFiles--
                r.file?.let { files.add(it) }
                images.addAll(r.images)
                (r.error ?: r.note)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun takePhoto() {
        try {
            val f = File(context.cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", f)
            cameraUri = uri
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Keine Kamera-App gefunden.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecording() {
        try {
            recorder.start()
            recording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Aufnahme nicht möglich: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Mikrofon-Berechtigung benötigt.", Toast.LENGTH_SHORT).show()
    }

    val current = conv
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val modelInfo = ModelCatalog.info(current.model)
    val env = current.envId?.let { id -> environments.firstOrNull { it.id == id } }
    val items = remember(current.messages, current.selected) { chatItems(current) }
    val results = remember(current.messages) { current.messages.filter { it.role == "tool" }.associateBy { it.toolCallId } }
    val repoLabel = when {
        !current.githubEnabled -> "Aus"
        current.repo == null -> "An – kein festes Repository"
        else -> current.repo + (current.branch?.let { " · $it" } ?: "")
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Outlined.Menu, contentDescription = "Menü") }
                },
                title = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { showModelPicker = true },
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                modelInfo.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp),
                            )
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) { Icon(Icons.Outlined.AddComment, contentDescription = "Neuer Chat") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            val hasMessages = current.messages.isNotEmpty()
                            DropdownMenuItem(
                                text = { Text("Umbenennen") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                enabled = hasMessages,
                                onClick = { menuOpen = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text(if (current.pinned) "Lösen" else "Anheften") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                enabled = hasMessages,
                                onClick = { menuOpen = false; vm.setPinned(!current.pinned) },
                            )
                            DropdownMenuItem(
                                text = { Text("In Ordner verschieben") },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileMove, contentDescription = null) },
                                enabled = hasMessages,
                                onClick = { menuOpen = false; showFolder = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Persona / System-Prompt") },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                onClick = { menuOpen = false; showSystemPrompt = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Umgebung") },
                                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                onClick = { menuOpen = false; showEnvPicker = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Chat exportieren (PDF, Word …)") },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                enabled = hasMessages,
                                onClick = { menuOpen = false; export = current.title to chatToMarkdown(current) },
                            )
                            DropdownMenuItem(
                                text = { Text("Chat löschen") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                enabled = hasMessages,
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                input = input,
                onInput = { input = it },
                placeholder = "Frag ${modelInfo.displayName}",
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
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startRecording()
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSend = {
                    if (images.isNotEmpty() && modelInfo.vision == false) {
                        Toast.makeText(context, "Hinweis: ${modelInfo.displayName} versteht keine Bilder.", Toast.LENGTH_LONG).show()
                    }
                    vm.send(input, images.toList(), files.toList())
                    input = ""
                    images.clear()
                    files.clear()
                },
                onStop = { vm.stop() },
            ) {
                // Always visible while active: the selected repository, environment and persona.
                if (current.githubEnabled) {
                    ContextChip(Icons.Outlined.Code, current.repo?.let { r -> r + (current.branch?.let { " · $it" } ?: "") } ?: "GitHub · Repo wählen", { showRepoPicker = true })
                }
                if (env != null) ContextChip(Icons.Outlined.Tune, env.name, { showEnvPicker = true })
                if (!current.systemPrompt.isNullOrBlank()) {
                    ContextChip(Icons.Outlined.Person, current.persona ?: "System-Prompt", { showSystemPrompt = true })
                }
            }
        },
    ) { pad ->
        if (items.isEmpty() && !running) {
            EmptyChat(
                hasApiKey = settings.hasApiKey,
                modelSummary = modelInfo.summary,
                padding = pad,
                suggestions = templates.take(6),
                onTemplate = { t ->
                    if (t.kind == TemplateKind.PERSONA) vm.setSystemPrompt(t.content, t.name) else input = t.content
                },
                onApiKey = { onNavigate("settings/api") },
            )
        } else {
            val listState = rememberLazyListState()
            val reversed = remember(items) { items.asReversed() }
            val last = current.thread().lastOrNull()
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
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
                    item(key = "typing") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("denkt nach…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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

    if (showPlus) {
        PlusSheet(
            repoLabel = repoLabel,
            envLabel = env?.name ?: "Keine",
            personaLabel = current.persona ?: if (current.systemPrompt.isNullOrBlank()) "Keine" else "Eigener System-Prompt",
            onCamera = { takePhoto() },
            onPhotos = {
                if (modelInfo.vision == false) {
                    Toast.makeText(context, "Hinweis: ${modelInfo.displayName} versteht keine Bilder.", Toast.LENGTH_SHORT).show()
                }
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFiles = { filePicker.launch(arrayOf("*/*")) },
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
            onPick = { t ->
                if (t.kind == TemplateKind.PERSONA) vm.setSystemPrompt(t.content, t.name)
                else input = if (input.isBlank()) t.content else t.content + input
            },
            onManage = { onNavigate("templates") },
            onDismiss = { showTemplates = false },
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = modelsState.ofType(ModelType.CHAT),
            selected = current.model,
            onSelect = { vm.setModel(it) },
            onDismiss = { showModelPicker = false },
        )
    }

    regenerateFor?.let { question ->
        ModelPickerSheet(
            models = modelsState.ofType(ModelType.CHAT),
            selected = current.model,
            onSelect = { vm.regenerate(question, it) },
            onDismiss = { regenerateFor = null },
        )
    }

    if (showRepoPicker) {
        RepoPickerDialog(
            c = c,
            enabled = current.githubEnabled,
            selectedRepo = current.repo,
            selectedBranch = current.branch,
            hasToken = settings.hasGitHub,
            onApply = { enabled, repo, branch -> vm.setGitHub(enabled, repo, branch) },
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
        ConfirmDialog("Chat löschen?", "„${current.title}“ wird dauerhaft gelöscht.", "Löschen", onConfirm = { vm.deleteChat() }, onDismiss = { showDelete = false })
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
            title = { Text("Aktion erlauben?") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    val where = if (p.call.name == "fetch_url") "im Web" else "in ${current.repo ?: "GitHub"} (Branch ${current.branch ?: "-"})"
                    Text("Das Modell möchte „${p.call.name}“ $where ausführen:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(prettyArgs(p.call.arguments).take(4000), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.answerPending(true) }) { Text("Erlauben") } },
            dismissButton = { TextButton(onClick = { vm.answerPending(false) }) { Text("Ablehnen") } },
        )
    }
}

@Composable
private fun EmptyChat(
    hasApiKey: Boolean,
    modelSummary: String,
    padding: PaddingValues,
    suggestions: List<io.github.thepytales.llmhub.data.Template>,
    onTemplate: (io.github.thepytales.llmhub.data.Template) -> Unit,
    onApiKey: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("Wie kann ich helfen?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(
            modelSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!hasApiKey) {
            Spacer(Modifier.size(20.dp))
            Button(onClick = onApiKey) {
                Icon(Icons.Outlined.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("API-Key eintragen")
            }
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .widthIn(min = 150.dp, max = 200.dp)
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
        }
    }
}
