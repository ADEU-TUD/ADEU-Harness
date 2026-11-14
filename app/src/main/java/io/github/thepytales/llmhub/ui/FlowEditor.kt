package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed as ctrlHeld
import androidx.compose.ui.input.pointer.isMetaPressed as metaHeld
import androidx.compose.ui.input.pointer.isShiftPressed as shiftHeld
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.text.style.Hyphens
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
import io.github.thepytales.llmhub.docs.DocComment
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.text.Align
import io.github.thepytales.llmhub.docs.text.AutoCorrect
import io.github.thepytales.llmhub.docs.text.PageLayout
import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.docs.text.ParaFormat
import io.github.thepytales.llmhub.docs.text.ParaKind
import io.github.thepytales.llmhub.docs.text.RichHtml
import io.github.thepytales.llmhub.docs.text.RichText
import io.github.thepytales.llmhub.docs.text.StyleSet
import io.github.thepytales.llmhub.docs.text.StyleSheet
import io.github.thepytales.llmhub.docs.text.Styles
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
        /** Page breaks inside this text (Seitenansicht, AP-43) and room above it when it starts a page. */
        var breaks by mutableStateOf<List<PageBreakAt>>(emptyList())
        var push by mutableFloatStateOf(0f)
        /** Tabstopps (AP-44): room after each tab character (offset → px), from the last layout. */
        var tabExtra by mutableStateOf<Map<Int, Float>>(emptyMap())
        internal var tabFor: Any? = null
        internal var tabRounds = 0

        /**
         * Änderungen nachverfolgen (AP-44): this text when tracking began (null: not tracked) and the changes since –
         * inserted ranges of [rich], removed text of [base] at its place.
         */
        var base by mutableStateOf<RichText?>(null)
        private var cacheKey: Pair<RichText?, RichText>? = null
        private var cache: Pair<List<io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk>, List<Pair<Int, String>>> = emptyList<io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk>() to emptyList()

        /** Changes and removed text (offset → text), computed once per pair of texts. */
        private fun tracking(): Pair<List<io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk>, List<Pair<Int, String>>> {
            val b = base
            val r = rich
            val key = cacheKey
            if (key == null || key.first !== b || key.second !== r) {
                val h = b?.let { io.github.thepytales.llmhub.docs.text.TrackDiff.hunks(it.text, r.text) }.orEmpty()
                cache = h to if (b == null) emptyList() else h.filter { !it.del.isEmpty() }.map { it.at to b.text.substring(it.del.first, it.del.last + 1) }
                cacheKey = b to r
            }
            return cache
        }

        val hunks: List<io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk> get() = tracking().first
        val deleted: List<Pair<Int, String>> get() = tracking().second

        /** Where [offset] of the text is in the shown text (with the room of the page breaks and removed text). */
        fun shown(offset: Int): Int = shownOffset(breaks, deleted, offset)


        /** This text as Markdown blocks, made once per text (große Dokumente: only the piece typed in is made anew). */
        fun blocks(): List<String> {
            val r = rich
            if (blocksFor !== r) { blocksCache = RichText.toBlocks(r); blocksFor = r }
            return blocksCache
        }
        private var blocksFor: RichText? = null
        private var blocksCache: List<String> = emptyList()

        /** The measured lines of the last layout (see naturalLines), made once per layout. */
        internal var linesFor: Any? = null
        internal var linesCache: List<NaturalLine>? = null
    }

    class Raw(override val id: Long, val markdown: String) : FlowSeg() {
        /** Measured height without [push] (−1 until measured) and room above it when it starts a page. */
        var height by mutableFloatStateOf(-1f)
        var push by mutableFloatStateOf(0f)

        /** A table (Markdown rows `| … |`). */
        val isTable: Boolean = markdown.trim().startsWith("|")

        /**
         * Tabellen über Seiten (AP-43): measured height of each row with the room after it and of the bar below the
         * rows (−1 until measured); room before rows (row → px) and the rows above which the header row is repeated.
         */
        var rowHeights by mutableStateOf<List<Float>>(emptyList())
        var barHeight by mutableFloatStateOf(-1f)
        var rowPush by mutableStateOf<Map<Int, Float>>(emptyMap())
        var repeatAt by mutableStateOf<Set<Int>>(emptySet())

        /** Takes the measurements of [old] (the same block before an edit), so the page plan does not wait for them. */
        fun measuredLike(old: Raw): Raw = also {
            height = old.height
            if (isTable && old.isTable) { rowHeights = old.rowHeights; barHeight = old.barHeight }
        }
    }
}

/** How the pages look: real pages with page breaks (Seitenansicht) or one running page (Ohne Seiten). */
enum class FlowView { PAGES, PAGELESS }

/** The state of the Fließtext-Editor for one tab. */
class FlowState(markdown: String) {
    private var nextId = 1L
    val segs = mutableStateListOf<FlowSeg>()
    var active by mutableStateOf<FlowSeg.Text?>(null)
    /**
     * Markieren über Grenzen (AP-44): where a selection over several text fields (and the blocks between) began; its
     * other end is the cursor of [active]. Null: only the selection inside the active field (see FlowSpan).
     */
    var spanAnchor by mutableStateOf<Pair<FlowSeg.Text, Int>?>(null)
    /** Umschalt is held (a click then extends the selection into another field). */
    var shiftDown = false
    /** The field a Umschalt+Klick went into (its click becomes the end of the selection over several fields). */
    internal var shiftClick: FlowSeg.Text? = null
    /** The next focus change comes from the editor itself (it keeps a selection over several fields). */
    internal var focusByEditor = false
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

    /** Formatvorlagen of the document (AP-46), set by the editor from its setting lines. */
    var sheet by mutableStateOf(StyleSheet())
    /** Format übertragen (Pinsel, AP-46): the format the next selection takes. */
    var painter by mutableStateOf<Pair<CharFormat, ParaFormat>?>(null)
    /** Format kopieren (Strg+Umschalt+C, AP-125 c): the format Strg+Alt+V puts on a selection. */
    var formatClip: Pair<CharFormat, ParaFormat>? = null
    /** A text field that takes the focus as soon as it is shown (the text after a page break of Strg+Enter). */
    var wantFocus: FlowSeg.Text? = null
    /** A new id for a piece of the document. */
    fun newId(): Long = nextId++

    /** Bilder anfassen (AP-125 d): the picture that is selected (its id), with handles and its bar; null: none. */
    var picked by mutableStateOf<Long?>(null)

    // Suchen und Ersetzen (AP-44).
    var findOpen by mutableStateOf(false)
    var find by mutableStateOf("")
    var replaceWith by mutableStateOf("")
    var matchCase by mutableStateOf(false)
    var wholeWord by mutableStateOf(false)
    var findAt by mutableIntStateOf(0)

    /** Open comments of the tab: at the margin and marked in the text (AP-44). */
    var comments by mutableStateOf<List<DocComment>>(emptyList())
    /** Top of each piece of running text on the page (px from the top of the text area), for jumps and comments. */
    val segTop = mutableStateMapOf<Long, Float>()
    /** The text area of the page and the scrolled content, to turn places on the page into scroll positions. */
    var pageCoords: LayoutCoordinates? = null
    var contentCoords: LayoutCoordinates? = null
    /** Set by the editor: scrolls so that a place on the page ([segTop] coordinates) is visible. */
    var scrollTo: ((Float) -> Unit)? = null
    /** Pages of the Seitenansicht (from [io.github.thepytales.llmhub.docs.text.PageLayout]). */
    var pageCount by mutableIntStateOf(1)
    /** The page at the top third of the window (Statusleiste, APD-15) and the zoom at which a page fills the width. */
    var pageNow by mutableIntStateOf(1)
    var fitZoom by mutableFloatStateOf(1f)
    /** Abschnitte (AP-43/45): first page (1-based) of each section after the first, with its header and footer. */
    var sectionPages by mutableStateOf<List<Pair<Int, DocMarkup.Section>>>(emptyList())
    /** Text area height and room between two text areas of the Seitenansicht (px), as the page plan used them. */
    var pageText = 0f
    var pageBetween = 0f


    init {
        load(markdown)
    }

