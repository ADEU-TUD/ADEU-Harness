package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.CsvPilot
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.FontRescue
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.PdfConvert
import io.github.thepytales.llmhub.docs.PrintCheck
import io.github.thepytales.llmhub.docs.PrintJob
import io.github.thepytales.llmhub.docs.PrintPilot
import io.github.thepytales.llmhub.files.Archives
import io.github.thepytales.llmhub.files.FileProtect
import io.github.thepytales.llmhub.files.PhotoPrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The file tools of round 3 in *Dateien* and in the Lora-Ordner: CSV-Lotse (AP-93), Fotos aufbereiten (AP-100),
 * Archiv-Werkzeug (AP-99), Passwortschutz (AP-98), Schriften-Notfall (AP-91) and Druck-Lotse (AP-97, PC). A menu line
 * or Lora asks for a tool through [FileTools.request]; [FileToolDialogs] (once per screen) shows it. Originals always
 * stay; results go next to them with their own name.
 */
object FileTools {
    enum class Kind { CSV, PHOTOS, ARCHIVE, PACK, PASSWORD, OPEN_PROTECTED, FONTS, PRINT }

    data class Request(val kind: Kind, val files: List<File>)

    var request by mutableStateOf<Request?>(null)

    fun open(kind: Kind, vararg files: File) { request = Request(kind, files.toList()) }

    fun isCsv(f: File) = f.extension.lowercase() in setOf("csv", "tsv")

    /** What opening [f] should do instead of the usual way: the CSV-Lotse, the archive or the password. */
    fun interceptOpen(f: File): Boolean {
        when {
            isCsv(f) -> open(Kind.CSV, f)
            Archives.isArchive(f.name) -> open(Kind.ARCHIVE, f)
            FileProtect.kind(f.name) == FileProtect.Kind.OFFICE && FileProtect.isProtected(f) -> open(Kind.OPEN_PROTECTED, f)
            else -> return false
        }
        return true
    }
}

