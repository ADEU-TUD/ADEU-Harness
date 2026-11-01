package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Replay30
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.onClick
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.audio.TranscriptPlayer
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.transcribe.InlineMark
import io.github.thepytales.llmhub.transcribe.SearchHit
import io.github.thepytales.llmhub.transcribe.Segment
import io.github.thepytales.llmhub.transcribe.TextWord
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.transcribe.TranscriptEdit
import io.github.thepytales.llmhub.transcribe.TranscriptExport

/** Parts of the transcript view: text layout of the passages, player bar and the passage dialog. */

/** Text size of the transcript (Ansicht › Schriftgröße). */
@Composable
internal fun transcriptTextStyle(size: String): TextStyle {
    val base = MaterialTheme.typography.bodyLarge
    return when (size) {
        "small" -> MaterialTheme.typography.bodyMedium
        "large" -> base.copy(fontSize = base.fontSize * 1.2f, lineHeight = base.lineHeight * 1.2f)
        "xlarge" -> base.copy(fontSize = base.fontSize * 1.45f, lineHeight = base.lineHeight * 1.45f)
        else -> base
    }
}

/**
 * The text of a segment with inline time stamps "(0:03)", the current word, search hits and (optionally) unmeasured
 * words underlined. Annotations: "w" = index of the word, "t" = time of a stamp.
 */
internal fun segmentText(
    s: Segment,
    tw: List<TextWord>,
    marks: List<InlineMark>,
    currentWord: Int,
    hits: List<SearchHit>,
    currentHit: SearchHit?,
    markUnmeasured: Boolean,
    markColor: Color,
    wordColor: Color,
): AnnotatedString {
    val text = s.text
    val byOffset = marks.groupBy { it.offset }
    val map = IntArray(text.length + 1)
    val b = AnnotatedString.Builder()
    for (i in 0..text.length) {
        byOffset[i]?.forEach { m ->
            b.pushStringAnnotation("t", m.time.toString())
            b.pushStyle(SpanStyle(color = markColor, fontWeight = FontWeight.Normal))
            b.append("(" + TranscriptExport.clock(m.time) + ") ")
            b.pop(); b.pop()
        }
        map[i] = b.length
        if (i < text.length) b.append(text[i])
    }
    tw.forEachIndexed { k, w ->
        b.addStringAnnotation("w", k.toString(), map[w.from], map[w.to])
        if (markUnmeasured && w.word?.measured != true) b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = markColor), map[w.from], map[w.to])
        if (k == currentWord) b.addStyle(SpanStyle(background = wordColor), map[w.from], map[w.to])
    }
    hits.forEach { h ->
        val strong = currentHit == h
        b.addStyle(SpanStyle(background = if (strong) Color(0xCCFFB300) else Color(0x66FFD54F)), map[h.from], map[h.to])
    }
    return b.toAnnotatedString()
}

/** Where a tap in the text starts the playback: a stamp, the measured start of the word, else the segment start. */
internal fun tapTime(text: AnnotatedString, offset: Int, s: Segment, tw: List<TextWord>): Double {
    text.getStringAnnotations("t", offset, offset + 1).firstOrNull()?.item?.toDoubleOrNull()?.let { return it }
    val k = text.getStringAnnotations("w", offset, offset + 1).firstOrNull()?.item?.toIntOrNull()
        ?: text.getStringAnnotations("w", (offset - 1).coerceAtLeast(0), offset).firstOrNull()?.item?.toIntOrNull()
    val w = k?.let { tw.getOrNull(it)?.word }
    return if (w != null && w.measured) w.start else s.start
}

/** Color of a speaker in the current look. */
@Composable
fun speakerColor(i: Int): Color = LocalChatLook.current.speakers.let { it[i.mod(it.size)] }

/** The transcript as an attachment for the chat (text with times, speakers and comments). */
fun transcriptAttachment(t: Transcript) = FileAttachment(
    name = DocumentWriter.safeName(t.title) + ".txt",
    mime = "text/plain",
    text = TranscriptExport.markdown(t),
)

