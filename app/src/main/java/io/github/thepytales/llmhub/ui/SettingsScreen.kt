package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.SecureStorage
import io.github.thepytales.llmhub.agent.AgentRunner
import io.github.thepytales.llmhub.audio.VoiceOption
import io.github.thepytales.llmhub.data.Logo
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, spot: String? = null, onChange: (Boolean) -> Unit) {
    val row: @Composable () -> Unit = {
        // One switch for screen readers and the keyboard (AP-30): title and subtitle are read with its state.
        Row(
            Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
    if (spot != null) Spot(spot) { row() } else row()
}

/** Settings as a clear list of sections (like the ChatGPT app); [section] opens one of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(c: AppContainer, section: String?, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(settingsTitle(section)) }) },
        // Lora schläft unten rechts – ein Tipp weckt sie (AP-07).
        floatingActionButton = { LoraHelperButton(c) },
    ) { pad ->
        ReadableColumn(pad) {
            when (section) {
                "api" -> ApiSection(c)
                "lora" -> LoraSettings(c)
                "places" -> PlacesSettings(c, onNavigate)
                "github" -> GitHubSection(c)
                "chat" -> ChatSection(c)
                "tools" -> ToolsSection(c)
                "ram" -> MemoryUsageSection(c)
                "privacy" -> PrivacySection(c)
                "appearance" -> AppearanceSettings(c)
                "transcription" -> TranscriptionSettings(c)
                "voice" -> VoiceSection(c)
                "documents" -> { DocumentsSection(c); HouseStyleSettings(c) }
                "memory" -> MemorySection(c)
                "knowledge" -> KnowledgeSection(c)
                "literature" -> LiteratureSettings(c)
                "backup" -> BackupSection(c)
                "trash" -> TrashSection(c)
                "folders" -> LocalFoldersSection(c)
                "storage" -> StorageSection(c)
                "connections" -> ConnectionsSettings(c)
                "updates" -> UpdateSection(c)
                "help" -> HelpSection()
                "downloads" -> DownloadsSection(c, onNavigate)
                else -> if (!PlatformSettingsSection(c, section)) SettingsOverview(c, onNavigate, deviceItem = platformSettingsItem())
            }
            // Room for the sleeping Lora at the bottom right, so she covers no entry.
            Spacer(Modifier.size(LORA_BUTTON_ROOM))
        }
    }
}

@Composable
private fun ApiSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    if (!SecureStorage.encrypted) SettingsCard(tr("Achtung")) {
        Text(
            tr("Der Schlüsselspeicher dieses Geräts funktioniert nicht. API-Key und Tokens liegen deshalb unverschlüsselt im " +
                "privaten Speicher der App. Andere Apps können sie nicht lesen, bei einem gerooteten Gerät oder einer Datensicherung " +
                "per Kabel aber schon. Ein Neustart des Geräts behebt das manchmal."),
            color = MaterialTheme.colorScheme.error,
        )
    }
    Section(tr("ScaDS.AI · Standard")) {
        // Wohin die Daten gehen (AP-119).
        DataPlaceChip(io.github.thepytales.llmhub.data.DataPlace.main(c))
        Text(
            tr("API-Key aus dem Self-Service-Portal deiner Uni (beginnt mit „sk-“). {0}", KEY_STORAGE_HINT),
            style = MaterialTheme.typography.bodySmall,
        )
        SecretField(tr("API-Key"), s.apiKey) { v -> c.settings.update { it.copy(apiKey = v.trim()) } }
        OutlinedTextField(
            value = s.baseUrl,
            onValueChange = { v -> c.settings.update { it.copy(baseUrl = v.trim()) } },
            label = { Text(tr("API-Basis-URL")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        status = try {
                            val ids = c.llm.listModels()
                            c.models.refresh()
                            tr("✓ Verbunden – {0} Modelle verfügbar", ids.size)
                        } catch (e: Exception) {
                            "✗ ${e.message}"
                        }
                        testing = false
                    }
                },
            ) { Text(tr("Verbindung testen")) }
            if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { uri.openUri("https://llm.scads.ai/status/") }) { Text(tr("Statusseite öffnen")) }
    }
    ApiProvidersSection(c)
}

@Composable
private fun GitHubSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Section(tr("GitHub · Hauptkonto")) {
        Text(
            tr("Mit einem Personal Access Token kann die App – wie ein Coding-Agent – in deinen Repositories lesen, " +
                "Dateien ändern, Branches und Pull Requests erstellen. Empfohlen: Fine-grained Token mit ") +
                tr("„Contents“, „Pull requests“ und „Issues“ (Read and write) für die gewünschten Repositories. " +
                "Für Repositories von Organisationen weiter unten je ein weiteres Token eintragen."),
            style = MaterialTheme.typography.bodySmall,
        )
        SecretField(tr("GitHub-Token"), s.githubToken) { v ->
            // A changed token has to be verified again before the GitHub options appear.
            c.settings.update { if (it.githubToken == v.trim()) it else it.copy(githubToken = v.trim(), githubUser = "") }
        }
        suspend fun check() {
            testing = true
            status = try {
                tr("✓ Angemeldet als {0}", c.github.verify())
            } catch (e: Exception) {
                "✗ ${e.message}"
            }
            testing = false
        }
        // Checks a newly entered token automatically, once typing has paused.
        LaunchedEffect(s.githubToken) {
            if (s.githubToken.isBlank()) {
                status = null
            } else if (s.githubUser.isBlank()) {
                delay(800)
                check()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !testing && s.githubToken.isNotBlank(),
                onClick = { scope.launch { check() } },
            ) { Text(tr("Token testen")) }
            if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        (status ?: s.githubUser.ifBlank { null }?.let { tr("✓ Angemeldet als {0}", it) })?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (!s.hasGitHub) {
            Text(
                tr("Die GitHub-Optionen im Chat und bei geplanten Aufgaben erscheinen, sobald ein gültiges Token hinterlegt ist " +
                    "(Hauptkonto oder ein weiteres Token)."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") }) {
            Text(tr("Token auf GitHub erstellen"))
        }
        if (s.hasGitHub) {
            SwitchRow(
                tr("In neuen Chats automatisch aktiv"),
                tr("Das Modell kann sofort auf GitHub zugreifen. Ein GitHub-Link in einer Nachricht aktiviert die Werkzeuge immer."),
                s.githubAutoTools,
                spot = "github.auto",
            ) { v -> c.settings.update { it.copy(githubAutoTools = v) } }
        }
    }
    GitHubAccountsSection(c)
    // Also covers writing web requests, so it stays visible without GitHub.
    Section(tr("Freigaben")) { ApprovalModeOptions(c) }
    ReliabilitySection(c)
}

@Composable
private fun ChatSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    var showModelPicker by remember { mutableStateOf(false) }
    Section(tr("Neue Chats")) {
        Spot("chat.defaultModel") {
            OutlinedButton(onClick = { showModelPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Standardmodell: {0}", ModelCatalog.info(s.defaultModel).displayName) + if (s.autoModel) tr(" · Automatisch") else "")
            }
        }
        SwitchRow(
            spot = "chat.autoModel",
            title = tr("Modell automatisch wählen"),
            subtitle =
            tr("Neue Chats starten mit „Automatisch“: Je Anfrage schlägt die App das passende Modell vor – Bilder und Alltag: " +
                "Standardmodell, Programmieren: Qwen, schwierige Analysen: GLM-5.3, sehr lange Dokumente: GLM-5.3 Flash."),
            checked = s.autoModel,
        ) { v -> c.settings.update { it.copy(autoModel = v) } }
        // Browser neben dem Chat auf Android (AP-63); Windows has the switch under Einstellungen › Windows.
        if (IS_ANDROID) SwitchRow(
            tr("Links in Lora öffnen"),
            tr("Links aus Chat, Literatur und Dokumenten öffnen sich in einem einfachen Browser in der App – mit Frage zur Seite, Zusammenfassung und Übernahme in Literatur oder Wissenssammlung. Aus: im normalen Browser."),
            s.browserPanel,
            spot = "chat.browser",
        ) { v -> c.settings.update { it.copy(browserPanel = v) } }
        if (s.autoModel) {
            SwitchRow(
                tr("Vor dem Wechsel fragen"),
                tr("Die App fragt jedes Mal, bevor sie ein anderes Modell nimmt. Aus: Sie wechselt selbst und sagt kurz, warum."),
                s.autoModelAsk,
                spot = "chat.autoModelAsk",
            ) { v -> c.settings.update { it.copy(autoModelAsk = v) } }
        }
        val thinking = ThinkingLevel.parse(s.thinkingLevel)
        Spot("chat.thinking") { DropdownField(
            label = tr("Denkstufe (Reasoning)"),
            value = thinking.label,
            options = ThinkingLevel.entries.map { it.label },
            onSelect = { v -> c.settings.update { it.copy(thinkingLevel = ThinkingLevel.entries.first { e -> e.label == v }.name) } },
        ) }
        Text(
            thinking.description + tr(" Gilt für Modelle mit Denkmodus (DeepSeek, GLM, Qwen, gpt-oss …). Unterstützt der Server " +
                "die Einstellung für ein Modell nicht, antwortet es mit seinem Standard."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Section(tr("Werkzeuge")) {
        Text(tr("Zusätzliche Fähigkeiten für Modelle mit Werkzeug-Unterstützung."), style = MaterialTheme.typography.bodySmall)
        SwitchRow(
            tr("Dokumente erstellen"),
            tr("PDF, Word, PowerPoint, OPAL-Tests, Briefe, E-Mail-Entwürfe und Termine – mit Gestaltung, Logo und Barrierefreiheit."),
            s.documentTools,
            spot = "chat.documents",
        ) { v -> c.settings.update { it.copy(documentTools = v) } }
        SwitchRow(
            spot = "chat.web",
            title = tr("Websuche, Webseiten & Quellen-Check"),
            subtitle =
            tr("Im Web und nach Nachrichten suchen, Links lesen und Literaturangaben bei Crossref prüfen."),
            checked = s.webTools,
        ) { v -> c.settings.update { it.copy(webTools = v) } }
    }
    Section(tr("Agent")) {
        DropdownField(
            label = tr("Max. Werkzeugschritte pro Anfrage"),
            value = s.maxAgentSteps.toString(),
            options = listOf("10", "20", "30", "50", "100"),
            onSelect = { v -> c.settings.update { it.copy(maxAgentSteps = v.toInt()) } },
        )
        SwitchRow(
            tr("Danach automatisch weitermachen"),
            tr("Der Agent arbeitet nach dem Limit ohne „weiter“ fort (höchstens {0} Schritte). ", AgentRunner.HARD_STEP_LIMIT) +
                tr("Wiederholt er immer dieselbe Aktion, hält er an."),
            s.autoContinue,
            spot = "chat.autoContinue",
        ) { v -> c.settings.update { it.copy(autoContinue = v) } }
        SwitchRow(
            spot = "chat.collapse",
            title = tr("Arbeitsschritte einklappen"),
            subtitle =
            tr("Gedankengang und Werkzeugschritte bleiben eingeklappt. Solange der Agent arbeitet, zeigt Lora, das Maskottchen der " +
                "App, den aktuellen Schritt; die Zahl daneben klappt alle Schritte jederzeit auf."),
            checked = s.collapseSteps,
        ) { v -> c.settings.update { it.copy(collapseSteps = v) } }
    }
    Spot("chat.approvals") { Section(tr("Freigaben")) { ApprovalModeOptions(c) } }
    ReliabilitySection(c)
    Section(tr("Erweitert")) {
        OutlinedTextField(
            value = s.systemPrompt,
            onValueChange = { v -> c.settings.update { it.copy(systemPrompt = v) } },
            label = { Text(tr("Globaler System-Prompt (optional)")) },
            supportingText = { Text(tr("Gilt in jedem Chat zusätzlich zu Persona und Projekt-Anweisungen.")) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(tr("Bild-Modell"), s.imageModel, models.ofType(ModelType.IMAGE).map { it.id }, { v -> c.settings.update { it.copy(imageModel = v) } })
    }
    if (showModelPicker) {
        ModelPickerSheet(
            models = models.ofType(ModelType.CHAT),
            selected = s.defaultModel,
            onSelect = { id -> c.settings.update { it.copy(defaultModel = id) } },
            onDismiss = { showModelPicker = false },
            defaultModel = s.defaultModel,
            title = tr("Standardmodell"),
            note = tr("Mit diesem Modell startet jeder neue Chat. Im Chat lässt es sich jederzeit wechseln."),
        )
    }
}

private val SERVER_VOICES = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

@Composable
private fun VoiceSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    val playing by c.speaker.playingId.collectAsStateWithLifecycle()
    var voices by remember { mutableStateOf<List<VoiceOption>?>(null) }
    LaunchedEffect(s.ttsEngine) {
        if (s.ttsEngine == "DEVICE" && voices == null) voices = c.speaker.deviceVoices()
    }
    Section(tr("Vorlesen")) {
        PlaceNote(c, io.github.thepytales.llmhub.data.Area.SPEECH)
        Text(
            tr("Wähle, wie Antworten vorgelesen werden. Beide Wege sind datenschutzfreundlich: Die KI-Stimme läuft auf dem ") +
                tr("Uni-Server, der die Antwort ohnehin geschrieben hat – es gehen keine Daten an Dritte. Die {0} ", DEVICE_VOICE) +
                tr("arbeitet komplett offline."),
            style = MaterialTheme.typography.bodySmall,
        )
        Spot("voice.engine") { Column { listOf(
            Triple("SERVER", tr("KI-Stimme (Uni-Server)"), tr("Sehr natürlich klingende neuronale Stimmen; braucht Internet.")),
            Triple("DEVICE", "$DEVICE_VOICE_TITLE (offline)", DEVICE_VOICE_DESCRIPTION),
            // AP-111: a natural German voice on this device.
            Triple("LOCAL", tr("Natürliche Stimme auf diesem Gerät"), tr("Deutsche Stimme „Thorsten“ (Piper), offline und ohne Server; einmal {0} MB laden.", io.github.thepytales.llmhub.audio.LocalVoiceInfo.MEGABYTES)),
        ).forEach { (key, label, desc) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { c.settings.update { it.copy(ttsEngine = key) } },
            ) {
                RadioButton(selected = s.ttsEngine == key, onClick = { c.settings.update { it.copy(ttsEngine = key) } })
                Column(Modifier.weight(1f)) {
                    Text(label)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } } }
        if (s.ttsEngine == "DEVICE") {
            when {
                voices == null -> CircularProgressIndicator(Modifier.size(24.dp))
                voices!!.isEmpty() -> Text(
                    NO_DEVICE_VOICES,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val list = voices!!
                    val current = list.firstOrNull { it.name == s.deviceVoice }?.label ?: tr("Automatisch (beste deutsche Stimme)")
                    DropdownField(tr("Stimme"), current, listOf(tr("Automatisch (beste deutsche Stimme)")) + list.map { it.label }, { label ->
                        c.settings.update { it.copy(deviceVoice = list.firstOrNull { v -> v.label == label }?.name.orEmpty()) }
                    })
                }
            }
        } else if (s.ttsEngine == "LOCAL") {
            LocalVoiceSettings(c)
        } else {
            DropdownField(tr("Sprachausgabe-Modell"), s.ttsModel, models.ofType(ModelType.TTS).map { it.id }, { v -> c.settings.update { it.copy(ttsModel = v) } })
            DropdownField(tr("Stimme"), s.ttsVoice, (SERVER_VOICES + s.ttsVoice).distinct(), { v -> c.settings.update { it.copy(ttsVoice = v) } })
        }
        val fastVoice = tr("Schnell: {0} (empfohlen)", ModelCatalog.info(ModelCatalog.FAST_CHAT).displayName)
        DropdownField(
            tr("Modell im Sprachmodus"), if (s.voiceModel.isBlank()) tr("Modell des Chats") else fastVoice, listOf(fastVoice, tr("Modell des Chats")),
            { v -> c.settings.update { it.copy(voiceModel = if (v == fastVoice) ModelCatalog.FAST_CHAT else "") } },
        )
        Text(tr("Tempo: {0}×", String.format(java.util.Locale.GERMANY, "%.1f", s.speechRate)), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = s.speechRate,
            onValueChange = { v -> c.settings.update { it.copy(speechRate = (v * 10).roundToInt() / 10f) } },
            valueRange = 0.6f..1.8f,
            steps = 11,
        )
        if (s.ttsEngine == "SERVER") {
            Text(tr("Das Tempo gilt für die {0}.", DEVICE_VOICE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = {
            c.speaker.toggle("preview", tr("Hallo! So klinge ich, wenn ich dir Antworten vorlese. Viel Spaß mit Lora."))
        }) {
            Icon(if (playing == "preview") Icons.Outlined.Stop else Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(if (playing == "preview") tr("Stopp") else tr("Probehören"))
        }
    }
    Section(tr("Spracheingabe")) {
        DropdownField(tr("Spracherkennungs-Modell"), s.sttModel, models.ofType(ModelType.STT).map { it.id }, { v -> c.settings.update { it.copy(sttModel = v) } })
    }
}

private val DOC_COLORS = listOf("#1E40AF", "#0284C7", "#0D9488", "#15803D", "#B45309", "#C2410C", "#B91C1C", "#BE185D", "#6D28D9", "#475569", "#111827")

private fun parseHex(hex: String): Color? = DocTheme.parseColor(hex)?.let { Color((0xFF000000 or it.removePrefix("#").toLong(16)).toInt()) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentsSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val logos by c.logos.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingLogo by remember { mutableStateOf<ByteArray?>(null) }
    var renameLogo by remember { mutableStateOf<Logo?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var hex by remember { mutableStateOf(s.docAccent) }
    val pickLogo = rememberImageBytesPicker { bytes -> pendingLogo = bytes }

    Section(tr("Gestaltung")) {
        Text(
            tr("So sehen PDFs und Word-Dateien aus, die das Modell erstellt oder die du exportierst. Das Modell kann für besondere " +
                "Wünsche (Flyer, Rechnung, Lebenslauf …) auch ein eigenes Design mit Farben, Formen und Schriften verwenden."),
            style = MaterialTheme.typography.bodySmall,
        )
        DocStyle.entries.forEach { st ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { c.settings.update { it.copy(docStyle = st.name) } },
            ) {
                RadioButton(selected = (DocStyle.parse(s.docStyle) ?: DocStyle.MODERN) == st, onClick = { c.settings.update { it.copy(docStyle = st.name) } })
                Column(Modifier.weight(1f)) {
                    Text(st.label)
                    Text(st.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(tr("Akzentfarbe"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DOC_COLORS.forEach { h ->
                val selected = s.docAccent.equals(h, ignoreCase = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(parseHex(h) ?: Color.Gray)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .a11yLabel(tr("Akzentfarbe") + " " + h)
                        .selectable(selected = selected, role = Role.RadioButton) { hex = h; c.settings.update { it.copy(docAccent = h) } },
                ) {
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                hex = v
                DocTheme.parseColor(v)?.let { col -> c.settings.update { it.copy(docAccent = col) } }
            },
            label = { Text(tr("Eigene Farbe (#RRGGBB)")) },
            singleLine = true,
            isError = DocTheme.parseColor(hex) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.docFooter,
            onValueChange = { v -> c.settings.update { it.copy(docFooter = v) } },
            label = { Text(tr("Fußzeile (optional)")) },
            placeholder = { Text(tr("z.B. Max Muster · Musterstraße 1 · 01069 Dresden")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.tudUnit,
            onValueChange = { v -> c.settings.update { it.copy(tudUnit = v) } },
            label = { Text(tr("Einrichtung für den Stil „TU Dresden“")) },
            supportingText = { Text(tr("Steht im Kopf unter dem Logo, eine Ebene pro Zeile (Fakultät, Institut, Professur).")) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spot("documents.sender") { OutlinedTextField(
            value = s.senderInfo,
            onValueChange = { v -> c.settings.update { it.copy(senderInfo = v) } },
            label = { Text(tr("Meine Absenderangaben")) },
            placeholder = { Text(tr("Dr. Erika Musterfrau\nWissenschaftliche Mitarbeiterin\nTelefon: +49 351 463-…\nE-Mail: …@tu-dresden.de\nRaum: MS1/…")) },
            supportingText = { Text(tr("Füllt das Feld „Absender“ in Brief- und E-Mail-Vorlagen automatisch aus.")) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        ) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = !previewBusy, onClick = {
                previewBusy = true
                scope.launch {
                    val result = runCatching {
                        DocumentWriter.create(
                            context, java.io.File(context.cacheDir, "exports"), "Beispieldokument", SAMPLE_DOC, DocFormat.PDF,
                            theme = c.documentTools.settingsTheme().let { t -> if (t.logo == null) t.copy(logo = c.logos.default?.let { c.logos.pngBytes(it) }) else t },
                        ).file
                    }
                    previewBusy = false
                    result.onSuccess { openDocument(context, it) }
                        .onFailure { Notices.error(tr("Vorschau fehlgeschlagen: {0}", it.message)) }
                }
            }) { Text(tr("Vorschau als PDF")) }
            if (previewBusy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
    }

    Section(tr("Logos")) {
        Text(
            tr("Gespeicherte Logos kann das Modell auf Befehl einbinden („mit meinem Logo“, „mit dem Logo Firma“). Das " +
                "Standardlogo (★) wird verwendet, wenn kein Name genannt wird."),
            style = MaterialTheme.typography.bodySmall,
        )
        SwitchRow(tr("Standardlogo automatisch einfügen"), tr("In jedes erstellte oder exportierte Dokument."), s.autoLogo, spot = "documents.logo") { v ->
            c.settings.update { it.copy(autoLogo = v) }
        }
        logos.forEach { logo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(logo.path) { runCatching { decodeImage(java.io.File(logo.path).readBytes()) }.getOrNull() }
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(width = 72.dp, height = 44.dp)) {
                    if (bmp != null) Image(bmp, contentDescription = logo.name, contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp))
                }
                Spacer(Modifier.size(12.dp))
                Text(logo.name, modifier = Modifier.weight(1f).clickable { renameLogo = logo }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { c.logos.setDefault(logo.id) }) {
                    Icon(if (logo.isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = tr("Als Standard"))
                }
                IconButton(onClick = { c.logos.remove(logo.id) }) { Icon(Icons.Outlined.Delete, contentDescription = tr("Löschen")) }
            }
        }
        OutlinedButton(onClick = pickLogo) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(tr("Logo hinzufügen"))
        }
        Text(
            tr("Tipp: PNG mit transparentem Hintergrund sieht am besten aus. Du kannst dem Modell auch ein Bild schicken und " +
                "„Speichere das als Logo“ sagen."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pendingLogo?.let { bytes ->
        TextInputDialog(tr("Logo speichern"), if (logos.isEmpty()) "Logo" else "", tr("Name"), onConfirm = { name ->
            runCatching { c.logos.add(name, bytes) }
                .onFailure { Notices.error(it.message ?: tr("Bild konnte nicht gelesen werden.")) }
        }, onDismiss = { pendingLogo = null })
    }
    renameLogo?.let { logo ->
        TextInputDialog(tr("Logo umbenennen"), logo.name, tr("Name"), onConfirm = { n -> c.logos.update(logo.id) { it.copy(name = n) } }, onDismiss = { renameLogo = null })
    }
}

private const val SAMPLE_DOC = """# Projektbericht

Dies ist ein **Beispieldokument**, das zeigt, wie Überschriften, Listen, Tabellen und Hinweisboxen aussehen.

## Zusammenfassung

- Ziel erreicht: *neue Funktionen* wurden umgesetzt
- Nächster Schritt: Feedback einholen

> [!TIP]
> Hinweisboxen hebt die App farbig hervor.

## Zahlen

| Quartal | Umsatz | Wachstum |
| --- | --- | --- |
| Q1 | 12.400 € | +4 % |
| Q2 | 15.100 € | +22 % |

> [!WARNING]
> Die Zahlen sind nur ein Beispiel.
"""
