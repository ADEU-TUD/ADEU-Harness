package io.github.thepytales.llmhub.ui

import io.github.thepytales.llmhub.desktop.Autostart
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
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

/** Windows-only settings page; true if [section] was one of them. */
@Composable
fun PlatformSettingsSection(c: AppContainer, section: String?): Boolean {
    if (section != "windows") return false
    WindowsSection(c)
    return true
}

/** Entry for the Windows page in the settings overview. */
fun platformSettingsItem(): SettingsItem? = SettingsItem("settings/windows", Icons.Outlined.Computer, "Windows", "Infobereich, Autostart, Datenordner")

const val KEY_STORAGE_HINT = "Er wird verschlüsselt auf diesem PC gespeichert (nur dein Windows-Konto kann ihn lesen)."
const val DEVICE_VOICE = "Windows-Stimme"
const val DEVICE_VOICE_TITLE = "Windows-Stimme"
const val DEVICE_VOICE_DESCRIPTION = "Sprachausgabe von Windows – nichts verlässt den PC."
const val NO_DEVICE_VOICES = "Keine Windows-Stimmen gefunden. Unter Windows-Einstellungen › Zeit und Sprache › Sprache lässt sich eine deutsche Stimme hinzufügen."

/** Windows-specific behavior: notification area, autostart and where the data lives. */
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
            if (Autostart.supported) "Startet ADEU LLM nach der Anmeldung unsichtbar im Infobereich – für geplante Aufgaben. Keine Admin-Rechte nötig."
            else "Nur in der Windows-Version verfügbar.",
            autostart,
        ) { v ->
            if (Autostart.supported) {
                Autostart.set(v)
                autostart = Autostart.enabled
            }
        }
    }
    Section("Daten") {
        Text(
            "Chats, Einstellungen und Dokumente liegen in deinem Windows-Profil. Sie bleiben erhalten, wenn du eine neue " +
                "Version startest. API-Key, Tokens und Umgebungen sind mit Windows verschlüsselt (nur dein Konto kann sie lesen).",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(Paths.data.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { DesktopActions.open(Paths.data) }) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Datenordner öffnen")
        }
    }
}
