package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thepytales.llmhub.docs.PdfTable
import io.github.thepytales.llmhub.docs.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Prüfansicht of PDF-Tabellen nach Excel (AP-95): the PDF page on the left, the table found on the right with cells to
 * correct, rows to drop; numbers are shown right-aligned as Excel will treat them. „Übernehmen“ hands the corrected
 * tables back (then Excel or the table editor).
 */
@Composable
fun PdfTableReview(file: File, tables: List<PdfTable>, onDone: (List<PdfTable>) -> Unit, onDismiss: () -> Unit) {
    val edited = remember(tables) { mutableStateListOf(*tables.map { t -> t.rows.map { it.toMutableList() } }.toTypedArray()) }
    var which by remember { mutableIntStateOf(0) }
    val table = tables[which]
    var page by remember(which) { mutableIntStateOf(table.page) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize().padding(24.dp).testTag("pdf-table-review")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Tabellen prüfen"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(tr("Abbrechen")) }
                    Button(onClick = { onDone(tables.mapIndexed { i, t -> t.copy(rows = edited[i].map { it.toList() }) }) }) { Text(tr("Übernehmen")) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tables.forEachIndexed { i, t ->
                        FilterChip(selected = i == which, onClick = { which = i }, label = { Text(tr("Tabelle {0} · {1}", i + 1, tr("S.") + " " + (if (t.lastPage > t.page) "${t.page + 1}–${t.lastPage + 1}" else "${t.page + 1}"))) })
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth > 700.dp
                    val content: @Composable (Modifier, Modifier) -> Unit = { left, right ->
                        Column(left, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (table.lastPage > table.page) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (table.page..table.lastPage).forEach { p -> FilterChip(selected = p == page, onClick = { page = p }, label = { Text(tr("S.") + " ${p + 1}") }) }
                            }
                            val image by produceState<ImageBitmap?>(null, file, page) { value = withContext(Dispatchers.IO) { runCatching { PdfRender.page(file, page, 900) }.getOrNull() } }
                            Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                                image?.let { Image(it, contentDescription = tr("Seite {0}", page + 1), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                            }
                        }
                        Column(right.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                            edited[which].forEachIndexed { r, row ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    row.forEachIndexed { col, cell ->
                                        val number = r > 0 && XlsxWriter.number(cell) != null && cell.any { it.isDigit() }
                                        BasicTextField(
                                            value = cell,
                                            onValueChange = { v -> edited[which] = edited[which].mapIndexed { i, rr -> if (i == r) rr.toMutableList().also { it[col] = v } else rr }.toMutableList() },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = if (number) TextAlign.End else TextAlign.Start,
                                                fontWeight = if (r == 0) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                            ),
                                            modifier = Modifier.width(120.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                .background(if (r == 0) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                        )
                                    }
                                    IconButton(onClick = { edited[which] = edited[which].filterIndexed { i, _ -> i != r }.toMutableList() }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = tr("Zeile löschen"), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        content(Modifier.weight(0.45f).fillMaxHeight(), Modifier.weight(0.55f).fillMaxHeight())
                    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        content(Modifier.weight(0.4f).fillMaxWidth(), Modifier.weight(0.6f).fillMaxWidth())
                    }
                }
            }
        }
    }
}
