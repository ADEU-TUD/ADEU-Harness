package io.github.thepytales.llmhub.video

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.PointerByReference
import io.github.thepytales.llmhub.AppLog
import io.github.thepytales.llmhub.desktop.Extensions
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A face in a picture (AP-54): centre [x], [y] and size [w], [h] as shares of the picture, and how sure it is. */
data class Face(val x: Float, val y: Float, val w: Float, val h: Float, val score: Float = 1f)

/** Finds faces in one picture (AP-54). The app uses [YuNet]; tests use a fake. Called from one thread at a time. */
interface FaceDetector {
    val name: String
    fun detect(img: BufferedImage): List<Face>
}

/**
 * Faces found automatically and pixelated (AP-54), all on this computer: frames of a clip are sampled with ffmpeg,
 * a [FaceDetector] finds the faces in each, faces seen in successive frames become one track, and each track becomes a
 * keyframed [Region] of the clip – so the pixelation follows the person, through the same graph as everything else.
 *
 * The detector is **YuNet** (OpenCV Zoo `face_detection_yunet_2023mar.onnx`, MIT licence, 227 KB) in the
 * Videoeditor-Paket; it runs on the ONNX Runtime that the app ships with the speech recognition (sherpa-onnx), so the
 * package grows by the model only. Without the package the editor says so and regions are drawn by hand.
 */
object Faces {
    /** The model in the Videoeditor-Paket. */
    const val MODEL = "faces/face_detection_yunet_2023mar.onnx"

    /** Tests (and a developer with another runtime) put a detector here. */
    @Volatile
    var override: FaceDetector? = null

    fun model(): File? = Extensions.file(Extensions.VIDEO.id, MODEL) ?: System.getProperty("lora.faces.model")?.let(::File)?.takeIf { it.isFile }

