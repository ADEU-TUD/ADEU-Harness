package io.github.thepytales.llmhub.desktop

import io.github.thepytales.llmhub.AppLog
import java.io.File

/**
 * Folders of the desktop app. Data lives per user – Windows: %APPDATA%\ADEU LLM, macOS: ~/Library/Application Support/
 * ADEU LLM, Linux: ~/.config/adeu-llm and ~/.local/share/adeu-llm (AP-31) –, so it survives updates and needs no admin
 * rights.
 */
object Paths {
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val home = File(System.getProperty("user.home"))

    /** Where the data lived before the Lora-Ordner (AP-16): %APPDATA%\\ADEU LLM. It keeps the files of this PC and the pointer. */
    private val standard: File by lazy {
        val dir = when {
            windows -> File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Roaming").path, "ADEU LLM")
            // Development runs before AP-31 kept it here.
            File(home, ".adeu-llm").isDirectory -> File(home, ".adeu-llm")
            Os.mac -> File(home, "Library/Application Support/ADEU LLM")
            else -> File(xdg("XDG_CONFIG_HOME", ".config"), "adeu-llm")
        }
        dir.apply { mkdirs() }
    }

    /** Text file in [standard] with the path of the Lora-Ordner. */
    private val rootPointer: File get() = File(standard, "lora-ordner.txt")

    /** Before AP-16: text file with the path of a data folder the user chose (Einstellungen › Speicherort, Sync). */
    private val legacyPointer: File get() = File(standard, "speicherort.txt")

    /** Marks that the user put the Lora-Ordner into a cloud folder on purpose (Einstellungen › Synchronisation). */
    private val syncFlag: File get() = File(standard, "sync-an.txt")

    private val testData: String? get() = System.getProperty("adeu.data")?.takeIf { it.isNotBlank() }

