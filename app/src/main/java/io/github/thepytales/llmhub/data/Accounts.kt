package io.github.thepytales.llmhub.data

import io.github.thepytales.llmhub.logFailure
import io.github.thepytales.llmhub.net.JSON
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/**
 * A further GitHub token next to the main one in [AppSettings.githubToken], e.g. a fine-grained token whose
 * resource owner is an organization (such a token only reaches the repositories of that one owner).
 */
@Serializable
data class GitHubAccount(
    val id: String = UUID.randomUUID().toString(),
    val token: String = "",
    /** Account the token was verified for; empty while the token is unchecked or invalid. */
    val user: String = "",
    /** Owners (user and organizations) of the repositories the token reached when it was last checked. */
    val owners: List<String> = emptyList(),
)

/** How the app talks to an additional API provider. */
enum class ProviderKind(val label: String, val defaultName: String, val defaultUrl: String) {
    /** Google AI Studio key; Google's OpenAI-compatible endpoint. */
    GEMINI("Google Gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    /** Any other OpenAI-compatible endpoint. */
    OPENAI("OpenAI-kompatibel", "Eigener Anbieter", ""),
    ;

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: OPENAI
    }
}

/** A further API provider next to the main one ([AppSettings.apiKey] at [AppSettings.baseUrl], ScaDS.AI). */
@Serializable
data class ApiProvider(
    val id: String = UUID.randomUUID().toString(),
    /** Enum name of [ProviderKind]. */
    val kind: String = ProviderKind.GEMINI.name,
    val name: String = ProviderKind.GEMINI.defaultName,
    val baseUrl: String = ProviderKind.GEMINI.defaultUrl,
    val apiKey: String = "",
) {
    val providerKind get() = ProviderKind.of(kind)
    val usable get() = apiKey.isNotBlank() && baseUrl.isNotBlank()
}

/** JSON for the preferences; broken or missing values give an empty list. */
object AccountsJson {
    private val accounts = ListSerializer(GitHubAccount.serializer())
    private val providers = ListSerializer(ApiProvider.serializer())

    fun accounts(text: String?): List<GitHubAccount> =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { JSON.decodeFromString(accounts, it) }.logFailure("Konten lesen").getOrNull() }.orEmpty()

    fun accounts(list: List<GitHubAccount>): String = JSON.encodeToString(accounts, list)

    fun providers(text: String?): List<ApiProvider> =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { JSON.decodeFromString(providers, it) }.logFailure("Anbieter lesen").getOrNull() }.orEmpty()

    fun providers(list: List<ApiProvider>): String = JSON.encodeToString(providers, list)
}
