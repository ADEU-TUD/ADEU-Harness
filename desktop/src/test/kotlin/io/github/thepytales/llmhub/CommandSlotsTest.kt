package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.CommandSlots
import io.github.thepytales.llmhub.agent.LoraAgent
import io.github.thepytales.llmhub.agent.ModelRouter
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.AppSettings
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Werte aus dem Wunsch (AP-71): with the right label given, the rules of [CommandSlots] must read the values the
 * Messkorpus expects (on/off, pages, column, folder, date …). And the mixed mode: where a request runs.
 */
class CommandSlotsTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun same(a: String?, b: String) = a != null && AppMap.norm(a).replace(" ", "") == AppMap.norm(b).replace(" ", "")

    /** Whether the values read from the wish of [s] are the expected ones, for its first label. */
    private fun valuesRight(s: CommandCorpus.Sample): Boolean {
        val label = s.gold.first()
        return when {
            label.startsWith("stell:") -> {
                val setting = LoraAgent.SETTINGS.first { it.id == label.removePrefix("stell:") }
                CommandSlots.settingValue(setting, s.text, setting.get(AppSettings())) == s.values["wert"]
            }
            label.startsWith("tu:") -> {
                val args = CommandSlots.args(label.removePrefix("tu:"), s.text) ?: return false
                s.values.all { (k, v) -> same(args[k], v) }
            }
            label == "lora:frist" -> CommandSlots.date(s.text, today)?.toString() == s.values["datum"]
            label == "lora:chats" -> same(CommandSlots.chatQuery(s.text), s.values.getValue("suche"))
            else -> true
        }
    }

    @Test
    fun valuesOfTheMesskorpusAreRead() {
        val report = StringBuilder()
        for (section in listOf("entwicklung", "pruef")) {
            val with = CommandCorpus.load(section).filter { it.values.isNotEmpty() }
            val wrong = with.filterNot(::valuesRight)
            report.appendLine("Werte, $section: ${with.size - wrong.size} von ${with.size} richtig")
            // Error lists for the development part only.
            if (section == "entwicklung") wrong.forEach { s ->
                val label = s.gold.first()
                val got = if (label.startsWith("tu:")) CommandSlots.args(label.removePrefix("tu:"), s.text).toString() else ""
                report.appendLine("    ${s.id}  ${s.text}  erwartet ${s.values}  $got")
            }
            assertTrue(wrong.size * 10 <= with.size, "$section: ${wrong.size} von ${with.size} Werten falsch\n$report")
        }
        File("build/befehle").mkdirs()
        File("build/befehle/werte.txt").writeText(report.toString())
        println(report)
    }

    @Test
    fun aWishWithoutARequiredValueIsPassedOn() {
        assertNull(CommandSlots.args("pdf.delete_pages", "lösch die letzte seite"))
        assertNull(CommandSlots.args("files.move", "verschieben"))
        assertNull(CommandSlots.args("sheet.sort", "sortieren"))
        assertEquals(mapOf("seiten" to "2,5-7"), CommandSlots.args("pdf.delete_pages", "Entferne die Seiten 2 und 5-7"))
        assertEquals("alle", CommandSlots.args("pdf.rotate", "dreh alles um 90 Grad")?.get("seiten"))
        assertEquals("90", CommandSlots.args("pdf.rotate", "dreh alles um 90 Grad")?.get("grad"))
        // Without a file in the wish the action takes the selection.
        assertEquals(emptyMap(), CommandSlots.args("pdf.merge", "füge die PDFs zusammen"))
        assertEquals("Antrag_final.pdf", CommandSlots.args("pdf.compress", "verkleinere Antrag_final.pdf")?.get("datei"))
    }

    @Test
    fun settingsNeedAClearValue() {
        fun value(id: String, wish: String, now: String? = null) = CommandSlots.settingValue(LoraAgent.SETTINGS.first { it.id == id }, wish, now)
        assertEquals("DARK", value("theme", "Mach mal dunkel"))
        assertEquals("LIGHT", value("theme", "Dunkelmodus aus"))
        assertNull(value("theme", "Helligkeit"))
        assertEquals("1.3", value("text_size", "Schrift größer", now = "1.15"))
        assertEquals("1.0", value("text_size", "Schrift kleiner", now = "1.15"))
        assertEquals("LOCAL", value("lora_place", "Lora soll nichts mehr an den Server schicken"))
        assertEquals("SERVER", value("lora_place", "nur Server"))
        assertEquals("false", value("web", "keine Websuche mehr"))
    }

    @Test
    fun mixedModeDecidesWhereARequestRuns() {
        fun place(mode: ModelRouter.Mode, simple: Boolean, text: String, good: String? = "local/klein", any: String? = "local/klein", server: Boolean = true, images: Int = 0, device: Boolean = false) =
            ModelRouter.place(mode, simple, text, images, 0, good, any, server, device)
        val mixed = ModelRouter.Mode.MIXED
        // Short and simple with a model that runs well here: on this device.
        assertEquals(ModelRouter.Place.LOCAL, place(mixed, true, "Formuliere höflicher: Schick mir das bis morgen").place)
        // Not sure that it is simple, pictures, the web, long texts, no fitting model: the server as before.
        assertEquals(ModelRouter.Place.SERVER, place(mixed, false, "Formuliere höflicher: Schick mir das bis morgen").place)
        assertEquals(ModelRouter.Place.SERVER, place(mixed, true, "Was ist auf dem Bild?", images = 1).place)
        assertEquals(ModelRouter.Place.SERVER, place(mixed, true, "Such im Web nach den Terminen der Tagung").place)
        assertEquals(ModelRouter.Place.SERVER, place(mixed, true, "Fasse zusammen: " + "Text ".repeat(200)).place)
        assertEquals(ModelRouter.Place.SERVER, place(mixed, true, "Was ist Validität?", good = null).place)
        // Without a key the device is better than nothing.
        assertEquals(ModelRouter.Place.LOCAL, place(mixed, false, "Vergleiche zwei Theorien ausführlich", good = null, server = false).place)
        // „Nur lokal“ never asks a server, „Nur Server“ never a local model – except in projects that stay on the device.
        assertEquals(ModelRouter.Place.LOCAL, place(ModelRouter.Mode.LOCAL, false, "Recherchiere aktuelle Studien").place)
        assertEquals(ModelRouter.Place.NONE, place(ModelRouter.Mode.LOCAL, false, "Hallo", good = null, any = null).place)
        assertEquals(ModelRouter.Place.SERVER, place(ModelRouter.Mode.SERVER, true, "Was ist Validität?").place)
        assertEquals(ModelRouter.Place.LOCAL, place(ModelRouter.Mode.SERVER, true, "Was ist Validität?", device = true).place)
        assertEquals(ModelRouter.Mode.MIXED, ModelRouter.Mode.of("unbekannt"))
        assertEquals(ModelRouter.Mode.MIXED, ModelRouter.Mode.of(AppSettings().loraPlace))
    }
}
