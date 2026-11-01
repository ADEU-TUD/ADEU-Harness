package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.agent.ChatMode

/** Icon of a mode (agent/ChatModes keeps only a key, so it stays free of the interface). */
fun ChatMode.iconVector(): ImageVector = when (icon) {
    "search" -> Icons.Outlined.ManageSearch
    "plan" -> Icons.Outlined.EventNote
    "voice" -> Icons.Outlined.GraphicEq
    "sources" -> Icons.Outlined.FactCheck
    "excerpt" -> Icons.Outlined.MenuBook
    "games" -> Icons.Outlined.SportsEsports
    "mail" -> Icons.Outlined.Mail
    "design" -> Icons.Outlined.Palette
    else -> Icons.Outlined.TipsAndUpdates
}

/**
 * The frame of every mode above the input (AP-05): a chip with the name and ✕, below it one line „what happens here ·
 * Daten: …“.
 */
@Composable
fun ModeBanner(mode: ChatMode, onClose: () -> Unit) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(mode.iconVector(), contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr(mode.name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClickLabel = tr("Modus verlassen"), onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, contentDescription = tr("Modus verlassen"), modifier = Modifier.size(16.dp)) }
            }
        }
        Text(
            tr(mode.explain) + " · " + tr("Daten:") + " " + tr(mode.data),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, top = 3.dp),
        )
    }
}

/** In an empty chat with a mode: its example to tap (fills the input). */
@Composable
fun ModeExample(mode: ChatMode, onUse: (String) -> Unit) {
    AccentBox(Modifier.widthIn(max = 520.dp), onClick = { onUse(mode.example) }) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(mode.iconVector(), size = 34.dp)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(tr(mode.name) + " · " + tr("Beispiel zum Antippen"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(tr(mode.example), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
