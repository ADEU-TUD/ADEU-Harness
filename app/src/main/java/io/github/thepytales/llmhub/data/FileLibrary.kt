package io.github.thepytales.llmhub.data

import android.content.Context
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PdfExtras
import io.github.thepytales.llmhub.files.FormatReaders
import io.github.thepytales.llmhub.files.TextCodec
import io.github.thepytales.llmhub.files.TextFormats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

/*
 * Dateiverwaltung (AP-18): one place for everything the app keeps as files – documents of the editor, the files in
 * storage locations and project folders, and the knowledge collection – with folders, Schlagwörter, a full-text search
 * over all formats (PDF, Word, Excel, PowerPoint, OpenDocument, mails, text …), „Zuletzt bearbeitet“ and comparing
 * versions. The text of files on disk is read once and kept in a small cache (no database: the search over the cache
 * is fast enough for the thousands of files a person has; a SQLite index stays for later, see ARBEITSPLAN AP-18).
 */

/** Folder and Schlagwörter of an item, by its [key] („doc:<id>“, „file:<path>“, „knowledge:<id>“). */
@Serializable
data class FileMeta(
    val key: String,
    /** Folder inside the app, e.g. „Lehre/WiSe 2026“ (documents and knowledge; files on disk keep their real folder). */
    val folder: String = "",
    val tags: List<String> = emptyList(),
    /** Marked with a star („Markiert“ in the Dateien tab, AP-16). */
    val starred: Boolean = false,
)

enum class FileSource(val label: String) { APP("In der App"), DISK("Ablageort"), KNOWLEDGE("Wissenssammlung") }

enum class FileKind(val label: String) {
    DOC("Dokument"), SLIDES("Präsentation"), SHEET("Tabelle"), MARKDOWN("Markdown"), PDF("PDF"), WORD("Word"), EXCEL("Excel"),
    POWERPOINT("PowerPoint"), IMAGE("Bild"), MAIL("Mail"), TEXT("Text"), KNOWLEDGE("Wissen"), OTHER("Datei");

    companion object {
        fun ofDoc(kind: DocKind) = when (kind) {
            DocKind.DOC -> DOC
            DocKind.SLIDES -> SLIDES
            DocKind.SHEET -> SHEET
            DocKind.MARKDOWN -> MARKDOWN
        }

        fun ofName(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> PDF
            "doc", "docx", "docm", "odt", "rtf" -> WORD
            "xls", "xlsx", "xlsm", "ods", "csv", "tsv" -> EXCEL
            "ppt", "pptx", "pptm", "odp" -> POWERPOINT
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff", "svg", "heic" -> IMAGE
            "eml", "msg" -> MAIL
            "md", "markdown" -> MARKDOWN
            else -> if (TextFormats.isText(name)) TEXT else OTHER
        }
    }
}

/** An entry of the Dateiverwaltung. */
data class FileItem(
    val key: String,
    val name: String,
    val kind: FileKind,
    val source: FileSource,
    /** The top level of the folder tree: „Dokumente“, „Wissenssammlung“ or a storage location / project folder. */
    val place: String,
    /** Folder below [place]: inside the app (documents), the collection (knowledge), the real folder (files on disk). */
    val folder: String,
    /** Where it is stored, shown in its line (storage location of a document, collection …). */
    val where: String,
    val modified: Long,
    val size: Long = 0,
    val tags: List<String> = emptyList(),
    val path: String? = null,
    val docId: String? = null,
    val versions: Int = 0,
)

/** A hit of the full-text search: the item, a passage around the words, the PDF page it is on. */
data class FileHit(val item: FileItem, val snippet: String, val page: Int?, val score: Int)

class FileLibrary(private val c: AppContainer, context: Context) {
    val meta = ListStore<FileMeta>(FileBackend(File(context.filesDir, "file_meta.json")), FileMeta.serializer(), { it.key })
    private val cacheDir = File(context.cacheDir, "volltext").apply { mkdirs() }
    private val appContext = context

    private val _indexing = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Progress while texts of files are read (done, total), null when idle. */
    val indexing: StateFlow<Pair<Int, Int>?> = _indexing.asStateFlow()

    // ── The catalog ───────────────────────────────────────────────────────────────────────────────────────

