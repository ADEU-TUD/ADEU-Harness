package de.lokal.whisper;

/**
 * Die ladbaren Erkennungsmodelle. Geladen wird immer ein fester Stand (Commit) von Hugging Face,
 * jede Datei wird mit ihrer SHA-256-Pruefsumme kontrolliert. So kann eine Aenderung am Repo die App
 * nicht kaputt machen.
 */
public final class Models {
    private Models() {}

    public static final class FileSpec {
        public final String name; public final long size; public final String sha256;
        FileSpec(String name, long size, String sha256) { this.name = name; this.size = size; this.sha256 = sha256; }
    }

    public static final class ModelInfo {
        public final String id, name, desc, kind, repo, commit;
        public final FileSpec[] files;
        public final int featDim;
        /** Whisper kann ins Englische uebersetzen; Parakeet nicht. */
        public final boolean canTranslate;
        /** Liefert Zeitstempel pro Wort (Wort-Hervorhebung beim Abspielen). */
        public final boolean wordTimes;

        ModelInfo(String id, String name, String desc, String kind, String repo, String commit, int featDim,
                  boolean canTranslate, boolean wordTimes, FileSpec... files) {
            this.id = id; this.name = name; this.desc = desc; this.kind = kind; this.repo = repo; this.commit = commit;
            this.featDim = featDim; this.canTranslate = canTranslate; this.wordTimes = wordTimes; this.files = files;
        }

        public long size() { long s = 0; for (FileSpec f : files) s += f.size; return s; }

        /** Die bisherige Quelle der Datei. */
        public String originalUrl(FileSpec f) { return "https://huggingface.co/" + repo + "/resolve/" + commit + "/" + f.name + "?download=true"; }

        /** Wo die Datei geladen wird, der Reihe nach: unser Spiegel (gleiche Datei, gleicher SHA-256), dann das Original. */
        public String[] urls(FileSpec f) {
            String m = mirror(repo, commit);
            String original = originalUrl(f);
            return m == null ? new String[]{original}
                : new String[]{"https://huggingface.co/" + MIRROR_ORG + "/" + m.replace("@", "/resolve/") + "/" + f.name + "?download=true", original};
        }

        /** Die erste Quelle (für „von Hand laden“). */
        public String url(FileSpec f) { return urls(f)[0]; }

        public boolean isWhisper() { return "whisper".equals(kind); }

        FileSpec file(String suffix) {
            for (FileSpec f : files) if (f.name.endsWith(suffix)) return f;
            throw new IllegalStateException(id + ": " + suffix);
        }
    }

    /**
     * Eigener Spiegel auf Hugging Face (Lora Edition, byte-gleich, mit Lizenz und Modellkarte): Quelle "owner/repo@commit"
     * → Spiegel "repo@commit" in {@link #MIRROR_ORG}. Ein neues Modell zuerst spiegeln (tools/huggingface), dann hier eintragen.
     */
    public static final String MIRROR_ORG = "thepytales";
    private static final String[] MIRRORS = {
        "csukuangfj/sherpa-onnx-whisper-turbo@2ca6ff69fc878651b770880507669577ac41c2ff", "whisper-large-v3-turbo-lora-edition@b682226a200b4ddf308c83f29d52120e0eb106da",
        "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8@2bda32ec70b097a55adaa07d9a7173915b43cc78", "parakeet-tdt-0.6b-v3-lora-edition@22274dc80ec506b321c018b3074d3a247b79df8c",
        "csukuangfj/sherpa-onnx-whisper-small@8f3c18b358db4d1f2fc1eae49d75cd20989e4309", "whisper-small-lora-edition@e0f06bc2d90ddd6d7b3e0ef2a1ac272850072c08",
        "csukuangfj/sherpa-onnx-whisper-base@bb53ee204431c90d314c1cc08d28d23e5b7927cc", "whisper-base-lora-edition@5bdd164edfe5e46d8d874931acc8673022f5ddf8",
        "csukuangfj/speaker-embedding-models@0743f301363dec56491a490f6d6cbc9d67f9a3bf", "wespeaker-resnet34-lm-lora-edition@b30aca49122a45ae2466679bc91513036101407e",
    };

    /** Spiegel von repo@commit als "repo@commit" in {@link #MIRROR_ORG}, null ohne Spiegel. */
    static String mirror(String repo, String commit) {
        String key = repo + "@" + commit;
        for (int i = 0; i < MIRRORS.length; i += 2) if (MIRRORS[i].equals(key)) return MIRRORS[i + 1];
        return null;
    }

    private static final String WHISPER_TOKENS_SHA = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126";

    public static final ModelInfo[] ALL = {
        new ModelInfo("turbo", "Whisper Turbo", "Beste Qualität, für längere Aufnahmen etwas Geduld", "whisper",
            "csukuangfj/sherpa-onnx-whisper-turbo", "2ca6ff69fc878651b770880507669577ac41c2ff", 128, false, false,
            new FileSpec("turbo-encoder.int8.onnx", 674_716_297L, "b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b"),
            new FileSpec("turbo-decoder.int8.onnx", 361_080_764L, "20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547"),
            new FileSpec("turbo-tokens.txt", 816_730L, WHISPER_TOKENS_SHA)),
        new ModelInfo("parakeet", "Parakeet v3", "Sehr schnell, Wort für Wort mitlesen · 25 europäische Sprachen, erkennt die Sprache selbst", "parakeet",
            "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8", "2bda32ec70b097a55adaa07d9a7173915b43cc78", 128, false, true,
            new FileSpec("encoder.int8.onnx", 652_184_281L, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
            new FileSpec("decoder.int8.onnx", 11_845_275L, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
            new FileSpec("joiner.int8.onnx", 6_355_277L, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
            new FileSpec("tokens.txt", 93_939L, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d")),
        new ModelInfo("small", "Whisper Small", "Schnell und gut für klare Aufnahmen, kann ins Englische übersetzen", "whisper",
            "csukuangfj/sherpa-onnx-whisper-small", "8f3c18b358db4d1f2fc1eae49d75cd20989e4309", 80, true, false,
            new FileSpec("small-encoder.int8.onnx", 112_442_483L, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
            new FileSpec("small-decoder.int8.onnx", 262_226_114L, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
            new FileSpec("small-tokens.txt", 816_730L, WHISPER_TOKENS_SHA)),
        new ModelInfo("base", "Whisper Base", "Sehr schnell, einfache Qualität", "whisper",
            "csukuangfj/sherpa-onnx-whisper-base", "bb53ee204431c90d314c1cc08d28d23e5b7927cc", 80, true, false,
            new FileSpec("base-encoder.int8.onnx", 29_120_534L, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
            new FileSpec("base-decoder.int8.onnx", 130_672_026L, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
            new FileSpec("base-tokens.txt", 816_730L, WHISPER_TOKENS_SHA)),
    };

    public static ModelInfo get(String id) {
        for (ModelInfo m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    /** Sprechermodell (wird bei der ersten Sprechererkennung geladen). */
    public static final FileSpec SPEAKER = new FileSpec("wespeaker-resnet34-LM.onnx", 26_530_550L,
        "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012");
    public static final String[] SPEAKER_URLS = {
        "https://huggingface.co/" + MIRROR_ORG + "/wespeaker-resnet34-lm-lora-edition/resolve/b30aca49122a45ae2466679bc91513036101407e/wespeaker_en_voxceleb_resnet34_LM.onnx?download=true",
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx",
        "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/0743f301363dec56491a490f6d6cbc9d67f9a3bf/wespeaker_en_voxceleb_resnet34_LM.onnx?download=true",
    };
}
