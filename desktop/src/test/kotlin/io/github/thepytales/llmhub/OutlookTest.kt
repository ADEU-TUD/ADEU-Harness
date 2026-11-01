package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.OutlookTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.BusyTimes
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookBridge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mail und Kalender über das klassische Outlook (AP-13), with an Outlook made up for the test. */
class OutlookTest {
    private val zone = ZoneId.of("Europe/Berlin")

    /** An Outlook in memory: what the app did is in its lists; it has no delete and no send either. */
    private class FakeOutlook : OutlookBridge {
        val folders = mutableListOf(
            MailFolder("inbox|s", "\\\\dominik@tu-dresden.de\\Posteingang", "Posteingang", 3, 2),
            MailFolder("news|s", "\\\\dominik@tu-dresden.de\\Posteingang\\Newsletter", "Newsletter"),
            MailFolder("sent|s", "\\\\dominik@tu-dresden.de\\Gesendete Elemente", "Gesendete Elemente"),
        )
        val mails = mutableListOf(
            MailHeader("m1|s", folders[0].path, "SLUB Dresden", "news@slub-dresden.de", subject = "Newsletter November", received = 3_000, preview = "Neue Datenbanken …"),
            MailHeader("m2|s", folders[0].path, "Prof. Beispiel", "beispiel@tu-dresden.de", subject = "Gutachten bis 15.11.", received = 2_000, unread = true, important = true, preview = "Könnten Sie das Gutachten bis zum 15.11. schicken?"),
            MailHeader("m3|s", folders[0].path, "Prüfungsamt", subject = "Noteneintragung", received = 1_000, unread = true, preview = "Frist für die Noteneintragung ist der 30.11."),
        )
        val drafts = mutableListOf<MailDraft>()
        val events = mutableListOf<NewEvent>()
        val tasks = mutableListOf<NewTask>()

