package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.outlined.PictureAsPdf
import io.github.thepytales.llmhub.data.withTool
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.FileHit
import io.github.thepytales.llmhub.data.FileItem
import io.github.thepytales.llmhub.data.FileKind
import io.github.thepytales.llmhub.data.FileSource
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.data.TrashEntry
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SlideDeck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Opens the Dateien tab at a place: the files of a chat („Dateien dieses Chats“) or a folder. */
object FilesHandoff {
    val chat = MutableStateFlow<String?>(null)
}

/** The parts of the Dateien tab (left on wide screens, chips on top on phones). */
enum class DriveSection(val label: String) {
    RECENT(trLater("Zuletzt")), STARRED(trLater("Markiert")), MINE(trLater("Meine Dateien")), UPLOADS(trLater("Hochgeladen")), CREATED(trLater("Erstellt")), PLACES(trLater("Orte")), TRASH(trLater("Papierkorb")), CHAT(trLater("Aus dem Chat")),
}

private fun DriveSection.icon() = when (this) {
    DriveSection.RECENT -> Icons.Outlined.Schedule
    DriveSection.STARRED -> Icons.Outlined.Star
    DriveSection.MINE -> Icons.Outlined.FolderOpen
    DriveSection.UPLOADS -> Icons.Outlined.CloudUpload
    DriveSection.CREATED -> Icons.Outlined.AutoAwesome
    DriveSection.PLACES -> Icons.Outlined.Storage
    DriveSection.TRASH -> Icons.Outlined.Delete
    DriveSection.CHAT -> Icons.Outlined.ChatBubbleOutline
}

/** An entry of the Dateien tab: a folder or a file (an editor document is its real file). */
data class DriveItem(
    val file: File,
    val dir: Boolean,
    val doc: LiveDoc? = null,
    val fixed: Boolean = false,
    val starred: Boolean = false,
    val chatId: String? = null,
    val modified: Long = file.lastModified(),
    val size: Long = if (dir) 0 else file.length(),
    /** Number of entries of a folder. */
    val count: Int = 0,
) {
    val name: String get() = file.name
    val kind: FileKind get() = doc?.let { FileKind.ofDoc(it.docKind) } ?: FileKind.ofName(file.name)
    val key: String get() = file.absolutePath
}

private val WHEN = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

