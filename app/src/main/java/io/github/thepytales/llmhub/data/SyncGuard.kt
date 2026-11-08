package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Einfacher Sync (AP-12): the data folder lies in a cloud folder (Nextcloud, Google Drive, OneDrive) that is set up on
 * several PCs – like an Obsidian vault. Instead of merging changes, working on two PCs at once is prevented:
 *
 * - every PC that opens the folder keeps a small note in `sync/` (which PC, since when, still alive, which files it
 *   changed) – one file per PC, so the notes themselves never collide;
 * - before a PC loads anything it [check]s the notes: is the folder open elsewhere, or have the changes of the PC used
 *   last not arrived yet (cloud service still busy)?
 * - conflict copies the cloud service made anyway are found ([conflicts]) and both versions are kept ([keepBoth]):
 *   a chat becomes a second chat „… (zweite Fassung)“, every other file is set aside in `sync/konflikte/`.
 *
 * API keys never lie in this folder (Windows: encrypted on the PC, see createSecurePrefs).
 */
object SyncGuard {
    /** Folder of the notes inside the data folder. */
    const val FOLDER = "sync"

    /** Conflict copies set aside, in `sync/konflikte/<date>/<path>`. */
    const val CONFLICTS = "konflikte"
    /** Conflict copies of vault chats, kept encrypted inside the vault (AP-63). */
    const val VAULT_CONFLICTS = "vault/konflikte"

    /** A PC that has not said it is alive for this long counts as closed (crashed, asleep, switched off). */
    const val STALE_MS = 15 * 60_000L

    /** How often a running app renews its note. */
    const val HEARTBEAT_MS = 2 * 60_000L

    /** At most this many changed files are listed in a note (the rest is not checked). */
    private const val MAX_LISTED = 4000

    /** Folders of the data folder that are not synced content: temporary files, the notes, local files in tests. */
    private val SKIPPED = setOf("cache", FOLDER, "local")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /** This PC: shown [pc] name, a random [id] kept on the PC (two PCs may share a name), user and app version. */
    data class Me(val pc: String, val id: String, val user: String = "", val app: String = "")

    /**
     * The note of one PC: open since [openedAt], alive at [aliveAt], [closedAt] when it was closed properly;
     * [background] = only running in the notification area, nothing to do; [changed] = files written since opening
     * (path in the data folder → size).
     */
    @Serializable
    data class Session(
        val pc: String,
        val id: String,
        val user: String = "",
        val app: String = "",
        val openedAt: Long,
        val aliveAt: Long,
        val closedAt: Long? = null,
        val background: Boolean = false,
        val changed: Map<String, Long> = emptyMap(),
    ) {
        /** When this PC was last seen working on the folder. */
        val lastSeen: Long get() = closedAt ?: aliveAt

        fun isOpen(now: Long) = closedAt == null && now - aliveAt < STALE_MS
    }

    /** What speaks against opening the folder now. */
    sealed interface Finding {
        val session: Session

        /** The folder is open on another PC. */
        data class OpenElsewhere(override val session: Session) : Finding

        /** The changes of another PC have not all arrived yet ([missing] paths). */
        data class StillSyncing(override val session: Session, val missing: List<String>) : Finding
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(40).ifBlank { "PC" }

    /** The note of [me] in [data]. */
    fun noteFile(data: File, me: Me) = File(File(data, FOLDER), "geoeffnet-${safe(me.pc)}-${safe(me.id)}.json")

