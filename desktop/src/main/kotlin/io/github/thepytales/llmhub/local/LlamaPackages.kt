package io.github.thepytales.llmhub.local

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Which llama.cpp release package fits this system, and unpacking it (AP-86). The release names its packages
 * `llama-<tag>-bin-<system>-<arch>.<zip|tar.gz>`: Windows as ZIP (CPU, Vulkan as an extra), macOS and Linux as tar.gz
 * with a folder `llama-<tag>/` and symbolic links between the libraries (libllama.0.dylib → libllama.0.7.0.dylib).
 * Pure functions, so the choice is tested against the real list of a release without network ([LlamaPackagesTest]).
 */
object LlamaPackages {
    enum class Platform { WINDOWS, MAC, LINUX }

    /** A package of the release: [kind] is the part after "bin-" without the ending, e.g. "macos-arm64". */
    data class Choice(val kind: String, val gpu: Boolean)

    /** The packages this system needs: first the main one (CPU, on Apple Silicon with Metal), then the GPU extra. */
    fun wanted(system: Platform, arm: Boolean, gpu: Boolean): List<Choice> = when (system) {
        Platform.WINDOWS -> listOfNotNull(
            Choice(if (arm) "win-cpu-arm64" else "win-cpu-x64", false),
            // Vulkan works with NVIDIA, AMD and Intel without admin rights; there is no ARM build of it.
            Choice("win-vulkan-x64", true).takeIf { gpu && !arm },
        )
        // Apple Silicon: Metal is part of the main package; Intel Macs compute on the CPU.
        Platform.MAC -> listOf(Choice(if (arm) "macos-arm64" else "macos-x64", false))
        Platform.LINUX -> listOf(Choice(if (arm) "ubuntu-arm64" else "ubuntu-x64", false))
    }

    /**
     * The asset of [names] for [kind]: exactly "bin-<kind>" followed by a known ending, so "ubuntu-x64" never takes
     * "ubuntu-vulkan-x64" and "macos-arm64" never a later "macos-arm64-kleidiai". ZIP before tar.gz if both exist.
     */
    fun pick(names: List<String>, kind: String): String? {
        val match = names.filter { n -> ENDINGS.any { e -> n.endsWith("-bin-$kind$e") || n == "bin-$kind$e" } }
        return match.firstOrNull { it.endsWith(".zip") } ?: match.firstOrNull()
    }

    /** File name the release uses for [kind] (for downloads without the GitHub API and for the hint on loading by hand). */
    fun fileName(tag: String, kind: String): String = "llama-$tag-bin-$kind" + if (kind.startsWith("win-")) ".zip" else ".tar.gz"

    fun downloadUrl(tag: String, name: String) = "https://github.com/ggml-org/llama.cpp/releases/download/$tag/$name"

    /** Whether [name] is a llama.cpp package (ZIP or tar.gz), as opposed to a model file. */
    fun isPackage(name: String): Boolean = ENDINGS.any { name.endsWith(it, ignoreCase = true) }

    private val ENDINGS = listOf(".zip", ".tar.gz", ".tgz")

    /**
     * Unpacks [input] (ZIP or tar.gz, by [name]) flat into [dir]: the files of the release sit in one folder, the
     * folder `llama-<tag>/` is dropped. Symbolic links of the tar.gz become links (or copies where links are not
     * allowed), because the libraries are loaded by the linked name. Hidden files (macOS "._" resource forks) are skipped.
     */
    fun unpack(name: String, input: InputStream, dir: File) {
        dir.mkdirs()
        if (name.endsWith(".zip", ignoreCase = true)) unzip(input, dir) else untar(GZIPInputStream(input, 1 shl 16), dir)
    }

    private fun safeName(path: String): String? {
        val n = path.replace('\\', '/').trimEnd('/').substringAfterLast('/')
        return n.takeIf { it.isNotBlank() && !it.startsWith(".") && it != ".." }
    }

