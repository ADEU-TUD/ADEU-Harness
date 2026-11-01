package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.SyncGuard
import io.github.thepytales.llmhub.desktop.CloudFolders
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
import io.github.thepytales.llmhub.desktop.SyncSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Einfacher Sync on Windows (AP-12): the check before the data is loaded, the note when another PC opens the folder,
 * and Einstellungen › Synchronisation.
 */

/**
 * Before the data is loaded: if the folder is open on another PC or that PC's changes have not all arrived, a small
 * window says so and waits – it goes on by itself once the other PC is closed and everything is there. Returns false
 * if the user chose „Beenden“.
 */
fun runSyncGate(): Boolean {
    val first = SyncSession.check() ?: return true
    var go = false
    application(exitProcessOnExit = false) {
        val state = rememberWindowState(size = DpSize(560.dp, 380.dp), position = WindowPosition(androidx.compose.ui.Alignment.Center))
        var finding by remember { mutableStateOf<SyncGuard.Finding?>(first) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(4000)
                finding = withContext(Dispatchers.IO) { SyncSession.check() }
                if (finding == null) {
                    go = true
                    exitApplication()
                }
            }
        }
        Window(onCloseRequest = ::exitApplication, state = state, title = "Lora", icon = androidx.compose.ui.res.painterResource("icon.png")) {
            LlmHubTheme(AppSettings()) {
                finding?.let { f ->
                    SyncGateContent(
                        f,
                        onOpen = { go = true; exitApplication() },
                        onQuit = ::exitApplication,
                    )
                }
            }
        }
    }
    return go
}

private val TIME = SimpleDateFormat("dd.MM. 'um' HH:mm", Locale.GERMANY)

private fun ago(ms: Long): String {
    val min = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> tr("gerade eben")
        min < 60 -> tr("vor {0} Min.", min)
        else -> "am " + TIME.format(Date(ms))
    }
}

/** „ein Chat“, „die Einstellungen“ … for a path in the data folder. */
internal fun describeSyncPath(path: String): String = when {
    path.startsWith("conversations/") -> tr("ein Chat")
    path == "einstellungen.json" -> tr("die Einstellungen")
    path.startsWith("documents/") || path.startsWith("livedocs/") -> tr("ein Dokument")
    else -> "„" + path.substringAfterLast('/') + "“"
}

