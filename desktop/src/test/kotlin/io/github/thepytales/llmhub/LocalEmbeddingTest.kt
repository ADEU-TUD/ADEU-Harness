package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.KnowledgeStore
import io.github.thepytales.llmhub.data.LocalEmbeddingInfo
import io.github.thepytales.llmhub.data.OrtClassifier
import io.github.thepytales.llmhub.data.SentencePiece
import io.github.thepytales.llmhub.data.meanPool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Suche nach Bedeutung auf dem Gerät (AP-107). Without the model: the SentencePiece tokenizer on a small hand-made
 * model, mean pooling and the mixing of the search. With SUCHE_MODELL (folder with multilingual-e5-small.int8.onnx and
 * multilingual-e5-small.spm) and SUCHE_ORT (libonnxruntime): the same ids as Hugging Face on tools/suche/modell/token-probe.json
 * and the Messkorpus through the KnowledgeStore of the app → desktop/build/suche/bericht.json.
 */
class LocalEmbeddingTest {
    /** A SentencePiece model with the given pieces (protobuf as in sentencepiece.bpe.model). */
    private fun model(pieces: List<Triple<String, Float, Int>>): ByteArray {
        fun varint(out: ByteArrayOutputStream, v: Long) { var x = v; while (x >= 0x80) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }; out.write(x.toInt()) }
        val all = ByteArrayOutputStream()
        for ((p, score, type) in pieces) {
            val m = ByteArrayOutputStream()
            val bytes = p.toByteArray()
            varint(m, (1 shl 3 or 2).toLong()); varint(m, bytes.size.toLong()); m.write(bytes)
            varint(m, (2 shl 3 or 5).toLong()); val bits = java.lang.Float.floatToIntBits(score); for (k in 0 until 4) m.write((bits ushr (8 * k)) and 0xFF)
            varint(m, (3 shl 3).toLong()); varint(m, type.toLong())
            val body = m.toByteArray()
            varint(all, (1 shl 3 or 2).toLong()); varint(all, body.size.toLong()); all.write(body)
        }
        return all.toByteArray()
    }

    private val small by lazy {
        val special = listOf(Triple("<unk>", 0f, 2), Triple("<s>", 0f, 3), Triple("</s>", 0f, 3))
        val words = listOf("▁" to -1f, "▁Hilfs" to -3f, "kraft" to -3f, "▁Hilfskraft" to -4f, "▁H" to -6f, "i" to -5f, "l" to -5f, "f" to -5f,
            "s" to -5f, "k" to -5f, "r" to -5f, "a" to -5f, "t" to -5f, "▁die" to -2f, "▁Stra" to -4f, "ße" to -4f)
        // Filler pieces, so the reader sees more than its minimum.
        val filler = (0 until 1200).map { Triple("▁füll$it", -20f, 1) }
        SentencePiece(model(special + words.map { Triple(it.first, it.second, 1) } + filler))
    }

    @Test
    fun viterbiTakesTheBestPiecesAndShiftsTheIds() {
        // "▁Hilfskraft" (-4) beats "▁Hilfs" + "kraft" (-6).
        assertContentEquals(intArrayOf(6), small.pieces("Hilfskraft"))
        assertContentEquals(longArrayOf(0, 7, 2), small.encode("Hilfskraft"))
        assertContentEquals(intArrayOf(16, 6), small.pieces("  die   Hilfskraft"))
        assertEquals("▁die▁Hilfskraft▁", small.normalize("  die \n Hilfskraft "))
        // NFKC: „ﬁ“ → "fi"; unknown characters next to each other become one <unk> (id 3).
        assertEquals("▁fi", small.normalize("ﬁ"))
        val unknown = small.encode("Straße 😀😀")
        assertEquals(listOf(0L, 18L, 19L, 4L, 3L, 2L), unknown.toList())
        // At most MAX_TOKENS with <s> and </s>.
        assertEquals(10, small.encode("Hilfskraft ".repeat(50), max = 10).size)
    }

    @Test
    fun meanPoolGivesUnitVectors() {
        val v = meanPool(floatArrayOf(1f, 0f, 3f, 0f), 2)
        assertEquals(1f, v[0], 1e-6f)
        assertEquals(0f, v[1], 1e-6f)
    }

    @Test
    fun meaningIsMixedWithStretchedCosines() {
        val store = KnowledgeStore(Files.createTempDirectory("adeu-suche").toFile())
        val a = store.add("a.txt", "Die Hilfskraft arbeitet zwanzig Stunden.", "")
        val b = store.add("b.txt", "Der Wasserkocher braucht eine Prüfplakette. Hilfskraft Hilfskraft.", "")
        runBlocking {
            store.embed(a, LocalEmbeddingInfo.ID) { listOf(floatArrayOf(0.9f, 0.43589f)) }
            store.embed(b, LocalEmbeddingInfo.ID) { listOf(floatArrayOf(0.8f, 0.6f)) }
        }
        // Cosines 0.9 and 0.8 are close; stretched to 1 and 0, the meaning wins over the repeated keyword in b.
        val hits = store.search("Hilfskraft", listOf(""), 2, floatArrayOf(1f, 0f), LocalEmbeddingInfo.ID)
        assertEquals("a.txt", hits.first().doc.name)
        // Without vectors of the same model it is the keyword search.
        assertEquals("b.txt", store.search("Hilfskraft", listOf(""), 2, floatArrayOf(1f, 0f), "anderes/modell").first().doc.name)
    }

    // ── With the real model ──────────────────────────────────────────────────────────────────────────

    private val korpus = File("../tools/suche/messkorpus")

    private fun fragen(teil: String) = File(korpus, "$teil.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
        val (q, d, b) = l.split("|").map { it.trim() }
        Triple(q, d, b)
    }

    @Test
    fun realModelGivesTheSameTokensAndFindsTheMesskorpus() {
        val dir = System.getenv("SUCHE_MODELL")?.let(::File)?.takeIf { File(it, LocalEmbeddingInfo.MODEL_FILE).isFile } ?: return
        val lib = System.getenv("SUCHE_ORT")?.let(::File)?.takeIf { it.isFile } ?: return
        val tok = SentencePiece(File(dir, LocalEmbeddingInfo.VOCAB_FILE).readBytes())
        val probe = Json.parseToJsonElement(File("../tools/suche/modell/token-probe.json").readText()).jsonArray
        val wrong = probe.map { it.jsonObject }.filter { o ->
            val want = o["ids"]!!.jsonArray.map { it.jsonPrimitive.int.toLong() }
            tok.encode(o["text"]!!.jsonPrimitive.content, max = 100_000).toList() != want
        }.map { it["text"]!!.jsonPrimitive.content.take(60) }
        assertTrue(wrong.isEmpty(), "${wrong.size} von ${probe.size} Texten mit anderen Ids: $wrong")

        OrtClassifier(lib, File(dir, LocalEmbeddingInfo.MODEL_FILE), labels = 384, perToken = true, name = "test-suche", output = "last_hidden_state", softmax = false).use { enc ->
            fun embed(texts: List<String>, prefix: String) = texts.map { t -> val ids = tok.encode(prefix + t); meanPool(enc.run(ids, LongArray(ids.size)), ids.size) }
            val store = KnowledgeStore(Files.createTempDirectory("adeu-suche-korpus").toFile())
            korpus.resolve("dokumente").listFiles()!!.sortedBy { it.name }.forEach { f -> store.add(f.name, f.readText(), "") }
            val times = mutableListOf<Long>()
            runBlocking {
                store.docs.value.forEach { d ->
                    store.embed(d, LocalEmbeddingInfo.ID) { texts ->
                        texts.map { t -> val t0 = System.nanoTime(); embed(listOf(t), LocalEmbeddingInfo.PASSAGE).single().also { times += (System.nanoTime() - t0) / 1_000_000 } }
                    }
                }
            }
            val report = StringBuilder("{\n")
            val results = mutableMapOf<String, Pair<Double, Double>>()
            for (teil in listOf("entwicklung", "pruefung")) {
                val qs = fragen(teil)
                fun rank(q: Triple<String, String, String>, vector: FloatArray?): Int? {
                    val hits = store.search(q.first, listOf(""), 40, vector, if (vector != null) LocalEmbeddingInfo.ID else null)
                    return hits.indexOfFirst { it.doc.name.startsWith(q.second + "-") && q.third in it.text }.takeIf { it >= 0 }
                }
                val local = qs.map { q -> rank(q, embed(listOf(q.first), LocalEmbeddingInfo.QUERY).single()) }
                val words = qs.map { q -> rank(q, null) }
                fun top(r: List<Int?>, k: Int) = 100.0 * r.count { it != null && it < k } / r.size
                fun mrr(r: List<Int?>) = r.sumOf { if (it == null) 0.0 else 1.0 / (it + 1) } / r.size
                results[teil] = top(local, 5) to top(words, 5)
                report.append("  \"$teil\": {\"fragen\": ${qs.size}, \"lokal\": {\"top1\": %.1f, \"top5\": %.1f, \"mrr\": %.3f}, \"nur Wörter\": {\"top1\": %.1f, \"top5\": %.1f, \"mrr\": %.3f}},\n"
                    .format(java.util.Locale.ROOT, top(local, 1), top(local, 5), mrr(local), top(words, 1), top(words, 5), mrr(words)))
            }
            val ms = times.sorted()
            report.append("  \"abschnitte\": ${ms.size}, \"ms je Abschnitt\": {\"mittel\": %.1f, \"median\": %d, \"max\": %d}\n}\n"
                .format(java.util.Locale.ROOT, ms.average(), ms[ms.size / 2], ms.last()))
            File("build/suche").mkdirs()
            File("build/suche/bericht.json").writeText(report.toString())
            println(report)
            // Chosen on the Entwicklungsteil: the model must beat the keyword search there; the Prüfteil is only reported.
            val (dev, devWords) = results.getValue("entwicklung")
            assertTrue(dev > devWords, "Entwicklungsteil: lokal $dev % ≤ nur Wörter $devWords %")
        }
    }
}
