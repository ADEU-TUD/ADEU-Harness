package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.GalleryImage
import io.github.thepytales.llmhub.data.ImageGallery
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

private const val MIN_SIDE = 256
private const val MAX_SIDE = 2048

/** "1024x768" → (1024, 768). */
private fun sides(size: String): Pair<Int, Int>? =
    size.lowercase().split('x', '×').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 }?.let { it[0] to it[1] }

private fun ratioOf(size: String): Float = sides(size)?.let { (w, h) -> w.toFloat() / h.coerceAtLeast(1) } ?: 1f

/** "1024 × 768" for the screen. */
fun sizeLabel(size: String) = sides(size)?.let { (w, h) -> "$w × $h" } ?: size

/** Own width × height: within the limits and rounded to multiples of 16; null if not a number. */
fun cleanSide(value: String): Int? = value.toIntOrNull()?.coerceIn(MIN_SIDE, MAX_SIDE)?.let { (it + 8) / 16 * 16 }

/** Ideas for the empty conversation: tap fills the description. */
private val EXAMPLES = listOf(
    "Ein Hörsaal voller Studierender, die an einem Projekt arbeiten, warmes Morgenlicht",
    "Das Dresdner Elbufer bei Sonnenuntergang im Stil eines Aquarells",
    "Symbol für eine Lernplattform: ein Buch, aus dem Ideen aufsteigen",
    "Hintergrund für eine Präsentationsfolie zum Thema Digitalisierung in der Schule",
)

private const val IMPROVE_PROMPT =
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

/** A finished turn of the conversation: the request and the pictures it brought. */
private class Turn(val id: String, val images: List<GalleryImage>) {
    val first get() = images.first()
}

