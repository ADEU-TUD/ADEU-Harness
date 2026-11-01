package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.files.OfficeEdit
import io.github.thepytales.llmhub.logFailure
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Editor documents as real files in the Lora-Ordner (AP-16): a document is a .docx, a presentation a .pptx, a table an
 * .xlsx, Markdown an .md – they open in Word, PowerPoint and Excel. The editor keeps versions, comments and the chat in
 * its data (.lora); the file is written a moment after each change.
 *
 * Verlustfrei: a file that came from outside (or was changed in Word) is kept as original. Changes of the text in the
 * editor are then written *into* that original – paragraph by paragraph, cell by cell – so its formatting, styles,
 * headers and everything the editor does not know stay. Only when the structure changes (new or removed blocks, other
 * formatting) the file is written anew from the editor (the original stays in .lora/originale as backup).
 */
class DocFiles(private val store: DocsStore, dataDir: File, private val cache: File, private val theme: (LiveDoc) -> DocTheme) {
    private val originals = File(dataDir, "originale")
    private val json = Json { ignoreUnknownKeys = true }
    private val tabsSer = ListSerializer(DocTab.serializer())
    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Lora-Dateien").apply { isDaemon = true } }
    private val pending = HashMap<String, ScheduledFuture<*>>()

    /** What happened at the last write of a document (for the editor's status line). */
    @Volatile
    var lastWrite: Pair<String, Boolean>? = null
        private set

