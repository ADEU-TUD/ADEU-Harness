package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.docs.TudBrand
import io.github.thepytales.llmhub.ui.trLater

/**
 * Gestaltungsvorlagen unter „Erstellen“ (AP-24) für den Uni-Alltag nach dem Corporate Design 2025 der TU Dresden:
 * Tagungsposter, digitales Poster, Flyer, Aushang, Einladungskarte, Social Media, OPAL-Kursbanner, Titelbild für
 * Lehrvideos (Briefe, Protokolle und Bescheinigungen sind TU Vorlagen). Regeln aus dem CD-Paket: Logo oben links mit
 * Schutzraum, Überschriften in Noto Sans Medium (Brillantblau auf Weiß, Weiß auf Brillantblau), Fließtext Schwarz,
 * nur TUD Farben, die Shape als Bildmotiv (randabfallend, in 45°-Schritten, gern mit Foto) und die TUD-Icons.
 * Texte, Bilder und QR-Codes, die die KI füllt, haben einen [DesignElement.slot]; Texte mit [DesignElement.fit]
 * werden kleiner, wenn sie nicht passen.
 */
data class DesignTemplate(
    val id: String,
    val name: String,
    val description: String,
    val format: DesignFormat,
    private val build: () -> List<DesignPage>,
) {
    /** A new design from this template. */
    fun create(title: String = name, chatId: String? = null): Design =
        Design(title = title, format = format.id, width = format.width, height = format.height, pages = build(), template = id, chatId = chatId)

    /** Placeholders with their default text (for the agent's tool description). */
    val slots: List<Pair<String, String>> by lazy {
        build().flatMap { it.elements }.filter { it.slot.isNotBlank() }.map { it.slot to (if (it.kind == ElementKind.TEXT) it.text else ElementKind.label(it.kind)) }
    }
}

object DesignTemplates {
    // Only colours of the CD (TUD Farben): Brillantblau, Blau 1/2, Dunkelblau, Grau, Schwarz, Weiß.
    private const val BLUE = TudBrand.BLUE
    private const val MID = TudBrand.MID_BLUE
    private const val LIGHT = TudBrand.LIGHT_BLUE
    private const val GREY = TudBrand.GREY_100
    private const val WHITE = "#FFFFFF"
    private const val INK = "#000000"

    // TUD icons of the CD package (Icon-Themensets).
    private const val ICON_DATE = "tud:Kommunikation & Organisation/16"
    private const val ICON_PLACE = "tud:Mobilität/15"
    private const val ICON_PERSON = "tud:Symbole/40"

    private fun text(
        slot: String, x: Float, y: Float, w: Float, h: Float, text: String, size: Float, medium: Boolean = false,
        color: String = INK, align: String = "left", valign: String = "top", lineHeight: Float = 1.25f, fit: Boolean = true, italic: Boolean = false,
    ) = DesignElement(
        kind = ElementKind.TEXT, slot = slot, x = x, y = y, w = w, h = h, text = text, fontSize = size, medium = medium, italic = italic,
        color = color, align = align, valign = valign, lineHeight = lineHeight, fit = fit,
    )

    /** A heading of the CD: Noto Sans Medium (never bold). */
    private fun heading(
        slot: String, x: Float, y: Float, w: Float, h: Float, text: String, size: Float, color: String = BLUE,
        valign: String = "top", lineHeight: Float = 1.1f,
    ) = text(slot, x, y, w, h, text, size, medium = true, color = color, valign = valign, lineHeight = lineHeight)

    private fun rect(x: Float, y: Float, w: Float, h: Float, fill: String, locked: Boolean = false) =
        DesignElement(kind = ElementKind.RECT, x = x, y = y, w = w, h = h, fill = fill, locked = locked)

    /** The official horizontal logo (without its protection zone, which the layouts keep free). */
    private fun logo(x: Float, y: Float, w: Float, white: Boolean) = DesignElement(
        kind = ElementKind.IMAGE, slot = "logo", x = x, y = y, w = w, h = w / DesignBrand.LOGO_RATIO, src = if (white) DesignBrand.LOGO_WHITE else DesignBrand.LOGO_BLUE,
        fitMode = "contain", alt = "Logo der TU Dresden",
    )

