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
            .onFailure { Notices.error(it.message ?: tr("Fehler")) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = { Text(tr("Modelle auf diesem Gerät")) },
            )
        },
    ) { pad ->
        ReadableColumn(pad) {
            HintText(
                tr("Sprachmodelle, die komplett auf diesem Gerät laufen – ohne Internet und ohne dass Daten das Gerät verlassen. " +
                    "Ideal für vertrauliche Inhalte (Projekte mit der Datenschutzstufe „Nur auf diesem Gerät“). Sie sind kleiner als " +
                    "die Modelle des Uni-Servers: gut für Zusammenfassen, Umformulieren, Briefe und Feedback, weniger für lange Agenten-Aufgaben."),
            )
            HardwareCard(hw, state)
            hw?.let { DeviceProfileCard(c, it, state) }
            LocalMessage(c, state)

            val h = hw
            if (h == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    HintText(tr("Gerät wird geprüft …"))
                }
                return@ReadableColumn
            }
            val recommended = LocalCatalog.recommended(h)
            GroupLabel(tr("Empfohlen für dieses Gerät"))
            AppCard {
                Column {
                    recommended.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        LocalModelRow(c, m, Fit.RECOMMENDED, state, onTry)
                    }
                }
            }
            TextButton(onClick = { others = !others }) { Text(if (others) tr("Weitere Modelle ausblenden") else tr("Weitere Modelle anzeigen")) }
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

            LocalSpeedCard(c, LocalCatalog.models.filter { it.id in state.installed })
            GroupLabel(tr("Download im Firmennetz gesperrt?"))
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText(
                        tr("Modelldateien (.gguf) lassen sich auch im Browser oder an einem anderen Rechner laden (Adresse unten kopieren) und hier " +
                            "einspielen; jede Datei wird mit ihrer Prüfsumme kontrolliert.") +
                            (local.runtimePackage()?.takeIf { state.runtime == RuntimeStatus.MISSING }?.let { tr(" Das gilt auch für das Programm llama.cpp: für dieses Gerät die Datei „{0}“ (github.com/ggml-org/llama.cpp/releases).", it) } ?: ""),
                    )
                    OutlinedButton(onClick = import) { Text(tr("Datei einspielen …")) }
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    recommended.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.file, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.url)) }) { Text(tr("Adresse kopieren")) }
                        }
                    }
                }
            }
            if (state.running != null) {
                TextButton(onClick = { scope.launch { local.stop() } }) { Text(tr("Laufendes Modell beenden (Speicher freigeben)")) }
            }
        }
    }
}

/** „schreibt ≈ 12 Wörter/s · liest ≈ 300 Wörter/s · geschätzt“ – the forecast of AP-105 for one model. */
@Composable
internal fun forecastLine(c: AppContainer, m: LocalModel, hw: Hardware): String {
    val stored by io.github.thepytales.llmhub.local.DeviceBench.stored(c).collectAsStateWithLifecycle()
    val speed by io.github.thepytales.llmhub.local.LocalSpeed.results(c).collectAsStateWithLifecycle()
    val f = remember(m.id, hw, stored, speed) { io.github.thepytales.llmhub.local.DeviceBench.forecast(c, m, hw) }
    val how = when (f.source) {
        io.github.thepytales.llmhub.local.SpeedForecast.Source.MEASURED -> tr("gemessen")
        io.github.thepytales.llmhub.local.SpeedForecast.Source.CALIBRATED -> tr("vorhergesagt nach Messung")
        io.github.thepytales.llmhub.local.SpeedForecast.Source.ESTIMATE -> tr("geschätzt")
    }
    return tr("schreibt ≈ {0} Wörter/s · liest ≈ {1} Wörter/s · {2}", io.github.thepytales.llmhub.local.SpeedForecast.words(f.wordsPerSec),
        io.github.thepytales.llmhub.local.SpeedForecast.words(f.readWordsPerSec), how)
}

/**
 * Geräteprofil und Kurzmessung (AP-105): what decides the speed here (memory bandwidth, instruction sets, Apple chip),
 * „Gerät messen“ (about 20 s) and the protocol to copy – forecast against measurement per model.
 */
