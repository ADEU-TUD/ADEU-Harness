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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.AiReview
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.LiveDoc
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** How the text of a document is shown: editable blocks, the Markdown source (like Obsidian) or for reading. */
enum class TextMode(val label: String) { EDIT(trLater("Bearbeiten")), SOURCE("Markdown-Quelltext"), READ(trLater("Lesen")) }

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
    "Elternbrief" to "# Elternbrief: [Anlass]\n\nSehr geehrte Eltern und Erziehungsberechtigte der Klasse [Klasse],\n\n[Worum es geht – Termin, Ort, was mitzubringen ist.]\n\n" +
        "| Angabe | Eintrag |\n| --- | --- |\n| Datum | |\n| Uhrzeit | |\n| Ort | |\n| Kosten | |\n\nBitte geben Sie den Abschnitt unten bis zum [Datum] zurück.\n\n" +
        "Mit freundlichen Grüßen\n\n[Name], Klassenleitung\n\n---\n\n**Rückmeldung** (bitte abtrennen)\n\nName des Kindes: ________________\n\n- [ ] Mein Kind nimmt teil.\n- [ ] Mein Kind nimmt nicht teil.\n\n" +
        "| Ort, Datum | Unterschrift |\n| --- | --- |\n| | |\n",
    "Unterrichtsentwurf" to "# Unterrichtsentwurf: [Thema]\n\n| Angabe | Eintrag |\n| --- | --- |\n| Fach / Klasse | |\n| Datum, Zeit | |\n| Lehrkraft | |\n\n" +
        "## 1. Bedingungsanalyse\n\n## 2. Sachanalyse\n\n## 3. Didaktische Analyse\n\n## 4. Lernziele\n\n- Die Schüler:innen können …\n\n## 5. Methodische Überlegungen\n\n" +
        "## 6. Verlaufsplan\n\n| Zeit | Phase | Inhalt / Aktivität | Sozialform | Medien |\n| --- | --- | --- | --- | --- |\n| 5 min | Einstieg | | Plenum | |\n| 20 min | Erarbeitung | | Partnerarbeit | |\n| 15 min | Sicherung | | Plenum | |\n| 5 min | Abschluss | | | |\n\n## 7. Literatur\n",
    "Dienstreiseantrag" to "# Antrag auf Dienstreise / Fortbildung\n\n| Angabe | Eintrag |\n| --- | --- |\n| Name, Vorname | |\n| Einrichtung | |\n| Anlass / Veranstaltung | |\n| Ort | |\n" +
        "| Zeitraum (mit Uhrzeiten) | |\n| Verkehrsmittel | |\n| Voraussichtliche Kosten | |\n| Kostenstelle / Projekt | |\n\n## Begründung\n\n[Dienstlicher Nutzen, Bezug zu Lehre oder Forschung]\n\n" +
        "## Vertretung\n\n- [ ] Lehrveranstaltungen sind vertreten bzw. verlegt.\n- [ ] Keine Lehrveranstaltungen betroffen.\n\n| Ort, Datum | Unterschrift |\n| --- | --- |\n| | |\n",
)

