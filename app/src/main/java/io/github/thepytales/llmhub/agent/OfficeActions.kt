package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.docs.SheetCalc
import io.github.thepytales.llmhub.docs.SheetChart
import io.github.thepytales.llmhub.docs.SheetEngine
import io.github.thepytales.llmhub.docs.SheetMeta
import io.github.thepytales.llmhub.docs.SheetTools
import io.github.thepytales.llmhub.docs.SlideDeck
import io.github.thepytales.llmhub.net.s
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater

/**
 * The open table or presentation (AP-38 Stufe c): the editor reports its Markdown here and takes a new one. Lora's
 * actions work on the data model (rows of the sheet, slides of the deck) and keep the Markdown before for Rückgängig.
 * The editors of AP-48 and AP-51 fulfil the same interface.
 */
interface EditorDocHost {
    /** Markdown of the open tab (sheet) or the whole deck. */
    fun markdown(): String

    /** Replaces it (the editor saves as after a change by hand). */
    fun replace(markdown: String)

    /** The selected slide (presentation) or row (sheet), from 0. */
    fun selected(): Int = 0
}

/** Pure helpers for Lora's table actions; the actions below and the tests use them. */
object SheetLora {
    /** The column [name] means: a letter (B), a number (2) or the (start of the) heading. Null when there is none. */
    fun column(rows: List<List<String>>, name: String): Int? {
        val n = name.trim().trim('"', '„', '“')
        if (n.isEmpty()) return null
        val head = rows.firstOrNull().orEmpty()
        head.indexOfFirst { it.trim().equals(n, true) }.takeIf { it >= 0 }?.let { return it }
        if (Regex("^[A-Za-z]{1,2}$").matches(n)) SheetEngine.parseRef(n.uppercase() + "1")?.second?.let { return it }
        n.toIntOrNull()?.let { if (it >= 1) return it - 1 }
        return head.indexOfFirst { it.trim().startsWith(n, true) }.takeIf { it >= 0 }
            ?: head.indexOfFirst { it.contains(n, true) }.takeIf { it >= 0 }
    }

    /** „A1=Name; B2=5“ (also with „:“ or line breaks) → cell (row, column) to value. */
    fun parseCells(spec: String): Map<Pair<Int, Int>, String> = spec.split(';', '\n').mapNotNull { part ->
        val m = Regex("^\\s*([A-Za-z]{1,3}\\d{1,6})\\s*[=:]\\s*(.*)$").find(part) ?: return@mapNotNull null
        val at = SheetEngine.parseRef(m.groupValues[1].uppercase()) ?: return@mapNotNull null
        at to m.groupValues[2].trim()
    }.toMap()

    fun setCells(rows: List<List<String>>, cells: Map<Pair<Int, Int>, String>): List<List<String>> {
        val height = maxOf(rows.size, (cells.keys.maxOfOrNull { it.first } ?: -1) + 1)
        val width = maxOf(rows.maxOfOrNull { it.size } ?: 0, (cells.keys.maxOfOrNull { it.second } ?: -1) + 1)
        return (0 until height).map { r -> (0 until width).map { c -> cells[r to c] ?: rows.getOrNull(r)?.getOrNull(c).orEmpty() } }
    }

    /**
     * A new column [header] at the right; [formula] is written into every data row with „{r}“ as the row number
     * (=B{r}*2 → =B2*2, =B3*2 …). Without „{r}“ the same value goes into every row.
     */
    fun addColumn(rows: List<List<String>>, header: String, formula: String): List<List<String>> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.mapIndexed { r, row ->
            val padded = row + List(width - row.size) { "" }
            padded + if (r == 0) header else formula.replace("{r}", (r + 1).toString())
        }
    }

    /** Data rows sorted by [col] (numbers and dates by value, empty cells last); the head row stays. */
    fun sortOrder(rows: List<List<String>>, col: Int, desc: Boolean): List<Int> {
        val order = (1 until rows.size).sortedWith { a, b ->
            val x = rows[a].getOrNull(col).orEmpty()
            val y = rows[b].getOrNull(col).orEmpty()
            val nx = SheetTools.sortKey(x)
            val ny = SheetTools.sortKey(y)
            val r = when {
                x.isBlank() != y.isBlank() -> return@sortedWith if (x.isBlank()) 1 else -1
                nx != null && ny != null -> nx.compareTo(ny)
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (desc) -r else r
        }
        return listOf(0) + order
    }

    /**
     * Cleans the data (AP-49 „Daten bereinigen“, already here for Lora): spaces at the ends and double spaces inside,
     * empty rows and rows that repeat an earlier one. Returns the rows and what changed.
     */
    fun clean(rows: List<List<String>>): Pair<List<List<String>>, List<String>> = cleanKeep(rows).let { (r, n, _) -> r to n }

    /** Like [clean], with the old index of every row kept (for moving the formats along). */
    fun cleanKeep(rows: List<List<String>>): Triple<List<List<String>>, List<String>, List<Int>> {
        var trimmed = 0
        val spaced = rows.map { row -> row.map { v -> v.trim().replace(Regex("[ \\t]{2,}"), " ").also { if (it != v) trimmed++ } } }
        // Dates in mixed formats (2026-04-03, 3.4.26, 03.04.2026) in a date column become 03.04.2026 (AP-49).
        var dated = 0
        val width = spaced.maxOfOrNull { it.size } ?: 0
        val dateCols = (0 until width).filter { c ->
            val cells = (1 until spaced.size).map { spaced[it].getOrNull(c).orEmpty() }.filter { it.isNotBlank() && !it.startsWith("=") }
            cells.isNotEmpty() && cells.count { SheetEngine.parseDate(it) != null } * 2 > cells.size
        }.toSet()
        val tidy = spaced.mapIndexed { r, row ->
            if (r == 0 || dateCols.isEmpty()) row
            else row.mapIndexed { c, v ->
                if (c !in dateCols || v.startsWith("=")) v
                else SheetEngine.parseDate(v)?.let(SheetEngine::dateText)?.also { if (it != v) dated++ } ?: v
            }
        }
        val seen = HashSet<List<String>>()
        var empty = 0
        var dupes = 0
        val kept = tidy.indices.filter { i ->
            val row = tidy[i]
            when {
                i == 0 -> true
                row.all { it.isBlank() } -> { empty++; false }
                !seen.add(row) -> { dupes++; false }
                else -> true
            }
        }
        val out = kept.map { tidy[it] }
        val notes = buildList {
            if (trimmed > 0) add(tr("{0} Zellen ohne überzählige Leerzeichen", trimmed))
            if (dated > 0) add(tr("{0} Datumsangaben als TT.MM.JJJJ vereinheitlicht", dated))
            if (empty > 0) add(tr("{0} leere Zeilen entfernt", empty))
            if (dupes > 0) add(tr("{0} doppelte Zeilen entfernt", dupes))
        }
        return Triple(out, notes, kept)
    }

    /** Sum and mean below every column with numbers (as formulas, so they follow changes). */
    fun summary(rows: List<List<String>>): List<List<String>>? {
        if (rows.size < 2) return null
        val width = rows.maxOf { it.size }
        val numeric = (0 until width).filter { c ->
            val cells = (1 until rows.size).map { r -> rows[r].getOrNull(c).orEmpty().trim() }.filter { it.isNotEmpty() }
            val numbers = cells.count { SheetEngine.parseDate(it) == null && SheetTools.sortKey(it) != null }
            numbers > 0 && numbers * 2 >= cells.size
        }
        if (numeric.isEmpty()) return null
        val last = rows.size
        val label = numeric.first().let { if (it > 0) 0 else -1 }
        fun row(name: String, fn: String) = (0 until width).map { c ->
            when {
                c in numeric -> "=$fn(${SheetEngine.colName(c)}2:${SheetEngine.colName(c)}$last)"
                c == label -> name
                else -> ""
            }
        }
        return rows + listOf(row(tr("Summe"), "SUMME"), row(tr("Mittelwert"), "MITTELWERT"))
    }
}

