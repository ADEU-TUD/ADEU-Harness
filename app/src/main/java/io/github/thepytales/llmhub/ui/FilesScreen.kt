package io.github.thepytales.llmhub.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.FileHit
import io.github.thepytales.llmhub.data.FileItem
import io.github.thepytales.llmhub.data.FileKind
import io.github.thepytales.llmhub.data.FileLibrary
import io.github.thepytales.llmhub.data.FileSource
import io.github.thepytales.llmhub.docs.TextDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Icon of an entry of the Dateiverwaltung. */
internal fun FileKind.icon(): ImageVector = when (this) {
    FileKind.DOC, FileKind.WORD -> Icons.Outlined.Description
    FileKind.SLIDES, FileKind.POWERPOINT -> Icons.Outlined.Slideshow
    FileKind.SHEET, FileKind.EXCEL -> Icons.Outlined.TableChart
    FileKind.MARKDOWN, FileKind.TEXT -> Icons.Outlined.Code
    FileKind.PDF -> Icons.Outlined.PictureAsPdf
    FileKind.IMAGE -> Icons.Outlined.Image
    FileKind.MAIL -> Icons.Outlined.Email
    FileKind.KNOWLEDGE -> Icons.Outlined.AutoStories
    FileKind.OTHER -> Icons.Outlined.InsertDriveFile
}

internal fun FileKind.color(): Color = when (this) {
    FileKind.DOC, FileKind.WORD -> Color(0xFF2B579A)
    FileKind.SLIDES, FileKind.POWERPOINT -> Color(0xFFC43E1C)
    FileKind.SHEET, FileKind.EXCEL -> Color(0xFF217346)
    FileKind.PDF -> Color(0xFFB71C1C)
    FileKind.KNOWLEDGE -> Color(0xFF6A4FB6)
    else -> Color(0xFF5F6B7A)
}

/** „1 Datei“, „3 Dateien“. */
private fun count(n: Int) = "$n " + tr(if (n == 1) "Datei" else "Dateien")

private val WHEN = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

internal fun fileSizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1048576.0)
}

