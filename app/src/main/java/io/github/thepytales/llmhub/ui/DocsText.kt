package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.LiveDoc
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How the text of a document is shown: editable blocks, the Markdown source (like Obsidian) or for reading. */
enum class TextMode(val label: String) { EDIT("Bearbeiten"), SOURCE("Markdown-Quelltext"), READ("Lesen") }

/**
 * The block being edited, hoisted so the formatting bar above the page works on it. Edits are saved after a short
 * pause and when another block is chosen, so nothing typed gets lost.
 */
class TextEditing {
    var index by mutableStateOf<Int?>(null)
    var value by mutableStateOf(TextFieldValue(""))

    fun start(i: Int, block: String, caretAtEnd: Boolean = true) {
        index = i
        value = TextFieldValue(block, TextRange(if (caretAtEnd) block.length else 0))
    }

    /** Selected text in the edited block, or null. */
    val selection: String? get() = value.selection.takeIf { !it.collapsed && index != null }?.let { value.text.substring(it.min, it.max) }
}

/** Templates offered on an empty document. */
val DOC_STARTERS = listOf(
    "Protokoll" to "# Protokoll\n\n**Datum:** \n**Teilnehmende:** \n**Protokoll:** \n\n## Tagesordnung\n\n1. \n\n## Ergebnisse und Beschlüsse\n\n- \n\n## Aufgaben\n\n- [ ] \n",
    "Seminarplan" to "# Seminarplan\n\n| Sitzung | Datum | Thema | Lektüre |\n| --- | --- | --- | --- |\n| 1 |  | Einführung |  |\n| 2 |  |  |  |\n",
    "Gliederung" to "# Titel\n\n## Einleitung\n\n## Theoretischer Hintergrund\n\n## Methode\n\n## Ergebnisse\n\n## Diskussion\n\n## Fazit\n",
    "Besprechungsnotiz" to "# Besprechung\n\n## Ziele\n\n- \n\n## Notizen\n\n\n\n## Nächste Schritte\n\n- [ ] \n",
    "Aufgabenliste" to "# Aufgaben\n\n- [ ] \n- [ ] \n- [ ] \n",
)

/**
 * Toolbar for text documents (like Google Docs): block type, bold, italic, strikethrough, code, link, lists,
 * checklist, quote, table, line, date, ✨ and comment. Works on the edited block, otherwise inserts at the end.
 */
