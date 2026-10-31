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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
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
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
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

/** Inline syntax of the chat view: like [INLINE], plus bare web addresses and numbered source markers ([1], [2, 3], [^4]). */
private val CHAT_INLINE = Regex(
    INLINE.pattern + "|(https?://[^\\s<>()\\[\\]]*[^\\s<>()\\[\\].,;:!?\"'])|\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)](?![(:])|\\[\\^(\\d{1,3})]"
)

/** "www.uni-dresden.de/…" → "uni-dresden.de"; null for anything that is not a web address. */
internal fun linkDomain(url: String): String? {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    return host.removePrefix("www.").lowercase().ifBlank { null }
}

/** Where a numbered source marker leads: a web address, or an action (open a PDF page, a knowledge entry …). */
class SourceTarget(val label: String, val url: String? = null, val open: (() -> Unit)? = null)

/**
 * Text with Markdown formatting for display. Links show as their text followed by a small marker with the domain,
 * bare web addresses as that marker alone; numbered source markers ([1]) become small badges, linked to
 * [sources] when the number is known there.
 */
fun inlineMarkdown(
    text: String,
    codeBackground: Color,
    linkColor: Color,
    sources: Map<Int, SourceTarget> = emptyMap(),
    /** Collects domain and source markers as real little badges (see [MarkdownText]); null: styled text instead. */
    chips: MutableList<InlineChip>? = null,
): AnnotatedString = buildAnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val markerStyle = SpanStyle(color = linkColor, fontSize = 0.8.em, fontWeight = FontWeight.Medium)
    fun chip(label: String, source: Boolean, url: String?, open: (() -> Unit)?): Boolean {
        if (chips == null) return false
        val id = "chip${chips.size}"
        chips += InlineChip(id, label, source, url, open)
        appendInlineContent(id, if (source) "[$label]" else label)
        return true
    }
    fun domainMarker(domain: String, url: String? = null) {
        if (!chip(domain, source = false, url = url, open = null)) withStyle(markerStyle) { append("\u2009↗\u2009$domain") }
    }
    fun sourceMarker(n: Int, target: SourceTarget? = sources[n]) {
        if (!chip("$n", source = true, url = target?.url, open = target?.open)) sourceMarkerWith(n, target, linkColor)
    }
    var last = 0
    for (m in CHAT_INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groups
        when {
            g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(inlineMarkdown(g[2]!!.value, codeBackground, linkColor, sources, chips))
            }
            g[3] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(g[3]!!.value)
            }
            g[4] != null -> {
                val label = g[4]!!.value
                val url = g[5]!!.value
                val domain = linkDomain(url)
                when {
                    // "[3](https://…)": a numbered source with its address.
                    label.trim().matches(Regex("\\d{1,3}")) -> {
                        val n = label.trim().toInt()
                        sourceMarker(n, sources[n] ?: SourceTarget(url, url = url))
                    }
                    domain == null -> withLink(LinkAnnotation.Url(url, linkStyle)) { append(label) }
                    // The address itself as text: only the domain marker.
                    label == url || label.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/') == domain ->
                        if (chips != null) domainMarker(domain, url) else withLink(LinkAnnotation.Url(url)) { domainMarker(domain) }
                    else -> {
                        withLink(LinkAnnotation.Url(url, linkStyle)) { append(label) }
                        append("\u2009")
                        if (chips != null) domainMarker(domain, url) else withLink(LinkAnnotation.Url(url)) { domainMarker(domain) }
                    }
                }
            }
            g[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]!!.value) }
            g[7] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[7]!!.value) }
            g[8] != null -> {
                val url = g[8]!!.value
                if (chips != null) domainMarker(linkDomain(url) ?: url, url) else withLink(LinkAnnotation.Url(url)) { domainMarker(linkDomain(url) ?: url) }
            }
            g[9] != null -> g[9]!!.value.split(',').map { it.trim().toInt() }.forEachIndexed { i, n ->
                if (i > 0) append("\u200A")
                sourceMarker(n)
            }
            g[10] != null -> sourceMarker(g[10]!!.value.toInt())
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** A numbered source as small raised badge; linked when its [target] is known. */
private fun AnnotatedString.Builder.sourceMarkerWith(n: Int, target: SourceTarget?, color: Color) {
    val style = SpanStyle(color = color, fontSize = 0.72.em, fontWeight = FontWeight.Bold, baselineShift = BaselineShift.Superscript)
    val annotation = when {
        target?.open != null -> LinkAnnotation.Clickable("source-$n", TextLinkStyles(style)) { target.open.invoke() }
        target?.url != null -> LinkAnnotation.Url(target.url, TextLinkStyles(style))
        else -> null
    }
    if (annotation != null) withLink(annotation) { append("\u2009$n\u2009") } else withStyle(style) { append("\u2009$n\u2009") }
}

