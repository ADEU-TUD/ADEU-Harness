package io.github.thepytales.llmhub.data

/**
 * Our own mirror on Hugging Face (AP-106, decision of 10.11.2026): the curated models of the Lora Edition lie there
 * byte for byte with licence and model card under huggingface.co/[ORG], each at a fixed commit. [sources] gives the
 * mirror first and the original as a fallback – path, size and SHA-256 are the same, so a download that stopped can go
 * on from the other source. A new model of the catalog is mirrored first (tools/huggingface) and entered here.
 */
object ModelMirror {
    const val ORG = "thepytales"

    /** Source "owner/repo@commit" → mirror "repo@commit" in [ORG]. */
    val REPOS: Map<String, String> = mapOf(
        "unsloth/Qwen3.5-0.8B-GGUF@6ab461498e2023f6e3c1baea90a8f0fe38ab64d0" to "qwen3.5-0.8b-lora-edition@9c63d1ad44d450cfd6d5133d40264f4aa623f5f1",
        "unsloth/Qwen3.5-2B-GGUF@f6d5376be1edb4d416d56da11e5397a961aca8ae" to "qwen3.5-2b-lora-edition@dc556816b3504faf8867a86ca3c59eeece465580",
        "ggml-org/gemma-4-E2B-it-GGUF@6938c58e698502037401a5f19f6905f2fb2f7dee" to "gemma-4-e2b-lora-edition@23cce86e0f04c96153fbac238b5dbe5f30e9afe4",
        "unsloth/Qwen3.5-4B-GGUF@e87f176479d0855a907a41277aca2f8ee7a09523" to "qwen3.5-4b-lora-edition@94a62f98bc9310fe15f2bfc3d67c72be4e191824",
        "ggml-org/gemma-4-E4B-it-GGUF@a69f018d7678a2b9f5e705f8903d6fb29b9b25dc" to "gemma-4-e4b-lora-edition@29a6e4962cbc4bfefeb4ccf30646a5049b299db7",
        "unsloth/Qwen2.5-Coder-7B-Instruct-GGUF@0ecf11859560b2bf42e703207f9371186d02245f" to "qwen2.5-coder-7b-lora-edition@8791ac6d1f2768a99af225812aba89fc1aafcd94",
        "unsloth/Qwen3.5-9B-GGUF@3885219b6810b007914f3a7950a8d1b469d598a5" to "qwen3.5-9b-lora-edition@36172661ffa8ef35f2e05b7006af892c8d1e7bba",
        "ggml-org/gemma-4-12B-it-GGUF@a29b2a6b584a5bdcb672ccdcda99ea9faeacc51a" to "gemma-4-12b-lora-edition@ff9eef048e046cc133e9012a081caabee1d6b0ab",
        "unsloth/Qwen2.5-Coder-14B-Instruct-GGUF@388f3f20271ef903bb2dbe7d8236158903e7edb3" to "qwen2.5-coder-14b-lora-edition@dfe02271b764da8f2cb2fa09b0bbba5cd5ba4c83",
        "ggml-org/gemma-4-26B-A4B-it-GGUF@9cf8c8db0f83fb4582f15381f8fb1a7c1b0b9ca2" to "gemma-4-26b-a4b-lora-edition@720521f4bb8b24f4a6a6be0fc7e1b967fbbce6ae",
        "unsloth/Qwen3.6-27B-GGUF@82d411acf4a06cfb8d9b073a5211bf410bfc29bf" to "qwen3.6-27b-lora-edition@0c2bfb1056eb25ff0fdd4ea67a59565e51f4d31f",
        "unsloth/gemma-4-31B-it-GGUF@c1ac76e99d5513b141e8adde7288b85c3f9c32ec" to "gemma-4-31b-lora-edition@66e0715439b4fada018328f215b3dbfef0790685",
        "unsloth/Qwen3-Coder-30B-A3B-Instruct-GGUF@b17cb02dd882d5b6ab62fc777ad2995f19668350" to "qwen3-coder-30b-a3b-lora-edition@3665712c58a51f80e3ebab854c2e34e18dd1757a",
        "ggml-org/Qwen3.6-35B-A3B-GGUF@7878dc9a905c7c9379e5e8d6a1aa9affdd6839c5" to "qwen3.6-35b-a3b-lora-edition@e75f6b025e0eda7d041ba0b29176d73f7bd31aee",
        "intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3" to "multilingual-e5-small-lora-edition@fd34574b2e7c46e1110a04aa9a3102e4c0699a2d",
    )

    private val HF = Regex("""^https://huggingface\.co/([^/]+/[^/]+)/resolve/([0-9a-f]{40})/([^?]+)(\?.*)?$""")

    /** The mirror of [url] (null if it has none). */
    fun mirror(url: String): String? {
        val m = HF.matchEntire(url) ?: return null
        val (repo, commit, path, query) = m.destructured
        val (name, at) = REPOS["$repo@$commit"]?.split('@') ?: return null
        return "https://huggingface.co/$ORG/$name/resolve/$at/$path$query"
    }

    /** Where to load [url] from, in order: the mirror first, then the original. */
    fun sources(url: String): List<String> = listOfNotNull(mirror(url), url)
}
