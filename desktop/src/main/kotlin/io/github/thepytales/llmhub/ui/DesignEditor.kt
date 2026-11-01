package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.DesignTools
import io.github.thepytales.llmhub.creative.DesignEditorState
import io.github.thepytales.llmhub.creative.DesignExport
import io.github.thepytales.llmhub.creative.DesignGeometry
import io.github.thepytales.llmhub.creative.DesignIcons
import io.github.thepytales.llmhub.creative.DesignImages
import io.github.thepytales.llmhub.creative.DesignText
import io.github.thepytales.llmhub.creative.Handle
import io.github.thepytales.llmhub.creative.designColor
import io.github.thepytales.llmhub.creative.drawDesignPage
import io.github.thepytales.llmhub.creative.renderDesignPage
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignBrand
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.TudBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val EditorAccent = Color(0xFF2F57B2)

/** The editor of the Kreativbereich (AP-24) for design [id]. Changes are saved after a short pause and when leaving. */
@Composable
fun DesignEditor(c: AppContainer, id: String, onClose: () -> Unit) {
    val initial = remember(id) { c.designs.get(id) }
    if (initial == null) {
        LaunchedEffect(id) { onClose() }
        return
    }
    val state = remember(id) { DesignEditorState(initial) }
    var savedVersion by remember { mutableStateOf(0) }
    var savedAt by remember { mutableStateOf(initial.updatedAt) }
    LaunchedEffect(state.version) {
        if (state.version == savedVersion) return@LaunchedEffect
        delay(600)
        savedAt = withContext(Dispatchers.IO) { c.designs.save(state.design).updatedAt }
        savedVersion = state.version
    }
    DisposableEffect(id) {
        onDispose { if (state.version != savedVersion) c.designs.save(state.design) }
    }
    // The AI changed the design while it is open: take its version unless there are unsaved own changes.
    val stored by c.designs.items.collectAsStateWithLifecycle()
    LaunchedEffect(stored) {
        val s = stored.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (s.updatedAt > savedAt + 50 && state.version == savedVersion && s != state.design) {
            state.replace(s)
            savedAt = s.updatedAt
            Notices.info(tr("Das Design wurde im Chat geändert – die neue Fassung ist geladen."))
        }
    }
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorTopBar(c, state, onClose)
        HorizontalDivider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            ToolRail(c, state)
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) { DesignCanvas(state, focus) }
            VerticalDivider()
            Box(Modifier.width(300.dp).fillMaxHeight()) { PropertiesPanel(c, state) }
        }
        HorizontalDivider()
        PageStrip(state)
    }
    LaunchedEffect(id) { runCatching { focus.requestFocus() } }
}

// ── Top bar ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(c: AppContainer, state: DesignEditorState, onClose: () -> Unit) {
    val d = state.design
    var title by remember(d.id) { mutableStateOf(d.title) }
    var exportMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun export(label: String, ext: String, write: (File) -> File?) {
        exportMenu = false
        scope.launch {
            val target = withContext(Dispatchers.IO) { DesktopActions.pickSaveFile(tr("Speichern unter"), c.designs.fileName(state.design, ext), ext) } ?: return@launch
            busy = label
            val result = withContext(Dispatchers.IO) { runCatching { write(target) } }
            busy = null
            result.onSuccess { f -> if (f != null) Notices.show(Notices.Notice(tr("Gespeichert:") + " " + f.absolutePath, action = tr("Öffnen"), onAction = { DesktopActions.open(f) })) }
                .onFailure { Notices.error(tr("Export fehlgeschlagen:") + " " + (it.message ?: it.javaClass.simpleName)) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück")) }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; if (it.isNotBlank()) state.edit("title") { x -> x.copy(title = it.trim()) } },
            singleLine = true,
            modifier = Modifier.width(320.dp),
            textStyle = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            listOfNotNull(DesignFormat.byId(d.format)?.let { "${it.label} · ${it.sizeLabel}" }).joinToString(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = state::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = tr("Rückgängig") + " (Strg+Z)") }
        IconButton(onClick = state::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = tr("Wiederholen") + " (Strg+Y)") }
        IconButton(onClick = { state.zoom = (state.zoom / 1.25f).coerceAtLeast(0.25f) }) { Icon(Icons.Outlined.ZoomOut, contentDescription = tr("Verkleinern")) }
        TextButton(onClick = { state.zoom = 1f; state.pan = Offset.Zero }) { Text("${(state.zoom * 100).toInt()} %") }
        IconButton(onClick = { state.zoom = (state.zoom * 1.25f).coerceAtMost(8f) }) { Icon(Icons.Outlined.ZoomIn, contentDescription = tr("Vergrößern")) }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = {
            ChatHandoff.pending.value = ChatHandoff.Content(
                tr("Ändere das Design „${d.title}“ (${DesignTools.ID_LABEL} ${d.id}): "), emptyList(),
                mode = ChatModes.DESIGN.id.takeIf { d.chatId == null || c.chats.conversations.value.none { it.id == d.chatId } },
                chatId = d.chatId?.takeIf { cid -> c.chats.conversations.value.any { it.id == cid } },
            )
            ChatHandoff.open.value = d.chatId?.takeIf { cid -> c.chats.conversations.value.any { it.id == cid } }.orEmpty()
        }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Mit Lora ändern")) }
        Spacer(Modifier.size(8.dp))
        Box {
            Button(onClick = { exportMenu = true }, enabled = busy == null) {
                if (busy != null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(busy ?: tr("Exportieren"))
            }
            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                val many = d.pages.size > 1
                DropdownMenuItem(
                    text = { Text(if (d.screen) "PNG (${DesignFormat.byId(d.format)?.sizeLabel})" else "PNG (150 dpi)") },
                    onClick = { export("PNG …", if (many) "zip" else "png") { f -> DesignExport.writePng(state.design, f, DesignExport.pngScale(state.design)) } },
                )
                DropdownMenuItem(
                    text = { Text(if (d.screen) tr("PNG doppelte Auflösung") else "PNG (300 dpi)") },
                    onClick = { export("PNG …", if (many) "zip" else "png") { f -> DesignExport.writePng(state.design, f, DesignExport.pngScale(state.design, dpi = 300, factor = 2)) } },
                )
                DropdownMenuItem(text = { Text(tr("PDF (Druck)")) }, onClick = { export("PDF …", "pdf") { f -> DesignExport.writePdf(state.design, f); f } })
                DropdownMenuItem(text = { Text(tr("PDF für die Druckerei (3 mm Beschnitt, Schnittmarken)")) }, onClick = { export("PDF …", "pdf") { f -> DesignExport.writePdf(state.design, f, bleed = true); f } })
                DropdownMenuItem(text = { Text(tr("PowerPoint (bearbeitbar)")) }, onClick = { export("PowerPoint …", "pptx") { f -> DesignExport.writePptx(state.design, f); f } })
            }
        }
    }
}

