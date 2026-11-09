package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/*
 * Die Mailansicht (AP-69 Stufe a), without any model: the folder tree, the list in pages grouped by day, the choice of
 * mails and the changes by hand (move, read/unread, folders) with Rückgängig. Plain Kotlin, so the screen and the tests
 * use the same logic. Nothing here deletes or sends.
 */

/** One line of the folder tree: a folder or – without [folder] – a mailbox heading that only groups. */
data class FolderNode(
    val key: String,
    val label: String,
    val depth: Int,
    val folder: MailFolder?,
    val hasChildren: Boolean,
    val expanded: Boolean,
    /** Unread mails of the folder; for a closed node with those of its subfolders. */
    val unread: Int,
)

object MailFolders {
    /** The levels of a folder path typed by the user („Lehre/WS 2026-27“ or „Lehre\WS 2026-27“). */
    fun levels(path: String): List<String> = path.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }

    /** The parts of a folder's path („\\name@tu-dresden.de\Posteingang\Projekte“ → name@…, Posteingang, Projekte). */
    fun segments(path: String): List<String> = path.split('\\').filter { it.isNotEmpty() }

    /** The path without the mailbox (Outlook's „\\name@…“ in front), for texts: „Posteingang\Projekte“. */
    fun shortPath(f: MailFolder): String = segments(f.path).let { s -> if (f.path.startsWith("\\\\") && s.size > 1) s.drop(1) else s }.joinToString("\\").ifEmpty { f.name }

    private val ORDER = listOf("posteingang", "inbox", "entwürfe", "drafts", "gesendete elemente", "gesendet", "sent", "sent items", "archiv", "archive")

    /** Whether [f] is a Posteingang (Outlook's German or English name, or the server's INBOX). */
    fun isInbox(f: MailFolder): Boolean = f.id.equals("INBOX", ignoreCase = true) || f.name.lowercase() in setOf("posteingang", "inbox")

    /**
     * The tree of [folders] (by their paths) as visible lines: the Posteingang first, then Entwürfe, Gesendete Elemente,
     * Archiv, then the rest alphabetically; nodes in [collapsed] (their keys) hide their subfolders. A single mailbox
     * heading without a folder of its own (Outlook's „\\name@tu-dresden.de“) is left out.
     */
    fun tree(folders: List<MailFolder>, collapsed: Set<String> = emptySet()): List<FolderNode> {
        class N(val key: String, val label: String) {
            var folder: MailFolder? = null
            val kids = LinkedHashMap<String, N>()
            fun unreadBelow(): Int = kids.values.sumOf { (it.folder?.unread ?: 0) + it.unreadBelow() }
        }
        val root = N("", "")
        for (f in folders) {
            var n = root
            for (p in segments(f.path).ifEmpty { listOf(f.name) }) {
                val key = if (n.key.isEmpty()) p else n.key + "\\" + p
                n = n.kids.getOrPut(p) { N(key, p) }
            }
            n.folder = f
        }
        val tops = root.kids.values.toList().let { t -> if (t.size == 1 && t[0].folder == null) t[0].kids.values.toList() else t }
        val order = compareBy<N>({ n -> ORDER.indexOf(n.label.lowercase()).let { if (it < 0) ORDER.size else it } }, { it.label.lowercase() })
        val out = ArrayList<FolderNode>(folders.size + 4)
        fun add(n: N, depth: Int) {
            val open = n.key !in collapsed
            val own = n.folder?.unread ?: 0
            out += FolderNode(n.key, n.folder?.name ?: n.label, depth, n.folder, n.kids.isNotEmpty(), open, if (open) own else own + n.unreadBelow())
            if (open) n.kids.values.sortedWith(order).forEach { add(it, depth + 1) }
        }
        tops.sortedWith(order).forEach { add(it, 0) }
        return out
    }
}

/** The day groups of the list, like Outlook: Heute, Gestern, Diese Woche … then month by month. */
object MailGroups {
    val TODAY = trLater("Heute")
    val YESTERDAY = trLater("Gestern")
    val THIS_WEEK = trLater("Diese Woche")
    val LAST_WEEK = trLater("Letzte Woche")
    val THIS_MONTH = trLater("Früher in diesem Monat")
    val LAST_MONTH = trLater("Letzter Monat")
    val UNKNOWN = trLater("Ohne Datum")

