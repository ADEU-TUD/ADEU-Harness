package io.github.thepytales.llmhub.ui

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material.icons.outlined.Timer
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
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.docs.TudBrand
import kotlinx.coroutines.delay

private val TUD_BLUE = Color(0xFF00008C)
private val TUD_LIGHT_BLUE = Color(0xFF97C6FF)
private val TUD_MID_BLUE = Color(0xFF2F57B2)
private val TUD_GREY = Color(0xFF566371)
private val TUD_TINT = Color(0xFFE5F1FF)
private val TUD_LINE = Color(0xFFBBC1C6)

/** ✨ actions on a slide. */
private val SLIDE_AI = listOf(
    "Verbessern" to "Verbessere die Folie: prägnanter Titel, höchstens 5 kurze Stichpunkte, klare Sprache. Gib die Folie im selben Markdown-Format zurück (## Titel, Stichpunkte, ggf. Notizen: …).",
    "Kürzen" to "Kürze die Folie auf das Wesentliche (höchstens 4 Stichpunkte); Details wandern in „Notizen: …“. Gleiches Format.",
    "Stichpunkte ausformulieren" to "Formuliere zu den Stichpunkten ausführliche Sprechernotizen (Notizen: …); die Folie selbst bleibt knapp. Gleiches Format.",
    "Beispiel ergänzen" to "Ergänze ein anschauliches Beispiel aus Schule oder Hochschule als Stichpunkt. Gleiches Format.",
    "Als Tabelle" to "Stelle den Inhalt der Folie als übersichtliche Markdown-Tabelle dar (höchstens 5 Zeilen). Gleiches Format.",
    "In zwei Spalten" to "Teile den Inhalt sinnvoll auf zwei Spalten auf (z. B. Vorher/Nachher, Pro/Contra); die rechte Spalte beginnt nach einer Zeile „|||“. Gleiches Format.",
    "Ins Englische" to "Übersetze die Folie samt Notizen ins Englische. Gleiches Format.",
)

/**
 * What the slides show around the content, as in the exported PowerPoint: the organizational unit (title slide) and
 * the footer line ("TU Dresden · Professur … · Titel").
 */