// ── Tools on the left ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolRail(c: AppContainer, state: DesignEditorState) {
    var shapes by remember { mutableStateOf(false) }
    var texts by remember { mutableStateOf(false) }
    var logos by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    var generate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val d = state.design
    val unit = min(d.width, d.height)
    fun pickImage() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Bild auswählen"), multiple = false, extensions = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")).firstOrNull() } ?: return@launch
        val bytes = withContext(Dispatchers.IO) { runCatching { f.readBytes() }.getOrNull() } ?: return@launch
        addPicture(c, state, bytes, f.nameWithoutExtension)
    }
    Column(
        Modifier.width(84.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            RailButton(Icons.Outlined.TextFields, tr("Text")) { texts = true }
            DropdownMenu(expanded = texts, onDismissRequest = { texts = false }) {
                listOf(
                    Triple(tr("Überschrift"), 0.055f, true),
                    Triple(tr("Zwischenüberschrift"), 0.035f, true),
                    Triple(tr("Fließtext"), 0.02f, false),
                ).forEach { (label, k, bold) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        texts = false
                        val pt = unit * k / DesignFormat.MM_PER_PT
                        val base = DesignElement(
                            kind = ElementKind.TEXT, text = label, fontSize = pt, bold = bold, w = d.width * 0.7f, h = 10f,
                            color = if (bold) TudBrand.BLUE else "#1A1A1A", lineHeight = if (bold) 1.1f else 1.35f,
                        )
                        state.add(base.copy(h = DesignText.neededHeight(base)))
                        state.focusText++
                    })
                }
            }
        }
        Box {
            RailButton(Icons.Outlined.Category, tr("Formen")) { shapes = true }
            DropdownMenu(expanded = shapes, onDismissRequest = { shapes = false }) {
                val s = unit * 0.25f
                listOf(
                    tr("Rechteck") to DesignElement(kind = ElementKind.RECT, w = s * 1.4f, h = s, fill = TudBrand.BLUE),
                    tr("Abgerundetes Rechteck") to DesignElement(kind = ElementKind.RECT, w = s * 1.4f, h = s, fill = "#E5F1FF", radius = s * 0.12f),
                    tr("Kreis") to DesignElement(kind = ElementKind.ELLIPSE, w = s, h = s, fill = TudBrand.MID_BLUE),
                    tr("Dreieck") to DesignElement(kind = ElementKind.TRIANGLE, w = s, h = s, fill = TudBrand.LIGHT_BLUE),
                    tr("Stern") to DesignElement(kind = ElementKind.STAR, w = s, h = s, fill = "#EE7F00"),
                    tr("Linie") to DesignElement(kind = ElementKind.LINE, w = s * 1.6f, h = 0f, stroke = TudBrand.BLUE, strokeWidth = unit * 0.004f),
                    tr("Pfeil") to DesignElement(kind = ElementKind.ARROW, w = s * 1.6f, h = 0f, stroke = TudBrand.BLUE, strokeWidth = unit * 0.005f),
                ).forEach { (label, e) -> DropdownMenuItem(text = { Text(label) }, onClick = { shapes = false; state.add(e) }) }
            }
        }
        RailButton(Icons.Outlined.EmojiSymbols, tr("Symbol")) { symbols = true }
        RailButton(Icons.Outlined.Image, tr("Bild")) { pickImage() }
        RailButton(Icons.Outlined.AutoAwesome, tr("Bild erzeugen")) { generate = true }
        RailButton(Icons.Outlined.QrCode2, tr("QR-Code")) {
            state.add(DesignElement(kind = ElementKind.QR, w = unit * 0.2f, h = unit * 0.2f, text = "https://tu-dresden.de", color = TudBrand.BLUE, fill = "#FFFFFF"))
        }
        Box {
            RailButton(Icons.Outlined.School, tr("Logo")) { logos = true }
            DropdownMenu(expanded = logos, onDismissRequest = { logos = false }) {
                val w = unit * 0.3f
                fun logo(src: String, ratio: Float) = DesignElement(kind = ElementKind.IMAGE, src = src, w = w, h = w / ratio, fitMode = "contain", alt = "Logo")
                DropdownMenuItem(text = { Text(tr("TU Dresden (blau)")) }, onClick = { logos = false; state.add(logo(DesignBrand.LOGO_BLUE, DesignBrand.LOGO_RATIO).copy(alt = "Logo der TU Dresden")) })
                DropdownMenuItem(text = { Text(tr("TU Dresden (weiß, für blaue Flächen)")) }, onClick = { logos = false; state.add(logo(DesignBrand.LOGO_WHITE, DesignBrand.LOGO_RATIO).copy(alt = "Logo der TU Dresden")) })
                c.logos.current.forEach { l ->
                    DropdownMenuItem(text = { Text(l.name) }, onClick = {
                        logos = false
                        val img = DesignImages.image(state.design.id, "logo:${l.id}")
                        state.add(logo("logo:${l.id}", img?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 2f).copy(alt = l.name))
                    })
                }
                if (c.logos.current.isEmpty()) DropdownMenuItem(text = { Text(tr("Eigene Logos: Einstellungen › Dokumente & Logos"), style = MaterialTheme.typography.bodySmall) }, onClick = { logos = false }, enabled = false)
            }
        }
    }
    if (symbols) SymbolPicker(onDismiss = { symbols = false }) { name ->
        symbols = false
        val s = unit * 0.12f
        state.add(DesignElement(kind = ElementKind.ICON, icon = name, w = s, h = s, color = TudBrand.BLUE))
    }
    if (generate) GenerateImageDialog(c, state, onDismiss = { generate = false })
}

