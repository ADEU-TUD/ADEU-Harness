package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.research.BibEntry
import io.github.thepytales.llmhub.research.BibResult
import io.github.thepytales.llmhub.research.BibTeX
import io.github.thepytales.llmhub.research.Bibliography
import io.github.thepytales.llmhub.research.CitationFormatter
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.research.CslJson
import io.github.thepytales.llmhub.research.EntryStatus
import io.github.thepytales.llmhub.research.LitSource
import io.github.thepytales.llmhub.research.Person
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import io.github.thepytales.llmhub.research.ReferenceImport
import io.github.thepytales.llmhub.research.Ris
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What another screen hands to the literature screen: references to format or a list to show. */
object LiteratureHandoff {
    data class Request(val tab: Int, val text: String = "", val list: String? = null)

    val pending = MutableStateFlow<Request?>(null)

    fun take(): Request? = pending.value.also { pending.value = null }
}

private const val TAB_SEARCH = 0
private const val TAB_COLLECTION = 1
private const val TAB_BIBLIOGRAPHY = 2

/** Literature: start a literature search, the collection of kept works, and reference lists in any citation style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiteratureScreen(c: AppContainer, onBack: () -> Unit, onOpenChat: () -> Unit, onSettings: () -> Unit) {
    val handoff = remember { LiteratureHandoff.take() }
    var tab by rememberSaveable { mutableIntStateOf(handoff?.tab ?: TAB_SEARCH) }
    /** References going from the collection (or a chat) into the bibliography tab. */
    var bibInput by rememberSaveable { mutableStateOf(handoff?.text.orEmpty()) }
    var bibRefs by remember { mutableStateOf<List<Reference>>(emptyList()) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text("Literatur") })
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == TAB_SEARCH, onClick = { tab = TAB_SEARCH }, text = { Text("Suche") })
                    Tab(selected = tab == TAB_COLLECTION, onClick = { tab = TAB_COLLECTION }, text = { Text("Sammlung") })
                    Tab(selected = tab == TAB_BIBLIOGRAPHY, onClick = { tab = TAB_BIBLIOGRAPHY }, text = { Text("Verzeichnis") })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 820.dp).fillMaxSize()) {
                when (tab) {
                    TAB_SEARCH -> SearchStart(c, onOpenChat, onSettings)
                    TAB_COLLECTION -> Collection(c, initialList = handoff?.list) { refs ->
                        bibRefs = refs
                        bibInput = ""
                        tab = TAB_BIBLIOGRAPHY
                    }
                    else -> BibliographyMaker(c, bibInput, { bibInput = it }, bibRefs, { bibRefs = it })
                }
            }
        }
    }
}

