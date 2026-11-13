package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.video.ExportQueue
import io.github.thepytales.llmhub.video.VideoProject

/*
 * Der Videoeditor für Einsteiger (AP-121): the start cards of the project list, „So entsteht dein Video“ with the next
 * step and buttons that do it, labelled buttons where there is room, the help menu, the „Gespeichert“ badge and the
 * right-click menus of the timeline. One view for everyone – the helpers sit where they are needed and fold away.
 */

// ── Project list ───────────────────────────────────────────────────────────────────────────────────

/** A big card to begin with: symbol, what it is, one line how. The whole card is the button. */
@Composable
internal fun StartCard(icon: ImageVector, tint: Color, title: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = LocalChatLook.current.card, border = boxBorder(),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = title, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                HintText(text)
            }
        }
    }
}

/** The four steps of a video, in one quiet line under the start cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StepsLine(modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GUIDE_STEPS.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepNumber(i + 1, done = false, small = true)
                Spacer(Modifier.width(6.dp))
                Text(tr(s.title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StepNumber(n: Int, done: Boolean, small: Boolean = false, current: Boolean = false) {
    val brand = LocalChatLook.current.brand
    val d = if (small) 20.dp else 24.dp
    Box(
        Modifier.size(d).then(
            when {
                done -> Modifier.background(LocalChatLook.current.success, CircleShape)
                current -> Modifier.background(brand, CircleShape)
                else -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(d * 0.7f))
        else Text("$n", style = MaterialTheme.typography.labelSmall, color = if (current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── „So entsteht dein Video“ ───────────────────────────────────────────────────────────────────────

/** One step of a video: what it is called, what to do, and when it counts as done. */
internal class GuideStep(val key: String, val title: String, val text: String, val optional: Boolean = false, val done: (VideoProject) -> Boolean)

internal val GUIDE_STEPS = listOf(
    GuideStep(
        "cut", trLater("Schneiden"),
        trLater("Abspielkopf an die Stelle ziehen, „Teilen“, das Stück anklicken und Entf drücken. Für ein Stück aus der Mitte: Anfang (I) und Ende (O) markieren, dann „Herausschneiden“."),
    ) { p -> p.clips.size > 1 || p.clips.any { it.inSec > 0.05 } },
    GuideStep("text", trLater("Titel und Untertitel"), trLater("Ein Titel am Anfang, Namen als Bauchbinde, Untertitel aus der Transkription.")) { p ->
        p.overlays.isNotEmpty() || p.subtitles.isNotEmpty()
    },
    GuideStep("sound", trLater("Musik und Ton"), trLater("Musik unterlegen oder Rauschen mindern. Gleichmäßig laut macht der Export von selbst."), optional = true) { p ->
        p.audio.any { it.linkedTo == null && it.path !in p.clips.map { c -> c.path } } || p.sound.denoise != "aus"
    },
    GuideStep("export", trLater("Exportieren"), trLater("Als MP4 für OPAL oder YouTube speichern – im Hintergrund, du kannst weiterarbeiten.")) { p ->
        ExportQueue.entries.value.any { it.project.id == p.id && it.status != ExportQueue.Status.CANCELLED }
    },
)

