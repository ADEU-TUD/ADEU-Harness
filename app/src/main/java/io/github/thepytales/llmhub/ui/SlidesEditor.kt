package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.docs.DocMarkup
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.TudBrand
import io.github.thepytales.llmhub.docs.TudSlide
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TUD_BLUE = Color(0xFF00008C)
private val TUD_LIGHT_BLUE = Color(0xFF97C6FF)
private val TUD_MID_BLUE = Color(0xFF2F57B2)
private val TUD_GREY = Color(0xFF566371)
private val TUD_TINT = Color(0xFFE5F1FF)
private val TUD_LINE = Color(0xFFBBC1C6)

/** ✨ actions on a slide. */
private val SLIDE_AI = listOf(
    trLater("Verbessern") to "Verbessere die Folie: prägnanter Titel, höchstens 5 kurze Stichpunkte, klare Sprache. Gib die Folie im selben Markdown-Format zurück (## Titel, Stichpunkte, ggf. Notizen: …).",
    trLater("Kürzen") to "Kürze die Folie auf das Wesentliche (höchstens 4 Stichpunkte); Details wandern in „Notizen: …“. Gleiches Format.",
    trLater("Stichpunkte ausformulieren") to "Formuliere zu den Stichpunkten ausführliche Sprechernotizen (Notizen: …); die Folie selbst bleibt knapp. Gleiches Format.",
    trLater("Beispiel ergänzen") to "Ergänze ein anschauliches Beispiel aus Schule oder Hochschule als Stichpunkt. Gleiches Format.",
    trLater("Als Tabelle") to "Stelle den Inhalt der Folie als übersichtliche Markdown-Tabelle dar (höchstens 5 Zeilen). Gleiches Format.",
    trLater("In zwei Spalten") to "Teile den Inhalt sinnvoll auf zwei Spalten auf (z. B. Vorher/Nachher, Pro/Contra); die rechte Spalte beginnt nach einer Zeile „|||“. Gleiches Format.",
    trLater("Sprechernotizen schreiben") to "Schreibe ausführliche Sprechernotizen zu dieser Folie (Notizen: …): was ich dazu sagen kann und die Überleitung. Die Folie selbst bleibt unverändert. Gleiches Format.",
    trLater("Ins Englische") to "Übersetze die Folie samt Notizen ins Englische. Gleiches Format.",
)

/**
 * What the slides show around the content, as in the exported PowerPoint: the organizational unit (title slide) and
 * the footer line ("TU Dresden · Professur … · Titel").
 */
data class DeckFrame(val unit: List<String>, val footer: String) {
    companion object {
        /** The footer of the CD: „Titel der Präsentation • Vortragende“ (the slide number follows). */
        fun of(unit: String, footer: String, deckTitle: String, presenter: String? = null): DeckFrame {
            val lines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val foot = footer.ifBlank { listOfNotNull(deckTitle.takeIf { it.isNotBlank() }, presenter?.let { plainInline(it) }?.takeIf { it.isNotBlank() }).joinToString(" • ").ifBlank { "TU Dresden" } }
            return DeckFrame(lines, foot)
        }
    }
}

/**
 * The presentation editor (like PowerPoint): slides on the left (on phones as a strip on top), the chosen slide
 * large in TUD design – exactly as the export draws it – with its fields below, speaker notes, layouts, ✨ per slide,
 * a slide sorter and a presenter view with timer and next slide.
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
    val settings by c.settings.settings.collectAsState()
    val slides = remember(markdown) { SlideDeck.parse(markdown).ifEmpty { listOf(SlideDeck.Slide(tr("Titel"), layout = SlideDeck.Layout.TITLE)) } }
    val frame = DeckFrame.of(
        settings.tudUnit, settings.docFooter, slides.first().title.ifBlank { doc.title },
        slides.first().takeIf { it.layout == SlideDeck.Layout.TITLE }?.body?.lines()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() },
    )
    var selected by remember(doc.id) { mutableIntStateOf(0) }
    val sel = selected.coerceIn(0, slides.lastIndex)
    var presenting by remember { mutableStateOf<Int?>(null) }
    var sorter by remember { mutableStateOf(false) }
    var handout by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun set(list: List<SlideDeck.Slide>) = onMarkdown(SlideDeck.markdown(list))
    // Lora in der Präsentation (AP-38 Stufe c): her actions read and replace the slides.
    val latestMarkdown = androidx.compose.runtime.rememberUpdatedState(markdown)
    val latestSet = androidx.compose.runtime.rememberUpdatedState(onMarkdown)
    androidx.compose.runtime.DisposableEffect(doc.id) {
        val host = object : io.github.thepytales.llmhub.agent.EditorDocHost {
            override fun markdown(): String = latestMarkdown.value
            override fun replace(markdown: String) = latestSet.value(markdown)
            override fun selected(): Int = selected
        }
        io.github.thepytales.llmhub.agent.ActionHosts.slides = host
        onDispose { if (io.github.thepytales.llmhub.agent.ActionHosts.slides === host) io.github.thepytales.llmhub.agent.ActionHosts.slides = null }
    }
    fun update(i: Int, s: SlideDeck.Slide) = set(slides.toMutableList().apply { set(i, s) })
    fun addSlide(layout: SlideDeck.Layout) {
        set(slides.toMutableList().apply { add(sel + 1, SlideDeck.template(layout)) })
        selected = sel + 1
    }
    fun duplicate() { set(slides.toMutableList().apply { add(sel + 1, slides[sel]) }); selected = sel + 1 }
    fun move(delta: Int) {
        val to = sel + delta
        if (to !in slides.indices || to == 0 || sel == 0) return
        set(slides.toMutableList().apply { val x = removeAt(sel); add(to, x) })
        selected = to
    }
    /** Splits slides with more than six points (no model needed). */
    fun splitFull() {
        val (list, n) = SlideDeck.splitFull(slides)
        if (n == 0) Notices.info(tr("Keine Folie hat mehr als sechs Stichpunkte."))
        else {
            set(list)
            Notices.undo(tr("{0} Folie(n) aufgeteilt", n), restore = { set(slides) })
        }
    }
    /** A whole-deck request to the model in the presentation's chat. */
    fun askDeck(label: String, text: String) {
        if (DocsAi.send(c, doc, label, text, DocsAi.instructions(doc))) Notices.info(tr("Die KI überarbeitet die Präsentation."))
    }
    val base = LocalDocBase.current
    /** A picture for the current slide: stored next to the document, the slide becomes "Bild + Text" if it shows none. */
    val pickImage = rememberImageBytesPicker { bytes ->
        if (bytes == null || DocMarkup.imageType(bytes) == null) { if (bytes != null) Notices.error(tr("Das Bild konnte nicht gelesen werden (PNG, JPEG, GIF oder BMP).")); return@rememberImageBytesPicker }
        val target = base ?: c.docs.baseDir(doc)
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { DocMarkup.storeImage(target, bytes) }.getOrNull() } ?: return@launch Notices.error(tr("Das Bild konnte nicht gespeichert werden."))
            val s = slides[sel]
            val layout = if (sel == 0 || s.layout.withImage) s.layout else SlideDeck.Layout.IMAGE_TEXT
            update(sel, s.copy(image = path, imageAlt = s.imageAlt.ifBlank { "" }, layout = layout))
        }
    }
    fun delete() {
        if (slides.size <= 1) return
        val removed = slides[sel]
        val at = sel
        set(slides.toMutableList().apply { removeAt(at) })
        selected = (at - 1).coerceAtLeast(0)
        Notices.undo(tr("Folie gelöscht"), restore = { set(slides.toMutableList().apply { add(at.coerceAtMost(size), removed) }) })
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || writing) return@onPreviewKeyEvent false
            when {
                e.key == Key.F5 -> { presenting = if (e.isShiftPressed) sel else 0; true }
                e.isCmd && e.key == Key.M -> { addSlide(SlideDeck.Layout.CONTENT); true }
                e.isCmd && e.key == Key.D -> { duplicate(); true }
                e.isCmd && e.isShiftPressed && e.key == Key.DirectionUp -> { move(-1); true }
                e.isCmd && e.isShiftPressed && e.key == Key.DirectionDown -> { move(1); true }
                else -> false
            }
        },
    ) {
        // Toolbar.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            var add by remember { mutableStateOf(false) }
            Box {
                ToolLabel(tr("+ Neue Folie ▾")) { add = true }
                DropdownMenu(expanded = add, onDismissRequest = { add = false }) {
                    SlideDeck.Layout.entries.filter { it != SlideDeck.Layout.TITLE }.forEach { l ->
                        DropdownMenuItem(text = { Text(l.label) }, onClick = { add = false; addSlide(l) })
                    }
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            ToolIcon(Icons.Outlined.ContentCopy, tr("Folie duplizieren (Strg+D)")) { duplicate() }
            ToolIcon(Icons.Outlined.ArrowUpward, tr("Nach vorn (Strg+Umschalt+↑)"), enabled = sel > 1) { move(-1) }
            ToolIcon(Icons.Outlined.ArrowDownward, tr("Nach hinten (Strg+Umschalt+↓)"), enabled = sel in 1 until slides.lastIndex) { move(1) }
            ToolIcon(Icons.Outlined.Delete, tr("Folie löschen"), enabled = slides.size > 1) { delete() }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            var aiMenu by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Outlined.AutoAwesome, tr("KI für diese Folie")) { aiMenu = true }
                DropdownMenu(expanded = aiMenu, onDismissRequest = { aiMenu = false }) {
                    SLIDE_AI.forEach { (label, instruction) ->
                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = {
                            aiMenu = false
                            inlineAi.start("s$sel", doc, SlideDeck.markdown(listOf(SlideDeck.Slide(), slides[sel])).substringAfter("---").trim(), label, instruction)
                        })
                    }
                    HorizontalDivider()
                    Text(tr("Ganze Präsentation"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    DropdownMenuItem(text = { Text(tr("Sprechernotizen für alle Folien")) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = { aiMenu = false; askDeck(tr("Sprechernotizen"), NOTES_ALL_SLIDES) })
                    DropdownMenuItem(text = { Text(tr("Stil vereinheitlichen")) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }, onClick = { aiMenu = false; askDeck(tr("Stil vereinheitlichen"), STYLE_SLIDES) })
                    DropdownMenuItem(text = { Text(tr("Volle Folien teilen (ohne KI)")) }, onClick = { aiMenu = false; splitFull() })
                }
            }
            ToolIcon(Icons.Outlined.Image, tr("Bild einfügen"), enabled = !writing) { pickImage() }
            ToolIcon(Icons.Outlined.SpeakerNotes, tr("Kommentieren")) { onComment(slides[sel].title) }
            ToolIcon(Icons.Outlined.GridView, tr("Folienübersicht"), active = sorter) { sorter = !sorter }
            ToolIcon(Icons.Outlined.Slideshow, tr("Vorführen ab dieser Folie (Umschalt+F5)")) { presenting = sel }
            ToolLabel(tr("Handout (PDF)")) { handout = true }
        }
        HorizontalDivider()
        when {
            sorter -> Sorter(slides, sel, frame, onPick = { selected = it; sorter = false })
            narrow -> {
                LazyRow(contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(slides) { i, s -> Thumb(i, s, slides.size, frame, i == sel, Modifier.width(120.dp), slides) { selected = i } }
                }
                SlideWork(c, doc, slides, sel, frame, writing, inlineAi, ::update, Modifier.weight(1f))
            }
            else -> Row(Modifier.weight(1f)) {
                LazyColumn(Modifier.width(190.dp).fillMaxHeight(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(slides) { i, s -> Thumb(i, s, slides.size, frame, i == sel, Modifier.fillMaxWidth(), slides) { selected = i } }
                    item {
                        Surface(
                            onClick = { set(slides + SlideDeck.template(SlideDeck.Layout.CONTENT)); selected = slides.size },
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, contentDescription = tr("Folie hinzufügen (Strg+M)")) } }
                    }
                }
                VerticalDivider()
                SlideWork(c, doc, slides, sel, frame, writing, inlineAi, ::update, Modifier.weight(1f))
            }
        }
    }
    presenting?.let { start -> Presenter(slides, start, frame, narrow) { presenting = null } }
    if (handout) HandoutDialog(c, doc, DocMarkup.resolveImages(markdown, base ?: c.docs.baseDir(doc)), onDismiss = { handout = false })
}

