package io.github.thepytales.llmhub.data

import android.content.Context
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.logFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

/*
 * Der Lora-Ordner (AP-16 Stufe 0): the files the user sees, like a Google Drive on the PC. Two fixed folders –
 * „Hochgeladen“ (a copy of every file put into the app) and „Erstellt“ (everything made in a chat) – and the user's own
 * folders; the editor's documents live there as real .docx/.xlsx/.pptx/.md files (docs/DocFiles). Files can be moved,
 * renamed and deleted (into the Papierkorb, 30 days); a file moved out of „Erstellt“ keeps its chat, because the chats
 * keep the old path and [resolve] finds the file again.
 */

/** A file the app put into the Lora-Ordner or moved there: where it is now and where it was. */
@Serializable
data class DriveEntry(
    val id: String = UUID.randomUUID().toString().take(12),
    /** Path inside the Lora-Ordner with „/“. */
    val path: String,
    /** „upload“, „created“ or „moved“. */
    val origin: String = "moved",
    /** Chat it came from (pictures; uploads and created files are found through the chats themselves). */
    val chatId: String? = null,
    /** SHA-256 of an upload, so the same file is kept only once. */
    val sha: String? = null,
    /** Absolute paths it had before (chats point to them). */
    val oldPaths: List<String> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
)

/** Something in the Papierkorb of the Lora-Ordner. */
@Serializable
data class TrashEntry(
    val id: String = UUID.randomUUID().toString().take(12),
    val name: String,
    /** Absolute path it was deleted from. */
    val from: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val dir: Boolean = false,
    /** Editor document that was this file (restored with it). */
    val docId: String? = null,
)

class LoraDrive(private val c: AppContainer, context: Context) {
    private val files = context.filesDir
    val root: File = LoraRoot.dir(context).apply { mkdirs() }
    val uploads: File get() = File(root, UPLOADS).apply { mkdirs() }
    val created: File get() = File(root, CREATED).apply { mkdirs() }
    val entries = ListStore<DriveEntry>(FileBackend(File(files, "dateien.json")), DriveEntry.serializer(), { it.id })
    val trash = ListStore<TrashEntry>(FileBackend(File(files, "dateien_papierkorb.json")), TrashEntry.serializer(), { it.id })
    private val trashDir = File(files, "dateien-papierkorb")
    private val rootsFile = File(files, "lora_ordner_frueher.txt")

    /** Raised after every change, so the Dateien tab reloads. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** Called after files changed (Android copies them into the chosen folder, files/LoraMirror). */
    var onChanged: (() -> Unit)? = null

    fun changed() {
        _changes.value++
        runCatching { onChanged?.invoke() }
    }

    /** Earlier places of the Lora-Ordner (moved as a whole): paths below them are found again below [root]. */
    private val formerRoots: List<String> = runCatching {
        val known = if (rootsFile.isFile) rootsFile.readLines().filter { it.isNotBlank() } else emptyList()
        val last = known.lastOrNull()
        val now = root.absolutePath
        val list = if (last == null) listOf(now) else if (last != now) (known.filter { it != now } + now) else known
        if (list != known) rootsFile.writeText(list.joinToString("\n"))
        list.dropLast(1)
    }.getOrDefault(emptyList())

    init {
        uploads
        created
    }

    // ── Paths ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Path of [f] inside the Lora-Ordner with „/“, null if it is outside. */
    fun rel(f: File): String? {
        val r = root.absoluteFile.normalize().path
        val p = f.absoluteFile.normalize().path
        return when {
            p == r -> ""
            p.startsWith(r + File.separator) -> p.substring(r.length + 1).replace(File.separatorChar, '/')
            else -> null
        }
    }

    fun inside(f: File) = rel(f) != null

    fun fileOf(rel: String): File = if (rel.isEmpty()) root else File(root, rel)

