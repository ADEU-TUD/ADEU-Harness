package io.github.thepytales.llmhub.files

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes OLE2 compound files (version 3, 512-byte sectors) – the container of password-protected Word, Excel and
 * PowerPoint files (AP-98). Streams under 4096 bytes go into the mini stream as the format demands; storages may
 * nest. Counterpart of the reader [Cfb].
 */
class CfbWriter {
    private sealed class Node(val name: String) {
        class Storage(name: String) : Node(name) { val children = mutableListOf<Node>() }
        class Stream(name: String, val data: ByteArray) : Node(name)
    }

    private val root = Node.Storage("Root Entry")

    /** Adds the stream [path] (storages separated by "/"), creating the storages on the way. */
    fun add(path: String, data: ByteArray): CfbWriter {
        val parts = path.split('/')
        var dir = root
        for (p in parts.dropLast(1)) {
            dir = dir.children.filterIsInstance<Node.Storage>().firstOrNull { it.name == p } ?: Node.Storage(p).also { dir.children += it }
        }
        dir.children += Node.Stream(parts.last(), data)
        return this
    }

    private class Dir(val node: Node, var left: Int = -1, var right: Int = -1, var child: Int = -1, var start: Int = END, var size: Long = 0)

    fun bytes(): ByteArray {
        // Directory in tree order; each storage's children as a balanced binary tree (ordered as the format says).
        val dirs = mutableListOf(Dir(root))
        fun order(a: Node, b: Node): Int = if (a.name.length != b.name.length) a.name.length - b.name.length else a.name.uppercase().compareTo(b.name.uppercase())
        fun place(storageIndex: Int) {
            val s = dirs[storageIndex].node as Node.Storage
            val sorted = s.children.sortedWith(::order)
            val indices = sorted.map { n -> dirs.size.also { dirs += Dir(n) } }
            fun build(from: Int, to: Int): Int {
                if (from > to) return -1
                val mid = (from + to) / 2
                val d = dirs[indices[mid]]
                d.left = build(from, mid - 1)
                d.right = build(mid + 1, to)
                return indices[mid]
            }
            dirs[storageIndex].child = build(0, indices.lastIndex)
            indices.forEach { if (dirs[it].node is Node.Storage) place(it) }
        }
        place(0)

        // Mini stream: the small streams, 64-byte mini sectors.
        val mini = java.io.ByteArrayOutputStream()
        val miniFat = mutableListOf<Int>()
        val big = mutableListOf<Dir>()
        for (d in dirs) {
            val n = d.node as? Node.Stream ?: continue
            d.size = n.data.size.toLong()
            if (n.data.isEmpty()) { d.start = END; continue }
            if (n.data.size < MINI_CUTOFF) {
                val first = miniFat.size
                val count = (n.data.size + 63) / 64
                for (k in 0 until count) miniFat += if (k == count - 1) END else first + k + 1
                d.start = first
                mini.write(n.data)
                mini.write(ByteArray(count * 64 - n.data.size))
            } else big += d
        }
        val miniBytes = mini.toByteArray()

        // Sectors: [FAT …][directory][mini FAT][mini stream][big streams]
        val dirSectors = (dirs.size + 3) / 4
        val miniFatSectors = (miniFat.size + 127) / 128
        val miniStreamSectors = (miniBytes.size + 511) / 512
        val bigSectors = big.sumOf { ((it.size + 511) / 512).toInt() }
        // FAT sectors beyond the 109 of the header are listed in DIFAT sectors (127 each).
        var fatSectors = 1
        var difatSectors = 0
        while (true) {
            difatSectors = if (fatSectors > 109) (fatSectors - 109 + 126) / 127 else 0
            val total = fatSectors + difatSectors + dirSectors + miniFatSectors + miniStreamSectors + bigSectors
            if (fatSectors * 128 >= total) break
            fatSectors++
        }
        val total = fatSectors + difatSectors + dirSectors + miniFatSectors + miniStreamSectors + bigSectors
        val fat = IntArray(fatSectors * 128) { FREE }
        var next = 0
        fun chain(count: Int, mark: Int? = null): Int {
            if (count == 0) return END
            val first = next
            for (k in 0 until count) fat[next + k] = mark ?: if (k == count - 1) END else next + k + 1
            next += count
            return first
        }
        chain(fatSectors, FAT_SECT)
        val difatStart = if (difatSectors > 0) chain(difatSectors, DIFAT_SECT) else END
        val dirStart = chain(dirSectors)
        val miniFatStart = chain(miniFatSectors)
        val miniStreamStart = chain(miniStreamSectors)
        for (d in big) d.start = chain(((d.size + 511) / 512).toInt())
        check(next == total)
        dirs[0].start = if (miniBytes.isEmpty()) END else miniStreamStart
        dirs[0].size = miniBytes.size.toLong()

        val out = ByteBuffer.allocate(512 + total * 512).order(ByteOrder.LITTLE_ENDIAN)
        // Header
        out.put(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))
        out.position(24)
        out.putShort(0x003E); out.putShort(3); out.putShort(0xFFFE.toShort()); out.putShort(9); out.putShort(6)
        out.position(44)
        out.putInt(fatSectors); out.putInt(dirStart); out.putInt(0); out.putInt(MINI_CUTOFF)
        out.putInt(if (miniFatSectors > 0) miniFatStart else END); out.putInt(miniFatSectors)
        out.putInt(difatStart); out.putInt(difatSectors)
        for (i in 0 until 109) out.putInt(if (i < fatSectors) i else FREE)
        fun sector(i: Int) = 512 + i * 512
        // DIFAT sectors: the FAT sectors after the first 109, then the next DIFAT sector.
        for (k in 0 until difatSectors) {
            out.position(sector(difatStart + k))
            for (j in 0 until 127) {
                val fatIndex = 109 + k * 127 + j
                out.putInt(if (fatIndex < fatSectors) fatIndex else FREE)
            }
            out.putInt(if (k == difatSectors - 1) END else difatStart + k + 1)
        }
        // FAT
        out.position(sector(0))
        fat.forEach { out.putInt(it) }
        // Directory
        out.position(sector(dirStart))
        for (i in 0 until dirSectors * 4) {
            val d = dirs.getOrNull(i)
            val base = sector(dirStart) + i * 128
            out.position(base)
            if (d == null) {
                out.position(base + 68); out.putInt(-1); out.putInt(-1); out.putInt(-1)
                continue
            }
            val name = d.node.name.take(31)
            name.forEach { out.putChar(it) }
            out.position(base + 64)
            out.putShort(((name.length + 1) * 2).toShort())
            out.put(if (d.node === root) 5 else if (d.node is Node.Storage) 1 else 2)
            out.put(1) // black
            out.putInt(d.left); out.putInt(d.right); out.putInt(d.child)
            out.position(base + 116)
            out.putInt(d.start)
            out.putInt(d.size.toInt()); out.putInt(0)
        }
        // Mini FAT
        if (miniFatSectors > 0) {
            out.position(sector(miniFatStart))
            for (i in 0 until miniFatSectors * 128) out.putInt(miniFat.getOrElse(i) { FREE })
        }
        if (miniBytes.isNotEmpty()) { out.position(sector(miniStreamStart)); out.put(miniBytes) }
        for (d in big) { out.position(sector(d.start)); out.put((d.node as Node.Stream).data) }
        return out.array()
    }

    private companion object {
        const val END = -2
        const val FREE = -1
        const val FAT_SECT = -3
        const val DIFAT_SECT = -4
        const val MINI_CUTOFF = 4096
    }
}