@Composable
private fun Thumb(i: Int, s: SlideDeck.Slide, total: Int, frame: DeckFrame, selected: Boolean, modifier: Modifier, deck: List<SlideDeck.Slide> = emptyList(), onClick: () -> Unit) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
        ) { SlideCanvas(s, Modifier.fillMaxSize(), i + 1, total, frame, deck) }
        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

/** All slides as a grid (like the slide sorter of PowerPoint); a click opens the slide. */
@Composable
private fun Sorter(slides: List<SlideDeck.Slide>, sel: Int, frame: DeckFrame, onPick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(slides) { i, s -> Thumb(i, s, slides.size, frame, i == sel, Modifier.fillMaxWidth(), slides) { onPick(i) } }
    }
}

/** The chosen slide large, its fields and the ✨ suggestion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlideWork(
    c: AppContainer,
    doc: LiveDoc,
    slides: List<SlideDeck.Slide>,
    sel: Int,
    frame: DeckFrame,
    writing: Boolean,
    inlineAi: InlineAiRunner,
    update: (Int, SlideDeck.Slide) -> Unit,
    modifier: Modifier,
) {
    val s = slides[sel]
    val isTitle = s.layout == SlideDeck.Layout.TITLE
    // The title slide keeps the subtitle in the first line of the body, further details (date, occasion) below it.
    fun subtitleOf(body: String) = if (isTitle) body.lines().firstOrNull { it.isNotBlank() }.orEmpty().trim() else body
    fun metaOf(body: String) = if (isTitle) body.lines().filter { it.isNotBlank() }.drop(1).joinToString("\n") { it.trim() } else ""
    // Fields keep their own text while typing and save after a short pause.
    var title by remember(doc.id, sel) { mutableStateOf(TextFieldValue(s.title)) }
    var body by remember(doc.id, sel) { mutableStateOf(TextFieldValue(subtitleOf(s.body))) }
    var meta by remember(doc.id, sel) { mutableStateOf(TextFieldValue(metaOf(s.body))) }
    var right by remember(doc.id, sel) { mutableStateOf(TextFieldValue(s.right)) }
    var notes by remember(doc.id, sel) { mutableStateOf(TextFieldValue(s.notes)) }
    fun norm(t: String) = t.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    fun bodyText() = if (isTitle) listOf(body.text.trim(), meta.text.trim()).filter { it.isNotEmpty() }.joinToString("\n") else body.text
    // Changes from outside (the model, undo) replace the fields; saving trims the text, which must not move the cursor.
    LaunchedEffect(s) {
        if (s.title.trim() != title.text.trim()) title = TextFieldValue(s.title)
        if (if (isTitle) norm(s.body) != norm(bodyText()) else s.body.trim() != bodyText().trim()) {
            body = TextFieldValue(subtitleOf(s.body))
            meta = TextFieldValue(metaOf(s.body))
        }
        if (s.right.trim() != right.text.trim()) right = TextFieldValue(s.right)
        if (s.notes.trim() != notes.text.trim()) notes = TextFieldValue(s.notes)
    }
    LaunchedEffect(title.text, body.text, meta.text, right.text, notes.text) {
        delay(600)
        val next = s.copy(title = title.text, body = bodyText(), right = right.text, notes = notes.text)
        if (next != s) update(sel, next)
    }
    val live = s.copy(title = title.text, body = bodyText(), right = right.text, notes = notes.text)
    Column(Modifier.then(modifier).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        var picked by remember(doc.id, sel) { mutableStateOf<Int?>(null) }
        Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().aspectRatio(16f / 9f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {
            SlideCanvas(live, Modifier.fillMaxSize(), sel + 1, slides.size, frame, slides)
            // Frei gestalten (AP-16): elements can be moved and resized right on the slide.
            if (!writing && live.elements.isNotEmpty()) FreeOverlay(live.elements, picked, onPick = { picked = it }) { list -> update(sel, live.copy(elements = list)) }
        }
        if (!writing) FreeDesignBar(c, doc, slides, sel, live, picked, onPick = { picked = it }, updateAt = update) { next -> update(sel, next) }
        if (frame.unit.isEmpty()) UnitHint(c)
        inlineAi.state?.takeIf { it.first == "s$sel" }?.second?.let { sug ->
            Box(Modifier.widthIn(max = 820.dp)) {
                AiSuggestionCard(
                    sug,
                    onAccept = {
                        SlideDeck.parse("# x\n\n---\n\n" + sug.text).getOrNull(1)?.let { parsed ->
                            update(
                                sel,
                                if (s.layout != SlideDeck.Layout.TITLE) parsed
                                else parsed.copy(layout = SlideDeck.Layout.TITLE, body = parsed.body.lines().joinToString("\n") { it.replace(Regex("^#{1,6}\\s+"), "") }.trim()),
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
                SlideDeck.Layout.entries.filter { if (sel == 0) it == SlideDeck.Layout.TITLE else it != SlideDeck.Layout.TITLE }.forEach { l ->
                    FilterChip(selected = s.layout == l, onClick = { update(sel, live.copy(layout = l)) }, label = { Text(l.label) }, enabled = !writing)
                }
            }
            OutlinedTextField(title, { title = it }, label = { Text(tr("Titel")) }, singleLine = true, enabled = !writing, modifier = Modifier.fillMaxWidth())
            when (s.layout) {
                SlideDeck.Layout.TITLE -> {
                    OutlinedTextField(body, { body = it }, label = { Text(tr("Untertitel")) }, singleLine = true, enabled = !writing, modifier = Modifier.fillMaxWidth())
                    RichField(meta, { meta = it }, tr("Angaben (Vortragende, Anlass, Datum – je Zeile eine)"), minLines = 2, enabled = !writing, toolbar = false)
                }
                SlideDeck.Layout.SECTION -> RichField(body, { body = it }, tr("Kurze Beschreibung (optional)"), minLines = 1, enabled = !writing, toolbar = false)
                SlideDeck.Layout.QUOTE -> RichField(body, { body = it }, tr("Zitat (letzte Zeile mit „– Quelle“)"), minLines = 3, enabled = !writing, toolbar = false)
                SlideDeck.Layout.TWO_COLUMNS -> {
                    RichField(body, { body = it }, tr("Linke Spalte"), minLines = 4, enabled = !writing)
                    RichField(right, { right = it }, tr("Rechte Spalte"), minLines = 4, enabled = !writing)
                }
                SlideDeck.Layout.CONTENT -> RichField(body, { body = it }, tr("Inhalt"), minLines = 4, enabled = !writing)
                SlideDeck.Layout.IMAGE_TEXT, SlideDeck.Layout.IMAGE -> {
                    ImageFields(live, enabled = !writing) { update(sel, it) }
                    if (s.layout == SlideDeck.Layout.IMAGE_TEXT) RichField(body, { body = it }, tr("Text neben dem Bild"), minLines = 3, enabled = !writing)
                }
                SlideDeck.Layout.COMPARISON -> {
                    RichField(body, { body = it }, tr("Linke Spalte (erste Zeile „### Überschrift“)"), minLines = 4, enabled = !writing)
                    RichField(right, { right = it }, tr("Rechte Spalte (erste Zeile „### Überschrift“)"), minLines = 4, enabled = !writing)
                }
                SlideDeck.Layout.TABLE -> {
                    val rows = DocBlocks.tableRows(body.text).ifEmpty { listOf(listOf("Spalte 1", "Spalte 2"), listOf("", "")) }
                    TableGrid(rows) { r -> body = TextFieldValue(DocBlocks.table(r)) }
                }
                SlideDeck.Layout.CHART -> {
                    OutlinedTextField(
                        body, { body = it }, label = { Text(tr("Diagramm (```chart-Block mit JSON)")) }, minLines = 4, enabled = !writing,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth(),
                    )
                    if (SlideDeck.chartOf(body.text) == null) HintText(tr("Kein gültiges Diagramm – Typen: bar, line, pie, hbar, stacked, scatter, box, histogram."))
                }
                SlideDeck.Layout.AGENDA -> {
                    RichField(body, { body = it }, tr("Punkte (leer = automatisch aus den Abschnittsfolien)"), minLines = 2, enabled = !writing)
                    HintText(tr("Automatisch: ") + SlideDeck.agenda(slides.mapIndexed { k, x -> if (k == sel) live else x }, sel).joinToString(" · ").ifBlank { tr("noch keine Abschnitte") })
                }
                SlideDeck.Layout.END -> RichField(body, { body = it }, tr("Text und Kontakt (je Zeile eine Angabe)"), minLines = 2, enabled = !writing, toolbar = false)
                SlideDeck.Layout.FREE -> {
                    RichField(body, { body = it }, tr("Text unter dem Titel (optional)"), minLines = 1, enabled = !writing)
                    HintText(tr("Freie Folie: Textfelder, Bilder und Formen über die Leiste unter der Folie einfügen, auf der Folie verschieben, an der Ecke unten rechts in der Größe ändern."))
                }
            }
            OutlinedTextField(
                notes, { notes = it }, label = { Text(tr("Sprechernotizen")) }, minLines = 2, enabled = !writing, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.SpeakerNotes, contentDescription = null) },
            )
            HintText(
                tr("Folie {0} von {1} · Enter: neuer Stichpunkt, Tab: einrücken, Strg+B: fett · Strg+M: neue Folie, F5: vorführen · ", sel + 1, slides.size) +
                    tr("Export als PowerPoint im TUD-Design über ⋮ › Exportieren"),
            )
        }
    }
}

/** The organizational unit is missing: title slide and footer stay incomplete until it is entered. */
@Composable
private fun UnitHint(c: AppContainer) {
    var unit by remember { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Für das TUD-Design fehlt die Einrichtung"), style = MaterialTheme.typography.titleSmall)
            }
            HintText(tr("Sie steht auf der Titelfolie unten und in der Fußzeile jeder Folie – eine Ebene pro Zeile (Fakultät, Institut, Professur)."))
            OutlinedTextField(unit, { unit = it }, minLines = 3, modifier = Modifier.fillMaxWidth(), placeholder = { Text(tr("Fakultät …\nInstitut …\nProfessur …")) })
            TextButton(enabled = unit.isNotBlank(), onClick = { c.settings.update { it.copy(tudUnit = unit.trim()) } }) { Text(tr("Für alle Präsentationen übernehmen")) }
        }
    }
}

