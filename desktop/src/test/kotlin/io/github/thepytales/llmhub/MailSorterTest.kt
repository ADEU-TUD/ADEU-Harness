package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ActionArea
import io.github.thepytales.llmhub.agent.ActionFailed
import io.github.thepytales.llmhub.agent.FlowStep
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraFlow
import io.github.thepytales.llmhub.agent.LoraFlows
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.MailSortRun
import io.github.thepytales.llmhub.agent.MailSortWish
import io.github.thepytales.llmhub.agent.MailSorter
import io.github.thepytales.llmhub.agent.SortModel
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.data.BusyTimes
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.ImapBridge
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lora sortiert selbst (AP-69 b–d): with a mailbox and a model on this device made up for the test. */
class MailSorterTest {
    /** A mailbox in memory that, like Outlook, gives every moved mail a new id; it records what the sorter asks of it. */
    private class FakeBox : OutlookBridge {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val folders = mutableListOf(
            MailFolder("inbox|s", "\\\\dominik@tu-dresden.de\\Posteingang", "Posteingang"),
            MailFolder("lehre|s", "\\\\dominik@tu-dresden.de\\Posteingang\\Lehre", "Lehre"),
            MailFolder("sent|s", "\\\\dominik@tu-dresden.de\\Gesendete Elemente", "Gesendete Elemente"),
        )
        private val inbox = folders[0].path
        private val now = System.currentTimeMillis()
        private val hour = 3_600_000L
        val mails = mutableListOf(
            MailHeader("m1|s", inbox, "Studienbüro", "lehre-info@tu-dresden.de", subject = "Vorlesung Statistik: Übungsblatt 3", received = now - 1 * hour, preview = "Das Übungsblatt für die Vorlesung …"),
            MailHeader("m2|s", inbox, "DFG", "info@dfg.de", subject = "DFG-Antrag: Zwischenbericht", received = now - 5 * hour, preview = "Bitte reichen Sie den Zwischenbericht …"),
            MailHeader("m3|s", inbox, "Reisekostenstelle", "reise@tu-dresden.de", subject = "Reisekostenabrechnung", received = now - 26 * hour, preview = "Ihre Abrechnung …"),
            MailHeader("m4|s", inbox, "Studienbüro", "lehre-info@tu-dresden.de", subject = "Klausureinsicht Statistik", received = now - 50 * hour, preview = "Termin der Klausureinsicht …"),
            MailHeader("m5|s", inbox, "Anna", "anna@tu-dresden.de", subject = "Kaffeerunde Freitag", received = now - 70 * hour, preview = "Wer kommt mit?"),
            MailHeader("m6|s", inbox, "Prof. Beispiel", "beispiel@tu-dresden.de", subject = "Gutachten Promotion", received = now - 90 * hour, preview = "Könnten Sie das Gutachten …"),
            MailHeader("m7|s", inbox, "Alt", "alt@tu-dresden.de", subject = "Alte Mail", received = now - 20 * 24 * hour, preview = "Längst erledigt"),
        )
        /** Ids handed to [moveTracked], in order. */
        val movedIds: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private var next = 100

        fun where(subject: String) = synchronized(this) { mails.first { it.subject == subject } }