@Composable
fun TextToolbar(
    editing: TextEditing,
    blocks: List<String>,
    leading: @Composable () -> Unit,
    onBlocks: (List<String>, Int?) -> Unit,
    onAi: () -> Unit,
    onComment: () -> Unit,
    onDoneEditing: () -> Unit,
) {
    val i = editing.index
    fun wrap(mark: String, end: String = mark) {
        val v = editing.value
        val sel = v.selection
        val t = v.text
        val inner = t.substring(sel.min, sel.max)
        // Without selection the marks go around the word at the cursor.
        val (a, b) = if (sel.collapsed) {
            var s = sel.min
            var e = sel.max
            while (s > 0 && t[s - 1].isLetterOrDigit()) s--
            while (e < t.length && t[e].isLetterOrDigit()) e++
            s to e
        } else sel.min to sel.max
        val word = if (sel.collapsed) t.substring(a, b) else inner
        // Already marked: remove the marks again.
        if (a >= mark.length && t.startsWith(mark, a - mark.length) && t.startsWith(end, b)) {
            val next = t.substring(0, a - mark.length) + word + t.substring(b + end.length)
            editing.value = TextFieldValue(next, TextRange(a - mark.length, b - mark.length))
            return
        }
        val next = t.substring(0, a) + mark + word + end + t.substring(b)
        editing.value = TextFieldValue(next, TextRange(a + mark.length, b + mark.length))
    }

    fun setKind(k: DocBlocks.Kind) {
        if (i != null) {
            val converted = DocBlocks.convert(editing.value.text.ifBlank { " " }, k).let { if (it.isBlank()) DocBlocks.convert("Text", k) else it }
            editing.value = TextFieldValue(converted, TextRange(converted.length))
        } else {
            val new = DocBlocks.convert(if (k == DocBlocks.Kind.TABLE || k == DocBlocks.Kind.RULE) "" else "Text", k)
            onBlocks(blocks + new, blocks.size)
        }
    }

    fun insert(block: String) {
        val at = (i ?: blocks.lastIndex) + 1
        onBlocks(blocks.toMutableList().apply { add(at.coerceIn(0, size), block) }, if (block == "---") null else at)
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        var kinds by remember { mutableStateOf(false) }
        Box {
            ToolLabel((if (i != null) DocBlocks.kindOf(editing.value.text).label else "Einfügen") + " ▾") { kinds = true }
            DropdownMenu(expanded = kinds, onDismissRequest = { kinds = false }) {
                DocBlocks.Kind.entries.forEach { k ->
                    DropdownMenuItem(text = { Text(k.label, style = kindStyle(k)) }, onClick = { kinds = false; setKind(k) })
                }
            }
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        val text = i != null
        ToolIcon(Icons.Outlined.FormatBold, "Fett (Strg+B)", enabled = text) { wrap("**") }
        ToolIcon(Icons.Outlined.FormatItalic, "Kursiv (Strg+I)", enabled = text) { wrap("*") }
        ToolIcon(Icons.Outlined.FormatStrikethrough, "Durchgestrichen", enabled = text) { wrap("~~") }
        ToolIcon(Icons.Outlined.Code, "Code", enabled = text) { wrap("`") }
        var link by remember { mutableStateOf(false) }
        ToolIcon(Icons.Outlined.Link, "Link (Strg+K)", enabled = text) { link = true }
        if (link) TextPrompt("Link einfügen (Adresse)", "https://", onDismiss = { link = false }) { url ->
            link = false
            wrap("[", "]($url)")
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Aufzählung") { setKind(DocBlocks.Kind.BULLETS) }
        ToolIcon(Icons.Outlined.FormatListNumbered, "Nummerierte Liste") { setKind(DocBlocks.Kind.NUMBERS) }
        ToolIcon(Icons.Outlined.CheckBox, "Checkliste") { setKind(DocBlocks.Kind.TASKS) }
        ToolIcon(Icons.Outlined.FormatQuote, "Zitat") { setKind(DocBlocks.Kind.QUOTE) }
        ToolIcon(Icons.Outlined.TableChart, "Tabelle einfügen") { insert("| Spalte 1 | Spalte 2 | Spalte 3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |") }
        ToolIcon(Icons.Outlined.HorizontalRule, "Trennlinie") { insert("---") }
        ToolIcon(Icons.Outlined.Today, "Heutiges Datum") {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            if (i != null) {
                val v = editing.value
                editing.value = TextFieldValue(v.text.substring(0, v.selection.min) + date + v.text.substring(v.selection.max), TextRange(v.selection.min + date.length))
            } else insert(date)
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        ToolIcon(Icons.Outlined.AutoAwesome, "KI für diese Stelle", enabled = text, onClick = onAi)
        ToolIcon(Icons.Outlined.AddComment, "Kommentieren (Strg+Alt+M)", onClick = onComment)
        if (text) ToolIcon(Icons.Outlined.KeyboardHide, "Fertig (Esc)", onClick = onDoneEditing)
    }
}

@Composable
private fun kindStyle(k: DocBlocks.Kind): TextStyle = when (k) {
    DocBlocks.Kind.H1 -> MaterialTheme.typography.titleLarge
    DocBlocks.Kind.H2 -> MaterialTheme.typography.titleMedium
    DocBlocks.Kind.H3 -> MaterialTheme.typography.titleSmall
    DocBlocks.Kind.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    else -> MaterialTheme.typography.bodyMedium
}

/** Slash commands: "/" at the start of an empty block offers block types (like Notion and Obsidian). */
private val SLASH = listOf(
    "Überschrift 1" to "# ", "Überschrift 2" to "## ", "Überschrift 3" to "### ", "Aufzählung" to "- ", "Nummerierte Liste" to "1. ",
    "Checkliste" to "- [ ] ", "Zitat" to "> ", "Hinweisbox" to "> [!NOTE]\n> ", "Tabelle" to "| Spalte 1 | Spalte 2 |\n| --- | --- |\n|  |  |",
    "Code" to "```\n\n```", "Trennlinie" to "---", "Datum" to "",
)

/**
 * Content of a paragraph that was just started and is still empty. Markdown has no empty paragraphs, so without it a
 * new paragraph would vanish (and the indexes of the following ones shift) before anything is typed into it.
 */
const val EMPTY_BLOCK = "\u200B"

/**
 * The pages of a text document: blocks to read and edit one by one, checklists to tick, highlighted changes of the
 * model, comments and ✨ suggestions below the passage they belong to.
 */
@Composable
fun TextPage(
    doc: LiveDoc?,
    tabId: String?,
    blocks: List<String>,
    mode: TextMode,
    writing: Boolean,
    aiChanged: Set<Int>,
    find: String,
    editing: TextEditing,
    inlineAi: InlineAiRunner,
    listState: LazyListState,
    narrow: Boolean,
    onBlocks: (List<String>) -> Unit,
    onComment: (String) -> Unit,
    onShowComments: () -> Unit,
    onAskAi: (String) -> Unit,
    footer: String,
) {
    val editable = doc != null && !writing && mode == TextMode.EDIT
    val look = LocalChatLook.current

    /** The blocks with the edited one closed: its text saved, removed when it stayed empty. */
    fun closed(): List<String> {
        val i = editing.index ?: return blocks
        val text = editing.value.text.replace(EMPTY_BLOCK, "")
        editing.index = null
        if (i !in blocks.indices) return blocks
        return if (text.isBlank()) blocks.toMutableList().apply { removeAt(i) } else blocks.toMutableList().apply { set(i, text) }
    }

    /** Saves the edited block (also while typing, after a pause); [close] ends editing. */
    fun commit(close: Boolean) {
        if (close) {
            val next = closed()
            if (next != blocks) onBlocks(next)
            return
        }
        val i = editing.index ?: return
        val text = editing.value.text.replace(EMPTY_BLOCK, "").ifBlank { EMPTY_BLOCK }
        if (i in blocks.indices && text != blocks[i]) onBlocks(blocks.toMutableList().apply { set(i, text) })
    }
    // Effects outlive a composition: they must call the newest commit, which knows the current blocks.
    val commitLatest by rememberUpdatedState(::commit)
    LaunchedEffect(editing.value.text) {
        delay(1200)
        commitLatest(false)
    }
    DisposableEffect(tabId, mode) { onDispose { commitLatest(true) } }

    fun startNew(at: Int, list: List<String>) {
        onBlocks(list.toMutableList().apply { add(at.coerceIn(0, size), EMPTY_BLOCK) })
        editing.start(at.coerceIn(0, list.size), "")
    }

    fun edit(i: Int) {
        if (!editable || editing.index == i) return
        val before = editing.index
        val list = closed()
        // Closing removed an empty paragraph above: the chosen one moved up.
        val target = if (before != null && before < i && list.size < blocks.size) i - 1 else i
        if (list != blocks) onBlocks(list)
        list.getOrNull(target)?.let { editing.start(target, it.replace(EMPTY_BLOCK, "")) }
    }

    val pad = if (narrow) 20.dp else 56.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = if (narrow) 8.dp else 24.dp, end = if (narrow) 8.dp else 24.dp, top = 16.dp, bottom = 120.dp),
        ) {
            item(key = "top") {
                Box(Modifier.fillMaxWidth().height(if (narrow) 20.dp else 48.dp).background(look.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
            }
            itemsIndexed(blocks, key = { i, _ -> "b$i" }) { i, block ->
                Column(Modifier.fillMaxWidth().background(look.card).padding(horizontal = pad)) {
                    if (editing.index == i && editable) {
                        BlockField(
                            editing = editing,
                            onEnterAtEnd = {
                                val text = editing.value.text.replace(EMPTY_BLOCK, "").ifBlank { EMPTY_BLOCK }
                                startNew(i + 1, blocks.toMutableList().apply { set(i, text) })
                            },
                            onBackspaceEmpty = {
                                if (i > 0) {
                                    val prev = blocks[i - 1]
                                    editing.index = null
                                    onBlocks(blocks.toMutableList().apply { removeAt(i) })
                                    editing.start(i - 1, prev.replace(EMPTY_BLOCK, ""))
                                }
                            },
                            onDone = { commit(close = true) },
                            onMove = { delta ->
                                val j = i + delta
                                if (j in blocks.indices) {
                                    val list = blocks.toMutableList().apply { set(i, editing.value.text.ifBlank { EMPTY_BLOCK }) }
                                    val x = list.removeAt(i)
                                    list.add(j, x)
                                    editing.index = j
                                    onBlocks(list)
                                }
                            },
                            onDelete = {
                                editing.index = null
                                onBlocks(blocks.toMutableList().apply { removeAt(i) })
                            },
                            onComment = { onComment(editing.selection ?: DocBlocks.plainOf(editing.value.text).take(200)) },
                            onAi = { onAskAi(editing.selection ?: editing.value.text) },
                        )
                    } else {
                        BlockView(
                            doc, tabId, block.replace(EMPTY_BLOCK, ""), highlight = i in aiChanged || find.isNotBlank() && block.contains(find, true),
                            editable = editable,
                            onClick = { edit(i) },
                            onToggleTask = { n -> onBlocks(blocks.toMutableList().apply { set(i, DocBlocks.toggleTask(block, n)) }) },
                            onShowComments = onShowComments,
                        )
                    }
                    inlineAi.state?.takeIf { it.first == "b$i" }?.second?.let { s ->
                        AiSuggestionCard(
                            s,
                            onAccept = {
                                val current = if (editing.index == i) editing.value.text else block.replace(EMPTY_BLOCK, "")
                                val next = if (s.original.isNotBlank() && s.original != current && s.original in current) current.replaceFirst(s.original, s.text) else s.text
                                if (editing.index == i) editing.value = TextFieldValue(next, TextRange(next.length))
                                onBlocks(blocks.toMutableList().apply { set(i, next) })
                                inlineAi.clear()
                            },
                            onDiscard = { inlineAi.clear() },
                            onRetry = { inlineAi.retry(doc) },
                            onInsertBelow = {
                                onBlocks(blocks.toMutableList().apply { add(i + 1, s.text) })
                                inlineAi.clear()
                            },
                        )
                    }
                }
            }
            item(key = "end") {
                Column(
                    Modifier.fillMaxWidth().background(look.card, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)).padding(horizontal = pad, vertical = 12.dp),
                ) {
                    when {
                        writing -> WritingCaret()
                        doc != null && mode == TextMode.EDIT && editing.index == null && DocBlocks.plainOf(blocks.drop(1).joinToString("").replace(EMPTY_BLOCK, "")).isBlank() -> EmptyDocStart(
                            onTemplate = { md -> onBlocks(DocBlocks.split(md)) },
                            onAi = { onAskAi("") },
                            onWrite = { closed().let { startNew(it.size, it) } },
                        )
                        editable -> Row(
                            Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { closed().let { startNew(it.size, it) } },
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hier weiterschreiben – „/“ für Formate", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            item(key = "footer") { HintText(footer, Modifier.padding(top = 10.dp, start = 4.dp)) }
        }
    }
}

/** Start of an empty document: write, use a template or let the model draft it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyDocStart(onTemplate: (String) -> Unit, onAi: () -> Unit, onWrite: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onWrite).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Einfach lostippen …", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
        Surface(onClick = onAi, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Mit KI entwerfen", style = MaterialTheme.typography.titleSmall)
                    Text("Beschreibe, was entstehen soll – die KI schreibt den ersten Entwurf direkt hier hinein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Oder mit einer Vorlage beginnen:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DOC_STARTERS.forEach { (label, md) -> OutlinedButton(onClick = { onTemplate(md) }) { Text(label) } }
        }
    }
}

/** A block as it will look in the document; checklists can be ticked right here. */
@Composable
private fun BlockView(
    doc: LiveDoc?,
    tabId: String?,
    block: String,
    highlight: Boolean,
    editable: Boolean,
    onClick: () -> Unit,
    onToggleTask: (Int) -> Unit,
    onShowComments: () -> Unit,
) {
    val commented = doc?.comments?.any { !it.resolved && it.tabId == tabId && it.quote.isNotBlank() && (it.quote in block || DocBlocks.plainOf(block).contains(it.quote.take(60))) } == true
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(enabled = editable, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) {
            when {
                block.isBlank() -> Text(" ", style = MaterialTheme.typography.bodyLarge)
                DocBlocks.kindOf(block) == DocBlocks.Kind.TASKS -> Column {
                    DocBlocks.tasks(block).forEachIndexed { n, (text, done) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(done, { onToggleTask(n) }, enabled = editable, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1f)) {
                                if (done) Text(text, style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.outline)
                                else MarkdownText(text.ifBlank { " " })
                            }
                        }
                    }
                }
                else -> MarkdownText(block)
            }
        }
        if (commented) {
            Icon(
                Icons.Outlined.AddComment, contentDescription = "Kommentiert", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp).size(16.dp).clickable(onClick = onShowComments),
            )
        }
    }
}

/** The edited block: plain text field without frame (like typing on the page), small actions below. */
@Composable
private fun BlockField(
    editing: TextEditing,
    onEnterAtEnd: () -> Unit,
    onBackspaceEmpty: () -> Unit,
    onDone: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onComment: () -> Unit,
    onAi: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(editing.index) { runCatching { focus.requestFocus() } }
    val v = editing.value
    val kind = DocBlocks.kindOf(v.text)
    val style = when (kind) {
        DocBlocks.Kind.H1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        DocBlocks.Kind.H2 -> MaterialTheme.typography.titleLarge
        DocBlocks.Kind.H3 -> MaterialTheme.typography.titleMedium
        DocBlocks.Kind.CODE, DocBlocks.Kind.TABLE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else -> MaterialTheme.typography.bodyLarge
    }.copy(color = MaterialTheme.colorScheme.onSurface)
    val slash = v.text.startsWith("/") && '\n' !in v.text && ' ' !in v.text.trim()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (kind == DocBlocks.Kind.TABLE) {
            TableGrid(DocBlocks.tableRows(v.text)) { rows -> editing.value = TextFieldValue(DocBlocks.table(rows)) }
        } else Box {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = v,
                    onValueChange = { editing.value = it },
                    textStyle = style,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val listLike = kind in setOf(DocBlocks.Kind.BULLETS, DocBlocks.Kind.NUMBERS, DocBlocks.Kind.TASKS)
                        when {
                            e.key == Key.Escape -> { onDone(); true }
                            e.isCtrlPressed && e.isAltPressed && e.key == Key.M -> { onComment(); true }
                            e.isCtrlPressed && e.key == Key.J -> { onAi(); true }
                            e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionUp -> { onMove(-1); true }
                            e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionDown -> { onMove(1); true }
                            e.key == Key.Backspace && v.text.isEmpty() -> { onBackspaceEmpty(); true }
                            // Enter at the end of a paragraph or heading starts a new block (Umschalt+Enter: new line).
                            e.key == Key.Enter && !e.isShiftPressed && !listLike && kind !in setOf(DocBlocks.Kind.CODE, DocBlocks.Kind.TABLE, DocBlocks.Kind.QUOTE, DocBlocks.Kind.NOTE) &&
                                v.selection.collapsed && v.selection.end == v.text.length && !slash -> { onEnterAtEnd(); true }
                            // In lists Enter continues the list; on an empty item it ends the list.
                            e.key == Key.Enter && !e.isShiftPressed && listLike && v.selection.collapsed && v.selection.end == v.text.length -> {
                                val last = v.text.lines().last()
                                val marker = Regex("^(\\s*)([-*+]\\s\\[[ xX]]\\s|[-*+]\\s|(\\d+)[.)]\\s)").find(last)
                                if (marker != null && last.substring(marker.range.last + 1).isBlank()) {
                                    editing.value = TextFieldValue(v.text.lines().dropLast(1).joinToString("\n"))
                                    onEnterAtEnd()
                                } else {
                                    val next = when {
                                        marker == null -> "- "
                                        marker.groupValues[3].isNotEmpty() -> marker.groupValues[1] + (marker.groupValues[3].toInt() + 1) + ". "
                                        marker.groupValues[2].contains("[") -> marker.groupValues[1] + "- [ ] "
                                        else -> marker.groupValues[1] + marker.groupValues[2]
                                    }
                                    val t = v.text + "\n" + next
                                    editing.value = TextFieldValue(t, TextRange(t.length))
                                }
                                true
                            }
                            e.key == Key.Tab && listLike -> {
                                val lines = v.text.lines().toMutableList()
                                val l = lines.last()
                                lines[lines.lastIndex] = if (e.isShiftPressed) l.removePrefix("  ") else "  $l"
                                val t = lines.joinToString("\n")
                                editing.value = TextFieldValue(t, TextRange(t.length))
                                true
                            }
                            else -> false
                        }
                    },
                    decorationBox = { inner ->
                        if (v.text.isEmpty()) Text("Schreiben … („/“ für Formate)", style = style.copy(color = MaterialTheme.colorScheme.outline))
                        inner()
                    },
                )
            }
            DropdownMenu(expanded = slash, onDismissRequest = { editing.value = TextFieldValue("") }) {
                val q = v.text.removePrefix("/").lowercase()
                SLASH.filter { q.isEmpty() || it.first.lowercase().contains(q) }.forEach { (label, md) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        val t = if (label == "Datum") LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) else md
                        editing.value = TextFieldValue(t, TextRange(if (t.startsWith("```")) 4 else t.length))
                    })
                }
                DropdownMenuItem(
                    text = { Text("KI schreiben lassen …") },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) },
                    onClick = { editing.value = TextFieldValue(""); onAi() },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            HintText(
                when (kind) {
                    DocBlocks.Kind.TABLE -> "Zellen direkt bearbeiten · Esc: fertig"
                    DocBlocks.Kind.BULLETS, DocBlocks.Kind.NUMBERS, DocBlocks.Kind.TASKS -> "Enter: neuer Punkt · Tab: einrücken"
                    else -> "Enter: neuer Absatz · Strg+J: KI"
                },
                Modifier.weight(1f),
            )
            IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "Nach oben", modifier = Modifier.size(16.dp)) }
            IconButton(onClick = { onMove(1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "Nach unten", modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, contentDescription = "Block löschen", modifier = Modifier.size(16.dp)) }
            TextButton(onClick = onDone) { Text("Fertig") }
        }
    }
}

