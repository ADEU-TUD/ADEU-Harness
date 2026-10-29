package io.github.thepytales.llmhub.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import io.github.thepytales.llmhub.agent.WorkFolder
import io.github.thepytales.llmhub.ui.Notices
import java.io.File

/**
 * Project folders on Android (Projekt › Bearbeiten › Projektordner): a folder chosen in the system dialog – also one of
 * Nextcloud, Google Drive & Co., if their app offers it there – reached with a lasting permission. Windows has plain
 * paths instead (its version of this file is empty).
 */
object ProjectTree {
    const val SUPPORTED = true

    /** A file or folder in the project folder; [path] relative to it. */
    data class Entry(val path: String, val id: String, val dir: Boolean, val size: Long)

    fun isTree(path: String) = path.startsWith("content://")

    /** Readable name of the folder, e.g. "Documents/Seminar". */
    fun label(path: String): String = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(path)).substringAfter(':').ifBlank { "Hauptordner" }
    }.getOrDefault(path)

    /** Keeps the permission after a restart; called right after the folder was chosen. */
    fun keep(context: Context, path: String) {
        context.contentResolver.takePersistableUriPermission(
            Uri.parse(path), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Null if the folder can be used, otherwise what is wrong. */
    fun problem(context: Context, path: String): String? = runCatching {
        children(context, Uri.parse(path), root(path))
        null
    }.getOrElse { "Auf den Ordner kann die App nicht zugreifen (${it.message ?: it.javaClass.simpleName}). Bitte neu wählen." }

    /**
     * Copies [file] into the folder's „Ausgabe“. A file of the same name is replaced only if the app made it ([own]);
     * otherwise the storage app picks a new name. Returns the address of the copy.
     */
    fun deliver(context: Context, path: String, file: File, own: Set<String>): String {
        val tree = Uri.parse(path)
        val r = context.contentResolver
        val out = children(context, tree, root(path)).firstOrNull { it.dir && it.path == WorkFolder.OUTPUT }?.id
            ?: DocumentsContract.createDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, root(path)), Document.MIME_TYPE_DIR, WorkFolder.OUTPUT)
                ?.let(DocumentsContract::getDocumentId)
            ?: error("Der Ordner „${WorkFolder.OUTPUT}“ ließ sich nicht anlegen.")
        val existing = children(context, tree, out).firstOrNull { !it.dir && it.path == file.name }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val target = existing?.takeIf { it.toString() in own }
            ?: DocumentsContract.createDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, out), mime, file.name)
            ?: error("Die Datei ließ sich nicht anlegen.")
        (r.openOutputStream(target, "wt") ?: error("Die Datei ließ sich nicht schreiben.")).use { o -> file.inputStream().use { it.copyTo(o) } }
        return target.toString()
    }

    /** Files in the folder and its subfolders (at most [max]). */
    fun list(context: Context, path: String, max: Int = 300): List<Entry> {
        val tree = Uri.parse(path)
        val out = mutableListOf<Entry>()
        fun walk(id: String, prefix: String, depth: Int) {
            for (e in children(context, tree, id)) {
                if (out.size >= max) return
                if (!e.dir) out += e.copy(path = prefix + e.path)
                else if (depth < 4) walk(e.id, prefix + e.path + "/", depth + 1)
            }
        }
        walk(root(path), "", 0)
        return out
    }

    /** Text of a file in the folder, read like an attachment (PDF, Word, PowerPoint, Excel, text …). */
    suspend fun read(context: Context, path: String, entry: Entry): ReadResult =
        AttachmentReader.read(context, DocumentsContract.buildDocumentUriUsingTree(Uri.parse(path), entry.id))

    fun open(context: Context, path: String) {
        runCatching {
            val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(path), root(path))
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure { Notices.info("Keine App zum Öffnen von Ordnern gefunden – der Ordner „${label(path)}“ ist in der Dateien-App zu finden.") }
    }

    private fun root(path: String) = DocumentsContract.getTreeDocumentId(Uri.parse(path))

    private fun children(context: Context, tree: Uri, id: String): List<Entry> {
        val cols = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        val cursor = context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id), cols, null, null, null)
            ?: error("Ordner nicht lesbar")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Entry(c.getString(0).orEmpty(), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR, if (c.isNull(3)) 0 else c.getLong(3)))
                }
            }
        }
    }
}
