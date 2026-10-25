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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

/** Look of a picture; the hint is added to the description in English, which image models follow best. */
enum class ImageStyle(val label: String, val hint: String) {
    NONE("Ohne", ""),
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

/** Formats with the size sent to the model. */
enum class ImageFormat(val label: String, val size: String, val ratio: Float) {
    SQUARE("1:1", "1024x1024", 1f),
    PORTRAIT("3:4", "768x1024", 0.75f),
    LANDSCAPE("4:3", "1024x768", 4f / 3f),
    STORY("9:16", "768x1344", 768f / 1344f),
    WIDE("16:9", "1344x768", 1344f / 768f),
}

private fun ratioOf(size: String): Float =
    size.split('x').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 2 && it[1] > 0 }?.let { it[0] / it[1] } ?: 1f

/** Ideas for the empty gallery: tap fills the description. */
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

class ImagesViewModel(private val c: AppContainer) : ViewModel() {
    val gallery = ImageGallery(File(c.appContext.filesDir, "images"))
    var prompt by mutableStateOf("")
    var style by mutableStateOf(ImageStyle.NONE)
    var format by mutableStateOf(ImageFormat.SQUARE)
    var count by mutableIntStateOf(1)
    var busy by mutableStateOf(false)
        private set
    /** Pictures still to come in the running request (placeholders). */
    var pending by mutableIntStateOf(0)
        private set
    var improving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null
    private var last: Triple<String, String, Int>? = null
    /** Survives the screen: finishing a deletion after "Rückgängig" was not used. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch { gallery.load() }
    }

    private fun fullPrompt(text: String, s: ImageStyle) = if (s.hint.isEmpty()) text else "$text, ${s.hint}"

    fun generate(model: String) {
        if (prompt.isBlank() || busy) return
        run(model, prompt.trim(), style, format.size, count)
    }

    /** Same description again (new variations), or the one of an existing picture. */
    fun again(image: GalleryImage) {
        if (busy) return
        val s = ImageStyle.entries.firstOrNull { it.label == image.style } ?: ImageStyle.NONE
        run(image.model, image.prompt, s, image.size, 1)
    }

    fun retry() {
        val (model, size, n) = last ?: return
        run(model, prompt.trim().ifBlank { return }, style, size, n)
    }

