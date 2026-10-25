package io.github.thepytales.llmhub.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Replay30
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.TranscriptPlayer
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.transcribe.InlineMark
import io.github.thepytales.llmhub.transcribe.SearchHit
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.TextWord
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptEdit
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.TranscriptStatus
import io.github.thepytales.llmhub.transcribe.TranscriptionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Name of a language code for the meta line. */
private fun languageName(code: String?) = SPEECH_LANGUAGES.firstOrNull { it.first == code }?.second ?: code?.uppercase()

/** Controls of the playback that the panels and the segment dialog use too. */
class PlaybackControl(
    val player: TranscriptPlayer?,
    val position: () -> Double,
    val settings: () -> TranscriptionSettings,
) {
    val available get() = player != null

    fun play(from: Double? = null) {
        val p = player ?: return
        from?.let { p.seek(it.coerceAtLeast(0.0)) }
        p.play()
    }

    /** Pausing goes back a little (setting), so the last words are heard again when continuing – as in f4transkript. */
    fun pause() {
        val p = player ?: return
        p.pause()
        val back = settings().rewindOnPause
        if (back > 0) p.seek((position() - back).coerceAtLeast(0.0))
    }

    fun toggle(playing: Boolean) = if (playing) pause() else play()
    fun skip(seconds: Double) { player?.seek((position() + seconds).coerceAtLeast(0.0)) }
}

/** Keys while listening and typing (Windows and keyboards on tablets): F4 play/pause, F3/F5 back/forward, F6 repeat. */
fun transcriptKey(e: KeyEvent, control: PlaybackControl, playing: () -> Boolean, onLoop: () -> Unit, onSearch: (() -> Unit)? = null): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val skip = control.settings().skipSeconds.toDouble()
    return when {
        e.key == Key.F4 || e.isCtrlPressed && e.key == Key.Spacebar -> { control.toggle(playing()); true }
        e.key == Key.F3 -> { control.skip(-skip); true }
        e.key == Key.F5 -> { control.skip(skip); true }
        e.key == Key.F6 -> { onLoop(); true }
        e.isCtrlPressed && e.key == Key.F && onSearch != null -> { onSearch(); true }
        else -> false
    }
}

