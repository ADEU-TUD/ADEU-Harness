package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.Block
import io.github.thepytales.llmhub.ui.inlineSegments
import io.github.thepytales.llmhub.ui.parseBlocks

/** LaTeX from Markdown: a complete document (KOMA-Script article, German) with headings, lists, tables and code. */
object LatexWriter {
    private val SPECIAL = mapOf(
        '\\' to "\\textbackslash{}", '{' to "\\{", '}' to "\\}", '$' to "\\$", '&' to "\\&", '#' to "\\#", '%' to "\\%",
        '_' to "\\_", '^' to "\\textasciicircum{}", '~' to "\\textasciitilde{}", '<' to "\\textless{}", '>' to "\\textgreater{}",
    )

    fun esc(s: String): String = buildString { for (ch in s) append(SPECIAL[ch] ?: ch.toString()) }

    /** Formulas in $…$ stay as they are; the rest is escaped. */
    private fun inline(text: String): String {
        val parts = Regex("(?<!\\\\)\\$[^$\\n]+\\$").split(text)
        val formulas = Regex("(?<!\\\\)\\$[^$\\n]+\\$").findAll(text).map { it.value }.toList()
        return parts.mapIndexed { i, p -> segments(p) + (formulas.getOrNull(i) ?: "") }.joinToString("")
    }

    private fun segments(text: String): String = inlineSegments(text).joinToString("") { s ->
        var t = if (s.code) "\\texttt{${esc(s.text)}}" else esc(s.text).replace("„", "\\glqq{}").replace("“", "\\grqq{}")
        if (s.bold) t = "\\textbf{$t}"
        if (s.italic) t = "\\emph{$t}"
        if (s.strike) t = "\\sout{$t}"
        if (s.link != null) t = "\\href{${s.link.replace("%", "\\%").replace("#", "\\#")}}{$t}"
        t
    }

    fun write(title: String, markdown: String): String = buildString {
        val blocks = parseBlocks(ChartBlocks.asTables(markdown))
        val hasTitle = blocks.firstOrNull().let { it is Block.Heading && it.level == 1 }
        val docTitle = (blocks.firstOrNull() as? Block.Heading)?.takeIf { it.level == 1 }?.text ?: title
        append("\\documentclass[11pt,a4paper]{scrartcl}\n")
        append("\\usepackage[T1]{fontenc}\n\\usepackage[utf8]{inputenc}\n\\usepackage[ngerman]{babel}\n\\usepackage{lmodern}\n")
        append("\\usepackage{booktabs}\n\\usepackage{tabularx}\n\\usepackage{listings}\n\\usepackage[normalem]{ulem}\n\\usepackage{amsmath}\n")
        append("\\usepackage[hidelinks]{hyperref}\n\\lstset{basicstyle=\\ttfamily\\small,breaklines=true,frame=single}\n\n")
        append("\\title{").append(esc(docTitle)).append("}\n\\date{}\n\n\\begin{document}\n")
        if (docTitle.isNotBlank()) append("\\maketitle\n\n")
        val list = ArrayDeque<String>()
        fun closeLists(toDepth: Int) { while (list.size > toDepth) append("\\end{").append(list.removeLast()).append("}\n") }
        for ((i, b) in blocks.withIndex()) {
            if (i == 0 && hasTitle) continue
            if (b !is Block.Item) closeLists(0)
            when (b) {
                is Block.Heading -> {
                    val cmd = when (if (hasTitle) b.level - 1 else b.level) { 0, 1 -> "section"; 2 -> "subsection"; 3 -> "subsubsection"; 4 -> "paragraph"; else -> "subparagraph" }
                    append("\\").append(cmd).append("{").append(inline(b.text)).append("}\n\n")
                }
                is Block.Paragraph -> append(inline(b.text).replace(Regex(" {2}\n"), "\\\\\\\\\n")).append("\n\n")
                is Block.Quote -> append("\\begin{quote}\n").append(inline(b.text)).append("\n\\end{quote}\n\n")
                is Block.Code -> {
                    val lang = mapOf("python" to "Python", "py" to "Python", "java" to "Java", "r" to "R", "c" to "C", "cpp" to "C++", "sql" to "SQL", "bash" to "bash", "sh" to "bash", "matlab" to "Matlab")[b.lang.lowercase()]
                    append("\\begin{lstlisting}").append(lang?.let { "[language=$it]" }.orEmpty()).append('\n').append(b.code).append("\n\\end{lstlisting}\n\n")
                }
                is Block.Rule -> append("\\noindent\\rule{\\textwidth}{0.4pt}\n\n")
                is Block.Table -> {
                    val width = b.rows.maxOfOrNull { it.size } ?: continue
                    append("\\begin{table}[h]\n\\centering\n\\begin{tabularx}{\\textwidth}{").append("X".repeat(width)).append("}\n\\toprule\n")
                    b.rows.forEachIndexed { r, row ->
                        append((0 until width).joinToString(" & ") { c -> inline(row.getOrElse(c) { "" }).let { if (r == 0) "\\textbf{$it}" else it } })
                        append(" \\\\\n")
                        if (r == 0) append("\\midrule\n")
                    }
                    append("\\bottomrule\n\\end{tabularx}\n\\end{table}\n\n")
                }
                is Block.Item -> {
                    val env = if (b.marker.first().isDigit()) "enumerate" else "itemize"
                    val depth = (b.indent + 1).coerceAtMost(4)
                    closeLists(depth)
                    if (list.size == depth && list.last() != env) closeLists(depth - 1)
                    while (list.size < depth) { append("\\begin{").append(env).append("}\n"); list.addLast(env) }
                    append("\\item ").append(inline(b.text)).append('\n')
                }
            }
        }
        closeLists(0)
        append("\n\\end{document}\n")
    }
}
