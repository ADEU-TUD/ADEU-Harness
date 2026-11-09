package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.Privacy
import io.github.thepytales.llmhub.data.Pseudonymizer
import io.github.thepytales.llmhub.local.Fit
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalModel
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.ui.ChatHandoff
import io.github.thepytales.llmhub.ui.splitThinking
import io.github.thepytales.llmhub.ui.tr
import io.github.thepytales.llmhub.ui.trLater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Postfach nur mit lokalem Modell (AP-68): the one place that decides whether a model gets at mails, folders,
 * appointments and tasks – the classic Outlook (COM) and the mail server (IMAP) alike, both behind
 * [io.github.thepytales.llmhub.data.OutlookBridge]. Only a model that runs on this device (llama.cpp, local/LocalLlm)
 * may; the Uni server and external providers never do, and there is no switch to lift this.
 *
 * Every way to the mailbox asks here: the agent's tools (AgentRunner), the Postfach mode (ChatModes.MAIL), Lora's
 * mail tasks and actions („mail.*“, see [isMailAction]), Abläufe, scheduled tasks, and code that sends mail content to a
 * model directly ([complete]). [guard] is the last line in the LLM client: a request with mail tool results never
 * leaves for a server.
 */
object MailAccess {
    /** Short line for step rows and cards: „läuft auf diesem Gerät“. */
    val ON_DEVICE = trLater("läuft auf diesem Gerät")

    /** The data note of the Postfach (mode line, Mail & Kalender, plan cards). */
    val DATA_NOTE = trLater("Mails verlassen den PC nicht – ein Modell auf diesem Gerät liest sie.")

    /** Why the Postfach needs a model on this device. */
    val WHY = trLater(
        "Aus Datenschutzgründen liest nur ein Sprachmodell auf diesem Gerät Mails, Ordner, Termine und Aufgaben. Der Uni-Server " +
            "und externe Anbieter haben keinen Zugriff auf das Postfach – auch nicht auf Wunsch. Ergebnisse des lokalen Modells " +
            "lassen sich nach Prüfung an einen Chat übergeben.",
    )

    /** Without a local model: what to do. */
    val NEEDS_MODEL = trLater("Für das Postfach braucht es ein Modell auf diesem Gerät – unter Einstellungen › Modelle auf diesem Gerät eins laden.")

    // ── The rule ───────────────────────────────────────────────────────────────────────────────────────────

    /** Whether [model] runs on this device – the only kind of model that may read the mailbox. */
    fun onDevice(model: String?): Boolean = model != null && Privacy.isLocal(model)

    /** Tools that reach the mailbox (Outlook and IMAP share them). */
    fun isMailTool(name: String?): Boolean = name != null && (name.startsWith("outlook_") || name.startsWith("mail_"))

    /** Lora actions that work on the mailbox (AP-38 register): every id „mail.…“ – they run only with the local model. */
    fun isMailAction(actionId: String?): Boolean = actionId != null && actionId.startsWith("mail.")

    /** Whether the mailbox already went into [conv]: a mail tool was called or answered there. */
    fun touchedMailbox(conv: Conversation): Boolean = conv.messages.any { m ->
        (m.role == "tool" && isMailTool(m.toolName)) || m.toolCalls.any { isMailTool(it.name) }
    }

    /**
     * A chat bound to this device: the Postfach mode, or any chat the mailbox went into. Its data protection level is
     * „Nur auf diesem Gerät“ ([io.github.thepytales.llmhub.data.Privacy.level]); no model outside the device may answer it.
     */
    fun locked(conv: Conversation): Boolean = ChatModes.byId(conv.mode)?.onDevice == true || touchedMailbox(conv)

    /** Whether a run of [conv] with [model] gets the mail tools: only on this device, never in the vault, only when wanted. */
    fun mayUseMailTools(c: AppContainer, conv: Conversation, model: String): Boolean {
        if (!onDevice(model) || conv.vault || !Outlook.supported) return false
        val mode = ChatModes.byId(conv.mode)?.tools.orEmpty()
        return ModeTool.MAIL in mode || c.settings.current.toolOn(AppTool.MAIL.id)
    }

    /** Why [model] may not answer [conv], null if it may. */
    fun blockReason(conv: Conversation, model: String): String? {
        if (!locked(conv) || onDevice(model)) return null
        return tr(
            "Im Postfach arbeitet nur ein Modell auf diesem Gerät – {0} läuft auf einem Server. Bitte ein Modell auf diesem Gerät " +
                "wählen (Einstellungen › Modelle auf diesem Gerät). Ergebnisse lassen sich danach mit „An Chat übergeben“ weitergeben.",
            ModelCatalog.info(model).displayName,
        )
    }

    /** Throws when [model] may not read mail – for code that sends mail content to a model itself. */
    fun check(model: String) {
        if (!onDevice(model)) throw ActionFailed(tr("Mails liest nur ein Modell auf diesem Gerät – {0} läuft auf einem Server.", ModelCatalog.info(model).displayName))
    }

