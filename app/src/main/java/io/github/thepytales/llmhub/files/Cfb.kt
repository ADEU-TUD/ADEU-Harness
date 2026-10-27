package io.github.thepytales.llmhub.files

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal reader for OLE2 compound files (Outlook .msg, Word .doc, Excel .xls, PowerPoint .ppt): the directory tree
 * and the content of its streams, including the mini stream. Read-only; defensive against broken chains.
 */
class Cfb private constructor(private val data: ByteArray) {
    class Entry(val name: String, val type: Int, val start: Int, val size: Long, internal val left: Int, internal val right: Int, internal val child: Int) {
        val isStorage get() = type == 1 || type == 5
        val isStream get() = type == 2
        internal val children = mutableListOf<Entry>()
    }

    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private val sectorSize = 1 shl buf.getShort(30).toInt()
    private val miniSectorSize = 1 shl buf.getShort(32).toInt()
    private val miniCutoff = buf.getInt(56).toLong() and 0xFFFFFFFFL
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<Entry>
    private val miniStream: ByteArray

    val root: Entry

    init {
        require(sectorSize in setOf(512, 4096) && miniSectorSize == 64) { "Kein gültiges OLE-Dokument." }
        // DIFAT: the first 109 FAT sectors in the header, the rest in a chain of DIFAT sectors.
        val fatSectors = mutableListOf<Int>()
        for (i in 0 until 109) buf.getInt(76 + i * 4).takeIf { it >= 0 }?.let(fatSectors::add)
        var difat = buf.getInt(68)
        var guard = 0
        while (difat >= 0 && guard++ < 10_000) {
            val off = offset(difat)
            if (off + sectorSize > data.size) break
            for (i in 0 until sectorSize / 4 - 1) buf.getInt(off + i * 4).takeIf { it >= 0 }?.let(fatSectors::add)
            difat = buf.getInt(off + sectorSize - 4)
        }
        fat = IntArray(fatSectors.size * sectorSize / 4) { i ->
            val off = offset(fatSectors[i / (sectorSize / 4)]) + (i % (sectorSize / 4)) * 4
            if (off + 4 <= data.size) buf.getInt(off) else END
        }
        val dir = chain(buf.getInt(48), Long.MAX_VALUE)
        val b = ByteBuffer.wrap(dir).order(ByteOrder.LITTLE_ENDIAN)
        entries = (0 until dir.size / 128).map { i ->
            val nameLen = (b.getShort(i * 128 + 64).toInt() and 0xFFFF).coerceIn(0, 64)
            val name = String(dir, i * 128, maxOf(0, nameLen - 2), Charsets.UTF_16LE)
            Entry(
                name, dir[i * 128 + 66].toInt(), b.getInt(i * 128 + 116), b.getLong(i * 128 + 120).let { if (sectorSize == 512) it and 0xFFFFFFFFL else it },
                b.getInt(i * 128 + 68), b.getInt(i * 128 + 72), b.getInt(i * 128 + 76),
            )
        }
        root = entries.firstOrNull { it.type == 5 } ?: throw IllegalArgumentException("Kein gültiges OLE-Dokument.")
        val miniFatBytes = chain(buf.getInt(60), Long.MAX_VALUE)
        miniFat = IntArray(miniFatBytes.size / 4) { ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN).getInt(it * 4) }
        miniStream = chain(root.start, root.size)
        // Children of each storage: the red-black tree below its child entry.
        val seen = HashSet<Int>()
        fun collect(parent: Entry, index: Int) {
            if (index < 0 || index >= entries.size || !seen.add(index)) return
            val e = entries[index]
            parent.children += e
            collect(parent, e.left)
            collect(parent, e.right)
            if (e.isStorage) collect(e, e.child)
        }
        collect(root, root.child)
    }

    private fun offset(sector: Int) = (sector + 1) * sectorSize

    private fun chain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = start
        var guard = 0
        while (s >= 0 && guard++ < 200_000 && out.size() < size) {
            val off = offset(s)
            if (off >= data.size) break
            out.write(data, off, minOf(sectorSize, data.size - off))
            s = if (s < fat.size) fat[s] else END
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }

    fun children(e: Entry): List<Entry> = e.children

    fun child(e: Entry, name: String): Entry? = e.children.firstOrNull { it.name.equals(name, true) }

    /** Content of the stream [e]. */
    fun read(e: Entry): ByteArray {
        if (!e.isStream) return ByteArray(0)
        if (e.size >= miniCutoff) return chain(e.start, e.size)
        val out = java.io.ByteArrayOutputStream()
        var s = e.start
        var guard = 0
        while (s >= 0 && guard++ < 1_000_000 && out.size() < e.size) {
            val off = s * miniSectorSize
            if (off >= miniStream.size) break
            out.write(miniStream, off, minOf(miniSectorSize, miniStream.size - off))
            s = if (s < miniFat.size) miniFat[s] else END
        }
        return out.toByteArray().let { if (it.size > e.size) it.copyOf(e.size.toInt()) else it }
    }

    /** The stream [name] directly below the root, or null. */
    fun stream(name: String): ByteArray? = child(root, name)?.takeIf { it.isStream }?.let(::read)

    companion object {
        private const val END = -2

        fun isCfb(bytes: ByteArray): Boolean = bytes.size >= 512 &&
            bytes.copyOf(8).contentEquals(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))

        fun open(bytes: ByteArray): Cfb {
            require(isCfb(bytes)) { "Kein OLE-Dokument (Outlook/Office 97–2003)." }
            return Cfb(bytes)
        }
    }
}
