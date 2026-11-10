package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.local.DeviceBench
import io.github.thepytales.llmhub.local.Hardware
import io.github.thepytales.llmhub.local.HardwareInfo
import io.github.thepytales.llmhub.local.LocalCatalog
import io.github.thepytales.llmhub.local.LocalModel
import io.github.thepytales.llmhub.local.SpeedForecast
import io.github.thepytales.llmhub.local.SpeedForecast.Sample
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Geräteprofil und Messung (AP-105): reading memory and instruction sets, the forecast of writing and reading speed per
 * model, and its calibration by measurements on the device. Real device measurements (five device classes, ±30 %) are made
 * by the main user with *Gerät messen* and *Messen*; these tests check the arithmetic.
 */
class SpeedForecastTest {
    private fun model(id: String) = LocalCatalog.get(id)!!

    // ── SMBIOS (Windows: GetSystemFirmwareTable) ─────────────────────────────────────────────────────────────

    /** One type-17 structure (SMBIOS 3.x layout) with its string section. */
    private fun dimm(sizeMb: Int, width: Int, type: Int, configured: Int, speed: Int = configured): ByteArray {
        val len = 0x5C
        val b = ByteArray(len)
        b[0] = 17; b[1] = len.toByte()
        fun w(o: Int, v: Int) { b[o] = (v and 0xff).toByte(); b[o + 1] = (v shr 8 and 0xff).toByte() }
        w(0x08, width); w(0x0A, width); w(0x0C, sizeMb)
        b[0x12] = type.toByte()
        w(0x15, speed); w(0x20, configured)
        return b + "DIMM\u0000\u0000".toByteArray()
    }

    private fun table(vararg s: ByteArray) = ByteArray(8) + s.fold(ByteArray(0)) { a, x -> a + x } + byteArrayOf(127, 4, 0, 0, 0, 0)

    @Test
    fun readsMemoryFromTheFirmwareTable() {
        val ddr5 = HardwareInfo.smbiosMemory(table(dimm(16384, 64, 0x22, 5600), dimm(16384, 64, 0x22, 5600)))!!
        assertEquals("DDR5", ddr5.type)
        assertEquals(89.6, ddr5.gbs, 0.01)
        assertEquals(32.0, ddr5.gb, 0.01)
        // Four modules on two channels are still 128 bits; an empty slot does not count.
        val ddr4 = HardwareInfo.smbiosMemory(table(dimm(8192, 64, 0x1A, 3200), dimm(8192, 64, 0x1A, 3200), dimm(8192, 64, 0x1A, 3200), dimm(8192, 64, 0x1A, 3200), dimm(0, 64, 0x1A, 0)))!!
        assertEquals(51.2, ddr4.gbs, 0.01)
        assertEquals(4, ddr4.modules)
        // One module: single channel, half the bandwidth (the typical cheap office PC).
        assertEquals(25.6, HardwareInfo.smbiosMemory(table(dimm(16384, 64, 0x1A, 3200)))!!.gbs, 0.01)
        // Soldered LPDDR5X lists every 16-bit channel.
        val lp = HardwareInfo.smbiosMemory(table(*Array(8) { dimm(2048, 16, 0x23, 7500) }))!!
        assertEquals(120.0, lp.gbs, 0.01)
        assertTrue(lp.detail().startsWith("LPDDR5-7500 · 128 Bit"))
        // Configured speed below the maximum of the module: the configured one counts.
        assertEquals(76.8, HardwareInfo.smbiosMemory(table(dimm(16384, 64, 0x22, 4800, speed = 6000), dimm(16384, 64, 0x22, 4800, speed = 6000)))!!.gbs, 0.01)
        assertNull(HardwareInfo.smbiosMemory(ByteArray(8)))
    }

