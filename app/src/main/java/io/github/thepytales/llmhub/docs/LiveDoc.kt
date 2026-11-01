package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import io.github.thepytales.llmhub.ui.trLater

/** A tab of a document, like a section of a notebook; exported one after another or on its own. */
@Serializable
data class DocTab(val id: String = UUID.randomUUID().toString(), val title: String = "Tab 1", val markdown: String = "")

@Serializable
data class DocReply(val author: String, val text: String, val createdAt: Long = System.currentTimeMillis(), val ai: Boolean = false)

/** A comment on a passage ([quote]) of a tab; with [toAi] the model answers in the thread and makes the change. */
@Serializable
data class DocComment(
    val id: String = UUID.randomUUID().toString().take(8),
    val tabId: String,
    /** The commented text (a block or a selection), to find the place again after edits. */
    val quote: String,
    val text: String,
    val author: String = "Sie",
    val toAi: Boolean = false,
    val resolved: Boolean = false,
    val replies: List<DocReply> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Id of the Word comment it came from or was written as (AP-16), so it is not written twice. */
    val wordId: String? = null,
)

/** A saved state of the whole document; every change is attributed to the person or the model that made it. */
@Serializable
data class DocVersion(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    /** "Sie" or "KI · Modellname". */
    val author: String,
    val note: String = "",
    val title: String,
    val tabs: List<DocTab>,
) {
    val byAi: Boolean get() = author.startsWith("KI")
}

/** What a living document is: text (Word), slides (PowerPoint) or tables (Excel) – one editor, three bodies. */
enum class DocKind(val label: String, val newTitle: String, val ext: String) {
    DOC(trLater("Dokument"), "Unbenanntes Dokument", "md"),
    SLIDES(trLater("Präsentation"), "Unbenannte Präsentation", "md"),
    SHEET(trLater("Tabelle"), "Unbenannte Tabelle", "md"),
    /** Plain Markdown with its marks visible (like Obsidian or a README), with a preview beside it. */
    MARKDOWN(trLater("Markdown-Datei"), "Unbenannte Markdown-Datei", "md"),
    ;

    /** Format of its real file in the Lora-Ordner (AP-16). */
    val fileExt: String get() = when (this) { DOC -> "docx"; SLIDES -> "pptx"; SHEET -> "xlsx"; MARKDOWN -> "md" }

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DOC

        /** Kind for a file the model created or the user opened. */
        fun forFile(name: String): DocKind = when (name.substringAfterLast('.', "").lowercase()) {
            "pptx", "ppt", "odp", "key" -> SLIDES
            "xlsx", "xls", "xlsm", "ods", "csv", "tsv" -> SHEET
            "md", "markdown" -> MARKDOWN
            else -> DOC
        }
    }
}

/**
 * A living document (like Claude Docs): written by the model in the chat, edited directly by the user, commented
 * and exported as PDF, Word, Markdown, HTML or PowerPoint. The tabs hold Markdown, which the app renders for
 * editing and for the documents.
 */
