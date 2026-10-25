package de.lokal.whisper;

/**
 * Streaming-Resampler (gefensterter Sinc, beliebiges Verhaeltnis) nach 16 kHz mono.
 * Reines Java, damit er auch ausserhalb von Android getestet werden kann.
 */
public final class Resampler {
    public interface Sink { void write(float[] data, int n); }

    private static final int HALF = 16;          // Halbe Filterlaenge in Eingangs-Samples
    private static final int PHASES = 256;       // Aufloesung der Filtertabelle
    private final double step;                   // Eingangs-Samples pro Ausgangs-Sample
    private final float[][] table;               // [phase][2*HALF]
    private final boolean passthrough;
    private float[] hist = new float[0];         // Puffer mit noch benoetigten Eingangs-Samples
    private double pos;                          // Position des naechsten Ausgangs-Samples relativ zu hist[0]
    private final float[] out = new float[4096];
    private int outN;
    private final Sink sink;

    public Resampler(int inRate, int outRate, Sink sink) {
        this.sink = sink;
        this.step = (double) inRate / outRate;
        this.passthrough = inRate == outRate;
        double cutoff = Math.min(1.0, (double) outRate / inRate) * 0.94;
        table = new float[PHASES + 1][2 * HALF];
        for (int p = 0; p <= PHASES; p++) {
            double frac = (double) p / PHASES;
            double sum = 0;
            for (int k = 0; k < 2 * HALF; k++) {
                double x = (k - HALF + 1) - frac;            // Abstand Tap -> Zielzeit
                double s = x == 0 ? 1.0 : Math.sin(Math.PI * cutoff * x) / (Math.PI * cutoff * x);
                double w = 0.5 + 0.5 * Math.cos(Math.PI * x / (HALF + 0.5));   // Hann
                double v = cutoff * s * w;
                table[p][k] = (float) v;
                sum += v;
            }
            for (int k = 0; k < 2 * HALF; k++) table[p][k] /= (float) sum;
        }
        pos = HALF - 1;  // erste Ausgabe benoetigt HALF-1 Samples Vorlauf (Null-Padding)
        hist = new float[HALF - 1];
    }

    public void process(float[] in, int n) {
        if (passthrough) { sink.write(in, n); return; }
        float[] buf = new float[hist.length + n];
        System.arraycopy(hist, 0, buf, 0, hist.length);
        System.arraycopy(in, 0, buf, hist.length, n);
        int avail = buf.length;
        while (true) {
            int base = (int) Math.floor(pos);
            if (base + HALF >= avail) break;
            double frac = pos - base;
            int ph = (int) Math.round(frac * PHASES);
            float[] t = table[ph];
            int start = base - HALF + 1;
            float acc = 0f;
            for (int k = 0; k < 2 * HALF; k++) {
                int idx = start + k;
                if (idx >= 0) acc += buf[idx] * t[k];
            }
            out[outN++] = acc;
            if (outN == out.length) { sink.write(out, outN); outN = 0; }
            pos += step;
        }
        int keepFrom = Math.max(0, (int) Math.floor(pos) - HALF + 1);
        hist = new float[avail - keepFrom];
        System.arraycopy(buf, keepFrom, hist, 0, hist.length);
        pos -= keepFrom;
    }

    public void flush() {
        if (!passthrough) process(new float[HALF + 1], HALF + 1);
        if (outN > 0) { sink.write(out, outN); outN = 0; }
    }
}
