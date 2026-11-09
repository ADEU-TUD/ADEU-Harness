package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.video.Frame
import io.github.thepytales.llmhub.video.Media
import io.github.thepytales.llmhub.video.PoolItem
import io.github.thepytales.llmhub.video.VideoEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the media pool does with a file (AP-53). */
internal enum class PoolUse { INSERT, OVERWRITE, PIP, SOUND }

/**
 * Media pool (AP-53): the files of the project in folders (bins), with picture, length and search. A click opens the
 * file in the source monitor; dragging puts it on the timeline (V1 inserts, V2 … as picture-in-picture, A … as sound).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaPool(h: History, drag: PoolDrag, open: String?, onOpen: (PoolItem) -> Unit, onImport: () -> Unit, onUse: (PoolItem, PoolUse) -> Unit, modifier: Modifier = Modifier) {
    val p = h.project
    var query by remember { mutableStateOf("") }
    var bin by remember { mutableStateOf<String?>(null) }
    var newBin by remember { mutableStateOf(false) }
    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Medienpool"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { newBin = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.CreateNewFolder, contentDescription = tr("Neuer Ordner"), modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onImport, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Add, contentDescription = tr("Dateien in den Medienpool"), modifier = Modifier.size(18.dp)) }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text(tr("Suchen")) }, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth().typing(), textStyle = MaterialTheme.typography.bodySmall,
        )
        if (p.bins.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FilterChip(selected = bin == null, onClick = { bin = null }, label = { Text(tr("Alle")) })
            FilterChip(selected = bin == "", onClick = { bin = "" }, label = { Text(tr("Ohne Ordner")) })
            p.bins.forEach { b -> FilterChip(selected = bin == b, onClick = { bin = b }, label = { Text(b) }) }
        }
        val shown = p.media.filter { (bin == null || it.bin == bin) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.bin.contains(query.trim(), ignoreCase = true)) }
        if (p.media.isEmpty()) HintText(tr("Hier liegen die Dateien des Projekts. „+“ holt neue dazu; ziehen legt sie in die Zeitleiste, ein Klick zeigt sie im Quellmonitor."))
        else if (shown.isEmpty()) HintText(tr("Nichts gefunden."))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(shown, key = { it.id }) { item -> PoolRow(h, item, item.id == open, drag, onOpen, onUse) }
        }
    }
    if (newBin) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newBin = false },
            title = { Text(tr("Neuer Ordner")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name")) }, modifier = Modifier.typing()) },
            confirmButton = {
                Button(enabled = name.isNotBlank() && name.trim() !in p.bins, onClick = {
                    h.edit { it.copy(bins = it.bins + name.trim()) }
                    bin = name.trim(); newBin = false
                }) { Text(tr("Anlegen")) }
            },
            dismissButton = { TextButton(onClick = { newBin = false }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun PoolRow(h: History, item: PoolItem, open: Boolean, drag: PoolDrag, onOpen: (PoolItem) -> Unit, onUse: (PoolItem, PoolUse) -> Unit) {
    val file = File(item.path)
    val thumb by produceState<ImageBitmap?>(null, item.path) { value = withContext(Dispatchers.IO) { runCatching { Media.thumbnail(file, 0.5) }.getOrNull() } }
    val info by produceState<io.github.thepytales.llmhub.video.MediaInfo?>(null, item.path) { value = withContext(Dispatchers.IO) { Media.probe(file) } }
    var menu by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (open) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { off -> drag.item = item; drag.at = origin + off },
                    onDrag = { ch, amount -> ch.consume(); drag.at += amount },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.item = null },
                )
            }
            .clickable { onOpen(item) }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(64.dp).height(36.dp).background(Color.Black, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            thumb?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                ?: Icon(if (info?.hasVideo == false) Icons.Outlined.MusicNote else Icons.Outlined.Movie, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(6.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = listOfNotNull(
                info?.duration?.takeIf { it > 0.05 && !Media.isStill(file, info) }?.let { clock(it) },
                if (Media.isStill(file, info)) tr("Bild") else null,
                item.bin.ifBlank { null },
                if (!file.isFile) tr("fehlt") else null,
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.MoreVert, contentDescription = tr("Mehr"), modifier = Modifier.size(16.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Einfügen am Abspielkopf (F9)")) }, onClick = { menu = false; onUse(item, PoolUse.INSERT) })
                DropdownMenuItem(text = { Text(tr("Überschreiben am Abspielkopf (F10)")) }, onClick = { menu = false; onUse(item, PoolUse.OVERWRITE) })
                DropdownMenuItem(text = { Text(tr("Als Bild-in-Bild (F12)")) }, onClick = { menu = false; onUse(item, PoolUse.PIP) })
                DropdownMenuItem(text = { Text(tr("Als Ton auf eine Tonspur")) }, onClick = { menu = false; onUse(item, PoolUse.SOUND) })
                HorizontalDivider()
                (listOf("") + h.project.bins).filter { it != item.bin }.forEach { b ->
                    DropdownMenuItem(text = { Text(if (b.isEmpty()) tr("Aus dem Ordner nehmen") else tr("In Ordner „{0}“", b)) }, onClick = {
                        menu = false
                        h.edit { pp -> pp.copy(media = pp.media.map { if (it.id == item.id) it.copy(bin = b) else it }) }
                    })
                }
                DropdownMenuItem(text = { Text(tr("Aus dem Medienpool entfernen")) }, onClick = {
                    menu = false
                    h.edit { pp -> pp.copy(media = pp.media.filter { it.id != item.id }) }
                })
            }
        }
    }
}

/**
 * Position, size, crop and opacity of a picture (AP-52): corners for picture-in-picture, then the fine settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FrameEditor(frame: Frame, key: String, onChange: (String, Frame) -> Unit) {
    Text(tr("Position und Größe"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Frame.CORNERS.forEach { (k, l) ->
            val target = Frame.corner(k, if (k == "voll") 1f else if (frame.scale < 0.95f) frame.scale else 0.3f).copy(opacity = frame.opacity, cropL = frame.cropL, cropR = frame.cropR, cropT = frame.cropT, cropB = frame.cropB)
            FilterChip(selected = frame.x == target.x && frame.y == target.y && frame.scale == target.scale, onClick = { onChange("$key-ecke", target) }, label = { Text(tr(l)) })
        }
    }
    @Composable
    fun slider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, part: String, f: (Float) -> Frame) {
        Text("$label ${(value * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = { onChange("$key-$part", f(it)) }, valueRange = range, modifier = Modifier.height(28.dp))
    }
    slider(tr("Größe"), frame.scale, 0.1f..1f, "s") { frame.copy(scale = it) }
    slider(tr("Waagrecht"), frame.x, 0f..1f, "x") { frame.copy(x = it) }
    slider(tr("Senkrecht"), frame.y, 0f..1f, "y") { frame.copy(y = it) }
    slider(tr("Deckkraft"), frame.opacity, 0f..1f, "o") { frame.copy(opacity = it) }
    Text(tr("Zuschneiden"), style = MaterialTheme.typography.labelLarge)
    slider(tr("Links"), frame.cropL, 0f..0.45f, "cl") { frame.copy(cropL = it) }
    slider(tr("Rechts"), frame.cropR, 0f..0.45f, "cr") { frame.copy(cropR = it) }
    slider(tr("Oben"), frame.cropT, 0f..0.45f, "ct") { frame.copy(cropT = it) }
    slider(tr("Unten"), frame.cropB, 0f..0.45f, "cb") { frame.copy(cropB = it) }
    if (frame != Frame() && frame != Frame.corner("unten-rechts")) TextButton(onClick = { onChange("$key-reset", Frame()) }) { Text(tr("Ganzes Bild")) }
}

/** Properties of a picture on V2 … (AP-52). */
@Composable
internal fun LayerProperties(h: History, id: String, pos: Double, onDelete: () -> Unit, onGone: () -> Unit) {
    val l = h.project.layers.firstOrNull { it.id == id } ?: run { onGone(); return }
    val srcLen = sourceLength(l.path)
    fun change(key: String, f: (io.github.thepytales.llmhub.video.LayerClip) -> io.github.thepytales.llmhub.video.LayerClip) =
        h.edit(key) { pp -> pp.copy(layers = pp.layers.map { if (it.id == l.id) f(it) else it }) }
    Text(tr("Bild-in-Bild auf V{0}", l.track) + ": " + l.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SecondsField(tr("Beginn (s)"), l.startSec, "lst-${l.id}", Modifier.weight(1f)) { v -> change("lst-${l.id}") { it.copy(startSec = v.coerceAtLeast(0.0)) } }
        SecondsField(tr("Länge (s)"), l.length, "lln-${l.id}", Modifier.weight(1f)) { v -> change("lln-${l.id}") { it.copy(outSec = (it.inSec + v.coerceAtLeast(0.2)).let { o -> srcLen?.let { m -> minOf(o, maxOf(m, it.inSec + 0.2)) } ?: o }) } }
    }
    SecondsField(tr("Anfang in der Datei (s)"), l.inSec, "lin-${l.id}") { v -> change("lin-${l.id}") { it.copy(inSec = v.coerceIn(0.0, it.outSec - 0.2)) } }
    OutlinedButton(onClick = { change("lpos-${l.id}") { it.copy(startSec = pos) } }) { Text(tr("An den Abspielkopf")) }
    FrameEditor(l.frame, "lf-${l.id}") { k, f -> change(k) { it.copy(frame = f) } }
    SecondsField(tr("Ein- und Ausblenden (s)"), l.fadeSec, "lfd-${l.id}") { v -> change("lfd-${l.id}") { it.copy(fadeSec = v.coerceIn(0.0, 5.0)) } }
    Text(tr("Lautstärke") + " ${(l.volume * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
    Slider(value = l.volume, onValueChange = { v -> change("lvol-${l.id}") { it.copy(volume = v) } }, valueRange = 0f..2f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Ton aus"), modifier = Modifier.weight(1f))
        Switch(l.muted, { m -> change("lm-${l.id}") { it.copy(muted = m) } })
    }
    OutlinedButton(onClick = onDelete) { Text(tr("Löschen")) }
}

/** Properties of a marker (AP-53). */
@Composable
internal fun MarkerProperties(h: History, id: String, onSeek: (Double) -> Unit, onDelete: () -> Unit, onGone: () -> Unit) {
    val m = h.project.markers.firstOrNull { it.id == id } ?: run { onGone(); return }
    fun change(key: String?, f: (io.github.thepytales.llmhub.video.Marker) -> io.github.thepytales.llmhub.video.Marker) =
        h.edit(key) { pp -> pp.copy(markers = pp.markers.map { if (it.id == m.id) f(it) else it }.sortedBy { it.sec }) }
    Text(tr("Marker bei {0}", clock(m.sec, true)), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(m.note, { t -> change("mn-${m.id}") { it.copy(note = t) } }, label = { Text(tr("Notiz")) }, modifier = Modifier.fillMaxWidth().typing(), maxLines = 4)
    SecondsField(tr("Zeit (s)"), m.sec, "ms-${m.id}") { v -> change("ms-${m.id}") { it.copy(sec = v.coerceAtLeast(0.0)) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Als Kapitel"))
            HintText(tr("Kapitel stehen im MP4 und als Liste für YouTube in den Projekteinstellungen."))
        }
        Switch(m.chapter, { v -> change(null) { it.copy(chapter = v) } })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onSeek(m.sec) }) { Text(tr("Hinspringen")) }
        OutlinedButton(onClick = onDelete) { Text(tr("Löschen")) }
    }
}

