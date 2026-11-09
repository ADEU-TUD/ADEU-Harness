package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.ActionFailed
import io.github.thepytales.llmhub.agent.ActionArea
import io.github.thepytales.llmhub.agent.ActionPlan
import io.github.thepytales.llmhub.agent.ActionResult
import io.github.thepytales.llmhub.agent.ChatModes
import io.github.thepytales.llmhub.agent.FlowStep
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraFlow
import io.github.thepytales.llmhub.agent.LoraFlows
import io.github.thepytales.llmhub.agent.LoraMail
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.MailAccess
import io.github.thepytales.llmhub.agent.MailHandoff
import io.github.thepytales.llmhub.agent.OutlookTools
import io.github.thepytales.llmhub.agent.RepoContext
import io.github.thepytales.llmhub.agent.ToolAction
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.agent.ToolSelection
import io.github.thepytales.llmhub.agent.execute
import io.github.thepytales.llmhub.agent.toSchema
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.PrivacyLevel
import io.github.thepytales.llmhub.data.ToolCall
import io.github.thepytales.llmhub.data.appendToThread
import io.github.thepytales.llmhub.data.withTool
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.ui.ChatHandoff
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Postfach nur mit lokalem Modell (AP-68): no mail tool reaches a model of the Uni server or an API provider – not in a
 * chat with the tool switched on, not through Lora, Abläufe, batches or scheduled tasks; a handoff carries only the
 * checked text; the Postfach chat takes no model outside the device.
 */
class MailLocalOnlyTest {
    private val api = ModelCatalog.DEFAULT_CHAT
    private val uni = ModelCatalog.FAST_CHAT
    private val local = "local/qwen3.5-4b"

