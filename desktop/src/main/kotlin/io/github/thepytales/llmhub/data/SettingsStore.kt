package io.github.thepytales.llmhub.data

import android.content.Context
import android.content.SharedPreferences
import io.github.thepytales.llmhub.desktop.Paths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val githubToken: String = "",
    /** GitHub account the token was verified for; empty while the token is unchecked or invalid. */
    val githubUser: String = "",
    val defaultModel: String = ModelCatalog.DEFAULT_CHAT,
    val systemPrompt: String = "",
    val imageModel: String = "alias-image-generation",
    val ttsModel: String = "tts-1-hd",
    val ttsVoice: String = "alloy",
    val sttModel: String = "alias-stt",
    val confirmWrites: Boolean = true,
    val maxAgentSteps: Int = 30,
    /** After maxAgentSteps tool rounds the agent continues on its own instead of asking for "weiter". */
    val autoContinue: Boolean = true,
    /** How much reasoning models think: enum name of net/ThinkingLevel. */
    val thinkingLevel: String = "AUTO",
    /** New chats get the GitHub tools automatically when a token is configured. */
    val githubAutoTools: Boolean = true,
    /** Offer the create_document tool (PDF, Word, Markdown …). */
    val documentTools: Boolean = true,
    /** Offer the fetch_url tool to read web pages. */
    val webTools: Boolean = true,
    /** Appearance: enum names from ui/Theme.kt (ThemeMode, AppStyle, Accent). */
    val themeMode: String = "SYSTEM",
    val appStyle: String = "CLAUDE",
    val accent: String = "AUTO",
    /** Pure black background in dark mode. */
    val amoled: Boolean = false,
    val textScale: Float = 1f,
    /** Environment used for new chats (empty = none). */
    val defaultEnvId: String = "",
    /** SERVER: speech model of the API; DEVICE: Android's offline text-to-speech. */
    val ttsEngine: String = "SERVER",
    /** Voice name of the Android TTS engine (empty = automatic). */
    val deviceVoice: String = "",
    val speechRate: Float = 1f,
    /** Look of generated documents: enum name of docs/DocStyle. */
    val docStyle: String = "MODERN",
    /** Accent color of documents as #RRGGBB. */
    val docAccent: String = "#1E40AF",
    /** Footer line for documents, e.g. name and address. */
    val docFooter: String = "",
    /** Unit below the TU Dresden logo (style TU Dresden), one level per line. */
    val tudUnit: String = DEFAULT_TUD_UNIT,
    /** Put the default logo into every document. */
    val autoLogo: Boolean = false,
    /** First name for the greeting and the avatar in the side menu (optional). */
    val userName: String = "",
    /** Windows: closing the window keeps the app running in the notification area (for answers and tasks). */
    val closeToTray: Boolean = true,
) {
    val hasApiKey get() = apiKey.isNotBlank()
    /** Only a verified token unlocks the GitHub options. */
    val hasGitHub get() = githubToken.isNotBlank() && githubUser.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://llm.scads.ai/v1"
        const val DEFAULT_TUD_UNIT = "Fakultät Erziehungswissenschaften\nInstitut für Erziehungswissenschaft\n" +
            "Professur für Allgemeine Didaktik und Empirische Unterrichtsforschung"
    }
}

/** Stores settings (incl. API keys) encrypted in the data folder. */
class SettingsStore(context: Context) {
    /** Encrypted preferences; also used for environments because they can contain secrets. */
    val prefs: SharedPreferences = createPrefs(context)
    private val _settings = MutableStateFlow(migrate(load()))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    /**
     * Version 2 changed the defaults: DeepSeek instead of Qwen and the Claude look. Installs that still
     * use the old defaults (or a model that is no longer offered) get the new ones once.
     */
    private fun migrate(s: AppSettings): AppSettings {
        if (prefs.getInt("settingsVersion", 1) >= SETTINGS_VERSION) return s
        val oldDefault = s.defaultModel == "Qwen/Qwen3.8-27B" || ModelCatalog.info(s.defaultModel).tier == ModelTier.HIDDEN
        val next = s.copy(
            defaultModel = if (oldDefault) ModelCatalog.DEFAULT_CHAT else s.defaultModel,
            appStyle = if (s.appStyle == "STANDARD") "CLAUDE" else s.appStyle,
        )
        save(next)
        return next
    }

