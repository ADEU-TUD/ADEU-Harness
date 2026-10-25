package de.lokal.whisper;

import java.util.regex.Pattern;

/** Entfernt typische Whisper-Halluzinationen und Wiederholungsschleifen. Reines Java. */
final class TextClean {
    private TextClean() {}

    private static final Pattern BRACKETS_ONLY = Pattern.compile("^[\\s\\[\\(\\*♪]*(musik|music|applaus|applause|lachen|laughter|gelächter|stille|silence|beifall|räuspern)?[\\s\\]\\)\\*♪.!]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HALLU = Pattern.compile(
        "(untertitel(ung)?\\s+(im auftrag|von|der|des|by)|amara\\.org|copyright\\s|©|www\\.[a-z]|zdf\\s?\\d{4}|swr\\s?\\d{4}|vielen dank (fürs|für's|für das) zu(schauen|sehen|hören)|thanks? for watching|subtitles by|untertitel:)",
        Pattern.CASE_INSENSITIVE);

    static String clean(String text, double seconds) {
        if (text == null) return "";
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return "";
        if (BRACKETS_ONLY.matcher(t).matches()) return "";
        if (HALLU.matcher(t).find() && (seconds < 6 || t.length() < 80)) return "";
        t = collapseRepeats(t);
        return t;
    }

    /** "ja ja ja ja ja ja ja" oder wiederholte Saetze auf hoechstens 3 Wiederholungen kuerzen. */
    static String collapseRepeats(String t) {
        String[] w = t.split(" ");
        if (w.length < 8) return t;
        for (int n = 1; n <= 8; n++) {
            StringBuilder out = new StringBuilder();
            int i = 0, changed = 0;
            while (i < w.length) {
                int reps = 1;
                while (i + (reps + 1) * n <= w.length && same(w, i, i + reps * n, n)) reps++;
                int keep = reps > 3 ? 3 : reps;
                if (reps > 3) changed++;
                for (int r = 0; r < keep; r++) for (int k = 0; k < n && i + r * n + k < w.length; k++) {
                    if (out.length() > 0) out.append(' ');
                    out.append(w[i + r * n + k]);
                }
                i += reps * n;
                if (reps == 1) { /* einzelnes Wort bereits angehaengt */ }
            }
            if (changed > 0) { w = out.toString().split(" "); }
        }
        return join(w);
    }

    private static boolean same(String[] w, int a, int b, int n) {
        for (int k = 0; k < n; k++) {
            if (b + k >= w.length) return false;
            if (!norm(w[a + k]).equals(norm(w[b + k]))) return false;
        }
        return true;
    }

    private static String norm(String s) { return s.toLowerCase().replaceAll("[.,!?;:]", ""); }

    private static String join(String[] w) {
        StringBuilder sb = new StringBuilder();
        for (String s : w) { if (sb.length() > 0) sb.append(' '); sb.append(s); }
        return sb.toString();
    }
}
