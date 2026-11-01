package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.PdfComment
import io.github.thepytales.llmhub.docs.PdfFieldSpec
import io.github.thepytales.llmhub.docs.PdfFieldType
import io.github.thepytales.llmhub.docs.PdfInfo
import io.github.thepytales.llmhub.docs.PdfMark
import io.github.thepytales.llmhub.docs.PdfMarkKind
import io.github.thepytales.llmhub.docs.PdfNumbering
import io.github.thepytales.llmhub.docs.PdfOutlineEntry
import io.github.thepytales.llmhub.docs.PdfPoint
import io.github.thepytales.llmhub.docs.PdfSearchHit
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.parsePageRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** An open PDF of the toolbox: the original, the working copy and earlier versions for „Rückgängig“. */
class PdfTab(val id: String, val name: String, val original: File?, file: File) {
    var file by mutableStateOf(file)
    var info by mutableStateOf<PdfInfo?>(null)
    val history = mutableStateListOf<File>()
    /** Form fields being edited (AP-11, Formular). */
    val fields = mutableStateListOf<PdfFieldSpec>()
    var fieldsLoaded by mutableStateOf(false)
    /** Page to scroll to (from a link or the outline). */
    var goTo by mutableStateOf<Int?>(null)
}

/** The open PDFs stay open while the app runs (also when the screen is left). */
object PdfWork {
    val tabs = mutableStateListOf<PdfTab>()
    var selected by mutableStateOf(0)

    fun dir(context: android.content.Context) = File(context.cacheDir, "pdf-work").apply { mkdirs() }

    /** Opens [source] in a new tab (a working copy; the original is only changed by „Speichern“). */
    suspend fun open(context: android.content.Context, source: File, name: String = source.name, page: Int = 1, keepOriginal: Boolean = true): PdfTab {
        tabs.firstOrNull { it.original?.absolutePath == source.absolutePath }?.let { t ->
            selected = tabs.indexOf(t)
            t.goTo = page - 1
            return t
        }
        val id = UUID.randomUUID().toString().take(8)
        val work = File(dir(context), "$id-0.pdf")
        withContext(Dispatchers.IO) { source.copyTo(work, overwrite = true) }
        val tab = PdfTab(id, name, source.takeIf { keepOriginal }, work)
        tab.info = withContext(Dispatchers.IO) { runCatching { PdfTools.info(context, work) }.getOrNull() }
        tab.goTo = (page - 1).takeIf { it > 0 }
        tabs += tab
        selected = tabs.lastIndex
        return tab
    }

    fun close(tab: PdfTab) {
        val i = tabs.indexOf(tab)
        tabs.remove(tab)
        (tab.history + tab.file).forEach { f -> PdfRender.close(f); f.delete() }
        selected = (if (selected >= i) selected - 1 else selected).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }
}

private enum class PdfMode(val label: String) {
    VIEW("Ansehen"), PAGES("Seiten"), MARK("Markieren"), SIGN("Unterschreiben"), FORM("Formular"), CONVERT("Umwandeln"), AI("KI"),
}

/** Runs a change of the PDF: the result becomes a new version, the old one stays for „Rückgängig“. */
private suspend fun PdfTab.change(context: android.content.Context, op: (src: File, out: File) -> Unit): Boolean {
    val out = File(PdfWork.dir(context), "$id-${System.nanoTime()}.pdf")
    val ok = withContext(Dispatchers.IO) { runCatching { op(file, out) }.onFailure { out.delete() } }
    ok.exceptionOrNull()?.let { Notices.error(tr("Das hat nicht geklappt") + ": " + (it.message ?: it.javaClass.simpleName)); return false }
    history += file
    file = out
    info = withContext(Dispatchers.IO) { runCatching { PdfTools.info(context, out) }.getOrNull() }
    return true
}

private fun PdfTab.undo(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
    val previous = history.removeLastOrNull() ?: return
    val current = file
    file = previous
    scope.launch {
        info = withContext(Dispatchers.IO) { runCatching { PdfTools.info(context, previous) }.getOrNull() }
        PdfRender.close(current)
        current.delete()
    }
}

