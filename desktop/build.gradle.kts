import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

/**
 * Java memory of the Windows app (AP-04): limit 1.5 GB (large PDFs and transcripts fit), free memory goes back to the
 * system, duplicate strings (chat texts) are shared. Also written into "ADEU LLM.cfg" by .github/scripts/release-local.sh.
 */
val MEMORY_JVM_ARGS = listOf(
    "-Xms64m", "-Xmx1536m", "-XX:+UseG1GC", "-XX:G1PeriodicGCInterval=60000", "-XX:MinHeapFreeRatio=10",
    "-XX:MaxHeapFreeRatio=30", "-XX:+UseStringDeduplication", "-XX:SoftRefLRUPolicyMSPerMB=200",
)

/** The packages are built on the OS they are for (Windows, macOS, Linux runner); only -PwindowsJars builds across. */
val hostOs: String = System.getProperty("os.name").orEmpty()
val isMacHost = hostOs.startsWith("Mac")
val isLinuxHost = hostOs.startsWith("Linux")

/**
 * JCEF (browser beside the chat, AP-14) reaches into AWT internals for the native window of its browser. The Windows
 * list is also written into "ADEU LLM.cfg" by .github/scripts/release-local.sh. On macOS JCEF needs the LWAWT packages
 * instead of sun.awt.windows (jcefmaven README, "To use on MacOSX"); on Linux it takes the window through JAWT and
 * needs neither (an --add-opens for a package the JDK lacks there only prints a warning).
 */
val BROWSER_JVM_ARGS = listOf(
    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED", "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
) + when {
    isMacHost -> listOf("--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED", "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
    isLinuxHost -> emptyList()
    else -> listOf("--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED")
} + "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED"

/**
 * macOS signature and notarization (AP-31), only when the variables are set (secrets of the release workflow);
 * otherwise Compose signs ad hoc ("-"), which Apple Silicon requires, and the DMG is not notarized.
 *   MAC_SIGN_IDENTITY     "Developer ID Application: Name (TEAMID)" or only "Name (TEAMID)"
 *   MAC_SIGN_KEYCHAIN     keychain with that certificate (optional, else the default keychain)
 *   MAC_NOTARY_APPLE_ID, MAC_NOTARY_PASSWORD (app-specific password), MAC_NOTARY_TEAM_ID – for ./gradlew notarizeDmg
 */
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val macSignIdentity = env("MAC_SIGN_IDENTITY")?.removePrefix("Developer ID Application:")?.trim()

/** Info.plist texts for the permission prompts of macOS (without them macOS ends the app on first use). */
val MAC_PLIST_KEYS = """
    <key>NSMicrophoneUsageDescription</key>
    <string>Lora braucht das Mikrofon für die Spracheingabe und für Aufnahmen, die transkribiert werden sollen.</string>
    <key>NSAppleEventsUsageDescription</key>
    <string>Lora steuert andere Programme nur auf Wunsch, z. B. Outlook oder Mail für Mails und Termine.</string>
""".trimIndent()

/** Same version scheme as the APK: 1.0.<build number>. */
val appVersion = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"

dependencies {
    // -PwindowsJars: the Windows libraries also on another OS (portable release built without a Windows runner).
    implementation(if (project.hasProperty("windowsJars")) compose.desktop.windows_x64 else compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // JavaScript sandbox for exact calculations and data analysis (run_javascript).
    implementation("org.mozilla:rhino:1.7.15")
    // Reading attached PDFs (text and scanned pages), page count of generated PDFs.
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    // Digitale Signatur (AP-60): CMS signatures for PDFs and their check (Bouncy Castle, MIT licence).
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    // HTML/CSS → PDF when neither Edge nor Chrome is available.
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.24")
    implementation("org.jsoup:jsoup:1.18.1")
    // Windows: DPAPI for the API key, registry for autostart.
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    // Plays the MP3 speech of the server.
    implementation("javazoom:jlayer:1.0.1")
    // Speech recognition on the device (speech/): sherpa-onnx (Java part) and org.json, which Android has built in.
    implementation(files("../speech/libs/sherpa-onnx-jvm-1.13.8.jar"))
    implementation("org.json:json:20250517")
    // Browser beside the chat (AP-14): Chromium through JCEF. Only the Java part is in the app; Chromium itself
    // (about 170 MB) is set up once on first use into %LOCALAPPDATA%\ADEU LLM\browser (see desktop/WebEngine.kt).
    implementation("me.friwi:jcefmaven:152.0.6")
    // QR codes in the Kreativbereich (AP-24), MIT licence, one small class without further dependencies.
    implementation("io.nayuki:qrcodegen:1.8.0")

    testImplementation(kotlin("test"))
    // Compose UI tests without a window (chat scrolling).
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

/*
 * The Windows app shares the platform-independent code with the Android app: every file of
 * app/src/main/java is compiled here, unless src/main/kotlin has a file with the same path
 * (the Windows version of it) or the file only makes sense on Android.
 */
val androidSources = file("../app/src/main/java")
val desktopSources = file("src/main/kotlin")
val androidOnly = setOf(
    "android/print/PdfPrintBridge.kt",
    "io/github/thepytales/llmhub/MainActivity.kt",
    "io/github/thepytales/llmhub/transcribe/TranscriptionService.kt",
    // Browser neben dem Chat auf Android (AP-63, WebView); the PC has its own with Chromium (ui/BrowserPanel.kt).
    "io/github/thepytales/llmhub/ui/PhoneBrowser.kt",
)

val sharedSources by tasks.registering(Sync::class) {
    from(androidSources) {
        exclude { e -> !e.isDirectory && (e.path in androidOnly || desktopSources.resolve(e.path).exists()) }
    }
    into(layout.buildDirectory.dir("shared-src"))
}

/** Version for the About section and crash reports (Android: BuildConfig). */
val buildConfig by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildconfig")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/thepytales/llmhub/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package io.github.thepytales.llmhub\n\nobject BuildConfig {\n" +
                "    const val VERSION_NAME = \"$version\"\n    const val DEBUG = false\n}\n"
        )
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(sharedSources)
        kotlin.srcDir(buildConfig)
        // Logo and font of the TU Dresden design (resources/tud), shared with the Android app.
        resources.srcDir(file("../app/src/main/resources"))
    }
}

