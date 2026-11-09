package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.files.MailText
import io.github.thepytales.llmhub.net.ImapConnection
import io.github.thepytales.llmhub.net.ImapParser
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import io.github.thepytales.llmhub.ui.tr

/** How to reach the mail server (Einstellungen › Verbindungen › Mail über den Server). */
data class MailServerConfig(val host: String, val port: Int, val user: String, val password: String, val address: String) {
    val complete: Boolean get() = host.isNotBlank() && user.isNotBlank() && password.isNotBlank()
}

/**
 * Mail über den Server (AP-27): the same functions as with the classic Outlook (AP-13), over IMAP – for the new
 * Outlook, other mail programs and Android. Reads folders and mails, moves mails (only with the server's MOVE or UIDPLUS,
 * so no other mail is touched), creates folders and saves answers as drafts in „Entwürfe“. Never deletes, never sends.
 * Calendar and tasks need Exchange (EWS/Graph, with the IT's approval) and are not part of this path yet.
 * [open] makes the connection (tests replace it).
 */
class ImapBridge(
    private val config: MailServerConfig,
    private val open: (MailServerConfig) -> ImapConnection = { c -> ImapConnection.open(c.host, c.port) },
) : OutlookBridge {
    private fun <T> session(block: (ImapConnection) -> T): T {
        if (!config.complete) throw OutlookException(tr("Mail über den Server ist noch nicht eingerichtet (Einstellungen › Verbindungen)."))
        val c = try {
            open(config)
        } catch (e: IOException) {
            throw OutlookException(tr("Der Mailserver {0} ist nicht erreichbar: {1}", config.host, e.message))
        }
        return c.use { conn ->
            try {
                conn.login(config.user, config.password)
                block(conn)
            } catch (e: IOException) {
                throw OutlookException(e.message ?: tr("Fehler beim Mailserver"))
            }
        }
    }

    override fun connect(): String = session { "${config.user} @ ${config.host}" }

    private data class Box(val raw: String, val delimiter: String?, val flags: Set<String>)

    private fun boxes(c: ImapConnection): List<Box> = c.ok("LIST \"\" \"*\"").untagged.mapNotNull { l ->
        val items = l.items()
        if (items.size < 5 || items[0] != "*" || (items[1] as? String)?.uppercase() != "LIST") return@mapNotNull null
        val flags = (items[2] as? List<*>)?.mapNotNull { (it as? String)?.uppercase() }?.toSet().orEmpty()
        Box(ImapParser.text(items[4]) ?: return@mapNotNull null, ImapParser.text(items[3]), flags)
    }

    private fun display(b: Box): String {
        val parts = (if (b.delimiter.isNullOrEmpty()) listOf(b.raw) else b.raw.split(b.delimiter)).map { ImapParser.decodeFolder(it) }
        return parts.joinToString("\\") { if (it.equals("INBOX", ignoreCase = true)) "Posteingang" else it }
    }

    override fun folders(): List<MailFolder> = session { c ->
        boxes(c).filter { "\\NOSELECT" !in it.flags && "\\NONEXISTENT" !in it.flags }.take(200).mapIndexed { i, b ->
            // Counts only for the first folders: STATUS is one request each.
            val (items, unread) = if (i < 60) status(c, b.raw) else 0 to 0
            val path = display(b)
            MailFolder(id = b.raw, path = path, name = path.substringAfterLast('\\'), items = items, unread = unread)
        }
    }

    private fun status(c: ImapConnection, raw: String): Pair<Int, Int> = runCatching {
        val r = c.command("STATUS ${ImapParser.quote(raw)} (MESSAGES UNSEEN)")
        val items = r.untagged.firstOrNull()?.items().orEmpty()
        val l = items.lastOrNull() as? List<*> ?: return@runCatching 0 to 0
        fun v(name: String) = l.indexOfFirst { (it as? String)?.uppercase() == name }.takeIf { it >= 0 }?.let { (l[it + 1] as? String)?.toIntOrNull() } ?: 0
        v("MESSAGES") to v("UNSEEN")
    }.getOrDefault(0 to 0)

    private fun id(uid: Long, folder: String) = "$uid@$folder"
    private fun parseId(id: String): Pair<Long, String> {
        val uid = id.substringBefore('@').toLongOrNull() ?: throw OutlookException(tr("Unbekannte Mail-ID {0}", id))
        return uid to id.substringAfter('@')
    }

    override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int): List<MailHeader> = session { c ->
        val folder = folderId ?: "INBOX"
        c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
        val criteria = listOfNotNull(since?.let { "SINCE " + SEARCH_DATE.get()!!.format(Date(it)) }, if (unreadOnly) "UNSEEN" else null).ifEmpty { listOf("ALL") }
        val uids = c.ok("UID SEARCH " + criteria.joinToString(" ")).untagged
            .filter { it.text.startsWith("* SEARCH", ignoreCase = true) }
            .flatMap { it.text.substring(8).trim().split(' ').mapNotNull(String::toLongOrNull) }
            .sortedDescending().take(limit.coerceIn(1, 500))
        if (uids.isEmpty()) return@session emptyList()
        val r = c.ok("UID FETCH ${uids.joinToString(",")} (UID FLAGS INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] BODY.PEEK[TEXT]<0.2000>)")
        r.untagged.mapNotNull { ImapParser.fetch(it) }.mapNotNull { f -> header(f, folder) }.sortedByDescending { it.received }
    }

    private fun header(f: Map<String, Any?>, folder: String): MailHeader? {
        val uid = (f["UID"] as? String)?.toLongOrNull() ?: return null
        val flags = (f["FLAGS"] as? List<*>)?.mapNotNull { (it as? String)?.uppercase() }.orEmpty()
        val head = f.entries.firstOrNull { it.key.startsWith("BODY[HEADER") }?.value?.let { ImapParser.text(it) }.orEmpty()
        val h = headers(head)
        val text = f.entries.firstOrNull { it.key.startsWith("BODY[TEXT]") }?.value?.let { ImapParser.text(it) }.orEmpty()
        val (name, address) = person(h["from"].orEmpty())
        return MailHeader(
            id = id(uid, folder), folder = folder, from = name.ifBlank { address }, fromAddress = address,
            to = MailText.decodeWords(h["to"].orEmpty()), subject = MailText.decodeWords(h["subject"].orEmpty()),
            received = (f["INTERNALDATE"] as? String)?.let { d -> runCatching { INTERNAL_DATE.get()!!.parse(d)?.time }.getOrNull() } ?: 0,
            unread = "\\SEEN" !in flags, flagged = "\\FLAGGED" in flags,
            preview = preview(h["content-type"].orEmpty(), text),
        )
    }

    override fun mail(id: String): MailBody? = session { c ->
        val (uid, folder) = parseId(id)
        c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
        val r = c.ok("UID FETCH $uid (UID FLAGS INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] BODY.PEEK[])")
        val f = r.untagged.mapNotNull { ImapParser.fetch(it) }.firstOrNull() ?: return@session null
        val header = header(f, folder) ?: return@session null
        val raw = f.entries.firstOrNull { it.key == "BODY[]" }?.value as? ByteArray ?: return@session null
        val text = MailText.eml(raw)
        val h = headers(String(raw, Charsets.ISO_8859_1).substringBefore("\r\n\r\n"))
        val attachments = text.lines().firstOrNull { it.startsWith("Anhänge: ") }?.removePrefix("Anhänge: ")?.split(", ").orEmpty()
        MailBody(header, cc = MailText.decodeWords(h["cc"].orEmpty()), body = text.substringAfter("\n\n", text), attachmentNames = attachments)
    }

    override fun move(ids: List<String>, folderId: String): Int = session { c ->
        val target = ImapParser.quote(folderId)
        var moved = 0
        for ((folder, list) in ids.map { parseId(it) }.groupBy({ it.second }, { it.first })) {
            if (folder == folderId) continue
            c.ok("SELECT ${ImapParser.quote(folder)}", what = "Ordner öffnen")
            val set = list.joinToString(",")
            when {
                c.has("MOVE") -> c.ok("UID MOVE $set $target", what = "Verschieben")
                // Copy, then mark and remove exactly these mails (UID EXPUNGE touches no other mail).
                c.has("UIDPLUS") -> {
                    c.ok("UID COPY $set $target", what = "Verschieben")
                    c.ok("UID STORE $set +FLAGS.SILENT (\\Deleted)", what = "Verschieben")
                    c.ok("UID EXPUNGE $set", what = "Verschieben")
                }
                else -> throw OutlookException(tr("Dieser Mailserver kann nicht verschieben, ohne dabei andere gelöschte Mails endgültig zu entfernen – deshalb lässt die App das."))
            }
            moved += list.size
        }
        moved
    }

    override fun createFolder(parentId: String?, name: String): MailFolder = session { c ->
        val delimiter = boxes(c).firstOrNull { it.delimiter != null }?.delimiter ?: "/"
        val raw = (parentId?.let { it + delimiter } ?: "") + ImapParser.encodeFolder(name.trim())
        c.ok("CREATE ${ImapParser.quote(raw)}", what = "Ordner anlegen")
        runCatching { c.command("SUBSCRIBE ${ImapParser.quote(raw)}") }
        MailFolder(id = raw, path = display(Box(raw, delimiter, emptySet())), name = name.trim())
    }

    override fun draft(draft: MailDraft): String = session { c ->
        var to = draft.to
        var cc = draft.cc
        var subject = draft.subject
        var body = draft.body
        val extra = mutableListOf<String>()
        draft.replyTo?.let { rid ->
            val (uid, folder) = parseId(rid)
            c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
            val f = c.ok("UID FETCH $uid (UID BODY.PEEK[])").untagged.mapNotNull { ImapParser.fetch(it) }.firstOrNull()
            val raw = f?.get("BODY[]") as? ByteArray
            if (raw != null) {
                val h = headers(String(raw, Charsets.ISO_8859_1).substringBefore("\r\n\r\n"))
                if (to.isBlank()) to = MailText.decodeWords(h["reply-to"] ?: h["from"].orEmpty())
                if (draft.replyAll && cc.isBlank()) cc = listOf(h["to"], h["cc"]).filterNotNull().joinToString(", ") { MailText.decodeWords(it) }
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.contains(config.address.ifBlank { "\u0000" }, ignoreCase = true) }.joinToString(", ")
                val original = MailText.decodeWords(h["subject"].orEmpty())
                if (subject.isBlank()) subject = if (original.startsWith("AW:", ignoreCase = true) || original.startsWith("Re:", ignoreCase = true)) original else "AW: $original"
                h["message-id"]?.let { mid -> extra += "In-Reply-To: $mid"; extra += "References: " + listOfNotNull(h["references"], mid).joinToString(" ") }
                body += "\n\n-----Ursprüngliche Nachricht-----\n" + MailText.eml(raw).lines().joinToString("\n") { "> $it" }
            }
        }
        val drafts = boxes(c).let { all ->
            all.firstOrNull { "\\DRAFTS" in it.flags }
                ?: all.firstOrNull { b -> ImapParser.decodeFolder(b.raw).substringAfterLast(b.delimiter ?: "/").lowercase() in setOf("entwürfe", "drafts", "entwurf") }
        }?.raw ?: throw OutlookException(tr("Der Ordner „Entwürfe“ wurde auf dem Mailserver nicht gefunden."))
        val message = mime(config.address.ifBlank { config.user }, to, cc, subject, body, extra)
        val r = c.ok("APPEND ${ImapParser.quote(drafts)} (\\Draft \\Seen)", literal = message, what = "Entwurf speichern")
        val uid = Regex("APPENDUID \\d+ (\\d+)", RegexOption.IGNORE_CASE).find(r.text)?.groupValues?.get(1)?.toLongOrNull()
        if (uid != null) id(uid, drafts) else "entwurf@$drafts"
    }

    private val noCalendar = tr("Kalender, Termine und Aufgaben gibt es über den Mailserver (IMAP) nicht – dafür braucht es Exchange (EWS oder Microsoft Graph mit Freigabe der IT) oder das klassische Outlook unter Windows. Termine lassen sich als Kalenderdatei (.ics) anlegen.")

    override fun events(from: Long, to: Long): List<CalendarEntry> = throw OutlookException(noCalendar)
    override fun createEvent(event: NewEvent): String = throw OutlookException(noCalendar)
    override fun createTask(task: NewTask): String = throw OutlookException(noCalendar)
    override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int): List<BusyTimes> = throw OutlookException(noCalendar)
    override fun show(id: String) = throw OutlookException(tr("Über den Mailserver gibt es kein Outlook zum Öffnen – die Mail bzw. der Entwurf liegt im Postfach (z. B. in Outlook im Web)."))

    // ── AP-69 b/c: Lora sortiert selbst ──

    /** SEARCH SINCE/BEFORE (whole days on the server), then exactly [from]–[until] by the arrival time. */
    override fun mailsInRange(folderId: String?, from: Long, until: Long?, limit: Int): List<MailHeader> = session { c ->
        val folder = folderId ?: "INBOX"
        c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
        val criteria = "SINCE " + SEARCH_DATE.get()!!.format(Date(from)) + (until?.let { " BEFORE " + SEARCH_DATE.get()!!.format(Date(it + 86_400_000L)) } ?: "")
        val uids = c.ok("UID SEARCH $criteria").untagged
            .filter { it.text.startsWith("* SEARCH", ignoreCase = true) }
            .flatMap { it.text.substring(8).trim().split(' ').mapNotNull(String::toLongOrNull) }
            .sortedDescending().take(limit.coerceIn(1, 2000))
        if (uids.isEmpty()) return@session emptyList()
        uids.chunked(200).flatMap { part ->
            c.ok("UID FETCH ${part.joinToString(",")} (UID FLAGS INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] BODY.PEEK[TEXT]<0.2000>)")
                .untagged.mapNotNull { ImapParser.fetch(it) }.mapNotNull { f -> header(f, folder) }
        }.filter { it.received == 0L || (it.received >= from && (until == null || it.received < until)) }.sortedByDescending { it.received }
    }

    /**
     * New UIDs from the server's COPYUID answer (UIDPLUS); a server without it is asked by Message-ID in the target
     * folder afterwards.
     */
    override fun moveTracked(ids: List<String>, folderId: String): Map<String, String> = session { c ->
        val target = ImapParser.quote(folderId)
        val out = LinkedHashMap<String, String>()
        for ((folder, list) in ids.map { parseId(it) }.groupBy({ it.second }, { it.first })) {
            if (folder == folderId) continue
            c.ok("SELECT ${ImapParser.quote(folder)}", what = "Ordner öffnen")
            val set = list.joinToString(",")
            val messageIds = runCatching {
                c.ok("UID FETCH $set (UID BODY.PEEK[HEADER.FIELDS (MESSAGE-ID)])").untagged.mapNotNull { ImapParser.fetch(it) }.mapNotNull { f ->
                    val uid = (f["UID"] as? String)?.toLongOrNull() ?: return@mapNotNull null
                    val head = f.entries.firstOrNull { it.key.startsWith("BODY[HEADER") }?.value?.let { ImapParser.text(it) }.orEmpty()
                    headers(head)["message-id"]?.takeIf { it.isNotBlank() }?.let { uid to it }
                }.toMap()
            }.getOrDefault(emptyMap())
            val answers = when {
                c.has("MOVE") -> c.ok("UID MOVE $set $target", what = "Verschieben").let { r -> listOf(r.text) + r.untagged.map { it.text } }
                c.has("UIDPLUS") -> {
                    val r = c.ok("UID COPY $set $target", what = "Verschieben")
                    c.ok("UID STORE $set +FLAGS.SILENT (\\Deleted)", what = "Verschieben")
                    c.ok("UID EXPUNGE $set", what = "Verschieben")
                    listOf(r.text) + r.untagged.map { it.text }
                }
                else -> throw OutlookException(tr("Dieser Mailserver kann nicht verschieben, ohne dabei andere gelöschte Mails endgültig zu entfernen – deshalb lässt die App das."))
            }
            val newUids = answers.fold(emptyMap<Long, Long>()) { acc, t -> acc + copyUid(t) }
            val missing = list.filter { it !in newUids && it in messageIds }
            val found = if (missing.isEmpty()) emptyMap() else runCatching {
                c.ok("EXAMINE $target", what = "Ordner öffnen")
                missing.mapNotNull { uid ->
                    val mid = messageIds[uid]!!.replace("\"", "")
                    c.ok("UID SEARCH HEADER Message-ID ${ImapParser.quote(mid)}").untagged
                        .filter { it.text.startsWith("* SEARCH", ignoreCase = true) }
                        .flatMap { it.text.substring(8).trim().split(' ').mapNotNull(String::toLongOrNull) }
                        .maxOrNull()?.let { uid to it }
                }.toMap()
            }.getOrDefault(emptyMap())
            list.forEach { uid -> out[id(uid, folder)] = (newUids[uid] ?: found[uid])?.let { id(it, folderId) }.orEmpty() }
        }
        out
    }

    companion object {
        private val SEARCH_DATE = ThreadLocal.withInitial { SimpleDateFormat("dd-MMM-yyyy", Locale.US) }
        private val INTERNAL_DATE = ThreadLocal.withInitial { SimpleDateFormat("dd-MMM-yyyy HH:mm:ss Z", Locale.US) }

        /** „[COPYUID 38505 304,319:320 3956:3958]“ (UIDPLUS) → old UID → new UID; empty without one. */
        fun copyUid(text: String): Map<Long, Long> {
            val m = Regex("COPYUID\\s+\\d+\\s+([0-9:,]+)\\s+([0-9:,]+)", RegexOption.IGNORE_CASE).find(text) ?: return emptyMap()
            val from = uidSet(m.groupValues[1])
            val to = uidSet(m.groupValues[2])
            return if (from.size == to.size) from.zip(to).toMap() else emptyMap()
        }

        /** „304,319:320“ → 304, 319, 320 (a range may also be written backwards). */
        fun uidSet(set: String): List<Long> = set.split(',').flatMap { part ->
            val ends = part.split(':').mapNotNull { it.trim().toLongOrNull() }
            when (ends.size) {
                1 -> ends
                2 -> if (ends[0] <= ends[1]) (ends[0]..ends[1]).toList() else (ends[0] downTo ends[1]).toList()
                else -> emptyList()
            }
        }

        /** Header fields (lower-case names, folded lines joined). */
        fun headers(text: String): Map<String, String> {
            val out = linkedMapOf<String, String>()
            var last: String? = null
            for (line in text.replace("\r\n", "\n").split('\n')) {
                if (line.isEmpty()) continue
                if ((line[0] == ' ' || line[0] == '\t') && last != null) out[last] = out[last] + " " + line.trim()
                else if (':' in line) {
                    last = line.substringBefore(':').trim().lowercase()
                    out[last] = line.substringAfter(':').trim()
                }
            }
            return out
        }

        /** „"Müller, Anna" <anna@tu-dresden.de>“ → name and address. */
        fun person(raw: String): Pair<String, String> {
            val decoded = MailText.decodeWords(raw)
            val address = Regex("<([^>]+)>").find(decoded)?.groupValues?.get(1) ?: decoded.trim().takeIf { '@' in it }.orEmpty()
            val name = decoded.substringBefore('<').trim().trim('"').trim()
            return name to address
        }

        /** A short preview from the start of the body, if it is plain or quoted-printable text. */
        fun preview(contentType: String, text: String): String {
            val plain = when {
                contentType.isBlank() || contentType.lowercase().startsWith("text/plain") -> text
                contentType.lowercase().startsWith("multipart/") -> {
                    // The first text/plain part of the beginning.
                    val part = Regex("(?is)content-type:\\s*text/plain.*?\\r?\\n\\r?\\n(.*?)(\\r?\\n--|$)").find(text)?.groupValues?.get(1)
                    if (part != null && !Regex("(?i)content-transfer-encoding:\\s*base64").containsMatchIn(text.substringBefore(part))) part else ""
                }
                else -> ""
            }
            val decoded = if (Regex("=[0-9A-F]{2}").containsMatchIn(plain)) runCatching { String(MailText.quotedPrintable(plain, false), Charsets.UTF_8) }.getOrDefault(plain) else plain
            return decoded.replace(Regex("\\s+"), " ").trim().take(240)
        }

        /** A draft as MIME message (UTF-8, base64), with [extra] header lines. */
        fun mime(from: String, to: String, cc: String, subject: String, body: String, extra: List<String> = emptyList()): ByteArray {
            fun word(s: String) = if (s.all { it.code in 0x20..0x7e }) s else "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8)) + "?="
            val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("Europe/Berlin") }.format(Date())
            val lines = listOfNotNull(
                "From: $from",
                "To: ${word(to)}".takeIf { to.isNotBlank() },
                "Cc: ${word(cc)}".takeIf { cc.isNotBlank() },
                "Subject: ${word(subject)}",
                "Date: $date",
                "Message-ID: <${UUID.randomUUID()}@lora.tu-dresden>",
            ) + extra + listOf(
                "MIME-Version: 1.0",
                "Content-Type: text/plain; charset=UTF-8",
                "Content-Transfer-Encoding: base64",
                "X-Mailer: Lora (Entwurf, nicht gesendet)",
            )
            val encoded = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(body.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
            return (lines.joinToString("\r\n") + "\r\n\r\n" + encoded + "\r\n").toByteArray(Charsets.UTF_8)
        }
    }
}
