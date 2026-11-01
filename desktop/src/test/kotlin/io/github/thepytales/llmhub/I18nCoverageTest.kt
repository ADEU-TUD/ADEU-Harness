package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.ui.I18n
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * English interface (AP-29): every German text the code marks for translation – `tr("…")`, `trLater("…")` and
 * `Text("…")` on the screens of the ui package – must have an English line in i18n/en.tsv. The test reads the Kotlin
 * sources of both platforms, so a new text without a translation is reported with file and line.
 */
class I18nCoverageTest {
    private val roots = listOf(File("../app/src/main/java"), File("src/main/kotlin"))
    /** Also fully qualified calls (`io.github.thepytales.llmhub.ui.tr(…)` in files of other packages). */
    private val call = Regex("""(?<![\w.])(?:io\.github\.thepytales\.llmhub\.ui\.)?(tr|trLater|Text)\(""")

    data class Found(val text: String, val where: String, val template: Boolean)

    @Test
    fun everyMarkedTextHasAnEnglishTranslation() {
        val en = I18n.EN
        val missing = mutableListOf<String>()
        val templates = mutableListOf<String>()
        for (f in found()) {
            if (f.template) {
                templates += "${f.where}: Platzhalter {0} statt \$… verwenden: ${f.text.take(80)}"
                continue
            }
            if (f.text in en) continue
            val parts = f.text.split(" › ")
            if (parts.size > 1 && parts.all { it.isEmpty() || it in en }) continue
            missing += "${f.where}: ${f.text.take(100)}"
        }
        assertTrue(templates.isEmpty(), "tr()/trLater() mit \$-Vorlage (wird nie gefunden):\n" + templates.joinToString("\n"))
        assertTrue(missing.isEmpty(), "${missing.size} Text(e) ohne englische Fassung in i18n/en.tsv:\n" + missing.distinct().take(200).joinToString("\n"))
    }

    @Test
    fun englishTextsKeepPlaceholders() {
        val ph = Regex("""\{\d}""")
        val wrong = I18n.EN.filter { (de, en) -> ph.findAll(de).map { it.value }.toSet() != ph.findAll(en).map { it.value }.toSet() }
        assertTrue(wrong.isEmpty(), "Platzhalter passen nicht:\n" + wrong.entries.joinToString("\n") { "${it.key} → ${it.value}" })
        val blank = I18n.EN.filter { it.value.isBlank() && it.key.isNotBlank() }
        assertTrue(blank.isEmpty(), "Leere Übersetzungen: " + blank.keys)
    }

    @Test
    fun scannerFindsMarkedTexts() {
        val src = """
            val a = tr("Eins")
            val b = trLater("Zwei " +
                "Drei")
            Text(tr(if (x) "Vier" else "Fünf"))
            val c = tr("Sechs {0}", name)
            val e = io.github.thepytales.llmhub.ui.tr("Sieben")
            val d = tr("Bitte ${'$'}name")
        """.trimIndent()
        val texts = scan(src, "x.kt", ui = true).map { it.text to it.template }
        assertTrue(("Eins" to false) in texts && ("Zwei Drei" to false) in texts, texts.toString())
        assertTrue(("Vier" to false) in texts && ("Fünf" to false) in texts && ("Sechs {0}" to false) in texts && ("Sieben" to false) in texts, texts.toString())
        assertTrue(texts.any { it.second }, texts.toString())
    }

    private fun found(): List<Found> = roots.flatMap { root ->
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val src = f.readText()
            val ui = src.take(300).contains("package io.github.thepytales.llmhub.ui\n") && "import androidx.compose.material3.Text\n" !in src
            scan(src, f.path, ui)
        }.toList()
    }

    /** Marked texts in [src]: literals of the first argument outside nested parentheses, `"a" + "b"` joined. */
    private fun scan(src: String, path: String, ui: Boolean): List<Found> {
        val out = mutableListOf<Found>()
        for (m in call.findAll(src)) {
            val kind = m.groupValues[1]
            if (kind == "Text" && !ui) continue
            val lineStart = src.lastIndexOf('\n', m.range.first) + 1
            val before = src.substring(lineStart, m.range.first).trimStart()
            if (before.startsWith("//") || before.startsWith("*") || before.startsWith("/*") || before.endsWith("fun ")) continue
            val line = src.substring(0, m.range.first).count { it == '\n' } + 1
            for ((text, template) in firstArgument(src, m.range.last + 1)) {
                if (template) {
                    // Text("${n} Wörter") shows something computed; only tr()/trLater() must not contain templates.
                    if (kind != "Text") out += Found(text, "$path:$line", true)
                    continue
                }
                if (text.any { it.isLetter() }) out += Found(text, "$path:$line", false)
            }
        }
        return out
    }

    private fun firstArgument(src: String, start: Int): List<Pair<String, Boolean>> {
        val lits = mutableListOf<Pair<String, Boolean>>()
        var i = start
        var paren = 0
        var brace = 0
        var lastEnd = -1
        while (i < src.length) {
            val c = src[i]
            if (c == '/' && src.startsWith("//", i)) { i = src.indexOf('\n', i).let { if (it < 0) src.length else it }; continue }
            if (c == '"') {
                val (lit, end, template) = literal(src, i)
                if (lit != null && paren == 0) {
                    val between = if (lastEnd >= 0) src.substring(lastEnd, i) else null
                    if (between != null && between.trim() == "+" && lits.isNotEmpty()) {
                        val (prev, t) = lits.removeAt(lits.lastIndex)
                        lits += (prev + lit) to (t || template)
                    } else lits += lit to template
                    lastEnd = end
                }
                i = end
                continue
            }
            when (c) {
                '(', '[' -> paren++
                ')', ']' -> { if (paren == 0) return lits; paren-- }
                '{' -> brace++
                '}' -> brace--
                ',' -> if (paren == 0 && brace == 0) return lits
            }
            i++
        }
        return lits
    }

    /** The string literal starting at [i] (a quote): its text, the index after it and whether it has a `$` template. */
    private fun literal(src: String, i: Int): Triple<String?, Int, Boolean> {
        if (src.startsWith("\"\"\"", i)) {
            val e = src.indexOf("\"\"\"", i + 3)
            return Triple(null, if (e < 0) src.length else e + 3, false)
        }
        val out = StringBuilder()
        var j = i + 1
        var template = false
        while (j < src.length) {
            val c = src[j]
            when {
                c == '\\' -> {
                    when (val n = src[j + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> { out.append(src.substring(j + 2, j + 6).toInt(16).toChar()); j += 4 }
                        else -> out.append(n)
                    }
                    j += 2
                }
                c == '$' && j + 1 < src.length && (src[j + 1] == '{' || src[j + 1].isLetter() || src[j + 1] == '_') -> {
                    template = true
                    if (src[j + 1] == '{') {
                        var depth = 0
                        var k = j + 1
                        while (k < src.length) {
                            if (src[k] == '{') depth++
                            else if (src[k] == '}') { depth--; if (depth == 0) break }
                            else if (src[k] == '"') { k = literal(src, k).second; continue }
                            k++
                        }
                        out.append(src, j, k + 1)
                        j = k + 1
                    } else { out.append(c); j++ }
                }
                c == '"' -> return Triple(out.toString(), j + 1, template)
                c == '\n' -> return Triple(null, j, false)
                else -> { out.append(c); j++ }
            }
        }
        return Triple(null, j, false)
    }
}
