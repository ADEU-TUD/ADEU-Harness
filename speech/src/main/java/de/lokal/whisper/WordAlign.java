package de.lokal.whisper;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Uebertraegt gemessene Wortzeiten auf einen Text. Whisper (und der Uni-Server) liefern keine verlaesslichen
 * Zeiten pro Wort; Parakeet misst sie auf etwa eine Zehntelsekunde genau. Deshalb wird derselbe Abschnitt
 * zusaetzlich mit Parakeet erkannt und dessen Woerter mit dem Whisper-Text abgeglichen (Editierabstand auf
 * Wortebene, auch "ein Wort = zwei Woerter"). Gemessen an LibriSpeech (958 Woerter, Wortgrenzen per Forced
 * Alignment): Median 50 ms Abweichung am Wortanfang, 0,4 % ueber 250 ms; geschaetzte Zeiten liegen bei 200 ms.
 *
 * Jedes Wort bekommt eine Guete: 'm' gemessen (gleiches Wort), 's' gemessen, aber anders geschrieben
 * (z. B. "45" gegen "fuenfundvierzig"), 'i' nicht gemessen und zwischen gemessene Nachbarn gelegt.
 * Reines Java (testbar).
 */
public final class WordAlign {
    private WordAlign() {}

    /** Kosten fuer ein Wort ohne Gegenstueck (0 = gleich, 1 = ganz verschieden). */
    private static final double GAP = 0.7;
    /** Zusammenziehen nur, wenn die Schreibung danach fast gleich ist. */
    private static final double MERGE = 0.2;

    public static String norm(String w) { return w.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }

    private static int lev(String a, String b) {
        int[] p = new int[b.length() + 1], c = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) p[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            c[0] = i;
            for (int j = 1; j <= b.length(); j++) c[j] = Math.min(Math.min(c[j - 1] + 1, p[j] + 1), p[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = p; p = c; c = t;
        }
        return p[b.length()];
    }

    /** Unterschied zweier Woerter ohne Satzzeichen und Gross-/Kleinschreibung: 0 gleich … 1 ganz verschieden. */
    public static double dist(String a, String b) {
        a = norm(a); b = norm(b);
        if (a.isEmpty() && b.isEmpty()) return 0;
        return (double) lev(a, b) / Math.max(a.length(), b.length());
    }

    /** Woerter eines Textes, wie die App sie markiert (an Leerraum getrennt). */
    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : text.trim().split("\\s+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /**
     * Zeiten fuer die Woerter von {@code text} aus den gemessenen Woertern {@code ref} (absolute Zeiten).
     * {@code start}/{@code end}: Grenzen des Abschnitts, falls am Rand Woerter nicht gemessen wurden.
     * Liefert eine leere Liste, wenn nichts gemessen wurde.
     */
    public static List<Speech.Word> align(String text, List<Speech.Word> ref, double start, double end) {
        List<String> words = split(text);
        int n = words.size(), m = ref == null ? 0 : ref.size();
        if (n == 0 || m == 0) return new ArrayList<>();
        double[][] d = new double[n + 1][m + 1];
        byte[][] op = new byte[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                if (i == 0 && j == 0) continue;
                double best = Double.MAX_VALUE;
                byte bo = -1;
                if (i > 0 && j > 0) { double c = d[i - 1][j - 1] + dist(words.get(i - 1), ref.get(j - 1).text); if (c < best) { best = c; bo = 0; } }
                if (i > 0) { double c = d[i - 1][j] + GAP; if (c < best) { best = c; bo = 1; } }
                if (j > 0) { double c = d[i][j - 1] + GAP; if (c < best) { best = c; bo = 2; } }
                // Ein Wort im Text = zwei gemessene ("Gruppenarbeit" / "Gruppen arbeit") und umgekehrt, nur bei fast gleicher Schreibung
                if (i > 0 && j > 1) { double x = dist(words.get(i - 1), ref.get(j - 2).text + ref.get(j - 1).text); double c = d[i - 1][j - 2] + x + 0.05; if (x <= MERGE && c < best) { best = c; bo = 3; } }
                if (i > 1 && j > 0) { double x = dist(words.get(i - 2) + words.get(i - 1), ref.get(j - 1).text); double c = d[i - 2][j - 1] + x + 0.05; if (x <= MERGE && c < best) { best = c; bo = 4; } }
                d[i][j] = best;
                op[i][j] = bo;
            }
        }
        Speech.Word[] out = new Speech.Word[n];
        int i = n, j = m;
        while (i > 0 || j > 0) {
            byte o = op[i][j];
            if (o == 0) {
                Speech.Word r = ref.get(j - 1);
                // Bei erneutem Abgleich (Text bearbeitet) bleibt die Guete des alten Worts erhalten
                out[i - 1] = new Speech.Word(r.start, r.end, words.get(i - 1), dist(words.get(i - 1), r.text) <= 0.34 ? r.q : 's');
                i--; j--;
            } else if (o == 1) {
                i--;
            } else if (o == 2) {
                j--;
            } else if (o == 3) {
                Speech.Word a = ref.get(j - 2), b = ref.get(j - 1);
                out[i - 1] = new Speech.Word(a.start, b.end, words.get(i - 1), a.q == 'm' && b.q == 'm' ? 'm' : 's');
                i--; j -= 2;
            } else {
                // Zwei Woerter, ein gemessenes: nach Buchstaben teilen (die Grenze dazwischen ist nicht gemessen)
                Speech.Word r = ref.get(j - 1);
                String a = words.get(i - 2), b = words.get(i - 1);
                double mid = r.start + (r.end - r.start) * Math.max(1, norm(a).length()) / Math.max(2, norm(a + b).length());
                out[i - 2] = new Speech.Word(r.start, mid, a, 's');
                out[i - 1] = new Speech.Word(mid, r.end, b, 's');
                i -= 2; j--;
            }
        }
        // Nicht gemessene Woerter zwischen die gemessenen Nachbarn legen
        boolean any = false;
        for (Speech.Word w : out) if (w != null) { any = true; break; }
        if (!any) return new ArrayList<>();
        for (int a = 0; a < n; ) {
            if (out[a] != null) { a++; continue; }
            int b = a;
            while (b < n && out[b] == null) b++;
            double from = a > 0 ? out[a - 1].end : Math.min(start, out[b].start);
            double to = b < n ? out[b].start : Math.max(end, out[a - 1].end);
            if (to < from) to = from;
            double tot = 0;
            for (int k = a; k < b; k++) tot += norm(words.get(k)).length() + 1;
            double t = from;
            for (int k = a; k < b; k++) {
                double len = (to - from) * (norm(words.get(k)).length() + 1) / tot;
                out[k] = new Speech.Word(round(t), round(t + len), words.get(k), 'i');
                t += len;
            }
            a = b;
        }
        // Gleiche Anfangszeiten (aufgeteilte Woerter) duerfen nicht rueckwaerts laufen
        for (int k = 1; k < n; k++) if (out[k].start < out[k - 1].start) out[k] = new Speech.Word(out[k - 1].start, Math.max(out[k].end, out[k - 1].start), out[k].text, out[k].q);
        return new ArrayList<>(Arrays.asList(out));
    }

    /** Woerter fuer transcript.json: [start, end, text] und bei nicht exakt gemessenen Woertern die Guete als viertes Feld. */
    public static JSONArray toJson(List<Speech.Word> words) throws Exception {
        JSONArray a = new JSONArray();
        for (Speech.Word w : words) {
            JSONArray x = new JSONArray().put(round(w.start)).put(round(w.end)).put(w.text);
            if (w.q != 'm') x.put(String.valueOf(w.q));
            a.put(x);
        }
        return a;
    }

    /** Anzahl gemessen / anders geschrieben / zwischengelegt. */
    public static int[] count(List<Speech.Word> words) {
        int[] c = new int[3];
        for (Speech.Word w : words) c[w.q == 's' ? 1 : w.q == 'i' ? 2 : 0]++;
        return c;
    }

    /** Sprachen, die Parakeet v3 erkennt (fuer andere Sprachen gibt es keine gemessenen Wortzeiten). */
    public static final List<String> PARAKEET_LANGUAGES = Arrays.asList(
            "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it", "lv", "lt", "mt", "pl", "pt", "ro",
            "sk", "sl", "es", "sv", "ru", "uk");

    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}
