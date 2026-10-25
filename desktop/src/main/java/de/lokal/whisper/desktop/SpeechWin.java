package de.lokal.whisper.desktop;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import de.lokal.whisper.Util;

/** Small Windows functions for the speech recognition (via JNA); harmless on other systems. */
public final class SpeechWin {
    public static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private SpeechWin() {}

    interface K32 extends StdCallLibrary {
        K32 I = Native.load("kernel32", K32.class, W32APIOptions.DEFAULT_OPTIONS);
        int SetThreadExecutionState(int flags);
        boolean SetPriorityClass(WinNT.HANDLE process, int priorityClass);
        WinNT.HANDLE GetCurrentProcess();
    }

    static final int ES_CONTINUOUS = 0x80000000, ES_SYSTEM_REQUIRED = 0x00000001;
    static final int NORMAL_PRIORITY_CLASS = 0x20, BELOW_NORMAL_PRIORITY_CLASS = 0x4000;

    /** Number of real cores (without hyper-threading), 0 if unknown. */
    public static int physicalCores() {
        if (!WINDOWS) return 0;
        try {
            int n = 0;
            for (WinNT.SYSTEM_LOGICAL_PROCESSOR_INFORMATION i : Kernel32Util.getLogicalProcessorInformation())
                if (i.relationship == WinNT.LOGICAL_PROCESSOR_RELATIONSHIP.RelationProcessorCore) n++;
            return n;
        } catch (Throwable t) {
            Util.err("Rechenkerne", t);
            return 0;
        }
    }

    public static String cpuName() {
        if (!WINDOWS) return System.getProperty("os.arch");
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
                    "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "ProcessorNameString").trim();
        } catch (Throwable t) { return ""; }
    }

    /** Lower priority while transcribing, so Office and the browser stay smooth. */
    public static void setGentle(boolean gentle) {
        if (!WINDOWS) return;
        try { K32.I.SetPriorityClass(K32.I.GetCurrentProcess(), gentle ? BELOW_NORMAL_PRIORITY_CLASS : NORMAL_PRIORITY_CLASS); }
        catch (Throwable t) { Util.err("Priorität", t); }
    }

    private static volatile boolean awake;
    private static Thread awakeThread;

    /**
     * Prevents sleep while transcribing or recording (the screen may still turn off). The setting is per thread,
     * hence an own thread.
     */
    public static synchronized void keepAwake(boolean on) {
        awake = on;
        if (!WINDOWS || awakeThread != null) return;
        awakeThread = new Thread(() -> {
            boolean last = false;
            while (true) {
                boolean want = awake;
                try {
                    if (want != last || want) K32.I.SetThreadExecutionState(want ? ES_CONTINUOUS | ES_SYSTEM_REQUIRED : ES_CONTINUOUS);
                    last = want;
                } catch (Throwable t) { Util.err("Energiesparen", t); return; }
                try { Thread.sleep(20_000); } catch (InterruptedException e) { return; }
            }
        }, "keep-awake");
        awakeThread.setDaemon(true);
        awakeThread.start();
    }
}
