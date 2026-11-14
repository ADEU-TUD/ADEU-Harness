package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.docs.AiReview
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocCitations
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.DocStats
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SpellCheck
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.docs.DocComment
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.DocReply
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTab
import io.github.thepytales.llmhub.docs.DocVersion
import io.github.thepytales.llmhub.docs.DocsLive
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetTools
import io.github.thepytales.llmhub.docs.TextDiff
import io.github.thepytales.llmhub.docs.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Opens documents in the editor from anywhere (document cards, literature, chat answers, attachments). */
object DocsHandoff {
    /** Id of the document to open, or "chat:<id>" for the document the model is writing in that chat. */
    val request = MutableStateFlow<String?>(null)

    /** The chat screen shows documents beside the chat on wide screens; returns false if it cannot. */
    @Volatile
    var sidePanel: ((String) -> Boolean)? = null

    fun show(id: String) {
        if (sidePanel?.invoke(id) != true) request.value = id
    }

    /** Creates a document from [markdown] and opens it. */
    fun open(c: AppContainer, title: String, markdown: String, chatId: String? = null, kind: DocKind = DocKind.DOC): LiveDoc {
        val doc = LiveDoc.create(title, markdown, "Sie", chatId, kind = kind).copy(vault = c.docs.defaultVault)
        c.docs.save(doc)
        show(doc.id)
        return doc
    }

    /**
     * Imports a file (Word, PowerPoint, Excel, Markdown, text …) to keep working on it in the editor with the model;
     * a file that is already there is returned instead of imported twice. Reads the file: call it off the main thread.
     */
    fun importFile(c: AppContainer, file: File, chatId: String? = null): LiveDoc? {
        c.docs.forFile(file.absolutePath)?.let { return it }
        val imported = runCatching { OfficeImport.read(file.name, file.readBytes()) }.getOrNull() ?: return null
        val doc = OfficeImport.toLiveDoc(imported, chatId = chatId).copy(file = file.absolutePath, vault = c.docs.defaultVault)
        saveMedia(c, doc, imported)
        c.docs.save(doc)
        return doc
    }

    /** Pictures of an imported file go next to the document (Bilder/…), where its Markdown points. */
    fun saveMedia(c: AppContainer, doc: LiveDoc, imported: OfficeImport.Imported) {
        val base = c.docs.baseDir(doc)
        imported.media.forEach { (path, bytes) -> runCatching { File(base, path).apply { parentFile?.mkdirs() }.writeBytes(bytes) } }
    }

    /** An attachment of a chat message (its text was read when it was attached). */
    fun openAttachment(c: AppContainer, file: FileAttachment, chatId: String?) {
        val kind = DocKind.forFile(file.name)
        val title = file.name.substringBeforeLast('.').removePrefix("Dokument – ")
        val markdown = when {
            kind == DocKind.SHEET && file.name.endsWith(".csv", true) -> OfficeImport.csv(file.text)
            kind == DocKind.SHEET && !file.text.trimStart().startsWith("|") -> SheetEngine.markdown(file.text.lines().filter { it.isNotBlank() }.map { l -> l.split('\t', ';').map(String::trim) })
            else -> file.text
        }
        open(c, title, markdown, chatId, kind)
    }
}

private fun time(millis: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** The editor on its own screen (phones) or as route "doc/{id}". */
@Composable
fun DocScreen(c: AppContainer, id: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    Scaffold { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            DocPane(c, id, onClose = onBack, onOpenChat = onOpenChat)
        }
    }
}

/** Side panels of the editor (beside the page on wide screens, as a sheet on phones). */
private enum class DocPanel { NONE, AI, COMMENTS, OUTLINE }

