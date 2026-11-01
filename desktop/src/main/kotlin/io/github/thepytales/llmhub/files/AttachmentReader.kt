package io.github.thepytales.llmhub.files

import android.content.Context
import io.github.thepytales.llmhub.desktop.Images
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.docs.PdfForms
import io.github.thepytales.llmhub.docs.PdfOriginals
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import io.github.thepytales.llmhub.agent.WebTools
import io.github.thepytales.llmhub.data.FileAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import io.github.thepytales.llmhub.data.PageMarks
import io.github.thepytales.llmhub.data.PageMarks.PAGE_BREAK
import java.nio.file.Files
import java.util.zip.ZipInputStream
import io.github.thepytales.llmhub.ui.tr

/** Result of reading an attached file: its text, page images (scanned PDFs, pictures) or an error. */
data class ReadResult(
    val file: FileAttachment? = null,
    val images: List<String> = emptyList(),
    val error: String? = null,
    val note: String? = null,
)

/** Extracts text from PDFs, Word/PowerPoint/OpenDocument files and all kinds of text files. */
object AttachmentReader {
    const val MAX_CHARS = 120_000
    private const val MAX_BYTES = 25L * 1024 * 1024

    /** Code, configuration and markup (shared list, see [TextFormats]). */
    private val TEXT_EXTENSIONS = TextFormats.TEXT_EXTENSIONS

    private val IMAGE_EXTENSIONS = TextFormats.IMAGES

