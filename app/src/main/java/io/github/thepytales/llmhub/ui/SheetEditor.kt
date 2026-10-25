package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.XlsxWriter

private val CELL_W = 118.dp
private val CELL_H = 34.dp
private val HEAD_W = 44.dp

/** What a ✨ suggestion of the table is for. */
private enum class SheetAi { FORMULA, FILL, EXPLAIN }

/**
 * The table editor (like Excel): cells with formulas, formula bar, row and column headers, sorting, Σ, function
 * list, status bar with sum and average, and ✨ for formulas and filling columns. Each tab is one sheet.
 */
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
    // Rows as edited here; written back after each change.
    var rows by remember(doc.id, tabId) { mutableStateOf(saved) }
    LaunchedEffect(saved) { if (saved != rows) rows = saved }
    val cols = rows.maxOf { it.size }.coerceAtLeast(1)
    var sel by remember(doc.id, tabId) { mutableStateOf(1.coerceAtMost(rows.lastIndex) to 0) }
    val (sr, sc) = sel.first.coerceIn(0, rows.lastIndex) to sel.second.coerceIn(0, cols - 1)
    var bar by remember(doc.id, tabId) { mutableStateOf(TextFieldValue(rows.getOrNull(sr)?.getOrNull(sc).orEmpty())) }
    var aiMode by remember { mutableStateOf(SheetAi.FORMULA) }
    val barFocus = remember { FocusRequester() }
    val engine = remember(rows) { SheetEngine.Sheet(rows) }
    val hScroll = rememberScrollState()

    fun write(next: List<List<String>>) {
        rows = next
        onMarkdown(SheetEngine.markdown(next))
    }

    fun setCell(r: Int, col: Int, v: String) {
        if (rows.getOrNull(r)?.getOrNull(col).orEmpty() == v) return
        val width = maxOf(cols, col + 1)
        val next = (0 until maxOf(rows.size, r + 1)).map { i -> (0 until width).map { j -> if (i == r && j == col) v else rows.getOrNull(i)?.getOrNull(j).orEmpty() } }
        write(next)
    }

    fun select(r: Int, col: Int) {
        // The formula bar's text belongs to the cell that was selected until now.
        setCell(sr, sc, bar.text)
        val rr = r.coerceAtLeast(0)
        val cc = col.coerceAtLeast(0)
        val grown = if (rr >= rows.size || cc >= cols) (0 until maxOf(rows.size, rr + 1)).map { i -> (0 until maxOf(cols, cc + 1)).map { j -> rows.getOrNull(i)?.getOrNull(j).orEmpty() } } else rows
        if (grown !== rows) write(grown)
        sel = rr to cc
        val v = grown.getOrNull(rr)?.getOrNull(cc).orEmpty()
        bar = TextFieldValue(v, TextRange(v.length))
    }

    LaunchedEffect(sr, sc, saved) {
        val v = rows.getOrNull(sr)?.getOrNull(sc).orEmpty()
        if (v != bar.text) bar = TextFieldValue(v, TextRange(v.length))
    }

    /** Numbers of column [col] below the head row (for Σ, sorting and the status bar). */
    fun numbers(col: Int) = (1 until rows.size).mapNotNull { r -> (engine.value(r, col) as? SheetEngine.Value.Num)?.v }

    fun csvSample(): String = rows.take(25).mapIndexed { r, row -> "${r + 1}: " + row.mapIndexed { j, _ -> engine.display(r, j) }.joinToString(" | ") }.joinToString("\n")

    Column(Modifier.fillMaxSize()) {
        // ── Toolbar ──
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            var rowsMenu by remember { mutableStateOf(false) }
            Box {
                ToolLabel("Zeilen & Spalten ▾") { rowsMenu = true }
                DropdownMenu(expanded = rowsMenu, onDismissRequest = { rowsMenu = false }) {
                    DropdownMenuItem(text = { Text("Zeile darüber einfügen") }, onClick = { rowsMenu = false; write(rows.toMutableList().apply { add(sr, List(cols) { "" }) }) })
                    DropdownMenuItem(text = { Text("Zeile darunter einfügen") }, onClick = { rowsMenu = false; write(rows.toMutableList().apply { add(sr + 1, List(cols) { "" }) }) })
                    DropdownMenuItem(text = { Text("Spalte links einfügen") }, onClick = { rowsMenu = false; write(rows.map { r -> r.toMutableList().apply { while (size < cols) add(""); add(sc, "") } }) })
                    DropdownMenuItem(text = { Text("Spalte rechts einfügen") }, onClick = { rowsMenu = false; write(rows.map { r -> r.toMutableList().apply { while (size < cols) add(""); add(sc + 1, "") } }) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Zeile ${sr + 1} löschen") }, enabled = rows.size > 1, onClick = { rowsMenu = false; write(rows.filterIndexed { i, _ -> i != sr }) })
                    DropdownMenuItem(text = { Text("Spalte ${SheetEngine.colName(sc)} löschen") }, enabled = cols > 1, onClick = { rowsMenu = false; write(rows.map { r -> r.filterIndexed { j, _ -> j != sc } }) })
                    DropdownMenuItem(text = { Text("Leere Zeilen entfernen") }, onClick = { rowsMenu = false; write(rows.filterIndexed { i, r -> i == 0 || r.any { it.isNotBlank() } }) })
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            var sortMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.AutoMirrored.Outlined.Sort, "Sortieren") { sortMenu = true }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    fun sort(desc: Boolean) {
                        sortMenu = false
                        val head = rows.first()
                        val body = rows.drop(1).sortedWith { a, b ->
                            val x = a.getOrNull(sc).orEmpty()
                            val y = b.getOrNull(sc).orEmpty()
                            val nx = XlsxWriter.number(x)
                            val ny = XlsxWriter.number(y)
                            val r = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
                            if (desc) -r else r
                        }
                        write(listOf(head) + body)
                    }
                    DropdownMenuItem(text = { Text("Nach Spalte ${SheetEngine.colName(sc)} aufsteigend") }, onClick = { sort(false) })
                    DropdownMenuItem(text = { Text("Nach Spalte ${SheetEngine.colName(sc)} absteigend") }, onClick = { sort(true) })
                }
            }
            var fx by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.Functions, "Funktion einfügen") { fx = true }
                DropdownMenu(expanded = fx, onDismissRequest = { fx = false }) {
                    DropdownMenuItem(text = { Text("Σ Summe der Spalte darunter einfügen") }, onClick = {
                        fx = false
                        val last = rows.indexOfLast { r -> r.getOrNull(sc)?.isNotBlank() == true }.coerceAtLeast(1)
                        val formula = "=SUMME(${SheetEngine.ref(1, sc)}:${SheetEngine.ref(last, sc)})"
                        setCell(last + 1, sc, formula)
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
            var ai by remember { mutableStateOf(false) }
            var ask by remember { mutableStateOf<SheetAi?>(null) }
            Box {
                ToolIcon(Icons.Outlined.AutoAwesome, "KI") { ai = true }
                DropdownMenu(expanded = ai, onDismissRequest = { ai = false }) {
                    DropdownMenuItem(text = { Text("Formel für ${SheetEngine.ref(sr, sc)} beschreiben …") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = { ai = false; ask = SheetAi.FORMULA })
                    DropdownMenuItem(text = { Text("Spalte ${SheetEngine.colName(sc)} mit KI ausfüllen …") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = { ai = false; ask = SheetAi.FILL })
                    DropdownMenuItem(text = { Text("Formel in ${SheetEngine.ref(sr, sc)} erklären") }, enabled = bar.text.startsWith("="), onClick = {
                        ai = false
                        aiMode = SheetAi.EXPLAIN
                        inlineAi.start("sheet", doc, bar.text, "Erklärung", "Erkläre kurz und verständlich, was diese Excel-Formel berechnet (2–3 Sätze). Tabelle:\n${csvSample()}")
                    })
                }
            }
            ask?.let { mode ->
                TextPrompt(
                    if (mode == SheetAi.FORMULA) "Was soll in ${SheetEngine.ref(sr, sc)} berechnet werden?" else "Womit soll Spalte ${SheetEngine.colName(sc)} gefüllt werden?",
                    "", onDismiss = { ask = null },
                ) { text ->
                    ask = null
                    aiMode = mode
                    if (mode == SheetAi.FORMULA) {
                        inlineAi.start(
                            "sheet", doc, "", "Formel",
                            "Gib genau eine Formel für Zelle ${SheetEngine.ref(sr, sc)} zurück (eine Zeile, beginnt mit =, deutsche Funktionsnamen wie SUMME, MITTELWERT, WENN, " +
                                "ANZAHL, RUNDEN; Argumente mit ; trennen; Zellbezüge wie B2:B9). Aufgabe: $text\nTabelle (Zeilennummer: Werte, Zeile 1 = Kopf, Spalten A, B, C …):\n${csvSample()}",
                        )
                    } else {
                        inlineAi.start(
                            "sheet", doc, "", "Spalte füllen",
                            "Fülle Spalte ${SheetEngine.colName(sc)} („${rows[0].getOrNull(sc).orEmpty()}“) für die Datenzeilen 2 bis ${rows.size}: $text\n" +
                                "Gib genau ${rows.size - 1} Zeilen zurück, je Datenzeile eine, nur den Wert (ohne Nummer, ohne Aufzählungszeichen; leere Zeile für leer).\n" +
                                "Tabelle (Zeilennummer: Werte):\n${rows.mapIndexed { r, row -> "${r + 1}: " + row.mapIndexed { j, _ -> engine.display(r, j) }.joinToString(" | ") }.take(200).joinToString("\n")}",
                        )
                    }
                }
            }
            ToolIcon(Icons.Outlined.AddComment, "Kommentieren") { onComment("${SheetEngine.ref(sr, sc)}: ${engine.display(sr, sc)}") }
        }
        // ── Formula bar ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(SheetEngine.ref(sr, sc), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp).padding(8.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(6.dp))
            Text("fx", style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Serif), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        when (e.key) {
                            Key.Enter -> { select(if (e.isShiftPressed) sr - 1 else sr + 1, sc); true }
                            Key.Tab -> { select(sr, if (e.isShiftPressed) (sc - 1).coerceAtLeast(0) else sc + 1); true }
                            Key.Escape -> { val v = rows.getOrNull(sr)?.getOrNull(sc).orEmpty(); bar = TextFieldValue(v, TextRange(v.length)); true }
                            else -> false
                        }
                    },
                    decorationBox = { inner ->
                        if (bar.text.isEmpty()) Text("Wert oder Formel, z. B. =SUMME(B2:B9)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
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
        HorizontalDivider()
        // ── Grid ──
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "head") {
                    Row(Modifier.horizontalScroll(hScroll)) {
                        HeadCell("", HEAD_W, false) {}
                        (0 until cols + 1).forEach { j -> HeadCell(if (j < cols) SheetEngine.colName(j) else "+", CELL_W, j == sc) { if (j < cols) select(sr, j) else select(sr, cols) } }
                    }
                }
                items(rows.size + 1, key = { "r$it" }) { r ->
                    Row(Modifier.horizontalScroll(hScroll)) {
                        HeadCell(if (r < rows.size) "${r + 1}" else "+", HEAD_W, r == sr) { select(r, sc) }
                        if (r < rows.size) (0 until cols).forEach { j ->
                            val selected = r == sr && j == sc
                            val shown = if (selected && !bar.text.startsWith("=")) bar.text else engine.display(r, j)
                            val value = engine.value(r, j)
                            Box(
                                Modifier.width(CELL_W).height(CELL_H)
                                    .background(if (r == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else LocalChatLook.current.card)
                                    .border(if (selected) 2.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                    .clickable(enabled = !writing) { if (selected) runCatching { barFocus.requestFocus() } else select(r, j) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = if (value is SheetEngine.Value.Num && r > 0) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                Text(
                                    shown, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal),
                                    color = if (value is SheetEngine.Value.Err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        // ── Status bar (like Excel): sum, average and count of the selected column ──
        val nums = numbers(sc)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (nums.isEmpty()) "Spalte ${SheetEngine.colName(sc)} · ${rows.size - 1} Zeilen · Formeln mit = beginnen"
                else "Spalte ${SheetEngine.colName(sc)} · Summe ${SheetEngine.short(nums.sum())} · Mittelwert ${SheetEngine.short(nums.average())} · Anzahl ${nums.size}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
