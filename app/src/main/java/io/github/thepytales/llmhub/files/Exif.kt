package io.github.thepytales.llmhub.files

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What a photo says about itself (AP-100): orientation, the time it was taken, whether it carries a location and the
 * camera. Reads the EXIF block of JPEG files (APP1), of HEIC/HEIF files (iPhone; the „Exif“ item of the ISO-BMFF
 * container, plus its rotation „irot“ and mirror „imir“, which take precedence there) and of PNG/WebP (eXIf/EXIF
 * chunks). Read-only and tolerant: a broken block gives what could be read.
 */
object Exif {
    data class Info(
        /** EXIF orientation 1–8 (1 = upright). */
        val orientation: Int = 1,
        val taken: LocalDateTime? = null,
        val hasGps: Boolean = false,
        val camera: String = "",
        /** Further metadata besides the orientation (camera, date, location, comments …) – what „entfernen“ takes away. */
        val hasMetadata: Boolean = false,
    )

    private val EXIF_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun read(bytes: ByteArray): Info = runCatching {
        when {
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> jpeg(bytes)
            isHeif(bytes) -> heif(bytes)
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> png(bytes)
            bytes.size > 12 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> webp(bytes)
            else -> Info()
        }
    }.getOrDefault(Info())

    /** ISO-BMFF image (HEIC, HEIF, AVIF): „ftyp“ with an image brand. */
    fun isHeif(bytes: ByteArray): Boolean {
        if (bytes.size < 12 || String(bytes, 4, 4, Charsets.ISO_8859_1) != "ftyp") return false
        val size = be32(bytes, 0).toInt().coerceIn(8, minOf(bytes.size, 256))
        val brands = String(bytes, 8, size - 8, Charsets.ISO_8859_1)
        return listOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "avif").any { it in brands }
    }

    // ── JPEG ──

    private fun jpeg(b: ByteArray): Info {
        var i = 2
        var extra = false
        while (i + 4 <= b.size) {
            if (b[i] != 0xFF.toByte()) return Info(hasMetadata = extra)
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            val start = i + 4
            if (marker == 0xE1 && len >= 8 && String(b, start, 4, Charsets.ISO_8859_1) == "Exif") {
                return tiff(b, start + 6, start + len - 2)
            }
            // XMP, IPTC (APP13) and comments are metadata too.
            if (marker == 0xE1 || marker == 0xED || marker == 0xFE) extra = true
            i = start + len - 2
        }
        return Info(hasMetadata = extra)
    }

    // ── TIFF structure of the EXIF block ──

    private fun tiff(b: ByteArray, start: Int, end: Int): Info {
        if (start + 8 > b.size) return Info()
        val bb = ByteBuffer.wrap(b, 0, minOf(end, b.size))
        bb.order(if (b[start] == 'I'.code.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        fun u16(o: Int) = bb.getShort(start + o).toInt() and 0xFFFF
        fun u32(o: Int) = bb.getInt(start + o).toLong() and 0xFFFFFFFFL
        var orientation = 1
        var taken: String? = null
        var gps = false
        var make = ""
        var model = ""
        var tags = 0
        fun ascii(type: Int, count: Long, valueOff: Int): String {
            if (type != 2) return ""
            val off = if (count <= 4) valueOff else u32(valueOff).toInt()
            if (off < 0 || start + off + count > b.size) return ""
            return String(b, start + off, count.toInt().coerceAtMost(200), Charsets.ISO_8859_1).trimEnd('\u0000', ' ')
        }
        fun ifd(at: Long, depth: Int) {
            if (depth > 3 || at <= 0 || start + at + 2 > end) return
            val n = u16(at.toInt())
            for (k in 0 until minOf(n, 500)) {
                val e = at.toInt() + 2 + k * 12
                if (start + e + 12 > end) return
                val tag = u16(e)
                val type = u16(e + 2)
                val count = u32(e + 4)
                tags++
                when (tag) {
                    0x0112 -> orientation = u16(e + 8).takeIf { it in 1..8 } ?: 1
                    0x9003 -> taken = ascii(type, count, e + 8).ifBlank { null } ?: taken
                    0x0132 -> if (taken == null) taken = ascii(type, count, e + 8).ifBlank { null }
                    0x010F -> make = ascii(type, count, e + 8)
                    0x0110 -> model = ascii(type, count, e + 8)
                    0x8769 -> ifd(u32(e + 8), depth + 1)
                    0x8825 -> {
                        // A GPS block with at least one coordinate.
                        val g = u32(e + 8)
                        if (start + g + 2 <= end && u16(g.toInt()) > 0) gps = true
                    }
                }
            }
        }
        ifd(u32(4), 0)
        val time = taken?.let { t -> runCatching { LocalDateTime.parse(t.take(19), EXIF_TIME) }.getOrNull() }
        val camera = listOf(make, model).filter { it.isNotBlank() }.distinct().joinToString(" ").let { c ->
            if (make.isNotBlank() && model.startsWith(make)) model else c
        }
        return Info(orientation, time, gps, camera, hasMetadata = tags > 1 || gps)
    }

    // ── HEIF (ISO-BMFF) ──

    private fun be32(b: ByteArray, o: Int): Long = ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
        ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun be16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun beN(b: ByteArray, o: Int, n: Int): Long {
        var v = 0L
        for (k in 0 until n) v = (v shl 8) or (b[o + k].toLong() and 0xFF)
        return v
    }

    private class Box(val type: String, val start: Int, val end: Int)

    /** The boxes between [from] and [to]. */
    private fun boxes(b: ByteArray, from: Int, to: Int): List<Box> {
        val out = mutableListOf<Box>()
        var i = from
        while (i + 8 <= to) {
            var size = be32(b, i)
            val type = String(b, i + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L && i + 16 <= to) { size = beN(b, i + 8, 8); header = 16 }
            if (size == 0L) size = (to - i).toLong()
            if (size < header || i + size > to) break
            out += Box(type, i + header, (i + size).toInt())
            i += size.toInt()
        }
        return out
    }

    private fun heif(b: ByteArray): Info {
        val meta = boxes(b, 0, b.size).firstOrNull { it.type == "meta" } ?: return Info()
        val inner = boxes(b, meta.start + 4, meta.end)
        val primary = inner.firstOrNull { it.type == "pitm" }?.let { p -> if (b[p.start] == 0.toByte()) be16(b, p.start + 4) else be32(b, p.start + 4).toInt() }
        // Item infos: the id of the Exif item.
        var exifId: Int? = null
        inner.firstOrNull { it.type == "iinf" }?.let { iinf ->
            val v = b[iinf.start].toInt()
            val first = iinf.start + 4 + if (v == 0) 2 else 4
            for (infe in boxes(b, first, iinf.end).filter { it.type == "infe" }) {
                val ver = b[infe.start].toInt()
                if (ver < 2) continue
                val id = if (ver == 2) be16(b, infe.start + 4) else be32(b, infe.start + 4).toInt()
                val typeAt = infe.start + 4 + (if (ver == 2) 2 else 4) + 2
                if (String(b, typeAt, 4, Charsets.ISO_8859_1) == "Exif") exifId = id
            }
        }
        var info = Info()
        val id = exifId
        if (id != null) inner.firstOrNull { it.type == "iloc" }?.let { iloc -> locate(b, iloc, id) }?.let { (off, len) ->
            if (off + len <= b.size && len > 4) {
                val skip = be32(b, off).toInt()
                val tiffStart = off + 4 + skip
                // Some writers put „Exif\0\0“ before the TIFF header without counting it.
                val s = if (String(b, tiffStart, 4, Charsets.ISO_8859_1) == "Exif") tiffStart + 6 else tiffStart
                info = tiff(b, s, off + len)
            }
        }
        // Rotation and mirror of the primary image (properties „irot“, „imir“) – they decide in HEIF, not the EXIF tag.
        val iprp = inner.firstOrNull { it.type == "iprp" }
        if (iprp != null && primary != null) {
            val parts = boxes(b, iprp.start, iprp.end)
            val props = parts.firstOrNull { it.type == "ipco" }?.let { boxes(b, it.start, it.end) }.orEmpty()
            val used = parts.firstOrNull { it.type == "ipma" }?.let { associations(b, it, primary) }.orEmpty()
            var rot = 0
            var mirror: Int? = null
            for (index in used) {
                val p = props.getOrNull(index - 1) ?: continue
                when (p.type) {
                    "irot" -> rot = (b[p.start].toInt() and 3) * 90
                    "imir" -> mirror = b[p.start].toInt() and 1
                }
            }
            if (rot != 0 || mirror != null) info = info.copy(orientation = orientationOf(rot, mirror))
            else if (used.isNotEmpty()) info = info.copy(orientation = 1)
        }
        return info
    }

    /** EXIF orientation for a HEIF rotation (counter-clockwise, degrees) and mirror axis (0 vertical, 1 horizontal). */
    private fun orientationOf(ccw: Int, mirror: Int?): Int = when (mirror) {
        null -> when (ccw) { 90 -> 8; 180 -> 3; 270 -> 6; else -> 1 }
        // imir axis 0: mirrored top to bottom; 1: left to right (applied after the rotation).
        0 -> when (ccw) { 90 -> 7; 180 -> 2; 270 -> 5; else -> 4 }
        else -> when (ccw) { 90 -> 5; 180 -> 4; 270 -> 7; else -> 2 }
    }

    private fun associations(b: ByteArray, ipma: Box, item: Int): List<Int> {
        val version = b[ipma.start].toInt()
        val flags = b[ipma.start + 3].toInt() and 1
        var i = ipma.start + 4
        val count = be32(b, i).toInt(); i += 4
        repeat(count.coerceAtMost(10_000)) {
            val id = if (version < 1) be16(b, i).also { i += 2 } else be32(b, i).toInt().also { i += 4 }
            val n = b[i].toInt() and 0xFF; i++
            val list = mutableListOf<Int>()
            repeat(n) {
                val idx = if (flags == 1) (be16(b, i) and 0x7FFF).also { i += 2 } else (b[i].toInt() and 0x7F).also { i++ }
                list += idx
            }
            if (id == item) return list
        }
        return emptyList()
    }

    /** Offset and length of item [id] in the file (iloc versions 0–2, construction method 0, first extent). */
    private fun locate(b: ByteArray, iloc: Box, id: Int): Pair<Int, Int>? {
        val version = b[iloc.start].toInt()
        var i = iloc.start + 4
        val sizes = b[i].toInt() and 0xFF
        val sizes2 = b[i + 1].toInt() and 0xFF
        i += 2
        val offSize = sizes shr 4
        val lenSize = sizes and 15
        val baseSize = sizes2 shr 4
        val idxSize = if (version == 1 || version == 2) sizes2 and 15 else 0
        val count = if (version < 2) be16(b, i).also { i += 2 } else be32(b, i).toInt().also { i += 4 }
        repeat(count.coerceAtMost(10_000)) {
            val itemId = if (version < 2) be16(b, i).also { i += 2 } else be32(b, i).toInt().also { i += 4 }
            if (version == 1 || version == 2) i += 2
            i += 2 // data reference index
            val base = beN(b, i, baseSize); i += baseSize
            val extents = be16(b, i); i += 2
            var first: Pair<Int, Int>? = null
            repeat(extents) {
                i += idxSize
                val off = beN(b, i, offSize); i += offSize
                val len = beN(b, i, lenSize); i += lenSize
                if (first == null) first = (base + off).toInt() to len.toInt()
            }
            if (itemId == id) return first
        }
        return null
    }

    // ── PNG, WebP ──

    private fun png(b: ByteArray): Info {
        var i = 8
        var extra = false
        while (i + 12 <= b.size) {
            val len = be32(b, i).toInt()
            val type = String(b, i + 4, 4, Charsets.ISO_8859_1)
            if (type == "eXIf") return tiff(b, i + 8, i + 8 + len)
            if (type == "tEXt" || type == "iTXt" || type == "zTXt") extra = true
            if (type == "IEND" || len < 0) break
            i += 12 + len
        }
        return Info(hasMetadata = extra)
    }

    private fun webp(b: ByteArray): Info {
        var i = 12
        var extra = false
        while (i + 8 <= b.size) {
            val len = (b[i + 4].toInt() and 0xFF) or ((b[i + 5].toInt() and 0xFF) shl 8) or ((b[i + 6].toInt() and 0xFF) shl 16) or ((b[i + 7].toInt() and 0xFF) shl 24)
            val type = String(b, i, 4, Charsets.ISO_8859_1)
            if (type == "EXIF") {
                val s = if (String(b, i + 8, 4, Charsets.ISO_8859_1) == "Exif") i + 14 else i + 8
                return tiff(b, s, i + 8 + len)
            }
            if (type == "XMP ") extra = true
            if (len < 0) break
            i += 8 + len + (len and 1)
        }
        return Info(hasMetadata = extra)
    }
}
