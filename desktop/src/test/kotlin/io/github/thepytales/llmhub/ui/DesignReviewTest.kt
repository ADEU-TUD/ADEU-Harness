package io.github.thepytales.llmhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.thepytales.llmhub.AppContainer
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.ChatMessage
import io.github.thepytales.llmhub.data.Conversation
import io.github.thepytales.llmhub.data.Folder
import io.github.thepytales.llmhub.data.ScheduledTask
import io.github.thepytales.llmhub.research.Person
import io.github.thepytales.llmhub.research.RefType
import io.github.thepytales.llmhub.research.Reference
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * APD-01 (Design-Arbeitsplan): pictures of every area of the real app for the UI review – the whole window
 * ([AppRoot]) led through its routes, in the sizes and looks of the review:
 *  - PC 1366 × 768 at 150 % (911 × 512 dp), a wide window (1600 × 900 dp) and a phone (393 × 852 dp),
 *  - ADEU light and dark; TU Dresden and Hoher Kontrast as samples,
 *  - empty (first start) and with example data.
 * Pictures in build/design-review/<config>/<nn>-<route>.png. Only pictures, no assertions about the look.
 * About 6 minutes, so only on request (like BrowserLiveTest):
 * ADEU_DESIGN_REVIEW=1 ./gradlew -p desktop test --tests '*DesignReviewTest*'
 */
@OptIn(ExperimentalTestApi::class)
class DesignReviewTest {
    private val out = File("build/design-review").apply { mkdirs() }

    /** Only with ADEU_DESIGN_REVIEW=1 (the pictures take minutes). */
    private val wanted get() = System.getenv("ADEU_DESIGN_REVIEW") == "1"

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    /** A fresh app with its own data folder. */
    private fun container(): Pair<AppContainer, File> {
        val base = java.nio.file.Files.createTempDirectory("adeu-design-review").toFile()
        System.setProperty("adeu.data", File(base, "daten").absolutePath)
        val c = AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        return c to base
    }

    /** Settings of a user who has finished the first start (no dialogs in the way, no start animation). */
    private fun AppSettings.settled(style: String, mode: String) = copy(
        appStyle = style, themeMode = mode, startAnimation = false, loraIntroDone = true, modelsIntroDone = true,
        pseudonymModelAsk = false, commandModelAsk = false, loraTours = false, memoryIntroDone = true, autoModelAsk = false,
        aiUseHint = false, userName = "Alex",
    )