    /**
     * Last line in the LLM client: a request that carries mail tool results or offers mail tools never goes to a
     * model outside the device, whichever way it was built.
     */
    fun guard(model: String, messages: List<ChatMessage>, tools: JsonArray?) {
        if (onDevice(model)) return
        val mailData = messages.any { m -> (m.role == "tool" && isMailTool(m.toolName)) || m.toolCalls.any { isMailTool(it.name) } }
        val mailTools = tools?.any { t -> isMailTool(((t as? JsonObject)?.get("function") as? JsonObject)?.get("name")?.let { (it as? JsonPrimitive)?.content }) } == true
        if (mailData || mailTools) throw IllegalStateException(blockText(model))
    }

    private fun blockText(model: String) =
        tr("Mails, Termine und Aufgaben gehen nicht an {0}: Ins Postfach darf nur ein Modell auf diesem Gerät.", ModelCatalog.info(model).displayName)

    // ── The local model for the Postfach ──────────────────────────────────────────────────────────────────

    /**
     * The model on this device for mail work, or null without one: the one loaded already (no second start), else
     * the strongest installed model that runs well on this PC, else the smallest one. Only models with tool calls.
     */
    fun model(c: AppContainer): String? {
        val installed = c.localLlm.installedModels().filter { it.tools }
        if (installed.isEmpty()) return null
        c.localLlm.state.value.running?.let { r -> installed.firstOrNull { it.modelId == r }?.let { return it.modelId } }
        val hw = c.localLlm.hardware.value
        val fitting = if (hw == null) emptyList() else installed.filter { LocalCatalog.fit(it, hw) == Fit.OK }
        return (fitting.maxByOrNull { it.needGb } ?: installed.minByOrNull { it.needGb })?.modelId
    }

    /** [model] or [ActionFailed] with what to do – for Lora's mail actions and Abläufe. */
    fun requireModel(c: AppContainer): String = model(c) ?: throw ActionFailed(tr(NEEDS_MODEL))

    /**
     * A model to download for the Postfach (Offene Frage 10): with tool calls, sized for this PC – with a graphics card
     * (8 GB and more) Qwen 3.5 9B, without one Qwen 3.5 4B, on small devices the 2B. Null when none fits.
     */
    fun suggestedDownload(hw: Hardware?): LocalModel? {
        val order = if (hw != null && hw.vramGb >= 8) listOf("qwen3.5-9b", "qwen3.5-4b", "gemma-4-e4b", "qwen3.5-2b") else listOf("qwen3.5-4b", "gemma-4-e4b", "qwen3.5-2b")
        val models = order.mapNotNull { LocalCatalog.get(it) }.filter { it.tools }
        if (hw == null) return models.firstOrNull()
        return models.firstOrNull { LocalCatalog.fit(it, hw) == Fit.OK } ?: models.lastOrNull { LocalCatalog.fit(it, hw) != Fit.TOO_BIG }
    }

    /**
     * Sends mail content to the model on this device and returns its answer (no tools, thinking off) – summaries in
     * Mail & Kalender, sorting (AP-69). Refuses every model outside the device.
     */
    suspend fun complete(c: AppContainer, messages: List<ChatMessage>, model: String? = null): String {
        val m = model ?: requireModel(c)
        check(m)
        val result = c.llm.streamChat(m, messages, null, thinking = ThinkingLevel.OFF) { }
        return splitThinking(result.content).second.trim()
    }

    // ── Tempo: short prompts, shortened mails ──────────────────────────────────────────────────────────────

    /** Who answers in a Postfach chat – short, local models read every token on the processor. */
    const val IDENTITY = "Du bist Lora, die Helferin der App „Lora“ (TU Dresden), und läufst auf diesem PC. Mails verlassen den PC nicht."

    /** For chats with a model outside the device while the mail tool is on: where mail work happens. */
    const val REMOTE_HINT = "Auf Mails, Ordner, Termine und Aufgaben hast du keinen Zugriff: Ins Postfach darf aus Datenschutzgründen nur ein " +
        "Modell auf diesem Gerät (Modus „Postfach“ mit einem lokalen Modell). Bei solchen Wünschen verweise freundlich darauf."

    /** Characters of a mail the model reads by default (head and beginning). */
    const val SHORT_CHARS = 2_500

    /** Characters of a mail read in full (on request). */
    const val FULL_CHARS = 20_000

    private val QUOTE = Regex(
        "(?m)^(\\s*-{2,}\\s*(Original(nachricht| Message)|Ursprüngliche Nachricht|Weitergeleitete Nachricht|Forwarded message)\\s*-{2,}|" +
            "\\s*Am .{5,160}schrieb .{1,160}:\\s*$|\\s*On .{5,160}wrote:\\s*$|\\s*(Von|From): .+\\R\\s*(Gesendet|Datum|Sent|Date): |\\s*>)",
    )

