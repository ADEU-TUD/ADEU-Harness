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

/**
 * Desktop parts of the local models. The llama.cpp program is loaded once from the official release into
 * <local folder>/llm/runtime and checked against the SHA-256 GitHub publishes for it: Windows the CPU package (with a
 * graphics card also the Vulkan backend, which works with NVIDIA, AMD and Intel without admin rights), macOS the package
 * for Apple Silicon with Metal, Linux the Ubuntu x64 package (AP-31).
 */
internal object LlamaHost {
    /** llama.cpp release; change only here (with [PINNED]). If a package is gone, the latest release is used. */
    internal const val TAG = "b11870"

    /**
     * Size and SHA-256 of the packages of [TAG] (checked 10.11.2026 against the release files), so the download needs
     * neither the GitHub API (60 requests per hour for a whole university network) nor trust in the moment.
     */
    internal val PINNED = mapOf(
        "win-cpu-x64" to (24_214_736L to "95d12ad225891399faa0b5a5dec48bb042c9cda1ef178c4ae770ccbe3bdba8d8"),
        "win-cpu-arm64" to (12_833_523L to "957950d7e192808a03c4cfa10f94beedfb5e95d306b9e8ec89b8e44fbfdf6c47"),
        "win-vulkan-x64" to (37_035_796L to "2420f932bad0ca5838f7d5881f213272ae651a3409218bca37f4be8305b24fd3"),
        "macos-arm64" to (16_286_198L to "f5adb73765f1d1e1e55b81765aed3da98cbcc95997cbef0287a7030492e636f5"),
        "macos-x64" to (12_021_141L to "ce2ba39c8c916aacc32ec576c155b87ea3722267079db921adc5e42a7706bec2"),
        "ubuntu-x64" to (22_485_156L to "e4b4204cfa97f8864c44fed2877c76a126c816ce93dc90f339289e7dabfb00af"),
        "ubuntu-arm64" to (18_202_293L to "0190be3c22b16151b2b9bfb59f7a62c20ec2f24caa9a6b2a0251a0d160789336"),
    )

    fun root(@Suppress("UNUSED_PARAMETER") c: AppContainer): File = File(Paths.local, "llm")

    private val system get() = when {
        Os.windows -> LlamaPackages.Platform.WINDOWS
        Os.mac -> LlamaPackages.Platform.MAC
        else -> LlamaPackages.Platform.LINUX
    }

    private fun wanted(gpu: Boolean) = LlamaPackages.wanted(system, Os.arm, gpu)

    /** The file to load by hand when the download is blocked, e.g. "llama-b11870-bin-macos-arm64.tar.gz". */
    @Suppress("UNUSED_PARAMETER")
    fun packageName(c: AppContainer): String? = LlamaPackages.fileName(TAG, wanted(false).first().kind)

    private val serverName get() = if (Os.windows) "llama-server.exe" else "llama-server"

    private fun runtimeDir(root: File) = File(root, "runtime")

    fun hardware(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File): Hardware {
        // Apple Silicon: the graphics share the memory; llama.cpp computes with Metal.
        val gpu = if (SpeechWin.WINDOWS) gpu() else if (Os.mac && Os.arm) javaRam()?.let { "Apple Silicon (Metal)" to it * 0.66 } else null
        val ram = javaRam() ?: windowsRam()
        val cores = SpeechWin.physicalCores().takeIf { it > 0 } ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        val profile = runCatching { profile(cores) }.onFailure { AppLog.e("Geräteprofil", it) }.getOrNull() ?: Profile()
        return Hardware(
            phone = false,
            mac = Os.mac,
            ramGb = ram ?: 8.0,
            cores = cores,
            cpuName = SpeechWin.cpuName().orEmpty().ifBlank { profile.cpu.ifBlank { envCpu() } },
            gpuName = if (profile.chip.isNotBlank() && gpu != null) "${profile.chip} (Metal)" else gpu?.first,
            vramGb = gpu?.second ?: 0.0,
            freeDiskGb = freeDisk(root),
            limited = ram == null,
            memBandwidthGbs = profile.bandwidth,
            memDetail = profile.detail,
            cpuFeatures = profile.features,
            chip = profile.chip,
            gpuCores = profile.gpuCores,
        )
    }

