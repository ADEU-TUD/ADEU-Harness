package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

class WordAlignTest {
    private static List<Speech.Word> ref(Object... x) {
        List<Speech.Word> out = new ArrayList<>();
        for (int i = 0; i < x.length; i += 3) out.add(new Speech.Word(((Number) x[i]).doubleValue(), ((Number) x[i + 1]).doubleValue(), (String) x[i + 2]));
        return out;
    }

    @Test void takesMeasuredTimesForTheSameWords() {
        // Whisper schreibt "Sie" gross, Parakeet klein und ohne Komma – trotzdem dieselben Woerter
        List<Speech.Word> w = WordAlign.align("Wie haben Sie, die Stunde erlebt?",
                ref(1.0, 1.2, "wie", 1.3, 1.6, "haben", 1.7, 1.9, "sie", 2.0, 2.1, "die", 2.2, 2.6, "Stunde", 2.7, 3.1, "erlebt?"), 0.8, 3.3);
        assertEquals(6, w.size());
        assertEquals("Sie,", w.get(2).text);
        assertEquals(1.7, w.get(2).start, 1e-9);
        assertEquals(3.1, w.get(5).end, 1e-9);
        for (Speech.Word x : w) assertEquals('m', x.q);
    }

    @Test void numbersAndCompoundsKeepTheMeasuredRegion() {
        List<Speech.Word> w = WordAlign.align("Etwa 45 Minuten Gruppenarbeit.",
                ref(0.0, 0.3, "Etwa", 0.4, 1.4, "fünfundvierzig", 1.5, 1.9, "Minuten", 2.0, 2.4, "Gruppen", 2.4, 2.9, "arbeit."), 0, 3);
        assertEquals(4, w.size());
        assertEquals('s', w.get(1).q, "anders geschrieben, aber an derselben Stelle gemessen");
        assertEquals(0.4, w.get(1).start, 1e-9);
        assertEquals(1.4, w.get(1).end, 1e-9);
        assertEquals('m', w.get(3).q);
        assertEquals(2.0, w.get(3).start, 1e-9);
        assertEquals(2.9, w.get(3).end, 1e-9);
    }

    @Test void wordsWithoutMeasurementLieBetweenTheirNeighbours() {
        List<Speech.Word> w = WordAlign.align("Ich denke ähm danach ist gut",
                ref(0.0, 0.2, "Ich", 0.3, 0.6, "denke", 1.2, 1.6, "danach", 1.7, 1.8, "ist", 1.9, 2.1, "gut"), 0, 2.2);
        assertEquals("ähm", w.get(2).text);
        assertEquals('i', w.get(2).q);
        assertTrue(w.get(2).start >= 0.6 - 1e-9 && w.get(2).end <= 1.2 + 1e-9, w.get(2).start + "–" + w.get(2).end);
        for (int i = 1; i < w.size(); i++) assertTrue(w.get(i).start >= w.get(i - 1).start);
    }

    @Test void nothingMeasuredGivesNoTimes() {
        assertTrue(WordAlign.align("Hallo Welt", new ArrayList<>(), 0, 1).isEmpty());
        assertTrue(WordAlign.align("", ref(0, 1, "Hallo"), 0, 1).isEmpty());
    }

    @Test void jsonCarriesTheQualityOnlyWhenNotExact() throws Exception {
        JSONArray a = WordAlign.toJson(List.of(new Speech.Word(1.234, 1.5, "Ja"), new Speech.Word(1.5, 1.9, "45", 's')));
        assertEquals(3, a.getJSONArray(0).length());
        assertEquals(1.23, a.getJSONArray(0).getDouble(0), 1e-9);
        assertEquals("s", a.getJSONArray(1).getString(3));
        assertArrayEquals(new int[]{1, 1, 0}, WordAlign.count(List.of(new Speech.Word(0, 1, "a"), new Speech.Word(1, 2, "b", 's'))));
    }
}
