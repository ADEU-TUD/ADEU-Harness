package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ArgRepair
import io.github.thepytales.llmhub.agent.AssistMode
import io.github.thepytales.llmhub.agent.DocumentTools
import io.github.thepytales.llmhub.agent.FieldAssist
import io.github.thepytales.llmhub.agent.Param
import io.github.thepytales.llmhub.agent.ScriptRunner
import io.github.thepytales.llmhub.agent.ToolDef
import io.github.thepytales.llmhub.agent.toSchema
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.data.UsageStore
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.ui.splitThinking
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Checks against the real university server whether the models follow what the app relies on. Runs only with
 * a key: LLM_API_KEY=sk-… ./gradlew -p desktop test --tests '*LiveApiTest*' (optional LLM_MODEL=…).
 */
class LiveApiTest {
    private val key = System.getenv("LLM_API_KEY").orEmpty()
    private val model = System.getenv("LLM_MODEL")?.takeIf { it.isNotBlank() } ?: ModelCatalog.DEFAULT_CHAT
    private lateinit var llm: LlmClient

    @BeforeTest
    fun setUp() {
        assumeTrue(key.isNotBlank(), "LLM_API_KEY nicht gesetzt – Live-Tests übersprungen")
        val dir = File("build/live-test").apply { mkdirs() }
        System.setProperty("adeu.data", dir.absolutePath)
        val context = Context(dir, dir)
        val settings = SettingsStore(context).apply { update { it.copy(apiKey = key) } }
        llm = LlmClient(settings, UsageStore(dir))
    }

    private fun ask(messages: List<ChatMessage>, tools: kotlinx.serialization.json.JsonArray? = null) = runBlocking {
        llm.streamChat(model, messages, tools, thinking = ThinkingLevel.OFF) { }
    }

    @Test
    fun modelAnswers() {
        val r = ask(listOf(ChatMessage(role = "user", content = "Antworte nur mit dem Wort OK.")))
        assertTrue("OK" in splitThinking(r.content).second.uppercase(), r.content)
    }

    @Test
    fun aiButtonOnlyWritesTheLetterBody() {
        val t = TemplateLibrary.builtIn.first { it.direct == "letter" }
        val field = t.fields.first { it.label == "Brieftext" }
        val values = mapOf("Betreff" to "Haushaltsvorschläge 2027", "Empfänger" to "Dezernat Finanzen", "Brieftext" to "Haushalt: Vorschläge bis 30.11. einreichen, zwei Positionen")
        val r = ask(FieldAssist.prompt(t, field, values, AssistMode.EXPAND))
        val text = FieldAssist.clean(r.content)
        assertTrue(text.length > 80, text)
        assertFalse(text.startsWith("Sehr geehrte"), "keine Anrede: $text")
        assertFalse("Mit freundlichen Grüßen" in text, "keine Grußformel: $text")
        assertTrue("30" in text, "Frist bleibt erhalten: $text")
    }

    @Test
    fun toolCallsAreValidAndUsed() {
        val tool = ToolDef("run_javascript", "Führt JavaScript aus; Ausgabe mit print(...).", listOf(Param("code", "JavaScript-Code")))
        val r = ask(
            listOf(ChatMessage(role = "user", content = "Berechne 1234 * 5678 mit dem Werkzeug run_javascript.")),
            listOf(tool).toSchema(),
        )
        val call = r.toolCalls.firstOrNull { it.name == "run_javascript" }
        assertTrue(call != null, "kein Werkzeugaufruf: ${r.content}")
        val args = assertIs<ArgRepair.Result.Ok>(ArgRepair.parse(call.arguments)).args
        assertEquals("7006652", ScriptRunner.run(args.s("code")!!).output.lines().first { it.isNotBlank() }.removePrefix("→ ").trim())
    }

    @Test
    fun slidesFollowTheFormat() {
        val r = ask(
            listOf(
                ChatMessage(role = "system", content = DocumentTools.PPTX_HELP + " Gib nur den Markdown-Inhalt der Präsentation aus, ohne Werkzeug."),
                ChatMessage(role = "user", content = "Fünf Folien zu Classroom Management für Lehramtsstudierende, mit Sprechernotizen."),
            )
        )
        val slides = PptxWriter.parse(splitThinking(r.content).second.removePrefix("```markdown").removePrefix("```").removeSuffix("```"))
        assertTrue(slides.size >= 4, "nur ${slides.size} Folien:\n${r.content.take(800)}")
        assertEquals(PptxWriter.Kind.TITLE, slides.first().kind)
        assertTrue(slides.drop(1).all { it.title.isNotBlank() }, "Folie ohne Titel")
        assertTrue(slides.count { it.notes.isNotBlank() } >= 2, "kaum Sprechernotizen")
    }
}