/** Pure helpers for Lora's slide actions. */
object SlidesLora {
    /** Slide number [n] (from 1) as an index, or the selected one when [n] is blank. */
    fun index(slides: List<SlideDeck.Slide>, n: String?, selected: Int): Int? {
        val t = n?.trim().orEmpty()
        if (t.isEmpty() || t.lowercase() in setOf("diese", "aktuelle", "markierte")) return selected.takeIf { it in slides.indices }
        if (t.lowercase() in setOf("letzte", "last")) return slides.lastIndex
        t.filter { it.isDigit() }.toIntOrNull()?.let { return (it - 1).takeIf { i -> i in slides.indices } }
        return slides.indexOfFirst { it.title.contains(t, true) }.takeIf { it >= 0 }
    }

    /** Points of a text: lines, or sentences split at „;“ – each a bullet. */
    fun bullets(text: String): String = text.split('\n', ';').map { it.trim().removePrefix("-").removePrefix("•").trim() }
        .filter { it.isNotEmpty() }.joinToString("\n") { "- $it" }

    /** Slides from an outline: „# Titel“ / „Titel:“ starts a slide, the lines below are its points. */
    fun fromOutline(outline: String): List<SlideDeck.Slide> {
        val out = mutableListOf<SlideDeck.Slide>()
        var title: String? = null
        val points = mutableListOf<String>()
        fun flush() {
            title?.let { out += SlideDeck.Slide(it, bullets(points.joinToString("\n"))) }
            points.clear()
        }
        outline.lines().filter { it.isNotBlank() }.forEach { l ->
            val t = l.trim()
            val bullet = t.startsWith("-") || t.startsWith("•") || t.startsWith("* ") || l.startsWith(" ") || l.startsWith("\t")
            val head = Regex("^#{1,3}\\s+(.+)$").find(t)?.groupValues?.get(1)
                ?: t.removeSuffix(":").takeIf { !bullet && t.length <= 90 }
            if (head != null) { flush(); title = head.trim() } else points += t
        }
        flush()
        return out
    }

    /** One slide as the editor's Markdown (for the model). */
    fun slideMarkdown(s: SlideDeck.Slide): String = SlideDeck.markdown(listOf(SlideDeck.Slide(), s)).substringAfter("---").trim()

    private suspend fun ask(c: io.github.thepytales.llmhub.AppContainer, model: String, prompt: String): String {
        val r = c.llm.streamChat(model, listOf(io.github.thepytales.llmhub.data.ChatMessage(role = "user", content = prompt)), null, thinking = io.github.thepytales.llmhub.net.ThinkingLevel.OFF) {}
        return io.github.thepytales.llmhub.ui.splitThinking(r.content).second.trim()
    }