/**
 * Dateien (AP-18): everything the app keeps as files in one place – documents of the editor, files in storage
 * locations and project folders, the knowledge collection. „Zuletzt bearbeitet“, places and folders, Schlagwörter,
 * a full-text search over all formats with the passage and page, and comparing versions or two files.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(c: AppContainer, onBack: () -> Unit, onOpenDoc: (String) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs by c.docs.items.collectAsStateWithLifecycle()
    val metas by c.fileLibrary.meta.items.collectAsStateWithLifecycle()
    val knowledge by c.knowledge.docs.collectAsStateWithLifecycle()
    val indexing by c.fileLibrary.indexing.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    var hasRoots by remember { mutableStateOf(true) }
    val items by produceState<List<FileItem>?>(null, docs, metas, knowledge, reload) {
        value = withContext(Dispatchers.IO) {
            hasRoots = runCatching { c.fileLibrary.roots().isNotEmpty() }.getOrDefault(false)
            runCatching { c.fileLibrary.items() }.getOrDefault(emptyList())
        }
    }
    // Read the texts of new and changed files once, in the background.
    LaunchedEffect(items?.size, reload) { items?.let { list -> withContext(Dispatchers.IO) { runCatching { c.fileLibrary.index(list) } } } }

    var query by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    var path by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<FileHit>?>(null) }
    LaunchedEffect(query, items, indexing == null) {
        val q = query.trim()
        if (q.length < 2) {
            hits = null
            return@LaunchedEffect
        }
        delay(250)
        val all = items ?: return@LaunchedEffect
        hits = withContext(Dispatchers.Default) { c.fileLibrary.search(q, all, forms = io.github.thepytales.llmhub.data.Vocabulary.forms(c)) }
    }

    var tagsFor by remember { mutableStateOf<FileItem?>(null) }
    var folderFor by remember { mutableStateOf<FileItem?>(null) }
    // Formatwandler mit Verlustanzeige (AP-90).
    var convertFor by remember { mutableStateOf<FileItem?>(null) }
    var versionsOf by remember { mutableStateOf<String?>(null) }
    var compareFirst by remember { mutableStateOf<FileItem?>(null) }
    var comparison by remember { mutableStateOf<Pair<FileItem, FileItem>?>(null) }

    fun open(item: FileItem, page: Int? = null) {
        when {
            item.docId != null -> onOpenDoc(item.docId)
            item.source == FileSource.KNOWLEDGE -> onNavigate("settings/knowledge")
            item.path != null && item.kind == FileKind.PDF && c.settings.current.toolOn(io.github.thepytales.llmhub.data.AppTool.PDF.id) -> {
                PdfHandoff.open(item.path, page ?: 1, item.name)
                onNavigate("pdf")
            }
            item.path != null && io.github.thepytales.llmhub.docs.OfficeImport.canOpen(item.name) && item.kind != FileKind.TEXT -> scope.launch {
                val doc = withContext(Dispatchers.IO) { DocsHandoff.importFile(c, File(item.path)) }
                if (doc != null) onOpenDoc(doc.id) else openDocument(context, File(item.path))
            }
            item.path != null -> openDocument(context, File(item.path))
        }
    }

    val actions = FileActions(
        open = { open(it) },
        tags = { tagsFor = it },
        folder = { folderFor = it },
        versions = { versionsOf = it.docId },
        compare = { item ->
            val first = compareFirst
            if (first == null || first.key == item.key) {
                compareFirst = item
                Notices.info(tr("Gemerkt – jetzt bei der zweiten Datei „Vergleichen mit …“ wählen."))
            } else {
                comparison = first to item
                compareFirst = null
            }
        },
        compareFirst = compareFirst,
        convert = { convertFor = it },
    )

    Scaffold(
        topBar = {
            TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(tr("Dateien")) })
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val all = items
            LazyColumn(
                Modifier.widthIn(max = 900.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LORA_BUTTON_ROOM),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, tr("Leeren")) } },
                        placeholder = { Text(tr("Alle Dateien durchsuchen – Namen, Schlagwörter und Inhalt")) },
                    )
                }
                indexing?.let { (d, t) ->
                    item {
                        Column {
                            LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth())
                            HintText(tr("Lese Dateien für die Volltextsuche") + " … $d / $t")
                        }
                    }
                }
                compareFirst?.let { first ->
                    item {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.CompareArrows, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Vergleichen mit") + " „${first.name}“ – " + tr("zweite Datei wählen"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { compareFirst = null }) { Icon(Icons.Outlined.Close, tr("Abbrechen")) }
                            }
                        }
                    }
                }
                if (all == null) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    return@LazyColumn
                }
                val tags = c.fileLibrary.allTags(all)
                if (tags.isNotEmpty() && hits == null) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(30).forEach { t ->
                            FilterChip(tag == t, { tag = if (tag == t) null else t }, label = { Text("#$t") }, leadingIcon = { Icon(Icons.Outlined.Label, null, Modifier.size(16.dp)) })
                        }
                    }
                }
                val found = hits
                when {
                    found != null -> {
                        item { SectionLabel(if (found.isEmpty()) tr("Nichts gefunden") else "${found.size} " + tr("Treffer")) }
                        if (found.isEmpty()) item {
                            HintText(tr("Gesucht wird in Dokumenten, Ablageorten, Projektordnern und der Wissenssammlung – in PDFs, Word, Excel, PowerPoint, OpenDocument, Mails und Textdateien. Scans erst im PDF-Werkzeugkasten durchsuchbar machen."))
                        }
                        items(found, key = { it.item.key }) { h -> FileRow(h.item, actions, hit = h, query = query, onOpen = { open(h.item, h.page) }) }
                    }
                    tag != null -> {
                        val tagged = all.filter { i -> i.tags.any { it.equals(tag, ignoreCase = true) } }
                        item { SectionLabel("#$tag · ${tagged.size}") }
                        items(tagged, key = { it.key }) { i -> FileRow(i, actions) }
                    }
                    place == null -> {
                        item { SectionLabel(tr("Zuletzt bearbeitet")) }
                        if (all.isEmpty()) item {
                            HintText(tr("Noch keine Dateien. Hier erscheinen Dokumente aus dem Editor, Dateien aus Ablageorten und Projektordnern und die Wissenssammlung."))
                        }
                        items(all.take(8), key = { "recent-" + it.key }) { i -> FileRow(i, actions) }
                        item { SectionLabel(tr("Orte")) }
                        val places = all.groupBy { it.place }
                        items(places.keys.sortedWith(compareBy({ it != FileLibrary.DOCUMENTS }, { it != FileSource.KNOWLEDGE.label }, { it.lowercase() })).toList(), key = { "place-$it" }) { p ->
                            FolderRow(p, count(places.getValue(p).size), onClick = { place = p; path = "" })
                        }
                        if (!hasRoots) item {
                            HintText(tr("Tipp: Unter Einstellungen › Dokumente & Logos einen Ablageort wählen oder einem Projekt einen Ordner geben – dann erscheinen seine Dateien hier und werden mit durchsucht."))
                        }
                    }
                    else -> {
                        val inPlace = all.filter { it.place == place }
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { place = null; path = "" }) { Text(tr("Alle Orte")) }
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                                TextButton(onClick = { path = "" }) { Text(place!!) }
                                var acc = ""
                                path.split('/').filter { it.isNotEmpty() }.forEach { seg ->
                                    acc = if (acc.isEmpty()) seg else "$acc/$seg"
                                    val target = acc
                                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                                    TextButton(onClick = { path = target }) { Text(seg) }
                                }
                            }
                        }
                        val prefix = if (path.isEmpty()) "" else "$path/"
                        val subfolders = inPlace.mapNotNull { i -> i.folder.takeIf { it.startsWith(prefix) && it.length > prefix.length }?.removePrefix(prefix)?.substringBefore('/') }
                            .groupingBy { it }.eachCount()
                        items(subfolders.keys.sortedBy { it.lowercase() }, key = { "folder-$path/$it" }) { name ->
                            FolderRow(name, count(inPlace.count { i -> i.folder == prefix + name || i.folder.startsWith("$prefix$name/") }), onClick = { path = prefix + name })
                        }
                        val here = inPlace.filter { it.folder == path }
                        items(here, key = { it.key }) { i -> FileRow(i, actions) }
                        if (here.isEmpty() && subfolders.isEmpty()) item { HintText(tr("Dieser Ordner ist leer.")) }
                        if (place == FileLibrary.DOCUMENTS) item {
                            HintText(tr("Ordner für Dokumente entstehen über „In Ordner …“ am Dokument, z. B. „Lehre/WiSe 2026“."))
                        }
                    }
                }
            }
        }
    }

    convertFor?.let { item ->
        FormatConvertDialog(c, item, onDismiss = { convertFor = null }, onDone = { reload++ })
    }
    tagsFor?.let { item ->
        TagsDialog(item, c.fileLibrary.allTags(items.orEmpty()), onDismiss = { tagsFor = null }, onSave = { c.fileLibrary.setTags(item.key, it); tagsFor = null })
    }
    folderFor?.let { item ->
        val existing = items.orEmpty().filter { it.source == FileSource.APP }.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted()
        FolderDialog(item, existing, onDismiss = { folderFor = null }, onSave = { c.fileLibrary.setFolder(item.key, it); folderFor = null })
    }
    versionsOf?.let { id -> c.docs.get(id)?.let { doc -> VersionCompareDialog(doc, onDismiss = { versionsOf = null }) } ?: run { versionsOf = null } }
    comparison?.let { (a, b) ->
        val texts by produceState<Pair<String?, String?>?>(null, a.key, b.key) {
            value = withContext(Dispatchers.IO) { c.fileLibrary.text(a) to c.fileLibrary.text(b) }
        }
        CompareDialog(
            title = tr("Vergleich"), left = a.name, right = b.name,
            old = texts?.first, new = texts?.second, loading = texts == null, onDismiss = { comparison = null },
        )
    }
}

/** What a line of the Dateiverwaltung can do. */
private class FileActions(
    val open: (FileItem) -> Unit,
    val tags: (FileItem) -> Unit,
    val folder: (FileItem) -> Unit,
    val versions: (FileItem) -> Unit,
    val compare: (FileItem) -> Unit,
    val compareFirst: FileItem?,
    val convert: (FileItem) -> Unit = {},
)

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun FolderRow(name: String, meta: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, tint = Color(0xFFE3A21A), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                HintText(meta)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

/** One file: icon, name, where and when, Schlagwörter; with a search hit its passage (the words in bold) and page. */
@Composable
private fun FileRow(item: FileItem, actions: FileActions, hit: FileHit? = null, query: String = "", onOpen: (() -> Unit)? = null) {
    AppCard(onClick = { (onOpen ?: { actions.open(item) })() }) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = RoundedCornerShape(8.dp), color = item.kind.color().copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(item.kind.icon(), contentDescription = item.kind.label, tint = item.kind.color(), modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                HintText(
                    listOfNotNull(
                        WHEN.format(Date(item.modified)), item.kind.label, item.where + if (item.folder.isNotEmpty() && item.source != FileSource.KNOWLEDGE) " › " + item.folder else "",
                        fileSizeLabel(item.size).takeIf { item.source == FileSource.DISK && it.isNotEmpty() },
                        item.versions.takeIf { it > 1 }?.let { "$it " + tr("Versionen") },
                        hit?.page?.let { tr("Seite") + " $it" },
                    ).joinToString(" · "),
                )
                if (item.tags.isNotEmpty()) Text(item.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (hit != null && hit.snippet.isNotBlank()) {
                    val words = FileLibrary.fold(query).split(Regex("\\s+")).filter { it.length >= 2 }
                    val folded = FileLibrary.fold(hit.snippet)
                    Text(
                        buildAnnotatedString {
                            append(hit.snippet)
                            words.forEach { w ->
                                var i = folded.indexOf(w)
                                while (i >= 0) {
                                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), i, i + w.length)
                                    i = folded.indexOf(w, i + w.length)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Öffnen")) }, onClick = { menu = false; actions.open(item) })
                    DropdownMenuItem(text = { Text(tr("Schlagwörter …")) }, leadingIcon = { Icon(Icons.Outlined.Label, null) }, onClick = { menu = false; actions.tags(item) })
                    if (item.source == FileSource.APP) {
                        DropdownMenuItem(text = { Text(tr("In Ordner …")) }, leadingIcon = { Icon(Icons.Outlined.Folder, null) }, onClick = { menu = false; actions.folder(item) })
                    }
                    if (item.path != null && io.github.thepytales.llmhub.docs.FormatConvert.targets(item.name).isNotEmpty()) {
                        DropdownMenuItem(text = { Text(tr("Umwandeln …")) }, leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) }, onClick = { menu = false; actions.convert(item) })
                    }
                    if (item.versions > 1) {
                        DropdownMenuItem(text = { Text(tr("Versionen vergleichen …")) }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { menu = false; actions.versions(item) })
                    }
                    if (item.kind != FileKind.IMAGE && item.kind != FileKind.OTHER) {
                        val first = actions.compareFirst
                        DropdownMenuItem(
                            text = { Text(if (first != null && first.key != item.key) tr("Vergleichen mit") + " „${first.name.take(30)}“" else tr("Zum Vergleichen merken")) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CompareArrows, null) },
                            onClick = { menu = false; actions.compare(item) },
                        )
                    }
                }
            }
        }
    }
}

