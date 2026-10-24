package io.github.thepytales.llmhub.ui

import android.widget.Toast
import io.github.thepytales.llmhub.desktop.Autostart
import io.github.thepytales.llmhub.desktop.DesktopActions
import io.github.thepytales.llmhub.desktop.Paths
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.ApprovalMode
import io.github.thepytales.llmhub.BuildConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Verbergen" else "Anzeigen",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = LocalChatLook.current.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val fill = accent.color?.let { Brush.linearGradient(listOf(it, it)) }
        ?: Brush.sweepGradient(listOf(Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFFDB2777), Color(0xFF2563EB)))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(fill)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        ) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(accent.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val style = enumOr(s.appStyle, AppStyle.CLAUDE)
    val accent = enumOr(s.accent, Accent.AUTO)
    val mode = enumOr(s.themeMode, ThemeMode.SYSTEM)
    Section("Darstellung") {
        Text("Stil", style = MaterialTheme.typography.labelLarge)
        AppStyle.entries.forEach { st ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { c.settings.update { it.copy(appStyle = st.name) } },
            ) {
                RadioButton(selected = style == st, onClick = { c.settings.update { it.copy(appStyle = st.name) } })
                Column(Modifier.weight(1f)) {
                    Text(st.label)
                    Text(st.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Farbton", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Accent.entries.forEach { a ->
                AccentSwatch(a, a == accent) { c.settings.update { it.copy(accent = a.name) } }
            }
        }
        Text(
            if (style == AppStyle.STANDARD) "„Automatisch“ nutzt das Blau von Material Design."
            else "„Automatisch“ verwendet die typischen Farben des gewählten Stils.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Modus", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { c.settings.update { it.copy(themeMode = m.name) } },
                    label = { Text(m.label) },
                )
            }
        }
        SwitchRow("Reines Schwarz (AMOLED)", "Schwarzer Hintergrund im dunklen Modus – spart Akku auf OLED-Displays.", s.amoled) { v ->
            c.settings.update { it.copy(amoled = v) }
        }
        Text("Schriftgröße", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.9f to "Klein", 1f to "Normal", 1.15f to "Groß", 1.3f to "Sehr groß").forEach { (scale, label) ->
                FilterChip(
                    selected = s.textScale == scale,
                    onClick = { c.settings.update { it.copy(textScale = scale) } },
                    label = { Text(label) },
                )
            }
        }
    }
}

private data class SettingsEntry(val route: String, val icon: ImageVector, val title: String, val subtitle: String)

