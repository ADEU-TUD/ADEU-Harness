package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Area
import io.github.thepytales.llmhub.data.Place
import io.github.thepytales.llmhub.data.Places

/** The icon of an area in the table. */
internal fun Area.icon(): ImageVector = when (this) {
    Area.LORA -> Icons.Outlined.AutoAwesome
    Area.CHAT -> Icons.Outlined.ChatBubbleOutline
    Area.MAIL -> Icons.Outlined.Mail
    Area.DOCS -> Icons.Outlined.Description
    Area.TRANSCRIPTION -> Icons.Outlined.GraphicEq
    Area.DICTATION -> Icons.Outlined.Mic
    Area.SEARCH -> Icons.Outlined.Search
    Area.SPEECH -> Icons.AutoMirrored.Outlined.VolumeUp
    Area.VISION -> Icons.Outlined.Image
    Area.NAMES -> Icons.Outlined.Shield
    Area.COMMANDS -> Icons.Outlined.Bolt
    Area.IMAGES -> Icons.Outlined.Palette
    Area.WEB -> Icons.Outlined.Language
}

/**
 * Everything [Places.status] reads, as one value: the page recomposes when a download ends, the hardware is known or a
 * setting changes.
 */
@Composable
internal fun placesTick(c: AppContainer): Any {
    val s by c.settings.settings.collectAsState()
    val local by c.localLlm.state.collectAsState()
    val hw by c.localLlm.hardware.collectAsState()
    val speech by c.transcription.state.collectAsState()
    val ts by c.transcription.settings.collectAsState()
    val emb by c.localEmbedding.state.collectAsState()
    val voice by c.localVoice.state.collectAsState()
    val pii by c.piiModel.state.collectAsState()
    val cmd by c.commandModel.state.collectAsState()
    val bench by io.github.thepytales.llmhub.local.DeviceBench.stored(c).collectAsState()
    return listOf(s, local.installed, local.vision, hw, speech.models.map { it.installed }, ts.mode, emb.installed, voice.installed, pii.installed, cmd.installed, bench?.at)
}

/** The colour of a label. */
internal fun Places.Kind.tone(): PillTone = when (this) {
    Places.Kind.READY -> PillTone.SUCCESS
    Places.Kind.SETUP -> PillTone.ACCENT
    Places.Kind.NOT_HERE -> PillTone.WARNING
    Places.Kind.INTERNET, Places.Kind.API_ONLY, Places.Kind.API -> PillTone.NEUTRAL
}

/**
 * Kennzeichnung je Funktion (AP-112): „läuft auf diesem Gerät“, „braucht Internet“, „nur über API“ (with a lock) or
 * „nicht auf diesem Gerät“ – a calm label with one line why, for the screens of the functions. „Nur über API“ is shown,
 * never hidden.
 */
@Composable
fun PlaceNote(c: AppContainer, area: Area, modifier: Modifier = Modifier, onSetup: (() -> Unit)? = null) {
    placesTick(c)
    val st = Places.status(c, area)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st.kind == Places.Kind.API_ONLY) Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        StatusPill(tr(st.kind.label), st.kind.tone())
        Text(st.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
        if (onSetup != null && st.step != null) TextButton(onClick = onSetup) { Text(tr("Einrichten")) }
    }
}

/** The quick choice that is shown as selected; null for an own mix. */
private fun Places.Profile?.label(): String = this?.label ?: trLater("Eigene Auswahl")

/**
 * Einstellungen › Wo Lora arbeitet (AP-112): the quick choice „Alles lokal · Gemischt (empfohlen) · Alles über API“, the
 * setup step by step, and one row per area with Lokal / Gemischt / API (what is not possible is greyed out with its
 * reason) and what that means on this device.
 */
