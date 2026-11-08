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
        LoraTour(
            "video", "video", trLater("Videoeditor"), android = false, steps = listOf(
                TourStep("video.new", trLater("Ein neues Projekt beginnt mit einer Aufnahme oder einem Screencast.")),
                TourStep("video.titles", trLater("Titel und Bauchbinden im TUD-Design setzt du hier.")),
                TourStep("video.subtitles", trLater("Untertitel kommen aus der Transkription oder einer SRT-Datei.")),
                TourStep("video.roughcut", trLater("Der Rohschnitt schneidet Pausen und Füllwörter heraus – oder ich kürze mit dir.")),
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
 * The tour layer over the app (AP-41): the offer on the first visit of a tool and the card of the running tour
 * (step x of y, text, Zurück · Überspringen · Weiter). Lora flies to each place only when movements are allowed.
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
    AnimatedVisibility(
        visible,
        enter = slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
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
}
