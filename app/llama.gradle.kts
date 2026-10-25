/*
 * llama-server for Android (local language models): built from a fixed llama.cpp release with the NDK, statically
 * linked (only Android's own libraries needed) and packed into the APK's lib folder – the only place Android lets an
 * app start a program from. Two variants: libllama-server.so for processors with dot product instructions (most
 * phones since 2019, much faster) and libllama-server-basic.so for all others. Takes a few minutes; the result is kept
 * in build/llama-cache until the version changes.
 *
 * Needs git, the NDK (android.ndkVersion) and CMake (Android SDK or system). -PskipNatives or -PskipLlama builds
 * without local models.
 */
import java.io.File

val llamaTag = "b11870"
val skip = providers.gradleProperty("skipNatives").isPresent || providers.gradleProperty("skipLlama").isPresent

fun run(dir: File, vararg cmd: String) {
    val p = ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    if (p.waitFor() != 0) throw GradleException("${cmd.take(3).joinToString(" ")} fehlgeschlagen:\n${out.takeLast(4000)}")
}

val llamaServerAndroid by tasks.registering {
    val out = layout.buildDirectory.dir("llama/jni")
    inputs.property("tag", llamaTag)
    inputs.property("skip", skip)
    outputs.dir(out)
    doLast {
        val jni = out.get().asFile.resolve("arm64-v8a").apply { parentFile.deleteRecursively(); mkdirs() }
        if (skip) { logger.warn("llama-server: übersprungen (-PskipNatives/-PskipLlama)"); return@doLast }
        val cache = rootProject.layout.buildDirectory.dir("llama-cache/$llamaTag").get().asFile.apply { mkdirs() }
        val variants = mapOf(
            "libllama-server.so" to "armv8.2-a+dotprod+fp16",
            "libllama-server-basic.so" to "armv8-a",
        )
        if (variants.keys.any { !File(cache, it).isFile }) {
            // Script plugins do not see the classes of the Android plugin: ask its extension by reflection. Its
            // ndkDirectory throws when the NDK version is not installed, so the NDK is looked up here.
            val android = project.extensions.getByName("android")
            val sdkDir = android.javaClass.getMethod("getSdkDirectory").invoke(android) as File
            val ndkVersion = runCatching { android.javaClass.getMethod("getNdkVersion").invoke(android) as String? }.getOrNull()
            val ndk = listOfNotNull(
                ndkVersion?.let { sdkDir.resolve("ndk/$it") },
                System.getenv("ANDROID_NDK_HOME")?.let(::File),
                System.getenv("ANDROID_NDK_LATEST_HOME")?.let(::File),
                System.getenv("ANDROID_NDK_ROOT")?.let(::File),
                sdkDir.resolve("ndk").listFiles()?.filter { it.resolve("build/cmake/android.toolchain.cmake").isFile }?.maxByOrNull { it.name },
            ).firstOrNull { it.resolve("build/cmake/android.toolchain.cmake").isFile }
                ?: throw GradleException("Android NDK fehlt (gesucht: $sdkDir/ndk/$ndkVersion, ANDROID_NDK_HOME). Installieren: sdkmanager \"ndk;$ndkVersion\" – oder ohne lokale Modelle bauen: -PskipLlama")
            logger.lifecycle("llama-server: NDK $ndk")
            val src = File(cache, "src")
            if (!File(src, "CMakeLists.txt").isFile) {
                src.deleteRecursively()
                run(cache, "git", "clone", "--quiet", "--depth", "1", "--branch", llamaTag, "https://github.com/ggml-org/llama.cpp", src.path)
            }
            val sdkCmake = sdkDir.resolve("cmake").listFiles()?.sortedBy { it.name }?.lastOrNull()?.resolve("bin")
            val cmake = sdkCmake?.resolve("cmake")?.takeIf { it.isFile }?.path ?: "cmake"
            val ninja = sdkCmake?.resolve("ninja")?.takeIf { it.isFile }?.path
            val strip = ndk.resolve("toolchains/llvm/prebuilt").listFiles()!!.first().resolve("bin/llvm-strip").path
            for ((name, arch) in variants) {
                val build = File(cache, "build-$arch")
                val gen = if (ninja != null) arrayOf("-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=$ninja") else arrayOf("-G", "Unix Makefiles")
                run(src, cmake, "-S", ".", "-B", build.path, *gen,
                    "-DCMAKE_TOOLCHAIN_FILE=${ndk.resolve("build/cmake/android.toolchain.cmake")}",
                    "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-28", "-DANDROID_STL=c++_static",
                    "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=OFF", "-DGGML_NATIVE=OFF", "-DGGML_OPENMP=OFF",
                    "-DLLAMA_CURL=OFF", "-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF", "-DGGML_CPU_ARM_ARCH=$arch")
                run(src, cmake, "--build", build.path, "--target", "llama-server", "-j", Runtime.getRuntime().availableProcessors().toString())
                val bin = File(build, "bin/llama-server")
                val target = File(cache, name)
                bin.copyTo(target, overwrite = true)
                run(cache, strip, target.path)
            }
        }
        variants.keys.forEach { File(cache, it).copyTo(File(jni, it), overwrite = true) }
    }
}
tasks.named("preBuild") { dependsOn(llamaServerAndroid) }