@Composable
fun PlacesSettings(c: AppContainer, onNavigate: (String) -> Unit) {
    placesTick(c)
    val s = c.settings.current
    val profile = Places.profile(c)
    Section(tr("Wo Lora arbeitet")) {
        Spot("places.profile") {
            Segmented(
                Places.Profile.entries.map { it as Places.Profile? to tr(it.label) },
                profile,
                enabled = { it == Places.Profile.ALL_LOCAL || s.hasAnyApiKey },
                onSelect = { p ->
                    if (p != null) {
                        Places.apply(c, p)
                        if (p == Places.Profile.ALL_LOCAL && Area.entries.any { Places.status(c, it).kind == Places.Kind.SETUP }) onNavigate("local-setup")
                    }
                },
            )
        }
        HintText(
            when (profile) {
                Places.Profile.ALL_LOCAL -> tr("Alles auf diesem Gerät: Lora, Chats, Postfach, Suche, Sprache und Bilder verstehen laufen hier. Internet nur für Web, Literatur, Nextcloud und Updates; Bilder erzeugen geht nur über API. Was doch hinausgeht, wird vorher pseudonymisiert.")
                Places.Profile.MIXED -> tr("Befehle, Namensschutz und kurze Aufgaben auf diesem Gerät, alles Anspruchsvolle über den Uni-Server bzw. den gewählten Anbieter. So arbeitet Lora bisher.")
                Places.Profile.ALL_API -> tr("Sprachmodelle, Spracherkennung, Suche und Stimme über den Server – am genauesten, braucht immer Internet. Postfach, Befehle und Namensschutz bleiben auf diesem Gerät.")
                null -> tr("Eigene Auswahl – je Bereich unten eingestellt.")
            },
        )
        if (!s.hasAnyApiKey) SettingsHint(tr("Kein API-Schlüssel hinterlegt: Alles, was eine Wahl hat, läuft auf diesem Gerät. Einen Schlüssel trägst du unter Einstellungen › API-Zugang ein."))
        SetupCard(c, onNavigate)
    }
    Section(tr("Je Bereich")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 620.dp
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Area.entries.forEachIndexed { i, area ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    AreaRow(c, area, wide, onNavigate)
                }
            }
        }
        HintText(tr("Ausgegraut heißt: hier nicht möglich – der Grund steht darunter. Immer gilt: Ins Postfach schaut nur ein Modell auf diesem Gerät, Projekte „Nur auf diesem Gerät“ und der Tresor behalten ihre Regeln."))
    }
}

/** The card „Einrichtung Schritt für Schritt“: what is ready, and where it continues. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SetupCard(c: AppContainer, onNavigate: (String) -> Unit) {
    val s = c.settings.current
    val (ready, all) = Places.localCount(c)
    val missing = Area.entries.count { Places.status(c, it).kind == Places.Kind.SETUP }
    androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.widthIn(max = 560.dp)) {
            Text(tr("Alles lokal einrichten – Schritt für Schritt"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("{0} von {1} Bereichen laufen schon auf diesem Gerät.", ready, all) +
                    if (missing > 0) " " + tr("Für {0} fehlt noch ein Modell.", missing) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val step = s.localSetupStep
        if (step in 1..Places.SetupStep.entries.size) {
            Button(onClick = { onNavigate("local-setup") }) { Text(tr("Fortsetzen (Schritt {0} von {1})", step, Places.SetupStep.entries.size)) }
        } else {
            OutlinedButton(onClick = { onNavigate("local-setup") }) { Text(tr("Einrichtung starten")) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AreaRow(c: AppContainer, area: Area, wide: Boolean, onNavigate: (String) -> Unit) {
    // What applies is shown: without a key that is „Lokal“, and Gemischt/API are greyed out.
    val chosen = Places.effective(c, area)
    val keyed = Places.hasKeyFor(c.settings.current, area)
    val st = Places.status(c, area)
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(area.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(tr(area.title), style = MaterialTheme.typography.bodyLarge)
                Text(tr(area.what), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val choice: @Composable (Modifier) -> Unit = { m ->
        if (area == Area.WEB) {
            Text(tr("keine Wahl"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = m.padding(top = 10.dp))
        } else {
            Segmented(
                Place.entries.map { it to tr(it.label) },
                chosen ?: Place.LOCAL,
                { p -> Places.set(c, area, p) },
                modifier = m,
                enabled = { it in area.places && (it == Place.LOCAL || keyed) },
            )
        }
    }
    // Why the greyed places are not possible: one line (the same reason only once).
    val why = (area.reasons.filterKeys { it !in area.places }.values.map { tr(it) } +
        listOfNotNull(tr("Gemischt und API brauchen einen API-Schlüssel.").takeIf { !keyed && area.places.size > 1 })).distinct().joinToString(" ")
    val status: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            androidx.compose.foundation.layout.FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (st.kind == Places.Kind.API_ONLY) Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                StatusPill(tr(st.kind.label), st.kind.tone())
                if (st.step != null) TextButton(onClick = {
                    c.settings.update { it.copy(localSetupStep = st.step.ordinal + 1) }
                    onNavigate("local-setup")
                }) { Text(tr("Laden")) }
            }
            Text(st.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (why.isNotBlank() && area != Area.WEB) Text(why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
    Spot("places.${area.name.lowercase()}") {
        if (wide) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.25f)) { title() }
                choice(Modifier.width(280.dp))
                Column(Modifier.weight(1.6f)) { status() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                title()
                choice(Modifier.fillMaxWidth())
                status()
            }
        }
    }
}

/** One line for the settings overview: the quick choice and how many areas run here. */
@Composable
internal fun placesSummary(c: AppContainer): String {
    placesTick(c)
    val (ready, all) = Places.localCount(c)
    return tr("{0} · {1} von {2} Bereichen auf diesem Gerät", tr(Places.profile(c).label()), ready, all)
}

