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
    /** Model for answers in the Sprachmodus (quick); empty = the chat's model. */
    val voiceModel: String = ModelCatalog.FAST_CHAT,
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
    /** Formatvorlagen for documents without their own (AP-46 „Als Standard speichern“): their setting lines. */
    val docStyleLines: String = "",
    /** Autokorrektur in documents (AP-125): the rules switched off (ids of docs/text/AutoCorrect, comma-separated). */
    val docAutocorrectOff: String = "",
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
    /** Windows (AP-59): files deleted in Dateien go into the Windows Papierkorb instead of the Lora-Papierkorb. */
    val systemTrash: Boolean = false,
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
    /** Big tools switched on under Einstellungen › Werkzeuge: ids of data/AppTool, one per line (all off at first). */
    val enabledTools: String = "",
    /** Areas of the side menu shown on top (ids of ui/DrawerArea, one per line); the rest is under „Mehr“. */
    val drawerTop: String = DEFAULT_DRAWER_TOP,
    /** „Mehr“ in the side menu is unfolded. */
    val drawerMoreOpen: Boolean = false,
    /** Areas hidden from the side menu (ids of ui/DrawerArea, one per line); still reachable with Strg+K and Lora. */
    val drawerHidden: String = "",
    /** Where Lora appears: EVERYWHERE (start page, chat, settings …), CHAT (only in the chat) or OFF. */
    val loraMode: String = "EVERYWHERE",
    /** Model of Lora, the navigator (short answers, thinking off). */
    val loraModel: String = ModelCatalog.FAST_CHAT,
    /** Lora moves less (no hopping, no walking across the card). */
    val loraCalm: Boolean = false,
    /** Lora may give a one-time hint per area (e.g. that a tool exists). */
    val loraHints: Boolean = true,
    /** The two questions of the first start (Lora, visible tools) were answered or skipped. */
    val loraIntroDone: Boolean = false,
    /** AP-41: Lora offers a short tour on the first visit of a tool. */
    val loraTours: Boolean = true,
    /** AP-41: tours offered already (ids, one per line) – each is offered once. */
    val loraToursSeen: String = "",
    /** Model of the chats Lora hands questions to (AP-33): DeepSeek by default, "auto" = chosen per question. */
    val loraHandoffModel: String = ModelCatalog.DEFAULT_CHAT,
    /** A question Lora hands to a chat lands in the input field first instead of being sent right away (AP-33). */
    val loraHandoffReview: Boolean = false,
    /** Lora offers next steps under answers (reminder for a deadline, table as Excel …), found on the device (AP-35). */
    val loraNextSteps: Boolean = true,
    /** While typing in the chat, Lora points to a matching place of the app (AP-35). */
    val loraTyping: Boolean = true,
    /** Windows (AP-14): links open as tabs in the browser beside the chat instead of the system browser. */
    val browserPanel: Boolean = true,
    /** Android (AP-16): folder from the system dialog that gets a copy of the Lora-Ordner (content:// tree), "" = none. */
    val loraTree: String = "",
    /** Dateien tab (AP-16): grid instead of list. */
    val filesGrid: Boolean = false,
    /** Dateien tab: sorting („name“, „modified“, „kind“, „size“) and direction. */
    val filesSort: String = "name",
    val filesSortDown: Boolean = false,
    /** KI-Nutzungsnachweis (AP-19): offer the declaration once a chat about a work has text written by the AI. */
    val aiUseHint: Boolean = true,
    /** Texts from public sources (web search, books and papers with ISBN/DOI, the literature collection) stay as they are. */
    val pseudonymPublic: Boolean = true,
    /** Thorough recognition: a local language model looks for further names (slower; always on in the vault). */
    val pseudonymThorough: Boolean = false,
    /** Recognition model on this device (AP-67): finds names, institutions and places once it is downloaded. */
    val pseudonymModel: Boolean = true,
    /** Offer the download of the recognition model in a popup (off after „Nicht mehr fragen“). */
    val pseudonymModelAsk: Boolean = true,
    /** Offer the new version of Lora Namensschutz to who still has the model of 1.0.65 (off after „Nicht mehr fragen“). */
    val pseudonymModelUpdateAsk: Boolean = true,
    /** AP-117: SHA-256 of the version whose update got „Nicht mehr fragen“ – a newer version is offered again. */
    val pseudonymModelUpdateDeclined: String = "",
    /** Einstellungen › Datenschutz (AP-103): the parts of the page that are open, comma-separated (all folded at first). */
    val privacyOpen: String = "",
    /** Suche nach Bedeutung auf dem Gerät (AP-107): the Wissenssammlung uses the embedding model on this device once it is loaded. */
    val semanticLocal: Boolean = true,
    /** Where Lora works (AP-71): MIXED (per request), LOCAL (only on this device) or SERVER (language models only on the server). */
    val loraPlace: String = "MIXED",
    /**
     * Wo Lora arbeitet (AP-112): the place of the areas that have no setting of their own, „CHAT=LOCAL;VISION=MIXED“
     * (see data/Places); Lora, Vorlesen, Transkription and Suche keep their settings and are only stricter here.
     */
    val places: String = "",
    /** Einrichtung „Alles lokal“ (AP-112): the step to continue at (1 …), 0 = not started or finished. */
    val localSetupStep: Int = 0,
    /** Lora einrichten Schritt für Schritt (AP-120): the step to continue at (1 …), 0 = not started or finished. */
    val setupStep: Int = 0,
    /** Rückfall (AP-112): when the question „Uni-Server nicht mehr erreichbar“ was last answered with „Später“; -1 = never again. */
    val uniFallbackAsked: Long = 0,
    /** Befehlsmodell (AP-71): Lora and Strg+K understand commands on the device once it is downloaded. */
    val commandModel: Boolean = true,
    /** Offer the download of the command model in a popup (off after „Nicht mehr fragen“). */
    val commandModelAsk: Boolean = true,
    /** Offer the new version of Lora Befehle to who still has the model of 1.0.65 (off after „Nicht mehr fragen“). */
    val commandModelUpdateAsk: Boolean = true,
    /** AP-117: SHA-256 of the version whose update got „Nicht mehr fragen“ – a newer version is offered again. */
    val commandModelUpdateDeclined: String = "",
    /** Lora lernt aus Korrekturen (AP-72): corrected wishes are remembered on this device (Einstellungen › Lora). */
    val loraLearn: Boolean = true,
    /** Lora-Verlauf (AP-75): the last wishes to Lora are kept on this device. */
    val loraHistory: Boolean = true,
    /** Rollenauswahl (AP-80): „TEACHING,RESEARCH“ … – what Lora is used for, from the first start; empty = not asked. */
    val roles: String = "",
    /** The question „Welche Modelle sollen auf dieses Gerät?“ was answered (first start, or once for those who used the app before AP-71). */
    val modelsIntroDone: Boolean = false,
    /** Modellkatalog nach Zweck (AP-106): „TEXT,CODE“ … – specialists besides the allrounder; empty = from the roles. */
    val localPurposes: String = "",
    /** Nextcloud (AP-22): server address, login name and app password (secret), folder for results. */
    val nextcloudServer: String = "",
    val nextcloudUser: String = "",
    val nextcloudPassword: String = "",
    val nextcloudFolder: String = "/Lora",
    /** Sync für Android (AP-28): the Lora-Ordner of the PC in the Nextcloud („/Lora“); empty = off. */
    val phoneSyncRoot: String = "",
    /** Sync für Android also in the background, without the app open (AP-63, WorkManager every 30 minutes). */
    val phoneSyncBackground: Boolean = true,
    /** The background sync only on Wi-Fi (no mobile data). */
    val phoneSyncWifiOnly: Boolean = false,
    /** Mail über den Server (AP-27): IMAP server, port, login, password (secret), own address; on Windows by choice. */
    val mailHost: String = "",
    val mailPort: Int = 993,
    val mailUser: String = "",
    val mailPassword: String = "",
    val mailAddress: String = "",
    /** Windows: use the server instead of the classic Outlook (Android always uses the server). */
    val mailUseServer: Boolean = false,
    /** Stapel (AP-20): how many requests per minute a batch may use at most, so chats stay quick (0 = no own limit). */
    val batchPerMinute: Int = 0,
) {
    val mailServer: MailServerConfig get() = MailServerConfig(mailHost.trim(), mailPort, mailUser.trim(), mailPassword, mailAddress.trim())

    /** Nextcloud is set up (Einstellungen › Verbindungen). */
    val hasNextcloud get() = nextcloudServer.isNotBlank() && nextcloudUser.isNotBlank() && nextcloudPassword.isNotBlank()

    /** Tools switched on (see data/AppTool). */
    val enabledToolIds: Set<String> get() = enabledTools.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun toolOn(id: String) = id in enabledToolIds

    val drawerHiddenIds: Set<String> get() = drawerHidden.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

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
        const val DEFAULT_DRAWER_TOP = "templates\nliterature\ntranscripts\nfiles"
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
            .putString("voiceModel", next.voiceModel)
            .putString("defaultEnvId", next.defaultEnvId)
            .putString("ttsEngine", next.ttsEngine)
            .putString("deviceVoice", next.deviceVoice)
            .putFloat("speechRate", next.speechRate)
            .putString("docStyle", next.docStyle)
            .putString("docAccent", next.docAccent)
            .putString("docFooter", next.docFooter)
            .putString("tudUnit", next.tudUnit)
            .putString("docStyleLines", next.docStyleLines)
            .putString("docAutocorrectOff", next.docAutocorrectOff)
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
            .putBoolean("systemTrash", next.systemTrash)
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
            .putString("enabledTools", next.enabledTools)
            .putString("nextcloudServer", next.nextcloudServer.trim())
            .putString("nextcloudUser", next.nextcloudUser.trim())
            .putString("nextcloudPassword", next.nextcloudPassword.trim())
            .putString("nextcloudFolder", next.nextcloudFolder.trim())
            .putString("phoneSyncRoot", next.phoneSyncRoot.trim())
            .putBoolean("phoneSyncBackground", next.phoneSyncBackground)
            .putBoolean("phoneSyncWifiOnly", next.phoneSyncWifiOnly)
            .putString("mailHost", next.mailHost.trim())
            .putInt("mailPort", next.mailPort)
            .putString("mailUser", next.mailUser.trim())
            .putString("mailPassword", next.mailPassword)
            .putString("mailAddress", next.mailAddress.trim())
            .putBoolean("mailUseServer", next.mailUseServer)
            .putInt("batchPerMinute", next.batchPerMinute)
            .putString("drawerTop", next.drawerTop)
            .putBoolean("drawerFilesOnce", true)
            .putBoolean("drawerMoreOpen", next.drawerMoreOpen)
            .putString("drawerHidden", next.drawerHidden)
            .putString("loraMode", next.loraMode)
            .putString("loraModel", next.loraModel)
            .putBoolean("loraCalm", next.loraCalm)
            .putBoolean("loraHints", next.loraHints)
            .putString("loraHandoffModel", next.loraHandoffModel)
            .putBoolean("loraHandoffReview", next.loraHandoffReview)
            .putBoolean("loraNextSteps", next.loraNextSteps)
            .putBoolean("loraTyping", next.loraTyping)
            .putBoolean("browserPanel", next.browserPanel)
            .putString("loraTree", next.loraTree)
            .putBoolean("filesGrid", next.filesGrid)
            .putString("filesSort", next.filesSort)
            .putBoolean("filesSortDown", next.filesSortDown)
            .putBoolean("aiUseHint", next.aiUseHint)
            .putBoolean("loraIntroDone", next.loraIntroDone)
            .putBoolean("loraTours", next.loraTours)
            .putString("loraToursSeen", next.loraToursSeen)
            .putBoolean("pseudonymPublic", next.pseudonymPublic)
            .putBoolean("pseudonymThorough", next.pseudonymThorough)
            .putBoolean("pseudonymModel", next.pseudonymModel)
            .putBoolean("pseudonymModelAsk", next.pseudonymModelAsk)
            .putBoolean("pseudonymModelUpdateAsk", next.pseudonymModelUpdateAsk)
            .putString("pseudonymModelUpdateDeclined", next.pseudonymModelUpdateDeclined)
            .putString("privacyOpen", next.privacyOpen)
            .putBoolean("semanticLocal", next.semanticLocal)
            .putString("loraPlace", next.loraPlace)
            .putString("places", next.places)
            .putInt("localSetupStep", next.localSetupStep)
            .putInt("setupStep", next.setupStep)
            .putLong("uniFallbackAsked", next.uniFallbackAsked)
            .putBoolean("commandModel", next.commandModel)
            .putBoolean("commandModelAsk", next.commandModelAsk)
            .putBoolean("commandModelUpdateAsk", next.commandModelUpdateAsk)
            .putString("commandModelUpdateDeclined", next.commandModelUpdateDeclined)
            .putBoolean("loraLearn", next.loraLearn)
            .putBoolean("loraHistory", next.loraHistory)
            .putString("roles", next.roles)
            .putBoolean("modelsIntroDone", next.modelsIntroDone)
            .putString("localPurposes", next.localPurposes)
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
            voiceModel = prefs.getString("voiceModel", d.voiceModel) ?: d.voiceModel,
            defaultEnvId = prefs.getString("defaultEnvId", d.defaultEnvId) ?: d.defaultEnvId,
            ttsEngine = prefs.getString("ttsEngine", d.ttsEngine) ?: d.ttsEngine,
            deviceVoice = prefs.getString("deviceVoice", d.deviceVoice) ?: d.deviceVoice,
            speechRate = prefs.getFloat("speechRate", d.speechRate),
            docStyle = prefs.getString("docStyle", d.docStyle) ?: d.docStyle,
            docAccent = prefs.getString("docAccent", d.docAccent) ?: d.docAccent,
            docFooter = prefs.getString("docFooter", d.docFooter) ?: d.docFooter,
            tudUnit = prefs.getString("tudUnit", d.tudUnit) ?: d.tudUnit,
            docStyleLines = prefs.getString("docStyleLines", d.docStyleLines) ?: d.docStyleLines,
            docAutocorrectOff = prefs.getString("docAutocorrectOff", d.docAutocorrectOff) ?: d.docAutocorrectOff,
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
            systemTrash = prefs.getBoolean("systemTrash", d.systemTrash),
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
            enabledTools = prefs.getString("enabledTools", d.enabledTools) ?: d.enabledTools,
            drawerTop = (prefs.getString("drawerTop", d.drawerTop) ?: d.drawerTop).let { top ->
                // AP-16: „Dokumente“ became „Dateien“ – once on top of the side menu where there is room.
                val lines = top.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { if (it == "docs") "files" else it }.distinct()
                if (!prefs.getBoolean("drawerFilesOnce", false) && "files" !in lines && lines.size < 4) (lines + "files").joinToString("\n")
                else lines.joinToString("\n")
            },
            drawerMoreOpen = prefs.getBoolean("drawerMoreOpen", d.drawerMoreOpen),
            drawerHidden = prefs.getString("drawerHidden", d.drawerHidden) ?: d.drawerHidden,
            // „Lora ausblenden“ became Lora: aus.
            loraMode = prefs.getString("loraMode", null) ?: if (prefs.getBoolean("hideLora", false)) "OFF" else d.loraMode,
            loraModel = prefs.getString("loraModel", d.loraModel) ?: d.loraModel,
            loraCalm = prefs.getBoolean("loraCalm", d.loraCalm),
            loraHints = prefs.getBoolean("loraHints", d.loraHints),
            loraTours = prefs.getBoolean("loraTours", d.loraTours),
            loraToursSeen = prefs.getString("loraToursSeen", d.loraToursSeen) ?: d.loraToursSeen,
            loraHandoffModel = prefs.getString("loraHandoffModel", d.loraHandoffModel) ?: d.loraHandoffModel,
            loraHandoffReview = prefs.getBoolean("loraHandoffReview", d.loraHandoffReview),
            loraNextSteps = prefs.getBoolean("loraNextSteps", d.loraNextSteps),
            loraTyping = prefs.getBoolean("loraTyping", d.loraTyping),
            browserPanel = prefs.getBoolean("browserPanel", d.browserPanel),
            loraTree = prefs.getString("loraTree", d.loraTree) ?: d.loraTree,
            filesGrid = prefs.getBoolean("filesGrid", d.filesGrid),
            filesSort = prefs.getString("filesSort", d.filesSort) ?: d.filesSort,
            filesSortDown = prefs.getBoolean("filesSortDown", d.filesSortDown),
            aiUseHint = prefs.getBoolean("aiUseHint", d.aiUseHint),
            loraIntroDone = prefs.getBoolean("loraIntroDone", d.loraIntroDone),
            pseudonymPublic = prefs.getBoolean("pseudonymPublic", d.pseudonymPublic),
            pseudonymThorough = prefs.getBoolean("pseudonymThorough", d.pseudonymThorough),
            pseudonymModel = prefs.getBoolean("pseudonymModel", d.pseudonymModel),
            pseudonymModelAsk = prefs.getBoolean("pseudonymModelAsk", d.pseudonymModelAsk),
            pseudonymModelUpdateAsk = prefs.getBoolean("pseudonymModelUpdateAsk", d.pseudonymModelUpdateAsk),
            pseudonymModelUpdateDeclined = prefs.getString("pseudonymModelUpdateDeclined", d.pseudonymModelUpdateDeclined) ?: d.pseudonymModelUpdateDeclined,
            privacyOpen = prefs.getString("privacyOpen", d.privacyOpen) ?: d.privacyOpen,
            semanticLocal = prefs.getBoolean("semanticLocal", d.semanticLocal),
            loraPlace = prefs.getString("loraPlace", d.loraPlace) ?: d.loraPlace,
            places = prefs.getString("places", d.places) ?: d.places,
            localSetupStep = prefs.getInt("localSetupStep", d.localSetupStep),
            setupStep = prefs.getInt("setupStep", d.setupStep),
            uniFallbackAsked = prefs.getLong("uniFallbackAsked", d.uniFallbackAsked),
            commandModel = prefs.getBoolean("commandModel", d.commandModel),
            commandModelAsk = prefs.getBoolean("commandModelAsk", d.commandModelAsk),
            commandModelUpdateAsk = prefs.getBoolean("commandModelUpdateAsk", d.commandModelUpdateAsk),
            commandModelUpdateDeclined = prefs.getString("commandModelUpdateDeclined", d.commandModelUpdateDeclined) ?: d.commandModelUpdateDeclined,
            loraLearn = prefs.getBoolean("loraLearn", d.loraLearn),
            loraHistory = prefs.getBoolean("loraHistory", d.loraHistory),
            roles = prefs.getString("roles", d.roles) ?: d.roles,
            modelsIntroDone = prefs.getBoolean("modelsIntroDone", d.modelsIntroDone),
            localPurposes = prefs.getString("localPurposes", d.localPurposes) ?: d.localPurposes,
            nextcloudServer = prefs.getString("nextcloudServer", d.nextcloudServer) ?: d.nextcloudServer,
            nextcloudUser = prefs.getString("nextcloudUser", d.nextcloudUser) ?: d.nextcloudUser,
            nextcloudPassword = prefs.getString("nextcloudPassword", d.nextcloudPassword) ?: d.nextcloudPassword,
            nextcloudFolder = prefs.getString("nextcloudFolder", d.nextcloudFolder) ?: d.nextcloudFolder,
            phoneSyncRoot = prefs.getString("phoneSyncRoot", d.phoneSyncRoot) ?: d.phoneSyncRoot,
            phoneSyncBackground = prefs.getBoolean("phoneSyncBackground", d.phoneSyncBackground),
            phoneSyncWifiOnly = prefs.getBoolean("phoneSyncWifiOnly", d.phoneSyncWifiOnly),
            mailHost = prefs.getString("mailHost", d.mailHost) ?: d.mailHost,
            mailPort = prefs.getInt("mailPort", d.mailPort),
            mailUser = prefs.getString("mailUser", d.mailUser) ?: d.mailUser,
            mailPassword = prefs.getString("mailPassword", d.mailPassword) ?: d.mailPassword,
            mailAddress = prefs.getString("mailAddress", d.mailAddress) ?: d.mailAddress,
            mailUseServer = prefs.getBoolean("mailUseServer", d.mailUseServer),
            batchPerMinute = prefs.getInt("batchPerMinute", d.batchPerMinute),
        )
    }

    private companion object {
        const val SETTINGS_VERSION = 4
    }
}
