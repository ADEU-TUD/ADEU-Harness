package io.github.thepytales.llmhub.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GeneratedImage(val prompt: String, val model: String, val bytes: ByteArray, val bitmap: ImageBitmap)

class ImagesViewModel(private val c: AppContainer) : ViewModel() {
    var prompt by mutableStateOf("")
    var size by mutableStateOf("1024x1024")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val results = mutableStateListOf<GeneratedImage>()

    fun generate(model: String) {
        if (prompt.isBlank() || busy) return
        busy = true
        error = null
        val p = prompt.trim()
        viewModelScope.launch {
            try {
                val images = c.llm.generateImage(model, p, size)
                val decoded = withContext(Dispatchers.Default) {
                    images.mapNotNull { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                            GeneratedImage(p, model, bytes, it.asImageBitmap())
                        }
                    }
                }
                results.addAll(0, decoded)
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                busy = false
            }
        }
    }
}

private fun saveToGallery(context: Context, img: GeneratedImage) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Notices.info("Speichern wird erst ab Android 10 unterstützt.")
        return
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "llmhub_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ADEU LLM")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        Notices.error("Speichern fehlgeschlagen.")
        return
    }
    resolver.openOutputStream(uri)?.use { out ->
        val bmp = BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    Notices.info("In Galerie gespeichert (Bilder/ADEU LLM).")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(c: AppContainer, onBack: () -> Unit) {
    val vm: ImagesViewModel = viewModel { ImagesViewModel(c) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val models by c.models.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imageModels = models.ofType(ModelType.IMAGE).map { it.id }

    Scaffold(topBar = { TopAppBar(navigationIcon = { BackButton(onBack) }, title = { Text("Bilder generieren") }) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = vm.prompt,
                        onValueChange = { vm.prompt = it },
                        label = { Text("Beschreibung des Bildes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownField("Modell", settings.imageModel, imageModels, { v -> c.settings.update { it.copy(imageModel = v) } })
                    DropdownField(
                        "Größe", vm.size,
                        listOf("1024x1024", "768x1024", "1024x768", "768x1344", "1344x768", "512x512"),
                        { vm.size = it },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.generate(settings.imageModel) }, enabled = !vm.busy && vm.prompt.isNotBlank()) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Generieren")
                        }
                        if (vm.busy) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    }
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            items(vm.results) { img ->
                Card(Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = img.bitmap,
                        contentDescription = img.prompt,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            img.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { saveToGallery(context, img) }) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Speichern")
                        }
                    }
                }
            }
        }
    }
}
