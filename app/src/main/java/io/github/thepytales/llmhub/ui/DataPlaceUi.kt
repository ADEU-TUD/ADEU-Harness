package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.DataPlace

/** Colours of the data places (AP-119): text/dot and background, for light and dark surfaces. */
private fun DataPlace.colors(dark: Boolean): Pair<Color, Color> = when (this) {
    DataPlace.LOCAL -> if (dark) Color(0xFF81C995) to Color(0x3381C995) else Color(0xFF1B6E33) to Color(0xFFDDF3E2)
    DataPlace.TU -> if (dark) Color(0xFFFDD663) to Color(0x33FDD663) else Color(0xFF7A5900) to Color(0xFFFFF1C2)
    DataPlace.CLOUD -> if (dark) Color(0xFFF28B82) to Color(0x33F28B82) else Color(0xFFB3261E) to Color(0xFFFCE1DF)
}

/**
 * „lokal“ (green), „TU-Server“ (yellow), „Cloud“ (red) next to a model or server (AP-119): where its data goes. The
 * word is always there (colour alone is not enough), screen readers hear the full sentence.
 */
@Composable
fun DataPlaceChip(place: DataPlace, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (fg, bg) = place.colors(dark)
    val sentence = tr(place.explanation)
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = modifier.semantics { contentDescription = tr(place.label) + ": " + sentence }) {
        Row(Modifier.padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(7.dp).background(fg, CircleShape))
            Text(tr(place.label), style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

/** Where the chat model [id] runs; „TU-Server“ when the app is not fully set up yet (tests, previews). */
fun dataPlaceOf(id: String): DataPlace = runCatching { DataPlace.ofModel(io.github.thepytales.llmhub.App.container, id) }.getOrDefault(DataPlace.TU)
