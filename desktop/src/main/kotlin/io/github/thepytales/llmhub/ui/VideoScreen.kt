package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Extensions
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.video.AudioClip
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.RoughCut
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import io.github.thepytales.llmhub.video.VideoRender
import io.github.thepytales.llmhub.video.VideoStore
import io.github.thepytales.llmhub.video.VideoTitles
import io.github.thepytales.llmhub.video.newVideoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The video projects of this PC's data folder (AP-25). */
object VideoHub {
    val store: VideoStore by lazy { VideoStore(io.github.thepytales.llmhub.App.container.appContext.filesDir) }
}

private val VIDEO_MEDIA = listOf("mp4", "mov", "mkv", "avi", "wmv", "m4v", "webm", "mpg", "mpeg", "3gp", "png", "jpg", "jpeg")
private val AUDIO_MEDIA = listOf("mp3", "m4a", "wav", "ogg", "opus", "flac", "aac", "wma", "mp4", "mov", "webm")

internal fun clock(sec: Double, tenths: Boolean = false): String {
    val s = sec.coerceAtLeast(0.0)
    val m = (s / 60).toInt()
    val r = s - m * 60
    return if (tenths) String.format(Locale.GERMANY, "%d:%04.1f", m, r) else String.format(Locale.GERMANY, "%d:%02d", m, r.toInt())
}

/**
 * Videoeditor (AP-25): the projects, and the editor of one. On the first visit it offers to set up the
 * Videoeditor-Paket (an extension, AP-25); it also works without it with the app's ffmpeg.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(c: AppContainer, onBack: () -> Unit) {
    val prefs = remember { c.appContext.getSharedPreferences("ui_state", 0) }
    var setupSeen by remember { mutableStateOf(prefs.getBoolean("videoSetupSeen", false) || Extensions.installed(Extensions.VIDEO.id)) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    open?.let { id ->
        VideoEditor(c, id, onClose = { open = null })
        return
    }
    val projects by VideoHub.store.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun newProject() = scope.launch {
        val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Videos auswählen"), multiple = true, extensions = VIDEO_MEDIA) }
        if (files.isEmpty()) return@launch
        val p = withContext(Dispatchers.IO) { projectFrom(files) }
        if (p == null) Notices.error(tr("Die Dateien ließen sich nicht lesen – sind es Videos?")) else open = VideoHub.store.save(p).id
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Videoeditor")) },
                actions = { TextButton(onClick = { newProject() }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(4.dp)); Text(tr("Neues Projekt")) } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!setupSeen) item {
                    ExtensionSetupCard(c, Extensions.VIDEO) {
                        prefs.edit().putBoolean("videoSetupSeen", true).apply()
                        setupSeen = true
                    }
                }
                if (projects.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LoraFigure(Modifier.size(width = 120.dp, height = 84.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.SCISSORS, LoraAction.HEADPHONES, LoraAction.LOOK), LoraAction.SCISSORS))
                        Text(tr("Noch keine Videoprojekte"), style = MaterialTheme.typography.titleMedium)
                        HintText(tr("Lehrvideos schneiden, Titel und Bauchbinden im TUD-Design einblenden, Untertitel aus der Transkription, Pausen und Füllwörter automatisch herausschneiden – und als MP4 für OPAL oder YouTube speichern. Die Videos bleiben, wo sie sind."))
                        Button(onClick = { newProject() }) { Text(tr("Videos auswählen")) }
                    }
                }
                items(projects, key = { it.id }) { p -> ProjectRow(p, onOpen = { open = p.id }, onDelete = {
                    VideoHub.store.delete(p.id)
                    Notices.undo("„${p.title}“ " + tr("gelöscht"), restore = { VideoHub.store.save(p, touch = false) })
                }) }
            }
        }
    }
}

/** A new project of [files]: every file a clip at full length; size from the first video (at most Full HD). */
internal fun projectFrom(files: List<File>): VideoProject? {
    val clips = files.mapNotNull { f -> Media.probe(f)?.takeIf { it.duration > 0.05 || it.hasVideo }?.let { i -> VideoClip(path = f.absolutePath, inSec = 0.0, outSec = if (i.duration > 0.05) i.duration else 5.0) } }
    if (clips.isEmpty()) return null
    val first = Media.probe(File(clips.first().path))
    val (w, h) = when {
        first == null || first.width <= 0 || first.height <= 0 -> 1920 to 1080
        first.height > first.width -> 1080 to 1920
        abs(first.width.toDouble() / first.height - 1.0) < 0.05 -> 1080 to 1080
        else -> 1920 to 1080
    }
    val fps = first?.fps?.takeIf { it in 20.0..61.0 }?.let { if (it > 40) 50 else if (it > 27) 30 else 25 } ?: 25
    return VideoProject(title = File(clips.first().path).nameWithoutExtension, width = w, height = h, fps = fps, clips = clips)
}

private val DATE = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY)

@Composable
private fun ProjectRow(p: VideoProject, onOpen: () -> Unit, onDelete: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, p.id) {
        value = withContext(Dispatchers.IO) { p.clips.firstOrNull()?.let { Media.thumbnail(File(it.path), it.inSec + 0.5) } }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(128.dp).aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                thumb?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } ?: Icon(Icons.Outlined.Movie, null, tint = Color.White)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(clock(p.duration), "${p.clips.size} " + tr("Clips"), "${p.width} × ${p.height}", DATE.format(Date(p.updatedAt))).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
        }
    }
}

// ── Editor ─────────────────────────────────────────────────────────────────────────────────────────

private sealed interface Sel {
    data object None : Sel
    data class Clip(val id: String) : Sel
    data class Title(val id: String) : Sel
    data class Sound(val id: String) : Sel
}

