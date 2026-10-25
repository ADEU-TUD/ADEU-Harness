package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp

internal sealed interface Block {
    data class Code(val lang: String, val code: String) : Block
    data class Heading(val level: Int, val text: String) : Block
    data class Item(val indent: Int, val marker: String, val text: String) : Block
    data class Quote(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    data class Paragraph(val text: String) : Block
    data object Rule : Block
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val LIST_ITEM = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val RULE = Regex("^([-*_])\\1{2,}$")
internal val INLINE = Regex(
    "(\\*\\*|__)(.+?)\\1|`([^`]+)`|\\[([^\\]]+)]\\(([^)\\s]+)\\)|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)|~~(.+?)~~"
)

internal fun parseBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = src.lines()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[i])
                    i++
                }
                out += Block.Code(lang, sb.toString())
            }
            t.isEmpty() -> flush()
            HEADING.matches(t) -> {
                flush()
                val m = HEADING.find(t)!!
                out += Block.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            RULE.matches(t) -> {
                flush()
                out += Block.Rule
            }
            t.startsWith(">") -> {
                flush()
                val sb = StringBuilder(t.removePrefix(">").trim())
                while (i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                    i++
                    sb.append('\n').append(lines[i].trim().removePrefix(">").trim())
                }
                out += Block.Quote(sb.toString())
            }
            t.startsWith("|") -> {
                flush()
                val rows = mutableListOf<List<String>>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith("|")) {
                    val cells = lines[j].trim().trim('|').split("|").map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                    j++
                }
                i = j - 1
                out += Block.Table(rows)
            }
            LIST_ITEM.matches(line) -> {
                flush()
                val m = LIST_ITEM.find(line)!!
                val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
                out += Block.Item(m.groupValues[1].replace("\t", "  ").length / 2, marker, m.groupValues[3])
            }
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line.trimEnd())
            }
        }
        i++
    }
    flush()
    return out
}

