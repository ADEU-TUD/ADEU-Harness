package io.github.thepytales.llmhub

import android.content.Context
import com.sun.net.httpserver.HttpServer
import io.github.thepytales.llmhub.data.CommandModel
import io.github.thepytales.llmhub.data.CommandModelInfo
import io.github.thepytales.llmhub.data.FileDownload
import io.github.thepytales.llmhub.data.ModelDownload
import io.github.thepytales.llmhub.data.ModelPart
import io.github.thepytales.llmhub.data.ModelVersion
import io.github.thepytales.llmhub.data.OwnModels
import io.github.thepytales.llmhub.data.PiiModel
import io.github.thepytales.llmhub.data.PiiModelInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Eigene Modelle aktualisieren (AP-117): the app tells the versions of Lora Namensschutz and Lora Befehle apart by
 * SHA-256 – an older version of the same or another size is recognized as older, keeps working until the download and
 * is replaced by the newest one afterwards; a download in several parts (for files over GitHub's 100 MB) is joined and
 * checked part by part, as a whole and unpacked.
 */
class OwnModelsTest {
    private val base = Files.createTempDirectory("adeu-eigene-modelle").toFile()
    private var server: HttpServer? = null

    @AfterTest
    fun cleanUp() {
        server?.stop(0)
        base.deleteRecursively()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun gz(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    /** Serves [files] (name → bytes) on 127.0.0.1; returns the base address. */
    private fun serve(files: Map<String, ByteArray>): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            val body = files[ex.requestURI.path.trimStart('/')]
            if (body == null) ex.sendResponseHeaders(404, -1) else {
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    /**
     * Three versions of a pretend model, newest first: the newest and the one before share name and size (a retraining
     * with the same name, as the next Lora Befehle will be), the oldest has its own name and size (as 1.0.65).
     */
    private class Pretend(url: String, val newBytes: ByteArray, val sameSize: ByteArray, val oldBytes: ByteArray, gz: ByteArray, parts: Int) {
        val gzBytes = gz
        val pieces = (0 until parts).map { i -> gz.copyOfRange(i * gz.size / parts, (i + 1) * gz.size / parts) }
        fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
        val versions = listOf(
            ModelVersion(
                "neu", "modell.onnx", newBytes.size.toLong(), sha(newBytes), 0.6f, 0.4f, labels = "/befehle/labels.json", vocab = "/befehle/vocab.txt",
                download = ModelDownload("c0ffee", pieces.mapIndexed { i, p -> ModelPart("$url/teil$i", p.size.toLong(), sha(p)) }, gz.size.toLong(), sha(gz)),
            ),
            ModelVersion("gleich groß", "modell.onnx", sameSize.size.toLong(), sha(sameSize), 0.5f, 0.45f, labels = "/befehle/labels.json", vocab = "/befehle/vocab.txt"),
            ModelVersion("alt", "alt.onnx", oldBytes.size.toLong(), sha(oldBytes), 0.8f, 0.3f, labels = "/befehle/labels.json", vocab = "/befehle/vocab.txt"),
        )
        val files get() = pieces.withIndex().associate { (i, p) -> "teil$i" to p }
    }

    private fun pretend(parts: Int = 3, files: (Pretend) -> Map<String, ByteArray> = { it.files }): Pair<Pretend, File> {
        val rnd = Random(117)
        val newBytes = rnd.nextBytes(300_000)
        val sameSize = rnd.nextBytes(300_000)
        val oldBytes = rnd.nextBytes(200_000)
        val gzBytes = gz(newBytes)
        // The address is only known once the server runs, the files only once the versions exist: two steps.
        val probe = Pretend("x", newBytes, sameSize, oldBytes, gzBytes, parts)
        val url = serve(files(probe))
        return Pretend(url, newBytes, sameSize, oldBytes, gzBytes, parts) to File(base, "modelle").apply { mkdirs() }
    }

    @Test
    fun theVersionsOf1065And1066AreKnown() {
        val pii = PiiModelInfo.VERSIONS
        assertEquals(listOf("26045982aa6bcd00a2bf3d9f75a8a0bfab5fd1eba557f18c31db3e4ca31e3a02", "0e42df90ad38c060c7160a08de9d0e70ec242d519eaf7b49dc3131562ff89931"), pii.map { it.sha256 })
        assertEquals(listOf(110_514_540L, 110_099_426L), pii.map { it.size })
        assertEquals(listOf(0.93f, 0.9f), pii.map { it.threshold })
        val cmd = CommandModelInfo.VERSIONS
        assertEquals(listOf("f445c6b49e67f81177f6b0cb60f8e950ec24bcd8fbe6b367cd69ae9beba231b9", "3251a9ef4dd4104164bf861c0ac0d6778f0d8ef1c5dc8c286e02e017ab803f65"), cmd.map { it.sha256 })
        assertEquals(listOf(0.5f to 0.45f, 0.8f to 0.3f), cmd.map { it.threshold to it.bound })
        for (v in pii + cmd) {
            assertTrue(OwnModels.labels(v.labels).isNotEmpty() && OwnModels.vocab(v.vocab).containsKey("[CLS]"), v.name)
        }
        assertEquals(PiiModelInfo.LABELS, OwnModels.labels(pii.first().labels))
        // Only the newest version is downloaded; today one part each, below GitHub's limit.
        for (d in listOf(pii.first().download!!, cmd.first().download!!)) {
            assertEquals(1, d.parts.size)
            assertTrue(d.parts.all { it.size < OwnModels.PART_LIMIT && it.url.contains(d.commit) })
        }
        assertEquals(93, pii.first().download!!.megabytes)
        assertEquals(55, cmd.first().download!!.megabytes)
        assertTrue(pii.drop(1).all { it.download == null } && cmd.drop(1).all { it.download == null })
    }

    @Test
    fun anOlderVersionOfTheSameSizeIsRecognizedAndReplaced() {
        val (p, dir) = pretend()
        // Same name and same size as the newest – before AP-117 that counted as „schon da“ and the update never came.
        File(dir, "modell.onnx").writeBytes(p.sameSize)
        val files = OwnModels({ dir }, p.versions)
        val before = assertNotNull(files.detect())
        assertEquals("gleich groß", before.version.name)
        assertTrue(before.outdated(files.current), "Update angeboten")
        assertEquals(0.5f, before.version.threshold, "arbeitet bis dahin mit ihrer Schwelle")
        assertTrue(File(dir, "modell.onnx.fassung").readText().contains(p.sha(p.sameSize)), "Merkdatei")

        var unloaded = false
        val after = files.install({ false }, { }) { unloaded = true }
        assertTrue(unloaded, "vor dem Ersetzen entladen")
        assertEquals("neu", after.version.name)
        assertFalse(after.outdated(files.current))
        assertEquals(p.sha(p.newBytes), FileDownload.sha256(File(dir, "modell.onnx")))
        assertEquals("neu", files.detect()?.version?.name, "nach dem Download die neue")
        assertTrue(dir.listFiles()!!.none { it.name.contains(".part") }, "keine Reste: " + dir.list()!!.toList())
    }

    @Test
    fun anOlderVersionOfAnotherSizeKeepsWorkingUntilTheDownload() {
        val (p, dir) = pretend(parts = 1)
        File(dir, "alt.onnx").writeBytes(p.oldBytes)
        val files = OwnModels({ dir }, p.versions)
        val before = assertNotNull(files.detect())
        assertEquals("alt", before.version.name)
        assertEquals(0.8f to 0.3f, before.version.threshold to before.version.bound)
        val after = files.install({ false }, { }) {}
        assertEquals("neu", after.version.name)
        assertFalse(File(dir, "alt.onnx").exists(), "alte Datei gelöscht")
        assertEquals("neu", files.detect()?.version?.name)
    }

    @Test
    fun aFileWithAnUnknownSumDoesNotCount() {
        val (p, dir) = pretend()
        File(dir, "modell.onnx").writeBytes(p.newBytes.copyOf().also { it[10] = (it[10] + 1).toByte() })
        assertNull(OwnModels({ dir }, p.versions).detect(), "beschädigt oder unbekannt")
        // Newest wins when an interrupted clean-up left two versions.
        File(dir, "modell.onnx").writeBytes(p.newBytes)
        File(dir, "alt.onnx").writeBytes(p.oldBytes)
        assertEquals("neu", OwnModels({ dir }, p.versions).detect()?.version?.name)
    }

    @Test
    fun aBrokenPartLeavesTheOldVersionInPlace() {
        val (p, dir) = pretend { pp -> pp.files + ("teil1" to pp.files.getValue("teil1").copyOf().also { it[0] = (it[0] + 1).toByte() }) }
        File(dir, "alt.onnx").writeBytes(p.oldBytes)
        val files = OwnModels({ dir }, p.versions)
        var unloaded = false
        assertFailsWith<IllegalStateException> { files.install({ false }, { }) { unloaded = true } }
        assertFalse(unloaded, "die laufende Fassung bleibt geladen")
        assertEquals("alt", files.detect()?.version?.name, "arbeitet weiter")
    }

    @Test
    fun partsAreJoinedAndProgressGoesUp() {
        val (p, dir) = pretend(parts = 4)
        val seen = mutableListOf<Double>()
        OwnModels({ dir }, p.versions).install({ false }, { seen += it }) {}
        assertEquals(p.sha(p.gzBytes), p.versions.first().download!!.gzSha256)
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a } && seen.last() == 1.0, seen.toString())
        assertEquals(p.sha(p.newBytes), FileDownload.sha256(File(dir, "modell.onnx")))
    }

    @Test
    fun notAskingAgainHoldsPerVersion() {
        val v = PiiModelInfo.VERSIONS.first()
        val next = v.copy(sha256 = "1".repeat(64))
        // „Nicht mehr fragen“ before AP-117 (only the switch): meant the version of 1.0.66 – a newer one is offered again.
        assertTrue(OwnModels.updateDeclined(ask = false, declinedSha = "", current = v, legacySha = PiiModelInfo.LEGACY_DECLINED))
        assertFalse(OwnModels.updateDeclined(ask = false, declinedSha = "", current = next, legacySha = PiiModelInfo.LEGACY_DECLINED))
        // Since AP-117 the declined version is kept.
        assertTrue(OwnModels.updateDeclined(ask = false, declinedSha = next.sha256, current = next, legacySha = PiiModelInfo.LEGACY_DECLINED))
        assertFalse(OwnModels.updateDeclined(ask = false, declinedSha = v.sha256, current = next, legacySha = PiiModelInfo.LEGACY_DECLINED))
        assertFalse(OwnModels.updateDeclined(ask = true, declinedSha = "", current = v, legacySha = PiiModelInfo.LEGACY_DECLINED))
    }

    @Test
    fun theModelsOfTheAppOfferTheUpdateAndSwitch() {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(base, "daten").absolutePath)
        val c = AppContainer(Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() }))
        val (p, dir) = pretend()
        File(dir, "modell.onnx").writeBytes(p.sameSize)
        for (model in listOf("pii", "befehle")) {
            val d = File(base, "app-$model").apply { mkdirs() }
            File(dir, "modell.onnx").copyTo(File(d, "modell.onnx"), overwrite = true)
            if (model == "pii") {
                val m = PiiModel(c, p.versions, d)
                waitFor { m.state.value.version != null }
                assertTrue(m.state.value.old && !m.state.value.installed && m.hasOld(), "Update verfügbar")
                assertEquals("gleich groß", m.state.value.version?.name)
                c.settings.update { it.copy(pseudonymModel = true, pseudonymModelUpdateAsk = true, pseudonymModelUpdateDeclined = "") }
                m.offerIfNeeded()
                assertTrue(m.offer.value, "Popup als Update-Angebot")
                m.install()
                waitFor { m.state.value.installed }
                assertEquals("neu", m.state.value.version?.name)
                assertFalse(m.state.value.old)
            } else {
                val m = CommandModel(c, p.versions, d)
                waitFor { m.state.value.version != null }
                assertTrue(m.state.value.old && m.available)
                m.declineUpdate()
                assertTrue(m.updateDeclined(), "Nicht mehr fragen für diese Fassung")
                m.offerIfNeeded()
                assertFalse(m.offer.value)
                m.install()
                waitFor { m.state.value.installed }
                assertEquals("neu", m.state.value.version?.name)
            }
        }
        c.settings.update { it.copy(pseudonymModelUpdateDeclined = "", commandModelUpdateDeclined = "", commandModelUpdateAsk = true) }
    }

    /**
     * Probelauf (AP-117): a version published with tools/modelle/veroeffentlichen.py, loaded from GitHub with the code of the
     * app – FASSUNG_JSON=<fassung.json> ./gradlew -p desktop test --tests '*OwnModelsTest*'.
     */
    @Test
    fun aPublishedVersionLoadsFromGitHub() {
        val path = System.getenv("FASSUNG_JSON").orEmpty()
        org.junit.jupiter.api.Assumptions.assumeTrue(path.isNotBlank(), "FASSUNG_JSON nicht gesetzt – übersprungen")
        val j = kotlinx.serialization.json.Json.parseToJsonElement(File(path).readText()) as kotlinx.serialization.json.JsonObject
        fun kotlinx.serialization.json.JsonElement.s(k: String) = ((this as kotlinx.serialization.json.JsonObject)[k] as kotlinx.serialization.json.JsonPrimitive).content
        val packed = j.getValue("gepackt")
        val parts = (j.getValue("teile") as kotlinx.serialization.json.JsonArray).map { ModelPart(it.s("url"), it.s("groesse").toLong(), it.s("sha256")) }
        val v = ModelVersion(
            j.s("fassung"), j.s("datei"), j.s("groesse").toLong(), j.s("sha256"), j.s("schwelle").toFloat(), labels = "/befehle/labels.json", vocab = "/befehle/vocab.txt",
            download = ModelDownload(j.s("commit"), parts, packed.s("groesse").toLong(), packed.s("sha256")),
        )
        val dir = File(base, "github").apply { mkdirs() }
        val files = OwnModels({ dir }, listOf(v))
        assertEquals(v, files.install({ false }, { }) {}.version)
        assertEquals(v, files.detect()?.version)
    }

    private fun waitFor(what: () -> Boolean) {
        val until = System.currentTimeMillis() + 15_000
        while (!what()) {
            if (System.currentTimeMillis() > until) error("Zeitüberschreitung")
            Thread.sleep(20)
        }
    }
}
