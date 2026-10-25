package de.lokal.whisper.desktop;

import de.lokal.whisper.Platform;
import de.lokal.whisper.Util;
import de.lokal.whisper.Resampler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Mikrofon ueber Java Sound. Nimmt bevorzugt direkt mit 16 kHz mono auf; kann das Geraet das nicht,
 * wird mit 48/44,1 kHz aufgenommen und umgerechnet.
 */
final class JavaSoundMic implements Platform.MicSource {
    private static final AudioFormat[] FORMATS = {
        new AudioFormat(16000f, 16, 1, true, false),
        new AudioFormat(48000f, 16, 1, true, false),
        new AudioFormat(44100f, 16, 1, true, false),
        new AudioFormat(48000f, 16, 2, true, false),
        new AudioFormat(44100f, 16, 2, true, false),
        new AudioFormat(32000f, 16, 1, true, false),
        new AudioFormat(22050f, 16, 1, true, false),
    };

    private final TargetDataLine line;
    private final AudioFormat fmt;
    private final Resampler resampler;
    private byte[] raw = new byte[0];
    private float[] fifo = new float[32000];
    private int fifoN;

    JavaSoundMic(String deviceId) throws Exception {
        Mixer mixer = find(deviceId);
        TargetDataLine l = null;
        AudioFormat chosen = null;
        Exception last = null;
        for (AudioFormat f : FORMATS) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
            try {
                if (mixer != null) { if (!mixer.isLineSupported(info)) continue; l = (TargetDataLine) mixer.getLine(info); }
                else { if (!AudioSystem.isLineSupported(info)) continue; l = (TargetDataLine) AudioSystem.getLine(info); }
                l.open(f, (int) (f.getFrameRate() * f.getFrameSize() / 5)); // 200 ms Puffer
                chosen = f;
                break;
            } catch (Exception e) {
                last = e;
                l = null;
            }
        }
        if (l == null) throw new IOException("Kein Mikrofon gefunden oder Zugriff verweigert"
                + (SpeechWin.WINDOWS ? " (Windows-Einstellungen › Datenschutz › Mikrofon: „Desktop-Apps den Zugriff erlauben“)" : "")
                + (last != null ? ": " + last.getMessage() : ""));
        line = l;
        fmt = chosen;
        resampler = fmt.getSampleRate() == 16000f && fmt.getChannels() == 1 ? null
                : new Resampler((int) fmt.getSampleRate(), 16000, (d, n) -> push(d, n));
        line.start();
        Util.log("Mikrofon: " + (mixer == null ? "Standard" : mixer.getMixerInfo().getName()) + " · " + fmt);
    }

    private void push(float[] d, int n) {
        if (fifoN + n > fifo.length) fifo = java.util.Arrays.copyOf(fifo, Math.max(fifo.length * 2, fifoN + n));
        System.arraycopy(d, 0, fifo, fifoN, n);
        fifoN += n;
    }

    @Override public int read(short[] buf) throws IOException {
        if (resampler == null) {
            if (raw.length < buf.length * 2) raw = new byte[buf.length * 2];
            int n = line.read(raw, 0, buf.length * 2);
            if (n < 0) return -1;
            for (int i = 0; i < n / 2; i++) buf[i] = (short) ((raw[2 * i] & 0xff) | (raw[2 * i + 1] << 8));
            return n / 2;
        }
        int ch = fmt.getChannels();
        while (fifoN < buf.length) {
            int frames = (int) (fmt.getSampleRate() / 10);
            int bytes = frames * 2 * ch;
            if (raw.length < bytes) raw = new byte[bytes];
            int n = line.read(raw, 0, bytes);
            if (n <= 0) { if (!line.isOpen()) return -1; continue; }
            int got = n / (2 * ch);
            float[] mono = new float[got];
            for (int f = 0; f < got; f++) {
                float acc = 0;
                for (int c = 0; c < ch; c++) { int i = (f * ch + c) * 2; acc += (short) ((raw[i] & 0xff) | (raw[i + 1] << 8)) / 32768f; }
                mono[f] = acc / ch;
            }
            resampler.process(mono, got);
        }
        for (int i = 0; i < buf.length; i++) {
            float v = Math.max(-1f, Math.min(1f, fifo[i]));
            buf[i] = (short) (v * 32767f);
        }
        System.arraycopy(fifo, buf.length, fifo, 0, fifoN - buf.length);
        fifoN -= buf.length;
        return buf.length;
    }

    @Override public void close() {
        try { line.stop(); } catch (Exception ignored) {}
        try { line.close(); } catch (Exception ignored) {}
    }

    private static Mixer find(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) if (mi.getName().equals(id)) return AudioSystem.getMixer(mi);
        return null;
    }

    /** Alle Aufnahmegeraete: [{id, name}]. */
    static JSONArray list() {
        JSONArray a = new JSONArray();
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(mi);
                boolean capture = false;
                for (Line.Info li : m.getTargetLineInfo()) if (li instanceof DataLine.Info && TargetDataLine.class.isAssignableFrom(li.getLineClass())) capture = true;
                if (!capture || mi.getName().startsWith("Port ")) continue;
                a.put(new JSONObject().put("id", mi.getName()).put("name", mi.getName().replace("Primärer Soundaufnahmetreiber", "Standard (Windows)")));
            } catch (Exception ignored) {}
        }
        return a;
    }
}
