package de.lokal.whisper;

import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Zentrale Logik: Modelle, Aufnahme, Warteschlange und Transkription. Lebt so lange wie der Prozess.
 * Plattformunabhaengig; Android und Windows liefern nur die {@link Platform}.
 */
public final class Engine {
    public static final class Job {
        public String id, name, srcPath, status = "queued", phase = "queued", stage = "Wartet", error, notice, transcriptId, language;
        public long size, created = System.currentTimeMillis(), started, finished;
        public double progress, duration;
        Double eta;
        JSONObject opts;
        JSONArray marks;
        boolean deleteSrc;
        volatile boolean cancel;
        final List<JSONObject> segments = new ArrayList<>();
        long lastEmit;
        transient LiveSession live;

        public boolean active() { return "queued".equals(status) || "running".equals(status); }

        JSONObject toJson(boolean full) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id); o.put("name", name); o.put("status", status); o.put("phase", phase); o.put("stage", stage);
                o.put("progress", progress); o.put("eta", eta == null ? JSONObject.NULL : eta);
                o.put("created", created); o.put("started", started); o.put("finished", finished);
                o.put("error", error == null ? JSONObject.NULL : error);
                o.put("notice", notice == null ? JSONObject.NULL : notice);
                o.put("transcript_id", transcriptId == null ? JSONObject.NULL : transcriptId);
                o.put("duration", duration); o.put("size", size);
                o.put("language", language == null ? JSONObject.NULL : language);
                o.put("opts", opts);
                synchronized (segments) {
                    o.put("segment_count", segments.size());
                    if (full) { JSONArray a = new JSONArray(); for (JSONObject s : segments) a.put(s); o.put("segments", a); }
                }
            } catch (Exception ignored) {}
            return o;
        }

        /** Was nach einem Neustart gebraucht wird (ohne Zwischenergebnisse). */
        JSONObject persist() {
            JSONObject o = toJson(false);
            try {
                o.put("src", srcPath); o.put("delete_src", deleteSrc);
                if (marks != null) o.put("marks", marks);
            } catch (Exception ignored) {}
            return o;
        }

        static Job restore(JSONObject o) {
            Job j = new Job();
            j.id = o.optString("id"); j.name = o.optString("name", "Audio"); j.srcPath = o.optString("src", null);
            j.status = o.optString("status", "queued"); j.phase = o.optString("phase", j.status); j.stage = o.optString("stage", "");
            j.error = o.isNull("error") ? null : o.optString("error", null);
            j.notice = o.isNull("notice") ? null : o.optString("notice", null);
            j.transcriptId = o.isNull("transcript_id") ? null : o.optString("transcript_id", null);
            j.language = o.isNull("language") ? null : o.optString("language", null);
            j.size = o.optLong("size"); j.created = o.optLong("created", System.currentTimeMillis());
            j.started = o.optLong("started"); j.finished = o.optLong("finished");
            j.progress = o.optDouble("progress", 0); j.duration = o.optDouble("duration", 0);
            j.opts = o.optJSONObject("opts"); if (j.opts == null) j.opts = new JSONObject();
            j.marks = o.optJSONArray("marks");
            j.deleteSrc = o.optBoolean("delete_src", false);
            return j;
        }
    }

    /** Fehler mit verstaendlicher Meldung fuer die Oberflaeche. */
    public static final class UserError extends Exception { public UserError(String m) { super(m); } }

    /** Modell-Id fuer die Erkennung auf dem Server (Whisper des Uni-Servers) statt auf dem Geraet. */
    public static final String CLOUD = "cloud";

    /**
     * Erkennung auf einem Server (ADEU: Whisper des Uni-Servers). Bekommt 16-kHz-Samples eines Sprachabschnitts und
     * liefert den Text. Sprachabschnitte, Sprechererkennung und Speichern laufen weiter auf dem Geraet.
     */
    public interface Remote {
        String recognize(float[] samples, String language) throws Exception;
        /** Anzeigename, z. B. "Uni-Server (Whisper Large v3 Turbo)". */
        String name();
    }

    /** Wird von der App gesetzt; ohne Remote ist nur die Erkennung auf dem Geraet moeglich. */
    public static volatile Remote remote;

    private static Engine instance;

    public final Platform platform;
    public final File dataDir, models, transcripts, recordings, incomingDir, cacheDir;
    private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    final Map<String, Job> jobs = new LinkedHashMap<>();
    final Map<String, Double> downloads = new HashMap<>();
    private final Map<String, Boolean> cancelDownload = new HashMap<>();
    final List<JSONObject> pendingIncoming = new ArrayList<>();
    final SearchIndex index;
    volatile Job current;
    volatile Recording recorder;
    volatile boolean calibrating;
    private volatile boolean uiReady;

    public static synchronized Engine start(Platform p) {
        if (instance == null) instance = new Engine(p);
        return instance;
    }

    public static synchronized Engine get() { return instance; }

    /** Eigene Instanz fuer Tests (ohne Singleton). */
    static Engine createForTest(Platform p) { return new Engine(p); }

    private Engine(Platform p) {
        platform = p;
        dataDir = p.dataDir();
        models = p.modelsDir();
        cacheDir = p.cacheDir();
        transcripts = new File(dataDir, "transcripts");
        recordings = new File(dataDir, "recordings");
        incomingDir = new File(dataDir, "incoming");
        models.mkdirs(); transcripts.mkdirs(); recordings.mkdirs(); incomingDir.mkdirs(); cacheDir.mkdirs();
        index = new SearchIndex(transcripts, new File(dataDir, "index.json"));
        // Reste frueherer Laeufe aufraeumen
        Util.deleteRecursive(new File(cacheDir, "work"));
        Util.deleteRecursive(new File(cacheDir, "incoming"));
        cleanShareDir(0);
        restoreJobs();
        recoverRecordings();
        cleanIncoming();
        Thread t = new Thread(this::workerLoop, "whisper-worker");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ Ereignisse
    void emit(JSONObject ev) { platform.emit(ev.toString()); }

    void emitSimple(String type, Object... kv) {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type);
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1] == null ? JSONObject.NULL : kv[i + 1]);
        } catch (Exception ignored) {}
        emit(o);
    }

    public void toast(String kind, String msg) { emitSimple("toast", "kind", kind, "message", msg); }

    private void emitJob(Job j, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - j.lastEmit < 250) return;
        j.lastEmit = now;
        JSONObject o = new JSONObject();
        try { o.put("type", "job"); o.put("job", j.toJson(false)); } catch (Exception ignored) {}
        emit(o);
        platform.onActivity(this);
    }

    /** Die Oberflaeche ist geladen: liegengebliebene Dateien anbieten. */
    public void uiReady() {
        uiReady = true;
        synchronized (this) { if (!pendingIncoming.isEmpty()) emitSimple("incoming"); }
    }

    // ------------------------------------------------------------------ Zustand
    JSONObject nativeState() {
        JSONObject o = null;
        try { String s = Util.readText(new File(dataDir, "native.json")); if (s != null) o = new JSONObject(s); } catch (Exception ignored) {}
        return o == null ? new JSONObject() : o;
    }

    void saveNativeState(JSONObject o) { Util.writeText(new File(dataDir, "native.json"), o.toString()); }

    File modelDir(Models.ModelInfo m) { return new File(models, m.id); }

    boolean installed(Models.ModelInfo m) { return new File(modelDir(m), ".complete").isFile(); }

    public synchronized String state() {
        JSONObject o = new JSONObject();
        try {
            JSONObject speed = nativeState().optJSONObject("speed");
            JSONArray ms = new JSONArray();
            for (Models.ModelInfo m : Models.ALL) {
                JSONObject j = new JSONObject();
                j.put("id", m.id); j.put("name", m.name); j.put("desc", m.desc); j.put("size", m.size());
                j.put("kind", m.kind); j.put("can_translate", m.canTranslate); j.put("word_times", m.wordTimes);
                j.put("installed", installed(m));
                Double d = downloads.get(m.id);
                j.put("downloading", d == null ? JSONObject.NULL : d);
                if (speed != null && speed.has(m.id)) j.put("speed", speed.optDouble(m.id));
                JSONArray urls = new JSONArray();
                for (Models.FileSpec f : m.files) urls.put(new JSONObject().put("name", f.name).put("url", m.url(f)).put("size", f.size));
                j.put("files", urls);
                ms.put(j);
            }
            o.put("models", ms);
            JSONArray js = new JSONArray();
            for (Job j : jobs.values()) js.put(j.toJson(false));
            o.put("jobs", js);
            JSONObject rec = new JSONObject();
            Recording r = recorder;
            rec.put("active", r != null);
            rec.put("paused", r != null && r.isPaused());
            rec.put("seconds", r == null ? 0 : r.seconds());
            if (r != null) {
                rec.put("marks", r.marks());
                LiveSession l = r.live;
                if (l != null) {
                    rec.put("live", new JSONObject().put("model", l.model.id).put("model_name", l.model.name).put("language", l.language)
                            .put("segments", l.segmentsJson()).put("progress", l.progress()));
                }
            }
            o.put("recording", rec);
            JSONObject ns = nativeState();
            o.put("calibrated", ns.has("cluster_threshold"));
            o.put("calibrating", calibrating);
            o.put("speaker_model", speakerModelReady());
            o.put("threads", platform.threads());
            o.put("storage", Util.size(transcripts));
            o.put("models_size", Util.size(models));
            o.put("models_dir", models.getAbsolutePath());
        } catch (Exception e) { Util.err("state", e); }
        return o.toString();
    }

    void emitState() {
        JSONObject o = new JSONObject();
        try { o.put("type", "state"); o.put("state", new JSONObject(state())); } catch (Exception ignored) {}
        emit(o);
        platform.onActivity(this);
    }

    // ------------------------------------------------------------------ Eingebaute Modelle (VAD, Sprecher)
    File builtin(String name) {
        File dir = new File(models, "builtin");
        dir.mkdirs();
        File f = new File(dir, name);
        if (f.isFile() && f.length() > 0) return f;
        File part = new File(dir, name + ".part");
        try (InputStream in = platform.openBuiltin(name); OutputStream out = new FileOutputStream(part)) {
            Util.copy(in, out);
        } catch (Exception e) {
            Util.err("builtin " + name, e);
            return f;
        }
        part.renameTo(f);
        return f;
    }

    /** Eingebaute Datei (VAD, Segmentierung, Kalibrierung) als Datei im Modellordner. */
    public File builtinFile(String name) { return builtin(name); }

    public synchronized List<Job> jobList() { return new ArrayList<>(jobs.values()); }

    File speakerModel() { return new File(new File(models, "builtin"), Models.SPEAKER.name); }

    boolean speakerModelReady() { File f = speakerModel(); return f.isFile() && f.length() == Models.SPEAKER.size; }

    private final Object spkLock = new Object();

    /** Laedt das Sprechermodell, falls noetig. Wirft eine Ausnahme, wenn es nicht klappt. */
    void ensureSpeakerModel(final Platform.Cancel cancel) throws Exception {
        synchronized (spkLock) {
            if (speakerModelReady()) return;
            File f = speakerModel();
            f.getParentFile().mkdirs();
            File part = new File(f.getParentFile(), Models.SPEAKER.name + ".part");
            Exception last = null;
            for (String url : Models.SPEAKER_URLS) {
                try {
                    httpDownload(url, part, got -> { if (cancel != null && cancel.cancelled()) throw new InterruptedException("abgebrochen"); });
                    verify(part, Models.SPEAKER);
                    if (!part.renameTo(f)) throw new Exception("Datei konnte nicht gespeichert werden");
                    return;
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    last = e;
                    Util.err("speaker model " + url, e);
                    part.delete();
                }
            }
            throw last == null ? new Exception("Download fehlgeschlagen") : last;
        }
    }

    public interface Bytes { void on(long got) throws InterruptedException; }

    /** HTTP-Download mit Weiterleitungen und Fortsetzen (Range). */
    void httpDownload(String url, File part, Bytes progress) throws Exception {
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection con = null;
        for (int redirect = 0; redirect < 10; redirect++) {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(20000);
            con.setReadTimeout(60000);
            con.setRequestProperty("User-Agent", "Whisper/" + platform.version() + " (" + platform.deviceKind() + ")");
            if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
            int code = con.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = con.getHeaderField("Location");
                con.disconnect();
                if (loc == null) throw new Exception("Weiterleitung ohne Ziel");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code == 416) { have = 0; part.delete(); con.disconnect(); continue; }
            if (code != 200 && code != 206) { con.disconnect(); throw new Exception("HTTP " + code); }
            if (code == 200) have = 0;
            break;
        }
        try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(part, have > 0)) {
            byte[] buf = new byte[1 << 16];
            int n;
            long got = have;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                if (progress != null) progress.on(got);
            }
        } finally {
            con.disconnect();
        }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(s.getBytes(Util.UTF8))) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static void verify(File f, Models.FileSpec spec) throws Exception {
        if (f.length() != spec.size) throw new Exception("Datei unvollständig (" + f.length() + " statt " + spec.size + " Bytes)");
        if (!spec.sha256.equals(sha256(f))) throw new Exception("Prüfsumme stimmt nicht – die Datei ist beschädigt");
    }

    // ------------------------------------------------------------------ Downloads
    public void downloadModel(final String id) {
        final Models.ModelInfo m = Models.get(id);
        if (m == null || installed(m)) return;
        synchronized (this) {
            if (downloads.containsKey(id)) return;
            downloads.put(id, 0.0);
            cancelDownload.put(id, false);
        }
        emitState();
        new Thread(() -> doDownload(m), "download-" + id).start();
    }

    public void cancelDownload(String id) { synchronized (this) { if (cancelDownload.containsKey(id)) cancelDownload.put(id, true); } }

    private void doDownload(final Models.ModelInfo m) {
        File dir = modelDir(m);
        dir.mkdirs();
        final long total = m.size();
        final long[] doneBefore = {0};
        final long[] lastEmit = {0};
        try {
            for (Models.FileSpec spec : m.files) {
                File dst = new File(dir, spec.name);
                if (dst.isFile() && dst.length() == spec.size) { doneBefore[0] += spec.size; continue; }
                File part = new File(dir, spec.name + ".part");
                httpDownload(m.url(spec), part, got -> {
                    Boolean c;
                    synchronized (Engine.this) { c = cancelDownload.get(m.id); }
                    if (c != null && c) throw new InterruptedException("abgebrochen");
                    long now = System.currentTimeMillis();
                    if (now - lastEmit[0] > 400) {
                        lastEmit[0] = now;
                        double f = Math.min(0.995, (doneBefore[0] + got) / (double) total);
                        synchronized (Engine.this) { downloads.put(m.id, f); }
                        emitSimple("download", "id", m.id, "progress", f, "done", doneBefore[0] + got, "total", total);
                    }
                });
                emitSimple("download", "id", m.id, "progress", Math.min(0.995, (doneBefore[0] + part.length()) / (double) total), "verifying", true);
                try {
                    verify(part, spec);
                } catch (Exception e) {
                    part.delete();
                    throw e;
                }
                if (dst.exists()) dst.delete();
                if (!part.renameTo(dst)) throw new Exception("Datei konnte nicht gespeichert werden");
                doneBefore[0] += spec.size;
            }
            Util.writeText(new File(dir, ".complete"), m.repo + "@" + m.commit);
            try { ensureSpeakerModel(null); } catch (Exception e) { Util.err("speaker model", e); }
            toast("ok", "Modell „" + m.name + "“ ist bereit.");
        } catch (InterruptedException e) {
            toast("", "Download abgebrochen.");
        } catch (Exception e) {
            Util.err("download", e);
            toast("error", "Download fehlgeschlagen: " + e.getMessage() + ". Beim nächsten Versuch wird fortgesetzt. "
                    + "Wird die Seite im Firmennetz blockiert, kannst du die Dateien auch im Browser laden und unter Einstellungen einspielen.");
        } finally {
            synchronized (this) { downloads.remove(m.id); cancelDownload.remove(m.id); }
            emitState();
        }
    }

    public void deleteModel(String id) {
        Models.ModelInfo m = Models.get(id);
        if (m == null) return;
        if (Speech.inUse(id) || (current != null && current.opts.optString("model", "").equals(id))) { toast("error", "Das Modell wird gerade benutzt."); return; }
        Speech.evictIdle(id);
        Util.deleteRecursive(modelDir(m));
        emitState();
    }

    /**
     * Modelldatei von Hand einspielen (z. B. im Browser geladen oder per USB-Stick), auch als ZIP.
     * Liefert eine Meldung fuer die Oberflaeche.
     */
    public String importModelFile(String name, InputStream in) throws Exception {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            int n = 0;
            try (ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String base = e.getName().replace('\\', '/');
                    base = base.substring(base.lastIndexOf('/') + 1);
                    if (target(base) != null) { importModelFile(base, new NonClosing(zin)); n++; }
                }
            }
            if (n == 0) throw new UserError("Die ZIP-Datei enthält keine passenden Modelldateien.");
            return n + " Dateien eingespielt.";
        }
        Object[] t = target(name);
        if (t == null) throw new UserError("„" + name + "“ gehört zu keinem bekannten Modell.");
        Models.FileSpec spec = (Models.FileSpec) t[1];
        File dst = (File) t[2];
        dst.getParentFile().mkdirs();
        File part = new File(dst.getPath() + ".import");
        try (OutputStream out = new FileOutputStream(part)) { Util.copy(in, out); }
        try { verify(part, spec); } catch (Exception e) { part.delete(); throw new UserError("„" + name + "“: " + e.getMessage()); }
        if (dst.exists()) dst.delete();
        if (!part.renameTo(dst)) throw new UserError("Datei konnte nicht gespeichert werden");
        Models.ModelInfo m = (Models.ModelInfo) t[0];
        String msg = "„" + name + "“ eingespielt.";
        if (m != null) {
            boolean all = true;
            for (Models.FileSpec f : m.files) { File x = new File(modelDir(m), f.name); if (!x.isFile() || x.length() != f.size) all = false; }
            if (all) { Util.writeText(new File(modelDir(m), ".complete"), m.repo + "@" + m.commit); msg = "Modell „" + m.name + "“ ist bereit."; }
            else msg += " Es fehlen noch weitere Dateien von „" + m.name + "“.";
        }
        emitState();
        return msg;
    }

    /** [Modell oder null, FileSpec, Zieldatei] fuer einen Dateinamen. */
    private Object[] target(String name) {
        for (Models.ModelInfo m : Models.ALL) for (Models.FileSpec f : m.files) if (f.name.equals(name)) return new Object[]{m, f, new File(modelDir(m), f.name)};
        if (name.equals(Models.SPEAKER.name) || name.equals("wespeaker_en_voxceleb_resnet34_LM.onnx")) return new Object[]{null, Models.SPEAKER, speakerModel()};
        return null;
    }

    private static final class NonClosing extends java.io.FilterInputStream {
        NonClosing(InputStream in) { super(in); }
        @Override public void close() {}
    }

    // ------------------------------------------------------------------ Eingehende Dateien (Teilen / Auswahl / Ziehen)
    /** Uebernimmt eine Datei (Inhalt wird kopiert) und bietet sie zum Transkribieren an. */
    public JSONObject importStream(String name, long size, InputStream in) throws Exception {
        if (name == null || name.trim().isEmpty()) name = "Audio";
        String token = Util.hex(6);
        File dir = new File(incomingDir, token);
        dir.mkdirs();
        File dst = new File(dir, Util.safeName(name, "audio"));
        try (OutputStream out = new FileOutputStream(dst)) {
            long n = Util.copy(in, out);
            if (n <= 0) throw new UserError("Die Datei ist leer.");
            size = n;
        } catch (Exception e) {
            Util.deleteRecursive(dir);
            throw e;
        }
        JSONObject o = new JSONObject();
        o.put("token", token); o.put("name", name); o.put("size", size);
        addIncoming(o);
        return o;
    }

    void addIncoming(JSONObject item) {
        synchronized (this) { pendingIncoming.add(item); saveJobs(); }
        if (uiReady) emitSimple("incoming");
    }

    public synchronized String takePendingIncoming() {
        JSONArray a = new JSONArray();
        for (JSONObject o : pendingIncoming) a.put(o);
        return a.toString();
    }

    public void discardIncoming(String token) {
        synchronized (this) {
            pendingIncoming.removeIf(o -> token.equals(o.optString("token")));
            saveJobs();
        }
        if (token.matches("[0-9a-f]+")) Util.deleteRecursive(new File(incomingDir, token));
    }

    // ------------------------------------------------------------------ Auftraege
    Job enqueueFile(String path, String name, long size, JSONObject opts, boolean deleteSrc) {
        Job j = new Job();
        j.id = Util.hex(5);
        j.name = name;
        j.srcPath = path;
        j.size = size;
        j.opts = opts;
        j.deleteSrc = deleteSrc;
        synchronized (this) { jobs.put(j.id, j); saveJobs(); }
        queue.add(j);
        emitJob(j, true);
        return j;
    }

    public void enqueueIncoming(String token, JSONObject opts) throws UserError {
        JSONObject item = null;
        synchronized (this) {
            for (JSONObject o : pendingIncoming) if (token.equals(o.optString("token"))) item = o;
            if (item != null) pendingIncoming.remove(item);
        }
        File dir = new File(incomingDir, token);
        File[] fs = dir.listFiles((d, n) -> !n.endsWith(".marks.json"));
        if (fs == null || fs.length == 0) { saveJobs(); throw new UserError("Die Datei ist nicht mehr vorhanden."); }
        File f = fs[0];
        Job j = new Job();
        j.id = Util.hex(5); j.name = f.getName(); j.srcPath = f.getAbsolutePath(); j.size = f.length(); j.opts = opts; j.deleteSrc = true;
        if (item != null && item.optJSONArray("marks") != null) j.marks = item.optJSONArray("marks");
        synchronized (this) { jobs.put(j.id, j); saveJobs(); }
        queue.add(j);
        emitJob(j, true);
    }

    /** Vorhandenes Transkript mit anderen Einstellungen erneut verarbeiten (z. B. Uebersetzen). */
    public String retranscribe(String tid, JSONObject opts) throws Exception {
        File dir = new File(transcripts, tid);
        JSONObject t = new JSONObject(Util.readText(new File(dir, "transcript.json")));
        String audio = t.optString("audio", "");
        File src = new File(dir, audio);
        if (audio.isEmpty() || !src.isFile()) throw new UserError("Für dieses Transkript ist kein Audio gespeichert.");
        File tmp = new File(incomingDir, Util.hex(6));
        tmp.mkdirs();
        File copy = new File(tmp, Util.safeName(t.optString("title", "Audio"), "Audio") + "." + Util.ext(audio));
        Util.copyFile(src, copy);
        Job j = enqueueFile(copy.getAbsolutePath(), copy.getName(), copy.length(), opts, true);
        if (t.optJSONArray("marks") != null) j.marks = t.optJSONArray("marks");
        return j.id;
    }

    public void cancelJob(String id) {
        Job j;
        synchronized (this) { j = jobs.get(id); }
        if (j == null) return;
        j.cancel = true;
        if ("queued".equals(j.status)) {
            queue.remove(j);
            j.status = "cancelled"; j.phase = "cancelled"; j.stage = "Abgebrochen"; j.finished = System.currentTimeMillis();
            if (j.live != null) j.live.abort();
            cleanupSrc(j);
            synchronized (this) { saveJobs(); }
            emitJob(j, true);
        } else if ("running".equals(j.status)) {
            j.stage = "Wird abgebrochen …";
            emitJob(j, true);
        }
    }

    /** Fertigen, abgebrochenen oder fehlgeschlagenen Auftrag aus der Liste entfernen (dauerhaft). */
    public void dismissJob(String id) {
        synchronized (this) {
            Job j = jobs.get(id);
            if (j == null || j.active()) return;
            jobs.remove(id);
            saveJobs();
        }
        emitState();
    }

    public void clearFinished() {
        synchronized (this) {
            jobs.values().removeIf(j -> !j.active());
            saveJobs();
        }
        emitState();
    }

    public String jobJson(String id) {
        Job j;
        synchronized (this) { j = jobs.get(id); }
        return j == null ? null : j.toJson(true).toString();
    }

    public boolean busy() {
        if (recorder != null || calibrating) return true;
        synchronized (this) {
            if (!downloads.isEmpty()) return true;
            for (Job j : jobs.values()) if (j.active()) return true;
        }
        return false;
    }

    public boolean hasActiveJobs() {
        synchronized (this) { for (Job j : jobs.values()) if (j.active()) return true; }
        return false;
    }

    public Job currentJob() { return current; }

    /** Transcript list (JSON array of summaries) for the app. */
    public String indexList() { return index.list(); }
    /** Full-text search: JSON array [{id, hits, snippet}]. */
    public String indexSearch(String q) { return index.search(q); }
    /** A transcript was changed or deleted by the app. */
    public void indexInvalidate(String id) { index.invalidate(id); }
    public Recording recorder() { return recorder; }
    public synchronized Map<String, Double> downloadsSnapshot() { return new HashMap<>(downloads); }

    // ------------------------------------------------------------------ Warteschlange speichern / wiederherstellen
    private File jobsFile() { return new File(dataDir, "jobs.json"); }

    /** Aufrufen mit Lock auf this. */
    void saveJobs() {
        try {
            JSONObject o = new JSONObject();
            JSONArray a = new JSONArray();
            long now = System.currentTimeMillis();
            for (Job j : jobs.values()) {
                if ("done".equals(j.status) && now - j.finished > 24L * 3600 * 1000) continue;
                a.put(j.persist());
            }
            o.put("jobs", a);
            JSONArray p = new JSONArray();
            for (JSONObject x : pendingIncoming) p.put(x);
            o.put("pending", p);
            Util.writeText(jobsFile(), o.toString());
        } catch (Exception e) { Util.err("saveJobs", e); }
    }

    private void restoreJobs() {
        String s = Util.readText(jobsFile());
        if (s == null) return;
        try {
            JSONObject o = new JSONObject(s);
            JSONArray a = o.optJSONArray("jobs");
            int resumed = 0;
            long now = System.currentTimeMillis();
            if (a != null) for (int i = 0; i < a.length(); i++) {
                Job j = Job.restore(a.getJSONObject(i));
                if (j.id.isEmpty()) continue;
                if ("done".equals(j.status) && now - j.finished > 24L * 3600 * 1000) continue;
                if (j.active()) {
                    if (j.srcPath == null || !new File(j.srcPath).isFile()) {
                        j.status = "error"; j.phase = "error"; j.stage = "Fehler"; j.finished = now;
                        j.error = "Die Datei war nach dem Neustart der App nicht mehr vorhanden.";
                    } else {
                        j.status = "queued"; j.phase = "queued"; j.stage = "Wartet"; j.progress = 0; j.started = 0;
                        j.notice = "Nach einem Neustart der App wird von vorne begonnen.";
                        resumed++;
                    }
                }
                jobs.put(j.id, j);
                if (j.active()) queue.add(j);
            }
            JSONArray p = o.optJSONArray("pending");
            if (p != null) for (int i = 0; i < p.length(); i++) {
                JSONObject it = p.optJSONObject(i);
                if (it != null && new File(incomingDir, it.optString("token", "-")).isDirectory()) pendingIncoming.add(it);
            }
            if (resumed > 0) Util.log(resumed + " Aufträge nach Neustart fortgesetzt");
        } catch (Exception e) { Util.err("restoreJobs", e); }
    }

    /** Importierte Dateien, zu denen es keinen Auftrag mehr gibt, loeschen. */
    private void cleanIncoming() {
        Set<String> keep = new HashSet<>();
        for (JSONObject o : pendingIncoming) keep.add(o.optString("token"));
        for (Job j : jobs.values()) if (j.srcPath != null) {
            File f = new File(j.srcPath);
            if (f.getParentFile() != null) keep.add(f.getParentFile().getName());
        }
        File[] dirs = incomingDir.listFiles();
        if (dirs != null) for (File d : dirs) if (!keep.contains(d.getName())) Util.deleteRecursive(d);
    }

    /** Aufnahmen, die ein Absturz zurueckgelassen hat: reparieren und zum Transkribieren anbieten. */
    private void recoverRecordings() {
        Set<String> used = new HashSet<>();
        for (Job j : jobs.values()) if (j.srcPath != null) used.add(new File(j.srcPath).getName());
        File[] fs = recordings.listFiles((d, n) -> n.endsWith(".wav"));
        if (fs == null) return;
        for (File f : fs) {
            if (used.contains(f.getName())) continue;
            File marks = Recording.marksFileFor(f);
            long samples = Wav.repair(f);
            if (samples < Wav.RATE) { f.delete(); marks.delete(); continue; }
            try { offerRecording(f, marks, true); } catch (Exception e) { Util.err("recover", e); }
        }
    }

    /** Verschiebt eine Aufnahme in die eingehenden Dateien (Start-Dialog in der Oberflaeche). */
    private void offerRecording(File wav, File marks, boolean recovered) throws Exception {
        String token = Util.hex(6);
        File dir = new File(incomingDir, token);
        dir.mkdirs();
        String title = "Aufnahme " + new SimpleDateFormat("dd.MM.yyyy HH-mm", Locale.GERMANY).format(new Date(wav.lastModified()))
                + (recovered ? " (wiederhergestellt)" : "");
        File dst = new File(dir, title + ".wav");
        if (!wav.renameTo(dst)) { Util.copyFile(wav, dst); wav.delete(); }
        JSONObject o = new JSONObject();
        o.put("token", token); o.put("name", dst.getName()); o.put("size", dst.length()); o.put("recovered", recovered);
        o.put("duration", (dst.length() - Wav.HEADER) / 2 / (double) Wav.RATE);
        String m = Util.readText(marks);
        if (m != null) { try { o.put("marks", new JSONArray(m)); } catch (Exception ignored) {} }
        marks.delete();
        synchronized (this) { pendingIncoming.add(o); saveJobs(); }
        if (uiReady) emitSimple("incoming");
    }

    // ------------------------------------------------------------------ Verarbeitung
    private void workerLoop() {
        while (true) {
            Job j;
            try { j = queue.take(); } catch (InterruptedException e) { continue; }
            if (j.cancel || !"queued".equals(j.status)) continue;
            current = j;
            j.status = "running";
            j.started = System.currentTimeMillis();
            synchronized (this) { saveJobs(); }
            platform.onActivity(this);
            try {
                process(j);
                j.status = "done"; j.phase = "done"; j.stage = "Fertig"; j.progress = 1.0;
            } catch (InterruptedException e) {
                j.status = "cancelled"; j.phase = "cancelled"; j.stage = "Abgebrochen";
            } catch (UserError e) {
                j.status = "error"; j.phase = "error"; j.stage = "Fehler"; j.error = e.getMessage();
            } catch (Throwable e) {
                Util.err("job", e);
                j.status = "error"; j.phase = "error"; j.stage = "Fehler";
                j.error = e instanceof OutOfMemoryError ? "Nicht genug Arbeitsspeicher. Versuche ein kleineres Modell oder eine kürzere Datei."
                        : "Unerwarteter Fehler: " + e;
            } finally {
                j.finished = System.currentTimeMillis();
                j.eta = null;
                if (j.live != null) { j.live.abort(); j.live = null; }
                current = null;
                if (!"error".equals(j.status)) cleanupSrc(j);
                synchronized (this) { saveJobs(); }
                emitJob(j, true);
                if ("done".equals(j.status)) emitSimple("transcript_saved", "id", j.transcriptId, "job_id", j.id, "name", j.name);
                platform.onJobFinished(this, j);
                platform.onActivity(this);
            }
        }
    }

    private void cleanupSrc(Job j) {
        if (!j.deleteSrc || j.srcPath == null) return;
        File f = new File(j.srcPath);
        if (f.getParentFile() != null && f.getParentFile().getParentFile() != null
                && f.getParentFile().getParentFile().equals(incomingDir)) {
            Util.deleteRecursive(f.getParentFile());
        } else if (f.exists()) {
            f.delete();
            Recording.marksFileFor(f).delete();
        }
    }

    private void stage(Job j, String phase, String stage, double progress) {
        j.phase = phase; j.stage = stage; j.progress = progress; j.eta = null;
        emitJob(j, true);
    }

    private static void check(Job j) throws InterruptedException { if (j.cancel) throw new InterruptedException(); }

    /** Eigene Aufnahme (16 kHz mono PCM16 WAV)? Dann muss nichts dekodiert werden. */
    static boolean isOwnWav(File f) {
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".wav") || f.length() < Wav.HEADER) return false;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[Wav.HEADER];
            raf.readFully(h);
            return "RIFF".equals(new String(h, 0, 4, "US-ASCII")) && "data".equals(new String(h, 36, 4, "US-ASCII"))
                    && Arrays.equals(Arrays.copyOfRange(h, 12, 36), Arrays.copyOfRange(Wav.header(0), 12, 36));
        } catch (Exception e) { return false; }
    }

    /** Audio nach 16 kHz PCM; eigene Aufnahmen werden nur kopiert. */
    long toPcm(File src, File pcm, Platform.Progress progress, Platform.Cancel cancel) throws Exception {
        if (isOwnWav(src)) {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(pcm)) {
                long skipped = 0;
                while (skipped < Wav.HEADER) { long k = in.skip(Wav.HEADER - skipped); if (k <= 0) throw new Exception("kurz"); skipped += k; }
                Util.copy(in, out);
            }
            return pcm.length() / 2;
        }
        return platform.decode(src, pcm, progress, cancel);
    }

    private void process(final Job j) throws Exception {
        final JSONObject opts = j.opts;
        final boolean cloud = CLOUD.equals(opts.optString("model", "turbo"));
        final Remote rem = remote;
        if (cloud && rem == null) throw new UserError("Die Transkription über den Uni-Server ist nicht eingerichtet (API-Key fehlt).");
        final Models.ModelInfo m = cloud ? CLOUD_INFO : Models.get(opts.optString("model", "turbo"));
        if (m == null || (!cloud && !installed(m))) throw new UserError("Das Modell ist noch nicht heruntergeladen. Bitte in den Einstellungen laden.");
        // Laeuft gerade eine Live-Transkription mit einem anderen Modell, warten (sonst zwei Modelle im Speicher)
        if (!cloud && !"pc".equals(platform.deviceKind())) {
            while (true) {
                Recording r = recorder;
                LiveSession l = r == null ? null : r.live;
                if (l == null || j.live == l || l.model.id.equals(m.id)) break;
                if (!"Wartet auf das Ende der Aufnahme …".equals(j.stage)) stage(j, "queued", "Wartet auf das Ende der Aufnahme …", 0);
                check(j);
                Thread.sleep(1000);
            }
        }
        File work = new File(cacheDir, "work/" + j.id);
        Util.deleteRecursive(work);
        work.mkdirs();
        final File pcm = new File(work, "audio.pcm");
        final Platform.Cancel cancel = () -> j.cancel;
        try {
            // 1) Audio lesen -------------------------------------------------------------------
            stage(j, "decode", "Audio wird gelesen …", 0);
            long n;
            File src = new File(j.srcPath);
            try {
                n = toPcm(src, pcm, f -> { j.progress = 0.05 * f; emitJob(j, false); }, cancel);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                Util.err("decode", e);
                throw new UserError("Die Datei konnte nicht gelesen werden. Ist es eine Audio- oder Videodatei?");
            }
            check(j);
            final long total = n;
            final double dur = n / 16000.0;
            j.duration = dur;
            if (dur < 0.3) throw new UserError("Die Datei enthält keine hörbare Tonspur.");

            // 2) Sprecher ---------------------------------------------------------------------
            String speakers = opts.optString("speakers", "off");
            boolean diarOn = !"off".equals(speakers);
            List<Chunker.Turn> turns = null;
            final double diarShare = diarOn ? 0.3 : 0.0;
            long maxDiar = Runtime.getRuntime().maxMemory() > 3_000_000_000L ? 240 * 60 : 100 * 60;
            if (diarOn && dur > maxDiar) {
                diarOn = false;
                j.notice = "Die Aufnahme ist länger als " + (maxDiar / 60) + " Minuten. Die Sprechererkennung wurde ausgelassen, damit der Speicher reicht.";
            }
            if (diarOn && !speakerModelReady()) {
                stage(j, "load", "Sprechermodell wird geladen (einmalig, 26 MB) …", 0.05);
                try { ensureSpeakerModel(cancel); }
                catch (InterruptedException e) { throw e; }
                catch (Exception e) {
                    diarOn = false;
                    j.notice = "Das Sprechermodell konnte nicht geladen werden (keine Internetverbindung?). Das Transkript enthält keine Sprecher.";
                }
                check(j);
            }
            if (diarOn) {
                File seg = builtin("pyannote-segmentation-3.0.onnx");
                File emb = speakerModel();
                int num = "auto".equals(speakers) ? -1 : Math.max(2, Integer.parseInt(speakers));
                float threshold = (float) nativeState().optDouble("cluster_threshold", 0.6);
                if (num < 0 && !nativeState().has("cluster_threshold")) {
                    stage(j, "calibrate", "Sprecher-Automatik wird eingerichtet (nur beim ersten Mal) …", 0.05);
                    threshold = runCalibration(j);
                }
                String sens = opts.optString("sensitivity", "normal");
                if ("fewer".equals(sens)) threshold += 0.1f; else if ("more".equals(sens)) threshold -= 0.1f;
                stage(j, "diarize", "Sprecher werden erkannt …", 0.05);
                float[] all = Wav.readPcm(pcm, 0, total);
                try {
                    turns = Speech.diarize(all, seg, emb, num, Math.max(0.1f, threshold), platform.threads(),
                            f -> { j.progress = 0.05 + diarShare * f; emitJob(j, false); }, cancel);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Throwable e) {
                    Util.err("diarize", e);
                    turns = null;
                    j.notice = "Sprechererkennung fehlgeschlagen. Das Transkript enthält keine Sprecher.";
                }
                all = null;
                if (turns != null) turns = Chunker.cleanTurns(turns, 4.0, 0.02);
                if (turns != null && turns.isEmpty()) turns = null;
            }
            check(j);

            String lang = m.isWhisper() || cloud ? opts.optString("language", "de") : "auto";
            String task = m.canTranslate ? opts.optString("task", "transcribe") : "transcribe";
            final boolean withSpk = turns != null;
            final double base = 0.05 + diarShare;
            final double span = 1.0 - base - 0.02;
            final Replacements rep = Replacements.load(dataDir);
            final String[] detected = {null};

            LiveSession live = j.live;
            if (cloud) {
                if (live != null) { live.abort(); j.live = null; }
                // 3c) Erkennung auf dem Server, Abschnitt fuer Abschnitt --------------------------------
                stage(j, "transcribe", "Wird auf dem " + rem.name() + " transkribiert …", base);
                final long t0 = System.currentTimeMillis();
                final String fLang = lang;
                Chunker chunker = new Chunker(turns, c -> {
                    check(j);
                    long pad = 2400;
                    long from = Math.max(0, c.start - pad), to = Math.min(total, c.end + pad);
                    float[] samples = Wav.readPcm(pcm, from, to - from);
                    if (samples.length < 1600) return;
                    String text;
                    try {
                        text = rem.recognize(samples, "auto".equals(fLang) ? null : fLang);
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        Util.err("remote", e);
                        throw new UserError("Der Uni-Server hat nicht geantwortet: " + e.getMessage()
                                + ". Versuche es später erneut oder transkribiere auf diesem Gerät.");
                    }
                    Speech.Result res = new Speech.Result(text, null, null);
                    JSONObject s = segmentJson(res, c, from, (c.end - c.start) / 16000.0, rep);
                    if (s != null) {
                        if (withSpk) s.put("speaker", c.speaker);
                        synchronized (j.segments) { s.put("id", j.segments.size()); j.segments.add(s); }
                        JSONObject ev = new JSONObject();
                        ev.put("type", "segment"); ev.put("job_id", j.id); ev.put("segment", s);
                        emit(ev);
                    }
                    double f = Math.min(1.0, c.end / (double) total);
                    j.progress = base + span * f;
                    double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
                    j.eta = f > 0.03 && elapsed > 3 ? elapsed * (1 - f) / f : null;
                    emitJob(j, false);
                }, 28);
                Vad vad = Speech.newVad(builtin("silero_vad.onnx"));
                try {
                    long block = 16000L * 10;
                    for (long pos = 0; pos < total; pos += block) {
                        check(j);
                        feedVad(vad, Wav.readPcm(pcm, pos, Math.min(block, total - pos)), chunker);
                    }
                    vad.flush();
                    drainVad(vad, chunker);
                    chunker.finish();
                } finally {
                    vad.release();
                }
            } else if (live != null && live.compatible(opts)) {
                // 3a) Schon waehrend der Aufnahme transkribiert: nur noch den Rest abwarten ---------
                stage(j, "transcribe", "Letzte Sätze werden transkribiert …", base);
                live.await(cancel, f -> { j.progress = base + span * f; emitJob(j, false); });
                for (JSONObject s : live.segments()) {
                    if (withSpk) s.put("speaker", Chunker.speakerAt(turns, s.optDouble("start"), s.optDouble("end")));
                    synchronized (j.segments) { j.segments.add(s); }
                }
                detected[0] = live.detectedLanguage();
            } else {
                if (live != null) { live.abort(); j.live = null; }
                // 3) Modell laden ------------------------------------------------------------------
                stage(j, "load", "Modell „" + m.name + "“ wird geladen …", base);
                Speech.Handle h = Speech.acquire(m, modelDir(m), lang, task, platform.threads());
                try {
                    check(j);
                    // 4) Sprachabschnitte finden und transkribieren --------------------------------------
                    final long t0 = System.currentTimeMillis();
                    stage(j, "transcribe", "Transkribiere …", base);
                    Chunker chunker = new Chunker(turns, c -> {
                        check(j);
                        long pad = 2400; // 150 ms Rand
                        long from = Math.max(0, c.start - pad), to = Math.min(total, c.end + pad);
                        float[] samples = Wav.readPcm(pcm, from, to - from);
                        if (samples.length < 1600) return;
                        Speech.Result res = Speech.recognize(h, samples);
                        if (detected[0] == null && res.lang != null && !res.lang.isEmpty()) detected[0] = res.lang.replace("<|", "").replace("|>", "");
                        JSONObject s = segmentJson(res, c, from, (c.end - c.start) / 16000.0, rep);
                        if (s != null) {
                            if (withSpk) s.put("speaker", c.speaker);
                            synchronized (j.segments) { s.put("id", j.segments.size()); j.segments.add(s); }
                            JSONObject ev = new JSONObject();
                            ev.put("type", "segment"); ev.put("job_id", j.id); ev.put("segment", s);
                            emit(ev);
                        }
                        double f = Math.min(1.0, c.end / (double) total);
                        j.progress = base + span * f;
                        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
                        j.eta = f > 0.03 && elapsed > 3 ? elapsed * (1 - f) / f : null;
                        emitJob(j, false);
                    });
                    Vad vad = Speech.newVad(builtin("silero_vad.onnx"));
                    try {
                        long block = 16000L * 10;
                        for (long pos = 0; pos < total; pos += block) {
                            check(j);
                            feedVad(vad, Wav.readPcm(pcm, pos, Math.min(block, total - pos)), chunker);
                        }
                        vad.flush();
                        drainVad(vad, chunker);
                        chunker.finish();
                    } finally {
                        vad.release();
                    }
                } finally {
                    Speech.release(h);
                }
            }
            if (cloud) j.language = "auto".equals(lang) ? null : lang;
            else if (detected[0] != null && ("auto".equals(lang) || !m.isWhisper())) j.language = detected[0];
            else if (m.isWhisper()) j.language = "translate".equals(task) ? "en" : lang;
            else j.language = null;

            // 5) Speichern ---------------------------------------------------------------------
            stage(j, "save", "Wird gespeichert …", 0.99);
            j.transcriptId = saveTranscript(j, m, pcm, withSpk);
            if (!cloud) recordSpeed(m, j);
        } finally {
            Util.deleteRecursive(work);
        }
    }

    /** Erkanntes Stueck als Segment (oder null, wenn nichts Brauchbares erkannt wurde). */
    static JSONObject segmentJson(Speech.Result res, Chunker.Chunk c, long fromSample, double seconds, Replacements rep) throws Exception {
        String raw = res.text == null ? "" : res.text.trim();
        String text = TextClean.clean(raw, seconds);
        if (text.isEmpty()) return null;
        List<Speech.Word> words = res.words;
        if (words != null && !words.isEmpty() && !Replacements.sameText(words, text)) words = null;
        if (rep != null && !rep.isEmpty()) {
            String t2 = rep.apply(text);
            if (!t2.equals(text)) { words = rep.applyWords(words, t2); text = t2.trim(); }
            if (text.isEmpty()) return null;
        }
        JSONObject s = new JSONObject();
        s.put("start", Math.round(c.start / 16.0) / 1000.0);
        s.put("end", Math.round(c.end / 16.0) / 1000.0);
        s.put("text", text);
        if (words != null && !words.isEmpty()) {
            double off = fromSample / 16000.0;
            JSONArray w = new JSONArray();
            for (Speech.Word x : words) {
                w.put(new JSONArray().put(Math.round((off + x.start) * 100) / 100.0).put(Math.round((off + x.end) * 100) / 100.0).put(x.text));
            }
            s.put("words", w);
        }
        return s;
    }

    /** Merkt sich, wie schnell ein Modell auf diesem Geraet ist (Sekunden Rechenzeit pro Sekunde Audio). */
    private void recordSpeed(Models.ModelInfo m, Job j) {
        if (j.duration < 30 || j.live != null) return;
        try {
            double rtf = (System.currentTimeMillis() - j.started) / 1000.0 / j.duration;
            JSONObject ns = nativeState();
            JSONObject sp = ns.optJSONObject("speed");
            if (sp == null) sp = new JSONObject();
            double old = sp.optDouble(m.id, Double.NaN);
            sp.put(m.id, Math.round((Double.isNaN(old) ? rtf : old * 0.6 + rtf * 0.4) * 1000) / 1000.0);
            ns.put("speed", sp);
            saveNativeState(ns);
        } catch (Exception e) { Util.err("speed", e); }
    }

    /** sherpa-onnx entscheidet pro Aufruf ueber Sprache/Stille, daher in Fenstern von 512 Samples fuettern. */
    static void feedVad(Vad vad, float[] b, Chunker chunker) throws Exception {
        final int w = 512;
        float[] win = new float[w];
        int i = 0;
        for (; i + w <= b.length; i += w) {
            System.arraycopy(b, i, win, 0, w);
            vad.acceptWaveform(win);
            drainVad(vad, chunker);
        }
        if (i < b.length) { vad.acceptWaveform(Arrays.copyOfRange(b, i, b.length)); drainVad(vad, chunker); }
    }

    static void drainVad(Vad vad, Chunker chunker) throws Exception {
        while (!vad.empty()) {
            SpeechSegment s = vad.front();
            vad.pop();
            long start = s.getStart();
            chunker.addRange(start, start + s.getSamples().length);
        }
    }

    private float runCalibration(final Job j) throws Exception {
        calibrating = true;
        try {
            File wav = builtin("cal-4speakers.wav");
            File pcm = new File(cacheDir, "cal.pcm");
            toPcm(wav, pcm, null, null);
            float[] a = Wav.readPcm(pcm, 0, pcm.length() / 2);
            pcm.delete();
            StringBuilder table = new StringBuilder();
            float t = Speech.calibrate(a, builtin("pyannote-segmentation-3.0.onnx"), speakerModel(), platform.threads(),
                    f -> { if (j != null) { j.progress = 0.05 * f; emitJob(j, false); } }, table, () -> j != null && j.cancel);
            JSONObject ns = nativeState();
            ns.put("cluster_threshold", (double) t);
            ns.put("calibration", table.toString().trim());
            saveNativeState(ns);
            Util.log("Kalibrierung: " + t + " / " + table);
            return t;
        } finally {
            calibrating = false;
        }
    }

    public void recalibrate() {
        JSONObject ns = nativeState();
        ns.remove("cluster_threshold");
        saveNativeState(ns);
        emitState();
    }

    private static final Set<String> KEEP_AS_IS = new HashSet<>(Arrays.asList("mp3", "m4a", "aac", "ogg", "oga", "opus", "webm", "flac"));

    private String saveTranscript(Job j, Models.ModelInfo m, File pcm, boolean withSpk) throws Exception {
        String id = Util.hex(6);
        File dir = new File(transcripts, id);
        dir.mkdirs();
        // Audio fuer die Wiedergabe: komprimierte Formate unveraendert, sonst platzsparend neu kodieren
        String audioName = null;
        File src = new File(j.srcPath);
        String ext = Util.ext(j.name);
        try {
            if (src.isFile() && KEEP_AS_IS.contains(ext) && src.length() < 400L * 1024 * 1024) {
                String outExt = "opus".equals(ext) ? "ogg" : ext;
                audioName = "audio." + outExt;
                File dst = new File(dir, audioName);
                if (!(j.deleteSrc && src.renameTo(dst))) Util.copyFile(src, dst);
            } else {
                stage(j, "save", "Audio wird komprimiert …", 0.99);
                audioName = platform.encodeSpeech(pcm, dir, "audio");
                if (audioName == null || !new File(dir, audioName).isFile()) {
                    audioName = "audio.wav";
                    Wav.pcmToWav(pcm, new File(dir, audioName));
                }
            }
        } catch (Exception e) {
            Util.err("audio copy", e);
            audioName = null;
        }
        // Nur ein Sprecher erkannt: keine Sprecheranzeige
        if (withSpk) {
            Set<Integer> distinct = new HashSet<>();
            synchronized (j.segments) { for (JSONObject s : j.segments) distinct.add(s.optInt("speaker", 0)); }
            if (distinct.size() <= 1) {
                withSpk = false;
                synchronized (j.segments) { for (JSONObject s : j.segments) s.remove("speaker"); }
            }
        }
        // Sprecher neu nummerieren (in Reihenfolge des Auftretens)
        Map<Integer, Integer> remap = new LinkedHashMap<>();
        JSONArray segs = new JSONArray();
        synchronized (j.segments) {
            int i = 0;
            for (JSONObject s : j.segments) {
                if (withSpk) {
                    int k = s.optInt("speaker", 0);
                    if (!remap.containsKey(k)) remap.put(k, remap.size());
                    s.put("speaker", remap.get(k));
                }
                s.put("id", i++);
                segs.put(s);
            }
        }
        JSONObject spk = new JSONObject();
        for (int i = 0; i < remap.size(); i++) spk.put(String.valueOf(i), "Sprecher " + (i + 1));
        JSONObject t = new JSONObject();
        String title = j.opts.optString("title", "");
        if (title.isEmpty()) {
            title = j.name;
            int dot = title.lastIndexOf('.');
            if (dot > 0) title = title.substring(0, dot);
            if ("translate".equals(j.opts.optString("task"))) title += " (Englisch)";
        }
        t.put("id", id);
        t.put("title", title);
        t.put("created", isoNow());
        t.put("source_name", j.name);
        t.put("source_size", j.size);
        t.put("duration", Math.round(j.duration * 100) / 100.0);
        t.put("language", j.language == null ? JSONObject.NULL : j.language);
        t.put("model", m.id);
        t.put("model_name", m.name);
        t.put("device", platform.deviceKind());
        t.put("processing_seconds", Math.round((System.currentTimeMillis() - j.started) / 100.0) / 10.0);
        t.put("options", j.opts);
        t.put("diarization", withSpk);
        t.put("speakers", spk);
        t.put("segments", segs);
        t.put("audio", audioName == null ? JSONObject.NULL : audioName);
        if (audioName != null) t.put("audio_size", new File(dir, audioName).length());
        if (j.marks != null && j.marks.length() > 0) t.put("marks", j.marks);
        String folder = j.opts.optString("folder", "");
        t.put("folder", folder.isEmpty() ? JSONObject.NULL : folder);
        if (j.notice != null) t.put("notice", j.notice);
        t.put("version", 3);
        Util.writeText(new File(dir, "transcript.json"), t.toString());
        index.invalidate(id);
        return id;
    }

    /** Beschreibt die Server-Erkennung im Transkript (Modell "cloud"). */
    static final Models.ModelInfo CLOUD_INFO = new Models.ModelInfo(CLOUD, "Uni-Server", "Whisper auf dem Server", "cloud", "", "", 0, false, false);

    // ------------------------------------------------------------------ Aufnahme
    /**
     * Startet die Aufnahme. Mit {@code liveOpts} (Modell, Sprache) wird schon waehrend der Aufnahme
     * transkribiert, sofern das Modell geladen ist und nicht gerade ein anderes Modell rechnet.
     */
    public synchronized boolean startRecording(JSONObject liveOpts, String deviceId) {
        if (recorder != null) return true;
        String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        final File f = new File(recordings, "rec-" + ts + ".wav");
        final Recording[] self = new Recording[1];
        Recording r = new Recording(platform, f, deviceId, new Recording.Listener() {
            long last;
            @Override public void onLevel(float level, double seconds) {
                long now = System.currentTimeMillis();
                if (now - last < 90) return;
                last = now;
                Recording cur = recorder;
                emitSimple("rec", "level", (double) level, "seconds", seconds, "paused", cur != null && cur.isPaused());
            }
            @Override public void onError(String msg) {
                toast("error", "Aufnahme-Fehler: " + msg + (self[0] != null && self[0].seconds() > 1 ? " Das bisher Aufgenommene bleibt erhalten." : ""));
                Recording rec;
                synchronized (Engine.this) { rec = recorder; if (rec == self[0]) recorder = null; }
                if (rec == self[0] && rec != null) {
                    if (rec.live != null) rec.live.abort();
                    new Thread(() -> {
                        try {
                            Thread.sleep(300);
                            if (rec.seconds() >= 1) offerRecording(rec.file, rec.marksFile, false);
                            else { rec.file.delete(); rec.marksFile.delete(); }
                        } catch (Exception e) { Util.err("rec error", e); }
                    }).start();
                }
                emitState();
            }
        });
        self[0] = r;
        if (liveOpts != null && liveOpts.optBoolean("live", false)) {
            Models.ModelInfo m = Models.get(liveOpts.optString("model", ""));
            if (m != null && installed(m)) {
                String lang = m.isWhisper() ? liveOpts.optString("language", "de") : "auto";
                String active = Speech.activeKey();
                if (active == null || active.equals(Speech.key(m, lang, "transcribe"))) r.live = new LiveSession(this, m, lang, f);
            }
        }
        recorder = r;
        r.start();
        emitState();
        return true;
    }

    public synchronized void pauseRecording(boolean p) {
        if (recorder != null) recorder.setPaused(p);
        emitState();
    }

    public JSONObject addMarker(String label) {
        Recording r = recorder;
        if (r == null) return null;
        JSONObject m = r.addMark(label);
        emitSimple("mark", "mark", m);
        return m;
    }

    public void stopRecording(boolean keep, JSONObject opts) {
        Recording r;
        synchronized (this) { r = recorder; recorder = null; }
        if (r == null) return;
        r.stop();
        double secs = r.seconds();
        if (keep && secs >= 0.5) {
            String title = new SimpleDateFormat("'Aufnahme' dd.MM.yyyy HH-mm", Locale.GERMANY).format(new Date());
            LiveSession live = r.live;
            if (live != null) {
                if (live.compatible(opts)) live.finish(); else { live.abort(); live = null; }
            }
            Job j = new Job();
            j.id = Util.hex(5); j.name = title + ".wav"; j.srcPath = r.file.getAbsolutePath(); j.size = r.file.length();
            j.opts = opts; j.deleteSrc = true; j.marks = r.marks(); j.live = live;
            synchronized (this) { jobs.put(j.id, j); saveJobs(); }
            queue.add(j);
            emitJob(j, true);
            r.marksFile.delete();
        } else {
            if (r.live != null) r.live.abort();
            r.file.delete();
            r.marksFile.delete();
        }
        emitState();
    }

    // ------------------------------------------------------------------ Teilen
    /** Ordner fuer Dateien, die geteilt werden; Dateien aelter als {@code maxAgeMs} werden geloescht. */
    public File shareDir() {
        cleanShareDir(3600_000L);
        File d = new File(cacheDir, "share");
        d.mkdirs();
        return d;
    }

    private void cleanShareDir(long maxAgeMs) {
        File[] fs = new File(cacheDir, "share").listFiles();
        if (fs == null) return;
        long now = System.currentTimeMillis();
        for (File f : fs) if (now - f.lastModified() > maxAgeMs) Util.deleteRecursive(f);
    }

    // ------------------------------------------------------------------ Sonstiges
    static String isoNow() { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()); }
}
