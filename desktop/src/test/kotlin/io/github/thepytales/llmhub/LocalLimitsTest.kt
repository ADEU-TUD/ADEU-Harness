package io.github.thepytales.llmhub

import android.content.Context
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.thepytales.llmhub.agent.AgentHost
import io.github.thepytales.llmhub.agent.MailAccess
import io.github.thepytales.llmhub.agent.Risk
import io.github.thepytales.llmhub.data.ApiProvider
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ProviderKind
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.thread
import io.github.thepytales.llmhub.local.DeviceClass
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.HardwareInfo
import io.github.thepytales.llmhub.local.Heat
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalLimits
import io.github.thepytales.llmhub.local.LocalLlm
import io.github.thepytales.llmhub.local.OnDeviceStop
import io.github.thepytales.llmhub.local.SpeedForecast
import io.github.thepytales.llmhub.net.LlmClient
import io.github.thepytales.llmhub.net.ThinkingLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lokale Modelle: Tempo, Abbruch und Hitze (AP-116) – everything that can be checked without a device: the limits per
 * class of device, thinking off, the threads, the heat rule, and with a pretend llama-server (an HTTP server on
 * 127.0.0.1): few steps and then an answer, Stopp ends the server's work, nothing is repeated on its own, the time limit
 * gives the hint, a hot device stops the request, a warm one pauses first, the progress of reading shows.
 */
class LocalLimitsTest {
    private val model = "local/qwen3.5-4b"

    // ── Without a server ────────────────────────────────────────────────────────────────────────────────────

    private val onePlus15 = Hardware(phone = true, ramGb = 16.0, cores = 8, bigCores = HardwareInfo.bigCores(listOf(4_608_000L, 4_608_000L) + List(6) { 3_629_000L }))
    private val officePc = Hardware(phone = false, ramGb = 16.0, cores = 6)
    private val gamingPc = Hardware(phone = false, ramGb = 32.0, cores = 8, gpuName = "RTX 5080", vramGb = 16.0)
    private val mac = Hardware(phone = false, mac = true, ramGb = 32.0, cores = 10, chip = "Apple M4 Pro", vramGb = 21.0)

    @Test
    fun limitsFollowTheClassOfDevice() {
        val m = LocalCatalog.get(model)!!
        assertEquals(DeviceClass.PHONE, LocalLimits.of(onePlus15, m).device)
        assertEquals(3, LocalLimits.of(onePlus15, m).maxSteps, "Handy: wenige Werkzeugschritte")
        assertEquals(DeviceClass.PC_CPU, LocalLimits.of(officePc, m).device)
        assertEquals(DeviceClass.PC_FAST, LocalLimits.of(gamingPc, m).device)
        assertEquals(DeviceClass.PC_FAST, LocalLimits.of(mac, m).device)
        assertEquals(DeviceClass.PC_CPU, LocalLimits.of(null).device, "Gerät unbekannt: vorsichtig")
        // A measurement (AP-105) decides where it is known.
        fun forecast(read: Double) = SpeedForecast.Forecast(10.0, read, 0.75, gpu = false, source = SpeedForecast.Source.MEASURED)
        assertEquals(DeviceClass.PC_CPU, LocalLimits.of(gamingPc, m, forecast(80.0)).device, "langsam gemessen")
        assertEquals(DeviceClass.PC_FAST, LocalLimits.of(officePc, m, forecast(500.0)).device, "schnell gemessen")
        // Each class reads less of the mailbox and has less time than the next one.
        val order = listOf(LocalLimits.PHONE, LocalLimits.PC_CPU, LocalLimits.PC_FAST)
        order.zipWithNext().forEach { (a, b) ->
            assertTrue(a.maxSteps < b.maxSteps && a.answerMs < b.answerMs && a.mailList < b.mailList && a.mailChars < b.mailChars, "$a / $b")
        }
    }

    @Test
    fun thinkingIsOffOnThePhone() {
        ThinkingLevel.entries.forEach { assertEquals(ThinkingLevel.OFF, LocalLimits.PHONE.thinking(it), "Handy, Einstellung $it") }
        assertEquals(ThinkingLevel.OFF, LocalLimits.PC_CPU.thinking(ThinkingLevel.AUTO), "PC ohne Grafikkarte: Modellstandard → aus")
        assertEquals(ThinkingLevel.HIGH, LocalLimits.PC_CPU.thinking(ThinkingLevel.HIGH), "ausdrückliche Wahl bleibt")
        assertEquals(ThinkingLevel.AUTO, LocalLimits.PC_FAST.thinking(ThinkingLevel.AUTO))
    }