/**
 * PDF-Werkzeugkasten Stufe 1 (AP-11), in the style of pdf24 + Xodo: several PDFs in tabs with thumbnails, outline, search
 * and night mode; pages merge, split, extract, delete, rotate, sort (drag the thumbnails) and number; highlight,
 * underline, strike through, freehand, notes and stamps with an exportable list of comments; and on Windows: make a
 * paper form fillable – the fields are found automatically and can be moved, resized and retyped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolScreen(c: AppContainer, onBack: () -> Unit, onOpenChat: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val tab = PdfWork.tabs.getOrNull(PdfWork.selected)
    val pick = rememberMediaImport(anyFile = true) { name, _, input ->
        // AP-15: Word, Excel, PowerPoint, pictures, HTML and text open as a PDF.
        val kind = io.github.thepytales.llmhub.docs.ToPdfKind.of(name)
        if (kind == io.github.thepytales.llmhub.docs.ToPdfKind.UNSUPPORTED) {
            Notices.error("„$name“ " + tr("lässt sich nicht als PDF öffnen (PDF, Word, Excel, PowerPoint, Bilder, HTML und Text gehen)."))
            return@rememberMediaImport
        }
        val tmp = File(PdfWork.dir(context), "import-${System.nanoTime()}." + name.substringAfterLast('.', "pdf"))
        withContext(Dispatchers.IO) { input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } }
        if (kind == io.github.thepytales.llmhub.docs.ToPdfKind.PDF) {
            PdfWork.open(context, tmp, name, keepOriginal = false)
        } else {
            busy = true
            val converted = runCatching { io.github.thepytales.llmhub.docs.PdfConvert.toPdf(context, tmp, name, File(PdfWork.dir(context), "umgewandelt")) }
            busy = false
            converted.onSuccess { r ->
                PdfWork.open(context, r.file, name.substringBeforeLast('.') + ".pdf", keepOriginal = false)
                r.file.delete()
                Notices.info("„$name“ " + tr("in PDF umgewandelt") + if (r.how.isNotEmpty()) " (" + tr(r.how) + ")" else "")
            }.onFailure { Notices.error(it.message ?: tr("Das hat nicht geklappt")) }
        }
        tmp.delete()
    }
    val saveAs = rememberSaveAs("application/pdf")

    // Opened from a link (evidence in the Quellenmodus, a document card).
    val request by PdfHandoff.request.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        request?.let { r ->
            PdfHandoff.request.value = null
            val f = File(r.path)
            if (f.isFile) PdfWork.open(context, f, r.name ?: f.name, r.page) else Notices.error(tr("Die Datei gibt es nicht mehr."))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("PDF-Werkzeugkasten")) },
                actions = {
                    IconButton(onClick = pick) { Icon(Icons.Outlined.FolderOpen, contentDescription = tr("PDF öffnen")) }
                    if (tab != null) {
                        IconButton(onClick = { tab.undo(context, scope) }, enabled = tab.history.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = tr("Rückgängig"))
                        }
                        if (DESKTOP && tab.original?.canWrite() == true) {
                            IconButton(onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { runCatching { tab.file.copyTo(tab.original, overwrite = true) }.isSuccess }
                                    if (ok) Notices.info(tr("Gespeichert") + ": " + tab.original.name) else Notices.error(tr("Speichern fehlgeschlagen – „Speichern unter“ versuchen."))
                                }
                            }) { Icon(Icons.Outlined.Save, contentDescription = tr("Speichern")) }
                        }
                        IconButton(onClick = {
                            val named = File(PdfWork.dir(context), tab.name.removeSuffix(".pdf").removeSuffix(".PDF") + ".pdf")
                            scope.launch {
                                withContext(Dispatchers.IO) { tab.file.copyTo(named, overwrite = true) }
                                saveAs(named)
                            }
                        }) { Icon(Icons.Outlined.IosShare, contentDescription = tr("Speichern unter / Teilen")) }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (PdfWork.tabs.isEmpty()) {
                PdfEmpty(onOpen = pick)
                return@Column
            }
            ScrollableTabRow(selectedTabIndex = PdfWork.selected.coerceIn(0, PdfWork.tabs.lastIndex), edgePadding = 8.dp) {
                PdfWork.tabs.forEachIndexed { i, t ->
                    Tab(selected = i == PdfWork.selected, onClick = { PdfWork.selected = i }, text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                            Spacer(Modifier.size(4.dp))
                            Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"), modifier = Modifier.size(16.dp).clickable { PdfWork.close(t) })
                        }
                    })
                }
            }
            val current = tab ?: return@Column
            var mode by remember { mutableStateOf(PdfMode.VIEW) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Android: viewer, pages and marking; making forms fillable is for the PC (AP-11).
                PdfMode.entries.filter { it != PdfMode.FORM || !IS_ANDROID }.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(tr(m.label)) })
                }
                Spacer(Modifier.size(8.dp))
                current.info?.let { HintText("${it.pages} " + tr(if (it.pages == 1) "Seite" else "Seiten")) }
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            fun run(op: (File, File) -> Unit) {
                busy = true
                scope.launch {
                    current.change(context, op)
                    busy = false
                }
            }
            when (mode) {
                PdfMode.VIEW -> PdfViewer(c, current)
                PdfMode.PAGES -> PdfPages(current, onRun = ::run, onBusy = { busy = it })
                PdfMode.MARK -> PdfMarker(current, onRun = ::run)
                PdfMode.FORM -> PdfFormEditor(current, onRun = ::run, onOpenChat = onOpenChat)
                PdfMode.SIGN -> PdfSigner(c, current, onRun = ::run)
                PdfMode.CONVERT -> PdfConverter(c, current, onRun = ::run, onPickFile = pick)
                PdfMode.AI -> PdfAi(c, current, onConvert = { mode = PdfMode.CONVERT })
            }
        }
    }
}

@Composable
private fun PdfEmpty(onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val director = rememberLoraDirector(sequence = PDF_LORA, start = PDF_LORA.first())
        LoraFigure(Modifier.size(150.dp, 150.dp / LORA_ASPECT), label = tr("Lora mit Schere, Hefter und Stempel"), director = director)
        Spacer(Modifier.size(12.dp))
        Text(tr("PDF-Werkzeugkasten"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(6.dp))
        Text(
            tr("PDFs in Tabs ansehen, zusammenfügen, teilen, Seiten drehen, löschen und sortieren, Seitenzahlen setzen, markieren und kommentieren, unterschreiben") +
                (if (DESKTOP) tr(", Papierformulare ausfüllbar machen") else "") +
                tr(". Scans durchsuchbar machen, umwandeln (Word, Excel, PowerPoint, Bilder ↔ PDF) und mit KI fragen, zusammenfassen, Tabellen herausziehen und Formulare erklären lassen."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(Modifier.size(16.dp))
        Button(onClick = onOpen) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(tr("PDF öffnen"))
        }
    }
}

/** Lora's moves for the PDF tools: cutting, stapling, stamping. */
internal val PDF_LORA = listOf(LoraAction.SCISSORS, LoraAction.STAPLER, LoraAction.STAMP, LoraAction.READ)

