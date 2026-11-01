package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.desktop.Os
import io.github.thepytales.llmhub.ui.tr
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import de.lokal.whisper.desktop.SpeechWin
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.lang.management.ManagementFactory
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Desktop parts of the local models. The llama.cpp program is loaded once from the official release into
 * <local folder>/llm/runtime and checked against the SHA-256 GitHub publishes for it: Windows the CPU package (with a
 * graphics card also the Vulkan backend, which works with NVIDIA, AMD and Intel without admin rights), macOS the package
 * for Apple Silicon with Metal, Linux the Ubuntu x64 package (AP-31).
 */
internal object LlamaHost {
    /** llama.cpp release; change only here. If it lacks a package, the latest release is used. */
    private const val TAG = "b11870"

    fun root(@Suppress("UNUSED_PARAMETER") c: AppContainer): File = File(Paths.local, "llm")

    /** Release package of this system (CPU, on a Mac with Metal). */
    private val cpuPackage: String? get() = when {
        Os.windows -> "bin-win-cpu-x64.zip"
        Os.mac -> if (Os.arm) "bin-macos-arm64.zip" else "bin-macos-x64.zip"
        Os.arm -> null
        else -> "bin-ubuntu-x64.zip"
    }

    /** Extra package for graphics cards (only Windows; macOS has Metal in the main package). */
    private val gpuPackage: String? get() = if (Os.windows) "bin-win-vulkan-x64.zip" else null

    private val serverName get() = if (Os.windows) "llama-server.exe" else "llama-server"

    private fun runtimeDir(root: File) = File(root, "runtime")

    fun hardware(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File): Hardware {
        // Apple Silicon: the graphics share the memory; llama.cpp computes with Metal.
        val gpu = if (SpeechWin.WINDOWS) gpu() else if (Os.mac && Os.arm) javaRam()?.let { "Apple Silicon (Metal)" to it * 0.66 } else null
        val ram = javaRam() ?: windowsRam()
        return Hardware(
            phone = false,
            ramGb = ram ?: 8.0,
            cores = SpeechWin.physicalCores().takeIf { it > 0 } ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
            cpuName = SpeechWin.cpuName().orEmpty().ifBlank { envCpu() },
            gpuName = gpu?.first,
            vramGb = gpu?.second ?: 0.0,
            freeDiskGb = freeDisk(root),
            limited = ram == null,
        )
    }

    /**
     * Without registry and system functions (JNA), for PCs where IT rules block them (AppLocker, WDAC, virus scanner):
     * memory from Java, cores and processor from the environment. The graphics card stays unknown.
     */
    fun basicHardware(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File): Hardware = Hardware(
        phone = false,
        ramGb = javaRam() ?: 8.0,
        cores = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
        cpuName = envCpu(),
        freeDiskGb = freeDisk(root),
        limited = true,
    )

    private fun javaRam(): Double? = runCatching {
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize?.takeIf { it > 0 }?.div(1e9)
    }.getOrNull()

    private fun windowsRam(): Double? = if (!SpeechWin.WINDOWS) null else runCatching {
        val status = com.sun.jna.platform.win32.WinBase.MEMORYSTATUSEX()
        if (com.sun.jna.platform.win32.Kernel32.INSTANCE.GlobalMemoryStatusEx(status)) status.ullTotalPhys.toLong() / 1e9 else null
    }.getOrNull()

    private fun envCpu(): String = System.getenv("PROCESSOR_IDENTIFIER")?.trim().orEmpty()

    private fun freeDisk(root: File): Double = runCatching { root.apply { mkdirs() }.usableSpace / 1e9 }.getOrDefault(Double.NaN)

