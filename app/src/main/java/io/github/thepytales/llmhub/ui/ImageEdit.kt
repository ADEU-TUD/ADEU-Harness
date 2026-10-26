package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** Colors of the marked areas (Bereich 1–4), also used in the chat to show what was marked. */
val REGION_COLORS = listOf(Color(0xFFE5484D), Color(0xFF3E63DD), Color(0xFF30A46C), Color(0xFFF76B15))

/** One brush stroke in coordinates relative to the picture (0–1), with its width relative to the picture width. */
data class EditStroke(val points: List<Offset>, val width: Float)

/** A marked area: strokes plus what should happen there. */
data class EditRegion(val index: Int, val strokes: List<EditStroke>, val description: String) {
    val color get() = REGION_COLORS[index % REGION_COLORS.size]

    /** Where the area lies, in words ("oben links") – for models that edit without a mask. */
    fun place(): String {
        val pts = strokes.flatMap { it.points }
        if (pts.isEmpty()) return "im Bild"
        val x = (pts.minOf { it.x } + pts.maxOf { it.x }) / 2
        val y = (pts.minOf { it.y } + pts.maxOf { it.y }) / 2
        val v = when { y < 0.34f -> "oben"; y > 0.66f -> "unten"; else -> "" }
        val h = when { x < 0.34f -> "links"; x > 0.66f -> "rechts"; else -> "" }
        return when {
            v.isEmpty() && h.isEmpty() -> "in der Mitte"
            v.isEmpty() -> "in der Mitte $h"
            h.isEmpty() -> "$v in der Mitte"
            else -> "$v $h"
        }
    }

    /** Bounding box of the strokes (0–1): left, top, right, bottom. */
    private fun box(): FloatArray? {
        val pts = strokes.flatMap { s -> s.points.map { it to s.width / 2 } }
        if (pts.isEmpty()) return null
        return floatArrayOf(
            pts.minOf { it.first.x - it.second }.coerceIn(0f, 1f), pts.minOf { it.first.y - it.second }.coerceIn(0f, 1f),
            pts.maxOf { it.first.x + it.second }.coerceIn(0f, 1f), pts.maxOf { it.first.y + it.second }.coerceIn(0f, 1f),
        )
    }

    /**
     * Where the area lies in English, with its extent in percent ("at the bottom center (about 40–60 % from the left,
     * 70–95 % from the top)"): image models without masks (FLUX.2) only know the place from the words.
     */
    fun placeEnglish(): String {
        val b = box() ?: return "in the image"
        val x = (b[0] + b[2]) / 2
        val y = (b[1] + b[3]) / 2
        val v = when { y < 0.34f -> "top"; y > 0.66f -> "bottom"; else -> "" }
        val h = when { x < 0.34f -> "left"; x > 0.66f -> "right"; else -> "" }
        val where = when {
            v.isEmpty() && h.isEmpty() -> "in the center"
            v.isEmpty() -> "on the $h side"
            h.isEmpty() -> "at the $v center"
            else -> "in the $v $h corner"
        }
        fun pct(f: Float) = (f * 100).roundToInt()
        return "$where (about ${pct(b[0])}–${pct(b[2])} % from the left edge, ${pct(b[1])}–${pct(b[3])} % from the top edge)"
    }
}

/**
 * What goes to the model, in English (image models follow it far better than German): with a mask and without one.
 * Both name the places in words, because many servers accept a mask and still ignore it (FLUX.2 edits from the
 * reference picture only) – a prompt that only says "the marked areas" then changes nothing. Pass descriptions that
 * are already English (the view model translates them); German ones still work, just less reliably.
 */
fun editPrompts(regions: List<EditRegion>, note: String): Pair<String, String> {
    val used = regions.filter { it.strokes.isNotEmpty() }
    val extra = note.trim().takeIf { it.isNotEmpty() }
    if (used.isEmpty()) {
        val whole = extra ?: "Improve the overall quality of the image."
        return whole to whole
    }
    val changes = used.joinToString("; ") { r ->
        "${r.placeEnglish()}: ${r.description.trim().ifEmpty { "fill in something fitting" }}"
    }
    val keep = "Keep the rest of the picture (composition, colors, lighting) as it is."
    val also = extra?.let { " Also: $it" }.orEmpty()
    val withMask = "Edit the masked area of this image. Make this change clearly visible – $changes. $keep$also"
    val withoutMask = "Edit this image. Make this change clearly visible – $changes. $keep$also"
    return withMask to withoutMask
}

