package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.McpPolicy
import io.github.thepytales.llmhub.data.McpServer
import io.github.thepytales.llmhub.net.McpAuthRequired
import io.github.thepytales.llmhub.net.McpStdio
import kotlinx.coroutines.launch

/** Einstellungen › Verbindungen (AP-22, AP-23): Nextcloud and the MCP servers. */
@Composable
fun ConnectionsSettings(c: AppContainer) {
    NextcloudSection(c)
    McpSection(c)
}

/** Subtitle of „Verbindungen“ in the settings overview. */
internal fun connectionsSummary(c: AppContainer): String {
    val parts = listOfNotNull(
        if (c.settings.current.hasNextcloud) "Nextcloud verbunden" else null,
        c.mcpServers.current.count { it.enabled }.takeIf { it > 0 }?.let { "$it MCP-Server" },
    )
    return parts.joinToString(" · ").ifEmpty { "Nextcloud, MCP-Server (fremde Werkzeuge)" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun McpSection(c: AppContainer) {
    val servers by c.mcpServers.items.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<McpServer?>(null) }
    var adding by remember { mutableStateOf(false) }
    Section(tr("MCP-Server")) {
        SettingsHint(
            tr("Über das Model Context Protocol (MCP) bekommt Lora Werkzeuge anderer Dienste, z. B. ein Wiki, eine Datenbank oder ein Ticketsystem. Fern-Server laufen über das Internet (mit Token oder Anmeldung), lokale Server sind Programme auf diesem PC.") +
                " " + tr("Je Werkzeug legst du fest: Erlauben (ohne Rückfrage), Fragen (über die Freigaben) oder Sperren. Fremde Werkzeuge gibt es nie im Tresor und nur in Projekten, deren Datenschutzstufe der Server einhält.")
        )
        if (servers.isEmpty()) HintText(tr("Noch kein Server verbunden."))
        servers.forEach { s -> McpServerCard(c, s, onEdit = { edit = s }) }
        OutlinedButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Server hinzufügen")) }
    }
    if (adding) McpServerDialog(c, null) { adding = false }
    edit?.let { s -> McpServerDialog(c, s) { edit = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun McpServerCard(c: AppContainer, s: McpServer, onEdit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var authMeta by remember { mutableStateOf<String?>(null) }
    var needsLogin by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun connect() {
        busy = true
        status = null
        scope.launch {
            try {
                val n = c.mcp.refresh(s.id)
                status = tr("Verbunden") + " · $n " + tr("Werkzeuge")
                needsLogin = false
                open = true
            } catch (e: McpAuthRequired) {
                needsLogin = true
                authMeta = e.resourceMetadata
                status = tr("Der Server verlangt eine Anmeldung.")
            } catch (e: Exception) {
                status = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Hub, size = 32.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        (if (s.local) tr("Lokal") + " · " else "") + s.where,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = s.enabled, onCheckedChange = { on -> c.mcpServers.update(s.id) { it.copy(enabled = on) }; if (!on) c.mcp.disconnect(s.id) })
            }
            val line = status ?: s.lastError ?: if (s.tools.isNotEmpty()) "${s.tools.size} " + tr("Werkzeuge") + if (s.internal) " · " + tr("auch in Projekten „Nur TU Dresden“") else "" else tr("Noch nicht verbunden")
            Text(line, style = MaterialTheme.typography.bodySmall, color = if (s.lastError != null && status == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.local && !McpStdio.SUPPORTED) HintText(tr("Lokale Server laufen nur in der Windows-Version."))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else if (needsLogin || (s.oauth == null && s.lastError?.contains("Anmeldung") == true)) Button(onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val n = c.mcp.login(s.id, authMeta) { url -> runCatching { uri.openUri(url) } }
                            status = tr("Angemeldet") + " · $n " + tr("Werkzeuge")
                            needsLogin = false
                            open = true
                        } catch (e: Exception) {
                            status = e.message ?: e.javaClass.simpleName
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(tr("Im Browser anmelden")) }
                else OutlinedButton(onClick = { connect() }) { Text(if (s.tools.isEmpty()) tr("Verbinden") else tr("Werkzeuge neu laden")) }
                if (s.tools.isNotEmpty()) TextButton(onClick = { open = !open }) { Text(if (open) tr("Werkzeuge ausblenden") else tr("Werkzeuge ansehen")) }
                TextButton(onClick = onEdit) { Text(tr("Bearbeiten")) }
                TextButton(onClick = { confirmDelete = true }) { Text(tr("Entfernen")) }
            }
            if (open && s.tools.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Alle:"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                    McpPolicy.entries.forEach { p ->
                        TextButton(onClick = { c.mcpServers.update(s.id) { it.copy(defaultPolicy = p.name, policies = emptyMap()) } }) { Text(tr(p.label)) }
                    }
                }
                s.tools.forEach { t ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.name + if (t.readOnly) " · " + tr("liest nur") else "", style = MaterialTheme.typography.bodyMedium)
                        if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            McpPolicy.entries.forEach { p ->
                                FilterChip(
                                    selected = s.policy(t.name) == p,
                                    onClick = { c.mcpServers.update(s.id) { it.copy(policies = it.policies + (t.name to p.name)) } },
                                    label = { Text(tr(p.label)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        tr("Server entfernen?"), "„${s.name}“ " + tr("und seine Werkzeuge verschwinden aus der App."), tr("Entfernen"),
        onConfirm = { c.mcp.disconnect(s.id); c.mcpServers.delete(s.id) }, onDismiss = { confirmDelete = false },
    )
}

/** Adds a server ([existing] null) or changes one. */
@Composable
private fun McpServerDialog(c: AppContainer, existing: McpServer?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var local by remember { mutableStateOf(existing?.local == true) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var command by remember { mutableStateOf(existing?.command.orEmpty()) }
    var env by remember { mutableStateOf(existing?.env.orEmpty()) }
    var token by remember { mutableStateOf(existing?.token.orEmpty()) }
    var header by remember { mutableStateOf(existing?.tokenHeader.orEmpty()) }
    var clientId by remember { mutableStateOf(existing?.clientId.orEmpty()) }
    var internal by remember { mutableStateOf(existing?.internal == true) }
    val errors = remember { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (existing == null) "MCP-Server hinzufügen" else "MCP-Server bearbeiten")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(tr("Name")) }, modifier = Modifier.fillMaxWidth())
                if (McpStdio.SUPPORTED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !local, onClick = { local = false }, label = { Text(tr("Fern-Server (URL)")) })
                    FilterChip(selected = local, onClick = { local = true }, label = { Text(tr("Lokales Programm")) })
                }
                if (local) {
                    OutlinedTextField(
                        value = command, onValueChange = { command = it }, label = { Text(tr("Befehl")) },
                        supportingText = { Text(tr("z. B.") + " npx -y @modelcontextprotocol/server-filesystem \"C:\\Daten\"") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = env, onValueChange = { env = it }, label = { Text(tr("Umgebungsvariablen (optional)")) },
                        supportingText = { Text(tr("Eine je Zeile: NAME=Wert")) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it; errors.remove("url") }, singleLine = true, label = { Text(tr("Adresse (Streamable HTTP)")) },
                        supportingText = { Text(errors["url"] ?: tr("z. B. https://mcp.example.org/mcp")) }, isError = "url" in errors, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token, onValueChange = { token = it }, singleLine = true, label = { Text(tr("Token (optional)")) },
                        supportingText = { Text(tr("Leer lassen, wenn sich der Server über den Browser anmeldet (OAuth).") + " " + KEY_STORAGE_HINT) },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = header, onValueChange = { header = it }, singleLine = true, label = { Text(tr("Header für den Token (optional)")) },
                        supportingText = { Text(tr("Leer: Authorization: Bearer …; sonst z. B. X-API-Key")) }, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = clientId, onValueChange = { clientId = it }, singleLine = true, label = { Text(tr("Client-ID für OAuth (optional)")) },
                        supportingText = { Text(tr("Nur nötig, wenn der Betreiber sie vorgibt.")) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Läuft an der TU Dresden oder auf diesem PC"))
                        Text(tr("Dann auch in Projekten mit „Nur TU Dresden“ nutzbar."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = internal, onCheckedChange = { internal = it })
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && (if (local) command.isNotBlank() else url.isNotBlank()), onClick = {
                val u = url.trim()
                if (!local && !u.startsWith("https://") && !u.startsWith("http://127.0.0.1") && !u.startsWith("http://localhost")) {
                    errors["url"] = tr("Bitte eine https://-Adresse.")
                    return@Button
                }
                val base = existing ?: McpServer(name = name.trim())
                val next = base.copy(
                    name = name.trim(), transport = if (local) McpServer.STDIO else McpServer.HTTP, url = u, command = command.trim(), env = env,
                    token = token.trim(), tokenHeader = header.trim(), clientId = clientId.trim(), internal = internal,
                    // Another address or program: the old login and tool list no longer apply.
                    oauth = existing?.oauth?.takeIf { existing.url.trim() == u },
                    tools = if (existing != null && existing.where != (if (local) command.trim() else u)) emptyList() else base.tools,
                )
                c.mcp.disconnect(next.id)
                c.mcpServers.upsert(next)
                onDismiss()
            }) { Text(tr("Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