/**
 * Der Tab „Dateien“ (AP-16 Stufe 0) – like Google Drive: the Lora-Ordner with the fixed folders „Hochgeladen“ and
 * „Erstellt“ and own folders, grid with previews or list, sorting, the big „+“ for new folders, documents and uploads,
 * moving (dragging onto a folder or „Verschieben nach …“), renaming, the Papierkorb, stars, Schlagwörter and the
 * full-text search of AP-18 over everything (also storage locations, project folders and the knowledge collection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(c: AppContainer, onBack: () -> Unit, onOpenDoc: (String) -> Unit, onOpenChat: (String) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drive = c.drive
    val settings by c.settings.settings.collectAsState()
    val changes by drive.changes.collectAsState()
    val docs by c.docs.items.collectAsState()
    val metas by c.fileLibrary.meta.items.collectAsState()
    val chats by c.chats.conversations.collectAsState()
    val trash by drive.trash.items.collectAsState()
    val handoffChat by FilesHandoff.chat.collectAsState()

    var section by rememberSaveable { mutableStateOf(DriveSection.MINE) }
    var folderPath by rememberSaveable { mutableStateOf("") }
    var placeRoot by rememberSaveable { mutableStateOf<String?>(null) }
    var chatFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    val selection = remember { mutableStateMapOf<String, DriveItem>() }

    LaunchedEffect(handoffChat) {
        handoffChat?.let { id ->
            chatFilter = id
            section = DriveSection.CHAT
            FilesHandoff.chat.value = null
        }
    }
    // Files changed in the Explorer or in Word appear without a reload.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            reload++
        }
    }

    /** The folder shown (Meine Dateien, a fixed folder, a place). */
    val currentDir: File? = when (section) {
        DriveSection.MINE -> drive.fileOf(folderPath)
        DriveSection.UPLOADS -> if (folderPath.isEmpty()) drive.uploads else drive.fileOf(folderPath)
        DriveSection.CREATED -> if (folderPath.isEmpty()) drive.created else drive.fileOf(folderPath)
        DriveSection.PLACES -> placeRoot?.let { r -> if (folderPath.isEmpty()) File(r) else File(r, folderPath) }
        else -> null
    }
    /** Where „+“ puts new things. */
    val targetDir: File = currentDir ?: drive.root

    val links by produceState<Map<String, String>>(emptyMap(), chats, changes) { value = withContext(Dispatchers.IO) { runCatching { drive.chatLinks() }.getOrDefault(emptyMap()) } }

    fun item(f: File, links: Map<String, String>): DriveItem {
        val doc = if (f.isFile) c.docs.forRealFile(f) else null
        return DriveItem(
            f, f.isDirectory, doc, fixed = drive.isFixed(f), starred = drive.starred(f) || doc?.starred == true,
            chatId = links[f.absolutePath] ?: doc?.chatId,
            modified = maxOf(f.lastModified(), doc?.updatedAt ?: 0),
            count = if (f.isDirectory) f.listFiles()?.count { drive.visible(it) } ?: 0 else 0,
        )
    }

    val entries by produceState<List<DriveItem>?>(null, section, folderPath, placeRoot, chatFilter, changes, docs, metas, links, reload, settings.filesSort, settings.filesSortDown) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val list = when (section) {
                    DriveSection.RECENT -> drive.allFiles().take(60)
                    DriveSection.STARRED -> drive.allFiles().filter { drive.starred(it) || c.docs.forRealFile(it)?.starred == true } +
                        drive.allFolders().drop(1).map { drive.fileOf(it) }.filter { drive.starred(it) }
                    DriveSection.CHAT -> {
                        val id = chatFilter
                        links.filterValues { it == id }.keys.map(::File).filter { it.exists() }
                    }
                    DriveSection.TRASH -> emptyList()
                    DriveSection.PLACES -> currentDir?.let { drive.list(it) }.orEmpty()
                    else -> currentDir?.let { drive.list(it) }.orEmpty()
                }
                val items = list.map { item(it, links) }
                sortItems(items, settings.filesSort, settings.filesSortDown, keepFolders = section != DriveSection.RECENT)
            }.getOrDefault(emptyList())
        }
    }

    // Full-text search (AP-18) over the Lora-Ordner, storage locations, project folders and the knowledge collection.
    val indexing by c.fileLibrary.indexing.collectAsState()
    val all by produceState<List<FileItem>?>(null, docs, metas, changes, query.isNotBlank()) {
        if (query.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { c.fileLibrary.items() }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(all?.size) { all?.let { list -> withContext(Dispatchers.IO) { runCatching { c.fileLibrary.index(list) } } } }
    var hits by remember { mutableStateOf<List<FileHit>?>(null) }
    LaunchedEffect(query, all, indexing == null) {
        val q = query.trim()
        if (q.length < 2) {
            hits = null
            return@LaunchedEffect
        }
        delay(250)
        val items = all ?: return@LaunchedEffect
        hits = withContext(Dispatchers.Default) { c.fileLibrary.search(q, items, forms = io.github.thepytales.llmhub.data.Vocabulary.forms(c)) }
    }

    // ── Actions ──
    var renaming by remember { mutableStateOf<DriveItem?>(null) }
    var moving by remember { mutableStateOf<List<DriveItem>?>(null) }
    var tagsFor by remember { mutableStateOf<DriveItem?>(null) }
    var versionsOf by remember { mutableStateOf<LiveDoc?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var aiCreate by remember { mutableStateOf<DocKind?>(null) }
    var templates by remember { mutableStateOf(false) }
    var folderSettings by remember { mutableStateOf(false) }
    // Nextcloud (AP-22): fetch files into „Hochgeladen“, save copies there.
    val toNextcloud = rememberNextcloudSave(c)
    var fromNextcloud by remember { mutableStateOf(false) }
    if (fromNextcloud) NextcloudBrowser(
        c, tr("Aus Nextcloud holen"), confirm = tr("In „Hochgeladen“ holen"), onDismiss = { fromNextcloud = false },
        onFiles = { picked ->
            fromNextcloud = false
            scope.launch {
                val got = picked.mapNotNull { e -> runCatching { c.nextcloud.import(e.path) }.onFailure { Notices.error("„${e.name}“: ${it.message}") }.getOrNull() }
                if (got.isNotEmpty()) {
                    drive.changed()
                    Notices.info(if (got.size == 1) tr("„{0}“ liegt jetzt unter „Hochgeladen“.", got.first().name) else tr("{0} Dateien liegen jetzt unter „Hochgeladen“.", got.size))
                }
            }
        },
    )

    fun go(s: DriveSection) {
        section = s
        folderPath = ""
        placeRoot = null
        selection.clear()
        query = ""
    }

    fun openFolder(f: File) {
        selection.clear()
        if (section == DriveSection.PLACES) {
            val r = placeRoot ?: return
            folderPath = f.absoluteFile.relativeTo(File(r).absoluteFile).path.replace(File.separatorChar, '/')
            return
        }
        val rel = drive.rel(f) ?: return
        section = when {
            rel == LoraDrive.UPLOADS || rel.startsWith(LoraDrive.UPLOADS + "/") -> if (section == DriveSection.UPLOADS) DriveSection.UPLOADS else DriveSection.MINE
            rel == LoraDrive.CREATED || rel.startsWith(LoraDrive.CREATED + "/") -> if (section == DriveSection.CREATED) DriveSection.CREATED else DriveSection.MINE
            else -> if (section in setOf(DriveSection.UPLOADS, DriveSection.CREATED)) section else DriveSection.MINE
        }
        folderPath = rel
    }

    /** Opens a PDF in the werkzeugkasten; switches it on first when it is off (the menu entry „Im PDF-Werkzeugkasten bearbeiten“). */
    fun editPdf(it: DriveItem, page: Int? = null) {
        io.github.thepytales.llmhub.agent.LoraFocus.files = listOf(it.file)
        if (!c.settings.current.toolOn(io.github.thepytales.llmhub.data.AppTool.PDF.id)) {
            c.settings.update { s -> s.withTool(io.github.thepytales.llmhub.data.AppTool.PDF, true) }
            Notices.info(tr("PDF-Werkzeugkasten eingeschaltet"))
        }
        PdfHandoff.open(it.file.absolutePath, page ?: 1, it.name)
        onNavigate("pdf")
    }

    fun open(it: DriveItem, page: Int? = null) {
        if (!it.dir) io.github.thepytales.llmhub.agent.LoraFocus.files = listOf(it.file)
        when {
            it.dir -> openFolder(it.file)
            it.doc != null -> onOpenDoc(it.doc.id)
            // CSV-Lotse (AP-93), Archiv-Werkzeug (AP-99), protected Office files (AP-98).
            FileTools.interceptOpen(it.file) -> Unit
            it.kind == FileKind.PDF && c.settings.current.toolOn(io.github.thepytales.llmhub.data.AppTool.PDF.id) -> {
                PdfHandoff.open(it.file.absolutePath, page ?: 1, it.name)
                onNavigate("pdf")
            }
            drive.inside(it.file) && it.file.extension.lowercase() in EDITABLE -> scope.launch {
                val doc = withContext(Dispatchers.IO) {
                    runCatching {
                        val imported = OfficeImport.read(it.name, it.file.readBytes()) ?: return@runCatching null
                        c.docs.openReal(it.file, imported).also { d -> DocsHandoff.saveMedia(c, d, imported) }
                    }.getOrNull()
                }
                if (doc != null) onOpenDoc(doc.id) else openDocument(context, it.file)
            }
            else -> openDocument(context, it.file)
        }
    }

    fun delete(items: List<DriveItem>) {
        drive.systemTrashed = emptyList()
        val removed = drive.delete(items.map { it.file })
        selection.clear()
        drive.systemTrashed.takeIf { it.isNotEmpty() }?.let { names ->
            Notices.info(if (names.size == 1) tr("„{0}“ im Windows-Papierkorb", names.first()) else tr("{0} Einträge im Windows-Papierkorb", names.size))
        }
        if (removed.isNotEmpty()) Notices.undo(
            if (removed.size == 1) tr("„{0}“ im Papierkorb", removed.first().name) else tr("{0} Einträge im Papierkorb", removed.size),
            restore = { removed.forEach { e -> drive.restore(e) } },
        )
    }

    fun upload(name: String, bytes: ByteArray) {
        val target = LoraDrive.unique(targetDir, LoraDrive.safe(name))
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        drive.changed()
    }

    val importFile = rememberMediaImport(anyFile = true) { name, _, input ->
        val bytes = withContext(Dispatchers.IO) { input.use { it.readBytes() } }
        withContext(Dispatchers.IO) { runCatching { upload(name, bytes) }.onFailure { Notices.error(tr("„{0}“ ließ sich nicht ablegen: {1}", name, it.message)) } }
    }
    DroppedFilesEffect { files ->
        scope.launch(Dispatchers.IO) {
            val copied = runCatching { drive.copy(files.filter { it.exists() }, targetDir) }.getOrDefault(emptyList())
            if (copied.isNotEmpty()) Notices.info(tr("{0} Datei(en) nach „{1}“ kopiert", copied.size, targetDir.name))
        }
    }

    // Lora handelt in Dateien (AP-38): she knows the open folder and what is chosen.
    SideEffect {
        io.github.thepytales.llmhub.agent.LoraFocus.folder = currentDir
        if (selection.isNotEmpty()) io.github.thepytales.llmhub.agent.LoraFocus.files = selection.values.map { it.file }
    }
    DisposableEffect(Unit) {
        onDispose {
            io.github.thepytales.llmhub.agent.LoraFocus.folder = null
            io.github.thepytales.llmhub.agent.LoraFocus.files = emptyList()
        }
    }

    // Frag diesen Ordner (AP-58): read the folder's texts in the background (progress below), then a new chat.
    var asking by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    fun askFolder(dir: File) {
        if (asking != null) return
        asking = Triple(dir.name, 0, 0)
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                runCatching { io.github.thepytales.llmhub.agent.FolderAsk.prepare(c, dir) { done, total -> asking = Triple(dir.name, done, total) } }.getOrDefault(emptyList())
            }
            asking = null
            if (files.isEmpty()) { Notices.info(tr("Im Ordner „{0}“ gibt es keine lesbaren Dateien.", dir.name)); return@launch }
            io.github.thepytales.llmhub.agent.FolderAsk.suggestedPrivacy(dir, files)?.let {
                Notices.info(tr("Der Ordner enthält vertraulich wirkende Dateien – Datenschutzstufe „{0}“ empfohlen.", tr(it.label)))
            }
            ChatHandoff.pending.value = ChatHandoff.Content(
                "", emptyList(), mode = io.github.thepytales.llmhub.agent.ChatModes.SOURCES.id,
                instructions = io.github.thepytales.llmhub.agent.FolderAsk.instructions(dir, files),
                handoff = tr("Frag diesen Ordner: {0} ({1} Dateien)", dir.name, files.size),
            )
            ChatHandoff.open.value = ""
        }
    }
    // Dateiwerkzeuge (AP-91, AP-93, AP-97–AP-100); „Frag dieses Archiv“ goes through the folder chat.
    FileToolDialogs(c, onOpenDoc = onOpenDoc, onChanged = { drive.changed() }, onAsk = ::askFolder)
    asking?.let { (name, done, total) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("Frag diesen Ordner")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (total == 0) tr("Suche Dateien in „{0}“ …", name) else tr("Lese {0} von {1} Dateien aus „{2}“ …", done, total, name))
                    if (total > 0) androidx.compose.material3.LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    HintText(tr("Einmal gelesene Dateien kommen danach aus dem Volltext-Cache."))
                }
            },
            confirmButton = {},
        )
    }

    // Schnellansicht and Details-Spalte (AP-66): the file under the mouse (Windows) or chosen, the space bar shows it large.
    var lookAt by remember { mutableStateOf<DriveItem?>(null) }
    var hoverItem by remember { mutableStateOf<DriveItem?>(null) }
    var lastItem by remember { mutableStateOf<DriveItem?>(null) }
    var details by rememberSaveable { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    /** The file the space bar and the Details-Spalte are about. */
    fun target(): DriveItem? = hoverItem ?: selection.values.singleOrNull() ?: lastItem
    val actions = DriveActions(
        open = { open(it) },
        editPdf = { editPdf(it) },
        focus = { io.github.thepytales.llmhub.agent.LoraFocus.files = listOf(it.file); lastItem = it },
        rename = { renaming = it },
        move = { moving = listOf(it) },
        copy = { scope.launch(Dispatchers.IO) { drive.copy(listOf(it.file), it.file.parentFile ?: drive.root) } },
        star = { drive.setStarred(it.file, !it.starred) },
        tags = { tagsFor = it },
        chat = { id -> onOpenChat(id) },
        versions = { versionsOf = it },
        reveal = { openLoraFolder(it.file.parentFile) },
        external = { openDocument(context, it.file) },
        delete = { delete(listOf(it)) },
        select = { if (selection.containsKey(it.key)) selection.remove(it.key) else selection[it.key] = it },
        selecting = { selection.isNotEmpty() },
        selected = { selection.containsKey(it.key) },
        toNextcloud = toNextcloud?.let { save -> { item: DriveItem -> save(item.file) } },
        ask = { item -> askFolder(item.file) },
        selectedFiles = { selection.values.map { it.file } },
        hover = { item -> hoverItem = item; if (item != null) lastItem = item },
        look = { lookAt = it },
    )

    // Dragging onto a folder (Windows): folders note where they are on screen.
    val dropTargets = remember { mutableStateMapOf<String, Pair<Rect, File>>() }
    var dragging by remember { mutableStateOf<List<DriveItem>?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    val hovered = dragging?.let { dropTargets.entries.firstOrNull { (_, v) -> v.first.contains(dragAt) }?.value?.second }
    val drag = if (!DRAG_TO_MOVE) null else DragSupport(
        start = { item -> dragging = if (selection.containsKey(item.key)) selection.values.toList() else listOf(item) },
        moveTo = { p -> dragAt = p },
        end = {
            val target = hovered
            val items = dragging.orEmpty()
            dragging = null
            if (target != null && items.none { it.file.absoluteFile == target.absoluteFile }) {
                val moved = runCatching { drive.move(items.map { it.file }, target) }.onFailure { Notices.error(tr("Verschieben ging nicht: {0}", it.message)) }.getOrDefault(emptyList())
                selection.clear()
                if (moved.isNotEmpty()) Notices.info(tr("{0} nach „{1}“ verschoben", moved.size, target.name))
            }
        },
        register = { key, rect, dir -> if (rect == null) dropTargets.remove(key) else dropTargets[key] = rect to dir },
    )

    val keys = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }
    Scaffold(
        // AP-66: Leertaste opens the Schnellansicht of the file under the mouse or chosen – before a focused button or
        // entry takes it as a click; the search field keeps its spaces.
        modifier = Modifier.focusRequester(keys).focusable().onPreviewKeyEvent { e ->
            if (e.key != Key.Spacebar || searchFocused || lookAt != null) return@onPreviewKeyEvent false
            val t = target() ?: return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) lookAt = t
            true
        },
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Dateien")) },
                actions = {
                    // Details-Spalte (AP-66), on wide windows.
                    IconButton(onClick = { details = !details }) {
                        Icon(Icons.Outlined.Info, contentDescription = if (details) tr("Details ausblenden") else tr("Details einblenden"), tint = if (details) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = { c.settings.update { it.copy(filesGrid = !it.filesGrid) } }) {
                        Icon(if (settings.filesGrid) Icons.Outlined.ViewList else Icons.Outlined.GridView, contentDescription = if (settings.filesGrid) tr("Liste") else tr("Raster"))
                    }
                    SortMenu(settings.filesSort, settings.filesSortDown) { sort, down -> c.settings.update { it.copy(filesSort = sort, filesSortDown = down) } }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
                        DropdownMenu(menu, { menu = false }) {
                            if (LORA_FOLDER_OPENS) DropdownMenuItem(text = { Text(tr("Im Explorer öffnen")) }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) }, onClick = { menu = false; openLoraFolder(currentDir) })
                            DropdownMenuItem(text = { Text(tr("Lora-Ordner …")) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { menu = false; folderSettings = true })
                            DropdownMenuItem(text = { Text(tr("Wissenssammlung")) }, onClick = { menu = false; onNavigate("settings/knowledge") })
                            if (settings.hasNextcloud) DropdownMenuItem(text = { Text(tr("Aus Nextcloud holen …")) }, leadingIcon = { Icon(Icons.Outlined.Cloud, null) }, onClick = { menu = false; fromNextcloud = true })
                            else DropdownMenuItem(text = { Text(tr("Nextcloud verbinden …")) }, leadingIcon = { Icon(Icons.Outlined.Cloud, null) }, onClick = { menu = false; onNavigate("settings/connections") })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (section != DriveSection.TRASH && query.isBlank() && selection.isEmpty() && (section != DriveSection.PLACES || placeRoot != null)) {
                NewMenuButton(
                    onFolder = { newFolder = true },
                    onDoc = { k -> onOpenDoc(newLiveDoc(c, k, folder = drive.rel(targetDir) ?: "").id) },
                    onTemplate = { templates = true },
                    onAi = { k -> aiCreate = k },
                    onUpload = importFile,
                    onScan = { onNavigate("scan") },
                )
            }
        },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Spot("files.places") {
                    Column(Modifier.width(232.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        DriveSection.entries.filter { it != DriveSection.CHAT || chatFilter != null }.forEach { s ->
                            val target = when (s) {
                                DriveSection.MINE -> drive.root
                                DriveSection.UPLOADS -> drive.uploads
                                DriveSection.CREATED -> drive.created
                                else -> null
                            }
                            NavigationDrawerItem(
                                label = { Text(if (s == DriveSection.CHAT) chatTitle(c, chatFilter) else tr(s.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(s.icon(), contentDescription = null) },
                                badge = { if (s == DriveSection.TRASH && trash.isNotEmpty()) Text("${trash.size}") },
                                selected = section == s,
                                onClick = { go(s) },
                                modifier = Modifier.heightIn(min = 44.dp).then(if (target != null && drag != null) Modifier.dropTarget("nav:${s.name}", target, drag, hovered) else Modifier),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        HintText(loraFolderLabel(), Modifier.padding(12.dp).clickable { folderSettings = true })
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Spot("files.search") {
                        OutlinedTextField(
                            query, { query = it }, singleLine = true, shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp).widthIn(max = 900.dp)
                                .onFocusChanged { searchFocused = it.isFocused },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, tr("Leeren")) } },
                            placeholder = { Text(tr("In Dateien suchen – Namen, Schlagwörter und Inhalt")) },
                        )
                    }
                    if (!wide) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DriveSection.entries.filter { it != DriveSection.CHAT || chatFilter != null }.forEach { s ->
                                FilterChip(section == s, { go(s) }, label = { Text(if (s == DriveSection.CHAT) chatTitle(c, chatFilter) else tr(s.label), maxLines = 1) }, leadingIcon = { Icon(s.icon(), null, Modifier.size(16.dp)) })
                            }
                        }
                    }
                    if (selection.isNotEmpty()) {
                        SelectionBar(
                            count = selection.size,
                            onMove = { moving = selection.values.toList() },
                            onStar = { selection.values.forEach { drive.setStarred(it.file, true) }; selection.clear() },
                            onDelete = { delete(selection.values.toList()) },
                            onClear = { selection.clear() },
                        )
                    } else if (query.isBlank()) {
                        Breadcrumbs(
                            section, folderPath, placeRoot, drive, chatTitle(c, chatFilter),
                            onPath = { p -> folderPath = p; selection.clear() },
                            onPlaces = { placeRoot = null; folderPath = "" },
                            drag = drag, hovered = hovered,
                        )
                    }
                    indexing?.takeIf { query.isNotBlank() }?.let { (d, t) ->
                        LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            query.isNotBlank() -> SearchResults(c, hits, query, onOpen = { h ->
                                val f = h.item.path?.let(::File)
                                when {
                                    h.item.docId != null -> onOpenDoc(h.item.docId)
                                    h.item.source == FileSource.KNOWLEDGE -> onNavigate("settings/knowledge")
                                    f != null -> open(item(f, links), h.page)
                                }
                            })
                            section == DriveSection.TRASH -> TrashList(drive, trash)
                            section == DriveSection.PLACES && placeRoot == null -> PlacesList(c) { dir -> placeRoot = dir.absolutePath; folderPath = "" }
                            entries == null -> {}
                            entries!!.isEmpty() -> EmptyHint(section)
                            settings.filesGrid -> DriveGrid(c, entries!!, actions, drag, hovered)
                            else -> DriveList(c, entries!!, actions, drag, hovered, chats.associate { it.id to it.title })
                        }
                    }
                }
                // Details-Spalte (AP-66).
                if (wide && details) {
                    androidx.compose.material3.VerticalDivider()
                    FileDetailsPane(
                        c, target(), chatTitle = { id -> chats.firstOrNull { it.id == id }?.title },
                        onLook = { lookAt = it }, onOpen = { open(it) }, onClose = { details = false },
                    )
                }
            }
            // The dragged files follow the pointer.
            dragging?.let { list ->
                val density = LocalDensity.current
                Surface(
                    shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp,
                    modifier = Modifier.offset { IntOffset(dragAt.x.roundToInt() + 12, dragAt.y.roundToInt() - with(density) { 60.dp.roundToPx() }) },
                ) {
                    Text(
                        (if (list.size == 1) list.first().name else tr("{0} Einträge", list.size)) + (hovered?.let { " → ${it.name}" } ?: ""),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    renaming?.let { it0 ->
        TextDialog(tr("Umbenennen"), if (it0.dir) it0.name else it0.file.nameWithoutExtension, confirm = tr("Umbenennen"), onDismiss = { renaming = null }) { name ->
            renaming = null
            runCatching { drive.rename(it0.file, name) }.onFailure { Notices.error(it.message ?: tr("Umbenennen ging nicht.")) }
        }
    }
    lookAt?.let { start ->
        QuickLookDialog(c, entries.orEmpty().filter { !it.dir || it.key == start.key }.ifEmpty { listOf(start) }, start, onOpen = { lookAt = null; open(it) }, onClose = { lookAt = null })
    }
    moving?.let { list ->
        MoveDialog(drive, list, placeRoot?.let(::File), onDismiss = { moving = null }) { target ->
            moving = null
            val moved = runCatching { drive.move(list.map { it.file }, target) }.onFailure { Notices.error(tr("Verschieben ging nicht: {0}", it.message)) }.getOrDefault(emptyList())
            selection.clear()
            if (moved.isNotEmpty()) Notices.info(tr("{0} nach „{1}“ verschoben", moved.size, target.name.ifBlank { tr("Lora-Ordner") }))
        }
    }
    if (newFolder) TextDialog(tr("Neuer Ordner"), tr("Neuer Ordner"), confirm = tr("Erstellen"), onDismiss = { newFolder = false }) { name ->
        newFolder = false
        runCatching { drive.mkdir(targetDir, name) }.onFailure { Notices.error(tr("Ordner ließ sich nicht anlegen: {0}", it.message)) }
    }
    tagsFor?.let { it0 ->
        val key = drive.metaKey(it0.file)
        val fi = FileItem(key, it0.name, it0.kind, FileSource.DISK, FileLibraryPlace, "", "", it0.modified, tags = c.fileLibrary.meta.get(key)?.tags.orEmpty())
        TagsDialog(fi, c.fileLibrary.allTags(emptyList()) + metas.flatMap { it.tags }.distinct(), onDismiss = { tagsFor = null }) { tags ->
            c.fileLibrary.setTags(key, tags)
            tagsFor = null
        }
    }
    versionsOf?.let { d -> VersionCompareDialog(d, onDismiss = { versionsOf = null }) }
    aiCreate?.let { k -> AiCreateDialog(c, k, folder = drive.rel(targetDir) ?: "", onDismiss = { aiCreate = null }, onOpen = onOpenDoc) }
    if (templates) TemplatePickerDialog(onDismiss = { templates = false }) { kind, title, md, style ->
        templates = false
        onOpenDoc(newLiveDoc(c, kind, md, title, style, folder = drive.rel(targetDir) ?: "").id)
    }
    if (folderSettings) AlertDialog(
        onDismissRequest = { folderSettings = false },
        title = { Text(tr("Lora-Ordner")) },
        text = { Column(Modifier.heightIn(max = 520.dp)) { LoraFolderSettings() } },
        confirmButton = { TextButton(onClick = { folderSettings = false }) { Text(tr("Fertig")) } },
    )
}

private val FileLibraryPlace = trLater("Lora-Ordner")

/** Formats the editor opens in place (and writes back into) when they lie in the Lora-Ordner. */
private val EDITABLE = setOf("docx", "pptx", "xlsx", "xlsm", "md", "markdown")

private fun chatTitle(c: AppContainer, id: String?): String = id?.let { c.chats.conversations.value.firstOrNull { it.id == id }?.title } ?: tr("Aus dem Chat")

/** Folders first (except in „Zuletzt“), then by [sort]. */
internal fun sortItems(items: List<DriveItem>, sort: String, down: Boolean, keepFolders: Boolean = true): List<DriveItem> {
    val cmp: Comparator<DriveItem> = when (sort) {
        "modified" -> compareBy { it.modified }
        "kind" -> compareBy<DriveItem> { it.kind.label }.thenBy { it.name.lowercase() }
        "size" -> compareBy { it.size }
        else -> compareBy { it.name.lowercase() }
    }
    val ordered = if (down) cmp.reversed() else cmp
    // Fixed folders (Hochgeladen, Erstellt) always come first.
    val withFolders = if (keepFolders) compareBy<DriveItem> { !it.fixed }.thenBy { !it.dir }.then(ordered) else compareBy<DriveItem> { 0 }.then(ordered)
    return items.sortedWith(withFolders)
}

private class DriveActions(
    val open: (DriveItem) -> Unit,
    /** Opens a PDF in the PDF-Werkzeugkasten (switched on if needed). */
    val editPdf: (DriveItem) -> Unit,
    /** The item the user works with (its menu opened): Lora's actions use it when no file is named (AP-38). */
    val focus: (DriveItem) -> Unit,
    val rename: (DriveItem) -> Unit,
    val move: (DriveItem) -> Unit,
    val copy: (DriveItem) -> Unit,
    val star: (DriveItem) -> Unit,
    val tags: (DriveItem) -> Unit,
    val chat: (String) -> Unit,
    val versions: (LiveDoc) -> Unit,
    val reveal: (DriveItem) -> Unit,
    val external: (DriveItem) -> Unit,
    val delete: (DriveItem) -> Unit,
    val select: (DriveItem) -> Unit,
    val selecting: () -> Boolean,
    val selected: (DriveItem) -> Boolean,
    /** Nextcloud (AP-22): save a copy there; null while it is not set up. */
    val toNextcloud: ((DriveItem) -> Unit)? = null,
    /** „Frag diesen Ordner“ (AP-58): a chat in the Quellenmodus over the folder's files. */
    val ask: ((DriveItem) -> Unit)? = null,
    /** The files of the selection (AP-59: dragging out and copying several at once). */
    val selectedFiles: () -> List<File> = { emptyList() },
    /** The mouse is over this item / left it (AP-66: Leertaste and Details-Spalte). */
    val hover: (DriveItem?) -> Unit = {},
    /** Schnellansicht of the item (AP-66). */
    val look: (DriveItem) -> Unit = {},
)

/** Dragging items onto folders with the mouse (Windows). */
private class DragSupport(
    val start: (DriveItem) -> Unit,
    val moveTo: (Offset) -> Unit,
    val end: () -> Unit,
    val register: (String, Rect?, File) -> Unit,
)

/** Makes this place a target for dragged files: it notes its bounds and lights up while something hovers over it. */
private fun Modifier.dropTarget(key: String, dir: File, drag: DragSupport, hovered: File?): Modifier =
    this.onGloballyPositioned { drag.register(key, it.boundsInWindow(), dir) }
        .then(if (hovered?.absoluteFile == dir.absoluteFile) Modifier.border(2.dp, Color(0xFF00305D), RoundedCornerShape(12.dp)) else Modifier)

/** Clicks, long press (select), right click (menu) and – on Windows – dragging of an item. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.itemGestures(item: DriveItem, actions: DriveActions, drag: DragSupport?, onMenu: () -> Unit): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = this
        .onGloballyPositioned { origin = it.positionInWindow() }
        // AP-66: which file the mouse is over (the space bar shows it, the Details-Spalte describes it).
        .pointerInput(item.key, "hover") {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    when (e.type) {
                        androidx.compose.ui.input.pointer.PointerEventType.Enter -> actions.hover(item)
                        androidx.compose.ui.input.pointer.PointerEventType.Exit -> actions.hover(null)
                    }
                }
            }
        }
        .combinedClickable(
            onClick = { if (actions.selecting()) actions.select(item) else actions.open(item) },
            onLongClick = { actions.select(item) },
            onDoubleClick = if (DRAG_TO_MOVE) ({ actions.open(item) }) else null,
        )
        .secondaryClick(onMenu)
    if (drag == null || item.fixed) return base
    return base.pointerInput(item.key) {
        detectDragGestures(
            onDragStart = { p -> drag.start(item); drag.moveTo(origin + p) },
            onDrag = { change, _ -> drag.moveTo(origin + change.position) },
            onDragEnd = { drag.end() },
            onDragCancel = { drag.end() },
        )
    }
}

// ── List and grid ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DriveList(c: AppContainer, items: List<DriveItem>, actions: DriveActions, drag: DragSupport?, hovered: File?, chatTitles: Map<String, String>) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LORA_BUTTON_ROOM + 72.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.key }) { item -> DriveRow(c, item, actions, drag, hovered, chatTitles) }
    }
}

@Composable
private fun DriveRow(c: AppContainer, item: DriveItem, actions: DriveActions, drag: DragSupport?, hovered: File?, chatTitles: Map<String, String>) {
    var menu by remember { mutableStateOf(false) }
    val selected = actions.selected(item)
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .then(if (item.dir && drag != null) Modifier.dropTarget(item.key, item.file, drag, hovered) else Modifier)
                .itemGestures(item, actions, drag) { menu = true }
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindTile(item, selected)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HintText(
                        listOfNotNull(
                            tr("Geändert ") + WHEN.format(Date(item.modified)),
                            if (item.dir) "${item.count} " + (if (item.count == 1) tr("Eintrag") else tr("Einträge")) else fileSizeLabel(item.size).ifEmpty { null },
                            item.doc?.let { d -> d.openComments.takeIf { it > 0 }?.let { tr("{0} Kommentare", it) } },
                        ).joinToString(" · "),
                    )
                    item.chatId?.let { id ->
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), modifier = Modifier.clickable { actions.chat(id) }) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(tr("aus „{0}“", chatTitles[id] ?: tr("Chat")).take(48), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (item.starred) Icon(Icons.Outlined.Star, contentDescription = tr("Markiert"), tint = Color(0xFFF2B01E), modifier = Modifier.size(18.dp))
            // In Word/Excel/PowerPoint geöffnet (AP-59).
            if (!item.dir && ExternalOffice.isOpen(item.file)) HintText(tr("in {0} geöffnet", ExternalOffice.program(item.file) ?: "Office"))
            // Out of Lora into the Explorer, a mail or another program (AP-59, Windows).
            if (!item.fixed) FileDragHandle { if (actions.selected(item)) actions.selectedFiles() else listOf(item.file) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
        }
        ItemMenu(c, item, actions, menu) { menu = false }
    }
}

@Composable
private fun KindTile(item: DriveItem, selected: Boolean, size: Int = 40) {
    val color = if (item.dir) Color(0xFF5F6B7A) else item.kind.color()
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            when {
                selected -> Icon(Icons.Outlined.CheckCircle, contentDescription = tr("Ausgewählt"), tint = MaterialTheme.colorScheme.primary)
                item.fixed -> Icon(if (item.name == LoraDrive.UPLOADS) Icons.Outlined.CloudUpload else Icons.Outlined.FolderSpecial, null, tint = Color(0xFF00305D))
                item.dir -> Icon(Icons.Outlined.Folder, null, tint = color)
                else -> Icon(item.kind.icon(), null, tint = color, modifier = Modifier.size((size / 2).dp))
            }
        }
    }
}

@Composable
private fun DriveGrid(c: AppContainer, items: List<DriveItem>, actions: DriveActions, drag: DragSupport?, hovered: File?) {
    val folders = items.filter { it.dir }
    val files = items.filter { !it.dir }
    LazyVerticalGrid(
        GridCells.Adaptive(176.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LORA_BUTTON_ROOM + 72.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (folders.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { HintText(tr("Ordner")) }
        items(folders, key = { it.key }) { f -> FolderCard(c, f, actions, drag, hovered) }
        if (files.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { HintText(tr("Dateien"), Modifier.padding(top = 6.dp)) }
        items(files, key = { it.key }) { f -> FileCard(c, f, actions, drag) }
    }
}

@Composable
private fun FolderCard(c: AppContainer, item: DriveItem, actions: DriveActions, drag: DragSupport?, hovered: File?) {
    var menu by remember { mutableStateOf(false) }
    val selected = actions.selected(item)
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else LocalChatLook.current.card, border = LocalChatLook.current.cardBorder,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (drag != null) Modifier.dropTarget(item.key, item.file, drag, hovered) else Modifier).itemGestures(item, actions, drag) { menu = true },
        ) {
            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                KindTile(item, selected, size = 32)
                Spacer(Modifier.width(10.dp))
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
            }
        }
        ItemMenu(c, item, actions, menu) { menu = false }
    }
}

@Composable
private fun FileCard(c: AppContainer, item: DriveItem, actions: DriveActions, drag: DragSupport?) {
    var menu by remember { mutableStateOf(false) }
    val selected = actions.selected(item)
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else LocalChatLook.current.card, border = LocalChatLook.current.cardBorder,
            modifier = Modifier.fillMaxWidth().itemGestures(item, actions, drag) { menu = true },
        ) {
            Column {
                Row(Modifier.padding(start = 10.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selected) Icons.Outlined.CheckCircle else item.kind.icon(), null, Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else item.kind.color())
                    Spacer(Modifier.width(8.dp))
                    Text(item.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr"), modifier = Modifier.size(18.dp)) }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(1.25f).padding(start = 8.dp, end = 8.dp, bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    DriveThumb(c, item)
                    if (item.starred) Icon(Icons.Outlined.Star, null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp), tint = Color(0xFFF2B01E))
                    if (item.chatId != null) Icon(Icons.Outlined.ChatBubbleOutline, tr("Aus einem Chat"), Modifier.align(Alignment.BottomEnd).padding(4.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ItemMenu(c, item, actions, menu) { menu = false }
    }
}

/** What a preview shows: a picture (PDF page, photo) or the start of the text. */
private sealed class Preview {
    class Picture(val image: ImageBitmap) : Preview()
    class Page(val title: String, val text: String) : Preview()
    class Grid(val rows: List<List<String>>) : Preview()
    class Slide(val slide: SlideDeck.Slide) : Preview()
    object None : Preview()
}

/** Previews of the grid, kept for the last files shown. */
private object Thumbs {
    private val cache = object : LinkedHashMap<String, Preview>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Preview>?) = size > 120
    }

    @Synchronized
    fun get(key: String) = cache[key]

    @Synchronized
    fun put(key: String, p: Preview) {
        cache[key] = p
    }
}

