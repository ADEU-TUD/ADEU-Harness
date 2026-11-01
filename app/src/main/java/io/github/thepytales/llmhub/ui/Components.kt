package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelFilter
import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.ModelSort
import io.github.thepytales.llmhub.data.ModelTier
import io.github.thepytales.llmhub.data.pickerModels
import io.github.thepytales.llmhub.data.ModelType
import java.util.Locale

fun formatTokens(t: Int): String = when {
    t >= 1_048_576 -> if (t % 1_048_576 == 0) "${t / 1_048_576}M" else String.format(Locale.US, "%.1fM", t / 1_048_576.0)
    t >= 1024 -> "${t / 1024}k"
    else -> t.toString()
}

@Composable
private fun Chip(text: String, icon: ImageVector? = null, on: Boolean = true) {
    val bg = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilityChips(model: ModelInfo, modifier: Modifier = Modifier) {
    val info = ModelCatalog.effective(model)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        info.contextTokens?.let { Chip(tr("{0} Kontext", formatTokens(it))) }
        if (info.thinking == true) Chip(tr("Denken"), Icons.Outlined.Psychology)
        if (info.vision == true) Chip(tr("Bilder"), Icons.Outlined.Visibility)
        if (info.tools == true) Chip(tr("Werkzeuge"), Icons.Outlined.Build)
        if (info.thinking == false) Chip(tr("kein Denken"), on = false)
        if (info.vision == false) Chip(tr("keine Bilder"), on = false)
        if (info.isAlias) Chip(tr("Alias"), on = false)
    }
}

