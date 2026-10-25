package de.lokal.whisper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** WAV-Dateien (16 kHz, mono, PCM16): Kopf schreiben, reparieren, aus PCM erzeugen. */
public final class Wav {
    public static final int HEADER = 44;
    public static final int RATE = 16000;

    private Wav() {}

    public static byte[] header(long samples) {
        long dataLen = samples * 2;
        byte[] h = new byte[HEADER];
        putStr(h, 0, "RIFF"); putInt(h, 4, (int) Math.min(0xFFFFFFFFL, 36 + dataLen)); putStr(h, 8, "WAVE");
        putStr(h, 12, "fmt "); putInt(h, 16, 16); putShort(h, 20, 1); putShort(h, 22, 1);
        putInt(h, 24, RATE); putInt(h, 28, RATE * 2); putShort(h, 32, 2); putShort(h, 34, 16);
        putStr(h, 36, "data"); putInt(h, 40, (int) Math.min(0xFFFFFFFFL, dataLen));
        return h;
    }

    /** Schreibt den Kopf an den Dateianfang; die Schreibposition bleibt erhalten. */
    public static void writeHeader(RandomAccessFile raf, long samples) throws IOException {
        long pos = raf.getFilePointer();
        raf.seek(0);
        raf.write(header(samples));
        raf.seek(Math.max(pos, HEADER));
    }

    /**
     * Repariert eine Aufnahme, deren Kopf nicht zur Dateilaenge passt (App wurde waehrend der Aufnahme
     * beendet). Liefert die Anzahl Samples oder -1, wenn es keine WAV-Datei dieser App ist.
     */
    public static long repair(File f) {
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            long len = raf.length();
            if (len < HEADER) return -1;
            byte[] h = new byte[HEADER];
            raf.readFully(h);
            boolean empty = true;
            for (byte b : h) if (b != 0) { empty = false; break; }
            boolean ours = empty || ("RIFF".equals(new String(h, 0, 4, "US-ASCII")) && "WAVE".equals(new String(h, 8, 4, "US-ASCII"))
                    && getInt(h, 24) == RATE && getShort(h, 22) == 1 && getShort(h, 34) == 16);
            if (!ours) return -1;
            long samples = (len - HEADER) / 2;
            if ((len - HEADER) % 2 != 0) raf.setLength(HEADER + samples * 2);
            raf.seek(0);
            raf.write(header(samples));
            return samples;
        } catch (IOException e) {
            Util.err("wav repair " + f, e);
            return -1;
        }
    }

    public static void pcmToWav(File pcm, File wav) throws IOException {
        long samples = pcm.length() / 2;
        try (RandomAccessFile out = new RandomAccessFile(wav, "rw"); FileInputStream in = new FileInputStream(pcm)) {
            out.setLength(0);
            out.write(header(samples));
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** Liest Samples aus einer PCM16-Datei (ab Sample {@code from}). */
    public static float[] readPcm(File pcm, long from, long count) throws IOException {
        return readPcm(pcm, 0, from, count);
    }

    /** Wie readPcm, aber die Samples beginnen erst bei {@code offset} Bytes (z. B. 44 bei WAV). */
    public static float[] readPcm(File pcm, long offset, long from, long count) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(pcm, "r")) {
            long total = (raf.length() - offset) / 2;
            if (from < 0) from = 0;
            if (from + count > total) count = total - from;
            if (count <= 0) return new float[0];
            float[] out = new float[(int) count];
            byte[] b = new byte[1 << 20];
            raf.seek(offset + from * 2);
            int pos = 0;
            while (pos < out.length) {
                int want = Math.min(b.length / 2, out.length - pos);
                raf.readFully(b, 0, want * 2);
                for (int i = 0; i < want; i++) out[pos + i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8)) / 32768f;
                pos += want;
            }
            return out;
        }
    }

    private static void putStr(byte[] b, int o, String s) { for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i); }
    private static void putInt(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24); }
    private static void putShort(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
    private static int getInt(byte[] b, int o) { return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8 | (b[o + 2] & 0xff) << 16 | (b[o + 3] & 0xff) << 24; }
    private static int getShort(byte[] b, int o) { return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8; }
}
