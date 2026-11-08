package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.GlobalHotkeys
import io.github.thepytales.llmhub.transcribe.StartOptions
import io.github.thepytales.llmhub.video.ScreenRecorder
import io.github.thepytales.llmhub.video.VideoLora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.io.File

/** The monitors: name and bounds (for „Bildschirm aufnehmen“). */
internal fun screens(): List<Pair<String, ScreenRecorder.Area>> = runCatching {
    if (GraphicsEnvironment.isHeadless()) emptyList()
    else GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.mapIndexed { i, d ->
        val b = d.defaultConfiguration.bounds
        val t = d.defaultConfiguration.defaultTransform
        // Windows scales the desktop; gdigrab counts real pixels.
        val area = ScreenRecorder.Area((b.x * t.scaleX).toInt(), (b.y * t.scaleY).toInt(), (b.width * t.scaleX).toInt(), (b.height * t.scaleY).toInt())
        tr("Bildschirm {0}", i + 1) + " (${area.w} × ${area.h})" to area
    }
}.getOrDefault(emptyList())

/** Folder of new recordings: „Aufnahmen“ in the Lora-Ordner. */
internal fun recordingFolder(c: AppContainer): File = ScreenRecorder.folder(runCatching { c.drive.root }.getOrElse { c.appContext.filesDir })

