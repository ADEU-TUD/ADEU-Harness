package io.github.thepytales.llmhub.docs
import io.github.thepytales.llmhub.ui.trLater

/**
 * Slides of the presentation editor, stored as the Markdown [PptxWriter] reads: slides separated by "---", the
 * first heading is the title, "|||" starts the right column and "Notizen:" the speaker notes. Layouts that cannot
 * be told from the content carry a line `<!-- layout: bild-text -->` (bild-links, bild, vergleich, tabelle,
 * diagramm, agenda, abschluss); a picture is a line `![Beschreibung](Bilder/foto.png)`.
 */
object SlideDeck {
    enum class Layout(val label: String, val key: String = "") {
        TITLE(trLater("Titelfolie")), SECTION(trLater("Abschnitt")), CONTENT(trLater("Inhalt")), TWO_COLUMNS(trLater("Zwei Spalten")), QUOTE(trLater("Zitat")),
        IMAGE_TEXT(trLater("Bild + Text"), "bild-text"), IMAGE(trLater("Vollbild-Bild"), "bild"), COMPARISON(trLater("Vergleich"), "vergleich"),
        TABLE(trLater("Tabelle"), "tabelle"), CHART(trLater("Diagramm"), "diagramm"), AGENDA("Agenda", "agenda"), END(trLater("Abschluss"), "abschluss"),
        /** Frei gestalten (AP-16): only the title (optional) and freely placed elements. */
        FREE(trLater("Frei"), "frei"),
        ;

        /** Whether the layout shows the picture of the slide. */
        val withImage: Boolean get() = this == IMAGE_TEXT || this == IMAGE
    }

    data class Slide(
        val title: String = "",
        /** Subtitle (title slide) or content in Markdown. */
        val body: String = "",
        /** Right column (two columns, comparison). */
        val right: String = "",
        val notes: String = "",
        val layout: Layout = Layout.CONTENT,
        /** Picture (path relative to the document, absolute or data URL) and its description. */
        val image: String = "",
        val imageAlt: String = "",
        /** Picture on the left of the text (Bild + Text); otherwise on the right. */
        val imageLeft: Boolean = false,
        /** Freely placed text boxes, pictures and shapes (AP-16), drawn over the layout. */
        val elements: List<SlideElement> = emptyList(),
        /** Masterfolie of the deck (kept on the first slide). */
        val master: SlideMaster? = null,
        /** Übergang to this slide (AP-51): "" none, "fade" Überblenden, "push" Schieben. */
        val transition: String = "",
        /** Stichpunkte nacheinander einblenden (one top-level point per click). */
        val build: Boolean = false,
    )

    private val ELEMENT = Regex("^\\s*<!--\\s*element\\s*:\\s*(\\{.*\\})\\s*-->\\s*$")
    private val MASTER_LINE = Regex("^\\s*<!--\\s*master\\s*:\\s*(\\{.*\\})\\s*-->\\s*$")
    private val ANIMATION = Regex("^\\s*<!--\\s*animation\\s*:\\s*(\\{.*\\})\\s*-->\\s*$")

    /** Übergang and Aufbau of a slide (AP-51), kept as a comment line of the slide. */
    @kotlinx.serialization.Serializable
    data class Animation(val transition: String = "", val build: Boolean = false)

    /** The animation line of a slide's Markdown (none: no transition, no build). */
    fun animationOf(text: String): Animation = text.lineSequence().firstNotNullOfOrNull { l ->
        ANIMATION.find(l)?.groupValues?.get(1)?.let { runCatching { json.decodeFromString(Animation.serializer(), it) }.getOrNull() }
    } ?: Animation()

    /** How many clicks the slide's points need when [Slide.build] is on (its top-level list items). */
    fun buildCount(s: Slide): Int = if (!s.build) 0 else s.body.lines().count { TOP_ITEM.containsMatchIn(it) }

