package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Building blocks of the common design, used by the transcription and the newer screens. They follow the chosen
 * look (card color, frame and corner radius come from [LocalChatLook]).
 */

/** White card with the frame and corners of the current look. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalChatLook.current
    val colors = CardDefaults.cardColors(containerColor = look.card)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = look.cardShape, colors = colors, border = look.cardBorder, content = content)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = look.cardShape, colors = colors, border = look.cardBorder, content = content)
    }
}

/** Small uppercase heading above a group of cards ("HEUTE", "ERKENNUNGSMODELLE"). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    // Translated before the capitals, otherwise "HEUTE" would not be found in i18n/en.tsv (AP-29).
    val shown = tr(text)
    Text(
        shown.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.outline,
        // Read as written ("Heute", not "H-E-U-T-E") and as a heading (AP-30).
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp).semantics { heading(); contentDescription = shown },
    )
}

enum class PillTone { ACCENT, SUCCESS, WARNING, DANGER, NEUTRAL }

/** Small colored label such as "Empfohlen", "bereit" or "Nur lokal". */
@Composable
fun StatusPill(text: String, tone: PillTone = PillTone.ACCENT, modifier: Modifier = Modifier) {
    val look = LocalChatLook.current
    val color = when (tone) {
        PillTone.ACCENT -> MaterialTheme.colorScheme.primary
        PillTone.SUCCESS -> look.success
        PillTone.WARNING -> look.warning
        PillTone.DANGER -> MaterialTheme.colorScheme.error
        PillTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f), contentColor = color, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

/** Segmented control (two to four options side by side), e.g. "Auf diesem Gerät | Uni-Server". */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: (T) -> Boolean = { true }) {
    val look = LocalChatLook.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            val on = enabled(value)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) look.card else Color.Transparent)
                    .selectable(selected = sel, enabled = on, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tr(label),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        !on -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        sel -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    // Large text (Android up to 200 %) wraps instead of being cut off (AP-30).
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Name a screen reader reads for an element without visible text of its own, e.g. a switch beside its label (AP-30). */
fun Modifier.a11yLabel(text: String): Modifier = semantics { contentDescription = text }

/** Hint text below a field or group. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Title and subtitle stacked, for rows inside cards. */
@Composable
fun TitleBlock(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (!subtitle.isNullOrBlank()) HintText(subtitle)
    }
}

/** Read-only field that opens a menu of [options] (value to label). */
@Composable
fun <T> ChoiceField(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier) {
        androidx.compose.material3.OutlinedTextField(
            value = tr(options.firstOrNull { it.first == selected }?.second ?: ""),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(enabled = enabled) { open = true })
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(tr(l)) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}

/** Bytes as "670 MB" / "1,0 GB". */
fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f GB", b / 1e9)
    b >= 1_000_000 -> "${(b / 1e6).toInt()} MB"
    b >= 1000 -> "${(b / 1e3).toInt()} KB"
    else -> "$b B"
}

/*
 * ADEU style of the chat (AP-01): boxes in answers – code, tables, notes, document cards – have a fine frame and a
 * colored accent on the left instead of a gray surface. The frame follows the look (2 dp black in Hoher Kontrast).
 */

/** Frame of boxes in the chat. */
@Composable
fun boxBorder(): androidx.compose.foundation.BorderStroke =
    LocalChatLook.current.cardBorder ?: androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** A colored bar along the left edge (drawn behind the content, inside the box's clip). */
fun Modifier.accentStripe(color: Color, width: androidx.compose.ui.unit.Dp = 3.dp): Modifier = drawBehind {
    drawRect(color, size = androidx.compose.ui.geometry.Size(width.toPx(), size.height))
}

/** A box in the chat: card color, fine frame, accent bar; [onClick] makes the whole box tappable. */
@Composable
fun AccentBox(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Surface(
        color = LocalChatLook.current.card,
        border = boxBorder(),
        shape = shape,
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.accentStripe(accent).padding(start = 3.dp), content = content)
    }
}

/** Small square with an icon in the accent color on a light tint (file and document cards). */
@Composable
fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = MaterialTheme.colorScheme.primary, size: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.56f))
    }
}
