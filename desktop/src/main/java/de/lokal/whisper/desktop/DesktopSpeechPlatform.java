package de.lokal.whisper.desktop;

import de.lokal.whisper.Engine;
import de.lokal.whisper.Platform;

import org.json.JSONArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetAddress;

/**
 * Windows side of the speech recognition (also runs on Linux for development): ffmpeg reads every audio and video
 * format, Java Sound records. The built-in models come from the app's resources, the native libraries and ffmpeg
 * from the resources folder next to the app.
 */
public final class DesktopSpeechPlatform implements Platform {
    /** Implemented by the app (io.github.thepytales.llmhub.transcribe). */
    public interface Host {
        void emit(String json);
        void onActivity(Engine engine);
        void onJobFinished(Engine engine, Engine.Job job);
        int threads();
        String version();
    }

    private final File data, models, cache;
    private final Host host;
    private final int cores = Math.max(SpeechWin.physicalCores(), 0);

    /** {@code resources}: folder with native/ and tools/ (Compose: compose.application.resources.dir). */
    public DesktopSpeechPlatform(File root, File cache, File resources, Host host) {
        this.data = mk(new File(root, "data"));
        this.models = mk(new File(root, "models"));
        this.cache = mk(cache);
        this.host = host;
        if (resources != null) {
            Ffmpeg.resources = resources;
            File nat = new File(resources, "native");
            if (nat.isDirectory()) System.setProperty("sherpa_onnx.native.path", nat.getAbsolutePath());
        }
    }

    @Override public File dataDir() { return data; }
    @Override public File modelsDir() { return models; }
    @Override public File cacheDir() { return cache; }
    @Override public String deviceKind() { return "pc"; }

    @Override public String deviceName() {
        String host;
        try { host = InetAddress.getLocalHost().getHostName(); } catch (Exception e) { host = "PC"; }
        String cpu = SpeechWin.cpuName();
        return host + (cpu == null || cpu.isEmpty() ? "" : " · " + cpu);
    }

    @Override public String version() { return host.version(); }

    @Override public InputStream openBuiltin(String name) throws FileNotFoundException {
        InputStream in = DesktopSpeechPlatform.class.getClassLoader().getResourceAsStream("models/" + name);
        if (in == null) throw new FileNotFoundException("models/" + name);
        return in;
    }

    @Override public void emit(String json) { host.emit(json); }
    @Override public void onActivity(Engine engine) { host.onActivity(engine); }
    @Override public void onJobFinished(Engine engine, Engine.Job job) { host.onJobFinished(engine, job); }

    @Override public long decode(File src, File pcmOut, Progress progress, Cancel cancel) throws Exception {
        return Ffmpeg.decode(src, pcmOut, progress, cancel);
    }

    @Override public String encodeSpeech(File pcm, File dir, String baseName) { return Ffmpeg.encode(pcm, dir, baseName); }

    @Override public MicSource openMic(String deviceId) throws Exception {
        String test = System.getProperty("whisper.testMic");
        if (test != null && !test.isEmpty()) return new TestMic(new File(test), Double.parseDouble(System.getProperty("whisper.testMicSpeed", "1")));
        return new JavaSoundMic(deviceId);
    }

    /** Set number of cores, or automatically all real cores (at most 8). */
    @Override public int threads() {
        int set = host.threads();
        if (set > 0) return set;
        int logical = Runtime.getRuntime().availableProcessors();
        // Virtual machines (VDI, Citrix) sometimes report the cores wrongly; then at least half of the threads.
        int n = Math.max(cores, Math.max(1, logical / 2));
        return Math.max(1, Math.min(8, n));
    }

    /** Recording devices: [{id, name}]. */
    public static JSONArray microphones() { return JavaSoundMic.list(); }

    /** Path of ffmpeg (Windows: tools/ffmpeg.exe in the app), null if missing. */
    public static String ffmpeg() { return Ffmpeg.exe(); }

    /** Whether ffmpeg is there (Windows: tools/ffmpeg.exe in the app). */
    public static boolean canDecode() { return Ffmpeg.exe() != null; }

    private static File mk(File f) { f.mkdirs(); return f; }
}
