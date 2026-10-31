package io.github.thepytales.llmhub

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Einfacher Sync (AP-12): the start check shows its small window with its own `application { }` before the app's –
 * two applications one after the other in the same process must work. Needs a display (ADEU_GUI_TEST=1, e.g. with
 * `xvfb-run`), so normal test runs skip it.
 */
class SyncGateWindowTest {
    @Test
    fun twoApplicationsOneAfterTheOther() {
        if (System.getenv("ADEU_GUI_TEST") != "1") return
        var runs = 0
        repeat(2) {
            application(exitProcessOnExit = false) {
                Window(onCloseRequest = ::exitApplication, title = "Test $it") {
                    LaunchedEffect(Unit) {
                        runs++
                        exitApplication()
                    }
                }
            }
        }
        assertEquals(2, runs)
    }
}