    /** A mailbox in memory: reading works, there is no delete and no send. */
    private class FakeMailbox : OutlookBridge {
        private val inbox = MailFolder("inbox|s", "\\\\person@tu-dresden.de\\Posteingang", "Posteingang", 1, 1)
        private val mails = listOf(
            MailHeader("m1|s", inbox.path, "Prof. Beispiel", "beispiel@tu-dresden.de", subject = "Gutachten bis 15.11.", received = 1_000, unread = true, preview = "Könnten Sie das Gutachten schicken?"),
        )
        override fun connect() = "person@tu-dresden.de"
        override fun folders() = listOf(inbox)
        override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int) = mails
        override fun mail(id: String) = mails.firstOrNull { it.id == id }?.let { MailBody(it, body = it.preview) }
        override fun move(ids: List<String>, folderId: String) = 0
        override fun createFolder(parentId: String?, name: String) = inbox
        override fun draft(draft: MailDraft) = "d1|s"
        override fun events(from: Long, to: Long) = emptyList<CalendarEntry>()
        override fun createEvent(event: NewEvent) = "e1|s"
        override fun createTask(task: NewTask) = "t1|s"
        override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int) = emptyList<io.github.thepytales.llmhub.data.BusyTimes>()
        override fun show(id: String) = Unit
    }

    private val base = Files.createTempDirectory("adeu-postfach-lokal").toFile()
    private val c by lazy {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        Outlook.testBridge = FakeMailbox()
        AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })).also { c ->
            c.settings.update { it.withTool(AppTool.MAIL, true).withTool(AppTool.BATCH, true) }
        }
    }

    @AfterTest
    fun cleanUp() {
        Outlook.testBridge = null
        ChatHandoff.pending.value = null
        MailHandoff.pending.value = null
        base.deleteRecursively()
    }

    private fun chat(model: String, mode: String? = null) = Conversation(title = "Test", model = model, mode = mode)

    private fun mailToolIn(conv: Conversation, model: String = conv.model, tools: ToolSelection? = null) =
        c.outlookTools in c.agent.providers(conv, tools, model)

    /** A chat the mailbox went into: the local model listed the mails. */
    private fun touched(model: String = local): Conversation {
        val call = ToolCall(id = "call-1", name = OutlookTools.LIST_MAILS, arguments = "{}")
        return chat(model)
            .appendToThread(ChatMessage(role = "user", content = "Was ist neu?"))
            .appendToThread(ChatMessage(role = "assistant", content = "", toolCalls = listOf(call), model = model))
            .appendToThread(ChatMessage(role = "tool", content = "Posteingang: 1 Mails …", toolCallId = call.id, toolName = call.name))
            .appendToThread(ChatMessage(role = "assistant", content = "Prof. Beispiel [M1] bittet um das Gutachten bis 15.11.", model = model))
    }

    @Test
    fun mailToolsOnlyReachModelsOnThisDevice() {
        // The tool „Mail & Kalender“ is on – still nothing for the Uni server or an API model.
        assertFalse(mailToolIn(chat(api)), "API-Modell")
        assertFalse(mailToolIn(chat(uni)), "Uni-Server")
        assertTrue(mailToolIn(chat(local)), "Modell auf diesem Gerät")
        // The model of the run counts (voice model, fallback), not only the one stored on the chat.
        assertFalse(mailToolIn(chat(local), model = api))
        // Scheduled tasks run through the same gate.
        val task = chat(api).copy(taskId = "aufgabe-1")
        assertFalse(mailToolIn(task, tools = ToolSelection(github = false, documents = true, web = true)))
        assertTrue(mailToolIn(chat(local).copy(taskId = "aufgabe-2"), tools = ToolSelection(github = false, documents = true, web = true)))
        // Not in the vault either.
        assertFalse(mailToolIn(chat(local).copy(vault = true)))
        // The tools refuse a caller outside the device even if a call should get through.
        val refused = runBlocking { c.outlookTools.execute(OutlookTools.LIST_MAILS, "{}", RepoContext(mutableListOf(), chatId = "x", model = api)).text }
        assertTrue(refused.startsWith("FEHLER"), refused)
        val listed = runBlocking { c.outlookTools.execute(OutlookTools.LIST_MAILS, "{}", RepoContext(mutableListOf(), chatId = "x", model = local)).text }
        assertTrue("Gutachten" in listed, listed)
        // A model outside the device that gets the mail tool on hears where mail work happens.
        val system = c.agent.buildApiMessages(chat(api), RepoContext(mutableListOf()), c.agent.providers(chat(api)), api).first().content
        assertTrue(MailAccess.REMOTE_HINT in system)
    }

    @Test
    fun thePostfachChatTakesNoModelOutsideTheDevice() {
        val postfach = chat(local, mode = ChatModes.MAIL.id)
        assertTrue(ChatModes.MAIL.onDevice)
        assertEquals(PrivacyLevel.LOCAL, Privacy.level(c, postfach))
        // The model picker only offers models on this device.
        val offered = listOf(api, uni, local).filter { Privacy.allows(c, Privacy.level(c, postfach), it) }
        assertEquals(listOf(local), offered)
        assertNotNull(Privacy.blockReason(c, postfach, api))
        assertNotNull(Privacy.blockReason(c, postfach, uni))
        assertNull(Privacy.blockReason(c, postfach, local))
        // In the mode with a server model there is no mail tool, with the local one only few tools (short prompts).
        assertFalse(mailToolIn(postfach.copy(model = api)))
        val tools = c.agent.providers(postfach)
        assertTrue(c.outlookTools in tools && tools.size <= 2, tools.toString())
        // The data note of the mode.
        assertEquals("Mails verlassen den PC nicht – ein Modell auf diesem Gerät liest sie.", ChatModes.MAIL.data)
        assertFalse("gewählte Modell" in OutlookTools.MODE_PROMPT)
    }

    @Test
    fun aChatTheMailboxWentIntoStaysOnTheDevice() {
        val conv = touched()
        assertTrue(MailAccess.touchedMailbox(conv) && MailAccess.locked(conv))
        assertEquals(PrivacyLevel.LOCAL, Privacy.level(c, conv))
        assertNotNull(Privacy.blockReason(c, conv, api), "Wechsel auf ein API-Modell")
        // From now on only the mail tools: no web search, batches, folders or knowledge that could carry mail content off.
        val tools = c.agent.providers(conv)
        assertFalse(c.batchTools in tools || c.webTools in tools || c.documentTools in tools || c.assistantTools in tools, tools.toString())
        // A normal chat with an API model keeps its batches – but never the mail tools.
        val other = c.agent.providers(chat(api))
        assertTrue(c.batchTools in other && c.outlookTools !in other)
        // Earlier Postfach chats are no material for a model outside the device, and the memory learns nothing there.
        c.chats.save(conv)
        assertTrue(io.github.thepytales.llmhub.agent.Memory.earlierChats(c, chat(api), api).none { it.id == conv.id })
        assertTrue(io.github.thepytales.llmhub.agent.Memory.earlierChats(c, chat(local), local).any { it.id == conv.id })
    }

    @Test
    fun noRequestWithMailDataLeavesForAServer() {
        val conv = touched()
        val messages = conv.messages
        // The last line in the LLM client: mail tool results and mail tools never go to a server.
        assertFailsWith<IllegalStateException> { MailAccess.guard(api, messages, null) }
        assertFailsWith<IllegalStateException> { MailAccess.guard(uni, emptyList(), c.outlookTools.defs.toSchema()) }
        MailAccess.guard(local, messages, c.outlookTools.defs.toSchema())
        MailAccess.guard(api, listOf(ChatMessage(role = "user", content = "Hallo")), null)
        val e = assertFailsWith<IllegalStateException> { runBlocking { c.llm.streamChat(api, messages, null) { } } }
        assertTrue("Gerät" in e.message.orEmpty(), e.message)
        // Code that sends mail content to a model itself is refused outside the device.
        assertFailsWith<ActionFailed> { MailAccess.check(api) }
        MailAccess.check(local)
        assertFailsWith<ActionFailed> { runBlocking { MailAccess.complete(c, listOf(ChatMessage(role = "user", content = "Mail")), api) } }
    }

    @Test
    fun loraLeadsToThePostfachButReadsNoMails() {
        // Lora's own tools reach no mailbox.
        assertTrue((LoraAgent.TOOLS + LoraMail.TOOL).none { MailAccess.isMailTool(it.name) })
        // A clear mail task is taken on the device – carried out by the model on this device in a Postfach chat.
        val reply = assertNotNull(LoraMail.fromWish(c, "Fass mir bitte die Mails von gestern zusammen", null))
        val step = reply.steps.single() as LoraStep.Action
        assertTrue(MailAccess.isMailAction(step.plan.actionId), "Schrittzeile „läuft auf diesem Gerät“")
        val result = runBlocking { step.plan.run() }
        val opened = assertNotNull(ChatHandoff.pending.value)
        assertEquals(ChatModes.MAIL.id, opened.mode)
        // Without a model on this device the chat waits and offers one instead of sending.
        assertFalse(opened.send)
        assertNull(opened.model)
        assertEquals(MailAccess.NEEDS_MODEL, result.text)
        // Lora's model only hears that it runs on the device – no mail content, no result.
        val (output, toolStep) = LoraAgent(c).runTool("postfach_auftrag", JsonObject(mapOf("auftrag" to JsonPrimitive("Was ist wichtig?"))), emptyList())
        assertEquals(LoraMail.TOOL_OUTPUT, output)
        assertTrue(toolStep is LoraStep.Action)
        // Wishes about other things stay Lora's.
        assertNull(LoraMail.fromWish(c, "Mach das Design dunkel", null))
    }

    @Test
    fun flowsKeepMailWorkOnTheDevice() {
        var serverStepRan = false
        ToolActions.register(
            listOf(
                ToolAction("mail.test_read", ActionArea.SLIDES, "Postfach lesen", "Liest das Postfach (Test).", emptyList(), risky = false, android = false) { cc, _ ->
                    ActionPlan("mail.test_read", "Liest das Postfach …", false) { ActionResult("gelesen") }.also { MailAccess.requireModel(cc) }
                },
                ToolAction("mail.test_list", ActionArea.SLIDES, "Postfach zählen", "Zählt Mails (Test, ohne Modell).", emptyList(), risky = false, android = false) { _, _ ->
                    ActionPlan("mail.test_list", "Zählt Mails …", false) { ActionResult("1 Mail") }
                },
                ToolAction("test.server_summary", ActionArea.SLIDES, "Zusammenfassen", "Fasst zusammen (Test).", emptyList(), risky = false, android = false, usesModel = true) { _, _ ->
                    ActionPlan("test.server_summary", "Fasst zusammen …", false) { serverStepRan = true; ActionResult("fertig") }
                },
            ),
        )
        // A mail action needs the model on this device: without one the step stops with what to do.
        val (needs, _) = assertNotNull(LoraFlows.draft(c, "Postfach lesen", listOf("mail.test_read" to emptyMap()), android = false))
        assertTrue(MailAccess.DATA_NOTE in needs.note, needs.note)
        assertTrue(MailAccess.ON_DEVICE in needs.steps[0].title, needs.steps[0].title)
        LoraFlows.start(c, needs.id)
        val failed = waitFor(needs.id) { it.state == LoraFlow.FAILED }
        assertEquals(MailAccess.NEEDS_MODEL, failed.steps[0].result)
        // After a Postfach step, nothing goes on to a model on a server.
        val (flow, _) = assertNotNull(LoraFlows.draft(c, "Zählen, dann zusammenfassen", listOf("mail.test_list" to emptyMap(), "test.server_summary" to emptyMap()), android = false))
        LoraFlows.start(c, flow.id)
        val stopped = waitFor(flow.id) { it.state == LoraFlow.FAILED || it.state == LoraFlow.DONE }
        assertEquals(FlowStep.DONE, stopped.steps[0].status)
        assertEquals(FlowStep.FAILED, stopped.steps[1].status, stopped.toString())
        assertFalse(serverStepRan)
    }

    @Test
    fun theHandoffCarriesOnlyTheCheckedText() {
        val summary = "Frau Dr. Anna Kaltenbach (a.kaltenbach@example.org) bittet in [M1] um das Gutachten bis 15.11. Entwurf: D1 liegt in Outlook."
        val prepared = MailHandoff.prepare(c, summary, question = "Formuliere daraus eine kurze Notiz.")
        assertFalse("[M1]" in prepared || "D1" in prepared, prepared)
        assertFalse("a.kaltenbach@example.org" in prepared || "Kaltenbach" in prepared, "pseudonymisiert: $prepared")
        assertTrue(prepared.startsWith("Formuliere daraus eine kurze Notiz.") && "Gutachten bis 15.11." in prepared, prepared)
        // Lora's handoff from a Postfach chat takes the answer of the local model – on the card to check first.
        val conv = touched()
        c.chats.save(conv)
        val review = MailHandoff.offer(c, assertNotNull(MailHandoff.lastLocalAnswer(conv)), conv.id)
        assertEquals(review, MailHandoff.pending.value)
        assertEquals(ModelCatalog.DEFAULT_CHAT, review.model, "Standard: DeepSeek V4.1 Flash")
        assertFalse("[M1]" in review.text)
        // The person changes the text; exactly this text goes out – no files, pictures, instructions, mode or mail ids.
        val checked = review.text + "\nBitte knapp."
        val content = MailHandoff.content(checked, review.model)
        assertEquals(checked.trim(), content.text)
        assertTrue(content.files.isEmpty() && content.images.isEmpty())
        assertNull(content.instructions)
        assertNull(content.mode)
        assertEquals(review.model, content.model)
        // The new chat cannot reach the mailbox, and its request holds the checked text and nothing of the mails.
        val handed = chat(content.model!!).appendToThread(ChatMessage(role = "user", content = content.text, handoff = content.handoff))
        assertFalse(MailAccess.locked(handed))
        assertFalse(mailToolIn(handed))
        val sent = c.agent.buildApiMessages(handed, RepoContext(mutableListOf()), c.agent.providers(handed))
        val user = sent.single { it.role == "user" }
        assertTrue(user.content.startsWith(checked.trim()), user.content)
        assertTrue(sent.none { it.role == "tool" } && sent.none { "Posteingang: 1 Mails" in it.content })
    }

    @Test
    fun aModelForThePostfachFitsThePc() {
        // Without a graphics card the 4B (tool calls, on the processor), with one the 9B, on small devices the 2B.
        assertEquals("qwen3.5-4b", MailAccess.suggestedDownload(Hardware(phone = false, ramGb = 16.0, cores = 4))?.id)
        assertEquals("qwen3.5-9b", MailAccess.suggestedDownload(Hardware(phone = false, ramGb = 32.0, cores = 8, gpuName = "RTX", vramGb = 8.0))?.id)
        assertEquals("qwen3.5-2b", MailAccess.suggestedDownload(Hardware(phone = true, ramGb = 6.0, cores = 8))?.id)
        assertTrue(listOfNotNull(MailAccess.suggestedDownload(null)).all { it.tools })
        // Nothing installed here: no model, and actions say what to do.
        assertNull(MailAccess.model(c))
        assertEquals(MailAccess.NEEDS_MODEL, assertFailsWith<ActionFailed> { MailAccess.requireModel(c) }.message)
    }

    @Test
    fun mailsAreShortenedForTheLocalModel() {
        val fresh = "Liebe Kollegin,\n\n" + "bitte schicken Sie mir das Gutachten. ".repeat(20)
        val quoted = "\n\nAm 02.11.2026 um 10:00 schrieb Prof. Beispiel:\n> Alte Nachricht " + "x".repeat(5000)
        val short = MailAccess.shorten(fresh + quoted)
        assertFalse("Alte Nachricht" in short, short)
        assertTrue(short.startsWith("Liebe Kollegin") && "full=true" in short)
        assertTrue("Alte Nachricht" in MailAccess.shorten(fresh + quoted, full = true))
        val long = MailAccess.shorten("Wort ".repeat(3000))
        assertTrue(long.length < MailAccess.SHORT_CHARS + 120, long.length.toString())
        assertTrue(runBlocking { c.outlookTools.execute(OutlookTools.READ_MAIL, "{\"id\":\"M1\"}", RepoContext(mutableListOf(), model = local)).text }.let { "Gutachten" in it || "Kürzel" in it })
    }

    private fun waitFor(id: String, until: (LoraFlow) -> Boolean): LoraFlow {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            LoraFlows.get(c, id)?.takeIf(until)?.let { return it }
            Thread.sleep(30)
        }
        error("Ablauf kam nicht an: ${LoraFlows.get(c, id)}")
    }
}
