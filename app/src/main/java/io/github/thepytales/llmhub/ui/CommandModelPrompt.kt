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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ModelRouter
import io.github.thepytales.llmhub.data.CommandModelInfo
import io.github.thepytales.llmhub.data.LoraWays
import io.github.thepytales.llmhub.data.PiiModelInfo
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalModel

/**
 * Offers Lora Befehle, the command model (AP-71), once per start of the app: when Lora is first asked or Einstellungen ›
 * Lora is opened, while it is not downloaded. „Laden“, „Später“ (asks again at the next start) or „Nicht mehr fragen“
 * (the download stays in the settings). Who still has the model of 1.0.65 gets the new version offered as an update.
 * Waits while the popup of Lora Namensschutz (AP-67) is open.
 */
@Composable
fun CommandModelPrompt(c: AppContainer) {
    val offer by c.commandModel.offer.collectAsState()
    val other by c.piiModel.offer.collectAsState()
    val s by c.settings.settings.collectAsState()
    // Until „Welche Modelle sollen auf dieses Gerät?“ is answered, that dialog asks – not this popup on top of it.
    if (!offer || other || !s.modelsIntroDone) return
    val update = remember { c.commandModel.hasOld() }
    fun close() { c.commandModel.offer.value = false }
    AlertDialog(
        onDismissRequest = ::close,
        icon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
        title = { Text(if (update) tr("Neue Version von {0}", CommandModelInfo.NAME) else tr("Lora versteht Befehle sofort")) },
        text = {
            Text(
                if (update) tr("Es gibt eine bessere Version von {0} ({1} MB): Sie erkennt mehr Befehle selbst, auch umgangssprachlich und " +
                    "auf Englisch, und führt weiter nichts aus, wenn sie unsicher ist. Bis du sie lädst, arbeitet die bisherige Version " +
                    "weiter. Dabei verlässt nichts das Gerät.", CommandModelInfo.NAME, CommandModelInfo.MEGABYTES)
                else tr("Lora kann {0} laden ({1} MB), ein kleines Modell nur für Befehle. Damit erkennt sie auf diesem Gerät in " +
                    "Millisekunden, was du meinst – einen Bereich öffnen, eine Einstellung umstellen, ein PDF zusammenfügen –, auch ohne " +
                    "Internet und mit Tippfehlern. Ist sie unsicher, fragt sie wie bisher ein Sprachmodell. Dabei verlässt nichts das Gerät.",
                    CommandModelInfo.NAME, CommandModelInfo.MEGABYTES),
            )
        },
        confirmButton = { Button(onClick = { c.commandModel.install(); close() }) { Text(tr("Laden")) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    c.settings.update { if (update) it.copy(commandModelUpdateAsk = false) else it.copy(commandModelAsk = false) }
                    close()
                }) { Text(tr("Nicht mehr fragen")) }
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
                ModelRouter.Mode.LOCAL -> tr("Nichts geht an einen Server: Lora nutzt nur Lora Befehle und ein Sprachmodell auf diesem Gerät. Das kostet: Die Antworten sind schlichter und öfter ungenau, lange Texte passen nicht hinein (etwa 10 bis 25 Seiten statt ganzer Bücher), eine Antwort braucht ohne Grafikkarte oft eine halbe Minute oder länger, und Websuche, Bilder verstehen und lange Werkzeugketten fehlen oder sind unzuverlässig. Sinnvoll für vertrauliche Arbeit oder ohne Internet.")
                ModelRouter.Mode.SERVER -> tr("Sprachmodelle laufen nur auf dem Server, auch für kurze Aufgaben – am genauesten, aber jede Anfrage braucht Internet und geht (pseudonymisiert) hinaus. Befehle erkennt Lora weiter auf diesem Gerät, solange Lora Befehle eingeschaltet ist.")
            },
        )
        if (ModelRouter.Mode.of(s.loraPlace) != ModelRouter.Mode.SERVER && local.installed.isEmpty()) {
            SettingsHint(tr("Auf diesem Gerät ist noch kein Sprachmodell installiert (Einstellungen › Modelle auf diesem Gerät). Bis dahin laufen freie Anfragen über den Server bzw. gar nicht („Nur lokal“)."))
        }
        HintText(tr("Immer gilt: Ins Postfach schaut nur ein Modell auf diesem Gerät, Projekte „Nur auf diesem Gerät“ und der Tresor behalten ihre Regeln, und vor jeder Server-Anfrage werden Namen ersetzt, wenn die Pseudonymisierung an ist."))
        ToggleRow(
            tr("{0}: Befehle auf dem Gerät verstehen (empfohlen)", CommandModelInfo.NAME),
            tr("Ein kleines Modell auf diesem Gerät ordnet, was du Lora oder in Strg+K schreibst, in Millisekunden einer Aktion der App zu – offline, auch mit Tippfehlern. Ist es unsicher, führt es nichts aus, sondern Lora fragt wie bisher ein Sprachmodell."),
            s.commandModel,
        ) { v -> c.settings.update { it.copy(commandModel = v) } }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            val progress = st.progress
            when {
                !c.commandModel.available -> SettingsHint(tr("Für diese Version gibt es noch kein {0} zum Laden – Lora arbeitet wie bisher.", CommandModelInfo.NAME))
                progress != null -> {
                    LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tr("Wird geladen … {0} %", (progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = c.commandModel::cancelInstall) { Text(tr("Abbrechen")) }
                    }
                }
                st.old -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr("Die bisherige Version ist geladen und arbeitet weiter. Es gibt eine bessere."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = c.commandModel::install) { Text(tr("Neue Version laden ({0} MB)", CommandModelInfo.MEGABYTES)) }
                        TextButton(onClick = c.commandModel::delete) { Text(tr("Löschen")) }
                    }
                }
                !st.installed -> OutlinedButton(onClick = c.commandModel::install) { Text(tr("{0} laden ({1} MB)", CommandModelInfo.NAME, CommandModelInfo.MEGABYTES)) }
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

