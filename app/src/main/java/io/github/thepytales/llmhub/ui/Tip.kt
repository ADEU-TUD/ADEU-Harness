package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A short tooltip (AP-70): what a symbol does and its key, shown while the mouse is over it (long press on touch).
 * [text] is already translated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) { content() }
}

/**
 * A symbol button with its tooltip instead of a text label (toolbars that must not wrap, AP-70): [label] is the tooltip
 * and what screen readers say; [selected] makes it a switch that shows when it is on (tinted background).
 */
@Composable
fun TipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    tint: Color = Color.Unspecified,
    size: Dp = 36.dp,
) {
    Tip(label, modifier) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(size)
                .then(if (selected == true) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)) else Modifier)
                .then(if (selected != null) Modifier.semantics { this.selected = selected; role = Role.Button } else Modifier),
        ) {
            Icon(
                icon, contentDescription = label, modifier = Modifier.size((size.value * 0.55f).dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    tint != Color.Unspecified -> tint
                    selected == true -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
