package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.CellStyle
import io.github.thepytales.llmhub.docs.CondRule
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SheetChart
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetMeta
import io.github.thepytales.llmhub.docs.SheetTools

private val CELL_W = 118.dp
private val CELL_H = 34.dp
private val HEAD_W = 44.dp

/** What a ✨ suggestion of the table is for. */
private enum class SheetAi { FORMULA, FILL, EXPLAIN }

/** Cells copied in this editor: pasted again, their formulas move along like in Excel. */
private data class SheetClip(val text: String, val cells: List<List<String>>, val row: Int, val col: Int)

private var lastClip: SheetClip? = null

/** View filter of a column (like Excel's AutoFilter): only these values, text contains, number above or below. */
private data class ColFilter(val values: Set<String>? = null, val contains: String = "", val above: String = "", val below: String = "") {
    fun passes(text: String, value: SheetEngine.Value): Boolean {
        if (values != null && text !in values) return false
        if (contains.isNotBlank() && !text.contains(contains.trim(), ignoreCase = true)) return false
        val n = (value as? SheetEngine.Value.Num)?.v
        SheetEngine.numberOrDate(above)?.let { if (n == null || n <= it) return false }
        SheetEngine.numberOrDate(below)?.let { if (n == null || n >= it) return false }
        return true
    }
}

/** Colors offered for text and fill (Excel's light tones for fills). */
private val TEXT_COLORS = listOf("#000000" to trLater("Schwarz"), "#C00000" to trLater("Rot"), "#E26B0A" to trLater("Orange"), "#00863D" to trLater("Grün"), "#1F4E79" to trLater("Blau"), "#7030A0" to trLater("Lila"), "#7F7F7F" to trLater("Grau"))
private val FILL_COLORS = listOf("#FFF2CC" to trLater("Gelb"), "#FFC7CE" to trLater("Rot"), "#C6EFCE" to trLater("Grün"), "#DDEBF7" to trLater("Blau"), "#FCE4D6" to trLater("Orange"), "#E4DFEC" to trLater("Lila"), "#EDEDED" to trLater("Grau"))

private fun hexColor(hex: String?): Color? = hex?.let { SheetTools.rgb(it) }?.let { (r, g, b) -> Color(r, g, b) }

