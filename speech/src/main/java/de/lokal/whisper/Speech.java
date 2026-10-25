package de.lokal.whisper;

import com.k2fsa.sherpa.onnx.FastClusteringConfig;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Duenne Schicht ueber sherpa-onnx (Whisper, Parakeet, Silero-VAD, Sprechererkennung).
 * Dieselben Java-Klassen laufen auf Android und Windows.
 */
public final class Speech {
    public interface Progress { void on(double f); }

    public static final class Word {
        public final double start, end; public final String text;
        /** Guete der Zeit: 'm' gemessen, 's' gemessen (anders geschrieben), 'i' zwischen Nachbarn gelegt (siehe WordAlign). */
        public final char q;
        public Word(double start, double end, String text) { this(start, end, text, 'm'); }
        public Word(double start, double end, String text, char q) { this.start = start; this.end = end; this.text = text; this.q = q; }
    }

    public static final class Result {
        public final String text, lang; public final List<Word> words;
        public Result(String text, String lang, List<Word> words) { this.text = text; this.lang = lang; this.words = words; }
    }

    /** Ein geladenes Modell. Mehrere Nutzer (Auftrag und Live-Transkription) teilen es sich. */
    public static final class Handle {
        final String key, modelId; final boolean words;
        final OfflineRecognizer rec;
        int users;
        Handle(String key, String modelId, boolean words, OfflineRecognizer rec) { this.key = key; this.modelId = modelId; this.words = words; this.rec = rec; }
    }

    private static final Map<String, Handle> loaded = new LinkedHashMap<>();

    private Speech() {}

    static String key(Models.ModelInfo m, String language, String task) {
        String lang = !m.isWhisper() || language == null || "auto".equals(language) ? "" : language;
        return m.id + "|" + lang + "|" + (m.canTranslate && "translate".equals(task) ? "translate" : "transcribe");
    }

    /**
     * Laedt das Modell (oder nimmt das bereits geladene). Unbenutzte andere Modelle werden vorher
     * freigegeben, damit nie mehr Speicher belegt ist als noetig.
     */
    public static synchronized Handle acquire(Models.ModelInfo m, File dir, String language, String task, int threads) {
        String key = key(m, language, task);
        Handle h = loaded.get(key);
        if (h == null) {
            evictIdle(null);
            h = new Handle(key, m.id, m.wordTimes, new OfflineRecognizer(config(m, dir, language, task, threads)));
            loaded.put(key, h);
        }
        h.users++;
        return h;
    }

    public static synchronized void release(Handle h) {
        if (h == null) return;
        h.users = Math.max(0, h.users - 1);
    }

    /** Gibt unbenutzte Modelle frei (alle, oder nur die mit dieser Kennung). */
    public static synchronized void evictIdle(String modelId) {
        for (Iterator<Handle> it = loaded.values().iterator(); it.hasNext(); ) {
            Handle h = it.next();
            if (h.users > 0 || (modelId != null && !modelId.equals(h.modelId))) continue;
            try { h.rec.release(); } catch (Throwable ignored) {}
            it.remove();
        }
    }

    public static synchronized boolean inUse(String modelId) {
        for (Handle h : loaded.values()) if (h.users > 0 && h.modelId.equals(modelId)) return true;
        return false;
    }

    /** Schluessel des Modells, das gerade benutzt wird (oder null). */
    public static synchronized String activeKey() {
        for (Handle h : loaded.values()) if (h.users > 0) return h.key;
        return null;
    }

    private static OfflineRecognizerConfig config(Models.ModelInfo m, File dir, String language, String task, int threads) {
        OfflineModelConfig.Builder mc = OfflineModelConfig.builder()
                .setNumThreads(threads)
                .setDebug(false)
                .setProvider("cpu");
        if (m.isWhisper()) {
            String lang = language == null || "auto".equals(language) ? "" : language;
            mc.setWhisper(OfflineWhisperModelConfig.builder()
                    .setEncoder(new File(dir, m.file("-encoder.int8.onnx").name).getAbsolutePath())
                    .setDecoder(new File(dir, m.file("-decoder.int8.onnx").name).getAbsolutePath())
                    .setLanguage(lang)
                    .setTask(m.canTranslate && "translate".equals(task) ? "translate" : "transcribe")
                    .setTailPaddings(-1)
                    .build())
              .setTokens(new File(dir, m.file("tokens.txt").name).getAbsolutePath())
              .setModelType("whisper");
        } else {
            mc.setTransducer(OfflineTransducerModelConfig.builder()
                    .setEncoder(new File(dir, m.file("encoder.int8.onnx").name).getAbsolutePath())
                    .setDecoder(new File(dir, m.file("decoder.int8.onnx").name).getAbsolutePath())
                    .setJoiner(new File(dir, m.file("joiner.int8.onnx").name).getAbsolutePath())
                    .build())
              .setTokens(new File(dir, m.file("tokens.txt").name).getAbsolutePath())
              .setModelType("nemo_transducer");
        }
        return OfflineRecognizerConfig.builder()
                .setFeatureConfig(FeatureConfig.builder().setSampleRate(16000).setFeatureDim(m.featDim).build())
                .setOfflineModelConfig(mc.build())
                .setDecodingMethod("greedy_search")
                .build();
    }

