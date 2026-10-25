package de.lokal.whisper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** Kleine Helfer fuer Dateien und Logging. */
public final class Util {
    /** Ziel der Log-Ausgaben (Android: Logcat, Windows: log.txt). */
    public interface Logger { void log(String msg); void err(String msg, Throwable t); }

    static final Charset UTF8 = Charset.forName("UTF-8");
    public static volatile Logger logger = new Logger() {
        @Override public void log(String msg) { System.out.println(msg); }
        @Override public void err(String msg, Throwable t) { System.err.println(msg); if (t != null) t.printStackTrace(); }
    };

    private Util() {}

    public static void log(String msg) { logger.log(msg); }

    public static void err(String msg, Throwable t) { logger.err(msg, t); }

    public static String readText(File f) {
        if (f == null || !f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), UTF8);
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    public static boolean writeText(File f, String text) {
        return writeBytes(f, text == null ? new byte[0] : text.getBytes(UTF8));
    }

    /**
     * Writes atomically: first into a temporary file (flushed to disk), then renamed over the old one. A crash or
     * an empty battery leaves either the old or the new version, never a half-written file.
     */
    public static boolean writeBytes(File f, byte[] data) {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            err("write " + f, e);
            tmp.delete();
            return false;
        }
        try {
            java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            try {
                java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e2) {
                err("rename " + f, e2);
                return false;
            }
        }
    }

    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
        return total;
    }

    public static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) { copy(in, out); }
    }

    public static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        return f.delete();
    }

    public static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) s += size(k);
        return s;
    }

    public static String safeName(String name, String fallback) {
        if (name == null) name = "";
        String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        while (s.startsWith(".")) s = s.substring(1);
        if (s.length() > 90) s = s.substring(0, 90).trim();
        return s.isEmpty() ? fallback : s;
    }

    public static String ext(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i < 0 || i < name.length() - 6) return "";
        return name.substring(i + 1).toLowerCase();
    }

    public static String mimeFor(String name) {
        switch (ext(name)) {
            case "pdf": return "application/pdf";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt": return "text/plain";
            case "md": return "text/markdown";
            case "srt": return "application/x-subrip";
            case "vtt": return "text/vtt";
            case "json": return "application/json";
            case "wav": return "audio/wav";
            case "mp3": return "audio/mpeg";
            case "m4a": case "aac": return "audio/mp4";
            case "ogg": case "opus": case "oga": return "audio/ogg";
            case "webm": return "audio/webm";
            case "flac": return "audio/flac";
            case "mp4": return "video/mp4";
            default: return "application/octet-stream";
        }
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public static String hex(int bytes) {
        java.security.SecureRandom r = RANDOM;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes; i++) sb.append(String.format("%02x", r.nextInt(256)));
        return sb.toString();
    }
}

