package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Design
import io.github.thepytales.llmhub.data.DesignBrand
import io.github.thepytales.llmhub.data.DesignElement
import io.github.thepytales.llmhub.data.DesignFormat
import io.github.thepytales.llmhub.data.DesignPage
import io.github.thepytales.llmhub.data.DesignTemplates
import io.github.thepytales.llmhub.data.ElementKind
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Erstellen › Gestalten (AP-24): the agent makes a design from a template of the TUD corporate design and fills its
 * placeholders („Poster aus diesem Paper“), changes it later and reads it back. Pictures for the picture placeholder
 * come from the image model (FLUX) when the agent describes one. Offered in every chat of the Windows app.
 */
class DesignTools(private val c: AppContainer) : ToolProvider {
    private val templateList: String
        get() = DesignTemplates.all.joinToString("; ") { t -> "${t.id} (${t.name}, ${t.format.sizeLabel}): " + t.slots.joinToString(", ") { it.first } }

    override val defs: List<ToolDef>
        get() = listOf(
            ToolDef(
                CREATE,
                "Gestaltet ein neues Design im Corporate Design der TU Dresden aus einer Vorlage und füllt deren Platzhalter. " +
                    "Vorlagen und ihre Platzhalter: $templateList; leer (leere Seite, Format mit format). Texte passen sich der Box an " +
                    "(werden kleiner). Absätze mit \\n, Stichpunkte mit „• “. Danach lässt es sich unter „Erstellen“ bearbeiten und als PNG, PDF oder PowerPoint exportieren. " +
                    "Nicht für Briefe, Protokolle oder Bescheinigungen (dafür create_document).",
                listOf(
                    Param("template", "Vorlage, z. B. poster, flyer, aushang, einladung, urkunde, post, story, banner, video oder leer"),
                    Param("title", "Name des Designs, z. B. „Poster DGfE 2026“"),
                    Param("texts", "Texte je Platzhalter, z. B. {\"titel\": \"…\", \"abschnitt1_text\": \"…\"}. Leerer Text entfernt den Platzhalter (z. B. \"qr\": \"\").", required = false, schema = TEXTS_SCHEMA),
                    Param("qr", "Link oder Text für den QR-Code (z. B. DOI des Papers)", required = false),
                    Param("image_prompt", "Bild für den Bildplatz: kurze englische Beschreibung für das Bildmodell, ohne Text im Bild", required = false),
                    Param("format", "Nur bei template=leer: ${DesignFormat.ALL.joinToString(", ") { it.id }}", required = false),
                    Param("elements", ELEMENTS_HELP, required = false, schema = ELEMENTS_SCHEMA),
                ),
                writes = true,
            ),
            ToolDef(
                UPDATE,
                "Ändert ein Design: Texte von Platzhaltern ersetzen, Elemente hinzufügen oder entfernen, neues Bild. Vorher mit $READ den Stand holen.",
                listOf(
                    Param("id", "Design-ID"),
                    Param("texts", "Neue Texte je Platzhalter oder Element-ID; leerer Text entfernt das Element", required = false, schema = TEXTS_SCHEMA),
                    Param("title", "Neuer Name", required = false),
                    Param("qr", "Neuer Inhalt des QR-Codes", required = false),
                    Param("image_prompt", "Neues Bild für den Bildplatz (englische Beschreibung)", required = false),
                    Param("elements", ELEMENTS_HELP, required = false, schema = ELEMENTS_SCHEMA),
                    Param("remove", "IDs oder Platzhalter der Elemente, die wegfallen", required = false, schema = REMOVE_SCHEMA),
                ),
                writes = true,
            ),
            ToolDef(
                READ,
                "Liest ein Design (Format, Seiten, Elemente mit ID, Platzhalter, Position in mm und Text). Ohne id: Liste aller Designs.",
                listOf(Param("id", "Design-ID (leer: Liste)", required = false)),
            ),
        )

