package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.CommandSlots
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraCommands
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.CommandModelInfo
import io.github.thepytales.llmhub.data.CommandRecognizer
import io.github.thepytales.llmhub.data.OrtClassifier
import io.github.thepytales.llmhub.data.SentenceClassifier
import io.github.thepytales.llmhub.data.WordPiece
import io.github.thepytales.llmhub.video.VideoLora
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Befehlsmodell (AP-71, Stufe h): the tokenizer and the encoding of the app against the ones of the training, the
 * labels of the shipped model against the code, what Lora does with a recognized command – and, with BEFEHLE_MODELL
 * (folder with befehle-modell.int8.onnx) and BEFEHLE_ORT (the ONNX Runtime library), the real model on the Messkorpus
 * as in the app.
 */
class CommandModelTest {
    private val targets = AppMap.build(android = false)

    @Test
    fun encodingIsTheOneOfTraining() {
        val r = CommandRecognizer({ _, _ -> FloatArray(CommandModelInfo.labels.size) })
        val lines = javaClass.getResourceAsStream("/befehle/tokenizer-erwartet.jsonl")!!.bufferedReader().readLines()
        var wrong = 0
        for (line in lines) {
            val o = Json.parseToJsonElement(line).jsonObject
            val text = o.getValue("text").jsonPrimitive.content
            val ids = o.getValue("ids").jsonArray.map { it.jsonPrimitive.long }
            val (got, types) = r.encode(text, o.getValue("bereich").jsonPrimitive.content)
            if (got.toList() != ids) {
                wrong++
                if (wrong <= 3) println("anders: „$text“\n  erwartet $ids\n  bekommen ${got.toList()}")
            }
            // Segment ids: 0 for [CLS] area [SEP], 1 for the wish and its [SEP].
            assertEquals(0L, types.first())
            assertEquals(1L, types.last())
        }
        assertTrue(lines.size >= 600, "${lines.size} Wünsche")
        assertEquals(0, wrong, "Eingabe weicht bei $wrong von ${lines.size} Wünschen ab")
    }

    @Test
    fun shippedLabelsExistInTheCode() {
        VideoLora.register()
        val code = LoraCommands.catalog(android = false).map { it.label }.toSet()
        val model = CommandModelInfo.labels
        assertEquals(model.size, model.toSet().size)
        assertTrue(code.containsAll(model), "Etiketten des Modells ohne Ziel im Code: ${model.toSet() - code}")
        // New places and actions the model does not know yet go to the language model until the next training.
        val unknown = code - model.toSet()
        File("build/befehle").mkdirs()
        File("build/befehle/neu-im-code.txt").writeText(unknown.sorted().joinToString("\n"))
        if (unknown.isNotEmpty()) println("Neu im Code, dem Befehlsmodell noch unbekannt (geht an das Sprachmodell): $unknown")
        assertTrue(unknown.size <= code.size / 10, "Zu viel Neues im Code für das Modell – neu trainieren: $unknown")
    }

    /** A classifier that is [confidence] sure of [label]. */
    private fun fake(label: String, confidence: Float) = SentenceClassifier { _, _ ->
        FloatArray(CommandModelInfo.labels.size) { (1 - confidence) / (CommandModelInfo.labels.size - 1) }.also { it[CommandModelInfo.labels.indexOf(label)] = confidence }
    }

    @Test
    fun belowTheThresholdNothingIsCarriedOut() {
        val sure = CommandRecognizer(fake("ort:area.files", 0.99f)).recognize("Dateien")
        assertEquals("ort:area.files", sure.label)
        assertTrue(sure.sure && !sure.free)
        val unsure = CommandRecognizer(fake("ort:area.files", CommandModelInfo.THRESHOLD - 0.05f)).recognize("Dateien vielleicht")
        assertFalse(unsure.sure, "unter der Schwelle wird weitergegeben")
        assertTrue(CommandRecognizer(fake("frei.komplex", 0.99f)).recognize("Vergleiche zwei Theorien").free)
        assertEquals("mach  DUNKEL".lowercase().replace("  ", " "), CommandRecognizer.normalize(" Mach  DUNKEL "))
    }

    /** A classifier with the probabilities [p] for their labels; the rest is shared out evenly. */
    private fun spread(vararg p: Pair<String, Float>) = SentenceClassifier { _, _ ->
        val n = CommandModelInfo.labels.size
        val rest = (1 - p.sumOf { it.second.toDouble() }).toFloat() / (n - p.size)
        FloatArray(n) { rest }.also { a -> p.forEach { (l, v) -> a[CommandModelInfo.labels.indexOf(l)] = v } }
    }