/** Adds a picture (file, clipboard, image model) sized to its proportions, at most half of the page. */
internal fun addPicture(c: AppContainer, state: DesignEditorState, bytes: ByteArray, alt: String) {
    val type = DocMarkup.imageType(bytes) ?: "png"
    val name = c.designs.addAsset(state.design.id, bytes, if (type == "jpeg") "jpg" else type)
    val img = DesignImages.image(state.design.id, name)
    if (img == null) {
        Notices.error(tr("Das Bild ließ sich nicht lesen."))
        return
    }
    val d = state.design
    val ratio = img.width.toFloat() / img.height.coerceAtLeast(1)
    var w = d.width * 0.5f
    var h = w / ratio
    if (h > d.height * 0.5f) { h = d.height * 0.5f; w = h * ratio }
    // Replaces the picture of a selected empty picture placeholder instead of adding one.
    val target = state.single?.takeIf { it.kind == ElementKind.IMAGE && !it.src.startsWith("logo:") }
    if (target != null) state.updateSelected { it.copy(src = name, cropX = 0f, cropY = 0f, cropW = 1f, cropH = 1f, alt = alt) }
    else state.add(DesignElement(kind = ElementKind.IMAGE, src = name, w = w, h = h, alt = alt))
}

@Composable
private fun RailButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SymbolPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Symbol einfügen")) },
        text = {
            LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.width(520.dp).height(360.dp)) {
                items(DesignIcons.ALL) { (name, label, icon) ->
                    Column(
                        Modifier.padding(4.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(name) }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, contentDescription = label, tint = Color(0xFF00008C), modifier = Modifier.size(32.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** „Bild erzeugen“: a picture from the image model of the Uni server (FLUX) straight onto the canvas. */
@Composable
private fun GenerateImageDialog(c: AppContainer, state: DesignEditorState, onDismiss: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val target = state.single?.takeIf { it.kind == ElementKind.IMAGE && !it.src.startsWith("logo:") }
    val ratio = target?.let { it.w / it.h.coerceAtLeast(1f) } ?: (state.design.width / state.design.height)
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(tr("Bild erzeugen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(460.dp)) {
                HintText(
                    if (target != null) tr("Das Bild kommt in den ausgewählten Bildplatz.") else tr("Das Bild kommt als neues Element auf die Seite."),
                )
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("z. B. Studierende arbeiten im Hörsaal an Laptops, warmes Licht")) },
                )
                HintText(tr("Die Beschreibung geht an das Bildmodell des Uni-Servers. Text im Bild gelingt selten – Texte besser auf der Leinwand setzen."))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = prompt.isNotBlank() && !running, onClick = {
                running = true
                error = null
                scope.launch {
                    val result = runCatching {
                        c.llm.generateImage(c.settings.current.imageModel, prompt.trim() + ", no text, no lettering", DesignTools.imageSize(ratio)).first()
                    }
                    running = false
                    result.onSuccess { bytes -> addPicture(c, state, bytes, prompt.trim().take(200)); onDismiss() }
                        .onFailure { error = it.message ?: tr("Das Bild ließ sich nicht erzeugen.") }
                }
            }) {
                if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(tr("Erzeugen"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !running) { Text(tr("Abbrechen")) } },
    )
}

// ── Canvas ──────────────────────────────────────────────────────────────────────────────────────────

/** What the pointer does on the canvas. */
private sealed interface Drag {
    data class Move(val start: List<DesignElement>, val from: Offset) : Drag
    data class Resize(val start: DesignElement, val handle: Int) : Drag
    data class Rotate(val start: DesignElement) : Drag
    data class Marquee(val from: Offset, val to: Offset) : Drag
    data class Pan(val from: Offset, val pan: Offset) : Drag
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DesignCanvas(state: DesignEditorState, focus: FocusRequester) {
    val c = io.github.thepytales.llmhub.App.container
    var drag by remember { mutableStateOf<Drag?>(null) }
    var hover by remember { mutableStateOf<Int?>(null) }
    var lastClick by remember { mutableStateOf(0L to Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFE9ECF1))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = if (ev.isShiftPressed) 10f else 1f
                when {
                    ev.isCtrlPressed && ev.key == Key.Z -> { if (ev.isShiftPressed) state.redo() else state.undo(); true }
                    ev.isCtrlPressed && ev.key == Key.Y -> { state.redo(); true }
                    ev.isCtrlPressed && ev.key == Key.D -> { state.duplicate(); true }
                    ev.isCtrlPressed && ev.key == Key.C -> { state.copy(); DesignClipboard.mark(state.clipboard.size); true }
                    ev.isCtrlPressed && ev.key == Key.V -> { pasteFromClipboard(c, state); true }
                    ev.isCtrlPressed && ev.key == Key.A -> { state.selection = state.current.elements.filter { !it.locked }.map { it.id }.toSet(); true }
                    ev.key == Key.Delete || ev.key == Key.Backspace -> { state.delete(); true }
                    ev.key == Key.Escape && state.selection.isNotEmpty() -> { state.selection = emptySet(); true }
                    ev.key == Key.DirectionLeft -> { state.move(-step, 0f); true }
                    ev.key == Key.DirectionRight -> { state.move(step, 0f); true }
                    ev.key == Key.DirectionUp -> { state.move(0f, -step); true }
                    ev.key == Key.DirectionDown -> { state.move(0f, step); true }
                    else -> false
                }
            },
    ) {
        val d = state.design
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val fit = min((viewW - 64f) / d.width, (viewH - 64f) / d.height).coerceAtLeast(0.05f)
        val scale = fit * state.zoom
        val origin = Offset((viewW - d.width * scale) / 2, (viewH - d.height * scale) / 2) + state.pan
        fun toMm(p: Offset) = (p - origin) / scale
        val handlePx = 10f
        fun handleAt(p: Offset): Int? {
            val e = state.single ?: return null
            if (e.locked) return null
            val hs = if (DesignGeometry.isLine(e)) listOf(Handle.START, Handle.END) else (0..7).toList() + Handle.ROTATE
            return hs.firstOrNull { h ->
                val pos = if (h == Handle.ROTATE) DesignGeometry.toPage(e, Offset(e.w / 2, -24f / scale)) else DesignGeometry.toPage(e, Handle.local(e, h))
                val s = origin + pos * scale
                abs(s.x - p.x) <= handlePx && abs(s.y - p.y) <= handlePx
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Scroll) { ev ->
                    val change = ev.changes.firstOrNull() ?: return@onPointerEvent
                    val delta = change.scrollDelta
                    if (ev.keyboardModifiers.isCtrlPressed) {
                        val old = state.zoom
                        val next = (old * if (delta.y < 0) 1.12f else 1 / 1.12f).coerceIn(0.25f, 8f)
                        // Zoom around the pointer.
                        val p = change.position
                        val mm = toMm(p)
                        val newScale = fit * next
                        val newOrigin = p - mm * newScale
                        val baseOrigin = Offset((viewW - d.width * newScale) / 2, (viewH - d.height * newScale) / 2)
                        state.zoom = next
                        state.pan = newOrigin - baseOrigin
                    } else {
                        val px = 48f
                        state.pan = if (ev.keyboardModifiers.isShiftPressed) state.pan + Offset(-delta.y * px, 0f) else state.pan + Offset(-delta.x * px, -delta.y * px)
                    }
                    change.consume()
                }
                .onPointerEvent(PointerEventType.Move) { ev ->
                    if (drag == null) hover = ev.changes.firstOrNull()?.let { handleAt(it.position) }
                }
                .pointerHoverIcon(
                    when (hover) {
                        null -> PointerIcon.Default
                        Handle.ROTATE -> PointerIcon.Hand
                        else -> PointerIcon(java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR))
                    },
                )
                .pointerInput(state.design.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        runCatching { focus.requestFocus() }
                        val shift = currentEvent.keyboardModifiers.isShiftPressed
                        val p = down.position
                        val mm = toMm(p)
                        val now = System.currentTimeMillis()
                        val double = now - lastClick.first < 400 && (lastClick.second - p).getDistance() < 6f
                        lastClick = now to p
                        val tol = 4f / scale
                        val handle = handleAt(p)
                        val hit = DesignGeometry.hit(state.current, mm, tol)
                        drag = when {
                            handle == Handle.ROTATE -> state.single?.let { state.beginGesture(); Drag.Rotate(it) }
                            handle != null -> state.single?.let { state.beginGesture(); Drag.Resize(it, handle) }
                            currentEvent.buttons.isSecondaryPressed || currentEvent.buttons.isTertiaryPressed -> Drag.Pan(p, state.pan)
                            hit != null -> {
                                if (shift) state.selection = if (hit.id in state.selection) state.selection - hit.id else state.selection + hit.id
                                else if (hit.id !in state.selection) state.selection = setOf(hit.id)
                                if (double && hit.kind == ElementKind.TEXT) state.focusText++
                                if (double && hit.kind == ElementKind.IMAGE) DesignCanvasActions.replaceImage?.invoke()
                                val moving = state.selected.filter { !it.locked }
                                if (moving.isEmpty()) null else { state.beginGesture(); Drag.Move(moving, mm) }
                            }
                            else -> {
                                if (!shift) state.selection = emptySet()
                                Drag.Marquee(mm, mm)
                            }
                        }
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val pos = toMm(ch.position)
                            if ((ch.position - p).getDistance() > 2f) moved = true
                            when (val g = drag) {
                                is Drag.Move -> if (moved) state.dragMove(g.start, pos - g.from, 7f / scale, free = ev.keyboardModifiers.isCtrlPressed)
                                is Drag.Resize -> state.dragResize(g.start, g.handle, pos, Handle.keepsRatio(g.start) != ev.keyboardModifiers.isShiftPressed)
                                is Drag.Rotate -> state.dragRotate(g.start, pos, ev.keyboardModifiers.isShiftPressed)
                                is Drag.Marquee -> drag = g.copy(to = pos)
                                is Drag.Pan -> state.pan = g.pan + (ch.position - g.from)
                                null -> Unit
                            }
                            ch.consume()
                        }
                        (drag as? Drag.Marquee)?.let { g ->
                            if (moved) {
                                val r = androidx.compose.ui.geometry.Rect(min(g.from.x, g.to.x), min(g.from.y, g.to.y), max(g.from.x, g.to.x), max(g.from.y, g.to.y))
                                val inside = state.current.elements.filter { !it.locked && r.contains(DesignGeometry.bounds(it).topLeft) && r.contains(DesignGeometry.bounds(it).bottomRight) }
                                state.selection = (if (shift) state.selection else emptySet()) + inside.map { it.id }
                            }
                        }
                        if (drag is Drag.Move && !moved) state.endGesture()
                        if (drag !is Drag.Marquee && drag !is Drag.Pan) state.endGesture()
                        drag = null
                    }
                },
        ) {
            // Shadow and page.
            translate(origin.x, origin.y) {
                drawRect(Color(0x22000000), Offset(3f, 4f), Size(d.width * scale, d.height * scale))
                drawRect(Color.White, Offset.Zero, Size(d.width * scale, d.height * scale))
                drawDesignPage(d, state.current, scale, editing = true)
                // Selection frames and handles.
                val sel = state.selected
                sel.forEach { e ->
                    val corners = DesignGeometry.corners(e).map { it * scale }
                    val frame = Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    if (DesignGeometry.isLine(e)) {
                        val a = DesignGeometry.toPage(e, Offset(0f, 0f)) * scale
                        val b = DesignGeometry.toPage(e, Offset(e.w, e.h)) * scale
                        drawLine(EditorAccent, a, b, 1.5f)
                    } else drawPath(frame, if (e.locked) Color.Gray else EditorAccent, style = Stroke(1.5f, pathEffect = if (e.locked) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null))
                }
                state.single?.takeIf { !it.locked }?.let { e ->
                    val hs = if (DesignGeometry.isLine(e)) listOf(Handle.START, Handle.END) else (0..7).toList()
                    hs.forEach { h ->
                        val pos = DesignGeometry.toPage(e, Handle.local(e, h)) * scale
                        drawRect(Color.White, pos - Offset(5f, 5f), Size(10f, 10f))
                        drawRect(EditorAccent, pos - Offset(5f, 5f), Size(10f, 10f), style = Stroke(1.5f))
                    }
                    if (!DesignGeometry.isLine(e)) {
                        val top = DesignGeometry.toPage(e, Offset(e.w / 2, 0f)) * scale
                        val rot = DesignGeometry.toPage(e, Offset(e.w / 2, -24f / scale)) * scale
                        drawLine(EditorAccent, top, rot, 1.2f)
                        drawCircle(Color.White, 7f, rot)
                        drawCircle(EditorAccent, 7f, rot, style = Stroke(1.5f))
                    }
                }
                // Guides while moving.
                state.guides.forEach { g ->
                    if (g.vertical) drawLine(Color(0xFFE6007E), Offset(g.at * scale, -2000f), Offset(g.at * scale, d.height * scale + 2000f), 1f)
                    else drawLine(Color(0xFFE6007E), Offset(-2000f, g.at * scale), Offset(d.width * scale + 2000f, g.at * scale), 1f)
                }
                (drag as? Drag.Marquee)?.let { g ->
                    val tl = Offset(min(g.from.x, g.to.x), min(g.from.y, g.to.y)) * scale
                    val br = Offset(max(g.from.x, g.to.x), max(g.from.y, g.to.y)) * scale
                    drawRect(EditorAccent.copy(alpha = 0.08f), tl, Size(br.x - tl.x, br.y - tl.y))
                    drawRect(EditorAccent, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(1f))
                }
            }
        }
        if (state.current.elements.isEmpty()) {
            Text(
                tr("Leere Seite – links Text, Formen, Symbole, Bilder, QR-Codes oder das Logo hinzufügen."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}

/** Hooks of the canvas for the panel (double click on a picture opens the file dialog). */
internal object DesignCanvasActions {
    var replaceImage: (() -> Unit)? = null
}

/** Strg+C/V: elements inside the editor; a picture or text from other programs becomes a new element. */
internal object DesignClipboard {
    private const val MARK = "[Lora-Design: Elemente]"

    fun mark(count: Int) {
        if (count == 0) return
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(MARK), null) }
    }

    /** What the system clipboard holds: our own elements (null), a picture or a text. */
    fun read(): Any? = runCatching {
        val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        when {
            cb.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.imageFlavor) -> cb.getData(java.awt.datatransfer.DataFlavor.imageFlavor) as? java.awt.Image
            cb.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor) -> (cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String)?.takeIf { it != MARK }
            else -> null
        }
    }.getOrNull()
}