/** Preview of a file in the grid: the first page or slide, the start of a table, a photo or the PDF's first page. */
@Composable
private fun DriveThumb(c: AppContainer, item: DriveItem) {
    val key = item.key + ":" + item.modified + ":" + item.size
    val preview by produceState(Thumbs.get(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { makePreview(c, item) }.getOrDefault(Preview.None) }.also { Thumbs.put(key, it) }
    }
    when (val p = preview) {
        is Preview.Picture -> androidx.compose.foundation.Image(p.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        is Preview.Slide -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SlideCanvas(p.slide, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        is Preview.Page -> Surface(color = Color.White, modifier = Modifier.fillMaxSize().padding(10.dp), shadowElevation = 1.dp) {
            Column(Modifier.padding(8.dp)) {
                if (p.title.isNotBlank()) Text(p.title, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2933), maxLines = 2)
                Text(p.text, fontSize = 6.sp, lineHeight = 8.sp, color = Color(0xFF52606D), overflow = TextOverflow.Clip)
            }
        }
        is Preview.Grid -> Surface(color = Color.White, modifier = Modifier.fillMaxSize().padding(10.dp), shadowElevation = 1.dp) {
            Column(Modifier.padding(4.dp)) {
                p.rows.forEachIndexed { r, row ->
                    Row {
                        row.forEach { cell ->
                            Text(
                                cell, fontSize = 6.sp, lineHeight = 8.sp, maxLines = 1, overflow = TextOverflow.Clip,
                                fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF1F2933),
                                modifier = Modifier.weight(1f).border(0.5.dp, Color(0xFFD9E2EC)).padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(item.kind.icon(), null, Modifier.size(42.dp), tint = item.kind.color().copy(alpha = 0.7f))
        }
    }
}

private fun makePreview(c: AppContainer, item: DriveItem): Preview {
    val f = item.file
    item.doc?.let { d ->
        return when (d.docKind) {
            DocKind.SLIDES -> SlideDeck.parse(d.tabs.first().markdown).firstOrNull()?.let { Preview.Slide(it) } ?: Preview.None
            DocKind.SHEET -> Preview.Grid(SheetEngine.grid(d.tabs.first().markdown).take(8).map { it.take(4) })
            else -> page(d.title, d.markdown())
        }
    }
    return when (item.kind) {
        FileKind.PDF -> PdfRender.page(f, 0, 360)?.let { Preview.Picture(it) } ?: Preview.None
        FileKind.IMAGE -> if (f.length() < 15_000_000) decodeImage(f.readBytes())?.let { Preview.Picture(it) } ?: Preview.None else Preview.None
        FileKind.POWERPOINT -> if (f.extension.equals("pptx", true)) {
            runCatching { SlideDeck.parse(OfficeImport.pptx(f.readBytes())).firstOrNull() }.getOrNull()?.let { Preview.Slide(it) } ?: Preview.None
        } else Preview.None
        FileKind.EXCEL -> runCatching { OfficeImport.read(f.name, f.readBytes())?.tabs?.firstOrNull()?.second }.getOrNull()
            ?.let { Preview.Grid(SheetEngine.grid(it).take(8).map { r -> r.take(4) }) } ?: Preview.None
        FileKind.WORD, FileKind.TEXT, FileKind.MARKDOWN, FileKind.MAIL -> c.fileLibrary.textOf(f)?.let { page("", it) } ?: Preview.None
        else -> Preview.None
    }
}

private fun page(title: String, markdown: String): Preview {
    val blocks = DocBlocks.split(markdown)
    val head = DocBlocks.titleOf(markdown) ?: title
    val text = blocks.filterNot { it.startsWith("# ") }.joinToString("\n") { DocBlocks.plainOf(it) }.take(700)
    return Preview.Page(head, text)
}

// ── Menus and bars ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ItemMenu(c: AppContainer, item: DriveItem, actions: DriveActions, open: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(open, onDismiss) {
        fun act(a: () -> Unit) { onDismiss(); a() }
        DropdownMenuItem(text = { Text(tr("Öffnen")) }, onClick = { act { actions.open(item) } })
        // Schnellansicht (AP-66): large, without opening (Windows: Leertaste).
        DropdownMenuItem(text = { Text(tr("Vorschau") + if (DESKTOP) " " + tr("(Leertaste)") else "") }, leadingIcon = { Icon(Icons.Outlined.Visibility, null) }, onClick = { act { actions.look(item) } })
        if (open) LaunchedEffect(item.key) { actions.focus(item) }
        if (!item.dir && item.kind == FileKind.PDF) {
            DropdownMenuItem(
                text = { Text(tr("Im PDF-Werkzeugkasten bearbeiten")) },
                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                onClick = { act { actions.editPdf(item) } },
            )
        }
        if (item.dir) actions.ask?.let { ask ->
            DropdownMenuItem(text = { Text(tr("Frag diesen Ordner")) }, leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, onClick = { act { ask(item) } })
        }
        if (item.fixed) {
            if (LORA_FOLDER_OPENS) DropdownMenuItem(text = { Text(tr("Im Explorer zeigen")) }, onClick = { act { openLoraFolder(item.file) } })
            return@DropdownMenu
        }
        if (!item.dir) DropdownMenuItem(text = { Text(tr("Mit anderer App öffnen")) }, onClick = { act { actions.external(item) } })
        // AP-59: Word/Excel/PowerPoint with the way back; files onto the clipboard for the Explorer or a mail.
        if (!item.dir && ExternalOffice.opener != null) ExternalOffice.program(item.file)?.let { prog ->
            DropdownMenuItem(text = { Text(tr("In {0} öffnen", prog)) }, onClick = { act { ExternalOffice.opener?.invoke(item.file) } })
        }
        if (CAN_COPY_FILES) DropdownMenuItem(text = { Text(tr("Als Datei kopieren (für Explorer oder Mail)")) }, onClick = {
            act { if (copyFilesToClipboard(if (actions.selected(item)) actions.selectedFiles() else listOf(item.file))) Notices.info(tr("Kopiert – im Explorer oder in einer Mail mit Strg+V einfügen.")) }
        })
        if (!item.dir) actions.toNextcloud?.let { save -> DropdownMenuItem(text = { Text(tr("In Nextcloud speichern")) }, leadingIcon = { Icon(Icons.Outlined.Cloud, null) }, onClick = { act { save(item) } }) }
        item.chatId?.let { id -> DropdownMenuItem(text = { Text(tr("Zum Chat")) }, leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) }, onClick = { act { actions.chat(id) } }) }
        // Mehrere Fenster (AP-84, PC): a document of the app in a window of its own.
        item.doc?.let { d -> WindowHooks.openDoc?.let { open -> DropdownMenuItem(text = { Text(tr("In neuem Fenster öffnen")) }, onClick = { act { open(d.id, d.title) } }) } }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(tr("Umbenennen")) }, onClick = { act { actions.rename(item) } })
        DropdownMenuItem(text = { Text(tr("Verschieben nach …")) }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { act { actions.move(item) } })
        DropdownMenuItem(text = { Text(tr("Kopie erstellen")) }, onClick = { act { actions.copy(item) } })
        DropdownMenuItem(
            text = { Text(if (item.starred) tr("Markierung entfernen") else tr("Markieren")) },
            leadingIcon = { Icon(if (item.starred) Icons.Outlined.StarBorder else Icons.Outlined.Star, null) },
            onClick = { act { actions.star(item) } },
        )
        DropdownMenuItem(text = { Text(tr("Schlagwörter …")) }, onClick = { act { actions.tags(item) } })
        DropdownMenuItem(text = { Text(if (actions.selected(item)) tr("Auswahl aufheben") else tr("Auswählen")) }, onClick = { act { actions.select(item) } })
        // Dateiwerkzeuge (AP-91, AP-93, AP-97–AP-100).
        FileToolMenuItems(item.file, onDismiss)
        item.doc?.takeIf { it.versions.size > 1 }?.let { d -> DropdownMenuItem(text = { Text(tr("Versionen vergleichen")) }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { act { actions.versions(d) } }) }
        if (LORA_FOLDER_OPENS) DropdownMenuItem(text = { Text(tr("Im Explorer zeigen")) }, onClick = { act { actions.reveal(item) } })
        HorizontalDivider()
        DropdownMenuItem(colors = dangerMenuColors(), text = { Text(tr("In den Papierkorb")) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { act { actions.delete(item) } })
    }
}

