package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.FormatConvert
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.XlsxWriter
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Formatwandler mit Verlustanzeige (AP-90): test files made with the app's writers, with comments, tracked changes,
 * animations, transitions, macros, formulas and two sheets put in; every offered direction converts and the loss card
 * names exactly what is there.
 */
class FormatConvertTest {
    private val base = Files.createTempDirectory("adeu-umwandeln").toFile()
    private val context = Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })
    private val dir = File(base, "ordner").apply { mkdirs() }

    /** [file] with entries added or changed. */
    private fun patch(file: File, add: Map<String, String> = emptyMap(), change: (String, String) -> String = { _, t -> t }) {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            ZipInputStream(file.inputStream()).use { input ->
                while (true) {
                    val e = input.nextEntry ?: break
                    val bytes = input.readBytes()
                    z.putNextEntry(ZipEntry(e.name))
                    z.write(if (e.name.endsWith(".xml")) change(e.name, bytes.toString(Charsets.UTF_8)).toByteArray() else bytes)
                    z.closeEntry()
                }
            }
            add.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
        }
        file.writeBytes(out.toByteArray())
    }

    private fun docx(): File = runBlocking {
        val f = DocumentWriter.create(context, dir, "Protokoll", "# Protokoll\n\nDie Sitzung beginnt um 14 Uhr.\n\n## Beschlüsse\n\n- Haushalt angenommen\n", DocFormat.DOCX).file
        patch(f, add = mapOf("word/comments.xml" to "<w:comments xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>")) { name, t ->
            if (name == "word/document.xml") t.replaceFirst("<w:body>", "<w:body><w:p><w:ins w:id=\"1\" w:author=\"X\"><w:r><w:t>Neu</w:t></w:r></w:ins></w:p>") else t
        }
        f
    }

    private fun xlsx(): File {
        val f = File(dir, "Haushalt.xlsx")
        XlsxWriter.writeTabs(f, listOf("Daten" to "| Posten | Betrag | Doppelt |\n| --- | --- | --- |\n| Bücher | 120 | =B2*2 |\n| Reisen | 80 | =B3*2 |", "Notizen" to "| Text |\n| --- |\n| zweites Blatt |"))
        return f
    }

    private fun pptx(): File = runBlocking {
        val f = DocumentWriter.create(context, dir, "Vortrag", "# Vortrag\n\n---\n\n## Erste Folie\n\n- Punkt eins\n\n---\n\n## Zweite Folie\n\n- Punkt zwei\n", DocFormat.PPTX).file
        patch(f, add = mapOf("ppt/vbaProject.bin" to "x")) { name, t ->
            if (Regex("ppt/slides/slide\\d+\\.xml").matches(name)) t.replace("</p:sld>", "<p:transition/><p:timing><p:tnLst><p:par/></p:tnLst></p:timing></p:sld>") else t
        }
        f
    }

    @Test
    fun theLossCardNamesWhatIsThere() {
        val x = xlsx()
        val csv = FormatConvert.analyze(x.name, x.readBytes(), "csv")
        assertTrue(csv.losses.any { it.title == "Formeln" && "2 Formeln" in it.detail }, csv.toString())
        assertTrue(csv.losses.any { it.title == "Tabellenblätter" && "2 Blättern" in it.detail }, csv.toString())
        assertEquals("ods", csv.better)
        // ODS keeps formulas and sheets.
        val ods = FormatConvert.analyze(x.name, x.readBytes(), "ods")
        assertTrue(ods.losses.none { it.title == "Formeln" || it.title == "Tabellenblätter" }, ods.toString())

        val d = docx()
        val odt = FormatConvert.analyze(d.name, d.readBytes(), "odt")
        assertTrue(odt.losses.any { it.title == "Kommentare" } && odt.losses.any { it.title == "Änderungsspuren" }, odt.toString())
        assertFalse(odt.losses.any { it.title == "Makros" })
        assertEquals("md", FormatConvert.analyze(d.name, d.readBytes(), "txt").better)

        val p = pptx()
        val pdf = FormatConvert.analyze(p.name, p.readBytes(), "pdf")
        assertTrue(pdf.losses.any { it.title == "Animationen" && Regex("auf [2-9] Folien").containsMatchIn(it.detail) }, pdf.toString())
        assertTrue(pdf.losses.any { it.title == "Übergänge" } && pdf.losses.any { it.title == "Makros" }, pdf.toString())

        // A plain Markdown file into Word loses nothing.
        val md = File(dir, "Notiz.md").apply { writeText("# Notiz\n\nText.") }
        assertTrue(FormatConvert.analyze(md.name, md.readBytes(), "docx").losses.isEmpty())
    }

    @Test
    fun oneSlideKeepsItsText() = runBlocking {
        val f = DocumentWriter.create(context, dir, "Eine", "# Nur eine Folie\n\n- Erster Punkt\n- Zweiter Punkt\n", DocFormat.PPTX).file
        val md = OfficeImport.read(f.name, f.readBytes())!!.tabs.first().second
        val out = FormatConvert.convert(context, f, f.name, "odp", dir)
        val odp = ZipFile(out).use { z -> z.getInputStream(z.getEntry("content.xml")).readBytes().toString(Charsets.UTF_8) }
        assertTrue("Erster Punkt" in odp && "Zweiter Punkt" in odp, "Markdown des Lesers:\n$md\n\nODP: ${odp.takeLast(600)}")
    }

    @Test
    fun everyOfferedDirectionConverts() = runBlocking {
        val sources = listOf(docx(), xlsx(), pptx(), File(dir, "Notiz.md").apply { writeText("# Notiz\n\nEin **wichtiger** Satz.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |") },
            File(dir, "Liste.csv").apply { writeText("Name;Punkte\nAnna;12\nBert;9\n") })
        for (src in sources) {
            val original = src.readBytes()
            for (t in FormatConvert.targets(src.name)) {
                val out = FormatConvert.convert(context, src, src.name, t, dir)
                assertTrue(out.isFile && out.length() > 20, "${src.name} → $t")
                assertTrue(out.name.endsWith(".$t") && out.parentFile == dir, out.path)
                assertContentEquals(original, src.readBytes(), "Original bleibt: ${src.name}")
            }
        }
        // Contents: CSV with the results of the formulas, ODS with both sheets and the formula, ODT/Markdown with the text.
        val csv = File(dir, "Haushalt.csv").readText()
        assertTrue(csv.startsWith("﻿") && "Bücher;120;240" in csv, csv)
        val ods = ZipFile(File(dir, "Haushalt.ods")).use { z -> z.getInputStream(z.getEntry("content.xml")).readBytes().toString(Charsets.UTF_8) }
        assertTrue("table:name=\"Daten\"" in ods && "table:name=\"Notizen\"" in ods && "table:formula" in ods, ods.take(400))
        val odt = ZipFile(File(dir, "Protokoll.odt")).use { z -> z.getInputStream(z.getEntry("content.xml")).readBytes().toString(Charsets.UTF_8) }
        assertTrue("Die Sitzung beginnt um 14 Uhr." in odt)
        assertTrue("Haushalt angenommen" in File(dir, "Protokoll.md").readText())
        val odp = ZipFile(File(dir, "Vortrag.odp")).use { z -> z.getInputStream(z.getEntry("content.xml")).readBytes().toString(Charsets.UTF_8) }
        assertTrue("Erste Folie" in odp && "Punkt zwei" in odp)
        val fromCsv = OfficeImport.read("Liste.xlsx", File(dir, "Liste.xlsx").readBytes())
        assertNotNull(fromCsv)
        assertTrue("Anna" in fromCsv.tabs.first().second)
        // A second conversion does not overwrite the first.
        val again = FormatConvert.convert(context, File(dir, "Notiz.md"), "Notiz.md", "docx", dir)
        assertEquals("Notiz (2).docx", again.name)
        assertNull(FormatConvert.targets("bild.xyz").firstOrNull())
    }
}