    /**
     * „Folie schöner machen“ with the model: up to two variants of slide [s] in the editor's Markdown (layout, shorter
     * points, notes); its picture and freely placed elements stay.
     */
    suspend fun modelSuggestions(c: io.github.thepytales.llmhub.AppContainer, model: String, s: SlideDeck.Slide): List<io.github.thepytales.llmhub.docs.SlideLook.Suggestion> {
        val prompt = buildString {
            append("Du gestaltest Folien für einen Vortrag im Design der TU Dresden. Mache diese Folie schöner und verständlicher: prägnanter Titel, ")
            append("höchstens 5 kurze Stichpunkte, ein Layout, das zum Inhalt passt. Gib genau zwei unterschiedliche Varianten zurück, getrennt durch eine Zeile ---. ")
            append("Format je Variante: „## Titel“, optional eine Layout-Zeile (<!-- layout: vergleich -->, <!-- layout: tabelle --> mit Markdown-Tabelle, ")
            append("<!-- layout: diagramm --> mit einem ```chart-Block {\"type\":\"bar\",\"labels\":[…],\"series\":[{\"name\":…,\"values\":[…]}]}), Stichpunkte mit „- “, ")
            append("zwei Spalten mit einer Zeile „|||“ dazwischen, am Ende „Notizen: …“ mit dem, was nicht auf die Folie passt. Nichts erfinden, was nicht auf der Folie steht; ")
            append("keine Erklärungen außerhalb der Folien.")
            LoraPrefs.instructions(c, ActionArea.SLIDES)?.let { append("\nVorlieben des Nutzers: ").append(it) }
            append("\n\nFolie:\n").append(slideMarkdown(s))
        }
        val text = ask(c, model, prompt).replace(Regex("^```(markdown|md)?\\s*\\n|\\n```\\s*$"), "")
        return SlideDeck.parse("# _\n\n---\n\n$text").drop(1).filter { it.title.isNotBlank() || it.body.isNotBlank() }.take(2).map { v ->
            val kept = v.copy(
                image = v.image.ifBlank { s.image }, imageAlt = v.imageAlt.ifBlank { s.imageAlt }, imageLeft = if (v.image.isBlank()) s.imageLeft else v.imageLeft,
                layout = if (v.image.isBlank() && s.image.isNotBlank() && !v.layout.withImage && v.layout == SlideDeck.Layout.CONTENT) SlideDeck.Layout.IMAGE_TEXT else v.layout,
                elements = s.elements, notes = v.notes.ifBlank { s.notes }, transition = s.transition, build = s.build,
            )
            io.github.thepytales.llmhub.docs.SlideLook.Suggestion(trLater("Vorschlag von Lora"), listOf(kept))
        }.filter { it.slides.single() != s }
    }

    /** Sprechertexte with the model for the slides [targets] of [slides] (index → text); the deck is the context. */
    suspend fun modelNotes(c: io.github.thepytales.llmhub.AppContainer, model: String, slides: List<SlideDeck.Slide>, targets: List<Int>): Map<Int, String> {
        val prompt = buildString {
            append("Schreibe Sprechertexte für die genannten Folien eines Vortrags: je 2 bis 4 Sätze in gesprochener Sprache, auf Deutsch, mit einer kurzen ")
            append("Überleitung zur nächsten Folie. Nichts erfinden, was nicht auf den Folien steht. Antworte nur mit einer Zeile je Folie im Format „Folie N: Text“.")
            LoraPrefs.instructions(c, ActionArea.SLIDES)?.let { append("\nVorlieben des Nutzers: ").append(it) }
            append("\n\nDie Präsentation:\n")
            slides.forEachIndexed { i, s -> append("Folie ${i + 1}: ").append(s.title).append(" – ").append(s.body.replace(Regex("\\s+"), " ").take(500)).append('\n') }
            append("\nSprechertexte für die Folien: ").append(targets.joinToString(", ") { (it + 1).toString() })
        }
        return Regex("(?m)^\\s*\\**Folie\\s+(\\d+)\\**\\s*[:.–-]\\s*(.+)$").findAll(ask(c, model, prompt))
            .mapNotNull { m -> m.groupValues[1].toIntOrNull()?.let { it - 1 }?.takeIf { it in targets }?.let { it to m.groupValues[2].trim() } }.toMap()
    }

    /** The structure of a paper (PDF) from its lines with font sizes; from its plain text when that fails. */
    fun paper(c: io.github.thepytales.llmhub.AppContainer, f: java.io.File): io.github.thepytales.llmhub.docs.PaperDeck.Paper {
        val lines = runCatching { io.github.thepytales.llmhub.docs.PaperDeck.lines(io.github.thepytales.llmhub.docs.PdfTools.textRuns(c.appContext, f).first) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: io.github.thepytales.llmhub.docs.PaperDeck.lines(io.github.thepytales.llmhub.docs.PdfExtras.pageTexts(c.appContext, f).joinToString("\u000C"))
        return io.github.thepytales.llmhub.docs.PaperDeck.read(lines, f.nameWithoutExtension)
    }

    /**
     * The figures of a paper as pictures next to the deck (Windows; Android reads no pictures out of PDFs): large ones
     * only, nothing that repeats on several pages (logos of the journal), at most [max].
     */
    fun figures(f: java.io.File, base: java.io.File, max: Int = 8): List<io.github.thepytales.llmhub.docs.PaperDeck.Figure> {
        if (!io.github.thepytales.llmhub.docs.PdfStage3.SUPPORTED) return emptyList()
        val all = runCatching { io.github.thepytales.llmhub.docs.PdfStage3.images(f) }.getOrDefault(emptyList())
        val repeated = all.groupBy { Triple(it.width, it.height, it.bytes) }.filter { (_, l) -> l.map { it.page }.distinct().size > 2 }.keys
        return all.filter { it.width >= 300 && it.height >= 180 && Triple(it.width, it.height, it.bytes) !in repeated }.distinctBy { Triple(it.page, it.width, it.height) }.take(max).mapNotNull { img ->
            runCatching { io.github.thepytales.llmhub.docs.PdfStage3.imagePreview(f, img.page, img.name, 1600) }.getOrNull()
                ?.let { b -> io.github.thepytales.llmhub.docs.PaperDeck.Figure(img.page, io.github.thepytales.llmhub.docs.DocMarkup.storeImage(base, b), img.width, img.height) }
        }
    }
}

/** Lora's actions for tables and presentations (AP-38 Stufe c). */
internal object OfficeActions {
    private fun sheetHost() = ActionHosts.sheet ?: throw ActionFailed(tr("Bitte eine Tabelle öffnen."))
    private fun slidesHost() = ActionHosts.slides ?: throw ActionFailed(tr("Bitte eine Präsentation öffnen."))