private fun pasteFromClipboard(c: AppContainer, state: DesignEditorState) {
    when (val content = DesignClipboard.read()) {
        is java.awt.Image -> {
            val img = java.awt.image.BufferedImage(content.getWidth(null).coerceAtLeast(1), content.getHeight(null).coerceAtLeast(1), java.awt.image.BufferedImage.TYPE_INT_ARGB)
            img.createGraphics().apply { drawImage(content, 0, 0, null); dispose() }
            val out = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "png", out)
            addPicture(c, state, out.toByteArray(), tr("Bild aus der Zwischenablage"))
        }
        is String -> if (content.isNotBlank()) {
            val d = state.design
            val pt = min(d.width, d.height) * 0.035f / DesignFormat.MM_PER_PT
            state.add(DesignElement(kind = ElementKind.TEXT, text = content.trim(), fontSize = pt, w = d.width * 0.6f, h = d.height * 0.2f, fit = true, lineHeight = 1.35f, color = "#1A1A1A"))
        } else state.paste()
        else -> state.paste()
    }
}

// ── Properties on the right ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesPanel(c: AppContainer, state: DesignEditorState) {
    val sel = state.selected
    val e = state.single
    val scope = rememberCoroutineScope()
    fun pickImage() = scope.launch {
        val f = withContext(Dispatchers.IO) { DesktopActions.pickFiles(tr("Bild auswählen"), multiple = false, extensions = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")).firstOrNull() } ?: return@launch
        withContext(Dispatchers.IO) { runCatching { f.readBytes() }.getOrNull() }?.let { addPicture(c, state, it, f.nameWithoutExtension) }
    }
    DisposableEffect(Unit) {
        DesignCanvasActions.replaceImage = { pickImage() }
        onDispose { DesignCanvasActions.replaceImage = null }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            sel.isEmpty() -> PageProperties(state)
            e == null -> {
                Text(tr("${sel.size} Elemente ausgewählt"), style = MaterialTheme.typography.titleSmall)
                AlignButtons(state, many = true)
                LayerButtons(state)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(ElementKind.label(e.kind)) + if (e.slot.isNotBlank()) " · ${e.slot}" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { state.updateSelected { it.copy(locked = !it.locked) } }) {
                        Icon(if (e.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, contentDescription = tr(if (e.locked) "Entsperren" else "Sperren"))
                    }
                }
                when (e.kind) {
                    ElementKind.TEXT -> TextProperties(state, e)
                    ElementKind.IMAGE -> ImageProperties(state, e, ::pickImage)
                    ElementKind.ICON -> IconProperties(state, e)
                    ElementKind.QR -> QrProperties(state, e)
                    ElementKind.LINE, ElementKind.ARROW -> {
                        ColorField(tr("Farbe"), e.stroke.ifBlank { e.color }, allowNone = false) { col -> state.updateSelected("stroke") { it.copy(stroke = col) } }
                        NumberField(tr("Linienstärke (mm)"), e.strokeWidth, "sw") { v -> state.updateSelected("sw") { it.copy(strokeWidth = v.coerceIn(0.05f, 50f)) } }
                    }
                    else -> ShapeProperties(state, e)
                }
                GeometryFields(state, e)
                AlignButtons(state, many = false)
                LayerButtons(state)
            }
        }
    }
}

