package io.github.thepytales.llmhub

import android.content.Context
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.thepytales.llmhub.agent.AgentHost
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.ModelRouter
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.Area
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.OnDeviceOnly
import io.github.thepytales.llmhub.data.Place
import io.github.thepytales.llmhub.data.PlaceScope
import io.github.thepytales.llmhub.data.Places
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.UniWatch
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LlamaHost
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalLimits
import io.github.thepytales.llmhub.local.LocalLlm
import io.github.thepytales.llmhub.ui.ErrorHelp
import io.github.thepytales.llmhub.ui.IntroModelChoice
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Offline-Schiene (AP-112) without a device: an app container without an API key, a network that only reaches
 * 127.0.0.1 (every other address goes to a tripwire that refuses and notes the host), and a pretend llama-server as in
 * [LocalLimitsTest]. Every function of the profile „Alles auf diesem Gerät“ runs here or says plainly why not, and
 * nothing asks a server on its own.
 */
class OfflineProfileTest {
    // ── The network: only 127.0.0.1 ─────────────────────────────────────────────────────────────────────────
    /** Hosts something tried to reach outside this device. */
    private val outside = CopyOnWriteArrayList<String>()
    /** A port on 127.0.0.1 where nothing listens: a connection there is refused, like a network that ends here. */
    private val tripwirePort = ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { it.localPort }
    private val oldSelector: ProxySelector? = ProxySelector.getDefault()