/**
 * One transcript: speakers with their share, the text (tap a word to play from there, the current word is marked
 * while playing), the player with correction mode, search, and the actions (view, speakers, segmentation, word times,
 * chapters, statistics, status, export, use in chat, find & replace, re-transcribe, discard changes).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TranscriptScreen(c: AppContainer, id: String, onBack: () -> Unit, onOpenChat: () -> Unit) {
    val t = c.transcription
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val look = LocalChatLook.current
    val vs by t.settings.collectAsStateWithLifecycle()
    val vm: TranscriptViewModel = viewModel(key = "transcript-$id") { TranscriptViewModel(c, id) }
    val tr by vm.transcript.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()

    val current = tr
    if (current == null) {
        Scaffold(topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text("Transkript") }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                if (loaded) HintText("Das Transkript gibt es nicht mehr.")
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    HintText("Transkript wird geladen …")
                }
            }
        }
        return
    }

    fun update(next: Transcript, undoable: Boolean = true) = vm.update(next, undoable)

    val audio = remember(current.id, current.audio) { t.audioFile(current) }
    val player = remember(audio) { audio?.let { TranscriptPlayer(it, context) } }
    DisposableEffect(player) { onDispose { player?.release() } }
    val position by (player?.position ?: remember { MutableStateFlow(0.0) }).collectAsStateWithLifecycle()
    val playing by (player?.playing ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val playerError by (player?.error ?: remember { MutableStateFlow<String?>(null) }).collectAsStateWithLifecycle()
    val control = remember(player) { PlaybackControl(player, { player?.position?.value ?: 0.0 }, { t.settings.value }) }
    LaunchedEffect(player, vs.speed) { player?.setSpeed(vs.speed) }

    var menu by rememberSaveable(id) { mutableStateOf(false) }
    var rename by rememberSaveable(id) { mutableStateOf(false) }
    var moveFolder by rememberSaveable(id) { mutableStateOf(false) }
    var export by rememberSaveable(id) { mutableStateOf(false) }
    var findReplace by rememberSaveable(id) { mutableStateOf(false) }
    var retranscribe by rememberSaveable(id) { mutableStateOf(false) }
    var delete by rememberSaveable(id) { mutableStateOf(false) }
    var discard by rememberSaveable(id) { mutableStateOf(false) }
    var viewSheet by rememberSaveable(id) { mutableStateOf(false) }
    var speakersSheet by rememberSaveable(id) { mutableStateOf(false) }
    var segmentSheet by rememberSaveable(id) { mutableStateOf(false) }
    var statsSheet by rememberSaveable(id) { mutableStateOf(false) }
    var chaptersSheet by rememberSaveable(id) { mutableStateOf(false) }
    var statusDialog by rememberSaveable(id) { mutableStateOf(false) }
    var wordsDialog by rememberSaveable(id) { mutableStateOf(false) }
    var editSegment by rememberSaveable(id) { mutableStateOf<Int?>(null) }
    var editSpeaker by rememberSaveable(id) { mutableStateOf<Int?>(null) }
    var loop by rememberSaveable(id) { mutableStateOf<Int?>(null) }
    var searching by rememberSaveable(id) { mutableStateOf(false) }
    var query by rememberSaveable(id) { mutableStateOf("") }
    var hitIndex by rememberSaveable(id) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    // Keys (F4 …) go to the screen; the search field gets the focus while it is open
    LaunchedEffect(searching) { runCatching { if (searching) searchFocus.requestFocus() else focus.requestFocus() } }

    val segs = current.segments
    val active = if (playing || position > 0) segs.indexOfLast { it.start <= position + 0.05 } else -1
    val words = remember(segs) { segs.map { TranscriptEdit.textWords(it) } }
    val stamps = TranscriptEdit.Stamps.of(vs.stamps)
    val marks = remember(segs, stamps) { TranscriptEdit.inlineMarks(current, stamps) }
    val hits = remember(segs, query) { TranscriptEdit.search(current, query) }
    val hit = hits.getOrNull(hitIndex.coerceIn(0, (hits.size - 1).coerceAtLeast(0)))
    val chaptersAt = remember(segs, current.chapters) {
        current.chapters.groupBy { ch -> segs.indexOfLast { it.start <= ch.time + 0.25 }.coerceAtLeast(0) }
    }
    // Current word: the last measured word that has started (unmeasured words are not marked)
    val activeWord = if (active >= 0 && vs.wordHighlight) words[active].indexOfLast { w -> w.word?.let { it.measured && it.start <= position + 0.02 } == true } else -1

    LaunchedEffect(active, playing) {
        if (vs.follow && playing && active >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (active + 1 !in visible) listState.animateScrollToItem((active + 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(hit) { hit?.let { listState.animateScrollToItem(it.segment + 1) } }
    // Repeat one passage (F6 or the repeat button) for listening closely
    LaunchedEffect(position, loop) {
        val l = loop?.let { segs.getOrNull(it) } ?: return@LaunchedEffect
        if (playing && (position >= l.end || position < l.start - 0.5)) player?.seek(l.start)
    }
    // Skip pauses between passages
    LaunchedEffect(position, playing, vs.skipSilence) {
        if (!vs.skipSilence || !playing || loop != null) return@LaunchedEffect
        val inside = segs.any { position >= it.start - 0.2 && position <= it.end + 0.3 }
        val next = segs.firstOrNull { it.start > position } ?: return@LaunchedEffect
        if (!inside && next.start - position > 1.5) player?.seek(next.start - 0.3)
    }

    PlatformBackHandler(enabled = searching) { searching = false; query = "" }

    fun toggleLoop() { loop = if (loop != null) null else active.takeIf { it >= 0 } ?: 0 }
    fun openSearch() { searching = true }

    Scaffold(
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e -> transcriptKey(e, control, { player?.playing?.value == true }, ::toggleLoop, ::openSearch) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (searching) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = { IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Suche schließen") } },
                    title = {
                        TextField(
                            query, { query = it; hitIndex = 0 },
                            placeholder = { Text("Im Transkript suchen") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (hits.isNotEmpty()) hitIndex = (hitIndex + 1) % hits.size }),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                    },
                    actions = {
                        Text(if (query.isBlank()) "" else if (hits.isEmpty()) "0" else "${hitIndex + 1}/${hits.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        IconButton(enabled = hits.isNotEmpty(), onClick = { hitIndex = (hitIndex - 1 + hits.size) % hits.size }) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Voriger Treffer") }
                        IconButton(enabled = hits.isNotEmpty(), onClick = { hitIndex = (hitIndex + 1) % hits.size }) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Nächster Treffer") }
                    },
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = { BackButton(onBack) },
                    title = { Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { rename = true }) },
                    actions = {
                        if (undo != null) TextButton(onClick = vm::undoLast) { Text("Rückgängig") }
                        IconButton(onClick = ::openSearch) { Icon(Icons.Outlined.Search, contentDescription = "Suchen (Strg+F)") }
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
                                DropdownMenuItem(text = { Text("Ansicht & Zeitstempel …") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { menu = false; viewSheet = true })
                                DropdownMenuItem(text = { Text("Sprecher …") }, leadingIcon = { Icon(Icons.Outlined.People, null) }, onClick = { menu = false; speakersSheet = true })
                                DropdownMenuItem(text = { Text("Segmentierung …") }, leadingIcon = { Icon(Icons.Outlined.ContentCut, null) }, onClick = { menu = false; segmentSheet = true })
                                DropdownMenuItem(text = { Text("Wortzeiten …") }, leadingIcon = { Icon(Icons.Outlined.Timer, null) }, onClick = { menu = false; wordsDialog = true })
                                DropdownMenuItem(text = { Text("Kapitel & Zusammenfassung …") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, null) }, onClick = { menu = false; chaptersSheet = true })
                                DropdownMenuItem(text = { Text("Redeanteile & Statistik …") }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = { menu = false; statsSheet = true })
                                DropdownMenuItem(text = { Text("Status: ${current.status.label} …") }, leadingIcon = { Icon(Icons.Outlined.FactCheck, null) }, onClick = { menu = false; statusDialog = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Exportieren …") }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = { menu = false; export = true })
                                DropdownMenuItem(text = { Text("Umbenennen") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; rename = true })
                                DropdownMenuItem(text = { Text("Projekt zuordnen") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; moveFolder = true })
                                DropdownMenuItem(text = { Text("Suchen & Ersetzen") }, leadingIcon = { Icon(Icons.Outlined.FindReplace, null) }, onClick = { menu = false; findReplace = true })
                                if (audio != null) DropdownMenuItem(text = { Text("Neu transkribieren …") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; retranscribe = true })
                                if (current.hasOriginal) DropdownMenuItem(text = { Text("Änderungen verwerfen …") }, leadingIcon = { Icon(Icons.Outlined.Restore, null) }, onClick = { menu = false; discard = true })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; delete = true })
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (player != null) PlayerBar(c, player, control, position, playing, current.duration, playerError, loop != null, ::toggleLoop)
        },
    ) { pad ->
        val textStyle = transcriptTextStyle(vs.textSize)
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val wide = Modifier.widthIn(max = 760.dp).fillMaxWidth()
            item {
                Column(wide, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetaText(Icons.Outlined.Schedule, TranscriptExport.clock(current.duration))
                        languageName(current.language)?.let { MetaText(Icons.Outlined.Language, it) }
                        if (current.modelName.isNotBlank()) MetaText(Icons.Outlined.AutoAwesome, current.modelName)
                        MetaText(null, "${current.wordCount} Wörter")
                        current.folder?.let { f -> c.folders.items.value.firstOrNull { it.id == f } }?.let { MetaText(Icons.Outlined.Folder, it.name) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val share = current.measuredShare
                        Box(Modifier.clip(RoundedCornerShape(50)).clickable { wordsDialog = true }) {
                            StatusPill(
                                if (share > 0) "Wortzeiten gemessen · ${Math.round(share * 100)} %" else "Wortzeiten nicht gemessen",
                                if (share >= 0.9) PillTone.SUCCESS else if (share > 0) PillTone.WARNING else PillTone.NEUTRAL,
                            )
                        }
                        Box(Modifier.clip(RoundedCornerShape(50)).clickable { statusDialog = true }) {
                            StatusPill(current.status.label, when (current.status) { TranscriptStatus.CHECKED -> PillTone.SUCCESS; TranscriptStatus.CORRECTED -> PillTone.ACCENT; else -> PillTone.NEUTRAL })
                        }
                    }
                    current.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = look.warning) }
                    if (current.diarized) {
                        val total = segs.sumOf { it.end - it.start }.coerceAtLeast(0.001)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.speakers.keys.sorted().forEach { k ->
                                val share = segs.filter { it.speaker == k }.sumOf { it.end - it.start } / total
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
                    if (current.marks.isNotEmpty() || current.chapters.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            current.chapters.forEach { ch ->
                                FilterChip(selected = false, onClick = { control.play(ch.time) }, label = { Text("${TranscriptExport.clock(ch.time)} · ${ch.title}") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, null, Modifier.size(16.dp)) })
                            }
                            current.marks.forEach { m ->
                                FilterChip(selected = false, onClick = { control.play(m.time) }, label = { Text("${TranscriptExport.clock(m.time)} · ${m.label}") })
                            }
                        }
                    }
                    current.summary?.let { sum ->
                        var open by remember { mutableStateOf(false) }
                        AppCard(onClick = { open = !open }) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Zusammenfassung", style = MaterialTheme.typography.titleSmall)
                                Text(sum, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                }
            }
            itemsIndexed(segs, key = { i, s -> "$i-${s.start}" }) { i, s ->
                val prev = segs.getOrNull(i - 1)
                val header = when (vs.paragraphs) {
                    "segment" -> true
                    "speaker" -> i == 0 || current.diarized && prev?.speaker != s.speaker
                    else -> i == 0 || current.diarized && prev?.speaker != s.speaker || !current.diarized && s.start - (prev?.end ?: 0.0) > 6
                }
                val headerTime = header && stamps != TranscriptEdit.Stamps.OFF
                Column(wide) {
                    chaptersAt[i]?.forEach { ch ->
                        Text(
                            ch.title, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = if (i > 0) 18.dp else 4.dp, bottom = 4.dp).clickable { control.play(ch.time) },
                        )
                    }
                    if (header) {
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            if (current.diarized) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(speakerColor(s.speaker ?: 0)))
                                Spacer(Modifier.width(7.dp))
                                Text(current.speakerName(s.speaker), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                Spacer(Modifier.width(8.dp))
                            }
                            if (headerTime) Text(TranscriptExport.clock(s.start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            if (loop == i) { Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.RepeatOne, contentDescription = "Wird wiederholt", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    val segMarks = marks.getOrElse(i) { emptyList() }.filter { !(headerTime && it.offset == 0) }
                    val segHits = hits.filter { it.segment == i }
                    val isActive = i == active
                    val markColor = MaterialTheme.colorScheme.outline
                    val wordColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    val text = remember(s, segMarks, if (isActive) activeWord else -1, segHits, hit, vs.markUnmeasured, markColor, wordColor) {
                        segmentText(s, words[i], segMarks, if (isActive) activeWord else -1, segHits, hit, vs.markUnmeasured, markColor, wordColor)
                    }
                    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val shown by rememberUpdatedState(text)
                    Text(
                        text,
                        style = textStyle,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                            .pointerInput(s, player) {
                                detectTapGestures(
                                    onTap = { pos ->
                                        if (player == null) { editSegment = i; return@detectTapGestures }
                                        val off = layout?.getOffsetForPosition(pos) ?: 0
                                        if (loop != null) loop = i
                                        control.play(tapTime(shown, off, s, words[i]))
                                    },
                                    onLongPress = { editSegment = i },
                                )
                            }
                            .secondaryClick { editSegment = i }
                            // TalkBack: tap gestures above are invisible to it, so the same actions as named actions.
                            .semantics {
                                onClick(label = if (player != null) "Ab hier abspielen" else "Bearbeiten") {
                                    if (player == null) editSegment = i else control.play(s.start)
                                    true
                                }
                                customActions = listOf(CustomAccessibilityAction("Abschnitt bearbeiten") { editSegment = i; true })
                            }
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                    )
                    if (vs.showComments && !s.comment.isNullOrBlank()) {
                        Row(Modifier.padding(start = 6.dp, top = 2.dp, bottom = 4.dp).clickable { editSegment = i }, verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.ModeComment, contentDescription = "Kommentar", Modifier.size(15.dp).padding(top = 2.dp), tint = look.warning)
                            Spacer(Modifier.width(6.dp))
                            Text(s.comment, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                HintText(
                    if (player != null) "Tippen: ab diesem Wort abspielen · lange drücken (Rechtsklick): bearbeiten, teilen, kommentieren · " +
                        "F4 Abspielen/Anhalten, F3/F5 zurück/vor, F6 Abschnitt wiederholen, Strg+F Suchen"
                    else "Tippen oder lange drücken: Abschnitt bearbeiten",
                    wide.padding(top = 18.dp),
                )
            }
        }
    }

    if (rename) TextInputDialog("Umbenennen", current.title, "Titel", onConfirm = { v -> update(current.copy(title = v.trim().ifBlank { current.title })) }, onDismiss = { rename = false })
    if (moveFolder) FolderPickerDialog(c, current.folder, onPick = { f -> update(current.copy(folder = f)) }, onDismiss = { moveFolder = false })
    if (delete) ConfirmDialog(
        "Transkript löschen?", "„${current.title}“ kommt mit der Aufnahme in den Papierkorb (30 Tage, Einstellungen › Daten).", "Löschen",
        onConfirm = { player?.release(); vm.delete(onBack) }, onDismiss = { delete = false },
    )
    if (discard) ConfirmDialog(
        "Änderungen verwerfen?",
        "Text, Abschnitte und Sprecher werden auf die Fassung direkt nach dem Transkribieren zurückgesetzt. Titel, Projekt, Kapitel und Status bleiben. " +
            "Gleich danach geht es mit „Rückgängig“ noch zurück.",
        "Verwerfen",
        onConfirm = { current.original()?.let { update(it) } },
        onDismiss = { discard = false },
    )
    editSpeaker?.let { k -> SpeakerDialog(current, k, onSave = { next -> update(next) }, onDismiss = { editSpeaker = null }) }
    editSegment?.let { i ->
        if (i in segs.indices) SegmentDialog(current, i, control, { player?.playing?.value == true }, ::toggleLoop, onSave = { next -> update(next) }, onDismiss = { editSegment = null })
        else editSegment = null
    }
    if (findReplace) FindReplaceDialog(current, onApply = { next, n ->
        update(next)
        Notices.show(Notices.Notice("$n× ersetzt", action = "Rückgängig", onAction = vm::undoLast))
    }, onDismiss = { findReplace = false })
    if (export) ExportSheet(c, current, onDismiss = { export = false })
    if (viewSheet) ViewSheet(c, onDismiss = { viewSheet = false })
    if (speakersSheet) SpeakersSheet(current, onEdit = { editSpeaker = it }, onSave = { update(it) }, onDismiss = { speakersSheet = false })
    if (segmentSheet) SegmentationSheet(current, onSave = { update(it) }, onDismiss = { segmentSheet = false })
    if (statsSheet) StatsSheet(current, onDismiss = { statsSheet = false })
    if (chaptersSheet) ChaptersSheet(c, current, control, onSave = { update(it) }, onDismiss = { chaptersSheet = false })
    if (statusDialog) StatusDialog(current.status, onPick = { update(current.copy(status = it)) }, onDismiss = { statusDialog = false })
    if (wordsDialog) WordTimesDialog(c, current, audio != null, onDismiss = { wordsDialog = false })
    if (retranscribe) RetranscribeSheet(c, current, onStarted = { retranscribe = false; onBack() }, onDismiss = { retranscribe = false })
}

/** Text size of the transcript (Ansicht › Schriftgröße). */
@Composable
private fun transcriptTextStyle(size: String): TextStyle {
    val base = MaterialTheme.typography.bodyLarge
    return when (size) {
        "small" -> MaterialTheme.typography.bodyMedium
        "large" -> base.copy(fontSize = base.fontSize * 1.2f, lineHeight = base.lineHeight * 1.2f)
        "xlarge" -> base.copy(fontSize = base.fontSize * 1.45f, lineHeight = base.lineHeight * 1.45f)
        else -> base
    }
}

