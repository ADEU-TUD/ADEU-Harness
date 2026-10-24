package io.github.thepytales.llmhub.data

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
    /** Further API providers; without secrets their keys are empty. */
    val providers: List<ApiProvider> = emptyList(),
)

data class ImportResult(val files: Int, val chats: Int, val from: String)

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
        s.fallbackModel, s.requestsPerMinute, s.memoryMode,
    )

    fun apply(s: AppSettings, p: PortableSettings) = s.copy(
        defaultModel = p.defaultModel, systemPrompt = p.systemPrompt, approvalMode = p.approvalMode, maxAgentSteps = p.maxAgentSteps,
        autoContinue = p.autoContinue, thinkingLevel = p.thinkingLevel, documentTools = p.documentTools, webTools = p.webTools,
        themeMode = p.themeMode, appStyle = p.appStyle, accent = p.accent, amoled = p.amoled, textScale = p.textScale,
        ttsEngine = p.ttsEngine, ttsVoice = p.ttsVoice, speechRate = p.speechRate, docStyle = p.docStyle, docAccent = p.docAccent,
        docFooter = p.docFooter, tudUnit = p.tudUnit, autoLogo = p.autoLogo, senderInfo = p.senderInfo,
        favoriteTemplates = p.favoriteTemplates, userName = p.userName, fallbackModel = p.fallbackModel,
        requestsPerMinute = p.requestsPerMinute, memoryMode = p.memoryMode,
    )

    fun export(c: AppContainer, out: OutputStream, withSecrets: Boolean) {
        val root = c.appContext.filesDir
        val s = c.settings.current
        val envs = c.environments.current.map { e ->
            if (withSecrets) e else e.copy(variables = e.variables.map { v -> if (v.secret) v.copy(value = "") else v })
        }
        val manifest = BackupManifest(
            VERSION, BuildConfig.VERSION_NAME, System.currentTimeMillis(), root.absolutePath, portable(s), envs,
            apiKey = s.apiKey.takeIf { withSecrets && it.isNotBlank() },
            githubToken = s.githubToken.takeIf { withSecrets && it.isNotBlank() },
            githubTokens = if (withSecrets) s.githubAccounts.map { it.token }.filter { it.isNotBlank() } else emptyList(),
            providers = s.providers.map { if (withSecrets) it else it.copy(apiKey = "") },
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
        texts.forEach { f -> runCatching { f.writeText(movePaths(f.readText(), m.root, root.absolutePath)) } }
        c.settings.update { s ->
            apply(s, m.settings).let { if (m.apiKey != null) it.copy(apiKey = m.apiKey) else it }
                .let { if (m.githubToken != null) it.copy(githubToken = m.githubToken, githubUser = "") else it }
                .let { cur ->
                    val known = cur.githubAccounts.map { a -> a.token }.toSet() + cur.githubToken
                    cur.copy(githubAccounts = cur.githubAccounts + m.githubTokens.filter { t -> t !in known }.map { t -> GitHubAccount(token = t) })
                }
                .let { cur ->
                    // Keys that were not exported keep the one of the same provider on this device.
                    val imported = m.providers.map { p ->
                        if (p.apiKey.isNotEmpty()) p
                        else p.copy(apiKey = cur.providers.firstOrNull { o -> o.id == p.id || o.kind == p.kind && o.baseUrl == p.baseUrl }?.apiKey.orEmpty())
                    }
                    cur.copy(providers = cur.providers.filter { o -> imported.none { it.id == o.id || it.kind == o.kind && it.baseUrl == o.baseUrl } } + imported)
                }
        }
        if (m.environments.isNotEmpty()) {
            val current = c.environments.current
            c.environments.replaceAll(current.filter { e -> m.environments.none { it.id == e.id } } + m.environments.map { imported ->
                // Secret values that were not exported keep the value already on this device.
                val old = current.firstOrNull { it.id == imported.id }
                imported.copy(variables = imported.variables.map { v ->
                    if (v.secret && v.value.isEmpty()) old?.variables?.firstOrNull { it.name == v.name }?.let { v.copy(value = it.value) } ?: v else v
                })
            })
        }
        return ImportResult(files, chats, m.appVersion)
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
