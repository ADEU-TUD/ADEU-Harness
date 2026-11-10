package io.github.thepytales.llmhub

import android.content.Context
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.LoraStep
import io.github.thepytales.llmhub.data.LoraCorrections
import io.github.thepytales.llmhub.ui.LoraHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lora lernt aus Korrekturen (AP-72): the list on this device, what may be learned, the Tresor, the export for
 * tools/befehle – and Lora carrying out a corrected wish the second time without a language model.
 */
class LoraCorrectionsTest {
    private fun container(): AppContainer {
        val base = Files.createTempDirectory("adeu-korrekturen").toFile()
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        return AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
    }

    @Test
    fun pairsAreKeptOnTheDeviceAndFoundAgain() {
        val c = container()
        assertTrue(LoraCorrections.learn(c, "Wo liegt mein Krempel?", "app", "ort:area.files"))
        // Case, accents, punctuation and spaces do not matter.
        assertEquals("ort:area.files", LoraCorrections.meant(c, "wo  liegt mein krempel", "app"))
        assertEquals("ort:area.files", LoraCorrections.meant(c, "Wo liegt mein Krempel", "pdf"), "a place counts from every area")
        assertEquals(2, LoraCorrections.all(c).single().used, "counted each time it is used")
        // An action of a tool only where that tool is open.
        LoraCorrections.learn(c, "nach punkten", "sheet", "tu:sheet.sort")
        assertEquals("tu:sheet.sort", LoraCorrections.meant(c, "Nach Punkten", "sheet"))
        assertNull(LoraCorrections.meant(c, "Nach Punkten", "doc"))
        // A newer correction of the same wish replaces the older one.
        LoraCorrections.learn(c, "wo liegt mein krempel", "app", "ort:place.files.search")
        assertEquals("ort:place.files.search", LoraCorrections.meant(c, "Wo liegt mein Krempel?", "app"))
        assertEquals(2, LoraCorrections.all(c).size)
        // Deletable one by one and all at once.
        LoraCorrections.remove(c, LoraCorrections.all(c).first { it.label == "tu:sheet.sort" })
        assertEquals(1, LoraCorrections.all(c).size)
        LoraCorrections.clear(c)
        assertTrue(LoraCorrections.all(c).isEmpty())
    }

    @Test
    fun nothingInTheTresorNoFreeRequestsAndSwitchable() {
        val c = container()
        assertFalse(LoraCorrections.learn(c, "wo liegt mein krempel", "app", "ort:area.files", vault = true))
        assertFalse(LoraCorrections.learn(c, "schreib mir ein gedicht", "app", "frei.einfach"))
        assertFalse(LoraCorrections.learn(c, "x", "app", "ort:area.files"))
        assertTrue(LoraCorrections.all(c).isEmpty())
        c.settings.update { it.copy(loraLearn = false) }
        assertFalse(LoraCorrections.learn(c, "wo liegt mein krempel", "app", "ort:area.files"))
        c.settings.update { it.copy(loraLearn = true) }
        assertTrue(LoraCorrections.learn(c, "wo liegt mein krempel", "app", "ort:area.files"))
        c.settings.update { it.copy(loraLearn = false) }
        assertNull(LoraCorrections.meant(c, "wo liegt mein krempel", "app"), "switched off: not used either")
    }

    @Test
    fun undoneMeansNotWithoutAsking() {
        val c = container()
        assertTrue(LoraCorrections.undone(c, "mach dunkel", "app", "stell:theme"))
        assertTrue(LoraCorrections.refused(c, "Mach dunkel!", "app", "stell:theme"))
        assertFalse(LoraCorrections.refused(c, "mach dunkel", "app", "stell:font_scale"))
        assertNull(LoraCorrections.meant(c, "mach dunkel", "app"), "an undone action is not meant")
        // A later choice of the same action lifts it.
        LoraCorrections.learn(c, "mach dunkel", "app", "stell:theme")
        assertFalse(LoraCorrections.refused(c, "mach dunkel", "app", "stell:theme"))
    }

