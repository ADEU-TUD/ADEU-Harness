package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.PiiDetector
import io.github.thepytales.llmhub.data.PiiKind
import io.github.thepytales.llmhub.docs.MarkdownDocx
import io.github.thepytales.llmhub.docs.PdfCompare
import io.github.thepytales.llmhub.docs.PdfExtras
import io.github.thepytales.llmhub.docs.PdfImageInfo
import io.github.thepytales.llmhub.docs.PdfMeta
import io.github.thepytales.llmhub.docs.PdfRedaction
import io.github.thepytales.llmhub.docs.PdfStage3
import io.github.thepytales.llmhub.docs.PdfSigning
import io.github.thepytales.llmhub.docs.PdfBox
import io.github.thepytales.llmhub.docs.PdfPoint
import io.github.thepytales.llmhub.docs.PdfSnap
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.docs.PdfTagReport
import io.github.thepytales.llmhub.docs.PdfSignatureInfo
import io.github.thepytales.llmhub.docs.PdfUaFinding
import io.github.thepytales.llmhub.files.FileText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF-Werkzeugkasten Stufe 3 (AP-26): Schwärzen, Text ersetzen, Bilder, Verkleinern, PDF/A, Passwort, Metadaten,
 * Barrierefreiheit (PDF/UA) and Vergleichen. Every change becomes a new version of the tab (Rückgängig as usual). On
 * Android only „Vergleichen“; the rest is in the Windows app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PdfStage3Tools(c: AppContainer, tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (PdfStage3.SUPPORTED) {
                RedactCard(c, tab, onRun)
                if (PdfSigning.SUPPORTED) SignatureCard(tab, onRun)
                ReplaceTextCard(tab, onRun)
                ImagesCard(tab, onRun)
                ShrinkCard(tab, onRun)
                ArchiveCard(tab, onRun)
                PasswordCard(tab, onRun)
                MetaCard(tab, onRun)
                UaCard(tab, onRun)
                TagsCard(tab, onRun)
            } else {
                // AP-64: „Verkleinern“ also on Android (scans as mail attachments).
                if (PdfStage3.SHRINK) ShrinkCard(tab, onRun)
                HintText(tr("Schwärzen, Text und Bilder ändern, PDF/A, Passwort, Metadaten und die PDF/UA-Prüfung gibt es in der Windows-App."))
            }
            CompareCard(c, tab)
        }
    }
}

