package io.github.thepytales.llmhub.docs.text

import io.github.thepytales.llmhub.docs.DocBlocks
import io.github.thepytales.llmhub.ui.ALIGN_MARK
import io.github.thepytales.llmhub.ui.inlineSegments

/**
 * Gliederung (AP-46): the headings of a document as a tree, and moving whole sections (a heading with everything up
 * to the next heading of the same or a higher level) to another place.
 */
object DocOutline {
    /** A heading: [block] is its index among the document's Markdown blocks. */
    data class Heading(val block: Int, val level: Int, val text: String)

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")

    private fun blocks(markdown: String) = DocBlocks.split(markdown).filter { it.isNotBlank() }

    fun headings(markdown: String): List<Heading> = headingsOf(blocks(markdown))

    private fun headingsOf(blocks: List<String>): List<Heading> = blocks.mapIndexedNotNull { i, b ->
        val m = HEADING.find(b.trim()) ?: return@mapIndexedNotNull null
        val text = inlineSegments(ALIGN_MARK.replace(m.groupValues[2], "")).joinToString("") { it.text }.trim()
        Heading(i, m.groupValues[1].length.coerceAtMost(4), text)
    }

    /** The blocks of the section of heading [h]: from it to the next heading of the same or a higher level. */
    private fun section(blocks: List<String>, heads: List<Heading>, h: Int): IntRange {
        val start = heads[h].block
        val end = heads.drop(h + 1).firstOrNull { it.level <= heads[h].level }?.block ?: blocks.size
        return start until end
    }

    /**
     * [markdown] with the section of heading [from] moved before heading [to] ([to] = number of headings: to the end).
     * Unchanged when the target lies inside the section itself.
     */
    fun move(markdown: String, from: Int, to: Int): String {
        val blocks = blocks(markdown)
        val heads = headingsOf(blocks)
        if (from !in heads.indices || to !in 0..heads.size) return markdown
        val sec = section(blocks, heads, from)
        val target = if (to == heads.size) blocks.size else heads[to].block
        if (target in sec || target == sec.last + 1) return markdown
        val moved = blocks.subList(sec.first, sec.last + 1)
        val rest = blocks.filterIndexed { i, _ -> i !in sec }
        val at = if (target > sec.last) target - moved.size else target
        val out = rest.toMutableList().apply { addAll(at, moved) }
        return DocBlocks.join(out)
    }

    /** One step up (before the previous heading of the same level) or down (after the next one). */
    fun step(markdown: String, from: Int, up: Boolean): String {
        val heads = headings(markdown)
        val h = heads.getOrNull(from) ?: return markdown
        if (up) {
            val prev = (from - 1 downTo 0).firstOrNull { heads[it].level <= h.level } ?: return markdown
            if (heads[prev].level < h.level) return markdown
            return move(markdown, from, prev)
        }
        val blocks = blocks(markdown)
        val sec = section(blocks, heads, from)
        val next = heads.indexOfFirst { it.block == sec.last + 1 }.takeIf { it >= 0 && heads[it].level == h.level } ?: return markdown
        // After the next sibling's section: before the heading that follows it, or at the end.
        val afterNext = section(blocks, heads, next).last + 1
        val target = heads.indexOfFirst { it.block == afterNext }.takeIf { it >= 0 } ?: heads.size
        return move(markdown, from, target)
    }
}