    override fun parallelSafe(name: String) = name == READ

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        CREATE -> create(args, ctx)
        UPDATE -> update(args)
        else -> read(args.s("id")?.trim().orEmpty())
    }

    private suspend fun create(args: JsonObject, ctx: RepoContext): ToolOutput {
        val key = args.req("template").trim().lowercase()
        val title = args.s("title")?.trim()?.ifBlank { null }
        val template = DesignTemplates.byId(key)
        if (template == null && key !in setOf("leer", "blank", "frei", "empty")) {
            return ToolOutput("FEHLER: Unbekannte Vorlage „$key“. Möglich: ${DesignTemplates.all.joinToString(", ") { it.id }}, leer.")
        }
        var d = template?.create(title ?: template.name, ctx.chatId)
            ?: DesignTemplates.blank(DesignFormat.byId(args.s("format")?.trim()) ?: DesignFormat.A4, title ?: "Neues Design", ctx.chatId)
        d = applyTexts(d, texts(args["texts"]), args.s("qr"))
        d = addElements(d, args["elements"])
        val notes = mutableListOf<String>()
        args.s("image_prompt")?.trim()?.takeIf { it.isNotEmpty() }?.let { prompt ->
            d = c.designs.save(d, touch = false)
            imageInto(d, prompt).fold({ d = it }, { notes += "Bild nicht erzeugt: ${it.message}" })
        }
        d = c.designs.save(d)
        return ToolOutput(summary("Design „${d.title}“ gespeichert.", d, notes))
    }

    private suspend fun update(args: JsonObject): ToolOutput {
        val id = args.req("id").trim()
        var d = c.designs.get(id) ?: return ToolOutput("FEHLER: Kein Design mit der ID $id. Mit $READ ohne id die Liste holen.")
        args.s("title")?.trim()?.takeIf { it.isNotEmpty() }?.let { d = d.copy(title = it) }
        d = applyTexts(d, texts(args["texts"]), args.s("qr"))
        list(args["remove"]).takeIf { it.isNotEmpty() }?.let { gone ->
            d = d.copy(pages = d.pages.map { p -> p.copy(elements = p.elements.filter { it.id !in gone && (it.slot.isBlank() || it.slot !in gone) }) })
        }
        d = addElements(d, args["elements"])
        val notes = mutableListOf<String>()
        args.s("image_prompt")?.trim()?.takeIf { it.isNotEmpty() }?.let { prompt ->
            imageInto(d, prompt).fold({ d = it }, { notes += "Bild nicht erzeugt: ${it.message}" })
        }
        d = c.designs.save(d)
        return ToolOutput(summary("Design „${d.title}“ geändert.", d, notes))
    }

    private fun read(id: String): ToolOutput {
        if (id.isEmpty()) {
            val all = c.designs.items.value
            return ToolOutput(
                if (all.isEmpty()) "Noch keine Designs."
                else all.joinToString("\n") { "- ${it.title} (${DesignFormat.byId(it.format)?.label ?: it.format}, ${it.pages.size} Seite(n)) – $ID_LABEL ${it.id}" },
            )
        }
        val d = c.designs.get(id) ?: return ToolOutput("FEHLER: Kein Design mit der ID $id.")
        return ToolOutput(describe(d).take(MAX_TOOL_RESULT))
    }

    /** Fills placeholders (by slot name or element id); an empty text removes the element. */
    private fun applyTexts(d: Design, texts: Map<String, String>, qr: String?): Design {
        val all = if (qr != null) texts + ("qr" to qr) else texts
        if (all.isEmpty()) return d
        return d.copy(pages = d.pages.map { p ->
            p.copy(elements = p.elements.mapNotNull { e ->
                val value = all[e.slot.takeIf { it.isNotBlank() }] ?: all[e.id] ?: return@mapNotNull e
                when {
                    value.isBlank() && e.kind != ElementKind.IMAGE -> null
                    e.kind == ElementKind.TEXT || e.kind == ElementKind.QR -> e.copy(text = value.replace("\\n", "\n").trim())
                    e.kind == ElementKind.IMAGE && value.isBlank() -> null
                    else -> e
                }
            })
        })
    }

    /** Adds elements the agent describes (on the page given by "page", 1 = first; new pages are added as needed). */
    private fun addElements(d: Design, raw: JsonElement?): Design {
        val items = when (raw) {
            is JsonArray -> raw
            is JsonPrimitive -> raw.contentOrNull?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
            else -> null
        } ?: return d
        val pages = d.pages.toMutableList()
        items.filterIsInstance<JsonObject>().forEach { o ->
            val e = element(o, d) ?: return@forEach
            val index = ((o.num("page") ?: 1f).toInt() - 1).coerceIn(0, 19)
            while (pages.size <= index) pages += DesignPage(background = pages.lastOrNull()?.background ?: "#FFFFFF")
            pages[index] = pages[index].copy(elements = pages[index].elements + e)
        }
        return d.copy(pages = pages)
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.let { it.floatOrNull ?: it.contentOrNull?.replace(',', '.')?.toFloatOrNull() }
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull == "true") }

    private fun element(o: JsonObject, d: Design): DesignElement? {
        val kind = o.str("kind")?.trim()?.lowercase()?.let { k ->
            when (k) {
                "textbox", "titel", "heading" -> ElementKind.TEXT
                "rectangle", "box", "rechteck" -> ElementKind.RECT
                "circle", "kreis", "oval" -> ElementKind.ELLIPSE
                "picture", "bild", "logo" -> ElementKind.IMAGE
                "symbol" -> ElementKind.ICON
                "qrcode", "qr-code" -> ElementKind.QR
                "shape", "tud-shape", "tud_shape" -> ElementKind.TUD_SHAPE
                else -> k
            }
        }?.takeIf { it in ElementKind.ALL } ?: return null
        val w = (o.num("w") ?: o.num("width") ?: if (kind == ElementKind.TEXT) d.width * 0.6f else 30f).coerceIn(0f, d.width * 2)
        val h = (o.num("h") ?: o.num("height") ?: if (kind == ElementKind.LINE) 0f else 20f).coerceIn(0f, d.height * 2)
        val src = o.str("src")?.trim().orEmpty().let { s ->
            val l = s.lowercase().replace("ß", "ss").replace("white", "weiss").replace("black", "schwarz")
            when {
                s.isEmpty() && o.str("kind")?.lowercase() == "logo" -> DesignBrand.LOGO_BLUE
                // An official variant („logo:tud-kurz-weiss“) or the horizontal logo in blue or white.
                DesignBrand.logo(l) != null -> l
                l.startsWith("logo") -> if ("weiss" in l) DesignBrand.LOGO_WHITE else DesignBrand.LOGO_BLUE
                else -> ""
            }
        }
        val logo = DesignBrand.logo(src)
        return DesignElement(
            kind = kind,
            x = o.num("x") ?: 10f, y = o.num("y") ?: 10f, w = w, h = h,
            rotation = o.num("rotation") ?: 0f,
            opacity = (o.num("opacity") ?: 1f).coerceIn(0.05f, 1f),
            fill = DesignBrand.color(o.str("fill")).orEmpty(),
            stroke = DesignBrand.color(o.str("stroke")).orEmpty(),
            strokeWidth = o.num("stroke_width") ?: if (kind == ElementKind.LINE || kind == ElementKind.ARROW) 0.8f else 0f,
            radius = o.num("radius") ?: 0f,
            text = o.str("text")?.replace("\\n", "\n").orEmpty(),
            fontSize = (o.num("size") ?: o.num("font_size") ?: 24f).coerceIn(4f, 400f),
            bold = o.bool("bold") ?: false,
            medium = o.bool("medium") ?: false,
            italic = o.bool("italic") ?: false,
            color = DesignBrand.color(o.str("color")) ?: "#000000",
            align = o.str("align")?.takeIf { it in setOf("left", "center", "right") } ?: "left",
            valign = o.str("valign")?.takeIf { it in setOf("top", "middle", "bottom") } ?: "top",
            fit = o.bool("fit") ?: (kind == ElementKind.TEXT),
            src = src,
            fitMode = if (src.startsWith("logo:")) "contain" else "cover",
            icon = o.str("icon").orEmpty(),
            flipX = o.bool("flip") ?: o.bool("mirror") ?: false,
            mask = if (kind == ElementKind.IMAGE && (o.str("mask")?.lowercase()?.contains("shape") == true)) "shape" else "",
            slot = o.str("slot").orEmpty(),
        ).let { e ->
            when {
                // Logos keep their proportions, the Shape is never distorted and turns in 45° steps.
                logo != null && o.num("h") == null -> e.copy(h = e.w / logo.ratio)
                e.kind == ElementKind.TUD_SHAPE || e.mask == "shape" -> minOf(e.w, e.h).let { side -> e.copy(w = side, h = side, rotation = Math.round(e.rotation / 45f) * 45f) }
                else -> e
            }
        }
    }

    /** Generates a picture for the picture placeholder (or the largest empty picture) of the first page that has one. */
    private suspend fun imageInto(d: Design, prompt: String): Result<Design> = runCatching {
        val (pageIndex, target) = d.allElements().filter { it.second.kind == ElementKind.IMAGE && !it.second.src.startsWith("logo:") }
            .sortedWith(compareByDescending<Pair<Int, DesignElement>> { it.second.slot == "bild" }.thenByDescending { it.second.w * it.second.h })
            .firstOrNull() ?: throw IllegalStateException("Das Design hat keinen Bildplatz – zuerst mit elements ein Bild (kind image) hinzufügen.")
        val size = imageSize(target.w / target.h.coerceAtLeast(1f))
        val bytes = c.llm.generateImage(c.settings.current.imageModel, prompt, size).firstOrNull() ?: throw IllegalStateException("Das Bildmodell hat kein Bild geliefert.")
        val name = c.designs.addAsset(d.id, bytes, io.github.thepytales.llmhub.docs.DocMarkup.imageType(bytes) ?: "png")
        d.copy(pages = d.pages.mapIndexed { i, p ->
            if (i != pageIndex) p else p.copy(elements = p.elements.map { if (it.id == target.id) it.copy(src = name, cropX = 0f, cropY = 0f, cropW = 1f, cropH = 1f, alt = prompt.take(200)) else it })
        })
    }

    private fun summary(head: String, d: Design, notes: List<String>): String {
        val open = d.allElements().map { it.second }.filter { e ->
            e.slot.isNotBlank() && ((e.kind == ElementKind.TEXT && DesignTemplates.byId(d.template)?.slots?.any { it.first == e.slot && it.second == e.text } == true) ||
                (e.kind == ElementKind.IMAGE && e.src.isBlank()))
        }.map { it.slot }.distinct()
        return buildString {
            append(head).append(' ').append(ID_LABEL).append(' ').append(d.id)
            append(" · ").append(DesignFormat.byId(d.format)?.label ?: d.format).append(" · ").append(d.pages.size).append(if (d.pages.size == 1) " Seite" else " Seiten")
            if (open.isNotEmpty()) append("\nNoch mit Beispieltext bzw. ohne Bild: ").append(open.joinToString(", ")).append(" (mit $UPDATE füllen oder mit leerem Text entfernen).")
            notes.forEach { append("\n").append(it) }
            append("\nDas Design lässt sich unter „Erstellen“ › Meine Designs weiter bearbeiten und als PNG, PDF (Druck) oder PowerPoint exportieren.")
        }
    }

    companion object {
        const val CREATE = "create_design"
        const val UPDATE = "update_design"
        const val READ = "read_design"

        /** Marks the id in a tool result (the chat shows a card with „Bearbeiten“). */
        const val ID_LABEL = "Design-ID:"

        fun idIn(result: String): String? = Regex(Regex.escape(ID_LABEL) + "\\s*([0-9a-f-]{8,})").find(result)?.groupValues?.get(1)

        private const val ELEMENTS_HELP = "Zusätzliche Elemente (Liste): kind (text, shape = TUD-Shape, rect, ellipse, line, arrow, image, icon, qr, logo), " +
            "page (1 = erste Seite), x, y, w, h in mm ab oben links, text, size (pt), medium (Überschriften in Noto Sans Medium, nicht bold), " +
            "color/fill (TUD Farben: Brillantblau, Dunkelblau, Blau 1, Blau 2, Türkis 1, Grün 2 … oder #RRGGBB), align (left/center/right), " +
            "rotation (Shape nur in 45°-Schritten), flip (Shape spiegeln), mask: \"shape\" (Foto in der Shape), " +
            "icon (TUD-Icon wie tud:Kommunikation & Organisation/16 = Kalender, tud:Mobilität/15 = Ort, tud:Symbole/40 = Person, oder ein Material-Symbol), " +
            "src (logo:tud, logo:tud-weiss, logo:tud-kurz-blau, logo:tud-vertikal-blau-de …)"

        private val TEXTS_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("additionalProperties") { put("type", "string") }
        }

        private val ELEMENTS_SCHEMA: JsonObject = buildJsonObject {
            put("type", "array")
            putJsonObject("items") { put("type", "object") }
        }

        private val REMOVE_SCHEMA: JsonObject = buildJsonObject {
            put("type", "array")
            putJsonObject("items") { put("type", "string") }
        }

        /** "texts" as an object, or as a JSON string some models send. */
        internal fun texts(raw: JsonElement?): Map<String, String> {
            val o = when (raw) {
                is JsonObject -> raw
                is JsonPrimitive -> raw.contentOrNull?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                else -> null
            } ?: return emptyMap()
            return o.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k.trim() to it } }.toMap()
        }

        private fun list(raw: JsonElement?): Set<String> = when (raw) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.toSet()
            is JsonPrimitive -> raw.contentOrNull.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else -> emptySet()
        }

        /** A size the image model knows, close to the aspect ratio [ratio] (width / height) of the picture box. */
        fun imageSize(ratio: Float): String {
            val sizes = listOf("1024x1024", "1152x896", "1344x768", "1536x640", "896x1152", "768x1344", "1024x1536", "1536x1024")
            return sizes.minBy { s ->
                val (w, h) = s.split('x').map { it.toFloat() }
                kotlin.math.abs(kotlin.math.ln((w / h) / ratio.coerceIn(0.2f, 5f)))
            }
        }

        /** The design as text for the model. */
        fun describe(d: Design): String = buildString {
            append("Design „${d.title}“ ($ID_LABEL ${d.id}), ${DesignFormat.byId(d.format)?.label ?: d.format}, ")
            append("${d.width.toInt()} × ${d.height.toInt()} mm, Vorlage ${d.template.ifBlank { "leer" }}\n")
            d.pages.forEachIndexed { i, p ->
                append("\nSeite ${i + 1} (Hintergrund ${p.background}):\n")
                p.elements.forEach { e ->
                    append("- ${e.id} ${e.kind}")
                    if (e.slot.isNotBlank()) append(" [${e.slot}]")
                    append(" x=${e.x.toInt()} y=${e.y.toInt()} w=${e.w.toInt()} h=${e.h.toInt()}")
                    when (e.kind) {
                        ElementKind.TEXT -> append(" ${e.fontSize.toInt()} pt${if (e.bold) " fett" else if (e.medium) " Medium" else ""}: „${e.text.replace("\n", "\\n").take(400)}“")
                        ElementKind.QR -> append(": ${e.text}")
                        ElementKind.IMAGE -> append(if (e.src.isBlank()) " (leer)" else if (e.src.startsWith("logo:")) " ${e.src}" else " Bild")
                        ElementKind.ICON -> append(" ${e.icon}")
                        ElementKind.TUD_SHAPE -> append(" ${e.fill}${if (e.rotation != 0f) " ${e.rotation.toInt()}°" else ""}${if (e.flipX) " gespiegelt" else ""}")
                        else -> if (e.fill.isNotBlank()) append(" ${e.fill}")
                    }
                    append('\n')
                }
            }
        }

        /** Gestalten mode: how a good design is made. */
        val MODE_PROMPT: String
            get() = "GESTALTEN-MODUS (AP-24): Du gestaltest im Corporate Design 2025 der TU Dresden Poster, digitale Poster, Flyer, " +
                "Aushänge, Einladungskarten, Social-Media-Beiträge, OPAL-Kursbanner und Titelbilder für Lehrvideos. Briefe, Protokolle und " +
                "Teilnahmebescheinigungen sind Dokumente (create_document, TU Vorlagen), keine Designs.\n" +
                "Regeln der CD: Logo oben links mit Schutzraum, nie verzerrt; Überschriften in Noto Sans Medium (medium, nicht bold), " +
                "Brillantblau auf Weiß oder Weiß auf Brillantblau, Fließtext Schwarz; nur TUD Farben; die Shape (kind shape) ist das Bildmotiv – " +
                "randabfallend, nur in 45°-Schritten gedreht, gespiegelt erlaubt, nie als Umriss, mehrere Shapes gleich groß und ohne Überlappung, " +
                "Fotos dürfen in die Shape (mask shape); Symbole bevorzugt als TUD-Icons. Die Vorlagen halten diese Regeln schon ein – ändere am " +
                "Aufbau nur, was nötig ist.\n" +
                "1. Wähle die passende Vorlage (${DesignTemplates.all.joinToString(", ") { "${it.id} = ${it.name}" }}) und erstelle das Design mit $CREATE. " +
                "Fehlt Wesentliches (Anlass, Datum, Ort), frage höchstens einmal mit ask_user.\n" +
                "2. Inhalte aus angehängten Dateien (z. B. einem Paper für ein Poster) knapp und fachlich korrekt übernehmen: Titel kurz, " +
                "Poster-Abschnitte höchstens 60–90 Wörter, gern Stichpunkte mit „• “. Nichts erfinden – keine Ergebnisse, Zahlen, Namen oder Termine, " +
                "die nicht in den Unterlagen stehen; Unbekanntes als Platzhalter in eckigen Klammern lassen.\n" +
                "3. Bild nur, wenn es passt: image_prompt (Englisch, ohne Text im Bild); für Diagramme aus dem Paper bleibt der Bildplatz frei. " +
                "QR-Code: DOI oder Link, sonst \"qr\": \"\".\n" +
                "4. Änderungen („kürzer“, „anderes Bild“, „Datum ergänzen“): $READ, dann $UPDATE.\n" +
                "5. Antworte danach kurz, was entstanden ist und was noch fehlt; das Design lässt sich unter „Erstellen“ weiter bearbeiten und als " +
                "PNG, PDF (Druck) oder PowerPoint exportieren."
    }
}
