package de.lokal.whisper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Eigener Spiegel auf Hugging Face: jede Datei der Erkennungsmodelle zuerst vom Spiegel, dann vom Original. */
class ModelsMirrorTest {
    @Test
    void everyFileHasItsMirrorFirst() {
        for (Models.ModelInfo m : Models.ALL) {
            for (Models.FileSpec f : m.files) {
                String[] urls = m.urls(f);
                assertEquals(2, urls.length, m.id + "/" + f.name);
                assertTrue(urls[0].startsWith("https://huggingface.co/" + Models.MIRROR_ORG + "/"), urls[0]);
                assertTrue(urls[0].matches(".*/resolve/[0-9a-f]{40}/" + java.util.regex.Pattern.quote(f.name) + "\\?download=true"), urls[0]);
                assertEquals(m.originalUrl(f), urls[1]);
                assertEquals(urls[0], m.url(f));
            }
        }
        assertTrue(Models.SPEAKER_URLS[0].startsWith("https://huggingface.co/" + Models.MIRROR_ORG + "/"));
        assertEquals(3, Models.SPEAKER_URLS.length);
    }

    @Test
    void turboMapsExactly() {
        Models.ModelInfo m = Models.get("turbo");
        assertEquals("https://huggingface.co/thepytales/whisper-large-v3-turbo-lora-edition/resolve/b682226a200b4ddf308c83f29d52120e0eb106da/turbo-encoder.int8.onnx?download=true",
            m.urls(m.files[0])[0]);
    }
}
