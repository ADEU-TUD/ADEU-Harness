package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.ErrorLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Safe saving: a crash, a force stop or an empty battery while writing leaves either the old or the new version
 * of a file, never a half-written one. Files that can no longer be read are set aside instead of being dropped.
 */
object AtomicFiles {
    /** Writes [text] via a temporary file that is flushed to disk and then renamed over [file]. */
    fun write(file: File, text: String, keepBackup: Boolean = false) = writeBytes(file, text.toByteArray(), keepBackup)

    fun writeBytes(file: File, data: ByteArray, keepBackup: Boolean = false) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(data)
            out.flush()
            out.fd.sync()
        }
        // The previous version stays as ".bak", a second chance should the new one turn out broken.
        if (keepBackup && file.isFile) runCatching { Files.copy(file.toPath(), backupOf(file).toPath(), StandardCopyOption.REPLACE_EXISTING) }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: UnsupportedOperationException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Reads [file]; if it is missing, a finished temporary file of an interrupted save takes its place (the rename
     * is the last step, so a complete ".tmp" is the newest version).
     */
    fun read(file: File): String? {
        if (!file.isFile) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            if (tmp.isFile && tmp.length() > 0) runCatching { Files.move(tmp.toPath(), file.toPath()) }
        }
        return if (file.isFile) file.readText() else null
    }

    /** The version before the last save (see [write] with keepBackup). */
    fun backupOf(file: File) = File(file.parentFile, file.name + ".bak")

    /**
     * Moves a file that cannot be read into "defekt/" next to it, so it is neither lost nor overwritten and can
     * be sent in for repair. Returns the new place.
     */
    fun quarantine(file: File, error: Throwable): File? {
        ErrorLog.record("Datei nicht lesbar: ${file.name}", error)
        val dir = File(file.parentFile, "defekt").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}-${file.name}")
        return runCatching { Files.move(file.toPath(), target.toPath()); target }.getOrNull()
    }
}
