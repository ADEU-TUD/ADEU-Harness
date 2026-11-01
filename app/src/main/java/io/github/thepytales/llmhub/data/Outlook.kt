package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/*
 * Mail und Kalender über das klassische Outlook (AP-13). The app talks to the Outlook on this PC (Windows: COM, see
 * desktop/ComOutlook) – no server access, no IT approval. Hard rules, built into [OutlookBridge] itself: it has no way
 * to delete anything and no way to send – answers and invitations end up as drafts the user sends in Outlook.
 */

/** A mail folder of Outlook; [id] is Outlook's own (entry id and store id), [path] e.g. „\\name@tu-dresden.de\Posteingang\Projekte“. */
data class MailFolder(val id: String, val path: String, val name: String, val items: Int = 0, val unread: Int = 0)

/** A mail in a list: sender, subject, time and the beginning of the text. */
data class MailHeader(
    val id: String,
    val folder: String,
    val from: String,
    val fromAddress: String = "",
    val to: String = "",
    val subject: String,
    val received: Long,
    val unread: Boolean = false,
    val important: Boolean = false,
    val flagged: Boolean = false,
    val categories: String = "",
    val preview: String = "",
    val attachments: Int = 0,
    /** „Mail“, „Einladung“ (meeting request) … */
    val kind: String = "Mail",
)

/** A whole mail. */
data class MailBody(val header: MailHeader, val cc: String = "", val body: String, val attachmentNames: List<String> = emptyList())

/** An appointment of the calendar (recurring ones as single occurrences). */
data class CalendarEntry(
    val id: String,
    val subject: String,
    val start: Long,
    val end: Long,
    val location: String = "",
    val allDay: Boolean = false,
    /** 0 frei, 1 mit Vorbehalt, 2 gebucht, 3 abwesend, 4 woanders tätig (Outlook's OlBusyStatus). */
    val busy: Int = 2,
    val organizer: String = "",
    val recurring: Boolean = false,
)

/**
 * Free/busy of one person (Outlook's Recipient.FreeBusy): one character per [slotMinutes] from midnight of [dayStart],
 * '0' frei, '1' mit Vorbehalt, '2' gebucht, '3' abwesend, '4' woanders tätig. [resolved] is false when Outlook does not
 * know the name.
 */
data class BusyTimes(val person: String, val resolved: Boolean, val dayStart: Long, val slotMinutes: Int, val states: String)

/** A draft: an answer to [replyTo] (with [replyAll]) or a new mail. Never sent by the app. */
data class MailDraft(
    val replyTo: String? = null,
    val replyAll: Boolean = false,
    val to: String = "",
    val cc: String = "",
    val subject: String = "",
    val body: String,
    /** Open the draft in Outlook afterwards. */
    val show: Boolean = false,
)

/** A new appointment; with [attendees] a meeting invitation that the user sends in Outlook. */
data class NewEvent(
    val subject: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val location: String = "",
    val body: String = "",
    val reminderMinutes: Int? = 15,
    val attendees: List<String> = emptyList(),
    val show: Boolean = false,
)

/** A new task of Outlook (Aufgaben/To Do). */
data class NewTask(val subject: String, val due: Long? = null, val body: String = "", val reminder: Long? = null)

/** An error the user can act on (Outlook missing, a dialog is open …). */
class OutlookException(message: String) : Exception(message)

/**
 * The classic Outlook on this PC. Implementations only read, move, create folders, save drafts, appointments and tasks
 * – there is deliberately no delete and no send.
 */
interface OutlookBridge {
    /** Connects (starts Outlook in the background if needed); returns the profile's account names. */
    fun connect(): String

    fun folders(): List<MailFolder>

    /** Mails of [folderId] (null: Posteingang), newest first, received after [since]. */
    fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int): List<MailHeader>

    fun mail(id: String): MailBody?

    /** Moves the mails to [folderId]; returns how many moved. */
    fun move(ids: List<String>, folderId: String): Int

    fun createFolder(parentId: String?, name: String): MailFolder

    /** Saves [draft] in „Entwürfe“; returns its id. */
    fun draft(draft: MailDraft): String

    fun events(from: Long, to: Long): List<CalendarEntry>

    fun createEvent(event: NewEvent): String

    fun createTask(task: NewTask): String

    /** Free/busy of [people] (names or addresses; „ich“ is the user) for about a month from midnight of [day]. */
    fun freeBusy(people: List<String>, day: Long, slotMinutes: Int): List<BusyTimes>

    /** Opens a mail, draft or appointment in Outlook. */
    fun show(id: String)
}

