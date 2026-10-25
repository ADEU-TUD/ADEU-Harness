package io.github.thepytales.llmhub.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
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
    ) {
        options.forEach { (value, label) ->
            val sel = value == selected
            val on = enabled(value)
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) look.card else Color.Transparent)
                    .clickable(enabled = on) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        !on -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        sel -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

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
            value = options.firstOrNull { it.first == selected }?.second ?: "",
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
                androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
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