@Serializable
data class LiveDoc(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tabs: List<DocTab> = listOf(DocTab()),
    /** Look of exported documents: enum name of [DocStyle]; null = settings. */
    val style: String? = null,
    /** Chat the document belongs to; comments for the model go there. */
    val chatId: String? = null,
    /** Last file the model or the user exported (PDF, Word …), shown in the chat. */
    val file: String? = null,
    val versions: List<DocVersion> = emptyList(),
    val comments: List<DocComment> = emptyList(),
    /** Comments go to the model by default (remembered per document, like the checkbox in Claude Docs). */
    val sendToAi: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Who changed it last: "Sie" or "KI · …". */
    val lastAuthor: String = "Sie",
    /** Enum name of [DocKind]. */
    val kind: String = DocKind.DOC.name,
    /** Storage folder ([DocVault] id); null = inside the app. */
    val vault: String? = null,
    /** Pinned to the top of the document list. */
    val starred: Boolean = false,
    /** File name of the Markdown copy in the vault. */
    val mirror: String? = null,
    /** Lora-Ordner (AP-16): path of the real file (.docx, .pptx, .xlsx, .md) inside it, with „/“. */
    val path: String? = null,
    /**
     * Änderungen nachverfolgen (AP-16): the text of each tab (by id) when tracking began or a change was last accepted –
     * everything since is shown as suggestion to accept or reject (and written as Word revisions). null: off.
     */
    val trackBase: Map<String, String>? = null,
) {
    val docKind: DocKind get() = DocKind.of(kind)

    fun tab(id: String?): DocTab = tabs.firstOrNull { it.id == id } ?: tabs.first()

    /** All tabs as one Markdown text (for export and for the model); tabs from the second on start with their title. */
    fun markdown(tabId: String? = null): String = if (tabId != null) tab(tabId).markdown
    else if (tabs.size == 1) tabs.first().markdown
    else tabs.joinToString("\n\n") { t -> if (t == tabs.first()) t.markdown else "## ${t.title}\n\n${t.markdown}" }

    val openComments: Int get() = comments.count { !it.resolved }

    val words: Int get() = tabs.sumOf { t -> t.markdown.split(Regex("\\s+")).count { w -> w.any(Char::isLetterOrDigit) } }

    /**
     * New content with a version entry. Changes of the same person within [MERGE_MS] become one version, so typing
     * does not fill the history.
     */
    fun change(author: String, note: String = "", transform: (LiveDoc) -> LiveDoc): LiveDoc {
        val next = transform(this)
        if (next.tabs == tabs && next.title == title) return next
        val now = System.currentTimeMillis()
        val last = versions.lastOrNull()
        val version = DocVersion(author = author, note = note, title = next.title, tabs = next.tabs)
        val list = if (last != null && last.author == author && !last.byAi && now - last.createdAt < MERGE_MS && note.isBlank()) {
            versions.dropLast(1) + version.copy(id = last.id, createdAt = now)
        } else versions + version
        return next.copy(versions = list.takeLast(MAX_VERSIONS), updatedAt = now, lastAuthor = author)
    }

    companion object {
        const val MERGE_MS = 3 * 60_000L
        const val MAX_VERSIONS = 60

        fun create(title: String, markdown: String, author: String, chatId: String? = null, style: String? = null, kind: DocKind = DocKind.DOC): LiveDoc {
            val tab = DocTab(title = if (kind == DocKind.SHEET) "Tabelle 1" else "Tab 1", markdown = markdown)
            return LiveDoc(
                title = title.ifBlank { DocBlocks.titleOf(markdown) ?: kind.newTitle },
                tabs = listOf(tab), chatId = chatId, style = style, lastAuthor = author, kind = kind.name,
                versions = listOf(DocVersion(author = author, note = "Erstellt", title = title, tabs = listOf(tab))),
            )
        }
    }
}

/** A folder outside the app where documents live as readable files (like an Obsidian vault), e.g. in Google Drive. */
@Serializable
data class DocVault(val id: String = UUID.randomUUID().toString().take(8), val name: String, val path: String)

@Serializable
private data class VaultConfig(val vaults: List<DocVault> = emptyList(), val defaultVault: String? = null)

/**
 * The living documents, one JSON file each (versions make them large; the list stays fast). Documents in a
 * [DocVault] keep their JSON in "<Ordner>/.adeu" and a Markdown copy "<Titel>.md" next to it, so sync tools
 * (Google Drive, OneDrive, Nextcloud) carry them to other computers and other editors (Obsidian) can read them.
 * [refresh] picks up what changed there: edited Markdown copies, documents of other computers, new .md files.
 */
class DocsStore(dir: File) {
    private val folder = File(dir, "livedocs").apply { mkdirs() }
    private val configFile = File(dir, "doc_vaults.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var config: VaultConfig = runCatching { json.decodeFromString(VaultConfig.serializer(), AtomicFiles.read(configFile) ?: "{}") }.getOrDefault(VaultConfig())
    private val _vaults = MutableStateFlow(config.vaults)
    val vaults: StateFlow<List<DocVault>> = _vaults.asStateFlow()
    private val _defaultVault = MutableStateFlow(config.defaultVault)
    /** Id of the vault for new documents (observable for the settings). */
    val defaultVaultId: StateFlow<String?> = _defaultVault.asStateFlow()

    /** Last modification time of each file as written or read by this app (to recognize changes from outside). */
    private val known = HashMap<String, Long>()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<LiveDoc>> = _items.asStateFlow()
    val current: List<LiveDoc> get() = _items.value

    /** Vault for new documents; null = inside the app. */
    val defaultVault: String? get() = config.defaultVault?.takeIf { id -> config.vaults.any { it.id == id } }

    private fun metaDir(v: DocVault) = File(v.path, ".adeu")

    private fun read(f: File): LiveDoc? = runCatching { json.decodeFromString(LiveDoc.serializer(), AtomicFiles.read(f) ?: return null) }
        .getOrElse { e -> AtomicFiles.quarantine(f, e); null }

    private fun load(): List<LiveDoc> {
        val own = folder.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull(::read)
        return (own + config.vaults.flatMap { loadVault(it) }).distinctBy { it.id }.sortedByDescending { it.updatedAt }
    }

    private fun loadVault(v: DocVault): List<LiveDoc> {
        val meta = metaDir(v)
        return meta.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            known[f.path] = f.lastModified()
            read(f)?.copy(vault = v.id)?.also { d -> mirror(v, d)?.let { m -> if (m.exists()) known[m.path] = m.lastModified() } }
        }
    }

