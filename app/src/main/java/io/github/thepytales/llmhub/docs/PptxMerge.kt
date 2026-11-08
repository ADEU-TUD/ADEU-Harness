package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.files.OfficeEdit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Unbekanntes bleibt erhalten (AP-51): writes the editor's deck into the original .pptx instead of a new file, when
 * slides were added, removed, moved or restructured (text changes alone are patched by [DocFiles]).
 *
 * Slides whose Markdown did not change stay as they were, byte for byte, with everything the editor does not know
 * (SmartArt, animations, custom XML, comments, tags) – and so do the master, the layouts, the theme, sections and every
 * other part of the package. A slide whose text changed but not its structure gets only the text replaced. New or
 * restructured slides come from the editor's own export ([PptxWriter], passed as [merge]'s `generated`): they are added
 * with their pictures, notes and charts, hide the master's graphics (they bring the TUD design along) and name their
 * cSld [OWN_SLIDE], so [PptxReader] reads them the way Lora wrote them. Deleted slides leave the package (also the
 * sections and custom shows), the order follows the editor.
 */
object PptxMerge {
    /** cSld name of the slides Lora added to a foreign deck. */
    const val OWN_SLIDE = "Lora"

    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CT_SLIDE = "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"

    private class Rel(val id: String, val type: String, val target: String, val external: Boolean)

