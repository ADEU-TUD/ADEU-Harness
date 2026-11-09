package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.ImapBridge
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailServerConfig
import io.github.thepytales.llmhub.data.OutlookException
import io.github.thepytales.llmhub.net.ImapConnection
import io.github.thepytales.llmhub.net.ImapParser
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mail über den Server (AP-27): IMAP gegen einen kleinen Testserver. */
class MailServerTest {
    @Test
    fun folderNamesAndResponses() {
        assertEquals("Entwürfe", ImapParser.decodeFolder("Entw&APw-rfe"))
        assertEquals("A&B", ImapParser.decodeFolder("A&-B"))
        assertEquals("Entw&APw-rfe", ImapParser.encodeFolder("Entwürfe"))
        assertEquals("Prüfungen/Ölmühle", ImapParser.decodeFolder(ImapParser.encodeFolder("Prüfungen/Ölmühle")))
        val items = ImapParser.parse("* LIST (\\HasNoChildren \\Drafts) \"/\" \"Entw&APw-rfe\"")
        assertEquals(listOf("\\HasNoChildren", "\\Drafts"), items[2])
        assertEquals("/", items[3])
        val fetch = ImapParser.fetch(ImapConnection.Line("* 3 FETCH (UID 17 FLAGS (\\Seen) BODY[HEADER.FIELDS (FROM SUBJECT)] \u00000)", listOf("Subject: x".toByteArray())))!!
        assertEquals("17", fetch["UID"])
        assertEquals("Subject: x", ImapParser.text(fetch["BODY[HEADER.FIELDS (FROM SUBJECT)]"]))
        assertEquals("\"a\\\"b\"", ImapParser.quote("a\"b"))
        assertEquals("Müller, Anna" to "anna@tu-dresden.de", ImapBridge.person("\"=?UTF-8?Q?M=C3=BCller=2C_Anna?=\" <anna@tu-dresden.de>"))
    }

    /** A tiny IMAP server: INBOX with two mails, Entwürfe, Projekte; records every command. */
    private class FakeImap(val capabilities: String = "IMAP4rev1 MOVE UIDPLUS") {
        val server = ServerSocket(0)
        val commands: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val appended: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val boxes = mutableMapOf(
            "INBOX" to mutableListOf(1L, 2L),
            "Entw&APw-rfe" to mutableListOf(),
            "Projekte" to mutableListOf<Long>(),
        )
        private val mails = mapOf(
            1L to ("From: \"Prof. Weber\" <weber@tu-dresden.de>\r\nTo: anna@tu-dresden.de\r\nSubject: =?UTF-8?Q?Pr=C3=BCfung_am_Montag?=\r\nDate: Sun, 01 Nov 2026 09:00:00 +0100\r\nMessage-ID: <m1@tu>\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nBitte die Liste bis Freitag schicken.\r\n"),
            2L to ("From: newsletter@example.org\r\nSubject: News\r\nDate: Sat, 31 Oct 2026 09:00:00 +0100\r\nContent-Type: text/plain\r\n\r\nNeuigkeiten.\r\n"),
        )
        private val seen = mutableSetOf(2L)

        init {
            thread(isDaemon = true) { while (!server.isClosed) runCatching { serve(server.accept()) } }
        }