/**
 * "Bilder erzeugen" as a conversation, like Gemini: the description goes in at the bottom, each request with its
 * pictures appears as a turn. Pictures can be reworked by marking areas. A second view shows all pictures as grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(c: AppContainer, onBack: () -> Unit, onOpenChat: () -> Unit = {}) {
    val vm: ImagesViewModel = viewModel { ImagesViewModel(c) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    val images by vm.gallery.items.collectAsStateWithLifecycle()
    val imageModels = models.ofType(ModelType.IMAGE).ifEmpty { listOf(ModelCatalog.info(settings.imageModel)) }
        .map { it.id to if (it.aliasOf != null) "Standard" else it.displayName }
    var galleryView by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<GalleryImage?>(null) }
    val turns = remember(images) {
        images.groupBy { it.turnId }.map { (id, list) -> Turn(id, list.sortedBy { it.createdAt }) }.sortedBy { it.first.createdAt }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size, vm.pending?.id, galleryView) {
        val last = turns.size + (if (vm.pending != null) 1 else 0) - 1
        if (last >= 0 && !galleryView) listState.animateScrollToItem(last)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = { Text(if (galleryView) "Galerie" else "Bilder") },
                actions = {
                    IconButton(onClick = { galleryView = !galleryView }) {
                        Icon(
                            if (galleryView) Icons.Outlined.Forum else Icons.Outlined.Collections,
                            contentDescription = if (galleryView) "Als Verlauf zeigen" else "Alle Bilder als Galerie",
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!galleryView) {
                Box(Modifier.fillMaxWidth().imePadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                    Composer(
                        vm, settings.imageModel, imageModels,
                        onModel = { id -> c.settings.update { it.copy(imageModel = id) } },
                        onEditAreas = { editing = vm.editTarget },
                    )
                }
            }
        },
    ) { pad ->
        if (galleryView) {
            GalleryGrid(vm, images, pad, onOpen = { open = it.id })
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (turns.isEmpty() && vm.pending == null) item { Welcome { vm.prompt = it } }
                items(turns, key = { it.id }) { t ->
                    TurnView(vm, t, images, onOpen = { open = it.id }, onEdit = { img -> vm.editTarget = img; vm.regions = emptyList(); editing = img })
                }
                vm.pending?.let { p -> item(key = "pending-" + p.id) { PendingView(vm, p) } }
            }
        }
    }

    open?.let { id ->
        val img = images.firstOrNull { it.id == id }
        if (img == null) open = null
        else Viewer(
            vm, img, onOpenChat,
            onDismiss = { open = null },
            onUsePrompt = {
                vm.prompt = img.prompt
                ImageStyle.entries.firstOrNull { it.label == img.style }?.let { vm.style = it }
                vm.size = img.size
                open = null
                galleryView = false
            },
            onEdit = { vm.editTarget = img; vm.regions = emptyList(); open = null; galleryView = false; editing = img },
        )
    }
    editing?.let { img ->
        val bmp = rememberGalleryBitmap(vm, img)
        if (bmp != null) ImageEditor(
            bmp, vm.regions,
            onDone = { r -> vm.regions = r; editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Welcome(onExample: (String) -> Unit) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
        }
        Text("Was soll ich malen?", style = MaterialTheme.typography.headlineSmall)
        HintText("Beschreibe unten ein Bild. Fertige Bilder lassen sich überarbeiten: Stellen markieren und sagen, was dort anders werden soll.")
        Spacer(Modifier.size(4.dp))
        EXAMPLES.forEach { e ->
            AppCard(Modifier.fillMaxWidth(), onClick = { onExample(e) }) {
                Text(e, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

/** The user's request as a bubble on the right (with the reworked picture and its marked areas). */
@Composable
private fun RequestBubble(vm: ImagesViewModel, prompt: String, details: String, editOf: GalleryImage?, edits: List<String>) {
    Box(Modifier.widthIn(max = 760.dp).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), modifier = Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (editOf != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bmp = rememberGalleryBitmap(vm, editOf)
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Überarbeiten", style = MaterialTheme.typography.labelLarge)
                    }
                    edits.forEachIndexed { i, e ->
                        val n = e.substringBefore(' ').toIntOrNull()?.minus(1) ?: i
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(REGION_COLORS[n % REGION_COLORS.size], CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(e.substringAfter(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (prompt.isNotBlank()) Text(prompt, style = MaterialTheme.typography.bodyLarge)
                Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun details(style: String, size: String, count: Int) =
    listOfNotNull(style.ifBlank { null }, sizeLabel(size), if (count > 1) "$count Bilder" else null).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TurnView(vm: ImagesViewModel, t: Turn, all: List<GalleryImage>, onOpen: (GalleryImage) -> Unit, onEdit: (GalleryImage) -> Unit) {
    val first = t.first
    val source = first.editOf?.let { id -> all.firstOrNull { it.id == id } }
    // A rework without own words repeats the description of the source; it is not shown again.
    val prompt = if (first.editOf != null && first.prompt == source?.prompt) "" else first.prompt
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RequestBubble(vm, prompt, details(first.style, first.size, t.images.size), source, first.edits)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val single = t.images.size == 1
            t.images.forEach { img ->
                Thumb(vm, img, Modifier.widthIn(max = if (single) 420.dp else 240.dp).fillMaxWidth(if (single) 0.85f else 0.48f), onOpen = { onOpen(img) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.again(first) }, enabled = !vm.busy) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Noch eins")
            }
            if (t.images.size == 1) TextButton(onClick = { onEdit(first) }, enabled = !vm.busy) {
                Icon(Icons.Outlined.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Überarbeiten")
            }
        }
    }
}

@Composable
private fun PendingView(vm: ImagesViewModel, p: PendingTurn) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RequestBubble(vm, p.prompt, details(p.style, p.size, p.count), p.editOf, p.edits)
        if (p.error != null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = vm::retry) { Text("Erneut versuchen") }
                        TextButton(onClick = vm::dismissError) { Text("Verwerfen") }
                    }
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.85f).aspectRatio(ratioOf(p.size)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ThinkingIndicator(if (p.editOf != null) "Wird überarbeitet …" else "Wird gemalt …")
                        TextButton(onClick = vm::cancel) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stopp")
                        }
                    }
                }
            }
        }
    }
}

