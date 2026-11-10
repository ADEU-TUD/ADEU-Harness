package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LlamaHost
import io.github.thepytales.llmhub.local.LlamaPackages
import io.github.thepytales.llmhub.local.LlamaPackages.Platform
import io.github.thepytales.llmhub.local.LocalLlm
import io.github.thepytales.llmhub.local.RuntimeDownloadException
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lokale Modelle auf dem Mac laden (AP-86): the package choice for Windows, macOS (arm64, x64) and Linux against the
 * real asset list of the fixed llama.cpp release (test resource, every name checked at its download address), and
 * unpacking the tar.gz of macOS/Linux with its folder and symbolic links.
 */
class LlamaPackagesTest {
    private val release: List<String> = javaClass.getResource("/llama-release-b11870.txt")!!.readText()
        .lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun names(system: Platform, arm: Boolean, gpu: Boolean) =
        LlamaPackages.wanted(system, arm, gpu).map { LlamaPackages.pick(release, it.kind) }

    @Test
    fun picksThePackageOfEverySystemFromTheRealRelease() {
        assertEquals(listOf("llama-b11870-bin-win-cpu-x64.zip"), names(Platform.WINDOWS, arm = false, gpu = false))
        assertEquals(listOf("llama-b11870-bin-win-cpu-x64.zip", "llama-b11870-bin-win-vulkan-x64.zip"), names(Platform.WINDOWS, arm = false, gpu = true))
        assertEquals(listOf("llama-b11870-bin-win-cpu-arm64.zip"), names(Platform.WINDOWS, arm = true, gpu = true))
        assertEquals(listOf("llama-b11870-bin-macos-arm64.tar.gz"), names(Platform.MAC, arm = true, gpu = true))
        assertEquals(listOf("llama-b11870-bin-macos-x64.tar.gz"), names(Platform.MAC, arm = false, gpu = false))
        assertEquals(listOf("llama-b11870-bin-ubuntu-x64.tar.gz"), names(Platform.LINUX, arm = false, gpu = false))
        assertEquals(listOf("llama-b11870-bin-ubuntu-arm64.tar.gz"), names(Platform.LINUX, arm = true, gpu = false))
    }

    @Test
    fun theOldNameIsGoneAndSimilarPackagesAreNotTaken() {
        // Until 1.0.66 the app looked for a name ending in "bin-macos-arm64.zip" – the release has none.
        assertTrue(release.none { it.endsWith("bin-macos-arm64.zip") })
        assertNull(LlamaPackages.pick(listOf("llama-b1-bin-ubuntu-vulkan-x64.tar.gz"), "ubuntu-x64"))
        assertNull(LlamaPackages.pick(listOf("llama-b1-bin-macos-arm64-kleidiai.tar.gz"), "macos-arm64"))
        assertEquals("llama-b2-bin-macos-arm64.zip", LlamaPackages.pick(listOf("llama-b2-bin-macos-arm64.tar.gz", "llama-b2-bin-macos-arm64.zip"), "macos-arm64"))
    }

    @Test
    fun pinnedPackagesMatchTheReleaseNames() {
        for (kind in LlamaHost.PINNED.keys) {
            val name = LlamaPackages.fileName(LlamaHost.TAG, kind)
            assertTrue(name in release, "$name fehlt im Release")
            assertEquals(name, LlamaPackages.pick(release, kind))
            assertEquals(64, LlamaHost.PINNED.getValue(kind).second.length)
        }
        for (s in Platform.entries) for (arm in listOf(false, true)) for (gpu in listOf(false, true)) {
            LlamaPackages.wanted(s, arm, gpu).forEach { assertTrue(it.kind in LlamaHost.PINNED, "${it.kind} ohne Prüfsumme") }
        }
        assertTrue(LlamaPackages.isPackage("llama-b11870-bin-macos-arm64.tar.gz") && LlamaPackages.isPackage("x.zip"))
        assertFalse(LlamaPackages.isPackage("Qwen3.5-9B-Q4_K_M.gguf"))
    }

