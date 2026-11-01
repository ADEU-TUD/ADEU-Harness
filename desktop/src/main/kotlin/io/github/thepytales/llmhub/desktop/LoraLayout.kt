package io.github.thepytales.llmhub.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The inside of the Lora-Ordner (AP-16): the app's data in the hidden „.lora“, everything else is the user's. Moves the
 * data of earlier versions in once – from %APPDATA%\ADEU LLM or from a data folder chosen before (where the data lay
 * directly in the folder).
 */
object LoraLayout {
    const val DATA = ".lora"

    /** Folders the app keeps in its data folder (only these and its own files move out of a chosen folder of before). */
    private val DATA_DIRS = setOf(
        "attachments", "cache", "chats", "codebooks", "conversations", "data", "docs", "documents", "images", "knowledge", "lernspiele",
        "livedocs", "logos", "papierkorb", "profil", "recordings", "sync", "transcripts", "vault", "volltext", "dateien-papierkorb",
    )
    private val DATA_EXT = setOf("json", "dat", "bin", "pending", "pid")
    private val DATA_FILES = setOf("selftest.txt", "work_files.txt", "crash.txt", "absturz.txt")

    /** Whether [dir] holds Lora's data. */
    fun isData(dir: File): Boolean =
        listOf("einstellungen.json", "adeu-settings.json", "secure_settings.dat").any { File(dir, it).isFile } ||
            listOf("conversations", "documents", "livedocs").any { File(dir, it).isDirectory }

    private fun isDataEntry(f: File): Boolean =
        if (f.isDirectory) f.name in DATA_DIRS else f.name in DATA_FILES || f.extension.lowercase() in DATA_EXT

    /**
     * A folder of before AP-16 had the data directly inside: moves those entries into [data] (an entry that is already
     * there stays – e.g. synced from a PC that moved first).
     */
    fun moveLegacy(root: File, data: File) {
        if (!isData(root)) return
        data.mkdirs()
        root.listFiles().orEmpty().filter { it.name != DATA && !it.name.startsWith("instance.") && isDataEntry(it) }.forEach { f ->
            val dest = File(data, f.name)
            if (!dest.exists()) move(f, dest) else if (f.isDirectory) mergeInto(f, dest)
        }
    }

    /** Moves the data of %APPDATA%\ADEU LLM into [data]; the files of this PC ([keep]) stay. */
    fun moveStandard(standard: File, data: File, keep: Set<String>) {
        if (!isData(standard)) return
        standard.listFiles().orEmpty().filter { it.name !in keep && it.name != "cache" && !it.name.startsWith("instance.") && it.name != "local" }.forEach { f ->
            val dest = File(data, f.name)
            if (!dest.exists()) move(f, dest) else if (f.isDirectory) mergeInto(f, dest)
        }
    }

    /** Moves the content of [from] into [to] where it is missing there, then removes what was moved. */
    private fun mergeInto(from: File, to: File) {
        from.listFiles().orEmpty().forEach { f ->
            val dest = File(to, f.name)
            if (!dest.exists()) move(f, dest) else if (f.isDirectory && dest.isDirectory) mergeInto(f, dest)
        }
        from.delete()
    }

    /** Rename when possible, else copy and delete (another drive). */
    private fun move(f: File, dest: File) {
        dest.parentFile?.mkdirs()
        try {
            Files.move(f.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            f.copyRecursively(dest, overwrite = false)
            f.deleteRecursively()
        }
    }

    /** Hides the data folder in the Explorer (Windows). */
    fun hide(data: File) {
        if (!Paths.windows || !data.exists()) return
        runCatching { Files.setAttribute(data.toPath(), "dos:hidden", true) }
    }

    /**
     * Copies the tree [from] into [to]: existing files stay, unless [overwriteOlder] and they are older. Skips the
     * cache, lock files, temporary files and what [skip] says (relative path with „/“).
     */
    fun copyTree(from: File, to: File, overwriteOlder: Boolean, skip: (String) -> Boolean = { false }) {
        val base = from.canonicalFile
        base.walkTopDown().onEnter { d -> d == base || !(d.parentFile == File(base, DATA).canonicalFile && d.name == "cache") }.filter { it.isFile }.forEach { f ->
            val rel = f.canonicalFile.path.removePrefix(base.path).trimStart(File.separatorChar).replace(File.separatorChar, '/')
            val name = rel.substringAfterLast('/')
            if (name.startsWith("instance.") || name.endsWith(".tmp") || skip(rel)) return@forEach
            val dest = File(to, rel)
            if (!dest.exists() || (overwriteOlder && dest.lastModified() < f.lastModified())) {
                dest.parentFile?.mkdirs()
                f.copyTo(dest, overwrite = true)
                dest.setLastModified(f.lastModified())
            }
        }
    }
}