    /**
     * The Shape as key visual: always the same size within a layout, rotated in 45° steps, cut off at the edge of
     * the page – never as an outline and never distorted (Shape-Regeln of the CD).
     */
    private fun shape(x: Float, y: Float, side: Float, color: String, rotation: Float = 0f, flip: Boolean = false) =
        DesignElement(kind = ElementKind.TUD_SHAPE, x = x, y = y, w = side, h = side, fill = color, rotation = rotation, flipX = flip)

    private fun picture(slot: String, x: Float, y: Float, w: Float, h: Float) =
        DesignElement(kind = ElementKind.IMAGE, slot = slot, x = x, y = y, w = w, h = h)

    /** A photo inside the Shape („Fotos dürfen in die Shape gesetzt werden“); Blau 2 until a picture is chosen. */
    private fun shapePicture(slot: String, x: Float, y: Float, side: Float, rotation: Float = 0f, flip: Boolean = false) =
        DesignElement(kind = ElementKind.IMAGE, slot = slot, x = x, y = y, w = side, h = side, mask = "shape", fill = LIGHT, rotation = rotation, flipX = flip)

    private fun qr(slot: String, x: Float, y: Float, size: Float, color: String = BLUE, text: String = "https://tu-dresden.de") =
        DesignElement(kind = ElementKind.QR, slot = slot, x = x, y = y, w = size, h = size, text = text, color = color, fill = WHITE)

    private fun icon(name: String, x: Float, y: Float, size: Float, color: String = BLUE) =
        DesignElement(kind = ElementKind.ICON, icon = name, x = x, y = y, w = size, h = size, color = color)

    private fun line(x: Float, y: Float, w: Float, color: String = LIGHT, width: Float = 0.4f) =
        DesignElement(kind = ElementKind.LINE, x = x, y = y, w = w, h = 0f, stroke = color, strokeWidth = width)

    /** Pixels of a screen format in mm. */
    private fun p(v: Int) = v * DesignFormat.MM_PER_PX

    /** Pixel font size → pt. */
    private fun px(v: Int) = v * 0.75f

    private fun format(id: String) = DesignFormat.byId(id) ?: DesignFormat.A4

    val POSTER = DesignTemplate("poster", trLater("Tagungsposter"), trLater("A1 hoch, weiß mit Logo, Shape und vier Abschnitten in zwei Spalten, Bild und QR-Code."), format("a1")) {
        val c1 = 40f
        val c2 = 317f
        val cw = 237f
        listOf(
            DesignPage(
                elements = listOf(
                    shape(404f, -70f, 260f, BLUE),
                    logo(40f, 40f, 150f, white = false),
                    heading("titel", 40f, 112f, 344f, 72f, "Titel des Beitrags", 76f, valign = "bottom"),
                    text("autoren", 40f, 190f, 344f, 14f, "Vorname Nachname, Vorname Nachname", 30f),
                    text("einrichtung", 40f, 206f, 344f, 12f, "Professur … · Fakultät … · TU Dresden", 24f, color = GREY),
                    heading("abschnitt1_titel", c1, 246f, cw, 16f, "Einleitung", 40f),
                    text("abschnitt1_text", c1, 268f, cw, 214f, "Worum geht es? Fragestellung und Hintergrund in wenigen Sätzen.", 25f, lineHeight = 1.35f),
                    heading("abschnitt2_titel", c1, 500f, cw, 16f, "Methode", 40f),
                    text("abschnitt2_text", c1, 522f, cw, 214f, "Stichprobe, Vorgehen, Instrumente.", 25f, lineHeight = 1.35f),
                    heading("abschnitt3_titel", c2, 246f, cw, 16f, "Ergebnisse", 40f),
                    picture("bild", c2, 268f, cw, 160f),
                    text("abschnitt3_text", c2, 436f, cw, 146f, "Die wichtigsten Befunde.", 25f, lineHeight = 1.35f),
                    heading("abschnitt4_titel", c2, 600f, cw, 16f, "Fazit", 40f),
                    text("abschnitt4_text", c2, 622f, cw, 114f, "Was folgt daraus? Ausblick.", 25f, lineHeight = 1.35f),
                    line(40f, 756f, 514f, LIGHT, 0.6f),
                    text("kontakt", 40f, 768f, 420f, 50f, "Kontakt: vorname.nachname@tu-dresden.de", 24f, color = GREY, valign = "middle", lineHeight = 1.35f),
                    qr("qr", 498f, 766f, 56f),
                ),
            ),
        )
    }

