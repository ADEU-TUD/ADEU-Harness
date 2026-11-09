package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.Variant.VARIANT
import com.sun.jna.platform.win32.WinReg
import io.github.thepytales.llmhub.data.BusyTimes
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.data.MailPage
import io.github.thepytales.llmhub.data.MailQuery
import io.github.thepytales.llmhub.data.MoveResult
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.data.OutlookException
import io.github.thepytales.llmhub.ui.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.Date

/** Short name inside this file. */
private typealias Com = ComObj

/**
 * The classic Outlook over COM (AP-13): late binding through JNA, which the app has anyway – no PowerShell, no extra
 * library, no admin rights. Every call runs on one COM thread (single-threaded apartment); Outlook starts in the
 * background if it is not open. Every object of a call is released at its end, so Outlook can close normally.
 *
 * Deliberately missing: Delete and Send. Answers and invitations are saved as drafts (or shown in Outlook) for the
 * user to send.
 *
 * Note: depending on the PC's virus scanner Outlook may ask once „Ein Programm versucht, auf E-Mail-Adressen
 * zuzugreifen“ (Object Model Guard) – the user allows it there.
 */
object ComOutlook : OutlookBridge {
    private const val TIMEOUT_S = 90L
    private const val INBOX = 6
    private const val CALENDAR = 9
    private const val MAIL_ITEM = 0
    private const val APPOINTMENT_ITEM = 1
    private const val TASK_ITEM = 3
    private const val CLASS_MAIL = 43L

    /** Searching all folders shows at most this many mails. */
    private const val ALL_FOLDERS_MAX = 1000

    private val thread = ComThread("outlook-com")

    /** Runs [block] on the COM thread with Outlook's application and MAPI namespace; maps errors to plain words. */
    private fun <T> session(block: (app: Com, ns: Com) -> T): T = try {
        thread.run(
            TIMEOUT_S,
            onTimeout = {
                OutlookException(tr("Outlook antwortet nicht. Ist in Outlook gerade ein Fenster oder eine Rückfrage offen (z. B. „Ein Programm versucht, auf E-Mail-Adressen zuzugreifen“)? Bitte dort antworten und es noch einmal versuchen."))
            },
        ) {
            val app = try {
                // CoCreateInstance attaches to a running Outlook (one instance per user) or starts it in the background.
                Com("Outlook.Application")
            } catch (e: COMException) {
                throw OutlookException(startProblem(e))
            }
            val ns = app.obj("GetNamespace", VARIANT("MAPI"))
            block(app, ns)
        }
    } catch (e: OutlookException) {
        throw e
    } catch (e: Exception) {
        throw OutlookException(problem(e))
    }

    private fun hresult(e: Throwable): Int? = ComErrors.hresult(e)

    /** „Class not registered“ and „Invalid class string“ (no Outlook.Application at all): the classic Outlook is missing. */
    private val NOT_INSTALLED = setOf(0x80040154.toInt(), 0x800401F3.toInt())

    private fun startProblem(e: COMException): String = when (hresult(e)) {
        in NOT_INSTALLED -> tr("Das klassische Outlook ist auf diesem PC nicht installiert. Das neue Outlook (und Outlook im Browser) bieten keine Schnittstelle für Programme – dafür kommt später der Weg über den Mailserver.")
        0x80080005.toInt() -> tr("Outlook ließ sich nicht ansprechen. Läuft Outlook mit anderen Rechten als Lora (z. B. „Als Administrator ausführen“)? Dann beide normal starten.")
        else -> tr("Outlook ließ sich nicht starten: {0}", e.message ?: tr("unbekannter Fehler"))
    }

    private fun problem(e: Throwable): String = when (hresult(e)) {
        in NOT_INSTALLED -> tr("Das klassische Outlook ist auf diesem PC nicht installiert.")
        0x80004004.toInt() -> tr("Der Zugriff wurde in Outlook abgelehnt (Rückfrage „Ein Programm versucht …“ mit „Nein“ beantwortet?).")
        else -> tr("Outlook meldet einen Fehler: {0}", e.message ?: e.javaClass.simpleName)
    }

    // ── Ids: entry id and store id together, so items of every mailbox (also shared ones) are found again. ──

    private fun idOf(o: Com, storeId: String): String = o.str("EntryID") + "|" + storeId

