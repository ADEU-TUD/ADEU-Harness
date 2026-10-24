package io.github.thepytales.llmhub.docs

import android.content.Context
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/** Windows version of PdfForms (PDFBox 3); same logic as the Android version with pdfbox-android. */
@Suppress("UNUSED_PARAMETER")
object PdfForms {
    private const val MM = 25.4f / 72f

    /** Number of fillable fields (0 for PDFs without a form). */
    fun fieldCount(context: Context, bytes: ByteArray): Int = runCatching {
        Loader.loadPDF(bytes).use { doc -> doc.documentCatalog.acroForm?.fieldTree?.count { it is PDTerminalField && it !is PDPushButton } ?: 0 }
    }.getOrDefault(0)

    fun inspect(context: Context, file: File): PdfFormInfo {
        return Loader.loadPDF(file).use { doc ->
            val lines = textLines(doc)
            val pageOf = widgetPages(doc)
            val form = doc.documentCatalog.acroForm
            val fields = form?.fieldTree?.filterIsInstance<PDTerminalField>()?.filter { it !is PDPushButton }.orEmpty().map { f ->
                val widget = f.widgets.firstOrNull()
                val page = widget?.let { pageOf[it.cosObject] }
                val height = page?.let { doc.getPage(it).mediaBox.height } ?: 842f
                val rect = widget?.rectangle
                val x = rect?.lowerLeftX
                val top = rect?.let { height - it.upperRightY }
                val bottom = rect?.let { height - it.lowerLeftY }
                PdfFormField(
                    name = f.fullyQualifiedName,
                    type = typeOf(f),
                    value = f.valueAsString.orEmpty(),
                    options = optionsOf(f),
                    page = page?.plus(1),
                    xMm = x?.times(MM),
                    yMm = top?.times(MM),
                    label = if (page != null && x != null && top != null && bottom != null) labelFor(lines, page + 1, x * MM, top * MM, bottom * MM) else "",
                    tooltip = f.alternateFieldName.orEmpty(),
                    readOnly = f.isReadOnly,
                    required = f.isRequired,
                    multiline = (f as? PDTextField)?.isMultiline == true,
                )
            }
            val box = doc.getPage(0).mediaBox
            PdfFormInfo(
                pages = doc.numberOfPages,
                pageSizeMm = box.width * MM to box.height * MM,
                fields = fields,
                lines = lines,
                xfaOnly = fields.isEmpty() && form?.hasXFA() == true,
            )
        }
    }

    fun fill(context: Context, src: File, out: File, values: Map<String, String>, texts: List<PdfText>, flatten: Boolean): PdfFillReport {
        val filled = mutableListOf<String>()
        val problems = mutableListOf<String>()
        Loader.loadPDF(src).use { doc ->
            val form = doc.documentCatalog.acroForm
            if (values.isNotEmpty() && form == null) problems += "Das PDF hat keine Formularfelder – nutze texts mit Positionen."
            if (form != null) {
                val all = form.fieldTree.filterIsInstance<PDTerminalField>()
                for ((name, value) in values) {
                    val field = find(form, all, name)
                    if (field == null) {
                        problems += "Feld „$name“ nicht gefunden."
                        continue
                    }
                    runCatching { setValue(field, value) }
                        .onSuccess { msg -> if (msg == null) filled += field.fullyQualifiedName else problems += "„$name“: $msg" }
                        .onFailure { problems += "„$name“: ${it.message ?: it.javaClass.simpleName}" }
                }
                if (flatten) {
                    runCatching { form.refreshAppearances() }
                    runCatching { form.flatten() }.onFailure { problems += "Abflachen nicht möglich: ${it.message}" }
                } else {
                    // Viewers rebuild the look of the fields, so values appear even where PDFBox could not draw them.
                    form.needAppearances = true
                }
            }
            if (texts.isNotEmpty()) {
                val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
                texts.forEach { t ->
                    if (t.page < 1 || t.page > doc.numberOfPages) {
                        problems += "Seite ${t.page} gibt es nicht."
                        return@forEach
                    }
                    val page = doc.getPage(t.page - 1)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(font, t.size)
                        cs.setLeading(t.size * 1.2f)
                        cs.newLineAtOffset(t.xMm / MM, page.mediaBox.height - t.yMm / MM)
                        t.text.lines().forEachIndexed { i, line ->
                            if (i > 0) cs.newLine()
                            cs.showText(encodable(line) { c -> runCatching { font.encode(c) }.isSuccess })
                        }
                        cs.endText()
                    }
                    filled += "Text S.${t.page}: ${t.text.lines().first().take(40)}"
                }
            }
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
        return PdfFillReport(filled, problems)
    }

