package io.github.thepytales.llmhub

import java.io.File

/**
 * The test corpus of the pseudonymization (AP-67): tools/pii/messkorpus, format in its README. The measures are the
 * same as in tools/pii/corpus.py, so the rules of the app and a Python model are measured alike: words, core words of
 * a detail, and the replaced words found by aligning the original with the masked text.
 */
object PseudonymCorpus {
    class Span(val text: String, val kind: String, val private: Boolean, val words: List<Int>)

    class Sample(val id: String, val section: String, val text: String, val spans: List<Span>, val words: List<String>)

    /** Kinds the recognition model learns (MUSTER stays with the rules). */
    val MODEL_KINDS = setOf("PER", "EINR", "ORT")
    private val PRIVATE_KINDS = setOf("PER", "EINR", "ORT", "MUSTER")
    private val PUBLIC_KINDS = setOf("AUT", "OEFF", "EINR", "ORT", "WORT")
    private val SECTIONS = mapOf("prüfteil" to "pruef", "entwicklung" to "entwicklung")

    /** A word: letters/digits joined by hyphen, apostrophe, dot or @ – the same as WORD in corpus.py. */
    private val WORD = Regex("[\\p{L}\\p{N}]+(?:[-'’.@][\\p{L}\\p{N}]+)*")
    private val MARKUP = Regex("\\[\\[(.+?)\\|([A-Z]+)]]|\\{\\{(.+?)\\|([A-Z]+)}}")

    /** General words that need not be replaced inside a detail – the same as GENERAL in corpus.py. */
    private val GENERAL = """
        am an auf der die das des dem den im in zum zur von vom und bei
        e.V Dr Prof Frau Herr Herrn Familie Oma Opa
        Grundschule Oberschule Gymnasium Schule Kita Kindertagesstätte Kindergarten Hort Förderschule Förderzentrum
        Gesamtschule Berufsschule Mittelschule Realschule Schulzentrum Montessori-Schule Freie Freien Evangelisches
        Praxis Ergotherapiepraxis Musikschule Beratungsstelle Jugendhaus Verein SV
    """.trim().split(Regex("\\s+")).map { it.lowercase() }.toSet()