/** Short lines for the chat ("1 · oben links: Himmel blauer"). */
fun editSummary(regions: List<EditRegion>): List<String> =
    regions.filter { it.strokes.isNotEmpty() }.map { "${it.index + 1} · ${it.place()}: ${it.description.trim().ifEmpty { "–" }}" }

/** The painted strokes in the size of the picture: white where something was painted, black elsewhere. */
fun renderMask(width: Int, height: Int, regions: List<EditRegion>): ImageBitmap {
    val bmp = ImageBitmap(width, height)
    val canvas = Canvas(bmp)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.Black })
    val line = Paint().apply {
        color = Color.White
        style = PaintingStyle.Stroke
        strokeCap = StrokeCap.Round
    }
    val dot = Paint().apply { color = Color.White }
    regions.flatMap { it.strokes }.forEach { s ->
        line.strokeWidth = s.width * width
        val pts = s.points.map { Offset(it.x * width, it.y * height) }
        if (pts.size == 1) canvas.drawCircle(pts[0], s.width * width / 2, dot)
        pts.zipWithNext { a, b -> canvas.drawLine(a, b, line) }
    }
    return bmp
}

/**
 * The mask as PNG for /images/edits, readable in both conventions: OpenAI reads the alpha channel (transparent = may
 * change), diffusers-based servers read the brightness (white = may change). Painted pixels are therefore white
 * *and* transparent, the rest opaque black. Bitmaps store premultiplied colours (a transparent pixel is always
 * black), so the PNG is written here directly.
 */
fun maskPng(width: Int, height: Int, regions: List<EditRegion>): ByteArray {
    val painted = renderMask(width, height, regions)
    val pixels = IntArray(width * height)
    painted.readPixels(pixels, 0, 0, width, height)
    return Png.rgba(width, height) { x, y ->
        val p = pixels[y * width + x]
        if ((p shr 8 and 0xFF) > 127) 0xFFFFFF00.toInt() else 0x000000FF
    }
}