/** Schlagwörter of a file: remove (✕), add (Enter or „Hinzufügen“), or pick one already in use. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsDialog(item: FileItem, known: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val tags = remember { mutableStateListOf<String>().apply { addAll(item.tags) } }
    var text by remember { mutableStateOf("") }
    fun add(t: String) {
        val clean = t.trim().removePrefix("#")
        if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) }) tags += clean
        text = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Schlagwörter") + " · " + item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t ->
                        InputChip(selected = true, onClick = { tags.remove(t) }, label = { Text("#$t") }, trailingIcon = { Icon(Icons.Outlined.Close, tr("Entfernen"), Modifier.size(16.dp)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        text, { v -> if (v.endsWith(",") || v.endsWith("\n")) add(v.trimEnd(',', '\n')) else text = v },
                        singleLine = true, placeholder = { Text(tr("z. B. Lehre, Antrag, 2026")) }, modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = text.isNotBlank(), onClick = { add(text) }) { Text(tr("Hinzufügen")) }
                }
                val suggestions = known.filter { k -> tags.none { it.equals(k, ignoreCase = true) } }.take(12)
                if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s -> AssistChip(onClick = { add(s) }, label = { Text("#$s") }) }
                }
            }
        },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) add(text); onSave(tags.toList()) }) { Text(tr("Speichern")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Folder of a document inside the app: a path like „Lehre/WiSe 2026“, existing ones to pick, empty = top level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDialog(item: FileItem, existing: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(item.folder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("In Ordner") + " · " + item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(tr("Ordner")) }, placeholder = { Text(tr("z. B. Lehre/WiSe 2026")) }, modifier = Modifier.fillMaxWidth())
                if (existing.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    existing.forEach { f -> FilterChip(text == f, { text = f }, label = { Text(f) }, leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) }) }
                }
                HintText(tr("Leer lassen für die oberste Ebene. Unterordner mit „/“."))
            }
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text(tr("Verschieben")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Two versions of a document side by side as differences: choose the older and the newer one. */
@Composable
internal fun VersionCompareDialog(doc: io.github.thepytales.llmhub.docs.LiveDoc, onDismiss: () -> Unit) {
    val versions = doc.versions
    var a by remember { mutableIntStateOf((versions.size - 2).coerceAtLeast(0)) }
    var b by remember { mutableIntStateOf(versions.lastIndex) }
    fun label(i: Int) = versions.getOrNull(i)?.let { v -> WHEN.format(Date(v.createdAt)) + " · " + v.author + if (i == versions.lastIndex) " (" + tr("aktuell") + ")" else "" }.orEmpty()
    fun text(i: Int) = versions.getOrNull(i)?.tabs?.joinToString("\n\n") { it.markdown }.orEmpty()
    CompareDialog(
        title = tr("Versionen vergleichen") + " · " + doc.title,
        left = label(a), right = label(b), old = text(a), new = text(b), loading = false, onDismiss = onDismiss,
        pickers = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionPicker(tr("Ältere"), versions.indices.toList(), a, ::label) { a = it }
                VersionPicker(tr("Neuere"), versions.indices.toList(), b, ::label) { b = it }
            }
        },
    )
}

