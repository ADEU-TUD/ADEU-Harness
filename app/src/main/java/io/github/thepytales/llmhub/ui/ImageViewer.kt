package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thepytales.llmhub.data.GalleryImage
import io.github.thepytales.llmhub.data.ModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pictures of "Bilder erzeugen": thumbnails, the gallery grid and the full-screen view. */

/** All pictures as a grid, with favorites. */
@Composable
internal fun GalleryGrid(vm: ImagesViewModel, images: List<GalleryImage>, pad: PaddingValues, onOpen: (GalleryImage) -> Unit) {
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
                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text(tr("Alle  {0}", images.size)) })
                FilterChip(
                    selected = favoritesOnly, onClick = { favoritesOnly = true },
                    leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(tr("Favoriten  {0}", images.count { it.favorite })) },
                )
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            HintText(if (favoritesOnly) tr("Noch keine Favoriten – ☆ auf einem Bild antippen.") else tr("Noch keine Bilder."))
        }
        items(shown, key = { it.id }) { img -> Thumb(vm, img, Modifier.fillMaxWidth(), onOpen = { onOpen(img) }) }
    }
}

/** The picture from its file, decoded off the main thread; null while loading or if it cannot be read. */
@Composable
internal fun rememberGalleryBitmap(vm: ImagesViewModel, img: GalleryImage): ImageBitmap? =
    produceState<ImageBitmap?>(null, img.id) {
        value = withContext(Dispatchers.IO) { runCatching { decodeImage(vm.gallery.file(img).readBytes()) }.getOrNull() }
    }.value

@Composable
internal fun Thumb(vm: ImagesViewModel, img: GalleryImage, modifier: Modifier, onOpen: () -> Unit) {
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
                    contentDescription = if (img.favorite) tr("Aus Favoriten entfernen") else tr("Zu Favoriten"),
                    tint = if (img.favorite) Color(0xFFFFC94D) else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Full screen: zoom with two fingers (double tap switches), all actions for the picture. */
@Composable
internal fun Viewer(
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
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = tr("Schließen"), tint = Color.White) }
                    Text(
                        "$date · ${ModelCatalog.info(img.model).displayName}",
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.toggleFavorite(img) }) {
                        Icon(
                            if (img.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (img.favorite) tr("Aus Favoriten entfernen") else tr("Zu Favoriten"),
                            tint = if (img.favorite) Color(0xFFFFC94D) else Color.White,
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .transformable(transform)
                        .pointerInput(img.id) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                        // Keyboard (AP-30): Enter zooms in and out like a double tap.
                        .keyboardClick { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero },
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
                    ViewerAction(Icons.Outlined.Brush, tr("Überarbeiten"), onEdit)
                    ViewerAction(Icons.Outlined.FileDownload, SAVE_IMAGE_LABEL) { saveImageToDevice(context, vm.gallery.file(img)) }
                    ViewerAction(Icons.Outlined.Share, tr("Teilen")) { shareImage(context, vm.gallery.file(img)) }
                    ViewerAction(Icons.AutoMirrored.Outlined.Chat, tr("Im Chat")) {
                        scope.launch {
                            val url = imageDataUrl(context, vm.gallery.file(img))
                            if (url == null) Notices.error(tr("Das Bild ließ sich nicht anhängen."))
                            else {
                                ChatHandoff.pending.value = ChatHandoff.Content("", emptyList(), images = listOf(url))
                                onDismiss()
                                onOpenChat()
                            }
                        }
                    }
                    ViewerAction(Icons.Outlined.Refresh, tr("Noch eins")) { vm.again(img); onDismiss() }
                    ViewerAction(Icons.Outlined.TextFields, tr("Beschreibung")) { onUsePrompt() }
                    ViewerAction(Icons.Outlined.Delete, tr("Löschen")) { vm.delete(img); onDismiss() }
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
