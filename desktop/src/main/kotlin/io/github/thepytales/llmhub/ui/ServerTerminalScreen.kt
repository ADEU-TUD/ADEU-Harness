package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.ssh.KnownHosts
import io.github.thepytales.llmhub.ssh.Risk
import io.github.thepytales.llmhub.ssh.ServerAssistant
import io.github.thepytales.llmhub.ssh.ServerFlows
import io.github.thepytales.llmhub.ssh.ServerStep
import io.github.thepytales.llmhub.ssh.ServerTerminal
import io.github.thepytales.llmhub.ssh.SshAuth
import io.github.thepytales.llmhub.ssh.SshProfile
import io.github.thepytales.llmhub.ssh.SshTty
import io.github.thepytales.llmhub.ssh.StepState
import kotlinx.coroutines.launch
import java.io.File

/** One Server-Terminal per app run: the connection stays when the user leaves the screen (AP-118). */
object ServerTerminals {
    @Volatile private var instance: ServerTerminal? = null

    fun of(c: AppContainer): ServerTerminal = instance ?: synchronized(this) {
        instance ?: ServerTerminal(File(Paths.data, "ssh"), ServerAssistant(c)).also { instance = it }
    }
}

/**
 * Server-Terminal (AP-118): saved sessions like in PuTTY, the terminal (JediTerm) and Lora's column with steps that
 * run only after a click each. [terminalView] draws the terminal (tests replace it, the real one is a Swing component).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerTerminalScreen(
    c: AppContainer,
    onBack: () -> Unit,
    term: ServerTerminal = ServerTerminals.of(c),
    terminalView: @Composable (Modifier) -> Unit = { m -> JediTerminal(term, m) },
) {
    val state by term.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(state.profile?.takeIf { state.connected }?.let { tr("Server-Terminal · {0}", it.label) } ?: tr("Server-Terminal")) },
                actions = {
                    if (state.connected) TextButton(onClick = { term.disconnect() }) { Text(tr("Trennen")) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            state.busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!state.connected) SessionsPane(term, Modifier.fillMaxSize())
            else Row(Modifier.fillMaxSize()) {
                terminalView(Modifier.weight(1.5f).fillMaxHeight().padding(8.dp))
                LoraServerPane(c, term, Modifier.weight(1f).widthIn(min = 320.dp).fillMaxHeight())
            }
        }
    }
    state.hostKey?.let { p ->
        HostKeyDialog(p.result, p.host, p.port, onTrust = { replace -> scope.launch { term.acceptHostKey(replace) } }, onCancel = { term.rejectHostKey() })
    }
}

/** Saved sessions on the left, the form of the chosen one on the right (stacked when narrow). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionsPane(term: ServerTerminal, modifier: Modifier) {
    val state by term.state.collectAsStateWithLifecycle()
    var list by remember { mutableStateOf(term.sessions.all()) }
    var edit by remember { mutableStateOf(state.profile ?: list.firstOrNull() ?: SshProfile()) }
    val scope = rememberCoroutineScope()
    Row(modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Gespeicherte Sitzungen"), style = MaterialTheme.typography.titleSmall)
            list.forEach { p ->
                val chosen = p.id == edit.id
                Surface(
                    onClick = { edit = p; term.clearError() },
                    shape = MaterialTheme.shapes.small,
                    color = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(p.label, style = MaterialTheme.typography.bodyMedium)
                            Text("${p.user}@${p.host}:${p.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            OutlinedButton(onClick = { edit = SshProfile() }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text(tr("Neue Sitzung")) }
            Spacer(Modifier.size(8.dp))
            Text(
                tr("Wie PuTTY: Host, Port, eigener Benutzer, Schlüssel oder Passwort. Passwörter bleiben auf diesem Gerät und gehen nie an ein Modell."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f).widthIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SessionForm(edit, onChange = { edit = it })
            var secret by remember(edit.id) { mutableStateOf("") }
            var keep by remember(edit.id) { mutableStateOf(false) }
            val saved = remember(edit.id, list) { term.sessions.secrets.get(edit.id) != null }
            OutlinedTextField(
                secret, { secret = it },
                label = { Text(if (edit.auth == SshAuth.PASSWORD) tr("Passwort") else tr("Passphrase des Schlüssels (falls gesetzt)")) },
                placeholder = { if (saved) Text(tr("gespeichert – leer lassen")) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(keep, { keep = it })
                Text(tr("Verschlüsselt auf diesem Gerät speichern"), style = MaterialTheme.typography.bodySmall)
                if (saved) TextButton(onClick = { term.sessions.secrets.forget(edit.id); list = term.sessions.all() }) { Text(tr("Gespeichertes vergessen")) }
            }
            state.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val problems = edit.problems()
                Button(enabled = problems.isEmpty() && state.busy == null, onClick = {
                    term.sessions.save(edit); list = term.sessions.all()
                    scope.launch { term.connect(edit, secret, keep) }
                }) { Text(tr("Verbinden")) }
                OutlinedButton(enabled = problems.isEmpty(), onClick = { term.sessions.save(edit); list = term.sessions.all() }) { Text(tr("Speichern")) }
                if (list.any { it.id == edit.id }) TextButton(onClick = { term.sessions.delete(edit.id); list = term.sessions.all(); edit = list.firstOrNull() ?: SshProfile() }) { Text(tr("Löschen")) }
            }
            edit.problems().takeIf { it.isNotEmpty() && edit.host.isNotBlank() }?.let { Text(it.joinToString(" · ") { p -> tr(p) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionForm(p: SshProfile, onChange: (SshProfile) -> Unit) {
    Text(if (p.host.isBlank()) tr("Neue Sitzung") else p.label, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(p.name, { onChange(p.copy(name = it)) }, label = { Text(tr("Name (frei)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(p.host, { onChange(p.copy(host = it.trim())) }, label = { Text(tr("Host oder IP-Adresse")) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(p.port.toString(), { v -> v.filter { it.isDigit() }.take(5).toIntOrNull()?.let { onChange(p.copy(port = it)) } }, label = { Text(tr("Port")) }, singleLine = true, modifier = Modifier.width(96.dp))
    }
    OutlinedTextField(p.user, { onChange(p.copy(user = it.trim())) }, label = { Text(tr("Benutzer")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(p.auth == SshAuth.KEY, { onChange(p.copy(auth = SshAuth.KEY)) }, label = { Text(tr("Schlüssel")) })
        FilterChip(p.auth == SshAuth.PASSWORD, { onChange(p.copy(auth = SshAuth.PASSWORD)) }, label = { Text(tr("Passwort")) })
    }
    if (p.auth == SshAuth.KEY) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(p.keyFile, { onChange(p.copy(keyFile = it)) }, label = { Text(tr("Schlüsseldatei (OpenSSH oder PuTTY .ppk)")) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { pickKeyFile()?.let { onChange(p.copy(keyFile = it)) } }) { Text(tr("Durchsuchen")) }
    }
    OutlinedTextField(p.workDir, { onChange(p.copy(workDir = it.trim())) }, label = { Text(tr("Ordner der Anwendung (z. B. /var/www/app)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(p.webUser, { onChange(p.copy(webUser = it.trim())) }, label = { Text(tr("Benutzer des Webservers")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

private fun pickKeyFile(): String? = runCatching {
    val d = java.awt.FileDialog(null as java.awt.Frame?, tr("Schlüsseldatei wählen"), java.awt.FileDialog.LOAD)
    File(System.getProperty("user.home"), ".ssh").takeIf { it.isDirectory }?.let { d.directory = it.absolutePath }
    d.isVisible = true
    d.file?.let { File(d.directory, it).absolutePath }
}.getOrNull()

/** Lora's column: where the proposals come from, the wish, the flows and the steps with their buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoraServerPane(c: AppContainer, term: ServerTerminal, modifier: Modifier, where: ServerAssistant.Where = remember { ServerAssistant(c).where() }) {
    val state by term.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var wish by remember { mutableStateOf("") }
    var showLog by remember { mutableStateOf(false) }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Lora"), style = MaterialTheme.typography.titleSmall)
            // Datenschutz: always visible where the proposals come from and what leaves the device.
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp).padding(top = 2.dp), tint = if (where.onDevice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(6.dp))
                Text(where.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                wish, { wish = it }, label = { Text(tr("Was soll passieren?")) },
                placeholder = { Text(tr("z. B. Deploy wie immer, Zertifikat läuft ab")) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(enabled = wish.isNotBlank() && where.model != null && state.busy == null, onClick = { scope.launch { term.propose(wish) } }) { Text(tr("Vorschlagen")) }
                ServerFlows.ALL.forEach { f -> AssistChip(onClick = { term.load(f) }, label = { Text(tr(f.title)) }) }
            }
            state.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.steps.isNotEmpty()) {
                HorizontalDivider()
                state.flowTitle?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                if (state.note.isNotBlank()) Text(state.note, style = MaterialTheme.typography.bodySmall)
                Text(tr("Jeder Schritt läuft erst nach deinem Klick – einzeln, der Reihe nach."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val next = state.steps.indexOfFirst { it.state == StepState.OPEN || it.state == StepState.FAILED }
                state.steps.forEachIndexed { i, s ->
                    StepCard(i, s, isNext = i == next, term)
                    if (s.state == StepState.FAILED && where.model != null) TextButton(enabled = state.busy == null, onClick = {
                        scope.launch { term.propose(tr("Der Schritt „{0}“ ist fehlgeschlagen (Exit {1}). Was ist die Ursache, und was ist der nächste Schritt?", s.command, s.exitCode ?: "?"), s.output) }
                    }) { Text(tr("Lora fragen")) }
                }
                TextButton(onClick = { term.cancel() }) { Text(tr("Abbrechen")) }
            }
            state.sent?.let { sent ->
                var open by remember(sent) { mutableStateOf(false) }
                TextButton(onClick = { open = !open }) { Text(if (open) tr("Gesendetes ausblenden") else tr("Was an den Server ging")) }
                if (open) SelectionContainer { Text(sent, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) tr("Protokoll ausblenden") else tr("Protokoll")) }
                TextButton(onClick = { clipboard.setText(AnnotatedString(term.readLog())) }) { Text(tr("Protokoll kopieren")) }
            }
            if (showLog) Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                SelectionContainer { Text(term.readLog().ifBlank { tr("Noch keine Einträge.") }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun StepCard(index: Int, s: ServerStep, isNext: Boolean, term: ServerTerminal) {
    val scope = rememberCoroutineScope()
    var editing by remember(s.command) { mutableStateOf(false) }
    var text by remember(s.command) { mutableStateOf(s.command) }
    val riskColor = when (s.risk) {
        Risk.READ -> MaterialTheme.colorScheme.onSurfaceVariant
        Risk.CHANGE -> MaterialTheme.colorScheme.tertiary
        Risk.RISKY -> MaterialTheme.colorScheme.error
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}.", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                if (s.risk == Risk.RISKY) Icon(Icons.Outlined.Warning, null, Modifier.size(16.dp), tint = riskColor)
                Text(tr(s.risk.label), color = riskColor, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    when (s.state) {
                        StepState.OPEN -> ""
                        StepState.RUNNING -> tr("läuft …")
                        StepState.DONE -> tr("fertig") + (s.exitCode?.let { " · Exit $it" } ?: "")
                        StepState.FAILED -> tr("Fehler") + (s.exitCode?.let { " · Exit $it" } ?: "")
                        StepState.SKIPPED -> tr("übersprungen")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s.state == StepState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editing) OutlinedTextField(text, { text = it }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
            else Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
                SelectionContainer { Text(s.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp)) }
            }
            if (s.what.isNotBlank()) Text(s.what, style = MaterialTheme.typography.bodySmall)
            if (s.safety.isNotBlank()) Text(tr("Sicherheit: {0}", s.safety), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.output.isNotBlank()) Box(Modifier.fillMaxWidth().heightIn(max = 160.dp).background(MaterialTheme.colorScheme.surfaceVariant).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(s.output.takeLast(4000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp)) }
            }
            if (s.state == StepState.OPEN || s.state == StepState.FAILED) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    Button(onClick = { term.edit(index, text); editing = false }) { Text(tr("Übernehmen")) }
                    TextButton(onClick = { editing = false; text = s.command }) { Text(tr("Zurück")) }
                } else {
                    Button(
                        enabled = isNext,
                        onClick = { scope.launch { term.run(index) } },
                        colors = if (s.risk == Risk.RISKY) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                    ) { Text(if (s.state == StepState.FAILED) tr("Nochmal") else tr("Ausführen")) }
                    TextButton(enabled = isNext, onClick = { TerminalInput.send(s.command); term.sentToTerminal(index) }) { Text(tr("Im Terminal")) }
                    TextButton(onClick = { editing = true }) { Text(tr("Bearbeiten")) }
                    TextButton(onClick = { term.skip(index) }) { Text(tr("Überspringen")) }
                }
            }
        }
    }
}

/** Types a command into the open terminal (the user presses Enter there – e.g. when sudo asks for a password). */
internal object TerminalInput {
    @Volatile var tty: SshTty? = null
    fun send(command: String) { runCatching { tty?.write(command) } }
}

