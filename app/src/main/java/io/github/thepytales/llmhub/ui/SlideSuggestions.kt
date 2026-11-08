package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.agent.SlidesLora
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.SlideLook

/** The open slide editor's „Folie schöner machen“, for Lora's action slides.beautify (null when no deck is open). */
object SlideSuggestionsHost {
    @Volatile
    var open: ((Int) -> Unit)? = null
}

/**
 * „Folie schöner machen“ (AP-51): suggestions for slide [index] as small slides to tap – at once the ones from fixed
 * rules ([SlideLook]), then the model's variants when one is allowed. Tapping replaces the slide ([onPick]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlideSuggestionsDialog(c: AppContainer, slides: List<SlideDeck.Slide>, index: Int, frame: DeckFrame, onPick: (List<SlideDeck.Slide>) -> Unit, onDismiss: () -> Unit) {
    val s = slides[index]
    val rules = remember(s) { SlideLook.suggest(s) }
    val model = remember { ToolActions.model(c) }
    var fromModel by remember(s) { mutableStateOf<List<SlideLook.Suggestion>>(emptyList()) }
    var thinking by remember(s) { mutableStateOf(model != null) }
    LaunchedEffect(s) {
        if (model == null) return@LaunchedEffect
        fromModel = runCatching { SlidesLora.modelSuggestions(c, model, s) }.getOrNull().orEmpty()
        thinking = false
    }
    val all = fromModel + rules
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Schließen")) } },
        title = { Text(tr("Folie schöner machen")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (model == null) tr("Antippen übernimmt den Vorschlag. Ohne Modell kommen die Vorschläge aus festen Regeln.") else tr("Antippen übernimmt den Vorschlag."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (thinking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(tr("Lora entwirft eigene Varianten …"), style = MaterialTheme.typography.bodySmall)
                }
                if (all.isEmpty() && !thinking) Text(tr("Für diese Folie habe ich keinen Vorschlag – sie sieht schon gut aus."))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    all.forEach { sug ->
                        Surface(
                            onClick = { onPick(sug.slides) }, shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.width(220.dp),
                        ) {
                            Column(Modifier.padding(6.dp)) {
                                SlideCanvas(sug.slides.first(), Modifier.fillMaxWidth().aspectRatio(16f / 9f), index + 1, slides.size, frame, slides)
                                Text(
                                    tr(sug.label) + if (sug.slides.size > 1) " · " + tr("{0} Folien", sug.slides.size) else "",
                                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
