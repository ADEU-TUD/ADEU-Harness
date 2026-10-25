package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeechWordsTest {
    @Test void joinsSubwordTokens() {
        // So liefert Parakeet "Alles hat ein Ende, nur die Wurst hat zwei."
        String[] tk = {" Al", "les", " hat", " ein", " En", "de", ",", " nur", " die", " W", "ur", "st", " hat", " zwei", "."};
        float[] ts = new float[tk.length], du = new float[tk.length];
        for (int i = 0; i < tk.length; i++) { ts[i] = 0.16f * i; du[i] = 0.16f; }
        List<Speech.Word> w = Speech.words(tk, ts, du);
        assertEquals(9, w.size());
        assertEquals("Alles", w.get(0).text);
        assertEquals("Ende,", w.get(3).text);
        assertEquals("zwei.", w.get(8).text);
        assertEquals(0.0, w.get(0).start, 1e-6);
        assertEquals(0.32, w.get(0).end, 1e-6);
        assertTrue(Replacements.sameText(w, "Alles hat ein Ende, nur die Wurst hat zwei."));
    }

    @Test void handlesMissingArrays() {
        assertTrue(Speech.words(null, null, null).isEmpty());
        assertTrue(Speech.words(new String[]{"a"}, new float[0], null).isEmpty());
    }
}
