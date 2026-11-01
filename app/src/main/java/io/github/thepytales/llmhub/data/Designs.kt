package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.docs.TudBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Kinds of [DesignElement]s (AP-24). */
object ElementKind {
    const val TEXT = "text"
    const val RECT = "rect"
    const val ELLIPSE = "ellipse"
    const val TRIANGLE = "triangle"
    const val LINE = "line"
    const val ARROW = "arrow"
    const val STAR = "star"
    const val IMAGE = "image"
    const val ICON = "icon"
    const val QR = "qr"
    /** The Shape of the CD 2025: the two components of the logo (rotated in 45° steps, mirrored, cut off at the edge). */
    const val TUD_SHAPE = "tudshape"

    val SHAPES = listOf(RECT, ELLIPSE, TRIANGLE, LINE, ARROW, STAR)
    val ALL = listOf(TEXT) + SHAPES + listOf(TUD_SHAPE, IMAGE, ICON, QR)

    fun label(kind: String) = when (kind) {
        TEXT -> "Text"
        RECT -> "Rechteck"
        ELLIPSE -> "Kreis"
        TRIANGLE -> "Dreieck"
        LINE -> "Linie"
        ARROW -> "Pfeil"
        STAR -> "Stern"
        IMAGE -> "Bild"
        ICON -> "Symbol"
        QR -> "QR-Code"
        TUD_SHAPE -> "TUD-Shape"
        else -> kind
    }
}

/**
 * One element on a page of a [Design] (AP-24): a text box, a shape, a picture, a symbol or a QR code. Position and size
 * are millimetres from the top left corner of the page (before [rotation], which turns the element around its centre).
 * The order of the page's list is the order of the layers: later elements lie on top.
 */
@Serializable
data class DesignElement(
    val id: String = newDesignId(),
    val kind: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 40f,
    val h: Float = 20f,
    /** Degrees clockwise around the centre. */
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** "#RRGGBB" or empty (no fill). Text boxes: the background behind the text. */
    val fill: String = "",
    val stroke: String = "",
    /** Line width in mm. */
    val strokeWidth: Float = 0f,
    /** Corner radius in mm (rectangles, pictures, text boxes with a background). */
    val radius: Float = 0f,
    val text: String = "",
    /** Font size in pt (1 pt = 0.3528 mm). */
    val fontSize: Float = 24f,
    val bold: Boolean = false,
    /** Noto Sans Medium – the weight of headings in the CD (ignored when [bold]). */
    val medium: Boolean = false,
    val italic: Boolean = false,
    /** Text, symbol and QR colour. */
    val color: String = "#000000",
    /** left · center · right */
    val align: String = "left",
    /** top · middle · bottom */
    val valign: String = "top",
    val lineHeight: Float = 1.25f,
    /** Inner margin of text boxes in mm. */
    val padding: Float = 0f,
    /** The font gets smaller until the text fits the box (texts the AI fills into a template). */
    val fit: Boolean = false,
    /** Picture: file name in the design's folder, or "logo:tud", "logo:tud-weiss", "logo:<id of an own logo>". */
    val src: String = "",
    /** Visible part of the picture as fractions (zuschneiden). */
    val cropX: Float = 0f,
    val cropY: Float = 0f,
    val cropW: Float = 1f,
    val cropH: Float = 1f,
    /** Pictures: "cover" fills the box (cuts off what sticks out), "contain" shows everything (logos). */
    val fitMode: String = "cover",
    /** Symbol name: a Material symbol („School“) or a TUD icon („tud:Forschung/3“). */
    val icon: String = "",
    /** Mirrored left–right (the TUD-Shape may be mirrored). */
    val flipX: Boolean = false,
    /** Pictures: "shape" shows the picture inside the TUD-Shape („Fotos dürfen in die Shape gesetzt werden“). */
    val mask: String = "",
    /** Name of a placeholder of a template ("titel", "text1", "bild" …) that the AI fills. */
    val slot: String = "",
    val locked: Boolean = false,
    /** Alternative text of pictures (accessibility of the PDF and PowerPoint). */
    val alt: String = "",
) {
    val isText get() = kind == ElementKind.TEXT
    val cx get() = x + w / 2
    val cy get() = y + h / 2
}

@Serializable
data class DesignPage(
    val id: String = newDesignId(),
    val background: String = "#FFFFFF",
    val elements: List<DesignElement> = emptyList(),
)

/**
 * A design of „Erstellen“ › Gestalten (AP-24): poster, flyer, notice, invitation card, social media post … with one
 * or more pages of the same size. Sizes are millimetres; screen formats (social media, banners, video titles) are
 * pixels at 96 dpi converted to mm, so a 1080 px post exports as 1080 px again.
 */
