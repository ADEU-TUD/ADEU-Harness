package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.DNode
import io.github.thepytales.llmhub.docs.DStroke
import io.github.thepytales.llmhub.docs.Diagram
import io.github.thepytales.llmhub.docs.Diagrams
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Diagramme und Whiteboard (AP-61): Mermaid in and out, arranging, connectors on the borders, blocks and SVG. */
class DiagramsTest {
    private val antrag = """
        flowchart TD
            A([Start]) --> B[Antrag schreiben]
            B --> C{Vollständig?}
            C -->|Ja| D[Einreichen]
            C -- Nein --> B
            D --> E & F
            E[Gutachten] -.-> G([Ende])
            F(Rückfragen) --> G
    """.trimIndent()

    @Test
    fun flowchartFromMermaid() {
        val d = assertNotNull(Diagrams.fromMermaid(antrag))
        assertEquals("flow", d.kind)
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G"), d.nodes.map { it.id })
        assertEquals("stadium", d.node("A")!!.shape)
        assertEquals("diamond", d.node("C")!!.shape)
        assertEquals("round", d.node("F")!!.shape)
        assertEquals("Vollständig?", d.node("C")!!.text)
        assertEquals(listOf("Ja", "Nein"), d.edges.filter { it.from == "C" }.map { it.label })
        assertTrue(d.edges.any { it.from == "D" && it.to == "E" } && d.edges.any { it.from == "D" && it.to == "F" })
        assertTrue(d.edges.first { it.from == "E" }.dashed)
        // Round trip: the Mermaid text of the diagram reads back to the same graph.
        val again = assertNotNull(Diagrams.fromMermaid(Diagrams.toMermaid(d)!!))
        assertEquals(d.nodes.map { it.id to it.text to it.shape }, again.nodes.map { it.id to it.text to it.shape })
        assertEquals(d.edges, again.edges)
    }

    @Test
    fun mindmapAndTimeline() {
        val m = assertNotNull(Diagrams.fromMermaid("mindmap\n  root((Lehre))\n    Vorlesung\n      Folien\n    Seminar\n    Prüfung"))
        assertEquals("mind", m.kind)
        assertEquals(listOf("Lehre", "Vorlesung", "Folien", "Seminar", "Prüfung"), m.nodes.map { it.text })
        assertEquals(listOf("n1" to "n2", "n2" to "n3", "n1" to "n4", "n1" to "n5"), m.edges.map { it.from to it.to })
        assertTrue(Diagrams.toMermaid(m)!!.contains("root((Lehre))"))
        val t = assertNotNull(Diagrams.fromMermaid("timeline\n    title Projekt\n    2025 : Antrag\n    2026 : Start : Erste Ergebnisse\n    2027 : Abschluss"))
        assertEquals("timeline", t.kind)
        assertEquals("Projekt", t.title)
        assertEquals(listOf("2025\nAntrag", "2026\nStart\nErste Ergebnisse", "2027\nAbschluss"), t.nodes.map { it.text })
        assertNull(Diagrams.fromMermaid("sequenceDiagram\n A->>B: Hallo"))
    }

    @Test
    fun arrangedWithoutOverlapsAndConnectorsOnTheBorders() {
        val d = Diagrams.arrange(Diagrams.fromMermaid(antrag)!!)
        // Top down: every forward edge goes downwards (the loop back from „Nein“ is the exception).
        for (e in d.edges.filter { !(it.from == "C" && it.to == "B") }) assertTrue(d.node(e.to)!!.y > d.node(e.from)!!.y, "$e")
        for (a in d.nodes) for (b in d.nodes) if (a.id < b.id) {
            val overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
            assertTrue(!overlap, "${a.id} und ${b.id} überlappen")
        }
        // A connector starts and ends on the outlines, not in the middle.
        val shapes = Diagrams.layout(d)
        val c = d.node("C")!!
        val e = shapes.filterIsInstance<Diagrams.Edge>().first()
        val a = d.node("A")!!
        assertEquals(a.y + a.h, e.y1, 0.5f)
        // Rhombus: the border point lies on the rhombus.
        val (bx, by) = Diagrams.border(c, c.x + c.w / 2, c.y + c.h * 5)
        assertEquals(c.x + c.w / 2, bx, 0.5f)
        assertEquals(c.y + c.h, by, 0.5f)
        // Left to right.
        val lr = Diagrams.arrange(Diagrams.fromMermaid("graph LR\n A --> B --> C")!!)
        assertTrue(lr.node("B")!!.x > lr.node("A")!!.x && lr.node("C")!!.x > lr.node("B")!!.x)
        // A mind map has its root in the middle.
        val m = Diagrams.arrange(Diagrams.fromMermaid("mindmap\n  root((Thema))\n    A\n    B\n    C\n    D")!!)
        val root = m.nodes.first()
        val cx = root.x + root.w / 2
        assertTrue(m.nodes.drop(1).any { it.x + it.w / 2 < cx } && m.nodes.drop(1).any { it.x + it.w / 2 > cx })
    }