    /** The notes of all PCs that ever opened [data] (broken or half-synced notes are skipped). */
    fun sessions(data: File): List<Session> =
        File(data, FOLDER).listFiles { f -> f.isFile && f.name.startsWith("geoeffnet-") && f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { f -> runCatching { json.decodeFromString(Session.serializer(), f.readText()) }.getOrNull() }
            // A conflict copy of a note is the same PC twice: keep its newest state.
            .groupBy { it.id }.map { (_, list) -> list.maxBy { it.aliveAt } }

    /**
     * What speaks against [me] opening [data] now: another PC that has it open, or a PC that worked on it after this
     * PC's last session began and whose changes are not all here yet. null = fine.
     */
    fun check(data: File, me: Me, now: Long = System.currentTimeMillis()): Finding? {
        val all = sessions(data)
        val mine = all.firstOrNull { it.id == me.id }
        val others = all.filter { it.id != me.id }
        others.filter { it.isOpen(now) }.maxByOrNull { it.aliveAt }?.let { return Finding.OpenElsewhere(it) }
        val since = mine?.openedAt ?: 0L
        for (o in others.sortedByDescending { it.lastSeen }) {
            if (o.lastSeen <= since) continue
            // After a proper close the sizes are final; after a crash only the files themselves are certain.
            val missing = o.changed.filter { (rel, size) ->
                val f = File(data, rel)
                !f.isFile || (o.closedAt != null && f.length() != size)
            }.keys
            if (missing.isNotEmpty()) return Finding.StillSyncing(o, missing.sorted())
        }
        return null
    }

    /**
     * Another PC that opened [data] after this one (opened at [openedAt]) while this one still runs – one of the two
     * should close, so neither overwrites the other's changes.
     */
    fun newcomer(data: File, me: Me, openedAt: Long, now: Long = System.currentTimeMillis()): Session? =
        sessions(data).filter { it.id != me.id && it.isOpen(now) && it.openedAt > openedAt }.maxByOrNull { it.openedAt }

    /** Writes the note of [me]: open since [openedAt], alive now, [closed] when the app ends. */
    fun touch(data: File, me: Me, openedAt: Long, closed: Boolean = false, background: Boolean = false, now: Long = System.currentTimeMillis()) {
        val note = Session(
            pc = me.pc, id = me.id, user = me.user, app = me.app, openedAt = openedAt, aliveAt = now,
            closedAt = if (closed) now else null, background = background && !closed, changed = changedSince(data, openedAt),
        )
        val file = noteFile(data, me)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Session.serializer(), note))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Files of [data] written since [since] (path → size), without temporary files, notes and conflict copies. */
    fun changedSince(data: File, since: Long): Map<String, Long> {
        val root = data.canonicalFile
        return root.walkTopDown()
            .onEnter { dir -> dir == root || dir.parentFile != root || dir.name !in SKIPPED }
            .filter { it.isFile && it.lastModified() >= since - 2000 && !it.name.endsWith(".tmp") && conflictOriginal(it, emptyList()) == null }
            .take(MAX_LISTED)
            .associate { relative(root, it) to it.length() }
    }

    private fun relative(root: File, f: File) = f.canonicalFile.path.removePrefix(root.path).trimStart(File.separatorChar).replace(File.separatorChar, '/')

    // --- Conflict copies -------------------------------------------------------------------------------------------

    /** A conflict copy [file] of [original] (null if the original's name cannot be told). */
    data class Conflict(val file: File, val original: File?, val path: String)

    /** Words cloud services put into the names of conflict copies (Nextcloud, Dropbox, Syncthing, ownCloud, German). */
    private val MARKED = Regex("(?i)conflict|konflikt")
    private val MARKERS = listOf(
        Regex("(?i) \\([^()]*(conflict|konflikt)[^()]*\\)"),
        Regex("(?i)\\.sync-conflict-\\d{8}-\\d{6}(-[A-Z0-9]+)?"),
        Regex("(?i)_conflict-\\d{8}-\\d{6}"),
        Regex("(?i)^\\[?(conflict|konflikt)\\]?[ _-]+"),
    )

    /** Kinds of files the app writes itself (chats, settings, documents, notes). */
    private val APP_FILES = setOf("json", "md", "adeu", "txt", "csv", "jsonl")

    /** „name (1).json“ – Google Drive and others, a conflict only if „name.json“ exists too. */
    private val NUMBERED = Regex("^(.*) \\((\\d{1,2})\\)(\\.[^.]+)?$")