    /** Example data: projects, chats (one with an answer), files, literature, a scheduled task. */
    private fun seed(c: AppContainer): String {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val p1 = Folder(name = "Lehrinnovation 2027")
        val p2 = Folder(name = "Promotion Kapitel 3")
        c.folders.upsert(p1); c.folders.upsert(p2)
        val answer = """
            Gern – hier ist ein Vorschlag für die **Gliederung des Antrags** „Digitale Lehre im Grundstudium“:

            ## 1. Ausgangslage
            Die Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende. Übungen sind überlaufen.

            ## 2. Ziele
            - Selbstlernmodule mit sofortiger Rückmeldung
            - Weniger Abbrüche im ersten Semester (Ziel: −15 %)
            - Wiederverwendbare Materialien unter offener Lizenz

            ## 3. Zeitplan
            | Phase | Zeitraum | Ergebnis |
            |---|---|---|
            | Konzeption | 01–03/2027 | Lernziele, Storyboard |
            | Umsetzung | 04–09/2027 | 12 Module in OPAL |
            | Evaluation | 10–12/2027 | Bericht, Kennzahlen |

            > Tipp: Die Ausschreibung verlangt höchstens **5 Seiten** – Abschnitt 2 lässt sich gut als Tabelle kürzen.

            ```python
            abbruch_vorher, abbruch_nachher = 0.31, 0.26
            print(f"Rückgang: {abbruch_vorher - abbruch_nachher:.0%}")
            ```

            Soll ich daraus gleich ein Dokument im Stil der TU Dresden erstellen?
        """.trimIndent()
        val q = ChatMessage(role = "user", content = "Hilf mir, den Antrag für den Lehrinnovationsfonds zu gliedern. Fokus: Statistik-Grundvorlesung, 400 Studierende.")
        val a = ChatMessage(role = "assistant", content = answer, model = io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT)
        val main = Conversation(title = "Antrag Lehrinnovationsfonds gliedern", model = io.github.thepytales.llmhub.data.ModelCatalog.DEFAULT_CHAT, messages = listOf(q, a), folderId = p1.id, updatedAt = now)
        c.chats.save(main)
        listOf(
            Triple("Mail an Prüfungsamt formulieren", null, now - 2 * 3600_000L),
            Triple("Literatur zu Peer Instruction", p2.id, now - day),
            Triple("Interview 3 zusammenfassen", p2.id, now - day - 3600_000L),
            Triple("Folien Ringvorlesung KI", null, now - 3 * day),
            Triple("Excel: Notenliste bereinigen", null, now - 5 * day),
            Triple("Übersetzung Abstract Englisch", null, now - 12 * day),
            Triple("Stellenausschreibung WHK", null, now - 40 * day),
        ).forEach { (t, f, u) ->
            c.chats.save(Conversation(title = t, model = "m", folderId = f, updatedAt = u, messages = listOf(ChatMessage(role = "user", content = t))))
        }
        val created = c.drive.created.apply { mkdirs() }
        runCatching {
            io.github.thepytales.llmhub.docs.MarkdownPdf.write(File(created, "Ausschreibung Lehrinnovation.pdf"), "Ausschreibung", "# Ausschreibung\n\nFörderung bis 25000 Euro.\n\n## Fristen\n\nBis 30.11.2026 einreichen.")
        }
        File(created, "Einladung Kolloquium.md").writeText("# Einladung\n\nLiebe Kolleginnen und Kollegen,\n\nherzliche Einladung zum Kolloquium am 12.11.2026.")
        File(created, "Notenliste WS26.csv").writeText("Matrikel;Note\n4711;1,7\n4712;2,3\n")
        File(created, "Protokoll Institutsrat.txt").writeText("Protokoll der Sitzung vom 05.11.2026 …")
        File(c.drive.root, "Lehre").mkdirs()
        File(c.drive.root, "Forschung").mkdirs()
        runCatching {
            c.literature.addAll(
                listOf(
                    Reference(type = RefType.ARTICLE, authors = listOf(Person("Mazur", "Eric")), year = "1997", title = "Peer Instruction: A User's Manual", container = "Prentice Hall"),
                    Reference(type = RefType.ARTICLE, authors = listOf(Person("Crouch", "Catherine H."), Person("Mazur", "Eric")), year = "2001", title = "Peer Instruction: Ten years of experience and results", container = "American Journal of Physics"),
                    Reference(type = RefType.BOOK, authors = listOf(Person("Biggs", "John"), Person("Tang", "Catherine")), year = "2011", title = "Teaching for Quality Learning at University", container = ""),
                ),
                list = "Promotion",
            )
        }
        runCatching { c.tasks.upsert(ScheduledTask(name = "Wochenbericht Lehrstuhl", prompt = "Fasse die Mails der Woche zusammen.", model = "m", hour = 8)) }
        return main.id
    }

    private fun SkikoComposeUiTest.settle(ms: Long = 1600) {
        repeat(8) {
            Thread.sleep(60)
            mainClock.advanceTimeBy(ms / 8)
        }
        runCatching { waitForIdle() }
    }