    @Test
    fun readsInstructionSetsAndAppleChips() {
        val x86 = "processor\t: 0\nflags\t\t: fpu sse sse2 avx avx2 fma avx512f avx512_vnni amx_tile\n"
        assertEquals(setOf("avx2", "avx512", "amx"), HardwareInfo.cpuFeatures(x86))
        val arm = "Features\t: fp asimd evtstrm aes crc32 asimddp i8mm sve\n"
        assertEquals(setOf("neon", "dotprod", "i8mm", "sve"), HardwareInfo.cpuFeatures(arm))
        assertEquals(40, HardwareInfo.appleGpuCores("""  | "gpu-core-count" = 40\n"""))
        assertEquals(400.0 to 40, SpeedForecast.appleChip("Apple M3 Max"))
        assertEquals(120.0 to 10, SpeedForecast.appleChip("Apple M4"))
        assertEquals(68.25 to 8, SpeedForecast.appleChip("Apple M1"))
        assertNotNull(SpeedForecast.appleChip("Apple M7 Max"), "unbekannte neue Chips: grobe Schätzung statt nichts")
        assertNull(SpeedForecast.appleChip("Intel Core i7"))
    }

    // ── Forecast ─────────────────────────────────────────────────────────────────────────────────────────────

    private val office = Hardware(phone = false, ramGb = 32.0, cores = 8, memBandwidthGbs = 51.2, cpuFeatures = setOf("avx2"))
    private val gaming = Hardware(phone = false, ramGb = 32.0, cores = 8, gpuName = "NVIDIA GeForce RTX 5080", vramGb = 16.0, memBandwidthGbs = 89.6, cpuFeatures = setOf("avx2"))
    private val mac = Hardware(phone = false, mac = true, ramGb = 38.6, cores = 12, gpuName = "Apple M3 Max (Metal)", vramGb = 25.5, chip = "Apple M3 Max", gpuCores = 40, memBandwidthGbs = 400.0, cpuFeatures = setOf("neon", "dotprod", "i8mm"))

    @Test
    fun forecastFollowsBandwidthAndActiveParameters() {
        val small = SpeedForecast.forecast(model("qwen3.5-2b"), office)
        val mid = SpeedForecast.forecast(model("qwen3.5-9b"), office)
        val moe = SpeedForecast.forecast(model("qwen3.6-35b-a3b"), office)
        assertTrue(small.wordsPerSec > mid.wordsPerSec * 2, "2B schreibt viel schneller als 9B")
        assertTrue(moe.wordsPerSec > mid.wordsPerSec, "Expertenmodell mit 3 Mrd. aktiven Parametern schreibt schneller als 9B")
        assertTrue(small.readWordsPerSec > moe.readWordsPerSec)
        // Ranges a person can check: a 9B model on an office PC writes a few words per second, on the RTX 5080 dozens.
        assertTrue(mid.wordsPerSec in 1.5..8.0, "9B auf dem Office-PC: ${mid.wordsPerSec}")
        val gpu = SpeedForecast.forecast(model("qwen3.5-9b"), gaming)
        assertTrue(gpu.gpu && gpu.wordsPerSec > 30, "9B auf der RTX 5080: ${gpu.wordsPerSec}")
        // Single channel halves writing.
        val single = SpeedForecast.forecast(model("qwen3.5-9b"), office.copy(memBandwidthGbs = 25.6))
        assertEquals(mid.wordsPerSec / 2, single.wordsPerSec, 0.01)
        assertEquals(SpeedForecast.Source.ESTIMATE, mid.source)
        // The time to the first word grows with the request.
        assertTrue(mid.firstWordMs(2000) > mid.firstWordMs(90))
    }

