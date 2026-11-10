package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ModelRouter
import io.github.thepytales.llmhub.data.CommandModelInfo
import io.github.thepytales.llmhub.data.LoraWays

/**
 * Offers the command model (AP-71) once per start of the app: when Lora is first asked or Einstellungen › Lora is
 * opened, while it is not downloaded. „Laden“, „Später“ (asks again at the next start) or „Nicht mehr fragen“ (the
 * download stays in the settings). Waits while the popup of the recognition model (AP-67) is open.
 */
@Composable
fun CommandModelPrompt(c: AppContainer) {
    val offer by c.commandModel.offer.collectAsState()
    val other by c.piiModel.offer.collectAsState()
    if (!offer || other) return
    fun close() { c.commandModel.offer.value = false }
    AlertDialog(
        onDismissRequest = ::close,
        icon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
        title = { Text(tr("Lora versteht Befehle sofort")) },
        text = {
            Text(
                tr("Lora kann ein kleines Befehlsmodell laden ({0} MB). Damit erkennt sie auf diesem Gerät in Millisekunden, was du " +
                    "meinst – einen Bereich öffnen, eine Einstellung umstellen, ein PDF zusammenfügen –, auch ohne Internet und mit " +
                    "Tippfehlern. Ist sie unsicher, fragt sie wie bisher ein Sprachmodell. Dabei verlässt nichts das Gerät.",
                    CommandModelInfo.MEGABYTES),
            )
        },
        confirmButton = { Button(onClick = { c.commandModel.install(); close() }) { Text(tr("Laden")) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { c.settings.update { it.copy(commandModelAsk = false) }; close() }) { Text(tr("Nicht mehr fragen")) }
                TextButton(onClick = ::close) { Text(tr("Später")) }
            }
        },
    )
}

/** The three ways Lora may work (AP-71) with their names. */
val LORA_PLACES = listOf("MIXED" to trLater("Gemischt (empfohlen)"), "LOCAL" to trLater("Nur lokal"), "SERVER" to trLater("Nur Server"))

/**
 * Einstellungen › Lora › „Wo Lora arbeitet“ (AP-71): the mixed mode with a plain explanation of what each choice costs,
 * the command model (download, switch, model card) and how often and how fast Lora answered where.
 */
