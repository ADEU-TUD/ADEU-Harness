# R8 for the release APK: shrinks the libraries (above all material-icons-extended) but keeps names, so crash
# reports stay readable, and leaves code alone that is reached by reflection or from native code.
-dontobfuscate

# The app itself (serializers, view models created by name, the print bridge in android.print).
-keep class io.github.thepytales.** { *; }
-keep class android.print.** { *; }

# Speech recognition: Java of the engine and sherpa-onnx, called back from native code (JNI).
-keep class de.lokal.whisper.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }

# JavaScript sandbox: Rhino looks up its classes by name.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# PDF text extraction (PdfBox-Android loads fonts, filters and resources by name).
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**

# kotlinx.serialization: generated serializers are found through companion objects.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio: optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Encrypted settings (security-crypto with Tink).
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# Optional parts of Tink that the app does not use (key download over Google's HTTP client).
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