/**
 * The editor for documents, presentations and tables (like Google Docs, PowerPoint and Excel, with the model as
 * co-author): same header, tabs, comments, version history, export and assistant for all three; the body differs.
 * While the model writes, the text grows on screen; its changes are highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocPane(c: AppContainer, id: String, onClose: () -> Unit, onOpenChat: ((String) -> Unit)? = null, inSidePanel: Boolean = false) {
    val docs by c.docs.items.collectAsState()
    val drafts by DocsLive.drafts.collectAsState()
    val runs by c.runs.runs.collectAsState()
    val chatId = id.removePrefix("chat:").takeIf { id.startsWith("chat:") }
    val doc = if (chatId != null) docs.filter { it.chatId == chatId }.maxByOrNull { it.updatedAt } else docs.firstOrNull { it.id == id }
    val draft = (chatId ?: doc?.chatId)?.let { drafts[it] }
    val writing = draft != null
    val aiWorking = doc?.chatId?.let { runs[it] } != null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tabId by rememberSaveable(doc?.id) { mutableStateOf(doc?.tabs?.firstOrNull()?.id) }
    var mode by rememberSaveable(doc?.id) { mutableStateOf(TextMode.EDIT) }
    var panel by rememberSaveable { mutableStateOf(DocPanel.NONE) }
    var loraShown by rememberSaveable { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var commentOn by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var export by remember { mutableStateOf<Pair<String, String>?>(null) }
    var sendToChat by remember { mutableStateOf(false) }
    var chooseVault by remember { mutableStateOf(false) }
    var chooseStyle by remember { mutableStateOf(false) }
    var dismissedAi by remember { mutableStateOf<String?>(null) }
    /** ✨ menu open for this passage. */
    var aiMenuFor by remember { mutableStateOf<String?>(null) }
    var pageSetup by remember { mutableStateOf(false) }
    var stopTracking by remember { mutableStateOf(false) }
    var spellLocal by remember { mutableStateOf(false) }
    var citing by remember { mutableStateOf(false) }
    var spellFor by remember { mutableStateOf<String?>(null) }
    var deckFromDoc by remember { mutableStateOf(false) }
    /** Menüs ausblenden (APD-15, Strg+Umschalt+F like Google Docs): only the Formatleiste stays above the page. */
    var menusHidden by rememberSaveable { mutableStateOf(false) }
    /** Changes of the model the user has accepted (block texts), per version of the model. */
    val acceptedAi = remember(doc?.versions?.lastOrNull { it.byAi }?.id) { mutableStateListOf<String>() }
    val undo = remember(doc?.id) { mutableStateListOf<List<DocTab>>() }
    val redo = remember(doc?.id) { mutableStateListOf<List<DocTab>>() }
    val editing = remember(doc?.id, tabId) { TextEditing() }
    val inlineAi = rememberInlineAi(c)
    val listState = rememberLazyListState()
    val docBase = remember(doc?.id, doc?.vault) { c.docs.baseDir(doc) }
    /** The Fließtext-Editor (AP-44) – the editor of text documents unless changes wait for review. */
    var flowHost by remember(doc?.id, tabId) { mutableStateOf(FlowHost()) }

    // Documents in a storage folder: pick up what changed there (another computer, another editor).
    LaunchedEffect(doc?.vault) {
        if (doc?.vault != null) while (true) {
            delay(4000)
            withContext(Dispatchers.IO) { runCatching { c.docs.refresh() } }
        }
    }

    if (doc == null && draft == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(tr("Dokument nicht gefunden"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose) { Text(tr("Schließen")) }
        }
        return
    }

    val kind = doc?.docKind ?: DocKind.DOC
    val tab = doc?.tab(tabId)
    val markdown = when {
        writing && (doc == null || draft?.docId == null && doc.versions.size <= 1 && chatId != null) -> draft!!.markdown
        else -> tab?.markdown.orEmpty()
    }
    val showingDraft = writing && markdown == draft?.markdown
    val blocks = remember(markdown) { DocBlocks.split(markdown) }
    // What the model changed last (highlighted until the user dismisses it or edits): compared with the version before
    // the model's, as long as only reviews of its changes ("KI-Änderung …") came after it.
    val versions = doc?.versions.orEmpty()
    val aiAt = versions.indexOfLast { it.byAi }
    val lastVersion = versions.getOrNull(aiAt)
    val trackBase = tab?.let { t -> doc?.trackBase?.get(t.id) }
    val review = if (trackBase != null) remember(trackBase, tab?.markdown) { io.github.thepytales.llmhub.docs.Tracking.review(trackBase, tab!!.markdown) }
    else remember(versions.size, tabId, tab?.markdown) {
        if (aiAt >= 1 && tab != null && versions.drop(aiAt + 1).all { it.note.startsWith("KI-Änderung") }) {
            val before = versions[aiAt - 1].tabs.firstOrNull { it.id == tab.id }?.markdown.orEmpty()
            AiReview.compare(before.replace(EMPTY_BLOCK, ""), tab.markdown.replace(EMPTY_BLOCK, ""))
        } else null
    }?.takeIf { lastVersion?.id != dismissedAi }?.let { r ->
        r.copy(changes = r.changes.filter { blocks.getOrNull(it.index) !in acceptedAi }, removed = r.removed.filter { it.text !in acceptedAi })
    }
    val aiChanged = review?.changes?.map { it.index }?.toSet().orEmpty()

    fun save(author: String = "Sie", note: String = "", transform: (LiveDoc) -> LiveDoc) {
        val d = c.docs.get(doc?.id) ?: return
        undo += d.tabs
        if (undo.size > 80) undo.removeAt(0)
        // Large tables: keep only as many steps as fit into about 60 million characters.
        while (undo.size > 1 && undo.sumOf { tabs -> tabs.sumOf { it.markdown.length.toLong() } } > 60_000_000L) undo.removeAt(0)
        redo.clear()
        c.docs.save(d.change(author, note, transform))
    }

    fun setMarkdown(md: String) {
        val t = tab ?: return
        if (md == t.markdown) return
        save { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(markdown = md) else it }) }
    }

    fun setBlocks(newBlocks: List<String>) = setMarkdown(DocBlocks.join(newBlocks))

    // Charts linked from a table (AP-49): drawn from the table's current data when the document is opened.
    LaunchedEffect(doc?.id) {
        val d = doc ?: return@LaunchedEffect
        if (d.docKind == DocKind.SHEET) return@LaunchedEffect
        val fresh = withContext(Dispatchers.Default) { runCatching { io.github.thepytales.llmhub.docs.ChartLinks.refreshDoc(d) { id -> c.docs.get(id) } }.getOrNull() }
        if (fresh != null) save(note = tr("Verknüpfte Diagramme aktualisiert")) { x -> x.copy(tabs = x.tabs.map { t -> fresh.tabs.firstOrNull { it.id == t.id } ?: t }) }
    }

    fun reviewAction(action: ReviewAction) {
        val t = tab ?: return
        fun set(list: List<String>, note: String) = save(note = note) { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(markdown = DocBlocks.join(list)) else it }) }
        editing.index = null
        // Änderungen nachverfolgen: accepting moves the change into the base.
        if (trackBase != null) {
            fun base(next: String, note: String) = save(note = note) { d -> d.copy(trackBase = d.trackBase.orEmpty() + (t.id to next)) }
            when (action) {
                is ReviewAction.Accept -> base(io.github.thepytales.llmhub.docs.Tracking.baseAfter(trackBase, t.markdown, accepted = action.change), trLater("Änderung angenommen"))
                is ReviewAction.Reject -> set(AiReview.reject(blocks, action.change), trLater("Änderung abgelehnt"))
                is ReviewAction.Restore -> set(AiReview.restore(blocks, action.removed), trLater("Löschung abgelehnt"))
                is ReviewAction.Drop -> base(io.github.thepytales.llmhub.docs.Tracking.baseAfter(trackBase, t.markdown, dropped = action.removed), trLater("Löschung angenommen"))
            }
            return
        }
        when (action) {
            is ReviewAction.Accept -> blocks.getOrNull(action.change.index)?.let { acceptedAi += it }
            is ReviewAction.Reject -> set(AiReview.reject(blocks, action.change), trLater("KI-Änderung abgelehnt"))
            is ReviewAction.Restore -> set(AiReview.restore(blocks, action.removed), trLater("KI-Änderung zurückgenommen"))
            is ReviewAction.Drop -> acceptedAi += action.removed.text
        }
    }

    /** Inserts [block] after the edited block (or at the end) and opens it. */
    fun insertBlock(block: String) {
        // Fließtext-Editor (AP-44): at its cursor.
        flowHost.insertBlock?.let { it(block); return }
        val list = blocks.toMutableList()
        val i = editing.index
        if (i != null && i in list.indices) list[i] = editing.value.text.ifBlank { EMPTY_BLOCK }
        val at = ((i ?: list.lastIndex) + 1).coerceIn(0, list.size)
        list.add(at, block)
        setBlocks(list)
        editing.start(at, block)
    }

    /** A citation at the cursor and the work in the generated literature list at the end. */
    fun cite(ref: io.github.thepytales.llmhub.research.Reference, style: CitationStyle) {
        val t = tab ?: return
        // Fließtext-Editor (AP-44): the citation at its cursor, the literature list at the end.
        flowHost.markdown?.let { current ->
            val refs0 = (DocCitations.cited(current()).ids.mapNotNull { c.literature.get(it) } + ref).distinctBy { it.id }
            val citation = DocCitations.inText(ref, style, DocCitations.number(refs0, ref, style))
            flowHost.insertText?.invoke(" $citation")
            val md = DocCitations.withBibliography(current(), refs0, style)
            save(note = tr("Beleg eingefügt")) { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(markdown = md) else it }) }
            Notices.info(tr("Beleg eingefügt: {0}", citation))
            return
        }
        val list = blocks.toMutableList()
        val i = editing.index
        val md0 = DocBlocks.join(list.mapIndexed { k, b -> if (k == i) editing.value.text else b })
        val cited = DocCitations.cited(md0)
        val refs = (cited.ids.mapNotNull { c.literature.get(it) } + ref).distinctBy { it.id }
        val citation = DocCitations.inText(ref, style, DocCitations.number(refs, ref, style))
        if (i != null && i in list.indices) {
            val v = editing.value
            val at = v.selection.max
            val spacer = if (at > 0 && !v.text[at - 1].isWhitespace()) " " else ""
            list[i] = v.text.substring(0, at) + spacer + citation + v.text.substring(at)
        } else {
            val last = list.indexOfLast { DocBlocks.kindOf(it) == DocBlocks.Kind.PARAGRAPH && !DocMarkup.isComment(it) }
            if (last >= 0) list[last] = list[last].trimEnd() + " " + citation else list += citation
        }
        editing.index = null
        val md = DocCitations.withBibliography(DocBlocks.join(list), refs, style)
        save(note = tr("Beleg eingefügt")) { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(markdown = md) else it }) }
        Notices.info(tr("Beleg eingefügt: {0}", citation))
    }

    val pickImage = rememberImageBytesPicker { bytes ->
        if (bytes == null || DocMarkup.imageType(bytes) == null) { if (bytes != null) Notices.error(tr("Das Bild konnte nicht gelesen werden (PNG, JPEG, GIF oder BMP).")); return@rememberImageBytesPicker }
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { DocMarkup.storeImage(docBase, bytes) }.getOrNull() }
            if (path == null) Notices.error(tr("Das Bild konnte nicht gespeichert werden."))
            else insertBlock(DocMarkup.Image("Bildunterschrift", path, 60).markdown())
        }
    }

    fun undoOnce() {
        val d = c.docs.get(doc?.id) ?: return
        val last = undo.removeLastOrNull() ?: return
        redo += d.tabs
        editing.index = null
        c.docs.save(d.change("Sie", trLater("Rückgängig")) { it.copy(tabs = last) })
    }

    fun redoOnce() {
        val d = c.docs.get(doc?.id) ?: return
        val next = redo.removeLastOrNull() ?: return
        undo += d.tabs
        editing.index = null
        c.docs.save(d.change("Sie", "Wiederholt") { it.copy(tabs = next) })
    }

    /** The marked passage for requests to the model: selection, edited block or nothing. */
    fun selection(): String? = editing.selection ?: editing.index?.let { blocks.getOrNull(it) }

    fun togglePanel(p: DocPanel) { panel = if (panel == p) DocPanel.NONE else p }

    // Fließtext-Editor (AP-44) for text documents; the block view stays for reviewing changes of the model.
    // Änderungen nachverfolgen shows in the running text too (AP-44), unless tables, pictures or formulas changed.
    val trackInFlow = trackBase != null && remember(trackBase, tab?.markdown) { FlowState.trackable(trackBase, tab?.markdown.orEmpty()) }
    val flow = kind == DocKind.DOC && doc != null && mode == TextMode.EDIT && !showingDraft && !writing &&
        (if (trackBase != null) trackInFlow else review == null || review.changes.isEmpty() && review.removed.isEmpty())
    /** The document as it is now (callbacks remembered once per document must not export or copy an old state). */
    val liveDoc by rememberUpdatedState(doc)
    val closeNow by rememberUpdatedState(onClose)
    /** What *Datei* of the Fließtext-Editor does (APD-15): the former „⋮“ menu, on the document as it is now. */
    val fileActions = remember(doc?.id) {
        DocFileActions(
            rename = { renaming = true },
            duplicate = {
                liveDoc?.let { d ->
                    val copy = LiveDoc.create(d.title + tr(" (Kopie)"), d.markdown(), "Sie", kind = d.docKind)
                        .copy(tabs = d.tabs.map { it.copy(id = java.util.UUID.randomUUID().toString()) }, vault = d.vault)
                    c.docs.save(copy)
                    DocsHandoff.show(copy.id)
                }
            },
            star = { liveDoc?.let { d -> c.docs.update(d.id) { it.copy(starred = !it.starred) } } },
            vault = { chooseVault = true },
            export = { tabOnly ->
                liveDoc?.let { d ->
                    val t = d.tab(tabId)
                    export = if (tabOnly && t != null) "${d.title} – ${t.title}" to DocMarkup.resolveImages(exportMarkdown(d, t.id), docBase)
                    else d.title to DocMarkup.resolveImages(exportMarkdown(d, null), docBase)
                }
            },
            sendToChat = { sendToChat = true },
            shareText = { liveDoc?.let { d -> shareText(context, exportMarkdown(d, null), d.title) } },
            deck = { deckFromDoc = true },
            pageSetup = { pageSetup = true },
            design = { chooseStyle = true },
            history = { showHistory = true },
            delete = {
                liveDoc?.let { d ->
                    c.docs.delete(d.id)
                    closeNow()
                    Notices.undo(tr("{0} gelöscht", d.docKind.label), restore = { c.docs.save(d) })
                }
            },
        )
    }
    // The Fließtext-Editor's connection to this window (APD-15): made before the header, which shows the editor's
    // menus and Formatleiste through it.
    val hostNow = remember(doc?.id, tabId) {
        FlowHost(
            onComment = { commentOn = it },
            onImage = pickImage,
            onCitation = { citing = true },
            onFind = { findOpen = true },
            onExport = { liveDoc?.let { d -> export = d.title to DocMarkup.resolveImages(exportMarkdown(d, null), docBase) } },
            onPageSetup = { pageSetup = true },
            onHistory = { showHistory = true },
            onOutline = { panel = DocPanel.OUTLINE },
            onSource = { mode = TextMode.SOURCE },
            onRead = { mode = TextMode.READ },
            onSpell = { spellLocal = true },
            onTrack = { if (liveDoc?.trackBase == null) save(note = tr("Änderungen nachverfolgen an")) { d -> d.copy(trackBase = d.tabs.associate { it.id to it.markdown }) } else stopTracking = true },
            onAskAi = { panel = DocPanel.AI },
            onShowComments = { panel = DocPanel.COMMENTS },
            // APD-15: everything of the former „⋮“ menu in *Datei*, like Google Docs.
            fileMenu = { close -> liveDoc?.let { d -> FlowFileMenu(c, d, liveTab = tabId, close = close, actions = fileActions) } },
            onAddTab = {
                liveDoc?.let { d ->
                    val new = DocTab(title = tr("Tab {0}", d.tabs.size + 1))
                    save { x -> x.copy(tabs = x.tabs + new) }
                    tabId = new.id
                }
            },
            onSpellAi = { spellFor = (flowHost.markdown?.invoke() ?: liveDoc?.tab(tabId)?.markdown).orEmpty().replace(EMPTY_BLOCK, "") },
        )
    }
    if (flowHost !== hostNow) flowHost = hostNow

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        val narrow = maxWidth < 600.dp
        val wide = maxWidth > 980.dp
        // Lora sits beside the document on wide screens from the start (AP-16, like Google Docs); closable.
        LaunchedEffect(doc?.id, wide) { if (wide && doc != null && !loraShown && panel == DocPanel.NONE && (kind == DocKind.DOC || kind == DocKind.MARKDOWN || kind == DocKind.SLIDES)) { panel = DocPanel.AI; loraShown = true } }
        val undoRedo: @Composable () -> Unit = {
            if (doc != null && narrow) {
                ToolIcon(Icons.AutoMirrored.Outlined.Undo, tr("Rückgängig (Strg+Z)"), enabled = undo.isNotEmpty()) { undoOnce() }
                ToolIcon(Icons.AutoMirrored.Outlined.Redo, tr("Wiederholen (Strg+Y)"), enabled = redo.isNotEmpty()) { redoOnce() }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCmd || doc == null) return@onPreviewKeyEvent false
                // In the Fließtext-Editor Strg+Z/Y undo keystroke by keystroke and Strg+F/H open its search (like Word); the
                // window's own Rückgängig (whole saved states) is for the other kinds of documents (APD-15).
                when (e.key) {
                    Key.Z -> { if (flow) { if (e.isShiftPressed) flowHost.redo?.invoke() else flowHost.undo?.invoke() } else if (e.isShiftPressed) redoOnce() else undoOnce(); true }
                    Key.Y -> { if (flow) flowHost.redo?.invoke() else redoOnce(); true }
                    Key.F, Key.H -> { if (flow && e.isShiftPressed && e.key == Key.F) menusHidden = !menusHidden else if (flow) flowHost.find?.invoke() else findOpen = true; true }
                    Key.E -> if (flow && e.isShiftPressed) false else { togglePanel(DocPanel.AI); true }
                    else -> false
                }
            },
        ) {
            // ── Header: title, status, actions ──
            if (flow && doc != null) { if (!menusHidden) FlowDocHeader(
                doc = doc, narrow = narrow, inSidePanel = inSidePanel, onClose = onClose, onRename = { renaming = true },
                status = (if (aiWorking) tr("KI arbeitet am {0} …", tr(kind.label)) else tr("Gespeichert") + (doc.vault?.let { v -> c.docs.vault(v)?.let { tr(" in „{0}“", it.name) } }.orEmpty()) + " · " + time(doc.updatedAt)),
                busy = aiWorking,
                tracking = doc.trackBase?.let { io.github.thepytales.llmhub.docs.Tracking.pending(doc) },
                onAcceptAll = { save(note = tr("Alle Änderungen angenommen")) { d -> d.copy(trackBase = d.tabs.associate { it.id to it.markdown }) } },
                menus = flowHost.menus,
                onComments = { togglePanel(DocPanel.COMMENTS) }, onAi = { togglePanel(DocPanel.AI) },
                onExport = { (liveDoc ?: doc).let { d -> export = d.title to DocMarkup.resolveImages(exportMarkdown(d, null), docBase) } },
            ) } else
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(if (inSidePanel) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = if (inSidePanel) tr("Schließen") else tr("Zurück")) }
                Column(Modifier.weight(1f).clickable(enabled = doc != null) { renaming = true }) {
                    Text(doc?.title ?: draft?.title?.ifBlank { null } ?: tr("Neues Dokument"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        writing -> "KI schreibt …"
                        aiWorking -> "KI arbeitet am ${kind.label} …"
                        doc != null -> kind.label + tr(" · Gespeichert") + (doc.vault?.let { v -> c.docs.vault(v)?.let { tr(" in „{0}“", it.name) } }.orEmpty()) + " · " + time(doc.updatedAt)
                        else -> ""
                    }
                    Text(
                        status, style = MaterialTheme.typography.labelSmall, color = if (writing || aiWorking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (doc != null) {
                    if (!narrow) {
                        IconButton(onClick = ::undoOnce, enabled = undo.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = tr("Rückgängig")) }
                        IconButton(onClick = ::redoOnce, enabled = redo.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = tr("Wiederholen")) }
                    }
                    IconButton(onClick = { togglePanel(DocPanel.AI) }) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = tr("KI-Assistent (Strg+E)"), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { togglePanel(DocPanel.COMMENTS) }) {
                        BadgedBox(badge = { if (doc.openComments > 0) Badge { Text("${doc.openComments}") } }) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = tr("Kommentare"))
                        }
                    }
                    var more by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr")) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text(tr("Exportieren …")) }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = {
                                more = false
                                export = doc.title to DocMarkup.resolveImages(exportMarkdown(doc, null), docBase)
                            })
                            if (doc.tabs.size > 1 && tab != null) DropdownMenuItem(text = { Text(tr("Nur „{0}“ exportieren", tab.title)) }, onClick = {
                                more = false
                                export = "${doc.title} – ${tab.title}" to DocMarkup.resolveImages(exportMarkdown(doc, tab.id), docBase)
                            })
                            // In Word/Excel/PowerPoint öffnen und zurück (AP-59, Windows): the real file in its program.
                            val real = remember(doc.id, doc.path) { c.docs.realFile(doc)?.takeIf { it.isFile } }
                            if (real != null && ExternalOffice.opener != null) ExternalOffice.program(real)?.let { prog ->
                                DropdownMenuItem(text = { Text(tr("In {0} öffnen", prog)) }, onClick = {
                                    more = false
                                    if (ExternalOffice.opener?.invoke(real) != true) Notices.error(tr("„{0}“ ließ sich nicht öffnen.", real.name))
                                })
                            }
                            DropdownMenuItem(text = { Text(tr("In Chat senden …")) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Send, null) }, onClick = { more = false; sendToChat = true })
                            DropdownMenuItem(text = { Text(tr("Als Text teilen")) }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) }, onClick = {
                                more = false
                                shareText(context, exportMarkdown(doc, null), doc.title)
                            })
                            HorizontalDivider()
                            if (kind == DocKind.DOC) DropdownMenuItem(
                                text = { Text(tr("Design: ") + (DocStyle.parse(doc.style)?.label ?: tr("wie in den Einstellungen"))) },
                                leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                onClick = { more = false; chooseStyle = true },
                            )
                            if (kind == DocKind.MARKDOWN) DropdownMenuItem(text = { Text(tr("Gliederung & Verknüpfungen")) }, leadingIcon = { Icon(Icons.Outlined.Toc, null) }, onClick = { more = false; togglePanel(DocPanel.OUTLINE) })
                            if (kind == DocKind.DOC) {
                                DropdownMenuItem(text = { Text(tr("Gliederung & Verknüpfungen")) }, leadingIcon = { Icon(Icons.Outlined.Toc, null) }, onClick = { more = false; togglePanel(DocPanel.OUTLINE) })
                                TextMode.entries.filter { it != mode }.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(if (m == TextMode.EDIT) tr("Bearbeiten") else if (m == TextMode.SOURCE) tr("Markdown-Quelltext bearbeiten") else tr("Lesemodus")) },
                                        leadingIcon = { Icon(if (m == TextMode.SOURCE) Icons.Outlined.Code else if (m == TextMode.READ) Icons.Outlined.MenuBook else Icons.Outlined.Edit, null) },
                                        onClick = { more = false; editing.index = null; mode = m },
                                    )
                                }
                            }
                            if (kind == DocKind.DOC) {
                                DropdownMenuItem(text = { Text(tr("Seite einrichten …")) }, onClick = { more = false; pageSetup = true })
                                DropdownMenuItem(text = { Text(tr("Literatur einfügen …")) }, onClick = { more = false; citing = true })
                                DropdownMenuItem(text = { Text(tr("Rechtschreibung prüfen")) }, onClick = { more = false; editing.index = null; spellLocal = true })
                                DropdownMenuItem(text = { Text(tr("Rechtschreibung prüfen (KI)")) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = {
                                    more = false
                                    spellFor = selection()?.takeIf { it.isNotBlank() && editing.selection != null } ?: markdown.replace(EMPTY_BLOCK, "")
                                })
                                DropdownMenuItem(text = { Text(tr("Präsentation aus Dokument …")) }, leadingIcon = { Icon(Icons.Outlined.Slideshow, null) }, onClick = { more = false; deckFromDoc = true })
                            }
                            DropdownMenuItem(text = { Text(tr("Suchen & Ersetzen")) }, leadingIcon = { Icon(Icons.Outlined.FindReplace, null) }, onClick = { more = false; findOpen = true })
                            DropdownMenuItem(text = { Text(tr("Versionsverlauf")) }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { more = false; showHistory = true })
                            if (kind == DocKind.DOC || kind == DocKind.MARKDOWN) DropdownMenuItem(
                                text = { Text(if (doc.trackBase != null) tr("Änderungen nicht mehr nachverfolgen") else tr("Änderungen nachverfolgen")) },
                                leadingIcon = { Icon(Icons.Outlined.History, null) },
                                onClick = {
                                    more = false
                                    if (doc.trackBase == null) save(note = tr("Änderungen nachverfolgen an")) { d -> d.copy(trackBase = d.tabs.associate { it.id to it.markdown }) }
                                    else stopTracking = true
                                },
                            )
                            if (kind == DocKind.DOC) DropdownMenuItem(text = { Text(tr("Word mit Änderungsverfolgung …")) }, onClick = { more = false; showHistory = true })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (doc.starred) tr("Nicht mehr anheften") else tr("Anheften")) },
                                leadingIcon = { Icon(if (doc.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null) },
                                onClick = { more = false; c.docs.update(doc.id) { it.copy(starred = !it.starred) } },
                            )
                            if (FolderAccess.SUPPORTED || doc.vault != null) DropdownMenuItem(
                                text = { Text(tr("Ablageort …")) }, leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                onClick = { more = false; chooseVault = true },
                            )
                            DropdownMenuItem(text = { Text(tr("Duplizieren")) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                                more = false
                                val copy = LiveDoc.create(doc.title + " (Kopie)", doc.markdown(), "Sie", kind = kind)
                                    .copy(tabs = doc.tabs.map { it.copy(id = java.util.UUID.randomUUID().toString()) }, vault = doc.vault)
                                c.docs.save(copy)
                                DocsHandoff.show(copy.id)
                            })
                            doc.chatId?.takeIf { onOpenChat != null && !inSidePanel }?.let { cid ->
                                DropdownMenuItem(text = { Text(tr("Chat zum {0} öffnen", kind.label)) }, onClick = { more = false; onOpenChat?.invoke(cid) })
                            }
                            DropdownMenuItem(colors = dangerMenuColors(), text = { Text(tr("Löschen")) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = {
                                more = false
                                val copy = doc
                                c.docs.delete(doc.id)
                                onClose()
                                Notices.undo(tr("{0} gelöscht", kind.label), restore = { c.docs.save(copy) })
                            })
                        }
                    }
                }
            }
            // ── Tabs (sheets of a table) ──
            if (doc != null && (if (flow) doc.tabs.size > 1 else kind != DocKind.SLIDES || doc.tabs.size > 1)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    doc.tabs.forEach { t ->
                        var tabMenu by remember { mutableStateOf(false) }
                        var renameTab by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = t.id == tab?.id, onClick = { if (t.id == tab?.id) tabMenu = true else { editing.index = null; tabId = t.id } },
                                label = { Text(t.title, maxLines = 1) },
                            )
                            DropdownMenu(expanded = tabMenu, onDismissRequest = { tabMenu = false }) {
                                DropdownMenuItem(text = { Text(tr("Umbenennen")) }, onClick = { tabMenu = false; renameTab = true })
                                DropdownMenuItem(text = { Text(tr("Duplizieren")) }, onClick = {
                                    tabMenu = false
                                    val copy = DocTab(title = t.title + tr(" (Kopie)"), markdown = t.markdown)
                                    save { d -> d.copy(tabs = d.tabs.toMutableList().apply { add(indexOfFirst { it.id == t.id } + 1, copy) }) }
                                    tabId = copy.id
                                })
                                if (doc.tabs.size > 1) DropdownMenuItem(colors = dangerMenuColors(), text = { Text(tr("Löschen")) }, onClick = {
                                    tabMenu = false
                                    save { d -> d.copy(tabs = d.tabs.filterNot { it.id == t.id }) }
                                    tabId = doc.tabs.firstOrNull { it.id != t.id }?.id
                                })
                            }
                        }
                        if (renameTab) TextPrompt(if (kind == DocKind.SHEET) tr("Tabellenblatt umbenennen") else tr("Tab umbenennen"), t.title, onDismiss = { renameTab = false }) { name ->
                            save { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(title = name) else it }) }
                            renameTab = false
                        }
                    }
                    IconButton(onClick = {
                        val new = when (kind) {
                            DocKind.SHEET -> DocTab(title = tr("Tabelle {0}", doc.tabs.size + 1), markdown = SheetEngine.blank())
                            DocKind.SLIDES -> DocTab(title = tr("Folien {0}", doc.tabs.size + 1), markdown = io.github.thepytales.llmhub.docs.SlideDeck.blank(tr("Titel")))
                            DocKind.DOC, DocKind.MARKDOWN -> DocTab(title = tr("Tab {0}", doc.tabs.size + 1))
                        }
                        save { d -> d.copy(tabs = d.tabs + new) }
                        editing.index = null
                        tabId = new.id
                    }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Add, contentDescription = if (kind == DocKind.SHEET) tr("Tabellenblatt hinzufügen") else tr("Tab hinzufügen"), modifier = Modifier.size(18.dp)) }
                    if (flow) Unit
                    else if (doc?.trackBase != null) {
                        Spacer(Modifier.width(8.dp))
                        val open = io.github.thepytales.llmhub.docs.Tracking.pending(doc)
                        StatusPill(if (open == 0) tr("Änderungen werden nachverfolgt") else tr("{0} Änderungen offen · alle annehmen", open), PillTone.ACCENT, Modifier.clickable(enabled = open > 0) {
                            save(note = tr("Alle Änderungen angenommen")) { d -> d.copy(trackBase = d.tabs.associate { it.id to it.markdown }) }
                        })
                    } else if (aiChanged.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill(tr("{0} Änderungen der KI markiert ✕", aiChanged.size), PillTone.ACCENT, Modifier.clickable { dismissedAi = lastVersion?.id })
                    }
                }
            }
            if (findOpen && doc != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(find, { find = it }, singleLine = true, placeholder = { Text(tr("Suchen")) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(replace, { replace = it }, singleLine = true, placeholder = { Text(tr("Ersetzen durch")) }, modifier = Modifier.weight(1f))
                    val hits = if (find.isBlank()) 0 else Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(markdown).count()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("{0} Treffer", hits), style = MaterialTheme.typography.labelSmall)
                        TextButton(enabled = hits > 0, onClick = {
                            setMarkdown(markdown.replace(Regex(Regex.escape(find), RegexOption.IGNORE_CASE), Regex.escapeReplacement(replace)))
                            Notices.info(tr("{0} Stelle(n) ersetzt", hits))
                        }) { Text(tr("Alle ersetzen")) }
                    }
                    IconButton(onClick = { findOpen = false; find = ""; replace = "" }) { Icon(Icons.Outlined.Close, contentDescription = tr("Suche schließen")) }
                }
            }
            // ── Toolbar of text documents (slides and tables bring their own) ──
            if (flow) flowHost.toolbar?.let { bar ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { bar() }
                        // Like Google Docs: the arrow at the end of the Formatleiste hides and shows title and menus.
                        CompositionLocalProvider(LocalToolIconSize provides 34.dp) {
                            ToolIcon(
                                if (menusHidden) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                                if (menusHidden) tr("Menüs einblenden (Strg+Umschalt+F)") else tr("Menüs ausblenden (Strg+Umschalt+F)"),
                            ) { menusHidden = !menusHidden }
                        }
                    }
                }
            }
            if (kind == DocKind.DOC && doc != null && mode == TextMode.EDIT && !showingDraft && !flow) {
                TextToolbar(
                    editing, blocks, leading = undoRedo,
                    onBlocks = { list, at ->
                        setBlocks(list)
                        if (at != null) list.getOrNull(at)?.let { editing.start(at, it.replace(EMPTY_BLOCK, "")) }
                    },
                    onAi = { aiMenuFor = selection() },
                    onComment = { commentOn = editing.selection ?: editing.index?.let { DocBlocks.plainOf(blocks.getOrNull(it).orEmpty()).take(200) } ?: "" },
                    onDoneEditing = {
                        val i = editing.index
                        val text = editing.value.text.replace(EMPTY_BLOCK, "")
                        editing.index = null
                        if (i != null && i in blocks.indices) setBlocks(blocks.toMutableList().apply { if (text.isBlank()) removeAt(i) else set(i, text) })
                    },
                    onImage = pickImage,
                    onCitation = { citing = true },
                )
            }
            // Statistics of the text (of the selection, if there is one).
            if ((kind == DocKind.DOC || kind == DocKind.MARKDOWN) && doc != null && !showingDraft && !flow) {
                val sel = editing.selection
                val stats = remember(markdown, sel) { DocStats.of(sel ?: markdown) }
                HintText((if (sel != null) "Auswahl: " else "") + stats.label(), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 1.dp))
            }
            // Open in Word/Excel/PowerPoint (AP-59): the editor says so; a save there comes back here.
            val openElsewhere by ExternalOffice.open.collectAsState()
            val realFile = doc?.let { d -> remember(d.id, d.path) { c.docs.realFile(d) } }
            if (realFile != null && realFile.absolutePath in openElsewhere) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        tr("In {0} geöffnet – was du dort speicherst, kommt hierher zurück. Bitte nicht gleichzeitig hier und dort ändern.", ExternalOffice.program(realFile) ?: "Office"),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            HorizontalDivider()
            // ── Page and side panel ──
            androidx.compose.runtime.CompositionLocalProvider(LocalDocBase provides docBase) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        kind == DocKind.SLIDES && doc != null && !showingDraft -> SlidesBody(
                            c, doc, markdown, writing || aiWorking, narrow, leading = undoRedo, onMarkdown = ::setMarkdown,
                            onComment = { commentOn = it }, inlineAi = inlineAi,
                        )
                        kind == DocKind.SHEET && doc != null && !showingDraft -> SheetBody(
                            c, doc, markdown, tab?.id, writing, leading = undoRedo, onMarkdown = ::setMarkdown,
                            onComment = { commentOn = it }, inlineAi = inlineAi,
                        )
                        kind == DocKind.MARKDOWN && doc != null && !showingDraft -> MarkdownBody(markdown, tab?.id, narrow, leading = undoRedo, onChange = ::setMarkdown)
                        mode == TextMode.SOURCE && doc != null -> SourceEditor(markdown, tab?.id, ::setMarkdown)
                        flow && doc != null -> {
                            val host = flowHost
                            FlowEditor(
                                c, doc, tab?.id, markdown, narrow, host, onChange = ::setMarkdown, chromeInHost = true,
                                trackBase = trackBase,
                                onTrackBase = { next -> tab?.id?.let { id -> save(note = trLater("Änderung angenommen")) { d -> d.copy(trackBase = d.trackBase.orEmpty() + (id to next)) } } },
                            )
                        }
                        else -> {
                            LaunchedEffect(blocks.size, showingDraft) { if (showingDraft && blocks.isNotEmpty()) listState.animateScrollToItem(blocks.size) }
                            TextPage(
                                doc, tab?.id, blocks, mode, writing = showingDraft, aiChanged = aiChanged, find = find, editing = editing, inlineAi = inlineAi,
                                listState = listState, narrow = narrow, onBlocks = ::setBlocks,
                                tud = kind == DocKind.DOC && DocStyle.parse(doc?.style) == DocStyle.TUD,
                                unit = c.settings.current.tudUnit,
                                onComment = { commentOn = it }, onShowComments = { panel = DocPanel.COMMENTS },
                                onAskAi = { text -> if (text.isBlank() || editing.index == null) panel = DocPanel.AI else aiMenuFor = text },
                                footer = if (doc == null) "" else tr("{0} Wörter · ca. {1} Min. Lesezeit · {2} Versionen", doc.words, (doc.words / 200).coerceAtLeast(1), doc.versions.size) +
                                    if (mode == TextMode.EDIT) tr(" · Tippen zum Bearbeiten · „/“ für Formate · Strg+E: KI") else "",
                                review = review, onReview = ::reviewAction, tracking = trackBase != null,
                            )
                        }
                    }
                    if (doc != null && !flow && (kind == DocKind.DOC || kind == DocKind.MARKDOWN) && panel == DocPanel.NONE && mode != TextMode.SOURCE) {
                        Box(Modifier.align(Alignment.BottomCenter)) { AiPromptBar(kind, aiWorking || writing) { panel = DocPanel.AI } }
                    }
                }
                if (wide && panel != DocPanel.NONE && doc != null) {
                    VerticalDivider()
                    Box(Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        PanelContent(c, doc, panel, tab, blocks, ::selection, onOpenChat, onClose = { panel = DocPanel.NONE }, onAddComment = { commentOn = "" }, onJump = { i ->
                            scope.launch { listState.animateScrollToItem(i + 1) }
                        })
                    }
                }
            }
            }
        }
        if (!wide && panel != DocPanel.NONE && doc != null) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { panel = DocPanel.NONE }, sheetState = state) {
                Box(Modifier.fillMaxWidth().height(if (panel == DocPanel.AI) 560.dp else 520.dp)) {
                    PanelContent(c, doc, panel, tab, blocks, ::selection, onOpenChat, onClose = { panel = DocPanel.NONE }, onAddComment = { commentOn = "" }, onJump = { i ->
                        panel = DocPanel.NONE
                        scope.launch { listState.animateScrollToItem(i + 1) }
                    })
                }
            }
        }
    }

    // ✨ on a passage: menu of actions, then the suggestion appears below it.
    aiMenuFor?.let { text ->
        val at = editing.index
        InlineAiMenu(expanded = true, onDismiss = { aiMenuFor = null }) { action, instruction ->
            aiMenuFor = null
            if (at != null) inlineAi.start("b$at", doc, text, action.label, instruction)
        }
    }
    commentOn?.let { quote ->
        if (doc != null && tab != null) {
            val q = quote.ifBlank { editing.index?.let { DocBlocks.plainOf(blocks.getOrNull(it).orEmpty()).take(200) }.orEmpty() }
            NewCommentDialog(doc, q, onDismiss = { commentOn = null }) { text, toAi ->
                val comment = DocComment(tabId = tab.id, quote = q, text = text, toAi = toAi)
                c.docs.save(doc.copy(comments = doc.comments + comment, sendToAi = toAi))
                if (toAi) {
                    val current = c.docs.get(doc.id)!!
                    if (DocsAi.send(c, current, tr("Kommentar im {0}", kind.label), tr("Kommentar zu „{0}“: {1}", comment.quote.take(120), comment.text), DocsAi.commentInstructions(current, comment))) {
                        Notices.info(tr("Die KI bearbeitet den Kommentar."))
                    }
                }
                commentOn = null
                panel = DocPanel.COMMENTS
            }
        }
    }
    if (spellLocal && doc != null) SpellCheckDialog(
        blocks,
        onReplace = { i, b -> setBlocks(blocks.toMutableList().apply { if (i in indices) set(i, b) }) },
        onDismiss = { spellLocal = false },
        onAskAi = { spellFor = markdown.replace(EMPTY_BLOCK, "") },
    )
    if (stopTracking && doc != null) AlertDialog(
        onDismissRequest = { stopTracking = false },
        title = { Text(tr("Änderungen nicht mehr nachverfolgen")) },
        text = { Text(if (io.github.thepytales.llmhub.docs.Tracking.pending(doc) == 0) tr("Es sind keine Änderungen offen.") else tr("Was soll mit den offenen Änderungen passieren?")) },
        confirmButton = {
            TextButton(onClick = { stopTracking = false; save(note = tr("Alle Änderungen angenommen")) { d -> d.copy(trackBase = null) } }) { Text(tr("Alle annehmen")) }
        },
        dismissButton = {
            TextButton(onClick = {
                stopTracking = false
                save(note = tr("Alle Änderungen abgelehnt")) { d -> d.copy(tabs = d.tabs.map { t -> d.trackBase?.get(t.id)?.let { b -> t.copy(markdown = b) } ?: t }, trackBase = null) }
            }) { Text(tr("Alle ablehnen")) }
        },
    )
    if (pageSetup && tab != null) PageSetupDialog(DocMarkup.pageSetup(tab.markdown), onDismiss = { pageSetup = false }) { setup ->
        pageSetup = false
        editing.index = null
        setMarkdown(DocMarkup.withPageSetup(tab.markdown, setup))
    }
    if (citing && doc != null) {
        val style = DocCitations.cited(markdown).style ?: CitationStyle.parse(c.settings.current.citationStyle) ?: CitationStyle.APA7_DE
        CitationDialog(c, style, onDismiss = { citing = false }) { ref, st ->
            citing = false
            cite(ref, st)
        }
    }
    spellFor?.let { text ->
        SpellCheckDialog(c, doc, text, onDismiss = { spellFor = null }) { list ->
            val t = tab ?: return@SpellCheckDialog
            editing.index = null
            val current = c.docs.get(doc?.id)?.tab(t.id)?.markdown ?: t.markdown
            val next = SpellCheck.applyAll(current, list)
            if (next != current) save(note = tr("Rechtschreibung korrigiert")) { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(markdown = next) else it }) }
        }
    }
    if (deckFromDoc && doc != null) {
        var withAi by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deckFromDoc = false },
            title = { Text(tr("Präsentation aus Dokument")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintText(tr("Aus Überschriften, Listen, Tabellen und Bildern entstehen Folien im TUD-Design; lange Absätze wandern in die Sprechernotizen."))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withAi = !withAi }) {
                        Checkbox(withAi, { withAi = it })
                        Text(tr("Anschließend von der KI überarbeiten lassen"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deckFromDoc = false
                    val slides = SlideDeck.fromDocument(doc.markdown().replace(EMPTY_BLOCK, ""), doc.title)
                    val deck = LiveDoc.create(doc.title, SlideDeck.markdown(slides), "Sie", kind = DocKind.SLIDES).copy(vault = doc.vault)
                    c.docs.save(deck)
                    if (withAi) DocsAi.send(
                        c, deck, tr("Präsentation aus Dokument"),
                        tr("Überarbeite diese aus dem Dokument „{0}“ erzeugte Präsentation: prägnante Folientitel, höchstens 5 kurze ", doc.title) +
                            tr("Stichpunkte je Folie, passende Folientypen (Abschnitt, Vergleich, Tabelle, Bild), Sprechernotizen zu jeder Folie. " +
                            "Bilder und Tabellen bleiben erhalten. Das Dokument zur Orientierung:\n\n") + doc.markdown().take(20_000),
                        DocsAi.instructions(deck),
                    )
                    DocsHandoff.show(deck.id)
                }) { Text(tr("Erstellen")) }
            },
            dismissButton = { TextButton(onClick = { deckFromDoc = false }) { Text(tr("Abbrechen")) } },
        )
    }
    if (showHistory && doc != null) VersionHistory(c, doc, tab?.id, onDismiss = { showHistory = false })
    if (renaming && doc != null) TextPrompt(tr("Umbenennen"), doc.title, onDismiss = { renaming = false }) { name ->
        save { it.copy(title = name) }
        renaming = false
    }
    if (sendToChat && doc != null) SendToChatDialog(c, doc, onDismiss = { sendToChat = false })
    if (chooseVault && doc != null) VaultDialog(c, doc, onDismiss = { chooseVault = false })
    if (chooseStyle && doc != null) StyleDialog(doc.style, onDismiss = { chooseStyle = false }) { style ->
        c.docs.update(doc.id) { it.copy(style = style) }
        chooseStyle = false
    }
    export?.let { (title, md) ->
        val d = doc
        ExportDialog(
            c, title, md, onDismiss = { export = null },
            onCreated = { file -> d?.let { dd -> c.docs.update(dd.id) { it.copy(file = file.absolutePath) } } },
            initial = when (kind) { DocKind.SLIDES -> DocFormat.PPTX; DocKind.SHEET -> DocFormat.XLSX; DocKind.DOC -> DocFormat.PDF; DocKind.MARKDOWN -> DocFormat.MD },
            initialStyle = DocStyle.parse(d?.style),
            custom = if (kind == DocKind.SHEET && d != null) { format, file ->
                when (format) {
                    DocFormat.XLSX -> {
                        // With formats, conditional formatting, filter and frozen head row of each tab.
                        XlsxWriter.writeTabs(file, d.tabs.map { t -> t.title to t.markdown })
                        true
                    }
                    else -> false
                }
            } else null,
        )
    }
}

