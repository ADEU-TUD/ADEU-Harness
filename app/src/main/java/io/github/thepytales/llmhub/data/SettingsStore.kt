package io.github.thepytales.llmhub.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val githubToken: String = "",
    /** GitHub account the token was verified for; empty while the token is unchecked or invalid. */
    val githubUser: String = "",
    /** Further GitHub tokens next to the main one, e.g. a fine-grained token per organization. */
    val githubAccounts: List<GitHubAccount> = emptyList(),
    /** Further API providers next to the main key (ScaDS.AI), e.g. Google Gemini. */
    val providers: List<ApiProvider> = emptyList(),
    val defaultModel: String = ModelCatalog.DEFAULT_CHAT,
    val systemPrompt: String = "",
    val imageModel: String = "alias-image-generation",
    val ttsModel: String = "tts-1-hd",
    val ttsVoice: String = "alloy",
    val sttModel: String = "alias-stt",
    /** When the agent asks before write actions in chats: enum name of agent/ApprovalMode. */
    val approvalMode: String = "ASK",
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
    val appStyle: String = "ADEU",
    val accent: String = "AUTO",
    /** Pure black background in dark mode. */
    val amoled: Boolean = false,
    val textScale: Float = 1f,
    /** Zoom of the whole interface on Windows (Strg+/Strg−/Strg+0), 0.8 to 2. */
    val uiZoom: Float = 1f,
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
    /** Own details for letters and e-mails (name, function, phone, e-mail, room); prefills the "Absender" fields of templates. */
    val senderInfo: String = "",
    /** Ids of the templates marked as favourite, in the order they were added. */
    val favoriteTemplates: List<String> = emptyList(),
    /** First name for the greeting and the avatar in the side menu (optional). */
    val userName: String = "",
    /** Model used when the chosen one stays unreachable (502/503/504): "auto", "off" or a model id. */
    val fallbackModel: String = "auto",
    /** Requests per minute the app allows itself (0 = learn the limit from the server's 429 answers). */
    val requestsPerMinute: Int = 0,
    /** What the model may note about the user: enum name of agent/MemoryMode. */
    val memoryMode: String = "ASK",
    /** Template forms suggest values from memory, sender details, knowledge and earlier entries (see agent/FieldSuggestions). */
    val formSuggestions: Boolean = true,
    /** Replace personal details by placeholders before anything goes to a server (see data/Pseudonymizer). */
    val pseudonymize: Boolean = true,
    /** Also find names on their own (after Frau/Herr/Dr., frequent first names), not only the own list. */
    val pseudonymAuto: Boolean = true,
    /** Own names and terms that are always replaced, one per line (class lists, schools …). */
    val pseudonymTerms: String = "",
    /** Words that are never replaced although they look like names ("Frank" as a word, a town called "Martin"), one per line. */
    val pseudonymIgnore: String = "",
    /** Switched-off parts of the pseudonymization: enum names of data/PiiFeature, one per line (all on by default). */
    val pseudonymOff: String = "",
    /** Windows: closing the window keeps the app running in the notification area (for answers and tasks). */
    val closeToTray: Boolean = true,
    /** Short animation of the logo when the app starts. */
    val startAnimation: Boolean = true,
    /** Citation style of reference lists: enum name of research/CitationStyle. */
    val citationStyle: String = "APA7_DE",
    /** Free OpenAlex API key (without one all users behind the same IP share a small daily budget). */
    val openAlexKey: String = "",
    /** Zotero web API: user ID and key with write access (zotero.org/settings/keys). */
    val zoteroUserId: String = "",
    val zoteroKey: String = "",
    /** Databases of the literature search, comma-separated enum names of research/LitSource. */
    val literatureSources: String = "OPENALEX,CROSSREF,ERIC,DNB,SLUB",
    /** Language of the app's interface: "de" or "en". */
    val uiLanguage: String = "de",
    /** Name or initials of the person coding transcripts (intercoder agreement). */
    val coderName: String = "",
    /** While the agent works, thinking and tool steps are folded away behind Lora, the animated mascot (see ui/LoraFigure). */
    val collapseSteps: Boolean = true,
    /** Lora, the mascot, nowhere (start page, working card, voice mode): a calm symbol of the style instead. */
    val hideLora: Boolean = false,
    /** After a chat, a small model looks for lasting details about the user and suggests them (see agent/MemoryLearner). */
    val memoryLearn: Boolean = true,
    /** The model may search earlier chats (search_chats) and knows the titles of the latest ones. */
    val memoryChats: Boolean = true,
    /** The three questions that fill an empty memory were answered or dismissed. */
    val memoryIntroDone: Boolean = false,
    /** New chats start with the automatic model choice (Automatisch in the model picker). */
    val autoModel: Boolean = false,
    /** Automatic model choice: ask before a request goes to another model than the current one. */
    val autoModelAsk: Boolean = true,
) {
    /** Parts of the pseudonymization that are switched off (Einstellungen › Datenschutz). */
    val pseudonymOffFeatures: Set<PiiFeature>
        get() = pseudonymOff.lines().mapNotNull { l -> PiiFeature.entries.firstOrNull { it.name == l.trim() } }.toSet()

    fun pii(f: PiiFeature) = pseudonymize && f !in pseudonymOffFeatures

    /** Key of the main provider (ScaDS.AI); speech, images and embeddings only work with it. */
    val hasApiKey get() = apiKey.isNotBlank()
    /** Chatting works with the main key or any further provider. */
    val hasAnyApiKey get() = hasApiKey || providers.any { it.usable }
    /** Only a verified token unlocks the GitHub options. */
    val hasGitHub get() = githubToken.isNotBlank() && githubUser.isNotBlank() ||
        githubAccounts.any { it.token.isNotBlank() && it.user.isNotBlank() }
    /** Number of verified GitHub tokens (main token and further ones). */
    val githubTokenCount get() = (if (githubToken.isNotBlank() && githubUser.isNotBlank()) 1 else 0) +
        githubAccounts.count { it.token.isNotBlank() && it.user.isNotBlank() }

    companion object {
        const val DEFAULT_BASE_URL = "https://llm.scads.ai/v1"
        const val DEFAULT_TUD_UNIT = "Fakultät Erziehungswissenschaften\nInstitut für Erziehungswissenschaft\n" +
            "Professur für Allgemeine Didaktik und Empirische Unterrichtsforschung"
    }
}

