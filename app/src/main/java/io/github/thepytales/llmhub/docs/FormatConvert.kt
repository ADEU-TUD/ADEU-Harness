package io.github.thepytales.llmhub.docs

import android.content.Context
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Formatwandler mit Verlustanzeige (AP-90): Word ↔ ODT/RTF/PDF/Markdown/Text, Excel ↔ ODS/CSV/PDF, PowerPoint → ODP/PDF,
 * Markdown/Text/CSV/RTF → Office. Before converting, [analyze] says what goes missing or changes – formulas (only the
 * values), animations and transitions, comments and tracked changes, macros, more than one sheet for CSV, fonts that are
 * not embedded, headers and footers – with a better target where there is one. The original always stays; the new file
 * goes next to it with its own name.
 */
object FormatConvert {
    /** One thing that is lost or changes; [severe]: content goes (not only looks). */
    data class Loss(val title: String, val detail: String, val severe: Boolean = false)

    /** What converting [name] into [target] means: the losses and, where there is one, a target that keeps more. */
    data class Plan(val target: String, val losses: List<Loss>, val better: String? = null, val betterWhy: String? = null)

    private val WORD = setOf("docx", "docm")
    private val EXCEL = setOf("xlsx", "xlsm")
    private val POWERPOINT = setOf("pptx", "pptm")

    /** Labels of the targets for the menu. */
    val LABELS = mapOf(
        "docx" to trLater("Word (.docx)"), "odt" to trLater("OpenDocument-Text (.odt)"), "rtf" to "RTF", "pdf" to "PDF",
        "md" to "Markdown", "txt" to trLater("Text"), "html" to "HTML", "xlsx" to trLater("Excel (.xlsx)"),
        "ods" to trLater("OpenDocument-Tabelle (.ods)"), "csv" to "CSV", "odp" to trLater("OpenDocument-Präsentation (.odp)"),
        "pptx" to "PowerPoint (.pptx)", "jpg" to "JPG", "png" to trLater("PNG (mit Transparenz)"), "webp" to "WebP",
    )

    /** Pictures (AP-90): read with [io.github.thepytales.llmhub.files.PhotoCodec], HEIC/HEIF only as a source. */
    private val IMAGES = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif", "tif", "tiff", "avif")
    private val ODF_TEXT = "odt"
    private val ODF_SHEET = "ods"
    private val ODF_SLIDES = "odp"

    fun isImage(name: String) = ext(name) in IMAGES

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    /** Formats [name] can be turned into. */
    fun targets(name: String): List<String> = when (ext(name)) {
        in WORD -> listOf("odt", "pdf", "rtf", "md", "txt", "html")
        in EXCEL -> listOf("ods", "csv", "pdf")
        in POWERPOINT -> listOf("odp", "pdf")
        "csv", "tsv" -> listOf("xlsx", "ods")
        "md", "markdown" -> listOf("docx", "odt", "pdf", "html", "rtf")
        "txt" -> listOf("docx", "odt", "pdf")
        "rtf" -> listOf("docx", "odt", "pdf", "md")
        "html", "htm" -> listOf("docx", "pdf", "md")
        // OpenDocument as a source (AP-90).
        ODF_TEXT -> listOf("docx", "pdf", "rtf", "md", "txt", "html")
        ODF_SHEET -> listOf("xlsx", "csv", "pdf")
        ODF_SLIDES -> listOf("pptx", "pdf")
        in IMAGES -> if (!io.github.thepytales.llmhub.files.PhotoCodec.readable(ext(name))) emptyList()
            else listOf("jpg", "png", "webp", "pdf").filter { t -> t != ext(name).replace("jpeg", "jpg") && (if (t == "pdf") ToPdfKind.of(name) == ToPdfKind.IMAGE else io.github.thepytales.llmhub.files.PhotoCodec.writable(t)) }
        else -> emptyList()
    }

