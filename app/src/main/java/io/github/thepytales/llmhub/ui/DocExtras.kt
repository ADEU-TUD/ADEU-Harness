package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.HtmlPdf
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SlideHandout
import io.github.thepytales.llmhub.docs.SpellCheck
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.Reference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Folder the pictures of the open document are relative to (set by the editor). */
val LocalDocBase = staticCompositionLocalOf<File?> { null }

/** A picture of the document, loaded in the background; null while loading or when it cannot be read. */
@Composable
fun rememberDocImage(src: String): ImageBitmap? {
    val base = LocalDocBase.current
    val state = produceState<ImageBitmap?>(null, src, base) {
        value = withContext(Dispatchers.IO) { DocMarkup.imageBytes(src, base)?.let { runCatching { decodeImage(it) }.getOrNull() } }
    }
    return state.value
}

/** A picture block on the page: at its width, centred, the caption below it. */
@Composable
fun DocImageView(img: DocMarkup.Image, modifier: Modifier = Modifier) {
    val bitmap = rememberDocImage(img.src)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bitmap != null) {
            Image(
                bitmap, contentDescription = img.alt.ifBlank { tr("Bild") }, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth((img.width ?: 100) / 100f).heightIn(max = 520.dp),
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth(0.6f).height(90.dp)) {
                Box(contentAlignment = Alignment.Center) { HintText(tr("Bild nicht gefunden: {0}", img.src.substringAfterLast('/'))) }
            }
        }
        if (img.alt.isNotBlank()) Text(
            img.alt, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Header and footer of the exported pages: page numbers, date, header line. */
@Composable
fun PageSetupDialog(setup: DocMarkup.PageSetup, onDismiss: () -> Unit, onSave: (DocMarkup.PageSetup) -> Unit) {
    var numbers by remember { mutableStateOf(setup.pageNumbers) }
    var date by remember { mutableStateOf(setup.date) }
    var header by remember { mutableStateOf(setup.header) }
    var footer by remember { mutableStateOf(setup.footer) }
    var margins by remember { mutableStateOf(setup.margins) }
    var titlePage by remember { mutableStateOf(setup.titlePage) }
    var headerFirst by remember { mutableStateOf(setup.headerFirst) }
    var footerFirst by remember { mutableStateOf(setup.footerFirst) }
    var evenPages by remember { mutableStateOf(setup.hasEven) }
    var headerEven by remember { mutableStateOf(setup.headerEven) }
    var footerEven by remember { mutableStateOf(setup.footerEven) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Seite einrichten")) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HintText(tr("Gilt für PDF und Word. Seitenumbrüche (\\newpage) und Querformat-Seiten fügst du über „Einfügen“ ein."))
                Row(Modifier.fillMaxWidth().clickable { numbers = !numbers }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(numbers, { numbers = it })
                    Text(tr("Seitenzahlen „Seite X von Y“ in der Fußzeile"))
                }
                Row(Modifier.fillMaxWidth().clickable { date = !date }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(date, { date = it })
                    Text(tr("Datum in der Kopfzeile"))
                }
                OutlinedTextField(header, { header = it }, label = { Text(tr("Kopfzeile (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(footer, { footer = it }, label = { Text(tr("Fußzeile (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HintText(tr("{seite} und {seiten} in Kopf- oder Fußzeile werden zur Seitenzahl und zur Zahl der Seiten."))
                // Erste Seite anders (Deckblatt) and gerade Seiten anders, like Word (AP-45).
                Row(Modifier.fillMaxWidth().clickable { titlePage = !titlePage }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(titlePage, { titlePage = it })
                    Text(tr("Erste Seite anders (Deckblatt ohne Kopf-/Fußzeile und Seitenzahl)"))
                }
                if (titlePage) {
                    OutlinedTextField(headerFirst, { headerFirst = it }, label = { Text(tr("Kopfzeile der ersten Seite (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(footerFirst, { footerFirst = it }, label = { Text(tr("Fußzeile der ersten Seite (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(Modifier.fillMaxWidth().clickable { evenPages = !evenPages }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(evenPages, { evenPages = it })
                    Text(tr("Gerade Seiten anders"))
                }
                if (evenPages) {
                    OutlinedTextField(headerEven, { headerEven = it }, label = { Text(tr("Kopfzeile gerader Seiten")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(footerEven, { footerEven = it }, label = { Text(tr("Fußzeile gerader Seiten")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text(tr("Seitenränder"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("schmal" to tr("Schmal (1,27 cm)"), "normal" to tr("Normal (2 cm)"), "breit" to tr("Breit (2,54 cm)")).forEach { (k, label) ->
                        FilterChip(margins == k, { margins = k }, label = { Text(label) })
                    }
                    // A width set by dragging the ruler (AP-44).
                    DocMarkup.customMm(margins)?.let { mm -> FilterChip(true, {}, label = { Text(tr("Eigene ({0} mm)", mm)) }) }
                }
                HintText(tr("Ein Inhaltsverzeichnis fügst du über „Einfügen › Inhaltsverzeichnis“ ein – es listet die Überschriften, Word ergänzt die Seitenzahlen."))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(
                    DocMarkup.PageSetup(
                        numbers, date, header.trim(), footer.trim(), margins,
                        titlePage = titlePage, headerFirst = if (titlePage) headerFirst.trim() else "", footerFirst = if (titlePage) footerFirst.trim() else "",
                        headerEven = if (evenPages) headerEven.trim() else "", footerEven = if (evenPages) footerEven.trim() else "",
                    ),
                ) }) { Text(tr("Übernehmen")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Picks a work from the literature collection and the citation style for "Literatur einfügen". */
@Composable
fun CitationDialog(c: AppContainer, initialStyle: CitationStyle, onDismiss: () -> Unit, onPick: (Reference, CitationStyle) -> Unit) {
    val all by c.literature.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(initialStyle) }
    var styles by remember { mutableStateOf(false) }
    val list = all.filter { r -> query.isBlank() || r.short().contains(query, true) || r.container.contains(query, true) }
        .sortedByDescending { it.addedAt }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Literatur einfügen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HintText(tr("Setzt den Beleg an der Cursorposition ein und pflegt „Literatur“ am Ende des Dokuments automatisch."))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Zitierstil: "), style = MaterialTheme.typography.labelLarge)
                    Box {
                        TextButton(onClick = { styles = true }) { Text(style.label) }
                        DropdownMenu(expanded = styles, onDismissRequest = { styles = false }) {
                            CitationStyle.entries.forEach { st ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(st.label) }, onClick = { style = st; styles = false })
                            }
                        }
                    }
                }
                OutlinedTextField(query, { query = it }, placeholder = { Text(tr("Suchen (Autor:in, Titel, Jahr)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (all.isEmpty()) HintText(tr("Die Literatursammlung ist noch leer – Werke unter „Literatur“ hinzufügen oder suchen lassen."))
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(list.take(200), key = { it.id }) { r ->
                        Text(
                            r.short(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(r, style) }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/**
 * The model's spelling and grammar check of [text]: suggestions to take one by one or all at once. [onApply] gets
 * each accepted correction.
 */
@Composable
fun SpellCheckDialog(c: AppContainer, doc: LiveDoc?, text: String, onDismiss: () -> Unit, onApply: (List<SpellCheck.Correction>) -> Unit) {
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val suggestions = remember { mutableStateListOf<SpellCheck.Correction>() }
    LaunchedEffect(text) {
        runCatching { DocsAi.spellCheck(c, doc, text) }
            .onSuccess { suggestions.clear(); suggestions += SpellCheck.applicable(text, it) }
            .onFailure { e -> if (e !is kotlinx.coroutines.CancellationException) error = e.message ?: tr("Fehler") }
        busy = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Rechtschreibung prüfen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(tr("  Die KI prüft den Text …"))
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    suggestions.isEmpty() -> Text(tr("Keine Fehler gefunden."))
                    else -> HintText(tr("{0} Vorschläge – einzeln übernehmen oder alle auf einmal.", suggestions.size))
                }
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions.toList(), key = { it.original + "→" + it.korrektur }) { s ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.original, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough))
                                    Text("  →  ", style = MaterialTheme.typography.bodyMedium)
                                    Text(s.korrektur, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (s.grund.isNotBlank()) HintText(s.grund)
                                Row {
                                    TextButton(onClick = { onApply(listOf(s)); suggestions.remove(s) }) { Text(tr("Übernehmen")) }
                                    TextButton(onClick = { suggestions.remove(s) }) { Text(tr("Ignorieren")) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (suggestions.size > 1) TextButton(onClick = { onApply(suggestions.toList()); suggestions.clear(); onDismiss() }) { Text(tr("Alle übernehmen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/** Handout of a presentation as PDF: 3 or 6 slides per page, with or without the speaker notes. */
@Composable
fun HandoutDialog(c: AppContainer, doc: LiveDoc, markdown: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var perPage by remember { mutableStateOf(3) }
    var notes by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val saveAs = rememberSaveAs("application/pdf")

    fun make(then: (File) -> Unit) {
        busy = true
        scope.launch {
            val result = runCatching {
                val settings = c.settings.current
                val html = withContext(Dispatchers.Default) {
                    SlideHandout.html(markdown, doc.title, perPage, notes, settings.tudUnit, settings.docFooter, c.docs.baseDir(doc))
                }
                val file = withContext(Dispatchers.IO) {
                    File(File(context.cacheDir, "exports").apply { mkdirs() }, DocumentWriter.safeName("${doc.title} – Handout") + ".pdf")
                }
                HtmlPdf.write(context, file, html)
                file
            }
            busy = false
            result.onSuccess(then).onFailure { Notices.error(tr("Handout fehlgeschlagen: {0}", it.message)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Handout als PDF")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(3 to tr("3 Folien pro Seite (daneben Notizen oder Linien)"), 6 to tr("6 Folien pro Seite")).forEach { (n, label) ->
                    Row(Modifier.fillMaxWidth().clickable { perPage = n }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(perPage == n, { perPage = n })
                        Text(label)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { notes = !notes }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(notes, { notes = it })
                    Text(tr("Sprechernotizen drucken"))
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { make { openDocument(context, it) } }) { Text(tr("Öffnen")) }
                TextButton(enabled = !busy, onClick = { make(saveAs) }) { Text(SAVE_AS_LABEL) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

/**
 * Rechtschreibprüfung on the device (AP-16): the dictionaries of Windows or Android check the text block by block;
 * each word shows its suggestions – tap one to replace it, or ignore it / add it to the dictionary.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SpellCheckDialog(blocks: List<String>, onReplace: (Int, String) -> Unit, onDismiss: () -> Unit, onAskAi: () -> Unit) {
    var lang by remember { mutableStateOf("de-DE") }
    var reload by remember { mutableStateOf(0) }
    val ignored = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val found by androidx.compose.runtime.produceState<List<Pair<Int, io.github.thepytales.llmhub.docs.LocalSpelling.Error>>?>(null, lang, reload) {
        value = null
        if (!io.github.thepytales.llmhub.docs.LocalSpelling.available(lang)) { value = emptyList(); return@produceState }
        val out = mutableListOf<Pair<Int, io.github.thepytales.llmhub.docs.LocalSpelling.Error>>()
        blocks.forEachIndexed { i, b ->
            if (b.trimStart().startsWith("```") || b.trimStart().startsWith("<!--") || io.github.thepytales.llmhub.docs.DocMarkup.image(b) != null) return@forEachIndexed
            val plain = io.github.thepytales.llmhub.docs.DocBlocks.plainOf(b)
            runCatching { io.github.thepytales.llmhub.docs.LocalSpelling.check(plain, lang) }.getOrDefault(emptyList()).forEach { out += i to it }
        }
        value = out
    }
    var none by remember { mutableStateOf(false) }
    LaunchedEffect(lang) { none = !io.github.thepytales.llmhub.docs.LocalSpelling.available(lang) }
    val shown = found?.filter { (_, e) -> e.word !in ignored }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Rechtschreibung")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(lang == "de-DE", { lang = "de-DE" }, label = { Text(tr("Deutsch")) })
                    FilterChip(lang == "en-US", { lang = "en-US" }, label = { Text(tr("Englisch")) })
                }
                when {
                    none -> HintText(tr("Für diese Sprache ist auf dem Gerät kein Wörterbuch eingerichtet (Windows: Einstellungen › Zeit und Sprache › Sprache). Die Prüfung mit der KI geht trotzdem."))
                    shown == null -> HintText(tr("Prüfe …"))
                    shown.isEmpty() -> HintText(tr("Keine Fehler gefunden."))
                    else -> LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown.size) { k ->
                            val (i, e) = shown[k]
                            val plain = io.github.thepytales.llmhub.docs.DocBlocks.plainOf(blocks.getOrNull(i).orEmpty())
                            val from = (e.start - 30).coerceAtLeast(0)
                            val to = (e.start + e.length + 30).coerceAtMost(plain.length)
                            Column {
                                Text(
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        append(if (from > 0) "…" else "")
                                        append(plain.substring(from, e.start))
                                        pushStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(0xFFB3261E), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
                                        append(e.word)
                                        pop()
                                        append(plain.substring((e.start + e.length).coerceAtMost(plain.length), to))
                                        append(if (to < plain.length) "…" else "")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    e.suggestions.take(5).forEach { sug ->
                                        AssistChip(onClick = {
                                            val block = blocks.getOrNull(i) ?: return@AssistChip
                                            val re = Regex("(?<![\\p{L}\\d])" + Regex.escape(e.word) + "(?![\\p{L}\\d])")
                                            val next = re.find(block)?.let { m -> block.replaceRange(m.range, sug) } ?: block
                                            if (next != block) onReplace(i, next)
                                            ignored += e.word
                                        }, label = { Text(sug.ifEmpty { tr("löschen") }) })
                                    }
                                    TextButton(onClick = { ignored += e.word }) { Text(tr("Ignorieren")) }
                                    TextButton(onClick = { ignored += e.word; scope.launch { io.github.thepytales.llmhub.docs.LocalSpelling.learn(e.word, lang) } }) { Text(tr("Ins Wörterbuch")) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Fertig")) } },
        dismissButton = { TextButton(onClick = { onDismiss(); onAskAi() }) { Text(tr("Mit KI prüfen")) } },
    )
}
