package io.github.thepytales.llmhub.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import io.github.thepytales.llmhub.data.AtomicFiles
import io.github.thepytales.llmhub.ui.tr
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Android (AP-16): the Lora-Ordner lives inside the app; a folder chosen in the system dialog (e.g. Dokumente/Lora, also
 * one of Nextcloud or Google Drive) gets a copy of everything in it and gives back what was changed there – so the files
 * are easy to find on the phone. Two-way by modification time; what was deleted on one side since the last time goes on
 * the other side too. Windows has no such copy (its version of this file is empty).
 */
object LoraMirror {
    const val SUPPORTED = true

    data class Result(val up: Int, val down: Int, val removed: Int)

    private data class Remote(val id: String, val dir: Boolean, val modified: Long)

    private val json = Json { ignoreUnknownKeys = true }

    /** Keeps the permission for [tree] after a restart. */
    fun keep(context: Context, tree: String) = ProjectTree.keep(context, tree)

    fun label(tree: String) = ProjectTree.label(tree)

    /** Compares [root] with the chosen folder [tree] and copies in both directions. */
    @Synchronized
    fun sync(context: Context, root: File, tree: String): Result {
        val uri = Uri.parse(tree)
        val r = context.contentResolver
        val state = File(context.filesDir, "lora_abgleich.json")
        val last = runCatching { json.decodeFromString(ListSerializer(String.serializer()), state.readText()).toSet() }.getOrDefault(emptySet())
        val remote = HashMap<String, Remote>()
        fun walk(id: String, prefix: String, depth: Int) {
            if (depth > 8) return
            for ((name, e) in children(context, uri, id)) {
                if (name.startsWith(".")) continue
                val path = prefix + name
                remote[path] = e
                if (e.dir) walk(e.id, "$path/", depth + 1)
            }
        }
        walk(DocumentsContract.getTreeDocumentId(uri), "", 0)
        val local = root.walkTopDown().filter { it != root && !it.name.startsWith(".") && it.relativeTo(root).path.split(File.separatorChar).none { p -> p.startsWith(".") } }
            .associateBy { it.relativeTo(root).path.replace(File.separatorChar, '/') }
        var up = 0
        var down = 0
        var removed = 0
        // Deleted on one side since the last time.
        for (p in last) {
            val l = local[p]
            val rm = remote[p]
            if (l == null && rm != null && !rm.dir) { runCatching { DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(uri, rm.id)) }; remote.remove(p); removed++ }
            if (rm == null && l != null && l.isFile) { l.delete(); removed++ }
        }
        val localNow = local.filterValues { it.exists() }
        for ((p, f) in localNow) {
            if (f.isDirectory) continue
            val rm = remote[p]
            if (rm == null || f.lastModified() > rm.modified + 2000) {
                runCatching { upload(context, uri, remote, p, f) }.onSuccess { up++ }
            }
        }
        for ((p, rm) in remote) {
            if (rm.dir) { File(root, p).mkdirs(); continue }
            val f = File(root, p)
            if (!f.exists() || rm.modified > f.lastModified() + 2000) {
                runCatching {
                    val bytes = r.openInputStream(DocumentsContract.buildDocumentUriUsingTree(uri, rm.id))?.use { it.readBytes() } ?: return@runCatching
                    f.parentFile?.mkdirs()
                    AtomicFiles.writeBytes(f, bytes)
                    f.setLastModified(rm.modified)
                }.onSuccess { down++ }
            }
        }
        val all = (root.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.map { it.relativeTo(root).path.replace(File.separatorChar, '/') }).toList()
        runCatching { state.writeText(json.encodeToString(ListSerializer(String.serializer()), all)) }
        return Result(up, down, removed)
    }

    private fun upload(context: Context, tree: Uri, remote: HashMap<String, Remote>, path: String, f: File) {
        val r = context.contentResolver
        val parentPath = path.substringBeforeLast('/', "")
        val parentId = folder(context, tree, remote, parentPath)
        val existing = remote[path]
        val target = existing?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }
            ?: DocumentsContract.createDocument(
                r, DocumentsContract.buildDocumentUriUsingTree(tree, parentId),
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream", f.name,
            ) ?: error(tr("Datei ließ sich nicht anlegen"))
        (r.openOutputStream(target, "wt") ?: error(tr("nicht schreibbar"))).use { o -> f.inputStream().use { it.copyTo(o) } }
        remote[path] = Remote(DocumentsContract.getDocumentId(target), false, System.currentTimeMillis())
    }

    /** Id of the folder [path] in the tree (created when missing). */
    private fun folder(context: Context, tree: Uri, remote: HashMap<String, Remote>, path: String): String {
        if (path.isEmpty()) return DocumentsContract.getTreeDocumentId(tree)
        remote[path]?.takeIf { it.dir }?.let { return it.id }
        val parent = folder(context, tree, remote, path.substringBeforeLast('/', ""))
        val made = DocumentsContract.createDocument(
            context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, parent), Document.MIME_TYPE_DIR, path.substringAfterLast('/'),
        ) ?: error(tr("Ordner ließ sich nicht anlegen"))
        val id = DocumentsContract.getDocumentId(made)
        remote[path] = Remote(id, true, System.currentTimeMillis())
        return id
    }

    private fun children(context: Context, tree: Uri, id: String): List<Pair<String, Remote>> {
        val cols = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
        val cursor = context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), cols, null, null, null) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(c.getString(0).orEmpty() to Remote(c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR, if (c.isNull(3)) 0 else c.getLong(3)))
                }
            }
        }
    }
}
