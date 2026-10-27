package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        in 18..22 -> "Guten Abend"
        else -> "Hallo"
    }
    return if (name.isBlank()) tr(hello) else "${tr(hello)}, ${name.trim()}"
}

/** An example on the start page: fills the input (and possibly switches mode) or opens an area of the app. */
internal data class ChatExample(val label: String, val text: String = "", val literature: Boolean = false, val route: String? = null)

internal val CHAT_EXAMPLES = listOf(
    ChatExample("Literatur zu einem Thema finden", "Finde aktuelle Literatur zum Thema: ", literature = true),
    ChatExample("Literaturverzeichnis ordnen", route = "literature"),
    ChatExample("Dokument entwerfen", "Entwirf ein Dokument: "),
    ChatExample("Diagramm aus Daten", "Erstelle ein Diagramm aus diesen Daten:\n"),
    ChatExample("Interview transkribieren", route = "transcripts"),
)

/**
 * Start of a new chat: Lora waves and offers ideas (the favourite or suggested templates and a few examples of what
 * the app can do) in a speech bubble, below the greeting and the chosen model.
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
) {
    val look = LocalChatLook.current
    // Templates and examples take turns, the whole library comes last.
    val ideas = buildList {
        val templates = suggestions.map { t -> LoraIdea(t.name, tr("Vorlage"), template = true) { onTemplate(t) } }
        val examples = CHAT_EXAMPLES.map { e -> LoraIdea(tr(e.label), tr("Idee"), template = false) { onExample(e) } }
        for (i in 0 until maxOf(templates.size, examples.size)) {
            templates.getOrNull(i)?.let(::add)
            examples.getOrNull(i)?.let(::add)
        }
        add(LoraIdea(tr("Alle Vorlagen ansehen"), tr("Vorlagen"), template = true, run = onAllTemplates))
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        // Lora shrinks with the space (keyboard open); the bubble needs some room.
        val loraHeight = if (hasApiKey) (maxHeight * 0.22f).coerceIn(72.dp, 132.dp) else 72.dp
        val bubble = hasApiKey && maxHeight >= 380.dp
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
                Spacer(Modifier.size(14.dp))
                Box(Modifier.widthIn(max = 520.dp)) { extra() }
            }
            Spacer(Modifier.weight(1.3f))
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
        MenuEntry(Icons.Outlined.Description, "Als PDF/Word exportieren", hasMessages, onClick = pick(onExport))
        MenuEntry(Icons.Outlined.DataObject, "Als JSON exportieren", hasMessages, onClick = pick(onExportJson))
        MenuEntry(Icons.Outlined.Search, "Im Chat suchen", hasMessages, onClick = pick(onSearch))
        MenuGroup(null)
        MenuEntry(Icons.Outlined.Edit, "Umbenennen", hasMessages, onClick = pick(onRename))
        MenuEntry(Icons.Outlined.PushPin, if (pinned) "Lösen" else "Anheften", hasMessages, onClick = pick(onPin))
        MenuEntry(Icons.Outlined.DriveFileMove, "In Projekt verschieben", hasMessages, onClick = pick(onFolder))
        MenuEntry(Icons.Outlined.ContentCopy, "Duplizieren", hasMessages, onClick = pick(onDuplicate))
        MenuEntry(Icons.Outlined.Archive, if (archived) "Aus dem Archiv holen" else "Archivieren", hasMessages, onClick = pick(onArchive))
        onHomeScreen?.let { MenuEntry(Icons.Outlined.Home, "Zum Startbildschirm hinzufügen", hasMessages, onClick = pick(it)) }
        MenuGroup("Für diesen Chat")
        MenuEntry(Icons.Outlined.Person, "Anweisungen & Persona", onClick = pick(onPersona))
        MenuEntry(Icons.Outlined.Tune, "Umgebung", onClick = pick(onEnvironment))
        MenuEntry(
            Icons.Outlined.DoneAll,
            if (autoApprove) "Aktionen wieder bestätigen" else "Aktionen ohne Rückfrage",
            onClick = pick(onAutoApprove),
        )
        MenuGroup(null)
        MenuEntry(Icons.Outlined.Delete, "Löschen", hasMessages, danger = true, onClick = pick(onDelete))
    }
}

