package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Probelauf (AP-77): „Was würde passieren, wenn …?“ – Lora plans the action as usual but only shows the preview: what
 * would be moved, deleted or overwritten, how many files, what could not be taken back, and a piece of advice („Das
 * betrifft 214 Dateien – erst an einem Ordner ausprobieren?“). Every risky action shows the same notes on its preview.
 */
object DryRun {
    /** „Was würde passieren, wenn …“, „Probelauf: …“, „nur Vorschau: …“, "what would happen if …", "dry run: …". */
    private val ASK = Regex(
        "^(?:was (?:wurde|wuerde|wird|passiert|geschieht|geschahe)(?: (?:passieren|geschehen))?,? (?:wenn|falls)(?: (?:ich|du|lora))?|" +
            "probelauf|trockenlauf|nur (?:als )?vorschau|zeig (?:mir )?(?:nur )?(?:die )?vorschau(?: fur| fuer| von)?|" +
            "what (?:would|will) happen if(?: i| you)?|dry run|preview only)[\\s,:]+(.+)$",
    )

    /** The wish without „Was würde passieren, wenn …“, or null when it is no dry run. */
    fun strip(wish: String): String? {
        val n = AppMap.norm(wish).trim()
        val m = ASK.find(n) ?: return null
        // The rest in the original spelling (names of files and folders keep their case): as many words dropped as the
        // question took in the normalized wish.
        val prefix = n.substring(0, m.groups[1]!!.range.first).split(' ').count { it.isNotBlank() }
        val tokens = wish.trim().split(Regex("\\s+"))
        var used = 0
        var i = 0
        while (i < tokens.size && used < prefix) { used += AppMap.norm(tokens[i]).split(' ').count { it.isNotBlank() }; i++ }
        val rest = tokens.drop(i).joinToString(" ").trim().trimEnd('?', '.', '!', ' ')
        return imperative(rest).takeIf { it.length >= 3 }
    }

    /** „ich alle PDFs verschieben würde“ → „alle PDFs verschieben“: the conditional words go, the wish stays. */
    private fun imperative(rest: String): String = rest
        .replace(Regex("(?i)\\s+(wurde|würde|wuerde|wurdest|würdest|would)$"), "")
        .replace(Regex("(?i)^(ich|du|lora)\\s+"), "")
        .trim()

    /** Notes for the preview of [plan]: how many, what cannot be taken back, a piece of advice. */
    fun notes(plan: ActionPlan, action: ToolAction? = ToolActions.byId(plan.actionId)): List<String> {
        val out = mutableListOf<String>()
        val count = plan.preview.size
        val trash = plan.preview.count { (_, after) -> after == tr("Papierkorb") || after == "Papierkorb" }
        if (count > 0) out += tr("Betrifft {0} Datei(en) bzw. Stellen.", count)
        if (trash > 0) out += tr("{0} davon in den Papierkorb – 30 Tage wiederherstellbar, danach endgültig weg.", trash)
        when {
            plan.irreversible != null -> out += plan.irreversible
            action != null && action.id in IN_TOOL_UNDO -> out += tr("Lässt sich nicht über Lora zurücknehmen – nur im Werkzeug mit Strg+Z, solange es offen ist.")
            action != null && action.risky -> out += tr("Lässt sich danach mit „Rückgängig“ zurücknehmen (bis zum Neustart der App).")
        }
        advice(count)?.let { out += it }
        return out
    }

    /** Advice for many files at once. */
    fun advice(count: Int): String? = when {
        count >= 50 -> tr("Das betrifft {0} Dateien – erst an einem Ordner ausprobieren?", count)
        count >= 20 -> tr("Das sind {0} Dateien – vorher die Liste durchsehen.", count)
        else -> null
    }

    /** Actions whose change only the tool itself takes back (its own Strg+Z), not Lora's „Rückgängig“. */
    val IN_TOOL_UNDO = setOf(
        "pdf.delete_pages", "sheet.clean", "slides.delete", "video.delete_clip", "video.rough_cut", "video.shorten", "video.text_cut",
    )

    // ── „Alles rückgängig seit heute Morgen“ ─────────────────────────────────────────────────────────────

