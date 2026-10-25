package de.lokal.whisper;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.IOException;

/**
 * Mikrofon mit 16 kHz mono. Standard ist die Quelle VOICE_RECOGNITION: Android liefert dann den Ton
 * so, wie ihn Spracherkennung braucht (ohne Lautstaerke-Automatik und Klangfilter, die Woerter
 * verschlucken koennen). Gibt es die Quelle nicht, wird das normale Mikrofon benutzt.
 */
final class AndroidMic implements Platform.MicSource {
    private final AudioRecord rec;

    @SuppressLint("MissingPermission")
    AndroidMic(boolean speechSource) throws IOException {
        int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(min, 16000 * 2) * 2;
        AudioRecord r = null;
        int[] sources = speechSource ? new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC}
                : new int[]{MediaRecorder.AudioSource.MIC};
        for (int src : sources) {
            try {
                r = new AudioRecord(src, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                if (r.getState() == AudioRecord.STATE_INITIALIZED) break;
                r.release();
                r = null;
            } catch (Exception e) {
                Util.err("AudioRecord " + src, e);
                r = null;
            }
        }
        if (r == null) throw new IOException("Mikrofon nicht verfügbar");
        rec = r;
        rec.startRecording();
    }

    @Override public int read(short[] buf) {
        int n = rec.read(buf, 0, buf.length);
        return n < 0 ? -1 : n;
    }

    @Override public void close() {
        try { rec.stop(); } catch (Throwable ignored) {}
        rec.release();
    }
}
