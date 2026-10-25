package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WavTest {
    @TempDir Path tmp;

    @Test void repairsRecordingWithoutHeader() throws Exception {
        // So sieht eine Aufnahme aus, wenn die App vor dem Stoppen beendet wurde: Kopf mit 0 Samples
        File f = tmp.resolve("rec.wav").toFile();
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.write(Wav.header(0));
            raf.write(new byte[16000 * 2 * 3 + 1]); // 3 s + ein halbes Sample
        }
        assertEquals(48000, Wav.repair(f));
        assertTrue(Engine.isOwnWav(f));
        byte[] h = Files.readAllBytes(f.toPath());
        assertEquals(44 + 96000, h.length);
        assertEquals(96000, (h[40] & 0xff) | (h[41] & 0xff) << 8 | (h[42] & 0xff) << 16 | (h[43] & 0xff) << 24);
    }

    @Test void repairsZeroHeader() throws Exception {
        File f = tmp.resolve("zero.wav").toFile();
        Files.write(f.toPath(), new byte[44 + 3200]);
        assertEquals(1600, Wav.repair(f));
        assertTrue(Engine.isOwnWav(f));
    }

    @Test void rejectsForeignFiles() throws Exception {
        File f = tmp.resolve("x.wav").toFile();
        byte[] b = new byte[1000];
        "RIFF....WAVEfmt ".getBytes("US-ASCII");
        System.arraycopy("RIFFxxxxWAVE".getBytes("US-ASCII"), 0, b, 0, 12);
        b[24] = (byte) 0x44; b[25] = (byte) 0xAC; // 44100 Hz
        Files.write(f.toPath(), b);
        assertEquals(-1, Wav.repair(f));
    }

    @Test void readsPcmWithOffset() throws Exception {
        File f = tmp.resolve("a.wav").toFile();
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.write(Wav.header(3));
            raf.write(new byte[]{0, 0x40, 0, (byte) 0xC0, (byte) 0xFF, 0x7F});
        }
        float[] s = Wav.readPcm(f, Wav.HEADER, 1, 5);
        assertEquals(2, s.length);
        assertEquals(-0.5f, s[0], 1e-4);
        assertEquals(32767 / 32768f, s[1], 1e-4);
    }
}
