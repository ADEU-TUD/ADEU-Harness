package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.net.ApiException
import io.github.thepytales.llmhub.net.NetErrors
import io.github.thepytales.llmhub.ui.ErrorHelp
import io.github.thepytales.llmhub.ui.ErrorHelp.Fix
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fehlermeldungen in Klartext (AP-74): the 30 most frequent errors (from the error log, the tests and the HTTP codes of
 * the Uni-Server) each get a sentence and a button, and no raw exception reaches the chat or Lora's panel.
 */
class ErrorHelpTest {
    /** Error → expected catalog id and button (null = no button needed). */
    private val frequent: List<Triple<Any, String, Fix?>> = listOf(
        Triple(java.net.UnknownHostException("llm.scads.ai"), "offline", Fix.LOCAL_MODELS),
        Triple(java.net.ConnectException("Failed to connect to llm.scads.ai/141.30.1.1:443"), "offline", Fix.LOCAL_MODELS),
        Triple(java.net.SocketTimeoutException("timeout"), "zeitueberschreitung", Fix.RETRY),
        Triple(javax.net.ssl.SSLHandshakeException("PKIX path building failed"), "ssl", Fix.RETRY),
        Triple(java.io.IOException("unexpected end of stream on https://llm.scads.ai/..."), "verbindung-abgebrochen", Fix.RETRY),
        Triple(ApiException(401, "HTTP 401 (API-Key ungültig …): Invalid API key"), "schluessel-ungueltig", Fix.CHECK_KEY),
        Triple(ApiException(401, "Kein API-Key hinterlegt. Bitte in den Einstellungen eintragen."), "kein-schluessel", Fix.CHECK_KEY),
        Triple(ApiException(403, "HTTP 403: model access denied"), "kein-zugriff-modell", Fix.CHANGE_MODEL),
        Triple(ApiException(404, "HTTP 404: The model `foo` does not exist"), "modell-weg", Fix.CHANGE_MODEL),
        Triple(ApiException(400, "HTTP 400: This model's maximum context length is 131072 tokens"), "chat-zu-lang", Fix.NEW_CHAT),
        Triple(ApiException(400, "HTTP 400: \"auto\" tool choice requires --enable-auto-tool-choice"), "werkzeuge-nicht-unterstuetzt", Fix.CHANGE_MODEL),
        Triple(ApiException(413, "HTTP 413: Request Entity Too Large"), "anfrage-zu-gross", null),
        Triple(ApiException(429, "HTTP 429: rate limit exceeded"), "zu-viele-anfragen", Fix.RETRY),
        Triple(ApiException(502, "HTTP 502: Bad Gateway"), "server-ueberlastet", Fix.CHANGE_MODEL),
        Triple(ApiException(503, "HTTP 503: Service Unavailable"), "server-ueberlastet", Fix.CHANGE_MODEL),
        Triple(ApiException(500, "HTTP 500: Internal Server Error"), "server-fehler", Fix.RETRY),
        Triple(java.io.IOException("No space left on device"), "speicher-voll", Fix.FREE_SPACE),
        Triple(OutOfMemoryError("Java heap space"), "arbeitsspeicher", Fix.MEMORY),
        Triple(java.nio.file.FileSystemException("C:\\x\\Bericht.docx: The process cannot access the file because it is being used by another process"), "datei-gesperrt", Fix.RETRY),
        Triple(java.nio.file.AccessDeniedException("C:\\Windows\\x.txt"), "kein-zugriff-datei", null),
        Triple(java.io.FileNotFoundException("/home/x/Bericht.pdf (No such file or directory)"), "datei-fehlt", null),
        Triple(java.util.zip.ZipException("zip END header not found"), "datei-kaputt", Fix.REPORT),
        Triple(IllegalStateException("Dieses Werkzeug ist ausgeschaltet – Einstellungen › Werkzeuge."), "werkzeug-aus", Fix.ENABLE_TOOL),
        Triple(IllegalStateException("Kein Modell auf diesem Gerät geladen."), "lokales-modell-fehlt", Fix.LOCAL_MODELS),
        Triple(IllegalStateException("Für das Diktat auf dem Gerät unter Einstellungen › Transkription ein Erkennungsmodell laden."), "spracherkennung-fehlt", Fix.TRANSCRIPTION),
        Triple(IllegalStateException("ffmpeg fehlt in der App."), "ffmpeg-fehlt", Fix.UPDATES),
        Triple(ApiException(401, "Kein GitHub-Token hinterlegt (Einstellungen)."), "github-token", Fix.GITHUB),
        Triple(IllegalStateException("Nextcloud ist noch nicht eingerichtet (Einstellungen › Verbindungen › Nextcloud)."), "verbindung-einrichten", Fix.CONNECTIONS),
        Triple("Ins Postfach darf nur ein Modell auf diesem Gerät.", "postfach-lokal", Fix.LOCAL_MODELS),
        Triple(NullPointerException(), "unbekannt", Fix.REPORT),
    )