    fun get(id: String?): LiveDoc? = id?.let { i -> _items.value.firstOrNull { it.id == i } }

    /** The document whose last export is [path]. */
    fun forFile(path: String?): LiveDoc? = path?.let { p -> _items.value.firstOrNull { it.file == p } }

    fun forChat(chatId: String): List<LiveDoc> = _items.value.filter { it.chatId == chatId }

    fun vault(id: String?): DocVault? = id?.let { i -> config.vaults.firstOrNull { it.id == i } }

    /**
     * Folder that the pictures of [doc] are relative to (Markdown "![…](Bilder/…)"): the vault next to its Markdown copy
     * (so Obsidian and other editors show them too), otherwise the app's document folder.
     */
    fun baseDir(doc: LiveDoc?): File = vault(doc?.vault)?.let { File(it.path) } ?: folder

    /** File name of the Markdown copy in a vault. */
    private fun mirrorName(d: LiveDoc) = d.title.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "Dokument" } +
        (if (d.docKind == DocKind.SLIDES || d.docKind == DocKind.SHEET) " (${d.docKind.label})" else "") + ".md"

    private fun mirror(v: DocVault, d: LiveDoc): File? = d.mirror?.let { File(v.path, it) }

    // ── Lora-Ordner (AP-16): documents without a storage location are real files there ──

    /** The visible Lora-Ordner; null: documents stay inside the app (tests, before the container set it). */
    @Volatile
    var root: File? = null

    /** Writes the real files (set by the container). */
    @Volatile
    var files: DocFiles? = null

    /** Moves a real file into the Papierkorb of the Lora-Ordner (set by the container). */
    @Volatile
    var trashFile: ((File) -> Unit)? = null

    /** Documents that stay inside the app – those of Tresor chats (AP-10); set by the container. */
    @Volatile
    var keepInApp: (LiveDoc) -> Boolean = { false }

    /** Folder for documents made in a chat („Erstellt“). */
    var createdFolder = "Erstellt"

    /** The real file of [doc] in the Lora-Ordner, if it has one. */
    fun realFile(doc: LiveDoc): File? = doc.path?.let { p -> root?.let { File(it, p) } }

    /** The document whose real file is [f]. */
    fun forRealFile(f: File): LiveDoc? {
        val target = f.absoluteFile.normalize()
        return _items.value.firstOrNull { d -> realFile(d)?.absoluteFile?.normalize() == target }
    }

    /** The written file is the app's own: not read back as change from outside. */
    fun markWritten(f: File) {
        known[f.absolutePath] = f.lastModified()
    }

    private fun relPath(f: File): String? {
        val r = root?.absoluteFile?.normalize()?.path ?: return null
        val p = f.absoluteFile.normalize().path
        return if (p.startsWith(r + File.separator)) p.substring(r.length + 1).replace(File.separatorChar, '/') else null
    }

    /** File name of a document: its title and the format of its kind. */
    fun fileName(doc: LiveDoc): String {
        val base = doc.title.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), " ").replace(Regex("\\s+"), " ").trim().trim('.').take(80).ifBlank { doc.docKind.newTitle }
        return "$base.${doc.docKind.fileExt}"
    }

    /** A free path for [doc] in [folder] (relative, „“ = top level). */
    private fun freePath(doc: LiveDoc, folder: String): String {
        val r = root ?: return fileName(doc)
        val dir = if (folder.isEmpty()) r else File(r, folder)
        val name = fileName(doc)
        var f = File(dir, name)
        var n = 2
        val taken = { file: File -> file.exists() || _items.value.any { it.id != doc.id && realFile(it)?.absoluteFile == file.absoluteFile } }
        while (taken(f)) f = File(dir, name.substringBeforeLast('.') + " ($n)." + doc.docKind.fileExt).also { n++ }
        return relPath(f) ?: name
    }

    /**
     * The path of [doc]: a new document gets one (made in a chat: „Erstellt“, otherwise [folder] or the top level); a
     * changed title renames the file, like in Google Drive.
     */
    private fun withPath(doc: LiveDoc, folder: String? = null): LiveDoc {
        val r = root ?: return doc
        if (doc.vault != null) return doc.copy(path = null)
        if (doc.path == null && keepInApp(doc)) return doc
        val current = doc.path
        if (current == null) {
            val dir = folder ?: if (doc.chatId != null) createdFolder else ""
            return doc.copy(path = freePath(doc, dir))
        }
        val file = File(r, current)
        val wanted = fileName(doc)
        if (file.name == wanted || file.nameWithoutExtension.replace(Regex(" \\(\\d+\\)$"), "") == wanted.substringBeforeLast('.')) return doc
        val dir = current.substringBeforeLast('/', "")
        val next = freePath(doc, dir)
        if (file.isFile) runCatching { java.nio.file.Files.move(file.toPath(), File(r, next).toPath()) }.onFailure { return doc }
        return doc.copy(path = next)
    }

    /** Saves a new document into [folder] of the Lora-Ordner (the Dateien tab's „+“ in an open folder). */
    fun saveIn(doc: LiveDoc, folder: String) = save(withPath(doc.copy(path = null, vault = null), folder))

    /** A file or folder of the Lora-Ordner moved from [oldAbs] to [newAbs] (Dateien tab): the documents follow. */
    @Synchronized
    fun relocated(oldAbs: String, newAbs: String) {
        val old = File(oldAbs).absoluteFile.normalize().path
        val now = File(newAbs)
        _items.value.filter { d -> realFile(d)?.absoluteFile?.normalize()?.path?.let { it == old || it.startsWith(old + File.separator) } == true }.forEach { d ->
            val abs = realFile(d)!!.absoluteFile.normalize().path
            val moved = if (abs == old) now else File(now, abs.removePrefix(old + File.separator))
            val rel = relPath(moved) ?: return@forEach
            // A file renamed in the Dateien tab gives the document its name.
            val title = if (abs == old && moved.nameWithoutExtension != File(abs).nameWithoutExtension) moved.nameWithoutExtension else d.title
            val next = d.copy(path = rel, title = title)
            known[moved.absolutePath] = moved.lastModified()
            _items.update { list -> list.map { if (it.id == d.id) next else it } }
            persistOwn(next)
        }
    }

    /** Removes [id] from the list and its data (the file itself went to the Papierkorb). */
    @Synchronized
    fun forget(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        File(folder, "$id.json").delete()
    }

    /** Takes [doc] back (restored from the Papierkorb) with its file [f]. */
    fun adopt(doc: LiveDoc, f: File) {
        val rel = relPath(f)
        val d = doc.copy(path = rel, vault = null)
        known[f.absolutePath] = f.lastModified()
        _items.update { list -> (listOf(d) + list.filterNot { it.id == d.id }).sortedByDescending { it.updatedAt } }
        persistOwn(d)
    }

    /** Stores [d] without a new version and without writing its file again (ids of Word comments). */
    @Synchronized
    fun saveQuietly(d: LiveDoc) {
        _items.update { list -> list.map { if (it.id == d.id) d else it } }
        if (d.vault == null) persistOwn(d) else vault(d.vault)?.let { persist(it, d) }
    }

    fun encode(d: LiveDoc): String = json.encodeToString(LiveDoc.serializer(), d)

    fun decode(text: String): LiveDoc? = runCatching { json.decodeFromString(LiveDoc.serializer(), text) }.getOrNull()

    private fun persistOwn(d: LiveDoc) {
        runCatching { AtomicFiles.write(File(folder, "${d.id}.json"), json.encodeToString(LiveDoc.serializer(), d)) }.logFailure("Dokument speichern")
    }

    /** Documents of before AP-16 (inside the app) become files in the Lora-Ordner: AP-18 folders become real folders. */
    fun migrateToFiles(folderOf: (LiveDoc) -> String) {
        val r = root ?: return
        _items.value.filter { it.vault == null && it.path == null && !keepInApp(it) }.forEach { d ->
            val dir = folderOf(d).ifBlank { if (d.chatId != null) createdFolder else "" }
            val next = d.copy(path = freePath(d, dir))
            File(r, dir).mkdirs()
            _items.update { list -> list.map { if (it.id == d.id) next else it } }
            persistOwn(next)
            files?.let { f -> runCatching { f.write(next) }.logFailure("Dokument als Datei ablegen") }
        }
    }

    /** Opens [f] of the Lora-Ordner in the editor: the document it already is, or a new one that writes into it. */
    fun openReal(f: File, imported: OfficeImport.Imported): LiveDoc {
        forRealFile(f)?.let { return it }
        val bytes = f.readBytes()
        val plain = OfficeImport.toLiveDoc(imported).copy(path = relPath(f), title = f.nameWithoutExtension, updatedAt = f.lastModified())
        // Word comments and revisions come along (AP-16).
        val doc = if (f.extension.equals("docx", true)) DocxExtras.adopt(plain, bytes) else plain
        files?.keepOriginal(doc.id, f.extension.lowercase(), bytes, doc.tabs)
        known[f.absolutePath] = f.lastModified()
        _items.update { list -> (listOf(doc) + list).sortedByDescending { it.updatedAt } }
        persistOwn(doc)
        return doc
    }

    /** Picks up real files changed outside the app (Word, Excel, another PC); files removed there end their document. */
    @Synchronized
    fun refreshFiles() {
        val r = root ?: return
        if (!r.isDirectory) return
        val f = files ?: return
        for (d in _items.value.filter { it.path != null && it.vault == null }) {
            val file = realFile(d) ?: continue
            if (!file.exists()) {
                // Removed or moved in the Explorer (only when its folder is there – not a missing drive).
                if (file.parentFile?.isDirectory == true && known.containsKey(file.absolutePath)) forget(d.id)
                continue
            }
            val stamp = file.lastModified()
            val seen = known[file.absolutePath]
            if (seen == null) {
                known[file.absolutePath] = stamp
                continue
            }
            if (seen == stamp) continue
            known[file.absolutePath] = stamp
            val next = f.reload(d, file) ?: continue
            _items.update { list -> (listOf(next) + list.filterNot { it.id == d.id }).sortedByDescending { it.updatedAt } }
            persistOwn(next)
        }
    }

    @Synchronized
    fun save(doc0: LiveDoc) {
        val doc = withPath(doc0)
        val v = vault(doc.vault)
        val stored = if (v == null) doc.copy(vault = null, mirror = null) else writeMirror(v, doc)
        _items.update { list -> (listOf(stored) + list.filterNot { it.id == stored.id }).sortedByDescending { it.updatedAt } }
        runCatching {
            val f = if (v == null) File(folder, "${stored.id}.json") else File(metaDir(v).apply { mkdirs() }, "${stored.id}.json")
            AtomicFiles.write(f, json.encodeToString(LiveDoc.serializer(), stored))
            known[f.path] = f.lastModified()
        }.logFailure("Dokument speichern")
        if (stored.path != null && stored.vault == null) files?.schedule(stored)
    }

    /** Writes the readable copy "<Titel>.md" (renamed with the title); returns the document with its name. */
    private fun writeMirror(v: DocVault, doc: LiveDoc): LiveDoc {
        val dir = File(v.path).apply { mkdirs() }
        val old = doc.mirror
        var name = old.takeIf { it != null && it.removeSuffix(".md").startsWith(mirrorName(doc).removeSuffix(".md")) } ?: mirrorName(doc)
        // Another document already uses that name: add a number.
        var k = 2
        while (_items.value.any { it.id != doc.id && it.vault == v.id && it.mirror.equals(name, true) }) name = mirrorName(doc).removeSuffix(".md") + " ${k++}.md"
        val target = File(dir, name)
        runCatching {
            val text = doc.markdown()
            if (!target.exists() || target.readText() != text) AtomicFiles.write(target, text)
            known[target.path] = target.lastModified()
            if (old != null && !old.equals(name, true)) File(dir, old).delete()
        }.logFailure("Markdown-Kopie speichern")
        return doc.copy(vault = v.id, mirror = name)
    }

    fun update(id: String, transform: (LiveDoc) -> LiveDoc): LiveDoc? {
        val doc = get(id) ?: return null
        return transform(doc).also { save(it) }
    }

    @Synchronized
    fun delete(id: String) {
        val doc = get(id)
        // A real file of the Lora-Ordner goes into its Papierkorb (with the document, restorable there).
        doc?.let { realFile(it) }?.takeIf { it.isFile && trashFile != null }?.let { f ->
            trashFile?.invoke(f)
            _items.update { list -> list.filterNot { it.id == id } }
            File(folder, "$id.json").delete()
            return
        }
        deleteData(id)
    }

    /** Removes the document's data (not its real file). */
    @Synchronized
    private fun deleteData(id: String) {
        val doc = get(id)
        _items.update { list -> list.filterNot { it.id == id } }
        File(folder, "$id.json").delete()
        vault(doc?.vault)?.let { v ->
            File(metaDir(v), "$id.json").delete()
            mirror(v, doc!!)?.delete()
        }
    }

    /** Moves a document into [vaultId] (null = back into the app). */
    @Synchronized
    fun move(id: String, vaultId: String?) {
        val doc = get(id) ?: return
        if (doc.vault == vaultId) return
        val from = baseDir(doc)
        val real = realFile(doc)
        deleteData(id)
        real?.delete()
        val moved = doc.copy(vault = vaultId, mirror = null, path = null)
        // The pictures go along, so the relative paths stay valid.
        val to = baseDir(moved)
        if (from.absoluteFile != to.absoluteFile) DocMarkup.imagePaths(doc.markdown()).forEach { rel ->
            runCatching { File(from, rel).takeIf { it.isFile }?.copyTo(File(to, rel), overwrite = false) }
        }
        save(moved)
    }

    // ── Vaults ──

    private fun saveConfig() {
        runCatching { AtomicFiles.write(configFile, json.encodeToString(VaultConfig.serializer(), config)) }.logFailure("Ablageorte speichern")
        _vaults.value = config.vaults
        _defaultVault.value = defaultVault
    }

    /** Adds [path] as vault; documents already there (from another computer) appear right away. */
    @Synchronized
    fun addVault(name: String, path: String, makeDefault: Boolean): DocVault {
        config.vaults.firstOrNull { File(it.path).absoluteFile == File(path).absoluteFile }?.let { return it }
        val v = DocVault(name = name.ifBlank { File(path).name.ifBlank { "Ablageort" } }, path = File(path).absolutePath)
        config = config.copy(vaults = config.vaults + v, defaultVault = if (makeDefault) v.id else config.defaultVault)
        saveConfig()
        refresh()
        return v
    }

    @Synchronized
    fun setDefaultVault(id: String?) {
        config = config.copy(defaultVault = id)
        saveConfig()
    }

    /** Removes the vault from the app; its files stay in the folder. */
    @Synchronized
    fun removeVault(id: String) {
        config = config.copy(vaults = config.vaults.filterNot { it.id == id }, defaultVault = config.defaultVault.takeIf { it != id })
        saveConfig()
        _items.update { list -> list.filterNot { it.vault == id } }
    }

    /**
     * Picks up changes made outside the app: documents of other computers (JSON), edited Markdown copies and new
     * .md files in the vault. Cheap enough to call every few seconds while documents are on screen.
     */
    @Synchronized
    fun refresh() {
        val vaults = config.vaults
        if (vaults.isEmpty()) return
        var list = _items.value
        var changed = false
        for (v in vaults) {
            val dir = File(v.path)
            if (!dir.isDirectory) continue
            val metas = metaDir(v).listFiles { f -> f.extension == "json" }.orEmpty().associateBy { it.nameWithoutExtension }
            // Documents removed on another computer.
            list.filter { it.vault == v.id && it.id !in metas }.forEach { gone -> list = list.filterNot { it.id == gone.id }; changed = true }
            for ((id, f) in metas) {
                if (known[f.path] == f.lastModified() && list.any { it.id == id }) continue
                val d = read(f)?.copy(vault = v.id) ?: continue
                known[f.path] = f.lastModified()
                list = listOf(d) + list.filterNot { it.id == id }
                mirror(v, d)?.let { m -> if (m.exists()) known[m.path] = m.lastModified() }
                changed = true
            }
            // Markdown copies edited outside (Obsidian, Word, another editor) and new .md files.
            val names = list.filter { it.vault == v.id }.associateBy { it.mirror?.lowercase() }
            dir.listFiles { f -> f.isFile && f.extension.equals("md", true) }.orEmpty().forEach { f ->
                if (known[f.path] == f.lastModified()) return@forEach
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                known[f.path] = f.lastModified()
                val doc = names[f.name.lowercase()]
                if (doc != null) {
                    if (doc.tabs.size == 1 && text != doc.markdown()) {
                        val next = doc.change("Extern", "Außerhalb der App geändert (${f.name})") { it.copy(tabs = listOf(it.tabs[0].copy(markdown = text))) }
                        list = listOf(next) + list.filterNot { it.id == doc.id }
                        persist(v, next)
                        changed = true
                    }
                } else {
                    val kind = when {
                        f.name.endsWith("(Präsentation).md") -> DocKind.SLIDES
                        f.name.endsWith("(Tabelle).md") -> DocKind.SHEET
                        else -> DocKind.DOC
                    }
                    val title = f.nameWithoutExtension.removeSuffix(" (Präsentation)").removeSuffix(" (Tabelle)")
                    val next = LiveDoc.create(title, text, "Extern", kind = kind).copy(vault = v.id, mirror = f.name, updatedAt = f.lastModified())
                    list = listOf(next) + list
                    persist(v, next)
                    changed = true
                }
            }
        }
        if (changed) _items.value = list.distinctBy { it.id }.sortedByDescending { it.updatedAt }
    }

    private fun persist(v: DocVault, d: LiveDoc) {
        runCatching {
            val f = File(metaDir(v).apply { mkdirs() }, "${d.id}.json")
            AtomicFiles.write(f, json.encodeToString(LiveDoc.serializer(), d))
            known[f.path] = f.lastModified()
        }.logFailure("Dokument speichern")
    }
}

