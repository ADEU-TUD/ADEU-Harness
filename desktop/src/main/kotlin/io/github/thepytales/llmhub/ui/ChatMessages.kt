package io.github.thepytales.llmhub.ui

import android.content.Context
import android.util.Base64
import io.github.thepytales.llmhub.desktop.DesktopActions
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.parentKey
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.net.JSON
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One entry of the message list. */
sealed interface ChatItem {
    val key: String

    data class User(val m: ChatMessage, val versions: List<ChatMessage>, val isLast: Boolean) : ChatItem {
        override val key get() = m.id
    }

    /**
     * An assistant message; the action row (copy, read aloud, versions …) is shown at the end of a turn,
     * which can consist of several messages when tools are used.
     */
    data class Assistant(
        val m: ChatMessage,
        val turnEnd: Boolean,
        val versions: List<ChatMessage>,
        val head: ChatMessage,
        val question: ChatMessage?,
        val turnText: String,
        val isLast: Boolean,
        /** All messages of the turn including tool results, for the agent protocol (set on the turn end). */
        val turn: List<ChatMessage> = emptyList(),
    ) : ChatItem {
        override val key get() = m.id
    }
}

/** Builds the visible list (oldest first) from the active thread of [conv]. */
fun chatItems(conv: Conversation): List<ChatItem> {
    val thread = conv.thread()
    val byParent = conv.messages.groupBy { it.parentKey }
    val visible = thread.filter { it.role != "tool" }
    val out = ArrayList<ChatItem>()
    var head: ChatMessage? = null
    var question: ChatMessage? = null
    val turnParts = mutableMapOf<String, MutableList<String>>()
    visible.forEachIndexed { i, m ->
        val isLast = i == visible.lastIndex
        when (m.role) {
            "user" -> {
                out += ChatItem.User(m, byParent[m.parentKey].orEmpty().filter { it.role == "user" }, isLast)
                head = null
                question = m
            }
            "assistant" -> {
                val h = head ?: m.also { head = it }
                val next = visible.getOrNull(i + 1)
                val answer = splitThinking(m.content).second.trim()
                if (!m.error && answer.isNotEmpty()) turnParts.getOrPut(h.id) { mutableListOf() } += answer
                out += ChatItem.Assistant(
                    m = m,
                    turnEnd = next == null || next.role != "assistant",
                    versions = byParent[h.parentKey].orEmpty().filter { it.role == "assistant" },
                    head = h,
                    question = question,
                    turnText = "",
                    isLast = isLast,
                )
            }
        }
    }
    // Messages of each turn (answers and tool results) by the first answer of the turn.
    val turns = mutableMapOf<String, MutableList<ChatMessage>>()
    var turnHead: String? = null
    for (m in thread) {
        when (m.role) {
            "user" -> turnHead = null
            "assistant", "tool" -> {
                val h = turnHead ?: m.id.also { turnHead = it }
                turns.getOrPut(h) { mutableListOf() } += m
            }
        }
    }
    return out.map { item ->
        if (item is ChatItem.Assistant && item.turnEnd) {
            item.copy(turnText = turnParts[item.head.id].orEmpty().joinToString("\n\n"), turn = turns[item.head.id].orEmpty())
        } else item
    }
}

