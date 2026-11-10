package io.github.thepytales.llmhub

import java.io.File

/**
 * The test corpus of the command model (AP-71): tools/befehle/messkorpus, format in its README. The measures are the
 * same as in tools/befehle/corpus.py, so the rules of the app, a Python model and the model in the app are measured alike.
 */
object CommandCorpus {
    class Sample(val id: String, val section: String, val text: String, val gold: List<String>, val area: String, val values: Map<String, String>) {
        val free: Boolean get() = gold.first().startsWith("frei")
    }

    private val SECTIONS = mapOf("prüfteil" to "pruef", "entwicklung" to "entwicklung")
    private val FREE = listOf("frei.einfach", "frei.komplex")

    fun dir(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            File(d, "tools/befehle/messkorpus").takeIf { it.isDirectory }?.let { return it }
            d = d.parentFile
        }
        error("tools/befehle/messkorpus nicht gefunden")
    }

    fun load(section: String? = null): List<Sample> {
        val out = mutableListOf<Sample>()
        for (file in dir().listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }) {
            var current: String? = null
            var n = 0
            for (raw in file.readLines()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                Regex("==\\s*(.+?)\\s*==").matchEntire(line)?.let { current = SECTIONS.getValue(it.groupValues[1].lowercase()) }?.also { return@also } ?: run {
                    n++
                    val at = line.lastIndexOf("=>")
                    val parts = line.substring(at + 2).split(" ; ").map { it.trim() }
                    val head = parts[0].split(Regex("\\s+"))
                    val gold = head[0].split('|').flatMap { if (it == "frei") FREE else listOf(it) }
                    val area = head.drop(1).firstOrNull { it.startsWith("@") }?.drop(1) ?: "app"
                    val values = parts.drop(1).filter { it.isNotEmpty() }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                    if (section == null || section == current) out += Sample("${file.nameWithoutExtension}:$n", current!!, line.substring(0, at).trim(), gold, area, values)
                }
            }
        }
        return out
    }

    /** Counts as in corpus.py: [add] takes the label and whether the recognition is sure enough to act. */
    class Score {
        var commands = 0; var hit = 0; var passed = 0; var wrong = 0
        var free = 0; var freePassed = 0; var freeWrong = 0; var freeKind = 0; var freeKindHit = 0
        var valued = 0; var valuesRight = 0
        val errors = mutableListOf<String>()

        fun add(s: Sample, label: String, sure: Boolean) {
            val act = sure && !label.startsWith("frei")
            if (s.free) {
                free++
                if (act && label !in s.gold) { freeWrong++; errors += "${s.id}  ${s.text}  [${s.gold.joinToString("/")}] → $label (frei → ausgeführt)" } else freePassed++
                val kinds = s.gold.filter { it.startsWith("frei") }
                if (kinds.size == 1) { freeKind++; if (label == kinds[0]) freeKindHit++ }
            } else {
                commands++
                when {
                    act && label in s.gold -> hit++
                    act -> { wrong++; errors += "${s.id}  ${s.text}  [${s.gold.joinToString("/")}] → $label (falsch ausgeführt)" }
                    else -> { passed++; errors += "${s.id}  ${s.text}  [${s.gold.joinToString("/")}] → $label (weitergegeben)" }
                }
            }
        }

        val total get() = commands + free
        private fun pct(a: Int, b: Int) = "%.1f".format(java.util.Locale.ROOT, 100.0 * a / maxOf(b, 1))

        fun report(title: String): String = buildString {
            appendLine(title)
            appendLine("  Befehle: ${pct(hit, commands)} % getroffen ($hit/$commands), ${pct(passed, commands)} % weitergegeben")
            appendLine("  falsch ausgeführt: ${pct(wrong + freeWrong, total)} % aller Eingaben (${wrong + freeWrong}/$total)")
            appendLine("  freie Anfragen: ${pct(freePassed, free)} % weitergegeben ($freePassed/$free), Art (einfach/komplex) richtig: ${pct(freeKindHit, freeKind)} %")
            if (valued > 0) appendLine("  Werte richtig: ${pct(valuesRight, valued)} % ($valuesRight/$valued)")
        }
    }
}
