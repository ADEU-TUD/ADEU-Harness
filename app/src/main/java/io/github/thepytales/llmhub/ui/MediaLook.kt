package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Vorschau für Video und Ton in der Schnellansicht (AP-82): what plays a file – the same controls on both platforms
 * (Start/Pause, ±10 s, Springen mit dem Regler, Dauer); the picture of a video comes from the platform ([mediaControl],
 * [MediaPicture] in ui/MediaPreview.kt).
 */
interface MediaControl {
    val position: StateFlow<Double>
    val playing: StateFlow<Boolean>
    /** Length in seconds, null while unknown. */
    val duration: StateFlow<Double?>
    val error: StateFlow<String?>
    fun toggle()
    fun seek(seconds: Double)
    fun release()
}

/** Video or sound by the file name (both are in [io.github.thepytales.llmhub.files.TextFormats.MEDIA]). */
object MediaFiles {
    val VIDEO = setOf("mp4", "m4v", "mov", "webm", "mkv", "avi", "wmv", "mpeg", "mpg")
    fun isMedia(f: File) = f.extension.lowercase() in io.github.thepytales.llmhub.files.TextFormats.MEDIA
    fun isVideo(f: File) = f.extension.lowercase() in VIDEO

    /** „1:05:03“, „3:07“. */
    fun clock(seconds: Double): String {
        val s = seconds.coerceAtLeast(0.0).toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}

/** The player of the Schnellansicht: starts by itself, K or the button pauses, J/L jump 10 s back and forth. */
@Composable
internal fun MediaLook(c: AppContainer, file: File, keys: MediaKeys) {
    val video = MediaFiles.isVideo(file)
    val control = remember(file) { mediaControl(c, file, video) }
    DisposableEffect(control) { onDispose { control.release() } }
    val position by control.position.collectAsState()
    val playing by control.playing.collectAsState()
    val duration by control.duration.collectAsState()
    val error by control.error.collectAsState()
    var dragging by remember(file) { mutableStateOf<Float?>(null) }
    LaunchedEffect(control) { if (!control.playing.value) control.toggle() }
    keys.toggle = control::toggle
    keys.jump = { d -> control.seek((control.position.value + d).coerceIn(0.0, control.duration.value ?: Double.MAX_VALUE)) }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (video) MediaPicture(control, Modifier.fillMaxSize())
            else Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val total = duration
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Slider(
                value = dragging ?: if (total != null && total > 0) (position / total).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { v -> total?.let { control.seek(v * it) } }; dragging = null },
                enabled = total != null && total > 0,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(MediaFiles.clock(dragging?.let { v -> (total ?: 0.0) * v } ?: position) + " / " + (total?.let { MediaFiles.clock(it) } ?: "–:––"), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { keys.jump(-10.0) }) { Icon(Icons.Outlined.Replay10, contentDescription = tr("10 Sekunden zurück")) }
                    FilledIconButton(onClick = control::toggle) {
                        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) tr("Pause") else tr("Abspielen"))
                    }
                    IconButton(onClick = { keys.jump(10.0) }) { Icon(Icons.Outlined.Forward10, contentDescription = tr("10 Sekunden vor")) }
                }
                HintText(tr("K: Start/Pause · J/L: 10 s"))
            }
        }
    }
}

/** The keys of the Schnellansicht reach the player through this (set while a video or sound is shown). */
internal class MediaKeys {
    var toggle: () -> Unit = {}
    var jump: (Double) -> Unit = {}
}