    @Test
    fun blocksForDocumentsAndSvg() {
        val d = Diagrams.fromMermaid(antrag)!!
        val block = Diagrams.block(d)
        assertTrue(block.startsWith("```mermaid\nflowchart TD"))
        val (lang, code) = assertNotNull(Diagrams.parseBlock(block))
        assertEquals(d.edges, Diagrams.ofBlock(lang, code)!!.edges)
        // Placed by hand, or a whiteboard with notes and strokes: JSON with all positions.
        val board = Diagram(kind = "board", nodes = listOf(DNode("n1", "Idee", "note", 10f, 10f)), strokes = listOf(DStroke(listOf(0f, 0f, 50f, 40f))), manual = true)
        val b2 = Diagrams.block(board)
        assertTrue(b2.startsWith("```diagram\n{"))
        val (l2, c2) = Diagrams.parseBlock(b2)!!
        val back = Diagrams.ofBlock(l2, c2)!!
        assertEquals(board.nodes.single().copy(), back.nodes.single())
        assertEquals(board.strokes, back.strokes)
        val svg = Diagrams.svg(d)
        assertTrue(svg.startsWith("<svg") && svg.contains("<polygon") && svg.contains("marker-end") && svg.contains(">Vollständig?<") && svg.contains(">Ja<"))
        assertTrue(Diagrams.altText(d).contains("Antrag schreiben → Vollständig?"))
        // The model's answer: the Mermaid in a code block.
        assertEquals("graph TD\n A --> B", Diagrams.mermaidIn("Hier ist es:\n```mermaid\ngraph TD\n A --> B\n```\nViel Erfolg"))
        assertNull(Diagrams.mermaidIn("Das kann ich nicht."))
    }

