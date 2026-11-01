package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.creative.renderDesignPage
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** A design to open in the editor (from „Erstellen“ or a chat card); the app root navigates there. */
object DesignHandoff {
    val request = MutableStateFlow<String?>(null)
}

/** Preview of the first (or [page]) page of [design], rendered in the background. */
@Composable
fun DesignThumbnail(design: Design, modifier: Modifier = Modifier, page: Int = 0, widthPx: Int = 360) {
    val bmp by produceState<ImageBitmap?>(null, design.id, design.updatedAt, page, widthPx) {
        value = withContext(Dispatchers.Default) { runCatching { renderDesignPage(design, page, widthPx) }.getOrNull() }
    }
    Box(
        modifier.aspectRatio(design.width / design.height.coerceAtLeast(1f)).background(Color(0xFFF2F4F7)).border(0.5.dp, Color(0x22000000)),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it, contentDescription = design.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
    }
}

/** The card under a chat answer that made or changed a design (AP-24): preview and „Bearbeiten“. */
@Composable
internal fun DesignCard(id: String) {
    val c = io.github.thepytales.llmhub.App.container
    val designs by c.designs.items.collectAsStateWithLifecycle()
    val d = designs.firstOrNull { it.id == id } ?: return
    Surface(shape = MaterialTheme.shapes.medium, color = LocalChatLook.current.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DesignThumbnail(d, Modifier.width(if (d.width >= d.height) 160.dp else 96.dp), widthPx = 320)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(DesignFormat.byId(d.format)?.label, if (d.pages.size > 1) "${d.pages.size} " + tr("Seiten") else null, tr("unter „Erstellen“")).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { DesignHandoff.request.value = d.id }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Bearbeiten"))
                }
            }
        }
    }
}
