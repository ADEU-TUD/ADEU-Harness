package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class TemplateKind(val label: String) {
    /** Becomes the system prompt of a chat ("Übersetzer", "Code-Reviewer" …). */
    PERSONA("Persona (System-Prompt)"),
    /** Text that is inserted into the input field. */
    PROMPT("Prompt-Vorlage"),
}

@Serializable
data class Template(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: TemplateKind,
    val content: String,
    val description: String = "",
)

object DefaultTemplates {
    val all = listOf(
        Template(
            id = "tpl_translator", name = "Übersetzer", kind = TemplateKind.PERSONA,
            description = "Übersetzt jeden Text – Deutsch ↔ Englisch",
            content = "Du bist ein professioneller Übersetzer. Übersetze jeden Text, den der Nutzer schickt: deutsche Texte " +
                "ins Englische, alle anderen ins Deutsche – außer der Nutzer nennt eine andere Zielsprache. Behalte Ton, " +
                "Formatierung und Fachbegriffe bei. Gib nur die Übersetzung aus, ohne Erklärungen.",
        ),
        Template(
            id = "tpl_reviewer", name = "Code-Reviewer", kind = TemplateKind.PERSONA,
            description = "Prüft Code auf Fehler, Sicherheit und Lesbarkeit",
            content = "Du bist ein erfahrener Senior-Softwareentwickler und führst Code-Reviews durch. Prüfe den Code auf " +
                "Fehler, Randfälle, Sicherheitsprobleme, Performance und Lesbarkeit. Gliedere deine Antwort in " +
                "„Kritisch“, „Verbesserungen“ und „Kleinigkeiten“, nenne jeweils die Stelle und zeige konkrete " +
                "Korrekturen als Codeblock. Lobe kurz, was gut gelöst ist.",
        ),
        Template(
            id = "tpl_tutor", name = "Erklärer", kind = TemplateKind.PERSONA,
            description = "Erklärt Themen einfach und Schritt für Schritt",
            content = "Du bist ein geduldiger Tutor. Erkläre Themen verständlich und Schritt für Schritt, mit Beispielen " +
                "aus dem Alltag. Prüfe am Ende mit einer kurzen Frage, ob alles verstanden wurde.",
        ),
        Template(
            id = "tpl_editor", name = "Lektor", kind = TemplateKind.PERSONA,
            description = "Korrigiert Rechtschreibung, Grammatik und Stil",
            content = "Du bist ein sorgfältiger Lektor. Korrigiere Rechtschreibung, Grammatik und Zeichensetzung der Texte " +
                "des Nutzers und verbessere behutsam den Stil. Gib zuerst den korrigierten Text aus und liste danach die " +
                "wichtigsten Änderungen stichpunktartig auf.",
        ),
        Template(
            id = "tpl_summary", name = "Zusammenfassen", kind = TemplateKind.PROMPT,
            description = "Kernaussagen in Stichpunkten",
            content = "Fasse den folgenden Text bzw. die angehängte Datei in höchstens 7 Stichpunkten zusammen und nenne " +
                "am Ende die drei wichtigsten Erkenntnisse:\n\n",
        ),
        Template(
            id = "tpl_mail", name = "E-Mail schreiben", kind = TemplateKind.PROMPT,
            description = "Höfliche, klare E-Mail formulieren",
            content = "Schreibe eine höfliche, klare E-Mail. Empfänger: \nAnliegen: \nTon: freundlich-professionell",
        ),
        Template(
            id = "tpl_explain", name = "Einfach erklären", kind = TemplateKind.PROMPT,
            description = "Wie für Einsteiger erklärt",
            content = "Erkläre mir so einfach wie möglich, mit einem Beispiel: ",
        ),
        Template(
            id = "tpl_proscons", name = "Pro & Contra", kind = TemplateKind.PROMPT,
            description = "Abwägung mit Empfehlung",
            content = "Stelle Vor- und Nachteile in einer Tabelle gegenüber und gib am Ende eine begründete Empfehlung: ",
        ),
    )
}

@Serializable
enum class NetworkAccess(val label: String, val description: String) {
    FULL("Vollständig", "Die Web-Werkzeuge dürfen alle Webseiten aufrufen."),
    CUSTOM("Benutzerdefiniert", "Nur die unten eingetragenen Domains sind erlaubt."),
    NONE("Keine", "Keine Web-Werkzeuge – das Modell kann nichts aus dem Internet abrufen."),
}

@Serializable
data class EnvVar(
    val name: String,
    val value: String,
    /** Secret values are never shown to the model; it only uses them as ${NAME}. */
    val secret: Boolean = false,
)

/**
 * Configuration for agent runs, similar to cloud environments of coding agents: variables the agent
 * can use, notes for the model and which web hosts its tools may reach.
 */
@Serializable
data class AgentEnvironment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val network: NetworkAccess = NetworkAccess.FULL,
    val allowedDomains: List<String> = emptyList(),
    val variables: List<EnvVar> = emptyList(),
    /** Extra instructions for the model in this environment. */
    val notes: String = "",
) {
    fun allowsHost(host: String): Boolean = when (network) {
        NetworkAccess.FULL -> true
        NetworkAccess.NONE -> false
        NetworkAccess.CUSTOM -> {
            val h = host.lowercase().trimEnd('.')
            allowedDomains.any { d ->
                val dom = d.trim().lowercase().removePrefix("*.").removePrefix("https://").removePrefix("http://").trimEnd('/')
                dom.isNotEmpty() && (h == dom || h.endsWith(".$dom"))
            }
        }
    }

    /** Replaces ${NAME} with the variable values. */
    fun substitute(text: String): String {
        if (variables.isEmpty() || "\${" !in text) return text
        return VAR_REF.replace(text) { m -> variables.firstOrNull { it.name == m.groupValues[1] }?.value ?: m.value }
    }

    /** Hides secret values in text that is shown to the model or the user. */
    fun mask(text: String): String = variables.filter { it.secret && it.value.length >= 4 }
        .fold(text) { acc, v -> acc.replace(v.value, "\${${v.name}}") }

    fun systemPrompt(): String = buildString {
        append("Arbeitsumgebung „$name“.")
        if (notes.isNotBlank()) append("\nHinweise: ").append(notes.trim())
        if (variables.isNotEmpty()) {
            append("\nUmgebungsvariablen (in den Argumenten von fetch_url als \${NAME} nutzbar, die App setzt den Wert ein):")
            variables.forEach { v ->
                append("\n- ${v.name}")
                append(if (v.secret) " (geheim – Wert nicht sichtbar, nur als \${${v.name}} verwenden)" else " = ${v.value}")
            }
        }
        when (network) {
            NetworkAccess.FULL -> {}
            NetworkAccess.NONE -> append("\nKein Internetzugriff in dieser Umgebung.")
            NetworkAccess.CUSTOM -> append("\nInternetzugriff nur auf: ${allowedDomains.joinToString(", ")}.")
        }
    }

    companion object {
        // Android's regex engine (ICU) needs the closing brace escaped as well.
        val VAR_REF = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}")
        val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        /** Parses lines like NAME=value (as in .env files). */
        fun parseVariables(text: String, secret: Boolean = false): List<EnvVar> = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .mapNotNull { line ->
                val name = line.substringBefore('=').trim().removePrefix("export ").trim()
                val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                if (NAME.matches(name)) EnvVar(name, value, secret) else null
            }
    }
}

/** A logo the user saved for documents. */
@Serializable
data class Logo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Absolute path of the PNG file. */
    val path: String,
    val isDefault: Boolean = false,
)
