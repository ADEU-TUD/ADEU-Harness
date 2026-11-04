package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.text.Align
import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.docs.text.ParaFormat
import io.github.thepytales.llmhub.docs.text.ParaKind
import io.github.thepytales.llmhub.docs.text.RichText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fließtext-Editor (AP-44): the text of a document as one running text like in Google Docs – a continuous cursor,
 * selecting across paragraphs, formatting with the toolbar, the menus and the usual shortcuts. Headings, paragraphs,
 * lists and quotes are one text field; tables, pictures, formulas, the Inhaltsverzeichnis and page breaks sit between
 * them as their own blocks. The document stays Markdown underneath (see RichText), so chat, exports and Word keep
 * working.
 *
 * Lora schreibt im Dokument (AP-47): „Hilf mir schreiben“ writes at the cursor (visible, highlighted, then
 * Übernehmen · Neu · Verwerfen); „Überarbeiten“ turns Lora's revision into suggestions that are accepted one by one.
 */

/** One piece of the document: running text or a block of its own (table, picture …). */
sealed class FlowSeg {
    abstract val id: Long

    class Text(override val id: Long, rich: RichText) : FlowSeg() {
        var rich by mutableStateOf(rich)
        var selection by mutableStateOf(TextRange(0))
        var composition by mutableStateOf<TextRange?>(null)
        /** Format for the next typed characters (Strg+B without a selection …); null: the one before the cursor. */
        var typing by mutableStateOf<CharFormat?>(null)
        var layout by mutableStateOf<TextLayoutResult?>(null)
        val focus = FocusRequester()
    }

    class Raw(override val id: Long, val markdown: String) : FlowSeg()
}

/** How the pages look: real pages with page breaks (Seitenansicht) or one running page (Ohne Seiten). */
enum class FlowView { PAGES, PAGELESS }

/** The state of the Fließtext-Editor for one tab. */
class FlowState(markdown: String) {
    private var nextId = 1L
    val segs = mutableStateListOf<FlowSeg>()
    var active by mutableStateOf<FlowSeg.Text?>(null)
    /** The Markdown last loaded or saved; a different one from outside (the model, another PC) is loaded anew. */
    var lastMarkdown: String = markdown
    /** Counts the changes; the editor saves a moment after the last one. */
    var version by mutableIntStateOf(0)
    private val undo = ArrayDeque<List<Pair<Long, Any>>>()
    private val redo = ArrayDeque<List<Pair<Long, Any>>>()
    private var lastPush = 0L

    /** Lora is writing at the cursor (AP-47): what she was asked, and the running request. */
    var writing by mutableStateOf<String?>(null)
    var writeJob: Job? = null
    var writeError by mutableStateOf<String?>(null)
    /** Her text is there and waits for Übernehmen · Neu · Verwerfen. */
    var written by mutableStateOf(false)
    /** The suggestion (AP-47) the review bar points at. */
    var reviewAt by mutableIntStateOf(0)
    var revising by mutableStateOf(false)
    var nextSuggestion = 1

    init {
        load(markdown)
    }

    fun load(markdown: String) {
        lastMarkdown = markdown
        segs.clear()
        var pending = mutableListOf<String>()
        fun flushText() {
            segs += FlowSeg.Text(nextId++, if (pending.isEmpty()) RichText.empty() else RichText.fromBlocks(pending))
            pending = mutableListOf()
        }
        val blocks = DocBlocks.split(markdown).map { it.replace(EMPTY_BLOCK, "") }.filter { it.isNotBlank() }
        for (b in blocks) {
            if (RichText.isFlowBlock(b)) pending += b
            else {
                if (pending.isNotEmpty() || segs.lastOrNull() !is FlowSeg.Text) flushText()
                segs += FlowSeg.Raw(nextId++, b)
            }
        }
        flushText()
        active = segs.filterIsInstance<FlowSeg.Text>().firstOrNull { it.id == active?.id } ?: segs.filterIsInstance<FlowSeg.Text>().first()
    }

    fun markdown(): String = DocBlocks.join(segs.flatMap { s ->
        when (s) {
            is FlowSeg.Text -> RichText.toBlocks(s.rich)
            is FlowSeg.Raw -> listOf(s.markdown)
        }
    })

    private fun snapshot(): List<Pair<Long, Any>> = segs.map { s -> s.id to (if (s is FlowSeg.Text) s.rich else (s as FlowSeg.Raw).markdown) }

    /** Remembers the state before a change for Rückgängig; typing is grouped (one step per pause). */
    fun checkpoint(group: Boolean = false) {
        val now = System.currentTimeMillis()
        if (group && now - lastPush < 900 && undo.isNotEmpty()) { lastPush = now; return }
        lastPush = if (group) now else 0L
        undo.addLast(snapshot())
        if (undo.size > 200) undo.removeFirst()
        redo.clear()
    }