    private fun run(model: String, text: String, s: ImageStyle, size: String, n: Int) {
        busy = true
        error = null
        pending = n
        last = Triple(model, size, n)
        job = viewModelScope.launch {
            try {
                repeat(n) {
                    val images = c.llm.generateImage(model, fullPrompt(text, s), size)
                    images.forEach { bytes ->
                        gallery.add(GalleryImage(prompt = text, model = model, size = size, style = s.label.takeIf { s != ImageStyle.NONE }.orEmpty()), bytes)
                    }
                    pending = (pending - 1).coerceAtLeast(0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = NetErrors.describe(e)
            } finally {
                busy = false
                pending = 0
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(c: AppContainer, onBack: () -> Unit, onOpenChat: () -> Unit = {}) {
    val vm: ImagesViewModel = viewModel { ImagesViewModel(c) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    val images by vm.gallery.items.collectAsStateWithLifecycle()
    val imageModels = models.ofType(ModelType.IMAGE).ifEmpty { listOf(ModelCatalog.info(settings.imageModel)) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (favoritesOnly) images.filter { it.favorite } else images

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = { Text("Bilder erzeugen") },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Composer(vm, settings.imageModel, imageModels.map { it.id to if (it.aliasOf != null) "Standard" else it.displayName }) { id -> c.settings.update { it.copy(imageModel = id) } }
                }
            }
            if (vm.busy) {
                items(vm.pending.coerceAtLeast(1)) { Placeholder(vm.format.ratio) }
            }
            if (images.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Galerie", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("Alle  ${images.size}") })
                    FilterChip(
                        selected = favoritesOnly, onClick = { favoritesOnly = true },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("${images.count { it.favorite }}") },
                    )
                }
            }
            if (images.isEmpty() && !vm.busy) item(span = { GridItemSpan(maxLineSpan) }) { EmptyGallery { vm.prompt = it } }
            items(shown, key = { it.id }) { img ->
                Thumb(vm, img, onOpen = { open = img.id })
            }
        }
    }

    open?.let { id ->
        val img = images.firstOrNull { it.id == id }
        if (img == null) open = null
        else Viewer(
            vm, img, onOpenChat,
            onDismiss = { open = null },
            onUsePrompt = { vm.prompt = img.prompt; ImageStyle.entries.firstOrNull { it.label == img.style }?.let { vm.style = it }; open = null },
        )
    }
}

/** Description, style, format, number and model – and the button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(vm: ImagesViewModel, model: String, models: List<Pair<String, String>>, onModel: (String) -> Unit) {
    AppCard(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = vm.prompt,
                onValueChange = { vm.prompt = it },
                placeholder = { Text("Was soll zu sehen sein? Motiv, Umgebung, Stimmung …") },
                minLines = 3,
                maxLines = 8,
                trailingIcon = if (vm.prompt.isNotEmpty()) ({ IconButton(onClick = { vm.prompt = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Leeren") } }) else null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::improve, enabled = vm.prompt.isNotBlank() && !vm.improving) {
                    if (vm.improving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (vm.improving) "Wird verbessert …" else "Beschreibung verbessern")
                }
            }
            Label("Stil")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageStyle.entries.forEach { s ->
                    FilterChip(selected = vm.style == s, onClick = { vm.style = s }, label = { Text(s.label) })
                }
            }
            Label("Format")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageFormat.entries.forEach { f ->
                    FilterChip(
                        selected = vm.format == f, onClick = { vm.format = f },
                        leadingIcon = { RatioIcon(f.ratio, vm.format == f) },
                        label = { Text(f.label) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Anzahl")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..4).forEach { n -> FilterChip(selected = vm.count == n, onClick = { vm.count = n }, label = { Text("$n") }) }
                    }
                }
                if (models.size > 1) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Modell")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        models.forEach { (id, name) ->
                            FilterChip(selected = model == id, onClick = { onModel(id) }, label = { Text(name, maxLines = 1) })
                        }
                    }
                }
            }
            ModelCatalog.info(model).strengths.takeIf { it.isNotBlank() }?.let { HintText(it) }
            if (vm.busy) {
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Wird erzeugt … (${vm.count - vm.pending + 1} von ${vm.count}) · Stopp")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            } else {
                Button(onClick = { vm.generate(model) }, enabled = vm.prompt.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.count == 1) "Bild erzeugen" else "${vm.count} Bilder erzeugen")
                }
            }
            vm.error?.let { err ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::retry) { Text("Erneut versuchen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Grey box with a pulsing spark while a picture is being made. */
@Composable
private fun Placeholder(ratio: Float) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
        Box(contentAlignment = Alignment.Center) { ThinkingIndicator("Wird gemalt …") }
    }
}

@Composable
private fun EmptyGallery(onExample: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
        }
        Text("Noch keine Bilder", style = MaterialTheme.typography.titleMedium)
        HintText("Erzeugte Bilder bleiben hier gespeichert. Ideen zum Ausprobieren:")
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EXAMPLES.forEach { e ->
                AppCard(Modifier.fillMaxWidth(), onClick = { onExample(e) }) {
                    Text(e, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
    }
}

/** The picture from its file, decoded off the main thread; null while loading or if it cannot be read. */
@Composable
private fun rememberGalleryBitmap(vm: ImagesViewModel, img: GalleryImage): ImageBitmap? =
    produceState<ImageBitmap?>(null, img.id) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(vm.gallery.file(img).readBytes()) }.getOrNull() }
    }.value

@Composable
private fun Thumb(vm: ImagesViewModel, img: GalleryImage, onOpen: () -> Unit) {
    val bmp = rememberGalleryBitmap(vm, img)
    Box(
        Modifier
            .fillMaxWidth()
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

/** Full screen: zoom with two fingers (double tap resets), all actions for the picture. */
@Composable
private fun Viewer(vm: ImagesViewModel, img: GalleryImage, onOpenChat: () -> Unit, onDismiss: () -> Unit, onUsePrompt: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bmp = rememberGalleryBitmap(vm, img)
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
    }
    LaunchedEffect(img.id) { scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero }
    val date = remember(img.createdAt) {
        Instant.ofEpochMilli(img.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFF101114), contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Schließen", tint = Color.White) }
                    Text("$date · ${ModelCatalog.info(img.model).displayName}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
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
                        .pointerInput(img.id) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = androidx.compose.ui.geometry.Offset.Zero }) },
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
                    Text(
                        listOfNotNull(img.style.ifBlank { null }, ImageFormat.entries.firstOrNull { it.size == img.size }?.label ?: img.size).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
                    ViewerAction(Icons.Outlined.Refresh, "Variante") { vm.again(img); onDismiss() }
                    ViewerAction(Icons.Outlined.TextFields, "Beschreibung") { onUsePrompt() }
                    ViewerAction(Icons.Outlined.Delete, "Löschen") { vm.delete(img); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
