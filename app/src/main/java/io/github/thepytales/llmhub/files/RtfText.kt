package io.github.thepytales.llmhub.files

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Plain text of an RTF document: control words and hidden groups (font and colour tables, style sheets, document
 * information, pictures, field instructions) are dropped, \'hh is decoded in the document's code page (Windows-1252
 * by default) and \uN as Unicode; paragraphs become line breaks, table cells are separated by " | ".
 */
object RtfText {
    private val SKIP = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "objdata", "themedata", "colorschememapping", "datastore",
        "latentstyles", "listtable", "listoverridetable", "rsidtbl", "generator", "xmlnstbl", "mmathPr", "fldinst", "bkmkstart",
        "bkmkend", "pgdsctbl", "revtbl", "filetbl", "nonshppict", "shpinst", "userprops", "docvar", "wgrffmtfilter", "pnseclvl",
        "header", "headerl", "headerr", "headerf", "footer", "footerl", "footerr", "footerf", "operator", "author", "title",
        "comment", "doccomm", "keywords", "subject", "company", "creatim", "revtim", "printim", "buptim", "atnid", "atnauthor",
        "annotation", "protusertbl", "listpicture", "fchars", "lchars", "xe", "tc", "template", "passwordhash", "ftnsep",
        "ftnsepc", "aftnsep", "aftnsepc", "blipuid", "sp", "sn", "sv",
    )

    private val SYMBOLS = mapOf(
        "par" to "\n", "line" to "\n", "sect" to "\n\n", "page" to "\n\n", "tab" to "\t", "cell" to " | ", "nestcell" to " | ",
        "row" to "\n", "nestrow" to "\n", "emdash" to "—", "endash" to "–", "bullet" to "•", "lquote" to "‘", "rquote" to "’",
        "ldblquote" to "“", "rdblquote" to "”", "emspace" to " ", "enspace" to " ", "qmspace" to " ", "zwnj" to "", "zwj" to "",
    )

    fun isRtf(bytes: ByteArray): Boolean = bytes.size > 5 && String(bytes, 0, 5, Charsets.ISO_8859_1) == "{\\rtf"

    fun text(bytes: ByteArray): String = text(String(bytes, Charsets.ISO_8859_1))

    fun text(src: String): String {
        val out = StringBuilder()
        val pending = ByteArrayOutputStream()
        var charset: Charset = Charset.forName("windows-1252")
        // Per group: whether its text is hidden, and how many characters follow \uN as fallback.
        var skip = false
        var uc = 1
        val stack = ArrayDeque<Pair<Boolean, Int>>()
        var toSkip = 0
        var i = 0
        val n = src.length

        fun flush() {
            if (pending.size() > 0) {
                out.append(String(pending.toByteArray(), charset))
                pending.reset()
            }
        }

        fun emit(s: String) {
            if (skip) return
            flush()
            out.append(s)
        }

        while (i < n) {
            val ch = src[i]
            when (ch) {
                '{' -> {
                    flush(); stack.addLast(skip to uc); toSkip = 0; i++
                }
                '}' -> {
                    flush(); stack.removeLastOrNull()?.let { (s, u) -> skip = s; uc = u }; toSkip = 0; i++
                }
                '\\' -> {
                    if (i + 1 >= n) { i++; continue }
                    val c = src[i + 1]
                    when {
                        c.isLetter() -> {
                            var j = i + 1
                            while (j < n && src[j].isLetter() && j - i < 40) j++
                            val word = src.substring(i + 1, j)
                            var k = j
                            if (k < n && (src[k] == '-' || src[k].isDigit())) {
                                k++
                                while (k < n && src[k].isDigit()) k++
                            }
                            val param = src.substring(j, k).toIntOrNull()
                            if (k < n && src[k] == ' ') k++
                            i = k
                            if (toSkip > 0 && word != "u") { toSkip--; continue }
                            when {
                                word in SKIP -> skip = true
                                word == "u" -> {
                                    val code = (param ?: 0).let { if (it < 0) it + 65536 else it }
                                    emit(code.toChar().toString())
                                    toSkip = uc
                                }
                                word == "uc" -> uc = param ?: 1
                                word == "ansicpg" -> param?.let { cp -> runCatching { Charset.forName("windows-$cp") }.getOrNull()?.let { flush(); charset = it } }
                                word == "bin" -> i += (param ?: 0).coerceAtLeast(0)
                                else -> SYMBOLS[word]?.let(::emit)
                            }
                        }
                        c == '\'' -> {
                            val hex = src.substring(minOf(n, i + 2), minOf(n, i + 4)).toIntOrNull(16)
                            i += 4
                            if (toSkip > 0) { toSkip--; continue }
                            if (hex != null && !skip) pending.write(hex)
                        }
                        c == '*' -> { skip = true; i += 2 }
                        c == '~' -> { emit(" "); i += 2 }
                        c == '_' -> { emit("-"); i += 2 }
                        c == '-' -> i += 2
                        c == '\n' || c == '\r' -> { emit("\n"); i += 2 }
                        else -> { emit(c.toString()); i += 2 }
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (toSkip > 0) toSkip-- else emit(ch.toString())
                    i++
                }
            }
        }
        flush()
        return out.toString().lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
