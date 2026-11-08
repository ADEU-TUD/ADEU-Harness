package io.github.thepytales.llmhub.creative

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.docs.CellRich
import io.github.thepytales.llmhub.docs.text.CharFormat
import io.github.thepytales.llmhub.ui.tr

/**
 * Formatting inside a text box of the shared canvas (AP-50, text core AP-43): the box keeps its text as inline Markdown
 * like a cell of a sheet ([CellRich]: `**fett**`, `*kursiv*`, `<u>…</u>`, `<span style="color:#C00000">…</span>`), one
 * line at a time – Markdown marks do not span lines, and a text box is a list of paragraphs in PowerPoint as well.
 * Gestalten and the slides draw, type and export their text boxes with these functions, so a box looks the same in
 * the editor, the PNG, the PDF and PowerPoint.
 */
object FieldRich {
    /** The visible text of a box and one format per character (line breaks are plain). */
    class Parsed(val plain: String, val chars: List<CharFormat>) {
        val rich: Boolean get() = chars.any { it != CharFormat.PLAIN }
    }

    /** [raw] of a box; plain boxes ([rich] false) keep every character as it is. */
    fun parse(raw: String, rich: Boolean = true): Parsed {
        if (!rich) return Parsed(raw, List(raw.length) { CharFormat.PLAIN })
        val plain = StringBuilder()
        val chars = ArrayList<CharFormat>(raw.length)
        raw.split('\n').forEachIndexed { i, line ->
            if (i > 0) { plain.append('\n'); chars += CharFormat.PLAIN }
            val p = CellRich.parse(line)
            plain.append(p.plain)
            chars += p.chars
        }
        return Parsed(plain.toString(), chars)
    }

    /** The visible text of [raw] (search, accessibility, the size a box needs). */
    fun plain(raw: String, rich: Boolean): String = if (!rich) raw else parse(raw).plain

    /** [plain] with [chars] as inline Markdown, line by line (runs of equal format as the document editor writes them). */
    fun write(plain: String, chars: List<CharFormat>): String {
        val out = StringBuilder()
        var start = 0
        plain.split('\n').forEachIndexed { i, line ->
            if (i > 0) out.append('\n')
            out.append(CellRich.write(line, chars.subList(start, start + line.length)))
            start += line.length + 1
        }
        return out.toString()
    }

    /** Paragraphs of [raw] as runs of equal format (text, format) – what PowerPoint gets as `<a:r>` with `<a:rPr>`. */
    fun lines(raw: String, rich: Boolean): List<List<Pair<String, CharFormat>>> {
        val p = parse(raw, rich)
        var start = 0
        return p.plain.split('\n').map { line ->
            val out = ArrayList<Pair<String, CharFormat>>()
            var i = 0
            while (i < line.length) {
                var j = i + 1
                while (j < line.length && p.chars[start + j] == p.chars[start + i]) j++
                out += line.substring(i, j) to p.chars[start + i]
                i = j
            }
            start += line.length + 1
            out
        }
    }

    /** Whether all characters [from] until [to] have [mark] (state of the format buttons). */
    fun has(chars: List<CharFormat>, from: Int, to: Int, mark: CellRich.Mark): Boolean {
        val a = from.coerceIn(0, chars.size)
        val b = to.coerceIn(a, chars.size)
        if (a == b) return false
        return (a until b).all { markOf(chars[it], mark) }
    }

    private fun markOf(f: CharFormat, mark: CellRich.Mark) = when (mark) {
        CellRich.Mark.BOLD -> f.bold
        CellRich.Mark.ITALIC -> f.italic
        CellRich.Mark.UNDERLINE -> f.underline
        CellRich.Mark.STRIKE -> f.strike
    }

    private fun withMark(f: CharFormat, mark: CellRich.Mark, on: Boolean) = when (mark) {
        CellRich.Mark.BOLD -> f.copy(bold = on)
        CellRich.Mark.ITALIC -> f.copy(italic = on)
        CellRich.Mark.UNDERLINE -> f.copy(underline = on)
        CellRich.Mark.STRIKE -> f.copy(strike = on)
    }

    /** [chars] with [mark] switched for [from] until [to]: on if one of them does not have it yet (like Word), otherwise off. */
    fun toggle(chars: List<CharFormat>, from: Int, to: Int, mark: CellRich.Mark): List<CharFormat> {
        val a = from.coerceIn(0, chars.size)
        val b = to.coerceIn(a, chars.size)
        if (a == b) return chars
        val on = !has(chars, a, b, mark)
        return chars.mapIndexed { i, f -> if (i in a until b) withMark(f, mark, on) else f }
    }