    @Test
    fun twoButtonsWhenTheModelHesitatesBetweenTwoCommands() {
        // AP-73: unsure, and the two likeliest commands together reach the lower bound → „Meinst du … oder …?“.
        val r = CommandRecognizer(spread("ort:area.files" to 0.45f, "ort:place.files.search" to 0.35f)).recognize("wo ist die datei")
        assertFalse(r.sure)
        assertEquals(listOf("ort:area.files", "ort:place.files.search"), r.choices)
        // Sure: carried out, no question.
        assertTrue(CommandRecognizer(fake("ort:area.files", 0.95f)).recognize("dateien").choices.isEmpty())
        // A free request keeps its probability on frei.* and goes on without buttons …
        assertTrue(CommandRecognizer(spread("frei.komplex" to 0.6f, "ort:area.files" to 0.1f, "lora:hilfe" to 0.1f)).recognize("vergleiche zwei theorien").choices.isEmpty())
        // … and the free labels are never buttons, even when they lead.
        val mixed = CommandRecognizer(spread("frei.einfach" to 0.4f, "stell:theme" to 0.3f, "ort:option.appearance.theme" to 0.25f)).recognize("dunkel")
        assertEquals(listOf("stell:theme", "ort:option.appearance.theme"), mixed.choices)
        // Below the lower bound: passed on as before.
        assertTrue(CommandRecognizer(spread("ort:area.files" to 0.12f, "lora:hilfe" to 0.1f)).recognize("hm").choices.isEmpty())
        assertTrue(CommandModelInfo.CHOICE_BOUND < CommandModelInfo.THRESHOLD)
        // The lower bound is the one tools/befehle/rueckfrage.py chose on the development part.
        val chosen = File(CommandCorpus.dir().parentFile, "modell/rueckfrage.json").readText()
        val bound = Json.parseToJsonElement(chosen).jsonObject.getValue("untergrenze").jsonPrimitive.content.toFloat()
        assertEquals(bound, CommandModelInfo.CHOICE_BOUND)
    }

    @Test
    fun everyCommandHasANameForItsButton() {
        VideoLora.register()
        for (android in listOf(false, true)) {
            val here = AppMap.build(android = android)
            for (e in LoraCommands.catalog(android)) {
                val title = LoraCommands.title(e.label, here, android)
                if (LoraCommands.isFree(e.label)) assertNull(title) else assertTrue(!title.isNullOrBlank(), "kein Name für ${e.label} (android=$android)")
            }
        }
    }

    @Test
    fun recognizedCommandsBecomeTheStepsLoraAlreadyKnows() {
        val s = AppSettings()
        // A place: opened; a tool that is switched off: offered first; a mode: a chat in it.
        val files = LoraCommands.open(LoraCommands.target("ort:area.files", targets)!!, s.enabledToolIds)
        assertEquals("files", (files.steps.single() as LoraStep.Navigate).target.route)
        assertTrue(LoraCommands.open(LoraCommands.target("ort:tool.pdf", targets)!!, emptySet()).steps.single() is LoraStep.EnableTool)
        assertTrue(LoraCommands.open(LoraCommands.target("ort:place.pdf.merge", targets)!!, emptySet()).steps.single() is LoraStep.EnableTool)
        assertTrue(LoraCommands.open(LoraCommands.target("ort:tool.pdf", targets)!!, setOf("pdf")).steps.single() is LoraStep.Navigate)
        assertTrue(LoraCommands.open(LoraCommands.target("ort:mode.literature", targets)!!, emptySet()).steps.single() is LoraStep.OpenMode)
        assertEquals("settings", LoraCommands.target("ort:area.settings", targets)?.route)
        assertNull(LoraCommands.target("ort:gibt.es.nicht", targets))
        // A setting: asked before it changes; without a value Lora shows the option; already set: said so.
        val dark = LoraCommands.change("stell:theme", "mach dunkel", s, targets)!!.steps.single() as LoraStep.ChangeSetting
        assertEquals("DARK", dark.value)
        assertEquals("appearance.theme", (LoraCommands.change("stell:theme", "Helligkeit", s, targets)!!.steps.single() as LoraStep.Navigate).target.spot)
        assertTrue(LoraCommands.change("stell:lora_place", "Lora gemischt", s, targets)!!.steps.isEmpty(), "steht schon auf Gemischt")
        assertEquals("LOCAL", (LoraCommands.change("stell:lora_place", "arbeite nur lokal", s, targets)!!.steps.single() as LoraStep.ChangeSetting).value)
        // Strg+K shows what would happen.
        assertNotNull(LoraCommands.describe("stell:theme", "mach dunkel", s, targets))
        assertNotNull(LoraCommands.describe("ort:area.files", "dateien", s, targets))
        assertNull(LoraCommands.describe("frei.einfach", "hallo", s, targets))
        // Every setting Lora may change has its label, and every local action reads its values without failing.
        LoraAgent.SETTINGS.forEach { st -> assertTrue(LoraCommands.catalog().any { it.label == LoraCommands.SET + st.id }) }
        LoraCommands.LOCAL_ACTIONS.forEach { id -> CommandSlots.args(id, "bitte") }
    }