    fun load(markdown: String) {
        lastMarkdown = markdown
        segs.clear()
        var pending = mutableListOf<String>()
        fun flushText() {
            // Große Dokumente (AP-43 d): long running text in pieces at its chapters (see [pieces]), one text field each.
            val groups = pieces(pending)
            if (groups.isEmpty()) segs += FlowSeg.Text(nextId++, RichText.empty())
            for (g in groups) segs += FlowSeg.Text(nextId++, RichText.fromBlocks(g))
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

    /**
     * Running text of more than [PIECES_FROM] characters in pieces: a new piece starts at a heading of level 1 or 2 once
     * the piece has [PIECE_MIN] characters, and at any paragraph once it has [PIECE_MAX] (a long text without
     * chapters). Compose lays out a whole text field on every key; pieces keep that short. Cursor, deleting and
     * selecting go on across the pieces (see FlowSpan).
     */
    private fun pieces(blocks: List<String>): List<List<String>> {
        if (blocks.isEmpty()) return emptyList()
        if (blocks.sumOf { it.length } <= PIECES_FROM) return listOf(blocks)
        val out = mutableListOf<MutableList<String>>(mutableListOf())
        var size = 0
        for (b in blocks) {
            val chapter = b.startsWith("# ") || b.startsWith("## ")
            if (out.last().isNotEmpty() && (chapter && size >= PIECE_MIN || size >= PIECE_MAX)) { out += mutableListOf<String>(); size = 0 }
            out.last() += b
            size += b.length
        }
        return out
    }

    fun markdown(): String {
        // The same pieces as last time: the same Markdown (several parts of the editor ask for it per change).
        val key = segs.map { if (it is FlowSeg.Text) it.rich else (it as FlowSeg.Raw).markdown }
        val last = mdKey
        if (last != null && last.size == key.size && key.indices.all { last[it] === key[it] }) return mdCache
        val md = DocBlocks.join(segs.flatMap { s ->
            when (s) {
                is FlowSeg.Text -> s.blocks()
                is FlowSeg.Raw -> listOf(s.markdown)
            }
        })
        mdKey = key
        mdCache = md
        return md
    }
    private var mdKey: List<Any>? = null
    private var mdCache = ""

    /**
     * The blocks of their own (settings, tables …) as Markdown: page setup, sections and Formatvorlagen live there, so
     * they need not be read from the whole text on every key.
     */
    fun rawMarkdown(): String = segs.filterIsInstance<FlowSeg.Raw>().joinToString("\n\n") { it.markdown }

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

    /** Takes a changed Markdown of the whole document (outline moves, margins, Formatvorlagen) as one step. */
    fun applyMarkdown(md: String) {
        if (md == markdown()) return
        checkpoint()
        load(md)
        changed()
    }

    fun setMargins(mm: Int) {
        val md = markdown()
        applyMarkdown(DocMarkup.withPageSetup(md, DocMarkup.pageSetup(md).copy(margins = DocMarkup.marginsFor(mm))))
    }

    fun applySheet(s: StyleSheet) = applyMarkdown(Styles.write(markdown(), s))

    // ── Änderungen nachverfolgen (AP-44) ─────────────────────────────────────────────────────────

    /** The document when tracking began, in pieces like this one (null: not tracking). */
    private var trackState: FlowState? = null
    private var trackMarkdown: String? = null
    /** The tracked change the review bar points at. */
    var trackAt by mutableIntStateOf(0)

    /**
     * Pairs the running text with [base] (the tab's Markdown when tracking began, null: not tracking): each piece of
     * running text gets its text of then. False when tables, pictures or formulas differ – then the changes are shown
     * in the block view.
     */
    fun track(base: String?): Boolean {
        val texts = segs.filterIsInstance<FlowSeg.Text>()
        if (base == null) {
            trackState = null
            trackMarkdown = null
            texts.forEach { if (it.base != null) it.base = null }
            return true
        }
        if (base != trackMarkdown || trackState == null) {
            trackState = FlowState(base)
            trackMarkdown = base
        }
        val b = shape(trackState!!.segs)
        val c = shape(segs)
        val ok = sameShape(b, c)
        c.forEachIndexed { i, y -> if (y is FlowSeg.Text) { val r = if (ok) (b[i] as FlowSeg.Text).rich else null; if (y.base !== r) y.base = r } }
        return ok
    }

    /** The open tracked changes in text order. */
    fun tracked(): List<Pair<FlowSeg.Text, io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk>> =
        segs.filterIsInstance<FlowSeg.Text>().flatMap { t -> t.hunks.map { t to it } }

    /** Accepts change [h] of [t]: it moves into the text of then; returns that text as the new base Markdown. */
    fun acceptTracked(t: FlowSeg.Text, h: io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk): String? {
        val bs = trackState ?: return null
        val c = shape(segs)
        val bt = shape(bs.segs).getOrNull(c.indexOf(t)) as? FlowSeg.Text ?: return null
        var r = bt.rich
        if (!h.del.isEmpty()) r = r.cut(h.del.first, h.del.last + 1)
        if (!h.ins.isEmpty()) r = r.insert(h.del.first, t.rich.slice(h.ins.first, h.ins.last + 1))
        bt.rich = r
        return bs.markdown().also { trackMarkdown = it }
    }

    /** Rejects change [h] of [t]: the text of then comes back in its place. */
    fun rejectTracked(t: FlowSeg.Text, h: io.github.thepytales.llmhub.docs.text.TrackDiff.Hunk) {
        val base = t.base ?: return
        checkpoint()
        var r = t.rich
        if (!h.ins.isEmpty()) r = r.cut(h.ins.first, h.ins.last + 1)
        if (!h.del.isEmpty()) r = r.insert(h.ins.first, base.slice(h.del.first, h.del.last + 1))
        t.rich = r
        t.selection = TextRange(h.ins.first.coerceAtMost(r.text.length))
        changed()
    }

    /** All changes taken back: every piece of running text as it was. */
    fun rejectAllTracked() {
        checkpoint()
        segs.filterIsInstance<FlowSeg.Text>().forEach { t -> t.base?.let { b -> t.rich = b; t.selection = TextRange(0) } }
        changed()
    }

    companion object {
        /** Große Dokumente: running text is split into pieces from this length on (about 12 pages). */
        const val PIECES_FROM = 30_000
        const val PIECE_MIN = 3_000
        const val PIECE_MAX = 15_000

        /** The editor shown last (for the checks of the Seitenansicht in the tests). */
        internal var shown: java.lang.ref.WeakReference<FlowState>? = null

        /** Pieces that must be alike for tracking in the running text: running text and blocks other than settings. */
        private fun shape(list: List<FlowSeg>) = list.filter { it is FlowSeg.Text || it is FlowSeg.Raw && !DocMarkup.isComment(it.markdown) }

        private fun sameShape(b: List<FlowSeg>, c: List<FlowSeg>) = b.size == c.size && b.indices.all { i ->
            val x = b[i]
            val y = c[i]
            x is FlowSeg.Text && y is FlowSeg.Text || x is FlowSeg.Raw && y is FlowSeg.Raw && x.markdown.trim() == y.markdown.trim()
        }

        /** Whether the changes from [base] to [current] can be shown in the running text (blocks unchanged). */
        fun trackable(base: String, current: String): Boolean = sameShape(shape(FlowState(base).segs), shape(FlowState(current).segs))
    }

    // ── Suchen und Ersetzen (AP-44) ──────────────────────────────────────────────────────────────

    private fun pattern(): Regex? {
        if (find.isEmpty()) return null
        val esc = Regex.escape(find)
        val p = if (wholeWord) "(?<![\\p{L}\\p{N}])$esc(?![\\p{L}\\p{N}])" else esc
        return Regex(p, if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
    }

    fun hitsIn(r: RichText): List<IntRange> = if (!findOpen) emptyList() else pattern()?.findAll(r.text)?.map { it.range }?.filter { !it.isEmpty() }?.toList().orEmpty()

    fun hits(): List<Pair<FlowSeg.Text, IntRange>> = if (!findOpen) emptyList() else segs.filterIsInstance<FlowSeg.Text>().flatMap { t -> hitsIn(t.rich).map { t to it } }

    /** Replaces hit [i] (the replacement takes the format of the hit's first character). */
    fun replaceHit(i: Int) {
        val (t, r) = hits().getOrNull(i) ?: return
        checkpoint()
        t.rich = t.rich.replace(r, replaceWith, t.rich.chars[r.first].stored)
        t.selection = TextRange(r.first + replaceWith.length)
        changed()
    }

    fun replaceAll(): Int {
        val all = hits()
        if (all.isEmpty()) return 0
        checkpoint()
        all.groupBy { it.first }.forEach { (t, list) ->
            list.map { it.second }.sortedByDescending { it.first }.forEach { r -> t.rich = t.rich.replace(r, replaceWith, t.rich.chars[r.first].stored) }
            t.selection = TextRange(t.selection.start.coerceAtMost(t.rich.text.length))
        }
        changed()
        return all.size
    }

    /** Formats every hit (Suchen und Ersetzen „auch Formatierung“). */
    fun formatHits(change: (CharFormat) -> CharFormat): Int {
        val all = hits()
        if (all.isEmpty()) return 0
        checkpoint()
        all.forEach { (t, r) -> t.rich = t.rich.format(r, change) }
        changed()
        return all.size
    }

    // ── Places in the text ───────────────────────────────────────────────────────────────────────

    /** Where [quote] (of a comment) is in the running text. */
    fun findQuote(quote: String): Pair<FlowSeg.Text, IntRange>? {
        val q = quote.trim()
        if (q.isEmpty()) return null
        val head = q.take(80)
        for (t in segs.filterIsInstance<FlowSeg.Text>()) {
            val i = t.rich.text.indexOf(head)
            if (i >= 0) return t to (i until (i + q.length).coerceAtMost(t.rich.text.length))
        }
        return null
    }

    /** Top of the line of [quote] on the page (px), or null. */
    fun quoteY(quote: String): Float? {
        val (t, r) = findQuote(quote) ?: return null
        return lineTop(t, r.first)
    }

    private fun lineTop(t: FlowSeg.Text, offset: Int): Float? {
        val top = segTop[t.id] ?: return null
        val layout = t.layout ?: return top
        return top + layout.getLineTop(layout.getLineForOffset(t.shown(offset).coerceIn(0, layout.layoutInput.text.length)))
    }

    fun showQuote(quote: String) {
        val (t, r) = findQuote(quote) ?: return
        show(t, r)
    }

    /** Marks [r] in [t] and scrolls there. */
    fun show(t: FlowSeg.Text, r: IntRange) {
        active = t
        t.selection = TextRange(r.first, (r.last + 1).coerceAtLeast(r.first))
        runCatching { t.focus.requestFocus() }
        lineTop(t, r.first)?.let { scrollTo?.invoke(it) }
    }

    /** Jumps to heading number [k] of the document (Gliederung). */
    fun jumpToHeading(k: Int) {
        var n = 0
        for (t in segs.filterIsInstance<FlowSeg.Text>()) for (i in t.rich.paras.indices) if (t.rich.paras[i].kind.heading) {
            if (n == k) {
                val s0 = t.rich.starts[i]
                show(t, s0 until s0)
                return
            }
            n++
        }
    }

    /** Puts [piece] (from the clipboard) at the cursor, replacing the selection. */
    fun paste(t: FlowSeg.Text, piece: RichText) {
        checkpoint()
        val sel = t.selection
        t.rich = t.rich.cut(sel.min, sel.max).insert(sel.min, piece)
        t.selection = TextRange(sel.min + piece.text.length)
        changed()
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
        spanParts()?.let { parts ->
            checkpoint()
            parts.forEach { (seg, r) -> if (seg is FlowSeg.Text && r != null && !r.isEmpty()) seg.rich = seg.rich.format(r, change) }
            changed()
            return
        }
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
        spanParts()?.let { parts ->
            val on = !parts.all { (seg, r) -> seg !is FlowSeg.Text || r == null || r.all { i -> seg.rich.text[i] == '\n' || get(seg.rich.chars[i]) } }
            format { set(it, on) }
            return
        }
        val sel = t.selection
        val on = if (sel.collapsed) !get(formatHere()) else !(sel.min until sel.max).all { i -> t.rich.text[i] == '\n' || get(t.rich.chars[i]) }
        format { set(it, on) }
    }

    fun paragraph(change: (ParaFormat) -> ParaFormat) {
        val t = active ?: return
        spanParts()?.let { parts ->
            checkpoint()
            parts.forEach { (seg, r) -> if (seg is FlowSeg.Text && r != null) seg.rich = seg.rich.formatParas(r.first, (r.last + 1).coerceAtLeast(r.first), change) }
            changed()
            return
        }
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
        if (spanParts() != null) deleteSpan()
        val t = active ?: return
        checkpoint()
        val sel = t.selection
        t.rich = t.rich.replace(sel.min until sel.max, text, formatHere().stored)
        t.selection = TextRange(sel.min + text.length)
        changed()
    }

    /**
     * Diktat (AP-65): puts [dictated] text at the cursor (without one: at the end of the document) with a space before it
     * and a capital letter at the start of a sentence; „neuer Absatz“ became a new paragraph.
     */
    fun dictate(dictated: String) {
        if (dictated.isBlank()) return
        val t = active ?: segs.filterIsInstance<FlowSeg.Text>().lastOrNull()?.also { last ->
            active = last
            last.selection = TextRange(last.rich.text.length)
        } ?: return
        val at = t.selection.min.coerceIn(0, t.rich.text.length)
        insertText(io.github.thepytales.llmhub.audio.Dictation.join(t.rich.text.substring(maxOf(0, at - 60), at), dictated))
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

    /** The block [seg] becomes [markdown] (blank: it goes); [group]: typing in a table cell is one step per pause. */
    fun setRaw(seg: FlowSeg.Raw, markdown: String, group: Boolean = false) {
        val i = segs.indexOf(seg).takeIf { it >= 0 } ?: segs.indexOfFirst { it.id == seg.id }
        if (i < 0) return
        val now = segs[i] as? FlowSeg.Raw ?: return
        if (now.markdown == markdown) return
        checkpoint(group)
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
        } else segs[i] = FlowSeg.Raw(seg.id, markdown).measuredLike(now)
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
    val onShowComments: () -> Unit = {},
    /** The *Datei* menu of the document (APD-15): what the window around the editor offers (export, copy, delete …). */
    val fileMenu: (@Composable (close: () -> Unit) -> Unit)? = null,
    /** *Einfügen › Tab* (APD-15): the tabs row only shows with more than one tab. */
    val onAddTab: (() -> Unit)? = null,
    /** *Extras › Rechtschreibung prüfen (KI)*. */
    val onSpellAi: (() -> Unit)? = null,
) {
    /** Set by the editor while it is shown: puts a block (picture …) or text (a citation) at the cursor. */
    var insertBlock: ((String) -> Unit)? = null
    var insertText: ((String) -> Unit)? = null
    /** The editor's Markdown right now (including what was not saved yet). */
    var markdown: (() -> String)? = null
    /** The editor's own Rückgängig, Wiederholen and Suchen (APD-15): Strg+Z/Y/F reach them from anywhere in the window. */
    var undo: (() -> Unit)? = null
    var redo: (() -> Unit)? = null
    var find: (() -> Unit)? = null
    /** *Link …* for the selection (Rechtsklick-Menü, AP-125). */
    var link: (() -> Unit)? = null
    /** *Bild ersetzen …* (AP-125 d): where the next picture chosen with [onImage] goes instead of the cursor. */
    var imageInto: ((String) -> Unit)? = null
    /**
     * Menus and Formatleiste for the window's header (APD-15, like Google Docs: menus under the title, the Formatleiste
     * across the whole width). Set by the editor when it is shown with `chromeInHost`.
     */
    var menus by mutableStateOf<(@Composable () -> Unit)?>(null)
    var toolbar by mutableStateOf<(@Composable () -> Unit)?>(null)
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

/** Points of a Formatvorlage on the screen: A4 is 794 dp wide at 100 %, so 1 pt is 4/3 dp. */
internal fun ptSp(pt: Float, zoom: Float) = (pt * 4f / 3f * zoom).sp

/** Millimetres on the page as text units (indents). */
private fun mmSp(mm: Int, zoom: Float) = (mm * 3.7795f * zoom).sp

/** The Formatvorlagen of a document: its own setting lines, else the saved default, else those of its export style. */
internal fun sheetFor(markdown: String, docStyle: String?, defaults: String): StyleSheet {
    val own = Styles.read(markdown)
    if (own.explicit || own.changed.isNotEmpty()) return own
    if (defaults.isNotBlank()) Styles.read(defaults).let { if (it.explicit || it.changed.isNotEmpty()) return it }
    return StyleSheet(StyleSet.of(DocStyle.parse(docStyle)))
}

/**
 * The look of the running text: Formatvorlagen of the paragraphs ([sheet]), their line spacing and indents, character
 * formats, Lora's marks and [marks] (hits of the search, commented text) as backgrounds.
 */
internal fun flowAnnotated(
    r: RichText, base: TextStyle, accent: Color, muted: Color, codeBg: Color, density: Float,
    sheet: StyleSheet = StyleSheet(), zoom: Float = 1f, marks: List<Pair<IntRange, Color>> = emptyList(),
    inserted: List<IntRange> = emptyList(),
): AnnotatedString = buildAnnotatedString {
    append(r.text)
    val listIndent = 28f * zoom
    r.paras.forEachIndexed { i, p ->
        val range = r.paraRange(i)
        val end = if (i + 1 < r.paras.size) range.last + 2 else r.text.length
        if (end <= range.first && r.text.isNotEmpty() && range.first >= r.text.length) return@forEachIndexed
        val def = sheet.of(p.kind)
        // Literatur hangs by 1 cm unless the paragraph says otherwise.
        val left = if (p.kind == ParaKind.LITERATURE && p.left == 0 && p.first == 0) 10 else p.left
        val first = if (p.kind == ParaKind.LITERATURE && p.left == 0 && p.first == 0) -10 else p.first
        val listSp = when {
            p.kind.list -> listIndent * (p.indent + 1)
            p.kind == ParaKind.QUOTE -> 18f * zoom
            else -> 0f
        }
        val rest = listSp + mmSp(left, zoom).value
        val firstLine = (rest + mmSp(first, zoom).value).coerceAtLeast(0f)
        addStyle(
            ParagraphStyle(
                textAlign = when (p.align) { Align.CENTER -> TextAlign.Center; Align.RIGHT -> TextAlign.End; Align.JUSTIFY -> TextAlign.Justify; Align.LEFT -> TextAlign.Start },
                textIndent = if (rest > 0f || firstLine > 0f) TextIndent(firstLine.sp, rest.sp) else null,
                lineHeight = ((p.spacing ?: def.spacing) * 1.2f).em,
                // Every line as high as the next (also the first and last of a paragraph): the page plan stays the same
                // when a page break splits a paragraph.
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
                // Silbentrennung (AP-43) where it matters: in justified text.
                hyphens = if (p.align == Align.JUSTIFY) Hyphens.Auto else Hyphens.None,
            ),
            range.first, end.coerceAtLeast(range.first),
        )
        if (range.last >= range.first) {
            addStyle(
                SpanStyle(
                    fontSize = ptSp(def.size, zoom),
                    fontWeight = if (def.bold) FontWeight.Bold else null,
                    fontStyle = if (def.italic) FontStyle.Italic else null,
                    fontFamily = if (p.kind == ParaKind.CODE) FontFamily.Monospace else familyOf(def.font),
                    color = when {
                        p.kind == ParaKind.CHECK && p.checked -> muted
                        def.color != null -> hex(def.color)
                        else -> Color.Unspecified
                    },
                    background = if (p.kind == ParaKind.CODE) codeBg else Color.Unspecified,
                    textDecoration = if (p.kind == ParaKind.CHECK && p.checked) TextDecoration.LineThrough else null,
                ),
                range.first, range.last + 1,
            )
        }
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
                        f.size != null -> ptSp(f.size.toFloat(), zoom)
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
    // Änderungen nachverfolgen: inserted text green and underlined.
    for (range in inserted) {
        val a = range.first.coerceIn(0, r.text.length)
        val b = (range.last + 1).coerceIn(a, r.text.length)
        if (b > a) addStyle(SpanStyle(color = Color(0xFF1B7F3B), textDecoration = TextDecoration.Underline, background = Color(0x161B7F3B)), a, b)
    }
    for ((range, color) in marks) {
        val a = range.first.coerceIn(0, r.text.length)
        val b = (range.last + 1).coerceIn(a, r.text.length)
        if (b > a) addStyle(SpanStyle(background = color), a, b)
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
    /** Änderungen nachverfolgen (AP-44): the tab's Markdown when tracking began; accepted changes move into it. */
    trackBase: String? = null,
    onTrackBase: (String) -> Unit = {},
    /**
     * APD-15: menus and Formatleiste go to the window's header through [FlowHost.menus] and [FlowHost.toolbar] (like
     * Google Docs) instead of above the page; a Statusleiste shows pages, words and zoom.
     */
    chromeInHost: Boolean = false,
) {
    val state = remember(doc.id, tabId) { FlowState(markdown).also { FlowState.shown = java.lang.ref.WeakReference(it) } }
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(FlowView.PAGES) }
    var zoom by remember { mutableStateOf(1f) }
    // Gliederung on the left (AP-46) and the Lineal (AP-44), like in Docs: on on wide screens.
    var outline by remember { mutableStateOf(!narrow) }
    var ruler by remember { mutableStateOf(true) }
    val defaults = c.settings.current.docStyleLines
    val raws = remember(state.version) { state.rawMarkdown() }
    val sheet = remember(raws, doc.style, defaults) { sheetFor(raws, doc.style, defaults) }
    if (state.sheet != sheet) state.sheet = sheet
    val comments = remember(doc.comments, tabId) { openComments(doc, tabId) }
    if (state.comments != comments) state.comments = comments
    val scroll = androidx.compose.foundation.rememberScrollState()
    DisposableEffect(state, scroll) {
        state.scrollTo = { y ->
            val page = state.pageCoords
            val content = state.contentCoords
            val inContent = if (page != null && content != null && page.isAttached && content.isAttached) content.localPositionOf(page, Offset(0f, y)).y else y
            scope.launch { scroll.animateScrollTo((inContent - 120f).roundToInt().coerceAtLeast(0)) }
        }
        onDispose { state.scrollTo = null }
    }
    // Changes from outside (the model, Lora's chat, another PC) are loaded; the editor's own saves are not.
    LaunchedEffect(markdown) {
        if (markdown != state.lastMarkdown && state.writing == null && state.suggestions().isEmpty()) state.load(markdown)
    }
    // Änderungen nachverfolgen: each piece of running text knows its text of then (the changes show in the text).
    LaunchedEffect(trackBase, markdown, state.version) { state.track(trackBase) }
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
            override fun markdown(): String = state.markdown()
            override fun insertBlock(markdown: String): String? {
                val before = state.markdown()
                state.insertBlock(markdown)
                return before
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

    fun quickRevise(instruction: String?) {
        if (instruction == null) { askRevise = true; return }
        if (!startRevising(c, doc, state, "markierung", instruction)) Notices.error(tr("Bitte zuerst Text markieren."))
    }
    fun openFind() {
        state.active?.let { t -> if (!t.selection.collapsed) t.rich.text.substring(t.selection.min, t.selection.max).takeIf { '\n' !in it }?.let { state.find = it } }
        state.findOpen = true
    }

    // Menus and Formatleiste: above the page, or in the window's header (APD-15). Remembered once per editor, so the
    // header does not recompose with every key; they read the current document and the editor's state when shown.
    val docNow by rememberUpdatedState(doc)
    val menus: @Composable () -> Unit = remember(state, host) {
        @Composable {
            FlowMenus(
                c, docNow, state, view, onView = { view = it }, zoom = zoom, onZoom = { zoom = it }, host = host, onWrite = { askWrite = true }, onRevise = { askRevise = true },
                onLink = { state.active?.selection?.takeIf { !it.collapsed }?.let { linkFor = it.min until it.max } },
                outline = outline, onOutline = { outline = !outline }, ruler = ruler, onRuler = { ruler = !ruler }, onFind = ::openFind,
                compact = chromeInHost,
            )
        }
    }
    val toolbar: @Composable () -> Unit = remember(state, host) {
        @Composable {
            FlowToolbar(c, docNow, state, host, onWrite = { askWrite = true }, onLink = {
                state.active?.selection?.let { linkFor = it.min until it.max }
            })
        }
    }
    DisposableEffect(state, host, chromeInHost) {
        host.undo = { state.undo() }
        host.redo = { state.redo() }
        host.find = ::openFind
        host.link = { state.active?.selection?.let { linkFor = it.min until it.max } }
        if (chromeInHost) {
            host.menus = menus
            host.toolbar = toolbar
        }
        onDispose {
            host.undo = null
            host.redo = null
            host.find = null
            host.link = null
            host.menus = null
            host.toolbar = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!chromeInHost) {
            menus()
            toolbar()
            HorizontalDivider()
        }
        if (state.findOpen) FlowFindBar(state) { seg, range -> state.show(seg, range) }
        // Lora writes (AP-47): her request, then Übernehmen · Neu · Verwerfen.
        LoraWriteBar(c, doc, state)
        ReviewBar(state)
        if (trackBase != null) TrackBar(state, onBase = onTrackBase, onStop = { host.onTrack?.invoke() })
        Row(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            if (outline && !narrow) {
                FlowOutline(state, onJump = { state.jumpToHeading(it) }, onClose = { outline = false }, modifier = Modifier.width(if (chromeInHost) 208.dp else 240.dp))
                androidx.compose.material3.VerticalDivider()
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                FlowPages(
                    c, doc, tabId, state, view, zoom, narrow, host, scroll, ruler = ruler, onWrite = { askWrite = true },
                    onRevise = ::quickRevise, onFind = ::openFind,
                )
            }
        }
        if (chromeInHost && !narrow) FlowStatusBar(state, view, zoom, onZoom = { zoom = it }, tracking = trackBase != null, onTrack = host.onTrack)
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
    AlertDialog(
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

/**
 * Änderungen nachverfolgen im Fließtext (AP-44): the changes since tracking began, one by one – Annehmen moves one into
 * the text of then ([onBase]), Ablehnen takes it back in the text.
 */
@Composable
private fun TrackBar(state: FlowState, onBase: (String) -> Unit, onStop: () -> Unit) {
    val list = state.tracked()
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp))
            if (list.isEmpty()) {
                Text(tr("Änderungen werden nachverfolgt – noch keine Änderungen."), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onStop) { Text(tr("Beenden")) }
                return@Row
            }
            val at = state.trackAt.coerceIn(0, list.lastIndex)
            val (seg, h) = list[at]
            val removed = seg.base?.text?.let { b -> if (h.del.isEmpty()) "" else b.substring(h.del.first, h.del.last + 1) }.orEmpty().replace('\n', ' ').trim()
            val added = if (h.ins.isEmpty()) "" else seg.rich.text.substring(h.ins.first, h.ins.last + 1).replace('\n', ' ').trim()
            Column(Modifier.weight(1f)) {
                Text(tr("Änderung {0} von {1}", at + 1, list.size), style = MaterialTheme.typography.labelLarge)
                Text(
                    buildAnnotatedString {
                        if (removed.isNotEmpty()) { pushStyle(SpanStyle(color = Color(0xFFC62828), textDecoration = TextDecoration.LineThrough)); append(removed.take(80)); pop(); append("  ") }
                        if (added.isNotEmpty()) { pushStyle(SpanStyle(color = Color(0xFF1B7F3B))); append(added.take(80)); pop() }
                    },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
            }
            fun go(i: Int) {
                state.trackAt = i
                list.getOrNull(i)?.let { (t, x) -> state.show(t, x.at until x.at) }
            }
            TextButton(onClick = { go((at - 1).coerceAtLeast(0)) }, enabled = at > 0) { Text("‹") }
            TextButton(onClick = { go((at + 1).coerceAtMost(list.lastIndex)) }, enabled = at < list.lastIndex) { Text("›") }
            FilledTonalButton(onClick = { state.acceptTracked(seg, h)?.let(onBase) }) { Text(tr("Annehmen")) }
            TextButton(onClick = { state.rejectTracked(seg, h) }) { Text(tr("Ablehnen")) }
            var more by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { more = true }) { Text(tr("Alle …")) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(tr("Alle annehmen")) }, onClick = { more = false; onBase(state.markdown()) })
                    DropdownMenuItem(text = { Text(tr("Alle ablehnen")) }, onClick = { more = false; state.rejectAllTracked() })
                    DropdownMenuItem(text = { Text(tr("Nachverfolgen beenden")) }, onClick = { more = false; onStop() })
                }
            }
        }
    }
}

/** The menus like in Docs: Datei · Bearbeiten · Ansicht · Einfügen · Format · Extras. */
@Composable
private fun FlowMenus(
    c: AppContainer, doc: LiveDoc, state: FlowState, view: FlowView, onView: (FlowView) -> Unit, zoom: Float, onZoom: (Float) -> Unit, host: FlowHost,
    onWrite: () -> Unit, onRevise: () -> Unit, onLink: () -> Unit,
    outline: Boolean, onOutline: () -> Unit, ruler: Boolean, onRuler: () -> Unit, onFind: () -> Unit,
    /** In the window's header under the title (APD-15): small menu names like in Google Docs. */
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf<String?>(null) }
    var tablePick by remember { mutableStateOf(false) }
    var sectionFor by remember { mutableStateOf<DocMarkup.Section?>(null) }
    var special by remember { mutableStateOf(false) }
    var autoCorrect by remember { mutableStateOf(false) }
    // Diagramme und Whiteboard (AP-61).
    var newDiagram by remember { mutableStateOf<String?>(null) }
    @Composable
    fun menu(name: String, items: @Composable (close: () -> Unit) -> Unit) {
        Box {
            if (compact) {
                // Like Google Docs: plain words, a soft background on hover and while the menu is open.
                val source = remember { MutableInteractionSource() }
                val hovered by source.collectIsHoveredAsState()
                Text(
                    tr(name), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (open == name || hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
                        .hoverable(source)
                        .clickable(interactionSource = source, indication = null, role = androidx.compose.ui.semantics.Role.Button) { open = name }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            } else {
                TextButton(onClick = { open = name }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr(name), style = MaterialTheme.typography.bodyMedium) }
            }
            DropdownMenu(open == name, { open = null }) { items { open = null } }
        }
    }
    @Composable
    fun item(label: String, shortcut: String = "", enabled: Boolean = true, onClick: () -> Unit, close: () -> Unit) =
        MenuRow(tr(label), if (shortcut.isNotEmpty()) tr(shortcut) else "", enabled) { close(); onClick() }
    Row(Modifier.then(if (compact) Modifier else Modifier.fillMaxWidth()).horizontalScroll(rememberScrollState()).padding(horizontal = if (compact) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        menu("Datei") { close ->
            val file = host.fileMenu
            if (file != null) file(close) else {
                item("Exportieren …", onClick = host.onExport, close = close)
                item("Seite einrichten …", onClick = host.onPageSetup, close = close)
                item("Versionen …", onClick = host.onHistory, close = close)
            }
        }
        menu("Bearbeiten") { close ->
            item("Rückgängig", "Strg+Z", state.canUndo, { state.undo() }, close)
            item("Wiederholen", "Strg+Y", state.canRedo, { state.redo() }, close)
            item("Alles auswählen", "Strg+A", onClick = { state.active?.let { it.selection = TextRange(0, it.rich.text.length) } }, close = close)
            item("Suchen und ersetzen", "Strg+F", onClick = onFind, close = close)
        }
        menu("Ansicht") { close ->
            item(if (view == FlowView.PAGES) "✓ Seitenansicht" else "Seitenansicht", onClick = { onView(FlowView.PAGES) }, close = close)
            item(if (view == FlowView.PAGELESS) "✓ Ohne Seiten" else "Ohne Seiten", onClick = { onView(FlowView.PAGELESS) }, close = close)
            HorizontalDivider()
            listOf(0.75f, 0.9f, 1f, 1.25f, 1.5f).forEach { z -> item((if (zoom == z) "✓ " else "") + "Zoom ${(z * 100).roundToInt()} %", onClick = { onZoom(z) }, close = close) }
            HorizontalDivider()
            item(if (outline) "✓ Gliederung" else "Gliederung", onClick = onOutline, close = close)
            item(if (ruler) "✓ Lineal" else "Lineal", onClick = onRuler, close = close)
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
            item("Seitenumbruch", "Strg+Enter", onClick = { state.insertBlock("\\newpage") }, close = close)
            item("Abschnittsumbruch …", onClick = { sectionFor = DocMarkup.Section() }, close = close)
            item("Inhaltsverzeichnis", onClick = { state.insertBlock(DocMarkup.TOC) }, close = close)
            item("Fußnote", onClick = { insertFootnote(state) }, close = close)
            item("Formel", onClick = { state.insertBlock("$$\nE = mc^2\n$$") }, close = close)
            item("Diagramm …", onClick = { newDiagram = "flow" }, close = close)
            item("Whiteboard …", onClick = { newDiagram = "board" }, close = close)
            item("Trennlinie", onClick = { state.insertBlock("---") }, close = close)
            item("Datum", onClick = { state.insertText(DocMarkup.today()) }, close = close)
            item("Sonderzeichen …", onClick = { special = true }, close = close)
            host.onAddTab?.let { add ->
                HorizontalDivider()
                item("Tab", onClick = add, close = close)
            }
        }
        menu("Format") { close ->
            item("Fett", "Strg+B", onClick = { state.toggle({ it.bold }) { f, v -> f.copy(bold = v) } }, close = close)
            item("Kursiv", "Strg+I", onClick = { state.toggle({ it.italic }) { f, v -> f.copy(italic = v) } }, close = close)
            item("Unterstrichen", "Strg+U", onClick = { state.toggle({ it.underline }) { f, v -> f.copy(underline = v) } }, close = close)
            item("Durchgestrichen", onClick = { state.toggle({ it.strike }) { f, v -> f.copy(strike = v) } }, close = close)
            item("Hochgestellt", onClick = { state.toggle({ it.sup }) { f, v -> f.copy(sup = v, sub = false) } }, close = close)
            item("Tiefgestellt", onClick = { state.toggle({ it.sub }) { f, v -> f.copy(sub = v, sup = false) } }, close = close)
            item("Code", onClick = { state.toggle({ it.code }) { f, v -> f.copy(code = v) } }, close = close)
            // Tasten wie Word (AP-125 c).
            item("Schrift größer", "Strg+Umschalt+>", onClick = { state.active?.let { state.stepFontSize(it, up = true) } }, close = close)
            item("Schrift kleiner", "Strg+Umschalt+<", onClick = { state.active?.let { state.stepFontSize(it, up = false) } }, close = close)
            item("Groß-/Kleinschreibung", "Umschalt+F3", onClick = { state.active?.let { state.cycleCase(it) } }, close = close)
            HorizontalDivider()
            ParaKind.entries.forEach { k ->
                item(k.label, when (k.level) { 0 -> if (k == ParaKind.NORMAL) "Strg+Alt+0" else ""; else -> "Strg+Alt+${k.level}" }, onClick = { state.kind(k) }, close = close)
            }
            HorizontalDivider()
            // Zeilenabstand (AP-44).
            val p = state.paraHere()
            LINE_SPACINGS.forEach { (z, label) -> item((if (p.spacing == z) "✓ " else "") + tr("Zeilenabstand {0}", tr(label)), onClick = { state.paragraph { it.copy(spacing = z) } }, close = close) }
            item((if (p.spacing == null) "✓ " else "") + tr("Zeilenabstand der Formatvorlage"), onClick = { state.paragraph { it.copy(spacing = null) } }, close = close)
            // Tabstopps (AP-44): set by a click into the ruler, moved by dragging, removed here or by dragging off.
            item("Tabstopps entfernen", enabled = p.tabs.isNotEmpty(), onClick = { state.paragraph { it.copy(tabs = emptyList()) } }, close = close)
            HorizontalDivider()
            // Formatvorlagen (AP-46).
            item(tr("„{0}“ an Markierung anpassen", tr(Styles.base(p.kind).label)), onClick = { adaptStyle(state) }, close = close)
            StyleSet.entries.forEach { set ->
                item((if (state.sheet.set == set) "✓ " else "") + tr("Vorlagen: {0}", tr(set.label)), onClick = { switchStyleSet(c, doc, state, set) }, close = close)
            }
            item("Formatvorlagen zurücksetzen", enabled = state.sheet.changed.isNotEmpty(), onClick = { state.applySheet(state.sheet.copy(changed = emptyMap(), explicit = true)) }, close = close)
            item("Als Standard für neue Dokumente speichern", onClick = {
                c.settings.update { it.copy(docStyleLines = Styles.defaultLines(state.sheet)) }
                Notices.info(tr("Gespeichert: Dokumente ohne eigene Formatvorlagen sehen jetzt so aus."))
            }, close = close)
            HorizontalDivider()
            item("Format übertragen", onClick = { state.painter = state.formatHere() to state.paraHere() }, close = close)
            item("Formatierung entfernen", "Strg+\\", onClick = { state.clearFormat() }, close = close)
        }
        menu("Extras") { close ->
            item("Hilf mir schreiben", "Strg+J", onClick = onWrite, close = close)
            item("Markierung mit Lora überarbeiten …", onClick = onRevise, close = close)
            item("Lora fragen", onClick = host.onAskAi, close = close)
            item("Rechtschreibung prüfen", "F7", onClick = host.onSpell, close = close)
            item("Autokorrektur …", onClick = { autoCorrect = true }, close = close)
            host.onSpellAi?.let { item("Rechtschreibung prüfen (KI)", onClick = it, close = close) }
            host.onTrack?.let { item("Änderungen nachverfolgen", onClick = it, close = close) }
            item("Wortzahl", onClick = {
                val words = state.segs.filterIsInstance<FlowSeg.Text>().sumOf { t -> Regex("\\S+").findAll(t.rich.text).count() }
                Notices.info(tr("{0} Wörter", words))
            }, close = close)
        }
    }
    sectionFor?.let { sec ->
        SectionDialog(sec, onDismiss = { sectionFor = null }) { s2 -> sectionFor = null; state.insertBlock(DocMarkup.sectionMarker(s2)) }
    }
    if (tablePick) TablePickDialog(onDismiss = { tablePick = false }) { rows, cols ->
        tablePick = false
        state.insertBlock(DocBlocks.table(listOf(List(cols) { tr("Spalte {0}", it + 1) }) + List(rows - 1) { List(cols) { "" } }))
    }
    if (special) SpecialCharsDialog(onDismiss = { special = false }) { ch -> special = false; state.insertText(ch) }
    if (autoCorrect) AutoCorrectDialog(c, onDismiss = { autoCorrect = false })
    newDiagram?.let { kind ->
        DiagramEditorDialog(if (kind == "board") io.github.thepytales.llmhub.docs.Diagram(kind = "board", manual = true) else starterDiagram(kind), onDismiss = { newDiagram = null }) { d ->
            newDiagram = null
            state.insertBlock(io.github.thepytales.llmhub.docs.Diagrams.block(d))
        }
    }
}

/** Line spacings to choose (AP-44). */
private val LINE_SPACINGS = listOf(1.0f to "Einfach", 1.15f to "1,15", 1.5f to "1,5", 2.0f to "Doppelt")

/** „Überschrift 1 an Markierung anpassen“ (AP-46): the Formatvorlage of the paragraph at the cursor takes its look. */
internal fun adaptStyle(state: FlowState) {
    val p = state.paraHere()
    val kind = Styles.base(p.kind)
    val def = Styles.adapt(state.sheet.of(kind), state.formatHere(), p)
    state.applySheet(state.sheet.copy(changed = state.sheet.changed + (kind to def), explicit = true))
    Notices.info(tr("„{0}“ angepasst – alle Absätze mit dieser Formatvorlage sehen jetzt so aus.", tr(kind.label)))
}

/** Another set of Formatvorlagen (AP-46): the whole document changes its look; exports follow (its style). */
internal fun switchStyleSet(c: AppContainer, doc: LiveDoc, state: FlowState, set: StyleSet) {
    state.applySheet(StyleSheet(set, emptyMap(), explicit = true))
    c.docs.update(doc.id) { it.copy(style = set.docStyle?.name) }
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
/** Extras › Autokorrektur (AP-125): each rule on or off, for all documents. */
@Composable
private fun AutoCorrectDialog(c: AppContainer, onDismiss: () -> Unit) {
    var off by remember { mutableStateOf(AutoCorrect.rulesOff(c.settings.current.docAutocorrectOff)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Autokorrektur")) },
        text = {
            Column {
                HintText(tr("Beim Tippen in Dokumenten. Strg+Z direkt danach nimmt eine Korrektur zurück."))
                Spacer(Modifier.height(8.dp))
                AutoCorrect.Rule.entries.forEach { rule ->
                    Row(
                        Modifier.fillMaxWidth().clickable { off = if (rule in off) off - rule else off + rule }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tr(rule.label), Modifier.weight(1f))
                        Switch(checked = rule !in off, onCheckedChange = { on -> off = if (on) off - rule else off + rule })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.settings.update { it.copy(docAutocorrectOff = AutoCorrect.setting(off)) }
                onDismiss()
            }) { Text(tr("Übernehmen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun TablePickDialog(onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    var hover by remember { mutableStateOf(3 to 3) }
    AlertDialog(
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
    AlertDialog(
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
private fun FlowToolbar(c: AppContainer, doc: LiveDoc, state: FlowState, host: FlowHost, onWrite: () -> Unit, onLink: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = state.version
    val f = state.formatHere()
    val p = state.paraHere()
    // Diktat (AP-65): speak at the cursor; „Punkt“, „Komma“, „neuer Absatz“ become marks and paragraphs.
    val dictation = rememberDictation(c) { text -> state.dictate(text) }
    // Groups in the order of Google Docs; what does not fit goes into „⋯“ from the right (APD-15), nothing is cut off.
    val groups: List<@Composable () -> Unit> = listOf(
        {
            ToolIcon(Icons.AutoMirrored.Outlined.Undo, tr("Rückgängig") + " (Strg+Z)", enabled = state.canUndo) { state.undo() }
            ToolIcon(Icons.AutoMirrored.Outlined.Redo, tr("Wiederholen") + " (Strg+Y)", enabled = state.canRedo) { state.redo() }
        },
        {
            var styleMenu by remember { mutableStateOf(false) }
            Box {
                BarDropdown(tr(p.kind.label), tr("Formatvorlage"), Modifier.widthIn(max = 132.dp)) { styleMenu = true }
                DropdownMenu(styleMenu, { styleMenu = false }) {
                    // The Formatvorlagen as they look in this document (AP-46); lists are the buttons further right.
                    Styles.KINDS.forEach { k ->
                        val d = state.sheet.of(k)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    tr(k.label),
                                    fontSize = ptSp(d.size.coerceIn(9f, 20f), 0.9f),
                                    fontWeight = if (d.bold) FontWeight.Bold else null,
                                    fontStyle = if (d.italic) FontStyle.Italic else null,
                                    fontFamily = if (k == ParaKind.CODE) FontFamily.Monospace else familyOf(d.font),
                                    color = d.color?.let(::hex) ?: Color.Unspecified,
                                    maxLines = 1,
                                )
                            },
                            trailingIcon = { if (Styles.base(p.kind) == k) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                            onClick = { styleMenu = false; state.kind(k) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(tr("„{0}“ an Markierung anpassen", tr(Styles.base(p.kind).label))) }, onClick = { styleMenu = false; adaptStyle(state) })
                    Text(tr("Vorlagensatz"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    StyleSet.entries.forEach { set ->
                        DropdownMenuItem(
                            text = { Column { Text(tr(set.label)); Text(tr(set.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            trailingIcon = { if (state.sheet.set == set) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                            onClick = { styleMenu = false; switchStyleSet(c, doc, state, set) },
                        )
                    }
                }
            }
            var fontMenu by remember { mutableStateOf(false) }
            Box {
                BarDropdown(f.font ?: tr("Standard"), tr("Schriftart"), Modifier.widthIn(max = 116.dp)) { fontMenu = true }
                DropdownMenu(fontMenu, { fontMenu = false }) {
                    FONTS.forEach { (font, label) -> DropdownMenuItem(text = { Text(tr(label), fontFamily = familyOf(font)) }, onClick = { fontMenu = false; state.format { it.copy(font = font) } }) }
                }
            }
            var sizeMenu by remember { mutableStateOf(false) }
            Box {
                val size = f.size?.toString() ?: state.sheet.of(p.kind).size.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString().replace('.', ',') }
                BarDropdown(size, tr("Schriftgröße")) { sizeMenu = true }
                DropdownMenu(sizeMenu, { sizeMenu = false }) {
                    DropdownMenuItem(text = { Text(tr("Standard")) }, onClick = { sizeMenu = false; state.format { it.copy(size = null) } })
                    SIZES.forEach { s -> DropdownMenuItem(text = { Text("$s") }, onClick = { sizeMenu = false; state.format { it.copy(size = s) } }) }
                }
            }
        },
        {
            ToolIcon(Icons.Outlined.FormatBold, tr("Fett") + " (Strg+B)", active = f.bold) { state.toggle({ it.bold }) { x, on -> x.copy(bold = on) } }
            ToolIcon(Icons.Outlined.FormatItalic, tr("Kursiv") + " (Strg+I)", active = f.italic) { state.toggle({ it.italic }) { x, on -> x.copy(italic = on) } }
            ToolIcon(Icons.Outlined.FormatUnderlined, tr("Unterstrichen") + " (Strg+U)", active = f.underline) { state.toggle({ it.underline }) { x, on -> x.copy(underline = on) } }
            ColorButton(Icons.Outlined.FormatColorText, tr("Textfarbe"), TEXT_COLORS, f.color) { col -> state.format { it.copy(color = col) } }
            ColorButton(Icons.Outlined.FormatColorFill, tr("Hervorheben"), HIGHLIGHTS, f.highlight) { col -> state.format { it.copy(highlight = col) } }
        },
        {
            ToolIcon(Icons.Outlined.Link, tr("Link")) { onLink() }
            ToolIcon(Icons.Outlined.AddComment, tr("Kommentar")) { host.onComment(selectedText(state)) }
            ToolIcon(Icons.Outlined.Image, tr("Bild")) { host.onImage() }
        },
        {
            // Ausrichtung as one button with its four choices (like Google Docs); it shows the current one.
            var alignMenu by remember { mutableStateOf(false) }
            val aligns = listOf(
                Align.LEFT to (Icons.AutoMirrored.Outlined.FormatAlignLeft to "Linksbündig"),
                Align.CENTER to (Icons.Outlined.FormatAlignCenter to "Zentriert (Strg+Umschalt+E)"),
                Align.RIGHT to (Icons.AutoMirrored.Outlined.FormatAlignRight to "Rechtsbündig (Strg+R)"),
                Align.JUSTIFY to (Icons.Outlined.FormatAlignJustify to "Blocksatz (Strg+Umschalt+J)"),
            )
            Box {
                val (icon, _) = aligns.firstOrNull { it.first == p.align }?.second ?: aligns.first().second
                ToolIcon(icon, tr("Ausrichtung")) { alignMenu = true }
                DropdownMenu(alignMenu, { alignMenu = false }) {
                    Row(Modifier.padding(horizontal = 6.dp)) {
                        aligns.forEach { (a, il) -> ToolIcon(il.first, tr(il.second), active = p.align == a) { alignMenu = false; state.paragraph { it.copy(align = a) } } }
                    }
                }
            }
            var spacingMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.FormatLineSpacing, tr("Zeilenabstand")) { spacingMenu = true }
                DropdownMenu(spacingMenu, { spacingMenu = false }) {
                    LINE_SPACINGS.forEach { (z, label) ->
                        DropdownMenuItem(text = { Text(tr(label)) }, trailingIcon = { if (p.spacing == z) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }, onClick = { spacingMenu = false; state.paragraph { it.copy(spacing = z) } })
                    }
                    DropdownMenuItem(text = { Text(tr("Wie Formatvorlage")) }, trailingIcon = { if (p.spacing == null) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }, onClick = { spacingMenu = false; state.paragraph { it.copy(spacing = null) } })
                }
            }
        },
        {
            ToolIcon(Icons.Outlined.CheckBox, tr("Checkliste"), active = p.kind == ParaKind.CHECK) { state.kind(ParaKind.CHECK) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, tr("Aufzählung"), active = p.kind == ParaKind.BULLET) { state.kind(ParaKind.BULLET) }
            ToolIcon(Icons.Outlined.FormatListNumbered, tr("Nummerierte Liste"), active = p.kind == ParaKind.NUMBERED) { state.kind(ParaKind.NUMBERED) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, tr("Einzug verkleinern"), enabled = p.kind.list && p.indent > 0) { state.indent(-1) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, tr("Einzug vergrößern"), enabled = p.kind.list) { state.indent(1) }
        },
        {
            ToolIcon(Icons.Outlined.FormatPaint, tr("Format übertragen (Pinsel)"), active = state.painter != null) {
                state.painter = if (state.painter != null) null else state.formatHere() to state.paraHere()
                if (state.painter != null) Notices.info(tr("Jetzt den Text markieren, der dieses Format bekommen soll."))
            }
            ToolIcon(Icons.Outlined.FormatClear, tr("Formatierung entfernen") + " (Strg+\\)") { state.clearFormat() }
        },
        {
            ToolIcon(Icons.Outlined.EditNote, tr("Hilf mir schreiben") + " (Strg+J)") { onWrite() }
            DictateIcon(dictation)
        },
    )
    CompositionLocalProvider(LocalToolIconSize provides 34.dp) {
        OverflowBar(groups, Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

/**
 * A row of tool groups that never cuts anything off (APD-15): the groups that fit stand in the row, the rest open from
 * „⋯“ at its end – like the Formatleiste of Google Docs in a narrow window. Separators stand between the groups.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverflowBar(groups: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    var moreOpen by remember { mutableStateOf(false) }
    // How many groups stand in the row (set while measuring; the menu of „⋯“ shows the others).
    var shownForMenu by remember { mutableIntStateOf(groups.size) }
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { constraints ->
        val loose = androidx.compose.ui.unit.Constraints(maxHeight = constraints.maxHeight)
        val parts = groups.mapIndexed { i, g ->
            subcompose("g$i") { Row(verticalAlignment = Alignment.CenterVertically) { if (i > 0) Sep(); g() } }.map { it.measure(loose) }
        }
        val widths = parts.map { ps -> ps.sumOf { it.width } }
        val total = widths.sum()
        val max = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        var shown = groups.size
        val more = if (total <= max) null else {
            val moreParts = subcompose("more") {
                Box {
                    ToolIcon(Icons.Outlined.MoreHoriz, tr("Weitere Werkzeuge"), active = moreOpen) { moreOpen = true }
                    DropdownMenu(moreOpen, { moreOpen = false }) {
                        FlowRow(Modifier.widthIn(max = 340.dp).padding(horizontal = 6.dp), verticalArrangement = Arrangement.Center) {
                            groups.drop(shownForMenu).forEach { g -> Row(verticalAlignment = Alignment.CenterVertically) { g() } }
                        }
                    }
                }
            }.map { it.measure(loose) }
            val moreW = moreParts.sumOf { it.width }
            var used = 0
            shown = 0
            while (shown < groups.size && used + widths[shown] + moreW <= max) { used += widths[shown]; shown++ }
            moreParts
        }
        if (shownForMenu != shown) shownForMenu = shown
        val placed = parts.take(shown).flatten() + more.orEmpty()
        val height = (placed.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placed.sumOf { it.width }
        layout(width, height) {
            var x = 0
            placed.forEach { pl -> pl.placeRelative(x, (height - pl.height) / 2); x += pl.width }
        }
    }
}

/**
 * Statusleiste under the page (APD-15, like Word): page of pages, words (of the selection, too), tracked changes, zoom.
 * The words are counted a moment after typing stops, off the main thread (long documents).
 */
@Composable
private fun FlowStatusBar(state: FlowState, view: FlowView, zoom: Float, onZoom: (Float) -> Unit, tracking: Boolean, onTrack: (() -> Unit)?) {
    var words by remember { mutableIntStateOf(0) }
    var chars by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.version) {
        delay(300)
        val texts = state.segs.filterIsInstance<FlowSeg.Text>().map { it.rich.text }
        val (w, ch) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { texts.sumOf { t -> WORD.findAll(t).count() } to texts.sumOf { it.length } }
        words = w
        chars = ch
    }
    val selected = state.active?.let { t -> if (t.selection.collapsed) null else t.rich.text.substring(t.selection.min, t.selection.max.coerceAtMost(t.rich.text.length)) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.labelSmall
    @Composable
    fun part(text: String, label: String? = null, onClick: (() -> Unit)? = null) = Text(
        text, style = small, color = muted, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (view == FlowView.PAGES) part(tr("Seite {0} von {1}", state.pageNow, state.pageCount.coerceAtLeast(1)))
            val wordText = if (selected != null) tr("{0} von {1} Wörtern", WORD.findAll(selected).count(), words) else tr("{0} Wörter", words)
            part(wordText, tr("Wortzahl")) { Notices.info(tr("{0} Wörter · {1} Zeichen · {2} Seiten", words, chars, state.pageCount.coerceAtLeast(1))) }
            if (tracking) part(tr("Änderungen werden nachverfolgt"), tr("Änderungen nachverfolgen"), onTrack)
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalToolIconSize provides 26.dp) {
                ToolIcon(Icons.Outlined.Remove, tr("Verkleinern")) { onZoom(ZOOMS.lastOrNull { it < zoom - 0.01f } ?: zoom) }
                var zoomMenu by remember { mutableStateOf(false) }
                Box {
                    part("${(zoom * 100).roundToInt()} %", tr("Zoom")) { zoomMenu = true }
                    DropdownMenu(zoomMenu, { zoomMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Seitenbreite")) }, onClick = { zoomMenu = false; onZoom(state.fitZoom) })
                        HorizontalDivider()
                        ZOOMS.forEach { z ->
                            DropdownMenuItem(
                                text = { Text("${(z * 100).roundToInt()} %") },
                                trailingIcon = { if (kotlin.math.abs(zoom - z) < 0.01f) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                                onClick = { zoomMenu = false; onZoom(z) },
                            )
                        }
                    }
                }
                ToolIcon(Icons.Outlined.Add, tr("Vergrößern")) { onZoom(ZOOMS.firstOrNull { it > zoom + 0.01f } ?: zoom) }
            }
        }
    }
}

private val WORD = Regex("\\S+")
private val ZOOMS = listOf(0.5f, 0.75f, 0.9f, 1f, 1.25f, 1.5f, 2f)

/** A choice in the Formatleiste as a word with ▾ (Formatvorlage, Schrift, Größe), compact like in Google Docs. */
@Composable
private fun BarDropdown(text: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    // Bounded width, so the word can shorten with „…“ (the Formatleiste measures its groups without a width limit).
    Row(
        modifier
            .widthIn(max = 140.dp)
            .height(LocalToolIconSize.current - 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = "$label: $text" }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(" ▾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** The pages: the document's pieces one below the other on A4 pages (Seitenansicht) or on one running page. */
@Composable
private fun FlowPages(
    c: AppContainer, doc: LiveDoc, tabId: String?, state: FlowState, view: FlowView, zoom: Float, narrow: Boolean, host: FlowHost,
    scroll: ScrollState, ruler: Boolean, onWrite: () -> Unit, onRevise: (String?) -> Unit, onFind: () -> Unit,
) {
    val density = LocalDensity.current
    val raws = remember(state.version) { state.rawMarkdown() }
    val setup = remember(raws) { DocMarkup.pageSetup(raws) }
    val margin = if (narrow) 20.dp else (setup.marginMm * 3.7795f).dp * zoom
    val pages = view == FlowView.PAGES && !narrow
    val pageWidth = if (pages) 794.dp * zoom else androidx.compose.ui.unit.Dp.Unspecified
    // A4 is 297 mm high; the text area between the margins makes one page, pages lie 16 dp apart.
    val pageFull = 1123.dp * zoom
    val pageGap = 16.dp
    val spacing = 6.dp
    val textHeightPx = with(density) { (pageFull - margin * 2).toPx() }
    val betweenPx = with(density) { (margin * 2 + pageGap).toPx() }
    val spacingPx = with(density) { spacing.toPx() }
    val minFieldPx = with(density) { 28.dp.toPx() }
    // Echte Seiten (AP-43): the page plan follows the measured lines and blocks; the room it adds does not change them.
    LaunchedEffect(state, pages, textHeightPx, betweenPx) {
        if (!pages) {
            clearPlan(state)
            return@LaunchedEffect
        }
        var version = -1
        var applied = 0
        snapshotFlow { pageItems(state, spacingPx, minFieldPx) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (items, owners) ->
                // Never more than a few plans for one text (rounding of the measured lines must not make it swing).
                if (state.version != version) { version = state.version; applied = 0 }
                if (++applied > 8) return@collect
                state.pageText = textHeightPx
                state.pageBetween = betweenPx
                applyPlan(PageLayout.plan(items, textHeightPx, betweenPx), owners, state)
            }
    }
    val look = LocalChatLook.current
    val outline = MaterialTheme.colorScheme.outline
    val sheetBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val marginText = rememberTextMeasurer()
    val topPad = if (narrow) 8.dp else 24.dp
    val count = state.pageCount.coerceAtLeast(1)
    val today = remember { DocMarkup.today() }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val viewHeight = with(density) { maxHeight.toPx() }.takeIf { it.isFinite() } ?: 4000f
    // Comments at the margin only where they fit beside the page (not with a narrow window or the side panel open).
    val showComments = !narrow && state.comments.isNotEmpty() && (!pages || maxWidth >= pageWidth + 250.dp)
    // Statusleiste (APD-15): the page in the upper third of the window, and the zoom at which a page fills the width.
    val pitchPx = with(density) { (pageFull + pageGap).toPx() }
    val fit = ((maxWidth - 48.dp) / 794.dp).coerceIn(0.5f, 2f)
    androidx.compose.runtime.SideEffect { if (state.fitZoom != fit) state.fitZoom = fit }
    LaunchedEffect(scroll, pitchPx, pages, viewHeight) {
        snapshotFlow { scroll.value }.collect { y ->
            state.pageNow = if (!pages) 1 else ((y + viewHeight / 3f) / pitchPx).toInt().plus(1).coerceIn(1, state.pageCount.coerceAtLeast(1))
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.onGloballyPositioned { state.contentCoords = it }, verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = if (pages) Modifier else Modifier.weight(1f, fill = false)) {
                if (ruler && pages) {
                    Spacer(Modifier.height(8.dp))
                    FlowRuler(state, pageWidth, setup.marginMm, zoom)
                }
                Column(
                    Modifier
                        .padding(vertical = if (ruler && pages) 8.dp else topPad)
                        .then(if (pageWidth != androidx.compose.ui.unit.Dp.Unspecified) Modifier.width(pageWidth) else Modifier.fillMaxWidth().widthIn(max = 900.dp))
                        .then(
                            if (!pages) {
                                Modifier
                                    .background(look.card, RoundedCornerShape(4.dp))
                                    .border(1.dp, sheetBorder, RoundedCornerShape(4.dp))
                            } else {
                                // Seitenansicht: one sheet per page with its header and footer (page numbers, date).
                                Modifier.drawBehind {
                                    val full = pageFull.toPx()
                                    val gap = pageGap.toPx()
                                    val m = margin.toPx()
                                    val style = TextStyle(fontSize = (9f * zoom).coerceAtLeast(7f).sp, color = outline)
                                    val half = ((size.width - 2 * m) / 2f).coerceAtLeast(10f).toInt()
                                    // Only the sheets around the visible part are drawn (große Dokumente: 200+ pages).
                                    val viewTop = scroll.value.toFloat() - full
                                    val viewBottom = scroll.value.toFloat() + viewHeight + full
                                    for (p in 0 until count) {
                                        val top = p * (full + gap)
                                        if (top + full < viewTop || top > viewBottom) continue
                                        drawRoundRect(look.card, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, full), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                                        drawRoundRect(sheetBorder, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, full), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                                        val pm = pageMargins(setup, p + 1, count, today, sectionOf(state.sectionPages, setup, p + 1))
                                        // Measured first and drawn as a layout: drawText(measurer, text, topLeft) only allows the
                                        // width left of topLeft inside this box (a negative width crashed the app before).
                                        fun put(text: String, right: Boolean, footer: Boolean) {
                                            if (text.isBlank()) return
                                            val l = marginText.measure(text, style, maxLines = 1, constraints = androidx.compose.ui.unit.Constraints(maxWidth = half))
                                            val yy = if (footer) top + full - m / 2f - l.size.height / 2f else top + m / 2f - l.size.height / 2f
                                            drawText(l, topLeft = Offset(if (right) size.width - m - l.size.width else m, yy))
                                        }
                                        put(pm.headerLeft, right = false, footer = false)
                                        put(pm.headerRight, right = true, footer = false)
                                        put(pm.footerLeft, right = false, footer = true)
                                        put(pm.footerRight, right = true, footer = true)
                                    }
                                }
                            },
                        )
                        .padding(horizontal = margin, vertical = if (narrow) 20.dp else margin)
                        .onGloballyPositioned { state.pageCoords = it }
                        .heightIn(min = if (pages) with(density) { (textHeightPx * count + betweenPx * (count - 1)).toDp() } else 300.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    if (!pages && setup.hasHeader) Text(listOfNotNull(setup.header.takeIf { it.isNotBlank() }, DocMarkup.today().takeIf { setup.date }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = outline)
                    state.segs.forEach { seg ->
                        androidx.compose.runtime.key(seg.id) {
                            when (seg) {
                                is FlowSeg.Text -> FlowField(c, state, seg, zoom, host, onWrite, onRevise, onFind)
                                // Setting lines of the Formatvorlagen are not drawn (the look shows them).
                                is FlowSeg.Raw -> if (!isHiddenSetting(seg.markdown)) RawBlock(doc, tabId, state, seg, pages, zoom, host)
                            }
                        }
                    }
                    if (!pages && setup.footer.isNotBlank()) Text(setup.footer, style = MaterialTheme.typography.labelSmall, color = outline)
                }
            }
            if (showComments) {
                val offset = with(density) { ((if (ruler && pages) 8.dp + 22.dp + 8.dp else topPad) + margin).toPx() }
                FlowMarginComments(c, doc, state, offset, onOpen = { host.onShowComments() }, modifier = Modifier.padding(top = 0.dp))
            }
        }
    }
    }
}

/** A block of its own between the running text: shown as it looks; tables edit in place, others via ✎. */
@Composable
private fun RawBlock(doc: LiveDoc, tabId: String?, state: FlowState, seg: FlowSeg.Raw, pages: Boolean, zoom: Float, host: FlowHost) {
    var editing by remember { mutableStateOf(false) }
    val image = remember(seg.markdown) { DocMarkup.image(seg.markdown) }
    val density = LocalDensity.current
    // Room above it when it starts a page (Seitenansicht); its own height is measured without that room.
    // Inside a selection over several fields the block is marked as a whole.
    val inSpan = state.spanIn(seg) != null
    val outer = Modifier.fillMaxWidth().padding(top = with(density) { seg.push.toDp() })
        .then(if (inSpan) Modifier.background(LocalChatLook.current.brand.copy(alpha = 0.18f), RoundedCornerShape(4.dp)) else Modifier)
    val measure = Modifier.onSizeChanged { if (kotlin.math.abs(seg.height - it.height) > 0.5f) seg.height = it.height.toFloat() }
    // Abschnittsumbruch (AP-43/45): a new page with the section's own header and footer.
    val section = DocMarkup.section(seg.markdown)
    if (section != null) {
        var edit by remember { mutableStateOf(false) }
        Row(outer.then(measure), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MarkerLine(sectionLabel(section)) }
            IconButton(onClick = { edit = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Edit, contentDescription = tr("Abschnitt bearbeiten"), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = { state.setRaw(seg, "") }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(16.dp)) }
        }
        if (edit) SectionDialog(section, onDismiss = { edit = false }) { s2 -> edit = false; state.setRaw(seg, DocMarkup.sectionMarker(s2)) }
        return
    }
    // Seitenumbruch (AP-43): in the Seitenansicht the text goes on on the next page.
    if (DocMarkup.isPageBreak(seg.markdown)) {
        Row(outer.then(measure), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageBreakFill(0f) }
            IconButton(onClick = { state.setRaw(seg, "") }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(16.dp)) }
        }
        return
    }
    Row(outer.then(measure), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) {
            if (seg.markdown.trim().startsWith("|")) {
                val paging = remember(seg) { rawPaging(seg) }
                FlowTableBlock(state, seg, paging = if (pages) paging else null, zoom = zoom, onMarkdown = { editing = true })
            } else if (image != null) {
                FlowImageBlock(state, seg, image, host, onMarkdown = { editing = true })
            } else {
                BlockView(doc, tabId, seg.markdown, highlight = false, editable = true, onClick = { editing = true }, onToggleTask = {}, onShowComments = {})
            }
        }
        // Pictures and tables are handled on themselves (AP-125 d: handles, bar, Rechtsklick); other blocks keep ✎ and 🗑.
        if (image == null && !seg.isTable) Column {
            IconButton(onClick = { editing = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Edit, contentDescription = tr("Bearbeiten"), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = { state.setRaw(seg, "") }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(16.dp)) }
        }
    }
    // A diagram (AP-61) opens in the diagram editor; everything else as Markdown.
    val diagram = remember(seg.markdown) { io.github.thepytales.llmhub.docs.Diagrams.parseBlock(seg.markdown)?.let { (l, c) -> io.github.thepytales.llmhub.docs.Diagrams.ofBlock(l, c) } }
    if (editing && diagram != null) DiagramEditorDialog(diagram, onDismiss = { editing = false }) { d -> editing = false; state.setRaw(seg, io.github.thepytales.llmhub.docs.Diagrams.block(d)) }
    else if (editing) TextPrompt(tr("Block bearbeiten (Markdown)"), seg.markdown, onDismiss = { editing = false }) { md -> editing = false; state.setRaw(seg, md) }
}

/** Tabellen über Seiten (AP-43): the room of the page plan for the rows of [seg], and where its rows are measured. */
private fun rawPaging(seg: FlowSeg.Raw) = object : TablePaging {
    override fun push(row: Int) = seg.rowPush[row] ?: 0f
    override fun repeats(row: Int) = row in seg.repeatAt
    override fun headerHeight() = seg.rowHeights.firstOrNull()?.coerceAtLeast(0f) ?: 0f
    override fun measuredRow(row: Int, rows: Int, height: Float) {
        val old = seg.rowHeights
        if (old.size == rows && kotlin.math.abs(old[row] - height) <= 0.5f) return
        seg.rowHeights = List(rows) { i -> if (i == row) height else old.getOrElse(i) { -1f } }
    }
    override fun measuredBar(height: Float) {
        if (kotlin.math.abs(seg.barHeight - height) > 0.5f) seg.barHeight = height
    }
}

/** What a section break says in the editor: „Abschnittsumbruch · Kopfzeile: … · Fußzeile: …“. */
internal fun sectionLabel(s: DocMarkup.Section): String = listOfNotNull(
    tr("Abschnittsumbruch"),
    s.header?.let { if (it.isBlank()) tr("ohne Kopfzeile") else tr("Kopfzeile: {0}", it) },
    s.footer?.let { if (it.isBlank()) tr("ohne Fußzeile") else tr("Fußzeile: {0}", it) },
).joinToString(" · ")

/** Header and footer of a section: take over the ones before, none, or own text (fields {seite}, {seiten}). */
@Composable
private fun SectionDialog(initial: DocMarkup.Section, onDismiss: () -> Unit, onDone: (DocMarkup.Section) -> Unit) {
    var ownHeader by remember { mutableStateOf(initial.header != null) }
    var header by remember { mutableStateOf(initial.header.orEmpty()) }
    var ownFooter by remember { mutableStateOf(initial.footer != null) }
    var footer by remember { mutableStateOf(initial.footer.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Abschnittsumbruch")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Ab hier beginnt eine neue Seite. Der Abschnitt kann eine eigene Kopf- und Fußzeile haben (Felder: {seite}, {seiten})."), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(ownHeader, { ownHeader = it })
                    Text(tr("Eigene Kopfzeile"))
                }
                if (ownHeader) OutlinedTextField(header, { header = it.replace("\n", " ") }, singleLine = true, label = { Text(tr("Kopfzeile (leer: keine)")) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(ownFooter, { ownFooter = it })
                    Text(tr("Eigene Fußzeile"))
                }
                if (ownFooter) OutlinedTextField(footer, { footer = it.replace("\n", " ") }, singleLine = true, label = { Text(tr("Fußzeile (leer: keine)")) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onDone(DocMarkup.Section(header.takeIf { ownHeader }, footer.takeIf { ownFooter })) }) { Text(tr("Übernehmen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** `**fett**` and `*kursiv*` typed in the running text become formatting (AP-46), like in Docs. */
private val TYPED_BOLD = Regex("(?<![*\\\\])\\*\\*([^*\\n]*[^*\\s])\\*\\*$")
private val TYPED_ITALIC = Regex("(?<![*\\w\\\\])\\*([^*\\s][^*\\n]*?)\\*$")

/** The piece of [r] in the selection as HTML and plain text (Zwischenablage mit Formatierung, AP-43). */
private fun copySelection(c: AppContainer, seg: FlowSeg.Text): Boolean {
    val sel = seg.selection
    if (sel.collapsed) return false
    val piece = seg.rich.slice(sel.min, sel.max)
    val plain = piece.text.filterIndexed { i, _ -> piece.chars[i].ins == 0 }
    return RichClipboard.copy(c, RichHtml.fromRich(piece), plain, runCatching { io.github.thepytales.llmhub.docs.text.RichRtf.fromRich(piece) }.getOrNull())
}

/**
 * Rechtsklick-Menü im Text (AP-125, like Word and Google Docs): spelling suggestions for the word under the cursor (on
 * the device), Ausschneiden · Kopieren · Einfügen · Nur Text einfügen, Link (öffnen, entfernen), Kommentar, Lora,
 * Formatierung, Alles auswählen – with the keys beside them.
 */
@Composable
private fun FlowTextMenu(
    c: AppContainer, state: FlowState, seg: FlowSeg.Text, host: FlowHost,
    onWrite: () -> Unit, onRevise: (String?) -> Unit, onDismiss: () -> Unit,
) {
    val sel = seg.selection
    val hasSelection = !sel.collapsed || state.spanParts() != null
    val text = seg.rich.text
    // The word under the cursor (or the selected word) and what the spell checker of the device suggests for it.
    val word = remember(sel, seg.rich) {
        if (!sel.collapsed && sel.max - sel.min <= 40) text.substring(sel.min, sel.max).takeIf { w -> w.isNotBlank() && w.all { it.isLetter() || it == '-' } }?.let { sel.min until sel.max }
        else if (sel.collapsed) {
            var a = sel.start.coerceIn(0, text.length)
            var b = a
            while (a > 0 && text[a - 1].isLetter()) a--
            while (b < text.length && text[b].isLetter()) b++
            if (b - a >= 2) a until b else null
        } else null
    }
    var suggestions by remember(word) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(word) {
        val w = word ?: return@LaunchedEffect
        val found = runCatching { io.github.thepytales.llmhub.docs.LocalSpelling.check(text.substring(w.first, w.last + 1)) }.getOrDefault(emptyList())
        suggestions = found.firstOrNull()?.suggestions?.take(5)
    }
    val link = seg.rich.chars.getOrNull(sel.start.coerceAtMost(text.length - 1).coerceAtLeast(0))?.link?.takeIf { text.isNotEmpty() }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    @Composable
    fun entry(label: String, keys: String = "", enabled: Boolean = true, onClick: () -> Unit) =
        MenuRow(label, keys, enabled) { onDismiss(); onClick() }
    DropdownMenu(true, onDismiss) {
        val w = word
        suggestions?.let { list ->
            if (w != null) {
                if (list.isEmpty()) Text(tr("Keine Vorschläge"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                list.forEach { s ->
                    DropdownMenuItem(modifier = MENU_ROW, text = { Text(s, fontWeight = FontWeight.SemiBold) }, onClick = {
                        onDismiss()
                        state.checkpoint()
                        seg.rich = seg.rich.replace(w.first until w.last + 1, s, seg.rich.chars.getOrNull(w.first)?.stored ?: CharFormat.PLAIN.stored)
                        seg.selection = TextRange(w.first + s.length)
                        state.changed()
                    })
                }
                entry(tr("Zum Wörterbuch hinzufügen")) {
                    val word0 = text.substring(w.first, w.last + 1)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { runCatching { io.github.thepytales.llmhub.docs.LocalSpelling.learn(word0) } }
                }
                HorizontalDivider()
            }
        }
        entry(tr("Ausschneiden"), tr("Strg+X"), enabled = hasSelection) { cutSelection(c, state, seg) }
        entry(tr("Kopieren"), tr("Strg+C"), enabled = hasSelection) { if (state.spanParts() != null) state.copySpan(c) else copySelection(c, seg) }
        entry(tr("Einfügen"), tr("Strg+V")) { if (!pasteClipboard(c, state, seg, plain = false)) Notices.info(tr("Die Zwischenablage ist leer.")) }
        entry(tr("Nur Text einfügen"), tr("Strg+Umschalt+V")) { if (!pasteClipboard(c, state, seg, plain = true)) Notices.info(tr("Die Zwischenablage ist leer.")) }
        HorizontalDivider()
        if (link != null) {
            entry(tr("Link öffnen")) { runCatching { uri.openUri(link) } }
            entry(tr("Link entfernen")) {
                // The whole link around the cursor.
                var a = sel.start.coerceAtMost(text.length - 1)
                var b = a
                while (a > 0 && seg.rich.chars[a - 1].link == link) a--
                while (b < text.length && seg.rich.chars[b].link == link) b++
                state.checkpoint()
                seg.rich = seg.rich.format(a until b) { it.copy(link = null) }
                state.changed()
            }
        } else entry(tr("Link …"), enabled = !sel.collapsed) { host.link?.invoke() }
        entry(tr("Kommentar")) { host.onComment(if (sel.collapsed) "" else text.substring(sel.min, sel.max)) }
        if (hasSelection) {
            entry(tr("Mit Lora überarbeiten …")) { onRevise(null) }
            entry(tr("Lora fragen")) { host.onAskAi() }
        } else entry(tr("Hilf mir schreiben"), tr("Strg+J")) { onWrite() }
        HorizontalDivider()
        entry(tr("Format übertragen")) { state.painter = state.formatHere() to state.paraHere() }
        entry(tr("Formatierung entfernen"), "Strg+\\") { state.clearFormat() }
        entry(tr("Alles auswählen"), tr("Strg+A")) {
            // Several fields: the selection over all of them; one field: its whole text.
            if (!state.selectAllPieces()) state.focusPiece(seg, TextRange(0, seg.rich.text.length), keepSpan = false)
        }
    }
}

/** Rows of the editor's menus: compact for the mouse on the PC (like Word and Docs), finger-sized on the phone. */
internal val MENU_ROW: Modifier = if (DESKTOP) Modifier.height(36.dp) else Modifier

/**
 * A row of the editor's menus (APD-15/AP-125): the word on one line, its keys right-aligned in their own column – like
 * Word and Docs, and without the 280 dp limit of Material's menu rows (long keys such as Strg+Umschalt+V fit).
 */
@Composable
internal fun MenuRow(label: String, keys: String, enabled: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(
        Modifier.fillMaxWidth().widthIn(min = 180.dp).heightIn(min = if (DESKTOP) 36.dp else 48.dp)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = color, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
        if (keys.isNotEmpty()) {
            Spacer(Modifier.width(32.dp))
            Text(keys, color = MaterialTheme.colorScheme.onSurfaceVariant.let { if (enabled) it else it.copy(alpha = 0.38f) }, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
        }
    }
}

/** Ausschneiden (Strg+X, Rechtsklick): the selection with its formatting to the clipboard, then out of the text. */
private fun cutSelection(c: AppContainer, state: FlowState, seg: FlowSeg.Text): Boolean {
    if (state.spanParts() != null) return state.copySpan(c) && state.deleteSpan()
    if (!copySelection(c, seg)) return false
    val sel = seg.selection
    state.checkpoint()
    seg.rich = seg.rich.cut(sel.min, sel.max)
    seg.selection = TextRange(sel.min)
    state.changed()
    return true
}

/**
 * Einfügen from the clipboard (Strg+V, Strg+Umschalt+V, Rechtsklick): with formatting from Word, Docs and the browser
 * (HTML, RTF), or [plain] – only the text, in the format at the cursor (*Nur Text einfügen*, AP-125).
 */
private fun pasteClipboard(c: AppContainer, state: FlowState, seg: FlowSeg.Text, plain: Boolean): Boolean {
    if (!plain) {
        val piece = (RichClipboard.html(c)?.let { runCatching { RichHtml.toRich(it) }.getOrNull() }
            ?: RichClipboard.rtf(c)?.let { runCatching { io.github.thepytales.llmhub.docs.text.RichRtf.toRich(it) }.getOrNull() })?.takeIf { it.text.isNotEmpty() }
        if (piece != null) { state.paste(seg, piece); return true }
    }
    val text = RichClipboard.text(c)?.replace("\r\n", "\n")?.replace('\r', '\n') ?: return false
    state.active = seg
    state.insertText(text)
    return true
}

/** One stretch of running text: the text field with list marks in the gutter and the keys of a word processor. */
@Composable
private fun FlowField(
    c: AppContainer, state: FlowState, seg: FlowSeg.Text, zoom: Float, host: FlowHost,
    onWrite: () -> Unit, onRevise: (String?) -> Unit, onFind: () -> Unit,
) {
    val density = LocalDensity.current
    val look = LocalChatLook.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val sheet = state.sheet
    val bodyDef = sheet.of(ParaKind.NORMAL)
    val base = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface, fontSize = ptSp(bodyDef.size, zoom), fontFamily = familyOf(bodyDef.font),
        lineHeight = (bodyDef.spacing * 1.2f).em,
    )
    val rich = seg.rich
    // Hits of the search (the current one stronger) and commented text as backgrounds.
    val hitColor = Color(0x66FFD54F)
    val currentHit = Color(0xFFFFA726)
    val commentColor = Color(0x40F9A825)
    val hits = state.hitsIn(rich)
    val current = state.hits().getOrNull(state.findAt)?.takeIf { it.first === seg }?.second
    val quoted = state.comments.mapNotNull { cm -> state.findQuote(cm.quote)?.takeIf { it.first === seg }?.second }
    // The part of a selection over several fields that lies in this one (the active field shows its own selection).
    val spanHere = if (state.active !== seg) state.spanIn(seg)?.second?.takeIf { !it.isEmpty() } else null
    val marks = quoted.map { it to commentColor } + hits.map { it to (if (it == current) currentHit else hitColor) } +
        listOfNotNull(spanHere?.let { it to look.brand.copy(alpha = 0.28f) })
    val inserted = seg.hunks.map { it.ins }.filter { !it.isEmpty() }
    val annotated = remember(rich, look.brand, zoom, sheet, marks, inserted) { flowAnnotated(rich, base, look.brand, muted, codeBg, density.density, sheet, zoom, marks, inserted) }
    val tabbed = remember(annotated, seg.tabExtra, density) { FlowTabs.annotate(annotated, seg.tabExtra, density.density, density.fontScale) }
    val value = TextFieldValue(tabbed, seg.selection, seg.composition)
    val pxPerMm = with(density) { (3.7795f * zoom).dp.toPx() }
    val measurer = rememberTextMeasurer()
    val marksList = remember(rich) { listMarks(rich) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    /** Rechtsklick-Menü (AP-125): where it opens in this field, or null. */
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    // The text after a page break of Strg+Enter takes the cursor once it is shown.
    LaunchedEffect(seg) {
        if (state.wantFocus === seg) {
            state.wantFocus = null
            state.focusPiece(seg, TextRange(0), keepSpan = false)
        }
    }

    // Format übertragen (AP-46): the next selection takes the format, once the selection rests.
    LaunchedEffect(seg.selection, state.painter) {
        val paint = state.painter ?: return@LaunchedEffect
        if (seg.selection.collapsed || state.active !== seg) return@LaunchedEffect
        delay(450)
        val sel = seg.selection
        if (sel.collapsed) return@LaunchedEffect
        state.checkpoint()
        seg.rich = seg.rich.format(sel.min until sel.max) { paint.first.stored }
            .formatParas(sel.min, sel.max) { q -> q.copy(align = paint.second.align, spacing = paint.second.spacing, left = paint.second.left, first = paint.second.first) }
        state.painter = null
        state.changed()
    }

    fun onValue(v: TextFieldValue) {
        val old = seg.rich
        if (v.text != old.text) {
            var r = old.edited(v.text, seg.typing)
            var sel = v.selection
            var typing: CharFormat? = null
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
            // „**fett**“ and „*kursiv*“: the marks go, the text between them is formatted.
            if (v.text.length == old.text.length + 1 && sel.collapsed && sel.start > 1 && v.text[sel.start - 1] == '*' && r.text.length == v.text.length) {
                val p = r.paraAt(sel.start - 1)
                val ps = r.starts[p]
                val before = r.text.substring(ps, sel.start)
                val bold = TYPED_BOLD.find(before)
                val italic = if (bold == null) TYPED_ITALIC.find(before) else null
                val m = bold ?: italic
                if (m != null && r.paras[p].kind != ParaKind.CODE) {
                    val w = if (bold != null) 2 else 1
                    val a = ps + m.range.first
                    val e = sel.start
                    r = r.cut(e - w, e).cut(a, a + w)
                    val inner = a until e - 2 * w
                    r = r.format(inner) { if (bold != null) it.copy(bold = true) else it.copy(italic = true) }
                    sel = TextRange(e - 2 * w)
                    typing = r.chars.getOrNull(inner.last)?.let { if (bold != null) it.copy(bold = false) else it.copy(italic = false) }
                }
            }
            // Autokorrektur (AP-125): one typed character, nothing else changed, no input method composing.
            val typedAt = sel.start - 1
            val fix = if (v.text.length == old.text.length + 1 && sel.collapsed && typedAt >= 0 && r.text == v.text && v.composition == null) {
                val code = r.chars.getOrNull(typedAt)?.code == true || r.paras[r.paraAt(typedAt)].kind == ParaKind.CODE
                AutoCorrect.after(r.text, sel.start, r.text[typedAt], AutoCorrect.rulesOff(c.settings.current.docAutocorrectOff), code)
            } else null
            state.checkpoint(group = true)
            seg.rich = r
            seg.selection = sel
            seg.composition = v.composition
            seg.typing = typing
            state.changed()
            if (fix != null) {
                // Its own step: Rückgängig right after it takes back only the correction (like Word).
                state.checkpoint()
                val fmt = r.chars.getOrNull(fix.range.first)?.stored ?: CharFormat.PLAIN
                seg.rich = seg.rich.replace(fix.range, fix.text, fmt)
                seg.selection = TextRange(fix.cursor)
                state.changed()
            }
        } else {
            if (v.selection != seg.selection) seg.typing = null
            var sel = v.selection
            val anchor = state.spanAnchor
            if (state.shiftClick === seg && anchor != null) {
                state.shiftClick = null
                val edge = if (state.segs.indexOf(anchor.first) < state.segs.indexOf(seg)) 0 else seg.rich.text.length
                sel = TextRange(edge, sel.end)
            } else if (anchor != null && v.selection != seg.selection && !state.shiftDown && state.active === seg) {
                // A click or a key without Umschalt ends the selection over several fields.
                state.spanAnchor = null
            }
            seg.selection = sel
            seg.composition = v.composition
        }
    }

    // Room above the text when it starts a page (Seitenansicht, AP-43).
    Box(Modifier.fillMaxWidth().padding(top = with(density) { seg.push.toDp() }).onGloballyPositioned { co ->
        // Where this text is on the page (jumps of the Gliederung, comments at the margin).
        val page = state.pageCoords
        if (page != null && page.isAttached && co.isAttached) {
            val y = page.localPositionOf(co, Offset.Zero).y
            if (state.segTop[seg.id]?.let { kotlin.math.abs(it - y) < 0.5f } != true) state.segTop[seg.id] = y
        }
    }.pointerInput(seg) {
        // Rechtsklick (AP-125, like Word): the cursor goes to the click unless it is inside the selection, then the menu
        // opens there. Read in the first pass, so the text field does not treat the press as its own.
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (e.type != androidx.compose.ui.input.pointer.PointerEventType.Press) continue
                // Strg+Klick (AP-125 c, like Word): on a link it opens the link, elsewhere it marks the sentence.
                if (e.buttons.isPrimaryPressed && (e.keyboardModifiers.ctrlHeld || e.keyboardModifiers.metaHeld) && !e.keyboardModifiers.shiftHeld) {
                    val l = seg.layout ?: continue
                    val pos = e.changes.firstOrNull()?.position ?: continue
                    e.changes.forEach { it.consume() }
                    val o = Shown(seg.rich.text, seg.breaks, seg.deleted).toOrig(l.getOffsetForPosition(pos)).coerceIn(0, seg.rich.text.length)
                    val link = seg.rich.chars.getOrNull(o)?.link ?: seg.rich.chars.getOrNull(o - 1)?.link?.takeIf { o == seg.rich.text.length }
                    if (link != null) runCatching { uri.openUri(link) } else state.selectSentence(seg, o)
                    continue
                }
                if (!e.buttons.isSecondaryPressed) continue
                val at = e.changes.firstOrNull()?.position ?: continue
                e.changes.forEach { it.consume() }
                val l = seg.layout
                if (l != null) {
                    val o = Shown(seg.rich.text, seg.breaks, seg.deleted).toOrig(l.getOffsetForPosition(at)).coerceIn(0, seg.rich.text.length)
                    val sel = seg.selection
                    if (sel.collapsed || o < sel.min || o > sel.max) {
                        state.spanAnchor = null
                        seg.selection = TextRange(o)
                    }
                }
                state.active = seg
                menuAt = at
            }
        }
    }) {
        menuAt?.let { at ->
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)) {
                FlowTextMenu(c, state, seg, host, onWrite = onWrite, onRevise = onRevise, onDismiss = { menuAt = null })
            }
        }
        OwnTextMenu {
        BasicTextField(
            value = value,
            onValueChange = ::onValue,
            textStyle = base,
            cursorBrush = SolidColor(look.brand),
            // Page breaks inside the text: room shown in the text, not part of it.
            visualTransformation = remember(seg.breaks, seg.deleted, density) {
                if (seg.breaks.isEmpty() && seg.deleted.isEmpty()) VisualTransformation.None
                else PageSpacers(seg.breaks, density.density, density.fontScale, seg.deleted, SpanStyle(color = Color(0xFFC62828), textDecoration = TextDecoration.LineThrough))
            },
            onTextLayout = { l ->
                seg.layout = l
                // Tabstopps: the room after each tab follows the new layout (a few rounds at most per text).
                if (seg.tabFor !== seg.rich) { seg.tabFor = seg.rich; seg.tabRounds = 0 }
                if ('\t' in seg.rich.text || seg.tabExtra.isNotEmpty()) {
                    val next = FlowTabs.extras(l, seg.rich, seg::shown, seg.tabExtra, pxPerMm)
                    if (FlowTabs.differ(next, seg.tabExtra) && seg.tabRounds++ < 6) seg.tabExtra = next
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp)
                .focusRequester(seg.focus)
                .onFocusChanged {
                    if (it.isFocused) state.picked = null
                    if (!it.isFocused || state.active === seg) return@onFocusChanged
                    val before = state.active
                    // Umschalt+Klick into another field: the selection reaches from the cursor before to the click.
                    if (!state.focusByEditor && state.shiftDown && before != null) {
                        if (state.spanAnchor == null) state.spanAnchor = before to before.selection.start
                        state.shiftClick = seg
                    } else if (!state.focusByEditor) state.spanAnchor = null
                    state.focusByEditor = false
                    state.active = seg
                }
                .semantics { contentDescription = tr("Dokumenttext") }
                .drawBehind {
                    // List marks (•, 1., a.) in the gutter before the indented text.
                    val layout = seg.layout ?: return@drawBehind
                    marksList.forEach { (pi, mark) ->
                        if (mark == "check") return@forEach
                        val off = seg.shown(rich.starts[pi]).coerceAtMost(layout.layoutInput.text.length)
                        val line = layout.getLineForOffset(off)
                        val indent = (28f * (rich.paras[pi].indent + 1)).sp.toPx() * zoom
                        val m = measurer.measure(mark, base.copy(fontWeight = FontWeight.Normal))
                        drawText(m, topLeft = Offset(indent - m.size.width - 6.dp.toPx(), layout.getLineBaseline(line) - m.firstBaseline))
                    }
                }
                .onPreviewKeyEvent { e ->
                    state.shiftDown = e.isShiftPressed
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // Over the edges of this field: other pieces, the selection over several of them (FlowSpan).
                    if (state.pieceKey(c, seg, e)) return@onPreviewKeyEvent true
                    val cmd = e.isCtrlPressed || e.isMetaPressed
                    val shift = e.isShiftPressed
                    when {
                        // Tasten und Markieren wie Word (AP-125 c, FlowKeys): by word and paragraph, to the ends of the
                        // document, deleting words – also over the edges of this field; Umschalt marks.
                        cmd && !e.isAltPressed && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) -> state.moveByWord(seg, e.key == Key.DirectionRight, shift)
                        cmd && !e.isAltPressed && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> state.moveByParagraph(seg, e.key == Key.DirectionDown, shift)
                        cmd && !e.isAltPressed && (e.key == Key.MoveHome || e.key == Key.MoveEnd) -> state.moveToDocumentEdge(seg, e.key == Key.MoveEnd, shift)
                        cmd && !e.isAltPressed && !shift && (e.key == Key.Backspace || e.key == Key.Delete) -> state.deleteWord(seg, e.key == Key.Delete)
                        cmd && !e.isAltPressed && !shift && (e.key == Key.Enter || e.key == Key.NumPadEnter) -> state.pageBreakHere()
                        e.isAltPressed && shift && !cmd && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> state.moveParagraphs(seg, e.key == Key.DirectionUp)
                        shift && !cmd && !e.isAltPressed && e.key == Key.F3 -> state.cycleCase(seg)
                        !shift && !cmd && !e.isAltPressed && e.key == Key.F7 -> { host.onSpell(); true }
                        // Schrift größer/kleiner: Strg+Umschalt+> and < – on a German keyboard the key beside Umschalt
                        // (with Umschalt bigger, without smaller), on others Strg+Umschalt+. and , (also Google Docs).
                        cmd && !e.isAltPressed && isAngleKey(e) -> state.stepFontSize(seg, up = shift)
                        cmd && shift && !e.isAltPressed && (e.key == Key.Period || e.key == Key.Comma) -> state.stepFontSize(seg, up = e.key == Key.Period)
                        // Format kopieren/einfügen: Strg+Umschalt+C like Word (Strg+Umschalt+V stays Nur Text einfügen),
                        // Strg+Alt+C/V like Google Docs.
                        cmd && (shift && !e.isAltPressed || e.isAltPressed && !shift) && e.key == Key.C -> state.copyFormat()
                        cmd && e.isAltPressed && !shift && e.key == Key.V -> state.pasteFormat(seg)
                        cmd && !e.isShiftPressed && e.key == Key.Z -> { state.undo(); true }
                        cmd && (e.key == Key.Y || e.isShiftPressed && e.key == Key.Z) -> { state.redo(); true }
                        cmd && !e.isAltPressed && e.key == Key.B -> { state.toggle({ it.bold }) { f, on -> f.copy(bold = on) }; true }
                        cmd && !e.isAltPressed && e.key == Key.I -> { state.toggle({ it.italic }) { f, on -> f.copy(italic = on) }; true }
                        cmd && !e.isAltPressed && e.key == Key.U -> { state.toggle({ it.underline }) { f, on -> f.copy(underline = on) }; true }
                        cmd && !e.isShiftPressed && e.key == Key.J -> { onWrite(); true }
                        // Ausrichtung (AP-125): keys that no app-wide shortcut uses (Strg+E stays the KI, Strg+L Lora,
                        // Strg+Umschalt+L Literatur) – Zentriert and Blocksatz like Google Docs, Rechtsbündig like Word.
                        cmd && e.isShiftPressed && e.key == Key.E -> { state.paragraph { it.copy(align = Align.CENTER) }; true }
                        cmd && e.isShiftPressed && e.key == Key.J -> { state.paragraph { it.copy(align = Align.JUSTIFY) }; true }
                        cmd && !e.isShiftPressed && e.key == Key.R -> { state.paragraph { it.copy(align = Align.RIGHT) }; true }
                        cmd && (e.key == Key.F || e.key == Key.H) -> { onFind(); true }
                        cmd && e.key == Key.Backslash -> { state.clearFormat(); true }
                        // Zwischenablage mit Formatierung (AP-43): HTML for Word and the browser, HTML from them.
                        cmd && !e.isShiftPressed && e.key == Key.C -> copySelection(c, seg)
                        cmd && !e.isShiftPressed && e.key == Key.X -> cutSelection(c, state, seg)
                        // Nur Text einfügen (AP-125, like Word and Docs).
                        cmd && e.isShiftPressed && e.key == Key.V -> pasteClipboard(c, state, seg, plain = true)
                        // The menu key and Umschalt+F10 open the Rechtsklick-Menü at the cursor (keyboard, AP-30).
                        (e.key == Key.Menu || e.isShiftPressed && e.key == Key.F10) && !cmd -> {
                            val l = seg.layout
                            menuAt = l?.let { val r = it.getCursorRect(seg.shown(seg.selection.end).coerceIn(0, it.layoutInput.text.length)); Offset(r.left, r.bottom) } ?: Offset.Zero
                            true
                        }
                        cmd && !e.isShiftPressed && e.key == Key.V -> {
                            val piece = (RichClipboard.html(c)?.let { runCatching { RichHtml.toRich(it) }.getOrNull() }
                                ?: RichClipboard.rtf(c)?.let { runCatching { io.github.thepytales.llmhub.docs.text.RichRtf.toRich(it) }.getOrNull() })?.takeIf { it.text.isNotEmpty() }
                            if (piece == null) false else { state.paste(seg, piece); true }
                        }
                        cmd && e.isAltPressed && e.key == Key.Zero -> { state.kind(ParaKind.NORMAL); true }
                        cmd && e.isAltPressed && e.key == Key.One -> { state.kind(ParaKind.H1); true }
                        cmd && e.isAltPressed && e.key == Key.Two -> { state.kind(ParaKind.H2); true }
                        cmd && e.isAltPressed && e.key == Key.Three -> { state.kind(ParaKind.H3); true }
                        cmd && e.isAltPressed && e.key == Key.Four -> { state.kind(ParaKind.H4); true }
                        cmd && e.isShiftPressed && e.key == Key.Seven -> { state.kind(ParaKind.NUMBERED); true }
                        cmd && e.isShiftPressed && e.key == Key.Eight -> { state.kind(ParaKind.BULLET); true }
                        cmd && e.isShiftPressed && e.key == Key.Nine -> { state.kind(ParaKind.CHECK); true }
                        e.key == Key.Escape && state.painter != null -> { state.painter = null; true }
                        e.key == Key.Tab && state.paraHere().kind.list -> { state.indent(if (e.isShiftPressed) -1 else 1); true }
                        // A tab in the text (AP-44): it goes to the next Tabstopp of the paragraph.
                        e.key == Key.Tab && !cmd && !e.isShiftPressed && !e.isAltPressed -> { state.insertText("\t"); true }
                        // Backspace at the start of a heading or list item first makes it normal text.
                        e.key == Key.Backspace && seg.selection.collapsed && seg.rich.starts.contains(seg.selection.start) && seg.rich.paras[seg.rich.paraAt(seg.selection.start)].let { it.kind != ParaKind.NORMAL } -> {
                            state.paragraph { p -> if (p.kind.list && p.indent > 0) p.copy(indent = p.indent - 1) else p.copy(kind = ParaKind.NORMAL, indent = 0) }
                            true
                        }
                        else -> false
                    }
                },
        )
        }
        // Checkboxes of the checklist: real buttons in the gutter.
        val layout = seg.layout
        if (layout != null) marksList.filter { it.second == "check" }.forEach { (pi, _) ->
            val off = seg.shown(rich.starts[pi]).coerceAtMost(layout.layoutInput.text.length)
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
        // Lora over the selection (AP-47): quick revisions as suggestions, or a comment.
        val sel = seg.selection
        if (layout != null && state.active === seg && !sel.collapsed && state.writing == null && !state.revising && state.painter == null && state.suggestions().isEmpty()) {
            val n = layout.layoutInput.text.length
            val box = layout.getBoundingBox(seg.shown(sel.min).coerceIn(0, (n - 1).coerceAtLeast(0)))
            val above = with(density) { 44.dp.toPx() }
            val y = if (box.top >= above) box.top - above else layout.getBoundingBox(seg.shown(sel.max - 1).coerceIn(0, (n - 1).coerceAtLeast(0))).bottom + with(density) { 4.dp.toPx() }
            FlowSelectionMenu(
                onRevise = onRevise,
                onComment = { host.onComment(rich.text.substring(sel.min.coerceAtMost(rich.text.length), sel.max.coerceAtMost(rich.text.length))) },
                modifier = Modifier.offset { IntOffset(box.left.roundToInt().coerceAtLeast(0), y.roundToInt()) },
            )
        }
        // An empty document: an invitation to write or to let Lora write.
        if (rich.text.isEmpty() && state.segs.count { it is FlowSeg.Text || it is FlowSeg.Raw && !isHiddenSetting(it.markdown) } == 1) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Hier schreiben – oder"), style = base, color = muted)
                TextButton(onClick = onWrite) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr("Hilf mir schreiben")) }
            }
        }
    }
}

/** Check mark used by tests and the review (keeps the import of the icon set in one place). */
internal val FLOW_CHECK = Icons.Outlined.Check
