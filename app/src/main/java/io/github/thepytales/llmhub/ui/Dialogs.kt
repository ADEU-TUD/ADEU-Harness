package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/*
 * Dialoge aus einem Guss (AP-123). Same name and parameters as Material's AlertDialog, so every dialog of the app gets
 * this without being touched:
 * - the confirm button is the filled main button, the way out (dismiss) is neutral; a red [DangerButton] (or a red
 *   text button) marks what deletes,
 * - Enter starts the main action (also from a one-line field), Ctrl+Enter also from a longer text, Esc closes – but
 *   Enter never starts a red action and never chooses between two main buttons,
 * - on the PC the first field gets the cursor at once,
 * - card colour, frame and corners of the style; an icon sits in a small tinted circle beside the title.
 */

/** What the keyboard of a dialog can reach: its main actions and its fields. */
internal class DialogKeys {
    class Action(var onClick: () -> Unit, var enabled: Boolean, var danger: Boolean)

    val actions = mutableListOf<Action>()
    val fields = mutableListOf<FocusRequester>()

    /** The action Enter starts: the only main button, if it is enabled and not red. */
    fun enterAction(): Action? = actions.singleOrNull()?.takeIf { it.enabled && !it.danger }
}

internal val LocalDialogKeys = staticCompositionLocalOf<DialogKeys?> { null }

/** A button in a dialog's confirm slot tells the dialog what Enter does. */
@Composable
internal fun DialogAction(onClick: () -> Unit, enabled: Boolean, danger: Boolean) {
    val keys = LocalDialogKeys.current ?: return
    if (LocalDialogSlot.current != DialogSlot.CONFIRM) return
    val action = remember { DialogKeys.Action(onClick, enabled, danger) }
    SideEffect {
        action.onClick = onClick
        action.enabled = enabled
        action.danger = danger
    }
    DisposableEffect(keys) {
        keys.actions += action
        onDispose { keys.actions -= action }
    }
}

/**
 * A field in a dialog's text: the first one gets the cursor when the dialog opens (PC). Enter in a one-line field
 * starts the dialog's main action – unless the field has its own (keyboard actions).
 */
@Composable
internal fun Modifier.dialogField(editable: Boolean, singleLine: Boolean, ownEnter: Boolean): Modifier {
    val keys = LocalDialogKeys.current ?: return this
    if (!editable || LocalDialogSlot.current != DialogSlot.NONE) return this
    val requester = remember { FocusRequester() }
    DisposableEffect(keys) {
        keys.fields += requester
        onDispose { keys.fields -= requester }
    }
    val enter = if (singleLine && !ownEnter) Modifier.onPreviewKeyEvent { e ->
        val run = e.type == KeyEventType.KeyDown && isEnter(e.key) && !e.isShiftPressed && !e.isAltPressed
        run && keys.enterAction()?.let { it.onClick(); true } == true
    } else Modifier
    return focusRequester(requester).then(enter)
}

private fun isEnter(key: Key) = key == Key.Enter || key == Key.NumPadEnter

/** Dialog with title, text and buttons (Material's parameters, the app's look and keys). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = LocalChatLook.current.card,
    iconContentColor: Color = MaterialTheme.colorScheme.primary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        val keys = remember { DialogKeys() }
        val root = remember { FocusRequester() }
        var inside by remember { mutableStateOf(false) }
        // Nothing focused after the first frames (no field asked for it): the first field, else the dialog itself –
        // so Enter and Esc work at once.
        LaunchedEffect(Unit) {
            withFrameNanos {}
            withFrameNanos {}
            if (inside) return@LaunchedEffect
            val field = keys.fields.firstOrNull()?.takeIf { !IS_ANDROID }
            if (field == null || !runCatching { field.requestFocus() }.isSuccess) runCatching { root.requestFocus() }
        }
        Surface(
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
            shadowElevation = 8.dp,
            border = appFloatingBorder(),
        ) {
            Column(
                Modifier
                    .onPreviewKeyEvent { e ->
                        // Ctrl+Enter: the main action even from a field with several lines.
                        val run = e.type == KeyEventType.KeyDown && isEnter(e.key) && (e.isCtrlPressed || e.isMetaPressed)
                        run && keys.enterAction()?.let { it.onClick(); true } == true
                    }
                    .onKeyEvent { e ->
                        // Enter that no field or focused button used.
                        val run = e.type == KeyEventType.KeyDown && isEnter(e.key) && !e.isShiftPressed && !e.isAltPressed
                        run && keys.enterAction()?.let { it.onClick(); true } == true
                    }
                    .focusRequester(root)
                    .onFocusChanged { inside = it.hasFocus }
                    .focusable()
                    .padding(24.dp),
            ) {
                CompositionLocalProvider(LocalDialogKeys provides keys) {
                    if (icon != null || title != null) {
                        Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (icon != null) {
                                Box(
                                    Modifier.padding(end = 14.dp).size(40.dp).clip(CircleShape).background(iconContentColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides iconContentColor) { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() } }
                                }
                            }
                            if (title != null) {
                                CompositionLocalProvider(
                                    LocalContentColor provides titleContentColor,
                                    LocalTextStyle provides MaterialTheme.typography.titleLarge,
                                ) { Box(Modifier.weight(1f, fill = false)) { title() } }
                            }
                        }
                    }
                    if (text != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides textContentColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                        ) { Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) { text() } }
                    }
                    DialogButtons(dismissButton, confirmButton, Modifier.align(Alignment.End))
                }
            }
        }
    }
}

/**
 * The buttons of a dialog: side by side at the right (the way out, then the main action) as long as they fit in one
 * line – the dialog grows for that up to its largest width. Only when they do not fit even then (small window, phone,
 * large text) they stand below each other in full width, the main action on top.
 */
