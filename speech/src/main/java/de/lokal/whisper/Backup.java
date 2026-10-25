package de.lokal.whisper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Sicherung aller Transkripte als ZIP (wahlweise mit Audio) und Wiederherstellung daraus.
 * Funktioniert zwischen Handy und PC: dieselbe Datei laesst sich auf beiden einspielen.
 */
public final class Backup {
    static final String ROOT = "Whisper-Sicherung/";
    private static final Pattern ENTRY = Pattern.compile("^(?:Whisper-Sicherung/)?transcripts/([0-9a-zA-Z_-]{3,40})/([0-9A-Za-z._-]{1,80})$");

    private Backup() {}

    public static String fileName() {
        return "Whisper-Sicherung-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".zip";
    }

    /** Schreibt die Sicherung; liefert die Anzahl der Transkripte. */
    public static int export(Engine e, OutputStream os, boolean withAudio) throws Exception {
        int n = 0;
        try (ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.setLevel(6);
            File[] dirs = e.transcripts.listFiles();
            if (dirs != null) for (File d : dirs) {
                File t = new File(d, "transcript.json");
                if (!t.isFile()) continue;
                put(zip, ROOT + "transcripts/" + d.getName() + "/transcript.json", t);
                if (withAudio) {
                    String audio = null;
                    try { audio = new JSONObject(Util.readText(t)).optString("audio", null); } catch (Exception ignored) {}
                    File a = audio == null || audio.isEmpty() || "null".equals(audio) ? null : new File(d, audio);
                    if (a != null && a.isFile()) put(zip, ROOT + "transcripts/" + d.getName() + "/" + a.getName(), a);
                }
                n++;
            }
            File rep = new File(e.dataDir, Replacements.FILE);
            if (rep.isFile()) put(zip, ROOT + Replacements.FILE, rep);
            JSONObject m = new JSONObject();
            m.put("app", "Whisper"); m.put("version", 1); m.put("created", Engine.isoNow()); m.put("transcripts", n);
            m.put("with_audio", withAudio); m.put("device", e.platform.deviceKind());
            zip.putNextEntry(new ZipEntry(ROOT + "manifest.json"));
            zip.write(m.toString(2).getBytes(Util.UTF8));
            zip.closeEntry();
        }
        return n;
    }

    private static void put(ZipOutputStream zip, String name, File f) throws Exception {
        ZipEntry ze = new ZipEntry(name);
        ze.setTime(f.lastModified());
        String ext = Util.ext(f.getName());
        boolean packed = ext.equals("mp3") || ext.equals("m4a") || ext.equals("ogg") || ext.equals("opus") || ext.equals("aac") || ext.equals("webm");
        zip.putNextEntry(ze);
        if (packed) zip.setLevel(0);
        try (InputStream in = new FileInputStream(f)) { Util.copy(in, zip); }
        zip.closeEntry();
        if (packed) zip.setLevel(6);
    }

    private static String contentKey(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            o.remove("id");
            return Engine.sha256Hex(o.toString());
        } catch (Exception e) { return null; }
    }

    /** Spielt eine Sicherung ein. Vorhandene Transkripte bleiben unveraendert; liefert eine Meldung. */
    public static String restore(Engine e, InputStream in) throws Exception {
        File staging = new File(e.cacheDir, "restore-" + Util.hex(4));
        Util.deleteRecursive(staging);
        staging.mkdirs();
        int added = 0, same = 0, renamed = 0;
        boolean any = false;
        try {
            try (ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {
                    if (ze.isDirectory()) continue;
                    String name = ze.getName().replace('\\', '/');
                    if (name.equals(ROOT + Replacements.FILE) || name.equals(Replacements.FILE)) {
                        File rep = new File(e.dataDir, Replacements.FILE);
                        if (!rep.isFile()) try (OutputStream out = new FileOutputStream(rep)) { Util.copy(zin, out); }
                        continue;
                    }
                    Matcher m = ENTRY.matcher(name);
                    if (!m.matches() || m.group(2).startsWith(".")) continue;
                    any = true;
                    File dst = new File(new File(staging, m.group(1)), m.group(2));
                    dst.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(dst)) { Util.copy(zin, out); }
                }
            }
            if (!any) throw new Engine.UserError("Das ist keine Whisper-Sicherung (keine Transkripte gefunden).");
            // Inhalt aller vorhandenen Transkripte (ohne Kennung), damit nichts doppelt eingespielt wird
            java.util.Set<String> existing = new java.util.HashSet<>();
            File[] have = e.transcripts.listFiles();
            if (have != null) for (File d : have) {
                String c = contentKey(Util.readText(new File(d, "transcript.json")));
                if (c != null) existing.add(c);
            }
            File[] dirs = staging.listFiles();
            if (dirs != null) for (File d : dirs) {
                File t = new File(d, "transcript.json");
                String text = Util.readText(t);
                if (text == null) continue;
                JSONObject json;
                try { json = new JSONObject(text); } catch (Exception ex) { continue; }
                String key = contentKey(text);
                if (key != null && existing.contains(key)) { same++; continue; }
                File target = new File(e.transcripts, d.getName());
                if (target.exists()) {
                    String id = Util.hex(6);
                    json.put("id", id);
                    Util.writeText(t, json.toString());
                    target = new File(e.transcripts, id);
                    renamed++;
                }
                if (!d.renameTo(target)) {
                    target.mkdirs();
                    File[] fs = d.listFiles();
                    if (fs != null) for (File f : fs) Util.copyFile(f, new File(target, f.getName()));
                }
                added++;
                if (key != null) existing.add(key);
                e.index.invalidate(target.getName());
            }
        } finally {
            Util.deleteRecursive(staging);
        }
        StringBuilder msg = new StringBuilder();
        msg.append(added == 1 ? "1 Transkript übernommen" : added + " Transkripte übernommen");
        if (same > 0) msg.append(", ").append(same).append(" waren schon vorhanden");
        if (renamed > 0) msg.append(" (").append(renamed).append(" mit gleicher Kennung als Kopie)");
        return msg.append('.').toString();
    }
}
