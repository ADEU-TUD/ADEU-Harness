package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.withTool
import io.github.thepytales.llmhub.ui.DrawerArea
import io.github.thepytales.llmhub.ui.MAX_TOP_AREAS
import io.github.thepytales.llmhub.ui.drawerLayout
import io.github.thepytales.llmhub.ui.moveDrawerArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Side menu with „Mehr“ and the tools that can be switched on (AP-02). */
class ToolsAndDrawerTest {
    @Test
    fun defaultMenuHasAtMostFiveEntriesOnTop() {
        val (top, more) = drawerLayout(AppSettings.DEFAULT_DRAWER_TOP, emptySet())
        assertEquals(listOf(DrawerArea.TEMPLATES, DrawerArea.LITERATURE, DrawerArea.TRANSCRIPTS), top)
        assertTrue(top.size + 1 <= 5, "mit „Neuer Chat“ höchstens fünf Einträge")
        // AP-18: „Dateien“ is an area of its own (not a tool), right after the documents.
        assertEquals(listOf(DrawerArea.DOCS, DrawerArea.FILES, DrawerArea.IMAGES, DrawerArea.TASKS), more, "Rest unter „Mehr“, Werkzeuge nur eingeschaltet")
        assertEquals("TU Vorlagen", DrawerArea.TEMPLATES.label)
    }

    @Test
    fun switchedOnToolsAppearUnderMore() {
        val (_, more) = drawerLayout(AppSettings.DEFAULT_DRAWER_TOP, setOf("pdf"))
        assertTrue(DrawerArea.PDF in more)
        assertTrue(DrawerArea.VAULT !in more)
    }

    @Test
    fun areasMoveUpAndDownButTheTopStaysSmall() {
        var top = AppSettings.DEFAULT_DRAWER_TOP
        top = moveDrawerArea(top, DrawerArea.DOCS, toTop = true)!!
        assertEquals(MAX_TOP_AREAS, drawerLayout(top, emptySet()).first.size)
        assertNull(moveDrawerArea(top, DrawerArea.TASKS, toTop = true), "oben voll")
        top = moveDrawerArea(top, DrawerArea.LITERATURE, toTop = false)!!
        val (upper, more) = drawerLayout(top, emptySet())
        assertEquals(listOf(DrawerArea.TEMPLATES, DrawerArea.TRANSCRIPTS, DrawerArea.DOCS), upper)
        assertTrue(DrawerArea.LITERATURE in more)
        // Unknown or switched-off entries in the stored order are skipped.
        assertEquals(listOf(DrawerArea.TEMPLATES), drawerLayout("templates\npdf\nunbekannt", emptySet()).first)
    }

    @Test
    fun toolsAreOffAtFirstAndLoraSuggestsThem() {
        val s = AppSettings()
        assertTrue(s.enabledToolIds.isEmpty(), "anfangs aus")
        val on = s.withTool(AppTool.PDF, true)
        assertTrue(on.toolOn("pdf"))
        assertTrue(!on.withTool(AppTool.PDF, false).toolOn("pdf"))
        val all = AppTool.entries.toList()
        assertEquals(AppTool.PDF, AppTool.suggestFor("Ich will zwei PDFs zusammenfügen", emptySet(), android = false, tools = all))
        assertEquals(AppTool.VAULT, AppTool.suggestFor("Interviewdaten sensibel ablegen", emptySet(), android = false, tools = all))
        assertNull(AppTool.suggestFor("Ich will zwei PDFs zusammenfügen", setOf("pdf"), android = false, tools = all), "schon an")
        assertNull(AppTool.suggestFor("Schreib mir eine Mail", emptySet(), android = false, tools = all))
        // Desktop-only tools are not offered on Android.
        assertTrue(AppTool.planned(android = true).none { it == AppTool.VIDEO })
    }
}