    /** The ONNX Runtime library of the app (beside sherpa-onnx), or one named by `lora.faces.ort`. */
    fun runtime(): File? {
        System.getProperty("lora.faces.ort")?.let(::File)?.takeIf { it.isFile }?.let { return it }
        val dir = System.getProperty("sherpa_onnx.native.path")?.let(::File)?.takeIf { it.isDirectory } ?: return null
        return dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name.length }
            .firstOrNull { it.name == "onnxruntime.dll" || it.name.startsWith("libonnxruntime.so") || it.name.startsWith("libonnxruntime") && it.name.endsWith(".dylib") }
    }

    /** Why faces cannot be found automatically here, or null. */
    fun missing(): String? = when {
        override != null -> null
        model() == null -> tr("Für die automatische Gesichtserkennung fehlt das Videoeditor-Paket (Einstellungen › Werkzeuge › Erweiterungen) oder es ist zu alt. Bereiche lassen sich von Hand setzen.")
        runtime() == null -> tr("Die Gesichtserkennung braucht die Spracherkennung der App (ONNX Runtime), sie fehlt hier. Bereiche lassen sich von Hand setzen.")
        else -> null
    }

    @Volatile
    private var yunet: YuNet? = null

    /** The detector, loaded once; null with [missing] saying why. */
    @Synchronized
    fun detector(): FaceDetector? {
        override?.let { return it }
        yunet?.let { return it }
        val m = model() ?: return null
        val lib = runtime() ?: return null
        return runCatching { YuNet(lib, m) }.onFailure { AppLog.e("Gesichtserkennung laden", it) }.getOrNull()?.also { yunet = it }
    }

    /**
     * Frames of [file] from [from] to [to] seconds every [step] seconds (one ffmpeg run, raw BGR, at most 640 pixels
     * wide or high) with the faces [detector] finds in them; [progress] 0..1. The first frame of every [step]-long slot
     * is taken (select), not ffmpeg's fps filter: that one hands out frames up to half a step late, and the pixelation
     * then ran ahead of a moving face.
     */
    suspend fun scan(file: File, from: Double, to: Double, detector: FaceDetector, step: Double = 0.4, progress: (Float) -> Unit = {}): List<Pair<Double, List<Face>>> = withContext(Dispatchers.IO) {
        val exe = Media.ffmpeg() ?: throw IllegalStateException(tr("ffmpeg fehlt in der App."))
        val info = Media.probe(file)
        val (sw, sh) = VideoFx.workSize(info, 640, 640)
        val len = (to - from).coerceAtLeast(0.1)
        val proc = ProcessBuilder(
            exe, "-nostdin", "-hide_banner", "-loglevel", "error", "-ss", String.format(Locale.ROOT, "%.3f", from), "-t", String.format(Locale.ROOT, "%.3f", len),
            "-i", file.absolutePath, "-an", "-vf", String.format(Locale.ROOT, "select='isnan(prev_selected_t)+gte(floor(t/%.4f+0.001)-floor(prev_selected_t/%.4f+0.001),1)',scale=%d:%d", step, step, sw, sh),
            "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "bgr24", "pipe:1",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val out = mutableListOf<Pair<Double, List<Face>>>()
        try {
            val input = DataInputStream(proc.inputStream.buffered(1 shl 20))
            var k = 0
            while (true) {
                coroutineContext.ensureActive()
                val img = BufferedImage(sw, sh, BufferedImage.TYPE_3BYTE_BGR)
                try { input.readFully((img.raster.dataBuffer as DataBufferByte).data) } catch (_: EOFException) { break }
                val t = from + k * step
                out += t to detector.detect(img)
                k++
                progress((k * step / len).toFloat().coerceIn(0f, 1f))
            }
        } finally {
            if (proc.isAlive) proc.destroyForcibly()
        }
        out
    }

    private fun iou(a: Face, b: Face): Float {
        val x0 = maxOf(a.x - a.w / 2, b.x - b.w / 2); val x1 = minOf(a.x + a.w / 2, b.x + b.w / 2)
        val y0 = maxOf(a.y - a.h / 2, b.y - b.h / 2); val y1 = minOf(a.y + a.h / 2, b.y + b.h / 2)
        val i = maxOf(0f, x1 - x0) * maxOf(0f, y1 - y0)
        val u = a.w * a.h + b.w * b.h - i
        return if (u <= 0f) 0f else i / u
    }

    /**
     * Detections in time order → tracks: a face continues the track whose last face overlaps it most (or lies within
     * its own size), unless that was more than [maxGap] seconds ago or its size changed by more than 1.6× – then a new
     * track (and region) begins, so a region never needs a much larger box than the face.
     */
    fun tracks(samples: List<Pair<Double, List<Face>>>, maxGap: Double = 1.6): List<List<Pair<Double, Face>>> {
        val done = mutableListOf<MutableList<Pair<Double, Face>>>()
        val open = mutableListOf<MutableList<Pair<Double, Face>>>()
        for ((t, faces) in samples.sortedBy { it.first }) {
            open.removeAll { tr -> (t - tr.last().first > maxGap).also { if (it) done += tr } }
            val fresh = mutableListOf<MutableList<Pair<Double, Face>>>()
            for (f in faces.sortedByDescending { it.score }) {
                fun near(l: Face) = abs(l.x - f.x) < maxOf(l.w, f.w) && abs(l.y - f.y) < maxOf(l.h, f.h)
                // Tracks not continued in this frame yet, whose last face is near; the one overlapping most.
                val best = open.filter { it.last().first < t - 1e-9 && near(it.last().second) && f.w / it.first().second.w in 0.62f..1.6f }
                    .maxByOrNull { iou(it.last().second, f) }
                if (best != null) best += t to f else fresh += mutableListOf(t to f)
            }
            open += fresh
        }
        return done + open
    }

    /** Drops keys that the line between their neighbours already gives (within [tolerance]). */
    internal fun simplify(keys: List<Key>, tolerance: Float = 0.006f): List<Key> {
        if (keys.size <= 2) return keys
        val out = mutableListOf(keys.first())
        for (i in 1 until keys.lastIndex) {
            val a = out.last(); val b = keys[i]; val c = keys[i + 1]
            val u = ((b.t - a.t) / (c.t - a.t)).toFloat()
            fun lin(g: (Key) -> Float) = g(a) + (g(c) - g(a)) * u
            if (abs(lin { it.x } - b.x) > tolerance || abs(lin { it.y } - b.y) > tolerance || abs(lin { it.w } - b.w) > tolerance || abs(lin { it.h } - b.h) > tolerance) out += b
        }
        out += keys.last()
        return out
    }

    /**
     * Tracks → regions of [mode] ("pixel" or "blur"): each face enlarged by [margin] (hair, ears, a turn of the head),
     * a little more upwards; tracks seen only once in a short scan still count – a missed face is worse than one box
     * too many.
     */
    fun regions(samples: List<Pair<Double, List<Face>>>, mode: String = "pixel", margin: Float = 0.45f): List<Region> =
        tracks(samples).map { tr ->
            val keys = tr.map { (t, f) -> Key(t, f.x, (f.y - f.h * 0.05f).coerceIn(0f, 1f), (f.w * (1 + margin)).coerceAtMost(1f), (f.h * (1 + margin * 1.2f)).coerceAtMost(1f)) }
            Region(keys = simplify(keys), mode = mode, face = true, hold = false)
        }

    /** Puts [found] as the face regions of clip [id] (earlier found faces of the clip are replaced, hand-drawn stay). */
    fun apply(p: VideoProject, id: String, found: List<Region>): VideoProject =
        VideoFx.change(p, id) { fx -> fx.copy(regions = fx.regions.filter { !it.face } + found) }

    /**
     * Finds the faces in V1 clip [c] (over its whole piece of the source) as regions of [mode]. Throws with a clear
     * message when the detector is not there.
     */
    suspend fun find(c: VideoClip, mode: String = "pixel", progress: (Float) -> Unit = {}): List<Region> {
        val d = withContext(Dispatchers.IO) { detector() } ?: throw IllegalStateException(missing() ?: tr("Die Gesichtserkennung ließ sich nicht starten."))
        val samples = if (c.fx.freeze) listOf(c.inSec to d.detect(withContext(Dispatchers.IO) { Media.frameAwt(File(c.path), c.inSec) } ?: return emptyList()))
        else scan(File(c.path), c.inSec, c.outSec, d, progress = progress)
        return regions(samples, mode)
    }
}

