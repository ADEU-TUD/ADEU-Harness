package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AgentEnvironment
import io.github.thepytales.llmhub.data.EnvVar
import io.github.thepytales.llmhub.data.NetworkAccess

/** Agent environments: variables, notes and network access – like cloud environments of coding agents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentsScreen(c: AppContainer, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val envs by c.environments.items.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(tr("Umgebungen")) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit("new") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(tr("Neue Umgebung")) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    tr("Eine Umgebung gibt dem Agenten Variablen (z.B. API-Keys für Web-APIs), Hinweise und Regeln für den " +
                        "Internetzugriff. Geheime Werte sieht das Modell nie – es nutzt sie nur als \${NAME} in Web-Anfragen. " +
                        "Alles wird verschlüsselt auf dem Gerät gespeichert. Wähle die Umgebung im Chat über „+“ › Umgebung."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (envs.isEmpty()) item {
                Text(tr("Noch keine Umgebungen."), modifier = Modifier.padding(vertical = 16.dp))
            }
            items(envs, key = { it.id }) { e ->
                val isDefault = settings.defaultEnvId == e.id
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onEdit(e.id) },
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall)
                            Text(envSummary(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isDefault) Text(tr("Standard für neue Chats"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { c.settings.update { it.copy(defaultEnvId = if (isDefault) "" else e.id) } }) {
                            Icon(
                                if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isDefault) tr("Nicht mehr Standard") else tr("Als Standard für neue Chats"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Editor like "Neue Umgebung erstellen": name, network access, variables and notes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentEditScreen(c: AppContainer, id: String, onBack: () -> Unit) {
    val existing = remember(id) { c.environments.get(id) }
    var env by remember(id) { mutableStateOf(existing ?: AgentEnvironment(name = "Standard")) }
    var domains by remember(id) { mutableStateOf(env.allowedDomains.joinToString("\n")) }
    var bulk by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    val nameError = env.variables.firstOrNull { it.name.isNotBlank() && !AgentEnvironment.NAME.matches(it.name) }

    fun save() {
        val cleaned = env.copy(
            name = env.name.trim().ifBlank { "Umgebung" },
            allowedDomains = domains.lines().map { it.trim() }.filter { it.isNotEmpty() },
            variables = (env.variables + AgentEnvironment.parseVariables(bulk)).filter { it.name.isNotBlank() }.distinctBy { it.name },
        )
        c.environments.upsert(cleaned)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(if (existing == null) tr("Neue Umgebung") else tr("Umgebung bearbeiten")) },
                actions = {
                    if (existing != null) IconButton(onClick = { showDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
                    TextButton(enabled = nameError == null, onClick = { save() }) { Text(if (existing == null) tr("Erstellen") else tr("Speichern")) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(value = env.name, onValueChange = { env = env.copy(name = it) }, label = { Text(tr("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(tr("Netzwerkzugriff"), style = MaterialTheme.typography.titleSmall)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    NetworkAccess.entries.forEachIndexed { i, n ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { env = env.copy(network = n) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(n.label, style = MaterialTheme.typography.titleSmall)
                                Text(n.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (env.network == n) Icon(Icons.Outlined.CheckCircle, contentDescription = tr("Ausgewählt"), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (env.network == NetworkAccess.CUSTOM) {
                OutlinedTextField(
                    value = domains,
                    onValueChange = { domains = it },
                    label = { Text(tr("Erlaubte Domains (eine pro Zeile)")) },
                    placeholder = { Text(tr("api.example.com\nbing.com\nduckduckgo.com")) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("Subdomains sind eingeschlossen. Für die Websuche duckduckgo.com, für Nachrichten bing.com bzw. news.google.com erlauben."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(tr("Umgebungsvariablen"), style = MaterialTheme.typography.titleSmall)
            env.variables.forEachIndexed { i, v ->
                VariableRow(
                    v = v,
                    onChange = { nv -> env = env.copy(variables = env.variables.toMutableList().also { it[i] = nv }) },
                    onRemove = { env = env.copy(variables = env.variables.filterIndexed { j, _ -> j != i }) },
                )
            }
            nameError?.let {
                Text(
                    tr("„{0}“ ist kein gültiger Name (Buchstaben, Ziffern, _; nicht mit Ziffer beginnen).", it.name),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = { env = env.copy(variables = env.variables + EnvVar("", "", secret = true)) }) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr("Variable hinzufügen"))
            }
            OutlinedTextField(
                value = bulk,
                onValueChange = { bulk = it },
                label = { Text(tr("Oder als .env einfügen")) },
                placeholder = { Text("API_KEY=hunter2") },
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                supportingText = { Text(tr("Zeilen im Format NAME=Wert werden beim Speichern als geheime Variablen übernommen.")) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = env.notes,
                onValueChange = { env = env.copy(notes = it) },
                label = { Text(tr("Hinweise für das Modell (optional)")) },
                placeholder = { Text(tr("z.B. Nutze die API unter https://api.example.com/v2 mit Header X-Key: \${API_KEY}.")) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(32.dp))
        }
    }

    if (showDelete) {
        ConfirmDialog(tr("Umgebung löschen?"), tr("Chats, die sie nutzen, laufen danach ohne Umgebung."), tr("Löschen"), onConfirm = {
            c.environments.delete(id)
            if (c.settings.current.defaultEnvId == id) c.settings.update { it.copy(defaultEnvId = "") }
            onBack()
        }, onDismiss = { showDelete = false })
    }
}

@Composable
private fun VariableRow(v: EnvVar, onChange: (EnvVar) -> Unit, onRemove: () -> Unit) {
    var visible by remember { mutableStateOf(!v.secret) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = v.name,
                    onValueChange = { onChange(v.copy(name = it.trim().replace(' ', '_'))) },
                    label = { Text(tr("Name")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(v.copy(secret = !v.secret)); visible = v.secret }) {
                    Icon(
                        if (v.secret) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (v.secret) tr("Geheim – für das Modell unsichtbar") else tr("Sichtbar für das Modell"),
                    )
                }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = tr("Entfernen")) }
            }
            OutlinedTextField(
                value = v.value,
                onValueChange = { onChange(v.copy(value = it)) },
                label = { Text(if (v.secret) tr("Wert (geheim)") else tr("Wert (für das Modell sichtbar)")) },
                singleLine = true,
                visualTransformation = if (v.secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    if (v.secret) TextButton(onClick = { visible = !visible }) { Text(if (visible) tr("Verbergen") else tr("Zeigen")) }
                },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
        }
    }
}
