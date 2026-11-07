package io.github.thepytales.llmhub.docs.text

/**
 * Zwischenablage mit Formatierung als RTF (AP-43 Stufe c): what older programs and Word put on the clipboard besides
 * HTML (or instead of it, e.g. WordPad, Outlook in some views). Writes running text as RTF and reads the common part of
 * RTF back – fett, kursiv, unterstrichen, durchgestrichen, hoch/tief, Farbe, Hervorhebung, Größe, Schrift, Ausrichtung,
 * Absätze; pictures, tables and fields are left out.
 */
object RichRtf {
    // ── Running text → RTF ───────────────────────────────────────────────────────────────────────

    fun fromRich(r: RichText): String {
        val fonts = mutableListOf("Calibri", "Courier New")
        val colors = mutableListOf<String>()
        fun font(name: String?): Int = if (name == null) 0 else fonts.indexOf(name).takeIf { it >= 0 } ?: fonts.size.also { fonts += name }
        fun color(hex: String?): Int = if (hex == null) 0 else (colors.indexOf(hex.uppercase()).takeIf { it >= 0 } ?: colors.size.also { colors += hex.uppercase() }) + 1
        val body = StringBuilder()
        val numbers = IntArray(6)
        r.paras.forEachIndexed { pi, p ->
            val range = r.paraRange(pi)
            body.append("\\pard\\plain")
            body.append(when (p.align) { Align.CENTER -> "\\qc"; Align.RIGHT -> "\\qr"; Align.JUSTIFY -> "\\qj"; Align.LEFT -> "\\ql" })
            val listIndent = if (p.kind.list) (p.indent + 1) * 360 else if (p.kind == ParaKind.QUOTE) 567 else 0
            val left = listIndent + p.left * 57
            if (left != 0) body.append("\\li").append(left)
            val first = if (p.kind.list) -360 else p.first * 57
            if (first != 0) body.append("\\fi").append(first)
            p.spacing?.let { body.append("\\sl").append((it * 240).toInt()).append("\\slmult1") }
            body.append("\\sa120 ")
            // Formatvorlagen as direct format: headings bold and larger.
            val (paraSize, paraBold) = when (p.kind) {
                ParaKind.TITLE -> 26 to true
                ParaKind.SUBTITLE -> 15 to false
                ParaKind.H1 -> 16 to true
                ParaKind.H2 -> 14 to true
                ParaKind.H3 -> 12 to true
                ParaKind.H4 -> 11 to true
                else -> 11 to false
            }
            if (!p.kind.list) numbers.fill(0)
            if (p.kind.list && !p.soft) {
                val lvl = p.indent.coerceIn(0, 5)
                for (k in lvl + 1 until 6) numbers[k] = 0
                val mark = when (p.kind) {
                    ParaKind.NUMBERED -> { numbers[lvl]++; "${numbers[lvl]}." }
                    ParaKind.CHECK -> if (p.checked) "\u2612" else "\u2610"
                    else -> "\u2022"
                }
                body.append(esc(mark)).append("\\tab ")
            }
            var i = range.first
            while (i <= range.last) {
                val f = r.chars[i]
                var j = i + 1
                while (j <= range.last && r.chars[j] == f) j++
                if (f.ins == 0) {
                    body.append('{')
                    if (f.bold || paraBold) body.append("\\b")
                    if (f.italic || p.kind == ParaKind.QUOTE) body.append("\\i")
                    if (f.underline || f.link != null) body.append("\\ul")
                    if (f.strike || f.del > 0) body.append("\\strike")
                    if (f.sup) body.append("\\super") else if (f.sub) body.append("\\sub")
                    val fi = if (f.code || p.kind == ParaKind.CODE) 1 else font(f.font)
                    if (fi != 0) body.append("\\f").append(fi)
                    body.append("\\fs").append((f.size ?: paraSize) * 2)
                    val ci = color(f.color ?: if (f.link != null) "1D4ED8" else null)
                    if (ci != 0) body.append("\\cf").append(ci)
                    val hi = color(f.highlight)
                    if (hi != 0) body.append("\\highlight").append(hi).append("\\chcbpat").append(hi)
                    body.append(' ').append(esc(r.text.substring(i, j))).append('}')
                }
                i = j
            }
            body.append("\\par\n")
        }
        return buildString {
            append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\uc1")
            append("{\\fonttbl")
            fonts.forEachIndexed { k, f -> append("{\\f").append(k).append(if (k == 1) "\\fmodern " else "\\fswiss ").append(esc(f)).append(";}") }
            append("}")
            append("{\\colortbl;")
            colors.forEach { c ->
                val v = c.toIntOrNull(16) ?: 0
                append("\\red").append(v shr 16 and 255).append("\\green").append(v shr 8 and 255).append("\\blue").append(v and 255).append(';')
            }
            append("}\n")
            append(body)
            append("}")
        }
    }

