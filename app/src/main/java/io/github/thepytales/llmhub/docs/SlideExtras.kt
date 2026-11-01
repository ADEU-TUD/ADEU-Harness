package io.github.thepytales.llmhub.docs

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
                val off = "<a:xfrm><a:off x=\"${emu(el.x)}\" y=\"${emu(el.y)}\"/><a:ext cx=\"${emu(el.w.coerceAtLeast(0.2))}\" cy=\"${emu(el.h.coerceAtLeast(0.2))}\"/></a:xfrm>"
                when (el.kind) {
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
                        val fill = if (fillHex != null && geom != "line") "<a:solidFill><a:srgbClr val=\"$fillHex\"/></a:solidFill>" else "<a:noFill/>"
                        val line = if (geom == "line") "<a:ln w=\"28575\"><a:solidFill><a:srgbClr val=\"${hex(el.color) ?: accent ?: "00008C"}\"/></a:solidFill></a:ln>" else "<a:ln><a:noFill/></a:ln>"
                        val color = hex(el.color) ?: if (shape) "FFFFFF" else "00008C"
                        val algn = when (el.align) { "mitte" -> "ctr"; "rechts" -> "r"; else -> "l" }
                        val paras = el.text.lines().ifEmpty { listOf("") }.joinToString("") { l ->
                            "<a:p><a:pPr algn=\"$algn\"/><a:r><a:rPr lang=\"de-DE\" sz=\"${el.size.coerceIn(6, 120) * 100}\"${if (el.bold) " b=\"1\"" else ""} dirty=\"0\">" +
                                "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:latin typeface=\"Noto Sans\"/></a:rPr><a:t>${esc(l)}</a:t></a:r></a:p>"
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
}
