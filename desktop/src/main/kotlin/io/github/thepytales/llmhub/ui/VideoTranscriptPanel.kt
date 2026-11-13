package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.transcribe.Transcript
import io.github.thepytales.llmhub.video.TextCut
import io.github.thepytales.llmhub.video.VideoPlayer
import io.github.thepytales.llmhub.video.VideoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the transcript panel has selected (AP-55): tokens [from]..[to] of the current text, and the Lora proposals with
 * whether each is accepted. Kept by the editor, so Entf in the editor cuts the selected words.
 */
internal class TextCutState {
    var anchor by mutableStateOf(-1)
    var from by mutableStateOf(-1)
    var to by mutableStateOf(-1)
    var proposals by mutableStateOf<List<TextCut.Proposal>>(emptyList())
    var accepted by mutableStateOf<Set<String>>(emptySet())
    var doc: TextCut.Doc? = null
    val hasSelection: Boolean get() = from >= 0 && to >= from

    fun clear() { anchor = -1; from = -1; to = -1 }

    fun select(k: Int, extend: Boolean) {
        if (extend && anchor >= 0) { from = minOf(anchor, k); to = maxOf(anchor, k) }
        else { anchor = k; from = k; to = k }
    }

    /** Cuts the selection out of the project (through the editor's history). */
    fun deleteSelection(h: History): Boolean {
        val d = doc ?: return false
        if (!hasSelection) return false
        val ranges = TextCut.ranges(d, from, to)
        if (ranges.isEmpty()) return false
        val saved = TextCut.seconds(h.project, ranges)
        h.edit { TextCut.cut(it, ranges) }
        clear()
        proposals = emptyList(); accepted = emptySet()
        Notices.info(tr("{0} herausgeschnitten. Mit Strg+Z rückgängig.", clock(saved, true)))
        return true
    }
}

/** The transcripts of the project's sources (path → transcript). */
internal suspend fun projectTranscripts(c: AppContainer, p: VideoProject): Map<String, Transcript> = withContext(Dispatchers.IO) {
    p.transcripts.mapNotNull { (path, tid) -> c.transcription.load(tid)?.let { path to it } }.toMap()
}

