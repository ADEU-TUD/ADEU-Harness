package io.github.thepytales.llmhub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted preferences for settings, API keys and environments (Windows: DPAPI, see the desktop version). */
fun createSecurePrefs(context: Context): SharedPreferences = try {
    val key = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        key,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
} catch (e: Exception) {
    // Some devices have a broken keystore; fall back to private preferences (and say so in the settings).
    io.github.thepytales.llmhub.ErrorLog.record("Verschlüsselter Speicher nicht verfügbar", e)
    SecureStorage.encrypted = false
    context.getSharedPreferences("settings_fallback", Context.MODE_PRIVATE)
}