// ── Search ──────────────────────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchStart(c: AppContainer, onOpenChat: () -> Unit, onSettings: () -> Unit) {
    val settings by c.settings.settings.collectAsState()
    var topic by rememberSaveable { mutableStateOf("") }
    var context by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var german by rememberSaveable { mutableStateOf(true) }
    var english by rememberSaveable { mutableStateOf(true) }
    var depth by rememberSaveable { mutableIntStateOf(1) }
    val types = remember { mutableStateListOf<RefType>() }
    val sources = remember { mutableStateListOf<LitSource>().apply { addAll(settings.literatureSources.split(',').mapNotNull { LitSource.parse(it) }) } }
    var style by rememberSaveable { mutableStateOf(settings.citationStyle) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ManageSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("Literatursuche mit dem Agenten", style = MaterialTheme.typography.titleMedium)
                }
                HintText(
                    "Thema und Kontext angeben – der Agent sucht in mehreren Datenbanken, wertet zentrale Arbeiten per " +
                        "Schneeballsuche aus, wählt passende Werke anhand der Abstracts aus, legt sie in der Sammlung ab und " +
                        "schreibt ein Suchprotokoll für den Methodenteil."
                )
                OutlinedTextField(topic, { topic = it }, label = { Text("Thema") }, placeholder = { Text("z.B. Klassenführung bei Berufseinsteigenden") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    context, { context = it }, label = { Text("Kontext und Fragestellung (optional)") },
                    placeholder = { Text("Wofür? z.B. Masterarbeit, Seminar, Antrag; Schulform, Fach, Perspektive …") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it.filter(Char::isDigit).take(4) }, label = { Text("Ab Jahr") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(to, { to = it.filter(Char::isDigit).take(4) }, label = { Text("Bis Jahr") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("Sprachen", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(german, { german = !german }, label = { Text("Deutsch") })
                    FilterChip(english, { english = !english }, label = { Text("Englisch") })
                }
                Text("Publikationsarten (leer = alle)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(RefType.ARTICLE, RefType.BOOK, RefType.CHAPTER, RefType.THESIS, RefType.REPORT).forEach { t ->
                        FilterChip(t in types, { if (t in types) types.remove(t) else types.add(t) }, label = { Text(t.label.substringBefore(" (")) })
                    }
                }
                Text("Datenbanken", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LitSource.entries.forEach { s ->
                        FilterChip(s in sources, { if (s in sources) sources.remove(s) else sources.add(s) }, label = { Text(s.label) })
                    }
                }
                if (LitSource.OPENALEX in sources && settings.openAlexKey.isBlank()) {
                    HintText("OpenAlex ohne eigenen (kostenlosen) Schlüssel teilt sich ein kleines Tageskontingent mit allen im selben Netz. Schlüssel eintragen unter Einstellungen › Literatur & Zotero.")
                    TextButton(onClick = onSettings) { Text("Schlüssel eintragen") }
                }
                Text("Umfang", style = MaterialTheme.typography.labelLarge)
                Segmented(listOf(0 to "Überblick (~10)", 1 to "Standard (~20)", 2 to "Gründlich (~40)"), depth, { depth = it })
                ChoiceField("Zitierstil für das Verzeichnis", CitationStyle.entries.map { it.name to it.label }, style, { style = it })
                Button(
                    enabled = topic.isNotBlank() && sources.isNotEmpty(),
                    onClick = {
                        val text = buildString {
                            append("Literatursuche zum Thema: ").append(topic.trim()).append("\n")
                            if (context.isNotBlank()) append("Kontext und Fragestellung: ").append(context.trim()).append("\n")
                            if (from.isNotBlank() || to.isNotBlank()) append("Zeitraum: ${from.ifBlank { "…" }}–${to.ifBlank { "heute" }}\n")
                            val langs = listOfNotNull("Deutsch".takeIf { german }, "Englisch".takeIf { english })
                            if (langs.isNotEmpty()) append("Sprachen: ").append(langs.joinToString(", ")).append("\n")
                            if (types.isNotEmpty()) append("Publikationsarten: ").append(types.joinToString(", ") { it.label }).append("\n")
                            append("Datenbanken: ").append(sources.joinToString(", ") { it.label }).append("\n")
                            append("Umfang: ").append(listOf("Überblick, etwa 10 Werke", "Standard, etwa 20 Werke", "gründlich, etwa 40 Werke")[depth]).append("\n")
                            append("Zitierstil: ").append(CitationStyle.parse(style)?.label ?: style)
                        }
                        ChatHandoff.pending.value = ChatHandoff.Content(text, emptyList(), mode = Conversation.MODE_LITERATURE, send = true)
                        onOpenChat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Literatursuche starten")
                }
            }
        }
        HintText(
            "Durchsucht werden offene Datenbanken: OpenAlex, Crossref, ERIC und die Deutsche Nationalbibliothek. Lizenzierte " +
                "Fachdatenbanken (FIS Bildung, PsycINFO, Web of Science) erreicht die App nicht – das Suchprotokoll nennt sie als Ergänzung.",
            Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ── Collection ──────────────────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Collection(c: AppContainer, initialList: String?, onMakeBibliography: (List<Reference>) -> Unit) {
    val all by c.literature.items.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var list by rememberSaveable { mutableStateOf(initialList) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var editing by remember { mutableStateOf<Reference?>(null) }
    var importing by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf(false) }
    var exportKind by remember { mutableStateOf("bib") }
    val lists = remember(all) { c.literature.lists() }
    val shown = remember(all, list, query) {
        all.filter { r -> list == null || r.lists.any { it.equals(list, true) } }
            .filter { r -> query.isBlank() || listOf(r.title, r.container, r.year, r.authors.joinToString { it.family }, r.note).any { it.contains(query, true) } }
            .sortedBy { Bibliography.sortKey(it).lowercase() }
    }
    val chosen = shown.filter { it.id in selected }.ifEmpty { shown }
    val export = rememberFileExport(
        when (exportKind) { "ris" -> "application/x-research-info-systems"; "json" -> "application/json"; else -> "application/x-bibtex" },
        onDone = { ok -> if (ok) Notices.info("Exportiert") else Notices.error("Export fehlgeschlagen") },
    ) { out ->
        val refs = chosen
        out.write(
            when (exportKind) {
                "ris" -> Ris.write(refs)
                "json" -> CslJson.write(refs)
                else -> BibTeX.write(refs)
            }.toByteArray(Charsets.UTF_8)
        )
    }
    val importFile = rememberFileImport(arrayOf("*/*")) { input ->
        val text = input.readBytes().toString(Charsets.UTF_8)
        val refs = ReferenceImport.read(text)
        c.literature.addAll(refs, list)
        Notices.info("${refs.size} Einträge übernommen")
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("In der Sammlung suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(list == null, { list = null; selected.clear() }, label = { Text("Alle (${all.size})") })
                    lists.forEach { l -> FilterChip(list == l, { list = l; selected.clear() }, label = { Text("$l (${all.count { r -> r.lists.any { it.equals(l, true) } }})") }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.isEmpty()) "${shown.size} Werke" else "${selected.size} ausgewählt",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { importing = true }) { Icon(Icons.Outlined.FileUpload, contentDescription = "Importieren") }
                var more by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { more = true }, enabled = shown.isNotEmpty()) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Literaturverzeichnis erstellen") }, leadingIcon = { Icon(Icons.Outlined.LibraryBooks, null) }, onClick = { more = false; onMakeBibliography(chosen) })
                        DropdownMenuItem(text = { Text("Als BibTeX exportieren") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { more = false; exportKind = "bib"; export("Literatur.bib") })
                        DropdownMenuItem(text = { Text("Als RIS exportieren (Citavi, EndNote)") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { more = false; exportKind = "ris"; export("Literatur.ris") })
                        DropdownMenuItem(text = { Text("Als CSL-JSON exportieren (Zotero)") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { more = false; exportKind = "json"; export("Literatur.json") })
                        if (c.researchTools.zotero.configured) {
                            DropdownMenuItem(text = { Text("Nach Zotero übertragen") }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) }, onClick = {
                                more = false
                                scope.launch {
                                    runCatching {
                                        val key = list?.let { c.researchTools.zotero.collection(it) }
                                        c.researchTools.zotero.add(chosen, key)
                                    }.onSuccess { (ok, errors) -> if (errors.isEmpty()) Notices.info("$ok Werke in Zotero gespeichert") else Notices.error("$ok gespeichert, Fehler: ${errors.first()}") }
                                        .onFailure { Notices.error(it.message ?: "Zotero nicht erreichbar") }
                                }
                            })
                        }
                        DropdownMenuItem(text = { Text("Zitate kopieren (APA-Kurzform)") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                            more = false
                            clipboard.setText(AnnotatedString(chosen.joinToString("\n") { it.short() }))
                        })
                        if (selected.isNotEmpty()) {
                            DropdownMenuItem(text = { Text("In Liste aufnehmen …") }, leadingIcon = { Icon(Icons.Outlined.PlaylistAdd, null) }, onClick = { more = false; moveTo = true })
                            if (list != null) DropdownMenuItem(text = { Text("Aus Liste „$list“ entfernen") }, onClick = {
                                more = false
                                selected.forEach { c.literature.removeFromList(it, list!!) }
                                selected.clear()
                            })
                            DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = {
                                more = false
                                val removed = all.filter { it.id in selected }
                                removed.forEach { c.literature.delete(it.id) }
                                selected.clear()
                                Notices.undo("${removed.size} Werke gelöscht", restore = { removed.forEach { c.literature.upsert(it) } })
                            })
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.LibraryBooks, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                Text(if (all.isEmpty()) "Noch keine Literatur gesammelt" else "Nichts gefunden", style = MaterialTheme.typography.titleSmall)
                HintText("Werke kommen aus der Literatursuche, aus „Merken“ im Chat oder per Import (BibTeX, RIS, CSL-JSON oder Text).")
                OutlinedButton(onClick = { importing = true }) { Text("Importieren") }
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { r ->
                AppCard(onClick = { editing = r }) {
                    Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                        Checkbox(r.id in selected, { on -> if (on) selected.add(r.id) else selected.remove(r.id) })
                        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.title.ifBlank { r.raw }, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            HintText(listOf(r.creators.take(3).joinToString(", ") { it.family } + if (r.creators.size > 3) " et al." else "", r.year, r.container).filter { it.isNotBlank() }.joinToString(" · "))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(r.type.label.substringBefore(" ("), PillTone.NEUTRAL)
                                r.citations?.let { StatusPill("$it Zitationen", PillTone.NEUTRAL) }
                                if (r.openAccessUrl.isNotBlank()) StatusPill("Open Access", PillTone.SUCCESS)
                                r.lists.take(2).forEach { StatusPill(it, PillTone.ACCENT) }
                            }
                            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (importing) ImportDialog(onDismiss = { importing = false }, onFile = { importing = false; importFile() }) { text ->
        val refs = ReferenceImport.read(text)
        c.literature.addAll(refs, list)
        Notices.info("${refs.size} Einträge übernommen")
        importing = false
    }
    if (moveTo) {
        var name by remember { mutableStateOf(list.orEmpty()) }
        AlertDialog(
            onDismissRequest = { moveTo = false },
            title = { Text("In Liste aufnehmen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name der Liste") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { lists.forEach { l -> FilterChip(name == l, { name = l }, label = { Text(l) }) } }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    c.literature.addAll(all.filter { it.id in selected }, name)
                    selected.clear()
                    moveTo = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { moveTo = false }) { Text("Abbrechen") } },
        )
    }
    editing?.let { r ->
        ReferenceEditor(r, onDismiss = { editing = null }, onDelete = {
            c.literature.delete(r.id)
            editing = null
            Notices.undo("Werk gelöscht", restore = { c.literature.upsert(r) })
        }) { updated ->
            c.literature.upsert(updated)
            editing = null
        }
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onFile: () -> Unit, onText: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Literatur importieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText("BibTeX, RIS (Citavi, EndNote), CSL-JSON (Zotero) oder Literaturangaben als Text einfügen – oder eine Datei wählen.")
                OutlinedTextField(text, { text = it }, minLines = 6, maxLines = 12, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onText(text) }) { Text("Übernehmen") } },
        dismissButton = {
            Row {
                TextButton(onClick = onFile) { Text("Datei wählen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

/** All fields of a reference, one per line, as the user can change them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReferenceEditor(initial: Reference, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (Reference) -> Unit) {
    var r by remember(initial.id) { mutableStateOf(initial) }
    fun people(list: List<Person>) = list.joinToString("; ") { if (it.literal || it.given.isBlank()) it.family else "${it.family}, ${it.given}" }
    fun parsePeople(s: String) = s.split(';').mapNotNull { p -> Person.parse(p)?.let { if (!p.contains(',') && p.trim().split(' ').size > 3) Person(p.trim(), literal = true) else it } }
    var authors by remember(initial.id) { mutableStateOf(people(initial.authors)) }
    var editors by remember(initial.id) { mutableStateOf(people(initial.editors)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Angaben bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceField("Art", RefType.entries.map { it to it.label }, r.type, { r = r.copy(type = it) })
                if (r.unparsed) HintText("Nicht gelesener Text: ${r.raw}")
                OutlinedTextField(authors, { authors = it }, label = { Text("Autor:innen (Nachname, Vorname; …)") }, modifier = Modifier.fillMaxWidth())
                if (r.type in setOf(RefType.CHAPTER, RefType.EDITED_BOOK, RefType.CONFERENCE) || editors.isNotBlank()) {
                    OutlinedTextField(editors, { editors = it }, label = { Text("Herausgeber:innen") }, modifier = Modifier.fillMaxWidth())
                }
                @Composable
                fun field(label: String, value: String, set: (String) -> Unit) =
                    OutlinedTextField(value, set, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = label != "Titel" && label != "Notiz")
                field("Jahr", r.year) { r = r.copy(year = it) }
                field("Titel", r.title) { r = r.copy(title = it) }
                field(
                    when (r.type) { RefType.ARTICLE -> "Zeitschrift"; RefType.WEB -> "Website"; RefType.CHAPTER -> "Titel des Sammelbands"; RefType.CONFERENCE -> "Tagungsband"; else -> "Reihe / enthalten in" },
                    r.container,
                ) { r = r.copy(container = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { field("Band", r.volume) { r = r.copy(volume = it) } }
                    Box(Modifier.weight(1f)) { field("Heft", r.issue) { r = r.copy(issue = it) } }
                    Box(Modifier.weight(1f)) { field("Seiten", r.pages) { r = r.copy(pages = it) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { field("Verlag", r.publisher) { r = r.copy(publisher = it) } }
                    Box(Modifier.weight(1f)) { field("Ort", r.place) { r = r.copy(place = it) } }
                    Box(Modifier.weight(0.6f)) { field("Auflage", r.edition) { r = r.copy(edition = it) } }
                }
                if (r.type == RefType.THESIS || r.type == RefType.REPORT) {
                    field("Hochschule / Einrichtung", r.institution) { r = r.copy(institution = it) }
                    field(if (r.type == RefType.THESIS) "Art der Arbeit" else "Berichtsnummer", r.genre) { r = r.copy(genre = it) }
                }
                field("DOI", r.doi) { r = r.copy(doi = Reference.cleanDoi(it)) }
                field("URL", r.url) { r = r.copy(url = it) }
                if (r.type == RefType.WEB) field("Abgerufen am (JJJJ-MM-TT)", r.accessed) { r = r.copy(accessed = it) }
                field("ISBN", r.isbn) { r = r.copy(isbn = it) }
                field("Notiz", r.note) { r = r.copy(note = it) }
                HintText("Vorschau (APA 7): " + CitationFormatter.format(r.copy(authors = parsePeople(authors), editors = parsePeople(editors)), CitationStyle.APA7_DE).replace("*", ""))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val final = r.copy(authors = parsePeople(authors), editors = parsePeople(editors))
                onSave(if (final.title.isNotBlank()) final.copy(raw = "") else final)
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

// ── Bibliography ────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BibliographyMaker(c: AppContainer, input: String, onInput: (String) -> Unit, fromCollection: List<Reference>, setFromCollection: (List<Reference>) -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var style by rememberSaveable { mutableStateOf(CitationStyle.parse(c.settings.current.citationStyle) ?: CitationStyle.APA7_DE) }
    var complete by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var result by remember { mutableStateOf<BibResult?>(null) }
    var editing by remember { mutableStateOf<BibEntry?>(null) }
    var export by remember { mutableStateOf<String?>(null) }
    var fileKind by remember { mutableStateOf("bib") }
    val saveFile = rememberFileExport(if (fileKind == "ris") "application/x-research-info-systems" else "application/x-bibtex", onDone = { ok -> if (!ok) Notices.error("Speichern fehlgeschlagen") }) { out ->
        val refs = result?.entries?.map { it.ref }.orEmpty()
        out.write((if (fileKind == "ris") Ris.write(refs) else BibTeX.write(refs)).toByteArray(Charsets.UTF_8))
    }

    fun run() {
        val refs = fromCollection + ReferenceImport.read(input)
        if (refs.isEmpty()) {
            Notices.error("Keine Literaturangaben erkannt")
            return
        }
        busy = true
        progress = 0 to refs.size
        scope.launch {
            val entries = if (complete) {
                runCatching { Bibliography.complete(c.researchTools.sources(null), refs) { done, total -> progress = done to total } }
                    .getOrElse { refs.map { r -> BibEntry(r) } }
            } else refs.map { BibEntry(it) }
            result = Bibliography.build(entries, style)
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Literaturverzeichnis erstellen", style = MaterialTheme.typography.titleMedium)
                    HintText("Alphabetisch sortiert, im gewählten Stil. Fehlende Angaben (DOI, Seiten, Heft, Verlag, Ort …) ergänzt die App aus Crossref, der Deutschen Nationalbibliothek, Google Books und OpenAlex – nur aus eindeutig passenden Einträgen.")
                    if (fromCollection.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill("${fromCollection.size} Werke aus der Sammlung", PillTone.ACCENT)
                            TextButton(onClick = { setFromCollection(emptyList()) }) { Text("Entfernen") }
                        }
                    }
                    OutlinedTextField(
                        input, onInput, label = { Text(if (fromCollection.isEmpty()) "Literaturangaben" else "Weitere Literaturangaben (optional)") },
                        placeholder = { Text("Je Angabe eine Zeile – oder BibTeX/RIS einfügen") }, minLines = 5, maxLines = 14, modifier = Modifier.fillMaxWidth(),
                    )
                    ChoiceField("Zitierstil", CitationStyle.entries.map { it to it.label }, style, { style = it; result = result?.let { r -> Bibliography.build(r.entries, it) } })
                    HintText(style.description)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fehlende Angaben online ergänzen", modifier = Modifier.weight(1f))
                        Switch(complete, { complete = it })
                    }
                    Button(onClick = ::run, enabled = !busy && (input.isNotBlank() || fromCollection.isNotEmpty()), modifier = Modifier.fillMaxWidth()) { Text("Verzeichnis erstellen") }
                    if (busy) {
                        LinearProgressIndicator(progress = { if (progress.second == 0) 0f else progress.first.toFloat() / progress.second }, modifier = Modifier.fillMaxWidth())
                        HintText("Prüfe ${progress.first} von ${progress.second} …")
                    }
                }
            }
        }
        result?.let { res ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val counts = res.entries.groupingBy { it.status }.eachCount()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        counts[EntryStatus.COMPLETED]?.let { StatusPill("$it ergänzt", PillTone.SUCCESS) }
                        counts[EntryStatus.FOUND]?.let { StatusPill("$it bestätigt", PillTone.SUCCESS) }
                        counts[EntryStatus.CORRECTED]?.let { StatusPill("$it abweichend", PillTone.WARNING) }
                        counts[EntryStatus.NOT_FOUND]?.let { StatusPill("$it nicht gefunden", PillTone.DANGER) }
                        res.entries.count { it.missing.isNotEmpty() }.takeIf { it > 0 }?.let { StatusPill("$it unvollständig", PillTone.WARNING) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(res.plain())); Notices.info("Kopiert") }) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text("Kopieren")
                        }
                        OutlinedButton(onClick = { export = res.markdown() }) {
                            Icon(Icons.Outlined.Description, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text("Dokument")
                        }
                        var more by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                DropdownMenuItem(text = { Text("Als Markdown kopieren") }, onClick = { more = false; clipboard.setText(AnnotatedString(res.markdown(null))) })
                                DropdownMenuItem(text = { Text("Im Dokument-Editor öffnen") }, onClick = {
                                    more = false
                                    DocsHandoff.open(c, "Literaturverzeichnis (${res.style.label})", res.markdown())
                                })
                                DropdownMenuItem(text = { Text("BibTeX speichern") }, onClick = { more = false; fileKind = "bib"; saveFile("Literatur.bib") })
                                DropdownMenuItem(text = { Text("RIS speichern") }, onClick = { more = false; fileKind = "ris"; saveFile("Literatur.ris") })
                                DropdownMenuItem(text = { Text("In die Sammlung übernehmen") }, onClick = {
                                    more = false
                                    c.literature.addAll(res.entries.map { it.ref }.filter { !it.unparsed }, "Literaturverzeichnis")
                                    Notices.info("In der Liste „Literaturverzeichnis“ gespeichert")
                                })
                                DropdownMenuItem(text = { Text("Prüfbericht kopieren") }, onClick = { more = false; clipboard.setText(AnnotatedString(res.report())) })
                            }
                        }
                    }
                }
            }
            items(res.entries.size) { i ->
                val e = res.entries[i]
                AppCard(onClick = { editing = e }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SelectionContainer { MarkdownText(res.lines[i]) }
                        val tone = when (e.status) {
                            EntryStatus.FOUND, EntryStatus.COMPLETED -> PillTone.SUCCESS
                            EntryStatus.CORRECTED -> PillTone.WARNING
                            EntryStatus.NOT_FOUND -> PillTone.DANGER
                            EntryStatus.UNCHECKED -> PillTone.NEUTRAL
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(e.status.symbol + " " + e.status.label, tone)
                            Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.added.isNotEmpty()) HintText("Ergänzt aus ${e.via}: ${e.added.joinToString(", ")}")
                        if (e.missing.isNotEmpty()) Text("Fehlt noch: ${e.missing.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        e.notes.forEach { HintText(it) }
                    }
                }
            }
        }
    }
    export?.let { md -> ExportDialog(c, "Literaturverzeichnis", md, onDismiss = { export = null }) }
    editing?.let { e ->
        ReferenceEditor(e.ref, onDismiss = { editing = null }) { updated ->
            val res = result ?: return@ReferenceEditor
            result = Bibliography.build(res.entries.map { if (it.ref.id == e.ref.id) it.copy(ref = updated) else it }, res.style)
            editing = null
        }
    }
}
