package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.PageMarks
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.PdfConvert
import io.github.thepytales.llmhub.docs.PdfExtras
import io.github.thepytales.llmhub.docs.PdfOcr
import io.github.thepytales.llmhub.docs.PdfOriginals
import io.github.thepytales.llmhub.docs.PdfPlatform
import io.github.thepytales.llmhub.docs.PdfTable
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.Signature
import io.github.thepytales.llmhub.docs.SignatureImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * PDF-Werkzeugkasten Stufe 2 (AP-15): the areas „Unterschreiben“, „Umwandeln“ (with the text recognition) and „KI“ of
 * the toolbox (ui/PdfToolScreen).
 */

// ── Unterschreiben ────────────────────────────────────────────────────────────────────────────────────────

/** A signature drawn small (chips, the pad's preview). */
@Composable
internal fun SignaturePreview(sig: Signature, modifier: Modifier = Modifier) {
    val ink = Color(0xFF000000 or sig.color.toLong())
    Canvas(modifier) {
        if (sig.image != null) {
            drawRect(ink.copy(alpha = 0.12f))
            return@Canvas
        }
        val w = minOf(size.width, size.height * sig.aspect)
        val h = w / sig.aspect
        val ox = (size.width - w) / 2
        val oy = (size.height - h) / 2
        sig.strokes.forEach { s -> strokePath(s.map { Offset(ox + it.x * w, oy + it.y * h) }, ink, 1.6f) }
    }
}

private fun DrawScope.strokePath(points: List<Offset>, color: Color, width: Float) {
    if (points.isEmpty()) return
    val p = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(p, color, style = Stroke(width * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Unterschreiben: choose or draw a signature, its size and an optional „Ort, Datum“, then tap the place on the page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfSigner(c: AppContainer, tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val context = LocalContext.current
    val info = tab.info ?: return
    val signatures by c.signatures.items.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    val sig = signatures.firstOrNull { it.id == selected } ?: signatures.firstOrNull()
    var draw by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(0.26f) }
    var withDate by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf("Dresden") }
    val importImage = rememberMediaImport(anyFile = true) { name, _, input ->
        val bytes = withContext(Dispatchers.IO) { input.use { it.readBytes() } }
        val size = SignatureImages.size(bytes)
        if (size == null) {
            Notices.error("„$name“ " + tr("ist kein PNG- oder JPEG-Bild."))
        } else {
            val s = Signature(name = name.substringBeforeLast('.'), image = java.util.Base64.getEncoder().encodeToString(bytes), aspect = (size.first.toFloat() / size.second).coerceIn(0.5f, 12f))
            c.signatures.upsert(s)
            selected = s.id
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            signatures.forEach { s ->
                FilterChip(
                    selected = s.id == sig?.id, onClick = { selected = s.id },
                    label = { SignaturePreview(s, Modifier.size(72.dp, 24.dp)) },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, contentDescription = tr("Unterschrift löschen"), modifier = Modifier.size(16.dp).pointerInput(s.id) {
                            detectTapGestures {
                                c.signatures.delete(s.id)
                                Notices.undo(tr("Unterschrift gelöscht"), restore = { c.signatures.upsert(s) })
                            }
                        })
                    },
                )
            }
            AssistChip(onClick = { draw = true }, label = { Text(tr("Zeichnen")) }, leadingIcon = { Icon(Icons.Outlined.Draw, null, Modifier.size(18.dp)) })
            AssistChip(onClick = importImage, label = { Text(tr("Bild")) }, leadingIcon = { Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)) })
        }
        if (sig != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Größe"), style = MaterialTheme.typography.labelMedium)
                Slider(width, { width = it }, valueRange = 0.12f..0.5f, modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 8.dp))
                Checkbox(withDate, { withDate = it })
                Text(tr("Ort, Datum darunter"), style = MaterialTheme.typography.labelMedium)
                if (withDate) OutlinedTextField(place, { place = it }, singleLine = true, modifier = Modifier.width(150.dp).padding(start = 8.dp), textStyle = MaterialTheme.typography.bodySmall)
            }
        }
        HintText(
            tr(if (sig == null) "Lege zuerst eine Unterschrift an: mit Maus oder Finger zeichnen oder ein Bild (PNG mit durchsichtigem Hintergrund) wählen." else "Auf die Stelle tippen, an der die Unterschrift stehen soll. Rückgängig nimmt sie wieder weg."),
        )
    }
    val caption = if (withDate) listOf(place.trim(), SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())).filter { it.isNotEmpty() }.joinToString(", ") else null
    PageCanvasList(tab, info) { page, w, h ->
        val s = sig ?: return@PageCanvasList
        Box(
            Modifier.fillMaxSize().pointerInput(s.id, width, w, h, caption) {
                detectTapGestures { p ->
                    val bw = width
                    val bh = width * (w / h) / s.aspect
                    val box = PdfBox((p.x / w - bw / 2).coerceIn(0f, 1f - bw), (p.y / h - bh / 2).coerceIn(0f, (1f - bh).coerceAtLeast(0f)), bw, bh)
                    onRun { src, out -> PdfExtras.sign(context, src, page, box, s, caption, out) }
                }
            },
        )
    }
    if (draw) SignaturePad(onDismiss = { draw = false }, onSave = { s ->
        c.signatures.upsert(s)
        selected = s.id
        draw = false
    })
}