// Speech recognition: the shared Java sources (speech/), the built-in models as resources and the native libraries
// plus ffmpeg as app resources (resources/native, resources/tools next to the jars; see speech/natives.gradle.kts).
apply(from = "../speech/natives.gradle.kts")
sourceSets.main {
    java.srcDir("../speech/src/main/java")
    resources.srcDir(tasks.named("speechBuiltinModels"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(BROWSER_JVM_ARGS)
}

compose.desktop {
    application {
        mainClass = "io.github.thepytales.llmhub.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Xss4m")
        // AP-04: a fixed upper limit instead of a quarter of the RAM, and free heap goes back to Windows while idle
        // (G1 collects every minute when nothing happens, keeps at most 30 % free). The same list is in release-local.sh.
        jvmArgs += MEMORY_JVM_ARGS
        jvmArgs += BROWSER_JVM_ARGS

        nativeDistributions {
            appResourcesRootDir.set(layout.buildDirectory.dir("speech/desktop"))
            // AppImage: the portable app folder of createDistributable (Windows: ADEU LLM.exe + runtime, see
            // .github/scripts/windows-portable.ps1; macOS: ADEU LLM.app; Linux: ADEU LLM/bin/ADEU LLM) – the technical
            // name stays, the app is called Lora (Brand.kt). Dmg and Deb only exist on their own OS (packageDmg on
            // macOS, packageDeb on Linux with dpkg-deb and fakeroot). On macOS Compose rejects AppImage while
            // configuring („Unexpected target format for MacOS: AppImage“, release 1.0.48), so only Dmg there –
            // createDistributable builds ADEU LLM.app without it.
            if (System.getProperty("os.name").startsWith("Mac")) targetFormats(TargetFormat.Dmg)
            else targetFormats(TargetFormat.AppImage, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "ADEU LLM"
            packageVersion = appVersion
            description = "Lora – Der freundliche Helfer für alle Wünsche in Forschung, Lehre und Verwaltung an der TU Dresden"
            vendor = "thepytales"
            copyright = "Inoffiziell, nicht mit der Universität verbunden."
            // java.sql: used by some libraries; jdk.crypto.ec / jdk.charsets: TLS and windows-1252 text files;
            // jdk.crypto.mscapi: certificates of Windows (company proxies); jdk.localedata: German dates;
            // jdk.management: installed memory for the hardware check without JNA.
            modules("java.instrument", "java.management", "jdk.management", "java.naming", "java.net.http", "java.sql", "jdk.unsupported",
                "jdk.crypto.ec", "jdk.charsets", "jdk.accessibility", "jdk.localedata")
            if (System.getProperty("os.name").startsWith("Windows")) modules("jdk.crypto.mscapi")
            windows {
                iconFile.set(project.file("icons/app.ico"))
                // Without console window; errors go to log.txt in the data folder.
                console = false
            }
            // AP-31: Apple Silicon (macos-15 runner). ADEU LLM.app keeps the technical name, menu bar and Dock say Lora.
            macOS {
                iconFile.set(project.file("icons/app.icns"))
                bundleID = "de.tu-dresden.adeu.lora"
                packageName = "Lora"
                dockName = "Lora"
                appCategory = "public.app-category.productivity"
                // Chromium of the browser (JCEF 152) needs macOS 12.
                minimumSystemVersion = "12.0"
                // Hardened runtime (Developer ID and ad-hoc signature): the JVM's JIT, native libraries that are not
                // signed by us (JNA, JCEF/Chromium, sherpa-onnx, Skia), microphone, Apple Events. No XML comments in
                // these files – codesign embeds them as they are.
                entitlementsFile.set(project.file("macos/entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("macos/runtime-entitlements.plist"))
                infoPlist { extraKeysRawXml = MAC_PLIST_KEYS }
                if (macSignIdentity != null) {
                    signing {
                        sign.set(true)
                        identity.set(macSignIdentity)
                        env("MAC_SIGN_KEYCHAIN")?.let { keychain.set(it) }
                    }
                }
                notarization {
                    env("MAC_NOTARY_APPLE_ID")?.let { appleID.set(it) }
                    env("MAC_NOTARY_PASSWORD")?.let { password.set(it) }
                    env("MAC_NOTARY_TEAM_ID")?.let { teamID.set(it) }
                }
            }
            linux {
                iconFile.set(project.file("icons/app.png"))
                // Debian package "adeu-llm" (installs to /opt/adeu-llm, menu entry in "Büro"/Office).
                packageName = "adeu-llm"
                // Only the address – jpackage writes "Maintainer: <vendor> <address>".
                debMaintainer = "thepytales@users.noreply.github.com"
                // Categories= of the .desktop file (freedesktop main category).
                menuGroup = "Office"
                // Section: of the DEB control file; the Debian list has no "office", "misc" is jpackage's default.
                appCategory = "misc"
                shortcut = true
            }
        }
    }
}

/**
 * The app's jars (own jar plus all libraries) in build/portable/app – with -PwindowsJars for the portable Windows
 * version, which .github/scripts/windows-portable-cross.sh assembles from them and the launcher and Java runtime
 * of a previous release, without a Windows runner.
 */
val portableJars by tasks.registering(Sync::class) {
    from(tasks.named("jar"))
    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("portable/app"))
}

/** Native libraries and ffmpeg for the portable version (app/resources), see release-local.sh. */
val portableResources by tasks.registering(Sync::class) {
    from(tasks.named("speechDesktopNatives").map { it.outputs.files.singleFile.resolve("windows") })
    into(layout.buildDirectory.dir("portable/resources"))
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn("speechDesktopNatives") }