/**
 * Bildschirmaufnahme (AP-56): what to record (screen, window, area), webcam, microphone and system sound, highlighted
 * clicks and an immediate transcription. „Aufnehmen“ counts down and starts; the recording bar takes over.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenRecordDialog(c: AppContainer, onDismiss: () -> Unit) {
    val prefs = remember { c.appContext.getSharedPreferences("ui_state", 0) }
    val devices by produceState<ScreenRecorder.Devices?>(null) { value = ScreenRecorder.devices() }
    val monitors = remember { screens() }
    val windows = remember { GlobalHotkeys.windowTitles() }
    var source by remember { mutableStateOf(ScreenRecorder.Source.SCREEN) }
    var monitor by remember { mutableIntStateOf(0) }
    var window by remember { mutableStateOf<String?>(null) }
    var area by remember { mutableStateOf<ScreenRecorder.Area?>(null) }
    var webcam by remember { mutableStateOf(prefs.getString("recordWebcam", null)) }
    var mic by remember { mutableStateOf(prefs.getString("recordMic", null)) }
    var system by remember { mutableStateOf(prefs.getString("recordSystem", null)) }
    var clicks by remember { mutableStateOf(prefs.getBoolean("recordClicks", true)) }
    var transcribe by remember { mutableStateOf(prefs.getBoolean("recordTranscribe", false)) }
    var countdown by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    // Devices that are gone (a webcam unplugged) are not offered.
    LaunchedEffect(devices) {
        val d = devices ?: return@LaunchedEffect
        if (webcam != null && webcam !in d.video) webcam = null
        if (mic != null && mic !in d.audio) mic = d.microphones.firstOrNull()
        if (mic == null && !prefs.contains("recordMic")) mic = d.microphones.firstOrNull()
        if (system != null && system !in d.audio) system = null
    }
    fun setup() = ScreenRecorder.Setup(
        source = source, screen = monitors.getOrNull(monitor)?.second, window = window, area = area,
        webcam = webcam, mic = mic, systemAudio = system, clicks = clicks, transcribe = transcribe,
    )
    AlertDialog(
        onDismissRequest = { if (countdown < 0) onDismiss() },
        title = { Text(tr("Bildschirm aufnehmen")) },
        text = {
            if (countdown > 0) {
                Box(Modifier.width(520.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(countdown.toString(), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = LocalChatLook.current.brand)
                }
                return@AlertDialog
            }
            Column(Modifier.width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HintText(tr("Nach dem Stopp liegt die Aufnahme als Projekt im Videoeditor – Bildschirm, Webcam und Ton auf getrennten Spuren. Pause und Stopp auch über Strg+Umschalt+F9 / F10 und den Infobereich."))
                Text(tr("Was aufnehmen?"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(source == ScreenRecorder.Source.SCREEN, { source = ScreenRecorder.Source.SCREEN }, label = { Text(tr("Ganzer Bildschirm")) })
                    FilterChip(source == ScreenRecorder.Source.WINDOW, { source = ScreenRecorder.Source.WINDOW }, label = { Text(tr("Fenster")) }, enabled = windows.isNotEmpty())
                    FilterChip(source == ScreenRecorder.Source.AREA, { source = ScreenRecorder.Source.AREA }, label = { Text(tr("Bereich")) })
                }
                when (source) {
                    ScreenRecorder.Source.SCREEN -> if (monitors.size > 1) Choice(tr("Bildschirm"), monitors.getOrNull(monitor)?.first, monitors.map { it.first }) { monitor = monitors.indexOfFirst { m -> m.first == it }.coerceAtLeast(0) }
                    ScreenRecorder.Source.WINDOW -> Choice(tr("Fenster"), window, windows) { window = it }
                    ScreenRecorder.Source.AREA -> Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { AreaPicker.pick { area = it } }) { Text(tr("Bereich ziehen …")) }
                        Spacer(Modifier.size(8.dp))
                        Text(area?.let { "${it.w} × ${it.h} " + tr("bei") + " ${it.x}, ${it.y}" } ?: tr("noch kein Bereich"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val d = devices
                if (d == null) HintText(tr("Suche Kameras und Mikrofone …"))
                else {
                    Choice(tr("Webcam (Bild-in-Bild)"), webcam ?: tr("keine"), listOf(tr("keine")) + d.video) { webcam = it.takeIf { v -> v in d.video } }
                    Choice(tr("Mikrofon"), mic ?: tr("keins"), listOf(tr("keins")) + d.microphones) { mic = it.takeIf { v -> v in d.audio } }
                    Choice(tr("Systemton"), system ?: tr("aus"), listOf(tr("aus")) + d.loopback) { system = it.takeIf { v -> v in d.audio } }
                    if (d.loopback.isEmpty()) HintText(tr("Für den Systemton braucht Windows ein Aufnahmegerät wie „Stereomix“ (Sound › Aufnahme › deaktivierte Geräte anzeigen)."))
                }
                SwitchRow(tr("Klicks hervorheben"), clicks) { clicks = it }
                SwitchRow(tr("Nach dem Stopp sofort transkribieren"), transcribe, enabled = mic != null) { transcribe = it }
            }
        },
        confirmButton = {
            Button(
                enabled = countdown < 0 && (source != ScreenRecorder.Source.WINDOW || window != null) && (source != ScreenRecorder.Source.AREA || area != null),
                onClick = {
                    prefs.edit().putString("recordWebcam", webcam).putString("recordMic", mic).putString("recordSystem", system)
                        .putBoolean("recordClicks", clicks).putBoolean("recordTranscribe", transcribe).apply()
                    scope.launch {
                        for (n in 3 downTo 1) { countdown = n; delay(1000) }
                        runCatching { ScreenRecorder.start(setup(), recordingFolder(c)) }
                            .onSuccess { onDismiss() }
                            .onFailure { countdown = -1; Notices.error(it.message ?: it.javaClass.simpleName) }
                    }
                },
            ) { Icon(Icons.Outlined.FiberManualRecord, null, Modifier.size(16.dp), tint = Color(0xFFE53935)); Spacer(Modifier.size(6.dp)); Text(tr("Aufnehmen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = countdown < 0) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun Choice(label: String, value: String?, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(170.dp))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(value ?: tr("wählen …"), maxLines = 1) }
            DropdownMenu(open, { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(on, onChange, enabled = enabled)
    }
}

/**
 * Turns a finished recording into a project and opens it in the video editor; with „sofort transkribieren“ the
 * microphone of every part goes to the transcription and the editor assigns the transcripts when they are done.
 */