        override fun connect() = "dominik@tu-dresden.de"
        override fun folders() = folders.toList()
        override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int): List<MailHeader> {
            val path = folders.firstOrNull { it.id == (folderId ?: "inbox|s") }?.path
            return mails.filter { it.folder == path && (!unreadOnly || it.unread) && (since == null || it.received >= since) }.sortedByDescending { it.received }.take(limit)
        }
        override fun mail(id: String) = mails.firstOrNull { it.id == id }?.let { MailBody(it, body = it.preview + "\n\nViele Grüße") }
        override fun move(ids: List<String>, folderId: String): Int {
            val target = folders.first { it.id == folderId }
            var n = 0
            mails.replaceAll { m -> if (m.id in ids) m.copy(folder = target.path).also { n++ } else m }
            return n
        }
        override fun createFolder(parentId: String?, name: String): MailFolder {
            val parent = folders.first { it.id == (parentId ?: "inbox|s") }
            return MailFolder("${name.lowercase()}|s", parent.path + "\\" + name, name).also { folders += it }
        }
        override fun draft(draft: MailDraft): String = "d${drafts.size + 1}|s".also { drafts += draft }
        override fun events(from: Long, to: Long) = listOf(CalendarEntry("e1|s", "Sprechstunde", from + 10 * 3_600_000L, from + 11 * 3_600_000L, "Raum 112"))
        override fun createEvent(event: NewEvent): String = "e${events.size + 2}|s".also { events += event }
        override fun createTask(task: NewTask): String = "t${tasks.size + 1}|s".also { tasks += task }
        override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int) = people.map { p ->
            // Everyone busy from 8 to 10 every day; „Unbekannt“ is not in the address book.
            val perDay = 24 * 60 / slotMinutes
            val states = buildString { repeat(30) { repeat(perDay) { i -> append(if (i * slotMinutes in 8 * 60 until 10 * 60) '2' else '0') } } }
            BusyTimes(p, p != "Unbekannt", java.time.Instant.ofEpochMilli(day).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), slotMinutes, states)
        }
        override fun show(id: String) = Unit
    }

    private val base = Files.createTempDirectory("adeu-outlook").toFile()
    private val fake = FakeOutlook()
    private val c by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        Outlook.testBridge = fake
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    @AfterTest
    fun cleanUp() {
        Outlook.testBridge = null
        base.deleteRecursively()
    }

    private fun call(name: String, vararg pairs: Pair<String, Any>): String = runBlocking {
        val args = JsonObject(pairs.associate { (k, v) ->
            k to when (v) {
                is List<*> -> JsonArray(v.map { JsonPrimitive(it.toString()) })
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        })
        c.outlookTools.execute(name, args.toString(), RepoContext(mutableListOf(), chatId = "chat-1")).text
    }

    @Test
    fun readsSortsAndDraftsWithoutDeletingOrSending() {
        val list = call(OutlookTools.LIST_MAILS)
        assertTrue("3 Mails" in list && "2 ungelesen" in list, list)
        // Short ids instead of Outlook's long ones.
        assertTrue("[M1]" in list && "[M3]" in list && "m1|s" !in list, list)
        assertTrue("Gutachten bis 15.11." in list && "wichtig" in list)
        val read = call(OutlookTools.READ_MAIL, "id" to "M2")
        assertTrue("Von: Prof. Beispiel <beispiel@tu-dresden.de>" in read && "Viele Grüße" in read, read)
        // Only unread, and a search.
        assertTrue("2 Mails" in call(OutlookTools.LIST_MAILS, "unread_only" to true))
        assertTrue("1 Mails" in call(OutlookTools.LIST_MAILS, "query" to "slub"))

        // Moving asks first; the folder is found by its name.
        assertEquals(Risk.PUBLIC, c.outlookTools.risk(OutlookTools.MOVE_MAILS, JsonObject(emptyMap())))
        assertEquals(Risk.EDIT, c.outlookTools.risk(OutlookTools.SAVE_DRAFT, JsonObject(emptyMap())))
        assertNull(c.outlookTools.risk(OutlookTools.LIST_MAILS, JsonObject(emptyMap())))
        val moved = call(OutlookTools.MOVE_MAILS, "ids" to listOf("M1"), "folder" to "Newsletter")
        assertTrue("1 von 1" in moved && "Newsletter" in moved, moved)
        assertEquals(fake.folders[1].path, fake.mails.first { it.id == "m1|s" }.folder)
        assertTrue(call(OutlookTools.MOVE_MAILS, "ids" to listOf("M2"), "folder" to "Gibtsnicht").startsWith("FEHLER"))

        // An answer as a draft, never sent.
        val draft = call(OutlookTools.SAVE_DRAFT, "reply_to" to "M2", "body" to "Sehr geehrter Herr Beispiel,\n\ngern, bis 15.11.\n\nViele Grüße")
        assertTrue("Entwürfe" in draft && "nicht gesendet" in draft, draft)
        assertEquals("m2|s", fake.drafts.single().replyTo)
        assertTrue(call(OutlookTools.SAVE_DRAFT, "body" to "Hallo").startsWith("FEHLER"), "neue Mail ohne Empfänger")
        // The bridge has no way to delete or send at all.
        val names = OutlookBridge::class.java.methods.map { it.name.lowercase() }
        assertTrue(names.none { it.contains("delete") || it.contains("send") || it.contains("remove") }, names.toString())
        // Every change is in the protocol.
        val log = c.outlookLog.current.map { it.what }
        assertTrue("Verschoben" in log && "Entwurf" in log, log.toString())
    }

    @Test
    fun deadlinesBecomeAppointmentsAndInvitationsStayUnsent() {
        val deadline = call(OutlookTools.CREATE_EVENT, "subject" to "Frist Noteneintragung", "start" to "2026-11-30")
        assertTrue("eingetragen" in deadline, deadline)
        val e = fake.events.single()
        assertTrue(e.allDay && e.end - e.start == 86_400_000L && e.reminderMinutes == 1440, e.toString())
        val meeting = call(OutlookTools.CREATE_EVENT, "subject" to "Absprache Gutachten", "start" to "2026-11-04 14:00", "duration_minutes" to 30, "attendees" to listOf("Prof. Beispiel"))
        assertTrue("NICHT gesendet" in meeting, meeting)
        assertEquals(listOf("Prof. Beispiel"), fake.events.last().attendees)
        assertEquals(30 * 60_000L, fake.events.last().end - fake.events.last().start)
        assertTrue(call(OutlookTools.CREATE_EVENT, "subject" to "x", "start" to "irgendwann").startsWith("FEHLER"))
        assertTrue("fällig" in call(OutlookTools.CREATE_TASK, "subject" to "Gutachten schicken", "due" to "15.11.2026"))
        assertNotNull(fake.tasks.single().due)
        assertTrue("Sprechstunde" in call(OutlookTools.LIST_EVENTS, "from" to "2026-11-02", "days" to 1))
    }

    @Test
    fun freeTimesOfEveryoneOnWorkingDays() {
        val day = LocalDate.of(2026, 11, 2) // a Monday
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val perDay = 24 * 2
        fun busy(person: String, blocked: IntRange) = BusyTimes(person, true, from, 30, buildString { repeat(30) { repeat(perDay) { i -> append(if (i in blocked) '2' else '0') } } })
        // Both busy 8–10, the colleague also 10–12 (slots 16..23).
        val slots = Outlook.freeSlots(listOf(busy("ich", 16..19), busy("Kollegin", 16..23)), from, from + 7 * 86_400_000L, 60, zone = zone, limit = 4, perDay = 2)
        val first = java.time.Instant.ofEpochMilli(slots.first().first).atZone(zone)
        assertEquals(LocalTime.of(12, 0), first.toLocalTime())
        assertEquals(day, first.toLocalDate())
        // Two per day, then the next working day; never at the weekend.
        assertEquals(4, slots.size)
        assertTrue(slots.all { java.time.Instant.ofEpochMilli(it.first).atZone(zone).dayOfWeek.value <= 5 })
        assertTrue(slots[1].first >= slots[0].last + 30 * 60_000L, "Pause zwischen zwei Vorschlägen")
        // Tentative counts as busy, „woanders tätig“ as free.
        val tentative = BusyTimes("x", true, from, 30, "1".repeat(perDay * 30))
        assertFalse(Outlook.free(tentative, from + 9 * 3_600_000L, from + 10 * 3_600_000L))
        assertTrue(Outlook.free(BusyTimes("x", true, from, 30, "4".repeat(perDay)), from + 9 * 3_600_000L, from + 10 * 3_600_000L))
        // The tool leaves out people Outlook does not know – and says so.
        val text = call(OutlookTools.FREE_TIMES, "people" to listOf("Kollegin", "Unbekannt"), "from" to "2026-11-02", "days" to 3)
        assertTrue("Nicht gefunden" in text && "Unbekannt" in text && "Gemeinsam frei" in text, text)
    }

    @Test
    fun rulesMoveOnlyWhenApplied() {
        val saved = call(OutlookTools.SAVE_RULE, "name" to "Newsletter SLUB", "from" to "slub", "folder" to "Posteingang\\Newsletter")
        assertTrue("gespeichert" in saved, saved)
        assertEquals("news|s", c.mailRules.current.single().folder)
        assertEquals(1, fake.mails.count { it.folder == fake.folders[0].path && it.from.startsWith("SLUB") }, "Speichern verschiebt nichts")
        val preview = call(OutlookTools.APPLY_RULES, "dry_run" to true)
        assertTrue("Würde verschieben" in preview && "Newsletter November" in preview, preview)
        assertNull(c.outlookTools.risk(OutlookTools.APPLY_RULES, JsonObject(mapOf("dry_run" to JsonPrimitive(true)))))
        assertEquals(Risk.PUBLIC, c.outlookTools.risk(OutlookTools.APPLY_RULES, JsonObject(emptyMap())))
        val done = call(OutlookTools.APPLY_RULES)
        assertTrue("1 × „Newsletter SLUB“" in done, done)
        assertEquals(1, c.mailRules.current.single().moved)
        assertTrue("Keine der" in call(OutlookTools.APPLY_RULES), "schon einsortiert")
        // Matching ignores case and accents; a rule without conditions never fits.
        val m = fake.mails.first()
        assertTrue(Outlook.matches(MailRule(name = "x", subject = "NEWSLETTER", folder = "f"), m))
        assertFalse(Outlook.matches(MailRule(name = "x", folder = "f"), m))
        assertFalse(Outlook.matches(MailRule(name = "x", from = "slub", folder = "f", enabled = false), m))
    }

    @Test
    fun foldersAndTimesAreReadLikePeopleWriteThem() {
        val folders = fake.folders
        assertEquals("news|s", Outlook.resolveFolder("Newsletter", folders)?.id)
        assertEquals("news|s", Outlook.resolveFolder("posteingang/newsletter", folders)?.id)
        assertEquals("sent|s", Outlook.resolveFolder("\\\\dominik@tu-dresden.de\\Gesendete Elemente", folders)?.id)
        assertNull(Outlook.resolveFolder("Archiv", folders))
        val today = LocalDate.of(2026, 11, 1)
        fun at(text: String) = Outlook.parseTime(text, zone, today = today)?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toString() }
        assertEquals("2026-11-03T14:00", at("2026-11-03 14:00"))
        assertEquals("2026-11-03T14:00", at("3.11.2026 14:00"))
        assertEquals("2026-11-03T14:30", at("2026-11-03T14:30:00Z"))
        assertEquals("2026-11-03T09:00", at("2026-11-03"))
        assertEquals("2026-11-02T10:15", at("morgen 10:15"))
        assertNull(at("bald"))
        assertTrue(Outlook.dateOnly("2026-11-30") && Outlook.dateOnly("30.11.2026") && !Outlook.dateOnly("2026-11-30 10:00"))
    }

    @Test
    fun theToolAndItsModeAppearOnlyWhenSwitchedOn() {
        // Since AP-27 also on Android (over the mail server).
        assertTrue(AppTool.MAIL.android)
        assertEquals(AppTool.MAIL, AppTool.suggestFor("Kannst du mein Postfach sortieren?", emptySet(), android = false))
        assertEquals(AppTool.MAIL, AppTool.suggestFor("Kannst du mein Postfach sortieren?", emptySet(), android = true))
        ChatModes.enabledTools = { emptySet() }
        assertTrue(ChatModes.visible().none { it.id == "mail" })
        ChatModes.enabledTools = { setOf("mail") }
        assertTrue(ChatModes.visible().any { it.id == "mail" })
        ChatModes.enabledTools = { emptySet() }
        assertTrue("nie" in OutlookTools.MODE_PROMPT.lowercase() && OutlookTools.SAVE_DRAFT in OutlookTools.MODE_PROMPT)
    }
}
