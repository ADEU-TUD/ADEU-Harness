package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Settings that move to another device (no device-specific or secret values). */
@Serializable
data class PortableSettings(
    val defaultModel: String, val systemPrompt: String, val approvalMode: String, val maxAgentSteps: Int,
    val autoContinue: Boolean, val thinkingLevel: String, val documentTools: Boolean, val webTools: Boolean,
    val themeMode: String, val appStyle: String, val accent: String, val amoled: Boolean, val textScale: Float,
    val ttsEngine: String, val ttsVoice: String, val speechRate: Float, val docStyle: String, val docAccent: String,
    val docFooter: String, val tudUnit: String, val autoLogo: Boolean, val senderInfo: String,
    val favoriteTemplates: List<String>, val userName: String, val fallbackModel: String, val requestsPerMinute: Int,
    val memoryMode: String,
    val pseudonymize: Boolean = true, val pseudonymAuto: Boolean = true, val pseudonymTerms: String = "",
    val citationStyle: String = "APA7_DE", val zoteroUserId: String = "", val literatureSources: String = "OPENALEX,CROSSREF,ERIC,DNB",
    val uiLanguage: String = "de", val coderName: String = "",
    val formSuggestions: Boolean = true,
    val pseudonymIgnore: String = "", val pseudonymOff: String = "",
)

@Serializable
data class BackupManifest(
    val version: Int,
    val appVersion: String,
    val createdAt: Long,
    /** Data folder of the device the backup came from; paths inside the data are moved to the new one. */
    val root: String,
    val settings: PortableSettings,
    val environments: List<AgentEnvironment> = emptyList(),
    val apiKey: String? = null,
    val githubToken: String? = null,
    /** Further GitHub tokens (only with secrets). */
    val githubTokens: List<String> = emptyList(),
    /** Further API providers; their keys are only in [secrets]. */
    val providers: List<ApiProvider> = emptyList(),
    /** API keys, tokens and secret variables, encrypted with the password chosen at export ([BackupSecrets]). */
    val secrets: SecretBox? = null,
)

/** The secret part of a backup; stored only encrypted (see [BackupManifest.secrets]). */
@Serializable
data class BackupSecrets(
    val apiKey: String? = null,
    val githubToken: String? = null,
    val githubTokens: List<String> = emptyList(),
    /** Provider id → API key. */
    val providerKeys: Map<String, String> = emptyMap(),
    /** Environment id → variable name → value of secret variables. */
    val envSecrets: Map<String, Map<String, String>> = emptyMap(),
    val zoteroKey: String? = null,
    val openAlexKey: String? = null,
)

/** [locked]: the backup carries encrypted keys; [Backup.unlock] with the password applies them. */
data class ImportResult(val files: Int, val chats: Int, val from: String, val locked: SecretBox? = null)

/**
 * Everything of the app in one ZIP (Einstellungen › Sicherung & Umzug): chats, projects, templates, tasks,
 * documents, logos, knowledge, memory, address book and the settings – to keep a copy or to move to another
 * device, also between Android and Windows. API keys and GitHub tokens only if the user asks for it.
 */
object Backup {
    private const val VERSION = 1
    private const val MANIFEST = "backup.json"
    private const val PREFIX = "files/"
    /** Top-level entries of the data folder that are device-specific, secret or temporary. */
    private val SKIP = setOf("cache", "secure_settings.dat", "last_crash.txt", "logs", "instance.lock", "app.lock")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun portable(s: AppSettings) = PortableSettings(
        s.defaultModel, s.systemPrompt, s.approvalMode, s.maxAgentSteps, s.autoContinue, s.thinkingLevel, s.documentTools,
        s.webTools, s.themeMode, s.appStyle, s.accent, s.amoled, s.textScale, s.ttsEngine, s.ttsVoice, s.speechRate,
        s.docStyle, s.docAccent, s.docFooter, s.tudUnit, s.autoLogo, s.senderInfo, s.favoriteTemplates, s.userName,
        s.fallbackModel, s.requestsPerMinute, s.memoryMode, s.pseudonymize, s.pseudonymAuto, s.pseudonymTerms,
        s.citationStyle, s.zoteroUserId, s.literatureSources, s.uiLanguage, s.coderName, s.formSuggestions,
        s.pseudonymIgnore, s.pseudonymOff,
    )

