package io.github.thepytales.llmhub.ui

import androidx.compose.material3.Slider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer

/**
 * Einstellungen › Darstellung: the looks as small previews (the businesslike ones first, the imitations of other chat
 * apps under "Weitere Stile"), then accent, light/dark and text size. Shared by Android and Windows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettings(c: AppContainer) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val style = enumOr(s.appStyle, AppStyle.ADEU)
    val accent = enumOr(s.accent, Accent.AUTO)
    val mode = enumOr(s.themeMode, ThemeMode.SYSTEM)
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var more by rememberSaveable { mutableStateOf(!style.recommended) }
    fun pick(st: AppStyle) = c.settings.update { it.copy(appStyle = st.name) }

    GroupLabel(tr("Sprache der Oberfläche"))
    Spot("appearance.language") { Segmented(I18n.LANGUAGES, s.uiLanguage, { l -> c.settings.update { it.copy(uiLanguage = l) } }) }
    HintText(if (s.uiLanguage == "en") tr("Menus, settings and the start page are in English so far; answers follow this language.") else tr("Englisch übersetzt bisher Menüs, Einstellungen und Startseite; Antworten folgen der gewählten Sprache."))

    GroupLabel(tr("Stil"))
    Spot("appearance.style") { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppStyle.entries.filter { it.recommended }.forEach { st -> StylePreview(st, dark, st == style) { pick(st) } }
    } }
    HintText(tr(style.description))
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { more = !more }.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Weitere Stile (wie andere Chat-Apps)"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (more) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (more) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppStyle.entries.filter { !it.recommended }.forEach { st -> StylePreview(st, dark, st == style) { pick(st) } }
        }
    }

    GroupLabel(tr("Helligkeit"))
    Spot("appearance.theme") { Segmented(ThemeMode.entries.map { it to tr(it.label) }, mode, { m -> c.settings.update { it.copy(themeMode = m.name) } }) }
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TitleBlock(tr("Reines Schwarz (AMOLED)"), tr("Schwarzer Hintergrund im dunklen Modus – spart Akku auf OLED-Displays."), Modifier.weight(1f))
            Switch(checked = s.amoled, onCheckedChange = { v -> c.settings.update { it.copy(amoled = v) } })
        }
    }

    Spot("appearance.startAnimation") { AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TitleBlock(tr("Startanimation"), tr("Kurze Animation des Logos beim Öffnen der App (etwa eine Sekunde)."), Modifier.weight(1f))
            Switch(checked = s.startAnimation, onCheckedChange = { v -> c.settings.update { it.copy(startAnimation = v) } })
        }
    } }

    GroupLabel(tr("Farbton"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Accent.entries.forEach { a -> AccentSwatch(a, a == accent) { c.settings.update { it.copy(accent = a.name) } } }
    }
    HintText(
        if (style == AppStyle.STANDARD) tr("„Automatisch“: {0}", tr(MATERIAL_YOU_DESCRIPTION))
        else tr("„Automatisch“ verwendet die Farben des gewählten Stils (empfohlen)."),
    )

    GroupLabel(tr("Schriftgröße"))
    Spot("appearance.textsize") { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.9f to tr("Klein"), 1f to tr("Normal"), 1.15f to tr("Groß"), 1.3f to tr("Sehr groß")).forEach { (scale, label) ->
            FilterChip(selected = s.textScale == scale, onClick = { c.settings.update { it.copy(textScale = scale) } }, label = { Text(label) })
        }
    } }

    if (DESKTOP) {
        GroupLabel(tr("Zoom"))
        Spot("appearance.zoom") { Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = s.uiZoom, onValueChange = { v -> c.settings.update { it.copy(uiZoom = Math.round(v * 10) / 10f) } },
                valueRange = 0.8f..2f, steps = 11, modifier = Modifier.weight(1f),
            )
            Text("${Math.round(s.uiZoom * 100)} %", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp).widthIn(min = 48.dp))
        } }
        HintText(tr("Vergrößert die ganze Oberfläche. Auch mit Strg++ und Strg+−, Strg+0 setzt zurück, oder Strg+Mausrad."))
    }
}

/** Miniature of a look: background, a card, a question bubble and the main button, in the look's own colors. */
@Composable
private fun StylePreview(style: AppStyle, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val (scheme, look) = stylePreview(style, dark)
    val frame = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(frame, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxWidth().background(scheme.background).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Card with two text lines
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(look.cardShape)
                    .background(look.card)
                    .then(look.cardBorder?.let { Modifier.border(it, look.cardShape) } ?: Modifier)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurface))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurfaceVariant.copy(alpha = 0.5f)))
            }
            // Question bubble on the right
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.widthIn(min = 60.dp).width(70.dp).height(16.dp).clip(look.userShape).background(look.userBubble))
            }
            // Main button
            Box(Modifier.width(64.dp).height(18.dp).clip(RoundedCornerShape(50)).background(look.action))
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(style.label), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            if (selected) Icon(Icons.Outlined.Check, contentDescription = tr("Ausgewählt"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val fill = accent.color?.let { Brush.linearGradient(listOf(it, it)) }
        ?: Brush.sweepGradient(listOf(Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFFDB2777), Color(0xFF2563EB)))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(fill)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        ) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(tr(accent.label), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.size(0.dp))
    }
}
