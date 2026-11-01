package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    NONE(trLater("Ohne Stil"), ""),
    PHOTO(trLater("Foto"), "photorealistic photograph, natural light, sharp focus, 35mm"),
    ILLUSTRATION(trLater("Illustration"), "clean digital illustration, soft colors, friendly"),
    WATERCOLOR(trLater("Aquarell"), "watercolor painting, soft washes, paper texture"),
    FLAT(trLater("Flach"), "flat vector illustration, simple shapes, limited palette, no text"),
    ICON(trLater("Symbol"), "minimal icon, centered, plain background, flat design"),
    RENDER("3D", "3D render, soft studio lighting, high detail"),
    SKETCH(trLater("Skizze"), "pencil sketch, hand drawn, black and white"),
    COMIC(trLater("Comic"), "comic style, bold outlines, vibrant colors"),
    SLIDE(trLater("Folie"), "modern presentation background, plenty of empty space for text, subtle, professional"),
}

/** Resolutions to choose from, grouped by aspect ratio. Width and height are multiples of 16 (FLUX). */
val IMAGE_RESOLUTIONS: List<Pair<String, List<String>>> = listOf(
    trLater("Quadrat 1:1") to listOf("512x512", "768x768", "1024x1024", "1536x1536", "2048x2048"),
    trLater("Hochformat 3:4") to listOf("768x1024", "1152x1536", "1536x2048"),
    trLater("Querformat 4:3") to listOf("1024x768", "1536x1152", "2048x1536"),
    trLater("Hochformat 2:3") to listOf("832x1248", "1024x1536"),
    trLater("Querformat 3:2") to listOf("1248x832", "1536x1024"),
    trLater("Story 9:16") to listOf("720x1280", "768x1344", "1088x1920"),
    trLater("Breitbild 16:9") to listOf("1280x720", "1344x768", "1920x1088"),
    "A4 hoch (≈150 dpi)" to listOf("1248x1760"),
    trLater("Banner 3:1") to listOf("1536x512"),
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
    trLater("Ein Hörsaal voller Studierender, die an einem Projekt arbeiten, warmes Morgenlicht"),
    trLater("Das Dresdner Elbufer bei Sonnenuntergang im Stil eines Aquarells"),
    trLater("Symbol für eine Lernplattform: ein Buch, aus dem Ideen aufsteigen"),
    trLater("Hintergrund für eine Präsentationsfolie zum Thema Digitalisierung in der Schule"),
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
    /** Conversation it was sent in. */
    val chat: String = ImageGallery.FIRST_CHAT,
)

/** One conversation of the Bilder tab, as listed under "Verläufe". */
data class ImageChat(val key: String, val title: String, val updatedAt: Long, val count: Int, val cover: GalleryImage?)

/** The conversations of [images], newest activity first; [titles] are own names. */
fun imageChats(images: List<GalleryImage>, titles: Map<String, String>): List<ImageChat> =
    images.groupBy { it.chat }.map { (key, list) ->
        val first = list.minByOrNull { it.createdAt }
        ImageChat(
            key = key,
            title = titles[key] ?: first?.prompt?.trim()?.lineSequence()?.firstOrNull()?.take(60)?.ifBlank { null } ?: tr("Bilder"),
            updatedAt = list.maxOf { it.createdAt },
            count = list.size,
            cover = list.maxByOrNull { it.createdAt },
        )
    }.sortedByDescending { it.updatedAt }