    private fun folderOf(ns: Com, id: String?): Com {
        if (id.isNullOrBlank()) return ns.obj("GetDefaultFolder", VARIANT(INBOX))
        val (entry, store) = id.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return try {
            if (store.isEmpty()) ns.obj("GetFolderFromID", VARIANT(entry)) else ns.obj("GetFolderFromID", VARIANT(entry), VARIANT(store))
        } catch (e: Exception) {
            throw OutlookException(tr("Den Ordner gibt es in Outlook nicht mehr (oder er gehört zu einem anderen Postfach)."))
        }
    }

    private fun itemOf(ns: Com, id: String): Com {
        val (entry, store) = id.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return try {
            if (store.isEmpty()) ns.obj("GetItemFromID", VARIANT(entry)) else ns.obj("GetItemFromID", VARIANT(entry), VARIANT(store))
        } catch (e: Exception) {
            throw OutlookException(tr("Diese Mail gibt es in Outlook nicht mehr (verschoben oder gelöscht)."))
        }
    }

    override fun connect(): String = session { _, ns ->
        val accounts = ns.objOrNull("Accounts")
        val names = if (accounts == null) emptyList() else (1..accounts.num("Count")).mapNotNull { i ->
            accounts.objOrNull("Item", VARIANT(i.toInt()))?.let { a -> a.str("SmtpAddress").ifBlank { a.str("DisplayName") } }
        }
        names.joinToString(", ").ifBlank { ns.objOrNull("CurrentUser")?.str("Name").orEmpty().ifBlank { "Outlook" } }
    }

    override fun folders(): List<MailFolder> = session { _, ns -> mailFolders(ns).map { it.first } }

    /** Every mail folder of every mailbox of the profile (no calendar, contacts, tasks, notes), with its COM object. */
    private fun mailFolders(ns: Com): List<Pair<MailFolder, Com>> {
        val out = mutableListOf<Pair<MailFolder, Com>>()
        fun walk(f: Com, store: String, depth: Int) {
            if (out.size >= 1500 || depth > 8) return
            if (depth > 0 && f.num("DefaultItemType") == MAIL_ITEM.toLong()) {
                out += MailFolder(idOf(f, store), f.str("FolderPath"), f.str("Name"), runCatching { f.obj("Items").num("Count").toInt() }.getOrDefault(0), f.num("UnReadItemCount").toInt()) to f
            }
            val subs = f.objOrNull("Folders") ?: return
            for (i in 1..subs.num("Count")) subs.objOrNull("Item", VARIANT(i.toInt()))?.let { walk(it, store, depth + 1) }
        }
        val roots = ns.obj("Folders")
        for (i in 1..roots.num("Count")) {
            val root = roots.objOrNull("Item", VARIANT(i.toInt())) ?: continue
            walk(root, root.str("StoreID"), 0)
        }
        return out
    }

    private fun header(m: Com, folderPath: String, store: String, withPreview: Boolean): MailHeader? {
        val cls = m.num("Class")
        val kind = when (cls) {
            CLASS_MAIL -> "Mail"
            in 53L..57L -> "Einladung"
            else -> return null
        }
        val body = if (withPreview) m.str("Body") else ""
        return MailHeader(
            id = idOf(m, store),
            folder = folderPath,
            from = m.str("SenderName"),
            fromAddress = m.str("SenderEmailAddress").takeIf { '@' in it }.orEmpty(),
            to = m.str("To"),
            subject = m.str("Subject"),
            received = m.date("ReceivedTime") ?: 0L,
            unread = m.bool("UnRead"),
            important = m.num("Importance") == 2L,
            flagged = m.num("FlagStatus") == 2L,
            categories = m.str("Categories"),
            preview = body.replace(Regex("\\s+"), " ").trim().take(300),
            attachments = m.objOrNull("Attachments")?.num("Count")?.toInt() ?: 0,
            kind = kind,
        )
    }