    /** The group of a mail received at [received]; fixed groups come as German keys (translate with tr), months as „Oktober 2026“ in [locale]. */
    fun label(received: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.GERMAN): String {
        if (received <= 0) return UNKNOWN
        val d = Instant.ofEpochMilli(received).atZone(zone).toLocalDate()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        return when {
            !d.isBefore(today) -> TODAY
            d == today.minusDays(1) -> YESTERDAY
            !d.isBefore(weekStart) -> THIS_WEEK
            !d.isBefore(weekStart.minusWeeks(1)) -> LAST_WEEK
            !d.isBefore(monthStart) -> THIS_MONTH
            !d.isBefore(monthStart.minusMonths(1)) -> LAST_MONTH
            else -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(d).replaceFirstChar { it.titlecase(locale) }
        }
    }

    /** [mails] (newest first) as runs of the same group, in their order. */
    fun groups(mails: List<MailHeader>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.GERMAN): List<Pair<String, List<MailHeader>>> {
        val out = ArrayList<Pair<String, MutableList<MailHeader>>>()
        for (m in mails) {
            val l = label(m.received, today, zone, locale)
            if (out.isEmpty() || out.last().first != l) out += l to mutableListOf()
            out.last().second += m
        }
        return out
    }
}

/** The mails chosen in the mail view, for Lora's panel (AP-69): ids, how many and subjects – never the text of the mails. */
data class MailSelection(val folder: String, val ids: List<String>, val subjects: List<String>) {
    val count: Int get() = ids.size
}

/** What the mail view shows; one immutable state, so the screen and the tests read the same. */
data class MailViewState(
    val folders: List<MailFolder> = emptyList(),
    val query: MailQuery = MailQuery(),
    val mails: List<MailHeader> = emptyList(),
    /** Matching mails in all (the list may hold fewer yet). */
    val total: Int = 0,
    /** Offset of the next page. */
    val next: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    /** Where a Shift-click range starts. */
    val anchor: String? = null,
    /** The mail last clicked or reached with the arrow keys. */
    val lead: String? = null,
    val canMove: Boolean = true,
) {
    val hasMore: Boolean get() = next < total

    /** The mail for the reading pane: the only chosen one. */
    val open: String? get() = selected.singleOrNull()

    /** The folder shown (null while the Posteingang is not known yet or when all folders are searched). */
    val folder: MailFolder? get() = if (query.allFolders) null else folders.firstOrNull { it.id == query.folderId }

    fun selectedMails(): List<MailHeader> = if (selected.isEmpty()) emptyList() else mails.filter { it.id in selected }
}

/** A change made in the mailbox: the sentence for the notice and its way back ([undo] null: there is none). */
class MailChange(val text: String, val undo: (suspend () -> String)? = null)

/**
 * The logic of the mail view (AP-69a) over one [OutlookBridge]: pages of the list (the next one when the user scrolls
 * near the end), the choice (click, Strg, Umschalt, arrow keys, all), and changes by hand – move, read/unread, rename
 * and create folders – each in the Outlook protocol ([log]) and, except creating, with Rückgängig. Bridge calls run on
 * [io].
 */
