package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FiberManualRecord
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.utf16CodePoint
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
import io.github.thepytales.llmhub.video.VideoEdit
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
    // Lora made a project (AP-38): it opens here.
    val loraOpen by io.github.thepytales.llmhub.video.VideoLora.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(loraOpen) { loraOpen?.let { open = it; io.github.thepytales.llmhub.video.VideoLora.openRequest.value = null } }
    open?.let { id ->
        VideoEditor(c, id, onClose = { open = null })
        return
    }
    val projects by VideoHub.store.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var recordDialog by remember { mutableStateOf(false) }
    if (recordDialog) ScreenRecordDialog(c, onDismiss = { recordDialog = false })
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
                actions = {
                    // Bildschirmaufnahme (AP-56): the result comes back as a project.
                    Spot("video.record") { TextButton(onClick = { recordDialog = true }) { Icon(Icons.Outlined.FiberManualRecord, null, tint = androidx.compose.ui.graphics.Color(0xFFE53935)); Spacer(Modifier.size(4.dp)); Text(tr("Bildschirm aufnehmen")) } }
                    ExportQueueChip()
                    Spot("video.new") { TextButton(onClick = { newProject() }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(4.dp)); Text(tr("Neues Projekt")) } }
                },
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

internal sealed interface Sel {
    data object None : Sel
    data class Clip(val id: String) : Sel
    data class Title(val id: String) : Sel
    data class Sound(val id: String) : Sel
    /** A picture on V2 … (AP-52). */
    data class Layer(val id: String) : Sel
    /** A marker (AP-53). */
    data class Mark(val id: String) : Sel
}

/** History of a project for undo/redo; repeated changes of one field within 1.5 s are one step. Linked sound follows its clip. */
internal class History(initial: VideoProject) {
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
        val next = f(project).linked()
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
    // Projects from before AP-53 get their files into the media pool.
    val initial = remember(id) { VideoHub.store.get(id)?.let { VideoEdit.withPool(it) } }
    if (initial == null) { LaunchedEffect(id) { onClose() }; return }
    val h = remember(id) { History(initial) }
    val player = remember(id) { VideoPlayer() }
    val source = remember(id) { VideoPlayer() }
    DisposableEffect(id) { onDispose { player.dispose(); source.dispose(); VideoHub.store.save(h.project) } }
    var saved by remember { mutableIntStateOf(0) }
    LaunchedEffect(h.version) {
        if (h.version == saved) return@LaunchedEffect
        delay(600)
        withContext(Dispatchers.IO) { VideoHub.store.save(h.project) }
        saved = h.version
    }
    // Proxy files for a smooth preview, one after another in the background.
    LaunchedEffect((h.project.clips.map { it.path } + h.project.layers.map { it.path } + h.project.media.map { it.path }).toSet()) {
        withContext(Dispatchers.IO) {
            (h.project.clips.map { it.path } + h.project.layers.map { it.path } + h.project.media.map { it.path }).distinct().map { File(it) }.filter { it.isFile }.forEach { runCatching { Media.makeProxy(it) } }
        }
    }
    val p = h.project
    val pos by player.position.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    var sel by remember { mutableStateOf<Sel>(Sel.None) }
    var tool by remember { mutableStateOf(CutTool.SELECT) }
    var sourceView by remember { mutableStateOf(false) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var loop by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf(false) }
    val drag = remember { PoolDrag() }
    // Left panel: media pool or the transcript (textbasierter Schnitt, AP-55).
    var leftTab by remember { mutableStateOf("media") }
    val textCut = remember(id) { TextCutState() }
    var assignTranscript by remember { mutableStateOf(false) }
    val sourceItem = p.media.firstOrNull { it.id == sourceId }
    val sourceInfo by produceState<io.github.thepytales.llmhub.video.MediaInfo?>(null, sourceItem?.path) { value = sourceItem?.let { withContext(Dispatchers.IO) { Media.probe(File(it.path)) } } }
    val sourceLen = sourceItem?.let { if (Media.isStill(File(it.path), sourceInfo)) 5.0 else sourceInfo?.duration?.takeIf { d -> d > 0.05 } }
    val sourceProject = remember(sourceItem?.path, sourceLen) {
        if (sourceItem == null || sourceLen == null) null
        else VideoProject(title = sourceItem.name, fadeEnds = false, width = p.width, height = p.height, clips = listOf(VideoClip(path = sourceItem.path, inSec = 0.0, outSec = sourceLen)), sound = io.github.thepytales.llmhub.video.AudioSettings(normalize = false))
    }
    val srcPos by source.position.collectAsStateWithLifecycle()
    val srcPlaying by source.playing.collectAsStateWithLifecycle()
    LaunchedEffect(sourceProject) { sourceProject?.let { source.seek(it, sourceItem?.inSec ?: 0.0) } }
    LaunchedEffect(id) { player.seek(p, 0.0) }
    // Lora's actions (AP-38 Stufe d) change this project through its history, so Strg+Z takes them back as well.
    DisposableEffect(id) {
        val host = io.github.thepytales.llmhub.video.VideoLora.Host(id, { h.project }, { f -> h.edit(record = true, f = f) }, { player.position.value })
        io.github.thepytales.llmhub.video.VideoLora.host = host
        io.github.thepytales.llmhub.video.VideoLora.lastId = id
        onDispose { if (io.github.thepytales.llmhub.video.VideoLora.host === host) io.github.thepytales.llmhub.video.VideoLora.host = null }
    }
    // „Sofort transkribieren“ (AP-56): transcripts that were being made are assigned as soon as they are there.
    val transcriptList by c.transcription.transcripts.collectAsStateWithLifecycle()
    LaunchedEffect(transcriptList, p.pendingTranscripts) {
        if (p.pendingTranscripts.isEmpty()) return@LaunchedEffect
        val found = p.pendingTranscripts.mapNotNull { (path, title) -> transcriptList.firstOrNull { it.title == title }?.let { path to it.id } }.toMap()
        if (found.isNotEmpty()) h.edit(record = false) { it.copy(transcripts = it.transcripts + found, pendingTranscripts = it.pendingTranscripts - found.keys) }
    }
    // A pause in editing shows the picture at the playhead again (the clip under it may have changed).
    LaunchedEffect(h.version) { delay(250); if (!player.playing.value) player.seek(h.project, player.position.value) }
    var export by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val typing = remember { TypingState() }
    val showSource = sourceView && sourceProject != null