    /**
     * Opens the app in one configuration and photographs [routes] (each from a fresh chat as root). A route can be
     * "chat:<id>" to open a chat, or one of AppRoot's own requests ("drawer", "palette", "lora", "shortcuts").
     */
    private fun shoot(
        config: String, widthDp: Int, heightDp: Int, density: Float,
        style: String = "ADEU", mode: String = "LIGHT", data: Boolean = true, firstStart: Boolean = false,
        routes: List<String>,
        /** Routes that need data made in this app first (editors: document, sheet, slides, design, whiteboard). */
        extra: (AppContainer) -> List<String> = { emptyList() },
    ) {
        if (!wanted) return
        val dir = File(out, config).apply { deleteRecursively(); mkdirs() }
        val (c, base) = container()
        val mainChat = if (data) seed(c) else ""
        // The settings file lies in the data folder of the first test of this JVM (Paths is read once): start from the
        // defaults every time, so an empty run really is empty (no key, no name).
        c.settings.update { if (firstStart) AppSettings(appStyle = style, themeMode = mode, startAnimation = false) else AppSettings().settled(style, mode) }
        if (data) c.settings.update { it.copy(apiKey = "sk-beispiel") }
        val allRoutes = routes + extra(c)
        val openChat = MutableStateFlow<String?>(null)
        val openRoute = MutableStateFlow<String?>(null)
        val errors = mutableListOf<String>()
        try {
            runSkikoComposeUiTest(Size(widthDp * density, heightDp * density), Density(density)) {
                mainClock.autoAdvance = false
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                        val s by c.settings.settings.collectAsState()
                        LlmHubTheme(s) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AppRoot(c, openChat, openRoute) }
                        }
                    }
                }
                settle(3000)
                allRoutes.forEachIndexed { i, r ->
                    runCatching {
                        if (!firstStart || i > 0) {
                            openRoute.value = "chat"
                            settle(600)
                        }
                        when {
                            r == "start" -> {}
                            r == "chat:main" -> openChat.value = mainChat
                            r.startsWith("chat:") -> openChat.value = r.removePrefix("chat:")
                            else -> openRoute.value = r
                        }
                        settle(if (r == "lora") 2600 else 1800)
                        val name = "%02d-%s.png".format(i + 1, r.replace('/', '_').replace(':', '_').replace('?', '_'))
                        ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, name))
                        // Overlays of AppRoot stay open: close them before the next route.
                        if (r == "lora") { LoraHelper.toggle(); settle(600) }
                        if (r == "drawer") { openRoute.value = "back"; settle(900) }
                    }.onFailure { errors += "$r: ${it::class.simpleName} ${it.message?.take(160)}" }
                }
            }
        } finally {
            File(dir, "fehler.txt").writeText(errors.joinToString("\n"))
            runCatching { base.deleteRecursively() }
        }
        assertTrue((dir.listFiles()?.count { it.extension == "png" } ?: 0) > 0, "Bilder in $dir")
    }

    private val settingsSections = listOf(
        "api", "lora", "places", "github", "chat", "tools", "ram", "privacy", "appearance", "transcription", "voice",
        "documents", "memory", "knowledge", "literature", "backup", "trash", "folders", "storage", "connections",
        "updates", "help", "downloads", "windows",
    )

    /** Every route of the desktop app. */
    private val allRoutes = listOf(
        "start", "chat:main", "drawer", "lora", "files", "templates", "literature", "transcripts", "pdf", "vault",
        "games", "batches", "proposals", "mail", "video", "server", "tasks", "task/new", "environments",
        "environment/new", "images", "compare", "scan", "whiteboard/neu", "design/neu", "models", "local-models",
        "local-setup", "usage", "settings",
    ) + settingsSections.map { "settings/$it" }

    private val keyRoutes = listOf(
        "start", "chat:main", "drawer", "lora", "files", "templates", "literature", "transcripts", "pdf", "settings",
        "settings/appearance", "settings/api", "models", "tasks", "games", "mail",
    )

    private fun firstOf(routes: List<String>, n: Int) = routes.take(n)

    @Test fun pcLight() = shoot("pc-adeu-hell", 911, 512, 1.5f, routes = allRoutes)
    @Test fun pcDark() = shoot("pc-adeu-dunkel", 911, 512, 1.5f, mode = "DARK", routes = allRoutes)
    @Test fun wideLight() = shoot("breit-adeu-hell", 1600, 900, 1f, routes = keyRoutes + listOf("video", "server", "proposals", "batches", "vault"))
    @Test fun wideDark() = shoot("breit-adeu-dunkel", 1600, 900, 1f, mode = "DARK", routes = firstOf(keyRoutes, 8))
    @Test fun phoneLight() = shoot("handy-adeu-hell", 393, 852, 2f, routes = keyRoutes + listOf("vault", "proposals", "settings/privacy"))
    @Test fun phoneDark() = shoot("handy-adeu-dunkel", 393, 852, 2f, mode = "DARK", routes = firstOf(keyRoutes, 6))
    @Test fun pcTud() = shoot("pc-tud-hell", 911, 512, 1.5f, style = "TUD", routes = keyRoutes)
    @Test fun wideTud() = shoot("breit-tud-hell", 1600, 900, 1f, style = "TUD", routes = firstOf(keyRoutes, 6))
    @Test fun pcContrast() = shoot("pc-kontrast-hell", 911, 512, 1.5f, style = "CONTRAST", routes = keyRoutes)
    @Test fun pcContrastDark() = shoot("pc-kontrast-dunkel", 911, 512, 1.5f, style = "CONTRAST", mode = "DARK", routes = firstOf(keyRoutes, 6))

    /** The editors with an example each (they open from Dateien and Erstellen). */
    private fun editors(c: AppContainer): List<String> {
        val doc = io.github.thepytales.llmhub.docs.LiveDoc.create(
            "Antrag Lehrinnovation", "# Antrag Lehrinnovation\n\n## 1. Ausgangslage\n\nDie Vorlesung *Einführung in die Statistik* hat jedes Semester rund 400 Teilnehmende.\n\n## 2. Ziele\n\n- Selbstlernmodule mit sofortiger Rückmeldung\n- Weniger Abbrüche im ersten Semester\n\n## 3. Zeitplan\n\n| Phase | Zeitraum |\n|---|---|\n| Konzeption | 01–03/2027 |\n| Umsetzung | 04–09/2027 |\n",
            "Alex",
        )
        val sheet = io.github.thepytales.llmhub.docs.LiveDoc.create(
            "Notenliste WS26", "| Matrikel | Name | Klausur | Note |\n|---|---|---|---|\n| 4711 | A. Weber | 87 | 1,7 |\n| 4712 | B. Kraus | 71 | 2,3 |\n| 4713 | C. Lehmann | 55 | 3,3 |\n",
            "Alex", kind = io.github.thepytales.llmhub.docs.DocKind.SHEET,
        )
        val slides = io.github.thepytales.llmhub.docs.LiveDoc.create(
            "Ringvorlesung KI", "# Ringvorlesung KI\n\nSitzung 3 · Sprachmodelle in der Lehre\n\n---\n\n## Worum es heute geht\n\n- Was Sprachmodelle können\n- Wo sie irren\n- Gute Prüfungsfragen\n",
            "Alex", kind = io.github.thepytales.llmhub.docs.DocKind.SLIDES,
        )
        listOf(doc, sheet, slides).forEach { runCatching { c.docs.save(it) } }
        val design = runCatching { c.designs.save(io.github.thepytales.llmhub.data.Design(title = "Tagungsposter")) }.getOrNull()
        Whiteboards.init(c.appContext.filesDir)
        val board = runCatching { Whiteboards.save(Board(title = "Ablauf Prüfungsanmeldung", diagram = starterDiagram("flow"))) }.getOrNull()
        return listOfNotNull("doc/${doc.id}", "doc/${sheet.id}", "doc/${slides.id}", design?.let { "design/${it.id}" }, board?.let { "whiteboard/${it.id}" })
    }

    @Test fun pcEditors() = shoot("pc-editoren", 911, 512, 1.5f, routes = emptyList(), extra = ::editors)
    @Test fun wideEditors() = shoot("breit-editoren", 1600, 900, 1f, routes = emptyList(), extra = ::editors)

    /** Lora alone on a transparent ground, for the drafts of the style direction (build/design-review/lora-*.png). */
    @Test
    fun loraForDrafts() {
        if (!wanted) return
        listOf(LoraAction.WAVE, LoraAction.LOOK, LoraAction.READ, LoraAction.SLEEP, LoraAction.CHEER, LoraAction.SEARCH).forEach { action ->
            runSkikoComposeUiTest(Size(320 * 2f, 224 * 2f), Density(2f)) {
                mainClock.autoAdvance = false
                setContent {
                    LlmHubTheme(AppSettings(themeMode = "LIGHT")) {
                        LoraFigure(Modifier.fillMaxSize(), label = "Lora", director = rememberLoraDirector(listOf(action), action))
                    }
                }
                settle(action.periodMs / 3)
                ImageIO.write(captureToImage().toAwtImage(), "png", File(out, "lora-${action.name.lowercase()}.png"))
            }
        }
    }

    /**
     * The same states in the window sizes people really have (content area, title bar and taskbar taken off), to see
     * from which size on something is cut off: build/design-review/groessen/<size>-<state>.png.
     */
    @Test
    fun sizes() {
        if (!wanted) return
        File(out, "schrift.txt").writeText(
            "Standardschrift im Test: " + org.jetbrains.skia.FontMgr.default.legacyMakeTypeface("", org.jetbrains.skia.FontStyle.NORMAL)?.familyName + "\n",
        )
        val sizes = listOf(
            "a-1366x768-150" to (911 to 450), "b-1920x1080-150" to (1280 to 650), "c-app-standard-1180x820" to (1180 to 790),
            "d-1366x768-100" to (1366 to 690), "e-1920x1080-125" to (1536 to 790),
        )
        sizes.forEach { (name, wh) ->
            shoot("groessen-$name", wh.first, wh.second, 1.5f, data = false, routes = listOf("start"))
            shoot("groessen-$name-daten", wh.first, wh.second, 1.5f, routes = listOf("start", "chat:main", "templates", "files"), extra = { c -> editors(c).let { listOf(it[0], it[3]) } })
        }
    }

    /** First start: the setup window, then the empty states (no key, no data). */
    @Test fun firstStart() = shoot("pc-erster-start", 911, 512, 1.5f, data = false, firstStart = true, routes = listOf("start"))
    @Test fun wideFirstStart() = shoot("breit-erster-start", 1600, 900, 1f, data = false, firstStart = true, routes = listOf("start"))

    @Test fun pcEmpty() = shoot(
        "pc-leer", 911, 512, 1.5f, data = false,
        routes = listOf("start", "drawer", "files", "templates", "literature", "transcripts", "tasks", "vault", "games", "batches", "proposals", "environments", "images", "usage"),
    )
    @Test fun wideEmpty() = shoot("breit-leer", 1600, 900, 1f, data = false, routes = listOf("start", "files", "literature", "transcripts", "tasks"))
    @Test fun phoneEmpty() = shoot("handy-leer", 393, 852, 2f, data = false, routes = listOf("start", "drawer", "files", "transcripts"))

    /** Overlays of the frame: Strg+K and the shortcut overview (with data). */
    @Test fun overlays() = shoot("pc-overlays", 911, 512, 1.5f, routes = listOf("palette"))
    @Test fun overlaysShortcuts() = shoot("pc-overlays-kuerzel", 911, 512, 1.5f, routes = listOf("shortcuts"))
    @Test fun overlaysWide() = shoot("breit-overlays", 1600, 900, 1f, routes = listOf("palette"))
}
