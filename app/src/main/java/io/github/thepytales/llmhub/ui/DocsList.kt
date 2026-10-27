package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.OfficeImport
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SlideDeck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Colour and icon of each kind (like the Word, PowerPoint and Excel icons). */
fun kindIcon(kind: DocKind): ImageVector = when (kind) {
    DocKind.DOC -> Icons.Outlined.Description
    DocKind.SLIDES -> Icons.Outlined.Slideshow
    DocKind.SHEET -> Icons.Outlined.TableChart
    DocKind.MARKDOWN -> Icons.Outlined.Code
}

fun kindColor(kind: DocKind): Color = when (kind) {
    DocKind.DOC -> Color(0xFF2B579A)
    DocKind.SLIDES -> Color(0xFFC43E1C)
    DocKind.SHEET -> Color(0xFF217346)
    DocKind.MARKDOWN -> Color(0xFF3D4451)
}

/**
 * Documents in the corporate design of TU Dresden: they open with the TUD look (logo, unit, TUD blue) and export as
 * PDF or Word in that design.
 */
val TUD_STARTERS = listOf(
    "Ergebnisprotokoll" to "# Ergebnisprotokoll\n\n| | |\n| --- | --- |\n| Datum | |\n| Ort | |\n| Leitung | |\n| Protokoll | |\n| Anwesend | |\n| Entschuldigt | |\n\n## Tagesordnung\n\n1. Begrüßung und Genehmigung der Tagesordnung\n2. \n3. Verschiedenes\n\n## TOP 1 – Begrüßung\n\n\n\n## TOP 2 – \n\n**Beschluss:** \n\n## Aufgaben\n\n| Aufgabe | Verantwortlich | Bis |\n| --- | --- | --- |\n| | | |\n",
    "Aktennotiz" to "# Aktennotiz\n\n**Datum:** \n**Verfasst von:** \n**Betreff:** \n\n## Sachverhalt\n\n\n\n## Ergebnis / weiteres Vorgehen\n\n- \n",
    "Einladung" to "# Einladung\n\nSehr geehrte Damen und Herren,\n\nwir laden Sie herzlich ein zu\n\n> [!NOTE]\n> **Veranstaltung** · **Datum, Uhrzeit** · **Ort**\n\n## Programm\n\n| Zeit | Programmpunkt |\n| --- | --- |\n| | |\n\nWir bitten um Anmeldung bis zum … an …\n\nMit freundlichen Grüßen\n\n",
    "Handout / Thesenpapier" to "# Thema\n\n**Seminar:** · **Dozent:in:** · **Referent:innen:** · **Datum:** \n\n## Thesen\n\n1. \n2. \n3. \n\n## Zentrale Begriffe\n\n- **Begriff:** Erklärung\n\n## Diskussionsfragen\n\n- \n\n## Literatur\n\n- \n",
    "Stellungnahme" to "# Stellungnahme zu …\n\n## Anlass\n\n\n\n## Bewertung\n\n\n\n## Empfehlung\n\n\n",
    "Merkblatt" to "# Merkblatt: …\n\n> [!NOTE]\n> Das Wichtigste in einem Satz.\n\n## Worum es geht\n\n\n\n## So gehen Sie vor\n\n1. \n2. \n3. \n\n## Ansprechpersonen\n\n| Name | Kontakt | Sprechzeit |\n| --- | --- | --- |\n| | | |\n",
    "Projektskizze" to "# Projektskizze: …\n\n## Ausgangslage und Problemstellung\n\n\n\n## Ziele und Forschungsfragen\n\n- \n\n## Methodisches Vorgehen\n\n\n\n## Arbeits- und Zeitplan\n\n| Arbeitspaket | Zeitraum | Ergebnis |\n| --- | --- | --- |\n| | | |\n\n## Ressourcen\n\n\n",
)

/** Templates for Markdown files. */
private val MARKDOWN_STARTERS = listOf(
    "README" to "# Projektname\n\nKurze Beschreibung in einem Satz.\n\n## Installation\n\n```bash\n\n```\n\n## Verwendung\n\n\n\n## Lizenz\n\n",
    "Notiz" to "# Notiz\n\nTags: #\n\n## Gedanken\n\n- \n\n## Verknüpfungen\n\n- [[ ]]\n",
)

