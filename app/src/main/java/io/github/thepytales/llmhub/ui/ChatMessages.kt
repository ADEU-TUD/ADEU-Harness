package io.github.thepytales.llmhub.ui

import android.util.Base64
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
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timeline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
fun rememberDataUrlBitmap(url: String, maxPx: Int = THUMB_MAX_PX): ImageBitmap? = remember(url, maxPx) {
    runCatching {
        val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
        decodeImage(bytes)?.let { downscale(it, maxPx) }
    }.getOrNull()
}

/** Thumbnails in the chat are kept small in memory (AP-04): a 12-megapixel photo needs 48 MB, its thumbnail 1 MB. */
const val THUMB_MAX_PX = 512

/** [img] scaled down so that its longer side is at most [maxPx]; smaller pictures stay as they are. */
fun downscale(img: ImageBitmap, maxPx: Int): ImageBitmap {
    val longer = maxOf(img.width, img.height)
    if (longer <= maxPx) return img
    val f = maxPx.toFloat() / longer
    val out = ImageBitmap((img.width * f).toInt().coerceAtLeast(1), (img.height * f).toInt().coerceAtLeast(1))
    androidx.compose.ui.graphics.Canvas(out).drawImageRect(
        img, dstSize = androidx.compose.ui.unit.IntSize(out.width, out.height),
        paint = androidx.compose.ui.graphics.Paint().apply { filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium },
    )
    return out
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

/** Small card for an attached document; with [onOpen] a tap opens it in the editor. */
@Composable
fun FileChip(file: FileAttachment, onRemove: (() -> Unit)? = null, onOpen: (() -> Unit)? = null, onTogglePublic: (() -> Unit)? = null) {
    Box {
        // Attachments look like the document cards (AP-01): card color, fine frame, icon on a light accent tint.
        Surface(
            color = LocalChatLook.current.card,
            border = boxBorder(),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.widthIn(max = 280.dp).clip(MaterialTheme.shapes.small).then(if (onOpen != null) Modifier.clickable(onClickLabel = "Im Editor öffnen", onClick = onOpen) else Modifier),
        ) {
            Row(Modifier.padding(start = 8.dp, end = if (onRemove != null) 24.dp else 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(if (file.mime == "application/pdf") Icons.Outlined.PictureAsPdf else Icons.AutoMirrored.Outlined.InsertDriveFile, size = 32.dp)
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(file.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val info = listOfNotNull(
                        file.pages?.let { "$it ${if (it == 1) "Seite" else "Seiten"}" },
                        "${(file.text.length + 999) / 1000}k Zeichen" + if (file.truncated) " (gekürzt)" else "",
                    ).joinToString(" · ")
                    Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Public sources go without placeholders (AP-03); the app suggests it, a tap switches it.
                    if (onTogglePublic != null) {
                        val public = io.github.thepytales.llmhub.data.Pseudonymizer.isPublic(file)
                        Text(
                            if (public) "öffentlich · Namen bleiben" else "vertraulich · pseudonymisiert",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (public) MaterialTheme.colorScheme.primary else LocalChatLook.current.success,
                            modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClickLabel = "Umschalten", onClick = onTogglePublic).padding(vertical = 1.dp),
                        )
                    }
                }
                if (onOpen != null) {
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.Outlined.EditNote, contentDescription = "Im Editor öffnen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
    onSubmitEdit: (String, List<FileAttachment>, List<String>) -> Unit,
    onSelectVersion: (ChatMessage) -> Unit,
    onFork: () -> Unit = {},
    onRemember: (String) -> Unit = {},
) {
    val m = item.m
    val look = LocalChatLook.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // Handed over by Lora (AP-33): who chose the model and why; what Lora already knew opens with a tap.
        LoraHandoffCard(m)
        m.template?.let { name ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)) {
                Icon(Icons.Outlined.Bookmarks, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                Text("Vorlage: $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (m.files.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                // Tap: open in the editor and keep working on it with the model.
                m.files.forEach { f -> FileChip(f, onOpen = { DocsHandoff.openAttachment(io.github.thepytales.llmhub.App.container, f, null) }) }
            }
        }
        if (m.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                m.images.forEach { Thumbnail(it, 110) }
            }
        }
        if (editing) {
            var text by remember(m.id) { mutableStateOf(m.content) }
            // Attachments can be removed when editing (the text of the files goes along again otherwise).
            val keptFiles = remember(m.id) { androidx.compose.runtime.mutableStateListOf<FileAttachment>().apply { addAll(m.files) } }
            val keptImages = remember(m.id) { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(m.images) } }
            Surface(color = look.userBubble, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    if (keptFiles.isNotEmpty() || keptImages.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            keptImages.forEach { img -> Thumbnail(img, 60) { keptImages.remove(img) } }
                            keptFiles.forEach { f -> FileChip(f) { keptFiles.remove(f) } }
                        }
                    }
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 24)
                    Row(Modifier.align(Alignment.End).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancelEdit) { Text("Abbrechen") }
                        Button(onClick = { onSubmitEdit(text, keptFiles.toList(), keptImages.toList()) }, enabled = (text.isNotBlank() || keptFiles.isNotEmpty()) && !running) { Text("Senden") }
                    }
                }
            }
        } else if (m.content.isNotBlank()) {
            // Long texts (pasted transcripts …) are folded; the bubble takes up to 80 % of the reading width.
            var expanded by remember(m.id) { mutableStateOf(false) }
            val long = m.content.length > 1200 || m.content.count { it == '\n' } > 14
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                val bubbleMax = (maxWidth * 0.8f).coerceAtLeast(260.dp)
                Box {
                Surface(
                    color = look.userBubble,
                    shape = look.userShape,
                    modifier = Modifier
                        .widthIn(max = bubbleMax)
                        .clip(look.userShape)
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .secondaryClick { menu = true },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        SelectionContainer {
                            Text(
                                m.content, color = look.onUserBubble,
                                maxLines = if (long && !expanded) 12 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (long) {
                            Text(
                                if (expanded) "Weniger anzeigen" else "Mehr anzeigen",
                                style = MaterialTheme.typography.labelMedium, color = look.onUserBubble.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 6.dp).clickable { expanded = !expanded },
                            )
                        }
                    }
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
                    DropdownMenuItem(
                        text = { Text(tr("Merken …")) },
                        leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
                        onClick = { menu = false; onRemember(m.content) },
                    )
                    DropdownMenuItem(
                        text = { Text("Neuer Chat ab hier") },
                        leadingIcon = { Icon(Icons.Outlined.CallSplit, contentDescription = null) },
                        onClick = { menu = false; onFork() },
                    )
                }
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
    val error = MaterialTheme.colorScheme.error
    Surface(
        color = error.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, error.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.accentStripe(error).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
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
    onOpenInEditor: (String) -> Unit = {},
    onBibliography: (String) -> Unit = {},
    onOpenLiterature: (String) -> Unit = {},
    chatId: String? = null,
    onRate: (Int?) -> Unit = {},
    onContinue: () -> Unit = {},
    onQuote: (String) -> Unit = {},
    onFork: () -> Unit = {},
    onRemember: (String) -> Unit = {},
    /** Thinking and tool steps folded away (Arbeitsschritte einklappen); results like documents stay visible. */
    fold: Boolean = false,
    /** The line that opens folded steps, on the first message of a turn. */
    stepsLine: (@Composable () -> Unit)? = null,
    /** Sends the answers of a question card (ask_user, AP-06); null while the answer runs. */
    onAnswer: ((String) -> Unit)? = null,
    /** Belegprotokoll of this turn in the Quellenmodus (AP-08): marks, clickable evidence and the card at the end. */
    evidence: io.github.thepytales.llmhub.agent.EvidenceReport? = null,
) {
    val m = item.m
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // The answer was just finished: Lora's signature cheers for a moment.
    val justDone = rememberJustFinished(item.isLast && (running || streaming))
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
        stepsLine?.invoke()
        if (thinking != null && !fold) ThinkingBox(thinking, streaming && answer.isBlank())
        if (answer.isNotBlank()) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = LocalChatLook.current.answerFont)) {
                if (evidence != null) {
                    // Quellenmodus: statements without evidence are marked, a marker opens the page of its source.
                    val marked = remember(answer, evidence) { io.github.thepytales.llmhub.agent.Evidence.mark(answer, evidence) }
                    val targets = remember(evidence) { evidenceTargets(evidence) }
                    androidx.compose.runtime.CompositionLocalProvider(LocalSourceTargets provides targets) {
                        SelectionContainer { MarkdownText(marked) }
                    }
                } else SelectionContainer { MarkdownText(answer) }
            }
        }
        // Many finished tool calls collapse into one line ("5 Werkzeuge genutzt"); results like documents stay visible.
        val done = m.toolCalls.all { results[it.id] != null } && !(streaming || running && item.isLast)
        var showTools by remember(m.id) { mutableStateOf(false) }
        val grouped = done && m.toolCalls.size >= 3 && !showTools
        if (done && m.toolCalls.size >= 3 && !fold) {
            ToolGroupLine(m.toolCalls.map { it.name }, open = showTools) { showTools = !showTools }
        }
        m.toolCalls.forEach { call ->
            val result = results[call.id]
            // A question round (AP-06): a card with answer buttons instead of a tool step; active only as the last word.
            if (call.name == io.github.thepytales.llmhub.agent.AskUserTool.NAME) {
                val questions = remember(call.arguments) { io.github.thepytales.llmhub.agent.AskUserTool.parse(call.arguments) }
                if (result != null && !result.content.startsWith("FEHLER")) {
                    QuestionCard(questions, onAnswer.takeIf { item.isLast && !running && !streaming })
                }
                return@forEach
            }
            if (!grouped && !fold) ToolCallCard(call.name, shortArgs(call.arguments), result?.content, streaming || running && result == null)
            // The model is writing a document: it can be watched live in the editor.
            if (call.name == "create_document" && result == null && (streaming || running) && chatId != null) LiveDraftCard(chatId)
            if (call.name == "create_chart" && result != null && !result.content.startsWith("FEHLER")) {
                val raw = (io.github.thepytales.llmhub.agent.parseArgs(call.arguments)["spec"])?.let { e -> if (e is kotlinx.serialization.json.JsonPrimitive && e.isString) e.content else e.toString() }
                raw?.let { r -> remember(r) { io.github.thepytales.llmhub.docs.ChartSpec.parse(r) } }?.let { ChartView(it) }
            }
            if (call.name == "save_literature" && result != null && !result.content.startsWith("FEHLER")) {
                val list = io.github.thepytales.llmhub.agent.parseArgs(call.arguments)["list"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                if (list != null) LiteratureListCard(list, onOpen = { onOpenLiterature(list) })
            }
            result?.file?.let { DocumentCard(it) }
            // A learning game was built or changed (AP-17): play it right here, export it for OPAL.
            if ((call.name == io.github.thepytales.llmhub.agent.GameTools.CREATE || call.name == io.github.thepytales.llmhub.agent.GameTools.UPDATE) &&
                result != null && !result.content.startsWith("FEHLER")
            ) io.github.thepytales.llmhub.agent.GameTools.idIn(result.content)?.let { GameCard(it) }
            // A design was made or changed (AP-24): preview and „Bearbeiten“ (Erstellen › Meine Designs).
            if ((call.name == io.github.thepytales.llmhub.agent.DesignTools.CREATE || call.name == io.github.thepytales.llmhub.agent.DesignTools.UPDATE) &&
                result != null && !result.content.startsWith("FEHLER")
            ) io.github.thepytales.llmhub.agent.DesignTools.idIn(result.content)?.let { DesignCard(it) }
        }
        if (item.turnEnd && !(running && item.isLast) && evidence != null) EvidenceCard(evidence, onExport)
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
                // Helpful or not: stays on the device and shows under Verbrauch which models work well.
                if (text.isNotBlank()) {
                    val rating = item.head.rating
                    ActionIcon(if (rating == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, if (rating == 1) "Bewertung entfernen" else "Hilfreich") { onRate(if (rating == 1) null else 1) }
                    ActionIcon(if (rating == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, if (rating == -1) "Bewertung entfernen" else "Nicht hilfreich") { onRate(if (rating == -1) null else -1) }
                }
                var protocol by remember { mutableStateOf(false) }
                if (item.turn.any { it.durationMs != null }) ActionIcon(Icons.Outlined.Timeline, "Agent-Protokoll") { protocol = true }
                if (protocol) AgentProtocolDialog(item.turn, onDismiss = { protocol = false }, question = item.question, chatId = chatId)
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
                                text = { Text("Im Dokument-Editor öffnen") },
                                leadingIcon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                                onClick = { more = false; onOpenInEditor(text) },
                            )
                            DropdownMenuItem(
                                text = { Text("Literaturverzeichnis erstellen") },
                                leadingIcon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                                onClick = { more = false; onBibliography(text) },
                            )
                        }
                        if (text.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Quellen prüfen") },
                                leadingIcon = { Icon(Icons.Outlined.FactCheck, contentDescription = null) },
                                onClick = { more = false; checkSources = true },
                            )
                        }
                        if (text.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Zitieren und nachfragen") },
                                leadingIcon = { Icon(Icons.Outlined.FormatQuote, contentDescription = null) },
                                onClick = { more = false; onQuote(text) },
                            )
                        }
                        if (text.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(tr("Merken …")) },
                                leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
                                onClick = { more = false; onRemember(text) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Neuer Chat ab hier") },
                            leadingIcon = { Icon(Icons.Outlined.CallSplit, contentDescription = null) },
                            onClick = { more = false; onFork() },
                        )
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
                // Lora signs the answer next to the model that wrote it – and cheers for a moment when it is done.
                if (!io.github.thepytales.llmhub.App.container.settings.current.hideLora) LoraSignature(justDone)
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
            // Lora's next steps under the last answer (AP-35): deadlines, tables, literature, Word.
            if (item.isLast && !running && text.isNotBlank()) LoraNextSteps(io.github.thepytales.llmhub.App.container, text, chatId)
            // Stopped at the length limit: continue where it ended.
            if (item.isLast && !running && item.turn.ifEmpty { listOf(m) }.any { it.truncated }) {
                OutlinedButton(onClick = onContinue) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Weiterschreiben – die Antwort wurde abgeschnitten")
                }
            }
        }
    }
}

