package io.github.thepytales.llmhub.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A learning game (AP-17): a self-contained browser game (one HTML file with its CSS and JavaScript) – quiz, matching,
 * memory, escape room … – that the agent built from a script, slides or a description. Every change is a new
 * [versions] number, so earlier versions stay.
 */
@Serializable
data class LearningGame(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    /** Kind of game: „Quiz“, „Zuordnen“, „Memory“, „Escape-Room“, „Lückentext“ … */
    val kind: String = "",
    /** One sentence: topic and target group. */
    val description: String = "",
    val versions: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** The chat it was built in (to change it there). */
    val chatId: String? = null,
)

/**
 * The collection of learning games (Lernspiele › Sammlung): `lernspiele/<id>/v<n>.html` plus an index. Games run
 * walled off ([sandboxed]): a Content-Security-Policy forbids every request to the network and to other files, so a
 * game can neither fetch app data nor send anything away; it only runs its own inline script.
 */
class LearningGameStore(files: File) {
    private val dir = File(files, "lernspiele").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<LearningGame>> = _items.asStateFlow()

    private fun load(): List<LearningGame> = runCatching {
        json.decodeFromString(ListSerializer(LearningGame.serializer()), AtomicFiles.read(indexFile) ?: return emptyList())
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

    @Synchronized
    private fun persist(list: List<LearningGame>) {
        _items.value = list.sortedByDescending { it.updatedAt }
        AtomicFiles.write(indexFile, json.encodeToString(ListSerializer(LearningGame.serializer()), _items.value))
    }

    fun get(id: String): LearningGame? = _items.value.firstOrNull { it.id == id }

    /** Saves a new game. */
    @Synchronized
    fun create(title: String, kind: String, description: String, html: String, chatId: String?): LearningGame {
        val game = LearningGame(title = title.trim().ifBlank { "Lernspiel" }, kind = kind.trim(), description = description.trim(), chatId = chatId)
        versionFile(game.id, 1).apply { parentFile.mkdirs() }.writeText(html)
        persist(_items.value + game)
        return game
    }

    /** Saves [html] as the next version of game [id] (title and kind change only when given). */
    @Synchronized
    fun update(id: String, html: String, title: String? = null, kind: String? = null): LearningGame? {
        val old = get(id) ?: return null
        val next = old.copy(
            versions = old.versions + 1, updatedAt = System.currentTimeMillis(),
            title = title?.trim()?.ifBlank { null } ?: old.title, kind = kind?.trim()?.ifBlank { null } ?: old.kind,
        )
        versionFile(id, next.versions).apply { parentFile.mkdirs() }.writeText(html)
        persist(_items.value.map { if (it.id == id) next else it })
        return next
    }

    /** The HTML of [version] (default: the latest) as the agent wrote it. */
    fun html(id: String, version: Int? = null): String? {
        val game = get(id) ?: return null
        return versionFile(id, version ?: game.versions).takeIf { it.isFile }?.readText()
    }

    fun delete(id: String) {
        File(dir, id).deleteRecursively()
        persist(_items.value.filter { it.id != id })
    }

    private fun versionFile(id: String, version: Int) = File(File(dir, id), "v$version.html")

    /** The game walled off, written to [out] (an .html file) – to play it or to upload it to OPAL/Moodle. */
    fun writePlayable(id: String, out: File, version: Int? = null): File? {
        val html = html(id, version) ?: return null
        out.parentFile?.mkdirs()
        out.writeText(sandboxed(html))
        return out
    }

    /**
     * The game as a ZIP for OPAL („HTML-Seite“ / „Lernpaket“) and Moodle („Datei“/„Textseite“): index.html (walled
     * off) and a short note on how to use it.
     */
    fun writeZip(id: String, out: File, version: Int? = null): File? {
        val game = get(id) ?: return null
        val html = html(id, version) ?: return null
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("index.html"))
            zip.write(sandboxed(html).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("LIESMICH.txt"))
            zip.write(
                ("${game.title}${if (game.kind.isNotBlank()) " (${game.kind})" else ""}\n\n" +
                    "Ein eigenständiges Lernspiel: index.html im Browser öffnen – es braucht kein Internet und lädt nichts nach.\n" +
                    "OPAL: Kursbaustein „Einzelne Seite“ oder „Ressourcenordner“, ZIP hochladen und index.html als Startseite wählen.\n" +
                    "Moodle: Material „Datei“ (index.html) oder „Verzeichnis“ (ZIP entpacken).\n\nErstellt mit Lora.\n").toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()
        }
        return out
    }

    /** A file name for exports: „Zuordnen Kapitel 3.html“. */
    fun fileName(game: LearningGame, extension: String): String =
        game.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(60).ifBlank { "Lernspiel" } + ".$extension"

    companion object {
        /** No requests to anything outside the file: no network, no other files, no frames; only its own inline code. */
        const val POLICY = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; " +
            "media-src data: blob:; font-src data:; connect-src 'none'; form-action 'none'; frame-src 'none'; base-uri 'none'"

        /** Largest game the agent may save (a single HTML file). */
        const val MAX_CHARS = 400_000

        /** [html] with the [POLICY] as the first thing in its head (added when the game had none of its own). */
        fun sandboxed(html: String): String {
            val meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"$POLICY\">"
            val withoutOwn = html.replace(Regex("(?i)<meta[^>]+http-equiv=[\"']?content-security-policy[^>]*>"), "")
            val head = Regex("(?i)<head[^>]*>").find(withoutOwn)
            return when {
                head != null -> withoutOwn.substring(0, head.range.last + 1) + meta + withoutOwn.substring(head.range.last + 1)
                Regex("(?i)<html[^>]*>").containsMatchIn(withoutOwn) -> withoutOwn.replaceFirst(Regex("(?i)(<html[^>]*>)"), "$1<head>$meta</head>")
                else -> "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"utf-8\">$meta</head><body>$withoutOwn</body></html>"
            }
        }

        /** What is wrong with [html] as a game, or null: it must be one complete page that loads nothing from outside. */
        fun problem(html: String): String? {
            val h = html.trim()
            return when {
                h.length < 200 -> "Das HTML ist zu kurz – übergib das vollständige Spiel (eine Datei mit CSS und JavaScript)."
                h.length > MAX_CHARS -> "Das Spiel ist zu groß (${h.length / 1000} KB, höchstens ${MAX_CHARS / 1000} KB). Kürze Texte oder Grafiken."
                !Regex("(?i)<(html|body)").containsMatchIn(h) -> "Es fehlt <html> bzw. <body> – übergib eine vollständige HTML-Seite."
                Regex("(?i)<script[^>]+src\\s*=").containsMatchIn(h) -> "Externe Skripte (<script src=…>) sind nicht erlaubt – schreibe das JavaScript direkt in die Datei."
                Regex("(?i)<link[^>]+href\\s*=\\s*[\"']?https?:").containsMatchIn(h) -> "Externe Stylesheets oder Schriften sind nicht erlaubt – CSS direkt in die Datei."
                else -> null
            }
        }
    }
}