    /** A base folder of the XDG standard (Linux), e.g. ~/.config. */
    private fun xdg(variable: String, fallback: String): File =
        System.getenv(variable)?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, fallback)

    /**
     * The Documents folder of Windows (also when it was moved into OneDrive); on Linux the one of the desktop
     * (~/Dokumente on a German system, from user-dirs.dirs); else ~/Documents.
     */
    val documents: File by lazy {
        val known = when {
            windows -> runCatching {
                com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(com.sun.jna.platform.win32.KnownFolders.FOLDERID_Documents)
            }.getOrNull()?.let(::File)
            Os.linux -> runCatching {
                File(xdg("XDG_CONFIG_HOME", ".config"), "user-dirs.dirs").readLines()
                    .firstOrNull { it.startsWith("XDG_DOCUMENTS_DIR=") }?.substringAfter('=')?.trim()?.trim('"')
                    ?.replace("\$HOME", home.path)?.let(::File)
            }.getOrNull()
            else -> null
        }?.takeIf { it.isDirectory && it != home }
        known ?: File(home, "Documents").takeIf { it.isDirectory } ?: home
    }

    /** Where a new Lora-Ordner goes: Dokumente\\Lora. */
    val defaultRoot: File get() = File(documents, "Lora")

    /** The pointer's folder, read fresh (null: none yet). */
    private fun pointed(): File? = runCatching { rootPointer.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::File)

    /** The data folder the user chose before AP-16 (now the Lora-Ordner, with the data moved into its .lora). */
    val chosen: File? get() = pointed() ?: runCatching { legacyPointer.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::File)

    /**
     * The Lora-Ordner was set but is gone (moved in the Explorer, drive not connected): its old path. Checked by main()
     * before anything is loaded, so the user can show where it is now ([useRoot]).
     */
    fun missingRoot(): File? = if (testData != null) null else pointed()?.takeIf { !it.isDirectory }

    /** Points to [dir] as the Lora-Ordner (before the app loads anything). */
    fun useRoot(dir: File) {
        dir.mkdirs()
        rootPointer.writeText(dir.absolutePath)
        layout = null
    }

    private class Layout(val root: File, val data: File)

    @Volatile
    private var layout: Layout? = null

    private fun layout(): Layout = layout ?: synchronized(this) { layout ?: resolve().also { layout = it } }

    /**
     * Lora-Ordner (AP-16): one folder for everything, like an Obsidian vault – the files the user sees (Hochgeladen,
     * Erstellt, own folders, documents of the editor as .docx/.xlsx/.pptx/.md) and the app's data hidden in „.lora“.
     * The user puts it where it is needed (also into a cloud folder, AP-12) and can move it. Takes over the data of
     * earlier versions once: from %APPDATA%\\ADEU LLM into Dokumente\\Lora\\.lora, from a chosen data folder into its .lora.
     */
    private fun resolve(): Layout {
        testData?.let { return Layout(File(it, "Lora").apply { mkdirs() }, File(it).apply { mkdirs() }) }
        pointed()?.let { root ->
            val data = File(root, LoraLayout.DATA)
            root.mkdirs()
            runCatching { LoraLayout.moveLegacy(root, data) }.onFailure { AppLog.e("Lora-Ordner: alte Daten übernehmen", it) }
            LoraLayout.hide(data)
            return Layout(root, data.apply { mkdirs() })
        }
        // A data folder chosen before AP-16 (e.g. in the cloud folder for the sync): it becomes the Lora-Ordner.
        runCatching { legacyPointer.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory }?.let { root ->
            val data = File(root, LoraLayout.DATA)
            return runCatching {
                LoraLayout.moveLegacy(root, data)
                LoraLayout.hide(data)
                rootPointer.writeText(root.absolutePath)
                runCatching { syncFlag.writeText("1") }
                legacyPointer.delete()
                Layout(root, data)
            }.getOrElse { e -> AppLog.e("Lora-Ordner einrichten", e); Layout(File(root, "Dateien"), root) }
        }
        // First start with AP-16: the data of %APPDATA% moves into Dokumente\\Lora\\.lora.
        val root = defaultRoot
        val data = File(root, LoraLayout.DATA)
        return runCatching {
            data.mkdirs()
            LoraLayout.moveStandard(standard, data, PER_PC)
            LoraLayout.hide(data)
            rootPointer.writeText(root.absolutePath)
            Layout(root, data)
        }.getOrElse { e -> AppLog.e("Lora-Ordner einrichten", e); Layout(File(standard, "Dateien").apply { mkdirs() }, standard) }
    }

    /** The Lora-Ordner: the files the user sees (Hochgeladen, Erstellt, own folders). */
    val root: File get() = layout().root

    /**
     * Chats, settings, documents … (Android: filesDir), hidden in the Lora-Ordner („.lora“). -Dadeu.data=… for tests.
     */
    val data: File get() = layout().data

    /** Whether the Lora-Ordner lies in a cloud folder (sync on, AP-12): only then is there something to guard. */
    val synced: Boolean
        get() = testData == null && (syncFlag.isFile || CloudFolders.serviceOf(root) != null)

    /**
     * Files that belong to this PC and never roam with a synced data folder (AP-12): the API keys and tokens, the
     * window position, the update state. The standard folder (or the data folder in tests).
     */
    val machine: File
        get() = if (testData != null) data else standard

    /** Files of this PC in [machine] that are never copied into another data folder. */
    private val PER_PC = setOf(
        "speicherort.txt", "lora-ordner.txt", "sync-an.txt", "secure_settings.dat", "schluessel.dat", "key.bin", "pc-id.txt",
        "ui_state.prefs.json", "updates.prefs.json", "log.txt", "log.old.txt",
    )

    /** Whether [dir] already holds a Lora-Ordner (e.g. of another PC, synced by the cloud service), also one of before AP-16. */
    fun hasData(dir: File): Boolean = LoraLayout.isData(File(dir, LoraLayout.DATA)) || LoraLayout.isData(dir)

    /**
     * Uses [target] as Lora-Ordner from the next start. With [copy] the whole current Lora-Ordner (files and data) is
     * copied there first (files that already exist there are kept, so data of another computer is not overwritten –
     * unless [overwriteOlder] and the existing file is older). null = back to Dokumente\\Lora. [sync]: the user puts it into
     * a cloud folder (Einstellungen › Synchronisation).
     */
    fun relocate(target: File?, copy: Boolean, overwriteOlder: Boolean = false, sync: Boolean? = null) {
        val dest = (target ?: defaultRoot).absoluteFile
        if (copy && dest.canonicalPath != root.canonicalPath) {
            dest.mkdirs()
            LoraLayout.copyTree(root, dest, overwriteOlder, skip = { rel -> rel.substringAfterLast('/') in PER_PC })
            // Before AP-16 the data could lie outside the visible folder (fallback): copy it into .lora.
            if (!data.canonicalPath.startsWith(root.canonicalPath)) LoraLayout.copyTree(data, File(dest, LoraLayout.DATA), overwriteOlder, skip = { rel -> rel in PER_PC })
        }
        // An existing folder of before AP-16 (another PC not updated yet): its data moves into .lora at the next start.
        rootPointer.writeText(dest.path)
        legacyPointer.delete()
        when (sync) {
            true -> runCatching { syncFlag.writeText("1") }
            false -> syncFlag.delete()
            null -> {}
        }
    }

    /** Starts the app again and ends this instance, e.g. after choosing another data folder. */
    fun restart(): Nothing {
        val exe = ProcessHandle.current().info().command().orElse(null)
        // Wait two seconds so this instance has released its lock.
        when {
            windows && exe != null -> runCatching { ProcessBuilder("cmd", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" \"$exe\"").start() }
            Os.mac -> Os.appDir?.let { app -> runCatching { ProcessBuilder("sh", "-c", "sleep 2; open -n \"$1\"", "sh", app.path).start() } }
            else -> (Os.launcher?.path ?: exe)?.let { launcher ->
                runCatching { ProcessBuilder("sh", "-c", "sleep 2; \"$1\" >/dev/null 2>&1 &", "sh", launcher).start() }
            }
        }
        kotlin.system.exitProcess(0)
    }

    /**
     * Large files that should not roam with the Windows profile: speech models, recordings, transcripts and local
     * language models (%LOCALAPPDATA%\ADEU LLM). Elsewhere the data folder.
     */
    val local: File by lazy {
        val custom = System.getProperty("adeu.data")?.takeIf { it.isNotBlank() }
        val dir = when {
            custom != null -> File(custom, "local")
            windows -> File(System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Local").path, "ADEU LLM")
            File(home, ".adeu-llm/local").isDirectory -> File(home, ".adeu-llm/local")
            Os.mac -> File(home, "Library/Application Support/ADEU LLM/local")
            else -> File(xdg("XDG_DATA_HOME", ".local/share"), "adeu-llm")
        }
        dir.apply { mkdirs() }
    }

    /** Temporary files (Android: cacheDir). */
    val cache: File by lazy { File(data, "cache").apply { mkdirs() } }

    /** Default folder for saving files the user exports: the Lora-Ordner. */
    val exports: File get() = root
}