    init {
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                val host = uri.host.orEmpty()
                if (host == "127.0.0.1" || host == "localhost" || host == "::1") return listOf(Proxy.NO_PROXY)
                outside += host
                return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLoopbackAddress(), tripwirePort)))
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) {}
        })
    }

    // ── The app ────────────────────────────────────────────────────────────────────────────────────────────
    private val base = Files.createTempDirectory("adeu-offline").toFile()
    private val made = lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }
    private val c by made
    private var server: HttpServer? = null
    private val bodies = CopyOnWriteArrayList<JsonObject>()
    private val modelId = "qwen3.5-4b"
    private val model = LocalCatalog.get(modelId)!!
    private var fakeFile: File? = null
    private val officePc = Hardware(phone = false, ramGb = 16.0, cores = 6, cpuFeatures = setOf("avx2"), memBandwidthGbs = 51.2)

    @AfterTest
    fun cleanUp() {
        server?.stop(0)
        if (made.isInitialized()) {
            fakeFile?.delete()
            c.localLlm.refresh()
            c.llm.localProvider = { m -> c.localLlm.provider(m) }
            c.llm.localAbort = { c.localLlm.abort() }
            c.llm.localLimits = { m -> c.localLlm.limits(m) }
            c.llm.localHeat = { c.localLlm.heat() }
        }
        ProxySelector.setDefault(oldSelector)
        base.deleteRecursively()
    }

    /** A language model „on the device“: a file of the right size (sparse, takes no space) and the pretend server. */
    private fun install(answer: (HttpExchange, Int) -> Unit = { ex, _ -> sse(ex, text("Hallo, ich arbeite auf diesem Gerät.")) }) {
        c.localLlm.pretend(officePc)
        val f = c.localLlm.file(model)
        f.parentFile.mkdirs()
        RandomAccessFile(f, "rw").use { it.setLength(model.size) }
        fakeFile = f
        c.localLlm.refresh()
        assertEquals(model.modelId, c.localLlm.preferredModel(), "Modell gilt als installiert (${LlamaHost.root(c)})")
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/v1/chat/completions") { ex ->
            bodies += Json.parseToJsonElement(ex.requestBody.readBytes().decodeToString()).jsonObject
            try { answer(ex, bodies.size) } catch (_: Exception) { } finally { ex.close() }
        }
        s.executor = java.util.concurrent.Executors.newCachedThreadPool()
        s.start()
        server = s
        val provider = ApiProvider(id = LocalLlm.LOCAL_PROVIDER_ID, kind = ProviderKind.OPENAI.name, name = "Test", baseUrl = "http://127.0.0.1:${s.address.port}/v1", apiKey = "k")
        c.llm.localProvider = { provider }
        c.llm.localAbort = {}
        c.llm.localLimits = { LocalLimits.PC_CPU }
        c.llm.localHeat = { io.github.thepytales.llmhub.local.Heat.OK }
    }

    private fun sse(ex: HttpExchange, vararg chunks: String) {
        ex.responseHeaders.add("Content-Type", "text/event-stream")
        ex.sendResponseHeaders(200, 0)
        ex.responseBody.use { out ->
            chunks.forEach { out.write("data: $it\n\n".toByteArray()); out.flush() }
            out.write("data: [DONE]\n\n".toByteArray())
        }
    }

    private fun text(t: String) = """{"choices":[{"index":0,"delta":{"content":"$t"},"finish_reason":"stop"}]}"""
    private fun toolCall(n: Int) =
        """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"c$n","type":"function","function":{"name":"lies_nichts","arguments":"{\"n\":$n}"}}]},"finish_reason":"tool_calls"}]}"""

    private class Host(start: Conversation) : AgentHost {
        @Volatile override var conversation: Conversation? = start
        override fun update(transform: (Conversation) -> Conversation) { conversation = conversation?.let(transform) }
        override fun persist() {}
        override fun notice(message: String) {}
        override suspend fun confirm(call: ToolCall, risk: Risk) = true
    }

    private fun chat(model: String = ModelCatalog.DEFAULT_CHAT) =
        Conversation(title = "Test", model = model).appendToThread(ChatMessage(role = "user", content = "Fasse kurz zusammen: Die Sitzung ist am Montag."))

    private fun noKey() = c.settings.update { it.copy(apiKey = "", providers = emptyList(), places = "", loraPlace = "MIXED", ttsEngine = "SERVER", semanticLocal = true) }
    private fun withKey() = c.settings.update { it.copy(apiKey = "sk-test", baseUrl = "https://llm.scads.ai/v1", places = "", loraPlace = "MIXED", ttsEngine = "SERVER") }

    private fun assertNothingLeft(what: String) = assertTrue(outside.isEmpty(), "$what: Anfrage nach außen an $outside")

    // ── Erster Start ohne Schlüssel ───────────────────────────────────────────────────────────────────────────

    @Test
    fun firstStartWithoutAKeyWorksOnThisDevice() {
        noKey()
        // Every area with a choice works here; fixed ones keep their place.
        Area.entries.filter { it.choosable }.forEach { assertEquals(Place.LOCAL, Places.effective(c, it), it.name) }
        assertEquals(Place.LOCAL, Places.effective(c, Area.MAIL))
        assertEquals(ModelRouter.Mode.LOCAL, Places.loraMode(c))
        // The first start offers „Alles auf diesem Gerät“ and ticks nothing that needs a server.
        val intro = IntroModelChoice(c)
        assertEquals("LOCAL", intro.place)
        assertTrue(intro.wantsSetup)
        // Without a model: a plain message with the way to the setup instead of „Kein API-Key“.
        val e = assertFailsWith<OnDeviceOnly> { Places.route(c, Area.CHAT, ModelCatalog.DEFAULT_CHAT, images = false) }
        assertTrue("Ohne API-Schlüssel" in e.message!! && "Einrichtung" in e.message!!, e.message)
        val plain = ErrorHelp.explain(e)
        assertEquals(ErrorHelp.Fix.LOCAL_SETUP, plain.fix)
        assertEquals(e.message, plain.text, "Klartext bleibt, wie er ist")
        // Lora without model and key: understands commands, says what is missing, no server.
        val reply = runBlocking { LoraAgent(c).ask("Schreib mir einen freundlichen Gruß an mein Team.") }
        assertTrue("Sprachmodell" in reply.text, reply.text)
        assertTrue(reply.steps.none { it is io.github.thepytales.llmhub.agent.LoraStep.Handoff }, "keine Übergabe an ein Server-Modell")
        assertEquals(ErrorHelp.Fix.LOCAL_SETUP, reply.fix)
        assertNothingLeft("erster Start")
    }

    @Test
    fun withAModelChatLoraAndDocumentsAnswerHere() {
        noKey()
        install()
        // Chat: a chat that was started with the Uni-Server model continues here.
        val host = Host(chat())
        runBlocking { c.agent.run(host) }
        assertEquals("Hallo, ich arbeite auf diesem Gerät.", host.conversation!!.thread().last().content)
        assertEquals(1, bodies.size)
        // Lora herself (answers, tools): the language model here.
        val reply = runBlocking { LoraAgent(c).ask("Schreib mir einen freundlichen Gruß an mein Team.") }
        // Lora answers with the model here or hands over to a chat – then to the model here, not to the server.
        val handoffs = reply.steps.filterIsInstance<io.github.thepytales.llmhub.agent.LoraStep.Handoff>()
        assertTrue(bodies.size >= 2 || handoffs.isNotEmpty(), "Lora: ${reply.text}")
        assertTrue(handoffs.all { it.model == model.modelId }, "Übergabe an ${handoffs.map { it.model }}")
        // Dokumente (Lora schreibt mit), helpers of every area without a scope of their own.
        runBlocking {
            withContext(PlaceScope(Area.DOCS)) { c.llm.streamChat(c.settings.current.defaultModel, listOf(ChatMessage(role = "user", content = "Verbessere: Hallo")), null) { } }
            c.llm.streamChat(ModelCatalog.FAST_CHAT, listOf(ChatMessage(role = "user", content = "Titel?")), null) { }
        }
        assertTrue(bodies.size >= 3)
        // New chats start with the model here.
        assertEquals(model.modelId, io.github.thepytales.llmhub.ui.newConversation(c).model)
        assertNothingLeft("Chat, Lora, Dokumente")
    }

    @Test
    fun theProfileKeepsEverythingHereEvenWithAKey() {
        withKey()
        install()
        Places.apply(c, Places.Profile.ALL_LOCAL)
        assertEquals(Places.Profile.ALL_LOCAL, Places.profile(c))
        assertTrue(Places.allLocal(c))
        assertEquals("LOCAL", c.settings.current.loraPlace)
        assertEquals("LOCAL", c.transcription.settings.value.mode)
        // Chat and Lora: on the device although a key is there.
        val host = Host(chat())
        runBlocking { c.agent.run(host) }
        assertEquals(1, bodies.size)
        val lora = runBlocking { LoraAgent(c).ask("Schreib mir einen freundlichen Gruß an mein Team.") }
        assertTrue(lora.steps.filterIsInstance<io.github.thepytales.llmhub.agent.LoraStep.Handoff>().all { it.model == model.modelId })
        // Suche, Vorlesen, Diktat: never the server – a plain message instead.
        runBlocking {
            val search = assertFailsWith<OnDeviceOnly> { c.llm.embed("alias-embeddings", listOf("Prüfungsordnung")) }
            assertTrue("Suchmodell" in search.message!!, search.message)
            val voice = assertFailsWith<OnDeviceOnly> { c.llm.speech("tts-1-hd", "alloy", "Hallo") }
            assertTrue("Stimme" in voice.message!!, voice.message)
            val audio = File(base, "a.m4a").apply { writeBytes(ByteArray(16)) }
            val dictation = assertFailsWith<OnDeviceOnly> { Places.recognize(c, audio) }
            assertTrue("Erkennungsmodell" in dictation.message!!, dictation.message)
            // Transkription: no silent detour to the server when no model is loaded.
            assertEquals(io.github.thepytales.llmhub.transcribe.TranscriptionMode.LOCAL, c.transcription.defaultOptions().mode)
            // Bilder verstehen: a picture needs a model that sees it.
            val img = assertFailsWith<OnDeviceOnly> { Places.route(c, Area.CHAT, ModelCatalog.DEFAULT_CHAT, images = true) }
            assertTrue("Bildteil" in img.message!!, img.message)
        }
        // Pseudonymisierung works on the device (rules); Postfach is fixed here.
        assertEquals(Places.Kind.SETUP, Places.status(c, Area.NAMES).kind)
        assertEquals(Place.LOCAL, Places.effective(c, Area.MAIL))
        assertEquals(Places.Kind.READY, Places.status(c, Area.MAIL).kind)
        // Bilder erzeugen: shown as „nur über API“, not hidden.
        assertEquals(Places.Kind.API_ONLY, Places.status(c, Area.IMAGES).kind)
        assertEquals(Places.Kind.INTERNET, Places.status(c, Area.WEB).kind)
        assertNothingLeft("Profil „Alles auf diesem Gerät“")
    }

    @Test
    fun theLimitsOfAP116HoldForChatsThatMovedHere() {
        noKey()
        // The model wants tools again and again; a chat of the Uni-Server model, moved here, still stops after its steps.
        install { ex, _ -> sse(ex, if (bodies.last()["tools"] != null) toolCall(bodies.size) else text("Mit dem, was da ist: Montag.")) }
        c.llm.localLimits = { LocalLimits.PHONE }
        val host = Host(chat())
        runBlocking { c.agent.run(host) }
        assertEquals(LocalLimits.PHONE.maxSteps + 1, bodies.size, "drei Schritte, dann eine Antwort")
        assertEquals("Mit dem, was da ist: Montag.", host.conversation!!.thread().last().content)
        assertNothingLeft("Grenzen")
    }

    @Test
    fun errorsWithoutNetworkArePlain() {
        withKey()
        c.settings.update { it.copy(places = "") }
        // A server request in „Gemischt“ reaches only the tripwire: no Internet here.
        val e = runBlocking { runCatching { c.llm.streamChat(ModelCatalog.DEFAULT_CHAT, listOf(ChatMessage(role = "user", content = "Hallo")), null) { } }.exceptionOrNull() }
        assertNotNull(e)
        val plain = ErrorHelp.explain(e)
        assertTrue(plain.id in setOf("offline", "verbindung-abgebrochen", "zeitueberschreitung", "server-ueberlastet"), "${plain.id}: ${plain.text} / ${e.message}")
        assertTrue(!plain.text.contains("Exception"), plain.text)
        assertTrue("llm.scads.ai" in outside, "die Anfrage ging an den Stolperdraht: $outside")
        assertEquals(ErrorHelp.Fix.LOCAL_MODELS, ErrorHelp.explain(java.net.UnknownHostException("llm.scads.ai")).fix, "offline: Weg zu den Modellen auf dem Gerät")
    }

    @Test
    fun overviewSaysHonestlyWhatAnOfficePcLacks() {
        noKey()
        c.localLlm.pretend(Hardware(phone = false, ramGb = 1.5, cores = 2))
        val chat = Places.status(c, Area.CHAT)
        assertEquals(Places.Kind.NOT_HERE, chat.kind)
        assertTrue("Arbeitsspeicher" in chat.reason && "mindestens" in chat.reason, chat.reason)
        // An Office-PC without graphics: language models fit; pictures say how long they take.
        c.localLlm.pretend(officePc)
        assertEquals(Places.Kind.SETUP, Places.status(c, Area.CHAT).kind)
        val vision = Places.status(c, Area.VISION)
        assertTrue("je Bild" in vision.reason, vision.reason)
        // A gaming PC: everything but „nur über API“ can run here.
        c.localLlm.pretend(Hardware(phone = false, ramGb = 32.0, cores = 8, gpuName = "RTX 5080", vramGb = 16.0))
        Area.entries.filter { it != Area.IMAGES && it != Area.WEB }.forEach { a ->
            assertTrue(Places.status(c, a).kind != Places.Kind.NOT_HERE, "$a: ${Places.status(c, a).reason}")
        }
    }

    @Test
    fun theQuickChoicesAndTheTable() {
        withKey()
        Places.apply(c, Places.Profile.MIXED)
        assertEquals(Places.Profile.MIXED, Places.profile(c), "Gemischt ist der bisherige Stand")
        Places.apply(c, Places.Profile.ALL_API)
        assertEquals(Places.Profile.ALL_API, Places.profile(c))
        assertEquals("SERVER", c.settings.current.loraPlace)
        assertEquals("SERVER", c.settings.current.ttsEngine)
        assertTrue(!c.settings.current.semanticLocal)
        // Own mix: no quick choice is shown as selected.
        Places.set(c, Area.VISION, Place.LOCAL)
        assertEquals(null, Places.profile(c))
        // What is not possible is not taken.
        Places.set(c, Area.MAIL, Place.API)
        assertEquals(Place.LOCAL, Places.chosen(c, Area.MAIL))
        Places.set(c, Area.IMAGES, Place.LOCAL)
        assertEquals(Place.API, Places.chosen(c, Area.IMAGES))
        assertTrue(Area.IMAGES.reasons[Place.LOCAL]!!.contains("Nur über API"))
        assertEquals("CHAT=LOCAL;VISION=MIXED", Places.format(Places.parse("VISION=MIXED;CHAT=LOCAL;FOO=BAR")))
    }

    @Test
    fun theFallbackNoticesWhenTheUniServerIsGoneForGood() {
        var now = 1_000_000_000L
        val w = UniWatch(File(base, "uni.json")) { now }
        // Offline over a weekend is no reason: no answer from anywhere.
        repeat(5) { w.failed(refused = false); now += 24 * 3_600_000L }
        assertTrue(!w.gone(), "nur offline")
        // The Internet worked meanwhile (update check): the server is gone.
        w.internet()
        assertTrue(w.gone())
        // An answer resets everything.
        w.ok()
        assertTrue(!w.gone())
        // A refused key for two days and four tries: gone.
        repeat(4) { w.failed(refused = true); now += 17 * 3_600_000L }
        assertTrue(w.gone())
        assertTrue(UniWatch(File(base, "uni.json")) { now }.gone(), "gemerkt über einen Neustart")
        // In the app: requests to the main provider are counted, not those of other providers or on the device.
        withKey()
        runBlocking { runCatching { c.llm.streamChat(ModelCatalog.DEFAULT_CHAT, listOf(ChatMessage(role = "user", content = "Hallo")), null) { } } }
        assertTrue(c.uniWatch.state.failures >= 1, "${c.uniWatch.state}")
        // Switching to „Alles auf diesem Gerät“ keeps the chats: nothing is deleted, the chat moves to the model here.
        install()
        Places.apply(c, Places.Profile.ALL_LOCAL)
        val old = chat()
        c.chats.save(old)
        val host = Host(old)
        runBlocking { c.agent.run(host) }
        assertNotNull(c.chats.get(old.id), "Chat bleibt")
        assertEquals("Hallo, ich arbeite auf diesem Gerät.", host.conversation!!.thread().last().content)
    }
}
