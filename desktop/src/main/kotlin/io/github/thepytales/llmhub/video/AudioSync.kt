package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppLog
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Waveforms (AP-52) and synchronising two sound sources over them (AP-53): the loudness of a file in steps of
 * 1/[RATE] s, from ffmpeg (mono, 8 kHz), cached in memory and in the cache folder. [offset] finds by cross-correlation
 * how far a second recording (lapel microphone, recorder) is shifted against the camera.
 */
object AudioSync {
    /** Values per second. */
    const val RATE = 100
    private const val SAMPLE_RATE = 8000

    private val cache = ConcurrentHashMap<String, FloatArray>()

    /** Loudness envelope of [file] (peak per 10 ms, 0..1), or null without sound or ffmpeg. Blocking, cached. */
    fun envelope(file: File): FloatArray? {
        val key = file.absolutePath + "|" + file.length() + "|" + file.lastModified()
        cache[key]?.let { return it }
        val disk = File(Media.cacheDir, "welle-" + Integer.toHexString(key.hashCode()) + "-" + file.length() + ".bin")
        if (disk.isFile) runCatching {
            DataInputStream(disk.inputStream().buffered()).use { d -> FloatArray(d.readInt()) { d.readFloat() } }
        }.getOrNull()?.let { cache[key] = it; return it }
        val exe = Media.ffmpeg() ?: return null
        val env = runCatching {
            val p = ProcessBuilder(exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", file.absolutePath, "-vn", "-ac", "1", "-ar", SAMPLE_RATE.toString(), "-f", "s16le", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val per = SAMPLE_RATE / RATE
            val out = ArrayList<Float>(1 shl 14)
            val buf = ByteArray(per * 2 * 64)
            var peak = 0
            var n = 0
            p.inputStream.use { input ->
                while (true) {
                    val r = input.readNBytes(buf, 0, buf.size)
                    if (r <= 0) break
                    var k = 0
                    while (k + 1 < r) {
                        val v = (buf[k].toInt() and 0xff) or (buf[k + 1].toInt() shl 8)
                        val a = if (v < 0) -v else v
                        if (a > peak) peak = a
                        if (++n == per) { out += peak / 32768f; peak = 0; n = 0 }
                        k += 2
                    }
                    if (r < buf.size) break
                }
            }
            p.waitFor()
            if (n > 0) out += peak / 32768f
            out.toFloatArray()
        }.onFailure { AppLog.e("Wellenform ${file.name}", it) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        cache[key] = env
        runCatching { DataOutputStream(disk.outputStream().buffered()).use { d -> d.writeInt(env.size); env.forEach(d::writeFloat) } }
        return env
    }

    /** The envelope already in memory (for drawing without waiting), or null. */
    fun cached(file: File): FloatArray? = cache[file.absolutePath + "|" + file.length() + "|" + file.lastModified()]

    /**
     * Seconds by which [b] is behind [a]: an event at time t in [a] is at t + result in [b] (both envelopes at [RATE]).
     * Compares the onsets (where it gets louder), so different microphones and levels still match; searches up to
     * [maxLagSec] either way. Null when the two have nothing in common.
     */
    fun offset(a: FloatArray, b: FloatArray, maxLagSec: Double = 600.0): Double? {
        val limit = 15 * 60 * RATE
        val x = onsets(a.copyOf(minOf(a.size, limit)))
        val y = onsets(b.copyOf(minOf(b.size, limit)))
        if (x.isEmpty() || y.isEmpty()) return null
        var n = 1
        while (n < x.size + y.size) n = n shl 1
        val xr = DoubleArray(n); val xi = DoubleArray(n)
        val yr = DoubleArray(n); val yi = DoubleArray(n)
        for (i in x.indices) xr[i] = x[i]
        for (i in y.indices) yr[i] = y[i]
        fft(xr, xi, false); fft(yr, yi, false)
        // corr[d] = Σ x[k]·y[k+d]  ⇔  IFFT(conj(X)·Y)
        for (i in 0 until n) {
            val r = xr[i] * yr[i] + xi[i] * yi[i]
            val im = xr[i] * yi[i] - xi[i] * yr[i]
            xr[i] = r; xi[i] = im
        }
        fft(xr, xi, true)
        val maxLag = minOf((maxLagSec * RATE).toInt(), n / 2 - 1)
        var best = 0; var bestV = Double.NEGATIVE_INFINITY
        var sum = 0.0; var count = 0
        for (d in -maxLag..maxLag) {
            val v = xr[Math.floorMod(d, n)]
            sum += v; count++
            if (v > bestV) { bestV = v; best = d }
        }
        val norm = sqrt(x.sumOf { it * it } * y.sumOf { it * it })
        if (norm <= 0 || bestV / norm < 0.15) return null
        // Parabola through the peak and its neighbours: better than 10 ms.
        val l = xr[Math.floorMod(best - 1, n)]; val r = xr[Math.floorMod(best + 1, n)]
        val den = l - 2 * bestV + r
        val frac = if (den < 0) ((l - r) / (2 * den)).coerceIn(-0.5, 0.5) else 0.0
        return (best + frac) / RATE
    }

    /** Where it gets louder: positive change of the log loudness, without its mean. */
    private fun onsets(e: FloatArray): DoubleArray {
        if (e.size < 2) return DoubleArray(0)
        val l = DoubleArray(e.size) { ln(1e-4 + e[it]) }
        val d = DoubleArray(e.size) { i -> if (i == 0) 0.0 else max(0.0, l[i] - l[i - 1]) }
        val mean = d.average()
        for (i in d.indices) d[i] -= mean
        return d
    }

    /** In-place radix-2 FFT ([re].size a power of two); [inverse] divides by n. */
    internal fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * Math.PI / len * if (inverse) 1 else -1
            val wr = kotlin.math.cos(ang); val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k]; val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br; im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br; im[i + k + len / 2] = ai - bi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }

    /**
     * Synchronises sound [soundId] with V1 clip [clipId] over the waveforms and links them (AP-53). Returns the
     * project, or null when no match was found.
     */
    fun synchronise(p: VideoProject, clipId: String, soundId: String, muteClip: Boolean = true): VideoProject? {
        val c = p.clips.firstOrNull { it.id == clipId } ?: return null
        val a = p.audio.firstOrNull { it.id == soundId } ?: return null
        val off = offsetOf(File(c.path), File(a.path)) ?: return null
        return VideoEdit.link(p, clipId, soundId, off, muteClip)
    }

    /** Seconds by which the sound of [other] is behind that of [camera] (blocking), or null. */
    fun offsetOf(camera: File, other: File): Double? {
        val cam = envelope(camera) ?: return null
        val mic = envelope(other) ?: return null
        return offset(cam, mic)
    }
}
