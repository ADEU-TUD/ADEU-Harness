package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Repeat
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    // Lora leads to a place of the editor (a tour step, „Wo setze ich Untertitel?“): the latest project opens (AP-70).
    val spot by Spotlight.target.collectAsStateWithLifecycle()
    LaunchedEffect(spot, projects.isEmpty()) {
        if (spot in EDITOR_SPOTS && open == null) projects.maxByOrNull { it.updatedAt }?.let { open = it.id }
    }
    val scope = rememberCoroutineScope()
    var recordDialog by remember { mutableStateOf(false) }
    if (recordDialog) ScreenRecordDialog(c, onDismiss = { recordDialog = false })
    fun projectOf(files: List<File>) = scope.launch {
        if (files.isEmpty()) return@launch
        val p = withContext(Dispatchers.IO) { projectFrom(files.filter { it.extension.lowercase() in VIDEO_MEDIA }) }
        if (p == null) Notices.error(tr("Die Dateien ließen sich nicht lesen – sind es Videos?")) else open = VideoHub.store.save(p).id
    }
    fun newProject() = scope.launch {
        val files = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Videos auswählen"), multiple = true, extensions = VIDEO_MEDIA) }
        projectOf(files)
    }
    // Videos dragged from the Explorer onto the list become a new project (AP-121).
    val drop = remember { fileDrop { files -> projectOf(files) } }
    var renaming by remember { mutableStateOf<VideoProject?>(null) }
    renaming?.let { r ->
        RenameDialog(r.title, onDismiss = { renaming = null }) { t -> VideoHub.store.save(r.copy(title = t)); renaming = null }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Videoeditor")) },
                actions = { ExportQueueChip() },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).dragAndDropTarget(shouldStartDragAndDrop = { true }, target = drop), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!setupSeen) item {
                    ExtensionSetupCard(c, Extensions.VIDEO) {
                        prefs.edit().putBoolean("videoSetupSeen", true).apply()
                        setupSeen = true
                    }
                }
                // Two ways to begin, always at the top (AP-121): before, they were two small words in the top bar.
                item {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cards: @Composable (Modifier) -> Unit = { m ->
                            Spot("video.new", m) { StartCard(Icons.Outlined.Movie, LocalChatLook.current.brand, tr("Video schneiden"), tr("Videos auswählen – oder einfach hierher ziehen"), { newProject() }) }
                        }
                        val record: @Composable (Modifier) -> Unit = { m ->
                            Spot("video.record", m) { StartCard(Icons.Filled.FiberManualRecord, Color(0xFFE53935), tr("Bildschirm aufnehmen"), tr("Screencast mit Webcam, Mikrofon und Systemton"), { recordDialog = true }) }
                        }
                        if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards(Modifier.weight(1f)); record(Modifier.weight(1f)) }
                        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { cards(Modifier.fillMaxWidth()); record(Modifier.fillMaxWidth()) }
                    }
                }
                item { StepsLine(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
                if (projects.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LoraFigure(Modifier.size(width = 120.dp, height = 84.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.SCISSORS, LoraAction.HEADPHONES, LoraAction.LOOK), LoraAction.SCISSORS))
                        Text(tr("Noch keine Videoprojekte"), style = MaterialTheme.typography.titleMedium)
                        HintText(tr("Lehrvideos schneiden, Titel und Bauchbinden im TUD-Design einblenden, Untertitel aus der Transkription, Pausen und Füllwörter automatisch herausschneiden – und als MP4 für OPAL oder YouTube speichern. Die Videos bleiben, wo sie sind."))
                    }
                } else item {
                    Text(tr("Deine Projekte"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, start = 4.dp).semantics { heading() })
                }
                items(projects.sortedByDescending { it.updatedAt }, key = { it.id }) { p ->
                    ProjectRow(
                        p, onOpen = { open = p.id },
                        onRename = { renaming = p },
                        onDuplicate = {
                            val copy = VideoHub.store.save(VideoEdit.duplicate(p, tr("(Kopie)")))
                            Notices.info(tr("Kopie „{0}“ angelegt.", copy.title))
                        },
                        onDelete = {
                            VideoHub.store.delete(p.id)
                            Notices.undo("„${p.title}“ " + tr("gelöscht"), restore = { VideoHub.store.save(p, touch = false) })
                        },
                    )
                }
            }
        }
    }
}

