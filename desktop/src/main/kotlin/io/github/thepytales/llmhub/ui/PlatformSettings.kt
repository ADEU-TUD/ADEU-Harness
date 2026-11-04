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
    when (section) {
        "windows" -> WindowsSection(c)
        "sync" -> SyncSettings(c)
        else -> return false
    }
    return true
}

/** Entry for Einstellungen › Synchronisation (AP-12) in the overview's „Daten“. */
fun syncSettingsItem(): SettingsItem? = syncSettingsItemDesktop()

/** Entry for the Windows page in the settings overview. */
fun platformSettingsItem(): SettingsItem? = SettingsItem("settings/windows", Icons.Outlined.Computer, io.github.thepytales.llmhub.desktop.Os.label, tr("Infobereich, Autostart, Datenordner"))

val KEY_STORAGE_HINT: String get() = tr("Er wird verschlüsselt auf diesem PC gespeichert (nur dein Windows-Konto kann ihn lesen).")
val DEVICE_VOICE: String get() = tr("Windows-Stimme")
val DEVICE_VOICE_TITLE: String get() = tr("Windows-Stimme")
val DEVICE_VOICE_DESCRIPTION: String get() = tr("Sprachausgabe von Windows – nichts verlässt den PC.")
val NO_DEVICE_VOICES: String get() = tr("Keine Windows-Stimmen gefunden. Unter Windows-Einstellungen › Zeit und Sprache › Sprache lässt sich eine deutsche Stimme hinzufügen.")

/** Windows-specific behavior: notification area, autostart and where the data lives. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WindowsSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var autostart by remember { mutableStateOf(Autostart.enabled) }
    Section(tr("Im Hintergrund")) {
        SwitchRow(
            tr("Beim Schließen im Infobereich weiterlaufen"),
            tr("Antworten werden fertig geschrieben und geplante Aufgaben laufen weiter, auch wenn das Fenster zu ist. " +
                "Öffnen und Beenden über das Symbol unten rechts in der Taskleiste."),
            s.closeToTray,
            spot = "desktop.tray",
        ) { v -> c.settings.update { it.copy(closeToTray = v) } }
        SwitchRow(
            tr("Mit Windows starten"),
            if (Autostart.supported) tr("Startet Lora nach der Anmeldung unsichtbar im Infobereich – für geplante Aufgaben. Keine Admin-Rechte nötig.")
            else tr("Nur in der Windows-Version verfügbar."),
            autostart,
            spot = "desktop.autostart",
        ) { v ->
            if (Autostart.supported) {
                Autostart.set(v)
                autostart = Autostart.enabled
            }
        }
    }
    Section(tr("Browser neben dem Chat")) {
        SwitchRow(
            tr("Links neben dem Chat öffnen"),
            tr("Links aus Chat, Literatur und Dokumenten öffnen sich als Tabs rechts neben der App – mit Fragen zur Seite, Zusammenfassung " +
                "und Übernahme in die Literatur. Beim ersten Mal richtet Lora dafür einmalig Chromium ein (ca. 170 MB). Aus: im normalen Browser."),
            s.browserPanel,
            spot = "desktop.browser",
        ) { v -> c.settings.update { it.copy(browserPanel = v) } }
    }
    Section(tr("Lora-Ordner")) { LoraFolderSettings() }
}
