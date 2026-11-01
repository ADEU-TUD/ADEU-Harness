package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The editor of Markdown files (like Obsidian or a README on GitHub): the source with all its marks, a toolbar that
 * inserts them, and a preview – beside the source on wide screens, to switch to on phones.
 */
@Composable
fun MarkdownBody(markdown: String, tabId: String?, narrow: Boolean, leading: @Composable () -> Unit, onChange: (String) -> Unit) {
    var value by remember(tabId) { mutableStateOf(TextFieldValue(markdown)) }
    var view by remember { mutableStateOf("split") }
    LaunchedEffect(markdown) { if (markdown != value.text) value = value.copy(text = markdown, selection = TextRange(value.selection.start.coerceAtMost(markdown.length))) }
    LaunchedEffect(value.text) {
        delay(600)
        if (value.text != markdown) onChange(value.text)
    }
    fun linePrefix(prefix: String) {
        val t = value.text
        val start = t.lastIndexOf('\n', value.selection.min - 1) + 1
        val line = t.substring(start).substringBefore('\n')
        // Same kind of heading again removes it; another one replaces it.
        val existing = Regex("^(#{1,6} |> |- \\[ ] |- |\\d+\\. )").find(line)?.value.orEmpty()
        val next = if (existing == prefix) "" else prefix
        val text = t.substring(0, start) + next + line.removePrefix(existing) + t.substring(start + line.length)
        val delta = next.length - existing.length
        value = TextFieldValue(text, TextRange((value.selection.start + delta).coerceIn(start, text.length)))
    }
    fun insert(block: String) {
        val at = value.selection.max
        val before = if (at == 0 || value.text[at - 1] == '\n') "" else "\n\n"
        val text = value.text.substring(0, at) + before + block + "\n" + value.text.substring(at)
        value = TextFieldValue(text, TextRange(at + before.length + block.length))
    }
    val showSource = view != "preview"
    val showPreview = view == "preview" || (view == "split" && !narrow)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            ToolLabel("H1") { linePrefix("# ") }
            ToolLabel("H2") { linePrefix("## ") }
            ToolLabel("H3") { linePrefix("### ") }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            ToolIcon(Icons.Outlined.FormatBold, tr("Fett (Strg+B)")) { value = toggleMarks(value, "**") }
            ToolIcon(Icons.Outlined.FormatItalic, tr("Kursiv (Strg+I)")) { value = toggleMarks(value, "*") }
            ToolIcon(Icons.Outlined.Code, tr("Code")) { value = toggleMarks(value, "`") }
            ToolIcon(Icons.Outlined.Link, tr("Link (Strg+K)")) { value = toggleMarks(value, "[", "](https://)") }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, tr("Aufzählung")) { linePrefix("- ") }
            ToolIcon(Icons.Outlined.FormatListNumbered, tr("Nummerierte Liste")) { linePrefix("1. ") }
            ToolIcon(Icons.Outlined.CheckBox, tr("Checkliste")) { linePrefix("- [ ] ") }
            ToolIcon(Icons.Outlined.FormatQuote, tr("Zitat")) { linePrefix("> ") }
            ToolIcon(Icons.Outlined.TableChart, tr("Tabelle")) { insert("| Spalte 1 | Spalte 2 |\n| --- | --- |\n|  |  |") }
            ToolIcon(Icons.Outlined.HorizontalRule, tr("Trennlinie")) { insert("---") }
            ToolLabel("```") { insert("```\n\n```") }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            listOf("source" to tr("Quelltext"), "split" to (if (narrow) tr("Quelltext") else "Geteilt"), "preview" to "Vorschau").distinctBy { it.second }.forEach { (k, l) ->
                FilterChip(view == k || (narrow && k == "source" && view == "split"), { view = k }, label = { Text(l) }, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
        HorizontalDivider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (showSource) {
                Surface(color = LocalChatLook.current.card, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            val at = value.selection.start
                            value = if (value.selection.collapsed && next.text.length == value.text.length + 1 &&
                                next.text == value.text.substring(0, at) + "\n" + value.text.substring(at)
                            ) continueList(value) ?: next else next
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.isCtrlPressed && e.key == Key.B -> { value = toggleMarks(value, "**"); true }
                                e.isCtrlPressed && e.key == Key.I -> { value = toggleMarks(value, "*"); true }
                                e.isCtrlPressed && e.key == Key.K -> { value = toggleMarks(value, "[", "](https://)"); true }
                                e.key == Key.Tab && Regex("^\\s*([-*+]|\\d+\\.)\\s").containsMatchIn(value.text.substring(value.text.lastIndexOf('\n', value.selection.start - 1) + 1)) -> {
                                    value = indentLine(value, e.isShiftPressed); true
                                }
                                else -> false
                            }
                        },
                    )
                }
            }
            if (showPreview) {
                if (showSource) VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (value.text.isBlank()) HintText(tr("Die Vorschau erscheint, sobald du schreibst."))
                        MarkdownText(value.text)
                    }
                }
            }
        }
    }
}
