package de.lokal.whisper;

import com.k2fsa.sherpa.onnx.Vad;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Transkribiert schon waehrend der Aufnahme (ohne Sprecher). Die Audiodaten kommen vom Recorder,
 * die Abschnitte werden aus der wachsenden WAV-Datei gelesen. Nach dem Stoppen ist der Text fast
 * sofort fertig; die Sprechererkennung kann danach laufen.
 */
final class LiveSession {
    private static final float[] END = new float[0];

    final Models.ModelInfo model;
    final String language;
    private final Engine engine;
    private final File wav;
    private final LinkedBlockingQueue<float[]> queue = new LinkedBlockingQueue<>();
    private final List<JSONObject> segments = new ArrayList<>();
    private final Thread thread;
    private volatile boolean aborted;
    private volatile long fed, done;
    private volatile Throwable error;
    private volatile String detectedLang;

    LiveSession(Engine engine, Models.ModelInfo model, String language, File wav) {
        this.engine = engine;
        this.model = model;
        this.language = language;
        this.wav = wav;
        thread = new Thread(this::run, "live");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    /** Passen die gewaehlten Optionen zur laufenden Live-Transkription? */
    boolean compatible(JSONObject opts) {
        return model.id.equals(opts.optString("model")) && language.equals(opts.optString("language", "de"))
                && !"translate".equals(opts.optString("task"));
    }

    void feed(float[] samples) {
        fed += samples.length;
        queue.add(samples);
    }

    /** Keine weiteren Daten; der Rest wird noch transkribiert. */
    void finish() { queue.add(END); }

    void abort() {
        aborted = true;
        queue.clear();
        queue.add(END);
    }

    /** Anteil des bisher Aufgenommenen, der schon transkribiert ist. */
    double progress() { long f = fed; return f <= 0 ? 1 : Math.min(1, done / (double) f); }

    String detectedLanguage() { return detectedLang; }

    synchronized List<JSONObject> segments() { return new ArrayList<>(segments); }

    synchronized JSONArray segmentsJson() {
        JSONArray a = new JSONArray();
        for (JSONObject s : segments) a.put(s);
        return a;
    }

    /** Wartet, bis alles transkribiert ist. */
    void await(Platform.Cancel cancel, Platform.Progress p) throws Exception {
        while (thread.isAlive()) {
            thread.join(250);
            if (cancel.cancelled()) { abort(); throw new InterruptedException("abgebrochen"); }
            if (p != null) p.on(progress());
        }
        if (error != null) throw new Exception("Live-Transkription fehlgeschlagen", error);
    }

    private void run() {
        Speech.Handle h = null;
        Vad vad = null;
        try {
            h = Speech.acquire(model, engine.modelDir(model), language, "transcribe", engine.platform.threads());
            final Speech.Handle handle = h;
            final Replacements rep = Replacements.load(engine.dataDir);
            Chunker chunker = new Chunker(null, c -> {
                if (aborted) throw new InterruptedException();
                long pad = 2400;
                long from = Math.max(0, c.start - pad), to = c.end + pad;
                float[] samples = Wav.readPcm(wav, Wav.HEADER, from, to - from);
                if (samples.length < 1600) { done = c.end; return; }
                Speech.Result r = Speech.recognize(handle, samples);
                done = c.end;
                if (detectedLang == null && r.lang != null && !r.lang.isEmpty()) detectedLang = r.lang.replace("<|", "").replace("|>", "");
                JSONObject s = Engine.segmentJson(r, c, from, (c.end - c.start) / 16000.0, rep);
                if (s == null) return;
                synchronized (LiveSession.this) { s.put("id", segments.size()); segments.add(s); }
                JSONObject ev = new JSONObject();
                ev.put("type", "live_segment");
                ev.put("segment", s);
                engine.emit(ev);
            });
            vad = Speech.newVad(engine.builtin("silero_vad.onnx"));
            while (true) {
                float[] b = queue.take();
                if (b == END || aborted) break;
                Engine.feedVad(vad, b, chunker);
            }
            if (!aborted) {
                vad.flush();
                Engine.drainVad(vad, chunker);
                chunker.finish();
                done = fed;
            }
        } catch (InterruptedException e) {
            // abgebrochen
        } catch (Throwable t) {
            Util.err("live", t);
            error = t;
        } finally {
            if (vad != null) { try { vad.release(); } catch (Throwable ignored) {} }
            Speech.release(h);
        }
    }
}
