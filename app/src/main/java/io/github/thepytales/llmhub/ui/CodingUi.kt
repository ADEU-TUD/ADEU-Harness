package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.transcribe.Code
import io.github.thepytales.llmhub.transcribe.Codebook
import io.github.thepytales.llmhub.transcribe.Intercoder
import io.github.thepytales.llmhub.transcribe.RefiQda
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptCoding
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import io.github.thepytales.llmhub.transcribe.withCoding

fun codeColor(hex: String): Color = runCatching { Color(0xFF000000 or hex.trim().removePrefix("#").take(6).toLong(16)) }.getOrDefault(Color(0xFF1E40AF))

/** Codes of one passage as small colored labels (with the coder's initials when several people coded). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentCodes(coding: TranscriptCoding, codebook: Codebook?, segment: Segment, onClick: () -> Unit) {
    val here = coding.at(segment)
    if (here.isEmpty() || codebook == null) return
    val several = coding.coders().size > 1
    FlowRow(Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp).clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        here.forEach { c ->
            val code = codebook.code(c.codeId) ?: return@forEach
            val color = codeColor(code.color)
            Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), contentColor = color) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(5.dp))
                    Text(code.name + if (several && c.coder.isNotBlank()) " · ${initials(c.coder)}" else "", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun initials(name: String) = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { it.take(1).uppercase() }.ifEmpty { name.take(2) }

/** Codes of the scheme as checkboxes for one passage (the current coder's codings). */
@Composable
fun CodePickerDialog(segment: Segment, coding: TranscriptCoding, codebook: Codebook, coder: String, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val mine = coding.at(segment, coder).map { it.codeId }.toSet()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Codieren") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("„${segment.text.trim().take(220)}“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HintText("Codierer:in: $coder")
                codebook.codes.forEach { code ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(code.id) }.padding(start = if (code.parentId != null) 20.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(code.id in mine, { onToggle(code.id) })
                        Box(Modifier.size(10.dp).clip(CircleShape).background(codeColor(code.color)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(code.name, style = MaterialTheme.typography.bodyMedium)
                            if (code.description.isNotBlank()) Text(code.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}

/**
 * Coding of a transcript: choose or create the coding scheme, the coder, switch the coding mode on, see how
 * often each code was used and which passages it has, compare two coders and export for MAXQDA/ATLAS.ti/NVivo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CodingSheet(
    c: AppContainer,
    t: Transcript,
    codingMode: Boolean,
    onCodingMode: (Boolean) -> Unit,
    onUpdate: (Transcript) -> Unit,
    onJump: (Segment) -> Unit,
    onDismiss: () -> Unit,
) {
    val books by c.codebooks.items.collectAsState()
    val settings by c.settings.settings.collectAsState()
    val coding = TranscriptCoding.of(t)
    val book = books.firstOrNull { it.id == coding.codebookId }
    val clipboard = LocalClipboardManager.current
    var newBook by remember { mutableStateOf(false) }
    var editBook by remember { mutableStateOf<Codebook?>(null) }
    var retrieval by remember { mutableStateOf<Code?>(null) }
    var compare by remember { mutableStateOf<Intercoder.Result?>(null) }
    var exportKind by remember { mutableStateOf("qdpx") }
    val export = rememberFileExport(if (exportKind == "qdpx") "application/zip" else "text/csv", onDone = { ok -> if (ok) Notices.info("Exportiert") else Notices.error("Export fehlgeschlagen") }) { out ->
        if (exportKind == "qdpx") RefiQda.write(out, t, coding, book) else out.write(("﻿" + RefiQda.csv(t, coding, book)).toByteArray(Charsets.UTF_8))
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Codieren", style = MaterialTheme.typography.titleLarge)
            HintText("Manuelles Codieren nach einem Kategoriensystem (z.B. inhaltlich-strukturierende Inhaltsanalyse). Die Codierungen bleiben im Transkript gespeichert.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ChoiceField(
                        "Kategoriensystem", listOf<Pair<String?, String>>(null to "– keins –") + books.map { it.id to "${it.name} (${it.codes.size} Codes)" },
                        coding.codebookId, { id -> onUpdate(t.withCoding(coding.copy(codebookId = id))) },
                    )
                }
                IconButton(onClick = { newBook = true }) { Icon(Icons.Outlined.Add, contentDescription = "Neues Kategoriensystem") }
                if (book != null) IconButton(onClick = { editBook = book }) { Icon(Icons.Outlined.Edit, contentDescription = "Kategoriensystem bearbeiten") }
            }
            OutlinedTextField(
                settings.coderName, { v -> c.settings.update { it.copy(coderName = v) } }, singleLine = true,
                label = { Text("Codierer:in (Name oder Kürzel)") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Codiermodus")
                    HintText("Tippen auf einen Abschnitt öffnet die Codes statt abzuspielen.")
                }
                Switch(codingMode, { on ->
                    if (on && book == null) Notices.info("Bitte zuerst ein Kategoriensystem wählen oder anlegen.")
                    else if (on && settings.coderName.isBlank()) Notices.info("Bitte zuerst den Namen oder das Kürzel der Codierer:in eintragen.")
                    else { onCodingMode(on); if (on) onDismiss() }
                })
            }
            if (book != null) {
                HorizontalDivider()
                Text("Häufigkeiten (${coding.codings.size} Codierungen)", style = MaterialTheme.typography.titleSmall)
                book.codes.forEach { code ->
                    val n = coding.codings.count { it.codeId == code.id }
                    Row(Modifier.fillMaxWidth().clickable { retrieval = code }.padding(vertical = 4.dp, horizontal = if (code.parentId != null) 16.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(codeColor(code.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(code.name, modifier = Modifier.weight(1f))
                        Text("$n", style = MaterialTheme.typography.labelLarge)
                    }
                }
                val coders = coding.coders()
                if (coders.size >= 2) {
                    HorizontalDivider()
                    Text("Intercoder-Übereinstimmung", style = MaterialTheme.typography.titleSmall)
                    var a by remember { mutableStateOf(coders[0]) }
                    var b by remember { mutableStateOf(coders[1]) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { coders.forEach { n -> FilterChip(a == n, { a = n }, label = { Text("A: $n") }) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { coders.forEach { n -> FilterChip(b == n, { b = n }, label = { Text("B: $n") }) } }
                    OutlinedButton(enabled = a != b, onClick = { compare = Intercoder.compare(t, coding, book, a, b) }) { Text("Vergleichen") }
                    compare?.let { r ->
                        Text(
                            "${r.passages} Abschnitte, identisch codiert: ${"%.0f".format(r.exactAgreement * 100)} %" +
                                (r.meanKappa?.let { " · mittleres Kappa ${"%.2f".format(it)} (${Intercoder.interpret(it)})" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        r.perCode.forEach { pc ->
                            HintText("${pc.code.name}: beide ${pc.both}, nur ${r.coderA} ${pc.onlyA}, nur ${r.coderB} ${pc.onlyB} · κ = ${pc.kappa?.let { "%.2f".format(it) } ?: "–"}")
                        }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(Intercoder.report(r))); Notices.info("Tabelle kopiert") }) { Text("Als Tabelle kopieren") }
                    }
                }
                HorizontalDivider()
                Text("Export", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportKind = "qdpx"; export(io.github.thepytales.llmhub.docs.DocumentWriter.safeName(t.title) + ".qdpx") }) { Text("REFI-QDA (.qdpx)") }
                    OutlinedButton(onClick = { exportKind = "csv"; export(io.github.thepytales.llmhub.docs.DocumentWriter.safeName(t.title) + " – Codierungen.csv") }) { Text("Tabelle (CSV)") }
                }
                HintText("REFI-QDA öffnen MAXQDA, ATLAS.ti, NVivo und QDA Miner – mit Kategoriensystem, Codierungen je Codierer:in und Kommentaren.")
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    if (newBook) {
        var name by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newBook = false },
            title = { Text("Neues Kategoriensystem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        text, { text = it }, minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Codes") },
                        placeholder = { Text("Je Zeile ein Code, optional „Code: Definition“.\nEingerückte Zeilen sind Subcodes.\nAuch die Tabelle der Vorlage „Kategoriensystem“ lässt sich einfügen.") },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && text.isNotBlank(), onClick = {
                    val cb = Codebook.parse(name.trim(), text)
                    c.codebooks.upsert(cb)
                    onUpdate(t.withCoding(coding.copy(codebookId = cb.id)))
                    newBook = false
                }) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { newBook = false }) { Text("Abbrechen") } },
        )
    }
    editBook?.let { b -> CodebookEditor(b, onSave = { c.codebooks.upsert(it); editBook = null }, onDelete = {
        c.codebooks.delete(b.id); editBook = null
        Notices.undo("Kategoriensystem gelöscht", restore = { c.codebooks.upsert(b) })
    }, onDismiss = { editBook = null }) }
    retrieval?.let { code ->
        val segs = t.segments.filter { s -> coding.at(s).any { it.codeId == code.id } }
        AlertDialog(
            onDismissRequest = { retrieval = null },
            title = { Text("Code „${code.name}“ · ${segs.size} Stellen") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (code.description.isNotBlank()) HintText("Definition: ${code.description}")
                    segs.forEach { s ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().clickable { retrieval = null; onDismiss(); onJump(s) }) {
                            Column(Modifier.padding(10.dp)) {
                                HintText(TranscriptExport.clock(s.start) + (if (t.diarized) " · " + t.speakerName(s.speaker) else "") + " · " + coding.at(s).filter { it.codeId == code.id }.joinToString { it.coder })
                                Text(s.text.trim(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(segs.joinToString("\n\n") { s -> "(${TranscriptExport.clock(s.start)}) " + (if (t.diarized) t.speakerName(s.speaker) + ": " else "") + s.text.trim() }))
                    Notices.info("Stellen kopiert")
                }) { Text("Kopieren") }
            },
            dismissButton = { TextButton(onClick = { retrieval = null }) { Text("Schließen") } },
        )
    }
}

/** Codes of a scheme: name, color, definition, anchor example, main code. */
@Composable
private fun CodebookEditor(initial: Codebook, onSave: (Codebook) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var book by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Code?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategoriensystem") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(book.name, { book = book.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                book.codes.forEach { code ->
                    Row(Modifier.fillMaxWidth().clickable { editing = code }.padding(start = if (code.parentId != null) 16.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(codeColor(code.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(code.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { book = book.copy(codes = book.codes.filterNot { it.id == code.id || it.parentId == code.id }) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Code löschen")
                        }
                    }
                }
                TextButton(onClick = { editing = Code(name = "", color = Codebook.COLORS[book.codes.size % Codebook.COLORS.size]) }) { Text("+ Code") }
            }
        },
        confirmButton = { TextButton(enabled = book.name.isNotBlank(), onClick = { onSave(book) }) { Text("Speichern") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
    editing?.let { code ->
        var c by remember(code.id) { mutableStateOf(code) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (code.name.isBlank()) "Neuer Code" else "Code bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(c.name, { c = c.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(c.description, { c = c.copy(description = it) }, label = { Text("Definition") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(c.example, { c = c.copy(example = it) }, label = { Text("Ankerbeispiel") }, modifier = Modifier.fillMaxWidth())
                    ChoiceField("Hauptkategorie", listOf<Pair<String?, String>>(null to "– keine (Hauptcode) –") + book.codes.filter { it.id != c.id && it.parentId == null }.map { it.id to it.name }, c.parentId, { c = c.copy(parentId = it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Codebook.COLORS.forEach { hex ->
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(codeColor(hex)).clickable { c = c.copy(color = hex) },
                                contentAlignment = Alignment.Center,
                            ) { if (c.color == hex) Text("✓", color = Color.White) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = c.name.isNotBlank(), onClick = {
                    book = book.copy(codes = if (book.codes.any { it.id == c.id }) book.codes.map { if (it.id == c.id) c else it } else book.codes + c)
                    editing = null
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } },
        )
    }
}