    override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int): List<MailHeader> = session { _, ns ->
        val folder = folderOf(ns, folderId)
        val path = folder.str("FolderPath")
        val store = folder.str("StoreID")
        var items = folder.obj("Items")
        if (unreadOnly) items = items.obj("Restrict", VARIANT("[UnRead] = True"))
        items.run("Sort", VARIANT("[ReceivedTime]"), VARIANT(true))
        val out = mutableListOf<MailHeader>()
        var m = items.objOrNull("GetFirst")
        var seen = 0
        while (m != null && out.size < limit && seen < limit * 4) {
            seen++
            val h = header(m, path, store, withPreview = true)
            if (h != null) {
                if (since != null && h.received in 1 until since) break
                out += h
            }
            m = items.objOrNull("GetNext")
        }
        out
    }

    override fun mail(id: String): MailBody? = session { _, ns ->
        val m = itemOf(ns, id)
        val store = id.substringAfter('|', "")
        val parent = m.objOrNull("Parent")
        val h = header(m, parent?.str("FolderPath").orEmpty(), store, withPreview = false) ?: return@session null
        val atts = m.objOrNull("Attachments")
        val names = if (atts == null) emptyList() else (1..atts.num("Count")).mapNotNull { i -> atts.objOrNull("Item", VARIANT(i.toInt()))?.str("FileName") }
        MailBody(h, cc = m.str("CC"), body = m.str("Body"), attachmentNames = names)
    }

    override fun move(ids: List<String>, folderId: String): Int = session { _, ns -> moveIn(ns, ids, folderId).moved }

    override fun moveMails(ids: List<String>, folderId: String): MoveResult = session { _, ns -> moveIn(ns, ids, folderId) }

    /** MailItem.Move returns the moved mail – with a NEW EntryID in the target (AP-69: for Rückgängig). */
    private fun moveIn(ns: Com, ids: List<String>, folderId: String): MoveResult {
        val target = folderOf(ns, folderId)
        val store = target.str("StoreID")
        var moved = 0
        val newIds = LinkedHashMap<String, String>()
        ids.forEach { id ->
            val m = runCatching { itemOf(ns, id) }.getOrNull() ?: return@forEach
            val result = runCatching { m.get("Move", target.toVariant()) }
            if (result.isFailure) return@forEach
            moved++
            (result.getOrNull() as? Com)?.str("EntryID")?.takeIf { it.isNotEmpty() }?.let { newIds[id] = "$it|$store" }
        }
        return MoveResult(moved, newIds)
    }

    override fun createFolder(parentId: String?, name: String): MailFolder = session { _, ns ->
        val parent = folderOf(ns, parentId)
        val store = parent.str("StoreID")
        val subs = parent.obj("Folders")
        // An existing folder of that name is used as it is.
        val existing = (1..subs.num("Count")).firstNotNullOfOrNull { i ->
            subs.objOrNull("Item", VARIANT(i.toInt()))?.takeIf { it.str("Name").equals(name, ignoreCase = true) }
        }
        val f = existing ?: subs.obj("Add", VARIANT(name))
        MailFolder(idOf(f, store), f.str("FolderPath"), f.str("Name"))
    }

    override fun draft(draft: MailDraft): String = session { app, ns ->
        val item: Com
        val store: String
        if (draft.replyTo != null) {
            val original = itemOf(ns, draft.replyTo)
            store = draft.replyTo.substringAfter('|', "")
            item = original.obj(if (draft.replyAll) "ReplyAll" else "Reply")
            val html = item.str("HTMLBody")
            if (html.isNotBlank()) {
                // The answer goes above the quoted mail, in Outlook's own HTML.
                val text = htmlParagraphs(draft.body)
                val at = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(html)?.range?.last?.plus(1) ?: 0
                item.put("HTMLBody", html.substring(0, at) + text + html.substring(at))
            } else item.put("Body", draft.body + "\r\n\r\n" + item.str("Body"))
            if (draft.cc.isNotBlank()) item.put("CC", draft.cc)
        } else {
            item = app.obj("CreateItem", VARIANT(MAIL_ITEM))
            store = ""
            item.put("To", draft.to)
            if (draft.cc.isNotBlank()) item.put("CC", draft.cc)
            item.put("Subject", draft.subject)
            item.put("Body", draft.body)
        }
        if (draft.replyTo != null && draft.subject.isNotBlank()) item.put("Subject", draft.subject)
        item.run("Save")
        if (draft.show) runCatching { item.run("Display") }
        idOf(item, store.ifBlank { item.objOrNull("Parent")?.str("StoreID").orEmpty() })
    }

    private fun htmlParagraphs(text: String): String = text.trim().split(Regex("\\n\\s*\\n")).joinToString("") { p ->
        "<p>" + p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"
    } + "<p>&nbsp;</p>"

    /** Short date of Windows' regional settings (e.g. dd.MM.yyyy) – Outlook reads filter dates in that format. */
    private val filterFormat: DateTimeFormatter by lazy {
        val windows = runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "Control Panel\\International", "sShortDate") }.getOrNull()
        val pattern = windows?.takeIf { it.isNotBlank() && it.all { ch -> ch in "dMy" || !ch.isLetter() } } ?: "dd.MM.yyyy"
        DateTimeFormatter.ofPattern("$pattern HH:mm")
    }

    override fun events(from: Long, to: Long): List<CalendarEntry> = session { _, ns ->
        val folder = ns.obj("GetDefaultFolder", VARIANT(CALENDAR))
        val store = folder.str("StoreID")
        val zone = ZoneId.systemDefault()
        fun fmt(m: Long) = filterFormat.format(Instant.ofEpochMilli(m).atZone(zone))
        val all = folder.obj("Items")
        val restricted = runCatching {
            // Recurring appointments as single occurrences: sort by start, include recurrences, then restrict.
            all.run("Sort", VARIANT("[Start]"))
            all.put("IncludeRecurrences", true)
            all.obj("Restrict", VARIANT("[Start] < '${fmt(to)}' AND [End] > '${fmt(from)}'"))
        }.getOrNull()
        val out = mutableListOf<CalendarEntry>()
        val items = restricted ?: folder.obj("Items")
        var a = items.objOrNull("GetFirst")
        var seen = 0
        while (a != null && out.size < 300 && seen < 5000) {
            seen++
            val start = a.date("Start") ?: 0L
            val end = a.date("End") ?: start
            if (start < to && end > from) {
                out += CalendarEntry(
                    id = idOf(a, store), subject = a.str("Subject"), start = start, end = end, location = a.str("Location"),
                    allDay = a.bool("AllDayEvent"), busy = a.num("BusyStatus").toInt(), organizer = a.str("Organizer"), recurring = a.bool("IsRecurring"),
                )
            } else if (restricted != null && start >= to) break
            a = items.objOrNull("GetNext")
        }
        out.sortedBy { it.start }
    }

    override fun createEvent(event: NewEvent): String = session { app, _ ->
        val a = app.obj("CreateItem", VARIANT(APPOINTMENT_ITEM))
        a.put("Subject", event.subject)
        if (event.location.isNotBlank()) a.put("Location", event.location)
        if (event.body.isNotBlank()) a.put("Body", event.body)
        a.put("AllDayEvent", event.allDay)
        a.put("Start", Date(event.start))
        a.put("End", Date(event.end))
        if (event.reminderMinutes != null) {
            a.put("ReminderSet", true)
            a.put("ReminderMinutesBeforeStart", event.reminderMinutes)
        } else a.put("ReminderSet", false)
        if (event.attendees.isNotEmpty()) {
            // A meeting: the invitation is prepared, not sent – the user sends it in Outlook.
            a.put("MeetingStatus", 1)
            val recipients = a.obj("Recipients")
            event.attendees.forEach { recipients.get("Add", VARIANT(it)) }
            runCatching { recipients.get("ResolveAll") }
        }
        a.run("Save")
        if (event.show || event.attendees.isNotEmpty()) runCatching { a.run("Display") }
        idOf(a, a.objOrNull("Parent")?.str("StoreID").orEmpty())
    }

    override fun createTask(task: NewTask): String = session { app, _ ->
        val t = app.obj("CreateItem", VARIANT(TASK_ITEM))
        t.put("Subject", task.subject)
        if (task.body.isNotBlank()) t.put("Body", task.body)
        task.due?.let { t.put("DueDate", Date(it)) }
        task.reminder?.let {
            t.put("ReminderSet", true)
            t.put("ReminderTime", Date(it))
        }
        t.run("Save")
        idOf(t, t.objOrNull("Parent")?.str("StoreID").orEmpty())
    }

    override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int): List<BusyTimes> = session { _, ns ->
        val zone = ZoneId.systemDefault()
        val midnight = Instant.ofEpochMilli(day).atZone(zone).toLocalDate().atStartOfDay(zone)
        people.map { person ->
            val me = person.trim().lowercase() in setOf("ich", "me", "mich", "selbst")
            val r = if (me) ns.objOrNull("CurrentUser") else ns.obj("CreateRecipient", VARIANT(person.trim()))
            val resolved = r != null && (me || runCatching { r.get("Resolve") as? Boolean }.getOrNull() == true)
            val states = if (!resolved) "" else runCatching {
                r!!.get("FreeBusy", VARIANT(Date.from(midnight.toInstant())), VARIANT(slotMinutes), VARIANT(true)) as? String
            }.getOrNull().orEmpty()
            BusyTimes(if (me) "ich" else person.trim(), resolved && states.isNotEmpty(), midnight.toInstant().toEpochMilli(), slotMinutes, states)
        }
    }

    override fun show(id: String) = session { _, ns -> itemOf(ns, id).run("Display") }

    // ── Mailansicht (AP-69a) ────────────────────────────────────────────────────────────────────────────────

    override fun inboxId(): String = session { _, ns ->
        val f = ns.obj("GetDefaultFolder", VARIANT(INBOX))
        idOf(f, f.str("StoreID"))
    }

    /** Outlook's own filter (Jet) for time and unread – dates in the short format of Windows, local time. */
    private fun jetFilter(q: MailQuery): String? {
        val zone = ZoneId.systemDefault()
        fun fmt(m: Long) = filterFormat.format(Instant.ofEpochMilli(m).atZone(zone))
        val parts = listOfNotNull(
            q.since?.let { "[ReceivedTime] >= '${fmt(it)}'" },
            q.until?.let { "[ReceivedTime] < '${fmt(it)}'" },
            "[UnRead] = True".takeIf { q.unreadOnly },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    /** The DASL filter (@SQL) for sender, subject, words (subject, sender, text) and attachments; null when none is asked. */
    internal fun daslFilter(q: MailQuery): String? {
        fun like(prop: String, v: String) = "\"urn:schemas:httpmail:$prop\" LIKE '%${v.replace("'", "''")}%'"
        val parts = listOfNotNull(
            "\"urn:schemas:httpmail:hasattachment\" = 1".takeIf { q.withAttachments },
            q.from.trim().takeIf { it.isNotEmpty() }?.let { "(" + like("fromname", it) + " OR " + like("fromemail", it) + ")" },
            q.subject.trim().takeIf { it.isNotEmpty() }?.let { like("subject", it) },
        ) + q.words.map { w -> "(" + like("subject", w) + " OR " + like("fromname", w) + " OR " + like("textdescription", w) + ")" }
        return parts.takeIf { it.isNotEmpty() }?.let { "@SQL=" + it.joinToString(" AND ") }
    }

    /** The items of [folder] that fit [q], newest first; null when Outlook rejects the filter (then the app filters itself). */
    private fun filtered(folder: Com, q: MailQuery): Com? = runCatching {
        var items = folder.obj("Items")
        jetFilter(q)?.let { items = items.obj("Restrict", VARIANT(it)) }
        daslFilter(q)?.let { items = items.obj("Restrict", VARIANT(it)) }
        items.run("Sort", VARIANT("[ReceivedTime]"), VARIANT(true))
        items
    }.getOrNull()

    override fun page(query: MailQuery, offset: Int, limit: Int): MailPage = session { _, ns ->
        if (query.allFolders) return@session pageOfAll(ns, query, offset, limit)
        val folder = folderOf(ns, query.folderId)
        val path = folder.str("FolderPath")
        val store = folder.str("StoreID")
        val items = filtered(folder, query) ?: return@session pageScanning(folder, path, store, query, offset, limit)
        val total = items.num("Count").toInt()
        val out = mutableListOf<MailHeader>()
        var i = offset
        // Items.Item(i) follows the sort; reports and other non-mails are skipped but counted.
        while (i < total && out.size < limit) {
            i++
            items.objOrNull("Item", VARIANT(i))?.let { m -> header(m, path, store, withPreview = true)?.let { out += it } }
        }
        MailPage(out, total, i)
    }

    /** When Outlook rejects a filter (some stores): read the folder newest first and filter here, at most 5000 per page. */
    private fun pageScanning(folder: Com, path: String, store: String, q: MailQuery, offset: Int, limit: Int): MailPage {
        val items = folder.obj("Items")
        items.run("Sort", VARIANT("[ReceivedTime]"), VARIANT(true))
        val count = items.num("Count").toInt()
        val out = mutableListOf<MailHeader>()
        var matched = 0
        var i = 0
        while (i < count && out.size < limit && i < offset * 8 + 5000) {
            i++
            val m = items.objOrNull("Item", VARIANT(i)) ?: continue
            val h = header(m, path, store, withPreview = true) ?: continue
            // Newest first: below the start of the time span nothing more fits.
            if (q.since != null && h.received in 1 until q.since) break
            if (!Outlook.fits(q, h)) continue
            if (matched++ >= offset) out += h
        }
        val more = i < count && out.size >= limit
        return MailPage(out, if (more) offset + out.size + 1 else offset + out.size, offset + out.size)
    }

    /**
     * Searching all folders: each folder filtered by Outlook, the newest [offset] + [limit] of each merged by time.
     * At most 1000 results, so a search over a big mailbox stays quick.
     */
    private fun pageOfAll(ns: Com, q: MailQuery, offset: Int, limit: Int): MailPage {
        val wanted = (offset + limit).coerceAtMost(ALL_FOLDERS_MAX)
        data class Source(val path: String, val store: String, val items: Com, val count: Int)
        data class Hit(val received: Long, val source: Int, val index: Int)
        val sources = mailFolders(ns).filter { it.first.items > 0 }.mapNotNull { (mf, f) ->
            val items = filtered(f, q) ?: return@mapNotNull null
            val n = items.num("Count").toInt()
            if (n == 0) null else Source(mf.path, f.str("StoreID"), items, n)
        }.take(150)
        val hits = mutableListOf<Hit>()
        sources.forEachIndexed { s, src ->
            for (i in 1..minOf(src.count, wanted)) src.items.objOrNull("Item", VARIANT(i))?.let { m -> hits += Hit(m.date("ReceivedTime") ?: 0L, s, i) }
        }
        hits.sortByDescending { it.received }
        val slice = hits.drop(offset).take(limit)
        val out = slice.mapNotNull { h -> sources[h.source].let { src -> src.items.objOrNull("Item", VARIANT(h.index))?.let { header(it, src.path, src.store, withPreview = true) } } }
        val total = minOf(sources.sumOf { it.count }, ALL_FOLDERS_MAX)
        return MailPage(out, total, offset + slice.size)
    }

    override fun markRead(ids: List<String>, read: Boolean): Int = session { _, ns ->
        var n = 0
        ids.forEach { id ->
            val m = runCatching { itemOf(ns, id) }.getOrNull() ?: return@forEach
            if (runCatching { m.put("UnRead", !read); m.run("Save") }.isSuccess) n++
        }
        n
    }

    /** Folders Outlook keeps under their own names: Gelöschte Elemente, Postausgang, Gesendete Elemente, Posteingang, Entwürfe, Junk-E-Mail. */
    private val DEFAULT_FOLDERS = listOf(3, 4, 5, 6, 16, 23)

    override fun renameFolder(id: String, name: String): MailFolder = session { _, ns ->
        val f = folderOf(ns, id)
        val entry = f.str("EntryID")
        if (DEFAULT_FOLDERS.any { kind -> runCatching { ns.obj("GetDefaultFolder", VARIANT(kind)).str("EntryID") == entry }.getOrDefault(false) }) {
            throw OutlookException(tr("Den Posteingang und die festen Ordner (Entwürfe, Gesendete Elemente, Gelöschte Elemente …) benennt die App nicht um."))
        }
        val store = f.str("StoreID")
        try {
            f.put("Name", name.trim())
        } catch (e: Exception) {
            throw OutlookException(tr("Outlook lässt den Ordner nicht umbenennen – gibt es dort schon einen Ordner „{0}“?", name.trim()))
        }
        MailFolder(idOf(f, store), f.str("FolderPath"), f.str("Name"), runCatching { f.obj("Items").num("Count").toInt() }.getOrDefault(0), f.num("UnReadItemCount").toInt())
    }

    override fun saveAttachment(id: String, index: Int, dir: File): File = session { _, ns ->
        val atts = itemOf(ns, id).obj("Attachments")
        if (index < 0 || index >= atts.num("Count")) throw OutlookException(tr("Diesen Anhang gibt es in der Mail nicht."))
        val a = atts.obj("Item", VARIANT(index + 1))
        dir.mkdirs()
        val file = LoraDrive.unique(dir, LoraDrive.safe(a.str("FileName").ifBlank { a.str("DisplayName") }.ifBlank { "Anhang" }))
        a.run("SaveAsFile", VARIANT(file.absolutePath))
        file
    }

    /** Today's date in Outlook's filter format (for the self-test). */
    internal fun filterDate(day: LocalDate = LocalDate.now()): String = filterFormat.format(day.atStartOfDay())
}