@Composable
private fun SelectionBar(count: Int, onMove: () -> Unit, onStar: () -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = tr("Auswahl aufheben")) }
            Text(tr("{0} ausgewählt", count), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onMove) { Icon(Icons.Outlined.DriveFileMove, contentDescription = tr("Verschieben nach …")) }
            IconButton(onClick = onStar) { Icon(Icons.Outlined.Star, contentDescription = tr("Markieren")) }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = tr("In den Papierkorb")) }
        }
    }
}

@Composable
private fun Breadcrumbs(
    section: DriveSection, path: String, placeRoot: String?, drive: LoraDrive, chatTitle: String,
    onPath: (String) -> Unit, onPlaces: () -> Unit, drag: DragSupport?, hovered: File?,
) {
    val parts = path.split('/').filter { it.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun crumb(label: String, target: File?, last: Boolean, onClick: () -> Unit) {
            Text(
                label, style = if (last) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).then(if (target != null && drag != null && !last) Modifier.dropTarget("crumb:${target.absolutePath}", target, drag, hovered) else Modifier)
                    .clickable(enabled = !last, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        when (section) {
            DriveSection.MINE, DriveSection.UPLOADS, DriveSection.CREATED -> {
                val (rootLabel, rootPath) = when (section) {
                    DriveSection.UPLOADS -> LoraDrive.UPLOADS to LoraDrive.UPLOADS
                    DriveSection.CREATED -> LoraDrive.CREATED to LoraDrive.CREATED
                    else -> tr("Meine Dateien") to ""
                }
                val skip = if (rootPath.isEmpty()) 0 else 1
                crumb(rootLabel, drive.fileOf(rootPath), parts.size <= skip) { onPath(rootPath) }
                parts.drop(skip).forEachIndexed { i, p ->
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    val sub = parts.take(skip + i + 1).joinToString("/")
                    crumb(p, drive.fileOf(sub), i == parts.size - skip - 1) { onPath(sub) }
                }
            }
            DriveSection.PLACES -> {
                crumb(tr("Orte"), null, placeRoot == null) { onPlaces() }
                placeRoot?.let { r ->
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    crumb(File(r).name, File(r), parts.isEmpty()) { onPath("") }
                    parts.forEachIndexed { i, p ->
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                        val sub = parts.take(i + 1).joinToString("/")
                        crumb(p, File(r, sub), i == parts.size - 1) { onPath(sub) }
                    }
                }
            }
            DriveSection.CHAT -> crumb(tr("Dateien aus „{0}“", chatTitle), null, true) {}
            else -> crumb(tr(section.label), null, true) {}
        }
    }
}

@Composable
private fun SortMenu(sort: String, down: Boolean, onChange: (String, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Spot("files.sort") { IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = tr("Sortieren")) } }
        DropdownMenu(open, { open = false }) {
            listOf("name" to tr("Name"), "modified" to tr("Geändert"), "kind" to tr("Typ"), "size" to tr("Größe")).forEach { (k, label) ->
                DropdownMenuItem(
                    text = { Text(label + if (k == sort) (if (down) "  ↓" else "  ↑") else "") },
                    onClick = { open = false; onChange(k, if (k == sort) !down else k == "modified") },
                )
            }
        }
    }
}