    /**
     * The file a chat or document points to by [path], also after it was moved in the Dateien tab, the Lora-Ordner was
     * moved as a whole, or the file came over from before AP-16 (documents/, attachments/).
     */
    fun resolve(path: String): File {
        val f = File(path)
        if (f.exists()) return f
        entries.current.firstOrNull { e -> path in e.oldPaths }?.let { return fileOf(it.path) }
        val norm = path.replace('\\', '/')
        for (old in formerRoots) {
            val o = old.replace('\\', '/').trimEnd('/') + "/"
            if (norm.startsWith(o)) {
                val moved = fileOf(norm.removePrefix(o))
                if (moved.exists()) return moved
                entries.current.firstOrNull { e -> e.oldPaths.any { it.replace('\\', '/') == norm } }?.let { return fileOf(it.path) }
            }
        }
        return f
    }

    // ── Listing ───────────────────────────────────────────────────────────────────────────────────────────

    /** Folders first, then files; no hidden ones (.lora, .quellen, Office lock files ~$…). */
    fun list(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { visible(it) }.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

    fun visible(f: File) = !f.name.startsWith(".") && !f.name.startsWith("~$") && !f.name.endsWith(".tmp")

    /** All files in the Lora-Ordner (for Zuletzt, Markiert, search), newest first. */
    fun allFiles(max: Int = 5000): List<File> {
        val out = mutableListOf<File>()
        fun go(d: File, depth: Int) {
            if (depth > 10 || out.size >= max) return
            d.listFiles().orEmpty().forEach { f ->
                if (!visible(f)) return@forEach
                if (f.isDirectory) go(f, depth + 1) else if (out.size < max) out += f
            }
        }
        go(root, 0)
        return out.sortedByDescending { it.lastModified() }
    }

    /** All folders of the Lora-Ordner (for „Verschieben nach …“), as paths with „/“. */
    fun allFolders(): List<String> {
        val out = mutableListOf("")
        fun go(d: File, depth: Int) {
            if (depth > 8) return
            d.listFiles().orEmpty().filter { it.isDirectory && visible(it) }.sortedBy { it.name.lowercase() }.forEach { sub ->
                rel(sub)?.let { out += it }
                go(sub, depth + 1)
            }
        }
        go(root, 0)
        return out
    }

    /** Key of a file for Schlagwörter and stars (relative inside the Lora-Ordner, so it survives moving the folder). */
    fun metaKey(f: File): String = rel(f)?.let { "lora:$it" } ?: "file:${f.absolutePath}"

    fun starred(f: File): Boolean = c.fileLibrary.meta.get(metaKey(f))?.starred == true

    fun setStarred(f: File, on: Boolean) {
        val key = metaKey(f)
        val old = c.fileLibrary.meta.get(key) ?: FileMeta(key)
        c.fileLibrary.meta.upsert(old.copy(starred = on))
        f.parentFile?.let { /* the tab reloads */ }
        changed()
    }

    // ── Uploads and created files ─────────────────────────────────────────────────────────────────────────

    /** Keeps a copy of an uploaded file in „Hochgeladen“ (the same file only once); returns it. */
    @Synchronized
    fun keepUpload(name: String, bytes: ByteArray, chatId: String? = null, oldPath: String? = null, at: Long? = null): File? = runCatching {
        val sha = sha256(bytes)
        entries.current.firstOrNull { it.sha == sha && fileOf(it.path).isFile }?.let { e ->
            if (oldPath != null && oldPath !in e.oldPaths) entries.upsert(e.copy(oldPaths = e.oldPaths + oldPath))
            return@runCatching fileOf(e.path)
        }
        val target = unique(uploads, safe(name))
        Files.write(target.toPath(), bytes)
        at?.let { target.setLastModified(it) }
        entries.upsert(DriveEntry(path = rel(target)!!, origin = "upload", chatId = chatId, sha = sha, oldPaths = listOfNotNull(oldPath)))
        changed()
        target
    }.logFailure("Upload im Lora-Ordner ablegen").getOrNull()

    private val staging = File(files, "hochladen-wartet")

    /**
     * An attachment that was just read: kept aside until its message is sent ([adopt]) – a chat of the Tresor never puts
     * it into the visible folder.
     */
    fun stage(name: String, bytes: ByteArray): File {
        staging.mkdirs()
        val f = File(staging, sha256(bytes) + "__" + safe(name))
        if (!f.exists()) Files.write(f.toPath(), bytes)
        return f
    }

    /** Whether [path] is an attachment kept aside by [stage]. */
    fun isStaged(path: String?) = path != null && File(path).parentFile?.absoluteFile == staging.absoluteFile

    /** The message with the staged attachment was sent in [chatId]: it moves into „Hochgeladen“ (the same file only once). */
    @Synchronized
    fun adopt(path: String, chatId: String?): File? = runCatching {
        val f = File(path)
        if (!f.isFile) return@runCatching null
        val sha = f.name.substringBefore("__")
        val name = f.name.substringAfter("__")
        entries.current.firstOrNull { it.sha == sha && fileOf(it.path).isFile }?.let { e ->
            f.delete()
            return@runCatching fileOf(e.path)
        }
        val target = unique(uploads, name)
        try {
            Files.move(f.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        entries.upsert(DriveEntry(path = rel(target)!!, origin = "upload", chatId = chatId, sha = sha, oldPaths = listOf(path)))
        changed()
        target
    }.logFailure("Upload im Lora-Ordner ablegen").getOrNull()

    /** Drops attachments that were never sent (or belonged to the Tresor). */
    fun purgeStaged(olderThanMs: Long = DAY) {
        val limit = System.currentTimeMillis() - olderThanMs
        staging.listFiles().orEmpty().filter { it.lastModified() < limit }.forEach { it.delete() }
    }

    /** Files made in a Tresor chat (AP-10) stay inside the app, never in the visible Lora-Ordner. */
    private val vaultFiles = File(files, "tresor-dateien")

    /** Whether [chatId] is a chat of the Tresor. */
    fun isVaultChat(chatId: String?): Boolean = chatId != null && runCatching { c.chats.get(chatId)?.vault == true }.getOrDefault(false)

    /** A file a tool made in a Tresor chat moves out of „Erstellt“ into the app's own folder. */
    fun keepVaultFile(chatId: String?, output: io.github.thepytales.llmhub.agent.ToolOutput): io.github.thepytales.llmhub.agent.ToolOutput {
        val path = output.file ?: return output
        if (!isVaultChat(chatId)) return output
        val f = File(path)
        if (!f.isFile || !inside(f)) return output
        vaultFiles.mkdirs()
        val target = unique(vaultFiles, f.name)
        return runCatching {
            Files.move(f.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            output.copy(file = target.absolutePath, text = output.text.replace(path, target.absolutePath))
        }.getOrDefault(output)
    }

    /** A picture of a chat (data URL) as file in „Hochgeladen“. */
    fun keepImage(dataUrl: String, chatId: String?, at: Long? = null): File? {
        val comma = dataUrl.indexOf(',')
        if (!dataUrl.startsWith("data:image/") || comma < 0) return null
        val ext = when (dataUrl.substring(11, dataUrl.indexOf(';').coerceAtLeast(11))) { "png" -> "png"; "gif" -> "gif"; "webp" -> "webp"; else -> "jpg" }
        val bytes = runCatching { Base64.getDecoder().decode(dataUrl.substring(comma + 1)) }.getOrNull() ?: return null
        val name = "Bild " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.GERMANY).format(Date(at ?: System.currentTimeMillis())) + ".$ext"
        return keepUpload(name, bytes, chatId, at = at)
    }

    /**
     * Which chat each file came from (absolute path → chat id): files the model made, attachments and pictures, documents
     * written in a chat – found in the chats themselves, so a file moved in the Dateien tab keeps its chat.
     */
    fun chatLinks(): Map<String, String> {
        val out = HashMap<String, String>()
        entries.current.forEach { e -> e.chatId?.let { out[fileOf(e.path).absolutePath] = it } }
        for (conv in c.chats.conversations.value) {
            if (conv.vault) continue
            for (m in conv.messages) {
                m.file?.let { p -> out.putIfAbsent(resolve(p).absolutePath, conv.id) }
                m.files.forEach { a -> (a.stored ?: a.path)?.let { p -> out.putIfAbsent(resolve(p).absolutePath, conv.id) } }
            }
        }
        c.docs.current.forEach { d -> d.chatId?.let { id -> c.docs.realFile(d)?.let { out.putIfAbsent(it.absolutePath, id) } } }
        return out
    }

    /** The chat a picture of „Hochgeladen“ came from (other files are found through the chats). */
    fun chatOfEntry(f: File): String? = rel(f)?.let { r -> entries.current.firstOrNull { it.path == r }?.chatId }

    // ── Changing ──────────────────────────────────────────────────────────────────────────────────────────

    /** Creates the folder [name] in [parent]; returns it. */
    fun mkdir(parent: File, name: String): File {
        val dir = unique(parent, safe(name).ifBlank { "Neuer Ordner" }, isDir = true)
        dir.mkdirs()
        changed()
        return dir
    }

    /** Whether [f] is one of the fixed folders („Hochgeladen“, „Erstellt“), which cannot be renamed or deleted. */
    fun isFixed(f: File) = f.isDirectory && rel(f) in setOf(UPLOADS, CREATED)

    /** Renames [f]; returns the new file (or throws with a message for the user). */
    fun rename(f: File, newName: String): File {
        require(!isFixed(f)) { "„${f.name}“ ist ein fester Ordner und lässt sich nicht umbenennen." }
        val clean = safe(newName).trim()
        require(clean.isNotEmpty()) { "Der Name ist leer." }
        val name = if (f.isFile && !clean.contains('.') && f.extension.isNotEmpty()) "$clean.${f.extension}" else clean
        if (name == f.name) return f
        val target = File(f.parentFile, name)
        require(!target.exists() || target.name.equals(f.name, true)) { "„$name“ gibt es hier schon." }
        moveFile(f, target)
        return target
    }

    /** Moves [items] into [dir]; existing names get a number. Returns the moved files. */
    fun move(items: List<File>, dir: File): List<File> {
        dir.mkdirs()
        return items.mapNotNull { f ->
            if (isFixed(f)) return@mapNotNull null
            if (f.parentFile?.absoluteFile == dir.absoluteFile) return@mapNotNull f
            // Not into itself.
            if (f.isDirectory && dir.absoluteFile.normalize().path.startsWith(f.absoluteFile.normalize().path + File.separator)) return@mapNotNull null
            val target = unique(dir, f.name, isDir = f.isDirectory)
            moveFile(f, target)
            target
        }
    }

    /** Copies [items] into [dir] (Duplizieren, Kopieren nach …). */
    fun copy(items: List<File>, dir: File): List<File> = items.map { f ->
        val target = unique(dir, if (f.parentFile?.absoluteFile == dir.absoluteFile) copyName(f) else f.name, isDir = f.isDirectory)
        f.copyRecursively(target, overwrite = false)
        target
    }.also { changed() }

    private fun copyName(f: File) = if (f.isDirectory || f.extension.isEmpty()) "${f.name} (Kopie)" else "${f.nameWithoutExtension} (Kopie).${f.extension}"

    /** Moves the file on disk and remembers it: entries, Schlagwörter/stars and documents of the editor follow. */
    private fun moveFile(from: File, to: File) {
        val oldAbs = from.absolutePath
        to.parentFile?.mkdirs()
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            if (from.isDirectory) {
                from.copyRecursively(to, overwrite = false)
                from.deleteRecursively()
            } else {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        relocated(oldAbs, to)
        changed()
    }

    /** Updates everything that pointed into [oldAbs] (a file or a folder) after it moved to [to]. */
    private fun relocated(oldAbs: String, to: File) {
        val oldRel = rel(File(oldAbs))
        val newRel = rel(to)
        val pairs = if (to.isDirectory) to.walkTopDown().filter { it.isFile }.map { f ->
            val sub = f.absolutePath.removePrefix(to.absolutePath)
            (oldAbs + sub) to f
        }.toList() else listOf(oldAbs to to)
        for ((old, now) in pairs) {
            val r = rel(now) ?: continue
            val oldR = rel(File(old))
            val e = entries.current.firstOrNull { it.path == oldR } ?: entries.current.firstOrNull { old in it.oldPaths }
            if (e != null) entries.upsert(e.copy(path = r, oldPaths = (e.oldPaths + old).distinct()))
            else entries.upsert(DriveEntry(path = r, oldPaths = listOf(old)))
        }
        // Schlagwörter and stars keep their file.
        if (oldRel != null && newRel != null) {
            c.fileLibrary.meta.current.filter { it.key == "lora:$oldRel" || it.key.startsWith("lora:$oldRel/") }.forEach { m ->
                c.fileLibrary.meta.delete(m.key)
                c.fileLibrary.meta.upsert(m.copy(key = "lora:$newRel" + m.key.removePrefix("lora:$oldRel")))
            }
        }
        c.docs.relocated(oldAbs, to.absolutePath)
    }

    /** Moves [items] into the Papierkorb (30 days); editor documents go with their file. */
    @Synchronized
    fun delete(items: List<File>): List<TrashEntry> = items.mapNotNull { f ->
        if (isFixed(f) || !f.exists()) return@mapNotNull null
        val doc = if (f.isFile) c.docs.forRealFile(f) else null
        val entry = TrashEntry(name = f.name, from = f.absolutePath, dir = f.isDirectory, docId = doc?.id)
        val dest = File(File(trashDir, entry.id).apply { mkdirs() }, f.name)
        try {
            Files.move(f.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            f.copyRecursively(dest, overwrite = true)
            f.deleteRecursively()
        }
        doc?.let { d ->
            runCatching { File(trashDir, "${entry.id}/doc.json").writeText(c.docs.encode(d)) }
            c.docs.forget(d.id)
        }
        trash.upsert(entry)
        entry
    }.also { if (it.isNotEmpty()) changed() }

    /** Puts [entry] back where it was (with a number if the name is taken meanwhile); returns the file. */
    @Synchronized
    fun restore(entry: TrashEntry): File? {
        val src = File(File(trashDir, entry.id), entry.name)
        if (!src.exists()) {
            trash.delete(entry.id)
            return null
        }
        val origin = File(entry.from)
        val target = unique(origin.parentFile ?: root, entry.name, isDir = entry.dir)
        target.parentFile?.mkdirs()
        try {
            Files.move(src.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            src.copyRecursively(target, overwrite = false)
            src.deleteRecursively()
        }
        File(trashDir, "${entry.id}/doc.json").takeIf { it.isFile }?.let { j ->
            c.docs.decode(j.readText())?.let { d -> c.docs.adopt(d, target) }
        }
        File(trashDir, entry.id).deleteRecursively()
        trash.delete(entry.id)
        changed()
        return target
    }

    /** Deletes [entry] for good. */
    fun deleteForever(entry: TrashEntry) {
        File(trashDir, entry.id).deleteRecursively()
        trash.delete(entry.id)
        changed()
    }

    fun emptyTrash() {
        trash.current.forEach { File(trashDir, it.id).deleteRecursively() }
        trash.replaceAll(emptyList())
        changed()
    }

    /** Removes what lies in the Papierkorb longer than [TRASH_DAYS] days. */
    fun purgeTrash(now: Long = System.currentTimeMillis()) {
        trash.current.filter { now - it.deletedAt > TRASH_DAYS * DAY }.forEach { deleteForever(it) }
    }

    // ── Taking over the files of before AP-16 ─────────────────────────────────────────────────────────────

    /**
     * Once: created files (documents/) move into „Erstellt“, kept PDFs (attachments/) and the pictures of all chats go into
     * „Hochgeladen“ – Word, Excel and other uploads were not kept before, only their text. Chats keep working with the
     * old paths ([resolve]).
     */
    fun migrate() {
        val flag = File(files, "dateien-uebernommen.txt")
        if (flag.isFile) return
        runCatching {
            val oldDocs = File(files, "documents")
            if (oldDocs.isDirectory && oldDocs.absoluteFile != created.absoluteFile) {
                oldDocs.listFiles().orEmpty().forEach { f ->
                    if (f.name == ".quellen") {
                        f.listFiles().orEmpty().forEach { q -> runCatching { q.copyTo(File(File(created, ".quellen"), q.name), overwrite = false) } }
                        return@forEach
                    }
                    if (!visible(f)) return@forEach
                    val target = unique(created, f.name, isDir = f.isDirectory)
                    runCatching {
                        f.copyRecursively(target, overwrite = false)
                        target.setLastModified(f.lastModified())
                        val pairs = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.map { it.absolutePath to File(target, it.relativeTo(f).path) }.toList()
                        else listOf(f.absolutePath to target)
                        pairs.forEach { (old, now) -> rel(now)?.let { r -> entries.upsert(DriveEntry(path = r, origin = "created", oldPaths = listOf(old))) } }
                    }.logFailure("Erstellte Datei übernehmen")
                }
            }
            // Uploads: the kept PDFs and the pictures of every chat.
            for (light in c.chats.conversations.value) {
                val conv = c.chats.get(light.id) ?: continue
                for (m in conv.messages) {
                    if (m.role != "user") continue
                    m.files.forEach { a ->
                        val p = a.path ?: return@forEach
                        val src = File(p)
                        if (src.isFile && entries.current.none { p in it.oldPaths }) keepUpload(a.name, src.readBytes(), conv.id, oldPath = p, at = m.createdAt)
                    }
                    m.images.forEach { url -> keepImage(url, conv.id, at = m.createdAt) }
                }
            }
        }.logFailure("Dateien übernehmen")
        runCatching { flag.writeText(System.currentTimeMillis().toString()) }
        changed()
    }

    companion object {
        const val UPLOADS = "Hochgeladen"
        const val CREATED = "Erstellt"
        const val TRASH_DAYS = 30
        private const val DAY = 24L * 60 * 60 * 1000

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** A name that works as file name on Windows and Android. */
        fun safe(name: String): String = DocumentWriter.safeName(name.substringBeforeLast('.', name)).let { base ->
            val ext = name.substringAfterLast('.', "").takeIf { name.contains('.') && it.length in 1..8 && it.all { ch -> ch.isLetterOrDigit() } }
            if (ext != null) "$base.$ext" else base
        }.ifBlank { "Datei" }

        /** [name] in [dir], with „ (2)“, „ (3)“ … if it is taken. */
        fun unique(dir: File, name: String, isDir: Boolean = false): File {
            var f = File(dir, name)
            if (!f.exists()) return f
            val base = if (isDir || !name.contains('.')) name else name.substringBeforeLast('.')
            val ext = if (isDir || !name.contains('.')) "" else "." + name.substringAfterLast('.')
            var n = 2
            while (f.exists()) f = File(dir, "$base ($n)$ext").also { n++ }
            return f
        }
    }
}

/** Hook for the attachment readers (shared with Windows): keeps uploads aside for „Hochgeladen“ once the container runs. */
object UploadSink {
    @Volatile
    var keep: ((name: String, bytes: ByteArray) -> String?)? = null

    /** Stores the copy and notes it at the attachment ([FileAttachment.stored]). */
    fun stored(name: String, bytes: ByteArray, file: FileAttachment?): FileAttachment? {
        if (file == null) return null
        val path = runCatching { keep?.invoke(name, bytes) }.getOrNull() ?: return file
        return file.copy(stored = path)
    }
}