/**
 * Erster Start (AP-71): what the user ticks under „Welche Modelle sollen auf dieses Gerät?“ – the two small helper
 * models, a language model that fits the hardware, and where Lora works. [apply] starts the downloads.
 */
class IntroModelChoice(private val c: AppContainer) {
    /** Offered only while it is not there yet. */
    val offerCommand = c.commandModel.available && !c.commandModel.installed()
    val offerPii = !c.piiModel.installed()
    /** There is something to decide at all. */
    val anything: Boolean get() = offerCommand || offerPii || c.localLlm.installedModels().isEmpty()
    var command by mutableStateOf(offerCommand)
    var pii by mutableStateOf(offerPii)
    /** Ids of the language models to download (none by default: they are large). */
    val local = mutableStateListOf<String>()
    /** Wissenspakete (AP-78) to load; empty by default – who needs one ticks it (or a role suggests it, AP-80). */
    val packs = mutableStateListOf<String>()
    var place by mutableStateOf(ModelRouter.Mode.of(c.settings.current.loraPlace).name)

    fun apply(c: AppContainer) {
        if (offerCommand) { if (command) c.commandModel.install() }
        if (offerPii) { if (pii) c.piiModel.install() }
        local.forEach { c.localLlm.install(it) }
        packs.mapNotNull { io.github.thepytales.llmhub.data.KnowledgePacks.offer(it) }.forEach { o ->
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { io.github.thepytales.llmhub.data.KnowledgePacks.download(c, o) }.onFailure { Notices.error(it) }
            }
        }
        // Who decides here is not asked again by the single popups.
        c.settings.update { it.copy(modelsIntroDone = true, loraPlace = place, commandModelAsk = if (offerCommand) false else it.commandModelAsk, pseudonymModelAsk = if (offerPii) false else it.pseudonymModelAsk) }
    }
}

/**
 * Erster Start: „Welche Modelle sollen auf dieses Gerät?“ – one line per purpose with a short explanation and the size,
 * the language models that fit this hardware, and where Lora works. Everything can be changed later in the settings.
 */
