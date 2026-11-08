package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.ActionArea
import io.github.thepytales.llmhub.agent.ToolActions
import io.github.thepytales.llmhub.data.AppMap
import io.github.thepytales.llmhub.data.LoraDrive
import io.github.thepytales.llmhub.docs.PdfStage3
import io.github.thepytales.llmhub.ui.LoraTours
import io.github.thepytales.llmhub.ui.PresentScreens
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android zieht nach (AP-64): what the phone gets of the new tools – saving into the Lora-Ordner where the Android dialog
 * does not reach, „Verkleinern“ as Lora action, the scanner's tour, presenting on a second screen – and what stays
 * Windows only (video, Gestalten, the rest of PDF Stufe 3).
 */
class AndroidCatchUpTest {
    private val base = Files.createTempDirectory("android-zieht-nach").toFile()

    @AfterTest
    fun cleanUp() { base.deleteRecursively() }

    @Test
    fun savingIntoTheLoraFolderKeepsWhatIsThere() {
        val root = File(base, "Lora").apply { mkdirs() }
        val dir = File(root, "Erstellt").apply { mkdirs() }
        val export = File(base, "Bericht.pdf").apply { writeText("%PDF-1.7 a") }
        File(dir, "Bericht.pdf").writeText("schon da")
        // The same steps as ui/saveIntoLoraFolder (without an AppContainer): a free name, the original stays.
        val target = LoraDrive.unique(dir, export.name)
        export.copyTo(target)
        assertEquals("Bericht (2).pdf", target.name)
        assertEquals("schon da", File(dir, "Bericht.pdf").readText())
        assertEquals("%PDF-1.7 a", target.readText())
    }

    @Test
    fun loraActionsOnThePhone() {
        val pdf = ToolActions.forArea(ActionArea.PDF, android = true).map { it.id }
        // Verkleinern now also on Android; Schwärzen stays on the PC (PDF Stufe 3 there).
        assertTrue(PdfStage3.SHRINK)
        assertTrue("pdf.compress" in pdf, pdf.toString())
        assertFalse("pdf.redact" in pdf)
        assertTrue("pdf.ocr" in pdf, "Texterkennung über ein Bildmodell")
        assertTrue(ToolActions.forArea(ActionArea.FILES, android = true).isNotEmpty())
        // Kein Videoeditor, kein Gestalten auf Android (unverändert): their places are not on the phone's map.
        assertTrue(AppMap.build(android = true).none { it.route == "video" || it.id == "place.create.diagrams" })
    }

    @Test
    fun theScannerHasATourAndThePhoneNoVideoTour() {
        val phone = LoraTours.available(android = true).map { it.id }
        assertTrue("scan" in phone && "pdf" in phone && "files" in phone)
        assertFalse("video" in phone)
        val scan = LoraTours.byId("scan")!!
        scan.steps.mapNotNull { it.spot }.forEach { assertTrue(it in AppMap.spotIds, it) }
        assertEquals("scan", LoraTours.find("Zeig mir das Scannen", "chat")?.id)
        // The scanner is on the map of both platforms.
        assertTrue(AppMap.build(android = true).any { it.route == "scan" })
        assertTrue(AppMap.build(android = false).any { it.route == "scan" })
    }

    @Test
    fun withoutASecondScreenTheSlidesStayHere() {
        // In the test there is no second screen (headless): the Referentenansicht shows the slides itself.
        assertFalse(PresentScreens.hasSecond(null))
    }
}
