package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.ssh.ServerFlows
import io.github.thepytales.llmhub.ssh.ServerTerminal
import io.github.thepytales.llmhub.ssh.SshAuth
import io.github.thepytales.llmhub.ssh.SshProfile
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.ServerTerminalScreen
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Server-Terminal (AP-118) as pictures (build/screenshots/server-*.png): the saved sessions and, connected to an SSH
 * server in the test, the flow „Symfony aktualisieren“ with two steps done. The terminal itself is a Swing component
 * (JediTerm) and cannot be drawn without a window – here a stand-in shows where it sits.
 */
@OptIn(ExperimentalTestApi::class)
class ServerTerminalLookTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-ssh-look").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }
    private val hostKey = KeyUtils.generateKeyPair(KeyPairProvider.ECDSA_SHA2_NISTP256, 256)
    private val server: SshServer = SshServer.setUpDefaultServer().apply {
        host = "127.0.0.1"
        port = 0
        keyPairProvider = KeyPairProvider.wrap(hostKey)
        setPasswordAuthenticator { _, p, _ -> p == "test" }
        setCommandFactory { _, command ->
            object : org.apache.sshd.server.command.Command {
                private lateinit var out: java.io.OutputStream
                private lateinit var exit: org.apache.sshd.server.ExitCallback
                override fun setInputStream(`in`: java.io.InputStream) {}
                override fun setOutputStream(out: java.io.OutputStream) { this.out = out }
                override fun setErrorStream(err: java.io.OutputStream) {}
                override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback) { exit = callback }
                override fun destroy(channel: org.apache.sshd.server.channel.ChannelSession) {}
                override fun start(channel: org.apache.sshd.server.channel.ChannelSession, env: org.apache.sshd.server.Environment) {
                    val text = if ("git pull" in command) "Updating 3f2a1c4..9b8e7d6\nFast-forward\n src/Controller/KursController.php | 12 ++++++------\n" else ""
                    Thread { out.write(text.toByteArray()); out.flush(); exit.onExit(0) }.start()
                }
            }
        }
        start()
    }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() {
        runCatching { server.stop(true) }
        base.deleteRecursively()
    }

    private fun SkikoComposeUiTest.shot(name: String, w: Int, h: Int) {
        repeat(6) { Thread.sleep(150); mainClock.advanceTimeBy(300) }
        waitForIdle()
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        File("build/screenshots").mkdirs()
        ImageIO.write(img, "png", File("build/screenshots/$name"))
    }

    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun sessionsAndStepsAtTheWidthOfAWindowsWindow() {
        val term = ServerTerminal(File(base, "ssh"))
        val profile = SshProfile(id = "demo", name = "Lehrstuhl-Webseite (Beispiel)", host = "127.0.0.1", port = server.port, user = "deploy", auth = SshAuth.PASSWORD, workDir = "/var/www/app")
        term.sessions.save(profile)
        term.sessions.save(SshProfile(id = "demo2", name = "Testsystem (Beispiel)", host = "test.example.org", user = "deploy", keyFile = "C:\\Users\\du\\.ssh\\id_ed25519"))
        c.settings.update { it.copy(apiKey = "") }
        runSkikoComposeUiTest(Size(1280f * 2, 820f * 2), Density(2f)) {
            mainClock.autoAdvance = false
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                    LlmHubTheme(AppSettings()) {
                        Box(Modifier.size(1280.dp, 820.dp).testTag("shot")) {
                            ServerTerminalScreen(c, onBack = {}, term = term, terminalView = { m -> FakeTerminal(m) })
                        }
                    }
                }
            }
            shot("server-sitzungen-windows.png", 1280, 820)
            assertTrue(has("Gespeicherte Sitzungen") && has("Lehrstuhl-Webseite (Beispiel)") && has("Verbinden"))

            term.knownHosts.accept("127.0.0.1", server.port, hostKey.public)
            runBlocking { term.connect(profile, "test") }
            assertTrue(term.state.value.connected, term.state.value.error ?: "")
            term.load(ServerFlows.SYMFONY_DEPLOY)
            runBlocking { term.run(0); term.run(1) }
            shot("server-ablauf-windows.png", 1280, 820)
            assertTrue(has("Symfony aktualisieren") && has("Ausführen") && has("composer install"))
            assertTrue(has("Für Vorschläge fehlt ein Sprachmodell"), "ohne Modell hier und ohne Schlüssel: ehrlicher Hinweis")
            term.disconnect()
        }
    }

    /** Where JediTerm sits: a dark area with a typical session. */
    @androidx.compose.runtime.Composable
    private fun FakeTerminal(m: Modifier) {
        Box(m.background(Color(0xFF1E1E1E)).padding(10.dp)) {
            androidx.compose.material3.Text(
                "deploy@web:~$ cd /var/www/app\ndeploy@web:/var/www/app$ git status --short\ndeploy@web:/var/www/app$ git pull\nUpdating 3f2a1c4..9b8e7d6\nFast-forward\n src/Controller/KursController.php | 12 ++++++------\ndeploy@web:/var/www/app$ █",
                color = Color(0xFFD4D4D4), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
