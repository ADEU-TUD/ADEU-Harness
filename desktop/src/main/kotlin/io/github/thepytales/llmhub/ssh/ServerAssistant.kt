package io.github.thepytales.llmhub.ssh

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.Area
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.ModelCatalog
import io.github.thepytales.llmhub.data.Place
import io.github.thepytales.llmhub.data.PlaceScope
import io.github.thepytales.llmhub.data.Places
import io.github.thepytales.llmhub.net.ThinkingLevel
import io.github.thepytales.llmhub.ui.tr
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lora in the Server-Terminal (AP-118): turns a wish („Deploy wie immer“, „Zertifikat läuft ab“) and what the terminal
 * showed into steps with command, explanation and safety notes. Prefers the model on this device; a server model only
 * when none is here and Wo Lora arbeitet allows it – then everything goes through [ServerMask] first (no host, IP, user,
 * mail address or secret leaves the device) and the screen says so. Never runs anything itself.
 */
class ServerAssistant(private val c: AppContainer) {
    /** Which model answers and what the screen says about it. */
    data class Where(val model: String?, val onDevice: Boolean, val note: String)

    data class Proposal(val steps: List<ServerStep>, val note: String, val model: String, val sent: String)

    fun where(): Where {
        c.localLlm.preferredModel()?.let { m ->
            return Where(m, true, tr("Lora schlägt mit {0} auf diesem Gerät vor – nichts verlässt den PC.", ModelCatalog.info(m).displayName))
        }
        val s = c.settings.current
        val allowed = s.hasAnyApiKey && Places.effective(c, Area.LORA) != Place.LOCAL
        if (!allowed) return Where(null, false, tr("Für Vorschläge fehlt ein Sprachmodell auf diesem Gerät (Einstellungen › Wo Lora arbeitet). Die festen Abläufe gehen trotzdem."))
        val model = s.loraModel.ifBlank { ModelCatalog.FAST_CHAT }
        return Where(model, false, tr("Kein Modell auf diesem Gerät: Vorschläge kommen vom Server ({0}). Vorher werden Hosts, IP-Adressen, Benutzer, Mail-Adressen und alle Geheimnisse ersetzt.", ModelCatalog.info(model).displayName))
    }

    suspend fun propose(wish: String, profile: SshProfile, recent: String, done: List<ServerStep>, secrets: Collection<String>): Proposal {
        val where = where()
        val model = where.model ?: throw IllegalStateException(where.note)
        // On this device nothing leaves the PC, but secrets still stay out of the model's context.
        val mask = ServerMask(secrets, profile.takeUnless { where.onDevice })
        val sent = request(wish, profile, recent, done, mask)
        val messages = listOf(ChatMessage(role = "system", content = SYSTEM), ChatMessage(role = "user", content = sent))
        val result = withContext(PlaceScope(Area.LORA)) { c.llm.streamChat(model, messages, null, thinking = ThinkingLevel.OFF) {} }
        val (steps, note) = parse(mask.unmask(result.content))
        return Proposal(steps, note, model, sent)
    }

    companion object {
        /** The request as it would leave the device – what [propose] sends (shown under „Was gesendet wird“). */
        fun request(wish: String, profile: SshProfile, recent: String, done: List<ServerStep>, mask: ServerMask): String = mask.mask(buildString {
            append("Wunsch: ").append(wish.trim()).append('\n')
            append("Server: Linux, Benutzer ").append(profile.user).append(", Webserver-Benutzer ").append(profile.webUser.ifBlank { "www-data" })
            if (profile.workDir.isNotBlank()) append(", Anwendung in ").append(profile.workDir)
            append('\n')
            if (done.isNotEmpty()) {
                append("Bisherige Schritte:\n")
                done.takeLast(8).forEach { s -> append("- ").append(s.command).append(" → ").append(s.state.name.lowercase()).append(s.exitCode?.let { " (Exit $it)" } ?: "").append('\n') }
            }
            if (recent.isNotBlank()) append("Letzte Ausgabe:\n").append(recent.takeLast(3000)).append('\n')
        })

        val SYSTEM = """
            Du bist Lora und hilfst, einen Linux-Webserver (Apache, PHP, Symfony, Let's Encrypt) über SSH zu pflegen.
            Schlage höchstens 8 Schritte vor, jeder ein einzelner Shell-Befehl. Erkläre je Schritt in einem Satz auf Deutsch,
            was er tut, und nenne Sicherheitsaspekte (Rechte, Neustart, Datenverlust, sudo). Bevorzuge erst prüfen, dann ändern
            (z. B. apachectl configtest vor reload, certbot --dry-run vor renew). Nie chmod 777, nie Passwörter in Befehle schreiben,
            nichts löschen ohne Not. Platzhalter wie <host-1> oder <geheim> unverändert lassen.
            Antworte nur mit JSON: {"steps":[{"command":"…","what":"…","safety":"…","risk":"read|change|risky"}],"note":"…"}
        """.trimIndent()

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Steps from the model's answer (JSON, also inside ``` fences); the app's own risk and notes are added. */
        fun parse(answer: String): Pair<List<ServerStep>, String> {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) return emptyList<ServerStep>() to answer.trim()
            val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject }.getOrNull()
                ?: return emptyList<ServerStep>() to answer.trim()
            val steps = obj["steps"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val cmd = o["command"]?.jsonPrimitive?.contentOrNull?.trim()?.removePrefix("$ ")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if ('\n' in cmd) return@mapNotNull null
                val lora = when (o["risk"]?.jsonPrimitive?.contentOrNull?.lowercase()) { "risky" -> Risk.RISKY; "change" -> Risk.CHANGE; "read" -> Risk.READ; else -> null }
                val safety = (listOfNotNull(o["safety"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }) + Risks.notes(cmd).map { tr(it) }).distinct().joinToString(" ")
                ServerStep(cmd, o["what"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(), safety).withLoraRisk(lora)
            }.take(8)
            return steps to (obj["note"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty())
        }
    }
}