/**
 * A text field for slide content without visible Markdown: bullet points show as "•", bold as bold. Enter continues
 * the list, Tab indents, Strg+B/I format; a small bar offers the same for touch screens.
 */
@Composable
private fun RichField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, label: String, minLines: Int, enabled: Boolean, toolbar: Boolean = true) {
    val link = MaterialTheme.colorScheme.primary
    val code = MaterialTheme.colorScheme.surfaceVariant
    val transformation = remember(link, code) { HideMarkdown(link, code) }
    Column(Modifier.fillMaxWidth()) {
        if (toolbar) Row(verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(Icons.Outlined.FormatBold, tr("Fett (Strg+B)"), enabled = enabled) { onChange(toggleMarks(value, "**")) }
            ToolIcon(Icons.Outlined.FormatItalic, tr("Kursiv (Strg+I)"), enabled = enabled) { onChange(toggleMarks(value, "*")) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, tr("Stichpunkte an/aus"), enabled = enabled) { onChange(toggleBullets(value)) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, tr("Einrücken (Tab)"), enabled = enabled) { onChange(indentLine(value, outdent = false)) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, tr("Ausrücken (Umschalt+Tab)"), enabled = enabled) { onChange(indentLine(value, outdent = true)) }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(smartEdit(value, it)) },
            label = { Text(label) },
            minLines = minLines,
            enabled = enabled,
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.Tab -> { onChange(indentLine(value, outdent = e.isShiftPressed)); true }
                    e.isCmd && e.key == Key.B -> { onChange(toggleMarks(value, "**")); true }
                    e.isCmd && e.key == Key.I -> { onChange(toggleMarks(value, "*")); true }
                    else -> false
                }
            },
        )
    }
}

/** Turns the lines of the selection (or the line at the cursor) into bullet points, or back into plain lines. */
fun toggleBullets(v: TextFieldValue): TextFieldValue {
    val t = v.text
    val from = t.lastIndexOf('\n', v.selection.min - 1) + 1
    val to = t.indexOf('\n', v.selection.max).let { if (it < 0) t.length else it }
    val lines = t.substring(from, to).split('\n')
    val bullet = Regex("^(\\s*)[-*+]\\s")
    val all = lines.all { bullet.containsMatchIn(it) || it.isBlank() }
    val next = lines.joinToString("\n") { l -> if (l.isBlank()) l else if (all) l.replaceFirst(bullet, "$1") else l.replaceFirst(Regex("^(\\s*)"), "$1- ") }
    val text = t.substring(0, from) + next + t.substring(to)
    return TextFieldValue(text, TextRange(from + next.length))
}

/** Edits of the user, with lists continued on Enter (also from the soft keyboard) and hidden marks kept intact. */
fun smartEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val at = old.selection.start
    if (old.selection.collapsed && new.text.length == old.text.length + 1 && new.selection.collapsed &&
        new.text == old.text.substring(0, at) + "\n" + old.text.substring(at)
    ) return continueList(old) ?: new
    return MarkdownHiding.fixEdit(old, new)
}

// ── The slide as drawn in the export ─────────────────────────────────────────────────────────────────────────────

/** Sizes of the exported slide (PptxWriter): 33.867 × 19.05 cm. */
private const val SLIDE_W_CM = 33.867f

/**
 * A slide drawn like the exported PowerPoint in the corporate design 2025 of TU Dresden ([TudSlide]): same positions,
 * font sizes and colours – title slide in Brillantblau with the white logo and the subtitle above the title, section
 * and closing slides with the Shape, content slides with a black title in Noto Sans Medium and the footer „TUD“ ·
 * „Titel • Vortragende • Seite N“.
 */
