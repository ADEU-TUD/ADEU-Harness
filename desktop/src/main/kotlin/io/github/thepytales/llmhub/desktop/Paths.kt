package io.github.thepytales.llmhub.desktop

import java.io.File

/**
 * Folders of the Windows app. Data lives per Windows user in %APPDATA%\ADEU LLM, so it survives updates
 * (a new version is just started instead of the old one) and needs no admin rights.
 */
object Paths {
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val home = File(System.getProperty("user.home"))

    /** Where the data lives without a chosen folder: %APPDATA%\ADEU LLM. It also holds the pointer to a chosen one. */
    private val standard: File by lazy {
        val dir = if (windows) File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Roaming").path, "ADEU LLM")
        else File(home, ".adeu-llm")
        dir.apply { mkdirs() }
    }

    /** Text file in [standard] with the path of a data folder the user chose (e.g. in Google Drive). */
    private val pointer: File get() = File(standard, "speicherort.txt")

    /** The data folder the user chose, if any (Einstellungen › Windows › Speicherort). */
    val chosen: File? get() = runCatching { pointer.readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::File)

    /**
     * Chats, settings, documents … (Android: filesDir). Can be changed with -Dadeu.data=… for tests, or in the
     * settings: then a folder the user chose, like an Obsidian vault – put into Google Drive or OneDrive, the data
     * is synced to other computers.
     */
    val data: File by lazy {
        val custom = System.getProperty("adeu.data")?.takeIf { it.isNotBlank() }
        val dir = when {
            custom != null -> File(custom)
            else -> chosen?.takeIf { it.isDirectory || it.mkdirs() } ?: standard
        }
        dir.apply { mkdirs() }
    }

    /**
     * Files that belong to this PC and never roam with a synced data folder (AP-12): the API keys and tokens, the
     * window position, the update state. The standard folder (or the data folder in tests).
     */
    val machine: File
        get() = if (!System.getProperty("adeu.data").isNullOrBlank()) data else standard

    /** Files of this PC in [machine] that are never copied into another data folder. */
    private val PER_PC = setOf("speicherort.txt", "secure_settings.dat", "schluessel.dat", "key.bin", "pc-id.txt", "ui_state.prefs.json", "updates.prefs.json")

    /** Whether [dir] already holds Lora's data (e.g. of another PC, synced by the cloud service). */
    fun hasData(dir: File): Boolean =
        listOf("einstellungen.json", "secure_settings.dat").any { File(dir, it).isFile } ||
            listOf("conversations", "documents", "livedocs").any { File(dir, it).isDirectory }

    /**
     * Uses [target] as data folder from the next start. With [copy] the current data is copied there first
     * (files that already exist there are kept, so data of another computer is not overwritten – unless
     * [overwriteOlder] and the existing file is older). null = back to the standard folder.
     */
    fun relocate(target: File?, copy: Boolean, overwriteOlder: Boolean = false) {
        val dest0 = target ?: standard
        if (copy && dest0.canonicalFile != data.canonicalFile) {
            val from = data.canonicalFile
            from.walkTopDown().onEnter { it == from || it.parentFile != from || it.name != "cache" }.filter { it.isFile }.forEach { f ->
                val rel = f.canonicalFile.path.removePrefix(from.path).trimStart(File.separatorChar)
                if (rel.startsWith("instance.") || rel.endsWith(".tmp") || rel in PER_PC) return@forEach
                val dest = File(dest0, rel)
                if (!dest.exists() || (overwriteOlder && dest.lastModified() < f.lastModified())) {
                    dest.parentFile?.mkdirs()
                    f.copyTo(dest, overwrite = true)
                    dest.setLastModified(f.lastModified())
                }
            }
        }
        if (target == null) pointer.delete() else pointer.writeText(target.absolutePath)
    }

    /** Starts the app again (Windows) and ends this instance, e.g. after choosing another data folder. */
    fun restart(): Nothing {
        val exe = ProcessHandle.current().info().command().orElse(null)
        if (windows && exe != null) {
            // Wait two seconds so this instance has released its lock.
            runCatching { ProcessBuilder("cmd", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" \"$exe\"").start() }
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
            else -> File(home, ".adeu-llm/local")
        }
        dir.apply { mkdirs() }
    }

    /** Temporary files (Android: cacheDir). */
    val cache: File by lazy { File(data, "cache").apply { mkdirs() } }

    /** Default folder for saving files the user exports (Documents\Lora, created on demand). */
    val exports: File get() = File(File(home, "Documents").takeIf { it.isDirectory } ?: home, "Lora")
}