    @Test
    fun fewerThreadsOnThePhone() {
        assertEquals(8, onePlus15.bigCores, "Snapdragon 8 Elite Gen 5: alle acht Kerne groß")
        assertEquals(4, LocalLlm.threads(onePlus15), "höchstens die Hälfte (1.0.67: sechs)")
        // Two big and six small cores: only the big ones.
        val older = Hardware(phone = true, ramGb = 8.0, cores = 8, bigCores = HardwareInfo.bigCores(listOf(3_200_000L, 3_200_000L) + List(6) { 2_000_000L }))
        assertEquals(2, older.bigCores)
        assertEquals(2, LocalLlm.threads(older))
        assertEquals(4, LocalLlm.threads(Hardware(phone = true, ramGb = 12.0, cores = 12)), "nie mehr als vier")
        assertEquals(0, HardwareInfo.bigCores(listOf(0L, 0L)), "nichts lesbar")
        // The PC stays as it was: half the cores, 2 to 8.
        assertEquals(3, LocalLlm.threads(officePc))
        assertEquals(8, LocalLlm.threads(Hardware(phone = false, ramGb = 64.0, cores = 24)))
    }

    @Test
    fun heatFromThePowerManager() {
        assertEquals(Heat.OK, Heat.of(0, 0.4f))
        assertEquals(Heat.OK, Heat.of(-1, Float.NaN), "unbekannt")
        assertEquals(Heat.OK, Heat.of(1, Float.NaN), "leicht")
        assertEquals(Heat.WARM, Heat.of(2, Float.NaN), "mäßig: keine neue Arbeit")
        assertEquals(Heat.WARM, Heat.of(0, 0.9f), "Reserve fast aufgebraucht")
        assertEquals(Heat.HOT, Heat.of(3, Float.NaN), "stark: sofort anhalten")
        assertEquals(Heat.HOT, Heat.of(1, 1.0f))
    }

    @Test
    fun promptProgressAndShortMails() {
        fun pp(json: String) = LlmClient.promptProgress(Json.parseToJsonElement(json).jsonObject)
        assertEquals(0.5, pp("""{"total":1100,"cache":100,"processed":600,"time_ms":900}"""))
        assertEquals(1.0, pp("""{"total":100,"cache":0,"processed":100}"""))
        assertNull(pp("""{"total":100,"cache":100,"processed":100}"""), "alles aus dem Cache")
        val mail = "Guten Tag, " + "bitte das Gutachten prüfen. ".repeat(200)
        val phone = MailAccess.shorten(mail, shortChars = LocalLimits.PHONE.mailChars)
        assertTrue(phone.length < LocalLimits.PHONE.mailChars + 120 && "full=true" in phone, phone.takeLast(120))
        assertTrue(MailAccess.shorten(mail).length > phone.length, "PC liest mehr")
        assertTrue(MailAccess.shorten(mail, full = true, fullChars = LocalLimits.PHONE.fullMailChars).length < LocalLimits.PHONE.fullMailChars + 60)
    }

    // ── With a pretend llama-server ──────────────────────────────────────────────────────────────────────────

