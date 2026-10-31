package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.desktop.Autostart
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import kotlinx.coroutines.launch

/** Windows-only settings page; true if [section] was one of them. */
@Composable
fun PlatformSettingsSection(c: AppContainer, section: String?): Boolean {
    if (section != "windows") return false
    WindowsSection(c)
    return true
}

/** Entry for the Windows page in the settings overview. */
fun platformSettingsItem(): SettingsItem? = SettingsItem("settings/windows", Icons.Outlined.Computer, "Windows", "Infobereich, Autostart, Datenordner (auch in Google Drive)")

const val KEY_STORAGE_HINT = "Er wird verschlüsselt auf diesem PC gespeichert (nur dein Windows-Konto kann ihn lesen)."
const val DEVICE_VOICE = "Windows-Stimme"
const val DEVICE_VOICE_TITLE = "Windows-Stimme"
const val DEVICE_VOICE_DESCRIPTION = "Sprachausgabe von Windows – nichts verlässt den PC."
const val NO_DEVICE_VOICES = "Keine Windows-Stimmen gefunden. Unter Windows-Einstellungen › Zeit und Sprache › Sprache lässt sich eine deutsche Stimme hinzufügen."

/** Windows-specific behavior: notification area, autostart and where the data lives. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WindowsSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var autostart by remember { mutableStateOf(Autostart.enabled) }
    Section("Im Hintergrund") {
        SwitchRow(
            "Beim Schließen im Infobereich weiterlaufen",
            "Antworten werden fertig geschrieben und geplante Aufgaben laufen weiter, auch wenn das Fenster zu ist. " +
                "Öffnen und Beenden über das Symbol unten rechts in der Taskleiste.",
            s.closeToTray,
        ) { v -> c.settings.update { it.copy(closeToTray = v) } }
        SwitchRow(
            "Mit Windows starten",
            if (Autostart.supported) "Startet Lora nach der Anmeldung unsichtbar im Infobereich – für geplante Aufgaben. Keine Admin-Rechte nötig."
            else "Nur in der Windows-Version verfügbar.",
            autostart,
        ) { v ->
            if (Autostart.supported) {
                Autostart.set(v)
                autostart = Autostart.enabled
            }
        }
    }
    var target by remember { mutableStateOf<java.io.File?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Section("Datenordner") {
        Text(
            "Chats, Einstellungen, Dokumente und Transkripte liegen in diesem Ordner. Wie bei Obsidian kannst du einen eigenen " +
                "wählen – etwa in Google Drive, OneDrive oder Nextcloud, dann synchronisiert deren Programm alles auf deine " +
                "anderen Rechner. Große Dateien (Sprach- und Erkennungsmodelle) bleiben auf diesem PC. API-Key, Tokens und " +
                "Umgebungen sind mit Windows verschlüsselt und gelten nur auf diesem PC – auf einem anderen Rechner trägst du " +
                "den API-Key einmal neu ein. Öffne die App nicht auf zwei Rechnern gleichzeitig mit demselben Ordner.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(Paths.data.absolutePath + if (Paths.chosen != null) " (selbst gewählt)" else " (Standard)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { DesktopActions.open(Paths.data) }) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Datenordner öffnen")
            }
            OutlinedButton(onClick = {
                scope.launch { FolderAccess.pick("Neuen Datenordner wählen (z. B. in Google Drive)")?.let { target = java.io.File(it) } }
            }) { Text("Speicherort ändern …") }
            if (Paths.chosen != null) androidx.compose.material3.TextButton(onClick = {
                Paths.relocate(null, copy = false)
                Paths.restart()
            }) { Text("Zurück zum Standardordner") }
        }
        Text(
            "Nur Dokumente in einen Sync-Ordner legen? Dafür gibt es unter Einstellungen › Speicherorte eigene Ablageorte.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    target?.let { dir ->
        val hasData = java.io.File(dir, "settings.json").exists() || java.io.File(dir, "livedocs").isDirectory || java.io.File(dir, "chats").isDirectory
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Datenordner wechseln") },
            text = {
                Text(
                    if (hasData) "In „${dir.absolutePath}“ liegen schon Daten (z. B. von einem anderen Rechner). Die App nutzt sie nach dem Neustart; " +
                        "„Meine Daten dazukopieren“ ergänzt nur Dateien, die dort noch fehlen."
                    else "Deine bisherigen Daten werden nach „${dir.absolutePath}“ kopiert, danach startet die App neu und arbeitet dort weiter. " +
                        "Der alte Ordner bleibt als Sicherung erhalten.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    runCatching { Paths.relocate(dir, copy = true) }
                        .onSuccess { Paths.restart() }
                        .onFailure { Notices.error("Kopieren fehlgeschlagen: ${it.message}") }
                }) { Text(if (hasData) "Meine Daten dazukopieren" else "Kopieren und neu starten") }
            },
            dismissButton = {
                Row {
                    if (hasData) androidx.compose.material3.TextButton(onClick = { Paths.relocate(dir, copy = false); Paths.restart() }) { Text("Nur wechseln") }
                    androidx.compose.material3.TextButton(onClick = { target = null }) { Text("Abbrechen") }
                }
            },
        )
    }
}