@Composable
internal fun DeviceProfileCard(c: AppContainer, hw: Hardware, state: LocalState) {
    val scope = rememberCoroutineScope()
    val stored by io.github.thepytales.llmhub.local.DeviceBench.stored(c).collectAsStateWithLifecycle()
    val speed by io.github.thepytales.llmhub.local.LocalSpeed.results(c).collectAsStateWithLifecycle()
    var running by remember { mutableStateOf<String?>(null) }
    val here = remember(stored, hw) { io.github.thepytales.llmhub.local.DeviceBench.here(c, hw) }
    val samples = remember(stored, speed, hw) { io.github.thepytales.llmhub.local.DeviceBench.samples(c, hw) }
    val checks = remember(samples) { io.github.thepytales.llmhub.local.SpeedForecast.check(samples, hw, here.javaGbs.takeIf { it > 0 }) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    GroupLabel(tr("Geräteprofil"))
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val bw = hw.memBandwidthGbs.takeIf { !it.isNaN() }
            HintText(
                listOfNotNull(
                    hw.chip.takeIf { it.isNotBlank() }?.let { tr("{0} mit gemeinsamem Speicher", it) + (hw.gpuCores.takeIf { n -> n > 0 }?.let { n -> tr(", {0} Grafikkerne", n) } ?: "") },
                    hw.memDetail.takeIf { it.isNotBlank() && hw.chip.isBlank() },
                    bw?.let { tr("Speicherbandbreite {0} GB/s", String.format(java.util.Locale.GERMANY, "%.0f", it)) },
                    here.javaGbs.takeIf { it > 0 }?.let { tr("gemessen {0} GB/s", String.format(java.util.Locale.GERMANY, "%.0f", it)) },
                    hw.cpuFeatures.takeIf { it.isNotEmpty() }?.let { tr("Befehlssätze: {0}", it.sorted().joinToString(", ") { f -> f.uppercase() }) },
                ).joinToString(" · ").ifBlank { tr("Speicherbandbreite und Befehlssätze sind hier nicht lesbar – die Kurzmessung misst sie.") },
            )
            HintText(
                tr("Wie schnell ein Modell schreibt, hängt vor allem an der Speicherbandbreite und daran, wie viele Parameter es je Wort rechnet; das Einlesen langer Texte am Prozessor bzw. an der Grafik. Daraus sagt Lora das Tempo jedes Modells vorher. Die Kurzmessung (etwa 20 Sekunden, mit dem kleinsten installierten Modell) macht die Vorhersage genauer; „Messen“ unten misst danach die echten Werte und korrigiert weiter."),
            )
            if (state.installed.isEmpty()) HintText(tr("Ohne installiertes Modell misst die Kurzmessung nur den Speicher – die Werte bleiben geschätzt."))
            if (checks.isNotEmpty()) {
                val ok = checks.count { it.withinTarget }
                HintText(tr("Vorhersage gegen Messung: {0} von {1} Modellen höchstens 30 % daneben ({2}).", ok, checks.size,
                    checks.joinToString(", ") { k -> (io.github.thepytales.llmhub.local.LocalCatalog.get(k.modelId)?.name?.let { tr(it) } ?: k.modelId) + String.format(java.util.Locale.GERMANY, " %+.0f %%", k.deviation * 100) }))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running == null) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                io.github.thepytales.llmhub.local.DeviceBench.run(c) { running = it }
                                Notices.info(tr("Gerät gemessen – die Vorhersagen sind aktualisiert."))
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Notices.error(e)
                            } finally { running = null }
                        }
                    }) { Text(if (here.at > 0) tr("Neu messen (≈ 20 s)") else tr("Gerät messen (≈ 20 s)")) }
                    TextButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(io.github.thepytales.llmhub.local.DeviceBench.protocol(c, hw)))
                        Notices.info(tr("Messprotokoll kopiert."))
                    }) { Text(tr("Messprotokoll kopieren")) }
                } else {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    HintText(running!!, Modifier.weight(1f))
                }
            }
        }
    }
}