/** Instruction for translating the wishes of "Überarbeiten" into English for the image model. */
internal const val TRANSLATE_EDIT_PROMPT =
    "Übersetze jede Zeile ins Englische als kurze, eindeutige Anweisung für ein Bildbearbeitungsmodell (z. B. „blume“ → " +
        "„replace it with a blooming flower“, „heller“ → „make it brighter“). Behalte die Nummer am Zeilenanfang („1: …“). " +
        "Antworte nur mit den übersetzten Zeilen, ohne Erklärungen."

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
    /** The conversation on screen; new pictures go there. */
    var chat by mutableStateOf(ImageGallery.FIRST_CHAT)
    var improving by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var retryAction: (() -> Unit)? = null
    /** Survives the screen: finishing a deletion after "Rückgängig" was not used. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val busy get() = pending != null && pending?.error == null

    init {
        viewModelScope.launch {
            gallery.load()
            // Continue the conversation used last.
            gallery.items.value.firstOrNull()?.let { chat = it.chat }
        }
    }

    /** Starts an empty conversation (the old ones stay under "Verläufe"). */
    fun newChat() {
        if (busy) return
        dismissError()
        chat = UUID.randomUUID().toString().take(12)
        editTarget = null
        regions = emptyList()
        prompt = ""
    }

    fun openChat(key: String) {
        if (busy && key != chat) return
        dismissError()
        chat = key
        editTarget = null
        regions = emptyList()
    }

    fun renameChat(key: String, title: String) {
        viewModelScope.launch { gallery.rename(key, title) }
    }

    /** Deletes a whole conversation with its pictures (can be undone for a few seconds). */
    fun deleteChat(key: String) {
        val removed = gallery.items.value.filter { it.chat == key }
        removed.forEach { gallery.hide(it.id) }
        if (chat == key) chat = gallery.items.value.firstOrNull()?.chat ?: UUID.randomUUID().toString().take(12)
        Notices.undo(
            tr("Verlauf gelöscht ({0} Bilder)", removed.size),
            restore = { removed.forEach { gallery.unhide(it) } },
            commit = { background.launch { removed.forEach { gallery.commitDelete(it) } } },
        )
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
        val target = chat
        val turn = PendingTurn(UUID.randomUUID().toString(), text, s.label.takeIf { s != ImageStyle.NONE }.orEmpty(), size, n, null, emptyList(), chat = target)
        retryAction = { create(model, text, s, size, n) }
        launchTurn(turn) {
            repeat(n) {
                c.llm.generateImage(model, fullPrompt(text, s), size).forEach { bytes ->
                    gallery.add(GalleryImage(prompt = text, model = model, size = size, style = turn.style, turnId = turn.id, chatId = target), bytes)
                }
            }
        }
    }

    private class EditInput(val image: ByteArray, val mask: ByteArray?, val width: Int, val height: Int)

    private fun edit(model: String, source: GalleryImage, marked: List<EditRegion>, note: String) {
        val summary = editSummary(marked)
        val target = chat
        val turn = PendingTurn(UUID.randomUUID().toString(), note, source.style, source.size, 1, source, summary, chat = target)
        retryAction = { edit(model, source, marked, note) }
        launchTurn(turn) {
            val input = withContext(Dispatchers.Default) {
                val bmp = decodeImage(gallery.file(source).readBytes()) ?: error(tr("Das Bild lässt sich nicht lesen."))
                val mask = if (marked.any { it.strokes.isNotEmpty() }) maskPng(bmp.width, bmp.height, marked) else null
                EditInput(encodePng(bmp), mask, bmp.width, bmp.height)
            }
            val (english, noteEnglish) = translate(marked, note)
            val (withMask, withoutMask) = editPrompts(english, noteEnglish)
            val size = "${input.width}x${input.height}"
            c.llm.editImage(model, input.image, input.mask, withMask, withoutMask, size).forEach { bytes ->
                gallery.add(
                    GalleryImage(
                        prompt = note.ifBlank { source.prompt }, model = model, size = size, style = source.style,
                        turnId = turn.id, editOf = source.id, edits = summary, chatId = target,
                    ),
                    bytes,
                )
            }
        }
    }

    /**
     * The wishes in English (image models understand German poorly; a single word like „blume“ is easily ignored).
     * Uses the chat model; without an answer the original words are sent.
     */
    private suspend fun translate(marked: List<EditRegion>, note: String): Pair<List<EditRegion>, String> {
        val used = marked.filter { it.strokes.isNotEmpty() && it.description.isNotBlank() }
        val lines = used.map { "${it.index + 1}: ${it.description.trim().replace('\n', ' ')}" } + listOfNotNull(note.trim().takeIf { it.isNotEmpty() }?.let { "0: ${it.replace('\n', ' ')}" })
        if (lines.isEmpty()) return marked to note
        val answer = runCatching {
            c.llm.streamChat(
                model = c.settings.current.defaultModel,
                messages = listOf(ChatMessage(role = "system", content = TRANSLATE_EDIT_PROMPT), ChatMessage(role = "user", content = lines.joinToString("\n"))),
                tools = null,
                thinking = ThinkingLevel.OFF,
            ) { }.content
        }.getOrNull()?.let { splitThinking(it).second } ?: return marked to note
        val byNumber = answer.lines().mapNotNull { l ->
            val m = Regex("^\\s*(\\d+)\\s*[:.)-]\\s*(.+)$").find(l) ?: return@mapNotNull null
            m.groupValues[1].toInt() to m.groupValues[2].trim().trim('"', '„', '“')
        }.toMap()
        val regions = marked.map { r -> byNumber[r.index + 1]?.takeIf { it.isNotBlank() && r.description.isNotBlank() }?.let { r.copy(description = it) } ?: r }
        return regions to (byNumber[0]?.takeIf { note.isNotBlank() } ?: note)
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
                    Notices.show(Notices.Notice(tr("Beschreibung verbessert"), action = tr("Rückgängig"), onAction = { prompt = before }))
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
        Notices.undo(tr("Bild gelöscht"), restore = { gallery.unhide(image) }, commit = { background.launch { gallery.commitDelete(image) } })
    }
}

