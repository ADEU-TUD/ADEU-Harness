package io.github.thepytales.llmhub.local

/**
 * Geräteprofil (AP-105): reading what the system tells about memory and processor, as pure functions over the raw
 * data (firmware table, /proc/cpuinfo, sysctl), so they are tested without the hardware.
 */
object HardwareInfo {
    /** Memory from the SMBIOS tables: speed in MT/s, bus width in bits of the populated modules, type and size. */
    data class Memory(val type: String, val mts: Int, val busBits: Int, val modules: Int, val gb: Double) {
        /** Theoretical bandwidth in GB/s. */
        val gbs: Double get() = mts * busBits / 8.0 / 1000.0
        fun detail(): String = buildString {
            append(type.ifBlank { "RAM" })
            if (mts > 0) append('-').append(mts)
            append(" · ").append(busBits).append(" Bit")
            append(" · ").append(modules).append(if (modules == 1) " Modul" else " Module")
        }
    }

    private val MEMORY_TYPES = mapOf(
        0x18 to "DDR3", 0x1A to "DDR4", 0x1B to "LPDDR", 0x1C to "LPDDR2", 0x1D to "LPDDR3", 0x1E to "LPDDR4",
        0x22 to "DDR5", 0x23 to "LPDDR5",
    )

    /**
     * Parses the raw SMBIOS data as Windows returns it (GetSystemFirmwareTable 'RSMB': 8 bytes header, then the
     * structures) – or the bare table when [header] is false (Linux /sys/firmware/dmi/tables/DMI). Type 17 (Memory
     * Device): size, data width, type, configured speed. Consumer boards have two channels of 64 bits: four modules on
     * them are still 128 bits, so the width is capped at 128 bits unless there are more than four modules (workstations,
     * servers) – soldered LPDDR lists one entry per 16/32-bit channel and adds up correctly.
     */
    fun smbiosMemory(raw: ByteArray, header: Boolean = true, cores: Int = 8): Memory? {
        var i = if (header) 8 else 0
        val modules = mutableListOf<Triple<String, Int, Int>>() // type, speed, width
        var totalMb = 0L
        while (i + 4 <= raw.size) {
            val type = raw[i].toInt() and 0xff
            val len = raw[i + 1].toInt() and 0xff
            if (len < 4 || i + len > raw.size) break
            if (type == 127) break
            if (type == 17 && len >= 0x17) {
                fun w(o: Int) = if (o + 1 < len) (raw[i + o].toInt() and 0xff) or ((raw[i + o + 1].toInt() and 0xff) shl 8) else 0
                fun d(o: Int) = if (o + 3 < len) (w(o).toLong() or (w(o + 2).toLong() shl 16)) else 0L
                val sizeField = w(0x0C)
                val mb = when {
                    sizeField == 0 || sizeField == 0xFFFF -> 0L
                    sizeField == 0x7FFF -> d(0x1C) and 0x7FFFFFFF
                    sizeField and 0x8000 != 0 -> (sizeField and 0x7FFF) / 1024L
                    else -> sizeField.toLong()
                }
                if (mb > 0) {
                    val width = w(0x0A).takeIf { it in 8..128 } ?: 64
                    val memType = MEMORY_TYPES[raw[i + 0x12].toInt() and 0xff].orEmpty()
                    var speed = w(0x20).takeIf { len > 0x21 && it in 1..0xFFFE } ?: 0
                    if (len > 0x21 && w(0x20) == 0xFFFF) speed = d(0x58).toInt()
                    if (speed == 0) speed = w(0x15).let { if (it == 0xFFFF) d(0x54).toInt() else it }
                    modules += Triple(memType, speed, width)
                    totalMb += mb
                }
            }
            // Skip the formatted part and the strings (ending with two zero bytes).
            var j = i + len
            while (j + 1 < raw.size && !(raw[j] == 0.toByte() && raw[j + 1] == 0.toByte())) j++
            i = j + 2
        }
        if (modules.isEmpty()) return null
        val speed = modules.map { it.second }.filter { it > 0 }.minOrNull() ?: 0
        val type = modules.groupingBy { it.first }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        val sum = modules.sumOf { it.third }
        val soldered = type.startsWith("LP")
        val bits = if (soldered || modules.size > 4 || cores > 16) sum else sum.coerceAtMost(128)
        return Memory(type, speed, bits, modules.size, totalMb / 1024.0)
    }

    /**
     * The big cores of a phone (AP-116) from the highest clock of each core (cpufreq/cpuinfo_max_freq in kHz): those
     * at 75 % of the fastest or more. 0 when nothing could be read.
     */
    fun bigCores(maxFreqs: List<Long>): Int {
        val top = maxFreqs.filter { it > 0 }.maxOrNull() ?: return 0
        return maxFreqs.count { it >= top * 0.75 }
    }

    /** Instruction sets from /proc/cpuinfo (Linux, Android): x86 "flags", ARM "Features". */
    fun cpuFeatures(cpuinfo: String): Set<String> {
        val words = cpuinfo.lineSequence()
            .filter { l -> l.startsWith("flags") || l.startsWith("Features") }
            .flatMap { it.substringAfter(':').trim().split(Regex("""\s+""")) }.toSet()
        return buildSet {
            if ("avx2" in words) add("avx2")
            if ("avx512f" in words) add("avx512")
            if ("amx_tile" in words || "amx_int8" in words) add("amx")
            if ("asimd" in words || "neon" in words) add("neon")
            if ("asimddp" in words) add("dotprod")
            if ("i8mm" in words) add("i8mm")
            if ("sve" in words) add("sve")
        }
    }

    /** The graphics cores from `ioreg -rd1 -c AGXAccelerator` ("gpu-core-count" = 40), 0 if not there. */
    fun appleGpuCores(ioreg: String): Int =
        Regex(""""gpu-core-count"\s*=\s*(\d+)""").find(ioreg)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