/** A pad to sign on with mouse, pen or finger; „Speichern“ keeps the signature for later. */
@Composable
private fun SignaturePad(onDismiss: () -> Unit, onSave: (Signature) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var name by remember { mutableStateOf("Unterschrift") }
    val ink = Color(0xFF1A2A6C)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Unterschrift zeichnen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2.6f).background(Color.White, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                ) {
                    // The line to sign on.
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(Color(0x33000000), Offset(size.width * 0.06f, size.height * 0.76f), Offset(size.width * 0.94f, size.height * 0.76f), 1.2f * density)
                    }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { p -> current.clear(); current += p },
                                onDrag = { change, _ -> change.consume(); current += change.position },
                                onDragEnd = { if (current.size > 1) strokes += current.toList(); current.clear() },
                                onDragCancel = { current.clear() },
                            )
                        },
                    ) {
                        strokes.forEach { strokePath(it, ink, 2.4f) }
                        strokePath(current.toList(), ink, 2.4f)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name")) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear() }) { Text(tr("Neu")) }
                }
                HintText(tr("Die Unterschrift bleibt in den Daten der App und lässt sich unter „Unterschreiben“ wieder löschen."))
            }
        },
        confirmButton = {
            Button(enabled = strokes.isNotEmpty(), onClick = {
                Signature.fromPad(strokes.map { s -> s.map { it.x to it.y } }, name.ifBlank { "Unterschrift" })?.let(onSave)
            }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

// ── Umwandeln und Texterkennung ───────────────────────────────────────────────────────────────────────────

/** Umwandeln: OCR for scans, the PDF as Word/Markdown/Text/Excel/pictures, and other files into PDFs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfConverter(c: AppContainer, tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit, onPickFile: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val texts by produceState<List<String>?>(null, tab.file) { value = withContext(Dispatchers.IO) { runCatching { PdfExtras.pageTexts(context, tab.file) }.getOrNull() } }
    val scanned = texts?.let { PdfOcr.scanned(it) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var askModel by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<String?>(null) }
    val local = PdfPlatform.localOcr
    val model = remember { PdfOcr.visionModel(c) }

    fun ocr(useModel: String?) {
        val pages = scanned.orEmpty().ifEmpty { texts?.indices?.toList().orEmpty() }
        if (pages.isEmpty()) return
        progress = 0 to pages.size
        scope.launch {
            val result = runCatching {
                PdfOcr.recognize(c, context, tab.file, pages, useModel, File(PdfWork.dir(context), "ocr-${tab.id}")) { d, t -> progress = d to t }
            }
            progress = null
            result.onFailure { Notices.error(tr("Texterkennung") + ": " + (it.message ?: it.javaClass.simpleName)) }
            result.onSuccess { r ->
                val found = r.pages.filter { it.lines.isNotEmpty() }
                if (found.isEmpty()) Notices.error(tr("Auf den Seiten wurde kein Text erkannt."))
                else {
                    onRun { src, out -> PdfExtras.addTextLayer(context, src, found, out) }
                    Notices.info(tr(if (found.size == 1) "Text auf {0} Seite erkannt" else "Text auf {0} Seiten erkannt", found.size) + " (${r.engine})" + if (r.failed.isNotEmpty()) " · " + tr("nicht erkannt") + ": " + r.failed.joinToString(", ") { "${it + 1}" } else "")
                }
            }
        }
    }

    val saveDocx = rememberSaveAs(DocFormat.DOCX.mime)
    val saveMd = rememberSaveAs(DocFormat.MD.mime)
    val saveTxt = rememberSaveAs(DocFormat.TXT.mime)
    val saveXlsx = rememberSaveAs(DocFormat.XLSX.mime)
    val saveZip = rememberSaveAs("application/zip")
    fun export(label: String, block: suspend (File) -> File, save: (File) -> Unit) {
        exporting = label
        scope.launch {
            val dir = File(PdfWork.dir(context), "export").apply { mkdirs() }
            val result = runCatching { withContext(Dispatchers.IO) { block(dir) } }
            exporting = null
            result.onSuccess(save).onFailure { Notices.error(it.message ?: tr("Das hat nicht geklappt")) }
        }
    }
    val title = tab.name.removeSuffix(".pdf").removeSuffix(".PDF")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Texterkennung (OCR)"), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    when {
                        scanned == null -> tr("Prüfe die Seiten …")
                        scanned.isEmpty() -> tr("Alle Seiten haben schon Text – Suchen, Kopieren und „Frag das PDF“ funktionieren.")
                        else -> "${scanned.size} " + tr("von") + " ${texts!!.size} " + tr("Seiten sind Scans ohne Text. Die Texterkennung legt den Text unsichtbar über die Seiten – danach lassen sie sich durchsuchen, kopieren und mit KI auswerten.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                progress?.let { (d, t) ->
                    LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth())
                    HintText(tr("Seite") + " ${(d + 1).coerceAtMost(t)} " + tr("von") + " $t …")
                }
                if (!scanned.isNullOrEmpty() && progress == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (local != null) Button(onClick = { ocr(null) }) { Text(tr("Auf diesem PC erkennen")) }
                        if (model != null) OutlinedButton(onClick = { askModel = model }) { Text(tr("Mit KI-Modell erkennen")) }
                    }
                    HintText(
                        if (local != null) tr("Auf diesem PC: mit der Texterkennung von Windows, nichts verlässt den PC. Mit KI-Modell: besser bei Handschrift und schlechten Scans, die Seitenbilder gehen an den Server.")
                        else tr("Die Seitenbilder gehen an ein KI-Modell, das Bilder versteht – darin lassen sich keine Namen ersetzen."),
                    )
                }
            }
        }
        AppCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Transform, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("PDF speichern als …"), style = MaterialTheme.typography.titleSmall)
                    if (exporting != null) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val busy = exporting != null
                    OutlinedButton(enabled = !busy, onClick = { export("Word", { PdfConvert.fromPdf(context, tab.file, title, DocFormat.DOCX, it) }, saveDocx) }) { Text("Word (.docx)") }
                    OutlinedButton(enabled = !busy, onClick = { export("Markdown", { PdfConvert.fromPdf(context, tab.file, title, DocFormat.MD, it) }, saveMd) }) { Text("Markdown") }
                    OutlinedButton(enabled = !busy, onClick = { export("Text", { PdfConvert.fromPdf(context, tab.file, title, DocFormat.TXT, it) }, saveTxt) }) { Text(tr("Text")) }
                    OutlinedButton(enabled = !busy, onClick = { export("Excel", { PdfConvert.fromPdf(context, tab.file, "$title (Tabellen)", DocFormat.XLSX, it) }, saveXlsx) }) { Text(tr("Tabellen als Excel")) }
                    OutlinedButton(enabled = !busy, onClick = { export("Bilder", { dir -> File(dir, DocsFileName.safe(title) + " (Seiten).zip").also { PdfExtras.pagesZip(context, tab.file, 150, it) } }, saveZip) }) { Text(tr("Seiten als Bilder (ZIP)")) }
                }
                HintText(tr("Word, Markdown und Text übernehmen Überschriften, Absätze, Listen und Tabellen; das Layout wird vereinfacht."))
            }
        }
        AppCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("In PDF umwandeln"), style = MaterialTheme.typography.titleSmall)
                Text(
                    tr("Word, Excel, PowerPoint, Bilder, HTML und Text öffnen sich hier als PDF – zum Zusammenfügen, Unterschreiben oder Weitergeben.") +
                        if (PdfPlatform.officeName.isNotEmpty()) " " + tr("Mit installiertem Microsoft Office genau wie gedruckt, sonst mit vereinfachtem Layout.") else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onPickFile) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Datei umwandeln …"))
                }
            }
        }
    }
    askModel?.let { m ->
        AlertDialog(
            onDismissRequest = { askModel = null },
            title = { Text(tr("Mit KI-Modell erkennen?")) },
            text = {
                Text(
                    tr("Die Bilder von {0} Seiten gehen an {1}.", scanned?.size ?: 0, io.github.thepytales.llmhub.data.ModelCatalog.info(m).displayName) + " " +
                        tr("In Bildern lassen sich keine Namen ersetzen – für sensible Unterlagen besser die Texterkennung auf dem PC nutzen."),
                )
            },
            confirmButton = { Button(onClick = { askModel = null; ocr(m) }) { Text(tr("Erkennen")) } },
            dismissButton = { TextButton(onClick = { askModel = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

/** Safe file names for exports. */
internal object DocsFileName {
    fun safe(name: String) = io.github.thepytales.llmhub.docs.DocumentWriter.safeName(name)
}

// ── KI ────────────────────────────────────────────────────────────────────────────────────────────────────

/** The PDF as attachment of a chat: text with page marks, a kept copy (so citations open the page), its form fields. */
internal suspend fun pdfAttachment(context: android.content.Context, tab: PdfTab): FileAttachment = withContext(Dispatchers.IO) {
    val texts = PdfExtras.pageTexts(context, tab.file)
    val text = PageMarks.number(texts.joinToString(PageMarks.PAGE_BREAK)).trim()
    val name = tab.name.let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
    val copy = PdfOriginals.keep(File(context.filesDir, "attachments"), name, tab.file.readBytes())?.absolutePath
    val fields = runCatching { PdfTools.formFields(context, tab.file).size }.getOrDefault(0)
    FileAttachment(name, "application/pdf", text.take(MAX_PDF_CHARS), texts.size, text.length > MAX_PDF_CHARS, path = copy, formFields = fields)
}

private const val MAX_PDF_CHARS = 120_000

/** KI: ask the PDF (Quellenmodus: every statement with page), summarize, pull out tables, explain a form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfAi(c: AppContainer, tab: PdfTab, onConvert: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val texts by produceState<List<String>?>(null, tab.file) { value = withContext(Dispatchers.IO) { runCatching { PdfExtras.pageTexts(context, tab.file) }.getOrNull() } }
    val noText = texts?.let { t -> PdfOcr.scanned(t).size == t.size } == true
    var question by remember { mutableStateOf("") }
    var tables by remember { mutableStateOf<List<PdfTable>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val saveXlsx = rememberSaveAs(DocFormat.XLSX.mime)

    fun toChat(text: String, send: Boolean, mode: String? = ChatModes.SOURCES.id, extra: String? = null) {
        busy = true
        scope.launch {
            val file = runCatching { pdfAttachment(context, tab) }.getOrElse { Notices.error(it.message ?: tr("Das PDF ließ sich nicht lesen.")); busy = false; return@launch }
            busy = false
            ChatHandoff.pending.value = ChatHandoff.Content(text, listOf(file), mode = mode, send = send, instructions = extra)
            ChatHandoff.open.value = ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (noText) {
            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Dieses PDF ist ein Scan ohne Text."), style = MaterialTheme.typography.titleSmall)
                    HintText(tr("Für Fragen, Tabellen und Erklärungen braucht die KI den Text: erst die Texterkennung laufen lassen."))
                    Button(onClick = onConvert) { Text(tr("Zur Texterkennung")) }
                }
            }
        }
        AppCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.QuestionAnswer, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Frag das PDF"), style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    question, { question = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    placeholder = { Text(tr("z. B. Welche Fristen nennt die Ausschreibung? Wie hoch ist die Förderung?")) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = question.isNotBlank() && !busy, onClick = { toChat(question.trim(), send = true); question = "" }) { Text(tr("Fragen")) }
                    if (busy) CircularProgressIndicator(Modifier.padding(start = 8.dp).size(18.dp), strokeWidth = 2.dp)
                }
                HintText(tr("Im Quellenmodus: Jede Aussage bekommt die Seite und ein Zitat, die App prüft jedes Zitat gegen das PDF; ein Klick öffnet die Stelle hier."))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                enabled = !busy, onClick = { toChat(tr("Fasse das angehängte PDF zusammen: Worum geht es, die wichtigsten Aussagen, Zahlen und Fristen – jeweils mit Seitenangabe."), send = true) },
                label = { Text(tr("Zusammenfassen")) }, leadingIcon = { Icon(Icons.Outlined.Summarize, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                enabled = !busy,
                onClick = {
                    toChat(
                        tr("Erkläre mir dieses Formular Feld für Feld: Was gehört wohin, welche Angaben und Nachweise brauche ich, worauf muss ich achten (Fristen, Unterschriften, Anlagen)? Fülle nichts aus."),
                        send = true, mode = null,
                    )
                },
                label = { Text(tr("Formular erklären")) }, leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                enabled = !busy,
                onClick = {
                    toChat(
                        tr("Zieh alle Tabellen aus dem angehängten PDF heraus und lege sie als Excel-Datei an (je Tabelle ein Blatt, Spaltenköpfe übernehmen, Zahlen als Zahlen). Nenne zu jeder Tabelle die Seite."),
                        send = true, mode = null,
                    )
                },
                label = { Text(tr("Tabellen mit KI")) }, leadingIcon = { Icon(Icons.Outlined.TextSnippet, null, Modifier.size(18.dp)) },
            )
        }
        AppCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TableChart, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Tabellen herausziehen (auf dem Gerät)"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch { tables = withContext(Dispatchers.IO) { runCatching { PdfConvert.tables(context, tab.file) }.getOrDefault(emptyList()) } }
                    }) { Text(tr("Suchen")) }
                }
                val found = tables
                when {
                    found == null -> HintText(tr("Findet Tabellen im Text des PDFs – ohne KI, nichts verlässt das Gerät. Für Scans erst die Texterkennung."))
                    found.isEmpty() -> HintText(tr("Keine Tabellen erkannt. „Tabellen mit KI“ versuchen – das findet auch unregelmäßige."))
                    else -> {
                        Text("${found.size} " + tr(if (found.size == 1) "Tabelle" else "Tabellen") + " · " + found.joinToString(", ") { tr("S.") + " ${it.page + 1}: ${it.rows.size}×${it.columns}" }, style = MaterialTheme.typography.bodySmall)
                        Text(
                            found.first().markdown().lines().take(8).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val title = tab.name.removeSuffix(".pdf").removeSuffix(".PDF") + " (Tabellen)"
                                    runCatching { PdfConvert.fromPdf(context, tab.file, title, DocFormat.XLSX, File(PdfWork.dir(context), "export")) }
                                        .onSuccess(saveXlsx).onFailure { Notices.error(it.message ?: tr("Das hat nicht geklappt")) }
                                }
                            }) { Text(tr("Als Excel speichern")) }
                            OutlinedButton(onClick = {
                                // One table document with a sheet per table.
                                val sheets = found.mapIndexed { i, t ->
                                    io.github.thepytales.llmhub.docs.DocTab(title = tr("Tabelle") + " ${i + 1} (" + tr("S.") + " ${t.page + 1})", markdown = io.github.thepytales.llmhub.docs.SheetEngine.markdown(t.rows))
                                }
                                val doc = io.github.thepytales.llmhub.docs.LiveDoc.create(tab.name.removeSuffix(".pdf").removeSuffix(".PDF") + " (Tabellen)", sheets.first().markdown, "Sie", kind = io.github.thepytales.llmhub.docs.DocKind.SHEET)
                                    .let { it.copy(tabs = sheets, vault = c.docs.defaultVault, versions = it.versions.map { v -> v.copy(tabs = sheets) }) }
                                c.docs.save(doc)
                                DocsHandoff.show(doc.id)
                            }) { Text(tr("Im Editor öffnen")) }
                        }
                    }
                }
            }
        }
    }
}
