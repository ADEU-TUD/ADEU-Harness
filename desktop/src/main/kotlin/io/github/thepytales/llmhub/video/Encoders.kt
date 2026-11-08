package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Hardware-Encoder für den Export (AP-57): NVIDIA NVENC, Intel Quick Sync, AMD AMF (Windows and Linux) and
 * VideoToolbox (macOS) – found automatically: ffmpeg must know the encoder **and** a short test encode must work (the list of
 * ffmpeg says nothing about the graphics card in this PC). Software (x264) is the fallback, also when a hardware encode
 * fails in the middle of an export.
 */
object Encoders {
    /** An H.264 encoder: [id] as ffmpeg names it, [label] for the dialog. */
    data class Encoder(val id: String, val label: String, val hardware: Boolean)

    val SOFTWARE = Encoder("libx264", trLater("Prozessor (x264)"), false)

    /** In order of preference. */
    val HARDWARE = listOf(
        Encoder("h264_nvenc", "NVIDIA NVENC", true),
        Encoder("h264_qsv", "Intel Quick Sync", true),
        Encoder("h264_amf", "AMD AMF", true),
        Encoder("h264_videotoolbox", "Apple VideoToolbox", true),
    )

    fun byId(id: String?): Encoder = (HARDWARE + SOFTWARE).firstOrNull { it.id == id } ?: SOFTWARE

    /**
     * The video encoder arguments for [e] at the quality of [crf] (x264 scale) and x264 speed [speed]: every hardware
     * encoder gets its own way of saying „constant quality“.
     */
    fun args(e: Encoder, crf: Int, speed: String): List<String> = when (e.id) {
        "h264_nvenc" -> listOf("-c:v", "h264_nvenc", "-preset", if (speed == "slow") "p6" else "p5", "-rc", "vbr", "-cq", (crf + 1).toString(), "-b:v", "0", "-pix_fmt", "yuv420p")
        "h264_qsv" -> listOf("-c:v", "h264_qsv", "-preset", if (speed == "slow") "slower" else "medium", "-global_quality", (crf + 1).toString(), "-pix_fmt", "nv12")
        "h264_amf" -> listOf("-c:v", "h264_amf", "-quality", if (speed == "slow") "quality" else "balanced", "-rc", "cqp", "-qp_i", crf.toString(), "-qp_p", (crf + 2).toString(), "-pix_fmt", "yuv420p")
        "h264_videotoolbox" -> listOf("-c:v", "h264_videotoolbox", "-q:v", (100 - crf * 2).coerceIn(30, 90).toString(), "-pix_fmt", "yuv420p")
        else -> listOf("-c:v", "libx264", "-preset", speed, "-crf", crf.toString(), "-pix_fmt", "yuv420p")
    }

    /** Encoders in `ffmpeg -encoders` (the lines „ V....D h264_nvenc …“). */
    internal fun listed(out: String): Set<String> =
        out.lines().mapNotNull { Regex("^\\s*V[.A-Z]{5}\\s+(\\S+)").find(it)?.groupValues?.get(1) }.toSet()

    private val cache = ConcurrentHashMap<String, List<Encoder>>()

    /** The hardware encoders that really work on this PC (tested once per ffmpeg), best first. */
    suspend fun available(exe: String? = Media.ffmpeg()): List<Encoder> = withContext(Dispatchers.IO) {
        val ffmpeg = exe ?: return@withContext emptyList()
        cache.getOrPut(ffmpeg) {
            val known = runCatching { listed(run(listOf(ffmpeg, "-hide_banner", "-encoders")).second) }.getOrDefault(emptySet())
            HARDWARE.filter { it.id in known && works(ffmpeg, it) }
        }
    }

    /** The encoder for an export: the best hardware one if [useHardware], else software. */
    suspend fun best(useHardware: Boolean = true, exe: String? = Media.ffmpeg()): Encoder =
        if (useHardware) available(exe).firstOrNull() ?: SOFTWARE else SOFTWARE

    /** A test encode of a fraction of a second; the driver or the card is missing when it fails. */
    private fun works(exe: String, e: Encoder): Boolean = runCatching {
        val cmd = listOf(exe, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=320x240:d=0.3:r=25") +
            args(e, 23, "medium") + listOf("-frames:v", "5", "-f", "null", "-")
        run(cmd, 20).first == 0
    }.getOrDefault(false)

    private fun run(cmd: List<String>, timeoutSec: Long = 20): Pair<Int, String> {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to out }
        return p.exitValue() to out
    }

    /** Tests: forget what was found. */
    internal fun reset() = cache.clear()
}
