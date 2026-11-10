package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalVision
import io.github.thepytales.llmhub.local.Purpose
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bilder verstehen lokal (AP-110): every model with an image part gets it from its own repository and commit, checked by
 * SHA-256; models of one size share it; the code specialists have none; the time per image is forecast (writes the table
 * to build/bildverstehen.md). Whether images are answered well and fast enough is measured on the device
 * (tools/bildverstehen/messen.py).
 */
class LocalVisionTest {
    @Test
    fun imagePartsComeFromTheModelsOwnSource() {
        val withImages = LocalCatalog.models.filter { it.projector != null }
        assertEquals(13, withImages.size)
        for (m in withImages) {
            val p = m.projector!!
            assertEquals(m.repo, p.repo, m.id)
            assertEquals(m.commit, p.commit, m.id)
            assertTrue(p.sha256.matches(Regex("[0-9a-f]{64}")) && p.size in 100_000_000L..1_500_000_000L, m.id)
            assertTrue(p.localName.startsWith("mmproj") || p.localName.contains("mmproj"), p.localName)
            assertFalse(LocalCatalog.models.any { it.file == p.localName }, "Name kollidiert mit einem Modell")
            assertTrue(LocalCatalog.info(m, vision = true).vision == true)
            assertTrue(LocalCatalog.info(m).vision == false)
        }
        // One file per repository: the 8-bit versions share the image part of their model; different repositories never clash.
        assertEquals(LocalCatalog.get("qwen3.5-4b")!!.projector, LocalCatalog.get("qwen3.5-4b-q8")!!.projector)
        val names = withImages.map { it.projector!! }.distinct().map { it.localName }
        assertEquals(names.size, names.toSet().size, names.toString())
        assertEquals("Qwen3.5-4B-mmproj-F16.gguf", LocalCatalog.get("qwen3.5-4b")!!.projector!!.localName)
        // The code specialists only read text.
        LocalCatalog.models.filter { it.purposes == setOf(Purpose.CODE) }.forEach { assertNull(it.projector, it.id) }
    }

    @Test
    fun theTimePerImageIsForecast() {
        val office = Hardware(phone = false, ramGb = 32.0, cores = 8, memBandwidthGbs = 51.2, cpuFeatures = setOf("avx2"))
        val gaming = office.copy(gpuName = "NVIDIA GeForce RTX 5080", vramGb = 16.0)
        val mac = Hardware(phone = false, mac = true, ramGb = 24.0, cores = 10, gpuName = "Apple M4 (Metal)", vramGb = 15.8, chip = "Apple M4",
            gpuCores = 10, memBandwidthGbs = 120.0, cpuFeatures = setOf("neon", "dotprod"))
        val rows = StringBuilder("| Modell | Office-PC 32 GB (CPU) | Gaming-PC RTX 5080 | Mac M4 24 GB |\n| --- | --- | --- | --- |\n")
        for (m in LocalCatalog.models.filter { it.projector != null && it.needGb <= 12 }) {
            val o = assertNotNull(LocalVision.secondsPerImage(m, office))
            val g = LocalVision.secondsPerImage(m, gaming)!!
            val a = LocalVision.secondsPerImage(m, mac)!!
            assertTrue(g < o, "${m.id}: Grafik schneller als Prozessor")
            rows.append("| ${m.original} | ").append(listOf(o, g, a).joinToString(" | ") { String.format(Locale.GERMANY, "%.0f s", it) }).append(" |\n")
        }
        File("build").mkdirs()
        File("build/bildverstehen.md").writeText(rows.toString())
        println(rows)
        assertNull(LocalVision.secondsPerImage(LocalCatalog.get("qwen2.5-coder-7b")!!, office))
    }
}
