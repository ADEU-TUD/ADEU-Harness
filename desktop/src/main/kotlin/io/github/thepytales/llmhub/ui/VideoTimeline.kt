package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.video.AudioSync
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.PoolItem
import io.github.thepytales.llmhub.video.TextOverlay
import io.github.thepytales.llmhub.video.VideoEdit
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Tools of the timeline (AP-53): A selection, B blade, T trim (roll at a cut, slip in the middle). */
internal enum class CutTool(val key: String, val label: String) { SELECT("A", "Auswahl"), BLADE("B", "Klinge"), TRIM("T", "Trimmen") }

/**
 * A file dragged from the media pool (AP-53): the timeline registers where it is and what a drop there does.
 */
internal class PoolDrag {
    var item by mutableStateOf<PoolItem?>(null)
    var at by mutableStateOf(Offset.Zero)
    var target: Rect? = null
    var onDrop: ((PoolItem, Offset) -> Unit)? = null

    fun end() {
        val i = item
        val t = target
        if (i != null && t != null && t.contains(at)) onDrop?.invoke(i, at - t.topLeft)
        item = null
    }
}

/** One lane of the timeline: [key] "T" titles, "V3" … "V1", "A1" …, "S" subtitles; top and height in pixels. */
internal data class LaneRow(val key: String, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
    val video: Boolean get() = key.startsWith("V")
    val audio: Boolean get() = key.startsWith("A")
    val number: Int get() = key.drop(1).toIntOrNull() ?: 0
}

/** The lanes of [p] for unit [u] (pixels per dp): titles, V(n) … V1, A1 … A(n), subtitles. */
internal fun lanesOf(p: VideoProject, u: Float): Pair<List<LaneRow>, Float> {
    val gap = 3f * u
    var y = 22f * u
    val out = mutableListOf<LaneRow>()
    fun add(key: String, hh: Float) { out += LaneRow(key, y, hh * u); y += hh * u + gap }
    add("T", 26f)
    for (n in p.videoTrackCount downTo 2) add("V$n", 40f)
    add("V1", 56f)
    for (n in 1..p.audioTrackCount) add("A$n", 40f)
    add("S", 20f)
    return out to y
}

private val V1A = Color(0xFF2F57B2)
private val V1B = Color(0xFF00008C)
private val LAYER = Color(0xFF7A4FC9)
private val SOUND = Color(0xFF65B32E)
private val LINKED = Color(0xFF3E8F1F)
private val SELECTED = Color(0xFFEE7F00)
private val MARKER = Color(0xFFE2001A)

/**
 * The timeline (AP-25, AP-52, AP-53): ruler with markers and In/Out, titles, the video tracks (V1 below, pictures
 * above), the audio tracks with waveforms and the subtitles; on the left the track heads with lock, mute and hide.
 * The tools: selection (move, ripple trim at the edges), blade, trim (roll at a cut, slip in the middle); the magnet
 * snaps to edges and lets the other tracks follow edits on V1.
 */
