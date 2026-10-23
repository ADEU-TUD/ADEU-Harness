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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.net.RepoSummary

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Dialog with one text field (rename, new folder …). */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirm: String = "Speichern",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String?, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Moves a chat into a folder (or out of it); new folders can be created here. */
@Composable
fun FolderPickerDialog(c: AppContainer, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val folders by c.folders.items.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("In Ordner verschieben") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                ListItem(
                    colors = transparentItem(),
                    modifier = Modifier.clickable { onPick(null); onDismiss() },
                    leadingContent = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                    headlineContent = { Text("Kein Ordner") },
                    trailingContent = { if (current == null) Icon(Icons.Outlined.Check, contentDescription = null) },
                )
                folders.sortedBy { it.name.lowercase() }.forEach { f ->
                    ListItem(
                        colors = transparentItem(),
                        modifier = Modifier.clickable { onPick(f.id); onDismiss() },
                        leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        headlineContent = { Text(f.name) },
                        trailingContent = { if (current == f.id) Icon(Icons.Outlined.Check, contentDescription = null) },
                    )
                }
                ListItem(
                    colors = transparentItem(),
                    modifier = Modifier.clickable { creating = true },
                    leadingContent = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    headlineContent = { Text("Neuer Ordner …") },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
    if (creating) {
        TextInputDialog("Neuer Ordner", "", "Name", confirm = "Erstellen", onConfirm = { name ->
            val folder = Folder(name = name)
            c.folders.upsert(folder)
            onPick(folder.id)
            onDismiss()
        }, onDismiss = { creating = false })
    }
}

/** System prompt of a chat; personas from the templates can be applied with one tap. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SystemPromptDialog(
    c: AppContainer,
    current: String?,
    onSave: (prompt: String, persona: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val templates by c.templates.items.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(current.orEmpty()) }
    var persona by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Persona / System-Prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val personas = templates.filter { it.kind == TemplateKind.PERSONA }
                if (personas.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        personas.forEach { t ->
                            FilterChip(
                                selected = text == t.content,
                                onClick = { text = t.content; persona = t.name },
                                label = { Text(t.name) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; persona = null },
                    minLines = 4,
                    maxLines = 10,
                    placeholder = { Text("z.B. Antworte immer kurz und auf Deutsch.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, persona ?: templates.firstOrNull { it.content == text }?.name); onDismiss() }) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (!current.isNullOrBlank()) TextButton(onClick = { onSave("", null); onDismiss() }) { Text("Entfernen") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

/** Chooses the agent environment of a chat. */
@Composable
fun EnvironmentPickerDialog(c: AppContainer, current: String?, onPick: (String?) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val envs by c.environments.items.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Umgebung") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Umgebungen enthalten Variablen (z.B. API-Keys) und Regeln für den Internetzugriff des Agenten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ListItem(
                    colors = transparentItem(),
                    modifier = Modifier.clickable { onPick(null); onDismiss() },
                    headlineContent = { Text("Keine") },
                    trailingContent = { if (current == null) Icon(Icons.Outlined.Check, contentDescription = null) },
                )
                envs.forEach { e ->
                    ListItem(
                        colors = transparentItem(),
                        modifier = Modifier.clickable { onPick(e.id); onDismiss() },
                        headlineContent = { Text(e.name) },
                        supportingContent = { Text(envSummary(e), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { if (current == e.id) Icon(Icons.Outlined.Check, contentDescription = null) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onManage() }) { Text("Verwalten") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

fun envSummary(e: AgentEnvironment): String = listOf(
    "Internet: ${e.network.label}",
    if (e.variables.isEmpty()) "keine Variablen" else "${e.variables.size} Variable${if (e.variables.size == 1) "" else "n"}",
).joinToString(" · ")

/** Picks a template: prompts go into the input field, personas become the system prompt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(c: AppContainer, onPick: (Template) -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val templates by c.templates.items.collectAsStateWithLifecycle()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vorlagen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDismiss(); onManage() }) { Text("Verwalten") }
        }
        LazyColumn {
            TemplateKind.entries.forEach { kind ->
                val list = templates.filter { it.kind == kind }
                if (list.isEmpty()) return@forEach
                item(key = "h_$kind") {
                    Text(
                        if (kind == TemplateKind.PERSONA) "Personas – setzen den System-Prompt" else "Prompts – fügen Text ins Eingabefeld ein",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { t ->
                    ListItem(
                        colors = transparentItem(),
                        modifier = Modifier.clickable { onPick(t); onDismiss() },
                        leadingContent = {
                            Icon(if (kind == TemplateKind.PERSONA) Icons.Outlined.Person else Icons.AutoMirrored.Outlined.ShortText, contentDescription = null)
                        },
                        headlineContent = { Text(t.name) },
                        supportingContent = {
                            Text(t.description.ifBlank { t.content }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
fun RepoPickerDialog(
    c: AppContainer,
    enabled: Boolean,
    selectedRepo: String?,
    selectedBranch: String?,
    hasToken: Boolean,
    onApply: (Boolean, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var repos by remember { mutableStateOf<List<RepoSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var toolsOn by remember { mutableStateOf(enabled) }

    LaunchedEffect(hasToken) {
        if (hasToken) {
            try {
                repos = c.github.listRepos()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub-Repository") },
        text = {
            if (!hasToken) {
                Text("Hinterlege zuerst in den Einstellungen ein GitHub-Token, damit das Modell auf deine Repositories zugreifen kann.")
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GitHub-Werkzeuge")
                        Text(
                            selectedRepo?.let { "Aktuell: $it" + (selectedBranch?.let { b -> " · $b" } ?: "") } ?: "Kein Repository gewählt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = toolsOn, onCheckedChange = {
                        toolsOn = it
                        onApply(it, if (it) selectedRepo else null, if (it) selectedBranch else null)
                        if (!it) onDismiss()
                    })
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Suchen oder owner/repo bzw. URL eingeben") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    repos == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        ListItem(
                            colors = transparentItem(),
                            modifier = Modifier.clickable { onApply(true, null, null); onDismiss() },
                            headlineContent = { Text("Ohne festes Repository") },
                            supportingContent = { Text("Das Modell wählt bei Bedarf selbst.") },
                        )
                        HorizontalDivider()
                        val typed = GitHubTools.parseRepo(query)
                        if (typed != null && repos!!.none { it.fullName.equals(typed, ignoreCase = true) }) {
                            ListItem(
                                colors = transparentItem(),
                                modifier = Modifier.clickable { onApply(true, typed, null); onDismiss() },
                                headlineContent = { Text("„$typed“ verwenden") },
                                supportingContent = { Text("Auch fremde öffentliche Repositories") },
                            )
                        }
                        repos!!.filter {
                            it.fullName.contains(query.trim(), ignoreCase = true) || typed != null && it.fullName.equals(typed, ignoreCase = true)
                        }.forEach { r ->
                            ListItem(
                                colors = transparentItem(),
                                modifier = Modifier.clickable {
                                    onApply(true, r.fullName, r.defaultBranch)
                                    onDismiss()
                                },
                                headlineContent = { Text(r.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(if (r.isPrivate) "privat" else "öffentlich", r.description).joinToString(" · "),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = { if (r.fullName == selectedRepo) Icon(Icons.Outlined.Check, contentDescription = "Ausgewählt") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
