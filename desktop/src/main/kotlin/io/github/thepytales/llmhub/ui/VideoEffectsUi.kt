package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.video.Faces
import io.github.thepytales.llmhub.video.Key
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.Region
import io.github.thepytales.llmhub.video.VideoClip
import io.github.thepytales.llmhub.video.VideoFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** „2×“, „0,25×“. */
internal fun speedLabel(s: Double): String = String.format(Locale.GERMANY, "%.2f", s).trimEnd('0').trimEnd(',') + "×"

/**
 * Effects of a clip on V1 (AP-54), below its properties: speed, reverse, freeze, colour, zoom and pan with keyframes
 * (set where the playhead is), the vertical reframe, regions to pixelate (by hand or the faces found), and the voice.
 * Every change goes through the history (Strg+Z) and through the same graph as the export.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClipEffectsEditor(h: History, clip: VideoClip, pos: Double, onSelect: (Sel) -> Unit) {
    val p = h.project
    val fx = clip.fx
    val scope = rememberCoroutineScope()
    val start = p.clipStart(clip.id) ?: 0.0
    // The source time under the playhead (or the clip's first frame when the playhead is elsewhere).
    val src = clip.sourceAt((pos - start).coerceIn(0.0, clip.length))
    fun change(key: String?, f: (io.github.thepytales.llmhub.video.ClipFx) -> io.github.thepytales.llmhub.video.ClipFx) = h.edit(key) { VideoFx.change(it, clip.id, f) }

    @Composable
    fun slider(label: String, shown: String, value: Float, range: ClosedFloatingPointRange<Float>, key: String, steps: Int = 0, onChange: (Float) -> Unit) {
        Text("$label $shown", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.height(28.dp))
    }

    HorizontalDivider()
    Text(tr("Effekte"), style = MaterialTheme.typography.titleSmall)
    VideoFx.summary(fx).takeIf { it.isNotBlank() }?.let { HintText(it) }

    // ── Time ──
    if (!fx.freeze) {
        Text(tr("Geschwindigkeit"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            VideoFx.SPEEDS.forEach { s ->
                FilterChip(selected = abs(fx.speed - s) < 1e-6, onClick = { change(null) { it.copy(speed = s) } }, label = { Text(speedLabel(s)) })
            }
        }
        if (abs(fx.speed - 1.0) > 1e-6) HintText(tr("Der Ton läuft mit, die Tonhöhe bleibt."))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Rückwärts"), modifier = Modifier.weight(1f))
            Switch(fx.reverse, { on ->
                val (q, why) = VideoFx.reverse(p, clip.id, on)
                if (q != null) h.edit { q } else why?.let { Notices.error(it) }
            })
        }
        OutlinedButton(onClick = {
            VideoFx.freeze(p, pos.coerceIn(start, start + clip.length), 2.0)?.let { (q, id) -> h.edit { q }; onSelect(Sel.Clip(id)) }
        }) { Text(tr("Standbild am Abspielkopf (2 s)")) }
    } else HintText(tr("Standbild: zeigt das Bild bei {0} s der Datei. Die Länge stellst du über Anfang und Ende ein.", String.format(Locale.GERMANY, "%.2f", clip.inSec)))

    // ── Colour ──
    Text(tr("Bildkorrektur"), style = MaterialTheme.typography.labelLarge)
    slider(tr("Helligkeit"), "${(fx.brightness * 100).roundToInt()}", fx.brightness, -0.5f..0.5f, "b") { v -> change("fxb-${clip.id}") { it.copy(brightness = v) } }
    slider(tr("Kontrast"), "${(fx.contrast * 100).roundToInt()} %", fx.contrast, 0.5f..2f, "c") { v -> change("fxc-${clip.id}") { it.copy(contrast = v) } }
    slider(tr("Sättigung"), "${(fx.saturation * 100).roundToInt()} %", fx.saturation, 0f..2f, "s") { v -> change("fxs-${clip.id}") { it.copy(saturation = v) } }
    var balancing by remember(clip.id) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(enabled = !balancing, onClick = {
            balancing = true
            scope.launch {
                val img = withContext(Dispatchers.IO) { Media.frameAwt(File(clip.path), src, 360) }
                balancing = false
                if (img == null) { Notices.error(tr("Das Bild ließ sich nicht lesen.")); return@launch }
                val (r, g, b) = VideoFx.grayWorld(img.getRGB(0, 0, img.width, img.height, null, 0, img.width))
                change(null) { it.copy(gainR = r, gainG = g, gainB = b) }
                Notices.info(tr("Weißabgleich: Rot {0}, Grün {1}, Blau {2}.", String.format(Locale.GERMANY, "%.2f", r), String.format(Locale.GERMANY, "%.2f", g), String.format(Locale.GERMANY, "%.2f", b)))
            }
        }) { if (balancing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(tr("Weißabgleich automatisch")) }
        if (fx.corrected || fx.balanced) TextButton(onClick = { change(null) { it.copy(brightness = 0f, contrast = 1f, saturation = 1f, gainR = 1f, gainG = 1f, gainB = 1f) } }) { Text(tr("Zurücksetzen")) }
    }

    // ── Zoom and pan ──
    Text(tr("Zoom und Schwenk"), style = MaterialTheme.typography.labelLarge)
    HintText(tr("Abspielkopf an eine Stelle setzen und den Ausschnitt einstellen – dort entsteht ein Keyframe; dazwischen gleitet das Bild."))
    val zk = VideoFx.keyAt(fx.zoom, src, fx.ease) ?: Key(src)
    fun setZoom(k: Key) = change("fxz-${clip.id}-${(src * 10).roundToInt()}") { f -> f.copy(zoom = (f.zoom.filter { abs(it.t - src) > 0.1 } + k.copy(t = src)).sortedBy { it.t }) }
    slider(tr("Ausschnitt"), "${(zk.w * 100).roundToInt()} %", zk.w, 0.15f..1f, "zw") { setZoom(zk.copy(w = it)) }
    slider(tr("Waagrecht"), "${(zk.x * 100).roundToInt()} %", zk.x, 0f..1f, "zx") { setZoom(zk.copy(x = it)) }
    slider(tr("Senkrecht"), "${(zk.y * 100).roundToInt()} %", zk.y, 0f..1f, "zy") { setZoom(zk.copy(y = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Sanft beschleunigen"), modifier = Modifier.weight(1f))
        Switch(fx.ease, { e -> change(null) { it.copy(ease = e) } })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Hochformat 9:16 (folgt den Keyframes)"), modifier = Modifier.weight(1f))
        Switch(fx.vertical, { v -> change(null) { it.copy(vertical = v) } })
    }
    if (fx.zoom.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("{0} Keyframe(s)", fx.zoom.size), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { change(null) { f -> f.copy(zoom = f.zoom.filter { abs(it.t - src) > 0.1 }) } }, enabled = fx.zoom.any { abs(it.t - src) <= 0.1 }) { Text(tr("Keyframe hier löschen")) }
        TextButton(onClick = { change(null) { it.copy(zoom = emptyList()) } }) { Text(tr("Alle löschen")) }
    }

    // ── Regions ──
    Text(tr("Bereiche unkenntlich machen"), style = MaterialTheme.typography.labelLarge)
    var finding by remember(clip.id) { mutableStateOf<Float?>(null) }
    val missing = remember(clip.id) { Faces.missing() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { h.edit { VideoFx.addRegion(it, clip.id, Key(src, 0.5f, 0.5f, 0.2f, 0.25f)).first } }) { Text(tr("Bereich hinzufügen")) }
        OutlinedButton(enabled = finding == null && missing == null, onClick = {
            finding = 0f
            scope.launch {
                runCatching { Faces.find(clip) { finding = it } }
                    .onSuccess { found ->
                        val n = found.size
                        h.edit { Faces.apply(it, clip.id, found) }
                        ToolActions.log(tr("Anonymisierung im Video „{0}“: {1} Gesicht(er) in Clip „{2}“ verpixelt ({3}).", h.project.title, n, clip.name, Faces.detector()?.name ?: ""))
                        Notices.info(if (n == 0) tr("Keine Gesichter gefunden.") else tr("{0} Gesicht(er) gefunden und verpixelt – bitte in der Vorschau prüfen.", n))
                    }
                    .onFailure { Notices.error(it.message ?: tr("Die Gesichtserkennung ist fehlgeschlagen.")) }
                finding = null
            }
        }) { Text(tr("Gesichter finden")) }
    }
    finding?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
    missing?.let { HintText(it) }
    fx.regions.forEachIndexed { j, r -> RegionRow(h, clip, r, j, src) }

    // ── Voice ──
    Text(tr("Stimme verfremden"), style = MaterialTheme.typography.labelLarge)
    slider(tr("Tonhöhe"), if (abs(fx.voice) < 0.01f) tr("aus") else tr("{0} Halbtöne", String.format(Locale.GERMANY, "%+.0f", fx.voice)), fx.voice, -8f..8f, "v", steps = 15) { v ->
        h.edit("fxv-${clip.id}") { VideoFx.voice(it, v.roundToInt().toFloat(), setOf(clip.id)) }
    }
    if (abs(fx.voice) > 0.01f) HintText(tr("Für echte Anonymität zusätzlich Namen und Orte im Ton prüfen – eine verschobene Stimme lässt sich teilweise zurückrechnen."))
}

/** One region: kind, place at the playhead (a keyframe there), delete. Found faces only show their track. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionRow(h: History, clip: VideoClip, r: Region, index: Int, src: Double) {
    fun update(key: String?, f: (Region) -> Region) = h.edit(key) { VideoFx.change(it, clip.id) { fx -> fx.copy(regions = fx.regions.map { x -> if (x.id == r.id) f(x) else x }) } }
    Text(
        if (r.face) tr("Gesicht {0} ({1}–{2} s)", index + 1, String.format(Locale.GERMANY, "%.1f", r.keys.minOf { it.t }), String.format(Locale.GERMANY, "%.1f", r.keys.maxOf { it.t }))
        else tr("Bereich {0}", index + 1),
        style = MaterialTheme.typography.bodyMedium,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("pixel" to tr("Verpixeln"), "blur" to tr("Weichzeichnen")).forEach { (k, l) ->
            FilterChip(selected = r.mode == k, onClick = { update(null) { it.copy(mode = k) } }, label = { Text(l) })
        }
        TextButton(onClick = { h.edit { VideoFx.change(it, clip.id) { fx -> fx.copy(regions = fx.regions.filter { x -> x.id != r.id }) } } }) { Text(tr("Entfernen")) }
    }
    if (!r.face) {
        val k = VideoFx.keyAt(r.keys, src, false) ?: Key(src)
        fun set(n: Key) = update("rg-${r.id}-${(src * 10).roundToInt()}") { x -> x.copy(keys = (x.keys.filter { abs(it.t - src) > 0.1 } + n.copy(t = src)).sortedBy { it.t }) }
        @Composable
        fun s(label: String, v: Float, range: ClosedFloatingPointRange<Float>, f: (Float) -> Key) {
            Text("$label ${(v * 100).roundToInt()} %", style = MaterialTheme.typography.labelSmall)
            Slider(value = v, onValueChange = { set(f(it)) }, valueRange = range, modifier = Modifier.height(24.dp))
        }
        s(tr("Waagrecht"), k.x, 0f..1f) { k.copy(x = it) }
        s(tr("Senkrecht"), k.y, 0f..1f) { k.copy(y = it) }
        s(tr("Breite"), k.w, 0.03f..1f) { k.copy(w = it) }
        s(tr("Höhe"), k.h, 0.03f..1f) { k.copy(h = it) }
        if (r.keys.size > 1) HintText(tr("{0} Keyframes – der Bereich folgt dazwischen.", r.keys.size))
    }
}
