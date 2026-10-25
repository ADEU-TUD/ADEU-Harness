package io.github.thepytales.llmhub.net

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Errors of requests in words a user can act on (shown in the chat, in banners and with transcriptions). */
object NetErrors {
    const val OFFLINE =
        "Keine Internetverbindung – der Server ist nicht erreichbar. Bitte WLAN oder mobile Daten prüfen. " +
            "Ohne Netz antworten Modelle auf diesem Gerät (Einstellungen › Modelle › Modelle auf diesem Gerät)."

    fun describe(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "HTTP ${e.code}"
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> OFFLINE
        is SocketTimeoutException ->
            "Der Server hat zu lange nicht geantwortet (Zeitüberschreitung). Bitte erneut versuchen – bei sehr langen Texten " +
                "hilft ein schnelleres Modell wie GLM-5.3 Flash."
        is SSLException ->
            "Die sichere Verbindung ist fehlgeschlagen. Stimmt die Uhrzeit des Geräts? In WLANs mit Anmeldeseite (Hotel, " +
                "Bahn) zuerst im Browser anmelden."
        is InterruptedIOException -> e.message ?: "Abgebrochen."
        else -> e.message ?: e.javaClass.simpleName
    }
}
