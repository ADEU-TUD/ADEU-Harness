package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.tr
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdfwriter.ContentStreamWriter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.common.PDNumberTreeNode
import org.apache.pdfbox.pdmodel.common.PDStream
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tags erzeugen für PDF/UA (AP-60), Windows: an untagged PDF gets a structure – headings (by font size), paragraphs,
 * lists (bullets and numbers), figures with their alternative text, and everything else (lines, page numbers) as
 * artifacts. Every piece of text and every picture in the content streams is wrapped in marked content with an MCID,
 * the structure tree points at it, the ParentTree back. Text and look of the pages stay as they are. Tables are not
 * recognised (they come out as paragraphs).
 */
object PdfAutoTag {
    private enum class Kind { TEXT, IMAGE }

    /** A piece of a page: a BT…ET block or an image, with what decides its role. */
    private class Unit(val kind: Kind, val start: Int, val end: Int, val size: Float, val bold: Boolean, val text: String, val image: String? = null) {
        var role: String = "P"
    }

    private val PATH = setOf("m", "l", "c", "v", "y", "h", "re")
    private val PAINT = setOf("S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n")
    private val BULLET = Regex("""^\s*([•▪◦‣∙·●○■□\-–*]|\d{1,2}[.)]|[a-zA-Z][.)])\s+\S""")
    private val PAGE_NUMBER = Regex("""^(Seite\s+|Page\s+|-\s*)?\d{1,4}(\s*(/|von|of)\s*\d{1,4})?(\s*-)?$""", RegexOption.IGNORE_CASE)

    private fun decode(font: PDFont?, s: COSString): String {
        if (font == null) return ""
        val sb = StringBuilder()
        runCatching {
            val input = ByteArrayInputStream(s.bytes)
            while (input.available() > 0) {
                val code = font.readCode(input)
                sb.append(font.toUnicode(code) ?: "")
            }
        }
        return sb.toString()
    }

    /** The pieces of [page] in the order of its content stream. */
    private fun units(page: PDPage, tokens: List<Any>): List<Unit> {
        val res = page.resources
        val out = mutableListOf<Unit>()
        val operands = mutableListOf<COSBase>()
        var inText = -1
        var font: PDFont? = null
        var fontSize = 0f
        var scale = 1f
        var maxSize = 0f
        var bold = false
        val text = StringBuilder()
        for ((i, t) in tokens.withIndex()) {
            if (t is COSBase) { operands += t; continue }
            val op = (t as? Operator)?.name ?: continue
            when (op) {
                "BT" -> { inText = i; maxSize = 0f; bold = false; text.clear(); scale = 1f }
                "ET" -> if (inText >= 0) {
                    out += Unit(Kind.TEXT, inText, i, maxSize, bold, text.toString().trim())
                    inText = -1
                }
                "Tf" -> {
                    val name = operands.getOrNull(0) as? COSName
                    fontSize = (operands.getOrNull(1) as? COSNumber)?.floatValue() ?: fontSize
                    font = name?.let { runCatching { res?.getFont(it) }.getOrNull() }
                    if (font?.name?.contains(Regex("Bold|Black|Heavy|Semibold", RegexOption.IGNORE_CASE)) == true) bold = true
                }
                "Tm" -> {
                    val a = (operands.getOrNull(0) as? COSNumber)?.floatValue() ?: 1f
                    val d = (operands.getOrNull(3) as? COSNumber)?.floatValue() ?: 1f
                    scale = maxOf(abs(a), abs(d)).takeIf { it > 0f } ?: 1f
                }
                "Tj", "'", "\"" -> {
                    (operands.lastOrNull() as? COSString)?.let { text.append(decode(font, it)) }
                    maxSize = maxOf(maxSize, fontSize * scale)
                }
                "TJ" -> {
                    (operands.lastOrNull() as? COSArray)?.forEach { e ->
                        when (e) {
                            is COSString -> text.append(decode(font, e))
                            is COSNumber -> if (e.floatValue() < -200) text.append(' ')
                        }
                    }
                    maxSize = maxOf(maxSize, fontSize * scale)
                }
                "Do" -> if (inText < 0) {
                    val name = operands.lastOrNull() as? COSName
                    val x = name?.let { runCatching { res?.getXObject(it) }.getOrNull() }
                    if (x is PDImageXObject) out += Unit(Kind.IMAGE, i, i, 0f, false, "", name.name)
                }
            }
            operands.clear()
        }
        return out
    }

