package io.github.thepytales.llmhub.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.TranscriptPlayer
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.StartOptions
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Name of a language code for the meta line. */
private fun languageName(code: String?) = SPEECH_LANGUAGES.firstOrNull { it.first == code }?.second ?: code?.uppercase()

/**
 * One transcript: speakers with their share, the text (tap a sentence to play from there, the current word is marked
 * while playing), the player, and the actions (rename, project, export, use in chat, find & replace, re-transcribe).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TranscriptScreen(c: AppContainer, id: String, onBack: () -> Unit, onOpenChat: () -> Unit) {
    val t = c.transcription
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = LocalChatLook.current
    var tr by remember(id) { mutableStateOf<Transcript?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    var undo by remember(id) { mutableStateOf<Transcript?>(null) }
    LaunchedEffect(id) {
        t.engine()
        tr = t.load(id)
        loaded = true
    }

    val current = tr
    if (current == null) {
        Scaffold(topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text("Transkript") }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                if (loaded) HintText("Das Transkript gibt es nicht mehr.") else CircularProgressIndicator()
            }
        }
        return
    }

    fun update(next: Transcript, undoable: Boolean = true) {
        if (undoable) undo = tr
        tr = next
        scope.launch { t.save(next) }
    }

    val audio = remember(current.id, current.audio) { t.audioFile(current) }
    val player = remember(audio) { audio?.let { TranscriptPlayer(it) } }
    DisposableEffect(player) { onDispose { player?.release() } }
    val position by (player?.position ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0.0) }).collectAsStateWithLifecycle()
    val playing by (player?.playing ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val playerError by (player?.error ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsStateWithLifecycle()

    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var moveFolder by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    var findReplace by remember { mutableStateOf(false) }
    var retranscribe by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var editSegment by remember { mutableStateOf<Segment?>(null) }
    var editSpeaker by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    val active = if (playing || position > 0) current.segments.indexOfLast { it.start <= position + 0.05 } else -1
    LaunchedEffect(active, playing) {
        if (playing && active >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (active + 1 !in visible) listState.animateScrollToItem((active + 1).coerceAtLeast(0))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = { Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { rename = true }) },
                actions = {
                    if (undo != null) TextButton(onClick = { undo?.let { update(it, undoable = false) }; undo = null }) { Text("Rückgängig") }
                    IconButton(onClick = {
                        ChatHandoff.pending.value = ChatHandoff.Content(
                            "Fasse das Transkript zusammen: wichtigste Punkte, Entscheidungen und offene Fragen.",
                            listOf(transcriptAttachment(current)),
                            current.folder,
                        )
                        NewChatFolder.id = current.folder
                        onOpenChat()
                    }) { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Im Chat verwenden") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Exportieren …") }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = { menu = false; export = true })
                            DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("Projekt zuordnen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; moveFolder = true })
                            DropdownMenuItem(text = { Text("Suchen & Ersetzen") }, leadingIcon = { Icon(Icons.Outlined.FindReplace, null) }, onClick = { menu = false; findReplace = true })
                            if (audio != null) DropdownMenuItem(text = { Text("Neu transkribieren …") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; retranscribe = true })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; delete = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (player != null) PlayerBar(player, position, playing, current.duration, playerError)
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val wide = Modifier.widthIn(max = 760.dp).fillMaxWidth()
            item {
                Column(wide, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetaText(Icons.Outlined.Schedule, TranscriptExport.clock(current.duration))
                        languageName(current.language)?.let { MetaText(Icons.Outlined.Language, it) }
                        if (current.modelName.isNotBlank()) MetaText(Icons.Outlined.AutoAwesome, current.modelName)
                        MetaText(null, "${current.wordCount} Wörter")
                        current.folder?.let { f -> c.folders.items.value.firstOrNull { it.id == f } }?.let { MetaText(Icons.Outlined.Folder, it.name) }
                    }
                    current.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = look.warning) }
                    if (current.diarized) {
                        val total = current.segments.sumOf { it.end - it.start }.coerceAtLeast(0.001)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.speakers.keys.sorted().forEach { k ->
                                val share = current.segments.filter { it.speaker == k }.sumOf { it.end - it.start } / total
                                Surface(onClick = { editSpeaker = k }, shape = RoundedCornerShape(50), color = look.card, border = look.cardBorder) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(9.dp).clip(CircleShape).background(speakerColor(k)))
                                        Spacer(Modifier.width(7.dp))
                                        Text(current.speakerName(k), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${(share * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                        HintText("Tippe auf einen Sprecher, um ihn zu benennen (z. B. „I“ und „B1“ für Interviews).")
                    }
                    if (current.marks.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.marks.forEach { m ->
                                FilterChip(selected = false, onClick = { player?.seek(m.time); player?.play() }, label = { Text("${TranscriptExport.clock(m.time)} · ${m.label}") })
                            }
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                }
            }
            itemsIndexed(current.segments, key = { i, s -> "$i-${s.start}" }) { i, s ->
                val prev = current.segments.getOrNull(i - 1)
                val header = i == 0 || current.diarized && prev?.speaker != s.speaker || !current.diarized && s.start - (prev?.end ?: 0.0) > 6
                Column(wide) {
                    if (header) {
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            if (current.diarized) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(speakerColor(s.speaker ?: 0)))
                                Spacer(Modifier.width(7.dp))
                                Text(current.speakerName(s.speaker), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(TranscriptExport.clock(s.start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    val isActive = i == active
                    val text = buildAnnotatedString {
                        if (isActive && s.words.isNotEmpty()) {
                            val w = s.words.indexOfLast { it.start <= position + 0.02 }
                            var pos = 0
                            s.words.forEachIndexed { wi, word ->
                                val at = s.text.indexOf(word.text.trim(), pos).takeIf { it >= 0 } ?: return@forEachIndexed
                                append(s.text.substring(pos, at))
                                if (wi == w) withStyle(SpanStyle(background = Color(0x60FFC400))) { append(s.text.substring(at, at + word.text.trim().length)) }
                                else append(s.text.substring(at, at + word.text.trim().length))
                                pos = at + word.text.trim().length
                            }
                            append(s.text.substring(pos.coerceAtMost(s.text.length)))
                        } else append(s.text)
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { player?.let { p -> p.seek(s.start); p.play() } ?: run { editSegment = s } },
                                onLongClick = { editSegment = s },
                            )
                            .secondaryClick { editSegment = s }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                }
            }
            item { HintText("Tippen: ab hier abspielen · lange drücken (Rechtsklick): Satz bearbeiten", wide.padding(top = 18.dp)) }
        }
    }

    if (rename) TextInputDialog("Umbenennen", current.title, "Titel", onConfirm = { v -> update(current.copy(title = v.trim().ifBlank { current.title })) }, onDismiss = { rename = false })
    if (moveFolder) FolderPickerDialog(c, current.folder, onPick = { f -> update(current.copy(folder = f)) }, onDismiss = { moveFolder = false })
    if (delete) ConfirmDialog(
        "Transkript löschen?", "„${current.title}“ und die Aufnahme werden dauerhaft gelöscht.", "Löschen",
        onConfirm = { scope.launch { player?.release(); t.delete(current.id); onBack() } }, onDismiss = { delete = false },
    )
    editSpeaker?.let { k -> SpeakerDialog(current, k, onSave = { next -> update(next) }, onDismiss = { editSpeaker = null }) }
    editSegment?.let { s -> SegmentDialog(current, s, onSave = { next -> update(next) }, onDismiss = { editSegment = null }) }
    if (findReplace) FindReplaceDialog(current, onApply = { next, n ->
        update(next)
        Toast.makeText(context, "$n× ersetzt. Rückgängig ist möglich.", Toast.LENGTH_LONG).show()
    }, onDismiss = { findReplace = false })
    if (export) ExportSheet(c, current, onDismiss = { export = false })
    if (retranscribe) {
        var opts by remember { mutableStateOf(t.defaultOptions(current.folder).copy(title = current.title)) }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { retranscribe = false }, sheetState = sheet, containerColor = look.sheet) {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Neu transkribieren", style = MaterialTheme.typography.titleLarge)
                HintText("Die Aufnahme wird mit den neuen Einstellungen noch einmal transkribiert; das bisherige Transkript bleibt erhalten.")
                StartOptionsForm(c, opts, { opts = it })
                Button(modifier = Modifier.align(Alignment.End), onClick = {
                    runCatching { t.retranscribe(current.id, opts) }
                        .onSuccess { retranscribe = false; onBack() }
                        .onFailure { Toast.makeText(context, it.message ?: "Fehler", Toast.LENGTH_LONG).show() }
                }) { Text("Starten") }
            }
        }
    }
}

/** Color of a speaker in the current look. */
@Composable
fun speakerColor(i: Int): Color = LocalChatLook.current.speakers.let { it[i.mod(it.size)] }

