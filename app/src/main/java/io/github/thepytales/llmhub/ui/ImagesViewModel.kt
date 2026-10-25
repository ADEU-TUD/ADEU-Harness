package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.GalleryImage
import io.github.thepytales.llmhub.data.ImageGallery
import io.github.thepytales.llmhub.net.NetErrors
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Look of a picture; the hint is added to the description in English, which image models follow best. */
enum class ImageStyle(val label: String, val hint: String) {
    NONE("Ohne Stil", ""),
    PHOTO("Foto", "photorealistic photograph, natural light, sharp focus, 35mm"),
    ILLUSTRATION("Illustration", "clean digital illustration, soft colors, friendly"),
    WATERCOLOR("Aquarell", "watercolor painting, soft washes, paper texture"),
    FLAT("Flach", "flat vector illustration, simple shapes, limited palette, no text"),
    ICON("Symbol", "minimal icon, centered, plain background, flat design"),
    RENDER("3D", "3D render, soft studio lighting, high detail"),
    SKETCH("Skizze", "pencil sketch, hand drawn, black and white"),
    COMIC("Comic", "comic style, bold outlines, vibrant colors"),
    SLIDE("Folie", "modern presentation background, plenty of empty space for text, subtle, professional"),
}

/** Resolutions to choose from, grouped by aspect ratio. Width and height are multiples of 16 (FLUX). */
val IMAGE_RESOLUTIONS: List<Pair<String, List<String>>> = listOf(
    "Quadrat 1:1" to listOf("512x512", "768x768", "1024x1024", "1536x1536", "2048x2048"),
    "Hochformat 3:4" to listOf("768x1024", "1152x1536", "1536x2048"),
    "Querformat 4:3" to listOf("1024x768", "1536x1152", "2048x1536"),
    "Hochformat 2:3" to listOf("832x1248", "1024x1536"),
    "Querformat 3:2" to listOf("1248x832", "1536x1024"),
    "Story 9:16" to listOf("720x1280", "768x1344", "1088x1920"),
    "Breitbild 16:9" to listOf("1280x720", "1344x768", "1920x1088"),
    "A4 hoch (≈150 dpi)" to listOf("1248x1760"),
    "Banner 3:1" to listOf("1536x512"),
)

internal const val MIN_SIDE = 256
internal const val MAX_SIDE = 2048

/** "1024x768" → (1024, 768). */
internal fun sides(size: String): Pair<Int, Int>? =
    size.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 }?.let { it[0] to it[1] }

internal fun ratioOf(size: String): Float = sides(size)?.let { (w, h) -> w.toFloat() / h.coerceAtLeast(1) } ?: 1f

/** "1024 × 768" for the screen. */
fun sizeLabel(size: String) = sides(size)?.let { (w, h) -> "$w × $h" } ?: size

/** Own width × height: within the limits and rounded to multiples of 16; null if not a number. */
fun cleanSide(value: String): Int? = value.toIntOrNull()?.coerceIn(MIN_SIDE, MAX_SIDE)?.let { (it + 8) / 16 * 16 }

/** Ideas for the empty conversation: tap fills the description. */
internal val EXAMPLES = listOf(
    "Ein Hörsaal voller Studierender, die an einem Projekt arbeiten, warmes Morgenlicht",
    "Das Dresdner Elbufer bei Sonnenuntergang im Stil eines Aquarells",
    "Symbol für eine Lernplattform: ein Buch, aus dem Ideen aufsteigen",
    "Hintergrund für eine Präsentationsfolie zum Thema Digitalisierung in der Schule",
)

internal const val IMPROVE_PROMPT =
    "Du verbesserst Beschreibungen für Bildgeneratoren (FLUX). Antworte nur mit der verbesserten Beschreibung auf " +
        "Englisch, ein Absatz, höchstens 80 Wörter: Motiv, Umgebung, Komposition, Licht, Farben und Stimmung. Behalte die " +
        "Absicht genau bei, erfinde keine Personen oder Marken dazu. Kein Text im Bild, außer er wird ausdrücklich verlangt. " +
        "Keine Erklärungen, keine Anführungszeichen."

/** A request that is running or failed: shown as the newest turn of the conversation. */
data class PendingTurn(
    val id: String,
    val prompt: String,
    val style: String,
    val size: String,
    val count: Int,
    val editOf: GalleryImage?,
    val edits: List<String>,
    val error: String? = null,
)

