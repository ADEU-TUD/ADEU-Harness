package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.FileBackend
import io.github.thepytales.llmhub.data.ListStore
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** One step of a [LoraFlow]: an action of AP-38 with its parameters and what came of it. */
@Serializable
data class FlowStep(
    val actionId: String,
    val args: Map<String, String> = emptyMap(),
    val title: String,
    /** pending, running, confirm (risky: waits for „Ausführen“), approved, done, failed, skipped. */
    val status: String = PENDING,
    val result: String = "",
    /** Files the step made (the next step works on them). */
    val files: List<String> = emptyList(),
    /** Preview of a risky step (vorher → nachher) while it waits. */
    val preview: List<List<String>> = emptyList(),
    /** Switched off under „Ändern“. */
    val enabled: Boolean = true,
    val estimateSec: Int = 10,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
) {
    companion object {
        const val PENDING = "pending"
        const val RUNNING = "running"
        const val CONFIRM = "confirm"
        const val APPROVED = "approved"
        const val DONE = "done"
        const val FAILED = "failed"
        const val SKIPPED = "skipped"
    }
}

/**
 * Ein Ablauf über Werkzeuge (AP-40): Lora plans first („Aus dieser Aufnahme: transkribieren, Untertitel, kürzen,
 * Handout als Word“), shows the plan as a card (steps, tools, estimated time, data protection) with Los · Ändern ·
 * Abbrechen, then carries out the steps with the actions of AP-38. Stored in „lora-ablaeufe.json“, so a running one
 * carries on after a restart of the app.
 */
@Serializable
data class LoraFlow(
    val id: String = UUID.randomUUID().toString(),
    val wish: String,
    val steps: List<FlowStep>,
    /** draft, running, paused, done, failed, cancelled. */
    val state: String = DRAFT,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val estimateSec: Int get() = steps.filter { it.enabled }.sumOf { it.estimateSec }
    val active: Boolean get() = state == RUNNING || state == PAUSED || state == FAILED

    companion object {
        const val DRAFT = "draft"
        const val RUNNING = "running"
        const val PAUSED = "paused"
        const val DONE = "done"
        const val FAILED = "failed"
        const val CANCELLED = "cancelled"
    }
}

/** Plans, stores and runs Lora's Abläufe (AP-40); the panel shows them (ui/LoraHelper, FlowCard). */
object LoraFlows {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Rückgängig of the steps that ran in this session (flow id → step index). */
    private val undos = ConcurrentHashMap<String, suspend () -> String>()

    @Volatile
    private var store: Pair<File, ListStore<LoraFlow>>? = null

    /** The plans of [c]'s data folder (one app has one; tests use several). */
    private fun store(c: AppContainer): ListStore<LoraFlow> {
        val file = File(c.appContext.filesDir, "lora-ablaeufe.json")
        store?.takeIf { it.first == file }?.let { return it.second }
        return synchronized(this) {
            store?.takeIf { it.first == file }?.second
                ?: ListStore(FileBackend(file), LoraFlow.serializer(), { it.id }).also { store = file to it }
        }
    }

    fun flows(c: AppContainer): StateFlow<List<LoraFlow>> = store(c).items

    fun get(c: AppContainer, id: String): LoraFlow? = store(c).get(id)

    /** Rough duration of an action, for the plan card. */
    internal fun estimate(actionId: String): Int = when (actionId) {
        "files.transcribe" -> 180
        "files.handout", "doc.write" -> 45
        "video.export" -> 300
        "video.subtitles", "video.rough_cut", "video.shorten", "video.text_cut" -> 60
        "pdf.ocr" -> 60
        "files.rename_by_content" -> 30
        "pdf.compress", "pdf.convert", "files.to_nextcloud" -> 15
        else -> 5
    }

    /** A readable line for a step: the action and its main parameter. */
    internal fun title(action: ToolAction, args: Map<String, String>): String {
        val main = args.entries.firstOrNull { it.value.isNotBlank() && it.key != "name" }?.value?.take(50)
        return tr(action.title) + (main?.let { " – $it" } ?: "")
    }