/**
 * YuNet face detection (OpenCV Zoo, MIT licence; Shiqi Yu et al.) on ONNX Runtime through its C API with JNA – the
 * runtime is the one the app ships for sherpa-onnx, so no second copy is loaded. The model takes a 640×640 BGR picture
 * (values 0…255) and gives, for strides 8, 16 and 32, class and object scores (already 0…1) and box offsets per cell;
 * decoded and filtered by non-maximum suppression as OpenCV's FaceDetectorYN does.
 */
internal class YuNet(lib: File, model: File, private val threshold: Float = 0.6f) : FaceDetector, AutoCloseable {
    override val name = "YuNet (OpenCV Zoo, MIT)"

    private companion object {
        const val SIZE = 640
        /** API version asked for: 11 = ONNX Runtime 1.11, all functions used here are older. */
        const val API_VERSION = 11
        // Indices into the OrtApi table (onnxruntime_c_api.h, stable across versions).
        const val GET_ERROR_MESSAGE = 2; const val CREATE_ENV = 3; const val CREATE_SESSION_FROM_ARRAY = 8; const val RUN = 9
        const val CREATE_SESSION_OPTIONS = 10; const val SET_INTRA_OP_THREADS = 24; const val CREATE_TENSOR_WITH_DATA = 49
        const val GET_TENSOR_DATA = 51; const val CREATE_CPU_MEMORY_INFO = 69; const val RELEASE_ENV = 92; const val RELEASE_STATUS = 93
        const val RELEASE_MEMORY_INFO = 94; const val RELEASE_SESSION = 95; const val RELEASE_VALUE = 96; const val RELEASE_SESSION_OPTIONS = 100
        val STRIDES = intArrayOf(8, 16, 32)
        val OUTPUTS = listOf("cls", "obj", "bbox", "kps").flatMap { k -> STRIDES.map { "${k}_$it" } }
    }

    private val native = NativeLibrary.getInstance(lib.absolutePath)
    private val api: Pointer = run {
        val base = native.getFunction("OrtGetApiBase").invokePointer(arrayOf<Any>()) ?: throw IllegalStateException("OrtGetApiBase")
        Function.getFunction(base.getPointer(0)).invokePointer(arrayOf(API_VERSION)) ?: throw IllegalStateException(tr("Die ONNX Runtime der App ist zu alt für die Gesichtserkennung."))
    }

    private fun fn(i: Int): Function = Function.getFunction(api.getPointer(i.toLong() * Native.POINTER_SIZE))

    private fun call(i: Int, vararg args: Any?) {
        val status = fn(i).invokePointer(args) ?: return
        val msg = fn(GET_ERROR_MESSAGE).invokeString(arrayOf(status), false)
        fn(RELEASE_STATUS).invokeVoid(arrayOf(status))
        throw IllegalStateException("ONNX Runtime: $msg")
    }

    private fun out(i: Int, vararg args: Any?): Pointer {
        val ref = PointerByReference()
        call(i, *args, ref)
        return ref.value
    }

