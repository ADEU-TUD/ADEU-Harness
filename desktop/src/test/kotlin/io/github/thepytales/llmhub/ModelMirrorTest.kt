package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.LocalEmbeddingInfo
import io.github.thepytales.llmhub.data.ModelMirror
import io.github.thepytales.llmhub.local.LocalCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Eigener Spiegel auf Hugging Face (AP-106): every model of the catalog comes from the mirror first, then the original. */
class ModelMirrorTest {
    private val files: List<String> = LocalCatalog.models.flatMap { m -> listOfNotNull(m.url, m.projector?.url) } +
        listOf(LocalEmbeddingInfo.MODEL_URL, LocalEmbeddingInfo.VOCAB_URL)

    @Test
    fun everyCatalogFileHasItsMirrorFirst() {
        for (url in files) {
            val sources = ModelMirror.sources(url)
            assertEquals(2, sources.size, "Kein Spiegel für $url – zuerst spiegeln (tools/huggingface), dann in ModelMirror.REPOS eintragen")
            assertTrue(sources[0].startsWith("https://huggingface.co/${ModelMirror.ORG}/"), sources[0])
            assertEquals(url, sources[1])
            // Same file at the same path (and the same query), only another repository and commit.
            assertEquals(url.substringAfter("/resolve/").substringAfter('/'), sources[0].substringAfter("/resolve/").substringAfter('/'))
            assertTrue(Regex("/resolve/[0-9a-f]{40}/").containsMatchIn(sources[0]), "fester Commit: ${sources[0]}")
        }
    }

    @Test
    fun aKnownFileMapsExactly() {
        val m = LocalCatalog.get("qwen3.5-4b-q8")!!
        assertEquals(
            "https://huggingface.co/thepytales/qwen3.5-4b-lora-edition/resolve/94a62f98bc9310fe15f2bfc3d67c72be4e191824/Qwen3.5-4B-Q8_0.gguf?download=true",
            ModelMirror.mirror(m.url),
        )
        assertEquals(
            "https://huggingface.co/thepytales/multilingual-e5-small-lora-edition/resolve/fd34574b2e7c46e1110a04aa9a3102e4c0699a2d/onnx/model_qint8_avx512_vnni.onnx",
            ModelMirror.mirror(LocalEmbeddingInfo.MODEL_URL),
        )
    }

    @Test
    fun otherFilesStayAsTheyAre() {
        // Own models, other commits and other hosts have no mirror: only the original.
        for (url in listOf(
            "https://raw.githubusercontent.com/ADEU-TUD/Lora/43ad93492aaa3e89742bccd6994d04eef9b89bb4/lora-namensschutz-pytales.int8.onnx.gz",
            "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/0000000000000000000000000000000000000000/Qwen3.5-4B-Q4_K_M.gguf",
            "https://huggingface.co/csukuangfj/vits-piper-de_DE-thorsten-medium/resolve/da8bed7888b27d17e1aa8fbd53e6703ab939583a/tokens.txt",
        )) {
            assertNull(ModelMirror.mirror(url), url)
            assertEquals(listOf(url), ModelMirror.sources(url))
        }
    }
}
