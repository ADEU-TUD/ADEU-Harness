package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.update.UpdateInfo
import io.github.thepytales.llmhub.update.UpdatePlatform
import io.github.thepytales.llmhub.update.Updates
import io.github.thepytales.llmhub.update.Updates.State
import kotlinx.coroutines.launch

private val windows get() = UpdatePlatform.KEY == "windows"

/**
 * Asks at the start (and when a check finds a new version later) whether to install the new version, then shows the
 * download and installs it – on Windows the app restarts by itself, on Android the system installer opens.
 */
@Composable
fun UpdatePrompt(c: AppContainer) {
    val context = LocalContext.current.applicationContext
    /** Version and notes, shown once after an update. */
    var whatsNew by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        Updates.start(context) { v, notes ->
            if (notes == null) Notices.info("Lora wurde auf Version $v aktualisiert.") else whatsNew = v to notes
        }
    }
    whatsNew?.let { (v, notes) ->
        AlertDialog(
            onDismissRequest = { whatsNew = null },
            icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
            title = { Text("Was ist neu in $v") },
            text = { Text(notes, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { whatsNew = null }) { Text("OK") } },
        )
    }
    val state by Updates.state.collectAsState()
    val postponed by Updates.postponed.collectAsState()
    val runs by c.runs.runs.collectAsState()
    val background by Updates.background.collectAsState()
    val uri = LocalUriHandler.current

    fun start() = Updates.download(context) {
        // Right away, unless the user went on working meanwhile or an answer is still being written (a restart cuts it off).
        if (!Updates.background.value && (!windows || c.runs.runs.value.isEmpty())) Updates.install(context)
    }

    when (val s = state) {
        is State.Available -> if (postponed != s.info.version) {
            AlertDialog(
                onDismissRequest = Updates::postpone,
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text("Neue Version verfügbar") },
                text = { UpdateText(s.info) },
                confirmButton = { Button(onClick = ::start) { Text("Jetzt aktualisieren") } },
                dismissButton = { TextButton(onClick = Updates::postpone) { Text("Später") } },
            )
        }
        is State.Downloading -> if (!background) AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
            title = { Text("Lora ${s.info.version} wird geladen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    val size = s.info.files[UpdatePlatform.KEY]?.size ?: 0
                    Text(
                        "${s.step} ${(s.progress * 100).toInt()} %" + if (size > 0) " von ${size shr 20} MB" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (windows) "Danach startet Lora kurz neu. Zum Weiterarbeiten „Im Hintergrund“ wählen – die App fragt dann vor dem Neustart."
                        else "Danach fragt Android, ob die App aktualisiert werden soll – einfach „Aktualisieren“ tippen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = Updates::toBackground) { Text("Im Hintergrund") } },
            dismissButton = { TextButton(onClick = Updates::cancelDownload) { Text("Abbrechen") } },
        )
        is State.Ready -> if (postponed != s.info.version) {
            val busy = windows && runs.isNotEmpty()
            AlertDialog(
                onDismissRequest = Updates::postpone,
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text("Update ist bereit") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (windows) "Lora ${s.info.version} ist heruntergeladen. Zum Installieren startet die App kurz neu – Chats und Einstellungen bleiben erhalten."
                            else "Lora ${s.info.version} ist heruntergeladen. Android fragt gleich, ob die App aktualisiert werden soll – einfach „Aktualisieren“ tippen.",
                        )
                        if (busy) Text("Gerade wird noch eine Antwort geschrieben – sie wird beim Neustart abgebrochen.", color = MaterialTheme.colorScheme.error)
                        s.note?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    }
                },
                confirmButton = {
                    Button(onClick = { Updates.install(context) }) { Text(if (windows) "Jetzt neu starten" else "Installieren") }
                },
                dismissButton = { TextButton(onClick = Updates::postpone) { Text("Später") } },
            )
        }
        is State.Failed -> if (s.info != null && postponed != s.info.version) {
            AlertDialog(
                onDismissRequest = Updates::postpone,
                title = { Text("Update hat nicht geklappt") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.message)
                        Text(
                            "Die bisherige Version funktioniert weiter. Beim nächsten Start fragt die App erneut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { Button(onClick = ::start) { Text("Erneut versuchen") } },
                dismissButton = {
                    Column {
                        TextButton(onClick = { runCatching { uri.openUri(Updates.RELEASES_PAGE) } }) { Text("Download-Seite öffnen") }
                        TextButton(onClick = Updates::postpone) { Text("Schließen") }
                    }
                },
            )
        }
        else -> {}
    }
}

