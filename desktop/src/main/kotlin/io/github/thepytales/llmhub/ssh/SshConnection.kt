package io.github.thepytales.llmhub.ssh

import io.github.thepytales.llmhub.ui.tr
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannel
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.putty.PuttyKeyUtils
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.security.KeyPair
import java.security.PublicKey
import java.time.Duration
import java.util.EnumSet

/** The server's key is not (yet) trusted – the UI asks (new) or warns (changed); see [KnownHosts]. */
class HostKeyProblem(val result: KnownHosts.Result, val key: PublicKey, val host: String, val port: Int) :
    Exception(if (result is KnownHosts.Result.Changed) tr("Der Schlüssel des Servers hat sich geändert.") else tr("Unbekannter Server-Schlüssel."))

/** The key file is protected and no passphrase was given (or the password is missing). */
class SecretNeeded(message: String) : Exception(message)

/** Login refused (wrong password, key not on the server, user unknown). */
class LoginFailed(message: String) : Exception(message)

/** Result of one command: what it printed and how it ended. */
data class ExecResult(val exitCode: Int?, val output: String, val timedOut: Boolean = false) {
    val ok: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * One SSH connection of the Server-Terminal (AP-118) with Apache MINA SSHD: logs in with a key file (OpenSSH or PuTTY)
 * or a password, accepts the server only when [KnownHosts] knows its key, and offers commands with captured output for
 * Lora's steps ([exec]) and an interactive shell with PTY for the terminal ([shell]). Nothing from `~/.ssh` is used –
 * neither keys nor `config` nor `known_hosts` – so the app does exactly what the saved session says.
 */
class SshConnection private constructor(val profile: SshProfile, private val client: SshClient, val session: ClientSession) : Closeable {
    val isOpen: Boolean get() = session.isOpen

    /**
     * Runs [command] in the application folder of the profile (when set) without a PTY – `sudo` asking for a
     * password then fails at once instead of waiting. [onOutput] gets the output as it arrives.
     */
    fun exec(command: String, timeout: Duration = Duration.ofMinutes(10), onOutput: (String) -> Unit = {}): ExecResult {
        val full = profile.workDir.takeIf { it.isNotBlank() }?.let { "cd ${quote(it)} && $command" } ?: command
        val out = ByteArrayOutputStream()
        val tee = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                synchronized(out) { out.write(b, off, len) }
                onOutput(String(b, off, len, Charsets.UTF_8))
            }
        }
        session.createExecChannel(full).use { ch ->
            // Empty input: a command that reads stdin ends instead of waiting.
            ch.setIn(java.io.ByteArrayInputStream(ByteArray(0)))
            ch.out = tee
            ch.err = tee
            ch.open().verify(Duration.ofSeconds(30))
            val events = ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EXIT_STATUS), timeout)
            val timedOut = ClientChannelEvent.TIMEOUT in events
            if (timedOut) ch.close(true)
            else ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(5))
            return ExecResult(ch.exitStatus, synchronized(out) { out.toString(Charsets.UTF_8) }, timedOut)
        }
    }

    /** An interactive shell with an xterm PTY of [cols]×[rows]; read and write through its inverted streams. */
    fun shell(cols: Int, rows: Int): ChannelShell = session.createShellChannel().apply {
        setPtyType("xterm-256color")
        setPtyColumns(cols)
        setPtyLines(rows)
        setEnv("LANG", "C.UTF-8")
        setRedirectErrorStream(true)
        open().verify(Duration.ofSeconds(30))
    }

    override fun close() {
        runCatching { session.close(true) }
        runCatching { client.stop() }
    }

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(20)

        fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

        /** Key pairs of [file] (OpenSSH, PEM or PuTTY .ppk); [passphrase] for a protected one. */
        fun loadKeys(file: File, passphrase: String?): List<KeyPair> {
            if (!file.isFile) throw SecretNeeded(tr("Schlüsseldatei nicht gefunden: {0}", file.name))
            var asked = false
            val provider = FilePasswordProvider { _, _, _ -> asked = true; passphrase }
            val ppk = file.useLines { it.firstOrNull().orEmpty() }.startsWith("PuTTY-User-Key-File")
            val parser = if (ppk) PuttyKeyUtils.DEFAULT_INSTANCE else SecurityUtils.getKeyPairResourceParser()
            val keys = try {
                parser.loadKeyPairs(null, file.toPath(), provider)?.toList().orEmpty()
            } catch (e: Exception) {
                if (asked && passphrase.isNullOrEmpty()) throw SecretNeeded(tr("Der Schlüssel ist mit einer Passphrase geschützt."))
                if (asked) throw SecretNeeded(tr("Passphrase falsch – der Schlüssel ließ sich nicht öffnen."))
                throw SecretNeeded(tr("Schlüsseldatei nicht lesbar ({0}).", e.message ?: e.javaClass.simpleName))
            }
            if (keys.isEmpty()) throw SecretNeeded(if (asked) tr("Der Schlüssel ist mit einer Passphrase geschützt.") else tr("Kein Schlüssel in der Datei."))
            return keys
        }

        /**
         * Connects and logs in. [secret] is the password ([SshAuth.PASSWORD]) or the passphrase of the key file.
         * Throws [HostKeyProblem] when the server's key is not confirmed yet or changed, [SecretNeeded] or [LoginFailed].
         */
        fun connect(profile: SshProfile, secret: String?, known: KnownHosts, timeout: Duration = TIMEOUT): SshConnection {
            profile.problems().firstOrNull()?.let { throw IllegalArgumentException(tr(it)) }
            val keys = if (profile.auth == SshAuth.KEY) loadKeys(File(profile.keyFile), secret) else emptyList()
            if (profile.auth == SshAuth.PASSWORD && secret.isNullOrEmpty()) throw SecretNeeded(tr("Passwort fehlt."))
            var problem: HostKeyProblem? = null
            val client = SshClient.setUpDefaultClient().apply {
                hostConfigEntryResolver = HostConfigEntryResolver.EMPTY
                keyIdentityProvider = KeyIdentityProvider.EMPTY_KEYS_PROVIDER
                serverKeyVerifier = ServerKeyVerifier { _, _, key ->
                    when (val r = known.check(profile.host, profile.port, key)) {
                        is KnownHosts.Result.Known -> true
                        else -> { problem = HostKeyProblem(r, key, profile.host, profile.port); false }
                    }
                }
                start()
            }
            try {
                val session = client.connect(profile.user, profile.host, profile.port).verify(timeout).session
                try {
                    if (profile.auth == SshAuth.PASSWORD) session.addPasswordIdentity(secret) else keys.forEach { session.addPublicKeyIdentity(it) }
                    session.auth().verify(timeout)
                } catch (e: Exception) {
                    problem?.let { throw it }
                    session.close(true)
                    throw LoginFailed(
                        if (profile.auth == SshAuth.PASSWORD) tr("Anmeldung abgelehnt – Benutzer oder Passwort stimmt nicht.")
                        else tr("Anmeldung abgelehnt – der Schlüssel ist für diesen Benutzer nicht auf dem Server hinterlegt."),
                    )
                }
                return SshConnection(profile, client, session)
            } catch (e: Exception) {
                runCatching { client.stop() }
                problem?.let { throw it }
                if (e is LoginFailed || e is SecretNeeded) throw e
                throw java.io.IOException(tr("Keine Verbindung zu {0} – {1}", "${profile.host}:${profile.port}", e.message ?: e.javaClass.simpleName), e)
            }
        }
    }
}
