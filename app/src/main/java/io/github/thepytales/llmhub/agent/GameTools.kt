package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.LearningGameStore
import io.github.thepytales.llmhub.net.s
import kotlinx.serialization.json.JsonObject

/**
 * Lernspiele (AP-17): the agent saves a self-contained browser game (one HTML file) in the collection, reads it back to
 * change it („mach Level 2 schwerer“ – every change is a new version) and lists the games. Offered while the tool
 * „Lernspiele“ is switched on (Einstellungen › Werkzeuge).
 */
class GameTools(private val c: AppContainer) : ToolProvider {
    override val defs = listOf(
        ToolDef(
            CREATE,
            "Speichert ein neues Lernspiel (eigenständiges Browserspiel: EINE HTML-Datei mit CSS und JavaScript, ohne externe Dateien " +
                "und ohne Netzwerk) in der Sammlung „Lernspiele“. Danach kann es in der App gespielt und als HTML/ZIP für OPAL exportiert werden.",
            listOf(
                Param("title", "Titel, z. B. „Zuordnen: Lerntheorien (Kapitel 3)“"),
                Param("kind", "Art: Quiz, Zuordnen, Memory, Escape-Room, Lückentext, Sortieren …"),
                Param("description", "Ein Satz: Thema und Zielgruppe", required = false),
                Param("html", "Das vollständige Spiel als eine HTML-Datei (<!DOCTYPE html> … </html>)"),
            ),
            writes = true,
        ),
        ToolDef(
            UPDATE,
            "Speichert eine geänderte Fassung eines Lernspiels als neue Version (frühere bleiben erhalten). Vorher mit $READ den aktuellen " +
                "Stand holen und das vollständige neue HTML übergeben.",
            listOf(
                Param("id", "Spiel-ID"),
                Param("html", "Das vollständige geänderte Spiel als eine HTML-Datei"),
                Param("title", "Neuer Titel, falls er sich ändert", required = false),
            ),
            writes = true,
        ),
        ToolDef(
            READ,
            "Liest ein Lernspiel aus der Sammlung (HTML der neuesten Version), um es zu ändern. Ohne id: Liste aller Lernspiele.",
            listOf(Param("id", "Spiel-ID (leer: Liste)", required = false)),
        ),
    )

    override fun parallelSafe(name: String) = name == READ

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        CREATE -> {
            val html = args.req("html")
            LearningGameStore.problem(html)?.let { ToolOutput("FEHLER: $it") } ?: run {
                val g = c.games.create(args.req("title"), args.s("kind").orEmpty(), args.s("description").orEmpty(), html, ctx.chatId)
                ToolOutput("Lernspiel „${g.title}“ gespeichert. $ID_LABEL ${g.id} · Version 1. Es lässt sich jetzt in der App spielen und als HTML/ZIP für OPAL exportieren.")
            }
        }
        UPDATE -> {
            val html = args.req("html")
            val id = args.req("id").trim()
            LearningGameStore.problem(html)?.let { ToolOutput("FEHLER: $it") }
                ?: c.games.update(id, html, title = args.s("title"))?.let { g ->
                    ToolOutput("Lernspiel „${g.title}“ geändert. $ID_LABEL ${g.id} · Version ${g.versions} (frühere Versionen bleiben in der Sammlung).")
                }
                ?: ToolOutput("FEHLER: Kein Lernspiel mit der ID $id. Mit $READ ohne id die Liste holen.")
        }
        else -> {
            val id = args.s("id")?.trim().orEmpty()
            if (id.isEmpty()) {
                val all = c.games.items.value
                ToolOutput(if (all.isEmpty()) "Noch keine Lernspiele." else all.joinToString("\n") { "- ${it.title} (${it.kind.ifBlank { "Spiel" }}, Version ${it.versions}) – $ID_LABEL ${it.id}" })
            } else {
                val g = c.games.get(id)
                val html = c.games.html(id)
                if (g == null || html == null) ToolOutput("FEHLER: Kein Lernspiel mit der ID $id.")
                else ToolOutput("Lernspiel „${g.title}“ (${g.kind}), Version ${g.versions}:\n\n$html".take(MAX_TOOL_RESULT))
            }
        }
    }

    companion object {
        const val CREATE = "create_learning_game"
        const val UPDATE = "update_learning_game"
        const val READ = "read_learning_game"

        /** Marks the id in a tool result (the chat shows a card with „Spielen“ for it). */
        const val ID_LABEL = "Spiel-ID:"

        /** The game id in a result of [CREATE] or [UPDATE]. */
        fun idIn(result: String): String? = Regex(Regex.escape(ID_LABEL) + "\\s*([0-9a-f-]{8,})").find(result)?.groupValues?.get(1)

        /** Lernspiel mode: how a good game is built. */
        const val MODE_PROMPT = "LERNSPIEL-MODUS (AP-17): Du baust Lernspiele für die Hochschullehre als eigenständige Browserspiele.\n" +
            "1. Kläre kurz (höchstens eine Rückfrage mit ask_user), falls Thema, Zielgruppe oder Spielart fehlen. Nutze angehängte Skripte, " +
            "Folien oder Texte als Inhalt – fachlich korrekt, nichts erfinden.\n" +
            "2. Baue EINE vollständige HTML-Datei: <!DOCTYPE html>, <meta charset=\"utf-8\">, <meta name=\"viewport\" …>, CSS in <style>, " +
            "JavaScript in <script>. KEINE externen Dateien, Bibliotheken, Schriften, Bilder aus dem Netz und KEINE Netzwerkanfragen " +
            "(fetch, XHR) – das Spiel läuft abgeschottet und offline (auch in OPAL). Grafiken als SVG oder CSS.\n" +
            "3. Gut spielbar: Titel und kurze Anleitung, klare Rückmeldung je Antwort (richtig/falsch mit kurzer Erklärung), Punktestand " +
            "oder Fortschritt, Ende mit Auswertung und „Nochmal spielen“. Barrierearm: gut lesbare Schrift, Kontrast, mit Tastatur " +
            "bedienbar, funktioniert auf Handy und PC. Deutsche Texte, sachlicher Ton.\n" +
            "4. Speichere mit $CREATE. Für Änderungen („mach Level 2 schwerer“) erst $READ, dann $UPDATE mit dem vollständigen HTML.\n" +
            "5. Antworte danach kurz: was das Spiel enthält, und dass es über „Spielen“ getestet und für OPAL exportiert werden kann. " +
            "Gib das HTML nicht zusätzlich im Chat aus."
    }
}