/** A run of inline text with its formatting, used by the document writers. */
internal data class Segment(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

internal fun inlineSegments(text: String, base: Segment = Segment("")): List<Segment> {
    val out = mutableListOf<Segment>()
    fun plain(t: String) {
        if (t.isNotEmpty()) out += base.copy(text = t)
    }
    var last = 0
    for (m in INLINE.findAll(text)) {
        plain(text.substring(last, m.range.first))
        val g = m.groups
        when {
            g[2] != null -> out += inlineSegments(g[2]!!.value, base.copy(bold = true))
            g[3] != null -> out += base.copy(text = g[3]!!.value, code = true)
            g[4] != null -> out += base.copy(text = g[4]!!.value, link = g[5]!!.value)
            g[6] != null -> out += inlineSegments(g[6]!!.value, base.copy(italic = true))
            g[7] != null -> out += inlineSegments(g[7]!!.value, base.copy(strike = true))
        }
        last = m.range.last + 1
    }
    plain(text.substring(last))
    return out
}

fun inlineMarkdown(text: String, codeBackground: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groups
        when {
            g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(inlineMarkdown(g[2]!!.value, codeBackground, linkColor))
            }
            g[3] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(g[3]!!.value)
            }
            g[4] != null -> withLink(
                LinkAnnotation.Url(
                    g[5]!!.value,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                )
            ) { append(g[4]!!.value) }
            g[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]!!.value) }
            g[7] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[7]!!.value) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val blocks = remember(text) { parseBlocks(text) }
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val link = MaterialTheme.colorScheme.primary
    fun md(s: String) = inlineMarkdown(io.github.thepytales.llmhub.docs.MathText.inText(s), codeBg, link)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Code -> {
                    val chart = if (b.lang.equals("chart", ignoreCase = true)) remember(b.code) { io.github.thepytales.llmhub.docs.ChartSpec.parse(b.code) } else null
                    if (chart != null) ChartView(chart) else CodeBlock(b.lang, b.code)
                }
                is Block.Heading -> Text(
                    md(b.text),
                    color = color,
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                )
                is Block.Item -> Row(Modifier.padding(start = (b.indent * 14).dp)) {
                    Text(b.marker, color = color, modifier = Modifier.padding(end = 6.dp))
                    Text(md(b.text), color = color)
                }
                is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Text(
                        md(b.text),
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is Block.Table -> TableBlock(b.rows)
                is Block.Paragraph -> Text(md(b.text), color = color)
                Block.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TableBlock(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val cols = rows.maxOf { it.size }
    val widths = (0 until cols).map { c -> rows.maxOf { it.getOrNull(c)?.length ?: 0 }.coerceIn(3, 40) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            rows.forEachIndexed { r, row ->
                Row {
                    (0 until cols).forEach { c ->
                        Text(
                            inlineMarkdown(row.getOrNull(c).orEmpty(), Color.Transparent, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .width((widths[c] * 8 + 16).dp)
                                .padding(end = 8.dp, bottom = 4.dp),
                            fontWeight = if (r == 0) FontWeight.Bold else null,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (r == 0) HorizontalDivider()
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    lang.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(18.dp))
                }
            }
            val colors = SyntaxColors(
                keyword = MaterialTheme.colorScheme.primary, string = Color(0xFF15803D).let { if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF86EFAC) else it },
                comment = MaterialTheme.colorScheme.outline, number = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                remember(code, lang, colors) { highlight(code, lang, colors) },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}


/** Colors of the syntax highlighting (taken from the theme, so dark mode works). */
data class SyntaxColors(val keyword: Color, val string: Color, val comment: Color, val number: Color)

private val KEYWORDS = setOf(
    // Kotlin, Java, JavaScript, TypeScript, C-like
    "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "do", "return", "break", "continue", "import", "package",
    "public", "private", "protected", "internal", "override", "open", "data", "sealed", "enum", "const", "static", "final", "void", "new", "this", "super",
    "null", "true", "false", "try", "catch", "finally", "throw", "throws", "function", "let", "async", "await", "export", "default", "extends", "implements",
    "switch", "case", "typeof", "instanceof", "in", "is", "as", "int", "long", "double", "float", "boolean", "char", "string", "suspend",
    // Python, R
    "def", "lambda", "pass", "with", "yield", "from", "and", "or", "not", "None", "True", "False", "elif", "except", "raise", "global", "nonlocal",
    "library", "require", "function", "repeat", "NULL", "NA", "TRUE", "FALSE", "Inf", "NaN",
    // SQL
    "select", "from", "where", "group", "by", "order", "having", "join", "left", "right", "inner", "outer", "on", "insert", "into", "values",
    "update", "set", "delete", "create", "table", "drop", "alter", "limit", "distinct", "count", "sum", "avg", "as",
)
private val TOKEN = Regex("(//[^\\n]*|#(?![\\w{])[^\\n]*|/\\*[\\s\\S]*?\\*/|--[^\\n]*)|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')|\\b(\\d+(?:\\.\\d+)?)\\b|\\b([A-Za-z_][A-Za-z0-9_]*)\\b")

/** Light syntax highlighting for the usual languages of answers (Kotlin, Java, JavaScript, Python, R, SQL). */
fun highlight(code: String, lang: String, c: SyntaxColors): AnnotatedString {
    if (code.length > 20_000 || lang.lowercase() in setOf("text", "txt", "plain", "markdown", "md", "csv")) return AnnotatedString(code)
    val sql = lang.lowercase() == "sql"
    val hashComments = lang.lowercase() in setOf("python", "py", "r", "bash", "sh", "shell", "yaml", "yml", "toml", "")
    return buildAnnotatedString {
        var last = 0
        TOKEN.findAll(code).forEach { m ->
            append(code.substring(last, m.range.first))
            val v = m.value
            when {
                m.groupValues[1].isNotEmpty() && (v.startsWith("#").not() || hashComments) && (!v.startsWith("--") || sql) ->
                    withStyle(SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)) { append(v) }
                m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(color = c.string)) { append(v) }
                m.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(color = c.number)) { append(v) }
                m.groupValues[4].isNotEmpty() && (v in KEYWORDS || sql && v.lowercase() in KEYWORDS) -> withStyle(SpanStyle(color = c.keyword, fontWeight = FontWeight.SemiBold)) { append(v) }
                else -> append(v)
            }
            last = m.range.last + 1
        }
        append(code.substring(last))
    }
}
