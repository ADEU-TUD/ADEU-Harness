package io.github.thepytales.llmhub.docs

import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.Color
import java.awt.RenderingHints
import java.awt.color.ColorSpace
import java.awt.color.ICC_Profile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.imageio.ImageIO

/**
 * PDF-Werkzeugkasten Stufe 3 (AP-26), Windows: echtes Schwärzen, Text ersetzen, Bilder tauschen oder entfernen,
 * verkleinern, PDF/A-2b, Passwort, Metadaten und die Prüfung nach PDF/UA. Android has a version of this file that
 * says these functions are on the PC.
 */
object PdfStage3 {
    const val SUPPORTED = true

    // --- Finding text with its place --------------------------------------------------------------------------------

    /** Every character of a page with its box (fractions of the displayed page, origin top left). */
    private fun glyphs(doc: PDDocument, pageIndex: Int): Pair<String, List<PdfBox>> {
        val page = doc.getPage(pageIndex)
        val (w, h) = PdfTools.displayed(page)
        val text = StringBuilder()
        val boxes = mutableListOf<PdfBox>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(s: String, positions: MutableList<TextPosition>) {
                for (p in positions) {
                    val ch = p.unicode ?: continue
                    val hh = (p.heightDir.takeIf { it > 0 } ?: p.fontSizeInPt) * 1.15f
                    val box = PdfBox(p.xDirAdj / w, (p.yDirAdj - hh) / h, p.widthDirAdj / w, (hh * 1.25f) / h)
                    for (c in ch) { text.append(c); boxes += box }
                }
                text.append(' '); boxes += PdfBox(0f, 0f, 0f, 0f)
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(doc)
        return text.toString() to boxes
    }

    /**
     * Where [terms] stand in the PDF (case ignored; spaces in a term match any whitespace). [wholeWords]: „Meier“ does
     * not hit „Meierhof“.
     */
    fun find(file: File, terms: List<String>, wholeWords: Boolean = true, limit: Int = 2000): List<PdfRedaction> {
        val wanted = terms.map { it.trim() }.filter { it.length >= 2 }.distinct()
        if (wanted.isEmpty()) return emptyList()
        return PdfTools.load(file).use { doc ->
            val out = mutableListOf<PdfRedaction>()
            for (i in 0 until doc.numberOfPages) {
                val (text, boxes) = glyphs(doc, i)
                for (t in wanted) {
                    val pattern = (if (wholeWords) "(?<![\\p{L}\\p{N}])" else "") + t.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) } + if (wholeWords) "(?![\\p{L}\\p{N}])" else ""
                    Regex(pattern, RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
                        val parts = boxes.subList(m.range.first, m.range.last + 1).filter { it.w > 0 || it.h > 0 }
                        // One box per line of the hit.
                        parts.groupBy { (it.y * 200).toInt() }.values.forEach { line ->
                            val x = line.minOf { it.x }
                            val y = line.minOf { it.y }
                            out += PdfRedaction(i, PdfBox(x, y, line.maxOf { it.right } - x, line.maxOf { it.bottom } - y), m.value)
                        }
                    }
                }
                if (out.size >= limit) break
            }
            out
        }
    }

    // --- Schwärzen ------------------------------------------------------------------------------------------------

    /**
     * Echtes Schwärzen: every page with a black box is drawn as a picture at [dpi] with the boxes burnt in – the text,
     * pictures and comments below them are gone from the file, not only covered. Other pages stay as they are.
     * [cleanMetadata] also removes title, author and the hidden XMP data.
     */
    fun redact(file: File, boxes: List<PdfRedaction>, out: File, dpi: Int = 200, cleanMetadata: Boolean = true) {
        PdfTools.load(file).use { doc ->
            val renderer = PDFRenderer(doc)
            val pages = boxes.groupBy { it.page }
            val images = pages.mapValues { (i, list) ->
                val img = renderer.renderImageWithDPI(i, dpi.toFloat(), ImageType.RGB)
                val g = img.createGraphics()
                g.color = Color.BLACK
                for (b in list) {
                    val pad = dpi / 72f
                    g.fillRect(
                        (b.box.x * img.width - pad).toInt(), (b.box.y * img.height - pad).toInt(),
                        (b.box.w * img.width + 2 * pad).toInt() + 1, (b.box.h * img.height + 2 * pad).toInt() + 1,
                    )
                }
                g.dispose()
                img
            }
            for ((i, img) in images) {
                val old = doc.getPage(i)
                val (w, h) = PdfTools.displayed(old)
                val page = PDPage(PDRectangle(w, h))
                val x = JPEGFactory.createFromImage(doc, img, 0.85f)
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(x, 0f, 0f, w, h) }
                doc.pages.insertBefore(page, old)
                doc.pages.remove(old)
            }
            if (cleanMetadata) clear(doc)
            doc.save(out)
        }
    }

    private fun clear(doc: PDDocument) {
        doc.documentInformation = PDDocumentInformation().apply { producer = "Lora" }
        doc.documentCatalog.metadata = null
    }

    // --- Text ersetzen ----------------------------------------------------------------------------------------------

    /**
     * Replaces [find] by [replace] where it stands (on [page], 0-based, or all pages): a white box over the old text and
     * the new text in Helvetica of the same height. The old text stays underneath in the file – for confidential text
     * „Schwärzen“ is the right tool. Returns how often it was replaced.
     */
    fun replaceText(file: File, find: String, replace: String, page: Int?, out: File): Int {
        val hits = find(file, listOf(find), wholeWords = false).filter { page == null || it.page == page }
        if (hits.isEmpty()) return 0
        PdfTools.load(file).use { doc ->
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            for ((i, list) in hits.groupBy { it.page }) {
                val p = doc.getPage(i)
                val h = PdfTools.displayed(p).second
                PDPageContentStream(doc, p, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (hit in list) {
                        val (x0, y0) = PdfTools.toUser(p, hit.box.x, 1f - hit.box.bottom)
                        val (x1, y1) = PdfTools.toUser(p, hit.box.right, 1f - hit.box.y)
                        cs.setNonStrokingColor(Color.WHITE)
                        cs.addRect(minOf(x0, x1), minOf(y0, y1), kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0))
                        cs.fill()
                        val size = (hit.box.h * h * 0.72f).coerceIn(4f, 72f)
                        val text = writable(replace)
                        if (text.isNotEmpty() && p.rotation % 360 == 0) {
                            cs.beginText()
                            cs.setNonStrokingColor(Color.BLACK)
                            cs.setFont(font, size)
                            cs.newLineAtOffset(minOf(x0, x1), minOf(y0, y1) + hit.box.h * h * 0.22f)
                            cs.showText(text)
                            cs.endText()
                        }
                    }
                }
            }
            doc.save(out)
        }
        return hits.size
    }

    private fun writable(s: String) = s.map { c -> if (c.code in 32..126 || c in "äöüÄÖÜßéèàáçñ€§°„“–") c else '?' }.joinToString("")

    // --- Bilder --------------------------------------------------------------------------------------------------

    /** The pictures of the pages (page 0-based, name in the page's resources, size in pixels, bytes in the file). */
    fun images(file: File): List<PdfImageInfo> = PdfTools.load(file).use { doc ->
        val out = mutableListOf<PdfImageInfo>()
        doc.pages.forEachIndexed { i, page ->
            val res = page.resources ?: return@forEachIndexed
            for (name in res.xObjectNames) {
                val x = runCatching { res.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                out += PdfImageInfo(i, name.name, x.width, x.height, x.cosObject.length.toLong())
            }
        }
        out
    }

    /** A small preview (PNG) of picture [name] on [page]. */
    fun imagePreview(file: File, page: Int, name: String, maxPx: Int = 240): ByteArray? = PdfTools.load(file).use { doc ->
        val x = doc.getPage(page).resources?.getXObject(COSName.getPDFName(name)) as? PDImageXObject ?: return@use null
        val img = scaled(x.image, maxPx)
        java.io.ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    /** Replaces picture [name] on [page] by [image] (PNG/JPEG bytes) or, with null, removes it (a white pixel). */
    fun replaceImage(file: File, page: Int, name: String, image: ByteArray?, out: File) {
        PdfTools.load(file).use { doc ->
            val res = doc.getPage(page).resources ?: error("Die Seite hat keine Bilder.")
            val cos = COSName.getPDFName(name)
            res.getXObject(cos) as? PDImageXObject ?: error("Bild „$name“ nicht gefunden.")
            val next = if (image == null) {
                LosslessFactory.createFromImage(doc, BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, 0xFFFFFF) })
            } else {
                val img = ImageIO.read(ByteArrayInputStream(image)) ?: error("Das Bild lässt sich nicht lesen (PNG oder JPEG verwenden).")
                if (img.colorModel.hasAlpha()) LosslessFactory.createFromImage(doc, img) else JPEGFactory.createFromImage(doc, img, 0.9f)
            }
            res.put(cos, next)
            doc.save(out)
        }
    }

    // --- Verkleinern -----------------------------------------------------------------------------------------------

    /**
     * Makes the file smaller: pictures larger than [maxPx] (longest side) are scaled down and stored as JPEG with
     * [quality]; the file is written compressed. Returns the sizes before and after.
     */
    fun shrink(file: File, out: File, maxPx: Int = 1600, quality: Float = 0.75f): Pair<Long, Long> {
        PdfTools.load(file).use { doc ->
            val done = HashMap<Any, PDImageXObject>()
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val x = runCatching { res.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
                    if (x.isStencil) continue
                    val already = done[x.cosObject]
                    if (already != null) {
                        res.put(name, already)
                        continue
                    }
                    val big = maxOf(x.width, x.height) > maxPx
                    val raw = x.cosObject.length > 200_000
                    if (!big && !raw) continue
                    val img = runCatching { x.image }.getOrNull() ?: continue
                    val smaller = if (big) scaled(img, maxPx) else img
                    val next = if (smaller.colorModel.hasAlpha()) LosslessFactory.createFromImage(doc, smaller) else JPEGFactory.createFromImage(doc, toRgb(smaller), quality)
                    if (next.cosObject.length < x.cosObject.length) {
                        res.put(name, next)
                        done[x.cosObject] = next
                    }
                }
            }
            doc.save(out)
        }
        return file.length() to out.length()
    }

    private fun scaled(img: BufferedImage, maxPx: Int): BufferedImage {
        val f = maxPx.toDouble() / maxOf(img.width, img.height)
        if (f >= 1) return img
        val w = (img.width * f).toInt().coerceAtLeast(1)
        val h = (img.height * f).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, if (img.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(img, 0, 0, w, h, null)
            dispose()
        }
        return out
    }

    private fun toRgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_RGB || img.type == BufferedImage.TYPE_3BYTE_BGR) return img
        return BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() } }
    }

    // --- PDF/A ---------------------------------------------------------------------------------------------------

    /**
     * Prepares the file for archiving as PDF/A-2b: XMP metadata with the PDF/A identification, sRGB output intent,
     * no encryption, no JavaScript, no embedded files. Returns what still keeps it from being valid PDF/A (fonts that
     * are not embedded) – an empty list means the usual requirements are met; strict validation: veraPDF.
     */
    fun toPdfA(file: File, title: String, out: File): List<String> {
        val problems = mutableListOf<String>()
        PdfTools.load(file).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val catalog = doc.documentCatalog
            catalog.names?.let { names ->
                if (names.javaScript != null) { names.cosObject.removeItem(COSName.JAVA_SCRIPT); problems += "JavaScript entfernt." }
                if (names.embeddedFiles != null) { names.cosObject.removeItem(COSName.EMBEDDED_FILES); problems += "Eingebettete Dateien entfernt (in PDF/A-2 nicht erlaubt)." }
            }
            catalog.cosObject.removeItem(COSName.OPEN_ACTION)
            val info = doc.documentInformation
            val t = info.title?.takeIf { it.isNotBlank() } ?: title
            info.title = t
            info.producer = "Lora"
            val now = Calendar.getInstance()
            if (info.creationDate == null) info.creationDate = now
            info.modificationDate = now
            catalog.metadata = PDMetadata(doc).apply { importXMPMetadata(xmp(t, info.author.orEmpty(), info.creationDate ?: now, now)) }
            if (catalog.outputIntents.isEmpty()) {
                val icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB).data
                val intent = PDOutputIntent(doc, ByteArrayInputStream(icc))
                intent.info = "sRGB IEC61966-2.1"
                intent.outputCondition = "sRGB IEC61966-2.1"
                intent.outputConditionIdentifier = "sRGB IEC61966-2.1"
                intent.registryName = "http://www.color.org"
                catalog.addOutputIntent(intent)
            }
            if (catalog.language.isNullOrBlank()) catalog.language = "de-DE"
            doc.version = 1.7f
            val fonts = mutableSetOf<String>()
            doc.pages.forEach { page ->
                val res = page.resources ?: return@forEach
                res.fontNames.forEach { n -> runCatching { res.getFont(n) }.getOrNull()?.let { f -> if (!f.isEmbedded) fonts += f.name.orEmpty() } }
            }
            if (fonts.isNotEmpty()) problems += "Nicht eingebettete Schriften: ${fonts.filter { it.isNotBlank() }.joinToString(", ")} – die Datei aus dem Ursprungsprogramm mit eingebetteten Schriften neu erzeugen (Word: „Als PDF speichern“ › Optionen › „ISO 19005-1-kompatibel (PDF/A)“)."
            doc.save(out)
        }
        return problems
    }

    private fun xmp(title: String, author: String, created: Calendar, modified: Calendar): ByteArray {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """<?xpacket begin="${'﻿'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
<rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/"><pdfaid:part>2</pdfaid:part><pdfaid:conformance>B</pdfaid:conformance></rdf:Description>
<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:format>application/pdf</dc:format><dc:title><rdf:Alt><rdf:li xml:lang="x-default">${esc(title)}</rdf:li></rdf:Alt></dc:title>${if (author.isNotBlank()) "<dc:creator><rdf:Seq><rdf:li>${esc(author)}</rdf:li></rdf:Seq></dc:creator>" else ""}</rdf:Description>
<rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/"><xmp:CreateDate>${f.format(created.time)}</xmp:CreateDate><xmp:ModifyDate>${f.format(modified.time)}</xmp:ModifyDate><xmp:CreatorTool>Lora</xmp:CreatorTool></rdf:Description>
<rdf:Description rdf:about="" xmlns:pdf="http://ns.adobe.com/pdf/1.3/"><pdf:Producer>Lora</pdf:Producer></rdf:Description>
</rdf:RDF></x:xmpmeta>
<?xpacket end="w"?>""".toByteArray(Charsets.UTF_8)
    }

    /** Whether [file] carries the PDF/A identification of [toPdfA]. */
    fun isPdfA(file: File): Boolean = Loader.loadPDF(file).use { doc ->
        doc.documentCatalog.metadata?.exportXMPMetadata()?.readBytes()?.decodeToString()?.contains("<pdfaid:part>") == true && doc.documentCatalog.outputIntents.isNotEmpty()
    }

    // --- Passwort -----------------------------------------------------------------------------------------------

    /**
     * Protects the file with AES-256: [openPassword] is needed to open it (empty: anyone may open it), [ownerPassword]
     * to change the rights; [print] and [copy] as allowed. Screen readers may always read it.
     */
    fun protect(file: File, openPassword: String, ownerPassword: String, print: Boolean, copy: Boolean, out: File) {
        PdfTools.load(file).use { doc ->
            val rights = AccessPermission().apply {
                setCanPrint(print)
                setCanPrintFaithful(print)
                setCanExtractContent(copy)
                setCanExtractForAccessibility(true)
                setCanModify(false)
                setCanAssembleDocument(false)
            }
            val policy = StandardProtectionPolicy(ownerPassword.ifBlank { java.util.UUID.randomUUID().toString() }, openPassword, rights)
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            doc.save(out)
        }
    }

    /** Removes the protection; [password] is the opening or owner password (empty for files that open without one). */
    fun unprotect(file: File, password: String, out: File) {
        val doc = try {
            Loader.loadPDF(file, password)
        } catch (e: org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw IllegalArgumentException("Das Passwort stimmt nicht.")
        }
        doc.use {
            it.isAllSecurityToBeRemoved = true
            it.save(out)
        }
    }

    fun encrypted(file: File): Boolean = runCatching { Loader.loadPDF(file).use { it.isEncrypted } }.getOrDefault(true)

    // --- Metadaten -----------------------------------------------------------------------------------------------

    fun meta(file: File): PdfMeta = PdfTools.load(file).use { doc ->
        val i = doc.documentInformation
        PdfMeta(i.title.orEmpty(), i.author.orEmpty(), i.subject.orEmpty(), i.keywords.orEmpty(), i.creator.orEmpty(), i.producer.orEmpty(), doc.documentCatalog.language.orEmpty(),
            doc.documentCatalog.metadata != null)
    }

    /** Writes [m] (title, author, subject, keywords, language); [clean]: everything else goes, also the XMP data. */
    fun setMeta(file: File, m: PdfMeta, clean: Boolean, out: File) {
        PdfTools.load(file).use { doc ->
            if (clean) clear(doc)
            doc.documentInformation.apply {
                title = m.title.ifBlank { null }
                author = m.author.ifBlank { null }
                subject = m.subject.ifBlank { null }
                keywords = m.keywords.ifBlank { null }
                modificationDate = Calendar.getInstance()
            }
            doc.documentCatalog.language = m.language.ifBlank { null }
            doc.save(out)
        }
    }

    // --- Barrierefreiheit nach PDF/UA ----------------------------------------------------------------------------

    /** The main points of PDF/UA (ISO 14289) that can be checked without a person; the rest needs a screen reader test. */
    fun uaCheck(context: android.content.Context, file: File): List<PdfUaFinding> {
        val info = PdfAccessibility.inspect(context, file)
        val out = mutableListOf<PdfUaFinding>()
        fun add(ok: Boolean, good: String, bad: String, fixable: Boolean = false) {
            out += PdfUaFinding(if (ok) PdfUaFinding.Level.OK else PdfUaFinding.Level.ERROR, if (ok) good else bad, fixable && !ok)
        }
        add(info.tagged, "Mit Struktur (Tags) – Screenreader kennen Überschriften, Absätze, Listen.",
            "Keine Tags: Screenreader lesen nur ungegliederten Text. Im Ursprungsprogramm mit Tags neu erzeugen (Word: Speichern unter › PDF › Optionen › „Dokumentstrukturtags für Barrierefreiheit“).")
        add(info.language.isNotBlank(), "Sprache angegeben (${info.language}).", "Keine Sprache angegeben.", fixable = true)
        add(info.title.isNotBlank(), "Titel: ${info.title}", "Kein Titel in den Metadaten.", fixable = true)
        add(info.displayTitle, "Das Fenster zeigt den Titel statt des Dateinamens.", "Das Fenster zeigt den Dateinamen statt des Titels.", fixable = true)
        add(info.fontsNotEmbedded.isEmpty(), "Alle Schriften eingebettet.", "Nicht eingebettete Schriften: " + info.fontsNotEmbedded.joinToString(", "))
        add(info.textChars > 0 || info.pages == 0, "Echter Text vorhanden.", "Kein Text – ein Scan. Erst „Umwandeln › Texterkennung“, sonst ist er für Screenreader leer.")
        if (info.formFields > 0) add(info.fieldsWithoutTooltip == 0, "Alle ${info.formFields} Formularfelder haben eine Beschreibung.", "${info.fieldsWithoutTooltip} von ${info.formFields} Formularfeldern ohne Beschreibung (Tooltip).")
        if (info.pages > 20) add(info.bookmarks, "Lesezeichen vorhanden.", "Lange Datei ohne Lesezeichen – Navigation schwer.")
        add(!info.encrypted, "Nicht verschlüsselt.", "Verschlüsselt – prüfen, dass Screenreader lesen dürfen.")
        PdfTools.load(file).use { doc ->
            val root = doc.documentCatalog.structureTreeRoot
            if (root != null) {
                var figures = 0
                var missing = 0
                fun walk(node: PDStructureNode, depth: Int) {
                    if (depth > 60) return
                    for (k in node.kids) {
                        val e = k as? PDStructureElement ?: continue
                        if (e.structureType == "Figure") { figures++; if (e.alternateDescription.isNullOrBlank() && e.actualText.isNullOrBlank()) missing++ }
                        walk(e, depth + 1)
                    }
                }
                runCatching { walk(root, 0) }
                if (figures > 0) add(missing == 0, "Alle $figures Abbildungen haben einen Alternativtext.", "$missing von $figures Abbildungen ohne Alternativtext.")
            }
            val annotated = doc.pages.withIndex().filter { (_, p) -> runCatching { p.annotations.isNotEmpty() }.getOrDefault(false) }
            if (annotated.isNotEmpty()) {
                val wrong = annotated.count { (_, p) -> p.cosObject.getNameAsString(COSName.getPDFName("Tabs")) != "S" }
                add(wrong == 0, "Tab-Reihenfolge folgt der Struktur.", "Auf $wrong Seite(n) folgt die Tab-Reihenfolge nicht der Struktur.", fixable = true)
            }
        }
        return out.sortedBy { if (it.level == PdfUaFinding.Level.ERROR) 0 else 1 }
    }

    /** Fixes what can be fixed without a person: language, title, title in the window, tab order. */
    fun uaFix(context: android.content.Context, file: File, title: String, out: File) {
        file.copyTo(out, overwrite = true)
        PdfAccessibility.finish(context, out, PdfTools.load(out).use { it.documentInformation.title?.takeIf { t -> t.isNotBlank() } } ?: title)
        val tmp = File(out.parentFile, out.name + ".tabs")
        PdfTools.load(out).use { doc ->
            doc.pages.forEach { p -> if (runCatching { p.annotations.isNotEmpty() }.getOrDefault(false)) p.cosObject.setName(COSName.getPDFName("Tabs"), "S") }
            val prefs = doc.documentCatalog.viewerPreferences ?: PDViewerPreferences(org.apache.pdfbox.cos.COSDictionary())
            prefs.setDisplayDocTitle(true)
            doc.documentCatalog.viewerPreferences = prefs
            doc.save(tmp)
        }
        tmp.copyTo(out, overwrite = true)
        tmp.delete()
    }
}
