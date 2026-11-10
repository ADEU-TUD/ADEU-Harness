package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.local.Fit
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.Purpose
import io.github.thepytales.llmhub.local.SpeedForecast
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Modellkatalog in feinen Stufen, nach Zweck (AP-106): every step (RAM 8–64 GB, graphics 4–24 GB, Apple chips, Android)
 * gets one model per purpose; licences, names „– Lora Edition“, fixed commits and checksums; the allrounder is the
 * standard and specialists only come with their purpose. Writes the table of the steps to build/modellkatalog.md (copied
 * into docs/MODELLKATALOG.md). The speeds are forecasts (AP-105); measured on the devices they are not yet.
 */
class LocalCatalogStepsTest {
    @Test
    fun everyModelHasAnAllowedLicenceAFixedSourceAndALoraEditionName() {
        val ids = LocalCatalog.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Ids doppelt")
        assertEquals(ids.size, LocalCatalog.models.map { it.file }.toSet().size, "Dateien doppelt")
        for (m in LocalCatalog.models) {
            assertTrue(m.license in setOf("Apache-2.0", "MIT"), "${m.id}: Lizenz ${m.license}")
            assertFalse("llama" in m.base.lowercase() || "llama" in m.name.lowercase(), "${m.id}: kein Llama")
            assertTrue(m.name.endsWith(" – Lora Edition"), m.name)
            assertTrue(m.commit.matches(Regex("[0-9a-f]{40}")), "${m.id}: Commit")
            assertTrue(m.sha256.matches(Regex("[0-9a-f]{64}")), "${m.id}: SHA-256")
            assertTrue(m.base.isNotBlank() && m.authors.isNotBlank(), "${m.id}: Original und Urheber")
            assertTrue(m.purposes.isNotEmpty() && m.tools, m.id)
            assertTrue(m.needGb > m.size / 1e9, "${m.id}: Speicher kleiner als die Datei")
            val card = LocalCatalog.card(m)
            assertTrue("Gewichte sind unverändert" in card && m.sha256 in card && m.base in card, card)
        }
        // Every purpose has a ladder from small to large.
        for (p in Purpose.entries) assertTrue(LocalCatalog.models.count { p in it.purposes } >= 3, "$p: zu wenige Modelle")
    }

    @Test
    fun everyStepHasAModelForEveryPurpose() {
        val rows = mutableListOf<String>()
        var slow = 0
        var cells = 0
        for ((step, hw) in LocalCatalog.steps()) {
            val cols = Purpose.entries.map { p ->
                val pick = assertNotNull(LocalCatalog.pick(p, hw), "$step / $p ohne Modell")
                assertTrue(LocalCatalog.fit(pick.model, hw) != Fit.TOO_BIG, "$step / $p: ${pick.model.id} passt nicht")
                assertTrue(p in pick.model.purposes)
                cells++
                if (!pick.fastEnough) slow++
                val f = pick.forecast
                "${pick.model.original} · " + SpeedForecast.words(f.wordsPerSec) + " W/s" + if (pick.fastEnough) "" else " ⚠"
            }
            rows += "| $step | " + cols.joinToString(" | ") + " |"
        }
        val table = buildString {
            appendLine("| Stufe | " + Purpose.entries.joinToString(" | ") { it.label } + " |")
            appendLine("| --- |" + " --- |".repeat(Purpose.entries.size))
            rows.forEach { appendLine(it) }
            appendLine()
            appendLine(String.format(Locale.GERMANY, "%d von %d Feldern erreichen die Vorhersage ≥ %.0f Wörter/s und erstes Wort ≤ %.0f s; ⚠ = schnellstes passendes Modell.",
                cells - slow, cells, LocalCatalog.MIN_WORDS_PER_SEC, LocalCatalog.MAX_FIRST_WORD_MS / 1000.0))
        }
        File("build").mkdirs()
        File("build/modellkatalog.md").writeText(table)
        println(table)
        // Graphics from 8 GB, Macs and DDR5 PCs reach the minimum speed for every purpose.
        for ((step, hw) in LocalCatalog.steps().filter { it.first.startsWith("Grafik 8") || it.first.startsWith("Grafik 1") || it.first.startsWith("Grafik 24") || it.first.startsWith("Mac") }) {
            for (p in Purpose.entries) assertTrue(LocalCatalog.pick(p, hw)!!.fastEnough, "$step / $p zu langsam")
        }
    }

