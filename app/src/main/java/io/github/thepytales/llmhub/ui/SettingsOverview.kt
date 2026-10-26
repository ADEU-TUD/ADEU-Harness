package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.BuildConfig
import io.github.thepytales.llmhub.agent.ApprovalMode
import io.github.thepytales.llmhub.data.MemoryMode
import io.github.thepytales.llmhub.docs.DocStyle

/** One entry of the settings overview. */
data class SettingsItem(val route: String, val icon: ImageVector, val title: String, val subtitle: String)

/** Title of a settings page (shared by Android and Windows). */
fun settingsTitle(section: String?): String = tr(settingsTitleDe(section))

private fun settingsTitleDe(section: String?): String = when (section) {
    "api" -> "API-Zugang"
    "github" -> "GitHub"
    "chat" -> "Chat & Werkzeuge"
    "privacy" -> "Datenschutz"
    "appearance" -> "Darstellung"
    "voice" -> "Vorlesen & Spracheingabe"
    "documents" -> "Dokumente & Logos"
    "folders" -> "Lokale Ordner"
    "storage" -> "Speicherorte"
    "windows" -> "Windows"
    "memory" -> "Gedächtnis"
    "knowledge" -> "Wissenssammlung"
    "literature" -> "Literatur & Zotero"
    "backup" -> "Sicherung & Umzug"
    "trash" -> "Papierkorb"
    "transcription" -> "Transkription"
    "updates" -> "Updates"
    else -> "Einstellungen"
}

/**
 * Scrollable page whose content keeps a comfortable reading width on large windows (Windows, tablets) and fills
 * the screen on phones.
 */
@Composable
fun ReadableColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * Overview of the settings in groups: access, the assistant, documents and look, data. [deviceItem] is the entry
 * only one platform has (Windows: tray, autostart, data folder).
 */