    fun apply(s: AppSettings, p: PortableSettings) = s.copy(
        defaultModel = p.defaultModel, systemPrompt = p.systemPrompt, approvalMode = p.approvalMode, maxAgentSteps = p.maxAgentSteps,
        autoContinue = p.autoContinue, thinkingLevel = p.thinkingLevel, documentTools = p.documentTools, webTools = p.webTools,
        themeMode = p.themeMode, appStyle = p.appStyle, accent = p.accent, amoled = p.amoled, textScale = p.textScale,
        ttsEngine = p.ttsEngine, ttsVoice = p.ttsVoice, speechRate = p.speechRate, docStyle = p.docStyle, docAccent = p.docAccent,
        docFooter = p.docFooter, tudUnit = p.tudUnit, autoLogo = p.autoLogo, senderInfo = p.senderInfo,
        favoriteTemplates = p.favoriteTemplates, userName = p.userName, fallbackModel = p.fallbackModel,
        requestsPerMinute = p.requestsPerMinute, memoryMode = p.memoryMode, pseudonymize = p.pseudonymize,
        pseudonymAuto = p.pseudonymAuto, pseudonymTerms = p.pseudonymTerms,
        citationStyle = p.citationStyle, zoteroUserId = p.zoteroUserId, literatureSources = p.literatureSources,
        uiLanguage = p.uiLanguage, coderName = p.coderName, formSuggestions = p.formSuggestions,
        pseudonymIgnore = p.pseudonymIgnore, pseudonymOff = p.pseudonymOff,
    )

