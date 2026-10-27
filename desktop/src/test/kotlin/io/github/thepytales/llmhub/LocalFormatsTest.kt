package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.LocalFolderTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.data.LocalFolder
import io.github.thepytales.llmhub.docs.EpubWriter
import io.github.thepytales.llmhub.docs.LatexWriter
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.OpenDocumentWriter
import io.github.thepytales.llmhub.docs.RtfWriter
import io.github.thepytales.llmhub.docs.XlsxWriter
import io.github.thepytales.llmhub.files.CalendarText
import io.github.thepytales.llmhub.files.EpubText
import io.github.thepytales.llmhub.files.FormatReaders
import io.github.thepytales.llmhub.files.MailText
import io.github.thepytales.llmhub.files.Notebook
import io.github.thepytales.llmhub.files.OdfText
import io.github.thepytales.llmhub.files.OfficeEdit
import io.github.thepytales.llmhub.files.RtfText
import io.github.thepytales.llmhub.files.SyntaxCheck
import io.github.thepytales.llmhub.files.TextCodec
import io.github.thepytales.llmhub.files.TextFormats
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFormatsTest {
    private val base = Files.createTempDirectory("adeu-formate").toFile()
    private val dir = File(base, "Freigabe").apply { mkdirs() }

    private val tools: LocalFolderTools by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", Files.createTempDirectory("adeu-daten").toFile().absolutePath)
        val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        c.localFolders.upsert(LocalFolder(name = "Test", path = dir.absolutePath))
        c.localFolderTools
    }

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    private fun call(name: String, args: String): String = runBlocking {
        tools.execute(name, args, RepoContext(mutableListOf())).text
    }

    private fun json(vararg pairs: Pair<String, Any>): String = JsonObject(pairs.associate { (k, v) ->
        k to when (v) {
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
            is Number -> kotlinx.serialization.json.JsonPrimitive(v)
            else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
        }
    }).toString()

    private fun wellFormed(xml: ByteArray) {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    }

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { out[e.name] = z.readBytes(); e = z.nextEntry }
        }
        return out
    }

    // ------------------------------------------------------------------ encoding and edits

    @Test
    fun encodingAndLineEndingsStay() {
        val cp1252 = Charset.forName("windows-1252")
        val f = File(dir, "alt.txt").apply { writeBytes("Grüße aus Dresden\r\nZeile zwei – Ende\r\n".toByteArray(cp1252)) }
        val read = call("read_local_file", json("path" to "alt.txt"))
        assertTrue("1: Grüße aus Dresden" in read && "Windows-1252" in read && "CRLF" in read, read)
        val r = call("edit_local_file", json("path" to "alt.txt", "old_text" to "Zeile zwei", "new_text" to "Zeile 2 (geändert)"))
        assertTrue(r.startsWith("Geändert") && "beibehalten" in r, r)
        assertContentEquals("Grüße aus Dresden\r\nZeile 2 (geändert) – Ende\r\n".toByteArray(cp1252), f.readBytes())

        // UTF-8 with BOM, LF; overwritten with write_local_file.
        val bom = File(dir, "bom.md").apply { writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "# Titel\n".toByteArray()) }
        call("write_local_file", json("path" to "bom.md", "content" to "# Neu\nÄnderung\n"))
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "# Neu\nÄnderung\n".toByteArray(), bom.readBytes())

        // UTF-16 LE with BOM.
        val u16 = File(dir, "u16.txt").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a\r\nb\r\n".toByteArray(Charsets.UTF_16LE)) }
        call("edit_local_file", json("path" to "u16.txt", "old_text" to "b", "new_text" to "ü"))
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a\r\nü\r\n".toByteArray(Charsets.UTF_16LE), u16.readBytes())

        // Characters Windows-1252 lacks: UTF-8 with BOM instead of losing them.
        val r2 = call("edit_local_file", json("path" to "alt.txt", "old_text" to "Ende", "new_text" to "→ Ende"))
        assertTrue("UTF-8" in r2 && "Achtung" in r2, r2)
        assertEquals("Grüße aus Dresden\nZeile 2 (geändert) – → Ende\n", TextCodec.read(f).text)
        assertTrue(TextCodec.read(f).style.crlf)
    }

    @Test
    fun severalEditsAndLineRanges() {
        File(dir, "code.py").writeText("def a():\n    return 1\n\ndef b():\n    return 2\n")
        val r = call(
            "edit_local_file",
            """{"path": "code.py", "edits": [{"old_text": "return 1", "new_text": "return 10"}, {"old_text": "return 2", "new_text": "return 20"}]}""",
        )
        assertTrue("2 Ersetzungen" in r, r)
        assertEquals("def a():\n    return 10\n\ndef b():\n    return 20\n", File(dir, "code.py").readText())
        // edits also as a JSON string; a failing edit changes nothing.
        val bad = call("edit_local_file", json("path" to "code.py", "edits" to """[{"old_text": "return 10", "new_text": "x"}, {"old_text": "fehlt", "new_text": "y"}]"""))
        assertTrue(bad.startsWith("FEHLER") && "Änderung 2" in bad, bad)
        assertTrue("return 10" in File(dir, "code.py").readText())
        // Line mode: replace lines 4–5, insert before line 1, delete line 3.
        call("edit_local_file", json("path" to "code.py", "start_line" to 4, "end_line" to 5, "new_text" to "def b(x):\n    return x * 2"))
        call("edit_local_file", json("path" to "code.py", "start_line" to 1, "end_line" to 0, "new_text" to "import math"))
        call("edit_local_file", json("path" to "code.py", "start_line" to 4, "new_text" to ""))
        assertEquals("import math\ndef a():\n    return 10\ndef b(x):\n    return x * 2\n", File(dir, "code.py").readText())
        // replace_all
        File(dir, "liste.txt").writeText("a, a, a")
        assertTrue("3 Ersetzungen" in call("edit_local_file", json("path" to "liste.txt", "old_text" to "a", "new_text" to "b", "replace_all" to true)))
        assertEquals("b, b, b", File(dir, "liste.txt").readText())
    }

    @Test
    fun searchWithRegexPatternAndContext() {
        File(dir, "src").mkdirs()
        File(dir, "src/main.py").writeText("import os\n\ndef laden(pfad):\n    return open(pfad)\n\ndef speichern(pfad, text):\n    pass\n")
        File(dir, "notizen.md").writeText("def ist hier nur Text\n")
        File(dir, ".git").mkdirs()
        File(dir, ".git/config").writeText("def versteckt")
        val r = call("search_local_files", json("query" to "^def \\w+\\(", "regex" to true, "file_pattern" to "*.py", "context" to 1))
        assertTrue("== src/main.py ==" in r && "3: def laden(pfad):" in r && "4- " in r && "6: def speichern" in r, r)
        assertFalse("notizen.md" in r || ".git" in r, r)
        val plain = call("search_local_files", json("query" to "DEF", "file_pattern" to "*.{md,txt}"))
        assertTrue("notizen.md:1:" in plain && "main.py" !in plain, plain)
        assertTrue(LocalFolderTools.globToRegex("src/**/*.kt").matches("src/a/b/C.kt"))
        assertTrue(LocalFolderTools.globToRegex("src/**/*.kt").matches("src/C.kt"))
        assertTrue(call("search_local_files", json("query" to "(", "regex" to true)).startsWith("FEHLER"))
    }

    @Test
    fun searchLooksIntoDocuments() {
        File(dir, "Bericht.odt").writeBytes(OpenDocumentWriter.odt("Bericht", "# Bericht\n\nDer Haushaltsplan steht.\n"))
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = org.apache.pdfbox.pdmodel.PDPage()
            doc.addPage(page)
            org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page).use { s ->
                s.beginText()
                s.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12f)
                s.newLineAtOffset(72f, 700f)
                s.showText("Anlage zum Haushaltsplan 2027")
                s.endText()
            }
            doc.save(File(dir, "Anlage.pdf"))
        }
        val r = call("search_local_files", json("query" to "Haushaltsplan"))
        assertTrue("Bericht.odt" in r && "Anlage.pdf:1: Anlage zum Haushaltsplan 2027" in r, r)
        // Second search comes from the cache and finds the same.
        assertEquals(r, call("search_local_files", json("query" to "Haushaltsplan")))
    }

    // ------------------------------------------------------------------ notebooks

    private val notebook = """{
 "cells": [
  {
   "cell_type": "markdown",
   "id": "a1",
   "metadata": {},
   "source": [
    "# Auswertung"
   ]
  },
  {
   "cell_type": "code",
   "execution_count": 3,
   "id": "b2",
   "metadata": {},
   "outputs": [
    {
     "name": "stdout",
     "output_type": "stream",
     "text": [
      "42\n"
     ]
    },
    {
     "data": {
      "image/png": "iVBORw0KGgo=",
      "text/plain": [
       "<Figure>"
      ]
     },
     "metadata": {},
     "output_type": "display_data"
    }
   ],
   "source": [
    "x = 41\n",
    "print(x + 1)"
   ]
  }
 ],
 "metadata": {
  "language_info": {
   "name": "python"
  }
 },
 "nbformat": 4,
 "nbformat_minor": 5
}
"""

    @Test
    fun notebooksAreReadAndEditedCellByCell() {
        val text = Notebook.text(notebook)
        assertTrue("## Zelle 1 · markdown" in text && "## Zelle 2 · code (python) [3]" in text, text)
        assertTrue("print(x + 1)" in text && "    42" in text && "[Bild ausgelassen: image/png]" in text, text)
        // Formatting like Jupyter: unchanged JSON stays byte for byte.
        assertEquals(notebook, Notebook.format(Json.parseToJsonElement(notebook), " ") + "\n")

        File(dir, "analyse.ipynb").writeText(notebook)
        val r = call("edit_notebook_cell", json("path" to "analyse.ipynb", "cell" to 2, "source" to "x = 1\nprint(x)"))
        assertTrue(r.startsWith("Zelle 2 ersetzt"), r)
        val edited = Json.parseToJsonElement(File(dir, "analyse.ipynb").readText()).jsonObject
        val cell = edited["cells"]!!.jsonArray[1].jsonObject
        assertEquals("[\"x = 1\\n\",\"print(x)\"]", cell["source"].toString())
        assertEquals(2, cell["outputs"]!!.jsonArray.size, "Ausgaben bleiben")
        assertEquals("b2", cell["id"].toString().trim('"'))
        call("edit_notebook_cell", json("path" to "analyse.ipynb", "cell" to 3, "action" to "insert_before", "source" to "## Fazit", "cell_type" to "markdown"))
        call("edit_notebook_cell", json("path" to "analyse.ipynb", "cell" to 1, "action" to "delete"))
        val cells = Json.parseToJsonElement(File(dir, "analyse.ipynb").readText()).jsonObject["cells"]!!.jsonArray
        assertEquals(2, cells.size)
        assertEquals("\"markdown\"", cells[1].jsonObject["cell_type"].toString())
        assertNotNull(cells[1].jsonObject["id"])
        assertNull(Notebook.problem(File(dir, "analyse.ipynb").readText()))
        assertTrue("Zelle 2" in call("read_local_file", json("path" to "analyse.ipynb")))
    }

    // ------------------------------------------------------------------ readers

    @Test
    fun rtfBecomesText() {
        val rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0 Arial;}}{\\colortbl;\\red255\\green0\\blue0;}" +
            "{\\*\\generator Test;}{\\info{\\title Geheim}}\\pard Gr\\'fc\\'dfe aus {\\b Dresden}\\par " +
            "Euro: \\u8364? und \\'84Zitat\\'93\\par{\\field{\\*\\fldinst HYPERLINK \"x\"}{\\fldrslt Link}}\\cell Ende}"
        val t = RtfText.text(rtf)
        assertEquals("Grüße aus Dresden\nEuro: € und „Zitat“\nLink | Ende", t)
        assertFalse("Arial" in t || "Geheim" in t || "Test" in t)
        // Round trip through the writer.
        val written = RtfWriter.write("Brief", "## Betreff\n\nSehr **geehrte** Frau Müller,\n\n- Punkt eins\n\n| A | B |\n|---|---|\n| 1 | 2 |\n")
        val back = RtfText.text(written)
        assertTrue("Betreff" in back && "Sehr geehrte Frau Müller," in back && "Punkt eins" in back && "1 | 2" in back, back)
    }

    @Test
    fun openDocumentWithHeadingsListsAndTables() {
        val md = "# Protokoll\n\n## Teilnehmende\n\n- Anna\n- Ben\n  - Gast\n\n1. Erstens\n2. Zweitens\n\n| Name | Punkte |\n|---|---|\n| Anna | 12 |\n\nText mit **fett** und *kursiv*.\n"
        val odt = OpenDocumentWriter.odt("Protokoll", md)
        val entries = zipEntries(odt)
        assertEquals("mimetype", entries.keys.first())
        assertEquals("application/vnd.oasis.opendocument.text", String(entries.getValue("mimetype")))
        ZipFile(File(dir, "p.odt").apply { writeBytes(odt) }).use { assertEquals(ZipEntry.STORED, it.getEntry("mimetype").method) }
        listOf("content.xml", "styles.xml", "meta.xml", "META-INF/manifest.xml").forEach { wellFormed(entries.getValue(it)) }
        val text = OdfText.markdown(odt)
        assertTrue("# Protokoll" in text && "## Teilnehmende" in text, text)
        assertTrue("- Anna" in text && "  - Gast" in text && "1. Erstens" in text && "2. Zweitens" in text, text)
        assertTrue("| Name | Punkte |" in text && "| Anna | 12 |" in text, text)
        assertTrue("**fett**" in text && "*kursiv*" in text, text)

        val ods = OpenDocumentWriter.ods("Noten", "## Klausur\n\n| Name | Punkte |\n|---|---|\n| Anna | 12,5 |\n| Ben | =SUM(B2:B2) |\n")
        val content = zipEntries(ods).getValue("content.xml")
        wellFormed(content)
        assertTrue("office:value=\"12.5\"" in String(content) && "of:=SUM([.B2:.B2])" in String(content), String(content))
        assertTrue("## Klausur" in OdfText.markdown(ods) && "| Anna | 12,5 |" in OdfText.markdown(ods), OdfText.markdown(ods))

        val odp = OpenDocumentWriter.odp("Vortrag", "# Vortrag\n\nUntertitel\n\n---\n\n## Gliederung\n\n- Erstens\n- Zweitens\n\nNotizen: Langsam sprechen\n")
        wellFormed(zipEntries(odp).getValue("content.xml"))
        val slides = OdfText.markdown(odp)
        assertTrue("## Folie 1: Vortrag" in slides && "## Folie 2: Gliederung" in slides && "- Erstens" in slides && "Notizen: Langsam sprechen" in slides, slides)
    }

    @Test
    fun emlWithQuotedPrintableAndAttachment() {
        val eml = """
            From: =?UTF-8?Q?J=C3=BCrgen_M=C3=BCller?= <juergen@tu-dresden.de>
            To: anna@tu-dresden.de
            Subject: =?UTF-8?B?UHLDvGZ1bmdzdGVybWlu?=
            Date: Tue, 3 Nov 2026 14:05:00 +0100
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary="XYZ"

            --XYZ
            Content-Type: multipart/alternative; boundary="ALT"

            --ALT
            Content-Type: text/plain; charset=utf-8
            Content-Transfer-Encoding: quoted-printable

            Liebe Anna,=0A die Pr=C3=BCfung ist am Freitag. Sch=
            =C3=B6ne Gr=C3=BC=C3=9Fe
            --ALT
            Content-Type: text/html; charset=utf-8

            <p>HTML-Version</p>
            --ALT--
            --XYZ
            Content-Type: application/pdf; name="Plan.pdf"
            Content-Disposition: attachment; filename="Plan.pdf"
            Content-Transfer-Encoding: base64

            JVBERi0xLjQK
            --XYZ--
        """.trimIndent().replace("\n", "\r\n")
        val t = MailText.eml(eml.toByteArray(Charsets.ISO_8859_1))
        assertTrue("Von: Jürgen Müller <juergen@tu-dresden.de>" in t, t)
        assertTrue("Betreff: Prüfungstermin" in t && "Datum: Di., 03.11.2026 14:05" in t.replace("Di, ", "Di., "), t)
        assertTrue("Anhänge: Plan.pdf (9 B)" in t && "die Prüfung ist am Freitag. Schöne Grüße" in t && "HTML-Version" !in t, t)
    }

    /** Minimal OLE2 compound file (512-byte sectors, no mini stream) for tests. */
    private fun cfb(streams: List<Triple<String, Int, ByteArray>>): ByteArray {
        // Entries: root, then the given ones (type 1 = storage, 2 = stream). Storage children follow a "/" in the name.
        data class E(val name: String, val type: Int, val data: ByteArray, val parent: Int)
        val entries = mutableListOf(E("Root Entry", 5, ByteArray(0), -1))
        val index = HashMap<String, Int>()
        for ((path, type, data) in streams) {
            val parent = if ('/' in path) index.getValue(path.substringBeforeLast('/')) else 0
            index[path] = entries.size
            entries += E(path.substringAfterLast('/'), type, data, parent)
        }
        val dirSectors = (entries.size + 3) / 4
        val dataSectors = entries.map { (it.data.size + 511) / 512 }
        val total = 1 + dirSectors + dataSectors.sum()
        val fat = IntArray(128) { -1 }
        fat[0] = -3
        for (i in 0 until dirSectors) fat[1 + i] = if (i == dirSectors - 1) -2 else 2 + i
        var next = 1 + dirSectors
        val starts = IntArray(entries.size) { -2 }
        entries.forEachIndexed { i, e ->
            val n = dataSectors[i]
            if (n == 0) return@forEachIndexed
            starts[i] = next
            for (k in 0 until n) fat[next + k] = if (k == n - 1) -2 else next + k + 1
            next += n
        }
        require(total <= 128)
        val out = ByteBuffer.allocate(512 * (total + 1)).order(ByteOrder.LITTLE_ENDIAN)
        out.put(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))
        out.putShort(24, 0x3E); out.putShort(26, 3); out.putShort(28, 0xFFFE.toShort()); out.putShort(30, 9); out.putShort(32, 6)
        out.putInt(44, 1); out.putInt(48, 1); out.putInt(56, 0); out.putInt(60, -2); out.putInt(64, 0); out.putInt(68, -2); out.putInt(72, 0)
        for (i in 0 until 109) out.putInt(76 + i * 4, if (i == 0) 0 else -1)
        fun sector(s: Int) = 512 * (s + 1)
        fat.forEachIndexed { i, v -> out.putInt(sector(0) + i * 4, v) }
        // Children of each storage as a chain of right siblings.
        val children = entries.indices.groupBy { entries[it].parent }
        entries.forEachIndexed { i, e ->
            val off = sector(1) + i * 128
            val name = e.name.toByteArray(Charsets.UTF_16LE)
            out.position(off); out.put(name)
            out.putShort(off + 64, (name.size + 2).toShort())
            out.put(off + 66, e.type.toByte())
            val siblings = children[e.parent].orEmpty()
            val pos = siblings.indexOf(i)
            out.putInt(off + 68, -1)
            out.putInt(off + 72, siblings.getOrNull(pos + 1) ?: -1)
            out.putInt(off + 76, children[i]?.firstOrNull() ?: -1)
            out.putInt(off + 116, starts[i])
            out.putInt(off + 120, e.data.size)
        }
        for (k in entries.size until dirSectors * 4) { val off = sector(1) + k * 128; out.putInt(off + 68, -1); out.putInt(off + 72, -1); out.putInt(off + 76, -1) }
        entries.forEachIndexed { i, e -> if (starts[i] >= 0) { out.position(sector(starts[i])); out.put(e.data) } }
        return out.array()
    }

    private fun u16(s: String) = s.toByteArray(Charsets.UTF_16LE)

    @Test
    fun outlookMsg() {
        val msg = cfb(
            listOf(
                Triple("__substg1.0_0037001F", 2, u16("Sitzung am Montag")),
                Triple("__substg1.0_0C1A001F", 2, u16("Prof. Weber")),
                Triple("__substg1.0_0E04001F", 2, u16("Anna Schmidt")),
                Triple("__substg1.0_1000001F", 2, u16("Hallo Anna,\r\nbitte die Folien mitbringen.")),
                Triple("__attach_version1.0_#00000000", 1, ByteArray(0)),
                Triple("__attach_version1.0_#00000000/__substg1.0_3707001F", 2, u16("Folien.pptx")),
            ),
        )
        val t = FormatReaders.text("Mail.msg", msg)!!
        assertTrue("Von: Prof. Weber" in t && "An: Anna Schmidt" in t && "Betreff: Sitzung am Montag" in t, t)
        assertTrue("Anhänge: Folien.pptx" in t && "bitte die Folien mitbringen." in t, t)
    }

    @Test
    fun oldWordFileFallsBackToTextRuns() {
        val doc = cfb(listOf(Triple("WordDocument", 2, ByteArray(40) + u16("Antrag auf Verlängerung der Abgabefrist") + ByteArray(20) { 7 })))
        val t = FormatReaders.text("Antrag.doc", doc)!!
        assertTrue("heuristisch" in t && "Antrag auf Verlängerung der Abgabefrist" in t, t)
    }

    @Test
    fun calendarAndContacts() {
        val ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nX-WR-CALNAME:Lehre\r\nBEGIN:VEVENT\r\nSUMMARY:Sprechstunde\\, Raum 2\r\n" +
            "DTSTART:20261103T130000Z\r\nDTEND:20261103T143000Z\r\nLOCATION:WEB 1\r\n 23\r\nDESCRIPTION:Bitte\\nanmelden\r\n" +
            "BEGIN:VALARM\r\nTRIGGER:-PT15M\r\nDESCRIPTION:Erinnerung\r\nEND:VALARM\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nSUMMARY:Tag der Lehre\r\nDTSTART;VALUE=DATE:20261101\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val t = CalendarText.ics(ics)
        assertTrue("Kalender „Lehre“: 2 Einträge" in t, t)
        assertTrue(t.indexOf("Tag der Lehre") < t.indexOf("Sprechstunde"), "nach Datum sortiert")
        assertTrue("## Sprechstunde, Raum 2" in t && "Beginn: Di., 03.11.2026 14:00" in t.replace("Di, ", "Di., ") && "Ort: WEB 123" in t, t)
        assertTrue("Beschreibung: Bitte\nanmelden" in t && "Erinnerung" !in t && "(ganztägig)" in t, t)
        val vcf = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Dr. Anna Schmidt\r\nN:Schmidt;Anna;;Dr.;\r\nORG:TU Dresden;Institut für Geographie\r\n" +
            "EMAIL;TYPE=work:anna.schmidt@tu-dresden.de\r\nTEL;TYPE=cell:+49 151 123\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:M=C3=BCller;J=C3=BCrgen\r\nTEL;WORK;VOICE:0351 4630\r\nEND:VCARD\r\n"
        val c = CalendarText.vcf(vcf)
        assertTrue("2 Kontakte" in c && "## Dr. Anna Schmidt" in c && "Organisation: TU Dresden, Institut für Geographie" in c, c)
        assertTrue("E-Mail: anna.schmidt@tu-dresden.de (dienstlich)" in c && "Telefon: +49 151 123 (mobil)" in c, c)
        assertTrue("## Jürgen Müller" in c && "Telefon: 0351 4630 (dienstlich)" in c, c)
    }

    @Test
    fun epubRoundTrip() {
        val book = EpubWriter.write("Skript", "# Skript\n\n## Einführung\n\nErster Text.\n\n## Methoden\n\n- Interviews\n- Umfragen\n")
        val entries = zipEntries(book)
        assertEquals("mimetype", entries.keys.first())
        entries.filterKeys { it.endsWith(".xhtml") || it.endsWith(".opf") || it.endsWith(".xml") }.values.forEach { bytes ->
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                .newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        }
        val text = EpubText.text(book)
        assertTrue(text.startsWith("# Skript"), text)
        assertTrue(text.indexOf("Einführung") < text.indexOf("Methoden") && "Erster Text." in text && "- Interviews" in text, text)
        assertTrue("kapitel2.xhtml" in String(entries.getValue("OEBPS/content.opf")))
    }

    @Test
    fun zipEntriesAreListedAndRead() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("daten/")); z.closeEntry()
            z.putNextEntry(ZipEntry("daten/liste.txt")); z.write("Erste Zeile\nZweite Zeile".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("daten/brief.rtf")); z.write("{\\rtf1 Hallo {\\b Welt}\\par}".toByteArray()); z.closeEntry()
        }
        File(dir, "Archiv.zip").writeBytes(bos.toByteArray())
        val list = call("list_local_files", json("path" to "Archiv.zip"))
        assertTrue("daten/liste.txt" in list && "2 Dateien" in list, list)
        val r = call("read_local_file", json("path" to "Archiv.zip/daten/liste.txt", "start_line" to 2))
        assertTrue("2: Zweite Zeile" in r && "1: Erste" !in r, r)
        assertTrue("1: Hallo Welt" in call("read_local_file", json("path" to "Archiv.zip/daten/brief.rtf")))
        assertTrue("daten/liste.txt" in call("read_local_file", json("path" to "Archiv.zip/daten")))
        assertTrue(call("read_local_file", json("path" to "Archiv.zip/fehlt.txt")).startsWith("FEHLER"))
    }

    // ------------------------------------------------------------------ writers and checks

    @Test
    fun writeProducesValidDocuments() {
        val md = "# Bericht\n\n## Ergebnisse\n\nText mit **fett**.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n"
        for (name in listOf("b.odt", "b.ods", "b.odp", "b.epub")) {
            val r = call("write_local_file", json("path" to name, "content" to md))
            assertTrue(r.startsWith("Angelegt"), r)
            val entries = zipEntries(File(dir, name).readBytes())
            assertEquals("mimetype", entries.keys.first(), name)
            entries.filterKeys { it.endsWith(".xml") }.values.forEach(::wellFormed)
        }
        call("write_local_file", json("path" to "b.rtf", "content" to md))
        assertTrue(File(dir, "b.rtf").readText().startsWith("{\\rtf1") && "Ergebnisse" in RtfText.text(File(dir, "b.rtf").readBytes()))
        call("write_local_file", json("path" to "b.tex", "content" to md))
        val tex = File(dir, "b.tex").readText()
        assertTrue("\\section{Ergebnisse}" in tex && "\\textbf{fett}" in tex && "\\begin{tabularx}" in tex && "\\end{document}" in tex, tex)
        // Ready-made LaTeX stays as it is.
        call("write_local_file", json("path" to "c.tex", "content" to "\\documentclass{article}\n\\begin{document}x\\end{document}\n"))
        assertTrue(File(dir, "c.tex").readText().startsWith("\\documentclass{article}"))
        call("write_local_file", json("path" to "t.csv", "content" to md))
        val csv = File(dir, "t.csv").readBytes()
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), csv.copyOf(3))
        assertEquals("A;B\r\n1;2", String(csv, 3, csv.size - 3))
        assertTrue("\\section{Ergebnisse}" in LatexWriter.write("x", md))
    }

    @Test
    fun syntaxProblemsAreReported() {
        val r = call("write_local_file", json("path" to "conf.json", "content" to "{\"a\": 1,}"))
        assertTrue(r.startsWith("Angelegt") && "Syntaxprüfung" in r && "JSON" in r, r)
        assertTrue(File(dir, "conf.json").isFile, "trotzdem gespeichert")
        assertTrue("Syntaxprüfung" !in call("write_local_file", json("path" to "ok.json", "content" to "{\"a\": [1, 2]}")))
        assertTrue("Zeile 1" in SyntaxCheck.xml("<a><b></a>")!!)
        assertNull(SyntaxCheck.xml("<a><b/></a>"))
        assertNotNull(SyntaxCheck.yaml("a:\n\tb: 1\n"))
        assertNotNull(SyntaxCheck.yaml("name: x\n  wert: 2\n"))
        assertNull(SyntaxCheck.yaml("a:\n  b: 1\n  c:\n    - x\n    - y: 2\n      z: 3\ntext: |\n  frei\n    eingerückt\n"))
        assertTrue("Zeile 3" in SyntaxCheck.csv("a;b\n1;2\n3\n", null)!!)
        assertNull(SyntaxCheck.csv("a;b\n\"1;x\";2\n", null))
    }

    // ------------------------------------------------------------------ Office in place

    private fun docx(documentXml: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write("<Types/>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("word/document.xml")); z.write(documentXml.toByteArray()); z.closeEntry()
        }
        return bos.toByteArray()
    }

    @Test
    fun docxEditKeepsRunsAndFormatting() {
        val w = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $w><w:body>" +
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Sehr geehrte Frau </w:t></w:r>" +
            "<w:r><w:rPr><w:i/></w:rPr><w:t>Mül</w:t></w:r><w:r><w:t>ler, &amp; Co.</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Zweiter Absatz</w:t></w:r></w:p></w:body></w:document>"
        File(dir, "Brief.docx").writeBytes(docx(xml))
        val r = call("edit_office_file", json("path" to "Brief.docx", "old_text" to "Frau Müller, &", "new_text" to "Herr Schulz und"))
        assertTrue(r.startsWith("Geändert") && "1 Ersetzung" in r, r)
        val out = String(zipEntries(File(dir, "Brief.docx").readBytes()).getValue("word/document.xml"))
        wellFormed(out.toByteArray())
        assertTrue("<w:r><w:rPr><w:b/></w:rPr><w:t>Sehr geehrte Herr Schulz und</w:t></w:r>" in out, out)
        assertTrue("<w:r><w:rPr><w:i/></w:rPr><w:t></w:t></w:r>" in out && "<w:t xml:space=\"preserve\"> Co.</w:t>" in out, out)
        assertTrue("<w:pStyle w:val=\"Heading1\"/>" in out && "standalone=\"yes\"" in out)
        assertTrue("Sehr geehrte Herr Schulz und Co." in OfficeImport.docx(File(dir, "Brief.docx").readBytes()).replace("*", ""))
        // Not found / ambiguous: nothing is written.
        assertTrue(call("edit_office_file", json("path" to "Brief.docx", "old_text" to "Absatz Zweiter", "new_text" to "x")).startsWith("FEHLER"))
        assertFailsWith<IllegalArgumentException> { OfficeEdit.replace("docx", docx(xml.replace("Zweiter Absatz", "Co. Co.")), "Co.", "GmbH") }
        val all = OfficeEdit.replace("docx", docx(xml.replace("Zweiter Absatz", "Co. Co.")), "Co.", "GmbH", all = true)
        assertEquals(3, all.count)
        // Line breaks become w:br.
        val br = OfficeEdit.replace("docx", docx(xml), "Zweiter Absatz", "Zeile 1\nZeile 2")
        assertTrue("<w:t xml:space=\"preserve\">Zeile 1</w:t><w:br/><w:t xml:space=\"preserve\">Zeile 2</w:t>" in String(zipEntries(br.bytes).getValue("word/document.xml")))
    }

    @Test
    fun pptxEditOnOneSlide() {
        val a = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
        fun slide(t: String) = "<p:sld $a><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:rPr lang=\"de-DE\" b=\"1\"/><a:t>$t</a:t></a:r><a:r><a:rPr lang=\"de-DE\"/><a:t> 2025</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("ppt/slides/slide1.xml")); z.write(slide("Bericht").toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("ppt/slides/slide2.xml")); z.write(slide("Bericht").toByteArray()); z.closeEntry()
        }
        val r = OfficeEdit.replace("pptx", bos.toByteArray(), "Bericht 2025", "Bericht 2026", slide = 2)
        assertEquals(1, r.count)
        val e = zipEntries(r.bytes)
        assertTrue("<a:t>Bericht 2026</a:t></a:r><a:r><a:rPr lang=\"de-DE\"/><a:t></a:t>" in String(e.getValue("ppt/slides/slide2.xml")))
        assertTrue("<a:t> 2025</a:t>" in String(e.getValue("ppt/slides/slide1.xml")))
    }

    @Test
    fun excelCellsAreSetInPlace() {
        val file = File(dir, "Noten.xlsx")
        val md = "## Klausur\n\n| Name | Punkte |\n|---|---|\n| Anna | 12 |\n| Ben | 9 |\n\n## Zweites Blatt\n\n| X |\n|---|\n| y |\n"
        XlsxWriter.write(file, "Noten", md)
        val reference = File(dir, "ref.xlsx").also { XlsxWriter.write(it, "Noten", md) }
        val r = call("set_excel_cells", """{"path": "Noten.xlsx", "sheet": "Klausur", "cells": {"B3": "10,5", "B4": "=SUMME(B2:B3)", "C1": "Bemerkung", "A6": "Summe", "D2": "'0351", "B2": ""}}""")
        assertTrue(r.startsWith("In „Klausur“") && "6 Zellen" in r, r)
        val e = zipEntries(file.readBytes())
        val sheet = String(e.getValue("xl/worksheets/sheet1.xml"))
        wellFormed(sheet.toByteArray())
        wellFormed(e.getValue("xl/workbook.xml"))
        assertTrue("<c r=\"B3\"><v>10.5</v></c>" in sheet && "<c r=\"B4\"><f>SUM(B2:B3)</f></c>" in sheet, sheet)
        assertTrue("<c r=\"C1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">Bemerkung</t></is></c>" in sheet, sheet)
        assertTrue("<c r=\"B2\"/>" in sheet && "<t xml:space=\"preserve\">0351</t>" in sheet, sheet)
        // Rows and cells in order, head row keeps its style.
        assertTrue(sheet.indexOf("r=\"4\"") < sheet.indexOf("<row r=\"6\">") && sheet.indexOf("r=\"A1\"") < sheet.indexOf("r=\"C1\""), sheet)
        assertTrue("<c r=\"A1\" t=\"inlineStr\" s=\"1\">" in sheet || "<c r=\"A1\" s=\"1\"" in sheet, sheet)
        assertTrue("fullCalcOnLoad=\"1\"" in String(e.getValue("xl/workbook.xml")))
        assertContentEquals(zipEntries(reference.readBytes()).getValue("xl/worksheets/sheet2.xml"), e.getValue("xl/worksheets/sheet2.xml"), "anderes Blatt unverändert")
        val tabs = OfficeImport.xlsx(file.readBytes())
        assertTrue("=SUM(B2:B3)" in tabs.first().second && "10,5" in tabs.first().second, tabs.first().second)
        assertEquals("ROUND(A1,2)", OfficeEdit.excelFormula("=ROUND(A1,2)"))
        assertEquals("ROUND(A1,2)", OfficeEdit.excelFormula("=RUNDEN(A1;2)"))
        assertTrue(call("set_excel_cells", json("path" to "Noten.xlsx", "sheet" to "Fehlt", "cells" to "{\"A1\": \"x\"}")).startsWith("FEHLER"))
    }

    @Test
    fun moreTextFormatsAndHints() {
        assertTrue(TextFormats.isText("Dockerfile") && TextFormats.isText("Makefile") && TextFormats.isText(".editorconfig"))
        assertTrue(TextFormats.isText("skript.ps1") && TextFormats.isText("modell.jl") && TextFormats.isText("x.tfvars"))
        assertEquals("PowerShell", TextFormats.language("skript.ps1"))
        File(dir, "Dockerfile").writeText("FROM python:3.12\n")
        assertTrue("Dockerfile" in call("read_local_file", json("path" to "Dockerfile")).lines().first())
        File(dir, "aufnahme.mp3").writeBytes(ByteArray(100))
        assertTrue("transcribe_local_file" in call("read_local_file", json("path" to "aufnahme.mp3")))
        File(dir, "logo.svg").writeText("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>")
        assertTrue("1: <svg" in call("read_local_file", json("path" to "logo.svg")))
        assertTrue(call("edit_office_file", json("path" to "logo.svg", "old_text" to "a", "new_text" to "b")).startsWith("FEHLER"))
    }
}
