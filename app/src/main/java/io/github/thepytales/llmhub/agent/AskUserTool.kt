package io.github.thepytales.llmhub.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One question of [AskUserTool]: [options] as answer buttons, [multi] for several answers; a free answer is always possible. */
data class UserQuestion(val question: String, val options: List<String>, val multi: Boolean = false)

/**
 * Rückfragen mit Antwortknöpfen (AP-06): instead of guessing, the model asks 1–4 short questions. They appear as a card
 * in the chat; the answers go back as one message. The run ends after the call (the agent waits for the answer).
 */
class AskUserTool : ToolProvider {
    override val defs = listOf(
        ToolDef(
            NAME,
            "Stellt dem Nutzer 1–4 kurze Rückfragen mit Antwortknöpfen, statt zu raten – z.B. vor einem Dokument, Antrag oder " +
                "einer größeren Aufgabe, wenn Ziel, Zielgruppe, Umfang oder Form unklar sind. Die Fragen erscheinen als Karte im " +
                "Chat; eine freie Antwort ist immer möglich. Nach dem Aufruf endet deine Antwort: Die Antworten kommen als nächste " +
                "Nachricht. Schreibe keinen weiteren Text in derselben Antwort und frage nichts, was schon geklärt ist.",
            listOf(
                Param(
                    "questions", "Die Fragen (1–4), je mit 2–5 kurzen Antwortmöglichkeiten.",
                    schema = buildJsonObject {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", MAX_QUESTIONS)
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("question") { put("type", "string"); put("description", "Die Frage, kurz und klar") }
                                putJsonObject("options") {
                                    put("type", "array")
                                    putJsonObject("items") { put("type", "string") }
                                    put("description", "2–5 Antwortmöglichkeiten, je wenige Wörter")
                                }
                                putJsonObject("multi") { put("type", "boolean"); put("description", "true: mehrere Antworten möglich") }
                            }
                            putJsonArray("required") { add(JsonPrimitive("question")); add(JsonPrimitive("options")) }
                        }
                    },
                ),
            ),
        )
    )

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput {
        val questions = parse(args)
        if (questions.isEmpty()) return ToolOutput("FEHLER: Keine Frage erkannt. Übergib questions als Liste von {question, options, multi}.")
        return ToolOutput(
            "Die ${questions.size} Frage(n) werden dem Nutzer als Karte mit Antwortknöpfen angezeigt. Beende jetzt deine Antwort " +
                "ohne weiteren Text – die Antworten kommen als nächste Nachricht.",
        )
    }

    companion object {
        const val NAME = "ask_user"
        const val MAX_QUESTIONS = 4

        /** Note for the system prompt: when to ask instead of guessing. */
        const val PROMPT = "Rückfragen: Ist eine größere Aufgabe (Dokument, Antrag, Präsentation, viele Dateien) unklar – Ziel, " +
            "Zielgruppe, Umfang, Form –, frage mit ask_user nach (höchstens eine Runde mit 1–4 Fragen und Antwortknöpfen), statt zu raten. " +
            "Bei einfachen Fragen antworte direkt."

        /** The questions of an ask_user call (tolerant: a JSON string instead of an array, a single object, missing options). */
        fun parse(args: JsonObject): List<UserQuestion> {
            val raw: JsonElement = args["questions"] ?: return emptyList()
            val list: List<JsonElement> = when (raw) {
                is JsonArray -> raw
                is JsonObject -> listOf(raw)
                is JsonPrimitive -> runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(raw.content).let { e -> if (e is JsonArray) e.toList() else listOf(e) }
                }.getOrDefault(emptyList())
                else -> emptyList()
            }
            return list.mapNotNull { e ->
                val o = (e as? JsonObject) ?: return@mapNotNull (e as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { UserQuestion(it, emptyList()) }
                val q = (o["question"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (q.isEmpty()) return@mapNotNull null
                val options = runCatching { o["options"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } } }
                    .getOrNull().orEmpty().distinct().take(6)
                UserQuestion(q, options, (o["multi"] as? JsonPrimitive)?.booleanOrNull ?: false)
            }.take(MAX_QUESTIONS)
        }

        /** The questions of a stored call (for the card in the chat). */
        fun parse(arguments: String): List<UserQuestion> = parse(parseArgs(arguments))

        /** The answers as one message: question by question, chosen buttons and the free answer. */
        fun answerText(questions: List<UserQuestion>, chosen: List<Set<String>>, free: List<String>): String =
            questions.mapIndexed { i, q ->
                val parts = chosen.getOrNull(i).orEmpty().toList() + listOfNotNull(free.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() })
                "${q.question}\n→ " + (parts.joinToString(", ").ifEmpty { "(keine Angabe – triff eine sinnvolle Annahme)" })
            }.joinToString("\n\n")

        /** What „Überspringen“ sends. */
        const val SKIP_TEXT = "Bitte ohne weitere Rückfragen fortfahren – triff sinnvolle Annahmen und nenne sie kurz."
    }
}

/**
 * TU Vorlagen als Fragerunde (AP-06): instead of the form, Lora asks for the fields step by step with answer buttons and
 * then carries out the template with the answers.
 */
object TemplateInterview {
    /** Templates with this many fields or more get the button „Mit Lora Schritt für Schritt“. */
    const val MIN_FIELDS = 3

    fun offered(t: io.github.thepytales.llmhub.data.Template): Boolean = t.direct == null && t.fields.size >= MIN_FIELDS

    /** The visible message that starts the round. */
    fun request(t: io.github.thepytales.llmhub.data.Template): String = "Ich möchte die TU Vorlage „${t.name}“ Schritt für Schritt mit dir ausfüllen."

    /** Hidden instructions for the model: the fields, what is known already, and the task of the template. */
    fun instructions(t: io.github.thepytales.llmhub.data.Template, known: Map<String, String>): String = buildString {
        append("VORLAGE ALS FRAGERUNDE: Fülle mit dem Nutzer die TU Vorlage „${t.name}“ aus.\n")
        append("Felder (Name – Hinweis):\n")
        t.fields.forEach { f -> append("- ${f.label}").append(if (f.hint.isNotBlank()) " – ${f.hint}" else "").append('\n') }
        val filled = known.filterValues { it.isNotBlank() }
        if (filled.isNotEmpty()) {
            append("Schon ausgefüllt:\n")
            filled.forEach { (k, v) -> append("- $k: ${v.trim()}\n") }
        }
        append(
            "Frage mit ask_user in Runden nach den fehlenden Feldern: je Runde bis zu ${AskUserTool.MAX_QUESTIONS} Felder, mit typischen " +
                "Antwortmöglichkeiten als Knöpfe, wo es welche gibt (sonst options leer lassen – eine freie Antwort ist immer möglich). " +
                "Erzwinge keine Felder, die leer bleiben dürfen. Fasse nach jeder Runde kurz zusammen, was feststeht.\n",
        )
        append("Sind alle nötigen Angaben da, erledige den Auftrag der Vorlage mit den Antworten anstelle der {{Felder}}:\n<<<\n")
        append(t.content.trim()).append("\n>>>")
        if (t.instructions.isNotBlank()) append("\n\nHinweise der Vorlage: ").append(t.instructions.trim())
    }
}
