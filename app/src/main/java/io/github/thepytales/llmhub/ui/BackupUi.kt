package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Backup
import java.time.LocalDate
import kotlin.system.exitProcess

/** Einstellungen › Sicherung & Umzug: everything as ZIP, and back. */
@Composable
fun BackupSection(c: AppContainer) {
    var secrets by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var restart by remember { mutableStateOf(false) }
    val export = rememberFileExport("application/zip", onDone = { ok ->
        message = if (ok) "Sicherung gespeichert." else "Die Sicherung konnte nicht gespeichert werden."
    }) { out -> Backup.export(c, out, secrets) }
    val import = rememberFileImport(arrayOf("application/zip", "application/octet-stream")) { input ->
        message = try {
            val r = Backup.import(c, input)
            restart = true
            "Importiert: ${r.chats} Chats, ${r.files} Dateien (aus Version ${r.from}). Die App wird jetzt beendet – bitte neu öffnen, damit alles geladen wird."
        } catch (e: Exception) {
            "Import fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    SettingsCard("Sicherung") {
        SettingsHint(
            "Speichert Chats, Projekte, Vorlagen, geplante Aufgaben, Dokumente, Logos, Wissenssammlung, Gedächtnis, Adressbuch " +
                "und Einstellungen in einer ZIP-Datei – als Kopie oder für den Umzug auf ein anderes Gerät (auch Android ↔ Windows)."
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { secrets = !secrets }) {
            Checkbox(checked = secrets, onCheckedChange = { secrets = it })
            Text("API-Key, GitHub-Token und geheime Umgebungsvariablen mitnehmen")
        }
        if (secrets) SettingsHint("Achtung: Diese stehen dann unverschlüsselt in der Datei. Nur auf eigenen Geräten verwenden und danach löschen.")
        OutlinedButton(onClick = { export("ADEU-LLM-Sicherung-${LocalDate.now()}.zip") }) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Sicherung erstellen")
        }
    }
    SettingsCard("Importieren") {
        SettingsHint(
            "Spielt eine Sicherung ein. Chats und Dateien mit gleichem Namen werden ersetzt, alles andere bleibt. Danach wird die App " +
                "beendet und muss neu geöffnet werden."
        )
        OutlinedButton(onClick = import) {
            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Sicherung importieren")
        }
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { if (!restart) message = null },
            title = { Text("Sicherung & Umzug") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null; if (restart) exitProcess(0) }) { Text(if (restart) "App beenden" else "OK") }
            },
        )
    }
}
