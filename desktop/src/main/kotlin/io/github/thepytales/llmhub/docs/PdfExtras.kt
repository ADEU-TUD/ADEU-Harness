package io.github.thepytales.llmhub.docs

import android.content.Context
import io.github.thepytales.llmhub.ui.tr
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

/**
 * PDF-Werkzeugkasten Stufe 2 (AP-15), Windows version with PDFBox 3 (the Android version under app/ has the same
 * functions with pdfbox-android): page texts and pictures, the invisible text layer of the OCR, signatures and pictures
 * as PDF pages.
 */
object PdfExtras {
    /** The text of every page (index = page − 1). */
    fun pageTexts(context: Context?, file: File, maxPages: Int = 2000): List<String> {
        PdfTools.init(context)
        return PdfTools.load(file).use { doc ->
            (0 until minOf(doc.numberOfPages, maxPages)).map { i ->
                PDFTextStripper().apply { startPage = i + 1; endPage = i + 1; sortByPosition = true }.getText(doc)
            }
        }
    }

    /** Pages [indices] as pictures at [dpi] into [dir] (PNG, grey for OCR; JPEG in color for a vision model). */
    fun pageImages(context: Context?, file: File, indices: List<Int>, dpi: Int, dir: File, jpeg: Boolean = false): List<File> {
        PdfTools.init(context)
        dir.mkdirs()
        return PdfTools.load(file).use { doc ->
            val renderer = PDFRenderer(doc)
            indices.map { i ->
                val img = renderer.renderImageWithDPI(i, dpi.toFloat(), if (jpeg) ImageType.RGB else ImageType.GRAY)
                File(dir, "seite-${i + 1}.${if (jpeg) "jpg" else "png"}").also { ImageIO.write(img, if (jpeg) "jpg" else "png", it) }
            }
        }
    }

    /** Maps the displayed page (points, origin bottom left, rotation applied) to the PDF's user space. */
    private fun displayMatrix(page: PDPage): Matrix {
        val (w, h) = PdfTools.displayed(page)
        val (ox, oy) = PdfTools.toUser(page, 0f, 1f)
        val (xx, xy) = PdfTools.toUser(page, 1f, 1f)
        val (yx, yy) = PdfTools.toUser(page, 0f, 0f)
        return Matrix((xx - ox) / w, (xy - oy) / w, (yx - ox) / h, (yy - oy) / h, ox, oy)
    }

    /** [text] with only characters the standard font can write (others become „?“). */
    private fun writable(font: PDFont, text: String): String = buildString {
        text.forEach { ch ->
            val ok = runCatching { font.getStringWidth(ch.toString()) }.isSuccess
            append(if (ok) ch else '?')
        }
    }

