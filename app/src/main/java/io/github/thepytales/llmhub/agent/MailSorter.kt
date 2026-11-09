package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.ListStore
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookAction
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.Repeat
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.ui.splitThinking
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
 * Lora sortiert selbst (AP-69 Stufen b–d): „Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung und
 * Verwaltung ein.“ The order (time, source folder, target folders) comes from the person's sentence – the rule based
 * parser below, or Lora's planner, which only sees that sentence. The mails themselves are read only by a model on this
 * device (local/LocalLlm): head and beginning of each mail, a short prompt and a compact answer „Nummer|Sicherheit|Grund“.
 * Then one preview for the whole order (folder → count, each mail can be moved to another folder or left out, uncertain
 * ones apart) and one click „Einordnen“. The run goes on in the background with progress, pause and cancel, survives a
 * restart („mail-sortierlaeufe.json“, like the Abläufe of AP-40) and can be undone as a whole – the new ids Outlook gives
 * moved mails are kept for that. Missing folders are created in Outlook and stay on undo. Nothing is deleted or sent:
 * only [OutlookBridge.folders], [OutlookBridge.mailsInRange], [OutlookBridge.createFolder] and
 * [OutlookBridge.moveTracked] are used.
 */

/** A target folder of a sort order: the path the person named („Lehre/WS 2026-27“) and, once known, Outlook's folder. */
@Serializable
data class SortTarget(
    val path: String,
    val folderId: String? = null,
    val folderPath: String = "",
    /** The folder was made by this run (it stays on Rückgängig). */
    val created: Boolean = false,
)

/** One mail of a run: what the model said, what the person changed, where it went. No mail text is stored. */
@Serializable
data class SortItem(
    /** Id of the mail where it is now (after Rückgängig: back in the source). */
    val id: String,
    val subject: String,
    val from: String,
    val address: String = "",
    val received: Long = 0,
    /** Index into [MailSortRun.targets]; -1 = none. */
    val target: Int = -1,
    /** What the model chose (-1: no folder fits). */
    val suggested: Int = -1,
    val confidence: Int = 0,
    val reason: String = "",
    val classified: Boolean = false,
    /** Stays in the source folder (left out by the person, or uncertain). */
    val skip: Boolean = false,
    /** The person chose the folder (a correction for the next run). */
    val changed: Boolean = false,
    /** New id in the target folder after moving ("" when the mailbox did not tell); null = not moved. */
    val movedTo: String? = null,
    /** Moved back by Rückgängig. */
    val back: Boolean = false,
    val error: String = "",
) {
    /** The model was not sure (or found no folder); such mails stay unless the person chooses a folder. */
    val uncertain: Boolean get() = classified && (suggested < 0 || confidence < MailSorter.MIN_CONFIDENCE)

    /** Moved and not yet back. */
    val moved: Boolean get() = movedTo != null && !back
}

/**
 * A sort order as the person said it – also what a scheduled task keeps (AP-69 d). Relative [days] („letzte 7 Tage“)
 * or an absolute [since]/[until]; [source] empty = Posteingang; [targets] are paths with „/“ for subfolders.
 */
@Serializable
data class MailSortSpec(
    val wish: String = "",
    val days: Int? = 7,
    val since: Long? = null,
    val until: Long? = null,
    val rangeLabel: String = "",
    val source: String = "",
    val targets: List<String> = emptyList(),
    /** „ohne Rückfrage“: no preview, uncertain mails stay where they are. */
    val noAsk: Boolean = false,
    /** Lora suggests the folders (the person confirms them first unless [noAsk]). */
    val propose: Boolean = false,
    /** „jeden Montag“: ISO days for a scheduled task, offered after the run. */
    val repeatDays: List<Int> = emptyList(),
)

/** A run of a sort order; stored, so it carries on after a restart. */
@Serializable
data class MailSortRun(
    val id: String = UUID.randomUUID().toString(),
    val spec: MailSortSpec,
    val from: Long,
    val until: Long? = null,
    val state: String = CLASSIFYING,
    /** The state to go back to after PAUSED or FAILED. */
    val resumeState: String? = null,
    val sourceId: String? = null,
    val sourcePath: String = "",
    val targets: List<SortTarget> = emptyList(),
    val items: List<SortItem> = emptyList(),
    /** The mails are listed (and the targets looked up in Outlook). */
    val loaded: Boolean = false,
    val resolved: Boolean = false,
    /** Local model that reads the mails. */
    val model: String = "",
    val message: String = "",
    /** More mails in the time than one run takes. */
    val more: Boolean = false,
    /** The person was asked whether their corrections should be remembered. */
    val examplesAsked: Boolean = false,
    /** Started by a scheduled task (shown at the top of Lora's panel afterwards). */
    val taskId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val classifiedCount: Int get() = items.count { it.classified }
    val toMove: List<SortItem> get() = items.filter { it.classified && !it.skip && it.target >= 0 && it.movedTo == null && it.error.isEmpty() }
    val movedCount: Int get() = items.count { it.moved }
    val running: Boolean get() = state in RUNNING
    val canUndo: Boolean get() = items.any { it.moved } && state in setOf(DONE, CANCELLED, FAILED)

    /** Still needs the person (shown at the top of Lora's panel). */
    val open: Boolean get() = state !in setOf(DONE, UNDONE, CANCELLED)

    companion object {
        /** Lora suggests folders from the subjects. */
        const val PROPOSING = "proposing"

        /** Waits for the person to confirm the suggested folders. */
        const val FOLDERS = "folders"
        const val CLASSIFYING = "classifying"

        /** The preview waits for „Einordnen“. */
        const val REVIEW = "review"
        const val MOVING = "moving"
        const val DONE = "done"
        const val UNDOING = "undoing"
        const val UNDONE = "undone"
        const val PAUSED = "paused"
        const val CANCELLED = "cancelled"
        const val FAILED = "failed"

        val RUNNING = setOf(PROPOSING, CLASSIFYING, MOVING, UNDOING)
    }
}

/** A correction of the person, kept only after asking (AP-39) and only for the model on this device. */
@Serializable
data class MailSortExample(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val from: String,
    val target: String,
    val at: Long = System.currentTimeMillis(),
)

/** The model that reads the mails – always one on this device ([MailSorter.testModel] in tests). */
interface SortModel {
    val id: String
    suspend fun answer(system: String, user: String): String
}

/** Reading the order from the person's sentence (rule based; also the fallback when Lora's planner is not asked). */
object MailSortWish {
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace("ß", "ss")

    private val MAILS = Regex("(e-?mails|\\bmails\\b|posteingang|postfach|nachrichten|\\binbox\\b)")
    private val SORT = Regex("(ordne|einordn|einsortier|sortier|verteil|aufteil|einteil|aufraum|raum\\w* .*\\bauf\\b|ablegen)")

    /** Questions („Wie sortiere ich …?“) and rules are not an order. */
    private val NOT_ORDER = Regex("^(wie|was|warum|wieso|weshalb|wo|wann|welche[rsn]?|gibt es|geht (das|es)|erklar|zeig mir,? wie)\\b|\\bregel")

    /** „Ordne die Mails … in … ein“, „Sortiere meinen Posteingang …“. */
    fun looksLikeSort(wish: String): Boolean {
        val n = norm(wish).trim()
        return MAILS.containsMatchIn(n) && SORT.containsMatchIn(n) && !NOT_ORDER.containsMatchIn(n)
    }

