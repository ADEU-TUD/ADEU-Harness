package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.docs.text.CharFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Frei gestalten in PowerPoint (AP-16 Stufe 3): adds the freely placed elements of each slide (text boxes, pictures,
 * shapes) to the .pptx the layouts were written into, and the Masterfolie's background and accent. Elements are real
 * PowerPoint shapes – movable and editable there.
 */
object SlideExtras {
    private const val EMU_PER_CM = 360000L
    private const val IMAGE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"

    private fun emu(cm: Double) = (cm * EMU_PER_CM).toLong()
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun hex(c: String) = c.trim().removePrefix("#").uppercase().takeIf { it.length == 6 && it.all { ch -> ch.isDigit() || ch in 'A'..'F' } }

    fun apply(file: File, markdown: String, base: File?) {
        val texts = SlideDeck.split(markdown)
        val elements = texts.map { SlideDeck.elementsOf(it) }
        val master = SlideDeck.masterOf(markdown)
        if (elements.all { it.isEmpty() } && master == null) return
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(file.readBytes())).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) files[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }
        val imageTypes = mutableSetOf<String>()
        texts.indices.forEach { i ->
            val n = i + 1
            val path = "ppt/slides/slide$n.xml"
            var xml = files[path]?.toString(Charsets.UTF_8) ?: return@forEach
            // Masterfolie: background of the light slides (title and end slide keep TUD blue).
            master?.background?.let(::hex)?.let { bg ->
                if ("<p:bg>" !in xml) xml = xml.replaceFirst("<p:cSld>", "<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"$bg\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>")
            }
            val rels = StringBuilder()
            val shapes = StringBuilder()
            elements[i].forEachIndexed { k, el ->
                val id = 1000 + k
                // Rotation and mirroring of the shared canvas (AP-50) in PowerPoint's own attributes.
                val rot = ((el.rotation % 360 + 360) % 360).takeIf { it > 0.01 }?.let { " rot=\"${Math.round(it * 60000)}\"" }.orEmpty()
                val flip = if (el.flipX) " flipH=\"1\"" else ""
                val off = "<a:xfrm$rot$flip><a:off x=\"${emu(el.x)}\" y=\"${emu(el.y)}\"/><a:ext cx=\"${emu(el.w.coerceAtLeast(0.2))}\" cy=\"${emu(el.h.coerceAtLeast(0.2))}\"/></a:xfrm>"
                fun picture(bytes: ByteArray, descr: String) {
                    val type = DocMarkup.imageType(bytes) ?: "png"
                    val media = "ppt/media/frei${n}_$k.$type"
                    files[media] = bytes
                    imageTypes += type
                    val rid = "rIdFrei$k"
                    rels.append("<Relationship Id=\"$rid\" Type=\"$IMAGE_REL\" Target=\"../media/frei${n}_$k.$type\"/>")
                    shapes.append(
                        "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Bild ${k + 1}\" descr=\"${esc(descr)}\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                            "<p:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                            "<p:spPr>$off<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>",
                    )
                }
                when (el.kind) {
                    "symbol", "qr" -> {
                        // Drawn by the renderer of Gestalten (Windows), sharp at up to 24 px per mm.
                        val d = io.github.thepytales.llmhub.creative.SlideCanvas.toDesign(el, "x")
                        val bytes = io.github.thepytales.llmhub.creative.CanvasArt.png(d, (2400f / maxOf(d.w, d.h, 1f)).coerceIn(6f, 24f)) ?: return@forEachIndexed
                        picture(bytes, if (el.kind == "qr") "QR-Code: " + el.text else "Symbol")
                    }
                    "tudshape" -> {
                        // The Shape as PowerPoint's own geometry: movable, recolourable, never distorted.
                        val wE = emu(el.w)
                        val hE = emu(el.h)
                        val side = minOf(el.w, el.h)
                        val ox = (el.w - side) / 2
                        val oy = (el.h - side) / 2
                        val paths = TudBrand.SHAPE.joinToString("") { poly ->
                            "<a:path w=\"$wE\" h=\"$hE\">" + (poly.indices step 2).joinToString("") { j ->
                                val pt = "<a:pt x=\"${emu(ox + poly[j] * side / 600.0)}\" y=\"${emu(oy + poly[j + 1] * side / 600.0)}\"/>"
                                if (j == 0) "<a:moveTo>$pt</a:moveTo>" else "<a:lnTo>$pt</a:lnTo>"
                            } + "<a:close/></a:path>"
                        }
                        shapes.append(
                            "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"TUD-Shape ${k + 1}\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
                                "<p:spPr>$off<a:custGeom><a:avLst/><a:gdLst/><a:ahLst/><a:cxnLst/><a:rect l=\"0\" t=\"0\" r=\"r\" b=\"b\"/><a:pathLst>$paths</a:pathLst></a:custGeom>" +
                                "<a:solidFill><a:srgbClr val=\"${hex(el.fill) ?: hex(el.color) ?: "00008C"}\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>",
                        )
                    }
                    "tabelle" -> shapes.append(tableFrame(id, "Tabelle ${k + 1}", emu(el.x), emu(el.y), emu(el.w.coerceAtLeast(0.2)), emu(el.h.coerceAtLeast(0.2)), el.rows, el.header, el.size, el.color, el.fill, el.bold))
                    "bild" -> {
                        val bytes = DocMarkup.imageBytes(el.src, base) ?: return@forEachIndexed
                        val type = DocMarkup.imageType(bytes) ?: "png"
                        val media = "ppt/media/frei${n}_$k.$type"
                        files[media] = bytes
                        imageTypes += type
                        val rid = "rIdFrei$k"
                        rels.append("<Relationship Id=\"$rid\" Type=\"$IMAGE_REL\" Target=\"../media/frei${n}_$k.$type\"/>")
                        shapes.append(
                            "<p:pic><p:nvPicPr><p:cNvPr id=\"$id\" name=\"Bild ${k + 1}\" descr=\"${esc(el.text)}\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                                "<p:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                                "<p:spPr>$off<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>",
                        )
                    }
                    else -> {
                        val shape = el.kind == "form"
                        val geom = if (!shape) "rect" else when (el.shape) { "ellipse" -> "ellipse"; "abgerundet" -> "roundRect"; "pfeil" -> "rightArrow"; "linie" -> "line"; else -> "rect" }
                        val accent = master?.accent?.let(::hex)
                        val fillHex = hex(el.fill) ?: if (shape && geom != "line") accent ?: "00008C" else null
                        val fill2 = hex(el.fill2)
                        val fill = when {
                            fillHex == null || geom == "line" -> "<a:noFill/>"
                            // Verlauf (AP-50): top to bottom like on the slide.
                            fill2 != null -> "<a:gradFill rotWithShape=\"1\"><a:gsLst><a:gs pos=\"0\"><a:srgbClr val=\"$fillHex\"/></a:gs><a:gs pos=\"100000\"><a:srgbClr val=\"$fill2\"/></a:gs></a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill>"
                            else -> "<a:solidFill><a:srgbClr val=\"$fillHex\"/></a:solidFill>"
                        }
                        val line = if (geom == "line") "<a:ln w=\"28575\"><a:solidFill><a:srgbClr val=\"${hex(el.color) ?: accent ?: "00008C"}\"/></a:solidFill></a:ln>" else "<a:ln><a:noFill/></a:ln>"
                        val color = hex(el.color) ?: if (shape) "FFFFFF" else "00008C"
                        val algn = when (el.align) { "mitte" -> "ctr"; "rechts" -> "r"; else -> "l" }
                        // One run per part of equal format (formatting inside the text box, AP-50).
                        val paras = io.github.thepytales.llmhub.creative.FieldRich.lines(el.text, el.rich).joinToString("") { line ->
                            "<a:p><a:pPr algn=\"$algn\"/>" + line.ifEmpty { listOf("" to CharFormat.PLAIN) }.joinToString("") { (t, f) -> "<a:r>${runProps(el.size, el.bold || f.bold, f, f.color ?: color)}<a:t>${esc(t)}</a:t></a:r>" } + "</a:p>"
                        }
                        shapes.append(
                            "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"${if (shape) "Form" else "Textfeld"} ${k + 1}\"/><p:cNvSpPr${if (!shape) " txBox=\"1\"" else ""}/><p:nvPr/></p:nvSpPr>" +
                                "<p:spPr>$off<a:prstGeom prst=\"$geom\"><a:avLst/></a:prstGeom>$fill$line</p:spPr>" +
                                "<p:txBody><a:bodyPr wrap=\"square\" lIns=\"72000\" tIns=\"36000\" rIns=\"72000\" bIns=\"36000\" anchor=\"${if (shape) "ctr" else "t"}\"><a:normAutofit/></a:bodyPr><a:lstStyle/>$paras</p:txBody></p:sp>",
                        )
                    }
                }
            }
            if (shapes.isNotEmpty()) xml = xml.replace("</p:spTree>", "$shapes</p:spTree>")
            files[path] = xml.toByteArray()
            if (rels.isNotEmpty()) {
                val relPath = "ppt/slides/_rels/slide$n.xml.rels"
                val old = files[relPath]?.toString(Charsets.UTF_8)
                    ?: "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>"
                files[relPath] = old.replace("</Relationships>", "$rels</Relationships>").toByteArray()
            }
        }
        if (imageTypes.isNotEmpty()) {
            var types = files["[Content_Types].xml"]?.toString(Charsets.UTF_8).orEmpty()
            imageTypes.forEach { t ->
                if ("Extension=\"$t\"" !in types) {
                    val mime = when (t) { "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "bmp" -> "image/bmp"; else -> "image/png" }
                    types = types.replace("<Default Extension=\"xml\"", "<Default Extension=\"$t\" ContentType=\"$mime\"/><Default Extension=\"xml\"")
                }
            }
            files["[Content_Types].xml"] = types.toByteArray()
        }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            files.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        file.writeBytes(bos.toByteArray())
    }

    /** `<a:rPr>` of a run: size in pt, bold, italic/underline/strike of [f], colour as RRGGBB. */
    private fun runProps(size: Int, bold: Boolean, f: CharFormat, color: String): String =
        "<a:rPr lang=\"de-DE\" sz=\"${size.coerceIn(6, 120) * 100}\"${if (bold) " b=\"1\"" else ""}${if (f.italic) " i=\"1\"" else ""}" +
            "${if (f.underline) " u=\"sng\"" else ""}${if (f.strike) " strike=\"sngStrike\"" else ""} dirty=\"0\">" +
            "<a:solidFill><a:srgbClr val=\"${hex(color) ?: "000000"}\"/></a:solidFill><a:latin typeface=\"Noto Sans\"/></a:rPr>"

    /**
     * A table of the shared canvas (AP-50) as a real PowerPoint table (`p:graphicFrame` with `a:tbl`) at [x], [y] in EMU,
     * [cx] by [cy] big: equal columns and rows, the head row in TUD blue with white bold text, bands in light blue, thin
     * grey lines, as [io.github.thepytales.llmhub.creative.CanvasTable] draws it. Cells keep their inline formatting.
     */
    fun tableFrame(
        id: Int, name: String, x: Long, y: Long, cx: Long, cy: Long, rows: List<List<String>>, header: Boolean,
        sizePt: Int, color: String, fill: String, bold: Boolean,
    ): String {
        val t = io.github.thepytales.llmhub.creative.CanvasTable
        val cells = t.normalized(rows)
        val cols = t.cols(cells)
        val colW = cx / cols
        val rowH = cy / cells.size
        val probe = io.github.thepytales.llmhub.data.DesignElement(kind = "table", color = color, fill = fill, header = header, bold = bold)
        val line = "<a:solidFill><a:srgbClr val=\"${hex(t.LINE)}\"/></a:solidFill>"
        val border = listOf("lnL", "lnR", "lnT", "lnB").joinToString("") { "<a:$it w=\"6350\">$line</a:$it>" }
        val columns = (0 until cols).joinToString("") { "<a:gridCol w=\"$colW\"/>" }
        val body = cells.mapIndexed { r, row ->
            val bg = hex(t.fill(probe, r))?.let { "<a:solidFill><a:srgbClr val=\"$it\"/></a:solidFill>" } ?: "<a:noFill/>"
            val text = hex(t.textColor(probe, r)) ?: "000000"
            "<a:tr h=\"$rowH\">" + row.joinToString("") { cell ->
                val runs = io.github.thepytales.llmhub.creative.FieldRich.lines(cell, rich = true).joinToString("") { l ->
                    "<a:p>" + l.ifEmpty { listOf("" to CharFormat.PLAIN) }.joinToString("") { (s, f) -> "<a:r>${runProps(sizePt, t.bold(probe, r) || f.bold, f, f.color ?: text)}<a:t>${esc(s)}</a:t></a:r>" } + "</a:p>"
                }
                "<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>$runs</a:txBody><a:tcPr marL=\"43200\" marR=\"43200\" marT=\"21600\" marB=\"21600\" anchor=\"ctr\">$border$bg</a:tcPr></a:tc>"
            } + "</a:tr>"
        }.joinToString("")
        return "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"$id\" name=\"${esc(name)}\"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>" +
            "<p:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></p:xfrm>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl><a:tblPr${if (header) " firstRow=\"1\"" else ""} bandRow=\"1\"/>" +
            "<a:tblGrid>$columns</a:tblGrid>$body</a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
    }
}
