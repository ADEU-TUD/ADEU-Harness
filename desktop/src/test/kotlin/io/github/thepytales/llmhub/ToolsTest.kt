package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ScriptRunner
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.docs.CalendarEvent
import io.github.thepytales.llmhub.docs.CalendarFiles
import io.github.thepytales.llmhub.docs.MailDraft
import io.github.thepytales.llmhub.docs.MailFiles
import io.github.thepytales.llmhub.ui.protocolOf
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolsTest {
    private val out = File("build/test-output").apply { mkdirs() }

    @Test
    fun javascriptCalculatesExactly() {
        val r = ScriptRunner.run("print(round(0.1 + 0.2, 2)); [1, 2, 3].map(x => x * 2)")
        assertNull(r.error)
        assertTrue("0.3" in r.output)
        assertTrue("→ [" in r.output && "6" in r.output, r.output)
    }

    @Test
    fun csvFromAttachmentsIsParsed() {
        val csv = "Name;Punkte;Note\nAnna;87;1,7\nBen;64;3,0\nCem;91,5;1,3\n"
        val r = ScriptRunner.run("var d = parseCSV(files['noten.csv']); print(mean(d.map(x => x.Punkte))); print(table(d.slice(0, 1)))", mapOf("noten.csv" to csv))
        assertNull(r.error, r.output)
        assertTrue(r.output.startsWith("80.83"), r.output)
        assertTrue("| Name | Punkte | Note |" in r.output && "| Anna | 87 | 1.7 |" in r.output, r.output)
    }

    @Test
    fun sandboxBlocksJavaAndStopsEndlessLoops() {
        val java = ScriptRunner.run("java.lang.System.exit(1)")
        assertNotNull(java.error)
        val loop = ScriptRunner.run("while (true) {}")
        assertNotNull(loop.error)
        assertTrue("Zeitlimit" in loop.error!!, loop.error)
    }

    @Test
    fun mailDraftRoundTrip() {
        val f = File(out, "entwurf.eml")
        val d = MailDraft("anna@tu-dresden.de", "", "Rückmeldung zur Hausarbeit", "Sehr geehrte Frau Müller,\n\nvielen Dank – grüße!\n")
        MailFiles.write(f, d)
        assertTrue(f.readText().startsWith("X-Unsent: 1"))
        assertEquals(d, MailFiles.read(f))
    }

    @Test
    fun calendarEventsAreWrittenAndRead() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 0) to false, CalendarFiles.parse("03.11.2026 14:00"))
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 0) to true, CalendarFiles.parse("2026-11-03"))
        assertNull(CalendarFiles.parse("nächsten Dienstag"))
        val f = File(out, "termin.ics")
        val e = CalendarEvent("Sprechstunde; Raum 2", LocalDateTime.of(2026, 11, 3, 14, 0), LocalDateTime.of(2026, 11, 3, 15, 30), location = "WEB 123", reminderMinutes = 15)
        CalendarFiles.write(f, e)
        val text = f.readText()
        assertTrue("DTSTART:20261103T130000Z" in text && "BEGIN:VALARM" in text, text)
        assertEquals(e.copy(reminderMinutes = null), CalendarFiles.read(f))
    }

    @Test
    fun protocolSummarisesATurn() {
        val turn = listOf(
            ChatMessage(role = "assistant", model = "deepseek-ai/DeepSeek-V4.1-Flash", durationMs = 1200, tokensIn = 900, tokensOut = 40,
                toolCalls = listOf(io.github.thepytales.llmhub.data.ToolCall("1", "web_search", "{\"query\":\"x\"}")), events = listOf("Kurze Pause – 3 s gewartet")),
            ChatMessage(role = "tool", toolCallId = "1", content = "Treffer", durationMs = 800),
            ChatMessage(role = "assistant", model = "deepseek-ai/DeepSeek-V4.1-Flash", durationMs = 900, tokensIn = 1500, tokensOut = 200, content = "Fertig"),
        )
        val p = protocolOf(turn)
        assertEquals(2900, p.totalMs)
        assertEquals(2, p.requests)
        assertEquals(1, p.tools)
        assertEquals(2400, p.tokensIn)
        assertTrue(p.steps.any { it.label == "Hinweis" })
    }
}