    /**
     * Tags [file] into [out]. [alt]: alternative texts of the pictures, key „Seite:Name“ (page 0-based and the name in the
     * page's resources, as [PdfStage3.images] lists them). Throws if the PDF has tags already.
     */
    fun tag(file: File, out: File, title: String, language: String = "de-DE", alt: Map<String, String> = emptyMap()): PdfTagReport {
        PdfTools.load(file).use { doc ->
            val catalog = doc.documentCatalog
            if (catalog.structureTreeRoot?.kids?.isNotEmpty() == true) throw IllegalStateException(tr("Das PDF hat schon Tags."))
            // Pass 1: the pieces of every page, and the usual text size of the document.
            val parsed = doc.pages.map { page -> val tokens = runCatching { PDFStreamParser(page).parse() }.getOrDefault(emptyList<Any>()); tokens to units(page, tokens) }
            val sizes = parsed.flatMap { it.second }.filter { it.kind == Kind.TEXT && it.text.isNotBlank() && it.size > 0 }
            val body = sizes.groupBy { (it.size * 2).roundToInt() / 2f }.maxByOrNull { (_, l) -> l.sumOf { it.text.length } }?.key ?: 11f
            for ((_, units) in parsed) for (u in units) {
                u.role = when {
                    u.kind == Kind.IMAGE -> "Figure"
                    u.text.isBlank() -> "Artifact"
                    PAGE_NUMBER.matches(u.text) && u.text.length <= 14 -> "Artifact"
                    u.size >= body * 1.45f -> "H1"
                    u.size >= body * 1.15f -> "H2"
                    u.bold && u.size >= body * 1.02f && u.text.length < 90 && !u.text.endsWith('.') -> "H3"
                    BULLET.containsMatchIn(u.text) -> "LI"
                    else -> "P"
                }
            }
            // Pass 2: marked content in the streams, the structure tree, the ParentTree.
            val root = PDStructureTreeRoot()
            catalog.structureTreeRoot = root
            val document = PDStructureElement("Document", root)
            root.appendKid(document)
            val parentTree = mutableMapOf<Int, COSArray>()
            var list: PDStructureElement? = null
            var heads = 0; var paras = 0; var items = 0; var figures = 0; var artifacts = 0; var noAlt = 0
            for ((pi, pair) in parsed.withIndex()) {
                val (tokens, units) = pair
                val page = doc.getPage(pi)
                if (tokens.isEmpty()) continue
                val byStart = units.associateBy { it.start }
                val byEnd = units.associateBy { it.end }
                val mcids = COSArray()
                val result = ArrayList<Any>(tokens.size + units.size * 4)
                var artifactOpen = false
                fun bdc(role: String, mcid: Int) {
                    result += COSName.getPDFName(role)
                    result += COSDictionary().apply { setInt(COSName.MCID, mcid) }
                    result += Operator.getOperator("BDC")
                }
                fun artifactBmc() { result += COSName.ARTIFACT; result += Operator.getOperator("BMC") }
                fun emc() { result += Operator.getOperator("EMC") }
                fun element(role: String, mcid: Int): PDStructureElement {
                    val parent: PDStructureElement = if (role == "LI") {
                        list ?: PDStructureElement("L", document).also { document.appendKid(it); list = it }
                    } else { list = null; document }
                    val e = PDStructureElement(role, parent)
                    e.page = page
                    if (role == "LI") {
                        parent.appendKid(e)
                        val bodyEl = PDStructureElement("LBody", e).also { it.page = page }
                        e.appendKid(bodyEl)
                        bodyEl.appendKid(PDMarkedContent.create(COSName.getPDFName("LBody"), COSDictionary().apply { setInt(COSName.MCID, mcid) }))
                        mcids.add(bodyEl)
                    } else {
                        parent.appendKid(e)
                        e.appendKid(PDMarkedContent.create(COSName.getPDFName(role), COSDictionary().apply { setInt(COSName.MCID, mcid) }))
                        mcids.add(e)
                    }
                    return e
                }
                var mcid = 0
                var inText = false
                val operands = mutableListOf<Any>()
                fun emit(t: Any) { result.addAll(operands); operands.clear(); result += t }
                fun closeArtifact() { if (artifactOpen) { emc(); artifactOpen = false } }
                for ((k, t) in tokens.withIndex()) {
                    val op = (t as? Operator)?.name
                    if (op == null) { operands += t; continue }
                    val u = byStart[k]
                    when {
                        u != null && u.kind == Kind.TEXT -> {
                            closeArtifact()
                            inText = true
                            if (u.role == "Artifact") { artifacts++; artifactBmc() }
                            else {
                                element(u.role, mcid)
                                if (u.role.startsWith("H")) heads++ else if (u.role == "LI") items++ else paras++
                                bdc(if (u.role == "LI") "LBody" else u.role, mcid++)
                            }
                            emit(t)
                        }
                        op == "ET" -> { emit(t); if (inText && byEnd[k]?.kind == Kind.TEXT) emc(); inText = false }
                        u != null && u.kind == Kind.IMAGE -> {
                            closeArtifact()
                            val e = element("Figure", mcid)
                            figures++
                            val a = alt["$pi:${u.image}"]?.trim().orEmpty()
                            if (a.isNotBlank()) e.alternateDescription = a else noAlt++
                            bdc("Figure", mcid++)
                            emit(t)
                            emc()
                        }
                        !inText && (op == "Do" || op == "sh" || op == "BI") -> {
                            // A form, a shading or an inline image: decoration.
                            closeArtifact()
                            artifactBmc(); emit(t); emc()
                        }
                        !inText && op in PATH -> { if (!artifactOpen) { artifactBmc(); artifactOpen = true }; emit(t) }
                        op in PAINT -> { emit(t); closeArtifact() }
                        else -> emit(t)
                    }
                }
                result.addAll(operands)
                closeArtifact()
                val stream = PDStream(doc)
                stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(result) }
                page.setContents(stream)
                page.structParents = pi
                page.cosObject.setName(COSName.getPDFName("Tabs"), "S")
                parentTree[pi] = mcids
            }
            val tree = PDNumberTreeNode(COSBase::class.java)
            tree.numbers = parentTree.mapValues { it.value as org.apache.pdfbox.pdmodel.common.COSObjectable }
            root.parentTree = tree
            root.parentTreeNextKey = doc.numberOfPages
            catalog.markInfo = PDMarkInfo().apply { isMarked = true }
            catalog.language = language
            doc.documentInformation.title = doc.documentInformation.title?.takeIf { it.isNotBlank() } ?: title
            val prefs = catalog.viewerPreferences ?: PDViewerPreferences(COSDictionary())
            prefs.setDisplayDocTitle(true)
            catalog.viewerPreferences = prefs
            catalog.metadata = PDMetadata(doc, ByteArrayInputStream(xmp(doc.documentInformation.title ?: title)))
            doc.save(out)
            return PdfTagReport(heads, paras, items, figures, 0, artifacts, noAlt)
        }
    }

    /** XMP with the title and the PDF/UA identification (part 1). */
    private fun xmp(title: String): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return """<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:pdfuaid="http://www.aiim.org/pdfua/ns/id/">
<dc:title><rdf:Alt><rdf:li xml:lang="x-default">${esc(title)}</rdf:li></rdf:Alt></dc:title>
<pdfuaid:part>1</pdfuaid:part>
</rdf:Description></rdf:RDF></x:xmpmeta>
<?xpacket end="w"?>""".toByteArray(Charsets.UTF_8)
    }
}