/** The big „+“ at the bottom right: folder, documents, from a template, with AI, upload. */
@Composable
private fun NewMenuButton(onFolder: () -> Unit, onDoc: (DocKind) -> Unit, onTemplate: () -> Unit, onAi: (DocKind) -> Unit, onUpload: () -> Unit, onScan: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Spot("files.new") {
            FloatingActionButton(
                onClick = { open = true }, shape = RoundedCornerShape(20.dp), containerColor = Color(0xFF00305D), contentColor = Color.White,
                modifier = Modifier.padding(bottom = LORA_BUTTON_ROOM - 24.dp).size(72.dp),
            ) { Icon(Icons.Outlined.Add, contentDescription = tr("Neu"), modifier = Modifier.size(34.dp)) }
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(tr("Ordner")) }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { open = false; onFolder() })
            DropdownMenuItem(text = { Text(tr("Hochladen …")) }, leadingIcon = { Icon(Icons.Outlined.CloudUpload, null) }, onClick = { open = false; onUpload() })
            DropdownMenuItem(text = { Text(tr("Scannen …")) }, leadingIcon = { Icon(Icons.Outlined.DocumentScanner, null) }, onClick = { open = false; onScan() })
            HorizontalDivider()
            DocKind.entries.forEach { k ->
                DropdownMenuItem(text = { Text(k.label) }, leadingIcon = { Icon(kindIcon(k), null, tint = kindColor(k)) }, onClick = { open = false; onDoc(k) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(tr("Aus Vorlage …")) }, onClick = { open = false; onTemplate() })
            DocKind.entries.forEach { k ->
                DropdownMenuItem(text = { Text(tr("{0} mit KI …", k.label)) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { open = false; onAi(k) })
            }
        }
    }
}