private val NIGHT = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

/** A rendered page: the picture at [widthPx], drawn [night] inverted; placeholder while it renders. */
@Composable
private fun PageImage(file: File, index: Int, aspect: Float, widthPx: Int, night: Boolean, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, index, widthPx) {
        value = withContext(Dispatchers.IO) { PdfRender.page(file, index, widthPx) }
    }
    Box(modifier.aspectRatio(aspect).background(if (night) Color(0xFF202020) else Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        bitmap?.let { Image(it, contentDescription = tr("Seite") + " ${index + 1}", modifier = Modifier.fillMaxSize(), colorFilter = if (night) NIGHT else null) }
            ?: CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.Center), strokeWidth = 2.dp)
    }
}

private fun PdfInfo.aspect(i: Int) = sizes.getOrNull(i)?.let { (w, h) -> (w / h).coerceIn(0.2f, 5f) } ?: 0.707f

/** Viewer: thumbnails or outline or search hits beside the pages; zoom and night mode. */
@Composable
private fun PdfViewer(c: AppContainer, tab: PdfTab) {
    val context = LocalContext.current
    val info = tab.info ?: return
    var zoom by remember { mutableStateOf(1f) }
    var night by remember { mutableStateOf(false) }
    var side by remember { mutableStateOf("thumbs") }
    var query by remember { mutableStateOf("") }
    var hits by remember(tab.file) { mutableStateOf<List<PdfSearchHit>>(emptyList()) }
    val outline by produceState<List<PdfOutlineEntry>>(emptyList(), tab.file) { value = withContext(Dispatchers.IO) { runCatching { PdfTools.outline(context, tab.file) }.getOrDefault(emptyList()) } }
    val list = rememberLazyListState()
    LaunchedEffect(tab.goTo) { tab.goTo?.let { list.scrollToItem(it.coerceIn(0, info.pages - 1)); tab.goTo = null } }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(tr("Im PDF suchen")) },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                scope.launch { hits = withContext(Dispatchers.IO) { PdfTools.search(context, tab.file, query) }; side = "search" }
            }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { side = if (side == "outline") "thumbs" else "outline" }, enabled = outline.isNotEmpty()) { Icon(Icons.Outlined.Toc, contentDescription = tr("Gliederung")) }
        IconButton(onClick = { night = !night }) { Icon(Icons.Outlined.DarkMode, contentDescription = tr("Nachtmodus")) }
        IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) }) { Icon(Icons.Outlined.ZoomOut, contentDescription = tr("Verkleinern")) }
        IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(3f) }) { Icon(Icons.Outlined.ZoomIn, contentDescription = tr("Vergrößern")) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 640.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(Modifier.width(170.dp).fillMaxHeight().padding(8.dp)) {
                    when (side) {
                        "outline" -> LazyColumn {
                            itemsIndexed(outline) { _, e ->
                                Text(
                                    e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().clickable { if (e.page > 0) tab.goTo = e.page - 1 }.padding(start = (e.level * 10).dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                        }
                        "search" -> LazyColumn {
                            item { HintText("${hits.size} " + tr("Treffer")) }
                            itemsIndexed(hits) { _, h ->
                                Column(Modifier.fillMaxWidth().clickable { tab.goTo = h.page - 1 }.padding(vertical = 6.dp)) {
                                    Text(tr("Seite") + " ${h.page}", style = MaterialTheme.typography.labelMedium)
                                    Text(h.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(info.pages) { i ->
                                Column(Modifier.clickable { tab.goTo = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    PageImage(tab.file, i, info.aspect(i), 220, night, Modifier.fillMaxWidth())
                                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            val density = LocalDensity.current
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                val pageWidth = (maxWidth - 24.dp) * zoom
                val px = with(density) { pageWidth.toPx() }.roundToInt().coerceIn(200, 2400)
                LazyColumn(state = list, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    if (!wide && side == "search" && hits.isNotEmpty()) item { HintText("${hits.size} " + tr("Treffer") + " – " + hits.take(5).joinToString { tr("S.") + " ${it.page}" }) }
                    items(info.pages) { i -> PageImage(tab.file, i, info.aspect(i), px, night, Modifier.width(pageWidth).padding(horizontal = 4.dp)) }
                }
            }
        }
    }
}

/** Pages: select, rotate, delete, extract, sort by dragging, merge, split, number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfPages(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val info = tab.info ?: return
    val selected = remember(tab.file) { mutableStateListOf<Int>() }
    val scope = rememberCoroutineScope()
    var splitOpen by remember { mutableStateOf(false) }
    var numbersOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    val chosen = selected.sorted()
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(enabled = chosen.isNotEmpty(), onClick = { onRun { src, out -> PdfTools.rearrange(context, src, (0 until info.pages).toList(), chosen.associateWith { 90 }, out) } }) {
            Icon(Icons.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Drehen"))
        }
        OutlinedButton(enabled = chosen.isNotEmpty() && chosen.size < info.pages, onClick = {
            onRun { src, out -> PdfTools.rearrange(context, src, (0 until info.pages).filter { it !in chosen }, emptyMap(), out) }
            selected.clear()
        }) { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Löschen")) }
        OutlinedButton(enabled = chosen.isNotEmpty(), onClick = {
            scope.launch {
                onBusy(true)
                val out = File(PdfWork.dir(context), tab.name.removeSuffix(".pdf") + " (Seiten ${chosen.joinToString(",") { "${it + 1}" }.take(40)}).pdf")
                val ok = withContext(Dispatchers.IO) { runCatching { PdfTools.extract(context, tab.file, chosen, out) }.isSuccess }
                onBusy(false)
                if (ok) PdfWork.open(context, out, out.name, keepOriginal = false) else Notices.error(tr("Extrahieren fehlgeschlagen."))
            }
        }) { Text(tr("Extrahieren")) }
        OutlinedButton(onClick = { mergeOpen = true }) { Text(tr("Zusammenfügen")) }
        OutlinedButton(onClick = { splitOpen = true }) { Text(tr("Teilen")) }
        OutlinedButton(onClick = { numbersOpen = true }) { Text(tr("Seitenzahlen")) }
        TextButton(onClick = { if (selected.size == info.pages) selected.clear() else { selected.clear(); selected.addAll(0 until info.pages) } }) {
            Text(tr(if (selected.size == info.pages) "Keine auswählen" else "Alle auswählen"))
        }
    }
    HintText(tr("Antippen wählt Seiten aus; lange drücken und ziehen sortiert sie um."), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    // Sorting by dragging a thumbnail onto another place.
    val grid = rememberLazyGridState()
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf<Int?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        state = grid,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(List(info.pages) { it }, key = { _, i -> "${tab.file.name}-$i" }) { _, i ->
            val isSel = i in selected
            val shift = if (dragging == i) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else IntOffset.Zero
            Column(
                Modifier
                    .offset { shift }
                    .border(if (target == i && dragging != i) 3.dp else if (isSel) 2.dp else 0.dp, if (target == i && dragging != i || isSel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(4.dp)
                    .pointerInput(tab.file, i) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = i; dragOffset = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                val me = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i } ?: return@detectDragGesturesAfterLongPress
                                val center = Offset(me.offset.x + me.size.width / 2f + dragOffset.x, me.offset.y + me.size.height / 2f + dragOffset.y)
                                target = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                    center.x >= item.offset.x && center.x <= item.offset.x + item.size.width && center.y >= item.offset.y && center.y <= item.offset.y + item.size.height
                                }?.index
                            },
                            onDragEnd = {
                                val from = dragging
                                val to = target
                                dragging = null; target = null; dragOffset = Offset.Zero
                                if (from != null && to != null && from != to) {
                                    val order = (0 until info.pages).toMutableList().apply { remove(from); add(to, from) }
                                    onRun { src, out -> PdfTools.rearrange(context, src, order, emptyMap(), out) }
                                    selected.clear()
                                }
                            },
                            onDragCancel = { dragging = null; target = null; dragOffset = Offset.Zero },
                        )
                    }
                    .clickable { if (isSel) selected.remove(i) else selected.add(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageImage(tab.file, i, info.aspect(i), 240, false, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(isSel, { on -> if (on) selected.add(i) else selected.remove(i) }, modifier = Modifier.size(28.dp))
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (mergeOpen) MergeDialog(tab, onDismiss = { mergeOpen = false }, onRun = onRun)
    if (splitOpen) SplitDialog(info.pages, onDismiss = { splitOpen = false }) { groups ->
        splitOpen = false
        scope.launch {
            onBusy(true)
            val files = withContext(Dispatchers.IO) {
                runCatching { PdfTools.split(context, tab.file, groups, File(PdfWork.dir(context), "teile-${System.nanoTime()}"), tab.name.removeSuffix(".pdf")) }.getOrDefault(emptyList())
            }
            onBusy(false)
            if (files.isEmpty()) Notices.error(tr("Teilen fehlgeschlagen.")) else {
                files.forEach { PdfWork.open(context, it, it.name, keepOriginal = false) }
                Notices.info("${files.size} " + tr("Teile geöffnet – je Tab speichern."))
            }
        }
    }
    if (numbersOpen) NumbersDialog(onDismiss = { numbersOpen = false }) { numbering ->
        numbersOpen = false
        onRun { src, out -> PdfTools.addPageNumbers(context, src, numbering, out) }
    }
}

@Composable
private fun MergeDialog(tab: PdfTab, onDismiss: () -> Unit, onRun: ((File, File) -> Unit) -> Unit) {
    val context = LocalContext.current
    val others = PdfWork.tabs.filter { it !== tab }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(others.map { it.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Zusammenfügen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (others.isEmpty()) Text(tr("Öffne zuerst weitere PDFs (Ordner-Symbol oben); sie werden dann an dieses angehängt."))
                else {
                    Text(tr("An") + " „${tab.name}“ " + tr("anhängen, in dieser Reihenfolge:"))
                    others.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (t.id in chosen) chosen.remove(t.id) else chosen.add(t.id) }) {
                            Checkbox(t.id in chosen, { on -> if (on) chosen.add(t.id) else chosen.remove(t.id) })
                            Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = chosen.isNotEmpty(), onClick = {
                val files = others.filter { it.id in chosen }.map { it.file }
                onRun { src, out -> PdfTools.merge(context, listOf(src) + files, out) }
                onDismiss()
            }) { Text(tr("Zusammenfügen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun SplitDialog(pages: Int, onDismiss: () -> Unit, onSplit: (List<List<Int>>) -> Unit) {
    var every by remember { mutableStateOf("1") }
    var ranges by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Teilen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(every, { every = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Alle … Seiten ein neues PDF")) }, singleLine = true)
                Text(tr("oder nach Bereichen, je Bereich ein PDF:"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(ranges, { ranges = it }, label = { Text(tr("z. B. 1-3; 4-6; 7-")) }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val groups = if (ranges.isNotBlank()) ranges.split(';').map { parsePageRanges(it, pages) }.filter { it.isNotEmpty() }
                else (0 until pages).chunked((every.toIntOrNull() ?: 1).coerceAtLeast(1))
                onSplit(groups)
            }) { Text(tr("Teilen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumbersDialog(onDismiss: () -> Unit, onApply: (PdfNumbering) -> Unit) {
    var format by remember { mutableStateOf("Seite {n} von {N}") }
    var start by remember { mutableStateOf("1") }
    var position by remember { mutableStateOf(PdfNumbering.Position.BOTTOM_CENTER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Seitenzahlen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(format, { format = it }, label = { Text(tr("Format ({n} = Seite, {N} = Seitenzahl)")) }, singleLine = true)
                OutlinedTextField(start, { start = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Erste Zahl")) }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfNumbering.Position.entries.forEach { p -> FilterChip(selected = position == p, onClick = { position = p }, label = { Text(tr(p.label)) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(PdfNumbering(format.ifBlank { "{n}" }, position, start.toIntOrNull() ?: 1)) }) { Text(tr("Setzen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

private val STAMPS = listOf("GEPRÜFT", "ERLEDIGT", "ENTWURF", "VERTRAULICH", "EINGEGANGEN")
private val MARK_COLORS = listOf(0xFFE066, 0x80DEEA, 0xA5D6A7, 0xF48FB1, 0x1565C0, 0xC62828)

/** Markieren & Notizen: tools, color, the pages to draw on, and the list of comments. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfMarker(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val context = LocalContext.current
    val info = tab.info ?: return
    var tool by remember { mutableStateOf(PdfMarkKind.HIGHLIGHT) }
    var color by remember { mutableStateOf(MARK_COLORS.first()) }
    var stamp by remember { mutableStateOf(STAMPS.first()) }
    var noteAt by remember { mutableStateOf<Pair<Int, PdfBox>?>(null) }
    var showComments by remember { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PdfMarkKind.entries.forEach { k -> FilterChip(selected = tool == k, onClick = { tool = k }, label = { Text(tr(k.label)) }) }
        if (tool == PdfMarkKind.STAMP) STAMPS.forEach { s -> FilterChip(selected = stamp == s, onClick = { stamp = s }, label = { Text(s) }) }
        if (tool != PdfMarkKind.STAMP && tool != PdfMarkKind.NOTE) MARK_COLORS.forEach { col ->
            Box(
                Modifier.size(28.dp).background(Color(0xFF000000 or col.toLong()), RoundedCornerShape(14.dp))
                    .border(if (color == col) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (color == col) 0.8f else 0.2f), RoundedCornerShape(14.dp))
                    .clickable { color = col },
            )
        }
        TextButton(onClick = { showComments = true }) { Text(tr("Kommentarliste")) }
    }
    HintText(
        tr(
            when (tool) {
                PdfMarkKind.INK -> "Mit dem Finger oder der Maus zeichnen."
                PdfMarkKind.NOTE -> "Auf die Stelle tippen, an der die Notiz stehen soll."
                PdfMarkKind.STAMP -> "Auf die Stelle tippen, an der der Stempel stehen soll."
                else -> "Über den Text ziehen, der markiert werden soll."
            },
        ),
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    fun add(mark: PdfMark) = onRun { src, out -> PdfTools.annotate(context, src, listOf(mark), out) }
    PageCanvasList(tab, info) { page, w, h ->
        MarkLayer(tool, w, h) { boxes, strokes, tap ->
            when (tool) {
                PdfMarkKind.NOTE -> tap?.let { noteAt = page to PdfBox(it.x, it.y, 0.02f, 0.02f) }
                PdfMarkKind.STAMP -> tap?.let { add(PdfMark(page, PdfMarkKind.STAMP, listOf(PdfBox(it.x, it.y, 0.1f, 0.03f)), text = stamp)) }
                PdfMarkKind.INK -> if (strokes.isNotEmpty()) add(PdfMark(page, PdfMarkKind.INK, strokes = strokes, color = color))
                else -> if (boxes.isNotEmpty()) add(PdfMark(page, tool, boxes, color = color))
            }
        }
    }
    noteAt?.let { (page, box) ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteAt = null },
            title = { Text(tr("Notiz")) },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(enabled = text.isNotBlank(), onClick = { add(PdfMark(page, PdfMarkKind.NOTE, listOf(box), text = text)); noteAt = null }) { Text(tr("Hinzufügen")) } },
            dismissButton = { TextButton(onClick = { noteAt = null }) { Text(tr("Abbrechen")) } },
        )
    }
    if (showComments) CommentsDialog(tab) { showComments = false }
}

@Composable
private fun CommentsDialog(tab: PdfTab, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val comments by produceState<List<PdfComment>?>(null, tab.file) { value = withContext(Dispatchers.IO) { runCatching { PdfTools.comments(context, tab.file) }.getOrDefault(emptyList()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kommentare")) },
        text = {
            val list = comments
            if (list == null) CircularProgressIndicator()
            else if (list.isEmpty()) Text(tr("Keine Kommentare in diesem PDF."))
            else LazyColumn(Modifier.height(360.dp)) {
                itemsIndexed(list) { _, cm ->
                    Column(Modifier.fillMaxWidth().clickable { tab.goTo = cm.page - 1; onDismiss() }.padding(vertical = 6.dp)) {
                        Text(tr("Seite") + " ${cm.page} · ${tr(cm.kind)}" + if (cm.author.isNotBlank()) " · ${cm.author}" else "", style = MaterialTheme.typography.labelMedium)
                        if (cm.text.isNotBlank()) Text(cm.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !comments.isNullOrEmpty(), onClick = {
                DocsHandoff.open(io.github.thepytales.llmhub.App.container, "Kommentare: " + tab.name, PdfTools.commentsMarkdown(tab.name, comments.orEmpty()))
                onDismiss()
            }) { Text(tr("Als Dokument exportieren")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** All pages, each with a [layer] on top (marking or the field editor) that knows the page and its size in px. */
@Composable
internal fun PageCanvasList(tab: PdfTab, info: PdfInfo, layer: @Composable (page: Int, w: Float, h: Float) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(tab.goTo) { tab.goTo?.let { list.scrollToItem(it.coerceIn(0, info.pages - 1)); tab.goTo = null } }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val pageWidth = (maxWidth - 24.dp).coerceAtMost(980.dp)
        val px = with(density) { pageWidth.toPx() }
        LazyColumn(state = list, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            items(info.pages) { i ->
                val aspect = info.aspect(i)
                Box(Modifier.width(pageWidth)) {
                    PageImage(tab.file, i, aspect, px.roundToInt().coerceIn(200, 2400), false, Modifier.fillMaxWidth())
                    Box(Modifier.matchParentSize()) { layer(i, px, px / aspect) }
                }
            }
        }
    }
}

/**
 * Drawing on a page of [w] × [h] px: a rectangle (highlight, underline, strike through), a freehand line or a tap
 * (note, stamp). [onDone] gets the result as fractions of the page.
 */
@Composable
private fun MarkLayer(tool: PdfMarkKind, w: Float, h: Float, onDone: (List<PdfBox>, List<List<PdfPoint>>, PdfPoint?) -> Unit) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val path = remember { mutableStateListOf<Offset>() }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxSize()
            .pointerInput(tool, w, h) {
                if (tool == PdfMarkKind.NOTE || tool == PdfMarkKind.STAMP) {
                    detectTapGestures { p -> onDone(emptyList(), emptyList(), PdfPoint(p.x / w, p.y / h)) }
                } else {
                    detectDragGestures(
                        onDragStart = { p -> start = p; end = p; path.clear(); path += p },
                        onDrag = { change, amount -> change.consume(); end = (end ?: Offset.Zero) + amount; path += end!! },
                        onDragEnd = {
                            val a = start
                            val b = end
                            if (a != null && b != null) {
                                if (tool == PdfMarkKind.INK) onDone(emptyList(), listOf(path.map { PdfPoint(it.x / w, it.y / h) }), null)
                                else {
                                    val x = minOf(a.x, b.x) / w
                                    val y = minOf(a.y, b.y) / h
                                    val bw = kotlin.math.abs(b.x - a.x) / w
                                    // A flat drag over a line of text still gets the height of a line.
                                    val bh = (kotlin.math.abs(b.y - a.y) / h).coerceAtLeast(0.018f)
                                    if (bw > 0.01f) onDone(listOf(PdfBox(x, y, bw, bh)), emptyList(), null)
                                }
                            }
                            start = null; end = null; path.clear()
                        },
                        onDragCancel = { start = null; end = null; path.clear() },
                    )
                }
            },
    ) {
        val a = start
        val b = end
        if (a != null && b != null) {
            if (tool == PdfMarkKind.INK && path.size > 1) {
                val p = Path().apply { moveTo(path[0].x, path[0].y); path.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(p, accent, style = Stroke(width = 3f))
            } else {
                drawRect(accent.copy(alpha = 0.25f), topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)), size = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)))
            }
        }
    }
}

/**
 * Formulare ausfüllbar machen (Windows): find the fields automatically or let the agent do it, then move them (drag),
 * resize (handle at the bottom right), retype or rename them; „Übernehmen“ writes them into the PDF.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfFormEditor(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit, onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val info = tab.info ?: return
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(tab.file) {
        if (!tab.fieldsLoaded) {
            val existing = withContext(Dispatchers.IO) { runCatching { PdfTools.formFields(context, tab.file) }.getOrDefault(emptyList()) }
            tab.fields.clear()
            tab.fields.addAll(existing)
            tab.fieldsLoaded = true
        }
    }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = {
            scope.launch {
                val found = withContext(Dispatchers.IO) { runCatching { PdfTools.detectFields(context, tab.file) }.getOrDefault(emptyList()) }
                val fresh = found.filter { f -> tab.fields.none { e -> e.page == f.page && overlap(e.box, f.box) } }
                tab.fields.addAll(fresh)
                Notices.info(if (fresh.isEmpty()) tr("Keine weiteren Eingabestellen gefunden – Felder lassen sich auch selbst hinzufügen.") else "${fresh.size} " + tr("Felder erkannt – prüfen und übernehmen."))
            }
        }) { Text(tr("Felder erkennen")) }
        OutlinedButton(onClick = { adding = !adding }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.size(4.dp))
            Text(tr(if (adding) "Feld aufziehen …" else "Feld hinzufügen"))
        }
        OutlinedButton(onClick = {
            val text = tr("Mach dieses PDF ausfüllbar: Erkenne, wo Eingaben hingehören (Linien, Kästchen, „Name: ____“), und setze die Formularfelder mit make_pdf_fillable.")
            ChatHandoff.pending.value = ChatHandoff.Content(
                text,
                listOf(io.github.thepytales.llmhub.data.FileAttachment(tab.name, "application/pdf", "", path = tab.file.absolutePath)),
                send = true,
            )
            onOpenChat()
        }) { Text(tr("Mit dem Agenten")) }
        Button(enabled = tab.fieldsLoaded, onClick = {
            val specs = tab.fields.toList()
            onRun { src, out -> PdfTools.setFormFields(context, src, specs, out) }
            Notices.info("${specs.size} " + tr("Felder übernommen – das PDF lässt sich jetzt ausfüllen."))
        }) { Text(tr("Übernehmen")) }
        HintText("${tab.fields.size} " + tr("Felder"))
    }
    selected?.let { idx ->
        val f = tab.fields.getOrNull(idx)
        if (f != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(f.name, { v -> tab.fields[idx] = f.copy(name = v) }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.width(220.dp))
            PdfFieldType.entries.forEach { t -> FilterChip(selected = f.type == t, onClick = { tab.fields[idx] = f.copy(type = t) }, label = { Text(tr(t.label)) }) }
            IconButton(onClick = { tab.fields.removeAt(idx); selected = null }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Feld löschen")) }
        }
    }
    HintText(tr("Felder ziehen zum Verschieben, die Ecke unten rechts ändert die Größe, Antippen wählt ein Feld aus."), Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
    PageCanvasList(tab, info) { page, w, h ->
        if (adding) MarkLayer(PdfMarkKind.HIGHLIGHT, w, h) { boxes, _, _ ->
            boxes.firstOrNull()?.let { b -> tab.fields += PdfFieldSpec(tr("Feld") + " ${tab.fields.size + 1}", page, b); adding = false; selected = tab.fields.lastIndex }
        }
        val density = LocalDensity.current
        tab.fields.forEachIndexed { idx, f ->
            if (f.page != page) return@forEachIndexed
            val x = with(density) { (f.box.x * w).toDp() }
            val y = with(density) { (f.box.y * h).toDp() }
            val bw = with(density) { (f.box.w * w).toDp() }
            val bh = with(density) { (f.box.h * h).toDp() }
            val sel = selected == idx
            Box(
                Modifier.offset(x, y).size(bw, bh)
                    .background((if (f.type == PdfFieldType.CHECKBOX) Color(0xFF66BB6A) else Color(0xFF42A5F5)).copy(alpha = if (sel) 0.35f else 0.2f))
                    .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color(0xFF1E88E5))
                    .pointerInput(idx, w, h) {
                        detectDragGestures(onDragStart = { selected = idx }) { change, amount ->
                            change.consume()
                            val cur = tab.fields.getOrNull(idx) ?: return@detectDragGestures
                            tab.fields[idx] = cur.copy(box = cur.box.copy(x = (cur.box.x + amount.x / w).coerceIn(0f, 1f - cur.box.w), y = (cur.box.y + amount.y / h).coerceIn(0f, 1f - cur.box.h)))
                        }
                    }
                    .clickable { selected = if (sel) null else idx },
            ) {
                if (f.type == PdfFieldType.CHECKBOX) Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = Color(0xFF2E7D32).copy(alpha = 0.5f))
                else Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
                // Resize handle.
                Box(
                    Modifier.align(Alignment.BottomEnd).size(12.dp).background(MaterialTheme.colorScheme.primary)
                        .pointerInput(idx, w, h) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                val cur = tab.fields.getOrNull(idx) ?: return@detectDragGestures
                                tab.fields[idx] = cur.copy(box = cur.box.copy(w = (cur.box.w + amount.x / w).coerceIn(0.01f, 1f - cur.box.x), h = (cur.box.h + amount.y / h).coerceIn(0.008f, 1f - cur.box.y)))
                            }
                        },
                )
            }
        }
    }
}

private fun overlap(a: PdfBox, b: PdfBox): Boolean = a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom
