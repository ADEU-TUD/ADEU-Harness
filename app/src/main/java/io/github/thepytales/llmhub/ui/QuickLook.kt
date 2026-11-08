package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.data.FileKind
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.files.FileDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the Schnellansicht shows of a file: its pages, its text, its table or its slides. */
private sealed class Look {
    class Pages(val file: File, val count: Int) : Look()
    class Picture(val image: ImageBitmap) : Look()
    class Text(val markdown: String) : Look()
    class Table(val rows: List<List<String>>) : Look()
    class Slides(val slides: List<SlideDeck.Slide>) : Look()
    class Folder(val names: List<String>) : Look()
    data object None : Look()
}

/** Number of pages of a PDF (0 if it cannot be read). */
internal fun pdfPages(c: AppContainer, f: File): Int = runCatching {
    io.github.thepytales.llmhub.docs.PdfTools.init(c.appContext)
    io.github.thepytales.llmhub.docs.PdfTools.load(f).use { it.numberOfPages }
}.getOrDefault(0)

private fun look(c: AppContainer, item: DriveItem): Look {
    val f = item.file
    if (item.dir) return Look.Folder(f.listFiles().orEmpty().filter { !it.name.startsWith(".") }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).take(60).map { (if (it.isDirectory) "📁 " else "") + it.name })
    item.doc?.let { d ->
        return when (d.docKind) {
            DocKind.SLIDES -> Look.Slides(SlideDeck.parse(d.tabs.first().markdown))
            DocKind.SHEET -> Look.Table(SheetEngine.grid(d.tabs.first().markdown).take(60).map { it.take(12) })
            else -> Look.Text(d.markdown())
        }
    }
    return when (item.kind) {
        FileKind.PDF -> pdfPages(c, f).let { if (it > 0) Look.Pages(f, it) else Look.None }
        FileKind.IMAGE -> if (f.length() < 40_000_000) decodeImage(f.readBytes())?.let { Look.Picture(it) } ?: Look.None else Look.None
        FileKind.POWERPOINT -> if (f.extension.equals("pptx", true)) runCatching { Look.Slides(SlideDeck.parse(OfficeImport.pptx(f.readBytes()))) }.getOrDefault(Look.None) else Look.None
        FileKind.EXCEL -> runCatching { OfficeImport.read(f.name, f.readBytes())?.tabs?.firstOrNull()?.second }.getOrNull()
            ?.let { Look.Table(SheetEngine.grid(it).take(60).map { r -> r.take(12) }) } ?: Look.None
        FileKind.WORD, FileKind.TEXT, FileKind.MARKDOWN, FileKind.MAIL -> c.fileLibrary.textOf(f)?.let { Look.Text(it.take(200_000)) } ?: Look.None
        else -> c.fileLibrary.textOf(f)?.takeIf { it.isNotBlank() }?.let { Look.Text(it.take(200_000)) } ?: Look.None
    }
}

/**
 * Schnellansicht (AP-66), like the space bar in Finder or the preview of the Explorer: the file large, without opening
 * an editor – PDF pages, pictures, documents, tables, slides, a folder's content. ←/→ go to the neighbours in the list,
 * Leertaste or Esc close, Enter opens.
 */
