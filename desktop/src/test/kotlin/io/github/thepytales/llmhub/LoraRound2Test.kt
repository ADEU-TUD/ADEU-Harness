package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ActionArea
import io.github.thepytales.llmhub.agent.ActionHosts
import io.github.thepytales.llmhub.agent.EditorDocHost
import io.github.thepytales.llmhub.agent.FlowStep
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraFlow
import io.github.thepytales.llmhub.agent.LoraFlows
import io.github.thepytales.llmhub.agent.LoraPrefs
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.SheetLora
import io.github.thepytales.llmhub.agent.SlidesLora
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.MemoryCategory
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.ui.LoraTours
import io.github.thepytales.llmhub.ui.renameEntry
import io.github.thepytales.llmhub.ui.renameProblem
import io.github.thepytales.llmhub.ui.deleteEntries
import io.github.thepytales.llmhub.video.VideoLora
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runde 2, „Jetzt“: Lora handelt in Tabellen, Folien und Video (AP-38), merkt sich Vorlieben nur nach Rückfrage
 * (AP-39), plant Abläufe über Werkzeuge (AP-40), hat Leuchtrahmen und Touren (AP-41) und benennt im Dateidialog um
 * (AP-42).
 */
class LoraRound2Test {
    private fun <T> withContainer(block: (AppContainer) -> T): T {
        val base = Files.createTempDirectory("lora-runde2").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        return try {
            block(AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })))
        } finally {
            base.deleteRecursively()
        }
    }

    private fun args(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

    /** An open table or deck as the editors report it. */
    private class FakeHost(var md: String, private val sel: Int = 0) : EditorDocHost {
        override fun markdown() = md
        override fun replace(markdown: String) { md = markdown }
        override fun selected() = sel
    }

    private val notes = """
        | Name | Klausur | Hausarbeit |
        | --- | --- | --- |
        | Berta  |  2,0 | 1,7 |
        | Anton | 1,3 | 2,3 |
        | Anton | 1,3 | 2,3 |
        |  |  |  |
    """.trimIndent()

    // ── AP-38: Tabellen und Folien ──────────────────────────────────────────────────────────────────────

    @Test
    fun sheetHelpers() {
        val rows = SheetEngine.grid(notes)
        assertEquals(1, SheetLora.column(rows, "Klausur"))
        assertEquals(2, SheetLora.column(rows, "C"))
        assertEquals(2, SheetLora.column(rows, "haus"))
        assertNull(SheetLora.column(rows, "Gibtsnicht"))
        val cells = SheetLora.parseCells("A1=Nachname; D2==B2+C2\nE7: x")
        assertEquals("=B2+C2", cells[1 to 3])
        assertEquals("x", cells[6 to 4])
        val set = SheetLora.setCells(rows, cells)
        assertEquals("Nachname", set[0][0])
        assertEquals(7, set.size)
        val withCol = SheetLora.addColumn(rows, "Mittel", "=MITTELWERT(B{r}:C{r})")
        assertEquals("Mittel", withCol[0].last())
        assertEquals("=MITTELWERT(B3:C3)", withCol[2].last())
        val order = SheetLora.sortOrder(rows, 0, desc = false)
        assertEquals(0, order.first())
        assertEquals(listOf("Anton", "Anton", "Berta"), order.drop(1).map { rows[it][0] }.filter { it.isNotBlank() })
        val (clean, said) = SheetLora.clean(rows)
        assertEquals(3, clean.size, clean.toString())
        assertEquals("Berta", clean[1][0])
        assertEquals(2, said.size, said.toString())
        assertEquals(1, SheetLora.clean(listOf(listOf("A"), listOf(" x  y "))).second.size)
        val sum = SheetLora.summary(clean)
        assertNotNull(sum)
        assertEquals("=SUMME(B2:B3)", sum[sum.size - 2][1])
        assertEquals("=MITTELWERT(C2:C3)", sum.last()[2])
    }

    @Test
    fun slideHelpers() {
        val slides = listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE), SlideDeck.Slide("Methode", "- a"), SlideDeck.Slide("Ergebnisse"))
        assertEquals(1, SlidesLora.index(slides, "2", 0))
        assertEquals(2, SlidesLora.index(slides, "Ergeb", 0))
        assertEquals(2, SlidesLora.index(slides, "letzte", 0))
        assertEquals(1, SlidesLora.index(slides, "", 1))
        assertEquals("- eins\n- zwei", SlidesLora.bullets("eins; zwei"))
        val made = SlidesLora.fromOutline("# Einleitung\n- Frage\n- Ziel\nMethode:\n- Interviews\n  Auswertung")
        assertEquals(listOf("Einleitung", "Methode"), made.map { it.title })
        assertEquals("- Interviews\n- Auswertung", made[1].body)
    }

    @Test
    fun actionsInTablesAndSlidesWithUndo() = withContainer { c ->
        runBlocking {
            val sheet = FakeHost(notes)
            ActionHosts.sheet = sheet
            try {
                assertEquals(ActionArea.SHEET, ActionArea.of("doc/abc"))
                assertTrue("sheet.sort" in ToolActions.forArea(ActionArea.SHEET, android = true).map { it.id })
                val sort = ToolActions.byId("sheet.sort")!!.plan(c, args("spalte" to "Klausur"))
                val r = sort.run()
                assertTrue(SheetEngine.grid(sheet.md)[1][0] == "Anton", sheet.md)
                assertEquals("Tabelle wie vorher.", r.undo!!())
                assertEquals(notes, sheet.md)
                val clean = ToolActions.byId("sheet.clean")!!.plan(c, JsonObject(emptyMap()))
                assertTrue(clean.risky && clean.preview.isNotEmpty())
                clean.run()
                assertEquals(3, SheetEngine.grid(sheet.md).size)
                // Lora knows what to do with „sortiere nach Hausarbeit absteigend“ without a model.
                sheet.md = notes
                val local = LoraAgent(c).localAction("Sortiere nach Hausarbeit absteigend", ActionArea.SHEET, android = false)
                val plan = (local!!.steps.first() as LoraStep.Action).plan
                plan.run()
                assertEquals("Anton", SheetEngine.grid(sheet.md)[1][0])
            } finally {
                ActionHosts.sheet = null
            }
            val deck = FakeHost(SlideDeck.markdown(listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE), SlideDeck.Slide("Eins"))), sel = 1)
            ActionHosts.slides = deck
            try {
                assertEquals(ActionArea.SLIDES, ActionArea.of("doc/abc"))
                val add = ToolActions.byId("slides.add")!!.plan(c, args("titel" to "Zwei", "inhalt" to "a; b"))
                val undo = add.run().undo!!
                assertEquals(listOf("Titel", "Eins", "Zwei"), SlideDeck.parse(deck.md).map { it.title })
                undo()
                assertEquals(2, SlideDeck.parse(deck.md).size)
                val del = ToolActions.byId("slides.delete")!!.plan(c, args("folie" to "2"))
                assertTrue(del.risky)
                del.run()
                assertEquals(listOf("Titel"), SlideDeck.parse(deck.md).map { it.title })
            } finally {
                ActionHosts.slides = null
            }
            assertEquals(ActionArea.DOC, ActionArea.of("doc/abc"))
        }
    }

    @Test
    fun privacyOfActions() = withContainer { c ->
        // Without a project the content may go to the chosen model; files of the Tresor never reach an action.
        assertEquals(io.github.thepytales.llmhub.data.PrivacyLevel.OPEN, ToolActions.privacy(c))
        assertFalse(ToolActions.allowed(c, File("/x/files/tresor-dateien/geheim.pdf")))
        assertTrue(ToolActions.allowed(c, File("/x/Lora/Lehre/plan.pdf")))
        if (!c.settings.current.hasAnyApiKey) assertNull(ToolActions.model(c), "ohne API-Schlüssel kein Modell")
        else assertEquals(io.github.thepytales.llmhub.data.ModelCatalog.FAST_CHAT, ToolActions.model(c))
        assertTrue(ToolActions.byId("files.rename_by_content")!!.usesModel)
    }

    @Test
    fun videoActionsWorkOnTheStoredProject() = withContainer { c ->
        runBlocking {
            VideoLora.register()
            assertTrue(ToolActions.forArea(ActionArea.VIDEO, android = false).map { it.id }.containsAll(listOf("video.split", "video.title", "video.subtitles", "video.export")))
            assertTrue(ToolActions.forArea(ActionArea.VIDEO, android = true).isEmpty(), "kein Video auf Android")
            assertEquals(90.0, VideoLora.seconds("1:30"))
            assertEquals(3723.0, VideoLora.seconds("1:02:03"))
            assertNull(VideoLora.seconds(" "))
            val p = io.github.thepytales.llmhub.ui.VideoHub.store.save(
                io.github.thepytales.llmhub.video.VideoProject(title = "Vortrag", clips = listOf(io.github.thepytales.llmhub.video.VideoClip(path = "/x/v.mp4", inSec = 0.0, outSec = 120.0))),
            )
            VideoLora.lastId = p.id
            ToolActions.byId("video.split")!!.plan(c, args("zeit" to "1:00")).run()
            assertEquals(2, io.github.thepytales.llmhub.ui.VideoHub.store.get(p.id)!!.clips.size)
            val title = ToolActions.byId("video.title")!!.plan(c, args("text" to "Dr. Erika Musterfrau", "untertext" to "Professur", "stil" to "bauchbinde", "zeit" to "0:05")).run()
            assertEquals(5.0, io.github.thepytales.llmhub.ui.VideoHub.store.get(p.id)!!.overlays.single().startSec)
            title.undo!!()
            assertTrue(io.github.thepytales.llmhub.ui.VideoHub.store.get(p.id)!!.overlays.isEmpty())
            VideoLora.lastId = null
        }
    }

    // ── AP-39: Gedächtnis ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun preferencesAreOnlyRememberedAfterAsking() = withContainer { c ->
        val tidy = ToolActions.byId("files.tidy")!!
        // Lora asks after the user chose „nach Semester“ …
        val offer = LoraPrefs.offerFor(c, tidy, args("nach" to "semester"))
        assertNotNull(offer)
        assertEquals("files", offer.tool)
        assertTrue(LoraPrefs.all(c).isEmpty(), "nichts still gemerkt")
        // … and only „Merken“ saves it, as the default for the next tidying.
        LoraPrefs.save(c, offer)
        assertEquals("semester", LoraPrefs.default(c, "files.tidy", "nach"))
        val (filled, used) = LoraPrefs.withDefaults(c, tidy, JsonObject(emptyMap()))
        assertEquals("semester", (filled["nach"] as JsonPrimitive).content)
        assertEquals(listOf("semester"), used)
        assertNull(LoraPrefs.offerFor(c, tidy, args("nach" to "semester")), "schon gemerkt")
        // A wish in words: asked first, the tool guessed.
        val wish = LoraPrefs.fromWish(c, "Merk dir, dass ich Handouts zweispaltig möchte")
        val step = wish!!.steps.single() as LoraStep.Remember
        assertEquals("doc", step.tool)
        assertEquals("Ich Handouts zweispaltig möchte", step.text)
        assertEquals("video", LoraPrefs.guessTool("Videos für OPAL in 720p"))
        assertEquals("literature", LoraPrefs.guessTool("Literaturverzeichnis in APA 7"))
        assertNull(LoraPrefs.fromWish(c, "Wie spät ist es?"))
        // Preferences are memory entries (category Vorlieben) and go into Lora's prompt.
        assertEquals(MemoryCategory.PREFERENCES.name, LoraPrefs.all(c).single().category)
        assertTrue(LoraPrefs.promptLine(c)!!.contains("Semester"))
        assertEquals(1, LoraPrefs.forgetAll(c).size)
        assertTrue(LoraPrefs.all(c).isEmpty())
    }

    // ── AP-40: Abläufe ─────────────────────────────────────────────────────────────────────────────────

    private fun waitFor(c: AppContainer, id: String, until: (LoraFlow) -> Boolean): LoraFlow {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            LoraFlows.get(c, id)?.takeIf(until)?.let { return it }
            Thread.sleep(30)
        }
        error("Ablauf kam nicht an: ${LoraFlows.get(c, id)}")
    }

    @Test
    fun aPlanRunsStepByStepAndWaitsForRiskyOnes() = withContainer { c ->
        val sheet = FakeHost(notes)
        ActionHosts.sheet = sheet
        try {
            assertTrue(LoraFlows.looksMultiStep("Bereinige die Tabelle, dann rechne Summe und Mittelwert"))
            assertFalse(LoraFlows.looksMultiStep("Räum meinen Ordner auf"))
            val steps = LoraFlows.parse(args("schritte" to """[{"aktion":"sheet.clean"},{"aktion":"act_sheet_summary"},{"aktion":"gibt.es.nicht"}]"""))
            assertEquals(3, steps.size)
            val (flow, dropped) = LoraFlows.draft(c, "Bereinigen und rechnen", steps, android = false)!!
            assertEquals(listOf("gibt.es.nicht"), dropped)
            assertEquals(listOf("sheet.clean", "sheet.summary"), flow.steps.map { it.actionId })
            assertEquals(LoraFlow.DRAFT, flow.state)
            LoraFlows.start(c, flow.id)
            // The risky step waits with its preview …
            val waiting = waitFor(c, flow.id) { it.steps[0].status == FlowStep.CONFIRM }
            assertEquals(LoraFlow.PAUSED, waiting.state)
            assertTrue(waiting.steps[0].preview.isNotEmpty())
            // … „Ausführen“ carries on to the end.
            LoraFlows.approve(c, flow.id, 0)
            val done = waitFor(c, flow.id) { it.state == LoraFlow.DONE }
            assertTrue(done.steps.all { it.status == FlowStep.DONE }, done.toString())
            assertTrue(sheet.md.contains("SUMME"), sheet.md)
        } finally {
            ActionHosts.sheet = null
        }
    }

    @Test
    fun aRunningPlanCarriesOnAfterARestart() = withContainer { c ->
        val sheet = FakeHost(notes)
        ActionHosts.sheet = sheet
        try {
            val (flow, _) = LoraFlows.draft(c, "Summe", listOf("sheet.summary" to emptyMap(), "sheet.sort" to mapOf("spalte" to "Name")), android = false)!!
            // As stored when the app closed in the middle of the first step.
            val file = File(c.appContext.filesDir, "lora-ablaeufe.json")
            val stored = flow.copy(state = LoraFlow.RUNNING, steps = flow.steps.mapIndexed { i, s -> if (i == 0) s.copy(status = FlowStep.RUNNING) else s })
            file.writeText(kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(kotlinx.serialization.builtins.ListSerializer(LoraFlow.serializer()), listOf(stored)))
            withContainer { fresh ->
                // A new start of the app reads the same file (the data folder moved into the new container).
                File(fresh.appContext.filesDir, "lora-ablaeufe.json").writeText(file.readText())
                LoraFlows.resume(fresh)
                val done = waitFor(fresh, flow.id) { it.state == LoraFlow.DONE || it.state == LoraFlow.FAILED }
                assertEquals(LoraFlow.DONE, done.state, done.toString())
            }
            // A failed step can be repeated.
            val (bad, _) = LoraFlows.draft(c, "Spalte", listOf("sheet.sort" to mapOf("spalte" to "Gibtsnicht")), android = false)!!
            LoraFlows.start(c, bad.id)
            val failed = waitFor(c, bad.id) { it.state == LoraFlow.FAILED }
            assertEquals(FlowStep.FAILED, failed.steps[0].status)
            assertTrue(failed.steps[0].result.contains("Gibtsnicht"))
        } finally {
            ActionHosts.sheet = null
        }
    }

    // ── AP-41: Leuchtrahmen und Touren ─────────────────────────────────────────────────────────────────

    private val sources: String by lazy {
        listOf(File("../app/src/main/java"), File("src/main/kotlin")).flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.toList() }.joinToString("\n") { it.readText() }
    }

    @Test
    fun everyPlaceOfTheMapHasItsFrame() {
        val missing = AppMap.spotIds.filter { id -> !sources.contains("Spot(\"$id\"") && !sources.contains("spot = \"$id\"") }
        assertTrue(missing.isEmpty(), "Ziele der Landkarte ohne Leuchtrahmen: $missing")
        // Every switch of the settings has a frame.
        val bare = Regex("""SwitchRow\(\s*tr\("([^"]+)"\)[^\n]*\n?[^)]*?\)\s*\{""").findAll(sources).count { !it.value.contains("spot") }
        assertTrue(AppMap.spotIds.size >= 40, "${AppMap.spotIds.size} Ziele")
        assertTrue(bare < 12, "$bare Schalter ohne Leuchtrahmen")
    }

    @Test
    fun toursAreDataWithPlacesOfTheMap() {
        assertTrue(LoraTours.ALL.map { it.id }.containsAll(listOf("pdf", "files", "templates", "video")))
        LoraTours.ALL.forEach { t ->
            assertTrue(t.steps.size in 3..6, "${t.id}: ${t.steps.size} Schritte")
            t.steps.mapNotNull { it.spot }.forEach { spot -> assertTrue(spot in AppMap.spotIds, "${t.id}: $spot nicht auf der Landkarte") }
        }
        assertTrue(LoraTours.available(android = true).none { it.id == "video" })
        assertEquals("pdf", LoraTours.find("Zeig mir, wie das geht", "pdf")?.id)
        assertEquals("files", LoraTours.find("Tour durch Dateien", "chat")?.id)
        withContainer { c ->
            val reply = LoraAgent(c).localTour("Zeig mir, wie das geht", android = false)
            // On the chat there is no tour: Lora names the tools that have one.
            assertNotNull(reply)
            assertTrue(reply.steps.isEmpty() || reply.steps.single() is LoraStep.Tour)
            LoraTours.markSeen(c, "pdf")
            assertTrue("pdf" in LoraTours.seen(c))
            LoraTours.onRoute(c, "pdf", android = false)
            assertNull(LoraTours.offer.value, "nur einmal angeboten")
        }
    }

    // ── AP-42: Umbenennen und Löschen im Dateidialog ──────────────────────────────────────────────────

    @Test
    fun renameAndDeleteInTheFileDialog() {
        val dir = Files.createTempDirectory("dialog").toFile()
        try {
            val a = File(dir, "a.txt").apply { writeText("a") }
            File(dir, "b.txt").writeText("b")
            assertEquals("Diesen Namen gibt es hier schon.", renameProblem(a, "b.txt"))
            assertNotNull(renameProblem(a, "x:y.txt"))
            assertNotNull(renameProblem(a, " "))
            assertNull(renameProblem(a, "c.txt"))
            val c = renameEntry(null, a, "c.txt")
            assertTrue(c.isFile && !a.exists())
            val sub = File(dir, "Ordner").apply { mkdirs(); File(this, "x.txt").writeText("x") }
            deleteEntries(null, listOf(sub, c), inLora = false, bin = false)
            assertFalse(sub.exists() || c.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