/** Runs [block] off the main thread and reports a failure. */
private suspend fun <T> work(block: () -> T): T? =
    withContext(Dispatchers.IO) { runCatching(block) }.onFailure { Notices.error(tr("Das hat nicht geklappt") + ": " + (it.message ?: it.javaClass.simpleName)) }.getOrNull()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedactCard(c: AppContainer, tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var terms by remember { mutableStateOf("") }
    var hits by remember(tab.file) { mutableStateOf<List<PdfRedaction>?>(null) }
    var clean by remember { mutableStateOf(true) }
    var mouse by remember { mutableStateOf(false) }
    SettingsCard(tr("Schwärzen")) {
        SettingsHint(tr("Echtes Schwärzen: Seiten mit schwarzen Balken werden als Bild neu gezeichnet – der Text darunter ist aus der Datei verschwunden, nicht nur verdeckt. Andere Seiten bleiben, wie sie sind."))
        OutlinedTextField(
            value = terms, onValueChange = { terms = it; hits = null }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("Was geschwärzt wird")) }, supportingText = { Text(tr("Ein Begriff je Zeile oder durch Komma getrennt, z. B. Namen, Matrikelnummern, Adressen.")) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val texts = work { PdfExtras.pageTexts(context, tab.file) } ?: return@launch
                    val own = c.settings.current.pseudonymTerms.lines().map { it.trim() }.filter { it.length >= 2 }
                    val found = PiiDetector.find(texts.joinToString("\n"), own).filter { it.kind != PiiKind.TERM || it.value in own }.map { it.value.trim() }.filter { it.length >= 2 }.distinct()
                    if (found.isEmpty()) Notices.info(tr("Keine personenbezogenen Angaben gefunden.")) else terms = (terms.lines().filter { it.isNotBlank() } + found).distinct().joinToString("\n")
                    hits = null
                }
            }) { Text(tr("Personenbezogenes vorschlagen")) }
            OutlinedButton(enabled = terms.isNotBlank(), onClick = {
                scope.launch { hits = work { PdfStage3.find(tab.file, terms.split(Regex("[\\n,;]"))) } }
            }) { Text(tr("Stellen suchen")) }
            // AP-60: on the page itself – drag a rectangle or click a word.
            OutlinedButton(onClick = { mouse = true }) { Text(tr("Mit der Maus schwärzen …")) }
        }
        if (mouse) MouseRedactDialog(tab, clean, onDismiss = { mouse = false }) { boxes ->
            mouse = false
            onRun { src, out -> PdfStage3.redact(src, boxes, out, cleanMetadata = clean) }
        }
        hits?.let { list ->
            if (list.isEmpty()) HintText(tr("Nichts gefunden – bei Scans erst „Umwandeln › Texterkennung“."))
            else {
                Text("${list.size} " + tr("Stellen auf") + " ${list.map { it.page }.distinct().size} " + tr("Seiten") + ": " +
                    list.groupBy { it.text.lowercase() }.entries.take(8).joinToString(", ") { "„${it.value.first().text}“ ×${it.value.size}" }, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clean, onCheckedChange = { clean = it })
                    Text(tr("Auch Titel, Autor und versteckte Metadaten entfernen"), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onRun { src, out -> PdfStage3.redact(src, list, out, cleanMetadata = clean) }; hits = null }) { Text(tr("Schwärzen") + " (${list.size})") }
            }
        }
    }
}

