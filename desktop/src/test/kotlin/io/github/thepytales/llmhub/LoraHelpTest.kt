package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraReply
import io.github.thepytales.llmhub.agent.LoraSense
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.loraCalendar
import io.github.thepytales.llmhub.agent.parseArgs
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.ui.LoraAction
import io.github.thepytales.llmhub.ui.moodFor
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lora as the helper at the core of the app (AP-33–AP-36): what she notices on the device (deadlines, tables,
 * literature, the place a typed text means, the step of the agent), her suggestions per area, her chat search, the
 * calendar entries and her reactions.
 */
class LoraHelpTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val targets = AppMap.build(TemplateLibrary.builtIn, android = false)

    @Test
    fun findsDeadlinesInLinesAndTables() {
        val answer = """
            Aktuelle Ausschreibungen:

            - **BMFTR – Generative KI in der Bildung**: Skizzenfrist 30.11.2026, bis zu 500.000 €.
            - Wübben Stiftung Sandpit-Call: Einreichung bis zum 15. Dezember.
            - Die Tagung war am 3.10.2026 (vorbei).

            | Fördergeber | Programm | Frist |
            |---|---|---|
            | VolkswagenStiftung | Momentum | 12.01.2027 |
            | Stifterverband | Freiraum | 2027-02-01 |
        """.trimIndent()
        val found = LoraSense.deadlines(answer, today)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 12), LocalDate.of(2027, 2, 1)),
            found.map { it.date },
        )
        assertTrue(found[0].title.contains("BMFTR"), found[0].title)
        assertTrue(found[2].title.startsWith("VolkswagenStiftung"), found[2].title)
        // Past days and numbers that are no dates (versions, sections) do not count.
        assertTrue(LoraSense.deadlines("Version 1.0.43, Abschnitt 3.2. Frist: 1.0.2026", today).isEmpty())
        assertTrue(LoraSense.deadlines("Frist war der 3.10.2026", today).isEmpty())
        // Without a year the next such day.
        assertEquals(LocalDate.of(2027, 3, 1), LoraSense.deadlines("Abgabe bis 1.3.", today).single().date)
    }

    @Test
    fun nextStepsUnderAnAnswer() {
        val table = "| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |"
        val steps = LoraSense.nextSteps("Frist: 30.11.2026\n\n$table\n\nSiehe doi:10.1007/s11618-023-01170-3.", today)
        assertTrue(steps[0] is LoraSense.NextStep.Calendar)
        assertEquals(table, (steps[1] as LoraSense.NextStep.Excel).table)
        assertEquals(listOf("10.1007/s11618-023-01170-3"), (steps[2] as LoraSense.NextStep.Literature).dois)
        assertEquals("Frist 30.11. in den Kalender", LoraSense.label(steps[0]))
        // A long text becomes a Word document; a short one offers nothing.
        assertEquals(listOf(LoraSense.NextStep.Word), LoraSense.nextSteps("Absatz. ".repeat(200), today))
        assertTrue(LoraSense.nextSteps("Kurze Antwort.", today).isEmpty())
    }

    @Test
    fun typingHintOnlyForClearAppWishes() {
        assertEquals("option.appearance.theme", LoraSense.typingHint("dunkles Design", targets)?.id)
        assertEquals("tool.pdf", LoraSense.typingHint("PDF zusammenfügen", targets)?.id)
        // Real questions and long texts are left to the chat.
        assertNull(LoraSense.typingHint("Erkläre mir den Unterschied zwischen hellem und dunklem Design in der Malerei", targets))
        assertNull(LoraSense.typingHint("Schreib eine Mail an die Studierenden", targets))
        assertNull(LoraSense.typingHint("Hallo", targets))
    }

    @Test
    fun readableStepsOfTheAgent() {
        assertEquals("Sucht im Web: „Ausschreibungen Geisteswissenschaften“", LoraSense.stepLine("web_search", """{"query":"Ausschreibungen Geisteswissenschaften"}""", "Websuche"))
        assertEquals("Liest dfg.de", LoraSense.stepLine("fetch_url", """{"url":"https://www.dfg.de/foerderung""", "Webseite lesen"))
        assertEquals("Sucht im Web …", LoraSense.stepLine("web_search", """{"que""", "Websuche"))
        assertEquals("Schreibt „Antrag Skizze“", LoraSense.stepLine("create_document", """{"title":"Antrag Skizze","content":"…""", "Dokument erstellen"))
        assertEquals("Rätseln …", LoraSense.stepLine("unbekannt", "{}", "Rätseln"))
    }

    @Test
    fun suggestionsFitTheArea() {
        val chat = LoraSense.suggestions("chat?id=x", inChat = true, enabledTools = emptySet(), android = false)
        assertTrue(chat.any { it.inChat && it.label == "Chat zusammenfassen" })
        val settings = LoraSense.suggestions("settings/appearance", inChat = false, enabledTools = emptySet(), android = false)
        assertEquals("option.appearance.theme", settings.first().target)
        assertEquals("Einstellungen › Darstellung", LoraSense.place("settings/appearance"))
        assertEquals("PDF-Werkzeugkasten", LoraSense.place("pdf"))
        assertEquals("Chat", LoraSense.place("chat?id=1&model=x"))
        // Every fixed target exists on the map, and every area offers at most four.
        for (route in listOf("chat", "settings", "templates", "literature", "transcripts", "pdf", "vault", "docs", "images", "tasks")) {
            for (inChat in listOf(false, true)) {
                val list = LoraSense.suggestions(route, inChat, setOf(AppTool.PDF.id), false)
                assertTrue(list.size in 1..4, route)
                list.mapNotNull { it.target }.forEach { id -> assertNotNull(AppMap.byId(id, targets), "$route: $id") }
            }
        }
    }

    @Test
    fun chatSearchCalendarAndHandoff() {
        val base = java.nio.file.Files.createTempDirectory("adeu-lora-help").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", java.io.File(base, "daten").absolutePath)
        val c = AppContainer(android.content.Context(java.io.File(base, "files").apply { mkdirs() }, java.io.File(base, "cache").apply { mkdirs() }))
        c.chats.save(Conversation(title = "Ausschreibungen GSW", model = "m", messages = listOf(ChatMessage(role = "user", content = "Wübben Stiftung Sandpit-Call"))))
        c.chats.save(Conversation(title = "Seminarplan", model = "m", messages = listOf(ChatMessage(role = "user", content = "Sitzungen im Winter"))))
        val agent = LoraAgent(c)
        assertEquals(listOf("Ausschreibungen GSW"), agent.searchChats("wübben").map { it.title })
        assertEquals(2, agent.searchChats("").size)
        assertTrue(agent.searchChats("gibtesnicht").isEmpty())
        // „add_reminder“ becomes a step the user confirms; the calendar file holds an all-day event with a reminder.
        val (_, step) = agent.runTool("add_reminder", parseArgs("""{"titel":"Skizzenfrist BMFTR","datum":"2026-11-30","tage_vorher":"2"}"""), targets)
        step as LoraStep.Reminder
        assertEquals(LocalDate.of(2026, 11, 30), step.date)
        val ics = c.loraCalendar(listOf(step.title to step.date), step.daysBefore).readText()
        assertTrue("DTSTART;VALUE=DATE:20261130" in ics, ics)
        assertTrue("SUMMARY:Frist: Skizzenfrist BMFTR" in ics, ics)
        assertTrue("TRIGGER:-PT2880M" in ics, ics)
        // Handing over names the model and why.
        val (_, handoff) = agent.runTool("handoff_chat", parseArgs("""{"anfrage":"Suche im Web nach Ausschreibungen","zusammenfassung":"Geisteswissenschaften, 4 Wochen"}"""), targets)
        handoff as LoraStep.Handoff
        assertTrue(handoff.reason.isNotBlank())
        assertEquals("Geisteswissenschaften, 4 Wochen", handoff.summary)
    }

    @Test
    fun handoverGoesToDeepSeekUnlessItIsSpecial() {
        val std = io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT
        assertTrue("DeepSeek" in io.github.thepytales.llmhub.data.ModelCatalog.info(std).displayName)
        // The funding question of the screenshots (with „Förderprogramm“) stays with DeepSeek.
        assertEquals(std to "Standard für Übergaben", LoraAgent.handoffModel(std, "Suche im Web nach Förderprogrammen für Forschung und Lehre, Frist in 4 Wochen", null))
        // Special cases: Lora's own choice with her reason, or a question that clearly needs another model.
        assertEquals("zai-org/GLM-5.3" to "sehr schwierige Analyse", LoraAgent.handoffModel(std, "Vergleiche drei Theorien", "zai-org/GLM-5.3", "sehr schwierige Analyse"))
        assertEquals(io.github.thepytales.llmhub.data.ModelCatalog.CODE_CHAT, LoraAgent.handoffModel(std, "Warum wirft mein Python-Skript eine Exception?", null).first)
        // An own standard counts.
        assertEquals("mein/Modell", LoraAgent.handoffModel("mein/Modell", "Erklär mir Exzerpte", null).first)
    }

    @Test
    fun loraReactsToHerAnswers() {
        val place = targets.first { it.id == "option.appearance.theme" }
        assertEquals(LoraAction.POINT, moodFor(LoraReply("Hier ist es.", listOf(LoraStep.Navigate(place)))))
        assertEquals(LoraAction.SHRUG, moodFor(LoraReply("Dazu habe ich nichts gefunden – versuch es mit anderen Worten.")))
        assertEquals(LoraAction.SCRATCH, moodFor(LoraReply("Ich erreiche gerade kein Modell – ohne Internetverbindung. In der App habe ich das gefunden:")))
        assertEquals(LoraAction.NOD, moodFor(LoraReply("Gern.")))
    }

    @Test
    fun brandIsLora() {
        assertEquals("Lora", Brand.NAME)
        assertTrue(Brand.SLOGAN.startsWith("Der freundliche Helfer"))
    }
}

class ChatTitlesTest {
    @Test
    fun shortTitlesFromTheFirstMessage() {
        val t = io.github.thepytales.llmhub.data.ChatTitles
        assertEquals("Aktuelle Ausschreibungen für Forschung", t.make("Suche bitte im Web nach aktuellen Ausschreibungen für Forschung, Lehre sowie Ausstattung"))
        assertEquals("Unterschied zwischen Inklusion und Integration", t.make("Kannst du mir bitte erklären: den Unterschied zwischen Inklusion und Integration?"))
        assertEquals("E-Mail an die Studierenden wegen der Klausur", t.make("Schreibe eine E-Mail an die Studierenden wegen der Klausur"))
        assertEquals("Hallo", t.make("Hallo"))
        assertTrue(t.make("a".repeat(30) + " " + "b".repeat(40)).endsWith("…"))
        assertEquals("", t.make("   "))
    }
}