    fun loopRange(): Pair<Double, Double>? = if (loop) (p.markIn ?: 0.0) to (p.markOut ?: p.duration) else null
    /** The marked piece of a pool file as a clip (In/Out of the source monitor, otherwise all of it). */
    fun clipOf(item: io.github.thepytales.llmhub.video.PoolItem): VideoClip? {
        val f = File(item.path)
        val info = Media.probe(f) ?: return null
        val len = if (Media.isStill(f, info)) 5.0 else info.duration.takeIf { it > 0.05 } ?: return null
        val a = (item.inSec ?: 0.0).coerceIn(0.0, len)
        val b = (item.outSec ?: len).coerceIn(a + 0.2, maxOf(len, a + 0.2))
        return VideoClip(path = item.path, inSec = a, outSec = b)
    }
    fun use(item: io.github.thepytales.llmhub.video.PoolItem, how: PoolUse, at: Double = player.position.value, lane: String? = null) {
        val clip = clipOf(item) ?: run { Notices.error(tr("Die Datei ließ sich nicht lesen.")); return }
        when (how) {
            PoolUse.INSERT -> { h.edit { VideoEdit.insert(it, clip, at) }; sel = Sel.Clip(clip.id) }
            PoolUse.OVERWRITE -> { h.edit { VideoEdit.overwrite(it, clip, at) }; sel = Sel.Clip(clip.id) }
            PoolUse.PIP -> {
                var made: String? = null
                h.edit { pp ->
                    val (q, lid) = VideoEdit.addLayer(pp, item.path, at, clip.length, still = Media.isStill(File(item.path)))
                    made = lid
                    val wanted = lane?.drop(1)?.toIntOrNull()?.takeIf { it >= 2 }
                    val placed = q.copy(layers = q.layers.map { if (it.id == lid) it.copy(inSec = clip.inSec, outSec = clip.outSec) else it })
                    if (wanted != null && placed.layers.none { it.id != lid && it.track == wanted && it.startSec < at + clip.length && it.endSec > at }) VideoEdit.moveLayer(placed, lid, at, wanted) else placed
                }
                made?.let { sel = Sel.Layer(it) }
            }
            PoolUse.SOUND -> {
                if (Media.probe(File(item.path))?.hasAudio != true) { Notices.error(tr("In der Datei ist kein Ton.")); return }
                val wanted = lane?.drop(1)?.toIntOrNull()
                val a = AudioClip(path = item.path, startSec = at, inSec = clip.inSec, outSec = clip.outSec, volume = 1f, fadeSec = 0.3, track = 1)
                h.edit { pp ->
                    val free = wanted?.takeIf { n -> pp.audio.none { it.track == n && it.startSec < at + a.length && it.endSec > at } } ?: VideoEdit.freeAudioTrack(pp, at, at + a.length)
                    pp.copy(audio = pp.audio + a.copy(track = free), audioTracks = maxOf(pp.audioTracks, free))
                }
                sel = Sel.Sound(a.id)
            }
        }
    }
    fun importToPool() = scope.launch {
        val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Dateien in den Medienpool"), multiple = true, extensions = (VIDEO_MEDIA + AUDIO_MEDIA).distinct()) }
        if (files.isNotEmpty()) h.edit { VideoEdit.addToPool(it, files.map { f -> f.absolutePath }) }
    }
    fun split() {
        val t = player.position.value
        val target: VideoEdit.Item? = when (val s = sel) {
            is Sel.Layer -> VideoEdit.Layer(s.id).takeIf { p.layers.any { it.id == s.id && t > it.startSec && t < it.endSec } }
            is Sel.Sound -> VideoEdit.Sound(s.id).takeIf { p.audio.any { it.id == s.id && t > it.startSec && t < it.endSec } }
            else -> null
        }
        val result = if (target != null) VideoEdit.split(p, target, t) else VideoEdit.splitV1(p, t)
        result?.let { (q, nid) ->
            h.edit { q }
            sel = when (target) { is VideoEdit.Layer -> Sel.Layer(nid); is VideoEdit.Sound -> Sel.Sound(nid); else -> Sel.Clip(nid) }
        }
    }
    fun deleteSelected(closeGap: Boolean = false) {
        fun magnet(f: (VideoProject) -> VideoProject): (VideoProject) -> VideoProject = { pp -> if (closeGap && !pp.magnet) f(pp.copy(magnet = true)).copy(magnet = false) else f(pp) }
        when (val s = sel) {
            is Sel.Clip -> h.edit(f = magnet { VideoEdit.delete(it, VideoEdit.Clip(s.id)) })
            is Sel.Layer -> h.edit(f = magnet { VideoEdit.delete(it, VideoEdit.Layer(s.id)) })
            is Sel.Sound -> h.edit(f = magnet { VideoEdit.delete(it, VideoEdit.Sound(s.id)) })
            is Sel.Title -> h.edit { pp -> pp.copy(overlays = pp.overlays.filter { it.id != s.id }) }
            is Sel.Mark -> h.edit { pp -> pp.copy(markers = pp.markers.filter { it.id != s.id }) }
            Sel.None -> return
        }
        sel = Sel.None
    }
    fun toggleLink() {
        when (val s = sel) {
            is Sel.Sound -> {
                val a = p.audio.firstOrNull { it.id == s.id } ?: return
                if (a.linkedTo != null) { h.edit { VideoEdit.unlink(it, a.id) }; Notices.info(tr("Verknüpfung gelöst.")) }
                else {
                    val i = p.at(a.startSec + 0.01)?.first ?: return
                    h.edit { VideoEdit.link(it, p.clips[i].id, a.id) }
                    Notices.info(tr("Mit Clip {0} verknüpft.", i + 1))
                }
            }
            is Sel.Clip -> if (p.audio.any { it.linkedTo == s.id }) {
                h.edit { pp -> pp.copy(audio = pp.audio.map { if (it.linkedTo == s.id) it.copy(linkedTo = null) else it }) }
                Notices.info(tr("Verknüpfung gelöst."))
            } else VideoEdit.detachSound(p, s.id)?.let { (q, _) -> h.edit { q }; Notices.info(tr("Ton auf eine Tonspur gelegt und verknüpft.")) }
            else -> {}
        }
    }
    fun cuts(): List<Double> {
        val s = p.starts()
        return (s + p.clips.indices.map { s[it] + p.clips[it].length } + p.layers.flatMap { listOf(it.startSec, it.endSec) } + p.markers.map { it.sec }).distinct().sorted()
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTyping provides typing) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).focusRequester(focus).focusable().onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // In a text field S, Leertaste, Entf and the arrows are typing: before, they also split the clip, started
            // the playback or deleted the selected title while its text was being edited.
            if (typing.fields > 0 && !ev.isCmd) return@onPreviewKeyEvent false
            // The source monitor takes the transport keys while it is shown (Q switches).
            val (pl, pr) = if (showSource) source to sourceProject!! else player to p
            val at = pl.position.value
            when {
                ev.utf16CodePoint == '?'.code || ev.key == Key.F1 -> { keys = true; true }
                ev.isCmd && ev.isAltPressed && ev.key == Key.L -> { toggleLink(); true }
                ev.isCmd && ev.key == Key.Z -> { h.undo(); true }
                ev.isCmd && ev.key == Key.Y -> { h.redo(); true }
                ev.isCmd && ev.key == Key.B -> { split(); true }
                ev.isAltPressed && ev.key == Key.X -> {
                    if (showSource) h.edit { pp -> pp.copy(media = pp.media.map { if (it.id == sourceId) it.copy(inSec = null, outSec = null) else it }) }
                    else h.edit { it.copy(markIn = null, markOut = null) }
                    true
                }
                ev.isCmd || ev.isAltPressed -> false
                ev.key == Key.Spacebar -> { if (showSource) source.toggle(pr) else player.toggle(p, loopRange()); true }
                ev.key == Key.K -> { pl.stop(); true }
                ev.key == Key.L -> { pl.forward(pr, if (showSource) null else loopRange()); true }
                ev.key == Key.J -> { pl.backward(pr); true }
                ev.key == Key.I -> {
                    if (showSource) h.edit { pp -> pp.copy(media = pp.media.map { if (it.id == sourceId) it.copy(inSec = at, outSec = it.outSec?.takeIf { o -> o > at + 0.2 }) else it }) }
                    else h.edit { it.copy(markIn = at, markOut = it.markOut?.takeIf { o -> o > at + 0.2 }) }
                    true
                }
                ev.key == Key.O -> {
                    if (showSource) h.edit { pp -> pp.copy(media = pp.media.map { if (it.id == sourceId) it.copy(outSec = at, inSec = it.inSec?.takeIf { i -> i < at - 0.2 }) else it }) }
                    else h.edit { it.copy(markOut = at, markIn = it.markIn?.takeIf { i -> i < at - 0.2 }) }
                    true
                }
                ev.key == Key.Q -> { if (sourceProject != null) sourceView = !sourceView; true }
                ev.key == Key.F9 -> { sourceItem?.let { use(it, PoolUse.INSERT) }; true }
                ev.key == Key.F10 -> { sourceItem?.let { use(it, PoolUse.OVERWRITE) }; true }
                ev.key == Key.F12 -> { sourceItem?.let { use(it, PoolUse.PIP) }; true }
                ev.key == Key.A -> { tool = CutTool.SELECT; true }
                ev.key == Key.B -> { tool = CutTool.BLADE; true }
                ev.key == Key.T -> { tool = CutTool.TRIM; true }
                ev.key == Key.N -> { h.edit { it.copy(magnet = !it.magnet) }; Notices.info(tr(if (!p.magnet) "Magnet an: Spuren ziehen mit, Lücken schließen sich." else "Magnet aus.")); true }
                ev.key == Key.M -> { val (q, mid) = VideoEdit.addMarker(p, player.position.value); h.edit { q }; sel = Sel.Mark(mid); true }
                ev.key == Key.S -> { split(); true }
                ev.key == Key.Delete || ev.key == Key.Backspace -> {
                    if (leftTab == "text" && textCut.hasSelection) textCut.deleteSelection(h) else deleteSelected(closeGap = ev.isShiftPressed)
                    true
                }
                ev.key == Key.DirectionLeft -> { pl.seek(pr, at - 1.0 / pr.fps); true }
                ev.key == Key.DirectionRight -> { pl.seek(pr, at + 1.0 / pr.fps); true }
                ev.key == Key.DirectionUp && !showSource -> { player.seek(p, cuts().lastOrNull { it < at - 1e-3 } ?: 0.0); true }
                ev.key == Key.DirectionDown && !showSource -> { player.seek(p, cuts().firstOrNull { it > at + 1e-3 } ?: p.duration); true }
                ev.key == Key.MoveHome -> { pl.seek(pr, 0.0); true }
                ev.key == Key.MoveEnd -> { pl.seek(pr, pr.duration); true }
                else -> false
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück")) }
            var title by remember(id) { mutableStateOf(p.title) }
            OutlinedTextField(title, { t -> title = t; if (t.isNotBlank()) h.edit("title") { it.copy(title = t.trim()) } }, singleLine = true, modifier = Modifier.width(280.dp).typing(), textStyle = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            Text(tr("{0} × {1} · {2} fps · {3}", p.width, p.height, p.fps, clock(p.duration)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            CutTool.entries.forEach { t ->
                FilterChip(selected = tool == t, onClick = { tool = t; runCatching { focus.requestFocus() } }, label = { Text(tr(t.label) + " (" + t.key + ")") }, modifier = Modifier.padding(end = 4.dp))
            }
            FilterChip(selected = p.magnet, onClick = { h.edit { it.copy(magnet = !it.magnet) } }, label = { Text(tr("Magnet") + " (N)") })
            IconButton(onClick = { keys = true }) { Icon(Icons.Outlined.Keyboard, contentDescription = tr("Tastenkürzel") + " (?)") }
            IconButton(onClick = h::undo, enabled = h.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = tr("Rückgängig")) }
            IconButton(onClick = h::redo, enabled = h.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = tr("Wiederholen")) }
            Spacer(Modifier.size(8.dp))
            ExportQueueChip()
            Spot("video.export") { Button(onClick = { player.stop(); export = true }, enabled = p.clips.isNotEmpty()) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Exportieren")) } }
        }
        HorizontalDivider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(if (leftTab == "text") 380.dp else 250.dp).fillMaxHeight()) {
                Row(Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = leftTab == "media", onClick = { leftTab = "media"; runCatching { focus.requestFocus() } }, label = { Text(tr("Medien")) })
                    Spot("video.transcript") { FilterChip(selected = leftTab == "text", onClick = { leftTab = "text"; runCatching { focus.requestFocus() } }, label = { Text(tr("Transkript")) }) }
                }
                if (leftTab == "text") TranscriptCutPanel(c, h, player, pos, textCut, onAssign = { assignTranscript = true }, modifier = Modifier.weight(1f).fillMaxWidth())
                else MediaPool(h, drag, sourceId, onOpen = { item -> sourceId = item.id; sourceView = true; player.stop(); runCatching { focus.requestFocus() } }, onImport = { importToPool() }, onUse = { item, how -> use(item, how) }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
            VerticalDivider()
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = showSource, enabled = sourceProject != null, onClick = { sourceView = true; player.stop() }, label = { Text(tr("Quelle") + (sourceItem?.let { ": " + it.name } ?: "")) })
                    FilterChip(selected = !showSource, onClick = { sourceView = false; source.stop() }, label = { Text(tr("Zeitleiste")) })
                    HintText(tr("Q wechselt"))
                }
                if (showSource) SourceViewer(sourceProject!!, sourceItem!!, source, srcPos, Modifier.weight(1f).fillMaxWidth())
                else Preview(p, player, pos, Modifier.weight(1f).fillMaxWidth())
                if (showSource) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { source.toggle(sourceProject!!) }) { Icon(if (srcPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = tr(if (srcPlaying) "Pause" else "Abspielen") + tr(" (Leertaste)")) }
                    Text("${clock(srcPos, true)} / ${clock(sourceProject!!.duration, true)}", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.size(8.dp))
                    val item = sourceItem!!
                    Text(tr("In {0} · Out {1}", clock(item.inSec ?: 0.0, true), clock(item.outSec ?: sourceProject!!.duration, true)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { use(item, PoolUse.INSERT) }) { Text(tr("Einfügen") + " (F9)") }
                    Spacer(Modifier.size(4.dp))
                    OutlinedButton(onClick = { use(item, PoolUse.OVERWRITE) }) { Text(tr("Überschreiben") + " (F10)") }
                    Spacer(Modifier.size(4.dp))
                    OutlinedButton(onClick = { use(item, PoolUse.PIP) }) { Text(tr("Bild-in-Bild") + " (F12)") }
                } else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { player.seek(p, 0.0) }) { Icon(Icons.Outlined.SkipPrevious, contentDescription = tr("Zum Anfang")) }
                    IconButton(onClick = { player.backward(p) }) { Icon(Icons.Outlined.FastRewind, contentDescription = tr("Rückwärts") + " (J)") }
                    IconButton(onClick = { player.toggle(p, loopRange()) }) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = tr(if (playing) "Pause" else "Abspielen") + tr(" (Leertaste)")) }
                    IconButton(onClick = { player.forward(p, loopRange()) }) { Icon(Icons.Outlined.FastForward, contentDescription = tr("Vorwärts") + " (L)") }
                    IconButton(onClick = { player.seek(p, p.duration) }) { Icon(Icons.Outlined.SkipNext, contentDescription = tr("Zum Ende")) }
                    Text("${clock(pos, true)} / ${clock(p.duration, true)}", style = MaterialTheme.typography.labelLarge)
                    if (playing && speed != 1.0 && speed != 0.0) Text("  " + (if (speed < 0) "◀ " else "▶ ") + "${kotlin.math.abs(speed).toInt()}×", style = MaterialTheme.typography.labelLarge, color = LocalChatLook.current.brand)
                    Spacer(Modifier.size(8.dp))
                    FilterChip(selected = loop, onClick = { loop = !loop }, label = { Text(tr("Schleife")) }, leadingIcon = { Icon(Icons.Outlined.Repeat, null, Modifier.size(16.dp)) })
                    if (p.markIn != null || p.markOut != null) Text("  " + tr("In {0} · Out {1}", clock(p.markIn ?: 0.0, true), clock(p.markOut ?: p.duration, true)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { val (q, mid) = VideoEdit.addMarker(p, pos); h.edit { q }; sel = Sel.Mark(mid) }) { Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Marker") + " (M)") }
                    Spacer(Modifier.size(4.dp))
                    OutlinedButton(onClick = ::split) { Icon(Icons.Outlined.ContentCut, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Teilen") + " (S)") }
                }
            }
            VerticalDivider()
            Box(Modifier.width(320.dp).fillMaxHeight()) { Properties(c, h, sel, pos, onSelect = { sel = it }, onSplit = ::split, onDelete = { deleteSelected() }, onSeek = { player.seek(h.project, it) }) }
        }
        HorizontalDivider()
        TimelineBar(c, h, player, onSelect = { sel = it })
        VideoTimelineView(
            h, player, pos, sel, tool, drag,
            onSelect = { sel = it; runCatching { focus.requestFocus() } },
            onDropItem = { item, lane, t ->
                when {
                    lane == "V1" -> use(item, PoolUse.INSERT, nearestCut(p, t))
                    lane.startsWith("V") -> use(item, PoolUse.PIP, t, lane)
                    else -> use(item, PoolUse.SOUND, t, lane)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }
    LaunchedEffect(id) { runCatching { focus.requestFocus() } }
    if (assignTranscript) TranscriptPicker(c, p, onDismiss = { assignTranscript = false }) { map ->
        assignTranscript = false
        h.edit { it.copy(transcripts = it.transcripts + map.mapValues { e -> e.value.id }) }
    }
    if (export) ExportDialog(c, p, onDismiss = { export = false })
    if (keys) VideoShortcutsDialog { keys = false; runCatching { focus.requestFocus() } }
}

/** Dropping on V1 inserts at the nearest cut (or the end), not in the middle of a clip. */
private fun nearestCut(p: VideoProject, t: Double): Double {
    val s = p.starts()
    val cuts = s + listOf(p.duration)
    return cuts.minByOrNull { abs(it - t) } ?: 0.0
}

/** Source monitor (AP-53): the file with its own playhead and the In–Out piece marked under the picture. */
@Composable
private fun SourceViewer(sp: VideoProject, item: io.github.thepytales.llmhub.video.PoolItem, player: VideoPlayer, pos: Double, modifier: Modifier) {
    val frame by player.frame.collectAsStateWithLifecycle()
    val brand = LocalChatLook.current.brand
    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
            val ratio = sp.width.toFloat() / sp.height
            val w = min(maxWidth.value, maxHeight.value * ratio)
            Box(Modifier.size(w.dp, (w / ratio).dp).background(Color.Black)) {
                frame?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                Text(tr("Quelle"), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x88000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        val total = maxOf(sp.duration, 0.1)
        Canvas(
            Modifier.fillMaxWidth().height(18.dp).pointerInput(sp) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    player.seek(sp, (down.position.x / size.width * total).coerceIn(0.0, total))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        player.seek(sp, (ch.position.x / size.width * total).coerceIn(0.0, total))
                        ch.consume()
                    }
                }
            },
        ) {
            drawRect(Color(0x22000000), Offset(0f, 4f), Size(size.width, size.height - 8f))
            val a = ((item.inSec ?: 0.0) / total * size.width).toFloat()
            val b = ((item.outSec ?: total) / total * size.width).toFloat()
            drawRect(brand.copy(alpha = 0.45f), Offset(a, 4f), Size(maxOf(1f, b - a), size.height - 8f))
            val px = (pos / total * size.width).toFloat()
            drawLine(brand, Offset(px, 0f), Offset(px, size.height), 2f)
        }
    }
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
                // Faded in and out as in the export.
                val fade = minOf(0.4, o.durationSec / 4)
                val alpha = if (fade <= 0) 1f else minOf(1.0, (pos - o.startSec) / fade, (o.endSec - pos) / fade).toFloat().coerceIn(0f, 1f)
                img?.let { Image(it, null, modifier = Modifier.fillMaxSize().alpha(alpha)) }
            }
            p.subtitles.firstOrNull { pos >= it.startSec && pos < it.endSec }?.let { cue ->
                val img = rememberSubtitle(cue.text, p, px, p.subtitleRaised(cue))
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
private fun rememberSubtitle(text: String, p: VideoProject, px: Int, raised: Boolean): ImageBitmap? {
    val key = "sub|$text|${p.width}x${p.height}|$px|$raised"
    val img by produceState(synchronized(titleCache) { titleCache[key] }, key) {
        if (value == null) value = withContext(Dispatchers.Default) { runCatching { VideoTitles.render(VideoTitles.subtitleDesign(text, p.width, p.height, raised), px) }.getOrNull() }?.also { synchronized(titleCache) { titleCache[key] = it } }
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
    // The playhead when a button is used: read then, not at the last composition of this bar (which does not follow
    // the playback – music was put where the playhead had been before).
    fun pos() = player.position.value
    fun addClips() = scope.launch {
        val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Videos auswählen"), multiple = true, extensions = VIDEO_MEDIA) }
        val clips = withContext(Dispatchers.IO) { files.mapNotNull { f -> Media.probe(f)?.let { i -> VideoClip(path = f.absolutePath, inSec = 0.0, outSec = if (i.duration > 0.05) i.duration else 5.0) } } }
        if (clips.isNotEmpty()) h.edit { VideoEdit.addToPool(it.copy(clips = it.clips + clips), clips.map { c -> c.path }) }
    }
    fun addPicture() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Bild-in-Bild: Video oder Bild auswählen"), multiple = false, extensions = VIDEO_MEDIA).firstOrNull() } ?: return@launch
        val info = withContext(Dispatchers.IO) { Media.probe(f) } ?: run { Notices.error(tr("Die Datei ließ sich nicht lesen.")); return@launch }
        val still = Media.isStill(f, info)
        val at = pos()
        val len = if (still) 5.0 else info.duration.takeIf { it > 0.05 } ?: 5.0
        var made: String? = null
        h.edit { pp -> VideoEdit.addLayer(VideoEdit.addToPool(pp, listOf(f.absolutePath)), f.absolutePath, at, len, still = still).let { (q, lid) -> made = lid; q } }
        made?.let { onSelect(Sel.Layer(it)) }
    }
    fun addAudio() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Musik oder Sprecherton auswählen"), multiple = false, extensions = AUDIO_MEDIA).firstOrNull() } ?: return@launch
        val info = withContext(Dispatchers.IO) { Media.probe(f) }
        if (info?.hasAudio != true) { Notices.error(tr("In der Datei ist kein Ton.")); return@launch }
        val at = pos()
        val a = AudioClip(path = f.absolutePath, startSec = at, outSec = minOf(info.duration, maxOf(1.0, h.project.duration - at)))
        h.edit { pp ->
            val tr0 = VideoEdit.freeAudioTrack(pp, a.startSec, a.endSec)
            VideoEdit.addToPool(pp.copy(audio = pp.audio + a.copy(track = tr0), audioTracks = maxOf(pp.audioTracks, tr0)), listOf(a.path))
        }
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
            Spot("video.titles") { TextButton(onClick = { titles = true }) { Icon(Icons.Outlined.Title, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Titel & Bauchbinde")) } }
            DropdownMenu(expanded = titles, onDismissRequest = { titles = false }) {
                TextOverlay.STYLES.forEach { (style, label) ->
                    DropdownMenuItem(text = { Text(tr(label)) }, onClick = {
                        titles = false
                        val start = when (style) { "titel" -> 0.0; "abspann" -> maxOf(0.0, p.duration - 5.0); else -> pos() }
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
        Spot("video.pip") { TextButton(onClick = { addPicture() }) { Icon(Icons.Outlined.PictureInPicture, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Bild-in-Bild")) } }
        TextButton(onClick = { addAudio() }) { Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Musik / Ton")) }
        Box {
            Spot("video.subtitles") { TextButton(onClick = { subs = true }) { Icon(Icons.Outlined.Subtitles, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Untertitel")) } }
            DropdownMenu(expanded = subs, onDismissRequest = { subs = false }) {
                DropdownMenuItem(text = { Text(tr("Aus der Transkription …")) }, onClick = { subs = false; pickTranscript = "subs" })
                DropdownMenuItem(text = { Text(tr("SRT/VTT importieren …")) }, onClick = { subs = false; importSubs() })
                if (p.subtitles.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Alle Untertitel entfernen")) }, onClick = { subs = false; h.edit { it.copy(subtitles = emptyList()) } })
            }
        }
        Box {
            Spot("video.roughcut") { TextButton(onClick = { cut = true }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Rohschnitt")) } }
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
                    OutlinedTextField(wish, { wish = it }, minLines = 2, modifier = Modifier.fillMaxWidth().typing(), placeholder = { Text(tr("z. B. „Nur die Erklärung des Versuchsaufbaus, höchstens 5 Minuten“")) })
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

// ── Properties ──────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Properties(c: AppContainer, h: History, sel: Sel, pos: Double, onSelect: (Sel) -> Unit, onSplit: () -> Unit, onDelete: () -> Unit, onSeek: (Double) -> Unit) {
    val p = h.project
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (sel) {
            is Sel.Layer -> LayerProperties(h, sel.id, pos, onDelete, onGone = { onSelect(Sel.None) })
            is Sel.Mark -> MarkerProperties(h, sel.id, onSeek, onDelete, onGone = { onSelect(Sel.None) })
            is Sel.Clip -> {
                val clip = p.clips.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                val i = p.clips.indexOf(clip)
                // Length of the source: the end cannot go past it (a picture has none and may stand as long as wanted).
                val srcLen = sourceLength(clip.path)
                Text(tr("Clip") + " ${i + 1}: ${clip.name}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Anfang (s)"), clip.inSec, "in-${clip.id}", Modifier.weight(1f)) { v -> h.edit("in-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(inSec = v.coerceIn(0.0, it.outSec - 0.2)) else it }) } }
                    SecondsField(tr("Ende (s)"), clip.outSec, "out-${clip.id}", Modifier.weight(1f)) { v -> h.edit("out-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(outSec = maxOf(srcLen?.let { m -> minOf(v, m) } ?: v, it.inSec + 0.2)) else it }) } }
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
                // Picture and sound (AP-53): the clip's sound onto an audio track, linked.
                val linked = p.audio.filter { it.linkedTo == clip.id }
                if (linked.isNotEmpty()) {
                    HintText(tr("Verknüpfter Ton auf {0} – er bewegt und kürzt sich mit dem Clip.", linked.joinToString(", ") { "A${it.track}" }))
                    TextButton(onClick = { h.edit { pp -> pp.copy(audio = pp.audio.map { if (it.linkedTo == clip.id) it.copy(linkedTo = null) else it }) } }) { Text(tr("Verknüpfung lösen")) }
                }
                if (!clip.muted) OutlinedButton(onClick = { VideoEdit.detachSound(p, clip.id)?.let { (q, aid) -> h.edit { q }; onSelect(Sel.Sound(aid)) } }) { Text(tr("Ton auf eine Tonspur legen")) }
                var place by remember(clip.id) { mutableStateOf(!clip.frame.isFull) }
                if (!place) TextButton(onClick = { place = true }) { Text(tr("Position und Größe …")) }
                else FrameEditor(clip.frame, "cf-${clip.id}") { k, f -> h.edit(k) { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(frame = f) else it }) } }
                ClipEffectsEditor(h, clip, pos, onSelect)
                p.transcripts[clip.path]?.let { HintText(tr("Transkript zugeordnet – Untertitel und Rohschnitt kommen daraus.")) }
            }
            is Sel.Title -> {
                val o = p.overlays.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                Text(tr(TextOverlay.label(o.style)), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextOverlay.STYLES.forEach { (k, l) -> FilterChip(selected = o.style == k, onClick = { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(style = k) else it }) } }, label = { Text(tr(l)) }) }
                }
                OutlinedTextField(o.text, { t -> h.edit("ot-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(text = t) else it }) } }, label = { Text(tr(if (o.style == "bauchbinde") "Name" else "Text")) }, modifier = Modifier.fillMaxWidth().typing(), maxLines = 3)
                OutlinedTextField(o.subtext, { t -> h.edit("os-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(subtext = t) else it }) } }, label = { Text(tr(if (o.style == "bauchbinde") "Funktion / Einrichtung" else "Zweite Zeile")) }, modifier = Modifier.fillMaxWidth().typing(), maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Beginn (s)"), o.startSec, "ost-${o.id}", Modifier.weight(1f)) { v -> h.edit("ost-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = v.coerceAtLeast(0.0)) else it }) } }
                    SecondsField(tr("Dauer (s)"), o.durationSec, "od-${o.id}", Modifier.weight(1f)) { v -> h.edit("od-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(durationSec = v.coerceAtLeast(0.5)) else it }) } }
                }
                OutlinedButton(onClick = { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = pos) else it }) } }) { Text(tr("An den Abspielkopf")) }
                OutlinedButton(onClick = onDelete) { Text(tr("Löschen")) }
            }
            is Sel.Sound -> {
                val a = p.audio.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                val srcLen = sourceLength(a.path)
                Text("A${a.track}: " + a.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val owner = a.linkedTo?.let { l -> p.clips.indexOfFirst { it.id == l } }?.takeIf { it >= 0 }
                if (owner != null) {
                    HintText(tr("Mit Clip {0} verknüpft – bewegt und kürzt sich mit ihm. Versatz zur Quelle: {1} s", owner + 1, String.format(Locale.GERMANY, "%+.2f", a.linkOffset)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { h.edit { VideoEdit.unlink(it, a.id) } }) { Text(tr("Verknüpfung lösen")) }
                        OutlinedButton(onClick = { onSelect(Sel.Clip(p.clips[owner].id)) }) { Text(tr("Zum Clip")) }
                    }
                } else {
                    // A second sound source (lapel microphone, recorder): synchronise over the waveform with a clip.
                    val candidates = p.clips.indices.filter { i -> val s0 = p.starts()[i]; s0 < a.endSec && s0 + p.clips[i].length > a.startSec }.ifEmpty { p.clips.indices.toList() }
                    var target by remember(a.id) { mutableStateOf(candidates.firstOrNull()) }
                    var mute by remember(a.id) { mutableStateOf(true) }
                    var busy by remember(a.id) { mutableStateOf(false) }
                    var menu by remember(a.id) { mutableStateOf(false) }
                    if (target != null && p.clips.isNotEmpty()) {
                        Text(tr("Mit dem Bild synchronisieren"), style = MaterialTheme.typography.labelLarge)
                        Box {
                            OutlinedButton(onClick = { menu = true }) { Text(tr("Clip {0}: {1}", target!! + 1, p.clips.getOrNull(target!!)?.name ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                p.clips.forEachIndexed { i, cc -> DropdownMenuItem(text = { Text(tr("Clip {0}: {1}", i + 1, cc.name)) }, onClick = { target = i; menu = false }) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(mute, { mute = it })
                            Text(tr("Kameraton stumm schalten"), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = {
                                val clip = p.clips.getOrNull(target!!) ?: return@Button
                                busy = true
                                scope.launch {
                                    val off = withContext(Dispatchers.IO) { runCatching { io.github.thepytales.llmhub.video.AudioSync.offsetOf(File(clip.path), File(a.path)) }.getOrNull() }
                                    busy = false
                                    if (off == null) Notices.error(tr("Die Tonspuren passen nicht zusammen – ist es dieselbe Aufnahme?"))
                                    else {
                                        h.edit { VideoEdit.link(it, clip.id, a.id, off, mute) }
                                        Notices.info(tr("Synchronisiert: der Ton liegt {0} s versetzt und ist jetzt mit dem Clip verknüpft.", String.format(Locale.GERMANY, "%+.2f", off)))
                                    }
                                }
                            }) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(tr("Über die Wellenform")) }
                            OutlinedButton(onClick = { h.edit { VideoEdit.link(it, p.clips[target!!].id, a.id, null, false) } }) { Text(tr("Verknüpfen, wie es liegt")) }
                        }
                    }
                }
                Text(tr("Lautstärke") + " ${(a.volume * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                Slider(value = a.volume, onValueChange = { v -> h.edit("avol-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(volume = v) else it }) } }, valueRange = 0f..1.5f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Beginn (s)"), a.startSec, "ast-${a.id}", Modifier.weight(1f)) { v -> h.edit("ast-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(startSec = v.coerceAtLeast(0.0)) else it }) } }
                    SecondsField(tr("Länge (s)"), a.length, "al-${a.id}", Modifier.weight(1f)) { v -> h.edit("al-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(outSec = (it.inSec + v.coerceAtLeast(0.5)).let { o -> srcLen?.let { m -> minOf(o, maxOf(m, it.inSec + 0.5)) } ?: o }) else it }) } }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Magnet-Zeitleiste"))
            HintText(tr("Schnitte auf V1 ziehen alle Spuren mit, Löschen schließt die Lücke, Ziehen rastet an Kanten ein (N)."))
        }
        Switch(p.magnet, { v -> h.edit { it.copy(magnet = v) } })
    }
    Text(tr("Spuren"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { h.edit { VideoEdit.addTrack(it, video = true) } }) { Text(tr("+ Videospur")) }
        OutlinedButton(onClick = { h.edit { VideoEdit.addTrack(it, video = false) } }) { Text(tr("+ Tonspur")) }
    }
    HintText(tr("{0} Video- und {1} Tonspuren. In der Zeitleiste links: sperren, stumm, ausblenden.", p.videoTrackCount, p.audioTrackCount))
    ChapterList(h)
    HorizontalDivider()
    Text(tr("Ton"), style = MaterialTheme.typography.titleSmall)
    Text(tr("Rauschminderung"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // The stored value stays German ("aus"); before, tr("aus") stored "off" in English and no chip was selected.
        listOf("aus" to tr("Aus"), "leicht" to tr("Leicht"), "stark" to tr("Stark")).forEach { (k, l) ->
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

/**
 * Seconds with a decimal comma or point. While the field has the focus the typed text stays: a value the project
 * corrects on the way („4“ on the way to „45“ is before the clip's start) used to replace the text after the first
 * digit. Leaving the field shows the value that counts.
 */
@Composable
internal fun SecondsField(label: String, value: Double, key: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (Double) -> Unit) {
    fun show(v: Double) = String.format(Locale.GERMANY, "%.2f", v)
    var text by remember(key) { mutableStateOf(show(value)) }
    var focused by remember(key) { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused && text.replace(',', '.').toDoubleOrNull()?.let { abs(it - value) > 0.005 } != false) text = show(value) }
    OutlinedTextField(
        text, { t -> text = t; t.replace(',', '.').toDoubleOrNull()?.let(onChange) }, label = { Text(label, maxLines = 1) }, singleLine = true,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.typing(), textStyle = MaterialTheme.typography.bodySmall,
    )
}

/** Length of the source file at [path] in seconds (null while unknown, and for a picture). */
@Composable
internal fun sourceLength(path: String): Double? {
    val len by produceState<Double?>(null, path) {
        value = withContext(Dispatchers.IO) { File(path).takeUnless { Media.isStill(it) }?.let { Media.probe(it)?.duration?.takeIf { d -> d > 0.05 } } }
    }
    return len
}

/** How many text fields of the editor have the focus: then the keys belong to the text, not to the editor's shortcuts. */
internal class TypingState {
    var fields by mutableIntStateOf(0)
}

internal val LocalTyping = androidx.compose.runtime.staticCompositionLocalOf<TypingState?> { null }

/** Marks a text field of the editor (see [TypingState]). */
@Composable
internal fun Modifier.typing(): Modifier {
    val t = LocalTyping.current ?: return this
    val on = remember { BooleanArray(1) }
    DisposableEffect(t) { onDispose { if (on[0]) { on[0] = false; t.fields-- } } }
    return onFocusChanged { f -> if (f.isFocused != on[0]) { on[0] = f.isFocused; t.fields += if (on[0]) 1 else -1 } }
}

// ── Export ──────────────────────────────────────────────────────────────────────────────────────────