@Composable
fun RatingRow(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp))
        Text(
            "●".repeat(value) + "○".repeat(5 - value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Short list of what a model can do, for people who don't know the technical terms. */
fun capabilityLine(m: ModelInfo): String {
    val e = ModelCatalog.effective(m)
    return listOfNotNull(
        if (e.vision == true) tr("versteht Bilder") else if (e.vision == false) tr("keine Bilder") else null,
        if (e.tools == true) tr("Websuche & Werkzeuge") else null,
        e.contextTokens?.let { tr("{0} Kontext", formatTokens(it)) },
    ).joinToString(" · ")
}

/** Rounded corners for rows that are stacked into one card with small gaps (like the Claude app). */
internal fun stackedShape(index: Int, count: Int): RoundedCornerShape {
    val big = 20.dp
    val small = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRows(
    models: List<ModelInfo>,
    selected: String,
    defaultModel: String?,
    showCapabilities: Boolean,
    onPick: (String) -> Unit,
) {
    val look = LocalChatLook.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        models.forEachIndexed { i, m ->
            Surface(
                shape = stackedShape(i, models.size),
                color = look.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(stackedShape(i, models.size))
                    .clickable { onPick(m.id) },
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            Text(m.displayName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                            if (m.id == defaultModel) Badge(tr("Standard"), Modifier.align(Alignment.CenterVertically))
                            m.badge?.let { Badge(it, Modifier.align(Alignment.CenterVertically)) }
                        }
                        Text(
                            m.useCase.ifBlank { m.summary },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (showCapabilities) {
                            val caps = capabilityLine(m)
                            if (caps.isNotEmpty()) {
                                Text(caps, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (m.id == selected) {
                        Icon(
                            Icons.Outlined.Check, contentDescription = tr("Ausgewählt"),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Model picker like the Claude app: the best models with what they are good for at the top, the rest
 * under „Weitere Modelle“. Outdated models and aliases are not offered (see [ModelTier]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelInfo>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    defaultModel: String? = null,
    title: String = "Modellauswahl",
    note: String? = null,
    /** Offers „Automatisch“ on top; true while it is chosen. Null: not offered (comparison, tasks …). */
    auto: Boolean? = null,
    onAuto: () -> Unit = {},
) {
    val look = LocalChatLook.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offered = remember(models) { pickerModels(models) }
    val featured = offered.filter { it.tier == ModelTier.FEATURED }
    val more = offered.filter { it.tier != ModelTier.FEATURED }
    var showMore by remember { mutableStateOf(featured.isEmpty()) }
    fun pick(id: String) {
        onSelect(id)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = look.sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                IconButton(
                    onClick = { if (showMore && featured.isNotEmpty()) showMore = false else onDismiss() },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    if (showMore && featured.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
                    else Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"))
                }
                Text(
                    if (showMore && featured.isNotEmpty()) tr("Weitere Modelle") else title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (!showMore) {
                if (auto != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = look.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onAuto(); onDismiss() },
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(tr("Automatisch"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                                Text(
                                    tr("Die App schlägt je Anfrage das passende Modell vor und fragt vorher nach."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (auto) Icon(Icons.Outlined.Check, contentDescription = tr("Ausgewählt"), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                ModelRows(featured, if (auto == true) "" else selected, defaultModel, showCapabilities = false, onPick = ::pick)
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
                if (more.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = look.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (note == null) 16.dp else 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showMore = true },
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Weitere Modelle"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), modifier = Modifier.weight(1f))
                            more.firstOrNull { it.id == selected }?.let {
                                Text(
                                    it.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 150.dp),
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    tr("Modelle für besondere Zwecke. Für den Alltag reichen die Modelle auf der ersten Seite."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
                ModelRows(more, selected, defaultModel, showCapabilities = true, onPick = ::pick)
            }
        }
    }
}

/** Simple dropdown field for choosing one of [options]. */
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay to catch taps on the read-only field.
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelChip(label: String, value: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = value > 0,
            onClick = { open = true },
            label = { Text(if (value > 0) "$label ≥ $value" else label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(tr("Alle")) }, onClick = { onSelect(0); open = false })
            (3..5).forEach { v ->
                DropdownMenuItem(
                    text = { Text(tr("mind. ") + "●".repeat(v) + "○".repeat(5 - v)) },
                    onClick = { onSelect(v); open = false },
                )
            }
        }
    }
}

/**
 * Search field and filter chips for model lists. [types] adds a row to filter by model type;
 * without it the type stays fixed (e.g. chat models in the picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelFilterBar(
    filter: ModelFilter,
    onChange: (ModelFilter) -> Unit,
    modifier: Modifier = Modifier,
    types: List<ModelType>? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onChange(filter.copy(query = it)) },
            placeholder = { Text(tr("Modelle suchen (Name, Stärke, Anbieter …)")) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onChange(filter.copy(query = "")) }) {
                        Icon(Icons.Outlined.Close, contentDescription = tr("Suche leeren"))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        )
        if (types != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter.type == null, onClick = { onChange(filter.copy(type = null)) }, label = { Text(tr("Alle")) })
                types.forEach { t ->
                    FilterChip(
                        selected = filter.type == t,
                        onClick = { onChange(filter.copy(type = if (filter.type == t) null else t)) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = filter.thinking,
                onClick = { onChange(filter.copy(thinking = !filter.thinking)) },
                label = { Text(tr("Denken")) },
                leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = filter.vision,
                onClick = { onChange(filter.copy(vision = !filter.vision)) },
                label = { Text(tr("Bilder")) },
                leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = filter.tools,
                onClick = { onChange(filter.copy(tools = !filter.tools)) },
                label = { Text(tr("Werkzeuge")) },
                leadingIcon = { Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            LevelChip(tr("Leistung"), filter.minPower) { onChange(filter.copy(minPower = it)) }
            LevelChip(tr("Tempo"), filter.minSpeed) { onChange(filter.copy(minSpeed = it)) }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = filter.sort != ModelSort.DEFAULT,
                    onClick = { sortOpen = true },
                    label = { Text(if (filter.sort == ModelSort.DEFAULT) tr("Sortieren") else tr("Nach {0}", filter.sort.label)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    ModelSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            trailingIcon = { if (s == filter.sort) Icon(Icons.Outlined.Check, contentDescription = null) },
                            onClick = { onChange(filter.copy(sort = s)); sortOpen = false },
                        )
                    }
                }
            }
            FilterChip(
                selected = filter.hideAliases,
                onClick = { onChange(filter.copy(hideAliases = !filter.hideAliases)) },
                label = { Text(tr("Ohne Aliase")) },
            )
            if (filter.isActive) {
                TextButton(onClick = { onChange(ModelFilter(type = if (types == null) filter.type else null)) }) {
                    Text(tr("Zurücksetzen"))
                }
            }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Zurück"))
    }
}