    /**
     * The graphics card with the most own memory, from the registry (readable without admin rights). Integrated
     * graphics report little or no own memory and are left out.
     */
    private fun gpu(): Pair<String, Double>? = runCatching {
        val base = "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}"
        Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, base).filter { it.matches(Regex("\\d{4}")) }.mapNotNull { k ->
            val key = "$base\\$k"
            val name = runCatching { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, key, "DriverDesc") }.getOrNull() ?: return@mapNotNull null
            val bytes = runCatching {
                when (val v = Advapi32Util.registryGetValue(WinReg.HKEY_LOCAL_MACHINE, key, "HardwareInformation.qwMemorySize")) {
                    is Long -> v
                    is Number -> v.toLong()
                    else -> null
                }
            }.getOrNull() ?: runCatching {
                when (val v = Advapi32Util.registryGetValue(WinReg.HKEY_LOCAL_MACHINE, key, "HardwareInformation.MemorySize")) {
                    is Int -> v.toLong() and 0xffffffffL
                    is ByteArray -> v.foldIndexed(0L) { i, acc, b -> acc or ((b.toLong() and 0xff) shl (8 * i)) }
                    is Number -> v.toLong()
                    else -> null
                }
            }.getOrNull() ?: 0L
            name to bytes / 1073741824.0
        }.filter { it.second >= 3.5 }.maxByOrNull { it.second }
    }.onFailure { AppLog.e("Grafikkarte erkennen", it) }.getOrNull()

    fun binary(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File): File? {
        System.getProperty("adeu.llamaServer")?.let { File(it) }?.takeIf { it.isFile }?.let { return it }
        return File(runtimeDir(root), serverName).takeIf { it.isFile }
    }

    fun runtimeStatus(c: AppContainer, root: File): RuntimeStatus = when {
        binary(c, root) != null -> RuntimeStatus.READY
        cpuPackage != null -> RuntimeStatus.MISSING
        else -> RuntimeStatus.UNSUPPORTED
    }

    /** Downloads the CPU package (and the Vulkan backend for graphics cards) and unpacks it. */
    suspend fun installRuntime(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File, gpu: Boolean, progress: (Double) -> Unit) = withContext(Dispatchers.IO) {
        val cpu = cpuPackage ?: error(tr("Für dieses System gibt es kein fertiges Programm für lokale Modelle."))
        val assets = releaseAssets(TAG).takeIf { a -> a.any { it.name.endsWith(cpu) } } ?: releaseAssets(null)
        val wanted = listOfNotNull(
            assets.firstOrNull { it.name.endsWith(cpu) } ?: error(tr("Kein Paket für {0} im llama.cpp-Release gefunden.", Os.label)),
            gpuPackage?.let { g -> assets.firstOrNull { it.name.endsWith(g) } }.takeIf { gpu },
        )
        val dir = runtimeDir(root)
        val tmp = File(root, "runtime.new").apply { deleteRecursively(); mkdirs() }
        val total = wanted.sumOf { it.size }.toDouble()
        var done = 0L
        for (a in wanted) {
            val zip = File(root, a.name)
            download(a.url, zip) { got -> progress(((done + got) / total).coerceAtMost(0.99)) }
            a.sha256?.let { expected -> if (sha256(zip) != expected) { zip.delete(); error("Prüfsumme von ${a.name} stimmt nicht.") } }
            zip.inputStream().use { unzip(it, tmp) }
            done += a.size
            zip.delete()
        }
        if (!File(tmp, serverName).isFile) error(tr("{0} fehlt im Paket.", serverName))
        executable(tmp)
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) error("Programm konnte nicht gespeichert werden.")
        progress(1.0)
    }

    /** A llama.cpp Windows ZIP loaded by hand (company network blocks GitHub). */
    fun importRuntime(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File, input: InputStream) {
        val dir = runtimeDir(root).apply { mkdirs() }
        unzip(input, dir)
        if (!File(dir, serverName).isFile) error(tr("In der ZIP-Datei fehlt {0} (llama.cpp, Paket „{1}“).", serverName, cpuPackage?.removeSuffix(".zip") ?: "–"))
        executable(dir)
    }

    /** ZIP files carry no rights: the programs of llama.cpp must be executable on macOS and Linux. */
    private fun executable(dir: File) {
        if (Os.windows) return
        dir.listFiles().orEmpty().filter { it.isFile && (it.name.startsWith("llama-") || !it.name.contains('.')) }.forEach { it.setExecutable(true) }
    }

    private data class Asset(val name: String, val url: String, val size: Long, val sha256: String?)

    private fun releaseAssets(tag: String?): List<Asset> = runCatching {
        val api = "https://api.github.com/repos/ggml-org/llama.cpp/releases/" + (tag?.let { "tags/$it" } ?: "latest")
        val con = URL(api).openConnection() as HttpURLConnection
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("User-Agent", "ADEU-LLM")
        val text = con.inputStream.use { it.readBytes().decodeToString() }
        Json.parseToJsonElement(text).jsonObject["assets"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            Asset(
                o.str("name"), o.str("browser_download_url"), o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
                o["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:"),
            )
        }
    }.getOrElse { if (tag == null) throw it else emptyList() }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun download(url: String, dest: File, progress: (Long) -> Unit) {
        var u = url
        var hops = 0
        while (true) {
            val con = URL(u).openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.setRequestProperty("User-Agent", "ADEU-LLM")
            val code = con.responseCode
            if (code in 300..399 && hops++ < 10) { u = URL(URL(u), con.getHeaderField("Location")).toString(); con.disconnect(); continue }
            if (code != 200) error("HTTP $code")
            con.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var got = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        got += n
                        progress(got)
                    }
                }
            }
            return
        }
    }

    private fun unzip(input: InputStream, dir: File) {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.replace('\\', '/').substringAfterLast('/')
                if (name.isBlank() || name.startsWith(".")) continue
                File(dir, name).outputStream().use { z.copyTo(it) }
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 20); while (true) { val n = i.read(b); if (n <= 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun environment(c: AppContainer, env: MutableMap<String, String>) {}

    private fun pidFile(root: File) = File(root, "server.pid")

    fun rememberPid(root: File, p: Process) { runCatching { pidFile(root).writeText(p.pid().toString()) } }

    fun forgetPid(root: File) { pidFile(root).delete() }

    /** A server left over from an earlier run (app ended without stopping it). */
    fun killStale(root: File) {
        val pid = runCatching { pidFile(root).readText().trim().toLong() }.getOrNull() ?: return
        ProcessHandle.of(pid).ifPresent { h -> if (h.info().command().orElse("").contains("llama-server")) h.destroyForcibly() }
        forgetPid(root)
    }
}