    /**
     * A new plan from [steps] (action id and parameters). Unknown actions and ones not on this platform are dropped;
     * the second value names them. Null when nothing is left.
     */
    fun draft(c: AppContainer, wish: String, steps: List<Pair<String, Map<String, String>>>, android: Boolean): Pair<LoraFlow, List<String>>? {
        val dropped = mutableListOf<String>()
        val list = steps.mapNotNull { (id, args) ->
            val action = ToolActions.byId(id) ?: ToolActions.byToolName(id)
            if (action == null || (android && !action.android)) { dropped += id; return@mapNotNull null }
            // Mail work (AP-68) runs with the model on this device – the step line says so.
            val where = if (MailAccess.isMailAction(action.id)) " · " + tr(MailAccess.ON_DEVICE) else ""
            FlowStep(action.id, args.filterValues { it.isNotBlank() }, title(action, args) + where, estimateSec = estimate(action.id))
        }
        if (list.isEmpty()) return null
        val actions = list.mapNotNull { ToolActions.byId(it.actionId) }
        val note = buildList {
            add(if (actions.any { it.usesModel } || list.any { it.actionId == "files.transcribe" }) ToolActions.privacyNote(c) else tr("Bleibt auf diesem Gerät."))
            if (actions.any { MailAccess.isMailAction(it.id) }) add(tr(MailAccess.DATA_NOTE))
            if (actions.any { it.risky }) add(tr("Riskante Schritte zeigen vorher eine Vorschau."))
        }.joinToString(" ")
        val flow = LoraFlow(wish = wish, steps = list, note = note)
        store(c).upsert(flow)
        return flow to dropped
    }

    private fun update(c: AppContainer, id: String, f: (LoraFlow) -> LoraFlow) = store(c).update(id) { f(it).copy(updatedAt = System.currentTimeMillis()) }

    private fun step(c: AppContainer, id: String, i: Int, f: (FlowStep) -> FlowStep) =
        update(c, id) { fl -> fl.copy(steps = fl.steps.mapIndexed { k, s -> if (k == i) f(s) else s }) }

    /** Switches a step on or off (under „Ändern“, before „Los“). */
    fun toggle(c: AppContainer, id: String, i: Int) = step(c, id, i) { it.copy(enabled = !it.enabled) }

    /** „Los“ / „Fortsetzen“: runs the open steps one after another in the background. */
    fun start(c: AppContainer, id: String) {
        if (jobs[id]?.isActive == true) return
        update(c, id) { it.copy(state = LoraFlow.RUNNING) }
        ToolActions.log("Ablauf gestartet: ${get(c, id)?.wish?.take(80)}")
        jobs[id] = scope.launch { runSteps(c, id) }
    }

    /** „Pause“: the running step stops (it is repeated on „Fortsetzen“). */
    fun pause(c: AppContainer, id: String) {
        jobs.remove(id)?.cancel()
        update(c, id) { fl -> fl.copy(state = LoraFlow.PAUSED, steps = fl.steps.map { if (it.status == FlowStep.RUNNING) it.copy(status = FlowStep.PENDING) else it }) }
    }

    fun cancel(c: AppContainer, id: String) {
        jobs.remove(id)?.cancel()
        update(c, id) { fl -> fl.copy(state = LoraFlow.CANCELLED, steps = fl.steps.map { if (it.status == FlowStep.RUNNING || it.status == FlowStep.CONFIRM) it.copy(status = FlowStep.PENDING) else it }) }
        ToolActions.log("Ablauf abgebrochen")
    }

    /** „Wiederholen“ of a failed step. */
    fun retry(c: AppContainer, id: String, i: Int) {
        step(c, id, i) { it.copy(status = FlowStep.PENDING, result = "") }
        start(c, id)
    }

    /** „Ausführen“ of a risky step that waits with its preview. */
    fun approve(c: AppContainer, id: String, i: Int) {
        step(c, id, i) { it.copy(status = FlowStep.APPROVED) }
        start(c, id)
    }

