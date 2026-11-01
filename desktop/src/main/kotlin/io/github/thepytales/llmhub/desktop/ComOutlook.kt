package io.github.thepytales.llmhub.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.Variant.VARIANT
import com.sun.jna.platform.win32.WinReg
import io.github.thepytales.llmhub.data.BusyTimes
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.data.OutlookException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    private val thread = ComThread("outlook-com")

    /** Runs [block] on the COM thread with Outlook's application and MAPI namespace; maps errors to plain words. */
    private fun <T> session(block: (app: Com, ns: Com) -> T): T = try {
        thread.run(
            TIMEOUT_S,
            onTimeout = {
                OutlookException("Outlook antwortet nicht. Ist in Outlook gerade ein Fenster oder eine Rückfrage offen (z. B. „Ein Programm versucht, auf E-Mail-Adressen zuzugreifen“)? Bitte dort antworten und es noch einmal versuchen.")
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
        in NOT_INSTALLED -> "Das klassische Outlook ist auf diesem PC nicht installiert. Das neue Outlook (und Outlook im Browser) bieten keine Schnittstelle für Programme – dafür kommt später der Weg über den Mailserver."
        0x80080005.toInt() -> "Outlook ließ sich nicht ansprechen. Läuft Outlook mit anderen Rechten als Lora (z. B. „Als Administrator ausführen“)? Dann beide normal starten."
        else -> "Outlook ließ sich nicht starten: ${e.message ?: "unbekannter Fehler"}"
    }

    private fun problem(e: Throwable): String = when (hresult(e)) {
        in NOT_INSTALLED -> "Das klassische Outlook ist auf diesem PC nicht installiert."
        0x80004004.toInt() -> "Der Zugriff wurde in Outlook abgelehnt (Rückfrage „Ein Programm versucht …“ mit „Nein“ beantwortet?)."
        else -> "Outlook meldet einen Fehler: ${e.message ?: e.javaClass.simpleName}"
    }

    // ── Ids: entry id and store id together, so items of every mailbox (also shared ones) are found again. ──

    private fun idOf(o: Com, storeId: String): String = o.str("EntryID") + "|" + storeId

    private fun folderOf(ns: Com, id: String?): Com {
        if (id.isNullOrBlank()) return ns.obj("GetDefaultFolder", VARIANT(INBOX))
        val (entry, store) = id.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return try {
            if (store.isEmpty()) ns.obj("GetFolderFromID", VARIANT(entry)) else ns.obj("GetFolderFromID", VARIANT(entry), VARIANT(store))
        } catch (e: Exception) {
            throw OutlookException("Den Ordner gibt es in Outlook nicht mehr (oder er gehört zu einem anderen Postfach).")
        }
    }

    private fun itemOf(ns: Com, id: String): Com {
        val (entry, store) = id.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return try {
            if (store.isEmpty()) ns.obj("GetItemFromID", VARIANT(entry)) else ns.obj("GetItemFromID", VARIANT(entry), VARIANT(store))
        } catch (e: Exception) {
            throw OutlookException("Diese Mail gibt es in Outlook nicht mehr (verschoben oder gelöscht).")
        }
    }

    override fun connect(): String = session { _, ns ->
        val accounts = ns.objOrNull("Accounts")
        val names = if (accounts == null) emptyList() else (1..accounts.num("Count")).mapNotNull { i ->
            accounts.objOrNull("Item", VARIANT(i.toInt()))?.let { a -> a.str("SmtpAddress").ifBlank { a.str("DisplayName") } }
        }
        names.joinToString(", ").ifBlank { ns.objOrNull("CurrentUser")?.str("Name").orEmpty().ifBlank { "Outlook" } }
    }

    override fun folders(): List<MailFolder> = session { _, ns ->
        val out = mutableListOf<MailFolder>()
        fun walk(f: Com, store: String, depth: Int) {
            if (out.size >= 1500 || depth > 8) return
            // Mail folders only (no calendar, contacts, tasks, notes).
            if (depth > 0 && f.num("DefaultItemType") == MAIL_ITEM.toLong()) {
                out += MailFolder(idOf(f, store), f.str("FolderPath"), f.str("Name"), runCatching { f.obj("Items").num("Count").toInt() }.getOrDefault(0), f.num("UnReadItemCount").toInt())
            }
            val subs = f.objOrNull("Folders") ?: return
            for (i in 1..subs.num("Count")) subs.objOrNull("Item", VARIANT(i.toInt()))?.let { walk(it, store, depth + 1) }
        }
        val roots = ns.obj("Folders")
        for (i in 1..roots.num("Count")) {
            val root = roots.objOrNull("Item", VARIANT(i.toInt())) ?: continue
            walk(root, root.str("StoreID"), 0)
        }
        out
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

    override fun move(ids: List<String>, folderId: String): Int = session { _, ns ->
        val target = folderOf(ns, folderId)
        var moved = 0
        ids.forEach { id ->
            val m = runCatching { itemOf(ns, id) }.getOrNull() ?: return@forEach
            if (runCatching { m.get("Move", target.toVariant()) }.isSuccess) moved++
        }
        moved
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

    /** Today's date in Outlook's filter format (for the self-test). */
    internal fun filterDate(day: LocalDate = LocalDate.now()): String = filterFormat.format(day.atStartOfDay())
}