/**
 * The table editor (like Excel): cells with formulas, formula bar, row and column headers, ranges, formats,
 * conditional formatting, filters per column, sorting, Σ, function list, filling series, copy and paste, charts,
 * status bar with sum and average, and ✨ for formulas and filling columns. Each tab is one sheet; formulas may
 * refer to the other sheets (`'Tabelle 2'!A1`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetBody(
    c: AppContainer,
    doc: LiveDoc,
    markdown: String,
    tabId: String?,
    writing: Boolean,
    leading: @Composable () -> Unit,
    onMarkdown: (String) -> Unit,
    onComment: (String) -> Unit,
    inlineAi: InlineAiRunner,
) {
    val saved = remember(markdown) { SheetEngine.grid(markdown) }
    val savedMeta = remember(markdown) { SheetMeta.parse(markdown) }
    // Rows and formats as edited here; written back after each change.
    var rows by remember(doc.id, tabId) { mutableStateOf(saved) }
    var meta by remember(doc.id, tabId) { mutableStateOf(savedMeta) }
    LaunchedEffect(saved, savedMeta) {
        if (saved != rows) rows = saved
        if (savedMeta != meta) meta = savedMeta
    }
    val cols = rows.maxOf { it.size }.coerceAtLeast(1)
    var sel by remember(doc.id, tabId) { mutableStateOf(1.coerceAtMost(rows.lastIndex) to 0) }
    // Other corner of the selected range (null = just the cell).
    var anchor by remember(doc.id, tabId) { mutableStateOf<Pair<Int, Int>?>(null) }
    val (sr, sc) = sel.first.coerceIn(0, rows.lastIndex) to sel.second.coerceIn(0, cols - 1)
    val top = minOf(sr, anchor?.first ?: sr).coerceIn(0, rows.lastIndex)
    val bottom = maxOf(sr, anchor?.first ?: sr).coerceIn(0, rows.lastIndex)
    val left = minOf(sc, anchor?.second ?: sc).coerceIn(0, cols - 1)
    val right = maxOf(sc, anchor?.second ?: sc).coerceIn(0, cols - 1)
    val isRange = top != bottom || left != right
    val rangeName = SheetEngine.rangeRef(top, left, bottom, right)
    var bar by remember(doc.id, tabId) { mutableStateOf(TextFieldValue(rows.getOrNull(sr)?.getOrNull(sc).orEmpty())) }
    var aiMode by remember { mutableStateOf(SheetAi.FORMULA) }
    val barFocus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    // The other sheets of the document, so formulas can refer to them.
    val engine = remember(rows, doc.tabs, tabId) {
        val index = doc.tabs.indexOfFirst { it.id == tabId }.takeIf { it >= 0 } ?: if (tabId == null) 0 else -1
        if (index < 0) SheetEngine.Sheet(rows)
        else SheetEngine.Workbook(doc.tabs.mapIndexed { i, t -> t.title to if (i == index) rows else SheetEngine.grid(t.markdown) }).sheet(index)
    }
    val paint = remember(engine, meta.rules) { SheetTools.conditional(meta.rules, engine) }
    // Filter (like the AutoFilter of Excel): rows whose cells contain the text; the head row always stays.
    var filterOpen by remember(doc.id, tabId) { mutableStateOf(false) }
    var filter by remember(doc.id, tabId) { mutableStateOf("") }
    var colFilters by remember(doc.id, tabId) { mutableStateOf<Map<Int, ColFilter>>(emptyMap()) }
    var filterDialog by remember { mutableStateOf<Int?>(null) }
    val visibleRows = remember(rows, filter, engine, colFilters) {
        (0 until rows.size).filter { r ->
            r == 0 || (
                (filter.isBlank() || rows[r].indices.any { j -> engine.display(r, j).contains(filter.trim(), ignoreCase = true) }) &&
                    colFilters.all { (j, f) -> f.passes(engine.display(r, j), engine.value(r, j)) }
                )
        }
    }
    var condDialog by remember { mutableStateOf(false) }
    var shiftDown by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()

    fun write(next: List<List<String>>, nextMeta: SheetMeta = meta) {
        rows = next
        meta = nextMeta
        onMarkdown(SheetMeta.compose(next, nextMeta))
    }

    fun setCell(r: Int, col: Int, v: String) {
        if (rows.getOrNull(r)?.getOrNull(col).orEmpty() == v) return
        val width = maxOf(cols, col + 1)
        val next = (0 until maxOf(rows.size, r + 1)).map { i -> (0 until width).map { j -> if (i == r && j == col) v else rows.getOrNull(i)?.getOrNull(j).orEmpty() } }
        write(next)
    }

    fun select(r: Int, col: Int, extend: Boolean = false) {
        // The formula bar's text belongs to the cell that was selected until now.
        setCell(sr, sc, bar.text)
        val rr = r.coerceAtLeast(0)
        val cc = col.coerceAtLeast(0)
        val grown = if (rr >= rows.size || cc >= cols) (0 until maxOf(rows.size, rr + 1)).map { i -> (0 until maxOf(cols, cc + 1)).map { j -> rows.getOrNull(i)?.getOrNull(j).orEmpty() } } else rows
        if (grown !== rows) write(grown)
        anchor = if (extend) anchor ?: (sr to sc) else null
        sel = rr to cc
        val v = grown.getOrNull(rr)?.getOrNull(cc).orEmpty()
        bar = TextFieldValue(v, TextRange(v.length))
    }

    /** Selects the range (top, left, bottom, right); the cursor goes to its last cell. */
    fun selectRange(g: IntArray) {
        select(g[2], g[3])
        if (g[0] != g[2] || g[1] != g[3]) anchor = g[0] to g[1]
    }

    LaunchedEffect(sr, sc, saved) {
        val v = rows.getOrNull(sr)?.getOrNull(sc).orEmpty()
        if (v != bar.text) bar = TextFieldValue(v, TextRange(v.length))
    }

    /** Applies [transform] to the formats of the selected cells. */
    fun restyle(transform: (CellStyle) -> CellStyle) {
        setCell(sr, sc, bar.text)
        write(rows, meta.restyle(top, left, bottom, right, transform))
    }

    fun copy() {
        setCell(sr, sc, bar.text)
        val raw = (top..bottom).map { r -> (left..right).map { j -> rows.getOrNull(r)?.getOrNull(j).orEmpty() } }
        val shown = (top..bottom).map { r -> (left..right).map { j -> SheetMeta.show(engine, r, j, meta.style(r, j)) } }
        val text = SheetTools.toTsv(shown)
        clipboard.setText(AnnotatedString(text))
        lastClip = SheetClip(text, raw, top, left)
        Notices.info(tr("{0} Zelle(n) kopiert", raw.size * (right - left + 1)))
    }

    fun paste() {
        val text = clipboard.getText()?.text.orEmpty()
        if (text.isEmpty()) return
        val clip = lastClip
        val block = if (clip != null && clip.text == text) {
            clip.cells.map { row -> row.map { SheetEngine.shift(it, top - clip.row, left - clip.col) } }
        } else SheetTools.parseClipboard(text)
        if (block.isEmpty()) return
        val next = SheetTools.pasteAt(rows, top, left, block)
        write(next)
        anchor = top to left
        sel = (top + block.size - 1) to (left + (block.maxOfOrNull { it.size } ?: 1) - 1)
        val v = next.getOrNull(sel.first)?.getOrNull(sel.second).orEmpty()
        bar = TextFieldValue(v, TextRange(v.length))
    }

    /** Rows sorted by column [col] (dates and numbers by value); formats move with their rows. */
    fun sort(col: Int, desc: Boolean) {
        setCell(sr, sc, bar.text)
        val order = (1 until rows.size).sortedWith { a, b ->
            val x = rows[a].getOrNull(col).orEmpty()
            val y = rows[b].getOrNull(col).orEmpty()
            val nx = SheetTools.sortKey(x)
            val ny = SheetTools.sortKey(y)
            val r = when {
                x.isBlank() != y.isBlank() -> if (x.isBlank()) 1 else -1
                nx != null && ny != null -> nx.compareTo(ny)
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (desc && !(x.isBlank() != y.isBlank())) -r else r
        }
        write(listOf(rows.first()) + order.map { rows[it] }, meta.remapRows(listOf(0) + order))
    }

    /** Numbers of column [col] below the head row (for Σ, sorting and the status bar). */
    fun numbers(col: Int) = (1 until rows.size).mapNotNull { r -> (engine.value(r, col) as? SheetEngine.Value.Num)?.takeIf { !it.date }?.v }

    fun csvSample(): String = rows.take(25).mapIndexed { r, row -> "${r + 1}: " + row.mapIndexed { j, _ -> engine.display(r, j) }.joinToString(" | ") }.joinToString("\n")

    val style = meta.style(sr, sc) ?: CellStyle()

    Column(Modifier.fillMaxSize()) {
        // ── Toolbar ──
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            var rowsMenu by remember { mutableStateOf(false) }
            Box {
                ToolLabel(tr("Zeilen & Spalten ▾")) { rowsMenu = true }
                DropdownMenu(expanded = rowsMenu, onDismissRequest = { rowsMenu = false }) {
                    fun insertRow(at: Int) {
                        rowsMenu = false
                        write(rows.toMutableList().apply { add(at, List(cols) { "" }) }, meta.remapRows((0 until at).toList() + listOf(null) + (at until rows.size)))
                    }
                    fun insertCol(at: Int) {
                        rowsMenu = false
                        write(rows.map { r -> r.toMutableList().apply { while (size < cols) add(""); add(at, "") } }, meta.remapCols((0 until at).toList() + listOf(null) + (at until cols)))
                    }
                    fun keepRows(kept: List<Int>) = write(kept.map { rows[it] }, meta.remapRows(kept))
                    DropdownMenuItem(text = { Text(tr("Zeile darüber einfügen")) }, onClick = { insertRow(sr) })
                    DropdownMenuItem(text = { Text(tr("Zeile darunter einfügen")) }, onClick = { insertRow(sr + 1) })
                    DropdownMenuItem(text = { Text(tr("Spalte links einfügen")) }, onClick = { insertCol(sc) })
                    DropdownMenuItem(text = { Text(tr("Spalte rechts einfügen")) }, onClick = { insertCol(sc + 1) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (bottom > top) tr("Zeilen {0}–{1} löschen", top + 1, bottom + 1) else tr("Zeile {0} löschen", sr + 1)) }, enabled = rows.size > bottom - top + 1,
                        onClick = { rowsMenu = false; anchor = null; keepRows(rows.indices.filter { it !in top..bottom }) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (right > left) tr("Spalten {0}–{1} löschen", SheetEngine.colName(left), SheetEngine.colName(right)) else tr("Spalte {0} löschen", SheetEngine.colName(sc))) },
                        enabled = cols > right - left + 1,
                        onClick = {
                            rowsMenu = false
                            anchor = null
                            val kept = (0 until cols).filter { it !in left..right }
                            write(rows.map { r -> kept.map { r.getOrNull(it).orEmpty() } }, meta.remapCols(kept))
                        },
                    )
                    DropdownMenuItem(text = { Text(tr("Leere Zeilen entfernen")) }, onClick = { rowsMenu = false; keepRows(rows.indices.filter { i -> i == 0 || rows[i].any { it.isNotBlank() } }) })
                    HorizontalDivider()
                    // Like dragging the fill handle in Excel: relative references move along, $-references stay.
                    if (isRange) {
                        DropdownMenuItem(
                            text = { Text(tr("{0} nach unten ausfüllen (Reihe)", rangeName)) }, enabled = bottom > top,
                            onClick = { rowsMenu = false; setCell(sr, sc, bar.text); write(SheetTools.fill(rows, top, left, bottom, right, down = true)) },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("{0} nach rechts ausfüllen (Reihe)", rangeName)) }, enabled = right > left,
                            onClick = { rowsMenu = false; setCell(sr, sc, bar.text); write(SheetTools.fill(rows, top, left, bottom, right, down = false)) },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(tr("{0} nach unten ausfüllen", SheetEngine.ref(sr, sc))) }, enabled = sr in 1 until rows.lastIndex,
                            onClick = {
                                rowsMenu = false
                                setCell(sr, sc, bar.text)
                                val source = bar.text
                                write(
                                    if (source.trimStart().startsWith("=")) rows.mapIndexed { r, row -> if (r <= sr) row else (0 until cols).map { j -> if (j == sc) SheetEngine.shift(source, r - sr, 0) else row.getOrNull(j).orEmpty() } }
                                    else SheetTools.fill(rows.mapIndexed { r, row -> if (r <= sr) row else (0 until cols).map { j -> if (j == sc) "" else row.getOrNull(j).orEmpty() } }, sr, sc, rows.lastIndex, sc, down = true),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("{0} nach rechts ausfüllen", SheetEngine.ref(sr, sc))) }, enabled = sc < cols - 1,
                            onClick = {
                                rowsMenu = false
                                setCell(sr, sc, bar.text)
                                val source = bar.text
                                write(
                                    if (source.trimStart().startsWith("=")) rows.mapIndexed { r, row -> if (r != sr) row else (0 until cols).map { j -> if (j > sc) SheetEngine.shift(source, 0, j - sc) else row.getOrNull(j).orEmpty() } }
                                    else SheetTools.fill(rows.mapIndexed { r, row -> if (r != sr) row else (0 until cols).map { j -> if (j > sc) "" else row.getOrNull(j).orEmpty() } }, sr, sc, sr, cols - 1, down = false),
                                )
                            },
                        )
                    }
                    DropdownMenuItem(text = { Text(tr("Duplikate entfernen")) }, onClick = {
                        rowsMenu = false
                        val before = rows.size
                        val kept = listOf(0) + (1 until rows.size).distinctBy { i -> rows[i].map { it.trim().lowercase() } }
                        keepRows(kept)
                        Notices.info(tr("{0} doppelte Zeile(n) entfernt", before - kept.size))
                    })
                    DropdownMenuItem(text = { Text(tr("Zeilen und Spalten tauschen")) }, onClick = {
                        rowsMenu = false
                        write((0 until cols).map { j -> rows.map { it.getOrNull(j).orEmpty() } }, meta.transposed())
                    })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text((if (meta.freeze) "✓ " else "") + tr("Kopfzeile fixieren")) },
                        onClick = { rowsMenu = false; write(rows, meta.copy(freeze = !meta.freeze)) },
                    )
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            ToolIcon(Icons.Outlined.ContentCopy, tr("Kopieren (als Tabelle für Excel und Word)")) { copy() }
            ToolIcon(Icons.Outlined.ContentPaste, tr("Einfügen (aus Excel, Word oder dem Web)")) { paste() }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            // ── Formats of the selected cells ──
            ToolIcon(Icons.Outlined.FormatBold, tr("Fett"), active = style.b) { restyle { it.copy(b = !style.b) } }
            ToolIcon(Icons.Outlined.FormatItalic, tr("Kursiv"), active = style.i) { restyle { it.copy(i = !style.i) } }
            var colorMenu by remember { mutableStateOf<String?>(null) }
            Box {
                Row {
                    ToolIcon(Icons.Outlined.FormatColorText, tr("Textfarbe"), active = style.color != null) { colorMenu = "text" }
                    ToolIcon(Icons.Outlined.FormatColorFill, tr("Füllfarbe"), active = style.fill != null) { colorMenu = "fill" }
                }
                DropdownMenu(expanded = colorMenu != null, onDismissRequest = { colorMenu = null }) {
                    val fill = colorMenu == "fill"
                    DropdownMenuItem(text = { Text(if (fill) tr("Keine Füllung") else tr("Automatisch")) }, onClick = { restyle { if (fill) it.copy(fill = null) else it.copy(color = null) }; colorMenu = null })
                    (if (fill) FILL_COLORS else TEXT_COLORS).forEach { (hex, label) ->
                        DropdownMenuItem(
                            text = { Text(tr(label)) },
                            leadingIcon = { Box(Modifier.size(18.dp).background(hexColor(hex) ?: Color.Gray, CircleShape).border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape)) },
                            onClick = { restyle { if (fill) it.copy(fill = hex) else it.copy(color = hex) }; colorMenu = null },
                        )
                    }
                }
            }
            var nfMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.Numbers, tr("Zahlenformat"), active = style.nf != null) { nfMenu = true }
                DropdownMenu(expanded = nfMenu, onDismissRequest = { nfMenu = false }) {
                    SheetMeta.NUMBER_FORMATS.forEach { (nf, label) ->
                        DropdownMenuItem(text = { Text((if (style.nf == nf) "✓ " else "") + label) }, onClick = { nfMenu = false; restyle { it.copy(nf = nf) } })
                    }
                }
            }
            var alignMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(
                    when (style.align) { "c" -> Icons.Outlined.FormatAlignCenter; "r" -> Icons.Outlined.FormatAlignRight; else -> Icons.Outlined.FormatAlignLeft },
                    tr("Ausrichtung"), active = style.align != null,
                ) { alignMenu = true }
                DropdownMenu(expanded = alignMenu, onDismissRequest = { alignMenu = false }) {
                    listOf(null to tr("Standard"), "l" to tr("Links"), "c" to tr("Mitte"), "r" to tr("Rechts")).forEach { (a, label) ->
                        DropdownMenuItem(text = { Text((if (style.align == a) "✓ " else "") + label) }, onClick = { alignMenu = false; restyle { it.copy(align = a) } })
                    }
                }
            }
            ToolIcon(Icons.Outlined.FormatPaint, tr("Bedingte Formatierung"), active = meta.rules.isNotEmpty()) { setCell(sr, sc, bar.text); condDialog = true }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            var sortMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.AutoMirrored.Outlined.Sort, tr("Sortieren")) { sortMenu = true }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(text = { Text(tr("Nach Spalte {0} aufsteigend", SheetEngine.colName(sc))) }, onClick = { sortMenu = false; sort(sc, false) })
                    DropdownMenuItem(text = { Text(tr("Nach Spalte {0} absteigend", SheetEngine.colName(sc))) }, onClick = { sortMenu = false; sort(sc, true) })
                }
            }
            var filterMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.FilterList, tr("Filtern"), active = filterOpen || filter.isNotBlank() || colFilters.isNotEmpty()) { filterMenu = true }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    DropdownMenuItem(text = { Text(tr("Spalte {0} filtern …", SheetEngine.colName(sc))) }, onClick = { filterMenu = false; filterDialog = sc })
                    DropdownMenuItem(text = { Text(if (filterOpen) tr("Suchfilter schließen") else tr("Zeilen nach Text durchsuchen …")) }, onClick = {
                        filterMenu = false
                        filterOpen = !filterOpen
                        if (!filterOpen) filter = ""
                    })
                    DropdownMenuItem(text = { Text(tr("Alle Filter aufheben")) }, enabled = colFilters.isNotEmpty() || filter.isNotBlank(), onClick = {
                        filterMenu = false
                        colFilters = emptyMap()
                        filter = ""
                        filterOpen = false
                    })
                }
            }
            var fx by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.Functions, tr("Funktion einfügen")) { fx = true }
                DropdownMenu(expanded = fx, onDismissRequest = { fx = false }) {
                    DropdownMenuItem(text = { Text(tr("Σ Summe der Spalte darunter einfügen")) }, onClick = {
                        fx = false
                        val last = rows.indexOfLast { r -> r.getOrNull(sc)?.isNotBlank() == true }.coerceAtLeast(1)
                        val formula = "=SUMME(${SheetEngine.ref(1, sc)}:${SheetEngine.ref(last, sc)})"
                        setCell(last + 1, sc, formula)
                        anchor = null
                        sel = (last + 1) to sc
                    })
                    SheetEngine.FUNCTIONS.forEach { (name, hint) ->
                        DropdownMenuItem(text = { Column { Text(name); Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, onClick = {
                            fx = false
                            val t = "=$name()"
                            bar = TextFieldValue(t, TextRange(t.length - 1))
                            runCatching { barFocus.requestFocus() }
                        })
                    }
                }
            }
            var chartMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.BarChart, tr("Diagramm aus Auswahl")) { chartMenu = true }
                DropdownMenu(expanded = chartMenu, onDismissRequest = { chartMenu = false }) {
                    if (!isRange) Text(
                        tr("Zuerst einen Bereich markieren (lange tippen oder Umschalt + Klick), z. B. Namen und Werte."),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(260.dp),
                    )
                    listOf("bar" to tr("Säulendiagramm"), "line" to tr("Liniendiagramm"), "pie" to tr("Kreisdiagramm")).forEach { (type, label) ->
                        DropdownMenuItem(text = { Text(tr("{0} aus {1}", label, rangeName)) }, enabled = isRange, onClick = {
                            chartMenu = false
                            setCell(sr, sc, bar.text)
                            val title = rows.getOrNull(top)?.getOrNull(if (right > left) left + 1 else left).orEmpty().takeIf { top == 0 }.orEmpty()
                            val chart = SheetChart(type, rangeName, title)
                            if (SheetTools.chartSpec(chart, engine) == null) Notices.info(tr("Im Bereich {0} stehen keine Zahlen für ein Diagramm", rangeName))
                            else write(rows, meta.copy(charts = meta.charts + chart))
                        })
                    }
                }
            }
            var ai by remember { mutableStateOf(false) }
            var ask by remember { mutableStateOf<SheetAi?>(null) }
            Box {
                ToolIcon(Icons.Outlined.AutoAwesome, tr("KI")) { ai = true }
                DropdownMenu(expanded = ai, onDismissRequest = { ai = false }) {
                    DropdownMenuItem(text = { Text(tr("Formel für {0} beschreiben …", SheetEngine.ref(sr, sc))) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = { ai = false; ask = SheetAi.FORMULA })
                    DropdownMenuItem(text = { Text(tr("Spalte {0} mit KI ausfüllen …", SheetEngine.colName(sc))) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = { ai = false; ask = SheetAi.FILL })
                    DropdownMenuItem(text = { Text(tr("Formel in {0} erklären", SheetEngine.ref(sr, sc))) }, enabled = bar.text.startsWith("="), onClick = {
                        ai = false
                        aiMode = SheetAi.EXPLAIN
                        inlineAi.start("sheet", doc, bar.text, tr("Erklärung"), "Erkläre kurz und verständlich, was diese Excel-Formel berechnet (2–3 Sätze). Tabelle:\n${csvSample()}")
                    })
                }
            }
            ask?.let { mode ->
                TextPrompt(
                    if (mode == SheetAi.FORMULA) tr("Was soll in {0} berechnet werden?", SheetEngine.ref(sr, sc)) else tr("Womit soll Spalte {0} gefüllt werden?", SheetEngine.colName(sc)),
                    "", onDismiss = { ask = null },
                ) { text ->
                    ask = null
                    aiMode = mode
                    if (mode == SheetAi.FORMULA) {
                        inlineAi.start(
                            "sheet", doc, "", tr("Formel"),
                            "Gib genau eine Formel für Zelle ${SheetEngine.ref(sr, sc)} zurück (eine Zeile, beginnt mit =, deutsche Funktionsnamen wie SUMME, MITTELWERT, WENN, " +
                                "ANZAHL, RUNDEN; Argumente mit ; trennen; Zellbezüge wie B2:B9). Aufgabe: $text\nTabelle (Zeilennummer: Werte, Zeile 1 = Kopf, Spalten A, B, C …):\n${csvSample()}",
                        )
                    } else {
                        inlineAi.start(
                            "sheet", doc, "", tr("Spalte füllen"),
                            "Fülle Spalte ${SheetEngine.colName(sc)} („${rows[0].getOrNull(sc).orEmpty()}“) für die Datenzeilen 2 bis ${rows.size}: $text\n" +
                                "Gib genau ${rows.size - 1} Zeilen zurück, je Datenzeile eine, nur den Wert (ohne Nummer, ohne Aufzählungszeichen; leere Zeile für leer).\n" +
                                "Tabelle (Zeilennummer: Werte):\n${rows.mapIndexed { r, row -> "${r + 1}: " + row.mapIndexed { j, _ -> engine.display(r, j) }.joinToString(" | ") }.take(200).joinToString("\n")}",
                        )
                    }
                }
            }
            ToolIcon(Icons.Outlined.AddComment, tr("Kommentieren")) { onComment("${SheetEngine.ref(sr, sc)}: ${engine.display(sr, sc)}") }
        }
        // ── Formula bar ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Name box: shows the cell or range; typing "B2:D9" selects it.
            var nameBox by remember(rangeName) { mutableStateOf(rangeName) }
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                BasicTextField(
                    nameBox, { nameBox = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == ':' || ch == '$' } }, singleLine = true,
                    textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        SheetEngine.parseRange(nameBox)?.let(::selectRange)
                    }),
                    modifier = Modifier.width(76.dp).padding(8.dp).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter) {
                            SheetEngine.parseRange(nameBox)?.let(::selectRange) ?: run { nameBox = rangeName }
                            true
                        } else false
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(tr("fx"), style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Serif), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = LocalChatLook.current.card, modifier = Modifier.weight(1f)) {
                BasicTextField(
                    bar, { bar = it }, singleLine = true, enabled = !writing,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = if (bar.text.startsWith("=")) FontFamily.Monospace else FontFamily.Default),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { select(sr + 1, sc) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).focusRequester(barFocus).onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val ctrl = e.isCmd || e.isMetaPressed
                        val formula = bar.text.startsWith("=")
                        when {
                            // Ranges are copied and pasted as cells; within one cell the text field does it.
                            ctrl && e.key == Key.C && isRange -> { copy(); true }
                            ctrl && e.key == Key.V && clipboard.getText()?.text.orEmpty().let { '\t' in it || '\n' in it.trimEnd() } -> { paste(); true }
                            ctrl && e.key == Key.B -> { restyle { it.copy(b = !style.b) }; true }
                            ctrl && e.key == Key.I -> { restyle { it.copy(i = !style.i) }; true }
                            e.key == Key.Enter -> { select(if (e.isShiftPressed) sr - 1 else sr + 1, sc); true }
                            e.key == Key.Tab -> { select(sr, if (e.isShiftPressed) (sc - 1).coerceAtLeast(0) else sc + 1); true }
                            e.key == Key.Escape -> { val v = rows.getOrNull(sr)?.getOrNull(sc).orEmpty(); bar = TextFieldValue(v, TextRange(v.length)); anchor = null; true }
                            // Arrow keys move between cells like in Excel (not while a formula is being typed); with Shift they mark a range.
                            e.key == Key.DirectionUp && !formula -> { select((sr - 1).coerceAtLeast(0), sc, extend = e.isShiftPressed); true }
                            e.key == Key.DirectionDown && !formula -> { select(sr + 1, sc, extend = e.isShiftPressed); true }
                            e.key == Key.DirectionLeft && e.isShiftPressed && !formula -> { select(sr, (sc - 1).coerceAtLeast(0), extend = true); true }
                            e.key == Key.DirectionRight && e.isShiftPressed && !formula -> { select(sr, sc + 1, extend = true); true }
                            else -> false
                        }
                    },
                    decorationBox = { inner ->
                        if (bar.text.isEmpty()) Text(tr("Wert oder Formel, z. B. =SUMME(B2:B9)"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        inner()
                    },
                )
            }
        }
        inlineAi.state?.takeIf { it.first == "sheet" }?.second?.let { s ->
            Box(Modifier.padding(horizontal = 10.dp)) {
                AiSuggestionCard(
                    s,
                    onAccept = {
                        when (aiMode) {
                            SheetAi.FORMULA -> {
                                val f = s.text.lines().map { it.trim().trim('`') }.firstOrNull { it.startsWith("=") } ?: ("=" + s.text.trim())
                                setCell(sr, sc, f)
                                bar = TextFieldValue(f, TextRange(f.length))
                            }
                            SheetAi.FILL -> {
                                val values = s.text.lines().map { it.trim().replace(Regex("^\\d+[:.)]\\s*"), "") }
                                val width = maxOf(cols, sc + 1)
                                write(rows.mapIndexed { r, row -> if (r == 0) row else (0 until width).map { j -> if (j == sc) values.getOrElse(r - 1) { row.getOrNull(j).orEmpty() } else row.getOrNull(j).orEmpty() } })
                            }
                            SheetAi.EXPLAIN -> {}
                        }
                        inlineAi.clear()
                    },
                    onDiscard = { inlineAi.clear() },
                    onRetry = { inlineAi.retry(doc) },
                )
            }
        }
        if (filterOpen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    filter, { filter = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text(tr("Zeilen filtern: nur Zeilen mit diesem Text zeigen")) },
                    leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                )
                Text(tr("{0} von {1}", visibleRows.size - 1, rows.size - 1), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        if (colFilters.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Gefiltert nach Spalte {0} · {1} von {2} Zeilen", colFilters.keys.sorted().joinToString(", ") { SheetEngine.colName(it) }, visibleRows.size - 1, rows.size - 1),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { colFilters = emptyMap() }) { Text(tr("Filter aufheben")) }
            }
        }
        val defaultText = MaterialTheme.colorScheme.onSurface
        val errorText = MaterialTheme.colorScheme.error
        val headBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        val cellBg = LocalChatLook.current.card
        val rangeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        val rowView: @Composable (Int) -> Unit = { r ->
            Row(Modifier.horizontalScroll(hScroll)) {
                HeadCell(if (r < rows.size) "${r + 1}" else "+", HEAD_W, r in top..bottom) { select(r, sc, extend = shiftDown) }
                if (r < rows.size) (0 until cols).forEach { j ->
                    val selected = r == sr && j == sc
                    val inRange = isRange && r in top..bottom && j in left..right
                    val st = meta.style(r, j)
                    val cond = paint[r to j]
                    val value = engine.value(r, j)
                    val shown = if (selected && !bar.text.startsWith("=")) bar.text else SheetMeta.show(engine, r, j, st)
                    val fill = hexColor(cond?.fill ?: st?.fill)
                    val textColor = hexColor(cond?.color ?: st?.color)
                        ?: if (value is SheetEngine.Value.Err) errorText else if (fill != null) Color(0xFF1F1F1F) else defaultText
                    Box(
                        Modifier.width(CELL_W).height(CELL_H)
                            .background(fill ?: if (r == 0) headBg else cellBg)
                            .then(if (inRange) Modifier.background(rangeBg) else Modifier)
                            .border(if (selected) 2.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            .combinedClickable(
                                enabled = !writing,
                                // Long press (or Shift + click) marks the range from the selected cell to this one.
                                onLongClick = { select(r, j, extend = true) },
                                onClick = { if (selected && !shiftDown) runCatching { barFocus.requestFocus() } else select(r, j, extend = shiftDown) },
                            )
                            .padding(horizontal = 6.dp),
                        contentAlignment = when (st?.align) {
                            "l" -> Alignment.CenterStart
                            "c" -> Alignment.Center
                            "r" -> Alignment.CenterEnd
                            else -> if (value is SheetEngine.Value.Num && r > 0) Alignment.CenterEnd else Alignment.CenterStart
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                shown, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (r == 0 || st?.b == true) FontWeight.SemiBold else FontWeight.Normal,
                                    fontStyle = if (st?.i == true) FontStyle.Italic else FontStyle.Normal,
                                ),
                                color = textColor,
                            )
                            // Filter button in the head row, like Excel's AutoFilter.
                            if (r == 0 && rows.size > 1) {
                                Text(
                                    if (j in colFilters) "▼" else "▾",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (j in colFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp).clickable { filterDialog = j }.padding(horizontal = 4.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        // ── Grid ──
        Box(
            Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).pointerInput(Unit) {
                // Remember whether Shift is held when a cell is clicked (to mark a range like in Excel).
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) shiftDown = e.keyboardModifiers.isShiftPressed
                    }
                }
            },
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                // Column letters (and the head row, if fixed) stay at the top while scrolling (like „Fenster fixieren“ in Excel).
                stickyHeader(key = "head") {
                    Column {
                        Row(Modifier.horizontalScroll(hScroll)) {
                            HeadCell("", HEAD_W, false) {}
                            (0 until cols + 1).forEach { j -> HeadCell(if (j < cols) SheetEngine.colName(j) else "+", CELL_W, j in left..right) { if (j < cols) select(sr, j, extend = shiftDown) else select(sr, cols) } }
                        }
                        if (meta.freeze) rowView(0)
                    }
                }
                if (!meta.freeze) item(key = "r0") { rowView(0) }
                items(visibleRows.filter { it != 0 } + rows.size, key = { "r$it" }) { r -> rowView(r) }
            }
        }
        // ── Charts of the sheet ──
        if (meta.charts.isNotEmpty()) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(max = 260.dp).horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meta.charts.forEachIndexed { i, chart ->
                    val spec = remember(chart, engine) { SheetTools.chartSpec(chart, engine) }
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                chart.title.ifBlank { "Diagramm" } + " · " + chart.range, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { write(rows, meta.copy(charts = meta.charts.filterIndexed { k, _ -> k != i })) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = tr("Diagramm entfernen"), modifier = Modifier.size(16.dp))
                            }
                        }
                        if (spec != null) ChartView(spec, Modifier.width(360.dp)) else Text(tr("Keine Zahlen im Bereich"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        // ── Status bar (like Excel): sum, average and count of the selected range or column ──
        val nums = if (isRange) (top..bottom).flatMap { r -> (left..right).mapNotNull { j -> (engine.value(r, j) as? SheetEngine.Value.Num)?.takeIf { !it.date }?.v } } else numbers(sc)
        val where = if (isRange) rangeName else tr("Spalte {0}", SheetEngine.colName(sc))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (nums.isEmpty()) tr("{0} · {1} Zeilen · Formeln mit = beginnen · Bereich: lange tippen oder Umschalt + Klick", where, rows.size - 1)
                else tr("{0} · Summe {1} · Mittelwert {2} · Anzahl {3}", where, SheetEngine.short(nums.sum()), SheetEngine.short(nums.average()), nums.size),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    filterDialog?.let { col ->
        ColumnFilterDialog(
            title = rows.firstOrNull()?.getOrNull(col).orEmpty().ifBlank { tr("Spalte {0}", SheetEngine.colName(col)) },
            values = (1 until rows.size).map { engine.display(it, col) }.distinct().sortedWith(compareBy({ SheetEngine.numberOrDate(it) == null }, { SheetEngine.numberOrDate(it) ?: 0.0 }, { it.lowercase() })),
            current = colFilters[col] ?: ColFilter(),
            onDismiss = { filterDialog = null },
            onApply = { f ->
                colFilters = if (f == null || f == ColFilter()) colFilters - col else colFilters + (col to f)
                filterDialog = null
            },
        )
    }
    if (condDialog) {
        ConditionalDialog(
            rules = meta.rules, range = if (isRange) rangeName else SheetEngine.rangeRef(1.coerceAtMost(rows.lastIndex), sc, rows.lastIndex, sc),
            onDismiss = { condDialog = false },
            onRules = { write(rows, meta.copy(rules = it)) },
        )
    }
}

/** Which rows to show for one column: chosen values, text contains, numbers above or below. */
@Composable
private fun ColumnFilterDialog(title: String, values: List<String>, current: ColFilter, onDismiss: () -> Unit, onApply: (ColFilter?) -> Unit) {
    var chosen by remember { mutableStateOf(current.values ?: values.toSet()) }
    var contains by remember { mutableStateOf(current.contains) }
    var above by remember { mutableStateOf(current.above) }
    var below by remember { mutableStateOf(current.below) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Filter: {0}", title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(contains, { contains = it }, singleLine = true, label = { Text(tr("Text enthält")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(above, { above = it }, singleLine = true, label = { Text(tr("Zahl größer als")) }, modifier = Modifier.weight(1f))
                    OutlinedTextField(below, { below = it }, singleLine = true, label = { Text(tr("kleiner als")) }, modifier = Modifier.weight(1f))
                }
                Text(tr("Nur diese Werte zeigen"), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosen = if (chosen.size == values.size) emptySet() else values.toSet() }) {
                    Checkbox(chosen.size == values.size, { chosen = if (it) values.toSet() else emptySet() })
                    Text(tr("(Alle auswählen)"))
                }
                values.take(300).forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosen = if (v in chosen) chosen - v else chosen + v }) {
                        Checkbox(v in chosen, { chosen = if (it) chosen + v else chosen - v })
                        Text(v.ifEmpty { "(leer)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(ColFilter(chosen.takeIf { it.size < values.size }, contains, above, below)) }) { Text(tr("Anwenden")) }
        },
        dismissButton = { TextButton(onClick = { onApply(null) }) { Text(tr("Filter entfernen")) } },
    )
}