@Composable
internal fun VideoTimelineView(
    h: History, player: VideoPlayer, pos: Double, sel: Sel, tool: CutTool, drag: PoolDrag,
    onSelect: (Sel) -> Unit, onDropItem: (PoolItem, String, Double) -> Unit, modifier: Modifier = Modifier,
) {
    val p = h.project
    val measurer = rememberTextMeasurer()
    var zoom by remember { mutableStateOf(1f) }
    var scroll by remember { mutableStateOf(0f) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val brand = LocalChatLook.current.brand
    val density = LocalDensity.current
    val u = density.density
    // Waveforms load in the background; each one ready draws the timeline again.
    var waves by remember { mutableIntStateOf(0) }
    val paths = remember(p.clips.map { it.path }, p.audio.map { it.path }, p.layers.map { it.path }) {
        (p.clips.filter { !it.muted }.map { it.path } + p.audio.map { it.path }).distinct()
    }
    LaunchedEffect(paths) {
        for (path in paths) {
            val f = File(path)
            if (AudioSync.cached(f) == null && f.isFile && !Media.isStill(f)) {
                withContext(Dispatchers.IO) { runCatching { AudioSync.envelope(f) } }
                waves++
            }
        }
    }
    val (lanes, totalHeight) = lanesOf(p, u)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(tr("Zoom"), style = MaterialTheme.typography.labelSmall)
            Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..24f, modifier = Modifier.width(180.dp))
            Spacer(Modifier.size(12.dp))
            HintText(
                when (tool) {
                    CutTool.SELECT -> tr("Auswahl (A): Clips ziehen verschiebt, Ränder ziehen kürzt (Ripple) · Klick in die Leiste springt · ? zeigt alle Tasten.")
                    CutTool.BLADE -> tr("Klinge (B): Klick auf einen Clip schneidet ihn dort.")
                    CutTool.TRIM -> tr("Trimmen (T): am Schnitt ziehen verschiebt ihn (Roll), in der Mitte ziehen verschiebt den Inhalt (Slip).")
                },
                Modifier.weight(1f),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(with(density) { totalHeight.toDp() } + 4.dp).background(Color(0x0F000000))) {
            val width = constraints.maxWidth.toFloat()
            val left = 118f * u
            val total = maxOf(p.duration, 1.0)
            val pxPerSec = ((width - left - 20f) / total * zoom).toFloat()
            val maxScroll = maxOf(0f, (total * pxPerSec).toFloat() - (width - left - 20f))
            val sc = scroll.coerceIn(0f, maxScroll)
            fun x(t: Double) = left + (t * pxPerSec).toFloat() - sc
            fun t(x: Float) = ((x - left + sc) / pxPerSec).toDouble()
            // The gesture handler lives longer than one composition (dragging changes the project): it reads these.
            val now = remember { arrayOfNulls<Any>(5) }
            now[0] = pxPerSec; now[1] = sc; now[2] = maxScroll; now[3] = tool; now[4] = lanes
            // Drop target of the media pool.
            Box(Modifier.matchParentSize().onGloballyPositioned { drag.target = it.boundsInWindow() })
            drag.onDrop = { item, at ->
                @Suppress("UNCHECKED_CAST")
                val ls = now[4] as List<LaneRow>
                val lane = ls.firstOrNull { at.y in it.top..it.bottom }
                val time = (((at.x - left + (now[1] as Float)) / (now[0] as Float)).toDouble()).coerceAtLeast(0.0)
                if (lane != null && (lane.video || lane.audio) && at.x >= left) onDropItem(item, lane.key, time)
            }
            Canvas(
                Modifier.matchParentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val base = h.project
                            val starts = base.starts()
                            val pxPerSec = now[0] as Float
                            val pps = pxPerSec.toDouble()
                            val sc = now[1] as Float
                            val tool = now[3] as CutTool
                            @Suppress("UNCHECKED_CAST")
                            val ls = now[4] as List<LaneRow>
                            fun x(t: Double) = left + (t * pxPerSec).toFloat() - sc
                            fun t(x: Float) = ((x - left + sc) / pxPerSec).toDouble()
                            val at = down.position
                            val time = t(at.x).coerceAtLeast(0.0)
                            val edge = 7f * u
                            val tolerance = 8f * u / pxPerSec
                            val snaps by lazy { VideoEdit.snapPoints(base) + player.position.value }
                            fun snapped(t: Double, except: String? = null) = if (base.magnet) VideoEdit.snap(t, if (except == null) snaps else VideoEdit.snapPoints(base, except) + player.position.value, tolerance.toDouble()) else t
                            var marked = false
                            fun apply(f: (VideoProject) -> VideoProject) {
                                val next = f(base).linked()
                                if (next != h.project) {
                                    if (!marked) { h.mark(); marked = true }
                                    h.edit(record = false) { next }
                                }
                            }
                            fun laneAt(y: Float) = ls.firstOrNull { y >= it.top - 1.5f * u && y <= it.bottom + 1.5f * u }
                            val lane = laneAt(at.y)
                            var action: ((Float, Float) -> Unit)? = null
                            when {
                                at.x < left -> {}
                                at.y < 22f * u -> {
                                    // Ruler: a marker is picked up, otherwise the playhead jumps (and follows).
                                    val m = base.markers.minByOrNull { abs(x(it.sec) - at.x) }?.takeIf { abs(x(it.sec) - at.x) < 6f * u }
                                    if (m != null) {
                                        onSelect(Sel.Mark(m.id))
                                        action = { dx, _ -> apply { pp -> pp.copy(markers = pp.markers.map { if (it.id == m.id) it.copy(sec = (m.sec + dx / pps).coerceAtLeast(0.0)) else it }.sortedBy { it.sec }) } }
                                    } else {
                                        player.seek(h.project, time)
                                        action = { dx, _ -> player.seek(h.project, t(at.x + dx)) }
                                    }
                                }
                                lane == null -> { onSelect(Sel.None); player.seek(h.project, time) }
                                lane.key == "T" -> {
                                    val o = base.overlays.lastOrNull { time >= it.startSec && time <= it.endSec }
                                    if (o != null) {
                                        onSelect(Sel.Title(o.id))
                                        val atEnd = abs(at.x - x(o.endSec)) < edge
                                        action = { dx, _ ->
                                            apply { pp -> pp.copy(overlays = pp.overlays.map {
                                                if (it.id != o.id) it else if (atEnd) it.copy(durationSec = (snapped(o.endSec + dx / pps) - o.startSec).coerceAtLeast(0.5)) else it.copy(startSec = snapped(o.startSec + dx / pps).coerceAtLeast(0.0))
                                            }) }
                                        }
                                    } else onSelect(Sel.None)
                                }
                                lane.key == "V1" -> {
                                    val i = starts.indices.lastOrNull { k -> time >= starts[k] && time <= starts[k] + base.clips[k].length } ?: -1
                                    if (i < 0) { onSelect(Sel.None); player.seek(h.project, time) }
                                    else {
                                        val c = base.clips[i]
                                        val srcLen = Media.probe(File(c.path))?.duration?.takeIf { it > 0.05 && !Media.isStill(File(c.path)) }
                                        val x0 = x(starts[i]); val x1 = x(starts[i] + c.length)
                                        val nextCut = if (i + 1 < base.clips.size) x(starts[i + 1]) else null
                                        when (tool) {
                                            CutTool.BLADE -> VideoEdit.split(base, VideoEdit.Clip(c.id), snapped(time))?.let { (q, id) -> h.edit { q }; onSelect(Sel.Clip(id)) }
                                            CutTool.TRIM -> {
                                                onSelect(Sel.Clip(c.id))
                                                val rollAt = when {
                                                    nextCut != null && abs(at.x - nextCut) < edge -> c.id
                                                    i > 0 && abs(at.x - x0) < edge -> base.clips[i - 1].id
                                                    else -> null
                                                }
                                                action = if (rollAt != null) { dx, _ ->
                                                    val k = base.clips.indexOfFirst { it.id == rollAt }
                                                    val len = Media.probe(File(base.clips[k].path))?.duration?.takeIf { it > 0.05 }
                                                    apply { VideoEdit.roll(it, rollAt, dx / pps, len) }
                                                } else { dx, _ -> apply { VideoEdit.slip(it, VideoEdit.Clip(c.id), -dx / pps, srcLen) } }
                                            }
                                            CutTool.SELECT -> {
                                                onSelect(Sel.Clip(c.id))
                                                action = when {
                                                    abs(at.x - x0) < edge -> { dx, _ -> apply { VideoEdit.rippleTrim(it, c.id, true, dx / pps) } }
                                                    abs(at.x - x1) < edge -> { dx, _ -> apply { VideoEdit.rippleTrim(it, c.id, false, dx / pps, srcLen) } }
                                                    base.track("V1").locked -> null
                                                    else -> { dx, _ ->
                                                        // Reorder: the clip goes where its middle is dropped.
                                                        val mid = starts[i] + c.length / 2 + dx / pps
                                                        val target = starts.indices.count { k -> k != i && starts[k] + base.clips[k].length / 2 < mid }
                                                        apply { pp -> pp.copy(clips = pp.clips.toMutableList().apply { removeAt(indexOfFirst { it.id == c.id }); add(target.coerceIn(0, size), c) }) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                lane.video -> {
                                    val l = base.layers.lastOrNull { it.track == lane.number && time >= it.startSec && time <= it.endSec }
                                    if (l == null) { onSelect(Sel.None); player.seek(h.project, time) }
                                    else {
                                        val srcLen = Media.probe(File(l.path))?.duration?.takeIf { it > 0.05 && !Media.isStill(File(l.path)) }
                                        when (tool) {
                                            CutTool.BLADE -> VideoEdit.split(base, VideoEdit.Layer(l.id), snapped(time))?.let { (q, id) -> h.edit { q }; onSelect(Sel.Layer(id)) }
                                            CutTool.TRIM -> { onSelect(Sel.Layer(l.id)); action = { dx, _ -> apply { VideoEdit.slip(it, VideoEdit.Layer(l.id), -dx / pps, srcLen) } } }
                                            CutTool.SELECT -> {
                                                onSelect(Sel.Layer(l.id))
                                                if (base.track(lane.key).locked) return@awaitEachGesture
                                                action = when {
                                                    abs(at.x - x(l.startSec)) < edge -> { dx, _ ->
                                                        apply { pp -> pp.copy(layers = pp.layers.map {
                                                            if (it.id != l.id) it else {
                                                                val d = (snapped(l.startSec + dx / pps, l.id) - l.startSec).coerceIn(-l.inSec, l.length - 0.2)
                                                                it.copy(startSec = l.startSec + d, inSec = l.inSec + d)
                                                            }
                                                        }) }
                                                    }
                                                    abs(at.x - x(l.endSec)) < edge -> { dx, _ ->
                                                        apply { pp -> pp.copy(layers = pp.layers.map {
                                                            if (it.id != l.id) it else it.copy(outSec = (l.outSec + snapped(l.endSec + dx / pps, l.id) - l.endSec).coerceIn(l.inSec + 0.2, srcLen ?: Double.MAX_VALUE))
                                                        }) }
                                                    }
                                                    else -> { dx, dy ->
                                                        val target = laneAt(at.y + dy)?.takeIf { it.video && it.number >= 2 }?.number ?: l.track
                                                        val raw = l.startSec + dx / pps
                                                        val s1 = snapped(raw, l.id)
                                                        val start = if (s1 != raw) s1 else snapped(raw + l.length, l.id) - l.length
                                                        apply { VideoEdit.moveLayer(it, l.id, start, target) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                lane.audio -> {
                                    val a = base.audio.lastOrNull { it.track == lane.number && time >= it.startSec && time <= it.endSec }
                                    if (a == null) { onSelect(Sel.None); player.seek(h.project, time) }
                                    else {
                                        val srcLen = Media.probe(File(a.path))?.duration?.takeIf { it > 0.05 }
                                        when (tool) {
                                            CutTool.BLADE -> VideoEdit.split(base, VideoEdit.Sound(a.id), snapped(time))?.let { (q, id) -> h.edit { q }; onSelect(if (a.linkedTo != null) Sel.Clip(id) else Sel.Sound(id)) }
                                            CutTool.TRIM -> { onSelect(Sel.Sound(a.id)); if (a.linkedTo == null) action = { dx, _ -> apply { VideoEdit.slip(it, VideoEdit.Sound(a.id), -dx / pps, srcLen) } } }
                                            CutTool.SELECT -> {
                                                onSelect(Sel.Sound(a.id))
                                                if (base.track(lane.key).locked) return@awaitEachGesture
                                                action = when {
                                                    a.linkedTo != null -> { _, dy ->
                                                        // Linked sound follows its clip: only the track can change.
                                                        val target = laneAt(at.y + dy)?.takeIf { it.audio }?.number ?: a.track
                                                        apply { VideoEdit.moveSound(it, a.id, a.startSec, target) }
                                                    }
                                                    abs(at.x - x(a.startSec)) < edge -> { dx, _ ->
                                                        apply { pp -> pp.copy(audio = pp.audio.map {
                                                            if (it.id != a.id) it else {
                                                                val d = (snapped(a.startSec + dx / pps, a.id) - a.startSec).coerceIn(-a.inSec, a.length - 0.2)
                                                                it.copy(startSec = a.startSec + d, inSec = a.inSec + d)
                                                            }
                                                        }) }
                                                    }
                                                    abs(at.x - x(a.endSec)) < edge -> { dx, _ ->
                                                        apply { pp -> pp.copy(audio = pp.audio.map {
                                                            if (it.id != a.id) it else it.copy(outSec = (a.outSec + snapped(a.endSec + dx / pps, a.id) - a.endSec).coerceIn(a.inSec + 0.2, srcLen ?: Double.MAX_VALUE))
                                                        }) }
                                                    }
                                                    else -> { dx, dy ->
                                                        val target = laneAt(at.y + dy)?.takeIf { it.audio }?.number ?: a.track
                                                        apply { VideoEdit.moveSound(it, a.id, snapped(a.startSec + dx / pps, a.id), target) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> { onSelect(Sel.None); player.seek(h.project, time) }
                            }
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                action?.invoke(ch.position.x - at.x, ch.position.y - at.y)
                                ch.consume()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.type == PointerEventType.Scroll) {
                                    val d = ev.changes.first().scrollDelta
                                    if (ev.keyboardModifiers.isCtrlPressed) zoom = (zoom * if (d.y > 0) 0.85f else 1.18f).coerceIn(1f, 24f)
                                    else scroll = (scroll + (d.y + d.x) * 60f).coerceIn(0f, now[2] as Float)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            ) {
                if (waves < 0) return@Canvas
                fun label(text: String, at: Offset, style: TextStyle, width: Float? = null) {
                    if (at.y > size.height - 12f || at.x > size.width - 12f || (width != null && width < 8f)) return
                    if (width == null) drawText(measurer, text, at, style = style, maxLines = 1)
                    else drawText(measurer, text, at, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, size = Size(minOf(width, size.width - at.x), minOf(16f * u, size.height - at.y)))
                }
                val small = TextStyle(fontSize = 11.sp, color = labelColor)
                // Lanes: a light band each, the name at the left.
                lanes.forEach { lane ->
                    drawRect(Color(0x08000000), Offset(left, lane.top), Size(size.width - left, lane.height))
                    val name = when {
                        lane.key == "T" -> tr("Titel")
                        lane.key == "S" -> tr("Untertitel")
                        else -> lane.key
                    }
                    val dim = (lane.video && p.track(lane.key).hidden) || (lane.audio && p.track(lane.key).muted)
                    label(name, Offset(6f * u, lane.top + lane.height / 2 - 8f * u), if (dim) small.copy(color = labelColor.copy(alpha = 0.4f)) else small)
                }
                // In–Out of the timeline.
                val mi = p.markIn; val mo = p.markOut
                if (mi != null || mo != null) {
                    val a = x(mi ?: 0.0).coerceAtLeast(left); val b = x(mo ?: p.duration)
                    if (b > a) drawRect(brand.copy(alpha = 0.18f), Offset(a, 0f), Size(b - a, 22f * u))
                }
                // Ruler.
                // Up to an hour between the marks: a 90-minute lecture at zoom 1 has less than 70 px per 5 minutes, and
                // first {} without a match threw while the timeline was drawn.
                val step = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 900.0, 1800.0, 3600.0).firstOrNull { it * pxPerSec >= 70 * u }
                    ?: (kotlin.math.ceil(70.0 * u / maxOf(pxPerSec, 1e-6f) / 3600.0) * 3600.0)
                var s = (t(left) / step).toInt() * step
                while (x(s) < size.width) {
                    if (x(s) >= left) {
                        drawLine(labelColor.copy(alpha = 0.5f), Offset(x(s), 0f), Offset(x(s), 22f * u), 1f)
                        label(clock(s), Offset(x(s) + 3f, 2f), TextStyle(fontSize = 10.sp, color = labelColor))
                    }
                    s += step
                }
                // Markers: a small flag in the ruler, a thin line down.
                p.markers.forEach { m ->
                    val mx = x(m.sec)
                    if (mx < left) return@forEach
                    val chosen = sel is Sel.Mark && sel.id == m.id
                    drawLine(MARKER.copy(alpha = 0.35f), Offset(mx, 22f * u), Offset(mx, size.height), 1f)
                    val flag = Path().apply { moveTo(mx, 8f * u); lineTo(mx + 7f * u, 13f * u); lineTo(mx, 18f * u); close() }
                    drawPath(flag, if (chosen) SELECTED else MARKER)
                    drawLine(if (chosen) SELECTED else MARKER, Offset(mx, 6f * u), Offset(mx, 22f * u), 2f)
                }
                val starts = p.starts()
                fun lane(key: String) = lanes.first { it.key == key }
                fun wave(path: String, from: Double, len: Double, x0: Float, x1: Float, top: Float, hh: Float, color: Color) {
                    val env = AudioSync.cached(File(path)) ?: return
                    val mid = top + hh / 2
                    var px = maxOf(x0, left)
                    val stepPx = maxOf(1f, u)
                    while (px < minOf(x1, size.width)) {
                        val ts = from + (px - x0) / pxPerSec
                        val k = (ts * AudioSync.RATE).toInt()
                        val k2 = ((ts + stepPx / pxPerSec) * AudioSync.RATE).toInt().coerceAtLeast(k + 1)
                        var v = 0f
                        for (j in k until minOf(k2, env.size)) if (j >= 0 && env[j] > v) v = env[j]
                        if (ts <= from + len) drawLine(color, Offset(px, mid - v * hh / 2), Offset(px, mid + v * hh / 2), stepPx)
                        px += stepPx
                    }
                }
                // V1.
                val v1 = lane("V1")
                p.clips.forEachIndexed { i, c ->
                    val x0 = maxOf(left, x(starts[i])); val x1 = x(starts[i] + c.length)
                    if (x1 < left || x0 > size.width) return@forEachIndexed
                    val selected = sel is Sel.Clip && sel.id == c.id
                    val hidden = p.track("V1").hidden
                    drawRoundRect((if (i % 2 == 0) V1A else V1B).copy(alpha = if (hidden) 0.35f else 1f), Offset(x0, v1.top), Size(maxOf(2f, x1 - x0 - 1f), v1.height), CornerRadius(5f))
                    if (!c.muted && !p.track("V1").muted) wave(c.path, c.inSec + (x0 - x(starts[i])) / pxPerSec, c.length, x0, x1, v1.top + v1.height * 0.55f, v1.height * 0.42f, Color(0x55FFFFFF))
                    if (selected) drawRoundRect(SELECTED, Offset(x0, v1.top), Size(maxOf(2f, x1 - x0 - 1f), v1.height), CornerRadius(5f), style = Stroke(3f))
                    // Effects of AP-54 after the name („2× · Zoom · 3 Bereiche“).
                    val fxText = if (c.fx.isNone) "" else io.github.thepytales.llmhub.video.VideoFx.summary(c.fx)
                    if (x1 - x0 > 40) label(if (fxText.isBlank()) c.name else "${c.name} · $fxText", Offset(x0 + 6f, v1.top + 5f), TextStyle(fontSize = 11.sp, color = Color.White), x1 - x0 - 10f)
                    if (x1 - x0 > 40) label(clock(c.length, true) + (if (c.muted) " · " + tr("stumm") else "") + (if (!c.frame.isFull) " · " + tr("platziert") else ""), Offset(x0 + 6f, v1.top + 21f * u), TextStyle(fontSize = 10.sp, color = Color(0xCCFFFFFF)), x1 - x0 - 10f)
                    if (p.transitionOf(i) > 0) {
                        val tx = x(starts[i]); val tw = (p.transitionOf(i) * pxPerSec).toFloat()
                        drawRect(Color(0x66FFFFFF), Offset(tx, v1.top + v1.height - 10f), Size(tw, 8f))
                    }
                }
                // Pictures on V2 ….
                p.layers.forEach { l ->
                    val ln = lanes.firstOrNull { it.key == "V${l.track}" } ?: return@forEach
                    val x0 = maxOf(left, x(l.startSec)); val x1 = x(l.endSec)
                    if (x1 < left || x0 > size.width) return@forEach
                    val selected = sel is Sel.Layer && sel.id == l.id
                    drawRoundRect(LAYER.copy(alpha = if (p.track(ln.key).hidden) 0.35f else 1f), Offset(x0, ln.top), Size(maxOf(2f, x1 - x0 - 1f), ln.height), CornerRadius(5f))
                    if (selected) drawRoundRect(SELECTED, Offset(x0, ln.top), Size(maxOf(2f, x1 - x0 - 1f), ln.height), CornerRadius(5f), style = Stroke(3f))
                    if (x1 - x0 > 30) label(l.name, Offset(x0 + 5f, ln.top + 4f), TextStyle(fontSize = 10.sp, color = Color.White), x1 - x0 - 8f)
                    if (x1 - x0 > 30) label(tr("Bild-in-Bild") + " · " + clock(l.length, true), Offset(x0 + 5f, ln.top + 19f * u), TextStyle(fontSize = 9.sp, color = Color(0xCCFFFFFF)), x1 - x0 - 8f)
                }
                // Sound on A1 … with waveform; linked sound darker, with the clip it belongs to.
                p.audio.forEach { a ->
                    val ln = lanes.firstOrNull { it.key == "A${a.track}" } ?: return@forEach
                    val x0 = maxOf(left, x(a.startSec)); val x1 = x(a.endSec)
                    if (x1 < left || x0 > size.width) return@forEach
                    val selected = (sel is Sel.Sound && sel.id == a.id) || (sel is Sel.Clip && a.linkedTo == sel.id)
                    val color = (if (a.linkedTo != null) LINKED else SOUND).copy(alpha = if (p.track(ln.key).muted) 0.35f else 1f)
                    drawRoundRect(color, Offset(x0, ln.top), Size(maxOf(2f, x1 - x0 - 1f), ln.height), CornerRadius(5f))
                    wave(a.path, a.inSec + (x0 - x(a.startSec)) / pxPerSec, a.length, x0, x1, ln.top + 2f, ln.height - 4f, Color(0x66FFFFFF))
                    if (selected) drawRoundRect(SELECTED, Offset(x0, ln.top), Size(maxOf(2f, x1 - x0 - 1f), ln.height), CornerRadius(5f), style = Stroke(3f))
                    if (x1 - x0 > 30) label((if (a.linkedTo != null) "∞ " else "") + a.name, Offset(x0 + 5f, ln.top + 4f), TextStyle(fontSize = 10.sp, color = Color.White), x1 - x0 - 8f)
                }
                // Titles and subtitles.
                val tl = lane("T")
                p.overlays.forEach { o ->
                    val x0 = maxOf(left, x(o.startSec)); val x1 = x(o.endSec)
                    if (x1 < left || x0 > size.width) return@forEach
                    val selected = sel is Sel.Title && sel.id == o.id
                    drawRoundRect(Color(0xFF97C6FF), Offset(x0, tl.top), Size(maxOf(2f, x1 - x0), tl.height), CornerRadius(5f))
                    if (selected) drawRoundRect(SELECTED, Offset(x0, tl.top), Size(maxOf(2f, x1 - x0), tl.height), CornerRadius(5f), style = Stroke(3f))
                    if (x1 - x0 > 30) label(TextOverlay.label(o.style) + ": " + o.text, Offset(x0 + 5f, tl.top + 5f), TextStyle(fontSize = 10.sp, color = Color(0xFF001450)), x1 - x0 - 8f)
                }
                val sl = lane("S")
                p.subtitles.forEach { cue ->
                    val x0 = maxOf(left, x(cue.startSec)); val x1 = x(cue.endSec)
                    if (x1 >= left && x0 <= size.width) drawRect(if (p.burnSubtitles) Color(0xFF566371) else Color(0x66566371), Offset(x0, sl.top), Size(maxOf(1f, x1 - x0 - 1f), sl.height))
                }
                // A file dragged from the media pool: where it would go.
                drag.item?.let { item ->
                    val tgt = drag.target
                    if (tgt != null && tgt.contains(drag.at)) {
                        val local = drag.at - tgt.topLeft
                        lanes.firstOrNull { local.y in it.top..it.bottom && (it.video || it.audio) }?.let { ln ->
                            drawRect(SELECTED.copy(alpha = 0.25f), Offset(maxOf(left, local.x), ln.top), Size(80f * u, ln.height))
                            label(item.name, Offset(maxOf(left, local.x) + 4f, ln.top + 4f), TextStyle(fontSize = 10.sp, color = labelColor), 76f * u)
                        }
                    }
                }
                // Playhead.
                val px = x(pos)
                if (px >= left) {
                    drawLine(brand, Offset(px, 0f), Offset(px, size.height), 2f)
                    drawCircle(brand, 5f, Offset(px, 6f))
                }
                drawLine(labelColor.copy(alpha = 0.25f), Offset(left - 1f, 0f), Offset(left - 1f, size.height), 1f)
            }
            // Track heads: lock, mute (sound), hide (picture).
            lanes.filter { it.video || it.audio }.forEach { lane ->
                val st = p.track(lane.key)
                Row(
                    Modifier.offset { IntOffset((34f * u).toInt(), (lane.top + lane.height / 2 - 11f * u).toInt()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackToggle(if (st.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, st.locked, tr("Spur {0} sperren", lane.key)) {
                        h.edit { VideoEdit.setTrack(it, lane.key) { s -> s.copy(locked = !s.locked) } }
                    }
                    TrackToggle(if (st.muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, st.muted, tr("Spur {0} stumm", lane.key)) {
                        h.edit { VideoEdit.setTrack(it, lane.key) { s -> s.copy(muted = !s.muted) } }
                    }
                    if (lane.video) TrackToggle(if (st.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, st.hidden, tr("Spur {0} ausblenden", lane.key)) {
                        h.edit { VideoEdit.setTrack(it, lane.key) { s -> s.copy(hidden = !s.hidden) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackToggle(icon: ImageVector, on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (on) SELECTED else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
    }
}
