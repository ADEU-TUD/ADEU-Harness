package io.github.thepytales.llmhub.docs

import android.content.Context
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/** Windows version of PdfAccessibility (PDFBox 3); same logic as the Android version with pdfbox-android. */
@Suppress("UNUSED_PARAMETER")
object PdfAccessibility {
    fun finish(context: Context, file: File, title: String) {
        val tmp = File(file.parentFile, file.name + ".a11y.tmp")
        Loader.loadPDF(file).use { doc ->
            val catalog = doc.documentCatalog
            catalog.language = "de-DE"
            doc.documentInformation.apply {
                if (title.isNotBlank()) this.title = title
                creator = "Lora"
                producer = "Lora"
            }
            val prefs = catalog.viewerPreferences ?: PDViewerPreferences(COSDictionary())
            prefs.setDisplayDocTitle(true)
            catalog.viewerPreferences = prefs
            doc.save(tmp)
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun inspect(context: Context, file: File): PdfAccessInfo {
        return Loader.loadPDF(file).use { doc ->
            val catalog = doc.documentCatalog
            val notEmbedded = mutableSetOf<String>()
            var images = 0
            doc.pages.take(MAX_PAGES).forEach { page ->
                val res = page.resources ?: return@forEach
                res.fontNames.forEach { name ->
                    runCatching { res.getFont(name) }.getOrNull()?.let { f ->
                        if (!f.isEmbedded) notEmbedded += f.name.orEmpty()
                    }
                }
                images += res.xObjectNames.count { runCatching { res.isImageXObject(it) }.getOrDefault(false) }
            }
            val text = runCatching {
                PDFTextStripper().apply { startPage = 1; endPage = minOf(doc.numberOfPages, 5) }.getText(doc)
            }.getOrDefault("")
            val fields = catalog.acroForm?.fieldTree?.filterIsInstance<PDTerminalField>()?.filter { it !is PDPushButton }.orEmpty()
            PdfAccessInfo(
                pages = doc.numberOfPages,
                tagged = catalog.markInfo?.isMarked == true && catalog.structureTreeRoot != null,
                language = catalog.language.orEmpty(),
                title = doc.documentInformation?.title.orEmpty(),
                displayTitle = catalog.viewerPreferences?.displayDocTitle() == true,
                textChars = text.trim().length,
                bookmarks = catalog.documentOutline?.firstChild != null,
                fontsNotEmbedded = notEmbedded.filter { it.isNotBlank() },
                images = images,
                formFields = fields.size,
                fieldsWithoutTooltip = fields.count { it.alternateFieldName.isNullOrBlank() },
                encrypted = doc.isEncrypted,
            )
        }
    }

    private const val MAX_PAGES = 60
}
