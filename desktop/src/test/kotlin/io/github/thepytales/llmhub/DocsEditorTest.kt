package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocsLive
import io.github.thepytales.llmhub.docs.DocsStore
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.TextDiff
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsEditorTest {
    private val md = """
        # Seminarplan

        Einleitender Absatz
        über zwei Zeilen.

        - Punkt eins
        - Punkt zwei

        | Sitzung | Thema |
        | --- | --- |
        | 1 | Einführung |

        > [!NOTE]
        > Hinweis

        ```
        code
        ```
    """.trimIndent()

    @Test
    fun blocksSplitAndJoinWithoutLosingText() {
        val blocks = DocBlocks.split(md)
        assertEquals(6, blocks.size, blocks.joinToString("\n---\n"))
        assertEquals(DocBlocks.Kind.H1, DocBlocks.kindOf(blocks[0]))
        assertEquals(DocBlocks.Kind.BULLETS, DocBlocks.kindOf(blocks[2]))
        assertEquals(DocBlocks.Kind.TABLE, DocBlocks.kindOf(blocks[3]))
        assertEquals(DocBlocks.Kind.NOTE, DocBlocks.kindOf(blocks[4]))
        assertEquals(DocBlocks.split(DocBlocks.join(blocks)), blocks)
        assertEquals("## Einleitender Absatz über zwei Zeilen.", DocBlocks.convert(blocks[1], DocBlocks.Kind.H2))
        assertEquals("1. Punkt eins\n2. Punkt zwei", DocBlocks.convert(blocks[2], DocBlocks.Kind.NUMBERS))
        val rows = DocBlocks.tableRows(blocks[3])
        assertEquals(listOf(listOf("Sitzung", "Thema"), listOf("1", "Einführung")), rows)
        assertEquals(rows, DocBlocks.tableRows(DocBlocks.table(rows)))
    }

    @Test
    fun wordDiffAndChangedBlocks() {
        val pieces = TextDiff.words("Die Sitzung beginnt um 10 Uhr.", "Die Sitzung beginnt pünktlich um 11 Uhr.")
        assertEquals("+2 / −1 Wörter", TextDiff.summary(pieces))
        assertEquals("Die Sitzung beginnt um 10 Uhr.", pieces.filter { it.op != TextDiff.Op.ADDED }.joinToString("") { it.text })
        assertEquals("Die Sitzung beginnt pünktlich um 11 Uhr.", pieces.filter { it.op != TextDiff.Op.REMOVED }.joinToString("") { it.text })
        assertEquals(setOf(1), TextDiff.changedBlocks(md, md.replace("Einleitender", "Kurzer")))
    }

    @Test
    fun versionsAreAttributedAndTypingIsMerged() {
        var doc = LiveDoc.create("Plan", md, "KI · DeepSeek")
        doc = doc.change("Sie") { it.copy(tabs = listOf(it.tabs[0].copy(markdown = md + "\nA"))) }
        doc = doc.change("Sie") { it.copy(tabs = listOf(it.tabs[0].copy(markdown = md + "\nAB"))) }
        assertEquals(listOf("KI · DeepSeek", "Sie"), doc.versions.map { it.author })
        doc = doc.change("KI · DeepSeek", "überarbeitet") { it.copy(title = "Plan 2") }
        assertEquals(3, doc.versions.size)
        assertEquals("KI · DeepSeek", doc.lastAuthor)
        val dir = Files.createTempDirectory("docs").toFile()
        DocsStore(dir).save(doc)
        assertEquals(doc, DocsStore(dir).get(doc.id))
    }

    @Test
    fun liveDraftReadsArgumentsThatAreStillStreaming() {
        val (title, content) = DocsLive.partialArgs("{\"title\": \"Prot\\u00f6koll\", \"content\": \"# Kopf\\n\\nErster Satz mit \\\"Zitat\\\" und wei")
        assertEquals("Protökoll", title)
        assertEquals("# Kopf\n\nErster Satz mit \"Zitat\" und wei", content)
        assertEquals("" to "", DocsLive.partialArgs("{\"tit"))
    }

    @Test
    fun wordWithTrackedChangesIsValidXml() {
        val out = File("build/test-output").apply { mkdirs() }
        val file = File(out, "tracked.docx")
        MarkdownDocx.writeTracked(file, "Plan", md, md.replace("zwei Zeilen", "drei Zeilen").replace("- Punkt zwei", "- Punkt zwei\n- Punkt drei") + "\n\nNeuer Schluss.")
        ZipFile(file).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            assertTrue("<w:ins " in xml && "<w:del " in xml, "Revisionen fehlen")
            assertTrue("<w:delText xml:space=\"preserve\">zwei</w:delText>" in xml, xml.take(3000))
            assertTrue("Neuer Schluss." in xml)
        }
    }
}
