package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import io.github.thepytales.llmhub.data.ChatRepository
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    /** Asking for the password before the export (with secrets). */
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf<String?>(null) }
    /** Keys of an imported backup, waiting for its password. */
    var locked by remember { mutableStateOf<SecretBox?>(null) }
    var imported by remember { mutableStateOf<String?>(null) }
    val export = rememberFileExport("application/zip", onDone = { ok ->
        message = if (ok) "Sicherung gespeichert." else "Die Sicherung konnte nicht gespeichert werden."
        password = null
    }) { out -> Backup.export(c, out, password) }
    val import = rememberFileImport(arrayOf("application/zip", "application/octet-stream")) { input ->
        try {
            val r = Backup.import(c, input)
            val text = "Importiert: ${r.chats} Chats, ${r.files} Dateien (aus Version ${r.from}). Die App wird jetzt beendet – bitte neu öffnen, damit alles geladen wird."
            if (r.locked != null) {
                imported = text
                locked = r.locked
            } else {
                restart = true
                message = text
            }
        } catch (e: Exception) {
            message = "Import fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    val fileName = "Lora-Sicherung-${LocalDate.now()}.zip"
    SettingsCard("Sicherung") {
        SettingsHint(
            "Speichert Chats, Projekte, Vorlagen, geplante Aufgaben, Dokumente, Logos, Wissenssammlung, Gedächtnis, Adressbuch " +
                "und Einstellungen in einer ZIP-Datei – als Kopie oder für den Umzug auf ein anderes Gerät (auch Android ↔ Windows)."
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { secrets = !secrets }) {
            Checkbox(checked = secrets, onCheckedChange = { secrets = it })
            Text("API-Key, GitHub-Token und geheime Umgebungsvariablen mitnehmen")
        }
        if (secrets) SettingsHint("Diese werden mit einem Passwort verschlüsselt, das Sie gleich festlegen. Beim Import wird es abgefragt.")
        OutlinedButton(onClick = { if (secrets) askPassword = true else { password = null; export(fileName) } }) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Sicherung erstellen")
        }
    }
    val importChat = rememberFileImport(arrayOf("application/json", "text/*", "*/*")) { input ->
        message = runCatching {
            val chats = ChatJson.read(input.readBytes().toString(Charsets.UTF_8))
            chats.forEach { c.chats.save(it) }
            "${chats.size} Chat(s) importiert – sie stehen in der Seitenleiste."
        }.getOrElse { "Chat-Import fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}" }
    }
    SettingsCard("Einzelne Chats") {
        SettingsHint("Ein Chat lässt sich über ⋮ › „Als JSON exportieren“ vollständig speichern (mit allen Versionen) und hier wieder einlesen – auch auf einem anderen Gerät.")
        OutlinedButton(onClick = importChat) {
            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Chat importieren (JSON)")
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
    if (askPassword) NewPasswordDialog(
        onConfirm = { pw -> askPassword = false; password = pw; export(fileName) },
        onDismiss = { askPassword = false },
    )
    locked?.let { box ->
        UnlockDialog(
            onUnlock = { pw -> Backup.unlock(c, box, pw).also { ok -> if (ok) { locked = null; restart = true; message = imported } } },
            onSkip = {
                locked = null
                restart = true
                message = (imported ?: "Importiert.") + "\n\nAPI-Key und Tokens wurden nicht übernommen."
            },
        )
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

/** Password for the keys in a backup: twice, at least [SecretBox.MIN_PASSWORD] characters. */
@Composable
private fun NewPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val long = first.length >= SecretBox.MIN_PASSWORD
    val same = first == second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passwort für die Schlüssel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API-Key und Tokens werden damit verschlüsselt. Ohne dieses Passwort lassen sie sich nicht wiederherstellen.")
                PasswordField(first, { first = it }, "Passwort")
                PasswordField(second, { second = it }, "Wiederholen")
                if (first.isNotEmpty() && !long) HintText("Mindestens ${SecretBox.MIN_PASSWORD} Zeichen.")
                else if (second.isNotEmpty() && !same) HintText("Die Passwörter stimmen nicht überein.")
            }
        },
        confirmButton = { TextButton(enabled = long && same, onClick = { onConfirm(first) }) { Text("Sicherung erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Asks for the password of an imported backup; [onUnlock] returns false for a wrong one. */
@Composable
private fun UnlockDialog(onUnlock: (String) -> Boolean, onSkip: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Passwort der Sicherung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Die Sicherung enthält verschlüsselte Schlüssel (API-Key, Tokens). Mit dem Passwort werden sie übernommen.")
                PasswordField(pw, { pw = it; wrong = false }, "Passwort")
                if (wrong) Text("Falsches Passwort.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { wrong = !onUnlock(pw) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Ohne Schlüssel") } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Einstellungen › Daten › Papierkorb: deleted chats and transcripts, restorable for 30 days. */
@Composable
fun TrashSection(c: AppContainer) {
    val scope = rememberCoroutineScope()
    var chats by remember { mutableStateOf<List<Pair<Conversation, Long>>?>(null) }
    var transcripts by remember { mutableStateOf<List<Triple<String, String, Long>>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) {
        chats = withContext(Dispatchers.IO) { c.chats.trashed() }
        transcripts = runCatching { c.transcription.trashed() }.getOrDefault(emptyList())
    }
    val format = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    fun day(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(format)
    SettingsHint("Gelöschte Chats und Transkripte bleiben ${ChatRepository.TRASH_DAYS} Tage hier und lassen sich wiederherstellen. Danach werden sie endgültig gelöscht.")
    SettingsCard("Chats") {
        val list = chats
        when {
            list == null -> CircularProgressIndicator(Modifier.size(24.dp))
            list.isEmpty() -> SettingsHint("Keine gelöschten Chats.")
            else -> list.forEach { (conv, time) ->
                TrashRow(conv.title, "Gelöscht am ${day(time)}",
                    onRestore = { c.chats.restore(conv.id); chats = list.filterNot { it.first.id == conv.id } },
                    onDelete = { scope.launch { withContext(Dispatchers.IO) { c.chats.deleteForever(conv.id) }; version++ } })
            }
        }
    }
    SettingsCard("Transkripte") {
        val list = transcripts
        when {
            list == null -> CircularProgressIndicator(Modifier.size(24.dp))
            list.isEmpty() -> SettingsHint("Keine gelöschten Transkripte.")
            else -> list.forEach { (id, title, time) ->
                TrashRow(title, "Gelöscht am ${day(time)}",
                    onRestore = { scope.launch { c.transcription.restore(id); version++ } },
                    onDelete = { scope.launch { c.transcription.deleteForever(id); version++ } })
            }
        }
    }
}

@Composable
private fun TrashRow(title: String, subtitle: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HintText(subtitle)
        }
        TextButton(onClick = onRestore) { Text("Wiederherstellen") }
        IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.DeleteForever, contentDescription = "Endgültig löschen") }
    }
    if (confirm) ConfirmDialog("Endgültig löschen?", "„$title“ lässt sich danach nicht mehr wiederherstellen.", "Löschen", onConfirm = onDelete, onDismiss = { confirm = false })
}
