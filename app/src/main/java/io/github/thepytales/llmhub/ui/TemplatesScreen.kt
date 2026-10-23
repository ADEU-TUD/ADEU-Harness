package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.DefaultTemplates
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind

/** Manages prompt templates and personas (system prompts such as "Übersetzer" or "Code-Reviewer"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(c: AppContainer, onBack: () -> Unit, onUse: (Template) -> Unit) {
    val templates by c.templates.items.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Template?>(null) }
    var restore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text("Vorlagen") },
                actions = {
                    IconButton(onClick = { restore = true }) { Icon(Icons.Outlined.RestartAlt, contentDescription = "Standardvorlagen wiederherstellen") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Template(name = "", kind = TemplateKind.PERSONA, content = "") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Neue Vorlage") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 88.dp),
        ) {
            item {
                Text(
                    "Personas legen fest, wie sich das Modell verhält (System-Prompt). Prompt-Vorlagen fügen häufige Aufgaben ins " +
                        "Eingabefeld ein. Beides findest du im Chat über „+“ › Vorlagen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            TemplateKind.entries.forEach { kind ->
                val list = templates.filter { it.kind == kind }
                item(key = "h_$kind") {
                    Text(
                        if (kind == TemplateKind.PERSONA) "Personas" else "Prompt-Vorlagen",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                if (list.isEmpty()) item(key = "e_$kind") {
                    Text("Keine Vorlagen.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
                items(list, key = { it.id }) { t ->
                    ListItem(
                        modifier = Modifier.clickable { editing = t },
                        leadingContent = {
                            Icon(if (kind == TemplateKind.PERSONA) Icons.Outlined.Person else Icons.AutoMirrored.Outlined.ShortText, contentDescription = null)
                        },
                        headlineContent = { Text(t.name) },
                        supportingContent = { Text(t.description.ifBlank { t.content }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            TextButton(onClick = { onUse(t) }) { Text(if (kind == TemplateKind.PERSONA) "Chatten" else "Nutzen") }
                        },
                    )
                }
            }
        }
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
    if (restore) {
        ConfirmDialog(
            "Standardvorlagen wiederherstellen?",
            "Fehlende oder geänderte Standardvorlagen (Übersetzer, Code-Reviewer …) werden zurückgesetzt. Eigene Vorlagen bleiben erhalten.",
            "Wiederherstellen",
            onConfirm = { DefaultTemplates.all.forEach { c.templates.upsert(it) } },
            onDismiss = { restore = false },
        )
    }
}

@Composable
private fun TemplateEditor(template: Template, isNew: Boolean, onSave: (Template) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(template.name) }
    var description by remember { mutableStateOf(template.description) }
    var content by remember { mutableStateOf(template.content) }
    var kind by remember { mutableStateOf(template.kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neue Vorlage" else "Vorlage bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(if (k == TemplateKind.PERSONA) "Persona" else "Prompt") })
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Kurzbeschreibung (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (kind == TemplateKind.PERSONA) "System-Prompt" else "Text für das Eingabefeld") },
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && content.isNotBlank(),
                onClick = {
                    onSave(template.copy(name = name.trim(), description = description.trim(), content = content, kind = kind))
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
