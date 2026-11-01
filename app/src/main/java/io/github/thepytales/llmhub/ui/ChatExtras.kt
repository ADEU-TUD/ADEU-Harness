package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ImageGallery
import io.github.thepytales.llmhub.data.Snippet
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateLibrary
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.research.Bibliography
import io.github.thepytales.llmhub.research.CitationStyle
import io.github.thepytales.llmhub.transcribe.TranscriptExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer

/** One entry of the suggestions above the input: /commands, templates, text blocks and @-mentions. */
data class InputSuggestion(val icon: ImageVector, val label: String, val detail: String, val apply: () -> Unit)

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]"), "")

/** The word being typed at the end of the input, if it starts with / or @. */
fun trigger(input: String): Pair<Char, String>? {
    val word = input.substringAfterLast(' ').substringAfterLast('\n')
    if (word.length > 40 || word.isEmpty()) return null
    val first = word.first()
    if (first != '/' && first != '@') return null
    // "/" only at the start of the input, so URLs and dates are no commands.
    if (first == '/' && input.trimStart() != word) return null
    return first to word.drop(1)
}

/**
 * Suggestions for "/" (templates, text blocks, literature search, documents) and "@" (transcripts, documents,
 * literature lists, knowledge) at the end of [input].
 */
fun inputSuggestions(
    c: AppContainer,
    input: String,
    templates: List<Template>,
    snippets: List<Snippet>,
    replace: (String) -> Unit,
    useTemplate: (Template) -> Unit,
    attach: (FileAttachment) -> Unit,
    startMode: (io.github.thepytales.llmhub.agent.ChatMode) -> Unit,
    openDocs: () -> Unit,
): List<InputSuggestion> {
    val (kind, query) = trigger(input) ?: return emptyList()
    val q = norm(query)
    val before = input.dropLast(query.length + 1)
    fun matches(name: String) = q.isEmpty() || norm(name).contains(q)
    val out = mutableListOf<InputSuggestion>()
    if (kind == '/') {
        // Every mode (AP-05) by its command.
        io.github.thepytales.llmhub.agent.ChatModes.matching(query).forEach { m ->
            out += InputSuggestion(m.iconVector(), "/" + m.command, m.explain) { replace(before); startMode(m) }
        }
        if (matches("Dateien") || matches("Dokumente")) out += InputSuggestion(Icons.Outlined.Description, "/Dateien", tr("Dateien und Dokumente öffnen")) { replace(before); openDocs() }
        snippets.filter { matches(it.name) }.take(5).forEach { s ->
            out += InputSuggestion(Icons.Outlined.ShortText, "/" + s.name, s.text.take(60).replace('\n', ' ')) { replace(before + s.text) }
        }
        TemplateLibrary.all(templates).filter { matches(it.name) }.take(8).forEach { t ->
            out += InputSuggestion(Icons.Outlined.Bookmarks, "/" + t.name, t.description.ifBlank { t.section.label }) { replace(before); useTemplate(t) }
        }
    } else {
        c.transcription.transcripts.value.filter { matches(it.title) }.take(4).forEach { t ->
            out += InputSuggestion(Icons.Outlined.GraphicEq, "@" + t.title, tr("Transkript · {0} Wörter", t.words)) {
                replace("$before@${t.title} ")
                kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                    c.transcription.load(t.id)?.let { tr -> attach(FileAttachment("Transkript – ${t.title}.txt", "text/plain", TranscriptExport.plainText(tr))) }
                }
            }
        }
        c.docs.current.filter { matches(it.title) }.take(4).forEach { d ->
            out += InputSuggestion(Icons.Outlined.Description, "@" + d.title, tr("Dokument · {0} Wörter", d.words)) {
                replace("$before@${d.title} ")
                attach(FileAttachment("Dokument – ${d.title}.md", "text/markdown", d.markdown()))
            }
        }
        c.literature.lists().filter { matches(it) }.take(4).forEach { l ->
            out += InputSuggestion(Icons.Outlined.MenuBook, "@$l", tr("Literaturliste")) {
                replace("$before@$l ")
                val style = CitationStyle.parse(c.settings.current.citationStyle) ?: CitationStyle.APA7_DE
                val result = Bibliography.build(c.literature.inList(l).map { io.github.thepytales.llmhub.research.BibEntry(it) }, style)
                attach(FileAttachment("Literaturliste – $l.md", "text/markdown", result.markdown(l) + "\n\nAbstracts und Notizen:\n" +
                    c.literature.inList(l).joinToString("\n\n") { r -> "- ${r.short()}" + (if (r.note.isNotBlank()) "\n  Notiz: ${r.note}" else "") + (if (r.abstract.isNotBlank()) "\n  Abstract: ${r.abstract.take(800)}" else "") }))
            }
        }
        c.knowledge.docs.value.filter { matches(it.name) }.take(3).forEach { k ->
            out += InputSuggestion(Icons.Outlined.Description, "@" + k.name, tr("Wissenssammlung")) {
                replace("$before@${k.name} ")
                attach(FileAttachment(k.name, "text/plain", "Aus der Wissenssammlung: „${k.name}“ – bitte mit search_knowledge darin suchen."))
            }
        }
    }
    return out
}

