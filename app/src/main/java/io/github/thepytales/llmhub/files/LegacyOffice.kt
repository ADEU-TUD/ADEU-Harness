package io.github.thepytales.llmhub.files

import io.github.thepytales.llmhub.docs.OfficeImport
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Text of old Office files (Word .doc, Excel .xls, PowerPoint .ppt, 97–2003). If LibreOffice is installed it converts
 * the file to the new format first (best result); otherwise the text comes from the binary format itself: the piece
 * table of Word, the cells of Excel (BIFF8) and the text atoms of PowerPoint, with a scan for text runs as last resort.
 */
object LegacyOffice {
    private val CP1252: Charset = Charset.forName("windows-1252")

    fun text(name: String, bytes: ByteArray): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        converted(name, ext, bytes)?.let { return it }
        val parsed = runCatching {
            val cfb = Cfb.open(bytes)
            when (ext) {
                "doc", "dot" -> word(cfb)
                "xls", "xlt" -> excel(cfb)
                "ppt", "pps", "pot" -> powerPoint(cfb)
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val label = when (ext) { "doc", "dot" -> "Word"; "xls", "xlt" -> "Excel"; else -> "PowerPoint" }
        if (parsed != null) {
            return "[Hinweis: Text aus dem alten $label-Format (.$ext) gelesen – ohne Formatierung" +
                (if (ext.startsWith("x")) "; Formeln als berechnete Werte" else "; Tabellen und Bilder fehlen") +
                ". Für das Original in Office öffnen oder als ${if (ext.startsWith("x")) ".xlsx" else if (ext.startsWith("p")) ".pptx" else ".docx"} speichern.]\n\n$parsed"
        }
        val runs = textRuns(bytes)
        if (runs.isBlank()) throw IllegalArgumentException("„$name“: Aus dieser alten $label-Datei ließ sich kein Text gewinnen.")
        return "[Hinweis: Text heuristisch aus dem alten $label-Format (.$ext) gewonnen – Reihenfolge, Formatierung und einzelne Stellen " +
            "können fehlen oder Reste enthalten.]\n\n$runs"
    }

    // ------------------------------------------------------------------ LibreOffice

    private val soffice: String? by lazy {
        val candidates = listOfNotNull(
            System.getenv("ProgramFiles")?.let { "$it\\LibreOffice\\program\\soffice.exe" },
            System.getenv("ProgramFiles(x86)")?.let { "$it\\LibreOffice\\program\\soffice.exe" },
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "/usr/bin/soffice", "/usr/bin/libreoffice", "/Applications/LibreOffice.app/Contents/MacOS/soffice",
        ) + System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
            .flatMap { listOf("$it${File.separator}soffice", "$it${File.separator}soffice.exe") }
        candidates.firstOrNull { runCatching { File(it).let { f -> f.isFile && f.canExecute() } }.getOrDefault(false) }
    }

    /** Converted with LibreOffice into .docx/.xlsx/.pptx and read like those; null without LibreOffice. */
    private fun converted(name: String, ext: String, bytes: ByteArray): String? {
        val exe = runCatching { soffice }.getOrNull() ?: return null
        val target = when (ext) { "doc", "dot" -> "docx"; "xls", "xlt" -> "xlsx"; "ppt", "pps", "pot" -> "pptx"; else -> return null }
        val dir = runCatching { kotlin.io.path.createTempDirectory("adeu-office").toFile() }.getOrNull() ?: return null
        return try {
            val src = File(dir, "datei.$ext").apply { writeBytes(bytes) }
            val p = ProcessBuilder(exe, "--headless", "--norestore", "--convert-to", target, "--outdir", dir.absolutePath, src.absolutePath)
                .redirectErrorStream(true).start()
            p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(90, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
            val out = File(dir, "datei.$target").takeIf { it.isFile } ?: return null
            val imported = OfficeImport.read(out.name, out.readBytes()) ?: return null
            val text = imported.tabs.joinToString("\n\n") { (t, md) -> if (imported.tabs.size > 1) "## $t\n\n$md" else md }
            "[Hinweis: „$name“ wurde mit LibreOffice in .$target umgewandelt und so gelesen.]\n\n$text"
        } catch (e: Exception) {
            null
        } finally {
            dir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ Word 97–2003

    private fun word(cfb: Cfb): String? {
        val doc = cfb.stream("WordDocument") ?: return null
        val b = ByteBuffer.wrap(doc).order(ByteOrder.LITTLE_ENDIAN)
        if (doc.size < 0x01AA) return null
        val flags = b.getShort(0x0A).toInt()
        val table = cfb.stream(if (flags and 0x0200 != 0) "1Table" else "0Table") ?: return null
        val ccpText = b.getInt(0x4C)
        val fcClx = b.getInt(0x01A2)
        val lcbClx = b.getInt(0x01A6)
        if (fcClx < 0 || lcbClx <= 0 || fcClx + lcbClx > table.size) return null
        val t = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        var pos = fcClx
        // Skip Prc blocks (property modifiers), then the piece table.
        while (pos < fcClx + lcbClx && table[pos].toInt() == 1) pos += 3 + (t.getShort(pos + 1).toInt() and 0xFFFF)
        if (pos >= fcClx + lcbClx || table[pos].toInt() != 2) return null
        val lcb = t.getInt(pos + 1)
        val plc = pos + 5
        val n = (lcb - 4) / 12
        if (n <= 0 || plc + lcb > table.size) return null
        val sb = StringBuilder()
        for (i in 0 until n) {
            val cpStart = t.getInt(plc + i * 4)
            val cpEnd = t.getInt(plc + (i + 1) * 4)
            val pcd = plc + (n + 1) * 4 + i * 8
            val fcRaw = t.getInt(pcd + 2)
            val compressed = fcRaw and 0x40000000 != 0
            val fc = fcRaw and 0x3FFFFFFF
            val len = (cpEnd - cpStart).coerceAtLeast(0)
            if (compressed) {
                val start = fc / 2
                if (start + len <= doc.size) sb.append(String(doc, start, len, CP1252))
            } else if (fc + len * 2 <= doc.size) sb.append(String(doc, fc, len * 2, Charsets.UTF_16LE))
            if (sb.length >= ccpText && ccpText > 0) break
        }
        val text = if (ccpText in 1..sb.length) sb.substring(0, ccpText) else sb.toString()
        return cleanWord(text)
    }

    /** Word's special characters: paragraph and cell marks, fields (code hidden, result kept), objects. */
    private fun cleanWord(text: String): String {
        val sb = StringBuilder()
        var fieldDepth = 0
        val inCode = ArrayDeque<Boolean>()
        for (c in text) {
            when (c.code) {
                0x13 -> { fieldDepth++; inCode.addLast(true) }
                0x14 -> if (inCode.isNotEmpty()) { inCode.removeLast(); inCode.addLast(false) }
                0x15 -> if (fieldDepth > 0) { fieldDepth--; inCode.removeLastOrNull() }
                else -> {
                    if (inCode.lastOrNull() == true) continue
                    when (c.code) {
                        0x0D -> sb.append('\n')
                        0x07 -> sb.append('\t')
                        0x0B, 0x0C -> sb.append('\n')
                        0x1E -> sb.append('-')
                        0x01, 0x08, 0x1F, 0x05, 0x02 -> {}
                        else -> if (c.code >= 0x20 || c == '\t') sb.append(c)
                    }
                }
            }
        }
        return sb.toString().lines().joinToString("\n") { it.trimEnd().replace(Regex("\t+$"), "") }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // ------------------------------------------------------------------ Excel 97–2003 (BIFF8)

    private fun excel(cfb: Cfb): String? {
        val wb = cfb.stream("Workbook") ?: cfb.stream("Book") ?: return null
        val b = ByteBuffer.wrap(wb).order(ByteOrder.LITTLE_ENDIAN)
        val sheets = mutableListOf<Pair<String, Int>>()
        val sst = mutableListOf<String>()
        var pos = 0
        // Globals: sheet names and the shared strings.
        while (pos + 4 <= wb.size) {
            val type = b.getShort(pos).toInt() and 0xFFFF
            val len = b.getShort(pos + 2).toInt() and 0xFFFF
            val data = pos + 4
            if (data + len > wb.size) break
            when (type) {
                0x0085 -> {
                    val offset = b.getInt(data)
                    val cch = wb[data + 6].toInt() and 0xFF
                    val high = wb[data + 7].toInt() and 1
                    val name = if (high == 1) String(wb, data + 8, minOf(cch * 2, len - 8), Charsets.UTF_16LE) else String(wb, data + 8, minOf(cch, len - 8), CP1252)
                    sheets += name to offset
                }
                0x00FC -> {
                    val chunks = mutableListOf(wb.copyOfRange(data, data + len))
                    var next = data + len
                    while (next + 4 <= wb.size && (b.getShort(next).toInt() and 0xFFFF) == 0x003C) {
                        val l = b.getShort(next + 2).toInt() and 0xFFFF
                        if (next + 4 + l > wb.size) break
                        chunks += wb.copyOfRange(next + 4, next + 4 + l)
                        next += 4 + l
                    }
                    runCatching { readSst(chunks, sst) }
                }
                0x000A -> if (sheets.isNotEmpty()) break // EOF of the globals
            }
            pos = data + len
        }
        if (sheets.isEmpty()) return null
        val out = StringBuilder()
        for ((name, offset) in sheets) {
            val cells = sortedMapOf<Int, java.util.TreeMap<Int, String>>()
            fun put(r: Int, c: Int, v: String) { if (r < 5000 && c < 100) cells.getOrPut(r) { java.util.TreeMap() }[c] = v }
            var p = offset
            var lastFormula: Pair<Int, Int>? = null
            while (p + 4 <= wb.size) {
                val type = b.getShort(p).toInt() and 0xFFFF
                val len = b.getShort(p + 2).toInt() and 0xFFFF
                val d = p + 4
                if (d + len > wb.size) break
                when (type) {
                    0x00FD -> put(b.getShort(d).toInt() and 0xFFFF, b.getShort(d + 2).toInt() and 0xFFFF, sst.getOrElse(b.getInt(d + 6)) { "" })
                    0x0203 -> put(b.getShort(d).toInt() and 0xFFFF, b.getShort(d + 2).toInt() and 0xFFFF, number(b.getDouble(d + 6)))
                    0x027E -> put(b.getShort(d).toInt() and 0xFFFF, b.getShort(d + 2).toInt() and 0xFFFF, number(rk(b.getInt(d + 6))))
                    0x00BD -> {
                        val r = b.getShort(d).toInt() and 0xFFFF
                        val first = b.getShort(d + 2).toInt() and 0xFFFF
                        val count = (len - 6) / 6
                        for (k in 0 until count) put(r, first + k, number(rk(b.getInt(d + 4 + k * 6 + 2))))
                    }
                    0x0204 -> {
                        val cch = b.getShort(d + 6).toInt() and 0xFFFF
                        val high = wb[d + 8].toInt() and 1
                        put(b.getShort(d).toInt() and 0xFFFF, b.getShort(d + 2).toInt() and 0xFFFF,
                            if (high == 1) String(wb, d + 9, minOf(cch * 2, len - 9), Charsets.UTF_16LE) else String(wb, d + 9, minOf(cch, len - 9), CP1252))
                    }
                    0x0006 -> {
                        val r = b.getShort(d).toInt() and 0xFFFF
                        val c = b.getShort(d + 2).toInt() and 0xFFFF
                        val marker = b.getShort(d + 12).toInt() and 0xFFFF
                        if (marker != 0xFFFF) put(r, c, number(b.getDouble(d + 6)))
                        else when (wb[d + 6].toInt()) {
                            0 -> lastFormula = r to c // string result follows in a STRING record
                            1 -> put(r, c, if (wb[d + 8].toInt() != 0) "WAHR" else "FALSCH")
                        }
                    }
                    0x0207 -> lastFormula?.let { (r, c) ->
                        val cch = b.getShort(d).toInt() and 0xFFFF
                        val high = wb[d + 2].toInt() and 1
                        put(r, c, if (high == 1) String(wb, d + 3, minOf(cch * 2, len - 3), Charsets.UTF_16LE) else String(wb, d + 3, minOf(cch, len - 3), CP1252))
                        lastFormula = null
                    }
                    0x000A -> break
                }
                p = d + len
            }
            if (cells.isEmpty()) continue
            val width = cells.values.maxOf { it.lastKey() } + 1
            val firstRow = cells.firstKey()
            out.append("## ").append(name).append("\n\n")
            var header = true
            for (r in firstRow..cells.lastKey()) {
                val row = cells[r]
                val line = (0 until width).joinToString(" | ") { c -> row?.get(c).orEmpty().replace("|", "\\|").replace("\n", " ") }
                out.append("| ").append(line).append(" |\n")
                if (header) { out.append("|").append(" --- |".repeat(width)).append('\n'); header = false }
            }
            out.append('\n')
        }
        return out.toString().trim()
    }

    private fun rk(v: Int): Double {
        val n = if (v and 2 != 0) (v shr 2).toDouble() else java.lang.Double.longBitsToDouble((v.toLong() and 0xFFFFFFFCL) shl 32)
        return if (v and 1 != 0) n / 100 else n
    }

    private fun number(d: Double): String = if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString().replace('.', ',')

    /** Shared string table; strings may continue in the next CONTINUE record with a new flags byte. */
    private fun readSst(chunks: List<ByteArray>, out: MutableList<String>) {
        var ci = 0
        var pos = 0
        fun ensure() { while (ci < chunks.size && pos >= chunks[ci].size) { ci++; pos = 0 } }
        fun u8(): Int { ensure(); return chunks[ci][pos++].toInt() and 0xFF }
        fun u16() = u8() or (u8() shl 8)
        fun u32() = u16() or (u16() shl 16)
        fun skip(n: Int) { repeat(n) { u8() } }
        u32()
        val unique = u32()
        repeat(unique) {
            if (ci >= chunks.size) return
            val cch = u16()
            val flags = u8()
            var high = flags and 1
            val runs = if (flags and 8 != 0) u16() else 0
            val ext = if (flags and 4 != 0) u32() else 0
            val sb = StringBuilder()
            var left = cch
            while (left > 0) {
                if (ci < chunks.size && pos >= chunks[ci].size) { ci++; pos = 0; if (ci >= chunks.size) break; high = chunks[ci][pos++].toInt() and 1 }
                if (ci >= chunks.size) break
                val avail = (chunks[ci].size - pos) / (if (high == 1) 2 else 1)
                val take = minOf(left, avail)
                if (take <= 0) { pos = chunks[ci].size; continue }
                sb.append(if (high == 1) String(chunks[ci], pos, take * 2, Charsets.UTF_16LE) else String(chunks[ci], pos, take, CP1252))
                pos += take * (if (high == 1) 2 else 1)
                left -= take
            }
            skip(runs * 4 + ext)
            out += sb.toString()
        }
    }

    // ------------------------------------------------------------------ PowerPoint 97–2003

    private fun powerPoint(cfb: Cfb): String? {
        val doc = cfb.stream("PowerPoint Document") ?: return null
        val b = ByteBuffer.wrap(doc).order(ByteOrder.LITTLE_ENDIAN)
        val out = StringBuilder()
        val seen = HashSet<String>()
        var slide = 0
        var listed = false
        fun walk(from: Int, to: Int, depth: Int, inList: Boolean) {
            var p = from
            while (p + 8 <= to && depth < 30) {
                val verInst = b.getShort(p).toInt() and 0xFFFF
                val type = b.getShort(p + 2).toInt() and 0xFFFF
                val len = b.getInt(p + 4)
                val d = p + 8
                if (len < 0 || d + len > to) break
                when {
                    type == 0x03F8 -> {} // main master: only placeholder texts
                    // Slide list with text (instance 0 = slides): a persist atom starts the next slide.
                    type == 0x0FF0 -> walk(d, d + len, depth + 1, verInst ushr 4 == 0)
                    type == 0x03F3 && inList -> { slide++; listed = true; out.append("\n## Folie ").append(slide).append("\n\n") }
                    type == 0x03EE -> {
                        if (!listed) { slide++; out.append("\n## Folie ").append(slide).append("\n\n") }
                        walk(d, d + len, depth + 1, false)
                    }
                    type == 0x03F0 -> {
                        val mark = out.length
                        out.append("\nNotizen:\n")
                        walk(d, d + len, depth + 1, false)
                        if (out.length == mark + 10) out.setLength(mark)
                    }
                    type == 0x0FA0 || type == 0x0FA8 -> {
                        val text = (if (type == 0x0FA0) String(doc, d, len, Charsets.UTF_16LE) else String(doc, d, len, Charsets.ISO_8859_1))
                            .replace('\r', '\n').replace('\u000B', '\n').trim()
                        if (text.isNotEmpty() && seen.add(text)) out.append(text).append("\n\n")
                    }
                    verInst and 0x000F == 0x000F -> walk(d, d + len, depth + 1, inList)
                }
                p = d + len
            }
        }
        walk(0, doc.size, 0, false)
        return out.toString().replace(Regex("(## Folie \\d+\n\n)(?=\n## Folie|\\s*$)"), "").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // ------------------------------------------------------------------ last resort

    /** Runs of readable text (UTF-16LE and 8-bit) in a binary file, without obvious garbage. */
    fun textRuns(bytes: ByteArray, min: Int = 6): String {
        val runs = mutableListOf<Pair<Int, String>>()
        fun good(c: Char) = c.isLetterOrDigit() || c in " .,;:!?-–—()[]\"'„“”‚‘’/%&+*=#@€§\t\n\r"
        // UTF-16LE
        var i = 0
        val sb = StringBuilder()
        var start = 0
        while (i + 1 < bytes.size) {
            val c = ((bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)).toChar()
            if (good(c) && (c.code < 0x3000)) { if (sb.isEmpty()) start = i; sb.append(c); i += 2 }
            else { if (sb.length >= min) runs += start to sb.toString(); sb.clear(); i++ }
        }
        if (sb.length >= min) runs += start to sb.toString()
        sb.clear()
        // 8-bit
        val text = String(bytes, CP1252)
        for ((k, c) in text.withIndex()) {
            if (good(c)) { if (sb.isEmpty()) start = k; sb.append(c) }
            else { if (sb.length >= min) runs += start to sb.toString(); sb.clear() }
        }
        if (sb.length >= min) runs += start to sb.toString()
        return runs.sortedBy { it.first }.map { it.second.trim() }
            .filter { r -> r.length >= min && r.count { it.isLetter() } * 2 >= r.length && Regex("[aeiouäöüAEIOU]").containsMatchIn(r) && !Regex("(.)\\1{5,}").containsMatchIn(r) }
            .distinct().joinToString("\n").take(200_000)
    }
}
