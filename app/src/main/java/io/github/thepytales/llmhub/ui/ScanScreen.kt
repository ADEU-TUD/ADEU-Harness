package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.docs.DocScanner
import io.github.thepytales.llmhub.docs.PdfOcr
import io.github.thepytales.llmhub.docs.PdfPlatform
import io.github.thepytales.llmhub.docs.ScanImages
import io.github.thepytales.llmhub.docs.ScanMode
import io.github.thepytales.llmhub.docs.ScanPage
import io.github.thepytales.llmhub.docs.ScanPoint
import io.github.thepytales.llmhub.docs.ScanQuad
import io.github.thepytales.llmhub.docs.ScanRaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

/** The pages of the scan being made – kept while the app runs, so a look at another screen loses nothing. */
object ScanDraft {
    val pages = mutableStateListOf<ScanPage>()
    var mode: ScanMode = ScanMode.COLOR

    /** Folder of the finished scans in the Lora-Ordner. */
    const val FOLDER = "Scans"

    /** Pages from new photos: the paper is looked for in each (in the background). */
    suspend fun add(files: List<File>): List<ScanPage> = withContext(Dispatchers.Default) {
        files.map { f ->
            val quad = runCatching { DocScanner.detect(ScanImages.read(f, 1000)) }.getOrNull()
            ScanPage(f, quad ?: ScanQuad.FULL, mode, detected = quad != null)
        }
    }
}

/** Preview size of the page being edited. */
private const val PREVIEW_SIDE = 1100

