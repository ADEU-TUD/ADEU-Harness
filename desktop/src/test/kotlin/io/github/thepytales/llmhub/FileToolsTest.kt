package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.FormatConvert
import io.github.thepytales.llmhub.docs.OdfImport
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.files.Archives
import io.github.thepytales.llmhub.files.Cfb
import io.github.thepytales.llmhub.files.Exif
import io.github.thepytales.llmhub.files.FileProtect
import io.github.thepytales.llmhub.files.OfficeCrypto
import io.github.thepytales.llmhub.files.PhotoPrep
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * File tools of round 3: Fotos vom Handy aufbereiten (AP-100), Formatwandler for pictures and OpenDocument as a source
 * (AP-90), Archiv-Werkzeug (AP-99) and Passwortschutz (AP-98).
 */
class FileToolsTest {
    private val dir = createTempDirectory("lora-dateiwerkzeuge").toFile()
    private val context = android.content.Context(File(dir, "files").apply { mkdirs() }, File(dir, "cache").apply { mkdirs() })

    // ── Building test photos ──

    /** A TIFF/EXIF block (big endian) with orientation, time taken, camera and – if [gps] – a GPS block. */
    private fun exifTiff(orientation: Int, taken: String, gps: Boolean): ByteArray {
        val b = ByteBuffer.allocate(400).order(ByteOrder.BIG_ENDIAN)
        b.put('M'.code.toByte()); b.put('M'.code.toByte()); b.putShort(42); b.putInt(8)
        // IFD0 at 8: Make, Orientation, ExifIFD, (GPS)
        val n = if (gps) 4 else 3
        b.putShort(n.toShort())
        val ifd0End = 8 + 2 + n * 12 + 4
        val makeAt = ifd0End
        val exifAt = makeAt + 8
        val gpsAt = exifAt + 2 + 12 + 4 + 20
        fun entry(tag: Int, type: Int, count: Int, value: Int) { b.putShort(tag.toShort()); b.putShort(type.toShort()); b.putInt(count); b.putInt(value) }
        entry(0x010F, 2, 6, makeAt)
        b.putShort(0x0112); b.putShort(3); b.putInt(1); b.putShort(orientation.toShort()); b.putShort(0)
        entry(0x8769, 4, 1, exifAt)
        if (gps) entry(0x8825, 4, 1, gpsAt)
        b.putInt(0)
        b.position(makeAt); b.put("Apple\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1))
        b.position(exifAt); b.putShort(1); entry(0x9003, 2, 20, exifAt + 2 + 12 + 4); b.putInt(0)
        b.put((taken + "\u0000").toByteArray(Charsets.ISO_8859_1))
        if (gps) {
            b.position(gpsAt); b.putShort(1); b.putShort(1); b.putShort(2); b.putInt(2); b.put("N\u0000\u0000\u0000".toByteArray(Charsets.ISO_8859_1)); b.putInt(0)
        }
        return b.array().copyOf(b.position().coerceAtLeast(gpsAt + 30))
    }

    /** A 40 × 20 JPEG (left half red, right half blue) with an EXIF block. */
    private fun photo(name: String, orientation: Int, taken: String, gps: Boolean = true): File {
        val img = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 20) for (x in 0 until 40) img.setRGB(x, y, if (x < 20) Color.RED.rgb else Color.BLUE.rgb)
        val jpg = ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
        val tiff = exifTiff(orientation, taken, gps)
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte()) + ByteBuffer.allocate(2).putShort((tiff.size + 8).toShort()).array() + "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
        val bytes = jpg.copyOfRange(0, 2) + app1 + jpg.copyOfRange(2, jpg.size)
        return File(dir, name).apply { writeBytes(bytes) }
    }

    @Test
    fun exifOfJpeg() {
        val f = photo("a.jpg", 6, "2026:11:10 14:32:05")
        val info = Exif.read(f.readBytes())
        assertEquals(6, info.orientation)
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 32, 5), info.taken)
        assertTrue(info.hasGps)
        assertEquals("Apple", info.camera)
    }

    /** A minimal HEIF container: ftyp, meta with pitm, iinf (one Exif item), iloc and irot of the primary image. */
    private fun heif(tiff: ByteArray, irot: Int): ByteArray {
        fun box(type: String, body: ByteArray) = ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(body).array()
        fun full(type: String, version: Int, body: ByteArray) = box(type, byteArrayOf(version.toByte(), 0, 0, 0) + body)
        fun u16(v: Int) = ByteBuffer.allocate(2).putShort(v.toShort()).array()
        fun u32(v: Int) = ByteBuffer.allocate(4).putInt(v).array()
        val ftyp = box("ftyp", "heic".toByteArray() + u32(0) + "mif1heic".toByteArray())
        val pitm = full("pitm", 0, u16(1))
        val infeImage = full("infe", 2, u16(1) + u16(0) + "hvc1".toByteArray() + byteArrayOf(0))
        val infeExif = full("infe", 2, u16(2) + u16(0) + "Exif".toByteArray() + byteArrayOf(0))
        val iinf = full("iinf", 0, u16(2) + infeImage + infeExif)
        val ipco = box("ipco", box("irot", byteArrayOf(irot.toByte())))
        val ipma = full("ipma", 0, u32(1) + u16(1) + byteArrayOf(1, 0x81.toByte()))
        val iprp = box("iprp", ipco + ipma)
        val payload = u32(6) + "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
        // iloc v0: offset 4, length 4, base 0; one item (Exif), one extent – the offset is set below.
        fun iloc(offset: Int) = full("iloc", 0, byteArrayOf(0x44, 0x00) + u16(1) + u16(2) + u16(0) + u16(1) + u32(offset) + u32(payload.size))
        val metaSize = 8 + 4 + pitm.size + iinf.size + iprp.size + iloc(0).size
        val mdatStart = ftyp.size + metaSize + 8
        val meta = full("meta", 0, pitm + iinf + iprp + iloc(mdatStart))
        return ftyp + meta + box("mdat", payload)
    }

    @Test
    fun exifAndRotationOfHeic() {
        // irot 3 = 270° counter-clockwise = turn 90° clockwise to show it = orientation 6; it wins over the EXIF tag.
        val bytes = heif(exifTiff(1, "2026:11:09 08:15:00", gps = true), irot = 3)
        assertTrue(Exif.isHeif(bytes))
        val info = Exif.read(bytes)
        assertEquals(6, info.orientation)
        assertEquals(LocalDateTime.of(2026, 11, 9, 8, 15), info.taken)
        assertTrue(info.hasGps)
    }

    @Test
    fun photosArePreparedTurnedRenamedAndWithoutMetadata() = runBlocking {
        val src = File(dir, "Exkursion").apply { mkdirs() }
        photo("Exkursion/IMG_0001.jpg", 6, "2026:11:10 14:32:05").renameTo(File(src, "IMG_0001.jpg"))
        photo("Exkursion/IMG_0002.jpg", 1, "2026:11:10 14:32:40").renameTo(File(src, "IMG_0002.jpg"))
        photo("Exkursion/IMG_0003.jpg", 3, "2026:11:09 09:00:00", gps = false).renameTo(File(src, "IMG_0003.jpg"))
        val photos = PhotoPrep.photos(listOf(src))
        assertEquals(3, photos.size)
        val plan = PhotoPrep.plan(photos, PhotoPrep.Options(label = "Exkursion TU"))
        assertEquals(listOf("2026-11-09_09-00_Exkursion-TU.jpg", "2026-11-10_14-32_Exkursion-TU.jpg", "2026-11-10_14-32_Exkursion-TU_2.jpg"), plan.map { it.name })
        val result = PhotoPrep.prepare(photos, PhotoPrep.Options(label = "Exkursion TU"))
        assertTrue(result.failed.isEmpty(), result.failed.toString())
        assertEquals(File(src, "aufbereitet"), result.folder)
        // The originals stay.
        assertEquals(3, src.listFiles()!!.count { it.isFile })
        val turned = result.made.first { it.name == "2026-11-10_14-32_Exkursion-TU.jpg" }
        val img = ImageIO.read(turned)
        // Orientation 6: turned 90° clockwise – 20 × 40, red (the left half) now on top.
        assertEquals(20, img.width)
        assertEquals(40, img.height)
        assertTrue(Color(img.getRGB(10, 5)).red > 200, "oben rot")
        assertTrue(Color(img.getRGB(10, 35)).blue > 200, "unten blau")
        val upside = ImageIO.read(result.made.first { it.name.startsWith("2026-11-09") })
        assertTrue(Color(upside.getRGB(5, 10)).blue > 200, "180° gedreht: links blau")
        for (f in result.made) {
            val info = Exif.read(f.readBytes())
            assertFalse(info.hasGps, f.name)
            assertEquals(null, info.taken, f.name)
        }
        // Smaller on request.
        val small = PhotoPrep.prepare(photos, PhotoPrep.Options(maxSide = 10, target = File(dir, "klein")))
        assertTrue(small.made.all { ImageIO.read(it).let { i -> maxOf(i.width, i.height) == 10 } })
    }

    @Test
    fun picturesConvertBetweenFormats() = runBlocking {
        val png = File(dir, "logo.png")
        val img = BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 30) for (x in 0 until 30) img.setRGB(x, y, if (x < 15) 0x00000000 else Color.GREEN.rgb)
        ImageIO.write(img, "png", png)
        val targets = FormatConvert.targets(png.name)
        assertTrue("jpg" in targets && "pdf" in targets && "png" !in targets, targets.toString())
        val plan = FormatConvert.analyze(png.name, png.readBytes(), "jpg")
        assertTrue(plan.losses.any { it.title == "Transparenz" && it.severe }, plan.toString())
        assertEquals("png", plan.better)
        val jpg = FormatConvert.convert(context, png, png.name, "jpg", dir)
        val back = ImageIO.read(jpg)
        assertEquals(Color.WHITE.rgb, back.getRGB(3, 15) or 0x00010101 or Color.WHITE.rgb)
        assertTrue(Color(back.getRGB(25, 15)).green > 200)
        // A turned photo comes out upright, without its metadata.
        val photo = photo("hochkant.jpg", 6, "2026:11:10 14:32:05")
        val asPng = FormatConvert.convert(context, photo, photo.name, "png", dir)
        ImageIO.read(asPng).let { assertEquals(20, it.width); assertEquals(40, it.height) }
        assertTrue(FormatConvert.analyze(photo.name, photo.readBytes(), "png").losses.any { it.title == "Metadaten" })
        if ("webp" in FormatConvert.targets(png.name)) {
            val webp = FormatConvert.convert(context, png, png.name, "webp", dir)
            assertEquals("WEBP", String(webp.readBytes(), 8, 4, Charsets.ISO_8859_1))
        }
    }

    // ── OpenDocument as a source ──

    private fun odf(mime: String, content: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("mimetype")); z.write(mime.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("content.xml")); z.write(content.toByteArray()); z.closeEntry()
        }
        return out.toByteArray()
    }

    private val ns = "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" " +
        "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" " +
        "xmlns:presentation=\"urn:oasis:names:tc:opendocument:xmlns:presentation:1.0\" xmlns:of=\"urn:oasis:names:tc:opendocument:xmlns:of:1.2\""

    @Test
    fun openDocumentSpreadsheetKeepsValuesAndFormulas() {
        val content = """<?xml version="1.0"?><office:document-content $ns><office:body><office:spreadsheet>
            <table:table table:name="Haushalt">
              <table:table-row><table:table-cell office:value-type="string"><text:p>Posten</text:p></table:table-cell><table:table-cell office:value-type="string"><text:p>Betrag</text:p></table:table-cell><table:table-cell office:value-type="string"><text:p>Datum</text:p></table:table-cell></table:table-row>
              <table:table-row><table:table-cell office:value-type="string"><text:p>Papier</text:p></table:table-cell><table:table-cell office:value-type="float" office:value="12.5"><text:p>12,50</text:p></table:table-cell><table:table-cell office:value-type="date" office:date-value="2026-04-03"><text:p>03.04.26</text:p></table:table-cell></table:table-row>
              <table:table-row><table:table-cell office:value-type="string"><text:p>Toner</text:p></table:table-cell><table:table-cell office:value-type="float" office:value="80"><text:p>80</text:p></table:table-cell></table:table-row>
              <table:table-row><table:table-cell office:value-type="string"><text:p>Summe</text:p></table:table-cell><table:table-cell table:formula="of:=SUM([.B2:.B3])" office:value-type="float" office:value="92.5"><text:p>92,5</text:p></table:table-cell><table:table-cell table:formula="of:=[${'$'}Zweites.A1]*2" office:value-type="float" office:value="2"/></table:table-row>
              <table:table-row table:number-rows-repeated="1000"><table:table-cell table:number-columns-repeated="1024"/></table:table-row>
            </table:table>
            <table:table table:name="Zweites"><table:table-row><table:table-cell office:value-type="float" office:value="1"/></table:table-row></table:table>
            </office:spreadsheet></office:body></office:document-content>"""
        val bytes = odf("application/vnd.oasis.opendocument.spreadsheet", content)
        val imported = OfficeImport.read("haushalt.ods", bytes)!!
        assertEquals(listOf("Haushalt", "Zweites"), imported.tabs.map { it.first })
        val grid = SheetEngine.grid(imported.tabs[0].second)
        assertEquals(4, grid.size)
        assertEquals("12,5", grid[1][1])
        assertEquals("03.04.2026", grid[1][2])
        assertEquals("=SUM(B2:B3)", grid[3][1])
        assertEquals("=Zweites!A1*2", grid[3][2])
        assertEquals("=SUM(A1:A3;2,5)", OdfImport.formula("of:=SUM([.A1:.A3];2.5)"))
        assertEquals(listOf("xlsx", "csv", "pdf"), FormatConvert.targets("haushalt.ods"))
        val plan = FormatConvert.analyze("haushalt.ods", bytes, "csv")
        assertTrue(plan.losses.any { it.title == "Formeln" } && plan.losses.any { it.title == "Tabellenblätter" }, plan.toString())
        assertTrue(FormatConvert.analyze("haushalt.ods", bytes, "xlsx").losses.none { it.title == "Formeln" })
    }

    @Test
    fun openDocumentTextAndSlides() = runBlocking {
        val text = """<?xml version="1.0"?><office:document-content $ns><office:body><office:text>
            <text:h text:outline-level="1">Protokoll</text:h><text:p>Erster Absatz.</text:p>
            <text:list><text:list-item><text:p>Punkt eins</text:p></text:list-item><text:list-item><text:p>Punkt zwei</text:p></text:list-item></text:list>
            <text:p>Mit <office:annotation><text:p>Kommentar</text:p></office:annotation>Kommentar.</text:p>
            </office:text></office:body></office:document-content>"""
        val odt = File(dir, "protokoll.odt").apply { writeBytes(odf("application/vnd.oasis.opendocument.text", text)) }
        val md = OfficeImport.read(odt.name, odt.readBytes())!!.tabs.first().second
        assertTrue("# Protokoll" in md && "- Punkt eins" in md, md)
        assertTrue(FormatConvert.analyze(odt.name, odt.readBytes(), "docx").losses.any { it.title == "Kommentare" })
        val docx = FormatConvert.convert(context, odt, odt.name, "docx", dir)
        assertTrue("Punkt zwei" in OfficeImport.read(docx.name, docx.readBytes())!!.tabs.first().second)

        val slides = """<?xml version="1.0"?><office:document-content $ns><office:body><office:presentation>
            <draw:page draw:name="1"><draw:frame presentation:class="title"><draw:text-box><text:p>Semesterstart</text:p></draw:text-box></draw:frame>
              <draw:frame presentation:class="outline"><draw:text-box><text:list><text:list-item><text:p>Begrüßung</text:p></text:list-item><text:list-item><text:p>Termine</text:p></text:list-item></text:list></draw:text-box></draw:frame>
              <presentation:notes><draw:frame><draw:text-box><text:p>Langsam sprechen</text:p></draw:text-box></draw:frame></presentation:notes></draw:page>
            <draw:page draw:name="2"><draw:frame presentation:class="title"><draw:text-box><text:p>Prüfungen</text:p></draw:text-box></draw:frame>
              <draw:frame presentation:class="outline"><draw:text-box><text:p>Anmeldung bis 30.11.</text:p></draw:text-box></draw:frame></draw:page>
            </office:presentation></office:body></office:document-content>"""
        val odp = File(dir, "start.odp").apply { writeBytes(odf("application/vnd.oasis.opendocument.presentation", slides)) }
        val deck = OfficeImport.read(odp.name, odp.readBytes())!!.tabs.first().second
        assertTrue("# Semesterstart" in deck && "- Begrüßung" in deck && "## Prüfungen" in deck && "Notizen: Langsam sprechen" in deck, deck)
        val pptx = FormatConvert.convert(context, odp, odp.name, "pptx", dir)
        val back = OfficeImport.read(pptx.name, pptx.readBytes())!!.tabs.first().second
        assertTrue("Prüfungen" in back && "Termine" in back, back)
    }

    // ── Archives ──

    @Test
    fun archivesOpenLikeFoldersAndPackWithPasswordAndParts() {
        val src = File(dir, "Projekt").apply { mkdirs() }
        File(src, "Kapitel 1").mkdirs()
        File(src, "Kapitel 1/text.txt").writeText("Hallo Archiv")
        File(src, "Kapitel 1/daten.csv").writeText("a;b\n1;2\n")
        File(src, "liesmich.md").writeText("# Liesmich")
        val big = File(src, "zufall.bin").apply { writeBytes(ByteArray(3 * 1024 * 1024).also { java.util.Random(7).nextBytes(it) }) }

        val zip = Archives.outFile(listOf(src))
        assertEquals("Projekt.zip", zip.name)
        Archives.pack(listOf(src), zip, Archives.Strength.MAX)
        val entries = Archives.list(zip)
        val (folders, files) = Archives.inFolder(entries, "Projekt")
        assertEquals(listOf("Kapitel 1"), folders.map { it.name })
        assertEquals(setOf("liesmich.md", "zufall.bin"), files.map { it.name }.toSet())
        assertEquals(listOf("Projekt/Kapitel 1/daten.csv"), Archives.search(entries, "kapitel csv").map { it.path })
        val one = Archives.extract(zip, File(dir, "raus"), listOf("Projekt/Kapitel 1/text.txt"))
        assertEquals("Hallo Archiv", one.single().readText())

        // With a password (AES-256): without it, nothing comes out.
        val locked = File(dir, "geheim.zip")
        Archives.pack(listOf(src), locked, password = "Sommer2026!".toCharArray())
        assertTrue(Archives.needsPassword(locked))
        assertTrue(FileProtect.isProtected(locked))
        assertFailsWith<Archives.PasswordNeeded> { Archives.extract(locked, File(dir, "raus2")) }
        assertFailsWith<Archives.PasswordNeeded> { Archives.extract(locked, File(dir, "raus3"), password = "falsch".toCharArray()) }
        val got = Archives.extract(locked, File(dir, "raus4"), password = "Sommer2026!".toCharArray())
        assertEquals(4, got.size)

        // In parts of 1 MB for mail: .zip plus .z01 …, opened again as a whole.
        val parts = Archives.pack(listOf(src), File(dir, "Teile.zip"), Archives.Strength.STORE, partMb = 1)
        assertTrue(parts.size >= 3, parts.toString())
        assertTrue(parts.all { it.length() <= 1024 * 1024 })
        val whole = Archives.extract(parts.first { it.name.endsWith(".zip") }, File(dir, "zusammen"))
        assertContentEquals(big.readBytes(), whole.first { it.name == "zufall.bin" }.readBytes())
    }

    @Test
    fun tarGzAndSevenZipAreRead() {
        val tgz = File(dir, "a.tar.gz")
        org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(java.util.zip.GZIPOutputStream(tgz.outputStream())).use { t ->
            val data = "Tar-Inhalt".toByteArray()
            t.putArchiveEntry(org.apache.commons.compress.archivers.tar.TarArchiveEntry("ordner/datei.txt").apply { size = data.size.toLong() })
            t.write(data)
            t.closeArchiveEntry()
        }
        assertEquals(listOf("ordner/datei.txt"), Archives.list(tgz).filter { !it.dir }.map { it.path })
        assertEquals("Tar-Inhalt", Archives.extract(tgz, File(dir, "tar")).single().readText())

        val seven = File(dir, "b.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(seven).use { z ->
            val e = z.createArchiveEntry(File(dir, "a.tar.gz"), "innen/kopie.tar.gz")
            z.putArchiveEntry(e)
            z.write(tgz.readBytes())
            z.closeArchiveEntry()
        }
        assertEquals(listOf("innen/kopie.tar.gz"), Archives.list(seven).filter { !it.dir }.map { it.path })
        assertContentEquals(tgz.readBytes(), Archives.extract(seven, File(dir, "sieben")).single().readBytes())
    }

    @Test
    fun zipSlipIsRefused() {
        val evil = File(dir, "boese.zip")
        ZipOutputStream(evil.outputStream()).use { z -> z.putNextEntry(ZipEntry("../../ausserhalb.txt")); z.write(1); z.closeEntry() }
        assertFailsWith<SecurityException> { Archives.extract(evil, File(dir, "ziel")) }
        assertFalse(File(dir.parentFile, "ausserhalb.txt").exists())
    }

    // ── Passwords for Office files ──

    private fun docx(): File {
        val f = File(dir, "brief.docx")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml"))
            z.write("""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""".toByteArray())
            z.putNextEntry(ZipEntry("_rels/.rels"))
            z.write("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray())
            z.putNextEntry(ZipEntry("word/document.xml"))
            z.write("""<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Vertraulicher Brief</w:t></w:r></w:p>${"<w:p><w:r><w:t>Füllzeile</w:t></w:r></w:p>".repeat(400)}</w:body></w:document>""".toByteArray())
        }
        f.writeBytes(out.toByteArray())
        return f
    }

    @Test
    fun officeFilesGetAndLoseAPassword() {
        val plain = docx()
        val locked = FileProtect.outFile(plain, "mit Passwort")
        assertEquals("brief (mit Passwort).docx", locked.name)
        FileProtect.protect(plain, "Geheim-123", locked)
        val bytes = locked.readBytes()
        // For a check with Office/LibreOffice by hand: LORA_CRYPTO_OUT=/some/dir keeps the file.
        System.getenv("LORA_CRYPTO_OUT")?.let { locked.copyTo(File(it, locked.name), overwrite = true) }
        assertTrue(OfficeCrypto.isEncrypted(bytes))
        assertTrue(FileProtect.isProtected(locked))
        assertFalse(FileProtect.isProtected(plain))
        // The container as Office expects it.
        val cfb = Cfb.open(bytes)
        val info = cfb.stream("EncryptionInfo")!!
        assertEquals(listOf<Byte>(4, 0, 4, 0), info.take(4))
        assertTrue("keyBits=\"256\"" in String(info, 8, info.size - 8) && "SHA512" in String(info, 8, info.size - 8))
        assertTrue(cfb.child(cfb.root, "\u0006DataSpaces") != null)
        // Without the right password nothing opens; with it, exactly the original.
        assertFailsWith<OfficeCrypto.WrongPassword> { OfficeCrypto.decrypt(bytes, "geheim-123") }
        assertContentEquals(plain.readBytes(), OfficeCrypto.decrypt(bytes, "Geheim-123"))
        assertTrue("Vertraulicher Brief" in OfficeImport.read("x.docx", FileProtect.openOffice(locked, "Geheim-123"))!!.tabs.first().second)
        // Remove and change.
        val open = FileProtect.outFile(locked, "ohne Passwort")
        assertEquals("brief (ohne Passwort).docx", open.name)
        FileProtect.unprotect(locked, "Geheim-123", open)
        assertContentEquals(plain.readBytes(), open.readBytes())
        val changed = File(dir, "neu.docx")
        FileProtect.change(locked, "Geheim-123", "Anders!", changed)
        assertContentEquals(plain.readBytes(), OfficeCrypto.decrypt(changed.readBytes(), "Anders!"))
        assertFailsWith<OfficeCrypto.WrongPassword> { OfficeCrypto.decrypt(changed.readBytes(), "Geheim-123") }
    }

    @Test
    fun bigOfficeFilesNeedMoreThan109FatSectors() {
        // Over 7 MB the container lists its FAT sectors in DIFAT sectors.
        val data = ByteArray(9 * 1024 * 1024).also { java.util.Random(3).nextBytes(it) }
        val enc = OfficeCrypto.encrypt(data, "x")
        assertContentEquals(data, OfficeCrypto.decrypt(enc, "x"))
    }

    @Test
    fun pdfGetsAndLosesAPassword() {
        val pdf = File(dir, "bericht.pdf")
        org.apache.pdfbox.pdmodel.PDDocument().use { d -> d.addPage(org.apache.pdfbox.pdmodel.PDPage()); d.save(pdf) }
        val locked = File(dir, "bericht (mit Passwort).pdf")
        FileProtect.protect(pdf, "pdf-Passwort", locked)
        assertTrue(FileProtect.isProtected(locked))
        assertFailsWith<Exception> { org.apache.pdfbox.Loader.loadPDF(locked).close() }
        val open = File(dir, "bericht (ohne Passwort).pdf")
        FileProtect.unprotect(locked, "pdf-Passwort", open)
        assertFalse(FileProtect.isProtected(open))
    }
}