    /**
     * Kopf und Anfang (AP-68, Tempo): the new part of a mail without the quoted history, at most [SHORT_CHARS]; the whole
     * mail ([FULL_CHARS]) only with [full].
     */
    fun shorten(body: String, full: Boolean = false): String {
        val text = body.trim()
        if (full) return if (text.length <= FULL_CHARS) text else text.take(FULL_CHARS) + "\n\n[… gekürzt nach $FULL_CHARS Zeichen]"
        val fresh = QUOTE.find(text)?.range?.first?.takeIf { it >= 40 }?.let { text.substring(0, it).trimEnd() } ?: text
        if (fresh.length <= SHORT_CHARS) {
            return if (fresh.length < text.length) "$fresh\n\n[… zitierter Verlauf ausgelassen – mit full=true die ganze Mail lesen]" else fresh
        }
        val cut = fresh.take(SHORT_CHARS).let { s -> s.substringBeforeLast(' ').takeIf { it.length > SHORT_CHARS / 2 } ?: s }
        return "$cut\n\n[… gekürzt, ${text.length} Zeichen – mit full=true die ganze Mail lesen]"
    }

    // ── Handing over (only what the local model wrote) ─────────────────────────────────────────────────────

    private val SHORT_ID = Regex("""\s?[\[(]\s*[MFTD]\d{1,5}(\s*[,;/]\s*[MFTD]\d{1,5})*\s*[\])]""")
    private val LABELLED_ID = Regex("""\b(Entwurf|Termin|Kürzel|Mail|Ordner):?\s+[MFTD]\d{1,5}\b""")
    private val BARE_ID = Regex("""(?<![\p{L}\d-])[MD]\d{1,3}(?![\p{L}\d-])""")
    private val LONG_ID = Regex("""\b[0-9A-Fa-f]{24,}(\|[0-9A-Za-z]*)?\b|\S+\|[0-9A-Fa-f]{8,}\S*""")

    /** Removes the mail ids of the Postfach („[M3]“, „Entwurf: D1“, Outlook's long ids) from [text]. */
    fun stripIds(text: String): String = text
        .replace(SHORT_ID, "")
        .replace(LABELLED_ID) { it.groupValues[1] }
        .replace(LONG_ID, "")
        .replace(BARE_ID, "")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("[ \\t]{2,}"), " ")
        .trim()

    /** [text] with personal details replaced by placeholders – always, whatever Einstellungen › Datenschutz says. */
    fun pseudonymize(c: AppContainer, text: String): String {
        val before = Pseudonymizer.FORCE.get()
        Pseudonymizer.FORCE.set(true)
        return try {
            c.privacy.mask(text, HANDOFF_SCOPE)
        } finally {
            Pseudonymizer.FORCE.set(before)
        }
    }

    private const val HANDOFF_SCOPE = "postfach-uebergabe"
}

/**
 * An Chat übergeben (AP-68): what the model on this device wrote goes to a chat with an API model (DeepSeek by default)
 * – pseudonymized, without mail ids, shown first as a card to check and change ([pending]). Exactly the checked text
 * goes out: no files, no pictures, no hidden instructions, no mode; the new chat cannot reach the mailbox.
 */
object MailHandoff {
    data class Review(val text: String, val model: String, val chatId: String?)

    /** The card to check, shown by the app (ui/MailLocalModel); null while none is open. */
    val pending = MutableStateFlow<Review?>(null)

    /** The model a Postfach result goes to: the one for Lora's handoffs (Einstellungen › Lora), DeepSeek by default. */
    fun model(c: AppContainer): String = c.settings.current.loraHandoffModel.ifBlank { ModelCatalog.DEFAULT_CHAT }
        .takeIf { !Privacy.isLocal(it) } ?: ModelCatalog.DEFAULT_CHAT

    /** The text for the card: [question] (if any) and [text] of the local model, mail ids removed, pseudonymized. */
    fun prepare(c: AppContainer, text: String, question: String = ""): String {
        val body = MailAccess.stripIds(splitThinking(text).second)
        val joined = listOf(question.trim(), body).filter { it.isNotBlank() }.joinToString("\n\n")
        return MailAccess.pseudonymize(c, joined).trim()
    }

    /** Opens the card for [text] written by the local model in the chat [chatId]. */
    fun offer(c: AppContainer, text: String, chatId: String?, question: String = ""): Review =
        Review(prepare(c, text, question), model(c), chatId).also { pending.value = it }

    /** The last answer the model on this device wrote in [conv] (without thinking), or null. */
    fun lastLocalAnswer(conv: Conversation): String? = conv.messages
        .lastOrNull { it.role == "assistant" && !it.error && MailAccess.onDevice(it.model) && splitThinking(it.content).second.isNotBlank() }
        ?.let { splitThinking(it.content).second }

    /** The new chat for the checked [text]: exactly this text as its first message, nothing else. */
    fun content(text: String, model: String): ChatHandoff.Content = ChatHandoff.Content(
        text = text.trim(),
        files = emptyList(),
        images = emptyList(),
        mode = null,
        send = true,
        instructions = null,
        handoff = tr("Aus dem Postfach, geprüft") + " · " + ModelCatalog.info(model).displayName,
        model = model,
    )
}