/**
 * Markdown split into the blocks the editor shows and edits one at a time: paragraphs, headings, list groups,
 * tables, code, quotes and callouts. Joining the blocks with an empty line gives the Markdown back.
 */
object DocBlocks {
    private val HEADING = Regex("^#{1,6}\\s")
    private val LIST = Regex("^\\s*([-*+]|\\d+[.)])\\s")

    fun split(markdown: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var kind = ""
        fun flush() {
            val s = cur.toString().trimEnd()
            if (s.isNotBlank()) out += s.trimStart('\n')
            cur.clear()
            kind = ""
        }
        val lines = markdown.replace("\r\n", "\n").lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flush()
                    cur.append(line)
                    i++
                    while (i < lines.size) {
                        cur.append('\n').append(lines[i])
                        if (lines[i].trim().startsWith("```")) break
                        i++
                    }
                    flush()
                }
                t.isEmpty() -> if (kind != "list") flush() else {
                    // A list continues after an empty line only if the next line is another item.
                    if (i + 1 < lines.size && LIST.containsMatchIn(lines[i + 1])) cur.append('\n') else flush()
                }
                HEADING.containsMatchIn(t) -> { flush(); out += t }
                t.startsWith("|") -> {
                    if (kind != "table") flush()
                    kind = "table"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
                t.startsWith(">") -> {
                    if (kind != "quote") flush()
                    kind = "quote"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
                LIST.containsMatchIn(line) || (kind == "list" && line.startsWith("  ")) -> {
                    if (kind != "list") flush()
                    kind = "list"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
                t.matches(Regex("^([-*_])\\1{2,}$")) -> { flush(); out += t }
                else -> {
                    if (kind != "" && kind != "para") flush()
                    kind = "para"
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(line)
                }
            }
            i++
        }
        flush()
        return out
    }

