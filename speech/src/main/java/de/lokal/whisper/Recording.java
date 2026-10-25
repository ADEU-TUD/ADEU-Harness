package de.lokal.whisper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Nimmt mit 16 kHz mono PCM16 auf und schreibt direkt eine WAV-Datei. Der Dateikopf wird alle paar
 * Sekunden aktualisiert, damit die Aufnahme auch nach einem Absturz lesbar bleibt (beim naechsten
 * Start repariert die Engine liegengebliebene Aufnahmen und bietet sie zum Transkribieren an).
 */
public final class Recording {
    public interface Listener { void onLevel(float level, double seconds); void onError(String msg); }

    static final long HEADER_EVERY_MS = 2000;

    public final File file;
    final File marksFile;
    private final Platform platform;
    private final String deviceId;
    private final Listener listener;
    private volatile boolean running;
    private volatile boolean paused;
    private Thread thread;
    private volatile long samples;
    private final JSONArray marks = new JSONArray();
    volatile LiveSession live;

    Recording(Platform platform, File file, String deviceId, Listener l) {
        this.platform = platform;
        this.file = file;
        this.marksFile = marksFileFor(file);
        this.deviceId = deviceId;
        this.listener = l;
    }

    static File marksFileFor(File wav) { return new File(wav.getParentFile(), wav.getName().replaceAll("\\.wav$", "") + ".marks.json"); }

    public boolean isPaused() { return paused; }
    public double seconds() { return samples / (double) Wav.RATE; }
    public long samples() { return samples; }
    void setPaused(boolean p) { paused = p; }

    /** Markierung an der aktuellen Stelle; wird sofort gespeichert. */
    synchronized JSONObject addMark(String label) {
        JSONObject m = new JSONObject();
        try {
            m.put("t", Math.round(seconds() * 10) / 10.0);
            m.put("label", label == null || label.trim().isEmpty() ? "Markierung " + (marks.length() + 1) : label.trim());
            marks.put(m);
            Util.writeText(marksFile, marks.toString());
        } catch (Exception e) { Util.err("mark", e); }
        return m;
    }

    synchronized JSONArray marks() {
        try { return new JSONArray(marks.toString()); } catch (Exception e) { return new JSONArray(); }
    }

    void start() {
        running = true;
        thread = new Thread(this::loop, "recorder");
        thread.start();
    }

    /** Stoppt und wartet, bis die Datei fertig geschrieben ist. */
    void stop() {
        running = false;
        if (thread != null) { try { thread.join(5000); } catch (InterruptedException ignored) {} }
    }

    private void loop() {
        Platform.MicSource mic = null;
        RandomAccessFile raf = null;
        try {
            mic = platform.openMic(deviceId);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            raf = new RandomAccessFile(file, "rw");
            raf.setLength(0);
            raf.write(Wav.header(0));
            short[] buf = new short[1600];          // 100 ms
            byte[] bytes = new byte[3200];
            long lastHeader = System.currentTimeMillis();
            while (running) {
                int n = mic.read(buf);
                if (n < 0) throw new IOException("Mikrofon liefert keine Daten mehr");
                if (n == 0) continue;
                double sum = 0;
                for (int i = 0; i < n; i++) sum += (double) buf[i] * buf[i];
                float level = (float) Math.sqrt(sum / n) / 32768f;
                if (!paused) {
                    for (int i = 0; i < n; i++) { bytes[2 * i] = (byte) (buf[i] & 0xff); bytes[2 * i + 1] = (byte) ((buf[i] >> 8) & 0xff); }
                    raf.write(bytes, 0, 2 * n);
                    samples += n;
                    LiveSession l = live;
                    if (l != null) {
                        float[] f = new float[n];
                        for (int i = 0; i < n; i++) f[i] = buf[i] / 32768f;
                        l.feed(f);
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastHeader >= HEADER_EVERY_MS) {
                        lastHeader = now;
                        Wav.writeHeader(raf, samples);
                    }
                }
                listener.onLevel(paused ? 0f : level, seconds());
            }
        } catch (Throwable t) {
            Util.err("recorder", t);
            running = false;
            listener.onError(t.getMessage() == null ? t.toString() : t.getMessage());
        } finally {
            if (mic != null) { try { mic.close(); } catch (Throwable ignored) {} }
            if (raf != null) {
                try { Wav.writeHeader(raf, samples); raf.close(); } catch (IOException e) { Util.err("wav header", e); }
            }
        }
    }
}
