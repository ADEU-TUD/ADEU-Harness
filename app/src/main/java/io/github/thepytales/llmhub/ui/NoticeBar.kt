package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Hinweis-Banner (AP-123): errors, „… gelöscht – Rückgängig“ and results that arrive while the app is open show what
 * they are at a glance – a symbol in a tinted circle (red for errors, green for results, the accent for the rest), a
 * coloured edge, the action as a clear button and a ✕. Screen readers announce them (live region).
 */

enum class NoticeTone { INFO, ERROR, DONE }

/** What a banner shows (for SnackbarHostState.showSnackbar), with its [tone]. */
class NoticeVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
    val tone: NoticeTone = NoticeTone.INFO,
) : SnackbarVisuals

/** The banner of a notice of [Notices]. */
fun Notices.Notice.visuals(): NoticeVisuals = NoticeVisuals(
    message = text,
    actionLabel = action,
    withDismissAction = true,
    duration = if (error || action != null) SnackbarDuration.Long else SnackbarDuration.Short,
    tone = if (error) NoticeTone.ERROR else NoticeTone.INFO,
)

/** One banner: symbol, text, action and ✕ on the card colour, with an edge in the colour of its tone. */
@Composable
fun NoticeBar(data: SnackbarData, modifier: Modifier = Modifier) {
    val v = data.visuals
    val tone = (v as? NoticeVisuals)?.tone ?: NoticeTone.INFO
    val look = LocalChatLook.current
    val color: Color = when (tone) {
        NoticeTone.ERROR -> MaterialTheme.colorScheme.error
        NoticeTone.DONE -> look.success
        NoticeTone.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (tone) {
        NoticeTone.ERROR -> Icons.Outlined.ErrorOutline
        NoticeTone.DONE -> Icons.Outlined.CheckCircle
        NoticeTone.INFO -> Icons.Outlined.Info
    }
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = look.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = appFloatingBorder(),
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.accentStripe(color, 4.dp).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(
                v.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            v.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) { Text(label, fontWeight = FontWeight.SemiBold, color = if (tone == NoticeTone.ERROR) color else MaterialTheme.colorScheme.primary) }
            }
            if (v.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