/**
 * Dokumenten-Scanner (AP-62): photograph (Android) or scan (Windows, WIA) pages, see the found edges and move the
 * corners, choose colour, grey or black and white, turn, reorder, and save everything as one PDF – with text recognition,
 * so it can be searched – in the Lora-Ordner under „Scans“ (and through the sync on the other devices).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(c: AppContainer, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = ScanDraft.pages
    var current by remember { mutableStateOf(0) }
    var crop by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var saved by remember { mutableStateOf<DocScanner.Saved?>(null) }

    val sources = rememberScanSources { files ->
        adding = true
        scope.launch {
            val added = runCatching { ScanDraft.add(files) }.onFailure { Notices.error(it.message ?: tr("Das Bild ließ sich nicht lesen.")) }.getOrDefault(emptyList())
            pages.addAll(added)
            if (added.isNotEmpty()) {
                current = pages.size - 1
                // No edges found: the corners are shown at once so they can be put on the paper.
                crop = added.any { !it.detected }
            }
            adding = false
        }
    }
    val page = pages.getOrNull(current.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))

    fun update(p: ScanPage) {
        val i = pages.indexOfFirst { it.id == p.id }
        if (i >= 0) pages[i] = p
    }

    fun openSaved(f: File) {
        if (c.settings.current.toolOn(AppTool.PDF.id)) {
            PdfHandoff.open(f.absolutePath, 1, f.name)
            onNavigate("pdf")
        } else openDocument(context, f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(if (pages.isEmpty()) tr("Scannen") else tr("Scannen · {0} Seite(n)", pages.size)) },
                actions = {
                    if (pages.isNotEmpty() && saving == null) Spot("scan.save") {
                        Button(onClick = { saveOpen = true }) {
                            Icon(Icons.Outlined.PictureAsPdf, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Als PDF speichern"))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            saving?.let { (what, done, total) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    HintText(if (what == "ocr") tr("Texterkennung: Seite {0} von {1} …", (done + 1).coerceAtMost(total), total) else tr("Seiten werden aufbereitet: {0} von {1} …", (done + 1).coerceAtMost(total), total))
                }
            }
            if (page == null) {
                EmptyScan(sources, adding)
            } else {
                // ── The page: result or corners ──
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (crop) CornerEditor(page) { q -> update(page.copy(quad = q, detected = true)) }
                    else ResultView(page)
                    if (adding) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                // ── Tools of the page ──
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = crop, onClick = { crop = !crop },
                        label = { Text(if (crop) tr("Fertig zuschneiden") else tr("Ecken anpassen")) },
                        leadingIcon = { Icon(Icons.Outlined.Crop, null, Modifier.size(18.dp)) },
                    )
                    ScanMode.entries.forEach { m ->
                        FilterChip(selected = page.mode == m, onClick = { ScanDraft.mode = m; update(page.copy(mode = m)) }, label = { Text(tr(m.label)) })
                    }
                    IconButton(onClick = { update(page.copy(rotation = (page.rotation + 1) % 4)) }) { Icon(Icons.Outlined.RotateRight, contentDescription = tr("Drehen")) }
                    IconButton(onClick = { update(page.copy(quad = ScanQuad.FULL, detected = false)) }) { Icon(Icons.Outlined.CropFree, contentDescription = tr("Ganzes Bild")) }
                    IconButton(enabled = current > 0, onClick = {
                        val i = current
                        pages.add(i - 1, pages.removeAt(i))
                        current = i - 1
                    }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Nach vorn")) }
                    IconButton(enabled = current < pages.size - 1, onClick = {
                        val i = current
                        pages.add(i + 1, pages.removeAt(i))
                        current = i + 1
                    }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("Nach hinten")) }
                    IconButton(onClick = {
                        val removed = pages.removeAt(current)
                        current = current.coerceAtMost(pages.size - 1).coerceAtLeast(0)
                        Notices.undo(tr("Seite entfernt"), restore = { pages.add(min(current, pages.size), removed) })
                    }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Seite entfernen")) }
                }
                // ── All pages and „add“ ──
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.forEachIndexed { i, p -> Thumb(p, i + 1, selected = i == current) { current = i; crop = false } }
                    sources.forEach { s ->
                        OutlinedButton(onClick = s.start, enabled = !adding) {
                            Icon(s.icon, null)
                            Spacer(Modifier.width(6.dp))
                            Text(s.label)
                        }
                    }
                }
            }
        }
    }

    if (saveOpen) SaveScanDialog(c, onDismiss = { saveOpen = false }) { name, useOcr ->
        saveOpen = false
        val list = pages.toList()
        saving = Triple("pages", 0, list.size)
        scope.launch {
            val dir = File(c.drive.root, ScanDraft.FOLDER)
            val target = DocScanner.target(dir, name)
            val engine = scanOcr(c, context, useOcr)
            val result = runCatching {
                withContext(Dispatchers.IO) { DocScanner.save(context, list, target, engine) { what, d, t -> saving = Triple(what, d, t) } }
            }
            saving = null
            result.onFailure { Notices.error(tr("Das PDF ließ sich nicht speichern: {0}", it.message ?: it.javaClass.simpleName)) }
            result.onSuccess { s ->
                c.drive.changed()
                io.github.thepytales.llmhub.agent.LoraFocus.files = listOf(s.file)
                // The photos are not needed any more (the PDF has the pages); the draft starts empty.
                withContext(Dispatchers.IO) { list.forEach { p -> if (p.source.parentFile == scanWorkDir(context)) p.source.delete() } }
                pages.clear()
                current = 0
                saved = s
            }
        }
    }

    saved?.let { s ->
        AlertDialog(
            onDismissRequest = { saved = null },
            icon = { Icon(Icons.Outlined.DocumentScanner, null) },
            title = { Text(tr("Scan gespeichert")) },
            text = {
                Text(
                    tr("„{0}“ liegt in Dateien › {1} ({2} Seite(n)).", s.file.name, ScanDraft.FOLDER, s.pages) + " " +
                        when {
                            s.textPages > 0 -> tr("Text auf {0} Seite(n) erkannt – das PDF ist durchsuchbar.", s.textPages) + (s.engine?.let { " ($it)" } ?: "")
                            s.ocrError != null -> tr("Ohne Texterkennung: {0}", s.ocrError)
                            else -> tr("Ohne Texterkennung gespeichert.")
                        },
                )
            },
            confirmButton = { TextButton(onClick = { saved = null; openSaved(s.file) }) { Text(tr("Öffnen")) } },
            dismissButton = { TextButton(onClick = { saved = null }) { Text(tr("Schließen")) } },
        )
    }
}

/** Text recognition for a scan: the PC's own (Windows) or a vision model the privacy level allows; null if none or off. */
private fun scanOcr(c: AppContainer, context: android.content.Context, wanted: Boolean): DocScanner.Ocr? {
    if (!wanted) return null
    val model = if (PdfPlatform.localOcr != null) null else PdfOcr.visionModel(c) ?: return null
    return DocScanner.Ocr { pdf, pages, progress ->
        val r = PdfOcr.recognize(c, context, pdf, pages, model, File(scanWorkDir(context), "ocr-${System.currentTimeMillis()}"), progress)
        r.pages to r.engine
    }
}

/** Which text recognition a scan would use, for the save dialog; null if there is none. */
private fun ocrLabel(c: AppContainer): String? =
    PdfPlatform.localOcr?.name ?: PdfOcr.visionModel(c)?.let { tr("Bildmodell {0}", io.github.thepytales.llmhub.data.ModelCatalog.info(it).displayName) }