@Composable
fun SlideCanvas(s: SlideDeck.Slide, modifier: Modifier, number: Int = 1, total: Int = 1, frame: DeckFrame = DeckFrame(emptyList(), "TU Dresden"), deck: List<SlideDeck.Slide> = emptyList()) {
    val logoWhite = remember { runCatching { decodeImage(TudBrand.logoWhite) }.getOrNull() }
    val shortBlue = remember { runCatching { decodeImage(TudBrand.logoShortBlue) }.getOrNull() }
    val shortWhite = remember { runCatching { decodeImage(TudBrand.logoShortWhite) }.getOrNull() }
    val density = LocalDensity.current
    val dark = s.layout == SlideDeck.Layout.TITLE || s.layout == SlideDeck.Layout.END || s.layout == SlideDeck.Layout.SECTION
    // Masterfolie (AP-16): background of the light slides.
    val master = deck.firstOrNull()?.master ?: s.master
    val light = master?.background?.let { parseHexColor(it) } ?: Color.White
    BoxWithConstraints(modifier.clip(RoundedCornerShape(4.dp)).background(if (dark) TUD_BLUE else light)) {
        val cm: Dp = maxWidth / SLIDE_W_CM
        fun at(v: Double): Dp = cm * v.toFloat()
        fun pt(v: Number): TextUnit = with(density) { (cm * (v.toFloat() * 0.03528f)).toSp() }
        fun style(size: Number, color: Color, bold: Boolean = false, italic: Boolean = false, align: TextAlign = TextAlign.Start, medium: Boolean = false) = TextStyle(
            fontFamily = FontFamily.SansSerif, fontSize = pt(size), lineHeight = pt(size.toFloat() * 1.2f), color = color,
            fontWeight = if (bold) FontWeight.SemiBold else if (medium) FontWeight.Medium else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, textAlign = align,
        )

        @Composable
        fun area(x: Double, y: Double, w: Double, h: Double, align: Alignment = Alignment.TopStart, content: @Composable () -> Unit) {
            Box(Modifier.offset(at(x), at(y)).size(at(w), at(h)), contentAlignment = align) { content() }
        }

        /** The Shape in Blau 2, cut off at the top right. */
        @Composable
        fun shape() {
            Canvas(Modifier.matchParentSize()) {
                val k = size.width / SLIDE_W_CM.toFloat()
                val ox = TudSlide.SHAPE_X.toFloat() * k
                val oy = TudSlide.SHAPE_Y.toFloat() * k
                val side = TudSlide.SHAPE_SIDE.toFloat() * k
                val path = androidx.compose.ui.graphics.Path()
                TudBrand.SHAPE.forEach { poly ->
                    path.moveTo(ox + poly[0] * side / 600f, oy + poly[1] * side / 600f)
                    for (i in 2 until poly.size step 2) path.lineTo(ox + poly[i] * side / 600f, oy + poly[i + 1] * side / 600f)
                    path.close()
                }
                drawPath(path, TUD_LIGHT_BLUE)
            }
        }

        @Composable
        fun chrome() {
            (if (dark) shortWhite else shortBlue)?.let {
                area(TudSlide.X, TudSlide.FOOT_Y, TudSlide.FOOT_LOGO_H * TudBrand.LOGO_SHORT_RATIO, TudSlide.FOOT_LOGO_H) {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            area(TudSlide.FOOT_TEXT_X, TudSlide.FOOT_Y - 0.05, TudSlide.FOOT_TEXT_W, 0.6, Alignment.CenterStart) {
                Text(TudSlide.footer(frame.footer, number), style = style(TudSlide.FOOT_PT, if (dark) Color.White else TUD_BLUE), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        @Composable
        fun heading(title: String) {
            val size = if (title.length > 55) TudSlide.TITLE_LONG_PT else TudSlide.TITLE_PT
            area(TudSlide.X, TudSlide.TITLE_Y, TudSlide.CONTENT_W, TudSlide.TITLE_H) {
                Text(title.ifBlank { "Folientitel" }, style = style(size, Color.Black, medium = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        val top = TudSlide.BODY_TOP
        val bodyH = TudSlide.BODY_BOTTOM - TudSlide.BODY_TOP
        val colW = 14.8
        val col2 = TudSlide.X + TudSlide.CONTENT_W - colW
        @Composable
        fun whiteLogo() = logoWhite?.let {
            area(TudSlide.X, TudSlide.LOGO_Y, TudSlide.LOGO_H * 1190 / 400, TudSlide.LOGO_H) { Image(it, contentDescription = "TU Dresden", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        when (s.layout) {
            SlideDeck.Layout.TITLE -> {
                val lines = s.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                whiteLogo()
                if (lines.isNotEmpty()) area(TudSlide.X, TudSlide.SUBTITLE_Y, TudSlide.CONTENT_W, TudSlide.SUBTITLE_H, Alignment.BottomStart) {
                    Column { lines.take(5).forEach { Text(plainInline(it), style = style(if (lines.size > 3) 16 else 20, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
                area(TudSlide.X, TudSlide.COVER_TITLE_Y, TudSlide.CONTENT_W, TudSlide.COVER_TITLE_H) {
                    Text(s.title.ifBlank { tr("Titel") }, style = style(if (s.title.length > 60) 36 else TudSlide.COVER_TITLE_PT, Color.White, medium = true), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (frame.unit.isNotEmpty()) area(TudSlide.X, TudSlide.COVER_FOOT_Y, 21.76, 0.71) {
                    Text(frame.unit.joinToString(" · "), style = style(12, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SlideDeck.Layout.SECTION -> {
                shape()
                area(TudSlide.X, 7.9, TudSlide.CONTENT_W, 5.0, Alignment.BottomStart) { Text(s.title.ifBlank { "Abschnitt" }, style = style(36, Color.White, medium = true), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (s.body.isNotBlank()) area(TudSlide.X, 13.8, TudSlide.CONTENT_W, 2.43) { Text(plainInline(s.body.lines().joinToString(" ")), style = style(20, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                chrome()
            }
            SlideDeck.Layout.QUOTE -> {
                if (s.title.isNotBlank()) heading(s.title)
                val q = s.body.lines().map { it.trim().removePrefix(">").trim() }.filter { it.isNotEmpty() }
                val source = q.lastOrNull()?.takeIf { q.size > 1 && (it.startsWith("—") || it.startsWith("–") || it.startsWith("-")) }
                val quote = (if (source != null) q.dropLast(1) else q).joinToString(" ").trim().trim('„', '“', '"')
                val qTop = if (s.title.isNotBlank()) top else 2.43
                area(TudSlide.X, qTop, TudSlide.CONTENT_W, TudSlide.BODY_BOTTOM - qTop) {
                    Column {
                        Text("„$quote“", style = style(if (quote.length > 220) 24 else 32, Color.Black, italic = true), maxLines = 7, overflow = TextOverflow.Ellipsis)
                        if (source != null) Text(source, style = style(14, Color.Black), modifier = Modifier.padding(top = with(LocalDensity.current) { pt(14).toDp() }))
                    }
                }
                chrome()
            }
            SlideDeck.Layout.CONTENT, SlideDeck.Layout.TWO_COLUMNS, SlideDeck.Layout.TABLE -> {
                heading(s.title)
                if (s.layout == SlideDeck.Layout.TWO_COLUMNS) {
                    area(TudSlide.X, top, colW, bodyH) { SlideContent(s.body, half = true, ::pt, ::style) }
                    area(col2, top, colW, bodyH) { SlideContent(s.right, half = true, ::pt, ::style) }
                } else area(TudSlide.X, top, TudSlide.CONTENT_W, bodyH) { SlideContent(s.body, half = false, ::pt, ::style) }
                chrome()
            }
            SlideDeck.Layout.IMAGE_TEXT -> {
                heading(s.title)
                val (ix, tx) = if (s.imageLeft) TudSlide.X to col2 else col2 to TudSlide.X
                area(ix, top, colW, bodyH) { SlidePicture(s.image, s.imageAlt) }
                area(tx, top, colW, bodyH) { SlideContent(s.body, half = true, ::pt, ::style) }
                chrome()
            }
            SlideDeck.Layout.IMAGE -> {
                val withTitle = s.title.isNotBlank()
                if (withTitle) heading(s.title)
                val caption = s.imageAlt.isNotBlank() && s.imageAlt != s.title
                val pTop = if (withTitle) top else 1.12
                area(TudSlide.X, pTop, TudSlide.CONTENT_W, (if (caption) 15.4 else TudSlide.BODY_BOTTOM) - pTop) { SlidePicture(s.image, s.imageAlt) }
                if (caption) area(TudSlide.X, 15.5, TudSlide.CONTENT_W, 0.7) { Text(s.imageAlt, style = style(12, TUD_GREY), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                chrome()
            }
            SlideDeck.Layout.COMPARISON -> {
                heading(s.title)
                listOf(TudSlide.X to s.body, col2 to s.right).forEach { (x, col) ->
                    val (head, rest) = SlideDeck.columnHead(col)
                    var y = top
                    if (head.isNotBlank()) {
                        area(x, y, colW, 1.3, Alignment.CenterStart) {
                            Box(Modifier.fillMaxSize().background(TUD_TINT).padding(start = at(0.4)), contentAlignment = Alignment.CenterStart) {
                                Text(plainInline(head), style = style(20, TUD_BLUE, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        y += 1.7
                    }
                    area(x, y, colW, TudSlide.BODY_BOTTOM - y) { SlideContent(rest, half = true, ::pt, ::style) }
                }
                chrome()
            }
            SlideDeck.Layout.CHART -> {
                heading(s.title)
                val spec = remember(s.body) { SlideDeck.chartOf(s.body) }
                area(TudSlide.X, top, TudSlide.CONTENT_W, bodyH, Alignment.Center) {
                    if (spec != null) Box(Modifier.fillMaxHeight().aspectRatio(io.github.thepytales.llmhub.docs.ChartLayout.W / io.github.thepytales.llmhub.docs.ChartLayout.H)) { ChartCanvas(spec, Modifier.fillMaxSize()) }
                    else Text(tr("Diagramm"), style = style(18, TUD_GREY))
                }
                chrome()
            }
            SlideDeck.Layout.AGENDA -> {
                heading(s.title.ifBlank { "Agenda" })
                val items = if (deck.isNotEmpty()) SlideDeck.agenda(deck.mapIndexed { k, x -> if (k == number - 1) s else x }, number - 1) else SlideDeck.agenda(listOf(s), 0)
                val size = if (items.size > 7) 20 else 24
                area(TudSlide.X, top, TudSlide.CONTENT_W, bodyH) {
                    Column {
                        items.forEachIndexed { k, item ->
                            Row(Modifier.padding(top = with(LocalDensity.current) { pt(10).toDp() })) {
                                Text("${k + 1}.", style = style(size, TUD_BLUE), modifier = Modifier.width(with(LocalDensity.current) { pt(size * 1.6f).toDp() }))
                                Text(plainInline(item), style = style(size, Color.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                chrome()
            }
            SlideDeck.Layout.END -> {
                shape()
                whiteLogo()
                area(TudSlide.X, 4.82, TudSlide.CONTENT_W, 4.5, Alignment.BottomStart) {
                    Text(s.title.ifBlank { "Vielen Dank!" }, style = style(TudSlide.COVER_TITLE_PT, Color.White, medium = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                area(TudSlide.X, TudSlide.COVER_TITLE_Y, TudSlide.CONTENT_W, TudSlide.COVER_TITLE_H) {
                    Column { s.body.lines().map { it.trim().removePrefix("- ") }.filter { it.isNotEmpty() }.take(5).forEach { Text(plainInline(it), style = style(20, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
            }
            SlideDeck.Layout.FREE -> {
                if (s.title.isNotBlank()) heading(s.title)
                if (s.body.isNotBlank()) area(TudSlide.X, top, TudSlide.CONTENT_W, bodyH) { SlideContent(s.body, half = false, ::pt, ::style) }
                if (master?.logo != false) chrome()
            }
        }
        // Frei gestalten (AP-16): the freely placed elements on top.
        s.elements.forEach { el -> area(el.x, el.y, el.w, el.h) { SlideElementView(el, master, ::pt) } }
    }
}

/** A colour from „#RRGGBB“, null if it is not one. */
internal fun parseHexColor(hex: String): Color? = hex.trim().removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** One freely placed element of a slide: text box, picture or shape (the export writes the same as PowerPoint shapes). */
@Composable
internal fun SlideElementView(el: io.github.thepytales.llmhub.docs.SlideElement, master: io.github.thepytales.llmhub.docs.SlideMaster?, pt: (Number) -> TextUnit) {
    val accent = master?.accent?.let { parseHexColor(it) } ?: TUD_BLUE
    val color = parseHexColor(el.color) ?: if (el.kind == "form") Color.White else TUD_BLUE
    val align = when (el.align) { "mitte" -> TextAlign.Center; "rechts" -> TextAlign.End; else -> TextAlign.Start }
    val textStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = pt(el.size), lineHeight = pt(el.size * 1.2f), color = color, fontWeight = if (el.bold) FontWeight.SemiBold else FontWeight.Normal, textAlign = align)
    when (el.kind) {
        "bild" -> SlidePicture(el.src, el.text)
        "form" -> {
            val fill = parseHexColor(el.fill) ?: accent
            when (el.shape) {
                "linie" -> androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawLine(parseHexColor(el.color) ?: accent, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx())
                }
                "pfeil" -> androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val head = minOf(w * 0.35f, h)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, h * 0.25f); lineTo(w - head, h * 0.25f); lineTo(w - head, 0f); lineTo(w, h / 2); lineTo(w - head, h); lineTo(w - head, h * 0.75f); lineTo(0f, h * 0.75f); close()
                    }
                    drawPath(path, fill)
                }
                else -> Box(
                    Modifier.fillMaxSize().background(fill, when (el.shape) { "ellipse" -> androidx.compose.foundation.shape.CircleShape; "abgerundet" -> RoundedCornerShape(18); else -> RoundedCornerShape(0.dp) }),
                    contentAlignment = Alignment.Center,
                ) { if (el.text.isNotBlank()) Text(el.text, style = textStyle, modifier = Modifier.fillMaxWidth().padding(4.dp)) }
            }
        }
        else -> Box(Modifier.fillMaxSize().then(parseHexColor(el.fill)?.let { Modifier.background(it) } ?: Modifier).padding(2.dp)) {
            Text(el.text.ifBlank { " " }, style = textStyle, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Picture of a slide: choose it, its description and its place (left, right, whole slide). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageFields(s: SlideDeck.Slide, enabled: Boolean, onChange: (SlideDeck.Slide) -> Unit) {
    var alt by remember(s.image) { mutableStateOf(s.imageAlt) }
    LaunchedEffect(alt) {
        delay(600)
        if (alt != s.imageAlt) onChange(s.copy(imageAlt = alt))
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (s.image.isBlank()) HintText(tr("Noch kein Bild – über das Bild-Symbol in der Leiste oben einfügen."))
        OutlinedTextField(alt, { alt = it }, label = { Text(tr("Bildbeschreibung (Alternativtext, beim Vollbild auch Bildunterschrift)")) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(s.layout == SlideDeck.Layout.IMAGE_TEXT && s.imageLeft, { onChange(s.copy(layout = SlideDeck.Layout.IMAGE_TEXT, imageLeft = true)) }, label = { Text(tr("Bild links")) }, enabled = enabled)
            FilterChip(s.layout == SlideDeck.Layout.IMAGE_TEXT && !s.imageLeft, { onChange(s.copy(layout = SlideDeck.Layout.IMAGE_TEXT, imageLeft = false)) }, label = { Text(tr("Bild rechts")) }, enabled = enabled)
            FilterChip(s.layout == SlideDeck.Layout.IMAGE, { onChange(s.copy(layout = SlideDeck.Layout.IMAGE)) }, label = { Text(tr("Ganze Folie")) }, enabled = enabled)
            if (s.image.isNotBlank()) TextButton(onClick = { onChange(s.copy(image = "", imageAlt = "")) }, enabled = enabled) { Text(tr("Bild entfernen")) }
        }
    }
}

/** A picture of the slide in its area (fitted, centred). */
@Composable
private fun SlidePicture(src: String, alt: String) {
    val bitmap = if (src.isBlank()) null else rememberDocImage(src)
    if (bitmap != null) Image(bitmap, contentDescription = alt.ifBlank { tr("Bild") }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    else Box(Modifier.fillMaxSize().background(TUD_TINT), contentAlignment = Alignment.Center) {
        Text(if (src.isBlank()) tr("Bild") else tr("Bild nicht gefunden"), color = TUD_GREY, style = MaterialTheme.typography.labelSmall)
    }
}

/** Text without inline Markdown marks, for single lines on the slide. */
private fun plainInline(s: String) = inlineSegments(s).joinToString("") { it.text }

/** Content of a slide with the font size the export chooses for its amount of text. */
@Composable
private fun SlideContent(
    md: String,
    half: Boolean,
    pt: (Number) -> TextUnit,
    style: (Number, Color, Boolean, Boolean, TextAlign) -> TextStyle,
) {
    val blocks = remember(md) { parseBlocks(md) }
    val perLine = if (half) 34 else 72
    val lines = blocks.filter { it !is Block.Table }.sumOf { b ->
        when (b) {
            is Block.Paragraph -> b.text.lines().sumOf { 1 + it.length / perLine }
            is Block.Item -> 1 + b.text.length / perLine
            is Block.Heading -> 1 + b.text.length / perLine
            is Block.Quote -> b.text.lines().size
            is Block.Code -> b.code.lines().size
            else -> 0
        }
    }
    val size = when {
        lines <= 6 -> if (half) 20 else 24
        lines <= 8 -> if (half) 18 else 22
        lines <= 10 -> 18
        lines <= 13 -> 16
        else -> 14
    }
    val dark = Color(0xFF1A1A1A)
    Column(Modifier.fillMaxSize()) {
        var number = 0
        blocks.forEach { b ->
            when (b) {
                is Block.Paragraph -> b.text.lines().forEach { l -> Text(inlineMarkdown(l, TUD_TINT, TUD_MID_BLUE), style = style(size, dark, false, false, TextAlign.Start), modifier = Modifier.padding(top = with(LocalDensity.current) { pt(6).toDp() })) }
                is Block.Heading -> Text(plainInline(b.text), style = style(size, TUD_BLUE, false, false, TextAlign.Start).copy(fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = with(LocalDensity.current) { pt(10).toDp() }))
                is Block.Item -> {
                    val numbered = b.marker.first().isDigit()
                    if (numbered) number++ else number = 0
                    val box = Regex("^\\[([ xX])]\\s+").find(b.text)
                    val text = if (box != null) (if (box.groupValues[1].isBlank()) "☐ " else "☑ ") + b.text.substring(box.range.last + 1) else b.text
                    val s = if (b.indent > 0) size - 2 else size
                    val indentDp = with(LocalDensity.current) { pt(s * 1.6f * b.indent.coerceAtMost(2)).toDp() }
                    Row(Modifier.padding(start = indentDp, top = with(LocalDensity.current) { pt(6).toDp() })) {
                        Text(if (numbered) "$number." else "•", style = style(s, TUD_BLUE, false, false, TextAlign.Start), modifier = Modifier.width(with(LocalDensity.current) { pt(s * 1.3f).toDp() }))
                        Text(inlineMarkdown(text, TUD_TINT, TUD_MID_BLUE), style = style(s, dark, false, false, TextAlign.Start))
                    }
                }
                is Block.Quote -> Text(plainInline(b.text), style = style(size, TUD_BLUE, false, true, TextAlign.Start))
                is Block.Code -> Text(b.code, style = style((size - 6).coerceAtLeast(10), dark, false, false, TextAlign.Start).copy(fontFamily = FontFamily.Monospace))
                is Block.Table -> SlideTable(b.rows, pt, style, if (b.rows.size > 8) 11 else 14)
                Block.Rule -> {}
            }
        }
    }
}

@Composable
private fun SlideTable(rows: List<List<String>>, pt: (Number) -> TextUnit, style: (Number, Color, Boolean, Boolean, TextAlign) -> TextStyle, size: Int) {
    val cols = rows.maxOfOrNull { it.size } ?: return
    val pad = with(LocalDensity.current) { pt(size * 0.4f).toDp() }
    Column(Modifier.padding(top = pad).border(0.5.dp, TUD_LINE)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().background(if (r == 0) TUD_BLUE else if (r % 2 == 0) TUD_TINT else Color.White)) {
                (0 until cols).forEach { c ->
                    Text(
                        plainInline(row.getOrElse(c) { "" }), style = style(size, if (r == 0) Color.White else Color(0xFF1A1A1A), r == 0, false, TextAlign.Start),
                        modifier = Modifier.weight(1f).padding(horizontal = pad, vertical = pad / 2), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen presentation: ←/→, Space or tap to go on, Pos1/Ende, a number and Enter jumps to that slide, B / W
 * black or white screen, L laser pointer (a red dot follows mouse or finger), N notes, Esc ends. On wide screens the
 * presenter view shows the notes, the next slide and a timer beside the slide.
 */
@Composable
private fun Presenter(slides: List<SlideDeck.Slide>, start: Int, frame: DeckFrame, narrow: Boolean, onClose: () -> Unit) {
    var i by remember { mutableIntStateOf(start) }
    var notes by remember { mutableStateOf(!narrow) }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    /** Black or white instead of the slide (null = slide). */
    var blank by remember { mutableStateOf<Color?>(null) }
    var laser by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    // Aufbau (AP-51): how many points of the slide are shown; Zeichnen: strokes per slide.
    var step by remember { mutableIntStateOf(0) }
    var pen by remember { mutableStateOf(false) }
    val ink = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<List<Offset>>>() }
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Zweiter Bildschirm (AP-51): with a second screen the slides go there, the Referentenansicht stays here.
    val canSecond = remember { PresentScreens.hasSecond() }
    var second by remember { mutableStateOf(canSecond) }
    var stageSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    /** Digits typed for "number + Enter". */
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    LaunchedEffect(typed) { if (typed.isNotEmpty()) { delay(3000); typed = "" } }
    val focus = remember { FocusRequester() }
    fun go(to: Int) {
        val next = to.coerceIn(0, slides.lastIndex)
        // Going back shows the slide complete, going on starts its build.
        step = if (next < i) SlideDeck.buildCount(slides[next]) else 0
        i = next
        blank = null
    }
    /** One click on: the next point of the build, then the next slide. */
    fun forward() { if (step < SlideDeck.buildCount(slides[i])) step++ else go(i + 1) }
    fun backward() { if (step > 0) step-- else go(i - 1) }
    /** The slide as the audience sees it (black/white, transition, build, ink, laser); [map] places pointer and ink on it. */
    val stage: @Composable (map: (Offset) -> Offset) -> Unit = { map ->
        if (blank != null) Box(Modifier.fillMaxSize().background(blank!!))
        else androidx.compose.animation.AnimatedContent(
            targetState = i,
            transitionSpec = {
                val forwardMove = targetState > initialState
                when (slides.getOrNull(targetState)?.transition) {
                    "fade" -> androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(450))
                    "push" -> androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(450)) { w -> if (forwardMove) w else -w } togetherWith
                        androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(450)) { w -> if (forwardMove) -w else w }
                    else -> androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                }
            },
            label = "folie",
        ) { k ->
            val shown = if (k == i) SlideDeck.buildStep(slides[k], step) else slides[k]
            SlideCanvas(shown, Modifier.fillMaxWidth().aspectRatio(16f / 9f), k + 1, slides.size, frame, slides)
        }
        val lines = ink[i].orEmpty() + listOf(stroke).filter { it.size > 1 }
        if (lines.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            lines.forEach { pts -> pts.zipWithNext { a, b -> drawLine(Color(0xFFE6001A), map(a), map(b), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) } }
        }
        val dot = pointer
        if (laser && dot != null) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x55FF0000), radius = 18.dp.toPx(), center = map(dot))
            drawCircle(Color(0xFFFF1A1A), radius = 7.dp.toPx(), center = map(dot))
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AudienceScreen(second, onClose = { second = false }) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val size = androidx.compose.ui.unit.IntSize(constraints.maxWidth, constraints.maxHeight)
                stage { p -> audiencePoint(p, stageSize, size) }
            }
        }
        Column(
            Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = when (e.key) {
                    Key.Zero, Key.NumPad0 -> 0; Key.One, Key.NumPad1 -> 1; Key.Two, Key.NumPad2 -> 2; Key.Three, Key.NumPad3 -> 3
                    Key.Four, Key.NumPad4 -> 4; Key.Five, Key.NumPad5 -> 5; Key.Six, Key.NumPad6 -> 6; Key.Seven, Key.NumPad7 -> 7
                    Key.Eight, Key.NumPad8 -> 8; Key.Nine, Key.NumPad9 -> 9; else -> null
                }
                if (digit != null) { typed = (typed + digit).takeLast(3); return@onKeyEvent true }
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        val n = typed.toIntOrNull()
                        typed = ""
                        if (n != null) go(n - 1) else forward()
                        true
                    }
                    Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.PageDown -> { forward(); true }
                    Key.DirectionLeft, Key.DirectionUp, Key.PageUp, Key.Backspace -> { backward(); true }
                    Key.D -> { pen = !pen; laser = false; true }
                    Key.E -> { ink.remove(i); true }
                    Key.MoveHome -> { go(0); true }
                    Key.MoveEnd -> { go(slides.lastIndex); true }
                    Key.B, Key.Period -> { blank = if (blank == Color.Black) null else Color.Black; true }
                    Key.W, Key.Comma -> { blank = if (blank == Color.White) null else Color.White; true }
                    Key.L -> { laser = !laser; pen = false; pointer = null; true }
                    Key.N -> { notes = !notes; true }
                    Key.Escape -> { if (blank != null) blank = null else onClose(); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.weight(if ((notes || second) && !narrow) 2.2f else 1f).fillMaxHeight()
                        .onSizeChanged { stageSize = it }
                        .then(if (laser || pen) Modifier else Modifier.clickable { forward() })
                        // Zeichnen beim Präsentieren (D): strokes in red over the slide, E erases them.
                        .pointerInput(pen, i) {
                            if (!pen) return@pointerInput
                            detectDragGestures(
                                onDragStart = { stroke = listOf(it) },
                                onDragEnd = { if (stroke.size > 1) ink[i] = ink[i].orEmpty() + listOf(stroke); stroke = emptyList() },
                                onDragCancel = { stroke = emptyList() },
                            ) { change, _ -> change.consume(); stroke = stroke + change.position }
                        }
                        .pointerInput(laser) {
                            if (!laser) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    pointer = if (event.type == PointerEventType.Exit || (event.type == PointerEventType.Release && change.type != androidx.compose.ui.input.pointer.PointerType.Mouse)) null else change.position
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    stage { it }
                }
                if ((notes || second) && !narrow) {
                    Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1C1C1C)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("Nächste Folie"), color = Color(0xFFBBBBBB), style = MaterialTheme.typography.labelMedium)
                        slides.getOrNull(i + 1)?.let { next ->
                            SlideCanvas(next, Modifier.fillMaxWidth().aspectRatio(16f / 9f), i + 2, slides.size, frame, slides)
                        } ?: Text(tr("Ende der Präsentation"), color = Color.White)
                        Text(tr("Notizen"), color = Color(0xFFBBBBBB), style = MaterialTheme.typography.labelMedium)
                        Text(slides[i].notes.ifBlank { tr("Keine Notizen zu dieser Folie.") }, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
                        Text(
                            tr("←/→ blättern · Zahl + Enter: Folie · B/W: schwarz/weiß · L: Laserpointer · D: zeichnen, E: löschen · N: Notizen · Esc: Ende"),
                            color = Color(0xFF888888), style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (notes && narrow && slides[i].notes.isNotBlank()) {
                Text(slides[i].notes, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = tr("Beenden (Esc)"), tint = Color.White) }
                val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(18.dp))
                Text(
                    "%d:%02d".format(secs / 60, secs % 60), color = Color.White,
                    modifier = Modifier.padding(start = 4.dp).clickable { startedAt = System.currentTimeMillis(); now = startedAt },
                )
                Text(
                    if (typed.isNotEmpty()) tr("Gehe zu Folie {0} … (Enter)", typed) else "${i + 1} / ${slides.size}",
                    color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconButton(onClick = { laser = !laser; pointer = null }) {
                    Icon(Icons.Outlined.Circle, contentDescription = if (laser) tr("Laserpointer aus (L)") else tr("Laserpointer (L)"), tint = if (laser) Color(0xFFFF4040) else Color.White)
                }
                IconButton(onClick = { pen = !pen; laser = false }) {
                    Icon(Icons.Outlined.Edit, contentDescription = if (pen) tr("Zeichnen aus (D)") else tr("Zeichnen (D)"), tint = if (pen) Color(0xFFFF4040) else Color.White)
                }
                IconButton(onClick = { blank = if (blank == Color.Black) null else Color.Black }) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = tr("Schwarzer Bildschirm (B)"), tint = if (blank == Color.Black) Color(0xFF97C6FF) else Color.White)
                }
                IconButton(onClick = { notes = !notes }) { Icon(Icons.Outlined.SpeakerNotes, contentDescription = tr("Notizen (N)"), tint = Color.White) }
                if (canSecond) IconButton(onClick = { second = !second }) {
                    Icon(Icons.Outlined.Slideshow, contentDescription = if (second) tr("Folien nur auf diesem Bildschirm") else tr("Folien auf dem zweiten Bildschirm"), tint = if (second) Color(0xFF97C6FF) else Color.White)
                }
                IconButton(onClick = { backward() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = tr("Zurück"), tint = Color.White) }
                IconButton(onClick = { forward() }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = tr("Weiter"), tint = Color.White) }
            }
        }
    }
}


/**
 * Where point [p] of the presenter's slide area ([from]) lies on the audience's screen ([to]): both show the 16:9 slide
 * centred, so ink and laser land on the same spot of the slide.
 */
internal fun audiencePoint(p: Offset, from: androidx.compose.ui.unit.IntSize, to: androidx.compose.ui.unit.IntSize): Offset {
    fun slide(s: androidx.compose.ui.unit.IntSize): androidx.compose.ui.geometry.Rect {
        val w = minOf(s.width.toFloat(), s.height * 16f / 9f)
        val h = w * 9f / 16f
        return androidx.compose.ui.geometry.Rect(Offset((s.width - w) / 2, (s.height - h) / 2), androidx.compose.ui.geometry.Size(w, h))
    }
    val a = slide(from)
    val b = slide(to)
    if (a.width <= 0f || a.height <= 0f) return p
    return Offset(b.left + (p.x - a.left) * b.width / a.width, b.top + (p.y - a.top) * b.height / a.height)
}

private val FREE_COLORS = listOf("#00008C", "#2F57B2", "#97C6FF", "#566371", "#000000", "#FFFFFF", "#1B7F3B", "#B3261E", "#E37222", "#F2B01E")

/** The elements over the slide preview: tap to select, drag to move, the corner handle to resize (in cm, AP-16). */
@Composable
private fun FreeOverlay(elements: List<io.github.thepytales.llmhub.docs.SlideElement>, picked: Int?, onPick: (Int?) -> Unit, onChange: (List<io.github.thepytales.llmhub.docs.SlideElement>) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pxPerCm = with(density) { maxWidth.toPx() } / SLIDE_W_CM
        val cm: Dp = maxWidth / SLIDE_W_CM
        elements.forEachIndexed { i, el ->
            var dx by remember(el) { mutableStateOf(0f) }
            var dy by remember(el) { mutableStateOf(0f) }
            var dw by remember(el) { mutableStateOf(0f) }
            var dh by remember(el) { mutableStateOf(0f) }
            val x = el.x + dx / pxPerCm
            val y = el.y + dy / pxPerCm
            val w = (el.w + dw / pxPerCm).coerceAtLeast(0.5)
            val h = (el.h + dh / pxPerCm).coerceAtLeast(0.3)
            fun commit() {
                if (dx != 0f || dy != 0f || dw != 0f || dh != 0f) {
                    val r = { v: Double -> Math.round(v * 10) / 10.0 }
                    val moved = el.copy(x = r(x.coerceIn(-5.0, SLIDE_W_CM.toDouble())), y = r(y.coerceIn(-5.0, 19.05)), w = r(w), h = r(h))
                    // A group moves together (shared canvas, AP-50).
                    val ddx = moved.x - el.x
                    val ddy = moved.y - el.y
                    val together = el.group.isNotEmpty() && dw == 0f && dh == 0f
                    onChange(elements.mapIndexed { j, o -> if (j == i) moved else if (together && o.group == el.group) o.copy(x = r(o.x + ddx), y = r(o.y + ddy)) else o })
                }
            }
            Box(
                Modifier.offset(cm * x.toFloat(), cm * y.toFloat()).size(cm * w.toFloat(), cm * h.toFloat())
                    .border(if (picked == i) 2.dp else 1.dp, if (picked == i) Color(0xFF2F57B2) else Color(0x332F57B2))
                    .clickable { onPick(i) }
                    .pointerInput(el) {
                        detectDragGestures(
                            onDragStart = { onPick(i) },
                            onDrag = { change, d -> change.consume(); dx += d.x; dy += d.y },
                            onDragEnd = { commit() },
                            onDragCancel = { commit() },
                        )
                    },
            ) {
                if (picked == i) Box(
                    Modifier.align(Alignment.BottomEnd).size(14.dp).background(Color(0xFF2F57B2)).pointerInput(el) {
                        detectDragGestures(
                            onDrag = { change, d -> change.consume(); dw += d.x; dh += d.y },
                            onDragEnd = { commit() },
                            onDragCancel = { commit() },
                        )
                    },
                )
            }
        }
    }
}

/**
 * Frei gestalten (AP-16 Stufe 3): insert text boxes, pictures and shapes on any slide, change the selected one (text,
 * colours, size, order) and the Masterfolie of the deck (background, accent, footer, logo).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeDesignBar(
    c: AppContainer, doc: LiveDoc, slides: List<SlideDeck.Slide>, sel: Int, s: SlideDeck.Slide, picked: Int?,
    onPick: (Int?) -> Unit, updateAt: (Int, SlideDeck.Slide) -> Unit, onChange: (SlideDeck.Slide) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val base = LocalDocBase.current
    var shapes by remember { mutableStateOf(false) }
    var masterOpen by remember { mutableStateOf(false) }
    fun add(el: io.github.thepytales.llmhub.docs.SlideElement) {
        onChange(s.copy(elements = s.elements + el))
        onPick(s.elements.size)
    }
    val pickImage = rememberImageBytesPicker { bytes ->
        if (bytes == null || DocMarkup.imageType(bytes) == null) return@rememberImageBytesPicker
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { DocMarkup.storeImage(base ?: c.docs.baseDir(doc), bytes) }.getOrNull() } ?: return@launch
            val ratio = DocMarkup.imageSize(bytes)?.let { (w, h) -> h.toDouble() / w.coerceAtLeast(1) } ?: 0.75
            add(io.github.thepytales.llmhub.docs.SlideElement(kind = "bild", x = 8.0, y = 4.0, w = 12.0, h = (12.0 * ratio).coerceIn(2.0, 14.0), src = path))
        }
    }
    Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AssistChip(onClick = { add(io.github.thepytales.llmhub.docs.SlideElement(kind = "text", text = tr("Text"))) }, label = { Text(tr("Textfeld")) })
            AssistChip(onClick = pickImage, label = { Text(tr("Bild …")) })
            Box {
                AssistChip(onClick = { shapes = true }, label = { Text(tr("Form ▾")) })
                DropdownMenu(shapes, { shapes = false }) {
                    listOf("rechteck" to tr("Rechteck"), "abgerundet" to tr("Abgerundetes Rechteck"), "ellipse" to tr("Ellipse"), "pfeil" to tr("Pfeil"), "linie" to tr("Linie")).forEach { (k, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            shapes = false
                            add(io.github.thepytales.llmhub.docs.SlideElement(kind = "form", shape = k, w = if (k == "linie") 10.0 else 8.0, h = if (k == "linie") 0.6 else 4.0, color = if (k == "linie") "#00008C" else "#FFFFFF"))
                        })
                    }
                }
            }
            AssistChip(onClick = { masterOpen = true }, label = { Text(tr("Masterfolie …")) })
            // Übergang und Aufbau (AP-51), beim Vorführen und in PowerPoint.
            var animMenu by remember { mutableStateOf(false) }
            Box {
                AssistChip(onClick = { animMenu = true }, label = { Text(tr("Animation: {0}", listOfNotNull(when (s.transition) { "fade" -> tr("Überblenden"); "push" -> tr("Schieben"); else -> null }, if (s.build) tr("Punkte nacheinander") else null).joinToString(", ").ifBlank { tr("keine") })) })
                DropdownMenu(expanded = animMenu, onDismissRequest = { animMenu = false }) {
                    listOf("" to tr("Kein Übergang"), "fade" to tr("Überblenden"), "push" to tr("Schieben")).forEach { (k, label) ->
                        DropdownMenuItem(text = { Text((if (s.transition == k) "✓ " else "") + label) }, onClick = { animMenu = false; onChange(s.copy(transition = k)) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text((if (s.build) "✓ " else "") + tr("Stichpunkte nacheinander einblenden")) }, onClick = { animMenu = false; onChange(s.copy(build = !s.build)) })
                }
            }
            if (s.layout != SlideDeck.Layout.FREE && sel > 0) AssistChip(onClick = { onChange(s.copy(layout = SlideDeck.Layout.FREE)) }, label = { Text(tr("Leere Folie (frei)")) })
        }
        val el = picked?.let { s.elements.getOrNull(it) }
        if (el != null) {
            fun set(next: io.github.thepytales.llmhub.docs.SlideElement) = onChange(s.copy(elements = s.elements.toMutableList().apply { set(picked, next) }))
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when (el.kind) { "bild" -> tr("Bild"); "form" -> tr("Form"); else -> tr("Textfeld") } + " ${picked + 1}", style = MaterialTheme.typography.labelLarge)
                    if (el.kind != "bild" && el.shape != "linie") {
                        var text by remember(el) { mutableStateOf(el.text) }
                        LaunchedEffect(text) { delay(500); if (text != el.text) set(el.copy(text = text)) }
                        OutlinedTextField(text, { text = it }, label = { Text(tr("Text")) }, modifier = Modifier.fillMaxWidth(), minLines = 1)
                    } else if (el.kind == "bild") {
                        var alt by remember(el) { mutableStateOf(el.text) }
                        LaunchedEffect(alt) { delay(500); if (alt != el.text) set(el.copy(text = alt)) }
                        OutlinedTextField(alt, { alt = it }, label = { Text(tr("Bildbeschreibung (Alternativtext)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (el.kind != "bild") {
                        HintText(if (el.kind == "form" && el.shape != "linie") tr("Füllung") else tr("Hintergrund"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (el.kind == "text") ColorDot(null, el.fill.isBlank()) { set(el.copy(fill = "")) }
                            if (el.shape != "linie") FREE_COLORS.forEach { col -> ColorDot(col, el.fill.equals(col, true)) { set(el.copy(fill = col)) } }
                        }
                        HintText(if (el.shape == "linie") tr("Linienfarbe") else tr("Schriftfarbe"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { FREE_COLORS.forEach { col -> ColorDot(col, el.color.equals(col, true)) { set(el.copy(color = col)) } } }
                        if (el.shape != "linie") Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { set(el.copy(size = (el.size - 2).coerceAtLeast(8))) }) { Text("A−") }
                            Text(tr("{0} pt", el.size), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { set(el.copy(size = (el.size + 2).coerceAtMost(96))) }) { Text("A+") }
                            FilterChip(el.bold, { set(el.copy(bold = !el.bold)) }, label = { Text(tr("Fett")) })
                            listOf("links" to "◧", "mitte" to "▣", "rechts" to "◨").forEach { (k, sym) -> FilterChip(el.align == k, { set(el.copy(align = k)) }, label = { Text(sym) }) }
                        }
                    }
                    // The same operations as Erstellen › Gestalten (shared canvas, AP-50): align to the slide, layers, groups.
                    HintText(tr("Ausrichten auf der Folie"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("left" to "⇤", "hcenter" to "↔", "right" to "⇥", "top" to "⤒", "vcenter" to "↕", "bottom" to "⤓").forEach { (mode, sym) ->
                            FilterChip(false, { onChange(s.copy(elements = io.github.thepytales.llmhub.creative.SlideCanvas.align(s.elements, setOf(picked), mode))) }, label = { Text(sym) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fun layer(up: Boolean) {
                            val next = io.github.thepytales.llmhub.creative.SlideCanvas.reorder(s.elements, setOf(picked), up, all = true)
                            onChange(s.copy(elements = next))
                            onPick(next.indexOfFirst { it == el }.takeIf { it >= 0 })
                        }
                        TextButton(onClick = { layer(true) }) { Text(tr("Nach vorn")) }
                        TextButton(onClick = { layer(false) }) { Text(tr("Nach hinten")) }
                        // Gruppieren: with the element drawn just below it (on a slide one element is picked at a time).
                        if (el.group.isNotEmpty()) TextButton(onClick = { onChange(s.copy(elements = s.elements.map { if (it.group == el.group) it.copy(group = "") else it })) }) { Text(tr("Gruppierung aufheben")) }
                        else if (picked > 0) TextButton(onClick = {
                            val id = io.github.thepytales.llmhub.data.newDesignId()
                            val below = s.elements[picked - 1].group.ifEmpty { id }
                            onChange(s.copy(elements = s.elements.mapIndexed { i, x -> if (i == picked || i == picked - 1 || (x.group.isNotEmpty() && x.group == s.elements[picked - 1].group)) x.copy(group = below) else x }))
                        }) { Text(tr("Mit dem Element darunter gruppieren")) }
                        TextButton(onClick = { onChange(s.copy(elements = s.elements + el.copy(x = el.x + 1, y = el.y + 1))); onPick(s.elements.size) }) { Text(tr("Duplizieren")) }
                        TextButton(onClick = { onChange(s.copy(elements = s.elements.toMutableList().apply { removeAt(picked) })); onPick(null) }) { Text(tr("Löschen"), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (masterOpen) {
        val first = slides.firstOrNull() ?: return
        val m = first.master ?: io.github.thepytales.llmhub.docs.SlideMaster()
        var bg by remember { mutableStateOf(m.background) }
        var accent by remember { mutableStateOf(m.accent) }
        var footer by remember { mutableStateOf(m.footer) }
        var logo by remember { mutableStateOf(m.logo) }
        AlertDialog(
            onDismissRequest = { masterOpen = false },
            title = { Text(tr("Masterfolie")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HintText(tr("Gilt für alle Folien der Präsentation (Titel- und Abschlussfolie bleiben im TUD-Blau)."))
                    Text(tr("Hintergrund"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ColorDot("#FFFFFF", bg.isBlank() || bg.equals("#FFFFFF", true)) { bg = "" }
                        listOf("#F5F7FA", "#E5F1FF", "#FFF8E1", "#EEF7EE", "#F3F0FA").forEach { col -> ColorDot(col, bg.equals(col, true)) { bg = col } }
                    }
                    Text(tr("Akzentfarbe (Formen)"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { FREE_COLORS.filter { it != "#FFFFFF" }.forEach { col -> ColorDot(col, accent.equals(col, true)) { accent = col } } }
                    OutlinedTextField(footer, { footer = it }, label = { Text(tr("Fußzeile (leer: TU Dresden · Einrichtung · Titel)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.clickable { logo = !logo }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(logo, { logo = it })
                        Text(tr("Logo und Fußzeile auf freien Folien"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    masterOpen = false
                    val next = io.github.thepytales.llmhub.docs.SlideMaster(bg, accent, footer.trim(), logo)
                    if (sel == 0) onChange(s.copy(master = next)) else updateAt(0, first.copy(master = next))
                }) { Text(tr("Übernehmen")) }
            },
            dismissButton = { TextButton(onClick = { masterOpen = false }) { Text(tr("Abbrechen")) } },
        )
    }
}

@Composable
private fun ColorDot(hex: String?, selected: Boolean, onClick: () -> Unit) {
    val color = hex?.let { parseHexColor(it) }
    Box(
        Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(color ?: Color.Transparent)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFF2F57B2) else Color(0xFFBBC1C6), androidx.compose.foundation.shape.CircleShape)
            .a11yLabel(hex?.let { tr("Farbe") + " " + it } ?: tr("Keine Füllung"))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (color == null) Text("∅", style = MaterialTheme.typography.labelSmall) }
}