/** List of suggestions shown above the input field. */
@Composable
fun SuggestionList(items: List<InputSuggestion>) {
    if (items.isEmpty()) return
    Surface(
        color = LocalChatLook.current.card, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(items) { s ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = s.apply),
                    leadingContent = { Icon(s.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    headlineContent = { Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(s.detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/** Text blocks: insert, add, edit, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsSheet(c: AppContainer, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    val snippets by c.snippets.items.collectAsState()
    var editing by remember { mutableStateOf<Snippet?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Kurztexte"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = Snippet(name = "", text = "") }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Neu"))
            }
        }
        HintText(tr("Häufige Textbausteine – antippen fügt sie ins Eingabefeld ein. Im Eingabefeld auch mit „/Name“."), Modifier.padding(horizontal = 20.dp))
        if (snippets.isEmpty()) HintText(tr("Noch keine Kurztexte, z.B. „Signatur“, „Kurz und förmlich antworten“ oder „Als Tabelle“."), Modifier.padding(20.dp))
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            items(snippets, key = { it.id }) { s ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onInsert(s.text); onDismiss() },
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text(s.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = s }) { Icon(Icons.Outlined.Edit, contentDescription = tr("Bearbeiten")) }
                            IconButton(onClick = { c.snippets.delete(s.id); Notices.undo(tr("Kurztext gelöscht"), restore = { c.snippets.upsert(s) }) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"))
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.size(24.dp))
    }
    editing?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.name) }
        var text by remember(s.id) { mutableStateOf(s.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (s.name.isBlank()) tr("Neuer Kurztext") else tr("Kurztext bearbeiten")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(tr("Name (für /Name)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(text, { text = it }, label = { Text(tr("Text")) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && text.isNotBlank(), onClick = { c.snippets.upsert(s.copy(name = name.trim(), text = text)); editing = null }) { Text(tr("Speichern")) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

/** Pick paragraphs of an answer to quote them in the next question. */
@Composable
fun QuoteDialog(text: String, onDismiss: () -> Unit, onQuote: (String) -> Unit) {
    val blocks = remember(text) { DocBlocks.split(text) }
    val chosen = remember { mutableStateListOf<Int>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Zitieren und nachfragen")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HintText(tr("Absätze wählen, auf die sich die Nachfrage bezieht."))
                blocks.forEachIndexed { i, b ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable { if (i in chosen) chosen.remove(i) else chosen.add(i) }) {
                        Checkbox(i in chosen, { if (it) chosen.add(i) else chosen.remove(i) })
                        Text(DocBlocks.plainOf(b), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                onQuote(chosen.sorted().joinToString("\n>\n") { i -> DocBlocks.plainOf(blocks[i]).lines().joinToString("\n") { "> $it" } } + "\n\n")
            }) { Text(tr("Zitieren")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Why an answer was not helpful (optional, stays on the device). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RatingNoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    val reasons = listOf(tr("Sachlich falsch"), tr("Zu lang"), tr("Zu kurz"), tr("Thema verfehlt"), tr("Quellen fehlen"), tr("Falsches Format"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Was war nicht hilfreich?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { r -> androidx.compose.material3.FilterChip(note == r, { note = r }, label = { Text(r) }) }
                }
                OutlinedTextField(note, { note = it }, placeholder = { Text(tr("Optional")) }, modifier = Modifier.fillMaxWidth())
                HintText(tr("Bleibt auf diesem Gerät. Unter Einstellungen › Verbrauch sehen Sie, welche Modelle sich bewährt haben."))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text(tr("Speichern")) } },
        dismissButton = { TextButton(onClick = { onSave("") }) { Text(tr("Überspringen")) } },
    )
}

/** Attach something from the app: a transcript, a document, a literature list or a generated image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FromAppSheet(c: AppContainer, onFile: (FileAttachment) -> Unit, onImage: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val transcripts by c.transcription.transcripts.collectAsState()
    val docs by c.docs.items.collectAsState()
    val literature by c.literature.items.collectAsState()
    val gallery = remember { ImageGallery(File(c.appContext.filesDir, "images")) }
    val images by gallery.items.collectAsState()
    LaunchedEffect(Unit) { gallery.load() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun done(action: () -> Unit) { action(); onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyColumn(Modifier.heightIn(max = 560.dp)) {
            item { SectionHeader(tr("Transkripte")) }
            if (transcripts.isEmpty()) item { HintText(tr("Keine Transkripte."), Modifier.padding(horizontal = 20.dp)) }
            items(transcripts.take(8), key = { "t" + it.id }) { t ->
                Pick(Icons.Outlined.GraphicEq, t.title, tr("{0} Wörter", t.words)) {
                    done { scope.launch { c.transcription.load(t.id)?.let { tr -> onFile(FileAttachment("Transkript – ${t.title}.txt", "text/plain", TranscriptExport.plainText(tr))) } } }
                }
            }
            item { SectionHeader(tr("Dokumente")) }
            if (docs.isEmpty()) item { HintText(tr("Keine Dokumente im Editor."), Modifier.padding(horizontal = 20.dp)) }
            items(docs.take(8), key = { "d" + it.id }) { d ->
                Pick(Icons.Outlined.Description, d.title, tr("{0} Wörter", d.words)) { done { onFile(FileAttachment("Dokument – ${d.title}.md", "text/markdown", d.markdown())) } }
            }
            val lists = c.literature.lists()
            item { SectionHeader(tr("Literaturlisten")) }
            if (lists.isEmpty()) item { HintText(tr("Keine Literaturlisten."), Modifier.padding(horizontal = 20.dp)) }
            items(lists.take(8), key = { "l$it" }) { l ->
                val works = literature.filter { r -> r.lists.any { it.equals(l, true) } }
                Pick(Icons.Outlined.MenuBook, l, tr("{0} Werke", works.size)) {
                    done {
                        val style = CitationStyle.parse(c.settings.current.citationStyle) ?: CitationStyle.APA7_DE
                        onFile(FileAttachment("Literaturliste – $l.md", "text/markdown", Bibliography.build(works.map { io.github.thepytales.llmhub.research.BibEntry(it) }, style).markdown(l)))
                    }
                }
            }
            item { SectionHeader(tr("Erzeugte Bilder")) }
            if (images.isEmpty()) item { HintText(tr("Keine Bilder in der Galerie."), Modifier.padding(horizontal = 20.dp)) }
            items(images.take(8), key = { "i" + it.id }) { img ->
                Pick(Icons.Outlined.Image, img.prompt.take(60), img.size) {
                    done {
                        scope.launch {
                            val bytes = withContext(Dispatchers.IO) { runCatching { gallery.file(img).readBytes() }.getOrNull() }
                            if (bytes != null) onImage("data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Pick(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle) },
    )
}

/** Chats as JSON: a complete, re-importable copy (all versions, settings, attachments' text). */
object ChatJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun write(conv: Conversation): String = json.encodeToString(Conversation.serializer(), conv)

    /** Reads a chat (or a list of chats); each gets a new id so nothing is overwritten. */
    fun read(text: String): List<Conversation> {
        val t = text.trim()
        val list = if (t.startsWith("[")) json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Conversation.serializer()), t)
        else listOf(json.decodeFromString(Conversation.serializer(), t))
        return list.map { it.copy(id = java.util.UUID.randomUUID().toString(), title = it.title + tr(" (importiert)"), folderId = null, taskId = null, temporary = false) }
    }
}

/** Text of a chat item for the search in the open chat. */
fun itemText(item: ChatItem): String = when (item) {
    is ChatItem.User -> item.m.content
    is ChatItem.Assistant -> splitThinking(item.m.content).second
}

fun matchCount(conv: Conversation, query: String): Int =
    if (query.isBlank()) 0 else chatItems(conv).count { itemText(it).contains(query, ignoreCase = true) }

/** Search field in the top bar of the chat with the number of hits and jumping between them. */
@Composable
fun ChatSearchField(query: String, onQuery: (String) -> Unit, count: Int, hit: Int, onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.TextField(
            query, onQuery, singleLine = true, placeholder = { Text(tr("Im Chat suchen")) },
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 260.dp).focusRequester(focus),
        )
        Text(if (count == 0) "0" else "${(hit.coerceAtMost(count - 1)) + 1}/$count", style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onPrev, enabled = count > 0) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = tr("Vorheriger Treffer")) }
        IconButton(onClick = onNext, enabled = count > 0) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = tr("Nächster Treffer")) }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Suche schließen")) }
    }
}