private val SOURCE_HEADING = Regex("(?i)\\b(quellen|belege|fundstellen|literatur|referenzen|sources|references)\\b")
private val SOURCE_LINE = Regex("^\\s*(?:\\[(\\d{1,3})]|\\[\\^(\\d{1,3})]|(\\d{1,3})[.)])\\s*:?\\s*(.+)$")
private val URL_IN = Regex("https?://[^\\s<>()\\[\\]]*[^\\s<>()\\[\\].,;:!?\"']")
private val DOI_IN = Regex("\\b10\\.\\d{4,9}/[^\\s\"<>]+[^\\s\"<>.,;]")

/**
 * The numbered sources an answer lists at its end ("[1] Titel – https://…", "1. … doi:10.…" under a heading such as
 * „Quellen“), so the markers in the text can link to them.
 */
internal fun sourceList(text: String): Map<Int, SourceTarget> {
    val out = mutableMapOf<Int, SourceTarget>()
    var inSources = false
    for (line in text.lines()) {
        val t = line.trim()
        if (t.startsWith("#") || (t.startsWith("**") && t.length < 60)) {
            inSources = SOURCE_HEADING.containsMatchIn(t)
            continue
        }
        val m = SOURCE_LINE.find(line) ?: continue
        val bracketed = m.groupValues[1].isNotEmpty() || m.groupValues[2].isNotEmpty()
        if (!bracketed && !inSources) continue
        val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).toInt()
        val rest = m.groupValues[4]
        val url = URL_IN.find(rest)?.value ?: DOI_IN.find(rest)?.value?.let { "https://doi.org/$it" }
        val label = rest.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1").replace(URL_IN, "").trim().trimEnd('–', '-', ':', ',').trim()
        if (n !in out) out[n] = SourceTarget(label.ifBlank { url ?: "Quelle $n" }, url = url)
    }
    return out
}

/** A domain or source marker shown as a little badge inside the text. */
class InlineChip(val id: String, val label: String, val source: Boolean, val url: String?, val open: (() -> Unit)?)

