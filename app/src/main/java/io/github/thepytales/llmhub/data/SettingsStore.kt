package io.github.thepytales.llmhub.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val githubToken: String = "",
    val defaultModel: String = "Qwen/Qwen3.8-27B",
    val systemPrompt: String = "",
    val imageModel: String = "alias-image-generation",
    val ttsModel: String = "tts-1-hd",
    val ttsVoice: String = "alloy",
    val sttModel: String = "alias-stt",
    val confirmWrites: Boolean = true,
    val maxAgentSteps: Int = 30,
) {
    val hasApiKey get() = apiKey.isNotBlank()
    val hasGitHub get() = githubToken.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://llm.scads.ai/v1"
    }
}

/** Stores settings (incl. API keys) in encrypted shared preferences. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString("apiKey", next.apiKey.trim())
            .putString("baseUrl", next.baseUrl.trim())
            .putString("githubToken", next.githubToken.trim())
            .putString("defaultModel", next.defaultModel)
            .putString("systemPrompt", next.systemPrompt)
            .putString("imageModel", next.imageModel)
            .putString("ttsModel", next.ttsModel)
            .putString("ttsVoice", next.ttsVoice)
            .putString("sttModel", next.sttModel)
            .putBoolean("confirmWrites", next.confirmWrites)
            .putInt("maxAgentSteps", next.maxAgentSteps)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            baseUrl = prefs.getString("baseUrl", d.baseUrl)?.ifBlank { d.baseUrl } ?: d.baseUrl,
            githubToken = prefs.getString("githubToken", d.githubToken) ?: d.githubToken,
            defaultModel = prefs.getString("defaultModel", d.defaultModel) ?: d.defaultModel,
            systemPrompt = prefs.getString("systemPrompt", d.systemPrompt) ?: d.systemPrompt,
            imageModel = prefs.getString("imageModel", d.imageModel) ?: d.imageModel,
            ttsModel = prefs.getString("ttsModel", d.ttsModel) ?: d.ttsModel,
            ttsVoice = prefs.getString("ttsVoice", d.ttsVoice) ?: d.ttsVoice,
            sttModel = prefs.getString("sttModel", d.sttModel) ?: d.sttModel,
            confirmWrites = prefs.getBoolean("confirmWrites", d.confirmWrites),
            maxAgentSteps = prefs.getInt("maxAgentSteps", d.maxAgentSteps),
        )
    }

    private companion object {
        fun createPrefs(context: Context): SharedPreferences = try {
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
            // Some devices have a broken keystore; fall back to private preferences.
            context.getSharedPreferences("settings_fallback", Context.MODE_PRIVATE)
        }
    }
}
