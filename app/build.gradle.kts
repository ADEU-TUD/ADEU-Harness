import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.thepytales.llmhub"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "io.github.thepytales.llmhub"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0-dev"
    }

    // The release key is not part of the repository (whoever has it can publish "updates" that read API keys and
    // chats). It comes from keystore.properties next to settings.gradle.kts (not checked in, see
    // keystore.properties.example) or from the environment (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS,
    // KEY_PASSWORD; in GitHub Actions from secrets). Without it the release APK stays unsigned.
    val keystore = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
    }
    fun signing(env: String, key: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystore.getProperty(key)
    val storePath = signing("KEYSTORE_PATH", "storeFile")
    signingConfigs {
        if (storePath != null) create("release") {
            storeFile = rootProject.file(storePath)
            storePassword = signing("KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signing("KEY_ALIAS", "keyAlias")
            keyPassword = signing("KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 drops unused library code (smaller APK, faster start); see proguard-rules.pro for what is kept.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Native libraries unpacked at installation: sherpa-onnx loads from there, and the local language model
        // (libllama-server.so) can only be started from the app's library folder.
        jniLibs.useLegacyPackaging = true
    }

    sourceSets["main"].apply {
        // Android-only Java of the speech recognition (decoding with MediaCodec, Opus, microphone).
        java.srcDir("src/androidOnly/java")
        assets.srcDir(layout.buildDirectory.dir("speech/models"))
        jniLibs.srcDir(layout.buildDirectory.dir("speech/jni"))
        jniLibs.srcDir(layout.buildDirectory.dir("llama/jni"))
    }
}

// Speech recognition on the device: sherpa-onnx for Android and the built-in models (see speech/natives.gradle.kts).
apply(from = "../speech/natives.gradle.kts")
// Local language models: llama-server built with the NDK (see llama.gradle.kts).
apply(from = "llama.gradle.kts")
tasks.named("preBuild") { dependsOn("speechBuiltinModels", "speechAndroidJni") }

dependencies {
    implementation(project(":speech"))
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    // Text extraction from attached PDFs; encryption support (BouncyCastle) is not needed.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        exclude(group = "org.bouncycastle")
    }

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // JavaScript sandbox for exact calculations and data analysis (run_javascript).
    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Erkennungsmodell der Pseudonymisierung (AP-67): ONNX Runtime of sherpa-onnx through its C API, as on the PC.
    implementation("net.java.dev.jna:jna:5.15.0@aar")
    // Archiv-Werkzeug (AP-99) und Passwortschutz (AP-98), as on the PC: zip4j (ZIP with AES and in parts), Commons
    // Compress with XZ for Java (7z, TAR, GZ).
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
}