    /** Geräteprofil (AP-105): memory bandwidth, instruction sets and the Apple chip, where the system tells them. */
    private data class Profile(
        val bandwidth: Double = Double.NaN,
        val detail: String = "",
        val features: Set<String> = emptySet(),
        val chip: String = "",
        val gpuCores: Int = 0,
        val cpu: String = "",
    )

    private fun profile(cores: Int): Profile = when {
        Os.windows -> {
            // SMBIOS through GetSystemFirmwareTable needs no admin rights; IsProcessorFeaturePresent knows AVX2/AVX-512.
            val mem = windowsSmbios()?.let { HardwareInfo.smbiosMemory(it, header = true, cores = cores) }
            val k = com.sun.jna.platform.win32.Kernel32.INSTANCE
            val features = buildSet {
                if (runCatching { k.IsProcessorFeaturePresent(40) }.getOrDefault(false)) add("avx2")
                if (runCatching { k.IsProcessorFeaturePresent(41) }.getOrDefault(false)) add("avx512")
                if (Os.arm) add("neon")
            }
            Profile(mem?.gbs ?: Double.NaN, mem?.detail().orEmpty(), features)
        }
        Os.mac -> {
            val brand = sysctl("machdep.cpu.brand_string")
            if (Os.arm) {
                val chip = brand.takeIf { it.startsWith("Apple") }.orEmpty()
                val cores = HardwareInfo.appleGpuCores(command("ioreg", "-rd1", "-c", "AGXAccelerator")).takeIf { it > 0 }
                    ?: SpeedForecast.appleChip(chip)?.second ?: 0
                val features = buildSet {
                    add("neon")
                    if (sysctl("hw.optional.arm.FEAT_DotProd") == "1") add("dotprod")
                    if (sysctl("hw.optional.arm.FEAT_I8MM") == "1") add("i8mm")
                }
                Profile(SpeedForecast.appleChip(chip)?.first ?: Double.NaN, if (chip.isNotBlank()) "$chip · gemeinsamer Speicher" else "", features, chip, cores, brand)
            } else {
                val features = buildSet {
                    if (sysctl("hw.optional.avx2_0") == "1") add("avx2")
                    if (sysctl("hw.optional.avx512f") == "1") add("avx512")
                }
                Profile(features = features, cpu = brand)
            }
        }
        else -> {
            val info = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
            // The DMI table is only readable as root; where it is, it gives the memory as on Windows.
            val mem = runCatching { File("/sys/firmware/dmi/tables/DMI").takeIf { it.canRead() }?.readBytes() }.getOrNull()
                ?.let { HardwareInfo.smbiosMemory(it, header = false, cores = cores) }
            val cpu = info.lineSequence().firstOrNull { it.startsWith("model name") }?.substringAfter(':')?.trim().orEmpty()
            Profile(mem?.gbs ?: Double.NaN, mem?.detail().orEmpty(), HardwareInfo.cpuFeatures(info), cpu = cpu)
        }
    }

    private interface Firmware : com.sun.jna.win32.StdCallLibrary {
        fun GetSystemFirmwareTable(provider: Int, id: Int, buffer: com.sun.jna.Pointer?, size: Int): Int
    }

    /** The raw SMBIOS tables of Windows ('RSMB'), null if not readable. */
    private fun windowsSmbios(): ByteArray? = runCatching {
        val lib = com.sun.jna.Native.load("kernel32", Firmware::class.java, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS)
        val rsmb = 0x52534D42
        val size = lib.GetSystemFirmwareTable(rsmb, 0, null, 0)
        if (size <= 0) return null
        val mem = com.sun.jna.Memory(size.toLong())
        val got = lib.GetSystemFirmwareTable(rsmb, 0, mem, size)
        if (got <= 0) null else mem.getByteArray(0, got)
    }.onFailure { AppLog.e("SMBIOS lesen", it) }.getOrNull()

