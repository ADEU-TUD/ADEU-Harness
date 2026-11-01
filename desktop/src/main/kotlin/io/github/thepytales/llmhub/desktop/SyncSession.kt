package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.data.SyncGuard
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Einfacher Sync on Windows (AP-12): while the data folder is a chosen one (e.g. in OneDrive, Google Drive or Nextcloud),
 * this PC keeps its note in `sync/` – at the start, every two minutes and when the app ends – and watches for another PC
 * that opens the same folder meanwhile ([newcomer]). Conflict copies of the cloud service are kept as second versions
 * at the start ([kept]). See [SyncGuard].
 */
object SyncSession {
    /** This PC: Windows computer name and a random id kept on the PC. */
    val me: SyncGuard.Me by lazy {
        val idFile = File(Paths.machine, "pc-id.txt")
        val id = runCatching { idFile.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().take(8).also { runCatching { idFile.writeText(it) } }
        SyncGuard.Me(pc = computerName(), id = id, user = System.getProperty("user.name").orEmpty(), app = BuildConfig.VERSION_NAME)
    }

    /** The Lora-Ordner lies in a cloud folder (and no test runs): only then is there something to guard. */
    val active: Boolean
        get() = Paths.synced

    /** When this PC opened the folder (0 = not yet). */
    @Volatile
    var openedAt = 0L
        private set

    /** Whether the app only runs in the notification area with nothing to do (set by the window). */
    @Volatile
    var background: () -> Boolean = { false }

    /** Another PC that opened the folder while this one runs. */
    val newcomer = MutableStateFlow<SyncGuard.Session?>(null)

    /** What happened to conflict copies at the start (shown once). */
    val kept = MutableStateFlow<SyncGuard.Kept?>(null)

    /** The names of all PCs that ever opened the folder (OneDrive names conflict copies after them). */
    fun pcNames(): List<String> = (SyncGuard.sessions(Paths.data).map { it.pc } + me.pc).distinct()

    /**
     * Starts the session: keeps conflict copies as second versions, writes the note, renews it every two minutes and
     * closes it when the app ends. Call once the start check passed, before the data is loaded.
     */
    fun start() {
        if (!active || openedAt != 0L) return
        val data = Paths.data
        runCatching {
            val found = SyncGuard.conflicts(data, pcNames())
            if (found.isNotEmpty()) {
                val result = SyncGuard.keepBoth(data, found)
                AppLog.i("Sync: ${result.chats.size} Chat(s) als zweite Fassung, ${result.setAside.size} Datei(en) nach sync/konflikte")
                if (!result.isEmpty) kept.value = result
            }
        }.onFailure { AppLog.e("Sync: Konfliktdateien", it) }
        openedAt = System.currentTimeMillis()
        touch()
        Runtime.getRuntime().addShutdownHook(Thread { touch(closed = true) })
        thread(isDaemon = true, name = "sync-note") {
            while (true) {
                Thread.sleep(SyncGuard.HEARTBEAT_MS)
                touch()
                runCatching { SyncGuard.newcomer(data, me, openedAt) }.getOrNull()?.let { other ->
                    if (newcomer.value?.id != other.id) AppLog.i("Sync: Lora wurde auch auf ${other.pc} geöffnet")
                    newcomer.value = other
                }
            }
        }
    }

    /** Renews the note (the files changed so far, alive now). */
    fun touch(closed: Boolean = false) {
        if (openedAt == 0L) return
        runCatching { SyncGuard.touch(Paths.data, me, openedAt, closed = closed, background = runCatching { background() }.getOrDefault(false)) }
            .onFailure { AppLog.e("Sync: Notiz schreiben", it) }
    }

    /** What speaks against opening the folder now (null = nothing, or no chosen folder). */
    fun check(): SyncGuard.Finding? = if (!active) null else runCatching { SyncGuard.check(Paths.data, me) }.getOrNull()

    private fun computerName(): String =
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.substringBefore('.')?.takeIf { it.isNotBlank() }
            ?: "PC"
}

/** Cloud folders found on this PC, to put the data folder in with one click (AP-12). */
object CloudFolders {
    /** A cloud service's folder: [name] (e.g. „OneDrive“) and where Lora's data would go. */
    data class Place(val name: String, val root: File) {
        val target: File get() = File(root, "Lora")
    }

    /** OneDrive (private and work), Google Drive for desktop, Nextcloud (also the TU's), Dropbox. */
    fun found(): List<Place> {
        val home = File(System.getProperty("user.home"))
        val out = mutableListOf<Place>()
        fun add(name: String, dir: File?) {
            if (dir != null && dir.isDirectory && out.none { it.root.absoluteFile == dir.absoluteFile }) out += Place(name, dir)
        }
        add("OneDrive (Arbeit/Uni)", System.getenv("OneDriveCommercial")?.let(::File))
        add("OneDrive", (System.getenv("OneDriveConsumer") ?: System.getenv("OneDrive"))?.let(::File))
        // Google Drive for desktop shows up as a drive (G: by default) with „Meine Ablage“ / „My Drive“; only drives
        // that exist are asked (call this off the UI thread – a network drive can be slow).
        if (Paths.windows) {
            File.listRoots().orEmpty().filter { it.path.firstOrNull()?.uppercaseChar() in 'D'..'Z' }.forEach { root ->
                listOf("Meine Ablage", "My Drive").forEach { sub -> add("Google Drive", File(root, sub)) }
            }
        }
        add("Google Drive", File(home, "Google Drive"))
        listOf("Nextcloud", "Datashare", "cloudstore").forEach { add("Nextcloud", File(home, it)) }
        add("Dropbox", File(home, "Dropbox"))
        return out
    }

    /** The cloud service [dir] lies in, if it can be told from its path. */
    fun serviceOf(dir: File): String? {
        val p = dir.absolutePath.lowercase()
        return when {
            "onedrive" in p -> "OneDrive"
            "meine ablage" in p || "my drive" in p || "google drive" in p -> "Google Drive"
            "nextcloud" in p || "datashare" in p || "cloudstore" in p -> "Nextcloud"
            "dropbox" in p -> "Dropbox"
            else -> null
        }
    }
}