@Composable
fun IntroModels(c: AppContainer, choice: IntroModelChoice) {
    val hw by c.localLlm.hardware.collectAsState()
    val local by c.localLlm.state.collectAsState()
    val suggested: List<LocalModel> = hw?.let { LocalCatalog.recommended(it) }.orEmpty().filter { it.id !in local.installed }
    Text(tr("Welche Modelle sollen auf dieses Gerät?"), style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (choice.offerCommand) ModelLine(
            tr("{0}: Befehle verstehen ({1} MB)", CommandModelInfo.NAME, CommandModelInfo.MEGABYTES),
            tr("Lora erkennt sofort und offline, was du meinst – Bereich öffnen, Einstellung umstellen, PDF zusammenfügen."),
            choice.command,
        ) { choice.command = it }
        if (choice.offerPii) ModelLine(
            tr("{0}: Namen schützen ({1} MB)", PiiModelInfo.NAME, PiiModelInfo.MEGABYTES),
            tr("Die Pseudonymisierung findet Namen, Schulen und Orte zuverlässiger, bevor etwas an einen Server geht."),
            choice.pii,
        ) { choice.pii = it }
        suggested.forEach { m ->
            ModelLine(
                tr("Sprachmodell {0} ({1} GB)", tr(m.name), String.format(java.util.Locale.GERMANY, "%.1f", m.size / 1e9)),
                tr("Für das Postfach, vertrauliche Daten, kurze Aufgaben und ohne Internet – passt zu diesem Gerät. {0}", tr(m.description)),
                m.id in choice.local,
            ) { on -> if (on) choice.local += m.id else choice.local -= m.id }
        }
        if (suggested.isEmpty() && local.installed.isEmpty()) HintText(tr("Sprachmodelle für dieses Gerät findest du später unter Einstellungen › Modelle auf diesem Gerät."))
        // Wissenspakete (AP-78): who needs one ticks it; everything else is under Einstellungen › Inhalte zum Nachladen.
        val packsInstalled = remember { io.github.thepytales.llmhub.data.KnowledgePacks.installed(c).map { it.id }.toSet() }
        io.github.thepytales.llmhub.data.KnowledgePacks.CATALOG.filter { it.id !in packsInstalled }.forEach { o ->
            ModelLine(
                tr("Wissenspaket „{0}“ ({1} KB)", tr(o.title), (o.size / 1024).coerceAtLeast(1)),
                tr(o.description),
                o.id in choice.packs,
            ) { on -> if (on) choice.packs += o.id else choice.packs -= o.id }
        }
    }
    Text(tr("Wo soll ich arbeiten?"), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LORA_PLACES.forEach { (id, label) -> FilterChip(selected = choice.place == id, onClick = { choice.place = id }, label = { Text(tr(label)) }) }
    }
    HintText(
        when (ModelRouter.Mode.of(choice.place)) {
            ModelRouter.Mode.MIXED -> tr("Befehle und kurze Aufgaben auf diesem Gerät, alles Anspruchsvolle über den Uni-Server (Chat: DeepSeek V4.1 Flash, Lora: GLM-5.3 Flash).")
            ModelRouter.Mode.LOCAL -> tr("Nichts geht an einen Server – dafür schlichtere, langsamere Antworten, kurze Texte, keine Websuche. Braucht ein Sprachmodell auf diesem Gerät.")
            ModelRouter.Mode.SERVER -> tr("Sprachmodelle nur über den Uni-Server – am genauesten, braucht immer Internet.")
        },
    )
}

@Composable
private fun ModelLine(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** „Später“ of [ModelsIntroDialog] holds until the next start of the app. */
private var modelsIntroLater by mutableStateOf(false)

/**
 * „Neu: Modelle für dieses Gerät“ (AP-71) – once for everyone who set the app up before the question existed: the same
 * choice as at the first start (command model, recognition model, a language model that fits, where Lora works), so
 * they see what is new and decide. „Später“ asks again at the next start, „Nicht mehr fragen“ never; new users answer
 * it in Lora's first-start dialog instead.
 */
@Composable
fun ModelsIntroDialog(c: AppContainer) {
    val s by c.settings.settings.collectAsState()
    // New users with a key get the question in Lora's first-start dialog (LoraIntroDialog), which sets the flag.
    val firstStartAsks = !s.loraIntroDone && s.hasAnyApiKey && !s.hideLora
    if (s.modelsIntroDone || modelsIntroLater || firstStartAsks) return
    val choice = remember { IntroModelChoice(c) }
    if (!choice.anything) {
        LaunchedEffect(Unit) { c.settings.update { it.copy(modelsIntroDone = true) } }
        return
    }
    AlertDialog(
        onDismissRequest = { modelsIntroLater = true },
        icon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
        title = { Text(tr("Neu: Modelle für dieses Gerät")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Lora kann jetzt mehr auf diesem Gerät erledigen – schneller, offline und ohne dass etwas hinausgeht. Such aus, was du möchtest; alles lässt sich später unter Einstellungen › Lora und › Modelle auf diesem Gerät ändern."), style = MaterialTheme.typography.bodyMedium)
                IntroModels(c, choice)
            }
        },
        confirmButton = { Button(onClick = { choice.apply(c) }) { Text(tr("Übernehmen")) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { c.settings.update { it.copy(modelsIntroDone = true) } }) { Text(tr("Nicht mehr fragen")) }
                TextButton(onClick = { modelsIntroLater = true }) { Text(tr("Später")) }
            }
        },
    )
}
