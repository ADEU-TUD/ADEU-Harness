package de.lokal.whisper.desktop;

import de.lokal.whisper.Platform;
import de.lokal.whisper.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest jede Audio- und Videodatei (MP3, M4A, WhatsApp-Opus, MP4, MKV …) mit ffmpeg, das der App
 * beiliegt, und speichert Aufnahmen platzsparend als Opus (etwa 10 MB pro Stunde statt 115 MB).
 */
final class Ffmpeg {
    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");

    private Ffmpeg() {}

    /** Folder of the app's resources (tools/ffmpeg.exe, native/*.dll); set by the app. */
    static volatile File resources;

    static String exe() {
        File f = resources == null ? null : new File(resources, "tools/" + (SpeechWin.WINDOWS ? "ffmpeg.exe" : "ffmpeg"));
        if (f != null && f.isFile()) return f.getAbsolutePath();
        return SpeechWin.WINDOWS ? null : "ffmpeg"; // Entwicklung unter Linux: ffmpeg aus dem System
    }

    static long decode(File src, File pcmOut, Platform.Progress progress, Platform.Cancel cancel) throws Exception {
        String exe = exe();
        if (exe == null) throw new Exception("ffmpeg fehlt in der App");
        List<String> cmd = new ArrayList<>(Arrays.asList(exe, "-nostdin", "-hide_banner", "-y", "-i", src.getAbsolutePath(),
                "-vn", "-sn", "-dn", "-ac", "1", "-ar", "16000", "-acodec", "pcm_s16le", "-f", "s16le",
                "-progress", "pipe:1", "-nostats", pcmOut.getAbsolutePath()));
        run(cmd, progress, cancel);
        return pcmOut.length() / 2;
    }

    /** Opus in Ogg (oder AAC, falls Opus fehlt); null bei Fehler. */
    static String encode(File pcm, File dir, String base) {
        String exe = exe();
        if (exe == null) return null;
        String[][] variants = {
            {base + ".ogg", "-c:a", "libopus", "-b:a", "24k", "-application", "voip"},
            {base + ".m4a", "-c:a", "aac", "-b:a", "48k"},
        };
        for (String[] v : variants) {
            File out = new File(dir, v[0]);
            List<String> cmd = new ArrayList<>(Arrays.asList(exe, "-nostdin", "-hide_banner", "-y", "-f", "s16le", "-ar", "16000", "-ac", "1",
                    "-i", pcm.getAbsolutePath()));
            cmd.addAll(Arrays.asList(v).subList(1, v.length));
            cmd.add(out.getAbsolutePath());
            try {
                run(cmd, null, null);
                if (out.length() > 0) return out.getName();
            } catch (Exception e) {
                Util.err("ffmpeg " + v[0], e);
            }
            out.delete();
        }
        return null;
    }

    private static void run(List<String> cmd, Platform.Progress progress, Platform.Cancel cancel) throws Exception {
        Process p = new ProcessBuilder(cmd).start();
        final double[] duration = {0};
        final LinkedList<String> tail = new LinkedList<>();
        Thread err = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = DURATION.matcher(line);
                    if (m.find() && duration[0] == 0) duration[0] = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
                    synchronized (tail) { tail.add(line); if (tail.size() > 15) tail.removeFirst(); }
                }
            } catch (Exception ignored) {}
        }, "ffmpeg-err");
        err.setDaemon(true);
        err.start();
        Thread watch = null;
        if (cancel != null) {
            watch = new Thread(() -> {
                while (p.isAlive()) {
                    if (cancel.cancelled()) { p.destroyForcibly(); return; }
                    try { Thread.sleep(200); } catch (InterruptedException e) { return; }
                }
            }, "ffmpeg-cancel");
            watch.setDaemon(true);
            watch.start();
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            long last = 0;
            while ((line = r.readLine()) != null) {
                if (progress != null && line.startsWith("out_time_us=") && duration[0] > 0) {
                    try {
                        double t = Long.parseLong(line.substring(12).trim()) / 1e6;
                        long now = System.currentTimeMillis();
                        if (now - last > 300) { last = now; progress.on(Math.max(0, Math.min(1, t / duration[0]))); }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        int code = p.waitFor();
        err.join(2000);
        if (cancel != null && cancel.cancelled()) throw new InterruptedException("abgebrochen");
        if (code != 0) {
            String msg;
            synchronized (tail) { msg = String.join(" | ", tail); }
            throw new Exception("ffmpeg " + code + ": " + msg);
        }
        if (progress != null) progress.on(1.0);
    }
}