@Composable
private fun SyncGateContent(f: SyncGuard.Finding, onOpen: () -> Unit, onQuit: () -> Unit) {
    val s = f.session
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            LoraFace(Modifier.size(56.dp), label = "Lora", director = rememberLoraDirector(listOf(LoraAction.THINK, LoraAction.LOOK), LoraAction.THINK))
            Spacer(Modifier.size(12.dp))
            Text(
                when (f) {
                    is SyncGuard.Finding.OpenElsewhere -> tr("Lora ist auf „{0}“ geöffnet", s.pc)
                    is SyncGuard.Finding.StillSyncing -> tr("Die Änderungen von „{0}“ sind noch unterwegs", s.pc)
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        when (f) {
            is SyncGuard.Finding.OpenElsewhere -> {
                Text(
                    if (s.background) tr("Dort läuft Lora nur im Hintergrund (Infobereich) und hat nichts zu tun. Sie beendet sich von selbst, sobald du hier öffnest.")
                    else tr("Seit {0}, zuletzt aktiv {1}. Damit keine Änderungen verloren gehen, sollte Lora nur auf ", TIME.format(Date(s.openedAt)), ago(s.aliveAt)) +
                        tr("einem PC offen sein. Schließe sie dort (Symbol im Infobereich › Beenden) – dann geht es hier von selbst weiter."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is SyncGuard.Finding.StillSyncing -> {
                val n = f.missing.size
                Text(
                    tr("{0} noch, z. B. {1} (zuletzt geändert {2}). ", if (n == 1) tr("Eine Datei fehlt") else tr("{0} Dateien fehlen", n), describeSyncPath(f.missing.first()), ago(s.lastSeen)) +
                        tr("Dein Cloud-Dienst lädt sie gerade herunter – es geht von selbst weiter, sobald alles da ist."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
        HintText(tr("Lora prüft alle paar Sekunden neu. „Trotzdem öffnen“ kann Änderungen des anderen PCs überschreiben."))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
            TextButton(onClick = onQuit) { Text(tr("Beenden")) }
            if (f is SyncGuard.Finding.OpenElsewhere && s.background) Button(onClick = onOpen) { Text(tr("Hier öffnen")) }
            else OutlinedButton(onClick = onOpen) { Text(tr("Trotzdem öffnen")) }
        }
    }
}

/**
 * While the app runs, another PC opened the same folder: in the background (window closed, nothing running) Lora
 * ends here by herself ([onQuit] with a note); with the window open she asks.
 */
@Composable
fun SyncNewcomerDialog(visible: Boolean, idle: Boolean, onQuit: (String?) -> Unit) {
    val other by SyncSession.newcomer.collectAsState()
    var dismissed by remember { mutableStateOf<String?>(null) }
    val o = other ?: return
    if (dismissed == o.id + o.openedAt) return
    if (!visible && idle) {
        LaunchedEffect(o.id, o.openedAt) { onQuit(tr("Lora wurde auf „{0}“ geöffnet – hier beendet, damit nichts überschrieben wird.", o.pc)) }
        return
    }
    if (!visible) return
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
        title = { Text(tr("Lora ist jetzt auch auf „{0}“ geöffnet", o.pc)) },
        text = {
            Text(
                tr("Beide PCs nutzen denselben Datenordner. Damit keiner die Änderungen des anderen überschreibt, sollte Lora nur auf einem " +
                    "PC offen sein. Am besten hier beenden, wenn du dort weiterarbeitest."),
            )
        },
        confirmButton = { Button(onClick = { onQuit(null) }) { Text(tr("Hier beenden")) } },
        dismissButton = { TextButton(onClick = { dismissed = o.id + o.openedAt }) { Text(tr("Hier weiterarbeiten")) } },
    )
}

/** Entry of Einstellungen › Synchronisation in the overview. */
fun syncSettingsItemDesktop(): SettingsItem = SettingsItem(
    "settings/sync", Icons.Outlined.CloudSync, tr("Synchronisation"),
    if (SyncSession.active) tr("An · {0}", CloudFolders.serviceOf(Paths.root) ?: Paths.root.name) else tr("Lora-Ordner in OneDrive, Google Drive oder Nextcloud legen"),
)

/**
 * Einstellungen › Synchronisation (AP-12): put the data folder into a cloud folder (one click for the ones found), open
 * an existing one on the second PC, see the PCs that use it and the conflict copies set aside, end it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncSettings(c: AppContainer) {
    val scope = rememberCoroutineScope()
    val places by produceState(emptyList<CloudFolders.Place>()) { value = withContext(Dispatchers.IO) { CloudFolders.found() } }
    var target by remember { mutableStateOf<File?>(null) }
    val active = SyncSession.active
    val kept by SyncSession.kept.collectAsState()
    Section(tr("So funktioniert es")) {
        Text(
            tr("Wie bei Obsidian: Der Lora-Ordner (Dateien, Chats und Einstellungen) liegt in einem Cloud-Ordner (Nextcloud, Google Drive für Desktop oder OneDrive), der auf " +
                "deinen PCs eingerichtet ist. Deren Programm hält ihn überall gleich – am Büro-PC beginnen, am Laptop weiterarbeiten, ohne Umzug."),
            style = MaterialTheme.typography.bodyMedium,
        )
        HintText(
            tr("Synchronisiert werden Chats, Projekte, Dokumente, Literatur, TU Vorlagen, Gedächtnis und Einstellungen. API-Schlüssel, Tokens und " +
                "Umgebungen nie: Sie bleiben verschlüsselt auf jedem PC – auf dem zweiten PC trägst du den API-Schlüssel einmal ein. Große Dateien " +
                "(Sprachmodelle, lokale Modelle, Aufnahmen) bleiben ebenfalls auf dem PC."),
        )
        HintText(
            tr("Lora ist immer nur auf einem PC offen: Beim Start prüft sie, ob sie anderswo läuft oder dessen Änderungen noch unterwegs sind, und " +
                "wartet dann kurz. Legt der Cloud-Dienst trotzdem Konfliktkopien an, behält Lora beide Fassungen."),
        )
    }
    if (active) {
        Section(tr("Synchronisation ist an")) {
            Text(Paths.root.absolutePath + (CloudFolders.serviceOf(Paths.root)?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodySmall)
            val notes = remember { SyncGuard.sessions(Paths.data).sortedByDescending { it.lastSeen } }
            notes.forEach { n ->
                val me = n.id == SyncSession.me.id
                Text(
                    "• ${n.pc}${if (me) " (dieser PC)" else ""} – " + when {
                        me -> tr("geöffnet seit {0}", TIME.format(Date(n.openedAt)))
                        n.isOpen(System.currentTimeMillis()) -> tr("gerade geöffnet") + if (n.background) tr(" (im Hintergrund)") else ""
                        else -> "zuletzt ${ago(n.lastSeen)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { DesktopActions.open(Paths.root) }) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(tr("Lora-Ordner öffnen"))
                }
                val conflictDir = File(File(Paths.data, SyncGuard.FOLDER), SyncGuard.CONFLICTS)
                if (conflictDir.isDirectory) OutlinedButton(onClick = { DesktopActions.open(conflictDir) }) { Text(tr("Beiseitegelegte Konfliktkopien")) }
                TextButton(onClick = { target = Paths.defaultRoot; endSync = true }) { Text(tr("Synchronisation beenden …")) }
            }
            kept?.let { k ->
                HintText(
                    buildString {
                        if (k.chats.isNotEmpty()) append("${k.chats.size} Chat(s) gab es in zwei Fassungen – beide sind da („… (zweite Fassung)“). ")
                        if (k.setAside.isNotEmpty()) append("${k.setAside.size} Konfliktkopie(n) liegen unter sync/konflikte.")
                    },
                )
            }
        }
    } else {
        Section(tr("Lora-Ordner in einen Cloud-Ordner legen")) {
            Text(tr("Erster PC: Dein Lora-Ordner wird in den Cloud-Ordner kopiert, danach startet Lora neu und arbeitet dort."), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                places.forEach { p -> Button(onClick = { target = p.target }) { Text(tr("In {0} legen", p.name)) } }
                OutlinedButton(onClick = { scope.launch { FolderAccess.pick(tr("Ordner in deinem Cloud-Ordner wählen"))?.let { target = File(it) } } }) {
                    Text(if (places.isEmpty()) tr("Cloud-Ordner wählen …") else tr("Anderen Ordner wählen …"))
                }
            }
            if (places.isEmpty()) HintText(tr("Kein Cloud-Ordner gefunden. Richte zuerst Nextcloud, Google Drive für Desktop oder OneDrive ein."))
        }
        Section(tr("Zweiter PC: vorhandenen Ordner öffnen")) {
            Text(
                tr("Auf dem zweiten PC wählst du denselben Ordner (z. B. „Lora“ in deinem Cloud-Ordner). Lora nutzt die Daten dort – alles ist da."),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { scope.launch { FolderAccess.pick(tr("Vorhandenen Lora-Ordner wählen"))?.let { target = File(it) } } }) {
                Text(tr("Vorhandenen Ordner öffnen …"))
            }
        }
    }
    target?.let { dir -> SyncSwitchDialog(dir, ending = endSync, onDismiss = { target = null; endSync = false }) }
}

/** „Synchronisation beenden“ was chosen (the dialog copies the data back into the standard folder). */
private var endSync by mutableStateOf(false)

@Composable
private fun SyncSwitchDialog(dir: File, ending: Boolean, onDismiss: () -> Unit) {
    val hasData = !ending && Paths.hasData(dir)
    fun run(action: () -> Unit) {
        runCatching(action).onSuccess { Paths.restart() }.onFailure { Notices.error(tr("Kopieren fehlgeschlagen: {0}", it.message)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ending) tr("Synchronisation beenden") else if (hasData) tr("Vorhandenen Ordner nutzen") else tr("Lora-Ordner verlegen")) },
        text = {
            Text(
                when {
                    ending -> tr("Dein Lora-Ordner wird zurück nach „{0}“ kopiert (neuere Dateien ersetzen ältere), dann ", Paths.defaultRoot.absolutePath) +
                        tr("startet Lora neu. Der Cloud-Ordner bleibt unverändert – auf anderen PCs geht die Synchronisation weiter.")
                    hasData -> tr("In „{0}“ liegen schon Lora-Daten (z. B. vom anderen PC). Lora startet neu und nutzt sie. ", dir.absolutePath) +
                        tr("„Meine Daten dazukopieren“ ergänzt nur Dateien, die dort noch fehlen.")
                    else -> tr("Dein Lora-Ordner wird nach „{0}“ kopiert, danach startet Lora neu und arbeitet dort weiter. ", dir.absolutePath) +
                        tr("Der bisherige Ordner bleibt als Sicherung erhalten.")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    ending -> run { Paths.relocate(null, copy = true, overwriteOlder = true, sync = false) }
                    hasData -> run { Paths.relocate(dir, copy = false, sync = true) }
                    else -> run { dir.mkdirs(); Paths.relocate(dir, copy = true, sync = true) }
                }
            }) { Text(if (ending) tr("Zurückkopieren und neu starten") else if (hasData) tr("Nutzen und neu starten") else tr("Kopieren und neu starten")) }
        },
        dismissButton = {
            Row {
                if (hasData) TextButton(onClick = { run { Paths.relocate(dir, copy = true, sync = true) } }) { Text(tr("Meine Daten dazukopieren")) }
                TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) }
            }
        },
    )
}
