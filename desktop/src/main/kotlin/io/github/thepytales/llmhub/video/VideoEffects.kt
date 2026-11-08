package io.github.thepytales.llmhub.video

import io.github.thepytales.llmhub.ui.tr
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A keyframe of a rectangle (AP-54) at time [t] of the clip's SOURCE file: centre [x], [y] and size [w], [h] as shares
 * of the source picture. Source time (not timeline time) so that trimming, splitting, slipping or a change of speed
 * keeps a region on the face it follows – and face detections are in source time anyway. For zoom, [w] is the share of
 * the picture that stays visible (0.5 = twice as large) and [h] is not used.
 */
@Serializable
data class Key(val t: Double, val x: Float = 0.5f, val y: Float = 0.5f, val w: Float = 1f, val h: Float = 1f)

/**
 * A region of the picture made unrecognisable (AP-54): [mode] "pixel" (coarse blocks) or "blur", moving with its [keys]
 * (interpolated between them). [hold]: before the first and after the last key the region stays where it is (drawn by
 * hand); a found face ([face]) only exists while it was seen, plus a short margin.
 */
@Serializable
data class Region(
    val id: String = newVideoId(),
    val keys: List<Key>,
    val mode: String = "pixel",
    val face: Boolean = false,
    val hold: Boolean = true,
)

/**
 * Simple effects of a clip on V1 (AP-54) – everything goes through the one ffmpeg graph of export and preview:
 * [speed] 0.25×–4× (sound with pitch kept), [reverse], [freeze] (the clip holds the frame at its In for its length),
 * brightness/contrast/saturation (ffmpeg eq), white balance as gains per colour channel (automatic from a grey-world
 * estimate), [zoom] keyframes (Ken Burns, zoom onto a part of a screencast), [vertical] 9:16 reframe that follows the
 * same keys, [regions] pixelated or blurred, and [voice] (pitch shift in semitones, to make a voice unrecognisable).
 */
@Serializable
data class ClipFx(
    val speed: Double = 1.0,
    val reverse: Boolean = false,
    val freeze: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val gainR: Float = 1f,
    val gainG: Float = 1f,
    val gainB: Float = 1f,
    val zoom: List<Key> = emptyList(),
    val ease: Boolean = true,
    val vertical: Boolean = false,
    val regions: List<Region> = emptyList(),
    val voice: Float = 0f,
) {
    val isNone: Boolean get() = this == ClipFx()
    val corrected: Boolean get() = abs(brightness) > 1e-3 || abs(contrast - 1f) > 1e-3 || abs(saturation - 1f) > 1e-3
    val balanced: Boolean get() = abs(gainR - 1f) > 1e-3 || abs(gainG - 1f) > 1e-3 || abs(gainB - 1f) > 1e-3

    /** Whether the picture needs the effect chain of the graph (anything but the sound). */
    val picture: Boolean get() = abs(speed - 1.0) > 1e-6 || reverse || freeze || corrected || balanced || zoom.isNotEmpty() || vertical || regions.isNotEmpty()

    /** The picture effects that a frozen frame inserted from this clip keeps (anonymisation must not get lost). */
    fun forFreeze(): ClipFx = ClipFx(freeze = true, brightness = brightness, contrast = contrast, saturation = saturation, gainR = gainR, gainG = gainG, gainB = gainB, zoom = zoom, ease = ease, vertical = vertical, regions = regions)
}

/**
 * The effects of AP-54 as pure functions: keyframe interpolation (for the editor and as ffmpeg expressions), the
 * parts of the filter graph, the grey-world white balance and the edits (speed, freeze, reverse, zoom, regions, voice).
 * [VideoRender.graph] calls [pictureLines] and [soundFilters]; nothing here runs ffmpeg.
 */
