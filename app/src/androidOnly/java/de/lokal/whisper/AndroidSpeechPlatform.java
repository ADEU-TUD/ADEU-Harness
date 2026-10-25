package de.lokal.whisper;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Android side of the speech recognition: app folders, decoding with MediaCodec, Opus recordings and the microphone.
 * Everything visible (events, notifications) goes to the {@link Host} of the app.
 */
public final class AndroidSpeechPlatform implements Platform {
    /** Implemented by the app (io.github.thepytales.llmhub.transcribe). */
    public interface Host {
        void emit(String json);
        void onActivity(Engine engine);
        void onJobFinished(Engine engine, Engine.Job job);
        /** Set number of threads (0 = automatic). */
        int threads();
        /** "speech" (VOICE_RECOGNITION source, recommended) or "standard". */
        String micMode();
        String version();
    }

    private final Context ctx;
    private final Host host;
    private final File root;

    public AndroidSpeechPlatform(Context c, Host host) {
        this.ctx = c.getApplicationContext();
        this.host = host;
        this.root = new File(ctx.getFilesDir(), "speech");
    }

    @Override public File dataDir() { return mk(new File(root, "data")); }
    @Override public File modelsDir() { return mk(new File(root, "models")); }
    @Override public File cacheDir() { return mk(new File(ctx.getCacheDir(), "speech")); }
    @Override public String deviceKind() { return "phone"; }
    @Override public String deviceName() { return Build.MANUFACTURER + " " + Build.MODEL; }
    @Override public String version() { return host.version(); }

    @Override public InputStream openBuiltin(String name) throws IOException { return ctx.getAssets().open("models/" + name); }

    @Override public void emit(String json) { host.emit(json); }
    @Override public void onActivity(Engine engine) { host.onActivity(engine); }
    @Override public void onJobFinished(Engine engine, Engine.Job job) { host.onJobFinished(engine, job); }

    @Override public long decode(File src, File pcmOut, Progress progress, Cancel cancel) throws Exception {
        return AudioDecoder.decode(src.getAbsolutePath(), pcmOut, progress, cancel);
    }

    @Override public String encodeSpeech(File pcm, File dir, String baseName) { return AudioEncoder.encode(pcm, dir, baseName); }

    @Override public MicSource openMic(String deviceId) throws Exception {
        return new AndroidMic(!"standard".equals(host.micMode()));
    }

    @Override public int threads() {
        int set = host.threads();
        if (set > 0) return set;
        int n = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(6, n - 2));
    }

    private static File mk(File f) { f.mkdirs(); return f; }
}