/** Templates for new presentations and tables. */
private val SLIDE_STARTERS = listOf(
    "Seminarsitzung" to "# Thema der Sitzung\n\n## Seminar · Datum\n\n---\n\n## Ablauf\n\n- Einstieg\n- Input\n- Arbeitsphase\n- Sicherung\n\n---\n\n## Lernziele\n\n- Die Teilnehmenden können …\n\n---\n\n## Arbeitsauftrag\n\n- \n\nNotizen: Zeit: 20 Minuten\n\n---\n\n## Zusammenfassung\n\n- \n",
    "Vortrag" to "# Titel des Vortrags\n\n## Name · Anlass\n\n---\n\n## Ausgangslage\n\n- \n\n---\n\n## Fragestellung\n\n> „Leitfrage des Vortrags“\n\n---\n\n## Ergebnisse\n\n- \n\n---\n\n## Fazit\n\n- \n\n---\n\n## Vielen Dank!\n\nFragen und Diskussion\n",
)

private val SHEET_STARTERS = listOf(
    "Notenliste" to SheetEngine.markdown(
        listOf(listOf("Name", "Punkte", "Note", "Bestanden")) +
            (2..6).map { r -> listOf("", "", "=WENN(B$r>=90;1;WENN(B$r>=75;2;WENN(B$r>=60;3;WENN(B$r>=50;4;5))))", "=WENN(B$r>=50;\"ja\";\"nein\")") } +
            listOf(listOf("Mittelwert", "=MITTELWERT(B2:B6)", "=MITTELWERT(C2:C6)", "=ZÄHLENWENN(D2:D6;\"ja\")")),
    ),
    "Budget" to SheetEngine.markdown(
        listOf(listOf("Posten", "Menge", "Einzelpreis", "Summe")) +
            (2..5).map { r -> listOf("", "", "", "=B$r*C$r") } + listOf(listOf("Gesamt", "", "", "=SUMME(D2:D5)")),
    ),
    "Zeitplan" to SheetEngine.markdown(listOf(listOf("Aufgabe", "Verantwortlich", "Beginn", "Ende", "Status")) + List(6) { List(5) { "" } }),
)

/** Creates a new document of [kind] (in the default storage folder) and returns it. */
fun newLiveDoc(c: AppContainer, kind: DocKind, markdown: String? = null, title: String? = null, style: String? = null): LiveDoc {
    val t = title ?: kind.newTitle
    val md = markdown ?: when (kind) {
        DocKind.DOC, DocKind.MARKDOWN -> "# $t\n\n"
        DocKind.SLIDES -> SlideDeck.blank(t)
        DocKind.SHEET -> SheetEngine.blank()
    }
    val doc = LiveDoc.create(DocBlocks.titleOf(md)?.takeIf { it.isNotBlank() } ?: t, md, "Sie", kind = kind, style = style).copy(vault = c.docs.defaultVault)
    c.docs.save(doc)
    return doc
}