/** History of a project for undo/redo; repeated changes of one field within 1.5 s are one step. */
private class History(initial: VideoProject) {
    var project by mutableStateOf(initial)
        private set
    var version by mutableIntStateOf(0)
        private set
    private val back = ArrayDeque<VideoProject>()
    private val forward = ArrayDeque<VideoProject>()
    private var lastKey: String? = null
    private var lastAt = 0L
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun edit(key: String? = null, record: Boolean = true, f: (VideoProject) -> VideoProject) {
        val next = f(project)
        if (next == project) return
        val now = System.currentTimeMillis()
        if (record && !(key != null && key == lastKey && now - lastAt < 1500)) {
            back.addLast(project); if (back.size > 200) back.removeFirst(); forward.clear()
        }
        lastKey = key; lastAt = now
        project = next; version++
        canUndo = back.isNotEmpty(); canRedo = forward.isNotEmpty()
    }

    fun mark() {
        back.addLast(project); if (back.size > 200) back.removeFirst(); forward.clear(); lastKey = null
        canUndo = true; canRedo = false
    }

    fun undo() { back.removeLastOrNull()?.let { forward.addLast(project); project = it; version++ }; lastKey = null; canUndo = back.isNotEmpty(); canRedo = forward.isNotEmpty() }
    fun redo() { forward.removeLastOrNull()?.let { back.addLast(project); project = it; version++ }; lastKey = null; canUndo = back.isNotEmpty(); canRedo = forward.isNotEmpty() }
}

@Composable
private fun VideoEditor(c: AppContainer, id: String, onClose: () -> Unit) {
    val initial = remember(id) { VideoHub.store.get(id) }
    if (initial == null) { LaunchedEffect(id) { onClose() }; return }
    val h = remember(id) { History(initial) }
    val player = remember(id) { VideoPlayer() }
    DisposableEffect(id) { onDispose { player.dispose(); VideoHub.store.save(h.project) } }
    var saved by remember { mutableIntStateOf(0) }
    LaunchedEffect(h.version) {
        if (h.version == saved) return@LaunchedEffect
        delay(600)
        withContext(Dispatchers.IO) { VideoHub.store.save(h.project) }
        saved = h.version
    }
    // Proxy files for a smooth preview, one after another in the background.
    LaunchedEffect(h.project.clips.map { it.path }.toSet()) {
        withContext(Dispatchers.IO) { h.project.clips.map { File(it.path) }.distinct().forEach { runCatching { Media.makeProxy(it) } } }
    }
    val p = h.project
    val pos by player.position.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()
    var sel by remember { mutableStateOf<Sel>(Sel.None) }
    LaunchedEffect(id) { player.seek(p, 0.0) }
    // A pause in editing shows the picture at the playhead again (the clip under it may have changed).
    LaunchedEffect(h.version) { delay(250); if (!player.playing.value) player.seek(h.project, player.position.value) }
    var export by remember { mutableStateOf(false) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    fun split() {
        val (i, src) = p.at(pos) ?: return
        val clip = p.clips[i]
        if (src - clip.inSec < 0.2 || clip.outSec - src < 0.2) return
        val a = clip.copy(outSec = src)
        val b = clip.copy(id = newVideoId(), inSec = src, transition = "none")
        h.edit { it.copy(clips = it.clips.toMutableList().apply { set(i, a); add(i + 1, b) }) }
        sel = Sel.Clip(b.id)
    }
    fun deleteSelected() {
        when (val s = sel) {
            is Sel.Clip -> h.edit { pp -> pp.copy(clips = pp.clips.filter { it.id != s.id }) }
            is Sel.Title -> h.edit { pp -> pp.copy(overlays = pp.overlays.filter { it.id != s.id }) }
            is Sel.Sound -> h.edit { pp -> pp.copy(audio = pp.audio.filter { it.id != s.id }) }
            Sel.None -> return
        }
        sel = Sel.None
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).focusRequester(focus).focusable().onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                ev.isCtrlPressed && ev.key == Key.Z -> { h.undo(); true }
                ev.isCtrlPressed && ev.key == Key.Y -> { h.redo(); true }
                ev.key == Key.Spacebar -> { player.toggle(p); true }
                ev.key == Key.S && !ev.isCtrlPressed -> { split(); true }
                ev.key == Key.Delete -> { deleteSelected(); true }
                ev.key == Key.DirectionLeft -> { player.seek(p, pos - 1.0 / p.fps); true }
                ev.key == Key.DirectionRight -> { player.seek(p, pos + 1.0 / p.fps); true }
                else -> false
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück")) }
            var title by remember(id) { mutableStateOf(p.title) }
            OutlinedTextField(title, { t -> title = t; if (t.isNotBlank()) h.edit("title") { it.copy(title = t.trim()) } }, singleLine = true, modifier = Modifier.width(320.dp), textStyle = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            Text(tr("{0} × {1} · {2} fps · {3}", p.width, p.height, p.fps, clock(p.duration)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = h::undo, enabled = h.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = tr("Rückgängig")) }
            IconButton(onClick = h::redo, enabled = h.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = tr("Wiederholen")) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { player.stop(); export = true }, enabled = p.clips.isNotEmpty()) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Exportieren")) }
        }
        HorizontalDivider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                Preview(p, player, pos, Modifier.weight(1f).fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { player.seek(p, 0.0) }) { Icon(Icons.Outlined.SkipPrevious, contentDescription = tr("Zum Anfang")) }
                    IconButton(onClick = { player.toggle(p) }) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = tr(if (playing) "Pause" else "Abspielen") + tr(" (Leertaste)")) }
                    IconButton(onClick = { player.seek(p, p.duration) }) { Icon(Icons.Outlined.SkipNext, contentDescription = tr("Zum Ende")) }
                    Text("${clock(pos, true)} / ${clock(p.duration, true)}", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = ::split) { Icon(Icons.Outlined.ContentCut, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Teilen") + " (S)") }
                }
            }
            VerticalDivider()
            Box(Modifier.width(320.dp).fillMaxHeight()) { Properties(c, h, sel, pos, onSelect = { sel = it }, onSplit = ::split, onDelete = ::deleteSelected) }
        }
        HorizontalDivider()
        TimelineBar(c, h, player, onSelect = { sel = it })
        Timeline(h, player, pos, sel, onSelect = { sel = it; runCatching { focus.requestFocus() } }, modifier = Modifier.fillMaxWidth().height(250.dp))
    }
    LaunchedEffect(id) { runCatching { focus.requestFocus() } }
    if (export) ExportDialog(p, onDismiss = { export = false })
}

