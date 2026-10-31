package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.AssistMode
import io.github.thepytales.llmhub.agent.FieldAssist
import io.github.thepytales.llmhub.agent.FieldSuggestion
import io.github.thepytales.llmhub.agent.FieldSuggestions
import io.github.thepytales.llmhub.data.Contact
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateCategory
import io.github.thepytales.llmhub.data.TemplateDraft
import io.github.thepytales.llmhub.data.TemplateField
import io.github.thepytales.llmhub.data.TemplateFields
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.TemplateLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun categoryIcon(category: TemplateCategory): ImageVector = when (category) {
    TemplateCategory.TUD -> Icons.Outlined.Description
    TemplateCategory.TEACHING -> Icons.Outlined.School
    TemplateCategory.THESES -> Icons.Outlined.HistoryEdu
    TemplateCategory.RESEARCH -> Icons.Outlined.Science
    TemplateCategory.COMMUNICATION -> Icons.Outlined.Email
    TemplateCategory.GENERAL -> Icons.AutoMirrored.Outlined.ShortText
    TemplateCategory.PERSONA -> Icons.Outlined.Person
    TemplateCategory.OWN -> Icons.Outlined.Bookmarks
}

fun toggleFavorite(c: AppContainer, id: String) = c.settings.update { s ->
    s.copy(favoriteTemplates = if (id in s.favoriteTemplates) s.favoriteTemplates - id else s.favoriteTemplates + id)
}

private const val FAVORITES = "FAVORITES"

/**
 * Searchable template library: filter chips per section, favourites marked with a star. Used by the template
 * picker in the chat and by the Vorlagen screen; [onOpen] decides what a tap does.
 */
