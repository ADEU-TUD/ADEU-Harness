package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** A new chat of the vault: a model of the Uni server, memory off, saved encrypted (AP-10). */
fun newVaultChat(c: AppContainer): Conversation {
    val base = newConversation(c)
    val model = base.model.takeIf { Privacy.allows(c, PrivacyLevel.TU, it) }
        ?: c.settings.current.defaultModel.takeIf { Privacy.allows(c, PrivacyLevel.TU, it) } ?: ModelCatalog.DEFAULT_CHAT
    return base.copy(title = tr("Tresor-Chat"), model = model, vault = true, memoryOff = true, autoModel = false, githubEnabled = false)
}

/**
 * Tresor für sensible Forschung (AP-10): set up with a PIN (and the recovery key shown once), unlock, reset the PIN
 * with the recovery key; unlocked it lists the vault chats and starts new ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(c: AppContainer, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val state by c.vault.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Tresor")) },
                actions = {
                    if (state == VaultStore.State.UNLOCKED) {
                        IconButton(onClick = { c.vault.lock() }) { Icon(Icons.Outlined.Lock, contentDescription = tr("Tresor sperren")) }
                    }
                },
            )
        },
        floatingActionButton = { LoraHelperButton(c) },
    ) { pad ->
        ReadableColumn(pad) {
            when (state) {
                VaultStore.State.NOT_SET_UP -> VaultSetup(c)
                VaultStore.State.LOCKED -> VaultUnlock(c)
                VaultStore.State.UNLOCKED -> VaultContents(c, onOpenChat)
            }
            // Room for the sleeping Lora at the bottom right.
            androidx.compose.foundation.layout.Spacer(Modifier.size(LORA_BUTTON_ROOM - 16.dp))
        }
    }
}

@Composable
private fun VaultFacts() {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Was der Tresor schützt"), style = MaterialTheme.typography.titleSmall)
            listOf(
                tr("Chats im Tresor sind verschlüsselt gespeichert (AES-256) – ohne PIN auch als Datei nicht lesbar."),
                tr("Sie erscheinen nicht in der Chatliste, der Suche, dem Gedächtnis oder in Vorschlägen."),
                tr("Nur der Uni-Server oder Modelle auf diesem Gerät; Websuche und GitHub sind gesperrt."),
                tr("Namen und persönliche Angaben werden streng ersetzt – im Zweifel lieber einmal zu viel."),
                tr("Der Tresor sperrt sich nach einigen Minuten ohne Aktivität."),
            ).forEach { Text("• " + tr(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(12)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VaultSetup(c: AppContainer) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Text(tr("Ein geschützter Bereich für sensible Forschung, z. B. Interviews oder Daten von Studierenden."), style = MaterialTheme.typography.bodyLarge)
    VaultFacts()
    Section(tr("Tresor einrichten")) {
        PinField(pin, { pin = it }, tr("PIN (4–12 Ziffern)"))
        PinField(again, { again = it }, tr("PIN wiederholen"))
        if (again.isNotEmpty() && again != pin) HintText(tr("Die PINs stimmen nicht überein."))
        Button(
            enabled = VaultStore.validPin(pin) && pin == again && !busy,
            onClick = {
                busy = true
                scope.launch {
                    recovery = withContext(Dispatchers.Default) { runCatching { c.vault.setUp(pin) }.getOrNull() }
                    busy = false
                    if (recovery == null) Notices.error(tr("Der Tresor ließ sich nicht einrichten."))
                }
            },
        ) { Text(tr(if (busy) "Wird eingerichtet …" else "Tresor einrichten")) }
    }
    recovery?.let { key -> RecoveryKeyDialog(key) { recovery = null } }
}

/** The recovery key, shown once: copy or keep it on paper; „Weiter“ only after confirming. */
@Composable
private fun RecoveryKeyDialog(key: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var kept by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Wiederherstellungsschlüssel")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Mit diesem Schlüssel setzt du die PIN neu, wenn du sie vergisst. Er wird nur jetzt angezeigt – drucke ihn aus oder schreibe ihn auf und bewahre ihn sicher auf, getrennt vom Gerät."))
                AppCard {
                    Text(key, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { kept = !kept }) {
                    Checkbox(kept, { kept = it })
                    Text(tr("Ich habe den Schlüssel sicher aufbewahrt."))
                }
            }
        },
        confirmButton = { Button(enabled = kept, onClick = onDone) { Text(tr("Weiter")) } },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString("Lora – Wiederherstellungsschlüssel des Tresors: $key"))
                Notices.info(tr("Kopiert – zum Ausdrucken einfügen und danach aus der Zwischenablage löschen."))
            }) { Text(tr("Kopieren")) }
        },
    )
}