        private fun serve(s: Socket) = s.use {
            val inp = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val out = s.getOutputStream()
            fun send(text: String) { out.write(text.toByteArray(Charsets.UTF_8)); out.flush() }
            fun literal(prefix: String, data: String, suffix: String) {
                val b = data.toByteArray(Charsets.UTF_8)
                out.write("$prefix{${b.size}}\r\n".toByteArray()); out.write(b); out.write("$suffix\r\n".toByteArray()); out.flush()
            }
            send("* OK [CAPABILITY $capabilities] Test bereit\r\n")
            var selected = "INBOX"
            while (true) {
                val line = inp.readLine() ?: return
                val tag = line.substringBefore(' ')
                val cmd = line.substringAfter(' ')
                commands += cmd
                val upper = cmd.uppercase()
                when {
                    upper.startsWith("CAPABILITY") -> send("* CAPABILITY $capabilities\r\n$tag OK fertig\r\n")
                    upper.startsWith("LOGIN") -> send(if ("\"geheim\"" in cmd) "$tag OK [CAPABILITY $capabilities] angemeldet\r\n" else "$tag NO falsch\r\n")
                    upper.startsWith("LIST") -> {
                        send("* LIST (\\HasNoChildren) \"/\" \"INBOX\"\r\n* LIST (\\HasNoChildren \\Drafts) \"/\" \"Entw&APw-rfe\"\r\n* LIST (\\HasNoChildren) \"/\" Projekte\r\n$tag OK fertig\r\n")
                    }
                    upper.startsWith("STATUS") -> {
                        val box = Regex("STATUS \"([^\"]+)\"").find(cmd)!!.groupValues[1]
                        val ids = boxes[box].orEmpty()
                        send("* STATUS \"$box\" (MESSAGES ${ids.size} UNSEEN ${ids.count { it !in seen }})\r\n$tag OK fertig\r\n")
                    }
                    upper.startsWith("EXAMINE") || upper.startsWith("SELECT") -> {
                        selected = Regex("\"([^\"]+)\"").find(cmd)!!.groupValues[1]
                        send("* ${boxes[selected]!!.size} EXISTS\r\n$tag OK [READ-WRITE] fertig\r\n")
                    }
                    upper.startsWith("UID SEARCH") -> send("* SEARCH ${boxes[selected]!!.joinToString(" ")}\r\n$tag OK fertig\r\n")
                    upper.startsWith("UID FETCH") -> {
                        val uids = cmd.split(' ')[2].split(',').mapNotNull { it.toLongOrNull() }.filter { it in boxes[selected]!! }
                        for ((n, uid) in uids.withIndex()) {
                            val raw = mails[uid] ?: continue
                            val flags = if (uid in seen) "\\Seen" else ""
                            val head = raw.substringBefore("\r\n\r\n") + "\r\n\r\n"
                            if ("BODY.PEEK[]" in cmd) literal("* ${n + 1} FETCH (UID $uid FLAGS ($flags) INTERNALDATE \"01-Nov-2026 09:00:00 +0100\" BODY[] ", raw, ")")
                            else literal("* ${n + 1} FETCH (UID $uid FLAGS ($flags) INTERNALDATE \"01-Nov-2026 09:00:00 +0100\" BODY[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] ", head, ")")
                        }
                        send("$tag OK fertig\r\n")
                    }
                    upper.startsWith("UID MOVE") -> {
                        val uids = cmd.split(' ')[2].split(',').map { it.toLong() }
                        val target = Regex("\"([^\"]+)\"$").find(cmd)!!.groupValues[1]
                        boxes[selected]!!.removeAll(uids); boxes[target]!!.addAll(uids)
                        send("$tag OK verschoben\r\n")
                    }
                    upper.startsWith("APPEND") -> {
                        val n = Regex("\\{(\\d+)}$").find(cmd)!!.groupValues[1].toInt()
                        send("+ bereit\r\n")
                        val buf = CharArray(n)
                        var read = 0
                        // The literal is ASCII (base64 body), so characters = bytes.
                        while (read < n) read += inp.read(buf, read, n - read)
                        inp.readLine()
                        appended += String(buf)
                        send("$tag OK [APPENDUID 1 99] gespeichert\r\n")
                    }
                    upper.startsWith("CREATE") -> { boxes[Regex("\"([^\"]+)\"").find(cmd)!!.groupValues[1]] = mutableListOf(); send("$tag OK angelegt\r\n") }
                    upper.startsWith("SUBSCRIBE") -> send("$tag OK\r\n")
                    upper.startsWith("LOGOUT") -> { send("* BYE\r\n$tag OK\r\n"); return }
                    else -> send("$tag BAD unbekannt\r\n")
                }
            }
        }
    }

