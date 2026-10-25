package de.lokal.whisper.desktop;

import de.lokal.whisper.Platform;
import de.lokal.whisper.Util;
import de.lokal.whisper.Wav;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Simuliertes Mikrofon fuer Tests ohne Soundkarte (CI): spielt eine 16-kHz-WAV-Datei in Echtzeit
 * (oder schneller) ab und danach Stille. Aktiv mit -Dwhisper.testMic=<datei.wav>.
 */
final class TestMic implements Platform.MicSource {
    private final RandomAccessFile raf;
    private final double speed;
    private final long t0 = System.nanoTime();
    private long delivered;

    TestMic(File wav, double speed) throws IOException {
        raf = new RandomAccessFile(wav, "r");
        raf.seek(Wav.HEADER);
        this.speed = speed <= 0 ? 1 : speed;
    }

    @Override public int read(short[] buf) throws IOException {
        long due = (long) ((delivered + buf.length) / 16000.0 / speed * 1e9);
        long wait = due - (System.nanoTime() - t0);
        if (wait > 0) { try { Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); } catch (InterruptedException e) { return -1; } }
        byte[] b = new byte[buf.length * 2];
        int n = raf.read(b);
        for (int i = 0; i < buf.length; i++) buf[i] = n > 0 && 2 * i + 1 < n ? (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8)) : 0;
        delivered += buf.length;
        return buf.length;
    }

    @Override public void close() {
        try { raf.close(); } catch (IOException ignored) {}
    }
}
