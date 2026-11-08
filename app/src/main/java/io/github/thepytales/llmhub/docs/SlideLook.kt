package io.github.thepytales.llmhub.docs

import io.github.thepytales.llmhub.ui.trLater

/**
 * „Folie schöner machen“ und Sprechertexte (AP-51) without a model: layouts that suit the content of a slide (two
 * columns, comparison, table, chart, quote, picture large, shorter points, points one by one), each a suggestion to
 * tap; and speaker notes written from the points. Lora's actions add the model's suggestions when one is allowed.
 */
object SlideLook {
    /** A suggestion: what it does and the slide(s) that replace the slide. */
    data class Suggestion(val label: String, val slides: List<SlideDeck.Slide>)

    private val ITEM = Regex("^([-*+]|\\d+[.)])\\s+")

    /** The top-level points of a slide (without their marks); sub-points are left out. */
    fun points(body: String): List<String> = body.lines().filter { ITEM.containsMatchIn(it) }.map { it.replace(ITEM, "").trim() }.filter { it.isNotEmpty() }

    private fun bullets(list: List<String>) = list.joinToString("\n") { "- $it" }

    private val LABELLED = Regex("^\\**([^:*]{2,40}?)\\**\\s*[:–—-]\\s+(.+)$")
    private val NUMBER = Regex("(-?\\d+(?:[.,]\\d+)?)\\s*(%|prozent|percent)?", RegexOption.IGNORE_CASE)

    /** Layout suggestions for [s] (the title slide gets none); at most [max], none equal to the slide. */
    fun suggest(s: SlideDeck.Slide, max: Int = 6): List<Suggestion> {
        if (s.layout == SlideDeck.Layout.TITLE) return emptyList()
        val out = mutableListOf<Suggestion>()
        val pts = points(s.body)
        val subs = s.body.lines().count { it.startsWith(" ") && ITEM.containsMatchIn(it.trimStart()) }
        val labelled = pts.mapNotNull { LABELLED.find(it)?.let { m -> m.groupValues[1].trim() to m.groupValues[2].trim() } }
        // Numbers per point („Gruppe A: 42 %“): a chart says it at a glance.
        val numbers = pts.mapNotNull { p ->
            val m = LABELLED.find(p)
            val label = m?.groupValues?.get(1)?.trim() ?: p.replace(NUMBER, "").trim().trimEnd(':', ',', '–', '-').trim()
            val value = NUMBER.findAll(m?.groupValues?.get(2) ?: p).toList().singleOrNull()?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            if (value != null && label.length in 1..40) label to value else null
        }
        if (pts.size >= 3 && numbers.size == pts.size) {
            val spec = ChartSpec(ChartSpec.Type.BAR, s.title, labels = numbers.map { it.first }, series = listOf(ChartSpec.Series(s.title.ifBlank { "Werte" }, numbers.map { it.second })))
            out += Suggestion(trLater("Als Diagramm"), listOf(s.copy(layout = SlideDeck.Layout.CHART, body = "```chart\n" + spec.toJson() + "\n```", build = false)))
        }
        // „Begriff: Erklärung“ in every point: a table with two columns.
        if (pts.size >= 3 && labelled.size == pts.size && s.layout != SlideDeck.Layout.TABLE) {
            val head = if (labelled.all { NUMBER.matches(it.second) }) listOf("", "Wert") else listOf("Begriff", "Bedeutung")
            out += Suggestion(trLater("Als Tabelle"), listOf(s.copy(layout = SlideDeck.Layout.TABLE, body = DocBlocks.table(listOf(head) + labelled.map { listOf(it.first, it.second) }), build = false)))
        }
        // Two sides („Vorher … Nachher“, „Pro … Contra“): a comparison with heads.
        val sides = listOf("vorher" to "nachher", "pro" to "contra", "vorteile" to "nachteile", "früher" to "heute", "ist" to "soll", "before" to "after")
        val split = sides.firstNotNullOfOrNull { (l, r) ->
            val li = pts.indexOfFirst { it.lowercase().startsWith(l) }
            val ri = pts.indexOfFirst { it.lowercase().startsWith(r) }
            if (li >= 0 && ri > li) Triple(l, r, li to ri) else null
        }
        if (split != null && s.layout != SlideDeck.Layout.COMPARISON) {
            val (l, r, at) = split
            fun rest(p: String, word: String) = p.drop(word.length).trimStart(':', ' ', '–', '-').trim()
            val left = (listOf(rest(pts[at.first], l)) + pts.subList(at.first + 1, at.second)).filter { it.isNotBlank() }
            val right = (listOf(rest(pts[at.second], r)) + pts.drop(at.second + 1)).filter { it.isNotBlank() }
            out += Suggestion(trLater("Als Vergleich"), listOf(s.copy(layout = SlideDeck.Layout.COMPARISON,
                body = SlideDeck.withColumnHead(l.replaceFirstChar { it.uppercase() }, bullets(left.ifEmpty { listOf("…") })),
                right = SlideDeck.withColumnHead(r.replaceFirstChar { it.uppercase() }, bullets(right.ifEmpty { listOf("…") })))))
        } else if (pts.size >= 4 && s.layout == SlideDeck.Layout.CONTENT && pts.all { it.length <= 80 }) {
            val half = (pts.size + 1) / 2
            out += Suggestion(trLater("In zwei Spalten"), listOf(s.copy(layout = SlideDeck.Layout.TWO_COLUMNS, body = bullets(pts.take(half)), right = bullets(pts.drop(half)))))
        }
        // Long points: short ones on the slide, the full sentences in the notes.
        if (pts.any { it.length > 90 }) {
            val short = pts.map { PaperDeck.shorten(it.substringBefore(". ").substringBefore("; "), 70) }
            out += Suggestion(trLater("Kürzere Punkte"), listOf(s.copy(body = bullets(short), notes = listOf(s.notes, pts.joinToString(" ") { it.trimEnd('.') + "." }).filter { it.isNotBlank() }.joinToString("\n\n"))))
        }
        // Too many points: two slides.
        if (pts.size > 6 && s.layout == SlideDeck.Layout.CONTENT) {
            out += Suggestion(trLater("Auf zwei Folien"), SlideDeck.splitFull(listOf(SlideDeck.Slide(), s), max = (pts.size + 1) / 2).first.drop(1))
        }
        if (subs > 0 && pts.size >= 2) {
            val details = s.body.lines().filter { it.startsWith(" ") && ITEM.containsMatchIn(it.trimStart()) }.joinToString(" ") { it.trimStart().replace(ITEM, "").trimEnd('.') + "." }
            out += Suggestion(trLater("Unterpunkte in die Notizen"), listOf(s.copy(body = bullets(pts), notes = listOf(s.notes, details).filter { it.isNotBlank() }.joinToString("\n\n"))))
        }
        // One sentence: a quote or a section slide.
        if (pts.isEmpty() && s.body.isNotBlank() && s.body.length <= 200 && '\n' !in s.body.trim() && s.layout != SlideDeck.Layout.QUOTE) {
            out += Suggestion(trLater("Als Zitat"), listOf(s.copy(layout = SlideDeck.Layout.QUOTE, body = "> " + s.body.trim().removePrefix(">").trim())))
        }
        // Pictures: large, or on the other side.
        if (s.image.isNotBlank()) {
            if (s.layout == SlideDeck.Layout.IMAGE_TEXT) {
                out += Suggestion(trLater("Bild groß, Text in die Notizen"), listOf(s.copy(layout = SlideDeck.Layout.IMAGE, body = "", notes = listOf(s.notes, pts.joinToString(" ")).filter { it.isNotBlank() }.joinToString("\n\n"))))
                out += Suggestion(if (s.imageLeft) trLater("Bild rechts") else trLater("Bild links"), listOf(s.copy(imageLeft = !s.imageLeft)))
            } else if (!s.layout.withImage) out += Suggestion(trLater("Bild + Text"), listOf(s.copy(layout = SlideDeck.Layout.IMAGE_TEXT)))
        }
        if (pts.size >= 2 && !s.build && s.layout in setOf(SlideDeck.Layout.CONTENT, SlideDeck.Layout.IMAGE_TEXT, SlideDeck.Layout.FREE)) {
            out += Suggestion(trLater("Punkte nacheinander"), listOf(s.copy(build = true, transition = s.transition.ifBlank { "fade" })))
        }
        return out.filter { it.slides != listOf(s) }.distinctBy { it.slides }.take(max)
    }

