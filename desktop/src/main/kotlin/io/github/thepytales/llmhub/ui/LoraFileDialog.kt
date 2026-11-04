package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.LlmHubApp
import io.github.thepytales.llmhub.data.NextcloudService
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Os
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Lora-Dateidialog (AP-42): Öffnen, Speichern unter und Ordnerwahl im Stil der App statt der alten AWT-/Swing-Dialoge.
 * The callers of DesktopActions.pickFiles/pickFolder/pickSaveFile wait on a background thread ([show]); the dialog
 * itself is a window of the app ([LoraFileDialogHost], set up by Main.kt), so it also lies over the browser panel.
 */
object LoraFileDialog {
    enum class Mode { OPEN, SAVE, FOLDER }

    class Request(
        val mode: Mode,
        val title: String,
        val multiple: Boolean = false,
        val extensions: List<String> = emptyList(),
        val suggested: String = "",
        val start: File? = null,
        /** The place last used for the same purpose is remembered under this key. */
        val purpose: String = mode.name,
    ) {
        internal val result = CompletableDeferred<List<File>?>()
    }

    val request = MutableStateFlow<Request?>(null)

    /** True while a window shows the host (the main window, or the start window of Main.kt). */
    @Volatile
    var hostReady = false

    /**
     * Shows the dialog and waits for the choice: the files (OPEN), the target file (SAVE) or the folder (FOLDER);
     * empty or null when cancelled. Null also when no window can show it (then the caller falls back).
     */
    fun show(r: Request): List<File>? {
        if (!hostReady || javax.swing.SwingUtilities.isEventDispatchThread()) return null
        // One dialog at a time: a second request waits for the first.
        synchronized(this) {
            request.value = r
            return try {
                runBlocking { r.result.await() } ?: emptyList()
            } finally {
                if (request.value === r) request.value = null
            }
        }
    }

    /**
     * False for the start window that asks for a missing Lora-Ordner: then nothing of the app may be touched (its
     * settings live in the Lora-Ordner, and loading them would create the missing folder anew).
     */
    @Volatile
    var appLoaded = true

    private val prefsLazy by lazy { LlmHubApp.context.getSharedPreferences("file_dialog", 0) }
    private val prefs get() = if (appLoaded) prefsLazy else error("not loaded")

    internal fun lastPlace(purpose: String): File? = runCatching { prefs.getString("place.$purpose", null) }.getOrNull()
        ?.let(::File)?.takeIf { it.isDirectory }

    internal fun rememberPlace(purpose: String, dir: File) {
        runCatching { prefs.edit().putString("place.$purpose", dir.absolutePath).apply() }
    }

    internal var grid: Boolean
        get() = runCatching { prefs.getBoolean("grid", false) }.getOrDefault(false)
        set(v) { runCatching { prefs.edit().putBoolean("grid", v).apply() } }

    /** Files chosen in the dialog lately (the „Zuletzt“ list also shows them, not only the Lora-Ordner). */
    internal fun recentPicks(): List<File> = runCatching { prefs.getString("recent", "").orEmpty() }.getOrDefault("")
        .split('\n').filter { it.isNotBlank() }.map(::File).filter { it.exists() }

    internal fun notePicks(files: List<File>) {
        val list = (files.map { it.absolutePath } + recentPicks().map { it.absolutePath }).distinct().take(30)
        runCatching { prefs.edit().putString("recent", list.joinToString("\n")).apply() }
    }
}

/** Places in the side column of the dialog. */
internal sealed class DialogPlace(val label: String, val icon: ImageVector) {
    class Folder(label: String, icon: ImageVector, val dir: File) : DialogPlace(label, icon)
    object Recent : DialogPlace(trLater("Zuletzt"), Icons.Outlined.Schedule)
    object Starred : DialogPlace(trLater("Markiert"), Icons.Outlined.Star)
    object Cloud : DialogPlace("Nextcloud", Icons.Outlined.Cloud)
}

/** One entry of the list: a local file or folder, or one in the Nextcloud ([dav] path). */
internal data class DialogEntry(val name: String, val dir: Boolean, val size: Long, val modified: Long, val file: File? = null, val dav: String? = null) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

/** File types the filter offers. */
private val TYPE_FILTERS: List<Pair<String, List<String>>> = listOf(
    trLater("PDF") to listOf("pdf"),
    trLater("Dokumente") to listOf("docx", "doc", "odt", "rtf", "txt", "md", "html"),
    trLater("Tabellen") to listOf("xlsx", "xls", "ods", "csv"),
    trLater("Präsentationen") to listOf("pptx", "ppt", "odp"),
    trLater("Bilder") to listOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg"),
    trLater("Audio und Video") to listOf("mp3", "m4a", "wav", "ogg", "opus", "flac", "aac", "mp4", "mkv", "mov", "avi", "webm", "wmv", "m4v"),
)