/** The picture at the playhead, with the titles and subtitles that are on at that time. */
@Composable
private fun Preview(p: VideoProject, player: VideoPlayer, pos: Double, modifier: Modifier) {
    val frame by player.frame.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier.background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        val ratio = p.width.toFloat() / p.height
        val w = min(maxWidth.value, maxHeight.value * ratio)
        Box(Modifier.size(w.dp, (w / ratio).dp).background(Color.Black)) {
            frame?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            val px = with(LocalDensity.current) { w.dp.roundToPx() }.coerceAtLeast(64)
            p.overlays.filter { pos >= it.startSec && pos < it.endSec }.forEach { o ->
                val img = rememberTitle(o, p, px)
                img?.let { Image(it, null, modifier = Modifier.fillMaxSize()) }
            }
            p.subtitles.firstOrNull { pos >= it.startSec && pos < it.endSec }?.let { cue ->
                val img = rememberSubtitle(cue.text, p, px)
                img?.let { Image(it, null, modifier = Modifier.fillMaxSize()) }
            }
            if (p.clips.isEmpty()) Text(tr("Noch keine Clips – unten „Clip hinzufügen“."), color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

private val titleCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 24
}

@Composable
private fun rememberTitle(o: TextOverlay, p: VideoProject, px: Int): ImageBitmap? {
    val key = "${o.style}|${o.text}|${o.subtext}|${p.width}x${p.height}|$px"
    val img by produceState(synchronized(titleCache) { titleCache[key] }, key) {
        if (value == null) value = withContext(Dispatchers.Default) { runCatching { VideoTitles.render(VideoTitles.design(o, p.width, p.height), px) }.getOrNull() }?.also { synchronized(titleCache) { titleCache[key] = it } }
    }
    return img
}

@Composable
private fun rememberSubtitle(text: String, p: VideoProject, px: Int): ImageBitmap? {
    val key = "sub|$text|${p.width}x${p.height}|$px"
    val img by produceState(synchronized(titleCache) { titleCache[key] }, key) {
        if (value == null) value = withContext(Dispatchers.Default) { runCatching { VideoTitles.render(VideoTitles.subtitleDesign(text, p.width, p.height), px) }.getOrNull() }?.also { synchronized(titleCache) { titleCache[key] = it } }
    }
    return img
}

// ── Toolbar above the timeline ─────────────────────────────────────────────────────────────────────

@Composable
private fun TimelineBar(c: AppContainer, h: History, player: VideoPlayer, onSelect: (Sel) -> Unit) {
    val scope = rememberCoroutineScope()
    var titles by remember { mutableStateOf(false) }
    var subs by remember { mutableStateOf(false) }
    var cut by remember { mutableStateOf(false) }
    var pickTranscript by remember { mutableStateOf<String?>(null) }
    var loraCut by remember { mutableStateOf(false) }
    val p = h.project
    val pos = player.position.value
    fun addClips() = scope.launch {
        val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Videos auswählen"), multiple = true, extensions = VIDEO_MEDIA) }
        val clips = withContext(Dispatchers.IO) { files.mapNotNull { f -> Media.probe(f)?.let { i -> VideoClip(path = f.absolutePath, inSec = 0.0, outSec = if (i.duration > 0.05) i.duration else 5.0) } } }
        if (clips.isNotEmpty()) h.edit { it.copy(clips = it.clips + clips) }
    }
    fun addAudio() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Musik oder Sprecherton auswählen"), multiple = false, extensions = AUDIO_MEDIA).firstOrNull() } ?: return@launch
        val info = withContext(Dispatchers.IO) { Media.probe(f) }
        if (info?.hasAudio != true) { Notices.error(tr("In der Datei ist kein Ton.")); return@launch }
        val a = AudioClip(path = f.absolutePath, startSec = pos, outSec = minOf(info.duration, maxOf(1.0, p.duration - pos)))
        h.edit { it.copy(audio = it.audio + a) }
        onSelect(Sel.Sound(a.id))
    }
    fun importSubs() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Untertitel (SRT oder VTT)"), multiple = false, extensions = listOf("srt", "vtt")).firstOrNull() } ?: return@launch
        val cues = withContext(Dispatchers.IO) { runCatching { VideoRender.parseSubtitles(f.readText()) }.getOrDefault(emptyList()) }
        if (cues.isEmpty()) Notices.error(tr("In der Datei wurden keine Untertitel gefunden.")) else {
            h.edit { it.copy(subtitles = cues) }
            Notices.info("${cues.size} " + tr("Untertitel übernommen."))
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { addClips() }) { Icon(Icons.Outlined.Movie, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Clip hinzufügen")) }
        Box {
            TextButton(onClick = { titles = true }) { Icon(Icons.Outlined.Title, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Titel & Bauchbinde")) }
            DropdownMenu(expanded = titles, onDismissRequest = { titles = false }) {
                TextOverlay.STYLES.forEach { (style, label) ->
                    DropdownMenuItem(text = { Text(tr(label)) }, onClick = {
                        titles = false
                        val start = when (style) { "titel" -> 0.0; "abspann" -> maxOf(0.0, p.duration - 5.0); else -> pos }
                        val o = TextOverlay(
                            style = style, startSec = start, durationSec = if (style == "bauchbinde") 6.0 else 4.0,
                            text = when (style) { "titel" -> p.title; "abspann" -> tr("Vielen Dank"); "bauchbinde" -> tr("Vorname Nachname"); else -> tr("Wichtiger Hinweis") },
                            subtext = when (style) { "titel" -> tr("Lehrvideo · TU Dresden"); "abspann" -> "tu-dresden.de"; "bauchbinde" -> tr("Professur … · TU Dresden"); else -> "" },
                        )
                        h.edit { it.copy(overlays = it.overlays + o) }
                        onSelect(Sel.Title(o.id))
                    })
                }
            }
        }
        TextButton(onClick = { addAudio() }) { Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Musik / Ton")) }
        Box {
            TextButton(onClick = { subs = true }) { Icon(Icons.Outlined.Subtitles, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Untertitel")) }
            DropdownMenu(expanded = subs, onDismissRequest = { subs = false }) {
                DropdownMenuItem(text = { Text(tr("Aus der Transkription …")) }, onClick = { subs = false; pickTranscript = "subs" })
                DropdownMenuItem(text = { Text(tr("SRT/VTT importieren …")) }, onClick = { subs = false; importSubs() })
                if (p.subtitles.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Alle Untertitel entfernen")) }, onClick = { subs = false; h.edit { it.copy(subtitles = emptyList()) } })
            }
        }
        Box {
            TextButton(onClick = { cut = true }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Rohschnitt")) }
            DropdownMenu(expanded = cut, onDismissRequest = { cut = false }) {
                DropdownMenuItem(text = { Text(tr("Pausen und Füllwörter herausschneiden …")) }, onClick = { cut = false; pickTranscript = "cut" })
                DropdownMenuItem(text = { Text(tr("Mit Lora kürzen …")) }, onClick = { cut = false; loraCut = true })
            }
        }
    }
    pickTranscript?.let { purpose ->
        TranscriptPicker(c, h.project, onDismiss = { pickTranscript = null }) { map ->
            pickTranscript = null
            h.edit { it.copy(transcripts = it.transcripts + map.mapValues { e -> e.value.id }) }
            scope.launch {
                val all = loadTranscripts(c, h.project)
                if (purpose == "subs") {
                    val cues = RoughCut.subtitles(h.project, all)
                    if (cues.isEmpty()) Notices.error(tr("Im Transkript passt nichts zu den Clips."))
                    else { h.edit { it.copy(subtitles = cues, burnSubtitles = true) }; Notices.info("${cues.size} " + tr("Untertitel aus der Transkription.")) }
                } else {
                    var saved = 0.0
                    h.edit { pp ->
                        var next = pp
                        // From the back, so the indices of the clips before stay valid.
                        for (i in pp.clips.indices.reversed()) {
                            val clip = pp.clips[i]
                            val t = all[clip.path] ?: continue
                            val parts = RoughCut.keep(t, clip.inSec, clip.outSec)
                            if (parts.isEmpty()) continue
                            saved += RoughCut.saved(clip.inSec, clip.outSec, parts)
                            next = RoughCut.apply(next, i, parts)
                        }
                        next
                    }
                    Notices.info(if (saved > 0.5) tr("Rohschnitt fertig:") + " ${clock(saved)} " + tr("kürzer. Mit Strg+Z rückgängig.") else tr("Nichts zum Herausschneiden gefunden."))
                }
            }
        }
    }
    if (loraCut) LoraCutDialog(c, h, onDismiss = { loraCut = false })
}

private suspend fun loadTranscripts(c: AppContainer, p: VideoProject): Map<String, Transcript> = withContext(Dispatchers.IO) {
    p.transcripts.mapNotNull { (path, tid) -> c.transcription.load(tid)?.let { path to it } }.toMap()
}

/** Which transcript belongs to which source file; or transcribe a video now. */
@Composable
private fun TranscriptPicker(c: AppContainer, p: VideoProject, onDismiss: () -> Unit, onPick: (Map<String, io.github.thepytales.llmhub.transcribe.TranscriptSummary>) -> Unit) {
    val list by c.transcription.transcripts.collectAsStateWithLifecycle()
    val sources = p.clips.map { it.path }.distinct()
    val chosen = remember { mutableStateOf(sources.associateWith { path -> list.firstOrNull { it.id == p.transcripts[path] } ?: list.firstOrNull { t -> t.title.equals(File(path).nameWithoutExtension, ignoreCase = true) } }) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Transkript wählen")) },
        text = {
            Column(Modifier.width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HintText(tr("Für jedes Video das Transkript aus „Transkription“. Gibt es noch keins: „Jetzt transkribieren“ – das Video erscheint unter Transkription zum Starten, danach hier auswählen."))
                sources.forEach { path ->
                    var menu by remember { mutableStateOf(false) }
                    Column {
                        Text(File(path).name, style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                OutlinedButton(onClick = { menu = true }) { Text(chosen.value[path]?.title ?: tr("Transkript auswählen"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    list.take(40).forEach { t ->
                                        DropdownMenuItem(text = { Text("${t.title} · ${clock(t.duration)}") }, onClick = { menu = false; chosen.value = chosen.value + (path to t) })
                                    }
                                    if (list.isEmpty()) DropdownMenuItem(text = { Text(tr("Noch keine Transkripte")) }, onClick = { menu = false }, enabled = false)
                                }
                            }
                            Spacer(Modifier.size(8.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    val f = File(path)
                                    runCatching { c.transcription.import(f.name, f.length(), withContext(Dispatchers.IO) { f.inputStream() }) }
                                        .onSuccess {
                                            Notices.info(tr("Das Video liegt unter Transkription bereit – dort starten, danach hier das Transkript wählen."))
                                            Navigation.openRoute.value = "transcripts"
                                            onDismiss()
                                        }
                                        .onFailure { Notices.error(it.message ?: tr("Das Video ließ sich nicht übernehmen.")) }
                                }
                            }) { Text(tr("Jetzt transkribieren")) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = chosen.value.values.any { it != null }, onClick = { onPick(chosen.value.filterValues { it != null }.mapValues { it.value!! }) }) { Text(tr("Übernehmen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** „Mit Lora kürzen“: a wish for the selected or the first clip with a transcript; Lora picks the passages. */
@Composable
private fun LoraCutDialog(c: AppContainer, h: History, onDismiss: () -> Unit) {
    var wish by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val withTranscript = h.project.clips.indexOfFirst { it.path in h.project.transcripts }
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(tr("Mit Lora kürzen")) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (withTranscript < 0) HintText(tr("Dafür braucht ein Clip ein Transkript: unter Rohschnitt › Pausen und Füllwörter oder Untertitel › Aus der Transkription eines zuordnen."))
                else {
                    HintText(tr("Lora liest das Transkript des Clips „{0}“ und wählt die Abschnitte, die bleiben. Der Text geht an das Standardmodell, Namen pseudonymisiert.", File(h.project.clips[withTranscript].path).name))
                    OutlinedTextField(wish, { wish = it }, minLines = 2, modifier = Modifier.fillMaxWidth(), placeholder = { Text(tr("z. B. „Nur die Erklärung des Versuchsaufbaus, höchstens 5 Minuten“")) })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = withTranscript >= 0 && wish.isNotBlank() && !running, onClick = {
                running = true; error = null
                scope.launch {
                    val clip = h.project.clips[withTranscript]
                    val result = runCatching {
                        val t = withContext(Dispatchers.IO) { c.transcription.load(h.project.transcripts.getValue(clip.path)) } ?: error(tr("Das Transkript fehlt."))
                        RoughCut.withLora(c, t, clip.inSec, clip.outSec, wish)
                    }
                    running = false
                    result.onSuccess { parts ->
                        val i = h.project.clips.indexOfFirst { it.id == clip.id }
                        if (i >= 0) h.edit { RoughCut.apply(it, i, parts) }
                        Notices.info(tr("Gekürzt auf") + " ${clock(parts.sumOf { it.second - it.first })}. " + tr("Mit Strg+Z rückgängig."))
                        onDismiss()
                    }.onFailure { error = it.message ?: it.javaClass.simpleName }
                }
            }) { if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(tr("Kürzen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !running) { Text(tr("Abbrechen")) } },
    )
}

// ── Timeline ────────────────────────────────────────────────────────────────────────────────────────

private const val RULER = 22f
private const val VIDEO_H = 60f
private const val LANE_H = 32f
private const val SUB_H = 22f

private data class Lane(val top: Float, val height: Float)

@Composable
private fun Timeline(h: History, player: VideoPlayer, pos: Double, sel: Sel, onSelect: (Sel) -> Unit, modifier: Modifier) {
    val p = h.project
    val measurer = rememberTextMeasurer()
    var zoom by remember { mutableStateOf(1f) }
    var scroll by remember { mutableStateOf(0f) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val brand = LocalChatLook.current.brand
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(tr("Zoom"), style = MaterialTheme.typography.labelSmall)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..12f, modifier = Modifier.width(180.dp))
            Spacer(Modifier.weight(1f))
            HintText(tr("Klick in die Leiste: springen · Clips ziehen: verschieben · Ränder ziehen: kürzen · S teilt, Entf löscht, Leertaste spielt."))
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0x0F000000))) {
            val width = constraints.maxWidth.toFloat()
            val left = 70f
            val total = maxOf(p.duration, 1.0)
            val pxPerSec = ((width - left - 20f) / total * zoom).toFloat()
            val maxScroll = maxOf(0f, (total * pxPerSec).toFloat() - (width - left - 20f))
            val sc = scroll.coerceIn(0f, maxScroll)
            fun x(t: Double) = left + (t * pxPerSec).toFloat() - sc
            fun t(x: Float) = ((x - left + sc) / pxPerSec).toDouble()
            // The gesture handler lives longer than one composition (dragging changes the project): it reads these.
            val now = remember { arrayOfNulls<Any>(3) }
            now[0] = pxPerSec; now[1] = sc; now[2] = maxScroll
            val video = Lane(RULER, VIDEO_H)
            val titles = Lane(RULER + VIDEO_H + 4, LANE_H)
            val sound = Lane(titles.top + LANE_H + 4, LANE_H)
            val subs = Lane(sound.top + LANE_H + 4, SUB_H)
            val starts = p.starts()
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val p = h.project
                            val starts = p.starts()
                            val pxPerSec = now[0] as Float
                            val sc = now[1] as Float
                            fun x(t: Double) = left + (t * pxPerSec).toFloat() - sc
                            fun t(x: Float) = ((x - left + sc) / pxPerSec).toDouble()
                            val at = down.position
                            val time = t(at.x)
                            var action: ((Float) -> Unit)? = null
                            when {
                                at.y < RULER || at.x < left -> { player.seek(h.project, time); action = { dx -> player.seek(h.project, t(at.x + dx)) } }
                                at.y in video.top..(video.top + video.height) -> {
                                    val i = starts.indices.lastOrNull { k -> time >= starts[k] && time <= starts[k] + p.clips[k].length } ?: -1
                                    if (i >= 0) {
                                        val c = p.clips[i]
                                        onSelect(Sel.Clip(c.id))
                                        val x0 = x(starts[i]); val x1 = x(starts[i] + c.length)
                                        h.mark()
                                        action = when {
                                            abs(at.x - x0) < 8 -> { dx -> h.edit(record = false) { pp -> pp.copy(clips = pp.clips.map { if (it.id == c.id) it.copy(inSec = (c.inSec + dx / pxPerSec).coerceIn(0.0, c.outSec - 0.2)) else it }) } }
                                            abs(at.x - x1) < 8 -> { dx ->
                                                val max = Media.probe(File(c.path))?.duration?.takeIf { it > 0.05 } ?: Double.MAX_VALUE
                                                h.edit(record = false) { pp -> pp.copy(clips = pp.clips.map { if (it.id == c.id) it.copy(outSec = (c.outSec + dx / pxPerSec).coerceIn(c.inSec + 0.2, max)) else it }) }
                                            }
                                            else -> { dx ->
                                                // Reorder: the clip goes where its middle is dropped.
                                                val mid = starts[i] + c.length / 2 + dx / pxPerSec
                                                val target = starts.indices.count { k -> k != i && starts[k] + p.clips[k].length / 2 < mid }
                                                if (target != i) h.edit(record = false) { pp -> pp.copy(clips = h.project.clips.toMutableList().apply { removeAt(indexOfFirst { it.id == c.id }); add(target.coerceIn(0, size), c) }) }
                                            }
                                        }
                                    }
                                }
                                at.y in titles.top..(titles.top + titles.height) -> {
                                    val o = p.overlays.lastOrNull { time >= it.startSec && time <= it.endSec }
                                    if (o != null) {
                                        onSelect(Sel.Title(o.id)); h.mark()
                                        val atEnd = abs(at.x - x(o.endSec)) < 8
                                        action = { dx ->
                                            h.edit(record = false) { pp -> pp.copy(overlays = pp.overlays.map {
                                                if (it.id != o.id) it else if (atEnd) it.copy(durationSec = (o.durationSec + dx / pxPerSec).coerceAtLeast(0.5)) else it.copy(startSec = (o.startSec + dx / pxPerSec).coerceAtLeast(0.0))
                                            }) }
                                        }
                                    } else onSelect(Sel.None)
                                }
                                at.y in sound.top..(sound.top + sound.height) -> {
                                    val a = p.audio.lastOrNull { time >= it.startSec && time <= it.endSec }
                                    if (a != null) {
                                        onSelect(Sel.Sound(a.id)); h.mark()
                                        action = { dx -> h.edit(record = false) { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(startSec = (a.startSec + dx / pxPerSec).coerceAtLeast(0.0)) else it }) } }
                                    } else onSelect(Sel.None)
                                }
                                else -> { onSelect(Sel.None); player.seek(h.project, time) }
                            }
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                action?.invoke(ch.position.x - at.x)
                                ch.consume()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) {
                                    val d = ev.changes.first().scrollDelta
                                    scroll = (scroll + (d.y + d.x) * 60f).coerceIn(0f, now[2] as Float)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            ) {
                /** Text only where it fits (drawText fails for places outside the canvas). */
                fun label(text: String, at: Offset, style: TextStyle, width: Float? = null) {
                    if (at.y > size.height - 12f || at.x > size.width - 12f || (width != null && width < 8f)) return
                    if (width == null) drawText(measurer, text, at, style = style, maxLines = 1)
                    else drawText(measurer, text, at, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, size = Size(minOf(width, size.width - at.x), minOf(16f, size.height - at.y)))
                }
                // Lane names.
                val small = TextStyle(fontSize = 11.sp, color = labelColor)
                listOf(tr("Video") to video, tr("Titel") to titles, tr("Ton") to sound, tr("Untertitel") to subs).forEach { (name, lane) ->
                    label(name, Offset(8f, lane.top + lane.height / 2 - 8f), small)
                }
                // Ruler.
                val step = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0).first { it * pxPerSec >= 70 }
                var s = (t(left) / step).toInt() * step
                while (x(s) < size.width) {
                    if (x(s) >= left) {
                        drawLine(labelColor.copy(alpha = 0.5f), Offset(x(s), 0f), Offset(x(s), RULER), 1f)
                        label(clock(s), Offset(x(s) + 3f, 2f), TextStyle(fontSize = 10.sp, color = labelColor))
                    }
                    s += step
                }
                // Clips.
                p.clips.forEachIndexed { i, c ->
                    val x0 = maxOf(left, x(starts[i])); val x1 = x(starts[i] + c.length)
                    if (x1 < left) return@forEachIndexed
                    val selected = sel is Sel.Clip && (sel as Sel.Clip).id == c.id
                    drawRoundRect(if (i % 2 == 0) Color(0xFF2F57B2) else Color(0xFF00008C), Offset(x0, video.top), Size(maxOf(2f, x1 - x0 - 1f), video.height), CornerRadius(5f))
                    if (selected) drawRoundRect(Color(0xFFEE7F00), Offset(x0, video.top), Size(maxOf(2f, x1 - x0 - 1f), video.height), CornerRadius(5f), style = Stroke(3f))
                    if (x1 - x0 > 40) label(c.name, Offset(x0 + 6f, video.top + 6f), TextStyle(fontSize = 11.sp, color = Color.White), x1 - x0 - 10f)
                    if (x1 - x0 > 40) label(clock(c.length, true) + if (c.muted) " · " + tr("stumm") else "", Offset(x0 + 6f, video.top + 24f), TextStyle(fontSize = 10.sp, color = Color(0xCCFFFFFF)), x1 - x0 - 10f)
                    if (p.transitionOf(i) > 0) {
                        val tx = x(starts[i]); val tw = (p.transitionOf(i) * pxPerSec).toFloat()
                        drawRect(Color(0x66FFFFFF), Offset(tx, video.top + video.height - 10f), Size(tw, 8f))
                    }
                }
                // Titles, sound, subtitles.
                p.overlays.forEach { o ->
                    val x0 = maxOf(left, x(o.startSec)); val x1 = x(o.endSec)
                    if (x1 < left) return@forEach
                    val selected = sel is Sel.Title && (sel as Sel.Title).id == o.id
                    drawRoundRect(Color(0xFF97C6FF), Offset(x0, titles.top), Size(maxOf(2f, x1 - x0), titles.height), CornerRadius(5f))
                    if (selected) drawRoundRect(Color(0xFFEE7F00), Offset(x0, titles.top), Size(maxOf(2f, x1 - x0), titles.height), CornerRadius(5f), style = Stroke(3f))
                    if (x1 - x0 > 30) label(TextOverlay.label(o.style) + ": " + o.text, Offset(x0 + 5f, titles.top + 8f), TextStyle(fontSize = 10.sp, color = Color(0xFF001450)), x1 - x0 - 8f)
                }
                p.audio.forEach { a ->
                    val x0 = maxOf(left, x(a.startSec)); val x1 = x(a.endSec)
                    if (x1 < left) return@forEach
                    val selected = sel is Sel.Sound && (sel as Sel.Sound).id == a.id
                    drawRoundRect(Color(0xFF65B32E), Offset(x0, sound.top), Size(maxOf(2f, x1 - x0), sound.height), CornerRadius(5f))
                    if (selected) drawRoundRect(Color(0xFFEE7F00), Offset(x0, sound.top), Size(maxOf(2f, x1 - x0), sound.height), CornerRadius(5f), style = Stroke(3f))
                    if (x1 - x0 > 30) label(a.name, Offset(x0 + 5f, sound.top + 8f), TextStyle(fontSize = 10.sp, color = Color.White), x1 - x0 - 8f)
                }
                p.subtitles.forEach { cue ->
                    val x0 = maxOf(left, x(cue.startSec)); val x1 = x(cue.endSec)
                    if (x1 >= left) drawRect(if (p.burnSubtitles) Color(0xFF566371) else Color(0x66566371), Offset(x0, subs.top), Size(maxOf(1f, x1 - x0 - 1f), subs.height))
                }
                // Playhead.
                val px = x(pos)
                if (px >= left) {
                    drawLine(brand, Offset(px, 0f), Offset(px, size.height), 2f)
                    drawCircle(brand, 5f, Offset(px, 6f))
                }
            }
        }
    }
}

// ── Properties ──────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Properties(c: AppContainer, h: History, sel: Sel, pos: Double, onSelect: (Sel) -> Unit, onSplit: () -> Unit, onDelete: () -> Unit) {
    val p = h.project
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (sel) {
            is Sel.Clip -> {
                val clip = p.clips.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                val i = p.clips.indexOf(clip)
                Text(tr("Clip") + " ${i + 1}: ${clip.name}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Anfang (s)"), clip.inSec, "in-${clip.id}", Modifier.weight(1f)) { v -> h.edit("in-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(inSec = v.coerceIn(0.0, it.outSec - 0.2)) else it }) } }
                    SecondsField(tr("Ende (s)"), clip.outSec, "out-${clip.id}", Modifier.weight(1f)) { v -> h.edit("out-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(outSec = maxOf(v, it.inSec + 0.2)) else it }) } }
                }
                Text(tr("Länge") + " ${clock(clip.length, true)}", style = MaterialTheme.typography.bodySmall)
                Text(tr("Lautstärke") + " ${(clip.volume * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                Slider(value = clip.volume, onValueChange = { v -> h.edit("vol-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(volume = v) else it }) } }, valueRange = 0f..2f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Ton aus"), modifier = Modifier.weight(1f))
                    Switch(clip.muted, { m -> h.edit { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(muted = m) else it }) } })
                }
                if (i > 0) {
                    Text(tr("Übergang vom vorigen Clip"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("none" to tr("Schnitt"), "fade" to tr("Überblenden"), "black" to tr("Über Schwarz")).forEach { (k, l) ->
                            FilterChip(selected = clip.transition == k, onClick = { h.edit { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(transition = k) else it }) } }, label = { Text(l) })
                        }
                    }
                    if (clip.transition != "none") SecondsField(tr("Dauer des Übergangs (s)"), clip.transitionSec, "tr-${clip.id}") { v ->
                        h.edit("tr-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(transitionSec = v.coerceIn(0.1, 3.0)) else it }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSplit) { Text(tr("Am Abspielkopf teilen")) }
                    OutlinedButton(onClick = onDelete) { Text(tr("Löschen")) }
                }
                p.transcripts[clip.path]?.let { HintText(tr("Transkript zugeordnet – Untertitel und Rohschnitt kommen daraus.")) }
            }
            is Sel.Title -> {
                val o = p.overlays.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                Text(tr(TextOverlay.label(o.style)), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextOverlay.STYLES.forEach { (k, l) -> FilterChip(selected = o.style == k, onClick = { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(style = k) else it }) } }, label = { Text(tr(l)) }) }
                }
                OutlinedTextField(o.text, { t -> h.edit("ot-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(text = t) else it }) } }, label = { Text(tr(if (o.style == "bauchbinde") "Name" else "Text")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(o.subtext, { t -> h.edit("os-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(subtext = t) else it }) } }, label = { Text(tr(if (o.style == "bauchbinde") "Funktion / Einrichtung" else "Zweite Zeile")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Beginn (s)"), o.startSec, "ost-${o.id}", Modifier.weight(1f)) { v -> h.edit("ost-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = v.coerceAtLeast(0.0)) else it }) } }
                    SecondsField(tr("Dauer (s)"), o.durationSec, "od-${o.id}", Modifier.weight(1f)) { v -> h.edit("od-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(durationSec = v.coerceAtLeast(0.5)) else it }) } }
                }
                OutlinedButton(onClick = { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = pos) else it }) } }) { Text(tr("An den Abspielkopf")) }
                OutlinedButton(onClick = onDelete) { Text(tr("Löschen")) }
            }
            is Sel.Sound -> {
                val a = p.audio.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(tr("Lautstärke") + " ${(a.volume * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                Slider(value = a.volume, onValueChange = { v -> h.edit("avol-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(volume = v) else it }) } }, valueRange = 0f..1.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Beginn (s)"), a.startSec, "ast-${a.id}", Modifier.weight(1f)) { v -> h.edit("ast-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(startSec = v.coerceAtLeast(0.0)) else it }) } }
                    SecondsField(tr("Länge (s)"), a.length, "al-${a.id}", Modifier.weight(1f)) { v -> h.edit("al-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(outSec = it.inSec + v.coerceAtLeast(0.5)) else it }) } }
                }
                SecondsField(tr("Ein- und Ausblenden (s)"), a.fadeSec, "af-${a.id}") { v -> h.edit("af-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(fadeSec = v.coerceIn(0.0, 10.0)) else it }) } }
                OutlinedButton(onClick = onDelete) { Text(tr("Löschen")) }
            }
            Sel.None -> ProjectSettings(h)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectSettings(h: History) {
    val p = h.project
    Text(tr("Projekt"), style = MaterialTheme.typography.titleSmall)
    Text(tr("Format"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Triple(1920, 1080, "16:9 Full HD"), Triple(1280, 720, "16:9 HD"), Triple(1080, 1920, "9:16 hoch"), Triple(1080, 1080, "1:1")).forEach { (w, hh, l) ->
            FilterChip(selected = p.width == w && p.height == hh, onClick = { h.edit { it.copy(width = w, height = hh) } }, label = { Text(l) })
        }
    }
    Text(tr("Bilder pro Sekunde"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(25, 30, 50).forEach { f -> FilterChip(selected = p.fps == f, onClick = { h.edit { it.copy(fps = f) } }, label = { Text("$f") }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Am Anfang und Ende aus Schwarz blenden"), modifier = Modifier.weight(1f))
        Switch(p.fadeEnds, { v -> h.edit { it.copy(fadeEnds = v) } })
    }
    HorizontalDivider()
    Text(tr("Ton"), style = MaterialTheme.typography.titleSmall)
    Text(tr("Rauschminderung"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(tr("aus") to tr("Aus"), "leicht" to tr("Leicht"), "stark" to tr("Stark")).forEach { (k, l) ->
            FilterChip(selected = p.sound.denoise == k, onClick = { h.edit { it.copy(sound = it.sound.copy(denoise = k)) } }, label = { Text(l) })
        }
    }
    HintText(if (Media.rnnoiseModel() != null) tr("Mit dem Sprachmodell des Videoeditor-Pakets (RNNoise).") else tr("Ohne Videoeditor-Paket mit dem einfacheren Filter von ffmpeg; das Paket gibt es unter Einstellungen › Werkzeuge › Erweiterungen."))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Lautheit angleichen"))
            HintText(tr("Gleichmäßig laut wie bei YouTube und OPAL üblich (−16 LUFS)."))
        }
        Switch(p.sound.normalize, { v -> h.edit { it.copy(sound = it.sound.copy(normalize = v)) } })
    }
    Text(tr("Pegel") + tr(" {0} dB", "%+.0f".format(p.sound.gainDb)), style = MaterialTheme.typography.labelMedium)
    Slider(value = p.sound.gainDb, onValueChange = { v -> h.edit("gain") { it.copy(sound = it.sound.copy(gainDb = Math.round(v).toFloat())) } }, valueRange = -12f..12f)
    HorizontalDivider()
    Text(tr("Untertitel") + if (p.subtitles.isNotEmpty()) " · ${p.subtitles.size}" else "", style = MaterialTheme.typography.titleSmall)
    if (p.subtitles.isEmpty()) HintText(tr("Unten über „Untertitel“ aus der Transkription holen oder eine SRT-/VTT-Datei laden."))
    else Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Ins Bild einbrennen"))
            HintText(tr("Sonst nur als SRT/VTT-Datei neben dem Video (für OPAL und YouTube zum Zuschalten)."))
        }
        Switch(p.burnSubtitles, { v -> h.edit { it.copy(burnSubtitles = v) } })
    }
}

/** Seconds with a decimal comma or point. */
@Composable
private fun SecondsField(label: String, value: Double, key: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (Double) -> Unit) {
    fun show(v: Double) = String.format(Locale.GERMANY, "%.2f", v)
    var text by remember(key) { mutableStateOf(show(value)) }
    LaunchedEffect(value) { if (text.replace(',', '.').toDoubleOrNull()?.let { abs(it - value) > 0.005 } != false) text = show(value) }
    OutlinedTextField(text, { t -> text = t; t.replace(',', '.').toDoubleOrNull()?.let(onChange) }, label = { Text(label, maxLines = 1) }, singleLine = true, modifier = modifier, textStyle = MaterialTheme.typography.bodySmall)
}

// ── Export ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportDialog(p: VideoProject, onDismiss: () -> Unit) {
    var preset by remember { mutableStateOf(VideoRender.PRESETS.first().id) }
    var subtitleFiles by remember { mutableStateOf(p.subtitles.isNotEmpty() && !p.burnSubtitles) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text(tr("Video exportieren")) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoRender.PRESETS.forEach { pr ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = progress == null) { preset = pr.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = preset == pr.id, onClick = { preset = pr.id }, enabled = progress == null)
                        Column {
                            Text(pr.label + " · " + VideoRender.size(p, pr).let { (w, hh) -> "$w × $hh" })
                            HintText(pr.hint)
                        }
                    }
                }
                if (p.subtitles.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(subtitleFiles, { subtitleFiles = it }, enabled = progress == null)
                    Text(tr("Untertitel zusätzlich als SRT und VTT speichern"))
                }
                HintText(tr("Länge") + " ${clock(p.duration)} · " + tr("das Rechnen dauert etwa so lange wie das Video, je nach PC auch länger."))
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()); Text("${(it * 100).toInt()} %", style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 8) }
            }
        },
        confirmButton = {
            Button(enabled = progress == null, onClick = {
                job = scope.launch {
                    val target = withContext(Dispatchers.IO) { DesktopActions.pickSaveFile(tr("Video speichern"), VideoHub.store.fileName(p, "mp4"), "mp4") } ?: return@launch
                    progress = 0f; error = null
                    runCatching {
                        VideoRender.render(p, VideoRender.preset(preset), target) { v -> progress = v }
                        if (subtitleFiles) withContext(Dispatchers.IO) {
                            File(target.parentFile, target.nameWithoutExtension + ".srt").writeText(VideoRender.srt(p))
                            File(target.parentFile, target.nameWithoutExtension + ".vtt").writeText(VideoRender.vtt(p))
                        }
                    }.onSuccess {
                        progress = null
                        Notices.show(Notices.Notice(tr("Video gespeichert:") + " " + target.absolutePath, action = tr("Öffnen"), onAction = { DesktopActions.open(target) }))
                        onDismiss()
                    }.onFailure { e ->
                        progress = null
                        if (e !is kotlinx.coroutines.CancellationException) error = e.message ?: e.javaClass.simpleName
                    }
                }
            }) { Text(tr("Speichern unter …")) }
        },
        dismissButton = {
            TextButton(onClick = { if (progress != null) { job?.cancel(); progress = null } else onDismiss() }) { Text(tr(if (progress != null) "Abbrechen" else "Schließen")) }
        },
    )
}
