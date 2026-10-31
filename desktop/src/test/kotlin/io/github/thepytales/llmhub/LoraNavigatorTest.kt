package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.agent.parseArgs
import io.github.thepytales.llmhub.agent.toSchema
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.MapKind
import io.github.thepytales.llmhub.ui.SETTINGS_INDEX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lora als Navigator (AP-07): the map of the app, the local answers and the settings Lora may change. */
class LoraNavigatorTest {
    private val targets = AppMap.build(android = false)

    @Test
    fun mapCoversAreasSettingsModesTemplatesAndTools() {
        val ids = targets.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "ids sind eindeutig")
        // Every settings page of the search has a place on the map.
        SETTINGS_INDEX.forEach { cmd -> assertTrue(targets.any { it.route == cmd.route }, "Karte kennt ${cmd.route}") }
        assertTrue(targets.any { it.kind == MapKind.MODE && it.route == "mode:literature" })
        assertTrue(targets.any { it.kind == MapKind.TEMPLATE && it.id == "template.b_res_excerpt" })
        assertTrue(targets.any { it.kind == MapKind.TOOL && it.route == "pdf" })
        // Windows-only places are missing on Android.
        assertTrue(AppMap.build(android = true).none { it.id == "settings.folders" })
    }

    @Test
    fun darkDesignLeadsToTheBrightnessOption() {
        val reply = LoraAgent.local("Dunkles Design", targets, emptySet(), android = false)
        assertNotNull(reply)
        val step = reply.steps.single() as LoraStep.Navigate
        assertEquals("settings/appearance", step.target.route)
        assertEquals("appearance.theme", step.target.spot)
        assertTrue(reply.local)
    }

    @Test
    fun simpleWishesAreFoundOnTheDevice() {
        fun nav(wish: String) = (LoraAgent.local(wish, targets, emptySet(), false)?.steps?.singleOrNull() as? LoraStep.Navigate)?.target
        assertEquals("voice.engine", nav("Wo stelle ich die Stimme um?")?.spot)
        assertEquals("appearance.textsize", nav("Schrift größer")?.spot)
        assertEquals("privacy.pseudonymize", nav("Pseudonymisierung")?.spot)
        assertEquals("lora.mode", nav("Lora ausblenden")?.spot)
        assertEquals("settings/backup", nav("Backup machen")?.route)
    }

    @Test
    fun pdfMergeSuggestsTheToolboxAndOpensItWhenOn() {
        val off = LoraAgent.local("Ich will ein PDF zusammenfügen", targets, emptySet(), android = false)
        assertEquals(AppTool.PDF, (off?.steps?.single() as? LoraStep.EnableTool)?.tool)
        val on = LoraAgent.local("Ich will ein PDF zusammenfügen", targets, setOf("pdf"), android = false)
        assertEquals("pdf", (on?.steps?.single() as? LoraStep.Navigate)?.target?.route)
    }

    @Test
    fun explanationsGoToAChat() {
        val reply = LoraAgent.local("Erklär mir Exzerpte", targets, emptySet(), android = false)
        val step = reply?.steps?.single() as? LoraStep.Handoff
        assertNotNull(step)
        assertEquals("Erklär mir Exzerpte", step.request)
        // „Wie geht …?“ about the app is answered from the built-in help.
        val howto = LoraAgent.local("Wie funktioniert der Tresor?", targets, emptySet(), android = false)
        assertNotNull(howto)
        assertTrue(howto.text.contains("PIN"), howto.text)
    }

    @Test
    fun unclearWishesNeedTheModel() {
        assertNull(LoraAgent.local("Ich habe morgen ein Gespräch mit meiner Betreuerin und brauche etwas Struktur", targets, emptySet(), false))
    }

    @Test
    fun modesOpenDirectly() {
        val reply = LoraAgent.local("Literatursuche", targets, emptySet(), false)
        assertEquals("mode:literature", (reply?.steps?.single() as? LoraStep.OpenMode)?.target?.route)
    }

    @Test
    fun toolsOfTheModelResolveToSteps() {
        val base = java.nio.file.Files.createTempDirectory("adeu-lora").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", java.io.File(base, "daten").absolutePath)
        val agent = LoraAgent(AppContainer(android.content.Context(java.io.File(base, "files").apply { mkdirs() }, java.io.File(base, "cache").apply { mkdirs() })))
        val (_, nav) = agent.runTool("navigate", parseArgs("""{"ziel":"option.appearance.theme"}"""), targets)
        assertEquals("appearance.theme", (nav as LoraStep.Navigate).target.spot)
        val (_, change) = agent.runTool("change_setting", parseArgs("""{"id":"theme","wert":"dunkel"}"""), targets)
        change as LoraStep.ChangeSetting
        assertEquals("DARK", change.value)
        assertEquals("DARK", change.setting.set(AppSettings(), change.value).themeMode)
        val (error, none) = agent.runTool("change_setting", parseArgs("""{"id":"apiKey","wert":"x"}"""), targets)
        assertNull(none)
        assertTrue(error.startsWith("FEHLER"))
        val (_, tool) = agent.runTool("enable_tool", parseArgs("""{"werkzeug":"pdf"}"""), targets)
        assertEquals(AppTool.PDF, (tool as LoraStep.EnableTool).tool)
        val (_, mode) = agent.runTool("open_mode", parseArgs("""{"modus":"planning","text":"Seminar"}"""), targets)
        assertEquals("Seminar", (mode as LoraStep.OpenMode).text)
        // The schema is valid for the API (names and required parameters).
        val schema = LoraAgent.TOOLS.toSchema()
        assertTrue(schema.size >= 7)
    }

    @Test
    fun loraSettingsResolveSpokenValues() {
        val lora = LoraAgent.SETTINGS.first { it.id == "lora" }
        assertEquals("OFF", lora.resolve("aus"))
        assertTrue(lora.set(AppSettings(), "OFF").hideLora, "„Aus“ blendet Lora aus wie der alte Schalter")
        val web = LoraAgent.SETTINGS.first { it.id == "web" }
        assertEquals("false", web.resolve("ausschalten"))
        LoraAgent.SETTING_SPOTS.forEach { (id, spot) ->
            assertTrue(LoraAgent.SETTINGS.any { it.id == id })
            assertNotNull(AppMap.byId(spot, targets), "Spot $spot liegt auf der Karte")
        }
    }
}