/** The transcript as an attachment for the chat (text with times and speakers). */
fun transcriptAttachment(t: Transcript) = FileAttachment(
    name = DocumentWriter.safeName(t.title) + ".txt",
    mime = "text/plain",
    text = TranscriptExport.markdown(t),
)

@Composable
private fun PlayerBar(player: TranscriptPlayer, position: Double, playing: Boolean, duration: Double, error: String?) {
    val look = LocalChatLook.current
    var speed by remember { mutableStateOf(1f) }
    Surface(color = look.card, border = look.cardBorder, shadowElevation = 6.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) HintText(error, Modifier.padding(4.dp))
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.seek((position - 10).coerceAtLeast(0.0)) }) { Icon(Icons.Outlined.Replay10, contentDescription = "10 Sekunden zurück") }
                Surface(onClick = { player.toggle() }, shape = CircleShape, color = look.action, contentColor = look.onAction, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) "Pause" else "Abspielen") }
                }
                IconButton(onClick = { player.seek((position + 10).coerceAtMost(duration)) }) { Icon(Icons.Outlined.Forward10, contentDescription = "10 Sekunden vor") }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(0.1f)),
                        onValueChange = { player.seek(it.toDouble()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(0.1f),
                    )
                    Row {
                        Text(TranscriptExport.clock(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.weight(1f))
                        Text(TranscriptExport.clock(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                TextButton(onClick = {
                    val speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
                    speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                    player.setSpeed(speed)
                }) { Text((if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×") }
            }
        }
    }
}

@Composable
private fun SpeakerDialog(t: Transcript, speaker: Int, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(t.speakerName(speaker)) }
    var mergeInto by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sprecher") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name oder Kürzel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val others = t.speakers.keys.filter { it != speaker }.sorted()
                if (others.isNotEmpty()) {
                    ChoiceField(
                        "Zusammenführen mit (falls doppelt erkannt)",
                        listOf<Pair<Int?, String>>(null to "Nicht zusammenführen") + others.map { it to t.speakerName(it) },
                        mergeInto, { mergeInto = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = mergeInto
                val next = if (target != null) t.copy(
                    speakers = t.speakers - speaker,
                    segments = t.segments.map { if (it.speaker == speaker) it.copy(speaker = target) else it },
                ) else t.copy(speakers = t.speakers + (speaker to name.trim().ifBlank { "Sprecher ${speaker + 1}" }))
                onSave(next)
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun SegmentDialog(t: Transcript, s: Segment, onSave: (Transcript) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(s.text) }
    var speaker by remember { mutableStateOf(s.speaker) }
    fun replace(seg: Segment?) = t.copy(segments = t.segments.mapNotNull { if (it.id == s.id && it.start == s.start) seg else it })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Satz bei ${TranscriptExport.clock(s.start)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (t.diarized) ChoiceField("Sprecher", t.speakers.keys.sorted().map { it to t.speakerName(it) }, speaker, { speaker = it })
                TextButton(onClick = { onSave(replace(null)); onDismiss() }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Satz löschen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Edited text: the word times no longer fit.
                onSave(replace(s.copy(text = text.trim(), speaker = speaker, words = if (text.trim() == s.text) s.words else emptyList())))
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun FindReplaceDialog(t: Transcript, onApply: (Transcript, Int) -> Unit, onDismiss: () -> Unit) {
    var find by remember { mutableStateOf("") }
    var with by remember { mutableStateOf("") }
    val count = remember(find) { if (find.isBlank()) 0 else t.segments.sumOf { s -> Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(s.text).count() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suchen & Ersetzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(find, { find = it }, label = { Text("Suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(with, { with = it }, label = { Text("Ersetzen durch") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HintText(if (find.isBlank()) "Groß-/Kleinschreibung egal. Für Namen, die immer falsch erkannt werden: Einstellungen › Transkription › Ersetzungsliste." else "$count Treffer")
            }
        },
        confirmButton = {
            TextButton(enabled = count > 0, onClick = {
                val re = Regex(Regex.escape(find), RegexOption.IGNORE_CASE)
                onApply(t.copy(segments = t.segments.map { s -> if (re.containsMatchIn(s.text)) s.copy(text = re.replace(s.text, Regex.escapeReplacement(with)), words = emptyList()) else s }), count)
                onDismiss()
            }) { Text("Alle ersetzen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Export formats; the file appears as a card with open, share and save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(c: AppContainer, t: Transcript, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var made by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf<TranscriptFormat?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = LocalChatLook.current.sheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exportieren", style = MaterialTheme.typography.titleLarge)
            made?.let { f -> DocumentCard(f.absolutePath) }
            TranscriptFormat.entries.forEach { f ->
                AppCard(onClick = {
                    busy = f
                    scope.launch {
                        made = runCatching { exportFile(c, context, t, f) }
                            .onFailure { Toast.makeText(context, "Export fehlgeschlagen: ${it.message}", Toast.LENGTH_LONG).show() }.getOrNull()
                        busy = null
                    }
                }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TitleBlock(f.label, f.description, Modifier.weight(1f))
                        if (busy == f) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else StatusPill(f.ext.uppercase(), PillTone.NEUTRAL)
                    }
                }
            }
        }
    }
}

/** Writes the export into the documents folder of the app and returns the file. */
private suspend fun exportFile(c: AppContainer, context: android.content.Context, t: Transcript, f: TranscriptFormat): File {
    val folder = File(context.filesDir, "documents/Transkripte")
    val name = DocumentWriter.safeName(t.title) + when (f) {
        TranscriptFormat.DRESING_PEHL -> " (Dresing-Pehl)"
        TranscriptFormat.MAXQDA -> " (MAXQDA)"
        else -> ""
    }
    return when (f) {
        TranscriptFormat.DOCX, TranscriptFormat.DRESING_PEHL ->
            DocumentWriter.create(context, folder, t.title, TranscriptExport.content(t, f), DocFormat.DOCX, fileName = name, overwrite = true).file
        TranscriptFormat.PDF ->
            DocumentWriter.create(context, folder, t.title, TranscriptExport.content(t, f), DocFormat.PDF, fileName = name, overwrite = true).file
        else -> withContext(Dispatchers.IO) {
            folder.mkdirs()
            File(folder, "$name.${f.ext}").apply {
                // MAXQDA reads Windows text files best with a byte order mark.
                if (f == TranscriptFormat.MAXQDA) writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + TranscriptExport.content(t, f).toByteArray())
                else writeText(TranscriptExport.content(t, f))
            }
        }
    }
}
