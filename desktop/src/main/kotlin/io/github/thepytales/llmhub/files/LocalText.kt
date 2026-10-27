package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.desktop.Images
import io.github.thepytales.llmhub.docs.OfficeImport
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * Text of a file in a shared local folder: Office files as Markdown, text files in their own encoding (complete, so
 * line numbers fit edit_local_file), the other formats like attachments.
 */
object LocalText {
    /** Folders on this PC can be shared with the agent. */
    const val SUPPORTED = true

    suspend fun read(file: File): String = read(file.name, file.readBytes()) { AttachmentReader.read(file) }

    /** Same for bytes that are not a file of their own (an entry of a ZIP archive). */
    suspend fun read(name: String, bytes: ByteArray): String = read(name, bytes) {
        val tmp = kotlin.io.path.createTempDirectory("adeu-zip").toFile()
        try {
            AttachmentReader.read(File(tmp, name.substringAfterLast('/')).apply { writeBytes(bytes) })
        } finally {
            tmp.deleteRecursively()
        }
    }

    private suspend fun read(name: String, bytes: ByteArray, attachment: suspend () -> ReadResult): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in setOf("docx", "pptx", "xlsx", "xlsm")) {
            OfficeImport.read(name, bytes)?.let { imported ->
                return imported.tabs.joinToString("\n\n") { (t, md) -> if (imported.tabs.size > 1) "## $t\n\n$md" else md }
            }
        }
        if (FormatReaders.handles(name)) FormatReaders.text(name, bytes)?.let { return it }
        if (ext in TextFormats.IMAGES) return imageNote(name, ext, bytes)
        if (ext in TextFormats.MEDIA) return mediaNote(name, bytes.size.toLong())
        if (ext != "pdf" && ext !in setOf("html", "htm") && (TextFormats.isText(name) || TextCodec.looksLikeText(bytes))) return TextCodec.text(bytes)
        val r = attachment()
        return r.file?.text ?: throw IllegalArgumentException(r.error ?: "„$name“ lässt sich nicht als Text lesen.")
    }

    private fun imageNote(name: String, ext: String, bytes: ByteArray): String {
        val size = Images.size(bytes) ?: AttachmentReader.skiaJpeg(bytes)?.let { Images.size(it) }
        return buildString {
            append("[Bild „").append(name).append("“ (").append(ext.uppercase())
            size?.let { (w, h) -> append(", ").append(w).append(" × ").append(h).append(" Pixel") }
            append(", ").append(ZipText.size(bytes.size.toLong())).append(")]\n")
            append("read_local_file liefert nur Text. Den Bildinhalt kannst du nicht sehen – bitte den Nutzer, das Bild in den Chat zu ziehen, wenn du es beschreiben sollst.")
            if (ext in setOf("heic", "heif") && size == null) append("\nHEIC-Fotos kann die Windows-App nicht öffnen – als JPEG speichern lassen.")
        }
    }

    private fun mediaNote(name: String, bytes: Long) =
        "[Audio-/Videodatei „$name“, ${ZipText.size(bytes)}]\nMit transcribe_local_file lässt sie sich in Text umwandeln (Transkription der App)."

    /** Text for search_local_files; PDFs without keeping a copy for forms. Null if the file has no readable text. */
    suspend fun searchText(file: File): String? = runCatching {
        if (file.extension.lowercase() == "pdf") {
            Loader.loadPDF(file).use { doc ->
                val stripper = PDFTextStripper()
                stripper.endPage = minOf(doc.numberOfPages, 300)
                stripper.getText(doc)
            }
        } else read(file)
    }.getOrNull()
}
