package io.github.thepytales.llmhub.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

/**
 * PDF-Werkzeugkasten Stufe 2 (AP-15), Android version with pdfbox-android; pictures of pages come from Android's
 * PdfRenderer. The Windows version (same path under desktop/) has the same functions with PDFBox 3.
 */
object PdfExtras {
    fun pageTexts(context: Context?, file: File, maxPages: Int = 2000): List<String> {
        PdfTools.init(context)
        return PdfTools.load(file).use { doc ->
            (0 until minOf(doc.numberOfPages, maxPages)).map { i ->
                PDFTextStripper().apply { startPage = i + 1; endPage = i + 1; sortByPosition = true }.getText(doc)
            }
        }
    }

    private fun <T> rendered(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(block) }

    private fun bitmap(renderer: PdfRenderer, index: Int, dpi: Int): Bitmap = renderer.openPage(index).use { page ->
        // The page size is in points (1/72 inch).
        val w = (page.width * dpi / 72f).toInt().coerceIn(32, 4000)
        val h = (page.height * dpi / 72f).toInt().coerceIn(32, 5600)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        }
    }

    fun pageImages(context: Context?, file: File, indices: List<Int>, dpi: Int, dir: File, jpeg: Boolean = false): List<File> {
        dir.mkdirs()
        return rendered(file) { r ->
            indices.map { i ->
                val bmp = bitmap(r, i, dpi)
                File(dir, "seite-${i + 1}.${if (jpeg) "jpg" else "png"}").also { f ->
                    f.outputStream().use { bmp.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 88, it) }
                    bmp.recycle()
                }
            }
        }
    }

    private fun displayMatrix(page: PDPage): Matrix {
        val (w, h) = PdfTools.displayed(page)
        val (ox, oy) = PdfTools.toUser(page, 0f, 1f)
        val (xx, xy) = PdfTools.toUser(page, 1f, 1f)
        val (yx, yy) = PdfTools.toUser(page, 0f, 0f)
        return Matrix((xx - ox) / w, (xy - oy) / w, (yx - ox) / h, (yy - oy) / h, ox, oy)
    }

    private fun writable(font: PDFont, text: String): String = buildString {
        text.forEach { ch -> append(if (runCatching { font.getStringWidth(ch.toString()) }.isSuccess) ch else '?') }
    }

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
                                cs.setFont(font, size)
                                cs.setHorizontalScaling(if (natural > 0f) (word.box.w * w / natural * 100f).coerceIn(10f, 400f) else 100f)
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
                    val bytes = Base64.getDecoder().decode(image)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException("Das Bild der Unterschrift ist nicht lesbar.")
                    cs.drawImage(LosslessFactory.createFromImage(doc, bmp), x0, top - bh, bw, bh)
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

    fun imagesToPdf(context: Context?, images: List<Pair<String, ByteArray>>, out: File) {
        PdfTools.init(context)
        PDDocument().use { doc ->
            images.forEach { (name, bytes) ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalArgumentException("„$name“ ist kein lesbares Bild.")
                val img = if (name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }) JPEGFactory.createFromImage(doc, bmp) else LosslessFactory.createFromImage(doc, bmp)
                val landscape = bmp.width > bmp.height
                val a4 = if (landscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                val page = PDPage(a4)
                doc.addPage(page)
                val scale = minOf(a4.width / bmp.width, a4.height / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, (a4.width - dw) / 2, (a4.height - dh) / 2, dw, dh) }
            }
            doc.save(out)
        }
    }

    fun pagesZip(context: Context?, file: File, dpi: Int, out: File) {
        rendered(file) { r ->
            java.util.zip.ZipOutputStream(out.outputStream()).use { zip ->
                for (i in 0 until r.pageCount) {
                    val bmp = bitmap(r, i, dpi)
                    val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    bmp.recycle()
                    zip.putNextEntry(java.util.zip.ZipEntry("Seite-${(i + 1).toString().padStart(3, '0')}.png"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }
}