    @Test
    fun theStrongerStepGetsTheStrongerModel() {
        val steps = LocalCatalog.steps().toMap()
        fun q(step: String, p: Purpose) = LocalCatalog.pick(p, steps.getValue(step))!!.model.quality
        for (p in Purpose.entries) {
            assertTrue(q("Grafik 24 GB", p) >= q("Grafik 8 GB", p), "$p: 24 GB ≥ 8 GB")
            assertTrue(q("Mac M3 Max 64 GB", p) >= q("Mac M2 16 GB", p), "$p: M3 Max ≥ M2")
            assertTrue(q("RAM 64 GB (DDR5)", p) >= q("RAM 16 GB (DDR4)", p), "$p: 64 GB ≥ 16 GB")
        }
        // 24 GB of graphics: the large dense models; a PC with 64 GB without graphics: the expert model (writes fast on the CPU).
        assertEquals("qwen3.6-27b", LocalCatalog.pick(Purpose.ALLROUND, steps.getValue("Grafik 24 GB"))!!.model.id)
        assertEquals("qwen3.6-35b-a3b", LocalCatalog.pick(Purpose.ALLROUND, steps.getValue("RAM 64 GB (DDR5)"))!!.model.id)
        assertEquals("qwen3-coder-30b-a3b", LocalCatalog.pick(Purpose.CODE, steps.getValue("Grafik 24 GB"))!!.model.id)
    }

    @Test
    fun theAllrounderIsTheStandardAndSpecialistsComeWithTheirPurpose() {
        val hw = LocalCatalog.steps().toMap().getValue("Grafik 16 GB")
        val only = LocalCatalog.picks(hw)
        assertEquals(listOf(Purpose.ALLROUND), only.map { it.purpose })
        val more = LocalCatalog.picks(hw, setOf(Purpose.ALLROUND, Purpose.CODE, Purpose.TEXT))
        assertEquals(Purpose.ALLROUND, more.first().purpose)
        assertTrue(more.any { Purpose.CODE in it.model.purposes && it.purpose == Purpose.CODE })
        // From the roles: Verwaltung → Postfach, Lehre/Forschung → Texte; chosen purposes win.
        assertEquals(setOf(Purpose.ALLROUND, Purpose.MAIL), LocalCatalog.purposes("", "ADMIN"))
        assertEquals(setOf(Purpose.ALLROUND, Purpose.TEXT), LocalCatalog.purposes("", "TEACHING,STUDY"))
        assertEquals(setOf(Purpose.ALLROUND, Purpose.CODE), LocalCatalog.purposes("CODE", "ADMIN"))
    }

    @Test
    fun measurementsChangeThePick() {
        // A slow office PC: measured slower than forecast → a smaller allrounder.
        val hw = Hardware(phone = false, ramGb = 16.0, cores = 8, memBandwidthGbs = 89.6, cpuFeatures = setOf("avx2"))
        val before = LocalCatalog.pick(Purpose.ALLROUND, hw)!!.model
        val m4 = LocalCatalog.get("qwen3.5-4b")!!
        val slow = listOf(SpeedForecast.Sample(m4.id, false, SpeedForecast.rawWrite(m4, hw, false) * 0.4))
        val after = LocalCatalog.pick(Purpose.ALLROUND, hw) { SpeedForecast.forecast(it, hw, slow) }!!.model
        assertTrue(after.quality < before.quality, "$before → $after")
    }
}