    /** Changes the open sheet: [change] gets rows and formats and returns the new ones (null = nothing to do). */
    private fun sheetChange(id: String, title: String, risky: Boolean, preview: List<Pair<String, String>> = emptyList(), result: String, change: (List<List<String>>, SheetMeta) -> Pair<List<List<String>>, SheetMeta>?): ActionPlan {
        val host = sheetHost()
        val before = host.markdown()
        val rows = SheetEngine.grid(before)
        val meta = SheetMeta.parse(before)
        val (next, nextMeta) = change(rows, meta) ?: throw ActionFailed(tr("Da gibt es nichts zu ändern."))
        return ActionPlan(id, title, risky, preview) {
            host.replace(SheetMeta.compose(next, nextMeta))
            ActionResult(result, undo = { host.replace(before); tr("Tabelle wie vorher.") })
        }
    }

    private fun slidesChange(id: String, title: String, risky: Boolean, preview: List<Pair<String, String>> = emptyList(), result: String, change: (List<SlideDeck.Slide>, Int) -> List<SlideDeck.Slide>?): ActionPlan {
        val host = slidesHost()
        val before = host.markdown()
        val slides = SlideDeck.parse(before)
        val next = change(slides, host.selected()) ?: throw ActionFailed(tr("Da gibt es nichts zu ändern."))
        return ActionPlan(id, title, risky, preview) {
            host.replace(SlideDeck.markdown(next))
            ActionResult(result, undo = { host.replace(before); tr("Folien wie vorher.") })
        }
    }