@Composable
private fun ReplaceTextCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    SettingsCard(tr("Text ändern")) {
        SettingsHint(tr("Ersetzt einen Text an jeder Stelle: weiß überdeckt, der neue Text in Helvetica darüber. Der alte Text bleibt in der Datei – für Vertrauliches „Schwärzen“ nutzen."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = find, onValueChange = { find = it }, singleLine = true, label = { Text(tr("Suchen")) }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = replace, onValueChange = { replace = it }, singleLine = true, label = { Text(tr("Ersetzen durch")) }, modifier = Modifier.weight(1f))
        }
        Button(enabled = find.length >= 2, onClick = {
            onRun { src, out -> if (PdfStage3.replaceText(src, find, replace, null, out) == 0) error(tr("„{0}“ kommt im Text nicht vor.", find)) }
        }) { Text(tr("Ersetzen")) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagesCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var images by remember(tab.file) { mutableStateOf<List<PdfImageInfo>?>(null) }
    var target by remember { mutableStateOf<PdfImageInfo?>(null) }
    val pick = rememberImageBytesPicker { bytes ->
        val t = target ?: return@rememberImageBytesPicker
        if (bytes != null) onRun { src, out -> PdfStage3.replaceImage(src, t.page, t.name, bytes, out) }
        target = null
    }
    SettingsCard(tr("Bilder")) {
        val list = images
        if (list == null) OutlinedButton(onClick = { scope.launch { images = work { PdfStage3.images(tab.file) } } }) { Text(tr("Bilder anzeigen")) }
        else if (list.isEmpty()) HintText(tr("Die Datei enthält keine Bilder."))
        else list.take(60).forEach { img ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, img, tab.file) {
                    value = withContext(Dispatchers.IO) { runCatching { PdfStage3.imagePreview(tab.file, img.page, img.name)?.let { decodeImage(it) } }.getOrNull() }
                }
                preview?.let { Image(it, contentDescription = null, modifier = Modifier.size(56.dp)) } ?: Spacer(Modifier.size(56.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Seite") + tr(" {0} · {1} × {2} px", img.page + 1, img.width, img.height), style = MaterialTheme.typography.bodyMedium)
                    Text(io.github.thepytales.llmhub.data.NextcloudService.size(img.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { target = img; pick() }) { Text(tr("Ersetzen …")) }
                TextButton(onClick = { onRun { src, out -> PdfStage3.replaceImage(src, img.page, img.name, null, out) }; images = null }) { Text(tr("Entfernen")) }
            }
        }
    }
}

@Composable
private fun ShrinkCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    var strong by remember { mutableStateOf(false) }
    var result by remember(tab.file) { mutableStateOf<String?>(null) }
    SettingsCard(tr("Verkleinern")) {
        SettingsHint(tr("Große Bilder werden kleiner gerechnet und als JPEG gespeichert, die Datei komprimiert – gut für Mailanhänge und OPAL."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !strong, onClick = { strong = false }, label = { Text(tr("Mittel (Bildschirm und Druck)")) })
            FilterChip(selected = strong, onClick = { strong = true }, label = { Text(tr("Stark (Bildschirm)")) })
        }
        Button(onClick = {
            val before = tab.file.length()
            onRun { src, out -> PdfStage3.shrink(src, out, maxPx = if (strong) 1000 else 1600, quality = if (strong) 0.6f else 0.75f) }
            result = tr("Vorher") + " " + io.github.thepytales.llmhub.data.NextcloudService.size(before)
        }) { Text(tr("Verkleinern")) }
        result?.let { Text(it + " · " + tr("jetzt") + " " + io.github.thepytales.llmhub.data.NextcloudService.size(tab.file.length()), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ArchiveCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    var problems by remember(tab.file) { mutableStateOf<List<String>?>(null) }
    SettingsCard(tr("PDF/A (Archiv)")) {
        SettingsHint(tr("Für die Langzeitarchivierung (z. B. Abschlussarbeiten, Akten): PDF/A-2b mit Metadaten und Farbprofil, ohne Verschlüsselung, JavaScript und eingebettete Dateien."))
        Button(onClick = {
            onRun { src, out -> problems = PdfStage3.toPdfA(src, tab.name.substringBeforeLast('.'), out) }
        }) { Text(tr("Als PDF/A-2b vorbereiten")) }
        problems?.let { p ->
            if (p.isEmpty()) StatusLine(true, tr("Fertig – die üblichen Anforderungen sind erfüllt. Streng prüfen lässt es sich z. B. mit veraPDF."))
            else p.forEach { Text("• " + it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PasswordCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    var open by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(true) }
    var copy by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf("") }
    SettingsCard(tr("Passwort")) {
        SettingsHint(tr("Schützt die Datei mit AES-256. Mit Öffnen-Passwort lässt sie sich nur mit Passwort öffnen; sonst lassen sich nur Drucken und Kopieren einschränken. Screenreader dürfen immer lesen."))
        OutlinedTextField(value = open, onValueChange = { open = it }, singleLine = true, label = { Text(tr("Passwort zum Öffnen (optional)")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = owner, onValueChange = { owner = it }, singleLine = true, label = { Text(tr("Passwort für die Rechte")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = print, onCheckedChange = { print = it }); Text(tr("Drucken erlauben"))
            Spacer(Modifier.size(12.dp))
            Checkbox(checked = copy, onCheckedChange = { copy = it }); Text(tr("Kopieren erlauben"))
        }
        Button(enabled = open.isNotBlank() || owner.isNotBlank(), onClick = { onRun { src, out -> PdfStage3.protect(src, open, owner, print, copy, out) }; open = ""; owner = "" }) { Text(tr("Schützen")) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = remove, onValueChange = { remove = it }, singleLine = true, label = { Text(tr("Vorhandenes Passwort")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onRun { src, out -> PdfStage3.unprotect(src, remove, out) }; remove = "" }) { Text(tr("Schutz entfernen")) }
        }
    }
}

@Composable
private fun MetaCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val meta by androidx.compose.runtime.produceState<PdfMeta?>(null, tab.file) { value = withContext(Dispatchers.IO) { runCatching { PdfStage3.meta(tab.file) }.getOrNull() } }
    SettingsCard(tr("Metadaten")) {
        val m = meta
        if (m == null) { HintText(tr("Wird gelesen …")); return@SettingsCard }
        var title by remember(m) { mutableStateOf(m.title) }
        var author by remember(m) { mutableStateOf(m.author) }
        var subject by remember(m) { mutableStateOf(m.subject) }
        var keywords by remember(m) { mutableStateOf(m.keywords) }
        var language by remember(m) { mutableStateOf(m.language) }
        if (m.creator.isNotBlank() || m.producer.isNotBlank()) Text(tr("Erstellt mit") + " " + listOf(m.creator, m.producer).filter { it.isNotBlank() }.distinct().joinToString(" · ") + if (m.xmp) " · " + tr("mit XMP-Daten") else "", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text(tr("Titel")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = author, onValueChange = { author = it }, singleLine = true, label = { Text(tr("Autor:in")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = subject, onValueChange = { subject = it }, singleLine = true, label = { Text(tr("Thema")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = keywords, onValueChange = { keywords = it }, singleLine = true, label = { Text(tr("Stichwörter")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = language, onValueChange = { language = it }, singleLine = true, label = { Text(tr("Sprache (z. B. de-DE)")) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRun { src, out -> PdfStage3.setMeta(src, m.copy(title = title, author = author, subject = subject, keywords = keywords, language = language), false, out) } }) { Text(tr("Speichern")) }
            OutlinedButton(onClick = { onRun { src, out -> PdfStage3.setMeta(src, PdfMeta(language = language), true, out) } }) { Text(tr("Alles entfernen")) }
        }
    }
}

@Composable
private fun UaCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var findings by remember(tab.file) { mutableStateOf<List<PdfUaFinding>?>(null) }
    SettingsCard(tr("Barrierefreiheit (PDF/UA)")) {
        SettingsHint(tr("Prüft die Punkte von PDF/UA, die sich ohne Menschen prüfen lassen: Tags, Sprache, Titel, Schriften, Text statt Scan, Alternativtexte, Formularfelder, Lesezeichen, Tab-Reihenfolge."))
        OutlinedButton(onClick = { scope.launch { findings = work { PdfStage3.uaCheck(context, tab.file) } } }) { Text(tr("Prüfen")) }
        findings?.let { list ->
            list.forEach { f -> Text((if (f.level == PdfUaFinding.Level.OK) "✓ " else "✗ ") + tr(f.text), style = MaterialTheme.typography.bodySmall, color = if (f.level == PdfUaFinding.Level.OK) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }
            if (list.any { it.fixable }) Button(onClick = { onRun { src, out -> PdfStage3.uaFix(context, src, tab.name.substringBeforeLast('.'), out) }; findings = null }) { Text(tr("Einfache Mängel beheben")) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareCard(c: AppContainer, tab: PdfTab) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val others = PdfWork.tabs.filter { it.id != tab.id }
    var result by remember(tab.file) { mutableStateOf<Pair<String, PdfCompare.Result>?>(null) }
    val saveDocx = rememberSaveAs("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    SettingsCard(tr("Zwei PDFs vergleichen")) {
        SettingsHint(tr("Vergleicht den Text dieser Datei (alt) mit einer anderen offenen (neu), Seite für Seite und Wort für Wort."))
        if (others.isEmpty()) HintText(tr("Öffne die zweite Datei als weiteren Tab (Ordner-Symbol oben)."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            others.forEach { o ->
                OutlinedButton(onClick = {
                    scope.launch {
                        val r = work { PdfCompare.compare(PdfExtras.pageTexts(context, tab.file), PdfExtras.pageTexts(context, o.file)) } ?: return@launch
                        result = o.name to r
                    }
                }) { Text(tr("mit") + " " + o.name) }
            }
        }
        result?.let { (name, r) ->
            if (r.same) StatusLine(true, tr("Der Text ist gleich."))
            else {
                Text("${r.changes.size} " + tr("Änderungen auf") + " ${r.changedPages.size} " + tr("Seiten") + if (r.pagesA != r.pagesB) " · ${r.pagesA} → ${r.pagesB} " + tr("Seiten") else "", style = MaterialTheme.typography.titleSmall)
                r.changes.take(80).forEach { ch ->
                    Text(
                        tr("S.") + " ${ch.page + 1}: " + when {
                            ch.removed.isEmpty() -> "+ " + ch.added
                            ch.added.isEmpty() -> "− " + ch.removed
                            else -> "„${ch.removed}“ → „${ch.added}“"
                        }.take(300),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        work {
                            File(File(c.appContext.cacheDir, "vergleich"), FileText.safeName("Vergleich ${tab.name.substringBeforeLast('.')}.docx")).apply { parentFile?.mkdirs() }
                                .also { MarkdownDocx.write(it, "Vergleich", PdfCompare.markdown(tab.name, name, r)) }
                        }?.let(saveDocx)
                    }
                }) { Text(tr("Als Word speichern")) }
            }
        }
    }
}

/**
 * Schwärzen mit der Maus (AP-60): the pages large, a dragged rectangle or a click on a word becomes a black box; the
 * boxes are burnt in with „Schwärzen“ (the same echte Schwärzen as above).
 */
@Composable
private fun MouseRedactDialog(tab: PdfTab, clean: Boolean, onDismiss: () -> Unit, onApply: (List<PdfRedaction>) -> Unit) {
    val context = LocalContext.current
    val info = tab.info ?: return
    val boxes = remember { androidx.compose.runtime.mutableStateListOf<PdfRedaction>() }
    val words by androidx.compose.runtime.produceState<List<io.github.thepytales.llmhub.docs.PdfWord>>(emptyList(), tab.file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfTools.words(context, tab.file) }.getOrDefault(emptyList()) }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(0.94f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Mit der Maus schwärzen"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(enabled = boxes.isNotEmpty(), onClick = { boxes.removeAt(boxes.lastIndex) }) { Text(tr("Rückgängig")) }
                    TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) }
                    Button(enabled = boxes.isNotEmpty(), onClick = { onApply(boxes.toList()) }) { Text(tr("Schwärzen") + " (${boxes.size})") }
                }
                HintText(tr("Rechteck ziehen oder auf ein Wort klicken. Die Balken werden erst mit „Schwärzen“ eingebrannt – danach ist der Text darunter aus der Datei verschwunden.") + if (clean) " " + tr("Metadaten werden mit entfernt.") else "")
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PageCanvasList(tab, info) { page, w, h ->
                        var start by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                        var end by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize()
                                .pointerInput(page, w, h, words) {
                                    detectTapGestures { p ->
                                        val word = PdfSnap.wordAt(words.filter { it.page == page }, PdfPoint(p.x / w, p.y / h))
                                        if (word != null) boxes += PdfRedaction(page, word.box, word.text)
                                    }
                                }
                                .pointerInput(page, w, h) {
                                    detectDragGestures(
                                        onDragStart = { start = it; end = it },
                                        onDrag = { ch, d -> ch.consume(); end = (end ?: androidx.compose.ui.geometry.Offset.Zero) + d },
                                        onDragEnd = {
                                            val a = start; val b = end
                                            if (a != null && b != null && kotlin.math.abs(a.x - b.x) > 4 && kotlin.math.abs(a.y - b.y) > 4) {
                                                boxes += PdfRedaction(page, PdfBox(minOf(a.x, b.x) / w, minOf(a.y, b.y) / h, kotlin.math.abs(a.x - b.x) / w, kotlin.math.abs(a.y - b.y) / h))
                                            }
                                            start = null; end = null
                                        },
                                        onDragCancel = { start = null; end = null },
                                    )
                                },
                        ) {
                            boxes.filter { it.page == page }.forEach { r ->
                                drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f), topLeft = androidx.compose.ui.geometry.Offset(r.box.x * w, r.box.y * h), size = androidx.compose.ui.geometry.Size(r.box.w * w, r.box.h * h))
                            }
                            val a = start; val b = end
                            if (a != null && b != null) drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), topLeft = androidx.compose.ui.geometry.Offset(minOf(a.x, b.x), minOf(a.y, b.y)), size = androidx.compose.ui.geometry.Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Digitale Signatur (AP-60): sign with a certificate of the Windows certificate store or a .p12/.pfx file, optionally
 * with a visible field, and check the signatures already in the PDF.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignatureCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var checks by remember(tab.file) { mutableStateOf<List<PdfSignatureInfo>?>(null) }
    val store by androidx.compose.runtime.produceState<List<io.github.thepytales.llmhub.docs.PdfCert>?>(null) { value = withContext(Dispatchers.IO) { runCatching { PdfSigning.storeCertificates() }.getOrDefault(emptyList()) } }
    var keyBytes by remember { mutableStateOf<ByteArray?>(null) }
    var password by remember { mutableStateOf("") }
    var fileCerts by remember { mutableStateOf<List<io.github.thepytales.llmhub.docs.PdfCert>>(emptyList()) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("Dresden") }
    var visible by remember { mutableStateOf(true) }
    val pickKey = rememberFileImport(arrayOf("application/x-pkcs12", "*/*")) { input -> keyBytes = input.readBytes(); fileCerts = emptyList(); chosen = null }
    SettingsCard(tr("Digital signieren")) {
        SettingsHint(tr("Signiert das PDF mit einem Zertifikat (z. B. dem persönlichen Zertifikat der TU) – jede spätere Änderung wird sichtbar. Der private Schlüssel bleibt im Zertifikatsspeicher von Windows bzw. in der Datei."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { scope.launch { checks = work { PdfSigning.verify(tab.file) } } }) { Text(tr("Signaturen prüfen")) }
        }
        checks?.let { list ->
            if (list.isEmpty()) HintText(tr("Dieses PDF ist nicht digital signiert."))
            list.forEach { ch ->
                val ok = ch.valid && ch.coversAll && ch.trusted
                Text(
                    (if (ok) "✓ " else if (ch.valid) "⚠ " else "✗ ") + ch.signer + (ch.time?.let { " · " + java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(it)) } ?: "") +
                        (ch.reason?.let { " · $it" } ?: "") + (ch.problem?.let { " – $it" } ?: " – " + tr("gültig, unverändert")),
                    style = MaterialTheme.typography.bodySmall, color = if (ch.valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
        val certs = store.orEmpty().map { it to true } + fileCerts.map { it to false }
        if (store != null && store!!.isEmpty()) HintText(tr("Im Zertifikatsspeicher von Windows ist kein Zertifikat mit privatem Schlüssel – eine .p12/.pfx-Datei geht auch."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            certs.forEach { (cert, fromStore) ->
                FilterChip(selected = chosen == cert.alias, onClick = { chosen = cert.alias }, label = { Text(cert.subject + " (" + (if (fromStore) tr("Windows") else tr("Datei")) + ")") })
            }
            OutlinedButton(onClick = pickKey) { Text(tr("Zertifikatsdatei (.p12/.pfx) …")) }
        }
        if (keyBytes != null && fileCerts.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(password, { password = it }, singleLine = true, label = { Text(tr("Passwort der Datei")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
            Button(onClick = { scope.launch { fileCerts = work { PdfSigning.fileCertificates(keyBytes!!, password) }.orEmpty(); chosen = fileCerts.firstOrNull()?.alias } }) { Text(tr("Öffnen")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(reason, { reason = it }, singleLine = true, label = { Text(tr("Grund (optional)")) }, modifier = Modifier.weight(1f))
            OutlinedTextField(location, { location = it }, singleLine = true, label = { Text(tr("Ort")) }, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(visible, { visible = it })
            Text(tr("Sichtbares Feld unten rechts auf der letzten Seite"), style = MaterialTheme.typography.bodySmall)
        }
        Button(enabled = chosen != null, onClick = {
            val alias = chosen ?: return@Button
            val fromFile = fileCerts.any { it.alias == alias } && store.orEmpty().none { it.alias == alias }
            val pages = tab.info?.pages ?: 1
            onRun { src, out -> PdfSigning.sign(src, out, alias, if (fromFile) keyBytes else null, if (fromFile) password else null, reason, location, if (visible) (pages - 1) to PdfBox(0.55f, 0.86f, 0.38f, 0.08f) else null) }
            checks = null
        }) { Text(tr("Digital signieren")) }
        HintText(tr("Danach nichts mehr am PDF ändern – sonst zeigt die Prüfung, dass nach dem Signieren etwas geändert wurde."))
    }
}

/**
 * Tags erzeugen (AP-60): an untagged PDF gets headings, paragraphs, lists and figures (with the alternative texts
 * entered here), the rest becomes decoration – then screen readers can read it in order.
 */
@Composable
private fun TagsCard(tab: PdfTab, onRun: ((File, File) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var images by remember(tab.file) { mutableStateOf<List<PdfImageInfo>?>(null) }
    val alts = remember(tab.file) { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var language by remember { mutableStateOf("de-DE") }
    var report by remember(tab.file) { mutableStateOf<PdfTagReport?>(null) }
    SettingsCard(tr("Tags erzeugen (PDF/UA)")) {
        SettingsHint(tr("Für PDFs ohne Tags: Überschriften (nach Schriftgröße), Absätze, Listen und Abbildungen werden ausgezeichnet, Linien und Seitenzahlen als Dekoration. Tabellen erkennt Lora noch nicht – sie werden zu Absätzen."))
        if (images == null) OutlinedButton(onClick = { scope.launch { images = work { PdfStage3.images(tab.file) } } }) { Text(tr("Abbildungen beschreiben …")) }
        images?.let { list ->
            if (list.isEmpty()) HintText(tr("Keine Abbildungen."))
            list.forEach { img ->
                val key = "${img.page}:${img.name}"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val preview by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, img) {
                        value = withContext(Dispatchers.IO) { runCatching { PdfStage3.imagePreview(tab.file, img.page, img.name)?.let { decodeImage(it) } }.getOrNull() }
                    }
                    preview?.let { Image(it, null, Modifier.size(56.dp)) }
                    OutlinedTextField(alts[key].orEmpty(), { alts[key] = it }, singleLine = true, label = { Text(tr("Alternativtext, Seite {0}", img.page + 1)) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(language, { language = it }, singleLine = true, label = { Text(tr("Sprache")) }, modifier = Modifier.widthIn(max = 160.dp))
            Button(onClick = {
                val a = alts.toMap()
                onRun { src, out -> report = PdfStage3.autoTag(src, out, tab.name.substringBeforeLast('.'), language.ifBlank { "de-DE" }, a) }
            }) { Text(tr("Tags erzeugen")) }
        }
        report?.let { r ->
            Text(tr("{0} Überschriften, {1} Absätze, {2} Listenpunkte, {3} Abbildungen, {4} Teile als Dekoration.", r.headings, r.paragraphs, r.listItems, r.figures, r.artifacts), style = MaterialTheme.typography.bodySmall)
            if (r.figuresWithoutAlt > 0) Text(tr("{0} Abbildung(en) ohne Alternativtext – oben beschreiben und neu erzeugen (auf dem Original).", r.figuresWithoutAlt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
