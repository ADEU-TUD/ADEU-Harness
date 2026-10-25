package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineTest {
    @TempDir Path tmp;

    private static void transcript(File dataDir, String id, String title, String... texts) throws Exception {
        JSONArray segs = new JSONArray();
        for (int i = 0; i < texts.length; i++) segs.put(new JSONObject().put("id", i).put("start", i * 2.0).put("end", i * 2 + 1.5).put("text", texts[i]));
        JSONObject t = new JSONObject().put("id", id).put("title", title).put("created", "2026-10-01T10:00:00").put("duration", 12.5)
                .put("segments", segs).put("audio", JSONObject.NULL).put("folder", JSONObject.NULL).put("diarization", false);
        Util.writeText(new File(dataDir, "transcripts/" + id + "/transcript.json"), t.toString());
    }

    private static boolean waitFor(java.util.function.BooleanSupplier c) throws InterruptedException {
        for (int i = 0; i < 100; i++) { if (c.getAsBoolean()) return true; Thread.sleep(50); }
        return false;
    }

    @Test void fullTextSearchFindsTextBeyondPreview() throws Exception {
        FakePlatform p = new FakePlatform(tmp.toFile());
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 40; i++) longText.append("Füllwort ");
        transcript(p.dataDir(), "aaa111", "Seminar Stadtgeographie", longText.toString(), "Am Hafen wurden alte Speicher umgebaut.");
        transcript(p.dataDir(), "bbb222", "Sprachnachricht", "Bring bitte die Unterlagen mit.");
        Engine e = Engine.createForTest(p);
        JSONArray list = new JSONArray(e.index.list());
        assertEquals(2, list.length());
        JSONArray hits = new JSONArray(e.index.search("speicher HAFEN"));
        assertEquals(1, hits.length());
        assertEquals("aaa111", hits.getJSONObject(0).getString("id"));
        assertTrue(hits.getJSONObject(0).getString("snippet").contains("Speicher"));
        assertEquals(0, new JSONArray(e.index.search("Speicher Unterlagen")).length(), "alle Woerter muessen vorkommen");
        // Index wird gespeichert und bei Aenderungen aktualisiert
        assertTrue(new File(p.dataDir(), "index.json").isFile());
        Thread.sleep(20);
        transcript(p.dataDir(), "bbb222", "Sprachnachricht", "Jetzt geht es um Speicher.");
        new File(p.dataDir(), "transcripts/bbb222/transcript.json").setLastModified(System.currentTimeMillis() + 5000);
        assertEquals(2, new JSONArray(e.index.search("speicher")).length());
    }

    @Test void backupRoundTrip() throws Exception {
        FakePlatform p1 = new FakePlatform(tmp.resolve("a").toFile());
        transcript(p1.dataDir(), "aaa111", "Eins", "Hallo Welt.");
        transcript(p1.dataDir(), "bbb222", "Zwei", "Noch ein Text.");
        Util.writeBytes(new File(p1.dataDir(), "transcripts/aaa111/audio.ogg"), new byte[]{1, 2, 3});
        JSONObject t = new JSONObject(Util.readText(new File(p1.dataDir(), "transcripts/aaa111/transcript.json")));
        Util.writeText(new File(p1.dataDir(), "transcripts/aaa111/transcript.json"), t.put("audio", "audio.ogg").toString());
        Engine e1 = Engine.createForTest(p1);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        assertEquals(2, Backup.export(e1, zip, true));

        FakePlatform p2 = new FakePlatform(tmp.resolve("b").toFile());
        transcript(p2.dataDir(), "bbb222", "Anders", "Anderer Inhalt.");
        Engine e2 = Engine.createForTest(p2);
        String msg = Backup.restore(e2, new ByteArrayInputStream(zip.toByteArray()));
        assertTrue(msg.startsWith("2 Transkripte übernommen"), msg);
        assertEquals(3, new JSONArray(e2.index.list()).length());
        assertTrue(new File(p2.dataDir(), "transcripts/aaa111/audio.ogg").isFile());
        // Nochmal einspielen: nichts doppelt
        String again = Backup.restore(e2, new ByteArrayInputStream(zip.toByteArray()));
        assertTrue(again.contains("schon vorhanden"), again);
        assertEquals(3, new JSONArray(e2.index.list()).length());
    }

    @Test void restoreRejectsForeignZip() throws Exception {
        FakePlatform p = new FakePlatform(tmp.toFile());
        Engine e = Engine.createForTest(p);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(bo)) {
            z.putNextEntry(new java.util.zip.ZipEntry("../../evil.txt")); z.write(1); z.closeEntry();
            z.putNextEntry(new java.util.zip.ZipEntry("transcripts/../../x/transcript.json")); z.write(1); z.closeEntry();
        }
        assertThrows(Engine.UserError.class, () -> Backup.restore(e, new ByteArrayInputStream(bo.toByteArray())));
        assertFalse(new File(tmp.toFile(), "evil.txt").exists());
    }

    @Test void queueSurvivesRestartAndRecordingsAreRecovered() throws Exception {
        FakePlatform p = new FakePlatform(tmp.toFile());
        File data = p.dataDir();
        // Auftrag, der beim "Absturz" lief, und eine liegengebliebene Aufnahme ohne gueltigen Kopf
        File src = new File(data, "incoming/abc123/Vortrag.mp3");
        Util.writeBytes(src, new byte[100]);
        File orphan = new File(data, "incoming/dead00/alt.mp3");
        Util.writeBytes(orphan, new byte[10]);
        JSONObject job = new JSONObject().put("id", "j1").put("name", "Vortrag.mp3").put("status", "running").put("src", src.getPath())
                .put("delete_src", true).put("opts", new JSONObject().put("model", "base"));
        Util.writeText(new File(data, "jobs.json"), new JSONObject().put("jobs", new JSONArray().put(job)).put("pending", new JSONArray()).toString());
        File rec = new File(data, "recordings/rec-2026-10-01_10-00-00.wav");
        rec.getParentFile().mkdirs();
        try (RandomAccessFile raf = new RandomAccessFile(rec, "rw")) { raf.write(Wav.header(0)); raf.write(new byte[16000 * 2 * 2]); }
        Util.writeText(new File(data, "recordings/rec-2026-10-01_10-00-00.marks.json"), "[{\"t\":1.0,\"label\":\"Wichtig\"}]");

        Engine e = Engine.createForTest(p);
        assertFalse(orphan.getParentFile().exists(), "verwaiste Importe werden geloescht");
        // Der Auftrag laeuft wieder an (und scheitert hier, weil das Modell fehlt)
        assertTrue(waitFor(() -> "error".equals(e.jobs.get("j1").status)));
        assertTrue(e.jobs.get("j1").error.contains("Modell"));
        assertTrue(src.isFile(), "Quelldatei bleibt bei Fehler erhalten (erneut versuchbar)");
        // Die Aufnahme wird angeboten, mit Markierungen
        JSONArray pending = new JSONArray(e.takePendingIncoming());
        assertEquals(1, pending.length());
        JSONObject it = pending.getJSONObject(0);
        assertTrue(it.getBoolean("recovered"));
        assertEquals(2.0, it.getDouble("duration"), 0.01);
        assertEquals("Wichtig", it.getJSONArray("marks").getJSONObject(0).getString("label"));
        assertFalse(rec.exists());
        // Wegklicken bleibt nach einem Neustart weg
        e.dismissJob("j1");
        Engine e2 = Engine.createForTest(p);
        assertNull(e2.jobs.get("j1"));
        assertEquals(1, new JSONArray(e2.takePendingIncoming()).length(), "Angebot bleibt bis zur Entscheidung erhalten");
    }
}
