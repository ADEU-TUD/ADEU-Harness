package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.GalleryImage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelType

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
    val titles by vm.gallery.titles.collectAsStateWithLifecycle()
    val chats = remember(images, titles) { imageChats(images, titles) }
    var showChats by rememberSaveable { mutableStateOf(false) }
    val imageModels = models.ofType(ModelType.IMAGE).ifEmpty { listOf(ModelCatalog.info(settings.imageModel)) }
        .map { it.id to if (it.aliasOf != null) "Standard" else it.displayName }
    var galleryView by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<GalleryImage?>(null) }
    val turns = remember(images, vm.chat) {
        images.filter { it.chat == vm.chat }.groupBy { it.turnId }.map { (id, list) -> Turn(id, list.sortedBy { it.createdAt }) }.sortedBy { it.first.createdAt }
    }
    val listState = rememberLazyListState()
    val pending = vm.pending?.takeIf { it.chat == vm.chat }
    LaunchedEffect(turns.size, pending?.id, galleryView, vm.chat) {
        val last = turns.size + (if (pending != null) 1 else 0) - 1
        if (last >= 0 && !galleryView) listState.animateScrollToItem(last)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton(onBack) },
                title = {
                    Text(
                        if (galleryView) "Galerie" else chats.firstOrNull { it.key == vm.chat }?.title ?: "Neuer Bilder-Chat",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (!galleryView) {
                        IconButton(onClick = vm::newChat, enabled = !vm.busy && turns.isNotEmpty()) {
                            Icon(Icons.Outlined.AddComment, contentDescription = "Neuer Bilder-Chat")
                        }
                        IconButton(onClick = { showChats = true }) {
                            BadgedBox(badge = { if (chats.size > 1) Badge { Text("${chats.size}") } }) {
                                Icon(Icons.Outlined.History, contentDescription = "Verläufe")
                            }
                        }
                    }
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
                if (turns.isEmpty() && pending == null) item { Welcome { vm.prompt = it } }
                items(turns, key = { it.id }) { t ->
                    TurnView(vm, t, images, onOpen = { open = it.id }, onEdit = { img -> vm.editTarget = img; vm.regions = emptyList(); editing = img })
                }
                pending?.let { p -> item(key = "pending-" + p.id) { PendingView(vm, p) } }
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
                vm.openChat(img.chat)
                vm.prompt = img.prompt
                ImageStyle.entries.firstOrNull { it.label == img.style }?.let { vm.style = it }
                vm.size = img.size
                open = null
                galleryView = false
            },
            onEdit = { vm.openChat(img.chat); vm.editTarget = img; vm.regions = emptyList(); open = null; galleryView = false; editing = img },
        )
    }
    if (showChats) ChatsSheet(vm, chats, onDismiss = { showChats = false })
    editing?.let { img ->
        val bmp = rememberGalleryBitmap(vm, img)
        if (bmp != null) ImageEditor(
            bmp, vm.regions,
            onDone = { r -> vm.regions = r; editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** All conversations of the Bilder tab: open, rename, delete, start a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsSheet(vm: ImagesViewModel, chats: List<ImageChat>, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf<ImageChat?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bilder-Chats", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.newChat(); onDismiss() }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Neuer Chat")
                }
            }
            if (chats.isEmpty()) HintText("Noch keine Bilder.", Modifier.padding(20.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(chats, key = { it.key }) { ch ->
                    val active = ch.key == vm.chat
                    Surface(
                        onClick = { vm.openChat(ch.key); onDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                ch.cover?.let { c -> rememberGalleryBitmap(vm, c)?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                HintText("${ch.count} ${if (ch.count == 1) "Bild" else "Bilder"} · " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(ch.updatedAt)))
                            }
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menu = false; renaming = ch })
                                    DropdownMenuItem(text = { Text("Löschen", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; vm.deleteChat(ch.key) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    renaming?.let { ch ->
        TextPrompt("Bilder-Chat umbenennen", ch.title, onDismiss = { renaming = null }) { name ->
            vm.renameChat(ch.key, name)
            renaming = null
        }
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

internal fun details(style: String, size: String, count: Int) =
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
internal fun RatioIcon(ratio: Float, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        val w = if (ratio >= 1f) 16.dp else 16.dp * ratio
        val h = if (ratio >= 1f) 16.dp / ratio else 16.dp
        Box(Modifier.size(w, h).border(BorderStroke(1.5.dp, color), RoundedCornerShape(3.dp)))
    }
}