    private fun restore(snap: List<Pair<Long, Any>>) {
        val texts = segs.filterIsInstance<FlowSeg.Text>().associateBy { it.id }
        segs.clear()
        snap.forEach { (id, v) ->
            if (v is RichText) segs += (texts[id] ?: FlowSeg.Text(id, v)).also { t ->
                t.rich = v
                t.selection = TextRange(t.selection.start.coerceAtMost(v.text.length), t.selection.end.coerceAtMost(v.text.length))
            } else segs += FlowSeg.Raw(id, v as String)
        }
        if (segs.none { it === active }) active = segs.filterIsInstance<FlowSeg.Text>().firstOrNull()
        changed()
    }

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(snapshot())
        lastPush = 0L
        restore(s)
    }

    fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(snapshot())
        lastPush = 0L
        restore(s)
    }

    fun changed() {
        version++
    }

    // ── Formatting of the active text ───────────────────────────────────────────────────────────────

    /** The character format at the cursor (or of the selection's start), for the toolbar. */
    fun formatHere(): CharFormat {
        val t = active ?: return CharFormat.PLAIN
        t.typing?.let { return it }
        val sel = t.selection
        val i = if (sel.collapsed) sel.start - 1 else sel.min
        return t.rich.chars.getOrNull(i)?.takeIf { t.rich.text.getOrNull(i) != '\n' } ?: CharFormat.PLAIN
    }

    fun paraHere(): ParaFormat {
        val t = active ?: return ParaFormat()
        return t.rich.paras[t.rich.paraAt(t.selection.min)]
    }

    /** Changes the character format of the selection, or of the next typed characters. */
    fun format(change: (CharFormat) -> CharFormat) {
        val t = active ?: return
        val sel = t.selection
        if (sel.collapsed) {
            t.typing = change(formatHere())
            return
        }
        checkpoint()
        t.rich = t.rich.format(sel.min until sel.max, change)
        changed()
    }

    /** Toggles a flag of the character format (bold, italic …) like Docs: on unless the whole selection has it. */
    fun toggle(get: (CharFormat) -> Boolean, set: (CharFormat, Boolean) -> CharFormat) {
        val t = active ?: return
        val sel = t.selection
        val on = if (sel.collapsed) !get(formatHere()) else !(sel.min until sel.max).all { i -> t.rich.text[i] == '\n' || get(t.rich.chars[i]) }
        format { set(it, on) }
    }

    fun paragraph(change: (ParaFormat) -> ParaFormat) {
        val t = active ?: return
        checkpoint()
        t.rich = t.rich.formatParas(t.selection.min, t.selection.max, change)
        changed()
    }

    /** Formatvorlage of the paragraphs at the cursor. */
    fun kind(k: ParaKind) = paragraph { p -> p.copy(kind = if (p.kind == k && k != ParaKind.NORMAL) ParaKind.NORMAL else k, soft = false) }

    fun indent(delta: Int) = paragraph { p -> if (p.kind.list) p.copy(indent = (p.indent + delta).coerceIn(0, 5)) else p }

    /** Removes all character formatting of the selection (Formatierung entfernen). */
    fun clearFormat() {
        format { CharFormat.PLAIN }
        paragraph { p -> p.copy(align = Align.LEFT) }
    }

    /** Inserts plain [text] (or [rich]) at the cursor of the active text, replacing the selection. */
    fun insertText(text: String) {
        val t = active ?: return
        checkpoint()
        val sel = t.selection
        t.rich = t.rich.replace(sel.min until sel.max, text, formatHere().stored)
        t.selection = TextRange(sel.min + text.length)
        changed()
    }

    /** Puts a block of its own (table, picture, page break …) at the cursor: the running text splits around it. */
    fun insertBlock(markdown: String) {
        val t = active ?: segs.filterIsInstance<FlowSeg.Text>().lastOrNull() ?: return
        checkpoint()
        val at = t.selection.max.coerceIn(0, t.rich.text.length)
        val before = t.rich.cut(at, t.rich.text.length).let { r -> if (r.text.endsWith("\n")) r.cut(r.text.length - 1, r.text.length) else r }
        val after = t.rich.cut(0, at).let { r -> if (r.text.startsWith("\n")) r.cut(0, 1) else r }
        val i = segs.indexOf(t)
        t.rich = before
        val raw = FlowSeg.Raw(nextId++, markdown.trim())
        val rest = FlowSeg.Text(nextId++, after)
        segs.add(i + 1, raw)
        segs.add(i + 2, rest)
        active = rest
        rest.selection = TextRange(0)
        changed()
    }

    fun setRaw(seg: FlowSeg.Raw, markdown: String) {
        val i = segs.indexOf(seg)
        if (i < 0) return
        checkpoint()
        if (markdown.isBlank()) {
            segs.removeAt(i)
            // Two texts that meet are joined.
            val a = segs.getOrNull(i - 1) as? FlowSeg.Text
            val b = segs.getOrNull(i) as? FlowSeg.Text
            if (a != null && b != null) {
                val joined = if (a.rich.text.isEmpty()) b.rich else if (b.rich.text.isEmpty()) a.rich else a.rich.insert(a.rich.text.length, RichText("\n", listOf(CharFormat.PLAIN), listOf(ParaFormat(), ParaFormat()))).let { it.insert(it.text.length, b.rich) }
                a.rich = joined
                segs.remove(b)
                if (active === b) active = a
            }
        } else segs[i] = FlowSeg.Raw(seg.id, markdown)
        changed()
    }

    // ── Lora schreibt im Dokument (AP-47) ───────────────────────────────────────────────────────────

    /** All open suggestions of the document, as (text, number). */
    fun suggestions(): List<Pair<FlowSeg.Text, Int>> = segs.filterIsInstance<FlowSeg.Text>().flatMap { t -> t.rich.suggestions.map { t to it } }

    fun settle(t: FlowSeg.Text, n: Int, accept: Boolean) {
        checkpoint()
        t.rich = t.rich.settle(n, accept)
        t.selection = TextRange(t.selection.start.coerceAtMost(t.rich.text.length))
        changed()
    }

    fun settleAll(accept: Boolean) {
        checkpoint()
        segs.filterIsInstance<FlowSeg.Text>().forEach { t -> t.rich.suggestions.forEach { n -> t.rich = t.rich.settle(n, accept) } }
        changed()
    }

    /** The text Lora wrote is taken ([keep]) or removed. */
    fun finishWriting(keep: Boolean) {
        segs.filterIsInstance<FlowSeg.Text>().forEach { t ->
            if (t.rich.chars.any { it.ai }) {
                t.rich = if (keep) t.rich.keepAi() else t.rich.removeWhere { i -> t.rich.chars[i].ai }
                t.selection = TextRange(t.selection.start.coerceAtMost(t.rich.text.length))
            }
        }
        written = false
        writing = null
        changed()
    }
}

/** What the editor needs from the document screen (menus that open dialogs there). */
class FlowHost(
    val onComment: (String) -> Unit = {},
    val onImage: () -> Unit = {},
    val onCitation: () -> Unit = {},
    val onFind: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onPageSetup: () -> Unit = {},
    val onHistory: () -> Unit = {},
    val onOutline: () -> Unit = {},
    val onSource: () -> Unit = {},
    val onRead: () -> Unit = {},
    val onSpell: () -> Unit = {},
    val onTrack: (() -> Unit)? = null,
    val onAskAi: () -> Unit = {},
) {
    /** Set by the editor while it is shown: puts a block (picture …) or text (a citation) at the cursor. */
    var insertBlock: ((String) -> Unit)? = null
    var insertText: ((String) -> Unit)? = null
    /** The editor's Markdown right now (including what was not saved yet). */
    var markdown: (() -> String)? = null
}

/** Text colours and highlight colours of the palettes (Docs-like; the first is „automatisch“). */
private val TEXT_COLORS = listOf(null, "000000", "595959", "C00000", "E36C09", "00B050", "0070C0", "00205F", "7030A0")
private val HIGHLIGHTS = listOf(null, "FFF59D", "C8E6C9", "BBDEFB", "F8BBD0", "FFE0B2", "E1BEE7", "D9D9D9")
private val FONTS = listOf(null to "Standard", "Georgia" to "Georgia", "Times New Roman" to "Times New Roman", "Arial" to "Arial", "Calibri" to "Calibri", "Courier New" to "Courier New")
private val SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36)

private fun hex(c: String) = Color(("FF$c").toLong(16))

/** Font of a run: serif names → serif, typewriter names → monospace. */
private fun familyOf(name: String?): FontFamily? = when {
    name == null -> null
    Regex("(?i)courier|consolas|mono").containsMatchIn(name) -> FontFamily.Monospace
    Regex("(?i)georgia|times|cambria|garamond|serif|palatino").containsMatchIn(name) -> FontFamily.Serif
    else -> FontFamily.SansSerif
}