/** What the user does with a change of the model in the text (accept, take back, restore a removed block). */
sealed interface ReviewAction {
    data class Accept(val change: AiReview.Change) : ReviewAction
    data class Reject(val change: AiReview.Change) : ReviewAction
    data class Restore(val removed: AiReview.Removed) : ReviewAction
    data class Drop(val removed: AiReview.Removed) : ReviewAction
}

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
    /** Opens the picture picker; null where pictures cannot be stored. */
    onImage: (() -> Unit)? = null,
    /** Opens "Literatur einfügen". */
    onCitation: (() -> Unit)? = null,
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

    /** The blocks with the edited one as it is in the field. */
    fun current(): MutableList<String> = blocks.toMutableList().apply { if (i != null && i in indices) set(i, editing.value.text.ifBlank { EMPTY_BLOCK }) }

    /** Footnote: the mark at the cursor (or at the end of the text), the note to write at the end of the document. */
    fun footnote() {
        val list = current()
        val label = DocMarkup.nextNoteLabel(list.joinToString("\n\n"))
        val ref = "[^$label]"
        if (i != null && i in list.indices) {
            val v = editing.value
            list[i] = v.text.substring(0, v.selection.max) + ref + v.text.substring(v.selection.max)
        } else {
            val last = list.indexOfLast { DocBlocks.kindOf(it) == DocBlocks.Kind.PARAGRAPH && !DocMarkup.isComment(it) }
            if (last < 0) list += "Text$ref" else list[last] = list[last] + ref
        }
        list += "[^$label]: "
        onBlocks(list, list.lastIndex)
    }

    /** Landscape pages after the edited block: start mark, a paragraph to write in, end mark. */
    fun landscape() {
        val list = current()
        val at = ((i ?: list.lastIndex) + 1).coerceIn(0, list.size)
        list.addAll(at, listOf(DocMarkup.LANDSCAPE_START, tr("Inhalt im Querformat, z. B. eine breite Tabelle"), DocMarkup.LANDSCAPE_END))
        onBlocks(list, at + 1)
    }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        var kinds by remember { mutableStateOf(false) }
        Box {
            ToolLabel((if (i != null) DocBlocks.kindOf(editing.value.text).label else tr("Einfügen")) + " ▾") { kinds = true }
            DropdownMenu(expanded = kinds, onDismissRequest = { kinds = false }) {
                DocBlocks.Kind.entries.forEach { k ->
                    DropdownMenuItem(text = { Text(k.label, style = kindStyle(k)) }, onClick = { kinds = false; setKind(k) })
                }
            }
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        val text = i != null
        ToolIcon(Icons.Outlined.FormatBold, tr("Fett (Strg+B)"), enabled = text) { wrap("**") }
        ToolIcon(Icons.Outlined.FormatItalic, tr("Kursiv (Strg+I)"), enabled = text) { wrap("*") }
        ToolIcon(Icons.Outlined.FormatStrikethrough, tr("Durchgestrichen"), enabled = text) { wrap("~~") }
        ToolIcon(Icons.Outlined.Code, tr("Code"), enabled = text) { wrap("`") }
        var link by remember { mutableStateOf(false) }
        ToolIcon(Icons.Outlined.Link, tr("Link (Strg+K)"), enabled = text) { link = true }
        if (link) TextPrompt(tr("Link einfügen (Adresse)"), "https://", onDismiss = { link = false }) { url ->
            link = false
            wrap("[", "]($url)")
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, tr("Aufzählung")) { setKind(DocBlocks.Kind.BULLETS) }
        ToolIcon(Icons.Outlined.FormatListNumbered, tr("Nummerierte Liste")) { setKind(DocBlocks.Kind.NUMBERS) }
        ToolIcon(Icons.Outlined.CheckBox, tr("Checkliste")) { setKind(DocBlocks.Kind.TASKS) }
        ToolIcon(Icons.Outlined.FormatQuote, tr("Zitat")) { setKind(DocBlocks.Kind.QUOTE) }
        ToolIcon(Icons.Outlined.TableChart, tr("Tabelle einfügen")) { insert("| Spalte 1 | Spalte 2 | Spalte 3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |") }
        ToolIcon(Icons.Outlined.HorizontalRule, tr("Trennlinie")) { insert("---") }
        var more by remember { mutableStateOf(false) }
        var formula by remember { mutableStateOf(false) }
        if (formula) FormulaDialog(onDismiss = { formula = false }) { tex, display ->
            formula = false
            if (display || i == null) insert("$$$tex$$")
            else {
                val v = editing.value
                val piece = "$$tex$"
                editing.value = TextFieldValue(v.text.substring(0, v.selection.min) + piece + v.text.substring(v.selection.max), TextRange(v.selection.min + piece.length))
            }
        }
        Box {
            ToolLabel(tr("Einfügen ▾")) { more = true }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                if (onImage != null) DropdownMenuItem(text = { Text(tr("Bild …")) }, onClick = { more = false; onImage() })
                DropdownMenuItem(text = { Text(tr("Fußnote")) }, onClick = { more = false; footnote() })
                if (onCitation != null) DropdownMenuItem(text = { Text(tr("Literatur (Beleg) …")) }, onClick = { more = false; onCitation() })
                DropdownMenuItem(text = { Text(tr("Seitenumbruch")) }, onClick = { more = false; insert(DocMarkup.PAGE_BREAK) })
                DropdownMenuItem(text = { Text(tr("Inhaltsverzeichnis")) }, onClick = { more = false; insert(DocMarkup.TOC) })
                DropdownMenuItem(text = { Text(tr("Formel (LaTeX) …")) }, onClick = { more = false; formula = true })
                DropdownMenuItem(text = { Text(tr("Querformat-Seiten")) }, onClick = { more = false; landscape() })
            }
        }
        ToolIcon(Icons.Outlined.Today, tr("Heutiges Datum")) {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            if (i != null) {
                val v = editing.value
                editing.value = TextFieldValue(v.text.substring(0, v.selection.min) + date + v.text.substring(v.selection.max), TextRange(v.selection.min + date.length))
            } else insert(date)
        }
        VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
        ToolIcon(Icons.Outlined.AutoAwesome, tr("KI für diese Stelle"), enabled = text, onClick = onAi)
        ToolIcon(Icons.Outlined.AddComment, tr("Kommentieren (Strg+Alt+M)"), onClick = onComment)
        if (text) ToolIcon(Icons.Outlined.KeyboardHide, tr("Fertig (Esc)"), onClick = onDoneEditing)
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
    trLater("Überschrift 1") to "# ", trLater("Überschrift 2") to "## ", trLater("Überschrift 3") to "### ", trLater("Aufzählung") to "- ", trLater("Nummerierte Liste") to "1. ",
    trLater("Checkliste") to "- [ ] ", trLater("Zitat") to "> ", trLater("Hinweisbox") to "> [!NOTE]\n> ", trLater("Tabelle") to "| Spalte 1 | Spalte 2 |\n| --- | --- |\n|  |  |",
    trLater("Code") to "```\n\n```", trLater("Trennlinie") to "---", trLater("Datum") to "",
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
    /** The page in the corporate design of TU Dresden: logo and organizational unit at the top. */
    tud: Boolean = false,
    unit: String = "",
    /** What the model changed last, to accept or reject block by block. */
    review: AiReview.Result? = null,
    onReview: (ReviewAction) -> Unit = {},
    /** Änderungen nachverfolgen (AP-16): the review shows the suggestions since the base, not the model's last change. */
    tracking: Boolean = false,
) {
    val editable = doc != null && !writing && mode == TextMode.EDIT
    val notes = remember(blocks) { DocMarkup.noteNumbers(blocks.joinToString("\n\n")) }
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

    // Seitenansicht (AP-16): the page with the margins, header and footer of „Seite einrichten“.
    val setup = remember(blocks) { DocMarkup.pageSetup(blocks.joinToString("\n\n")) }
    val pad = if (narrow) 20.dp else (setup.marginMm * 2.8f).dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = if (narrow) 8.dp else 24.dp, end = if (narrow) 8.dp else 24.dp, top = 16.dp, bottom = 120.dp),
        ) {
            item(key = "top") {
                if (tud) TudPageHead(unit, pad, look.card)
                else Box(Modifier.fillMaxWidth().height(if (narrow) 20.dp else 48.dp).background(look.card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                if (setup.hasHeader) Row(Modifier.fillMaxWidth().background(look.card).padding(horizontal = pad).padding(bottom = 8.dp)) {
                    Text(setup.header, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    if (setup.date) Text(DocMarkup.today(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            itemsIndexed(blocks, key = { i, _ -> "b$i" }) { i, block ->
                Column(Modifier.fillMaxWidth().background(look.card).padding(horizontal = pad)) {
                    if (editable) review?.removed?.filter { it.at == i }?.forEach { r -> RemovedCard(r, onReview, tracking) }
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
                            onAsk = { selected, request -> inlineAi.start("b$i", doc, selected, "KI fragen", askInstruction(request)) },
                        )
                    } else {
                        BlockView(
                            doc, tabId, block.replace(EMPTY_BLOCK, ""), highlight = i in aiChanged || find.isNotBlank() && block.contains(find, true),
                            editable = editable,
                            onClick = { edit(i) },
                            onToggleTask = { n -> onBlocks(blocks.toMutableList().apply { set(i, DocBlocks.toggleTask(block, n)) }) },
                            onShowComments = onShowComments,
                            notes = notes,
                        )
                        if (editable && i in aiChanged) review?.changes?.firstOrNull { it.index == i }?.let { ch -> ChangeBar(ch, onReview, tracking, block.replace(EMPTY_BLOCK, "")) }
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
                    if (editable) review?.removed?.filter { it.at >= blocks.size }?.forEach { r -> RemovedCard(r, onReview, tracking) }
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
                            Text(tr("Hier weiterschreiben – „/“ für Formate"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    if (setup.footer.isNotBlank() || setup.pageNumbers) Text(
                        listOfNotNull(setup.footer.ifBlank { null }, if (setup.pageNumbers) tr("Seite 1 von …") else null).joinToString("   ·   "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            item(key = "footer") { HintText(footer, Modifier.padding(top = 10.dp, start = 4.dp)) }
        }
    }
}

/**
 * Head of a page in the TUD design, as in the official templates and the PDF export: the logo at the top left, the
 * organizational unit below it (last line bold).
 */
@Composable
private fun TudPageHead(unit: String, pad: androidx.compose.ui.unit.Dp, card: Color) {
    val logo = remember { runCatching { decodeImage(io.github.thepytales.llmhub.docs.TudBrand.logoBlue) }.getOrNull() }
    val lines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
    Column(
        Modifier.fillMaxWidth().background(card, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).padding(start = pad, end = pad, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        logo?.let { androidx.compose.foundation.Image(it, contentDescription = "TU Dresden", modifier = Modifier.height(44.dp)) }
        Spacer(Modifier.height(6.dp))
        lines.forEachIndexed { i, l ->
            Text(l, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (i == lines.lastIndex) FontWeight.SemiBold else FontWeight.Normal), color = Color(0xFF00008C))
        }
        if (lines.isEmpty()) HintText(tr("Einrichtung für den Kopf: Einstellungen › Dokumente & Darstellung"))
    }
}

/** Start of an empty document: write, use a template or let the model draft it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyDocStart(onTemplate: (String) -> Unit, onAi: () -> Unit, onWrite: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onWrite).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Einfach lostippen …"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        }
        Surface(onClick = onAi, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(tr("Mit KI entwerfen"), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Beschreibe, was entstehen soll – die KI schreibt den ersten Entwurf direkt hier hinein."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(tr("Oder mit einer Vorlage beginnen:"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DOC_STARTERS.forEach { (label, md) -> OutlinedButton(onClick = { onTemplate(md) }) { Text(label) } }
        }
    }
}

/** A block as it will look in the document; checklists can be ticked right here. */
@Composable
internal fun BlockView(
    doc: LiveDoc?,
    tabId: String?,
    block: String,
    highlight: Boolean,
    editable: Boolean,
    onClick: () -> Unit,
    onToggleTask: (Int) -> Unit,
    onShowComments: () -> Unit,
    notes: Map<String, Int> = emptyMap(),
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
                DocMarkup.image(block) != null -> DocImageView(DocMarkup.image(block)!!)
                DocMarkup.isPageBreak(block) -> MarkerLine(tr("Seitenumbruch"))
                // Inhaltsverzeichnis (AP-16): the headings of the document, always up to date.
                DocMarkup.isToc(block) -> Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(10.dp)) {
                    Text(tr("Inhaltsverzeichnis"), style = MaterialTheme.typography.titleSmall)
                    val entries = DocMarkup.tocEntries(doc?.markdown(tabId).orEmpty())
                    if (entries.isEmpty()) HintText(tr("Erscheint, sobald das Dokument Überschriften hat."))
                    entries.forEach { (level, text) -> Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = ((level - 2).coerceAtLeast(0) * 16).dp, top = 2.dp)) }
                }
                // Display formula (AP-16): readable in the editor, a real formula in Word.
                io.github.thepytales.llmhub.docs.LatexOmml.display(block) != null -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(io.github.thepytales.llmhub.docs.MathText.inText(block.trim()), style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                }
                // Textfeld from Word (AP-45, read only): its text in a frame.
                DocMarkup.isTextBox(block) -> Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)).padding(8.dp)) {
                    Text(tr("Textfeld aus Word (nur lesen)"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MarkdownText(block.trimStart().removePrefix(DocMarkup.TEXTBOX).trim())
                }
                DocMarkup.isComment(block) -> MarkerLine(markerLabel(block))
                NOTE_DEF_BLOCK.containsMatchIn(block) -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    block.lines().filter { it.isNotBlank() }.forEach { line ->
                        val m = NOTE_DEF_BLOCK.find(line)
                        Row {
                            Text(m?.let { notes[it.groupValues[1]]?.let(DocMarkup::superscript) ?: it.groupValues[1] }.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(18.dp))
                            Box(Modifier.weight(1f)) {
                                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodySmall) { MarkdownText(withNoteMarks(if (m != null) line.substring(m.range.last + 1).trim() else line.trim(), notes).ifBlank { " " }) }
                            }
                        }
                    }
                }
                else -> MarkdownText(withNoteMarks(block, notes))
            }
        }
        if (commented) {
            Icon(
                Icons.Outlined.AddComment, contentDescription = tr("Kommentiert"), tint = MaterialTheme.colorScheme.primary,
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
    onAsk: (selected: String, request: String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(editing.index) { runCatching { focus.requestFocus() } }
    val v = editing.value
    val link = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val hiding = remember(link, codeBg) { HideMarkdown(link, codeBg) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textOrigin by remember { mutableStateOf(Offset.Zero) }
    // „KI fragen“: on Windows it opens by itself once a selection rests, on phones from the button below.
    var askFor by remember { mutableStateOf<TextRange?>(null) }
    var askActive by remember { mutableStateOf(false) }
    var dismissedFor by remember { mutableStateOf<TextRange?>(null) }
    LaunchedEffect(v.selection) {
        if (v.selection.collapsed) { askFor = null; return@LaunchedEffect }
        if (askFor != null && askActive) return@LaunchedEffect
        if (DESKTOP && v.selection != dismissedFor) {
            delay(350)
            askFor = v.selection
            askActive = false
        }
    }
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
        val img = DocMarkup.image(v.text)
        if (img != null) {
            ImageBlockEditor(img) { editing.value = TextFieldValue(it.markdown()) }
        } else if (kind == DocBlocks.Kind.TABLE) {
            TableGrid(DocBlocks.tableRows(v.text)) { rows -> editing.value = TextFieldValue(DocBlocks.table(rows)) }
        } else Box {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = v,
                    onValueChange = { editing.value = smartEdit(editing.value, it) },
                    textStyle = style,
                    visualTransformation = hiding,
                    onTextLayout = { layout = it },
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
                        .onGloballyPositioned { textOrigin = it.positionInWindow() }
                        .focusRequester(focus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val listLike = kind in setOf(DocBlocks.Kind.BULLETS, DocBlocks.Kind.NUMBERS, DocBlocks.Kind.TASKS)
                        when {
                            e.key == Key.Escape -> { onDone(); true }
                            e.isCmd && e.isAltPressed && e.key == Key.M -> { onComment(); true }
                            e.isCmd && e.key == Key.J -> {
                                if (!v.selection.collapsed) { askFor = v.selection; askActive = true } else onAi()
                                true
                            }
                            e.isCmd && e.key == Key.B -> { editing.value = toggleMarks(v, "**"); true }
                            e.isCmd && e.key == Key.I -> { editing.value = toggleMarks(v, "*"); true }
                            e.isCmd && e.isShiftPressed && e.key == Key.DirectionUp -> { onMove(-1); true }
                            e.isCmd && e.isShiftPressed && e.key == Key.DirectionDown -> { onMove(1); true }
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
                        if (v.text.isEmpty()) Text(tr("Schreiben … („/“ für Formate)"), style = style.copy(color = MaterialTheme.colorScheme.outline))
                        inner()
                    },
                )
            }
            val sel = askFor
            val l = layout
            if (sel != null && !sel.collapsed && l != null && sel.max <= v.text.length) {
                val map = remember(v.text, hiding) { hiding.filter(androidx.compose.ui.text.AnnotatedString(v.text)).offsetMapping }
                val shownLength = l.layoutInput.text.length
                val a = map.originalToTransformed(sel.min).coerceIn(0, (shownLength - 1).coerceAtLeast(0))
                val b = map.originalToTransformed(sel.max).coerceIn(0, shownLength)
                if (shownLength > 0) {
                    val first = l.getBoundingBox(a)
                    val last = l.getBoundingBox((b - 1).coerceAtLeast(a))
                    val rect = IntRect(
                        (textOrigin.x + first.left).toInt(), (textOrigin.y + first.top).toInt(),
                        (textOrigin.x + last.right).toInt(), (textOrigin.y + last.bottom).toInt(),
                    )
                    val selected = v.text.substring(sel.min, sel.max)
                    AskAiPopup(
                        rect, active = askActive,
                        onSend = { request ->
                            onAsk(selected, request)
                            dismissedFor = sel
                            askFor = null
                        },
                        onDismiss = { dismissedFor = sel; askFor = null },
                    )
                }
            }
            DropdownMenu(expanded = slash, onDismissRequest = { editing.value = TextFieldValue("") }) {
                val q = v.text.removePrefix("/").lowercase()
                SLASH.filter { q.isEmpty() || tr(it.first).lowercase().contains(q) }.forEach { (label, md) ->
                    DropdownMenuItem(text = { Text(tr(label)) }, onClick = {
                        val t = if (label == "Datum") LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) else md
                        editing.value = TextFieldValue(t, TextRange(if (t.startsWith("```")) 4 else t.length))
                    })
                }
                DropdownMenuItem(
                    text = { Text(tr("KI schreiben lassen …")) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) },
                    onClick = { editing.value = TextFieldValue(""); onAi() },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!v.selection.collapsed && askFor == null) TextButton(onClick = { askFor = v.selection; askActive = true }) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("KI fragen"))
            }
            HintText(
                when (kind) {
                    DocBlocks.Kind.TABLE -> tr("Zellen direkt bearbeiten · Esc: fertig")
                    DocBlocks.Kind.BULLETS, DocBlocks.Kind.NUMBERS, DocBlocks.Kind.TASKS -> tr("Enter: neuer Punkt · Tab: einrücken")
                    else -> if (DESKTOP) tr("Enter: neuer Absatz · Text markieren: KI fragen") else tr("Enter: neuer Absatz · Text markieren: KI fragen")
                },
                Modifier.weight(1f),
            )
            IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ArrowUpward, contentDescription = tr("Nach oben"), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = { onMove(1) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ArrowDownward, contentDescription = tr("Nach unten"), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Block löschen"), modifier = Modifier.size(16.dp)) }
            TextButton(onClick = onDone) { Text(tr("Fertig")) }
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
            Text(tr("Gliederung"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen")) }
        }
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(10.dp)) {
            if (headings.isEmpty()) item { HintText(tr("Noch keine Überschriften. Überschriften gliedern das Dokument und erscheinen hier zum Springen.")) }
            headings.forEach { (i, level, text) ->
                item {
                    Text(
                        text.ifBlank { tr("(ohne Text)") },
                        style = if (level == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().clickable { onJump(i) }.padding(start = (level * 14).dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
            item {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Verknüpfungen"), style = MaterialTheme.typography.titleSmall)
                    HintText(tr("Mit [[Titel]] verlinkst du andere Dokumente."))
                    linksTo.forEach { d -> TextButton(onClick = { onOpenDoc(d.id) }) { Text("→ ${d.title}") } }
                    if (backlinks.isNotEmpty()) Text(tr("Verlinkt von"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    backlinks.forEach { d -> TextButton(onClick = { onOpenDoc(d.id) }) { Text("← ${d.title}") } }
                }
            }
        }
    }
}

/** Table cells as fields, with rows and columns to add or remove. */
@Composable
internal fun TableGrid(
    rows: List<List<String>>,
    paging: TablePaging? = null,
    onChange: (List<List<String>>) -> Unit,
) {
    val cols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = 4.dp
    val gapPx = with(density) { gap.toPx() }

    @Composable
    fun cells(r: Int, row: List<String>, readOnly: Boolean) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            (0 until cols).forEach { col ->
                OutlinedTextField(
                    row.getOrElse(col) { "" },
                    { v -> onChange(rows.mapIndexed { i, rr -> if (i == r) (0 until cols).map { k -> if (k == col) v else rr.getOrElse(k) { "" } } else rr }) },
                    modifier = Modifier.width(150.dp),
                    readOnly = readOnly,
                    textStyle = if (r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                )
            }
            if (r > 0 && !readOnly) IconButton(onClick = { onChange(rows.filterIndexed { i, _ -> i != r }) }) { Icon(Icons.Outlined.Close, contentDescription = tr("Zeile löschen"), modifier = Modifier.size(16.dp)) }
        }
    }

    /** Room before row [r] on a new page (Seitenansicht), with the header row repeated at its end when asked. */
    @Composable
    fun room(r: Int) {
        val p = paging ?: return
        val push = p.push(r)
        if (push <= 0f) return
        val header = if (p.repeats(r)) p.headerHeight() else 0f
        Spacer(Modifier.height(with(density) { (push - header).coerceAtLeast(0f).toDp() }))
        if (header > 0f) {
            // Wiederholte Kopfzeile: the header row again, not editable (it is edited in its own place).
            Box(Modifier.height(with(density) { header.toDp() }).semantics { contentDescription = tr("Wiederholte Kopfzeile") }) {
                cells(0, rows[0], readOnly = true)
            }
        }
    }

    Column(Modifier.horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { r, row ->
            Column {
                room(r)
                Box(
                    Modifier
                        .padding(bottom = gap)
                        .then(if (paging != null) Modifier.onGloballyPositioned { paging.measuredRow(r, rows.size, it.size.height + gapPx) } else Modifier),
                ) { cells(r, row, readOnly = false) }
            }
        }
        Column {
            room(rows.size)
            Row(if (paging != null) Modifier.onGloballyPositioned { paging.measuredBar(it.size.height.toFloat()) } else Modifier) {
                TextButton(onClick = { onChange(rows + listOf(List(cols) { "" })) }) { Text(tr("+ Zeile")) }
                TextButton(onClick = { onChange(rows.map { it + "" }) }) { Text(tr("+ Spalte")) }
                if (cols > 1) TextButton(onClick = { onChange(rows.map { it.dropLast(1) }) }) { Text(tr("− Spalte")) }
            }
        }
    }
}

/**
 * Tabellen über Seiten (AP-43): how the Seitenansicht breaks a table – room before a row ([push], px; the bar below the
 * rows is row `rows.size`), whether the header row repeats there, and where the grid reports its measured rows.
 */
internal interface TablePaging {
    fun push(row: Int): Float
    fun repeats(row: Int): Boolean
    fun headerHeight(): Float
    fun measuredRow(row: Int, rows: Int, height: Float)
    fun measuredBar(height: Float)
}

private val NOTE_DEF_BLOCK = Regex("^\\s{0,3}\\[\\^([^\\]\\s]+)]:\\s?")

/** Footnote marks [^1] shown as superscript numbers in the editor. */
private fun withNoteMarks(text: String, notes: Map<String, Int>): String = if (notes.isEmpty()) text
else DocMarkup.NOTE_REF.replace(text) { m -> notes[m.groupValues[1]]?.let(DocMarkup::superscript) ?: m.value }

/** What a comment line of the editor means, in words. */
private fun markerLabel(block: String): String {
    val t = block.trim()
    return when {
        DocMarkup.orientation(t) == true -> tr("Ab hier Querformat")
        DocMarkup.orientation(t) == false -> tr("Ab hier wieder Hochformat")
        t.contains("literatur:", true) -> tr("Literaturverzeichnis – wird über „Einfügen › Literatur“ automatisch gepflegt")
        t.contains("seitenzahlen", true) || t.contains("kopfzeile", true) || t.contains("datum:", true) ->
            tr("Seiteneinrichtung: ") + t.removePrefix("<!--").removeSuffix("-->").trim()
        else -> "Notiz: " + t.removePrefix("<!--").removeSuffix("-->").trim()
    }
}

/** A thin line with a label (page break, landscape pages, notes that are not printed). */
@Composable
internal fun MarkerLine(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(" $label ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Below a block the model changed: take it or go back to what stood there before. */
@Composable
private fun ChangeBar(change: AiReview.Change, onReview: (ReviewAction) -> Unit, tracking: Boolean = false, now: String = "") {
    var before by remember { mutableStateOf(false) }
    Column {
        // Änderungen nachverfolgen: what changed, word by word (red struck through: removed, green: inserted).
        if (tracking && change.before != null) WordDiff(DocBlocks.plainOf(change.before), DocBlocks.plainOf(now))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tracking) (if (change.before == null) tr("Eingefügt") else tr("Geändert")) else if (change.before == null) tr("Von der KI eingefügt") else tr("Von der KI geändert"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (!tracking && change.before != null) TextButton(onClick = { before = !before }) { Text(if (before) tr("Vorher ausblenden") else tr("Vorher")) }
            TextButton(onClick = { onReview(ReviewAction.Accept(change)) }) { Text(if (tracking) tr("Annehmen") else tr("Übernehmen")) }
            TextButton(onClick = { onReview(ReviewAction.Reject(change)) }) { Text(if (change.before == null) tr("Entfernen") else tr("Ablehnen")) }
        }
        if (before && change.before != null) Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp)) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) { MarkdownText(change.before) }
        }
    }
}

/** A block the model removed: restore it or let it go. */
@Composable
private fun RemovedCard(r: AiReview.Removed, onReview: (ReviewAction) -> Unit, tracking: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(if (tracking) tr("Gelöscht") else tr("Von der KI entfernt"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Text(DocBlocks.plainOf(r.text).take(300), style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { onReview(ReviewAction.Restore(r)) }) { Text(if (tracking) tr("Ablehnen") else tr("Wiederherstellen")) }
                TextButton(onClick = { onReview(ReviewAction.Drop(r)) }) { Text(if (tracking) tr("Annehmen") else tr("Entfernt lassen")) }
            }
        }
    }
}

/** Editing a picture block: caption and width instead of the Markdown line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageBlockEditor(img: DocMarkup.Image, onChange: (DocMarkup.Image) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DocImageView(img)
        OutlinedTextField(
            img.alt, { onChange(img.copy(alt = it.replace("\n", " "))) }, label = { Text(tr("Bildunterschrift (auch Alternativtext)")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(25, 50, 75, 100).forEach { w ->
                FilterChip(selected = (img.width ?: 100) == w, onClick = { onChange(img.copy(width = w)) }, label = { Text("$w %") })
            }
        }
    }
}


/** Word-level differences in one text: removed words red and struck through, inserted ones green (AP-16). */
@Composable
internal fun WordDiff(old: String, new: String) {
    val pieces = remember(old, new) { io.github.thepytales.llmhub.docs.TextDiff.words(old, new) }
    val text = androidx.compose.ui.text.buildAnnotatedString {
        pieces.forEach { p ->
            when (p.op) {
                io.github.thepytales.llmhub.docs.TextDiff.Op.SAME -> append(p.text)
                io.github.thepytales.llmhub.docs.TextDiff.Op.REMOVED -> withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFB3261E), textDecoration = TextDecoration.LineThrough)) { append(p.text) }
                io.github.thepytales.llmhub.docs.TextDiff.Op.ADDED -> withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF1B7F3B), background = Color(0x221B7F3B))) { append(p.text) }
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
}


/** „Formel (LaTeX)“ (AP-16): typed in LaTeX with a preview; in Word it becomes a real formula. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FormulaDialog(onDismiss: () -> Unit, onInsert: (String, Boolean) -> Unit) {
    var tex by remember { mutableStateOf("\\frac{a}{b}") }
    var display by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Formel einfügen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tex, { tex = it }, label = { Text("LaTeX") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("\\frac{a}{b}", "x^{2}", "x_{i}", "\\sqrt{x}", "\\sum_{i=1}^{n} x_i", "\\int_{a}^{b} f(x)\\,dx", "\\bar{x}", "\\alpha", "\\leq", "\\cdot").forEach { s ->
                        AssistChip(onClick = { tex = if (tex.isBlank()) s else "$tex $s" }, label = { Text(io.github.thepytales.llmhub.docs.MathText.inText("$$s$")) })
                    }
                }
                Text(tr("Vorschau"), style = MaterialTheme.typography.labelMedium)
                Text(io.github.thepytales.llmhub.docs.MathText.inText("$$tex$"), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.clickable { display = !display }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(display, { display = it })
                    Text(tr("Eigene Zeile (sonst im Text an der Cursorposition)"))
                }
                HintText(tr("In Word wird daraus eine echte Formel, die sich dort weiter bearbeiten lässt."))
            }
        },
        confirmButton = { TextButton(enabled = tex.isNotBlank(), onClick = { onInsert(tex.trim(), display) }) { Text(tr("Einfügen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
