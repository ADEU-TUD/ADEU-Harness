package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraCommands
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.MapKind
import io.github.thepytales.llmhub.video.VideoLora
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Messkorpus of the command model (AP-71, Stufe b): format and labels of the corpus, and the starting point – what the
 * rules of the app ([LoraAgent.local], without any model) make of the wishes. Written to build/befehle/regeln.txt.
 */
class CommandCorpusTest {
    private val targets = AppMap.build(android = false)

    /** The label the rules would act on, or null when they pass the wish on to the model. */
    private fun rules(wish: String): String? {
        val reply = LoraAgent.local(wish, targets, AppTool.entries.map { it.id }.toSet(), android = false) ?: return null
        return when (val step = reply.steps.singleOrNull()) {
            is LoraStep.Navigate -> LoraCommands.PLACE + step.target.id
            is LoraStep.OpenMode -> if (step.target.kind == MapKind.TEMPLATE) LoraCommands.TEMPLATE else LoraCommands.PLACE + step.target.id
            is LoraStep.EnableTool -> LoraCommands.PLACE + "tool." + step.tool.id
            is LoraStep.Handoff -> LoraCommands.FREE_SIMPLE
            else -> if (reply.steps.isEmpty() && reply.hits.isNotEmpty()) LoraCommands.LORA + "hilfe" else null
        }
    }

    @Test
    fun corpusIsWellFormed() {
        VideoLora.register()
        val known = LoraCommands.catalog(android = false).map { it.label }.toSet()
        val all = CommandCorpus.load()
        val unknown = all.flatMap { s -> s.gold.filter { it !in known }.map { "${s.id}: $it" } }
        assertTrue(unknown.isEmpty(), "unbekannte Etiketten: $unknown")
        val test = CommandCorpus.load("pruef")
        assertTrue(all.size >= 600 && test.size >= 250, "${all.size} Wünsche, Prüfteil ${test.size}")
        // Every label of the shipped model has at least one wish in each part. (The catalog of the code may hold more:
        // modes of tools that are switched on, places added since the training.)
        val model = io.github.thepytales.llmhub.data.CommandModelInfo.labels.toSet()
        for (section in listOf("pruef", "entwicklung")) {
            val covered = CommandCorpus.load(section).map { it.gold.first() }.toSet()
            assertTrue(covered.containsAll(model), "$section ohne Beispiel: ${model - covered}")
        }
    }

    @Test
    fun rulesOfTodayAsTheStartingPoint() {
        val report = StringBuilder()
        for (section in listOf("entwicklung", "pruef")) {
            val score = CommandCorpus.Score()
            for (s in CommandCorpus.load(section)) {
                val label = rules(s.text)
                score.add(s, label ?: LoraCommands.FREE_COMPLEX, sure = label != null)
            }
            report.append(score.report("Regeln der App (LoraAgent.local), $section"))
            // Error lists for the development part only.
            if (section == "entwicklung") score.errors.filter { "falsch" in it || "frei →" in it }.forEach { report.appendLine("    $it") }
        }
        File("build/befehle").mkdirs()
        File("build/befehle/regeln.txt").writeText(report.toString())
        println(report)
    }
}
