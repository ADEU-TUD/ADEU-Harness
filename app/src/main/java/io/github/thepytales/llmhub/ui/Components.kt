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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelFilter
import io.github.thepytales.llmhub.data.ModelInfo
import io.github.thepytales.llmhub.data.ModelSort
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
        info.contextTokens?.let { Chip("${formatTokens(it)} Kontext") }
        if (info.thinking == true) Chip("Denken", Icons.Outlined.Psychology)
        if (info.vision == true) Chip("Bilder", Icons.Outlined.Visibility)
        if (info.tools == true) Chip("Werkzeuge", Icons.Outlined.Build)
        if (info.thinking == false) Chip("kein Denken", on = false)
        if (info.vision == false) Chip("keine Bilder", on = false)
        if (info.isAlias) Chip("Alias", on = false)
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

/** Bottom sheet to choose a model from [models]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelInfo>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf(ModelFilter()) }
    val shown = remember(models, filter) { filter.apply(models) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text(
            "Modell wählen",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ModelFilterBar(filter, { filter = it }, modifier = Modifier.padding(horizontal = 16.dp))
        if (shown.isEmpty()) {
            Text(
                "Kein Modell passt zum Filter.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(shown, key = { it.id }) { m ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(m.id); onDismiss() },
                    headlineContent = {
                        Text(m.displayName, fontWeight = if (m.id == selected) FontWeight.Bold else null)
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(m.aliasOf?.let { "→ $it · ${m.summary}" } ?: m.summary, style = MaterialTheme.typography.bodySmall)
                            CapabilityChips(m)
                            val e = ModelCatalog.effective(m)
                            if (e.power != null || e.speed != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    e.power?.let { RatingRow("Leistung", it) }
                                    e.speed?.let { RatingRow("Tempo", it) }
                                }
                            }
                        }
                    },
                    trailingContent = {
                        if (m.id == selected) Icon(Icons.Outlined.Check, contentDescription = "Ausgewählt")
                    },
                )
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
            DropdownMenuItem(text = { Text("Alle") }, onClick = { onSelect(0); open = false })
            (3..5).forEach { v ->
                DropdownMenuItem(
                    text = { Text("mind. " + "●".repeat(v) + "○".repeat(5 - v)) },
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
            placeholder = { Text("Modelle suchen (Name, Stärke, Anbieter …)") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onChange(filter.copy(query = "")) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Suche leeren")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        )
        if (types != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter.type == null, onClick = { onChange(filter.copy(type = null)) }, label = { Text("Alle") })
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
                label = { Text("Denken") },
                leadingIcon = { Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = filter.vision,
                onClick = { onChange(filter.copy(vision = !filter.vision)) },
                label = { Text("Bilder") },
                leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = filter.tools,
                onClick = { onChange(filter.copy(tools = !filter.tools)) },
                label = { Text("Werkzeuge") },
                leadingIcon = { Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            LevelChip("Leistung", filter.minPower) { onChange(filter.copy(minPower = it)) }
            LevelChip("Tempo", filter.minSpeed) { onChange(filter.copy(minSpeed = it)) }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = filter.sort != ModelSort.DEFAULT,
                    onClick = { sortOpen = true },
                    label = { Text(if (filter.sort == ModelSort.DEFAULT) "Sortieren" else "Nach ${filter.sort.label}") },
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
                label = { Text("Ohne Aliase") },
            )
            if (filter.isActive) {
                TextButton(onClick = { onChange(ModelFilter(type = if (types == null) filter.type else null)) }) {
                    Text("Zurücksetzen")
                }
            }
        }
    }
}