/** Input at the bottom: description, options as chips, send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(vm: ImagesViewModel, model: String, models: List<Pair<String, String>>, onModel: (String) -> Unit, onEditAreas: () -> Unit) {
    var styleMenu by remember { mutableStateOf(false) }
    var countMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var sizeSheet by remember { mutableStateOf(false) }
    val target = vm.editTarget
    Surface(
        color = LocalChatLook.current.card, border = LocalChatLook.current.cardBorder, shadowElevation = 3.dp,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            if (target != null) {
                Row(Modifier.padding(start = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Brush, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    val marked = vm.regions.count { it.strokes.isNotEmpty() }
                    Text(
                        if (marked == 0) "Überarbeiten – ganzes Bild" else "Überarbeiten – $marked Bereich${if (marked == 1) "" else "e"} markiert",
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEditAreas) { Text("Markieren") }
                    IconButton(onClick = { vm.editTarget = null; vm.regions = emptyList() }) { Icon(Icons.Outlined.Close, contentDescription = "Überarbeiten beenden") }
                }
            }
            TextField(
                value = vm.prompt,
                onValueChange = { vm.prompt = it },
                placeholder = { Text(if (target != null) "Was soll sich sonst noch ändern? (optional)" else "Beschreibe ein Bild …") },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (target == null) {
                        Box {
                            OptionChip(Icons.Outlined.Palette, vm.style.label) { styleMenu = true }
                            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                                ImageStyle.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.style = s; styleMenu = false }) }
                            }
                        }
                        OptionChip(Icons.Outlined.AspectRatio, sizeLabel(vm.size)) { sizeSheet = true }
                        Box {
                            OptionChip(Icons.Outlined.Collections, if (vm.count == 1) "1 Bild" else "${vm.count} Bilder") { countMenu = true }
                            DropdownMenu(expanded = countMenu, onDismissRequest = { countMenu = false }) {
                                (1..4).forEach { n -> DropdownMenuItem(text = { Text(if (n == 1) "1 Bild" else "$n Bilder") }, onClick = { vm.count = n; countMenu = false }) }
                            }
                        }
                    }
                    if (models.size > 1) Box {
                        OptionChip(Icons.Outlined.AutoAwesome, models.firstOrNull { it.first == model }?.second ?: model) { modelMenu = true }
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            models.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(name)
                                            ModelCatalog.info(id).strengths.takeIf { it.isNotBlank() }?.let { HintText(it) }
                                        }
                                    },
                                    onClick = { onModel(id); modelMenu = false },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = vm::improve, enabled = vm.prompt.isNotBlank() && !vm.improving) {
                    if (vm.improving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.AutoAwesome, contentDescription = "Beschreibung verbessern")
                }
                val canSend = !vm.busy && (vm.prompt.isNotBlank() || target != null && vm.regions.any { it.strokes.isNotEmpty() && it.description.isNotBlank() })
                Surface(
                    onClick = { vm.send(model) }, enabled = canSend, shape = CircleShape,
                    color = if (canSend) LocalChatLook.current.action else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canSend) LocalChatLook.current.onAction else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = if (target != null) "Überarbeiten" else "Erzeugen", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    if (sizeSheet) ModalBottomSheet(onDismissRequest = { sizeSheet = false }) {
        ResolutionPicker(vm.size, onPick = { vm.size = it; sizeSheet = false })
    }
}

@Composable
private fun OptionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label, maxLines = 1) }, leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) })
}

/** All resolutions by aspect ratio, plus width × height of one's own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolutionPicker(current: String, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Auflösung", style = MaterialTheme.typography.titleMedium)
        HintText("Größere Bilder brauchen länger. Eigene Größen werden auf Vielfache von 16 gerundet.")
        IMAGE_RESOLUTIONS.forEach { (group, sizes) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sizes.forEach { s ->
                        FilterChip(selected = current == s, onClick = { onPick(s) }, leadingIcon = { RatioIcon(ratioOf(s), current == s) }, label = { Text(sizeLabel(s)) })
                    }
                }
            }
        }
        val known = IMAGE_RESOLUTIONS.any { current in it.second }
        FilterChip(selected = !known, onClick = { custom = true }, label = { Text(if (known) "Eigene Größe …" else "Eigene: ${sizeLabel(current)}") })
    }
    if (custom) CustomSizeDialog(current, onPick = { custom = false; onPick(it) }, onDismiss = { custom = false })
}

@Composable
private fun CustomSizeDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val (w0, h0) = sides(current) ?: (1024 to 1024)
    var w by remember { mutableStateOf(w0.toString()) }
    var h by remember { mutableStateOf(h0.toString()) }
    val wi = cleanSide(w)
    val hi = cleanSide(h)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigene Größe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        w, { w = it.filter(Char::isDigit).take(4) }, label = { Text("Breite") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Text("×")
                    OutlinedTextField(
                        h, { h = it.filter(Char::isDigit).take(4) }, label = { Text("Höhe") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                HintText("$MIN_SIDE bis $MAX_SIDE Pixel je Seite." + if (wi != null && hi != null) " Wird zu $wi × $hi." else "")
            }
        },
        confirmButton = { TextButton(enabled = wi != null && hi != null, onClick = { onPick("${wi}x$hi") }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Small frame in the proportions of a format. */