    private val NO_ASK = Regex("(?i),?\\s*(?:ohne\\s+(?:r(?:ü|ue)ck-?\\s?frage[n]?|vorschau|nachfrage[n]?|(?:nach)?zu\\s?fragen|zu\\s+fragen)|frag\\w*\\s+(?:mich\\s+)?nicht(?:\\s+nach)?|ohne\\s+dass\\s+du\\s+fragst)")
    private val REPEAT = Regex("(?i),?\\s*(?:jeden\\s+(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|tag|morgen|abend)|(?:jede\\s+woche|w(?:ö|oe)chentlich)|t(?:ä|ae)glich)")
    private val PROPOSE = Regex("(schlag\\w*\\b.*\\bvor\\b|vorschlag|ordnerstruktur|welche ordner|sinnvolle\\w* ordner|passende\\w* ordner|eigene\\w* ordner|nach themen)")

    fun noAsk(wish: String): Boolean = NO_ASK.containsMatchIn(wish)

    fun propose(wish: String): Boolean = PROPOSE.containsMatchIn(norm(wish))

    private val DAYS = listOf("montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonntag")

    /** „jeden Montag“ → [1]; „täglich“ → Mo–Fr; „wöchentlich“ → Monday. */
    fun repeatDays(wish: String): List<Int> {
        val m = REPEAT.find(wish) ?: return emptyList()
        val day = m.groupValues[1].lowercase()
        return when {
            day in DAYS -> listOf(DAYS.indexOf(day) + 1)
            day.isNotEmpty() || norm(m.value).contains("taglich") -> (1..5).toList()
            else -> listOf(1)
        }
    }

    /** The time of the order. */
    data class Range(val days: Int?, val since: Long?, val until: Long?, val label: String, val default: Boolean = false)

    private val MONTHS = listOf("januar", "februar", "marz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember")
    private val NUMBERS = mapOf(
        "ein" to 1, "eine" to 1, "einen" to 1, "einem" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "funf" to 5, "sechs" to 6,
        "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwolf" to 12, "vierzehn" to 14,
    )
    private const val DATE = "(\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}\\.\\s?\\d{1,2}\\.(?:\\d{2,4})?|\\d{1,2}\\.\\s?(?:januar|februar|marz|april|mai|juni|juli|august|september|oktober|november|dezember)(?:\\s+\\d{4})?)"
    private val SHOW = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** A date the person wrote („1.11.“, „1.11.2026“, „2026-11-01“, „1. November“); without a year the last one that has passed. */
    fun date(text: String, today: LocalDate): LocalDate? {
        val t = norm(text).trim()
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(t)?.let { m -> return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() }
        val m = Regex("^(\\d{1,2})\\.\\s?(?:(\\d{1,2})\\.(\\d{2,4})?|([a-z]+)(?:\\s+(\\d{4}))?)$").find(t) ?: return null
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toIntOrNull() ?: (MONTHS.indexOf(m.groupValues[4]) + 1).takeIf { it > 0 } ?: return null
        val yearText = m.groupValues[3].ifEmpty { m.groupValues[5] }
        val year = yearText.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        val d = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return if (year == null && d.isAfter(today)) d.minusYears(1) else d
    }