@Composable
fun SettingsOverview(c: AppContainer, onNavigate: (String) -> Unit, deviceItem: SettingsItem? = null) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val envs by c.environments.items.collectAsStateWithLifecycle()
    val logos by c.logos.items.collectAsStateWithLifecycle()
    val memory by c.memory.items.collectAsStateWithLifecycle()
    val knowledge by c.knowledge.docs.collectAsStateWithLifecycle()

    ProfileCard(c)

    // Search over all settings pages (title and what can be set there).
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        placeholder = { Text(tr("Einstellungen durchsuchen")) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    if (query.isNotBlank()) {
        val hits = filterCommands(SETTINGS_INDEX + listOfNotNull(deviceItem?.let { Command(it.title, it.route, "Einstellungen", "windows autostart infobereich datenordner") }), query)
        if (hits.isEmpty()) {
            Text(tr("Keine passende Einstellung."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        } else {
            SettingsGroup("Treffer", hits.map { SettingsItem(it.route, Icons.Outlined.Tune, it.title, it.keywords) }, onNavigate)
        }
        return
    }

    SettingsGroup(
        "Zugang",
        listOf(
            SettingsItem("settings/api", Icons.Outlined.VpnKey, "API-Zugang", apiSummary(c)),
            SettingsItem("settings/github", Icons.Outlined.Code, "GitHub", gitHubSummary(c)),
            SettingsItem("environments", Icons.Outlined.Tune, "Umgebungen", if (envs.isEmpty()) "Variablen & Netzwerkzugriff für Agenten" else "${envs.size} Umgebung(en)"),
        ),
        onNavigate,
    )
    SettingsGroup(
        "Assistent",
        listOfNotNull(
            SettingsItem("settings/chat", Icons.Outlined.ChatBubbleOutline, "Chat & Werkzeuge", "Standardmodell, Werkzeuge · Freigaben: " + ApprovalMode.of(s.approvalMode).label),
            SettingsItem("settings/privacy", Icons.Outlined.Shield, "Datenschutz", if (s.pseudonymize) "Pseudonymisierung an" else "Pseudonymisierung aus"),
            SettingsItem("settings/memory", Icons.Outlined.Psychology, "Gedächtnis", MemoryMode.of(s.memoryMode).label + " · " + memory.size + " Einträge"),
            SettingsItem(
                "settings/knowledge", Icons.Outlined.LibraryBooks, "Wissenssammlung",
                knowledge.count { it.collection.isEmpty() }.let { if (it == 0) "Eigene Dokumente durchsuchbar machen" else "$it Dokument(e)" },
            ),
            SettingsItem(
                "settings/literature", Icons.Outlined.MenuBook, "Literatur & Zotero",
                (io.github.thepytales.llmhub.research.CitationStyle.parse(s.citationStyle)?.label ?: "APA 7") + if (s.zoteroKey.isNotBlank()) " · Zotero verbunden" else "",
            ),
            SettingsItem(
                "settings/folders", Icons.Outlined.Folder, "Lokale Ordner",
                c.localFolders.current.size.let { if (it == 0) "Agent in Ordnern auf diesem PC arbeiten lassen" else "$it Ordner freigegeben" },
            ).takeIf { FolderAccess.SUPPORTED },
        ),
        onNavigate,
    )
    SettingsGroup(
        "Modelle",
        listOf(
            SettingsItem("models", Icons.Outlined.AutoAwesome, "Modellübersicht", "Alle Modelle des Uni-Servers mit Stärken und Einsatzzweck"),
            SettingsItem("compare", Icons.Outlined.CompareArrows, "Modelle vergleichen", "Zwei Modelle beantworten dieselbe Frage nebeneinander"),
            SettingsItem("local-models", Icons.Outlined.Memory, "Modelle auf diesem Gerät", localSummary(c)),
            SettingsItem("settings/transcription", Icons.Outlined.GraphicEq, "Transkription", transcriptionSummary(c)),
        ),
        onNavigate,
    )
    SettingsGroup(
        "Dokumente & Darstellung",
        listOfNotNull(
            SettingsItem(
                "settings/documents", Icons.Outlined.Description, "Dokumente & Logos",
                (DocStyle.parse(s.docStyle)?.label ?: "Modern") + (if (logos.isEmpty()) "" else " · ${logos.size} Logo(s)") +
                    if (s.senderInfo.isBlank()) " · Absender fehlt" else "",
            ),
            SettingsItem("settings/voice", Icons.AutoMirrored.Outlined.VolumeUp, "Vorlesen & Spracheingabe", if (s.ttsEngine == "DEVICE") "Offline auf dem Gerät" else "KI-Stimme des Uni-Servers"),
            SettingsItem("settings/appearance", Icons.Outlined.Palette, "Darstellung", enumOr(s.appStyle, AppStyle.ADEU).label + " · " + enumOr(s.themeMode, ThemeMode.SYSTEM).label),
            deviceItem,
        ),
        onNavigate,
    )
    SettingsGroup(
        "Daten",
        listOfNotNull(
            SettingsItem("usage", Icons.Outlined.Insights, "Verbrauch", "Anfragen und Tokens pro Tag"),
            SettingsItem(
                "settings/storage", Icons.Outlined.FolderOpen, "Speicherorte",
                c.docs.vaults.value.size.let { if (it == 0) "Eigene Ordner für Dokumente, z. B. in Google Drive" else "$it Ablageort(e)" },
            ).takeIf { FolderAccess.SUPPORTED },
            SettingsItem("settings/backup", Icons.Outlined.SettingsBackupRestore, "Sicherung & Umzug", "Alles exportieren und auf einem anderen Gerät importieren"),
            SettingsItem("settings/trash", Icons.Outlined.DeleteOutline, "Papierkorb", "Gelöschte Chats und Transkripte wiederherstellen"),
            SettingsItem("settings/updates", Icons.Outlined.SystemUpdate, "Updates", updateSummary()),
        ),
        onNavigate,
    )
    Text(
        "ADEU LLM ${BuildConfig.VERSION_NAME} · Inoffizielle App, nicht mit der Universität verbunden.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Version and what the last update check found, for the settings overview. */
@Composable
private fun updateSummary(): String {
    val state by io.github.thepytales.llmhub.update.Updates.state.collectAsStateWithLifecycle()
    val version = "Version ${BuildConfig.VERSION_NAME}"
    return when (val s = state) {
        is io.github.thepytales.llmhub.update.Updates.State.Available -> "$version · Neue Version ${s.info.version} verfügbar"
        is io.github.thepytales.llmhub.update.Updates.State.Ready -> "$version · ${s.info.version} bereit zum Installieren"
        io.github.thepytales.llmhub.update.Updates.State.UpToDate -> "$version · aktuell"
        else -> "$version · automatisch beim Start"
    }
}

/** Avatar and first name: used for the greeting and the profile in the side menu. */
@Composable
private fun ProfileCard(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val look = LocalChatLook.current
    Surface(shape = look.cardShape, color = look.card, border = look.cardBorder) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = look.brand, contentColor = Color.White, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = s.userName.trim().firstOrNull()?.uppercaseChar()
                    if (initial != null) Text(initial.toString(), style = MaterialTheme.typography.titleLarge)
                    else Icon(Icons.Outlined.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.size(16.dp))
            OutlinedTextField(
                value = s.userName,
                onValueChange = { v -> c.settings.update { it.copy(userName = v) } },
                label = { Text("Vorname (optional)") },
                supportingText = { Text("Für die Begrüßung und das Profil in der Seitenleiste.") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, entries: List<SettingsItem>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupLabel(tr(title), Modifier.padding(start = 8.dp))
        val look = LocalChatLook.current
        Surface(shape = look.cardShape, color = look.card, border = look.cardBorder) {
            Column {
                entries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onNavigate(e.route) },
                        leadingContent = { Icon(e.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(tr(e.title)) },
                        supportingContent = { Text(e.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** Subtitle of "Transkription" in the overview. */
internal fun transcriptionSummary(c: AppContainer): String {
    val t = c.transcription
    val s = t.settings.value
    val where = if (s.mode == "SERVER") "Uni-Server" else "Auf diesem Gerät"
    val n = t.state.value.models.count { it.installed }
    return where + if (n > 0) " · $n Modell(e) geladen" else ""
}

/** Subtitle of "Modelle auf diesem Gerät" in the overview. */
internal fun localSummary(c: AppContainer): String {
    val n = c.localLlm.state.value.installed.size
    return if (n == 0) "Sprachmodelle offline – für vertrauliche Daten" else "$n Modell(e) installiert · offline"
}
