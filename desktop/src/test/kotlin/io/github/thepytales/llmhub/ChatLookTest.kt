package io.github.thepytales.llmhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.FileAttachment
import io.github.thepytales.llmhub.ui.AppStyle
import io.github.thepytales.llmhub.ui.FileChip
import io.github.thepytales.llmhub.ui.LlmHubTheme
import io.github.thepytales.llmhub.ui.MarkdownText
import io.github.thepytales.llmhub.ui.WorkingCard
import io.github.thepytales.llmhub.ui.calloutOf
import io.github.thepytales.llmhub.ui.linkDomain
import io.github.thepytales.llmhub.ui.sourceList
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ADEU style of the chat (AP-01): links with their domain, numbered sources, notes, and every look renders an
 * answer with code, table, note and attachment (pictures in build/chat-look, for the pull request and by eye).
 */
class ChatLookTest {
    private val sample = """
        ## Ergebnis

        Die Studie zeigt einen mittleren Effekt [1], der in Folgestudien bestätigt wurde [2, 3]. Mehr dazu unter
        [Bildungsbericht](https://www.bildungsbericht.de/de/bildungsberichte-seit-2006) oder direkt https://tu-dresden.de/gsw/ew.

        > [!NOTE]
        > Die Angaben beziehen sich auf Grundschulen in Sachsen.

        | Merkmal | Wert |
        | --- | --- |
        | Stichprobe | 412 |
        | Effekt (d) | 0,48 |

        ```python
        import pandas as pd
        df = pd.read_csv("daten.csv")  # Rohdaten
        ```

        ### Quellen
        [1] Müller, A. (2021). Unterrichtsqualität. https://doi.org/10.1000/xyz
        [2] Schmidt, B. (2022). Folgestudie – https://example.org/studie
        3. Klafki, W. (1996). Neue Studien zur Bildungstheorie.
    """.trimIndent()

    @Test
    fun domainsSourcesAndNotes() {
        assertEquals("bildungsbericht.de", linkDomain("https://www.bildungsbericht.de/de/x?y=1"))
        assertEquals("tu-dresden.de", linkDomain("http://tu-dresden.de:8080/"))
        assertNull(linkDomain("mailto:a@b.de"))
        val sources = sourceList(sample)
        assertEquals("https://doi.org/10.1000/xyz", sources[1]?.url)
        assertEquals("https://example.org/studie", sources[2]?.url)
        assertTrue(sources[3]?.label?.startsWith("Klafki") == true, "nummerierte Liste unter „Quellen“: ${sources[3]?.label}")
        assertEquals("Die Angaben", calloutOf("[!NOTE]\nDie Angaben")?.second)
        assertEquals("Achtung", calloutOf("**Achtung:** Frist beachten")?.first?.title)
        assertNull(calloutOf("Ein ganz normales Zitat."))
    }

    @Test
    fun rendersEveryLook() {
        val dir = File("build/chat-look").apply { mkdirs() }
        for (style in listOf(AppStyle.ADEU, AppStyle.TUD, AppStyle.OFFICE, AppStyle.CONTRAST)) {
            for (dark in listOf(false, true)) {
                val settings = AppSettings(appStyle = style.name, themeMode = if (dark) "DARK" else "LIGHT")
                ImageComposeScene(900, 1500, Density(1.5f)) {
                    LlmHubTheme(settings) {
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FileChip(FileAttachment("Studie_Mueller_2021.pdf", "application/pdf", "x".repeat(24_000), pages = 18))
                            WorkingCard("Durchsucht das Web …", "web_search", thinking = false, stepsCount = 3, open = false, onToggle = {})
                            MarkdownText(sample)
                        }
                        }
                    }
                }.use { scene ->
                    val png = scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
                    File(dir, "${style.name.lowercase()}-${if (dark) "dunkel" else "hell"}.png").writeBytes(png)
                    assertTrue(png.size > 20_000)
                }
            }
        }
    }
}
