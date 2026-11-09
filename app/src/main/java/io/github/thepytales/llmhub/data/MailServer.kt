package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.files.MailText
import io.github.thepytales.llmhub.net.ImapConnection
import io.github.thepytales.llmhub.net.ImapParser
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
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
                caps = conn.capabilities
                block(conn)
            } catch (e: IOException) {
                throw OutlookException(e.message ?: tr("Fehler beim Mailserver"))
            }
        }
    }

    override fun connect(): String = session { "${config.user} @ ${config.host}" }

    private data class Box(val raw: String, val delimiter: String?, val flags: Set<String>)

    /** Capabilities of the server at the last login (null before the first): whether it can move (MOVE or UIDPLUS). */
    @Volatile
    private var caps: Set<String>? = null

    private fun box(l: ImapConnection.Line): Box? {
        val items = l.items()
        if (items.size < 5 || items[0] != "*" || (items[1] as? String)?.uppercase() != "LIST") return null
        val flags = (items[2] as? List<*>)?.mapNotNull { (it as? String)?.uppercase() }?.toSet().orEmpty()
        return Box(ImapParser.text(items[4]) ?: return null, ImapParser.text(items[3]), flags)
    }

    private fun boxes(c: ImapConnection): List<Box> = c.ok("LIST \"\" \"*\"").untagged.mapNotNull(::box)

    private fun display(b: Box): String {
        val parts = (if (b.delimiter.isNullOrEmpty()) listOf(b.raw) else b.raw.split(b.delimiter)).map { ImapParser.decodeFolder(it) }
        return parts.joinToString("\\") { if (it.equals("INBOX", ignoreCase = true)) "Posteingang" else it }
    }

    private fun selectable(b: Box) = "\\NOSELECT" !in b.flags && "\\NONEXISTENT" !in b.flags

    /** „* STATUS "Projekte" (MESSAGES 3 UNSEEN 1)“ → the folder and its counts. */
    private fun statusOf(l: ImapConnection.Line): Pair<String, Pair<Int, Int>>? {
        val items = l.items()
        if (items.size < 4 || items[0] != "*" || (items[1] as? String)?.uppercase() != "STATUS") return null
        val raw = ImapParser.text(items[2]) ?: return null
        val list = items[3] as? List<*> ?: return null
        fun v(name: String) = list.indexOfFirst { (it as? String)?.uppercase() == name }.takeIf { it >= 0 }?.let { (list.getOrNull(it + 1) as? String)?.toIntOrNull() } ?: 0
        return raw to (v("MESSAGES") to v("UNSEEN"))
    }

    /**
     * Every folder with its mails and unread mails (AP-69: the whole tree): with LIST-STATUS in one answer, otherwise
     * STATUS for all folders at once (pipelined, in groups of 50).
     */
    private fun folderList(c: ImapConnection): List<MailFolder> {
        val counts = HashMap<String, Pair<Int, Int>>()
        val all = if (c.has("LIST-STATUS")) {
            val r = c.ok("LIST \"\" \"*\" RETURN (STATUS (MESSAGES UNSEEN))", what = "Ordner lesen")
            r.untagged.forEach { l -> statusOf(l)?.let { (raw, v) -> counts[raw] = v } }
            r.untagged.mapNotNull(::box)
        } else boxes(c)
        val usable = all.filter(::selectable).take(MAX_FOLDERS)
        if (!c.has("LIST-STATUS")) usable.chunked(50).forEach { group ->
            c.pipeline(group.map { "STATUS ${ImapParser.quote(it.raw)} (MESSAGES UNSEEN)" }).forEach { r ->
                r.untagged.forEach { l -> statusOf(l)?.let { (raw, v) -> counts[raw] = v } }
            }
        }
        return usable.map { b ->
            val (items, unread) = counts[b.raw] ?: (0 to 0)
            val path = display(b)
            MailFolder(id = b.raw, path = path, name = path.substringAfterLast('\\'), items = items, unread = unread)
        }
    }

    override fun folders(): List<MailFolder> = session { c -> folderList(c) }

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
            received = internalDate(f["INTERNALDATE"]),
            unread = "\\SEEN" !in flags, flagged = "\\FLAGGED" in flags,
            preview = preview(h["content-type"].orEmpty(), text),
            attachments = ImapParser.attachmentCount(f["BODYSTRUCTURE"]),
        )
    }

    /** INTERNALDATE („ 1-Nov-2026 09:00:00 +0100“, the day may start with a space) in epoch millis; 0 if unreadable. */
    private fun internalDate(v: Any?): Long =
        (v as? String)?.trim()?.let { d -> runCatching { INTERNAL_DATE.get()!!.parse(d)?.time }.getOrNull() } ?: 0

    override fun mail(id: String): MailBody? = session { c ->
        val (uid, folder) = parseId(id)
        c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
        val r = c.ok("UID FETCH $uid (UID FLAGS INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] BODY.PEEK[])")
        val f = r.untagged.mapNotNull { ImapParser.fetch(it) }.firstOrNull() ?: return@session null
        val header = header(f, folder) ?: return@session null
        val raw = f.entries.firstOrNull { it.key == "BODY[]" }?.value as? ByteArray ?: return@session null
        val text = MailText.eml(raw)
        val h = headers(String(raw, Charsets.ISO_8859_1).substringBefore("\r\n\r\n"))
        // The same parts, in the same order, that saveAttachment saves.
        val attachments = MailText.attachments(raw).map { it.first }
        MailBody(header.copy(attachments = attachments.size), cc = MailText.decodeWords(h["cc"].orEmpty()), body = text.substringAfter("\n\n", text), attachmentNames = attachments)
    }

    override fun move(ids: List<String>, folderId: String): Int = session { c -> moveIn(c, ids, folderId).moved }

    override fun moveMails(ids: List<String>, folderId: String): MoveResult = session { c -> moveIn(c, ids, folderId) }

    override fun canMove(): Boolean = caps?.let { "MOVE" in it || "UIDPLUS" in it } ?: true

    /**
     * Moves with the server's MOVE, or with UIDPLUS as copy and then removing exactly these mails from the old folder
     * (UID EXPUNGE touches no other mail). Without either the app does not move. The new UIDs come from COPYUID
     * (UIDPLUS); a server without it is asked for each mail's Message-ID in the target folder.
     */
    private fun moveIn(c: ImapConnection, ids: List<String>, folderId: String): MoveResult {
        val target = ImapParser.quote(folderId)
        var moved = 0
        val newIds = LinkedHashMap<String, String>()
        for ((folder, list) in ids.map { parseId(it) }.groupBy({ it.second }, { it.first })) {
            if (folder == folderId) continue
            if (!c.has("MOVE") && !c.has("UIDPLUS")) throw OutlookException(tr("Dieser Mailserver kann nicht verschieben, ohne dabei andere gelöschte Mails endgültig zu entfernen – deshalb lässt die App das."))
            c.ok("SELECT ${ImapParser.quote(folder)}", what = "Ordner öffnen")
            val set = ImapParser.uidSet(list)
            val messageIds = if (c.has("UIDPLUS")) emptyMap() else messageIds(c, set)
            val answers = if (c.has("MOVE")) listOf(c.ok("UID MOVE $set $target", what = "Verschieben"))
            else listOf(
                c.ok("UID COPY $set $target", what = "Verschieben"),
                c.ok("UID STORE $set +FLAGS.SILENT (\\Deleted)", what = "Verschieben"),
                c.ok("UID EXPUNGE $set", what = "Verschieben"),
            )
            moved += list.size
            val copy = answers.flatMap { r -> listOf(r.text) + r.untagged.map { it.text } }.firstNotNullOfOrNull { COPYUID.find(it) }
            if (copy != null) {
                val from = ImapParser.uids(copy.groupValues[1])
                val to = ImapParser.uids(copy.groupValues[2])
                from.zip(to).forEach { (old, new) -> newIds[id(old, folder)] = id(new, folderId) }
            } else if (messageIds.isNotEmpty()) {
                c.ok("EXAMINE $target", what = "Ordner öffnen")
                messageIds.forEach { (uid, mid) ->
                    numbers(c.ok("UID SEARCH HEADER Message-ID ${ImapParser.quote(mid)}", what = "Suchen"), "SEARCH").maxOrNull()?.let { newIds[id(uid, folder)] = id(it, folderId) }
                }
            }
        }
        forget()
        return MoveResult(moved, newIds)
    }

    /** Message-ID of each mail of [set] in the selected folder (UID → id). */
    private fun messageIds(c: ImapConnection, set: String): Map<Long, String> =
        c.ok("UID FETCH $set (UID BODY.PEEK[HEADER.FIELDS (MESSAGE-ID)])").untagged.mapNotNull { ImapParser.fetch(it) }.mapNotNull { f ->
            val uid = (f["UID"] as? String)?.toLongOrNull() ?: return@mapNotNull null
            val head = f.entries.firstOrNull { it.key.startsWith("BODY[HEADER") }?.value?.let { ImapParser.text(it) }.orEmpty()
            headers(head)["message-id"]?.takeIf { it.isNotBlank() }?.let { uid to it }
        }.toMap()

    /** The numbers of an untagged „* SEARCH 4 7 9“ (or „* SORT …“). */
    private fun numbers(r: ImapConnection.Response, word: String): List<Long> = r.untagged
        .filter { it.text.startsWith("* $word", ignoreCase = true) }
        .flatMap { it.text.substring(2 + word.length).trim().split(' ').mapNotNull(String::toLongOrNull) }

    override fun createFolder(parentId: String?, name: String): MailFolder = session { c ->
        val all = boxes(c)
        val delimiter = all.firstOrNull { it.delimiter != null }?.delimiter ?: "/"
        val raw = (parentId?.let { it + delimiter } ?: "") + ImapParser.encodeFolder(name.trim())
        // A folder that exists already is used as it is (Outlook does the same).
        all.firstOrNull { it.raw == raw }?.let { return@session MailFolder(id = raw, path = display(it), name = name.trim()) }
        c.ok("CREATE ${ImapParser.quote(raw)}", what = "Ordner anlegen")
        runCatching { c.command("SUBSCRIBE ${ImapParser.quote(raw)}") }
        MailFolder(id = raw, path = display(Box(raw, delimiter, emptySet())), name = name.trim())
    }

    // ── Mailansicht (AP-69a) ────────────────────────────────────────────────────────────────────────────────

    override fun inboxId(): String = "INBOX"

    /** A match of a search: folder, UID and arrival (0 when not asked for). */
    private data class Hit(val folder: String, val uid: Long, val date: Long)

    /** The matches of a folder for a search, kept while the folder is unchanged (UIDVALIDITY, EXISTS, UIDNEXT). */
    private class Listing(val stamp: String, val hits: List<Hit>)

    private val listings = object : LinkedHashMap<String, Listing>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Listing>?) = size > 12
    }

    /** After a change by the app the kept matches are void. */
    private fun forget() = synchronized(listings) { listings.clear() }

    /**
     * Every match of [q] in [folder], newest first. With SORT (and without [needDates]) the server sorts by arrival;
     * otherwise the app reads the arrival of each match (short lines) and sorts itself.
     */
    private fun hits(c: ImapConnection, folder: String, q: MailQuery, needDates: Boolean): List<Hit> {
        val examined = c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
        fun find(re: Regex) = examined.untagged.firstNotNullOfOrNull { re.find(it.text)?.groupValues?.get(1) }
        val validity = find(UIDVALIDITY)
        val next = find(UIDNEXT)
        val stamp = "$validity/${find(EXISTS)}/$next"
        val keys = searchKeys(q)
        val sorted = !needDates && c.has("SORT")
        val key = "$folder|$sorted|" + keys.first.joinToString("\u0001") + "|" + keys.second.joinToString("\u0001") { String(it, Charsets.UTF_8) }
        // Flags change without changing the stamp: „ungelesen“ is always asked anew.
        val keep = !q.unreadOnly && validity != null && next != null
        if (keep) synchronized(listings) { listings[key] }?.takeIf { it.stamp == stamp }?.let { return it.hits }
        val list = if (sorted) {
            val parts = listOf("UID SORT (REVERSE ARRIVAL) UTF-8 " + keys.first[0]) + keys.first.drop(1)
            numbers(c.ok(parts, keys.second, what = "Suchen"), "SORT").map { Hit(folder, it, 0) }
        } else {
            val prefix = "UID SEARCH " + if (keys.second.isNotEmpty()) "CHARSET UTF-8 " else ""
            val uids = numbers(c.ok(listOf(prefix + keys.first[0]) + keys.first.drop(1), keys.second, what = "Suchen"), "SEARCH")
            if (uids.isEmpty()) emptyList() else {
                val dates = HashMap<Long, Long>(uids.size * 2)
                c.ok("UID FETCH ${ImapParser.uidSet(uids)} (UID INTERNALDATE)", what = "Mails lesen").untagged.forEach { l ->
                    ImapParser.fetch(l)?.let { f -> (f["UID"] as? String)?.toLongOrNull()?.let { u -> dates[u] = internalDate(f["INTERNALDATE"]) } }
                }
                uids.map { Hit(folder, it, dates[it] ?: 0) }
                    // SINCE and BEFORE count whole days; the exact times are checked here.
                    .filter { h -> h.date == 0L || (q.since == null || h.date >= q.since) && (q.until == null || h.date < q.until) }
                    .sortedWith(compareByDescending<Hit> { it.date }.thenByDescending { it.uid })
            }
        }
        if (keep) synchronized(listings) { listings[key] = Listing(stamp, list) }
        return list
    }

    override fun page(query: MailQuery, offset: Int, limit: Int): MailPage = session { c ->
        val folders = if (query.allFolders) folderList(c).map { it.id } else listOf(query.folderId ?: "INBOX")
        val all = folders.flatMap { f -> runCatching { hits(c, f, query, needDates = folders.size > 1) }.getOrElse { e -> if (folders.size > 1) emptyList() else throw e } }
            .let { if (folders.size > 1) it.sortedWith(compareByDescending<Hit> { h -> h.date }.thenByDescending { h -> h.uid }) else it }
        val slice = all.drop(offset).take(limit.coerceIn(1, 500))
        val headers = HashMap<String, MailHeader>()
        slice.groupBy { it.folder }.forEach { (folder, list) ->
            c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
            c.ok("UID FETCH ${ImapParser.uidSet(list.map { it.uid })} (UID FLAGS INTERNALDATE BODYSTRUCTURE BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE CONTENT-TYPE)] BODY.PEEK[TEXT]<0.2000>)", what = "Mails lesen")
                .untagged.mapNotNull { ImapParser.fetch(it) }.mapNotNull { header(it, folder) }.forEach { headers[it.id] = it }
        }
        // „Mit Anhang“ asks the server for multipart/mixed; the structure tells for sure.
        val mails = slice.mapNotNull { headers[id(it.uid, it.folder)] }.filter { !query.withAttachments || it.attachments > 0 }
        MailPage(mails, all.size, offset + slice.size)
    }

    override fun markRead(ids: List<String>, read: Boolean): Int = session { c ->
        var n = 0
        for ((folder, uids) in ids.map { parseId(it) }.groupBy({ it.second }, { it.first })) {
            c.ok("SELECT ${ImapParser.quote(folder)}", what = "Ordner öffnen")
            c.ok("UID STORE ${ImapParser.uidSet(uids)} ${if (read) "+" else "-"}FLAGS.SILENT (\\Seen)", what = "Markieren")
            n += uids.size
        }
        forget()
        n
    }

    override fun renameFolder(id: String, name: String): MailFolder = session { c ->
        val all = boxes(c)
        val box = all.firstOrNull { it.raw == id } ?: throw OutlookException(tr("Den Ordner gibt es auf dem Mailserver nicht mehr."))
        // RENAME of INBOX would move all its mails into a new folder; the fixed folders stay as the server named them.
        if (id.equals("INBOX", ignoreCase = true) || box.flags.any { it in FIXED }) {
            throw OutlookException(tr("Den Posteingang und die festen Ordner (Entwürfe, Gesendet, Papierkorb …) benennt die App nicht um."))
        }
        val d = box.delimiter
        val parent = if (d.isNullOrEmpty() || !id.contains(d)) "" else id.substringBeforeLast(d) + d
        val raw = parent + ImapParser.encodeFolder(name.trim())
        if (raw != id) {
            if (all.any { it.raw == raw }) throw OutlookException(tr("Einen Ordner „{0}“ gibt es dort schon.", name.trim()))
            c.ok("RENAME ${ImapParser.quote(id)} ${ImapParser.quote(raw)}", what = "Ordner umbenennen")
            runCatching { c.command("SUBSCRIBE ${ImapParser.quote(raw)}") }
            runCatching { c.command("UNSUBSCRIBE ${ImapParser.quote(id)}") }
            forget()
        }
        val (items, unread) = status(c, raw)
        val path = display(Box(raw, d, emptySet()))
        MailFolder(id = raw, path = path, name = path.substringAfterLast('\\'), items = items, unread = unread)
    }

    override fun saveAttachment(id: String, index: Int, dir: File): File = session { c ->
        val (uid, folder) = parseId(id)
        c.ok("EXAMINE ${ImapParser.quote(folder)}", what = "Ordner öffnen")
        val raw = c.ok("UID FETCH $uid (UID BODY.PEEK[])").untagged.mapNotNull { ImapParser.fetch(it) }.firstOrNull()?.get("BODY[]") as? ByteArray
            ?: throw OutlookException(tr("Diese Mail gibt es auf dem Mailserver nicht mehr."))
        val (name, bytes) = MailText.attachments(raw).getOrNull(index) ?: throw OutlookException(tr("Diesen Anhang gibt es in der Mail nicht."))
        dir.mkdirs()
        LoraDrive.unique(dir, LoraDrive.safe(name)).also { it.writeBytes(bytes) }
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

    companion object {
        private val SEARCH_DATE = ThreadLocal.withInitial { SimpleDateFormat("dd-MMM-yyyy", Locale.US) }
        private val INTERNAL_DATE = ThreadLocal.withInitial { SimpleDateFormat("d-MMM-yyyy HH:mm:ss Z", Locale.US) }
        private val COPYUID = Regex("COPYUID \\d+ ([0-9:,]+) ([0-9:,]+)", RegexOption.IGNORE_CASE)
        private val UIDVALIDITY = Regex("\\[UIDVALIDITY (\\d+)\\]", RegexOption.IGNORE_CASE)
        private val UIDNEXT = Regex("\\[UIDNEXT (\\d+)\\]", RegexOption.IGNORE_CASE)
        private val EXISTS = Regex("^\\* (\\d+) EXISTS", RegexOption.IGNORE_CASE)

        /** Folders the app does not rename (special use, RFC 6154). */
        private val FIXED = setOf("\\DRAFTS", "\\SENT", "\\TRASH", "\\JUNK", "\\ALL")

        /** At most this many folders in the tree. */
        private const val MAX_FOLDERS = 500

        /**
         * The SEARCH keys of [q] as text parts around literals (AP-69): sender, subject, words (TEXT: head and body),
         * days (SINCE/BEFORE), UNSEEN and – for „mit Anhang“ – multipart/mixed mails. ASCII values are quoted, others go
         * as UTF-8 literals (the caller then adds CHARSET UTF-8). Without any key: ALL.
         */
        fun searchKeys(q: MailQuery, zone: ZoneId = ZoneId.systemDefault()): Pair<List<String>, List<ByteArray>> {
            val tokens = mutableListOf<Any>()
            fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            fun date(d: java.time.LocalDate) = java.time.format.DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.US).format(d)
            fun value(s: String) { tokens += if (s.all { it.code in 0x20..0x7e }) ImapParser.quote(s) else s.toByteArray(Charsets.UTF_8) }
            q.since?.let { tokens += "SINCE " + date(day(it)) }
            // BEFORE is the first day not wanted: the day after the last mail that may come.
            q.until?.let { tokens += "BEFORE " + date(day(it - 1).plusDays(1)) }
            if (q.unreadOnly) tokens += "UNSEEN"
            if (q.from.isNotBlank()) { tokens += "FROM"; value(q.from.trim()) }
            if (q.subject.isNotBlank()) { tokens += "SUBJECT"; value(q.subject.trim()) }
            q.words.forEach { w -> tokens += "TEXT"; value(w) }
            if (q.withAttachments) tokens += "HEADER Content-Type \"multipart/mixed\""
            if (tokens.isEmpty()) tokens += "ALL"
            val parts = mutableListOf<String>()
            val literals = mutableListOf<ByteArray>()
            val sb = StringBuilder()
            var afterLiteral = false
            for (t in tokens) {
                if (sb.isNotEmpty() || afterLiteral) sb.append(' ')
                afterLiteral = false
                when (t) {
                    is ByteArray -> {
                        parts += sb.toString()
                        sb.clear()
                        literals += t
                        afterLiteral = true
                    }
                    else -> sb.append(t)
                }
            }
            parts += sb.toString()
            return parts to literals
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