class MailBoxModel(
    private val bridge: OutlookBridge,
    private val log: (what: String, detail: String) -> Unit = { _, _ -> },
    private val io: CoroutineContext = Dispatchers.IO,
    val pageSize: Int = 60,
) {
    private val _state = MutableStateFlow(MailViewState())
    val state: StateFlow<MailViewState> = _state.asStateFlow()

    /** A new search makes pages of older ones void. */
    private val generation = AtomicLong()

    private suspend fun <T> call(block: () -> T): T = withContext(io) { block() }

    /** The folders and the first page of the Posteingang. */
    suspend fun start() {
        val inbox = runCatching { call { bridge.inboxId() } }.getOrNull()
        loadFolders()
        val q = _state.value.query
        val folderId = q.folderId ?: inbox ?: _state.value.folders.firstOrNull(MailFolders::isInbox)?.id
        search(q.copy(folderId = folderId))
    }

    /** (Re)reads the folder tree with its unread counts. */
    suspend fun loadFolders() {
        runCatching { call { bridge.folders() } }
            .onSuccess { f -> _state.update { it.copy(folders = f, canMove = runCatching { bridge.canMove() }.getOrDefault(true)) } }
            .onFailure { e -> _state.update { it.copy(error = Outlook.explain(e)) } }
    }

    /** Shows [query] from its first page; the choice is cleared. */
    suspend fun search(query: MailQuery) {
        val gen = generation.incrementAndGet()
        _state.update { it.copy(query = query, mails = emptyList(), total = 0, next = 0, loading = true, error = null, selected = emptySet(), anchor = null, lead = null) }
        fetch(gen, 0)
    }

    /** Opens a folder (the other filters stay). */
    suspend fun openFolder(folder: MailFolder) = search(_state.value.query.copy(folderId = folder.id, allFolders = false))

    /** The next page, if there is one and none is loading. */
    suspend fun loadMore() {
        while (true) {
            val s = _state.value
            if (s.loading || !s.hasMore) return
            if (_state.compareAndSet(s, s.copy(loading = true))) {
                fetch(generation.get(), s.next)
                return
            }
        }
    }

    /** Folders and list anew (after Rückgängig, or the reload button). */
    suspend fun refresh() {
        loadFolders()
        search(_state.value.query)
    }

    private suspend fun fetch(gen: Long, offset: Int) {
        val q = _state.value.query
        val result = runCatching { call { bridge.page(q, offset, pageSize) } }
        if (gen != generation.get()) return
        result.onSuccess { p ->
            _state.update { s ->
                // Stable keys for the list: a mail that a shifted page brings again is shown once.
                val known = s.mails.mapTo(HashSet(s.mails.size * 2)) { it.id }
                s.copy(mails = s.mails + p.mails.filter { known.add(it.id) }, total = p.total, next = p.next, loading = false, error = null)
            }
        }.onFailure { e -> _state.update { it.copy(loading = false, error = Outlook.explain(e)) } }
    }

    // ── Choice ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** A click on mail [id]: alone; with [toggle] (Strg) added or taken out; with [range] (Umschalt) from the anchor to here. */
    fun click(id: String, toggle: Boolean = false, range: Boolean = false) = _state.update { s ->
        val anchor = s.anchor
        when {
            range && anchor != null -> {
                val r = span(s, anchor, id) ?: return@update s.copy(selected = setOf(id), anchor = id, lead = id)
                s.copy(selected = if (toggle) s.selected + r else LinkedHashSet(r), lead = id)
            }
            toggle -> s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id, anchor = id, lead = id)
            else -> s.copy(selected = setOf(id), anchor = id, lead = id)
        }
    }

    /** The check box of a mail: adds it to the choice or takes it out (like Strg-click). */
    fun check(id: String) = click(id, toggle = true)

    private fun span(s: MailViewState, from: String, to: String): List<String>? {
        val a = s.mails.indexOfFirst { it.id == from }
        val b = s.mails.indexOfFirst { it.id == to }
        if (a < 0 || b < 0) return null
        return s.mails.subList(minOf(a, b), maxOf(a, b) + 1).map { it.id }
    }

    /** Arrow keys: the mail [delta] lines away; with [extend] (Umschalt) the choice grows from the anchor. */
    fun step(delta: Int, extend: Boolean = false) = _state.update { s ->
        if (s.mails.isEmpty()) return@update s
        val at = s.mails.indexOfFirst { it.id == s.lead }
        val to = s.mails[if (at < 0) 0 else (at + delta).coerceIn(0, s.mails.lastIndex)].id
        val anchor = s.anchor
        if (extend && anchor != null) s.copy(selected = LinkedHashSet(span(s, anchor, to) ?: listOf(to)), lead = to)
        else s.copy(selected = setOf(to), anchor = to, lead = to)
    }

    /** All mails loaded so far (Strg+A). */
    fun selectAll() = _state.update { s -> s.copy(selected = s.mails.mapTo(LinkedHashSet()) { it.id }, anchor = s.mails.firstOrNull()?.id, lead = s.lead ?: s.mails.firstOrNull()?.id) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet(), anchor = null) }

    /** The choice for Lora's panel, or null when nothing is chosen. */
    fun selection(): MailSelection? {
        val s = _state.value
        val mails = s.selectedMails().ifEmpty { return null }
        val where = if (s.query.allFolders) tr("alle Ordner") else s.folder?.let(MailFolders::shortPath) ?: tr("Posteingang")
        return MailSelection(where, mails.map { it.id }, mails.map { it.subject })
    }

    // ── Changes by hand ─────────────────────────────────────────────────────────────────────────────────────────

    /** The folder a loaded mail lies in (Outlook names it by path, the server by its id). */
    fun folderIdOf(m: MailHeader, s: MailViewState = _state.value): String? =
        s.folders.firstOrNull { it.id == m.folder || it.path == m.folder }?.id ?: s.query.folderId.takeIf { !s.query.allFolders }

    /** Moves the mails [ids] (from the list) into [target]; Rückgängig moves them back where they were. */
    suspend fun move(ids: Collection<String>, target: MailFolder): MailChange {
        val s = _state.value
        val mails = s.mails.filter { it.id in ids }
        val from = mails.associate { it.id to folderIdOf(it, s) }
        val moving = mails.filter { from[it.id] != target.id }
        if (moving.isEmpty()) return MailChange(tr("Die Mails liegen schon in „{0}“.", target.name))
        // Old id → new id of every mail that moved ("" when the mailbox cannot tell the new one).
        val result = call { bridge.moveTracked(moving.map { it.id }, target.id) }
        log("Verschoben", "${result.size} Mail(s) nach „${MailFolders.shortPath(target)}“ (in der Mailansicht)")
        if (s.query.allFolders) search(_state.value.query) else dropMoved(moving.filter { it.id in result }, target, from)
        // New id → the folder it came from.
        val back = result.mapNotNull { (old, new) -> if (new.isEmpty()) null else from[old]?.let { new to it } }
        val undo: (suspend () -> String)? = if (back.isEmpty()) null else suspend {
            var n = 0
            back.groupBy({ it.second }, { it.first }).forEach { (folder, newIds) -> n += call { bridge.moveTracked(newIds, folder) }.size }
            log("Rückgängig", "$n Mail(s) aus „${MailFolders.shortPath(target)}“ zurückgeschoben")
            refresh()
            tr("{0} Mail(s) zurückgeschoben", n)
        }
        return MailChange(tr("{0} Mail(s) nach „{1}“ verschoben", result.size, target.name), undo)
    }

    /** Takes moved mails out of the list and moves their counts from their folders to [target]. */
    private fun dropMoved(gone: List<MailHeader>, target: MailFolder, from: Map<String, String?>) = _state.update { s ->
        val ids = gone.mapTo(HashSet()) { it.id }
        val delta = HashMap<String, Pair<Int, Int>>()
        fun add(id: String?, items: Int, unread: Int) {
            if (id == null) return
            val (i, u) = delta[id] ?: (0 to 0)
            delta[id] = (i + items) to (u + unread)
        }
        gone.forEach { m ->
            val u = if (m.unread) 1 else 0
            add(from[m.id], -1, -u)
            add(target.id, 1, u)
        }
        s.copy(
            mails = s.mails.filter { it.id !in ids },
            total = (s.total - gone.size).coerceAtLeast(0),
            next = (s.next - gone.size).coerceAtLeast(0),
            selected = s.selected - ids,
            anchor = s.anchor?.takeIf { it !in ids },
            lead = s.lead?.takeIf { it !in ids },
            folders = s.folders.map { f -> delta[f.id]?.let { (i, u) -> f.copy(items = (f.items + i).coerceAtLeast(0), unread = (f.unread + u).coerceAtLeast(0)) } ?: f },
        )
    }

    /**
     * Marks the mails [ids] read ([read]) or unread – only those that are not so yet – and Rückgängig sets them back.
     * [quiet]: the reading pane moved on (like in Outlook) – no protocol line.
     */
    suspend fun markRead(ids: Collection<String>, read: Boolean, quiet: Boolean = false): MailChange {
        val changing = _state.value.mails.filter { it.id in ids && it.unread == read }
        if (changing.isEmpty()) return MailChange(if (read) tr("Die Mails sind schon gelesen.") else tr("Die Mails sind schon ungelesen."))
        val list = changing.map { it.id }
        val n = call { bridge.markRead(list, read) }
        applyRead(list, read)
        if (!quiet) log(if (read) "Gelesen" else "Ungelesen", "$n Mail(s) als ${if (read) "gelesen" else "ungelesen"} markiert")
        val undo: suspend () -> String = {
            call { bridge.markRead(list, !read) }
            applyRead(list, !read)
            if (!quiet) log("Rückgängig", "${list.size} Mail(s) wieder als ${if (read) "ungelesen" else "gelesen"} markiert")
            tr("Markierung zurückgenommen")
        }
        return MailChange(if (read) tr("{0} Mail(s) als gelesen markiert", n) else tr("{0} Mail(s) als ungelesen markiert", n), undo)
    }

    private fun applyRead(ids: List<String>, read: Boolean) = _state.update { s ->
        val set = ids.toHashSet()
        val delta = HashMap<String, Int>()
        val mails = s.mails.map { m ->
            if (m.id !in set || m.unread != read) m else {
                folderIdOf(m, s)?.let { f -> delta[f] = (delta[f] ?: 0) + if (read) -1 else 1 }
                m.copy(unread = !read)
            }
        }
        s.copy(mails = mails, folders = s.folders.map { f -> delta[f.id]?.let { d -> f.copy(unread = (f.unread + d).coerceAtLeast(0)) } ?: f })
    }

    /** Renames [folder] to [name]; Rückgängig gives it its old name back. */
    suspend fun renameFolder(folder: MailFolder, name: String): MailChange {
        val clean = name.trim()
        if (clean.isEmpty()) throw OutlookException(tr("Bitte einen Namen für den Ordner angeben."))
        if ('/' in clean || '\\' in clean) throw OutlookException(tr("Ein Ordnername darf kein / und kein \\ enthalten."))
        if (clean == folder.name) return MailChange(tr("Der Ordner heißt schon so."))
        val renamed = call { bridge.renameFolder(folder.id, clean) }
        log("Ordner", "„${MailFolders.shortPath(folder)}“ umbenannt in „${renamed.name}“")
        afterRename(folder, renamed)
        val undo: suspend () -> String = {
            val back = call { bridge.renameFolder(renamed.id, folder.name) }
            log("Rückgängig", "„${renamed.name}“ heißt wieder „${back.name}“")
            afterRename(renamed, back)
            tr("„{0}“ heißt wieder „{1}“", renamed.name, back.name)
        }
        return MailChange(tr("Ordner „{0}“ heißt jetzt „{1}“", folder.name, renamed.name), undo)
    }

    /** After a rename: the tree anew; the list too if it showed the folder or one below it (paths and server ids change). */
    private suspend fun afterRename(old: MailFolder, now: MailFolder) {
        val q = _state.value.query
        val current = _state.value.folders.firstOrNull { it.id == q.folderId }
        loadFolders()
        val inside = current != null && (current.id == old.id || current.path.startsWith(old.path + "\\"))
        if (!inside && !q.allFolders) return
        val newId = when {
            current == null -> q.folderId
            current.id == old.id -> now.id
            else -> _state.value.folders.firstOrNull { it.path == now.path + current.path.removePrefix(old.path) }?.id ?: q.folderId
        }
        search(q.copy(folderId = newId))
    }

    /** Creates [path] („Lehre/WS 2026-27“) below [parent] (null: the mailbox's usual place). Nothing is deleted, so there is no Rückgängig. */
    suspend fun createFolder(parent: MailFolder?, path: String): MailChange {
        if (MailFolders.levels(path).isEmpty()) throw OutlookException(tr("Bitte einen Namen für den Ordner angeben."))
        val made = call { bridge.createFolderPath(parent?.id, path) }
        log("Ordner", "„${MailFolders.shortPath(made)}“ angelegt (in der Mailansicht)")
        loadFolders()
        return MailChange(tr("Ordner „{0}“ angelegt", made.name))
    }

    /** The whole mail for the reading pane. */
    suspend fun body(id: String): MailBody? = call { bridge.mail(id) }

    /** Saves attachment [index] of mail [id] into [dir]. */
    suspend fun saveAttachment(id: String, index: Int, dir: File): File = call { bridge.saveAttachment(id, index, dir) }
}