/** Markdown for export: tables with computed values (the .xlsx export keeps the formulas). */
/** What the *Datei* menu of the Fließtext-Editor does (APD-15). */
internal class DocFileActions(
    val rename: () -> Unit, val duplicate: () -> Unit, val star: () -> Unit, val vault: () -> Unit, val export: (tabOnly: Boolean) -> Unit,
    val sendToChat: () -> Unit, val shareText: () -> Unit, val deck: () -> Unit, val pageSetup: () -> Unit, val design: () -> Unit,
    val history: () -> Unit, val delete: () -> Unit,
)

/**
 * Header of a text document in the Fließtext-Editor (APD-15, like Google Docs): back, the document symbol, the title
 * (click: rename) with its status, the menus right under the title; at the right Kommentare, Lora and *Exportieren*.
 */
@Composable
private fun FlowDocHeader(
    doc: LiveDoc, narrow: Boolean, inSidePanel: Boolean, onClose: () -> Unit, onRename: () -> Unit,
    status: String, busy: Boolean, tracking: Int?, onAcceptAll: () -> Unit,
    menus: (@Composable () -> Unit)?, onComments: () -> Unit, onAi: () -> Unit, onExport: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(if (inSidePanel) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = if (inSidePanel) tr("Schließen") else tr("Zurück")) }
        if (!narrow) Icon(Icons.Outlined.Description, contentDescription = null, tint = LocalChatLook.current.brand, modifier = Modifier.padding(end = 4.dp).size(30.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = tr("Umbenennen"), onClick = onRename).padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    status, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (tracking != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        if (tracking == 0) tr("Änderungen werden nachverfolgt") else tr("{0} Änderungen offen · alle annehmen", tracking), PillTone.ACCENT,
                        Modifier.clickable(enabled = tracking > 0, onClick = onAcceptAll),
                    )
                }
            }
            menus?.invoke()
        }
        IconButton(onClick = onComments) {
            BadgedBox(badge = { if (doc.openComments > 0) Badge { Text("${doc.openComments}") } }) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = tr("Kommentare"))
            }
        }
        IconButton(onClick = onAi) { Icon(Icons.Outlined.AutoAwesome, contentDescription = tr("KI-Assistent (Strg+E)"), tint = MaterialTheme.colorScheme.primary) }
        if (narrow) IconButton(onClick = onExport) { Icon(Icons.Outlined.FileDownload, contentDescription = tr("Exportieren …")) }
        else {
            Spacer(Modifier.width(4.dp))
            Button(onClick = onExport, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("Exportieren"))
            }
        }
    }
}

