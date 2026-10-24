package io.github.thepytales.llmhub.docs

import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** A fillable field of a PDF form (AcroForm), as [PdfForms.inspect] finds it. */
data class PdfFormField(
    val name: String,
    /** Text, Ankreuzfeld, Optionsfeld, Auswahlliste, Unterschrift … */
    val type: String,
    val value: String,
    /** Allowed values for check boxes, radio buttons and choice lists. */
    val options: List<String> = emptyList(),
    /** Page (1-based) and position of the field in mm from the top left corner. */
    val page: Int? = null,
    val xMm: Float? = null,
    val yMm: Float? = null,
    /** Text printed next to or above the field, and the tooltip of the field. */
    val label: String = "",
    val tooltip: String = "",
    val readOnly: Boolean = false,
    val required: Boolean = false,
    val multiline: Boolean = false,
)

/** A line of text on a page; y is the baseline in mm from the top edge. */
data class PdfTextLine(val page: Int, val xMm: Float, val yMm: Float, val text: String)

/** What a PDF offers for filling in. */
data class PdfFormInfo(
    val pages: Int,
    /** Width and height of the first page in mm. */
    val pageSizeMm: Pair<Float, Float>,
    val fields: List<PdfFormField>,
    val lines: List<PdfTextLine>,
    /** Dynamic XFA form, which PDF libraries cannot fill. */
    val xfaOnly: Boolean = false,
)

/** Text to print onto a page, for PDFs without form fields; x/y in mm from the top left corner. */
data class PdfText(val page: Int, val xMm: Float, val yMm: Float, val text: String, val size: Float = 10f)

data class PdfFillReport(val filled: List<String>, val problems: List<String>)

/** Description of a form for the model: the fields to fill, or the text lines to place text next to. */
fun PdfFormInfo.describe(fileName: String): String = buildString {
    val (w, h) = pageSizeMm
    append("„$fileName“: $pages Seite(n), Seitengröße ${mm(w)} × ${mm(h)} mm.\n")
    if (xfaOnly) {
        append("Das ist ein dynamisches XFA-Formular – es lässt sich nicht direkt ausfüllen. Biete an, das Formular mit ")
        append("create_document (style=tud) als ausgefülltes PDF nachzubauen.\n")
        return@buildString
    }
    if (fields.isNotEmpty()) {
        append("${fields.size} Formularfelder. Fülle sie mit fill_pdf (fields = {\"Feldname\": \"Wert\"}), Feldnamen exakt wie hier:\n")
        fields.forEach { f ->
            append("- \"").append(f.name).append("\" (").append(f.type)
            if (f.options.isNotEmpty()) append("; Werte: ").append(f.options.joinToString(" | "))
            if (f.multiline) append("; mehrzeilig")
            if (f.required) append("; Pflichtfeld")
            if (f.readOnly) append("; schreibgeschützt")
            append(")")
            f.page?.let { append(" S. ").append(it) }
            if (f.label.isNotBlank()) append(" – Beschriftung: ").append(f.label)
            if (f.tooltip.isNotBlank() && f.tooltip != f.label) append(" – Hinweis: ").append(f.tooltip)
            if (f.value.isNotBlank()) append(" – aktuell: ").append(f.value)
            append('\n')
        }
        append("Unterschriftsfelder bleiben leer (handschriftlich unterschreiben).\n")
    } else {
        append("Keine Formularfelder. Setze Text mit fill_pdf (texts) an Positionen: x/y in mm von oben links, y ist die ")
        append("Grundlinie. Für eine Angabe hinter einer Beschriftung: gleiche y wie die Beschriftung, x etwas rechts von ihrem Ende ")
        append("bzw. auf der Linie dahinter. Textzeilen (Seite: x, y – Text):\n")
    }
    if (fields.isEmpty() || lines.size <= 200) {
        if (fields.isNotEmpty()) append("\nTextzeilen zur Orientierung (Seite: x, y – Text):\n")
        lines.take(600).forEach { l -> append("S.${l.page}: ${mm(l.xMm)}, ${mm(l.yMm)} – ${l.text}\n") }
        if (lines.size > 600) append("… ${lines.size - 600} weitere Zeilen\n")
    }
}

private fun mm(v: Float) = String.format(Locale.ROOT, "%.0f", v)

/** "ja", "x", "true" … mean a checked box. */
fun isChecked(value: String) = value.trim().lowercase() in setOf("true", "ja", "yes", "x", "1", "an", "on", "✓", "✔", "checked", "angekreuzt", "wahr")

/** Copies of attached PDFs, kept so the agent can fill them in later. */
object PdfOriginals {
    private const val KEEP_DAYS = 90L

    /** Saves [bytes] in [dir] and returns the file; old copies are removed on the way. */
    fun keep(dir: File, name: String, bytes: ByteArray): File? = runCatching {
        dir.mkdirs()
        val limit = System.currentTimeMillis() - KEEP_DAYS * 24 * 3600 * 1000
        dir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
        val safe = DocumentWriter.safeName(name.substringBeforeLast('.'))
        File(dir, "${UUID.randomUUID().toString().take(8)}_$safe.pdf").apply { writeBytes(bytes) }
    }.getOrNull()

    /** Name of the copy without the random prefix. */
    fun displayName(file: File) = file.name.substringAfter('_')
}

/** The printed text closest to the left of a field (same line), otherwise just above it. */
fun labelFor(lines: List<PdfTextLine>, page: Int, x: Float, top: Float, bottom: Float): String {
    val onPage = lines.filter { it.page == page }
    val left = onPage.filter { it.yMm in (top - 2f)..(bottom + 2f) && it.xMm < x }.maxByOrNull { it.xMm }
    if (left != null) return left.text.take(80)
    val above = onPage.filter { it.yMm in (top - 9f)..(top + 1f) && abs(it.xMm - x) < 60f }.minByOrNull { abs(it.xMm - x) }
    return above?.text?.take(80).orEmpty()
}

/** Replaces characters the font cannot show (the standard fonts only cover WinAnsi). */
fun encodable(text: String, canShow: (String) -> Boolean): String = buildString {
    text.forEach { ch -> append(if (canShow(ch.toString())) ch else '?') }
}