/**
 * „So entsteht dein Video“ (AP-121) at the top of the properties while nothing is selected: the four steps, the next
 * one open with what to do and buttons that do it; done steps get a tick. ✕ folds the card away (the help menu brings
 * it back). Steps can be skipped; nothing here is needed to work.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuideCard(p: VideoProject, a: VideoActions, onSplit: () -> Unit, onClose: () -> Unit) {
    val exports by ExportQueue.entries.collectAsState()
    var skipped by remember(p.id) { mutableStateOf(setOf<String>()) }
    var opened by remember(p.id) { mutableStateOf<String?>(null) }
    val done = GUIDE_STEPS.associate { s -> s.key to (exports.let { s.done(p) }) }
    val next = GUIDE_STEPS.firstOrNull { done[it.key] != true && it.key !in skipped }?.key
    val open = opened ?: next
    Surface(
        shape = RoundedCornerShape(12.dp), color = LocalChatLook.current.brand.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, LocalChatLook.current.brand.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().testTag("video.guide"),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("So entsteht dein Video"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
                Text(tr("{0} von {1}", done.values.count { it }, GUIDE_STEPS.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                TipIconButton(Icons.Outlined.Close, tr("Hilfe ausblenden – über „?“ kommt sie wieder"), onClose, size = 30.dp)
            }
            GUIDE_STEPS.forEachIndexed { i, s ->
                val isDone = done[s.key] == true
                val isOpen = open == s.key
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = tr(if (isOpen) "Zuklappen" else "Aufklappen")) { opened = if (isOpen) "" else s.key }
                        .semantics { stateDescription = tr(if (isDone) "erledigt" else "offen") }
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepNumber(i + 1, isDone, current = !isDone && s.key == next)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr(s.title),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isDone && !isOpen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.optional) Text(tr("wenn du willst"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                    Spacer(Modifier.weight(0.001f))
                }
                if (isOpen) Column(Modifier.padding(start = 34.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintText(tr(s.text))
                    // Close together: the card is a help beside the work, not a form (no 48 dp touch rows on the PC).
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (s.key) {
                            "cut" -> {
                                GuideButton(Icons.AutoMirrored.Outlined.CallSplit, tr("Teilen"), onSplit)
                                if (a.canCutRange) GuideButton(CutRangeIcon, tr("Herausschneiden"), { a.cutRange() })
                                GuideButton(null, tr("Pausen entfernen …"), { a.pickTranscript = "cut" })
                            }
                            "text" -> {
                                GuideButton(Icons.Outlined.Title, tr("Titel"), { a.addTitle("titel") })
                                GuideButton(null, tr("Name einblenden"), { a.addTitle("bauchbinde") })
                                GuideButton(Icons.Outlined.Subtitles, tr("Untertitel …"), { a.pickTranscript = "subs" })
                            }
                            "sound" -> {
                                GuideButton(Icons.Outlined.MusicNote, tr("Musik …"), { a.addAudio() })
                                if (p.sound.denoise == "aus") GuideButton(null, tr("Rauschen mindern"), { a.h.edit { it.copy(sound = it.sound.copy(denoise = "leicht")) }; Notices.info(tr("Rauschminderung an (leicht).")) })
                            }
                            "export" -> GuideButton(Icons.Outlined.FileDownload, tr("Exportieren …"), { a.export = true }, enabled = p.clips.isNotEmpty())
                        }
                        if (!isDone && s.key != "export") TextButton(onClick = { skipped = skipped + s.key; opened = null }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 32.dp)) {
                            Text(tr("Überspringen"), maxLines = 1, softWrap = false)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideButton(icon: ImageVector?, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) {
        icon?.let { Icon(it, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

// ── Header and toolbar ─────────────────────────────────────────────────────────────────────────────

/**
 * A button of a bar (AP-121): with its word beside the symbol where there is room ([labelled]), otherwise the symbol
 * with the tooltip as before (AP-70). [tip] is the tooltip and what screen readers say.
 */
