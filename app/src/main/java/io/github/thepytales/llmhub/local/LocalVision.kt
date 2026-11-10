package io.github.thepytales.llmhub.local

import io.github.thepytales.llmhub.AppContainer

/**
 * Bilder verstehen lokal (AP-110): which model on this device understands images, and how long one image takes. The
 * image part (mmproj) is a download of its own ([LocalLlm.installVision]); with it, llama-server takes images in the
 * OpenAI form (`image_url` with a data URL), so chats send pictures to a local model as to a server model.
 * For the text recognition of scans on Android (PDF-Werkzeugkasten, Scanner) [model] is the place to ask – the PDF side
 * still has to call it (other package, see the work plan).
 */
object LocalVision {
    /** Image tokens llama-server is started with: fewer on the processor (faster), more on graphics. */
    const val CPU_IMAGE_TOKENS = 512
    const val GPU_IMAGE_TOKENS = 1024
    /** Tokens of the question and the answer around an image (a screenshot question; OCR of a page writes more). */
    const val QUESTION_TOKENS = 80
    const val ANSWER_TOKENS = 150

    /** The strongest installed model with its image part, without code specialists; null if there is none. */
    fun model(c: AppContainer): String? =
        c.localLlm.generalModels().filter { c.localLlm.seesImages(it) }.maxByOrNull { it.quality }?.modelId

    /**
     * Seconds for one image with [m] on [hw] (forecast of AP-105): the image encoder (its weights ≈ size of the image part,
     * about 2 FLOPs per weight and patch – 4 patches per image token), then reading the image tokens like text, then a
     * short answer. Processor: about 25 GFLOP/s per thread with AVX2; graphics: from their bandwidth.
     */
    fun secondsPerImage(m: LocalModel, hw: Hardware, f: SpeedForecast.Forecast = SpeedForecast.forecast(m, hw), answerTokens: Int = ANSWER_TOKENS): Double? {
        val p = m.projector ?: return null
        // Gemma encodes every image into a fixed 256 tokens; Qwen as many as llama-server allows.
        val tokens = if (m.family == "Gemma") 256 else if (f.gpu) GPU_IMAGE_TOKENS else CPU_IMAGE_TOKENS
        val params = p.size / if (p.file.contains("Q8_0")) 1.0 else 2.0
        val flops = params * 2 * tokens * 4
        val gflops = when {
            f.gpu && hw.chip.isNotBlank() -> (hw.gpuCores.takeIf { it > 0 } ?: 10) * 250.0
            f.gpu -> SpeedForecast.gpuBandwidth(hw.gpuName, hw.vramGb) * 15
            else -> LocalLlm.threads(hw) * 25.0 * SpeedForecast.simd(hw)
        }
        return flops / (gflops * 1e9) + (tokens + QUESTION_TOKENS) / f.readTokS + answerTokens / f.writeTokS
    }
}