@Composable
private fun UpdateText(info: UpdateInfo) {
    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lora ${info.version} ist da (installiert: ${Updates.installed}).")
        if (info.notes.isNotBlank()) {
            Text("Neu", style = MaterialTheme.typography.titleSmall)
            Text(info.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (windows) "Das Update dauert etwa eine Minute. Danach startet Lora von selbst neu – Chats, Einstellungen und API-Key bleiben erhalten."
            else "Danach fragt Android einmal nach („Aktualisieren“). Chats, Einstellungen und API-Key bleiben erhalten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Einstellungen › Updates: installed version, check now, automatic check and the update source. */
@Composable
fun UpdateSection(c: AppContainer) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val state by Updates.state.collectAsState()
    var auto by remember { mutableStateOf(Updates.autoCheck(context)) }
    var source by remember { mutableStateOf(Updates.ownSource(context)) }
    Section("Version") {
        Text("Installiert: Lora ${Updates.installed}", style = MaterialTheme.typography.titleSmall)
        if (!UpdatePlatform.supported) {
            SettingsHint(
                "Automatische Updates gibt es in der installierten Windows-Version („ADEU LLM.exe“) und in der Android-App. " +
                    "Neue Versionen stehen unter ${Updates.RELEASES_PAGE}.",
            )
        } else {
            Text(
                when (val s = state) {
                    State.Idle -> "Noch nicht gesucht."
                    State.Checking -> "Suche nach Updates …"
                    State.UpToDate -> "Du hast die neueste Version."
                    is State.Available -> "Version ${s.info.version} ist verfügbar."
                    is State.Downloading -> "Version ${s.info.version} wird geladen (${(s.progress * 100).toInt()} %)."
                    is State.Ready -> "Version ${s.info.version} ist bereit zum Installieren."
                    is State.Failed -> s.message
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(
                enabled = state !is State.Checking && state !is State.Downloading,
                onClick = { scope.launch { Updates.check(context, manual = true) } },
            ) { Text("Jetzt nach Updates suchen") }
        }
        TextButton(onClick = { runCatching { uri.openUri(Updates.RELEASES_PAGE) } }) { Text("Alle Versionen ansehen") }
    }
    if (UpdatePlatform.supported) {
        Section("Automatisch") {
            ToggleRow(
                "Beim Start nach Updates suchen",
                "Gibt es eine neue Version, fragt die App beim Start, ob sie installiert werden soll. Kein GitHub-Konto nötig.",
                auto,
            ) {
                auto = it
                Updates.setAutoCheck(context, it)
            }
        }
        Section("Update-Quelle") {
            SettingsHint(
                "Leer lassen für den Standard: die öffentlichen Releases unter github.com/${Updates.REPOSITORY}. " +
                    "Für Dienst-PCs, die GitHub nicht erreichen: einen Ordner eintragen (z. B. auf dem Netzlaufwerk " +
                    "„\\\\server\\freigabe\\ADEU-Update“), in dem die Dateien eines Releases liegen – version.json, die Windows-ZIP und die APK.",
            )
            OutlinedTextField(
                value = source,
                onValueChange = {
                    source = it
                    Updates.setSource(context, it)
                },
                label = { Text("Ordner oder Adresse (optional)") },
                placeholder = { UpdatePlatform.presetSource()?.let { Text(it) } },
                supportingText = { UpdatePlatform.presetSource()?.let { Text("Vorgegeben durch update-quelle.txt im App-Ordner") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