@Composable
private fun VaultUnlock(c: AppContainer) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(tr("Der Tresor ist gesperrt."), style = MaterialTheme.typography.titleMedium)
    }
    if (!c.vault.deviceKeyWorks()) HintText(tr("Der Geräteschlüssel passt nicht mehr (z. B. nach einer Neuinstallation). Öffne den Tresor mit dem Wiederherstellungsschlüssel."))
    PinField(pin, { pin = it; message = null }, tr("PIN"))
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = pin.length >= 4 && !busy,
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { c.vault.unlock(pin) }
                    busy = false
                    pin = ""
                    message = when (result) {
                        VaultStore.Unlock.Ok -> null
                        is VaultStore.Unlock.Wrong -> tr("Falsche PIN.") + if (result.waitSeconds > 0) " " + tr("Nächster Versuch in") + " ${result.waitSeconds} s." else ""
                        is VaultStore.Unlock.Blocked -> tr("Zu viele Versuche – bitte") + " ${result.waitSeconds} s " + tr("warten.")
                    }
                }
            },
        ) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(tr(if (busy) "Wird geöffnet …" else "Entsperren"))
        }
        TextButton(onClick = { forgot = true }) { Text(tr("PIN vergessen?")) }
    }
    if (forgot) ResetPinDialog(c) { forgot = false }
}

@Composable
private fun ResetPinDialog(c: AppContainer, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("PIN mit Wiederherstellungsschlüssel neu setzen")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(key, { key = it; error = false }, label = { Text(tr("Wiederherstellungsschlüssel")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PinField(pin, { pin = it }, tr("Neue PIN (4–12 Ziffern)"))
                if (error) Text(tr("Der Schlüssel passt nicht."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = key.isNotBlank() && VaultStore.validPin(pin) && !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { c.vault.resetPin(key, pin) }
                    busy = false
                    if (ok) {
                        Notices.info(tr("Neue PIN gesetzt – der Tresor ist offen."))
                        onDismiss()
                    } else error = true
                }
            }) { Text(tr("PIN setzen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

@Composable
private fun VaultContents(c: AppContainer, onOpenChat: (String) -> Unit) {
    val chats by c.vault.chats.collectAsStateWithLifecycle()
    var changePin by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val conv = newVaultChat(c)
            c.chats.save(conv)
            onOpenChat(conv.id)
        }) { Text(tr("Neuer Tresor-Chat")) }
        OutlinedButton(onClick = { c.vault.lock() }) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(tr("Sperren"))
        }
    }
    if (chats.isEmpty()) HintText(tr("Noch keine Chats im Tresor. Hänge Interviews oder Daten erst hier an – sie bleiben verschlüsselt."))
    val date = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    chats.forEach { conv ->
        AppCard(onClick = { c.vault.touch(); onOpenChat(conv.id) }) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HintText(date.format(Date(conv.updatedAt)) + " · " + ModelCatalog.info(conv.model).displayName)
                }
                TextButton(onClick = {
                    c.chats.delete(conv.id)
                    Notices.info(tr("Tresor-Chat gelöscht."))
                }) { Text(tr("Löschen")) }
            }
        }
    }
    Section(tr("Einstellungen des Tresors")) {
        var minutes by remember { mutableStateOf(c.vault.lockMinutes) }
        DropdownField(
            label = tr("Sperren nach Inaktivität"),
            value = "$minutes " + tr("Minuten"),
            options = listOf(1, 2, 5, 10, 15, 30, 60).map { "$it " + tr("Minuten") },
            onSelect = { v -> v.substringBefore(' ').toIntOrNull()?.let { m -> minutes = m; c.vault.setLockMinutes(m) } },
        )
        OutlinedButton(onClick = { changePin = true }) { Text(tr("PIN ändern")) }
        HintText(tr("Synchronisieren je Tresor-Chat (nur dieses Gerät oder verschlüsselt) kommt mit dem einfachen Sync zwischen PCs."))
    }
    VaultFacts()
    if (changePin) ChangePinDialog(c) { changePin = false }
}

@Composable
private fun ChangePinDialog(c: AppContainer, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("PIN ändern")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it }, tr("Neue PIN (4–12 Ziffern)"))
                PinField(again, { again = it }, tr("PIN wiederholen"))
            }
        },
        confirmButton = {
            Button(enabled = VaultStore.validPin(pin) && pin == again, onClick = {
                if (c.vault.changePin(pin)) Notices.info(tr("PIN geändert.")) else Notices.error(tr("Der Tresor ist gesperrt."))
                onDismiss()
            }) { Text(tr("Ändern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** Shown instead of a vault chat while the vault is locked: nothing of the chat is visible. */
@Composable
fun VaultLockedNotice(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(tr("Dieser Chat liegt im Tresor, der gerade gesperrt ist."), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onUnlock) { Text(tr("Tresor öffnen")) }
        }
    }
}