/** Shows the dialog whenever a request waits; placed into the main window by Main.kt. */
@Composable
fun LoraFileDialogHost(c: AppContainer?) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        LoraFileDialog.hostReady = true
        onDispose { LoraFileDialog.hostReady = false }
    }
    val request by LoraFileDialog.request.collectAsState()
    val r = request ?: return
    val state = rememberDialogState(size = DpSize(940.dp, 620.dp))
    DialogWindow(
        onCloseRequest = { r.result.complete(null) },
        state = state,
        title = tr(r.title),
        icon = androidx.compose.ui.res.painterResource("icon.png"),
    ) {
        window.minimumSize = java.awt.Dimension(620, 420)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            LoraFileBrowser(c, r, onDone = { files -> r.result.complete(files) })
            OverwriteHost()
        }
    }
}

/** Icon of a file type. */
internal fun fileIcon(e: DialogEntry): ImageVector = when {
    e.dir -> Icons.Outlined.Folder
    e.ext == "pdf" -> Icons.Outlined.PictureAsPdf
    e.ext in setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg") -> Icons.Outlined.Image
    e.ext in setOf("xlsx", "xls", "ods", "csv") -> Icons.Outlined.TableChart
    e.ext in setOf("mp3", "m4a", "wav", "ogg", "opus", "flac", "aac") -> Icons.Outlined.MusicNote
    e.ext in setOf("mp4", "mkv", "mov", "avi", "webm", "wmv", "m4v") -> Icons.Outlined.Movie
    e.ext in setOf("docx", "doc", "odt", "rtf", "txt", "md", "pptx", "ppt", "odp") -> Icons.Outlined.Description
    else -> Icons.Outlined.InsertDriveFile
}

/** The places of the side column: Lora's own first, then this PC. */
internal fun dialogPlaces(c: AppContainer?, mode: LoraFileDialog.Mode): List<Pair<String, List<DialogPlace>>> {
    val home = File(System.getProperty("user.home"))
    val lora = buildList {
        if (mode == LoraFileDialog.Mode.OPEN) {
            add(DialogPlace.Recent)
            if (c != null) add(DialogPlace.Starred)
        }
        if (c != null) {
            val drive = runCatching { c.drive }.getOrNull()
            if (drive != null) {
                add(DialogPlace.Folder(trLater("Lora-Ordner"), Icons.Outlined.FolderOpen, drive.root))
                add(DialogPlace.Folder(trLater("Hochgeladen"), Icons.Outlined.CloudUpload, File(drive.root, io.github.thepytales.llmhub.data.LoraDrive.UPLOADS)))
                add(DialogPlace.Folder(trLater("Erstellt"), Icons.Outlined.AutoAwesome, File(drive.root, io.github.thepytales.llmhub.data.LoraDrive.CREATED)))
            }
            if (mode != LoraFileDialog.Mode.FOLDER && runCatching { c.nextcloud.configured }.getOrDefault(false)) add(DialogPlace.Cloud)
            runCatching { c.fileLibrary.roots() }.getOrDefault(emptyList())
                .filter { (_, d) -> drive == null || d.absoluteFile != drive.root.absoluteFile }
                .forEach { (name, d) -> add(DialogPlace.Folder(name, Icons.Outlined.Work, d)) }
        }
    }
    val pc = buildList {
        fun folder(label: String, icon: ImageVector, f: File?) { if (f != null && f.isDirectory) add(DialogPlace.Folder(label, icon, f)) }
        folder(trLater("Persönlicher Ordner"), Icons.Outlined.Home, home)
        folder(trLater("Desktop"), Icons.Outlined.Computer, File(home, "Desktop").takeIf { it.isDirectory } ?: File(home, "Schreibtisch"))
        folder(trLater("Dokumente"), Icons.Outlined.Description, io.github.thepytales.llmhub.desktop.Paths.documents)
        folder(trLater("Downloads"), Icons.Outlined.Download, File(home, "Downloads"))
        folder(trLater("Bilder"), Icons.Outlined.Image, File(home, "Pictures").takeIf { it.isDirectory } ?: File(home, "Bilder"))
        // OneDrive (also „OneDrive - TU Dresden“), Google Drive, Nextcloud-Client, Dropbox: cloud folders of the PC.
        val clouds = (listOfNotNull(System.getenv("OneDrive"), System.getenv("OneDriveCommercial")).map(::File) +
            (home.listFiles()?.filter { it.isDirectory && Regex("^(OneDrive|Nextcloud|Dropbox|Google Drive|iCloud Drive|Sync)").containsMatchIn(it.name) }.orEmpty()))
            .distinctBy { it.absolutePath }
        clouds.forEach { folder(it.name, Icons.Outlined.Cloud, it) }
        // Drives (C:, D:, network drives) or / on macOS and Linux, plus mounted volumes.
        File.listRoots().orEmpty().forEach { r -> folder(if (Os.windows) r.path.removeSuffix("\\") else tr("Computer"), Icons.Outlined.Storage, r) }
        if (Os.mac) File("/Volumes").listFiles()?.filter { it.isDirectory }?.forEach { folder(it.name, Icons.Outlined.Storage, it) }
        if (Os.linux) (File("/media/${System.getProperty("user.name")}").listFiles().orEmpty().toList() + File("/mnt").listFiles().orEmpty().toList())
            .filter { it.isDirectory }.forEach { folder(it.name, Icons.Outlined.Storage, it) }
    }
    return listOf(trLater("Lora") to lora, trLater("Dieser PC") to pc).filter { it.second.isNotEmpty() }
}