@Serializable
data class Design(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val format: String = DesignFormat.A4.id,
    val width: Float = DesignFormat.A4.width,
    val height: Float = DesignFormat.A4.height,
    val pages: List<DesignPage> = listOf(DesignPage()),
    /** Template it was made from (for the gallery). */
    val template: String = "",
    /** The chat it was made in (to change it there). */
    val chatId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val screen: Boolean get() = DesignFormat.byId(format)?.screen ?: false

    fun page(index: Int): DesignPage = pages.getOrElse(index) { pages.first() }

    /** All elements of all pages with the page index. */
    fun allElements(): List<Pair<Int, DesignElement>> = pages.flatMapIndexed { i, p -> p.elements.map { i to it } }

    fun mapElements(transform: (DesignElement) -> DesignElement): Design = copy(pages = pages.map { p -> p.copy(elements = p.elements.map(transform)) })
}

/** Page formats of the designs. */
data class DesignFormat(val id: String, val label: String, val width: Float, val height: Float, val screen: Boolean = false) {
    /** Pixels of screen formats (96 dpi). */
    val pxWidth: Int get() = Math.round(width / MM_PER_PX)
    val pxHeight: Int get() = Math.round(height / MM_PER_PX)

    val sizeLabel: String
        get() = if (screen) "$pxWidth × $pxHeight px" else "${fmt(width)} × ${fmt(height)} mm"

    companion object {
        /** 1 px at 96 dpi in mm. */
        const val MM_PER_PX = 25.4f / 96f
        const val MM_PER_PT = 25.4f / 72f

        private fun fmt(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(java.util.Locale.GERMANY, v)
        private fun px(id: String, label: String, w: Int, h: Int) = DesignFormat(id, label, w * MM_PER_PX, h * MM_PER_PX, screen = true)

        val A4 = DesignFormat("a4", "A4 hoch", 210f, 297f)
        val ALL = listOf(
            A4,
            DesignFormat("a4-quer", "A4 quer", 297f, 210f),
            DesignFormat("a5", "A5 hoch (Flyer)", 148f, 210f),
            DesignFormat("a5-quer", "A5 quer (Einladung)", 210f, 148f),
            DesignFormat("din-lang", "DIN lang (Flyer)", 99f, 210f),
            DesignFormat("a6-quer", "A6 quer (Postkarte)", 148f, 105f),
            DesignFormat("a3", "A3 hoch (Aushang)", 297f, 420f),
            DesignFormat("a2", "A2 hoch (Poster)", 420f, 594f),
            DesignFormat("a1", "A1 hoch (Tagungsposter)", 594f, 841f),
            DesignFormat("a1-quer", "A1 quer (Tagungsposter)", 841f, 594f),
            DesignFormat("a0", "A0 hoch (Tagungsposter)", 841f, 1189f),
            px("post", "Social Media: Beitrag", 1080, 1080),
            px("post-hoch", "Social Media: Beitrag hoch", 1080, 1350),
            px("story", "Social Media: Story", 1080, 1920),
            px("banner", "OPAL-Kursbanner", 1500, 300),
            px("video", "Titelbild für Lehrvideos", 1920, 1080),
            px("poster-16-9", "Digitales Poster 16:9 (Bildschirm)", 1920, 1080),
            DesignFormat("folie", "Folie 16:9", 338.67f, 190.5f),
        )

        fun byId(id: String?): DesignFormat? = ALL.firstOrNull { it.id == id }
    }
}

/**
 * Brand-Kit (AP-24) from the CD package 2025 of TU Dresden: the „TUD Farben“ (primary and secondary colours), the house
 * font Noto Sans (headings Medium), the logos in every official variant and the Shape.
 */
object DesignBrand {
    /** Name and colour, in the order of the colour field (the palette of the official presentation template). */
    val COLORS: List<Pair<String, String>> = TudBrand.PALETTE

    /** The house font of TU Dresden; the Windows app brings it along (resources/fonts). */
    const val FONT = "Noto Sans"
    const val LOGO_BLUE = "logo:tud"
    const val LOGO_WHITE = "logo:tud-weiss"

    /** Width / height of the horizontal TUD logo without its protection zone (official SVG). */
    const val LOGO_RATIO = 2.9739f

    /** An official logo: [src] for [DesignElement.src], file name of the SVG and width / height without protection zone. */
    data class Logo(val src: String, val label: String, val variant: String, val ratio: Float)

    /** The logos of the CD package: horizontal, vertical, short („TUD“) and the mark alone, in blue, white and black. */
    val LOGOS: List<Logo> = buildList {
        for ((farbe, name) in listOf("blau" to "blau", "weiss" to "weiß", "schwarz" to "schwarz")) {
            add(Logo(if (farbe == "blau") LOGO_BLUE else if (farbe == "weiss") LOGO_WHITE else "logo:tud-horizontal-schwarz-de", "Horizontal, $name", "horizontal-$farbe-de", LOGO_RATIO))
            add(Logo("logo:tud-horizontal-$farbe-en", "Horizontal englisch, $name", "horizontal-$farbe-en", 3.0024f))
            add(Logo("logo:tud-vertikal-$farbe-de", "Vertikal, $name", "vertikal-$farbe-de", 0.8199f))
            add(Logo("logo:tud-vertikal-$farbe-en", "Vertikal englisch, $name", "vertikal-$farbe-en", 0.9100f))
            add(Logo("logo:tud-kurz-$farbe", "Kurz (TUD), $name", "kurz-$farbe", 3.1179f))
            add(Logo("logo:tud-bildmarke-$farbe", "Bildmarke, $name", "bildmarke-$farbe", 1f))
        }
    }