    /** Roots of files on disk: storage locations of the editor and project folders (name, directory). */
    fun roots(): List<Pair<String, File>> {
        val vaults = listOf(LORA to c.drive.root) + c.docs.vaults.value.map { it.name to File(it.path) }
        val projects = c.folders.current.mapNotNull { f -> io.github.thepytales.llmhub.agent.WorkFolder.of(c, f.id)?.let { (folder, dir) -> "Projekt ${folder.name}" to dir } }
        return (vaults + projects).filter { (_, d) -> d.isDirectory }.distinctBy { it.second.absolutePath }
    }

    /** Everything, newest first. Walks the folders on disk (call it off the main thread). */
    fun items(): List<FileItem> {
        val metas = meta.current.associateBy { it.key }
        val docs = c.docs.current.map { d ->
            val key = "doc:${d.id}"
            val m = metas[key]
            val real = c.docs.realFile(d)
            val folder = real?.parentFile?.let { c.drive.rel(it) } ?: m?.folder.orEmpty()
            FileItem(
                key, d.title, FileKind.ofDoc(d.docKind), FileSource.APP, if (real != null) LORA else DOCUMENTS, folder,
                if (real != null) LORA else c.docs.vault(d.vault)?.name ?: FileSource.APP.label,
                d.updatedAt, d.tabs.sumOf { it.markdown.length }.toLong(), (m?.tags.orEmpty() + (real?.let { metas[c.drive.metaKey(it)]?.tags }.orEmpty())).distinct(),
                path = real?.absolutePath, docId = d.id, versions = d.versions.size,
            )
        }
        // Mirrors of editor documents in a storage location are the documents themselves.
        val mirrors = c.docs.current.mapNotNull { d -> d.mirror?.let { m -> c.docs.vault(d.vault)?.let { File(it.path, m).absolutePath } } }.toSet() +
            c.docs.current.mapNotNull { it.file }.toSet() + c.docs.current.mapNotNull { d -> c.docs.realFile(d)?.absolutePath }.toSet()
        val disk = roots().flatMap { (name, root) ->
            walk(root).filter { it.absolutePath !in mirrors }.map { f ->
                val key = c.drive.metaKey(f)
                FileItem(
                    key, f.name, FileKind.ofName(f.name), FileSource.DISK, name, f.parentFile?.relativeTo(root)?.path?.replace('\\', '/').orEmpty(), name,
                    f.lastModified(), f.length(), metas[key]?.tags.orEmpty(), path = f.absolutePath,
                )
            }
        }
        val knowledge = c.knowledge.docs.value.map { k ->
            val key = "knowledge:${k.id}"
            val m = metas[key]
            val collection = if (k.collection.isBlank()) "Allgemein" else c.folders.get(k.collection)?.name ?: k.collection
            FileItem(
                key, k.name, FileKind.KNOWLEDGE, FileSource.KNOWLEDGE, FileSource.KNOWLEDGE.label, collection,
                FileSource.KNOWLEDGE.label + " › " + collection, k.addedAt, k.chars.toLong(), m?.tags.orEmpty(),
            )
        }
        return (docs + disk + knowledge).sortedByDescending { it.modified }
    }

    /** Files below [root]: no hidden folders, not the app's own (.adeu), at most [MAX_FILES]. */
    private fun walk(root: File): List<File> {
        val out = mutableListOf<File>()
        fun go(dir: File, depth: Int) {
            if (depth > 8 || out.size >= MAX_FILES) return
            dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { f ->
                if (f.name.startsWith(".") || f.name.startsWith("~$")) return@forEach
                if (f.isDirectory) go(f, depth + 1) else if (out.size < MAX_FILES) out += f
            }
        }
        go(root, 0)
        return out
    }

    // ── Folders and Schlagwörter ──────────────────────────────────────────────────────────────────────────

    fun setTags(key: String, tags: List<String>) {
        val clean = tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val old = meta.get(key) ?: FileMeta(key)
        meta.upsert(old.copy(tags = clean))
    }

    fun setFolder(key: String, folder: String) {
        val clean = folder.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")
        val old = meta.get(key) ?: FileMeta(key)
        meta.upsert(old.copy(folder = clean))
    }

