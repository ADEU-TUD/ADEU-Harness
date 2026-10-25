package de.lokal.whisper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Was die Engine vom Betriebssystem braucht. Android und Windows liefern je eine Umsetzung,
 * die gesamte uebrige Logik (Warteschlange, Transkription, Suche, Sicherung) ist gemeinsam.
 */
public interface Platform {
    interface Progress { void on(double fraction); }
    interface Cancel { boolean cancelled(); }

    /** Liefert 16-kHz-Mono-Samples (PCM16) vom Mikrofon. */
    interface MicSource {
        /** Blockiert, bis Samples da sind; gibt die Anzahl zurueck (oder -1 bei Fehler). */
        int read(short[] buf) throws IOException;
        void close();
    }

    /** Transkripte, Einstellungen, Warteschlange (bleibt bei Updates erhalten). */
    File dataDir();
    /** Modelle (gross; unter Windows lokal, nicht im servergespeicherten Profil). */
    File modelsDir();
    /** Temporaere Arbeitsdateien. */
    File cacheDir();

    /** "phone" oder "pc" (wird im Transkript vermerkt). */
    String deviceKind();
    String deviceName();
    String version();

    /** Eingebaute Datei aus models/ (VAD, Sprechersegmentierung, Kalibrierung). */
    InputStream openBuiltin(String name) throws IOException;

    /** Ereignis (JSON) an die Oberflaeche. */
    void emit(String json);

    /** Zustand hat sich geaendert: Aufnahme/Transkription/Download laeuft oder nicht mehr. */
    void onActivity(Engine engine);

    /** Eine Transkription ist fertig oder fehlgeschlagen (Benachrichtigung, falls die App nicht sichtbar ist). */
    void onJobFinished(Engine engine, Engine.Job job);

    /** Dekodiert eine Audio-/Videodatei nach 16 kHz mono PCM16 (little endian); liefert die Anzahl Samples. */
    long decode(File src, File pcmOut, Progress progress, Cancel cancel) throws Exception;

    /**
     * Speichert 16-kHz-PCM platzsparend (Opus/AAC) als Datei in {@code dir}. Liefert den Dateinamen
     * oder null, wenn es nicht geht (dann wird WAV gespeichert).
     */
    String encodeSpeech(File pcm, File dir, String baseName);

    /** Oeffnet das Mikrofon (deviceId leer = Standard). */
    MicSource openMic(String deviceId) throws Exception;

    /** Anzahl Rechenkerne fuer die Erkennung. */
    int threads();
}