    private fun save(next: AppSettings) {
        prefs.edit()
            .putInt("settingsVersion", SETTINGS_VERSION)
            .putString("apiKey", next.apiKey.trim())
            .putString("baseUrl", next.baseUrl.trim())
            .putString("githubToken", next.githubToken.trim())
            .putString("githubUser", next.githubUser)
            .putString("defaultModel", next.defaultModel)
            .putString("systemPrompt", next.systemPrompt)
            .putString("imageModel", next.imageModel)
            .putString("ttsModel", next.ttsModel)
            .putString("ttsVoice", next.ttsVoice)
            .putString("sttModel", next.sttModel)
            .putBoolean("confirmWrites", next.confirmWrites)
            .putInt("maxAgentSteps", next.maxAgentSteps)
            .putBoolean("autoContinue", next.autoContinue)
            .putString("thinkingLevel", next.thinkingLevel)
            .putBoolean("githubAutoTools", next.githubAutoTools)
            .putBoolean("documentTools", next.documentTools)
            .putBoolean("webTools", next.webTools)
            .putString("themeMode", next.themeMode)
            .putString("appStyle", next.appStyle)
            .putString("accent", next.accent)
            .putBoolean("amoled", next.amoled)
            .putFloat("textScale", next.textScale)
            .putString("defaultEnvId", next.defaultEnvId)
            .putString("ttsEngine", next.ttsEngine)
            .putString("deviceVoice", next.deviceVoice)
            .putFloat("speechRate", next.speechRate)
            .putString("docStyle", next.docStyle)
            .putString("docAccent", next.docAccent)
            .putString("docFooter", next.docFooter)
            .putString("tudUnit", next.tudUnit)
            .putBoolean("autoLogo", next.autoLogo)
            .putString("userName", next.userName)
            .putBoolean("closeToTray", next.closeToTray)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            baseUrl = prefs.getString("baseUrl", d.baseUrl)?.ifBlank { d.baseUrl } ?: d.baseUrl,
            githubToken = prefs.getString("githubToken", d.githubToken) ?: d.githubToken,
            githubUser = prefs.getString("githubUser", d.githubUser) ?: d.githubUser,
            defaultModel = prefs.getString("defaultModel", d.defaultModel) ?: d.defaultModel,
            systemPrompt = prefs.getString("systemPrompt", d.systemPrompt) ?: d.systemPrompt,
            imageModel = prefs.getString("imageModel", d.imageModel) ?: d.imageModel,
            ttsModel = prefs.getString("ttsModel", d.ttsModel) ?: d.ttsModel,
            ttsVoice = prefs.getString("ttsVoice", d.ttsVoice) ?: d.ttsVoice,
            sttModel = prefs.getString("sttModel", d.sttModel) ?: d.sttModel,
            confirmWrites = prefs.getBoolean("confirmWrites", d.confirmWrites),
            maxAgentSteps = prefs.getInt("maxAgentSteps", d.maxAgentSteps),
            autoContinue = prefs.getBoolean("autoContinue", d.autoContinue),
            thinkingLevel = prefs.getString("thinkingLevel", d.thinkingLevel) ?: d.thinkingLevel,
            githubAutoTools = prefs.getBoolean("githubAutoTools", d.githubAutoTools),
            documentTools = prefs.getBoolean("documentTools", d.documentTools),
            webTools = prefs.getBoolean("webTools", d.webTools),
            themeMode = prefs.getString("themeMode", d.themeMode) ?: d.themeMode,
            appStyle = prefs.getString("appStyle", d.appStyle) ?: d.appStyle,
            accent = prefs.getString("accent", d.accent) ?: d.accent,
            amoled = prefs.getBoolean("amoled", d.amoled),
            textScale = prefs.getFloat("textScale", d.textScale),
            defaultEnvId = prefs.getString("defaultEnvId", d.defaultEnvId) ?: d.defaultEnvId,
            ttsEngine = prefs.getString("ttsEngine", d.ttsEngine) ?: d.ttsEngine,
            deviceVoice = prefs.getString("deviceVoice", d.deviceVoice) ?: d.deviceVoice,
            speechRate = prefs.getFloat("speechRate", d.speechRate),
            docStyle = prefs.getString("docStyle", d.docStyle) ?: d.docStyle,
            docAccent = prefs.getString("docAccent", d.docAccent) ?: d.docAccent,
            docFooter = prefs.getString("docFooter", d.docFooter) ?: d.docFooter,
            tudUnit = prefs.getString("tudUnit", d.tudUnit) ?: d.tudUnit,
            autoLogo = prefs.getBoolean("autoLogo", d.autoLogo),
            userName = prefs.getString("userName", d.userName) ?: d.userName,
            closeToTray = prefs.getBoolean("closeToTray", d.closeToTray),
        )
    }

    private companion object {
        const val SETTINGS_VERSION = 2

        /** Encrypted with DPAPI: only this Windows user can read the API key and tokens. */
        @Suppress("UNUSED_PARAMETER")
        fun createPrefs(context: Context): SharedPreferences = FilePrefs.encrypted(File(Paths.data, "secure_settings.dat"))
    }
}