/** The keys of the video editor, close to DaVinci Resolve (AP-53). */
internal val VIDEO_SHORTCUTS: List<Pair<String, String>> = listOf(
    trLater("Leertaste") to trLater("Abspielen / anhalten (mit Schleife: zwischen In und Out)"),
    trLater("J · K · L") to trLater("Rückwärts · Halt · Vorwärts (mehrmals: schneller)"),
    trLater("← · →") to trLater("Ein Bild zurück · vor"),
    trLater("↑ · ↓") to trLater("Zum vorigen · nächsten Schnitt"),
    trLater("Pos1 · Ende") to trLater("Zum Anfang · Ende"),
    trLater("I · O") to trLater("In- · Out-Punkt setzen (Quelle oder Zeitleiste)"),
    trLater("Alt+X") to trLater("In und Out löschen"),
    trLater("Q") to trLater("Quellmonitor ⇄ Zeitleiste"),
    trLater("F9 · F10") to trLater("Aus der Quelle einfügen · überschreiben"),
    trLater("F12") to trLater("Aus der Quelle als Bild-in-Bild"),
    trLater("A · B · T") to trLater("Auswahl · Klinge · Trimmen"),
    trLater("S · Strg+B") to trLater("Am Abspielkopf teilen"),
    trLater("N") to trLater("Magnet ein/aus"),
    trLater("M") to trLater("Marker setzen"),
    trLater("Entf") to trLater("Löschen (mit Magnet: Lücke schließen)"),
    trLater("Umschalt+Entf") to trLater("Löschen und Lücke immer schließen"),
    trLater("Strg+Alt+L") to trLater("Bild und Ton verknüpfen / lösen"),
    trLater("Strg+Z · Strg+Y") to trLater("Rückgängig · Wiederholen"),
    trLater("?") to trLater("Diese Übersicht"),
)

@Composable
internal fun VideoShortcutsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Tastenkürzel im Videoeditor")) },
        text = {
            Column(Modifier.width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VIDEO_SHORTCUTS.forEach { (k, what) ->
                    Row {
                        Text(tr(k), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(150.dp))
                        Text(tr(what), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
                HintText(tr("Nah an DaVinci Resolve, damit Umsteigen leichtfällt."))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** Chapters for YouTube from the markers, to copy (AP-53). */
@Composable
internal fun ChapterList(h: History) {
    val text = VideoEdit.youtubeChapters(h.project)
    if (text.isBlank()) return
    Text(tr("Kapitel"), style = MaterialTheme.typography.labelLarge)
    Text(text, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
        Notices.info(tr("Kapitel kopiert – in die Beschreibung des YouTube-Videos einfügen."))
    }) { Text(tr("Für YouTube kopieren")) }
}