@Composable
private fun DialogButtons(dismiss: (@Composable () -> Unit)?, confirm: @Composable () -> Unit, modifier: Modifier) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
        Layout(
            modifier = modifier,
            content = {
                // propagateMinConstraints: in the stacked form the button inside takes the full width.
                Box(propagateMinConstraints = true) { CompositionLocalProvider(LocalDialogSlot provides DialogSlot.CONFIRM) { confirm() } }
                if (dismiss != null) Box(propagateMinConstraints = true) { CompositionLocalProvider(LocalDialogSlot provides DialogSlot.DISMISS) { dismiss() } }
            },
        ) { measurables, constraints ->
            val gap = 8.dp.roundToPx()
            val wanted = measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) } + gap * (measurables.size - 1)
            if (wanted <= constraints.maxWidth) {
                // Side by side: the way out left of the main action.
                val placed = measurables.map { it.measure(Constraints()) }.reversed()
                val height = placed.maxOf { it.height }
                val width = placed.sumOf { it.width } + gap * (placed.size - 1)
                layout(width, height) {
                    var x = 0
                    placed.forEach {
                        it.placeRelative(x, (height - it.height) / 2)
                        x += it.width + gap
                    }
                }
            } else {
                val width = constraints.maxWidth
                val placed = measurables.map { it.measure(Constraints.fixedWidth(width)) }
                val height = placed.sumOf { it.height } + gap * (placed.size - 1)
                layout(width, height) {
                    var y = 0
                    placed.forEach {
                        it.placeRelative(0, y)
                        y += it.height + gap
                    }
                }
            }
        }
    }
}

/** Labels of actions that delete or cannot be undone (German and English, already translated). */
private val DANGER_WORDS = listOf("lösch", "entfern", "zurücksetz", "verwerf", "leeren", "delete", "remove", "reset", "discard", "empty")

/** Whether [label] names an action that deletes or cannot be undone (red button, no Enter). */
internal fun isDangerLabel(label: String): Boolean = label.lowercase().let { l -> DANGER_WORDS.any { it in l } }

/** Whether [label] deletes something (the trash can; other red actions get a warning sign). */
internal fun isDeleteLabel(label: String): Boolean = label.lowercase().let { l -> listOf("lösch", "papierkorb", "delete", "trash").any { it in l } }

/**
 * Asks before an action. Deleting, removing, resetting and discarding get a red button and a symbol, and Enter does not
 * start them; [dismiss] names the way out when „Abbrechen“ would be ambiguous (e.g. „Transkription abbrechen?“).
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = tr("Abbrechen"),
    danger: Boolean = isDangerLabel(confirm),
) {
    val s = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val symbol = when {
                isDeleteLabel(confirm) -> Icons.Outlined.DeleteOutline
                danger -> Icons.Outlined.WarningAmber
                else -> Icons.AutoMirrored.Outlined.HelpOutline
            }
            Icon(symbol, contentDescription = null)
        },
        iconContentColor = if (danger) s.error else s.primary,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            if (danger) DangerButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) }
            else Button(onClick = { onConfirm(); onDismiss() }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

/**
 * Dialog with one text field (rename, new folder …). The cursor is in the field at once and the text is selected – for
 * a file name without its ending, as in the Explorer –, Enter saves.
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirm: String = "Speichern",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, nameSelection(initial))) }
    val focus = remember { FocusRequester() }
    // Also on Android (keyboard up): renaming means typing. After the first frame, when the field is in place.
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { Button(enabled = value.text.isNotBlank(), onClick = { onConfirm(value.text.trim()); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}

/** What is selected when renaming [name]: all of it, a file name without its ending („Bericht“ of „Bericht.docx“). */
internal fun nameSelection(name: String): TextRange {
    val dot = name.lastIndexOf('.')
    val ending = if (dot > 0) name.substring(dot + 1) else ""
    val isEnding = ending.length in 1..5 && ending.all { it.isLetterOrDigit() } && ending.any { it.isLetter() }
    return TextRange(0, if (isEnding) dot else name.length)
}
