package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.FileAttachment


/** Small chip above the input that shows active context (repository, environment, persona). */
@Composable
fun ContextChip(icon: ImageVector, label: String, onClick: () -> Unit, highlighted: Boolean = true) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

/** Pictures that can be picked at once for one message (files have no limit on the number, see AttachmentReader). */
const val MAX_IMAGES = 10

/** Round button of the input card (attachments, microphone). */
@Composable
private fun RoundButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Input card like the Claude app: text on top, below "+" for attachments, the model, microphone and
 * a round send button.
 */
@Composable
fun Composer(
    input: String,
    onInput: (String) -> Unit,
    placeholder: String,
    modelLabel: String,
    onModel: () -> Unit,
    images: List<String>,
    files: List<FileAttachment>,
    loadingFiles: Int,
    onRemoveImage: (String) -> Unit,
    onRemoveFile: (FileAttachment) -> Unit,
    running: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /** Strg+V: returns true when it attached an image or files from the clipboard. */
    onPaste: () -> Boolean = { false },
    /** ✨: makes the typed request clearer before it is sent. */
    onImprove: (() -> Unit)? = null,
    improving: Boolean = false,
    /** Suggestions for /commands and @-mentions, shown above the input. */
    suggestions: @Composable () -> Unit = {},
    /** Small note under the input (the reminder that AI can make mistakes). */
    footnote: String? = null,
    chips: @Composable RowScope.() -> Unit,
) {
    val canSend = input.isNotBlank() || images.isNotEmpty() || files.isNotEmpty()
    val look = LocalChatLook.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun buzz() = haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
    ) {
        suggestions()
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = chips,
        )
        Surface(
            color = look.card,
            shape = look.inputShape,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
                if (images.isNotEmpty() || files.isNotEmpty() || loadingFiles > 0) {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 6.dp, end = 6.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        images.forEach { a -> Thumbnail(a, 60) { onRemoveImage(a) } }
                        files.forEach { f -> FileChip(f) { onRemoveFile(f) } }
                        if (loadingFiles > 0) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                TextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = {
                        Text(
                            if (recording) "Aufnahme läuft … zum Beenden auf das Mikrofon $TAP" else placeholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    maxLines = 8,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    // Windows: Enter sends, Umschalt+Enter starts a new line (like the Claude desktop app). On Android
                    // Enter stays a new line, also with a keyboard attached.
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                        when {
                            e.type != KeyEventType.KeyDown -> false
                            ENTER_SENDS && (e.key == Key.Enter || e.key == Key.NumPadEnter) && !e.isShiftPressed -> {
                                if (canSend && !running) { buzz(); onSend() }
                                true
                            }
                            e.key == Key.V && e.isCtrlPressed -> onPaste()
                            else -> false
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    RoundButton(onClick = onPlus) { Icon(Icons.Outlined.Add, contentDescription = "Hinzufügen") }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onModel),
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    modelLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Modell wählen", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (onImprove != null && input.isNotBlank() && !running) {
                        Spacer(Modifier.width(8.dp))
                        RoundButton(onClick = onImprove, enabled = !improving) {
                            if (improving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.AutoAwesome, contentDescription = "Anfrage verbessern")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    RoundButton(onClick = { buzz(); onMic() }, enabled = !transcribing) {
                        when {
                            transcribing -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            recording -> Icon(Icons.Outlined.StopCircle, contentDescription = "Aufnahme beenden", tint = MaterialTheme.colorScheme.error)
                            else -> Icon(Icons.Outlined.Mic, contentDescription = "Spracheingabe")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val active = running || canSend
                    Surface(
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(enabled = active, onClick = { buzz(); if (running) onStop() else onSend() }),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (running) Icon(Icons.Filled.Stop, contentDescription = "Stopp")
                            else Icon(Icons.Filled.ArrowUpward, contentDescription = "Senden")
                        }
                    }
                }
            }
        }
        footnote?.let {
            Text(
                it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SheetTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
            }
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    )
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
    )
}

/** Sheet behind the "+" button: attachments and chat settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSheet(
    /** Null hides the repository row (no valid GitHub token). */
    repoLabel: String?,
    envLabel: String,
    personaLabel: String,
    /** Windows: paste from the clipboard (screenshot, copied files). */
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onTemplates: () -> Unit,
    onPersona: () -> Unit,
    onRepo: () -> Unit,
    onEnvironment: () -> Unit,
    onDismiss: () -> Unit,
    onFromApp: () -> Unit = {},
    onSnippets: () -> Unit = {},
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun pick(action: () -> Unit) {
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        SheetLabel("Anhängen")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTile(CameraTile.icon, CameraTile.label, { pick(onCamera) }, Modifier.weight(1f))
            SheetTile(Icons.Outlined.Image, PHOTOS_LABEL, { pick(onPhotos) }, Modifier.weight(1f))
            SheetTile(Icons.Outlined.AttachFile, "Dateien", { pick(onFiles) }, Modifier.weight(1f))
        }
        Text(
            "PDF, Word, PowerPoint, Excel, Text- und Code-Dateien werden gelesen und mitgeschickt.$ATTACH_HINT",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SheetRow(Icons.Outlined.Apps, "Aus der App", "Transkript, Dokument, Literaturliste oder erzeugtes Bild", { pick(onFromApp) })
        SheetLabel("Starten mit")
        SheetRow(Icons.Outlined.Bookmarks, "Vorlage", "Brief, Seminarplan, Gutachten, OPAL-Test … mit Formular", { pick(onTemplates) })
        SheetRow(Icons.Outlined.ShortText, "Kurztexte", "Eigene Textbausteine einfügen – auch mit /Name", { pick(onSnippets) })
        SheetLabel("Für diesen Chat")
        SheetRow(Icons.Outlined.Person, "Persona / Anweisungen", personaLabel, { pick(onPersona) })
        if (repoLabel != null) SheetRow(Icons.Outlined.Code, "GitHub-Repository", repoLabel, { pick(onRepo) })
        SheetRow(Icons.Outlined.Tune, "Umgebung", envLabel, { pick(onEnvironment) })
        Spacer(Modifier.height(24.dp))
    }
}
