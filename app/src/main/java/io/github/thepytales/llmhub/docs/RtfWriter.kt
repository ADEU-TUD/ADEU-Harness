package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks

/** RTF from Markdown (opens in Word, WordPad and LibreOffice): headings, lists, tables, quotes, code, bold and italic. */
object RtfWriter {
    /** Non-ASCII as \uN? so the file stays 7-bit; braces and backslashes escaped. */
    fun esc(s: String): String = buildString {
        for (ch in s) {
            when {
                ch == '\\' || ch == '{' || ch == '}' -> append('\\').append(ch)
                ch == '\t' -> append("\\tab ")
                ch == '\n' -> append("\\line ")
                ch.code < 0x20 -> {}
                ch.code < 0x80 -> append(ch)
                else -> append("\\u").append(ch.code.toShort().toInt()).append('?')
            }
        }
    }

    private fun inline(text: String): String = inlineSegments(text).joinToString("") { s ->
        var t = esc(s.text)
        if (s.code) t = "{\\f1 $t}"
        if (s.bold) t = "{\\b $t}"
        if (s.italic) t = "{\\i $t}"
        if (s.strike) t = "{\\strike $t}"
        if (s.link != null) t = "{\\field{\\*\\fldinst{HYPERLINK \"${esc(s.link)}\"}}{\\fldrslt{\\ul\\cf1 $t}}}"
        t
    }

    fun write(title: String, markdown: String): String = buildString {
        append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\uc1")
        append("{\\fonttbl{\\f0\\fswiss\\fcharset0 Arial;}{\\f1\\fmodern\\fcharset0 Consolas;}}")
        append("{\\colortbl;\\red0\\green84\\blue159;\\red128\\green128\\blue128;}")
        append("{\\info{\\title ").append(esc(title)).append("}}")
        append("\\paperw11906\\paperh16838\\margl1417\\margr1417\\margt1417\\margb1134\\f0\\fs22\n")
        for (b in parseBlocks(ChartBlocks.asTables(DocumentWriter.withTitle(title, markdown)))) {
            when (b) {
                is Block.Heading -> {
                    val size = listOf(36, 30, 26, 24, 22, 22)[b.level - 1]
                    append("{\\pard\\keepn\\sb240\\sa120\\outlinelevel${b.level - 1}\\b\\fs$size ").append(inline(b.text)).append("\\par}\n")
                }
                is Block.Paragraph -> append("{\\pard\\sa120 ").append(inline(b.text.replace(Regex(" {2}\n"), "\n"))).append("\\par}\n")
                is Block.Quote -> append("{\\pard\\li567\\sa120\\i ").append(inline(b.text)).append("\\par}\n")
                is Block.Code -> append("{\\pard\\sa120\\f1\\fs19 ").append(esc(b.code)).append("\\par}\n")
                is Block.Rule -> append("{\\pard\\brdrb\\brdrs\\brdrw10\\brsp20 \\par}\n")
                is Block.Item -> {
                    val indent = 360 + b.indent * 360
                    val marker = if (b.marker == "•") "\\u8226?" else esc(b.marker)
                    append("{\\pard\\fi-360\\li$indent\\sa60 ").append(marker).append("\\tab ").append(inline(b.text)).append("\\par}\n")
                }
                is Block.Table -> {
                    val width = b.rows.maxOfOrNull { it.size } ?: continue
                    val cellWidth = 9072 / width
                    b.rows.forEachIndexed { r, row ->
                        append("\\trowd\\trgaph70")
                        for (c in 1..width) append("\\clbrdrt\\brdrs\\clbrdrl\\brdrs\\clbrdrb\\brdrs\\clbrdrr\\brdrs\\cellx").append(c * cellWidth)
                        append('\n')
                        for (c in 0 until width) {
                            append("\\pard\\intbl ").append(if (r == 0) "{\\b " else "{").append(inline(row.getOrElse(c) { "" })).append("}\\cell ")
                        }
                        append("\\row\n")
                    }
                    append("\\pard\\sa120\\par\n")
                }
            }
        }
        append("}")
    }
}
