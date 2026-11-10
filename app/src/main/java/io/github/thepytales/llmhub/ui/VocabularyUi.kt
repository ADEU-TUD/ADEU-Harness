package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.CommandModelInfo
import io.github.thepytales.llmhub.data.KnowledgePacks
import io.github.thepytales.llmhub.data.LoraPackages
import io.github.thepytales.llmhub.data.OwnWords
import io.github.thepytales.llmhub.data.PiiModelInfo
import io.github.thepytales.llmhub.data.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Einstellungen › Lora › „Eigene Wörter“ (AP-78): Abkürzungen und Namen der Professur mit Erklärung und anderen
 * Schreibweisen. Lora, Strg+K, die Dateisuche und die Sprachmodelle nutzen sie.
 */
@Composable
fun OwnWordsSettings(c: AppContainer) {
    val version by OwnWords.version.collectAsState()
    val words = remember(version) { OwnWords.all(c) }
    var editing by remember { mutableStateOf<Term?>(null) }
    var adding by remember { mutableStateOf(false) }
    Section(tr("Eigene Wörter")) {
        HintText(tr("Abkürzungen und Namen, die Lora kennen soll – z. B. „MHB“ für Modulhandbuch oder Projektnamen. Lora versteht sie in Befehlen, die Dateisuche findet über sie, und Chats bekommen die passende Erklärung mit, wenn ein Wort vorkommt."))
        if (words.isEmpty()) HintText(tr("Noch keine eigenen Wörter."))
        words.forEach { t ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.term + if (t.synonyms.isNotEmpty()) " · " + t.synonyms.joinToString() else "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.explanation.isNotBlank()) HintText(t.explanation)
                }
                IconButton(onClick = { editing = t }) { Icon(Icons.Outlined.Edit, contentDescription = tr("Bearbeiten"), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { OwnWords.remove(c, t.term) }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(18.dp)) }
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text(tr("Wort hinzufügen")) }
    }
    if (adding) TermDialog(null, onDismiss = { adding = false }) { OwnWords.put(c, it); adding = false }
    editing?.let { t -> TermDialog(t, onDismiss = { editing = null }) { OwnWords.put(c, it, replacing = t.term); editing = null } }
}

