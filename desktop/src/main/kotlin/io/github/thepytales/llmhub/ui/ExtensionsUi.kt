package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.Extensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Einstellungen › Werkzeuge › Erweiterungen (AP-25): the reloadable extensions with their state, size and buttons to
 * set them up or remove them; the browser engine of AP-14 beside them.
 */
@Composable
fun ExtensionsSettings(c: AppContainer) {
    val states by Extensions.states.collectAsStateWithLifecycle()
    var browser by remember { mutableStateOf(Extensions.browserBytes()) }
    var browserPending by remember { mutableStateOf(Extensions.browserRemovalPending) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Extensions.refresh(); browser = Extensions.browserBytes() } }
    val scope = rememberCoroutineScope()
    Section(tr("Erweiterungen")) {
        HintText(tr("Große Teile, die nicht alle brauchen, kommen nicht mit jedem Update, sondern werden beim ersten Gebrauch einmalig eingerichtet – aus derselben Quelle wie die Updates – und lassen sich hier wieder entfernen."))
        Extensions.ALL.forEach { def ->
            ExtensionRow(def.name, def.description, states[def.id] ?: Extensions.State.Missing,
                onInstall = { scope.launch { Extensions.install(c.appContext, def.id)?.let { Notices.error(it) } } },
                onRemove = { Extensions.remove(def.id) },
            )
        }
        val b = browser
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Browser-Engine (Chromium)"), style = MaterialTheme.typography.bodyLarge)
            HintText(
                when {
                    browserPending -> tr("Wird beim nächsten Start entfernt.")
                    b != null -> tr("Für den Browser neben dem Chat, eingerichtet") + " · ${formatBytes(b)}"
                    else -> tr("Nicht eingerichtet – das passiert beim ersten Link, der sich neben dem Chat öffnet (ca. 170 MB).")
                },
            )
            if (b != null && !browserPending) OutlinedButton(onClick = {
                if (Extensions.removeBrowser()) { browser = null; Notices.info(tr("Browser-Engine entfernt.")) }
                else { browserPending = true; Notices.info(tr("Der Browser ist gerade geöffnet – die Engine wird beim nächsten Start entfernt.")) }
            }) { Text(tr("Entfernen")) }
        }
    }
}

@Composable
private fun ExtensionRow(name: String, description: String, state: Extensions.State, onInstall: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            StatusPill(
                when (state) {
                    is Extensions.State.Installed -> tr("eingerichtet") + " · ${formatBytes(state.bytes)}"
                    is Extensions.State.Installing -> tr("wird eingerichtet")
                    is Extensions.State.Failed -> tr("Fehler")
                    Extensions.State.Missing -> tr("nicht eingerichtet")
                },
                if (state is Extensions.State.Installed) PillTone.ACCENT else PillTone.NEUTRAL,
            )
        }
        HintText(description)
        when (state) {
            is Extensions.State.Installing -> {
                Text(state.step, style = MaterialTheme.typography.bodySmall)
                if (state.progress >= 0f) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { Extensions.cancel() }) { Text(tr("Abbrechen")) }
            }
            is Extensions.State.Installed -> OutlinedButton(onClick = onRemove) { Text(tr("Entfernen")) }
            is Extensions.State.Failed -> {
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = onInstall) { Text(tr("Erneut versuchen")) }
            }
            Extensions.State.Missing -> Button(onClick = onInstall) { Text(tr("Einrichten")) }
        }
    }
}

/**
 * „Videoeditor einrichten“ (AP-25): shown when a screen needs an extension that is not set up; [onContinue] goes on
 * without it (the screen works with what the app brings).
 */
@Composable
fun ExtensionSetupCard(c: AppContainer, def: Extensions.Def, onContinue: () -> Unit) {
    val states by Extensions.states.collectAsStateWithLifecycle()
    val state = states[def.id] ?: Extensions.State.Missing
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { if (state is Extensions.State.Installed) onContinue() }
    Surface(shape = MaterialTheme.shapes.large, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.widthIn(max = 560.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(def.name + " " + tr("einrichten") + " · " + tr("ca.") + tr(" {0} MB", def.estimateMb), style = MaterialTheme.typography.titleMedium)
            HintText(def.description)
            HintText(tr("Einmalig, aus derselben Quelle wie die Updates; später unter Einstellungen › Werkzeuge › Erweiterungen wieder zu entfernen."))
            when (state) {
                is Extensions.State.Installing -> {
                    Text(state.step, style = MaterialTheme.typography.bodySmall)
                    if (state.progress >= 0f) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is Extensions.State.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = state !is Extensions.State.Installing, onClick = { scope.launch { Extensions.install(c.appContext, def.id) } }) {
                    Text(if (state is Extensions.State.Failed) tr("Erneut versuchen") else tr("Einrichten"))
                }
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = onContinue) { Text(tr("Ohne Paket weiter")) }
            }
        }
    }
}
