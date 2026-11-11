package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.files.SystemFonts
import io.github.thepytales.llmhub.ui.tr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Schriften-Notfall (AP-91): which fonts a Word, PowerPoint or PDF file uses and which of them are missing here; for
 * each missing one a replacement – metrically equal where there is one (Carlito for Calibri, Liberation for Arial,
 * Times New Roman and Courier New …), else similar – with its effect on line breaks and pages; then replace the fonts
 * in the file or embed the installed ones into a Word file, so it looks the same on computers without them. The fonts
 * of the TU design (Noto Sans since 2025, earlier Open Sans and Univers) are recognised.
 */
object FontRescue {
    data class UsedFont(
        val name: String,
        val installed: Boolean,
        /** Carried inside the file (Word: embedded font, PDF: embedded font program). */
        val embedded: Boolean,
        val tu: Boolean,
        val replacement: String?,
        /** The replacement has the same character widths – nothing moves. */
        val metric: Boolean,
        val effect: String,
    ) {
        val missing get() = !installed && !embedded
    }

    data class Report(val fonts: List<UsedFont>, val pages: Int?) {
        val missing get() = fonts.filter { it.missing }
    }

    /** Metric twins: same widths, so line breaks and pages stay. */
    private val METRIC = mapOf(
        "calibri" to listOf("Carlito"), "cambria" to listOf("Caladea"),
        "arial" to listOf("Liberation Sans", "Arimo", "Helvetica"), "helvetica" to listOf("Arial", "Liberation Sans", "Arimo"),
        "times new roman" to listOf("Liberation Serif", "Tinos", "Times"), "times" to listOf("Times New Roman", "Liberation Serif"),
        "courier new" to listOf("Liberation Mono", "Cousine"), "courier" to listOf("Courier New", "Liberation Mono"),
        "arial narrow" to listOf("Liberation Sans Narrow"), "georgia" to listOf("Gelasio"), "segoe ui" to listOf("Selawik"),
        "carlito" to listOf("Calibri"), "caladea" to listOf("Cambria"), "liberation sans" to listOf("Arial", "Helvetica"),
        "liberation serif" to listOf("Times New Roman"), "liberation mono" to listOf("Courier New"),
    )

    /** Similar fonts (not equal widths). */
    private val SIMILAR = mapOf(
        "noto sans" to listOf("Open Sans", "Segoe UI", "Arial"), "noto sans medium" to listOf("Noto Sans", "Open Sans Semibold", "Segoe UI Semibold", "Arial"),
        "open sans" to listOf("Noto Sans", "Segoe UI", "Arial"), "univers" to listOf("Arial", "Helvetica", "Liberation Sans"),
        "univers 45 light" to listOf("Arial", "Helvetica"), "din" to listOf("Bahnschrift", "Arial"), "din pro" to listOf("Bahnschrift", "Arial"),
        "verdana" to listOf("DejaVu Sans", "Tahoma", "Arial"), "tahoma" to listOf("Verdana", "DejaVu Sans", "Arial"),
        "trebuchet ms" to listOf("Ubuntu", "Segoe UI", "Arial"), "century gothic" to listOf("TeX Gyre Adventor", "Avenir", "Arial"),
        "garamond" to listOf("EB Garamond", "Georgia", "Times New Roman"), "palatino linotype" to listOf("Palatino", "TeX Gyre Pagella", "Book Antiqua", "Georgia"),
        "book antiqua" to listOf("Palatino Linotype", "Palatino", "Georgia"), "consolas" to listOf("Cascadia Mono", "DejaVu Sans Mono", "Courier New"),
        "aptos" to listOf("Calibri", "Segoe UI", "Arial"), "aptos display" to listOf("Calibri Light", "Segoe UI", "Arial"),
        "calibri light" to listOf("Carlito", "Segoe UI Light", "Arial"), "lato" to listOf("Carlito", "Calibri", "Arial"),
        "roboto" to listOf("Arial", "Segoe UI"), "source sans pro" to listOf("Segoe UI", "Arial"), "segoe ui" to listOf("Selawik", "Noto Sans", "Arial"),
    )