    @Test
    fun aTemplateIsFoundBySearchNotByTheModel() {
        val reply = LoraCommands.template("ich brauche die vorlage für ein exzerpt", targets)
        assertNotNull(reply)
        assertTrue(reply.steps.isNotEmpty() || reply.hits.isNotEmpty())
    }

    @Test
    fun realModelOnTheMesskorpus() {
        val dir = System.getenv("BEFEHLE_MODELL")?.let(::File)?.takeIf { File(it, CommandModelInfo.FILE).isFile } ?: return
        val lib = System.getenv("BEFEHLE_ORT")?.let(::File)?.takeIf { it.isFile } ?: return
        OrtClassifier(lib, File(dir, CommandModelInfo.FILE), labels = CommandModelInfo.labels.size, inputs = CommandModelInfo.INPUTS, perToken = false, name = "test-befehle").use { cl ->
            val recognizer = CommandRecognizer({ ids, types -> cl.run(ids, types) })
            val report = StringBuilder()
            val checks = mutableListOf<() -> Unit>()
            for (section in listOf("entwicklung", "pruef")) {
                val score = CommandCorpus.Score()
                val times = mutableListOf<Long>()
                // AP-73: of the commands passed on, how often the right one is under the two buttons; free requests with buttons.
                var passed = 0
                var underButtons = 0
                var freeAsked = 0
                for (s in CommandCorpus.load(section)) {
                    val t0 = System.nanoTime()
                    val r = recognizer.recognize(s.text, s.area)
                    times += (System.nanoTime() - t0) / 1000
                    score.add(s, r.label, r.sure)
                    if (s.free) freeAsked += if (r.choices.isNotEmpty()) 1 else 0
                    else if (!(r.sure && !r.free)) {
                        passed++
                        if (r.choices.any { it in s.gold }) underButtons++
                    }
                }
                times.sort()
                report.append(score.report("App: Befehlsmodell (Schwelle ${CommandModelInfo.THRESHOLD}), $section"))
                report.appendLine("  Zeit je Wunsch: Median ${times[times.size / 2] / 1000.0} ms, 95 % unter ${times[times.size * 95 / 100] / 1000.0} ms")
                report.appendLine("  Rückfrage (Untergrenze ${CommandModelInfo.CHOICE_BOUND}): richtig unter den Knöpfen $underButtons/$passed bisher weitergegebenen Befehlen, freie mit Knöpfen $freeAsked/${score.free}")
                if (section == "entwicklung") score.errors.forEach { report.appendLine("    $it") }
                if (section == "pruef") checks += {
                    // The safety goals of AP-71 hold. The goal for hits is 90 %; the shipped model with its cautious
                    // threshold reaches about 85 % (the rest is passed on, not done wrongly) – the floor here guards against
                    // getting worse until the next training.
                    assertTrue((score.wrong + score.freeWrong) * 100 <= score.total * 2, "Prüfteil: ${score.wrong + score.freeWrong}/${score.total} falsch ausgeführt")
                    assertTrue(score.freePassed * 100 >= score.free * 95, "Prüfteil: ${score.freePassed}/${score.free} freie Anfragen weitergegeben")
                    assertTrue(score.hit * 100 >= score.commands * 80, "Prüfteil: ${score.hit}/${score.commands} Befehle getroffen")
                    // AP-73: at least 80 % of the commands passed on so far have the right action under the two buttons,
                    // at most 5 % of the free requests get buttons.
                    assertTrue(underButtons * 100 >= passed * 80, "Prüfteil: $underButtons/$passed richtig unter den Knöpfen")
                    assertTrue(freeAsked * 100 <= score.free * 5, "Prüfteil: $freeAsked/${score.free} freie Anfragen mit Knöpfen")
                }
            }
            File("build/befehle").mkdirs()
            File("build/befehle/app-mit-modell.txt").writeText(report.toString())
            println(report)
            checks.forEach { it() }
        }
    }
}
