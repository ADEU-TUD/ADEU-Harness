package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.ssh.HostKeyProblem
import io.github.thepytales.llmhub.ssh.KnownHosts
import io.github.thepytales.llmhub.ssh.Risk
import io.github.thepytales.llmhub.ssh.Risks
import io.github.thepytales.llmhub.ssh.ServerAssistant
import io.github.thepytales.llmhub.ssh.ServerFlows
import io.github.thepytales.llmhub.ssh.ServerMask
import io.github.thepytales.llmhub.ssh.ServerStep
import io.github.thepytales.llmhub.ssh.ServerTerminal
import io.github.thepytales.llmhub.ssh.SshAuth
import io.github.thepytales.llmhub.ssh.SshConnection
import io.github.thepytales.llmhub.ssh.SshProfile
import io.github.thepytales.llmhub.ssh.SshTty
import io.github.thepytales.llmhub.ssh.StepState
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Collections
import java.util.zip.ZipFile
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Server-Terminal (AP-118) against an SSH server embedded in the test (Apache MINA SSHD): login with password, with an
 * ed25519 key behind a passphrase and with a PuTTY key; the server key is asked for when new and blocked when changed;
 * the flow „Symfony aktualisieren“ runs step by step, only what was approved; secrets never reach the protocol or a
 * model request; the terminal gets a PTY. No real server, address or account anywhere.
 */
class ServerTerminalTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-ssh").toFile()
    private val user = "deploy"
    private val password = "Geheim-123!"
    private val passphrase = "Pass-Phrase-9"
    private val hostKey: KeyPair = KeyUtils.generateKeyPair(KeyPairProvider.ECDSA_SHA2_NISTP256, 256)
    private val clientKey: KeyPair = KeyUtils.generateKeyPair(KeyPairProvider.SSH_ED25519, 256)
    private val rsaKey: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    /** Commands the server received, in order. */
    private val received: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var ptyType: String? = null

    private val server: SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = 0
        keyPairProvider = KeyPairProvider.wrap(hostKey)
        setPasswordAuthenticator { u, p, _ -> u == user && p == password }
        setPublickeyAuthenticator { u, key, _ -> u == user && (KeyUtils.compareKeys(key, clientKey.public) || KeyUtils.compareKeys(key, rsaKey.public)) }
        setCommandFactory { _, command -> FakeCommand(command) }
        setShellFactory { _ -> FakeShell() }
        start()
    }

    @AfterTest
    fun cleanUp() {
        runCatching { server.stop(true) }
        base.deleteRecursively()
    }

    private fun profile(auth: SshAuth = SshAuth.PASSWORD, keyFile: File? = null) = SshProfile(
        id = "p1", name = "Testserver", host = "127.0.0.1", port = server.port, user = user, auth = auth,
        keyFile = keyFile?.absolutePath.orEmpty(), workDir = "/var/www/app",
    )

    /** A terminal that already trusts the test server. */
    private fun trusted(): ServerTerminal = ServerTerminal(File(base, "ssh")).also { it.knownHosts.accept("127.0.0.1", server.port, hostKey.public) }

    /** What the fake server answers: enough of git, composer, Symfony and setfacl for the flow. */
    private inner class FakeCommand(private val command: String) : Command {
        private lateinit var out: OutputStream
        private lateinit var exit: ExitCallback
        override fun setInputStream(`in`: InputStream) {}
        override fun setOutputStream(out: OutputStream) { this.out = out }
        override fun setErrorStream(err: OutputStream) {}
        override fun setExitCallback(callback: ExitCallback) { exit = callback }
        override fun destroy(channel: ChannelSession) {}
        override fun start(channel: ChannelSession, env: Environment) {
            received += command
            val cmd = command.substringAfter(" && ")
            val (text, code) = when {
                cmd.startsWith("git status") -> "" to 0
                cmd == "git pull" -> "Already up to date.\n" to 0
                cmd.startsWith("composer install") -> "Installing dependencies from lock file\nGenerating optimized autoload files\n" to 0
                cmd.contains("asset-map:compile") -> "[OK] Compiled 12 assets\n" to 0
                cmd.startsWith("setfacl") -> "" to 0
                cmd.contains("cache:clear") -> "[OK] Cache for the \"prod\" environment (debug=false) was successfully cleared.\n" to 0
                cmd.startsWith("cat .env.local") -> "APP_SECRET=a8F3kL9qXz2Vb7Nm4Pw6Rt1Yc5Hd0Gs8\nDATABASE_URL=mysql://app:dbpass99@db.lehrstuhl-intern.de:3306/app\nMAILER_DSN=smtp://mail.tu-beispiel.de\n# Passwort: $password\nServer 192.168.17.42\n" to 0
                cmd == "false" -> "" to 1
                else -> "unbekannt: $cmd\n" to 127
            }
            Thread {
                out.write(text.toByteArray()); out.flush()
                exit.onExit(code)
            }.start()
        }
    }

    /** A shell that reports its PTY and answers each line with „> line“. */
    private inner class FakeShell : Command {
        private lateinit var input: InputStream
        private lateinit var out: OutputStream
        private lateinit var exit: ExitCallback
        override fun setInputStream(`in`: InputStream) { input = `in` }
        override fun setOutputStream(out: OutputStream) { this.out = out }
        override fun setErrorStream(err: OutputStream) {}
        override fun setExitCallback(callback: ExitCallback) { exit = callback }
        override fun destroy(channel: ChannelSession) {}
        override fun start(channel: ChannelSession, env: Environment) {
            ptyType = env.env["TERM"]
            Thread {
                out.write("bereit\r\n".toByteArray()); out.flush()
                val line = StringBuilder()
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    if (b == '\r'.code || b == '\n'.code) {
                        if (line.toString() == "exit") break
                        out.write("> $line\r\n".toByteArray()); out.flush(); line.clear()
                    } else line.append(b.toChar())
                }
                exit.onExit(0)
            }.start()
        }
    }

    @Test
    fun newServerKeyIsAskedForAndAChangedOneIsBlocked() = runBlocking {
        val t = ServerTerminal(File(base, "ssh"))
        t.connect(profile(), password)
        val asked = assertNotNull(t.state.value.hostKey, "neuer Server: erst fragen")
        val r = assertIs<KnownHosts.Result.New>(asked.result)
        assertEquals(KnownHosts.fingerprint(hostKey.public), r.fingerprint)
        assertTrue(r.fingerprint.startsWith("SHA256:"))
        assertFalse(t.state.value.connected)
        assertTrue(received.isEmpty(), "vor der Bestätigung läuft nichts")
        t.acceptHostKey(replace = false)
        assertTrue(t.state.value.connected, t.state.value.error ?: "")
        assertTrue(t.knownHosts.file.readText().startsWith("[127.0.0.1]:${server.port} ecdsa-sha2-nistp256 "), t.knownHosts.file.readText())
        t.disconnect()

        // Another key for the same host and type: blocked, also when „confirmed“ without replace.
        val other = KeyUtils.generateKeyPair(KeyPairProvider.ECDSA_SHA2_NISTP256, 256)
        t.knownHosts.forget("127.0.0.1", server.port)
        t.knownHosts.accept("127.0.0.1", server.port, other.public)
        t.connect(profile(), password)
        val changed = assertIs<KnownHosts.Result.Changed>(t.state.value.hostKey?.result)
        assertEquals(listOf(KnownHosts.fingerprint(other.public)), changed.previous)
        t.acceptHostKey(replace = false)
        assertFalse(t.state.value.connected, "geänderter Schlüssel nur mit ausdrücklichem Ersetzen")
        t.acceptHostKey(replace = true)
        assertTrue(t.state.value.connected)
        assertEquals(1, t.knownHosts.list().size, "der alte Schlüssel ist ersetzt")
        t.disconnect()
        val log = t.logOf(profile()).read()
        assertTrue("Server-Schlüssel geändert" in log && "ausdrücklich ersetzt" in log, log)
    }

    @Test
    fun loginWithPasswordWithAKeyBehindAPassphraseAndWithAPuttyKey() = runBlocking {
        val t = trusted()
        t.connect(profile(), "falsch")
        assertFalse(t.state.value.connected)
        assertTrue(t.state.value.error.orEmpty().contains("Passwort"), t.state.value.error)

        // ed25519 in OpenSSH format with a passphrase.
        val keyFile = File(base, "id_ed25519")
        keyFile.outputStream().use { o ->
            val ctx = OpenSSHKeyEncryptionContext().apply { password = passphrase; cipherName = "AES"; cipherMode = "CTR"; cipherType = "256" }
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(clientKey, "test", ctx, o)
        }
        val withKey = profile(SshAuth.KEY, keyFile)
        t.connect(withKey, null)
        assertTrue(t.state.value.needsSecret, "ohne Passphrase: nachfragen")
        t.connect(withKey, passphrase, keep = true)
        assertTrue(t.state.value.connected, t.state.value.error ?: "")
        t.disconnect()
        // Kept encrypted: the file does not contain the passphrase, the next login works without typing it.
        val stored = File(base, "ssh/secrets.bin")
        assertTrue(stored.isFile && passphrase !in stored.readText() && passphrase !in String(Base64.getDecoder().decode(stored.readText().trim()), Charsets.ISO_8859_1))
        assertFalse(File(base, "ssh/sessions.json").let { it.isFile && passphrase in it.readText() })
        t.connect(withKey, null)
        assertTrue(t.state.value.connected, t.state.value.error ?: "")
        t.disconnect()

        // PuTTY key (.ppk, version 2, as puttygen writes it).
        val ppk = File(base, "schluessel.ppk").apply { writeText(puttyKey(rsaKey)) }
        val loaded = SshConnection.loadKeys(ppk, null).single()
        assertTrue(KeyUtils.compareKeys(loaded.public, rsaKey.public))
        t.connect(profile(SshAuth.KEY, ppk), null)
        assertTrue(t.state.value.connected, t.state.value.error ?: "")
        t.disconnect()
    }

    @Test
    fun symfonyFlowRunsStepByStepOnlyAfterApproval() = runBlocking {
        val t = trusted()
        t.connect(profile(), password)
        assertTrue(t.state.value.connected, t.state.value.error ?: "")
        t.load(ServerFlows.SYMFONY_DEPLOY)
        val steps = t.state.value.steps
        assertEquals(
            listOf(
                "git status --short", "git pull", "composer install --no-dev --optimize-autoloader", "php bin/console asset-map:compile",
                "setfacl -R -m u:www-data:rwX -m d:u:www-data:rwX public/assets/ vendor/", "sudo -u www-data php bin/console cache:clear",
            ),
            steps.map { it.command },
        )
        assertTrue(steps.all { it.what.isNotBlank() })
        assertEquals(Risk.READ, steps[0].risk)
        assertTrue(steps.drop(1).all { it.risk >= Risk.CHANGE })
        assertTrue("www-data" in steps[5].safety && "sudo" in steps[5].safety, steps[5].safety)
        assertTrue(received.isEmpty(), "Vorschlagen führt nichts aus")

        assertTrue(t.run(0)!!.ok)
        assertEquals(1, received.size, "genau ein Schritt je Zustimmung")
        assertEquals("cd '/var/www/app' && git status --short", received.single())
        t.skip(1)
        for (i in 2..5) assertTrue(t.run(i)!!.ok, t.state.value.steps[i].output)
        assertEquals(5, received.size)
        assertTrue(received.none { "git pull" in it }, "übersprungen heißt: nicht ausgeführt")
        assertTrue(received.last().endsWith("&& sudo -u www-data php bin/console cache:clear"))
        assertNull(t.run(5), "ein fertiger Schritt läuft nicht zweimal")
        assertEquals(listOf(StepState.DONE, StepState.SKIPPED, StepState.DONE, StepState.DONE, StepState.DONE, StepState.DONE), t.state.value.steps.map { it.state })
        assertTrue("successfully cleared" in t.state.value.steps[5].output)

        // A failing step stays open for „Nochmal“; an edited command is assessed again.
        t.edit(0, "false")
        assertEquals(StepState.OPEN, t.state.value.steps[0].state)
        assertFalse(t.run(0)!!.ok)
        assertEquals(StepState.FAILED, t.state.value.steps[0].state)
        assertEquals(1, t.state.value.steps[0].exitCode)
        t.edit(0, "rm -rf var/cache")
        assertEquals(Risk.RISKY, t.state.value.steps[0].risk)
        t.cancel()
        assertTrue(t.state.value.steps.isEmpty())

        val log = t.readLog()
        assertEquals(6, Regex("Zugestimmt, ausgeführt").findAll(log).count(), log)
        assertTrue("Übersprungen: git pull" in log && "Ende mit Exit 1: false" in log && "Abgebrochen" in log, log)
        t.disconnect()
    }

    @Test
    fun secretsNeverReachTheProtocolOrAModelRequest() = runBlocking {
        val t = trusted()
        t.connect(profile(), password)
        t.load(ServerFlows.SYMFONY_STATUS)
        t.edit(0, "cat .env.local")
        val r = t.run(0)!!
        assertTrue(password in r.output && "dbpass99" in r.output, "der Server zeigt die Geheimnisse")
        val log = t.readLog()
        for (secret in listOf(password, "dbpass99", "a8F3kL9qXz2Vb7Nm4Pw6Rt1Yc5Hd0Gs8")) assertFalse(secret in log, "$secret im Protokoll:\n$log")
        assertTrue("<geheim>" in log)

        // What would go to a model on a server: no secret, no host, no IP, no user, no folder.
        val p = profile().copy(host = "web01.lehrstuhl-intern.de", user = "mmuster")
        val mask = ServerMask(listOf(password), p)
        val sent = ServerAssistant.request("Warum geht die Datenbank nicht? Server web01.lehrstuhl-intern.de", p, r.output + "\nLogin mmuster@web01.lehrstuhl-intern.de, Mail an admin@lehrstuhl-intern.de, /home/mmuster/.ssh", emptyList(), mask)
        for (leak in listOf(password, "dbpass99", "a8F3kL9qXz2Vb7Nm4Pw6Rt1Yc5Hd0Gs8", "lehrstuhl-intern.de", "192.168.17.42", "mmuster", "/var/www/app", "tu-beispiel.de")) {
            assertFalse(leak in sent, "$leak geht hinaus:\n$sent")
        }
        assertTrue("<geheim>" in sent && "<host-1>" in sent && "<ip-1>" in sent && "<benutzer-1>" in sent && "<app-ordner>" in sent, sent)
        // Lora's proposal gets the real details back – but never a secret.
        val answer = """```json
            {"steps":[{"command":"ping -c1 <host-1> && ls <app-ordner>","what":"Erreichbarkeit prüfen","safety":"","risk":"read"},
                      {"command":"sudo systemctl restart apache2","what":"Apache neu starten","risk":"read"}],"note":"Passwort <geheim> nicht nötig"}
            ```"""
        val (steps, note) = ServerAssistant.parse(mask.unmask(answer))
        assertEquals("ping -c1 web01.lehrstuhl-intern.de && ls /var/www/app", steps[0].command)
        assertEquals(Risk.RISKY, steps[1].risk, "Loras „read“ macht einen Neustart nicht harmlos")
        assertTrue("<geheim>" in note && password !in note)
        t.disconnect()
    }

    @Test
    fun riskOfCommandsIsTheAppsOwn() {
        assertEquals(Risk.READ, Risks.of("ls -la public/"))
        assertEquals(Risk.READ, Risks.of("apachectl configtest"))
        assertEquals(Risk.READ, Risks.of("php bin/console about"))
        assertEquals(Risk.CHANGE, Risks.of("git pull"))
        assertEquals(Risk.CHANGE, Risks.of("sudo systemctl reload apache2"))
        assertEquals(Risk.CHANGE, Risks.of("php bin/console cache:clear"))
        assertEquals(Risk.RISKY, Risks.of("rm -rf var/cache/*"))
        assertEquals(Risk.RISKY, Risks.of("chmod -R 777 public"))
        assertEquals(Risk.RISKY, Risks.of("sudo systemctl restart apache2"))
        assertEquals(Risk.RISKY, Risks.of("php bin/console doctrine:migrations:migrate"))
        assertEquals(Risk.RISKY, Risks.of("curl https://example.org/x.sh | sudo bash"))
        assertEquals(Risk.CHANGE, ServerStep("ls", "").withLoraRisk(Risk.CHANGE).risk, "Lora darf vorsichtiger sein")
        assertEquals(Risk.RISKY, ServerStep("rm -rf /tmp/x", "").withLoraRisk(Risk.READ).risk)
        // Answers that are not JSON or have multi-line commands give no steps.
        assertTrue(ServerAssistant.parse("Keine Ahnung").first.isEmpty())
        assertTrue(ServerAssistant.parse("""{"steps":[{"command":"echo a\nrm -rf /","what":"x"}]}""").first.isEmpty())
    }

    @Test
    fun terminalGetsAShellWithPty() = runBlocking {
        val t = trusted()
        t.connect(profile(), password)
        val conn = assertNotNull(t.connection)
        val tty = SshTty(conn.shell(100, 30), "test")
        val buf = CharArray(4096)
        fun readUntil(text: String): String {
            val seen = StringBuilder()
            val until = System.currentTimeMillis() + 10_000
            while (text !in seen && System.currentTimeMillis() < until) {
                val n = tty.read(buf, 0, buf.size)
                if (n < 0) break
                seen.append(buf, 0, n)
            }
            return seen.toString()
        }
        assertTrue("bereit" in readUntil("bereit"))
        assertEquals("xterm-256color", ptyType)
        tty.write("ls -la\r")
        assertTrue("> ls -la" in readUntil("> ls -la"))
        assertTrue(tty.isConnected)
        tty.write("exit\r")
        tty.close()
        t.disconnect()
    }

    @Test
    fun noTerminalWithoutConfirmedServerEvenWithTheRightPassword() {
        val known = KnownHosts(File(base, "known_hosts"))
        val e = runCatching { SshConnection.connect(profile(), password, known) }.exceptionOrNull()
        assertIs<HostKeyProblem>(e)
        assertTrue(received.isEmpty())
    }

    /**
     * JediTerm 3.76 is built with Kotlin 2.4; the app ships its own Kotlin standard library (2.0.21). Every class and
     * method of `kotlin.*` that JediTerm's bytecode calls must exist in the library on the classpath – otherwise the
     * terminal would fail at run time with NoSuchMethodError.
     */
    @Test
    fun jediTermOnlyCallsFunctionsOfOurKotlinStdlib() {
        val jars = listOf(com.jediterm.terminal.TtyConnector::class.java, com.jediterm.terminal.ui.JediTermWidget::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()) }.distinct()
        assertEquals(2, jars.size, jars.toString())
        val missing = mutableListOf<String>()
        var checked = 0
        for (jar in jars) ZipFile(jar).use { z ->
            for (e in z.entries()) {
                if (!e.name.endsWith(".class")) continue
                for ((owner, name, desc) in ClassRefs.read(z.getInputStream(e).readBytes())) {
                    if (!owner.startsWith("kotlin/")) continue
                    checked++
                    val cls = runCatching { Class.forName(owner.replace('/', '.'), false, javaClass.classLoader) }.getOrNull()
                    if (cls == null) { missing += owner; continue }
                    if (name == null) continue
                    val ok = if (name == "<init>") ClassRefs.hasConstructor(cls, desc!!) else ClassRefs.hasMember(cls, name, desc!!)
                    if (!ok) missing += "$owner.$name$desc"
                }
            }
        }
        assertTrue(checked > 50, "$checked Aufrufe geprüft")
        assertTrue(missing.isEmpty(), "Fehlt in der Kotlin-Standardbibliothek der App:\n" + missing.distinct().joinToString("\n"))
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────────────

    /** A PuTTY key file version 2 without encryption, with the MAC as puttygen computes it. */
    private fun puttyKey(kp: KeyPair): String {
        val pub = kp.public as RSAPublicKey
        val priv = kp.private as RSAPrivateCrtKey
        fun blob(block: DataOutputStream.() -> Unit) = ByteArrayOutputStream().also { DataOutputStream(it).block() }.toByteArray()
        fun DataOutputStream.str(b: ByteArray) { writeInt(b.size); write(b) }
        fun DataOutputStream.mpint(n: BigInteger) = str(n.toByteArray())
        val pubBlob = blob { str("ssh-rsa".toByteArray()); mpint(pub.publicExponent); mpint(pub.modulus) }
        val privBlob = blob { mpint(priv.privateExponent); mpint(priv.primeP); mpint(priv.primeQ); mpint(priv.crtCoefficient) }
        val comment = "test-key"
        val macData = blob { str("ssh-rsa".toByteArray()); str("none".toByteArray()); str(comment.toByteArray()); str(pubBlob); str(privBlob) }
        val macKey = MessageDigest.getInstance("SHA-1").digest("putty-private-key-file-mac-key".toByteArray())
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(macKey, "HmacSHA1")) }.doFinal(macData)
        fun lines(b: ByteArray) = Base64.getEncoder().encodeToString(b).chunked(64)
        val pl = lines(pubBlob)
        val vl = lines(privBlob)
        return buildString {
            append("PuTTY-User-Key-File-2: ssh-rsa\nEncryption: none\nComment: $comment\n")
            append("Public-Lines: ${pl.size}\n"); pl.forEach { append(it).append('\n') }
            append("Private-Lines: ${vl.size}\n"); vl.forEach { append(it).append('\n') }
            append("Private-MAC: ").append(mac.joinToString("") { "%02x".format(it) }).append('\n')
        }
    }
}