@Composable
fun TemplateBrowser(
    c: AppContainer,
    onOpen: (Template) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: LazyListScope.() -> Unit = {},
) {
    val own by c.templates.items.collectAsStateWithLifecycle()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val all = remember(own) { TemplateLibrary.all(own) }
    val q = query.trim()
    val shown = all.filter { t ->
        (q.isEmpty() || listOf(t.name, t.description, t.content).any { it.contains(q, ignoreCase = true) }) && when (filter) {
            null -> true
            FAVORITES -> t.id in s.favoriteTemplates
            TemplateCategory.OWN.name -> !t.builtIn
            else -> t.section.name == filter
        }
    }
    LazyColumn(modifier, contentPadding = contentPadding) {
        header()
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("TU Vorlagen durchsuchen") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Suche leeren") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Alle") })
                if (s.favoriteTemplates.isNotEmpty()) {
                    FilterChip(
                        selected = filter == FAVORITES,
                        onClick = { filter = if (filter == FAVORITES) null else FAVORITES },
                        label = { Text("Favoriten") },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                TemplateCategory.entries.forEach { cat ->
                    val count = all.count { if (cat == TemplateCategory.OWN) !it.builtIn else it.section == cat }
                    if (count > 0) FilterChip(
                        selected = filter == cat.name,
                        onClick = { filter = if (filter == cat.name) null else cat.name },
                        label = { Text(cat.label) },
                    )
                }
            }
        }
        TemplateCategory.entries.forEach { cat ->
            val list = shown.filter { it.section == cat }
            if (list.isEmpty()) return@forEach
            item(key = "h_$cat") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp)) {
                    Text(cat.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(cat.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { t ->
                val favorite = t.id in s.favoriteTemplates
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(t) },
                    leadingContent = { Icon(categoryIcon(cat), contentDescription = null) },
                    headlineContent = { Text(if (t.builtIn) t.name else "${t.name} · eigene") },
                    supportingContent = { Text(t.description.ifBlank { t.content }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { toggleFavorite(c, t.id) }) {
                            Icon(
                                if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (favorite) "Aus den Favoriten entfernen" else "Zu den Favoriten",
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
        if (shown.isEmpty()) item(key = "empty") {
            Text(
                "Keine passende Vorlage gefunden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

/**
 * Fills the {{Feld}} placeholders of a prompt template. Every field shows what belongs into it; ✨ lets the model
 * tidy, formulate or shorten that one field. "Absender" fields start with the details from the settings, address
 * fields offer the address book, and what was typed is kept as draft until it is sent. [onInsert] puts the result
 * into the input field (to add files or change it), [onSend] sends it together with the single values.
 */
@Composable
fun TemplateFillDialog(
    c: AppContainer,
    template: Template,
    onInsert: (String) -> Unit,
    onSend: (text: String, values: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sender = c.settings.current.senderInfo
    val fields = remember(template.id) { template.fields }
    fun initial(f: TemplateField) = if (f.isSender) sender else ""
    val values = remember(template.id) {
        val draft = c.drafts.get(template.id)?.values.orEmpty()
        mutableStateMapOf<String, String>().apply { fields.forEach { put(it.label, draft[it.label] ?: initial(it)) } }
    }
    /** Text before the last ✨ change, for "Rückgängig". */
    val undo = remember(template.id) { mutableStateMapOf<String, String>() }
    var working by remember { mutableStateOf<String?>(null) }
    var customFor by remember { mutableStateOf<TemplateField?>(null) }
    var missing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contacts by c.contacts.items.collectAsStateWithLifecycle()
    /** Suggestions per field: from earlier entries and the address book, then what the model finds in memory and knowledge. */
    val suggestions = remember(template.id) { mutableStateMapOf<String, List<FieldSuggestion>>() }
    var suggesting by remember { mutableStateOf(false) }

    LaunchedEffect(template.id) {
        if (!c.settings.current.formSuggestions) return@LaunchedEffect
        suggestions.putAll(FieldSuggestions.local(c, template, values.toMap()))
        if (!FieldSuggestions.hasKnowledge(c)) return@LaunchedEffect
        suggesting = true
        try {
            FieldSuggestions.fromKnowledge(c, template, values.toMap()).forEach { (label, found) ->
                suggestions[label] = (found + suggestions[label].orEmpty()).distinctBy { it.text.trim() }.take(5)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            io.github.thepytales.llmhub.ErrorLog.record("Vorschläge für Vorlage", e)
        } finally {
            suggesting = false
        }
    }

    // Keeps the typed text as draft, so leaving the app or closing the form loses nothing.
    LaunchedEffect(template.id) {
        snapshotFlow { values.toMap() }.collect { current ->
            delay(400)
            val changed = fields.any { f -> current[f.label].orEmpty() != initial(f) }
            if (changed) c.drafts.upsert(TemplateDraft(template.id, current)) else c.drafts.delete(template.id)
        }
    }

    fun assist(f: TemplateField, mode: AssistMode, custom: String = "") {
        working = f.label
        scope.launch {
            try {
                val before = values[f.label].orEmpty()
                val text = FieldAssist.rewrite(c, template, f, values.toMap(), mode, custom)
                undo[f.label] = before
                values[f.label] = text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notices.error("✨ fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                working = null
            }
        }
    }

    // A letter needs its text; everything else may stay empty.
    val required = fields.firstOrNull { template.direct != null && it.multiline && !it.isAddress && !it.isSender }
    // Without any input the template name is sent, so that its instructions still go to the model.
    fun result() = TemplateFields.fill(template.content, values).ifBlank { template.name }
    fun finish(send: Boolean) {
        if (send && required != null && values[required.label].isNullOrBlank()) {
            missing = true
            return
        }
        val text = result()
        val map = values.toMap()
        c.drafts.delete(template.id)
        if (send) FieldSuggestions.remember(c, template, map)
        if (send) onSend(text, map) else onInsert(text)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(categoryIcon(template.section), contentDescription = null) },
        title = { Text(template.name) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (template.description.isNotBlank()) {
                    Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                fields.forEach { f ->
                    TemplateFieldInput(
                        field = f,
                        value = values[f.label].orEmpty(),
                        onChange = { values[f.label] = it; if (f == required) missing = false },
                        working = working == f.label,
                        enabled = working == null,
                        error = if (missing && f == required) "Bitte den Text eingeben – oder Stichpunkte schreiben und mit ✨ ausformulieren lassen." else null,
                        contacts = if (f.isAddress) contacts.sortedByDescending { it.lastUsed } else emptyList(),
                        onContact = { values[f.label] = it.address },
                        onDeleteContact = { c.contacts.delete(it.id) },
                        canUndo = undo.containsKey(f.label),
                        onUndo = { undo.remove(f.label)?.let { values[f.label] = it } },
                        onAssist = { mode -> if (mode == AssistMode.CUSTOM) customFor = f else assist(f, mode) },
                        suggestions = suggestions[f.label].orEmpty().filter { it.text.trim() != values[f.label].orEmpty().trim() },
                        onSuggestion = { text ->
                            undo[f.label] = values[f.label].orEmpty()
                            values[f.label] = text
                            if (f == required) missing = false
                        },
                    )
                }
                if (suggesting) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Suche Vorschläge in Gedächtnis und Wissenssammlung …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (sender.isBlank() && fields.any { it.isSender }) {
                    Text(
                        "Tipp: Ihre Absenderangaben einmal unter Einstellungen › Dokumente & Logos hinterlegen – dann sind sie hier schon ausgefüllt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (template.direct != null) "✨ überarbeitet ein Feld mit KI. Ihre Eingaben bleiben gespeichert, bis Sie senden."
                    else "Leere Felder werden weggelassen. ✨ überarbeitet ein Feld mit KI. „Einfügen“ übernimmt den Text ins Eingabefeld, z.B. um noch eine Datei anzuhängen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fields.any { f -> values[f.label].orEmpty() != initial(f) }) {
                    Text(
                        "Felder leeren",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            fields.forEach { values[it.label] = initial(it) }
                            undo.clear()
                            c.drafts.delete(template.id)
                        }.padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { finish(send = true) }, enabled = working == null) { Text(if (template.direct != null) "Erstellen" else "Senden") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
                if (template.direct == null) TextButton(onClick = { finish(send = false) }, enabled = working == null) { Text("Einfügen") }
            }
        },
    )
    customFor?.let { f ->
        var instruction by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customFor = null },
            title = { Text("✨ ${f.label}") },
            text = {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text("Was soll mit dem Text passieren?") },
                    placeholder = { Text("z.B. Füge einen Satz zur Frist hinzu") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = instruction.isNotBlank(), onClick = { customFor = null; assist(f, AssistMode.CUSTOM, instruction) }) { Text("Ausführen") }
            },
            dismissButton = { TextButton(onClick = { customFor = null }) { Text("Abbrechen") } },
        )
    }
}

/** One field of the template form: label, what belongs into it, ✨ and – for addresses – the address book. */
@Composable
private fun TemplateFieldInput(
    field: TemplateField,
    value: String,
    onChange: (String) -> Unit,
    working: Boolean,
    enabled: Boolean,
    error: String?,
    contacts: List<Contact>,
    onContact: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onAssist: (AssistMode) -> Unit,
    suggestions: List<FieldSuggestion> = emptyList(),
    onSuggestion: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            enabled = !working,
            isError = error != null,
            supportingText = (error ?: field.hint.ifBlank { null })?.let { t -> { Text(t) } },
            minLines = if (field.multiline) 3 else 1,
            maxLines = 14,
            trailingIcon = {
                Row {
                    if (contacts.isNotEmpty()) Box {
                        IconButton(onClick = { book = true }, enabled = enabled) {
                            Icon(Icons.Outlined.Contacts, contentDescription = "Adressbuch")
                        }
                        DropdownMenu(expanded = book, onDismissRequest = { book = false }) {
                            contacts.take(15).forEach { ct ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(ct.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                ct.address.lines().drop(1).joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    onClick = { book = false; onContact(ct) },
                                    trailingIcon = {
                                        IconButton(onClick = { onDeleteContact(ct) }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Aus dem Adressbuch löschen", modifier = Modifier.size(18.dp))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        if (working) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { menu = true }, enabled = enabled) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = "Mit KI überarbeiten", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            Text(
                                "Mit KI …",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            AssistMode.of(field).forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(if (mode == AssistMode.EXPAND && value.isBlank()) "Vorschlag schreiben" else mode.label) },
                                    onClick = { menu = false; onAssist(mode) },
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty() && !working) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = "Vorschläge", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                suggestions.forEach { sg ->
                    SuggestionChip(
                        onClick = { onSuggestion(sg.text) },
                        enabled = enabled,
                        label = {
                            Column(Modifier.widthIn(max = 260.dp)) {
                                Text(sg.text.lines().filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sg.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
        if (canUndo && !working) {
            Text(
                "↶ Rückgängig",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onUndo).padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

/** What a template does: its fields, the text and the instructions for the model. */
@Composable
fun TemplateDetailsDialog(
    c: AppContainer,
    template: Template,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showInstructions by remember { mutableStateOf(false) }
    val favorite = template.id in s.favoriteTemplates
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(categoryIcon(template.section), contentDescription = null) },
        title = { Text(template.name) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOf(template.section.label, if (template.builtIn) "in der App enthalten" else "eigene Vorlage").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (template.description.isNotBlank()) Text(template.description, style = MaterialTheme.typography.bodyMedium)
                val fields = template.fields
                Text(
                    when {
                        template.kind == TemplateKind.PERSONA -> "Persona: legt fest, wie sich das Modell im ganzen Chat verhält."
                        fields.isEmpty() -> "Fügt den Text ins Eingabefeld ein."
                        else -> "Beim Verwenden fragt die App: " + fields.joinToString(", ") { it.label } + "."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Text(template.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
                if (template.instructions.isNotBlank()) {
                    Text(
                        if (showInstructions) "Anweisungen an das Modell ausblenden" else "Anweisungen an das Modell anzeigen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showInstructions = !showInstructions }.padding(vertical = 4.dp),
                    )
                    if (showInstructions) {
                        Text(template.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { toggleFavorite(c, template.id) }) {
                        Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (favorite) "Favorit" else "Zu Favoriten")
                    }
                    if (!template.builtIn) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(TemplateLibrary.export(listOf(template))))
                            Notices.info("Vorlage kopiert – z.B. per Mail an Kolleg:innen schicken.")
                        }) { Text("Teilen") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onUse() }) { Text("Verwenden") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Schließen") }
                TextButton(onClick = { onDismiss(); onEdit() }) { Text(if (template.builtIn) "Anpassen" else "Bearbeiten") }
            }
        },
    )
}