/** The last message of the local models; after a failed download with "Erneut versuchen" (AP-86). */
@Composable
fun LocalMessage(c: AppContainer, state: LocalState) {
    val msg = state.message ?: return
    Column {
        HintText(msg)
        if (state.retry != null && state.downloads.isEmpty() && state.runtime != RuntimeStatus.INSTALLING) {
            TextButton(onClick = { c.localLlm.retry() }) { Text(tr("Erneut versuchen")) }
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
                Text(tr("Dieses Gerät"), style = MaterialTheme.typography.titleSmall)
                HintText(hw?.describe() ?: tr("wird geprüft …"))
                HintText(
                    when {
                        state.starting != null -> tr("{0} wird geladen …", state.starting)
                        state.running != null -> tr("Läuft gerade: {0}", LocalCatalog.get(state.running)?.name)
                        hw?.vramGb ?: 0.0 >= 4 -> tr("Die Grafikkarte rechnet mit – auch größere Modelle laufen flüssig.")
                        hw?.phone == true -> tr("Das Handy rechnet mit dem Prozessor; kleine Modelle laufen flüssig.")
                        else -> tr("Ohne Grafikkarte rechnet der Prozessor – kleine und mittlere Modelle laufen gut.")
                    },
                )
            }
        }
        if (hw?.limited == true) {
            HintText(
                tr("Die genaue Prüfung (Grafikkarte, Arbeitsspeicher) ist auf diesem PC gesperrt – vermutlich durch IT-Richtlinien. " +
                    "Die Empfehlungen beruhen deshalb auf den Grundangaben und sind eher vorsichtig; installieren lassen sich alle Modelle trotzdem."),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
        if (state.runtime == RuntimeStatus.UNSUPPORTED) {
            HintText(tr("Auf diesem Gerät sind lokale Modelle nicht verfügbar (nur Android-Handys mit 64-Bit-ARM, Windows-PCs, Macs und Linux-PCs)."), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
        state.runtimeProgress?.let { p ->
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                HintText(tr("Programm für lokale Modelle wird eingerichtet … {0} %", (p * 100).toInt()))
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
                        installed -> StatusPill(tr("installiert"), PillTone.SUCCESS)
                        fit == Fit.RECOMMENDED -> StatusPill(fit.label)
                        fit == Fit.TOO_BIG -> StatusPill(fit.label, PillTone.DANGER)
                        fit == Fit.SLOW -> StatusPill(fit.label, PillTone.WARNING)
                        else -> StatusPill(fit.label, PillTone.NEUTRAL)
                    }
                }
                HintText(m.description + " · " + formatBytes(m.size) + tr(" · braucht ca. {0} GB Speicher", m.needGb.toInt()))
                c.localLlm.hardware.collectAsStateWithLifecycle().value?.let { h -> HintText(forecastLine(c, m, h)) }
            }
            when {
                installed -> {
                    IconButton(onClick = { onTry(m.modelId) }) { Icon(Icons.Outlined.PlayArrow, contentDescription = tr("Im Chat ausprobieren"), tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { local.delete(m.id) }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen"), tint = MaterialTheme.colorScheme.error) }
                }
                progress != null -> TextButton(onClick = { local.cancelInstall(m.id) }) { Text(tr("Stopp")) }
                else -> {
                    val enabled = fit != Fit.TOO_BIG && state.runtime != RuntimeStatus.UNSUPPORTED
                    if (fit == Fit.RECOMMENDED) {
                        Button(enabled = enabled, onClick = { local.install(m.id) }) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Installieren"))
                        }
                    } else {
                        OutlinedButton(enabled = enabled, onClick = { local.install(m.id) }) { Text(tr("Installieren")) }
                    }
                }
            }
        }
        progress?.let { p ->
            LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
            HintText(tr("{0} % von {1}", (p * 100).toInt(), formatBytes(m.size)))
        }
    }
}

/**
 * Sprachmodell-Empfehlung nach Messung (AP-76): „Messen“ lässt jedes installierte Modell einmal eine kurze Mail schreiben
 * (etwa eine halbe Minute je Modell) und zeigt Wörter je Sekunde, Zeit bis zum ersten Wort und was das heißt – dazu die
 * Empfehlung für Postfach, kurze Aufgaben im Mischmodus und vertrauliche Projekte.
 */
@Composable
internal fun LocalSpeedCard(c: AppContainer, installed: List<LocalModel>) {
    if (installed.isEmpty()) return
    val results by io.github.thepytales.llmhub.local.LocalSpeed.results(c).collectAsStateWithLifecycle()
    val hw by c.localLlm.hardware.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val here = installed.mapNotNull { m -> io.github.thepytales.llmhub.local.LocalSpeed.of(c, m.modelId, hw)?.let { m.modelId to it } }.toMap()
    GroupLabel(tr("Wie schnell sind die Modelle hier?"))
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HintText(
                tr("Lora misst auf Wunsch, wie schnell die installierten Modelle auf diesem Gerät wirklich schreiben – etwa eine halbe Minute je Modell. Danach empfiehlt sie das passende Modell, und der Mischmodus nimmt für kurze Aufgaben nur Modelle, die schnell genug sind."),
            )
            installed.forEach { m ->
                val r = here[m.modelId]
                Column {
                    Text(tr(m.name), style = MaterialTheme.typography.titleSmall)
                    HintText(r?.let { io.github.thepytales.llmhub.local.LocalSpeed.advice(it).text } ?: tr("noch nicht gemessen"))
                }
            }
            if (here.isNotEmpty()) {
                val picks = io.github.thepytales.llmhub.local.LocalSpeed.picks(installed, here)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(tr("Empfehlung"), style = MaterialTheme.typography.titleSmall)
                HintText(tr("Postfach: {0}", picks.mail?.let { tr(it.name) } ?: tr("keines schnell genug – für lange Mails ein kleineres Modell laden")))
                HintText(tr("Kurze Aufgaben im Mischmodus: {0}", picks.short?.let { tr(it.name) } ?: tr("keines – solche Aufgaben gehen an den Server")))
                HintText(tr("Vertrauliche Projekte: {0}", picks.confidential?.let { tr(it.name) } ?: tr("keines schnell genug")))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (running == null) {
                    Button(onClick = {
                        job = scope.launch {
                            try {
                                for (m in installed) {
                                    runCatching { io.github.thepytales.llmhub.local.LocalSpeed.measure(c, m) { running = it } }
                                        .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e; Notices.error(e) }
                                }
                            } finally {
                                running = null
                            }
                        }
                    }) { Text(if (here.isEmpty()) tr("Messen") else tr("Neu messen")) }
                } else {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    HintText(running!!, Modifier.weight(1f))
                    TextButton(onClick = { job?.cancel() }) { Text(tr("Abbrechen")) }
                }
            }
        }
    }
}
