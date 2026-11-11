package io.github.thepytales.llmhub.ssh

import io.github.thepytales.llmhub.ui.trLater

/** How much a step can change on the server (AP-118). The higher of Lora's and the app's own assessment counts. */
enum class Risk(val label: String) {
    READ(trLater("liest nur")),
    CHANGE(trLater("ändert")),
    RISKY(trLater("riskant")),
}

/** What happened to a step: open, done (with exit code), skipped or failed. */
enum class StepState { OPEN, RUNNING, DONE, FAILED, SKIPPED }

/**
 * One step Lora proposes (AP-118): the command, what it does, what to watch out for, and how risky it is. Nothing runs
 * without the user's click on *Ausführen* for exactly this step.
 */
data class ServerStep(
    val command: String,
    val what: String,
    val safety: String = "",
    val risk: Risk = Risks.of(command),
    val state: StepState = StepState.OPEN,
    val exitCode: Int? = null,
    val output: String = "",
) {
    /** Lora's risk can only make a step more careful, never less (an `rm -rf` stays riskant). */
    fun withLoraRisk(lora: Risk?): ServerStep = if (lora != null && lora > risk) copy(risk = lora) else this
}

/**
 * The app's own assessment of a command – independent of the model, so a step that deletes, changes rights, restarts a
 * service or runs as another user is never shown as harmless.
 */
object Risks {
    private val risky = listOf(
        Regex("""\brm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)"""), Regex("""\bmkfs\b"""), Regex("""\bdd\s+"""), Regex("""\b(shutdown|reboot|halt|poweroff)\b"""),
        Regex("""\bsystemctl\s+(restart|stop|disable|mask)\b"""), Regex("""\bservice\s+\S+\s+(restart|stop)\b"""), Regex("""\bapachectl\s+(restart|stop|graceful-stop)\b"""),
        Regex("""\bchmod\s+(-R\s+)?[0-7]*7[0-7]{0,2}\b"""), Regex("""\bchmod\s+-R\b"""), Regex("""\bchown\s+-R\b"""),
        Regex("""\bgit\s+(reset\s+--hard|clean\s+-[a-z]*f|push\s+.*--force)"""), Regex("""\b(doctrine:schema:update|doctrine:migrations:migrate|doctrine:database:drop)\b"""),
        Regex("""\bcertbot\s+(revoke|delete)\b"""), Regex("""\b(iptables|ufw|nft)\s+"""), Regex("""\buserdel\b|\bpasswd\b"""), Regex("""\bcurl\b.*\|\s*(sudo\s+)?(ba)?sh\b"""),
        Regex(""">\s*/etc/"""), Regex("""\bcrontab\s+-r\b"""), Regex("""\bapt(-get)?\s+(remove|purge|dist-upgrade|full-upgrade)\b"""),
    )
    private val change = listOf(
        Regex("""\bsudo\b"""), Regex("""\bsu\s"""), Regex("""\b(rm|mv|cp|mkdir|rmdir|ln|touch|tee|truncate|chmod|chown|chgrp|setfacl)\b"""),
        Regex("""\bgit\s+(pull|merge|checkout|switch|stash|reset|rebase|fetch|commit|push|clean)\b"""), Regex("""\bcomposer\s+(install|update|require|remove|dump-autoload)\b"""),
        Regex("""\bnpm\s+(install|ci|run|update)\b"""), Regex("""\bphp\s+bin/console\s+(?!about\b|debug:|lint:|list\b|--version)"""), Regex("""\bsystemctl\s+(reload|start|enable)\b"""),
        Regex("""\b(a2enmod|a2dismod|a2ensite|a2dissite|a2enconf|a2disconf)\b"""), Regex("""\bapachectl\s+(graceful|start)\b"""), Regex("""\bcertbot\s+(renew|certonly|--apache|run)\b(?!.*--dry-run)"""),
        Regex("""\bapt(-get)?\s+(install|upgrade|update)\b"""), Regex("""\bsed\s+-i\b"""), Regex("""(^|[^2&])>{1,2}\s*[^&\s]"""), Regex("""\bcrontab\s+-e\b"""), Regex("""\bkill(all)?\b"""),
    )

    fun of(command: String): Risk {
        val c = command.trim()
        return when {
            risky.any { it.containsMatchIn(c) } -> Risk.RISKY
            change.any { it.containsMatchIn(c) } -> Risk.CHANGE
            else -> Risk.READ
        }
    }

