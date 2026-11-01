package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.ProposalTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.data.BudgetRow
import io.github.thepytales.llmhub.data.Funders
import io.github.thepytales.llmhub.data.ProposalChecks
import io.github.thepytales.llmhub.data.ProposalDeadline
import io.github.thepytales.llmhub.data.WorkPackage
import io.github.thepytales.llmhub.docs.CalendarFiles
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Antrags-Werkstatt (AP-21). */
class ProposalTest {
    @Test
    fun fundersBringOutlineChecklistAndFlatRate() {
        val dfg = Funders.newProposal("Feedback im Unterricht", Funders.DFG)
        assertEquals("1 Ausgangslage und eigene Vorarbeiten", dfg.sections.first().title)
        assertEquals(22.0, dfg.overheadPercent)
        assertTrue(dfg.checklist.any { "elan" in it.text })
        assertEquals(45.0, Funders.newProposal("X", Funders.EU).pageLimit)
        assertEquals(Funders.BMBF, Funders.byId("BMBF"))
        assertEquals(Funders.OTHER, Funders.byId("Volkswagenstiftung"))
        assertEquals("proposals", ChatModes.PROPOSAL.tool)
    }

    @Test
    fun watcherCountsAndTheCheckFindsWhatIsMissing() {
        val today = LocalDate.of(2026, 11, 1)
        var p = Funders.newProposal("Projekt", Funders.DFG).copy(durationMonths = 24)
        p = p.copy(sections = p.sections.mapIndexed { i, s -> if (i == 0) s.copy(text = "Wort ".repeat(700).trim(), limitChars = 3000) else s.copy(text = "Text.") })
        val first = p.sections.first()
        assertEquals(700, ProposalChecks.count(first.text, 3000).words)
        assertEquals("3.499 von 3.000 Zeichen", ProposalChecks.overLimit(first, 3000))
        assertTrue(ProposalChecks.usage(first, 3000)!! > 1f)
        assertNull(ProposalChecks.overLimit(first.copy(limitChars = null, limitPages = 2.0), 3000))
        assertNotNull(ProposalChecks.overLimit(first.copy(limitChars = null, limitPages = 1.0), 3000))

        p = p.copy(
            deadlines = listOf(ProposalDeadline(title = "Einreichung", date = "2026-11-06"), ProposalDeadline(title = "Kaputt", date = "bald")),
            workPackages = listOf(WorkPackage(title = "Erhebung", start = 1, months = 12), WorkPackage(title = "Auswertung", start = 13, months = 18)),
            budget = listOf(BudgetRow(category = "Personal", item = "Doktorand:in 65 %", amounts = listOf(50_000.0, 52_000.0)), BudgetRow(category = "Reisen", item = "Tagungen", amounts = listOf(2_000.0, 2_000.0))),
        )
        val findings = ProposalChecks.check(p, today)
        fun has(level: ProposalChecks.Level, text: String) = findings.any { it.level == level && text in it.text }
        assertTrue(has(ProposalChecks.Level.ERROR, "Checkliste"))
        assertTrue(has(ProposalChecks.Level.WARN, "Einreichung“ in 5 Tag"))
        assertTrue(has(ProposalChecks.Level.WARN, "kein lesbares Datum"))
        assertTrue(has(ProposalChecks.Level.ERROR, "zu lang"))
        assertTrue(has(ProposalChecks.Level.ERROR, "Auswertung“ liegt außerhalb"))
        assertTrue(has(ProposalChecks.Level.OK, "106.000 €"))
        assertEquals(ProposalChecks.Level.ERROR, findings.first().level, "Fehler zuerst")
        assertEquals(23_320.0, p.overhead, 0.01)

        val md = ProposalChecks.markdown(p)
        assertTrue("## Arbeitspakete und Zeitplan" in md && "| AP 1 | Erhebung |" in md && "| AP | Q1 | Q2 |" in md)
        assertTrue("**106.000 €**" in md && "Programmpauschale" in md)
        val ics = File(Files.createTempDirectory("antrag").toFile(), "f.ics")
        CalendarFiles.writeAll(ics, ProposalChecks.events(p))
        val text = ics.readText()
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(text).count(), "nur Fristen mit Datum")
        assertTrue("TRIGGER:-PT10080M" in text && "Projekt: Einreichung" in text)
    }

    @Test
    fun theAgentSavesTheCallWritesSectionsAndReadsTheCheck() = runBlocking {
        val base = Files.createTempDirectory("lora-antrag").toFile()
        val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val tools = c.proposalTools
        val ctx = RepoContext(mutableListOf(), chatId = "chat-1")
        val created = tools.execute(
            ProposalTools.SAVE,
            """{"title":"FeedLern","funder":"dfg","program":"Sachbeihilfe","duration_months":36,
               "checklist":["Lebenslauf","Publikationsliste"],
               "deadlines":[{"title":"Einreichung","date":"2027-03-01"}],
               "sections":[{"title":"Ziele","limit_pages":2},{"title":"Arbeitsprogramm","limit_chars":5000,"guidance":"mit Methoden"}],
               "work_packages":"[{\"title\":\"Erhebung\",\"start_month\":1,\"months\":12}]",
               "budget":[{"category":"Personal","item":"Postdoc","amounts":[70000,72000,74000]}]}""",
            ctx,
        ).text
        val id = ProposalTools.idIn(created)!!
        val p = c.proposals.get(id)!!
        assertEquals(listOf("Ziele", "Arbeitsprogramm"), p.sections.map { it.title })
        assertEquals(1, p.workPackages.size, "Listen als JSON-Text gehen auch")
        assertEquals(216_000.0, p.directCosts)
        assertEquals(22.0, p.overheadPercent)
        assertEquals("chat-1", p.chatId)

        val written = tools.execute(ProposalTools.WRITE, """{"id":"$id","section":"2","text":"${"Methode ".repeat(700)}"}""", ctx).text
        assertTrue("ZU LANG" in written, written)
        tools.execute(ProposalTools.WRITE, """{"id":"$id","section":"Ziele","text":"Wir untersuchen Feedback."}""", ctx)
        // A changed outline keeps the text of sections with the same title.
        tools.execute(ProposalTools.SAVE, """{"id":"$id","sections":[{"title":"Ziele"},{"title":"Zeitplan"}]}""", ctx)
        val again = c.proposals.get(id)!!
        assertEquals("Wir untersuchen Feedback.", again.sections.first().text)
        assertEquals(2.0, again.sections.first().limitPages)
        val read = tools.execute(ProposalTools.READ, """{"id":"$id"}""", ctx).text
        assertTrue("Abgabe-Check" in read && "Noch leer: Zeitplan" in read, read)
        tools.execute(ProposalTools.REVIEW, """{"id":"$id","summary":"Stark in der Methode, schwach im Transfer."}""", ctx)
        assertEquals(1, c.proposals.get(id)!!.reviews.size)
        assertTrue(tools.execute(ProposalTools.WRITE, """{"id":"$id","section":"Budget","text":"x"}""", ctx).text.startsWith("FEHLER"))
    }
}
