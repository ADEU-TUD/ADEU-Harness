package io.github.thepytales.llmhub.ui

import androidx.compose.material.icons.outlined.Folder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.label

/** Parts of the chat screen: the start of a new chat and the chat menu. */

internal fun greeting(name: String): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when (hour) {
        in 5..10 -> tr("Guten Morgen")
        in 11..17 -> tr("Guten Tag")
        in 18..22 -> tr("Guten Abend")
        else -> tr("Hallo")
    }
    return if (name.isBlank()) tr(hello) else "${tr(hello)}, ${name.trim()}"
}

/** An example on the start page: fills the input (and possibly switches mode) or opens an area of the app. */
internal data class ChatExample(val label: String, val text: String = "", val mode: String? = null, val route: String? = null)

/** A concrete start under an activity of the start page, e.g. „E-Mail formulieren“: fills the input or opens a place. */
internal class StartExample(val label: String, val run: () -> Unit)

/**
 * An activity of the start page (AP-02), shown as a chip, e.g. „Dateien auswerten“: a tap opens its [examples] to
 * pick from (what one can actually ask); without examples it runs [run] at once.
 */
internal class StartAction(val label: String, val icon: ImageVector, val examples: List<StartExample> = emptyList(), val run: () -> Unit = {})

internal val CHAT_EXAMPLES = listOf(
    ChatExample(trLater("Literatur zu einem Thema finden"), trLater("Finde aktuelle Literatur zum Thema: "), mode = "literature"),
    ChatExample(trLater("Ein Vorhaben planen (Planungsmodus)"), "", mode = "planning"),
    ChatExample(trLater("Literaturverzeichnis ordnen"), route = "literature"),
    ChatExample(trLater("Dokument entwerfen"), trLater("Entwirf ein Dokument: ")),
    ChatExample(trLater("Diagramm aus Daten"), trLater("Erstelle ein Diagramm aus diesen Daten:\n")),
    ChatExample(trLater("Interview transkribieren"), route = "transcripts"),
)

/**
 * Start of a new chat: Lora waves and offers ideas (the favourite or suggested templates and a few examples of what
 * the app can do) in a speech bubble – a tap on her opens her panel –, below the greeting, the chosen model and the
 * activities as chips; a chip opens concrete starts in place of the chips.
 */