/** The look of the running text: paragraph styles, character formats, Lora's marks. */
internal fun flowAnnotated(r: RichText, base: TextStyle, accent: Color, muted: Color, codeBg: Color, density: Float): AnnotatedString = buildAnnotatedString {
    append(r.text)
    val listIndent = 28f
    r.paras.forEachIndexed { i, p ->
        val range = r.paraRange(i)
        val end = if (i + 1 < r.paras.size) range.last + 2 else r.text.length
        if (end <= range.first && r.text.isNotEmpty() && range.first >= r.text.length) return@forEachIndexed
        val indent = when {
            p.kind.list -> (listIndent * (p.indent + 1)).sp
            p.kind == ParaKind.QUOTE -> 18.sp
            else -> 0.sp
        }
        addStyle(
            ParagraphStyle(
                textAlign = when (p.align) { Align.CENTER -> TextAlign.Center; Align.RIGHT -> TextAlign.End; Align.JUSTIFY -> TextAlign.Justify; Align.LEFT -> TextAlign.Start },
                textIndent = if (indent.value > 0f) TextIndent(indent, indent) else null,
                lineHeight = if (p.kind.heading) 1.3.em else 1.5.em,
            ),
            range.first, end.coerceAtLeast(range.first),
        )
        val headStyle = when (p.kind) {
            ParaKind.H1 -> SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
            ParaKind.H2 -> SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold)
            ParaKind.H3 -> SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            ParaKind.H4 -> SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            ParaKind.QUOTE -> SpanStyle(fontStyle = FontStyle.Italic, color = muted)
            ParaKind.CHECK -> if (p.checked) SpanStyle(color = muted, textDecoration = TextDecoration.LineThrough) else null
            else -> null
        }
        if (headStyle != null && range.last >= range.first) addStyle(headStyle, range.first, range.last + 1)
    }
    var i = 0
    while (i < r.text.length) {
        val f = r.chars[i]
        var j = i + 1
        while (j < r.text.length && r.chars[j] == f) j++
        if (f != CharFormat.PLAIN) {
            val decorations = listOfNotNull(
                TextDecoration.Underline.takeIf { f.underline || f.link != null || f.ins > 0 },
                TextDecoration.LineThrough.takeIf { f.strike || f.del > 0 },
            )
            addStyle(
                SpanStyle(
                    fontWeight = if (f.bold) FontWeight.Bold else null,
                    fontStyle = if (f.italic) FontStyle.Italic else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                    fontFamily = if (f.code) FontFamily.Monospace else familyOf(f.font),
                    fontSize = when {
                        f.sup || f.sub -> 0.75.em
                        f.size != null -> f.size.sp
                        else -> androidx.compose.ui.unit.TextUnit.Unspecified
                    },
                    baselineShift = if (f.sup) BaselineShift.Superscript else if (f.sub) BaselineShift.Subscript else null,
                    color = when {
                        f.ins > 0 -> Color(0xFF1B7F3B)
                        f.del > 0 -> Color(0xFFC62828)
                        f.link != null -> Color(0xFF1D4ED8)
                        f.color != null -> hex(f.color)
                        else -> Color.Unspecified
                    },
                    background = when {
                        f.ai -> accent.copy(alpha = 0.18f)
                        f.ins > 0 -> Color(0x221B7F3B)
                        f.highlight != null -> hex(f.highlight)
                        f.code -> codeBg
                        else -> Color.Unspecified
                    },
                ),
                i, j,
            )
        }
        i = j
    }
}

/** The list marks of the paragraphs (•, 1., ☐) – drawn in the gutter, not part of the text. */
private fun listMarks(r: RichText): List<Pair<Int, String>> {
    val out = mutableListOf<Pair<Int, String>>()
    val counters = IntArray(6)
    var prevList = false
    r.paras.forEachIndexed { i, p ->
        if (!p.kind.list) { counters.fill(0); prevList = false; return@forEachIndexed }
        if (p.soft) return@forEachIndexed
        if (!prevList) counters.fill(0)
        prevList = true
        val lvl = p.indent.coerceIn(0, 5)
        for (k in lvl + 1 until 6) counters[k] = 0
        out += i to when (p.kind) {
            ParaKind.NUMBERED -> { counters[lvl]++; if (lvl % 3 == 1) ('a' + (counters[lvl] - 1) % 26) + "." else "${counters[lvl]}." }
            ParaKind.CHECK -> "check"
            else -> when (lvl % 3) { 0 -> "•"; 1 -> "◦"; else -> "▪" }
        }
    }
    return out
}

/**
 * The editor: menus, toolbar and the pages with the running text. [markdown] is the tab's Markdown; changes come back
 * through [onChange] a moment after typing stops (and when the editor closes).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowEditor(
    c: AppContainer,
    doc: LiveDoc,
    tabId: String?,
    markdown: String,
    narrow: Boolean,
    host: FlowHost,
    onChange: (String) -> Unit,
) {
    val state = remember(doc.id, tabId) { FlowState(markdown) }
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(FlowView.PAGES) }
    var zoom by remember { mutableStateOf(1f) }
    // Changes from outside (the model, Lora's chat, another PC) are loaded; the editor's own saves are not.
    LaunchedEffect(markdown) {
        if (markdown != state.lastMarkdown && state.writing == null && state.suggestions().isEmpty()) state.load(markdown)
    }
    val onChangeLatest by rememberUpdatedState(onChange)
    fun save() {
        // While Lora writes or suggestions are open, only the stored form (without them) counts.
        val md = state.markdown()
        if (md != state.lastMarkdown) {
            state.lastMarkdown = md
            onChangeLatest(md)
        }
    }
    LaunchedEffect(state.version) {
        if (state.version == 0) return@LaunchedEffect
        delay(700)
        if (state.writing == null) save()
    }
    DisposableEffect(state) { onDispose { save() } }

    // Lora im Dokument (AP-38 Stufe c, AP-47): her actions work on this editor.
    DisposableEffect(state) {
        io.github.thepytales.llmhub.agent.LoraFocus.docId = doc.id
        io.github.thepytales.llmhub.agent.ActionHosts.doc = object : io.github.thepytales.llmhub.agent.DocActions {
            override fun write(prompt: String) = startWriting(c, doc, state, prompt)
            override fun revise(section: String, instruction: String): Boolean = startRevising(c, doc, state, section, instruction)
            override fun insertTable(header: List<String>, rows: Int): String? {
                val before = state.markdown()
                state.insertBlock(DocBlocks.table(listOf(header) + List(rows) { List(header.size) { "" } }))
                return before
            }
            override fun heading(text: String, level: Int): String? {
                val before = state.markdown()
                val needle = AppMapNorm(text)
                for (t in state.segs.filterIsInstance<FlowSeg.Text>()) {
                    val i = t.rich.paras.indices.firstOrNull { AppMapNorm(t.rich.paraText(it)).startsWith(needle) } ?: continue
                    state.checkpoint()
                    t.rich = t.rich.withParas(t.rich.paras.mapIndexed { k, p -> if (k == i) p.copy(kind = ParaKind.heading(level), soft = false) else p })
                    state.changed()
                    return before
                }
                return null
            }
            override fun restore(markdown: String) {
                state.load(markdown)
                state.changed()
            }
        }
        onDispose {
            if (io.github.thepytales.llmhub.agent.LoraFocus.docId == doc.id) io.github.thepytales.llmhub.agent.LoraFocus.docId = null
            io.github.thepytales.llmhub.agent.ActionHosts.doc = null
            state.writeJob?.cancel()
        }
    }

    DisposableEffect(state, host) {
        host.insertBlock = { md -> state.insertBlock(md) }
        host.insertText = { t -> state.insertText(t) }
        host.markdown = { state.markdown() }
        onDispose {
            host.insertBlock = null
            host.insertText = null
            host.markdown = null
        }
    }
    var askWrite by remember { mutableStateOf(false) }
    var askRevise by remember { mutableStateOf(false) }
    var linkFor by remember { mutableStateOf<IntRange?>(null) }

    Column(Modifier.fillMaxSize()) {
        FlowMenus(state, view, onView = { view = it }, zoom = zoom, onZoom = { zoom = it }, host = host, onWrite = { askWrite = true }, onRevise = { askRevise = true }, onLink = {
            state.active?.selection?.takeIf { !it.collapsed }?.let { linkFor = it.min until it.max }
        })
        FlowToolbar(state, host, onWrite = { askWrite = true }, onLink = {
            state.active?.selection?.let { linkFor = it.min until it.max }
        })
        HorizontalDivider()
        // Lora writes (AP-47): her request, then Übernehmen · Neu · Verwerfen.
        LoraWriteBar(c, doc, state)
        ReviewBar(state)
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            FlowPages(c, doc, tabId, state, view, zoom, narrow, host, onWrite = { askWrite = true })
        }
    }

    if (askWrite) {
        PromptDialog(
            title = tr("Hilf mir schreiben"),
            hint = tr("Was soll Lora an der Cursorstelle schreiben? Zum Beispiel: Schreib eine Einleitung zu …"),
            onDismiss = { askWrite = false },
        ) { prompt -> askWrite = false; startWriting(c, doc, state, prompt) }
    }
    if (askRevise) {
        PromptDialog(
            title = tr("Mit Lora überarbeiten"),
            hint = tr("Wie soll Lora die Markierung überarbeiten? Die Änderungen erscheinen als Vorschläge."),
            initial = tr("Sprachlich verbessern, klarer und kürzer"),
            onDismiss = { askRevise = false },
        ) { instruction -> askRevise = false; if (!startRevising(c, doc, state, "markierung", instruction)) Notices.error(tr("Bitte zuerst Text markieren.")) }
    }
    linkFor?.let { range ->
        PromptDialog(title = tr("Link einfügen"), hint = tr("Adresse, z. B. https://tu-dresden.de"), initial = "https://", onDismiss = { linkFor = null }) { url ->
            linkFor = null
            val t = state.active ?: return@PromptDialog
            if (range.isEmpty()) {
                state.checkpoint()
                val at = t.selection.min
                t.rich = t.rich.replace(at until at, url, CharFormat(link = url))
                t.selection = TextRange(at + url.length)
                state.changed()
            } else {
                state.checkpoint()
                t.rich = t.rich.format(range) { it.copy(link = url.trim().ifBlank { null }) }
                state.changed()
            }
        }
    }
}

private fun AppMapNorm(s: String) = io.github.thepytales.llmhub.data.AppMap.norm(s)

/** A small dialog with one text field. */
@Composable
private fun PromptDialog(title: String, hint: String, initial: String = "", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText(hint)
                OutlinedTextField(
                    text, { text = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text.trim()) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(text.trim()) }, enabled = text.isNotBlank()) { Text(tr("Los")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Lora writes at the cursor: the text appears step by step, highlighted, in the running text (AP-47). */
internal fun startWriting(c: AppContainer, doc: LiveDoc, state: FlowState, prompt: String) {
    val t = state.active ?: state.segs.filterIsInstance<FlowSeg.Text>().lastOrNull() ?: return
    state.writeJob?.cancel()
    if (state.written) state.finishWriting(keep = false)
    state.checkpoint()
    state.writing = prompt
    state.writeError = null
    val sel = t.selection
    // A selection is replaced by what Lora writes (it stays in Rückgängig).
    val base = if (sel.collapsed) t.rich else t.rich.cut(sel.min, sel.max)
    val at = sel.min.coerceIn(0, base.text.length)
    // A new paragraph when the cursor is inside text.
    val needsBreak = at > 0 && base.text[at - 1] != '\n'
    val context = base.text.substring(0, at).takeLast(1500)
    state.writeJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        fun show(md: String) {
            val piece = RichText.ofMarkdown(md) { it.copy(ai = true) }
            val glued = if (needsBreak && piece.text.isNotEmpty()) RichText("\n", listOf(CharFormat(ai = true)), listOf(ParaFormat(), ParaFormat())).let { it.insert(1, piece) } else piece
            t.rich = base.insert(at, glued)
            t.selection = TextRange(at + glued.text.length)
            state.changed()
        }
        runCatching {
            DocsAi.rewrite(
                c, doc, "",
                "Schreibe an der Cursorstelle des Dokuments: $prompt\n\nText direkt vor der Cursorstelle:\n<<<\n$context\n>>>\n" +
                    "Nutze Markdown-Überschriften (##) und Listen, wenn sie passen; keine Wiederholung des vorhandenen Textes.",
            ) { partial -> show(partial) }
        }.onSuccess { md ->
            show(md)
            state.written = true
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) return@launch
            state.writeError = e.message ?: e.javaClass.simpleName
            t.rich = base
            state.writing = null
            state.changed()
        }
    }
}