    /** „Überspringen“ of a waiting or failed step. */
    fun skip(c: AppContainer, id: String, i: Int) {
        step(c, id, i) { it.copy(status = FlowStep.SKIPPED) }
        start(c, id)
    }

    fun canUndo(id: String, i: Int) = undos.containsKey("$id/$i")

    suspend fun undo(c: AppContainer, id: String, i: Int): String {
        val u = undos.remove("$id/$i") ?: return tr("Das lässt sich nicht mehr zurücknehmen.")
        val text = runCatching { u() }.getOrElse { tr("Rückgängig ging nicht: {0}", it.message.orEmpty()) }
        step(c, id, i) { it.copy(status = FlowStep.SKIPPED, result = text) }
        ToolActions.log("Ablaufschritt rückgängig: $text")
        return text
    }

    /** Removes finished and cancelled plans older than a week (called at the start). */
    private fun prune(c: AppContainer) {
        val limit = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        store(c).current.filter { !it.active && it.updatedAt < limit }.forEach { store(c).delete(it.id) }
    }

    /** After a start of the app: Abläufe that were running carry on where they stopped. */
    fun resume(c: AppContainer) {
        prune(c)
        store(c).current.filter { it.state == LoraFlow.RUNNING }.forEach { fl ->
            update(c, fl.id) { f -> f.copy(steps = f.steps.map { if (it.status == FlowStep.RUNNING) it.copy(status = FlowStep.PENDING) else it }) }
            ToolActions.log("Ablauf nach dem Neustart fortgesetzt")
            start(c, fl.id)
        }
    }

