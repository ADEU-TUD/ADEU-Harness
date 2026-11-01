package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                ReplaceTextCard(tab, onRun)
                ImagesCard(tab, onRun)
                ShrinkCard(tab, onRun)
                ArchiveCard(tab, onRun)
                PasswordCard(tab, onRun)
                MetaCard(tab, onRun)
                UaCard(tab, onRun)
            } else HintText(tr("Schwärzen, Text und Bilder ändern, Verkleinern, PDF/A, Passwort, Metadaten und die PDF/UA-Prüfung gibt es in der Windows-App."))
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