object VideoFx {
    /** Speeds offered in the editor. */
    val SPEEDS = listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0)

    /**
     * Longest reversed clip on the timeline: ffmpeg's reverse filter keeps every frame of the clip in memory (at 1080p
     * about 3 MB each), so 5 s at 25 frames/s stay below 400 MB.
     */
    const val REVERSE_MAX = 5.0

    private fun f(v: Double) = String.format(Locale.ROOT, "%.3f", v)
    private fun f4(v: Double) = String.format(Locale.ROOT, "%.4f", v)
    private fun even(v: Int) = maxOf(2, (v / 2) * 2)

    // ── Keyframes ──

    /** Smoothstep: slow at both ends (ease in and out). */
    fun ease(u: Double): Double = u * u * (3 - 2 * u)

    /** The value of [points] (time → value, sorted by time) at [t]: held before the first and after the last. */
    fun interpolate(points: List<Pair<Double, Double>>, t: Double, eased: Boolean): Double {
        if (points.isEmpty()) return 0.0
        if (t <= points.first().first) return points.first().second
        if (t >= points.last().first) return points.last().second
        val k = points.indexOfLast { it.first <= t }
        val (t0, v0) = points[k]
        val (t1, v1) = points[k + 1]
        val u = if (t1 - t0 < 1e-9) 1.0 else (t - t0) / (t1 - t0)
        return v0 + (v1 - v0) * (if (eased) ease(u) else u)
    }

    /** The rectangle of [keys] at source time [t] (null without keys). */
    fun keyAt(keys: List<Key>, t: Double, eased: Boolean): Key? {
        if (keys.isEmpty()) return null
        val s = keys.sortedBy { it.t }
        fun v(g: (Key) -> Float) = interpolate(s.map { it.t to g(it).toDouble() }, t, eased).toFloat()
        return Key(t, v { it.x }, v { it.y }, v { it.w }, v { it.h })
    }

    /**
     * [points] as an ffmpeg expression in variable [v] (piecewise linear or eased, held at both ends). A balanced tree
     * of if(lt(…)) keeps the nesting at log2 of the number of keys – a face followed over minutes has hundreds.
     */
    fun expr(points: List<Pair<Double, Double>>, v: String, eased: Boolean): String {
        val p = points.sortedBy { it.first }
        if (p.isEmpty()) return "0"
        if (p.size == 1 || p.all { abs(it.second - p[0].second) < 1e-6 }) return f(p[0].second)
        fun seg(i: Int): String {
            val (t0, v0) = p[i]
            val (t1, v1) = p[i + 1]
            if (abs(v1 - v0) < 1e-6 || t1 - t0 < 1e-6) return f(v1)
            val u = "($v-${f4(t0)})/${f4(t1 - t0)}"
            val shaped = if (eased) "(${u})*(${u})*(3-2*(${u}))" else u
            return "${f(v0)}+${f(v1 - v0)}*$shaped"
        }
        // Segments i = 0 … n-2 between p[i] and p[i+1]; before the first and after the last the value is held.
        fun tree(a: Int, b: Int): String = if (a == b) seg(a) else {
            val m = (a + b + 1) / 2
            "if(lt($v,${f4(p[m].first)}),${tree(a, m - 1)},${tree(m, b)})"
        }
        return "if(lt($v,${f4(p.first().first)}),${f(p.first().second)},if(gte($v,${f4(p.last().first)}),${f(p.last().second)},${tree(0, p.size - 2)}))"
    }

    // ── Sound ──

    /** atempo in steps ffmpeg accepts everywhere (0.5 … 2 each): 4 → 2,2; 0.25 → 0.5,0.5. */
    fun atempo(factor: Double): List<String> {
        var r = factor.coerceIn(0.0625, 16.0)
        if (abs(r - 1.0) < 1e-4) return emptyList()
        val out = mutableListOf<String>()
        while (r >= 2.0 - 1e-6) { out += "atempo=2.0"; r /= 2.0 }
        while (r <= 0.5 + 1e-6) { out += "atempo=0.5"; r /= 0.5 }
        if (abs(r - 1.0) > 1e-4) out += "atempo=${f4(r)}"
        return out
    }

    /**
     * Sound of a clip at [speed] (pitch kept: atempo), [reverse] (areverse) and [voice] semitones higher or lower: the
     * sample rate is changed (pitch and tempo together) and atempo brings the tempo back – works with every ffmpeg
     * build, unlike rubberband. Empty for none of them.
     */
    fun soundFilters(speed: Double, reverse: Boolean, voice: Float, rate: Int = 48000): List<String> {
        val out = mutableListOf<String>()
        if (reverse) out += "areverse"
        val shift = if (abs(voice) > 0.01f) 2.0.pow(voice.coerceIn(-12f, 12f) / 12.0) else 1.0
        if (shift != 1.0) out += listOf("asetrate=${(rate * shift).roundToInt()}", "aresample=$rate")
        out += atempo(speed.coerceIn(0.25, 4.0) / shift)
        return out
    }

    // ── Picture ──

    /** Size of the working picture: the source fitted into [w]×[h] (the size it has in the film). */
    fun workSize(info: MediaInfo?, w: Int, h: Int): Pair<Int, Int> {
        val sw = info?.width ?: 0
        val sh = info?.height ?: 0
        if (sw <= 0 || sh <= 0) return w to h
        val a = sw.toDouble() / sh
        return if (a >= w.toDouble() / h) even(w) to even((w / a).roundToInt()) else even((h * a).roundToInt()) to even(h)
    }

    /**
     * Where a piece of a clip comes from: its first frame shows source time [src0], each second of the timeline moves
     * [rate] seconds through the source (negative when reversed, 0 when frozen). Converts source times of keyframes
     * into the piece's own time `t` (0 at its first frame).
     */
    data class Span(val src0: Double, val rate: Double, val length: Double) {
        fun local(src: Double): Double = if (abs(rate) < 1e-9) 0.0 else (src - src0) / rate
    }

    /** The piece of clip [c] from [head] seconds into it for [length] seconds of the timeline. */
    fun span(c: VideoClip, head: Double, length: Double, still: Boolean): Span = when {
        // A picture stands still anyway; keys of its zoom (Ken Burns on a photo) count from the clip's start.
        c.fx.freeze -> Span(c.inSec, 0.0, length)
        still -> Span(c.inSec + head, 1.0, length)
        c.fx.reverse -> Span(c.outSec - head * c.speed, -c.speed, length)
        else -> Span(c.inSec + head * c.speed, c.speed, length)
    }

    /** Input options of the piece (seek and length in the source) – speed reads more, a frozen frame only a little. */
    fun inputRange(c: VideoClip, head: Double, length: Double): Pair<Double, Double> = when {
        c.fx.freeze -> c.inSec to 0.5
        c.fx.reverse -> maxOf(0.0, c.outSec - (head + length) * c.speed) to length * c.speed
        else -> c.inSec + head * c.speed to length * c.speed
    }

    /** Keys as points in the piece's time: [g] picks the value. Frozen: the one value at the frame. */
    private fun points(keys: List<Key>, span: Span, eased: Boolean, g: (Key) -> Double): List<Pair<Double, Double>> {
        if (abs(span.rate) < 1e-9) return listOf(0.0 to (keyAt(keys, span.src0, eased)?.let(g) ?: g(keys.first())))
        return keys.map { span.local(it.t) to g(it) }.sortedBy { it.first }
    }

    /**
     * The lines of the graph that apply [fx] to the picture of a piece: from [input] (e.g. `[3:v]`) to `[out]`, at
     * the working size [bw]×[bh] and [fps]. Order: time (freeze, speed, reverse), regions (anonymisation first, in
     * source coordinates), zoom/reframe, then colour. Times in expressions are the piece's own (`t`, or `in/fps` in
     * perspective, which has no `t`).
     */
    fun pictureLines(input: String, out: String, fx: ClipFx, span: Span, bw: Int, bh: Int, fps: Int, still: Boolean): List<String> {
        val lines = mutableListOf<String>()
        val pre = StringBuilder(input)
        if (fx.freeze) pre.append("trim=end_frame=1,setpts=PTS-STARTPTS,fps=$fps,tpad=stop_mode=clone:stop_duration=${f(span.length + 0.2)},")
        else if (!still && abs(span.rate) > 1e-9 && abs(abs(span.rate) - 1.0) > 1e-6) pre.append("setpts=(PTS-STARTPTS)/${f4(abs(span.rate))},fps=$fps,")
        else pre.append("setpts=PTS-STARTPTS,fps=$fps,")
        pre.append("scale=$bw:$bh,setsar=1")
        if (fx.reverse && !fx.freeze && !still) pre.append(",reverse")
        pre.append(",setpts=PTS-STARTPTS")
        var cur = "${out}p"
        lines += "$pre[$cur]"
        // Regions: a copy of the rectangle, coarse or blurred, laid back at the same place (both follow the keys).
        fx.regions.filter { it.keys.isNotEmpty() }.forEachIndexed { j, r ->
            val rw = even((r.keys.maxOf { it.w }.coerceIn(0.02f, 1f) * bw).roundToInt()).coerceIn(16, bw)
            val rh = even((r.keys.maxOf { it.h }.coerceIn(0.02f, 1f) * bh).roundToInt()).coerceIn(16, bh)
            val cx = expr(points(r.keys, span, false) { it.x * bw.toDouble() }, "t", false)
            val cy = expr(points(r.keys, span, false) { it.y * bh.toDouble() }, "t", false)
            val x = "clip($cx-${rw / 2},0,${bw - rw})"
            val y = "clip($cy-${rh / 2},0,${bh - rh})"
            val effect = if (r.mode == "blur") {
                val rad = maxOf(2, minOf(rw, rh) / 5).coerceAtMost(minOf(rw, rh) / 2 - 1)
                val crad = maxOf(1, minOf(rad, minOf(rw, rh) / 4 - 1))
                "boxblur=luma_radius=$rad:luma_power=3:chroma_radius=$crad:chroma_power=3"
            } else {
                // Pixelate by scaling down and up again (block size known here, so no dependency on ffmpeg 6's pixelize).
                val blocks = 8
                "scale=${maxOf(1, rw / maxOf(2, rw / blocks))}:${maxOf(1, rh / maxOf(2, rw / blocks))}:flags=area,scale=$rw:$rh:flags=neighbor"
            }
            val enable = if (r.hold || r.keys.size < 2 || abs(span.rate) < 1e-9) "" else {
                val ts = r.keys.map { span.local(it.t) }
                ":enable='between(t,${f(ts.min() - 0.3)},${f(ts.max() + 0.3)})'"
            }
            val a = "${out}r${j}a"; val b = "${out}r${j}b"; val m = "${out}r${j}m"; val next = "${out}r$j"
            lines += "[$cur]split=2[$a][$b]"
            lines += "[$b]crop=$rw:$rh:'$x':'$y',$effect[$m]"
            lines += "[$a][$m]overlay=x='$x':y='$y'$enable[$next]"
            cur = next
        }
        // Zoom and pan (and the vertical reframe): the rectangle of the keys stretched over the picture.
        val chain = mutableListOf<String>()
        if (fx.zoom.isNotEmpty() || fx.vertical) {
            val keys = fx.zoom.ifEmpty { listOf(Key(0.0)) }
            val vw = even((bh * 9.0 / 16).roundToInt()).coerceAtMost(bw)
            fun rect(k: Key): DoubleArray {
                val z = k.w.coerceIn(0.05f, 1f).toDouble()
                var rh = z * bh
                var rw = if (fx.vertical) rh * 9 / 16 else z * bw
                if (rw > bw) { rh *= bw / rw; rw = bw.toDouble() }
                val cx = (k.x * bw.toDouble()).coerceIn(rw / 2, bw - rw / 2)
                val cy = (k.y * bh.toDouble()).coerceIn(rh / 2, bh - rh / 2)
                return doubleArrayOf(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
            }
            val pts = (0 until 4).map { i -> points(keys, span, fx.ease) { rect(it)[i] } }
            val static = pts.all { p -> p.all { abs(it.second - p[0].second) < 0.5 } }
            if (static) {
                val r = pts.map { it[0].second }
                val cw = even((r[2] - r[0]).roundToInt()); val ch = even((r[3] - r[1]).roundToInt())
                if (cw < bw - 1 || ch < bh - 1) chain += "crop=$cw:$ch:${r[0].roundToInt()}:${r[1].roundToInt()}"
            } else {
                val e = pts.map { expr(it, "(in/$fps)", fx.ease) }
                chain += "perspective=x0='${e[0]}':y0='${e[1]}':x1='${e[2]}':y1='${e[1]}':x2='${e[0]}':y2='${e[3]}':x3='${e[2]}':y3='${e[3]}':eval=frame"
            }
            chain += if (fx.vertical) "scale=$vw:$bh,setsar=1" else "scale=$bw:$bh,setsar=1"
        }
        if (fx.corrected) chain += "eq=brightness=${f(fx.brightness.coerceIn(-0.6f, 0.6f).toDouble())}:contrast=${f(fx.contrast.coerceIn(0.3f, 2.5f).toDouble())}:saturation=${f(fx.saturation.coerceIn(0f, 2.5f).toDouble())}"
        if (fx.balanced) chain += "colorchannelmixer=rr=${f(fx.gainR.coerceIn(0.5f, 2f).toDouble())}:gg=${f(fx.gainG.coerceIn(0.5f, 2f).toDouble())}:bb=${f(fx.gainB.coerceIn(0.5f, 2f).toDouble())}"
        lines += "[$cur]" + (chain.ifEmpty { listOf("null") }).joinToString(",") + "[$out]"
        return lines
    }

    // ── White balance ──

    /**
     * Grey world: the mean of all mid-tone pixels (ARGB) should be grey; the gains per channel that make it so,
     * normalised to keep the brightness and limited to 0.5…2. Very dark and burnt-out pixels do not count.
     */
    fun grayWorld(argb: IntArray): Triple<Float, Float, Float> {
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0
        for (c in argb) {
            val cr = c shr 16 and 0xff; val cg = c shr 8 and 0xff; val cb = c and 0xff
            val mx = maxOf(cr, cg, cb); val mn = minOf(cr, cg, cb)
            if (mx > 245 || mn < 12) continue
            r += cr; g += cg; b += cb; n++
        }
        if (n < 16 || r <= 0 || g <= 0 || b <= 0) return Triple(1f, 1f, 1f)
        val grey = (r + g + b) / 3
        fun gain(v: Double) = (grey / v).coerceIn(0.5, 2.0).toFloat()
        return Triple(gain(r), gain(g), gain(b))
    }

    // ── Edits (pure, like VideoEdit) ──

    private fun fxOf(p: VideoProject, id: String) = p.clips.firstOrNull { it.id == id }?.fx

    /** Changes the effects of V1 clip [id]; a change of its length (speed) closes up or moves the rest like a trim. */
    fun change(p: VideoProject, id: String, f: (ClipFx) -> ClipFx): VideoProject {
        val i = p.clips.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return p
        if (p.track("V1").locked) return p
        val c = p.clips[i]
        val next = c.copy(fx = f(c.fx))
        if (next == c) return p
        val change = next.length - c.length
        val oldEnd = if (i + 1 < p.clips.size) p.starts()[i + 1] else p.starts()[i] + c.length
        val q = p.copy(clips = p.clips.toMutableList().apply { set(i, next) })
        return if (p.magnet && abs(change) > 1e-9) VideoEdit.rippleAfter(q, oldEnd, change) else q.linked()
    }

    /** Speed of clip [id] (0.25…4). */
    fun speed(p: VideoProject, id: String, speed: Double): VideoProject = change(p, id) { it.copy(speed = speed.coerceIn(0.25, 4.0)) }

    /** Why clip [c] cannot run backwards, or null. */
    fun reverseProblem(c: VideoClip): String? = when {
        c.fx.freeze -> tr("Ein Standbild läuft nicht rückwärts.")
        c.sourceLength / c.fx.speed.coerceIn(0.25, 4.0) > REVERSE_MAX + 1e-6 ->
            tr("Rückwärts geht nur für kurze Clips bis {0} s (dieser ist {1} s lang) – bitte vorher mit der Klinge kürzen.", REVERSE_MAX.toInt(), String.format(Locale.GERMANY, "%.1f", c.length))
        else -> null
    }

    /** Reverses clip [id] (or back); null with the reason when it is too long. */
    fun reverse(p: VideoProject, id: String, on: Boolean = true): Pair<VideoProject?, String?> {
        val c = p.clips.firstOrNull { it.id == id } ?: return null to null
        if (on) reverseProblem(c)?.let { return null to it }
        return change(p, id) { it.copy(reverse = on) } to null
    }

    /**
     * A frozen frame of [seconds] at timeline time [at]: the clip there is split and the frame (with the clip's picture
     * effects – its regions stay pixelated) stands in between; everything after it moves on (magnet).
     */
    fun freeze(p: VideoProject, at: Double, seconds: Double): Pair<VideoProject, String>? {
        val (i, src) = p.at(at) ?: return null
        val c = p.clips[i]
        val len = seconds.coerceIn(0.2, 60.0)
        val frame = VideoClip(path = c.path, inSec = src, outSec = src + len, volume = c.volume, muted = true, frame = c.frame, fx = c.fx.forFreeze())
        return VideoEdit.insert(p, frame, at) to frame.id
    }

    /** Sets or replaces the zoom keyframe of clip [id] at its source time [src] (two keys within 0.1 s are one). */
    fun setZoomKey(p: VideoProject, id: String, key: Key): VideoProject = change(p, id) { fx ->
        fx.copy(zoom = (fx.zoom.filter { abs(it.t - key.t) > 0.1 } + key).sortedBy { it.t })
    }

    /**
     * Zoom of clip [id] onto the rectangle [target] (shares of the picture): from the whole picture at the clip's start
     * in [ramp] seconds to the target, held, and back out [ramp] seconds before its end (Ken Burns of a screencast).
     */
    fun zoomTo(p: VideoProject, id: String, target: Key, ramp: Double = 1.0): VideoProject {
        val c = p.clips.firstOrNull { it.id == id } ?: return p
        val a = c.inSec
        val b = c.outSec
        val r = minOf(ramp * c.speed, (b - a) / 3)
        val full = Key(0.0, 0.5f, 0.5f, 1f, 1f)
        val keys = listOf(full.copy(t = a), target.copy(t = a + r), target.copy(t = b - r), full.copy(t = b))
        return change(p, id) { it.copy(zoom = keys) }
    }

    /** Adds a region to clip [id]: a hand-drawn rectangle at source time [src] (it stays where it is). */
    fun addRegion(p: VideoProject, id: String, key: Key, mode: String = "pixel"): Pair<VideoProject, String> {
        val r = Region(keys = listOf(key), mode = mode)
        return change(p, id) { it.copy(regions = it.regions + r) } to r.id
    }

    /** Sets [semitones] of voice change on the clips [ids] of V1 (all when null) and on every sound of A1 …. */
    fun voice(p: VideoProject, semitones: Float, ids: Set<String>? = null): VideoProject {
        val v = semitones.coerceIn(-12f, 12f)
        return p.copy(
            clips = p.clips.map { if (ids == null || it.id in ids) it.copy(fx = it.fx.copy(voice = v)) else it },
            audio = p.audio.map { a -> if (ids == null || a.linkedTo != null && a.linkedTo in ids) a.copy(voice = v) else a },
        )
    }

    /**
     * Splits [c] at [local] seconds of the timeline into it (the blade): forward, reversed and frozen clips split their
     * source accordingly; effects, regions and keys go with both parts (keys are in source time).
     */
    fun splitClip(c: VideoClip, local: Double): Pair<VideoClip, VideoClip> {
        val right = c.copy(id = newVideoId(), transition = "none")
        return when {
            c.fx.freeze -> c.copy(outSec = c.inSec + local) to right.copy(outSec = c.inSec + (c.length - local))
            c.fx.reverse -> { val s = c.outSec - local * c.speed; c.copy(inSec = s) to right.copy(outSec = s) }
            else -> { val s = c.inSec + local * c.speed; c.copy(outSec = s) to right.copy(inSec = s) }
        }
    }

    /** A short line of what [fx] does, for the timeline and Lora („2× · rückwärts · 3 Bereiche“). */
    fun summary(fx: ClipFx): String = listOfNotNull(
        fx.speed.takeIf { abs(it - 1.0) > 1e-6 }?.let { String.format(Locale.GERMANY, "%s×", if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.GERMANY, "%.2f", it).trimEnd('0')) },
        tr("rückwärts").takeIf { fx.reverse },
        tr("Standbild").takeIf { fx.freeze },
        tr("Zoom").takeIf { fx.zoom.isNotEmpty() && !fx.vertical },
        tr("Hochformat").takeIf { fx.vertical },
        tr("{0} Bereich(e)", fx.regions.size).takeIf { fx.regions.isNotEmpty() },
        tr("Bild korrigiert").takeIf { fx.corrected || fx.balanced },
        tr("Stimme verfremdet").takeIf { abs(fx.voice) > 0.01f },
    ).joinToString(" · ")
}