    val ALL: List<ToolAction> = listOf(
        // Tabellen
        ToolAction(
            "sheet.set_cells", ActionArea.SHEET, trLater("Zellen füllen"), "Schreibt Werte oder Formeln in Zellen der offenen Tabelle, z. B. A1=Name; B2==SUMME(B3:B9).",
            listOf(ActionParam("zellen", "Zelle=Wert, mit Semikolon getrennt; Formeln mit =")), risky = false,
        ) { _, a ->
            val cells = SheetLora.parseCells(a.s("zellen").orEmpty())
            if (cells.isEmpty()) throw ActionFailed(tr("Welche Zellen? Zum Beispiel A1=Name; B2=5"))
            sheetChange("sheet.set_cells", tr("Füllt {0} Zellen …", cells.size), false, result = tr("{0} Zellen gesetzt.", cells.size)) { rows, meta ->
                SheetLora.setCells(rows, cells) to meta
            }
        },
        ToolAction(
            "sheet.add_column", ActionArea.SHEET, trLater("Spalte mit Formel"),
            "Hängt rechts eine Spalte an und füllt jede Datenzeile mit einer Formel, {r} steht für die Zeilennummer (z. B. =B{r}*0,5+C{r}*0,5).",
            listOf(ActionParam("ueberschrift", "Spaltenüberschrift"), ActionParam("formel", "Formel mit {r} oder fester Wert")), risky = false,
        ) { _, a ->
            val header = a.s("ueberschrift")?.takeIf { it.isNotBlank() } ?: throw ActionFailed(tr("Wie heißt die neue Spalte?"))
            val formula = a.s("formel").orEmpty()
            sheetChange("sheet.add_column", tr("Fügt die Spalte „{0}“ an …", header), false, result = tr("Spalte „{0}“ angefügt.", header)) { rows, meta ->
                SheetLora.addColumn(rows, header, formula) to meta
            }
        },
        ToolAction(
            "sheet.sort", ActionArea.SHEET, trLater("Sortieren"), "Sortiert die Datenzeilen der offenen Tabelle nach einer Spalte (Kopfzeile bleibt oben).",
            listOf(ActionParam("spalte", "Buchstabe oder Überschrift der Spalte"), ActionParam("absteigend", "ja für absteigend", required = false)), risky = false,
        ) { _, a ->
            val desc = a.s("absteigend").orEmpty().lowercase().let { it.startsWith("j") || it == "true" || it.startsWith("ab") }
            val host = sheetHost()
            val rows = SheetEngine.grid(host.markdown())
            val col = SheetLora.column(rows, a.s("spalte").orEmpty()) ?: throw ActionFailed(tr("Die Spalte „{0}“ finde ich nicht.", a.s("spalte").orEmpty()))
            val name = rows.firstOrNull()?.getOrNull(col)?.ifBlank { null } ?: SheetEngine.colName(col)
            sheetChange("sheet.sort", tr("Sortiert nach „{0}“ …", name), false, result = tr("Nach „{0}“ sortiert.", name)) { r, meta ->
                val order = SheetLora.sortOrder(r, col, desc)
                order.map { r[it] } to meta.remapRows(order)
            }
        },
        ToolAction(
            "sheet.clean", ActionArea.SHEET, trLater("Daten bereinigen"), "Bereinigt die offene Tabelle: Leerzeichen, Datumsformate, leere und doppelte Zeilen.",
            emptyList(), risky = true,
        ) { _, _ ->
            val rows = SheetEngine.grid(sheetHost().markdown())
            val (_, notes) = SheetLora.clean(rows)
            if (notes.isEmpty()) throw ActionFailed(tr("Die Tabelle ist schon sauber."))
            sheetChange("sheet.clean", tr("Bereinigt die Tabelle …"), true, notes.map { tr("{0} Zeilen", rows.size) to it }, tr("Bereinigt: {0}.", notes.joinToString(", "))) { r, meta ->
                SheetLora.cleanKeep(r).let { (rows, _, kept) -> rows to meta.remapRows(kept) }
            }
        },
        ToolAction(
            "sheet.summary", ActionArea.SHEET, trLater("Summe und Mittelwert"), "Fügt unter den Zahlenspalten Summe und Mittelwert als Formeln an.",
            emptyList(), risky = false,
        ) { _, _ ->
            sheetChange("sheet.summary", tr("Rechnet Summe und Mittelwert …"), false, result = tr("Summe und Mittelwert stehen unten.")) { rows, meta ->
                SheetLora.summary(rows)?.let { it to meta }
            }
        },
        // Lora rechnet (AP-49): on the device, the results are formulas next to the data, with a chart.
        ToolAction(
            "sheet.distribution", ActionArea.SHEET, trLater("Verteilung berechnen"),
            "Zählt, wie oft jeder Wert einer Spalte vorkommt (z. B. Notenverteilung), mit Anteil und Säulendiagramm; rechts neben den Daten, als Formeln.",
            listOf(ActionParam("spalte", "Buchstabe oder Überschrift der Spalte (leer: die Spalte „Note“)", required = false)), risky = false,
        ) { _, a ->
            val rows = SheetEngine.grid(sheetHost().markdown())
            val col = SheetLora.column(rows, a.s("spalte").orEmpty().ifBlank { "Note" }) ?: SheetLora.column(rows, "Noten")
                ?: throw ActionFailed(tr("Welche Spalte soll ich auszählen?"))
            val block = SheetCalc.distribution(rows, col, SheetEngine.Sheet(rows)) ?: throw ActionFailed(tr("In der Spalte stehen keine Werte."))
            sheetChange("sheet.distribution", tr("Berechnet die Verteilung von „{0}“ …", rows[0].getOrNull(col).orEmpty()), false, result = tr("Verteilung steht ab {0}: {1}.", SheetEngine.ref(block.row, block.col), block.note)) { r, meta ->
                SheetCalc.apply(r, block) to block.meta(meta)
            }
        },
        ToolAction(
            "sheet.group", ActionArea.SHEET, trLater("Kennzahlen je Gruppe"),
            "Fasst eine Zahlenspalte je Gruppe zusammen (Anzahl, Mittelwert, Min, Max – wie eine Pivot-Tabelle), z. B. Mittelwert der Note je Gruppe; mit Diagramm.",
            listOf(ActionParam("gruppe", "Spalte mit den Gruppen"), ActionParam("wert", "Spalte mit den Zahlen")), risky = false,
        ) { _, a ->
            val rows = SheetEngine.grid(sheetHost().markdown())
            val g = SheetLora.column(rows, a.s("gruppe").orEmpty()) ?: throw ActionFailed(tr("Die Spalte „{0}“ finde ich nicht.", a.s("gruppe").orEmpty()))
            val v = SheetLora.column(rows, a.s("wert").orEmpty().ifBlank { "Note" }) ?: throw ActionFailed(tr("Die Spalte „{0}“ finde ich nicht.", a.s("wert").orEmpty()))
            val block = SheetCalc.byGroup(rows, g, v, SheetEngine.Sheet(rows)) ?: throw ActionFailed(tr("Dafür finde ich keine Gruppen."))
            sheetChange("sheet.group", tr("Rechnet je „{0}“ …", rows[0].getOrNull(g).orEmpty()), false, result = tr("Zusammenfassung steht ab {0}: {1}.", SheetEngine.ref(block.row, block.col), block.note)) { r, meta ->
                SheetCalc.apply(r, block) to block.meta(meta)
            }
        },
        ToolAction(
            "sheet.chart", ActionArea.SHEET, trLater("Diagramm anlegen"),
            "Legt ein Diagramm aus einem Bereich an (Säulen, Balken, Linie, Kreis, Punkt, Histogramm, Boxplot) im TUD-Design.",
            listOf(
                ActionParam("bereich", "Bereich, z. B. A1:B10 (erste Zeile Namen)"),
                ActionParam("typ", "saeulen, balken, linie, kreis, punkt, histogramm oder boxplot", required = false),
                ActionParam("titel", "Titel", required = false),
            ), risky = false,
        ) { _, a ->
            val range = a.s("bereich").orEmpty().trim().uppercase()
            if (SheetEngine.parseRange(range) == null) throw ActionFailed(tr("Welcher Bereich? Zum Beispiel A1:B10"))
            val type = when (a.s("typ").orEmpty().lowercase()) {
                "balken", "hbar" -> "hbar"; "linie", "line" -> "line"; "kreis", "pie", "torte" -> "pie"; "punkt", "streu", "scatter" -> "scatter"
                "histogramm", "histogram" -> "histogram"; "boxplot", "box" -> "box"; "gestapelt", "stacked" -> "stacked"; else -> "bar"
            }
            val chart = SheetChart(type, range, a.s("titel").orEmpty(), id = SheetCalc.newId())
            sheetChange("sheet.chart", tr("Legt ein Diagramm aus {0} an …", range), false, result = tr("Diagramm aus {0} angelegt.", range)) { rows, meta ->
                if (SheetTools.chartSpec(chart, SheetEngine.Sheet(rows)) == null) throw ActionFailed(tr("Im Bereich {0} stehen keine Zahlen für ein Diagramm", range))
                rows to meta.copy(charts = meta.charts + chart)
            }
        },
        ToolAction(
            "sheet.explain", ActionArea.SHEET, trLater("Formel erklären"),
            "Erklärt in Worten, was die Formel einer Zelle rechnet (Funktionen und Bezüge mit ihren Spaltennamen), auf dem Gerät.",
            listOf(ActionParam("zelle", "Zelle, z. B. D2 (leer: die erste Formel der markierten Zeile)", required = false)), risky = false,
        ) { _, a ->
            val host = sheetHost()
            val rows = SheetEngine.grid(host.markdown())
            val at = a.s("zelle")?.let { SheetEngine.parseRef(it.trim()) }
                ?: host.selected().let { r -> rows.getOrNull(r)?.indexOfFirst { it.trimStart().startsWith("=") }?.takeIf { it >= 0 }?.let { r to it } }
                ?: rows.indices.firstNotNullOfOrNull { r -> rows[r].indexOfFirst { it.trimStart().startsWith("=") }.takeIf { it >= 0 }?.let { r to it } }
                ?: throw ActionFailed(tr("In der Tabelle steht keine Formel."))
            val formula = rows.getOrNull(at.first)?.getOrNull(at.second).orEmpty()
            val text = SheetCalc.explain(formula, rows)
            ActionPlan("sheet.explain", tr("Erklärt {0} …", SheetEngine.ref(at.first, at.second)), false) {
                ActionResult(SheetEngine.ref(at.first, at.second) + " " + formula + "\n" + text)
            }
        },
        // Folien
        ToolAction(
            "slides.add", ActionArea.SLIDES, trLater("Folie anlegen"), "Legt nach der ausgewählten Folie eine neue Folie mit Titel und Stichpunkten an.",
            listOf(
                ActionParam("titel", "Titel der Folie"), ActionParam("inhalt", "Stichpunkte, je Zeile oder mit Semikolon", required = false),
                ActionParam("layout", "inhalt, abschnitt, zwei spalten, zitat, abschluss (Standard inhalt)", required = false),
            ), risky = false,
        ) { _, a ->
            val title = a.s("titel")?.takeIf { it.isNotBlank() } ?: throw ActionFailed(tr("Welchen Titel soll die Folie bekommen?"))
            val layout = when (a.s("layout")?.lowercase().orEmpty()) {
                "abschnitt", "section" -> SlideDeck.Layout.SECTION
                "zwei spalten", "spalten" -> SlideDeck.Layout.TWO_COLUMNS
                "zitat" -> SlideDeck.Layout.QUOTE
                "abschluss", "ende" -> SlideDeck.Layout.END
                else -> SlideDeck.Layout.CONTENT
            }
            slidesChange("slides.add", tr("Legt die Folie „{0}“ an …", title), false, result = tr("Folie „{0}“ angelegt.", title)) { slides, sel ->
                slides.toMutableList().apply { add((sel + 1).coerceIn(1, size), SlideDeck.Slide(title, SlidesLora.bullets(a.s("inhalt").orEmpty()), layout = layout)) }
            }
        },
        ToolAction(
            "slides.edit", ActionArea.SLIDES, trLater("Folie ändern"), "Ändert Titel, Inhalt oder Sprechernotizen einer Folie (Nummer oder Titel; leer: die ausgewählte).",
            listOf(
                ActionParam("folie", "Nummer oder Titel (leer: die ausgewählte)", required = false), ActionParam("titel", "Neuer Titel", required = false),
                ActionParam("inhalt", "Neue Stichpunkte", required = false), ActionParam("notizen", "Sprechernotizen", required = false),
            ), risky = false,
        ) { _, a ->
            val host = slidesHost()
            val slides = SlideDeck.parse(host.markdown())
            val i = SlidesLora.index(slides, a.s("folie"), host.selected()) ?: throw ActionFailed(tr("Diese Folie finde ich nicht."))
            slidesChange("slides.edit", tr("Ändert Folie {0} …", i + 1), false, result = tr("Folie {0} geändert.", i + 1)) { list, _ ->
                list.toMutableList().apply {
                    val s = get(i)
                    set(i, s.copy(
                        title = a.s("titel")?.takeIf { it.isNotBlank() } ?: s.title,
                        body = a.s("inhalt")?.takeIf { it.isNotBlank() }?.let(SlidesLora::bullets) ?: s.body,
                        notes = a.s("notizen")?.takeIf { it.isNotBlank() } ?: s.notes,
                    ))
                }
            }
        },
        ToolAction(
            "slides.delete", ActionArea.SLIDES, trLater("Folie löschen"), "Löscht eine Folie (Nummer oder Titel).",
            listOf(ActionParam("folie", "Nummer oder Titel (leer: die ausgewählte)", required = false)), risky = true,
        ) { _, a ->
            val host = slidesHost()
            val slides = SlideDeck.parse(host.markdown())
            val i = SlidesLora.index(slides, a.s("folie"), host.selected())?.takeIf { slides.size > 1 } ?: throw ActionFailed(tr("Diese Folie finde ich nicht."))
            slidesChange("slides.delete", tr("Löscht Folie {0} …", i + 1), true, listOf(tr("Folie {0}: {1}", i + 1, slides[i].title.ifBlank { "–" }) to tr("gelöscht")), tr("Folie {0} gelöscht.", i + 1)) { list, _ ->
                list.toMutableList().apply { removeAt(i) }
            }
        },
        ToolAction(
            "slides.from_outline", ActionArea.SLIDES, trLater("Folien aus Gliederung"),
            "Legt aus einer Gliederung mehrere Folien am Ende an: Zeilen mit # oder „Titel:“ beginnen eine Folie, die Zeilen darunter sind Stichpunkte.",
            listOf(ActionParam("gliederung", "Die Gliederung, eine Zeile je Punkt")), risky = false,
        ) { _, a ->
            val made = SlidesLora.fromOutline(a.s("gliederung").orEmpty())
            if (made.isEmpty()) throw ActionFailed(tr("Aus der Gliederung entstehen keine Folien – bitte Titel mit # beginnen."))
            slidesChange("slides.from_outline", tr("Legt {0} Folien an …", made.size), false, result = tr("{0} Folien angelegt.", made.size)) { list, _ -> list + made }
        },
        // Lora baut Folien (AP-51): a whole deck in the TUD design from a document, on the device.
        ToolAction(
            "slides.from_document", ActionArea.DOC, trLater("Präsentation aus Dokument"),
            "Baut aus einem Dokument (Titel; leer: das zuletzt bearbeitete) eine Präsentation im TUD-Design: Abschnitte, Inhaltsfolien, Tabellen, Bilder, Diagramme, lange Absätze als Sprechernotizen; Punkte erscheinen nacheinander.",
            listOf(ActionParam("dokument", "Titel des Dokuments (leer: das zuletzt bearbeitete)", required = false)), risky = false,
        ) { c, a ->
            val name = a.s("dokument").orEmpty().trim()
            val docs = c.docs.items.value.filter { it.docKind == io.github.thepytales.llmhub.docs.DocKind.DOC }
            val doc = (if (name.isEmpty()) docs.maxByOrNull { it.updatedAt } else docs.firstOrNull { it.title.equals(name, true) } ?: docs.firstOrNull { it.title.contains(name, true) })
                ?: throw ActionFailed(tr("Das Dokument „{0}“ finde ich nicht.", name))
            val slides = SlideDeck.fromDocument(doc.markdown(), doc.title).mapIndexed { i, sl -> if (i > 0 && sl.layout == SlideDeck.Layout.CONTENT) sl.copy(build = true, transition = "fade") else sl }
            ActionPlan("slides.from_document", tr("Baut Folien aus „{0}“ …", doc.title), false, listOf(tr("Folien") to slides.size.toString())) {
                val deck = io.github.thepytales.llmhub.docs.LiveDoc.create(doc.title, SlideDeck.markdown(slides), "Lora", kind = io.github.thepytales.llmhub.docs.DocKind.SLIDES).copy(vault = doc.vault)
                c.docs.save(deck)
                io.github.thepytales.llmhub.ui.DocsHandoff.show(deck.id)
                ActionResult(tr("Präsentation „{0}“ mit {1} Folien angelegt.", doc.title, slides.size), undo = { c.docs.delete(deck.id); tr("Präsentation entfernt.") })
            }
        },
        ToolAction(
            "slides.animate", ActionArea.SLIDES, trLater("Animationen"),
            "Setzt Übergänge und lässt Stichpunkte nacheinander erscheinen – für alle Inhaltsfolien oder eine (Nummer/Titel).",
            listOf(
                ActionParam("uebergang", "ueberblenden, schieben oder keiner", required = false),
                ActionParam("nacheinander", "ja/nein: Stichpunkte nacheinander", required = false),
                ActionParam("folie", "Nummer oder Titel (leer: alle)", required = false),
            ), risky = false,
        ) { _, a ->
            val t = when (a.s("uebergang").orEmpty().lowercase()) { "schieben", "push" -> "push"; "keiner", "kein", "aus", "none" -> ""; else -> "fade" }
            val build = !a.s("nacheinander").orEmpty().lowercase().let { it.startsWith("n") || it == "false" }
            val host = slidesHost()
            val slides = SlideDeck.parse(host.markdown())
            val only = a.s("folie")?.takeIf { it.isNotBlank() }?.let { SlidesLora.index(slides, it, host.selected()) ?: throw ActionFailed(tr("Diese Folie finde ich nicht.")) }
            slidesChange("slides.animate", tr("Setzt Animationen …"), false, result = tr("Animationen gesetzt.")) { list, _ ->
                list.mapIndexed { i, sl ->
                    if ((only == null && i > 0) || i == only) sl.copy(transition = t, build = build && sl.body.lines().any { it.trimStart().startsWith("- ") }) else sl
                }
            }
        },
        ToolAction(
            "slides.split_full", ActionArea.SLIDES, trLater("Volle Folien teilen"), "Teilt Folien mit mehr als sechs Stichpunkten auf.",
            emptyList(), risky = false,
        ) { _, _ ->
            slidesChange("slides.split_full", tr("Teilt volle Folien …"), false, result = tr("Volle Folien geteilt.")) { list, _ ->
                SlideDeck.splitFull(list).let { (next, n) -> next.takeIf { n > 0 } }
            }
        },
        // Lora baut Folien aus einem Paper (AP-51): structure, figures and tables of a PDF, on the device.
        ToolAction(
            "slides.from_paper", ActionArea.PDF, trLater("Präsentation aus Paper"),
            "Baut aus einem Paper (PDF) eine Präsentation im TUD-Design: Titel und Autor:innen, Worum es geht (Abstract), Gliederung, je Abschnitt (Einleitung, Methode, Ergebnisse, Diskussion, Fazit) eine Folie mit den Kernaussagen, Abbildungen und Tabellen des Papers, Sprechernotizen; Punkte erscheinen nacheinander. Auf dem Gerät.",
            listOf(ActionParam("datei", "Das Paper als PDF (leer: das ausgewählte bzw. offene)", required = false)), risky = false,
        ) { c, a ->
            val f = ToolActions.files(c, a.s("datei"), "pdf").firstOrNull() ?: throw ActionFailed(tr("Aus welchem Paper? Bitte ein PDF auswählen oder den Namen nennen."))
            val paper = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SlidesLora.paper(c, f) }
            if (paper.sections.isEmpty() && paper.abstract.isBlank()) throw ActionFailed(tr("In „{0}“ finde ich keinen lesbaren Text – ist das PDF gescannt? Dann zuerst Texterkennung (OCR).", f.name))
            val estimate = io.github.thepytales.llmhub.docs.PaperDeck.slides(paper).size
            ActionPlan("slides.from_paper", tr("Baut Folien aus „{0}“ …", f.name), false, listOf(tr("Abschnitte") to paper.sections.size.toString(), tr("Folien") to estimate.toString())) {
                val figures = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SlidesLora.figures(f, c.docs.baseDir(null)) }
                val slides = io.github.thepytales.llmhub.docs.PaperDeck.slides(paper, figures)
                val title = paper.title.ifBlank { f.nameWithoutExtension }
                val deck = io.github.thepytales.llmhub.docs.LiveDoc.create(title, SlideDeck.markdown(slides), "Lora", kind = io.github.thepytales.llmhub.docs.DocKind.SLIDES)
                c.docs.save(deck)
                io.github.thepytales.llmhub.ui.DocsHandoff.show(deck.id)
                ActionResult(
                    tr("Präsentation „{0}“ mit {1} Folien angelegt ({2} Abbildungen) – Vorführen mit Umschalt+F5.", title, slides.size, slides.count { it.layout == SlideDeck.Layout.IMAGE }),
                    undo = { c.docs.delete(deck.id); tr("Präsentation entfernt.") },
                )
            }
        },
        // „Folie schöner machen“ (AP-51): suggestions to tap in the editor; asked by name, Lora takes one of them.
        ToolAction(
            "slides.beautify", ActionArea.SLIDES, trLater("Folie schöner machen"),
            "Macht Vorschläge für ein schöneres Layout einer Folie (zwei Spalten, Vergleich, Tabelle, Diagramm, kürzere Punkte, Bild groß …, mit Modell auch eigene Varianten) zum Antippen im Editor; mit „vorschlag“ wird dieser Vorschlag übernommen.",
            listOf(
                ActionParam("folie", "Nummer oder Titel (leer: die ausgewählte)", required = false),
                ActionParam("vorschlag", "Nummer des Vorschlags, der übernommen wird (leer: Vorschläge zeigen)", required = false),
            ), risky = false, usesModel = true,
        ) { c, a ->
            val host = slidesHost()
            val before = host.markdown()
            val slides = SlideDeck.parse(before)
            val i = SlidesLora.index(slides, a.s("folie"), host.selected()) ?: throw ActionFailed(tr("Diese Folie finde ich nicht."))
            if (i == 0) throw ActionFailed(tr("Die Titelfolie bleibt im TUD-Design – welche andere Folie?"))
            val pick = a.s("vorschlag")?.filter { it.isDigit() }?.toIntOrNull()
            val open = io.github.thepytales.llmhub.ui.SlideSuggestionsHost.open
            if (pick == null && open != null) return@ToolAction ActionPlan("slides.beautify", tr("Sucht Vorschläge für Folie {0} …", i + 1), false) {
                open(i)
                ActionResult(tr("Vorschläge für Folie {0} stehen im Editor zum Antippen bereit.", i + 1))
            }
            ActionPlan("slides.beautify", tr("Macht Folie {0} schöner …", i + 1), false) {
                val model = ToolActions.model(c)
                val all = (model?.let { m -> runCatching { SlidesLora.modelSuggestions(c, m, slides[i]) }.getOrNull() }.orEmpty()) + io.github.thepytales.llmhub.docs.SlideLook.suggest(slides[i])
                if (all.isEmpty()) throw ActionFailed(tr("Für Folie {0} habe ich keinen Vorschlag – sie sieht schon gut aus.", i + 1))
                val k = ((pick ?: 1) - 1).coerceIn(0, all.lastIndex)
                host.replace(SlideDeck.markdown(slides.toMutableList().apply { removeAt(i); addAll(i, all[k].slides) }))
                val others = all.indices.filter { it != k }.joinToString(", ") { "${it + 1}) ${tr(all[it].label)}" }
                ActionResult(
                    tr("Folie {0}: {1}.", i + 1, tr(all[k].label)) + if (others.isNotEmpty()) " " + tr("Weitere Vorschläge: {0}.", others) else "",
                    undo = { host.replace(before); tr("Folien wie vorher.") },
                )
            }
        },
        // Sprechertexte (AP-51): with the model in spoken language, without one from the points of the slide.
        ToolAction(
            "slides.notes", ActionArea.SLIDES, trLater("Sprechertexte schreiben"),
            "Schreibt Sprechertexte (Notizen) für eine Folie oder alle Folien ohne Notizen – mit Modell in gesprochener Sprache, sonst aus den Stichpunkten.",
            listOf(
                ActionParam("folie", "Nummer oder Titel (leer: alle Folien ohne Notizen)", required = false),
                ActionParam("alle", "ja: auch Folien, die schon Notizen haben, neu schreiben", required = false),
            ), risky = false, usesModel = true,
        ) { c, a ->
            val host = slidesHost()
            val before = host.markdown()
            val slides = SlideDeck.parse(before)
            val one = a.s("folie")?.takeIf { it.isNotBlank() }?.let { SlidesLora.index(slides, it, host.selected()) ?: throw ActionFailed(tr("Diese Folie finde ich nicht.")) }
            val again = a.s("alle").orEmpty().lowercase().let { it.startsWith("j") || it == "true" }
            val targets = one?.let { listOf(it) } ?: slides.indices.filter { again || slides[it].notes.isBlank() }
            if (targets.isEmpty()) throw ActionFailed(tr("Alle Folien haben schon Notizen – mit „alle“ schreibe ich sie neu."))
            val model = ToolActions.model(c)
            ActionPlan("slides.notes", tr("Schreibt Sprechertexte für {0} Folien …", targets.size), false, listOf(tr("Folien") to targets.joinToString(", ") { (it + 1).toString() }, tr("Datenschutz") to if (model != null) ToolActions.privacyNote(c) else tr("Bleibt auf diesem Gerät."))) {
                val written = model?.let { m -> runCatching { SlidesLora.modelNotes(c, m, slides, targets) }.getOrNull() }.orEmpty()
                val next = slides.mapIndexed { k, s -> if (k in targets) s.copy(notes = written[k]?.takeIf { it.isNotBlank() } ?: io.github.thepytales.llmhub.docs.SlideLook.notes(slides, k)) else s }
                host.replace(SlideDeck.markdown(next))
                val byModel = targets.count { !written[it].isNullOrBlank() }
                ActionResult(
                    if (byModel > 0) tr("Sprechertexte für {0} Folien geschrieben.", targets.size) else tr("Sprechertexte für {0} Folien aus den Stichpunkten geschrieben (ohne Modell).", targets.size),
                    undo = { host.replace(before); tr("Folien wie vorher.") },
                )
            }
        },
    )
}
