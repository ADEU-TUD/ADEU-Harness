package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.PiiFeature
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.PrivacyPage
import io.github.thepytales.llmhub.ui.PrivacySection
import io.github.thepytales.llmhub.ui.Spotlight
import io.github.thepytales.llmhub.ui.tr
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Einstellungen › Datenschutz (AP-103): the page as a picture (build/privacy-page.png) and its height – it has to fit
 * one screen without opening anything.
 */
@OptIn(ExperimentalTestApi::class)
class PrivacyPageTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-datenschutz").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    private fun ui(block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(760 * 2f, 2400 * 2f), Density(2f)) {
            mainClock.autoAdvance = false
            block()
        }

    private fun SkikoComposeUiTest.settle() {
        repeat(6) {
            Thread.sleep(200)
            mainClock.advanceTimeBy(300)
        }
        waitForIdle()
    }

    private fun SkikoComposeUiTest.page(name: String, style: String = "ADEU"): Int {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                LlmHubTheme(AppSettings(appStyle = style)) {
                    Box(Modifier.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopStart) {
                        Column(
                            Modifier.width(720.dp).wrapContentHeight().background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("shot"),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { PrivacySection(c) }
                    }
                }
            }
        }
        settle()
        return save(name)
    }

    private fun SkikoComposeUiTest.save(name: String): Int {
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        File("build").mkdirs()
        ImageIO.write(img, "png", File("build/$name"))
        return img.height / 2
    }

    private fun SkikoComposeUiTest.has(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun SkikoComposeUiTest.open(title: String) {
        onAllNodesWithText(title).onFirst().performClick()
        settle()
    }

    /** Fits one screen without opening anything; the status is on top. */
    @Test
    fun pageFitsOneScreenAndShowsTheStatus() = ui {
        c.settings.update { it.copy(privacyOpen = "") }
        val height = page("privacy-page.png")
        println("Datenschutzseite: $height dp hoch")
        // A laptop screen of 1366 × 768 shows about 640 dp below the title bar; the page fits a full-HD screen (≈ 900 dp).
        assertTrue(height <= 900, "Seite $height dp hoch")
        assertTrue(has("Aktiv: Regeln"))
        assertTrue(has("Postfach nur auf diesem Gerät"))
        // Folded parts show only their line.
        assertFalse(has("Werden immer ersetzt"))
        assertFalse(has("E-Mail-Adressen"))
    }

    /** Every switch of PiiFeature is reachable (Feineinstellungen and the hints), and opening is remembered. */
    @Test
    fun everySwitchIsReachableAndTheFoldsAreKept() = ui {
        c.settings.update { it.copy(privacyOpen = "") }
        page("privacy-open.png")
        open("Feineinstellungen")
        assertEquals(setOf(PrivacyPage.DETAILS), PrivacyPage.open(c.settings.current))
        PiiFeature.entries.filter { it !in PrivacyPage.HINTS }.map { it.group }.distinct().forEach { open(it) }
        open(PrivacyPage.SENDING_GROUP)
        PiiFeature.entries.forEach { f -> assertTrue(has(f.title), "${f.name} fehlt") }
        save("privacy-open.png")
        // The open parts are stored in the settings (they stay open after a restart).
        assertTrue(c.settings.current.privacyOpen.split(',').containsAll(listOf(PrivacyPage.DETAILS, "group:Hinweise beim Senden", "group:Kontaktdaten")))
        // A switch here changes the stored value.
        onAllNodesWithText(PiiFeature.PLATE.title).onFirst().performClick()
        settle()
        assertTrue(PiiFeature.PLATE in c.settings.current.pseudonymOffFeatures)
        assertTrue(has(tr("{0} von {1} Einzelschaltern an", 21, 22)))
        c.settings.update { it.copy(pseudonymOff = "", privacyOpen = "") }
    }

    /** Lora's frame on a setting in a folded part opens it (AP-41); the main switch is in the status card. */
    @Test
    fun loraFindsEverySpotOfThePage() = ui {
        c.settings.update { it.copy(privacyOpen = "") }
        val spots = AppMap.spotIds.filter { it.startsWith("privacy.") }
        assertEquals(setOf("privacy.pseudonymize", "privacy.never"), spots.toSet())
        page("privacy-spot.png")
        assertFalse(has("Werden immer ersetzt"))
        Spotlight.show("privacy.never")
        settle()
        assertTrue(has("Werden immer ersetzt"), "Meine Listen öffnet sich für den Leuchtrahmen")
        assertTrue(Spotlight.bounds.containsKey("privacy.never") && Spotlight.bounds.containsKey("privacy.pseudonymize"))
        Spotlight.show(null)
        assertEquals("privacy.pseudonymize", LoraAgent.SETTING_SPOTS["pseudonymize"])
    }

    /** With the protection off the parts are greyed out and the status says so; the high-contrast style works. */
    @Test
    fun offAndHighContrast() = ui {
        c.settings.update { it.copy(pseudonymize = false, privacyOpen = "") }
        page("privacy-off-contrast.png", style = "CONTRAST")
        assertTrue(has("Schutz aus – nichts wird ersetzt."))
        assertTrue(has("Aus – Namen, Kontaktdaten und Nummern gehen unverändert an den Server."))
        c.settings.update { it.copy(pseudonymize = true) }
    }

    @Test
    fun statusLineAndFoldsWithoutTheScreen() {
        val s = AppSettings(pseudonymThorough = true, pseudonymAuto = false)
        assertEquals("Aktiv: Regeln · Lora Namensschutz nicht geladen · öffentliche Quellen bleiben · Gründlich an", PrivacyPage.status(s, modelInstalled = false))
        assertEquals("Schutz aus – nichts wird ersetzt.", PrivacyPage.status(s.copy(pseudonymize = false), true))
        val opened = PrivacyPage.toggle(PrivacyPage.toggle(AppSettings(), PrivacyPage.TRY), PrivacyPage.LISTS)
        assertEquals("lists,try", opened.privacyOpen)
        assertEquals(setOf("try"), PrivacyPage.open(PrivacyPage.toggle(opened, PrivacyPage.LISTS)))
        assertEquals(2, PrivacyPage.count("Anna\n\n# Kommentar\nBert\n"))
        // Every group name has an English text.
        PrivacyPage.GROUPS.forEach { g -> assertTrue(g in io.github.thepytales.llmhub.ui.I18n.EN, g) }
        assertEquals(PiiFeature.entries.map { it.group }.toSet(), PrivacyPage.GROUPS.toSet())
    }
}