    /** The slide as shown after [step] clicks of its build: the text before the first point and the first [step] points. */
    fun buildStep(s: Slide, step: Int): Slide {
        if (!s.build || step >= buildCount(s)) return s
        val out = mutableListOf<String>()
        var seen = 0
        for (l in s.body.lines()) {
            if (TOP_ITEM.containsMatchIn(l)) { seen++; if (seen > step) break }
            out += l
        }
        return s.copy(body = out.joinToString("\n").trimEnd())
    }

    private val TOP_ITEM = Regex("^([-*+]|\\d+[.)])\\s")
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Whether [line] is an element or master line of free design (left out of the slide's text). */
    fun isDesignLine(line: String) = ELEMENT.matches(line) || MASTER_LINE.matches(line) || ANIMATION.matches(line)

    /** The elements of a slide's Markdown. */
    fun elementsOf(text: String): List<SlideElement> = text.lines().mapNotNull { l ->
        ELEMENT.find(l)?.groupValues?.get(1)?.let { runCatching { json.decodeFromString(SlideElement.serializer(), it) }.getOrNull() }
    }

    /** The Masterfolie of a deck's Markdown, if one is set. */
    fun masterOf(markdown: String): SlideMaster? = markdown.lineSequence().firstNotNullOfOrNull { l ->
        MASTER_LINE.find(l)?.groupValues?.get(1)?.let { runCatching { json.decodeFromString(SlideMaster.serializer(), it) }.getOrNull() }
    }

    private fun safeJson(s: String) = s.replace("-->", "--\\u003e")

    internal fun elementLine(e: SlideElement) = "<!-- element: " + safeJson(json.encodeToString(SlideElement.serializer(), e)) + " -->"

    internal fun masterLine(m: SlideMaster) = "<!-- master: " + safeJson(json.encodeToString(SlideMaster.serializer(), m)) + " -->"

    private val SEPARATOR = Regex("^\\s*-{3,}\\s*$")
    private val NOTES = Regex("^\\s*(Notizen|Notiz|Sprechernotizen|Sprechertext|Notes)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val COLUMNS = Regex("^\\s*(\\|\\|\\||::: *rechts|:::\\s*right)\\s*$", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("^#{1,6}\\s+")
    private val LAYOUT = Regex("^\\s*<!--\\s*layout\\s*:\\s*([a-zäöü-]+)\\s*-->\\s*$", RegexOption.IGNORE_CASE)

    /** Layout named in a marker line (and whether the picture goes left). */
    internal fun layoutOf(name: String): Pair<Layout, Boolean>? = when (name.lowercase()) {
        "bild-text", "bild-rechts", "bildtext", "image-text" -> Layout.IMAGE_TEXT to false
        "bild-links" -> Layout.IMAGE_TEXT to true
        "bild", "vollbild", "image" -> Layout.IMAGE to false
        "vergleich", "comparison" -> Layout.COMPARISON to false
        "tabelle", "table" -> Layout.TABLE to false
        "diagramm", "chart" -> Layout.CHART to false
        "agenda", "gliederung" -> Layout.AGENDA to false
        "abschluss", "ende", "danke", "end" -> Layout.END to false
        "frei", "free", "blank" -> Layout.FREE to false
        else -> null
    }

    /** The layout marker line of a slide, or null for layouts recognized from their content. */
    internal fun marker(layout: Layout, imageLeft: Boolean): String? = when (layout) {
        Layout.IMAGE_TEXT -> if (imageLeft) "<!-- layout: bild-links -->" else "<!-- layout: bild-text -->"
        Layout.IMAGE, Layout.COMPARISON, Layout.TABLE, Layout.CHART, Layout.AGENDA, Layout.END, Layout.FREE -> "<!-- layout: ${layout.key} -->"
        else -> null
    }

    fun parse(markdown: String): List<Slide> {
        val chunks = mutableListOf(StringBuilder())
        var inCode = false
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (!inCode && SEPARATOR.matches(line)) chunks += StringBuilder() else chunks.last().append(line).append('\n')
        }
        return chunks.map { it.toString().trim() }.filter { it.isNotEmpty() }.mapIndexed { i, t -> slide(t, i == 0) }
    }

    /** The Markdown of each slide (between the "---" lines), unchanged. */
    fun split(markdown: String): List<String> {
        val chunks = mutableListOf(StringBuilder())
        var inCode = false
        for (line in markdown.replace("\r\n", "\n").lines()) {
            if (line.trimStart().startsWith("```")) inCode = !inCode
            if (!inCode && SEPARATOR.matches(line)) chunks += StringBuilder() else chunks.last().append(line).append('\n')
        }
        return chunks.map { it.toString().trim() }.filter { it.isNotEmpty() }
    }

    private fun slide(text: String, first: Boolean): Slide {
        val elements = elementsOf(text)
        val master = if (first) masterOf(text) else null
        val anim = animationOf(text)
        return slideText(text.lines().filterNot { isDesignLine(it) }.joinToString("\n"), first).copy(elements = elements, master = master, transition = anim.transition, build = anim.build)
    }

    private fun slideText(text: String, first: Boolean): Slide {
        var lines = text.lines()
        // Layout marker and the (first) picture line.
        val marked = lines.firstNotNullOfOrNull { l -> LAYOUT.find(l)?.groupValues?.get(1)?.let(::layoutOf) }
        lines = lines.filterNot { LAYOUT.matches(it) }
        val imageAt = lines.indexOfFirst { DocMarkup.image(it) != null }
        val img = if (imageAt >= 0) DocMarkup.image(lines[imageAt]) else null
        if (imageAt >= 0) lines = lines.filterIndexed { i, _ -> i != imageAt }
        val notesAt = lines.indexOfFirst { NOTES.containsMatchIn(it) }
        val notes = if (notesAt < 0) "" else (listOf(lines[notesAt].replace(NOTES, "")) + lines.drop(notesAt + 1)).joinToString("\n").trim()
        val main = if (notesAt < 0) lines else lines.take(notesAt)
        val split = main.indexOfFirst { COLUMNS.matches(it) }
        val left = (if (split < 0) main else main.take(split)).toMutableList()
        val right = if (split < 0) "" else main.drop(split + 1).joinToString("\n").trim()
        val headAt = left.indexOfFirst { HEADING.containsMatchIn(it) }
        val title = if (headAt < 0) "" else left.removeAt(headAt).replace(HEADING, "").trim()
        var body = left.joinToString("\n").trim()
        val image = img?.src.orEmpty()
        val alt = img?.alt.orEmpty()
        if (first) {
            body = body.lines().joinToString("\n") { it.replace(HEADING, "") }.trim()
            return Slide(title, body, notes = notes, layout = Layout.TITLE, image = image, imageAlt = alt)
        }
        val layout = when {
            marked != null -> marked.first
            img != null -> if (body.isBlank() && right.isBlank()) Layout.IMAGE else Layout.IMAGE_TEXT
            right.isNotBlank() -> Layout.TWO_COLUMNS
            body.lines().all { it.isBlank() || it.trimStart().startsWith(">") } && body.isNotBlank() -> Layout.QUOTE
            body.isBlank() || body.length <= 140 && '\n' !in body && !body.trimStart().startsWith("-") && !body.trimStart().startsWith("|") -> Layout.SECTION
            else -> Layout.CONTENT
        }
        return Slide(title, body, right, notes, layout, image, alt, imageLeft = marked?.second == true)
    }

    fun markdown(slides: List<Slide>): String = slides.mapIndexed { i, s -> slideMarkdown(s, i == 0) }.joinToString("\n\n---\n\n") + "\n"

    private fun slideMarkdown(s: Slide, first: Boolean): String = buildString {
        if (first || s.layout == Layout.TITLE) {
            append("# ").append(s.title.ifBlank { "Titel" })
            // First line: subtitle; further lines (speaker, occasion, date) stay lines of their own below it.
            val lines = s.body.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) append("\n\n## ").append(lines.first())
            if (lines.size > 1) append("\n\n").append(lines.drop(1).joinToString("\n\n"))
            if (s.image.isNotBlank()) append("\n\n").append(DocMarkup.Image(s.imageAlt, s.image).markdown())
        } else {
            append("## ").append(s.title.ifBlank { if (s.layout == Layout.AGENDA) "Agenda" else "Folie" })
            marker(s.layout, s.imageLeft)?.let { append("\n\n").append(it) }
            if (s.image.isNotBlank()) append("\n\n").append(DocMarkup.Image(s.imageAlt, s.image).markdown())
            val body = when (s.layout) {
                Layout.QUOTE -> s.body.lines().filter { it.isNotBlank() }.joinToString("\n") { if (it.trimStart().startsWith(">")) it else "> $it" }
                else -> s.body.trim()
            }
            if (body.isNotBlank()) append("\n\n").append(body)
            if (s.layout == Layout.TWO_COLUMNS || s.layout == Layout.COMPARISON) append("\n\n|||\n\n").append(s.right.trim().ifBlank { "-" })
        }
        if (first) s.master?.let { append("\n\n").append(masterLine(it)) }
        s.elements.forEach { append("\n\n").append(elementLine(it)) }
        if (s.transition.isNotBlank() || s.build) append("\n\n<!-- animation: ").append(safeJson(json.encodeToString(Animation.serializer(), Animation(s.transition, s.build)))).append(" -->")
        if (s.notes.isNotBlank()) append("\n\nNotizen: ").append(s.notes.trim())
    }