    @Test
    fun unpacksTarGzWithFolderLinksAndRights() {
        val dir = Files.createTempDirectory("llama-tar").toFile()
        try {
            val longName = "llama-b1/" + "lib-very-long-name-".repeat(8) + ".dylib"
            val tar = tarGz(
                Entry("llama-b1/", type = '5'),
                Entry("llama-b1/llama-server", "server".toByteArray(), mode = "755"),
                Entry("llama-b1/libllama.0.7.0.dylib", ByteArray(1500) { it.toByte() }, mode = "755"),
                Entry("llama-b1/libllama.0.dylib", type = '2', link = "libllama.0.7.0.dylib"),
                Entry("llama-b1/libllama.dylib", type = '2', link = "libllama.0.dylib"),
                Entry("llama-b1/LICENSE", "MIT".toByteArray(), mode = "644"),
                Entry("llama-b1/._llama-server", "apple".toByteArray()),
                Entry(longName, "long".toByteArray(), mode = "644"),
            )
            LlamaPackages.unpack("llama-b1-bin-macos-arm64.tar.gz", ByteArrayInputStream(tar), dir)
            assertEquals("server", File(dir, "llama-server").readText())
            assertTrue(File(dir, "llama-server").canExecute())
            assertEquals(1500, File(dir, "libllama.dylib").readBytes().size, "Verkettete Links zeigen auf die Bibliothek")
            assertEquals(1500, File(dir, "libllama.0.dylib").readBytes().size)
            assertEquals("MIT", File(dir, "LICENSE").readText())
            assertFalse(File(dir, "._llama-server").exists())
            assertFalse(File(dir, "llama-b1").exists(), "Der Ordner des Pakets wird weggelassen")
            assertEquals("long", File(dir, longName.substringAfterLast('/')).readText())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun unpacksTheWindowsZipFlat() {
        val dir = Files.createTempDirectory("llama-zip").toFile()
        try {
            val zip = ByteArrayOutputStream().also { b ->
                ZipOutputStream(b).use { z ->
                    z.putNextEntry(ZipEntry("llama-server.exe")); z.write("exe".toByteArray()); z.closeEntry()
                    z.putNextEntry(ZipEntry("sub\\ggml-cpu-haswell.dll")); z.write("dll".toByteArray()); z.closeEntry()
                }
            }.toByteArray()
            LlamaPackages.unpack("llama-b1-bin-win-cpu-x64.zip", ByteArrayInputStream(zip), dir)
            assertEquals("exe", File(dir, "llama-server.exe").readText())
            assertEquals("dll", File(dir, "ggml-cpu-haswell.dll").readText())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun messagesSayWhatIsMissingWithoutDoubleFullStops() {
        assertEquals("Prüfsumme stimmt nicht.", LocalLlm.sentence("Prüfsumme stimmt nicht."))
        assertEquals("HTTP 404.", LocalLlm.sentence("HTTP 404"))
        assertTrue(LocalLlm.networkBlocked(java.net.UnknownHostException("github.com")))
        assertTrue(LocalLlm.networkBlocked(RuntimeException("x", RuntimeDownloadException("HTTP 407", blocked = true))))
        assertFalse(LocalLlm.networkBlocked(RuntimeException("HTTP 404")))
        assertFalse(LocalLlm.networkBlocked(IllegalStateException("Prüfsumme stimmt nicht")))
    }

    @Test
    fun aMacIsCalledMac() {
        assertTrue(Hardware(phone = false, mac = true, ramGb = 39.0, cores = 12).describe().startsWith("Mac ·"))
        assertTrue(Hardware(phone = false, ramGb = 16.0, cores = 8).describe().startsWith("PC ·"))
    }

    /**
     * The real packages (optional): LLAMA_PACKAGES=<folder with the downloaded llama-…-bin-….tar.gz/.zip> – each must
     * unpack to a folder with llama-server and resolvable library links.
     */
    @Test
    fun unpacksRealPackagesWhenGiven() {
        val folder = System.getenv("LLAMA_PACKAGES").orEmpty()
        assumeTrue(folder.isNotBlank() && File(folder).isDirectory, "LLAMA_PACKAGES nicht gesetzt")
        val files = File(folder).listFiles().orEmpty().filter { LlamaPackages.isPackage(it.name) }
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val dir = Files.createTempDirectory("llama-real").toFile()
            try {
                f.inputStream().use { LlamaPackages.unpack(f.name, it, dir) }
                val server = if (f.name.contains("-win-")) "llama-server.exe" else "llama-server"
                val vulkanOnly = f.name.contains("vulkan") && f.name.contains("-win-")
                if (!vulkanOnly) assertTrue(File(dir, server).isFile, "${f.name}: $server fehlt")
                dir.listFiles().orEmpty().filter { Files.isSymbolicLink(it.toPath()) }.forEach { assertTrue(it.isFile, "${f.name}: Link ${it.name} ins Leere") }
                // On a matching machine the program must start: the libraries are found by their linked names.
                val here = when {
                    System.getProperty("os.name").startsWith("Linux") -> if (System.getProperty("os.arch") == "aarch64") "ubuntu-arm64" else "ubuntu-x64"
                    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "macos-arm64" else "macos-x64"
                    else -> "win-cpu-x64"
                }
                if (f.name.contains("-bin-$here.")) {
                    val p = ProcessBuilder(File(dir, server).absolutePath, "--version").directory(dir).redirectErrorStream(true).start()
                    val out = p.inputStream.bufferedReader().readText()
                    assertEquals(0, p.waitFor(), out)
                    assertTrue("version" in out.lowercase(), out)
                }
            } finally { dir.deleteRecursively() }
        }
    }

    // ------------------------------------------------------------------ a small tar writer for the test

    private data class Entry(val name: String, val data: ByteArray = ByteArray(0), val type: Char = '0', val link: String = "", val mode: String = "644")

    private fun tarGz(vararg entries: Entry): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz ->
            for (e in entries) {
                if (e.name.length > 100) {
                    val n = (e.name + "\u0000").toByteArray()
                    gz.write(header("././@LongLink", n.size.toLong(), 'L', "", "644"))
                    gz.write(n); gz.write(ByteArray(((512 - n.size % 512) % 512)))
                }
                gz.write(header(e.name.take(100), e.data.size.toLong(), e.type, e.link, e.mode))
                gz.write(e.data); gz.write(ByteArray(((512 - e.data.size % 512) % 512)))
            }
            gz.write(ByteArray(1024))
        }
        return out.toByteArray()
    }

    private fun header(name: String, size: Long, type: Char, link: String, mode: String): ByteArray {
        val h = ByteArray(512)
        fun put(off: Int, s: String) = s.toByteArray().copyInto(h, off)
        put(0, name)
        put(100, mode.padStart(7, '0'))
        put(108, "0000000"); put(116, "0000000")
        put(124, size.toString(8).padStart(11, '0'))
        put(136, "00000000000")
        h[156] = type.code.toByte()
        put(157, link)
        put(257, "ustar"); put(263, "00")
        for (i in 148 until 156) h[i] = ' '.code.toByte()
        val sum = h.sumOf { it.toInt() and 0xff }
        put(148, sum.toString(8).padStart(6, '0')); h[154] = 0; h[155] = ' '.code.toByte()
        return h
    }
}
