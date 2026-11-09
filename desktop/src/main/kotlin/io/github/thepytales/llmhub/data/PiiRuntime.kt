package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File

/**
 * PC part of the recognition model (AP-67): the model lies with the other large local files (%LOCALAPPDATA%\ADEU LLM\pii,
 * not in the synced data folder), the ONNX Runtime is the one of sherpa-onnx in the app's native folder – or the file
 * named by `lora.pii.ort` (tests, developers).
 */
object PiiRuntime {
    fun root(@Suppress("UNUSED_PARAMETER") c: AppContainer): File = File(Paths.local, "pii")

    fun library(@Suppress("UNUSED_PARAMETER") c: AppContainer): File? {
        System.getProperty("lora.pii.ort")?.let(::File)?.takeIf { it.isFile }?.let { return it }
        val dirs = listOfNotNull(
            System.getProperty("sherpa_onnx.native.path"),
            System.getProperty("compose.application.resources.dir")?.let { File(it, "native").path },
        ).map(::File).filter { it.isDirectory }
        return dirs.firstNotNullOfOrNull { dir ->
            dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name.length }.firstOrNull {
                it.name == "onnxruntime.dll" || it.name.startsWith("libonnxruntime.so") || it.name.startsWith("libonnxruntime") && it.name.endsWith(".dylib")
            }
        }
    }
}
