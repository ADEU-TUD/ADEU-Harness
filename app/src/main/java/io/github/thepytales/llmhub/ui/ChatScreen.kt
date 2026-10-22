package io.github.thepytales.llmhub.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.audio.Recorder
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.net.RepoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Loads, downscales and encodes an image as a JPEG data URL. */
private suspend fun encodeImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val src = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val scale = minOf(1f, 1280f / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

@Composable
private fun rememberDataUrlBitmap(url: String): ImageBitmap? = remember(url) {
    runCatching {
        val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun Thumbnail(url: String, size: Int, onRemove: (() -> Unit)? = null) {
    val bmp = rememberDataUrlBitmap(url) ?: return
    Box {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        if (onRemove != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .clickable(onClick = onRemove),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Entfernen", modifier = Modifier.padding(3.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(c: AppContainer, id: String, onBack: () -> Unit) {
    val vm: ChatViewModel = viewModel(key = "chat_$id") { ChatViewModel(c, id) }
    val conv by vm.conversation.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val transcribing by vm.transcribing.collectAsStateWithLifecycle()
    val modelsState by c.models.state.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val playingId by c.player.playingId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<String>() }
    var showModelPicker by remember { mutableStateOf(false) }
    var showRepoPicker by remember { mutableStateOf(false) }
    var showSystemPrompt by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf<Pair<String, String>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val recorder = remember { Recorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        scope.launch { uris.forEach { uri -> encodeImage(context, uri)?.let { attachments.add(it) } } }
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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                title = {
                    Column(Modifier.clickable { showModelPicker = true }) {
                        Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                modelInfo.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showRepoPicker = true }) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = "GitHub",
                            tint = if (current.githubEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("System-Prompt") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; showSystemPrompt = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Erneut generieren") },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                enabled = !running && current.messages.isNotEmpty(),
                                onClick = { menuOpen = false; vm.retry() },
                            )
                            DropdownMenuItem(
                                text = { Text("Chat exportieren (PDF, Word …)") },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                enabled = current.messages.isNotEmpty(),
                                onClick = { menuOpen = false; export = current.title to chatToMarkdown(current) },
                            )
                            DropdownMenuItem(
                                text = { Text("Chat löschen") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (attachments.isNotEmpty()) {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            attachments.toList().forEach { a -> Thumbnail(a, 64) { attachments.remove(a) } }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (modelInfo.vision == false) {
                                Toast.makeText(context, "Hinweis: ${modelInfo.displayName} unterstützt keine Bilder.", Toast.LENGTH_SHORT).show()
                            }
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Bild anhängen")
                        }
                        IconButton(
                            enabled = !transcribing,
                            onClick = {
                                if (recording) {
                                    recording = false
                                    val file = recorder.stop()
                                    if (file != null) {
                                        vm.transcribe(file) { text -> input = if (input.isBlank()) text else "$input $text" }
                                    }
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    startRecording()
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            when {
                                transcribing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                recording -> Icon(Icons.Outlined.StopCircle, contentDescription = "Aufnahme beenden", tint = MaterialTheme.colorScheme.error)
                                else -> Icon(Icons.Outlined.Mic, contentDescription = "Spracheingabe")
                            }
                        }
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text(if (recording) "Aufnahme läuft…" else "Nachricht") },
                            maxLines = 6,
                            shape = LocalChatLook.current.inputShape,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(6.dp))
                        if (running) {
                            FilledIconButton(
                                onClick = { vm.stop() },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) { Icon(Icons.Outlined.Stop, contentDescription = "Stopp") }
                        } else {
                            FilledIconButton(
                                enabled = input.isNotBlank() || attachments.isNotEmpty(),
                                onClick = {
                                    vm.send(input, attachments.toList())
                                    input = ""
                                    attachments.clear()
                                },
                            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden") }
                        }
                    }
                }
            }
        },
    ) { pad ->
        val listState = rememberLazyListState()
        val reversed = remember(current.messages) { current.messages.filter { it.role != "tool" }.asReversed() }
        val resultsById = remember(current.messages) {
            current.messages.filter { it.role == "tool" }.associateBy { it.toolCallId }
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val last = current.messages.lastOrNull()
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
            items(reversed, key = { it.id }) { m ->
                when (m.role) {
                    "user" -> UserBubble(m)
                    "assistant" -> AssistantMessage(
                        m = m,
                        results = resultsById,
                        streaming = running && m.id == last?.id,
                        playing = playingId == m.id,
                        onSpeak = { vm.speak(m) },
                        onExport = { text -> export = current.title to text },
                    )
                    // Tool results are shown inside the assistant message that requested them.
                    else -> {}
                }
            }
            if (current.messages.isEmpty()) {
                item(key = "intro") { ChatIntro(current.githubEnabled, settings.hasGitHub, current.repo, modelInfo.summary) }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = modelsState.ofType(ModelType.CHAT),
            selected = current.model,
            onSelect = { vm.setModel(it) },
            onDismiss = { showModelPicker = false },
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

    if (showSystemPrompt) {
        var text by remember { mutableStateOf(current.systemPrompt.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showSystemPrompt = false },
            title = { Text("System-Prompt für diesen Chat") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    placeholder = { Text("z.B. Antworte immer kurz und auf Deutsch.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setSystemPrompt(text); showSystemPrompt = false }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showSystemPrompt = false }) { Text("Abbrechen") } },
        )
    }

    export?.let { (title, text) ->
        ExportDialog(title = title, markdown = text, onDismiss = { export = null })
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Chat löschen?") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; vm.deleteChat(); onBack() }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Abbrechen") } },
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
            title = { Text("Aktion erlauben?") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Das Modell möchte „${p.call.name}“ in ${current.repo ?: "GitHub"} (Branch ${current.branch ?: "-"}) ausführen:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        prettyArgs(p.call.arguments).take(4000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.answerPending(true) }) { Text("Erlauben") } },
            dismissButton = { TextButton(onClick = { vm.answerPending(false) }) { Text("Ablehnen") } },
        )
    }
}

private fun prettyArgs(args: String): String = runCatching {
    val obj = io.github.thepytales.llmhub.net.JSON.parseToJsonElement(args) as kotlinx.serialization.json.JsonObject
    obj.entries.joinToString("\n\n") { (k, v) ->
        val value = (v as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content ?: v.toString()
        "$k:\n$value"
    }
}.getOrDefault(args)

private fun shortArgs(args: String): String = runCatching {
    val obj = io.github.thepytales.llmhub.net.JSON.parseToJsonElement(args) as kotlinx.serialization.json.JsonObject
    obj.entries.filter { it.key !in setOf("content", "new_text", "old_text", "body") }
        .joinToString(", ") { (k, v) ->
            val s = (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: v.toString()
            "$k=${s.take(40)}"
        }
}.getOrDefault(args.take(60))

@Composable
private fun ChatIntro(github: Boolean, hasToken: Boolean, repo: String?, summary: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Neuer Chat", style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    github -> "GitHub-Werkzeuge aktiv${repo?.let { " – Repository: $it" } ?: ""}. Beispiel: „Lies die README von " +
                        "github.com/owner/repo und schlage Verbesserungen vor.“"
                    hasToken -> "Tipp: Über das </>-Symbol oben kannst du die GitHub-Werkzeuge einschalten – oder einfach einen " +
                        "GitHub-Link in die Nachricht schreiben."
                    else -> "Tipp: Mit einem GitHub-Token (Einstellungen) kann das Modell in deinen Repositories arbeiten."
                } + " Das Modell kann außerdem PDFs und Word-Dokumente erstellen und Webseiten lesen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    val look = LocalChatLook.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = look.userBubble,
            shape = look.userShape,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.images.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { m.images.forEach { Thumbnail(it, 96) } }
                }
                if (m.content.isNotBlank()) {
                    SelectionContainer {
                        Text(m.content, color = look.onUserBubble)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    m: ChatMessage,
    results: Map<String?, ChatMessage>,
    streaming: Boolean,
    playing: Boolean,
    onSpeak: () -> Unit,
    onExport: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    if (m.error) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                m.content,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }
    val (inlineThinking, answer) = remember(m.content) { splitThinking(m.content) }
    val thinking = listOfNotNull(m.reasoning, inlineThinking).joinToString("\n").ifBlank { null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (thinking != null) ThinkingBox(thinking, streaming && answer.isBlank())
        if (answer.isNotBlank()) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = LocalChatLook.current.answerFont)) {
                SelectionContainer { MarkdownText(answer) }
            }
        }
        m.toolCalls.forEach { call ->
            val result = results[call.id]
            ToolCallCard(call.name, shortArgs(call.arguments), result?.content, streaming)
            result?.file?.let { DocumentCard(it) }
        }
        if (!streaming && answer.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { clipboard.setText(AnnotatedString(answer)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { onExport(answer) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Description, contentDescription = "Als Dokument exportieren", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (playing) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "Vorlesen",
                        modifier = Modifier.size(16.dp),
                    )
                }
                m.model?.let {
                    Text(
                        ModelCatalog.info(it).displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingBox(text: String, active: Boolean) {
    var open by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    if (active) "Denkt nach…" else "Gedankengang",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            if (open || active) {
                Text(
                    if (open) text else text.takeLast(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(name: String, args: String, result: String?, streaming: Boolean) {
    var open by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "$name($args)",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    result == null && streaming -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    result != null -> Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (open && result != null) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SelectionContainer {
                    Text(
                        if (result.length > 5000) result.take(5000) + "\n…" else result,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoPickerDialog(
    c: AppContainer,
    enabled: Boolean,
    selectedRepo: String?,
    selectedBranch: String?,
    hasToken: Boolean,
    onApply: (Boolean, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var repos by remember { mutableStateOf<List<RepoSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toolsOn by remember { mutableStateOf(enabled) }

    LaunchedEffect(hasToken) {
        if (hasToken) {
            try {
                repos = c.github.listRepos()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub-Zugriff") },
        text = {
            if (!hasToken) {
                Text("Hinterlege zuerst in den Einstellungen ein GitHub-Token, damit das Modell auf deine Repositories zugreifen kann.")
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Werkzeuge aktiv", modifier = Modifier.weight(1f))
                    Switch(checked = toolsOn, onCheckedChange = {
                        toolsOn = it
                        onApply(it, selectedRepo, selectedBranch)
                        if (!it) onDismiss()
                    })
                }
                Text(
                    "Wähle ein Repository oder lass das Modell selbst eins auswählen. Ein Werkzeug-fähiges Modell wird empfohlen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Suchen oder owner/repo bzw. URL eingeben") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    repos == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        run {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onApply(true, null, null); onDismiss() },
                                headlineContent = { Text("Ohne festes Repository") },
                                supportingContent = { Text("Das Modell wählt bei Bedarf selbst.") },
                            )
                        }
                        val typed = GitHubTools.parseRepo(query)
                        if (typed != null && repos!!.none { it.fullName.equals(typed, ignoreCase = true) }) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onApply(true, typed, null); onDismiss() },
                                headlineContent = { Text("„$typed“ verwenden") },
                                supportingContent = { Text("Auch fremde öffentliche Repositories") },
                            )
                        }
                        repos!!.filter { it.fullName.contains(query.trim(), ignoreCase = true) || typed != null && it.fullName.equals(typed, ignoreCase = true) }.forEach { r ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable {
                                    onApply(true, r.fullName, r.defaultBranch)
                                    onDismiss()
                                },
                                headlineContent = {
                                    Text(
                                        r.fullName + if (r.fullName == selectedRepo) "  ✓" else "",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(if (r.isPrivate) "privat" else "öffentlich", r.description).joinToString(" · "),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