@Composable
private fun TermDialog(term: Term?, onDismiss: () -> Unit, onSave: (Term) -> Unit) {
    var word by remember { mutableStateOf(term?.term.orEmpty()) }
    var explanation by remember { mutableStateOf(term?.explanation.orEmpty()) }
    var synonyms by remember { mutableStateOf(term?.synonyms?.joinToString(", ").orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (term == null) tr("Wort hinzufügen") else tr("Wort bearbeiten")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(word, { word = it }, singleLine = true, label = { Text(tr("Wort oder Abkürzung")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(explanation, { explanation = it }, label = { Text(tr("Erklärung")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(synonyms, { synonyms = it }, singleLine = true, label = { Text(tr("Andere Schreibweisen, durch Komma getrennt")) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = word.isNotBlank(), onClick = { onSave(Term(word.trim(), explanation.trim(), synonyms.split(',').map { it.trim() }.filter { it.isNotEmpty() })) }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/**
 * Einstellungen › Inhalte zum Nachladen (AP-78 c): everything the app can load afterwards, in plain words – what it is
 * for, how big it is, Laden/Aktualisieren/Löschen – the command model, the recognition model, language models, speech
 * recognition, the video editor and the Wissenspakete (also the shared Lora-Pakete of AP-83).
 */
@Composable
fun DownloadsSection(c: AppContainer, onNavigate: (String) -> Unit) {
    val command by c.commandModel.state.collectAsState()
    val pii by c.piiModel.state.collectAsState()
    val packsVersion by KnowledgePacks.version.collectAsState()
    val packs = remember(packsVersion) { KnowledgePacks.installed(c) }
    val scope = rememberCoroutineScope()
    val loading = remember { mutableStateMapOf<String, Double>() }
    HintText(tr("Was sich zur App dazuladen lässt – nur, wer es braucht. Alles lässt sich hier auch wieder löschen."))
    Section(tr("Modelle für Lora")) {
        ContentRow(
            tr("Befehle verstehen"), tr("Lora erkennt offline und in Millisekunden, was du meinst."), tr("{0} MB", CommandModelInfo.MEGABYTES),
            installed = command.installed, progress = command.progress,
            onLoad = c.commandModel::install.takeIf { c.commandModel.available }, onDelete = c.commandModel::delete,
        )
        ContentRow(
            tr("Namen schützen (Erkennungsmodell)"), tr("Findet Namen, Schulen und Orte zuverlässiger, bevor etwas an einen Server geht."), tr("{0} MB", PiiModelInfo.MEGABYTES),
            installed = pii.installed, progress = pii.progress, onLoad = c.piiModel::install, onDelete = c.piiModel::delete,
        )
        ContentLink(tr("Sprachmodelle auf diesem Gerät"), tr("Für Postfach, vertrauliche Projekte und ohne Internet – 1 bis 20 GB je Modell."), tr("Öffnen")) { onNavigate("local-models") }
        ContentLink(tr("Spracherkennung"), tr("Diktat und Transkription auf diesem Gerät."), tr("Öffnen")) { onNavigate("settings/transcription") }
        if (DESKTOP) ContentLink(tr("Videoeditor-Paket"), tr("Genaue Videoangaben, bessere Rauschminderung, Gesichter verpixeln – etwa 40 MB."), tr("Öffnen")) { onNavigate("settings/tools") }
    }
    Section(tr("Wissenspakete")) {
        HintText(tr("Kleine Datenbanken, die Lora die Sprache eines Bereichs beibringen – jede Angabe mit Quelle und Stand. Lora erklärt Begriffe daraus („Was ist das ZLSB?“) und gibt sie Chats als Kontext mit."))
        KnowledgePacks.CATALOG.forEach { o ->
            val have = packs.firstOrNull { it.id == o.id }
            ContentRow(
                tr(o.title), tr(o.description) + (have?.let { " " + tr("Geladen: {0} Einträge, Stand {1}.", it.entries.size, it.date) } ?: ""),
                tr("{0} KB", (o.size / 1024).coerceAtLeast(1)), installed = have != null, progress = loading[o.id],
                onLoad = {
                    loading[o.id] = 0.0
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { KnowledgePacks.download(c, o) { p -> loading[o.id] = p } } }.onFailure { Notices.error(it) }
                        loading.remove(o.id)
                    }
                },
                onDelete = { KnowledgePacks.delete(c, o.id) },
                update = KnowledgePacks.updatable(c, o.id),
            )
        }
        packs.filter { it.origin == "geteilt" }.forEach { p ->
            ContentRow(
                p.title, p.description.ifBlank { tr("Geteiltes Lora-Paket") } + " " + tr("Importiert: {0} Wörter, {1} Befehle, {2} Vorlagen, {3} Hausstil-Regeln.", p.entries.size, p.commands.size, p.templates.size, p.houseRules.size),
                "", installed = true, progress = null, onLoad = null, onDelete = { LoraPackages.remove(c, p.id) },
            )
        }
    }
}

@Composable
private fun ContentRow(title: String, text: String, size: String, installed: Boolean, progress: Double?, onLoad: (() -> Unit)?, onDelete: () -> Unit, update: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title + if (size.isNotEmpty()) " · $size" else "", style = MaterialTheme.typography.bodyMedium)
                HintText(text)
            }
            when {
                progress != null -> {}
                installed && update && onLoad != null -> TextButton(onClick = onLoad) { Text(tr("Aktualisieren")) }
                installed -> TextButton(onClick = onDelete) { Text(tr("Löschen")) }
                onLoad != null -> OutlinedButton(onClick = onLoad) { Text(tr("Laden")) }
            }
        }
        progress?.let { p -> LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ContentLink(title: String, text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            HintText(text)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

/**
 * Einstellungen › Lora › „Lora-Paket teilen“ (AP-83): eigene Befehle, Wörter, Vorlagen und Hausstil als eine Datei
 * exportieren; eine Datei aus dem Team mit Vorschau importieren; importierte Pakete wieder entfernen.
 */
@Composable
fun LoraPackagesSettings(c: AppContainer) {
    var exporting by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<LoraPackages.Preview?>(null) }
    val packsVersion by KnowledgePacks.version.collectAsState()
    val shared = remember(packsVersion) { KnowledgePacks.installed(c).filter { it.origin == "geteilt" } }
    val import = rememberMediaImport(anyFile = true) { _, size, input ->
        if (size > 5_000_000) { Notices.error(tr("Die Datei ist größer als {0} MB.", 5)); return@rememberMediaImport }
        val text = withContext(Dispatchers.IO) { input.bufferedReader().readText() }
        runCatching { LoraPackages.preview(c, text) }.onSuccess { preview = it }.onFailure { Notices.error(it) }
    }
    Section(tr("Lora-Paket teilen")) {
        HintText(tr("Eigene Befehle, eigene Wörter, eigene TU Vorlagen und Hausstil-Regeln als eine Datei – zum Weitergeben im Team. Chats, Gedächtnis, Verlauf und Schlüssel bleiben draußen."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exporting = true }) { Text(tr("Exportieren …")) }
            OutlinedButton(onClick = import) { Text(tr("Importieren …")) }
        }
        shared.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.bodyMedium)
                    HintText(tr("{0} Wörter, {1} Befehle, {2} Vorlagen, {3} Hausstil-Regeln", p.entries.size, p.commands.size, p.templates.size, p.houseRules.size))
                }
                TextButton(onClick = { LoraPackages.remove(c, p.id) }) { Text(tr("Entfernen")) }
            }
        }
    }
    if (exporting) ExportPackDialog(c) { exporting = false }
    preview?.let { pv ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(tr("„{0}“ importieren?", pv.pack.title)) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pv.pack.description.isNotBlank()) HintText(pv.pack.description)
                    if (pv.added.isEmpty() && pv.replaced.isEmpty()) HintText(tr("Alles darin gibt es hier schon genauso."))
                    if (pv.added.isNotEmpty()) {
                        Text(tr("Kommt hinzu ({0})", pv.added.size), style = MaterialTheme.typography.titleSmall)
                        pv.added.forEach { Text("+ $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (pv.replaced.isNotEmpty()) {
                        Text(tr("Wird ersetzt ({0})", pv.replaced.size), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        pv.replaced.forEach { Text("↻ $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    HintText(tr("Das Paket lässt sich später unter Einstellungen › Inhalte zum Nachladen wieder entfernen."))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LoraPackages.import(c, pv.pack)
                    Notices.info(tr("„{0}“ importiert.", pv.pack.title))
                    preview = null
                }) { Text(tr("Importieren")) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun ExportPackDialog(c: AppContainer, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val parts = remember { androidx.compose.runtime.mutableStateListOf(*LoraPackages.Part.entries.toTypedArray()) }
    val counts = remember {
        mapOf(
            LoraPackages.Part.COMMANDS to io.github.thepytales.llmhub.data.LoraHistory.pinned(c).size,
            LoraPackages.Part.WORDS to OwnWords.all(c).size,
            LoraPackages.Part.TEMPLATES to c.templates.current.size,
            LoraPackages.Part.HOUSE to io.github.thepytales.llmhub.data.HouseStyle.rules(c).size,
        )
    }
    val labels = mapOf(
        LoraPackages.Part.COMMANDS to tr("Eigene Befehle (angeheftet)"),
        LoraPackages.Part.WORDS to tr("Eigene Wörter"),
        LoraPackages.Part.TEMPLATES to tr("Eigene TU Vorlagen"),
        LoraPackages.Part.HOUSE to tr("Hausstil-Regeln"),
    )
    val export = rememberFileExport("application/json", onDone = { ok ->
        if (ok) Notices.info(tr("Lora-Paket gespeichert.")) else Notices.error(tr("Speichern fehlgeschlagen."))
        onDone()
    }) { out -> out.write(LoraPackages.export(LoraPackages.build(c, name, description, parts.toSet())).toByteArray(Charsets.UTF_8)) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Lora-Paket exportieren")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Name des Pakets")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(tr("Kurze Beschreibung (optional)")) }, modifier = Modifier.fillMaxWidth())
                LoraPackages.Part.entries.forEach { part ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = part in parts, onCheckedChange = { on -> if (on) parts += part else parts -= part })
                        Text(labels.getValue(part) + " (${counts.getValue(part)})", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && parts.any { counts.getValue(it) > 0 }, onClick = { export(LoraPackages.idOf(name).removePrefix("geteilt-") + ".lora-paket.json") }) { Text(tr("Speichern …")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("Abbrechen")) } },
    )
}

/**
 * Einstellungen › Dokumente & Logos › „Hausstil“ (AP-79): eigene Regeln der Professur – was so nicht stehen soll, wie
 * es stattdessen heißt und warum („Lehrer“ → „Lehrkräfte“, Gendern). Lora prüft Dokumente, Folien und Dateien danach.
 */
@Composable
fun HouseStyleSettings(c: AppContainer) {
    val version by io.github.thepytales.llmhub.data.HouseStyle.version.collectAsState()
    val rules = remember(version) { io.github.thepytales.llmhub.data.HouseStyle.rules(c) }
    var avoid by remember { mutableStateOf("") }
    var preferred by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    Section(tr("Hausstil")) {
        HintText(tr("Eigene Schreibweisen der Professur – z. B. Gendern („Lehrer“ → „Lehrkräfte“) oder feste Begriffe. „Hausstil und Barrierefreiheit prüfen“ (Lora im Dokument, in Folien und bei Dateien) meldet jede Fundstelle und behebt sie auf Wunsch. Die Schreibweise der TU Dresden und Abkürzungen nach DIN 5008 prüft Lora immer."))
        rules.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("„${r.avoid}“ → " + (r.preferred.takeIf { it.isNotEmpty() }?.let { "„$it“" } ?: tr("nur hinweisen")), style = MaterialTheme.typography.bodyMedium)
                    if (r.note.isNotBlank()) HintText(r.note)
                }
                IconButton(onClick = { io.github.thepytales.llmhub.data.HouseStyle.save(c, rules - r) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(avoid, { avoid = it }, singleLine = true, label = { Text(tr("Nicht so")) }, modifier = Modifier.weight(1f))
            OutlinedTextField(preferred, { preferred = it }, singleLine = true, label = { Text(tr("Sondern so")) }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(note, { note = it }, singleLine = true, label = { Text(tr("Warum (optional)")) }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = avoid.isNotBlank(), onClick = {
            io.github.thepytales.llmhub.data.HouseStyle.save(c, rules.filterNot { it.avoid.equals(avoid.trim(), true) } + io.github.thepytales.llmhub.data.HouseRule(avoid.trim(), preferred.trim(), note.trim()))
            avoid = ""; preferred = ""; note = ""
        }) { Text(tr("Regel hinzufügen")) }
    }
}

/**
 * Einstellungen › Lora › „Wofür nutzt du Lora?“ (AP-80): die Rollen vom ersten Start. Eine neue Rolle schaltet ihre
 * Werkzeuge ein (nie aus), stellt ihre TU Vorlagen nach oben und empfiehlt Inhalte zum Nachladen.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RolesSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    val roles = io.github.thepytales.llmhub.data.Role.parse(s.roles)
    val packsVersion by KnowledgePacks.version.collectAsState()
    val missing = remember(s.roles, packsVersion) {
        val have = KnowledgePacks.installed(c).map { it.id }.toSet()
        io.github.thepytales.llmhub.data.Roles.packs(roles).filter { it !in have }.mapNotNull { KnowledgePacks.offer(it) }
    }
    val scope = rememberCoroutineScope()
    Section(tr("Wofür nutzt du Lora?")) {
        HintText(tr("Danach richten sich die vorgeschlagenen Werkzeuge, die Reihenfolge der TU Vorlagen, Loras Vorschläge und empfohlene Inhalte zum Nachladen."))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            io.github.thepytales.llmhub.data.Role.entries.forEach { r ->
                FilterChip(
                    selected = r in roles,
                    onClick = {
                        val next = if (r in roles) roles - r else roles + r
                        c.settings.update { cur -> if (r in roles) cur.copy(roles = io.github.thepytales.llmhub.data.Role.format(next)) else io.github.thepytales.llmhub.data.Roles.apply(cur, next, IS_ANDROID) }
                    },
                    label = { Text(tr(r.label)) },
                )
            }
        }
        missing.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                HintText(tr("Empfohlen: Wissenspaket „{0}“.", tr(o.title)), Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch { runCatching { withContext(Dispatchers.IO) { KnowledgePacks.download(c, o) } }.onFailure { Notices.error(it) } }
                }) { Text(tr("Laden")) }
            }
        }
    }
}