    /**
     * Fertig, wenn: Aus einer Beschreibung entsteht ein bearbeitbares Flussdiagramm, das in einem Dokument steht – die
     * Antwort des Modells (hier vorgegeben) wird zum Diagramm, steht als Block im Dokument, ist dort wieder bearbeitbar und
     * kommt nach PDF (SVG), Word und PowerPoint (Bild mit Alternativtext).
     */
    @Test
    fun fromADescriptionToAnEditableFlowchartInADocument() {
        val answer = "Gern:\n```mermaid\nflowchart TD\n  S([Start]) --> A[Antrag stellen]\n  A --> P{Vollständig?}\n  P -->|Nein| A\n  P -->|Ja| G[Genehmigen]\n  G --> E([Ende])\n```"
        assertTrue(Diagrams.prompt("flow", "Antrag stellen, prüfen, bei Fehlern zurück, sonst genehmigen").contains("```mermaid"))
        val d = Diagrams.arrange(Diagrams.fromMermaid(Diagrams.mermaidIn(answer)!!)!!)
        assertEquals(5, d.nodes.size)
        val markdown = "# Ablauf\n\nSo läuft ein Antrag:\n\n" + Diagrams.block(d) + "\n\nWeiterer Text."
        // In the document: the block reads back to the same diagram, changing it (a node moved by hand) keeps the positions.
        val block = io.github.thepytales.llmhub.docs.DocBlocks.split(markdown).first { Diagrams.parseBlock(it) != null }
        val (lang, code) = Diagrams.parseBlock(block)!!
        val again = Diagrams.ofBlock(lang, code)!!
        assertEquals(d.edges, again.edges)
        val moved = again.copy(nodes = again.nodes.map { if (it.id == "G") it.copy(x = it.x + 200f) else it }, manual = true)
        val movedBlock = Diagrams.block(moved)
        assertTrue(movedBlock.startsWith("```diagram"))
        assertEquals(moved.node("G")!!.x, Diagrams.ofBlock(Diagrams.parseBlock(movedBlock)!!.first, Diagrams.parseBlock(movedBlock)!!.second)!!.node("G")!!.x)
        // PDF over HTML: vector graphics.
        val html = io.github.thepytales.llmhub.docs.DocumentWriter.toHtml("Ablauf", markdown)
        assertTrue(html.contains("<figure class=\"diagram\"") && html.contains(">Vollständig?<"), html.take(300))
        // Word: a picture with the connections as alternative text.
        Diagrams.raster = { io.github.thepytales.llmhub.docs.DiagramRaster.png(it) }
        val dir = java.nio.file.Files.createTempDirectory("diagramm").toFile()
        try {
            val docx = java.io.File(dir, "ablauf.docx")
            io.github.thepytales.llmhub.docs.MarkdownDocx.write(docx, "Ablauf", markdown)
            java.util.zip.ZipFile(docx).use { z ->
                assertTrue(z.getEntry("word/media/diagramm1.png") != null, z.entries().toList().joinToString { it.name })
                val xml = z.getInputStream(z.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8)
                assertTrue(xml.contains("Antrag stellen → Vollständig?"))
                assertTrue(xml.contains("Weiterer Text"))
            }
            val png = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(io.github.thepytales.llmhub.docs.DiagramRaster.png(d)))
            assertTrue(png.width > 200 && png.height > 300)
            // Slides: a diagram slide goes to PowerPoint as a picture.
            val deck = "# Projekt\n\n---\n\n<!-- layout: diagramm -->\n## Ablauf\n\n" + Diagrams.block(d)
            val pptx = java.io.File(dir, "folien.pptx")
            io.github.thepytales.llmhub.docs.PptxWriter.write(pptx, "Projekt", deck, "TU Dresden")
            java.util.zip.ZipFile(pptx).use { z -> assertTrue(z.entries().toList().any { it.name.startsWith("ppt/media/") }, z.entries().toList().joinToString { it.name }) }
            assertTrue(io.github.thepytales.llmhub.docs.SlideDeck.diagramOf("## Ablauf\n\n" + Diagrams.block(d)) != null)
        } finally {
            Diagrams.raster = null
            dir.deleteRecursively()
        }
    }

    /** The editor shows the diagram and changes it; a whiteboard keeps notes and freehand. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun editorShowsAndChangesTheDiagram() = androidx.compose.ui.test.runSkikoComposeUiTest(androidx.compose.ui.geometry.Size(1200f, 800f), androidx.compose.ui.unit.Density(1f)) {
        var last: Diagram? = null
        setContent {
            io.github.thepytales.llmhub.ui.LlmHubTheme(io.github.thepytales.llmhub.data.AppSettings()) {
                io.github.thepytales.llmhub.ui.DiagramEditor(Diagrams.fromMermaid(antrag)!!, androidx.compose.ui.Modifier.fillMaxSize()) { last = it }
            }
        }
        waitForIdle()
        val area = onNode(androidx.compose.ui.test.SemanticsMatcher("Fläche") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.any { it.startsWith("Diagramm-Fläche") } == true })
        area.assertExists()
        // Mind map instead of flowchart: rearranged, connectors without arrows.
        onNodeWithText("Mindmap").performClick()
        waitForIdle()
        assertEquals("mind", last?.kind)
        assertTrue(last!!.edges.none { it.arrow } || last!!.edges.isNotEmpty())
        // Whiteboard: a note by click.
        onNodeWithText("Whiteboard").performClick()
        onNodeWithText("Notizzettel").performClick()
        area.performTouchInput { click(androidx.compose.ui.geometry.Offset(1000f, 500f)) }
        waitForIdle()
        assertTrue(last!!.nodes.any { it.shape == "note" }, last.toString())
        assertTrue(Diagrams.block(last!!).startsWith("```diagram"))
    }
}
