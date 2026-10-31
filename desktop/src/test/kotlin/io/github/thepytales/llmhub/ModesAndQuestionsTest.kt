package io.github.thepytales.llmhub

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.thepytales.llmhub.agent.AskUserTool
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.TemplateInterview
import io.github.thepytales.llmhub.agent.UserQuestion
import io.github.thepytales.llmhub.agent.parseArgs
import io.github.thepytales.llmhub.agent.toSchema
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.ui.APP_COMMANDS
import io.github.thepytales.llmhub.ui.MODE_ROUTE
import io.github.thepytales.llmhub.ui.QuestionCard
import io.github.thepytales.llmhub.ui.filterCommands
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** One frame for all modes (AP-05) and questions with answer buttons (AP-06). */
@OptIn(ExperimentalTestApi::class)
class ModesAndQuestionsTest {
    @Test
    fun everyModeIsCompleteAndReachable() {
        ChatModes.all.forEach { m ->
            assertTrue(m.name.isNotBlank() && m.explain.isNotBlank() && m.data.isNotBlank() && m.example.isNotBlank() && m.command.isNotBlank(), m.id)
            assertTrue(m.action || m.prompt.isNotBlank(), "Modus ${m.id} braucht einen Prompt-Zusatz")
            // Command palette (Strg+K) and "/" find every mode.
            assertTrue(APP_COMMANDS.any { it.route == MODE_ROUTE + m.id }, "Palette: ${m.id}")
            assertTrue(m in ChatModes.matching(m.command.take(4)), "/${m.command.take(4)} findet ${m.id}")
        }
        assertEquals(ChatModes.LITERATURE, ChatModes.byId("literature"), "alte Chats im Literatursuchmodus bleiben darin")
        assertTrue(ChatModes.PLANNING in ChatModes.matching("antrag"))
        assertEquals(MODE_ROUTE + "planning", filterCommands(APP_COMMANDS, "planungsmodus").first().route)
        assertEquals(ChatModes.all, ChatModes.matching(""))
    }

    @Test
    fun questionsAreParsedTolerantly() {
        val args = parseArgs(
            """{"questions": [{"question": "Für wen ist das Dokument?", "options": ["Studierende", "Kollegium", "Schulleitung"]},
               {"question": "Welche Formate?", "options": ["Word", "PDF", "PowerPoint"], "multi": true}, {"question": "  "}]}"""
        )
        val qs = AskUserTool.parse(args)
        assertEquals(2, qs.size, "leere Frage fällt weg")
        assertEquals(listOf("Studierende", "Kollegium", "Schulleitung"), qs[0].options)
        assertTrue(qs[1].multi)
        // A JSON string instead of an array, and a single object.
        assertEquals(1, AskUserTool.parse(parseArgs("""{"questions": "[{\"question\": \"Umfang?\", \"options\": [\"1 Seite\", \"5 Seiten\"]}]"}""")).size)
        assertEquals(1, AskUserTool.parse(parseArgs("""{"questions": {"question": "Frist?", "options": []}}""")).size)
        assertEquals(4, AskUserTool.parse(parseArgs("""{"questions": [${(1..6).joinToString(",") { "{\"question\": \"F$it\", \"options\": []}" }}]}""")).size)
        val text = AskUserTool.answerText(qs, listOf(setOf("Kollegium"), setOf("Word", "PDF")), listOf("", "am liebsten PDF/A"))
        assertTrue("Für wen ist das Dokument?\n→ Kollegium" in text, text)
        assertTrue("→ Word, PDF, am liebsten PDF/A" in text, text)
    }

    @Test
    fun theToolHasAFullSchema() {
        val schema = AskUserTool().defs.toSchema().first().jsonObject["function"]!!.jsonObject["parameters"]!!.jsonObject
        val questions = schema["properties"]!!.jsonObject["questions"]!!.jsonObject
        assertEquals("array", questions["type"]!!.jsonPrimitive.content)
        assertNotNull(questions["items"]!!.jsonObject["properties"]!!.jsonObject["options"])
        assertEquals("questions", schema["required"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun templatesCanBeFilledAsAQuestionRound() {
        val t = Template(
            name = "Seminarplan", kind = TemplateKind.PROMPT,
            content = "Erstelle einen Seminarplan für {{Thema}} mit {{Sitzungen|Anzahl}} Sitzungen für {{Zielgruppe}}.",
            instructions = "Als Tabelle.",
        )
        assertTrue(TemplateInterview.offered(t))
        val text = TemplateInterview.instructions(t, mapOf("Thema" to "Inklusion", "Zielgruppe" to ""))
        assertTrue("- Sitzungen – Anzahl" in text && "- Thema: Inklusion" in text && "ask_user" in text && "Als Tabelle." in text, text)
        assertTrue("Zielgruppe:" !in text, "leere Felder gelten nicht als ausgefüllt")
        assertTrue(!TemplateInterview.offered(t.copy(content = "Nur {{Ein}} Feld")))
    }

    @Test
    fun theCardSendsTheChosenAnswers() = runComposeUiTest {
        var sent: String? = null
        val qs = listOf(UserQuestion("Für wen?", listOf("Studierende", "Kollegium")), UserQuestion("Wie lang?", listOf("1 Seite", "3 Seiten")))
        setContent { QuestionCard(qs) { sent = it } }
        onNodeWithText("Kollegium").performClick()
        onNodeWithText("3 Seiten").performClick()
        onNodeWithText("Antworten").performClick()
        assertEquals("Für wen?\n→ Kollegium\n\nWie lang?\n→ 3 Seiten", sent)
        onNodeWithText("Überspringen").performClick()
        assertEquals(AskUserTool.SKIP_TEXT, sent)
    }
}