    @Test
    fun theListStaysSmall() {
        val c = container()
        repeat(LoraCorrections.MAX + 20) { LoraCorrections.learn(c, "wunsch nummer $it", "app", "ort:area.files") }
        assertEquals(LoraCorrections.MAX, LoraCorrections.all(c).size)
        assertNotNull(LoraCorrections.meant(c, "wunsch nummer ${LoraCorrections.MAX + 19}", "app"), "the newest stay")
    }

    @Test
    fun exportIsTrainingDataForToolsBefehle() {
        val c = container()
        LoraCorrections.learn(c, "Sortier das nach Punkten", "sheet", "tu:sheet.sort")
        LoraCorrections.learn(c, "wo liegt mein krempel", "app", "ort:area.files")
        LoraCorrections.undone(c, "mach dunkel", "app", "stell:theme")
        val lines = LoraCorrections.export(LoraCorrections.all(c)).trim().lines()
        assertEquals(2, lines.size, "undone pairs say what was not meant – they stay out")
        val first = Json.parseToJsonElement(lines.first()).jsonObject
        assertEquals("sortier das nach punkten", first.getValue("text").jsonPrimitive.content)
        assertEquals("tabelle", first.getValue("bereich").jsonPrimitive.content, "the area names of tools/befehle")
        assertEquals("tu:sheet.sort", first.getValue("label").jsonPrimitive.content)
        // Every label must be one tools/befehle knows.
        val known = CommandCorpus.dir().parentFile.resolve("etiketten.json").readText()
        lines.forEach { l -> assertTrue(known.contains("\"" + Json.parseToJsonElement(l).jsonObject.getValue("label").jsonPrimitive.content + "\"")) }
    }

    @Test
    fun correctionsAreRecognized() {
        assertEquals("dateien", LoraAgent.correction("Nein, ich meinte Dateien"))
        assertEquals("die tabelle sortieren", LoraAgent.correction("nee ich meinte eigentlich: die Tabelle sortieren."))
        assertEquals("einstellungen", LoraAgent.correction("Ich meinte Einstellungen"))
        assertEquals("the settings", LoraAgent.correction("No, I meant the settings"))
        assertEquals("pdf zusammenfügen".let { io.github.thepytales.llmhub.data.AppMap.norm(it) }, LoraAgent.correction("Falsch, ich wollte PDF zusammenfügen!"))
        assertNull(LoraAgent.correction("nein danke"))
        assertNull(LoraAgent.correction("Ich meine, wir sollten das anders machen"))
        assertNull(LoraAgent.correction("Was meinte Kant mit Aufklärung?"))
    }

    @Test
    fun aCorrectedWishWorksTheSecondTimeWithoutALanguageModel() = runBlocking {
        val c = container()
        LoraHelper.route.value = "chat"
        val agent = LoraAgent(c)
        // No API key, no command model: Lora only searches in the app.
        val first = agent.ask("wo liegt mein krempel")
        assertTrue(first.steps.none { it is LoraStep.Navigate && it.target.id == "area.files" })
        // „Nein, ich meinte Dateien“: the rest is answered on the device and remembered for the wish before.
        val fix = agent.ask("Nein, ich meinte Dateien", history = listOf("wo liegt mein krempel" to first.text))
        assertEquals("area.files", (fix.steps.single() as LoraStep.Navigate).target.id)
        assertTrue(fix.text.contains("merke ich mir"), fix.text)
        assertEquals("ort:area.files", LoraCorrections.all(c).single().label)
        // The second time: carried out at once, on the device.
        val again = agent.ask("Wo liegt mein Krempel?")
        assertEquals("area.files", (again.steps.single() as LoraStep.Navigate).target.id)
        assertTrue(again.local)
        assertEquals("ort:area.files", again.understood?.label)
        // A button under „Meinst du … oder …?“ (AP-73) counts as a correction, too.
        val step = LoraStep.Choose("bring mich zu den einstellungen", listOf(LoraStep.Choose.Choice("ort:area.settings", "Einstellungen"), LoraStep.Choose.Choice("ort:area.files", "Dateien")))
        val chosen = agent.chosen(step, "ort:area.settings")
        assertEquals("settings", (chosen.steps.single() as LoraStep.Navigate).target.route)
        assertEquals("ort:area.settings", LoraCorrections.meant(c, "Bring mich zu den Einstellungen", "app"))
    }
}
