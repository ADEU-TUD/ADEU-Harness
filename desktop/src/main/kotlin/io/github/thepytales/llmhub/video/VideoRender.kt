package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater

/**
 * Export of the video editor (AP-25): one ffmpeg run builds the whole film – the clips cut and joined (with
 * cross-dissolves or through black), the TUD titles and lower thirds faded in, subtitles burned in if wanted, music
 * mixed under, noise reduced and the loudness evened out – into an MP4 (H.264/AAC, fast start for the web).
 */
object VideoRender {
    /** Target of the export: height in lines (width from the project's aspect ratio), quality and audio rate. */
    data class Preset(val id: String, val label: String, val height: Int, val crf: Int, val audioKbps: Int, val speed: String, val hint: String)

    val PRESETS = listOf(
        Preset("opal", trLater("OPAL (kleine Datei, 720p)"), 720, 26, 128, "medium", trLater("Gut für die Lernplattform: etwa 5–10 MB pro Minute.")),
        Preset("youtube", trLater("YouTube (Full HD, 1080p)"), 1080, 20, 192, "medium", trLater("Hohe Qualität zum Hochladen; YouTube rechnet selbst noch einmal um.")),
        Preset("projekt", trLater("Wie das Projekt (beste Qualität)"), 0, 18, 192, "slow", trLater("Auflösung des Projekts, große Datei – z. B. zum Archivieren.")),
    )

    fun preset(id: String?) = PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()

    private fun f(v: Double) = String.format(Locale.ROOT, "%.3f", v)

    /** A path inside a filter graph: forward slashes, ':' and quotes escaped (Windows drive letters). */
    internal fun filterPath(file: File): String = file.absolutePath.replace('\\', '/').replace(":", "\\:")

    private fun even(v: Int) = (v / 2) * 2

    /** Size of the export for [preset]: [VideoProject.height] or the preset's height, width from the aspect ratio. */
    fun size(p: VideoProject, preset: Preset): Pair<Int, Int> {
        val h = if (preset.height <= 0) p.height else preset.height
        val w = even(Math.round(h.toDouble() * p.width / p.height).toInt())
        return w to even(h)
    }

    /** The ffmpeg arguments (without the program) and the files they need, written into [work]. */
    class Plan(val args: List<String>, val duration: Double)