/** The whole tab as Markdown in one field (Obsidian's source mode); saved after a short pause. */
@Composable
fun SourceEditor(markdown: String, tabId: String?, onChange: (String) -> Unit) {
    var value by remember(tabId) { mutableStateOf(TextFieldValue(markdown)) }
    LaunchedEffect(markdown) { if (markdown != value.text) value = value.copy(text = markdown) }
    LaunchedEffect(value.text) {
        delay(700)
        if (value.text != markdown) onChange(value.text)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Surface(
            color = LocalChatLook.current.card, shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize().padding(16.dp),
        ) {
            BasicTextField(
                value = value, onValueChange = { value = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            )
        }
    }
}

/** Outline of the tab (headings to jump to) and links between documents ([[Titel]]), like in Obsidian. */
@Composable
fun OutlinePanel(c: AppContainer, doc: LiveDoc, blocks: List<String>, onJump: (Int) -> Unit, onOpenDoc: (String) -> Unit, onClose: () -> Unit) {
    val headings = blocks.mapIndexedNotNull { i, b ->
        when (DocBlocks.kindOf(b)) {
            DocBlocks.Kind.H1 -> Triple(i, 0, DocBlocks.plainOf(b))
            DocBlocks.Kind.H2 -> Triple(i, 1, DocBlocks.plainOf(b))
            DocBlocks.Kind.H3 -> Triple(i, 2, DocBlocks.plainOf(b))
            else -> null
        }
    }
    val all = c.docs.current
    val linksTo = Regex("\\[\\[([^\\]|]+)(\\|[^\\]]*)?]]").findAll(doc.markdown()).map { it.groupValues[1].trim() }.toSet()
        .mapNotNull { t -> all.firstOrNull { it.id != doc.id && it.title.equals(t, true) } }
    val backlinks = all.filter { d -> d.id != doc.id && d.tabs.any { it.markdown.contains("[[${doc.title}", ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Gliederung", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Schließen") }
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(10.dp)) {
            if (headings.isEmpty()) item { HintText("Noch keine Überschriften. Überschriften gliedern das Dokument und erscheinen hier zum Springen.") }
            headings.forEach { (i, level, text) ->
                item {
                    Text(
                        text.ifBlank { "(ohne Text)" },
                        style = if (level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().clickable { onJump(i) }.padding(start = (level * 14).dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
            item {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Verknüpfungen", style = MaterialTheme.typography.titleSmall)
                    HintText("Mit [[Titel]] verlinkst du andere Dokumente.")
                    linksTo.forEach { d -> TextButton(onClick = { onOpenDoc(d.id) }) { Text("→ ${d.title}") } }
                    if (backlinks.isNotEmpty()) Text("Verlinkt von", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    backlinks.forEach { d -> TextButton(onClick = { onOpenDoc(d.id) }) { Text("← ${d.title}") } }
                }
            }
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
                    androidx.compose.material3.OutlinedTextField(
                        row.getOrElse(col) { "" },
                        { v -> onChange(rows.mapIndexed { i, rr -> if (i == r) (0 until cols).map { k -> if (k == col) v else rr.getOrElse(k) { "" } } else rr }) },
                        modifier = Modifier.width(150.dp),
                        textStyle = if (r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
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
