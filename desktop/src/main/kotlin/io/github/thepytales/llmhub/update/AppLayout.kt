package io.github.thepytales.llmhub.update

import io.github.thepytales.llmhub.desktop.Os
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

/**
 * How the packaged app looks on each system (AP-31) – the folder an update replaces and the launcher inside it:
 * Windows `ADEU LLM\ADEU LLM.exe` with app\ and runtime\, Linux `ADEU LLM/bin/ADEU LLM` with lib/app and lib/runtime
 * (jpackage app image), macOS the bundle `ADEU LLM.app` with Contents/MacOS/ADEU LLM.
 */
internal enum class AppLayout(val folder: String, val launcher: String, private val parts: List<String>, val top: List<String>) {
    WINDOWS("ADEU LLM", "ADEU LLM.exe", listOf("app", "runtime"), listOf("ADEU LLM.exe", "app")),
    LINUX("ADEU LLM", "bin/ADEU LLM", listOf("lib/app", "lib/runtime"), listOf("bin", "lib")),
    MAC("ADEU LLM.app", "Contents/MacOS/ADEU LLM", listOf("Contents/app", "Contents/runtime"), listOf("Contents"));

    /** [dir] is a complete app of this layout. */
    fun complete(dir: File) = File(dir, launcher).isFile && parts.all { File(dir, it).isDirectory }

    companion object {
        val current: AppLayout get() = when {
            Os.windows -> WINDOWS
            Os.mac -> MAC
            else -> LINUX
        }

        /** The complete app inside an unpacked update [target] (whatever system it was made for), or null. */
        fun find(target: File): File? = entries.firstNotNullOfOrNull { l -> File(target, l.folder).takeIf { l.complete(it) } }

        /** Layout of the app folder [app], or null. */
        fun of(app: File): AppLayout? = entries.firstOrNull { it.complete(app) }

        /** Layout whose top-level entries [names] (of an app folder) are all there. */
        fun ofNames(names: Collection<String>): AppLayout? = entries.firstOrNull { l -> l.top.all { it in names } }
    }
}

/**
 * Unpacks the .tar.gz of a macOS or Linux release (AP-31). Unlike ZIP, tar keeps what the app needs there: the
 * executable bit of the launchers and the symbolic links inside the Java runtime of a macOS bundle. Reads ustar, GNU
 * long names and pax headers (macOS tar); AppleDouble files ("._…") are skipped. Nothing may land outside [target].
 */