/** A small preview picture of pictures (and the first page of PDFs), made in the background and kept a while. */
private object DialogThumbs {
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 120
    }

    suspend fun of(f: File): ImageBitmap? {
        val key = f.absolutePath + ":" + f.lastModified()
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val img = withContext(Dispatchers.IO) {
            runCatching {
                when (f.extension.lowercase()) {
                    "png", "jpg", "jpeg", "gif", "bmp", "webp" -> if (f.length() > 25_000_000) null else {
                        val src = org.jetbrains.skia.Image.makeFromEncoded(f.readBytes())
                        val w = 160
                        val h = (src.height * w / src.width.coerceAtLeast(1)).coerceIn(1, 240)
                        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
                        surface.canvas.drawImageRect(src, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
                        surface.makeImageSnapshot().toComposeImageBitmap()
                    }
                    "pdf" -> if (f.length() > 80_000_000) null else org.apache.pdfbox.Loader.loadPDF(f).use { doc ->
                        org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0, 0.3f).toComposeImageBitmap()
                    }
                    else -> null
                }
            }.getOrNull()
        }
        synchronized(cache) { cache[key] = img }
        return img
    }
}

private fun sizeText(bytes: Long): String = NextcloudService.size(bytes)

/** The browser inside the dialog (also used by the start window that asks for the Lora-Ordner). */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun LoraFileBrowser(c: AppContainer?, r: LoraFileDialog.Request, onDone: (List<File>?) -> Unit) {
    val scope = rememberCoroutineScope()
    val groups = remember { dialogPlaces(c, r.mode) }
    val firstFolder = groups.flatMap { it.second }.filterIsInstance<DialogPlace.Folder>().firstOrNull()
    val startDir = r.start?.let { if (it.isDirectory) it else it.parentFile }?.takeIf { it.isDirectory }
        ?: LoraFileDialog.lastPlace(r.purpose)
        ?: firstFolder?.dir
        ?: File(System.getProperty("user.home"))
    var place by remember { mutableStateOf<DialogPlace?>(if (r.start == null && r.mode == LoraFileDialog.Mode.OPEN && LoraFileDialog.lastPlace(r.purpose) == null) DialogPlace.Recent else null) }
    var dir by remember { mutableStateOf(startDir) }
    var davPath by remember { mutableStateOf(c?.let { runCatching { it.nextcloud.resultFolder }.getOrNull() } ?: "/") }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(if (r.extensions.isNotEmpty()) -1 else -2) } // -1: the caller's types, -2: all, else TYPE_FILTERS
    var grid by remember { mutableStateOf(LoraFileDialog.grid) }
    var saveName by remember { mutableStateOf(r.suggested) }
    var reload by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    // AP-42: rename (F2, right click) and delete (Entf) right in the dialog.
    var renaming by remember { mutableStateOf<DialogEntry?>(null) }
    var deleting by remember { mutableStateOf<List<DialogEntry>?>(null) }
    var pathEdit by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateMapOf<String, DialogEntry>() }
    var cursor by remember { mutableStateOf(0) }
    var typed by remember { mutableStateOf("" to 0L) }
    val listFocus = remember { FocusRequester() }

    val entries by produceState(emptyList<DialogEntry>(), place, dir, davPath, reload) {
        loading = true
        error = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (val p = place) {
                    DialogPlace.Recent -> {
                        val lora = c?.let { runCatching { it.drive.allFiles(3000) }.getOrDefault(emptyList()) }.orEmpty()
                        (LoraFileDialog.recentPicks() + lora.sortedByDescending { it.lastModified() }.take(80))
                            .distinctBy { it.absolutePath }.filter { it.isFile }.take(80)
                            .map { DialogEntry(it.name, false, it.length(), it.lastModified(), it) }
                    }
                    DialogPlace.Starred -> c?.drive?.let { d -> d.allFiles(3000).filter { d.starred(it) } }.orEmpty()
                        .map { DialogEntry(it.name, it.isDirectory, it.length(), it.lastModified(), it) }
                    DialogPlace.Cloud -> c!!.nextcloud.list(davPath).map { DialogEntry(it.name, it.folder, it.size, it.modified, dav = it.path) }
                    else -> dir.listFiles().orEmpty()
                        .filter { !it.isHidden && !it.name.startsWith(".") && !it.name.startsWith("~$") }
                        .map { DialogEntry(it.name, it.isDirectory, if (it.isFile) it.length() else 0, it.lastModified(), it) }
                }
            }.onFailure { error = it.message ?: it.javaClass.simpleName }.getOrDefault(emptyList())
        }
        loading = false
        selected.clear()
        cursor = 0
    }

    val wanted: List<String>? = when {
        typeFilter == -1 -> r.extensions.map { it.lowercase() }
        typeFilter >= 0 -> TYPE_FILTERS[typeFilter].second
        else -> null
    }
    val shown = entries
        .filter { e -> e.dir || r.mode != LoraFileDialog.Mode.FOLDER }
        .filter { e -> e.dir || wanted == null || e.ext in wanted }
        .filter { e -> query.isBlank() || e.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<DialogEntry> { it.dir }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .let { cmp -> if (place == DialogPlace.Recent) compareByDescending { it.modified } else cmp })

    fun goTo(d: File) {
        place = null
        dir = d
        query = ""
    }

    fun parent() {
        when (place) {
            DialogPlace.Cloud -> if (davPath.trimEnd('/').isNotEmpty()) davPath = io.github.thepytales.llmhub.net.WebDavClient.parent(davPath.trimEnd('/')).ifBlank { "/" }
            null -> dir.parentFile?.let { goTo(it) }
            else -> Unit
        }
    }

    /** Local file of an entry; Nextcloud entries are loaded first. */
    suspend fun local(e: DialogEntry): File? = e.file ?: e.dav?.let { path ->
        loading = true
        try { c?.nextcloud?.fetch(path) } catch (t: Throwable) { error = t.message; null } finally { loading = false }
    }

    fun finish(files: List<File>) {
        val folder = when {
            place == null -> dir
            else -> files.firstOrNull()?.let { if (it.isDirectory) it else it.parentFile }
        }
        folder?.takeIf { it.isDirectory && place != DialogPlace.Cloud }?.let { LoraFileDialog.rememberPlace(r.purpose, it) }
        if (r.mode == LoraFileDialog.Mode.OPEN) LoraFileDialog.notePicks(files)
        onDone(files)
    }

    fun confirm() {
        scope.launch {
            when (r.mode) {
                LoraFileDialog.Mode.OPEN -> {
                    val picked = selected.values.filter { !it.dir }.ifEmpty { shown.getOrNull(cursor)?.takeIf { !it.dir }?.let(::listOf).orEmpty() }
                    if (picked.isEmpty()) {
                        shown.getOrNull(cursor)?.takeIf { it.dir }?.let { e -> if (e.dav != null) davPath = e.dav else e.file?.let(::goTo) }
                        return@launch
                    }
                    val files = picked.mapNotNull { local(it) }
                    if (files.isNotEmpty()) finish(if (r.multiple) files else files.take(1))
                }
                LoraFileDialog.Mode.FOLDER -> {
                    val chosen = selected.values.firstOrNull { it.dir }?.file ?: shown.getOrNull(cursor)?.takeIf { it.dir && selected.isNotEmpty() }?.file ?: dir
                    if (place == null || chosen != dir) finish(listOf(chosen))
                }
                LoraFileDialog.Mode.SAVE -> {
                    val name = saveName.trim().let { n -> r.extensions.firstOrNull()?.let { ext -> if (n.isEmpty() || n.lowercase().endsWith(".${ext.lowercase()}")) n else "$n.$ext" } ?: n }
                    if (name.isEmpty()) { error = tr("Bitte einen Dateinamen eingeben."); return@launch }
                    if (place == DialogPlace.Cloud) {
                        // Saved into a temporary file first; once the app has written it, it goes up into the Nextcloud.
                        val tmp = File(System.getProperty("java.io.tmpdir"), "lora-nextcloud-${System.nanoTime()}").apply { mkdirs() }
                        val target = File(tmp, io.github.thepytales.llmhub.files.FileText.safeName(name))
                        uploadWhenWritten(c, target, davPath)
                        onDone(listOf(target))
                        return@launch
                    }
                    val target = File(dir, name)
                    if (target.exists() && !confirmOverwrite(target)) return@launch
                    finish(listOf(target))
                }
            }
        }
    }

    fun activate(e: DialogEntry) {
        if (e.dir) {
            if (e.dav != null) davPath = e.dav else e.file?.let(::goTo)
            return
        }
        when (r.mode) {
            LoraFileDialog.Mode.SAVE -> { saveName = e.name; confirm() }
            LoraFileDialog.Mode.OPEN -> { selected.clear(); selected[e.name + e.dav + e.file] = e; confirm() }
            LoraFileDialog.Mode.FOLDER -> Unit
        }
    }

    fun keyOf(e: DialogEntry) = e.name + e.dav + e.file

    fun toggle(e: DialogEntry, add: Boolean) {
        val k = keyOf(e)
        if (!add || !r.multiple) {
            val had = selected.containsKey(k) && selected.size == 1
            selected.clear()
            if (!had || !add) selected[k] = e
        } else if (selected.containsKey(k)) selected.remove(k) else selected[k] = e
        if (r.mode == LoraFileDialog.Mode.SAVE && !e.dir) saveName = e.name
    }

    LaunchedEffect(Unit) { runCatching { listFocus.requestFocus() } }

    val drop = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val t = event.awtTransferable
                if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                @Suppress("UNCHECKED_CAST")
                val files = runCatching { t.getTransferData(DataFlavor.javaFileListFlavor) as List<File> }.getOrDefault(emptyList())
                if (files.isEmpty()) return false
                when (r.mode) {
                    LoraFileDialog.Mode.OPEN -> files.filter { it.isFile }.takeIf { it.isNotEmpty() }?.let { finish(if (r.multiple) it else it.take(1)) }
                        ?: files.firstOrNull { it.isDirectory }?.let(::goTo)
                    LoraFileDialog.Mode.FOLDER -> files.firstOrNull()?.let { goTo(if (it.isDirectory) it else it.parentFile) }
                    LoraFileDialog.Mode.SAVE -> files.firstOrNull()?.let { f -> goTo(if (f.isDirectory) f else f.parentFile); if (f.isFile) saveName = f.name }
                }
                return true
            }
        }
    }

    Row(
        Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = drop)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val cmd = e.isCtrlPressed || e.isMetaPressed
                when {
                    e.key == Key.Escape -> { onDone(null); true }
                    pathEdit != null || newFolder || renaming != null || deleting != null -> false
                    else -> false
                }
            },
    ) {
        // Side column like the Dateien tab.
        LazyColumn(Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 8.dp)) {
            groups.forEach { (title, places) ->
                item(key = "h_$title") {
                    Text(tr(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                }
                itemsIndexed(places, key = { i, p -> "$title-$i-${p.label}" }) { _, p ->
                    val active = when (p) {
                        is DialogPlace.Folder -> place == null && dir.absoluteFile == p.dir.absoluteFile
                        else -> place === p
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                query = ""
                                when (p) {
                                    is DialogPlace.Folder -> goTo(p.dir)
                                    else -> place = p
                                }
                            }
                            .semantics { this.selected = active; role = Role.Tab }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(p.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(tr(p.label), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Path bar, search and view.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::parent, enabled = place == null || place == DialogPlace.Cloud) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Übergeordneter Ordner"))
                }
                Box(Modifier.weight(1f)) {
                    val edit = pathEdit
                    if (edit != null) {
                        OutlinedTextField(
                            edit, { pathEdit = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                val f = File(edit.trim())
                                when {
                                    f.isDirectory -> goTo(f)
                                    f.isFile && r.mode == LoraFileDialog.Mode.OPEN -> finish(listOf(f))
                                    f.parentFile?.isDirectory == true && r.mode == LoraFileDialog.Mode.SAVE -> { goTo(f.parentFile); saveName = f.name }
                                    else -> error = tr("Diesen Ordner gibt es nicht.")
                                }
                                pathEdit = null
                            }),
                            label = { Text(tr("Pfad")) },
                        )
                    } else {
                        Breadcrumb(place, dir, davPath, onDir = ::goTo, onDav = { davPath = it }, onEdit = { pathEdit = dir.absolutePath })
                    }
                }
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.width(200.dp).padding(start = 8.dp),
                    placeholder = { Text(tr("Suchen")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                )
                var typeMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { typeMenu = true }) {
                        Text(
                            when {
                                typeFilter == -1 -> r.extensions.take(4).joinToString(", ") { it.uppercase() } + if (r.extensions.size > 4) " …" else ""
                                typeFilter >= 0 -> tr(TYPE_FILTERS[typeFilter].first)
                                else -> tr("Alle Dateien")
                            },
                            maxLines = 1,
                        )
                    }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        if (r.extensions.isNotEmpty()) DropdownMenuItem(text = { Text(tr("Passende Dateien")) }, onClick = { typeFilter = -1; typeMenu = false })
                        DropdownMenuItem(text = { Text(tr("Alle Dateien")) }, onClick = { typeFilter = -2; typeMenu = false })
                        TYPE_FILTERS.forEachIndexed { i, (label, _) -> DropdownMenuItem(text = { Text(tr(label)) }, onClick = { typeFilter = i; typeMenu = false }) }
                    }
                }
                IconButton(onClick = { grid = !grid; LoraFileDialog.grid = grid }) {
                    Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, contentDescription = if (grid) tr("Als Liste") else tr("Als Raster"))
                }
                if (place == null && r.mode != LoraFileDialog.Mode.OPEN || place == null && dir.canWrite()) {
                    IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = tr("Neuer Ordner")) }
                }
                val focusedLocal = (selected.values.singleOrNull() ?: shown.getOrNull(cursor))?.takeIf { it.file != null && place != DialogPlace.Cloud }
                IconButton(onClick = { renaming = focusedLocal }, enabled = focusedLocal != null) {
                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = tr("Umbenennen (F2)"))
                }
                IconButton(onClick = {
                    deleting = selected.values.filter { it.file != null }.ifEmpty { listOfNotNull(focusedLocal) }
                }, enabled = focusedLocal != null || selected.values.any { it.file != null }) {
                    Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen (Entf)"))
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

            val listKeys = Modifier.focusRequester(listFocus).focusable().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || pathEdit != null) return@onPreviewKeyEvent false
                val cmd = e.isCtrlPressed || e.isMetaPressed
                val step = if (grid) 5 else 1
                when {
                    e.key == Key.DirectionDown -> { cursor = (cursor + step).coerceAtMost(shown.lastIndex.coerceAtLeast(0)); true }
                    e.key == Key.DirectionUp -> { cursor = (cursor - step).coerceAtLeast(0); true }
                    grid && e.key == Key.DirectionRight -> { cursor = (cursor + 1).coerceAtMost(shown.lastIndex.coerceAtLeast(0)); true }
                    grid && e.key == Key.DirectionLeft -> { cursor = (cursor - 1).coerceAtLeast(0); true }
                    e.key == Key.Spacebar -> { shown.getOrNull(cursor)?.let { toggle(it, true) }; true }
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> { shown.getOrNull(cursor)?.let(::activate) ?: confirm(); true }
                    e.key == Key.Backspace -> { parent(); true }
                    e.key == Key.F2 -> { shown.getOrNull(cursor)?.takeIf { it.file != null }?.let { renaming = it }; true }
                    e.key == Key.Delete -> {
                        val list = selected.values.filter { it.file != null }.ifEmpty { listOfNotNull(shown.getOrNull(cursor)?.takeIf { it.file != null }) }
                        if (list.isNotEmpty()) deleting = list
                        true
                    }
                    cmd && e.key == Key.A && r.multiple -> { shown.filter { !it.dir }.forEach { selected[keyOf(it)] = it }; true }
                    !cmd && e.utf16CodePoint in 33..0xFFFF && Character.isLetterOrDigit(e.utf16CodePoint) -> {
                        // Typing jumps to the first entry with that beginning.
                        val now = System.currentTimeMillis()
                        val prefix = (if (now - typed.second < 900) typed.first else "") + e.utf16CodePoint.toChar()
                        typed = prefix to now
                        shown.indexOfFirst { it.name.startsWith(prefix, ignoreCase = true) }.takeIf { it >= 0 }?.let { cursor = it }
                        true
                    }
                    else -> false
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().then(listKeys)) {
                if (!loading && shown.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
                        HintText(if (query.isNotBlank()) tr("Nichts gefunden.") else tr("Dieser Ordner ist leer"))
                    }
                }
                if (grid) {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(cursor) { if (shown.isNotEmpty()) gridState.animateScrollToItem(cursor.coerceIn(0, shown.lastIndex)) }
                    LazyVerticalGrid(GridCells.Adaptive(132.dp), Modifier.fillMaxSize(), state = gridState, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        itemsIndexed(shown, key = { _, e -> keyOf(e) }) { i, e ->
                            EntryTile(e, selected.containsKey(keyOf(e)), i == cursor, grid = true,
                                onClick = { add -> cursor = i; toggle(e, add) }, onOpen = { cursor = i; activate(e) },
                                onRename = { cursor = i; renaming = e }, onDelete = { cursor = i; deleting = listOf(e) })
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(cursor) { if (shown.isNotEmpty()) listState.animateScrollToItem(cursor.coerceIn(0, shown.lastIndex)) }
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        itemsIndexed(shown, key = { _, e -> keyOf(e) }) { i, e ->
                            EntryTile(e, selected.containsKey(keyOf(e)), i == cursor, grid = false,
                                onClick = { add -> cursor = i; toggle(e, add) }, onOpen = { cursor = i; activate(e) },
                                onRename = { cursor = i; renaming = e }, onDelete = { cursor = i; deleting = listOf(e) })
                        }
                    }
                }
            }
            HorizontalDivider()
            // Bottom: file name (Speichern unter), what is chosen, buttons.
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                when (r.mode) {
                    LoraFileDialog.Mode.SAVE -> OutlinedTextField(
                        saveName, { saveName = it }, singleLine = true, label = { Text(tr("Dateiname")) }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                    )
                    LoraFileDialog.Mode.FOLDER -> Text(
                        (selected.values.firstOrNull { it.dir }?.file ?: dir).absolutePath,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    LoraFileDialog.Mode.OPEN -> Text(
                        when (selected.size) {
                            0 -> if (r.multiple) tr("Dateien wählen – mit Strg mehrere") else tr("Datei wählen")
                            1 -> selected.values.first().name
                            else -> tr("{0} ausgewählt", selected.size)
                        },
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { onDone(null) }) { Text(tr("Abbrechen")) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = ::confirm,
                    enabled = when (r.mode) {
                        LoraFileDialog.Mode.OPEN -> selected.values.any { !it.dir } || shown.getOrNull(cursor) != null
                        LoraFileDialog.Mode.SAVE -> (place == null || place == DialogPlace.Cloud) && saveName.isNotBlank()
                        LoraFileDialog.Mode.FOLDER -> place == null
                    },
                ) {
                    Text(
                        when (r.mode) {
                            LoraFileDialog.Mode.OPEN -> tr("Öffnen")
                            LoraFileDialog.Mode.SAVE -> tr("Speichern")
                            LoraFileDialog.Mode.FOLDER -> tr("Ordner wählen")
                        },
                    )
                }
            }
        }
    }

    renaming?.let { e ->
        val f = e.file ?: return@let
        var name by remember(e) { mutableStateOf(e.name) }
        val problem = renameProblem(f, name)
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr(if (e.dir) "Ordner umbenennen" else "Datei umbenennen")) },
            text = {
                OutlinedTextField(
                    name, { name = it }, singleLine = true, label = { Text(tr("Neuer Name")) }, isError = problem != null && name != e.name,
                    supportingText = { if (problem != null && name != e.name) Text(problem) },
                )
            },
            confirmButton = {
                TextButton(enabled = problem == null, onClick = {
                    val done = runCatching { renameEntry(c, f, name.trim()) }
                    renaming = null
                    done.onSuccess { reload++ }.onFailure { error = tr("Umbenennen ging nicht: {0}", it.message.orEmpty()) }
                }) { Text(tr("Umbenennen")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Abbrechen")) } },
        )
    }
    deleting?.let { list ->
        val files = list.mapNotNull { it.file }
        val inLora = c != null && files.all { c.drive.inside(it) }
        val bin = !inLora && java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(if (files.size == 1) tr("„{0}“ löschen?", files[0].name) else tr("{0} Einträge löschen?", files.size)) },
            text = {
                Text(
                    when {
                        inLora -> tr("Kommt in den Papierkorb von Lora und lässt sich 30 Tage lang wiederherstellen.")
                        bin -> tr("Kommt in den Papierkorb von Windows.")
                        else -> tr("Wird endgültig gelöscht – das lässt sich nicht rückgängig machen.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = runCatching { deleteEntries(c, files, inLora, bin) }
                    deleting = null
                    selected.clear()
                    ok.onSuccess { reload++ }.onFailure { error = tr("Löschen ging nicht: {0}", it.message.orEmpty()) }
                }) { Text(tr("Löschen")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Abbrechen")) } },
        )
    }
    if (newFolder) {
        var name by remember { mutableStateOf(tr("Neuer Ordner")) }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(tr("Neuer Ordner")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name")) }) },
            confirmButton = {
                TextButton(onClick = {
                    val made = runCatching {
                        val f = io.github.thepytales.llmhub.data.LoraDrive.unique(dir, io.github.thepytales.llmhub.files.FileText.safeName(name.trim().ifBlank { "Neuer Ordner" }), isDir = true)
                        f.mkdirs()
                        f
                    }.getOrNull()
                    newFolder = false
                    if (made != null) { if (r.mode == LoraFileDialog.Mode.OPEN) reload++ else goTo(made) } else error = tr("Der Ordner ließ sich hier nicht anlegen.")
                }) { Text(tr("Anlegen")) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(tr("Abbrechen")) } },
        )
    }
}

/** Asks before an existing file is replaced (Speichern unter). */
private suspend fun confirmOverwrite(target: File): Boolean = withContext(Dispatchers.IO) {
    // The question runs in the dialog's own window through a small state: simpler is the standard confirmation of the app.
    OverwriteQuestion.ask(target)
}

/** „Ersetzen?“ for Speichern unter, shown by [OverwriteHost] inside the dialog. */
internal object OverwriteQuestion {
    val pending = MutableStateFlow<Pair<File, CompletableDeferred<Boolean>>?>(null)

    suspend fun ask(target: File): Boolean {
        val d = CompletableDeferred<Boolean>()
        pending.value = target to d
        return try { d.await() } finally { pending.value = null }
    }
}

@Composable
internal fun OverwriteHost() {
    val p by OverwriteQuestion.pending.collectAsState()
    val (file, answer) = p ?: return
    AlertDialog(
        onDismissRequest = { answer.complete(false) },
        title = { Text(tr("Datei ersetzen?")) },
        text = { Text(tr("„{0}“ gibt es dort schon. Soll sie ersetzt werden?", file.name)) },
        confirmButton = { TextButton(onClick = { answer.complete(true) }) { Text(tr("Ersetzen")) } },
        dismissButton = { TextButton(onClick = { answer.complete(false) }) { Text(tr("Abbrechen")) } },
    )
}

/** Speichern unter in die Nextcloud: waits until the app has written [file], then uploads it into [folder]. */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun uploadWhenWritten(c: AppContainer?, file: File, folder: String) {
    c ?: return
    GlobalScope.launch(Dispatchers.IO) {
        var lastSize = -1L
        var stable = 0
        repeat(600) {
            delay(1000)
            val size = if (file.isFile) file.length() else -1L
            if (size > 0 && size == lastSize) stable++ else stable = 0
            lastSize = size
            if (stable >= 2) {
                runCatching { c.nextcloud.save(file, folder) }
                    .onSuccess { Notices.info(tr("In der Nextcloud gespeichert: {0}", file.name)) }
                    .onFailure { e -> AppLog.e("Nextcloud speichern", e); Notices.error(tr("Hochladen in die Nextcloud fehlgeschlagen: {0}", e.message.orEmpty())) }
                runCatching { file.delete(); file.parentFile?.delete() }
                return@launch
            }
        }
    }
}

@Composable
private fun Breadcrumb(place: DialogPlace?, dir: File, davPath: String, onDir: (File) -> Unit, onDav: (String) -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun crumb(label: String, onClick: () -> Unit) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp))
        }
        when (place) {
            DialogPlace.Recent, DialogPlace.Starred -> Text(tr(place.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(6.dp))
            DialogPlace.Cloud -> {
                crumb("Nextcloud") { onDav("/") }
                var acc = ""
                davPath.split('/').filter { it.isNotEmpty() }.forEach { part ->
                    acc += "/$part"
                    val target = acc
                    Text("›", color = MaterialTheme.colorScheme.outline)
                    crumb(part) { onDav(target) }
                }
            }
            else -> {
                val chain = generateSequence(dir.absoluteFile) { it.parentFile }.toList().reversed()
                chain.forEachIndexed { i, f ->
                    if (i > 0) Text("›", color = MaterialTheme.colorScheme.outline)
                    crumb(f.name.ifEmpty { f.path.removeSuffix(File.separator).ifEmpty { "/" } }) { onDir(f) }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onEdit) { Text(tr("Pfad eingeben")) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun EntryTile(
    e: DialogEntry, chosen: Boolean, focused: Boolean, grid: Boolean, onClick: (add: Boolean) -> Unit, onOpen: () -> Unit,
    onRename: () -> Unit = {}, onDelete: () -> Unit = {},
) {
    // Right click: rename and delete (AP-42), only for files on this PC.
    if (e.file != null) {
        androidx.compose.foundation.ContextMenuArea(items = {
            listOf(
                androidx.compose.foundation.ContextMenuItem(tr("Umbenennen"), onRename),
                androidx.compose.foundation.ContextMenuItem(tr("Löschen"), onDelete),
            )
        }) { EntryTileBody(e, chosen, focused, grid, onClick, onOpen) }
    } else EntryTileBody(e, chosen, focused, grid, onClick, onOpen)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun EntryTileBody(e: DialogEntry, chosen: Boolean, focused: Boolean, grid: Boolean, onClick: (add: Boolean) -> Unit, onOpen: () -> Unit) {
    val info = androidx.compose.ui.platform.LocalWindowInfo.current
    val bg = when {
        chosen -> MaterialTheme.colorScheme.secondaryContainer
        focused -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val thumb by produceState<ImageBitmap?>(null, e.file) {
        value = e.file?.takeIf { !e.dir && (grid || e.ext == "pdf" || e.ext in setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")) && grid }?.let { DialogThumbs.of(it) }
    }
    val base = Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
        .then(if (focused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
        .combinedClickable(onDoubleClick = onOpen, onClick = {
            val mods = info.keyboardModifiers
            onClick(mods.isCtrlPressed || mods.isMetaPressed || mods.isShiftPressed)
        })
        .semantics(mergeDescendants = true) {
            contentDescription = (if (e.dir) tr("Ordner") + " " else "") + e.name
            selected = chosen
        }
    if (grid) {
        Column(base.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val t = thumb
                if (t != null) Image(t, null, Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
                else Icon(fileIcon(e), null, Modifier.size(48.dp), tint = if (e.dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(e.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    } else {
        Row(base.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(fileIcon(e), null, Modifier.size(22.dp), tint = if (e.dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (e.modified > 0) Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.modified)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (e.dir) "" else sizeText(e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
        }
    }
}

/** Why [name] cannot be the new name of [f] (empty, forbidden characters, taken), or null. */
internal fun renameProblem(f: File, name: String): String? {
    val n = name.trim()
    return when {
        n.isEmpty() -> tr("Bitte einen Namen eingeben.")
        n == f.name -> null
        Regex("[\\\\/:*?\"<>|]").containsMatchIn(n) -> tr("Diese Zeichen gehen nicht: \\ / : * ? \" < > |")
        n == "." || n == ".." || n.endsWith(".") || n.endsWith(" ") -> tr("Der Name darf nicht mit Punkt oder Leerzeichen enden.")
        File(f.parentFile, n).exists() && !n.equals(f.name, ignoreCase = true) -> tr("Diesen Namen gibt es hier schon.")
        else -> null
    }
}

/** Renames [f]: in the Lora-Ordner through the drive (tags and stars move along), elsewhere on the disk. */
internal fun renameEntry(c: AppContainer?, f: File, name: String): File {
    if (name == f.name) return f
    renameProblem(f, name)?.let { throw IllegalArgumentException(it) }
    if (c != null && c.drive.inside(f)) return c.drive.rename(f, name).also { c.drive.changed() }
    val target = File(f.parentFile, name)
    if (!f.renameTo(target)) throw java.io.IOException(tr("Die Datei ist vielleicht noch in einem anderen Programm geöffnet."))
    return target
}

/** Deletes [files]: Lora's Papierkorb inside the Lora-Ordner, else the Windows one, else for good (after asking). */
internal fun deleteEntries(c: AppContainer?, files: List<File>, inLora: Boolean, bin: Boolean) {
    when {
        inLora && c != null -> { c.drive.delete(files); c.drive.changed() }
        bin -> files.forEach { if (!java.awt.Desktop.getDesktop().moveToTrash(it)) throw java.io.IOException(it.name) }
        else -> files.forEach { if (!it.deleteRecursively()) throw java.io.IOException(it.name) }
    }
}
