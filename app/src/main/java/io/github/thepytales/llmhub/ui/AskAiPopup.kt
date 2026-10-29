package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Quick requests of the „KI fragen“ window (label, request). */
private val ASK_QUICK = listOf(
    "Verbessern" to "Verbessere Stil und Verständlichkeit.",
    "Kürzen" to "Kürze auf das Wesentliche.",
    "Erklären" to "Erkläre kurz, was hier gemeint ist (als Antwort, nicht als Ersatz).",
    "Förmlicher" to "Formuliere förmlicher, wie in einem Schreiben der Universität.",
    "Englisch" to "Übersetze ins Englische.",
)

/** What the model gets for a request from the „KI fragen“ window. */
fun askInstruction(request: String) =
    "Bitte des Nutzers zur markierten Stelle: „${request.trim()}“. Soll die Stelle geändert werden, gib nur den neuen Text " +
        "für die Stelle zurück. Ist es eine Frage zur Stelle, beantworte sie knapp (der Nutzer entscheidet, ob er die Antwort einfügt)."

/**
 * The „KI fragen“ window above a text selection (like the ✨ bubble of Google Docs): a field for the request, send, and a
 * few quick requests. [target] is the selection in window pixels. It does not take the keyboard until it is clicked
 * (or [active] from the start, e.g. on phones where it opens from a button), so selecting with Shift+arrows goes on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskAiPopup(target: IntRect, active: Boolean = false, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var focused by remember { mutableStateOf(active) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    val provider = remember(target, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = target.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = target.top - popupContentSize.height - gap
                val y = if (above >= 0) above else (target.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
                return IntOffset(x, y)
            }
        }
    }
    fun send(request: String) {
        if (request.isBlank()) return
        onSend(request.trim())
        text = ""
    }
    LaunchedEffect(focused) { if (focused) runCatching { focus.requestFocus() } }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = focused)) {
        Surface(
            shape = RoundedCornerShape(14.dp), shadowElevation = 10.dp, tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.width(400.dp).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type == PointerEventType.Press) focused = true
                    }
                }
            },
        ) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("KI fragen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, contentDescription = "Schließen (Esc)", modifier = Modifier.size(16.dp)) }
                }
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                            var field by rememberCursorValue(text)
                            BasicTextField(
                                value = field, onValueChange = { field = it; text = it.text }, maxLines = 5,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when {
                                        e.key == Key.Enter && !e.isShiftPressed -> { send(text); true }
                                        e.key == Key.Enter -> { field = field.withLineBreak(); text = field.text; true }
                                        e.key == Key.Escape -> { onDismiss(); true }
                                        else -> false
                                    }
                                },
                                decorationBox = { inner ->
                                    if (text.isEmpty()) Text("Was soll die KI mit der Markierung tun?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                    inner()
                                },
                            )
                        }
                    }
                    IconButton(onClick = { send(text) }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Absenden (Enter)", tint = MaterialTheme.colorScheme.primary) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ASK_QUICK.forEach { (label, request) -> AssistChip(onClick = { send(request) }, label = { Text(label) }) }
                }
            }
        }
    }
}