/**
 * The text of a segment with inline time stamps "(0:03)", the current word, search hits and (optionally) unmeasured
 * words underlined. Annotations: "w" = index of the word, "t" = time of a stamp.
 */
private fun segmentText(
    s: Segment,
    tw: List<TextWord>,
    marks: List<InlineMark>,
    currentWord: Int,
    hits: List<SearchHit>,
    currentHit: SearchHit?,
    markUnmeasured: Boolean,
    markColor: Color,
    wordColor: Color,
): AnnotatedString {
    val text = s.text
    val byOffset = marks.groupBy { it.offset }
    val map = IntArray(text.length + 1)
    val b = AnnotatedString.Builder()
    for (i in 0..text.length) {
        byOffset[i]?.forEach { m ->
            b.pushStringAnnotation("t", m.time.toString())
            b.pushStyle(SpanStyle(color = markColor, fontWeight = FontWeight.Normal))
            b.append("(" + TranscriptExport.clock(m.time) + ") ")
            b.pop(); b.pop()
        }
        map[i] = b.length
        if (i < text.length) b.append(text[i])
    }
    tw.forEachIndexed { k, w ->
        b.addStringAnnotation("w", k.toString(), map[w.from], map[w.to])
        if (markUnmeasured && w.word?.measured != true) b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = markColor), map[w.from], map[w.to])
        if (k == currentWord) b.addStyle(SpanStyle(background = wordColor), map[w.from], map[w.to])
    }
    hits.forEach { h ->
        val strong = currentHit == h
        b.addStyle(SpanStyle(background = if (strong) Color(0xCCFFB300) else Color(0x66FFD54F)), map[h.from], map[h.to])
    }
    return b.toAnnotatedString()
}

