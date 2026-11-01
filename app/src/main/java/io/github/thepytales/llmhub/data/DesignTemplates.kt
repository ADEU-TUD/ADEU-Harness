package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.docs.TudBrand

/**
 * Vorlagen des Kreativbereichs (AP-24) für den Uni-Alltag, alle im TUD-Design: Tagungsposter, Flyer, Aushang, Einladung,
 * Teilnahmebescheinigung, Social Media, OPAL-Kursbanner, Titelbild für Lehrvideos. Texte, Bilder und QR-Codes, die die
 * KI füllt, haben einen [DesignElement.slot]; Texte mit [DesignElement.fit] werden kleiner, wenn sie nicht passen.
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
    private const val BLUE = TudBrand.BLUE
    private const val MID = TudBrand.MID_BLUE
    private const val LIGHT = TudBrand.LIGHT_BLUE
    private const val GREY = TudBrand.GREY
    private const val TINT = "#E5F1FF"
    private const val WHITE = "#FFFFFF"
    private const val INK = "#1A1A1A"

    private fun text(
        slot: String, x: Float, y: Float, w: Float, h: Float, text: String, size: Float, bold: Boolean = false,
        color: String = INK, align: String = "left", valign: String = "top", lineHeight: Float = 1.25f, fit: Boolean = true, italic: Boolean = false,
    ) = DesignElement(
        kind = ElementKind.TEXT, slot = slot, x = x, y = y, w = w, h = h, text = text, fontSize = size, bold = bold, italic = italic,
        color = color, align = align, valign = valign, lineHeight = lineHeight, fit = fit,
    )

    private fun rect(x: Float, y: Float, w: Float, h: Float, fill: String, radius: Float = 0f, stroke: String = "", strokeWidth: Float = 0f, locked: Boolean = false) =
        DesignElement(kind = ElementKind.RECT, x = x, y = y, w = w, h = h, fill = fill, radius = radius, stroke = stroke, strokeWidth = strokeWidth, locked = locked)

    private fun logo(x: Float, y: Float, w: Float, white: Boolean) = DesignElement(
        kind = ElementKind.IMAGE, slot = "logo", x = x, y = y, w = w, h = w / DesignBrand.LOGO_RATIO, src = if (white) DesignBrand.LOGO_WHITE else DesignBrand.LOGO_BLUE,
        fitMode = "contain", alt = "Logo der TU Dresden",
    )

    private fun picture(slot: String, x: Float, y: Float, w: Float, h: Float, radius: Float = 0f) =
        DesignElement(kind = ElementKind.IMAGE, slot = slot, x = x, y = y, w = w, h = h, radius = radius, fill = TINT)

    private fun qr(slot: String, x: Float, y: Float, size: Float, color: String = BLUE, text: String = "https://tu-dresden.de") =
        DesignElement(kind = ElementKind.QR, slot = slot, x = x, y = y, w = size, h = size, text = text, color = color, fill = WHITE)

    private fun icon(name: String, x: Float, y: Float, size: Float, color: String = BLUE) =
        DesignElement(kind = ElementKind.ICON, icon = name, x = x, y = y, w = size, h = size, color = color)

    private fun line(x: Float, y: Float, w: Float, color: String = GREY, width: Float = 0.4f) =
        DesignElement(kind = ElementKind.LINE, x = x, y = y, w = w, h = 0f, stroke = color, strokeWidth = width)

    /** Pixels of a screen format in mm. */
    private fun p(v: Int) = v * DesignFormat.MM_PER_PX

    /** Pixel font size → pt. */
    private fun px(v: Int) = v * 0.75f

    private fun format(id: String) = DesignFormat.byId(id) ?: DesignFormat.A4

    val POSTER = DesignTemplate("poster", "Tagungsposter", "A1 hoch mit Kopfband, vier Abschnitten in zwei Spalten, Bild und QR-Code.", format("a1")) {
        val c1 = 30f
        val c2 = 339f
        val cw = 255f
        listOf(
            DesignPage(
                elements = listOf(
                    rect(0f, 0f, 594f, 196f, BLUE, locked = true),
                    logo(30f, 24f, 140f, white = true),
                    text("titel", 30f, 84f, 534f, 62f, "Titel des Beitrags", 80f, bold = true, color = WHITE, lineHeight = 1.1f, valign = "bottom"),
                    text("autoren", 30f, 152f, 534f, 16f, "Vorname Nachname, Vorname Nachname", 32f, color = WHITE),
                    text("einrichtung", 30f, 172f, 534f, 14f, "Professur … · Fakultät … · TU Dresden", 24f, color = LIGHT),
                    text("abschnitt1_titel", c1, 222f, cw, 16f, "Einleitung", 40f, bold = true, color = BLUE),
                    text("abschnitt1_text", c1, 242f, cw, 238f, "Worum geht es? Fragestellung und Hintergrund in wenigen Sätzen.", 25f, lineHeight = 1.35f),
                    text("abschnitt2_titel", c1, 498f, cw, 16f, "Methode", 40f, bold = true, color = BLUE),
                    text("abschnitt2_text", c1, 518f, cw, 236f, "Stichprobe, Vorgehen, Instrumente.", 25f, lineHeight = 1.35f),
                    text("abschnitt3_titel", c2, 222f, cw, 16f, "Ergebnisse", 40f, bold = true, color = BLUE),
                    picture("bild", c2, 242f, cw, 176f, radius = 2f),
                    text("abschnitt3_text", c2, 426f, cw, 150f, "Die wichtigsten Befunde.", 25f, lineHeight = 1.35f),
                    text("abschnitt4_titel", c2, 594f, cw, 16f, "Fazit", 40f, bold = true, color = BLUE),
                    text("abschnitt4_text", c2, 614f, cw, 140f, "Was folgt daraus? Ausblick.", 25f, lineHeight = 1.35f),
                    rect(0f, 776f, 594f, 65f, TINT, locked = true),
                    text("kontakt", 30f, 788f, 440f, 42f, "Kontakt: vorname.nachname@tu-dresden.de", 24f, color = GREY, valign = "middle", lineHeight = 1.35f),
                    qr("qr", 516f, 783f, 50f),
                ),
            ),
        )
    }

    val FLYER = DesignTemplate("flyer", "Flyer", "A5 hoch mit großem Bild, Titel, Text und Fußband mit Logo.", format("a5")) {
        listOf(
            DesignPage(
                elements = listOf(
                    picture("bild", 0f, 0f, 148f, 80f),
                    rect(0f, 80f, 148f, 2.5f, MID),
                    text("titel", 12f, 92f, 124f, 26f, "Titel der Veranstaltung", 26f, bold = true, color = BLUE, lineHeight = 1.1f),
                    text("untertitel", 12f, 120f, 124f, 10f, "Untertitel oder Datum", 13f, color = GREY),
                    text("text", 12f, 134f, 124f, 46f, "Beschreibung in wenigen Sätzen: Worum geht es, für wen ist es, was nehmen die Teilnehmenden mit?", 10.5f, lineHeight = 1.4f),
                    rect(0f, 186f, 148f, 24f, BLUE, locked = true),
                    logo(10f, 191.3f, 40f, white = true),
                    text("kontakt", 56f, 189f, 82f, 18f, "tu-dresden.de", 8f, color = WHITE, align = "right", valign = "middle", lineHeight = 1.3f),
                ),
            ),
        )
    }

    val NOTICE = DesignTemplate("aushang", "Aushang", "A4 hoch für das Schwarze Brett: große Überschrift, Wann/Wo/Wer und QR-Code.", format("a4")) {
        listOf(
            DesignPage(
                elements = listOf(
                    rect(0f, 0f, 210f, 10f, BLUE, locked = true),
                    logo(15f, 20f, 62f, white = false),
                    text("kategorie", 15f, 52f, 180f, 10f, "Vortrag", 15f, bold = true, color = MID),
                    text("titel", 15f, 64f, 180f, 60f, "Überschrift des Aushangs", 46f, bold = true, color = BLUE, lineHeight = 1.08f),
                    text("text", 15f, 130f, 180f, 70f, "Kurzer Text: worum es geht und für wen es gedacht ist.", 14f, lineHeight = 1.4f),
                    rect(15f, 207f, 180f, 50f, TINT, radius = 3f),
                    icon("Event", 22f, 214f, 8f),
                    text("wann", 34f, 213.5f, 118f, 10f, "Mittwoch, 12. November, 16:40 Uhr", 14f, bold = true, valign = "middle"),
                    icon("Place", 22f, 228f, 8f),
                    text("wo", 34f, 227.5f, 118f, 10f, "Hörsaalzentrum, Raum 101", 14f, valign = "middle"),
                    icon("Person", 22f, 242f, 8f),
                    text("wer", 34f, 241.5f, 118f, 10f, "Anmeldung nicht nötig", 14f, valign = "middle"),
                    qr("qr", 159f, 212f, 31f),
                    text("kontakt", 15f, 266f, 180f, 12f, "Kontakt: vorname.nachname@tu-dresden.de", 10f, color = GREY, valign = "middle"),
                    rect(0f, 287f, 210f, 10f, BLUE, locked = true),
                ),
            ),
        )
    }

    val INVITATION = DesignTemplate("einladung", "Einladung", "A5 quer mit blauem Seitenband, Programm und Anmeldung.", format("a5-quer")) {
        listOf(
            DesignPage(
                elements = listOf(
                    rect(0f, 0f, 70f, 148f, BLUE, locked = true),
                    logo(10f, 12f, 50f, white = true),
                    text("anlass", 10f, 108f, 55f, 26f, "Einladung", 22f, bold = true, color = WHITE, valign = "bottom"),
                    text("titel", 82f, 16f, 116f, 34f, "Titel der Veranstaltung", 26f, bold = true, color = BLUE, lineHeight = 1.1f),
                    text("text", 82f, 54f, 116f, 40f, "Wir laden Sie herzlich ein … Programm in wenigen Zeilen.", 11f, lineHeight = 1.4f),
                    line(82f, 97f, 116f, LIGHT, 0.5f),
                    text("details", 82f, 100f, 116f, 22f, "Donnerstag, 4. Dezember 2026, 18 Uhr\nFestsaal, Rektorat", 11f, bold = true, lineHeight = 1.4f),
                    text("anmeldung", 82f, 126f, 116f, 12f, "Um Anmeldung wird gebeten bis …", 9f, color = GREY),
                ),
            ),
        )
    }

    val CERTIFICATE = DesignTemplate("urkunde", "Teilnahmebescheinigung", "A4 hoch mit Rahmen, Name, Veranstaltung und zwei Unterschriftszeilen.", format("a4")) {
        listOf(
            DesignPage(
                elements = listOf(
                    rect(10f, 10f, 190f, 277f, "", stroke = BLUE, strokeWidth = 0.8f, locked = true),
                    rect(13f, 13f, 184f, 271f, "", stroke = LIGHT, strokeWidth = 0.4f, locked = true),
                    logo(74f, 28f, 62f, white = false),
                    text("titel", 20f, 72f, 170f, 18f, "Teilnahmebescheinigung", 32f, bold = true, color = BLUE, align = "center"),
                    text("vortext", 20f, 102f, 170f, 10f, "Hiermit wird bestätigt, dass", 13f, align = "center", color = GREY),
                    text("name", 20f, 115f, 170f, 18f, "Vorname Nachname", 28f, bold = true, align = "center"),
                    text("text", 20f, 140f, 170f, 40f, "an der Veranstaltung „Titel der Veranstaltung“ teilgenommen hat.", 14f, align = "center", lineHeight = 1.4f),
                    text("details", 20f, 186f, 170f, 22f, "Umfang: 8 Stunden · Datum: 12.11.2026", 11f, align = "center", color = GREY, lineHeight = 1.4f),
                    line(30f, 250f, 65f, INK, 0.3f),
                    text("ort_datum", 30f, 252f, 65f, 8f, "Dresden, den …", 9f, color = GREY),
                    line(115f, 250f, 65f, INK, 0.3f),
                    text("unterzeichner", 115f, 252f, 65f, 12f, "Prof. Dr. … (Leitung)", 9f, color = GREY),
                ),
            ),
        )
    }

    val POST = DesignTemplate("post", "Social Media: Beitrag", "1080 × 1080 px auf TUD-Blau mit großer Aussage und Logo.", format("post")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    rect(p(80), p(80), p(120), p(12), LIGHT),
                    text("titel", p(80), p(140), p(920), p(520), "Eine klare Aussage in wenigen Worten", px(96), bold = true, color = WHITE, lineHeight = 1.1f, valign = "bottom"),
                    text("text", p(80), p(690), p(920), p(180), "Ein, zwei Sätze dazu – oder Datum und Ort.", px(40), color = WHITE, lineHeight = 1.3f),
                    logo(p(80), p(930), p(240), white = true),
                    text("handle", p(560), p(950), p(440), p(50), "#TUDresden", px(30), color = LIGHT, align = "right", valign = "middle"),
                ),
            ),
        )
    }

    val STORY = DesignTemplate("story", "Social Media: Story", "1080 × 1920 px mit Bild oben und Text auf TUD-Blau.", format("story")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    picture("bild", 0f, 0f, p(1080), p(900)),
                    text("titel", p(80), p(980), p(920), p(420), "Überschrift der Story", px(104), bold = true, color = WHITE, lineHeight = 1.1f),
                    text("text", p(80), p(1420), p(920), p(220), "Kurzer Text, Datum oder Aufruf.", px(44), color = WHITE, lineHeight = 1.3f),
                    logo(p(80), p(1730), p(260), white = true),
                ),
            ),
        )
    }

    val BANNER = DesignTemplate("banner", "OPAL-Kursbanner", "1500 × 300 px: Kurstitel, Semester und ein Bild rechts.", format("banner")) {
        listOf(
            DesignPage(
                background = BLUE,
                elements = listOf(
                    picture("bild", p(1000), 0f, p(500), p(300)),
                    logo(p(60), p(30), p(130), white = true),
                    text("titel", p(60), p(96), p(900), p(110), "Titel des Kurses", px(64), bold = true, color = WHITE, lineHeight = 1.05f, valign = "middle"),
                    text("untertitel", p(60), p(214), p(900), p(56), "Wintersemester 2026/27 · Professur …", px(30), color = LIGHT, valign = "middle"),
                ),
            ),
        )
    }

    val VIDEO_TITLE = DesignTemplate("video", "Titelbild für Lehrvideos", "1920 × 1080 px: Reihe, Titel und Person auf TUD-Blau, Bild rechts.", format("video")) {
        listOf(
            DesignPage(
                elements = listOf(
                    picture("bild", p(960), 0f, p(960), p(1080)),
                    rect(0f, 0f, p(960), p(1080), BLUE, locked = true),
                    logo(p(90), p(90), p(300), white = true),
                    text("reihe", p(90), p(380), p(780), p(60), "Vorlesung · Teil 3", px(40), color = LIGHT, valign = "middle"),
                    text("titel", p(90), p(450), p(780), p(330), "Titel des Lehrvideos", px(88), bold = true, color = WHITE, lineHeight = 1.08f),
                    text("person", p(90), p(860), p(780), p(110), "Prof. Dr. Vorname Nachname\nProfessur …", px(34), color = WHITE, lineHeight = 1.3f, valign = "bottom"),
                ),
            ),
        )
    }

    /** All templates, in the order of the picker. */
    val all: List<DesignTemplate> = listOf(POSTER, FLYER, NOTICE, INVITATION, CERTIFICATE, POST, STORY, BANNER, VIDEO_TITLE)

    fun byId(id: String?): DesignTemplate? {
        val n = id?.trim()?.lowercase().orEmpty()
        return all.firstOrNull { it.id == n } ?: all.firstOrNull { it.name.lowercase() == n } ?: when {
            n.isEmpty() -> null
            "poster" in n -> POSTER
            "flyer" in n -> FLYER
            "aushang" in n || "plakat" in n -> NOTICE
            "einladung" in n -> INVITATION
            "urkunde" in n || "bescheinigung" in n || "zertifikat" in n -> CERTIFICATE
            "story" in n -> STORY
            "social" in n || "instagram" in n || "beitrag" in n || "post" in n -> POST
            "banner" in n || "opal" in n -> BANNER
            "video" in n || "titelbild" in n -> VIDEO_TITLE
            else -> null
        }
    }

    /** An empty design of [format]. */
    fun blank(format: DesignFormat, title: String = "Neues Design", chatId: String? = null) =
        Design(title = title, format = format.id, width = format.width, height = format.height, pages = listOf(DesignPage()), chatId = chatId)
}