/** Reads the method and field references of a class file (constant pool only) – for the JediTerm check. */
internal object ClassRefs {
    fun read(b: ByteArray): List<Triple<String, String?, String?>> {
        val d = java.io.DataInputStream(b.inputStream())
        d.skipBytes(8)
        val n = d.readUnsignedShort()
        val utf = arrayOfNulls<String>(n)
        val cls = IntArray(n)
        val refs = mutableListOf<Pair<Int, Int>>()
        val nat = HashMap<Int, Pair<Int, Int>>()
        var i = 1
        while (i < n) {
            when (d.readUnsignedByte()) {
                1 -> utf[i] = d.readUTF()
                3, 4 -> d.skipBytes(4)
                5, 6 -> { d.skipBytes(8); i++ }
                7 -> cls[i] = d.readUnsignedShort()
                8, 16, 19, 20 -> d.skipBytes(2)
                9, 10, 11 -> refs += d.readUnsignedShort() to d.readUnsignedShort()
                12 -> nat[i] = d.readUnsignedShort() to d.readUnsignedShort()
                15 -> d.skipBytes(3)
                17, 18 -> d.skipBytes(4)
                else -> error("unbekannter Eintrag")
            }
            i++
        }
        val classes = cls.filter { it != 0 }.mapNotNull { utf[it] }.filter { !it.startsWith("[") }.map { Triple(it, null, null) }
        return classes + refs.mapNotNull { (c, t) ->
            val owner = utf[cls[c]] ?: return@mapNotNull null
            val (nm, ds) = nat[t] ?: return@mapNotNull null
            Triple(owner, utf[nm], utf[ds])
        }
    }

