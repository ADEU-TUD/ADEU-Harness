package de.lokal.whisper;

import java.util.ArrayList;
import java.util.List;

/**
 * Baut aus VAD-Sprachabschnitten (in Samples) Erkennungs-Haeppchen fuer Whisper:
 * teilt an Sprecherwechseln (falls Sprechererkennung aktiv) und fasst kurze Abschnitte
 * desselben Sprechers zusammen (max. ~18 s), damit Whisper genug Kontext hat.
 * Reines Java (testbar).
 */
public final class Chunker {
    static final int SR = 16000;

    static final class Turn {
        final double start, end; final int speaker;
        Turn(double s, double e, int k) { start = s; end = e; speaker = k; }
    }

    static final class Chunk {
        long start, end; int speaker;
        Chunk(long s, long e, int k) { start = s; end = e; speaker = k; }
    }

    interface Sink { void onChunk(Chunk c) throws Exception; }

    private final List<Turn> turns;   // sortiert, oder null
    private final Sink sink;
    private Chunk cur;
    private final long maxLen;
    private final long maxGap = (long) (0.8 * SR);
    private final long tiny = (long) (0.4 * SR);

    Chunker(List<Turn> turns, Sink sink) { this(turns, sink, 18); }

    /** {@code maxSeconds}: laengstes Haeppchen (Cloud-Erkennung: laenger, damit es weniger Anfragen sind). */
    Chunker(List<Turn> turns, Sink sink, int maxSeconds) { this.turns = turns; this.sink = sink; this.maxLen = (long) maxSeconds * SR; }

    void addRange(long s, long e) throws Exception {
        if (e <= s) return;
        if (turns == null || turns.isEmpty()) { addPiece(s, e, -1); return; }
        // An Sprecherwechseln innerhalb des Bereichs aufteilen
        long pos = s;
        while (pos < e) {
            double t = pos / (double) SR;
            int spk = speakerAt(t, Math.min(e, pos + SR) / (double) SR);
            long next = e;
            for (Turn tr : turns) {
                long ts = (long) (tr.start * SR), te = (long) (tr.end * SR);
                if (te <= pos || ts >= e) continue;
                if (tr.speaker != spk && ts > pos && ts < next) next = ts;
                if (tr.speaker == spk && te > pos && te < next) {
                    // Ende dieses Turns: nur schneiden, wenn danach ein anderer Sprecher anfaengt
                    int after = speakerAt(te / (double) SR + 0.05, te / (double) SR + 0.5);
                    if (after != spk && after >= 0) next = Math.min(next, te);
                }
            }
            if (next <= pos) next = e;
            addPiece(pos, next, spk);
            pos = next;
        }
    }

    private int speakerAt(double t0, double t1) { return speakerAt(turns, t0, t1); }

    /** Sprecher mit der groessten Ueberschneidung im Zeitraum (sonst der naechstgelegene). */
    static int speakerAt(List<Turn> turns, double t0, double t1) {
        double best = 0; int spk = -1;
        java.util.Map<Integer, Double> ov = new java.util.HashMap<>();
        for (Turn tr : turns) {
            double o = Math.min(t1, tr.end) - Math.max(t0, tr.start);
            if (o > 0) {
                double v = (ov.containsKey(tr.speaker) ? ov.get(tr.speaker) : 0) + o;
                ov.put(tr.speaker, v);
                if (v > best) { best = v; spk = tr.speaker; }
            }
        }
        if (spk >= 0) return spk;
        double mid = (t0 + t1) / 2, dist = Double.MAX_VALUE;
        for (Turn tr : turns) {
            double d = mid < tr.start ? tr.start - mid : (mid > tr.end ? mid - tr.end : 0);
            if (d < dist) { dist = d; spk = tr.speaker; }
        }
        return spk;
    }

    private void addPiece(long s, long e, int spk) throws Exception {
        if (cur == null) { cur = new Chunk(s, e, spk); return; }
        long gap = s - cur.end;
        boolean small = (e - s) < tiny;
        boolean sameSpk = spk == cur.speaker || small;
        if (sameSpk && gap <= maxGap && (e - cur.start) <= maxLen) {
            cur.end = e;
            return;
        }
        sink.onChunk(cur);
        cur = new Chunk(s, e, spk);
    }

    void finish() throws Exception {
        if (cur != null) { sink.onChunk(cur); cur = null; }
    }

    /** Verschmilzt benachbarte Turns desselben Sprechers und entfernt Winzlinge. */
    static List<Turn> cleanTurns(List<Turn> in, double minSeconds, double minShare) {
        List<Turn> out = new ArrayList<>();
        if (in == null || in.isEmpty()) return out;
        java.util.Map<Integer, Double> tot = new java.util.HashMap<>();
        double all = 0;
        for (Turn t : in) { double d = t.end - t.start; all += d; tot.put(t.speaker, (tot.containsKey(t.speaker) ? tot.get(t.speaker) : 0) + d); }
        double limit = Math.max(minSeconds, minShare * all);
        java.util.Set<Integer> big = new java.util.HashSet<>();
        for (java.util.Map.Entry<Integer, Double> en : tot.entrySet()) if (en.getValue() >= limit) big.add(en.getKey());
        boolean remap = !big.isEmpty() && big.size() < tot.size();
        List<Turn> bigTurns = new ArrayList<>();
        for (Turn t : in) if (big.contains(t.speaker)) bigTurns.add(t);
        for (Turn t : in) {
            int k = t.speaker;
            if (remap && !big.contains(k)) {
                double mid = (t.start + t.end) / 2, best = Double.MAX_VALUE;
                for (Turn b : bigTurns) {
                    double d = mid < b.start ? b.start - mid : (mid > b.end ? mid - b.end : 0);
                    if (d < best) { best = d; k = b.speaker; }
                }
            }
            Turn last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.speaker == k && t.start - last.end < 1.0) {
                out.set(out.size() - 1, new Turn(last.start, Math.max(last.end, t.end), k));
            } else {
                out.add(new Turn(t.start, t.end, k));
            }
        }
        return out;
    }
}
