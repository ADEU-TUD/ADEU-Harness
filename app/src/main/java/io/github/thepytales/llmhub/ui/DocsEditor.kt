package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocComment
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocReply
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTab
import io.github.thepytales.llmhub.docs.DocVersion
import io.github.thepytales.llmhub.docs.DocsLive
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.TextDiff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Opens documents in the editor from anywhere (document cards, literature, chat answers). */
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
    fun open(c: AppContainer, title: String, markdown: String, chatId: String? = null): LiveDoc {
        val doc = LiveDoc.create(title, markdown, "Sie", chatId)
        c.docs.save(doc)
        show(doc.id)
        return doc
    }
}

/** Sends a request about a document to the model in the document's chat (creating one if needed). */
object DocsAi {
    fun send(c: AppContainer, doc: LiveDoc, label: String, text: String, instructions: String): Boolean {
        val existing = doc.chatId?.let { id -> c.runs.get(id)?.conversation?.value ?: c.chats.get(id) }
        if (existing != null && c.runs.get(existing.id) != null) {
            Notices.info("Die KI arbeitet gerade in diesem Chat – bitte kurz warten.")
            return false
        }
        val base = existing ?: newConversation(c).copy(title = "Dokument: ${doc.title}".take(60), folderId = null)
        if (existing == null) c.docs.save(doc.copy(chatId = base.id))
        val conv = base.appendToThread(ChatMessage(role = "user", content = text, template = label, instructions = instructions))
        c.chats.save(conv.copy(updatedAt = System.currentTimeMillis()))
        c.runs.start(conv)
        return true
    }

    fun commentInstructions(doc: LiveDoc, comment: DocComment): String =
        "Der Nutzer hat im Dokument-Editor einen Kommentar zum Dokument „${doc.title}“ geschrieben (Kommentar-id ${comment.id}, " +
            "Tab „${doc.tab(comment.tabId).title}“). Lies das Dokument mit read_document (file=\"${doc.id}\"), ändere die kommentierte Stelle mit " +
            "edit_document (file=\"${doc.id}\", wörtliche Ersetzungen) – nur dort, alles andere bleibt – und antworte danach mit " +
            "reply_to_comment kurz, was du geändert hast. Ist der Wunsch unklar, frage mit reply_to_comment (resolve=false) nach."
}

private fun time(millis: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** The editor on its own screen (phones) or as route "doc/{id}". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocScreen(c: AppContainer, id: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    Scaffold { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            DocPane(c, id, onClose = onBack, onOpenChat = onOpenChat)
        }
    }
}