@Composable
private fun SaveScanDialog(c: AppContainer, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(DocScanner.defaultName()) }
    val label = remember { ocrLabel(c) }
    var ocr by remember { mutableStateOf(label != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Als PDF speichern")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name")) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Text erkennen (durchsuchbar)"))
                        HintText(label ?: tr("Keine Texterkennung verfügbar – unter Android braucht sie ein Bildmodell, das der Datenschutz erlaubt."))
                    }
                    Switch(checked = ocr, onCheckedChange = { ocr = it }, enabled = label != null)
                }
                HintText(tr("Ziel: Dateien › {0}", ScanDraft.FOLDER))
            }
        },
        confirmButton = { Button(onClick = { onSave(name, ocr && label != null) }) { Text(tr("Speichern")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun EmptyScan(sources: List<ScanSource>, adding: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(tr("Dokument scannen"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Mehrere Seiten werden ein PDF mit Texterkennung – gerade gerückt, mit weißem Papier und klarer Schrift."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            if (adding) CircularProgressIndicator()
            else sources.forEachIndexed { i, s ->
                if (i == 0) Spot("scan.capture") { Button(onClick = s.start) { Icon(s.icon, null); Spacer(Modifier.width(8.dp)); Text(s.label) } }
                else OutlinedButton(onClick = s.start) { Icon(s.icon, null); Spacer(Modifier.width(8.dp)); Text(s.label) }
            }
            HintText(SCAN_HINT)
        }
    }
}

/** The photo of [page] (smaller) and the picture to show; null while it is read. */
@Composable
private fun rememberPhoto(page: ScanPage): Pair<ScanRaster, ImageBitmap>? =
    produceState<Pair<ScanRaster, ImageBitmap>?>(null, page.source) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val r = ScanImages.read(page.source, PREVIEW_SIDE)
                decodeImage(ScanImages.jpeg(r, 80))?.let { r to it }
            }.getOrNull()
        }
    }.value

/** The finished page as it will be in the PDF. */
@Composable
private fun ResultView(page: ScanPage) {
    val photo = rememberPhoto(page)
    val result by produceState<ImageBitmap?>(null, photo, page.quad, page.mode, page.rotation) {
        val p = photo ?: return@produceState
        value = withContext(Dispatchers.Default) { runCatching { decodeImage(ScanImages.jpeg(DocScanner.process(page, PREVIEW_SIDE, p.first), 85)) }.getOrNull() }
    }
    val img = result
    if (img == null) CircularProgressIndicator()
    else Image(
        img, contentDescription = tr("Gescannte Seite"), contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
    )
}

/** The photo with the found page as frame; the four corners can be dragged onto the paper's corners. */
@Composable
private fun CornerEditor(page: ScanPage, onQuad: (ScanQuad) -> Unit) {
    val photo = rememberPhoto(page) ?: run { CircularProgressIndicator(); return }
    val img = photo.second
    val quad by rememberUpdatedState(page.quad)
    val change by rememberUpdatedState(onQuad)
    val density = LocalDensity.current
    val handle = with(density) { 14.dp.toPx() }
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / img.width, boxH / img.height)
        val w = img.width * scale
        val h = img.height * scale
        val left = (boxW - w) / 2
        val top = (boxH - h) / 2
        var dragging by remember { mutableStateOf(-1) }
        Box(Modifier.fillMaxSize().semantics { contentDescription = tr("Ecken der Seite – ziehen, um sie auf die Ecken des Papiers zu legen") }
            .pointerInput(page.id, w, h) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val pts = quad.points.map { Offset(left + it.x * w, top + it.y * h) }
                        val i = pts.indices.minBy { hypot(pts[it].x - pos.x, pts[it].y - pos.y) }
                        dragging = if (hypot(pts[i].x - pos.x, pts[i].y - pos.y) < handle * 4) i else -1
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                    onDrag = { ev, amount ->
                        if (dragging >= 0) {
                            ev.consume()
                            val p = quad.points[dragging]
                            change(quad.with(dragging, ScanPoint((p.x + amount.x / w).coerceIn(0f, 1f), (p.y + amount.y / h).coerceIn(0f, 1f))))
                        }
                    },
                )
            },
        ) {
            Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val pts = quad.points.map { Offset(left + it.x * w, top + it.y * h) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, primary.copy(alpha = 0.18f))
                drawPath(path, primary, style = Stroke(width = 2.dp.toPx()))
                pts.forEachIndexed { i, o ->
                    drawCircle(Color.White, radius = handle, center = o)
                    drawCircle(primary, radius = if (i == dragging) handle * 0.8f else handle * 0.6f, center = o)
                }
            }
        }
    }
}

@Composable
private fun Thumb(page: ScanPage, number: Int, selected: Boolean, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, page.source, page.quad, page.mode, page.rotation) {
        value = withContext(Dispatchers.Default) {
            runCatching { decodeImage(ScanImages.jpeg(DocScanner.process(page, 220, ScanImages.read(page.source, 600)), 75)) }.getOrNull()
        }
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(64.dp, 88.dp).border(BorderStroke(if (selected) 2.dp else 1.dp, border), RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = tr("Seite {0}", number) },
        contentAlignment = Alignment.Center,
    ) {
        thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp)) }
        Text(
            "$number", style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).offset { IntOffset(-4, -4) }.background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        )
    }
}
