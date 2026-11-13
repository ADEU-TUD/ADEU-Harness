package io.github.thepytales.llmhub.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/*
 * Bedienelemente aus einem Guss (AP-123). Buttons, symbol buttons, icons, switches, menus and text fields of the whole
 * app, with the same names and parameters as in androidx.compose.material3 – screens of this package get them without
 * an import, like Text (ui/TextI18n.kt) and the chips (ui/Chips.kt); ControlsLookTest keeps the material ones out.
 *
 * What they add to Material 3:
 * - Buttons take the corners of the chosen style (round in ADEU, small in TU Dresden, Office and Hoher Kontrast) and
 *   give a little when pressed. A secondary (outlined) button sits on the card colour with a calm frame.
 * - In a dialog (ui/Dialogs.kt) the confirm button is always the filled main button and the way out stays neutral –
 *   without touching the dialogs one by one. A red [DangerButton] marks what deletes or cannot be undone.
 * - A symbol button shows its icon's description as a tooltip while the mouse rests on it (PC), so no symbol stays a
 *   riddle.
 * - Switches show a check mark when on, menus have the corners, card colour and frame of the style, text fields sit on
 *   the card colour and password fields get an eye to show what was typed.
 */

/** Where a button sits in an [AlertDialog]: the main action, the way out, or anywhere else. */
internal enum class DialogSlot { NONE, CONFIRM, DISMISS }

internal val LocalDialogSlot = staticCompositionLocalOf { DialogSlot.NONE }

/** Collects the description of the icon inside an [IconButton] (its tooltip). */
@Stable
internal class IconLabel {
    var text by mutableStateOf<String?>(null)
}

internal val LocalIconLabel = staticCompositionLocalOf<IconLabel?> { null }

/** True inside a [Tip]: the button there already has its tooltip. */
internal val LocalInTip = staticCompositionLocalOf { false }

/** Gives a little while pressed (97 %), like a real key. */
@Composable
private fun Modifier.pressFeel(source: InteractionSource): Modifier {
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.97f else 1f, tween(if (down) 70 else 160), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Content colour that stays readable on [fill] (for buttons in a custom colour). */
@Composable
private fun readableOn(fill: Color): Color {
    val s = MaterialTheme.colorScheme
    return when (fill) {
        s.primary -> s.onPrimary
        s.error -> s.onError
        s.secondary -> s.onSecondary
        s.tertiary -> s.onTertiary
        else -> if (fill.luminance() > 0.45f) Color(0xFF111111) else Color.White
    }
}

/** Frame of a secondary button: calmer than Material's outline, 2 dp and strong in Hoher Kontrast. */
@Composable
internal fun appOutlineBorder(enabled: Boolean = true): BorderStroke {
    val s = MaterialTheme.colorScheme
    val strong = (LocalChatLook.current.cardBorder?.width ?: 0.dp) >= 2.dp
    val color = when {
        !enabled -> s.onSurface.copy(alpha = 0.12f)
        strong -> s.outline
        else -> lerp(s.outlineVariant, s.outline, 0.55f)
    }
    return BorderStroke(if (strong) 2.dp else 1.dp, color)
}

/** Filled main button. In a dialog's confirm slot it is the action Enter starts. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = LocalChatLook.current.buttonShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    DialogAction(onClick, enabled, danger = colors.containerColor == MaterialTheme.colorScheme.error)
    androidx.compose.material3.Button(
        onClick = onClick, modifier = modifier.pressFeel(source), enabled = enabled, shape = shape, colors = colors, elevation = elevation,
        border = border, contentPadding = contentPadding, interactionSource = source, content = content,
    )
}

/**
 * Button for what deletes, removes or cannot be undone: filled red. In a dialog Enter does not start it – it needs a
 * click (or Tab and then Enter).
 */
@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Button(onClick, modifier, enabled, colors = ButtonDefaults.buttonColors(containerColor = s.error, contentColor = s.onError), content = content)
}

/**
 * Text button. In a dialog's confirm slot it becomes the filled main button (in its own colour if it has one, e.g. red);
 * as the way out of a dialog it is neutral instead of in the accent colour.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = LocalChatLook.current.buttonShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val plain = colors == ButtonDefaults.textButtonColors()
    when (LocalDialogSlot.current) {
        DialogSlot.CONFIRM -> {
            val fill = if (plain) ButtonDefaults.buttonColors()
            else ButtonDefaults.buttonColors(containerColor = colors.contentColor, contentColor = readableOn(colors.contentColor))
            val padding = if (contentPadding == ButtonDefaults.TextButtonContentPadding) ButtonDefaults.ContentPadding else contentPadding
            Button(
                onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = fill, elevation = ButtonDefaults.buttonElevation(),
                border = border, contentPadding = padding, interactionSource = interactionSource, content = content,
            )
        }
        DialogSlot.DISMISS -> {
            val neutral = if (plain) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface) else colors
            androidx.compose.material3.TextButton(
                onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = neutral, elevation = elevation, border = border,
                contentPadding = contentPadding, interactionSource = interactionSource, content = content,
            )
        }
        DialogSlot.NONE -> androidx.compose.material3.TextButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border,
            contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
    }
}

/** Secondary button: card colour, calm frame, accent text. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = LocalChatLook.current.buttonShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(containerColor = LocalChatLook.current.card),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = appOutlineBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shown = if (enabled) colors else colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier.pressFeel(source), enabled = enabled, shape = shape, colors = shown, elevation = elevation,
        border = border, contentPadding = contentPadding, interactionSource = source, content = content,
    )
}

/** Tinted button for a second action that should still stand out. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = LocalChatLook.current.buttonShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick, modifier = modifier.pressFeel(source), enabled = enabled, shape = shape, colors = colors, elevation = elevation,
        border = border, contentPadding = contentPadding, interactionSource = source, content = content,
    )
}

/**
 * Symbol button. On the PC it shows the description of its icon as a tooltip while the mouse rests on it (not again
 * after a click until the mouse has left); inside a [Tip] the tip's text is used instead.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (IS_ANDROID || LocalInTip.current) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, interactionSource = interactionSource) {
            CompositionLocalProvider(LocalIconLabel provides null, content = content)
        }
        return
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val label = remember { IconLabel() }
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, interactionSource = source) {
        CompositionLocalProvider(LocalIconLabel provides label, content = content)
        HoverTip(source, label.text)
    }
}

/** The tooltip of a symbol button: after half a second of rest, not while or after pressing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoverTip(source: InteractionSource, text: String?) {
    if (text.isNullOrBlank()) return
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    var used by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) { if (pressed) used = true }
    LaunchedEffect(hovered) { if (!hovered) used = false }
    LaunchedEffect(hovered, used) {
        show = false
        if (hovered && !used) {
            delay(TIP_DELAY_MS)
            show = true
        }
    }
    if (show) {
        Popup(popupPositionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), properties = PopupProperties(focusable = false)) {
            TipBubble(text)
        }
    }
}

/** How long the mouse rests on a symbol before its tooltip appears. */
internal const val TIP_DELAY_MS = 500L