/** Menu lines of the file tools for [file] (a folder too). */
@Composable
fun FileToolMenuItems(file: File, onDismiss: () -> Unit) {
    fun act(kind: FileTools.Kind) { onDismiss(); FileTools.open(kind, file) }
    val ext = file.extension.lowercase()
    if (file.isDirectory) {
        DropdownMenuItem(text = { Text(tr("Als ZIP packen …")) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { act(FileTools.Kind.PACK) })
        if (file.listFiles()?.any(PhotoPrep::isPhoto) == true) DropdownMenuItem(text = { Text(tr("Fotos aufbereiten …")) }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = { act(FileTools.Kind.PHOTOS) })
        return
    }
    if (FileTools.isCsv(file) || ext == "txt") DropdownMenuItem(text = { Text(tr("Als Tabelle öffnen (CSV-Lotse) …")) }, leadingIcon = { Icon(Icons.Outlined.TableChart, null) }, onClick = { act(FileTools.Kind.CSV) })
    if (PhotoPrep.isPhoto(file)) DropdownMenuItem(text = { Text(tr("Fotos aufbereiten …")) }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = { act(FileTools.Kind.PHOTOS) })
    if (Archives.isArchive(file.name)) DropdownMenuItem(text = { Text(tr("Archiv öffnen …")) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { act(FileTools.Kind.ARCHIVE) })
    else DropdownMenuItem(text = { Text(tr("Als ZIP packen …")) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { act(FileTools.Kind.PACK) })
    if (FileProtect.supported(file.name)) DropdownMenuItem(text = { Text(tr("Passwort …")) }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = { act(FileTools.Kind.PASSWORD) })
    if (FontRescue.supported(file.name) && (ext != "pdf" || PrintPilot.AVAILABLE)) DropdownMenuItem(text = { Text(tr("Schriften prüfen …")) }, leadingIcon = { Icon(Icons.Outlined.FontDownload, null) }, onClick = { act(FileTools.Kind.FONTS) })
    if (PrintPilot.AVAILABLE && ext in setOf("pdf", "docx", "pptx", "odt", "odp")) DropdownMenuItem(text = { Text(tr("Druck-Lotse …")) }, leadingIcon = { Icon(Icons.Outlined.Print, null) }, onClick = { act(FileTools.Kind.PRINT) })
}

/** Shows the tool asked for; [onOpenDoc] opens a document of the editor, [onAsk] a folder chat (Frag diesen Ordner). */
@Composable
fun FileToolDialogs(c: AppContainer, onOpenDoc: (String) -> Unit, onChanged: () -> Unit = {}, onAsk: ((File) -> Unit)? = null) {
    val r = FileTools.request ?: return
    val close = { FileTools.request = null }
    val done = { onChanged(); c.drive.changed() }
    val f = r.files.firstOrNull() ?: return close()
    when (r.kind) {
        FileTools.Kind.CSV -> CsvPilotDialog(c, f, close, onOpenDoc)
        FileTools.Kind.PHOTOS -> PhotoPrepDialog(r.files, close, done)
        FileTools.Kind.ARCHIVE -> ArchiveDialog(f, close, done, onAsk)
        FileTools.Kind.PACK -> PackDialog(r.files, close, done)
        FileTools.Kind.PASSWORD -> PasswordDialog(f, close, done)
        FileTools.Kind.OPEN_PROTECTED -> OpenProtectedDialog(c, f, close, onOpenDoc)
        FileTools.Kind.FONTS -> FontsDialog(f, close, done)
        FileTools.Kind.PRINT -> PrintDialog(f, close, done)
    }
}

/** Runs [work] off the main thread with a busy flag; errors become a notice. */
private fun launchWork(scope: kotlinx.coroutines.CoroutineScope, busy: (Boolean) -> Unit, work: suspend () -> Unit) {
    busy(true)
    scope.launch {
        try {
            work()
        } catch (e: Exception) {
            Notices.error(e.message ?: e.javaClass.simpleName)
        } finally {
            busy(false)
        }
    }
}

@Composable
private fun Hint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warn: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// ── CSV-Lotse (AP-93) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsvPilotDialog(c: AppContainer, file: File, onDismiss: () -> Unit, onOpenDoc: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val bytes by produceState<ByteArray?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() } }
    val detected = remember(bytes) { bytes?.let { CsvPilot.detect(it, file.name) } }
    var s by remember(detected) { mutableStateOf(detected?.settings ?: CsvPilot.Settings()) }
    val rows = remember(bytes, s) { bytes?.let { runCatching { CsvPilot.rows(it, s) }.getOrNull() }.orEmpty() }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("„{0}“ als Tabelle öffnen", file.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detected == null) CircularProgressIndicator(Modifier.size(20.dp)) else {
                    Text(tr("Das habe ich erkannt – bei Bedarf anpassen:"), style = MaterialTheme.typography.titleSmall)
                    detected.notes.forEach { Hint(Icons.Outlined.Info, it) }
                    GroupLabel(tr("Kodierung"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CsvPilot.Encoding.entries.forEach { e -> FilterChip(selected = s.encoding == e, onClick = { s = s.copy(encoding = e) }, label = { Text(tr(e.label)) }) }
                        FilterChip(selected = s.repair, onClick = { s = s.copy(repair = !s.repair) }, label = { Text(tr("Umlaute reparieren")) })
                    }
                    GroupLabel(tr("Trennzeichen"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CsvPilot.SEPARATORS.forEach { sep -> FilterChip(selected = s.separator == sep, onClick = { s = s.copy(separator = sep) }, label = { Text(CsvPilot.separatorLabel(sep)) }) }
                    }
                    GroupLabel(tr("Zahlen"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Pair(',', '.'), Pair(',', null), Pair('.', ','), Pair('.', null)).forEach { (d, t) ->
                            FilterChip(selected = s.decimal == d && s.thousands == t, onClick = { s = s.copy(decimal = d, thousands = t) }, label = { Text(CsvPilot.numberExample(d, t)) })
                        }
                    }
                    GroupLabel(tr("Datum"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CsvPilot.DateStyle.entries.forEach { d -> FilterChip(selected = s.dates == d, onClick = { s = s.copy(dates = d) }, label = { Text(tr(d.label)) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Zeilen über der Tabelle überspringen: {0}", s.skip), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(enabled = s.skip > 0, onClick = { s = s.copy(skip = s.skip - 1) }) { Text("−") }
                        TextButton(onClick = { s = s.copy(skip = s.skip + 1) }) { Text("+") }
                    }
                    GroupLabel(tr("Vorschau"))
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        rows.take(8).forEach { row ->
                            Row {
                                row.take(8).forEach { cell ->
                                    Text(cell.replace("\n", " ").take(24), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp).padding(end = 6.dp))
                                }
                            }
                        }
                    }
                    HintText(tr("{0} Zeilen. Das Original bleibt unverändert; speichern als Excel oder CSV (für Excel oder Standard) über „Exportieren“.", rows.size))
                }
            }
        },
        confirmButton = {
            Button(enabled = bytes != null && !busy, onClick = {
                val b = bytes ?: return@Button
                launchWork(scope, { busy = it }) {
                    val doc = withContext(Dispatchers.IO) {
                        val imported = OfficeImport.Imported(DocKind.SHEET, file.nameWithoutExtension, listOf("Tabelle 1" to CsvPilot.markdown(b, s)))
                        OfficeImport.toLiveDoc(imported).copy(vault = c.docs.defaultVault).also { c.docs.save(it) }
                    }
                    onDismiss()
                    onOpenDoc(doc.id)
                }
            }) { Text(tr("Öffnen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

// ── Fotos vom Handy aufbereiten (AP-100) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoPrepDialog(files: List<File>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val photos = remember(files) { PhotoPrep.photos(files) }
    var label by remember { mutableStateOf(files.firstOrNull()?.let { if (it.isDirectory) it.name else it.parentFile?.name }.orEmpty().take(30)) }
    var max by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val plan by produceState<List<PhotoPrep.Item>?>(null, photos, label) { value = withContext(Dispatchers.IO) { PhotoPrep.plan(photos, PhotoPrep.Options(label = label)) } }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (photos.size == 1) tr("Foto aufbereiten") else tr("{0} Fotos aufbereiten", photos.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("In einem Schritt: als JPG, richtig gedreht, nach Aufnahmezeit benannt, ohne Ortsdaten und andere Metadaten."), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(label, { label = it }, label = { Text(tr("Zusatz im Namen (z. B. Exkursion)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                GroupLabel(tr("Größe"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<Int?, String>>(null to tr("Original"), 3000 to tr("3000 px (Druck)"), 2048 to tr("2048 px (Folien, Web)"), 1280 to tr("1280 px (Mail)")).forEach { (m, l) ->
                        FilterChip(selected = max == m, onClick = { max = m }, label = { Text(l) })
                    }
                }
                plan?.let { p ->
                    PhotoPrep.summary(p).takeIf { it.isNotBlank() }?.let { Hint(Icons.Outlined.Info, it) }
                    p.take(3).forEach { Text("${it.source.name} → ${it.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (p.size > 3) Text("…", style = MaterialTheme.typography.bodySmall)
                }
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                HintText(tr("Die Originale bleiben; die Fotos kommen in den Ordner „aufbereitet“ daneben."))
            }
        },
        confirmButton = {
            Button(enabled = !busy && photos.isNotEmpty(), onClick = {
                launchWork(scope, { busy = it }) {
                    val result = PhotoPrep.prepare(photos, PhotoPrep.Options(label = label, maxSide = max)) { d, t -> progress = if (t == 0) 1f else d.toFloat() / t }
                    onDone()
                    if (result.failed.isEmpty()) Notices.info(tr("{0} Fotos aufbereitet – im Ordner „{1}“.", result.made.size, result.folder.name))
                    else Notices.error(tr("{0} aufbereitet, {1} nicht: {2}", result.made.size, result.failed.size, result.failed.first().let { "${it.first.name}: ${it.second}" }))
                    onDismiss()
                    runCatching { openDocument(context, result.folder) }
                }
            }) { Text(tr("Aufbereiten")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

// ── Archiv-Werkzeug (AP-99) ──

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) = SecretField(label, value, onChange)

@Composable
private fun ArchiveDialog(file: File, onDismiss: () -> Unit, onDone: () -> Unit, onAsk: ((File) -> Unit)?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var needsPassword by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<Archives.Entry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var folder by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(file, reload) {
        withContext(Dispatchers.IO) {
            runCatching {
                needsPassword = Archives.needsPassword(file)
                entries = Archives.list(file, password.takeIf { it.isNotEmpty() }?.toCharArray())
                error = null
            }.onFailure { e -> if (e is Archives.PasswordNeeded) needsPassword = true else error = e.message ?: e.toString() }
        }
    }
    fun pw() = password.takeIf { it.isNotEmpty() }?.toCharArray()
    fun target(): File {
        val base = file.name.removeSuffix(".gz").removeSuffix(".tar").substringBeforeLast('.').ifBlank { "Archiv" }
        var d = File(file.parentFile, base)
        var n = 2
        while (d.exists()) d = File(file.parentFile, "$base ($n)").also { n++ }
        return d
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(file.name) },
        text = {
            Column(Modifier.widthIn(min = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsPassword) {
                    PasswordField(tr("Passwort des Archivs"), password) { password = it }
                    HintText(tr("Die Namen sind lesbar; zum Öffnen der Dateien braucht es das Passwort."))
                }
                error?.let { Hint(Icons.Outlined.WarningAmber, it, warn = true) }
                val all = entries
                if (all == null && error == null) CircularProgressIndicator(Modifier.size(20.dp))
                if (all != null) {
                    OutlinedTextField(query, { query = it }, label = { Text(tr("Im Archiv suchen")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val (dirs, files) = if (query.isNotBlank()) emptyList<Archives.Entry>() to Archives.search(all, query) else Archives.inFolder(all, folder)
                    HintText(tr("{0} Dateien", all.count { !it.dir }) + if (folder.isNotEmpty() && query.isBlank()) " · " + folder else "")
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        if (folder.isNotEmpty() && query.isBlank()) Text("⬑ ..", Modifier.fillMaxWidth().clickable { folder = folder.substringBeforeLast('/', "") }.padding(6.dp))
                        dirs.forEach { d ->
                            Row(Modifier.fillMaxWidth().clickable { folder = d.path.trimEnd('/') }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(d.name)
                            }
                        }
                        files.forEach { e ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                // One file out into a temporary folder and opened – without unpacking everything.
                                launchWork(scope, { busy = it }) {
                                    val out = withContext(Dispatchers.IO) { Archives.extract(file, File(context.cacheDir, "archiv-${System.nanoTime()}"), listOf(e.path), pw()).single() }
                                    openDocument(context, out)
                                }
                            }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                Text(if (query.isNotBlank()) e.path else e.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (e.size >= 0) Text(fileSizeLabel(e.size), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Row {
                if (onAsk != null) TextButton(enabled = !busy && entries != null, onClick = {
                    launchWork(scope, { busy = it }) {
                        val dir = File(context.cacheDir, "archiv-frag/" + file.nameWithoutExtension)
                        withContext(Dispatchers.IO) { dir.deleteRecursively(); Archives.extract(file, dir, password = pw()) }
                        onDismiss()
                        onAsk(dir)
                    }
                }) { Text(tr("Frag dieses Archiv")) }
                Button(enabled = !busy && (entries != null || needsPassword), onClick = {
                    if (needsPassword && entries == null) { reload++; return@Button }
                    launchWork(scope, { busy = it }) {
                        val dest = target()
                        val n = withContext(Dispatchers.IO) { Archives.extract(file, dest, password = pw()).size }
                        onDone()
                        Notices.info(tr("{0} Dateien entpackt nach „{1}“.", n, dest.name))
                        onDismiss()
                    }
                }) { Text(if (needsPassword && entries == null) tr("Öffnen") else tr("Alles entpacken")) }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackDialog(files: List<File>, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var strength by remember { mutableStateOf(Archives.Strength.NORMAL) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var withPassword by remember { mutableStateOf(false) }
    var part by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val out = remember(files) { Archives.outFile(files) }
    val mismatch = withPassword && repeat.isNotEmpty() && password != repeat
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Als ZIP packen")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Wird zu „{0}“ im selben Ordner.", out.name), style = MaterialTheme.typography.bodyMedium)
                GroupLabel(tr("Stärke"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Archives.Strength.entries.forEach { st -> FilterChip(selected = strength == st, onClick = { strength = st }, label = { Text(tr(st.label)) }) }
                }
                GroupLabel(tr("In Teile aufteilen (für Mails)"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<Int?, String>>(null to tr("Nicht teilen"), 10 to "10 MB", 20 to "20 MB", 50 to "50 MB").forEach { (m, l) -> FilterChip(selected = part == m, onClick = { part = m }, label = { Text(l) }) }
                }
                if (part != null) HintText(tr("Es entstehen „.zip“, „.z01“, „.z02“ … – alle Teile zusammen verschicken. Öffnen mit 7-Zip, WinRAR oder Lora; der Windows-Explorer kann geteilte ZIPs nicht öffnen."))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withPassword = !withPassword }) {
                    Checkbox(withPassword, { withPassword = it })
                    Text(tr("Mit Passwort (AES-256)"))
                }
                if (withPassword) {
                    PasswordField(tr("Passwort"), password) { password = it }
                    PasswordField(tr("Passwort wiederholen"), repeat) { repeat = it }
                    if (mismatch) Text(tr("Die Passwörter stimmen nicht überein."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    HintText(tr("Das Passwort auf anderem Weg weitergeben (Telefon, Messenger) – nicht in derselben Mail. Der Explorer von Windows öffnet AES-ZIPs nicht, 7-Zip und Lora schon."))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && (!withPassword || (password.isNotEmpty() && password == repeat)), onClick = {
                launchWork(scope, { busy = it }) {
                    val made = withContext(Dispatchers.IO) { Archives.pack(files, out, strength, password.takeIf { withPassword && it.isNotEmpty() }?.toCharArray(), part) }
                    onDone()
                    Notices.info(if (made.size > 1) tr("„{0}“ in {1} Teilen gepackt.", out.name, made.size) else tr("„{0}“ gepackt ({1}).", out.name, fileSizeLabel(made.sumOf { it.length() })))
                    onDismiss()
                }
            }) { Text(tr("Packen")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

// ── Passwortschutz (AP-98) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordDialog(file: File, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val protected by produceState<Boolean?>(null, file) { value = withContext(Dispatchers.IO) { FileProtect.isProtected(file) } }
    var mode by remember(protected) { mutableStateOf(if (protected == true) "remove" else "set") }
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val needsNew = mode != "remove"
    val ok = (!needsNew || (new.isNotEmpty() && new == repeat)) && (mode == "set" || old.isNotEmpty())
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Passwort für „{0}“", file.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (protected) {
                    null -> CircularProgressIndicator(Modifier.size(20.dp))
                    true -> Hint(Icons.Outlined.Lock, tr("Die Datei ist mit einem Passwort geschützt."))
                    false -> Hint(Icons.Outlined.Info, tr("Die Datei hat noch kein Passwort."))
                }
                if (protected == true) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "remove", onClick = { mode = "remove" }, label = { Text(tr("Passwort entfernen")) })
                    FilterChip(selected = mode == "change", onClick = { mode = "change" }, label = { Text(tr("Passwort ändern")) })
                }
                if (mode != "set") PasswordField(tr("Bisheriges Passwort"), old) { old = it }
                if (needsNew) {
                    PasswordField(tr("Neues Passwort"), new) { new = it }
                    PasswordField(tr("Passwort wiederholen"), repeat) { repeat = it }
                    if (repeat.isNotEmpty() && new != repeat) Text(tr("Die Passwörter stimmen nicht überein."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    HintText(tr("Moderne Verschlüsselung (AES-256) – Word, Excel, PowerPoint, Acrobat und 7-Zip öffnen die Datei mit dem Passwort. Das Passwort auf anderem Weg weitergeben, nicht in derselben Mail. Vergessene Passwörter kann niemand wiederherstellen."))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { replace = !replace }) {
                    Checkbox(replace, { replace = it })
                    Text(tr("Original ersetzen (sonst neue Datei daneben)"))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && protected != null && ok, onClick = {
                launchWork(scope, { busy = it }) {
                    val made = withContext(Dispatchers.IO) {
                        val out = FileProtect.outFile(file, if (mode == "remove") "ohne Passwort" else "mit Passwort")
                        when (mode) {
                            "set" -> FileProtect.protect(file, new, out)
                            "remove" -> FileProtect.unprotect(file, old, out)
                            else -> FileProtect.change(file, old, new, out)
                        }
                        if (replace) FileProtect.replace(file, out) else out
                    }
                    onDone()
                    Notices.info(tr("Gespeichert: „{0}“", made.name))
                    onDismiss()
                }
            }) {
                val label = when (mode) { "remove" -> tr("Entfernen"); "change" -> tr("Ändern"); else -> tr("Schützen") }
                Text(label)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** A protected Word/Excel/PowerPoint file: password, then it opens in the editor – nothing is written in plain text. */
@Composable
private fun OpenProtectedDialog(c: AppContainer, file: File, onDismiss: () -> Unit, onOpenDoc: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("„{0}“ ist geschützt", file.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(tr("Passwort"), password) { password = it }
                HintText(tr("Die Datei öffnet sich als Kopie in Lora; das Original bleibt verschlüsselt. Zum Speichern mit Passwort „Exportieren“ und danach „Passwort …“ wählen."))
            }
        },
        confirmButton = {
            Button(enabled = !busy && password.isNotEmpty(), onClick = {
                launchWork(scope, { busy = it }) {
                    val doc = withContext(Dispatchers.IO) {
                        val plain = FileProtect.openOffice(file, password)
                        val imported = OfficeImport.read(file.name, plain) ?: error(tr("„{0}“ lässt sich nicht lesen.", file.name))
                        OfficeImport.toLiveDoc(imported).copy(vault = c.docs.defaultVault).also { c.docs.save(it); DocsHandoff.saveMedia(c, it, imported) }
                    }
                    onDismiss()
                    onOpenDoc(doc.id)
                }
            }) { Text(tr("Öffnen")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

// ── Schriften-Notfall (AP-91) ──

@Composable
private fun FontsDialog(file: File, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val report by produceState<Result<FontRescue.Report>?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { FontRescue.check(file) } } }
    var busy by remember { mutableStateOf(false) }
    val word = file.extension.lowercase() in setOf("docx", "docm")
    val office = file.extension.lowercase() != "pdf"
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Schriften in „{0}“", file.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val r = report
                when {
                    r == null -> CircularProgressIndicator(Modifier.size(20.dp))
                    r.isFailure -> Hint(Icons.Outlined.WarningAmber, r.exceptionOrNull()?.message ?: "", warn = true)
                    else -> {
                        val rep = r.getOrThrow()
                        if (rep.fonts.isEmpty()) Text(tr("Keine Schriftangaben gefunden – die Datei nutzt die Standardschrift."))
                        if (rep.missing.isEmpty() && rep.fonts.isNotEmpty()) Hint(Icons.Outlined.Info, tr("Alle Schriften sind da – die Datei sieht hier so aus wie beim Verfasser."))
                        rep.fonts.forEach { f ->
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(f.name, style = MaterialTheme.typography.titleSmall)
                                    StatusPill(
                                        when { f.embedded -> tr("eingebettet"); f.installed -> tr("installiert"); else -> tr("fehlt") },
                                        if (f.missing) PillTone.DANGER else PillTone.SUCCESS,
                                    )
                                    if (f.tu) StatusPill(tr("TU-Design"))
                                }
                                if (f.missing) {
                                    Text(f.replacement?.let { tr("Ersatz: {0}", it) + if (f.metric) " " + tr("(gleiche Breiten)") else "" } ?: tr("Kein Ersatz installiert"), style = MaterialTheme.typography.bodySmall)
                                    Text(f.effect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (f.tu) Text(tr("Die Schriften des TU-Designs (Noto Sans) gibt es kostenlos – für ein genaues Ergebnis installieren."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (word) HintText(tr("„Einbetten“ legt die installierten Schriften in die Word-Datei, damit sie auf anderen Rechnern gleich aussieht (die Datei wird größer). Schriften, deren Lizenz das verbietet, bleiben draußen."))
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            Row {
                val rep = report?.getOrNull()
                if (office && rep != null && rep.missing.any { it.replacement != null }) TextButton(enabled = !busy, onClick = {
                    launchWork(scope, { busy = it }) {
                        val out = FileProtect.outFile(file, "Schriften ersetzt")
                        withContext(Dispatchers.IO) { FontRescue.replace(file, rep.missing.mapNotNull { f -> f.replacement?.let { f.name to it } }.toMap(), out) }
                        onDone(); Notices.info(tr("Gespeichert: „{0}“", out.name)); onDismiss()
                    }
                }) { Text(tr("Ersatz übernehmen")) }
                if (word && PrintPilot.AVAILABLE) Button(enabled = !busy && rep != null, onClick = {
                    launchWork(scope, { busy = it }) {
                        val out = FileProtect.outFile(file, "Schriften eingebettet")
                        val e = withContext(Dispatchers.IO) { FontRescue.embed(file, out) }
                        onDone()
                        Notices.info(if (e.fonts.isEmpty()) tr("Nichts eingebettet: {0}", e.skipped.joinToString { "${it.first} – ${it.second}" }.ifEmpty { tr("nur Standardschriften") }) else tr("Eingebettet: {0} – „{1}“", e.fonts.joinToString(), out.name))
                        onDismiss()
                    }
                }) { Text(tr("Einbetten")) }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}

// ── Druck-Lotse (AP-97, PC) ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrintDialog(file: File, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Word and slides become a PDF first (with Microsoft Office or the app's reader).
    val pdf by produceState<Result<File>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { if (file.extension.equals("pdf", true)) file else PdfConvert.toPdf(context, file, file.name, File(context.cacheDir, "druck")).file } }
    }
    val check by produceState<PrintCheck?>(null, pdf) { value = pdf?.getOrNull()?.let { p -> withContext(Dispatchers.IO) { runCatching { PrintPilot.check(p) }.getOrNull() } } }
    var job by remember { mutableStateOf(PrintJob.BOOKLET) }
    var perSheet by remember { mutableStateOf(2) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(tr("Druck-Lotse: „{0}“", file.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pdf?.exceptionOrNull()?.let { Hint(Icons.Outlined.WarningAmber, it.message ?: "", warn = true) }
                val ck = check
                if (pdf == null || (pdf?.isSuccess == true && ck == null)) CircularProgressIndicator(Modifier.size(20.dp))
                if (ck != null) {
                    Text(tr("{0} Seiten · {1} × {2} mm", ck.pages, ck.widthMm.toInt(), ck.heightMm.toInt()), style = MaterialTheme.typography.bodyMedium)
                    GroupLabel(tr("Prüfung"))
                    if (ck.issues.none { it.severe }) Hint(Icons.Outlined.Info, tr("Keine schweren Probleme gefunden."))
                    ck.issues.forEach { i -> Hint(if (i.severe) Icons.Outlined.WarningAmber else Icons.Outlined.Info, i.title + ": " + i.detail, warn = i.severe) }
                }
                GroupLabel(tr("Was soll entstehen?"))
                PrintJob.entries.forEach { j ->
                    Row(Modifier.fillMaxWidth().clickable { job = j }.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        androidx.compose.material3.RadioButton(selected = job == j, onClick = { job = j })
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(tr(j.label), style = MaterialTheme.typography.bodyMedium)
                            Text(tr(j.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (job == PrintJob.N_UP) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 4, 6, 9).forEach { n -> FilterChip(selected = perSheet == n, onClick = { perSheet = n }, label = { Text(tr("{0} pro Blatt", n)) }) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && pdf?.isSuccess == true, onClick = {
                val src = pdf?.getOrNull() ?: return@Button
                launchWork(scope, { busy = it }) {
                    val suffix = when (job) {
                        PrintJob.BOOKLET -> tr("Broschüre"); PrintJob.N_UP -> tr("{0} pro Blatt", perSheet); PrintJob.HANDOUT -> tr("Handzettel")
                        PrintJob.DUPLEX -> tr("doppelseitig"); PrintJob.BLEED -> tr("Druckerei")
                    }
                    val out = FileProtect.outFile(File(file.parentFile, file.nameWithoutExtension + ".pdf"), suffix)
                    val enlarged = withContext(Dispatchers.IO) {
                        when (job) {
                            PrintJob.BOOKLET -> { PrintPilot.booklet(src, out); false }
                            PrintJob.N_UP -> { PrintPilot.nUp(src, out, perSheet); false }
                            PrintJob.HANDOUT -> { PrintPilot.handout(src, out); false }
                            PrintJob.DUPLEX -> { PrintPilot.duplex(src, out); false }
                            PrintJob.BLEED -> PrintPilot.bleed(src, out)
                        }
                    }
                    onDone()
                    Notices.info(tr("Gespeichert: „{0}“", out.name) + if (enlarged) " – " + tr("ohne eigenen Beschnitt etwas vergrößert, Ränder prüfen.") else "")
                    if (job == PrintJob.BOOKLET) Notices.info(tr("Drucken: doppelseitig, „an der kurzen Kante spiegeln“, dann in der Mitte falten und heften."))
                    onDismiss()
                    runCatching { openDocument(context, out) }
                }
            }) { Text(tr("Erstellen")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Schließen")) } },
    )
}
