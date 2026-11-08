package io.github.thepytales.llmhub.creative

import androidx.compose.ui.geometry.Rect
import io.github.thepytales.llmhub.data.DesignElement

/**
 * Tables on the shared canvas (AP-50): rows × columns of short texts in the box of the element, the head row in TUD blue
 * with white bold text, the other rows alternating white and light blue, thin grey lines – the table style of the TUD
 * slides. Every renderer (Gestalten, slides, Android) and the PowerPoint export take the cells from here.
 */
object CanvasTable {
    const val HEAD_FILL = "#00008C"
    const val HEAD_TEXT = "#FFFFFF"
    const val BAND_FILL = "#E5F1FF"
    const val LINE = "#BBC1C6"

    /** A new table of [rows] × [cols] with a head row. */
    fun blank(rows: Int = 3, cols: Int = 3): List<List<String>> =
        List(rows) { r -> List(cols) { c -> if (r == 0) "Spalte ${c + 1}" else "" } }

    fun cols(rows: List<List<String>>): Int = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

    /** The rows filled up to the same number of columns (at least one cell). */
    fun normalized(rows: List<List<String>>): List<List<String>> {
        val n = cols(rows)
        return rows.ifEmpty { listOf(listOf("")) }.map { r -> r + List(n - r.size) { "" } }
    }

    /** Cell [r], [c] in the element's own box (mm): equal rows and columns. */
    fun cell(e: DesignElement, r: Int, c: Int): Rect {
        val rows = normalized(e.rows)
        val cw = e.w / cols(rows)
        val rh = e.h / rows.size
        return Rect(c * cw, r * rh, (c + 1) * cw, (r + 1) * rh)
    }

    /** Background of row [r] ("" = none). */
    fun fill(e: DesignElement, r: Int): String = when {
        e.header && r == 0 -> HEAD_FILL
        (if (e.header) r else r + 1) % 2 == 0 -> BAND_FILL
        else -> e.fill
    }

    fun textColor(e: DesignElement, r: Int): String = if (e.header && r == 0) HEAD_TEXT else e.color.ifBlank { "#000000" }

    fun bold(e: DesignElement, r: Int): Boolean = e.header && r == 0 || e.bold

    /** Inner margin of a cell in mm. */
    const val PAD = 1.2f

    /** [rows] with cell [r], [c] set to [text] (growing the table when needed). */
    fun withCell(rows: List<List<String>>, r: Int, c: Int, text: String): List<List<String>> {
        val n = maxOf(cols(rows), c + 1)
        val filled = (rows + List((r + 1 - rows.size).coerceAtLeast(0)) { emptyList() }).map { it + List(n - it.size) { "" } }
        return filled.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, v -> if (j == c) text else v } else row }
    }

    fun addRow(rows: List<List<String>>): List<List<String>> = normalized(rows) + listOf(List(cols(rows)) { "" })

    fun addColumn(rows: List<List<String>>): List<List<String>> = normalized(rows).mapIndexed { i, r -> r + (if (i == 0) "Spalte ${r.size + 1}" else "") }

    fun removeRow(rows: List<List<String>>): List<List<String>> = if (rows.size <= 1) rows else rows.dropLast(1)

    fun removeColumn(rows: List<List<String>>): List<List<String>> = if (cols(rows) <= 1) rows else normalized(rows).map { it.dropLast(1) }
}
