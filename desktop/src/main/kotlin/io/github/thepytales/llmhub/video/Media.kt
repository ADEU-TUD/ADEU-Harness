package io.github.thepytales.llmhub.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.lokal.whisper.desktop.DesktopSpeechPlatform
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Extensions
import io.github.thepytales.llmhub.desktop.Paths
import org.jetbrains.skia.Image
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** What a media file holds (from ffprobe, or from the output of ffmpeg -i without the Videoeditor-Paket). */
data class MediaInfo(
    val duration: Double,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    /** Rotation of phone videos (ffmpeg turns them upright by itself; width/height are already swapped). */
    val rotation: Int = 0,
)

/**
 * ffmpeg and ffprobe for the video editor (AP-25): ffmpeg comes with the app (also used by the transcription),
 * ffprobe with the Videoeditor-Paket (an extension); without it the details come from `ffmpeg -i`.
 */
object Media {
    fun ffmpeg(): String? = DesktopSpeechPlatform.ffmpeg()

    /** ffprobe of the Videoeditor-Paket; during development on Linux the one of the system. */
    fun ffprobe(): String? {
        Extensions.file(Extensions.VIDEO.id, "ffprobe.exe")?.let { return it.absolutePath }
        if (Paths.windows || forceNoProbe) return null
        return listOf("/usr/bin/ffprobe", "/usr/local/bin/ffprobe").firstOrNull { File(it).canExecute() }
    }

    /** Tests: use the fallback without ffprobe. */
    @Volatile
    var forceNoProbe = false

    /** Picture files: on the timeline they stand as long as the clip is set (export and preview loop the one picture). */
    val STILLS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    /** Whether [file] is a single picture (by its extension, or a video stream without length). */
    fun isStill(file: File, info: MediaInfo? = null): Boolean {
        if (file.extension.lowercase() in STILLS) return true
        val i = info ?: probe(file)
        return i?.hasVideo == true && i.duration <= 0.05
    }

    /** Speech model of the noise reduction (Videoeditor-Paket), or null. */
    fun rnnoiseModel(): File? = Extensions.file(Extensions.VIDEO.id, "rnnoise/sh.rnnn")

    private val infos = ConcurrentHashMap<String, MediaInfo>()

