package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SlideDeck
import kotlinx.coroutines.delay

private val TUD_BLUE = Color(0xFF00008C)
private val TUD_LIGHT = Color(0xFFE5F1FF)

/** ✨ actions on a slide. */
private val SLIDE_AI = listOf(
    "Verbessern" to "Verbessere die Folie: prägnanter Titel, höchstens 5 kurze Stichpunkte, klare Sprache. Gib die Folie im selben Markdown-Format zurück (## Titel, Stichpunkte, ggf. Notizen: …).",
    "Kürzen" to "Kürze die Folie auf das Wesentliche (höchstens 4 Stichpunkte); Details wandern in „Notizen: …“. Gleiches Format.",
    "Stichpunkte ausformulieren" to "Formuliere zu den Stichpunkten ausführliche Sprechernotizen (Notizen: …); die Folie selbst bleibt knapp. Gleiches Format.",
    "Beispiel ergänzen" to "Ergänze ein anschauliches Beispiel aus Schule oder Hochschule als Stichpunkt. Gleiches Format.",
    "Ins Englische" to "Übersetze die Folie samt Notizen ins Englische. Gleiches Format.",
)

/**
 * The presentation editor (like PowerPoint): slides on the left (on phones as a strip on top), the chosen slide
 * large in TUD design with its fields below, speaker notes, layouts, ✨ per slide and a presentation mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlidesBody(
    c: AppContainer,
    doc: LiveDoc,
    markdown: String,
    writing: Boolean,
    narrow: Boolean,
    leading: @Composable () -> Unit,
    onMarkdown: (String) -> Unit,
    onComment: (String) -> Unit,
    inlineAi: InlineAiRunner,
) {
    val slides = remember(markdown) { SlideDeck.parse(markdown).ifEmpty { listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE)) } }
    var selected by remember(doc.id) { mutableIntStateOf(0) }
    val sel = selected.coerceIn(0, slides.lastIndex)
    var presenting by remember { mutableStateOf<Int?>(null) }
    fun set(list: List<SlideDeck.Slide>) = onMarkdown(SlideDeck.markdown(list))
    fun update(i: Int, s: SlideDeck.Slide) = set(slides.toMutableList().apply { set(i, s) })

    Column(Modifier.fillMaxSize()) {
        // Toolbar.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            var add by remember { mutableStateOf(false) }
            Box {
                ToolLabel("+ Neue Folie ▾") { add = true }
                DropdownMenu(expanded = add, onDismissRequest = { add = false }) {
                    SlideDeck.Layout.entries.filter { it != SlideDeck.Layout.TITLE }.forEach { l ->
                        DropdownMenuItem(text = { Text(l.label) }, onClick = {
                            add = false
                            set(slides.toMutableList().apply { add(sel + 1, SlideDeck.template(l)) })
                            selected = sel + 1
                        })
                    }
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            ToolIcon(Icons.Outlined.ContentCopy, "Folie duplizieren") { set(slides.toMutableList().apply { add(sel + 1, slides[sel]) }); selected = sel + 1 }
            ToolIcon(Icons.Outlined.ArrowUpward, "Nach vorn", enabled = sel > 0) {
                set(slides.toMutableList().apply { val x = removeAt(sel); add(sel - 1, x) }); selected = sel - 1
            }
            ToolIcon(Icons.Outlined.ArrowDownward, "Nach hinten", enabled = sel < slides.lastIndex) {
                set(slides.toMutableList().apply { val x = removeAt(sel); add(sel + 1, x) }); selected = sel + 1
            }
            ToolIcon(Icons.Outlined.Delete, "Folie löschen", enabled = slides.size > 1) {
                val removed = slides[sel]
                set(slides.toMutableList().apply { removeAt(sel) })
                selected = (sel - 1).coerceAtLeast(0)
                Notices.undo("Folie gelöscht", restore = { set(slides.toMutableList().apply { add(sel.coerceAtMost(size), removed) }) })
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            var aiMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.AutoAwesome, "KI für diese Folie") { aiMenu = true }
                DropdownMenu(expanded = aiMenu, onDismissRequest = { aiMenu = false }) {
                    SLIDE_AI.forEach { (label, instruction) ->
                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = {
                            aiMenu = false
                            inlineAi.start("s$sel", doc, SlideDeck.markdown(listOf(SlideDeck.Slide(), slides[sel])).substringAfter("---").trim(), label, instruction)
                        })
                    }
                }
            }
            ToolIcon(Icons.Outlined.SpeakerNotes, "Kommentieren") { onComment(slides[sel].title) }
            ToolIcon(Icons.Outlined.Slideshow, "Vorführen") { presenting = sel }
        }
        HorizontalDivider()
        if (narrow) {
            LazyRow(contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(slides) { i, s -> Thumb(i, s, i == sel, Modifier.width(120.dp)) { selected = i } }
            }
            SlideWork(doc, slides, sel, writing, inlineAi, ::update, Modifier.weight(1f))
        } else {
            Row(Modifier.weight(1f)) {
                LazyColumn(Modifier.width(190.dp).fillMaxHeight(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(slides) { i, s -> Thumb(i, s, i == sel, Modifier.fillMaxWidth()) { selected = i } }
                    item {
                        Surface(
                            onClick = { set(slides + SlideDeck.template(SlideDeck.Layout.CONTENT)); selected = slides.size },
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, contentDescription = "Folie hinzufügen") } }
                    }
                }
                VerticalDivider()
                SlideWork(doc, slides, sel, writing, inlineAi, ::update, Modifier.weight(1f))
            }
        }
    }
    presenting?.let { start -> Presenter(slides, start) { presenting = null } }
}

@Composable
private fun Thumb(i: Int, s: SlideDeck.Slide, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
        ) { SlideCanvas(s, Modifier.fillMaxSize()) }
        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The chosen slide large, its fields and the ✨ suggestion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlideWork(
    doc: LiveDoc,
    slides: List<SlideDeck.Slide>,
    sel: Int,
    writing: Boolean,
    inlineAi: InlineAiRunner,
    update: (Int, SlideDeck.Slide) -> Unit,
    modifier: Modifier,
) {
    val s = slides[sel]
    // Fields keep their own text while typing and save after a short pause.
    var title by remember(doc.id, sel) { mutableStateOf(s.title) }
    var body by remember(doc.id, sel) { mutableStateOf(s.body) }
    var right by remember(doc.id, sel) { mutableStateOf(s.right) }
    var notes by remember(doc.id, sel) { mutableStateOf(s.notes) }
    // Changes from outside (the model, undo) replace the fields; saving trims the text, which must not move the cursor.
    LaunchedEffect(s) {
        if (s.title.trim() != title.trim()) title = s.title
        if (s.body.trim() != body.trim()) body = s.body
        if (s.right.trim() != right.trim()) right = s.right
        if (s.notes.trim() != notes.trim()) notes = s.notes
    }
    LaunchedEffect(title, body, right, notes) {
        delay(600)
        val next = s.copy(title = title, body = body, right = right, notes = notes)
        if (next != s) update(sel, next)
    }
    val live = s.copy(title = title, body = body, right = right, notes = notes)
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().aspectRatio(16f / 9f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {
            SlideCanvas(live, Modifier.fillMaxSize())
        }
        inlineAi.state?.takeIf { it.first == "s$sel" }?.second?.let { sug ->
            Box(Modifier.widthIn(max = 820.dp)) {
                AiSuggestionCard(
                    sug,
                    onAccept = {
                        SlideDeck.parse("# x\n\n---\n\n" + sug.text).getOrNull(1)?.let { parsed ->
                            update(
                                sel,
                                if (s.layout != SlideDeck.Layout.TITLE) parsed
                                else parsed.copy(layout = SlideDeck.Layout.TITLE, body = parsed.body.lines().joinToString(" ") { it.replace(Regex("^#{1,6}\\s+"), "") }.trim()),
                            )
                        }
                        inlineAi.clear()
                    },
                    onDiscard = { inlineAi.clear() },
                    onRetry = { inlineAi.retry(doc) },
                )
            }
        }
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlideDeck.Layout.entries.filter { sel == 0 || it != SlideDeck.Layout.TITLE }.forEach { l ->
                    FilterChip(selected = s.layout == l, onClick = { update(sel, live.copy(layout = l)) }, label = { Text(l.label) }, enabled = !writing)
                }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true, enabled = !writing, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                body, { body = it },
                label = { Text(if (s.layout == SlideDeck.Layout.TITLE) "Untertitel" else if (s.layout == SlideDeck.Layout.TWO_COLUMNS) "Linke Spalte" else "Inhalt (Stichpunkte mit „- “, Tabellen, **fett**)") },
                minLines = if (s.layout == SlideDeck.Layout.TITLE || s.layout == SlideDeck.Layout.SECTION) 1 else 4,
                enabled = !writing, modifier = Modifier.fillMaxWidth(),
            )
            if (s.layout == SlideDeck.Layout.TWO_COLUMNS) OutlinedTextField(right, { right = it }, label = { Text("Rechte Spalte") }, minLines = 4, enabled = !writing, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Sprechernotizen") }, minLines = 2, enabled = !writing, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.SpeakerNotes, contentDescription = null) },
            )
            HintText("Folie ${sel + 1} von ${slides.size} · Export als PowerPoint im TUD-Design über ⋮ › Exportieren")
        }
    }
}

/** A slide drawn like the exported PowerPoint (TUD design), scaled to the space it gets. */
@Composable
fun SlideCanvas(s: SlideDeck.Slide, modifier: Modifier) {
    BoxWithConstraints(modifier.background(Color.White, RoundedCornerShape(4.dp))) {
        val scale = maxWidth.value / 800f
        fun sz(v: Float) = (v * scale).sp
        fun dp(v: Float): Dp = (v * scale).dp
        when (s.layout) {
            SlideDeck.Layout.TITLE -> Box(Modifier.fillMaxSize().background(TUD_BLUE, RoundedCornerShape(4.dp)).padding(dp(48f)), contentAlignment = Alignment.CenterStart) {
                Column {
                    Text(s.title.ifBlank { "Titel" }, color = Color.White, fontSize = sz(40f), fontWeight = FontWeight.Bold, lineHeight = sz(46f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (s.body.isNotBlank()) Text(s.body, color = Color(0xFFCFE3FF), fontSize = sz(22f), modifier = Modifier.padding(top = dp(12f)), maxLines = 2)
                }
            }
            SlideDeck.Layout.SECTION -> Row(Modifier.fillMaxSize().padding(dp(56f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(dp(8f)).height(dp(140f)).background(TUD_BLUE))
                Spacer(Modifier.width(dp(24f)))
                Column {
                    Text(s.title.ifBlank { "Abschnitt" }, color = TUD_BLUE, fontSize = sz(36f), fontWeight = FontWeight.Bold, maxLines = 2)
                    if (s.body.isNotBlank()) Text(s.body, color = Color(0xFF444444), fontSize = sz(18f), modifier = Modifier.padding(top = dp(8f)), maxLines = 3)
                }
            }
            SlideDeck.Layout.QUOTE -> Box(Modifier.fillMaxSize().background(TUD_LIGHT).padding(dp(60f)), contentAlignment = Alignment.Center) {
                Text(
                    "„" + s.body.lines().joinToString(" ") { it.trim().removePrefix(">").trim() }.trim().trim('„', '“', '"') + "“",
                    color = TUD_BLUE, fontSize = sz(26f), fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, maxLines = 6, overflow = TextOverflow.Ellipsis,
                )
            }
            else -> Column(Modifier.fillMaxSize().padding(horizontal = dp(40f), vertical = dp(28f))) {
                Text(s.title.ifBlank { "Folientitel" }, color = TUD_BLUE, fontSize = sz(28f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = dp(6f), bottom = dp(14f)).width(dp(80f)).height(dp(3f)).background(TUD_BLUE))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(dp(24f))) {
                    SlideText(s.body, scale, Modifier.weight(1f))
                    if (s.layout == SlideDeck.Layout.TWO_COLUMNS) SlideText(s.right, scale, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("TU Dresden", color = Color(0xFF8A8A8A), fontSize = sz(10f), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Bullet points and paragraphs of a slide at slide scale. */
@Composable
private fun SlideText(md: String, scale: Float, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy((6 * scale).dp)) {
        md.lines().filter { it.isNotBlank() && !it.trim().matches(Regex("^\\|?\\s*:?-{2,}.*")) }.take(14).forEach { line ->
            val indent = line.takeWhile { it == ' ' }.length / 2
            val t = line.trim()
            val bullet = Regex("^([-*+]|\\d+[.)])\\s+").find(t)
            val text = (if (bullet != null) t.substring(bullet.range.last + 1) else t).replace("**", "").replace("|", "  ")
            Row(Modifier.padding(start = (indent * 22 * scale).dp)) {
                if (bullet != null) Text(if (bullet.value.first().isDigit()) bullet.value.trim() + " " else "•  ", color = TUD_BLUE, fontSize = (18 * scale).sp)
                Text(text, color = Color(0xFF222222), fontSize = ((if (indent > 0) 16 else 18) * scale).sp, lineHeight = (24 * scale).sp)
            }
        }
    }
}

/** Full-screen presentation: arrows, space or tap to go on, N for notes, Esc to end. */
@Composable
private fun Presenter(slides: List<SlideDeck.Slide>, start: Int, onClose: () -> Unit) {
    var i by remember { mutableIntStateOf(start) }
    var notes by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.Spacebar, Key.PageDown, Key.Enter -> { i = (i + 1).coerceAtMost(slides.lastIndex); true }
                    Key.DirectionLeft, Key.PageUp, Key.Backspace -> { i = (i - 1).coerceAtLeast(0); true }
                    Key.N -> { notes = !notes; true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Box(Modifier.weight(1f).fillMaxWidth().clickable { i = (i + 1).coerceAtMost(slides.lastIndex) }, contentAlignment = Alignment.Center) {
                SlideCanvas(slides[i], Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            if (notes && slides[i].notes.isNotBlank()) {
                Text(slides[i].notes, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Beenden", tint = Color.White) }
                Text("${i + 1} / ${slides.size}", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { notes = !notes }) { Icon(Icons.Outlined.SpeakerNotes, contentDescription = "Notizen", tint = Color.White) }
                IconButton(onClick = { i = (i - 1).coerceAtLeast(0) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = Color.White) }
                IconButton(onClick = { i = (i + 1).coerceAtMost(slides.lastIndex) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Weiter", tint = Color.White) }
            }
        }
    }
}