    private fun mimeOf(file: File, ext: String): String = when (ext) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "tif", "tiff" -> "image/tiff"
        "svg" -> "image/svg+xml"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "xml" -> "application/xml"
        else -> runCatching { Files.probeContentType(file.toPath()) }.getOrNull().orEmpty()
    }

    /** Reads a file the user picked or dropped onto the window. */
    suspend fun read(file: File): ReadResult = withContext(Dispatchers.IO) {
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = mimeOf(file, ext)
        try {
            if (!file.isFile) return@withContext ReadResult(error = tr("„{0}“ konnte nicht geöffnet werden.", name))
            if (file.length() > MAX_BYTES) return@withContext ReadResult(error = tr("„{0}“ ist zu groß (max. 25 MB).", name))
            val bytes = file.readBytes()
            when {
                mime == "application/pdf" || ext == "pdf" -> readPdf(name, bytes).also {
                    // AP-04: measuring point after a big PDF (does the memory go down again afterwards?).
                    if (bytes.size > 2_000_000) io.github.thepytales.llmhub.data.MemoryMeter.mark("Nach PDF-Anhang (${bytes.size / 1_000_000} MB)")
                }
                FormatReaders.handles(name) -> text(name, mime, FormatReaders.text(name, bytes).orEmpty())
                mime.startsWith("image/") || ext in IMAGE_EXTENSIONS -> image(name, ext, bytes)
                ext in TextFormats.MEDIA || mime.startsWith("audio/") || mime.startsWith("video/") ->
                    ReadResult(error = tr("„{0}“ ist eine Audio- oder Videodatei – bitte über „Transkription“ in Text umwandeln und das Transkript anhängen.", name))
                // Word and PowerPoint as Markdown: headings, lists and tables stay recognizable for the model.
                ext == "docx" -> text(name, mime, runCatching { io.github.thepytales.llmhub.docs.OfficeImport.docx(bytes) }.getOrNull()?.takeIf { it.isNotBlank() } ?: zipText(bytes, Regex("word/document\\.xml"), "w:p"))
                ext == "pptx" -> text(name, mime, runCatching { io.github.thepytales.llmhub.docs.OfficeImport.pptx(bytes) }.getOrNull()?.takeIf { it.isNotBlank() } ?: zipText(bytes, Regex("ppt/slides/slide(\\d+)\\.xml"), "a:p", "Folie"))
                ext == "xlsx" -> text(name, mime, xlsxText(bytes))
                ext in setOf("html", "htm") || mime == "text/html" -> text(name, mime, WebTools.htmlToText(decode(bytes)))
                mime.startsWith("text/") || ext in TEXT_EXTENSIONS || TextFormats.isText(name) || mime in setOf("application/json", "application/xml") ->
                    text(name, mime, decode(bytes))
                looksLikeText(bytes) -> text(name, mime, decode(bytes))
                else -> ReadResult(error = tr("„{0}“: Dieser Dateityp wird nicht unterstützt (PDF, Word, PowerPoint, Excel, Text und Code gehen).", name))
            }.let { r -> r.copy(file = r.file?.copy(name = name)) }
                // Lora-Ordner (AP-16): a copy of the file goes into „Hochgeladen“.
                .let { r -> if (r.error == null && r.file != null) r.copy(file = io.github.thepytales.llmhub.data.UploadSink.stored(name, bytes, r.file)) else r }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ReadResult(error = tr("„{0}“ konnte nicht gelesen werden: {1}", name, e.message ?: e.javaClass.simpleName))
        }
    }

    /** Pictures for vision models: ImageIO (PNG, JPEG, GIF, BMP, TIFF), else Skia (WebP …); HEIC usually not. */
    private fun image(name: String, ext: String, bytes: ByteArray): ReadResult {
        Images.dataUrl(bytes)?.let { return ReadResult(images = listOf(it)) }
        skiaJpeg(bytes)?.let { jpeg -> Images.dataUrl(jpeg)?.let { return ReadResult(images = listOf(it)) } }
        val hint = when (ext) {
            "heic", "heif" -> tr("HEIC-Fotos (iPhone) kann die Windows-App nicht lesen – bitte als JPEG speichern, z. B. in der Fotos-App über „Speichern unter“ oder am iPhone unter Einstellungen › Kamera › Formate › „Maximale Kompatibilität“.")
            "webp" -> tr("Dieses WebP-Bild ließ sich nicht lesen – bitte als PNG oder JPEG speichern (z. B. in Paint über „Speichern unter“).")
            else -> tr("Dieses Bildformat wird nicht unterstützt (PNG, JPEG, GIF, BMP, TIFF und WebP gehen).")
        }
        return ReadResult(error = "„$name“: $hint")
    }

    /** JPEG bytes of a picture Skia can decode (WebP and others ImageIO lacks), null if it cannot. */
    fun skiaJpeg(bytes: ByteArray): ByteArray? = runCatching {
        val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        img.encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 90)?.bytes
    }.getOrNull()

    private fun text(name: String, mime: String, text: String, pages: Int? = null): ReadResult {
        val clean = text.replace("\r\n", "\n").replace(Regex("\n{3,}"), "\n\n").trim()
        if (clean.isEmpty()) return ReadResult(error = tr("„{0}“ enthält keinen lesbaren Text.", name))
        val truncated = clean.length > MAX_CHARS
        return ReadResult(
            file = FileAttachment(name, mime.ifEmpty { "text/plain" }, clean.take(MAX_CHARS), pages, truncated),
            note = if (truncated) "„$name“ ist sehr lang – nur die ersten ${MAX_CHARS / 1000}k Zeichen werden gesendet." else null,
        )
    }

    private fun readPdf(name: String, bytes: ByteArray): ReadResult {
        val (text, pages) = Loader.loadPDF(bytes).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.endPage = minOf(doc.numberOfPages, 500)
            stripper.pageEnd = PAGE_BREAK
            PageMarks.number(stripper.getText(doc)) to doc.numberOfPages
        }
        // Kept so the agent can fill in the form later (read_pdf_form, fill_pdf).
        val copy = PdfOriginals.keep(File(Paths.data, "attachments"), name, bytes)?.absolutePath
        val fields = PdfForms.fieldCount(Context(Paths.data, Paths.cache), bytes)
        fun withCopy(r: ReadResult) = r.copy(file = r.file?.copy(path = copy, formFields = fields))
        if (text.trim().length >= 20 * minOf(pages, 3)) return withCopy(text(name, "application/pdf", text, pages))
        // Scanned PDF without text layer: send the first pages as images for vision models.
        val images = renderPdfPages(bytes, 4)
        if (fields > 0) return withCopy(text(name, "application/pdf", text.ifBlank { "[Formular ohne Textebene]" }, pages)).copy(images = images)
        if (images.isEmpty()) return ReadResult(error = tr("„{0}“ enthält keinen Text und konnte nicht gerendert werden.", name))
        return ReadResult(images = images, note = "„$name“ enthält keinen Text (Scan) – die ersten ${images.size} Seiten werden als Bilder angehängt.")
    }

    private fun renderPdfPages(bytes: ByteArray, max: Int): List<String> = runCatching {
        Loader.loadPDF(bytes).use { doc ->
            val renderer = PDFRenderer(doc)
            (0 until minOf(max, doc.numberOfPages)).map { i ->
                val box = doc.getPage(i).mediaBox
                val scale = 1280f / maxOf(box.width, box.height)
                Images.jpegDataUrl(renderer.renderImage(i, scale, ImageType.RGB))
            }
        }
    }.getOrDefault(emptyList())

    /** Text of Office Open XML / OpenDocument files: paragraphs of the matching XML parts. */
    private fun zipText(bytes: ByteArray, part: Regex, paragraph: String, label: String? = null): String {
        val parts = sortedMapOf<Int, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val m = part.matchEntire(entry.name)
                if (m != null) {
                    val no = m.groupValues.getOrNull(1)?.toIntOrNull() ?: parts.size
                    parts[no] = xmlText(zip.readBytes().toString(Charsets.UTF_8), paragraph)
                }
            }
        }
        return parts.entries.joinToString("\n\n") { (no, text) -> if (label != null) "[$label $no]\n$text" else text }
    }

    private fun xmlText(xml: String, paragraph: String): String = WebTools.decodeEntities(
        xml.replace(Regex("<w:tab/>|<text:tab/>"), "\t")
            .replace(Regex("<w:br/>|<text:line-break/>|<a:br/>"), "\n")
            .replace("</$paragraph>", "\n")
            .replace(Regex("<[^>]+>"), "")
    ).lines().joinToString("\n") { it.trimEnd() }

    /** Cells of all sheets of an Excel file as tab-separated rows. */
    private fun xlsxText(bytes: ByteArray): String {
        var shared = emptyList<String>()
        val sheets = sortedMapOf<Int, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = when {
                    entry.name == "xl/sharedStrings.xml" || entry.name.startsWith("xl/worksheets/sheet") ->
                        zip.readBytes().toString(Charsets.UTF_8)
                    else -> null
                } ?: continue
                if (entry.name == "xl/sharedStrings.xml") {
                    shared = Regex("<si>([\\s\\S]*?)</si>").findAll(content)
                        .map { WebTools.decodeEntities(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }.toList()
                } else {
                    val no = Regex("sheet(\\d+)\\.xml").find(entry.name)?.groupValues?.get(1)?.toIntOrNull() ?: sheets.size
                    sheets[no] = content
                }
            }
        }
        val cell = Regex("<c ([^>]*?)(/>|>([\\s\\S]*?)</c>)")
        return sheets.entries.joinToString("\n\n") { (no, xml) ->
            "[Tabelle $no]\n" + Regex("<row[^>]*>([\\s\\S]*?)</row>").findAll(xml).joinToString("\n") { row ->
                cell.findAll(row.groupValues[1]).joinToString("\t") { c ->
                    val attrs = c.groupValues[1]
                    val inner = c.groupValues[3]
                    val v = Regex("<v>([\\s\\S]*?)</v>").find(inner)?.groupValues?.get(1)
                        ?: Regex("<t[^>]*>([\\s\\S]*?)</t>").find(inner)?.groupValues?.get(1).orEmpty()
                    if ("t=\"s\"" in attrs) shared.getOrNull(v.toIntOrNull() ?: -1).orEmpty() else WebTools.decodeEntities(v)
                }
            }
        }
    }

    private fun looksLikeText(bytes: ByteArray): Boolean = TextCodec.looksLikeText(bytes)

    /** UTF-8 (with or without BOM), UTF-16 with BOM, or Windows-1252 for older text files. */
    private fun decode(bytes: ByteArray): String = TextCodec.text(bytes)
}
