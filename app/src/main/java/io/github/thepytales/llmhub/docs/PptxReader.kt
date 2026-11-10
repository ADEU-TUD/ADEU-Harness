package io.github.thepytales.llmhub.docs

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * PowerPoint-Treue (AP-51): reads a .pptx as slides of the editor.
 *
 * Lora's own decks ([PptxWriter]) come back as they were written: the layout from the TUD shapes, the freely placed
 * elements of [SlideExtras] (shape ids from 1000) as elements again, charts from their chart part.
 *
 * Foreign decks – also the TUD-Präsentationsvorlage – are read the way PowerPoint shows them: a placeholder inherits
 * position and text style from the placeholder of its layout and that one from the slide master; colours of the design
 * (schemeClr, also with lumMod/lumOff, tint and shade) come from the theme of the master through its colour map; groups
 * place their members through their own transform (chOff/chExt). The placeholders fill the layout of the editor's
 * slide (title, text, columns, picture, table, chart – the layout of the master decides between them); everything else
 * – shapes with fill and text, lines, pictures, members of groups – becomes a freely placed element at its real place,
 * scaled onto the 33.87 × 19.05 cm slide of the editor. Charts become a ```chart block with the values Office cached
 * ([OfficeCharts.read]), SmartArt its texts as points.
 *
 * What the editor does not show (animations beyond the build, SmartArt, sections, custom XML …) stays in the file:
 * [PptxMerge] writes the changes of the editor into the original package, slide by slide.
 */
object PptxReader {
    private const val EMU_PER_CM = 360000.0

    /** The slides of [bytes] as the editor's Markdown; pictures go into [media] by their Markdown path. */
    fun read(bytes: ByteArray, media: MutableMap<String, ByteArray> = LinkedHashMap()): String = SlideDeck.markdown(slides(bytes, media))

    fun slides(bytes: ByteArray, media: MutableMap<String, ByteArray> = LinkedHashMap()): List<SlideDeck.Slide> {
        val pkg = Pkg(entries(bytes))
        val names = pkg.slideNames()
        val slides = if (isOwn(pkg.files)) own(pkg, names, media) else foreign(pkg, names, media)
        return slides.mapIndexed { index, s -> s.copy(transition = transition(pkg.xml(names[index])), build = pkg.xml(names[index])?.all("bldP")?.any { it.attr("build") == "p" } == true) }
            .ifEmpty { listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE)) }
    }

    /** Whether the deck was written by Lora (then the TUD shapes of [PptxWriter] are read back as layouts). */
    internal fun isOwn(files: Map<String, ByteArray>): Boolean =
        files["docProps/core.xml"]?.toString(Charsets.UTF_8)?.contains("<dc:creator>Lora</dc:creator>") == true ||
            files["docProps/app.xml"]?.toString(Charsets.UTF_8)?.contains("<Application>Lora</Application>") == true

    /** Übergang of a slide (AP-51): push-like ones as „Schieben“, every other one as „Überblenden“. */
    private fun transition(root: Element?): String {
        val kinds = root?.all("transition")?.firstOrNull()?.children()?.map { it.localName }.orEmpty()
        return when {
            kinds.any { it == "push" || it == "cover" || it == "pull" || it == "wipe" } -> "push"
            kinds.isNotEmpty() -> "fade"
            else -> ""
        }
    }

    // ── The package ──────────────────────────────────────────────────────────────────────────────────────

    internal fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val media = e.name.contains("/media/") && e.size <= 30L * 1024 * 1024
                if (!e.isDirectory && (e.name.endsWith(".xml") || e.name.endsWith(".rels") || media)) out[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        return out
    }

    /** Parts of a package with their parsed XML and relationships (parsed once). */
    internal class Pkg(val files: Map<String, ByteArray>) {
        private val parsed = HashMap<String, Element?>()
        private val relsOf = HashMap<String, Map<String, String>>()

        fun xml(path: String?): Element? = path?.let { p -> parsed.getOrPut(p) { xml(files[p]) } }

        /** Relationship id → target (resolved) of part [path]. */
        fun rels(path: String): Map<String, String> = relsOf.getOrPut(path) {
            val dir = path.substringBeforeLast('/', "") + "/"
            val relsPath = dir + "_rels/" + path.substringAfterLast('/') + ".rels"
            xml(relsPath)?.children()?.filter { it.attr("TargetMode") != "External" }?.associate { r ->
                val t = r.attr("Target")
                r.attr("Id") to normalize(if (t.startsWith("/")) t.removePrefix("/") else dir + t)
            }.orEmpty()
        }

        /** The target of the first relationship of [path] whose type ends with [type]. */
        fun related(path: String, type: String): String? {
            val dir = path.substringBeforeLast('/', "") + "/"
            val relsPath = dir + "_rels/" + path.substringAfterLast('/') + ".rels"
            val id = xml(relsPath)?.children()?.firstOrNull { it.attr("Type").endsWith("/$type") }?.attr("Id") ?: return null
            return rels(path)[id]
        }

        /** Slides in the order of the presentation (sldIdLst), otherwise by number. */
        fun slideNames(): List<String> {
            val pres = "ppt/presentation.xml"
            val presRels = rels(pres)
            val ordered = xml(pres)?.all("sldId")?.mapNotNull { sid -> presRels[sid.relId("id")]?.takeIf { it in files } }.orEmpty()
            return ordered.ifEmpty {
                files.keys.mapNotNull { k -> Regex("^ppt/slides/slide(\\d+)\\.xml$").find(k)?.groupValues?.get(1)?.toInt()?.let { it to k } }.sortedBy { it.first }.map { it.second }
            }
        }

        fun notes(slide: String): String {
            val notes = xml(related(slide, "notesSlide")) ?: return ""
            return notes.all("sp").filter { sp -> sp.all("ph").firstOrNull()?.attr("type") == "body" }
                .flatMap { sp -> sp.all("p").map { p -> p.all("t").joinToString("") { it.textContent } } }
                .filter { it.isNotBlank() }.joinToString(" ").trim()
        }
    }

    // ── Lora's own decks ─────────────────────────────────────────────────────────────────────────────────

    /** Shapes of [SlideExtras] (freely placed elements) have ids from 1000 on. */
    private fun Element.isExtra(): Boolean = (cNvPr()?.attr("id")?.toIntOrNull() ?: 0) >= 1000

    /**
     * Lora's own export: titles, bullet points, tables, pictures and notes; the TUD shapes decide the slide type
     * (title, section, two columns, comparison, picture), [SlideExtras] shapes become elements again.
     */
    private fun own(pkg: Pkg, slideNames: List<String>, media: MutableMap<String, ByteArray>): List<SlideDeck.Slide> =
        slideNames.mapIndexed { index, key -> ownSlide(pkg, key, index, media) }

    /** One slide of Lora's export (also a slide [PptxMerge] added to a foreign deck: its cSld is named „Lora“). */
    private fun ownSlide(pkg: Pkg, key: String, index: Int, media: MutableMap<String, ByteArray>): SlideDeck.Slide {
        val files = pkg.files
        val slideHeight = pkg.xml("ppt/presentation.xml")?.all("sldSz")?.firstOrNull()?.attr("cy")?.toLongOrNull() ?: 6858000L
        val pal = Palette(emptyMap(), emptyMap())
        val geo = Geometry(12192000L, slideHeight)
        val root = pkg.xml(key) ?: return SlideDeck.Slide("Folie ${index + 1}")
        val slideRels = pkg.rels(key)
        val layoutType = pkg.xml(pkg.related(key, "slideLayout"))?.attr("type").orEmpty()
        var title = ""
        var subtitle = ""
        /** The title PptxWriter puts above slides without a visible one (for screen readers). */
        var hidden = ""
        val columns = mutableListOf<Pair<Long, List<String>>>()
        val heads = mutableListOf<Pair<Long, String>>()
        // The largest picture of the slide (logos of the design are left out).
        val picture = root.all("pic").filter { !it.isExtra() && !it.cNvPr()?.attr("name").orEmpty().startsWith("Logo") }.mapNotNull { pic ->
            val ext = pic.all("ext").firstOrNull { it.parentNode?.localName == "xfrm" }
            val area = (ext?.attr("cx")?.toLongOrNull() ?: 0L) * (ext?.attr("cy")?.toLongOrNull() ?: 0L)
            area to pic
        }.sortedByDescending { it.first }.firstNotNullOfOrNull { (_, pic) ->
            val embed = pic.all("blip").firstOrNull()?.relId("embed")
            val path = keepImage(files[slideRels[embed]], media) ?: return@firstNotNullOfOrNull null
            val x = pic.all("off").firstOrNull()?.attr("x")?.toLongOrNull() ?: 0L
            Triple(path, pic.cNvPr()?.attr("descr").orEmpty(), x)
        }
        root.all("sp").filter { !it.isExtra() }.forEach { sp ->
            val ph = sp.all("ph").firstOrNull()
            val type = ph?.attr("type").orEmpty()
            val x = sp.all("off").firstOrNull()?.attr("x")?.toLongOrNull() ?: Long.MAX_VALUE
            val y = sp.all("off").firstOrNull()?.attr("y")?.toLongOrNull() ?: 0L
            val shapeName = sp.cNvPr()?.attr("name").orEmpty()
            val paras = sp.all("p").map { p ->
                val lvl = p.child("pPr")?.attr("lvl")?.toIntOrNull() ?: 0
                lvl to p.all("t").joinToString("") { it.textContent }.trim()
            }.filter { it.second.isNotEmpty() }
            when (type) {
                "title", "ctrTitle" -> if (y < 0) hidden = paras.joinToString(" ") { it.second } else title = paras.joinToString(" ") { it.second }
                "subTitle" -> subtitle = paras.joinToString(" ") { it.second }
                "sldNum", "dt", "ftr", "sldImg" -> {}
                else -> if (paras.isNotEmpty()) {
                    val text = paras.joinToString(" ") { it.second }
                    when {
                        // Footer lines and slide numbers of the design, labels of drawn charts, the picture's caption.
                        type.isEmpty() && y > slideHeight * 9 / 10 -> {}
                        shapeName.startsWith("Beschriftung") || picture != null && text == picture.second -> {}
                        // Column heads of a comparison (this app's export).
                        shapeName.startsWith("Spaltenkopf") && heads.size < 2 -> heads += x to text
                        // Comparison layouts: small text placeholders above the two contents are the column heads.
                        layoutType == "twoTxTwoObj" && paras.size == 1 && type == "body" && (ph?.attr("sz") == "quarter" || paras[0].second.length < 60) && heads.size < 2 -> heads += x to paras[0].second
                        else -> columns += x to paras.map { (lvl, t) -> "  ".repeat(lvl) + "- " + t }
                    }
                }
            }
        }
        val tables = root.all("tbl").map(::tableOf).filter { it.isNotEmpty() }
        val chart = root.all("graphicFrame").firstNotNullOfOrNull { chartOf(pkg, key, it) }
        val elements = root.child("cSld")?.child("spTree")?.children().orEmpty().filter { it.isExtra() }.mapNotNull { el ->
            elementOf(Item(el, box(el.xfrm()), ""), Ctx(pkg, key, null, null, pal, geo, null), media)
        }
        val notes = pkg.notes(key)
        val sorted = columns.sortedBy { it.first }
        val body = (sorted.flatMap { it.second } + tables.map { "\n$it\n" }).joinToString("\n").trim()
        // The first slide is a title slide – unless it has content and no subtitle (a one-slide handout, AP-90): then its
        // bullets would be lost, so it is read like any other slide.
        if (index == 0 && (body.isBlank() || subtitle.isNotBlank() || title.isBlank()))
            return SlideDeck.Slide(title.ifBlank { body.lines().firstOrNull()?.removePrefix("- ").orEmpty() }, subtitle, notes = notes, layout = SlideDeck.Layout.TITLE, elements = elements)
        val heading = title.ifBlank { hidden.ifBlank { "Folie ${index + 1}" } }
        return when {
            chart != null -> SlideDeck.Slide(heading, listOf(body, chart).filter { it.isNotBlank() }.joinToString("\n\n"), notes = notes, layout = SlideDeck.Layout.CHART, elements = elements)
            picture != null -> {
                val left = picture.third < 12192000L / 3
                if (body.isBlank()) SlideDeck.Slide(heading, "", notes = notes, layout = SlideDeck.Layout.IMAGE, image = picture.first, imageAlt = picture.second, elements = elements)
                else SlideDeck.Slide(heading, body, notes = notes, layout = SlideDeck.Layout.IMAGE_TEXT, image = picture.first, imageAlt = picture.second, imageLeft = left, elements = elements)
            }
            (layoutType == "twoObj" || layoutType == "twoTxTwoObj" || sorted.size == 2) && sorted.size >= 2 && tables.isEmpty() -> {
                val sortedHeads = heads.sortedBy { it.first }.map { it.second }
                val leftText = SlideDeck.withColumnHead(sortedHeads.getOrElse(0) { "" }, sorted[0].second.joinToString("\n"))
                val rightText = SlideDeck.withColumnHead(sortedHeads.getOrElse(1) { "" }, sorted.drop(1).flatMap { it.second }.joinToString("\n"))
                SlideDeck.Slide(heading, leftText, rightText, notes, if (heads.isNotEmpty()) SlideDeck.Layout.COMPARISON else SlideDeck.Layout.TWO_COLUMNS, elements = elements)
            }
            tables.isNotEmpty() && sorted.isEmpty() -> SlideDeck.Slide(heading, tables.joinToString("\n\n"), notes = notes, layout = SlideDeck.Layout.TABLE, elements = elements)
            elements.isNotEmpty() && body.isEmpty() -> SlideDeck.Slide(title, "", notes = notes, layout = SlideDeck.Layout.FREE, elements = elements)
            layoutType == "secHead" || body.isEmpty() -> SlideDeck.Slide(heading, sorted.flatMap { it.second }.joinToString(" ") { it.trim().removePrefix("- ") }.take(140), notes = notes, layout = SlideDeck.Layout.SECTION, elements = elements)
            else -> SlideDeck.Slide(heading, body, notes = notes, layout = SlideDeck.Layout.CONTENT, elements = elements)
        }
    }

    // ── Foreign decks ────────────────────────────────────────────────────────────────────────────────────

    /** Slide size and how it is scaled onto the editor's 33.87 × 19.05 cm slide (centred, aspect kept). */
    internal class Geometry(val cx: Long, val cy: Long) {
        private val wCm = cx / EMU_PER_CM
        private val hCm = cy / EMU_PER_CM
        val scale = minOf(TudSlide.W / wCm, TudSlide.H / hCm).takeIf { it.isFinite() && it > 0 } ?: 1.0
        private val dx = (TudSlide.W - wCm * scale) / 2
        private val dy = (TudSlide.H - hCm * scale) / 2
        fun x(emu: Double) = emu / EMU_PER_CM * scale + dx
        fun y(emu: Double) = emu / EMU_PER_CM * scale + dy
        fun len(emu: Double) = emu / EMU_PER_CM * scale
    }

    /** Position of a shape in EMU of the slide (after the transforms of its groups); [rot] in degrees. */
    internal data class Box(val x: Double, val y: Double, val w: Double, val h: Double, val rot: Double = 0.0)

    /** A shape of the slide with its place and the group it belongs to ("" = none). */
    internal class Item(val el: Element, val box: Box?, val group: String)

    /** What a shape of a slide inherits from: its layout and master, the colours, the default text styles. */
    internal class Ctx(
        val pkg: Pkg, val slidePath: String, val layout: Element?, val master: Element?, val pal: Palette, val geo: Geometry, val defaults: Element?,
    ) {
        private fun phs(root: Element?) = root?.children("cSld")?.flatMap { it.children("spTree") }?.flatMap { tree -> tree.all("sp") }?.filter { it.ph() != null }.orEmpty()
        val layoutPhs by lazy { phs(layout) }
        val masterPhs by lazy { phs(master) }

        /** The placeholder of the layout and of the master [sp] inherits from. */
        fun inherited(sp: Element): List<Element> {
            val ph = sp.ph() ?: return emptyList()
            val type = ph.attr("type").ifEmpty { "obj" }
            val idx = ph.attr("idx")
            val onLayout = (if (idx.isNotEmpty()) layoutPhs.firstOrNull { it.ph()?.attr("idx") == idx } else null)
                ?: layoutPhs.firstOrNull { it.ph()?.attr("type").orEmpty().ifEmpty { "obj" } == type }
                ?: layoutPhs.firstOrNull { kindOf(it.ph()?.attr("type").orEmpty()) == kindOf(type) }
            val masterType = kindOf(onLayout?.ph()?.attr("type")?.ifEmpty { "obj" } ?: type)
            val onMaster = masterPhs.firstOrNull { kindOf(it.ph()?.attr("type").orEmpty().ifEmpty { "obj" }) == masterType }
            return listOfNotNull(onLayout, onMaster)
        }

        /** The text styles [sp] uses, nearest first: its own list style, the layout's and master's, the master's text styles. */
        fun styles(sp: Element): List<Element> {
            val own = sp.child("txBody")?.child("lstStyle")
            val ph = sp.ph()
            val chain = inherited(sp).mapNotNull { it.child("txBody")?.child("lstStyle") }
            val txStyles = master?.child("txStyles")
            val base = when {
                ph == null -> listOfNotNull(defaults, txStyles?.child("otherStyle"))
                kindOf(ph.attr("type").ifEmpty { "obj" }) == "title" -> listOfNotNull(txStyles?.child("titleStyle"))
                kindOf(ph.attr("type").ifEmpty { "obj" }) == "body" -> listOfNotNull(txStyles?.child("bodyStyle"))
                else -> listOfNotNull(txStyles?.child("otherStyle"))
            }
            return listOfNotNull(own) + chain + base
        }

        /** The transform of [sp]: its own, else the one of the placeholder it inherits from. */
        fun xfrm(sp: Element): Element? = sp.xfrm() ?: inherited(sp).firstNotNullOfOrNull { it.xfrm() }
    }

    /** Placeholder kinds that inherit from each other (a slide's "obj" from the master's "body"). */
    private fun kindOf(type: String) = when (type) {
        "title", "ctrTitle" -> "title"
        "body", "subTitle", "obj", "", "tbl", "chart", "pic", "dgm", "clipArt", "media" -> "body"
        else -> type
    }

    private fun foreign(pkg: Pkg, slideNames: List<String>, media: MutableMap<String, ByteArray>): List<SlideDeck.Slide> {
        val pres = pkg.xml("ppt/presentation.xml")
        val size = pres?.all("sldSz")?.firstOrNull()
        val geo = Geometry(size?.attr("cx")?.toLongOrNull() ?: 12192000L, size?.attr("cy")?.toLongOrNull() ?: 6858000L)
        val defaults = pres?.child("defaultTextStyle")
        var footer = ""
        var masterOfDeck: SlideMaster? = null
        val slides = slideNames.mapIndexed { index, key ->
            val root = pkg.xml(key) ?: return@mapIndexed SlideDeck.Slide("Folie ${index + 1}")
            if (root.child("cSld")?.attr("name") == PptxMerge.OWN_SLIDE) return@mapIndexed ownSlide(pkg, key, index, media)
            val layoutPath = pkg.related(key, "slideLayout")
            val layout = pkg.xml(layoutPath)
            val masterPath = layoutPath?.let { pkg.related(it, "slideMaster") }
            val master = pkg.xml(masterPath)
            val pal = palette(pkg, masterPath, master, layout, root)
            val ctx = Ctx(pkg, key, layout, master, pal, geo, defaults)
            if (index == 0) masterOfDeck = deckMaster(pkg, masterPath, master, layout, pal)
            val s = foreignSlide(ctx, root, index, media)
            if (footer.isEmpty()) footer = s.second
            s.first
        }
        val m = masterOfDeck?.copy(footer = footer.take(120))
        return if (m != null && m != SlideMaster() && slides.isNotEmpty()) listOf(slides[0].copy(master = m)) + slides.drop(1) else slides
    }

    /** The Masterfolie of the editor from the deck's master: its background, accent and whether it is the TUD design. */
    private fun deckMaster(pkg: Pkg, masterPath: String?, master: Element?, layout: Element?, pal: Palette): SlideMaster {
        val bg = background(master, pal)?.takeIf { it != "FFFFFF" }
        val accent = pal.scheme("accent1")
        val theme = pkg.xml(masterPath?.let { pkg.related(it, "theme") })
        val names = listOfNotNull(theme?.attr("name"), master?.child("cSld")?.attr("name"), layout?.child("cSld")?.attr("name")).joinToString(" ")
        val tud = names.contains("TUD", true) || names.contains("TU Dresden", true) || accent == "00008C" || pal.scheme("dk2") == "00008C"
        return SlideMaster(background = bg?.let { "#$it" }.orEmpty(), accent = if (tud || accent == null) "" else "#$accent", logo = tud)
    }

    /** The solid background of a slide, layout or master (gradients: their first stop), or null. */
    private fun background(root: Element?, pal: Palette): String? {
        val bg = root?.child("cSld")?.child("bg") ?: return null
        bg.child("bgPr")?.let { pr ->
            pr.child("solidFill")?.let { return pal.color(it) }
            pr.child("gradFill")?.all("gs")?.firstOrNull()?.let { return pal.color(it) }
            return null
        }
        return bg.child("bgRef")?.let { pal.color(it) }
    }

    /** Colours of the master's theme with the colour map of master, layout and slide. */
    private fun palette(pkg: Pkg, masterPath: String?, master: Element?, layout: Element?, slide: Element?): Palette {
        val theme = pkg.xml(masterPath?.let { pkg.related(it, "theme") })
        val plain = Palette(emptyMap(), emptyMap())
        val scheme = theme?.all("clrScheme")?.firstOrNull()?.children()?.mapNotNull { c -> plain.color(c)?.let { c.localName to it } }?.toMap().orEmpty()
        val map = HashMap<String, String>()
        master?.child("clrMap")?.let { m -> (0 until m.attributes.length).map { m.attributes.item(it) }.forEach { a -> map[a.localName ?: a.nodeName] = a.nodeValue } }
        for (over in listOfNotNull(layout, slide)) {
            over.child("clrMapOvr")?.child("overrideClrMapping")?.let { m -> (0 until m.attributes.length).map { m.attributes.item(it) }.forEach { a -> map[a.localName ?: a.nodeName] = a.nodeValue } }
        }
        if (map.isEmpty()) map.putAll(mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2"))
        return Palette(scheme, map)
    }

    /** All shapes of a shape tree in drawing order, members of groups placed through the group's transform. */
    private fun items(tree: Element, ctx: Ctx): List<Item> {
        val out = mutableListOf<Item>()
        var groups = 0
        fun walk(parent: Element, place: (Box) -> Box, group: String) {
            for (e in parent.children()) when (e.localName) {
                "sp", "pic", "cxnSp" -> out += Item(e, box(if (e.localName == "cxnSp") e.xfrm() else ctx.xfrm(e))?.let(place), group)
                "graphicFrame" -> out += Item(e, box(e.child("xfrm"))?.let(place), group)
                "grpSp" -> {
                    val x = e.child("grpSpPr")?.child("xfrm")
                    val off = x?.child("off")
                    val ext = x?.child("ext")
                    val chOff = x?.child("chOff")
                    val chExt = x?.child("chExt")
                    fun d(el: Element?, a: String) = el?.attr(a)?.toDoubleOrNull() ?: 0.0
                    val sx = d(chExt, "cx").takeIf { it > 0 }?.let { d(ext, "cx") / it } ?: 1.0
                    val sy = d(chExt, "cy").takeIf { it > 0 }?.let { d(ext, "cy") / it } ?: 1.0
                    val rot = (x?.attr("rot")?.toDoubleOrNull() ?: 0.0) / 60000.0
                    val inner: (Box) -> Box = { b -> place(Box(d(off, "x") + (b.x - d(chOff, "x")) * sx, d(off, "y") + (b.y - d(chOff, "y")) * sy, b.w * sx, b.h * sy, b.rot + rot)) }
                    // The outermost group moves together in the editor.
                    walk(e, inner, group.ifEmpty { "g${++groups}" })
                }
                // Newer content with a fallback for older readers (equations, 3D models …): the fallback is standard.
                "AlternateContent" -> (e.child("Fallback") ?: e.child("Choice"))?.let { walk(it, place, group) }
                else -> {}
            }
        }
        walk(tree, { it }, "")
        return out
    }

    private fun box(xfrm: Element?): Box? {
        val off = xfrm?.child("off") ?: return null
        val ext = xfrm.child("ext")
        return Box(
            off.attr("x").toDoubleOrNull() ?: 0.0, off.attr("y").toDoubleOrNull() ?: 0.0,
            ext?.attr("cx")?.toDoubleOrNull() ?: 0.0, ext?.attr("cy")?.toDoubleOrNull() ?: 0.0,
            (xfrm.attr("rot").toDoubleOrNull() ?: 0.0) / 60000.0,
        )
    }

    /** Layout of the editor a layout of PowerPoint means by its name (custom layouts of templates are named, not typed). */
    private fun layoutByName(name: String): SlideDeck.Layout? {
        val n = name.lowercase()
        return when {
            Regex("abschnitt|kapitel|section|zwischentitel|trenn").containsMatchIn(n) -> SlideDeck.Layout.SECTION
            Regex("agenda|gliederung|inhaltsverzeichnis|overview|übersicht").containsMatchIn(n) -> SlideDeck.Layout.AGENDA
            Regex("abschluss|danke|kontakt|schluss|ende\\b|closing|thank").containsMatchIn(n) -> SlideDeck.Layout.END
            Regex("zitat|quote").containsMatchIn(n) -> SlideDeck.Layout.QUOTE
            Regex("vergleich|comparison").containsMatchIn(n) -> SlideDeck.Layout.COMPARISON
            Regex("zwei|two|2 inhalte|2 spalten").containsMatchIn(n) -> SlideDeck.Layout.TWO_COLUMNS
            else -> null
        }
    }

    /** A placeholder's paragraphs as Markdown lines: points ("- ", "1. ") or plain lines, by the bullets of its styles. */
    private fun paragraphs(sp: Element, ctx: Ctx): List<String> {
        val styles = ctx.styles(sp)
        val out = mutableListOf<String>()
        var number = 0
        sp.child("txBody")?.children("p")?.forEach { p ->
            val text = paraText(p).trim()
            if (text.isEmpty()) return@forEach
            val lvl = p.child("pPr")?.attr("lvl")?.toIntOrNull() ?: 0
            val bullet = (listOfNotNull(p.child("pPr")) + styles.mapNotNull { it.child("lvl${lvl + 1}pPr") })
                .firstNotNullOfOrNull { pr -> pr.children().firstOrNull { it.localName in setOf("buNone", "buChar", "buAutoNum", "buBlip") }?.localName }
            val indent = "  ".repeat(lvl.coerceAtMost(4))
            out += when (bullet) {
                "buChar", "buBlip" -> indent + "- " + text
                "buAutoNum" -> indent + "${++number}. " + text
                else -> { number = 0; text }
            }
        }
        // Plain lines stay paragraphs of their own; points stay one list.
        return out.flatMapIndexed { i, l ->
            val list = Regex("^\\s*(- |\\d+\\. )").containsMatchIn(l)
            val prevList = i > 0 && Regex("^\\s*(- |\\d+\\. )").containsMatchIn(out[i - 1])
            if (i > 0 && !(list && prevList)) listOf("", l) else listOf(l)
        }
    }

    private fun paraText(p: Element): String = buildString {
        p.children().forEach { r ->
            when (r.localName) {
                "r", "fld" -> append(r.child("t")?.textContent.orEmpty())
                "br" -> append('\n')
            }
        }
    }

    private fun Element.text(): String = child("txBody")?.children("p")?.joinToString("\n") { paraText(it) }?.trim('\n', ' ').orEmpty()

    /** A table of a graphic frame as Markdown rows. */
    private fun tableOf(tbl: Element): String {
        val rows = tbl.all("tr").map { tr -> tr.children("tc").map { tc -> tc.all("p").joinToString(" ") { paraText(it).replace('\n', ' ').trim() }.trim().replace("|", "\\|") } }
        return if (rows.isEmpty() || rows.all { r -> r.all { it.isEmpty() } }) "" else DocBlocks.table(rows)
    }

    /** A chart of a graphic frame as ```chart block (with the values Office cached), or null. */
    private fun chartOf(pkg: Pkg, slidePath: String, frame: Element): String? {
        val data = frame.all("graphicData").firstOrNull()?.takeIf { it.attr("uri").endsWith("/chart") } ?: return null
        val rid = data.children().firstOrNull { it.localName == "chart" }?.relId("id") ?: return null
        val spec = OfficeCharts.read(pkg.files[pkg.rels(slidePath)[rid]])?.spec() ?: return null
        return "```chart\n" + spec.toJson() + "\n```"
    }

    /** The texts of a SmartArt graphic as points (its data part), or empty. */
    private fun smartArt(pkg: Pkg, slidePath: String, frame: Element): List<String> {
        val data = frame.all("graphicData").firstOrNull()?.takeIf { it.attr("uri").endsWith("/diagram") } ?: return emptyList()
        val rid = data.children().firstOrNull { it.localName == "relIds" }?.relId("dm") ?: return emptyList()
        val root = pkg.xml(pkg.rels(slidePath)[rid]) ?: return emptyList()
        return root.all("pt").filter { it.attr("type").let { t -> t.isEmpty() || t == "node" } }
            .mapNotNull { pt -> pt.all("t").joinToString("") { it.textContent }.trim().takeIf { it.isNotEmpty() } }.map { "- $it" }
    }

    /**
     * One foreign slide: the placeholders fill the layout, the rest becomes elements. Returns the slide and the text of
     * its footer placeholder (for the deck's Masterfolie).
     */
    private fun foreignSlide(ctx: Ctx, root: Element, index: Int, media: MutableMap<String, ByteArray>): Pair<SlideDeck.Slide, String> {
        val tree = root.child("cSld")?.child("spTree") ?: return SlideDeck.Slide("Folie ${index + 1}") to ""
        val all = items(tree, ctx)
        val slideArea = ctx.geo.cx.toDouble() * ctx.geo.cy
        var title = ""
        var footer = ""
        val bodies = mutableListOf<Pair<Item, List<String>>>()
        val heads = mutableListOf<Pair<Double, String>>()
        val tables = mutableListOf<String>()
        val charts = mutableListOf<String>()
        val rest = mutableListOf<Item>()
        val layoutType = ctx.layout?.attr("type").orEmpty()
        val byName = layoutByName(ctx.layout?.child("cSld")?.attr("name").orEmpty())
        for (item in all) {
            val e = item.el
            val ph = if (item.group.isEmpty()) e.ph() else null
            when {
                e.localName == "graphicFrame" -> {
                    val tbl = e.all("tbl").firstOrNull()
                    val chart = chartOf(ctx.pkg, ctx.slidePath, e)
                    val art = smartArt(ctx.pkg, ctx.slidePath, e)
                    when {
                        chart != null -> charts += chart
                        tbl != null -> tableOf(tbl).takeIf { t -> t.isNotEmpty() }?.let { t -> tables += t }
                        art.isNotEmpty() -> bodies += item to art
                    }
                }
                e.localName == "sp" && ph != null -> {
                    val type = ph.attr("type").ifEmpty { "obj" }
                    when {
                        kindOf(type) == "title" -> title = listOf(title, e.text().replace('\n', ' ')).filter { t -> t.isNotBlank() }.joinToString(" ")
                        type == "ftr" -> footer = e.text().replace('\n', ' ')
                        type in setOf("sldNum", "dt", "hdr", "sldImg") -> {}
                        else -> {
                            val lines = paragraphs(e, ctx)
                            when {
                                lines.isEmpty() -> {}
                                // Comparison layouts: small single-line placeholders above the two contents are the column heads.
                                layoutType == "twoTxTwoObj" && lines.size == 1 && type == "body" && lines[0].length < 60 && heads.size < 2 -> heads += (item.box?.x ?: 0.0) to lines[0].removePrefix("- ")
                                else -> bodies += item to lines
                            }
                        }
                    }
                }
                else -> rest += item
            }
        }
        // The slide's picture: a picture placeholder or one big picture; small pictures and logos stay elements.
        val pictures = rest.filter { it.el.localName == "pic" && it.group.isEmpty() && it.box != null }
        val picture = (pictures.firstOrNull { it.el.ph() != null } ?: pictures.filter { p ->
            val b = p.box!!
            b.w * b.h >= slideArea * 0.12 && !p.el.cNvPr()?.attr("name").orEmpty().startsWith("Logo", true)
        }.maxByOrNull { it.box!!.let { b -> b.w * b.h } })?.let { p ->
            val embed = p.el.all("blip").firstOrNull()?.relId("embed")
            keepImage(ctx.pkg.files[ctx.pkg.rels(ctx.slidePath)[embed]], media)?.let { path -> Triple(p, path, p.el.cNvPr()?.attr("descr").orEmpty()) }
        }
        val elements = rest.filter { it !== picture?.first }.mapNotNull { elementOf(it, ctx, media) }
        val notes = ctx.pkg.notes(ctx.slidePath)
        val sorted = bodies.sortedBy { it.first.box?.x ?: 0.0 }
        val bodyText = sorted.flatMap { listOf("") + it.second }.joinToString("\n").trim()
        val withTables = (listOf(bodyText) + tables).filter { it.isNotBlank() }.joinToString("\n\n")
        if (index == 0) {
            val lines = sorted.flatMap { it.second }.map { it.trim().removePrefix("- ").replace(Regex("^\\d+\\.\\s+"), "") }.filter { it.isNotBlank() }
            val head = title.ifBlank { lines.firstOrNull().orEmpty() }
            val sub = (if (title.isBlank()) lines.drop(1) else lines).joinToString("\n")
            val img = picture?.let { if (pictures.firstOrNull { p -> p.el.ph() != null } != null) it else null }
            val extra = picture?.takeIf { img == null }?.first?.let { elementOf(it, ctx, media) }
            return SlideDeck.Slide(head, sub, notes = notes, layout = SlideDeck.Layout.TITLE, image = img?.second.orEmpty(), imageAlt = img?.third.orEmpty(), elements = listOfNotNull(extra) + elements) to footer
        }
        val slideW = ctx.geo.cx.toDouble()
        val twoTypes = setOf("twoObj", "twoColTx", "twoTxTwoObj", "objAndTx", "txAndObj", "twoObjAndTx", "txAndTwoObj")
        val sideBySide = sorted.size >= 2 && abs((sorted[0].first.box?.x ?: 0.0) - (sorted[1].first.box?.x ?: 0.0)) > slideW / 5
        val heading = title.ifBlank { charts.firstOrNull()?.let { SlideDeck.chartOf(it)?.title?.ifBlank { null } } ?: "" }
        val slide = when {
            heading.isBlank() && bodyText.isBlank() && tables.isEmpty() && charts.isEmpty() ->
                SlideDeck.Slide("", "", layout = SlideDeck.Layout.FREE, elements = listOfNotNull(picture?.first?.let { elementOf(it, ctx, media) }) + elements)
            charts.isNotEmpty() -> SlideDeck.Slide(heading.ifBlank { "Diagramm" }, (listOf(withTables) + charts.first()).filter { it.isNotBlank() }.joinToString("\n\n"), layout = SlideDeck.Layout.CHART, elements = elements)
            heading.isBlank() -> SlideDeck.Slide("", withTables, layout = SlideDeck.Layout.FREE, elements = listOfNotNull(picture?.first?.let { elementOf(it, ctx, media) }) + elements)
            layoutType == "secHead" || byName == SlideDeck.Layout.SECTION ->
                SlideDeck.Slide(heading, sorted.flatMap { it.second }.filter { it.isNotBlank() }.joinToString(" ") { it.trim().removePrefix("- ") }.take(140), layout = SlideDeck.Layout.SECTION, elements = elements)
            byName == SlideDeck.Layout.AGENDA || byName == SlideDeck.Layout.END || byName == SlideDeck.Layout.QUOTE ->
                SlideDeck.Slide(heading, if (byName == SlideDeck.Layout.AGENDA) bodyText else sorted.flatMap { it.second }.filter { it.isNotBlank() }.joinToString("\n") { it.trim().removePrefix("- ") }, layout = byName, elements = elements)
            picture != null -> {
                val left = picture.first.box!!.let { it.x + it.w / 2 } < slideW / 2
                if (withTables.isBlank()) SlideDeck.Slide(heading, "", layout = SlideDeck.Layout.IMAGE, image = picture.second, imageAlt = picture.third, elements = elements)
                else SlideDeck.Slide(heading, withTables, layout = SlideDeck.Layout.IMAGE_TEXT, image = picture.second, imageAlt = picture.third, imageLeft = left, elements = elements)
            }
            sorted.size >= 2 && tables.isEmpty() && (layoutType in twoTypes || byName == SlideDeck.Layout.TWO_COLUMNS || byName == SlideDeck.Layout.COMPARISON || sideBySide) -> {
                val sortedHeads = heads.sortedBy { it.first }.map { it.second }
                val leftText = SlideDeck.withColumnHead(sortedHeads.getOrElse(0) { "" }, sorted[0].second.joinToString("\n"))
                val rightText = SlideDeck.withColumnHead(sortedHeads.getOrElse(1) { "" }, sorted.drop(1).flatMap { listOf("") + it.second }.joinToString("\n").trim())
                SlideDeck.Slide(heading, leftText, rightText, layout = if (heads.isNotEmpty() || byName == SlideDeck.Layout.COMPARISON) SlideDeck.Layout.COMPARISON else SlideDeck.Layout.TWO_COLUMNS, elements = elements)
            }
            tables.isNotEmpty() && bodyText.isBlank() -> SlideDeck.Slide(heading, tables.joinToString("\n\n"), layout = SlideDeck.Layout.TABLE, elements = elements)
            withTables.isBlank() && elements.isEmpty() -> SlideDeck.Slide(heading, "", layout = SlideDeck.Layout.SECTION)
            // Title and drawn shapes (layout „Nur Titel“): the shapes are the content.
            withTables.isBlank() -> SlideDeck.Slide(heading, "", layout = SlideDeck.Layout.FREE, elements = elements)
            else -> SlideDeck.Slide(heading, withTables, layout = SlideDeck.Layout.CONTENT, elements = elements)
        }
        return slide.copy(notes = notes) to footer
    }

    // ── Elements ─────────────────────────────────────────────────────────────────────────────────────────

    private val LINES = setOf("line", "straightConnector1", "bentConnector2", "bentConnector3", "curvedConnector3")
    private val ARROWS = setOf("rightArrow", "leftArrow", "upArrow", "downArrow", "leftRightArrow", "chevron", "homePlate", "notchedRightArrow", "stripedRightArrow")

    /** A shape, line or picture as freely placed element of the editor (null: invisible or not shown by the editor). */
    internal fun elementOf(item: Item, ctx: Ctx, media: MutableMap<String, ByteArray>): SlideElement? {
        val e = item.el
        val b = item.box ?: return null
        val g = ctx.geo
        fun r(v: Double) = Math.round(v * 100) / 100.0
        val x = r(g.x(b.x))
        val y = r(g.y(b.y))
        val w = r(g.len(b.w))
        val h = r(g.len(b.h))
        val rot = r(((b.rot % 360) + 360) % 360)
        val base = SlideElement(x = x, y = y, w = w.coerceAtLeast(0.1), h = h.coerceAtLeast(0.1), rotation = rot, group = item.group)
        val spPr = e.child("spPr")
        val blip = (if (e.localName == "pic") e.child("blipFill") else spPr?.child("blipFill"))?.child("blip")
        if (blip != null) {
            val path = keepImage(ctx.pkg.files[ctx.pkg.rels(ctx.slidePath)[blip.relId("embed")]], media) ?: return null
            return base.copy(kind = "bild", src = path, text = e.cNvPr()?.attr("descr").orEmpty())
        }
        if (e.localName == "pic" || e.localName == "graphicFrame") return null
        val style = e.child("style")
        val geom = spPr?.child("prstGeom")?.attr("prst") ?: if (spPr?.child("custGeom") != null) "cust" else "rect"
        val line = spPr?.child("ln")?.let { ln -> if (ln.child("noFill") != null) null else ln.child("solidFill")?.let(ctx.pal::color) }
            ?: style?.child("lnRef")?.takeIf { (it.attr("idx").toIntOrNull() ?: 0) > 0 && spPr?.child("ln")?.child("noFill") == null }?.let(ctx.pal::color)
        if (e.localName == "cxnSp" || geom in LINES) {
            val color = line ?: return null
            // The editor draws lines through the middle of their box.
            val hh = maxOf(h, 0.3)
            return base.copy(kind = "form", shape = "linie", color = "#$color", y = r(y + h / 2 - hh / 2), h = hh, w = maxOf(w, 0.3))
        }
        val (fill, fill2) = fillOf(spPr, style, ctx.pal)
        val text = e.text()
        if (text.isBlank() && fill == null && line == null) return null
        val styles = ctx.styles(e)
        val firstP = e.child("txBody")?.children("p")?.firstOrNull { paraText(it).isNotBlank() }
        val firstR = firstP?.children()?.firstOrNull { it.localName == "r" && it.child("t")?.textContent.orEmpty().isNotBlank() }?.child("rPr")
        val lvl = firstP?.child("pPr")?.attr("lvl")?.toIntOrNull() ?: 0
        val defRPrs = styles.mapNotNull { it.child("lvl${lvl + 1}pPr")?.child("defRPr") }
        val rPrs = listOfNotNull(firstR, firstP?.child("pPr")?.child("defRPr")) + defRPrs
        val sz = rPrs.firstNotNullOfOrNull { it.attr("sz").toIntOrNull() } ?: 1800
        val color = rPrs.firstNotNullOfOrNull { it.child("solidFill")?.let(ctx.pal::color) }
            ?: style?.child("fontRef")?.let(ctx.pal::color) ?: ctx.pal.scheme("tx1") ?: "000000"
        val bold = rPrs.firstNotNullOfOrNull { it.attr("b").takeIf { v -> v.isNotEmpty() } }?.let { it == "1" || it == "true" } ?: false
        val algn = (listOfNotNull(firstP?.child("pPr")) + styles.mapNotNull { it.child("lvl${lvl + 1}pPr") }).firstNotNullOfOrNull { it.attr("algn").takeIf { a -> a.isNotEmpty() } }
        val align = when (algn) { "ctr" -> "mitte"; "r" -> "rechts"; else -> "links" }
        val size = (sz / 100.0 * g.scale).roundToInt().coerceIn(6, 120)
        val box = e.child("nvSpPr")?.child("cNvSpPr")?.attr("txBox") == "1"
        val shape = when {
            geom == "ellipse" -> "ellipse"
            geom == "roundRect" || geom == "snipRoundRect" || geom == "round2SameRect" -> "abgerundet"
            geom in ARROWS -> "pfeil"
            else -> "rechteck"
        }
        return if (fill == null || (box && shape == "rechteck")) {
            base.copy(kind = "text", text = text, fill = fill?.let { "#$it" }.orEmpty(), fill2 = fill2?.let { "#$it" }.orEmpty(), color = "#$color", size = size, bold = bold, align = align)
        } else {
            base.copy(kind = "form", shape = shape, text = text, fill = "#$fill", fill2 = fill2?.let { "#$it" }.orEmpty(), color = "#$color", size = size, bold = bold, align = if (algn == null) "mitte" else align)
        }
    }

    /** Fill of a shape (and the second colour of a gradient): its own, else the one of its style; null = none. */
    private fun fillOf(spPr: Element?, style: Element?, pal: Palette): Pair<String?, String?> {
        spPr?.child("noFill")?.let { return null to null }
        spPr?.child("solidFill")?.let { return pal.color(it) to null }
        spPr?.child("gradFill")?.all("gs")?.let { gs -> if (gs.isNotEmpty()) return pal.color(gs.first()) to pal.color(gs.last()).takeIf { gs.size > 1 } }
        if (spPr?.child("pattFill") != null) return pal.color(spPr.child("pattFill")?.child("fgClr")) to null
        val ref = style?.child("fillRef")?.takeIf { (it.attr("idx").toIntOrNull() ?: 0) > 0 } ?: return null to null
        return pal.color(ref) to null
    }

    // ── Colours ──────────────────────────────────────────────────────────────────────────────────────────

    private val COLOR_TAGS = setOf("srgbClr", "schemeClr", "sysClr", "prstClr", "scrgbClr", "hslClr")
    private val PRESET = mapOf(
        "black" to "000000", "white" to "FFFFFF", "red" to "FF0000", "green" to "008000", "blue" to "0000FF", "yellow" to "FFFF00",
        "gray" to "808080", "grey" to "808080", "darkGray" to "A9A9A9", "lightGray" to "D3D3D3", "orange" to "FFA500", "navy" to "000080",
    )

    /** The colours of a theme (dk1, lt1, accent1 … as RRGGBB) and the master's colour map (tx1 → dk1 …). */
    internal class Palette(private val scheme: Map<String, String>, private val map: Map<String, String>) {
        fun scheme(name: String): String? = scheme[map[name] ?: name] ?: scheme[name]

        /** The colour inside [parent] (srgbClr, schemeClr, sysClr, prstClr …) with its modifiers, as RRGGBB; null if none. */
        fun color(parent: Element?): String? {
            val c = parent?.children()?.firstOrNull { it.localName in COLOR_TAGS } ?: return null
            val rgb = when (c.localName) {
                "srgbClr" -> c.attr("val").uppercase().takeIf { it.length == 6 }
                "schemeClr" -> scheme(c.attr("val"))
                "sysClr" -> c.attr("lastClr").ifEmpty { if (c.attr("val") == "window") "FFFFFF" else "000000" }.uppercase()
                "prstClr" -> PRESET[c.attr("val")] ?: "000000"
                "scrgbClr" -> listOf("r", "g", "b").joinToString("") { k -> "%02X".format((fromLinear((c.attr(k).toDoubleOrNull() ?: 0.0) / 100000) * 255).roundToInt().coerceIn(0, 255)) }
                "hslClr" -> hslToRgb(Triple((c.attr("hue").toDoubleOrNull() ?: 0.0) / 60000 / 360, (c.attr("sat").toDoubleOrNull() ?: 0.0) / 100000, (c.attr("lum").toDoubleOrNull() ?: 0.0) / 100000))
                else -> null
            } ?: return null
            return modified(rgb, c)
        }
    }

    /** Applies lumMod, lumOff, satMod (in HSL), tint and shade (in linear RGB, as Office does) in their order. */
    internal fun modified(rgb: String, color: Element): String {
        var out = rgb
        for (m in color.children()) {
            val v = (m.attr("val").toDoubleOrNull() ?: continue) / 100000
            out = when (m.localName) {
                "lumMod" -> hsl(out) { (h, s, l) -> Triple(h, s, l * v) }
                "lumOff" -> hsl(out) { (h, s, l) -> Triple(h, s, l + v) }
                "satMod" -> hsl(out) { (h, s, l) -> Triple(h, s * v, l) }
                "tint" -> linear(out) { c -> c * v + (1 - v) }
                "shade" -> linear(out) { c -> c * v }
                else -> out
            }
        }
        return out
    }

    private fun channels(rgb: String) = listOf(0, 2, 4).map { rgb.substring(it, it + 2).toInt(16) / 255.0 }
    private fun hex(c: List<Double>) = c.joinToString("") { "%02X".format((it.coerceIn(0.0, 1.0) * 255).roundToInt()) }
    private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun fromLinear(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
    private fun linear(rgb: String, f: (Double) -> Double) = hex(channels(rgb).map { fromLinear(f(toLinear(it)).coerceIn(0.0, 1.0)) })

    private fun hsl(rgb: String, f: (Triple<Double, Double, Double>) -> Triple<Double, Double, Double>): String {
        val (r, g, b) = channels(rgb)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        val s = if (d == 0.0) 0.0 else d / (1 - abs(2 * l - 1))
        val h = when {
            d == 0.0 -> 0.0
            max == r -> ((g - b) / d).mod(6.0) / 6
            max == g -> ((b - r) / d + 2) / 6
            else -> ((r - g) / d + 4) / 6
        }
        val (h2, s2, l2) = f(Triple(h, s, l))
        return hslToRgb(Triple(h2, s2.coerceIn(0.0, 1.0), l2.coerceIn(0.0, 1.0)))
    }

    private fun hslToRgb(v: Triple<Double, Double, Double>): String {
        val (h, s, l) = v
        val c = (1 - abs(2 * l - 1)) * s
        val hp = (h.mod(1.0)) * 6
        val x = c * (1 - abs(hp.mod(2.0) - 1))
        val (r, g, b) = when {
            hp < 1 -> Triple(c, x, 0.0)
            hp < 2 -> Triple(x, c, 0.0)
            hp < 3 -> Triple(0.0, c, x)
            hp < 4 -> Triple(0.0, x, c)
            hp < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = l - c / 2
        return hex(listOf(r + m, g + m, b + m))
    }

    // ── XML helpers ──────────────────────────────────────────────────────────────────────────────────────

    /** "ppt/slides/../media/x.png" → "ppt/media/x.png". */
    internal fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        path.split('/').forEach { p -> when (p) { ".." -> out.removeLastOrNull(); ".", "" -> {}; else -> out.addLast(p) } }
        return out.joinToString("/")
    }

    /** Keeps a picture of the file for the document; returns its Markdown path (null if it is no supported picture). */
    private fun keepImage(bytes: ByteArray?, media: MutableMap<String, ByteArray>): String? {
        bytes ?: return null
        val type = DocMarkup.imageType(bytes) ?: return null
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val path = "${DocMarkup.IMAGE_DIR}/bild-$hash.${if (type == "jpeg") "jpg" else type}"
        media[path] = bytes
        return path
    }

    internal fun xml(bytes: ByteArray?): Element? = bytes?.let {
        runCatching {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { f.isExpandEntityReferences = false }
            f.newDocumentBuilder().parse(ByteArrayInputStream(it)).documentElement
        }.getOrNull()
    }

    private fun Node.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun Element.children(local: String): List<Element> = children().filter { it.localName == local }

    private fun Element.child(local: String): Element? = children().firstOrNull { it.localName == local }

    private fun Element.all(local: String): List<Element> {
        val list = getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.attr(local: String): String = (0 until attributes.length).map { attributes.item(it) }
        .firstOrNull { it.localName == local || it.nodeName == local }?.nodeValue.orEmpty()

    /** An r:… attribute (relationship id) of the element. */
    private fun Element.relId(local: String): String? = (0 until attributes.length).map { attributes.item(it) }
        .firstOrNull { it.localName == local && it.namespaceURI?.contains("relationships") == true }?.nodeValue

    private fun Element.nv(): Element? = children().firstOrNull { it.localName.startsWith("nv") && it.localName.endsWith("Pr") }

    private fun Element.cNvPr(): Element? = nv()?.child("cNvPr")

    private fun Element.ph(): Element? = nv()?.child("nvPr")?.child("ph")

    /** The a:xfrm of a shape's spPr (or the p:xfrm of a graphic frame). */
    private fun Element.xfrm(): Element? = child("spPr")?.child("xfrm") ?: child("xfrm")
}