    /**
     * Makes scans searchable: the recognized words as invisible text over the picture of each page (like Acrobat's
     * OCR), so search, copy and the app's reading find them. Pages that already have this layer are left out by the
     * caller.
     */
    fun addTextLayer(context: Context?, file: File, pages: List<OcrPage>, out: File) {
        PdfTools.init(context)
        PdfTools.load(file).use { doc ->
            val font = PdfTools.font()
            pages.filter { it.page in 0 until doc.numberOfPages && it.lines.isNotEmpty() }.forEach { p ->
                val page = doc.getPage(p.page)
                val (w, h) = PdfTools.displayed(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(displayMatrix(page))
                    cs.beginText()
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    p.lines.forEach { line ->
                        line.words.forEach { word ->
                            val text = writable(font, word.text.trim())
                            if (text.isNotEmpty()) {
                                val size = (word.box.h * h * 0.9f).coerceIn(2f, 72f)
                                val natural = font.getStringWidth(text) / 1000f * size
                                val target = word.box.w * w
                                cs.setFont(font, size)
                                cs.setHorizontalScaling(if (natural > 0f) (target / natural * 100f).coerceIn(10f, 400f) else 100f)
                                cs.setTextMatrix(Matrix.getTranslateInstance(word.box.x * w, (1 - word.box.bottom) * h + word.box.h * h * 0.2f))
                                cs.showText(text)
                            }
                        }
                    }
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    /**
     * Puts [signature] on page [page] into [box] (fractions of the displayed page); [caption] (e.g. „Dresden,
     * 01.11.2026“) goes below it in small print.
     */
    fun sign(context: Context?, file: File, page: Int, box: PdfBox, signature: Signature, caption: String?, out: File) {
        PdfTools.init(context)
        PdfTools.load(file).use { doc ->
            val p = doc.getPage(page)
            val (w, h) = PdfTools.displayed(p)
            val x0 = box.x * w
            val top = (1 - box.y) * h
            val bw = box.w * w
            val bh = box.h * h
            PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.transform(displayMatrix(p))
                val image = signature.image
                if (image != null) {
                    val img = PDImageXObject.createFromByteArray(doc, Base64.getDecoder().decode(image), "unterschrift")
                    cs.drawImage(img, x0, top - bh, bw, bh)
                } else {
                    val col = signature.color
                    cs.setStrokingColor(PDColor(floatArrayOf(((col shr 16) and 0xFF) / 255f, ((col shr 8) and 0xFF) / 255f, (col and 0xFF) / 255f), PDDeviceRGB.INSTANCE))
                    cs.setLineWidth((bh * 0.03f).coerceIn(0.7f, 2.2f))
                    cs.setLineCapStyle(1)
                    cs.setLineJoinStyle(1)
                    signature.strokes.forEach { s ->
                        if (s.isEmpty()) return@forEach
                        cs.moveTo(x0 + s[0].x * bw, top - s[0].y * bh)
                        if (s.size == 1) cs.lineTo(x0 + s[0].x * bw + 0.3f, top - s[0].y * bh) else s.drop(1).forEach { pt -> cs.lineTo(x0 + pt.x * bw, top - pt.y * bh) }
                        cs.stroke()
                    }
                }
                if (!caption.isNullOrBlank()) {
                    val font = PdfTools.font()
                    cs.beginText()
                    cs.setFont(font, 9f)
                    cs.setNonStrokingColor(PDColor(floatArrayOf(0.2f, 0.2f, 0.2f), PDDeviceRGB.INSTANCE))
                    cs.newLineAtOffset(x0, top - bh - 10f)
                    cs.showText(writable(font, caption.trim()))
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    /** Pictures (PNG, JPEG, GIF, BMP …) as pages of a new PDF: each fitted on an A4 page (portrait or landscape). */
    fun imagesToPdf(context: Context?, images: List<Pair<String, ByteArray>>, out: File) {
        PdfTools.init(context)
        PDDocument().use { doc ->
            images.forEachIndexed { i, (name, bytes) ->
                val img = runCatching { PDImageXObject.createFromByteArray(doc, bytes, name) }.getOrElse {
                    // WebP and others ImageIO lacks: through Skia as JPEG.
                    val jpeg = org.jetbrains.skia.Image.makeFromEncoded(bytes).encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 92)?.bytes
                        ?: throw IllegalArgumentException(tr("„{0}“ ist kein lesbares Bild.", name))
                    PDImageXObject.createFromByteArray(doc, jpeg, "bild$i.jpg")
                }
                val landscape = img.width > img.height
                val a4 = if (landscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                val page = PDPage(a4)
                doc.addPage(page)
                val scale = minOf(a4.width / img.width, a4.height / img.height)
                val dw = img.width * scale
                val dh = img.height * scale
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, (a4.width - dw) / 2, (a4.height - dh) / 2, dw, dh) }
            }
            doc.save(out)
        }
    }

    /** Pages as PNG pictures in a ZIP (for slides or a website). */
    fun pagesZip(context: Context?, file: File, dpi: Int, out: File) {
        PdfTools.init(context)
        PdfTools.load(file).use { doc ->
            val renderer = PDFRenderer(doc)
            java.util.zip.ZipOutputStream(out.outputStream()).use { zip ->
                for (i in 0 until doc.numberOfPages) {
                    val bytes = ByteArrayOutputStream().also { ImageIO.write(renderer.renderImageWithDPI(i, dpi.toFloat(), ImageType.RGB), "png", it) }.toByteArray()
                    zip.putNextEntry(java.util.zip.ZipEntry("Seite-${(i + 1).toString().padStart(3, '0')}.png"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }
}