@Composable
fun LoraPlaceSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    val st by c.commandModel.state.collectAsState()
    val local by c.localLlm.state.collectAsState()
    var card by remember { mutableStateOf(false) }
    var ways by remember { mutableStateOf(LoraWays.read(c)) }
    LaunchedEffect(Unit) { c.commandModel.offerIfNeeded() }
    Section(tr("Wo Lora arbeitet")) {
        Spot("lora.place") {
            Segmented(LORA_PLACES.map { it.first to tr(it.second) }, ModelRouter.Mode.of(s.loraPlace).name, { m -> c.settings.update { it.copy(loraPlace = m) } })
        }
        HintText(
            when (ModelRouter.Mode.of(s.loraPlace)) {
                ModelRouter.Mode.MIXED -> tr("Lora entscheidet je Anfrage: Befehle erkennt sie sofort auf diesem Gerät. Kurze, einfache Aufgaben (umformulieren, kurz erklären, eine kurze Nachricht) beantwortet ein Sprachmodell auf diesem Gerät, wenn eines installiert ist und die Hardware reicht. Alles andere – lange Dokumente, Analysen, Bilder, Websuche, mehrere Schritte – läuft wie bisher über den Server.")
                ModelRouter.Mode.LOCAL -> tr("Nichts geht an einen Server: Lora nutzt nur das Befehlsmodell und ein Sprachmodell auf diesem Gerät. Das kostet: Die Antworten sind schlichter und öfter ungenau, lange Texte passen nicht hinein (etwa 10 bis 25 Seiten statt ganzer Bücher), eine Antwort braucht ohne Grafikkarte oft eine halbe Minute oder länger, und Websuche, Bilder verstehen und lange Werkzeugketten fehlen oder sind unzuverlässig. Sinnvoll für vertrauliche Arbeit oder ohne Internet.")
                ModelRouter.Mode.SERVER -> tr("Sprachmodelle laufen nur auf dem Server, auch für kurze Aufgaben – am genauesten, aber jede Anfrage braucht Internet und geht (pseudonymisiert) hinaus. Befehle erkennt Lora weiter auf diesem Gerät, solange das Befehlsmodell eingeschaltet ist.")
            },
        )
        if (ModelRouter.Mode.of(s.loraPlace) != ModelRouter.Mode.SERVER && local.installed.isEmpty()) {
            SettingsHint(tr("Auf diesem Gerät ist noch kein Sprachmodell installiert (Einstellungen › Modelle auf diesem Gerät). Bis dahin laufen freie Anfragen über den Server bzw. gar nicht („Nur lokal“)."))
        }
        HintText(tr("Immer gilt: Ins Postfach schaut nur ein Modell auf diesem Gerät, Projekte „Nur auf diesem Gerät“ und der Tresor behalten ihre Regeln, und vor jeder Server-Anfrage werden Namen ersetzt, wenn die Pseudonymisierung an ist."))
        ToggleRow(
            tr("Befehle auf dem Gerät verstehen (empfohlen)"),
            tr("Ein kleines Modell auf diesem Gerät ordnet, was du Lora oder in Strg+K schreibst, in Millisekunden einer Aktion der App zu – offline, auch mit Tippfehlern. Ist es unsicher, führt es nichts aus, sondern Lora fragt wie bisher ein Sprachmodell."),
            s.commandModel,
        ) { v -> c.settings.update { it.copy(commandModel = v) } }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            val progress = st.progress
            when {
                !c.commandModel.available -> SettingsHint(tr("Für diese Version gibt es noch kein Befehlsmodell zum Laden – Lora arbeitet wie bisher."))
                progress != null -> {
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Wird geladen … {0} %", (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = c.commandModel::cancelInstall) { Text(tr("Abbrechen")) }
                    }
                }
                !st.installed -> OutlinedButton(onClick = c.commandModel::install) { Text(tr("Befehlsmodell laden ({0} MB)", CommandModelInfo.MEGABYTES)) }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (st.ready) tr("Geladen und bereit.") else tr("Geladen."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { card = true }) { Text(tr("Modellkarte")) }
                    TextButton(onClick = c.commandModel::delete) { Text(tr("Löschen")) }
                }
            }
            st.message?.let { SettingsHint(it) }
        }
        // How often and how fast Lora answered where – numbers only, kept on this device.
        val device = ways[LoraWays.Way.DEVICE] ?: LoraWays.Sum()
        val onDevice = ways[LoraWays.Way.LOCAL] ?: LoraWays.Sum()
        val server = ways[LoraWays.Way.SERVER] ?: LoraWays.Sum()
        val share = LoraWays.localShare(ways)
        if (share == null) HintText(tr("Sobald du Lora etwas gefragt hast, steht hier, wie oft und wie schnell sie auf diesem Gerät statt über den Server geantwortet hat."))
        else {
            HintText(
                tr("Bisher {0} % ohne Server: {1} × sofort auf dem Gerät (Ø {2}), {3} × mit dem Sprachmodell auf diesem Gerät (Ø {4}), {5} × über den Server (Ø {6}). Gemessen wird, bis Lora antwortet oder weitergibt; gezählt werden nur Zahlen, keine Inhalte.",
                    share, device.count, duration(device.average), onDevice.count, duration(onDevice.average), server.count, duration(server.average)),
            )
            TextButton(onClick = { LoraWays.clear(c); ways = emptyMap() }) { Text(tr("Zähler zurücksetzen")) }
        }
    }
    if (card) {
        val text = remember { CommandModelInfo::class.java.getResourceAsStream("/befehle/MODELLKARTE.md")?.bufferedReader()?.readText().orEmpty() }
        AlertDialog(
            onDismissRequest = { card = false },
            title = { Text(tr("Modellkarte")) },
            text = { Text(text, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { card = false }) { Text("OK") } },
        )
    }
}

/** Milliseconds as „12 ms“ or „2,4 s“. */
internal fun duration(millis: Long): String = if (millis < 1000) "$millis ms" else String.format(java.util.Locale.GERMANY, "%.1f s", millis / 1000.0)