    fun join(blocks: List<String>): String = blocks.filter { it.isNotBlank() }.joinToString("\n\n") { it.trimEnd() } + "\n"

    fun titleOf(markdown: String): String? = markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()

    enum class Kind(val label: String) { PARAGRAPH(trLater("Absatz")), H1(trLater("Überschrift 1")), H2(trLater("Überschrift 2")), H3(trLater("Überschrift 3")), BULLETS(trLater("Aufzählung")), NUMBERS(trLater("Nummerierte Liste")), TASKS(trLater("Checkliste")), QUOTE(trLater("Zitat")), NOTE(trLater("Hinweisbox")), TABLE(trLater("Tabelle")), CODE("Code"), RULE(trLater("Trennlinie")) }

    fun kindOf(block: String): Kind {
        val t = block.trimStart()
        return when {
            t.startsWith("### ") || t.startsWith("#### ") -> Kind.H3
            t.startsWith("## ") -> Kind.H2
            t.startsWith("# ") -> Kind.H1
            t.startsWith("```") -> Kind.CODE
            t.startsWith("|") -> Kind.TABLE
            t.startsWith("> [!") -> Kind.NOTE
            t.startsWith(">") -> Kind.QUOTE
            Regex("^\\d+[.)]\\s").containsMatchIn(t) -> Kind.NUMBERS
            Regex("^[-*+]\\s\\[[ xX]]").containsMatchIn(t) -> Kind.TASKS
            Regex("^[-*+]\\s").containsMatchIn(t) -> Kind.BULLETS
            Regex("^([-*_])\\1{2,}$").matches(t) -> Kind.RULE
            else -> Kind.PARAGRAPH
        }
    }