        override fun connect() = "dominik@tu-dresden.de"
        override fun folders() = synchronized(this) { folders.toList() }
        override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int): List<MailHeader> = synchronized(this) {
            calls += "mails"
            val path = folders.firstOrNull { it.id == (folderId ?: "inbox|s") }?.path
            mails.filter { it.folder == path && (since == null || it.received >= since) }.sortedByDescending { it.received }.take(limit)
        }
        override fun mail(id: String): MailBody? = null
        override fun move(ids: List<String>, folderId: String): Int = moveTracked(ids, folderId).size
        override fun moveTracked(ids: List<String>, folderId: String): Map<String, String> = synchronized(this) {
            calls += "moveTracked"
            movedIds += ids
            val target = folders.first { it.id == folderId }
            val out = LinkedHashMap<String, String>()
            mails.replaceAll { m ->
                if (m.id in ids) {
                    val fresh = "n${next++}|s"
                    out[m.id] = fresh
                    m.copy(id = fresh, folder = target.path)
                } else m
            }
            out
        }
        override fun createFolder(parentId: String?, name: String): MailFolder = synchronized(this) {
            calls += "createFolder:$name"
            val parent = folders.first { it.id == (parentId ?: "inbox|s") }
            MailFolder("${name.lowercase()}-${folders.size}|s", parent.path + "\\" + name, name).also { folders += it }
        }
        override fun draft(draft: MailDraft): String { calls += "draft"; error("never") }
        override fun events(from: Long, to: Long) = emptyList<CalendarEntry>()
        override fun createEvent(event: NewEvent): String { calls += "event"; error("never") }
        override fun createTask(task: NewTask): String { calls += "task"; error("never") }
        override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int) = emptyList<BusyTimes>()
        override fun show(id: String) { calls += "show" }
    }

    /** A model „on this device“: answers by subject; [wait] slows each answer (for pause and restart). */
    private class FakeModel(val wait: Long = 0) : SortModel {
        override val id = "local/test-model"
        val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val systems: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val count = AtomicInteger()
        override suspend fun answer(system: String, user: String): String {
            if (wait > 0) delay(wait)
            count.incrementAndGet()
            systems += system
            if (system.startsWith("Schlage")) return "1. Lehre\n2. Forschung > Anträge\n- **Verwaltung**"
            val subject = user.lines().first { it.startsWith("Betreff: ") }.removePrefix("Betreff: ")
            asked += subject
            fun n(part: String) = system.lines().firstOrNull { "= " in it && part in it.substringAfter("= ") }?.substringBefore(' ') ?: "0"
            return when {
                "Vorlesung" in subject -> "${n("Lehre")}|90|Vorlesung"
                "DFG" in subject -> "${n("Forschung")}|85|Antrag"
                "Reise" in subject -> "Nummer: ${n("Verwaltung")}, Sicherheit: 80, Grund: Reisekosten"
                "Klausur" in subject -> "${n("Lehre")}|70|Prüfung"
                "Kaffee" in subject -> "0|20|privat"
                else -> "Hm, schwer zu sagen."
            }
        }
    }

    private val base = Files.createTempDirectory("adeu-mailsort").toFile()
    private val box = FakeBox()
    private val c by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        Outlook.testBridge = box
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    @AfterTest
    fun cleanUp() {
        MailSorter.forgetForTest()
        MailSorter.testModel = null
        Outlook.testBridge = null
        base.deleteRecursively()
    }

    private fun await(id: String): MailSortRun = runBlocking { withTimeout(20_000) { MailSorter.await(c, id) } }

    private fun waitFor(what: String, check: () -> Boolean) = runBlocking {
        withTimeout(20_000) { while (!check()) delay(10) }
    }.also { assertTrue(check(), what) }

    private fun item(run: MailSortRun, subject: String) = run.items.first { it.subject == subject }

    @Test
    fun theSentenceBecomesTheOrder() {
        val today = LocalDate.of(2026, 11, 9)
        val zone = ZoneId.systemDefault()
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

        val a = MailSortWish.spec("Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung und Verwaltung ein", today)
        assertEquals(listOf("Lehre", "Forschung", "Verwaltung"), a.targets)
        assertEquals(7, a.days)
        assertEquals("", a.source)
        assertFalse(a.noAsk || a.propose)

        val b = MailSortWish.spec("Sortiere die Mails aus dem Ordner Projekte seit 1.11.2026 in die Ordner „Lehre/WS 2026-27“ und Verwaltung ohne Rückfrage", today)
        assertEquals("Projekte", b.source)
        assertEquals(ms(LocalDate.of(2026, 11, 1)), b.since)
        assertEquals(listOf("Lehre/WS 2026-27", "Verwaltung"), b.targets)
        assertTrue(b.noAsk)

        val c2 = MailSortWish.spec("Ordne meine Mails der letzten 2 Wochen nach Lehre > Prüfungen, Forschung ein", today)
        assertEquals(14, c2.days)
        assertEquals(listOf("Lehre/Prüfungen", "Forschung"), c2.targets)

        val d = MailSortWish.spec("Ordne die Mails vom 2.11. bis 6.11. in Lehre und Forschung ein", today)
        assertEquals(ms(LocalDate.of(2026, 11, 2)), d.since)
        assertEquals(ms(LocalDate.of(2026, 11, 7)), d.until)

        val e = MailSortWish.spec("Räum meinen Posteingang auf und schlag mir sinnvolle Ordner vor", today)
        assertTrue(e.propose && e.targets.isEmpty(), e.toString())

        val f = MailSortWish.spec("Ordne jeden Montag die Mails der letzten Woche in Lehre und Forschung ein", today)
        assertEquals(listOf(1), f.repeatDays)
        assertEquals(listOf("Lehre", "Forschung"), f.targets)

        val october = MailSortWish.range("die Mails im Oktober", today)
        assertEquals(ms(LocalDate.of(2026, 10, 1)), october.since)
        assertEquals(ms(LocalDate.of(2026, 11, 1)), october.until)
        assertTrue(MailSortWish.range("Ordne die Mails ein", today).default)

        assertTrue(MailSortWish.looksLikeSort("Geh die Mails der letzten Woche durch und ordne sie in Lehre ein"))
        assertFalse(MailSortWish.looksLikeSort("Wie sortiere ich Mails in Outlook?"))
        assertFalse(MailSortWish.looksLikeSort("Sortiere die Tabelle nach Note"))

        // The compact answer of the model, read strictly.
        val t = listOf("Lehre", "Forschung", "Verwaltung")
        assertEquals(Triple(1, 85, "Antrag"), MailSorter.parseAnswer("2|85|Antrag", t))
        assertEquals(-1, MailSorter.parseAnswer("0|10|nichts", t)!!.first)
        assertEquals(Triple(2, 70, "Reise"), MailSorter.parseAnswer("Nummer: 3, Sicherheit: 70, Grund: Reise", t))
        assertNull(MailSorter.parseAnswer("7|90|zu groß", t))
        assertNull(MailSorter.parseAnswer("Keine Ahnung.", t))
        assertEquals(listOf("Lehre", "Forschung/Anträge", "Verwaltung"), MailSorter.parseProposal("1. Lehre\n2. Forschung > Anträge\n- **Verwaltung**"))

        // New UIDs of the mail server (UIDPLUS).
        assertEquals(mapOf(304L to 3956L, 319L to 3957L, 320L to 3958L), ImapBridge.copyUid("OK [COPYUID 38505 304,319:320 3956:3958] erledigt"))
        assertTrue(ImapBridge.copyUid("OK verschoben").isEmpty())
    }

    @Test
    fun previewOneClickAndUndoWithTheNewIds() {
        val model = FakeModel()
        MailSorter.testModel = model
        val reply = runBlocking { LoraAgent(c).ask("Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung/Anträge und Verwaltung ein") }
        val step = reply.steps.single() as LoraStep.MailSort
        val review = await(step.runId)
        assertEquals(MailSortRun.REVIEW, review.state, review.message)
        // Only the mails of the week, read by the model on this device; nothing changed in Outlook yet.
        assertEquals(6, review.items.size)
        assertFalse(model.asked.contains("Alte Mail"))
        assertTrue(box.calls.none { it.startsWith("createFolder") || it == "moveTracked" }, box.calls.toString())
        assertEquals("lehre|s", review.targets[0].folderId)
        assertNull(review.targets[1].folderId, "Forschung/Anträge ist neu")
        // Uncertain ones apart: no folder fitted, or the answer was unclear.
        assertTrue(item(review, "Kaffeerunde Freitag").let { it.uncertain && it.skip })
        assertTrue(item(review, "Gutachten Promotion").let { it.uncertain && it.skip })
        assertEquals(2, item(review, "Reisekostenabrechnung").target)

        // The person changes the preview: one mail to another folder, one left out, one uncertain one taken in.
        MailSorter.setTarget(c, review.id, "m4|s", 1)
        MailSorter.setSkip(c, review.id, "m3|s", true)
        MailSorter.setTarget(c, review.id, "m6|s", 1)
        assertEquals(4, MailSorter.get(c, review.id)!!.toMove.size)
        MailSorter.confirm(c, review.id)
        val done = await(review.id)
        assertEquals(MailSortRun.DONE, done.state, done.message)

        // Missing folders were made in Outlook, also the subfolder under its new parent.
        assertTrue(listOf("createFolder:Forschung", "createFolder:Anträge", "createFolder:Verwaltung").all { it in box.calls }, box.calls.toString())
        val antraege = box.folders().first { it.name == "Anträge" }
        assertTrue(antraege.path.endsWith("Posteingang\\Forschung\\Anträge"), antraege.path)
        assertEquals(box.folders[1].path, box.where("Vorlesung Statistik: Übungsblatt 3").folder)
        listOf("DFG-Antrag: Zwischenbericht", "Klausureinsicht Statistik", "Gutachten Promotion").forEach { assertEquals(antraege.path, box.where(it).folder, it) }
        listOf("Reisekostenabrechnung", "Kaffeerunde Freitag").forEach { assertEquals(box.folders[0].path, box.where(it).folder, it) }
        // The new ids Outlook gave are kept.
        done.items.filter { it.moved }.forEach { m -> assertEquals(box.where(m.subject).id, m.movedTo) }
        // Every move is in the protocol.
        val log = c.outlookLog.current
        assertTrue(log.any { it.what == "Verschoben" && "Vorlesung Statistik" in it.detail }, log.toString())
        assertTrue(log.any { it.what == "Ordner" && "Anträge" in it.detail }, log.toString())

        // Rückgängig for the whole run, with the new ids; the folders stay.
        val before = box.movedIds.size
        MailSorter.undo(c, done.id)
        val undone = await(done.id)
        assertEquals(MailSortRun.UNDONE, undone.state, undone.message)
        assertEquals(done.items.mapNotNull { it.movedTo }.toSet(), box.movedIds.drop(before).toSet())
        box.mails.filter { it.subject != "Alte Mail" }.forEach { assertEquals(box.folders[0].path, it.folder, it.subject) }
        assertTrue(box.folders().any { it.name == "Anträge" } && box.folders().any { it.name == "Verwaltung" })
        assertTrue(c.outlookLog.current.any { it.what == "Zurückgeschoben" })

        // Never deleted or sent: the sorter only listed, made folders and moved.
        assertTrue(box.calls.all { it == "mails" || it == "moveTracked" || it.startsWith("createFolder:") }, box.calls.toString())
        val names = OutlookBridge::class.java.methods.map { it.name.lowercase() }
        assertTrue(names.none { it.contains("delete") || it.contains("send") || it.contains("remove") }, names.toString())

        // Corrections become examples only after asking; the next run's prompt has them.
        assertEquals(setOf("Klausureinsicht Statistik", "Gutachten Promotion"), MailSorter.corrections(undone).map { it.subject }.toSet())
        assertTrue(MailSorter.examples(c).value.isEmpty())
        MailSorter.rememberCorrections(c, undone.id, true)
        assertEquals(2, MailSorter.examples(c).value.size)
        assertTrue(MailSorter.get(c, undone.id)!!.examplesAsked)
        val prompt = MailSorter.classifierPrompt(listOf("Lehre", "Forschung/Anträge"), MailSorter.examples(c).value)
        assertTrue("„Klausureinsicht Statistik“ (Studienbüro) → Forschung/Anträge" in prompt, prompt)
    }

    @Test
    fun withoutAskingUncertainOnesStayAndRulesAndRepeatsComeFromARun() {
        MailSorter.testModel = FakeModel()
        val run = MailSorter.start(c, MailSortWish.spec("Ordne die Mails der letzten Woche ohne Rückfrage in Lehre, Forschung und Verwaltung ein"))
        assertTrue(run.spec.noAsk)
        val done = await(run.id)
        assertEquals(MailSortRun.DONE, done.state, done.message)
        assertEquals(box.folders[1].path, box.where("Klausureinsicht Statistik").folder)
        assertTrue(box.where("Reisekostenabrechnung").folder.endsWith("\\Verwaltung"))
        listOf("Kaffeerunde Freitag", "Gutachten Promotion").forEach { assertEquals(box.folders[0].path, box.where(it).folder, "unsicher bleibt: $it") }
        assertTrue("unsichere" in MailSorter.summary(done), MailSorter.summary(done))

        // Stufe d: a fixed rule from the sender that only went to Lehre …
        val rules = MailSorter.ruleSuggestions(c, done)
        assertEquals(listOf("lehre-info@tu-dresden.de"), rules.map { it.from })
        assertEquals("lehre|s", rules.single().folder)
        MailSorter.saveRules(c, rules)
        assertEquals(1, c.mailRules.current.size)
        // … and a scheduled task that repeats the order with the model on this device.
        val task = MailSorter.scheduledTask(done, listOf(1), 8, noAsk = true)
        c.tasks.upsert(task)
        val stored = c.tasks.get(task.id)!!
        assertEquals(listOf("Lehre", "Forschung", "Verwaltung"), stored.mailSort!!.targets)
        assertEquals(7, stored.mailSort!!.days)
        assertEquals(listOf(1), stored.days)
        assertTrue(io.github.thepytales.llmhub.data.Privacy.isLocal(stored.model))
        val text = runBlocking { withTimeout(20_000) { MailSorter.runScheduled(c, stored) } }
        assertTrue("eingeordnet" in text, text)
    }

    @Test
    fun pausesCancelsAndCarriesOnAfterARestart() {
        val model = FakeModel(wait = 150)
        MailSorter.testModel = model
        val run = MailSorter.start(c, MailSortWish.spec("Ordne die Mails der letzten Woche in Lehre, Forschung und Verwaltung ein"))
        waitFor("zwei gelesen") { (MailSorter.get(c, run.id)?.classifiedCount ?: 0) >= 2 }
        MailSorter.pause(c, run.id)
        val paused = MailSorter.get(c, run.id)!!
        assertEquals(MailSortRun.PAUSED, paused.state)
        val read = paused.classifiedCount
        assertTrue(read in 2 until 6, read.toString())
        runBlocking { delay(400) }
        assertEquals(read, MailSorter.get(c, run.id)!!.classifiedCount, "pausiert liest nicht weiter")

        // „Restart“: the app ends; the run comes back from its file, paused, and goes on with „Fortsetzen“.
        MailSorter.forgetForTest()
        val again = MailSorter.get(c, run.id)!!
        assertEquals(MailSortRun.PAUSED, again.state)
        assertEquals(read, again.classifiedCount)
        MailSorter.proceed(c, run.id)
        val review = await(run.id)
        assertEquals(MailSortRun.REVIEW, review.state, review.message)
        assertEquals(6, review.classifiedCount)
        assertTrue(model.count.get() <= 7, "schon Gelesenes wird nicht noch einmal gelesen: ${model.count.get()}")

        // A run that was working when the app ended carries on by itself after the start.
        val second = MailSorter.start(c, MailSortWish.spec("Ordne die Mails der letzten Woche in Lehre und Forschung ein"))
        waitFor("eine gelesen") { (MailSorter.get(c, second.id)?.classifiedCount ?: 0) >= 1 }
        MailSorter.forgetForTest()
        assertEquals(MailSortRun.CLASSIFYING, MailSorter.get(c, second.id)!!.state)
        MailSorter.resumeAll(c)
        assertEquals(MailSortRun.REVIEW, await(second.id).state)

        // Abbrechen in the preview: nothing moves.
        MailSorter.cancel(c, review.id)
        assertEquals(MailSortRun.CANCELLED, MailSorter.get(c, review.id)!!.state)
        assertFalse(MailSorter.get(c, review.id)!!.canUndo)
        assertTrue(box.calls.none { it == "moveTracked" || it.startsWith("createFolder") }, box.calls.toString())
    }

    @Test
    fun lorasProposalAndTheActionInAnAblauf() {
        MailSorter.testModel = FakeModel()
        // Lora suggests the folders; the person confirms (or changes) them first.
        val run = MailSorter.start(c, MailSortWish.spec("Räum meinen Posteingang der letzten Woche auf und schlag mir sinnvolle Ordner vor"))
        val folders = await(run.id)
        assertEquals(MailSortRun.FOLDERS, folders.state, folders.message)
        assertEquals(listOf("Lehre", "Forschung/Anträge", "Verwaltung"), folders.targets.map { it.path })
        MailSorter.acceptFolders(c, run.id, listOf("Lehre", "Verwaltung"))
        val review = await(run.id)
        assertEquals(MailSortRun.REVIEW, review.state, review.message)
        assertEquals(listOf("Lehre", "Verwaltung"), review.targets.map { it.path })

        // mail.sort is an action of the register (AP-38) and runs in an Ablauf (AP-40).
        val action = ToolActions.byId(MailSorter.ACTION_ID)
        assertNotNull(action)
        assertEquals("act_mail_sort", action.toolName)
        assertEquals(ActionArea.MAIL, ActionArea.of("mail"))
        assertTrue(ToolActions.forArea(ActionArea.MAIL, android = true).any { it.id == MailSorter.ACTION_ID })
        val (flow, dropped) = LoraFlows.draft(c, "Mails einordnen", listOf(MailSorter.ACTION_ID to mapOf("ziele" to "Lehre, Verwaltung", "zeitraum" to "letzte 7 Tage", "ohne_rueckfrage" to "ja")), android = false)!!
        assertTrue(dropped.isEmpty())
        LoraFlows.start(c, flow.id)
        waitFor("Ablauf fertig") { LoraFlows.get(c, flow.id)?.state == LoraFlow.DONE }
        val step = LoraFlows.get(c, flow.id)!!.steps.single()
        assertEquals(FlowStep.DONE, step.status)
        assertTrue("eingeordnet" in step.result, step.result)
        assertTrue(box.where("Reisekostenabrechnung").folder.endsWith("\\Verwaltung"))
    }

    @Test
    fun withoutAModelOnThisDeviceThereIsNoRun() {
        MailSorter.testModel = null
        assertNull(MailSorter.localModel(c))
        val reply = MailSorter.fromWish(c, "Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung und Verwaltung ein")!!
        assertTrue("Modell auf diesem Gerät" in reply.text, reply.text)
        assertTrue(reply.steps.none { it is LoraStep.MailSort })
        assertFailsWith<ActionFailed> { MailSorter.start(c, MailSortWish.spec("Ordne die Mails in Lehre ein")) }
        assertFailsWith<ActionFailed> { runBlocking { ToolActions.byId(MailSorter.ACTION_ID)!!.plan(c, JsonObject(mapOf("ziele" to JsonPrimitive("Lehre")))) } }
        assertTrue(MailSorter.runs(c).value.isEmpty())
        assertTrue(box.calls.isEmpty(), "ohne lokales Modell wird das Postfach nicht einmal gelesen: ${box.calls}")
    }
}