/**
 * Lora revises [section] (a heading's section or „markierung“) after [instruction] as suggestions (AP-47): every
 * changed stretch can be accepted or rejected on its own; her reasons come as a comment at the margin. False if
 * there is nothing to revise.
 */
internal fun startRevising(c: AppContainer, doc: LiveDoc, state: FlowState, section: String, instruction: String): Boolean {
    val (t, range) = if (section.equals("markierung", true) || section.isBlank()) {
        val a = state.active ?: return false
        if (a.selection.collapsed) return false
        // Whole paragraphs, so formatting and lists stay intact.
        val from = a.rich.starts[a.rich.paraAt(a.selection.min)]
        val toPara = a.rich.paraAt((a.selection.max - 1).coerceAtLeast(a.selection.min))
        a to (from until (a.rich.paraRange(toPara).last + 1))
    } else {
        val needle = io.github.thepytales.llmhub.data.AppMap.norm(section)
        var found: Pair<FlowSeg.Text, IntRange>? = null
        for (seg in state.segs.filterIsInstance<FlowSeg.Text>()) {
            val r = seg.rich
            val h = r.paras.indices.firstOrNull { r.paras[it].kind.heading && io.github.thepytales.llmhub.data.AppMap.norm(r.paraText(it)).contains(needle) } ?: continue
            val level = r.paras[h].kind.level
            val end = (h + 1 until r.paras.size).firstOrNull { r.paras[it].kind.heading && r.paras[it].kind.level <= level } ?: r.paras.size
            if (h + 1 >= end) continue
            found = seg to (r.starts[h + 1] until (r.paraRange(end - 1).last + 1))
            break
        }
        found ?: return false
    }
    if (range.isEmpty()) return false
    val old = RichText(t.rich.text.substring(range.first, range.last + 1), t.rich.chars.subList(range.first, range.last + 1).map { it.stored }, t.rich.paras.subList(t.rich.paraAt(range.first), t.rich.paraAt(range.last) + 1))
    val oldMd = DocBlocks.join(RichText.toBlocks(old))
    state.revising = true
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        runCatching {
            DocsAi.rewrite(
                c, doc, oldMd,
                "$instruction. Behalte den Sinn, die Absätze und die Formatierung bei. Schreibe danach in eine letzte eigene Zeile " +
                    "„BEGRÜNDUNG: …“ mit einem Satz, was du warum geändert hast.",
            )
        }.onSuccess { answer ->
            val reason = Regex("(?im)^\\s*BEGR(Ü|UE)NDUNG:\\s*(.+)$").find(answer)?.groupValues?.get(2)?.trim()
            val newMd = answer.replace(Regex("(?im)^\\s*BEGR(Ü|UE)NDUNG:.*$"), "").trim()
            val newPlain = RichText.ofMarkdown(newMd).text
            if (newPlain == old.text) {
                Notices.info(tr("Lora hat nichts zu ändern gefunden."))
            } else {
                state.checkpoint()
                val suggested = RichText.suggest(RichText(old.text, t.rich.chars.subList(range.first, range.last + 1), old.paras), newPlain, state.nextSuggestion)
                state.nextSuggestion = (suggested.suggestions.maxOrNull() ?: state.nextSuggestion) + 1
                t.rich = t.rich.cut(range.first, range.last + 1).insert(range.first, suggested)
                state.reviewAt = 0
                state.changed()
                // Lora's reason at the margin.
                if (reason != null) c.docs.update(doc.id) { d ->
                    d.copy(comments = d.comments + io.github.thepytales.llmhub.docs.DocComment(tabId = d.tabs.first().id.let { id -> d.tabs.firstOrNull { it.markdown.contains(old.text.take(30)) }?.id ?: id }, quote = old.text.take(200), text = reason, author = "Lora"))
                }
            }
        }.onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) Notices.error(tr("Lora konnte nicht überarbeiten: {0}", e.message.orEmpty())) }
        state.revising = false
    }
    return true
}

