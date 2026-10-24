package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.GitHubTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.GitHubAccount
import io.github.thepytales.llmhub.data.LinkedRepo
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.ModelTier
import io.github.thepytales.llmhub.data.ModelsState
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.UsageStore
import io.github.thepytales.llmhub.data.label
import io.github.thepytales.llmhub.net.GitHubClient
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.net.toApiJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Several GitHub tokens, several repositories per chat and further API providers (Gemini). */
class AccountsTest {
    private val dir = File("build/test-output/accounts-${System.nanoTime()}").apply { mkdirs() }

    init {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", dir.absolutePath)
    }

    private val settings = SettingsStore(Context(dir, dir)).apply {
        update {
            it.copy(
                githubToken = "main", githubUser = "thepytales",
                githubAccounts = listOf(
                    GitHubAccount(id = "a1", token = "tud", user = "thepytales"),
                    GitHubAccount(id = "a2", token = "pytales", user = "thepytales"),
                ),
                providers = emptyList(),
                apiKey = "sk-test",
            )
        }
    }

    /** Fine-grained tokens: each one only reaches the repositories of its resource owner. */
    private val reach = mapOf("main" to "thepytales", "tud" to "ADEU-TUD", "pytales" to "ProjectPyTales")
    private val repos = mapOf("thepytales" to listOf("API_App"), "ADEU-TUD" to listOf("Test"), "ProjectPyTales" to listOf("ADEU_LLM"))