    private fun sysctl(key: String): String = command("sysctl", "-n", key).trim()

    /** A system tool's output (sysctl, ioreg), empty if it fails; at most five seconds. */
    private fun command(vararg args: String): String = runCatching {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); "" } else out
    }.getOrDefault("")

    /**
     * Without registry and system functions (JNA), for PCs where IT rules block them (AppLocker, WDAC, virus scanner):
     * memory from Java, cores and processor from the environment. The graphics card stays unknown.
     */
    fun basicHardware(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File): Hardware = Hardware(
        phone = false,
        mac = Os.mac,
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

    fun runtimeStatus(c: AppContainer, root: File): RuntimeStatus =
        if (binary(c, root) != null) RuntimeStatus.READY else RuntimeStatus.MISSING

    /**
     * Downloads the package of this system (with a graphics card on Windows also the Vulkan backend) and unpacks it
     * (AP-86): first the fixed release with its pinned checksum, if that is gone the latest release over the GitHub API.
     * Failures say what is missing (the program, not the model) and whether the network blocked it.
     */
    suspend fun installRuntime(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File, gpu: Boolean, progress: (Double) -> Unit) = withContext(Dispatchers.IO) {
        val choices = wanted(gpu)
        val tmp = File(root, "runtime.new").apply { deleteRecursively(); mkdirs() }
        val sizes = choices.map { PINNED[it.kind]?.first ?: 30_000_000L }
        val total = sizes.sum().toDouble()
        var done = 0L
        for ((i, choice) in choices.withIndex()) {
            try {
                val (name, url, sha) = source(choice.kind)
                val file = File(root, name)
                try {
                    try {
                        download(url, file) { got -> progress(((done + got) / total).coerceAtMost(0.99)) }
                    } catch (e: java.io.IOException) { throw RuntimeDownloadException(e.message ?: e.toString(), blocked(e), e) }
                    if (sha != null && sha256(file) != sha) error(tr("Prüfsumme von {0} stimmt nicht.", name))
                    file.inputStream().use { LlamaPackages.unpack(name, it, tmp) }
                } finally { file.delete() }
            } catch (e: RuntimeDownloadException) {
                // The graphics extra is optional: without it the processor computes.
                if (choice.gpu) AppLog.e("llama.cpp ${choice.kind}", e) else { tmp.deleteRecursively(); throw e }
            }
            done += sizes[i]
        }
        if (!File(tmp, serverName).isFile) { tmp.deleteRecursively(); throw RuntimeDownloadException(tr("{0} fehlt im Paket.", serverName), blocked = false) }
        prepare(tmp)
        val dir = runtimeDir(root)
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) error(tr("Programm konnte nicht gespeichert werden."))
        progress(1.0)
    }

    /** Name, address and checksum of a package: the pinned one of [TAG], else the latest release (API). */
    private fun source(kind: String): Triple<String, String, String?> {
        val fixed = LlamaPackages.fileName(TAG, kind)
        val fixedUrl = LlamaPackages.downloadUrl(TAG, fixed)
        val status = runCatching { head(fixedUrl) }.getOrElse { e -> throw RuntimeDownloadException(e.message ?: e.toString(), blocked(e), e) }
        if (status in 200..399) return Triple(fixed, fixedUrl, PINNED[kind]?.second)
        if (status == 403 || status == 407) throw RuntimeDownloadException("HTTP $status", blocked = true)
        val assets = runCatching { releaseAssets(null) }.getOrElse { e -> throw RuntimeDownloadException(e.message ?: e.toString(), blocked(e), e) }
        val name = LlamaPackages.pick(assets.map { it.name }, kind)
            ?: throw RuntimeDownloadException(tr("Kein Paket „{0}“ im llama.cpp-Release gefunden", kind), blocked = false)
        val a = assets.first { it.name == name }
        return Triple(a.name, a.url, a.sha256)
    }

    /** Whether the network (proxy, firewall, filter) stopped the download – only then the hint on company networks fits. */
    private fun blocked(e: Throwable): Boolean = generateSequence(e) { it.cause }.take(5).any {
        it is java.net.UnknownHostException || it is javax.net.ssl.SSLException || it is java.net.ConnectException ||
            it is java.net.SocketTimeoutException || (it.message ?: "").let { m -> "HTTP 403" in m || "HTTP 407" in m }
    }

    /** Whether the fixed release still has the file (GitHub answers with a redirect to its storage, or 404). */
    private fun head(url: String): Int {
        val con = URL(url).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = false
        con.requestMethod = "HEAD"
        con.connectTimeout = 15_000; con.readTimeout = 15_000
        con.setRequestProperty("User-Agent", "ADEU-LLM")
        return try { con.responseCode } finally { con.disconnect() }
    }

    /**
     * A package loaded by hand (company network blocks GitHub): the ZIP of Windows or the tar.gz of macOS/Linux, as
     * named by [packageName].
     */
    fun importRuntime(@Suppress("UNUSED_PARAMETER") c: AppContainer, root: File, name: String, input: InputStream) {
        val tmp = File(root, "runtime.new").apply { deleteRecursively(); mkdirs() }
        LlamaPackages.unpack(name, input, tmp)
        if (!File(tmp, serverName).isFile) {
            tmp.deleteRecursively()
            error(tr("In „{0}“ fehlt {1}. Für dieses Gerät passt das llama.cpp-Paket „{2}“.", name, serverName, packageName(c) ?: "–"))
        }
        prepare(tmp)
        // A Vulkan package loaded by hand after the CPU one adds to it instead of replacing it.
        val dir = runtimeDir(root)
        if (name.contains("vulkan", ignoreCase = true) && dir.isDirectory) {
            tmp.listFiles().orEmpty().forEach { it.copyTo(File(dir, it.name), overwrite = true) }
            tmp.deleteRecursively()
            prepare(dir)
        } else {
            dir.deleteRecursively()
            if (!tmp.renameTo(dir)) error(tr("Programm konnte nicht gespeichert werden."))
        }
    }

    /**
     * macOS and Linux: the programs must be executable (ZIP files carry no rights). On a Mac also without the quarantine
     * mark (files from a browser have it), so Gatekeeper does not stop llama-server; the release binaries are signed
     * ad hoc, which Apple Silicon needs.
     */
    private fun prepare(dir: File) {
        if (Os.windows) return
        dir.listFiles().orEmpty().filter { it.isFile && (it.name.startsWith("llama-") || !it.name.contains('.')) }.forEach { it.setExecutable(true) }
        if (Os.mac) runCatching {
            ProcessBuilder("xattr", "-dr", "com.apple.quarantine", dir.absolutePath).redirectErrorStream(true).start().waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
        }.onFailure { AppLog.e("Quarantäne entfernen", it) }
    }

    private data class Asset(val name: String, val url: String, val size: Long, val sha256: String?)

    private fun releaseAssets(tag: String?): List<Asset> {
        val api = "https://api.github.com/repos/ggml-org/llama.cpp/releases/" + (tag?.let { "tags/$it" } ?: "latest")
        val con = URL(api).openConnection() as HttpURLConnection
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.setRequestProperty("User-Agent", "ADEU-LLM")
        if (con.responseCode != 200) error("HTTP ${con.responseCode}")
        val text = con.inputStream.use { it.readBytes().decodeToString() }
        return Json.parseToJsonElement(text).jsonObject["assets"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            Asset(
                o.str("name"), o.str("browser_download_url"), o["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
                o["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:"),
            )
        }
    }

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
            if (code != 200) throw RuntimeDownloadException("HTTP $code", blocked = code == 403 || code == 407)
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

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 20); while (true) { val n = i.read(b); if (n <= 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun environment(c: AppContainer, env: MutableMap<String, String>) {}

    /** Hitzeschutz (AP-116) is for phones; a PC cools itself and throttles without getting hot in the hand. */
    @Suppress("UNUSED_PARAMETER")
    fun heat(c: AppContainer): Heat = Heat.OK

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