@Composable
fun rememberDataUrlBitmap(url: String): ImageBitmap? = remember(url) {
    runCatching {
        val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

@Composable
fun Thumbnail(url: String, size: Int, onRemove: (() -> Unit)? = null) {
    val bmp = rememberDataUrlBitmap(url) ?: return
    Box {
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        if (onRemove != null) RemoveBadge(onRemove, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun RemoveBadge(onRemove: () -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier
            .padding(2.dp)
            .size(22.dp)
            .clickable(onClick = onRemove),
    ) {
        Icon(Icons.Outlined.Close, contentDescription = "Entfernen", modifier = Modifier.padding(3.dp))
    }
}

/** Small card for an attached document. */
@Composable
fun FileChip(file: FileAttachment, onRemove: (() -> Unit)? = null) {
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 220.dp),
        ) {
            Row(Modifier.padding(start = 8.dp, end = if (onRemove != null) 24.dp else 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (file.mime == "application/pdf") Icons.Outlined.PictureAsPdf else Icons.AutoMirrored.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(6.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(file.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val info = listOfNotNull(
                        file.pages?.let { "$it ${if (it == 1) "Seite" else "Seiten"}" },
                        "${(file.text.length + 999) / 1000}k Zeichen" + if (file.truncated) " (gekürzt)" else "",
                    ).joinToString(" · ")
                    Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onRemove != null) RemoveBadge(onRemove, Modifier.align(Alignment.TopEnd))
    }
}

/** "‹ 2/3 ›" switcher between versions of a message. */
@Composable
fun VersionSwitcher(current: ChatMessage, versions: List<ChatMessage>, enabled: Boolean, onSelect: (ChatMessage) -> Unit) {
    if (versions.size < 2) return
    val index = versions.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = enabled && index > 0, onClick = { onSelect(versions[index - 1]) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Vorherige Version", modifier = Modifier.size(18.dp))
        }
        Text(
            "${index + 1}/${versions.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(enabled = enabled && index < versions.lastIndex, onClick = { onSelect(versions[index + 1]) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Nächste Version", modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun UserBubble(
    item: ChatItem.User,
    running: Boolean,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitEdit: (String) -> Unit,
    onSelectVersion: (ChatMessage) -> Unit,
) {
    val m = item.m
    val look = LocalChatLook.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        m.template?.let { name ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)) {
                Icon(Icons.Outlined.Bookmarks, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                Text("Vorlage: $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (m.files.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                m.files.forEach { FileChip(it) }
            }
        }
        if (m.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                m.images.forEach { Thumbnail(it, 110) }
            }
        }
        if (editing) {
            var text by remember(m.id) { mutableStateOf(m.content) }
            Surface(color = look.userBubble, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), maxLines = 10)
                    Row(Modifier.align(Alignment.End).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancelEdit) { Text("Abbrechen") }
                        Button(onClick = { onSubmitEdit(text) }, enabled = text.isNotBlank() && !running) { Text("Senden") }
                    }
                }
            }
        } else if (m.content.isNotBlank()) {
            Box {
                Surface(
                    color = look.userBubble,
                    shape = look.userShape,
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(look.userShape)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .onRightClick { menu = true },
                ) {
                    Text(m.content, color = look.onUserBubble, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Kopieren") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { clipboard.setText(AnnotatedString(m.content)); menu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Bearbeiten") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        enabled = !running,
                        onClick = { menu = false; onStartEdit() },
                    )
                }
            }
        }
        if (!editing && (item.versions.size > 1 || item.isLast)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VersionSwitcher(m, item.versions, !running, onSelectVersion)
                if (!running && m.content.isNotBlank()) {
                    IconButton(onClick = onStartEdit, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(text: String, canRetry: Boolean, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            if (canRetry) {
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Erneut versuchen")
                }
            }
        }
    }
}

@Composable
fun AssistantMessage(
    item: ChatItem.Assistant,
    results: Map<String?, ChatMessage>,
    running: Boolean,
    streaming: Boolean,
    playing: Boolean,
    onSpeak: () -> Unit,
    onExport: (String) -> Unit,
    onRegenerate: () -> Unit,
    onRegenerateWithModel: () -> Unit,
    onSelectVersion: (ChatMessage) -> Unit,
    onRetry: () -> Unit,
) {
    val m = item.m
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    if (m.error) {
        Column {
            ErrorCard(m.content, canRetry = item.isLast && !running, onRetry = onRetry)
            if (item.turnEnd && item.versions.size > 1) VersionSwitcher(item.head, item.versions, !running, onSelectVersion)
        }
        return
    }
    val (inlineThinking, answer) = remember(m.content) { splitThinking(m.content) }
    val thinking = listOfNotNull(m.reasoning, inlineThinking).joinToString("\n").ifBlank { null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (thinking != null) ThinkingBox(thinking, streaming && answer.isBlank())
        if (answer.isNotBlank()) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = LocalChatLook.current.answerFont)) {
                SelectionContainer { MarkdownText(answer) }
            }
        }
        m.toolCalls.forEach { call ->
            val result = results[call.id]
            ToolCallCard(call.name, shortArgs(call.arguments), result?.content, streaming || running && result == null)
            result?.file?.let { DocumentCard(it) }
        }
        if (item.turnEnd && !(running && item.isLast)) {
            var more by remember { mutableStateOf(false) }
            var checkSources by remember { mutableStateOf(false) }
            val text = item.turnText
            if (checkSources) ReferenceCheckDialog(io.github.thepytales.llmhub.App.container, text, onDismiss = { checkSources = false })
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (text.isNotBlank()) {
                    ActionIcon(Icons.Outlined.ContentCopy, "Kopieren") { clipboard.setText(AnnotatedString(text)) }
                    ActionIcon(Icons.Outlined.IosShare, "Teilen") { shareText(context, text) }
                    ActionIcon(if (playing) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, "Vorlesen", onClick = onSpeak)
                }
                if (item.question != null) ActionIcon(Icons.Outlined.Refresh, "Neu generieren", enabled = !running, onClick = onRegenerate)
                var protocol by remember { mutableStateOf(false) }
                if (item.turn.any { it.durationMs != null }) ActionIcon(Icons.Outlined.Timeline, "Agent-Protokoll") { protocol = true }
                if (protocol) AgentProtocolDialog(item.turn, onDismiss = { protocol = false })
                Box {
                    ActionIcon(Icons.Outlined.MoreHoriz, "Mehr") { more = true }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        if (text.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Als Dokument exportieren") },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                onClick = { more = false; onExport(text) },
                            )
                        }
                        if (text.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Quellen prüfen") },
                                leadingIcon = { Icon(Icons.Outlined.FactCheck, contentDescription = null) },
                                onClick = { more = false; checkSources = true },
                            )
                        }
                        if (item.question != null) {
                            DropdownMenuItem(
                                text = { Text("Mit anderem Modell neu generieren") },
                                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                                enabled = !running,
                                onClick = { more = false; onRegenerateWithModel() },
                            )
                        }
                    }
                }
                VersionSwitcher(item.head, item.versions, !running, onSelectVersion)
                Spacer(Modifier.weight(1f))
                m.model?.let {
                    Text(
                        ModelCatalog.info(it).displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.isLast) {
                Text(
                    "ADEU LLM nutzt KI und kann Fehler machen. Wichtiges bitte prüfen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Windows has no share sheet: the text goes to the clipboard, ready to paste into mail, Teams … */
@Suppress("UNUSED_PARAMETER")
fun shareText(context: Context, text: String, subject: String? = null) {
    DesktopActions.share(if (subject != null) "$subject\n\n$text" else text)
}

/** Shown while the model works on an answer: the brand spark slowly pulsing, like in the Claude app. */
@Composable
fun ThinkingIndicator(label: String = "Denkt nach …") {
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.AutoAwesome, contentDescription = null,
            tint = LocalChatLook.current.brand,
            modifier = Modifier.size(22.dp).graphicsLayer { alpha = pulse },
        )
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ThinkingBox(text: String, active: Boolean) {
    var open by remember { mutableStateOf(false) }
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    if (active) "Denkt nach…" else "Gedankengang",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            if (open || active) {
                Text(
                    if (open) text else text.takeLast(300),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Human-readable labels for tool calls. */
private fun toolLabel(name: String) = when (name) {
    "web_search" -> "Websuche"
    "news_search" -> "Nachrichtensuche"
    "fetch_url" -> "Webseite lesen"
    "create_document" -> "Dokument erstellen"
    "save_logo" -> "Logo speichern"
    "read_file" -> "Datei lesen"
    "list_files" -> "Dateien auflisten"
    "write_file", "edit_file" -> "Datei ändern"
    "search_code" -> "Code durchsuchen"
    "edit_document" -> "Dokument ändern"
    "check_references" -> "Quellen prüfen"
    "check_accessibility" -> "Barrierefreiheit prüfen"
    "run_javascript" -> "Rechnen"
    "search_knowledge" -> "Wissenssammlung durchsuchen"
    "read_pdf_form" -> "Formular lesen"
    "fill_pdf" -> "PDF ausfüllen"
    "create_email_draft" -> "E-Mail-Entwurf"
    "create_calendar_event" -> "Termin anlegen"
    "read_guide" -> "Anleitung lesen"
    else -> name
}

@Composable
fun ToolCallCard(name: String, args: String, result: String?, working: Boolean) {
    var open by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text(toolLabel(name), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(6.dp))
                Text(
                    args,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    result == null && working -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    result != null -> Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            if (open && result != null) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SelectionContainer {
                    Text(
                        if (result.length > 5000) result.take(5000) + "\n…" else result,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

fun prettyArgs(args: String): String = runCatching {
    val obj = JSON.parseToJsonElement(args) as JsonObject
    obj.entries.joinToString("\n\n") { (k, v) ->
        val value = (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: v.toString()
        "$k:\n$value"
    }
}.getOrDefault(args)

fun shortArgs(args: String): String = runCatching {
    val obj = JSON.parseToJsonElement(args) as JsonObject
    obj.entries.filter { it.key !in setOf("content", "new_text", "old_text", "body", "headers") }
        .joinToString(", ") { (_, v) ->
            val s = (v as? JsonPrimitive)?.content ?: v.toString()
            s.take(50)
        }
}.getOrDefault(args.take(60))
