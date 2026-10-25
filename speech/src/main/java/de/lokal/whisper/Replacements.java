package de.lokal.whisper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ersetzungsliste fuer Namen und Fachbegriffe, die immer gleich falsch erkannt werden
 * (z. B. "Herr Maier" -> "Herr Meyer"). Gilt fuer ganze Woerter, Gross-/Kleinschreibung egal.
 * Gespeichert in replacements.json: [{"from": "...", "to": "..."}].
 */
public final class Replacements {
    public static final String FILE = "replacements.json";

    private static final class Rule { final Pattern p; final String to; Rule(Pattern p, String to) { this.p = p; this.to = to; } }

    private final List<Rule> rules = new ArrayList<>();

    public static Replacements load(File dataDir) {
        String s = Util.readText(new File(dataDir, FILE));
        try { return parse(s == null ? new JSONArray() : new JSONArray(s)); }
        catch (Exception e) { Util.err("replacements", e); return new Replacements(); }
    }

    public static Replacements parse(JSONArray a) {
        Replacements r = new Replacements();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String from = o.optString("from", "").trim();
            if (from.isEmpty()) continue;
            String to = o.optString("to", "");
            String rx = "(?<![\\p{L}\\p{N}])" + Pattern.quote(from).replace(" ", "\\E\\s+\\Q") + "(?![\\p{L}\\p{N}])";
            r.rules.add(new Rule(Pattern.compile(rx, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), to));
        }
        return r;
    }

    public boolean isEmpty() { return rules.isEmpty(); }

    public String apply(String text) {
        if (text == null || rules.isEmpty()) return text;
        String t = text;
        for (Rule r : rules) t = r.p.matcher(t).replaceAll(Matcher.quoteReplacement(r.to));
        return t;
    }

    /**
     * Wendet die Liste auf einzelne Woerter an (fuer die Wort-Hervorhebung). Liefert null, wenn die
     * Woerter danach nicht mehr zum Text passen wuerden (z. B. Ersetzung ueber mehrere Woerter).
     */
    public List<Speech.Word> applyWords(List<Speech.Word> words, String newText) {
        if (words == null) return null;
        List<Speech.Word> out = new ArrayList<>();
        for (Speech.Word w : words) {
            String t = apply(w.text);
            if (t.trim().isEmpty()) continue;
            out.add(new Speech.Word(w.start, w.end, t.trim()));
        }
        return sameText(out, newText) ? out : null;
    }

    static boolean sameText(List<Speech.Word> words, String text) {
        StringBuilder sb = new StringBuilder();
        for (Speech.Word w : words) { if (sb.length() > 0) sb.append(' '); sb.append(w.text); }
        return norm(sb.toString()).equals(norm(text));
    }

    private static String norm(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }
}