    /** A new presentation: title slide and one content slide. */
    fun blank(title: String): String = markdown(
        listOf(
            Slide(title, "Untertitel", layout = Layout.TITLE),
            Slide("Erste Folie", "- Erster Punkt\n- Zweiter Punkt", layout = Layout.CONTENT),
        ),
    )

    /** Template for a new slide of [layout]. */
    fun template(layout: Layout): Slide = when (layout) {
        Layout.TITLE -> Slide("Titel", "Untertitel", layout = layout)
        Layout.SECTION -> Slide("Neuer Abschnitt", "", layout = layout)
        Layout.CONTENT -> Slide("Neue Folie", "- Punkt 1\n- Punkt 2\n- Punkt 3", layout = layout)
        Layout.TWO_COLUMNS -> Slide("Vergleich", "- Links", "- Rechts", layout = layout)
        Layout.QUOTE -> Slide("Zitat", "> „Ein treffendes Zitat.“ – Quelle", layout = layout)
        Layout.IMAGE_TEXT -> Slide("Bild und Text", "- Was das Bild zeigt\n- Worauf es ankommt", layout = layout)
        Layout.IMAGE -> Slide("Bild", "", layout = layout)
        Layout.COMPARISON -> Slide("Vergleich", "### Vorher\n\n- Punkt", "### Nachher\n\n- Punkt", layout = layout)
        Layout.TABLE -> Slide("Tabelle", "| Spalte 1 | Spalte 2 | Spalte 3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |", layout = layout)
        Layout.CHART -> Slide(
            "Diagramm",
            "```chart\n{\"type\": \"bar\", \"title\": \"\", \"labels\": [\"A\", \"B\", \"C\"], \"series\": [{\"name\": \"Werte\", \"values\": [3, 5, 2]}]}\n```",
            layout = layout,
        )
        Layout.AGENDA -> Slide("Agenda", "", layout = layout)
        Layout.END -> Slide("Vielen Dank!", "Fragen und Anmerkungen gern an:\nName · E-Mail", layout = layout)
        Layout.FREE -> Slide("", "", layout = layout, elements = listOf(SlideElement(kind = "text", x = 3.0, y = 3.0, w = 20.0, h = 3.0, text = "Textfeld", size = 32, bold = true)))
    }