/** A drop target for files from the Explorer (AP-121): [onFiles] gets the dropped files. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun fileDrop(onFiles: (List<File>) -> Unit) = object : androidx.compose.ui.draganddrop.DragAndDropTarget {
    override fun onDrop(event: androidx.compose.ui.draganddrop.DragAndDropEvent): Boolean {
        val t = event.awtTransferable
        if (!t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) return false
        @Suppress("UNCHECKED_CAST")
        val files = runCatching { t.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<File> }.getOrDefault(emptyList()).filter { it.isFile }
        if (files.isEmpty()) return false
        onFiles(files)
        return true
    }
}

@Composable
private fun RenameDialog(title: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Projekt umbenennen")) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name")) }, modifier = Modifier.width(420.dp)) },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onDone(name.trim()) }) { Text(tr("Umbenennen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Places of the map that are in the editor, not in the list of projects. */
private val EDITOR_SPOTS = setOf("video.titles", "video.subtitles", "video.roughcut", "video.pip", "video.export", "video.transcript", "video.layout", "video.tools", "video.guide", "video.cut", "video.help")

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
private fun ProjectRow(p: VideoProject, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, p.id) {
        value = withContext(Dispatchers.IO) { p.clips.firstOrNull()?.let { Media.thumbnail(File(it.path), it.inSec + 0.5) } }
    }
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(128.dp).aspectRatio(16f / 9f).background(Color.Black, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                thumb?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } ?: Icon(Icons.Outlined.Movie, null, tint = Color.White)
                Text(clock(p.duration), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (p.clips.size == 1) tr("1 Clip") else tr("{0} Clips", p.clips.size), "${p.width} × ${p.height}",
                        if (p.overlays.isNotEmpty()) tr("Titel") else null, if (p.subtitles.isNotEmpty()) tr("Untertitel") else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(tr("Geändert {0}", DATE.format(Date(p.updatedAt))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            OutlinedButton(onClick = onOpen) { Text(tr("Öffnen"), maxLines = 1, softWrap = false) }
            Box {
                TipIconButton(Icons.Outlined.MoreVert, tr("Mehr"), { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Umbenennen …")) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(tr("Kopie anlegen")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    HorizontalDivider()
                    DropdownMenuItem(colors = dangerMenuColors(), text = { Text(tr("Löschen")) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // The arrangement (AP-70): widths, heights, folded panels and tracks, remembered for the next start.
    val layout = remember { VideoLayout.of(c.appContext.getSharedPreferences("ui_state", 0)) }
    val view = remember(id) { TimelineState() }
    // Left panel: media pool or the transcript (textbasierter Schnitt, AP-55).
    var leftTab by layout::leftTab
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
    val scope = rememberCoroutineScope()
    // What buttons, menus, the step card and dropped files do (AP-121) – the same functions everywhere.
    val actions = remember(id) { VideoActions(c, h, player, scope) { sel = it } }
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
    /** Splits what was right-clicked at [t] (not at the playhead): a clip, a picture or a sound; nothing → V1. */
    fun splitAt(s: Sel, t: Double) {
        val item: VideoEdit.Item? = when (s) { is Sel.Clip -> VideoEdit.Clip(s.id); is Sel.Layer -> VideoEdit.Layer(s.id); is Sel.Sound -> VideoEdit.Sound(s.id); else -> null }
        val result = if (item != null) VideoEdit.split(h.project, item, t) else VideoEdit.splitV1(h.project, t)
        if (result == null) { Notices.info(tr("Hier lässt sich nicht teilen – zu nah an einem Schnitt.")); return }
        h.edit { result.first }
        sel = when (item) { is VideoEdit.Layer -> Sel.Layer(result.second); is VideoEdit.Sound -> Sel.Sound(result.second); else -> Sel.Clip(result.second) }
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
    // The app's side menu folds in while the editor is open; the menu button (or Strg+B) brings it back (AP-70).
    DisposableEffect(Unit) {
        Navigation.wideMenu.value = layout.menu
        Navigation.wide.value = true
        onDispose { Navigation.wide.value = false }
    }
    val menuShown by Navigation.wideMenu.collectAsStateWithLifecycle()
    LaunchedEffect(menuShown) { layout.menu = menuShown }
    // Lora leads to a place behind the full-screen preview or in a folded panel: it comes back first.
    val spot by Spotlight.target.collectAsStateWithLifecycle()
    LaunchedEffect(spot) {
        if (spot?.startsWith("video.") == true) layout.full = false
        if (spot == "video.transcript") { leftTab = "text"; if (!layout.poolOpen || layout.bigPreview) layout.togglePool(false) }
        // „So entsteht dein Video“ shows while nothing is selected, in the properties (AP-121).
        if (spot == "video.guide") { layout.guide = true; sel = Sel.None; if (!layout.propsOpen || layout.bigPreview) layout.toggleProps(false) }
    }
    fun refocus() { runCatching { focus.requestFocus() } }
    fun addMarker() { val (q, mid) = VideoEdit.addMarker(p, player.position.value); h.edit { q }; sel = Sel.Mark(mid) }
    val timelineTransport: @Composable (Boolean) -> Unit = { dark ->
        TimelineTransport(p, player, pos, playing, speed, loop, onLoop = { loop = !loop }, loopRange = ::loopRange, dark = dark)
    }
    val sourceTransport: @Composable (Boolean) -> Unit = { dark ->
        val item = sourceItem!!
        SourceTransport(sourceProject!!, item, source, srcPos, srcPlaying, dark, onUse = { how -> use(item, how) })
    }
    // Files from the Explorer (AP-121): onto the media pool they go into the pool, anywhere else also into the video.
    val dropToVideo = remember(id) { fileDrop { actions.dropFiles(it, toTimeline = true) } }
    val dropToPool = remember(id) { fileDrop { actions.dropFiles(it, toTimeline = false) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalTyping provides typing, LocalVideoLayout provides layout) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropToVideo)
            .focusRequester(focus).focusable().onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // In a text field S, Leertaste, Entf and the arrows are typing: before, they also split the clip, started
            // the playback or deleted the selected title while its text was being edited.
            if (typing.fields > 0 && !ev.isCmd) return@onPreviewKeyEvent false
            // The source monitor takes the transport keys while it is shown (Q switches).
            val (pl, pr) = if (showSource) source to sourceProject!! else player to p
            val at = pl.position.value
            when {
                ev.utf16CodePoint == '?'.code || ev.key == Key.F1 -> { keys = true; true }
                // Vollbild (Strg+F, Esc ends it) and „Vorschau groß“ (Alt+F) as in Resolve (AP-70).
                ev.key == Key.Escape && layout.full -> { layout.full = false; true }
                ev.isCmd && ev.key == Key.F -> { layout.full = !layout.full; true }
                ev.isAltPressed && ev.key == Key.F -> { layout.bigPreview = !layout.bigPreview; true }
                ev.isCmd && ev.isAltPressed && ev.key == Key.L -> { toggleLink(); true }
                ev.isCmd && ev.key == Key.Z -> { h.undo(); true }
                ev.isCmd && ev.key == Key.Y -> { h.redo(); true }
                // Strg+B stays the app's Seitenleiste here (key list, AP-70); Teilen is S (AppShortcuts.late, AP-125).
                ev.isAltPressed && ev.key == Key.X -> {
                    if (showSource) h.edit { pp -> pp.copy(media = pp.media.map { if (it.id == sourceId) it.copy(inSec = null, outSec = null) else it }) }
                    else h.edit { it.copy(markIn = null, markOut = null) }
                    true
                }
                ev.isCmd || ev.isAltPressed -> false
                // Umschalt+Z: the whole timeline in view again (AP-121, as in Resolve).
                ev.isShiftPressed && ev.key == Key.Z -> { view.zoom = 1f; view.scroll = 0f; true }
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
                    when {
                        leftTab == "text" && textCut.hasSelection -> textCut.deleteSelection(h)
                        // Nothing selected, a piece marked: Entf cuts it out (AP-121).
                        sel == Sel.None && !showSource && actions.canCutRange -> actions.cutRange()
                        else -> deleteSelected(closeGap = ev.isShiftPressed)
                    }
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
        if (layout.full) {
            // Vollbild: only the picture and the transport; Esc or the button ends it.
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Column(Modifier.fillMaxSize()) {
                    if (showSource) SourceViewer(sourceProject!!, sourceItem!!, source, srcPos, Modifier.weight(1f).fillMaxWidth())
                    else Preview(p, player, pos, Modifier.weight(1f).fillMaxWidth(), onToggle = { player.toggle(h.project, loopRange()) })
                    if (showSource) sourceTransport(true) else timelineTransport(true)
                }
                TipIconButton(Icons.Outlined.FullscreenExit, tr("Vollbild beenden") + " (Esc)", { layout.full = false; refocus() }, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            return@BoxWithConstraints
        }
        // Which panels fit: a preview of at least PREVIEW_MIN_WIDTH stays; too narrow, a panel folds in by itself.
        val left = layout.left
        val fit = fitPanels(
            maxWidth,
            if (layout.poolOpen && !layout.bigPreview) left.wanted to left.min else null,
            if (layout.propsOpen && !layout.bigPreview) layout.props.wanted to layout.props.min else null,
            preferPool = layout.preferPool,
        )
        val bodyHeight = maxHeight - 49.dp
        val timelineHeight = fitTimeline(bodyHeight, layout.timeline.wanted, layout.timeline.min)
        val width = maxWidth
        SideEffect {
            // Dragging stops where the preview would get narrower than its minimum.
            left.limit = width - PREVIEW_MIN_WIDTH - 20.dp - (fit.props ?: 0.dp)
            layout.props.limit = width - PREVIEW_MIN_WIDTH - 20.dp - (fit.pool ?: 0.dp)
            layout.timeline.limit = bodyHeight - UPPER_MIN_HEIGHT - 10.dp
        }
        Column(Modifier.fillMaxSize()) {
            EditorHeader(
                p, h, layout, menuShown, poolShown = fit.pool != null, propsShown = fit.props != null, saved = saved == h.version, wide = width >= 1150.dp,
                onClose = onClose, onKeys = { keys = true }, onExport = { player.stop(); actions.export = true }, onFocus = ::refocus,
                onGuide = { layout.guide = true; sel = Sel.None; if (fit.props == null) layout.toggleProps(false); refocus() },
            )
            HorizontalDivider()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                fit.pool?.let { w ->
                    Column(Modifier.width(w).fillMaxHeight().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropToPool)) {
                        PanelHead("pool", tr("Medienpool einklappen"), foldLeft = true, onFold = { layout.togglePool(true); refocus() }) {
                            FilterChip(selected = leftTab == "media", onClick = { leftTab = "media"; refocus() }, label = { Text(tr("Medien"), maxLines = 1) })
                            Spacer(Modifier.width(4.dp))
                            Spot("video.transcript") { FilterChip(selected = leftTab == "text", onClick = { leftTab = "text"; refocus() }, label = { Text(tr("Transkript"), maxLines = 1) }) }
                        }
                        if (leftTab == "text") TranscriptCutPanel(c, h, player, pos, textCut, onAssign = { assignTranscript = true }, modifier = Modifier.weight(1f).fillMaxWidth())
                        else MediaPool(h, drag, sourceId, onOpen = { item -> sourceId = item.id; sourceView = true; player.stop(); refocus() }, onImport = { importToPool() }, onUse = { item, how -> use(item, how) }, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                    PanelResizer(left, panelLeft = true, label = tr("Breite des Medienpools ändern"))
                }
                // The viewer: source or timeline, the picture, the transport.
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    EditorBar("viewer", height = 40.dp) {
                        Tip(tr("Q wechselt zwischen Quelle und Zeitleiste")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = showSource, enabled = sourceProject != null, onClick = { sourceView = true; player.stop(); refocus() }, label = { Text(tr("Quelle"), maxLines = 1) })
                                Spacer(Modifier.width(4.dp))
                                FilterChip(selected = !showSource, onClick = { sourceView = false; source.stop(); refocus() }, label = { Text(tr("Zeitleiste"), maxLines = 1) })
                            }
                        }
                        Text(
                            if (showSource) sourceItem?.name.orEmpty() else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        TipIconButton(
                            if (layout.bigPreview) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                            tr(if (layout.bigPreview) "Vorschau wieder normal" else "Vorschau groß") + " (Alt+F)",
                            { layout.bigPreview = !layout.bigPreview; refocus() }, selected = layout.bigPreview, size = 34.dp,
                        )
                        TipIconButton(Icons.Outlined.Fullscreen, tr("Vollbild") + " (Strg+F)", { layout.full = true; refocus() }, size = 34.dp)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                        if (showSource) SourceViewer(sourceProject!!, sourceItem!!, source, srcPos, Modifier.fillMaxSize())
                        else Preview(p, player, pos, Modifier.fillMaxSize(), onToggle = { player.toggle(h.project, loopRange()); refocus() }, onAdd = { actions.addClips() })
                    }
                    if (showSource) sourceTransport(false) else timelineTransport(false)
                }
                fit.props?.let { w ->
                    PanelResizer(layout.props, panelLeft = false, label = tr("Breite der Eigenschaften ändern"))
                    Column(Modifier.width(w).fillMaxHeight()) {
                        PanelHead("props", tr("Eigenschaften einklappen"), foldLeft = false, onFold = { layout.toggleProps(true); refocus() }) {
                            Text(tr("Eigenschaften"), style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Properties(c, h, sel, pos, actions, onSelect = { sel = it }, onSplit = ::split, onDelete = { deleteSelected() }, onSeek = { player.seek(h.project, it) })
                        }
                    }
                }
            }
            // The line above the timeline: drag for a higher or lower timeline, double-click resets.
            PanelResizer(layout.timeline, panelLeft = false, label = tr("Höhe der Zeitleiste ändern"), vertical = true)
            Column(Modifier.height(timelineHeight).fillMaxWidth()) {
                TimelineBar(
                    h, actions, tool, layout, view, hint = editHint(p, sel, tool),
                    onTool = { tool = it; refocus() }, onSplit = { split(); refocus() }, onMarker = { addMarker(); refocus() }, onFocus = ::refocus,
                )
                HorizontalDivider()
                VideoTimelineView(
                    h, player, pos, sel, tool, drag,
                    onSelect = { sel = it; refocus() },
                    onDropItem = { item, lane, t ->
                        when {
                            lane == "V1" -> use(item, PoolUse.INSERT, nearestCut(p, t))
                            lane.startsWith("V") -> use(item, PoolUse.PIP, t, lane)
                            else -> use(item, PoolUse.SOUND, t, lane)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    view = view, scale = layout.trackScale, folded = layout.foldedTracks, onFold = { layout.toggleTrack(it) },
                    // Right-click (AP-121): what can be done with the thing under the mouse, in plain words.
                    menu = { s, t, dismiss ->
                        TimelineMenu(h, actions, s, t, { dismiss(); refocus() },
                            onSplitAt = { item, at -> splitAt(item, at) },
                            onDelete = { deleteSelected() },
                            onProps = { if (fit.props == null) layout.toggleProps(false) },
                            onSeek = { player.seek(h.project, it) },
                            onMarker = { at -> val (q, mid) = VideoEdit.addMarker(h.project, at); h.edit { q }; sel = Sel.Mark(mid) },
                        )
                    },
                )
            }
        }
    }
    }
    LaunchedEffect(id) { runCatching { focus.requestFocus() } }
    if (assignTranscript) TranscriptPicker(c, p, onDismiss = { assignTranscript = false }) { map ->
        assignTranscript = false
        h.edit { it.copy(transcripts = it.transcripts + map.mapValues { e -> e.value.id }) }
    }
    if (actions.export) ExportDialog(c, p, onDismiss = { actions.export = false; refocus() })
    VideoActionDialogs(actions)
    if (keys) VideoShortcutsDialog { keys = false; runCatching { focus.requestFocus() } }
}

/**
 * The header of the editor (AP-70): side menu, back, title and format at the left; the panels and the arrangement;
 * at the right the keys, undo and redo, the export queue and Exportieren.
 */
@Composable
private fun EditorHeader(
    p: VideoProject, h: History, layout: VideoLayout, menuShown: Boolean, poolShown: Boolean, propsShown: Boolean, saved: Boolean, wide: Boolean,
    onClose: () -> Unit, onKeys: () -> Unit, onExport: () -> Unit, onFocus: () -> Unit, onGuide: () -> Unit,
) {
    EditorBar("header", height = 48.dp) {
        TipIconButton(Icons.Outlined.Menu, tr(if (menuShown) "Seitenleiste ausblenden" else "Seitenleiste zeigen") + " (Strg+B)", { Navigation.openRoute.value = "drawer" }, selected = menuShown)
        TipIconButton(Icons.AutoMirrored.Outlined.ArrowBack, tr("Zurück zu den Projekten"), onClose)
        var title by remember(p.id) { mutableStateOf(p.title) }
        TitleField(title, { t -> title = t; if (t.isNotBlank()) h.edit("title") { it.copy(title = t.trim()) } }, Modifier.widthIn(min = 120.dp, max = 300.dp).weight(1f, fill = false))
        Text(
            tr("{0} × {1} · {2} fps · {3}", p.width, p.height, p.fps, clock(p.duration)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        SavedBadge(saved, labelled = wide)
        ToolGroup {
            TipIconButton(Icons.Outlined.VideoLibrary, tr(if (poolShown) "Medienpool einklappen" else "Medienpool zeigen"), { layout.togglePool(poolShown); onFocus() }, selected = poolShown)
            TipIconButton(Icons.Outlined.Tune, tr(if (propsShown) "Eigenschaften einklappen" else "Eigenschaften zeigen"), { layout.toggleProps(propsShown); onFocus() }, selected = propsShown)
            var menu by remember { mutableStateOf(false) }
            Spot("video.layout") {
                Box {
                    TipIconButton(Icons.Outlined.SpaceDashboard, tr("Anordnung"), { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false; onFocus() }) {
                        DropdownMenuItem(text = { Text(tr("Vorschau groß") + " (Alt+F)") }, leadingIcon = { Icon(Icons.Outlined.OpenInFull, null) }, onClick = { menu = false; layout.bigPreview = !layout.bigPreview; onFocus() }, trailingIcon = { if (layout.bigPreview) Icon(Icons.Outlined.Check, null) })
                        DropdownMenuItem(text = { Text(tr("Vollbild") + " (Strg+F)") }, leadingIcon = { Icon(Icons.Outlined.Fullscreen, null) }, onClick = { menu = false; layout.full = true; onFocus() })
                        DropdownMenuItem(text = { Text(tr("Spuren höher")) }, leadingIcon = { Icon(Icons.Outlined.UnfoldMore, null) }, enabled = layout.trackScale < VideoLayout.MAX_TRACKS, onClick = { layout.tracksTaller(true) })
                        DropdownMenuItem(text = { Text(tr("Spuren niedriger")) }, leadingIcon = { Icon(Icons.Outlined.UnfoldLess, null) }, enabled = layout.trackScale > VideoLayout.MIN_TRACKS, onClick = { layout.tracksTaller(false) })
                        if (layout.foldedTracks.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Alle Spuren aufklappen")) }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) }, onClick = { menu = false; layout.folded = "" })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(tr("Anordnung zurücksetzen")) }, leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) }, onClick = {
                            menu = false
                            layout.reset()
                            Navigation.wideMenu.value = false
                            Notices.info(tr("Anordnung zurückgesetzt."))
                            onFocus()
                        })
                    }
                }
            }
        }
        BarGap()
        // Help (AP-121): the step card, the tour, the keys and Lora in one place.
        Spot("video.help") { HelpButton(layout.guide, onGuide = onGuide, onKeys = onKeys, onDismiss = onFocus) }
        ToolGroup {
            TipIconButton(Icons.AutoMirrored.Outlined.Undo, tr("Rückgängig") + " (Strg+Z)", h::undo, enabled = h.canUndo)
            TipIconButton(Icons.AutoMirrored.Outlined.Redo, tr("Wiederholen") + " (Strg+Y)", h::redo, enabled = h.canRedo)
        }
        BarGap()
        ExportQueueChip()
        Spot("video.export") {
            Button(onClick = onExport, enabled = p.clips.isNotEmpty(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Exportieren"), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Transport of the timeline: start, J, play, L, end, time, loop and In/Out – symbols with tooltips, one line. */
@Composable
private fun TimelineTransport(p: VideoProject, player: VideoPlayer, pos: Double, playing: Boolean, speed: Double, loop: Boolean, onLoop: () -> Unit, loopRange: () -> Pair<Double, Double>?, dark: Boolean) {
    val tint = if (dark) Color.White else Color.Unspecified
    EditorBar("transport", height = 44.dp) {
        TipIconButton(Icons.Outlined.SkipPrevious, tr("Zum Anfang") + " (Pos1)", { player.seek(p, 0.0) }, tint = tint)
        TipIconButton(Icons.Outlined.FastRewind, tr("Rückwärts") + " (J)", { player.backward(p) }, tint = tint)
        TipIconButton(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, tr(if (playing) "Pause" else "Abspielen") + tr(" (Leertaste)"), { player.toggle(p, loopRange()) }, tint = tint, size = 40.dp)
        TipIconButton(Icons.Outlined.FastForward, tr("Vorwärts") + " (L)", { player.forward(p, loopRange()) }, tint = tint)
        TipIconButton(Icons.Outlined.SkipNext, tr("Zum Ende") + " (Ende)", { player.seek(p, p.duration) }, tint = tint)
        Spacer(Modifier.width(6.dp))
        Text("${clock(pos, true)} / ${clock(p.duration, true)}", style = MaterialTheme.typography.labelLarge, color = if (dark) Color.White else Color.Unspecified, maxLines = 1, softWrap = false)
        if (playing && speed != 1.0 && speed != 0.0) Text("  " + (if (speed < 0) "◀ " else "▶ ") + "${abs(speed).toInt()}×", style = MaterialTheme.typography.labelLarge, color = LocalChatLook.current.brand, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(6.dp))
        TipIconButton(Icons.Outlined.Repeat, tr("Schleife zwischen In und Out"), onLoop, selected = loop, tint = tint)
        Text(
            if (p.markIn != null || p.markOut != null) tr("In {0} · Out {1}", clock(p.markIn ?: 0.0, true), clock(p.markOut ?: p.duration, true)) else "",
            style = MaterialTheme.typography.bodySmall, color = if (dark) Color(0xCCFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
    }
}

/** Transport of the source monitor: play, time, In/Out, and the three ways into the timeline (F9, F10, F12). */
@Composable
private fun SourceTransport(sp: VideoProject, item: io.github.thepytales.llmhub.video.PoolItem, source: VideoPlayer, srcPos: Double, srcPlaying: Boolean, dark: Boolean, onUse: (PoolUse) -> Unit) {
    val tint = if (dark) Color.White else Color.Unspecified
    EditorBar("transport", height = 44.dp) {
        TipIconButton(if (srcPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, tr(if (srcPlaying) "Pause" else "Abspielen") + tr(" (Leertaste)"), { source.toggle(sp) }, tint = tint, size = 40.dp)
        Text("${clock(srcPos, true)} / ${clock(sp.duration, true)}", style = MaterialTheme.typography.labelLarge, color = if (dark) Color.White else Color.Unspecified, maxLines = 1, softWrap = false)
        Text(
            tr("In {0} · Out {1}", clock(item.inSec ?: 0.0, true), clock(item.outSec ?: sp.duration, true)),
            style = MaterialTheme.typography.bodySmall, color = if (dark) Color(0xCCFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        ToolGroup {
            TipIconButton(Icons.AutoMirrored.Outlined.Input, tr("Einfügen am Abspielkopf (F9)"), { onUse(PoolUse.INSERT) }, tint = tint)
            TipIconButton(Icons.Outlined.VerticalAlignBottom, tr("Überschreiben am Abspielkopf (F10)"), { onUse(PoolUse.OVERWRITE) }, tint = tint)
            TipIconButton(Icons.Outlined.PictureInPicture, tr("Als Bild-in-Bild (F12)"), { onUse(PoolUse.PIP) }, tint = tint)
        }
    }
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

/**
 * The picture at the playhead, with the titles and subtitles that are on at that time. A click plays or pauses
 * ([onToggle], AP-121) – a round play symbol shows on the picture while it stands; without clips a button adds videos.
 */
@Composable
private fun Preview(p: VideoProject, player: VideoPlayer, pos: Double, modifier: Modifier, onToggle: (() -> Unit)? = null, onAdd: (() -> Unit)? = null) {
    val frame by player.frame.collectAsStateWithLifecycle()
    val playing by player.playing.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier.background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
        val ratio = p.width.toFloat() / p.height
        val w = min(maxWidth.value, maxHeight.value * ratio)
        val click = if (onToggle != null && p.clips.isNotEmpty()) Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            onClickLabel = tr(if (playing) "Pause" else "Abspielen"), onClick = onToggle,
        ) else Modifier
        Box(Modifier.size(w.dp, (w / ratio).dp).background(Color.Black).then(click)) {
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
            // The next subtitle is drawn ahead, so it is there the moment it starts (AP-70).
            p.subtitles.filter { it.startSec > pos }.minByOrNull { it.startSec }?.let { next ->
                val key = subtitleKey(next.text, p, px, p.subtitleRaised(next))
                LaunchedEffect(key) { drawTitle(key) { VideoTitles.render(VideoTitles.subtitleDesign(next.text, p.width, p.height, p.subtitleRaised(next)), px) } }
            }
            // Standing: a quiet play symbol in the middle, so it is clear a click plays (AP-121).
            if (onToggle != null && !playing && p.clips.isNotEmpty() && w >= 200f) {
                Box(Modifier.align(Alignment.Center).size(56.dp).background(Color(0x66000000), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(34.dp))
                }
            }
            if (p.clips.isEmpty()) Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Movie, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                Text(tr("Noch keine Clips"), color = Color.White, style = MaterialTheme.typography.titleSmall)
                if (onAdd != null) Button(onClick = onAdd) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Videos hinzufügen"), maxLines = 1, softWrap = false) }
                Text(tr("oder Dateien aus dem Explorer hierher ziehen"), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val titleCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 24
}

/**
 * A title or subtitle picture for [key], drawn in the background. While a new one is drawn (another size of the
 * preview, a title being typed) the last one of the same [slot] stays – before, it vanished for a moment (AP-70).
 */
@Composable
private fun rememberTitleImage(slot: String, key: String, draw: () -> ImageBitmap): ImageBitmap? {
    val img = remember(slot) { mutableStateOf(synchronized(titleCache) { titleCache[key] }) }
    LaunchedEffect(key) { drawTitle(key, draw)?.let { img.value = it } }
    return img.value
}

/** The picture for [key] from the cache, or drawn and put there. */
private suspend fun drawTitle(key: String, draw: () -> ImageBitmap): ImageBitmap? =
    synchronized(titleCache) { titleCache[key] }
        ?: withContext(Dispatchers.Default) { runCatching(draw).getOrNull() }?.also { synchronized(titleCache) { titleCache[key] = it } }

@Composable
private fun rememberTitle(o: TextOverlay, p: VideoProject, px: Int): ImageBitmap? =
    rememberTitleImage(o.id, "${o.style}|${o.text}|${o.subtext}|${p.width}x${p.height}|$px") { VideoTitles.render(VideoTitles.design(o, p.width, p.height), px) }

private fun subtitleKey(text: String, p: VideoProject, px: Int, raised: Boolean) = "sub|$text|${p.width}x${p.height}|$px|$raised"

/** A subtitle: its last picture stays only for the same text (a new cue never shows the old words). */
@Composable
private fun rememberSubtitle(text: String, p: VideoProject, px: Int, raised: Boolean): ImageBitmap? =
    rememberTitleImage("sub|$text", subtitleKey(text, p, px, raised)) { VideoTitles.render(VideoTitles.subtitleDesign(text, p.width, p.height, raised), px) }

// ── Toolbar above the timeline ─────────────────────────────────────────────────────────────────────

/**
 * The toolbar above the timeline (AP-70, AP-121): the tools as a group of symbols (A, B, T, magnet); cutting – split,
 * start and end of a piece, cut it out, marker; the things to add (+, titles, subtitles, rough cut); a one-line hint for
 * what is selected; track height, the whole timeline and zoom. Where there is room the main buttons carry their word.
 */
@Composable
private fun TimelineBar(
    h: History, a: VideoActions, tool: CutTool, layout: VideoLayout, view: TimelineState, hint: String,
    onTool: (CutTool) -> Unit, onSplit: () -> Unit, onMarker: () -> Unit, onFocus: () -> Unit,
) {
    var titles by remember { mutableStateOf(false) }
    var subs by remember { mutableStateOf(false) }
    var cut by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    val p = h.project
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Words beside the symbols from about 1250 dp (Full HD at 125 %, 1366 × 768 at 100 %); smaller, the symbols as before.
    val words = maxWidth >= 1250.dp
    EditorBar("toolbar", height = 42.dp) {
        Spot("video.tools") {
            ToolGroup {
                CutTool.entries.forEach { t ->
                    TipIconButton(t.icon, tr(t.tip), { onTool(t) }, selected = tool == t, size = 34.dp)
                }
                TipIconButton(MagnetIcon, tr("Magnet (N): Lücken schließen sich, Spuren ziehen mit"), { h.edit { it.copy(magnet = !it.magnet) } }, selected = p.magnet, size = 34.dp)
            }
        }
        BarGap()
        Spot("video.cut") {
            ToolGroup {
                BarButton(Icons.AutoMirrored.Outlined.CallSplit, tr("Teilen"), tr("Am Abspielkopf teilen") + " (S)", onSplit, words)
                TipIconButton(MarkInIcon, tr("Anfang des Stücks markieren") + " (I)", { a.mark(start = true); onFocus() }, selected = p.markIn != null, size = 34.dp)
                TipIconButton(MarkOutIcon, tr("Ende des Stücks markieren") + " (O)", { a.mark(start = false); onFocus() }, selected = p.markOut != null, size = 34.dp)
                BarButton(
                    CutRangeIcon, tr("Herausschneiden"),
                    if (a.canCutRange) tr("Markiertes Stück herausschneiden – der Rest rückt nach (Entf)") else tr("Erst Anfang (I) und Ende (O) des Stücks markieren"),
                    { a.cutRange(); onFocus() }, words, enabled = a.canCutRange,
                )
                TipIconButton(Icons.Outlined.BookmarkAdd, tr("Marker setzen") + " (M)", onMarker, size = 34.dp)
            }
        }
        BarGap()
        ToolGroup {
            Spot("video.pip") {
                Box {
                    BarButton(Icons.Outlined.Add, tr("Hinzufügen"), tr("Hinzufügen: Clip, Bild-in-Bild, Musik, Spuren"), { add = true }, words)
                    DropdownMenu(expanded = add, onDismissRequest = { add = false; onFocus() }) {
                        DropdownMenuItem(text = { Text(tr("Clip hinzufügen …")) }, leadingIcon = { Icon(Icons.Outlined.Movie, null) }, onClick = { add = false; a.addClips() })
                        DropdownMenuItem(text = { Text(tr("Bild-in-Bild …")) }, leadingIcon = { Icon(Icons.Outlined.PictureInPicture, null) }, onClick = { add = false; a.addPicture() })
                        DropdownMenuItem(text = { Text(tr("Musik / Ton …")) }, leadingIcon = { Icon(Icons.Outlined.MusicNote, null) }, onClick = { add = false; a.addAudio() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(tr("+ Videospur")) }, onClick = { add = false; h.edit { VideoEdit.addTrack(it, video = true) } })
                        DropdownMenuItem(text = { Text(tr("+ Tonspur")) }, onClick = { add = false; h.edit { VideoEdit.addTrack(it, video = false) } })
                    }
                }
            }
            Box {
                Spot("video.titles") { BarButton(Icons.Outlined.Title, tr("Titel"), tr("Titel & Bauchbinde"), { titles = true }, words) }
                DropdownMenu(expanded = titles, onDismissRequest = { titles = false; onFocus() }) {
                    TextOverlay.STYLES.forEach { (style, label) ->
                        DropdownMenuItem(text = { Text(tr(label)) }, onClick = { titles = false; a.addTitle(style) })
                    }
                }
            }
            Box {
                Spot("video.subtitles") { BarButton(Icons.Outlined.Subtitles, tr("Untertitel"), tr("Untertitel"), { subs = true }, words) }
                DropdownMenu(expanded = subs, onDismissRequest = { subs = false; onFocus() }) {
                    DropdownMenuItem(text = { Text(tr("Aus der Transkription …")) }, onClick = { subs = false; a.pickTranscript = "subs" })
                    DropdownMenuItem(text = { Text(tr("SRT/VTT importieren …")) }, onClick = { subs = false; a.importSubs() })
                    if (p.subtitles.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Alle Untertitel entfernen")) }, onClick = { subs = false; h.edit { it.copy(subtitles = emptyList()) } })
                }
            }
            Box {
                Spot("video.roughcut") { BarButton(Icons.Outlined.AutoAwesome, tr("Rohschnitt"), tr("Rohschnitt: Pausen und Füllwörter, mit Lora kürzen"), { cut = true }, words) }
                DropdownMenu(expanded = cut, onDismissRequest = { cut = false; onFocus() }) {
                    DropdownMenuItem(text = { Text(tr("Pausen und Füllwörter herausschneiden …")) }, onClick = { cut = false; a.pickTranscript = "cut" })
                    DropdownMenuItem(text = { Text(tr("Mit Lora kürzen …")) }, onClick = { cut = false; a.loraCut = true })
                }
            }
        }
        BarGap()
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TipIconButton(Icons.Outlined.UnfoldLess, tr("Spuren niedriger"), { layout.tracksTaller(false) }, enabled = layout.trackScale > VideoLayout.MIN_TRACKS, size = 32.dp)
        TipIconButton(Icons.Outlined.UnfoldMore, tr("Spuren höher"), { layout.tracksTaller(true) }, enabled = layout.trackScale < VideoLayout.MAX_TRACKS, size = 32.dp)
        TipIconButton(Icons.Outlined.ZoomOutMap, tr("Ganze Zeitleiste zeigen") + " (" + tr("Umschalt+Z") + ")", { view.zoom = 1f; view.scroll = 0f; onFocus() }, enabled = view.zoom > 1.01f || view.scroll > 0f, size = 32.dp)
        Tip(tr("Zoom (Strg+Mausrad)")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ZoomIn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = view.zoom, onValueChange = { view.zoom = it }, valueRange = 1f..24f, modifier = Modifier.width(if (words) 110.dp else 84.dp).height(32.dp).semantics { contentDescription = tr("Zoom") })
            }
        }
    }
    }
}

/**
 * The hint line of the toolbar (AP-121): what can be done with what is selected, in a few words – before, only what
 * the tool does.
 */
internal fun editHint(p: VideoProject, sel: Sel, tool: CutTool): String = when {
    tool == CutTool.BLADE -> tr(tool.hint)
    tool == CutTool.TRIM -> tr(tool.hint)
    sel is Sel.Clip -> tr("Clip gewählt: Ränder ziehen kürzt · S teilt · Entf löscht · Rechtsklick für mehr")
    sel is Sel.Title -> tr("Titel gewählt: Text rechts ändern · Ränder ziehen ändert die Dauer · Entf löscht")
    sel is Sel.Layer -> tr("Bild-in-Bild gewählt: Ecke und Größe rechts · Entf löscht")
    sel is Sel.Sound -> tr("Ton gewählt: Lautstärke rechts · S teilt · Entf löscht")
    sel is Sel.Mark -> tr("Marker gewählt: Notiz rechts · ziehen verschiebt · Entf löscht")
    p.markIn != null && p.markOut != null -> tr("Stück markiert: Entf schneidet es heraus · Alt+X hebt die Markierung auf")
    p.clips.isEmpty() -> tr("Videos aus dem Medienpool oder dem Explorer in die Zeitleiste ziehen")
    else -> tr("Klick setzt den Abspielkopf · Rechtsklick zeigt, was geht")
}

/** Which transcript belongs to which source file; or transcribe a video now. */
@Composable
internal fun TranscriptPicker(c: AppContainer, p: VideoProject, onDismiss: () -> Unit, onPick: (Map<String, io.github.thepytales.llmhub.transcribe.TranscriptSummary>) -> Unit) {
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
internal fun LoraCutDialog(c: AppContainer, h: History, onDismiss: () -> Unit) {
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
private fun Properties(c: AppContainer, h: History, sel: Sel, pos: Double, a: VideoActions, onSelect: (Sel) -> Unit, onSplit: () -> Unit, onDelete: () -> Unit, onSeek: (Double) -> Unit) {
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
                // Only what fits this clip (AP-70): sound only with sound in the file, the transition from the second clip.
                val hasSound = sourceHasSound(clip.path)
                PropTitle(tr("Clip") + " ${i + 1}: ${clip.name}") {
                    TipIconButton(Icons.AutoMirrored.Outlined.CallSplit, tr("Am Abspielkopf teilen") + " (S)", onSplit, size = 32.dp)
                    TipIconButton(Icons.Outlined.Delete, tr("Löschen") + " (Entf)", onDelete, size = 32.dp)
                }
                // Shorten without dragging (AP-121): everything before or after the playhead goes.
                val start = p.starts()[i]
                val inside = pos > start + 0.05 && pos < start + clip.length - 0.05
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tip(tr("Alles vor dem Abspielkopf aus diesem Clip entfernen")) {
                        OutlinedButton(onClick = { a.trimTo(start = true) }, enabled = inside, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                            Icon(MarkInIcon, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Anfang kürzen"), maxLines = 1, softWrap = false)
                        }
                    }
                    Tip(tr("Alles ab dem Abspielkopf aus diesem Clip entfernen")) {
                        OutlinedButton(onClick = { a.trimTo(start = false) }, enabled = inside, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                            Icon(MarkOutIcon, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Ende kürzen"), maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (!inside) HintText(tr("Zum Kürzen den Abspielkopf in diesen Clip setzen."))
                PropSection("clip.time", tr("Zeit"), summary = clock(clip.length, true)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondsField(tr("Anfang (s)"), clip.inSec, "in-${clip.id}", Modifier.weight(1f)) { v -> h.edit("in-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(inSec = v.coerceIn(0.0, it.outSec - 0.2)) else it }) } }
                        SecondsField(tr("Ende (s)"), clip.outSec, "out-${clip.id}", Modifier.weight(1f)) { v -> h.edit("out-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(outSec = maxOf(srcLen?.let { m -> minOf(v, m) } ?: v, it.inSec + 0.2)) else it }) } }
                    }
                    Text(tr("Länge") + " ${clock(clip.length, true)}", style = MaterialTheme.typography.bodySmall)
                }
                if (hasSound != false) PropSection("clip.sound", tr("Ton"), summary = if (clip.muted) tr("aus") else "${(clip.volume * 100).toInt()} %") {
                    Text(tr("Lautstärke") + " ${(clip.volume * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                    Slider(value = clip.volume, onValueChange = { v -> h.edit("vol-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(volume = v) else it }) } }, valueRange = 0f..2f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Ton aus"), modifier = Modifier.weight(1f))
                        Switch(clip.muted, { m -> h.edit { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(muted = m) else it }) } })
                    }
                    // Picture and sound (AP-53): the clip's sound onto an audio track, linked.
                    val linked = p.audio.filter { it.linkedTo == clip.id }
                    if (linked.isNotEmpty()) {
                        HintText(tr("Verknüpfter Ton auf {0} – er bewegt und kürzt sich mit dem Clip.", linked.joinToString(", ") { "A${it.track}" }))
                        TextButton(onClick = { h.edit { pp -> pp.copy(audio = pp.audio.map { if (it.linkedTo == clip.id) it.copy(linkedTo = null) else it }) } }) { Text(tr("Verknüpfung lösen"), maxLines = 1) }
                    }
                    if (!clip.muted) OutlinedButton(onClick = { VideoEdit.detachSound(p, clip.id)?.let { (q, aid) -> h.edit { q }; onSelect(Sel.Sound(aid)) } }) { Text(tr("Ton auf eine Tonspur legen"), maxLines = 1) }
                }
                if (i > 0) PropSection("clip.transition", tr("Übergang vom vorigen Clip"), summary = when (clip.transition) { "fade" -> tr("Überblenden"); "black" -> tr("Über Schwarz"); else -> tr("Schnitt") }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("none" to tr("Schnitt"), "fade" to tr("Überblenden"), "black" to tr("Über Schwarz")).forEach { (k, l) ->
                            FilterChip(selected = clip.transition == k, onClick = { h.edit { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(transition = k) else it }) } }, label = { Text(l) })
                        }
                    }
                    if (clip.transition != "none") SecondsField(tr("Dauer des Übergangs (s)"), clip.transitionSec, "tr-${clip.id}") { v ->
                        h.edit("tr-${clip.id}") { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(transitionSec = v.coerceIn(0.1, 3.0)) else it }) }
                    }
                }
                PropSection("clip.frame", tr("Position und Größe"), defaultOpen = !clip.frame.isFull, summary = if (!clip.frame.isFull) tr("platziert") else null) {
                    FrameEditor(clip.frame, "cf-${clip.id}", heading = false) { k, f -> h.edit(k) { pp -> pp.copy(clips = pp.clips.map { if (it.id == clip.id) it.copy(frame = f) else it }) } }
                }
                ClipEffectsEditor(h, clip, pos, onSelect)
                p.transcripts[clip.path]?.let { HintText(tr("Transkript zugeordnet – Untertitel und Rohschnitt kommen daraus.")) }
            }
            is Sel.Title -> {
                val o = p.overlays.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                PropTitle(tr(TextOverlay.label(o.style))) {
                    TipIconButton(Icons.Outlined.Delete, tr("Löschen") + " (Entf)", onDelete, size = 32.dp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextOverlay.STYLES.forEach { (k, l) -> FilterChip(selected = o.style == k, onClick = { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(style = k) else it }) } }, label = { Text(tr(l)) }) }
                }
                OutlinedTextField(o.text, { t -> h.edit("ot-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(text = t) else it }) } }, label = { Text(tr(if (o.style == "bauchbinde") "Name" else "Text")) }, modifier = Modifier.fillMaxWidth().typing(), maxLines = 3)
                OutlinedTextField(o.subtext, { t -> h.edit("os-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(subtext = t) else it }) } }, label = { Text(tr(if (o.style == "bauchbinde") "Funktion / Einrichtung" else "Zweite Zeile")) }, modifier = Modifier.fillMaxWidth().typing(), maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondsField(tr("Beginn (s)"), o.startSec, "ost-${o.id}", Modifier.weight(1f)) { v -> h.edit("ost-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = v.coerceAtLeast(0.0)) else it }) } }
                    SecondsField(tr("Dauer (s)"), o.durationSec, "od-${o.id}", Modifier.weight(1f)) { v -> h.edit("od-${o.id}") { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(durationSec = v.coerceAtLeast(0.5)) else it }) } }
                }
                OutlinedButton(onClick = { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = pos) else it }) } }) { Text(tr("An den Abspielkopf"), maxLines = 1) }
            }
            is Sel.Sound -> {
                val a = p.audio.firstOrNull { it.id == sel.id } ?: run { onSelect(Sel.None); return@Column }
                val srcLen = sourceLength(a.path)
                PropTitle("A${a.track}: " + a.name) {
                    TipIconButton(Icons.Outlined.Delete, tr("Löschen") + " (Entf)", onDelete, size = 32.dp)
                }
                val owner = a.linkedTo?.let { l -> p.clips.indexOfFirst { it.id == l } }?.takeIf { it >= 0 }
                if (owner != null) {
                    HintText(tr("Mit Clip {0} verknüpft – bewegt und kürzt sich mit ihm. Versatz zur Quelle: {1} s", owner + 1, String.format(Locale.GERMANY, "%+.2f", a.linkOffset)))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { h.edit { VideoEdit.unlink(it, a.id) } }) { Text(tr("Verknüpfung lösen"), maxLines = 1) }
                        OutlinedButton(onClick = { onSelect(Sel.Clip(p.clips[owner].id)) }) { Text(tr("Zum Clip"), maxLines = 1) }
                    }
                } else {
                    // A second sound source (lapel microphone, recorder): synchronise over the waveform with a clip.
                    val candidates = p.clips.indices.filter { i -> val s0 = p.starts()[i]; s0 < a.endSec && s0 + p.clips[i].length > a.startSec }.ifEmpty { p.clips.indices.toList() }
                    var target by remember(a.id) { mutableStateOf(candidates.firstOrNull()) }
                    var mute by remember(a.id) { mutableStateOf(true) }
                    var busy by remember(a.id) { mutableStateOf(false) }
                    var menu by remember(a.id) { mutableStateOf(false) }
                    if (target != null && p.clips.isNotEmpty()) PropSection("sound.sync", tr("Mit dem Bild synchronisieren")) {
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            }) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(tr("Über die Wellenform"), maxLines = 1) }
                            OutlinedButton(onClick = { h.edit { VideoEdit.link(it, p.clips[target!!].id, a.id, null, false) } }) { Text(tr("Verknüpfen, wie es liegt"), maxLines = 1) }
                        }
                    }
                }
                PropSection("sound.level", tr("Ton"), summary = "${(a.volume * 100).toInt()} %") {
                    Text(tr("Lautstärke") + " ${(a.volume * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
                    Slider(value = a.volume, onValueChange = { v -> h.edit("avol-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(volume = v) else it }) } }, valueRange = 0f..1.5f)
                    SecondsField(tr("Ein- und Ausblenden (s)"), a.fadeSec, "af-${a.id}") { v -> h.edit("af-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(fadeSec = v.coerceIn(0.0, 10.0)) else it }) } }
                }
                PropSection("sound.time", tr("Zeit"), summary = clock(a.length, true)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondsField(tr("Beginn (s)"), a.startSec, "ast-${a.id}", Modifier.weight(1f)) { v -> h.edit("ast-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(startSec = v.coerceAtLeast(0.0)) else it }) } }
                        SecondsField(tr("Länge (s)"), a.length, "al-${a.id}", Modifier.weight(1f)) { v -> h.edit("al-${a.id}") { pp -> pp.copy(audio = pp.audio.map { if (it.id == a.id) it.copy(outSec = (it.inSec + v.coerceAtLeast(0.5)).let { o -> srcLen?.let { m -> minOf(o, maxOf(m, it.inSec + 0.5)) } ?: o }) else it }) } }
                    }
                }
            }
            Sel.None -> {
                // „So entsteht dein Video“ (AP-121) above the settings of the project, while nothing is selected.
                val layout = LocalVideoLayout.current
                if (layout?.guide != false) Spot("video.guide") { GuideCard(p, a, onSplit = onSplit, onClose = { layout?.guide = false }) }
                ProjectSettings(h)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectSettings(h: History) {
    val p = h.project
    PropTitle(tr("Projekt"))
    PropSection("project.format", tr("Format"), summary = "${p.width} × ${p.height} · ${p.fps}") {
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
    }
    PropSection("project.timeline", tr("Zeitleiste"), summary = tr("{0} Video- und {1} Tonspuren", p.videoTrackCount, p.audioTrackCount)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Magnet-Zeitleiste"))
                HintText(tr("Schnitte auf V1 ziehen alle Spuren mit, Löschen schließt die Lücke, Ziehen rastet an Kanten ein (N)."))
            }
            Switch(p.magnet, { v -> h.edit { it.copy(magnet = v) } })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { h.edit { VideoEdit.addTrack(it, video = true) } }) { Text(tr("+ Videospur"), maxLines = 1) }
            OutlinedButton(onClick = { h.edit { VideoEdit.addTrack(it, video = false) } }) { Text(tr("+ Tonspur"), maxLines = 1) }
        }
        HintText(tr("{0} Video- und {1} Tonspuren. In der Zeitleiste links: sperren, stumm, ausblenden.", p.videoTrackCount, p.audioTrackCount))
        ChapterList(h)
    }
    PropSection("project.sound", tr("Ton"), summary = listOfNotNull(
        if (p.sound.denoise != "aus") tr("Rauschminderung") else null, if (p.sound.normalize) "−16 LUFS" else null,
    ).joinToString(" · ").ifBlank { null }) {
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
    }
    PropSection("project.subtitles", tr("Untertitel"), defaultOpen = p.subtitles.isNotEmpty(), summary = p.subtitles.size.takeIf { it > 0 }?.toString()) {
        if (p.subtitles.isEmpty()) HintText(tr("Über der Zeitleiste über „Untertitel“ aus der Transkription holen oder eine SRT-/VTT-Datei laden."))
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Ins Bild einbrennen"))
                HintText(tr("Sonst nur als SRT/VTT-Datei neben dem Video (für OPAL und YouTube zum Zuschalten)."))
            }
            Switch(p.burnSubtitles, { v -> h.edit { it.copy(burnSubtitles = v) } })
        }
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
    // „1:23,5“ as well as „83,5“ (AP-121): beginners read times as minutes and seconds, as the timeline shows them.
    LaunchedEffect(value, focused) { if (!focused && parseSeconds(text)?.let { abs(it - value) > 0.005 } != false) text = show(value) }
    OutlinedTextField(
        text, { t -> text = t; parseSeconds(t)?.let(onChange) }, label = { Text(label, maxLines = 1) }, singleLine = true,
        modifier = modifier.onFocusChanged { focused = it.isFocused }.typing(), textStyle = MaterialTheme.typography.bodySmall,
    )
}

/** Seconds typed into a field: „83,5“, „83.5“, „1:23,5“ or „1:02:03“; null while it is not a time (AP-121). */
internal fun parseSeconds(text: String): Double? = io.github.thepytales.llmhub.video.VideoLora.seconds(text)?.takeIf { !it.isNaN() && !it.isInfinite() }

/** Length of the source file at [path] in seconds (null while unknown, and for a picture). */
@Composable
internal fun sourceLength(path: String): Double? {
    val len by produceState<Double?>(null, path) {
        value = withContext(Dispatchers.IO) { File(path).takeUnless { Media.isStill(it) }?.let { Media.probe(it)?.duration?.takeIf { d -> d > 0.05 } } }
    }
    return len
}

/** Whether the file at [path] has sound (null while unknown). */
@Composable
internal fun sourceHasSound(path: String): Boolean? {
    val has by produceState<Boolean?>(null, path) {
        value = withContext(Dispatchers.IO) { Media.probe(File(path))?.hasAudio }
    }
    return has
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

