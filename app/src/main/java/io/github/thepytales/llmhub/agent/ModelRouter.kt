package io.github.thepytales.llmhub.agent

import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.data.ModelCatalog
import java.text.Normalizer

/**
 * Automatic model choice („Automatisch“ in the model picker): which of the recommended models fits a request. Decided
 * on the device from the request itself (no extra request, nothing leaves the device), so it is instant:
 * pictures → the standard model (sees images), programming → Qwen, very long documents → GLM-5.3 Flash, demanding
 * analyses → GLM-5.3, everything else → the standard model. The app asks before switching (see autoModelAsk).
 */
object ModelRouter {
    /** The suggested model and why, in a few words for the question to the user. */
    data class Choice(val model: String, val reason: String)

    private val codeWords = listOf(
        "programm", "quellcode", "code", "skript", "script", "python", "javascript", "typescript", "kotlin", "java ", "c++", "c#",
        "html", "css", "sql", "regex", "regulärer ausdruck", "funktion schreiben", "bug", "fehlermeldung", "stacktrace", "exception",
        "compil", "kompilier", "debugg", "github", "repository", "pull request", "commit", "api-aufruf", "json", "latex-code",
        "excel-makro", "vba", "r-skript", "spss-syntax", "jupyter", "notebook",
    )
    private val hardWords = listOf(
        "beweis", "herleit", "gründlich", "grundlich", "ausführlich analys", "ausfuhrlich analys", "schritt für schritt", "schritt fur schritt",
        "komplex", "kritisch prüf", "kritisch pruf", "gutachten", "forschungsdesign", "methodisch", "systematisch vergleich", "abwägen",
        "abwagen", "argumentation prüf", "logik", "rätsel", "ratsel", "mathemat", "berechne exakt", "statistische auswertung",
        "widerspr", "begründe ausführlich", "strategie", "konzept entwickeln",
    )

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFC)

    /**
     * The model for [text] with [images] and [files]; [github]: the chat works in a repository. Null if nothing
     * speaks for a particular model (then the standard model fits).
     */
    fun suggest(text: String, images: Int, files: List<FileAttachment>, github: Boolean, standard: String = ModelCatalog.DEFAULT_CHAT): Choice {
        val t = norm(text)
        val attached = files.sumOf { it.text.length }
        return when {
            images > 0 || files.any { it.mime.startsWith("image/") } -> Choice(standard, "versteht Bilder")
            github || "```" in text || codeWords.any { it in t } -> Choice(ModelCatalog.CODE_CHAT, "Programmieren und GitHub")
            attached > 300_000 -> Choice(ModelCatalog.FAST_CHAT, "sehr lange Dokumente, am schnellsten")
            hardWords.any { it in t } || (text.length > 1500 && attached == 0) -> Choice(ModelCatalog.HARD_CHAT, "schwierige Aufgabe – gründlich, aber langsamer")
            else -> Choice(standard, "für fast alles, schnell")
        }
    }
}
