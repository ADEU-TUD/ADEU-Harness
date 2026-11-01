package io.github.thepytales.llmhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.ui.GroupLabel
import io.github.thepytales.llmhub.ui.Section
import io.github.thepytales.llmhub.ui.Segmented
import io.github.thepytales.llmhub.ui.SwitchRow
import io.github.thepytales.llmhub.ui.keyboardClick
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Accessibility of the app (AP-30): screen readers get a name, role and state for the shared building blocks, headings
 * structure the settings, large text is not cut off, every icon-only button has a label, and the keyboard reaches
 * what the mouse reaches (Tab, Enter, Esc).
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityTest {
    private fun role(r: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, r)

    @Test
    fun switchRowIsOneSwitchWithItsLabel() = runComposeUiTest {
        setContent {
            var on by remember { mutableStateOf(false) }
            SwitchRow("Pseudonymisierung", "Namen ersetzen", on) { on = it }
        }
        // Title and subtitle belong to the switch, so a screen reader reads "Pseudonymisierung, Namen ersetzen, Schalter, aus".
        val row = onNodeWithText("Pseudonymisierung", useUnmergedTree = false)
        row.assert(role(Role.Switch)).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        row.performClick()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun sectionsAndGroupsAreHeadings() = runComposeUiTest {
        setContent {
            Column {
                Section("Datenschutz") {}
                GroupLabel("Heute")
            }
        }
        onNodeWithText("Datenschutz").assert(isHeading())
        // Shown in capitals, read as written.
        onNodeWithContentDescription("Heute").assert(isHeading())
    }

    @Test
    fun segmentedOptionsAreChoicesAndLargeTextFits() = runComposeUiTest {
        setContent {
            // Twice the font size on a narrow phone screen (Android allows up to 200 %).
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
                Column(Modifier.width(340.dp)) {
                    var sel by remember { mutableStateOf("device") }
                    Segmented(listOf("device" to "Auf diesem Gerät", "server" to "Uni-Server"), sel, { sel = it })
                }
            }
        }
        val option = onNodeWithText("Auf diesem Gerät")
        option.assert(role(Role.RadioButton)).assertIsSelected()
        onNodeWithText("Uni-Server").performClick().assertIsSelected()
        listOf("Auf diesem Gerät", "Uni-Server").forEach { label ->
            val node = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            assertEquals(1, layouts.size)
            assertTrue(!layouts.first().hasVisualOverflow, "„$label“ wird bei großer Schrift abgeschnitten")
        }
    }

    /** Icon-only buttons need a contentDescription, otherwise a screen reader only says "button". */
    @Test
    fun iconButtonsHaveLabels() {
        val missing = mutableListOf<String>()
        listOf(File("../app/src/main/java"), File("src/main/kotlin")).forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val src = f.readText()
                Regex("""(?<![\w.])IconButton\(""").findAll(src).forEach { m ->
                    val argsEnd = closing(src, m.range.last + 1, '(', ')')
                    var i = argsEnd + 1
                    while (i < src.length && src[i] == ' ') i++
                    val body = if (i < src.length && src[i] == '{') src.substring(i, closing(src, i + 1, '{', '}') + 1) else src.substring(m.range.last, argsEnd)
                    val args = src.substring(m.range.last, argsEnd)
                    if ("contentDescription = null" in body && "Text(" !in body && "semantics" !in args && "a11yLabel" !in args) {
                        missing += "${f.path}:${src.substring(0, m.range.first).count { it == '\n' } + 1}"
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Knöpfe nur mit Symbol brauchen eine Beschriftung:\n" + missing.joinToString("\n"))
    }

    /** Elements clicked by pointer gestures (a passage of a transcript) are reached with Tab and work with Enter and F2. */
    @Test
    fun keyboardClickWorksWithEnterAndF2() = runComposeUiTest {
        var activated = 0
        var secondary = 0
        val focus = FocusRequester()
        setContent {
            Box(Modifier.size(40.dp).testTag("passage").focusRequester(focus).keyboardClick(onSecondary = { secondary++ }) { activated++ })
        }
        runOnIdle { focus.requestFocus() }
        val node = onNodeWithTag("passage").assertIsFocused()
        node.performKeyInput { pressKey(Key.Enter) }
        node.performKeyInput { pressKey(Key.Spacebar) }
        node.performKeyInput { pressKey(Key.F2) }
        node.performKeyInput { pressKey(Key.A) }
        runOnIdle {
            assertEquals(2, activated)
            assertEquals(1, secondary)
        }
    }

    /**
     * Dialogs, bottom sheets and popups close with Esc on Windows (Compose maps Esc to "back"); switching that off would
     * trap keyboard users in them.
     */
    @Test
    fun dialogsCloseWithEscape() {
        val trapped = mutableListOf<String>()
        listOf(File("../app/src/main/java"), File("src/main/kotlin")).forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    if (Regex("""(dismissOnBackPress|shouldDismissOnBackPress)\s*=\s*false""").containsMatchIn(line)) trapped += "${f.path}:${i + 1}"
                }
            }
        }
        assertTrue(trapped.isEmpty(), "Dialoge müssen sich mit Esc schließen lassen:\n" + trapped.joinToString("\n"))
    }

    /** Index of the bracket that closes the one opened just before [from]. */
    private fun closing(src: String, from: Int, open: Char, close: Char): Int {
        var depth = 1
        var i = from
        while (i < src.length) {
            when (src[i]) {
                open -> depth++
                close -> { depth--; if (depth == 0) return i }
                '"' -> { i++; while (i < src.length && src[i] != '"') { if (src[i] == '\\') i++; i++ } }
            }
            i++
        }
        return src.length - 1
    }
}
