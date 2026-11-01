package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.BusyTimes
import io.github.thepytales.llmhub.data.CalendarEntry
import io.github.thepytales.llmhub.data.MailBody
import io.github.thepytales.llmhub.data.MailDraft
import io.github.thepytales.llmhub.data.MailFolder
import io.github.thepytales.llmhub.data.MailHeader
import io.github.thepytales.llmhub.data.MailRule
import io.github.thepytales.llmhub.data.NewEvent
import io.github.thepytales.llmhub.data.NewTask
import io.github.thepytales.llmhub.data.Outlook
import io.github.thepytales.llmhub.data.OutlookAction
import io.github.thepytales.llmhub.data.OutlookBridge
import io.github.thepytales.llmhub.docs.DocKind
import io.github.thepytales.llmhub.docs.LiveDoc
import io.github.thepytales.llmhub.docs.MarkdownPdf
import io.github.thepytales.llmhub.docs.PdfTools
import io.github.thepytales.llmhub.ui.FilesScreen
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.MailScreen
import io.github.thepytales.llmhub.ui.PdfAi
import io.github.thepytales.llmhub.ui.PdfConverter
import io.github.thepytales.llmhub.ui.PdfSigner
import io.github.thepytales.llmhub.ui.PdfTab
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pictures of the new areas (AP-13, AP-15, AP-18) for the pull request and by eye: build/mail-screen.png,
 * build/files-screen.png, build/pdf-sign.png, build/pdf-convert.png, build/pdf-ai.png.
 */
@OptIn(ExperimentalTestApi::class)
class NewToolsLookTest {
    private val base = java.nio.file.Files.createTempDirectory("adeu-new-look").toFile().also {
        if (System.getProperty("adeu.data").isNullOrBlank()) System.setProperty("adeu.data", File(it, "daten").absolutePath)
    }
    private val c by lazy { AppContainer(android.content.Context(File(base, "files").apply { mkdirs() }, File(base, "cache").apply { mkdirs() })) }

    private val owner = object : androidx.lifecycle.LifecycleOwner {
        val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
    }

    @AfterTest
    fun cleanUp() {
        Outlook.testBridge = null
    }