    /** Writes the file of [doc] in a moment (changes in between are written together). */
    @Synchronized
    fun schedule(doc: LiveDoc, delayMs: Long = 1200) {
        pending.remove(doc.id)?.cancel(false)
        pending[doc.id] = executor.schedule({
            synchronized(this) { pending.remove(doc.id) }
            store.get(doc.id)?.let { runCatching { write(it) }.logFailure("Datei des Dokuments schreiben") }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /** Writes every pending file now (before the app ends or the folder moves). */
    fun flush() {
        val ids = synchronized(this) { pending.keys.toList().also { pending.values.forEach { f -> f.cancel(false) }; pending.clear() } }
        ids.forEach { id -> store.get(id)?.let { runCatching { write(it) } } }
    }

    // ── Writing ───────────────────────────────────────────────────────────────────────────────────────────

    /** Writes the real file of [doc] now; true if it was written into the original (formatting kept). */
    fun write(doc: LiveDoc): Boolean {
        val file = store.realFile(doc) ?: return false
        val ext = file.extension.lowercase()
        // Open suggestions of „Änderungen nachverfolgen“ are written as Word revisions: from the editor, not into the original.
        val original = original(doc)?.takeIf { !(ext == "docx" && DocxExtras.hasRevisions(doc)) }
        var patched = original?.let { (bytes, base) -> runCatching { patch(doc, ext, bytes, base) }.getOrNull() }
        // Comments written in the editor since go into the original as Word comments.
        if (patched != null && ext == "docx") {
            val fresh = doc.comments.filter { !it.resolved && it.wordId == null }
            if (fresh.isNotEmpty()) {
                val (withComments, ids) = DocxExtras.withComments(patched, fresh)
                patched = withComments
                noteWordIds(doc.id, ids)
            }
        }
        val bytes = patched ?: generate(doc, ext)
        file.parentFile?.mkdirs()
        AtomicFiles.writeBytes(file, bytes)
        store.markWritten(file)
        if (patched != null) keepOriginal(doc.id, file.extension.lowercase(), bytes, doc.tabs)
        else if (original != null) dropOriginal(doc.id)
        lastWrite = doc.id to (patched != null)
        return patched != null
    }

    /** The file content made from the editor (the document's own format). */
    fun generate(doc: LiveDoc, ext: String = doc.docKind.fileExt): ByteArray {
        if (ext == "md" || doc.docKind == DocKind.MARKDOWN) return doc.markdown().toByteArray()
        val tmp = File(cache, "datei-${doc.id}.$ext").apply { parentFile?.mkdirs() }
        try {
            when (doc.docKind) {
                DocKind.DOC -> noteWordIds(doc.id, DocxExtras.write(tmp, doc, theme(doc), store.baseDir(doc)))
                DocKind.SLIDES -> SlideFiles.write(tmp, doc, theme(doc), store.baseDir(doc))
                DocKind.SHEET -> XlsxWriter.writeTabs(tmp, doc.tabs.map { t -> t.title to t.markdown })
                DocKind.MARKDOWN -> tmp.writeText(doc.markdown())
            }
            return tmp.readBytes()
        } finally {
            tmp.delete()
        }
    }

    /**
     * The original with the text changes of [doc] since [base] written into it; null if that is not possible (the
     * structure changed) – then the file is written anew.
     */
    internal fun patch(doc: LiveDoc, ext: String, original: ByteArray, base: List<DocTab>): ByteArray? {
        if (doc.tabs.map { it.markdown } == base.map { it.markdown }) return original
        return when (ext) {
            "docx", "pptx" -> {
                if (doc.tabs.size != base.size) return null
                var bytes = original
                doc.tabs.zip(base).forEach { (now, was) ->
                    for ((old, new) in textChanges(was.markdown, now.markdown, slides = ext == "pptx") ?: return null) {
                        bytes = OfficeEdit.replace(ext, bytes, old, new).bytes
                    }
                }
                bytes
            }
            "xlsx", "xlsm" -> {
                if (doc.tabs.map { it.title } != base.map { it.title }) return null
                var bytes = original
                doc.tabs.zip(base).forEach { (now, was) ->
                    if (SheetMeta.parse(now.markdown) != SheetMeta.parse(was.markdown)) return null
                    val a = SheetEngine.grid(was.markdown)
                    val b = SheetEngine.grid(now.markdown)
                    val cells = LinkedHashMap<String, String>()
                    for (r in 0 until maxOf(a.size, b.size)) for (col in 0 until maxOf(a.getOrNull(r)?.size ?: 0, b.getOrNull(r)?.size ?: 0)) {
                        val x = a.getOrNull(r)?.getOrNull(col).orEmpty()
                        val y = b.getOrNull(r)?.getOrNull(col).orEmpty()
                        if (x != y) cells[SheetEngine.ref(r, col)] = y
                    }
                    if (cells.isNotEmpty()) bytes = OfficeEdit.setCells(bytes, now.title, cells).bytes
                }
                bytes
            }
            else -> null
        }
    }

    /** Remembers the Word ids of comments written into the file (without a new version). */
    private fun noteWordIds(docId: String, ids: Map<String, String>) {
        if (ids.isEmpty()) return
        val d = store.get(docId) ?: return
        if (d.comments.all { c -> ids[c.id] == null || c.wordId == ids[c.id] }) return
        store.saveQuietly(d.copy(comments = d.comments.map { c -> ids[c.id]?.let { c.copy(wordId = it) } ?: c }))
    }

    // ── Originals ─────────────────────────────────────────────────────────────────────────────────────────

    private fun originalFile(id: String) = originals.listFiles { f -> f.name.startsWith("$id.") && !f.name.endsWith(".json") }.orEmpty().firstOrNull()

    private fun original(doc: LiveDoc): Pair<ByteArray, List<DocTab>>? {
        val f = originalFile(doc.id) ?: return null
        val base = runCatching { json.decodeFromString(tabsSer, File(originals, "${doc.id}.json").readText()) }.getOrNull() ?: return null
        return f.readBytes() to base
    }

    /** Remembers [bytes] as the original of [docId] with the editor text [tabs] it corresponds to. */
    fun keepOriginal(docId: String, ext: String, bytes: ByteArray, tabs: List<DocTab>) {
        if (ext !in setOf("docx", "pptx", "xlsx", "xlsm")) return
        runCatching {
            originals.mkdirs()
            originalFile(docId)?.takeIf { it.extension != ext }?.delete()
            AtomicFiles.writeBytes(File(originals, "$docId.$ext"), bytes)
            AtomicFiles.write(File(originals, "$docId.json"), json.encodeToString(tabsSer, tabs))
        }.logFailure("Original des Dokuments merken")
    }

    fun dropOriginal(docId: String) {
        originalFile(docId)?.delete()
        File(originals, "$docId.json").delete()
    }

    /** Whether [doc] is written into its original (formatting of Word/PowerPoint/Excel kept). */
    fun hasOriginal(doc: LiveDoc) = originalFile(doc.id) != null

    // ── Reading back ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * The file of [doc] was changed outside the app (Word, Excel, another PC): its content as new version; the file
     * becomes the original, so the next changes in the editor are written into it.
     */
    fun reload(doc: LiveDoc, file: File): LiveDoc? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val imported = runCatching { OfficeImport.read(file.name, bytes) }.getOrNull() ?: return null
        val tabs = imported.tabs.mapIndexed { i, (t, md) -> doc.tabs.getOrNull(i)?.copy(title = t, markdown = md) ?: DocTab(title = t, markdown = md) }
        val base = store.baseDir(doc)
        imported.media.forEach { (path, data) -> runCatching { File(base, path).apply { parentFile?.mkdirs() }.writeBytes(data) } }
        keepOriginal(doc.id, file.extension.lowercase(), bytes, tabs)
        val withWord = if (file.extension.equals("docx", true)) DocxExtras.adopt(doc.copy(tabs = tabs), bytes) else doc.copy(tabs = tabs)
        if (tabs.map { it.markdown } == doc.tabs.map { it.markdown } && withWord.comments == doc.comments && withWord.trackBase == doc.trackBase) return null
        return doc.change("Extern", "Außerhalb der App geändert (${file.name})") { withWord.copy(versions = it.versions) }
    }

    companion object {
        /**
         * Pairs of plain texts (old → new) that changed between [old] and [new], block by block – paragraphs, headings,
         * list items, table cells; null if the structure changed (blocks added, removed, other kinds, other formatting).
         */
        fun textChanges(old: String, new: String, slides: Boolean = false): List<Pair<String, String>>? {
            if (slides) {
                val a = SlideDeck.split(old)
                val b = SlideDeck.split(new)
                if (a.size != b.size) return null
                return a.zip(b).flatMap { (x, y) -> textChanges(x, y) ?: return null }
            }
            val a = DocBlocks.split(old)
            val b = DocBlocks.split(new)
            if (a.size != b.size) return null
            val out = mutableListOf<Pair<String, String>>()
            for ((x, y) in a.zip(b)) {
                if (x == y) continue
                if (DocBlocks.kindOf(x) != DocBlocks.kindOf(y)) return null
                val xl = x.lines()
                val yl = y.lines()
                if (xl.size != yl.size) return null
                for ((l1, l2) in xl.zip(yl)) {
                    if (l1 == l2) continue
                    if (l1.trim().startsWith("|")) {
                        val c1 = l1.trim().trim('|').split('|')
                        val c2 = l2.trim().trim('|').split('|')
                        if (c1.size != c2.size) return null
                        c1.zip(c2).forEach { (p, q) -> if (p != q) out += plainLine(p) to plainLine(q) }
                        continue
                    }
                    // Only text changes: the same marks (list, heading, bold …) around it.
                    if (marks(l1) != marks(l2)) return null
                    out += plainLine(l1) to plainLine(l2)
                }
            }
            return out.filter { (p, q) -> p != q }.map { (p, q) ->
                if (p.isBlank()) return null
                p to q
            }
        }

        private val PREFIX = Regex("^\\s*(#{1,6}\\s+|>\\s*(\\[![A-Z]+]\\s*)?|[-*+]\\s+(\\[[ xX]]\\s+)?|\\d+[.)]\\s+)")

        /** The formatting marks of a line (to tell a text change from a formatting change). */
        private fun marks(line: String): String = PREFIX.find(line)?.value?.replace(Regex("\\w"), "").orEmpty() + line.filter { it in "*_`~[]()" }

        private fun plainLine(line: String): String = line.replace(PREFIX, "").replace(Regex("\\*\\*|__|\\*|_|`|~~"), "")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1").trim()
    }
}
