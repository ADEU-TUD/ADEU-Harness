/*
 * Native parts of the speech recognition, loaded at build time with a fixed version and SHA-256 (applied by
 * app/build.gradle.kts and desktop/build.gradle.kts). Change versions only here.
 *
 *   speechBuiltinModels  → build/speech/models/   VAD, speaker segmentation, calibration recording (into the app)
 *   speechAndroidJni     → build/speech/jni/      sherpa-onnx + onnxruntime for Android (arm64, -PwithX86 also x86_64)
 *   speechDesktopNatives → build/speech/desktop/  windows/native/(DLLs) + windows/tools/ffmpeg.exe (or linux/native/(.so))
 *
 * -PskipNatives (or -PskipSpeech for only these) builds without them (no internet access): transcription on the device then
 * reports that it is not available.
 */
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

val sherpaVersion = "1.13.8"
val sherpa = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

/** url(s), sha256 */
val artifacts = mapOf(
    "android" to (listOf("$sherpa/v$sherpaVersion/sherpa-onnx-v$sherpaVersion-android.tar.bz2") to
        "2ff63469a71cb6009aa2e3ed5f4a670f8abdcbe4bb9ffd23776afc792a6b4f44"),
    "windows" to (listOf("$sherpa/v$sherpaVersion/sherpa-onnx-v$sherpaVersion-win-x64-jni.tar.bz2") to
        "d1da3b30a1ce347461dbe62d3114255b3e5bb1d6180f0b404a7915d1c5a956b4"),
    "linux" to (listOf("$sherpa/v$sherpaVersion/sherpa-onnx-v$sherpaVersion-linux-x64-jni.tar.bz2") to
        "f18bf581b408d8c28539c1b28899b4d4811faebb74eede08db21fd6e9243e346"),
    "silero" to (listOf("$sherpa/asr-models/silero_vad.onnx") to
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"),
    "pyannote" to (listOf("$sherpa/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2") to
        "24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488"),
    // ffmpeg for Windows: decoding of all audio/video formats, compressing recordings.
    "ffmpeg" to (listOf("https://github.com/GyanD/codexffmpeg/releases/download/9.0.2/ffmpeg-9.0.2-essentials_build.zip") to
        "60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba"),
)

val skip = providers.gradleProperty("skipNatives").isPresent || providers.gradleProperty("skipSpeech").isPresent
val withX86 = providers.gradleProperty("withX86").isPresent
val windowsTarget = providers.gradleProperty("windowsJars").isPresent || System.getProperty("os.name").startsWith("Windows")
val downloads = File(rootDir, (if (rootDir.name == "desktop") "../" else "") + "build/speech-downloads")

fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input -> val buf = ByteArray(1 shl 16); while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) } }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** Downloads an artifact once (with redirects) and checks its SHA-256; returns null with -PskipNatives on failure. */
fun fetch(key: String): File? {
    val (urls, sha) = artifacts.getValue(key)
    val dest = File(downloads, urls.first().substringAfterLast('/'))
    if (dest.isFile && sha256(dest) == sha) return dest
    dest.parentFile.mkdirs()
    var last: Exception? = null
    for (start in urls) {
        try {
            var url = start
            var con: HttpURLConnection
            var hops = 0
            while (true) {
                con = URL(url).openConnection() as HttpURLConnection
                con.instanceFollowRedirects = false
                con.connectTimeout = 30000; con.readTimeout = 120000
                val code = con.responseCode
                if (code in 300..399 && hops++ < 10) { url = URL(URL(url), con.getHeaderField("Location")).toString(); con.disconnect(); continue }
                if (code != 200) throw GradleException("HTTP $code für $url")
                break
            }
            val part = File(dest.path + ".part")
            con.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            val got = sha256(part)
            if (got != sha) { part.delete(); throw GradleException("Prüfsumme falsch für $url: $got") }
            dest.delete(); part.renameTo(dest)
            return dest
        } catch (e: Exception) { last = e }
    }
    if (skip) { logger.warn("speech: $key nicht geladen (-PskipNatives): ${last?.message}"); return null }
    throw GradleException("speech: $key konnte nicht geladen werden: ${last?.message}", last)
}

val speechBuiltinModels by tasks.registering {
    val out = layout.buildDirectory.dir("speech/models")
    inputs.property("skip", skip)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.resolve("models").apply { deleteRecursively(); mkdirs() }
        File(rootDir, (if (rootDir.name == "desktop") "../" else "") + "speech/models/cal-4speakers.wav").copyTo(File(dir, "cal-4speakers.wav"), true)
        fetch("silero")?.copyTo(File(dir, "silero_vad.onnx"), true)
        fetch("pyannote")?.let { tar ->
            project.copy {
                from(tarTree(resources.bzip2(tar))) { include("**/model.onnx"); eachFile { path = "pyannote-segmentation-3.0.onnx" }; includeEmptyDirs = false }
                into(dir)
            }
        }
    }
}

val speechAndroidJni by tasks.registering {
    val out = layout.buildDirectory.dir("speech/jni")
    inputs.property("withX86", withX86)
    inputs.property("skip", skip)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        val tar = fetch("android") ?: return@doLast
        project.copy {
            from(tarTree(resources.bzip2(tar))) {
                include("**/arm64-v8a/libsherpa-onnx-jni.so", "**/arm64-v8a/libonnxruntime.so")
                if (withX86) include("**/x86_64/libsherpa-onnx-jni.so", "**/x86_64/libonnxruntime.so")
                eachFile { path = path.substringAfter("jniLibs/") }
                includeEmptyDirs = false
            }
            into(dir)
        }
    }
}

val speechDesktopNatives by tasks.registering {
    val out = layout.buildDirectory.dir("speech/desktop")
    inputs.property("windows", windowsTarget)
    inputs.property("skip", skip)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        if (windowsTarget) {
            fetch("windows")?.let { tar ->
                project.copy { from(tarTree(resources.bzip2(tar))) { include("**/lib/*.dll"); eachFile { path = "windows/native/$name" }; includeEmptyDirs = false }; into(dir) }
            }
            fetch("ffmpeg")?.let { zip ->
                project.copy {
                    from(zipTree(zip)) {
                        include("**/bin/ffmpeg.exe", "**/LICENSE")
                        eachFile { path = "windows/tools/" + (if (name == "LICENSE") "ffmpeg-LICENSE.txt" else name) }
                        includeEmptyDirs = false
                    }
                    into(dir)
                }
            }
        } else {
            fetch("linux")?.let { tar ->
                project.copy { from(tarTree(resources.bzip2(tar))) { include("**/lib/*.so"); eachFile { path = "linux/native/$name" }; includeEmptyDirs = false }; into(dir) }
            }
        }
        File(dir, "common").mkdirs()
    }
}
