package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.OrtClassifier
import io.github.thepytales.llmhub.data.PiiKind
import io.github.thepytales.llmhub.data.PiiModelInfo
import io.github.thepytales.llmhub.data.PiiRecognizer
import io.github.thepytales.llmhub.data.Pseudonymizer
import io.github.thepytales.llmhub.data.TokenClassifier
import io.github.thepytales.llmhub.data.WordPiece
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Recognition model of the pseudonymization (AP-67, Stufe f): the tokenizer of the app against the one the model was
 * trained with, the decoding of the labels, the rules alone without the model – and, with PII_MODELL (folder with
 * pii-modell.int8.onnx) and PII_ORT (the ONNX Runtime library), the real model on the Messkorpus as in the app.
 */
class PiiModelTest {
    private var settings = AppSettings(pseudonymize = true)

    private fun pseudonymizer(): Pseudonymizer {
        val file = File.createTempFile("pseudonyms", ".json").apply { delete() }
        return Pseudonymizer(file) { settings }
    }

    @Test
    fun tokenizerIsTheOneOfTraining() {
        val wp = WordPiece(PiiModelInfo.vocab)
        val lines = javaClass.getResourceAsStream("/pii/tokenizer-erwartet.jsonl")!!.bufferedReader().readLines()
        var wrong = 0
        for (line in lines) {
            val o = Json.parseToJsonElement(line).jsonObject
            val text = o.getValue("text").jsonPrimitive.content
            val ids = o.getValue("ids").jsonArray.map { it.jsonPrimitive.int }
            val offsets = o.getValue("offsets").jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.int } }
            val got = wp.tokenize(text)
            if (got.map { it.id } != ids || got.map { listOf(it.start, it.end) } != offsets) {
                wrong++
                if (wrong <= 3) println("anders: „${text.take(60)}“\n  erwartet ${ids.take(20)}\n  bekommen ${got.map { it.id }.take(20)}")
            }
        }
        assertEquals(0, wrong, "Tokenizer weicht bei $wrong von ${lines.size} Texten ab")
    }

    /** A classifier that labels the tokens of the given phrases as a person (B- on the first token, I- on the rest). */
    private fun fake(text: String, phrases: Set<String>): TokenClassifier {
        val tokens = WordPiece(PiiModelInfo.vocab).tokenize(text)
        val ranges = phrases.flatMap { w -> Regex("(?<!\\p{L})${Regex.escape(w)}(?!\\p{L})").findAll(text).map { it.range }.toList() }
        return TokenClassifier { ids ->
            val labels = PiiModelInfo.LABELS.size
            FloatArray(ids.size * labels).also { p ->
                for (i in ids.indices) {
                    val t = tokens.getOrNull(i - 1)
                    val r = t?.let { tok -> ranges.firstOrNull { tok.start >= it.first && tok.end <= it.last + 1 } }
                    if (t == null || r == null) { p[i * labels] = 0.99f; continue }
                    p[i * labels] = 0.01f
                    p[i * labels + if (t.start == r.first) 1 else 2] = 0.97f
                }
            }
        }
    }

    @Test
    fun labelsBecomeWholeWordsAndWellKnownNamesStay() {
        val text = "Gestern kam Jolina Kretzschmann zur Lesung über Angela Merkel, und Annas Mutter auch."
        val r = PiiRecognizer(fake(text, setOf("Jolina Kretzschmann", "Angela Merkel", "Annas")))
        val hits = r.hits(text)
        val values = hits.map { it.value }
        assertTrue("Jolina Kretzschmann" in values && "Kretzschmann" in values, values.toString())
        assertFalse(values.any { "Merkel" in it }, "bekannte Namen bleiben: $values")
        assertTrue("Anna" in values, "Genitiv auf die Grundform: $values")
        assertTrue(hits.all { it.kind == PiiKind.NAME })
    }

    @Test
    fun withoutTheModelTheRulesWorkAsBefore() {
        val text = "Gestern sprach ich mit Jolina Kretzschmann über die Ausstellung zu Käthe Kollwitz."
        val alone = pseudonymizer().mask(text)
        val withNull = pseudonymizer().apply { model = { null } }.mask(text)
        assertEquals(alone, withNull, "ohne geladenes Modell: wie bisher")
        // With a model that finds nothing the first-name guesses are off: the rules keep their clear clues only.
        val quiet = pseudonymizer().apply { model = { emptyList() } }.mask("Frau Kretzschmann und Käthe Kollwitz")
        assertFalse("Kretzschmann" in quiet, quiet)
        assertTrue("Käthe Kollwitz" in quiet, quiet)
    }

    @Test
    fun realModelOnTheMesskorpus() {
        val dir = System.getenv("PII_MODELL")?.let(::File)?.takeIf { File(it, PiiModelInfo.FILE).isFile } ?: return
        val lib = System.getenv("PII_ORT")?.let(::File)?.takeIf { it.isFile } ?: return
        OrtClassifier(lib, File(dir, PiiModelInfo.FILE)).use { cl ->
            val recognizer = PiiRecognizer(cl)
            val report = StringBuilder()
            for (section in listOf("entwicklung", "pruef")) {
                val score = PseudonymCorpus.Score()
                var ms = 0L
                for (s in PseudonymCorpus.load(section)) {
                    val p = pseudonymizer().apply { model = { t -> val t0 = System.nanoTime(); recognizer.hits(t).also { ms += (System.nanoTime() - t0) / 1_000_000 } } }
                    score.add(s, PseudonymCorpus.replacedWords(s.text, p.mask(s.text)))
                }
                report.append(score.report("App: Regeln + Erkennungsmodell, $section"))
                if (section == "entwicklung") {
                    report.appendLine("  übersehen:"); score.missed.forEach { report.appendLine("    $it") }
                    report.appendLine("  fälschlich ersetzt:"); score.falseAlarms.forEach { report.appendLine("    $it") }
                }
                report.appendLine("  Modell insgesamt $ms ms")
                if (section == "pruef") {
                    val (found, total) = score.recall()
                    assertTrue(found * 100 >= total * 98, "Prüfteil: $found/$total gefunden")
                }
            }
            File("build/pii").mkdirs()
            File("build/pii/app-mit-modell.txt").writeText(report.toString())
            println(report)
        }
    }
}