// ── Dialogs and other views ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun TextDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onDone(text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** „Verschieben nach …“: all folders of the Lora-Ordner (or of the place that is open), with a new folder on the way. */
@Composable
private fun MoveDialog(drive: LoraDrive, items: List<DriveItem>, placeRoot: File?, onDismiss: () -> Unit, onMove: (File) -> Unit) {
    val base = placeRoot ?: drive.root
    var reload by remember { mutableStateOf(0) }
    val folders by produceState(emptyList<String>(), reload) {
        value = withContext(Dispatchers.IO) {
            if (placeRoot == null) drive.allFolders()
            else listOf("") + placeRoot.walkTopDown().maxDepth(6).filter { it.isDirectory && it != placeRoot && drive.visible(it) }
                .map { it.relativeTo(placeRoot).path.replace(File.separatorChar, '/') }.sorted().toList()
        }
    }
    var chosen by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf<String?>(null) }
    val moving = items.map { it.file.absolutePath }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.size == 1) tr("„{0}“ verschieben", items.first().name) else tr("{0} Einträge verschieben", items.size), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(folders.filter { f -> moving.none { m -> File(base, f).absolutePath.let { it == m || it.startsWith(m + File.separator) } } }) { f ->
                        val depth = if (f.isEmpty()) 0 else f.count { it == '/' } + 1
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (chosen == f) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { chosen = f }.padding(start = (8 + depth * 16).dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (f.isEmpty()) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (f.isEmpty()) (placeRoot?.name ?: tr("Meine Dateien")) else f.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                TextButton(onClick = { newName = "" }) {
                    Icon(Icons.Outlined.CreateNewFolder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Neuer Ordner hier"))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(if (chosen.isEmpty()) base else File(base, chosen)) }) { Text(tr("Hierhin verschieben")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
    newName?.let {
        TextDialog(tr("Neuer Ordner"), tr("Neuer Ordner"), confirm = tr("Erstellen"), onDismiss = { newName = null }) { name ->
            newName = null
            val dir = drive.mkdir(if (chosen.isEmpty()) base else File(base, chosen), name)
            chosen = dir.absoluteFile.relativeTo(base.absoluteFile).path.replace(File.separatorChar, '/')
            reload++
        }
    }
}

/** „Aus Vorlage …“: the templates of all kinds of documents. */
@Composable
private fun TemplatePickerDialog(onDismiss: () -> Unit, onPick: (DocKind, String, String, String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aus Vorlage")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                val groups = listOf(
                    Triple(DocKind.DOC, tr("Dokumente"), DOC_STARTERS.map { Triple(it.first, it.second, null as String?) } +
                        listOf(Triple(tr("Leeres TUD-Dokument"), "# ${DocKind.DOC.newTitle}\n\n", DocStyle.TUD.name)) + TUD_STARTERS.map { Triple("TUD: " + it.first, it.second, DocStyle.TUD.name) }),
                    Triple(DocKind.SLIDES, tr("Präsentationen"), SLIDE_STARTERS.map { Triple(it.first, it.second, null) }),
                    Triple(DocKind.SHEET, tr("Tabellen"), SHEET_STARTERS.map { Triple(it.first, it.second, null) }),
                    Triple(DocKind.MARKDOWN, "Markdown", MARKDOWN_STARTERS.map { Triple(it.first, it.second, null) }),
                )
                groups.forEach { (kind, label, list) ->
                    item { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                    items(list) { (title, md, style) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(kind, title.removePrefix("TUD: "), md, style) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(kindIcon(kind), null, Modifier.size(18.dp), tint = kindColor(kind))
                            Spacer(Modifier.width(10.dp))
                            Text(title)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

@Composable
private fun SearchResults(c: AppContainer, hits: List<FileHit>?, query: String, onOpen: (FileHit) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LORA_BUTTON_ROOM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            hits == null -> item { HintText(tr("Suche …"), Modifier.padding(16.dp)) }
            hits.isEmpty() -> item { HintText(tr("Nichts gefunden zu „{0}“.", query), Modifier.padding(16.dp)) }
            else -> items(hits, key = { it.item.key }) { h ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(h) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = h.item.kind.color().copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(h.item.kind.icon(), null, tint = h.item.kind.color()) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HintText(
                            listOfNotNull(h.item.where + (h.item.folder.takeIf { it.isNotEmpty() }?.let { " › $it" } ?: ""), h.page?.let { tr("Seite {0}", it) }, WHEN.format(Date(h.item.modified)))
                                .joinToString(" · "),
                        )
                        if (h.snippet.isNotBlank()) Text("„${h.snippet}“", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashList(drive: LoraDrive, trash: List<TrashEntry>) {
    var confirmEmpty by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LORA_BUTTON_ROOM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HintText(tr("Gelöschtes bleibt {0} Tage hier und lässt sich wiederherstellen.", LoraDrive.TRASH_DAYS), Modifier.weight(1f))
                if (trash.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(tr("Papierkorb leeren")) }
            }
        }
        if (trash.isEmpty()) item { HintText(tr("Der Papierkorb ist leer."), Modifier.padding(16.dp)) }
        items(trash.sortedByDescending { it.deletedAt }, key = { it.id }) { e ->
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (e.dir) Icons.Outlined.Folder else FileKind.ofName(e.name).icon(), null, tint = if (e.dir) Color(0xFF5F6B7A) else FileKind.ofName(e.name).color())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HintText(tr("Gelöscht ") + WHEN.format(Date(e.deletedAt)) + tr(" · aus ") + (File(e.from).parentFile?.name ?: ""))
                }
                IconButton(onClick = { drive.restore(e)?.let { Notices.info(tr("„{0}“ wiederhergestellt", e.name)) } }) { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = tr("Wiederherstellen")) }
                IconButton(onClick = { drive.deleteForever(e) }) { Icon(Icons.Outlined.DeleteForever, contentDescription = tr("Endgültig löschen")) }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text(tr("Papierkorb leeren?")) },
        text = { Text(tr("{0} Einträge werden endgültig gelöscht.", trash.size)) },
        confirmButton = { DangerButton(onClick = { confirmEmpty = false; drive.emptyTrash() }) { Text(tr("Endgültig löschen")) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(tr("Abbrechen")) } },
    )
}

/** „Orte“: storage locations of the editor, project folders, the knowledge collection – linked, not moved. */
@Composable
private fun PlacesList(c: AppContainer, onOpen: (File) -> Unit) {
    val places by produceState(emptyList<Pair<String, File>>()) {
        value = withContext(Dispatchers.IO) { runCatching { c.fileLibrary.roots().filter { (_, d) -> d.absoluteFile != c.drive.root.absoluteFile } }.getOrDefault(emptyList()) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LORA_BUTTON_ROOM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { HintText(tr("Ordner außerhalb des Lora-Ordners, die Lora kennt: Ablageorte und Projektordner. Ihre Dateien lassen sich hier öffnen, umbenennen, verschieben und löschen."), Modifier.padding(8.dp)) }
        if (places.isEmpty()) item { HintText(tr("Noch keine – Ablageorte unter Einstellungen › Speicherorte, Projektordner am Projekt."), Modifier.padding(8.dp)) }
        items(places) { (name, dir) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(dir) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Storage, null, tint = Color(0xFF5F6B7A))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HintText(dir.absolutePath)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(section: DriveSection) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(section.icon(), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Text(
            when (section) {
                DriveSection.STARRED -> tr("Noch nichts markiert")
                DriveSection.UPLOADS -> tr("Noch nichts hochgeladen")
                DriveSection.CREATED -> tr("Noch nichts im Chat erstellt")
                DriveSection.CHAT -> tr("Dieser Chat hat keine Dateien")
                DriveSection.RECENT -> tr("Noch keine Dateien")
                else -> tr("Dieser Ordner ist leer")
            },
            style = MaterialTheme.typography.titleSmall,
        )
        HintText(
            when (section) {
                DriveSection.UPLOADS -> tr("Jede Datei, die du in einen Chat ziehst oder anhängst, landet hier als Kopie.")
                DriveSection.CREATED -> tr("Was die KI im Chat erstellt – Dokumente, PDFs, Tabellen –, landet hier.")
                DriveSection.STARRED -> tr("Über ⋮ › Markieren erscheinen Dateien und Ordner hier.")
                else -> tr("Mit dem großen „+“ unten rechts legst du Ordner und Dokumente an oder lädst Dateien hoch.")
            },
            Modifier.padding(top = 4.dp),
        )
    }
}
