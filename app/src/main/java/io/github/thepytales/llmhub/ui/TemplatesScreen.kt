package io.github.thepytales.llmhub.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateCategory
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.TemplateLibrary

/**
 * The template library: built-in templates for teaching, supervision, research, communication and TUD
 * documents, plus own ones. Own templates can be shared with colleagues as text and imported again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(c: AppContainer, onBack: () -> Unit, onUse: (Template) -> Unit) {
    val own by c.templates.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var details by remember { mutableStateOf<Template?>(null) }
    var editing by remember { mutableStateOf<Template?>(null) }
    var importing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text("Vorlagen") },
                actions = {
                    IconButton(onClick = { importing = true }) { Icon(Icons.Outlined.ContentPaste, contentDescription = "Vorlagen importieren") }
                    IconButton(enabled = own.isNotEmpty(), onClick = {
                        clipboard.setText(AnnotatedString(TemplateLibrary.export(own)))
                        Toast.makeText(context, "${own.size} eigene Vorlage(n) kopiert – z.B. per Mail an Kolleg:innen schicken.", Toast.LENGTH_LONG).show()
                    }) { Icon(Icons.Outlined.IosShare, contentDescription = "Eigene Vorlagen teilen") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Template(name = "", kind = TemplateKind.PROMPT, content = "") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Neue Vorlage") },
            )
        },
    ) { pad ->
        TemplateBrowser(
            c,
            onOpen = { details = it },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 88.dp),
            header = {
                item(key = "intro") {
                    Text(
                        "Vorlage wählen, Felder ausfüllen, senden – die Anweisungen an das Modell schickt die App unsichtbar mit. " +
                            "Personas legen fest, wie sich das Modell im ganzen Chat verhält. Mit dem Stern landen Vorlagen als " +
                            "Favoriten auf dem Startbildschirm. Im Chat: „+“ › Vorlagen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }

    details?.let { t ->
        TemplateDetailsDialog(
            c, t,
            onUse = { onUse(t) },
            onEdit = { editing = if (t.builtIn) TemplateLibrary.copyOf(t) else t },
            onDismiss = { details = null },
        )
    }
    editing?.let { t ->
        TemplateEditor(
            template = t,
            isNew = c.templates.get(t.id) == null,
            onSave = { c.templates.upsert(it) },
            onDelete = { c.templates.delete(t.id) },
            onDismiss = { editing = null },
        )
    }
    if (importing) {
        ImportTemplatesDialog(
            initial = clipboard.getText()?.text?.takeIf { "{" in it }.orEmpty(),
            onImport = { list ->
                list.forEach { c.templates.upsert(it) }
                Toast.makeText(context, "${list.size} Vorlage(n) importiert.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { importing = false },
        )
    }
}

@Composable
private fun ImportTemplatesDialog(initial: String, onImport: (List<Template>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val found = remember(text) { TemplateLibrary.import(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vorlagen importieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Text einfügen, den eine Kollegin oder ein Kollege über „Teilen“ kopiert hat.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
                Text(
                    if (found.isEmpty()) "Keine Vorlagen erkannt." else found.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = found.isNotEmpty(), onClick = { onImport(found); onDismiss() }) {
                Text(if (found.size > 1) "${found.size} importieren" else "Importieren")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun TemplateEditor(template: Template, isNew: Boolean, onSave: (Template) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(template.name) }
    var description by remember { mutableStateOf(template.description) }
    var content by remember { mutableStateOf(template.content) }
    var instructions by remember { mutableStateOf(template.instructions) }
    var kind by remember { mutableStateOf(template.kind) }
    var category by remember { mutableStateOf(TemplateCategory.parse(template.category) ?: TemplateCategory.OWN) }
    val categories = TemplateCategory.entries.filter { it != TemplateCategory.PERSONA }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neue Vorlage" else "Vorlage bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(if (k == TemplateKind.PERSONA) "Persona" else "Prompt") })
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Kurzbeschreibung (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind == TemplateKind.PROMPT) {
                    DropdownField("Bereich", category.label, categories.map { it.label }, { v -> category = categories.first { it.label == v } })
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (kind == TemplateKind.PERSONA) "System-Prompt" else "Text für das Eingabefeld") },
                    supportingText = if (kind == TemplateKind.PROMPT) ({
                        Text("{{Feld}} oder {{Feld|Beispiel}} wird beim Verwenden zu einem Eingabefeld; Zeilen mit leeren Feldern entfallen. Ein Feld „Absender“ füllt die App mit Ihren Angaben aus den Einstellungen.")
                    }) else null,
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == TemplateKind.PROMPT) {
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Anweisungen an das Modell (optional)") },
                        supportingText = { Text("Werden mitgeschickt, aber im Chat nicht angezeigt – z.B. Aufbau, Ton, Format.") },
                        minLines = 3,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && content.isNotBlank(),
                onClick = {
                    onSave(
                        template.copy(
                            name = name.trim(), description = description.trim(), content = content, kind = kind,
                            category = if (kind == TemplateKind.PROMPT && category != TemplateCategory.OWN) category.name else "",
                            instructions = if (kind == TemplateKind.PROMPT) instructions.trim() else "",
                        )
                    )
                    onDismiss()
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    IconButton(onClick = { onDelete(); onDismiss() }) { Icon(Icons.Outlined.Delete, contentDescription = "Löschen") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