/** Stores settings (incl. API keys) encrypted: Android keystore, on Windows DPAPI (see [createSecurePrefs]). */
class SettingsStore(context: Context) {
    /** Encrypted preferences; also used for environments because they can contain secrets. */
    val prefs: SharedPreferences = createSecurePrefs(context)
    private val _settings = MutableStateFlow(migrate(load()))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    /**
     * Version 2 changed the defaults: DeepSeek instead of Qwen. Installs that still use the old default (or a model
     * that is no longer offered) get the new one once. Version 3 brought the common ADEU design (the look of the
     * transcription), which every install gets once; the other looks stay selectable. Version 4 switched the
     * pseudonymization on once.
     */
    private fun migrate(s: AppSettings): AppSettings {
        val version = prefs.getInt("settingsVersion", 1)
        if (version >= SETTINGS_VERSION) return s
        val oldDefault = s.defaultModel == "Qwen/Qwen3.8-27B" || ModelCatalog.info(s.defaultModel).tier == ModelTier.HIDDEN
        val next = s.copy(
            defaultModel = if (oldDefault) ModelCatalog.DEFAULT_CHAT else s.defaultModel,
            appStyle = if (version < 3) "ADEU" else s.appStyle,
            // Version 4: the pseudonymization is on for everyone once (it can be switched off again).
            pseudonymize = if (version < 4) true else s.pseudonymize,
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
            .putString("githubAccounts", AccountsJson.accounts(next.githubAccounts.map { it.copy(token = it.token.trim()) }))
            .putString("providers", AccountsJson.providers(next.providers.map { it.copy(apiKey = it.apiKey.trim(), baseUrl = it.baseUrl.trim()) }))
            .putString("defaultModel", next.defaultModel)
            .putString("systemPrompt", next.systemPrompt)
            .putString("imageModel", next.imageModel)
            .putString("ttsModel", next.ttsModel)
            .putString("ttsVoice", next.ttsVoice)
            .putString("sttModel", next.sttModel)
            .putString("approvalMode", next.approvalMode)
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
            .putFloat("uiZoom", next.uiZoom)
            .putString("defaultEnvId", next.defaultEnvId)
            .putString("ttsEngine", next.ttsEngine)
            .putString("deviceVoice", next.deviceVoice)
            .putFloat("speechRate", next.speechRate)
            .putString("docStyle", next.docStyle)
            .putString("docAccent", next.docAccent)
            .putString("docFooter", next.docFooter)
            .putString("tudUnit", next.tudUnit)
            .putBoolean("autoLogo", next.autoLogo)
            .putString("senderInfo", next.senderInfo)
            .putString("favoriteTemplates", next.favoriteTemplates.joinToString("\n"))
            .putString("userName", next.userName)
            .putString("fallbackModel", next.fallbackModel)
            .putInt("requestsPerMinute", next.requestsPerMinute)
            .putString("memoryMode", next.memoryMode)
            .putBoolean("formSuggestions", next.formSuggestions)
            .putBoolean("pseudonymize", next.pseudonymize)
            .putBoolean("pseudonymAuto", next.pseudonymAuto)
            .putString("pseudonymTerms", next.pseudonymTerms)
            .putString("pseudonymIgnore", next.pseudonymIgnore)
            .putString("pseudonymOff", next.pseudonymOff)
            .putBoolean("closeToTray", next.closeToTray)
            .putBoolean("startAnimation", next.startAnimation)
            .putString("citationStyle", next.citationStyle)
            .putString("openAlexKey", next.openAlexKey.trim())
            .putString("zoteroUserId", next.zoteroUserId.trim())
            .putString("zoteroKey", next.zoteroKey.trim())
            .putString("literatureSources", next.literatureSources)
            .putString("uiLanguage", next.uiLanguage)
            .putString("coderName", next.coderName)
            .putBoolean("collapseSteps", next.collapseSteps)
            .putBoolean("hideLora", next.hideLora)
            .putBoolean("memoryLearn", next.memoryLearn)
            .putBoolean("memoryChats", next.memoryChats)
            .putBoolean("memoryIntroDone", next.memoryIntroDone)
            .putBoolean("autoModelAsk", next.autoModelAsk)
            .putBoolean("autoModel", next.autoModel)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            baseUrl = prefs.getString("baseUrl", d.baseUrl)?.ifBlank { d.baseUrl } ?: d.baseUrl,
            githubToken = prefs.getString("githubToken", d.githubToken) ?: d.githubToken,
            githubUser = prefs.getString("githubUser", d.githubUser) ?: d.githubUser,
            githubAccounts = AccountsJson.accounts(prefs.getString("githubAccounts", null)),
            providers = AccountsJson.providers(prefs.getString("providers", null)),
            defaultModel = prefs.getString("defaultModel", d.defaultModel) ?: d.defaultModel,
            systemPrompt = prefs.getString("systemPrompt", d.systemPrompt) ?: d.systemPrompt,
            imageModel = prefs.getString("imageModel", d.imageModel) ?: d.imageModel,
            ttsModel = prefs.getString("ttsModel", d.ttsModel) ?: d.ttsModel,
            ttsVoice = prefs.getString("ttsVoice", d.ttsVoice) ?: d.ttsVoice,
            sttModel = prefs.getString("sttModel", d.sttModel) ?: d.sttModel,
            // Before the approval modes there was only "Schreibaktionen bestätigen" (on/off).
            approvalMode = prefs.getString("approvalMode", null) ?: if (prefs.getBoolean("confirmWrites", true)) "ASK" else "ALL",
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
            uiZoom = prefs.getFloat("uiZoom", d.uiZoom),
            defaultEnvId = prefs.getString("defaultEnvId", d.defaultEnvId) ?: d.defaultEnvId,
            ttsEngine = prefs.getString("ttsEngine", d.ttsEngine) ?: d.ttsEngine,
            deviceVoice = prefs.getString("deviceVoice", d.deviceVoice) ?: d.deviceVoice,
            speechRate = prefs.getFloat("speechRate", d.speechRate),
            docStyle = prefs.getString("docStyle", d.docStyle) ?: d.docStyle,
            docAccent = prefs.getString("docAccent", d.docAccent) ?: d.docAccent,
            docFooter = prefs.getString("docFooter", d.docFooter) ?: d.docFooter,
            tudUnit = prefs.getString("tudUnit", d.tudUnit) ?: d.tudUnit,
            autoLogo = prefs.getBoolean("autoLogo", d.autoLogo),
            senderInfo = prefs.getString("senderInfo", d.senderInfo) ?: d.senderInfo,
            favoriteTemplates = prefs.getString("favoriteTemplates", "").orEmpty().lines().filter { it.isNotBlank() },
            userName = prefs.getString("userName", d.userName) ?: d.userName,
            fallbackModel = prefs.getString("fallbackModel", d.fallbackModel) ?: d.fallbackModel,
            requestsPerMinute = prefs.getInt("requestsPerMinute", d.requestsPerMinute),
            memoryMode = prefs.getString("memoryMode", d.memoryMode) ?: d.memoryMode,
            formSuggestions = prefs.getBoolean("formSuggestions", d.formSuggestions),
            pseudonymize = prefs.getBoolean("pseudonymize", d.pseudonymize),
            pseudonymAuto = prefs.getBoolean("pseudonymAuto", d.pseudonymAuto),
            pseudonymTerms = prefs.getString("pseudonymTerms", d.pseudonymTerms) ?: d.pseudonymTerms,
            pseudonymIgnore = prefs.getString("pseudonymIgnore", d.pseudonymIgnore) ?: d.pseudonymIgnore,
            pseudonymOff = prefs.getString("pseudonymOff", d.pseudonymOff) ?: d.pseudonymOff,
            closeToTray = prefs.getBoolean("closeToTray", d.closeToTray),
            startAnimation = prefs.getBoolean("startAnimation", d.startAnimation),
            citationStyle = prefs.getString("citationStyle", d.citationStyle) ?: d.citationStyle,
            openAlexKey = prefs.getString("openAlexKey", d.openAlexKey) ?: d.openAlexKey,
            zoteroUserId = prefs.getString("zoteroUserId", d.zoteroUserId) ?: d.zoteroUserId,
            zoteroKey = prefs.getString("zoteroKey", d.zoteroKey) ?: d.zoteroKey,
            literatureSources = prefs.getString("literatureSources", d.literatureSources) ?: d.literatureSources,
            uiLanguage = prefs.getString("uiLanguage", d.uiLanguage) ?: d.uiLanguage,
            coderName = prefs.getString("coderName", d.coderName) ?: d.coderName,
            collapseSteps = prefs.getBoolean("collapseSteps", d.collapseSteps),
            hideLora = prefs.getBoolean("hideLora", d.hideLora),
            memoryLearn = prefs.getBoolean("memoryLearn", d.memoryLearn),
            memoryChats = prefs.getBoolean("memoryChats", d.memoryChats),
            memoryIntroDone = prefs.getBoolean("memoryIntroDone", d.memoryIntroDone),
            autoModelAsk = prefs.getBoolean("autoModelAsk", d.autoModelAsk),
            autoModel = prefs.getBoolean("autoModel", d.autoModel),
        )
    }

    private companion object {
        const val SETTINGS_VERSION = 4
    }
}