    /** The text of a block without its Markdown marks of the block type. */
    fun plainOf(block: String): String = block.lines().joinToString("\n") { l ->
        l.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^>\\s?(\\[![A-Z]+]\\s*)?"), "").replace(Regex("^\\s*([-*+]|\\d+[.)])\\s+(\\[[ xX]]\\s*)?"), "")
    }.trim()

    /** Changes the type of a block, keeping its text. */
    fun convert(block: String, to: Kind): String {
        val text = plainOf(block)
        val lines = text.lines().filter { it.isNotBlank() }
        return when (to) {
            Kind.PARAGRAPH -> lines.joinToString(" ")
            Kind.H1 -> "# " + lines.joinToString(" ")
            Kind.H2 -> "## " + lines.joinToString(" ")
            Kind.H3 -> "### " + lines.joinToString(" ")
            Kind.BULLETS -> lines.joinToString("\n") { "- $it" }
            Kind.NUMBERS -> lines.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")
            Kind.TASKS -> lines.joinToString("\n") { "- [ ] $it" }
            Kind.QUOTE -> lines.joinToString("\n") { "> $it" }
            Kind.NOTE -> "> [!NOTE]\n" + lines.joinToString("\n") { "> $it" }
            Kind.TABLE -> if (block.trimStart().startsWith("|")) block else "| Spalte 1 | Spalte 2 |\n| --- | --- |\n| ${lines.firstOrNull().orEmpty()} | |"
            Kind.CODE -> "```\n$text\n```"
            Kind.RULE -> "---"
        }
    }

    /** Rows of a Markdown table (without the separator line). */
    fun tableRows(block: String): List<List<String>> = block.lines().map { it.trim() }.filter { it.startsWith("|") }
        .map { it.trim('|').split('|').map(String::trim) }
        .filterNot { row -> row.all { it.matches(Regex(":?-{2,}:?")) } }

    /** Ticks or unticks item [index] of a checklist block. */
    fun toggleTask(block: String, index: Int): String {
        var n = -1
        return block.lines().joinToString("\n") { line ->
            val m = Regex("^(\\s*[-*+]\\s\\[)([ xX])(].*)$").find(line) ?: return@joinToString line
            n++
            if (n != index) line else m.groupValues[1] + (if (m.groupValues[2].isBlank()) "x" else " ") + m.groupValues[3]
        }
    }

    /** Items of a checklist block: text and whether it is ticked. */
    fun tasks(block: String): List<Pair<String, Boolean>> = block.lines().mapNotNull { line ->
        Regex("^\\s*[-*+]\\s\\[([ xX])]\\s?(.*)$").find(line)?.let { it.groupValues[2] to it.groupValues[1].isNotBlank() }
    }

    fun table(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val cols = rows.maxOf { it.size }
        fun line(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { r.getOrElse(it) { "" }.replace("|", "/").replace("\n", " ") } + " |"
        return (listOf(line(rows[0]), "| " + List(cols) { "---" }.joinToString(" | ") + " |") + rows.drop(1).map(::line)).joinToString("\n")
    }
}