/**
 * All documents, presentations and tables (like the start page of Google Docs or Office): create new ones, open
 * Word, PowerPoint and Excel files, filter by kind and storage folder.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocsListScreen(c: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val docs by c.docs.items.collectAsState()
    val vaults by c.docs.vaults.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf<DocKind?>(null) }
    var vaultFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    var aiCreate by remember { mutableStateOf<DocKind?>(null) }

    LaunchedEffect(vaults.size) {
        while (vaults.isNotEmpty()) {
            withContext(Dispatchers.IO) { runCatching { c.docs.refresh() } }
            delay(5000)
        }
    }

    val importFile = rememberMediaImport(anyFile = true) { name, _, input ->
        val bytes = withContext(Dispatchers.IO) { input.use { it.readBytes() } }
        val imported = OfficeImport.read(name, bytes)
        if (imported == null) {
            Notices.error("„$name“ lässt sich nicht im Editor öffnen (Word, PowerPoint, Excel, CSV, Markdown und Text gehen).")
        } else {
            val doc = OfficeImport.toLiveDoc(imported).copy(vault = c.docs.defaultVault)
            withContext(Dispatchers.IO) { DocsHandoff.saveMedia(c, doc, imported) }
            c.docs.save(doc)
            onOpen(doc.id)
        }
    }

    val shown = docs.filter { d ->
        (kindFilter == null || d.docKind == kindFilter) && (vaultFilter == null || d.vault == vaultFilter.takeIf { it != "app" }) &&
            (!starredOnly || d.starred) &&
            (query.isBlank() || d.title.contains(query, true) || d.tabs.any { t -> t.markdown.contains(query, true) })
    }.sortedWith(compareByDescending<LiveDoc> { it.starred }.thenByDescending { it.updatedAt })

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) }, title = { Text("Dokumente") },
                actions = {
                    TextButton(onClick = importFile) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Datei öffnen")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 900.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Neu erstellen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DocKind.entries.forEach { k ->
                            NewTile(k, onBlank = { onOpen(newLiveDoc(c, k).id) }, onAi = { aiCreate = k }, onTemplate = { title, md, style -> onOpen(newLiveDoc(c, k, md, title, style).id) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        query, { query = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Dokumente durchsuchen") },
                        singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(kindFilter == null && !starredOnly, { kindFilter = null; starredOnly = false }, label = { Text("Alle  ${docs.size}") })
                        FilterChip(starredOnly, { starredOnly = !starredOnly }, label = { Text("Angeheftet") }, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(16.dp)) })
                        DocKind.entries.forEach { k ->
                            FilterChip(
                                kindFilter == k, { kindFilter = if (kindFilter == k) null else k },
                                label = { Text(when (k) { DocKind.DOC -> "Dokumente"; DocKind.SLIDES -> "Präsentationen"; DocKind.SHEET -> "Tabellen"; DocKind.MARKDOWN -> "Markdown" } + "  ${docs.count { it.docKind == k }}") },
                                leadingIcon = { Icon(kindIcon(k), null, Modifier.size(16.dp), tint = kindColor(k)) },
                            )
                        }
                        if (vaults.isNotEmpty()) {
                            FilterChip(vaultFilter == "app", { vaultFilter = if (vaultFilter == "app") null else "app" }, label = { Text("In der App") })
                            vaults.forEach { v ->
                                FilterChip(vaultFilter == v.id, { vaultFilter = if (vaultFilter == v.id) null else v.id }, label = { Text(v.name) }, leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) })
                            }
                        }
                    }
                }
                if (shown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (docs.isEmpty()) "Noch keine Dokumente" else "Nichts gefunden", style = MaterialTheme.typography.titleSmall)
                        HintText(
                            "Dokumente entstehen hier über „Neu erstellen“, beim Öffnen einer Word-, PowerPoint- oder Excel-Datei, wenn die KI im Chat " +
                                "ein Dokument schreibt, oder über „Im Editor öffnen“ an Antworten und Anhängen.",
                        )
                    }
                }
                items(shown, key = { it.id }) { d -> DocRow(c, d, onOpen) }
            }
        }
    }

    aiCreate?.let { k ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { aiCreate = null },
            title = { Text("${k.label} mit KI erstellen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText("Beschreibe, was entstehen soll. Die KI schreibt den Entwurf direkt in den Editor – danach bearbeitet ihr ihn gemeinsam weiter.")
                    OutlinedTextField(
                        text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                when (k) {
                                    DocKind.DOC -> "z. B. Seminarplan für „Einführung in die Didaktik“, 14 Sitzungen, mit Lektüre"
                                    DocKind.SLIDES -> "z. B. 10 Folien zur Einführung in formatives Assessment für Lehramtsstudierende"
                                    DocKind.SHEET -> "z. B. Bewertungstabelle für 25 Hausarbeiten mit 4 gewichteten Kriterien und Endnote"
                                    DocKind.MARKDOWN -> "z. B. README für ein Forschungsrepositorium mit Installation und Datenstruktur"
                                },
                            )
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    aiCreate = null
                    val doc = newLiveDoc(c, k, title = k.newTitle)
                    DocsAi.send(c, doc, "Neu mit KI", text.trim(), DocsAi.instructions(doc) + " Das ${k.label} ist noch leer: Schreib einen vollständigen ersten Entwurf und gib ihm mit edit_document (title) einen passenden Titel.")
                    onOpen(doc.id)
                }) { Text("Erstellen") }
            },
            dismissButton = { TextButton(onClick = { aiCreate = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun NewTile(kind: DocKind, onBlank: () -> Unit, onAi: () -> Unit, onTemplate: (String, String, String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val color = kindColor(kind)
    Box {
        Surface(
            onClick = onBlank, shape = RoundedCornerShape(14.dp), color = LocalChatLook.current.card, border = LocalChatLook.current.cardBorder,
            modifier = Modifier.width(260.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = color, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(kindIcon(kind), contentDescription = null, tint = Color.White) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (kind) {
                            DocKind.DOC -> "wie Word / Google Docs, auch im TUD-Design"
                            DocKind.SLIDES -> "wie PowerPoint, im TUD-Design"
                            DocKind.SHEET -> "wie Excel, mit Formeln"
                            DocKind.MARKDOWN -> "Quelltext mit Vorschau, wie Obsidian"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onAi) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "${kind.label} mit KI erstellen", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Vorlagen") }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Leer") }, onClick = { menu = false; onBlank() })
            DropdownMenuItem(text = { Text("Mit KI erstellen …") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) }, onClick = { menu = false; onAi() })
            val starters = when (kind) { DocKind.DOC -> DOC_STARTERS; DocKind.SLIDES -> SLIDE_STARTERS; DocKind.SHEET -> SHEET_STARTERS; DocKind.MARKDOWN -> MARKDOWN_STARTERS }
            starters.forEach { (label, md) -> DropdownMenuItem(text = { Text("Vorlage: $label") }, onClick = { menu = false; onTemplate(label, md, null) }) }
            if (kind == DocKind.DOC) {
                HorizontalDivider()
                Text("Im TUD-Design", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                DropdownMenuItem(text = { Text("Leeres TUD-Dokument") }, onClick = { menu = false; onTemplate(DocKind.DOC.newTitle, "# ${DocKind.DOC.newTitle}\n\n", DocStyle.TUD.name) })
                TUD_STARTERS.forEach { (label, md) -> DropdownMenuItem(text = { Text("TUD: $label") }, onClick = { menu = false; onTemplate(label, md, DocStyle.TUD.name) }) }
            }
        }
    }
}

@Composable
private fun DocRow(c: AppContainer, d: LiveDoc, onOpen: (String) -> Unit) {
    AppCard(onClick = { onOpen(d.id) }) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = kindColor(d.docKind).copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(kindIcon(d.docKind), contentDescription = d.docKind.label, tint = kindColor(d.docKind), modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(d.updatedAt)),
                    d.lastAuthor,
                    when (d.docKind) {
                        DocKind.DOC, DocKind.MARKDOWN -> "${d.words} Wörter"
                        DocKind.SLIDES -> "${SlideDeck.parse(d.tabs.first().markdown).size} Folien"
                        DocKind.SHEET -> "${d.tabs.size} Blatt"
                    },
                    d.tabs.size.takeIf { it > 1 && d.docKind == DocKind.DOC }?.let { "$it Tabs" },
                    d.openComments.takeIf { it > 0 }?.let { "$it Kommentare" },
                    d.vault?.let { v -> c.docs.vault(v)?.name?.let { "📁 $it" } },
                )
                HintText(meta.joinToString(" · "))
            }
            IconButton(onClick = { c.docs.update(d.id) { it.copy(starred = !it.starred) } }) {
                Icon(if (d.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = if (d.starred) "Nicht mehr anheften" else "Anheften", tint = if (d.starred) Color(0xFFF2B01E) else MaterialTheme.colorScheme.outline)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Duplizieren") }, onClick = {
                        menu = false
                        c.docs.save(LiveDoc.create(d.title + " (Kopie)", d.markdown(), "Sie", kind = d.docKind).copy(tabs = d.tabs.map { it.copy(id = java.util.UUID.randomUUID().toString()) }, vault = d.vault))
                    })
                    DropdownMenuItem(text = { Text("Löschen") }, onClick = {
                        menu = false
                        c.docs.delete(d.id)
                        Notices.undo("${d.docKind.label} gelöscht", restore = { c.docs.save(d) })
                    })
                }
            }
        }
    }
}
