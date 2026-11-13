package io.github.thepytales.llmhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** One step of a tour: the place that glows ([spot], see data/AppMap) and one line of text. */
data class TourStep(val spot: String?, val text: String)

/**
 * A short tour through a tool (AP-41): 3–6 steps of glowing frame + one line, Weiter/Überspringen. Tours are data,
 * not programmed per screen; their texts go through tr() like everything else (English in en.tsv).
 */
data class LoraTour(val id: String, val route: String, val title: String, val steps: List<TourStep>, val android: Boolean = true)

object LoraTours {
    val ALL: List<LoraTour> = listOf(
        LoraTour(
            "pdf", "pdf", trLater("PDF-Werkzeugkasten"), listOf(
                TourStep("pdf.open", trLater("Hier öffnest du PDFs – mehrere gleichzeitig, jedes in einem eigenen Tab.")),
                TourStep("pdf.modes", trLater("Oben wählst du, was du tun willst: ansehen, Seiten ordnen, markieren, unterschreiben, umwandeln oder die KI fragen.")),
                TourStep("pdf.merge", trLater("Unter „Seiten“ fügst du PDFs zusammen, teilst sie, drehst und löschst Seiten.")),
                TourStep("pdf.save", trLater("Fertig? „Speichern unter“ legt eine Kopie ab – das Original bleibt, bis du es überschreibst.")),
                TourStep(null, trLater("Oder sag mir einfach, was ich tun soll – zum Beispiel „Füge die beiden PDFs zusammen“ oder „Schwärze alle Namen“.")),
            ),
        ),
        LoraTour(
            "files", "files", trLater("Dateien"), listOf(
                TourStep("files.places", trLater("Links liegen deine Orte: Lora-Ordner, Hochgeladen, Erstellt, Markiert und der Papierkorb.")),
                TourStep("files.search", trLater("Die Suche findet Dateien nach Namen, Schlagwörtern und Inhalt.")),
                TourStep("files.new", trLater("Mit dem Plus legst du Ordner, Dokumente, Tabellen und Präsentationen an oder lädst Dateien hoch.")),
                TourStep("files.sort", trLater("Hier sortierst du nach Name, Datum oder Größe.")),
                TourStep(null, trLater("Ich räume auch für dich auf: „Ordne Hochgeladen nach Semester“ oder „Finde Duplikate“ – immer mit Vorschau und Rückgängig.")),
            ),
        ),
        LoraTour(
            "templates", "templates", trLater("Erstellen"), listOf(
                TourStep("create.lora", trLater("Sag mir oben, was entstehen soll – ich suche die passende Vorlage oder lege los.")),
                TourStep("create.templates", trLater("Darunter stehen die TU Vorlagen: Briefe, Protokolle, Bescheinigungen im Corporate Design.")),
                TourStep("create.new", trLater("Eigene Vorlagen legst du hier an und teilst sie mit Kolleg:innen.")),
            ),
        ),
        LoraTour(
            "scan", "scan", trLater("Scannen"), listOf(
                TourStep("scan.capture", trLater("Fotografiere die Seite von oben – oder lies sie am PC mit dem Scanner ein. Ich finde die Kanten des Papiers.")),
                TourStep(null, trLater("Passen die Ecken nicht, legst du sie unter „Ecken anpassen“ mit dem Finger oder der Maus auf die Ecken des Papiers.")),
                TourStep(null, trLater("Farbe, Graustufen oder Schwarz-Weiß: Schatten verschwinden, das Papier wird weiß, die Schrift klar.")),
                TourStep("scan.save", trLater("„Als PDF speichern“ legt alle Seiten mit Texterkennung in Dateien › Scans ab – durchsuchbar und per Sync auch auf den anderen Geräten.")),
            ),
        ),
        // The editor opens with the latest project for its steps (AP-70); the card stays beside each place.
        LoraTour(
            "video", "video", trLater("Videoeditor"), android = false, steps = listOf(
                TourStep("video.new", trLater("Ein neues Projekt beginnt mit „Video schneiden“: Videos auswählen oder einfach hierher ziehen.")),
                TourStep("video.guide", trLater("Rechts zeigt dir „So entsteht dein Video“ den nächsten Schritt – mit Knöpfen, die ihn gleich ausführen.")),
                TourStep("video.cut", trLater("Zum Schneiden: „Teilen“ am Abspielkopf – oder Anfang und Ende eines Stücks markieren und „Herausschneiden“. Ein Rechtsklick auf einen Clip zeigt, was noch geht.")),
                TourStep("video.tools", trLater("Über der Zeitleiste liegen die Werkzeuge: Auswahl (A), Klinge (B), Trimmen (T) und der Magnet – die Taste steht im Tooltip.")),
                TourStep("video.titles", trLater("Daneben setzt du Titel und Bauchbinden im TUD-Design, Untertitel aus der Transkription und den Rohschnitt.")),
                TourStep("video.layout", trLater("Die Anordnung passt du an: Ränder ziehen, Medienpool und Eigenschaften einklappen – hier setzt du sie zurück.")),
                TourStep("video.export", trLater("Zum Schluss exportierst du als MP4 für OPAL oder YouTube.")),
                TourStep(null, trLater("Du kannst mir auch sagen: „Mach Untertitel und exportiere für OPAL“.")),
            ),
        ),
    )