    /**
     * If [f] is a conflict copy, its original (which may be missing for marked names); [pcs] are the names of the PCs
     * (OneDrive appends the computer name: „name-LAPTOP.json“).
     */
    fun conflictOriginal(f: File, pcs: Collection<String>): File? {
        val name = f.name
        val dir = f.parentFile
        if (MARKED.containsMatchIn(name)) {
            val stripped = MARKERS.fold(name) { acc, r -> acc.replace(r, "") }
            return File(dir, if (stripped != name && stripped.isNotBlank()) stripped else name)
        }
        // „(1)“ and the PC name only count for the app's own files: attachments and pictures may be named so on purpose.
        if (f.extension.lowercase() !in APP_FILES) return null
        NUMBERED.matchEntire(name)?.let { m ->
            val original = File(dir, m.groupValues[1] + m.groupValues[3])
            if (original.isFile) return original
        }
        for (pc in pcs.filter { it.isNotBlank() }) {
            val r = Regex("^(.*)-" + Regex.escape(pc) + "(?:-\\d+)?(\\.[^.]+)?$", RegexOption.IGNORE_CASE)
            r.matchEntire(name)?.let { m ->
                val original = File(dir, m.groupValues[1] + m.groupValues[2])
                if (original.isFile) return original
            }
        }
        return null
    }

    /** Conflict copies in [data] (not in the notes folder); [pcs] as in [conflictOriginal]. */
    fun conflicts(data: File, pcs: Collection<String>): List<Conflict> {
        val root = data.canonicalFile
        return root.walkTopDown()
            .onEnter { dir -> dir == root || dir.parentFile != root || dir.name !in SKIPPED }
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .mapNotNull { f -> conflictOriginal(f, pcs)?.let { o -> Conflict(f, o.takeIf { it != f }, relative(root, f)) } }
            // Already set aside for the vault (it makes second chats of them once it is opened, AP-63).
            .filter { !it.path.startsWith(VAULT_CONFLICTS + "/") }
            .toList()
    }

    /** What [keepBoth] did: [chats] conflict copies became chats of their own, [setAside] files went to `sync/konflikte`. */
    data class Kept(val chats: List<String>, val setAside: List<String>) {
        val isEmpty get() = chats.isEmpty() && setAside.isEmpty()
    }

    /**
     * Keeps both versions of every conflict: a chat (conversations/…json) becomes a second chat with a new id and
     * „(zweite Fassung)“ in its title; the copy itself, and every other conflict file, goes to
     * `sync/konflikte/<date>/<path>`, where it can be compared by hand. Nothing is deleted.
     */
    fun keepBoth(data: File, conflicts: List<Conflict>, now: Long = System.currentTimeMillis()): Kept {
        if (conflicts.isEmpty()) return Kept(emptyList(), emptyList())
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(now))
        val aside = File(File(File(data, FOLDER), CONFLICTS), day)
        val chats = mutableListOf<String>()
        val moved = mutableListOf<String>()
        for (c in conflicts) {
            // A vault chat (encrypted): only the vault can read it – it goes to the vault's own conflict folder and
            // becomes a second vault chat when the vault is opened next (VaultStore.adoptConflicts).
            if (c.path.startsWith("vault/chats/") && c.file.extension == "bin") {
                val dest = File(File(data, VAULT_CONFLICTS), c.file.name).let { d -> if (!d.exists()) d else File(d.parentFile, "${now}_${d.name}") }
                dest.parentFile?.mkdirs()
                if (c.file.renameTo(dest) || runCatching { c.file.copyTo(dest); c.file.delete() }.getOrDefault(false)) moved += c.path
                continue
            }
            val isChat = c.path.startsWith("conversations/") && c.path.count { it == '/' } == 1 && c.file.name.contains(".json")
            if (isChat) secondVersion(c.file)?.let { (title, id, text) ->
                File(c.file.parentFile, "$id.json").writeText(text)
                chats += title
            }
            val dest = File(aside, c.path).let { d -> if (!d.exists()) d else File(d.parentFile, "${now}_${d.name}") }
            dest.parentFile?.mkdirs()
            if (c.file.renameTo(dest) || runCatching { c.file.copyTo(dest); c.file.delete() }.getOrDefault(false)) moved += c.path
        }
        return Kept(chats, moved)
    }

    /** The chat in [f] as a second chat: its new title, new id and JSON; null if it is no chat. */
    private fun secondVersion(f: File): Triple<String, String, String>? = runCatching {
        val obj = Json.parseToJsonElement(f.readText()) as JsonObject
        val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Chat" }
        val newTitle = "$title (zweite Fassung)"
        val id = UUID.randomUUID().toString()
        val next = JsonObject(obj + mapOf("id" to JsonPrimitive(id), "title" to JsonPrimitive(newTitle)))
        Triple(newTitle, id, next.toString())
    }.getOrNull()
}