/** Text with Markdown inline formatting whose domain and source markers are rounded badges (measured to fit). */
@Composable
internal fun RichText(
    src: String,
    sources: Map<Int, SourceTarget>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val codeBg = accent.copy(alpha = 0.09f)
    val (text, chips) = remember(src, sources, accent) {
        val list = mutableListOf<InlineChip>()
        inlineMarkdown(io.github.thepytales.llmhub.docs.MathText.inText(src), codeBg, accent, sources, list) to list.toList()
    }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val base = style.fontSize.takeIf { it.isSp } ?: 16.sp
    val content = remember(chips, base, accent, density) {
        chips.associate { chip ->
            val chipStyle = androidx.compose.ui.text.TextStyle(
                fontSize = base * (if (chip.source) 0.68f else 0.76f), fontWeight = if (chip.source) FontWeight.Bold else FontWeight.Medium,
            )
            val size = measurer.measure(chip.label, chipStyle, maxLines = 1).size
            val (w, h) = with(density) {
                val pad = if (chip.source) 5.dp.toPx() else 7.dp.toPx()
                val height = size.height + 2.dp.toPx()
                maxOf(size.width + 2 * pad, height).toSp() to height.toSp()
            }
            chip.id to androidx.compose.foundation.text.InlineTextContent(
                androidx.compose.ui.text.Placeholder(w, h, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter),
            ) {
                val enabled = chip.open != null || chip.url != null
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = if (chip.source) 0.16f else 0.10f))
                        .then(
                            if (enabled) Modifier.clickable(onClickLabel = chip.url ?: chip.label) {
                                chip.open?.invoke() ?: chip.url?.let { runCatching { uri.openUri(it) } }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(chip.label, style = chipStyle, color = accent, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    Text(text, modifier = modifier, color = color, style = style, fontWeight = fontWeight, fontStyle = fontStyle, inlineContent = content)
}

/** Extra targets for source markers, e.g. the passages the evidence check found (see agent/Evidence). */
val LocalSourceTargets = androidx.compose.runtime.staticCompositionLocalOf<Map<Int, SourceTarget>> { emptyMap() }

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val blocks = remember(text) { parseBlocks(text) }
    val accent = MaterialTheme.colorScheme.primary
    val extra = LocalSourceTargets.current
    val sources = remember(text, extra) { sourceList(text) + extra }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is Block.Code -> {
                    val chart = if (b.lang.equals("chart", ignoreCase = true)) remember(b.code) { io.github.thepytales.llmhub.docs.ChartSpec.parse(b.code) } else null
                    if (chart != null) ChartView(chart) else CodeBlock(b.lang, b.code)
                }
                is Block.Heading -> RichText(
                    b.text, sources,
                    color = color,
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (i > 0 && b.level <= 2) 6.dp else 0.dp),
                )
                is Block.Item -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(
                        b.marker, color = if (b.marker == "•") accent else color,
                        fontWeight = if (b.marker == "•") FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    RichText(b.text, sources, color = color)
                }
                is Block.Quote -> {
                    val callout = remember(b.text) { calloutOf(b.text) }
                    if (callout != null) Callout(callout.first, callout.second, sources)
                    else Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(accent.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                        )
                        RichText(
                            b.text, sources,
                            modifier = Modifier.padding(start = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                is Block.Table -> TableBlock(b.rows, sources)
                is Block.Paragraph -> RichText(b.text, sources, color = color)
                Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Kind of a highlighted note in an answer ("> [!NOTE]", "> **Hinweis:** …"). */
internal enum class CalloutKind(val title: String) { NOTE("Hinweis"), TIP("Tipp"), IMPORTANT("Wichtig"), WARNING("Achtung") }

private val CALLOUT_TAG = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*", RegexOption.IGNORE_CASE)
private val CALLOUT_WORD = Regex("^\\*\\*(Hinweis|Tipp|Wichtig|Achtung|Warnung|Note|Tip|Important|Warning)\\s*:?\\*\\*\\s*:?\\s*", RegexOption.IGNORE_CASE)

/** A quote that is meant as a note: its kind and the text without the tag. */
internal fun calloutOf(text: String): Pair<CalloutKind, String>? {
    CALLOUT_TAG.find(text)?.let { m ->
        val kind = when (m.groupValues[1].uppercase()) {
            "TIP" -> CalloutKind.TIP
            "IMPORTANT" -> CalloutKind.IMPORTANT
            "WARNING", "CAUTION" -> CalloutKind.WARNING
            else -> CalloutKind.NOTE
        }
        return kind to text.substring(m.range.last + 1).trim()
    }
    CALLOUT_WORD.find(text)?.let { m ->
        val kind = when (m.groupValues[1].lowercase()) {
            "tipp", "tip" -> CalloutKind.TIP
            "wichtig", "important" -> CalloutKind.IMPORTANT
            "achtung", "warnung", "warning" -> CalloutKind.WARNING
            else -> CalloutKind.NOTE
        }
        return kind to text.substring(m.range.last + 1).trim()
    }
    return null
}

/** A note box: fine frame in the accent (or warning) color, a light tint, icon and title – no gray surface. */
@Composable
private fun Callout(kind: CalloutKind, text: String, sources: Map<Int, SourceTarget>) {
    val look = LocalChatLook.current
    val color = when (kind) {
        CalloutKind.WARNING -> look.warning
        CalloutKind.IMPORTANT -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when (kind) {
        CalloutKind.NOTE -> Icons.Outlined.Info
        CalloutKind.TIP -> Icons.Outlined.Lightbulb
        CalloutKind.IMPORTANT -> Icons.Outlined.PriorityHigh
        CalloutKind.WARNING -> Icons.Outlined.WarningAmber
    }
    Surface(
        color = color.copy(alpha = 0.06f),
        border = BorderStroke(if (look.cardBorder?.width == 2.dp) 2.dp else 1.dp, color.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.accentStripe(color).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(tr(kind.title), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
                RichText(text, sources, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TableBlock(rows: List<List<String>>, sources: Map<Int, SourceTarget> = emptyMap()) {
    if (rows.isEmpty()) return
    val cols = rows.maxOf { it.size }
    val widths = (0 until cols).map { c -> rows.maxOf { it.getOrNull(c)?.length ?: 0 }.coerceIn(3, 40) }
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Surface(color = LocalChatLook.current.card, border = boxBorder(), shape = MaterialTheme.shapes.small) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { r, row ->
                Row(
                    Modifier
                        .background(if (r == 0) accent.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = if (r == 0) 8.dp else 6.dp)
                ) {
                    (0 until cols).forEach { c ->
                        RichText(
                            row.getOrNull(c).orEmpty(), sources,
                            modifier = Modifier
                                .width((widths[c] * 8 + 16).dp)
                                .padding(end = 8.dp),
                            fontWeight = if (r == 0) FontWeight.SemiBold else null,
                            color = if (r == 0) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (r < rows.lastIndex) HorizontalDivider(color = if (r == 0) accent.copy(alpha = 0.35f) else line.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = LocalChatLook.current.card,
        border = boxBorder(),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.accentStripe(accent)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    lang.ifBlank { "code" },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val colors = SyntaxColors(
                keyword = MaterialTheme.colorScheme.primary, string = Color(0xFF15803D).let { if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF86EFAC) else it },
                comment = MaterialTheme.colorScheme.outline, number = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                remember(code, lang, colors) { highlight(code, lang, colors) },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
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