@Composable
private fun VersionPicker(title: String, choices: List<Int>, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$title: " + label(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.asReversed().forEach { i -> DropdownMenuItem(text = { Text(label(i)) }, onClick = { onPick(i); open = false }) }
        }
    }
}

/** Differences of two texts word by word: green = only in the right one, red and struck through = only in the left one. */
@Composable
internal fun CompareDialog(
    title: String,
    left: String,
    right: String,
    old: String?,
    new: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    pickers: (@Composable () -> Unit)? = null,
) {
    val pieces by produceState<List<TextDiff.Piece>?>(null, old, new) {
        value = if (old == null || new == null) null else withContext(Dispatchers.Default) { TextDiff.words(old, new) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pickers?.invoke()
                HintText(tr("Links") + ": $left\n" + tr("Rechts") + ": $right")
                HorizontalDivider()
                val list = pieces
                when {
                    loading || (list == null && old != null && new != null) -> CircularProgressIndicator(Modifier.size(24.dp))
                    old == null || new == null -> Text(tr("Von einer der beiden Dateien ist kein Text lesbar (z. B. ein Scan oder ein Bild)."), style = MaterialTheme.typography.bodySmall)
                    list != null -> {
                        HintText(TextDiff.summary(list) + " · " + tr("grün = nur rechts, rot = nur links"))
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(buildAnnotatedString {
                                    list.forEach { p ->
                                        when (p.op) {
                                            TextDiff.Op.SAME -> append(p.text)
                                            TextDiff.Op.ADDED -> withStyle(SpanStyle(background = Color(0x3325A35A), color = Color(0xFF1B7A43))) { append(p.text) }
                                            TextDiff.Op.REMOVED -> withStyle(SpanStyle(background = Color(0x33D32F2F), color = Color(0xFFB3261E), textDecoration = TextDecoration.LineThrough)) { append(p.text) }
                                        }
                                    }
                                }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}
