package io.github.thepytales.llmhub.docs

/** A box to black out (Schwärzen, AP-26): page (0-based), place on the displayed page, the text found there. */
data class PdfRedaction(val page: Int, val box: PdfBox, val text: String = "")

/** A picture in a PDF: page (0-based), its name in the page's resources, size in pixels and in the file. */
data class PdfImageInfo(val page: Int, val name: String, val width: Int, val height: Int, val bytes: Long)

/** Title, author, subject, keywords, producing programs and language of a PDF; [xmp]: hidden XMP data present. */
data class PdfMeta(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val language: String = "",
    val xmp: Boolean = false,
)

/** One point of the PDF/UA check; [fixable]: „Einfache Mängel beheben“ repairs it. */
data class PdfUaFinding(val level: Level, val text: String, val fixable: Boolean = false) {
    enum class Level { OK, ERROR }
}

/**
 * Zwei PDFs vergleichen (AP-26): page by page, word by word – what was removed, what was added. Works on the text of the
 * pages (PdfExtras.pageTexts), so on Android too; scans need the text recognition first.
 */
object PdfCompare {
    data class Change(val page: Int, val removed: String, val added: String)

    data class Result(val pagesA: Int, val pagesB: Int, val changes: List<Change>) {
        val same: Boolean get() = changes.isEmpty() && pagesA == pagesB
        val changedPages: List<Int> get() = changes.map { it.page }.distinct()
    }

    private fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** The differences of the page texts [a] and [b] (one string per page). */
    fun compare(a: List<String>, b: List<String>): Result {
        val changes = mutableListOf<Change>()
        for (p in 0 until maxOf(a.size, b.size)) {
            val wa = words(a.getOrElse(p) { "" })
            val wb = words(b.getOrElse(p) { "" })
            if (wa == wb) continue
            changes += diff(wa, wb).map { (r, ad) -> Change(p, r, ad) }
        }
        return Result(a.size, b.size, changes)
    }

    /** Runs of removed and added words (longest common subsequence; long pages are compared line by line). */
    fun diff(a: List<String>, b: List<String>): List<Pair<String, String>> {
        if (a.size.toLong() * b.size > 4_000_000L) {
            // Too long for word by word: whole blocks.
            return listOf(a.joinToString(" ") to b.joinToString(" "))
        }
        val n = a.size
        val m = b.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        val out = mutableListOf<Pair<String, String>>()
        val removed = mutableListOf<String>()
        val added = mutableListOf<String>()
        fun flush() {
            if (removed.isNotEmpty() || added.isNotEmpty()) out += removed.joinToString(" ") to added.joinToString(" ")
            removed.clear(); added.clear()
        }
        var i = 0
        var j = 0
        while (i < n || j < m) {
            when {
                i < n && j < m && a[i] == b[j] -> { flush(); i++; j++ }
                j < m && (i == n || lcs[i][j + 1] >= lcs[i + 1][j]) -> { added += b[j]; j++ }
                else -> { removed += a[i]; i++ }
            }
        }
        flush()
        return out
    }

    /** The comparison as Markdown (for Word): what changed on which page. */
    fun markdown(nameA: String, nameB: String, r: Result): String = buildString {
        append("# Vergleich\n\n**Alt:** ").append(nameA).append(" (").append(r.pagesA).append(" Seiten)  \n**Neu:** ").append(nameB).append(" (").append(r.pagesB).append(" Seiten)\n\n")
        if (r.same) { append("Der Text beider Dateien ist gleich.\n"); return@buildString }
        if (r.pagesA != r.pagesB) append("Die Seitenzahl unterscheidet sich.\n\n")
        for ((page, list) in r.changes.groupBy { it.page }) {
            append("## Seite ").append(page + 1).append("\n\n")
            for (c in list) {
                when {
                    c.removed.isEmpty() -> append("- **neu:** ").append(c.added)
                    c.added.isEmpty() -> append("- **entfernt:** ~~").append(c.removed).append("~~")
                    else -> append("- ~~").append(c.removed).append("~~ → **").append(c.added).append("**")
                }
                append('\n')
            }
            append('\n')
        }
    }
}