    @androidx.compose.runtime.Composable
    private fun Themed(content: @androidx.compose.runtime.Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
            LlmHubTheme(AppSettings()) { content() }
        }
    }

    private fun ui(width: Int, height: Int, block: SkikoComposeUiTest.() -> Unit) =
        runSkikoComposeUiTest(Size(width * 2f, height * 2f), Density(2f)) {
            mainClock.autoAdvance = false
            block()
        }

    private fun SkikoComposeUiTest.save(name: String) {
        File("build").mkdirs()
        val img = onNodeWithTag("shot").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File("build/$name"))
        assertTrue(img.width > 100 && img.height > 100)
    }

    /** Waits for work on other threads (Outlook, reading files) and lets the screen catch up. */
    private fun SkikoComposeUiTest.settle(ms: Long = 2500) {
        repeat(6) {
            Thread.sleep(150)
            mainClock.advanceTimeBy(ms / 6)
        }
        waitForIdle()
    }

    private object DemoOutlook : OutlookBridge {
        override fun connect() = "dominik.beispiel@tu-dresden.de"
        override fun folders() = listOf(MailFolder("n|s", "\\\\dominik.beispiel@tu-dresden.de\\Posteingang\\Newsletter", "Newsletter"))
        override fun mails(folderId: String?, since: Long?, unreadOnly: Boolean, limit: Int) = emptyList<MailHeader>()
        override fun mail(id: String): MailBody? = null
        override fun move(ids: List<String>, folderId: String) = 0
        override fun createFolder(parentId: String?, name: String) = folders().first()
        override fun draft(draft: MailDraft) = "d|s"
        override fun events(from: Long, to: Long): List<CalendarEntry> {
            val day = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return listOf(
                CalendarEntry("1", "Sprechstunde", day + 10 * 3_600_000L, day + 11 * 3_600_000L, "BZW A 112"),
                CalendarEntry("2", "Institutsrat", day + 14 * 3_600_000L, day + 16 * 3_600_000L, "Videokonferenz"),
                CalendarEntry("3", "Frist Noteneintragung", day + 86_400_000L, day + 2 * 86_400_000L, allDay = true),
            )
        }
        override fun createEvent(event: NewEvent) = "e|s"
        override fun createTask(task: NewTask) = "t|s"
        override fun freeBusy(people: List<String>, day: Long, slotMinutes: Int) = emptyList<BusyTimes>()
        override fun show(id: String) = Unit
    }

    @Test
    fun mailScreen() = ui(760, 900) {
        Outlook.testBridge = DemoOutlook
        c.mailRules.upsert(MailRule(name = "Newsletter SLUB", from = "slub", folder = "n|s", folderName = "Posteingang\\Newsletter", moved = 12))
        c.outlookLog.upsert(OutlookAction(what = "Entwurf", detail = "Antwortentwurf: Gutachten bis 15.11."))
        c.outlookLog.upsert(OutlookAction(what = "Verschoben", detail = "12 Mail(s) nach „Posteingang\\Newsletter“ (Regel „Newsletter SLUB“)", by = "Sie"))
        setContent {
            Themed {
                Box(Modifier.size(760.dp, 900.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) { MailScreen(c, onBack = {}) }
            }
        }
        settle()
        save("mail-screen.png")
    }

    @Test
    fun filesScreen() = ui(760, 900) {
        val place = File(base, "Ablage").apply { mkdirs() }
        c.docs.addVault("Lehre", place.absolutePath, makeDefault = false)
        File(place, "Verwaltung").mkdirs()
        MarkdownPdf.write(File(place, "Ausschreibung Lehrinnovation.pdf"), "Ausschreibung", "# Ausschreibung\n\nFörderung bis 25000 Euro.")
        File(place, "Verwaltung/Raumplanung.txt").writeText("Raumplanung Wintersemester")
        val doc = LiveDoc.create("Seminarplan Didaktik", "# Seminarplan", "Sie", kind = DocKind.DOC)
        c.docs.save(doc)
        c.docs.save(LiveDoc.create("Notenliste", "| Name | Note |\n| --- | --- |", "Sie", kind = DocKind.SHEET))
        c.fileLibrary.setTags("doc:${doc.id}", listOf("Lehre", "WiSe 2026"))
        c.knowledge.add("Modulhandbuch Bildungswissenschaften", "Modul EW-1", "")
        setContent {
            Themed {
                Box(Modifier.size(760.dp, 900.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) { FilesScreen(c, onBack = {}, onOpenDoc = {}, onNavigate = {}) }
            }
        }
        settle()
        save("files-screen.png")
    }

    private fun pdfTab(): PdfTab {
        val pdf = File(base, "antrag.pdf")
        MarkdownPdf.write(pdf, "Antrag", "# Antrag auf Förderung\n\n| Posten | Betrag |\n| --- | --- |\n| Hilfskraft | 4000 |\n| Reisen | 1000 |\n\nOrt, Datum, Unterschrift: ______________")
        return PdfTab("t1", "antrag.pdf", null, pdf).apply { info = PdfTools.info(null, pdf) }
    }

    @Test
    fun pdfAreas() {
        val tab = pdfTab()
        c.signatures.upsert(io.github.thepytales.llmhub.docs.Signature.fromPad(listOf(listOf(0f to 40f, 30f to 10f, 60f to 45f, 90f to 15f, 140f to 30f)), "Dominik")!!)
        listOf("sign", "convert", "ai").forEach { which ->
            ui(760, 640) {
                setContent {
                    Themed {
                        Column(Modifier.size(760.dp, 640.dp).background(MaterialTheme.colorScheme.background).testTag("shot")) {
                            when (which) {
                                "sign" -> PdfSigner(c, tab, onRun = {})
                                "convert" -> PdfConverter(c, tab, onRun = {}, onPickFile = {})
                                else -> PdfAi(c, tab, onConvert = {})
                            }
                        }
                    }
                }
                settle()
                save("pdf-$which.png")
            }
        }
    }
}