@Composable
private fun PageProperties(state: DesignEditorState) {
    val d = state.design
    var formats by remember { mutableStateOf(false) }
    Text(tr("Seite") + " ${state.page + 1} " + tr("von") + " ${d.pages.size}", style = MaterialTheme.typography.titleSmall)
    ColorField(tr("Hintergrund"), state.current.background, allowNone = false) { state.setBackground(it) }
    Text(tr("Format"), style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { formats = true }) { Text(DesignFormat.byId(d.format)?.let { "${it.label} (${it.sizeLabel})" } ?: d.format) }
        DropdownMenu(expanded = formats, onDismissRequest = { formats = false }) {
            DesignFormat.ALL.forEach { f ->
                DropdownMenuItem(text = { Text("${f.label} · ${f.sizeLabel}") }, onClick = { formats = false; state.changeFormat(f.id, f.width, f.height) })
            }
        }
    }
    HintText(tr("Ein anderes Format passt alle Elemente an die neue Größe an."))
    HorizontalDivider()
    HintText(
        tr("Tipps: Klick wählt aus, Umschalt+Klick mehrere, Ziehen auf der freien Fläche wählt alles im Rahmen. Pfeiltasten verschieben (mit Umschalt 10 mm), Strg+D verdoppelt, Entf löscht, Strg+Z macht rückgängig. Strg+Mausrad zoomt, das Mausrad schiebt die Ansicht; beim Ziehen rastet ein Element an Seite und anderen Elementen ein (Strg hält das Einrasten an)."),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextProperties(state: DesignEditorState, e: DesignElement) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.focusText) { if (state.focusText > 0) runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = e.text,
        onValueChange = { t -> state.updateSelected("text-${e.id}") { grow(it.copy(text = t)) } },
        minLines = 3, maxLines = 10,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        label = { Text(tr("Text")) },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        NumberField(tr("Größe (pt)"), e.fontSize, "fs-${e.id}", Modifier.width(110.dp)) { v -> state.updateSelected("fs-${e.id}") { grow(it.copy(fontSize = v.coerceIn(2f, 1000f))) } }
        Spacer(Modifier.size(6.dp))
        ToggleIcon(Icons.Outlined.FormatBold, tr("Fett"), e.bold) { state.updateSelected { it.copy(bold = !it.bold) } }
        ToggleIcon(Icons.Outlined.FormatItalic, tr("Kursiv"), e.italic) { state.updateSelected { it.copy(italic = !it.italic) } }
    }
    Row {
        ToggleIcon(Icons.Outlined.FormatAlignLeft, tr("Links"), e.align == "left") { state.updateSelected { it.copy(align = "left") } }
        ToggleIcon(Icons.Outlined.FormatAlignCenter, tr("Zentriert"), e.align == "center") { state.updateSelected { it.copy(align = "center") } }
        ToggleIcon(Icons.Outlined.FormatAlignRight, tr("Rechts"), e.align == "right") { state.updateSelected { it.copy(align = "right") } }
    }
    Row {
        listOf("top" to tr("Oben"), "middle" to tr("Mitte"), "bottom" to tr("Unten")).forEach { (v, label) ->
            FilterChip(selected = e.valign == v, onClick = { state.updateSelected { it.copy(valign = v) } }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
    ColorField(tr("Schriftfarbe"), e.color, allowNone = false) { col -> state.updateSelected("color") { it.copy(color = col) } }
    Text(tr("Zeilenabstand") + " ${"%.2f".format(e.lineHeight)}", style = MaterialTheme.typography.labelMedium)
    Slider(value = e.lineHeight, onValueChange = { v -> state.updateSelected("lh-${e.id}") { it.copy(lineHeight = (v * 20).toInt() / 20f) } }, valueRange = 0.8f..2.2f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("An die Box anpassen"), style = MaterialTheme.typography.bodyMedium)
            HintText(tr("Zu langer Text wird kleiner statt überzustehen."))
        }
        Switch(checked = e.fit, onCheckedChange = { on -> state.updateSelected { it.copy(fit = on) } })
    }
    ColorField(tr("Hintergrund der Box"), e.fill, allowNone = true) { col -> state.updateSelected("fill") { it.copy(fill = col, padding = if (col.isNotBlank() && it.padding == 0f) min(it.w, it.h) * 0.06f else it.padding) } }
    if (e.fill.isNotBlank()) {
        NumberField(tr("Innenabstand (mm)"), e.padding, "pad-${e.id}") { v -> state.updateSelected("pad-${e.id}") { it.copy(padding = v.coerceIn(0f, min(it.w, it.h) / 2)) } }
        NumberField(tr("Eckenradius (mm)"), e.radius, "r-${e.id}") { v -> state.updateSelected("r-${e.id}") { it.copy(radius = v.coerceAtLeast(0f)) } }
    }
}