    /** [chars] with the text colour [color] ("#RRGGBB", null = the box's colour) for [from] until [to]. */
    fun color(chars: List<CharFormat>, from: Int, to: Int, color: String?): List<CharFormat> {
        val c = color?.removePrefix("#")?.uppercase()
        return chars.mapIndexed { i, f -> if (i in from until to) f.copy(color = c) else f }
    }

    /** [raw] (a whole box) with [mark] switched for all of it – the format buttons of the panel without a selection. */
    fun toggleAll(raw: String, rich: Boolean, mark: CellRich.Mark): String {
        val p = parse(raw, rich)
        return write(p.plain, toggle(p.chars, 0, p.plain.length, mark))
    }

    /**
     * The formats after an edit from [old] to [new]: the unchanged start and end keep theirs, inserted characters take
     * [typing] (a format switched on with nothing selected) or the format of the character before them.
     */
    fun reflow(old: String, chars: List<CharFormat>, new: String, typing: CharFormat? = null): List<CharFormat> {
        var pre = 0
        val max = minOf(old.length, new.length)
        while (pre < max && old[pre] == new[pre]) pre++
        var suf = 0
        while (suf < max - pre && old[old.length - 1 - suf] == new[new.length - 1 - suf]) suf++
        val inserted = new.length - pre - suf
        val fmt = typing ?: chars.getOrNull(pre - 1)?.takeIf { pre > 0 && old[pre - 1] != '\n' } ?: chars.getOrNull(pre) ?: CharFormat.PLAIN
        return chars.take(pre) + List(inserted) { i -> if (new[pre + i] == '\n') CharFormat.PLAIN else fmt } + chars.takeLast(suf)
    }

    /** The colour of a format, or null for the box's own colour. */
    fun colorOf(f: CharFormat): Color? = f.color?.let { designHex(it) }

    /** [plain] with [chars] as an [AnnotatedString] for Compose (editor, slides, the renderer of Gestalten). */
    fun annotated(plain: String, chars: List<CharFormat>): AnnotatedString {
        if (chars.none { it != CharFormat.PLAIN }) return AnnotatedString(plain)
        val b = AnnotatedString.Builder(plain)
        var i = 0
        while (i < plain.length) {
            var j = i + 1
            val f = chars.getOrElse(i) { CharFormat.PLAIN }
            while (j < plain.length && chars.getOrElse(j) { CharFormat.PLAIN } == f) j++
            if (f != CharFormat.PLAIN) b.addStyle(spanOf(f), i, j)
            i = j
        }
        return b.toAnnotatedString()
    }

    fun annotated(raw: String, rich: Boolean): AnnotatedString = parse(raw, rich).let { annotated(it.plain, it.chars) }

    private fun spanOf(f: CharFormat): SpanStyle {
        val deco = listOfNotNull(TextDecoration.Underline.takeIf { f.underline }, TextDecoration.LineThrough.takeIf { f.strike })
        return SpanStyle(
            color = colorOf(f) ?: Color.Unspecified,
            fontWeight = if (f.bold) FontWeight.Bold else null,
            fontStyle = if (f.italic) FontStyle.Italic else null,
            textDecoration = if (deco.isEmpty()) null else TextDecoration.combine(deco),
        )
    }

    private fun designHex(hex: String): Color? = hex.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

    /** Colours of the format bar: automatic (the box's colour) and the TUD colours. */
    val COLORS = listOf<String?>(null, "#00008C", "#2F57B2", "#97C6FF", "#566371", "#000000", "#FFFFFF", "#B3261E", "#1B7F3B", "#E37222")
}

/**
 * Typing right on the canvas with formatting inside the box (AP-50): a text field in the box's font, size and colour
 * whose characters keep their format while typing; Strg+B/I/U and the small bar above it format the selection (or
 * what is typed next), Esc ends. [onChange] gets the new text as inline Markdown and whether it is rich now.
 */