/** The real terminal: JediTerm with the SSH shell of the connection; resized with the window. */
@Composable
fun JediTerminal(term: ServerTerminal, modifier: Modifier) {
    val conn = term.connection ?: return
    val opened = remember(conn) {
        runCatching {
            val w = com.jediterm.terminal.ui.JediTermWidget(100, 30, com.jediterm.terminal.ui.settings.DefaultSettingsProvider())
            val tty = SshTty(conn.shell(100, 30), conn.profile.label)
            TerminalInput.tty = tty
            w.setTtyConnector(tty)
            w.start()
            w
        }
    }
    val widget = opened.getOrNull()
    if (widget == null) {
        Text(tr("Das Terminal ließ sich nicht öffnen: {0}", opened.exceptionOrNull()?.message ?: "?"), modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        return
    }
    DisposableEffect(widget) { onDispose { TerminalInput.tty = null; runCatching { widget.close() } } }
    SwingPanel(factory = { widget }, modifier = modifier)
}

/** New key: show the fingerprint and ask. Changed key: warn and require an explicit replace after comparing. */
@Composable
private fun HostKeyDialog(result: KnownHosts.Result, host: String, port: Int, onTrust: (replace: Boolean) -> Unit, onCancel: () -> Unit) {
    val changed = result as? KnownHosts.Result.Changed
    var compared by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(if (changed != null) Icons.Outlined.Warning else Icons.Outlined.Lock, null) },
        // The circle behind the symbol in the same colour (AP-123): red when the server's key changed.
        iconContentColor = if (changed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        title = { Text(if (changed != null) tr("Achtung: Der Server-Schlüssel hat sich geändert") else tr("Diesem Server vertrauen?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (port == 22) host else "$host:$port", style = MaterialTheme.typography.titleSmall)
                if (changed != null) {
                    Text(tr("Lora kennt für diesen Server einen anderen Schlüssel. Das passiert nach einer Neuinstallation – oder wenn jemand die Verbindung abfängt. Verbinde dich nur, wenn die Administration den neuen Schlüssel bestätigt hat."), color = MaterialTheme.colorScheme.error)
                    Text(tr("Bisher: {0}", changed.previous.joinToString(", ")), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(tr("Jetzt: {0}", result.fingerprint), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(compared, { compared = it })
                        Text(tr("Ich habe den neuen Fingerabdruck verglichen."), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(tr("Erste Verbindung zu diesem Server. Vergleiche den Fingerabdruck mit dem, den die Administration nennt (ssh-keygen -lf auf dem Server):"))
                    Text(result.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    (result as? KnownHosts.Result.New)?.let { Text(tr("Typ: {0}", it.keyType), style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        confirmButton = {
            if (changed != null) OutlinedButton(enabled = compared, onClick = { onTrust(true) }) { Text(tr("Schlüssel ersetzen"), color = if (compared) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified) }
            else Button(onClick = { onTrust(false) }) { Text(tr("Vertrauen und verbinden")) }
        },
        dismissButton = { if (changed != null) Button(onClick = onCancel) { Text(tr("Abbrechen")) } else TextButton(onClick = onCancel) { Text(tr("Abbrechen")) } },
    )
}
