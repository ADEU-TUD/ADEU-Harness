package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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

    /**
     * A source fitted into [w]×[h] with black bars (a clip of V1 at full size). The single pictures of the preview take
     * the same filter ([previewStillArgs]), so they lie exactly where the played pictures lie (AP-70: the bars of a
     * portrait clip used to differ by a pixel – the pad of the graph rounds to even pixels).
     */
    internal fun fitInto(w: Int, h: Int) = "scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=black"

    /** Size of the export for [preset]: [VideoProject.height] or the preset's height, width from the aspect ratio. */
    fun size(p: VideoProject, preset: Preset): Pair<Int, Int> {
        val h = if (preset.height <= 0) p.height else preset.height
        val w = even(Math.round(h.toDouble() * p.width / p.height).toInt())
        return w to even(h)
    }
    /** The ffmpeg arguments (without the program) and the files they need, written into [work]. */
    class Plan(val args: List<String>, val duration: Double)

    /**
     * A piece of the timeline for the preview (AP-52): [from]..[to] seconds. [titles]: titles and subtitles in the picture
     * (the preview lays them over itself, with the same drawing), [loudness]: even loudness (needs seconds of look-ahead,
     * so only in the export).
     */
    data class Window(val from: Double, val to: Double, val titles: Boolean = false, val loudness: Boolean = false)

    /** Inputs and filter graph of a film: the picture ends in `[vout]`, the sound in `[aout]`. */
    class Graph(val inputs: List<String>, val lines: List<String>, val duration: Double, val chapters: Int?)

    /**
     * The filter graph of [p] at [w]×[h], [fps] (AP-25, AP-52): V1 cut and joined (cross-dissolve, through black), each
     * clip placed by its [Frame]; the pictures of V2 … laid over it in their order (position, size, crop, opacity,
     * fades); titles and subtitles; the sound of V1, of the pictures above and of A1 … mixed, cleaned and evened out.
     * Hidden tracks give no picture, muted ones no sound. [window] gives only a piece (the preview), [source] the file
     * to read for a source (the preview's proxies).
     */
    fun graph(
        p0: VideoProject, w: Int, h: Int, fps: Int, work: File, info: (File) -> MediaInfo?, rnnoise: File? = Media.rnnoiseModel(),
        window: Window? = null, source: (File) -> File = { it },
    ): Graph {
        require(p0.clips.isNotEmpty()) { tr("Das Projekt hat noch keine Clips.") }
        work.mkdirs()
        val p = p0.linked()
        val full = p.duration
        val wanted = window?.from?.coerceIn(0.0, maxOf(0.0, full - 0.04)) ?: 0.0
        val to = window?.to?.coerceIn(wanted + 0.04, full) ?: full
        // A window that begins in a transition starts half a second before it, so the cross-dissolve is there as in the
        // export; the extra piece is cut off at the end ([skip]).
        val from = run {
            val s = p.starts()
            val k = s.indices.lastOrNull { s[it] <= wanted + 1e-6 } ?: 0
            if (k > 0 && wanted < s[k] + p.transitionOf(k) - 1e-6) maxOf(0.0, s[k] - 0.5) else wanted
        }
        val skip = wanted - from
        val titles = window == null || window.titles
        val args = mutableListOf<String>()
        val graph = mutableListOf<String>()
        var input = 0
        fun hidden(track: String) = p.track(track).hidden
        fun muted(track: String) = p.track(track).muted
        fun box(fr: Frame) = even(maxOf(2, Math.round(w * fr.scale.coerceIn(0.02f, 4f)))) to even(maxOf(2, Math.round(h * fr.scale.coerceIn(0.02f, 4f))))
        fun crop(fr: Frame): String {
            if (!fr.cropped) return ""
            val l = fr.cropL.coerceIn(0f, 0.45f); val r = fr.cropR.coerceIn(0f, 0.45f); val t = fr.cropT.coerceIn(0f, 0.45f); val b = fr.cropB.coerceIn(0f, 0.45f)
            return "crop=iw*${f((1 - l - r).toDouble())}:ih*${f((1 - t - b).toDouble())}:iw*${f(l.toDouble())}:ih*${f(t.toDouble())},"
        }
        fun place(fr: Frame) = "x=${f(w * fr.x.toDouble())}-w/2:y=${f(h * fr.y.toDouble())}-h/2"
        val audioFormat = "aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo"

        // V1 from the window's start: the clip under it begins there (a transition into it is left out).
        val starts = p.starts()
        val first = starts.indices.lastOrNull { starts[it] <= from + 1e-6 } ?: 0
        val last = maxOf(first, starts.indices.lastOrNull { starts[it] < to - 1e-6 } ?: first)
        class Seg(val clip: VideoClip, val head: Double, val inSec: Double, val length: Double, val trans: Double)
        val segs = (first..last).map { k ->
            val c = p.clips[k]
            val head = if (k == first) (from - starts[k]).coerceIn(0.0, maxOf(0.0, c.length - 0.04)) else 0.0
            Seg(c, head, c.inSec + head, maxOf(0.04, c.length - head), if (k == first) 0.0 else p.transitionOf(k))
        }
        segs.forEachIndexed { i, s ->
            val c = s.clip
            val src = File(c.path)
            val mi = info(src)
            val still = Media.isStill(src, mi)
            // Effects (AP-54): speed reads more of the source, reverse reads backwards from Out, a frozen frame little.
            val fx = c.fx.takeIf { !it.isNone }
            val (ss, dur) = if (fx == null || still) s.inSec to s.length else VideoFx.inputRange(c, s.head, s.length)
            if (still) args += listOf("-loop", "1", "-framerate", fps.toString())
            if (!still) args += listOf("-ss", f(ss))
            args += listOf("-t", f(if (still) s.length else dur), "-i", (if (still) src else source(src)).absolutePath)
            val k = input++
            var vin = "[$k:v]"
            if (fx != null && fx.picture && mi?.hasVideo != false && !hidden("V1")) {
                val (bw, bh) = VideoFx.workSize(mi, w, h)
                graph += VideoFx.pictureLines(vin, "e$i", fx, VideoFx.span(c, s.head, s.length, still), bw, bh, fps, still)
                vin = "[e$i]"
            }
            // tpad + trim: the picture is exactly as long as the clip, like its sound (apad + atrim below) – a clip
            // set longer than its file (or a file whose last frames are missing) used to end early, and every clip
            // after it ran ahead of its sound at a cross-dissolve.
            val fit = "setsar=1,fps=$fps,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=${f(s.length)},trim=duration=${f(s.length)},setpts=PTS-STARTPTS"
            graph += when {
                mi?.hasVideo == false || hidden("V1") -> "color=c=black:s=${w}x$h:r=$fps:d=${f(s.length)},format=yuv420p,setsar=1[v$i]"
                c.frame.isFull -> "$vin${fitInto(w, h)},$fit,format=yuv420p[v$i]"
                else -> {
                    val (bw, bh) = box(c.frame)
                    val alpha = if (c.frame.opacity < 0.999f) ",colorchannelmixer=aa=${f(c.frame.opacity.coerceIn(0f, 1f).toDouble())}" else ""
                    "$vin${crop(c.frame)}scale=$bw:$bh:force_original_aspect_ratio=decrease:force_divisible_by=2,format=rgba$alpha,$fit[fg$i];\n" +
                        "color=c=black:s=${w}x$h:r=$fps:d=${f(s.length)}[bg$i];\n" +
                        "[bg$i][fg$i]overlay=${place(c.frame)}:eof_action=pass,format=yuv420p,setsar=1[v$i]"
                }
            }
            val tempo = if (still) emptyList() else VideoFx.soundFilters(c.speed, c.fx.reverse, c.fx.voice)
            graph += if (mi?.hasAudio == true && !c.muted && !muted("V1") && !c.fx.freeze) {
                val extra = if (tempo.isEmpty()) "" else tempo.joinToString(",", postfix = ",asetpts=PTS-STARTPTS,")
                "[$k:a]$audioFormat,volume=${f(c.volume.toDouble())},asetpts=PTS-STARTPTS,${extra}apad,atrim=0:${f(s.length)}[a$i]"
            } else "anullsrc=channel_layout=stereo:sample_rate=48000,atrim=0:${f(s.length)}[a$i]"
        }
        // Join: cross-dissolve / through black where a transition is set, otherwise a hard cut.
        var v = "v0"
        var a = "a0"
        var len = segs[0].length
        for (i in 1 until segs.size) {
            val t = segs[i].trans
            if (t > 0) {
                val kind = if (segs[i].clip.transition == "black") "fadeblack" else "fade"
                graph += "[$v][v$i]xfade=transition=$kind:duration=${f(t)}:offset=${f(len - t)}[vx$i]"
                graph += "[$a][a$i]acrossfade=d=${f(t)}[ax$i]"
                len += segs[i].length - t
            } else {
                graph += "[$v][$a][v$i][a$i]concat=n=2:v=1:a=1[vx$i][ax$i]"
                len += segs[i].length
            }
            v = "vx$i"
            a = "ax$i"
        }
        if (p.fadeEnds && full > 2.0) {
            val fadeOut = full - 0.6 - from
            val vf = listOfNotNull("fade=t=in:st=0:d=0.5".takeIf { from < 0.01 }, "fade=t=out:st=${f(maxOf(0.0, fadeOut))}:d=0.6".takeIf { fadeOut < to - from })
            val af = listOfNotNull("afade=t=in:st=0:d=0.4".takeIf { from < 0.01 }, "afade=t=out:st=${f(maxOf(0.0, fadeOut))}:d=0.6".takeIf { fadeOut < to - from })
            if (vf.isNotEmpty()) { graph += "[$v]${vf.joinToString(",")}[vf]"; v = "vf" }
            if (af.isNotEmpty()) { graph += "[$a]${af.joinToString(",")}[af]"; a = "af" }
        }
        if (window != null) {
            len = to - from
            graph += "[$v]trim=duration=${f(len)},setpts=PTS-STARTPTS[vw]"
            graph += "[$a]atrim=duration=${f(len)},asetpts=PTS-STARTPTS[aw]"
            v = "vw"; a = "aw"
        }
        val mix = mutableListOf<String>()
        // Pictures on V2 …: from the lowest track up, each from its start (setpts) over what is below.
        p.layers.filter { !hidden("V${it.track}") && it.endSec > from + 1e-6 && it.startSec < to - 1e-6 && it.length > 0.04 }
            .sortedWith(compareBy({ it.track }, { it.startSec })).forEachIndexed { j, l ->
                val cut = (from - l.startSec).coerceAtLeast(0.0)
                val start = (l.startSec - from).coerceAtLeast(0.0)
                val length = minOf(l.length - cut, len - start)
                if (length < 0.04) return@forEachIndexed
                val src = File(l.path)
                val mi = info(src)
                val still = Media.isStill(src, mi)
                if (still) args += listOf("-loop", "1", "-framerate", fps.toString()) else args += listOf("-ss", f(l.inSec + cut))
                args += listOf("-t", f(length), "-i", (if (still) src else source(src)).absolutePath)
                val k = input++
                if (mi?.hasVideo != false) {
                    val (bw, bh) = box(l.frame)
                    val chain = StringBuilder("[$k:v]${crop(l.frame)}scale=$bw:$bh:force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1,fps=$fps,format=rgba,setpts=PTS-STARTPTS")
                    if (l.frame.opacity < 0.999f) chain.append(",colorchannelmixer=aa=").append(f(l.frame.opacity.coerceIn(0f, 1f).toDouble()))
                    val fd = minOf(l.fadeSec, l.length / 4)
                    if (fd > 0.04) {
                        if (cut <= 1e-6) chain.append(",fade=t=in:st=0:d=${f(fd)}:alpha=1")
                        val endLocal = l.length - cut
                        if (endLocal <= length + 1e-6) chain.append(",fade=t=out:st=${f(endLocal - fd)}:d=${f(fd)}:alpha=1")
                    }
                    chain.append(",setpts=PTS+${f(start)}/TB[l$j]")
                    graph += chain.toString()
                    graph += "[$v][l$j]overlay=${place(l.frame)}:eof_action=pass[vl$j]"
                    v = "vl$j"
                }
                if (mi?.hasAudio == true && !l.muted && !muted("V${l.track}")) {
                    val delay = Math.round(start * 1000)
                    graph += "[$k:a]$audioFormat,volume=${f(l.volume.toDouble())},asetpts=PTS-STARTPTS,adelay=$delay|$delay[la$j]"
                    mix += "la$j"
                }
            }
        if (titles) {
            // Titles and lower thirds: transparent pictures, faded in and out.
            p.overlays.filter { it.endSec - from > 0.2 && it.startSec - from < len && it.durationSec > 0.2 }.forEachIndexed { k, o ->
                val png = File(work, "titel-$k.png")
                png.writeBytes(VideoTitles.png(VideoTitles.design(o, w, h), w))
                val s = o.startSec - from
                val e = minOf(o.endSec - from, len)
                args += listOf("-loop", "1", "-framerate", fps.toString(), "-t", f(e + 0.1), "-i", png.absolutePath)
                val idx = input++
                val fade = minOf(0.4, o.durationSec / 4)
                val fades = listOfNotNull("fade=t=in:st=${f(s)}:d=${f(fade)}:alpha=1".takeIf { s >= 0 }, "fade=t=out:st=${f(e - fade)}:d=${f(fade)}:alpha=1")
                graph += "[$idx:v]format=rgba,${fades.joinToString(",")}[t$k]"
                graph += "[$v][t$k]overlay=0:0:enable='between(t,${f(maxOf(0.0, s))},${f(e)})':eof_action=pass[vt$k]"
                v = "vt$k"
            }
            // Subtitles burned in: one picture per cue, joined as one picture stream (concat list with durations).
            val cues = p.subtitles.filter { it.endSec > it.startSec && it.endSec > from && it.startSec - from < len }.sortedBy { it.startSec }
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
                    val start = maxOf(cue.startSec - from, t)
                    val end = minOf(cue.endSec - from, len)
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
        }
        if (skip > 1e-6) { graph += "[$v]trim=start=${f(skip)},setpts=PTS-STARTPTS[vh]"; v = "vh" }
        graph += "[$v]format=yuv420p[vout]"
        // Music, voice-over, a second microphone on A1 … under the video's sound.
        p.audio.filter { !muted("A${it.track}") && it.length > 0.1 && it.endSec > from + 1e-6 && it.startSec - from < len }.forEachIndexed { k, m ->
            val cut = (from - m.startSec).coerceAtLeast(0.0)
            val start = (m.startSec - from).coerceAtLeast(0.0)
            val d = minOf(m.length - cut, len - start)
            if (d < 0.05) return@forEachIndexed
            // Speed and direction of the sound (AP-54; linked sound has its clip's), voice of the sound or of its clip.
            val sp = m.speed.coerceIn(0.25, 4.0)
            val voice = m.linkedTo?.let { l -> p.clips.firstOrNull { it.id == l }?.fx?.voice }?.takeIf { abs(it) > 0.01f } ?: m.voice
            val tempo = VideoFx.soundFilters(sp, m.reverse, voice)
            val ss = if (m.reverse) maxOf(0.0, m.outSec - (cut + d) * sp) else m.inSec + cut * sp
            args += listOf("-ss", f(ss), "-t", f(d * sp), "-i", File(m.path).let { if (Media.isStill(it)) it else source(it) }.absolutePath)
            val idx = input++
            val fade = m.fadeSec.coerceIn(0.0, m.length / 3)
            val fades = buildString {
                if (fade > 0 && cut <= 1e-6) append("afade=t=in:st=0:d=${f(fade)},")
                val endLocal = m.length - cut
                if (fade > 0 && endLocal <= d + 1e-6) append("afade=t=out:st=${f(endLocal - fade)}:d=${f(fade)},")
            }
            val delay = Math.round(start * 1000)
            graph += if (tempo.isEmpty()) "[$idx:a]$audioFormat,volume=${f(m.volume.toDouble())},${fades}asetpts=PTS-STARTPTS,adelay=$delay|$delay[m$k]"
            else "[$idx:a]$audioFormat,volume=${f(m.volume.toDouble())},asetpts=PTS-STARTPTS,${tempo.joinToString(",")},asetpts=PTS-STARTPTS,${fades}adelay=$delay|$delay[m$k]"
            mix += "m$k"
        }
        if (mix.isNotEmpty()) {
            graph += "[$a]" + mix.joinToString("") { "[$it]" } + "amix=inputs=${mix.size + 1}:duration=first:dropout_transition=0:normalize=0[amx]"
            a = "amx"
        }
        // Sound of the whole film: noise reduction, level, even loudness.
        val chain = mutableListOf<String>()
        if (skip > 1e-6) chain += listOf("atrim=start=${f(skip)}", "asetpts=PTS-STARTPTS")
        when (p.sound.denoise) {
            "leicht" -> chain += if (rnnoise != null) "arnndn=m='${filterPath(rnnoise)}':mix=0.7" else "afftdn=nr=12:nf=-40"
            "stark" -> chain += if (rnnoise != null) "arnndn=m='${filterPath(rnnoise)}'" else "afftdn=nr=24:nf=-30"
        }
        if (p.sound.gainDb != 0f) chain += "volume=${f(p.sound.gainDb.toDouble())}dB"
        if (p.sound.normalize && (window == null || window.loudness)) chain += listOf("loudnorm=I=-16:TP=-1.5:LRA=11", "aresample=48000")
        graph += "[$a]" + (chain.ifEmpty { listOf("anull") }).joinToString(",") + "[aout]"
        // Chapters from the markers (AP-53): an FFMETADATA file as one more input.
        var chapters: Int? = null
        val marks = if (window == null) VideoEdit.chapters(p) else emptyList()
        if (marks.isNotEmpty()) {
            val meta = StringBuilder(";FFMETADATA1\n")
            marks.forEach { (s, e, t) ->
                val clean = t.replace(Regex("[=;#\\\\\n]")) { m -> if (m.value == "\n") " " else "\\" + m.value }
                meta.append("[CHAPTER]\nTIMEBASE=1/1000\nSTART=${Math.round(s * 1000)}\nEND=${Math.round(e * 1000)}\ntitle=$clean\n")
            }
            val file = File(work, "kapitel.txt").apply { writeText(meta.toString()) }
            args += listOf("-f", "ffmetadata", "-i", file.absolutePath)
            chapters = input++
        }
        return Graph(args, graph, len - skip, chapters)
    }

    /**
     * Builds the command for [p]. [info] gives the details of each source (has it sound, is it a picture?);
     * the filter graph goes into a file in [work] (long graphs exceed the command line of Windows).
     */
    fun plan(
        p: VideoProject, preset: Preset, out: File, work: File, info: (File) -> MediaInfo?, rnnoise: File? = Media.rnnoiseModel(),
        scriptOption: String = "-filter_complex_script", encoder: Encoders.Encoder = Encoders.SOFTWARE, audioOnly: Boolean = false,
    ): Plan {
        require(p.clips.isNotEmpty()) { tr("Das Projekt hat noch keine Clips.") }
        val (w, h) = size(p, preset)
        val fps = p.fps.coerceIn(10, 60)
        val g = graph(p, w, h, fps, work, info, rnnoise)
        val args = mutableListOf("-nostdin", "-hide_banner", "-y")
        args += g.inputs
        val script = File(work, "filter.txt").apply { writeText(g.lines.joinToString(";\n")) }
        // Only the sound (AP-57, „nur Ton“): the picture is built and thrown away, the sound goes into an M4A.
        if (audioOnly) {
            File(work, "filter.txt").appendText(";\n[vout]nullsink")
            args += listOf(scriptOption, script.absolutePath, "-map", "[aout]")
            g.chapters?.let { args += listOf("-map_chapters", it.toString()) }
            args += listOf("-vn", "-c:a", "aac", "-b:a", "${preset.audioKbps}k", "-ar", "48000", "-movflags", "+faststart", "-metadata", "title=${p.title}", "-progress", "pipe:1", "-nostats", out.absolutePath)
            return Plan(args, g.duration)
        }
        args += listOf(scriptOption, script.absolutePath, "-map", "[vout]", "-map", "[aout]")
        g.chapters?.let { args += listOf("-map_chapters", it.toString()) }
        // Hardware encoder (AP-57) or x264, with the preset's quality.
        args += Encoders.args(encoder, preset.crf, preset.speed)
        args += listOf(
            "-r", fps.toString(),
            "-c:a", "aac", "-b:a", "${preset.audioKbps}k", "-ar", "48000", "-movflags", "+faststart",
            "-metadata", "title=${p.title}", "-progress", "pipe:1", "-nostats", out.absolutePath,
        )
        return Plan(args, g.duration)
    }

    /** What a preview process gives: the pictures (RGBA), the sound (PCM 16 bit stereo, 44.1 kHz) or one picture (RGBA). */
    enum class PreviewOut { VIDEO, AUDIO, STILL }

    /** Height of the preview pictures and their rate. */
    const val PREVIEW_HEIGHT = 360
    const val PREVIEW_FPS = 25

    /** Size of the preview pictures for [p]. */
    fun previewSize(p: VideoProject) = size(p, Preset("vorschau", "", PREVIEW_HEIGHT, 30, 96, "veryfast", ""))

    /**
     * The ffmpeg arguments of the preview (AP-52): the same graph as the export for [window], at preview size from the
     * proxies, played at [speed] (J/K/L). Video and sound come from two processes with the same graph; each throws the
     * other part away.
     */
    fun previewArgs(p: VideoProject, window: Window, out: PreviewOut, work: File, info: (File) -> MediaInfo?, scriptOption: String, speed: Double = 1.0, source: (File) -> File = { Media.proxy(it) ?: it }): List<String> {
        val (w, h) = previewSize(p)
        val g = graph(p, w, h, PREVIEW_FPS, work, info, Media.rnnoiseModel(), window, source)
        val lines = g.lines.toMutableList()
        val sp = speed.coerceIn(0.25, 4.0)
        val args = mutableListOf("-nostdin", "-hide_banner", "-loglevel", "error")
        args += g.inputs
        when (out) {
            PreviewOut.VIDEO, PreviewOut.STILL -> {
                lines += "[aout]anullsink"
                lines += if (abs(sp - 1.0) > 1e-3) "[vout]setpts=PTS/${f(sp)},fps=$PREVIEW_FPS,format=rgba[pv]" else "[vout]format=rgba[pv]"
            }
            PreviewOut.AUDIO -> {
                lines += "[vout]nullsink"
                lines += if (abs(sp - 1.0) > 1e-3) "[aout]atempo=${f(sp)},aresample=44100[pa]" else "[aout]aresample=44100[pa]"
            }
        }
        val script = File(work, "vorschau-${out.name.lowercase()}.txt").apply { writeText(lines.joinToString(";\n")) }
        args += listOf(scriptOption, script.absolutePath)
        args += when (out) {
            PreviewOut.VIDEO -> listOf("-map", "[pv]", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1")
            PreviewOut.STILL -> listOf("-map", "[pv]", "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1")
            PreviewOut.AUDIO -> listOf("-map", "[pa]", "-ac", "2", "-f", "s16le", "pipe:1")
        }
        return args
    }

    /**
     * One picture of [file] at [sec] for the preview of [p], straight from the file (quicker than the graph while
     * scrubbing) but through the same filters as a full clip of V1 in the graph: preview size, bars, RGBA.
     */
    fun previewStillArgs(p: VideoProject, file: File, sec: Double, picture: Boolean, source: (File) -> File = { Media.proxy(it) ?: it }): List<String> {
        val (w, h) = previewSize(p)
        val input = if (picture) listOf("-i", file.absolutePath) else listOf("-ss", f(sec.coerceAtLeast(0.0)), "-i", source(file).absolutePath)
        return listOf("-nostdin", "-hide_banner", "-loglevel", "error") + input +
            listOf("-frames:v", "1", "-vf", "${fitInto(w, h)},setsar=1,format=yuv420p,format=rgba", "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1")
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
    suspend fun render(
        p: VideoProject, preset: Preset, out: File, encoder: Encoders.Encoder = Encoders.SOFTWARE, audioOnly: Boolean = false, progress: (Float) -> Unit,
    ): File {
        // A hardware encoder that fails (driver, a card busy with something else) falls back to x264 (AP-57).
        if (encoder.hardware && !audioOnly) {
            val first = runCatching { renderWith(p, preset, out, encoder, false, progress) }
            first.exceptionOrNull()?.let { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.i("Export mit ${encoder.id} fehlgeschlagen, weiter mit x264: ${e.message?.take(300)}")
                lastFallback = encoder.id
                return renderWith(p, preset, out, Encoders.SOFTWARE, false, progress)
            }
            return first.getOrThrow()
        }
        return renderWith(p, preset, out, if (audioOnly) Encoders.SOFTWARE else encoder, audioOnly, progress)
    }

    /** The hardware encoder that last failed and was replaced by x264 (for the queue's note), null if none. */
    @Volatile
    var lastFallback: String? = null

    private suspend fun renderWith(p: VideoProject, preset: Preset, out: File, encoder: Encoders.Encoder, audioOnly: Boolean, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val exe = Media.ffmpeg() ?: throw IllegalStateException(tr("ffmpeg fehlt in der App."))
        val work = File(Media.cacheDir, "export-${System.nanoTime()}")
        val tmp = File(out.parentFile, out.nameWithoutExtension + ".teil." + out.extension.ifBlank { "mp4" })
        try {
            val plan = plan(p, preset, tmp, work, { Media.probe(it) }, scriptOption = scriptOption(exe), encoder = encoder, audioOnly = audioOnly)
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
