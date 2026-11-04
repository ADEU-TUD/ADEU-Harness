package io.github.thepytales.llmhub.docs.text

/**
 * Zwischenablage mit Formatierung (AP-43 Stufe c): running text as HTML for other programs (Word, the browser, mail)
 * and HTML from them (Word, Google Docs, web pages) as running text – headings, lists, quotes, alignment and the
 * character formats the Text-Kern knows. Anything else becomes plain text.
 */
object RichHtml {
    // ── Running text → HTML ──────────────────────────────────────────────────────────────────────

    fun fromRich(r: RichText): String = buildString {
        append("<html><body>")
        var list: ParaKind? = null
        fun closeList() {
            when (list) { ParaKind.NUMBERED -> append("</ol>"); null -> {}; else -> append("</ul>") }
            list = null
        }
        r.paras.forEachIndexed { i, p ->
            val range = r.paraRange(i)
            val body = inline(r.text.substring(range.first, range.last + 1), r.chars.subList(range.first, range.last + 1))
            val style = buildList {
                when (p.align) { Align.CENTER -> add("text-align:center"); Align.RIGHT -> add("text-align:right"); Align.JUSTIFY -> add("text-align:justify"); Align.LEFT -> {} }
                p.spacing?.let { add("line-height:$it") }
                if (p.left != 0) add("margin-left:${p.left}mm")
                if (p.first != 0) add("text-indent:${p.first}mm")
            }.joinToString(";").let { if (it.isEmpty()) "" else " style=\"$it\"" }
            if (p.kind.list) {
                val want = if (p.kind == ParaKind.NUMBERED) ParaKind.NUMBERED else ParaKind.BULLET
                if (list != want) { closeList(); append(if (want == ParaKind.NUMBERED) "<ol>" else "<ul>"); list = want }
                val box = if (p.kind == ParaKind.CHECK) (if (p.checked) "☒ " else "☐ ") else ""
                append("<li$style>$box$body</li>")
                return@forEachIndexed
            }
            closeList()
            val tag = when (p.kind) {
                ParaKind.TITLE, ParaKind.H1 -> "h1"
                ParaKind.H2 -> "h2"
                ParaKind.H3 -> "h3"
                ParaKind.H4 -> "h4"
                ParaKind.QUOTE -> "blockquote"
                ParaKind.CODE -> "pre"
                else -> "p"
            }
            append("<$tag$style>$body</$tag>")
        }
        closeList()
        append("</body></html>")
    }

