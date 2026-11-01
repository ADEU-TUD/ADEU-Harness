package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.DesignTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.creative.DesignExport
import io.github.thepytales.llmhub.creative.DesignText
import io.github.thepytales.llmhub.creative.pngBytes
import io.github.thepytales.llmhub.creative.renderDesignPage
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.DesignBrand
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.DesignTemplates
import io.github.thepytales.llmhub.data.ElementKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kreativbereich (AP-24): templates, text fitting, the agent's tools and the exports PNG, PDF and PowerPoint. */
class CreativeTest {
    private fun container(): AppContainer {
        val base = Files.createTempDirectory("adeu-creative").toFile()
        return AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    /** Pictures of all templates for a look at them (desktop/build/creative-look). */
    private val out = File("build/creative-look").apply { mkdirs() }

    @Test
    fun everyTemplateRendersAndExports() {
        val c = container()
        DesignTemplates.all.forEach { t ->
            val d = c.designs.save(t.create())
            assertTrue(t.slots.any { it.first == "titel" }, "${t.id} hat einen Titel-Platzhalter")
            val png = renderDesignPage(d, 0, 600).pngBytes()
            assertTrue(png.size > 1000, "${t.id}: PNG")
            File(out, "${t.id}.png").writeBytes(png)
            val pdf = File(out, "${t.id}.pdf")
            DesignExport.writePdf(d, pdf)
            Loader.loadPDF(pdf).use { doc ->
                assertEquals(1, doc.numberOfPages)
                val text = PDFTextStripper().getText(doc).replace(Regex("\\s+"), " ")
                val title = d.pages[0].elements.first { it.slot == "titel" }.text.replace(Regex("\\s+"), " ")
                assertTrue(title.split(' ').first() in text, "${t.id}: Text im PDF ($text)")
                // Page size in mm (1 pt = 25.4/72 mm).
                assertEquals(d.width, doc.getPage(0).mediaBox.width * 25.4f / 72f, 0.5f)
            }
            val pptx = File(out, "${t.id}.pptx")
            DesignExport.writePptx(d, pptx)
            ZipFile(pptx).use { z ->
                val slide = z.getInputStream(z.getEntry("ppt/slides/slide1.xml")).readBytes().decodeToString()
                assertTrue("Noto Sans" in slide && "<p:sp>" in slide, "${t.id}: echte Textfelder")
                assertNotNull(z.getEntry("ppt/presentation.xml"))
                assertTrue(z.entries().asSequence().any { it.name.startsWith("ppt/media/") }, "${t.id}: Logo als Bild")
            }
        }
    }

    @Test
    fun printPdfWithBleedHasTrimBox() {
        val c = container()
        val d = c.designs.save(DesignTemplates.NOTICE.create())
        val pdf = Files.createTempFile("aushang", ".pdf").toFile()
        DesignExport.writePdf(d, pdf, bleed = true)
        Loader.loadPDF(pdf).use { doc ->
            val page = doc.getPage(0)
            assertEquals(210f + 2 * 11f, page.mediaBox.width * 25.4f / 72f, 0.5f)
            assertEquals(210f, page.trimBox.width * 25.4f / 72f, 0.5f)
            assertEquals(216f, page.bleedBox.width * 25.4f / 72f, 0.5f)
        }
    }

    @Test
    fun longTextShrinksToFitItsBox() {
        val e = DesignElement(kind = ElementKind.TEXT, w = 60f, h = 20f, fontSize = 40f, fit = true, text = "Ein sehr langer Titel, der niemals in diese kleine Box passt, ohne kleiner zu werden")
        val laid = DesignText.layout(e)
        assertTrue(laid.fontSize < 40f, "kleiner geworden: ${laid.fontSize}")
        assertTrue(laid.layout.size.height / DesignText.REF <= 20.5f)
        val fixed = DesignText.layout(e.copy(fit = false))
        assertEquals(40f, fixed.fontSize)
        // A short text keeps its size.
        assertEquals(40f, DesignText.layout(e.copy(text = "Kurz", w = 120f)).fontSize)
    }

    @Test
    fun agentMakesAPosterFromTemplateAndChangesIt() = runBlocking {
        val c = container()
        val tools = c.designTools
        val created = tools.run(
            DesignTools.CREATE,
            buildJsonObject {
                put("template", "poster")
                put("title", "Poster Feedback")
                putJsonObject("texts") {
                    put("titel", "Feedback im Mathematikunterricht")
                    put("autoren", "A. Muster, B. Beispiel")
                    put("abschnitt1_text", "• Frage 1\\n• Frage 2")
                }
                put("qr", "https://doi.org/10.1234/abc")
            },
            RepoContext(mutableListOf(), chatId = "chat-1"),
        ).text
        val id = DesignTools.idIn(created)
        assertNotNull(id, created)
        val d = c.designs.get(id)!!
        assertEquals("Poster Feedback", d.title)
        assertEquals("chat-1", d.chatId)
        val els = d.pages[0].elements
        assertEquals("Feedback im Mathematikunterricht", els.first { it.slot == "titel" }.text)
        assertEquals("• Frage 1\n• Frage 2", els.first { it.slot == "abschnitt1_text" }.text, "\\n wird zum Absatz")
        assertEquals("https://doi.org/10.1234/abc", els.first { it.kind == ElementKind.QR }.text)
        assertTrue("abschnitt2_text" in created, "nennt die noch offenen Platzhalter: $created")

        val changed = tools.run(
            DesignTools.UPDATE,
            buildJsonObject {
                put("id", id)
                putJsonObject("texts") { put("qr", "") }
                put("elements", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("kind", "text"); put("x", 30); put("y", 760); put("w", 200); put("h", 10); put("text", "Gefördert von …"); put("color", "Grau") })
                })
            },
            RepoContext(mutableListOf()),
        ).text
        assertTrue("geändert" in changed, changed)
        val d2 = c.designs.get(id)!!
        assertNull(d2.pages[0].elements.firstOrNull { it.kind == ElementKind.QR }, "leerer Text entfernt den QR-Code")
        assertEquals(DesignBrand.color("Grau"), d2.pages[0].elements.last().color)
        assertTrue(DesignTools.describe(d2).contains("[titel]"))
        assertTrue(tools.run(DesignTools.CREATE, buildJsonObject { put("template", "gibtsnicht") }, RepoContext(mutableListOf())).text.startsWith("FEHLER"))
        // An empty page of a screen format.
        val blank = DesignTools.idIn(tools.run(DesignTools.CREATE, buildJsonObject { put("template", "leer"); put("format", "post") }, RepoContext(mutableListOf())).text)!!
        assertEquals(1080, DesignFormat.byId(c.designs.get(blank)!!.format)!!.pxWidth)
        assertEquals("1344x768", DesignTools.imageSize(16f / 9f))
        assertEquals(JsonPrimitive("x").content, "x")
    }

    @Test
    fun storeKeepsPicturesAndCopies() {
        val c = container()
        val d = c.designs.save(DesignTemplates.FLYER.create())
        val name = c.designs.addAsset(d.id, renderDesignPage(d, 0, 50).pngBytes())
        assertNotNull(c.designs.asset(d.id, name))
        assertNull(c.designs.asset(d.id, "../design.json"), "keine Pfade aus dem Ordner heraus")
        val copy = c.designs.duplicate(d.id)!!
        assertNotNull(c.designs.asset(copy.id, name), "Bilder kommen mit")
        c.designs.delete(d.id)
        assertNull(c.designs.get(d.id))
        assertEquals(1, c.designs.items.value.size)
        assertTrue(AppTool.entries.none { it.id == "creative" }, "kein einschaltbares Werkzeug mehr, sondern Teil von „Erstellen“")
        assertTrue(DesignTemplates.fits("Mach mir ein Tagungsposter für die DGfE") && !DesignTemplates.fits("Schreib ein Protokoll der Sitzung"))
        assertEquals("#00008C", DesignBrand.color("tud-dunkelblau"))
        assertEquals("#AABBCC", DesignBrand.color("abc"))
    }
}