/** Where a tap in the text starts the playback: a stamp, the measured start of the word, else the segment start. */
private fun tapTime(text: AnnotatedString, offset: Int, s: Segment, tw: List<TextWord>): Double {
    text.getStringAnnotations("t", offset, offset + 1).firstOrNull()?.item?.toDoubleOrNull()?.let { return it }
    val k = text.getStringAnnotations("w", offset, offset + 1).firstOrNull()?.item?.toIntOrNull()
        ?: text.getStringAnnotations("w", (offset - 1).coerceAtLeast(0), offset).firstOrNull()?.item?.toIntOrNull()
    val w = k?.let { tw.getOrNull(it)?.word }
    return if (w != null && w.measured) w.start else s.start
}

/** Color of a speaker in the current look. */
@Composable
fun speakerColor(i: Int): Color = LocalChatLook.current.speakers.let { it[i.mod(it.size)] }

/** The transcript as an attachment for the chat (text with times, speakers and comments). */
fun transcriptAttachment(t: Transcript) = FileAttachment(
    name = DocumentWriter.safeName(t.title) + ".txt",
    mime = "text/plain",
    text = TranscriptExport.markdown(t),
)

private val SPEEDS = listOf(0.5f, 0.6f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(v: Float) = (if (v % 1f == 0f) v.toInt().toString() else v.toString().replace('.', ',')) + "×"

@Composable
private fun PlayerBar(
    c: AppContainer,
    player: TranscriptPlayer,
    control: PlaybackControl,
    position: Double,
    playing: Boolean,
    duration: Double,
    error: String?,
    looping: Boolean,
    onLoop: () -> Unit,
) {
    val look = LocalChatLook.current
    val vs by c.transcription.settings.collectAsStateWithLifecycle()
    var speedMenu by remember { mutableStateOf(false) }
    val skip = vs.skipSeconds
    Surface(color = look.card, border = look.cardBorder, shadowElevation = 6.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) HintText(error, Modifier.padding(4.dp))
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { control.skip(-skip.toDouble()) }) {
                    Icon(when (skip) { 5 -> Icons.Outlined.Replay5; 30 -> Icons.Outlined.Replay30; else -> Icons.Outlined.Replay10 }, contentDescription = "$skip Sekunden zurück (F3)")
                }
                Surface(onClick = { control.toggle(playing) }, shape = CircleShape, color = look.action, contentColor = look.onAction, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) "Pause (F4)" else "Abspielen (F4)") }
                }
                IconButton(onClick = { control.skip(skip.toDouble()) }) {
                    Icon(when (skip) { 5 -> Icons.Outlined.Forward5; 30 -> Icons.Outlined.Forward30; else -> Icons.Outlined.Forward10 }, contentDescription = "$skip Sekunden vor (F5)")
                }
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
                IconButton(onClick = onLoop) {
                    Icon(if (looping) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, contentDescription = if (looping) "Wiederholen aus (F6)" else "Abschnitt wiederholen (F6)",
                        tint = if (looping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { speedMenu = true }) { Text(speedLabel(vs.speed)) }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        SPEEDS.forEach { v ->
                            DropdownMenuItem(text = { Text(speedLabel(v) + if (v == 1f) " (normal)" else "") }, onClick = {
                                speedMenu = false
                                c.transcription.updateSettings { it.copy(speed = v) }
                            })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentDialog(
    t: Transcript,
    index: Int,
    control: PlaybackControl,
    playing: () -> Boolean,
    onLoop: () -> Unit,
    onSave: (Transcript) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = t.segments[index]
    var text by remember(s) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(s.text)) }
    var speaker by remember(s) { mutableStateOf(s.speaker) }
    var comment by remember(s) { mutableStateOf(s.comment.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    /** The transcript with the edits of this dialog applied to the segment. */
    fun edited(): Transcript = t.copy(segments = t.segments.mapIndexed { i, x ->
        if (i == index) TranscriptEdit.withText(x, text.text).copy(speaker = speaker, comment = comment.trim().ifBlank { null }) else x
    })
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { e -> transcriptKey(e, control, playing, onLoop) },
        title = { Text("Abschnitt bei ${TranscriptExport.clock(s.start)}–${TranscriptExport.clock(s.end)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (t.diarized) ChoiceField("Sprecher", t.speakers.keys.sorted().map { it to t.speakerName(it) }, speaker, { speaker = it })
                OutlinedTextField(comment, { comment = it }, label = { Text("Kommentar (nicht Teil des Gesprochenen)") }, minLines = 1, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (control.available) {
                        TextButton(onClick = { control.play(s.start) }) { Text("▶ Abspielen") }
                        TextButton(onClick = {
                            val next = TranscriptEdit.setStart(edited(), index, control.position())
                            if (next == null) message = "Die Abspielposition liegt nicht zwischen dem vorigen Abschnitt und dem Ende dieses Abschnitts."
                            else { onSave(next); onDismiss() }
                        }) { Text("Start = ${TranscriptExport.clock(control.position())}") }
                    }
                    TextButton(onClick = {
                        val next = TranscriptEdit.split(edited(), index, text.selection.start, control.position().takeIf { control.available })
                        if (next == null) message = "Zum Teilen den Cursor vor das Wort setzen, mit dem der neue Abschnitt beginnt. Ohne gemessene Wortzeit " +
                            "die Wiedergabe genau an diese Stelle bringen – die Abspielposition wird dann der neue Anfang."
                        else { onSave(next); onDismiss() }
                    }) { Text("Am Cursor teilen") }
                    if (index > 0) TextButton(onClick = { TranscriptEdit.mergeWithNext(edited(), index - 1)?.let(onSave); onDismiss() }) { Text("Mit vorigem verbinden") }
                    if (index < t.segments.lastIndex) TextButton(onClick = { TranscriptEdit.mergeWithNext(edited(), index)?.let(onSave); onDismiss() }) { Text("Mit nächstem verbinden") }
                    TextButton(onClick = { onSave(t.copy(segments = t.segments.filterIndexed { i, _ -> i != index }.mapIndexed { i, x -> x.copy(id = i) })); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Löschen")
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                val measured = s.words.count { it.measured }
                HintText(
                    (if (measured > 0) "Wortzeiten: $measured von ${TranscriptEdit.textWords(s).size} Wörtern gemessen; geänderte Wörter behalten ihre Zeit, neue liegen dazwischen. "
                    else "Für diesen Abschnitt gibt es keine gemessenen Wortzeiten. ") + "F4 Abspielen/Anhalten, F3/F5 zurück/vor, F6 wiederholen – auch beim Tippen.",
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(edited()); onDismiss() }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