    private val base = Files.createTempDirectory("adeu-lokal-grenzen").toFile()
    private val made = lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }
    private val c by made
    private var server: HttpServer? = null
    private val bodies = CopyOnWriteArrayList<JsonObject>()
    private val aborts = AtomicInteger()

    @AfterTest
    fun cleanUp() {
        server?.stop(0)
        if (made.isInitialized()) {
            c.llm.localProvider = { m -> c.localLlm.provider(m) }
            c.llm.localAbort = { c.localLlm.abort() }
            c.llm.localLimits = { m -> c.localLlm.limits(m) }
            c.llm.localHeat = { c.localLlm.heat() }
        }
        base.deleteRecursively()
    }

    /** Starts the pretend server; [answer] writes the reply to one request (its number from 1). */
    private fun serve(limits: LocalLimits = LocalLimits.PHONE, heat: () -> Heat = { Heat.OK }, answer: (HttpExchange, Int) -> Unit) {
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
        c.llm.localAbort = { aborts.incrementAndGet() }
        c.llm.localLimits = { limits }
        c.llm.localHeat = heat
    }

    private fun sse(ex: HttpExchange, vararg chunks: String) {
        ex.responseHeaders.add("Content-Type", "text/event-stream")
        ex.sendResponseHeaders(200, 0)
        ex.responseBody.use { out ->
            chunks.forEach { out.write("data: $it\n\n".toByteArray()); out.flush() }
            out.write("data: [DONE]\n\n".toByteArray())
        }
    }

    private fun toolCall(n: Int) =
        """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"c$n","type":"function","function":{"name":"lies_nichts","arguments":"{\"n\":$n}"}}]},"finish_reason":"tool_calls"}]}"""

    private fun text(t: String) = """{"choices":[{"index":0,"delta":{"content":"$t"},"finish_reason":"stop"}]}"""

    private class Host(start: Conversation) : AgentHost {
        @Volatile override var conversation: Conversation? = start
        val progress = CopyOnWriteArrayList<Double?>()
        override fun update(transform: (Conversation) -> Conversation) { conversation = conversation?.let(transform) }
        override fun persist() {}
        override fun notice(message: String) {}
        override suspend fun confirm(call: ToolCall, risk: Risk) = true
        override fun progress(share: Double?) { progress += share }
    }

    private fun chat() = Conversation(title = "Test", model = model).appendToThread(ChatMessage(role = "user", content = "Was ist seit gestern wichtig?"))

    private fun JsonObject.thinkingOff() = (this["chat_template_kwargs"] as? JsonObject)?.get("enable_thinking")?.jsonPrimitive?.booleanOrNull == false

    @Test
    fun fewStepsThenAnAnswerWithWhatIsThere() {
        // The model wants tools again and again (as in 1.0.67); without tools it answers.
        serve { ex, _ -> sse(ex, if (bodies.last()["tools"] != null) toolCall(bodies.size) else text("Wichtig: das Gutachten.")) }
        val host = Host(chat())
        runBlocking { c.agent.run(host) }
        assertEquals(LocalLimits.PHONE.maxSteps + 1, bodies.size, "drei Schritte, dann eine Antwort")
        assertTrue(bodies.take(3).all { it["tools"] != null }, "Schritte mit Werkzeugen")
        assertNull(bodies.last()["tools"], "letzte Anfrage ohne Werkzeuge")
        assertTrue(bodies.last().toString().contains("Werkzeugschritte für diese Antwort sind aufgebraucht"), "Hinweis an das Modell")
        assertTrue(bodies.all { it.thinkingOff() }, "Denken aus: " + bodies.map { it["chat_template_kwargs"] })
        assertTrue(bodies.all { it["return_progress"]?.jsonPrimitive?.booleanOrNull == true }, "Fortschritt angefragt")
        val last = host.conversation!!.thread().last()
        assertEquals("Wichtig: das Gutachten.", last.content)
        assertTrue(!last.error && last.toolCalls.isEmpty())
    }

    @Test
    fun stopEndsTheServersWork() {
        val arrived = java.util.concurrent.CountDownLatch(1)
        serve { ex, _ -> arrived.countDown(); Thread.sleep(20_000); sse(ex, text("zu spät")) }
        val host = Host(chat())
        runBlocking {
            val job = launch(Dispatchers.IO) { c.agent.run(host) }
            assertTrue(arrived.await(10, java.util.concurrent.TimeUnit.SECONDS), "Anfrage angekommen")
            val t0 = System.currentTimeMillis()
            job.cancel()
            withTimeout(3_000) { job.join() }
            assertTrue(System.currentTimeMillis() - t0 < 3_000)
        }
        assertEquals(1, aborts.get(), "Stopp beendet die Arbeit des Modell-Servers")
        Thread.sleep(500)
        assertEquals(1, bodies.size, "keine neue Anfrage nach Stopp")
    }

    @Test
    fun nothingIsRepeatedOnItsOwn() {
        // 503 and a broken connection would be retried for a server model (5 s, 15 s, 30 s) – here once only.
        serve { ex, _ -> ex.sendResponseHeaders(503, -1) }
        runBlocking { assertFailsWith<java.io.IOException> { c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = "Hallo")), null) { } } }
        assertEquals(1, bodies.size, "503: kein zweiter Versuch")
        server?.stop(0)
        bodies.clear()
        serve { _, _ -> throw IllegalStateException("Verbindung bricht ab") }
        runBlocking { assertFailsWith<java.io.IOException> { c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = "Hallo")), null) { } } }
        assertEquals(1, bodies.size, "abgebrochene Verbindung: kein zweiter Versuch")
        assertEquals(0, aborts.get())
    }

    @Test
    fun timeLimitGivesAClearHint() {
        serve(LocalLimits.PHONE.copy(answerMs = 1_500)) { ex, _ -> Thread.sleep(15_000); sse(ex, text("zu spät")) }
        val host = Host(chat())
        val t0 = System.currentTimeMillis()
        runBlocking { c.agent.run(host) }
        assertTrue(System.currentTimeMillis() - t0 < 6_000, "hält nach dem Zeitlimit an")
        val last = host.conversation!!.thread().last()
        assertTrue(last.error && last.content == LocalLimits.tooSlow(mailbox = false), last.content)
        assertTrue("dauert auf diesem Gerät zu lange" in last.content && "Server-Modell" in last.content && "kürzere Frage" in last.content)
        assertEquals(1, aborts.get(), "Server hört auf zu rechnen")
        assertEquals(1, bodies.size, "keine Wiederholung")
        assertTrue("kürzere Frage" in LocalLimits.tooSlow(mailbox = true) && "Server-Modell" !in LocalLimits.tooSlow(mailbox = true), "im Postfach kein Server-Modell")
    }

    @Test
    fun aHotPhoneStopsTheRequestAndAWarmOnePausesFirst() {
        // Hot during the request: the server stops at once.
        serve(heat = { if (bodies.isEmpty()) Heat.OK else Heat.HOT }) { ex, _ -> Thread.sleep(15_000); sse(ex, text("zu spät")) }
        c.llm.heatCheckMs = 100
        val t0 = System.currentTimeMillis()
        val e = runBlocking { assertFailsWith<OnDeviceStop> { c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = "Hallo")), null) { } } }
        assertEquals(Heat.stopNote(), e.message)
        assertTrue(System.currentTimeMillis() - t0 < 5_000)
        assertEquals(1, aborts.get())
        server?.stop(0)
        bodies.clear()
        // Warm before the request: a pause (with a note), then the work.
        val states = ArrayDeque(listOf(Heat.WARM, Heat.WARM))
        serve(heat = { states.removeFirstOrNull() ?: Heat.OK }) { ex, _ -> sse(ex, text("Hallo")) }
        c.llm.coolStepMs = 50
        val notes = mutableListOf<String>()
        val r = runBlocking { c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = "Hallo")), null, onRetry = { why, _ -> notes += why }) { } }
        assertEquals("Hallo", r.content)
        assertEquals(listOf(Heat.waitNote(), Heat.waitNote()), notes)
        // Stays warm: no request at all.
        server?.stop(0)
        bodies.clear()
        serve(heat = { Heat.WARM }) { ex, _ -> sse(ex, text("Hallo")) }
        c.llm.coolMaxMs = 200
        runBlocking { assertFailsWith<OnDeviceStop> { c.llm.streamChat(model, listOf(ChatMessage(role = "user", content = "Hallo")), null) { } } }
        assertEquals(0, bodies.size, "zu warm: keine Arbeit")
    }

    @Test
    fun readingProgressShows() {
        serve { ex, _ ->
            sse(
                ex,
                """{"choices":[],"prompt_progress":{"total":2000,"cache":0,"processed":500,"time_ms":1000}}""",
                """{"choices":[],"prompt_progress":{"total":2000,"cache":0,"processed":2000,"time_ms":4000}}""",
                text("Fertig."),
            )
        }
        val host = Host(chat())
        runBlocking { c.agent.run(host) }
        assertTrue(0.25 in host.progress && 1.0 in host.progress, host.progress.toString())
        assertNull(host.progress.last(), "nach der Antwort keine Anzeige mehr")
        assertEquals("Fertig.", host.conversation!!.thread().last().content)
    }
}