internal object TarGz {
    fun extract(archive: File, target: File, progress: (Float) -> Unit = {}) {
        val base = target.canonicalFile
        val total = archive.length().coerceAtLeast(1)
        val counting = CountingInput(archive.inputStream().buffered())
        GZIPInputStream(counting, 1 shl 16).use { input ->
            val header = ByteArray(512)
            var longName: String? = null
            var longLink: String? = null
            var pax: Map<String, String> = emptyMap()
            while (true) {
                if (!readFully(input, header)) break
                if (header.all { it == 0.toByte() }) break
                val type = header[156].toInt().toChar()
                val size = octal(header, 124, 12)
                val data = size + (512 - size % 512) % 512
                when (type) {
                    'L', 'K', 'x', 'g' -> {
                        val bytes = ByteArray(size.toInt())
                        if (!readFully(input, bytes)) throw IOException("Archiv unvollständig")
                        skip(input, data - size)
                        when (type) {
                            'L' -> longName = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                            'K' -> longLink = String(bytes, Charsets.UTF_8).trimEnd('\u0000')
                            'x' -> pax = paxRecords(bytes)
                        }
                        continue
                    }
                }
                val prefix = text(header, 345, 155)
                val rawName = longName ?: pax["path"] ?: (if (prefix.isNotEmpty()) "$prefix/" else "") + text(header, 0, 100)
                val link = longLink ?: pax["linkpath"] ?: text(header, 157, 100)
                val mode = octal(header, 100, 8).toInt()
                longName = null; longLink = null; pax = emptyMap()
                val name = rawName.removePrefix("./").trimEnd('/')
                val leaf = name.substringAfterLast('/')
                val out = File(base, name)
                val inside = name.isNotEmpty() && out.canonicalPath.let { it == base.path || it.startsWith(base.path + File.separator) }
                if (!inside || leaf.startsWith("._")) {
                    skip(input, data)
                    continue
                }
                when (type) {
                    '5' -> out.mkdirs()
                    '2' -> {
                        // Only relative links that stay inside the app.
                        val resolved = File(out.parentFile, link).canonicalPath
                        if (!link.startsWith("/") && resolved.startsWith(base.path + File.separator)) {
                            out.parentFile.mkdirs()
                            out.delete()
                            Files.createSymbolicLink(out.toPath(), java.nio.file.Paths.get(link))
                        }
                    }
                    '1' -> {
                        val from = File(base, link.removePrefix("./"))
                        if (from.isFile && from.canonicalPath.startsWith(base.path + File.separator)) {
                            out.parentFile.mkdirs()
                            from.copyTo(out, overwrite = true)
                            permissions(out, mode)
                        }
                    }
                    '0', '\u0000', '7' -> {
                        out.parentFile.mkdirs()
                        out.outputStream().buffered().use { o ->
                            var left = size
                            val buf = ByteArray(1 shl 16)
                            while (left > 0) {
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (n < 0) throw IOException("Archiv unvollständig")
                                o.write(buf, 0, n)
                                left -= n
                            }
                        }
                        skip(input, data - size)
                        permissions(out, mode)
                    }
                    else -> skip(input, data)
                }
                progress((counting.count.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }

    private class CountingInput(input: InputStream) : FilterInputStream(input) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) {
                if (off == 0) return false
                throw IOException("Archiv unvollständig")
            }
            off += n
        }
        return true
    }

    private fun skip(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw IOException("Archiv unvollständig")
            left -= r
        }
    }

    private fun text(h: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && h[end] != 0.toByte()) end++
        return String(h, off, end - off, Charsets.UTF_8)
    }

    private fun octal(h: ByteArray, off: Int, len: Int): Long {
        // Base-256 for very large sizes (GNU).
        if (h[off].toInt() and 0x80 != 0) {
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (h[i].toLong() and 0xff)
            return v
        }
        return text(h, off, len).trim().ifEmpty { "0" }.toLong(8)
    }

    /** pax records "<length> <key>=<value>\n". */
    private fun paxRecords(bytes: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var i = 0
        while (i < bytes.size) {
            val sp = (i until bytes.size).firstOrNull { bytes[it] == ' '.code.toByte() } ?: break
            val len = String(bytes, i, sp - i, Charsets.US_ASCII).toIntOrNull() ?: break
            if (len <= 0 || i + len > bytes.size) break
            val record = String(bytes, sp + 1, i + len - sp - 2, Charsets.UTF_8)
            val eq = record.indexOf('=')
            if (eq > 0) out[record.substring(0, eq)] = record.substring(eq + 1)
            i += len
        }
        return out
    }

    private fun permissions(f: File, mode: Int) {
        runCatching {
            val p = mutableSetOf<PosixFilePermission>()
            if (mode and 0b100_000_000 != 0) p += PosixFilePermission.OWNER_READ
            if (mode and 0b010_000_000 != 0) p += PosixFilePermission.OWNER_WRITE
            if (mode and 0b001_000_000 != 0) p += PosixFilePermission.OWNER_EXECUTE
            if (mode and 0b000_100_000 != 0) p += PosixFilePermission.GROUP_READ
            if (mode and 0b000_001_000 != 0) p += PosixFilePermission.GROUP_EXECUTE
            if (mode and 0b000_000_100 != 0) p += PosixFilePermission.OTHERS_READ
            if (mode and 0b000_000_001 != 0) p += PosixFilePermission.OTHERS_EXECUTE
            p += PosixFilePermission.OWNER_READ
            p += PosixFilePermission.OWNER_WRITE
            Files.setPosixFilePermissions(f.toPath(), p)
        }
    }
}
