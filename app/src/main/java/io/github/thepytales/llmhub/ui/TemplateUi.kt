package io.github.thepytales.llmhub.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Template
import io.github.thepytales.llmhub.data.TemplateCategory
import io.github.thepytales.llmhub.data.TemplateFields
import io.github.thepytales.llmhub.data.TemplateKind
import io.github.thepytales.llmhub.data.TemplateLibrary

fun categoryIcon(category: TemplateCategory): ImageVector = when (category) {
    TemplateCategory.TUD -> Icons.Outlined.Description
    TemplateCategory.TEACHING -> Icons.Outlined.School
    TemplateCategory.THESES -> Icons.Outlined.HistoryEdu
    TemplateCategory.RESEARCH -> Icons.Outlined.Science
    TemplateCategory.COMMUNICATION -> Icons.Outlined.Email
    TemplateCategory.GENERAL -> Icons.AutoMirrored.Outlined.ShortText
    TemplateCategory.PERSONA -> Icons.Outlined.Person
    TemplateCategory.OWN -> Icons.Outlined.Bookmarks
}

fun toggleFavorite(c: AppContainer, id: String) = c.settings.update { s ->
    s.copy(favoriteTemplates = if (id in s.favoriteTemplates) s.favoriteTemplates - id else s.favoriteTemplates + id)
}

private const val FAVORITES = "FAVORITES"

/** Fields filled in with the details from the settings (Einstellungen › Dokumente & Logos). */
private fun isSenderField(label: String) = label.startsWith("Absender") || label.startsWith("Meine Angaben")

/**
 * Searchable template library: filter chips per section, favourites marked with a star. Used by the template
 * picker in the chat and by the Vorlagen screen; [onOpen] decides what a tap does.
 */
@Composable
fun TemplateBrowser(
    c: AppContainer,
    onOpen: (Template) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: LazyListScope.() -> Unit = {},
) {
    val own by c.templates.items.collectAsStateWithLifecycle()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val all = remember(own) { TemplateLibrary.all(own) }
    val q = query.trim()
    val shown = all.filter { t ->
        (q.isEmpty() || listOf(t.name, t.description, t.content).any { it.contains(q, ignoreCase = true) }) && when (filter) {
            null -> true
            FAVORITES -> t.id in s.favoriteTemplates
            TemplateCategory.OWN.name -> !t.builtIn
            else -> t.section.name == filter
        }
    }
    LazyColumn(modifier, contentPadding = contentPadding) {
        header()
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Vorlagen durchsuchen") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Suche leeren") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item(key = "filters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Alle") })
                if (s.favoriteTemplates.isNotEmpty()) {
                    FilterChip(
                        selected = filter == FAVORITES,
                        onClick = { filter = if (filter == FAVORITES) null else FAVORITES },
                        label = { Text("Favoriten") },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                TemplateCategory.entries.forEach { cat ->
                    val count = all.count { if (cat == TemplateCategory.OWN) !it.builtIn else it.section == cat }
                    if (count > 0) FilterChip(
                        selected = filter == cat.name,
                        onClick = { filter = if (filter == cat.name) null else cat.name },
                        label = { Text(cat.label) },
                    )
                }
            }
        }
        TemplateCategory.entries.forEach { cat ->
            val list = shown.filter { it.section == cat }
            if (list.isEmpty()) return@forEach
            item(key = "h_$cat") {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp)) {
                    Text(cat.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(cat.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { t ->
                val favorite = t.id in s.favoriteTemplates
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(t) },
                    leadingContent = { Icon(categoryIcon(cat), contentDescription = null) },
                    headlineContent = { Text(if (t.builtIn) t.name else "${t.name} · eigene") },
                    supportingContent = { Text(t.description.ifBlank { t.content }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { toggleFavorite(c, t.id) }) {
                            Icon(
                                if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (favorite) "Aus den Favoriten entfernen" else "Zu den Favoriten",
                                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
        if (shown.isEmpty()) item(key = "empty") {
            Text(
                "Keine passende Vorlage gefunden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

/**
 * Fills the {{Feld}} placeholders of a prompt template. "Absender" fields start with the details from the
 * settings. [onInsert] puts the result into the input field (to add files or change it), [onSend] sends it.
 */
@Composable
fun TemplateFillDialog(c: AppContainer, template: Template, onInsert: (String) -> Unit, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val sender = c.settings.current.senderInfo
    val fields = remember(template.id) { template.fields }
    val values = remember(template.id) {
        mutableStateMapOf<String, String>().apply { fields.forEach { put(it.label, if (isSenderField(it.label)) sender else "") } }
    }
    // Without any input the template name is sent, so that its instructions still go to the model.
    fun result() = TemplateFields.fill(template.content, values).ifBlank { template.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(categoryIcon(template.section), contentDescription = null) },
        title = { Text(template.name) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template.description.isNotBlank()) {
                    Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                fields.forEach { f ->
                    OutlinedTextField(
                        value = values[f.label].orEmpty(),
                        onValueChange = { values[f.label] = it },
                        label = { Text(f.label) },
                        placeholder = if (f.hint.isNotEmpty()) ({ Text(f.hint) }) else null,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (sender.isBlank() && fields.any { isSenderField(it.label) }) {
                    Text(
                        "Tipp: Ihre Absenderangaben einmal unter Einstellungen › Dokumente & Logos hinterlegen – dann sind sie hier schon ausgefüllt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Leere Felder werden weggelassen. „Einfügen“ übernimmt den Text ins Eingabefeld, z.B. um noch eine Datei anzuhängen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(result()); onDismiss() }) { Text("Senden") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
                TextButton(onClick = { onInsert(result()); onDismiss() }) { Text("Einfügen") }
            }
        },
    )
}

/** What a template does: its fields, the text and the instructions for the model. */
@Composable
fun TemplateDetailsDialog(
    c: AppContainer,
    template: Template,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showInstructions by remember { mutableStateOf(false) }
    val favorite = template.id in s.favoriteTemplates
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(categoryIcon(template.section), contentDescription = null) },
        title = { Text(template.name) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOf(template.section.label, if (template.builtIn) "in der App enthalten" else "eigene Vorlage").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (template.description.isNotBlank()) Text(template.description, style = MaterialTheme.typography.bodyMedium)
                val fields = template.fields
                Text(
                    when {
                        template.kind == TemplateKind.PERSONA -> "Persona: legt fest, wie sich das Modell im ganzen Chat verhält."
                        fields.isEmpty() -> "Fügt den Text ins Eingabefeld ein."
                        else -> "Beim Verwenden fragt die App: " + fields.joinToString(", ") { it.label } + "."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Text(template.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
                if (template.instructions.isNotBlank()) {
                    Text(
                        if (showInstructions) "Anweisungen an das Modell ausblenden" else "Anweisungen an das Modell anzeigen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showInstructions = !showInstructions }.padding(vertical = 4.dp),
                    )
                    if (showInstructions) {
                        Text(template.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { toggleFavorite(c, template.id) }) {
                        Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (favorite) "Favorit" else "Zu Favoriten")
                    }
                    if (!template.builtIn) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(TemplateLibrary.export(listOf(template))))
                            Toast.makeText(context, "Vorlage kopiert – z.B. per Mail an Kolleg:innen schicken.", Toast.LENGTH_LONG).show()
                        }) { Text("Teilen") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onUse() }) { Text("Verwenden") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Schließen") }
                TextButton(onClick = { onDismiss(); onEdit() }) { Text(if (template.builtIn) "Anpassen" else "Bearbeiten") }
            }
        },
    )
}