/** A minimal PNG encoder (8-bit RGBA, no interlacing); [rgba] gives each pixel as 0xRRGGBBAA. */
object Png {
    fun rgba(width: Int, height: Int, rgba: (Int, Int) -> Int): ByteArray {
        val raw = java.io.ByteArrayOutputStream(height * (width * 4 + 1))
        for (y in 0 until height) {
            raw.write(0) // no filter
            for (x in 0 until width) {
                val v = rgba(x, y)
                raw.write(v ushr 24 and 0xFF); raw.write(v ushr 16 and 0xFF); raw.write(v ushr 8 and 0xFF); raw.write(v and 0xFF)
            }
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val len = data.size
            out.write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
            val crc = java.util.zip.CRC32()
            val t = type.toByteArray(Charsets.US_ASCII)
            out.write(t); crc.update(t)
            out.write(data); crc.update(data)
            val c = crc.value.toInt()
            out.write(byteArrayOf((c ushr 24).toByte(), (c ushr 16).toByte(), (c ushr 8).toByte(), c.toByte()))
        }
        val header = java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(6).put(0).put(0).put(0).array()
        chunk("IHDR", header)
        val deflater = java.util.zip.Deflater(6)
        val z = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(z, deflater).use { it.write(raw.toByteArray()) }
        deflater.end()
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}

/**
 * Full screen editor: paint over what should change, one color per area, and say for each area what to do.
 * [onDone] gets the areas (none: the whole picture is reworked from the description).
 */
@Composable
fun ImageEditor(image: ImageBitmap, initial: List<EditRegion>, onDone: (List<EditRegion>) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<Pair<Int, EditStroke>>().apply { initial.forEach { r -> r.strokes.forEach { add(r.index to it) } } } }
    val descriptions = remember { mutableStateListOf("", "", "", "").apply { initial.forEach { set(it.index, it.description) } } }
    var region by remember { mutableIntStateOf(0) }
    var brush by remember { mutableFloatStateOf(0.06f) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val ratio = image.width.toFloat() / image.height.coerceAtLeast(1)
    fun regions() = (0 until 4).map { i -> EditRegion(i, strokes.filter { it.first == i }.map { it.second }, descriptions[i]) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Abbrechen") }
                    Text("Bereiche markieren", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(enabled = strokes.isNotEmpty(), onClick = { strokes.removeAt(strokes.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Letzten Strich zurücknehmen")
                    }
                    IconButton(enabled = strokes.isNotEmpty(), onClick = { strokes.clear() }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Alle Markierungen löschen")
                    }
                    Button(onClick = { onDone(regions()) }, modifier = Modifier.padding(end = 8.dp)) { Text("Fertig") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .widthIn(max = maxWidth)
                                .aspectRatio(ratio)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            Image(image, contentDescription = "Bild zum Überarbeiten", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                            Canvas(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(region, brush) {
                                        detectTapGestures { p ->
                                            strokes.add(region to EditStroke(listOf(Offset(p.x / size.width, p.y / size.height)), brush))
                                        }
                                    }
                                    .pointerInput(region, brush) {
                                        detectDragGestures(
                                            onDragStart = { p -> current = listOf(Offset(p.x / size.width, p.y / size.height)) },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                current = current + Offset(change.position.x / size.width, change.position.y / size.height)
                                            },
                                            onDragEnd = {
                                                if (current.isNotEmpty()) strokes.add(region to EditStroke(current, brush))
                                                current = emptyList()
                                            },
                                            onDragCancel = { current = emptyList() },
                                        )
                                    },
                            ) {
                                fun draw(color: Color, s: EditStroke) {
                                    val pts = s.points.map { Offset(it.x * size.width, it.y * size.height) }
                                    val w = s.width * size.width
                                    if (pts.size == 1) drawCircle(color, w / 2, pts[0])
                                    pts.zipWithNext { a, b -> drawLine(color, a, b, strokeWidth = w, cap = StrokeCap.Round) }
                                }
                                strokes.forEach { (i, s) -> draw(REGION_COLORS[i].copy(alpha = 0.45f), s) }
                                if (current.isNotEmpty()) draw(REGION_COLORS[region].copy(alpha = 0.45f), EditStroke(current, brush))
                                // Outline so the areas stay visible on similar colors.
                                drawRect(Color.Transparent, style = Stroke(1f))
                            }
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    HintText("Mit dem Finger (oder der Maus) über die Stellen malen, die sich ändern sollen. Für jede Farbe beschreiben, was dort passieren soll.")
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Pinsel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                        Slider(value = brush, onValueChange = { brush = it }, valueRange = 0.02f..0.15f, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until 4).forEach { i ->
                            FilterChip(
                                selected = region == i, onClick = { region = i },
                                leadingIcon = { Box(Modifier.size(12.dp).background(REGION_COLORS[i], CircleShape)) },
                                label = { Text("Bereich ${i + 1}") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = REGION_COLORS[i].copy(alpha = 0.18f)),
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    (0 until 4).filter { i -> i == region || strokes.any { it.first == i } }.forEach { i ->
                        OutlinedTextField(
                            value = descriptions[i],
                            onValueChange = { descriptions[i] = it },
                            leadingIcon = { Box(Modifier.size(14.dp).background(REGION_COLORS[i], CircleShape)) },
                            label = { Text("Bereich ${i + 1}: was soll dort hin?") },
                            placeholder = { Text("z. B. ein blauer Himmel mit Wolken") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    if (strokes.isEmpty()) {
                        TextButton(onClick = { onDone(emptyList()) }) { Text("Ohne Markierung: das ganze Bild nach der Beschreibung überarbeiten") }
                    }
                    Spacer(Modifier.size(16.dp))
                }
            }
        }
    }
}