/** Text boxes without „An die Box anpassen“ grow downwards when their text gets longer. */
private fun grow(e: DesignElement): DesignElement =
    if (e.fit || e.text.isEmpty()) e else e.copy(h = maxOf(e.h, DesignText.neededHeight(e)))

@Composable
private fun ShapeProperties(state: DesignEditorState, e: DesignElement) {
    ColorField(tr("Füllung"), e.fill, allowNone = true) { col -> state.updateSelected("fill") { it.copy(fill = col) } }
    ColorField(tr("Rahmen"), e.stroke, allowNone = true) { col -> state.updateSelected("stroke") { it.copy(stroke = col, strokeWidth = if (col.isNotBlank() && it.strokeWidth == 0f) 0.5f else it.strokeWidth) } }
    if (e.stroke.isNotBlank()) NumberField(tr("Rahmenstärke (mm)"), e.strokeWidth, "sw-${e.id}") { v -> state.updateSelected("sw-${e.id}") { it.copy(strokeWidth = v.coerceIn(0f, 50f)) } }
    if (e.kind == ElementKind.RECT) NumberField(tr("Eckenradius (mm)"), e.radius, "r-${e.id}") { v -> state.updateSelected("r-${e.id}") { it.copy(radius = v.coerceAtLeast(0f)) } }
}