    /** RTF text: \ { } escaped, everything beyond ASCII as \uN? (signed 16 bit). */
    private fun esc(s: String): String = buildString {
        for (ch in s) when {
            ch == '\\' || ch == '{' || ch == '}' -> append('\\').append(ch)
            ch == '\t' -> append("\\tab ")
            ch == '\n' -> append("\\line ")
            ch.code < 128 -> append(ch)
            else -> append("\\u").append(ch.code.toShort().toInt()).append('?')
        }
    }

    // ── RTF → running text ───────────────────────────────────────────────────────────────────────

    private data class St(
        val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val strike: Boolean = false,
        val sup: Boolean = false, val sub: Boolean = false, val color: Int = 0, val highlight: Int = 0, val size: Int = 24,
        val font: Int = 0, val skip: Boolean = false, val uc: Int = 1, val dest: String? = null,
    )

    /** Destinations whose text is not part of the document. */
    private val SKIP = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer", "headerl", "headerr", "headerf",
        "footerl", "footerr", "footerf", "footnote", "fldinst", "themedata", "colorschememapping", "latentstyles",
        "datastore", "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "generator", "pgdsctbl", "listtext", "pntext", "bkmkstart", "bkmkend",
    )

    fun toRich(rtf: String): RichText {
        val text = StringBuilder()
        val chars = ArrayList<CharFormat>()
        val paras = ArrayList<ParaFormat>()
        var para = ParaFormat()
        val fonts = HashMap<Int, String>()
        val colors = mutableListOf<String?>()
        var red = 0
        var green = 0
        var blue = 0
        var colorSet = false
        var fontNo = -1
        val fontName = StringBuilder()
        val stack = ArrayDeque<St>()
        var st = St()
        var skipChars = 0
        var star = false
        var usual = -1
        val bytes = java.io.ByteArrayOutputStream()

        fun flushBytes() {
            if (bytes.size() == 0) return
            val s = String(bytes.toByteArray(), charset("windows-1252"))
            bytes.reset()
            emit(s, st, text, chars, colors, fonts, usual)
        }
        fun put(s: String) {
            flushBytes()
            when (st.dest) {
                "fonttbl" -> fontName.append(s)
                null -> emit(s, st, text, chars, colors, fonts, usual)
                else -> {}
            }
        }
        fun newPara(soft: Boolean = false) {
            flushBytes()
            if (st.skip || st.dest != null) return
            paras += para
            text.append('\n')
            chars += CharFormat.PLAIN
            if (soft) para = para.copy(soft = true) else para = para.copy(soft = false)
        }

        var i = 0
        val n = rtf.length
        while (i < n) {
            val ch = rtf[i]
            when {
                ch == '{' -> { flushBytes(); stack.addLast(st); i++ }
                ch == '}' -> {
                    flushBytes()
                    if (st.dest == "fonttbl" && fontNo >= 0 && fontName.isNotEmpty()) {
                        fonts[fontNo] = fontName.toString().trim().removeSuffix(";").trim()
                        fontName.setLength(0)
                        fontNo = -1
                    }
                    st = stack.removeLastOrNull() ?: St()
                    i++
                }
                ch == '\\' -> {
                    i++
                    if (i >= n) break
                    val c = rtf[i]
                    if (c.isLetter()) {
                        val ws = i
                        while (i < n && rtf[i].isLetter()) i++
                        val word = rtf.substring(ws, i)
                        var num: Int? = null
                        if (i < n && (rtf[i] == '-' || rtf[i].isDigit())) {
                            val ns = i
                            i++
                            while (i < n && rtf[i].isDigit()) i++
                            num = rtf.substring(ns, i).toIntOrNull()
                        }
                        if (i < n && rtf[i] == ' ') i++
                        if (skipChars > 0 && word != "u") { skipChars--; continue }
                        // A destination: skipped when unknown after \*, or one of those without document text.
                        if (word in SKIP || star) {
                            flushBytes()
                            st = st.copy(dest = if (word == "fonttbl" || word == "colortbl") word else "skip", skip = word != "fonttbl" && word != "colortbl")
                            star = false
                            continue
                        }
                        if (st.dest == "colortbl") {
                            when (word) { "red" -> red = num ?: 0; "green" -> green = num ?: 0; "blue" -> blue = num ?: 0 }
                            if (word == "red" || word == "green" || word == "blue") colorSet = true
                            continue
                        }
                        if (st.dest == "fonttbl") {
                            if (word == "f") {
                                if (fontNo >= 0 && fontName.isNotBlank()) fonts[fontNo] = fontName.toString().trim().removeSuffix(";").trim()
                                fontNo = num ?: 0
                                fontName.setLength(0)
                            }
                            continue
                        }
                        if (st.skip) continue
                        val on = num != 0
                        when (word) {
                            "b" -> st = st.copy(bold = on)
                            "i" -> st = st.copy(italic = on)
                            "ul" -> st = st.copy(underline = on)
                            "ulnone" -> st = st.copy(underline = false)
                            "strike", "striked" -> st = st.copy(strike = on)
                            "super" -> st = st.copy(sup = true, sub = false)
                            "sub" -> st = st.copy(sub = true, sup = false)
                            "nosupersub" -> st = st.copy(sup = false, sub = false)
                            "cf" -> st = st.copy(color = num ?: 0)
                            "highlight", "chcbpat", "cb" -> st = st.copy(highlight = num ?: 0)
                            "fs" -> st = st.copy(size = num ?: 24)
                            "f" -> st = st.copy(font = num ?: 0)
                            "plain" -> st = st.copy(bold = false, italic = false, underline = false, strike = false, sup = false, sub = false, color = 0, highlight = 0, size = 24, font = 0)
                            "uc" -> st = st.copy(uc = num ?: 1)
                            "u" -> { put(((num ?: 0).let { if (it < 0) it + 65536 else it }).toChar().toString()); skipChars = st.uc }
                            "par" -> newPara()
                            "line" -> newPara(soft = true)
                            "tab" -> put("\t")
                            "pard" -> para = ParaFormat()
                            "qc" -> para = para.copy(align = Align.CENTER)
                            "qr" -> para = para.copy(align = Align.RIGHT)
                            "qj" -> para = para.copy(align = Align.JUSTIFY)
                            "ql" -> para = para.copy(align = Align.LEFT)
                            "emdash" -> put("\u2014")
                            "endash" -> put("\u2013")
                            "bullet" -> put("\u2022")
                            "lquote" -> put("\u2018")
                            "rquote" -> put("\u2019")
                            "ldblquote" -> put("\u201C")
                            "rdblquote" -> put("\u201D")
                            "deff" -> usual = num ?: 0
                        }
                    } else {
                        i++
                        if (skipChars > 0) { skipChars--; if (c == '\'') i += 2; continue }
                        when (c) {
                            '\\', '{', '}' -> put(c.toString())
                            '~' -> put("\u00A0")
                            '-', '_' -> {}
                            '*' -> star = true
                            '\'' -> {
                                val hex = rtf.substring(i, minOf(i + 2, n))
                                i += hex.length
                                hex.toIntOrNull(16)?.let { if (!st.skip && st.dest == null) bytes.write(it) else if (st.dest == "fonttbl") fontName.append(it.toChar()) }
                            }
                            '\n', '\r' -> newPara()
                        }
                    }
                }
                ch == '\n' || ch == '\r' -> i++
                ch == ';' && st.dest == "colortbl" -> {
                    // The first entry is usually empty: the automatic colour (\cf0).
                    colors += if (!colorSet) null else "%02X%02X%02X".format(red, green, blue)
                    red = 0; green = 0; blue = 0; colorSet = false
                    i++
                }
                else -> {
                    if (skipChars > 0) { skipChars--; i++; continue }
                    val start = i
                    while (i < n && rtf[i] != '\\' && rtf[i] != '{' && rtf[i] != '}' && rtf[i] != '\n' && rtf[i] != '\r' && !(rtf[i] == ';' && st.dest == "colortbl")) i++
                    put(rtf.substring(start, i))
                }
            }
        }
        flushBytes()
        paras += para
        // A trailing empty paragraph (the last \par) is not part of the piece.
        var r = RichText(text.toString(), chars, paras)
        while (r.paras.size > 1 && r.text.endsWith("\n")) r = r.cut(r.text.length - 1, r.text.length)
        return r
    }

    private fun emit(s: String, st: St, text: StringBuilder, chars: MutableList<CharFormat>, colors: List<String?>, fonts: Map<Int, String>, usual: Int) {
        if (st.skip || st.dest != null || s.isEmpty()) return
        val f = CharFormat(
            bold = st.bold, italic = st.italic, underline = st.underline, strike = st.strike, sup = st.sup, sub = st.sub,
            color = colors.getOrNull(st.color).takeIf { st.color > 0 }?.takeIf { it != "000000" },
            highlight = colors.getOrNull(st.highlight).takeIf { st.highlight > 0 }?.takeIf { it != "FFFFFF" },
            size = (st.size / 2).takeIf { st.size != 24 && st.size != 22 && st.size > 0 },
            font = fonts[st.font].takeIf { st.font != usual && st.font != 0 },
        )
        for (ch in s) {
            text.append(if (ch == '\t') ' ' else ch)
            chars += f
        }
    }
}