@Composable
internal fun BarButton(icon: ImageVector, label: String, tip: String, onClick: () -> Unit, labelled: Boolean, enabled: Boolean = true, selected: Boolean? = null) {
    if (!labelled) { TipIconButton(icon, tip, onClick, enabled = enabled, selected = selected, size = 34.dp); return }
    Tip(tip) {
        TextButton(
            onClick = onClick, enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(34.dp).semantics { contentDescription = tip }
                .then(if (selected == true) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

/** „Gespeichert“ in the header (AP-121): every change is stored by itself – beginners look for a save button. */
@Composable
internal fun SavedBadge(saved: Boolean, labelled: Boolean) {
    val text = tr(if (saved) "Gespeichert" else "Speichert …")
    Tip(tr("Jede Änderung wird von selbst gespeichert.")) {
        Row(Modifier.padding(horizontal = 6.dp).semantics(mergeDescendants = true) { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (saved) Icons.Outlined.CloudDone else Icons.Outlined.CloudSync, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (labelled) {
                Spacer(Modifier.width(4.dp))
                Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** The help of the editor (AP-121): the step card, Lora's tour, the keys, Lora. */
@Composable
internal fun HelpButton(guideShown: Boolean, onGuide: () -> Unit, onKeys: () -> Unit, onDismiss: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TipIconButton(Icons.AutoMirrored.Outlined.HelpOutline, tr("Hilfe"), { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false; onDismiss() }) {
            DropdownMenuItem(
                text = { Text(tr("So entsteht dein Video")) }, leadingIcon = { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp).then(if (guideShown) Modifier else Modifier.background(Color.Transparent)), tint = if (guideShown) MaterialTheme.colorScheme.primary else Color.Transparent) },
                onClick = { menu = false; onGuide() },
            )
            DropdownMenuItem(text = { Text(tr("Tour durch den Videoeditor")) }, leadingIcon = { Icon(Icons.Outlined.Map, null) }, onClick = {
                menu = false
                LoraTours.byId("video")?.let { LoraTours.start(it) }
            })
            DropdownMenuItem(text = { Text(tr("Tastenkürzel") + " (?)") }, leadingIcon = { Icon(Icons.Outlined.Keyboard, null) }, onClick = { menu = false; onKeys() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(tr("Lora fragen") + " (Strg+L)") }, leadingIcon = { LoraFaceStill(Modifier.size(20.dp)) }, onClick = { menu = false; LoraHelper.open() })
        }
    }
}

/** A small search field for the media pool (the outlined field took a fifth of its height). */
@Composable
internal fun CompactSearch(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    BasicTextField(
        query, onQuery, singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.typing().semantics { contentDescription = tr("Suchen") },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(34.dp).border(1.dp, outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("Suchen"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

// ── Symbols ────────────────────────────────────────────────────────────────────────────────────────

private fun stroked(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = build)
    }.build()

/** Start of the piece (In, I): a bracket with an arrow into it. */
internal val MarkInIcon: ImageVector by lazy {
    stroked("MarkIn") {
        moveTo(10f, 5f); lineTo(6f, 5f); lineTo(6f, 19f); lineTo(10f, 19f)
        moveTo(10f, 12f); lineTo(19f, 12f)
        moveTo(15f, 8.5f); lineTo(19f, 12f); lineTo(15f, 15.5f)
    }
}

/** End of the piece (Out, O). */
internal val MarkOutIcon: ImageVector by lazy {
    stroked("MarkOut") {
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f)
        moveTo(5f, 12f); lineTo(14f, 12f)
        moveTo(10f, 8.5f); lineTo(14f, 12f); lineTo(10f, 15.5f)
    }
}

/** The marked piece goes: both brackets and a cross between them. */
internal val CutRangeIcon: ImageVector by lazy {
    stroked("CutRange") {
        moveTo(7f, 5f); lineTo(4f, 5f); lineTo(4f, 19f); lineTo(7f, 19f)
        moveTo(17f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(17f, 19f)
        moveTo(9f, 9f); lineTo(15f, 15f)
        moveTo(15f, 9f); lineTo(9f, 15f)
    }
}

// ── Right-click menus of the timeline ──────────────────────────────────────────────────────────────

/**
 * What can be done with the right-clicked thing (AP-121), in plain words: split there, shorten up to there, sound off,
 * to the playhead, properties, delete; on an empty place play from there, a name, a marker or music there.
 */
@Composable
internal fun TimelineMenu(
    h: History, a: VideoActions, s: Sel, t: Double, dismiss: () -> Unit,
    onSplitAt: (Sel, Double) -> Unit, onDelete: () -> Unit, onProps: () -> Unit, onSeek: (Double) -> Unit, onMarker: (Double) -> Unit,
) {
    val p = h.project
    @Composable
    fun item(label: String, icon: ImageVector? = null, enabled: Boolean = true, f: () -> Unit) =
        DropdownMenuItem(text = { Text(label) }, leadingIcon = icon?.let { i -> { Icon(i, null, Modifier.size(20.dp)) } }, enabled = enabled, onClick = { dismiss(); f() })
    @Composable
    fun head(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(260.dp))
    when (s) {
        is Sel.Clip -> {
            val i = p.clips.indexOfFirst { it.id == s.id }
            val c = p.clips.getOrNull(i) ?: return
            head(tr("Clip {0}: {1}", i + 1, c.name) + " · " + clock(t, true))
            item(tr("Hier teilen"), Icons.AutoMirrored.Outlined.CallSplit) { onSplitAt(s, t) }
            item(tr("Anfang bis hier entfernen"), MarkInIcon) { a.trimTo(t, start = true) }
            item(tr("Ende ab hier entfernen"), MarkOutIcon) { a.trimTo(t, start = false) }
            HorizontalDivider()
            item(tr(if (c.muted) "Ton wieder an" else "Ton aus"), if (c.muted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff) {
                h.edit { pp -> pp.copy(clips = pp.clips.map { if (it.id == c.id) it.copy(muted = !c.muted) else it }) }
            }
            if (!c.muted && p.audio.none { it.linkedTo == c.id }) item(tr("Ton auf eine Tonspur legen"), Icons.Outlined.MusicNote) {
                io.github.thepytales.llmhub.video.VideoEdit.detachSound(h.project, c.id)?.let { (q, _) -> h.edit { q } }
            }
            item(tr("Eigenschaften"), Icons.Outlined.Tune) { onProps() }
            HorizontalDivider()
            item(tr("Löschen") + " (Entf)", Icons.Outlined.Delete) { onDelete() }
        }
        is Sel.Layer -> {
            val l = p.layers.firstOrNull { it.id == s.id } ?: return
            head(tr("Bild-in-Bild auf V{0}", l.track) + ": " + l.name)
            item(tr("Hier teilen"), Icons.AutoMirrored.Outlined.CallSplit) { onSplitAt(s, t) }
            item(tr("An den Abspielkopf"), Icons.Outlined.VerticalAlignCenter) { h.edit { pp -> pp.copy(layers = pp.layers.map { if (it.id == l.id) it.copy(startSec = a.pos()) else it }) } }
            item(tr("Eigenschaften"), Icons.Outlined.Tune) { onProps() }
            HorizontalDivider()
            item(tr("Löschen") + " (Entf)", Icons.Outlined.Delete) { onDelete() }
        }
        is Sel.Sound -> {
            val snd = p.audio.firstOrNull { it.id == s.id } ?: return
            head("A${snd.track}: " + snd.name)
            item(tr("Hier teilen"), Icons.AutoMirrored.Outlined.CallSplit) { onSplitAt(s, t) }
            if (snd.linkedTo != null) item(tr("Verknüpfung lösen"), Icons.Outlined.LinkOff) { h.edit { io.github.thepytales.llmhub.video.VideoEdit.unlink(it, snd.id) } }
            item(tr("Eigenschaften"), Icons.Outlined.Tune) { onProps() }
            HorizontalDivider()
            item(tr("Löschen") + " (Entf)", Icons.Outlined.Delete) { onDelete() }
        }
        is Sel.Title -> {
            val o = p.overlays.firstOrNull { it.id == s.id } ?: return
            head(tr(io.github.thepytales.llmhub.video.TextOverlay.label(o.style)) + ": " + o.text)
            item(tr("Text ändern"), Icons.Outlined.Edit) { onProps() }
            item(tr("An den Abspielkopf"), Icons.Outlined.VerticalAlignCenter) { h.edit { pp -> pp.copy(overlays = pp.overlays.map { if (it.id == o.id) it.copy(startSec = a.pos()) else it }) } }
            HorizontalDivider()
            item(tr("Löschen") + " (Entf)", Icons.Outlined.Delete) { onDelete() }
        }
        is Sel.Mark -> {
            val m = p.markers.firstOrNull { it.id == s.id } ?: return
            head(tr("Marker bei {0}", clock(m.sec, true)))
            item(tr("Hinspringen"), Icons.Outlined.PlayArrow) { onSeek(m.sec) }
            item(tr("Notiz ändern"), Icons.Outlined.Edit) { onProps() }
            HorizontalDivider()
            item(tr("Löschen") + " (Entf)", Icons.Outlined.Delete) { onDelete() }
        }
        Sel.None -> {
            head(tr("Bei {0}", clock(t, true)))
            item(tr("Ab hier abspielen"), Icons.Outlined.PlayArrow, enabled = p.clips.isNotEmpty()) { onSeek(t); a.player.toggle(h.project) }
            if (p.at(t) != null && t < p.duration) item(tr("Hier teilen"), Icons.AutoMirrored.Outlined.CallSplit) { onSplitAt(s, t) }
            if (a.canCutRange) item(tr("Markiertes Stück herausschneiden"), CutRangeIcon) { a.cutRange() }
            HorizontalDivider()
            item(tr("Name hier einblenden"), Icons.Outlined.Title) { a.addTitle("bauchbinde", t) }
            item(tr("Marker hier setzen"), Icons.Outlined.BookmarkAdd) { onMarker(t) }
            item(tr("Musik hier einfügen …"), Icons.Outlined.MusicNote) { a.addAudio(t) }
            item(tr("Clip hinzufügen …"), Icons.Outlined.Movie) { a.addClips() }
        }
    }
}