/** The bar while Lora writes or after she wrote (AP-47). */
@Composable
private fun LoraWriteBar(c: AppContainer, doc: LiveDoc, state: FlowState) {
    val prompt = state.writing ?: run {
        state.writeError?.let { e ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Lora konnte nicht schreiben: {0}", e), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { state.writeError = null }) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen")) }
            }
        }
        if (state.revising) LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    Surface(color = LocalChatLook.current.brand.copy(alpha = 0.1f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = LocalChatLook.current.brand, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(if (state.written) tr("Lora hat geschrieben") else tr("Lora schreibt …"), style = MaterialTheme.typography.labelLarge)
                Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (state.written) {
                FilledTonalButton(onClick = { state.finishWriting(keep = true) }) { Text(tr("Übernehmen")) }
                TextButton(onClick = { startWriting(c, doc, state, prompt) }) { Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Neu")) }
                TextButton(onClick = { state.finishWriting(keep = false) }) { Text(tr("Verwerfen")) }
            } else {
                TextButton(onClick = { state.writeJob?.cancel(); state.finishWriting(keep = false) }) { Text(tr("Abbrechen")) }
            }
        }
    }
    if (!state.written) LinearProgressIndicator(Modifier.fillMaxWidth())
}

/** The suggestions of Lora's revision, one at a time (AP-47). */
@Composable
private fun ReviewBar(state: FlowState) {
    val list = state.suggestions()
    if (list.isEmpty()) return
    val at = state.reviewAt.coerceIn(0, list.lastIndex)
    val (seg, n) = list[at]
    LaunchedEffect(seg.id, n) {
        // Puts the cursor on the suggestion, so the text field shows it.
        val i = seg.rich.chars.indexOfFirst { it.ins == n || it.del == n }
        if (i >= 0) seg.selection = TextRange(i)
    }
    val removed = seg.rich.text.indices.filter { seg.rich.chars[it].del == n }.joinToString("") { seg.rich.text[it].toString() }.trim()
    val added = seg.rich.text.indices.filter { seg.rich.chars[it].ins == n }.joinToString("") { seg.rich.text[it].toString() }.trim()
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Vorschlag {0} von {1}", at + 1, list.size), style = MaterialTheme.typography.labelLarge)
                Text(
                    buildAnnotatedString {
                        if (removed.isNotEmpty()) { pushStyle(SpanStyle(color = Color(0xFFC62828), textDecoration = TextDecoration.LineThrough)); append(removed.take(80)); pop(); append("  ") }
                        if (added.isNotEmpty()) { pushStyle(SpanStyle(color = Color(0xFF1B7F3B))); append(added.take(80)); pop() }
                    },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
            }
            TextButton(onClick = { state.reviewAt = (at - 1).coerceAtLeast(0) }, enabled = at > 0) { Text("‹") }
            TextButton(onClick = { state.reviewAt = (at + 1).coerceAtMost(list.lastIndex) }, enabled = at < list.lastIndex) { Text("›") }
            FilledTonalButton(onClick = { state.settle(seg, n, accept = true) }) { Text(tr("Annehmen")) }
            TextButton(onClick = { state.settle(seg, n, accept = false) }) { Text(tr("Ablehnen")) }
            var more by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { more = true }) { Text(tr("Alle …")) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(tr("Alle annehmen")) }, onClick = { more = false; state.settleAll(true) })
                    DropdownMenuItem(text = { Text(tr("Alle ablehnen")) }, onClick = { more = false; state.settleAll(false) })
                }
            }
        }
    }
}