    private suspend fun runSteps(c: AppContainer, id: String) {
        val began = System.currentTimeMillis()
        while (true) {
            val flow = get(c, id) ?: return
            if (flow.state != LoraFlow.RUNNING) return
            val i = flow.steps.indexOfFirst { it.enabled && it.status in setOf(FlowStep.PENDING, FlowStep.APPROVED, FlowStep.FAILED) }
            if (i < 0) {
                update(c, id) { it.copy(state = LoraFlow.DONE) }
                ToolActions.log("Ablauf fertig: ${flow.wish.take(80)}")
                if (System.currentTimeMillis() - began > 20_000 || !io.github.thepytales.llmhub.ui.LoraHelper.isOpen.value) {
                    io.github.thepytales.llmhub.tasks.Notifier.batchFinished(
                        c.appContext, tr("Lora: Ablauf fertig"),
                        tr("{0} Schritte erledigt – die Ergebnisse liegen in Erstellt.", flow.steps.count { it.status == FlowStep.DONE }),
                    )
                }
                return
            }
            val s = flow.steps[i]
            val action = ToolActions.byId(s.actionId)
            if (action == null) {
                step(c, id, i) { it.copy(status = FlowStep.FAILED, result = tr("Diese Aktion gibt es hier nicht.")) }
                update(c, id) { it.copy(state = LoraFlow.FAILED) }
                return
            }
            // AP-68: after a Postfach step, content only goes on to a model on this device.
            if (action.usesModel && ToolActions.privacy(c) != io.github.thepytales.llmhub.data.PrivacyLevel.LOCAL &&
                flow.steps.take(i).any { it.status == FlowStep.DONE && MailAccess.isMailAction(it.actionId) }
            ) {
                step(c, id, i) { it.copy(status = FlowStep.FAILED, result = tr("Nach einem Schritt im Postfach arbeitet nur ein Modell auf diesem Gerät weiter – dieser Schritt würde Inhalte an einen Server schicken.")) }
                update(c, id) { it.copy(state = LoraFlow.FAILED) }
                return
            }
            // The files of the step before are what this one works on (zusammenfügen → schwärzen → hochladen).
            val before = flow.steps.take(i).lastOrNull { it.status == FlowStep.DONE && it.files.isNotEmpty() }
            if (before != null) LoraFocus.files = before.files.map(::File).filter { it.isFile }
            step(c, id, i) { it.copy(status = FlowStep.RUNNING, startedAt = System.currentTimeMillis()) }
            try {
                val args = JsonObject(s.args.mapValues { JsonPrimitive(it.value) })
                val plan = action.plan(c, LoraPrefs.withDefaults(c, action, args).first)
                if (plan.risky && s.status != FlowStep.APPROVED) {
                    step(c, id, i) { it.copy(status = FlowStep.CONFIRM, title = plan.title.removeSuffix(" …"), preview = plan.preview.take(30).map { (a, b) -> listOf(a, b) }) }
                    update(c, id) { it.copy(state = LoraFlow.PAUSED) }
                    ToolActions.log("Ablauf wartet auf Bestätigung: ${plan.title}")
                    return
                }
                ToolActions.log("Ablaufschritt: ${plan.title}")
                val r = plan.run()
                r.undo?.let { undos["$id/$i"] = it }
                // „Alles rückgängig seit heute Morgen“ (AP-77) sees the steps of a flow too.
                ToolActions.log("${action.id} ausgeführt: ${r.text}")
                DayJournal.record(action.id, r)
                step(c, id, i) { it.copy(status = FlowStep.DONE, result = r.text, files = (r.files.ifEmpty { listOfNotNull(r.open) }).map { f -> f.absolutePath }, preview = emptyList(), endedAt = System.currentTimeMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = (e as? ActionFailed)?.message ?: io.github.thepytales.llmhub.ui.ErrorHelp.explain(e).text
                ToolActions.log("Ablaufschritt fehlgeschlagen: ${s.actionId}: $msg")
                step(c, id, i) { it.copy(status = FlowStep.FAILED, result = msg, endedAt = System.currentTimeMillis()) }
                update(c, id) { it.copy(state = LoraFlow.FAILED) }
                return
            }
        }
    }

    /** All actions for the planning prompt: id, area and what they need (short). */
    fun catalog(android: Boolean): String = ToolActions.all.filter { !android || it.android }.joinToString("\n") { a ->
        "- ${a.id} (${a.area.label}): ${a.description.substringBefore(". ").take(110)} | " + a.params.joinToString(", ") { p -> p.name + if (p.required) "" else "?" }
    }

    /** Tool for the model: plan_workflow with the steps as a list of {aktion, parameter}. */
    val TOOL = ToolDef(
        "plan_workflow",
        "Plant einen mehrschrittigen Auftrag über mehrere Werkzeuge (z. B. transkribieren, Untertitel, kürzen, Handout). " +
            "Der Nutzer sieht den Plan als Karte und startet ihn selbst. Jeder Schritt arbeitet mit den Dateien des vorigen weiter.",
        listOf(
            Param(
                "schritte", "Die Schritte in Reihenfolge", schema = kotlinx.serialization.json.buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", kotlinx.serialization.json.buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", kotlinx.serialization.json.buildJsonObject {
                            put("aktion", kotlinx.serialization.json.buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("id der Aktion, z. B. pdf.merge")) })
                            put("parameter", kotlinx.serialization.json.buildJsonObject { put("type", JsonPrimitive("object")); put("description", JsonPrimitive("Parameter der Aktion als Text-Werte")) })
                        })
                        put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("aktion"))))
                    })
                },
            ),
        ),
    )

    /** The steps from the arguments of plan_workflow. */
    fun parse(args: JsonObject): List<Pair<String, Map<String, String>>> {
        val raw = args["schritte"] ?: return emptyList()
        val arr = when (raw) {
            is kotlinx.serialization.json.JsonArray -> raw
            is JsonPrimitive -> runCatching { io.github.thepytales.llmhub.net.JSON.parseToJsonElement(raw.content) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o["aktion"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val params = (o["parameter"] as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }.orEmpty()
            id to params
        }
    }

    /** Wishes with several steps („…, dann …, danach …“) – then Lora gets the planning tool and the action catalog. */
    private val MULTI = Regex("(\\bdann\\b|danach|anschliessend|anschließend|zum schluss|am ende|als nachstes|ablauf|schritt|\\bplan)")

    fun looksMultiStep(wish: String): Boolean {
        val n = wish.lowercase()
        return MULTI.containsMatchIn(n) || n.count { it == ',' } >= 2
    }
}