    val DIGITAL_POSTER = DesignTemplate(
        "digital", trLater("Digitales Poster"), trLater("1920 × 1080 px für Bildschirme: Logo, Headline und Subline, Bild oben rechts, drei Spalten, Link und QR-Code."),
        format("poster-16-9"),
    ) {
        // Layout „3 Spalten“ of the CD's digital poster (55″ monitor, 5 m distance): text 32 px, headings Medium.
        val cols = listOf(p(48), p(670), p(1290))
        val cw = p(581)
        listOf(
            DesignPage(
                elements = listOf(
                    picture("bild", p(876), 0f, p(1044), p(328)),
                    logo(p(48), p(48), p(294), white = false),
                    heading("titel", p(48), p(170), p(800), p(124), "Headline des Posters", px(60), valign = "bottom"),
                    text("untertitel", p(48), p(299), p(800), p(49), "Subline mit einer kurzen Einordnung", px(32), color = BLUE),
                ) + cols.flatMapIndexed { i, x ->
                    listOf(
                        heading("abschnitt${i + 1}_titel", x, p(395), cw, p(64), listOf("Hintergrund", "Vorgehen", "Ergebnis")[i], px(40), valign = "bottom"),
                        text(
                            "abschnitt${i + 1}_text", x, p(467), cw, if (i == 2) p(360) else p(560),
                            listOf("Worum geht es, und warum ist es wichtig?", "Wie sind wir vorgegangen?", "Was kam heraus?")[i], px(32), lineHeight = 1.3f,
                        ),
                    )
                } + listOf(
                    text("link", p(1290), p(862), p(410), p(150), "Mehr erfahren:\ntu-dresden.de", px(28), color = BLUE, valign = "bottom", lineHeight = 1.3f),
                    qr("qr", p(1722), p(862), p(150)),
                ),
            ),
        )
    }

    val FLYER = DesignTemplate("flyer", "Flyer", trLater("A5 hoch, weiß mit Logo, Foto in der Shape, Titel, Text, Kontakt und QR-Code."), format("a5")) {
        listOf(
            DesignPage(
                elements = listOf(
                    shapePicture("bild", 50f, -24f, 122f),
                    logo(12f, 12f, 44f, white = false),
                    heading("titel", 12f, 104f, 124f, 30f, "Titel der Veranstaltung", 24f),
                    text("untertitel", 12f, 136f, 124f, 10f, "Untertitel oder Datum", 12f, color = BLUE),
                    text("text", 12f, 150f, 124f, 32f, "Beschreibung in wenigen Sätzen: Worum geht es, für wen ist es, was nehmen die Teilnehmenden mit?", 10f, lineHeight = 1.4f),
                    line(12f, 186f, 124f),
                    text("kontakt", 12f, 189f, 98f, 14f, "tu-dresden.de", 8f, color = GREY, valign = "middle", lineHeight = 1.3f),
                    qr("qr", 120f, 189f, 16f),
                ),
            ),
        )
    }