    private fun type(c: Class<*>): String = when {
        c == Void.TYPE -> "V"
        c == java.lang.Boolean.TYPE -> "Z"
        c == java.lang.Byte.TYPE -> "B"
        c == java.lang.Character.TYPE -> "C"
        c == java.lang.Short.TYPE -> "S"
        c == java.lang.Integer.TYPE -> "I"
        c == java.lang.Long.TYPE -> "J"
        c == java.lang.Float.TYPE -> "F"
        c == java.lang.Double.TYPE -> "D"
        c.isArray -> c.name.replace('.', '/')
        else -> "L" + c.name.replace('.', '/') + ";"
    }

    private fun descriptor(params: Array<Class<*>>, ret: Class<*>?): String = params.joinToString("", "(", ")") { type(it) } + (ret?.let { type(it) } ?: "V")

    fun hasConstructor(cls: Class<*>, desc: String) = cls.declaredConstructors.any { descriptor(it.parameterTypes, null) == desc }

    fun hasMember(cls: Class<*>, name: String, desc: String): Boolean {
        var c: Class<*>
        val seen = HashSet<Class<*>>()
        val queue = ArrayDeque<Class<*>>().apply { add(cls) }
        while (queue.isNotEmpty()) {
            c = queue.removeFirst()
            if (!seen.add(c)) continue
            if (c.declaredMethods.any { it.name == name && descriptor(it.parameterTypes, it.returnType) == desc }) return true
            if (c.declaredFields.any { it.name == name && type(it.type) == desc }) return true
            c.superclass?.let { queue += it }
            queue += c.interfaces
        }
        return false
    }
}