@Composable
fun CanvasTextEditor(
    raw: String,
    rich: Boolean,
    style: TextStyle,
    modifier: Modifier,
    onChange: (String, Boolean) -> Unit,
    onDone: () -> Unit,
    accent: Color = Color(0xFF2F57B2),
) {
    val focus = remember { FocusRequester() }
    val start = remember { FieldRich.parse(raw, rich) }
    var value by remember { mutableStateOf(TextFieldValue(start.plain, TextRange(start.plain.length))) }
    var chars by remember { mutableStateOf(start.chars) }
    var typing by remember { mutableStateOf<CharFormat?>(null) }
    var written by remember { mutableStateOf(raw) }
    // A change from outside (undo, the panel): take it over.
    LaunchedEffect(raw, rich) {
        if (raw != written) {
            val p = FieldRich.parse(raw, rich)
            chars = p.chars
            value = TextFieldValue(p.plain, TextRange(value.selection.end.coerceAtMost(p.plain.length)))
            written = raw
        }
    }
    fun commit(text: String, next: List<CharFormat>) {
        chars = next
        val out = FieldRich.write(text, next)
        val isRich = rich || next.any { it != CharFormat.PLAIN }
        written = if (isRich) out else text
        onChange(written, isRich)
    }
    fun mark(m: CellRich.Mark) {
        val s = value.selection
        if (s.collapsed) {
            val base = typing ?: chars.getOrNull(s.start - 1) ?: CharFormat.PLAIN
            typing = when (m) {
                CellRich.Mark.BOLD -> base.copy(bold = !base.bold)
                CellRich.Mark.ITALIC -> base.copy(italic = !base.italic)
                CellRich.Mark.UNDERLINE -> base.copy(underline = !base.underline)
                CellRich.Mark.STRIKE -> base.copy(strike = !base.strike)
            }
        } else commit(value.text, FieldRich.toggle(chars, s.min, s.max, m))
    }
    fun paint(color: String?) {
        val s = value.selection
        if (s.collapsed) typing = (typing ?: chars.getOrNull(s.start - 1) ?: CharFormat.PLAIN).copy(color = color?.removePrefix("#")?.uppercase())
        else commit(value.text, FieldRich.color(chars, s.min, s.max, color))
    }
    val transform = remember(chars) {
        VisualTransformation { t -> TransformedText(FieldRich.annotated(t.text, if (chars.size == t.text.length) chars else List(t.text.length) { CharFormat.PLAIN }), OffsetMapping.Identity) }
    }
    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = { v ->
                if (v.text != value.text) {
                    val next = FieldRich.reflow(value.text, chars, v.text, typing)
                    value = v
                    commit(v.text, next)
                } else {
                    if (v.selection != value.selection) typing = null
                    value = v
                }
            },
            textStyle = style,
            visualTransformation = transform,
            cursorBrush = SolidColor(accent),
            modifier = Modifier.matchParentSize().focusRequester(focus).onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val cmd = ev.isCtrlPressed || ev.isMetaPressed
                when {
                    ev.key == Key.Escape -> { onDone(); true }
                    cmd && ev.key == Key.B -> { mark(CellRich.Mark.BOLD); true }
                    cmd && ev.key == Key.I -> { mark(CellRich.Mark.ITALIC); true }
                    cmd && ev.key == Key.U -> { mark(CellRich.Mark.UNDERLINE); true }
                    else -> false
                }
            },
        )
        // The format bar just above the box; it never takes the focus, so the selection stays.
        Surface(
            shape = RoundedCornerShape(8.dp), shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopStart).offsetAbove(),
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val s = value.selection
                @Composable
                fun button(icon: ImageVector, m: CellRich.Mark, hint: String) {
                    val on = if (s.collapsed) (typing ?: chars.getOrNull(s.start - 1))?.let { f ->
                        when (m) { CellRich.Mark.BOLD -> f.bold; CellRich.Mark.ITALIC -> f.italic; CellRich.Mark.UNDERLINE -> f.underline; CellRich.Mark.STRIKE -> f.strike }
                    } == true else FieldRich.has(chars, s.min, s.max, m)
                    Box(
                        Modifier.size(26.dp).background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(5.dp))
                            .focusProperties { canFocus = false }.clickable(onClickLabel = hint) { mark(m) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, contentDescription = hint, modifier = Modifier.size(18.dp)) }
                }
                button(Icons.Outlined.FormatBold, CellRich.Mark.BOLD, tr("Fett (Strg+B)"))
                button(Icons.Outlined.FormatItalic, CellRich.Mark.ITALIC, tr("Kursiv (Strg+I)"))
                button(Icons.Outlined.FormatUnderlined, CellRich.Mark.UNDERLINE, tr("Unterstrichen (Strg+U)"))
                FieldRich.COLORS.forEach { c ->
                    Box(
                        Modifier.size(18.dp).background(c?.let { FieldRich.colorOf(CharFormat(color = it.removePrefix("#"))) } ?: Color.Transparent, CircleShape)
                            .border(1.dp, Color(0xFFBBC1C6), CircleShape)
                            .focusProperties { canFocus = false }
                            .clickable(onClickLabel = c?.let { tr("Schriftfarbe") + " " + it } ?: tr("Farbe des Textfelds")) { paint(c) },
                        contentAlignment = Alignment.Center,
                    ) { if (c == null) Text("A", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Puts the format bar above its box (its own height plus a small gap). */
private fun Modifier.offsetAbove(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        // Measured without the box's width: a narrow box must not squeeze the bar.
        val p = measurable.measure(Constraints())
        layout(p.width.coerceAtMost(constraints.maxWidth), p.height.coerceAtMost(constraints.maxHeight)) { p.place(0, -p.height - 6.dp.roundToPx()) }
    },
)