@Composable
private fun ImageProperties(state: DesignEditorState, e: DesignElement, pick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = pick) { Text(if (e.src.isBlank()) tr("Bild wählen") else tr("Bild ersetzen")) }
    }
    if (e.src.isNotBlank() && !e.src.startsWith("logo:")) {
        Row {
            FilterChip(selected = e.fitMode != "contain", onClick = { state.updateSelected { it.copy(fitMode = "cover") } }, label = { Text(tr("Füllen")) })
            Spacer(Modifier.size(6.dp))
            FilterChip(selected = e.fitMode == "contain", onClick = { state.updateSelected { it.copy(fitMode = "contain") } }, label = { Text(tr("Ganz zeigen")) })
        }
        Text(tr("Zuschneiden"), style = MaterialTheme.typography.labelLarge)
        CropSlider(tr("Links"), e.cropX) { v -> state.updateSelected("crop-l") { it.copy(cropX = v.coerceAtMost(it.cropX + it.cropW - 0.05f), cropW = (it.cropX + it.cropW - v).coerceAtLeast(0.05f)) } }
        CropSlider(tr("Rechts"), 1f - e.cropX - e.cropW) { v -> state.updateSelected("crop-r") { it.copy(cropW = (1f - v - it.cropX).coerceAtLeast(0.05f)) } }
        CropSlider(tr("Oben"), e.cropY) { v -> state.updateSelected("crop-t") { it.copy(cropY = v.coerceAtMost(it.cropY + it.cropH - 0.05f), cropH = (it.cropY + it.cropH - v).coerceAtLeast(0.05f)) } }
        CropSlider(tr("Unten"), 1f - e.cropY - e.cropH) { v -> state.updateSelected("crop-b") { it.copy(cropH = (1f - v - it.cropY).coerceAtLeast(0.05f)) } }
        NumberField(tr("Eckenradius (mm)"), e.radius, "r-${e.id}") { v -> state.updateSelected("r-${e.id}") { it.copy(radius = v.coerceAtLeast(0f)) } }
    }
    OutlinedTextField(
        value = e.alt, onValueChange = { t -> state.updateSelected("alt-${e.id}") { it.copy(alt = t) } },
        label = { Text(tr("Alternativtext (Barrierefreiheit)")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
    )
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
        Slider(value = value.coerceIn(0f, 0.9f), onValueChange = onChange, valueRange = 0f..0.9f, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()} %", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun IconProperties(state: DesignEditorState, e: DesignElement) {
    var pick by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { pick = true }) { Text(tr("Anderes Symbol")) }
    ColorField(tr("Farbe"), e.color, allowNone = false) { col -> state.updateSelected("color") { it.copy(color = col) } }
    if (pick) SymbolPicker(onDismiss = { pick = false }) { name -> pick = false; state.updateSelected { it.copy(icon = name) } }
}

@Composable
private fun QrProperties(state: DesignEditorState, e: DesignElement) {
    OutlinedTextField(
        value = e.text, onValueChange = { t -> state.updateSelected("qr-${e.id}") { it.copy(text = t) } },
        label = { Text(tr("Link oder Text")) }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
    )
    HintText(tr("Vor dem Druck mit dem Handy testen. Mindestgröße etwa 2 × 2 cm."))
    ColorField(tr("Farbe"), e.color, allowNone = false) { col -> state.updateSelected("color") { it.copy(color = col) } }
    ColorField(tr("Hintergrund"), e.fill.ifBlank { "#FFFFFF" }, allowNone = false) { col -> state.updateSelected("fill") { it.copy(fill = col) } }
}

