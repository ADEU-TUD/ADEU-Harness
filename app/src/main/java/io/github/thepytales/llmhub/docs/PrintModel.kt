package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.trLater

/** A font of a PDF and whether its program is inside the file (Druck-Lotse AP-97, Schriften-Notfall AP-91). */
data class PdfFontInfo(val name: String, val embedded: Boolean)

/** A picture of a PDF page with its effective resolution where it is drawn. */
data class PrintImage(val page: Int, val dpi: Int, val colorSpace: String, val widthPx: Int, val heightPx: Int)

/** One finding of the print check; [severe] ones should be fixed before going to a print shop. */
data class PrintIssue(val title: String, val detail: String, val severe: Boolean)

/** Result of the print check (Preflight) of a PDF. */
data class PrintCheck(
    val pages: Int,
    /** Page size of the first page in millimetres. */
    val widthMm: Double,
    val heightMm: Double,
    val issues: List<PrintIssue>,
    val fonts: List<PdfFontInfo>,
    val images: List<PrintImage>,
    val hasBleed: Boolean,
)

/**
 * What the Druck-Lotse makes (AP-97). The page order of a saddle-stitched booklet is computed here ([Imposition]),
 * the PDF work is done on the PC ([PrintPilot]).
 */
enum class PrintJob(val label: String, val hint: String) {
    BOOKLET(trLater("Broschüre (Rückenheftung)"), trLater("Zwei Seiten je Blattseite in Falt-Reihenfolge, auf ein Vielfaches von 4 aufgefüllt – doppelseitig über die kurze Kante drucken, falten, heften.")),
    N_UP(trLater("Mehrere Seiten pro Blatt"), trLater("2, 4, 6 oder 9 Seiten verkleinert auf ein Blatt – spart Papier für Leseexemplare.")),
    HANDOUT(trLater("Handzettel aus Folien"), trLater("3 Folien je Seite mit Linien für Notizen daneben.")),
    DUPLEX(trLater("Doppelseitig mit Bundsteg"), trLater("Verschiebt ungerade Seiten nach rechts und gerade nach links, damit beim Lochen oder Binden nichts verloren geht.")),
    BLEED(trLater("Beschnitt und Schnittmarken"), trLater("Für die Druckerei: 3 mm Beschnittzugabe rundum und Schnittmarken in den Ecken.")),
}

/** Page order for printing: sheets of a saddle-stitched booklet. */
object Imposition {
    /** Pages padded to a multiple of 4 (empty pages at the end). */
    fun padded(pages: Int): Int = ((pages + 3) / 4) * 4

    /**
     * For [pages] pages (1-based; numbers above [pages] are empty pages), the page pairs (left, right) of each sheet
     * side in printing order: front of sheet 1, back of sheet 1, front of sheet 2 … Folded and stapled in the middle,
     * the sheets give the pages in order.
     */
    fun booklet(pages: Int): List<Pair<Int, Int>> {
        val n = padded(pages)
        val out = mutableListOf<Pair<Int, Int>>()
        for (s in 0 until n / 4) {
            out += (n - 2 * s) to (1 + 2 * s)
            out += (2 + 2 * s) to (n - 1 - 2 * s)
        }
        return out
    }

    /** Columns × rows for [perSheet] pages on a sheet, and whether the sheet is landscape (for portrait pages). */
    fun grid(perSheet: Int): Triple<Int, Int, Boolean> = when (perSheet) {
        2 -> Triple(2, 1, true)
        4 -> Triple(2, 2, false)
        6 -> Triple(2, 3, false)
        9 -> Triple(3, 3, false)
        else -> Triple(1, 1, false)
    }
}