internal val SPEEDS = listOf(0.5f, 0.6f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

internal fun speedLabel(v: Float) = (if (v % 1f == 0f) v.toInt().toString() else v.toString().replace('.', ',')) + "×"

@Composable
internal fun PlayerBar(
    c: AppContainer,
    player: TranscriptPlayer,
    control: PlaybackControl,
    position: Double,
    playing: Boolean,
    duration: Double,
    error: String?,
    looping: Boolean,
    onLoop: () -> Unit,
) {
    val look = LocalChatLook.current
    val vs by c.transcription.settings.collectAsStateWithLifecycle()
    var speedMenu by remember { mutableStateOf(false) }
    val skip = vs.skipSeconds
    Surface(color = look.card, border = look.cardBorder, shadowElevation = 6.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) HintText(error, Modifier.padding(4.dp))
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { control.skip(-skip.toDouble()) }) {
                    Icon(when (skip) { 5 -> Icons.Outlined.Replay5; 30 -> Icons.Outlined.Replay30; else -> Icons.Outlined.Replay10 }, contentDescription = tr("{0} Sekunden zurück (F3)", skip))
                }
                Surface(onClick = { control.toggle(playing) }, shape = CircleShape, color = look.action, contentColor = look.onAction, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) tr("Pause (F4)") else tr("Abspielen (F4)")) }
                }
                IconButton(onClick = { control.skip(skip.toDouble()) }) {
                    Icon(when (skip) { 5 -> Icons.Outlined.Forward5; 30 -> Icons.Outlined.Forward30; else -> Icons.Outlined.Forward10 }, contentDescription = tr("{0} Sekunden vor (F5)", skip))
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(0.1f)),
                        onValueChange = { player.seek(it.toDouble()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(0.1f),
                    )
                    Row {
                        Text(TranscriptExport.clock(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.weight(1f))
                        Text(TranscriptExport.clock(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = onLoop) {
                    Icon(if (looping) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat, contentDescription = if (looping) tr("Wiederholen aus (F6)") else tr("Abschnitt wiederholen (F6)"),
                        tint = if (looping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { speedMenu = true }) { Text(speedLabel(vs.speed)) }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        SPEEDS.forEach { v ->
                            DropdownMenuItem(text = { Text(speedLabel(v) + if (v == 1f) tr(" (normal)") else "") }, onClick = {
                                speedMenu = false
                                c.transcription.updateSettings { it.copy(speed = v) }
                            })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SegmentDialog(
    t: Transcript,
    index: Int,
    control: PlaybackControl,
    playing: () -> Boolean,
    onLoop: () -> Unit,
    onSave: (Transcript) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = t.segments[index]
    var text by remember(s) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(s.text)) }
    var speaker by remember(s) { mutableStateOf(s.speaker) }
    var comment by remember(s) { mutableStateOf(s.comment.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    /** The transcript with the edits of this dialog applied to the segment. */
    fun edited(): Transcript = t.copy(segments = t.segments.mapIndexed { i, x ->
        if (i == index) TranscriptEdit.withText(x, text.text).copy(speaker = speaker, comment = comment.trim().ifBlank { null }) else x
    })
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.onPreviewKeyEvent { e -> transcriptKey(e, control, playing, onLoop) },
        title = { Text(tr("Abschnitt bei {0}–{1}", TranscriptExport.clock(s.start), TranscriptExport.clock(s.end))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (t.diarized) ChoiceField(tr("Sprecher"), t.speakers.keys.sorted().map { it to t.speakerName(it) }, speaker, { speaker = it })
                OutlinedTextField(comment, { comment = it }, label = { Text(tr("Kommentar (nicht Teil des Gesprochenen)")) }, minLines = 1, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (control.available) {
                        TextButton(onClick = { control.play(s.start) }) { Text(tr("▶ Abspielen")) }
                        TextButton(onClick = {
                            val next = TranscriptEdit.setStart(edited(), index, control.position())
                            if (next == null) message = tr("Die Abspielposition liegt nicht zwischen dem vorigen Abschnitt und dem Ende dieses Abschnitts.")
                            else { onSave(next); onDismiss() }
                        }) { Text(tr("Start = {0}", TranscriptExport.clock(control.position()))) }
                    }
                    TextButton(onClick = {
                        val next = TranscriptEdit.split(edited(), index, text.selection.start, control.position().takeIf { control.available })
                        if (next == null) message = tr("Zum Teilen den Cursor vor das Wort setzen, mit dem der neue Abschnitt beginnt. Ohne gemessene Wortzeit " +
                            "die Wiedergabe genau an diese Stelle bringen – die Abspielposition wird dann der neue Anfang.")
                        else { onSave(next); onDismiss() }
                    }) { Text(tr("Am Cursor teilen")) }
                    if (index > 0) TextButton(onClick = { TranscriptEdit.mergeWithNext(edited(), index - 1)?.let(onSave); onDismiss() }) { Text(tr("Mit vorigem verbinden")) }
                    if (index < t.segments.lastIndex) TextButton(onClick = { TranscriptEdit.mergeWithNext(edited(), index)?.let(onSave); onDismiss() }) { Text(tr("Mit nächstem verbinden")) }
                    TextButton(onClick = { onSave(t.copy(segments = t.segments.filterIndexed { i, _ -> i != index }.mapIndexed { i, x -> x.copy(id = i) })); onDismiss() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Löschen"))
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                val measured = s.words.count { it.measured }
                HintText(
                    (if (measured > 0) tr("Wortzeiten: {0} von {1} Wörtern gemessen; geänderte Wörter behalten ihre Zeit, neue liegen dazwischen. ", measured, TranscriptEdit.textWords(s).size)
                    else tr("Für diesen Abschnitt gibt es keine gemessenen Wortzeiten. ")) + tr("F4 Abspielen/Anhalten, F3/F5 zurück/vor, F6 wiederholen – auch beim Tippen."),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(edited()); onDismiss() }) { Text(tr("Speichern")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) } },
    )
}