internal suspend fun finishRecording(c: AppContainer, r: ScreenRecorder.Recording) {
    val parts = r.stop()
    if (parts.isEmpty()) { Notices.error(tr("Die Aufnahme ist leer – ffmpeg hat kein Bild geschrieben (Protokoll im Aufnahmeordner).")); return }
    val title = ScreenRecorder.title()
    val result = runCatching {
        withContext(Dispatchers.IO) {
            val ring = if (r.setup.clicks && r.clicks.isNotEmpty()) ScreenRecorder.ring(File(r.dir, "klick.png")) else null
            ScreenRecorder.project(title, parts, synchronized(r.clicks) { r.clicks.toList() }, ring, fps = r.setup.fps)
        }
    }
    val p = result.getOrElse { Notices.error(it.message ?: it.javaClass.simpleName); return }
    val pending = mutableMapOf<String, String>()
    if (r.setup.transcribe) parts.forEachIndexed { k, part ->
        val mic = part.mic?.takeIf { it.isFile } ?: return@forEachIndexed
        val name = if (parts.size > 1) "$title – " + tr("Teil {0}", k + 1) else title
        runCatching {
            c.transcription.import("$name.wav", mic.length(), withContext(Dispatchers.IO) { mic.inputStream() })
            val token = c.transcription.incoming.value.lastOrNull { it.name == "$name.wav" }?.token ?: error("nicht angeboten")
            c.transcription.start(token, StartOptions(title = name))
            pending[part.screen.absolutePath] = name
        }.onFailure { Notices.error(tr("Transkription nicht gestartet: {0}", it.message ?: "")) }
    }
    val saved = VideoHub.store.save(p.copy(pendingTranscripts = pending))
    VideoLora.lastId = saved.id
    VideoLora.openRequest.value = saved.id
    Navigation.openRoute.value = "video"
    Notices.info(tr("Aufnahme fertig ({0}) – als Projekt im Videoeditor.", clock(saved.duration)) + if (pending.isNotEmpty()) " " + tr("Das Transkript kommt, sobald es fertig ist.") else "")
}

/**
 * The recording bar (AP-56): a small window at the top of the screen while a recording runs – time, Pause/Weiter and
 * Stopp – kept out of the recording where Windows allows it. Also wires the hotkeys in all of Windows.
 */
@Composable
fun RecordingBar(c: AppContainer, onShowApp: () -> Unit) {
    val rec by ScreenRecorder.active.collectAsState()
    val r = rec ?: return
    val state by r.state.collectAsState()
    val scope = rememberCoroutineScope()
    var secs by remember { mutableStateOf(0.0) }
    LaunchedEffect(r) { while (true) { secs = r.seconds; delay(250) } }
    fun stop() = scope.launch { finishRecording(c, r); onShowApp() }
    androidx.compose.runtime.DisposableEffect(r) {
        GlobalHotkeys.register { k -> if (k == GlobalHotkeys.PAUSE) r.toggle() else if (k == GlobalHotkeys.STOP) stop() }
        onDispose { GlobalHotkeys.unregister() }
    }
    val bounds = remember { runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull() }
    val state2 = rememberWindowState(size = DpSize(330.dp, 52.dp), position = WindowPosition(((bounds?.let { it.x + it.width / 2 } ?: 800) - 165).dp, ((bounds?.y ?: 0) + 8).dp))
    Window(onCloseRequest = { stop() }, state = state2, title = tr("Aufnahme"), undecorated = true, resizable = false, alwaysOnTop = true, focusable = false) {
        LaunchedEffect(Unit) { GlobalHotkeys.excludeFromCapture(window) }
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FiberManualRecord, null, tint = if (state == ScreenRecorder.State.RECORDING) Color(0xFFE53935) else Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(clock(secs), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(6.dp))
                Text(if (state == ScreenRecorder.State.PAUSED) tr("Pause") else tr("Aufnahme"), color = Color(0xFFBDBDBD), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { r.toggle() }) {
                    Icon(if (state == ScreenRecorder.State.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = tr(if (state == ScreenRecorder.State.PAUSED) "Weiter" else "Pause") + " (Strg+Umschalt+F9)", tint = Color.White)
                }
                IconButton(onClick = { stop() }) { Icon(Icons.Outlined.Stop, contentDescription = tr("Stopp") + " (Strg+Umschalt+F10)", tint = Color(0xFFFF8A80)) }
            }
        }
    }
}