    private fun run(cmd: List<String>, timeoutSec: Long = 60): Pair<Int, String> {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to out }
        return p.exitValue() to out
    }

    /** Details of [file]; null if it is no media file ffmpeg can read. */
    fun probe(file: File): MediaInfo? {
        val key = file.absolutePath + "|" + file.length() + "|" + file.lastModified()
        infos[key]?.let { return it }
        val info = runCatching { ffprobe()?.let { probeJson(it, file) } ?: probeFfmpeg(file) }
            .onFailure { AppLog.e("Video prüfen: ${file.name}", it) }.getOrNull()
        if (info != null) infos[key] = info
        return info
    }

    private fun probeJson(exe: String, file: File): MediaInfo? {
        val (code, out) = run(listOf(exe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams", file.absolutePath))
        if (code != 0) return null
        val o = JSONObject(out.substring(out.indexOf('{').coerceAtLeast(0)))
        val streams = o.optJSONArray("streams")
        var w = 0; var h = 0; var fps = 0.0; var video = false; var audio = false; var rot = 0
        for (i in 0 until (streams?.length() ?: 0)) {
            val s = streams!!.getJSONObject(i)
            when (s.optString("codec_type")) {
                "video" -> if (!video && s.optJSONObject("disposition")?.optInt("attached_pic") != 1) {
                    video = true
                    w = s.optInt("width"); h = s.optInt("height")
                    fps = rate(s.optString("avg_frame_rate")).takeIf { it > 0 } ?: rate(s.optString("r_frame_rate"))
                    rot = s.optJSONObject("tags")?.optString("rotate")?.toIntOrNull() ?: run {
                        val sd = s.optJSONArray("side_data_list")
                        (0 until (sd?.length() ?: 0)).firstNotNullOfOrNull { k -> sd!!.getJSONObject(k).takeIf { it.has("rotation") }?.optInt("rotation") } ?: 0
                    }
                }
                "audio" -> audio = true
            }
        }
        if (rot % 180 != 0) { val t = w; w = h; h = t }
        val dur = o.optJSONObject("format")?.optString("duration")?.toDoubleOrNull() ?: 0.0
        return MediaInfo(dur, w, h, fps, video, audio, rot)
    }

    private fun rate(s: String): Double {
        val p = s.split('/')
        val a = p.getOrNull(0)?.toDoubleOrNull() ?: return 0.0
        val b = p.getOrNull(1)?.toDoubleOrNull() ?: 1.0
        return if (b == 0.0) 0.0 else a / b
    }

    /** Fallback: reads the stream lines of `ffmpeg -i` (stable for many years). */
    internal fun probeFfmpeg(file: File): MediaInfo? {
        val exe = ffmpeg() ?: return null
        val (_, out) = run(listOf(exe, "-hide_banner", "-nostdin", "-i", file.absolutePath))
        return parseFfmpegInfo(out)
    }

    internal fun parseFfmpegInfo(out: String): MediaInfo? {
        val d = Regex("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)").find(out) ?: return null
        val dur = d.groupValues[1].toInt() * 3600 + d.groupValues[2].toInt() * 60 + d.groupValues[3].toDouble()
        val videoLine = out.lines().firstOrNull { "Video:" in it && "attached pic" !in it }
        val audio = out.lines().any { "Audio:" in it }
        var w = 0; var h = 0; var fps = 0.0
        videoLine?.let { l ->
            Regex(",\\s*(\\d{2,5})x(\\d{2,5})").find(l)?.let { w = it.groupValues[1].toInt(); h = it.groupValues[2].toInt() }
            Regex("([\\d.]+) fps").find(l)?.let { fps = it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        }
        val rot = Regex("rotation of (-?[\\d.]+) degrees").find(out)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            ?: Regex("rotate\\s*:\\s*(-?\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (rot % 180 != 0) { val t = w; w = h; h = t }
        return MediaInfo(dur, w, h, fps, videoLine != null, audio, rot)
    }

    private fun hash(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update((file.absolutePath + "|" + file.length() + "|" + file.lastModified()).toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    val cacheDir: File get() = File(Paths.cache, "video").apply { mkdirs() }

    /**
     * Proxy file of [file] for smooth previews (360 lines, a key frame every half second); made once in the background,
     * kept in the cache. Null while it is missing.
     */
    fun proxy(file: File): File? = File(cacheDir, "proxy-${hash(file)}.mp4").takeIf { it.isFile && it.length() > 0 }

    private val making = ConcurrentHashMap.newKeySet<String>()

    /** Makes the proxy of [file] (blocking; returns it, or null when ffmpeg fails). */
    fun makeProxy(file: File, info: MediaInfo? = probe(file)): File? {
        proxy(file)?.let { return it }
        // A picture needs none – and a one-frame MP4 of it could not be looped by the preview.
        if (info?.hasVideo != true || isStill(file, info)) return null
        val exe = ffmpeg() ?: return null
        val target = File(cacheDir, "proxy-${hash(file)}.mp4")
        if (!making.add(target.path)) return null
        try {
            val tmp = File(cacheDir, target.name + ".part.mp4")
            val (code, out) = run(
                listOf(exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", file.absolutePath,
                    "-vf", "scale=-2:360", "-c:v", "libx264", "-preset", "veryfast", "-crf", "30", "-g", "12", "-keyint_min", "12",
                    "-c:a", "aac", "-b:a", "96k", "-ac", "2", tmp.absolutePath),
                timeoutSec = 3600,
            )
            if (code != 0 || !tmp.isFile) { AppLog.i("Proxy ${file.name} fehlgeschlagen: ${out.take(300)}"); tmp.delete(); return null }
            tmp.renameTo(target)
            return target
        } finally {
            making.remove(target.path)
        }
    }

    /** One picture of [file] at [sec] (from the proxy if there is one), [height] pixels high. */
    fun frame(file: File, sec: Double, height: Int = 360): ImageBitmap? {
        val exe = ffmpeg() ?: return null
        // A picture has its one frame at 0: seeking further gave nothing (black preview, no thumbnail).
        val still = isStill(file)
        val src = if (still) file else proxy(file) ?: file
        val at = if (still) 0.0 else sec.coerceAtLeast(0.0)
        return runCatching {
            val p = ProcessBuilder(
                exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-ss", String.format(Locale.ROOT, "%.3f", at),
                "-i", src.absolutePath, "-frames:v", "1", "-vf", "scale=-2:$height", "-f", "image2pipe", "-vcodec", "png", "pipe:1",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val bytes = p.inputStream.readBytes()
            p.waitFor(20, TimeUnit.SECONDS)
            if (bytes.isEmpty()) null else Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }

    /** Small pictures for the timeline: one per clip, cached. */
    private val thumbs = ConcurrentHashMap<String, ImageBitmap>()

    fun thumbnail(file: File, sec: Double): ImageBitmap? {
        val key = file.absolutePath + "@" + "%.1f".format(Locale.ROOT, sec)
        thumbs[key]?.let { return it }
        return frame(file, sec, 72)?.also { thumbs[key] = it }
    }
}
