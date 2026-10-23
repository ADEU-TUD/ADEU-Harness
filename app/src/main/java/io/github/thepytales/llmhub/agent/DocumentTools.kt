package io.github.thepytales.llmhub.agent

import android.content.Context
import android.util.Base64
import io.github.thepytales.llmhub.data.LogoStore
import io.github.thepytales.llmhub.data.SettingsStore
import io.github.thepytales.llmhub.docs.DocFormat
import io.github.thepytales.llmhub.docs.DocStyle
import io.github.thepytales.llmhub.docs.DocTheme
import io.github.thepytales.llmhub.docs.DocumentWriter
import io.github.thepytales.llmhub.docs.Letter
import io.github.thepytales.llmhub.docs.TudBrand
import io.github.thepytales.llmhub.net.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/** Lets the model create documents (PDF, Word, …) that the user can open, share or save. */
class DocumentTools(
    private val context: Context,
    private val settings: SettingsStore,
    private val logos: LogoStore,
) : ToolProvider {
    private val folder = File(context.filesDir, "documents")

    override val defs = listOf(
        ToolDef(
            "create_document",
            "Erstellt eine Datei (PDF, Word, HTML, Markdown oder Text). Der Nutzer kann sie direkt in der App öffnen, " +
                "teilen oder speichern. content ist entweder Markdown (Überschriften, Listen, Tabellen, Codeblöcke, " +
                "**fett**, *kursiv*, Links, Hinweisboxen mit „> [!NOTE]“, „> [!TIP]“, „> [!WARNING]“) – dann gestaltet " +
                "die App das Dokument nach style, accent_color und logo – oder für PDF/HTML eine vollständige HTML-Seite " +
                "mit eigenem CSS für freie Gestaltung (Farben, Flächen, Formen, Schriften, Spalten, Inline-SVG).",
            listOf(
                Param("title", "Titel des Dokuments (wird auch als Dateiname verwendet)"),
                Param("content", "Vollständiger Inhalt in Markdown oder als HTML-Seite (<!DOCTYPE html>…)"),
                Param("format", "pdf (Standard), docx, html, md oder txt", required = false),
                Param("style", "Gestaltung bei Markdown: modern, kraeftig, elegant, klassisch, schlicht oder tud (Corporate Design der TU Dresden)", required = false),
                Param("accent_color", "Akzentfarbe als #RRGGBB oder Name (z.B. blau, grün, bordeaux)", required = false),
                Param("logo", "Name eines gespeicherten Logos, „standard“ für das Standardlogo oder „kein“", required = false),
                Param("file_name", "Optionaler Dateiname ohne Endung", required = false),
                Param("letter_sender", "Nur für Briefe: Absenderblock, Zeilen mit \\n getrennt (Einrichtung, Name, Telefon, E-Mail). title ist dann der Betreff.", required = false),
                Param("letter_recipient", "Nur für Briefe: Anschrift des Empfängers, Zeilen mit \\n getrennt", required = false),
            ),
        ),
        ToolDef(
            "save_logo",
            "Speichert ein Bild aus der letzten Nachricht des Nutzers als Logo für Dokumente.",
            listOf(
                Param("name", "Name des Logos, z.B. Firmenname"),
                Param("image_index", "Nummer des Bildes in der Nachricht (1 = erstes, Standard)", required = false, type = "integer"),
                Param("make_default", "true, um es als Standardlogo zu verwenden", required = false, type = "boolean"),
            ),
        ),
    )

    override suspend fun run(name: String, args: JsonObject, ctx: RepoContext): ToolOutput = when (name) {
        "create_document" -> create(args)
        "save_logo" -> saveLogo(args, ctx)
        else -> ToolOutput("FEHLER: Unbekanntes Werkzeug '$name'.")
    }

    private fun theme(args: JsonObject): Pair<DocTheme, String?> {
        val s = settings.current
        val style = DocStyle.parse(args.s("style")) ?: DocStyle.parse(s.docStyle) ?: DocStyle.MODERN
        val accent = DocTheme.parseColor(args.s("accent_color")) ?: DocTheme.parseColor(s.docAccent) ?: DocTheme.DEFAULT_ACCENT
        val wanted = args.s("logo")?.trim()
        val noLogo = wanted != null && wanted.lowercase() in setOf("kein", "keins", "none", "nein", "false", "ohne")
        var warning: String? = null
        val logo: ByteArray? = when {
            noLogo -> null
            !wanted.isNullOrEmpty() -> (TudBrand.logo(wanted) ?: logos.find(wanted)?.let { logos.pngBytes(it) }).also {
                if (it == null) warning = "Logo „$wanted“ nicht gefunden – Dokument ohne Logo erstellt."
            }
            // The TU Dresden design always carries its own logo.
            style == DocStyle.TUD -> TudBrand.logoBlue
            s.autoLogo -> logos.default?.let { logos.pngBytes(it) }
            else -> null
        }
        return DocTheme(style, accent, logo, s.docFooter, s.tudUnit.trim()) to warning
    }

    /** Theme from the app settings (used for manual exports). */
    fun settingsTheme(style: DocStyle? = null): DocTheme =
        theme(JsonObject(emptyMap())).first.let { if (style != null) it.copy(style = style) else it }

    private suspend fun create(args: JsonObject): ToolOutput {
        val title = args.s("title")?.trim().orEmpty()
        var content = args.req("content")
        val format = DocFormat.parse(args.s("format"))
        val (theme, warning) = theme(args)
        if (DocumentWriter.isHtml(content)) content = insertLogos(content, theme)
        val sender = args.s("letter_sender")?.replace("\\n", "\n")?.trim().orEmpty()
        val recipient = args.s("letter_recipient")?.replace("\\n", "\n")?.trim().orEmpty()
        val letter = if (sender.isNotEmpty() || recipient.isNotEmpty()) Letter(sender, recipient) else null
        val doc = DocumentWriter.create(context, folder, title, content, format, args.s("file_name"), theme, letter)
        val size = withContext(Dispatchers.IO) { (doc.file.length() + 1023) / 1024 }
        val pages = doc.pages?.let { ", $it ${if (it == 1) "Seite" else "Seiten"}" }.orEmpty()
        val notes = listOfNotNull(
            warning,
            if (!doc.designed) {
                "Hinweis: Das gestaltete PDF ließ sich gerade nicht rendern, deshalb wurde ein einfaches PDF mit dem " +
                    "gleichen Text erstellt. Versuche es nicht erneut; sag dem Nutzer, dass das Layout vereinfacht ist, " +
                    "und biete bei Bedarf dieselbe Gestaltung zusätzlich als format=html an."
            } else null,
        )
        return ToolOutput(
            "Dokument erstellt: ${doc.file.name} (${format.label}$pages, $size KB). " +
                "Es wird dem Nutzer im Chat mit Knöpfen zum Öffnen, Teilen und Speichern angezeigt – nenne keinen Dateipfad." +
                notes.joinToString("") { "\n$it" },
            file = doc.file.absolutePath,
        )
    }

    /** Replaces src="logo" / src="logo:Name" in HTML with the image data. */
    private fun insertLogos(html: String, theme: DocTheme): String = LOGO_SRC.replace(html) { m ->
        val name = m.groupValues[2].ifEmpty { null }
        val builtIn = TudBrand.logo(name)?.let { "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }
        val logo = if (builtIn == null) logos.find(name) else null
        val url = builtIn ?: logo?.let { logos.dataUrl(it) } ?: theme.logoDataUrl
        if (url != null) "src=${m.groupValues[1]}$url${m.groupValues[1]}" else m.value
    }

    private suspend fun saveLogo(args: JsonObject, ctx: RepoContext): ToolOutput {
        if (ctx.images.isEmpty()) return ToolOutput("FEHLER: In der letzten Nachricht des Nutzers ist kein Bild. Bitte ihn, das Logo als Bild anzuhängen.")
        val index = ((args.int("image_index") ?: 1) - 1).coerceIn(0, ctx.images.lastIndex)
        val bytes = Base64.decode(ctx.images[index].substringAfter(","), Base64.DEFAULT)
        val logo = withContext(Dispatchers.IO) { logos.add(args.req("name"), bytes) }
        val makeDefault = args.s("make_default")?.lowercase() == "true"
        if (makeDefault) logos.setDefault(logo.id)
        val isDefault = makeDefault || logos.default?.id == logo.id
        return ToolOutput(
            "Logo „${logo.name}“ gespeichert" + (if (isDefault) " (Standardlogo)." else ".") +
                " Es kann mit logo=\"${logo.name}\" in create_document verwendet werden. Hinweis: Das Bild wurde als JPEG " +
                "übertragen; für transparente Logos kann der Nutzer es unter Einstellungen › Dokumente & Logos direkt hinzufügen."
        )
    }

    /** Instructions with the logos currently available. */
    fun prompt(): String = buildString {
        append("Mit create_document kannst du PDF-, Word-, HTML-, Markdown- oder Textdateien erstellen. Nutze es, wenn der ")
        append("Nutzer ein Dokument, eine Datei oder einen Bericht zum Herunterladen möchte. Für einfache Dokumente reicht Markdown ")
        append("(die App gestaltet es mit style und accent_color). Wünscht der Nutzer eine besondere Gestaltung (Farben, Formen, ")
        append("Flyer, Rechnung, Lebenslauf, Urkunde …), schreibe für format=pdf eine vollständige HTML-Seite mit eigenem CSS ")
        append("(A4: @page { size: A4; margin: 15mm }, keine externen Skripte). ")
        append("Für Dokumente der TU Dresden (Briefe, Protokolle, Formulare, Merkblätter …) nimm Markdown mit style=tud: ")
        append("die App setzt TUD-Blau (${TudBrand.BLUE}), Logo, Einrichtung, Datum und Fußzeile automatisch – das ist schnell ")
        append("und einheitlich; schreibe dafür kein eigenes HTML und wiederhole Logo, Einrichtung oder Datum nicht im Text. ")
        append("Ton: sachlich und förmlich, keine Emojis. Für Briefe letter_sender (Infoblock: Name, Telefon: …, E-Mail: …) und ")
        append("letter_recipient angeben, title ist der Betreff. Formulare: zweispaltige Tabellen „| Bezeichnung | |“ mit leerer ")
        append("rechter Zelle werden zu Eingabefeldern, „- [ ] Option“ zu Ankreuzkästchen, „________“ zu einer Linie. ")
        append("Eingebaute Logos für eigenes HTML: <img src=\"logo:${TudBrand.LOGO_NAME}\"> ")
        append("(blau) und <img src=\"logo:${TudBrand.LOGO_WHITE_NAME}\"> (weiß, für blaue Flächen). ")
        val all = logos.current
        if (all.isEmpty()) {
            append("Es sind keine Logos gespeichert; schickt der Nutzer ein Logo als Bild, kannst du es mit save_logo speichern.")
        } else {
            append("Gespeicherte Logos: ")
            append(all.joinToString(", ") { if (it.isDefault) "${it.name} (Standard)" else it.name })
            append(". Bei Markdown über den Parameter logo einbinden; im HTML mit <img src=\"logo:NAME\"> bzw. <img src=\"logo\"> für das Standardlogo.")
            if (settings.current.autoLogo) append(" Das Standardlogo wird automatisch in jedes Dokument eingefügt; binde es auch in eigenes HTML ein.")
        }
    }

    companion object {
        private val LOGO_SRC = Regex("src\\s*=\\s*([\"'])logo(?::([^\"']*))?\\1", RegexOption.IGNORE_CASE)
    }
}
