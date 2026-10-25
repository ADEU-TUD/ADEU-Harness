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
import androidx.compose.ui.graphics.BlendMode
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
}

/** What goes to the model: with a mask ("the marked areas") and without one (places in words). */
fun editPrompts(regions: List<EditRegion>, note: String): Pair<String, String> {
    val used = regions.filter { it.strokes.isNotEmpty() }
    val extra = note.trim().takeIf { it.isNotEmpty() }
    if (used.isEmpty()) {
        val whole = extra ?: "Verbessere das Bild."
        return whole to whole
    }
    val keep = "Alles andere bleibt genau wie im Original (Bildaufbau, Personen, Farben, Licht)."
    val withMask = "Überarbeite nur die markierten Bereiche. " +
        used.joinToString(" ") { "Bereich ${it.index + 1}: ${it.description.trim().ifEmpty { "passend ergänzen" }}." } +
        " $keep" + (extra?.let { " Zusätzlich: $it" } ?: "")
    val withoutMask = "Überarbeite das Bild nur an diesen Stellen: " +
        used.joinToString("; ") { "${it.place()}: ${it.description.trim().ifEmpty { "passend ergänzen" }}" } +
        ". $keep" + (extra?.let { " Zusätzlich: $it" } ?: "")
    return withMask to withoutMask
}

/** Short lines for the chat ("1 · oben links: Himmel blauer"). */
fun editSummary(regions: List<EditRegion>): List<String> =
    regions.filter { it.strokes.isNotEmpty() }.map { "${it.index + 1} · ${it.place()}: ${it.description.trim().ifEmpty { "–" }}" }

/**
 * The mask in the size of the picture: opaque everywhere, transparent where something was painted (OpenAI
 * convention: transparent = may change).
 */
fun renderMask(width: Int, height: Int, regions: List<EditRegion>): ImageBitmap {
    val bmp = ImageBitmap(width, height)
    val canvas = Canvas(bmp)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.Black })
    val clear = Paint().apply {
        blendMode = BlendMode.Clear
        style = PaintingStyle.Stroke
        strokeCap = StrokeCap.Round
    }
    val dot = Paint().apply { blendMode = BlendMode.Clear }
    regions.flatMap { it.strokes }.forEach { s ->
        clear.strokeWidth = s.width * width
        val pts = s.points.map { Offset(it.x * width, it.y * height) }
        if (pts.size == 1) canvas.drawCircle(pts[0], s.width * width / 2, dot)
        pts.zipWithNext { a, b -> canvas.drawLine(a, b, clear) }
    }
    return bmp
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