/** „Aufnehmen“ in the Referentenansicht (AP-51/56): records the slides' screen with the microphone, as a project. */
@Composable
fun PresentationRecordButton(second: Boolean) {
    val c = io.github.thepytales.llmhub.App.container
    val rec by ScreenRecorder.active.collectAsState()
    val scope = rememberCoroutineScope()
    val r = rec
    IconButton(onClick = {
        if (r != null) scope.launch { finishRecording(c, r) }
        else scope.launch {
            val mic = ScreenRecorder.devices().microphones.firstOrNull()
            val target = if (second) PresentScreens.second() else null
            val area = target?.let { b -> screens().firstOrNull { (_, a) -> a.w > 0 && kotlin.math.abs(a.x - b.x) < b.width / 2 && kotlin.math.abs(a.y - b.y) < b.height / 2 }?.second }
            runCatching { ScreenRecorder.start(ScreenRecorder.Setup(source = ScreenRecorder.Source.SCREEN, screen = area ?: screens().firstOrNull()?.second, mic = mic, clicks = false), recordingFolder(c)) }
                .onSuccess { Notices.info(tr("Die Präsentation wird aufgenommen – noch einmal tippen (oder Strg+Umschalt+F10) beendet.")) }
                .onFailure { Notices.error(it.message ?: it.javaClass.simpleName) }
        }
    }) {
        Icon(Icons.Outlined.FiberManualRecord, contentDescription = if (r != null) tr("Aufnahme beenden") else tr("Präsentation aufnehmen"), tint = if (r != null) Color(0xFFFF4040) else Color.White)
    }
}

/** A translucent window over all screens: drag a rectangle, Esc cancels (AP-56, „Bereich“). */
internal object AreaPicker {
    fun pick(onArea: (ScreenRecorder.Area) -> Unit) {
        if (GraphicsEnvironment.isHeadless()) return
        javax.swing.SwingUtilities.invokeLater {
            val all = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }.reduce { a, b -> a.union(b) }
            val w = javax.swing.JWindow()
            var start: java.awt.Point? = null
            var end: java.awt.Point? = null
            val panel = object : javax.swing.JComponent() {
                override fun paintComponent(g: java.awt.Graphics) {
                    val g2 = g as java.awt.Graphics2D
                    g2.color = java.awt.Color(0, 0, 0, 90)
                    g2.fillRect(0, 0, width, height)
                    val s = start; val e = end
                    if (s != null && e != null) {
                        val r = java.awt.Rectangle(minOf(s.x, e.x), minOf(s.y, e.y), kotlin.math.abs(s.x - e.x), kotlin.math.abs(s.y - e.y))
                        g2.composite = java.awt.AlphaComposite.Clear
                        g2.fillRect(r.x, r.y, r.width, r.height)
                        g2.composite = java.awt.AlphaComposite.SrcOver
                        g2.color = java.awt.Color(0xF5, 0xA2, 0x00)
                        g2.stroke = java.awt.BasicStroke(2f)
                        g2.drawRect(r.x, r.y, r.width, r.height)
                    }
                    g2.color = java.awt.Color.WHITE
                    g2.drawString(tr("Bereich ziehen · Esc bricht ab"), 20, 30)
                }
            }
            val mouse = object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) { start = e.point; end = e.point; panel.repaint() }
                override fun mouseDragged(e: java.awt.event.MouseEvent) { end = e.point; panel.repaint() }
                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    val s = start ?: return
                    w.dispose()
                    val scale = w.graphicsConfiguration?.defaultTransform
                    val sx = scale?.scaleX ?: 1.0
                    val sy = scale?.scaleY ?: 1.0
                    val x = (all.x + minOf(s.x, e.x)) * sx
                    val y = (all.y + minOf(s.y, e.y)) * sy
                    val ww = kotlin.math.abs(s.x - e.x) * sx
                    val hh = kotlin.math.abs(s.y - e.y) * sy
                    if (ww >= 32 && hh >= 32) onArea(ScreenRecorder.Area(x.toInt(), y.toInt(), ww.toInt(), hh.toInt()).even())
                }
            }
            panel.addMouseListener(mouse)
            panel.addMouseMotionListener(mouse)
            panel.isFocusable = true
            panel.addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) { if (e.keyCode == java.awt.event.KeyEvent.VK_ESCAPE) w.dispose() }
            })
            w.contentPane = panel
            w.background = java.awt.Color(0, 0, 0, 0)
            w.bounds = all
            w.isAlwaysOnTop = true
            w.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR)
            w.isVisible = true
            panel.requestFocusInWindow()
        }
    }
}