/** The menus like in Docs: Datei · Bearbeiten · Ansicht · Einfügen · Format · Extras. */
@Composable
private fun FlowMenus(
    state: FlowState, view: FlowView, onView: (FlowView) -> Unit, zoom: Float, onZoom: (Float) -> Unit, host: FlowHost,
    onWrite: () -> Unit, onRevise: () -> Unit, onLink: () -> Unit,
) {
    var open by remember { mutableStateOf<String?>(null) }
    var tablePick by remember { mutableStateOf(false) }
    var special by remember { mutableStateOf(false) }
    @Composable
    fun menu(name: String, items: @Composable (close: () -> Unit) -> Unit) {
        Box {
            TextButton(onClick = { open = name }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr(name), style = MaterialTheme.typography.bodyMedium) }
            DropdownMenu(open == name, { open = null }) { items { open = null } }
        }
    }
    @Composable
    fun item(label: String, shortcut: String = "", enabled: Boolean = true, onClick: () -> Unit, close: () -> Unit) = DropdownMenuItem(
        text = { Row { Text(tr(label), Modifier.weight(1f, fill = false)); if (shortcut.isNotEmpty()) { Spacer(Modifier.width(24.dp)); Text(tr(shortcut), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } },
        enabled = enabled, onClick = { close(); onClick() },
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        menu("Datei") { close ->
            item("Exportieren …", onClick = host.onExport, close = close)
            item("Seite einrichten …", onClick = host.onPageSetup, close = close)
            item("Versionen …", onClick = host.onHistory, close = close)
        }
        menu("Bearbeiten") { close ->
            item("Rückgängig", "Strg+Z", state.canUndo, { state.undo() }, close)
            item("Wiederholen", "Strg+Y", state.canRedo, { state.redo() }, close)
            item("Alles auswählen", "Strg+A", onClick = { state.active?.let { it.selection = TextRange(0, it.rich.text.length) } }, close = close)
            item("Suchen und ersetzen", "Strg+H", onClick = host.onFind, close = close)
        }
        menu("Ansicht") { close ->
            item(if (view == FlowView.PAGES) "✓ Seitenansicht" else "Seitenansicht", onClick = { onView(FlowView.PAGES) }, close = close)
            item(if (view == FlowView.PAGELESS) "✓ Ohne Seiten" else "Ohne Seiten", onClick = { onView(FlowView.PAGELESS) }, close = close)
            HorizontalDivider()
            listOf(0.75f, 0.9f, 1f, 1.25f, 1.5f).forEach { z -> item((if (zoom == z) "✓ " else "") + "Zoom ${(z * 100).roundToInt()} %", onClick = { onZoom(z) }, close = close) }
            HorizontalDivider()
            item("Gliederung", onClick = host.onOutline, close = close)
            item("Markdown-Quelltext", onClick = host.onSource, close = close)
            item("Lesemodus", onClick = host.onRead, close = close)
        }
        menu("Einfügen") { close ->
            item("Tabelle …", onClick = { tablePick = true }, close = close)
            item("Bild …", onClick = host.onImage, close = close)
            item("Link …", onClick = onLink, close = close)
            item("Kommentar", onClick = { host.onComment(selectedText(state)) }, close = close)
            item("Zitat aus der Literatur …", onClick = host.onCitation, close = close)
            HorizontalDivider()
            item("Seitenumbruch", onClick = { state.insertBlock("\\newpage") }, close = close)
            item("Inhaltsverzeichnis", onClick = { state.insertBlock(DocMarkup.TOC) }, close = close)
            item("Fußnote", onClick = { insertFootnote(state) }, close = close)
            item("Formel", onClick = { state.insertBlock("$$\nE = mc^2\n$$") }, close = close)
            item("Trennlinie", onClick = { state.insertBlock("---") }, close = close)
            item("Datum", onClick = { state.insertText(DocMarkup.today()) }, close = close)
            item("Sonderzeichen …", onClick = { special = true }, close = close)
        }
        menu("Format") { close ->
            item("Fett", "Strg+B", onClick = { state.toggle({ it.bold }) { f, v -> f.copy(bold = v) } }, close = close)
            item("Kursiv", "Strg+I", onClick = { state.toggle({ it.italic }) { f, v -> f.copy(italic = v) } }, close = close)
            item("Unterstrichen", "Strg+U", onClick = { state.toggle({ it.underline }) { f, v -> f.copy(underline = v) } }, close = close)
            item("Durchgestrichen", onClick = { state.toggle({ it.strike }) { f, v -> f.copy(strike = v) } }, close = close)
            item("Hochgestellt", onClick = { state.toggle({ it.sup }) { f, v -> f.copy(sup = v, sub = false) } }, close = close)
            item("Tiefgestellt", onClick = { state.toggle({ it.sub }) { f, v -> f.copy(sub = v, sup = false) } }, close = close)
            item("Code", onClick = { state.toggle({ it.code }) { f, v -> f.copy(code = v) } }, close = close)
            HorizontalDivider()
            ParaKind.entries.forEach { k ->
                item(k.label, when (k.level) { 0 -> if (k == ParaKind.NORMAL) "Strg+Alt+0" else ""; else -> "Strg+Alt+${k.level}" }, onClick = { state.kind(k) }, close = close)
            }
            HorizontalDivider()
            item("Formatierung entfernen", "Strg+\\", onClick = { state.clearFormat() }, close = close)
        }
        menu("Extras") { close ->
            item("Hilf mir schreiben", "Strg+J", onClick = onWrite, close = close)
            item("Markierung mit Lora überarbeiten …", onClick = onRevise, close = close)
            item("Lora fragen", onClick = host.onAskAi, close = close)
            item("Rechtschreibung prüfen", onClick = host.onSpell, close = close)
            host.onTrack?.let { item("Änderungen nachverfolgen", onClick = it, close = close) }
            item("Wortzahl", onClick = {
                val words = state.segs.filterIsInstance<FlowSeg.Text>().sumOf { t -> Regex("\\S+").findAll(t.rich.text).count() }
                Notices.info(tr("{0} Wörter", words))
            }, close = close)
        }
    }
    if (tablePick) TablePickDialog(onDismiss = { tablePick = false }) { rows, cols ->
        tablePick = false
        state.insertBlock(DocBlocks.table(listOf(List(cols) { tr("Spalte {0}", it + 1) }) + List(rows - 1) { List(cols) { "" } }))
    }
    if (special) SpecialCharsDialog(onDismiss = { special = false }) { ch -> special = false; state.insertText(ch) }
}

private fun selectedText(state: FlowState): String = state.active?.let { t ->
    if (t.selection.collapsed) t.rich.paraText(t.rich.paraAt(t.selection.min)).take(200) else t.rich.text.substring(t.selection.min, t.selection.max)
}.orEmpty()

/** A footnote at the cursor: the mark [^n] in the text and its note at the end of the document. */
private fun insertFootnote(state: FlowState) {
    val n = Regex("\\[\\^(\\d+)]").findAll(state.markdown()).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
    state.insertText("[^$n]")
    state.segs += FlowSeg.Raw(System.nanoTime(), "[^$n]: " + tr("Text der Fußnote"))
    state.segs += FlowSeg.Text(System.nanoTime() + 1, RichText.empty())
    state.changed()
}

/** Raster to choose the size of a new table (Einfügen › Tabelle). */
@Composable
private fun TablePickDialog(onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    var hover by remember { mutableStateOf(3 to 3) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Tabelle einfügen")) },
        text = {
            Column {
                Text(tr("{0} × {1}", hover.first, hover.second), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                for (r in 1..8) Row {
                    for (col in 1..8) {
                        val on = r <= hover.first && col <= hover.second
                        Box(
                            Modifier.padding(2.dp).size(20.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { if (hover == r to col) onPick(r, col) else hover = r to col }
                                .semantics { contentDescription = "$r × $col" },
                        )
                    }
                }
                HintText(tr("Größe wählen, dann noch einmal klicken."))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(hover.first, hover.second) }) { Text(tr("Einfügen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecialCharsDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val chars = listOf("–", "—", "„", "“", "‚", "‘", "»", "«", "…", "§", "€", "°", "±", "×", "÷", "≈", "≠", "≤", "≥", "→", "←", "↔", "⇒", "•", "©", "®", "™", "½", "¼", "²", "³", "α", "β", "γ", "δ", "μ", "π", "σ", "Σ", "Ω", "∞", "√", "∑", "✓", "✗")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Sonderzeichen")) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                chars.forEach { ch ->
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)).clickable { onPick(ch) }, contentAlignment = Alignment.Center) {
                        Text(ch, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** The toolbar like in Docs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowToolbar(state: FlowState, host: FlowHost, onWrite: () -> Unit, onLink: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    val f = state.formatHere()
    val p = state.paraHere()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolIcon(Icons.AutoMirrored.Outlined.Undo, tr("Rückgängig"), enabled = state.canUndo) { state.undo() }
        ToolIcon(Icons.AutoMirrored.Outlined.Redo, tr("Wiederholen"), enabled = state.canRedo) { state.redo() }
        Sep()
        var styleMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { styleMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr(p.kind.label) + " ▾", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            DropdownMenu(styleMenu, { styleMenu = false }) {
                ParaKind.entries.forEach { k ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                tr(k.label),
                                style = when (k) {
                                    ParaKind.H1 -> MaterialTheme.typography.titleLarge
                                    ParaKind.H2 -> MaterialTheme.typography.titleMedium
                                    ParaKind.H3, ParaKind.H4 -> MaterialTheme.typography.titleSmall
                                    else -> MaterialTheme.typography.bodyMedium
                                },
                            )
                        },
                        onClick = { styleMenu = false; state.kind(k) },
                    )
                }
            }
        }
        var fontMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { fontMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text((f.font ?: tr("Standard")) + " ▾", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            DropdownMenu(fontMenu, { fontMenu = false }) {
                FONTS.forEach { (font, label) -> DropdownMenuItem(text = { Text(tr(label), fontFamily = familyOf(font)) }, onClick = { fontMenu = false; state.format { it.copy(font = font) } }) }
            }
        }
        var sizeMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { sizeMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("${f.size ?: 11} ▾", style = MaterialTheme.typography.bodySmall) }
            DropdownMenu(sizeMenu, { sizeMenu = false }) {
                DropdownMenuItem(text = { Text(tr("Standard")) }, onClick = { sizeMenu = false; state.format { it.copy(size = null) } })
                SIZES.forEach { s -> DropdownMenuItem(text = { Text("$s") }, onClick = { sizeMenu = false; state.format { it.copy(size = s) } }) }
            }
        }
        Sep()
        ToolIcon(Icons.Outlined.FormatBold, tr("Fett") + " (Strg+B)", active = f.bold) { state.toggle({ it.bold }) { x, on -> x.copy(bold = on) } }
        ToolIcon(Icons.Outlined.FormatItalic, tr("Kursiv") + " (Strg+I)", active = f.italic) { state.toggle({ it.italic }) { x, on -> x.copy(italic = on) } }
        ToolIcon(Icons.Outlined.FormatUnderlined, tr("Unterstrichen") + " (Strg+U)", active = f.underline) { state.toggle({ it.underline }) { x, on -> x.copy(underline = on) } }
        ToolIcon(Icons.Outlined.FormatStrikethrough, tr("Durchgestrichen"), active = f.strike) { state.toggle({ it.strike }) { x, on -> x.copy(strike = on) } }
        ColorButton(Icons.Outlined.FormatColorText, tr("Textfarbe"), TEXT_COLORS, f.color) { col -> state.format { it.copy(color = col) } }
        ColorButton(Icons.Outlined.FormatColorFill, tr("Hervorheben"), HIGHLIGHTS, f.highlight) { col -> state.format { it.copy(highlight = col) } }
        Sep()
        ToolIcon(Icons.Outlined.Link, tr("Link")) { onLink() }
        ToolIcon(Icons.Outlined.AddComment, tr("Kommentar")) { host.onComment(selectedText(state)) }
        ToolIcon(Icons.Outlined.Image, tr("Bild")) { host.onImage() }
        Sep()
        ToolIcon(Icons.AutoMirrored.Outlined.FormatAlignLeft, tr("Linksbündig"), active = p.align == Align.LEFT) { state.paragraph { it.copy(align = Align.LEFT) } }
        ToolIcon(Icons.Outlined.FormatAlignCenter, tr("Zentriert"), active = p.align == Align.CENTER) { state.paragraph { it.copy(align = Align.CENTER) } }
        ToolIcon(Icons.AutoMirrored.Outlined.FormatAlignRight, tr("Rechtsbündig"), active = p.align == Align.RIGHT) { state.paragraph { it.copy(align = Align.RIGHT) } }
        ToolIcon(Icons.Outlined.FormatAlignJustify, tr("Blocksatz"), active = p.align == Align.JUSTIFY) { state.paragraph { it.copy(align = Align.JUSTIFY) } }
        Sep()
        ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, tr("Aufzählung"), active = p.kind == ParaKind.BULLET) { state.kind(ParaKind.BULLET) }
        ToolIcon(Icons.Outlined.FormatListNumbered, tr("Nummerierte Liste"), active = p.kind == ParaKind.NUMBERED) { state.kind(ParaKind.NUMBERED) }
        ToolIcon(Icons.Outlined.CheckBox, tr("Checkliste"), active = p.kind == ParaKind.CHECK) { state.kind(ParaKind.CHECK) }
        ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, tr("Einzug verkleinern"), enabled = p.kind.list && p.indent > 0) { state.indent(-1) }
        ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, tr("Einzug vergrößern"), enabled = p.kind.list) { state.indent(1) }
        ToolIcon(Icons.Outlined.FormatClear, tr("Formatierung entfernen")) { state.clearFormat() }
        Sep()
        ToolIcon(Icons.Outlined.AutoAwesome, tr("Hilf mir schreiben") + " (Strg+J)") { onWrite() }
    }
}