@Composable
internal fun EmptyChat(
    hasApiKey: Boolean,
    userName: String,
    model: ModelInfo,
    onModel: () -> Unit,
    padding: PaddingValues,
    suggestions: List<io.github.thepytales.llmhub.data.Template>,
    onTemplate: (io.github.thepytales.llmhub.data.Template) -> Unit,
    onApiKey: () -> Unit,
    onAllTemplates: () -> Unit,
    onOpen: (String) -> Unit,
    onExample: (ChatExample) -> Unit = {},
    /** Something is typed in the input: Lora takes notes and the bubble steps aside. */
    typing: Boolean = false,
    /** Lora ausblenden: the brand symbol instead of Lora, the ideas in a bubble below the greeting. */
    hideLora: Boolean = false,
    /** Below the greeting, e.g. the invitation to set up the memory. */
    extra: @Composable () -> Unit = {},
    /** The activities as chips below the greeting (AP-02); each opens concrete starts. */
    actions: List<StartAction> = emptyList(),
    /** The latest chats („Zuletzt“) and the favourite templates („Favoriten“), shown when there is room. */
    recent: List<io.github.thepytales.llmhub.data.Conversation> = emptyList(),
    favorites: List<io.github.thepytales.llmhub.data.Template> = emptyList(),
    onOpenChat: (String) -> Unit = {},
    /** The chat's mode (AP-05): its example to tap replaces the tiles. */
    mode: io.github.thepytales.llmhub.agent.ChatMode? = null,
    onModeExample: (String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onMode: (io.github.thepytales.llmhub.agent.ChatMode) -> Unit = {},
) {
    val look = LocalChatLook.current
    // Templates and examples take turns, the whole library comes last.
    val ideas = buildList {
        // Favourites have their own row below; Lora's bubble offers the other suggestions and the examples, one at a time.
        val templates = suggestions.filter { t -> favorites.none { it.id == t.id } }.map { t -> LoraIdea(t.name, tr("Vorlage"), template = true) { onTemplate(t) } }
        val examples = CHAT_EXAMPLES.map { e -> LoraIdea(tr(e.label), tr("Idee"), template = false) { onExample(e) } }
        for (i in 0 until maxOf(templates.size, examples.size)) {
            templates.getOrNull(i)?.let(::add)
            examples.getOrNull(i)?.let(::add)
        }
        add(LoraIdea(tr("Alle TU Vorlagen ansehen"), tr("TU Vorlagen"), template = true, run = onAllTemplates))
    }
    // The activity whose starts are open (in place of the chips); typing closes it.
    var opened by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(typing) { if (typing) opened = null }
    val open = actions.firstOrNull { it.label == opened }
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        // Lora shrinks with the space (keyboard open) and while the starts of an activity are open; the bubble needs room.
        val loraHeight = if (!hasApiKey) 72.dp else if (open != null) (maxHeight * 0.14f).coerceIn(64.dp, 96.dp) else (maxHeight * 0.22f).coerceIn(72.dp, 132.dp)
        val bubble = hasApiKey && maxHeight >= (if (open != null) 560.dp else 380.dp)
        val roomForTiles = maxHeight >= 420.dp
        val roomForLists = maxHeight >= 600.dp && open == null
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (hideLora) Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = look.brand, modifier = Modifier.size(40.dp))
            else LoraHome(ideas = if (bubble) ideas else emptyList(), typing = typing, loraHeight = loraHeight)
            Spacer(Modifier.size(10.dp))
            Text(
                greeting(userName),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = look.titleFont),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                model.displayName + (model.useCase.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onModel)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (!hasApiKey) {
                Spacer(Modifier.size(20.dp))
                WelcomeCard(onApiKey = onApiKey, onNavigate = onOpen)
            } else {
                if (hideLora && bubble) {
                    Spacer(Modifier.size(16.dp))
                    IdeaRow(ideas, typing)
                }
                if (mode != null) {
                    Spacer(Modifier.size(18.dp))
                    ModeExample(mode, onModeExample)
                } else if (actions.isNotEmpty() && roomForTiles) {
                    Spacer(Modifier.size(18.dp))
                    // The chips, or the starts of the opened activity in their place.
                    if (open == null) StartChips(actions) { a -> if (a.examples.isEmpty()) a.run() else opened = a.label }
                    else StartExamples(open, onClose = { opened = null }) { e -> opened = null; e.run() }
                }
                if (roomForLists && (recent.isNotEmpty() || favorites.isNotEmpty())) {
                    Spacer(Modifier.size(14.dp))
                    RecentAndFavorites(recent, favorites, onOpenChat, onTemplate)
                }
                Spacer(Modifier.size(14.dp))
                Box(Modifier.widthIn(max = 520.dp)) { extra() }
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

/** The activities as chips, centered and wrapping onto a second line on narrow windows. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StartChips(actions: List<StartAction>, onPick: (StartAction) -> Unit) {
    val look = LocalChatLook.current
    androidx.compose.foundation.layout.FlowRow(
        Modifier.widthIn(max = 900.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { a ->
            Surface(
                onClick = { onPick(a) },
                shape = RoundedCornerShape(50),
                color = look.card,
                border = boxBorder(),
                modifier = Modifier.heightIn(min = 42.dp),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(a.icon, contentDescription = null, tint = look.brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(a.label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** The concrete starts of [action] in a card (in place of the chips): a tap fills the input or opens the place. */
@Composable
private fun StartExamples(action: StartAction, onClose: () -> Unit, onPick: (StartExample) -> Unit) {
    val look = LocalChatLook.current
    Surface(shape = MaterialTheme.shapes.medium, color = look.card, border = boxBorder(), modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(action.icon, contentDescription = null, tint = look.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(tr(action.label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            action.examples.forEach { e ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(e) }
                        .heightIn(min = 46.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr(e.label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.NorthWest, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** „Zuletzt“ (the latest chats) and „Favoriten“ (favourite templates), side by side on wide screens. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecentAndFavorites(
    recent: List<io.github.thepytales.llmhub.data.Conversation>,
    favorites: List<Template>,
    onOpenChat: (String) -> Unit,
    onTemplate: (Template) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.widthIn(max = 760.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (recent.isNotEmpty()) {
            Column(Modifier.widthIn(min = 220.dp, max = 360.dp)) {
                GroupLabel(tr("Zuletzt"))
                recent.forEach { conv ->
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onOpenChat(conv.id) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Text(conv.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            Column(Modifier.widthIn(min = 220.dp, max = 360.dp)) {
                GroupLabel(tr("Favoriten"))
                favorites.forEach { t ->
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onTemplate(t) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = LocalChatLook.current.brand)
                        Spacer(Modifier.size(8.dp))
                        Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.heightIn(min = 52.dp),
    )
}

/** Divider between the groups of the chat menu, optionally with a small heading. */
@Composable
private fun MenuGroup(title: String?) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    if (title != null) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
        )
    }
}

/** Chat menu like the Claude app: title on top, big entries, "Löschen" in red. */
@Composable
internal fun ChatMenu(
    open: Boolean,
    title: String,
    hasMessages: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onFolder: () -> Unit,
    onHomeScreen: (() -> Unit)?,
    onExport: () -> Unit,
    onPersona: () -> Unit,
    onEnvironment: () -> Unit,
    autoApprove: Boolean,
    onAutoApprove: () -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    archived: Boolean = false,
    onArchive: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onAiNote: () -> Unit = {},
    /** „Dateien dieses Chats“ (AP-16): the Dateien tab with what was uploaded and made here; null when it has none. */
    onFiles: (() -> Unit)? = null,
) {
    fun pick(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = LocalChatLook.current.card,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 260.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
        )
        MenuEntry(Icons.Outlined.IosShare, SHARE_LABEL, hasMessages, onClick = pick(onShare))
        MenuEntry(Icons.Outlined.Description, tr("Als PDF/Word exportieren"), hasMessages, onClick = pick(onExport))
        MenuEntry(Icons.Outlined.DataObject, tr("Als JSON exportieren"), hasMessages, onClick = pick(onExportJson))
        MenuEntry(Icons.Outlined.Search, tr("Im Chat suchen"), hasMessages, onClick = pick(onSearch))
        MenuEntry(Icons.Outlined.VerifiedUser, tr("KI-Nutzungsnachweis"), hasMessages, onClick = pick(onAiNote))
        onFiles?.let { MenuEntry(Icons.Outlined.Folder, tr("Dateien dieses Chats"), hasMessages, onClick = pick(it)) }
        MenuGroup(null)
        MenuEntry(Icons.Outlined.Edit, tr("Umbenennen"), hasMessages, onClick = pick(onRename))
        MenuEntry(Icons.Outlined.PushPin, if (pinned) tr("Lösen") else tr("Anheften"), hasMessages, onClick = pick(onPin))
        MenuEntry(Icons.Outlined.DriveFileMove, tr("In Projekt verschieben"), hasMessages, onClick = pick(onFolder))
        MenuEntry(Icons.Outlined.ContentCopy, tr("Duplizieren"), hasMessages, onClick = pick(onDuplicate))
        MenuEntry(Icons.Outlined.Archive, if (archived) tr("Aus dem Archiv holen") else tr("Archivieren"), hasMessages, onClick = pick(onArchive))
        onHomeScreen?.let { MenuEntry(Icons.Outlined.Home, tr("Zum Startbildschirm hinzufügen"), hasMessages, onClick = pick(it)) }
        MenuGroup(tr("Für diesen Chat"))
        MenuEntry(Icons.Outlined.Person, tr("Anweisungen & Persona"), onClick = pick(onPersona))
        MenuEntry(Icons.Outlined.Tune, tr("Umgebung"), onClick = pick(onEnvironment))
        MenuEntry(
            Icons.Outlined.DoneAll,
            if (autoApprove) tr("Aktionen wieder bestätigen") else tr("Aktionen ohne Rückfrage"),
            onClick = pick(onAutoApprove),
        )
        MenuGroup(null)
        MenuEntry(Icons.Outlined.Delete, tr("Löschen"), hasMessages, danger = true, onClick = pick(onDelete))
    }
}