/**
 * Whether an assistant message still shows something when its steps are folded: an answer, an error, or a result
 * of a tool such as a document, a chart or a literature list (or the document being written).
 */
fun showsWithoutSteps(m: ChatMessage, results: Map<String?, ChatMessage>, running: Boolean): Boolean =
    m.error || splitThinking(m.content).second.isNotBlank() || m.toolCalls.any { call ->
        val r = results[call.id]
        when {
            r == null -> call.name == "create_document" && running
            r.file != null -> true
            r.content.startsWith("FEHLER") -> false
            call.name == io.github.thepytales.llmhub.agent.AskUserTool.NAME -> true
            else -> call.name == "create_chart" || call.name == "save_literature"
        }
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
/** Shown while the model writes a document: opens the editor where it grows. */
@Composable
private fun LiveDraftCard(chatId: String) {
    AccentBox {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.EditNote, size = 30.dp)
            Spacer(Modifier.size(10.dp))
            Text("Das Dokument entsteht gerade", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { DocsHandoff.show("chat:$chatId") }) { Text("Live ansehen") }
        }
    }
}

/** Works the literature search kept: opens the list in the collection. */
@Composable
private fun LiteratureListCard(list: String, onOpen: () -> Unit) {
    val c = io.github.thepytales.llmhub.App.container
    val all by c.literature.items.collectAsStateWithLifecycle()
    val works = all.filter { r -> r.lists.any { it.equals(list, true) } }
    AccentBox {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.MenuBook, size = 34.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Literaturliste „$list“", style = MaterialTheme.typography.titleSmall)
                    Text("${works.size} Werke · ${works.count { it.openAccessUrl.isNotBlank() }} frei zugänglich", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            works.take(4).forEach { r -> Text("• " + r.short(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 44.dp, top = 2.dp)) }
            if (works.size > 4) Text("… und ${works.size - 4} weitere", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 44.dp))
            TextButton(onClick = onOpen) { Text("In der Sammlung öffnen") }
        }
    }
}