@Composable
private fun Sep() = Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.outlineVariant))

@Composable
private fun ColorButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, colors: List<String?>, current: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box {
            ToolIcon(icon, label) { open = true }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(16.dp).height(3.dp).background(current?.let(::hex) ?: MaterialTheme.colorScheme.onSurface))
        }
        DropdownMenu(open, { open = false }) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEach { col ->
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(col?.let(::hex) ?: Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { open = false; onPick(col) }
                            .semantics { contentDescription = col?.let { "#$it" } ?: tr("Automatisch") },
                        contentAlignment = Alignment.Center,
                    ) { if (col == null) Icon(Icons.Outlined.Close, null, Modifier.size(14.dp)) }
                }
            }
        }
    }
}

/** The pages: the document's pieces one below the other on a page of A4 width (or one running page). */
@Composable
private fun FlowPages(c: AppContainer, doc: LiveDoc, tabId: String?, state: FlowState, view: FlowView, zoom: Float, narrow: Boolean, host: FlowHost, onWrite: () -> Unit) {
    val density = LocalDensity.current
    val setup = remember(state.version) { DocMarkup.pageSetup(state.markdown()) }
    val margin = if (narrow) 20.dp else (setup.marginMm * 3.78f).dp * zoom
    val pageWidth = if (view == FlowView.PAGES && !narrow) 794.dp * zoom else androidx.compose.ui.unit.Dp.Unspecified
    // A4 is 297 mm high; the text area between the margins makes one page.
    val pageHeightPx = with(density) { ((1123.dp * zoom) - margin * 2).toPx() }
    val look = LocalChatLook.current
    val outline = MaterialTheme.colorScheme.outline
    val pageLabel = rememberTextMeasurer()
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .padding(vertical = if (narrow) 8.dp else 24.dp)
                .then(if (pageWidth != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(pageWidth) else Modifier.fillMaxWidth().widthIn(max = 900.dp))
                .background(look.card, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = margin, vertical = if (narrow) 20.dp else margin)
                .heightIn(min = if (view == FlowView.PAGES) with(density) { pageHeightPx.toDp() } else 300.dp)
                // Seitenansicht: where one A4 page ends, a dashed line with the next page's number.
                .drawWithContent {
                    drawContent()
                    if (view == FlowView.PAGES && pageHeightPx > 100f) {
                        val labelStyle = TextStyle(fontSize = 10.sp, color = outline)
                        var y = pageHeightPx
                        var page = 2
                        while (y < size.height - 8f) {
                            drawLine(outline.copy(alpha = 0.6f), Offset(-margin.toPx() + 4f, y), Offset(size.width + margin.toPx() - 4f, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                            // Measured first and drawn as a layout: drawText(measurer, text, topLeft) only allows the
                            // width left of topLeft inside this box, and the label sits in the right page margin – with a
                            // margin over 60 dp (20 mm at 100 %) that width was negative and the app crashed.
                            val label = pageLabel.measure(tr("Seite {0}", page), labelStyle)
                            drawText(label, topLeft = Offset(size.width + margin.toPx() - label.size.width - 8.dp.toPx(), y + 2f))
                            y += pageHeightPx
                            page++
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (setup.hasHeader) Text(listOfNotNull(setup.header.takeIf { it.isNotBlank() }, DocMarkup.today().takeIf { setup.date }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = outline)
            state.segs.forEach { seg ->
                androidx.compose.runtime.key(seg.id) {
                    when (seg) {
                        is FlowSeg.Text -> FlowField(state, seg, zoom, onWrite)
                        is FlowSeg.Raw -> RawBlock(doc, tabId, state, seg)
                    }
                }
            }
            if (setup.footer.isNotBlank()) Text(setup.footer, style = MaterialTheme.typography.labelSmall, color = outline)
        }
    }
}

/** A block of its own between the running text: shown as it looks; tables edit in place, others via ✎. */
@Composable
private fun RawBlock(doc: LiveDoc, tabId: String?, state: FlowState, seg: FlowSeg.Raw) {
    var editing by remember { mutableStateOf(false) }
    var hover by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) {
            if (seg.markdown.trim().startsWith("|")) {
                TableGrid(DocBlocks.tableRows(seg.markdown)) { rows -> state.setRaw(seg, DocBlocks.table(rows)) }
            } else {
                BlockView(doc, tabId, seg.markdown, highlight = false, editable = true, onClick = { editing = true }, onToggleTask = {}, onShowComments = {})
            }
        }
        Column {
            IconButton(onClick = { editing = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Edit, contentDescription = tr("Bearbeiten"), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = { state.setRaw(seg, "") }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(16.dp)) }
        }
    }
    if (editing) TextPrompt(tr("Block bearbeiten (Markdown)"), seg.markdown, onDismiss = { editing = false }) { md -> editing = false; state.setRaw(seg, md) }
}

/** One stretch of running text: the text field with list marks in the gutter and the keys of a word processor. */
@Composable
private fun FlowField(state: FlowState, seg: FlowSeg.Text, zoom: Float, onWrite: () -> Unit) {
    val density = LocalDensity.current
    val look = LocalChatLook.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val base = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp * zoom)
    val rich = seg.rich
    val annotated = remember(rich, look.brand, zoom) { flowAnnotated(rich, base, look.brand, muted, codeBg, density.density) }
    val value = TextFieldValue(annotated, seg.selection, seg.composition)
    val measurer = rememberTextMeasurer()
    val marks = remember(rich) { listMarks(rich) }
    val onSurface = MaterialTheme.colorScheme.onSurface

    fun onValue(v: TextFieldValue) {
        val old = seg.rich
        if (v.text != old.text) {
            var r = old.edited(v.text, seg.typing)
            var sel = v.selection
            // Enter in an empty list item ends the list (like Docs).
            if (v.text.length == old.text.length + 1 && sel.collapsed && sel.start > 0 && v.text[sel.start - 1] == '\n') {
                val p = old.paraAt(sel.start - 1)
                if (old.paras[p].kind.list && old.paraText(p).isBlank()) {
                    r = old.withParas(old.paras.mapIndexed { i, q -> if (i == p) q.copy(kind = ParaKind.NORMAL, indent = 0) else q })
                    sel = TextRange(sel.start - 1)
                }
            }
            // Markdown marks while typing (AP-46): „# “, „## “, „- “, „1. “, „[] “, „> “ at the start of a paragraph.
            if (v.text.length == old.text.length + 1 && sel.collapsed && sel.start > 0 && v.text[sel.start - 1] == ' ') {
                val p = r.paraAt(sel.start - 1)
                val head = r.text.substring(r.starts[p], sel.start)
                val kind = when (head) {
                    "# " -> ParaKind.H1; "## " -> ParaKind.H2; "### " -> ParaKind.H3; "#### " -> ParaKind.H4
                    "- ", "* " -> ParaKind.BULLET; "1. " -> ParaKind.NUMBERED; "[] ", "[ ] " -> ParaKind.CHECK; "> " -> ParaKind.QUOTE
                    else -> null
                }
                if (kind != null && r.paras[p].kind == ParaKind.NORMAL) {
                    r = r.cut(r.starts[p], sel.start).let { x -> x.withParas(x.paras.mapIndexed { i, q -> if (i == p) q.copy(kind = kind) else q }) }
                    sel = TextRange(r.starts[p])
                }
            }
            state.checkpoint(group = true)
            seg.rich = r
            seg.selection = sel
            seg.composition = v.composition
            state.changed()
        } else {
            if (v.selection != seg.selection) seg.typing = null
            seg.selection = v.selection
            seg.composition = v.composition
        }
    }

    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = ::onValue,
            textStyle = base,
            cursorBrush = SolidColor(look.brand),
            onTextLayout = { seg.layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp)
                .focusRequester(seg.focus)
                .onFocusChanged { if (it.isFocused) state.active = seg }
                .semantics { contentDescription = tr("Dokumenttext") }
                .drawBehind {
                    // List marks (•, 1., a.) in the gutter before the indented text.
                    val layout = seg.layout ?: return@drawBehind
                    marks.forEach { (pi, mark) ->
                        if (mark == "check") return@forEach
                        val off = rich.starts[pi].coerceAtMost(layout.layoutInput.text.length)
                        val line = layout.getLineForOffset(off)
                        val indent = (28f * (rich.paras[pi].indent + 1)).sp.toPx() * zoom
                        val m = measurer.measure(mark, base.copy(fontWeight = FontWeight.Normal))
                        drawText(m, topLeft = Offset(indent - m.size.width - 6.dp.toPx(), layout.getLineBaseline(line) - m.firstBaseline))
                    }
                }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val cmd = e.isCtrlPressed || e.isMetaPressed
                    when {
                        cmd && !e.isShiftPressed && e.key == Key.Z -> { state.undo(); true }
                        cmd && (e.key == Key.Y || e.isShiftPressed && e.key == Key.Z) -> { state.redo(); true }
                        cmd && !e.isAltPressed && e.key == Key.B -> { state.toggle({ it.bold }) { f, on -> f.copy(bold = on) }; true }
                        cmd && !e.isAltPressed && e.key == Key.I -> { state.toggle({ it.italic }) { f, on -> f.copy(italic = on) }; true }
                        cmd && !e.isAltPressed && e.key == Key.U -> { state.toggle({ it.underline }) { f, on -> f.copy(underline = on) }; true }
                        cmd && e.key == Key.J -> { onWrite(); true }
                        cmd && e.key == Key.Backslash -> { state.clearFormat(); true }
                        cmd && e.isAltPressed && e.key == Key.Zero -> { state.kind(ParaKind.NORMAL); true }
                        cmd && e.isAltPressed && e.key == Key.One -> { state.kind(ParaKind.H1); true }
                        cmd && e.isAltPressed && e.key == Key.Two -> { state.kind(ParaKind.H2); true }
                        cmd && e.isAltPressed && e.key == Key.Three -> { state.kind(ParaKind.H3); true }
                        cmd && e.isAltPressed && e.key == Key.Four -> { state.kind(ParaKind.H4); true }
                        cmd && e.isShiftPressed && e.key == Key.Seven -> { state.kind(ParaKind.NUMBERED); true }
                        cmd && e.isShiftPressed && e.key == Key.Eight -> { state.kind(ParaKind.BULLET); true }
                        cmd && e.isShiftPressed && e.key == Key.Nine -> { state.kind(ParaKind.CHECK); true }
                        e.key == Key.Tab && state.paraHere().kind.list -> { state.indent(if (e.isShiftPressed) -1 else 1); true }
                        // Backspace at the start of a heading or list item first makes it normal text.
                        e.key == Key.Backspace && seg.selection.collapsed && seg.rich.starts.contains(seg.selection.start) && seg.rich.paras[seg.rich.paraAt(seg.selection.start)].let { it.kind != ParaKind.NORMAL } -> {
                            state.paragraph { p -> if (p.kind.list && p.indent > 0) p.copy(indent = p.indent - 1) else p.copy(kind = ParaKind.NORMAL, indent = 0) }
                            true
                        }
                        else -> false
                    }
                },
        )
        // Checkboxes of the checklist: real buttons in the gutter.
        val layout = seg.layout
        if (layout != null) marks.filter { it.second == "check" }.forEach { (pi, _) ->
            val off = rich.starts[pi].coerceAtMost(layout.layoutInput.text.length)
            val line = layout.getLineForOffset(off)
            val p = rich.paras[pi]
            val indentPx = with(density) { (28f * (p.indent + 1)).sp.toPx() } * zoom
            val sizePx = with(density) { 20.dp.toPx() }
            val top = layout.getLineTop(line) + (layout.getLineBottom(line) - layout.getLineTop(line) - sizePx) / 2
            Icon(
                if (p.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (p.checked) tr("Erledigt") else tr("Offen"),
                tint = if (p.checked) look.brand else onSurface,
                modifier = Modifier
                    .offset { IntOffset((indentPx - sizePx - with(density) { 6.dp.toPx() }).roundToInt(), top.roundToInt()) }
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        state.checkpoint()
                        seg.rich = seg.rich.withParas(seg.rich.paras.mapIndexed { i, q -> if (i == pi) q.copy(checked = !q.checked) else q })
                        state.changed()
                    },
            )
        }
        // An empty document: an invitation to write or to let Lora write.
        if (rich.text.isEmpty() && state.segs.size == 1) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Hier schreiben – oder"), style = base, color = muted)
                TextButton(onClick = onWrite) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Hilf mir schreiben")) }
            }
        }
    }
}

/** Check mark used by tests and the review (keeps the import of the icon set in one place). */
internal val FLOW_CHECK = Icons.Outlined.Check