    fun range(wish: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Range {
        val n = norm(wish)
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        fun show(d: LocalDate) = SHOW.format(d)
        Regex("(?:vom|von|zwischen)\\s+(?:dem\\s+)?$DATE\\s+(?:bis(?:\\s+zum)?|und|-|–)\\s+(?:zum\\s+)?$DATE").find(n)?.let { m ->
            val a = date(m.groupValues[1], today)
            val b = date(m.groupValues[2], today)
            if (a != null && b != null && !b.isBefore(a)) return Range(null, ms(a), ms(b.plusDays(1)), tr("{0} bis {1}", show(a), show(b)))
        }
        Regex("(?:seit|ab)\\s+(?:dem\\s+)?$DATE").find(n)?.let { m ->
            date(m.groupValues[1], today)?.let { return Range(null, ms(it), null, tr("seit {0}", show(it))) }
        }
        Regex("(?:seit|ab)\\s+(?:letztem\\s+|letzten\\s+|vergangenem\\s+)?(montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)").find(n)?.let { m ->
            val d = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(DAYS.indexOf(m.groupValues[1]) + 1)))
            return Range(null, ms(d), null, tr("seit {0}", show(d)))
        }
        if (Regex("seit gestern").containsMatchIn(n)) return Range(1, null, null, tr("seit gestern"))
        Regex("(?:letzten|vergangenen|letzte|vergangene)\\s+(\\d{1,3}|[a-z]+)\\s+(tag|tage|tagen|woche|wochen|monat|monate|monaten)\\b").find(n)?.let { m ->
            val count = m.groupValues[1].toIntOrNull() ?: NUMBERS[m.groupValues[1]]
            if (count != null) {
                val days = when {
                    m.groupValues[2].startsWith("tag") -> count
                    m.groupValues[2].startsWith("woche") -> count * 7
                    else -> count * 30
                }.coerceIn(0, 3650)
                return Range(days, null, null, tr("letzte {0} Tage", days))
            }
        }
        if (Regex("(?:diese[rnm]?|laufende[rnm]?)\\s+woche").containsMatchIn(n)) {
            val d = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return Range(null, ms(d), null, tr("seit {0}", show(d)))
        }
        if (Regex("(?:letzte[rnm]?|vergangene[rnm]?|vorige[rnm]?)\\s+kalenderwoche").containsMatchIn(n)) {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
            return Range(null, ms(monday), ms(monday.plusWeeks(1)), tr("{0} bis {1}", show(monday), show(monday.plusDays(6))))
        }
        if (Regex("(?:letzte[rnm]?|vergangene[rnm]?|vorige[rnm]?)\\s+woche|\\beine[rn]?\\s+woche").containsMatchIn(n)) return Range(7, null, null, tr("letzte {0} Tage", 7))
        if (Regex("(?:diese[rnm]?|laufende[rnm]?)\\s+monat").containsMatchIn(n)) {
            val d = today.withDayOfMonth(1)
            return Range(null, ms(d), null, tr("seit {0}", show(d)))
        }
        if (Regex("(?:letzte[rnm]?|vergangene[rnm]?|vorige[rnm]?)\\s+monat|\\beine[nm]?\\s+monat").containsMatchIn(n)) return Range(30, null, null, tr("letzte {0} Tage", 30))
        Regex("\\bim\\s+(januar|februar|marz|april|mai|juni|juli|august|september|oktober|november|dezember)(?:\\s+(\\d{4}))?").find(n)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[1]) + 1
            var first = LocalDate.of(m.groupValues[2].toIntOrNull() ?: today.year, month, 1)
            if (m.groupValues[2].isEmpty() && first.isAfter(today)) first = first.minusYears(1)
            val last = first.plusMonths(1).minusDays(1)
            return Range(null, ms(first), ms(first.plusMonths(1)), tr("{0} bis {1}", show(first), show(last)))
        }
        if (Regex("\\bgestern\\b").containsMatchIn(n)) {
            val d = today.minusDays(1)
            return Range(null, ms(d), ms(today), tr("gestern"))
        }
        if (Regex("\\bheute\\b").containsMatchIn(n)) return Range(0, null, null, tr("heute"))
        return Range(7, null, null, tr("letzte {0} Tage", 7), default = true)
    }

    private val SOURCE_NAMED = Regex("(?i)\\b(?:aus(?:\\s+dem|\\s+meinem)?|im|in\\s+dem|in\\s+meinem)\\s+ordner\\s+[„\"']?([^„“\"',;.!?]+?)[“\"']?(?=\\s+(?:in|nach|auf|zu|ein\\w*|der|des|von|vom|seit|ab|und)\\b|[,;.!?]|$)")
    private val SOURCE_PLAIN = Regex("\\b[Aa]us\\s+(?:dem\\s+|meinem\\s+)?([A-ZÄÖÜ„\"][^,;.!?]*?)(?=\\s+(?:in|nach|auf|zu|ein\\w*|der|des|von|vom|seit|ab|und)\\b|[,;.!?]|$)")

    /** The source folder the person named; "" = Posteingang. */
    fun source(wish: String): String {
        val raw = (SOURCE_NAMED.find(wish) ?: SOURCE_PLAIN.find(wish))?.groupValues?.get(1)?.trim()?.trim('„', '“', '"', '\'') ?: return ""
        return if (norm(raw) in setOf("posteingang", "inbox", "postfach", "eingang")) "" else raw
    }

    private val GENERIC = setOf(
        "posteingang", "postfach", "inbox", "ordner", "unterordner", "themen", "kategorien", "passende ordner", "sinnvolle ordner",
        "eigene ordner", "passende", "sinnvolle", "meinen posteingang", "meinem posteingang", "den posteingang", "dem posteingang",
        "verschiedene ordner", "neue ordner", "die ordner", "ordner ein", "sie", "die mails", "mails",
    )

    /** The target folders: „in Lehre, Forschung und Verwaltung ein“, „nach Lehre/WS 2026-27 und Verwaltung“, „Ordner: A, B“. */
    fun targets(wish: String): List<String> {
        val text = wish.replace(NO_ASK, " ").replace(REPEAT, " ")
        Regex("(?i)\\bordner\\s*:\\s*(.+)$").find(text)?.let { m -> split(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { return it } }
        val starts = Regex("(?i)\\s(?:in|nach|auf|zu|unter)\\s+(?!(?:der|dem|den|die)\\s+(?:letzten|vergangenen|vorigen)\\b)").findAll(text).toList()
        for (m in starts.asReversed()) {
            var list = text.substring(m.range.last + 1)
            list = list.replace(Regex("(?is)\\s+(?:ein|ab|einordnen|einsortieren|einteilen|ablegen|verteilen|aufteilen|sortieren|verschieben|zu)\\b.*$"), "")
            list = list.replace(Regex("(?is)\\s+(?:von|vom|seit|ab|aus|bis|der letzten|der vergangenen|des letzten)\\s.*$"), "")
            val found = split(list.trim().trimEnd('.', '!', '?', ',', ';'))
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** Words that do not belong in a folder name („… auf und schlag mir sinnvolle Ordner vor“). */
    private val NOT_A_NAME = setOf("und", "mir", "mich", "bitte", "vor", "schlag", "schlage", "sie", "mails", "mail", "e-mails", "alle", "ordne", "sortiere", "einordnen", "sinnvolle", "passende", "meine", "meinen")

    private fun split(list: String): List<String> = list.split(Regex("\\s*(?:,|;|\\s+und\\s+|\\s+sowie\\s+|\\s+oder\\s+)\\s*"))
        .map(::cleanTarget)
        .filter { t ->
            val words = norm(t).split(Regex("[\\s/]+")).filter { it.isNotEmpty() }
            t.length in 1..60 && norm(t) !in GENERIC && !norm(t).matches(Regex("[\\d .:-]*")) && words.size <= 6 && words.none { it in NOT_A_NAME }
        }
        .distinctBy { norm(it) }

    /** „die Ordner „Lehre““ → „Lehre“; „Lehre > WS“ and „Lehre\WS“ → „Lehre/WS“. */
    fun cleanTarget(raw: String): String = raw.trim().trim('„', '“', '"', '\'', '‚', '‘', '»', '«', '.', ' ')
        .replace(Regex("(?i)^(?:(?:in\\s+)?(?:die|den|dem|meine|meinen|meinem)\\s+)?(?:unter)?ordnern?\\s*"), "")
        .trim().trim('„', '“', '"', '\'', '‚', '‘', '»', '«')
        .replace(Regex("\\s*[>\\\\/]\\s*"), "/").trim('/').trim()

    /** The whole order from a sentence. */
    fun spec(wish: String, today: LocalDate = LocalDate.now()): MailSortSpec {
        val r = range(wish, today)
        val source = source(wish)
        val targets = targets(wish).filter { !it.equals(source, ignoreCase = true) }
        return MailSortSpec(
            wish = wish.trim(), days = r.days, since = r.since, until = r.until, rangeLabel = r.label, source = source, targets = targets,
            noAsk = noAsk(wish), propose = targets.isEmpty() || propose(wish), repeatDays = repeatDays(wish),
        )
    }
}

/** Runs, stores and undoes Lora's sort orders (AP-69 b–d). */
object MailSorter {
    const val ACTION_ID = "mail.sort"

    /** Below this certainty (0–100) a mail counts as uncertain and stays unless the person chooses a folder. */
    const val MIN_CONFIDENCE = 60

    /** Mails per run: about 25 minutes for a model without a graphics card. */
    const val MAX_MAILS = 500
    private const val CHUNK = 10
    private const val PREVIEW_CHARS = 300
    private const val BY = "Lora sortiert"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Head and beginning of the mails of a run while it reads them (never stored). */
    private val headers = ConcurrentHashMap<String, Map<String, MailHeader>>()

    /** Replaces the model on this device in tests. */
    @Volatile
    var testModel: SortModel? = null

    @Volatile
    private var store: Pair<File, ListStore<MailSortRun>>? = null

    @Volatile
    private var exampleStore: Pair<File, ListStore<MailSortExample>>? = null

    private fun store(c: AppContainer): ListStore<MailSortRun> {
        val file = File(c.appContext.filesDir, "mail-sortierlaeufe.json")
        store?.takeIf { it.first == file }?.let { return it.second }
        return synchronized(this) {
            store?.takeIf { it.first == file }?.second
                ?: ListStore(FileBackend(file), MailSortRun.serializer(), { it.id }).also { store = file to it }
        }
    }

    private fun exampleStore(c: AppContainer): ListStore<MailSortExample> {
        val file = File(c.appContext.filesDir, "mail-sortierbeispiele.json")
        exampleStore?.takeIf { it.first == file }?.let { return it.second }
        return synchronized(this) {
            exampleStore?.takeIf { it.first == file }?.second
                ?: ListStore(FileBackend(file), MailSortExample.serializer(), { it.id }).also { exampleStore = file to it }
        }
    }

    fun runs(c: AppContainer): StateFlow<List<MailSortRun>> = store(c).items

    fun get(c: AppContainer, id: String): MailSortRun? = store(c).get(id)

    fun examples(c: AppContainer): StateFlow<List<MailSortExample>> = exampleStore(c).items

    /** Tests: forgets the running jobs and the loaded stores, as after the end of the app (the files stay). */
    internal fun forgetForTest() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        headers.clear()
        store = null
        exampleStore = null
    }

    private fun update(c: AppContainer, id: String, f: (MailSortRun) -> MailSortRun) =
        store(c).update(id) { f(it).copy(updatedAt = System.currentTimeMillis()) }

    /** Changes the state only if the run is still in [from] (a pause or cancel in between wins). */
    private fun move(c: AppContainer, id: String, from: String, to: String, f: (MailSortRun) -> MailSortRun = { it }) =
        update(c, id) { if (it.state == from) f(it).copy(state = to) else it }

    private fun item(c: AppContainer, id: String, itemId: String, f: (SortItem) -> SortItem) =
        update(c, id) { r -> r.copy(items = r.items.map { if (it.id == itemId) f(it) else it }) }

    // ── The model on this device ────────────────────────────────────────────────────────────────────────

    /** Why there is no run without a model on this device (AP-68). */
    val NO_LOCAL = trLater(
        "Mails ordnet nur ein Modell auf diesem Gerät ein – so verlassen sie den PC nicht (Uni-Server und externe Anbieter sind im Postfach gesperrt). " +
            "Unter Einstellungen › Modelle auf diesem Gerät lässt sich eines laden, ohne Grafikkarte z. B. Qwen 3.5 4B oder Gemma 4 E4B.",
    )

    val NO_MAILBOX = trLater("Kein Postfach verbunden: unter Windows das klassische Outlook, sonst Einstellungen › Verbindungen › Mail über den Server.")

    /** The local model for sorting: the one that is loaded, else the strongest installed one that fits this device. */
    fun localModel(c: AppContainer): String? {
        testModel?.let { return it.id }
        // AP-68: the same choice as every other mail task (the loaded model first); sorting also works without tool calls.
        MailAccess.model(c)?.let { return it }
        val installed = c.localLlm.installedModels()
        if (installed.isEmpty()) return null
        c.localLlm.state.value.running?.takeIf { r -> installed.any { it.modelId == r } }?.let { return it }
        val room = c.localLlm.hardware.value?.usableForModel
        return (installed.filter { room == null || it.needGb <= room }.maxByOrNull { it.needGb } ?: installed.minByOrNull { it.needGb })?.modelId
    }

    fun modelName(id: String): String = LocalCatalog.get(id)?.name?.let { tr(it) } ?: id.removePrefix(Privacy.LOCAL_PREFIX)

    private fun model(c: AppContainer): SortModel? {
        testModel?.let { return it }
        val id = localModel(c)?.takeIf { Privacy.isLocal(it) } ?: return null
        return object : SortModel {
            override val id = id
            override suspend fun answer(system: String, user: String): String {
                // Only ever a model on this device: the mails never leave the PC.
                MailAccess.check(id)
                c.localLlm.touch()
                val r = c.llm.streamChat(id, listOf(ChatMessage(role = "system", content = system), ChatMessage(role = "user", content = user)), null, thinking = ThinkingLevel.OFF) {}
                return splitThinking(r.content).second
            }
        }
    }

    private fun bridge(): OutlookBridge = Outlook.bridge ?: throw ActionFailed(tr(NO_MAILBOX))

    /** Checks what a run needs before it starts; the message for the person when something is missing. */
    fun missing(c: AppContainer): String? = when {
        localModel(c) == null -> tr(NO_LOCAL)
        !Outlook.supported -> tr(NO_MAILBOX)
        else -> null
    }

    // ── Prompts and answers (short: models without a graphics card are slow) ────────────────────────────

    internal fun classifierPrompt(targets: List<String>, examples: List<MailSortExample>): String = buildString {
        append("Ordne die E-Mail genau einem Ordner zu.\n")
        targets.forEachIndexed { i, t -> append(i + 1).append(" = ").append(t).append('\n') }
        append("0 = passt nirgends\n")
        val known = examples.filter { e -> targets.any { it.equals(e.target, ignoreCase = true) } }.takeLast(8)
        if (known.isNotEmpty()) {
            append("So hat die Person früher entschieden:\n")
            known.forEach { e -> append("„").append(e.subject.take(70)).append("“ (").append(e.from.take(40)).append(") → ").append(e.target).append('\n') }
        }
        append("Antworte nur mit einer Zeile: Nummer|Sicherheit 0-100|Grund (höchstens 6 Wörter)\nBeispiel: 2|85|Projektantrag an die DFG")
    }

    internal fun mailPrompt(item: SortItem, header: MailHeader?): String = buildString {
        append("Von: ").append(item.from.ifBlank { "unbekannt" })
        if (item.address.isNotBlank() && !item.from.contains(item.address)) append(" <").append(item.address).append('>')
        append("\nBetreff: ").append(item.subject.ifBlank { "(ohne Betreff)" })
        header?.preview?.takeIf { it.isNotBlank() }?.let { append("\nAnfang: ").append(it.replace(Regex("\\s+"), " ").take(PREVIEW_CHARS)) }
    }

    /** „2|85|Projektantrag“ → target index 1, 85, reason; „0|…“ → -1. Null when the answer does not fit the format. */
    internal fun parseAnswer(answer: String, targets: List<String>): Triple<Int, Int, String>? {
        val lines = answer.lines().map { it.trim().trim('*', '`', '-', ' ') }.filter { it.isNotEmpty() }
        for (line in lines) {
            val m = Regex("^(?:(?:nummer|ordner|antwort)\\s*:?\\s*)?(\\d{1,2})\\s*(?:[|;:,/]|\\s-\\s)\\s*(?:sicherheit\\s*:?\\s*)?(\\d{1,3})\\s*%?\\s*(?:(?:[|;:,/]|\\s-\\s)\\s*(.*))?$", RegexOption.IGNORE_CASE).find(line)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                if (n > targets.size) return null
                return Triple(n - 1, m.groupValues[2].toInt().coerceIn(0, 100), m.groupValues[3].trim().replace(Regex("^(?i)grund\\s*:\\s*"), "").trim('"', '„', '“').take(80))
            }
            // A folder name instead of the number.
            val parts = line.split('|').map { it.trim() }
            val i = targets.indexOfFirst { it.equals(parts[0], ignoreCase = true) }
            if (i >= 0 && parts.size >= 2) return Triple(i, parts[1].removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100) ?: 50, parts.getOrElse(2) { "" }.take(80))
        }
        return null
    }

    internal fun proposalPrompt(): String =
        "Schlage 3 bis 6 Ordner vor, in die sich diese E-Mails sinnvoll einordnen lassen (Unterordner mit /, z. B. Lehre/Prüfungen). " +
            "Antworte nur mit den Ordnernamen, einer je Zeile."

    internal fun parseProposal(answer: String): List<String> = answer.lines()
        .map { it.trim().replace(Regex("^(?:[-*•]|\\d+[.)])\\s*"), "").replace("**", "") }
        .map { MailSortWish.cleanTarget(it.substringBefore(" – ").substringBefore(": ")) }
        .filter { it.length in 2..50 && !it.contains("  ") && it.split(' ').size <= 6 && !it.endsWith(":") && it.lowercase() !in setOf("posteingang", "inbox") }
        .distinctBy { it.lowercase() }.take(6)

    // ── Starting ───────────────────────────────────────────────────────────────────────────────────────

    /** Starts [spec] in the background; [ActionFailed] without a model on this device or a mailbox. */
    fun start(c: AppContainer, spec: MailSortSpec, taskId: String? = null, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): MailSortRun {
        missing(c)?.let { throw ActionFailed(it) }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = spec.since ?: today.minusDays((spec.days ?: 7).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val propose = spec.propose || spec.targets.isEmpty()
        val run = MailSortRun(
            spec = spec.copy(propose = propose),
            from = from, until = spec.until,
            state = if (propose) MailSortRun.PROPOSING else MailSortRun.CLASSIFYING,
            targets = spec.targets.map { SortTarget(it) },
            model = localModel(c).orEmpty(),
            taskId = taskId,
        )
        store(c).upsert(run)
        ToolActions.log("$ACTION_ID gestartet (auf diesem Gerät): ${spec.wish.take(80)}")
        launch(c, run.id)
        return run
    }

    private fun launch(c: AppContainer, id: String) {
        if (jobs[id]?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                drive(c, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = (e as? ActionFailed)?.message ?: Outlook.explain(e)
                ToolActions.log("$ACTION_ID fehlgeschlagen: $msg")
                update(c, id) { r -> if (r.state in MailSortRun.RUNNING) r.copy(state = MailSortRun.FAILED, resumeState = r.state, message = msg) else r }
            }
        }
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id, job) }
        job.start()
    }

    private suspend fun drive(c: AppContainer, id: String) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val run = get(c, id) ?: return
            when (run.state) {
                MailSortRun.PROPOSING -> propose(c, run)
                MailSortRun.CLASSIFYING -> classify(c, run)
                MailSortRun.MOVING -> moveAll(c, run)
                MailSortRun.UNDOING -> undoAll(c, run)
                else -> return
            }
        }
    }

    /** Lists the mails of the time and looks up the source and target folders (nothing changes in Outlook). */
    private fun load(c: AppContainer, run: MailSortRun): MailSortRun {
        val b = bridge()
        val folders = b.folders()
        val source = run.spec.source.takeIf { it.isNotBlank() }?.let { q ->
            Outlook.resolveFolder(q, folders) ?: throw ActionFailed(tr("Den Ordner „{0}“ finde ich im Postfach nicht.", q))
        }
        val mails = b.mailsInRange(source?.id, run.from, run.until, MAX_MAILS + 1)
        headers[run.id] = mails.associateBy { it.id }
        val sourceFolder = source
            ?: mails.firstOrNull()?.let { m -> folders.firstOrNull { it.path == m.folder || it.id == m.folder } }
            ?: inbox(folders)
        val items = mails.take(MAX_MAILS).map { m -> SortItem(m.id, m.subject, m.from, m.fromAddress, m.received) }
        update(c, run.id) {
            it.copy(
                loaded = true, items = items, more = mails.size > MAX_MAILS,
                sourceId = sourceFolder?.id, sourcePath = (sourceFolder?.path ?: tr("Posteingang")).trim('\\'),
            )
        }
        return get(c, run.id)!!
    }

    /** The Posteingang among [folders] (first mailbox), for undo when the source was not named. */
    private fun inbox(folders: List<MailFolder>): MailFolder? =
        folders.filter { it.name.lowercase() in setOf("posteingang", "inbox") }.minByOrNull { it.path.count { ch -> ch == '\\' } }

    /** Looks the targets up in Outlook: existing folders get their id, missing ones are made when moving. */
    private fun resolve(c: AppContainer, run: MailSortRun): MailSortRun {
        val folders = bridge().folders()
        update(c, run.id) { r ->
            r.copy(resolved = true, targets = r.targets.map { t ->
                if (t.folderId != null) t else Outlook.resolveFolder(t.path.replace('/', '\\'), folders)?.let { f -> t.copy(folderId = f.id, folderPath = f.path.trim('\\')) } ?: t
            })
        }
        return get(c, run.id)!!
    }

    private suspend fun ask(model: SortModel, system: String, user: String): String = try {
        model.answer(system, user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Once more (the server of the local model may just have started).
        model.answer(system, user)
    }

    private suspend fun propose(c: AppContainer, start: MailSortRun) {
        val run = if (start.loaded) start else load(c, start)
        val model = model(c) ?: throw ActionFailed(tr(NO_LOCAL))
        if (run.items.isEmpty()) {
            move(c, run.id, MailSortRun.PROPOSING, MailSortRun.DONE) { it.copy(message = tr("Im Zeitraum gibt es keine Mails.")) }
            return
        }
        val list = run.items.take(40).joinToString("\n") { "- " + it.subject.take(80) + " (" + it.from.take(30) + ")" }
        val proposal = parseProposal(ask(model, proposalPrompt(), list))
        if (proposal.isEmpty()) throw ActionFailed(tr("Mir fiel keine passende Ordnerstruktur ein – nenne mir die Ordner bitte selbst."))
        ToolActions.log("$ACTION_ID: Ordner vorgeschlagen: ${proposal.joinToString()}")
        val next = if (run.spec.noAsk) MailSortRun.CLASSIFYING else MailSortRun.FOLDERS
        // Folders the person named stay first; Lora's suggestions come after them.
        move(c, run.id, MailSortRun.PROPOSING, next) { r ->
            r.copy(targets = (r.targets.map { it.path } + proposal).distinctBy { it.lowercase() }.map { p -> SortTarget(p) }, resolved = false, model = model.id)
        }
    }

    private suspend fun classify(c: AppContainer, start: MailSortRun) {
        var run = if (start.loaded) start else load(c, start)
        if (!run.resolved) run = resolve(c, run)
        val model = model(c) ?: throw ActionFailed(tr(NO_LOCAL))
        if (run.model != model.id) update(c, run.id) { it.copy(model = model.id) }
        // After a restart the beginnings of the mails are read again (they are never stored).
        val heads = headers[run.id] ?: runCatching { bridge().mailsInRange(run.sourceId, run.from, run.until, MAX_MAILS + 1).associateBy { it.id } }.getOrDefault(emptyMap())
        headers[run.id] = heads
        val paths = run.targets.map { it.path }
        val system = classifierPrompt(paths, exampleStore(c).current)
        for (item in run.items.filter { !it.classified }) {
            currentCoroutineContext().ensureActive()
            if (get(c, run.id)?.state != MailSortRun.CLASSIFYING) return
            val parsed = parseAnswer(ask(model, system, mailPrompt(item, heads[item.id])), paths)
            item(c, run.id, item.id) { it0 ->
                if (it0.changed) it0.copy(classified = true)
                else if (parsed == null) it0.copy(classified = true, suggested = -1, target = -1, confidence = 0, reason = tr("Antwort des Modells unklar"), skip = true)
                else {
                    val (t, sure, why) = parsed
                    val done = it0.copy(classified = true, suggested = t, target = t, confidence = if (t < 0) 0 else sure, reason = why)
                    done.copy(skip = done.uncertain)
                }
            }
        }
        headers.remove(run.id)
        val done = get(c, run.id) ?: return
        if (done.state != MailSortRun.CLASSIFYING) return
        ToolActions.log("$ACTION_ID: ${done.items.size} Mails gelesen (${modelName(model.id)})")
        if (done.items.isEmpty()) {
            move(c, run.id, MailSortRun.CLASSIFYING, MailSortRun.DONE) { it.copy(message = tr("Im Zeitraum gibt es keine Mails.")) }
            return
        }
        if (done.spec.noAsk) move(c, run.id, MailSortRun.CLASSIFYING, MailSortRun.MOVING)
        else {
            move(c, run.id, MailSortRun.CLASSIFYING, MailSortRun.REVIEW)
            notifyIfAway(c, done, tr("Lora: Vorschau fertig"), summary(get(c, run.id)!!))
        }
    }

    /** Makes [path] („Lehre/WS 2026-27“) in Outlook, reusing every part that exists; true when something was made. */
    internal fun ensureFolder(b: OutlookBridge, path: String, folders: MutableList<MailFolder>): Pair<MailFolder, Boolean> {
        val parts = path.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
        require(parts.isNotEmpty()) { "leerer Ordnername" }
        Outlook.resolveFolder(parts.joinToString("\\"), folders)?.let { return it to false }
        var parent: MailFolder? = null
        var have = 0
        for (k in parts.size - 1 downTo 1) {
            val f = Outlook.resolveFolder(parts.take(k).joinToString("\\"), folders)
            if (f != null) { parent = f; have = k; break }
        }
        var made: MailFolder? = null
        for (name in parts.drop(have)) {
            made = b.createFolder(parent?.id, name)
            folders += made
            parent = made
        }
        return made!! to true
    }

    private fun log(c: AppContainer, what: String, detail: String) {
        c.outlookLog.upsert(OutlookAction(what = what, detail = detail.take(1500), by = BY))
        val all = c.outlookLog.current
        if (all.size > OutlookTools.MAX_LOG) c.outlookLog.replaceAll(all.sortedByDescending { it.at }.take(OutlookTools.MAX_LOG))
    }

    private fun subjects(items: List<SortItem>) = items.joinToString("; ") { "„" + it.subject.ifBlank { "(ohne Betreff)" }.take(60) + "“" }

    private suspend fun moveAll(c: AppContainer, start: MailSortRun) {
        val b = bridge()
        val folders = b.folders().toMutableList()
        // The folders the person asked for first, also subfolders (made only now, after „Einordnen“); they stay on undo.
        start.targets.indices.forEach { i ->
            val t = start.targets[i]
            if (t.folderId != null && folders.any { it.id == t.folderId }) return@forEach
            val (f, made) = ensureFolder(b, t.path, folders)
            if (made) log(c, "Ordner", "„${f.path.trim('\\')}“ angelegt")
            update(c, start.id) { r -> r.copy(targets = r.targets.mapIndexed { k, x -> if (k == i) x.copy(folderId = f.id, folderPath = f.path.trim('\\'), created = x.created || made) else x }) }
        }
        val run = get(c, start.id) ?: return
        for ((i, list) in run.toMove.groupBy { it.target }) {
            val t = run.targets[i]
            val folderId = t.folderId ?: continue
            if (folderId == run.sourceId) {
                list.forEach { m -> item(c, run.id, m.id) { it.copy(skip = true) } }
                continue
            }
            for (chunk in list.chunked(CHUNK)) {
                currentCoroutineContext().ensureActive()
                if (get(c, run.id)?.state != MailSortRun.MOVING) return
                val map = withContext(Dispatchers.IO) { b.moveTracked(chunk.map { it.id }, folderId) }
                update(c, run.id) { r ->
                    r.copy(items = r.items.map { m ->
                        if (chunk.none { it.id == m.id }) m
                        else map[m.id]?.let { m.copy(movedTo = it) } ?: m.copy(error = tr("nicht mehr im Ordner"))
                    })
                }
                val moved = chunk.filter { it.id in map }
                if (moved.isNotEmpty()) log(c, "Verschoben", "${moved.size} Mail(s) nach „${t.folderPath.ifBlank { t.path }}“: ${subjects(moved)}")
            }
        }
        val done = get(c, run.id) ?: return
        move(c, run.id, MailSortRun.MOVING, MailSortRun.DONE)
        ToolActions.log("$ACTION_ID ausgeführt: ${done.movedCount} Mails eingeordnet")
        notifyIfAway(c, done, tr("Lora: Mails eingeordnet"), summary(get(c, run.id)!!))
    }

    private suspend fun undoAll(c: AppContainer, run: MailSortRun) {
        val b = bridge()
        val sourceId = run.sourceId ?: throw ActionFailed(tr("Der Ausgangsordner ist nicht bekannt – die Mails liegen in den Zielordnern."))
        for ((i, list) in run.items.filter { it.moved }.groupBy { it.target }) {
            val t = run.targets.getOrNull(i)
            for (chunk in list.chunked(CHUNK)) {
                currentCoroutineContext().ensureActive()
                if (get(c, run.id)?.state != MailSortRun.UNDOING) return
                val known = chunk.filter { !it.movedTo.isNullOrEmpty() }
                val back = HashMap<String, String>()
                if (known.isNotEmpty()) {
                    val map = withContext(Dispatchers.IO) { b.moveTracked(known.map { it.movedTo!! }, sourceId) }
                    known.forEach { m -> map[m.movedTo!!]?.let { back[m.id] = it } }
                }
                // Mails whose new id is unknown (or changed again): found by subject and arrival in the target folder.
                val lost = chunk.filter { it.id !in back }
                if (lost.isNotEmpty() && t?.folderId != null) {
                    val there = runCatching { b.mailsInRange(t.folderId, lost.minOf { it.received } - 120_000, lost.maxOf { it.received } + 120_000, 200) }.getOrDefault(emptyList())
                    lost.forEach { m ->
                        val hit = there.firstOrNull { it.subject == m.subject && it.received == m.received } ?: return@forEach
                        b.moveTracked(listOf(hit.id), sourceId)[hit.id]?.let { back[m.id] = it }
                    }
                }
                update(c, run.id) { r ->
                    r.copy(items = r.items.map { m -> back[m.id]?.let { m.copy(id = it.ifEmpty { m.id }, back = true) } ?: m })
                }
                val moved = chunk.filter { it.id in back }
                if (moved.isNotEmpty()) log(c, "Zurückgeschoben", "${moved.size} Mail(s) aus „${t?.folderPath ?: ""}“ zurück nach „${run.sourcePath}“ (Rückgängig): ${subjects(moved)}")
            }
        }
        val done = get(c, run.id) ?: return
        val left = done.items.count { it.moved }
        move(c, run.id, MailSortRun.UNDOING, MailSortRun.UNDONE) {
            it.copy(message = if (left == 0) "" else tr("{0} Mails ließen sich nicht zurückschieben (in Outlook verschoben oder gelöscht?).", left))
        }
        ToolActions.log("$ACTION_ID rückgängig: ${done.items.count { it.back }} Mails zurück")
    }

    private fun notifyIfAway(c: AppContainer, run: MailSortRun, title: String, text: String) {
        if (System.currentTimeMillis() - run.createdAt < 20_000 && io.github.thepytales.llmhub.ui.LoraHelper.isOpen.value) return
        runCatching { io.github.thepytales.llmhub.tasks.Notifier.batchFinished(c.appContext, title, text) }
    }

    // ── What the person does ───────────────────────────────────────────────────────────────────────────

    /** „Pause“: the run stops after the mail it is at and continues with „Fortsetzen“ (also after a restart). */
    fun pause(c: AppContainer, id: String) {
        jobs.remove(id)?.cancel()
        update(c, id) { r -> if (r.state in MailSortRun.RUNNING) r.copy(state = MailSortRun.PAUSED, resumeState = r.state) else r }
    }

    /** „Fortsetzen“ after a pause, „Erneut versuchen“ after a failure. */
    fun proceed(c: AppContainer, id: String) {
        update(c, id) { r -> if ((r.state == MailSortRun.PAUSED || r.state == MailSortRun.FAILED) && r.resumeState != null) r.copy(state = r.resumeState, resumeState = null, message = "") else r }
        if (get(c, id)?.state in MailSortRun.RUNNING) launch(c, id)
    }

    /** „Abbrechen“: what moved stays moved (Rückgängig is offered), nothing else happens. */
    fun cancel(c: AppContainer, id: String) {
        jobs.remove(id)?.cancel()
        update(c, id) { r -> if (r.state in setOf(MailSortRun.DONE, MailSortRun.UNDONE, MailSortRun.UNDOING)) r else r.copy(state = MailSortRun.CANCELLED, resumeState = null) }
        ToolActions.log("$ACTION_ID abgebrochen")
    }

    /** „Einordnen“: one click for the whole preview. */
    fun confirm(c: AppContainer, id: String) {
        move(c, id, MailSortRun.REVIEW, MailSortRun.MOVING)
        launch(c, id)
    }

    /** Rückgängig for the whole run: every mail back to the source folder; new folders stay. */
    fun undo(c: AppContainer, id: String) {
        update(c, id) { r -> if (r.canUndo) r.copy(state = MailSortRun.UNDOING, resumeState = null) else r }
        if (get(c, id)?.state == MailSortRun.UNDOING) launch(c, id)
    }

    /** In the preview: another folder for one mail ([target] -1 = leave it where it is). */
    fun setTarget(c: AppContainer, id: String, itemId: String, target: Int) = update(c, id) { r ->
        if (r.state != MailSortRun.REVIEW) r
        else r.copy(items = r.items.map { m ->
            if (m.id != itemId) m
            else if (target < 0) m.copy(skip = true)
            else m.copy(target = target, skip = false, changed = target != m.suggested || m.uncertain)
        })
    }

    /** In the preview: leave a mail out (or take it in again). */
    fun setSkip(c: AppContainer, id: String, itemId: String, skip: Boolean) = update(c, id) { r ->
        if (r.state != MailSortRun.REVIEW) r
        else r.copy(items = r.items.map { m -> if (m.id == itemId && (skip || m.target >= 0)) m.copy(skip = skip) else m })
    }

    /** The suggested folders as the person changed them: reading starts. */
    fun acceptFolders(c: AppContainer, id: String, paths: List<String>) {
        val clean = paths.map { MailSortWish.cleanTarget(it) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (clean.isEmpty()) return
        move(c, id, MailSortRun.FOLDERS, MailSortRun.CLASSIFYING) { it.copy(targets = clean.map { p -> SortTarget(p) }, resolved = false, spec = it.spec.copy(targets = clean)) }
        launch(c, id)
    }

    /** Waits until the run needs the person or has ended. */
    suspend fun await(c: AppContainer, id: String): MailSortRun = runs(c).first { l -> l.firstOrNull { it.id == id }?.running != true }.first { it.id == id }

    /** After a start of the app: runs that were working carry on where they stopped; old finished ones go. */
    fun resumeAll(c: AppContainer) {
        val limit = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
        store(c).current.filter { !it.open && it.updatedAt < limit }.forEach { store(c).delete(it.id) }
        store(c).current.filter { it.running }.forEach { r ->
            ToolActions.log("$ACTION_ID nach dem Neustart fortgesetzt")
            launch(c, r.id)
        }
    }

    // ── Summaries ──────────────────────────────────────────────────────────────────────────────────────

    /** Folder → count of what would move (or moved). */
    fun counts(run: MailSortRun): List<Pair<String, Int>> = run.targets.mapIndexed { i, t ->
        t.path to run.items.count { it.target == i && !it.skip && it.classified && (run.state != MailSortRun.DONE || it.moved || it.back) }
    }.filter { it.second > 0 }

    fun summary(run: MailSortRun): String {
        val per = counts(run).joinToString(" · ") { "${it.first} ${it.second}" }
        val unsure = run.items.count { it.uncertain && it.skip && !it.changed }
        return when (run.state) {
            MailSortRun.REVIEW -> tr("{0} Mails gelesen: {1}", run.items.size, per.ifBlank { "–" }) + (if (unsure > 0) " · " + tr("{0} unsicher", unsure) else "")
            MailSortRun.DONE -> if (run.items.isEmpty()) run.message.ifBlank { tr("Im Zeitraum gibt es keine Mails.") }
            else tr("{0} Mails eingeordnet: {1}", run.movedCount, per.ifBlank { "–" }) + (if (unsure > 0) " · " + tr("{0} unsichere bleiben in „{1}“", unsure, run.sourcePath) else "")
            MailSortRun.UNDONE -> tr("Rückgängig: {0} Mails liegen wieder in „{1}“; neu angelegte Ordner bleiben.", run.items.count { it.back }, run.sourcePath) +
                run.message.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            MailSortRun.FAILED -> run.message
            MailSortRun.CANCELLED -> if (run.movedCount > 0) tr("Abgebrochen – {0} Mails sind schon eingeordnet.", run.movedCount) else tr("Abgebrochen – nichts verschoben.")
            else -> tr("{0} von {1} Mails gelesen", run.classifiedCount, run.items.size)
        }
    }

    // ── Stufe d: rules, examples, repeating ───────────────────────────────────────────────────────────

    /** Fixed rules from a run: senders with at least two mails, all of them in the same folder. */
    fun ruleSuggestions(c: AppContainer, run: MailSortRun): List<MailRule> {
        val existing = c.mailRules.current.map { it.from.lowercase() }.toSet()
        return run.items.filter { it.address.isNotBlank() && it.classified && !it.skip && it.target >= 0 }
            .groupBy { it.address.lowercase() }
            .filter { (address, list) -> list.size >= 2 && list.map { it.target }.distinct().size == 1 && address !in existing }
            .filter { (address, _) -> run.items.none { it.address.equals(address, true) && (it.skip || it.target != run.items.first { m -> m.address.equals(address, true) && !m.skip }.target) } }
            .mapNotNull { (address, list) ->
                val t = run.targets.getOrNull(list.first().target) ?: return@mapNotNull null
                val folder = t.folderId ?: return@mapNotNull null
                MailRule(name = list.first().from.ifBlank { address }.take(40), from = address, folder = folder, folderName = t.folderPath.ifBlank { t.path })
            }.take(12)
    }

    fun saveRules(c: AppContainer, rules: List<MailRule>) {
        rules.forEach { r ->
            c.mailRules.upsert(r)
            log(c, "Regel", "„${r.name}“: " + OutlookTools.ruleText(r))
        }
    }

    /** The person's corrections in [run] (mails they gave another folder). */
    fun corrections(run: MailSortRun): List<MailSortExample> = run.items.filter { it.changed && !it.skip && it.target >= 0 }.mapNotNull { m ->
        run.targets.getOrNull(m.target)?.let { MailSortExample(subject = m.subject.take(120), from = m.from.take(60), target = it.path) }
    }

    /** Answer to „Soll ich mir deine Korrekturen merken?“ (AP-39: only after asking). */
    fun rememberCorrections(c: AppContainer, id: String, remember: Boolean) {
        val run = get(c, id) ?: return
        if (remember) {
            corrections(run).forEach { exampleStore(c).upsert(it) }
            val all = exampleStore(c).current
            if (all.size > 40) exampleStore(c).replaceAll(all.sortedByDescending { it.at }.take(40))
            ToolActions.log("$ACTION_ID: ${corrections(run).size} Korrekturen als Beispiele gemerkt")
        }
        update(c, id) { it.copy(examplesAsked = true) }
    }

    fun forgetExamples(c: AppContainer) = exampleStore(c).replaceAll(emptyList())

    /** A scheduled task that repeats [run]'s order (AP-69 d): the last [days] days, on [weekdays] at [hour]. */
    fun scheduledTask(run: MailSortRun, weekdays: List<Int>, hour: Int, noAsk: Boolean): ScheduledTask {
        val days = if (weekdays.size >= 5) 1 else 7
        val targets = run.targets.map { it.path }
        val spec = run.spec.copy(days = days, since = null, until = null, rangeLabel = tr("letzte {0} Tage", days), targets = targets, propose = false, noAsk = noAsk, repeatDays = weekdays)
        return ScheduledTask(
            name = tr("Mails einordnen: {0}", targets.joinToString(", ")).take(80),
            prompt = run.spec.wish.ifBlank { tr("Mails einordnen: {0}", targets.joinToString(", ")) },
            model = run.model,
            repeat = Repeat.DAYS, days = weekdays.sorted(), hour = hour.coerceIn(0, 23), minute = 0,
            webTools = false, documentTools = false,
            mailSort = spec,
        )
    }

    /** Runs the order of a scheduled task: with preview it waits in Lora's panel, „ohne Rückfrage“ it sorts. */
    suspend fun runScheduled(c: AppContainer, task: ScheduledTask): String {
        val spec = task.mailSort ?: throw ActionFailed(tr("Diese Aufgabe hat keinen Sortierauftrag."))
        val run = start(c, spec.copy(propose = false), taskId = task.id)
        val end = await(c, run.id)
        return when (end.state) {
            MailSortRun.REVIEW -> summary(end) + "\n\n" + tr("Die Vorschau wartet in Lora (rechts oben) auf „Einordnen“.")
            MailSortRun.FAILED -> throw ActionFailed(end.message)
            else -> summary(end)
        }
    }

    // ── Lora: the action (AP-38) and the wish ─────────────────────────────────────────────────────────

    private fun JsonObject.yes(key: String) = s(key)?.trim()?.lowercase() in setOf("true", "ja", "yes", "1", "an")

    /** The order from the parameters of the action (Lora's planner or an Ablauf); the sentence fills the gaps. */
    fun specFromArgs(args: JsonObject, wish: String = ""): MailSortSpec {
        val base = MailSortWish.spec(wish)
        val range = args.s("zeitraum")?.takeIf { it.isNotBlank() }?.let { z ->
            z.trim().toIntOrNull()?.let { MailSortWish.Range(it, null, null, tr("letzte {0} Tage", it)) } ?: MailSortWish.range(z)
        }
        val targets = args.s("ziele")?.split(',', ';', '\n')?.map { MailSortWish.cleanTarget(it) }?.filter { it.isNotEmpty() }?.distinctBy { it.lowercase() }.orEmpty()
            .ifEmpty { base.targets }
        return base.copy(
            wish = wish.ifBlank { base.wish.ifBlank { tr("Mails einordnen: {0}", targets.joinToString(", ")) } },
            days = range?.days ?: base.days, since = range?.since ?: base.since, until = range?.until ?: base.until, rangeLabel = range?.label ?: base.rangeLabel,
            source = args.s("quelle")?.takeIf { it.isNotBlank() }?.let { MailSortWish.cleanTarget(it) }?.takeIf { it.lowercase() !in setOf("posteingang", "inbox") } ?: base.source,
            targets = targets,
            noAsk = args.yes("ohne_rueckfrage") || base.noAsk,
            propose = targets.isEmpty() || args.yes("vorschlagen"),
        )
    }

    /** The step line of a run: what, where from, which time, on which model. */
    fun title(spec: MailSortSpec): String =
        if (spec.targets.isEmpty()) tr("Ordnet die Mails ({0}) in passende Ordner ein …", spec.rangeLabel)
        else tr("Ordnet die Mails ({0}) in {1} ein …", spec.rangeLabel, spec.targets.joinToString(", "))

    val ACTIONS: List<ToolAction> = listOf(
        ToolAction(
            ACTION_ID, ActionArea.MAIL, trLater("Mails einordnen"),
            "Ordnet Mails eines Zeitraums mit dem Modell auf diesem Gerät in Ordner ein und legt fehlende Ordner (auch Unterordner mit /) in Outlook an. " +
                "Zeigt eine Vorschau für den ganzen Auftrag, außer ohne_rueckfrage. Löscht und sendet nie; als Ganzes rückgängig.",
            listOf(
                ActionParam("ziele", "Zielordner, mit Komma getrennt, Unterordner mit /, z. B. Lehre/WS 2026-27, Forschung; leer = Lora schlägt Ordner vor", required = false),
                ActionParam("zeitraum", "z. B. letzte 7 Tage, seit 1.11.2026, 1.11.2026 bis 7.11.2026, diese Woche (Standard: letzte 7 Tage)", required = false),
                ActionParam("quelle", "Quellordner (Standard: Posteingang)", required = false),
                ActionParam("ohne_rueckfrage", "ja = ohne Vorschau einordnen; Unsichere bleiben liegen", required = false),
            ),
            risky = false,
            // Mails go only to the model on this device, whatever the project allows (AP-68).
            usesModel = false,
        ) { c, args ->
            val spec = specFromArgs(args)
            missing(c)?.let { throw ActionFailed(it) }
            ActionPlan(ACTION_ID, title(spec) + " " + tr("(läuft auf diesem Gerät)"), risky = false) {
                val run = start(c, spec)
                val end = await(c, run.id)
                if (end.state == MailSortRun.FAILED) throw ActionFailed(end.message)
                ActionResult(
                    summary(end) + if (end.state == MailSortRun.REVIEW || end.state == MailSortRun.FOLDERS) " " + tr("Die Vorschau wartet in Lora auf „Einordnen“.") else "",
                    undo = if (end.canUndo) ({ undo(c, end.id); summary(await(c, end.id)) }) else null,
                )
            }
        },
    )

    /** Lora's planner called act_mail_sort: the run starts, its card shows the preview. Only the sentence went out. */
    fun loraStep(c: AppContainer, args: JsonObject, wish: String): Pair<String, LoraStep?> {
        val spec = specFromArgs(args, wish)
        return try {
            val run = start(c, spec)
            "Läuft auf diesem Gerät: ${title(spec)} Die Karte zeigt Fortschritt und Vorschau." to LoraStep.MailSort(run.id)
        } catch (e: ActionFailed) {
            "FEHLER: ${e.message} Sag das dem Nutzer." to null
        }
    }

    /**
     * „Geh die Mails der letzten Woche durch und ordne sie in Lehre, Forschung und Verwaltung ein“ – read on the device,
     * without asking a model for the order. Null for other wishes.
     */
    fun fromWish(c: AppContainer, wish: String): LoraReply? {
        if (!MailSortWish.looksLikeSort(wish)) return null
        missing(c)?.let { why ->
            val local = io.github.thepytales.llmhub.data.AppMap.byId("area.local")
            return LoraReply(why, listOfNotNull(local?.takeIf { localModel(c) == null }?.let { LoraStep.Navigate(it) }))
        }
        val spec = MailSortWish.spec(wish)
        val run = try {
            start(c, spec)
        } catch (e: ActionFailed) {
            return LoraReply(e.message.orEmpty())
        }
        val text = when {
            spec.targets.isEmpty() -> tr("Ich lese die Betreffzeilen ({0}) und schlage dir Ordner vor – auf diesem Gerät.", spec.rangeLabel)
            spec.noAsk -> tr("Ich ordne die Mails ({0}) ohne Rückfrage in {1} ein – auf diesem Gerät. Unsichere bleiben liegen.", spec.rangeLabel, spec.targets.joinToString(", "))
            else -> tr("Ich lese die Mails ({0}) auf diesem Gerät und zeige dir dann die Vorschau für {1}.", spec.rangeLabel, spec.targets.joinToString(", "))
        }
        return LoraReply(text, listOf(LoraStep.MailSort(run.id)))
    }
}