    @Test
    fun aMeasurementCalibratesAllOtherModelsOfTheSameKind() {
        // An invented device that is 30 % slower in writing and twice as fast in reading as the formula.
        val truth = { m: LocalModel -> Sample(m.id, false, SpeedForecast.rawWrite(m, office, false) * 0.7, SpeedForecast.rawRead(m, office, false) * 2.0, 0.55) }
        val samples = listOf(truth(model("qwen3.5-2b")))
        val f = SpeedForecast.forecast(model("gemma-4-12b"), office, samples)
        assertEquals(SpeedForecast.Source.CALIBRATED, f.source)
        assertEquals(SpeedForecast.rawWrite(model("gemma-4-12b"), office, false) * 0.7, f.writeTokS, 1e-6)
        assertEquals(SpeedForecast.rawRead(model("gemma-4-12b"), office, false) * 2.0, f.readTokS, 1e-6)
        assertEquals(0.55, f.wordsPerToken, 1e-9)
        // The measured model itself shows its measurement.
        assertEquals(SpeedForecast.Source.MEASURED, SpeedForecast.forecast(model("qwen3.5-2b"), office, samples).source)
        // A measurement on the processor does not change the forecast for the graphics.
        assertEquals(SpeedForecast.Source.ESTIMATE, SpeedForecast.forecast(model("qwen3.5-9b"), gaming, samples).source)
        // Leave one out: with two exact measurements each forecasts the other exactly.
        val two = samples + truth(model("gemma-4-e4b"))
        val checks = SpeedForecast.check(two, office)
        assertEquals(2, checks.size)
        assertTrue(checks.all { abs(it.deviation) < 1e-9 && it.withinTarget }, checks.toString())
    }

    @Test
    fun measurementsFromMessenCountAsSamples() {
        // AP-76 stores words per second; the median ratio is robust against one outlier.
        val s = listOf(
            Sample("qwen3.5-2b", false, SpeedForecast.rawWrite(model("qwen3.5-2b"), office, false) * 0.8),
            Sample("qwen3.5-4b", false, SpeedForecast.rawWrite(model("qwen3.5-4b"), office, false) * 0.9),
            Sample("gemma-4-e2b", false, SpeedForecast.rawWrite(model("gemma-4-e2b"), office, false) * 3.0),
        )
        assertEquals(0.9, SpeedForecast.calibrate(s, office, gpu = false).write!!, 1e-9)
    }

    /**
     * Plausibility on Apple chips against published llama.cpp values (discussion ggml-org/llama.cpp #4167, LLaMA 7B Q4_0,
     * writing 128 tokens, rounded; not re-checked here). They were used to set the shape of the Apple formula, so this is
     * no proof of the ±30 % target – that needs measurements on the devices themselves (*Gerät messen*).
     */
    @Test
    fun appleChipsAgreeWithPublishedValues() {
        val llama7b = LocalModel("ref-7b", "7B", "ref", "", "", "", "llama-7b-Q4_0.gguf", 3_820_000_000L, "", 5.0, 4096, false, 7.0)
        val published = listOf(
            "Apple M1" to 14.2, "Apple M1 Pro" to 36.4, "Apple M1 Max" to 61.2, "Apple M2" to 21.9, "Apple M2 Ultra" to 94.3,
            "Apple M3" to 21.3, "Apple M3 Max" to 66.3, "Apple M4" to 24.1, "Apple M4 Max" to 83.1,
        )
        for ((chip, tokS) in published) {
            val hw = mac.copy(chip = chip, memBandwidthGbs = SpeedForecast.appleChip(chip)!!.first, gpuName = "$chip (Metal)", vramGb = 20.0)
            val f = SpeedForecast.rawWrite(llama7b, hw, gpu = true)
            assertTrue(abs(f / tokS - 1) <= SpeedForecast.TARGET, "$chip: vorhergesagt %.1f, veröffentlicht %.1f".format(f, tokS))
        }
    }

    @Test
    fun readsTheTimingsOfLlamaServer() {
        val answer = """{"choices":[{"message":{"role":"assistant","content":"In der Sitzung wurden zwei zusätzliche Übungsgruppen beschlossen, ein Probelauf mit Portfolios vereinbart und die Tagung im Herbst geplant."}}],
            "timings":{"prompt_n":512,"prompt_ms":2100.5,"prompt_per_second":243.7,"predicted_n":32,"predicted_ms":2000.0,"predicted_per_second":16.0}}"""
        val t = DeviceBench.parseTimings(answer)
        assertEquals(243.7, t.readTokS, 1e-9)
        assertEquals(16.0, t.writeTokS, 1e-9)
        assertEquals(19.0 / 32, t.wordsPerToken!!, 1e-9)
    }

    @Test
    fun javaMeasuresSomeBandwidth() {
        val gbs = DeviceBench.javaBandwidth(phone = true, threads = 2)
        assertTrue(gbs > 0.5, "gemessen: $gbs GB/s")
    }
}
