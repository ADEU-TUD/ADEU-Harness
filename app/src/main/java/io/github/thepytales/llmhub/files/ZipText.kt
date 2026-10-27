package io.github.thepytales.llmhub.files

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream

/** Contents of ZIP archives: list of entries and single entries (with size limits against zip bombs). */
object ZipText {
    const val MAX_ENTRY = 25L * 1024 * 1024
    private const val MAX_LIST = 1000

    data class Entry(val name: String, val size: Long, val directory: Boolean)

    /** Names may be UTF-8 or (older Windows archives) code page 437. */
    private fun <T> withCharset(bytes: ByteArray, block: (ZipInputStream) -> T): T {
        val charsets = listOfNotNull(Charsets.UTF_8, runCatching { Charset.forName("IBM437") }.getOrNull(), Charsets.ISO_8859_1)
        var last: Exception? = null
        for (cs in charsets) {
            try {
                return ZipInputStream(ByteArrayInputStream(bytes), cs).use(block)
            } catch (e: IllegalArgumentException) {
                last = e
            }
        }
        throw last ?: IllegalArgumentException("Das ZIP-Archiv kann nicht gelesen werden.")
    }

    fun entries(bytes: ByteArray): List<Entry> = withCharset(bytes) { zip ->
        val out = mutableListOf<Entry>()
        var e = zip.nextEntry
        while (e != null && out.size < MAX_LIST + 1) {
            val size = if (e.size >= 0) e.size else if (e.isDirectory) 0 else count(zip)
            out += Entry(e.name, size, e.isDirectory)
            e = zip.nextEntry
        }
        out
    }

    private fun count(zip: ZipInputStream): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY * 4) break
        }
        return total
    }

    fun listing(name: String, bytes: ByteArray): String {
        val all = entries(bytes)
        val files = all.filter { !it.directory }
        return buildString {
            append("ZIP-Archiv „").append(name).append("“ mit ").append(files.size).append(if (files.size == 1) " Datei" else " Dateien")
            append(" (").append(size(files.sumOf { it.size })).append(" entpackt):\n")
            all.take(MAX_LIST).forEach { e ->
                if (e.directory) append("[Ordner] ").append(e.name).append('\n')
                else append(e.name).append(" · ").append(size(e.size)).append('\n')
            }
            if (all.size > MAX_LIST) append("… weitere Einträge\n")
            append("Einzelne Dateien lesen: Pfad „").append(name).append("/ordner/datei.txt“.")
        }
    }

    /** Bytes of the entry [path] (case-insensitive, / or \), or null if there is none. */
    fun entry(bytes: ByteArray, path: String): ByteArray? {
        val wanted = path.replace('\\', '/').trim('/')
        return withCharset(bytes) { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.trim('/').equals(wanted, true)) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_ENTRY) throw IllegalArgumentException("„$wanted“ ist entpackt zu groß (max. 25 MB).")
                    }
                    return@withCharset out.toByteArray()
                }
                e = zip.nextEntry
            }
            null
        }
    }

    /** Whether [path] names a directory inside the archive. */
    fun isDirectory(bytes: ByteArray, path: String): Boolean {
        val prefix = path.replace('\\', '/').trim('/') + "/"
        return entries(bytes).any { it.name.startsWith(prefix, true) }
    }

    fun size(b: Long) = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "${b / 1024} KB"
        else -> String.format(Locale.GERMANY, "%.1f MB", b / 1048576.0)
    }
}