    /** The corpus folder, found from the working directory of the test (desktop/) upwards. */
    fun dir(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            File(d, "tools/pii/messkorpus").takeIf { it.isDirectory }?.let { return it }
            d = d.parentFile
        }
        error("tools/pii/messkorpus nicht gefunden")
    }

    fun words(text: String): List<Pair<IntRange, String>> = WORD.findAll(text).map { it.range to it.value }.toList()

    /** All samples, or only those of [section] ("pruef" or "entwicklung"). */
    fun load(section: String? = null): List<Sample> {
        val out = mutableListOf<Sample>()
        for (file in dir().listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }) {
            var current: String? = null
            var n = 0
            val block = mutableListOf<String>()
            fun flush() {
                val s = current
                if (block.isNotEmpty() && s != null) out += parse(block.joinToString("\n"), "${file.nameWithoutExtension}:$s:${++n}", s)
                block.clear()
            }
            for (line in file.readLines()) {
                val t = line.trim()
                when {
                    t.startsWith("#") -> Unit
                    t.startsWith("== ") && t.endsWith(" ==") -> {
                        flush()
                        current = SECTIONS.getValue(t.trim('=', ' ').lowercase())
                        n = 0
                    }
                    t.isEmpty() -> flush()
                    else -> block += line.trimEnd()
                }
            }
            flush()
        }
        return out.filter { section == null || it.section == section }
    }

    private fun parse(raw: String, id: String, section: String): Sample {
        val plain = StringBuilder()
        val marks = mutableListOf<Triple<IntRange, String, Boolean>>()
        var pos = 0
        for (m in MARKUP.findAll(raw)) {
            plain.append(raw, pos, m.range.first)
            val private = m.groups[1] != null
            val content = if (private) m.groupValues[1] else m.groupValues[3]
            val kind = if (private) m.groupValues[2] else m.groupValues[4]
            require(kind in (if (private) PRIVATE_KINDS else PUBLIC_KINDS)) { "$id: Art $kind passt nicht zu ${m.value}" }
            marks += Triple(plain.length until plain.length + content.length, kind, private)
            plain.append(content)
            pos = m.range.last + 1
        }
        plain.append(raw, pos, raw.length)
        val text = plain.toString()
        val ws = words(text)
        val spans = marks.map { (range, kind, private) ->
            val inside = ws.indices.filter { i -> ws[i].first.first >= range.first && ws[i].first.last <= range.last }
            require(inside.isNotEmpty()) { "$id: Angabe ohne Wort" }
            Span(text.substring(range), kind, private, inside)
        }
        return Sample(id, section, text, spans, ws.map { it.second })
    }

    /** The words of [span] that must be replaced: all but single letters and general ones (all, if nothing is left). */
    fun coreWords(span: Span, words: List<String>): List<Int> =
        span.words.filter { words[it].length > 1 && words[it].lowercase() !in GENERAL }.ifEmpty { span.words }

    /** Indices of the words of [original] that are missing in [masked] (longest common subsequence). */
    fun replacedWords(original: String, masked: String): Set<Int> {
        val a = words(original).map { it.second }
        val b = words(masked).map { it.second }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val kept = HashSet<Int>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { kept += i; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return a.indices.toSet() - kept
    }

    /** Found private details and wrongly replaced public ones, per kind; other replaced words. */
    class Score {
        val found = sortedMapOf<String, IntArray>()
        val wrong = sortedMapOf<String, IntArray>()
        var other = 0
        var otherWords = 0
        val missed = mutableListOf<String>()
        val falseAlarms = mutableListOf<String>()

        fun add(s: Sample, replaced: Set<Int>) {
            val inSpan = HashSet<Int>()
            for (sp in s.spans) {
                inSpan += sp.words
                if (sp.private) {
                    val ok = coreWords(sp, s.words).all { it in replaced }
                    found.getOrPut(sp.kind) { IntArray(2) }.also { it[0] += if (ok) 1 else 0; it[1]++ }
                    if (!ok) missed += "${s.id} ${sp.kind} „${sp.text}“"
                } else {
                    val bad = sp.words.any { it in replaced }
                    wrong.getOrPut(sp.kind) { IntArray(2) }.also { it[0] += if (bad) 1 else 0; it[1]++ }
                    if (bad) falseAlarms += "${s.id} ${sp.kind} „${sp.text}“"
                }
            }
            val rest = s.words.indices.filter { it !in inSpan }
            otherWords += rest.size
            other += rest.count { it in replaced }
        }

        /** Found / total private details of the model kinds (PER, EINR, ORT). */
        fun recall(): Pair<Int, Int> = found.filterKeys { it in MODEL_KINDS }.values.let { v -> v.sumOf { it[0] } to v.sumOf { it[1] } }

        /** Wrongly replaced / total public details. */
        fun falseRate(): Pair<Int, Int> = wrong.values.let { v -> v.sumOf { it[0] } to v.sumOf { it[1] } }

        fun report(title: String): String = buildString {
            fun pct(a: Int, b: Int) = "%.1f".format(java.util.Locale.ROOT, 100.0 * a / maxOf(b, 1))
            appendLine(title)
            val (f, n) = recall()
            appendLine("  private Angaben gefunden (PER/EINR/ORT): $f/$n = ${pct(f, n)} %")
            found.forEach { (k, c) -> appendLine("    ${k.padEnd(6)} ${c[0]}/${c[1]} = ${pct(c[0], c[1])} %") }
            val (w, m) = falseRate()
            appendLine("  öffentliche Angaben fälschlich ersetzt: $w/$m = ${pct(w, m)} %")
            wrong.forEach { (k, c) -> appendLine("    ${k.padEnd(6)} ${c[0]}/${c[1]} = ${pct(c[0], c[1])} %") }
            append("  sonstige Fehlalarme: $other von $otherWords Wörtern = ")
            appendLine("%.1f je 1000".format(java.util.Locale.ROOT, 1000.0 * other / maxOf(otherWords, 1)))
        }
    }
}