    private fun inline(text: String, chars: List<CharFormat>): String = buildString {
        var i = 0
        while (i < text.length) {
            val f = chars[i].stored
            var j = i + 1
            while (j < text.length && chars[j].stored == f) j++
            if (chars[i].ins > 0) { i = j; continue }
            var t = esc(text.substring(i, j))
            if (f.code) t = "<code>$t</code>"
            if (f.bold) t = "<b>$t</b>"
            if (f.italic) t = "<i>$t</i>"
            if (f.underline) t = "<u>$t</u>"
            if (f.strike) t = "<s>$t</s>"
            if (f.sup) t = "<sup>$t</sup>" else if (f.sub) t = "<sub>$t</sub>"
            val css = buildList {
                f.color?.let { add("color:#$it") }
                f.highlight?.let { add("background-color:#$it") }
                f.size?.let { add("font-size:${it}pt") }
                f.font?.let { add("font-family:'$it'") }
            }
            if (css.isNotEmpty()) t = "<span style=\"${css.joinToString(";")}\">$t</span>"
            f.link?.let { t = "<a href=\"${esc(it)}\">$t</a>" }
            append(t)
            i = j
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ── HTML → running text ──────────────────────────────────────────────────────────────────────

    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9:]*)([^>]*?)(/?)>")
    private val BLOCK = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "tr", "dt", "dd", "section", "article", "header", "footer", "figcaption", "caption", "title")

    /** The running text of an HTML fragment (from the clipboard: only the part between Start-/EndFragment). */
    fun toRich(html: String): RichText {
        var h = html
        val s = h.indexOf("<!--StartFragment-->")
        val e = h.indexOf("<!--EndFragment-->")
        if (s >= 0 && e > s) h = h.substring(s + 20, e)
        h = h.replace(Regex("(?is)<head\\b.*?</head>|<style\\b.*?</style>|<script\\b.*?</script>|<!--.*?-->|<!\\[[^\\]]*]>|<\\?xml[^>]*>"), "")

        val text = StringBuilder()
        val chars = ArrayList<CharFormat>()
        val paras = ArrayList<ParaFormat>()
        var cur = ParaFormat()
        var open = false
        val formats = ArrayList<Pair<String, CharFormat>>()
        val lists = ArrayList<String>()
        var pre = 0
        var cellInRow = 0
        fun fmt(): CharFormat = formats.lastOrNull()?.second ?: CharFormat.PLAIN
        fun newPara(p: ParaFormat) {
            if (paras.isNotEmpty()) { text.append('\n'); chars += CharFormat.PLAIN }
            paras += p
            cur = p
            open = true
        }
        fun endPara() {
            // Trailing spaces of a paragraph go.
            while (text.isNotEmpty() && text.last() == ' ') { text.setLength(text.length - 1); chars.removeAt(chars.lastIndex) }
            open = false
        }
        fun ensurePara() { if (!open) newPara(ParaFormat(kind = if (cur.kind.list && lists.isNotEmpty()) cur.kind else ParaKind.NORMAL, indent = if (lists.isNotEmpty()) cur.indent else 0)) }
        fun addText(raw: String) {
            var t = decode(raw)
            t = if (pre == 0) t.replace(Regex("[\\t\\r\\n ]+"), " ").replace('\u00A0', ' ') else t.replace("\r", "").replace('\u00A0', ' ')
            if (t.isEmpty()) return
            if (pre == 0 && t.isBlank() && (!open || text.isEmpty() || text.last() == ' ' || text.last() == '\n')) return
            ensurePara()
            if (pre == 0 && (text.isEmpty() || text.last() == '\n')) t = t.trimStart()
            for (line in t.split('\n').withIndex()) {
                if (line.index > 0) newPara(cur.copy(soft = false))
                text.append(line.value)
                repeat(line.value.length) { chars += fmt() }
            }
        }
        var last = 0
        for (m in TAG.findAll(h)) {
            if (m.range.first > last) addText(h.substring(last, m.range.first))
            last = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase().substringAfter(':')
            val attrs = m.groupValues[3]
            val selfClosing = m.groupValues[4] == "/" || name in setOf("br", "img", "hr", "meta", "link", "input")
            if (closing) {
                when {
                    name in BLOCK -> { if (name == "pre") pre = (pre - 1).coerceAtLeast(0); endPara() }
                    name == "ul" || name == "ol" -> { lists.removeLastOrNull(); endPara() }
                    name == "td" || name == "th" -> {}
                    else -> {
                        val k = formats.indexOfLast { it.first == name }
                        if (k >= 0) while (formats.size > k) formats.removeAt(formats.lastIndex)
                    }
                }
                continue
            }
            when (name) {
                "br" -> if (open) newPara(cur.copy(soft = !cur.kind.heading && !cur.kind.list)) else newPara(ParaFormat())
                "ul", "ol" -> { endPara(); lists += name }
                "td", "th" -> { if (cellInRow++ > 0) addText(" \t ") }
                "img", "hr", "meta", "link", "input" -> {}
                else -> if (name in BLOCK) {
                    if (name == "tr") cellInRow = 0
                    endPara()
                    val cls = attr(attrs, "class").lowercase()
                    val style = attr(attrs, "style")
                    var p = when {
                        name == "h1" && cls.contains("title") -> ParaFormat(kind = ParaKind.TITLE)
                        name == "h1" -> ParaFormat(kind = ParaKind.H1)
                        name == "h2" -> ParaFormat(kind = ParaKind.H2)
                        name == "h3" -> ParaFormat(kind = ParaKind.H3)
                        name in setOf("h4", "h5", "h6") -> ParaFormat(kind = ParaKind.H4)
                        name == "blockquote" -> ParaFormat(kind = ParaKind.QUOTE)
                        name == "pre" -> ParaFormat(kind = ParaKind.CODE).also { pre++ }
                        name == "figcaption" || name == "caption" -> ParaFormat(kind = ParaKind.CAPTION)
                        name == "li" -> {
                            val numbered = lists.lastOrNull() == "ol"
                            ParaFormat(kind = if (numbered) ParaKind.NUMBERED else ParaKind.BULLET, indent = (lists.size - 1).coerceIn(0, 5))
                        }
                        cls.contains("msotitle") -> ParaFormat(kind = ParaKind.TITLE)
                        cls.contains("msosubtitle") -> ParaFormat(kind = ParaKind.SUBTITLE)
                        cls.contains("msocaption") -> ParaFormat(kind = ParaKind.CAPTION)
                        cls.contains("msoquote") -> ParaFormat(kind = ParaKind.QUOTE)
                        Regex("msoheading([1-6])").find(cls) != null -> ParaFormat(kind = ParaKind.heading(Regex("msoheading([1-6])").find(cls)!!.groupValues[1].toInt()))
                        cls.contains("msolistparagraph") -> ParaFormat(kind = ParaKind.BULLET)
                        else -> ParaFormat(kind = if (cur.kind.list && lists.isNotEmpty() && name == "p") cur.kind else ParaKind.NORMAL, indent = if (lists.isNotEmpty()) cur.indent else 0)
                    }
                    css(style, "text-align")?.let { a -> p = p.copy(align = when (a.lowercase()) { "center" -> Align.CENTER; "right", "end" -> Align.RIGHT; "justify" -> Align.JUSTIFY; else -> Align.LEFT }) }
                    css(style, "line-height")?.trim()?.toFloatOrNull()?.takeIf { it in 1.4f..3f }?.let { p = p.copy(spacing = (it * 20).toInt() / 20f) }
                    newPara(p)
                } else {
                    // Inline formats.
                    if (selfClosing) continue
                    val style = attr(attrs, "style")
                    var f = fmt()
                    when (name) {
                        "b", "strong" -> f = f.copy(bold = true)
                        "i", "em", "cite" -> f = f.copy(italic = true)
                        "u", "ins" -> f = f.copy(underline = true)
                        "s", "strike", "del" -> f = f.copy(strike = true)
                        "sup" -> f = f.copy(sup = true, sub = false)
                        "sub" -> f = f.copy(sub = true, sup = false)
                        "code", "tt", "kbd" -> f = f.copy(code = true)
                        "mark" -> f = f.copy(highlight = "FFF59D")
                        "a" -> attr(attrs, "href").takeIf { it.startsWith("http") || it.startsWith("mailto:") }?.let { f = f.copy(link = decode(it)) }
                        "font" -> {
                            color(attr(attrs, "color"))?.let { f = f.copy(color = it) }
                            attr(attrs, "face").substringBefore(',').trim().ifBlank { null }?.let { f = f.copy(font = it) }
                        }
                    }
                    if (style.isNotBlank()) f = styled(f, style)
                    formats += name to f
                }
            }
        }
        if (last < h.length) addText(h.substring(last))
        endPara()
        // Empty paragraphs (between blocks of the HTML) and list marks typed as text (Word: „·“, „1.“) are tidied.
        return tidy(RichText(text.toString(), chars, paras.ifEmpty { listOf(ParaFormat()) }))
    }

    private val TYPED_BULLET = Regex("^\\s*([·•◦▪§o\\-–])\\s+")
    private val TYPED_NUMBER = Regex("^\\s*(\\d{1,2}|[a-z])[.)]\\s+")

    /** Removes empty paragraphs and turns typed list marks of Word list paragraphs into list items. */
    private fun tidy(r: RichText): RichText {
        var x = r
        // Typed marks.
        for (i in x.paras.indices.reversed()) {
            val p = x.paras[i]
            val t = x.paraText(i)
            val b = TYPED_BULLET.find(t)
            val n = TYPED_NUMBER.find(t)
            val m = when {
                p.kind == ParaKind.BULLET && b != null -> b to ParaKind.BULLET
                p.kind == ParaKind.BULLET && n != null -> n to ParaKind.NUMBERED
                p.kind == ParaKind.NORMAL && b != null && b.groupValues[1] in setOf("·", "•") -> b to ParaKind.BULLET
                else -> null
            } ?: continue
            val start = x.starts[i]
            x = x.cut(start, start + m.first.value.length)
            x = x.withParas(x.paras.mapIndexed { k, q -> if (k == i) q.copy(kind = m.second) else q })
        }
        // Empty paragraphs.
        for (i in x.paras.indices.reversed()) {
            if (x.paras.size <= 1 || x.paraText(i).isNotBlank()) continue
            val end = x.paraRange(i).last + 1
            x = if (i > 0) x.cut(x.starts[i] - 1, end)
            else x.paras[1].let { second -> x.cut(0, x.starts[1]).let { y -> y.withParas(listOf(second) + y.paras.drop(1)) } }
        }
        return x
    }

    private fun styled(base: CharFormat, style: String): CharFormat {
        var f = base
        css(style, "font-weight")?.let { w -> f = f.copy(bold = w == "bold" || w == "bolder" || (w.toIntOrNull() ?: 400) >= 600) }
        css(style, "font-style")?.let { f = f.copy(italic = it == "italic" || it == "oblique") }
        css(style, "text-decoration")?.let { d -> if ("underline" in d) f = f.copy(underline = true); if ("line-through" in d) f = f.copy(strike = true) }
        css(style, "text-decoration-line")?.let { d -> if ("underline" in d) f = f.copy(underline = true); if ("line-through" in d) f = f.copy(strike = true) }
        css(style, "vertical-align")?.let { v -> if (v == "super") f = f.copy(sup = true) else if (v == "sub") f = f.copy(sub = true) }
        color(css(style, "color"))?.takeIf { it != "000000" }?.let { f = f.copy(color = it) }
        (color(css(style, "background-color")) ?: color(css(style, "background")) ?: css(style, "mso-highlight")?.let { HIGHLIGHT[it.lowercase()] })
            ?.takeIf { it != "FFFFFF" }?.let { f = f.copy(highlight = it) }
        css(style, "font-size")?.let { s ->
            val v = s.lowercase().trim()
            val pt = when {
                v.endsWith("pt") -> v.removeSuffix("pt").trim().toFloatOrNull()
                v.endsWith("px") -> v.removeSuffix("px").trim().toFloatOrNull()?.times(0.75f)
                else -> null
            }
            // The usual sizes of body text stay without a size of their own.
            pt?.takeIf { it < 10.5f || it > 12.5f }?.let { f = f.copy(size = kotlin.math.round(it).toInt().coerceIn(6, 72)) }
        }
        css(style, "font-family")?.substringBefore(',')?.trim()?.trim('\'', '"')?.takeIf { it.isNotBlank() && !it.startsWith("-") && it.lowercase() !in setOf("arial", "calibri", "sans-serif", "inherit", "initial", "helvetica", "roboto", "segoe ui", "system-ui") }
            ?.let { f = f.copy(font = it) }
        return f
    }

    private val HIGHLIGHT = mapOf("yellow" to "FFFF00", "green" to "00FF00", "cyan" to "00FFFF", "magenta" to "FF00FF", "lightgray" to "C0C0C0")

    private fun attr(attrs: String, name: String): String =
        Regex("(?i)\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").find(attrs)?.let { m -> m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } } }.orEmpty()

    private fun css(style: String, key: String): String? = style.split(';').map { it.trim() }
        .lastOrNull { it.substringBefore(':').trim().equals(key, true) }?.substringAfter(':')?.trim()?.removeSuffix("!important")?.trim()?.takeIf { it.isNotEmpty() }

    private fun color(v: String?): String? {
        val t = v?.trim()?.lowercase() ?: return null
        Regex("^#?([0-9a-f]{6})$").find(t)?.let { return it.groupValues[1].uppercase() }
        Regex("^#([0-9a-f])([0-9a-f])([0-9a-f])$").find(t)?.let { m -> return m.groupValues.drop(1).joinToString("") { it + it }.uppercase() }
        Regex("^rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(,\\s*([0-9.]+))?\\)$").find(t)?.let { m ->
            if (m.groupValues[5].toFloatOrNull()?.let { it < 0.1f } == true) return null
            return m.groupValues.subList(1, 4).joinToString("") { "%02X".format(it.toInt().coerceIn(0, 255)) }
        }
        return when (t) { "red" -> "FF0000"; "blue" -> "0000FF"; "green" -> "008000"; "yellow" -> "FFFF00"; "gray", "grey" -> "808080"; "white" -> "FFFFFF"; "black" -> "000000"; else -> null }
    }

    private fun decode(s: String): String {
        if ('&' !in s) return s
        return Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ENTITIES[v] ?: m.value
            }
        }
    }

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0", "ndash" to "–", "mdash" to "—",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "hellip" to "…", "euro" to "€",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß", "copy" to "©", "reg" to "®",
        "middot" to "·", "bull" to "•", "sect" to "§", "deg" to "°", "laquo" to "«", "raquo" to "»", "shy" to "",
    )
}
