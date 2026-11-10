package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.MailAccess
import io.github.thepytales.llmhub.agent.MailHandoff
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.RuntimeStatus

/**
 * Postfach nur mit lokalem Modell (AP-68) on Mail & Kalender: the data note, which model on this device reads the mails
 * and – without one – why it is needed and a model that fits this PC to download. A model loaded already is used.
 */
@Composable
fun MailLocalModelCard(c: AppContainer, onOpenModels: () -> Unit = { LoraHost.go("local-models") }) {
    val state by c.localLlm.state.collectAsStateWithLifecycle()
    val model = remember(state.installed, state.running) { MailAccess.model(c) }
    AppCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Nur auf diesem Gerät"), style = MaterialTheme.typography.titleSmall)
                HintText(tr(MailAccess.DATA_NOTE) + " " + tr("Der Uni-Server und externe Anbieter haben keinen Zugriff auf das Postfach – auch nicht auf Wunsch."))
                if (model != null) {
                    val name = LocalCatalog.get(model)?.name?.let { tr(it) } ?: ModelCatalog.info(model).displayName
                    StatusPill(
                        if (state.running == model) tr("{0} ist geladen und liest die Mails", name) else tr("{0} liest die Mails", name),
                        PillTone.SUCCESS,
                    )
                    HintText(tr("Zusammenfassungen und Entwürfe lassen sich danach mit „An Chat übergeben“ an ein anderes Modell weitergeben – erst nach deiner Prüfung, pseudonymisiert und ohne die Mails selbst."))
                } else {
                    Text(tr("Dafür braucht es ein Modell auf diesem Gerät."), style = MaterialTheme.typography.bodyMedium)
                    HintText(tr(MailAccess.WHY))
                    LocalModelOffer(c, onOpenModels)
                }
            }
        }
    }
}

/**
 * Under the mode line of a Postfach chat: nothing while a model on this device answers (the mode line says where
 * data goes), else why one is needed and one to download. As soon as one is there, the chat takes it ([onModel]).
 */
@Composable
fun MailModelNotice(c: AppContainer, model: String, onModel: (String) -> Unit, onOpenModels: () -> Unit) {
    val state by c.localLlm.state.collectAsStateWithLifecycle()
    val local = remember(state.installed, state.running) { MailAccess.model(c) }
    LaunchedEffect(local, model) { if (local != null && !MailAccess.onDevice(model)) onModel(local) }
    if (MailAccess.onDevice(model)) {
        Row(Modifier.padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                tr("Postfach-Chat: nur Modelle auf diesem Gerät") + " · " + ModelCatalog.info(model).displayName,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(Modifier.padding(start = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Dafür braucht es ein Modell auf diesem Gerät."), style = MaterialTheme.typography.labelLarge)
        HintText(tr(MailAccess.WHY))
        LocalModelOffer(c, onOpenModels)
    }
}

/** A model for the Postfach to download (with tool calls, sized for this PC), its progress, or where to find others. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalModelOffer(c: AppContainer, onOpenModels: () -> Unit) {
    val state by c.localLlm.state.collectAsStateWithLifecycle()
    val hw by c.localLlm.hardware.collectAsStateWithLifecycle()
    val suggestion = remember(hw) { MailAccess.suggestedDownload(hw) }
    val loading = state.downloads.entries.firstOrNull()
    when {
        state.runtime == RuntimeStatus.UNSUPPORTED ->
            HintText(tr("Auf diesem Gerät laufen keine lokalen Modelle – das Postfach bleibt ohne KI; Ansicht, Regeln und Protokoll gehen weiter."))
        state.starting != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            HintText(tr("{0} wird geladen …", state.starting))
        }
        loading != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val name = LocalCatalog.get(loading.key)?.name?.let { tr(it) } ?: loading.key
            HintText(tr("{0} wird heruntergeladen … {1} %", name, (loading.value * 100).toInt()))
            LinearProgressIndicator(progress = { loading.value.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            TextButton(onClick = { c.localLlm.cancelInstall(loading.key) }) { Text(tr("Stopp")) }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (suggestion != null) {
                HintText(
                    if (hw?.mac == true) tr("Passend für diesen Mac: {0} – rechnet mit dem Apple-Chip und nutzt Werkzeuge.", tr(suggestion.name))
                    else if ((hw?.vramGb ?: 0.0) >= 8) tr("Passend für diesen PC mit Grafikkarte: {0} – nutzt Werkzeuge und fasst auch viele Mails zügig zusammen.", tr(suggestion.name))
                    else tr("Passend für diesen PC ohne Grafikkarte: {0} – läuft auf dem Prozessor und nutzt Werkzeuge.", tr(suggestion.name)),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (suggestion != null) {
                    Button(onClick = { c.localLlm.install(suggestion.id) }) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("{0} laden ({1})", tr(suggestion.name), formatBytes(suggestion.size)))
                    }
                }
                TextButton(onClick = onOpenModels) { Text(if (suggestion != null) tr("Andere Modelle …") else tr("Modelle auf diesem Gerät …")) }
            }
        }
    }
    LocalMessage(c, state)
}

/**
 * An Chat übergeben (AP-68): the card to check and change what goes to the chat model – only the text the model on this
 * device wrote, pseudonymized and without mail ids. Exactly the checked text goes out; the new chat has no mail tools.
 * Hosted once by the app (see LoraHelperHost), [go] opens the new chat.
 */
@Composable
fun MailHandoffHost(c: AppContainer, go: (String) -> Unit) {
    val review by MailHandoff.pending.collectAsStateWithLifecycle()
    val r = review ?: return
    var text by remember(r) { mutableStateOf(r.text) }
    val name = ModelCatalog.info(r.model).displayName
    AlertDialog(
        onDismissRequest = { MailHandoff.pending.value = null },
        title = { Text(tr("An Chat übergeben")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HintText(
                    tr("Nur dieser Text geht an {0} – geschrieben vom Modell auf diesem Gerät, Namen pseudonymisiert, ohne Mails, Anhänge und Mail-Kennungen. Der neue Chat kann nicht ins Postfach greifen. Prüfe und ändere ihn vorher.", name),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(tr("Text für den Chat")) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                )
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                MailHandoff.pending.value = null
                ChatHandoff.pending.value = MailHandoff.content(text, r.model)
                go(chatRoute(model = r.model))
            }) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("An {0} übergeben", name))
            }
        },
        dismissButton = { TextButton(onClick = { MailHandoff.pending.value = null }) { Text(tr("Abbrechen")) } },
    )
}