/** Settings as a clear list of sections (like the ChatGPT app); [section] opens one of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(c: AppContainer, section: String?, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val envs by c.environments.items.collectAsStateWithLifecycle()
    val logos by c.logos.items.collectAsStateWithLifecycle()
    val title = when (section) {
        "api" -> "API-Zugang"
        "github" -> "GitHub"
        "chat" -> "Chat & Werkzeuge"
        "appearance" -> "Darstellung"
        "voice" -> "Vorlesen & Spracheingabe"
        "documents" -> "Dokumente & Logos"
        "windows" -> "Windows"
        else -> "Einstellungen"
    }
    Scaffold(topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text(title) }) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
                "api" -> ApiSection(c)
                "github" -> GitHubSection(c)
                "chat" -> ChatSection(c)
                "appearance" -> AppearanceSection(c)
                "voice" -> VoiceSection(c)
                "documents" -> DocumentsSection(c)
                "windows" -> WindowsSection(c)
                else -> {
                    val entries = listOf(
                        SettingsEntry("settings/api", Icons.Outlined.VpnKey, "API-Zugang", if (s.hasApiKey) "API-Key hinterlegt" else "API-Key fehlt"),
                        SettingsEntry("settings/github", Icons.Outlined.Code, "GitHub", if (s.hasGitHub) "Angemeldet als ${s.githubUser}" else if (s.githubToken.isNotBlank()) "Token ungültig oder nicht geprüft" else "Kein Token"),
                        SettingsEntry("environments", Icons.Outlined.Tune, "Umgebungen", if (envs.isEmpty()) "Variablen & Netzwerkzugriff für Agenten" else "${envs.size} Umgebung(en)"),
                        SettingsEntry("settings/chat", Icons.Outlined.ChatBubbleOutline, "Chat & Werkzeuge", "Standardmodell, Werkzeuge, Freigaben: " + ApprovalMode.of(s.approvalMode).label),
                        SettingsEntry("settings/documents", Icons.Outlined.Description, "Dokumente & Logos", if (logos.isEmpty()) "PDF-Gestaltung, Logos" else "${logos.size} Logo(s) · ${DocStyle.parse(s.docStyle)?.label ?: "Modern"}"),
                        SettingsEntry("settings/voice", Icons.AutoMirrored.Outlined.VolumeUp, "Vorlesen & Spracheingabe", if (s.ttsEngine == "DEVICE") "Offline mit Windows-Stimme" else "KI-Stimme des Uni-Servers"),
                        SettingsEntry("settings/windows", Icons.Outlined.Computer, "Windows", "Infobereich, Autostart, Datenordner"),
                        SettingsEntry("settings/appearance", Icons.Outlined.Palette, "Darstellung", enumOr(s.appStyle, AppStyle.CLAUDE).label + " · " + enumOr(s.themeMode, ThemeMode.SYSTEM).label),
                        SettingsEntry("templates", Icons.Outlined.Bookmarks, "Vorlagen", "Lehre, Forschung, Betreuung, TUD-Dokumente, Personas"),
                        SettingsEntry("tasks", Icons.Outlined.Schedule, "Geplante Aufgaben", "Wiederkehrende Aufträge an den Agenten"),
                        SettingsEntry("usage", Icons.Outlined.Insights, "Verbrauch", "Anfragen und Tokens pro Tag"),
                    )
                    OutlinedTextField(
                        value = s.userName,
                        onValueChange = { v -> c.settings.update { it.copy(userName = v) } },
                        label = { Text("Dein Vorname (optional)") },
                        supportingText = { Text("Für die Begrüßung im neuen Chat und das Profilbild im Menü.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Surface(shape = RoundedCornerShape(20.dp), color = LocalChatLook.current.card) {
                        Column {
                            entries.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable { onNavigate(e.route) },
                                    leadingContent = { Icon(e.icon, contentDescription = null) },
                                    headlineContent = { Text(e.title) },
                                    supportingContent = { Text(e.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                                )
                            }
                        }
                    }
                    Text(
                        "ADEU LLM ${BuildConfig.VERSION_NAME} · Inoffizielle App, nicht mit der Universität verbunden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
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
    Section("LLM-Zugang") {
        Text(
            "API-Key aus dem Self-Service-Portal deiner Uni (beginnt mit „sk-“). Er wird verschlüsselt auf diesem PC gespeichert (nur dein Windows-Konto kann ihn lesen).",
            style = MaterialTheme.typography.bodySmall,
        )
        SecretField("API-Key", s.apiKey) { v -> c.settings.update { it.copy(apiKey = v.trim()) } }
        OutlinedTextField(
            value = s.baseUrl,
            onValueChange = { v -> c.settings.update { it.copy(baseUrl = v.trim()) } },
            label = { Text("API-Basis-URL") },
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
                            "✓ Verbunden – ${ids.size} Modelle verfügbar"
                        } catch (e: Exception) {
                            "✗ ${e.message}"
                        }
                        testing = false
                    }
                },
            ) { Text("Verbindung testen") }
            if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { uri.openUri("https://llm.scads.ai/status/") }) { Text("Statusseite öffnen") }
    }
}

@Composable
private fun GitHubSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    Section("GitHub") {
        Text(
            "Mit einem Personal Access Token kann die App – wie ein Coding-Agent – in deinen Repositories lesen, " +
                "Dateien ändern, Branches und Pull Requests erstellen. Empfohlen: Fine-grained Token mit " +
                "„Contents“, „Pull requests“ und „Issues“ (Read and write) für die gewünschten Repositories.",
            style = MaterialTheme.typography.bodySmall,
        )
        SecretField("GitHub-Token", s.githubToken) { v ->
            // A changed token has to be verified again before the GitHub options appear.
            c.settings.update { if (it.githubToken == v.trim()) it else it.copy(githubToken = v.trim(), githubUser = "") }
        }
        suspend fun check() {
            testing = true
            status = try {
                "✓ Angemeldet als ${c.github.verify()}"
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
            ) { Text("Token testen") }
            if (testing) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        (status ?: s.githubUser.ifBlank { null }?.let { "✓ Angemeldet als $it" })?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (!s.hasGitHub) {
            Text(
                "Die GitHub-Optionen im Chat und bei geplanten Aufgaben erscheinen, sobald ein gültiges Token hinterlegt ist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") }) {
            Text("Token auf GitHub erstellen")
        }
        if (s.hasGitHub) {
            SwitchRow(
                "In neuen Chats automatisch aktiv",
                "Das Modell kann sofort auf GitHub zugreifen. Ein GitHub-Link in einer Nachricht aktiviert die Werkzeuge immer.",
                s.githubAutoTools,
            ) { v -> c.settings.update { it.copy(githubAutoTools = v) } }
        }
    }
    // Also covers writing web requests, so it stays visible without GitHub.
    Section("Freigaben") { ApprovalModeOptions(c) }
    ReliabilitySection(c)
}

@Composable
private fun ChatSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    var showModelPicker by remember { mutableStateOf(false) }
    Section("Chat") {
        OutlinedButton(onClick = { showModelPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Standardmodell: ${ModelCatalog.info(s.defaultModel).displayName}")
        }
        OutlinedTextField(
            value = s.systemPrompt,
            onValueChange = { v -> c.settings.update { it.copy(systemPrompt = v) } },
            label = { Text("Globaler System-Prompt (optional)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Max. Werkzeugschritte pro Anfrage",
            value = s.maxAgentSteps.toString(),
            options = listOf("10", "20", "30", "50", "100"),
            onSelect = { v -> c.settings.update { it.copy(maxAgentSteps = v.toInt()) } },
        )
        SwitchRow(
            "Danach automatisch weitermachen",
            "Der Agent arbeitet nach dem Limit ohne „weiter“ fort (höchstens ${AgentRunner.HARD_STEP_LIMIT} Schritte). " +
                "Wiederholt er immer dieselbe Aktion, hält er an.",
            s.autoContinue,
        ) { v -> c.settings.update { it.copy(autoContinue = v) } }
        val thinking = ThinkingLevel.parse(s.thinkingLevel)
        DropdownField(
            label = "Denkstufe (Reasoning)",
            value = thinking.label,
            options = ThinkingLevel.entries.map { it.label },
            onSelect = { v -> c.settings.update { it.copy(thinkingLevel = ThinkingLevel.entries.first { e -> e.label == v }.name) } },
        )
        Text(
            thinking.description + " Gilt für Modelle mit Denkmodus (DeepSeek, GLM, Qwen, gpt-oss …). Unterstützt der Server " +
                "die Einstellung für ein Modell nicht, antwortet es mit seinem Standard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownField("Bild-Modell", s.imageModel, models.ofType(ModelType.IMAGE).map { it.id }, { v -> c.settings.update { it.copy(imageModel = v) } })
    }
    Section("Werkzeuge") {
        Text("Zusätzliche Fähigkeiten für Modelle mit Werkzeug-Unterstützung.", style = MaterialTheme.typography.bodySmall)
        SwitchRow(
            "Dokumente erstellen",
            "PDF-, Word-, HTML-, Markdown- und Textdateien – mit Gestaltung und Logo.",
            s.documentTools,
        ) { v -> c.settings.update { it.copy(documentTools = v) } }
        SwitchRow(
            "Websuche & Webseiten",
            "Im Web und nach Nachrichten suchen, Links abrufen und lesen.",
            s.webTools,
        ) { v -> c.settings.update { it.copy(webTools = v) } }
    }
    Section("Freigaben") { ApprovalModeOptions(c) }
    ReliabilitySection(c)
    if (showModelPicker) {
        ModelPickerSheet(
            models = models.ofType(ModelType.CHAT),
            selected = s.defaultModel,
            onSelect = { id -> c.settings.update { it.copy(defaultModel = id) } },
            onDismiss = { showModelPicker = false },
            defaultModel = s.defaultModel,
            title = "Standardmodell",
            note = "Mit diesem Modell startet jeder neue Chat. Im Chat lässt es sich jederzeit wechseln.",
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
    Section("Vorlesen") {
        Text(
            "Wähle, wie Antworten vorgelesen werden. Beide Wege sind datenschutzfreundlich: Die KI-Stimme läuft auf dem " +
                "Uni-Server, der die Antwort ohnehin geschrieben hat – es gehen keine Daten an Dritte. Die Windows-Stimme " +
                "arbeitet komplett offline.",
            style = MaterialTheme.typography.bodySmall,
        )
        listOf(
            Triple("SERVER", "KI-Stimme (Uni-Server)", "Sehr natürlich klingende neuronale Stimmen; braucht Internet."),
            Triple("DEVICE", "Windows-Stimme (offline)", "Sprachausgabe von Windows – nichts verlässt den PC."),
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
        }
        if (s.ttsEngine == "DEVICE") {
            when {
                voices == null -> CircularProgressIndicator(Modifier.size(24.dp))
                voices!!.isEmpty() -> Text(
                    "Keine Windows-Stimmen gefunden. Unter Windows-Einstellungen › Zeit und Sprache › Sprache lässt sich eine deutsche Stimme hinzufügen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val list = voices!!
                    val current = list.firstOrNull { it.name == s.deviceVoice }?.label ?: "Automatisch (beste deutsche Stimme)"
                    DropdownField("Stimme", current, listOf("Automatisch (beste deutsche Stimme)") + list.map { it.label }, { label ->
                        c.settings.update { it.copy(deviceVoice = list.firstOrNull { v -> v.label == label }?.name.orEmpty()) }
                    })
                }
            }
        } else {
            DropdownField("Sprachausgabe-Modell", s.ttsModel, models.ofType(ModelType.TTS).map { it.id }, { v -> c.settings.update { it.copy(ttsModel = v) } })
            DropdownField("Stimme", s.ttsVoice, (SERVER_VOICES + s.ttsVoice).distinct(), { v -> c.settings.update { it.copy(ttsVoice = v) } })
        }
        Text("Tempo: ${String.format(java.util.Locale.GERMANY, "%.1f", s.speechRate)}×", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = s.speechRate,
            onValueChange = { v -> c.settings.update { it.copy(speechRate = (v * 10).roundToInt() / 10f) } },
            valueRange = 0.6f..1.8f,
            steps = 11,
        )
        if (s.ttsEngine == "SERVER") {
            Text("Das Tempo gilt für die Windows-Stimme.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = {
            c.speaker.toggle("preview", "Hallo! So klinge ich, wenn ich dir Antworten vorlese. Viel Spaß mit ADEU LLM.")
        }) {
            Icon(if (playing == "preview") Icons.Outlined.Stop else Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(if (playing == "preview") "Stopp" else "Probehören")
        }
    }
    Section("Spracheingabe") {
        DropdownField("Spracherkennungs-Modell", s.sttModel, models.ofType(ModelType.STT).map { it.id }, { v -> c.settings.update { it.copy(sttModel = v) } })
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
    fun pickLogo() = scope.launch {
        pendingLogo = withContext(Dispatchers.IO) {
            DesktopActions.pickFiles("Logo auswählen", multiple = false, extensions = listOf("png", "jpg", "jpeg", "gif", "bmp"))
                .firstOrNull()?.let { runCatching { it.readBytes() }.getOrNull() }
        }
    }

    Section("Gestaltung") {
        Text(
            "So sehen PDFs und Word-Dateien aus, die das Modell erstellt oder die du exportierst. Das Modell kann für besondere " +
                "Wünsche (Flyer, Rechnung, Lebenslauf …) auch ein eigenes Design mit Farben, Formen und Schriften verwenden.",
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
        Text("Akzentfarbe", style = MaterialTheme.typography.labelLarge)
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
                        .clickable { hex = h; c.settings.update { it.copy(docAccent = h) } },
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
            label = { Text("Eigene Farbe (#RRGGBB)") },
            singleLine = true,
            isError = DocTheme.parseColor(hex) == null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.docFooter,
            onValueChange = { v -> c.settings.update { it.copy(docFooter = v) } },
            label = { Text("Fußzeile (optional)") },
            placeholder = { Text("z.B. Max Muster · Musterstraße 1 · 01069 Dresden") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.tudUnit,
            onValueChange = { v -> c.settings.update { it.copy(tudUnit = v) } },
            label = { Text("Einrichtung für den Stil „TU Dresden“") },
            supportingText = { Text("Steht im Kopf unter dem Logo, eine Ebene pro Zeile (Fakultät, Institut, Professur).") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.senderInfo,
            onValueChange = { v -> c.settings.update { it.copy(senderInfo = v) } },
            label = { Text("Meine Absenderangaben") },
            placeholder = { Text("Dr. Erika Musterfrau\nWissenschaftliche Mitarbeiterin\nTelefon: +49 351 463-…\nE-Mail: …@tu-dresden.de\nRaum: MS1/…") },
            supportingText = { Text("Füllt das Feld „Absender“ in Brief- und E-Mail-Vorlagen automatisch aus.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
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
                        .onFailure { Toast.makeText(context, "Vorschau fehlgeschlagen: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Vorschau als PDF") }
            if (previewBusy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
    }

    Section("Logos") {
        Text(
            "Gespeicherte Logos kann das Modell auf Befehl einbinden („mit meinem Logo“, „mit dem Logo Firma“). Das " +
                "Standardlogo (★) wird verwendet, wenn kein Name genannt wird.",
            style = MaterialTheme.typography.bodySmall,
        )
        SwitchRow("Standardlogo automatisch einfügen", "In jedes erstellte oder exportierte Dokument.", s.autoLogo) { v ->
            c.settings.update { it.copy(autoLogo = v) }
        }
        logos.forEach { logo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bmp = remember(logo.path) { runCatching { org.jetbrains.skia.Image.makeFromEncoded(java.io.File(logo.path).readBytes()).toComposeImageBitmap() }.getOrNull() }
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(width = 72.dp, height = 44.dp)) {
                    if (bmp != null) Image(bmp, contentDescription = logo.name, contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp))
                }
                Spacer(Modifier.size(12.dp))
                Text(logo.name, modifier = Modifier.weight(1f).clickable { renameLogo = logo }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { c.logos.setDefault(logo.id) }) {
                    Icon(if (logo.isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = "Als Standard")
                }
                IconButton(onClick = { c.logos.remove(logo.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Löschen") }
            }
        }
        OutlinedButton(onClick = { pickLogo() }) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Logo hinzufügen")
        }
        Text(
            "Tipp: PNG mit transparentem Hintergrund sieht am besten aus. Du kannst dem Modell auch ein Bild schicken und " +
                "„Speichere das als Logo“ sagen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pendingLogo?.let { bytes ->
        TextInputDialog("Logo speichern", if (logos.isEmpty()) "Logo" else "", "Name", onConfirm = { name ->
            runCatching { c.logos.add(name, bytes) }
                .onFailure { Toast.makeText(context, it.message ?: "Bild konnte nicht gelesen werden.", Toast.LENGTH_LONG).show() }
        }, onDismiss = { pendingLogo = null })
    }
    renameLogo?.let { logo ->
        TextInputDialog("Logo umbenennen", logo.name, "Name", onConfirm = { n -> c.logos.update(logo.id) { it.copy(name = n) } }, onDismiss = { renameLogo = null })
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

/** Windows-specific behavior: notification area, autostart and where the data lives. */
@Composable
private fun WindowsSection(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var autostart by remember { mutableStateOf(Autostart.enabled) }
    Section("Im Hintergrund") {
        SwitchRow(
            "Beim Schließen im Infobereich weiterlaufen",
            "Antworten werden fertig geschrieben und geplante Aufgaben laufen weiter, auch wenn das Fenster zu ist. " +
                "Öffnen und Beenden über das Symbol unten rechts in der Taskleiste.",
            s.closeToTray,
        ) { v -> c.settings.update { it.copy(closeToTray = v) } }
        SwitchRow(
            "Mit Windows starten",
            if (Autostart.supported) "Startet ADEU LLM nach der Anmeldung unsichtbar im Infobereich – für geplante Aufgaben. Keine Admin-Rechte nötig."
            else "Nur in der Windows-Version verfügbar.",
            autostart,
        ) { v ->
            if (Autostart.supported) {
                Autostart.set(v)
                autostart = Autostart.enabled
            }
        }
    }
    Section("Daten") {
        Text(
            "Chats, Einstellungen und Dokumente liegen in deinem Windows-Profil. Sie bleiben erhalten, wenn du eine neue " +
                "Version startest. API-Key, Tokens und Umgebungen sind mit Windows verschlüsselt (nur dein Konto kann sie lesen).",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(Paths.data.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { DesktopActions.open(Paths.data) }) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Datenordner öffnen")
        }
    }
}