class ImagesViewModel(private val c: AppContainer) : ViewModel() {
    val gallery = ImageGallery(File(c.appContext.filesDir, "images"))
    var prompt by mutableStateOf("")
    var style by mutableStateOf(ImageStyle.NONE)
    var size by mutableStateOf("1024x1024")
    var count by mutableIntStateOf(1)
    /** Picture to rework and the marked areas ("Überarbeiten"). */
    var editTarget by mutableStateOf<GalleryImage?>(null)
    var regions by mutableStateOf<List<EditRegion>>(emptyList())
    var pending by mutableStateOf<PendingTurn?>(null)
        private set
    var improving by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var retryAction: (() -> Unit)? = null
    /** Survives the screen: finishing a deletion after "Rückgängig" was not used. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val busy get() = pending != null && pending?.error == null

    init {
        viewModelScope.launch { gallery.load() }
    }

    private fun fullPrompt(text: String, s: ImageStyle) = if (s.hint.isEmpty()) text else "$text, ${s.hint}"

    /** Sends what is in the input: a new picture, or the rework of [editTarget]. */
    fun send(model: String) {
        if (busy) return
        val target = editTarget
        if (target != null) {
            if (prompt.isBlank() && regions.none { it.strokes.isNotEmpty() && it.description.isNotBlank() }) return
            edit(model, target, regions, prompt.trim())
        } else {
            if (prompt.isBlank()) return
            create(model, prompt.trim(), style, size, count)
        }
        prompt = ""
        editTarget = null
        regions = emptyList()
    }

    /** Same description again (new variations). */
    fun again(image: GalleryImage) {
        if (busy) return
        val s = ImageStyle.entries.firstOrNull { it.label == image.style } ?: ImageStyle.NONE
        create(image.model, image.prompt, s, image.size, 1)
    }

    fun retry() = retryAction?.invoke()

    fun dismissError() {
        if (pending?.error != null) pending = null
    }

    private fun create(model: String, text: String, s: ImageStyle, size: String, n: Int) {
        val turn = PendingTurn(UUID.randomUUID().toString(), text, s.label.takeIf { s != ImageStyle.NONE }.orEmpty(), size, n, null, emptyList())
        retryAction = { create(model, text, s, size, n) }
        launchTurn(turn) {
            repeat(n) {
                c.llm.generateImage(model, fullPrompt(text, s), size).forEach { bytes ->
                    gallery.add(GalleryImage(prompt = text, model = model, size = size, style = turn.style, turnId = turn.id), bytes)
                }
            }
        }
    }

    private class EditInput(val image: ByteArray, val mask: ByteArray?, val width: Int, val height: Int)

    private fun edit(model: String, source: GalleryImage, marked: List<EditRegion>, note: String) {
        val summary = editSummary(marked)
        val turn = PendingTurn(UUID.randomUUID().toString(), note, source.style, source.size, 1, source, summary)
        retryAction = { edit(model, source, marked, note) }
        launchTurn(turn) {
            val input = withContext(Dispatchers.Default) {
                val bmp = decodeImage(gallery.file(source).readBytes()) ?: error("Das Bild lässt sich nicht lesen.")
                val mask = if (marked.any { it.strokes.isNotEmpty() }) encodePng(renderMask(bmp.width, bmp.height, marked)) else null
                EditInput(encodePng(bmp), mask, bmp.width, bmp.height)
            }
            val (withMask, withoutMask) = editPrompts(marked, note)
            val size = "${input.width}x${input.height}"
            c.llm.editImage(model, input.image, input.mask, withMask, withoutMask, size).forEach { bytes ->
                gallery.add(
                    GalleryImage(
                        prompt = note.ifBlank { source.prompt }, model = model, size = size, style = source.style,
                        turnId = turn.id, editOf = source.id, edits = summary,
                    ),
                    bytes,
                )
            }
        }
    }

    private fun launchTurn(turn: PendingTurn, work: suspend () -> Unit) {
        pending = turn
        job = viewModelScope.launch {
            try {
                work()
                pending = null
            } catch (e: CancellationException) {
                pending = null
                throw e
            } catch (e: Exception) {
                pending = turn.copy(error = NetErrors.describe(e))
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Lets the chat model turn a few words into a detailed description. */
    fun improve() {
        val text = prompt.trim()
        if (text.isEmpty() || improving) return
        improving = true
        viewModelScope.launch {
            try {
                val answer = c.llm.streamChat(
                    model = c.settings.current.defaultModel,
                    messages = listOf(ChatMessage(role = "system", content = IMPROVE_PROMPT), ChatMessage(role = "user", content = text)),
                    tools = null,
                    thinking = ThinkingLevel.OFF,
                ) { }.content
                val better = splitThinking(answer).second.trim().trim('"', '„', '“')
                if (better.isNotEmpty()) {
                    val before = prompt
                    prompt = better
                    Notices.show(Notices.Notice("Beschreibung verbessert", action = "Rückgängig", onAction = { prompt = before }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notices.error(NetErrors.describe(e), retry = ::improve)
            } finally {
                improving = false
            }
        }
    }

    fun toggleFavorite(image: GalleryImage) {
        viewModelScope.launch { gallery.update(image.copy(favorite = !image.favorite)) }
    }

    fun delete(image: GalleryImage) {
        gallery.hide(image.id)
        Notices.undo("Bild gelöscht", restore = { gallery.unhide(image) }, commit = { background.launch { gallery.commitDelete(image) } })
    }
}