data class DeckFrame(val unit: List<String>, val footer: String) {
    companion object {
        fun of(unit: String, footer: String, deckTitle: String): DeckFrame {
            val lines = unit.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val foot = footer.ifBlank { listOfNotNull("TU Dresden", lines.lastOrNull(), deckTitle.takeIf { it.isNotBlank() }).joinToString(" · ") }
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
    val slides = remember(markdown) { SlideDeck.parse(markdown).ifEmpty { listOf(SlideDeck.Slide("Titel", layout = SlideDeck.Layout.TITLE)) } }
    val frame = DeckFrame.of(settings.tudUnit, settings.docFooter, slides.first().title.ifBlank { doc.title })
    var selected by remember(doc.id) { mutableIntStateOf(0) }
    val sel = selected.coerceIn(0, slides.lastIndex)
    var presenting by remember { mutableStateOf<Int?>(null) }
    var sorter by remember { mutableStateOf(false) }
    fun set(list: List<SlideDeck.Slide>) = onMarkdown(SlideDeck.markdown(list))
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
    fun delete() {
        if (slides.size <= 1) return
        val removed = slides[sel]
        val at = sel
        set(slides.toMutableList().apply { removeAt(at) })
        selected = (at - 1).coerceAtLeast(0)
        Notices.undo("Folie gelöscht", restore = { set(slides.toMutableList().apply { add(at.coerceAtMost(size), removed) }) })
    }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || writing) return@onPreviewKeyEvent false
            when {
                e.key == Key.F5 -> { presenting = if (e.isShiftPressed) sel else 0; true }
                e.isCtrlPressed && e.key == Key.M -> { addSlide(SlideDeck.Layout.CONTENT); true }
                e.isCtrlPressed && e.key == Key.D -> { duplicate(); true }
                e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionUp -> { move(-1); true }
                e.isCtrlPressed && e.isShiftPressed && e.key == Key.DirectionDown -> { move(1); true }
                else -> false
            }
        },
    ) {
        // Toolbar.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            var add by remember { mutableStateOf(false) }
            Box {
                ToolLabel("+ Neue Folie ▾") { add = true }
                DropdownMenu(expanded = add, onDismissRequest = { add = false }) {
                    SlideDeck.Layout.entries.filter { it != SlideDeck.Layout.TITLE }.forEach { l ->
                        DropdownMenuItem(text = { Text(l.label) }, onClick = { add = false; addSlide(l) })
                    }
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            ToolIcon(Icons.Outlined.ContentCopy, "Folie duplizieren (Strg+D)") { duplicate() }
            ToolIcon(Icons.Outlined.ArrowUpward, "Nach vorn (Strg+Umschalt+↑)", enabled = sel > 1) { move(-1) }
            ToolIcon(Icons.Outlined.ArrowDownward, "Nach hinten (Strg+Umschalt+↓)", enabled = sel in 1 until slides.lastIndex) { move(1) }
            ToolIcon(Icons.Outlined.Delete, "Folie löschen", enabled = slides.size > 1) { delete() }
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
            ToolIcon(Icons.Outlined.GridView, "Folienübersicht", active = sorter) { sorter = !sorter }
            ToolIcon(Icons.Outlined.Slideshow, "Vorführen ab dieser Folie (Umschalt+F5)") { presenting = sel }
        }
        HorizontalDivider()
        when {
            sorter -> Sorter(slides, sel, frame, onPick = { selected = it; sorter = false })
            narrow -> {
                LazyRow(contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(slides) { i, s -> Thumb(i, s, slides.size, frame, i == sel, Modifier.width(120.dp)) { selected = i } }
                }
                SlideWork(c, doc, slides, sel, frame, writing, inlineAi, ::update, Modifier.weight(1f))
            }
            else -> Row(Modifier.weight(1f)) {
                LazyColumn(Modifier.width(190.dp).fillMaxHeight(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(slides) { i, s -> Thumb(i, s, slides.size, frame, i == sel, Modifier.fillMaxWidth()) { selected = i } }
                    item {
                        Surface(
                            onClick = { set(slides + SlideDeck.template(SlideDeck.Layout.CONTENT)); selected = slides.size },
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, contentDescription = "Folie hinzufügen (Strg+M)") } }
                    }
                }
                VerticalDivider()
                SlideWork(c, doc, slides, sel, frame, writing, inlineAi, ::update, Modifier.weight(1f))
            }
        }
    }
    presenting?.let { start -> Presenter(slides, start, frame, narrow) { presenting = null } }
}