    /** „Alles rückgängig“, „mach alles von heute rückgängig“, „was hast du heute gemacht“, "undo everything". */
    private val UNDO_DAY = Regex(
        "(alles (von heute |seit heute morgen |seit heute fruh |seit heute frueh )?ruckgangig|alles (heute |seit heute morgen )?zurucknehmen|" +
            "ruckgangig seit heute|heutige aktionen|was hast du heute (gemacht|geandert|erledigt)|aktionen von heute|undo (everything|all)( today)?)",
    )

    fun asksUndoDay(wish: String): Boolean = UNDO_DAY.containsMatchIn(AppMap.norm(wish).replace("ue", "u").replace("ae", "a"))
}

/**
 * The actions Lora carried out today (AP-77), newest first, with their „Rückgängig“ while the app runs. Before a restart
 * the undo closures are gone: such lines come from the protocol (AP-38) and say that they can no longer be taken back.
 */
object DayJournal {
    class Entry(val at: LocalDateTime, val actionId: String, val text: String, val result: ActionResult?) {
        @Volatile var undone: String? = null
        val canUndo: Boolean get() = result?.undo != null && undone == null
    }

    private val entries = ArrayDeque<Entry>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** An action of Lora was carried out. */
    fun record(actionId: String, result: ActionResult, at: LocalDateTime = LocalDateTime.now()) = synchronized(entries) {
        entries.addFirst(Entry(at, actionId, result.text, result))
        while (entries.size > 200) entries.removeLast()
        _version.value++
    }

    /** [result] was taken back from its own card. */
    fun markUndone(result: ActionResult, text: String) = synchronized(entries) {
        entries.firstOrNull { it.result === result }?.undone = text
        _version.value++
    }

    /** Today's actions since [since] (default: midnight), newest first, plus protocol lines from before a restart. */
    fun today(since: LocalTime = LocalTime.MIDNIGHT, now: LocalDateTime = LocalDateTime.now(), protocol: List<String> = ActionLog.recent(400)): List<Entry> {
        val start = LocalDate.from(now).atTime(since)
        val live = synchronized(entries) { entries.filter { !it.at.isBefore(start) } }
        val known = live.map { it.actionId + "@" + it.at.withNano(0) }.toSet()
        val earlier = protocol.mapNotNull(::parse).filter { !it.at.isBefore(start) && (it.actionId + "@" + it.at) !in known }
            // Lines of this run are in [live] already (same second); the rest were before a restart.
            .filter { line -> live.none { kotlin.math.abs(java.time.Duration.between(it.at, line.at).seconds) <= 1 && it.actionId == line.actionId } }
        return (live + earlier).sortedByDescending { it.at }
    }

    /** „2026-11-10 09:12:03  files.move ausgeführt: 3 Dateien …“ → an entry without „Rückgängig“. */
    internal fun parse(line: String): Entry? {
        val m = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+([a-z]+\.[a-z_]+) ausgeführt: (.*)$""").find(line) ?: return null
        val at = runCatching { LocalDateTime.parse(m.groupValues[1].replace(' ', 'T')) }.getOrNull() ?: return null
        return Entry(at, m.groupValues[2], m.groupValues[3], null)
    }

    /** Takes back all of [list] that still can be, newest first; returns how many and what could not. */
    suspend fun undoAll(list: List<Entry>): Pair<Int, List<Entry>> {
        var done = 0
        val failed = mutableListOf<Entry>()
        for (e in list.sortedByDescending { it.at }) {
            val undo = e.result?.undo
            if (undo == null || e.undone != null) { if (e.undone == null) failed += e; continue }
            val text = runCatching { undo() }.getOrElse { tr("Rückgängig ging nicht: {0}", io.github.thepytales.llmhub.ui.ErrorHelp.explain(it).text) }
            ToolActions.log("${e.actionId} rückgängig (alles von heute): $text")
            e.undone = text
            done++
        }
        _version.value++
        return done to failed
    }

    /** Takes back one entry. */
    suspend fun undo(e: Entry): String {
        val undo = e.result?.undo ?: return tr("Lässt sich nicht mehr zurücknehmen.")
        val text = runCatching { undo() }.getOrElse { tr("Rückgängig ging nicht: {0}", io.github.thepytales.llmhub.ui.ErrorHelp.explain(it).text) }
        ToolActions.log("${e.actionId} rückgängig: $text")
        e.undone = text
        _version.value++
        return text
    }

    internal fun clearForTest() = synchronized(entries) { entries.clear() }
}
