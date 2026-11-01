package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.DesignTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.creative.DesignExport
import io.github.thepytales.llmhub.creative.DesignText
import io.github.thepytales.llmhub.creative.TudIcons
import io.github.thepytales.llmhub.creative.TudLogos
import io.github.thepytales.llmhub.creative.pngBytes
import io.github.thepytales.llmhub.creative.renderDesignPage
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.DesignBrand
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.DesignTemplates
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.docs.PptxWriter
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
        // Colour names of the CD 2025.
        assertEquals("#00008C", DesignBrand.color("Brillantblau"))
        assertEquals("#001450", DesignBrand.color("tud-dunkelblau"))
        assertEquals("#0A777F", DesignBrand.color("Türkis"))
        assertEquals("#AABBCC", DesignBrand.color("abc"))
    }

    @Test
    fun templatesFollowTheCorporateDesign() {
        val palette = DesignBrand.COLORS.map { it.second }.toSet()
        DesignTemplates.all.forEach { t ->
            val els = t.create().pages.flatMap { it.elements }
            // The official logo, never bold headings (Noto Sans Medium), only TUD colours.
            assertTrue(els.any { it.kind == ElementKind.IMAGE && DesignBrand.logo(it.src) != null }, "${t.id}: offizielles Logo")
            assertTrue(els.none { it.bold }, "${t.id}: Überschriften Medium statt fett")
            assertTrue(els.first { it.slot == "titel" }.medium, "${t.id}: Titel in Noto Sans Medium")
            val colours = els.flatMap { listOf(it.fill, it.stroke, it.color) }.filter { it.isNotBlank() } + t.create().pages.map { it.background }
            colours.forEach { assertTrue(it.uppercase() in palette, "${t.id}: $it ist keine TUD Farbe") }
            // The Shape as key visual (only the digital poster follows the official layout without it).
            if (t.id != "digital") assertTrue(els.any { it.kind == ElementKind.TUD_SHAPE || it.mask == "shape" }, "${t.id}: Shape")
            els.filter { it.kind == ElementKind.TUD_SHAPE || it.mask == "shape" }.forEach {
                assertEquals(it.w, it.h, "${t.id}: Shape nicht verzerrt")
                assertEquals(0f, it.rotation % 45f, "${t.id}: Shape in 45°-Schritten")
            }
            els.filter { it.kind == ElementKind.ICON }.forEach { assertTrue(it.icon.startsWith("tud:") && TudIcons.byId(it.icon) != null, "${t.id}: TUD-Icon ${it.icon}") }
        }
        assertEquals(DesignTemplates.DIGITAL_POSTER, DesignTemplates.byId("digitales Poster für den Bildschirm"))
        assertTrue(TudLogos.vector("kurz-blau") != null && TudLogos.vector("horizontal-weiss-de") != null, "Logos aus dem CD-Paket")
        assertTrue(TudIcons.sets.sumOf { it.icons.size } > 500, "TUD-Icons")
    }

    @Test
    fun presentationFollowsTheOfficialTemplate() {
        val file = File(out, "praesentation.pptx")
        val md = "# Feedback im Unterricht\n## Dr. Erika Muster\n\n---\n\n# Grundlagen\n\nWas wir wissen\n\n---\n\n## Befunde\n\n- Erstens\n- Zweitens" +
            "\n\n---\n\n## Stimmen\n\n> Feedback wirkt, wenn es ankommt.\n> — John Hattie\n\n---\n\n<!-- layout: ende -->\n# Vielen Dank!\n\nerika.muster@tu-dresden.de"
        PptxWriter.write(file, "Feedback", md, "Fakultät Erziehungswissenschaften")
        ZipFile(file).use { z ->
            fun entry(name: String) = z.getInputStream(z.getEntry(name)).readBytes().decodeToString()
            val cover = entry("ppt/slides/slide1.xml")
            assertTrue("logo-weiss" in entry("ppt/slides/_rels/slide1.xml.rels") || "rIdLogoW" in cover, "weißes Logo auf der Titelfolie")
            assertTrue(cover.indexOf("Dr. Erika Muster") < cover.indexOf("Feedback im Unterricht"), "Untertitel über dem Titel")
            val content = entry("ppt/slides/slide3.xml")
            assertTrue("Feedback im Unterricht • Dr. Erika Muster • Seite 3" in content, "Fußzeile wie in der CD-Vorlage")
            assertTrue("rIdLogoK" in content && z.getEntry("ppt/media/logo-kurz-blau.png") != null, "Kurzlogo „TUD“ unten links")
            assertTrue("Noto Sans Medium" in content, "Titel in Noto Sans Medium")
            val section = entry("ppt/slides/slide2.xml")
            assertTrue("custGeom" in section && "97C6FF" in section, "Abschnittsfolie mit der Shape in Blau 2")
            assertTrue("TUD_2025" in entry("ppt/theme/theme1.xml"), "Farbschema der CD")
        }
    }
}
