package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.data.ModelType
import kotlinx.coroutines.launch

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
    Card(Modifier.fillMaxWidth()) {
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
    val style = enumOr(s.appStyle, AppStyle.STANDARD)
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
            if (style == AppStyle.STANDARD) "„Automatisch“ übernimmt ab Android 12 die Farben deines Hintergrundbilds."
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var llmStatus by remember { mutableStateOf<String?>(null) }
    var ghStatus by remember { mutableStateOf<String?>(null) }
    var testingLlm by remember { mutableStateOf(false) }
    var testingGh by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Einstellungen") }) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("LLM-Zugang") {
                Text(
                    "API-Key aus dem Self-Service-Portal deiner Uni (beginnt mit „sk-“). Er wird verschlüsselt auf dem Gerät gespeichert.",
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
                        enabled = !testingLlm,
                        onClick = {
                            testingLlm = true
                            scope.launch {
                                llmStatus = try {
                                    val ids = c.llm.listModels()
                                    c.models.refresh()
                                    "✓ Verbunden – ${ids.size} Modelle verfügbar"
                                } catch (e: Exception) {
                                    "✗ ${e.message}"
                                }
                                testingLlm = false
                            }
                        },
                    ) { Text("Verbindung testen") }
                    if (testingLlm) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
                }
                llmStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { uri.openUri("https://llm.scads.ai/status/") }) { Text("Statusseite öffnen") }
            }

            Section("GitHub") {
                Text(
                    "Mit einem Personal Access Token kann die App – wie ein Coding-Agent – in deinen Repositories lesen, " +
                        "Dateien ändern, Branches und Pull Requests erstellen. Empfohlen: Fine-grained Token mit " +
                        "„Contents“, „Pull requests“ und „Issues“ (Read and write) für die gewünschten Repositories.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SecretField("GitHub-Token", s.githubToken) { v -> c.settings.update { it.copy(githubToken = v.trim()) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !testingGh,
                        onClick = {
                            testingGh = true
                            scope.launch {
                                ghStatus = try {
                                    "✓ Angemeldet als ${c.github.me()}"
                                } catch (e: Exception) {
                                    "✗ ${e.message}"
                                }
                                testingGh = false
                            }
                        },
                    ) { Text("Token testen") }
                    if (testingGh) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
                }
                ghStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") }) {
                    Text("Token auf GitHub erstellen")
                }
                SwitchRow(
                    "In neuen Chats automatisch aktiv",
                    "Das Modell kann sofort auf GitHub zugreifen. Ein GitHub-Link in einer Nachricht aktiviert die Werkzeuge immer.",
                    s.githubAutoTools,
                ) { v -> c.settings.update { it.copy(githubAutoTools = v) } }
                SwitchRow(
                    "Schreibaktionen bestätigen",
                    "Vor jedem Commit, Branch, PR oder Kommentar nachfragen.",
                    s.confirmWrites,
                ) { v -> c.settings.update { it.copy(confirmWrites = v) } }
                DropdownField(
                    label = "Max. Werkzeugschritte pro Anfrage",
                    value = s.maxAgentSteps.toString(),
                    options = listOf("10", "20", "30", "50", "100"),
                    onSelect = { v -> c.settings.update { it.copy(maxAgentSteps = v.toInt()) } },
                )
            }

            AppearanceSection(c)

            Section("Werkzeuge") {
                Text(
                    "Zusätzliche Fähigkeiten für Modelle mit Werkzeug-Unterstützung.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SwitchRow(
                    "Dokumente erstellen",
                    "Das Modell kann PDF-, Word-, HTML-, Markdown- und Textdateien erzeugen, die du öffnen, teilen oder speichern kannst.",
                    s.documentTools,
                ) { v -> c.settings.update { it.copy(documentTools = v) } }
                SwitchRow(
                    "Webseiten lesen",
                    "Das Modell kann Links abrufen und den Text der Seite lesen.",
                    s.webTools,
                ) { v -> c.settings.update { it.copy(webTools = v) } }
            }

            Section("Chat") {
                OutlinedButton(onClick = { showModelPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Standardmodell: ${s.defaultModel}")
                }
                OutlinedTextField(
                    value = s.systemPrompt,
                    onValueChange = { v -> c.settings.update { it.copy(systemPrompt = v) } },
                    label = { Text("Globaler System-Prompt (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Audio & Bilder") {
                val tts = models.ofType(ModelType.TTS).map { it.id }
                val stt = models.ofType(ModelType.STT).map { it.id }
                val img = models.ofType(ModelType.IMAGE).map { it.id }
                DropdownField("Sprachausgabe-Modell", s.ttsModel, tts, { v -> c.settings.update { it.copy(ttsModel = v) } })
                OutlinedTextField(
                    value = s.ttsVoice,
                    onValueChange = { v -> c.settings.update { it.copy(ttsVoice = v.trim()) } },
                    label = { Text("Stimme (z.B. alloy, nova, echo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField("Spracherkennungs-Modell", s.sttModel, stt, { v -> c.settings.update { it.copy(sttModel = v) } })
                DropdownField("Bild-Modell", s.imageModel, img, { v -> c.settings.update { it.copy(imageModel = v) } })
            }

            HorizontalDivider()
            Text(
                "LLM Hub ${BuildConfig.VERSION_NAME} · Inoffizielle App, nicht mit der Universität verbunden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = models.ofType(ModelType.CHAT),
            selected = s.defaultModel,
            onSelect = { id -> c.settings.update { it.copy(defaultModel = id) } },
            onDismiss = { showModelPicker = false },
        )
    }
}