/** Word-level differences between two texts, for the version history and Word with tracked changes. */
object TextDiff {
    enum class Op { SAME, ADDED, REMOVED }

    data class Piece(val op: Op, val text: String)

    private fun tokens(s: String): List<String> = Regex("\\s+|[^\\s]+").findAll(s).map { it.value }.toList()

    /** Differences of [old] and [new] word by word (the longest common subsequence). */
    fun words(old: String, new: String): List<Piece> {
        val a = tokens(old)
        val b = tokens(new)
        if (a.size * b.size > 6_000_000) {
            // Very long texts: compare blocks instead of words.
            return blocks(old, new)
        }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<Piece>()
        fun add(op: Op, t: String) {
            if (out.isNotEmpty() && out.last().op == op) out[out.lastIndex] = Piece(op, out.last().text + t) else out += Piece(op, t)
        }
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { add(Op.SAME, a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { add(Op.REMOVED, a[i]); i++ }
                else -> { add(Op.ADDED, b[j]); j++ }
            }
        }
        while (i < a.size) add(Op.REMOVED, a[i++])
        while (j < b.size) add(Op.ADDED, b[j++])
        return out
    }

    /** Block by block: unchanged blocks stay together, changed ones are compared word by word. */
    fun blocks(old: String, new: String): List<Piece> {
        val a = DocBlocks.split(old)
        val b = DocBlocks.split(new)
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<Piece>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { out += Piece(Op.SAME, a[i] + "\n\n"); i++; j++ }
                j < b.size && (i >= a.size || dp[i][j + 1] >= dp[i + 1][j]) -> { out += Piece(Op.ADDED, b[j] + "\n\n"); j++ }
                else -> { out += Piece(Op.REMOVED, a[i] + "\n\n"); i++ }
            }
        }
        return out
    }

    /** Indexes of the blocks of [new] that differ from [old] (to highlight what the model just changed). */
    fun changedBlocks(old: String, new: String): Set<Int> {
        val before = DocBlocks.split(old).toSet()
        return DocBlocks.split(new).mapIndexedNotNull { i, b -> if (b !in before) i else null }.toSet()
    }

    fun summary(pieces: List<Piece>): String {
        val added = pieces.filter { it.op == Op.ADDED }.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        val removed = pieces.filter { it.op == Op.REMOVED }.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        return "+$added / −$removed Wörter"
    }
}

/**
 * What the model is writing right now (arguments of a create_document call still streaming), per chat: the editor
 * shows the document filling up as in Claude Docs.
 */
object DocsLive {
    data class Draft(val title: String, val markdown: String, val docId: String? = null)

    val drafts = MutableStateFlow<Map<String, Draft>>(emptyMap())

    /** Reads title and content from the beginning of a JSON object that is still being written. */
    fun partialArgs(json: String): Pair<String, String> = partialString(json, "title") to partialString(json, "content")

    private fun partialString(json: String, key: String): String {
        val m = Regex("\"$key\"\\s*:\\s*\"").find(json) ?: return ""
        val sb = StringBuilder()
        var i = m.range.last + 1
        while (i < json.length) {
            val ch = json[i]
            if (ch == '"') break
            if (ch == '\\') {
                if (i + 1 >= json.length) break
                when (val n = json[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> {}
                    'u' -> {
                        if (i + 5 < json.length) json.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) } else return sb.toString()
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
                continue
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    fun update(chatId: String, draft: Draft?) {
        drafts.update { m -> if (draft == null) m - chatId else m + (chatId to draft) }
    }
}
