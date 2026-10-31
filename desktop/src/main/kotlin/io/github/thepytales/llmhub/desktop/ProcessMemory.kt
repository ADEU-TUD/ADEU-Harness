package io.github.thepytales.llmhub.desktop

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary

/**
 * Memory of the whole app process on Windows (AP-04): the working set as the Task Manager shows it – Java heap plus
 * native parts (Skia, speech recognition, PDF rendering). Elsewhere null (MemoryMeter falls back to /proc).
 */
object ProcessMemory {
    @Suppress("FunctionName")
    private interface Psapi : StdCallLibrary {
        fun GetProcessMemoryInfo(process: WinNT.HANDLE, counters: Counters, cb: Int): Boolean
    }

    @Suppress("PropertyName", "unused")
    @Structure.FieldOrder(
        "cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize", "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage",
        "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage",
    )
    class Counters : Structure() {
        @JvmField var cb: Int = 0
        @JvmField var PageFaultCount: Int = 0
        @JvmField var PeakWorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var WorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var QuotaPeakPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var QuotaPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var QuotaPeakNonPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var QuotaNonPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var PagefileUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
        @JvmField var PeakPagefileUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
    }

    private val psapi: Psapi? by lazy {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) null
        else runCatching { Native.load("psapi", Psapi::class.java) }.getOrNull()
    }

    /** Working set in bytes, or null when not on Windows or the call fails. */
    fun workingSet(): Long? {
        val api = psapi ?: return null
        return runCatching {
            val c = Counters()
            c.cb = c.size()
            if (api.GetProcessMemoryInfo(Kernel32.INSTANCE.GetCurrentProcess(), c, c.size())) c.WorkingSetSize.toLong() else null
        }.getOrNull()
    }
}
