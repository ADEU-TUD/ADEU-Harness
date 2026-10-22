package io.github.thepytales.llmhub.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.FileAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Loads, rotates (EXIF), downscales and encodes an image as JPEG data URL. */
suspend fun encodeImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        var bmp = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)
        if (rotation != 0f) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }
        val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

/** Small chip above the input that shows active context (repository, environment, persona). */
@Composable
fun ContextChip(icon: ImageVector, label: String, onClick: () -> Unit, highlighted: Boolean = true) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

/** ChatGPT-like input: rounded field with "+" for attachments, microphone and send button. */
@Composable
fun Composer(
    input: String,
    onInput: (String) -> Unit,
    placeholder: String,
    images: List<String>,
    files: List<FileAttachment>,
    loadingFiles: Int,
    onRemoveImage: (String) -> Unit,
    onRemoveFile: (FileAttachment) -> Unit,
    running: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    chips: @Composable RowScope.() -> Unit,
) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = chips,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                if (images.isNotEmpty() || files.isNotEmpty() || loadingFiles > 0) {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        images.forEach { a -> Thumbnail(a, 60) { onRemoveImage(a) } }
                        files.forEach { f -> FileChip(f) { onRemoveFile(f) } }
                        if (loadingFiles > 0) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onPlus) { Icon(Icons.Outlined.Add, contentDescription = "Hinzufügen") }
                    TextField(
                        value = input,
                        onValueChange = onInput,
                        placeholder = { Text(if (recording) "Aufnahme läuft…" else placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(enabled = !transcribing, onClick = onMic) {
                        when {
                            transcribing -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            recording -> Icon(Icons.Outlined.StopCircle, contentDescription = "Aufnahme beenden", tint = MaterialTheme.colorScheme.error)
                            else -> Icon(Icons.Outlined.Mic, contentDescription = "Spracheingabe")
                        }
                    }
                    if (running) {
                        FilledIconButton(
                            onClick = onStop,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                        ) { Icon(Icons.Outlined.Stop, contentDescription = "Stopp", tint = MaterialTheme.colorScheme.surface) }
                    } else {
                        FilledIconButton(
                            enabled = input.isNotBlank() || images.isNotEmpty() || files.isNotEmpty(),
                            onClick = onSend,
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
            }
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    )
}

/** Sheet behind the "+" button: attachments and chat settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSheet(
    repoLabel: String,
    envLabel: String,
    personaLabel: String,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onTemplates: () -> Unit,
    onPersona: () -> Unit,
    onRepo: () -> Unit,
    onEnvironment: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun pick(action: () -> Unit) {
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTile(Icons.Outlined.CameraAlt, "Kamera", { pick(onCamera) }, Modifier.weight(1f))
            SheetTile(Icons.Outlined.Image, "Fotos", { pick(onPhotos) }, Modifier.weight(1f))
            SheetTile(Icons.Outlined.AttachFile, "Dateien", { pick(onFiles) }, Modifier.weight(1f))
        }
        Text(
            "PDF, Word, PowerPoint, Excel, Text- und Code-Dateien werden gelesen und mitgeschickt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SheetRow(Icons.Outlined.Bookmarks, "Vorlagen", "Prompt-Vorlagen und Personas", { pick(onTemplates) })
        SheetRow(Icons.Outlined.Person, "Persona / System-Prompt", personaLabel, { pick(onPersona) })
        SheetRow(Icons.Outlined.Code, "GitHub-Repository", repoLabel, { pick(onRepo) })
        SheetRow(Icons.Outlined.Tune, "Umgebung", envLabel, { pick(onEnvironment) })
        Spacer(Modifier.height(24.dp))
    }
}
