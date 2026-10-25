package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {
    static final int SR = 16000;

    private static List<Chunker.Chunk> run(List<Chunker.Turn> turns, long[][] ranges) throws Exception {
        List<Chunker.Chunk> out = new ArrayList<>();
        Chunker c = new Chunker(turns, out::add);
        for (long[] r : ranges) c.addRange(r[0], r[1]);
        c.finish();
        return out;
    }

    @Test void mergesShortPiecesWithoutSpeakers() throws Exception {
        List<Chunker.Chunk> out = run(null, new long[][]{{0, 2 * SR}, {(long) (2.3 * SR), 4 * SR}, {(long) (4.5 * SR), 6 * SR}});
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).start);
        assertEquals(6 * SR, out.get(0).end);
    }

    @Test void splitsAtLongGapsAndMaxLength() throws Exception {
        List<Chunker.Chunk> out = run(null, new long[][]{{0, 2 * SR}, {5 * SR, 7 * SR}});
        assertEquals(2, out.size());
        out = run(null, new long[][]{{0, 10 * SR}, {(long) (10.2 * SR), 20 * SR}});
        assertEquals(2, out.size(), "max. 18 s pro Abschnitt");
    }

    @Test void splitsAtSpeakerChange() throws Exception {
        List<Chunker.Turn> turns = Arrays.asList(new Chunker.Turn(0, 5, 0), new Chunker.Turn(5, 10, 1));
        List<Chunker.Chunk> out = run(turns, new long[][]{{0, 10 * SR}});
        assertEquals(2, out.size());
        assertEquals(0, out.get(0).speaker);
        assertEquals(1, out.get(1).speaker);
        assertEquals(5 * SR, out.get(0).end);
    }

    @Test void cleanTurnsRemapsTinySpeakers() {
        List<Chunker.Turn> in = Arrays.asList(new Chunker.Turn(0, 30, 0), new Chunker.Turn(30, 31, 2), new Chunker.Turn(31, 60, 1));
        List<Chunker.Turn> out = Chunker.cleanTurns(in, 4.0, 0.02);
        for (Chunker.Turn t : out) assertNotEquals(2, t.speaker);
    }

    @Test void speakerAtUsesOverlap() {
        List<Chunker.Turn> turns = Arrays.asList(new Chunker.Turn(0, 5, 0), new Chunker.Turn(5, 10, 1));
        assertEquals(1, Chunker.speakerAt(turns, 4, 9));
        assertEquals(1, Chunker.speakerAt(turns, 12, 13), "naechstgelegener Sprecher");
    }
}
