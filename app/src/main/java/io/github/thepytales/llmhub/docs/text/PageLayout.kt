package io.github.thepytales.llmhub.docs.text

/**
 * Seitenaufteilung des Text-Kerns (AP-43): where the pages of a document break. The input is the document as a row of
 * measured pieces – lines of paragraphs, blocks that are not split (pictures, formulas, tables), spaces between pieces
 * and forced page breaks –, the output is the page and place of every piece and the room to add before a piece so that
 * it starts on its page. The Fließtext-Editor (Seitenansicht) uses it with the lines its text fields measured; the rules
 * are those of Word and Google Docs:
 *
 * - **Witwen und Waisen**: a paragraph does not leave a single line at the end of a page (orphan) nor carry a single
 *   line over to the next page (widow); with two or three lines it moves as a whole.
 * - **Mit nächstem zusammenhalten**: headings (and the title) stay on the page of the paragraph after them.
 * - A piece taller than a page starts on a page of its own and runs on (the rules give way when nothing else fits).
 * - Spaces never start a page; a forced page break starts the next piece on a new page.
 */
object PageLayout {
    sealed class Item {
        abstract val height: Float
    }

    /** Line [line] of [lines] of paragraph [para] (unique in the document); [keepWithNext] for headings. */
    data class Line(override val height: Float, val para: Int, val line: Int, val lines: Int, val keepWithNext: Boolean = false) : Item()

    /** A piece that is not split: picture, formula, table, Inhaltsverzeichnis. */
    data class Block(override val height: Float, val keepWithNext: Boolean = false) : Item()

    /** Room between two pieces (the editor's spacing); never starts a page. */
    data class Space(override val height: Float) : Item()

    /** Seitenumbruch: the next piece starts on a new page. */
    data class Break(override val height: Float = 0f) : Item()

    /**
     * [page] and [y] (from the top of the page's text area) of every item; [push]: room to add before an item so that
     * it lands there (0 for most); [pages] in all; [rest]: free room at the end of the last page.
     */
    class Plan(val page: IntArray, val y: FloatArray, val push: FloatArray, val pages: Int, val rest: Float) {
        /** Indexes of the items that start a page (after the first one). */
        val pageStarts: List<Int> get() = page.indices.filter { it > 0 && page[it] != page[it - 1] && push[it] > 0f }
    }

    /** Lines a paragraph keeps together at a page end and at a page start. */
    const val MIN_LINES = 2

    /**
     * Breaks [items] into pages with a text area [pageHeight] high; [between] is the room from the end of one text area
     * to the start of the next (bottom margin, gap, top margin) – it goes into [Plan.push].
     */
    fun plan(items: List<Item>, pageHeight: Float, between: Float = 0f): Plan {
        val n = items.size
        val page = IntArray(n)
        val y = FloatArray(n)
        val h = pageHeight.coerceAtLeast(1f)
        val eps = 0.5f
        var p = 0
        var cy = 0f
        var pageStart = 0
        var i = 0
        var newPage = false
        // Each item is placed at most a few times (a break moves back, never past the page start), so this ends.
        var guard = 0
        while (i < n) {
            if (++guard > n * 8 + 64) break
            val it = items[i]
            if (newPage && it !is Space) {
                p++; cy = 0f; pageStart = i; newPage = false
            }
            if (it is Space || it is Break || i == pageStart || cy + it.height <= h + eps) {
                page[i] = p
                y[i] = cy
                cy += it.height
                if (it is Break) newPage = true
                // A piece taller than the page runs over the following pages.
                if (i == pageStart && it !is Space && cy > h + eps) {
                    val (np, ny) = overflow(p, cy, h, between)
                    p = np; cy = ny
                    if (cy <= 0f) { newPage = false; pageStart = i + 1 }
                }
                i++
                continue
            }
            val k = breakBefore(items, i, pageStart)
            p++; cy = 0f; pageStart = k; i = k
        }
        // Room before the items so that each lands at its place (absolute: page × (page + between) + y).
        val push = FloatArray(n)
        var acc = 0f
        var natural = 0f
        for (k in 0 until n) {
            val target = page[k] * (h + between) + y[k]
            val now = natural + acc
            if (items[k] !is Space && target - now > eps) {
                push[k] = target - now
                acc += push[k]
            }
            natural += items[k].height
        }
        val pages = if (n == 0) 1 else page.last() + 1 + (if (newPage) 1 else 0)
        return Plan(page, y, push, pages, if (newPage) h else (h - cy).coerceAtLeast(0f))
    }

    /** After a piece ran [cy] beyond the top of page [p]: the page and place where the next piece goes on. */
    private fun overflow(p: Int, cy: Float, h: Float, between: Float): Pair<Int, Float> {
        var np = p
        var ny = cy
        while (ny > h) {
            ny -= h + between
            np++
        }
        // It ended in the room between two text areas: the next piece starts at the top of the next one.
        if (ny < 0f) ny = 0f
        return np to ny
    }

    /** The item before which the page breaks when item [i] does not fit; never at or before [pageStart]. */
    private fun breakBefore(items: List<Item>, i: Int, pageStart: Int): Int {
        var k = i
        val it = items[i]
        if (it is Line) {
            val first = i - it.line
            // Witwe: at least two lines go over to the next page.
            if (it.lines - it.line < MIN_LINES && it.line > 0) k = (first + it.lines - MIN_LINES).coerceAtLeast(first)
            // Waise: at least two lines stay at the end of this page – else the paragraph moves as a whole.
            val staying = k - maxOf(first, pageStart)
            if (k > first && staying < MIN_LINES && first >= pageStart) k = first
        }
        // Headings stay with what follows them: the page breaks before the heading.
        var j = previousSolid(items, k)
        while (j >= pageStart && j >= 0 && keeps(items[j])) {
            val start = (items[j] as? Line)?.let { j - it.line } ?: j
            if (start < pageStart) break
            k = start
            j = previousSolid(items, k)
        }
        if (k <= pageStart) {
            // Nothing would stay on the page: keep only the paragraph rules if they leave something, else break here.
            k = i
            if (it is Line && it.line > 0) {
                val first = i - it.line
                val widow = (first + it.lines - MIN_LINES).coerceAtLeast(first)
                if (it.lines - it.line < MIN_LINES && widow > pageStart) k = widow
            }
        }
        // Spaces belong to the end of the page before.
        while (k < items.size && items[k] is Space) k++
        return k.coerceAtLeast(pageStart + 1).coerceAtMost(items.size)
    }

    private fun previousSolid(items: List<Item>, k: Int): Int {
        var j = k - 1
        while (j >= 0 && items[j] is Space) j--
        return j
    }

    private fun keeps(it: Item) = it is Line && it.keepWithNext || it is Block && it.keepWithNext
}