    /** "token GET /path" of every request. */
    private val log: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val fakeGitHub = Interceptor { chain ->
        val req = chain.request()
        val token = req.header("Authorization").orEmpty().removePrefix("Bearer ")
        val seg = req.url.pathSegments
        log += "$token ${req.method} /${seg.joinToString("/")}"
        val owner = reach[token]
        val (code, body) = when {
            owner == null -> 401 to """{"message":"Bad credentials"}"""
            seg == listOf("user") -> 200 to """{"login":"thepytales"}"""
            seg == listOf("user", "repos") -> 200 to repos.getValue(owner).joinToString(",", "[", "]") { name ->
                """{"full_name":"$owner/$name","private":true,"default_branch":"main","pushed_at":"2026-10-0${name.length % 9}T00:00:00Z"}"""
            }
            seg.size >= 3 && seg[0] == "repos" && !seg[1].equals(owner, ignoreCase = true) -> 404 to """{"message":"Not Found"}"""
            seg.size == 3 && seg[0] == "repos" -> 200 to """{"default_branch":"main"}"""
            seg.size == 4 && seg[3] == "branches" -> 200 to """[{"name":"main"},{"name":"dev"}]"""
            seg.size == 5 && seg[3] == "branches" -> 200 to """{"name":"${seg[4]}"}"""
            else -> 404 to """{"message":"Not Found"}"""
        }
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("fake")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private val gh = GitHubClient(settings, OkHttpClient.Builder().addInterceptor(fakeGitHub).build())

    @Test
    fun repositoriesOfAllTokensAreListedTogether() = runBlocking {
        val names = gh.listRepos().map { it.fullName }.toSet()
        assertEquals(setOf("thepytales/API_App", "ADEU-TUD/Test", "ProjectPyTales/ADEU_LLM"), names)
    }

    @Test
    fun everyRequestUsesTheTokenOfTheOwner() = runBlocking {
        // Without any knowledge the client tries the tokens until one reaches the repository.
        assertEquals("main", gh.defaultBranch("ADEU-TUD/Test"))
        assertTrue(log.any { it.startsWith("tud GET /repos/ADEU-TUD/Test") })
        // Once known (here from the token check in the settings), the right token is the first and only try.
        assertTrue("ProjectPyTales" in gh.verifyAccount("a2"))
        assertEquals(listOf("ProjectPyTales"), settings.current.githubAccounts.first { it.id == "a2" }.owners)
        log.clear()
        gh.defaultBranch("ProjectPyTales/ADEU_LLM")
        assertEquals(listOf("pytales GET /repos/ProjectPyTales/ADEU_LLM"), log.toList())
    }

    @Test
    fun theAgentWorksInSeveralLinkedRepositories() = runBlocking {
        val tools = GitHubTools(gh)
        val ctx = RepoContext(mutableListOf(LinkedRepo("ADEU-TUD/Test", "main")))
        // One linked repository: no parameter needed, as before.
        assertTrue("* main" in tools.execute("list_branches", "{}", ctx).text)
        // A second one is linked in addition, not instead.
        assertTrue("verknüpft" in tools.execute("select_repo", """{"full_name":"https://github.com/ProjectPyTales/ADEU_LLM"}""", ctx).text)
        assertEquals(listOf("ADEU-TUD/Test", "ProjectPyTales/ADEU_LLM"), ctx.linked().map { it.name })
        // Now the model has to say which one it means …
        assertTrue("Mehrere Repositories" in tools.execute("list_branches", "{}", ctx).text)
        // … and branches are kept per repository.
        tools.execute("switch_branch", """{"repo":"ProjectPyTales/ADEU_LLM","name":"dev"}""", ctx)
        assertEquals("dev", ctx.find("ProjectPyTales/ADEU_LLM")?.branch)
        assertEquals("main", ctx.find("ADEU-TUD/Test")?.branch)
        assertTrue("ProjectPyTales/ADEU_LLM" in GitHubTools.systemPrompt(ctx))
    }

    @Test
    fun oldChatsKeepTheirRepository() {
        val old = Conversation(model = "m", repo = "ADEU-TUD/Test", branch = "dev")
        assertEquals(listOf(LinkedRepo("ADEU-TUD/Test", "dev")), old.linkedRepos)
        val both = old.withRepos(old.linkedRepos + LinkedRepo("thepytales/API_App") + LinkedRepo("adeu-tud/test"))
        assertEquals(2, both.linkedRepos.size)
        assertEquals("ADEU-TUD/Test", both.repo)
        assertEquals("2 Repositorys", both.linkedRepos.label())
        assertNull(old.withRepos(emptyList()).repo)
    }

    @Test
    fun geminiModelsNeedTheGeminiKeyAndAreRecommended() {
        val flash = ModelCatalog.info("gemini-3.8-flash")
        assertEquals(ModelTier.FEATURED, flash.tier)
        assertEquals(ProviderKind.GEMINI, flash.via)
        assertEquals(listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash"), ModelCatalog.of(ProviderKind.GEMINI).map { it.id })
        // Unknown Gemini models from the key's list: chat models under „Weitere Modelle“, previews hidden.
        assertEquals(ModelTier.MORE, ModelCatalog.info("gemini-2.5-pro").tier)
        assertEquals(ModelTier.HIDDEN, ModelCatalog.info("gemini-3.9-flash-preview-11-2026").tier)
        // Without a Gemini key they are neither offered nor used as fallback.
        assertTrue(ModelsState().pickable.none { it.via != null })
        assertNull(ModelCatalog.fallbackFor(ModelCatalog.DEFAULT_CHAT, true, true, available = null)?.takeIf { it.startsWith("gemini") })
        // ScaDS.AI stays on top of the recommended models.
        val featured = ModelsState(fallback = ModelCatalog.main + ModelCatalog.of(ProviderKind.GEMINI)).pickable.filter { it.tier == ModelTier.FEATURED }
        assertEquals(null, featured.first().via)
        assertEquals("gemini-3.8-flash", featured.first { it.via != null }.id)
    }

    @Test
    fun requestsGoToTheProviderOfTheModel() {
        val llm = LlmClient(settings, UsageStore(dir))
        assertNull(llm.providerFor("gemini-3.8-flash"), "ohne Gemini-Key")
        val gemini = ApiProvider(id = "g", apiKey = "AIza-test")
        settings.update { it.copy(providers = listOf(gemini)) }
        try {
            assertEquals("g", llm.providerFor("gemini-3.8-flash")?.id)
            assertNull(llm.providerFor(ModelCatalog.DEFAULT_CHAT))
            // Models the provider listed that the catalog does not know.
            llm.owners = mapOf("gemma-3-27b-it" to "g")
            assertEquals("g", llm.providerFor("gemma-3-27b-it")?.id)
            assertTrue(settings.current.hasAnyApiKey)
        } finally {
            settings.update { it.copy(providers = emptyList()) }
        }
    }

    @Test
    fun geminiThoughtSignaturesGoBackWithTheToolCall() {
        val extra = """{"google":{"thought_signature":"abc"}}"""
        val msg = ChatMessage(role = "assistant", content = "", toolCalls = listOf(ToolCall("c1", "list_files", "{}", extra = extra)))
        val call = (msg.toApiJson() as JsonObject)["tool_calls"]!!.jsonArray.first().jsonObject
        assertEquals("abc", call["extra_content"]?.jsonObject?.get("google")?.jsonObject?.get("thought_signature")?.toString()?.trim('"'))
        val plain = ChatMessage(role = "assistant", content = "", toolCalls = listOf(ToolCall("c2", "list_files", "{}")))
        assertTrue("extra_content" !in (plain.toApiJson() as JsonObject)["tool_calls"]!!.jsonArray.first().jsonObject)
    }
}
