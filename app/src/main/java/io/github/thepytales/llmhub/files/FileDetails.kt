package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.ui.tr
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.DateFormat
import java.util.Date

/**
 * Details of a file for the Details-Spalte and the Schnellansicht in Dateien (AP-66): what it is, how big, when it was
 * made and changed, where it lies – and, depending on the kind, pages, words, slides or the picture's size. The numbers
 * that need the file read are filled in by the caller ([pages], [words] …); this part only formats.
 */
object FileDetails {
    data class Row(val label: String, val value: String)

    /** „1,2 MB“, „340 KB“, „12 B“. */
    fun size(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1048576.0)
        else -> String.format(java.util.Locale.GERMANY, "%.1f GB", bytes / 1073741824.0)
    }

    /** When [f] was created (the file system's birth time; null where it does not keep one). */
    fun created(f: File): Long? = runCatching {
        Files.readAttributes(f.toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
    }.getOrNull()?.takeIf { it > 0 }

    /** Where [f] lies: „Dateien › Erstellt › Briefe“ inside [root] (the Lora-Ordner), else its folder on the disk. */
    fun place(f: File, root: File?): String {
        val parent = f.absoluteFile.parentFile ?: return ""
        val r = root?.absoluteFile?.normalize()
        val p = parent.normalize()
        if (r != null && (p == r || p.path.startsWith(r.path + File.separator))) {
            val rel = p.path.removePrefix(r.path).trim(File.separatorChar)
            return (listOf(tr("Dateien")) + rel.split(File.separatorChar).filter { it.isNotBlank() }).joinToString(" › ")
        }
        return p.path
    }

    /** Words of a text (for documents, Word and text files). */
    fun words(text: String): Int = WORD.findAll(text).count()

    /** A word: letters and digits with inner apostrophes and hyphens; dates and numbers like 12.11.2026 or 3,5 are one. */
    private val WORD = Regex("[\\p{L}\\p{N}](?:[\\p{L}\\p{N}'’-]|[.,](?=\\p{N}))*")

    /**
     * The rows for [f]: [kind] (e.g. „PDF“), size, changed, created, place, then [extra] (pages, words … as the caller
     * found them) and [tags]. Folders show their number of entries instead of a size.
     */
    fun rows(
        f: File,
        kind: String,
        root: File?,
        extra: List<Row> = emptyList(),
        tags: List<String> = emptyList(),
        entries: Int? = null,
        dates: DateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT),
    ): List<Row> = buildList {
        add(Row(tr("Art"), kind))
        if (f.isDirectory) add(Row(tr("Inhalt"), tr("{0} Einträge", entries ?: f.listFiles()?.count { !it.name.startsWith(".") } ?: 0)))
        else add(Row(tr("Größe"), size(f.length())))
        add(Row(tr("Geändert"), dates.format(Date(f.lastModified()))))
        created(f)?.takeIf { kotlin.math.abs(it - f.lastModified()) > 2000 }?.let { add(Row(tr("Erstellt"), dates.format(Date(it)))) }
        addAll(extra.filter { it.value.isNotBlank() })
        if (tags.isNotEmpty()) add(Row(tr("Schlagwörter"), tags.joinToString(" ") { "#$it" }))
        place(f, root).takeIf { it.isNotBlank() }?.let { add(Row(tr("Ort"), it)) }
    }

    /** The neighbour of [current] in [keys] when going [delta] steps (Schnellansicht ←/→), wrapping around; null if none. */
    fun <T> step(keys: List<T>, current: T, delta: Int): T? {
        if (keys.isEmpty()) return null
        val i = keys.indexOf(current)
        if (i < 0) return keys.first()
        return keys[((i + delta) % keys.size + keys.size) % keys.size]
    }
}