/**
 * Textbasierter Schnitt (AP-55): the transcript of V1 as text beside the timeline. A click on a word jumps there,
 * Umschalt+Klick or dragging selects, Entf cuts the selection out of the video. Paragraphs move up and down (their clip
 * moves), filler words and long pauses are marked and go with one click, and Lora proposes passages to cut for a wish.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TranscriptCutPanel(
    c: AppContainer, h: History, player: VideoPlayer, pos: Double, state: TextCutState, onAssign: () -> Unit, modifier: Modifier = Modifier,
    load: suspend (VideoProject) -> Map<String, Transcript> = { projectTranscripts(c, it) },
) {
    val p = h.project
    val transcripts by produceState<Map<String, Transcript>?>(null, p.transcripts) { value = load(p) }
    val doc = remember(p, transcripts) { transcripts?.let { TextCut.build(p, it) } }
    state.doc = doc
    // A cut changes the token numbers: a selection of the old text would point somewhere else.
    LaunchedEffect(h.version) { if (state.doc != null && state.to >= (doc?.tokens?.size ?: 0)) state.clear() }
    val look = LocalChatLook.current
    val scope = rememberCoroutineScope()
    var wish by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val current = doc?.at(pos) ?: -1
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("Transkript"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { contentDescription = tr("Transkript") })
        if (transcripts == null) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); return@Column }
        if (doc == null || doc.paragraphs.isEmpty()) {
            HintText(tr("Ordne den Clips ein Transkript zu (oder transkribiere sie) – dann steht hier der Text: Wörter löschen schneidet das Video, Absätze verschieben verschiebt Clips."))
            OutlinedButton(onClick = onAssign) { Text(tr("Transkript zuordnen …")) }
            return@Column
        }
        HintText(tr("Klick springt, Umschalt+Klick oder Ziehen markiert, Entf schneidet heraus."))
        if (doc.missing.isNotEmpty()) HintText(tr("{0} Clip(s) ohne Transkript (oder mit Standbild/rückwärts) fehlen im Text.", doc.missing.size))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val fillers = doc.fillers().size
            val pauses = doc.pauses().size
            OutlinedButton(enabled = fillers > 0, onClick = { h.edit { TextCut.removeFillers(it, doc) }; state.clear(); Notices.info(tr("{0} Füllwörter entfernt. Mit Strg+Z rückgängig.", fillers)) }) { Text(tr("Füllwörter entfernen ({0})", fillers)) }
            OutlinedButton(enabled = pauses > 0, onClick = { h.edit { TextCut.shortenPauses(it, doc) }; state.clear(); Notices.info(tr("{0} Pausen gekürzt. Mit Strg+Z rückgängig.", pauses)) }) { Text(tr("Pausen kürzen ({0})", pauses)) }
            if (state.hasSelection) Button(onClick = { state.deleteSelection(h) }) { Icon(Icons.Outlined.ContentCut, null, Modifier.size(16.dp)); Spacer(Modifier.size(4.dp)); Text(tr("Herausschneiden") + " (Entf)") }
        }
        // Lora as editing assistant.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(wish, { wish = it }, singleLine = true, modifier = Modifier.weight(1f).typing(), placeholder = { Text(tr("Wunsch an Lora, z. B. „Kürze auf 10 Minuten“")) })
            IconButton(enabled = wish.isNotBlank() && !asking, onClick = {
                asking = true
                scope.launch {
                    val result = runCatching { TextCut.propose(c, doc, p.duration, wish) }
                    asking = false
                    result.onSuccess { list ->
                        state.proposals = list; state.accepted = list.map { it.id }.toSet()
                        if (list.isEmpty()) Notices.info(tr("Lora schlägt nichts zum Schneiden vor."))
                    }.onFailure { Notices.error(it.message ?: it.javaClass.simpleName) }
                }
            }) { if (asking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoAwesome, contentDescription = tr("Lora schlägt Schnitte vor")) }
        }
        if (state.proposals.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(8.dp), color = look.card, border = boxBorder(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val sel = state.proposals.filter { it.id in state.accepted }
                    val gain = TextCut.seconds(p, sel.flatMap { TextCut.ranges(doc, it.from, it.to) })
                    Text(tr("Lora schlägt {0} Schnitt(e) vor – spart {1}", state.proposals.size, clock(gain)), style = MaterialTheme.typography.labelLarge)
                    state.proposals.forEach { pr ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(pr.id in state.accepted, { on -> state.accepted = if (on) state.accepted + pr.id else state.accepted - pr.id })
                            TextButton(onClick = { doc.tokens.getOrNull(pr.from)?.let { player.seek(p, it.start) }; scope.launch { listState.animateScrollToItem(doc.tokens.getOrNull(pr.from)?.paragraph ?: 0) } }, modifier = Modifier.weight(1f)) {
                                val t = doc.tokens.getOrNull(pr.from)
                                Text(listOfNotNull(t?.let { clock(it.start) }, pr.reason.ifBlank { null }, "„" + doc.text(pr.from, pr.to).take(60) + "…“").joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(enabled = sel.isNotEmpty(), onClick = {
                            h.edit { TextCut.accept(it, doc, sel) }
                            state.proposals = emptyList(); state.accepted = emptySet(); state.clear()
                            Notices.info(tr("{0} Schnitt(e) übernommen. Mit Strg+Z rückgängig.", sel.size))
                        }) { Text(tr("Angenommene übernehmen")) }
                        TextButton(onClick = { state.proposals = emptyList(); state.accepted = emptySet() }) { Text(tr("Alle ablehnen")) }
                    }
                }
            }
        }
        HorizontalDivider()
        val proposed = remember(state.proposals, state.accepted) { state.proposals.filter { it.id in state.accepted }.flatMap { it.from..it.to }.toSet() }
        LaunchedEffect(current / 40, doc.paragraphs.size) {
            val k = doc.tokens.getOrNull(current)?.paragraph ?: return@LaunchedEffect
            if (player.playing.value && listState.layoutInfo.visibleItemsInfo.none { it.index == k }) listState.animateScrollToItem(k)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(doc.paragraphs, key = { k, para -> "${para.clipId}:${para.first}:$k" }) { k, para ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(clock(para.start), para.speaker, tr("Clip {0}", para.clip + 1)).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(enabled = k > 0, onClick = { transcripts?.let { t -> h.edit { TextCut.moveParagraph(it, t, k, k - 1) } }; state.clear() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.ArrowUpward, contentDescription = tr("Absatz nach oben"), modifier = Modifier.size(16.dp)) }
                        IconButton(enabled = k < doc.paragraphs.lastIndex, onClick = { transcripts?.let { t -> h.edit { TextCut.moveParagraph(it, t, k, k + 2) } }; state.clear() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.ArrowDownward, contentDescription = tr("Absatz nach unten"), modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = { state.select(para.first, false); state.select(para.last, true); state.deleteSelection(h) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, contentDescription = tr("Absatz herausschneiden"), modifier = Modifier.size(16.dp)) }
                    }
                    ParagraphText(doc, para, current, state, proposed, look.brand, onSeek = { player.seek(p, it) })
                }
            }
        }
    }
}

/** One paragraph as one text: every token a span (selected, being spoken, filler, pause, proposed for cutting). */
@Composable
private fun ParagraphText(doc: TextCut.Doc, para: TextCut.Paragraph, current: Int, state: TextCutState, proposed: Set<Int>, brand: Color, onSeek: (Double) -> Unit) {
    val selFrom = state.from
    val selTo = state.to
    val spanStarts = remember(doc, para) { IntArray(para.last - para.first + 1) }
    val text: AnnotatedString = buildAnnotatedString {
        for (k in para.first..para.last) {
            val t = doc.tokens[k]
            spanStarts[k - para.first] = length
            val selected = k in selFrom..selTo && selFrom >= 0
            val style = when {
                selected -> SpanStyle(background = brand.copy(alpha = 0.28f))
                k in proposed -> SpanStyle(background = Color(0x33D32F2F), textDecoration = TextDecoration.LineThrough)
                k == current -> SpanStyle(background = brand.copy(alpha = 0.14f), fontWeight = FontWeight.SemiBold)
                t.kind == TextCut.Kind.PAUSE -> SpanStyle(color = Color(0xFF8A8A8A), background = Color(0x14888888))
                t.filler -> SpanStyle(color = Color(0xFFB26A00), textDecoration = TextDecoration.Underline)
                else -> SpanStyle()
            }
            withStyle(style) { append(t.text) }
            append(' ')
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    fun tokenAt(offset: Int): Int {
        var lo = 0
        var hi = spanStarts.lastIndex
        var best = 0
        while (lo <= hi) { val mid = (lo + hi) / 2; if (spanStarts[mid] <= offset) { best = mid; lo = mid + 1 } else hi = mid - 1 }
        return para.first + best
    }
    Text(
        text, style = MaterialTheme.typography.bodyMedium, onTextLayout = { layout = it },
        modifier = Modifier.fillMaxWidth().background(Color.Transparent).pointerInput(para.first, para.last) {
            awaitPointerEventScope {
                var down = false
                while (true) {
                    val e = awaitPointerEvent()
                    val l = layout ?: continue
                    val ch = e.changes.firstOrNull() ?: continue
                    val k = tokenAt(l.getOffsetForPosition(ch.position))
                    when (e.type) {
                        PointerEventType.Press -> {
                            down = true
                            state.select(k, e.keyboardModifiers.isShiftPressed)
                            if (!e.keyboardModifiers.isShiftPressed) onSeek(doc.tokens[k].start)
                        }
                        PointerEventType.Move -> if (down && state.anchor >= 0 && k != state.to && k != state.from) state.select(k, true)
                        PointerEventType.Release -> down = false
                    }
                }
            }
        },
    )
}
