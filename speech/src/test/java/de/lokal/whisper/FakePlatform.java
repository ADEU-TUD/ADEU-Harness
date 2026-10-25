package de.lokal.whisper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Plattform fuer Tests: Ordner im Temp-Verzeichnis, Ereignisse werden gesammelt. */
class FakePlatform implements Platform {
    final File root;
    final List<String> events = new ArrayList<>();

    FakePlatform(File root) { this.root = root; }

    @Override public File dataDir() { return new File(root, "data"); }
    @Override public File modelsDir() { return new File(root, "models"); }
    @Override public File cacheDir() { return new File(root, "cache"); }
    @Override public String deviceKind() { return "test"; }
    @Override public String deviceName() { return "Test"; }
    @Override public String version() { return "0"; }
    @Override public InputStream openBuiltin(String name) { return new ByteArrayInputStream(new byte[0]); }
    @Override public synchronized void emit(String json) { events.add(json); }
    @Override public void onActivity(Engine engine) {}
    @Override public void onJobFinished(Engine engine, Engine.Job job) {}
    @Override public long decode(File src, File pcmOut, Progress progress, Cancel cancel) throws Exception { throw new IOException("nicht im Test"); }
    @Override public String encodeSpeech(File pcm, File dir, String baseName) { return null; }
    @Override public MicSource openMic(String deviceId) throws Exception { throw new IOException("kein Mikrofon"); }
    @Override public int threads() { return 1; }
}