    private fun unzip(input: InputStream, dir: File) {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val name = safeName(e.name) ?: continue
                File(dir, name).outputStream().use { z.copyTo(it) }
            }
        }
    }

    /** A minimal reader for ustar/GNU tar (as packed by the llama.cpp release): files, links, long names, pax headers. */
    private fun untar(input: InputStream, dir: File) {
        val links = mutableListOf<Pair<String, String>>()
        val executable = mutableSetOf<String>()
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        input.use { inp ->
            while (true) {
                if (!readFully(inp, header)) break
                if (header.all { it == 0.toByte() }) break
                val size = octal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val mode = octal(header, 100, 8)
                val path = longName ?: (cString(header, 345, 155).let { p -> if (p.isNotEmpty()) "$p/" else "" } + cString(header, 0, 100))
                val link = longLink ?: cString(header, 157, 100)
                longName = null; longLink = null
                when (type) {
                    'L' -> { longName = readText(inp, size); continue }
                    'K' -> { longLink = readText(inp, size); continue }
                    'x' -> {
                        val pax = readText(inp, size)
                        paxValue(pax, "path")?.let { longName = it }
                        paxValue(pax, "linkpath")?.let { longLink = it }
                        continue
                    }
                    'g' -> { skip(inp, size); continue }
                }
                val name = safeName(path)
                val isFile = type == '0' || type == '\u0000' || type == '7'
                if (isFile && name != null) {
                    File(dir, name).outputStream().use { out -> copy(inp, out, size) }
                    if (mode and 0b001_001_001L != 0L) executable += name
                } else {
                    if (type == '2' && name != null) links += name to link
                    skip(inp, size)
                }
                skip(inp, pad(size))
            }
        }
        // Links last: their targets are there now. Chains (libggml.dylib → libggml.0.dylib → …) resolve in any order.
        for ((name, target) in links) {
            val t = safeName(target) ?: continue
            val f = File(dir, name)
            f.delete()
            runCatching { Files.createSymbolicLink(f.toPath(), File(t).toPath()) }.onFailure {
                resolve(dir, t, links)?.copyTo(f, overwrite = true)
            }
        }
        executable.forEach { File(dir, it).setExecutable(true) }
    }

    private fun resolve(dir: File, target: String, links: List<Pair<String, String>>): File? {
        var t = target
        repeat(8) {
            val f = File(dir, t)
            if (f.isFile && !Files.isSymbolicLink(f.toPath())) return f
            t = links.firstOrNull { it.first == t }?.second?.let { safeName(it) } ?: return f.takeIf { it.isFile }
        }
        return null
    }

    private fun pad(size: Long) = (512 - size % 512) % 512

    private fun readFully(inp: InputStream, b: ByteArray): Boolean {
        var got = 0
        while (got < b.size) {
            val n = inp.read(b, got, b.size - got)
            if (n <= 0) return false
            got += n
        }
        return true
    }

    private fun copy(inp: InputStream, out: java.io.OutputStream, size: Long) {
        val buf = ByteArray(1 shl 16)
        var left = size
        while (left > 0) {
            val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) error("tar: Datei endet zu früh")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun skip(inp: InputStream, size: Long) = copy(inp, java.io.OutputStream.nullOutputStream(), size)

    private fun readText(inp: InputStream, size: Long): String {
        val b = java.io.ByteArrayOutputStream()
        copy(inp, b, size)
        skip(inp, pad(size))
        return b.toByteArray().decodeToString().trimEnd('\u0000', '\n')
    }

    private fun paxValue(pax: String, key: String): String? =
        pax.lineSequence().mapNotNull { l -> l.substringAfter(' ', "").takeIf { it.startsWith("$key=") }?.substringAfter('=') }.firstOrNull()

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        // GNU base-256 for very large sizes (first byte has the high bit).
        if (b[off].toInt() and 0x80 != 0) {
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (b[i].toLong() and 0xff)
            return v
        }
        return cString(b, off, len).trim().ifEmpty { "0" }.toLong(8)
    }
}