    /** The tour running now and its step. */
    val active = MutableStateFlow<Pair<LoraTour, Int>?>(null)

    /** The tour offered on the first visit of its tool. */
    val offer = MutableStateFlow<LoraTour?>(null)

    fun available(android: Boolean) = ALL.filter { !android || it.android }

    fun byId(id: String?): LoraTour? = ALL.firstOrNull { it.id == id }

    fun forRoute(route: String): LoraTour? = ALL.firstOrNull { route.substringBefore('?') == it.route }

    /** „Zeig mir, wie das geht“ / Strg+K: the tour of the open tool or the one whose name fits [text]. */
    fun find(text: String, route: String): LoraTour? {
        val n = io.github.thepytales.llmhub.data.AppMap.norm(text)
        return ALL.firstOrNull { t -> n.contains(io.github.thepytales.llmhub.data.AppMap.norm(t.id)) || n.contains(io.github.thepytales.llmhub.data.AppMap.norm(tr(t.title))) || n.contains(io.github.thepytales.llmhub.data.AppMap.norm(t.title)) }
            ?: forRoute(route)
    }

    private fun show(step: TourStep?) {
        Spotlight.hold = step?.spot != null
        Spotlight.show(step?.spot)
    }

    fun start(tour: LoraTour, go: ((String) -> Unit)? = null) {
        offer.value = null
        if (go != null && LoraHelper.route.value.substringBefore('?') != tour.route) go(tour.route)
        active.value = tour to 0
        show(tour.steps.firstOrNull())
    }

    fun next() {
        val (tour, i) = active.value ?: return
        if (i + 1 >= tour.steps.size) stop() else {
            active.value = tour to i + 1
            show(tour.steps[i + 1])
        }
    }

    fun back() {
        val (tour, i) = active.value ?: return
        if (i > 0) {
            active.value = tour to i - 1
            show(tour.steps[i - 1])
        }
    }

    fun stop() {
        active.value = null
        Spotlight.hold = false
        Spotlight.show(null)
    }

    fun seen(c: AppContainer): Set<String> = c.settings.current.loraToursSeen.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun markSeen(c: AppContainer, id: String) {
        if (id in seen(c)) return
        c.settings.update { it.copy(loraToursSeen = (seen(it.loraToursSeen) + id).joinToString("\n")) }
    }