    /**
     * Writes the backup. With a [password], API keys, tokens and secret variables go along – encrypted with it, never
     * in plain text.
     */
    fun export(c: AppContainer, out: OutputStream, password: String?) {
        val root = c.appContext.filesDir
        val s = c.settings.current
        val envs = c.environments.current.map { e -> e.copy(variables = e.variables.map { v -> if (v.secret) v.copy(value = "") else v }) }
        val secrets = password?.let {
            val plain = BackupSecrets(
                apiKey = s.apiKey.takeIf { k -> k.isNotBlank() },
                githubToken = s.githubToken.takeIf { t -> t.isNotBlank() },
                githubTokens = s.githubAccounts.map { a -> a.token }.filter { t -> t.isNotBlank() },
                providerKeys = s.providers.filter { p -> p.apiKey.isNotBlank() }.associate { p -> p.id to p.apiKey },
                envSecrets = c.environments.current.associate { e -> e.id to e.variables.filter { v -> v.secret && v.value.isNotEmpty() }.associate { v -> v.name to v.value } }
                    .filterValues { m -> m.isNotEmpty() },
                zoteroKey = s.zoteroKey.takeIf { k -> k.isNotBlank() },
                openAlexKey = s.openAlexKey.takeIf { k -> k.isNotBlank() },
            )
            SecretBox.seal(json.encodeToString(BackupSecrets.serializer(), plain), it)
        }
        val manifest = BackupManifest(
            VERSION, BuildConfig.VERSION_NAME, System.currentTimeMillis(), root.absolutePath, portable(s), envs,
            providers = s.providers.map { it.copy(apiKey = "") },
            secrets = secrets,
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            root.listFiles().orEmpty().filter { it.name !in SKIP && !it.name.endsWith(".tmp") }.forEach { top ->
                top.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                    zip.putNextEntry(ZipEntry(PREFIX + f.relativeTo(root).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Restores a backup over the current data (same chats and documents are replaced, others stay). The app must be
     * restarted afterwards, because the stores are read at start.
     */
    fun import(c: AppContainer, input: InputStream): ImportResult {
        val root = c.appContext.filesDir
        val base = root.canonicalFile
        var manifest: BackupManifest? = null
        var files = 0
        var chats = 0
        val texts = mutableListOf<File>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.isDirectory -> {}
                    e.name == MANIFEST -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                    e.name.startsWith(PREFIX) -> {
                        val rel = e.name.removePrefix(PREFIX)
                        val target = File(root, rel).canonicalFile
                        // Entries must stay inside the data folder.
                        if (!target.path.startsWith(base.path + File.separator) || rel.substringBefore('/') in SKIP) continue
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        files++
                        if (rel.startsWith("conversations/")) chats++
                        if (target.extension == "json") texts += target
                    }
                }
            }
        }
        val m = manifest ?: throw IllegalArgumentException("Das ist keine Sicherung von ADEU LLM (backup.json fehlt).")
        // Documents, logos and attachment copies are referenced with absolute paths of the old device.
        texts.forEach { f -> runCatching { f.writeText(movePaths(f.readText(), m.root, root.absolutePath)) }.logFailure("Pfade nach Umzug anpassen") }
        c.settings.update { s -> apply(s, m.settings) }
        // Older backups carried the keys in plain text.
        applySecrets(c, BackupSecrets(m.apiKey, m.githubToken, m.githubTokens), m.providers, m.environments)
        return ImportResult(files, chats, m.appVersion, locked = m.secrets)
    }

    /** Applies the encrypted keys of an imported backup; false if the password is wrong. */
    fun unlock(c: AppContainer, box: SecretBox, password: String): Boolean {
        val plain = box.open(password) ?: return false
        applySecrets(c, json.decodeFromString(BackupSecrets.serializer(), plain), emptyList(), emptyList())
        return true
    }

    /** Takes over keys, tokens, providers and environments; what is missing keeps the value on this device. */
    private fun applySecrets(c: AppContainer, secrets: BackupSecrets, providers: List<ApiProvider>, environments: List<AgentEnvironment>) {
        c.settings.update { s ->
            s.let { if (secrets.apiKey != null) it.copy(apiKey = secrets.apiKey) else it }
                .let { if (secrets.zoteroKey != null) it.copy(zoteroKey = secrets.zoteroKey) else it }
                .let { if (secrets.openAlexKey != null) it.copy(openAlexKey = secrets.openAlexKey) else it }
                .let { if (secrets.githubToken != null) it.copy(githubToken = secrets.githubToken, githubUser = "") else it }
                .let { cur ->
                    val known = cur.githubAccounts.map { a -> a.token }.toSet() + cur.githubToken
                    cur.copy(githubAccounts = cur.githubAccounts + secrets.githubTokens.filter { t -> t !in known }.map { t -> GitHubAccount(token = t) })
                }
                .let { cur ->
                    // Keys that were not exported keep the one of the same provider on this device.
                    val imported = providers.map { p ->
                        if (p.apiKey.isNotEmpty()) p
                        else p.copy(apiKey = cur.providers.firstOrNull { o -> o.id == p.id || o.kind == p.kind && o.baseUrl == p.baseUrl }?.apiKey.orEmpty())
                    }
                    val merged = cur.providers.filter { o -> imported.none { it.id == o.id || it.kind == o.kind && it.baseUrl == o.baseUrl } } + imported
                    cur.copy(providers = merged.map { p -> secrets.providerKeys[p.id]?.let { k -> p.copy(apiKey = k) } ?: p })
                }
        }
        val current = c.environments.current
        val withImported = if (environments.isEmpty()) current else current.filter { e -> environments.none { it.id == e.id } } + environments.map { imported ->
            // Secret values that were not exported keep the value already on this device.
            val old = current.firstOrNull { it.id == imported.id }
            imported.copy(variables = imported.variables.map { v ->
                if (v.secret && v.value.isEmpty()) old?.variables?.firstOrNull { it.name == v.name }?.let { v.copy(value = it.value) } ?: v else v
            })
        }
        val result = withImported.map { e ->
            val values = secrets.envSecrets[e.id] ?: return@map e
            e.copy(variables = e.variables.map { v -> values[v.name]?.let { v.copy(value = it) } ?: v })
        }
        if (result != current) c.environments.replaceAll(result)
    }

    /** Replaces the old data folder in JSON text with the new one, with forward slashes (valid on Android and Windows). */
    internal fun movePaths(text: String, oldRoot: String, newRoot: String): String {
        if (oldRoot.isBlank() || oldRoot == newRoot) return text
        fun jsonEscaped(p: String) = p.replace("\\", "\\\\")
        val old = Regex(Regex.escape(jsonEscaped(oldRoot)) + "([^\"]*)")
        val target = newRoot.replace('\\', '/')
        return old.replace(text) { m -> jsonEscaped(target) + m.groupValues[1].replace("\\\\", "/") }
    }
}
