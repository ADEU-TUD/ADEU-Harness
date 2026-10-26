package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.local.Fit
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalModel
import io.github.thepytales.llmhub.local.LocalState
import io.github.thepytales.llmhub.local.RuntimeStatus
import kotlinx.coroutines.launch

/**
 * Einstellungen › Modelle auf diesem Gerät: what this device can run, the recommended models with one button to
 * install, the others with how well they fit, and loading files by hand for company networks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(c: AppContainer, onBack: () -> Unit, onTry: (String) -> Unit) {
    val local = c.localLlm
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by local.state.collectAsStateWithLifecycle()
    val hw by local.hardware.collectAsStateWithLifecycle()
    var others by remember { mutableStateOf(false) }
    val import = rememberMediaImport(anyFile = true) { name, _, input ->
        runCatching { local.importFile(name, input) }
            .onSuccess { Notices.info(it) }
            .onFailure { Notices.error(it.message ?: "Fehler") }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = { Text("Modelle auf diesem Gerät") },
            )
        },
    ) { pad ->
        ReadableColumn(pad) {
            HintText(
                "Sprachmodelle, die komplett auf diesem Gerät laufen – ohne Internet und ohne dass Daten das Gerät verlassen. " +
                    "Ideal für vertrauliche Inhalte (Projekte mit der Datenschutzstufe „Nur auf diesem Gerät“). Sie sind kleiner als " +
                    "die Modelle des Uni-Servers: gut für Zusammenfassen, Umformulieren, Briefe und Feedback, weniger für lange Agenten-Aufgaben.",
            )
            HardwareCard(hw, state)
            state.message?.let { HintText(it) }

            val h = hw
            if (h == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    HintText("Gerät wird geprüft …")
                }
                return@ReadableColumn
            }
            val recommended = LocalCatalog.recommended(h)
            GroupLabel("Empfohlen für dieses Gerät")
            AppCard {
                Column {
                    recommended.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        LocalModelRow(c, m, Fit.RECOMMENDED, state, onTry)
                    }
                }
            }
            TextButton(onClick = { others = !others }) { Text(if (others) "Weitere Modelle ausblenden" else "Weitere Modelle anzeigen") }
            if (others) {
                AppCard {
                    Column {
                        LocalCatalog.models.filter { it !in recommended }.forEachIndexed { i, m ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            LocalModelRow(c, m, LocalCatalog.fit(m, h), state, onTry)
                        }
                    }
                }
            }

            GroupLabel("Download im Firmennetz gesperrt?")
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText(
                        "Modelldateien (.gguf) lassen sich auch im Browser oder an einem anderen Rechner laden (Adresse unten kopieren) und hier " +
                            "einspielen; jede Datei wird mit ihrer Prüfsumme kontrolliert." +
                            if (state.runtime == RuntimeStatus.MISSING) " Unter Windows gilt das auch für das Programm: llama.cpp-Paket „bin-win-cpu-x64“ als ZIP." else "",
                    )
                    OutlinedButton(onClick = import) { Text("Datei einspielen …") }
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    recommended.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.file, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.url)) }) { Text("Adresse kopieren") }
                        }
                    }
                }
            }
            if (state.running != null) {
                TextButton(onClick = { scope.launch { local.stop() } }) { Text("Laufendes Modell beenden (Speicher freigeben)") }
            }
        }
    }
}

@Composable
private fun HardwareCard(hw: Hardware?, state: LocalState) {
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Dieses Gerät", style = MaterialTheme.typography.titleSmall)
                HintText(hw?.describe() ?: "wird geprüft …")
                HintText(
                    when {
                        state.starting != null -> "${state.starting} wird geladen …"
                        state.running != null -> "Läuft gerade: ${LocalCatalog.get(state.running)?.name}"
                        hw?.vramGb ?: 0.0 >= 4 -> "Die Grafikkarte rechnet mit – auch größere Modelle laufen flüssig."
                        hw?.phone == true -> "Das Handy rechnet mit dem Prozessor; kleine Modelle laufen flüssig."
                        else -> "Ohne Grafikkarte rechnet der Prozessor – kleine und mittlere Modelle laufen gut."
                    },
                )
            }
        }
        if (hw?.limited == true) {
            HintText(
                "Die genaue Prüfung (Grafikkarte, Arbeitsspeicher) ist auf diesem PC gesperrt – vermutlich durch IT-Richtlinien. " +
                    "Die Empfehlungen beruhen deshalb auf den Grundangaben und sind eher vorsichtig; installieren lassen sich alle Modelle trotzdem.",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
        if (state.runtime == RuntimeStatus.UNSUPPORTED) {
            HintText("Auf diesem Gerät sind lokale Modelle nicht verfügbar (nur Android-Handys mit 64-Bit-ARM und Windows-PCs).", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
        state.runtimeProgress?.let { p ->
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                HintText("Programm für lokale Modelle wird eingerichtet … ${(p * 100).toInt()} %")
                LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
private fun LocalModelRow(c: AppContainer, m: LocalModel, fit: Fit, state: LocalState, onTry: (String) -> Unit) {
    val local = c.localLlm
    val installed = m.id in state.installed
    val progress = state.downloads[m.id]
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    when {
                        installed -> StatusPill("installiert", PillTone.SUCCESS)
                        fit == Fit.RECOMMENDED -> StatusPill(fit.label)
                        fit == Fit.TOO_BIG -> StatusPill(fit.label, PillTone.DANGER)
                        fit == Fit.SLOW -> StatusPill(fit.label, PillTone.WARNING)
                        else -> StatusPill(fit.label, PillTone.NEUTRAL)
                    }
                }
                HintText(m.description + " · " + formatBytes(m.size) + " · braucht ca. ${m.needGb.toInt()} GB Speicher")
            }
            when {
                installed -> {
                    IconButton(onClick = { onTry(m.modelId) }) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Im Chat ausprobieren", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { local.delete(m.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error) }
                }
                progress != null -> TextButton(onClick = { local.cancelInstall(m.id) }) { Text("Stopp") }
                else -> {
                    val enabled = fit != Fit.TOO_BIG && state.runtime != RuntimeStatus.UNSUPPORTED
                    if (fit == Fit.RECOMMENDED) {
                        Button(enabled = enabled, onClick = { local.install(m.id) }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Installieren")
                        }
                    } else {
                        OutlinedButton(enabled = enabled, onClick = { local.install(m.id) }) { Text("Installieren") }
                    }
                }
            }
        }
        progress?.let { p ->
            LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
            HintText("${(p * 100).toInt()} % von ${formatBytes(m.size)}")
        }
    }
}