    /** All Schlagwörter in use, most used first. */
    fun allTags(items: List<FileItem>): List<String> =
        items.flatMap { it.tags }.groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { e -> items.firstNotNullOfOrNull { i -> i.tags.firstOrNull { it.lowercase() == e.key } } }

    // ── Texts ─────────────────────────────────────────────────────────────────────────────────────────────

    private val memory = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        var chars = 0L
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            if (chars > MEMORY_CHARS && eldest != null) {
                chars -= eldest.value.length
                return true
            }
            return false
        }
    }

    @Synchronized
    private fun remember(key: String, text: String): String {
        memory.put(key, text)?.let { memory.chars -= it.length }
        memory.chars += text.length
        return text
    }

    @Synchronized
    private fun remembered(key: String): String? = memory[key]

    private fun cacheFile(path: String): File = File(cacheDir, sha1(path) + ".txt")

    /**
     * The text of [item] for search and comparison; files on disk from the cache (read once – with [extract] false only
     * what is already there, so a search in the chat never waits for thousands of PDFs). Null: no text.
     */
    fun text(item: FileItem, extract: Boolean = true): String? = when (item.source) {
        FileSource.APP -> c.docs.get(item.docId)?.let { d -> d.tabs.joinToString("\n\n") { it.markdown } }
        FileSource.KNOWLEDGE -> c.knowledge.docs.value.firstOrNull { "knowledge:${it.id}" == item.key }?.let { k -> c.knowledge.chunks(k).joinToString("\n") { it.text } }
        FileSource.DISK -> item.path?.let { File(it) }?.takeIf { extract || !stale(it) }?.let { cachedText(it) }
    }

    /** Whether the text of a file on disk still has to be read. */
    fun stale(f: File): Boolean {
        val cache = cacheFile(f.absolutePath)
        return !cache.exists() || cache.bufferedReader().use { it.readLine() } != stamp(f)
    }

    private fun stamp(f: File) = "${f.lastModified()} ${f.length()}"

    /** The text of a file on disk (read once, then from the cache) – for previews in the Dateien tab. */
    fun textOf(f: File): String? = runCatching { cachedText(f) }.getOrNull()

    private fun cachedText(f: File): String? {
        val key = f.absolutePath + "|" + stamp(f)
        remembered(key)?.let { return it.ifEmpty { null } }
        val cache = cacheFile(f.absolutePath)
        val text = if (!stale(f)) cache.readText().substringAfter('\n') else extract(appContext, f).orEmpty().also { t ->
            runCatching { cache.writeText(stamp(f) + "\n" + t) }
        }
        return remember(key, text).ifEmpty { null }
    }

    /** Reads the texts of all files on disk that changed since the last time (with [indexing] as progress). */
    fun index(items: List<FileItem>) {
        val todo = items.filter { it.source == FileSource.DISK && it.path != null }.map { File(it.path!!) }.filter { it.isFile && stale(it) }
        if (todo.isEmpty()) return
        todo.forEachIndexed { i, f ->
            _indexing.value = i to todo.size
            runCatching { cachedText(f) }
        }
        _indexing.value = null
    }

    // ── Search ────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Items whose name, Schlagwörter or text contain every word of [query] (case and accents ignored), best first:
     * matches in the name count most, then Schlagwörter, then how often the words occur in the text.
     */
    fun search(query: String, items: List<FileItem>, limit: Int = 100, extract: Boolean = true): List<FileHit> {
        val words = fold(query).split(Regex("\\s+")).map { it.trim('"', '\'', ',', '.') }.filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            val name = fold(item.name)
            val tags = fold(item.tags.joinToString(" "))
            val text = if (words.all { it in name || it in tags }) null else text(item, extract)
            val folded = text?.let(::fold)
            if (!words.all { w -> w in name || w in tags || folded?.contains(w) == true }) return@mapNotNull null
            var score = words.sumOf { w -> (if (w in name) 30 else 0) + (if (w in tags) 20 else 0) }
            var snippet = ""
            var page: Int? = null
            if (text != null && folded != null) {
                score += words.sumOf { w -> Regex(Regex.escape(w)).findAll(folded).take(50).count() }
                // The passage around the first word that is in the text.
                val at = words.mapNotNull { w -> folded.indexOf(w).takeIf { it >= 0 } }.minOrNull()
                if (at != null) {
                    snippet = passage(text, at)
                    if (item.kind == FileKind.PDF) page = PageMarks.pageAt(text, at)
                }
            }
            FileHit(item, snippet, page, score)
        }.sortedWith(compareByDescending<FileHit> { it.score }.thenByDescending { it.item.modified }).take(limit)
    }

    companion object {
        /** The place of the editor's documents in the folder tree. */
        const val DOCUMENTS = "Dokumente"
        /** The Lora-Ordner in the folder tree (AP-16). */
        const val LORA = "Lora-Ordner"
        const val MAX_FILES = 5000
        private const val MEMORY_CHARS = 25_000_000L
        private const val MAX_BYTES = 40L * 1024 * 1024
        private const val MAX_TEXT = 3_000_000

        /** Lower case without accents, one character for one (so positions in the folded text fit the original). */
        fun fold(s: String): String = buildString(s.length) {
            s.forEach { ch ->
                val lower = ch.lowercaseChar()
                append(
                    when (lower) {
                        'ß' -> 's'
                        in 'a'..'z', in '0'..'9', ' ' -> lower
                        else -> Normalizer.normalize(lower.toString(), Normalizer.Form.NFD).firstOrNull() ?: lower
                    },
                )
            }
        }

        /** About 200 characters around [at], cut at word borders, on one line. */
        fun passage(text: String, at: Int): String {
            var from = (at - 80).coerceAtLeast(0)
            var to = (at + 140).coerceAtMost(text.length)
            while (from > 0 && !text[from - 1].isWhitespace() && at - from < 110) from--
            while (to < text.length && !text[to].isWhitespace() && to - at < 170) to++
            return ((if (from > 0) "… " else "") + text.substring(from, to).replace(Regex("\\[Seite \\d+]"), " ").replace(Regex("\\s+"), " ").trim() + if (to < text.length) " …" else "")
        }

        /** Hits for the model (search_files): where each file is, its passage, and how to read it further. */
        fun describe(hits: List<FileHit>): String = if (hits.isEmpty()) "Keine passende Datei gefunden." else hits.joinToString("\n") { h ->
            val i = h.item
            buildString {
                append("- ").append(i.name).append(" (").append(i.kind.label).append(" · ").append(i.where)
                if (i.folder.isNotEmpty() && i.source != FileSource.KNOWLEDGE) append(" › ").append(i.folder)
                append(" · geändert ").append(java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(i.modified))).append(')')
                h.page?.let { append(" · Seite ").append(it) }
                if (i.tags.isNotEmpty()) append(" · ").append(i.tags.joinToString(" ") { "#$it" })
                if (h.snippet.isNotBlank()) append("\n  „").append(h.snippet).append("“")
                when {
                    i.docId != null -> append("\n  Im Editor: read_document mit file=„").append(i.name).append("“")
                    i.path != null -> append("\n  Pfad: ").append(i.path)
                    i.source == FileSource.KNOWLEDGE -> append("\n  In der Wissenssammlung: search_knowledge")
                }
            }
        }

        private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /**
         * The text of a file of any format the app can read: PDF (with page marks), Word, Excel, PowerPoint,
         * OpenDocument, RTF, mails, calendar, EPUB, notebooks and all text formats. Null for pictures and the like.
         */
        fun extract(context: Context?, f: File): String? {
            if (!f.isFile || f.length() > MAX_BYTES) return null
            val name = f.name
            val ext = name.substringAfterLast('.', "").lowercase()
            return runCatching {
                when {
                    // Every page with its mark (also a single one), so a hit knows its page.
                    ext == "pdf" -> PdfExtras.pageTexts(context, f).mapIndexed { i, t -> "[Seite ${i + 1}]\n" + t.trim() }.joinToString("\n\n")
                    OfficeImport.canOpen(name) -> OfficeImport.read(name, f.readBytes())?.tabs?.joinToString("\n\n") { it.second }
                    FormatReaders.handles(name) -> FormatReaders.text(name, f.readBytes())
                    TextFormats.isText(name) -> TextCodec.text(f.readBytes())
                    else -> null
                }
            }.getOrNull()?.take(MAX_TEXT)
        }
    }
}
