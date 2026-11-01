package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.NextcloudService
import io.github.thepytales.llmhub.net.DavEntry
import io.github.thepytales.llmhub.net.NextcloudLogin
import io.github.thepytales.llmhub.net.WebDavClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Einstellungen › Verbindungen › Nextcloud (AP-22): sign in through the browser (Login Flow v2 – works with the TU login and
 * a second factor, the app gets an app password of its own) or type a login name and app password; the folder for results.
 */
@Composable
fun NextcloudSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var server by remember { mutableStateOf(s.nextcloudServer.ifBlank { NextcloudService.TUD_SERVER }) }
    var user by remember { mutableStateOf(s.nextcloudUser) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf<Job?>(null) }
    var manual by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(s.nextcloudFolder) }

    Section(tr("Nextcloud")) {
        SettingsHint(
            tr("Dateien aus der Nextcloud (z. B. dem Cloudstore der TU Dresden) direkt öffnen, an Chats anhängen und Ergebnisse dort speichern – auf dem PC und dem Handy. Lora kann dort auch selbst nachsehen, wenn du sie bittest.")
        )
        if (s.hasNextcloud) {
            StatusLine(true, tr("Verbunden als") + " ${s.nextcloudUser} · ${WebDavClient.serverBase(s.nextcloudServer)}")
            OutlinedTextField(
                value = folder, onValueChange = { folder = it }, singleLine = true,
                label = { Text(tr("Ordner für Ergebnisse")) },
                supportingText = { Text(tr("Hier speichert „In Nextcloud speichern“, wenn du keinen anderen Ordner wählst.")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (folder != s.nextcloudFolder) Button(onClick = { c.settings.update { it.copy(nextcloudFolder = WebDavClient.normalize(folder.ifBlank { "/Lora" })) } }) { Text(tr("Ordner übernehmen")) }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        status = c.nextcloud.client().test().fold({ n -> tr("Verbindung klappt") + " · $n " + tr("Einträge im Hauptordner") }, { it.message })
                        busy = false
                    }
                }) { Text(tr("Verbindung testen")) }
                TextButton(onClick = {
                    c.settings.update { it.copy(nextcloudUser = "", nextcloudPassword = "") }
                    status = tr("Abgemeldet. Das App-Passwort kannst du in Nextcloud unter Einstellungen › Sicherheit widerrufen.")
                }) { Text(tr("Abmelden")) }
            }
        } else {
            OutlinedTextField(
                value = server, onValueChange = { server = it }, singleLine = true,
                label = { Text(tr("Adresse der Nextcloud")) },
                supportingText = { Text(tr("TU Dresden:") + " ${NextcloudService.TUD_SERVER}") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (waiting != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(tr("Im Browser anmelden und „Zugriff gewähren“ wählen – die App wartet …"), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { waiting?.cancel(); waiting = null }) { Text(tr("Abbrechen")) }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    status = null
                    waiting = scope.launch {
                        try {
                            val start = NextcloudLogin.start(c.llm.http, server)
                            runCatching { uri.openUri(start.loginUrl) }
                            var done: NextcloudLogin.Done? = null
                            // The login page stays valid for 20 minutes.
                            repeat(600) { if (done == null) { delay(2000); done = NextcloudLogin.poll(c.llm.http, start) } }
                            val d = done ?: error(tr("Die Anmeldung ist abgelaufen. Bitte noch einmal versuchen."))
                            c.settings.update { it.copy(nextcloudServer = d.server, nextcloudUser = d.loginName, nextcloudPassword = d.appPassword) }
                            status = tr("Angemeldet. Die App hat ein eigenes App-Passwort bekommen.")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            status = e.message ?: e.javaClass.simpleName
                        } finally {
                            waiting = null
                        }
                    }
                }) { Icon(Icons.Outlined.Cloud, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(tr("Im Browser anmelden")) }
                TextButton(onClick = { manual = !manual }) { Text(tr("Mit App-Passwort")) }
            }
            if (manual) {
                OutlinedTextField(value = user, onValueChange = { user = it }, singleLine = true, label = { Text(tr("Anmeldename")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text(tr("App-Passwort")) },
                    supportingText = { Text(tr("In Nextcloud unter Einstellungen › Sicherheit › „Neues App-Passwort erstellen“.") + " " + KEY_STORAGE_HINT) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(enabled = !busy && user.isNotBlank() && password.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        val test = WebDavClient(c.llm.http, server, user.trim(), password.trim()).test()
                        status = test.fold({ tr("Verbunden.") }, { it.message })
                        if (test.isSuccess) c.settings.update { it.copy(nextcloudServer = WebDavClient.serverBase(server), nextcloudUser = user.trim(), nextcloudPassword = password.trim()) }
                        busy = false
                    }
                }) { Text(tr("Verbinden")) }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Sync für Android (AP-28): the phone mirrors the PC's Lora-Ordner in the Nextcloud – chats, projects, memory, Kurztexte,
 * own TU Vorlagen and the visible files. Shown on Android once Nextcloud is connected.
 */
@Composable
fun PhoneSyncSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!IS_ANDROID || !s.hasNextcloud) return
    val scope = rememberCoroutineScope()
    val status by c.phoneSync.status.collectAsStateWithLifecycle()
    val running by c.phoneSync.running.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    Section(tr("Mit dem PC synchronisieren")) {
        SettingsHint(
            tr("Am PC liegt der Lora-Ordner in der Nextcloud (Einstellungen › Synchronisation am PC). Das Handy holt sich darüber Chats, Projekte, Gedächtnis, Kurztexte, eigene TU Vorlagen und die Dateien des Lora-Ordners und lädt eigene Änderungen hoch – beim Öffnen der App, alle fünf Minuten und beim Wechsel in den Hintergrund.") +
                " " + tr("Wie zwischen zwei PCs gilt: immer nur ein Gerät zur Zeit. Ist der Ordner am PC geöffnet, wartet das Handy; Änderungen auf beiden Seiten bleiben beide erhalten.")
        )
        if (s.phoneSyncRoot.isBlank()) {
            Button(onClick = { picking = true }) { Text(tr("Lora-Ordner in der Nextcloud wählen")) }
        } else {
            StatusLine(true, tr("Synchronisiert mit") + " ${s.phoneSyncRoot}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !running, onClick = { scope.launch { hint = c.phoneSync.syncNow() } }) { Text(tr("Jetzt synchronisieren")) }
                if (running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                TextButton(onClick = { c.settings.update { it.copy(phoneSyncRoot = "") }; hint = tr("Sync beendet. Die Daten auf dem Handy bleiben.") }) { Text(tr("Beenden")) }
            }
        }
        (hint ?: status)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HintText(tr("Literatur, Wissenssammlung, Transkripte und Einstellungen bleiben auf dem jeweiligen Gerät; API-Schlüssel gehen nie in den Ordner."))
    }
    if (picking) NextcloudBrowser(
        c, tr("Lora-Ordner des PCs wählen"), start = "/", pickFolder = true, confirm = tr("Diesen Ordner nehmen"),
        onDismiss = { picking = false },
        onFolder = { folder ->
            picking = false
            scope.launch {
                if (!c.phoneSync.looksLikeLoraFolder(folder)) {
                    hint = tr("In {0} liegt kein Lora-Ordner (es fehlt „.lora“).", folder) + " " + tr("Am PC unter Einstellungen › Synchronisation den Ordner in die Nextcloud legen und warten, bis er hochgeladen ist.")
                } else {
                    c.settings.update { it.copy(phoneSyncRoot = folder) }
                    hint = c.phoneSync.syncNow()
                }
            }
        },
    )
}

/**
 * Browses the Nextcloud: folders open with a tap, files can be ticked ([multi]) or picked. With [pickFolder] the dialog
 * chooses a folder („Hier speichern“) instead of files; [confirm] is the label of that button.
 */
@Composable
fun NextcloudBrowser(
    c: AppContainer,
    title: String,
    start: String = "/",
    pickFolder: Boolean = false,
    multi: Boolean = true,
    confirm: String = if (pickFolder) tr("Hier speichern") else tr("Anhängen"),
    onDismiss: () -> Unit,
    onFolder: (String) -> Unit = {},
    onFiles: (List<DavEntry>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(WebDavClient.normalize(start)) }
    var entries by remember { mutableStateOf<List<DavEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picked = remember { mutableStateListOf<DavEntry>() }
    var newFolder by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(path, reload) {
        entries = null
        error = null
        runCatching { c.nextcloud.list(path) }
            // A result folder that does not exist yet: show it empty, it is created on saving.
            .onSuccess { entries = it }
            .onFailure { e -> if (pickFolder && path != "/" && e.message.orEmpty().contains("existiert")) entries = emptyList() else error = e.message }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Breadcrumb: every part leads back up.
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
                    TextButton(onClick = { path = "/" }) { Text(tr("Nextcloud")) }
                    parts.forEachIndexed { i, p ->
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { path = "/" + parts.take(i + 1).joinToString("/") }) { Text(p, maxLines = 1) }
                    }
                    if (pickFolder) IconButton(onClick = { newFolder = "" }) { Icon(Icons.Outlined.CreateNewFolder, tr("Neuer Ordner")) }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)) {
                    val list = entries
                    when {
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        list.isEmpty() -> HintText(tr("Dieser Ordner ist leer."), Modifier.align(Alignment.Center))
                        else -> LazyColumn {
                            items(list.filter { it.folder || !pickFolder }, key = { it.path }) { e ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        if (e.folder) path = e.path
                                        else if (multi) { if (e in picked) picked.remove(e) else picked.add(e) }
                                        else onFiles(listOf(e))
                                    }.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!e.folder && multi) Checkbox(checked = e in picked, onCheckedChange = { on -> if (on) picked.add(e) else picked.remove(e) })
                                    Icon(if (e.folder) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.size(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (!e.folder) Text(NextcloudService.size(e.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pickFolder) Button(onClick = { onFolder(path) }) { Text(tr(confirm)) }
            else if (multi) Button(enabled = picked.isNotEmpty(), onClick = { onFiles(picked.toList()) }) { Text(tr(confirm) + if (picked.size > 1) " (${picked.size})" else "") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
    newFolder?.let { name ->
        AlertDialog(
            onDismissRequest = { newFolder = null },
            title = { Text(tr("Neuer Ordner")) },
            text = { OutlinedTextField(value = name, onValueChange = { newFolder = it }, singleLine = true, label = { Text(tr("Name")) }) },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    val target = WebDavClient.join(path, name.trim())
                    newFolder = null
                    scope.launch {
                        runCatching { c.nextcloud.client().mkdirs(target) }.onSuccess { path = target }.onFailure { Notices.error(it.message ?: tr("Ordner nicht angelegt.")) }
                    }
                }) { Text(tr("Anlegen")) }
            },
            dismissButton = { TextButton(onClick = { newFolder = null }) { Text(tr("Abbrechen")) } },
        )
    }
}

/** „In Nextcloud speichern“: picks a folder (the result folder at first), uploads the file and says where it is. */
@Composable
fun rememberNextcloudSave(c: AppContainer): ((File) -> Unit)? {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    if (!s.hasNextcloud) return null
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    file?.let { f ->
        NextcloudBrowser(
            c, tr("In Nextcloud speichern"), start = c.nextcloud.resultFolder, pickFolder = true,
            onDismiss = { file = null },
            onFolder = { folder ->
                file = null
                scope.launch {
                    runCatching { c.nextcloud.save(f, folder) }
                        .onSuccess { Notices.info(tr("In Nextcloud gespeichert:") + " $it") }
                        .onFailure { Notices.error(it.message ?: tr("Speichern in Nextcloud fehlgeschlagen.")) }
                }
            },
        )
    }
    return { f -> file = f }
}

/** Picks files in the Nextcloud and hands them over as attachments (text read on the device). */
@Composable
fun NextcloudAttachDialog(c: AppContainer, onFile: (FileAttachment) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    NextcloudBrowser(
        c, tr("Aus Nextcloud anhängen"), onDismiss = onDismiss,
        onFiles = { picked ->
            onDismiss()
            scope.launch {
                for (e in picked) {
                    runCatching { c.nextcloud.attach(e.path) }
                        .onSuccess { r ->
                            r.file?.let(onFile)
                            (r.error ?: r.note)?.let { Notices.info(it) }
                        }
                        .onFailure { Notices.error("„${e.name}“: " + (it.message ?: tr("nicht geladen"))) }
                }
            }
        },
    )
}