    /** The parts of an Office file (zip) with their text, only the small XML ones (for the checks). */
    private fun parts(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name
                    if (n.endsWith(".xml") || n.endsWith(".rels")) out[n] = z.readBytes().toString(Charsets.UTF_8)
                    else out[n] = ""
                }
            }
        }
        return out
    }

    /** Fonts that come with every system or have a metric twin the app and LibreOffice use (no visible change). */
    private val COMMON_FONTS = setOf(
        "Arial", "Times New Roman", "Calibri", "Cambria", "Courier New", "Helvetica", "Liberation Sans", "Liberation Serif",
        "Carlito", "Caladea", "Symbol", "Wingdings", "Calibri Light", "Open Sans", "Open Sans Light", "Segoe UI", "Verdana", "Georgia",
    )

    /** What converting [name] ([bytes]) into [target] loses. */
    fun analyze(name: String, bytes: ByteArray, target: String): Plan {
        val src = ext(name)
        val losses = mutableListOf<Loss>()
        val p = if (src in WORD + EXCEL + POWERPOINT + setOf(ODF_TEXT, ODF_SHEET, ODF_SLIDES)) parts(bytes) else emptyMap()
        val toText = target in setOf("txt", "md", "csv")
        val viaApp = target != "pdf" || PdfPlatform.officeName.isEmpty()
        if (p.keys.any { it.endsWith("vbaProject.bin") } || src in setOf("docm", "xlsm", "pptm")) losses += Loss(tr("Makros"), tr("Makros werden nicht übernommen."), severe = true)
        when (src) {
            in WORD -> {
                val doc = p["word/document.xml"].orEmpty()
                if (p.keys.any { it.startsWith("word/comments") }) losses += Loss(tr("Kommentare"), tr("Kommentare gehen verloren."), severe = true)
                if ("<w:ins " in doc || "<w:del " in doc) losses += Loss(tr("Änderungsspuren"), tr("Änderungen werden so übernommen, wie sie angezeigt werden – ohne Markierung, wer was geändert hat."), severe = true)
                if (viaApp && p.keys.any { it.startsWith("word/header") || it.startsWith("word/footer") }) losses += Loss(tr("Kopf- und Fußzeilen"), tr("Kopf- und Fußzeilen werden nicht übernommen."))
                if (viaApp && "w:txbxContent" in doc) losses += Loss(tr("Textfelder"), tr("Text in Textfeldern landet im Fließtext oder fehlt."))
                if (viaApp && !toText) losses += Loss(tr("Layout"), tr("Das Layout wird vereinfacht: Überschriften, Listen, Tabellen und Bilder bleiben, Spalten, Abstände und Farben nicht genau."))
                if (toText) losses += Loss(tr("Formatierung"), if (target == "md") tr("Nur Überschriften, Listen, Tabellen und Hervorhebungen bleiben.") else tr("Nur der Text bleibt – Überschriften, Tabellen und Bilder nicht."))
                if (target in setOf("pdf", "odt")) {
                    val fonts = Regex("w:ascii=\"([^\"]+)\"").findAll(doc + p["word/styles.xml"].orEmpty()).map { it.groupValues[1] }.toSet()
                    val embedded = p.keys.any { it.startsWith("word/fonts/") }
                    val unusual = fonts.filter { it !in COMMON_FONTS && !it.startsWith("+") }
                    if (unusual.isNotEmpty() && !embedded) losses += Loss(tr("Schriften"), tr("Nicht eingebettete Schriften werden ersetzt: {0}.", unusual.sorted().joinToString(", ")))
                }
            }
            in EXCEL -> {
                val sheets = p.keys.count { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                val formulas = p.filterKeys { it.startsWith("xl/worksheets/sheet") }.values.sumOf { Regex("<f[ >]").findAll(it).count() }
                // ODS keeps formulas (OpenDocument notation); CSV and PDF only have the results.
                if (formulas > 0 && target != "ods") losses += Loss(tr("Formeln"), tr("{0} Formeln werden zu festen Werten.", formulas), severe = true)
                if (target == "csv" && sheets > 1) losses += Loss(tr("Tabellenblätter"), tr("CSV hat nur ein Blatt: von {0} Blättern kommt nur das erste mit.", sheets), severe = true)
                if (p.keys.any { it.startsWith("xl/comments") || it.startsWith("xl/threadedComments") }) losses += Loss(tr("Kommentare"), tr("Kommentare gehen verloren."), severe = true)
                if (p.keys.any { it.startsWith("xl/charts/") }) losses += Loss(tr("Diagramme"), tr("Diagramme werden nicht übernommen."), severe = true)
                if (target != "pdf" || viaApp) losses += Loss(tr("Formatierung"), tr("Farben, Rahmen und Zahlenformate werden nicht übernommen; Zahlen bleiben Zahlen."))
            }
            in POWERPOINT -> {
                val slides = p.filterKeys { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }.values
                val animated = slides.count { "<p:timing" in it && "<p:par" in it }
                val transitions = slides.count { "<p:transition" in it }
                if (animated > 0) losses += Loss(tr("Animationen"), if (animated == 1) tr("Die Animationen einer Folie gehen verloren.") else tr("Animationen auf {0} Folien gehen verloren.", animated), severe = target == "odp")
                if (transitions > 0) losses += Loss(tr("Übergänge"), if (transitions == 1) tr("Der Übergang einer Folie geht verloren.") else tr("Folienübergänge auf {0} Folien gehen verloren.", transitions))
                if (p.keys.any { it.startsWith("ppt/comments") }) losses += Loss(tr("Kommentare"), tr("Kommentare gehen verloren."), severe = true)
                if (viaApp) losses += Loss(tr("Layout"), tr("Das Layout wird vereinfacht: Titel, Text, Aufzählungen, Tabellen und Bilder bleiben, die genaue Lage nicht."))
            }
            "csv", "tsv" -> Unit
            ODF_TEXT, ODF_SHEET, ODF_SLIDES -> {
                val content = p["content.xml"].orEmpty()
                if ("<office:annotation" in content) losses += Loss(tr("Kommentare"), tr("Kommentare gehen verloren."), severe = true)
                if ("<text:tracked-changes" in content) losses += Loss(tr("Änderungsspuren"), tr("Änderungen werden so übernommen, wie sie angezeigt werden – ohne Markierung, wer was geändert hat."), severe = true)
                if (p.keys.any { it.startsWith("Basic/") && it.endsWith(".xml") && it != "Basic/script-lc.xml" }) losses += Loss(tr("Makros"), tr("Makros werden nicht übernommen."), severe = true)
                when (src) {
                    ODF_TEXT -> {
                        if (target != "pdf" || viaApp) losses += if (target in setOf("txt", "md")) Loss(tr("Formatierung"), if (target == "md") tr("Nur Überschriften, Listen, Tabellen und Hervorhebungen bleiben.") else tr("Nur der Text bleibt – Überschriften, Tabellen und Bilder nicht."))
                            else Loss(tr("Layout"), tr("Das Layout wird vereinfacht: Überschriften, Listen und Tabellen bleiben, Bilder, Spalten, Abstände und Farben nicht genau."))
                    }
                    ODF_SHEET -> {
                        val sheets = Regex("<table:table ").findAll(content).count()
                        val formulas = Regex("table:formula=").findAll(content).count()
                        if (formulas > 0 && target != "xlsx") losses += Loss(tr("Formeln"), tr("{0} Formeln werden zu festen Werten.", formulas), severe = true)
                        if (target == "csv" && sheets > 1) losses += Loss(tr("Tabellenblätter"), tr("CSV hat nur ein Blatt: von {0} Blättern kommt nur das erste mit.", sheets), severe = true)
                        if ("<draw:object" in content) losses += Loss(tr("Diagramme"), tr("Diagramme werden nicht übernommen."), severe = true)
                        losses += Loss(tr("Formatierung"), tr("Farben, Rahmen und Zahlenformate werden nicht übernommen; Zahlen bleiben Zahlen."))
                    }
                    ODF_SLIDES -> {
                        if ("<anim:" in content) losses += Loss(tr("Animationen"), tr("Animationen gehen verloren."), severe = false)
                        if ("transition-type" in content || "<anim:transitionFilter" in content) losses += Loss(tr("Übergänge"), tr("Folienübergänge gehen verloren."))
                        if (viaApp) losses += Loss(tr("Layout"), tr("Das Layout wird vereinfacht: Titel, Text, Aufzählungen und Tabellen bleiben, Bilder und die genaue Lage nicht."))
                    }
                }
            }
            in IMAGES -> {
                val exif = io.github.thepytales.llmhub.files.Exif.read(bytes)
                if (target == "jpg" && hasTransparency(src, bytes)) losses += Loss(tr("Transparenz"), tr("JPG kennt keine Transparenz – durchsichtige Stellen werden weiß."), severe = true)
                if (src == "gif" && Regex("\u0021\u00F9").findAll(String(bytes, Charsets.ISO_8859_1)).count() > 1) losses += Loss(tr("Animation"), tr("Nur das erste Bild der Animation bleibt."), severe = true)
                if (target in setOf("jpg", "webp")) losses += Loss(tr("Qualität"), tr("{0} speichert mit leichtem Qualitätsverlust (90 %).", target.uppercase()))
                if (exif.hasMetadata || exif.hasGps) losses += Loss(tr("Metadaten"), if (exif.hasGps) tr("Aufnahmedatum, Kamera und Ortsdaten werden nicht übernommen.") else tr("Aufnahmedatum und Kamera werden nicht übernommen."))
            }
            else -> if (target in setOf("txt")) losses += Loss(tr("Formatierung"), tr("Nur der Text bleibt."))
        }
        val (better, why) = when {
            src in EXCEL && target == "csv" && losses.any { it.title == tr("Tabellenblätter") } -> "ods" to tr("OpenDocument-Tabelle behält alle Blätter.")
            src in WORD && target == "txt" -> "md" to tr("Markdown behält Überschriften, Listen und Tabellen.")
            src in IMAGES && target == "jpg" && losses.any { it.title == tr("Transparenz") } -> "png" to tr("PNG behält die Transparenz.")
            src == ODF_SHEET && target == "csv" && losses.any { it.title == tr("Tabellenblätter") } -> "xlsx" to tr("Excel behält alle Blätter.")
            else -> null to null
        }
        return Plan(target, losses, better, why)
    }

    /** Whether a PNG (alpha channel or tRNS), WebP (alpha flag) or GIF (transparent colour) may have see-through parts. */
    private fun hasTransparency(src: String, b: ByteArray): Boolean = when (src) {
        "png" -> b.size > 26 && ((b[25].toInt() and 0xFF) in setOf(4, 6) || "tRNS" in String(b, 0, minOf(b.size, 4096), Charsets.ISO_8859_1))
        "webp" -> b.size > 21 && String(b, 12, 4, Charsets.ISO_8859_1) == "VP8X" && (b[20].toInt() and 0x10) != 0 || "VP8L" == String(b, 12, minOf(4, b.size - 12), Charsets.ISO_8859_1)
        "gif" -> b.size > 13 && Regex("\u0021\u00F9\u0004[\u0001\u0003\u0005\u0007\u0009\u000B\u000D\u000F\u0011\u0013\u0015\u0017\u0019\u001B\u001D\u001F]").containsMatchIn(String(b, Charsets.ISO_8859_1))
        else -> false
    }

    /** Name of the new file next to the original: same name, new ending, numbered if it exists. */
    fun outFile(dir: File, name: String, target: String): File {
        val base = name.substringBeforeLast('.')
        var f = File(dir, "$base.$target")
        var n = 2
        while (f.exists()) f = File(dir, "$base ($n).$target").also { n++ }
        return f
    }

    /** Converts [input] (named [name]) into [target]; the new file lands in [dir] (normally the original's folder). */
    suspend fun convert(context: Context, input: File, name: String, target: String, dir: File): File = withContext(Dispatchers.IO) {
        require(target in targets(name)) { tr("„{0}“ lässt sich nicht in {1} umwandeln.", name, target.uppercase()) }
        dir.mkdirs()
        val out = outFile(dir, name, target)
        if (target == "pdf") {
            val made = PdfConvert.toPdf(context, input, name, File(context.cacheDir, "umwandeln"))
            made.file.copyTo(out)
            made.file.delete()
            return@withContext out
        }
        if (ext(name) in IMAGES) {
            // Upright as the camera noted, without metadata; a transparent PNG gets a white background as JPG.
            val pic = io.github.thepytales.llmhub.files.PhotoCodec.read(input) ?: throw IllegalArgumentException(tr("„{0}“ lässt sich nicht lesen.", name))
            val upright = io.github.thepytales.llmhub.files.PhotoCodec.orient(pic, io.github.thepytales.llmhub.files.Exif.read(input.readBytes()).orientation)
            io.github.thepytales.llmhub.files.PhotoCodec.write(upright, out, target, 90)
            return@withContext out
        }
        val bytes = input.readBytes()
        val imported = OfficeImport.read(name, bytes) ?: throw IllegalArgumentException(tr("„{0}“ lässt sich nicht lesen.", name))
        val title = imported.title
        val markdown = if (imported.tabs.size > 1) imported.tabs.joinToString("\n\n") { (t, md) -> "## $t\n\n$md" } else imported.tabs.first().second
        when (target) {
            "odt" -> out.writeBytes(OpenDocumentWriter.odt(title, markdown))
            "ods" -> out.writeBytes(OpenDocumentWriter.ods(title, markdown))
            "odp" -> out.writeBytes(OpenDocumentWriter.odp(title, markdown))
            "rtf" -> out.writeText(RtfWriter.write(title, markdown))
            "csv" -> {
                // Excel-tauglich: semicolon and a BOM, so Excel in German opens it in columns with the umlauts right.
                // Results of the formulas, not the formulas.
                val rows = SheetEngine.Sheet(SheetEngine.grid(imported.tabs.first().second)).computed()
                out.writeText(CsvPilot.write(rows, CsvPilot.Flavour.EXCEL))
            }
            "xlsx" -> XlsxWriter.writeTabs(out, imported.tabs)
            "pptx" -> {
                val media = File(context.cacheDir, "umwandeln-bilder-${System.nanoTime()}").apply { mkdirs() }
                try {
                    PptxWriter.write(out, title, markdown, "", base = media)
                } finally {
                    media.deleteRecursively()
                }
            }
            "md", "txt", "html", "docx" -> {
                // Pictures of the original next to the Markdown while writing, so they go into the new file.
                val media = File(context.cacheDir, "umwandeln-bilder-${System.nanoTime()}").apply { mkdirs() }
                imported.media.forEach { (path, data) -> File(media, path).apply { parentFile?.mkdirs() }.writeBytes(data) }
                try {
                    val made = DocumentWriter.create(context, dir, title, markdown, DocFormat.parse(target), fileName = out.name, imageBase = media)
                    if (made.file != out) made.file.renameTo(out)
                } finally {
                    media.deleteRecursively()
                }
            }
            else -> error(target)
        }
        out
    }
}