    /** Short notes the app adds to every step that needs them (not left to the model). */
    fun notes(command: String): List<String> = buildList {
        val c = command.trim()
        if (Regex("""\bsudo\b""").containsMatchIn(c)) add(trLater("Läuft mit erhöhten Rechten (sudo) – fragt ggf. nach dem Passwort; dann im Terminal ausführen."))
        if (Regex("""\bsudo\s+-u\s+www-data\b""").containsMatchIn(c)) add(trLater("Läuft als Benutzer des Webservers (www-data), damit Dateien ihm gehören."))
        if (Regex("""\bsystemctl\s+(restart|stop)\b|\bapachectl\s+(restart|stop)\b""").containsMatchIn(c)) add(trLater("Die Webseite ist kurz nicht erreichbar."))
        if (Regex("""\bsystemctl\s+reload\b|\bapachectl\s+graceful\b""").containsMatchIn(c)) add(trLater("Lädt die Einstellungen neu, ohne laufende Verbindungen zu trennen."))
        if (Regex("""\brm\s+-[a-zA-Z]*r""").containsMatchIn(c)) add(trLater("Löscht Ordner samt Inhalt – ohne Papierkorb."))
        if (Regex("""\b(chmod|chown|setfacl)\b""").containsMatchIn(c)) add(trLater("Ändert Rechte – zu weite Rechte (z. B. 777) wären eine Sicherheitslücke."))
        if (Regex("""\bgit\s+pull\b""").containsMatchIn(c)) add(trLater("Holt den neuen Stand; eigene Änderungen auf dem Server können Konflikte machen."))
        if (Regex("""\bcomposer\s+install\b""").containsMatchIn(c)) add(trLater("Lädt Pakete aus dem Internet (Packagist) nach composer.lock."))
        if (Regex("""doctrine:migrations:migrate|doctrine:schema:update""").containsMatchIn(c)) add(trLater("Ändert die Datenbank – vorher eine Sicherung machen."))
    }
}

/** A fixed sequence of steps for a frequent task (AP-118), with {dir} and {webuser} from the session. */
data class ServerFlow(val id: String, val title: String, val description: String, val steps: List<Triple<String, String, String>>) {
    /** The steps for [profile]; [translate] turns the German texts into the interface language. */
    fun steps(profile: SshProfile, translate: (String) -> String = { it }): List<ServerStep> = steps.map { (cmd, what, safety) ->
        val c = cmd.replace("{webuser}", profile.webUser.ifBlank { "www-data" })
        ServerStep(c, translate(what), (listOf(safety).filter { it.isNotBlank() } + Risks.notes(c)).distinct().joinToString(" ") { translate(it) })
    }
}

object ServerFlows {
    val SYMFONY_DEPLOY = ServerFlow(
        "symfony-deploy", trLater("Symfony aktualisieren"),
        trLater("Neuen Stand holen, Pakete installieren, Assets bauen, Rechte für den Webserver setzen, Cache leeren."),
        listOf(
            Triple("git status --short", trLater("Zeigt, ob auf dem Server etwas geändert wurde, bevor der neue Stand kommt."), ""),
            Triple("git pull", trLater("Holt den neuen Stand aus dem Repository."), ""),
            Triple("composer install --no-dev --optimize-autoloader", trLater("Installiert die PHP-Pakete genau nach composer.lock, ohne Entwicklungswerkzeuge."), ""),
            Triple("php bin/console asset-map:compile", trLater("Baut die Assets (CSS, JavaScript, Bilder) nach public/assets/."), ""),
            Triple(
                "setfacl -R -m u:{webuser}:rwX -m d:u:{webuser}:rwX public/assets/ vendor/",
                trLater("Standard-ACL: Der Webserver darf in public/assets/ und vendor/ lesen und schreiben – auch in Dateien, die später entstehen."),
                trLater("Nur diese beiden Ordner, keine Rechte für alle (kein chmod 777)."),
            ),
            Triple("sudo -u {webuser} php bin/console cache:clear", trLater("Leert den Symfony-Cache als Benutzer des Webservers, damit die neuen Cache-Dateien ihm gehören."), ""),
        ),
    )
    val APACHE = ServerFlow(
        "apache", trLater("Apache prüfen und neu laden"),
        trLater("Konfiguration prüfen, erst dann neu laden – die Seite bleibt erreichbar."),
        listOf(
            Triple("apachectl configtest", trLater("Prüft die Konfiguration auf Fehler, ohne etwas zu ändern."), ""),
            Triple("sudo systemctl reload apache2", trLater("Lädt die geprüfte Konfiguration neu."), trLater("Nur ausführen, wenn der Test „Syntax OK“ meldet.")),
            Triple("systemctl status apache2 --no-pager --lines=5", trLater("Zeigt, ob Apache läuft."), ""),
        ),
    )
    val CERTIFICATE = ServerFlow(
        "certificate", trLater("Zertifikat prüfen und erneuern"),
        trLater("Ablaufdaten zeigen, Erneuerung erst testen, dann erneuern (Let’s Encrypt/certbot)."),
        listOf(
            Triple("sudo certbot certificates", trLater("Zeigt alle Zertifikate mit Ablaufdatum."), ""),
            Triple("sudo certbot renew --dry-run", trLater("Probelauf der Erneuerung – ändert nichts."), ""),
            Triple("sudo certbot renew", trLater("Erneuert Zertifikate, die in den nächsten 30 Tagen ablaufen."), trLater("Apache lädt danach über den Hook von certbot neu.")),
        ),
    )
    val SYMFONY_STATUS = ServerFlow(
        "symfony-status", trLater("Symfony-Status"),
        trLater("Umgebung, Version und die letzten Fehler im Log – nur lesen."),
        listOf(
            Triple("php bin/console about", trLater("Zeigt Symfony-Version, Umgebung (prod/dev) und PHP-Version."), ""),
            Triple("git log -1 --oneline", trLater("Welcher Stand läuft."), ""),
            Triple("tail -n 30 var/log/prod.log", trLater("Die letzten Einträge im Log der Anwendung."), trLater("Das Log kann Namen oder E-Mail-Adressen enthalten – geht nur pseudonymisiert an ein Modell.")),
        ),
    )
    val ALL = listOf(SYMFONY_DEPLOY, APACHE, CERTIFICATE, SYMFONY_STATUS)
}