    /**
     * Builds the command for [p]. [info] gives the details of each source (has it sound, is it a picture?);
     * [graphFile] receives the filter graph (long graphs exceed the command line of Windows).
     */
    fun plan(p: VideoProject, preset: Preset, out: File, work: File, info: (File) -> MediaInfo?, rnnoise: File? = Media.rnnoiseModel(), scriptOption: String = "-filter_complex_script"): Plan {
        require(p.clips.isNotEmpty()) { tr("Das Projekt hat noch keine Clips.") }
        work.mkdirs()
        val (w, h) = size(p, preset)
        val fps = p.fps.coerceIn(10, 60)
        val total = p.duration
        val args = mutableListOf("-nostdin", "-hide_banner", "-y")
        val graph = mutableListOf<String>()
        var input = 0

        // Clips: one input each (seeking in the input is quick and exact when the picture is encoded again).
        p.clips.forEachIndexed { i, c ->
            val src = File(c.path)
            val mi = info(src)
            val still = Media.isStill(src, mi)
            if (still) args += listOf("-loop", "1", "-framerate", fps.toString())
            if (!still) args += listOf("-ss", f(c.inSec))
            args += listOf("-t", f(c.length), "-i", src.absolutePath)
            val k = input++
            graph += if (mi?.hasVideo == false) "color=c=black:s=${w}x$h:r=$fps:d=${f(c.length)},format=yuv420p,setsar=1[v$i]"
            // tpad + trim: the picture is exactly as long as the clip, like its sound (apad + atrim below) – a clip
            // set longer than its file (or a file whose last frames are missing) used to end early, and every clip
            // after it ran ahead of its sound at a cross-dissolve.
            else "[$k:v]scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,fps=$fps,format=yuv420p,setpts=PTS-STARTPTS," +
                "tpad=stop_mode=clone:stop_duration=${f(c.length)},trim=duration=${f(c.length)},setpts=PTS-STARTPTS[v$i]"
            graph += if (mi?.hasAudio == true && !c.muted) {
                "[$k:a]aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo,volume=${f(c.volume.toDouble())},asetpts=PTS-STARTPTS,apad,atrim=0:${f(c.length)}[a$i]"
            } else "anullsrc=channel_layout=stereo:sample_rate=48000,atrim=0:${f(c.length)}[a$i]"
        }
        // Join: cross-dissolve / through black where a transition is set, otherwise a hard cut.
        var v = "v0"
        var a = "a0"
        var len = p.clips[0].length
        for (i in 1 until p.clips.size) {
            val t = p.transitionOf(i)
            val c = p.clips[i]
            if (t > 0) {
                val kind = if (c.transition == "black") "fadeblack" else "fade"
                graph += "[$v][v$i]xfade=transition=$kind:duration=${f(t)}:offset=${f(len - t)}[vx$i]"
                graph += "[$a][a$i]acrossfade=d=${f(t)}[ax$i]"
                len += c.length - t
            } else {
                graph += "[$v][$a][v$i][a$i]concat=n=2:v=1:a=1[vx$i][ax$i]"
                len += c.length
            }
            v = "vx$i"
            a = "ax$i"
        }
        if (p.fadeEnds && len > 2.0) {
            graph += "[$v]fade=t=in:st=0:d=0.5,fade=t=out:st=${f(len - 0.6)}:d=0.6[vf]"
            graph += "[$a]afade=t=in:st=0:d=0.4,afade=t=out:st=${f(len - 0.6)}:d=0.6[af]"
            v = "vf"; a = "af"
        }
        // Titles and lower thirds: transparent pictures, faded in and out.
        p.overlays.filter { it.startSec < len && it.durationSec > 0.2 }.forEachIndexed { k, o ->
            val png = File(work, "titel-$k.png")
            png.writeBytes(VideoTitles.png(VideoTitles.design(o, w, h), w))
            args += listOf("-loop", "1", "-framerate", fps.toString(), "-t", f(minOf(o.endSec, len) + 0.1), "-i", png.absolutePath)
            val idx = input++
            val fade = minOf(0.4, o.durationSec / 4)
            val s = o.startSec
            val e = minOf(o.endSec, len)
            graph += "[$idx:v]format=rgba,fade=t=in:st=${f(s)}:d=${f(fade)}:alpha=1,fade=t=out:st=${f(e - fade)}:d=${f(fade)}:alpha=1[t$k]"
            graph += "[$v][t$k]overlay=0:0:enable='between(t,${f(s)},${f(e)})':eof_action=pass[vt$k]"
            v = "vt$k"
        }
        // Subtitles burned in: one picture per cue, joined as one picture stream (concat list with durations).
        val cues = p.subtitles.filter { it.endSec > it.startSec && it.startSec < len }.sortedBy { it.startSec }
        if (p.burnSubtitles && cues.isNotEmpty()) {
            val blank = File(work, "leer.png").apply { writeBytes(VideoTitles.blank(w, h)) }
            val list = StringBuilder("ffconcat version 1.0\n")
            // Quoted for the concat demuxer: a ' in the path (a user folder like „O'Neil“) is written as '\''.
            fun quoted(file: File) = "file '" + file.absolutePath.replace('\\', '/').replace("'", "'\\''") + "'\n"
            fun entry(file: File, dur: Double) {
                list.append(quoted(file))
                list.append("duration ").append(f(dur.coerceAtLeast(0.04))).append('\n')
            }
            var t = 0.0
            cues.forEachIndexed { k, cue ->
                val start = maxOf(cue.startSec, t)
                val end = minOf(cue.endSec, len)
                if (end <= start) return@forEachIndexed
                if (start - t > 0.02) entry(blank, start - t)
                val png = File(work, "untertitel-$k.png").apply { writeBytes(VideoTitles.png(VideoTitles.subtitleDesign(cue.text, w, h, p.subtitleRaised(cue)), w)) }
                entry(png, end - start)
                t = end
            }
            entry(blank, maxOf(len - t, 0.1))
            list.append(quoted(blank))
            val listFile = File(work, "untertitel.ffconcat").apply { writeText(list.toString()) }
            args += listOf("-f", "concat", "-safe", "0", "-i", listFile.absolutePath)
            val idx = input++
            graph += "[$idx:v]format=rgba,setpts=PTS-STARTPTS[subs]"
            graph += "[$v][subs]overlay=0:0:eof_action=pass[vs]"
            v = "vs"
        }
        graph += "[$v]format=yuv420p[vout]"
        // Music / voice-over under the video's sound.
        val music = p.audio.filter { it.startSec < len && it.length > 0.1 }
        music.forEachIndexed { k, m ->
            args += listOf("-ss", f(m.inSec), "-t", f(minOf(m.length, len - m.startSec)), "-i", File(m.path).absolutePath)
            val idx = input++
            val d = minOf(m.length, len - m.startSec)
            val fade = m.fadeSec.coerceIn(0.0, d / 3)
            val delay = Math.round(m.startSec * 1000)
            graph += "[$idx:a]aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo,volume=${f(m.volume.toDouble())}," +
                (if (fade > 0) "afade=t=in:st=0:d=${f(fade)},afade=t=out:st=${f(d - fade)}:d=${f(fade)}," else "") +
                "asetpts=PTS-STARTPTS,adelay=$delay|$delay[m$k]"
        }
        if (music.isNotEmpty()) {
            graph += "[$a]" + music.indices.joinToString("") { "[m$it]" } + "amix=inputs=${music.size + 1}:duration=first:dropout_transition=0:normalize=0[amx]"
            a = "amx"
        }
        // Sound of the whole film: noise reduction, level, even loudness.
        val chain = mutableListOf<String>()
        when (p.sound.denoise) {
            "leicht" -> chain += if (rnnoise != null) "arnndn=m='${filterPath(rnnoise)}':mix=0.7" else "afftdn=nr=12:nf=-40"
            "stark" -> chain += if (rnnoise != null) "arnndn=m='${filterPath(rnnoise)}'" else "afftdn=nr=24:nf=-30"
        }
        if (p.sound.gainDb != 0f) chain += "volume=${f(p.sound.gainDb.toDouble())}dB"
        if (p.sound.normalize) chain += listOf("loudnorm=I=-16:TP=-1.5:LRA=11", "aresample=48000")
        graph += "[$a]" + (chain.ifEmpty { listOf("anull") }).joinToString(",") + "[aout]"

        val script = File(work, "filter.txt").apply { writeText(graph.joinToString(";\n")) }
        args += listOf(scriptOption, script.absolutePath, "-map", "[vout]", "-map", "[aout]")
        args += listOf(
            "-c:v", "libx264", "-preset", preset.speed, "-crf", preset.crf.toString(), "-pix_fmt", "yuv420p", "-r", fps.toString(),
            "-c:a", "aac", "-b:a", "${preset.audioKbps}k", "-ar", "48000", "-movflags", "+faststart",
            "-metadata", "title=${p.title}", "-progress", "pipe:1", "-nostats", out.absolutePath,
        )
        return Plan(args, len)
    }