    /**
     * The original package with the deck [now] written into it; [base] is the Markdown the original corresponds to,
     * [generated] the editor's export of [now]. Null when that cannot be done safely (then the file is written anew).
     */
    fun merge(original: ByteArray, base: String, now: String, generated: ByteArray): ByteArray? {
        val files = unzip(original)
        val gen = unzip(generated)
        val pres = files["ppt/presentation.xml"]?.toString(Charsets.UTF_8) ?: return null
        var presRels = files["ppt/_rels/presentation.xml.rels"]?.toString(Charsets.UTF_8) ?: return null
        val list = Regex("<((?:\\w+:)?)sldIdLst>([\\s\\S]*?)</(?:\\w+:)?sldIdLst>").find(pres) ?: return null
        val prefix = list.groupValues[1]
        val sldIds = Regex("<(?:\\w+:)?sldId\\b[^>]*?/>").findAll(list.groupValues[2]).map { it.value }.toList()
        val rels = relsOf(presRels).associateBy { it.id }
        val slides = sldIds.map { tag ->
            val rid = Regex("\\s\\w+:id=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: return null
            val path = rels[rid]?.let { resolve("ppt/", it.target) }?.takeIf { it in files } ?: return null
            Triple(tag, rid, path)
        }
        val a = SlideDeck.split(base)
        val b = SlideDeck.split(now)
        if (a.size != slides.size || b.isEmpty() || "ppt/slides/slide${b.size}.xml" !in gen) return null

        // Which original slide each slide of the editor is: the same Markdown, else the same structure (text patched).
        val used = BooleanArray(a.size)
        val keep = arrayOfNulls<Int>(b.size)
        b.indices.forEach { j ->
            val i = (if (j < a.size && !used[j] && a[j] == b[j]) j else a.indices.firstOrNull { !used[it] && a[it] == b[j] }) ?: return@forEach
            used[i] = true
            keep[j] = i
        }
        val patched = HashMap<Int, Pair<ByteArray, ByteArray?>>()
        b.indices.filter { keep[it] == null }.forEach { j ->
            val candidates = a.indices.filter { !used[it] && DocFiles.textChanges(a[it], b[j]) != null }.sortedBy { kotlin.math.abs(it - j) }
            for (i in candidates) {
                val parts = patchText(files, slides[i].third, DocFiles.textChanges(a[i], b[j]).orEmpty()) ?: continue
                used[i] = true
                keep[j] = i
                patched[i] = parts
                break
            }
        }
        val removed = a.indices.filter { !used[it] }.map { slides[it] }
        // New slides get numbers after all of the original (removed ones are not reused).
        var nextNumber = files.keys.mapNotNull { Regex("^ppt/slides/slide(\\d+)\\.xml$").find(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0

        var types = files["[Content_Types].xml"]?.toString(Charsets.UTF_8) ?: return null
        val genTypes = gen["[Content_Types].xml"]?.toString(Charsets.UTF_8).orEmpty()
        fun addOverride(part: String, type: String) {
            if ("PartName=\"/$part\"" !in types) types = types.replace("</Types>", "<Override PartName=\"/$part\" ContentType=\"$type\"/></Types>")
        }
        fun dropOverride(part: String) {
            types = types.replace(Regex("<Override\\s+PartName=\"/${Regex.escape(part)}\"[^>]*/>"), "")
        }
        fun ensureDefault(ext: String) {
            if (Regex("Extension=\"${Regex.escape(ext)}\"", RegexOption.IGNORE_CASE).containsMatchIn(types)) return
            val type = Regex("<Default\\s+Extension=\"${Regex.escape(ext)}\"\\s+ContentType=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(genTypes)?.groupValues?.get(1)
                ?: when (ext.lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "png" -> "image/png"; "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; else -> "application/octet-stream" }
            types = types.replace("</Types>", "<Default Extension=\"$ext\" ContentType=\"$type\"/></Types>")
        }

        // Removed slides leave the package with their notes.
        for ((_, rid, path) in removed) {
            val own = relsOf(files[relsPath(path)]?.toString(Charsets.UTF_8).orEmpty())
            own.firstOrNull { it.type.endsWith("/notesSlide") }?.let { n ->
                val notes = resolve(dir(path), n.target)
                files.remove(notes); files.remove(relsPath(notes)); dropOverride(notes)
            }
            files.remove(path); files.remove(relsPath(path)); dropOverride(path)
            presRels = presRels.replace(Regex("<Relationship\\b[^>]*\\bId=\"${Regex.escape(rid)}\"[^>]*/>"), "")
        }
        patched.forEach { (i, parts) ->
            val path = slides[i].third
            files[path] = parts.first
            parts.second?.let { notes -> notesOf(files, path)?.let { files[it] = notes } }
        }

        // New and restructured slides from the editor's export.
        var nextId = sldIds.mapNotNull { Regex("\\sid=\"(\\d+)\"").find(it)?.groupValues?.get(1)?.toLongOrNull() }.maxOrNull()?.coerceAtLeast(255) ?: 255
        val layout = blankLayout(files) ?: return null
        val copied = HashMap<String, String>()
        var notesMaster = relsOf(presRels).firstOrNull { it.type.endsWith("/notesMaster") }?.let { resolve("ppt/", it.target) }
        var pres2 = pres
        fun unique(path: String): String {
            if (path !in files) return path
            var k = 2
            while ("${path.substringBeforeLast('.')}-$k.${path.substringAfterLast('.')}" in files) k++
            return "${path.substringBeforeLast('.')}-$k.${path.substringAfterLast('.')}"
        }
        /** Copies part [from] of the export (and what it refers to) into the package; returns its new path. */
        fun copyPart(from: String, slideNo: Int): String? {
            copied[from]?.let { return it }
            val data = gen[from] ?: return null
            val to = unique(dir(from) + "lora$slideNo-" + from.substringAfterLast('/'))
            copied[from] = to
            files[to] = data
            Regex("<Override\\s+PartName=\"/${Regex.escape(from)}\"\\s+ContentType=\"([^\"]+)\"").find(genTypes)?.let { addOverride(to, it.groupValues[1]) } ?: ensureDefault(to.substringAfterLast('.'))
            gen[relsPath(from)]?.toString(Charsets.UTF_8)?.let { r ->
                files[relsPath(to)] = rewriteRels(r) { rel ->
                    if (rel.external) null else copyPart(resolve(dir(from), rel.target), slideNo)?.let { relative(dir(to), it) }
                }.toByteArray()
            }
            return to
        }
        val order = b.indices.map { j ->
            keep[j]?.let { return@map slides[it].first }
            val no = ++nextNumber
            val id = ++nextId
            val path = "ppt/slides/slide$no.xml"
            var xml = gen.getValue("ppt/slides/slide${j + 1}.xml").toString(Charsets.UTF_8)
            // The slide brings the TUD design along: the master's graphics stay hidden, the background is its own.
            xml = xml.replaceFirst(Regex("<p:sld(\\s)"), "<p:sld showMasterSp=\"0\"$1")
            xml = xml.replaceFirst(Regex("<p:cSld(\\s[^>]*)?>"), "<p:cSld name=\"$OWN_SLIDE\">")
            if ("<p:bg>" !in xml) xml = xml.replaceFirst("<p:cSld name=\"$OWN_SLIDE\">", "<p:cSld name=\"$OWN_SLIDE\"><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>")
            files[path] = xml.toByteArray()
            addOverride(path, CT_SLIDE)
            val genRels = gen["ppt/slides/_rels/slide${j + 1}.xml.rels"]?.toString(Charsets.UTF_8).orEmpty()
            files[relsPath(path)] = rewriteRels(genRels) { rel ->
                when {
                    rel.external -> null
                    rel.type.endsWith("/slideLayout") -> relative("ppt/slides/", layout)
                    rel.type.endsWith("/notesSlide") -> {
                        val genNotes = resolve("ppt/slides/", rel.target)
                        val notesPath = unique("ppt/notesSlides/notesSlide$no.xml")
                        files[notesPath] = gen[genNotes] ?: return@rewriteRels null
                        addOverride(notesPath, "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml")
                        val master = notesMaster ?: run {
                            // The deck has no notes master yet: the export's comes along (with its theme).
                            val m = copyPart("ppt/notesMasters/notesMaster1.xml", no) ?: return@rewriteRels null
                            presRels = presRels.replace("</Relationships>", "<Relationship Id=\"rIdLoraNotesMaster\" Type=\"$REL/notesMaster\" Target=\"${m.removePrefix("ppt/")}\"/></Relationships>")
                            val entry = "<${prefix}notesMasterIdLst><${prefix}notesMasterId r:id=\"rIdLoraNotesMaster\"/></${prefix}notesMasterIdLst>"
                            pres2 = Regex("</(?:\\w+:)?sldMasterIdLst>").find(pres2)?.let { e -> pres2.replaceRange(e.range.last + 1, e.range.last + 1, entry) } ?: pres2
                            notesMaster = m
                            m
                        }
                        files[relsPath(notesPath)] = rewriteRels(gen[relsPath(genNotes)]?.toString(Charsets.UTF_8).orEmpty()) { nr ->
                            when {
                                nr.type.endsWith("/notesMaster") -> relative("ppt/notesSlides/", master)
                                nr.type.endsWith("/slide") -> relative("ppt/notesSlides/", path)
                                else -> null
                            }
                        }.toByteArray()
                        relative("ppt/slides/", notesPath)
                    }
                    else -> copyPart(resolve("ppt/slides/", rel.target), no)?.let { relative("ppt/slides/", it) }
                }
            }.toByteArray()
            val rid = "rIdLora$no"
            presRels = presRels.replace("</Relationships>", "<Relationship Id=\"$rid\" Type=\"$REL/slide\" Target=\"slides/slide$no.xml\"/></Relationships>")
            "<${prefix}sldId id=\"$id\" r:id=\"$rid\"/>"
        }

        // The presentation: new order, sections and custom shows without the removed slides.
        pres2 = pres2.replace(list.value, "<${prefix}sldIdLst>${order.joinToString("")}</${prefix}sldIdLst>")
        val idOf = { tag: String -> Regex("\\sid=\"(\\d+)\"").find(tag)?.groupValues?.get(1).orEmpty() }
        for ((tag, rid, _) in removed) {
            pres2 = pres2.replace(Regex("<(?:\\w+:)?sldId\\s+id=\"${idOf(tag)}\"\\s*/>"), "")
            pres2 = pres2.replace(Regex("<(?:\\w+:)?sld\\s+\\w+:id=\"${Regex.escape(rid)}\"\\s*/>"), "")
        }
        // Sections (PowerPoint 2010): every slide stays in its section, a new one joins the section of the slide before
        // it; each section lists its slides in the new order.
        val sectionRe = Regex("(<((?:\\w+:)?)section\\b[^>]*>[\\s\\S]*?<(?:\\w+:)?sldIdLst)(/>|>([\\s\\S]*?)</(?:\\w+:)?sldIdLst>)")
        val sections = sectionRe.findAll(pres2).toList()
        if (sections.isNotEmpty()) {
            val sectionOf = HashMap<String, Int>()
            sections.forEachIndexed { s, m -> Regex("\\sid=\"(\\d+)\"").findAll(m.groupValues[4]).forEach { sectionOf[it.groupValues[1]] = s } }
            var current = 0
            val members = List(sections.size) { mutableListOf<String>() }
            order.forEach { tag ->
                val id = idOf(tag)
                current = sectionOf[id] ?: current
                members[current] += id
            }
            var k = 0
            pres2 = sectionRe.replace(pres2) { m ->
                val p = m.groupValues[2]
                val ids = members.getOrNull(k++).orEmpty()
                m.groupValues[1] + ">" + ids.joinToString("") { "<${p}sldId id=\"$it\"/>" } + "</${p}sldIdLst>"
            }
        }
        files["ppt/presentation.xml"] = pres2.toByteArray()
        files["ppt/_rels/presentation.xml.rels"] = presRels.toByteArray()
        files["[Content_Types].xml"] = types.toByteArray()
        return zip(files)
    }

    /** The slide (and its notes) with [changes] (old → new text) written into it; null if a text is not found once. */
    private fun patchText(files: Map<String, ByteArray>, slide: String, changes: List<Pair<String, String>>): Pair<ByteArray, ByteArray?>? {
        val notes = notesOf(files, slide)
        var mini = zip(linkedMapOf("ppt/slides/slide1.xml" to (files[slide] ?: return null)).apply { notes?.let { n -> files[n]?.let { put("ppt/notesSlides/notesSlide1.xml", it) } } })
        val notesMark = Regex("^\\s*(Notizen|Notiz|Sprechernotizen|Sprechertext|Notes)\\s*:\\s*", RegexOption.IGNORE_CASE)
        for ((old, new) in changes) {
            val (o, n) = if (notesMark.containsMatchIn(old) && notesMark.containsMatchIn(new)) old.replace(notesMark, "") to new.replace(notesMark, "") else old to new
            mini = runCatching { OfficeEdit.replace("pptx", mini, o, n).bytes }.getOrNull() ?: return null
        }
        val out = unzip(mini)
        return out.getValue("ppt/slides/slide1.xml") to out["ppt/notesSlides/notesSlide1.xml"]
    }

    private fun notesOf(files: Map<String, ByteArray>, slide: String): String? =
        relsOf(files[relsPath(slide)]?.toString(Charsets.UTF_8).orEmpty()).firstOrNull { it.type.endsWith("/notesSlide") }?.let { resolve(dir(slide), it.target) }

    /** A layout for the added slides: „Leer“, else „Nur Titel“, else the first one. */
    private fun blankLayout(files: Map<String, ByteArray>): String? {
        val layouts = files.keys.filter { Regex("^ppt/slideLayouts/[^/]+\\.xml$").matches(it) }.sorted()
        fun typed(t: String) = layouts.firstOrNull { files.getValue(it).toString(Charsets.UTF_8).let { x -> Regex("<(?:\\w+:)?sldLayout\\b[^>]*\\btype=\"$t\"").containsMatchIn(x) } }
        return typed("blank") ?: typed("titleOnly") ?: layouts.firstOrNull()
    }

    private fun relsOf(xml: String): List<Rel> = Regex("<Relationship\\b[^>]*/?>").findAll(xml).map { m ->
        fun a(n: String) = Regex("\\b$n=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1).orEmpty()
        Rel(a("Id"), a("Type"), a("Target"), a("TargetMode") == "External")
    }.toList()

    /** The relationships [xml] with the targets [map] gives (null: the relationship is left out). */
    private fun rewriteRels(xml: String, map: (Rel) -> String?): String {
        val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        return head + relsOf(xml).mapNotNull { r ->
            if (r.external) return@mapNotNull "<Relationship Id=\"${r.id}\" Type=\"${r.type}\" Target=\"${r.target.replace("&", "&amp;")}\" TargetMode=\"External\"/>"
            map(r)?.let { "<Relationship Id=\"${r.id}\" Type=\"${r.type}\" Target=\"$it\"/>" }
        }.joinToString("") + "</Relationships>"
    }

    private fun dir(path: String) = path.substringBeforeLast('/', "") + "/"

    private fun relsPath(path: String) = dir(path) + "_rels/" + path.substringAfterLast('/') + ".rels"

    private fun resolve(dir: String, target: String) = PptxReader.normalize(if (target.startsWith("/")) target.removePrefix("/") else dir + target)

    /** [path] relative to the folder [fromDir] ("ppt/slides/", "ppt/media/x.png" → "../media/x.png"). */
    private fun relative(fromDir: String, path: String): String {
        val from = fromDir.trim('/').split('/').filter { it.isNotEmpty() }
        val to = path.split('/')
        var common = 0
        while (common < from.size && common < to.size - 1 && from[common] == to[common]) common++
        return "../".repeat(from.size - common) + to.drop(common).joinToString("/")
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var total = 0L
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val data = z.readBytes()
                    total += data.size
                    if (total > 300L * 1024 * 1024) throw IllegalArgumentException("Die Datei ist entpackt zu groß.")
                    out[e.name] = data
                }
                e = z.nextEntry
            }
        }
        return out
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            // [Content_Types].xml first, as Office writes it.
            (listOfNotNull(files.keys.firstOrNull { it == "[Content_Types].xml" }) + files.keys.filter { it != "[Content_Types].xml" }).forEach { name ->
                z.putNextEntry(ZipEntry(name))
                z.write(files.getValue(name))
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