/**
 * The document editor (like Claude Docs): the document as you will export it, editable block by block, with tabs,
 * comments for people and the model, a version history with comparison and export as PDF, Word (also with
 * tracked changes), Markdown, HTML, Excel or PowerPoint. While the model writes, the text grows on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocPane(c: AppContainer, id: String, onClose: () -> Unit, onOpenChat: ((String) -> Unit)? = null, inSidePanel: Boolean = false) {
    val docs by c.docs.items.collectAsState()
    val drafts by DocsLive.drafts.collectAsState()
    val chatId = id.removePrefix("chat:").takeIf { id.startsWith("chat:") }
    val doc = if (chatId != null) docs.filter { it.chatId == chatId }.maxByOrNull { it.updatedAt } else docs.firstOrNull { it.id == id }
    val draft = (chatId ?: doc?.chatId)?.let { drafts[it] }
    val writing = draft != null
    val context = LocalContext.current

    var tabId by rememberSaveable(doc?.id) { mutableStateOf(doc?.tabs?.firstOrNull()?.id) }
    var editing by remember(doc?.id) { mutableStateOf<Int?>(null) }
    var showComments by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var commentOn by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var export by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dismissedAi by remember { mutableStateOf<String?>(null) }
    val undo = remember(doc?.id) { mutableStateListOf<List<DocTab>>() }
    val redo = remember(doc?.id) { mutableStateListOf<List<DocTab>>() }

    if (doc == null && draft == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Dokument nicht gefunden", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClose) { Text("Schließen") }
        }
        return
    }

    val tab = doc?.tab(tabId)
    val markdown = when {
        writing && (doc == null || draft?.docId == null && doc.versions.size <= 1 && chatId != null) -> draft!!.markdown
        else -> tab?.markdown.orEmpty()
    }
    val showingDraft = writing && markdown == draft?.markdown
    val blocks = remember(markdown) { DocBlocks.split(markdown) }
    // What the model changed last (highlighted until the user looks away or edits).
    val lastVersion = doc?.versions?.lastOrNull()
    val aiChanged = remember(doc?.versions?.size, tabId) {
        val v = doc?.versions ?: emptyList()
        if (v.size >= 2 && v.last().byAi && tab != null) {
            val before = v[v.size - 2].tabs.firstOrNull { it.id == tab.id }?.markdown.orEmpty()
            TextDiff.changedBlocks(before, tab.markdown)
        } else emptySet()
    }.takeIf { lastVersion?.id != dismissedAi }.orEmpty()

    fun save(author: String = "Sie", note: String = "", transform: (LiveDoc) -> LiveDoc) {
        val d = c.docs.get(doc?.id) ?: return
        undo += d.tabs
        if (undo.size > 50) undo.removeAt(0)
        redo.clear()
        c.docs.save(d.change(author, note, transform))
    }

    fun setBlocks(newBlocks: List<String>) {
        val t = tab ?: return
        save { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(markdown = DocBlocks.join(newBlocks)) else it }) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // ── Header: title, status, actions ──
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(if (inSidePanel) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = if (inSidePanel) "Schließen" else "Zurück") }
            Column(Modifier.weight(1f).clickable(enabled = doc != null) { renaming = true }) {
                Text(doc?.title ?: draft?.title?.ifBlank { null } ?: "Neues Dokument", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when {
                    writing -> "KI schreibt …"
                    doc != null -> "Gespeichert · ${doc.lastAuthor} · ${time(doc.updatedAt)}"
                    else -> ""
                }
                Text(
                    status, style = MaterialTheme.typography.labelSmall, color = if (writing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (doc != null) {
                IconButton(onClick = {
                    val last = undo.removeLastOrNull() ?: return@IconButton
                    redo += doc.tabs
                    c.docs.save(doc.change("Sie", "Rückgängig") { it.copy(tabs = last) })
                }, enabled = undo.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Rückgängig") }
                IconButton(onClick = {
                    val next = redo.removeLastOrNull() ?: return@IconButton
                    undo += doc.tabs
                    c.docs.save(doc.change("Sie", "Wiederholt") { it.copy(tabs = next) })
                }, enabled = redo.isNotEmpty()) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Wiederholen") }
                IconButton(onClick = { showComments = !showComments }) {
                    BadgedBox(badge = { if (doc.openComments > 0) Badge { Text("${doc.openComments}") } }) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Kommentare")
                    }
                }
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, contentDescription = "Versionsverlauf") }
                var more by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Outlined.FileDownload, contentDescription = "Exportieren und teilen") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Exportieren (PDF, Word, Markdown …)") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = {
                            more = false
                            export = doc.title to DocumentWriter.withTitle(doc.title, doc.markdown())
                        })
                        if (doc.tabs.size > 1) DropdownMenuItem(text = { Text("Nur diesen Tab exportieren") }, onClick = {
                            more = false
                            export = "${doc.title} – ${tab!!.title}" to tab.markdown
                        })
                        DropdownMenuItem(text = { Text("Word mit Änderungsverfolgung …") }, onClick = { more = false; showHistory = true })
                        DropdownMenuItem(text = { Text("Als Text teilen") }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) }, onClick = {
                            more = false
                            shareText(context, doc.markdown(), doc.title)
                        })
                        DropdownMenuItem(text = { Text("Als PowerPoint-Präsentation (KI)") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = {
                            more = false
                            if (DocsAi.send(
                                    c, doc, "Präsentation aus Dokument", "Mach aus dem Dokument „${doc.title}“ eine Präsentation.",
                                    "Lies das Dokument mit read_document (file=\"${doc.id}\") und erstelle daraus mit create_document eine PowerPoint-Präsentation " +
                                        "(format=pptx) im TUD-Design: Kernaussagen als kurze Stichpunkte, Sprechernotizen mit den Details.",
                                )) Notices.info("Die KI erstellt die Präsentation im Chat.")
                        })
                        DropdownMenuItem(text = { Text("Suchen im Dokument") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, onClick = { more = false; findOpen = true })
                        doc.chatId?.takeIf { onOpenChat != null && !inSidePanel }?.let { cid ->
                            DropdownMenuItem(text = { Text("Zum Chat") }, onClick = { more = false; onOpenChat?.invoke(cid) })
                        }
                        DropdownMenuItem(text = { Text("Dokument löschen", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = {
                            more = false
                            val copy = doc
                            c.docs.delete(doc.id)
                            onClose()
                            Notices.undo("Dokument gelöscht", restore = { c.docs.save(copy) })
                        })
                    }
                }
            }
        }
        // ── Tabs ──
        if (doc != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                doc.tabs.forEach { t ->
                    var tabMenu by remember { mutableStateOf(false) }
                    var renameTab by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = t.id == tab?.id, onClick = { if (t.id == tab?.id) tabMenu = true else { tabId = t.id; editing = null } },
                            label = { Text(t.title, maxLines = 1) },
                        )
                        DropdownMenu(expanded = tabMenu, onDismissRequest = { tabMenu = false }) {
                            DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { tabMenu = false; renameTab = true })
                            if (doc.tabs.size > 1) DropdownMenuItem(text = { Text("Tab löschen") }, onClick = {
                                tabMenu = false
                                save { d -> d.copy(tabs = d.tabs.filterNot { it.id == t.id }) }
                                tabId = doc.tabs.firstOrNull { it.id != t.id }?.id
                            })
                        }
                    }
                    if (renameTab) TextPrompt("Tab umbenennen", t.title, onDismiss = { renameTab = false }) { name ->
                        save { d -> d.copy(tabs = d.tabs.map { if (it.id == t.id) it.copy(title = name) else it }) }
                        renameTab = false
                    }
                }
                IconButton(onClick = {
                    val new = DocTab(title = "Tab ${doc.tabs.size + 1}")
                    save { d -> d.copy(tabs = d.tabs + new) }
                    tabId = new.id
                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Add, contentDescription = "Tab hinzufügen", modifier = Modifier.size(18.dp)) }
                if (aiChanged.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill("${aiChanged.size} Änderungen der KI markiert", PillTone.ACCENT, Modifier.clickable { dismissedAi = lastVersion?.id })
                }
            }
        }
        if (findOpen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(find, { find = it }, singleLine = true, placeholder = { Text("Suchen") }, modifier = Modifier.weight(1f))
                val hits = if (find.isBlank()) 0 else Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(markdown).count()
                Text(" $hits Treffer ", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { findOpen = false; find = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Suche schließen") }
            }
        }
        HorizontalDivider()
        // ── Page ──
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wideComments = maxWidth > 900.dp && showComments && doc != null
            Row(Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                LaunchedEffect(blocks.size, showingDraft) { if (showingDraft && blocks.isNotEmpty()) listState.animateScrollToItem(blocks.lastIndex) }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        item {
                            Surface(
                                color = LocalChatLook.current.card, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                            ) { Spacer(Modifier.height(18.dp)) }
                        }
                        itemsIndexed(blocks, key = { i, b -> "$i:${b.hashCode()}" }) { i, block ->
                            Surface(
                                color = LocalChatLook.current.card,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(Modifier.padding(horizontal = 28.dp)) {
                                    if (editing == i && doc != null && !showingDraft) {
                                        BlockEditor(
                                            block = block,
                                            onDone = { text ->
                                                editing = null
                                                if (text != block) setBlocks(blocks.toMutableList().apply { if (text.isBlank()) removeAt(i) else set(i, text) })
                                            },
                                            onInsertBelow = { text ->
                                                setBlocks(blocks.toMutableList().apply { set(i, text); add(i + 1, "") })
                                                editing = i + 1
                                            },
                                            onMove = { delta ->
                                                val j = i + delta
                                                if (j in blocks.indices) {
                                                    setBlocks(blocks.toMutableList().apply { val x = removeAt(i); add(j, x) })
                                                    editing = j
                                                }
                                            },
                                            onDelete = { editing = null; setBlocks(blocks.toMutableList().apply { removeAt(i) }) },
                                            onComment = { quote -> commentOn = quote },
                                        )
                                    } else {
                                        val commented = doc?.comments?.any { !it.resolved && it.tabId == tab?.id && it.quote.isNotBlank() && (it.quote in block || DocBlocks.plainOf(block).contains(it.quote.take(60))) } == true
                                        val highlight = i in aiChanged || find.isNotBlank() && block.contains(find, true)
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .background(if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                .clickable(enabled = doc != null && !showingDraft) { editing = i }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Box(Modifier.weight(1f)) {
                                                if (block.isBlank()) Text("Leerer Absatz – tippen zum Schreiben", color = MaterialTheme.colorScheme.outline)
                                                else MarkdownText(block)
                                            }
                                            if (commented) {
                                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Kommentiert", tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 6.dp, top = 4.dp).size(16.dp).clickable { showComments = true })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Surface(
                                color = LocalChatLook.current.card, shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
                                    if (showingDraft) WritingCaret()
                                    else if (doc != null) {
                                        TextButton(onClick = { setBlocks(blocks + ""); editing = blocks.size }) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Absatz hinzufügen")
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            if (doc != null) HintText(
                                "${doc.words} Wörter · ${doc.versions.size} Versionen · Tippen zum Bearbeiten" +
                                    (if (doc.chatId != null) " · Kommentare mit „An KI senden“ ändert die KI im Chat" else ""),
                                Modifier.padding(top = 10.dp, start = 4.dp),
                            )
                        }
                    }
                }
                if (wideComments) {
                    VerticalDivider()
                    Box(Modifier.width(300.dp).fillMaxHeight()) { CommentsPanel(c, doc!!, tab?.id, onAdd = { commentOn = "" }) }
                }
            }
        }
    }

    if (showComments && doc != null) {
        // Narrow screens: comments in a sheet (wide ones show them beside the page).
        BoxWithConstraints {
            if (maxWidth <= 900.dp) {
                val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { showComments = false }, sheetState = state) {
                    Box(Modifier.fillMaxWidth().height(520.dp)) { CommentsPanel(c, doc, tab?.id, onAdd = { commentOn = "" }) }
                }
            }
        }
    }
    commentOn?.let { quote ->
        if (doc != null && tab != null) NewCommentDialog(doc, quote.ifBlank { DocBlocks.plainOf(blocks.getOrNull(editing ?: 0).orEmpty()).take(200) }, onDismiss = { commentOn = null }) { text, toAi ->
            val comment = DocComment(tabId = tab.id, quote = quote.ifBlank { DocBlocks.plainOf(blocks.getOrNull(editing ?: 0).orEmpty()).take(200) }, text = text, toAi = toAi)
            c.docs.save(doc.copy(comments = doc.comments + comment, sendToAi = toAi))
            if (toAi) {
                val current = c.docs.get(doc.id)!!
                if (DocsAi.send(c, current, "Kommentar im Dokument", "Kommentar zu „${comment.quote.take(120)}“: ${comment.text}", DocsAi.commentInstructions(current, comment))) {
                    Notices.info("Die KI bearbeitet den Kommentar im Chat.")
                }
            }
            commentOn = null
            showComments = true
        }
    }
    if (showHistory && doc != null) VersionHistory(c, doc, tab?.id, onDismiss = { showHistory = false })
    if (renaming && doc != null) TextPrompt("Dokument umbenennen", doc.title, onDismiss = { renaming = false }) { name ->
        save { it.copy(title = name) }
        renaming = false
    }
    export?.let { (title, md) ->
        ExportDialog(c, title, md, onDismiss = { export = null }, onCreated = { file -> doc?.let { d -> c.docs.update(d.id) { it.copy(file = file.absolutePath) } } })
    }
}

/** Blinking bar at the end of the text the model is writing. */
@Composable
private fun WritingCaret() {
    val alpha by rememberInfiniteTransition(label = "caret").animateFloat(0.2f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "alpha")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 18.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Spacer(Modifier.width(8.dp))
        Text("KI schreibt …", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** One block in edit mode: its Markdown, type, bold/italic, move, delete, comment; tables as a grid. */
@Composable
private fun BlockEditor(
    block: String,
    onDone: (String) -> Unit,
    onInsertBelow: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onComment: (String) -> Unit,
) {
    var value by remember(block) { mutableStateOf(TextFieldValue(block, TextRange(block.length))) }
    var rows by remember(block) { mutableStateOf(DocBlocks.tableRows(block)) }
    val kind = DocBlocks.kindOf(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun current() = if (kind == DocBlocks.Kind.TABLE) DocBlocks.table(rows) else value.text
    fun wrap(mark: String) {
        val sel = value.selection
        val t = value.text
        if (sel.collapsed) return
        val inner = t.substring(sel.min, sel.max)
        val next = t.substring(0, sel.min) + mark + inner + mark + t.substring(sel.max)
        value = TextFieldValue(next, TextRange(sel.min + mark.length, sel.max + mark.length))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            var kinds by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { kinds = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(kind.label, style = MaterialTheme.typography.labelMedium) }
                DropdownMenu(expanded = kinds, onDismissRequest = { kinds = false }) {
                    DocBlocks.Kind.entries.forEach { k ->
                        DropdownMenuItem(text = { Text(k.label) }, onClick = {
                            kinds = false
                            val converted = DocBlocks.convert(current(), k)
                            value = TextFieldValue(converted, TextRange(converted.length))
                            rows = DocBlocks.tableRows(converted)
                        })
                    }
                }
            }
            IconButton(onClick = { wrap("**") }) { Icon(Icons.Outlined.FormatBold, contentDescription = "Fett") }
            IconButton(onClick = { wrap("*") }) { Icon(Icons.Outlined.FormatItalic, contentDescription = "Kursiv") }
            IconButton(onClick = {
                val sel = value.selection
                onComment(if (sel.collapsed) DocBlocks.plainOf(value.text).take(200) else value.text.substring(sel.min, sel.max))
            }) { Icon(Icons.Outlined.AddComment, contentDescription = "Kommentieren") }
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "Nach oben") }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "Nach unten") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Block löschen") }
            IconButton(onClick = { onDone(current()) }) { Icon(Icons.Outlined.Check, contentDescription = "Fertig", tint = MaterialTheme.colorScheme.primary) }
        }
        if (kind == DocBlocks.Kind.TABLE) {
            TableGrid(rows, { rows = it })
        } else {
            OutlinedTextField(
                value, { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                    when {
                        e.type != KeyEventType.KeyDown -> false
                        e.key == Key.Escape -> { onDone(current()); true }
                        e.isCtrlPressed && e.key == Key.B -> { wrap("**"); true }
                        e.isCtrlPressed && e.key == Key.I -> { wrap("*"); true }
                        e.isCtrlPressed && e.isAltPressed && e.key == Key.M -> { onComment(DocBlocks.plainOf(value.text).take(200)); true }
                        // Enter at the end of a paragraph starts a new one below (Umschalt+Enter: new line).
                        e.key == Key.Enter && !e.isShiftPressed && kind in setOf(DocBlocks.Kind.PARAGRAPH, DocBlocks.Kind.H1, DocBlocks.Kind.H2, DocBlocks.Kind.H3) &&
                            value.selection.collapsed && value.selection.end == value.text.length -> { onInsertBelow(value.text); true }
                        else -> false
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            HintText("Markdown: **fett**, *kursiv*, [Link](https://…). Enter am Ende: neuer Absatz · Esc: fertig")
        }
    }
}

/** Table cells as fields, with rows and columns to add or remove. */
@Composable
private fun TableGrid(rows: List<List<String>>, onChange: (List<List<String>>) -> Unit) {
    val cols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                (0 until cols).forEach { col ->
                    OutlinedTextField(
                        row.getOrElse(col) { "" },
                        { v -> onChange(rows.mapIndexed { i, rr -> if (i == r) (0 until cols).map { k -> if (k == col) v else rr.getOrElse(k) { "" } } else rr }) },
                        modifier = Modifier.width(150.dp),
                        textStyle = if (r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        singleLine = false,
                    )
                }
                if (r > 0) IconButton(onClick = { onChange(rows.filterIndexed { i, _ -> i != r }) }) { Icon(Icons.Outlined.Close, contentDescription = "Zeile löschen", modifier = Modifier.size(16.dp)) }
            }
        }
        Row {
            TextButton(onClick = { onChange(rows + listOf(List(cols) { "" })) }) { Text("+ Zeile") }
            TextButton(onClick = { onChange(rows.map { it + "" }) }) { Text("+ Spalte") }
            if (cols > 1) TextButton(onClick = { onChange(rows.map { it.dropLast(1) }) }) { Text("− Spalte") }
        }
    }
}