@Composable
private fun GeometryFields(state: DesignEditorState, e: DesignElement) {
    Text(tr("Position und Größe (mm)"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("X", e.x, "x-${e.id}", Modifier.weight(1f)) { v -> state.updateSelected("x-${e.id}") { it.copy(x = v) } }
        NumberField("Y", e.y, "y-${e.id}", Modifier.weight(1f)) { v -> state.updateSelected("y-${e.id}") { it.copy(y = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField(tr("Breite"), e.w, "w-${e.id}", Modifier.weight(1f)) { v -> state.updateSelected("w-${e.id}") { it.copy(w = if (DesignGeometry.isLine(it)) v else v.coerceAtLeast(1f)) } }
        NumberField(tr("Höhe"), e.h, "h-${e.id}", Modifier.weight(1f)) { v -> state.updateSelected("h-${e.id}") { it.copy(h = if (DesignGeometry.isLine(it)) v else v.coerceAtLeast(1f)) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField(tr("Drehung (°)"), e.rotation, "rot-${e.id}", Modifier.weight(1f)) { v -> state.updateSelected("rot-${e.id}") { it.copy(rotation = ((v % 360) + 360) % 360) } }
        Column(Modifier.weight(1f)) {
            Text(tr("Deckkraft") + " ${(e.opacity * 100).toInt()} %", style = MaterialTheme.typography.labelSmall)
            Slider(value = e.opacity, onValueChange = { v -> state.updateSelected("op-${e.id}") { it.copy(opacity = v.coerceIn(0.05f, 1f)) } }, valueRange = 0.05f..1f)
        }
    }
}

@Composable
private fun AlignButtons(state: DesignEditorState, many: Boolean) {
    Text(if (many) tr("Ausrichten") else tr("Auf der Seite ausrichten"), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf("left" to "⇤", "hcenter" to "↔", "right" to "⇥", "top" to "⤒", "vcenter" to "↕", "bottom" to "⤓").forEach { (mode, sym) ->
            val label = when (mode) {
                "left" -> tr("Links"); "hcenter" -> tr("Mittig"); "right" -> tr("Rechts"); "top" -> tr("Oben"); "vcenter" -> tr("Mitte"); else -> tr("Unten")
            }
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { state.align(mode) }.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(sym, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (many && state.selected.size >= 3) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { state.align("distributeH") }) { Text(tr("Waagrecht verteilen"), style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(onClick = { state.align("distributeV") }) { Text(tr("Senkrecht verteilen"), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerButtons(state: DesignEditorState) {
    Text(tr("Ebene"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { state.reorder(up = true, all = true) }) { Icon(Icons.Outlined.FlipToFront, contentDescription = tr("Ganz nach vorne")) }
        TextButton(onClick = { state.reorder(up = true, all = false) }) { Text(tr("Nach vorne")) }
        TextButton(onClick = { state.reorder(up = false, all = false) }) { Text(tr("Nach hinten")) }
        IconButton(onClick = { state.reorder(up = false, all = true) }) { Icon(Icons.Outlined.FlipToBack, contentDescription = tr("Ganz nach hinten")) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.duplicate() }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Verdoppeln")) }
        OutlinedButton(onClick = { state.delete() }) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Löschen")) }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent, CircleShape)) {
        Icon(icon, contentDescription = label, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A number in a small field; commas count as decimal points. */
@Composable
private fun NumberField(label: String, value: Float, key: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (Float) -> Unit) {
    fun show(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(java.util.Locale.GERMANY, v)
    var text by remember(key) { mutableStateOf(show(value)) }
    LaunchedEffect(value) { if (text.replace(',', '.').toFloatOrNull() != value) text = show(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; t.replace(',', '.').toFloatOrNull()?.let(onChange) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

/** Colours of the Brand-Kit, „keine“ (optional) and an own value as #RRGGBB. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorField(label: String, value: String, allowNone: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (allowNone) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Color.White).border(if (value.isBlank()) 2.dp else 1.dp, if (value.isBlank()) EditorAccent else Color(0x33000000), CircleShape)
                        .clickable { onPick("") },
                    contentAlignment = Alignment.Center,
                ) { Text("⌀", style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
            }
            DesignBrand.COLORS.forEach { (_, hex) ->
                val on = value.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(designColor(hex) ?: Color.Gray)
                        .border(if (on) 2.dp else 1.dp, if (on) EditorAccent else Color(0x33000000), CircleShape)
                        .clickable { onPick(hex) },
                ) {}
            }
        }
        var own by remember(value) { mutableStateOf(value) }
        OutlinedTextField(
            value = own,
            onValueChange = { t -> own = t; DesignBrand.color(t)?.let(onPick) },
            placeholder = { Text("#RRGGBB") }, singleLine = true, modifier = Modifier.width(140.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }
}

// ── Pages at the bottom ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageStrip(state: DesignEditorState) {
    val d = state.design
    Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            d.pages.forEachIndexed { i, p ->
                val selected = i == state.page
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.height(66.dp).border(if (selected) 2.dp else 1.dp, if (selected) EditorAccent else Color(0x22000000)).clickable { state.page = i; state.selection = emptySet() },
                    ) { PageThumb(d, i, p.hashCode()) }
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Column {
            Row {
                TextButton(onClick = { state.addPage(copyCurrent = false) }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text(tr("Seite")) }
                TextButton(onClick = { state.addPage(copyCurrent = true) }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Text(tr("Verdoppeln")) }
            }
            Row {
                TextButton(onClick = { state.movePage(-1) }, enabled = state.page > 0) { Text("◀") }
                TextButton(onClick = { state.movePage(1) }, enabled = state.page < d.pages.size - 1) { Text("▶") }
                TextButton(onClick = { state.deletePage() }, enabled = d.pages.size > 1) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Text(tr("Löschen")) }
            }
        }
    }
}

@Composable
private fun PageThumb(d: Design, index: Int, key: Int) {
    val bmp by produceState<ImageBitmap?>(null, d.id, index, key, d.width, d.height) {
        delay(250)
        value = withContext(Dispatchers.Default) { runCatching { renderDesignPage(d, index, 160) }.getOrNull() }
    }
    val ratio = d.width / d.height.coerceAtLeast(1f)
    Box(Modifier.width((66 * ratio).coerceIn(30f, 200f).dp).fillMaxHeight().background(Color.White)) {
        bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize()) }
    }
}
