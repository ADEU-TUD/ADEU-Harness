package io.github.thepytales.llmhub.ui

import androidx.compose.material.icons.outlined.BugReport
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
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Speed
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
    "chat" -> tr("Chat & Werkzeuge")
    "tools" -> "Werkzeuge"
    "lora" -> "Lora"
    "ram" -> "Arbeitsspeicher"
    "privacy" -> "Datenschutz"
    "appearance" -> "Darstellung"
    "voice" -> tr("Vorlesen & Spracheingabe")
    "documents" -> tr("Dokumente & Logos")
    "folders" -> tr("Lokale Ordner")
    "storage" -> "Speicherorte"
    "windows" -> "Windows"
    "sync" -> "Synchronisation"
    "connections" -> "Verbindungen"
    "memory" -> tr("Gedächtnis")
    "knowledge" -> "Wissenssammlung"
    "literature" -> tr("Literatur & Zotero")
    "backup" -> tr("Sicherung & Umzug")
    "trash" -> "Papierkorb"
    "transcription" -> "Transkription"
    "updates" -> "Updates"
    "help" -> tr("Problem melden")
    else -> tr("Einstellungen")
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
        val hits = filterCommands(SETTINGS_INDEX + listOfNotNull(
            deviceItem?.let { Command(it.title, it.route, trLater("Einstellungen"), "windows autostart infobereich datenordner") },
            syncSettingsItem()?.let { Command(it.title, it.route, trLater("Einstellungen"), "sync synchronisieren cloud onedrive google drive nextcloud zweiter pc laptop") },
        ), query)
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
            SettingsItem("settings/api", Icons.Outlined.VpnKey, tr("API-Zugang"), apiSummary(c)),
            SettingsItem("settings/github", Icons.Outlined.Code, "GitHub", gitHubSummary(c)),
            SettingsItem("environments", Icons.Outlined.Tune, tr("Umgebungen"), if (envs.isEmpty()) tr("Variablen & Netzwerkzugriff für Agenten") else tr("{0} Umgebung(en)", envs.size)),
            SettingsItem("settings/connections", Icons.Outlined.Hub, tr("Verbindungen"), connectionsSummary(c)),
        ),
        onNavigate,
    )
    SettingsGroup(
        "Assistent",
        listOfNotNull(
            SettingsItem("settings/chat", Icons.Outlined.ChatBubbleOutline, tr("Chat & Werkzeuge"), tr("Standardmodell, Werkzeuge · Freigaben: ") + ApprovalMode.of(s.approvalMode).label),
            SettingsItem(
                "settings/tools", Icons.Outlined.Handyman, tr("Werkzeuge"),
                s.enabledToolIds.size.let { if (it == 0) tr("Große Werkzeuge ein- und ausschalten, Seitenleiste ordnen") else "$it eingeschaltet" },
            ),
            SettingsItem(
                "settings/lora", Icons.Outlined.AutoAwesome, "Lora",
                when (s.loraMode) { "CHAT" -> tr("Nur im Chat"); "OFF" -> tr("Aus"); else -> tr("Überall · Navigatorin für Einstellungen und Bereiche") },
            ),
            SettingsItem("settings/privacy", Icons.Outlined.Shield, tr("Datenschutz"), if (s.pseudonymize) tr("Pseudonymisierung an") else tr("Pseudonymisierung aus")),
            SettingsItem("settings/memory", Icons.Outlined.Psychology, tr("Gedächtnis"), MemoryMode.of(s.memoryMode).label + " · " + memory.size + tr(" Einträge")),
            SettingsItem(
                "settings/knowledge", Icons.Outlined.LibraryBooks, tr("Wissenssammlung"),
                knowledge.count { it.collection.isEmpty() }.let { if (it == 0) tr("Eigene Dokumente durchsuchbar machen") else tr("{0} Dokument(e)", it) },
            ),
            SettingsItem(
                "settings/literature", Icons.Outlined.MenuBook, tr("Literatur & Zotero"),
                (io.github.thepytales.llmhub.research.CitationStyle.parse(s.citationStyle)?.label ?: "APA 7") + if (s.zoteroKey.isNotBlank()) tr(" · Zotero verbunden") else "",
            ),
            SettingsItem(
                "settings/folders", Icons.Outlined.Folder, tr("Lokale Ordner"),
                c.localFolders.current.size.let { if (it == 0) tr("Agent in Ordnern auf diesem PC arbeiten lassen") else tr("{0} Ordner freigegeben", it) },
            ).takeIf { FolderAccess.SUPPORTED },
        ),
        onNavigate,
    )
    SettingsGroup(
        "Modelle",
        listOf(
            SettingsItem("models", Icons.Outlined.AutoAwesome, tr("Modellübersicht"), tr("Alle Modelle des Uni-Servers mit Stärken und Einsatzzweck")),
            SettingsItem("compare", Icons.Outlined.CompareArrows, tr("Modelle vergleichen"), tr("Zwei Modelle beantworten dieselbe Frage nebeneinander")),
            SettingsItem("local-models", Icons.Outlined.Memory, tr("Modelle auf diesem Gerät"), localSummary(c)),
            SettingsItem("settings/transcription", Icons.Outlined.GraphicEq, tr("Transkription"), transcriptionSummary(c)),
        ),
        onNavigate,
    )
    SettingsGroup(
        tr("Dokumente & Darstellung"),
        listOfNotNull(
            SettingsItem(
                "settings/documents", Icons.Outlined.Description, tr("Dokumente & Logos"),
                (DocStyle.parse(s.docStyle)?.label ?: "Modern") + (if (logos.isEmpty()) "" else " · ${logos.size} Logo(s)") +
                    if (s.senderInfo.isBlank()) tr(" · Absender fehlt") else "",
            ),
            SettingsItem("settings/voice", Icons.AutoMirrored.Outlined.VolumeUp, tr("Vorlesen & Spracheingabe"), if (s.ttsEngine == "DEVICE") tr("Offline auf dem Gerät") else tr("KI-Stimme des Uni-Servers")),
            SettingsItem("settings/appearance", Icons.Outlined.Palette, tr("Darstellung"), enumOr(s.appStyle, AppStyle.ADEU).label + " · " + enumOr(s.themeMode, ThemeMode.SYSTEM).label),
            deviceItem,
        ),
        onNavigate,
    )
    SettingsGroup(
        "Daten",
        listOfNotNull(
            syncSettingsItem(),
            SettingsItem("usage", Icons.Outlined.Insights, tr("Verbrauch"), tr("Anfragen und Tokens pro Tag")),
            SettingsItem(
                "settings/storage", Icons.Outlined.FolderOpen, tr("Speicherorte"),
                c.docs.vaults.value.size.let { if (it == 0) tr("Eigene Ordner für Dokumente, z. B. in Google Drive") else tr("{0} Ablageort(e)", it) },
            ).takeIf { FolderAccess.SUPPORTED },
            SettingsItem("settings/backup", Icons.Outlined.SettingsBackupRestore, tr("Sicherung & Umzug"), tr("Alles exportieren und auf einem anderen Gerät importieren")),
            SettingsItem("settings/trash", Icons.Outlined.DeleteOutline, tr("Papierkorb"), tr("Gelöschte Chats und Transkripte wiederherstellen")),
            SettingsItem("settings/ram", Icons.Outlined.Speed, tr("Arbeitsspeicher"), memorySummary()),
            SettingsItem("settings/updates", Icons.Outlined.SystemUpdate, tr("Updates"), updateSummary()),
            SettingsItem("settings/help", Icons.Outlined.BugReport, tr("Problem melden"), tr("Beschreibung und anonymes Fehlerprotokoll senden")),
        ),
        onNavigate,
    )
    Text(
        tr("Lora {0} · Inoffizielle App, nicht mit der Universität verbunden.", BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Version and what the last update check found, for the settings overview. */
@Composable
private fun updateSummary(): String {
    val state by io.github.thepytales.llmhub.update.Updates.state.collectAsStateWithLifecycle()
    val version = tr("Version {0}", BuildConfig.VERSION_NAME)
    return when (val s = state) {
        is io.github.thepytales.llmhub.update.Updates.State.Available -> tr("{0} · Neue Version {1} verfügbar", version, s.info.version)
        is io.github.thepytales.llmhub.update.Updates.State.Ready -> tr("{0} · {1} bereit zum Installieren", version, s.info.version)
        io.github.thepytales.llmhub.update.Updates.State.UpToDate -> "$version · aktuell"
        else -> tr("{0} · automatisch beim Start", version)
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
                label = { Text(tr("Vorname (optional)")) },
                supportingText = { Text(tr("Für die Begrüßung und das Profil in der Seitenleiste.")) },
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
    val where = if (s.mode == "SERVER") tr("Uni-Server") else tr("Auf diesem Gerät")
    val n = t.state.value.models.count { it.installed }
    return where + if (n > 0) tr(" · {0} Modell(e) geladen", n) else ""
}

/** Subtitle of "Modelle auf diesem Gerät" in the overview. */
internal fun localSummary(c: AppContainer): String {
    val n = c.localLlm.state.value.installed.size
    return if (n == 0) tr("Sprachmodelle offline – für vertrauliche Daten") else tr("{0} Modell(e) installiert · offline", n)
}