internal fun toolLabel(name: String) = when (name) {
    "ask_user" -> "Rückfrage"
    "web_search" -> "Websuche"
    "news_search" -> "Nachrichtensuche"
    "fetch_url" -> "Webseite lesen"
    "create_document" -> "Dokument erstellen"
    "create_learning_game" -> "Lernspiel bauen"
    "search_files" -> "Dateien durchsuchen"
    "outlook_list_folders" -> "Outlook-Ordner ansehen"
    "outlook_list_mails" -> "Postfach lesen"
    "outlook_read_mail" -> "Mail lesen"
    "outlook_move_mails" -> "Mails verschieben"
    "outlook_create_folder" -> "Mail-Ordner anlegen"
    "outlook_save_draft" -> "Entwurf in Outlook speichern"
    "outlook_list_events" -> "Kalender ansehen"
    "outlook_create_event" -> "Termin eintragen"
    "outlook_create_task" -> "Aufgabe anlegen"
    "outlook_find_free_times" -> "Freie Zeiten suchen"
    "outlook_save_rule" -> "Sortierregel speichern"
    "outlook_apply_rules" -> "Sortierregeln anwenden"
    "update_learning_game" -> "Lernspiel ändern"
    "read_learning_game" -> "Lernspiel lesen"
    "create_design" -> "Design gestalten"
    "update_design" -> "Design ändern"
    "read_design" -> "Design ansehen"
    "save_logo" -> "Logo speichern"
    "read_file" -> "Datei lesen"
    "list_files" -> "Dateien auflisten"
    "list_local_files" -> "Ordner ansehen"
    "read_local_file" -> "Datei vom PC lesen"
    "search_local_files" -> "Ordner durchsuchen"
    "write_local_file", "edit_local_file" -> "Datei auf dem PC speichern"
    "move_local_file" -> "Datei verschieben"
    "delete_local_file" -> "Datei in den Papierkorb"
    "copy_document_to_folder" -> "Dokument in Ordner speichern"
    "edit_notebook_cell" -> "Notebook-Zelle ändern"
    "edit_office_file" -> "Office-Datei ändern"
    "set_excel_cells" -> "Excel-Zellen setzen"
    "transcribe_local_file" -> "Aufnahme transkribieren"
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
    "search_literature" -> "Literatur suchen"
    "literature_details" -> "Werk ansehen"
    "snowball_literature" -> "Schneeballsuche"
    "save_literature" -> "Literatur merken"
    "format_bibliography" -> "Literaturverzeichnis"
    "zotero_add" -> "Nach Zotero"
    "zotero_search" -> "Zotero durchsuchen"
    "read_document" -> "Dokument lesen"
    "reply_to_comment" -> "Kommentar beantworten"
    "create_chart" -> "Diagramm"
    "journal_new_articles" -> "Neue Artikel"
    "read_feed" -> "Feed lesen"
    "create_series" -> "Serienbrief"
    "create_semester_plan" -> "Semesterplan"
    "remember" -> "Merken"
    "forget" -> "Vergessen"
    "search_chats" -> "Frühere Chats durchsuchen"
    else -> name
}

/** Summary of several tool calls, e.g. „5 Werkzeuge genutzt · Websuche ×3, Webseite lesen ×2“. */
@Composable
private fun ToolGroupLine(names: List<String>, open: Boolean, onToggle: () -> Unit) {
    val counts = names.groupingBy { toolLabel(it) }.eachCount()
    val summary = counts.entries.joinToString(", ") { (k, v) -> if (v > 1) "$k ×$v" else k }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.clickable(onClickLabel = if (open) "Werkzeuge einklappen" else "Werkzeuge anzeigen", onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                "${names.size} ${tr("Werkzeuge genutzt")} · $summary",
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (open) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.labelMedium)
        }
    }
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