    private fun bridge(fake: FakeImap, password: String = "geheim") = ImapBridge(MailServerConfig("127.0.0.1", fake.server.localPort, "anna", password, "anna@tu-dresden.de")) { c ->
        val s = Socket("127.0.0.1", c.port)
        ImapConnection(s.getInputStream(), s.getOutputStream(), s).apply { greeting() }
    }

    @Test
    fun readsSortsAndSavesDraftsButNeverDeletesOrSends() {
        val fake = FakeImap()
        try {
            val b = bridge(fake)
            val folders = b.folders()
            assertEquals(listOf("Posteingang", "Entwürfe", "Projekte"), folders.map { it.name })
            assertEquals(1, folders.first().unread)
            val mails = b.mails(null, since = null, unreadOnly = false, limit = 50)
            assertEquals(2, mails.size)
            val m = mails.first { it.fromAddress == "weber@tu-dresden.de" }
            assertEquals("Prüfung am Montag", m.subject)
            assertEquals("Prof. Weber", m.from)
            assertTrue(m.unread)
            assertFalse(mails.first { it.subject == "News" }.unread)
            val body = b.mail(m.id)!!
            assertTrue("Bitte die Liste bis Freitag schicken." in body.body, body.body)
            // Moving uses MOVE; nothing is deleted or sent.
            assertEquals(1, b.move(listOf(mails.first { it.subject == "News" }.id), "Projekte"))
            assertEquals(listOf(2L), fake.boxes["Projekte"]!!.toList())
            val id = b.draft(MailDraft(replyTo = m.id, body = "Gern, kommt bis Freitag."))
            assertEquals("99@Entw&APw-rfe", id)
            val draft = fake.appended.single()
            assertTrue("To: \"Prof. Weber\" <weber@tu-dresden.de>" in draft || "To: =?UTF-8?B?" in draft, draft)
            assertTrue("In-Reply-To: <m1@tu>" in draft && "Subject: =?UTF-8?B?" in draft)
            // A copy under the list's lock: the server thread may still be writing (LOGOUT) – iterating it directly raced.
            val commands = synchronized(fake.commands) { fake.commands.toList() }
            assertTrue(commands.none { c -> listOf("EXPUNGE", "DELETE", "STORE").any { c.uppercase().startsWith("UID $it") || c.uppercase().startsWith(it) } }, commands.toString())
            assertEquals("Neu", b.createFolder(null, "Neu").name)
            assertFailsWith<OutlookException> { b.events(0, 1) }
        } finally {
            fake.server.close()
        }
    }

    @Test
    fun wrongPasswordAndServersWithoutMoveAreRefused() {
        val fake = FakeImap(capabilities = "IMAP4rev1")
        try {
            val e = assertFailsWith<OutlookException> { bridge(fake, "falsch").folders() }
            assertTrue("Anmeldung" in e.message!!)
            val b = bridge(fake)
            val id = b.mails(null, null, false, 10).first().id
            val refused = assertFailsWith<OutlookException> { b.move(listOf(id), "Projekte") }
            assertTrue("nicht verschieben" in refused.message!!)
            assertTrue(synchronized(fake.commands) { fake.commands.toList() }.none { it.uppercase().contains("EXPUNGE") })
        } finally {
            fake.server.close()
        }
    }

    @Test
    fun draftsAreMime() {
        val m = String(ImapBridge.mime("anna@tu-dresden.de", "weber@tu-dresden.de", "", "Prüfung", "Grüße\nAnna"))
        assertTrue("Subject: =?UTF-8?B?" in m && "Content-Transfer-Encoding: base64" in m && "X-Mailer: Lora (Entwurf, nicht gesendet)" in m)
        val body = m.substringAfter("\r\n\r\n").replace("\r\n", "")
        assertEquals("Grüße\r\nAnna", String(java.util.Base64.getDecoder().decode(body)))
        assertEquals("Bitte bis Freitag.", ImapBridge.preview("text/plain; charset=UTF-8", "Bitte  bis\r\nFreitag."))
    }
}