@Composable
fun QuickLookDialog(c: AppContainer, items: List<DriveItem>, start: DriveItem, onOpen: (DriveItem) -> Unit, onClose: () -> Unit) {
    var current by remember { mutableStateOf(start) }
    val focus = remember { FocusRequester() }
    fun go(delta: Int) {
        FileDetails.step(items.map { it.key }, current.key, delta)?.let { k -> items.firstOrNull { it.key == k }?.let { current = it } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize().padding(24.dp).focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Spacebar, Key.Escape -> { onClose(); true }
                    Key.DirectionRight, Key.DirectionDown -> { go(1); true }
                    Key.DirectionLeft, Key.DirectionUp -> { go(-1); true }
                    Key.Enter, Key.NumPadEnter -> { onOpen(current); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(current.kind.icon(), null, tint = current.kind.color(), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HintText(listOfNotNull(tr(current.kind.label), FileDetails.size(current.size).takeIf { !current.dir }).joinToString(" · ") + "  ·  " + tr("Leertaste schließt, ←/→ blättert"))
                    }
                    if (items.size > 1) {
                        IconButton(onClick = { go(-1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Vorige Datei")) }
                        IconButton(onClick = { go(1) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("Nächste Datei")) }
                    }
                    TextButton(onClick = { onOpen(current) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Öffnen"))
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen")) }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)), contentAlignment = Alignment.TopCenter) {
                    LookBody(c, current)
                }
            }
        }
    }
}

@Composable
private fun LookBody(c: AppContainer, item: DriveItem) {
    val look by produceState<Look?>(null, item.key, item.modified) { value = withContext(Dispatchers.IO) { runCatching { look(c, item) }.getOrDefault(Look.None) } }
    when (val l = look) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Look.Pages -> LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items((0 until minOf(l.count, 200)).toList()) { i -> PdfPageView(l.file, i) }
            if (l.count > 200) item { HintText(tr("… und {0} weitere Seiten – zum Lesen öffnen.", l.count - 200)) }
        }
        is Look.Picture -> Image(l.image, contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
        is Look.Text -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(16.dp)) {
                MarkdownText(l.markdown, Modifier.padding(28.dp), color = Color(0xFF1F2933))
            }
        }
        is Look.Table -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Column {
                    l.rows.forEachIndexed { r, row ->
                        Row {
                            row.forEach { cell ->
                                Text(
                                    cell, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF1F2933),
                                    fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(120.dp).border(0.5.dp, Color(0xFFD9E2EC)).padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        is Look.Slides -> LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(l.slides.indices.toList()) { i ->
                Box(Modifier.widthIn(max = 900.dp).fillMaxWidth()) { SlideCanvas(l.slides[i], Modifier.fillMaxWidth().aspectRatio(16f / 9f), i + 1, l.slides.size, deck = l.slides) }
            }
        }
        is Look.Folder -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            if (l.names.isEmpty()) item { HintText(tr("Der Ordner ist leer.")) }
            items(l.names) { n -> Text(n, modifier = Modifier.padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Look.None -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(item.kind.icon(), null, Modifier.size(64.dp), tint = item.kind.color().copy(alpha = 0.7f))
                HintText(tr("Für diese Datei gibt es keine Vorschau."))
            }
        }
    }
}

@Composable
private fun PdfPageView(file: File, index: Int) {
    val page by produceState<ImageBitmap?>(null, file, index) { value = withContext(Dispatchers.IO) { PdfRender.page(file, index, 1000) } }
    Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
        val p = page
        if (p == null) Box(Modifier.fillMaxWidth().aspectRatio(0.707f), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        else Image(p, contentDescription = tr("Seite {0}", index + 1), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Details-Spalte (AP-66): beside the list on wide screens – a preview, what the file is, size, dates, place, pages or
 * words, Schlagwörter, the chat it came from; buttons for the Schnellansicht and to open it. Without a file: a hint.
 */
@Composable
fun FileDetailsPane(c: AppContainer, item: DriveItem?, chatTitle: (String) -> String?, onLook: (DriveItem) -> Unit, onOpen: (DriveItem) -> Unit, onClose: () -> Unit) {
    Column(Modifier.width(300.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Details"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Details ausblenden")) }
        }
        if (item == null) {
            HintText(tr("Zeige mit der Maus auf eine Datei oder wähle eine aus – hier stehen dann ihre Details. Leertaste: Schnellansicht."))
            return
        }
        val rows by produceState<List<FileDetails.Row>?>(null, item.key, item.modified) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val extra = buildList {
                        val d = item.doc
                        when {
                            d != null && d.docKind == DocKind.SLIDES -> add(FileDetails.Row(tr("Folien"), "${SlideDeck.parse(d.tabs.first().markdown).size}"))
                            d != null && d.docKind == DocKind.SHEET -> add(FileDetails.Row(tr("Blätter"), "${d.tabs.size}"))
                            d != null -> add(FileDetails.Row(tr("Wörter"), "${d.words}"))
                            item.kind == FileKind.PDF -> pdfPages(c, item.file).takeIf { it > 0 }?.let { add(FileDetails.Row(tr("Seiten"), "$it")) }
                            item.kind == FileKind.IMAGE && item.size < 40_000_000 -> decodeImage(item.file.readBytes())?.let { add(FileDetails.Row(tr("Abmessungen"), "${it.width} × ${it.height} px")) }
                            !item.dir && item.kind != FileKind.OTHER -> c.fileLibrary.textOf(item.file)?.let { add(FileDetails.Row(tr("Wörter"), "${FileDetails.words(it)}")) }
                        }
                        d?.takeIf { it.versions.size > 1 }?.let { add(FileDetails.Row(tr("Versionen"), "${it.versions.size}")) }
                        d?.takeIf { it.openComments > 0 }?.let { add(FileDetails.Row(tr("Offene Kommentare"), "${it.openComments}")) }
                        item.chatId?.let { id -> add(FileDetails.Row(tr("Aus dem Chat"), chatTitle(id) ?: tr("Chat"))) }
                    }
                    val tags = c.fileLibrary.meta.current.firstOrNull { it.key == "file:" + item.file.absolutePath || item.doc != null && it.key == "doc:" + item.doc.id }?.tags.orEmpty()
                    FileDetails.rows(item.file, tr(item.kind.label).takeIf { !item.dir } ?: tr("Ordner"), c.drive.root, extra, tags, item.count.takeIf { item.dir })
                }.getOrDefault(emptyList())
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLook(item) }) {
                    Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Vorschau"))
                }
                TextButton(onClick = { onOpen(item) }) { Text(tr("Öffnen")) }
            }
            val r = rows
            if (r == null) CircularProgressIndicator(Modifier.size(20.dp))
            else r.forEach { row ->
                Column {
                    Text(row.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.heightIn(min = 8.dp))
        }
    }
}