    /** Heading of a column ("### Vorher" in the first line) and the rest of it. */
    fun columnHead(text: String): Pair<String, String> {
        val lines = text.trim().lines()
        val first = lines.firstOrNull().orEmpty()
        return if (HEADING.containsMatchIn(first)) first.replace(HEADING, "").trim() to lines.drop(1).joinToString("\n").trim() else "" to text.trim()
    }

    fun withColumnHead(head: String, rest: String): String = if (head.isBlank()) rest.trim() else "### ${head.trim()}\n\n${rest.trim()}"

    /**
     * Items of an agenda: the titles of the section slides, or without sections the titles of the content slides
     * (an agenda written into the slide itself wins).
     */
    fun agenda(slides: List<Slide>, at: Int): List<String> {
        val own = slides.getOrNull(at)?.body?.lines()?.map { it.trim().removePrefix("- ").removePrefix("* ").replace(Regex("^\\d+[.)]\\s+"), "").trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (own.isNotEmpty()) return own
        val rest = slides.drop(1).filter { it.layout != Layout.AGENDA && it.layout != Layout.END && it.title.isNotBlank() }
        val sections = rest.filter { it.layout == Layout.SECTION }
        return (sections.ifEmpty { rest }).map { it.title }.distinct().take(10)
    }

    /** The ```chart block of a slide, if it has one. */
    fun chartOf(body: String): ChartSpec? = Regex("```chart\\s*\\n([\\s\\S]*?)\\n?```").find(body)?.let { ChartSpec.parse(it.groupValues[1]) }

    private val ITEM = Regex("^([-*+]|\\d+[.)])\\s+")

    /**
     * Splits slides with more than [max] top-level bullet points into several ("Titel (1/2)", "Titel (2/2)"); sub-points
     * stay with their point, text above the list stays on the first slide, notes as well. Returns the slides and how
     * many were split.
     */
    fun splitFull(slides: List<Slide>, max: Int = 6): Pair<List<Slide>, Int> {
        var split = 0
        val out = slides.flatMapIndexed { i, s ->
            if (i == 0 || s.layout != Layout.CONTENT) return@flatMapIndexed listOf(s)
            val lines = s.body.lines()
            val firstItem = lines.indexOfFirst { ITEM.containsMatchIn(it) }
            if (firstItem < 0) return@flatMapIndexed listOf(s)
            val intro = lines.take(firstItem).joinToString("\n").trim()
            // Points with their sub-points (indented lines).
            val groups = mutableListOf<MutableList<String>>()
            for (l in lines.drop(firstItem)) {
                if (l.isBlank()) continue
                if (ITEM.containsMatchIn(l) || groups.isEmpty()) groups += mutableListOf(l) else groups.last() += l
            }
            if (groups.size <= max) return@flatMapIndexed listOf(s)
            split++
            val parts = (groups.size + max - 1) / max
            val per = (groups.size + parts - 1) / parts
            val title = s.title.replace(Regex("\\s*\\(\\d+/\\d+\\)$"), "")
            groups.chunked(per).mapIndexed { k, chunk ->
                val body = (if (k == 0 && intro.isNotEmpty()) "$intro\n\n" else "") + chunk.flatten().joinToString("\n")
                s.copy(title = "$title (${k + 1}/$parts)", body = body, notes = if (k == 0) s.notes else "")
            }
        }
        return out to split
    }

    /**
     * A first presentation from a text document, without the model: the title becomes the title slide, "##" headings
     * section or content slides, lists bullet points (long ones are split), paragraphs one point per sentence,
     * tables table slides, pictures picture slides; long text goes into the speaker notes.
     */
    fun fromDocument(markdown: String, title: String): List<Slide> {
        val blocks = DocBlocks.split(DocMarkup.footnotes(markdown).body).filterNot { DocMarkup.isComment(it) || DocMarkup.isPageBreak(it) }
        val docTitle = blocks.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: title
        val slides = mutableListOf(Slide(docTitle.ifBlank { "Titel" }, DocMarkup.today(), layout = Layout.TITLE))
        var heading = ""
        /** Whether a slide already shows the current heading (otherwise it gets a section slide). */
        var shown = true
        val points = mutableListOf<String>()
        val notes = StringBuilder()
        fun add(slide: Slide) {
            slides += slide
            shown = true
        }
        fun flush() {
            if (points.isNotEmpty()) add(Slide(heading.ifBlank { docTitle }, points.joinToString("\n"), notes = notes.toString().trim(), layout = Layout.CONTENT))
            points.clear()
            notes.clear()
        }
        fun newHeading(text: String) {
            flush()
            if (!shown && heading.isNotBlank()) add(Slide(heading, "", layout = Layout.SECTION))
            heading = text
            shown = false
        }
        fun sentence(t: String) = Regex("^(.{20,140}?[.!?])(\\s|$)").find(t)?.groupValues?.get(1) ?: t.take(140)
        for (b in blocks) {
            val kind = DocBlocks.kindOf(b)
            val img = DocMarkup.image(b)
            when {
                b.startsWith("# ") -> {}
                kind == DocBlocks.Kind.H2 || kind == DocBlocks.Kind.H3 -> newHeading(DocBlocks.plainOf(b))
                img != null -> { flush(); add(Slide(heading.ifBlank { img.alt }, "", layout = Layout.IMAGE, image = img.src, imageAlt = img.alt)) }
                kind == DocBlocks.Kind.TABLE -> { flush(); add(Slide(heading.ifBlank { "Tabelle" }, b, layout = Layout.TABLE)) }
                kind == DocBlocks.Kind.CODE && b.trimStart().startsWith("```chart") -> { flush(); add(Slide(heading.ifBlank { "Diagramm" }, b, layout = Layout.CHART)) }
                kind == DocBlocks.Kind.BULLETS || kind == DocBlocks.Kind.NUMBERS || kind == DocBlocks.Kind.TASKS ->
                    b.lines().filter { it.isNotBlank() }.forEach { l -> points += l.replace(Regex("^(\\s*)(\\d+[.)]|[-*+])\\s+(\\[[ xX]]\\s+)?"), "$1- ") }
                kind == DocBlocks.Kind.QUOTE || kind == DocBlocks.Kind.NOTE -> points += "- " + sentence(DocBlocks.plainOf(b).replace("\n", " "))
                kind == DocBlocks.Kind.PARAGRAPH -> {
                    val text = DocBlocks.plainOf(b).replace("\n", " ")
                    points += "- " + sentence(text)
                    notes.append(text).append(' ')
                }
                else -> {}
            }
        }
        newHeading("")
        return splitFull(slides).first + Slide("Vielen Dank!", "Fragen und Diskussion", layout = Layout.END)
    }
}

/**
 * A freely placed element of a slide (AP-16 Stufe 3): text box, picture or shape, in centimetres on the 33,87 × 19,05 cm
 * slide (like PowerPoint). Colours as #RRGGBB.
 */
@kotlinx.serialization.Serializable
data class SlideElement(
    /** „text“, „bild“ or „form“. */
    val kind: String = "text",
    val x: Double = 4.0,
    val y: Double = 5.0,
    val w: Double = 12.0,
    val h: Double = 3.0,
    val text: String = "",
    /** Picture: path relative to the document („Bilder/…“), absolute or data URL. */
    val src: String = "",
    /** Shape: „rechteck“, „abgerundet“, „ellipse“, „pfeil“, „linie“. */
    val shape: String = "rechteck",
    /** Fill (shapes, text boxes); "" = none. */
    val fill: String = "",
    /** Text or line colour. */
    val color: String = "#00008C",
    /** Font size in pt. */
    val size: Int = 20,
    val bold: Boolean = false,
    /** „links“, „mitte“, „rechts“. */
    val align: String = "links",
    /** Degrees clockwise (shared canvas, AP-50). */
    val rotation: Double = 0.0,
    /** Elements with the same group id move and align together. */
    val group: String = "",
    /** Second colour of a gradient fill (top → bottom). */
    val fill2: String = "",
)

/** Masterfolie (AP-16 Stufe 3): background, accent and footer of every slide of the deck; logo on or off. */
@kotlinx.serialization.Serializable
data class SlideMaster(
    val background: String = "",
    val accent: String = "",
    val footer: String = "",
    val logo: Boolean = true,
)