    val NOTICE = DesignTemplate("aushang", trLater("Aushang"), trLater("A4 hoch für das Schwarze Brett: Logo, Shape, große Überschrift, Wann/Wo/Wer mit TUD-Icons und QR-Code."), format("a4")) {
        listOf(
            DesignPage(
                elements = listOf(
                    shape(126f, -34f, 118f, BLUE),
                    // Position and size of the logo on A4 as in the letters and certificates of the CD.
                    logo(25f, 18f, 50.9f, white = false),
                    text("kategorie", 25f, 96f, 160f, 9f, "Vortrag", 14f, medium = true, color = MID),
                    heading("titel", 25f, 107f, 160f, 54f, "Überschrift des Aushangs", 40f),
                    text("text", 25f, 166f, 160f, 40f, "Kurzer Text: worum es geht und für wen es gedacht ist.", 13f, lineHeight = 1.4f),
                    icon(ICON_DATE, 25f, 215f, 9f),
                    text("wann", 38f, 214.5f, 108f, 10f, "Mittwoch, 12. November, 16:40 Uhr", 13f, medium = true, valign = "middle"),
                    icon(ICON_PLACE, 25f, 230f, 9f),
                    text("wo", 38f, 229.5f, 108f, 10f, "Hörsaalzentrum, Raum 101", 13f, valign = "middle"),
                    icon(ICON_PERSON, 25f, 245f, 9f),
                    text("wer", 38f, 244.5f, 108f, 10f, "Anmeldung nicht nötig", 13f, valign = "middle"),
                    qr("qr", 155f, 214f, 30f),
                    line(25f, 266f, 160f),
                    text("kontakt", 25f, 269f, 160f, 10f, "Kontakt: vorname.nachname@tu-dresden.de", 10f, color = GREY, valign = "middle"),
                ),
            ),
        )
    }

    val INVITATION = DesignTemplate("einladung", trLater("Einladungskarte"), trLater("A5 quer: Brillantblau mit Logo und Shape links, Programm und Anmeldung rechts."), format("a5-quer")) {
        listOf(
            DesignPage(
                elements = listOf(
                    rect(0f, 0f, 80f, 148f, BLUE, locked = true),
                    shape(-14f, 46f, 66f, LIGHT),
                    logo(10f, 10f, 46f, white = true),
                    heading("anlass", 10f, 118f, 62f, 20f, "Einladung", 22f, color = WHITE, valign = "bottom"),
                    heading("titel", 92f, 14f, 106f, 34f, "Titel der Veranstaltung", 24f),
                    text("text", 92f, 52f, 106f, 40f, "Wir laden Sie herzlich ein … Programm in wenigen Zeilen.", 11f, lineHeight = 1.4f),
                    line(92f, 96f, 106f),
                    icon(ICON_DATE, 92f, 101f, 7f),
                    text("details", 102f, 99.5f, 96f, 22f, "Donnerstag, 4. Dezember 2026, 18 Uhr\nFestsaal, Rektorat", 11f, medium = true, lineHeight = 1.4f),
                    text("anmeldung", 92f, 126f, 106f, 12f, "Um Anmeldung wird gebeten bis …", 9f, color = GREY),
                ),
            ),
        )
    }