    /** The official logo behind [src] (also the older ids "logo:tud" and "logo:tud-weiss"), or null for own logos. */
    fun logo(src: String): Logo? = LOGOS.firstOrNull { it.src == src }

    fun isHex(s: String) = Regex("#[0-9a-fA-F]{6}").matches(s.trim())

    /** "#abc", "abcdef", a name of [COLORS] („Brillantblau“, „Türkis 1“) → "#RRGGBB"; null if unknown. */
    fun color(value: String?): String? {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (isHex(v)) return v.uppercase()
        if (Regex("[0-9a-fA-F]{6}").matches(v)) return "#" + v.uppercase()
        if (Regex("#?[0-9a-fA-F]{3}").matches(v)) return "#" + v.removePrefix("#").map { "$it$it" }.joinToString("").uppercase()
        val n = v.lowercase().replace("ß", "ss").replace("tud-", "").replace("tud ", "").trim()
        fun norm(x: String) = x.lowercase().replace("ß", "ss")
        COLORS.firstOrNull { norm(it.first) == n }?.let { return it.second }
        // Without a step: the strong one („Türkis“ → Türkis 1); „hellblau“ → Blau 2.
        COLORS.firstOrNull { norm(it.first) == "$n 1" }?.let { return it.second }
        return when (n) {
            "blau", "tud", "tud-blau", "brillant", "primärblau" -> TudBrand.BLUE
            "hellblau" -> TudBrand.LIGHT_BLUE
            "mittelblau" -> TudBrand.MID_BLUE
            "nachtblau" -> TudBrand.NIGHT_BLUE
            "weiss", "white" -> "#FFFFFF"
            "black" -> "#000000"
            "grey", "gray" -> TudBrand.GREY_100
            else -> COLORS.firstOrNull { norm(it.first).startsWith(n) }?.second
        }
    }
}

fun newDesignId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * The designs (Erstellen › Meine Designs): `designs/<id>/design.json` plus the pictures of the design in the same folder.
 * Kept in memory as a list (a few dozen designs), saved at once on every change.
 */
class DesignStore(files: File) {
    private val dir = File(files, "designs").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Design>> = _items.asStateFlow()

    private fun load(): List<Design> = dir.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { d ->
        runCatching { json.decodeFromString(Design.serializer(), AtomicFiles.read(File(d, "design.json")) ?: return@mapNotNull null) }.getOrNull()
    }.sortedByDescending { it.updatedAt }

    fun get(id: String): Design? = _items.value.firstOrNull { it.id == id }

    /** Folder of the design's pictures. */
    fun folder(id: String): File = File(dir, id.replace(Regex("[^A-Za-z0-9-]"), "_"))

    /** A picture of design [id] by its file name (null for logos and missing files). */
    fun asset(id: String, name: String): File? =
        name.takeIf { it.isNotBlank() && !it.startsWith("logo:") && !it.contains('/') && !it.contains('\\') }?.let { File(folder(id), it) }?.takeIf { it.isFile }

    /** Saves [bytes] as a picture of design [id]; returns its file name for [DesignElement.src]. */
    fun addAsset(id: String, bytes: ByteArray, extension: String = "png"): String {
        val name = "bild-" + UUID.randomUUID().toString().substring(0, 8) + "." + extension.lowercase().ifBlank { "png" }
        File(folder(id).apply { mkdirs() }, name).writeBytes(bytes)
        return name
    }

    /** Saves [design] (new or changed); [touch] sets the time of the change. */
    @Synchronized
    fun save(design: Design, touch: Boolean = true): Design {
        val d = if (touch) design.copy(updatedAt = System.currentTimeMillis()) else design
        AtomicFiles.write(File(folder(d.id).apply { mkdirs() }, "design.json"), json.encodeToString(Design.serializer(), d))
        _items.value = (_items.value.filter { it.id != d.id } + d).sortedByDescending { it.updatedAt }
        return d
    }

    @Synchronized
    fun delete(id: String) {
        folder(id).deleteRecursively()
        _items.value = _items.value.filter { it.id != id }
    }

    /** A copy with new ids (pictures copied along). */
    @Synchronized
    fun duplicate(id: String): Design? {
        val old = get(id) ?: return null
        val copy = old.copy(id = UUID.randomUUID().toString(), title = old.title + " (Kopie)", createdAt = System.currentTimeMillis())
        folder(old.id).listFiles().orEmpty().filter { it.isFile && it.name != "design.json" }.forEach { f ->
            runCatching { f.copyTo(File(folder(copy.id).apply { mkdirs() }, f.name), overwrite = true) }
        }
        return save(copy)
    }

    /** File name for exports: the title without characters Windows forbids. */
    fun fileName(d: Design, extension: String): String =
        d.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Design" }.take(80) + "." + extension
}
