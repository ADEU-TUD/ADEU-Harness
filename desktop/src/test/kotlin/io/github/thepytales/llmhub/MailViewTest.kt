package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.BusyTimes
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.ImapBridge
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailBoxModel
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailFolders
import io.github.thepytales.llmhub.data.MailGroups
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailPage
import io.github.thepytales.llmhub.data.MailQuery
import io.github.thepytales.llmhub.data.MailServerConfig
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.net.ImapConnection
import io.github.thepytales.llmhub.net.ImapParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Die Mailansicht (AP-69 Stufe a): paging, day groups, search, choice and Rückgängig – with a mailbox of over 5 000 mails. */
class MailViewTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 11) // a Wednesday
    private val noon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * A big Outlook in memory: 5 000 mails in the Posteingang (one every 2 hours back from today noon), every third
     * unread, every tenth with an attachment; a move gives the mail a new id like Outlook's EntryID.
     */
    private inner class BigOutlook : OutlookBridge {
        val folders = mutableListOf(
            MailFolder("inbox|s", "\\\\anna@tu-dresden.de\\Posteingang", "Posteingang"),
            MailFolder("proj|s", "\\\\anna@tu-dresden.de\\Posteingang\\Projekte", "Projekte"),
            MailFolder("sent|s", "\\\\anna@tu-dresden.de\\Gesendete Elemente", "Gesendete Elemente"),
        )
        val mails = (0 until 5000).map { i ->
            MailHeader(
                "m$i|s", folders[0].path, if (i % 7 == 0) "Prof. Weber" else "Absender $i", if (i % 7 == 0) "weber@tu-dresden.de" else "a$i@example.org",
                subject = if (i % 5 == 0) "Prüfung Modul $i" else "Nachricht $i", received = noon - i * 2 * 3_600_000L,
                unread = i % 3 == 0, attachments = if (i % 10 == 0) 1 else 0, preview = "Text der Mail $i",
            )
        }.toMutableList()
        var pages = 0
        var moves = 0

        private fun recount() {
            folders.replaceAll { f -> f.copy(items = mails.count { it.folder == f.path }, unread = mails.count { it.folder == f.path && it.unread }) }
        }

        init { recount() }

        override fun connect() = "anna@tu-dresden.de"
        override fun folders(): List<MailFolder> { recount(); return folders.toList() }
        override fun inboxId() = "inbox|s"
        override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int) = page(MailQuery(folderId, since = since, unreadOnly = unreadOnly), 0, limit).mails
        override fun page(query: MailQuery, offset: Int, limit: Int): MailPage {
            pages++
            val path = folders.first { it.id == (query.folderId ?: "inbox|s") }.path
            val all = mails.filter { (query.allFolders || it.folder == path) && Outlook.fits(query, it) }.sortedByDescending { it.received }
            val slice = all.drop(offset).take(limit)
            return MailPage(slice, all.size, offset + slice.size)
        }
        override fun mail(id: String) = mails.firstOrNull { it.id == id }?.let { MailBody(it, body = it.preview) }
        override fun move(ids: List<String>, folderId: String) = moveTracked(ids, folderId).size
        override fun moveTracked(ids: List<String>, folderId: String): Map<String, String> {
            val target = folders.first { it.id == folderId }
            val out = LinkedHashMap<String, String>()
            mails.replaceAll { m -> if (m.id in ids) m.copy(id = "n${++moves}-" + m.id.substringAfterLast('-'), folder = target.path).also { out[m.id] = it.id } else m }
            return out
        }
        override fun markRead(ids: List<String>, read: Boolean): Int {
            var n = 0
            mails.replaceAll { m -> if (m.id in ids) m.copy(unread = !read).also { n++ } else m }
            return n
        }
        override fun renameFolder(id: String, name: String): MailFolder {
            val f = folders.first { it.id == id }
            val path = f.path.substringBeforeLast('\\') + "\\" + name
            mails.replaceAll { m -> if (m.folder == f.path) m.copy(folder = path) else m }
            val now = f.copy(path = path, name = name)
            folders[folders.indexOf(f)] = now
            return now
        }
        override fun createFolder(parentId: String?, name: String): MailFolder {
            val parent = folders.first { it.id == (parentId ?: "inbox|s") }
            folders.firstOrNull { it.path == parent.path + "\\" + name }?.let { return it }
            return MailFolder("${name.lowercase()}|s", parent.path + "\\" + name, name).also { folders += it }
        }
        override fun draft(draft: MailDraft) = "d|s"
        override fun events(from: Long, to: Long) = emptyList<CalendarEntry>()
        override fun createEvent(event: NewEvent) = "e|s"
        override fun createTask(task: NewTask) = "t|s"
        override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int) = emptyList<BusyTimes>()
        override fun show(id: String) = Unit
    }

    private fun model(b: OutlookBridge, log: MutableList<String> = mutableListOf()) =
        MailBoxModel(b, log = { what, detail -> log += "$what: $detail" }, io = Dispatchers.Unconfined)

    @Test
    fun pagesThroughFiveThousandMailsWithStableKeys() = runBlocking {
        val b = BigOutlook()
        val m = model(b)
        m.start()
        var s = m.state.value
        assertEquals("inbox|s", s.query.folderId)
        assertEquals(60, s.mails.size)
        assertEquals(5000, s.total)
        assertTrue(s.hasMore)
        while (m.state.value.hasMore) m.loadMore()
        s = m.state.value
        assertEquals(5000, s.mails.size)
        assertEquals(5000, s.mails.map { it.id }.toSet().size, "every key once")
        assertTrue(s.mails.zipWithNext().all { (a, z) -> a.received >= z.received }, "newest first")
        assertEquals(84, b.pages, "one request per page of 60")
        // Folder tree with unread counts.
        val tree = MailFolders.tree(s.folders)
        assertEquals(listOf("Posteingang", "Projekte", "Gesendete Elemente"), tree.map { it.label })
        assertEquals(listOf(0, 1, 0), tree.map { it.depth })
        assertEquals(1667, tree.first().unread)
        val closed = MailFolders.tree(s.folders, collapsed = setOf(tree.first().key))
        assertEquals(listOf("Posteingang", "Gesendete Elemente"), closed.map { it.label })
    }

    @Test
    fun groupsByDayLikeOutlook() {
        fun at(d: LocalDate, h: Int = 9) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        fun label(d: LocalDate) = MailGroups.label(at(d), today, zone, Locale.GERMAN)
        assertEquals(MailGroups.TODAY, label(today))
        assertEquals(MailGroups.YESTERDAY, label(today.minusDays(1)))
        assertEquals(MailGroups.THIS_WEEK, label(LocalDate.of(2026, 11, 9)))
        assertEquals(MailGroups.LAST_WEEK, label(LocalDate.of(2026, 11, 2)))
        assertEquals(MailGroups.THIS_MONTH, label(LocalDate.of(2026, 11, 1)))
        assertEquals(MailGroups.LAST_MONTH, label(LocalDate.of(2026, 10, 3)))
        assertEquals("September 2026", label(LocalDate.of(2026, 9, 30)))
        assertEquals("September 2026", MailGroups.label(at(LocalDate.of(2026, 9, 30)), today, zone, Locale.ENGLISH))
        assertEquals(MailGroups.UNKNOWN, MailGroups.label(0, today, zone))
        val groups = MailGroups.groups(BigOutlook().mails, today, zone)
        assertEquals(5000, groups.sumOf { it.second.size })
        assertEquals(groups.size, groups.map { it.first }.toSet().size, "each group once, in a row")
        assertEquals(listOf(MailGroups.TODAY, MailGroups.YESTERDAY, MailGroups.THIS_WEEK, MailGroups.LAST_WEEK, MailGroups.THIS_MONTH, MailGroups.LAST_MONTH), groups.take(6).map { it.first })
        assertEquals(7, groups[0].second.size, "noon back to midnight every 2 hours")
    }

    @Test
    fun searchesAndFilters() = runBlocking {
        val q = MailQuery.parse("von:weber betreff:\"Prüfung Modul\" Text 35")
        assertEquals("weber", q.from)
        assertEquals("Prüfung Modul", q.subject)
        assertEquals("Text 35", q.text)
        val b = BigOutlook()
        val m = model(b)
        m.start()
        m.search(q.copy(folderId = "inbox|s"))
        // i % 7 == 0, i % 5 == 0 and the preview contains „35“: 35, 350–359 (only 350), 1435 …
        val hits = m.state.value.mails
        assertTrue(hits.isNotEmpty() && hits.all { it.from == "Prof. Weber" && it.subject.startsWith("Prüfung Modul") && "35" in it.preview }, hits.map { it.subject }.toString())
        m.search(MailQuery("inbox|s", unreadOnly = true, withAttachments = true))
        while (m.state.value.hasMore) m.loadMore()
        assertEquals((0 until 5000).count { it % 3 == 0 && it % 10 == 0 }, m.state.value.mails.size)
        val since = noon - 24 * 3_600_000L
        m.search(MailQuery("inbox|s", since = since, until = noon))
        assertTrue(m.state.value.mails.all { it.received in since until noon } && m.state.value.mails.size == 12)
        // Accents and case do not matter.
        assertTrue(Outlook.fits(MailQuery(subject = "prufung"), b.mails[0]))
        assertFalse(Outlook.fits(MailQuery(from = "weber"), b.mails[1]))
    }

    @Test
    fun choosesWithCtrlShiftArrowsAndTellsLora() = runBlocking {
        val m = model(BigOutlook())
        m.start()
        val ids = m.state.value.mails.map { it.id }
        m.click(ids[2])
        assertEquals(ids[2], m.state.value.open)
        m.click(ids[5], range = true)
        assertEquals(ids.subList(2, 6).toSet(), m.state.value.selected)
        m.click(ids[9], toggle = true)
        m.click(ids[3], toggle = true)
        assertEquals(setOf(ids[2], ids[4], ids[5], ids[9]), m.state.value.selected)
        assertNull(m.state.value.open, "several chosen: no single mail open")
        m.click(ids[0])
        m.step(1)
        m.step(1, extend = true)
        assertEquals(setOf(ids[1], ids[2]), m.state.value.selected)
        m.step(-5)
        assertEquals(setOf(ids[0]), m.state.value.selected)
        m.selectAll()
        assertEquals(60, m.state.value.selected.size)
        val sel = assertNotNull(m.selection())
        assertEquals(60, sel.count)
        assertEquals("Posteingang", sel.folder)
        assertEquals(m.state.value.mails.map { it.subject }, sel.subjects)
        m.clearSelection()
        assertNull(m.selection())
    }

    @Test
    fun movesMarksAndRenamesWithUndo() = runBlocking {
        val b = BigOutlook()
        val log = mutableListOf<String>()
        val m = model(b, log)
        m.start()
        val first = m.state.value.mails.take(3)
        val projekte = m.state.value.folders.first { it.name == "Projekte" }
        val unreadBefore = m.state.value.folders.first { it.id == "inbox|s" }.unread
        // Move three mails: gone from the list, counts follow, the bridge gave new ids.
        val moved = m.move(first.map { it.id }, projekte)
        assertTrue("3 Mail(s)" in moved.text, moved.text)
        var s = m.state.value
        assertTrue(s.mails.none { it.id in first.map { f -> f.id } })
        assertEquals(4997, s.total)
        assertEquals(57, s.next)
        assertEquals(3, s.folders.first { it.id == "proj|s" }.items)
        assertEquals(unreadBefore - first.count { it.unread }, s.folders.first { it.id == "inbox|s" }.unread)
        assertEquals(3, b.mails.count { it.folder.endsWith("Projekte") })
        // Rückgängig moves them back (with yet newer ids).
        val back = assertNotNull(moved.undo).invoke()
        assertTrue("3" in back, back)
        assertEquals(0, b.mails.count { it.folder.endsWith("Projekte") })
        assertEquals(5000, m.state.value.total)
        assertEquals(first.map { it.subject }, m.state.value.mails.take(3).map { it.subject })

        // Mark read: only those that were unread change; Rückgängig makes them unread again.
        val ids = m.state.value.mails.take(6).map { it.id }
        val wasUnread = m.state.value.mails.take(6).filter { it.unread }.map { it.id }
        val read = m.markRead(ids, true)
        assertTrue(m.state.value.mails.take(6).none { it.unread })
        assertEquals(unreadBefore - wasUnread.size, m.state.value.folders.first { it.id == "inbox|s" }.unread)
        assertNotNull(read.undo).invoke()
        assertEquals(wasUnread, m.state.value.mails.take(6).filter { it.unread }.map { it.id })
        assertEquals(wasUnread.size, b.mails.count { it.id in ids && it.unread })

        // Rename and back.
        val renamed = m.renameFolder(projekte, "Projekte 2026")
        assertTrue(m.state.value.folders.any { it.name == "Projekte 2026" })
        assertNotNull(renamed.undo).invoke()
        assertTrue(m.state.value.folders.any { it.name == "Projekte" } && m.state.value.folders.none { it.name == "Projekte 2026" })

        // A new folder path makes every level; nothing is deleted, so there is no Rückgängig.
        val made = m.createFolder(projekte, "Lehre/WS 2026-27")
        assertNull(made.undo)
        assertTrue(m.state.value.folders.any { it.path.endsWith("Projekte\\Lehre\\WS 2026-27") })
        assertTrue(m.state.value.folders.any { it.path.endsWith("Projekte\\Lehre") })
        // Every change is in the protocol.
        assertEquals(listOf("Verschoben", "Rückgängig", "Gelesen", "Rückgängig", "Ordner", "Rückgängig", "Ordner"), log.map { it.substringBefore(':') })
    }

    /** An old bridge without own paging: the default filters its mails in memory. */
    @Test
    fun olderBridgesPageWithTheDefault() {
        val b = BigOutlook()
        val plain = object : OutlookBridge by b {
            override fun page(query: MailQuery, offset: Int, limit: Int): MailPage = super.page(query, offset, limit)
            override fun moveTracked(ids: List<String>, folderId: String): Map<String, String> = super.moveTracked(ids, folderId)
        }
        val p = plain.page(MailQuery("inbox|s", withAttachments = true), 0, 20)
        assertEquals(20, p.mails.size)
        assertTrue(p.mails.all { it.attachments > 0 } && p.hasMore)
        val first = b.mails[0].id
        assertEquals(mapOf(first to first), plain.moveTracked(listOf(first), "proj|s"), "the default keeps the id")
        assertEquals(listOf("Lehre", "WS 2026-27"), MailFolders.levels(" Lehre / WS 2026-27\\"))
    }

    @Test
    fun outlookFiltersAreWrittenForRestrict() {
        val q = MailQuery(from = "O'Brien", withAttachments = true, text = "Antrag")
        val f = io.github.thepytales.llmhub.desktop.ComOutlook.daslFilter(q)!!
        assertTrue(f.startsWith("@SQL=\"urn:schemas:httpmail:hasattachment\" = 1 AND "), f)
        assertTrue("\"urn:schemas:httpmail:fromname\" LIKE '%O''Brien%'" in f, f)
        assertTrue("\"urn:schemas:httpmail:textdescription\" LIKE '%Antrag%'" in f, f)
        assertNull(io.github.thepytales.llmhub.desktop.ComOutlook.daslFilter(MailQuery(unreadOnly = true)))
    }

    // ── Mail über den Server (IMAP) ─────────────────────────────────────────────────────────────────────────

    @Test
    fun imapSetsStructuresAndSearchKeys() {
        assertEquals("1:3,7,9:10", ImapParser.uidSet(listOf(10L, 2L, 1L, 3L, 7L, 9L)))
        assertEquals(listOf(4L, 5L, 6L, 9L), ImapParser.uids("4:6,9"))
        assertEquals(listOf(6L, 5L, 4L), ImapParser.uids("6:4"))
        val plain = ImapParser.parse("(\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"7bit\" 25 1 NIL NIL NIL)")[0]
        assertEquals(0, ImapParser.attachmentCount(plain))
        val mixed = ImapParser.parse(
            "((\"text\" \"plain\" (\"charset\" \"utf-8\") NIL NIL \"7bit\" 25 1 NIL NIL NIL)" +
                "(\"application\" \"pdf\" (\"name\" \"Antrag.pdf\") NIL NIL \"base64\" 1234 NIL (\"attachment\" (\"filename\" \"Antrag.pdf\")) NIL)" +
                "(\"image\" \"png\" NIL \"<logo>\" NIL \"base64\" 99 NIL (\"inline\" NIL) NIL) \"mixed\" (\"boundary\" \"x\") NIL NIL)",
        )[0]
        assertEquals(1, ImapParser.attachmentCount(mixed))
        val (parts, literals) = ImapBridge.searchKeys(MailQuery(from = "weber", unreadOnly = true), zone)
        assertEquals(listOf("UNSEEN FROM \"weber\""), parts)
        assertTrue(literals.isEmpty())
        val since = LocalDate.of(2026, 11, 2).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = LocalDate.of(2026, 11, 6).atStartOfDay(zone).toInstant().toEpochMilli()
        val (p2, l2) = ImapBridge.searchKeys(MailQuery(subject = "Prüfung", text = "Liste", since = since, until = until), zone)
        assertEquals(listOf("SINCE 2-Nov-2026 BEFORE 6-Nov-2026 SUBJECT ", " TEXT \"Liste\""), p2)
        assertEquals("Prüfung", String(l2.single(), Charsets.UTF_8))
        assertEquals(listOf("ALL"), ImapBridge.searchKeys(MailQuery(), zone).first)
    }

    /**
     * A small IMAP server for the view: INBOX with 30 mails (arrival not in UID order), INBOX/Lehre, Projekte; SEARCH
     * (ALL, UNSEEN, FROM), FETCH, STORE, RENAME and MOVE with COPYUID.
     */
    private class ViewImap {
        val server = ServerSocket(0)
        val commands: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val boxes = linkedMapOf("INBOX" to (1L..30L).toMutableList(), "INBOX/Lehre" to mutableListOf(), "Projekte" to mutableListOf<Long>())
        val seen = (1L..30L).filter { it % 2 == 0L }.toMutableSet()
        var nextUid = 100L

        /** Arrival: UID 30 is the oldest, the others newer with the UID. */
        fun date(uid: Long) = if (uid == 30L) "01-Oct-2026 08:00:00 +0200" else String.format("%2d-Nov-2026 %02d:00:00 +0100", 1 + (uid / 24).toInt(), (uid % 24).toInt())

        init {
            thread(isDaemon = true) { while (!server.isClosed) runCatching { serve(server.accept()) } }
        }

        private fun serve(s: Socket) = s.use {
            val inp = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val out = s.getOutputStream()
            fun send(text: String) { out.write(text.toByteArray(Charsets.UTF_8)); out.flush() }
            val caps = "IMAP4rev1 MOVE UIDPLUS"
            send("* OK [CAPABILITY $caps] bereit\r\n")
            var selected = "INBOX"
            while (true) {
                val line = inp.readLine() ?: return
                val tag = line.substringBefore(' ')
                val cmd = line.substringAfter(' ')
                commands += cmd
                val upper = cmd.uppercase()
                fun quoted() = Regex("\"([^\"]+)\"").findAll(cmd).map { it.groupValues[1] }.toList()
                when {
                    upper.startsWith("CAPABILITY") -> send("* CAPABILITY $caps\r\n$tag OK\r\n")
                    upper.startsWith("LOGIN") -> send("$tag OK angemeldet\r\n")
                    upper.startsWith("LIST") -> send(boxes.keys.joinToString("") { "* LIST () \"/\" \"$it\"\r\n" } + "$tag OK\r\n")
                    upper.startsWith("STATUS") -> {
                        val box = quoted().first()
                        val ids = boxes[box].orEmpty()
                        send("* STATUS \"$box\" (MESSAGES ${ids.size} UNSEEN ${ids.count { it !in seen }})\r\n$tag OK\r\n")
                    }
                    upper.startsWith("EXAMINE") || upper.startsWith("SELECT") -> {
                        selected = quoted().first()
                        send("* ${boxes[selected]!!.size} EXISTS\r\n* OK [UIDVALIDITY 7] ok\r\n* OK [UIDNEXT $nextUid] ok\r\n$tag OK\r\n")
                    }
                    upper.startsWith("UID SEARCH") -> {
                        var ids = boxes[selected]!!.toList()
                        if ("UNSEEN" in upper) ids = ids.filter { it !in seen }
                        if ("FROM \"WEBER\"" in upper) ids = ids.filter { it % 3 == 0L }
                        send("* SEARCH ${ids.joinToString(" ")}\r\n$tag OK\r\n")
                    }
                    upper.startsWith("UID FETCH") -> {
                        val uids = ImapParser.uids(cmd.split(' ')[2]).filter { it in boxes[selected]!! }
                        for ((n, uid) in uids.withIndex()) {
                            if ("BODY.PEEK[HEADER" in upper) {
                                val head = "From: ${if (uid % 3 == 0L) "Weber <weber@tu-dresden.de>" else "Jemand <x@example.org>"}\r\nSubject: Mail $uid\r\n\r\n"
                                val b = head.toByteArray()
                                val flags = if (uid in seen) "\\Seen" else ""
                                out.write("* ${n + 1} FETCH (UID $uid FLAGS ($flags) INTERNALDATE \"${date(uid)}\" BODYSTRUCTURE (\"text\" \"plain\" NIL NIL NIL \"7bit\" 5 1 NIL NIL NIL) BODY[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] {${b.size}}\r\n".toByteArray())
                                out.write(b)
                                out.write(" BODY[TEXT]<0> \"Hallo\")\r\n".toByteArray())
                            } else out.write("* ${n + 1} FETCH (UID $uid INTERNALDATE \"${date(uid)}\")\r\n".toByteArray())
                        }
                        send("$tag OK\r\n")
                    }
                    upper.startsWith("UID STORE") -> {
                        val uids = ImapParser.uids(cmd.split(' ')[2])
                        if ("+FLAGS" in upper) seen += uids else seen -= uids.toSet()
                        send("$tag OK\r\n")
                    }
                    upper.startsWith("UID MOVE") -> {
                        val uids = ImapParser.uids(cmd.split(' ')[2])
                        val target = quoted().last()
                        val fresh = uids.map { nextUid++ }
                        boxes[selected]!!.removeAll(uids); boxes[target]!!.addAll(fresh)
                        uids.zip(fresh).filter { it.first in seen }.forEach { (_, f) -> seen += f }
                        send("* OK [COPYUID 7 ${ImapParser.uidSet(uids)} ${ImapParser.uidSet(fresh)}] kopiert\r\n$tag OK verschoben\r\n")
                    }
                    upper.startsWith("RENAME") -> {
                        val (old, new) = quoted()
                        boxes[new] = boxes.remove(old)!!
                        send("$tag OK\r\n")
                    }
                    upper.startsWith("SUBSCRIBE") || upper.startsWith("UNSUBSCRIBE") -> send("$tag OK\r\n")
                    upper.startsWith("LOGOUT") -> { send("* BYE\r\n$tag OK\r\n"); return }
                    else -> send("$tag BAD unbekannt\r\n")
                }
            }
        }
    }

    @Test
    fun imapPagesMarksRenamesAndMovesWithNewIds() {
        val fake = ViewImap()
        try {
            val b = ImapBridge(MailServerConfig("127.0.0.1", fake.server.localPort, "anna", "geheim", "anna@tu-dresden.de")) { c ->
                val s = Socket("127.0.0.1", c.port)
                ImapConnection(s.getInputStream(), s.getOutputStream(), s).apply { greeting() }
            }
            val folders = b.folders()
            assertEquals(listOf("Posteingang", "Posteingang\\Lehre", "Projekte"), folders.map { it.path })
            assertEquals(15, folders.first().unread)
            assertEquals(listOf(0, 1, 0), MailFolders.tree(folders).map { it.depth })
            // Pages newest first by arrival (UID 30 is the oldest), then the next page.
            val p1 = b.page(MailQuery("INBOX"), 0, 10)
            assertEquals(30, p1.total)
            assertEquals((29L downTo 20L).map { "$it@INBOX" }, p1.mails.map { it.id })
            val p3 = b.page(MailQuery("INBOX"), 20, 10)
            assertEquals("30@INBOX", p3.mails.last().id)
            assertFalse(p3.hasMore)
            assertEquals("Weber", p1.mails.first { it.id == "27@INBOX" }.from)
            val unread = b.page(MailQuery("INBOX", unreadOnly = true, from = "weber"), 0, 50)
            assertEquals((1L..30L).count { it % 2 == 1L && it % 3 == 0L }, unread.mails.size)
            // Marks and moves.
            assertEquals(2, b.markRead(listOf("1@INBOX", "3@INBOX"), true))
            assertTrue(1L in fake.seen && 3L in fake.seen)
            val moved = b.moveTracked(listOf("5@INBOX", "6@INBOX"), "Projekte")
            assertEquals(mapOf("5@INBOX" to "100@Projekte", "6@INBOX" to "101@Projekte"), moved)
            assertEquals(2, b.moveTracked(moved.values.toList(), "INBOX").size)
            assertTrue(fake.boxes["Projekte"]!!.isEmpty() && fake.boxes["INBOX"]!!.size == 30)
            // Rename keeps the parent; the Posteingang itself is not renamed.
            val lehre = b.renameFolder("INBOX/Lehre", "Lehre 2026")
            assertEquals("INBOX/Lehre 2026", lehre.id)
            assertEquals("Posteingang\\Lehre 2026", lehre.path)
            assertTrue(runCatching { b.renameFolder("INBOX", "Alt") }.isFailure)
            assertTrue(b.canMove())
            val commands = synchronized(fake.commands) { fake.commands.toList() }
            assertTrue(commands.none { it.uppercase().startsWith("DELETE") || it.uppercase().contains("EXPUNGE") }, commands.toString())
        } finally {
            fake.server.close()
        }
    }
}
