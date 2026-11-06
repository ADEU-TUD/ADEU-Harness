package io.github.thepytales.llmhub

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.thepytales.llmhub.ui.PanelResizer
import io.github.thepytales.llmhub.ui.PanelWidth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The line between side menu, screen and panels: dragging changes the width, it is remembered, double-click resets. */
@OptIn(ExperimentalTestApi::class)
class PanelResizerTest {
    /** Keeps values in memory, like ui_state. */
    private class MemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor { values[key] = value; return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { values[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { values[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { values[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { values[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { values.remove(key); return this }
            override fun apply() {}
            override fun commit() = true
        }
    }

    @Test
    fun dragRememberAndReset() = runComposeUiTest {
        val prefs = MemoryPrefs()
        val menu = PanelWidth(prefs, "menuWidth", 300.dp, 240.dp, 520.dp)
        val panel = PanelWidth(prefs, "panelWidth", 384.dp, 320.dp, 640.dp)
        // The test mouse keeps its position between gestures: every gesture starts with moveTo(center).
        setContent {
            Row(Modifier.height(400.dp)) {
                Box(Modifier.width(menu.value).fillMaxHeight())
                PanelResizer(menu, panelLeft = true, label = "Seitenleiste")
                Box(Modifier.width(200.dp).fillMaxHeight())
                PanelResizer(panel, panelLeft = false, label = "Panel")
                Box(Modifier.width(panel.value).fillMaxHeight())
            }
        }
        // Side menu: dragging to the right makes it wider, and the width is stored when the drag ends.
        onNodeWithContentDescription("Seitenleiste").performMouseInput { moveTo(center); press(); moveBy(Offset(60f, 0f)); moveBy(Offset(60f, 0f)); release() }
        waitForIdle()
        assertTrue(menu.value > 300.dp, "${menu.value}")
        assertEquals(menu.value.value, prefs.getFloat("menuWidth", 0f))
        // Panel at the right: dragging its line to the left makes it wider; never beyond its maximum.
        onNodeWithContentDescription("Panel").performMouseInput { moveTo(center); press(); moveBy(Offset(-2000f, 0f)); release() }
        waitForIdle()
        assertEquals(640.dp, panel.value)
        // A smaller window lowers what may be shown, without forgetting the chosen width.
        panel.limit = 500.dp
        assertEquals(500.dp, panel.value)
        panel.limit = androidx.compose.ui.unit.Dp.Infinity
        assertEquals(640.dp, panel.value)
        // Double-click puts the side menu back.
        onNodeWithContentDescription("Seitenleiste").performMouseInput { doubleClick(center) }
        waitForIdle()
        assertEquals(300.dp, menu.value)
        assertEquals(300f, prefs.getFloat("menuWidth", 0f))
        // Read again (next start): the remembered width.
        assertEquals(640.dp, PanelWidth(prefs, "panelWidth", 384.dp, 320.dp, 640.dp).value)
    }
}