    /** Sets a field; returns a problem description instead of a value it cannot take. */
    private fun setValue(field: PDTerminalField, value: String): String? {
        if (field.isReadOnly) return "schreibgeschützt"
        when (field) {
            is PDSignatureField -> return "Unterschriftsfeld – bleibt leer"
            is PDCheckBox -> if (isChecked(value) || value.equals(field.onValue, ignoreCase = true)) field.check() else field.unCheck()
            is PDRadioButton -> {
                val option = field.onValues.firstOrNull { it.equals(value.trim(), ignoreCase = true) }
                    ?: return "Wert „$value“ nicht erlaubt (möglich: ${field.onValues.joinToString(", ")})"
                field.setValue(option)
            }
            is PDChoice -> {
                val option = field.options.firstOrNull { it.equals(value.trim(), ignoreCase = true) }
                    ?: field.optionsDisplayValues.indexOfFirst { it.equals(value.trim(), ignoreCase = true) }.takeIf { it >= 0 }?.let { field.optionsExportValues.getOrNull(it) }
                when {
                    option != null -> field.setValue(option)
                    field is PDComboBox && field.isEdit -> field.setValue(value)
                    else -> return "Wert „$value“ nicht in der Auswahl (${field.options.joinToString(", ")})"
                }
            }
            is PDTextField -> {
                var text = if (field.isMultiline) value else value.replace(Regex("\\s*\\n\\s*"), ", ")
                if (field.maxLen in 1 until text.length) text = text.take(field.maxLen)
                field.setValue(text)
            }
            else -> field.setValue(value)
        }
        return null
    }

    private fun find(form: PDAcroForm, all: List<PDTerminalField>, name: String): PDTerminalField? =
        form.getField(name) as? PDTerminalField
            ?: all.firstOrNull { it.fullyQualifiedName.equals(name, ignoreCase = true) }
            ?: all.firstOrNull { it.partialName.equals(name, ignoreCase = true) }
            ?: all.firstOrNull { it.alternateFieldName?.equals(name, ignoreCase = true) == true }

    private fun typeOf(f: PDTerminalField) = when (f) {
        is PDCheckBox -> "Ankreuzfeld"
        is PDRadioButton -> "Optionsfeld"
        is PDComboBox -> "Auswahlliste"
        is PDChoice -> "Liste"
        is PDSignatureField -> "Unterschrift"
        is PDTextField -> "Text"
        else -> f.fieldType ?: "Feld"
    }

    private fun optionsOf(f: PDTerminalField): List<String> = when (f) {
        is PDCheckBox -> listOf(f.onValue, "Off")
        is PDRadioButton -> f.onValues.toList()
        is PDChoice -> f.options
        else -> emptyList()
    }

    /** Page index of every widget annotation. */
    private fun widgetPages(doc: PDDocument): Map<COSBase, Int> {
        val map = HashMap<COSBase, Int>()
        doc.pages.forEachIndexed { i, page -> runCatching { page.annotations }.getOrNull()?.forEach { map[it.cosObject] = i } }
        return map
    }

    private fun textLines(doc: PDDocument): List<PdfTextLine> {
        val lines = mutableListOf<PdfTextLine>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, positions: MutableList<TextPosition>) {
                val first = positions.firstOrNull() ?: return
                if (text.isNotBlank()) lines += PdfTextLine(currentPageNo, first.xDirAdj * MM, first.yDirAdj * MM, text.trim())
            }
        }
        stripper.sortByPosition = true
        stripper.endPage = minOf(doc.numberOfPages, 30)
        stripper.getText(doc)
        return lines
    }
}