/** How long „Später“ in [UniFallbackDialog] holds. */
private const val FALLBACK_LATER_MS = 3 * 24 * 3_600_000L

/**
 * Rückfall, wenn der Uni-Server wegfällt (AP-112): once access is gone for good ([io.github.thepytales.llmhub.data.UniWatch]),
 * Lora offers to work on this device or to enter the key of another provider – the chats, files and settings stay;
 * old chats continue with the new model.
 */
@Composable
fun UniFallbackDialog(c: AppContainer, go: (String) -> Unit) {
    val s by c.settings.settings.collectAsState()
    // The watch is a file, not a flow: look again every minute.
    val gone by androidx.compose.runtime.produceState(c.uniWatch.gone()) {
        while (true) { kotlinx.coroutines.delay(60_000); value = c.uniWatch.gone() }
    }
    val now = System.currentTimeMillis()
    if (!gone || !s.hasApiKey || s.uniFallbackAsked < 0 || now - s.uniFallbackAsked < FALLBACK_LATER_MS || Places.allLocal(c)) return
    val since = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(c.uniWatch.since))
    fun answer(at: Long) = c.settings.update { it.copy(uniFallbackAsked = at) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { answer(now) },
        icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
        title = { Text(tr("Der Uni-Server antwortet nicht mehr")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (c.uniWatch.state.refused) tr("Seit {0} nimmt der Uni-Server den API-Schlüssel nicht mehr an. Vielleicht ist er abgelaufen oder das Pilotprojekt beendet.", since)
                    else tr("Seit {0} erreicht Lora den Uni-Server nicht, obwohl das Internet geht. Vielleicht ist das Pilotprojekt beendet.", since),
                )
                Text(tr("Wie soll es weitergehen? Deine Chats, Dateien und Einstellungen bleiben in jedem Fall erhalten; bisherige Chats laufen mit dem neuen Modell weiter."), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                answer(now)
                Places.apply(c, Places.Profile.ALL_LOCAL)
                go(if (Area.entries.any { Places.status(c, it).kind == Places.Kind.SETUP }) "local-setup" else "settings/places")
            }) { Text(tr("Auf diesem Gerät weiterarbeiten")) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { answer(now); go("settings/api") }) { Text(tr("Anderen Schlüssel eintragen")) }
                TextButton(onClick = { answer(-1) }) { Text(tr("Nicht mehr fragen")) }
                TextButton(onClick = { answer(now) }) { Text(tr("Später")) }
            }
        },
    )
}