@Composable
private fun Thumb(i: Int, s: SlideDeck.Slide, total: Int, frame: DeckFrame, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
        ) { SlideCanvas(s, Modifier.fillMaxSize(), i + 1, total, frame) }
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
        itemsIndexed(slides) { i, s -> Thumb(i, s, slides.size, frame, i == sel, Modifier.fillMaxWidth()) { onPick(i) } }
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
        Box(Modifier.widthIn(max = 820.dp).fillMaxWidth().aspectRatio(16f / 9f).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))) {
            SlideCanvas(live, Modifier.fillMaxSize(), sel + 1, slides.size, frame)
        }
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
                SlideDeck.Layout.entries.filter { sel == 0 || it != SlideDeck.Layout.TITLE }.forEach { l ->
                    FilterChip(selected = s.layout == l, onClick = { update(sel, live.copy(layout = l)) }, label = { Text(l.label) }, enabled = !writing)
                }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true, enabled = !writing, modifier = Modifier.fillMaxWidth())
            when (s.layout) {
                SlideDeck.Layout.TITLE -> {
                    OutlinedTextField(body, { body = it }, label = { Text("Untertitel") }, singleLine = true, enabled = !writing, modifier = Modifier.fillMaxWidth())
                    RichField(meta, { meta = it }, "Angaben (Vortragende, Anlass, Datum – je Zeile eine)", minLines = 2, enabled = !writing, toolbar = false)
                }
                SlideDeck.Layout.SECTION -> RichField(body, { body = it }, "Kurze Beschreibung (optional)", minLines = 1, enabled = !writing, toolbar = false)
                SlideDeck.Layout.QUOTE -> RichField(body, { body = it }, "Zitat (letzte Zeile mit „– Quelle“)", minLines = 3, enabled = !writing, toolbar = false)
                SlideDeck.Layout.TWO_COLUMNS -> {
                    RichField(body, { body = it }, "Linke Spalte", minLines = 4, enabled = !writing)
                    RichField(right, { right = it }, "Rechte Spalte", minLines = 4, enabled = !writing)
                }
                SlideDeck.Layout.CONTENT -> RichField(body, { body = it }, "Inhalt", minLines = 4, enabled = !writing)
            }
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Sprechernotizen") }, minLines = 2, enabled = !writing, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.SpeakerNotes, contentDescription = null) },
            )
            HintText(
                "Folie ${sel + 1} von ${slides.size} · Enter: neuer Stichpunkt, Tab: einrücken, Strg+B: fett · Strg+M: neue Folie, F5: vorführen · " +
                    "Export als PowerPoint im TUD-Design über ⋮ › Exportieren",
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
                Text("Für das TUD-Design fehlt die Einrichtung", style = MaterialTheme.typography.titleSmall)
            }
            HintText("Sie steht auf der Titelfolie unten und in der Fußzeile jeder Folie – eine Ebene pro Zeile (Fakultät, Institut, Professur).")
            OutlinedTextField(unit, { unit = it }, minLines = 3, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Fakultät …\nInstitut …\nProfessur …") })
            TextButton(enabled = unit.isNotBlank(), onClick = { c.settings.update { it.copy(tudUnit = unit.trim()) } }) { Text("Für alle Präsentationen übernehmen") }
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
            ToolIcon(Icons.Outlined.FormatBold, "Fett (Strg+B)", enabled = enabled) { onChange(toggleMarks(value, "**")) }
            ToolIcon(Icons.Outlined.FormatItalic, "Kursiv (Strg+I)", enabled = enabled) { onChange(toggleMarks(value, "*")) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Stichpunkte an/aus", enabled = enabled) { onChange(toggleBullets(value)) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "Einrücken (Tab)", enabled = enabled) { onChange(indentLine(value, outdent = false)) }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "Ausrücken (Umschalt+Tab)", enabled = enabled) { onChange(indentLine(value, outdent = true)) }
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
                    e.isCtrlPressed && e.key == Key.B -> { onChange(toggleMarks(value, "**")); true }
                    e.isCtrlPressed && e.key == Key.I -> { onChange(toggleMarks(value, "*")); true }
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
 * A slide drawn like the exported PowerPoint in the corporate design of TU Dresden: same positions, font sizes and
 * colours, the logo (white on the blue title slide, blue at the top right of the others), the organizational unit on
 * the title slide and the footer line with the slide number.
 */
@Composable
fun SlideCanvas(s: SlideDeck.Slide, modifier: Modifier, number: Int = 1, total: Int = 1, frame: DeckFrame = DeckFrame(emptyList(), "TU Dresden")) {
    val logoBlue = remember { runCatching { decodeImage(TudBrand.logoBlue) }.getOrNull() }
    val logoWhite = remember { runCatching { decodeImage(TudBrand.logoWhite) }.getOrNull() }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.background(if (s.layout == SlideDeck.Layout.TITLE) TUD_BLUE else Color.White, RoundedCornerShape(4.dp))) {
        val cm: Dp = maxWidth / SLIDE_W_CM
        fun at(v: Double): Dp = cm * v.toFloat()
        fun pt(v: Number): TextUnit = with(density) { (cm * (v.toFloat() * 0.03528f)).toSp() }
        fun style(size: Number, color: Color, bold: Boolean = false, italic: Boolean = false, align: TextAlign = TextAlign.Start) = TextStyle(
            fontFamily = FontFamily.SansSerif, fontSize = pt(size), lineHeight = pt(size.toFloat() * 1.2f), color = color,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, textAlign = align,
        )

        @Composable
        fun area(x: Double, y: Double, w: Double, h: Double, align: Alignment = Alignment.TopStart, content: @Composable () -> Unit) {
            Box(Modifier.offset(at(x), at(y)).size(at(w), at(h)), contentAlignment = align) { content() }
        }

        @Composable
        fun chrome() {
            logoBlue?.let { area(SLIDE_W_CM - 1.4 - 1.1 * 1190 / 400, 1.0, 1.1 * 1190 / 400, 1.1) { Image(it, contentDescription = "TU Dresden", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } }
            area(1.6, 17.6, SLIDE_W_CM - 3.2, 0.03) { Box(Modifier.fillMaxSize().background(TUD_LINE)) }
            area(1.6, 17.8, 26.0, 0.7) { Text(frame.footer, style = style(9, TUD_GREY), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            area(SLIDE_W_CM - 5.6, 17.8, 4.0, 0.7, Alignment.TopEnd) { Text("$number", style = style(9, TUD_GREY, align = TextAlign.End)) }
        }

        @Composable
        fun heading(title: String) {
            val size = if (title.length > 55) 24 else 28
            area(1.6, 1.0, 26.0, 2.6, Alignment.BottomStart) { Text(title.ifBlank { "Folientitel" }, style = style(size, TUD_BLUE, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }

        when (s.layout) {
            SlideDeck.Layout.TITLE -> {
                val lines = s.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                logoWhite?.let { area(1.6, 1.4, 1.7 * 1190 / 400, 1.7) { Image(it, contentDescription = "TU Dresden", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } }
                area(1.6, 5.6, 29.5, 4.8, Alignment.BottomStart) {
                    Text(s.title.ifBlank { "Titel" }, style = style(if (s.title.length > 60) 34 else 40, Color.White, bold = true), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                lines.firstOrNull()?.let { sub -> area(1.6, 10.7, 29.5, 2.2) { Text(plainInline(sub), style = style(22, TUD_LIGHT_BLUE), maxLines = 2, overflow = TextOverflow.Ellipsis) } }
                if (lines.size > 1) area(1.6, 13.2, 29.5, 2.4) {
                    Column { lines.drop(1).take(3).forEach { Text(plainInline(it), style = style(16, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
                if (frame.unit.isNotEmpty()) area(1.6, 15.9, 29.5, 2.4, Alignment.BottomStart) {
                    Column { frame.unit.forEachIndexed { i, l -> Text(l, style = style(11, Color.White, bold = i == frame.unit.lastIndex), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                }
            }
            SlideDeck.Layout.SECTION -> {
                area(2.5, 6.2, 28.0, 3.4, Alignment.BottomStart) { Text(s.title.ifBlank { "Abschnitt" }, style = style(36, TUD_BLUE, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (s.body.isNotBlank()) area(2.5, 9.8, 28.0, 2.0) { Text(plainInline(s.body.lines().joinToString(" ")), style = style(18, TUD_GREY), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                area(1.6, 6.4, 0.25, 5.2) { Box(Modifier.fillMaxSize().background(TUD_BLUE)) }
                chrome()
            }
            SlideDeck.Layout.QUOTE -> {
                if (s.title.isNotBlank()) heading(s.title)
                val q = s.body.lines().map { it.trim().removePrefix(">").trim() }.filter { it.isNotEmpty() }
                val source = q.lastOrNull()?.takeIf { q.size > 1 && (it.startsWith("—") || it.startsWith("–") || it.startsWith("-")) }
                val quote = (if (source != null) q.dropLast(1) else q).joinToString(" ").trim().trim('„', '“', '"')
                area(3.5, 5.0, 26.8, 9.0, Alignment.Center) {
                    Text("„$quote“", style = style(if (quote.length > 220) 24 else 30, TUD_BLUE, italic = true, align = TextAlign.Center), maxLines = 7, overflow = TextOverflow.Ellipsis)
                }
                if (source != null) area(3.5, 14.2, 26.8, 1.2, Alignment.TopCenter) { Text(source, style = style(16, TUD_GREY, align = TextAlign.Center)) }
                chrome()
            }
            SlideDeck.Layout.CONTENT, SlideDeck.Layout.TWO_COLUMNS -> {
                heading(s.title)
                if (s.layout == SlideDeck.Layout.TWO_COLUMNS) {
                    area(1.6, 4.0, 14.8, 13.0) { SlideContent(s.body, half = true, ::pt, ::style) }
                    area(17.4, 4.0, 14.8, 13.0) { SlideContent(s.right, half = true, ::pt, ::style) }
                } else area(1.6, 4.0, 30.6, 13.0) { SlideContent(s.body, half = false, ::pt, ::style) }
                chrome()
            }
        }
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
                is Block.Heading -> Text(plainInline(b.text), style = style(size, TUD_BLUE, true, false, TextAlign.Start), modifier = Modifier.padding(top = with(LocalDensity.current) { pt(10).toDp() }))
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
 * Full-screen presentation: arrows, space or tap to go on, N for notes, Esc to end. On wide screens the presenter view
 * shows the notes, the next slide and a timer beside the slide.
 */
@Composable
private fun Presenter(slides: List<SlideDeck.Slide>, start: Int, frame: DeckFrame, narrow: Boolean, onClose: () -> Unit) {
    var i by remember { mutableIntStateOf(start) }
    var notes by remember { mutableStateOf(!narrow) }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.PageDown, Key.Enter -> { i = (i + 1).coerceAtMost(slides.lastIndex); true }
                    Key.DirectionLeft, Key.DirectionUp, Key.PageUp, Key.Backspace -> { i = (i - 1).coerceAtLeast(0); true }
                    Key.MoveHome -> { i = 0; true }
                    Key.MoveEnd -> { i = slides.lastIndex; true }
                    Key.N -> { notes = !notes; true }
                    Key.Escape -> { onClose(); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(if (notes && !narrow) 2.2f else 1f).fillMaxHeight().clickable { i = (i + 1).coerceAtMost(slides.lastIndex) }, contentAlignment = Alignment.Center) {
                    SlideCanvas(slides[i], Modifier.fillMaxWidth().aspectRatio(16f / 9f), i + 1, slides.size, frame)
                }
                if (notes && !narrow) {
                    Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1C1C1C)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Nächste Folie", color = Color(0xFFBBBBBB), style = MaterialTheme.typography.labelMedium)
                        slides.getOrNull(i + 1)?.let { next ->
                            SlideCanvas(next, Modifier.fillMaxWidth().aspectRatio(16f / 9f), i + 2, slides.size, frame)
                        } ?: Text("Ende der Präsentation", color = Color.White)
                        Text("Notizen", color = Color(0xFFBBBBBB), style = MaterialTheme.typography.labelMedium)
                        Text(slides[i].notes.ifBlank { "Keine Notizen zu dieser Folie." }, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    }
                }
            }
            if (notes && narrow && slides[i].notes.isNotBlank()) {
                Text(slides[i].notes, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(16.dp))
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Beenden (Esc)", tint = Color.White) }
                val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(18.dp))
                Text(
                    "%d:%02d".format(secs / 60, secs % 60), color = Color.White,
                    modifier = Modifier.padding(start = 4.dp).clickable { startedAt = System.currentTimeMillis(); now = startedAt },
                )
                Text("${i + 1} / ${slides.size}", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { notes = !notes }) { Icon(Icons.Outlined.SpeakerNotes, contentDescription = "Notizen (N)", tint = Color.White) }
                IconButton(onClick = { i = (i - 1).coerceAtLeast(0) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = Color.White) }
                IconButton(onClick = { i = (i + 1).coerceAtMost(slides.lastIndex) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Weiter", tint = Color.White) }
            }
        }
    }
}