/** A fixed rule for sorting (made by the user or confirmed from Lora's suggestion); applied only on request. */
@Serializable
data class MailRule(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    /** Part of the sender's name or address (empty: any). */
    val from: String = "",
    /** Part of the subject (empty: any). */
    val subject: String = "",
    /** Target folder: Outlook's id or path. */
    val folder: String,
    val folderName: String = "",
    val enabled: Boolean = true,
    val moved: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One entry of the Outlook protocol: every change the app made in Outlook. */
@Serializable
data class OutlookAction(
    val id: String = UUID.randomUUID().toString().take(8),
    val at: Long = System.currentTimeMillis(),
    /** „Verschoben“, „Entwurf“, „Termin“, „Aufgabe“, „Ordner“, „Regel“ … */
    val what: String,
    val detail: String,
    /** „Lora im Chat“, „Regel …“, „Sie“. */
    val by: String = "Lora im Chat",
    val chatId: String? = null,
)

object Outlook {
    /** Replaces the PC's Outlook in tests. */
    @Volatile
    var testBridge: OutlookBridge? = null

    /** Mail über den Server (AP-27): set by the app; null while it is not set up or not chosen. */
    @Volatile
    var serverBridge: () -> OutlookBridge? = { null }

    /** The mailbox of this platform: the server if it is chosen (always on Android), else the classic Outlook of the PC. */
    val bridge: OutlookBridge? get() = testBridge ?: serverBridge() ?: OutlookPlatform.bridge

    /** Whether the app can reach a mailbox at all (classic Outlook or the mail server). */
    val supported: Boolean get() = testBridge != null || serverBridge() != null || OutlookPlatform.supported

    /** The mailbox is the server's (no calendar, no Outlook to show things in). */
    val viaServer: Boolean get() = testBridge == null && serverBridge() != null

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace("ß", "ss").trim()

    /**
     * The folder [query] means: Outlook's id, the whole path, the end of a path („Posteingang\Projekte“) or a name that
     * only one folder has. Null when nothing or more than one fits.
     */
    fun resolveFolder(query: String, folders: List<MailFolder>): MailFolder? {
        val q = query.trim().trim('\\', '/')
        if (q.isEmpty()) return null
        folders.firstOrNull { it.id == q }?.let { return it }
        val nq = norm(q).replace('/', '\\')
        folders.firstOrNull { norm(it.path).trim('\\') == nq }?.let { return it }
        folders.filter { norm(it.path).endsWith("\\$nq") }.singleOrNull()?.let { return it }
        return folders.filter { norm(it.name) == nq }.singleOrNull()
    }

    /** Whether [rule] fits the mail (sender and subject parts, ignoring case and accents). */
    fun matches(rule: MailRule, mail: MailHeader): Boolean {
        if (!rule.enabled || (rule.from.isBlank() && rule.subject.isBlank())) return false
        val fromOk = rule.from.isBlank() || norm(mail.from + " " + mail.fromAddress).contains(norm(rule.from))
        val subjectOk = rule.subject.isBlank() || norm(mail.subject).contains(norm(rule.subject))
        return fromOk && subjectOk
    }

    /** Mails that the first fitting rule sends to another folder: mail to rule. */
    fun plan(rules: List<MailRule>, mails: List<MailHeader>): List<Pair<MailHeader, MailRule>> = mails.mapNotNull { m ->
        rules.firstOrNull { r -> matches(r, m) && r.folder != m.folder }?.let { m to it }
    }

    /** Mails whose sender, subject or text contain all words of [query]. */
    fun search(mails: List<MailHeader>, query: String): List<MailHeader> {
        val words = norm(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return mails
        return mails.filter { m ->
            val text = norm(listOf(m.from, m.fromAddress, m.subject, m.preview, m.categories).joinToString(" "))
            words.all { text.contains(it) }
        }
    }

    /**
     * Common free times of everyone in [busy] between [from] and [to]: at least [minutes] long, on working days within
     * [earliest]–[latest] o'clock, starting on full or half hours. Tentative ('1') counts as busy, „woanders tätig“ ('4')
     * as free. People Outlook did not know are left out. At most [perDay] per day and [limit] in all.
     */
    fun freeSlots(
        busy: List<BusyTimes>,
        from: Long,
        to: Long,
        minutes: Int,
        earliest: LocalTime = LocalTime.of(8, 0),
        latest: LocalTime = LocalTime.of(18, 0),
        zone: ZoneId = ZoneId.systemDefault(),
        limit: Int = 8,
        perDay: Int = 3,
    ): List<LongRange> {
        val known = busy.filter { it.resolved && it.states.isNotEmpty() }
        val step = 30L * 60_000
        val length = minutes.coerceAtLeast(5) * 60_000L
        val out = mutableListOf<LongRange>()
        var day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        while (!day.isAfter(lastDay) && out.size < limit) {
            if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) {
                val dayEnd = day.atTime(latest).atZone(zone).toInstant().toEpochMilli()
                val dayBase = day.atTime(earliest).atZone(zone).toInstant().toEpochMilli()
                /** The first start on the half-hour grid of the day at or after [m]. */
                fun grid(m: Long) = if (m <= dayBase) dayBase else m + (step - Math.floorMod(m - dayBase, step)) % step
                var t = grid(from)
                var today = 0
                while (t + length <= dayEnd && t + length <= to && today < perDay && out.size < limit) {
                    if (known.all { free(it, t, t + length) }) {
                        out += t until t + length
                        today++
                        // The next suggestion of the day comes after this one and a half-hour break.
                        t = grid(t + length) + step
                    } else t += step
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    /** Whether [b] is free for the whole time [start] until [end]. */
    fun free(b: BusyTimes, start: Long, end: Long): Boolean {
        val slot = b.slotMinutes * 60_000L
        val first = ((start - b.dayStart) / slot).toInt()
        val last = ((end - 1 - b.dayStart) / slot).toInt()
        if (first < 0 || last >= b.states.length) return false
        return (first..last).all { b.states[it] == '0' || b.states[it] == '4' }
    }

    private val DATE_TIME = listOf("yyyy-MM-dd'T'HH:mm[:ss]", "yyyy-MM-dd HH:mm[:ss]", "d.M.yyyy HH:mm", "d.M.yyyy, HH:mm", "d.M.yyyy 'um' HH:mm", "d.M.yy HH:mm")
        .map { DateTimeFormatter.ofPattern(it) }
    private val DATE = listOf("yyyy-MM-dd", "d.M.yyyy", "d.M.yy").map { DateTimeFormatter.ofPattern(it) }

    /**
     * A time the model or the user wrote („2026-11-03 14:00“, „3.11.2026 14:00“, „2026-11-03“); a date alone is
     * [defaultTime] o'clock. „heute“, „morgen“ and „übermorgen“ work too. Null when it cannot be read.
     */
    fun parseTime(text: String?, zone: ZoneId = ZoneId.systemDefault(), defaultTime: LocalTime = LocalTime.of(9, 0), today: LocalDate = LocalDate.now(zone)): Long? {
        val t = text?.trim()?.removeSuffix(" Uhr")?.replace(Regex("Z$|[+-]\\d\\d:?\\d\\d$"), "")?.trim() ?: return null
        if (t.isEmpty()) return null
        val relative = when (norm(t).substringBefore(' ')) {
            "heute" -> today
            "morgen" -> today.plusDays(1)
            "ubermorgen" -> today.plusDays(2)
            else -> null
        }
        if (relative != null) {
            val time = Regex("(\\d{1,2})[:.](\\d{2})").find(t)?.let { LocalTime.of(it.groupValues[1].toInt().coerceIn(0, 23), it.groupValues[2].toInt().coerceIn(0, 59)) }
            return relative.atTime(time ?: defaultTime).atZone(zone).toInstant().toEpochMilli()
        }
        DATE_TIME.forEach { f -> runCatching { return LocalDateTime.parse(t, f).atZone(zone).toInstant().toEpochMilli() } }
        DATE.forEach { f -> runCatching { return LocalDate.parse(t, f).atTime(defaultTime).atZone(zone).toInstant().toEpochMilli() } }
        return null
    }

    /** Whether [text] is only a date (an all-day appointment). */
    fun dateOnly(text: String?): Boolean = text?.trim()?.let { Regex("^\\d{4}-\\d{2}-\\d{2}$|^\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}$").matches(it) } == true

    private val SHOW = DateTimeFormatter.ofPattern("EE dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
    private val SHOW_DAY = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", java.util.Locale.GERMANY)
    private val SHOW_TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun show(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = SHOW.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „Di 03.11.2026 14:00–15:00“ (or the day alone for all-day appointments). */
    fun span(start: Long, end: Long, allDay: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String {
        val s = Instant.ofEpochMilli(start).atZone(zone)
        val e = Instant.ofEpochMilli(end).atZone(zone)
        return when {
            allDay -> SHOW_DAY.format(s) + if (e.toLocalDate().minusDays(1).isAfter(s.toLocalDate())) " – " + SHOW_DAY.format(e.minusDays(1)) else ""
            s.toLocalDate() == e.toLocalDate() -> SHOW.format(s) + "–" + SHOW_TIME.format(e)
            else -> SHOW.format(s) + " – " + SHOW.format(e)
        }
    }

    /** Explains a problem with Outlook in plain words (for the tools and the screen). */
    fun explain(e: Throwable): String = (e as? OutlookException)?.message ?: ("Outlook: " + (e.message ?: e.javaClass.simpleName))

    /** Short words for Outlook's busy states. */
    fun busyLabel(state: Int): String = when (state) {
        0 -> "frei"
        1 -> "mit Vorbehalt"
        3 -> "abwesend"
        4 -> "woanders tätig"
        else -> "gebucht"
    }
}