    @Test
    fun theThirtyMostFrequentErrorsHavePlainWordsAndAButton() {
        assertTrue(frequent.size >= 30)
        for ((error, id, fix) in frequent) {
            val plain = if (error is Throwable) ErrorHelp.explain(error) else ErrorHelp.explain(error as String)
            assertEquals(id, plain.id, "Falsche Einordnung für $error: ${plain.text}")
            assertEquals(fix, plain.fix, "Falscher Knopf für $error")
            assertTrue(ErrorHelp.readable(plain.text), "Kein Klartext für $error: ${plain.text}")
        }
    }

    @Test
    fun storedChatErrorsAreExplainedToo() {
        // What AgentRunner stored in older chats (NetErrors.describe) is read back from the text alone.
        val stored = NetErrors.describe(ApiException(503, "HTTP 503 (Der Uni-Server erreicht das Modell gerade nicht …): upstream"))
        assertEquals("server-ueberlastet", ErrorHelp.explain(stored).id)
        assertEquals("offline", ErrorHelp.explain(NetErrors.OFFLINE).id)
        assertEquals("zeitueberschreitung", ErrorHelp.explain(NetErrors.describe(java.net.SocketTimeoutException())).id)
    }

    @Test
    fun ownSentencesStayAndTechnicalTailsFoldAway() {
        val own = ErrorHelp.explain("Diese Mail gibt es in Outlook nicht mehr (verschoben oder gelöscht).")
        assertEquals("app", own.id)
        assertEquals("Diese Mail gibt es in Outlook nicht mehr (verschoben oder gelöscht).", own.text)
        val mixed = ErrorHelp.explain("„Bericht.pdf“ ließ sich nicht öffnen: java.lang.IllegalArgumentException: bad xref")
        assertEquals("„Bericht.pdf“ ließ sich nicht öffnen.", mixed.text)
        assertNotNull(mixed.technical)
        assertTrue("IllegalArgument" in mixed.technical!!)
    }

    @Test
    fun noRawExceptionReachesThePeople() {
        val raw = listOf(
            IllegalStateException(), IllegalArgumentException("index -1"), RuntimeException(java.io.IOException("Stream closed")),
            ClassCastException("class kotlinx.serialization.json.JsonNull cannot be cast"), NumberFormatException("For input string: \"x\""),
            IndexOutOfBoundsException("Index 3 out of bounds for length 2"), UnsupportedOperationException(),
        )
        for (e in raw) {
            val plain = ErrorHelp.explain(e)
            assertTrue(ErrorHelp.readable(plain.text), "${e.javaClass.simpleName} → ${plain.text}")
            assertFalse("Exception" in plain.text || "java." in plain.text || "kotlin." in plain.text, plain.text)
        }
    }

    @Test
    fun chatAndLoraPanelGoThroughTheCatalog() {
        val ui = File("../app/src/main/java/io/github/thepytales/llmhub/ui")
        val chat = File(ui, "ChatMessages.kt").readText()
        val card = chat.substringAfter("fun ErrorCard(").substringBefore("\n}\n")
        assertTrue("ErrorHelp.explain(text)" in card && "Text(plain.text" in card, "Die Fehlerkarte im Chat zeigt den Klartext")
        val lora = File(ui, "LoraHelper.kt").readText()
        assertFalse(Regex(""""failed:" \+ \(?e\.message""").containsMatchIn(lora), "Loras Karten zeigen keine rohe Exception")
        assertFalse(Regex("""getOrElse \{ tr\("Rückgängig ging nicht: \{0}", it\.message""").containsMatchIn(lora))
        assertTrue("ErrorHelp.explain(e)" in lora)
        val notices = File(ui, "Notices.kt").readText()
        assertTrue("ErrorHelp.explain(text)" in notices, "Hinweisleisten gehen über den Katalog")
    }
}
