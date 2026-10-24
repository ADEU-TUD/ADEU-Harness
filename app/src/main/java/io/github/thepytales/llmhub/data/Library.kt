package io.github.thepytales.llmhub.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** A folder of chats; with instructions or files it is a project whose chats share them. */
@Serializable
data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Project instructions: sent to the model in every chat of the folder. */
    val instructions: String = "",
)

@Serializable
enum class TemplateKind(val label: String) {
    /** Becomes the system prompt of a chat ("Übersetzer", "Code-Reviewer" …). */
    PERSONA("Persona (System-Prompt)"),
    /** Text that is inserted into the input field. */
    PROMPT("Prompt-Vorlage"),
}

/** Sections of the template library, in the order they are shown. */
enum class TemplateCategory(val label: String, val description: String) {
    TUD("TUD-Dokumente", "Briefe, Protokolle, Formulare und Bescheinigungen als PDF im Corporate Design der TU Dresden"),
    TEACHING("Lehre", "Seminare und Sitzungen planen, Lernziele, Prüfungsaufgaben, Feedback"),
    THESES("Betreuung & Prüfung", "Abschlussarbeiten, Exposés, Gutachten, mündliche Prüfungen"),
    RESEARCH("Forschung", "Fragestellung, Instrumente, Auswertung, Publikation und Anträge"),
    COMMUNICATION("Kommunikation", "E-Mails, Antworten an Studierende, Ankündigungen"),
    GENERAL("Allgemein", "Zusammenfassen, überarbeiten, übersetzen, erklären"),
    PERSONA("Personas", "Legen fest, wie sich das Modell im ganzen Chat verhält"),
    OWN("Eigene", "Selbst angelegte oder importierte Vorlagen"),
    ;

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name }
    }
}

@Serializable
data class Template(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: TemplateKind,
    /** Persona: the system prompt. Prompt: text for the input field, may contain {{Feld}} placeholders. */
    val content: String,
    val description: String = "",
    /** Section in the library: enum name of [TemplateCategory] (own prompts default to "Eigene"). */
    val category: String = "",
    /** Instructions for the model that are sent along with the message but not shown in the chat. */
    val instructions: String = "",
    /**
     * Document the app builds from the fields itself, without the model ("letter"): what the user typed is
     * taken over as written. Follow-up requests in the chat go to the model as usual.
     */
    val direct: String? = null,
) {
    val builtIn: Boolean get() = id.startsWith(TemplateLibrary.BUILT_IN)

    val section: TemplateCategory
        get() = if (kind == TemplateKind.PERSONA) TemplateCategory.PERSONA
        else TemplateCategory.parse(category)?.takeIf { it != TemplateCategory.PERSONA } ?: TemplateCategory.OWN

    val fields: List<TemplateField> get() = TemplateFields.of(content)
}

/** A field to fill in: {{Bezeichnung}} or {{Bezeichnung|Hinweis}} in the text of a prompt template. */
data class TemplateField(val label: String, val hint: String) {
    /** Longer texts get a bigger input box. */
    val multiline: Boolean get() = MULTILINE.containsMatchIn(label) || hint.contains("Stichpunkte")
    /** Address fields: one line per part, suggestions from the address book. */
    val isAddress: Boolean get() = label.startsWith("Empfänger") || label.startsWith("Anschrift")
    val isSender: Boolean get() = label.startsWith("Absender") || label.startsWith("Meine Angaben")

    private companion object {
        val MULTILINE = Regex("Inhalt|text|Text|Notizen|Tagesordnung|Angaben|Absender|Empfänger|Anschrift|Material|Ergebnisse|Stichpunkte|Beschreibung", RegexOption.IGNORE_CASE)
    }
}

/** Fields of a template form that were typed but not sent yet; they survive leaving the app. */
@Serializable
data class TemplateDraft(
    val templateId: String,
    val values: Map<String, String> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** An address used in a letter; offered again in the address fields of templates. */
@Serializable
data class Contact(
    val id: String = UUID.randomUUID().toString(),
    /** One line per part of the address. */
    val address: String,
    val lastUsed: Long = System.currentTimeMillis(),
) {
    val label: String get() = address.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

object TemplateFields {
    // Android's regex engine (ICU) needs the braces escaped, also inside character classes.
    private val PLACEHOLDER = Regex("\\{\\{([^\\{\\}|]+)(?:\\|([^\\{\\}]*))?\\}\\}")

    fun of(text: String): List<TemplateField> = PLACEHOLDER.findAll(text)
        .map { TemplateField(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        .distinctBy { it.label }
        .toList()

    /** Inserts the values; a line whose fields all stayed empty is left out completely. */
    fun fill(text: String, values: Map<String, String>): String = text.lines().mapNotNull { line ->
        val found = PLACEHOLDER.findAll(line).toList()
        when {
            found.isEmpty() -> line
            found.all { values[it.groupValues[1].trim()].isNullOrBlank() } -> null
            else -> PLACEHOLDER.replace(line) { m -> values[m.groupValues[1].trim()]?.trim().orEmpty() }
        }
    }.joinToString("\n").trim()
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