    /**
     * Speaker notes for slide [i] without a model: what the slide is about, its points as sentences in their order
     * and the bridge to the next slide.
     */
    fun notes(slides: List<SlideDeck.Slide>, i: Int): String {
        val s = slides[i]
        val next = slides.getOrNull(i + 1)?.title?.takeIf { it.isNotBlank() && slides[i + 1].layout != SlideDeck.Layout.END }
        val pts = points(s.body).map { it.replace(Regex("\\*\\*|__|`"), "").trimEnd('.', ';', ':') }
        val parts = mutableListOf<String>()
        when (s.layout) {
            SlideDeck.Layout.TITLE -> parts += "Herzlich willkommen zu „${s.title}“." + (s.body.lines().firstOrNull { it.isNotBlank() }?.let { " $it." } ?: "")
            SlideDeck.Layout.END -> parts += "Vielen Dank für Ihre Aufmerksamkeit – ich freue mich auf Ihre Fragen."
            SlideDeck.Layout.AGENDA -> parts += "Zuerst ein Überblick, worum es heute geht: " + SlideDeck.agenda(slides, i).joinToString(", ") + "."
            SlideDeck.Layout.SECTION -> parts += "Damit kommen wir zu „${s.title}“." + (s.body.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
            else -> {
                parts += "Auf dieser Folie geht es um „${s.title}“."
                val words = listOf("Zuerst", "Dann", "Außerdem", "Dazu kommt", "Weiter", "Schließlich")
                pts.forEachIndexed { k, p ->
                    val lead = if (k == pts.lastIndex && pts.size > 1) "Schließlich" else words.getOrElse(k) { "Außerdem" }
                    parts += "$lead: $p."
                }
                if (pts.isEmpty() && s.body.isNotBlank() && !s.body.trimStart().startsWith("```") && !s.body.trimStart().startsWith("|")) parts += s.body.replace(Regex("\\s+"), " ").trim()
                SlideDeck.chartOf(s.body)?.let { parts += "Das Diagramm zeigt ${it.title.ifBlank { "die Werte" }} – worauf es ankommt, ist der Unterschied zwischen den Balken." }
                if (s.image.isNotBlank() && s.imageAlt.isNotBlank()) parts += "Das Bild zeigt ${s.imageAlt.trimEnd('.')}."
            }
        }
        if (next != null && s.layout != SlideDeck.Layout.END) parts += "Als Nächstes: „$next“."
        return parts.joinToString(" ")
    }
}
