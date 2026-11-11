package io.github.thepytales.llmhub.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.ui.tr
import java.io.File
import java.io.InputStream

/**
 * Android parts of the local models (Windows has its own version of this file). llama-server is part of the app
 * (lib folder, the only place Android lets an app start programs from), in two variants: one for current processors
 * (ARMv8.2 with dot product) and one that runs everywhere.
 */
internal object LlamaHost {
    fun root(c: AppContainer): File = File(c.appContext.filesDir, "llm")

    fun hardware(c: AppContainer, root: File): Hardware {
        val am = c.appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        return Hardware(
            phone = true,
            ramGb = mem.totalMem / 1e9,
            cores = Runtime.getRuntime().availableProcessors(),
            cpuName = soc.trim(),
            freeDiskGb = root.usableSpace / 1e9,
            // Geräteprofil (AP-105): the instruction sets decide how fast a phone reads; the memory bandwidth is measured.
            cpuFeatures = runCatching { HardwareInfo.cpuFeatures(File("/proc/cpuinfo").readText()) }.getOrDefault(emptySet()),
            // AP-116: llama-server only on the big cores (fewer threads, less heat).
            bigCores = HardwareInfo.bigCores(maxFreqs()),
        )
    }

    /** Highest clock of each core in kHz (0 where the system does not tell). */
    private fun maxFreqs(): List<Long> = (0 until Runtime.getRuntime().availableProcessors()).map { i ->
        runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrDefault(0L)
    }

    /**
     * Hitzeschutz (AP-116): the thermal state of the system (Android 10+ status, Android 11+ headroom for the next 10 s).
     * Older systems: [Heat.OK].
     */
    fun heat(c: AppContainer): Heat {
        if (Build.VERSION.SDK_INT < 29) return Heat.OK
        val pm = c.appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return Heat.OK
        val status = runCatching { pm.currentThermalStatus }.getOrDefault(-1)
        val headroom = if (Build.VERSION.SDK_INT >= 30) runCatching { pm.getThermalHeadroom(10) }.getOrDefault(Float.NaN) else Float.NaN
        return Heat.of(status, headroom)
    }

    /** Same as [hardware]; Android needs no system functions that could be blocked. */
    fun basicHardware(c: AppContainer, root: File): Hardware = hardware(c, root)

    private fun libDir(c: AppContainer) = File(c.appContext.applicationInfo.nativeLibraryDir)

    /** Dot product instructions (asimddp) make the fast variant possible. */
    private fun fastCpu(): Boolean = runCatching { File("/proc/cpuinfo").readText().contains(" asimddp") }.getOrDefault(false)

    fun binary(c: AppContainer, root: File): File? {
        val dir = libDir(c)
        val fast = File(dir, "libllama-server.so")
        val basic = File(dir, "libllama-server-basic.so")
        return (if (fastCpu()) fast.takeIf { it.isFile } else null) ?: basic.takeIf { it.isFile } ?: fast.takeIf { it.isFile }
    }

    fun runtimeStatus(c: AppContainer, root: File): RuntimeStatus =
        if (binary(c, root) != null) RuntimeStatus.READY else RuntimeStatus.UNSUPPORTED

    /** Built into the app on Android. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun installRuntime(c: AppContainer, root: File, gpu: Boolean, progress: (Double) -> Unit) {}

    /** Android has the program built in; nothing to load by hand. */
    @Suppress("UNUSED_PARAMETER")
    fun packageName(c: AppContainer): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun importRuntime(c: AppContainer, root: File, name: String, input: InputStream): Unit = error(tr("Auf Android ist das Programm schon in der App enthalten."))

    fun environment(c: AppContainer, env: MutableMap<String, String>) {
        env["LD_LIBRARY_PATH"] = libDir(c).absolutePath
        env["HOME"] = c.appContext.filesDir.absolutePath
        env["TMPDIR"] = c.appContext.cacheDir.absolutePath
    }

    private fun pidFile(root: File) = File(root, "server.pid")

    fun rememberPid(root: File, p: Process) {
        runCatching { pidFile(root).writeText(pidOf(p).toString()) }
    }

    fun forgetPid(root: File) { pidFile(root).delete() }

    /** A server left over from a killed app process (same user id, so it may be stopped). */
    fun killStale(root: File) {
        val pid = runCatching { pidFile(root).readText().trim().toInt() }.getOrNull() ?: return
        val cmd = runCatching { File("/proc/$pid/cmdline").readText() }.getOrDefault("")
        if ("llama-server" in cmd) runCatching { android.os.Process.killProcess(pid) }
        forgetPid(root)
    }

    private fun pidOf(p: Process): Int = runCatching {
        p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p)
    }.getOrDefault(-1)
}