    private fun seen(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** On the first visit of a tool with a tour: offer it (once, when tours are on and Lora may appear). */
    fun onRoute(c: AppContainer, route: String, android: Boolean) {
        val s = c.settings.current
        if (!s.loraTours || !LoraHelper.available(s) || active.value != null) return
        val tour = forRoute(route)?.takeIf { !android || it.android } ?: run { offer.value = null; return }
        if (tour.id in seen(c)) return
        offer.value = tour
    }
}

/**
 * Where the card of a tour step goes (AP-70): beside its place, so it never covers it – above or below, whichever side
 * has more room, then right or left; [target] and the result in the coordinates of [area]. Without a place, or with
 * no room around it, at the bottom in the middle ([bottom] above the edge), or at the top when that would cover it.
 */
fun tourCardPosition(target: Rect?, card: IntSize, area: IntSize, margin: Float, gap: Float, bottom: Float): IntOffset {
    val w = card.width.toFloat()
    val h = card.height.toFloat()
    fun x(cx: Float) = (cx - w / 2).coerceIn(margin, maxOf(margin, area.width - w - margin))
    fun y(cy: Float) = (cy - h / 2).coerceIn(margin, maxOf(margin, area.height - h - margin))
    val home = IntOffset(x(area.width / 2f).toInt(), (area.height - h - bottom).coerceAtLeast(margin).toInt())
    if (target == null || target.isEmpty) return home
    val below = target.bottom + gap
    val above = target.top - gap - h
    val roomBelow = area.height - margin - below - h
    val roomAbove = above - margin
    val vertical = listOf(roomBelow to below, roomAbove to above).sortedByDescending { it.first }
    for ((room, top) in vertical) if (room >= 0f) return IntOffset(x(target.center.x).toInt(), top.toInt())
    val right = target.right + gap
    val left = target.left - gap - w
    val roomRight = area.width - margin - right - w
    val roomLeft = left - margin
    val sides = listOf(roomRight to right, roomLeft to left).sortedByDescending { it.first }
    for ((room, start) in sides) if (room >= 0f) return IntOffset(start.toInt(), y(target.center.y).toInt())
    val homeRect = Rect(home.x.toFloat(), home.y.toFloat(), home.x + w, home.y + h)
    return if (homeRect.overlaps(target)) IntOffset(home.x, margin.toInt()) else home
}

/**
 * The tour layer over the app (AP-41): the offer on the first visit of a tool and the card of the running tour
 * (step x of y, text, Zurück · Überspringen · Weiter). Lora flies to each place only when movements are allowed. The
 * card of a step sits beside its place, never over it (AP-70: in the video editor it covered the clips).
 */
@Composable
fun BoxScope.LoraTourLayer(c: AppContainer) {
    val route by LoraHelper.route.collectAsStateWithLifecycle()
    val offer by LoraTours.offer.collectAsStateWithLifecycle()
    val active by LoraTours.active.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        // Give the screen a moment to appear; leaving the tool ends its tour.
        delay(900)
        LoraTours.active.value?.let { (t, _) -> if (route.substringBefore('?') != t.route) LoraTours.stop() }
        LoraTours.onRoute(c, route, IS_ANDROID)
    }
    LaunchedEffect(active) {
        val (t, i) = active ?: return@LaunchedEffect
        val spot = t.steps.getOrNull(i)?.spot
        if (spot != null && !LoraMotion.calm) LoraFlight.start(LoraFlight.Landing.PLACE, spot)
    }
    val visible = offer != null || active != null
    // Where the step's place is (Spot keeps it in root coordinates), read again while the step shows: panels move.
    val spot = active?.let { (t, i) -> t.steps.getOrNull(i)?.spot }
    var target by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(spot) {
        target = null
        if (spot == null) return@LaunchedEffect
        while (true) {
            target = Spotlight.bounds[spot]
            delay(250)
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Layout(
        content = {
            AnimatedVisibility(
                visible,
                enter = slideInVertically(tween(260)) { it / 4 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(160)),
            ) {
                TourCard(c, offer, active)
            }
        },
        modifier = Modifier.matchParentSize().navigationBarsPadding().onGloballyPositioned { origin = it.positionInRoot() },
    ) { measurables, constraints ->
        val margin = with(density) { 16.dp.toPx() }
        val maxCard = minOf(with(density) { 520.dp.toPx() }, constraints.maxWidth - 2 * margin).toInt().coerceAtLeast(0)
        val area = IntSize(constraints.maxWidth, constraints.maxHeight)
        // Nothing to show: AnimatedVisibility leaves no child.
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = maxCard, maxHeight = constraints.maxHeight))
            ?: return@Layout layout(area.width, area.height) {}
        val at = tourCardPosition(target?.translate(-origin), IntSize(placeable.width, placeable.height), area, margin, with(density) { 12.dp.toPx() }, with(density) { 24.dp.toPx() })
        layout(area.width, area.height) { placeable.place(at) }
    }
}

/** The card itself: the offer, or step x of y with Zurück · Überspringen · Weiter. */
@Composable
private fun TourCard(c: AppContainer, offer: LoraTour?, active: Pair<LoraTour, Int>?) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().testTag("lora.tour.card"),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                LoraFaceStill(Modifier.size(36.dp), action = LoraAction.POINT)
                Spacer(Modifier.width(12.dp))
                val a = active
                val o = offer
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a != null) {
                        val (tour, i) = a
                        Text(tr("Tour: {0} · {1} von {2}", tr(tour.title), i + 1, tour.steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(tr(tour.steps[i].text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (i > 0) TextButton(onClick = LoraTours::back) { Text(tr("Zurück")) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { LoraTours.markSeen(c, tour.id); LoraTours.stop() }) { Text(tr("Überspringen")) }
                            FilledTonalButton(onClick = { if (i + 1 >= tour.steps.size) LoraTours.markSeen(c, tour.id); LoraTours.next() }) {
                                Text(tr(if (i + 1 >= tour.steps.size) "Fertig" else "Weiter"))
                            }
                        }
                    } else if (o != null) {
                        Text(tr("Soll ich dir „{0}“ kurz zeigen?", tr(o.title)), style = MaterialTheme.typography.bodyMedium)
                        HintText(tr("{0} Schritte, jederzeit wieder über Strg+K oder „Zeig mir, wie das geht“.", o.steps.size))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { LoraTours.start(o) }) { Text(tr("Tour starten")) }
                            TextButton(onClick = { LoraTours.markSeen(c, o.id); LoraTours.offer.value = null }) { Text(tr("Nein, danke")) }
                        }
                    }
                }
            }
        }
}