/** Conditional formatting: the rules of the sheet and a new one (e.g. grades 5 to 6 red). */
@Composable
private fun ConditionalDialog(rules: List<CondRule>, range: String, onDismiss: () -> Unit, onRules: (List<CondRule>) -> Unit) {
    var list by remember { mutableStateOf(rules) }
    var area by remember { mutableStateOf(range) }
    var op by remember { mutableStateOf("between") }
    var a by remember { mutableStateOf("5") }
    var b by remember { mutableStateOf("6") }
    var color by remember { mutableStateOf(0) }
    val ops = listOf("lt" to tr("Wert <"), "gt" to tr("Wert >"), "between" to "zwischen", "eq" to "gleich", "contains" to tr("Text enthält"), "scale" to tr("Farbskala"))
    val colors = listOf(Triple(tr("Rot"), "#FFC7CE", "#9C0006"), Triple(tr("Gelb"), "#FFEB9C", "#9C5700"), Triple(tr("Grün"), "#C6EFCE", "#006100"), Triple(tr("Blau"), "#DDEBF7", "#1F4E79"))
    val scales = listOf(Triple(tr("Rot → Grün"), "#F8696B", "#63BE7B"), Triple(tr("Grün → Rot"), "#63BE7B", "#F8696B"), Triple(tr("Weiß → Blau"), "#FFFFFF", "#5B9BD5"))
    fun describe(r: CondRule) = r.range + ": " + when (r.op) {
        "lt" -> tr("Wert < {0}", r.a)
        "gt" -> tr("Wert > {0}", r.a)
        "between" -> tr("zwischen {0} und {1}", r.a, r.b)
        "eq" -> "gleich ${r.a}"
        "contains" -> tr("enthält „{0}“", r.a)
        else -> tr("Farbskala")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Bedingte Formatierung")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (list.isEmpty()) Text(tr("Noch keine Regeln. Beispiel: Noten zwischen 5 und 6 rot."), style = MaterialTheme.typography.bodySmall)
                list.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).background(hexColor(r.fill) ?: Color.Gray, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(describe(r), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { list = list.filterIndexed { k, _ -> k != i }; onRules(list) }) { Icon(Icons.Outlined.Close, contentDescription = tr("Regel löschen")) }
                    }
                }
                HorizontalDivider()
                Text(tr("Neue Regel"), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(area, { area = it.uppercase() }, singleLine = true, label = { Text(tr("Bereich, z. B. C2:C30")) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ops.forEach { (k, label) -> FilterChip(op == k, { op = k; color = 0 }, label = { Text(label) }) }
                }
                if (op != "scale") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(a, { a = it }, singleLine = true, label = { Text(if (op == "contains") tr("Text") else if (op == "between") tr("von") else tr("Wert")) }, modifier = Modifier.weight(1f))
                        if (op == "between") OutlinedTextField(b, { b = it }, singleLine = true, label = { Text(tr("bis")) }, modifier = Modifier.weight(1f))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (op == "scale") scales else colors).forEachIndexed { i, (label, fill, _) ->
                        FilterChip(color == i, { color = i }, label = { Text(label) }, leadingIcon = { Box(Modifier.size(14.dp).background(hexColor(fill) ?: Color.Gray, CircleShape)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = SheetEngine.parseRange(area) != null && (op == "scale" || a.isNotBlank()), onClick = {
                val rule = if (op == "scale") scales[color].let { (_, lo, hi) -> CondRule(area.trim(), "scale", fill = lo, color = null, fill2 = hi) }
                else colors[color.coerceIn(0, colors.lastIndex)].let { (_, fill, text) -> CondRule(area.trim(), op, a.trim(), b.trim(), fill, text) }
                list = list + rule
                onRules(list)
            }) { Text(tr("Regel hinzufügen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Fertig")) } },
    )
}

@Composable
private fun HeadCell(text: String, width: androidx.compose.ui.unit.Dp, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(width).height(CELL_H)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
