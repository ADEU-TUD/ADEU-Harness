package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.text.DocxWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Word-Treue (AP-45): reading formatting from .docx and writing changes back without losing what the editor does not know. */
class WordFidelityTest {
    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> var e = z.nextEntry; while (e != null) { out[e.name] = z.readBytes(); e = z.nextEntry } }
        return out
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> files.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        return bos.toByteArray()
    }

    /** A Word file with things the editor does not know: a content control, a bookmark and a custom part. */
    private fun original(): ByteArray {
        val f = File.createTempFile("wt", ".docx")
        MarkdownDocx.write(f, "Bericht", "# Bericht\n\nErster Absatz bleibt.\n\n## Teil A\n\nZweiter Absatz wird geändert.\n\nDritter Absatz wird gelöscht.\n\n- Punkt eins\n- Punkt zwei\n")
        val files = unzip(f.readBytes())
        f.delete()
        var doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        // A content control and a bookmark before „Teil A“, which the editor never shows.
        val unknown = "<w:sdt><w:sdtPr><w:alias w:val=\"Kennung\"/></w:sdtPr><w:sdtContent><w:p><w:r><w:t>KENNUNG-42</w:t></w:r></w:p></w:sdtContent></w:sdt>" +
            "<w:p><w:bookmarkStart w:id=\"7\" w:name=\"Marke\"/><w:bookmarkEnd w:id=\"7\"/></w:p>"
        val at = doc.indexOf("<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/>")
        assertTrue(at > 0, "Heading2 im erzeugten Dokument")
        doc = doc.substring(0, at) + unknown + doc.substring(at)
        files["word/document.xml"] = doc.toByteArray()
        files["customXml/item1.xml"] = "<daten>bleibt</daten>".toByteArray()
        return zip(files)
    }

    @Test
    fun changesAreMergedAndUnknownPartsStay() {
        val bytes = original()
        val base = OfficeImport.docx(bytes)
        assertTrue("Zweiter Absatz wird geändert." in base, base)
        val now = base.replace("Zweiter Absatz wird geändert.", "Zweiter Absatz, jetzt **neu** und <u>unterstrichen</u>.")
            .replace("Dritter Absatz wird gelöscht.\n\n", "")
            .replace("- Punkt zwei\n", "- Punkt zwei\n\nGanz neuer Schlussabsatz.\n")
        val merged = assertNotNull(DocxWriter.merge(bytes, base, now))
        val files = unzip(merged)
        val doc = files["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue("KENNUNG-42" in doc && "w:name=\"Marke\"" in doc, "Unbekanntes bleibt")
        assertTrue("Erster Absatz bleibt." in doc)
        assertTrue("jetzt " in doc && "<w:u w:val=\"single\"/>" in doc, "Neuer Text mit Formatierung")
        assertFalse("Dritter Absatz" in doc, "Gelöschtes ist weg")
        assertTrue("Ganz neuer Schlussabsatz." in doc)
        assertTrue(doc.indexOf("Erster Absatz") < doc.indexOf("KENNUNG-42") && doc.indexOf("KENNUNG-42") < doc.indexOf("Teil A"), "Reihenfolge bleibt")
        // Other parts unchanged.
        val before = unzip(bytes)
        for (name in listOf("word/styles.xml", "customXml/item1.xml", "word/_rels/document.xml.rels")) {
            assertContentEquals(before[name], files[name], name)
        }
        // And it reads back as the editor's text.
        val again = OfficeImport.docx(merged)
        assertTrue("Ganz neuer Schlussabsatz." in again && "<u>unterstrichen</u>" in again, again)
    }

    @Test
    fun formattingIsReadFromWord() {
        val f = File.createTempFile("wt", ".docx")
        MarkdownDocx.write(f, "T", "# T\n\n<!--a:c-->Mitte mit <span style=\"color:#C00000\">rot</span>, <mark>markiert</mark> und x<sup>2</sup>.\n")
        val md = OfficeImport.docx(f.readBytes())
        f.delete()
        assertTrue(md.contains("<!--a:c-->"), md)
        assertTrue(md.contains("color:#C00000"), md)
        assertTrue(md.contains("<sup>2</sup>"), md)
        assertTrue(Regex("background:#[0-9A-F]{6}").containsMatchIn(md), md)
    }

    @Test
    fun unsafeMergesFallBack() {
        val bytes = original()
        val base = OfficeImport.docx(bytes)
        // A new picture needs parts of its own: written anew by the caller.
        assertEquals(null, DocxWriter.merge(bytes, base, base + "\n![Bild](data:image/png;base64,iVBORw0KGgo=)\n"))
    }
}