@Composable
private fun RatioIcon(ratio: Float, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        val w = if (ratio >= 1f) 16.dp else 16.dp * ratio
        val h = if (ratio >= 1f) 16.dp / ratio else 16.dp
        Box(Modifier.size(w, h).border(BorderStroke(1.5.dp, color), RoundedCornerShape(3.dp)))
    }
}

/** All pictures as a grid, with favorites. */
@Composable
private fun GalleryGrid(vm: ImagesViewModel, images: List<GalleryImage>, pad: PaddingValues, onOpen: (GalleryImage) -> Unit) {
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val shown = (if (favoritesOnly) images.filter { it.favorite } else images)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("Alle  ${images.size}") })
                FilterChip(
                    selected = favoritesOnly, onClick = { favoritesOnly = true },
                    leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Favoriten  ${images.count { it.favorite }}") },
                )
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            HintText(if (favoritesOnly) "Noch keine Favoriten – ☆ auf einem Bild antippen." else "Noch keine Bilder.")
        }
        items(shown, key = { it.id }) { img -> Thumb(vm, img, Modifier.fillMaxWidth(), onOpen = { onOpen(img) }) }
    }
}

/** The picture from its file, decoded off the main thread; null while loading or if it cannot be read. */
@Composable
private fun rememberGalleryBitmap(vm: ImagesViewModel, img: GalleryImage): ImageBitmap? =
    produceState<ImageBitmap?>(null, img.id) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(vm.gallery.file(img).readBytes()) }.getOrNull() }
    }.value

@Composable
private fun Thumb(vm: ImagesViewModel, img: GalleryImage, modifier: Modifier, onOpen: () -> Unit) {
    val bmp = rememberGalleryBitmap(vm, img)
    Box(
        modifier
            .aspectRatio(ratioOf(img.size))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen),
    ) {
        bmp?.let { Image(it, contentDescription = img.prompt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Surface(
            onClick = { vm.toggleFavorite(img) },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.35f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (img.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (img.favorite) "Aus Favoriten entfernen" else "Zu Favoriten",
                    tint = if (img.favorite) Color(0xFFFFC94D) else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Full screen: zoom with two fingers (double tap switches), all actions for the picture. */
@Composable
private fun Viewer(
    vm: ImagesViewModel,
    img: GalleryImage,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
    onUsePrompt: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bmp = rememberGalleryBitmap(vm, img)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    LaunchedEffect(img.id) { scale = 1f; offset = Offset.Zero }
    val date = remember(img.createdAt) {
        Instant.ofEpochMilli(img.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF101114), contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Schließen", tint = Color.White) }
                    Text(
                        "$date · ${ModelCatalog.info(img.model).displayName}",
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.toggleFavorite(img) }) {
                        Icon(
                            if (img.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (img.favorite) "Aus Favoriten entfernen" else "Zu Favoriten",
                            tint = if (img.favorite) Color(0xFFFFC94D) else Color.White,
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .transformable(transform)
                        .pointerInput(img.id) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp == null) CircularProgressIndicator(color = Color.White)
                    else Image(
                        bmp, contentDescription = img.prompt, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                    )
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(img.prompt, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(details(img.style, img.size, 1), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ViewerAction(Icons.Outlined.Brush, "Überarbeiten", onEdit)
                    ViewerAction(Icons.Outlined.FileDownload, SAVE_IMAGE_LABEL) { saveImageToDevice(context, vm.gallery.file(img)) }
                    ViewerAction(Icons.Outlined.Share, "Teilen") { shareImage(context, vm.gallery.file(img)) }
                    ViewerAction(Icons.AutoMirrored.Outlined.Chat, "Im Chat") {
                        scope.launch {
                            val url = imageDataUrl(context, vm.gallery.file(img))
                            if (url == null) Notices.error("Das Bild ließ sich nicht anhängen.")
                            else {
                                ChatHandoff.pending.value = ChatHandoff.Content("", emptyList(), images = listOf(url))
                                onDismiss()
                                onOpenChat()
                            }
                        }
                    }
                    ViewerAction(Icons.Outlined.Refresh, "Noch eins") { vm.again(img); onDismiss() }
                    ViewerAction(Icons.Outlined.TextFields, "Beschreibung") { onUsePrompt() }
                    ViewerAction(Icons.Outlined.Delete, "Löschen") { vm.delete(img); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