/** The *Datei* menu of the Fließtext-Editor (APD-15): file, sharing, page and versions – what „⋮“ held before. */
@Composable
private fun FlowFileMenu(c: AppContainer, d: LiveDoc, liveTab: String?, close: () -> Unit, actions: DocFileActions) {
    @Composable
    fun entry(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, danger: Boolean = false, onClick: () -> Unit) {
        // Every entry with a symbol or the room of one, so all words start in one column.
        val lead: @Composable () -> Unit = { if (icon != null) Icon(icon, contentDescription = null) else Spacer(Modifier.size(24.dp)) }
        if (danger) DropdownMenuItem(colors = dangerMenuColors(), text = { Text(label) }, leadingIcon = lead, onClick = { close(); onClick() })
        else DropdownMenuItem(text = { Text(label) }, leadingIcon = lead, onClick = { close(); onClick() })
    }
    entry(tr("Umbenennen …"), Icons.Outlined.Edit, onClick = actions.rename)
    entry(tr("Kopie anlegen"), Icons.Outlined.ContentCopy, onClick = actions.duplicate)
    entry(if (d.starred) tr("Nicht mehr anheften") else tr("Anheften"), if (d.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, onClick = actions.star)
    if (FolderAccess.SUPPORTED || d.vault != null) entry(tr("Ablageort …"), Icons.Outlined.Folder, onClick = actions.vault)
    HorizontalDivider()
    entry(tr("Exportieren …"), Icons.Outlined.FileDownload) { actions.export(false) }
    val tab = d.tab(liveTab)
    if (d.tabs.size > 1 && tab != null) entry(tr("Nur „{0}“ exportieren", tab.title), null) { actions.export(true) }
    // In Word öffnen und zurück (AP-59, Windows): the real file in its program.
    val real = remember(d.id, d.path) { c.docs.realFile(d)?.takeIf { it.isFile } }
    if (real != null && ExternalOffice.opener != null) ExternalOffice.program(real)?.let { prog ->
        entry(tr("In {0} öffnen", prog), null) { if (ExternalOffice.opener?.invoke(real) != true) Notices.error(tr("„{0}“ ließ sich nicht öffnen.", real.name)) }
    }
    entry(tr("Word mit Änderungsverfolgung …"), null, onClick = actions.history)
    entry(tr("In Chat senden …"), Icons.AutoMirrored.Outlined.Send, onClick = actions.sendToChat)
    entry(tr("Als Text teilen"), Icons.Outlined.IosShare, onClick = actions.shareText)
    entry(tr("Präsentation aus Dokument …"), Icons.Outlined.Slideshow, onClick = actions.deck)
    HorizontalDivider()
    entry(tr("Seite einrichten …"), null, onClick = actions.pageSetup)
    entry(tr("Design: ") + (DocStyle.parse(d.style)?.label ?: tr("wie in den Einstellungen")), Icons.Outlined.Palette, onClick = actions.design)
    entry(tr("Versionsverlauf"), Icons.Outlined.History, onClick = actions.history)
    HorizontalDivider()
    entry(tr("Löschen"), Icons.Outlined.Delete, danger = true, onClick = actions.delete)
}

private fun exportMarkdown(doc: LiveDoc, tabId: String?): String {
    val tabs = if (tabId != null) doc.tabs.filter { it.id == tabId } else doc.tabs
    return when (doc.docKind) {
        DocKind.SHEET -> {
            // References to other tabs are computed with the whole workbook; charts of a tab become chart blocks.
            val book = SheetTools.workbook(doc.tabs)
            tabs.joinToString("\n\n") { t ->
                (if (tabs.size > 1 || tabId == null) "## ${t.title}\n\n" else "") + SheetTools.exportMarkdown(t.markdown, book, t.title)
            }.let { DocumentWriter.withTitle(doc.title, it) }
        }
        DocKind.SLIDES -> tabs.joinToString("\n\n---\n\n") { it.markdown }
        DocKind.DOC -> DocumentWriter.withTitle(doc.title, if (tabId != null) doc.tab(tabId).markdown else doc.markdown()).replace(EMPTY_BLOCK, "")
        DocKind.MARKDOWN -> (if (tabId != null) doc.tab(tabId).markdown else doc.markdown()).replace(EMPTY_BLOCK, "")
    }
}

@Composable
private fun PanelContent(
    c: AppContainer,
    doc: LiveDoc,
    panel: DocPanel,
    tab: DocTab?,
    blocks: List<String>,
    selection: () -> String?,
    onOpenChat: ((String) -> Unit)?,
    onClose: () -> Unit,
    onAddComment: () -> Unit,
    onJump: (Int) -> Unit,
) {
    when (panel) {
        DocPanel.AI -> DocAssistantPanel(c, doc, tab?.title, selection, onClose, onOpenChat)
        DocPanel.COMMENTS -> CommentsPanel(c, doc, tab?.id, onAdd = onAddComment)
        DocPanel.OUTLINE -> OutlinePanel(c, doc, blocks, onJump = onJump, onOpenDoc = { DocsHandoff.show(it) }, onClose = onClose)
        DocPanel.NONE -> {}
    }
}

/** "In Chat senden": the document as attachment in a new or an existing chat. */
@Composable
private fun SendToChatDialog(c: AppContainer, doc: LiveDoc, onDismiss: () -> Unit) {
    val chats by c.chats.conversations.collectAsState()
    var asFile by remember { mutableStateOf(false) }
    val recent = chats.filter { !it.archived }.sortedByDescending { it.updatedAt }.take(8)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun send(chatId: String?) {
        onDismiss()
        if (!asFile) {
            ChatHandoff.send(chatId, FileAttachment("${doc.title}.md", "text/markdown", exportMarkdown(doc, null)))
            return
        }
        scope.launch {
            // As Word/PowerPoint/Excel file: the chat gets the text, the file is kept for the model.
            val format = when (doc.docKind) { DocKind.SLIDES -> DocFormat.PPTX; DocKind.SHEET -> DocFormat.XLSX; DocKind.DOC -> DocFormat.DOCX; DocKind.MARKDOWN -> DocFormat.MD }
            runCatching {
                withContext(Dispatchers.IO) {
                    DocumentWriter.create(
                        context, File(context.cacheDir, "exports"), doc.title, exportMarkdown(doc, null), format,
                        theme = c.documentTools.settingsTheme(DocStyle.parse(doc.style)), imageBase = c.docs.baseDir(doc),
                    ).file
                }
            }.onSuccess { f ->
                ChatHandoff.send(chatId, FileAttachment(f.name, format.mime, exportMarkdown(doc, null), path = f.absolutePath))
            }.onFailure { Notices.error(tr("Export fehlgeschlagen: {0}", it.message)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("In Chat senden")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HintText(tr("„{0}“ wird im Eingabefeld des Chats angehängt – du kannst noch etwas dazu schreiben.", doc.title))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { asFile = !asFile }) {
                    Checkbox(asFile, { asFile = it })
                    Text(when (doc.docKind) { DocKind.SLIDES -> tr("Als PowerPoint-Datei"); DocKind.SHEET -> tr("Als Excel-Datei"); DocKind.DOC -> tr("Als Word-Datei"); DocKind.MARKDOWN -> tr("Als .md-Datei") } + tr(" (sonst als Text)"))
                }
                Surface(onClick = { send(null) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Neuer Chat"))
                    }
                }
                LazyColumn(Modifier.height(280.dp)) {
                    items(recent, key = { it.id }) { conv ->
                        Text(
                            conv.title.ifBlank { "Chat" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { send(conv.id) }.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** The look of the document: the page in the editor and the exported PDF and Word file follow it. */
@Composable
private fun StyleDialog(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Design des Dokuments")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                HintText(tr("Gilt für die Seite im Editor und für PDF und Word beim Export."))
                (listOf<DocStyle?>(null) + DocStyle.entries).forEach { st ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(st?.name) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = DocStyle.parse(current) == st, onClick = { onPick(st?.name) })
                        Column(Modifier.weight(1f)) {
                            Text(st?.label ?: tr("Wie in den Einstellungen"), style = MaterialTheme.typography.bodyLarge)
                            HintText(st?.description ?: tr("Einstellungen › Dokumente & Darstellung"))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** Moves a document into a storage folder (Google Drive …) or back into the app. */
@Composable
private fun VaultDialog(c: AppContainer, doc: LiveDoc, onDismiss: () -> Unit) {
    val vaults by c.docs.vaults.collectAsState()
    var choice by remember { mutableStateOf(doc.vault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Ablageort")) },
        text = {
            Column {
                HintText(tr("In einem Ablageort liegt „{0}“ als lesbare Datei im Ordner – in Google Drive oder OneDrive auch auf deinen anderen Rechnern.", doc.title))
                Row(Modifier.fillMaxWidth().clickable { choice = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(choice == null, { choice = null })
                    Text(tr("In der App"))
                }
                vaults.forEach { v ->
                    Row(Modifier.fillMaxWidth().clickable { choice = v.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == v.id, { choice = v.id })
                        Column {
                            Text(v.name)
                            Text(v.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (vaults.isEmpty()) HintText(tr("Noch keine Ablageorte – unter Einstellungen › Speicherorte einen Ordner hinzufügen."))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.docs.move(doc.id, choice)
                onDismiss()
            }) { Text(tr("Verschieben")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Blinking bar at the end of the text the model is writing. */
@Composable
fun WritingCaret() {
    val alpha by rememberInfiniteTransition(label = "caret").animateFloat(0.2f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "alpha")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 18.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Spacer(Modifier.width(8.dp))
        Text(tr("KI schreibt …"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun TextPrompt(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onDone(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun NewCommentDialog(doc: LiveDoc, quote: String, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var toAi by remember { mutableStateOf(doc.sendToAi) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kommentar")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quote.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                    Text("„${quote.take(200)}“", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                }
                OutlinedTextField(text, { text = it }, placeholder = { Text(tr("z.B. Kürzer und förmlicher formulieren")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { toAi = !toAi }) {
                    Checkbox(toAi, { toAi = it })
                    Column {
                        Text(tr("An KI senden"))
                        HintText(tr("Die KI ändert die Stelle und antwortet im Kommentar."))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim(), toAi) }) { Text(tr("Kommentieren")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun CommentsPanel(c: AppContainer, doc: LiveDoc, tabId: String?, onAdd: () -> Unit) {
    var showResolved by remember { mutableStateOf(false) }
    val list = doc.comments.filter { showResolved || !it.resolved }.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Kommentare"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilterChip(showResolved, { showResolved = !showResolved }, label = { Text(tr("Erledigte")) })
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.AddComment, contentDescription = tr("Kommentar hinzufügen")) }
        }
        if (list.isEmpty()) HintText(tr("Noch keine Kommentare. Absatz antippen und das Sprechblasen-Symbol wählen."), Modifier.padding(12.dp))
        LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { cm ->
                var reply by remember { mutableStateOf("") }
                AppCard {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (cm.quote.isNotBlank()) Text("„${cm.quote.take(140)}“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(cm.text, style = MaterialTheme.typography.bodyMedium)
                        HintText("${cm.author} · ${time(cm.createdAt)}" + (if (doc.tabs.size > 1 && cm.tabId != tabId) tr(" · Tab „{0}“", doc.tab(cm.tabId).title) else "") + if (cm.toAi) tr(" · an KI") else "")
                        cm.replies.forEach { r ->
                            Surface(color = if (r.ai) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(r.text, style = MaterialTheme.typography.bodySmall)
                                    HintText("${r.author} · ${time(r.createdAt)}")
                                }
                            }
                        }
                        if (!cm.resolved) {
                            OutlinedTextField(reply, { reply = it }, placeholder = { Text(tr("Antworten")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Row {
                            if (reply.isNotBlank()) TextButton(onClick = {
                                c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(replies = it.replies + DocReply("Sie", reply.trim())) else it }) }
                                if (cm.toAi) DocsAi.send(c, doc, tr("Antwort im Kommentar"), reply.trim(), DocsAi.commentInstructions(doc, cm))
                                reply = ""
                            }) { Text(tr("Senden")) }
                            if (!cm.toAi && !cm.resolved) TextButton(onClick = {
                                c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(toAi = true) else it }) }
                                DocsAi.send(c, doc, tr("Kommentar im Dokument"), tr("Kommentar zu „{0}“: {1}", cm.quote.take(120), cm.text), DocsAi.commentInstructions(doc, cm))
                            }) { Text(tr("An KI")) }
                            TextButton(onClick = { c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(resolved = !it.resolved) else it }) } }) {
                                Text(if (cm.resolved) tr("Wieder öffnen") else tr("Erledigt"))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Versions of the document with author and time; compare with the current state, restore, Word with tracked changes. */
@Composable
private fun VersionHistory(c: AppContainer, doc: LiveDoc, tabId: String?, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<DocVersion?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val saveAs = rememberSaveAs(DocFormat.DOCX.mime)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selected == null) tr("Versionsverlauf") else tr("Version vom {0}", time(selected!!.createdAt))) },
        text = {
            val sel = selected
            if (sel == null) {
                LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val versions = doc.versions.reversed()
                    itemsIndexed(versions) { i, v ->
                        val previous = versions.getOrNull(i + 1)
                        val diff = remember(v.id) { previous?.let { TextDiff.summary(TextDiff.words(it.tabs.joinToString("\n\n") { t -> t.markdown }, v.tabs.joinToString("\n\n") { t -> t.markdown })) } }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selected = v },
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(time(v.createdAt), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    StatusPill(v.author, if (v.byAi) PillTone.ACCENT else PillTone.NEUTRAL)
                                }
                                HintText(listOfNotNull(v.note.ifBlank { null }, diff, if (i == 0) "aktuell" else null).joinToString(" · "))
                            }
                        }
                    }
                }
            } else {
                val oldText = sel.tabs.firstOrNull { it.id == tabId }?.markdown ?: sel.tabs.first().markdown
                val newText = doc.tab(tabId).markdown
                val pieces = remember(sel.id, newText) { TextDiff.words(oldText, newText) }
                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintText(tr("Unterschiede dieser Version zum aktuellen Stand ({0}): grün = seitdem hinzugekommen, rot = seitdem entfernt.", TextDiff.summary(pieces)))
                    SelectionContainer {
                        Text(buildAnnotatedString {
                            pieces.forEach { p ->
                                when (p.op) {
                                    TextDiff.Op.SAME -> append(p.text)
                                    TextDiff.Op.ADDED -> withStyle(SpanStyle(background = Color(0x3325A35A), color = Color(0xFF1B7A43))) { append(p.text) }
                                    TextDiff.Op.REMOVED -> withStyle(SpanStyle(background = Color(0x33D32F2F), color = Color(0xFFB3261E), textDecoration = TextDecoration.LineThrough)) { append(p.text) }
                                }
                            }
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            val sel = selected
            if (sel == null) TextButton(onClick = onDismiss) { Text(tr("Schließen")) }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    busy = true
                    scope.launch {
                        val file = runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val f = File(dir, DocumentWriter.safeName(tr("{0} (Änderungen)", doc.title)) + ".docx")
                                val base = c.docs.baseDir(doc)
                                MarkdownDocx.writeTracked(
                                    f, doc.title, DocMarkup.resolveImages(DocumentWriter.withTitle(sel.title, sel.tabs.joinToString("\n\n") { it.markdown }), base),
                                    DocMarkup.resolveImages(DocumentWriter.withTitle(doc.title, doc.markdown()), base), c.documentTools.settingsTheme(DocStyle.parse(doc.style)), author = "Lora",
                                )
                                f
                            }
                        }
                        busy = false
                        file.onSuccess { saveAs(it) }.onFailure { Notices.error(tr("Word-Export fehlgeschlagen: {0}", it.message)) }
                    }
                }) { Text(tr("Word mit Änderungen")) }
                TextButton(onClick = {
                    c.docs.save(doc.change("Sie", "Version vom ${time(sel.createdAt)} wiederhergestellt") { it.copy(title = sel.title, tabs = sel.tabs) })
                    Notices.info(tr("Version wiederhergestellt"))
                    onDismiss()
                }) { Text(tr("Wiederherstellen")) }
            }
        },
        dismissButton = { if (selected != null) TextButton(onClick = { selected = null }) { Text(tr("Zurück")) } },
    )
}