@Composable
private fun NewCommentDialog(doc: LiveDoc, quote: String, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var toAi by remember { mutableStateOf(doc.sendToAi) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kommentar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (quote.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                    Text("„${quote.take(200)}“", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                }
                OutlinedTextField(text, { text = it }, placeholder = { Text("z.B. Kürzer und förmlicher formulieren") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { toAi = !toAi }) {
                    Checkbox(toAi, { toAi = it })
                    Column {
                        Text("An KI senden")
                        HintText("Die KI ändert die Stelle und antwortet im Kommentar.")
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim(), toAi) }) { Text("Kommentieren") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun CommentsPanel(c: AppContainer, doc: LiveDoc, tabId: String?, onAdd: () -> Unit) {
    var showResolved by remember { mutableStateOf(false) }
    val list = doc.comments.filter { showResolved || !it.resolved }.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kommentare", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            FilterChip(showResolved, { showResolved = !showResolved }, label = { Text("Erledigte") })
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.AddComment, contentDescription = "Kommentar hinzufügen") }
        }
        if (list.isEmpty()) HintText("Noch keine Kommentare. Absatz antippen und das Sprechblasen-Symbol wählen.", Modifier.padding(12.dp))
        LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { cm ->
                var reply by remember { mutableStateOf("") }
                AppCard {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (cm.quote.isNotBlank()) Text("„${cm.quote.take(140)}“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(cm.text, style = MaterialTheme.typography.bodyMedium)
                        HintText("${cm.author} · ${time(cm.createdAt)}" + (if (doc.tabs.size > 1 && cm.tabId != tabId) " · Tab „${doc.tab(cm.tabId).title}“" else "") + if (cm.toAi) " · an KI" else "")
                        cm.replies.forEach { r ->
                            Surface(color = if (r.ai) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(r.text, style = MaterialTheme.typography.bodySmall)
                                    HintText("${r.author} · ${time(r.createdAt)}")
                                }
                            }
                        }
                        if (!cm.resolved) {
                            OutlinedTextField(reply, { reply = it }, placeholder = { Text("Antworten") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        Row {
                            if (reply.isNotBlank()) TextButton(onClick = {
                                c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(replies = it.replies + DocReply("Sie", reply.trim())) else it }) }
                                if (cm.toAi) DocsAi.send(c, doc, "Antwort im Kommentar", reply.trim(), DocsAi.commentInstructions(doc, cm))
                                reply = ""
                            }) { Text("Senden") }
                            if (!cm.toAi && !cm.resolved) TextButton(onClick = {
                                c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(toAi = true) else it }) }
                                DocsAi.send(c, doc, "Kommentar im Dokument", "Kommentar zu „${cm.quote.take(120)}“: ${cm.text}", DocsAi.commentInstructions(doc, cm))
                            }) { Text("An KI") }
                            TextButton(onClick = { c.docs.update(doc.id) { d -> d.copy(comments = d.comments.map { if (it.id == cm.id) it.copy(resolved = !it.resolved) else it }) } }) {
                                Text(if (cm.resolved) "Wieder öffnen" else "Erledigt")
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
        title = { Text(if (selected == null) "Versionsverlauf" else "Version vom ${time(selected!!.createdAt)}") },
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
                    HintText("Unterschiede dieser Version zum aktuellen Stand (${TextDiff.summary(pieces)}): grün = seitdem hinzugekommen, rot = seitdem entfernt.")
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
            if (sel == null) TextButton(onClick = onDismiss) { Text("Schließen") }
            else Row {
                TextButton(onClick = {
                    busy = true
                    scope.launch {
                        val file = runCatching {
                            withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                val f = File(dir, DocumentWriter.safeName("${doc.title} (Änderungen)") + ".docx")
                                MarkdownDocx.writeTracked(
                                    f, doc.title, DocumentWriter.withTitle(sel.title, sel.tabs.joinToString("\n\n") { it.markdown }),
                                    DocumentWriter.withTitle(doc.title, doc.markdown()), c.documentTools.settingsTheme(DocStyle.parse(doc.style)), author = "ADEU LLM",
                                )
                                f
                            }
                        }
                        busy = false
                        file.onSuccess { saveAs(it) }.onFailure { Notices.error("Word-Export fehlgeschlagen: ${it.message}") }
                    }
                }) { Text("Word mit Änderungen") }
                TextButton(onClick = {
                    c.docs.save(doc.change("Sie", "Version vom ${time(sel.createdAt)} wiederhergestellt") { it.copy(title = sel.title, tabs = sel.tabs) })
                    Notices.info("Version wiederhergestellt")
                    onDismiss()
                }) { Text("Wiederherstellen") }
            }
        },
        dismissButton = { if (selected != null) TextButton(onClick = { selected = null }) { Text("Zurück") } },
    )
}