    private val env = out(CREATE_ENV, 3, "lora-yunet")
    private val options = out(CREATE_SESSION_OPTIONS).also { call(SET_INTRA_OP_THREADS, it, 2) }
    private val session: Pointer = run {
        val bytes = model.readBytes()
        val mem = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
        out(CREATE_SESSION_FROM_ARRAY, env, mem, bytes.size.toLong(), options)
    }
    private val memInfo = out(CREATE_CPU_MEMORY_INFO, 1, 0)
    private val input = Memory(3L * SIZE * SIZE * 4)
    private val shape = Memory(4L * 8).apply { setLong(0, 1); setLong(8, 3); setLong(16, SIZE.toLong()); setLong(24, SIZE.toLong()) }
    private val inNames = StringArray(arrayOf("input"))
    private val outNames = StringArray(OUTPUTS.toTypedArray())

    @Synchronized
    override fun detect(img: BufferedImage): List<Face> {
        // Letterbox into 640×640 (top left), BGR planes.
        val s = SIZE.toDouble() / maxOf(img.width, img.height)
        val w = maxOf(1, (img.width * s).roundToInt()); val h = maxOf(1, (img.height * s).roundToInt())
        val box = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_3BYTE_BGR)
        box.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(img, 0, 0, w, h, null); dispose() }
        val px = (box.raster.dataBuffer as DataBufferByte).data
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            data[i] = (px[i * 3].toInt() and 0xff).toFloat()
            data[plane + i] = (px[i * 3 + 1].toInt() and 0xff).toFloat()
            data[2 * plane + i] = (px[i * 3 + 2].toInt() and 0xff).toFloat()
        }
        input.write(0, data, 0, data.size)
        val value = out(CREATE_TENSOR_WITH_DATA, memInfo, input, input.size(), shape, 4L, 1)
        val inputs = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, value) }
        val outputs = Memory(OUTPUTS.size.toLong() * Native.POINTER_SIZE).apply { clear() }
        try {
            call(RUN, session, null, inNames, inputs, 1L, outNames, OUTPUTS.size.toLong(), outputs)
            fun read(k: Int, n: Int): FloatArray = out(GET_TENSOR_DATA, outputs.getPointer(k.toLong() * Native.POINTER_SIZE)).getFloatArray(0, n)
            val found = mutableListOf<Face>()
            STRIDES.forEachIndexed { si, stride ->
                val cols = SIZE / stride
                val cells = cols * cols
                val cls = read(si, cells); val obj = read(3 + si, cells); val bbox = read(6 + si, cells * 4)
                for (c in 0 until cells) {
                    val score = sqrt(cls[c].coerceIn(0f, 1f) * obj[c].coerceIn(0f, 1f))
                    if (score < threshold) continue
                    val cx = (c % cols + bbox[c * 4]) * stride
                    val cy = (c / cols + bbox[c * 4 + 1]) * stride
                    val bw = exp(bbox[c * 4 + 2]) * stride
                    val bh = exp(bbox[c * 4 + 3]) * stride
                    found += Face((cx / w).coerceIn(0f, 1f), (cy / h).coerceIn(0f, 1f), (bw / w).coerceIn(0f, 1f), (bh / h).coerceIn(0f, 1f), score)
                }
            }
            return nms(found)
        } finally {
            for (k in OUTPUTS.indices) outputs.getPointer(k.toLong() * Native.POINTER_SIZE)?.let { fn(RELEASE_VALUE).invokeVoid(arrayOf(it)) }
            fn(RELEASE_VALUE).invokeVoid(arrayOf(value))
        }
    }

    private fun nms(faces: List<Face>, overlap: Float = 0.3f): List<Face> {
        val keep = mutableListOf<Face>()
        for (f in faces.sortedByDescending { it.score }) {
            if (keep.none { k ->
                    val x0 = maxOf(k.x - k.w / 2, f.x - f.w / 2); val x1 = minOf(k.x + k.w / 2, f.x + f.w / 2)
                    val y0 = maxOf(k.y - k.h / 2, f.y - f.h / 2); val y1 = minOf(k.y + k.h / 2, f.y + f.h / 2)
                    val i = maxOf(0f, x1 - x0) * maxOf(0f, y1 - y0)
                    i / (k.w * k.h + f.w * f.h - i) > overlap
                }) keep += f
        }
        return keep
    }

    override fun close() {
        fn(RELEASE_SESSION).invokeVoid(arrayOf(session))
        fn(RELEASE_SESSION_OPTIONS).invokeVoid(arrayOf(options))
        fn(RELEASE_MEMORY_INFO).invokeVoid(arrayOf(memInfo))
        fn(RELEASE_ENV).invokeVoid(arrayOf(env))
    }
}
