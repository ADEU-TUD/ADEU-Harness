package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.LocalFolder
import kotlinx.coroutines.launch
import java.io.File

/** Einstellungen › Lokale Ordner: folders on this PC the agent may read and change. */
@Composable
internal fun LocalFoldersSection(c: AppContainer) {
    val folders by c.localFolders.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Section(tr("Ordner für den Assistenten")) {
        SettingsHint(
            tr("Der Assistent kann in diesen Ordnern Dateien auflisten, lesen (auch Word, PowerPoint, Excel und PDF), durchsuchen, " +
                "neue Dateien und Dokumente anlegen und vorhandene ändern – in jedem Chat. Außerhalb dieser Ordner erreicht er nichts. " +
                "Gelöschte Dateien landen im Unterordner „.adeu-papierkorb“. Vor Änderungen fragt er je nach " +
                "Einstellung unter Chat & Werkzeuge › Freigaben nach."),
        )
        if (!FolderAccess.SUPPORTED) {
            SettingsHint(tr("Nur in der Windows-Version verfügbar: Android gibt Apps keinen direkten Zugriff auf Ordner."))
            return@Section
        }
        folders.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleSmall)
                    Text(f.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!File(f.path).isDirectory) Text(tr("Ordner nicht erreichbar"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(f.write, { v -> c.localFolders.upsert(f.copy(write = v)) })
                    Text(if (f.write) tr("Ändern") else tr("Nur lesen"), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { FolderAccess.open(f.path) }) { Icon(Icons.Outlined.FolderOpen, contentDescription = tr("Im Explorer öffnen")) }
                IconButton(onClick = {
                    c.localFolders.delete(f.id)
                    Notices.undo(tr("Freigabe entfernt (der Ordner selbst bleibt)"), restore = { c.localFolders.upsert(f) })
                }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Freigabe entfernen")) }
            }
        }
        OutlinedButton(onClick = {
            scope.launch {
                val path = FolderAccess.pick(tr("Ordner für den Assistenten freigeben")) ?: return@launch
                if (folders.any { File(it.path).absoluteFile == File(path).absoluteFile }) return@launch Notices.info(tr("Dieser Ordner ist schon freigegeben."))
                val base = File(path).name.ifBlank { path }
                var name = base
                var n = 2
                while (folders.any { it.name.equals(name, true) }) name = "$base ${n++}"
                c.localFolders.upsert(LocalFolder(name = name, path = path))
            }
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(tr("Ordner freigeben …"))
        }
        if (folders.isNotEmpty()) SettingsHint(tr("Im Chat z. B.: „Fasse alle Protokolle im Ordner {0} zusammen und speichere das Ergebnis dort als Word-Datei.“", folders.first().name))
    }
}

/**
 * Einstellungen › Speicherorte: folders where documents of the editor live as readable files – like Obsidian vaults.
 * Inside Google Drive, OneDrive or Nextcloud they are synced to other computers without anything else to set up.
 */
@Composable
internal fun StorageSection(c: AppContainer) {
    val vaults by c.docs.vaults.collectAsStateWithLifecycle()
    val docs by c.docs.items.collectAsStateWithLifecycle()
    val defaultVault by c.docs.defaultVaultId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Section(tr("Ablageorte für Dokumente")) {
        SettingsHint(
            tr("Wie Tresore in Obsidian: Jeder Ablageort ist ein Ordner, in dem Dokumente, Präsentationen und Tabellen als lesbare " +
                "Markdown-Dateien liegen (Versionen und Kommentare im Unterordner „.adeu“). Liegt der Ordner in Google Drive, ") +
                tr("OneDrive oder Nextcloud, synchronisiert deren Programm alles automatisch auf andere Rechner. Änderungen von dort " +
                "und neue .md-Dateien erscheinen nach wenigen Sekunden in der App."),
        )
        if (!FolderAccess.SUPPORTED) {
            SettingsHint(tr("Eigene Ablageorte gibt es in der Windows-Version. Auf Android liegen Dokumente in der App."))
            return@Section
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(defaultVault == null || vaults.none { it.id == defaultVault }, { c.docs.setDefaultVault(null) })
            Column(Modifier.weight(1f)) {
                Text(tr("In der App"))
                Text(tr("{0} Dokument(e) · nur auf diesem Gerät (mit Sicherung & Umzug übertragbar)", docs.count { it.vault == null }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        vaults.forEach { v ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(defaultVault == v.id, { c.docs.setDefaultVault(v.id) })
                Column(Modifier.weight(1f)) {
                    Text(v.name)
                    Text(v.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (File(v.path).isDirectory) tr("{0} Dokument(e)", docs.count { it.vault == v.id }) else tr("Ordner nicht erreichbar"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (File(v.path).isDirectory) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = { FolderAccess.open(v.path) }) { Icon(Icons.Outlined.FolderOpen, contentDescription = tr("Im Explorer öffnen")) }
                IconButton(onClick = {
                    c.docs.removeVault(v.id)
                    Notices.info(tr("Ablageort entfernt – die Dateien bleiben im Ordner und lassen sich wieder einbinden."))
                }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Ablageort entfernen")) }
            }
        }
        SettingsHint(tr("Der markierte Ort gilt für neue Dokumente. Vorhandene Dokumente verschiebst du im Editor über ⋮ › Ablageort."))
        OutlinedButton(onClick = {
            scope.launch {
                val path = FolderAccess.pick(tr("Ordner als Ablageort wählen (z. B. in Google Drive)")) ?: return@launch
                val v = c.docs.addVault(File(path).name, path, makeDefault = vaults.isEmpty())
                Notices.info(tr("Ablageort „{0}“ eingebunden.", v.name))
            }
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(tr("Ordner als Ablageort hinzufügen …"))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsHint(tr("Tipp für Google Drive: „Google Drive für Desktop“ installieren, dort einen Ordner wie „Meine Ablage/ADEU“ anlegen und ihn hier wählen. Auf dem zweiten Rechner denselben Ordner einbinden."))
    }
}
