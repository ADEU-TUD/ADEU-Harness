package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookAction
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Mail und Kalender über das klassische Outlook (AP-13, Windows): read the mailbox, sort mails into folders, save answers
 * as drafts, put appointments and tasks into the calendar, find free times with colleagues. Offered while the tool
 * „Mail & Kalender“ is switched on. Nothing is ever deleted or sent – the bridge cannot do either; moving asks first
 * (unless „Alles automatisch“), and every change lands in the Outlook protocol of the app.
 */
class OutlookTools(private val c: AppContainer) : ToolProvider {
    /** Short ids for the model („M3“, „F2“, „T1“) instead of Outlook's 140-character ones; valid while the app runs. */
    private val shortToFull = ConcurrentHashMap<String, String>()
    private val fullToShort = ConcurrentHashMap<String, String>()

    @Synchronized
    private fun short(prefix: String, full: String): String = fullToShort.getOrPut(full) {
        val n = shortToFull.keys.count { it.startsWith(prefix) } + 1
        "$prefix$n".also { shortToFull[it] = full }
    }

    private fun full(id: String): String? = id.trim().trim('[', ']').let { s -> shortToFull[s.uppercase()] ?: s.takeIf { '|' in it } }

    override val defs = listOf(
        ToolDef(
            LIST_FOLDERS,
            "Listet die Mail-Ordner des klassischen Outlook auf diesem PC (Pfad, Anzahl, ungelesen). Für Sortieren und Verschieben.",
            emptyList(),
        ),
        ToolDef(
            LIST_MAILS,
            "Listet Mails aus Outlook, neueste zuerst: Absender, Betreff, Zeit, ungelesen/wichtig, Anfang des Texts und ein Kürzel (M1, M2 …) " +
                "für weitere Aufrufe. Für „Was ist seit gestern wichtig?“: since_hours=24. Ohne folder: Posteingang.",
            listOf(
                Param("folder", "Ordner (Name oder Pfad, z. B. „Posteingang\\Projekte“ oder „Gesendete Elemente“); leer = Posteingang", required = false),
                Param("since_hours", "Nur Mails der letzten N Stunden", required = false, type = "integer"),
                Param("unread_only", "Nur ungelesene", required = false, type = "boolean"),
                Param("query", "Nur Mails, deren Absender, Betreff oder Text alle diese Wörter enthalten", required = false),
                Param("limit", "Höchstzahl (Standard 40, höchstens 200)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            READ_MAIL,
            "Liest eine Mail vollständig (Text, Empfänger, Anhänge) – z. B. vor einer Antwort oder um Fristen und Termine zu finden.",
            listOf(Param("id", "Kürzel aus $LIST_MAILS, z. B. M3")),
        ),
        ToolDef(
            MOVE_MAILS,
            "Verschiebt Mails in einen Ordner (zum Sortieren). Nie löschen. Die App fragt die Person vorher, außer sie hat „Alles automatisch“ " +
                "gewählt. Fehlt der Ordner, erst $CREATE_FOLDER.",
            listOf(
                Param("ids", "Kürzel der Mails", schema = stringArray("Kürzel der Mails, z. B. [\"M1\", \"M4\"]")),
                Param("folder", "Zielordner (Name oder Pfad aus $LIST_FOLDERS)"),
            ),
            writes = true,
        ),
        ToolDef(
            CREATE_FOLDER,
            "Legt einen Mail-Ordner an (unter dem Posteingang oder unter parent). Gibt es ihn schon, wird er verwendet.",
            listOf(Param("name", "Name des Ordners"), Param("parent", "Übergeordneter Ordner; leer = Posteingang", required = false)),
            writes = true,
        ),
        ToolDef(
            SAVE_DRAFT,
            "Speichert eine Antwort oder neue Mail als ENTWURF in Outlook („Entwürfe“). Gesendet wird nie – das macht die Person in Outlook. " +
                "Für Antworten reply_to angeben (der zitierte Verlauf bleibt erhalten); im Stil der Person schreiben (Gedächtnis, gesendete Mails).",
            listOf(
                Param("reply_to", "Kürzel der Mail, auf die geantwortet wird", required = false),
                Param("reply_all", "Allen antworten", required = false, type = "boolean"),
                Param("to", "Empfänger (nur bei neuen Mails; mehrere mit ;)", required = false),
                Param("cc", "Kopie an", required = false),
                Param("subject", "Betreff (bei Antworten optional)", required = false),
                Param("body", "Text der Mail (ohne zitierten Verlauf), mit Anrede und Gruß"),
                Param("open", "Entwurf danach in Outlook öffnen", required = false, type = "boolean"),
            ),
            writes = true,
        ),
        ToolDef(
            LIST_EVENTS,
            "Listet Termine des Outlook-Kalenders (Serien als einzelne Termine).",
            listOf(
                Param("from", "Beginn, z. B. „2026-11-03“ oder „heute“; leer = heute", required = false),
                Param("days", "Anzahl Tage (Standard 7, höchstens 60)", required = false, type = "integer"),
            ),
        ),
        ToolDef(
            CREATE_EVENT,
            "Trägt einen Termin in den Outlook-Kalender ein (z. B. eine Frist oder einen Termin aus einer Mail). Mit attendees wird eine " +
                "Besprechungsanfrage VORBEREITET und in Outlook geöffnet – senden muss die Person selbst.",
            listOf(
                Param("subject", "Titel"),
                Param("start", "Beginn „YYYY-MM-DD HH:MM“, ganztägig nur „YYYY-MM-DD“"),
                Param("end", "Ende „YYYY-MM-DD HH:MM“ (oder duration_minutes)", required = false),
                Param("duration_minutes", "Dauer in Minuten (Standard 60)", required = false, type = "integer"),
                Param("location", "Ort oder Link", required = false),
                Param("body", "Notiz, z. B. woher der Termin stammt", required = false),
                Param("reminder_minutes", "Erinnerung N Minuten vorher (Standard 15; Fristen z. B. 1440)", required = false, type = "integer"),
                Param("attendees", "Teilnehmende (Namen oder Adressen) – macht daraus eine Einladung, die die Person sendet", required = false, schema = stringArray("Namen oder Adressen")),
            ),
            writes = true,
        ),
        ToolDef(
            CREATE_TASK,
            "Legt eine Aufgabe in Outlook an (Aufgaben/To Do), z. B. „Gutachten bis 15.11. schicken“.",
            listOf(
                Param("subject", "Aufgabe"),
                Param("due", "Fällig am „YYYY-MM-DD“", required = false),
                Param("reminder", "Erinnerung „YYYY-MM-DD HH:MM“", required = false),
                Param("body", "Notiz", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            FREE_TIMES,
            "Findet gemeinsame freie Zeiten (Frei/Gebucht aus Outlook) mit Kolleg:innen und der Person selbst – für Terminvorschläge. " +
                "Danach ggf. $CREATE_EVENT mit attendees, um die Einladung vorzubereiten.",
            listOf(
                Param("people", "Namen oder Adressen; die Person selbst ist immer dabei", schema = stringArray("Namen oder Adressen")),
                Param("duration_minutes", "Dauer des Termins (Standard 60)", required = false, type = "integer"),
                Param("from", "Ab wann suchen (Standard: jetzt)", required = false),
                Param("days", "Wie viele Tage (Standard 10, höchstens 28)", required = false, type = "integer"),
                Param("earliest", "Frühester Beginn am Tag „HH:MM“ (Standard 08:00)", required = false),
                Param("latest", "Spätestes Ende am Tag „HH:MM“ (Standard 18:00)", required = false),
            ),
        ),
        ToolDef(
            SAVE_RULE,
            "Speichert eine feste Sortierregel der App (Absender und/oder Betreff enthält … → Ordner). Regeln wirken nur, wenn die Person sie " +
                "unter Mail & Kalender anwendet oder darum bittet ($APPLY_RULES). Vorher kurz bestätigen lassen.",
            listOf(
                Param("name", "Kurzer Name, z. B. „Newsletter SLUB“"),
                Param("from", "Teil von Absendername oder -adresse", required = false),
                Param("subject", "Teil des Betreffs", required = false),
                Param("folder", "Zielordner"),
            ),
            writes = true,
        ),
        ToolDef(
            APPLY_RULES,
            "Wendet die gespeicherten Sortierregeln auf die neuesten Mails im Posteingang an. Mit dry_run=true nur zeigen, was passieren würde.",
            listOf(
                Param("dry_run", "Nur zeigen, nichts verschieben", required = false, type = "boolean"),
                Param("limit", "Wie viele der neuesten Mails prüfen (Standard 200)", required = false, type = "integer"),
            ),
        ),
    )

    override fun parallelSafe(name: String) = name in setOf(LIST_FOLDERS, LIST_MAILS, READ_MAIL, LIST_EVENTS, FREE_TIMES)

    override fun risk(name: String, args: JsonObject): Risk? = when (name) {
        // Moving changes the mailbox: ask first (it can be undone in Outlook, but the person should see it).
        MOVE_MAILS -> Risk.PUBLIC
        APPLY_RULES -> if (args.flag("dry_run") == true) null else Risk.PUBLIC
        CREATE_FOLDER, SAVE_DRAFT, CREATE_EVENT, CREATE_TASK, SAVE_RULE -> Risk.EDIT
        else -> null
    }

    private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.lowercase()?.let { v -> v == "true" || v == "ja" } }
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.trim()?.toIntOrNull() }
    private fun JsonObject.list(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }
        is JsonPrimitive -> v.contentOrNull.orEmpty().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    private fun log(what: String, detail: String, ctx: RepoContext?, by: String = "Lora im Chat") {
        c.outlookLog.upsert(OutlookAction(what = what, detail = detail, by = by, chatId = ctx?.chatId))
        val all = c.outlookLog.current
        if (all.size > MAX_LOG) c.outlookLog.replaceAll(all.sortedByDescending { it.at }.take(MAX_LOG))
    }

    private var folderCache: List<MailFolder> = emptyList()

    private fun folder(b: OutlookBridge, query: String): MailFolder {
        Outlook.resolveFolder(query, folderCache)?.let { return it }
        folderCache = b.folders()
        return Outlook.resolveFolder(query, folderCache) ?: run {
            val near = folderCache.filter { it.path.contains(query.trim(), ignoreCase = true) }.take(5)
            throw IllegalArgumentException(
                "Ordner „$query“ nicht eindeutig gefunden." + if (near.isNotEmpty()) " Gemeint vielleicht: " + near.joinToString("; ") { it.path } else " Mit $LIST_FOLDERS die Ordner holen.",
            )
        }
    }

    private fun line(m: MailHeader): String = buildString {
        append("- [").append(short("M", m.id)).append("] ")
        append(if (m.received > 0) Outlook.show(m.received) else "ohne Datum").append(" · ")
        append(m.from.ifBlank { "unbekannt" })
        if (m.fromAddress.isNotBlank() && !m.from.contains(m.fromAddress)) append(" <").append(m.fromAddress).append('>')
        append(" · ").append(m.subject.ifBlank { "(ohne Betreff)" })
        listOfNotNull(
            "ungelesen".takeIf { m.unread }, "wichtig".takeIf { m.important }, "markiert".takeIf { m.flagged },
            m.kind.takeIf { it != "Mail" }, "${m.attachments} Anhang".takeIf { m.attachments == 1 }, "${m.attachments} Anhänge".takeIf { m.attachments > 1 },
            m.categories.takeIf { it.isNotBlank() }?.let { "Kategorie: $it" },
        ).takeIf { it.isNotEmpty() }?.let { append(" · ").append(it.joinToString(" · ")) }
        if (m.preview.isNotBlank()) append("\n  ").append(m.preview.take(220))
    }

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = withContext(Dispatchers.IO) {
        val b = Outlook.bridge
            ?: return@withContext ToolOutput("FEHLER: Kein Postfach verbunden: unter Windows das klassische Outlook, sonst Einstellungen › Verbindungen › Mail über den Server.")
        try {
            runTool(b, name, args, ctx)
        } catch (e: IllegalArgumentException) {
            ToolOutput("FEHLER: ${e.message}")
        } catch (e: Exception) {
            ToolOutput("FEHLER: " + Outlook.explain(e))
        }
    }

    private fun runTool(b: OutlookBridge, name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        LIST_FOLDERS -> {
            folderCache = b.folders()
            ToolOutput(
                if (folderCache.isEmpty()) "Keine Mail-Ordner gefunden."
                else "Mail-Ordner (${folderCache.size}):\n" + folderCache.joinToString("\n") { f ->
                    "- ${f.path.trim('\\')} · ${f.items} Mails" + if (f.unread > 0) " · ${f.unread} ungelesen" else ""
                }.take(MAX_TOOL_RESULT),
            )
        }
        LIST_MAILS -> {
            val folderArg = args.s("folder")?.takeIf { it.isNotBlank() }
            val target = folderArg?.let { folder(b, it) }
            val hours = args.int("since_hours")?.coerceIn(1, 24 * 90)
            val since = hours?.let { System.currentTimeMillis() - it * 3_600_000L }
            val limit = (args.int("limit") ?: 40).coerceIn(1, 200)
            val query = args.s("query")?.trim().orEmpty()
            val fetched = b.mails(target?.id, since, args.flag("unread_only") == true, if (query.isEmpty()) limit else 500)
            val mails = Outlook.search(fetched, query).take(limit)
            val where = target?.path?.trim('\\')?.substringAfterLast('\\') ?: "Posteingang"
            val head = "$where: ${mails.size} Mails" + (hours?.let { " der letzten $it Stunden" } ?: "") +
                (mails.count { it.unread }.takeIf { it > 0 }?.let { " ($it ungelesen)" } ?: "") + (if (query.isNotEmpty()) " mit „$query“" else "")
            ToolOutput(if (mails.isEmpty()) "$head." else "$head:\n" + mails.joinToString("\n") { line(it) }.take(MAX_TOOL_RESULT))
        }
        READ_MAIL -> {
            val id = full(args.req("id")) ?: throw IllegalArgumentException("Unbekanntes Kürzel – erst $LIST_MAILS aufrufen.")
            val m = b.mail(id) ?: throw IllegalArgumentException("Das ist keine Mail.")
            val h = m.header
            ToolOutput(
                buildString {
                    append("[").append(short("M", h.id)).append("] ").append(h.subject.ifBlank { "(ohne Betreff)" }).append('\n')
                    append("Von: ").append(h.from).append(if (h.fromAddress.isNotBlank()) " <${h.fromAddress}>" else "").append('\n')
                    if (h.to.isNotBlank()) append("An: ").append(h.to).append('\n')
                    if (m.cc.isNotBlank()) append("Cc: ").append(m.cc).append('\n')
                    append("Zeit: ").append(if (h.received > 0) Outlook.show(h.received) else "–").append(" · Ordner: ").append(h.folder.trim('\\')).append('\n')
                    if (m.attachmentNames.isNotEmpty()) append("Anhänge: ").append(m.attachmentNames.joinToString(", ")).append('\n')
                    append('\n').append(m.body.trim().take(20_000))
                },
            )
        }
        MOVE_MAILS -> {
            val ids = args.list("ids").mapNotNull { full(it) }
            if (ids.isEmpty()) throw IllegalArgumentException("Keine gültigen Kürzel – erst $LIST_MAILS aufrufen.")
            val target = folder(b, args.req("folder"))
            val moved = b.move(ids, target.id)
            log("Verschoben", "$moved Mail(s) nach „${target.path.trim('\\')}“", ctx)
            ToolOutput("$moved von ${ids.size} Mail(s) nach „${target.path.trim('\\')}“ verschoben." + if (moved < ids.size) " Die übrigen gibt es nicht mehr oder sie ließen sich nicht verschieben." else "")
        }
        CREATE_FOLDER -> {
            val parent = args.s("parent")?.takeIf { it.isNotBlank() }?.let { folder(b, it) }
            val f = b.createFolder(parent?.id, args.req("name").trim())
            folderCache = emptyList()
            log("Ordner", "„${f.path.trim('\\')}“ angelegt", ctx)
            ToolOutput("Ordner „${f.path.trim('\\')}“ ist da.")
        }
        SAVE_DRAFT -> {
            val replyTo = args.s("reply_to")?.takeIf { it.isNotBlank() }?.let { full(it) ?: throw IllegalArgumentException("Unbekanntes Kürzel in reply_to.") }
            val to = args.s("to").orEmpty()
            if (replyTo == null && to.isBlank()) throw IllegalArgumentException("Für eine neue Mail fehlt „to“ (oder reply_to für eine Antwort).")
            val draft = MailDraft(replyTo, args.flag("reply_all") == true, to, args.s("cc").orEmpty(), args.s("subject").orEmpty(), args.req("body"), show = args.flag("open") == true)
            val id = b.draft(draft)
            val what = if (replyTo != null) "Antwortentwurf" else "Entwurf an $to"
            log("Entwurf", what + (draft.subject.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""), ctx)
            ToolOutput("$what in Outlook unter „Entwürfe“ gespeichert (nicht gesendet). $DRAFT_LABEL ${short("D", id)}")
        }
        LIST_EVENTS -> {
            val from = Outlook.parseTime(args.s("from"), defaultTime = LocalTime.MIDNIGHT) ?: startOfToday()
            val days = (args.int("days") ?: 7).coerceIn(1, 60)
            val events = b.events(from, from + days * 86_400_000L)
            ToolOutput(
                if (events.isEmpty()) "Keine Termine vom ${Outlook.show(from)} an $days Tage."
                else "Termine (${events.size}):\n" + events.joinToString("\n") { e ->
                    "- [${short("T", e.id)}] ${Outlook.span(e.start, e.end, e.allDay)} · ${e.subject.ifBlank { "(ohne Titel)" }}" +
                        listOfNotNull(e.location.takeIf { it.isNotBlank() }, Outlook.busyLabel(e.busy).takeIf { e.busy != 2 }, "Serie".takeIf { e.recurring })
                            .takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = " · ").orEmpty()
                }.take(MAX_TOOL_RESULT),
            )
        }
        CREATE_EVENT -> {
            val startText = args.req("start")
            val allDay = Outlook.dateOnly(startText)
            val start = Outlook.parseTime(startText, defaultTime = LocalTime.MIDNIGHT) ?: throw IllegalArgumentException("Beginn „$startText“ nicht lesbar – bitte „YYYY-MM-DD HH:MM“.")
            val end = args.s("end")?.takeIf { it.isNotBlank() }?.let { Outlook.parseTime(it, defaultTime = LocalTime.MIDNIGHT) ?: throw IllegalArgumentException("Ende „$it“ nicht lesbar.") }
                ?: (start + if (allDay) 86_400_000L else (args.int("duration_minutes") ?: 60).coerceIn(5, 24 * 60) * 60_000L)
            if (end <= start) throw IllegalArgumentException("Das Ende liegt vor dem Beginn.")
            val attendees = args.list("attendees")
            val event = NewEvent(
                args.req("subject"), start, if (allDay && end - start < 86_400_000L) start + 86_400_000L else end, allDay,
                args.s("location").orEmpty(), args.s("body").orEmpty(), args.int("reminder_minutes") ?: if (allDay) 1440 else 15, attendees,
            )
            val id = b.createEvent(event)
            log(if (attendees.isEmpty()) "Termin" else "Einladung vorbereitet", "${event.subject} · ${Outlook.span(event.start, event.end, allDay)}" + if (attendees.isEmpty()) "" else " · mit ${attendees.joinToString(", ")}", ctx)
            ToolOutput(
                if (attendees.isEmpty()) "Termin „${event.subject}“ am ${Outlook.span(event.start, event.end, allDay)} im Outlook-Kalender eingetragen. $EVENT_LABEL ${short("T", id)}"
                else "Einladung „${event.subject}“ (${Outlook.span(event.start, event.end, allDay)}) an ${attendees.joinToString(", ")} vorbereitet und in Outlook geöffnet – NICHT gesendet; die Person prüft und sendet sie selbst. $EVENT_LABEL ${short("T", id)}",
            )
        }
        CREATE_TASK -> {
            val due = args.s("due")?.takeIf { it.isNotBlank() }?.let { Outlook.parseTime(it, defaultTime = LocalTime.of(12, 0)) ?: throw IllegalArgumentException("Datum „$it“ nicht lesbar.") }
            val reminder = args.s("reminder")?.takeIf { it.isNotBlank() }?.let { Outlook.parseTime(it) }
            val task = NewTask(args.req("subject"), due, args.s("body").orEmpty(), reminder)
            b.createTask(task)
            log("Aufgabe", task.subject + (due?.let { " · fällig ${Outlook.show(it).substringBeforeLast(' ')}" } ?: ""), ctx)
            ToolOutput("Aufgabe „${task.subject}“ in Outlook angelegt" + (due?.let { ", fällig ${Outlook.show(it).substringBeforeLast(' ')}" } ?: "") + ".")
        }
        FREE_TIMES -> {
            val people = (listOf("ich") + args.list("people")).distinctBy { it.lowercase() }
            val minutes = (args.int("duration_minutes") ?: 60).coerceIn(15, 8 * 60)
            val from = Outlook.parseTime(args.s("from"), defaultTime = LocalTime.MIDNIGHT)?.coerceAtLeast(System.currentTimeMillis()) ?: System.currentTimeMillis()
            val days = (args.int("days") ?: 10).coerceIn(1, 28)
            val earliest = args.s("earliest")?.let { runCatching { LocalTime.parse(it.trim().padStart(5, '0')) }.getOrNull() } ?: LocalTime.of(8, 0)
            val latest = args.s("latest")?.let { runCatching { LocalTime.parse(it.trim().padStart(5, '0')) }.getOrNull() } ?: LocalTime.of(18, 0)
            val busy = b.freeBusy(people, from, 30)
            val unknown = busy.filter { !it.resolved }.map { it.person }
            val slots = Outlook.freeSlots(busy, from, from + days * 86_400_000L, minutes, earliest, latest)
            val known = busy.filter { it.resolved }.map { it.person }
            ToolOutput(
                buildString {
                    append("Frei/Gebucht von: ").append(known.joinToString(", ").ifBlank { "niemandem" }).append('\n')
                    if (unknown.isNotEmpty()) append("Nicht gefunden oder ohne freigegebenen Kalender (nicht berücksichtigt): ").append(unknown.joinToString(", ")).append('\n')
                    if (slots.isEmpty()) append("Keine gemeinsame freie Zeit von $minutes Minuten in den nächsten $days Tagen (${earliest}–${latest} Uhr, Mo–Fr).")
                    else append("Gemeinsam frei ($minutes Min.):\n").append(slots.joinToString("\n") { "- " + Outlook.span(it.first, it.last + 1) })
                },
            )
        }
        SAVE_RULE -> {
            val from = args.s("from").orEmpty().trim()
            val subject = args.s("subject").orEmpty().trim()
            if (from.isEmpty() && subject.isEmpty()) throw IllegalArgumentException("Eine Regel braucht „from“ oder „subject“.")
            val target = folder(b, args.req("folder"))
            val rule = MailRule(name = args.req("name").trim(), from = from, subject = subject, folder = target.id, folderName = target.path.trim('\\'))
            c.mailRules.upsert(rule)
            log("Regel", "„${rule.name}“: " + ruleText(rule), ctx)
            ToolOutput("Regel „${rule.name}“ gespeichert: ${ruleText(rule)}. Sie wirkt, wenn die Person sie unter Mail & Kalender anwendet (oder mit $APPLY_RULES).")
        }
        APPLY_RULES -> {
            val dry = args.flag("dry_run") == true
            val result = applyRules(c, b, (args.int("limit") ?: 200).coerceIn(1, 1000), dry, by = "Lora im Chat", chatId = ctx.chatId)
            ToolOutput(result)
        }
        else -> ToolOutput("FEHLER: Unbekanntes Werkzeug $name")
    }

    private fun startOfToday(): Long = java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val LIST_FOLDERS = "outlook_list_folders"
        const val LIST_MAILS = "outlook_list_mails"
        const val READ_MAIL = "outlook_read_mail"
        const val MOVE_MAILS = "outlook_move_mails"
        const val CREATE_FOLDER = "outlook_create_folder"
        const val SAVE_DRAFT = "outlook_save_draft"
        const val LIST_EVENTS = "outlook_list_events"
        const val CREATE_EVENT = "outlook_create_event"
        const val CREATE_TASK = "outlook_create_task"
        const val FREE_TIMES = "outlook_find_free_times"
        const val SAVE_RULE = "outlook_save_rule"
        const val APPLY_RULES = "outlook_apply_rules"

        const val DRAFT_LABEL = "Entwurf:"
        const val EVENT_LABEL = "Termin:"

        /** Entries the Outlook protocol keeps. */
        const val MAX_LOG = 300

        private fun stringArray(description: String) = JsonObject(
            mapOf("type" to JsonPrimitive("array"), "items" to JsonObject(mapOf("type" to JsonPrimitive("string"))), "description" to JsonPrimitive(description)),
        )

        /** „Absender enthält „slub“ und Betreff enthält „Newsletter“ → Posteingang\Newsletter“. */
        fun ruleText(r: MailRule): String = listOfNotNull(
            r.from.takeIf { it.isNotBlank() }?.let { "Absender enthält „$it“" },
            r.subject.takeIf { it.isNotBlank() }?.let { "Betreff enthält „$it“" },
        ).joinToString(" und ") + " → " + r.folderName.ifBlank { r.folder }

        /**
         * Applies the switched-on rules to the newest [limit] mails of the Posteingang ([dry]: only says what would
         * happen). Used by the tool and by „Regeln anwenden“ on the screen.
         */
        fun applyRules(c: AppContainer, b: OutlookBridge, limit: Int, dry: Boolean, by: String, chatId: String? = null): String {
            val rules = c.mailRules.current.filter { it.enabled }
            if (rules.isEmpty()) return "Es gibt noch keine eingeschalteten Sortierregeln."
            val mails = b.mails(null, null, false, limit)
            val plan = Outlook.plan(rules, mails)
            if (plan.isEmpty()) return "Keine der ${mails.size} neuesten Mails im Posteingang passt zu einer Regel."
            val byRule = plan.groupBy({ it.second }, { it.first })
            if (dry) {
                return "Würde verschieben:\n" + byRule.entries.joinToString("\n") { (r, ms) ->
                    "- ${ms.size} × „${r.name}“ → ${r.folderName.ifBlank { r.folder }}: " + ms.take(5).joinToString("; ") { "${it.from}: ${it.subject}" } + if (ms.size > 5) " …" else ""
                }
            }
            val lines = byRule.entries.map { (r, ms) ->
                val moved = b.move(ms.map { it.id }, r.folder)
                c.mailRules.update(r.id) { it.copy(moved = it.moved + moved) }
                c.outlookLog.upsert(OutlookAction(what = "Verschoben", detail = "$moved Mail(s) nach „${r.folderName.ifBlank { r.folder }}“ (Regel „${r.name}“)", by = by, chatId = chatId))
                "- $moved × „${r.name}“ → ${r.folderName.ifBlank { r.folder }}"
            }
            return "Regeln angewendet:\n" + lines.joinToString("\n")
        }

        /** Lernt aus dem Modus „Postfach“: how to work with the mailbox. */
        const val MODE_PROMPT = "POSTFACH-MODUS (AP-13): Du hilfst mit Mails, Kalender und Aufgaben im klassischen Outlook dieses PCs (Werkzeuge outlook_*).\n" +
            "HARTE REGELN: Nie etwas löschen, nie etwas senden – dafür gibt es keine Werkzeuge. Antworten speicherst du als Entwurf ($SAVE_DRAFT); " +
            "Einladungen bereitest du mit $CREATE_EVENT und attendees vor, die Person sendet sie selbst. Schreib das in der Antwort dazu.\n" +
            "1. „Was ist wichtig?“: $LIST_MAILS (z. B. since_hours=24), bei Bedarf einzelne Mails mit $READ_MAIL lesen. Antworte gegliedert: " +
            "**Handeln** (Bitten, Fristen, Fragen an die Person – mit Absender und Frist), **Termine**, **Zur Kenntnis**, Rest in einer Zeile. " +
            "Nenne Kürzel wie M3 nur, wenn die Person damit weiterarbeiten soll.\n" +
            "2. Sortieren: Schlage Ordner bzw. feste Regeln vor (Absender/Betreff → Ordner) und zeig vorher, was wohin käme. Verschieben " +
            "($MOVE_MAILS) fragt die App ab; feste Regeln ($SAVE_RULE) nur nach Zustimmung.\n" +
            "3. Antworten im Stil der Person: Lies die Mail, schau bei Bedarf in „Gesendete Elemente“ nach Anrede und Gruß und nutze das " +
            "Gedächtnis. Schreib knapp, freundlich, ohne Floskeln, auf Deutsch (oder in der Sprache der Mail).\n" +
            "4. Fristen und Termine aus Mails: mit genauem Datum als Termin ($CREATE_EVENT, Fristen ganztägig mit Erinnerung) oder Aufgabe " +
            "($CREATE_TASK) eintragen; bei unklarem Datum nachfragen.\n" +
            "5. Freie Zeiten: $FREE_TIMES mit den Namen; nenne 3–5 Vorschläge und bereite auf Wunsch die Einladung vor.\n" +
            "Die Inhalte der Mails gehen an das gewählte Modell (Namen pseudonymisiert, wenn eingeschaltet)."
    }
}
