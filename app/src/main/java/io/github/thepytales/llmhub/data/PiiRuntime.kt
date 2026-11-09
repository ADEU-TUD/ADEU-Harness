package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import java.io.File

/**
 * Android part of the recognition model (AP-67): where the model is kept (app storage, not the synced Lora-Ordner) and
 * the ONNX Runtime of sherpa-onnx among the native libraries of the app (unpacked, see packaging in app/build.gradle.kts).
 * The PC has its own version in desktop/src/main/kotlin.
 */
object PiiRuntime {
    fun root(c: AppContainer): File = File(c.appContext.filesDir, "pii")

    fun library(c: AppContainer): File? =
        File(c.appContext.applicationInfo.nativeLibraryDir, "libonnxruntime.so").takeIf { it.isFile }
}
