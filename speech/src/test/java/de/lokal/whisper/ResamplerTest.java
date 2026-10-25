package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResamplerTest {
    private static float[] resample(float[] in, int rate, int chunk) {
        float[] out = new float[in.length * 16000 / rate + 64];
        int[] n = {0};
        Resampler r = new Resampler(rate, 16000, (d, k) -> { System.arraycopy(d, 0, out, n[0], k); n[0] += k; });
        for (int i = 0; i < in.length; i += chunk) {
            int k = Math.min(chunk, in.length - i);
            float[] part = new float[k];
            System.arraycopy(in, i, part, 0, k);
            r.process(part, k);
        }
        r.flush();
        float[] res = new float[n[0]];
        System.arraycopy(out, 0, res, 0, n[0]);
        return res;
    }

    @Test void keepsLengthAndTone() {
        int rate = 48000;
        float[] in = new float[rate];
        for (int i = 0; i < in.length; i++) in[i] = (float) (0.5 * Math.sin(2 * Math.PI * 440 * i / rate));
        float[] out = resample(in, rate, 1000);
        assertTrue(Math.abs(out.length - 16000) <= 20, "Laenge " + out.length);
        // Amplitude des 440-Hz-Tons bleibt erhalten
        double max = 0;
        for (int i = 2000; i < 14000; i++) max = Math.max(max, Math.abs(out[i]));
        assertEquals(0.5, max, 0.03);
        // Frequenz: Nulldurchgaenge zaehlen (440 Hz -> ~880 pro Sekunde)
        int zc = 0;
        for (int i = 1; i < out.length; i++) if ((out[i - 1] < 0) != (out[i] < 0)) zc++;
        assertEquals(880, zc, 10);
    }

    @Test void filtersAboveNyquist() {
        int rate = 44100;
        float[] in = new float[rate];
        for (int i = 0; i < in.length; i++) in[i] = (float) (0.5 * Math.sin(2 * Math.PI * 12000 * i / rate));
        float[] out = resample(in, rate, 4410);
        double max = 0;
        for (int i = 2000; i < out.length - 2000; i++) max = Math.max(max, Math.abs(out[i]));
        assertTrue(max < 0.05, "12 kHz muss weggefiltert werden: " + max);
    }

    @Test void passthroughAt16k() {
        float[] in = {0.1f, 0.2f, 0.3f};
        assertArrayEquals(in, resample(in, 16000, 3));
    }
}