/** Small dark label of a tooltip (like Material's plain tooltip). */
@Composable
internal fun TipBubble(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier.widthIn(max = 280.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** Material's icon; inside an [IconButton] its description becomes the button's tooltip. */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    ReportIconLabel(contentDescription)
    androidx.compose.material3.Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
fun Icon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    ReportIconLabel(contentDescription)
    androidx.compose.material3.Icon(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    ReportIconLabel(contentDescription)
    androidx.compose.material3.Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
private fun ReportIconLabel(text: String?) {
    val sink = LocalIconLabel.current ?: return
    SideEffect { if (sink.text != text) sink.text = text }
}

private val SwitchCheck: @Composable () -> Unit = {
    androidx.compose.material3.Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
}

/** Material's switch with a check mark on the thumb while it is on – on/off is clear without colour. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = if (checked) SwitchCheck else null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.Switch(
    checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, thumbContent = thumbContent, enabled = enabled, colors = colors,
    interactionSource = interactionSource,
)

/** Frame of menus and dialogs: the card frame of the style, a hairline where the style has none. */
@Composable
internal fun appFloatingBorder(): BorderStroke =
    LocalChatLook.current.cardBorder ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** Material's menu with the corners, card colour and frame of the style and a soft shadow. */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = LocalChatLook.current.card,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 6.dp,
    border: BorderStroke? = appFloatingBorder(),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.DropdownMenu(
    expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset, scrollState = scrollState,
    properties = properties, shape = shape, containerColor = containerColor, tonalElevation = tonalElevation,
    shadowElevation = shadowElevation, border = border,
) {
    // Entries of a menu opened from a dialog's button are no main buttons, and their icons are no tooltips.
    CompositionLocalProvider(LocalDialogSlot provides DialogSlot.NONE, LocalIconLabel provides null) { content() }
}

/** Fields sit on the card colour, so they read as fields on the grey page too. */
@Composable
internal fun appFieldColors(): TextFieldColors {
    val card = LocalChatLook.current.card
    return OutlinedTextFieldDefaults.colors(focusedContainerColor = card, unfocusedContainerColor = card, errorContainerColor = card)
}

/** A password field without its own symbol gets an eye that shows and hides what was typed. */
@Composable
private fun passwordEye(visualTransformation: VisualTransformation, trailingIcon: @Composable (() -> Unit)?): Pair<VisualTransformation, @Composable (() -> Unit)?> {
    if (visualTransformation !is PasswordVisualTransformation || trailingIcon != null) return visualTransformation to trailingIcon
    var shown by remember { mutableStateOf(false) }
    val eye: @Composable () -> Unit = {
        IconButton(onClick = { shown = !shown }) {
            Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = tr(if (shown) "Verbergen" else "Anzeigen"))
        }
    }
    return (if (shown) VisualTransformation.None else visualTransformation) to eye
}

/** Material's outlined text field on the card colour; in a dialog the first one gets the cursor on the PC. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = appFieldColors(),
) {
    val (shown, trailing) = passwordEye(visualTransformation, trailingIcon)
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.dialogField(enabled && !readOnly, singleLine, ownEnter = keyboardActions != KeyboardActions.Default),
        enabled = enabled, readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailing, prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
        visualTransformation = shown, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = shape, colors = colors,
    )
}

@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = appFieldColors(),
) {
    val (shown, trailing) = passwordEye(visualTransformation, trailingIcon)
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.dialogField(enabled && !readOnly, singleLine, ownEnter = keyboardActions != KeyboardActions.Default),
        enabled = enabled, readOnly = readOnly, textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailing, prefix = prefix, suffix = suffix, supportingText = supportingText, isError = isError,
        visualTransformation = shown, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, interactionSource = interactionSource, shape = shape, colors = colors,
    )
}

/** Colours of a menu entry that deletes („Löschen“, „In den Papierkorb“): text and symbol in red. */
@Composable
fun dangerMenuColors(): MenuItemColors {
    val red = MaterialTheme.colorScheme.error
    return MenuDefaults.itemColors(textColor = red, leadingIconColor = red, trailingIconColor = red)
}
