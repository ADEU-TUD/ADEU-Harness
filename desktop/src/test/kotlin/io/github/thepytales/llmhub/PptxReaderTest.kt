package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.docs.ChartSpec
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PptxMerge
import io.github.thepytales.llmhub.docs.PptxReader
import io.github.thepytales.llmhub.docs.PptxWriter
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SlideElement
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AP-51 PowerPoint-Treue: foreign decks with masters, layouts, placeholders, theme colours, groups, tables and charts
 * are read like PowerPoint shows them, and saving keeps what the editor does not know.
 */
class PptxReaderTest {
    private val ns = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\""
    private val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private val grp = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>"

    private fun rels(vararg r: Triple<String, String, String>) = head + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        r.joinToString("") { (id, type, target) -> "<Relationship Id=\"$id\" Type=\"$rel/$type\" Target=\"$target\"/>" } + "</Relationships>"

    private fun xfrm(x: Long, y: Long, w: Long, h: Long) = "<a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm>"

    private fun ph(id: Int, type: String?, idx: String?, body: String, spPr: String = "") =
        "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Platzhalter $id\"/><p:cNvSpPr/><p:nvPr><p:ph${type?.let { " type=\"$it\"" }.orEmpty()}${idx?.let { " idx=\"$it\"" }.orEmpty()}/></p:nvPr></p:nvSpPr>" +
            "<p:spPr>$spPr</p:spPr><p:txBody><a:bodyPr/><a:lstStyle/>$body</p:txBody></p:sp>"

    private fun p(text: String, lvl: Int = 0, rPr: String = "") = "<a:p>${if (lvl > 0) "<a:pPr lvl=\"$lvl\"/>" else ""}<a:r><a:rPr lang=\"de-DE\"$rPr</a:rPr><a:t>$text</a:t></a:r></a:p>"
        .replace("<a:rPr lang=\"de-DE\"</a:rPr>", "<a:rPr lang=\"de-DE\"/>")

    private val png: ByteArray by lazy {
        val img = java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB)
        ByteArrayOutputStream().also { javax.imageio.ImageIO.write(img, "png", it) }.toByteArray()
    }

    private val chartXml = head + "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><c:chart>" +
        "<c:title><c:tx><c:rich><a:p><a:r><a:t>Teilnahme</a:t></a:r></a:p></c:rich></c:tx></c:title><c:plotArea><c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/>" +
        "<c:ser><c:idx val=\"0\"/><c:tx><c:v>2026</c:v></c:tx><c:cat><c:strLit><c:ptCount val=\"3\"/><c:pt idx=\"0\"><c:v>Jan</c:v></c:pt><c:pt idx=\"1\"><c:v>Feb</c:v></c:pt><c:pt idx=\"2\"><c:v>Mär</c:v></c:pt></c:strLit></c:cat>" +
        "<c:val><c:numLit><c:ptCount val=\"3\"/><c:pt idx=\"0\"><c:v>12</c:v></c:pt><c:pt idx=\"1\"><c:v>18</c:v></c:pt><c:pt idx=\"2\"><c:v>9</c:v></c:pt></c:numLit></c:val></c:ser>" +
        "</c:barChart></c:plotArea></c:chart></c:chartSpace>"

    /**
     * A foreign deck (16:9) as PowerPoint writes it: theme „Fremd“ with its own accent colours, a master with colour map,
     * text styles and title/body placeholders, layouts „Titelfolie“, „Titel und Inhalt“ (placeholders without own
     * position), „Zwei Inhalte“, „Nur Titel“ and „Leer“; slides: title, content with shapes and a group, chart, two
     * columns (right one first in the tree), table – plus parts the editor does not know.
     */
    private fun foreignDeck(): ByteArray {
        val f = LinkedHashMap<String, String>()
        val slides = 5
        f["[Content_Types].xml"] = head + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/>" +
            "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>" +
            (1..slides).joinToString("") { "<Override PartName=\"/ppt/slides/slide$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" } +
            "<Override PartName=\"/ppt/charts/chart1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/></Types>"
        f["_rels/.rels"] = rels(Triple("rId1", "officeDocument", "ppt/presentation.xml"))
        f["docProps/core.xml"] = head + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:creator>Anna Muster</dc:creator></cp:coreProperties>"
        f["ppt/presentation.xml"] = head + "<p:presentation $ns><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>" +
            (1..slides).joinToString("") { "<p:sldId id=\"${255 + it}\" r:id=\"rId${it + 1}\"/>" } + "</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/>" +
            "<p:defaultTextStyle><a:lvl1pPr><a:defRPr sz=\"2000\"/></a:lvl1pPr></p:defaultTextStyle>" +
            "<p:extLst><p:ext uri=\"{521415D9-36F7-43E2-AB2F-B90AF26B5E84}\"><p14:sectionLst xmlns:p14=\"http://schemas.microsoft.com/office/powerpoint/2010/main\">" +
            "<p14:section name=\"Teil 1\" id=\"{11111111-1111-1111-1111-111111111111}\"><p14:sldIdLst>" + (1..slides).joinToString("") { "<p14:sldId id=\"${255 + it}\"/>" } +
            "</p14:sldIdLst></p14:section></p14:sectionLst></p:ext></p:extLst></p:presentation>"
        f["ppt/_rels/presentation.xml.rels"] = rels(Triple("rId1", "slideMaster", "slideMasters/slideMaster1.xml"), *(1..slides).map { Triple("rId${it + 1}", "slide", "slides/slide$it.xml") }.toTypedArray(),
            Triple("rId20", "theme", "theme/theme1.xml"))
        f["ppt/theme/theme1.xml"] = head + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Fremd\"><a:themeElements><a:clrScheme name=\"Fremd\">" +
            "<a:dk1><a:sysClr val=\"windowText\" lastClr=\"111111\"/></a:dk1><a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/></a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>" +
            "<a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1><a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3><a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>" +
            "<a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5><a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink>" +
            "</a:clrScheme></a:themeElements></a:theme>"
        val bullets = "<a:lvl1pPr><a:buChar char=\"•\"/><a:defRPr sz=\"2800\"/></a:lvl1pPr><a:lvl2pPr><a:buChar char=\"–\"/><a:defRPr sz=\"2400\"/></a:lvl2pPr>"
        f["ppt/slideMasters/slideMaster1.xml"] = head + "<p:sldMaster $ns><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FAF7F0\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>$grp" +
            ph(2, "title", null, p("Titel"), xfrm(838200, 365125, 10515600, 1325563)) + ph(3, "body", "1", p("Text"), xfrm(838200, 1825625, 10515600, 4351338)) +
            "</p:spTree></p:cSld><p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
            "<p:sldLayoutIdLst>" + (1..5).joinToString("") { "<p:sldLayoutId id=\"${2147483648L + it}\" r:id=\"rId$it\"/>" } + "</p:sldLayoutIdLst>" +
            "<p:txStyles><p:titleStyle><a:lvl1pPr><a:defRPr sz=\"4400\"/></a:lvl1pPr></p:titleStyle><p:bodyStyle>$bullets</p:bodyStyle><p:otherStyle><a:lvl1pPr><a:defRPr sz=\"1800\"/></a:lvl1pPr></p:otherStyle></p:txStyles></p:sldMaster>"
        f["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = rels(*(1..5).map { Triple("rId$it", "slideLayout", "../slideLayouts/slideLayout$it.xml") }.toTypedArray(), Triple("rId9", "theme", "../theme/theme1.xml"))
        val layouts = listOf(
            "title" to ("Titelfolie" to ph(2, "ctrTitle", null, p("Titel"), xfrm(1524000, 1122363, 9144000, 2387600)) + ph(3, "subTitle", "1", p("Untertitel"), xfrm(1524000, 3602038, 9144000, 1655762))),
            "obj" to ("Titel und Inhalt" to ph(2, "title", null, p("Titel")) + ph(3, null, "1", p("Text"))),
            "twoObj" to ("Zwei Inhalte" to ph(2, "title", null, p("Titel")) + ph(3, null, "1", p("Links"), xfrm(838200, 1825625, 5181600, 4351338)) + ph(4, null, "2", p("Rechts"), xfrm(6172200, 1825625, 5181600, 4351338))),
            "titleOnly" to ("Nur Titel" to ph(2, "title", null, p("Titel"))),
            "blank" to ("Leer" to ""),
        )
        layouts.forEachIndexed { i, (type, nameTree) ->
            f["ppt/slideLayouts/slideLayout${i + 1}.xml"] = head + "<p:sldLayout $ns type=\"$type\" preserve=\"1\"><p:cSld name=\"${nameTree.first}\"><p:spTree>$grp${nameTree.second}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"
            f["ppt/slideLayouts/_rels/slideLayout${i + 1}.xml.rels"] = rels(Triple("rId1", "slideMaster", "../slideMasters/slideMaster1.xml"))
        }
        fun slide(n: Int, layout: Int, tree: String, extra: String = "", more: Array<Triple<String, String, String>> = emptyArray()) {
            f["ppt/slides/slide$n.xml"] = head + "<p:sld $ns><p:cSld><p:spTree>$grp$tree</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>$extra</p:sld>"
            f["ppt/slides/_rels/slide$n.xml.rels"] = rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout$layout.xml"), *more)
        }
        // 1: title slide; it carries an extension the editor does not know.
        slide(1, 1, ph(2, "ctrTitle", null, p("Forschungstag 2026")) + ph(3, "subTitle", "1", p("Anna Muster")), "<p:extLst><p:ext uri=\"{LORA-TEST}\"><unbekannt/></p:ext></p:extLst>")
        // 2: content without own positions, a filled shape in a darker accent, a text box in tx1, a group, a logo.
        val shape = "<p:sp><p:nvSpPr><p:cNvPr id=\"10\" name=\"Rechteck 9\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(8000000, 4000000, 2000000, 1000000)}<a:prstGeom prst=\"roundRect\"><a:avLst/></a:prstGeom>" +
            "<a:solidFill><a:schemeClr val=\"accent1\"><a:lumMod val=\"75000\"/></a:schemeClr></a:solidFill></p:spPr>" +
            "<p:style><a:lnRef idx=\"0\"><a:schemeClr val=\"accent1\"/></a:lnRef><a:fillRef idx=\"1\"><a:schemeClr val=\"accent1\"/></a:fillRef><a:effectRef idx=\"0\"><a:schemeClr val=\"accent1\"/></a:effectRef><a:fontRef idx=\"minor\"><a:schemeClr val=\"lt1\"/></a:fontRef></p:style>" +
            "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:pPr algn=\"ctr\"/><a:r><a:rPr lang=\"de-DE\" b=\"1\"/><a:t>Neu!</a:t></a:r></a:p></p:txBody></p:sp>"
        val box = "<p:sp><p:nvSpPr><p:cNvPr id=\"11\" name=\"Textfeld 10\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(720000, 6000000, 3600000, 360000)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>" +
            "<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"de-DE\"><a:solidFill><a:schemeClr val=\"tx2\"><a:lumMod val=\"60000\"/><a:lumOff val=\"40000\"/></a:schemeClr></a:solidFill></a:rPr><a:t>Quelle: Umfrage</a:t></a:r></a:p></p:txBody></p:sp>"
        fun member(id: Int, x: Long, color: String) = "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Ellipse $id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(x, 0, 500000, 500000)}<a:prstGeom prst=\"ellipse\"><a:avLst/></a:prstGeom>" +
            "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill></p:spPr></p:sp>"
        val group = "<p:grpSp><p:nvGrpSpPr><p:cNvPr id=\"12\" name=\"Gruppe 11\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"1000000\" y=\"1000000\"/><a:ext cx=\"2000000\" cy=\"1000000\"/>" +
            "<a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"1000000\" cy=\"500000\"/></a:xfrm></p:grpSpPr>" + member(13, 0, "FF0000") + member(14, 500000, "00FF00") + "</p:grpSp>"
        val logo = "<p:pic><p:nvPicPr><p:cNvPr id=\"15\" name=\"Logo\" descr=\"Logo des Instituts\"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rId7\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
            "<p:spPr>${xfrm(10800000, 180000, 1080000, 540000)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"
        slide(2, 2, ph(2, "title", null, p("Ergebnisse")) + ph(3, null, "1", p("Punkt eins") + p("Unterpunkt", 1) + p("Punkt zwei")) + shape + box + group + logo,
            more = arrayOf(Triple("rId7", "image", "../media/image1.png"), Triple("rId8", "notesSlide", "../notesSlides/notesSlide2.xml")))
        f["ppt/notesSlides/notesSlide2.xml"] = head + "<p:notes $ns><p:cSld><p:spTree>$grp" + ph(2, "sldImg", null, "") + ph(3, "body", "1", p("Langsam sprechen")) + "</p:spTree></p:cSld></p:notes>"
        f["ppt/notesSlides/_rels/notesSlide2.xml.rels"] = rels(Triple("rId1", "slide", "../slides/slide2.xml"))
        // 3: a chart.
        val chartFrame = "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"4\" name=\"Diagramm 3\"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x=\"838200\" y=\"1825625\"/><a:ext cx=\"10515600\" cy=\"4351338\"/></p:xfrm>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" r:id=\"rId2\"/></a:graphicData></a:graphic></p:graphicFrame>"
        slide(3, 4, ph(2, "title", null, p("Zahlen")) + chartFrame, more = arrayOf(Triple("rId2", "chart", "../charts/chart1.xml")))
        f["ppt/charts/chart1.xml"] = chartXml
        // 4: two columns; the right one comes first in the tree.
        slide(4, 3, ph(2, "title", null, p("Vorher und nachher")) + ph(4, null, "2", p("Rechts A") + p("Rechts B")) + ph(3, null, "1", p("Links A")))
        // 5: a table.
        val cell = { t: String -> "<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"de-DE\"/><a:t>$t</a:t></a:r></a:p></a:txBody><a:tcPr/></a:tc>" }
        val table = "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"4\" name=\"Tabelle 3\"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x=\"838200\" y=\"1825625\"/><a:ext cx=\"6000000\" cy=\"740000\"/></p:xfrm>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl><a:tblGrid><a:gridCol w=\"3000000\"/><a:gridCol w=\"3000000\"/></a:tblGrid>" +
            "<a:tr h=\"370000\">${cell("Jahr")}${cell("Wert")}</a:tr><a:tr h=\"370000\">${cell("2025")}${cell("3")}</a:tr></a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
        slide(5, 4, ph(2, "title", null, p("Tabelle der Werte")) + table)
        // Something the editor does not know at all.
        f["ppt/customXml/lora-unbekannt.xml"] = head + "<unbekannt wert=\"42\"/>"
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            f.forEach { (name, text) -> z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            z.putNextEntry(ZipEntry("ppt/media/image1.png")); z.write(png); z.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun parts(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { out[e.name] = z.readBytes(); e = z.nextEntry }
        }
        return out
    }

    private fun near(expected: String, actual: String, tolerance: Int = 3): Boolean {
        val a = expected.removePrefix("#")
        val b = actual.removePrefix("#")
        if (a.length != 6 || b.length != 6) return false
        return (0..4 step 2).all { abs(a.substring(it, it + 2).toInt(16) - b.substring(it, it + 2).toInt(16)) <= tolerance }
    }

    @Test
    fun foreignDeckWithMastersLayoutsAndThemeColours() {
        val media = LinkedHashMap<String, ByteArray>()
        val slides = SlideDeck.parse(OfficeImport.pptx(foreignDeck(), media))
        assertEquals(listOf(SlideDeck.Layout.TITLE, SlideDeck.Layout.CONTENT, SlideDeck.Layout.CHART, SlideDeck.Layout.TWO_COLUMNS, SlideDeck.Layout.TABLE), slides.map { it.layout })
        assertEquals("Forschungstag 2026", slides[0].title)
        assertEquals("Anna Muster", slides[0].body)
        // Foreign design: its accent and background become the Masterfolie, the TUD logo stays off.
        val master = assertNotNull(slides[0].master)
        assertEquals("#4472C4", master.accent)
        assertEquals("#FAF7F0", master.background)
        assertFalse(master.logo)

        // Placeholders without own position inherit bullets from the master's body style.
        val content = slides[1]
        assertEquals("Ergebnisse", content.title)
        assertEquals("- Punkt eins\n  - Unterpunkt\n- Punkt zwei", content.body)
        assertEquals("Langsam sprechen", content.notes)
        val els = content.elements
        // Shape: accent1 darker 25 % (lumMod 75 %), text colour lt1 from the shape style, bold, centred, rounded.
        val rect = els.single { it.text == "Neu!" }
        assertEquals("form", rect.kind)
        assertEquals("abgerundet", rect.shape)
        assertTrue(near("#2F5597", rect.fill), rect.fill)
        assertEquals("#FFFFFF", rect.color)
        assertTrue(rect.bold)
        assertEquals("mitte", rect.align)
        assertEquals(22.22, rect.x, 0.01)
        assertEquals(11.11, rect.y, 0.01)
        assertEquals(5.56, rect.w, 0.01)
        // Text box: tx2 → dk2 lighter 40 %, size from the presentation's default text style.
        val note = els.single { it.text == "Quelle: Umfrage" }
        assertEquals("text", note.kind)
        assertTrue(near("#8497B0", note.color), note.color)
        assertEquals(20, note.size)
        // Group: members placed through chOff/chExt (scale 2) and moving together.
        val members = els.filter { it.shape == "ellipse" }
        assertEquals(2, members.size)
        assertEquals(listOf("#FF0000", "#00FF00"), members.map { it.fill })
        assertEquals(2.78, members[0].x, 0.01)
        assertEquals(5.56, members[1].x, 0.01)
        assertEquals(2.78, members[0].w, 0.01)
        assertTrue(members.all { it.group.isNotEmpty() } && members[0].group == members[1].group)
        // The small logo is a picture element with its description, its file goes into the media.
        val pic = els.single { it.kind == "bild" }
        assertEquals("Logo des Instituts", pic.text)
        assertTrue(pic.src in media)

        // Chart: a ```chart block with the cached values.
        val chart = assertNotNull(SlideDeck.chartOf(slides[2].body))
        assertEquals(ChartSpec.Type.BAR, chart.type)
        assertEquals(listOf("Jan", "Feb", "Mär"), chart.labels)
        assertEquals(listOf(12.0, 18.0, 9.0), chart.series.single().values)
        assertEquals("Zahlen", slides[2].title)

        // Two columns: left and right by the layout's positions, not by the order in the tree.
        assertEquals("- Links A", slides[3].body)
        assertEquals("- Rechts A\n- Rechts B", slides[3].right)
        assertTrue("| Jahr | Wert |" in slides[4].body && "| 2025 | 3 |" in slides[4].body, slides[4].body)

        // The Markdown keeps it all.
        val again = SlideDeck.parse(SlideDeck.markdown(slides))
        assertEquals(slides.map { it.layout }, again.map { it.layout })
        assertEquals(slides[1].elements, again[1].elements)
    }

    @Test
    fun coloursOfTheDesign() {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        fun color(xml: String) = doc.parse(ByteArrayInputStream("<x xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">$xml</x>".toByteArray())).documentElement
        val pal = PptxReader.Palette(mapOf("accent1" to "4472C4", "dk1" to "000000", "lt1" to "FFFFFF"), mapOf("tx1" to "dk1", "bg1" to "lt1"))
        assertEquals("4472C4", pal.color(color("<a:schemeClr val=\"accent1\"/>")))
        assertEquals("000000", pal.color(color("<a:schemeClr val=\"tx1\"/>")))
        assertTrue(near("D9E2F3", pal.color(color("<a:schemeClr val=\"accent1\"><a:lumMod val=\"20000\"/><a:lumOff val=\"80000\"/></a:schemeClr>"))!!))
        assertEquals("808080", pal.color(color("<a:srgbClr val=\"FFFFFF\"><a:lumMod val=\"50000\"/></a:srgbClr>")))
        assertEquals("000000", pal.color(color("<a:srgbClr val=\"FF0000\"><a:shade val=\"0\"/></a:srgbClr>")))
        assertEquals("FFFFFF", pal.color(color("<a:srgbClr val=\"FF0000\"><a:tint val=\"0\"/></a:srgbClr>")))
    }

    @Test
    fun ownDecksKeepFreeElementsAndCharts() {
        val deck = listOf(
            SlideDeck.Slide("Vortrag", "Untertitel", layout = SlideDeck.Layout.TITLE),
            SlideDeck.Slide("Frei", "", layout = SlideDeck.Layout.FREE, elements = listOf(
                SlideElement(kind = "text", x = 3.0, y = 4.0, w = 10.0, h = 2.0, text = "Hallo", size = 28, bold = true, color = "#00008C"),
                SlideElement(kind = "form", shape = "ellipse", x = 15.0, y = 5.0, w = 4.0, h = 4.0, fill = "#97C6FF", text = "Kreis", color = "#FFFFFF"),
            )),
            SlideDeck.template(SlideDeck.Layout.CHART).copy(title = "Werte"),
        )
        val file = Files.createTempFile("own", ".pptx").toFile()
        PptxWriter.write(file, "Vortrag", SlideDeck.markdown(deck), "Professur")
        val back = SlideDeck.parse(PptxReader.read(file.readBytes()))
        file.delete()
        assertEquals(SlideDeck.Layout.FREE, back[1].layout)
        val els = back[1].elements
        assertEquals(listOf("Hallo", "Kreis"), els.map { it.text })
        assertEquals(3.0, els[0].x, 0.01)
        assertEquals(28, els[0].size)
        assertTrue(els[0].bold)
        assertEquals("ellipse", els[1].shape)
        assertEquals("#97C6FF", els[1].fill)
        assertEquals("Werte", back[2].title)
    }

    @Test
    fun savingKeepsWhatTheEditorDoesNotKnow() {
        val original = foreignDeck()
        val base = OfficeImport.pptx(original)
        val slides = SlideDeck.parse(base)
        // Slide 4 gets a point more (new structure), slide 5 goes, a new slide comes at the end, slide 2 moves behind 3.
        val now = listOf(slides[0], slides[2], slides[1], slides[3].copy(body = slides[3].body + "\n- Links B"), SlideDeck.Slide("Neue Folie", "- Von Lora", notes = "Neu dazu"))
        val nowMd = SlideDeck.markdown(now)
        val generated = Files.createTempFile("gen", ".pptx").toFile()
        PptxWriter.write(generated, "Vortrag", nowMd, "Professur")
        val merged = assertNotNull(PptxMerge.merge(original, base, nowMd, generated.readBytes()))
        generated.delete()
        val before = parts(original)
        val after = parts(merged)
        // Unknown parts and unchanged slides stay byte for byte, the master and layouts as well.
        assertTrue(after["ppt/customXml/lora-unbekannt.xml"]!!.contentEquals(before["ppt/customXml/lora-unbekannt.xml"]!!))
        for (k in listOf("ppt/slides/slide1.xml", "ppt/slides/slide2.xml", "ppt/slides/slide3.xml", "ppt/slideMasters/slideMaster1.xml", "ppt/slideLayouts/slideLayout2.xml", "ppt/theme/theme1.xml", "ppt/charts/chart1.xml")) {
            assertTrue(after[k]!!.contentEquals(before[k]!!), k)
        }
        assertTrue("{LORA-TEST}" in after.getValue("ppt/slides/slide1.xml").toString(Charsets.UTF_8))
        // The deleted slide is gone, also from the section; the new slides use the „Leer“ layout and hide the master graphics.
        assertFalse("ppt/slides/slide5.xml" in after)
        val pres = after.getValue("ppt/presentation.xml").toString(Charsets.UTF_8)
        assertFalse("id=\"260\"" in pres, pres)
        val order = Regex("<p:sldId id=\"(\\d+)\"").findAll(pres).map { it.groupValues[1] }.toList()
        assertEquals(5, order.size)
        assertEquals(listOf("256", "258", "257"), order.take(3))
        assertEquals(5, Regex("<p14:sldId id=").findAll(pres).count())
        val added = after.keys.filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) && it !in before }
        assertEquals(2, added.size)
        added.forEach { k ->
            val xml = after.getValue(k).toString(Charsets.UTF_8)
            assertTrue("showMasterSp=\"0\"" in xml && "<p:cSld name=\"${PptxMerge.OWN_SLIDE}\">" in xml)
            val r = after.getValue(k.replace("slides/", "slides/_rels/") + ".rels").toString(Charsets.UTF_8)
            assertTrue("../slideLayouts/slideLayout5.xml" in r, r)
            Regex("Target=\"\\.\\./([^\"]+)\"").findAll(r).forEach { m -> assertTrue("ppt/" + m.groupValues[1] in after, m.value) }
        }
        val types = after.getValue("[Content_Types].xml").toString(Charsets.UTF_8)
        assertTrue(added.all { "PartName=\"/$it\"" in types } && "PartName=\"/ppt/slides/slide5.xml\"" !in types)
        // Read back: the order, the changed slide, the new slide with its notes (a notes master came along).
        val back = SlideDeck.parse(PptxReader.read(merged))
        assertEquals(listOf("Forschungstag 2026", "Zahlen", "Ergebnisse", "Vorher und nachher", "Neue Folie"), back.map { it.title })
        assertTrue("Links B" in back[3].body, back[3].body)
        assertEquals("- Von Lora", back[4].body)
        assertEquals("Neu dazu", back[4].notes)
        assertEquals(back[2].elements, slides[1].elements)
    }

    @Test
    fun textChangesStayInTheSlide() {
        val original = foreignDeck()
        val base = OfficeImport.pptx(original)
        val slides = SlideDeck.parse(base)
        // Same structure, other text – and the notes: written into the slide, the rest of it stays.
        val now = slides.mapIndexed { i, s -> if (i == 1) s.copy(body = s.body.replace("Punkt zwei", "Punkt drei"), notes = "Ruhig sprechen") else s }.dropLast(1)
        val nowMd = SlideDeck.markdown(now)
        val generated = Files.createTempFile("gen", ".pptx").toFile()
        PptxWriter.write(generated, "Vortrag", nowMd, "Professur")
        val merged = assertNotNull(PptxMerge.merge(original, base, nowMd, generated.readBytes()))
        generated.delete()
        val after = parts(merged)
        val s2 = after.getValue("ppt/slides/slide2.xml").toString(Charsets.UTF_8)
        assertTrue("Punkt drei" in s2 && "Rechteck 9" in s2 && "Gruppe 11" in s2)
        assertTrue("Ruhig sprechen" in after.getValue("ppt/notesSlides/notesSlide2.xml").toString(Charsets.UTF_8))
        assertEquals(slides.size - 1, SlideDeck.parse(PptxReader.read(merged)).size)
    }
}
