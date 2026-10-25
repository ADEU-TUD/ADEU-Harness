package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class ReplacementsTest {
    private static Replacements of(String... pairs) {
        JSONArray a = new JSONArray();
        for (int i = 0; i + 1 < pairs.length; i += 2) a.put(new JSONObject().put("from", pairs[i]).put("to", pairs[i + 1]));
        return Replacements.parse(a);
    }

    @Test void replacesWholeWordsIgnoringCase() {
        Replacements r = of("Maier", "Meyer", "Sie es", "SDS");
        assertEquals("Herr Meyer und Frau Meyer.", r.apply("Herr maier und Frau Maier."));
        assertEquals("Maierhof bleibt", r.apply("Maierhof bleibt"));
        assertEquals("Das SDS-Projekt", r.apply("Das Sie  es-Projekt"));
    }

    @Test void specialCharactersAreLiteral() {
        Replacements r = of("C++", "C plus plus", "a.b", "x$1");
        assertEquals("Wir nutzen C plus plus", r.apply("Wir nutzen C++"));
        assertEquals("Datei x$1 und aXb", r.apply("Datei a.b und aXb"));
    }

    @Test void keepsWordTimesForSingleWords() {
        Replacements r = of("Maier", "Meyer");
        List<Speech.Word> w = Arrays.asList(new Speech.Word(0, 0.3, "Herr"), new Speech.Word(0.3, 0.8, "Maier."));
        List<Speech.Word> out = r.applyWords(w, "Herr Meyer.");
        assertNotNull(out);
        assertEquals("Meyer.", out.get(1).text);
        assertEquals(0.3, out.get(1).start, 1e-9);
        assertNull(of("Herr Maier", "Dr. Meyer").applyWords(w, "Dr. Meyer."), "Ersetzung ueber zwei Woerter: keine Wortzeiten");
    }
}