    /** Erkennt einen Abschnitt (16 kHz). Zeiten der Woerter relativ zum Abschnittsanfang. */
    public static Result recognize(Handle h, float[] samples) {
        synchronized (h) {
            OfflineStream s = h.rec.createStream();
            try {
                s.acceptWaveform(samples, 16000);
                h.rec.decode(s);
                OfflineRecognizerResult r = h.rec.getResult(s);
                List<Word> words = h.words ? words(r.getTokens(), r.getTimestamps(), r.getDurations()) : null;
                return new Result(r.getText(), r.getLang(), words);
            } finally {
                s.release();
            }
        }
    }

    /** Fasst Teilwort-Tokens (" Al", "les", ",") zu Woertern mit Anfangs- und Endzeit zusammen. */
    static List<Word> words(String[] tokens, float[] times, float[] durations) {
        List<Word> out = new ArrayList<>();
        if (tokens == null || times == null || tokens.length != times.length) return out;
        StringBuilder cur = null;
        double start = 0, end = 0;
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i].replace('▁', ' ');
            if (t.isEmpty()) continue;
            double ts = times[i];
            double te = durations != null && durations.length > i && durations[i] > 0 ? ts + durations[i] : ts + 0.08;
            boolean startsWord = t.charAt(0) == ' ';
            String piece = t.trim();
            if (piece.isEmpty()) continue;
            boolean punct = piece.matches("\\p{Punct}+");
            if (cur == null || (startsWord && !punct)) {
                if (cur != null) out.add(new Word(round(start), round(end), cur.toString()));
                cur = new StringBuilder(piece);
                start = ts;
            } else {
                cur.append(piece);
            }
            end = Math.max(end, te);
        }
        if (cur != null) out.add(new Word(round(start), round(end), cur.toString()));
        return out;
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }

    public static Vad newVad(File model) {
        return new Vad(VadModelConfig.builder()
                .setSileroVadModelConfig(SileroVadModelConfig.builder()
                        .setModel(model.getAbsolutePath())
                        .setThreshold(0.45f)
                        .setMinSilenceDuration(0.5f)
                        .setMinSpeechDuration(0.25f)
                        .setWindowSize(512)
                        .setMaxSpeechDuration(18f)
                        .build())
                .setSampleRate(16000)
                .setNumThreads(1)
                .setProvider("cpu")
                .setDebug(false)
                .build());
    }

    /** Zaehlt Sprachabschnitte (Selbsttest der nativen Bibliotheken). */
    public static int countSpeech(File vadModel, float[] samples) {
        Vad vad = newVad(vadModel);
        try {
            int n = 0;
            float[] win = new float[512];
            for (int i = 0; i + 512 <= samples.length; i += 512) {
                System.arraycopy(samples, i, win, 0, 512);
                vad.acceptWaveform(win);
                while (!vad.empty()) { vad.pop(); n++; }
            }
            vad.flush();
            while (!vad.empty()) { vad.pop(); n++; }
            return n;
        } finally {
            vad.release();
        }
    }

    static OfflineSpeakerDiarizationConfig diarConfig(File seg, File emb, int numSpeakers, float threshold, int threads) {
        return OfflineSpeakerDiarizationConfig.builder()
                .setSegmentation(OfflineSpeakerSegmentationModelConfig.builder()
                        .setPyannote(OfflineSpeakerSegmentationPyannoteModelConfig.builder().setModel(seg.getAbsolutePath()).build())
                        .setNumThreads(threads).setDebug(false).setProvider("cpu").build())
                .setEmbedding(SpeakerEmbeddingExtractorConfig.builder()
                        .setModel(emb.getAbsolutePath()).setNumThreads(threads).setDebug(false).setProvider("cpu").build())
                .setClustering(FastClusteringConfig.builder()
                        .setNumClusters(numSpeakers > 0 ? numSpeakers : -1).setThreshold(threshold).build())
                .setMinDurationOn(0.3f)
                .setMinDurationOff(0.5f)
                .build();
    }

    /** Stimmabdruck (Sprecher-Einbettung) eines Audioausschnitts (16 kHz, mono), fuer Stimmprofile. */
    public static float[] embedding(float[] samples, File model, int threads) {
        com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor ex = new com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor(
                SpeakerEmbeddingExtractorConfig.builder().setModel(model.getAbsolutePath()).setNumThreads(threads).setDebug(false).setProvider("cpu").build());
        try {
            com.k2fsa.sherpa.onnx.OnlineStream st = ex.createStream();
            try {
                st.acceptWaveform(samples, 16000);
                st.inputFinished();
                return ex.compute(st);
            } finally {
                st.release();
            }
        } finally {
            ex.release();
        }
    }

    /**
     * Sprechererkennung. Laeuft in einem eigenen Thread, weil sherpa-onnx sich mittendrin nicht anhalten
     * laesst: bei Abbruch kehrt diese Methode sofort zurueck (InterruptedException), der Rest laeuft im
     * Hintergrund zu Ende und das Ergebnis wird verworfen.
     */
    public static List<Chunker.Turn> diarize(final float[] samples, File seg, File emb, int numSpeakers, float threshold, int threads,
                                             final Progress p, final Platform.Cancel cancel) throws Exception {
        final OfflineSpeakerDiarizationConfig cfg = diarConfig(seg, emb, numSpeakers, threshold, threads);
        final Object[] box = new Object[2];
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                OfflineSpeakerDiarization sd = null;
                try {
                    sd = new OfflineSpeakerDiarization(cfg);
                    box[0] = sd.processWithCallback(samples, (done, total, arg) -> {
                        if (p != null && total > 0 && !cancel.cancelled()) p.on(done / (double) total);
                        return 0;
                    });
                } catch (Throwable e) {
                    box[1] = e;
                } finally {
                    if (sd != null) { try { sd.release(); } catch (Throwable ignored) {} }
                }
            }
        }, "diarize");
        t.setDaemon(true);
        t.start();
        while (t.isAlive()) {
            t.join(200);
            if (cancel.cancelled()) throw new InterruptedException("abgebrochen");
        }
        if (box[1] instanceof Exception) throw (Exception) box[1];
        if (box[1] instanceof Throwable) throw new Exception((Throwable) box[1]);
        return toTurns((OfflineSpeakerDiarizationSegment[]) box[0]);
    }

    static List<Chunker.Turn> toTurns(OfflineSpeakerDiarizationSegment[] res) {
        List<Chunker.Turn> out = new ArrayList<>();
        if (res != null) for (OfflineSpeakerDiarizationSegment s : res) out.add(new Chunker.Turn(s.getStart(), s.getEnd(), s.getSpeaker()));
        Collections.sort(out, (a, b) -> Double.compare(a.start, b.start));
        return out;
    }

    /** Sucht die Clustering-Schwelle, bei der die 4-Sprecher-Testaufnahme 4 Sprecher ergibt. */
    public static float calibrate(float[] samples, File seg, File emb, int threads, Progress p, StringBuilder table, Platform.Cancel cancel) throws InterruptedException {
        OfflineSpeakerDiarization sd = new OfflineSpeakerDiarization(diarConfig(seg, emb, -1, 0.5f, threads));
        List<Float> hits = new ArrayList<>();
        List<float[]> rows = new ArrayList<>();
        try {
            int steps = 16;
            for (int i = 0; i < steps; i++) {
                if (cancel != null && cancel.cancelled()) throw new InterruptedException("abgebrochen");
                float t = 0.20f + 0.05f * i;
                sd.setConfig(diarConfig(seg, emb, -1, t, threads));
                OfflineSpeakerDiarizationSegment[] res = sd.process(samples);
                java.util.Set<Integer> spk = new java.util.HashSet<>();
                if (res != null) for (OfflineSpeakerDiarizationSegment s : res) spk.add(s.getSpeaker());
                int n = spk.size();
                rows.add(new float[]{t, n});
                if (table != null) table.append(String.format(java.util.Locale.US, "%.2f->%d ", t, n));
                if (n == 4) hits.add(t);
                if (p != null) p.on((i + 1) / (double) steps);
                if (n <= 1) break; // hoehere Schwellen ergeben ebenfalls nur einen Sprecher
            }
        } finally {
            sd.release();
        }
        if (!hits.isEmpty()) return hits.get(hits.size() / 2);
        float best = 0.6f; float bestD = Float.MAX_VALUE;
        for (float[] r : rows) { float d = Math.abs(r[1] - 4); if (d < bestD || (d == bestD && r[0] > best)) { bestD = d; best = r[0]; } }
        return best;
    }
}