/** All documents of the editor, newest first (like the Artifacts tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsListScreen(c: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val docs by c.docs.items.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = docs.filter { query.isBlank() || it.title.contains(query, true) || it.tabs.any { t -> t.markdown.contains(query, true) } }
    val importFile = rememberFileImport(arrayOf("text/*", "*/*")) { input ->
        val text = input.readBytes().toString(Charsets.UTF_8)
        val doc = LiveDoc.create(DocBlocks.titleOf(text) ?: "Importiertes Dokument", text, "Sie")
        c.docs.save(doc)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) }, title = { Text("Dokumente") },
                actions = {
                    TextButton(onClick = importFile) { Text("Importieren") }
                    TextButton(onClick = {
                        val doc = LiveDoc.create("Unbenanntes Dokument", "# Unbenanntes Dokument\n\n", "Sie")
                        c.docs.save(doc)
                        onOpen(doc.id)
                    }) { Text("Neu") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Dokumente durchsuchen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (shown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Noch keine Dokumente", style = MaterialTheme.typography.titleSmall)
                        HintText("Dokumente entstehen, wenn die KI im Chat ein Dokument schreibt, über „Im Editor öffnen“ an einer Antwort oder mit „Neu“.")
                    }
                }
                items(shown, key = { it.id }) { d ->
                    AppCard(onClick = { onOpen(d.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                HintText("${time(d.updatedAt)} · ${d.lastAuthor} · ${d.words} Wörter" + (if (d.tabs.size > 1) " · ${d.tabs.size} Tabs" else "") + (if (d.openComments > 0) " · ${d.openComments} Kommentare" else ""))
                            }
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Duplizieren") }, onClick = {
                                        menu = false
                                        c.docs.save(LiveDoc.create(d.title + " (Kopie)", d.markdown(), "Sie").copy(tabs = d.tabs.map { it.copy(id = java.util.UUID.randomUUID().toString()) }))
                                    })
                                    DropdownMenuItem(text = { Text("Löschen") }, onClick = {
                                        menu = false
                                        c.docs.delete(d.id)
                                        Notices.undo("Dokument gelöscht", restore = { c.docs.save(d) })
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