    /** Typical width relative to Arial – for the estimate of pages when nothing can be measured here. */
    private val WIDTH = mapOf(
        "arial" to 1.0, "helvetica" to 1.0, "liberation sans" to 1.0, "arimo" to 1.0, "calibri" to 0.89, "carlito" to 0.89,
        "cambria" to 0.94, "caladea" to 0.94, "times new roman" to 0.88, "liberation serif" to 0.88, "tinos" to 0.88,
        "georgia" to 1.03, "verdana" to 1.15, "tahoma" to 1.0, "segoe ui" to 0.98, "open sans" to 1.06, "noto sans" to 1.04,
        "noto sans medium" to 1.06, "garamond" to 0.85, "eb garamond" to 0.86, "century gothic" to 1.12, "courier new" to 1.25,
        "trebuchet ms" to 0.96, "univers" to 0.99, "book antiqua" to 0.96, "palatino linotype" to 0.96, "aptos" to 0.95,
        "lato" to 0.93, "roboto" to 0.98, "dejavu sans" to 1.13, "consolas" to 1.15,
    )

    private val TU = setOf("noto sans", "noto sans medium", "open sans", "open sans semibold", "univers", "univers 45 light", "univers 55", "din pro")

    fun supported(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("docx", "docm", "pptx", "pptm", "pdf")

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    /** Fonts any system shows the same way or that are no real text fonts. */
    private fun ignored(n: String) = n.isBlank() || n.startsWith("+") || n.lowercase() in setOf("symbol", "wingdings", "wingdings 2", "wingdings 3", "webdings", "marlett", "ms mincho", "ms gothic", "simsun")

    // ── Reading ──

    private fun parts(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) out[e.name] = z.readBytes()
            }
        }
        return out
    }

    private fun text(p: Map<String, ByteArray>, name: String) = p[name]?.toString(Charsets.UTF_8).orEmpty()

    private fun themeFonts(xml: String): Pair<String?, String?> {
        val major = Regex("<a:majorFont>\\s*<a:latin typeface=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
        val minor = Regex("<a:minorFont>\\s*<a:latin typeface=\"([^\"]*)\"").find(xml)?.groupValues?.get(1)
        return major to minor
    }

    /** The fonts of an Office file (name → embedded). */
    fun officeFonts(name: String, bytes: ByteArray): Map<String, Boolean> {
        val p = parts(bytes)
        val out = LinkedHashMap<String, Boolean>()
        if (ext(name) in setOf("docx", "docm")) {
            val (major, minor) = themeFonts(p.keys.firstOrNull { it.startsWith("word/theme/") }?.let { text(p, it) }.orEmpty())
            val xml = p.filterKeys { it == "word/document.xml" || it == "word/styles.xml" || it == "word/numbering.xml" || it.startsWith("word/header") || it.startsWith("word/footer") || it == "word/footnotes.xml" }
                .values.joinToString("") { it.toString(Charsets.UTF_8) }
            Regex("w:(?:ascii|hAnsi)=\"([^\"]+)\"").findAll(xml).forEach { out.putIfAbsent(it.groupValues[1], false) }
            if (Regex("w:(?:ascii|hAnsi)Theme=\"minor").containsMatchIn(xml) || "w:rFonts" !in xml) minor?.let { out.putIfAbsent(it, false) }
            if (Regex("w:(?:ascii|hAnsi)Theme=\"major").containsMatchIn(xml)) major?.let { out.putIfAbsent(it, false) }
            Regex("<w:font w:name=\"([^\"]+)\">(.*?)</w:font>", RegexOption.DOT_MATCHES_ALL).findAll(text(p, "word/fontTable.xml")).forEach { m ->
                if ("<w:embedRegular" in m.groupValues[2] && m.groupValues[1] in out) out[m.groupValues[1]] = true
            }
        } else {
            val (major, minor) = themeFonts(p.keys.firstOrNull { it.startsWith("ppt/theme/") }?.let { text(p, it) }.orEmpty())
            val xml = p.filterKeys { (it.startsWith("ppt/slides/") || it.startsWith("ppt/slideLayouts/") || it.startsWith("ppt/slideMasters/")) && it.endsWith(".xml") }
                .values.joinToString("") { it.toString(Charsets.UTF_8) }
            Regex("<a:latin typeface=\"([^\"]+)\"").findAll(xml).forEach { out.putIfAbsent(it.groupValues[1], false) }
            if ("+mn-lt" in xml || "<a:latin" !in xml) minor?.let { out.putIfAbsent(it, false) }
            if ("+mj-lt" in xml || "type=\"title\"" in xml || "type=\"ctrTitle\"" in xml) major?.let { out.putIfAbsent(it, false) }
            Regex("<p:embeddedFont>\\s*<p:font typeface=\"([^\"]+)\"").findAll(text(p, "ppt/presentation.xml")).forEach { out[it.groupValues[1]] = true }
        }
        return out.filterKeys { !ignored(it) }
    }

    private fun pages(name: String, bytes: ByteArray): Int? = runCatching {
        val app = text(parts(bytes), "docProps/app.xml")
        Regex(if (ext(name).startsWith("ppt")) "<Slides>(\\d+)</Slides>" else "<Pages>(\\d+)</Pages>").find(app)?.groupValues?.get(1)?.toInt()
    }.getOrNull()

    /** The replacement for [font] that is installed here, and whether it has the same widths. */
    fun replacement(font: String): Pair<String, Boolean>? {
        val key = font.lowercase()
        METRIC[key]?.firstOrNull { SystemFonts.installed(it) }?.let { return it to true }
        SIMILAR[key]?.firstOrNull { SystemFonts.installed(it) }?.let { return it to false }
        val fallback = when {
            Regex("mono|courier|consol|code", RegexOption.IGNORE_CASE).containsMatchIn(font) -> listOf("Courier New", "Liberation Mono", "DejaVu Sans Mono", "Menlo")
            Regex("serif|times|roman|garamond|book|antiqua|georgia|minion|palatino|cambria", RegexOption.IGNORE_CASE).containsMatchIn(font) && !font.contains("sans", true) ->
                listOf("Times New Roman", "Liberation Serif", "Georgia", "Noto Serif", "DejaVu Serif")
            else -> listOf("Arial", "Liberation Sans", "Helvetica", "Segoe UI", "Noto Sans", "DejaVu Sans", "Roboto")
        }
        return fallback.firstOrNull { SystemFonts.installed(it) }?.let { it to false }
    }

    private fun width(font: String): Double? = WIDTH[font.lowercase()] ?: SystemFonts.widthFactor(font)

    /** What replacing [font] by [repl] does to the layout. */
    fun effect(font: String, repl: String?, metric: Boolean, pages: Int?): String {
        if (repl == null) return tr("Kein passender Ersatz installiert – die Datei wird mit einer Standardschrift gezeigt.")
        if (metric) return tr("Gleiche Zeichenbreiten – Umbrüche und Seitenzahl bleiben.")
        val a = width(font)
        val b = width(repl)
        if (a == null || b == null) return tr("Ähnliche Schrift – Zeilenumbrüche und Seitenzahl können sich ändern.")
        val pct = Math.round((b / a - 1) * 100).toInt()
        val change = when {
            pct > 2 -> tr("Text etwa {0} % breiter", pct)
            pct < -2 -> tr("Text etwa {0} % schmaler", -pct)
            else -> return tr("Fast gleiche Breite – kaum andere Umbrüche.")
        }
        val p = pages?.takeIf { it > 1 }?.let { n -> Math.round(n * b / a).toInt().takeIf { it != n }?.let { m -> tr("aus {0} Seiten werden etwa {1}", n, m) } }
        return listOfNotNull(change, p).joinToString(" – ") + "."
    }

    /** Checks [file] (Word, PowerPoint, PDF). */
    fun check(file: File): Report {
        val name = file.name
        val fonts: Map<String, Boolean> = if (ext(name) == "pdf") PrintPilot.fonts(file).associate { it.name to it.embedded } else officeFonts(name, file.readBytes())
        val pages = if (ext(name) == "pdf") null else pages(name, file.readBytes())
        return Report(fonts.map { (n, embedded) ->
            val installed = SystemFonts.installed(n)
            val r = if (installed || embedded) null else replacement(n)
            UsedFont(n, installed, embedded, n.lowercase() in TU, r?.first, r?.second == true, if (installed || embedded) "" else effect(n, r?.first, r?.second == true, pages))
        }, pages)
    }

    // ── Writing ──

    private fun write(p: Map<String, ByteArray>, out: File) {
        ZipOutputStream(out.outputStream()).use { z ->
            p.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
    }

    /** Writes [file] with the fonts of [map] (old → new) replaced into [out]. */
    fun replace(file: File, map: Map<String, String>, out: File) {
        require(ext(file.name) != "pdf") { tr("In PDFs lassen sich Schriften nicht ersetzen – bitte das Original (Word, PowerPoint) nehmen.") }
        val p = parts(file.readBytes())
        for ((n, b) in p.entries.toList()) {
            if (!n.endsWith(".xml")) continue
            var s = b.toString(Charsets.UTF_8)
            var changed = false
            for ((old, new) in map) {
                val r = Regex("((?:w:ascii|w:hAnsi|w:cs|w:eastAsia|w:name|typeface)=\")" + Regex.escape(old) + "\"")
                if (r.containsMatchIn(s)) {
                    s = r.replace(s) { m -> m.groupValues[1] + new.replace("&", "&amp;").replace("\"", "&quot;") + "\"" }
                    changed = true
                }
            }
            if (changed) p[n] = s.toByteArray(Charsets.UTF_8)
        }
        write(p, out)
    }

    data class Embedded(val fonts: List<String>, val skipped: List<Pair<String, String>>)

    /** sfnt with TrueType outlines and an embedding licence that allows it (OS/2 fsType without „restricted“). */
    private fun embeddable(ttf: ByteArray): String? {
        if (ttf.size < 12) return tr("keine Schriftdatei")
        val version = ((ttf[0].toInt() and 0xFF) shl 24) or ((ttf[1].toInt() and 0xFF) shl 16) or ((ttf[2].toInt() and 0xFF) shl 8) or (ttf[3].toInt() and 0xFF)
        if (version != 0x00010000 && version != 0x74727565) return tr("OpenType mit CFF-Umrissen lässt sich in Word nicht einbetten")
        val tables = ((ttf[4].toInt() and 0xFF) shl 8) or (ttf[5].toInt() and 0xFF)
        for (i in 0 until tables) {
            val o = 12 + i * 16
            if (o + 16 > ttf.size) break
            if (String(ttf, o, 4, Charsets.ISO_8859_1) == "OS/2") {
                val at = ((ttf[o + 8].toInt() and 0xFF) shl 24) or ((ttf[o + 9].toInt() and 0xFF) shl 16) or ((ttf[o + 10].toInt() and 0xFF) shl 8) or (ttf[o + 11].toInt() and 0xFF)
                if (at + 10 > ttf.size) return null
                val fsType = ((ttf[at + 8].toInt() and 0xFF) shl 8) or (ttf[at + 9].toInt() and 0xFF)
                if (fsType and 0x000F == 0x0002) return tr("die Lizenz der Schrift verbietet das Einbetten")
                if (fsType and 0x0200 != 0) return tr("die Lizenz erlaubt nur Bitmaps")
            }
        }
        return null
    }

    /**
     * Embeds the installed fonts of the Word file [file] into [out] (obfuscated as Word does, ECMA-376 17.8.1), so it
     * looks the same on computers without them. Fonts every computer has (Arial, Times New Roman …) are left out.
     */
    fun embed(file: File, out: File): Embedded {
        require(ext(file.name) in setOf("docx", "docm")) { tr("Einbetten geht für Word-Dateien.") }
        val bytes = file.readBytes()
        val p = parts(bytes)
        val used = officeFonts(file.name, bytes).filter { !it.value }.keys
        val done = mutableListOf<String>()
        val skipped = mutableListOf<Pair<String, String>>()
        var table = text(p, "word/fontTable.xml").ifEmpty {
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:fonts xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"></w:fonts>"
        }
        if ("xmlns:r=" !in table) table = table.replaceFirst("<w:fonts ", "<w:fonts xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" ")
        val rels = StringBuilder(text(p, "word/_rels/fontTable.xml.rels").substringBefore("</Relationships>").ifEmpty {
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        })
        var n = p.keys.count { it.startsWith("word/fonts/") } + 1
        for (font in used) {
            if (font.lowercase() in setOf("arial", "times new roman", "courier new", "calibri", "cambria", "segoe ui", "verdana", "tahoma", "georgia")) continue
            val ttf = SystemFonts.fileOf(font)?.readBytes()
            if (ttf == null) { skipped += font to tr("nicht auf diesem Computer installiert"); continue }
            val refused = embeddable(ttf)
            if (refused != null) { skipped += font to refused; continue }
            val guid = UUID.randomUUID().toString().uppercase()
            val key = guid.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.reversed()
            val data = ttf.copyOf()
            for (i in 0 until minOf(32, data.size)) data[i] = (data[i].toInt() xor key[i % 16].toInt()).toByte()
            val part = "fonts/font$n.odttf"
            p["word/$part"] = data
            val rid = "rIdLoraFont$n"
            rels.append("<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/font\" Target=\"$part\"/>")
            val embed = "<w:embedRegular r:id=\"$rid\" w:fontKey=\"{$guid}\"/>"
            val existing = Regex("<w:font w:name=\"" + Regex.escape(font) + "\">(.*?)</w:font>", RegexOption.DOT_MATCHES_ALL)
            table = if (existing.containsMatchIn(table)) existing.replace(table) { m -> m.value.replace("</w:font>", "$embed</w:font>") }
            else table.replace("</w:fonts>", "<w:font w:name=\"${font.replace("&", "&amp;").replace("\"", "&quot;")}\">$embed</w:font></w:fonts>")
            done += font
            n++
        }
        if (done.isNotEmpty()) {
            p["word/fontTable.xml"] = table.toByteArray(Charsets.UTF_8)
            p["word/_rels/fontTable.xml.rels"] = (rels.toString() + "</Relationships>").toByteArray(Charsets.UTF_8)
            var types = text(p, "[Content_Types].xml")
            if ("Extension=\"odttf\"" !in types) types = types.replace("</Types>", "<Default Extension=\"odttf\" ContentType=\"application/vnd.openxmlformats-officedocument.obfuscatedFont\"/></Types>")
            if ("/word/fontTable.xml" !in types) types = types.replace("</Types>", "<Override PartName=\"/word/fontTable.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml\"/></Types>")
            p["[Content_Types].xml"] = types.toByteArray(Charsets.UTF_8)
            // The document must point to its font table.
            val docRels = text(p, "word/_rels/document.xml.rels")
            if (docRels.isNotEmpty() && "relationships/fontTable" !in docRels) {
                p["word/_rels/document.xml.rels"] = docRels.replace("</Relationships>", "<Relationship Id=\"rIdLoraFontTable\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable\" Target=\"fontTable.xml\"/></Relationships>").toByteArray(Charsets.UTF_8)
            }
            text(p, "word/settings.xml").takeIf { it.isNotEmpty() && "embedTrueTypeFonts" !in it }?.let { s ->
                p["word/settings.xml"] = Regex("(<w:settings[^>]*>)").replaceFirst(s, "$1<w:embedTrueTypeFonts/>").toByteArray(Charsets.UTF_8)
            }
        }
        write(p, out)
        return Embedded(done, skipped)
    }

    /** Reads the font back from an embedded part (for the check): undoes the obfuscation with [fontKey]. */
    fun deobfuscate(data: ByteArray, fontKey: String): ByteArray {
        val key = fontKey.trim('{', '}').replace("-", "").chunked(2).map { it.toInt(16).toByte() }.reversed()
        return data.copyOf().also { d -> for (i in 0 until minOf(32, d.size)) d[i] = (d[i].toInt() xor key[i % 16].toInt()).toByte() }
    }
}