    val POST = DesignTemplate("post", trLater("Social Media: Beitrag"), trLater("1080 × 1080 px auf Brillantblau mit Logo, großer Aussage und Shape."), format("post")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    shape(p(660), p(660), p(540), LIGHT),
                    logo(p(80), p(80), p(280), white = true),
                    heading("titel", p(80), p(230), p(920), p(380), "Eine klare Aussage in wenigen Worten", px(88), color = WHITE, valign = "bottom"),
                    text("text", p(80), p(650), p(540), p(220), "Ein, zwei Sätze dazu – oder Datum und Ort.", px(40), color = WHITE, lineHeight = 1.3f),
                    text("handle", p(80), p(950), p(540), p(50), "#TUDresden", px(30), color = LIGHT, valign = "middle"),
                ),
            ),
        )
    }

    val STORY = DesignTemplate("story", trLater("Social Media: Story"), trLater("1080 × 1920 px auf Brillantblau: Logo, Foto in der Shape, Überschrift und Text."), format("story")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    shapePicture("bild", p(300), p(250), p(920)),
                    logo(p(80), p(100), p(300), white = true),
                    heading("titel", p(80), p(1220), p(920), p(400), "Überschrift der Story", px(100), color = WHITE),
                    text("text", p(80), p(1640), p(920), p(200), "Kurzer Text, Datum oder Aufruf.", px(44), color = WHITE, lineHeight = 1.3f),
                ),
            ),
        )
    }

    val BANNER = DesignTemplate("banner", "OPAL-Kursbanner", trLater("1500 × 300 px auf Brillantblau: Logo, Kurstitel, Semester und ein Foto in der Shape."), format("banner")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    shapePicture("bild", p(1070), p(-60), p(420)),
                    logo(p(60), p(36), p(150), white = true),
                    heading("titel", p(60), p(100), p(940), p(110), "Titel des Kurses", px(64), color = WHITE, valign = "middle", lineHeight = 1.05f),
                    text("untertitel", p(60), p(214), p(940), p(56), "Wintersemester 2026/27 · Professur …", px(30), color = LIGHT, valign = "middle"),
                ),
            ),
        )
    }

    val VIDEO_TITLE = DesignTemplate("video", trLater("Titelbild für Lehrvideos"), trLater("1920 × 1080 px auf Brillantblau: Reihe, Titel und Person, Foto in der Shape rechts."), format("video")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    shapePicture("bild", p(900), p(40), p(1000)),
                    logo(p(96), p(96), p(330), white = true),
                    text("reihe", p(96), p(390), p(780), p(60), "Vorlesung · Teil 3", px(40), color = LIGHT, valign = "middle"),
                    heading("titel", p(96), p(460), p(780), p(330), "Titel des Lehrvideos", px(88), color = WHITE),
                    text("person", p(96), p(860), p(780), p(120), "Prof. Dr. Vorname Nachname\nProfessur …", px(34), color = WHITE, lineHeight = 1.3f, valign = "bottom"),
                ),
            ),
        )
    }

    /** All templates, in the order of the picker. */
    val all: List<DesignTemplate> = listOf(POSTER, DIGITAL_POSTER, FLYER, NOTICE, INVITATION, POST, STORY, BANNER, VIDEO_TITLE)

    fun byId(id: String?): DesignTemplate? {
        val n = id?.trim()?.lowercase().orEmpty()
        return all.firstOrNull { it.id == n } ?: all.firstOrNull { it.name.lowercase() == n } ?: when {
            n.isEmpty() -> null
            "digital" in n || "bildschirm" in n || "monitor" in n || "16:9" in n -> DIGITAL_POSTER
            "poster" in n -> POSTER
            "flyer" in n -> FLYER
            "aushang" in n || "plakat" in n -> NOTICE
            "einladung" in n -> INVITATION
            "story" in n -> STORY
            "social" in n || "instagram" in n || "beitrag" in n || "post" in n -> POST
            "banner" in n || "opal" in n -> BANNER
            "video" in n || "titelbild" in n -> VIDEO_TITLE
            else -> null
        }
    }

    /**
     * Whether a wish („Mach mir einen Aushang für …“) is about a design rather than a text document – then „Erstellen“
     * hands it to Lora in the Gestalten mode. Letters, minutes and certificates stay TU Vorlagen.
     */
    fun fits(text: String): Boolean {
        val t = text.lowercase()
        return listOf("poster", "flyer", "aushang", "plakat", "einladungskarte", "postkarte", "social media", "instagram", "linkedin", "mastodon",
            "story", "kursbanner", "banner", "titelbild", "thumbnail", "grafik", "gestalt", "design", "qr-code", "digitales poster", "infoscreen").any { it in t }
    }

    /** An empty design of [format]. */
    fun blank(format: DesignFormat, title: String = "Neues Design", chatId: String? = null) =
        Design(title = title, format = format.id, width = format.width, height = format.height, pages = listOf(DesignPage()), chatId = chatId)
}
