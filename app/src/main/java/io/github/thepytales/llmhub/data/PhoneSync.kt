package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.net.DavEntry
import io.github.thepytales.llmhub.net.WebDavClient
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Sync für Android (AP-28): the phone takes part in the simple sync of AP-12 through the Nextcloud. On the PC the
 * Lora-Ordner (visible files plus the app's data in `.lora`) lies in a Nextcloud folder; the phone has no Nextcloud
 * client of its own for the app, so it mirrors that folder over WebDAV:
 *
 * - `<Ordner>/.lora/` ↔ the phone's data: chats (`conversations/`), projects, memory, Kurztexte, own TU Vorlagen,
 *   contacts – the stores that can be read again while the app runs ([SYNCED_FILES], [SYNCED_DIRS]);
 * - `<Ordner>/…` (without `.lora`) ↔ the phone's Lora-Ordner (Hochgeladen, Erstellt, own folders).
 *
 * It keeps the rules of AP-12: one device at a time (the phone writes a note like a PC; while a PC has the folder open,
 * the phone waits), changes in both places are kept both (a chat becomes a „zweite Fassung“, other files go to
 * `sync/konflikte`), and an unusual number of deletions stops the sync instead of emptying a device.
 */
class PhoneSync(
    private val dav: () -> WebDavClient,
    private val data: File,
    private val visible: File,
    private val me: SyncGuard.Me,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    data class Entry(val mtime: Long, val size: Long, val etag: String? = null)

    /** What both sides looked like after the last sync, per key („d:conversations/x.json“, „v:Erstellt/Brief.docx“). */
    @Serializable
    data class State(val root: String = "", val files: Map<String, Entry> = emptyMap(), val lastSync: Long = 0)

    data class Result(
        val down: List<String> = emptyList(),
        val up: List<String> = emptyList(),
        val deletedHere: Int = 0,
        val deletedThere: Int = 0,
        val conflicts: List<String> = emptyList(),
        /** Why nothing was synced (open on a PC, changes still on their way, too many deletions). */
        val waiting: String? = null,
    ) {
        val changedHere: Boolean get() = down.isNotEmpty() || deletedHere > 0 || conflicts.isNotEmpty()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stateFile = File(File(data, "local"), "handy-sync.json")
    private val lock = Mutex()

    fun state(): State = runCatching { AtomicFiles.read(stateFile)?.let { json.decodeFromString(State.serializer(), it) } }.getOrNull() ?: State()

    private fun save(s: State) {
        stateFile.parentFile?.mkdirs()
        AtomicFiles.write(stateFile, json.encodeToString(State.serializer(), s))
    }

    private fun remoteData(root: String) = WebDavClient.join(root, DATA)

    /** The local file of [key]. */
    private fun local(key: String): File = if (key.startsWith("d:")) File(data, key.drop(2)) else File(visible, key.drop(2))

    private fun remote(root: String, key: String): String =
        if (key.startsWith("d:")) WebDavClient.join(remoteData(root), key.drop(2)) else WebDavClient.join(root, key.drop(2))

    /** Local files that take part: the synced stores and the visible Lora-Ordner. */
    private fun localFiles(): Map<String, File> {
        val out = linkedMapOf<String, File>()
        SYNCED_FILES.map { File(data, it) }.filter { it.isFile }.forEach { out["d:" + it.name] = it }
        for (dir in SYNCED_DIRS) {
            File(data, dir).walkTopDown().filter { it.isFile && wanted(it.name, it.length()) }.forEach { f ->
                out["d:" + f.relativeTo(data).path.replace(File.separatorChar, '/')] = f
            }
        }
        if (visible.isDirectory) visible.walkTopDown().onEnter { d -> d == visible || !d.name.startsWith(".") }
            .filter { it.isFile && wanted(it.name, it.length()) }
            .forEach { f -> out["v:" + f.relativeTo(visible).path.replace(File.separatorChar, '/')] = f }
        return out
    }

    private fun wanted(name: String, size: Long) = !name.startsWith(".") && !name.startsWith("~$") && !name.endsWith(".tmp") && size <= MAX_BYTES &&
        SyncGuard.conflictOriginal(File(name), emptyList()) == null

    /** Files in the Nextcloud folder that take part (same keys as [localFiles]). */
    private suspend fun remoteFiles(root: String): Map<String, DavEntry> {
        val d = dav()
        val out = linkedMapOf<String, DavEntry>()
        val dataRoot = remoteData(root)
        d.list(dataRoot).filter { !it.folder && it.name in SYNCED_FILES }.forEach { out["d:" + it.name] = it }
        for (dir in SYNCED_DIRS) {
            d.walk(WebDavClient.join(dataRoot, dir)).filter { wanted(it.name, it.size) }.forEach { e ->
                out["d:" + e.path.removePrefix(dataRoot).trimStart('/')] = e
            }
        }
        d.walk(root, skip = { it.name.startsWith(".") }).filter { wanted(it.name, it.size) }.forEach { e ->
            out["v:" + e.path.removePrefix(WebDavClient.normalize(root)).trimStart('/')] = e
        }
        return out
    }

    /** The other devices' notes in the folder (`.lora/sync/geoeffnet-*.json`). */
    private suspend fun notes(root: String): List<SyncGuard.Session> {
        val d = dav()
        val folder = WebDavClient.join(remoteData(root), SyncGuard.FOLDER)
        val files = runCatching { d.list(folder) }.getOrElse { emptyList() }.filter { !it.folder && it.name.startsWith("geoeffnet-") && it.name.endsWith(".json") }
        return files.mapNotNull { f -> runCatching { json.decodeFromString(SyncGuard.Session.serializer(), String(d.download(f.path, 1_000_000))) }.getOrNull() }
            .filter { it.id != me.id }.groupBy { it.id }.map { (_, l) -> l.maxBy { it.aliveAt } }
    }

    /** Whether the folder in [root] is a Lora-Ordner of a PC (has `.lora`). */
    suspend fun looksLikeLoraFolder(root: String): Boolean = runCatching { dav().list(root).any { it.folder && it.name == DATA } }.getOrDefault(false)

    /**
     * One sync with the Nextcloud folder [root]. [open]: the app is in use (the note says „open“, so a PC waits);
     * false when it goes to the background (the note says „closed“, a PC may open the folder).
     */
    suspend fun run(root: String, open: Boolean = true, sessionStart: Long = clock()): Result = lock.withLock {
        val now = clock()
        val others = notes(root)
        others.filter { it.isOpen(now) && !it.background }.maxByOrNull { it.aliveAt }?.let { o ->
            return@withLock Result(waiting = tr("Der Ordner ist gerade am PC „{0}“ geöffnet – der Sync wartet, bis er dort geschlossen ist.", o.pc))
        }
        var state = state().let { if (it.root != WebDavClient.normalize(root)) State(root = WebDavClient.normalize(root)) else it }
        val remote = remoteFiles(root)
        // AP-12: the PC that worked last must have finished uploading its changes.
        for (o in others.sortedByDescending { it.lastSeen }) {
            if (o.lastSeen <= state.lastSync) continue
            val missing = o.changed.filter { (rel, size) ->
                val key = "d:$rel"
                (rel in SYNCED_FILES || SYNCED_DIRS.any { rel.startsWith("$it/") }) && (remote[key] == null || (o.closedAt != null && remote[key]!!.size != size))
            }
            if (missing.isNotEmpty()) return@withLock Result(waiting = tr("Am PC „{0}“ sind noch {1} Änderung(en) unterwegs – der Sync wartet auf die Nextcloud.", o.pc, missing.size))
        }
        val locals = localFiles()
        val keys = (locals.keys + remote.keys + state.files.keys).toSortedSet()
        val plan = keys.map { k -> k to decide(locals[k], remote[k], state.files[k]) }
        val deletions = plan.count { it.second == Action.DELETE_HERE || it.second == Action.DELETE_THERE }
        if (deletions > maxOf(20, state.files.size * 3 / 10) && state.files.isNotEmpty()) {
            return@withLock Result(waiting = tr("Der Sync würde {0} Dateien löschen – das ist ungewöhnlich viel, deshalb ist er angehalten. Bitte am PC prüfen.", deletions))
        }
        val d = dav()
        val dirs = mutableSetOf<String>()
        suspend fun put(key: String, file: File) {
            val target = remote(root, key)
            val parent = WebDavClient.parent(target)
            if (dirs.add(parent)) d.mkdirs(parent)
            d.put(target, file.readBytes())
        }
        val files = state.files.toMutableMap()
        val down = mutableListOf<String>()
        val up = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        var deletedHere = 0
        var deletedThere = 0
        for ((key, action) in plan) {
            val f = local(key)
            when (action) {
                Action.NONE -> Unit
                Action.FORGET -> files.remove(key)
                Action.UPLOAD -> put(key, f)
                Action.DOWNLOAD, Action.CONFLICT -> {
                    val bytes = d.download(remote(root, key), MAX_BYTES)
                    if (action == Action.CONFLICT && f.isFile && !f.readBytes().contentEquals(bytes)) {
                        // Both changed: the phone's version is kept as a conflict copy (AP-12 makes a second chat of it).
                        val copy = File(f.parentFile, f.nameWithoutExtension + " (Konflikt Handy)" + if (f.extension.isEmpty()) "" else ".${f.extension}")
                        f.copyTo(copy, overwrite = true)
                        conflicts += key
                    }
                    f.parentFile?.mkdirs()
                    AtomicFiles.writeBytes(f, bytes)
                    remote[key]?.modified?.takeIf { it > 0 }?.let { f.setLastModified(it) }
                    down += key
                }
                Action.DELETE_HERE -> { f.delete(); files.remove(key); deletedHere++ }
                Action.DELETE_THERE -> { d.delete(remote(root, key)); files.remove(key); deletedThere++ }
            }
            if (action == Action.UPLOAD) up += key
        }
        // The state after the sync: what both sides look like now (etags fresh from the server for uploads).
        val after = if (up.isNotEmpty()) remoteFiles(root) else remote
        for (k in (down + up + plan.filter { it.second == Action.NONE }.map { it.first }).distinct()) {
            val f = local(k)
            val r = after[k]
            if (f.isFile && r != null) files[k] = Entry(f.lastModified(), f.length(), r.etag ?: "${r.modified}-${r.size}")
        }
        // Conflict copies become chats or go to sync/konflikte, as on the PC.
        if (conflicts.isNotEmpty()) SyncGuard.keepBoth(data, SyncGuard.conflicts(data, listOf(me.pc)), now)
        state = state.copy(files = files, lastSync = now)
        save(state)
        // The note of this device, like a PC's: which data files it changed since the session began.
        note(root, open, sessionStart, up.filter { it.startsWith("d:") }.associate { it.drop(2) to local(it).length() })
        Result(down, up, deletedHere, deletedThere, conflicts)
    }

    /** Writes this device's note into the folder. */
    suspend fun note(root: String, open: Boolean, sessionStart: Long, changed: Map<String, Long> = emptyMap()) {
        val now = clock()
        val previous = runCatching { json.decodeFromString(SyncGuard.Session.serializer(), String(dav().download(noteRemote(root), 1_000_000))) }.getOrNull()
        val merged = (previous?.takeIf { it.openedAt == sessionStart }?.changed.orEmpty()) + changed
        val note = SyncGuard.Session(
            pc = me.pc, id = me.id, user = me.user, app = me.app, openedAt = sessionStart, aliveAt = now,
            closedAt = if (open) null else now, background = !open, changed = merged,
        )
        val target = noteRemote(root)
        dav().mkdirs(WebDavClient.parent(target))
        dav().put(target, json.encodeToString(SyncGuard.Session.serializer(), note).toByteArray())
    }

    private fun noteRemote(root: String) = WebDavClient.join(WebDavClient.join(remoteData(root), SyncGuard.FOLDER), SyncGuard.noteFile(File("x"), me).name)

    enum class Action { NONE, FORGET, UPLOAD, DOWNLOAD, CONFLICT, DELETE_HERE, DELETE_THERE }

    /** What to do with one file: [l] here, [r] in the Nextcloud, [s] as both were after the last sync. */
    fun decide(l: File?, r: DavEntry?, s: Entry?): Action {
        val localChanged = l != null && (s == null || l.lastModified() != s.mtime || l.length() != s.size)
        val remoteChanged = r != null && (s == null || (r.etag ?: "${r.modified}-${r.size}") != s.etag)
        return when {
            l == null && r == null -> Action.FORGET
            l != null && r != null && s == null -> Action.CONFLICT
            l != null && r == null && s == null -> Action.UPLOAD
            l == null && r != null && s == null -> Action.DOWNLOAD
            l == null -> if (remoteChanged) Action.DOWNLOAD else Action.DELETE_THERE
            r == null -> if (localChanged) Action.UPLOAD else Action.DELETE_HERE
            localChanged && remoteChanged -> Action.CONFLICT
            localChanged -> Action.UPLOAD
            remoteChanged -> Action.DOWNLOAD
            else -> Action.NONE
        }
    }

    companion object {
        /** The app's data inside the Lora-Ordner of a PC (desktop/LoraLayout). */
        const val DATA = ".lora"
        /** Stores of single files that the app can read again while it runs. */
        val SYNCED_FILES = setOf("folders.json", "memory.json", "snippets.json", "templates.json", "contacts.json")
        /** Folders whose files are synced (one chat per file). */
        val SYNCED_DIRS = listOf("conversations")
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}

/**
 * Runs [PhoneSync] on Android (AP-28): when the app comes to the front, every few minutes while it is used, and when it
 * goes to the background; reloads chats and lists that came from the PC.
 */
class PhoneSyncService(private val c: AppContainer) {
    private val engine by lazy {
        val id = c.settings.prefs.getString("deviceId", null) ?: java.util.UUID.randomUUID().toString().also { c.settings.prefs.edit().putString("deviceId", it).apply() }
        PhoneSync(
            { c.nextcloud.client() }, c.appContext.filesDir, LoraRoot.dir(c.appContext),
            SyncGuard.Me(pc = "Handy " + deviceModel(), id = id, app = io.github.thepytales.llmhub.BuildConfig.VERSION_NAME),
        )
    }

    /** The phone's model name (android.os.Build.MODEL; read by reflection, the Windows build has no such class). */
    private fun deviceModel(): String = runCatching { Class.forName("android.os.Build").getField("MODEL").get(null) as? String }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android"

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var sessionStart = System.currentTimeMillis()

    val enabled: Boolean get() = c.settings.current.let { it.hasNextcloud && it.phoneSyncRoot.isNotBlank() }

    suspend fun looksLikeLoraFolder(root: String) = engine.looksLikeLoraFolder(root)

    /** One sync now; the text for the settings page. */
    suspend fun syncNow(open: Boolean = true): String {
        if (!enabled) return "Sync ist aus."
        _running.value = true
        return try {
            val r = engine.run(c.settings.current.phoneSyncRoot, open, sessionStart)
            if (r.changedHere) apply(r)
            val df = java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY)
            (r.waiting ?: ("Synchronisiert ${df.format(java.util.Date())}: ${r.down.size} geholt, ${r.up.size} hochgeladen" +
                (if (r.deletedHere + r.deletedThere > 0) ", ${r.deletedHere + r.deletedThere} gelöscht" else "") +
                (if (r.conflicts.isNotEmpty()) ", ${r.conflicts.size} Konflikt(e) – beide Fassungen behalten" else "") + "."))
                .also { _status.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ("Sync fehlgeschlagen: " + (e.message ?: e.javaClass.simpleName)).also { _status.value = it; io.github.thepytales.llmhub.ErrorLog.record("Handy-Sync", e) }
        } finally {
            _running.value = false
        }
    }

    /** Chats and lists that came from the PC are read again; the Lora-Ordner shows its new files. */
    private fun apply(r: PhoneSync.Result) {
        val keys = r.down + r.conflicts
        val chatIds = File(c.appContext.filesDir, "conversations").listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }
            .filter { id -> keys.any { it == "d:conversations/$id.json" } || c.chats.conversations.value.none { it.id == id } }
        c.chats.rescan(chatIds + keys.filter { it.startsWith("d:conversations/") && it.count { ch -> ch == '/' } == 1 }.map { it.substringAfterLast('/').removeSuffix(".json") })
        if ("d:folders.json" in keys) c.folders.reload()
        if ("d:memory.json" in keys) c.memory.reload()
        if ("d:snippets.json" in keys) c.snippets.reload()
        if ("d:templates.json" in keys) c.templates.reload()
        if ("d:contacts.json" in keys) c.contacts.reload()
        if (keys.any { it.startsWith("v:") }) c.drive.changed()
    }

    /** The loop of the app (started by the container): front, every five minutes, background. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            delay(4000)
            var front = false
            var last = 0L
            while (true) {
                if (enabled) {
                    val now = io.github.thepytales.llmhub.AppState.foreground
                    when {
                        now && !front -> { sessionStart = System.currentTimeMillis(); syncNow(open = true); last = System.currentTimeMillis() }
                        now && System.currentTimeMillis() - last > INTERVAL -> { syncNow(open = true); last = System.currentTimeMillis() }
                        !now && front -> syncNow(open = false)
                    }
                    front = now
                }
                delay(5000)
            }
        }
    }

    companion object {
        const val INTERVAL = 5 * 60_000L
    }
}