    /** `-filter_complex_script` up to ffmpeg 6; newer versions read option values from files with `-/filter_complex`. */
    fun scriptOption(exe: String): String = runCatching {
        val p = ProcessBuilder(exe, "-hide_banner", "-version").redirectErrorStream(true).start()
        val first = p.inputStream.bufferedReader().readLine().orEmpty()
        p.waitFor(10, TimeUnit.SECONDS)
        val major = Regex("version\\s+n?(\\d+)").find(first)?.groupValues?.get(1)?.toIntOrNull() ?: 7
        if (major >= 7) "-/filter_complex" else "-filter_complex_script"
    }.getOrDefault("-/filter_complex")

    /**
     * Renders [p] into [out]; [progress] gets 0..1. Throws with the end of ffmpeg's messages when it fails; a cancelled
     * coroutine stops ffmpeg.
     */
    suspend fun render(p: VideoProject, preset: Preset, out: File, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val exe = Media.ffmpeg() ?: throw IllegalStateException(tr("ffmpeg fehlt in der App."))
        val work = File(Media.cacheDir, "export-${System.nanoTime()}")
        val tmp = File(out.parentFile, out.nameWithoutExtension + ".teil.mp4")
        try {
            val plan = plan(p, preset, tmp, work, { Media.probe(it) }, scriptOption = scriptOption(exe))
            val proc = ProcessBuilder(listOf(exe) + plan.args).redirectError(File(work, "ffmpeg.log")).start()
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        coroutineContext.ensureActive()
                        val us = line.substringAfter("out_time_us=", "").toLongOrNull() ?: line.substringAfter("out_time_ms=", "").toLongOrNull()
                        if (us != null && plan.duration > 0) progress((us / 1_000_000.0 / plan.duration).toFloat().coerceIn(0f, 1f))
                    }
                }
                proc.waitFor()
            } finally {
                if (proc.isAlive) proc.destroyForcibly()
            }
            if (proc.exitValue() != 0 || !tmp.isFile) {
                val log = File(work, "ffmpeg.log").takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() }.takeLast(6).joinToString("\n")
                AppLog.i("Videoexport fehlgeschlagen:\n$log")
                throw IllegalStateException(tr("ffmpeg konnte das Video nicht schreiben.") + if (log.isNotBlank()) "\n$log" else "")
            }
            out.delete()
            if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true).also { tmp.delete() }
            progress(1f)
            out
        } finally {
            tmp.delete()
            work.deleteRecursively()
        }
    }

    // ── Subtitles as files ──

    private fun stamp(sec: Double, sep: Char): String {
        val ms = Math.round(sec.coerceAtLeast(0.0) * 1000)
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, sep, ms % 1000)
    }

    fun srt(p: VideoProject): String = p.subtitles.sortedBy { it.startSec }.mapIndexed { i, c ->
        "${i + 1}\n${stamp(c.startSec, ',')} --> ${stamp(c.endSec, ',')}\n${c.text.trim()}"
    }.joinToString("\n\n") + "\n"

    fun vtt(p: VideoProject): String = "WEBVTT\n\n" + p.subtitles.sortedBy { it.startSec }.joinToString("\n\n") { c ->
        "${stamp(c.startSec, '.')} --> ${stamp(c.endSec, '.')}\n${c.text.trim()}"
    } + "\n"

    /** Reads an SRT or VTT file into cues. */
    fun parseSubtitles(text: String): List<SubtitleCue> {
        val re = Regex("(\\d{1,2}:)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}:)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})")
        fun sec(h: String, m: String, s: String, ms: String) = (h.removeSuffix(":").toIntOrNull() ?: 0) * 3600.0 + m.toInt() * 60 + s.toInt() + ms.padEnd(3, '0').toInt() / 1000.0
        val out = mutableListOf<SubtitleCue>()
        val blocks = text.replace("\r", "").split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.lines()
            val idx = lines.indexOfFirst { re.containsMatchIn(it) }
            if (idx < 0) continue
            val m = re.find(lines[idx])!!.groupValues
            val body = lines.drop(idx + 1).joinToString("\n").replace(Regex("<[^>]+>"), "").trim()
            if (body.isNotEmpty()) out += SubtitleCue(sec(m[1], m[2], m[3], m[4]), sec(m[5], m[6], m[7], m[8]), body)
        }
        return out
    }
}
