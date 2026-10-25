package de.lokal.whisper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Kleiner Suchindex ueber alle Transkripte: Zusammenfassung fuer die Liste und der volle Text fuer die
 * Volltextsuche. Gespeichert in index.json; ein Transkript wird nur neu gelesen, wenn sich seine
 * Datei geaendert hat. So muss die Startseite nicht jedes Mal alle Transkripte komplett einlesen.
 */
public final class SearchIndex {
    private static final class Entry {
        long mtime, length;
        JSONObject summary;
        String text;      // Titel + Text, fuer die Suche
    }

    private final File dir, file;
    private final Map<String, Entry> entries = new HashMap<>();
    private boolean loaded;

    public SearchIndex(File transcriptsDir, File indexFile) { dir = transcriptsDir; file = indexFile; }

    public synchronized void invalidate(String id) { entries.remove(id); }

    private void load() {
        if (loaded) return;
        loaded = true;
        String s = Util.readText(file);
        if (s == null) return;
        try {
            JSONObject o = new JSONObject(s);
            if (o.optInt("v") != 1) return;
            JSONObject es = o.getJSONObject("entries");
            for (Iterator<String> it = es.keys(); it.hasNext(); ) {
                String id = it.next();
                JSONObject x = es.getJSONObject(id);
                Entry e = new Entry();
                e.mtime = x.optLong("m"); e.length = x.optLong("l"); e.summary = x.getJSONObject("s"); e.text = x.optString("t", "");
                entries.put(id, e);
            }
        } catch (Exception e) { Util.err("index load", e); entries.clear(); }
    }

    private void save() {
        try {
            JSONObject es = new JSONObject();
            for (Map.Entry<String, Entry> en : entries.entrySet()) {
                Entry e = en.getValue();
                es.put(en.getKey(), new JSONObject().put("m", e.mtime).put("l", e.length).put("s", e.summary).put("t", e.text));
            }
            Util.writeText(file, new JSONObject().put("v", 1).put("entries", es).toString());
        } catch (Exception e) { Util.err("index save", e); }
    }

    /** Bringt den Index auf den Stand der Dateien. */
    private void refresh() {
        load();
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        File[] dirs = dir.listFiles();
        if (dirs != null) for (File d : dirs) {
            File f = new File(d, "transcript.json");
            if (!f.isFile()) continue;
            String id = d.getName();
            seen.add(id);
            Entry e = entries.get(id);
            if (e != null && e.mtime == f.lastModified() && e.length == f.length()) continue;
            Entry n = read(id, f);
            if (n == null) { if (e != null) { entries.remove(id); changed = true; } continue; }
            entries.put(id, n);
            changed = true;
        }
        if (entries.keySet().retainAll(seen)) changed = true;
        if (changed) save();
    }

    private static Entry read(String dirName, File f) {
        String s = Util.readText(f);
        if (s == null) return null;
        try {
            JSONObject t = new JSONObject(s);
            JSONObject o = new JSONObject();
            o.put("id", t.optString("id", dirName));
            o.put("title", t.optString("title", ""));
            o.put("created", t.optString("created", ""));
            o.put("duration", t.optDouble("duration", 0));
            o.put("folder", t.isNull("folder") ? JSONObject.NULL : t.optString("folder"));
            o.put("model_name", t.optString("model_name", ""));
            o.put("audio", !t.isNull("audio") && !t.optString("audio", "").isEmpty());
            JSONArray segs = t.optJSONArray("segments");
            StringBuilder pv = new StringBuilder(), all = new StringBuilder();
            Set<Integer> spk = new HashSet<>();
            int words = 0;
            if (segs != null) for (int i = 0; i < segs.length(); i++) {
                JSONObject sg = segs.optJSONObject(i);
                if (sg == null) continue;
                String tx = sg.optString("text", "");
                if (pv.length() < 160) pv.append(tx).append(' ');
                all.append(tx).append(' ');
                words += tx.trim().isEmpty() ? 0 : tx.trim().split("\\s+").length;
                if (sg.has("speaker")) spk.add(sg.optInt("speaker", 0));
            }
            o.put("preview", pv.length() > 160 ? pv.substring(0, 160) : pv.toString().trim());
            o.put("speakers", t.optBoolean("diarization", false) ? spk.size() : 0);
            o.put("words", words);
            o.put("marks", t.optJSONArray("marks") == null ? 0 : t.optJSONArray("marks").length());
            Entry e = new Entry();
            e.mtime = f.lastModified(); e.length = f.length(); e.summary = o;
            e.text = (o.optString("title") + "\n" + all).trim();
            return e;
        } catch (Exception ex) {
            Util.err("index " + dirName, ex);
            return null;
        }
    }

    /** Liste aller Transkripte (kompakte Zusammenfassung). */
    public synchronized String list() {
        refresh();
        JSONArray out = new JSONArray();
        for (Entry e : entries.values()) out.put(e.summary);
        return out.toString();
    }

    /**
     * Volltextsuche (alle Woerter muessen vorkommen, Gross-/Kleinschreibung egal).
     * Liefert [{id, hits, snippet}] fuer die Treffer.
     */
    public synchronized String search(String query) {
        refresh();
        JSONArray out = new JSONArray();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.GERMAN);
        if (q.isEmpty()) return out.toString();
        String[] terms = q.split("\\s+");
        for (Map.Entry<String, Entry> en : entries.entrySet()) {
            String text = en.getValue().text;
            String lower = text.toLowerCase(Locale.GERMAN);
            int hits = 0, first = -1;
            boolean all = true;
            for (String term : terms) {
                int i = lower.indexOf(term), c = 0;
                if (i < 0) { all = false; break; }
                if (first < 0 || i < first) first = i;
                while (i >= 0 && c < 999) { c++; i = lower.indexOf(term, i + term.length()); }
                hits += c;
            }
            if (!all) continue;
            try {
                out.put(new JSONObject().put("id", en.getKey()).put("hits", hits).put("snippet", snippet(text, first, terms[0].length())));
            } catch (Exception ignored) {}
        }
        return out.toString();
    }

    static String snippet(String text, int at, int len) {
        int nl = text.indexOf('\n');
        if (nl >= 0 && at < nl) at = Math.min(text.length() - 1, nl + 1); // Treffer im Titel: Textanfang zeigen
        int from = Math.max(0, at - 50), to = Math.min(text.length(), at + len + 90);
        while (from > 0 && from < at && !Character.isWhitespace(text.charAt(from - 1))) from++;
        while (to < text.length() && to > at + len && !Character.isWhitespace(text.charAt(to - 1))) to--;
        String s = text.substring(from, Math.max(from, to)).replace('\n', ' ').trim();
        return (from > 0 ? "… " : "") + s + (to < text.length() ? " …" : "");
    }
}
