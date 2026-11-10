package io.github.thepytales.llmhub

import io.github.thepytales.llmhub.agent.LoraSense
import io.github.thepytales.llmhub.data.AppSettings
import io.github.thepytales.llmhub.data.AppTool
import io.github.thepytales.llmhub.data.Role
import io.github.thepytales.llmhub.data.Roles
import io.github.thepytales.llmhub.data.TemplateCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rollenauswahl beim ersten Start (AP-80). */
class RolesTest {
    @Test
    fun rolesAreKeptAndReadBack() {
        assertEquals(setOf(Role.TEACHING, Role.ADMIN), Role.parse(Role.format(setOf(Role.ADMIN, Role.TEACHING))))
        assertEquals("TEACHING,ADMIN", Role.format(setOf(Role.ADMIN, Role.TEACHING)))
        assertTrue(Role.parse("").isEmpty() && Role.parse("UNSINN").isEmpty())
    }

    @Test
    fun aRoleSwitchesItsToolsOnButNeverOff() {
        val start = AppSettings().let { Roles.apply(it, setOf(Role.RESEARCH), android = false) }
        assertTrue(start.toolOn(AppTool.PROPOSALS.id) && start.toolOn(AppTool.VAULT.id) && start.toolOn(AppTool.PDF.id))
        val more = Roles.apply(start, setOf(Role.TEACHING), android = false)
        assertTrue(more.toolOn(AppTool.PROPOSALS.id), "Forschungswerkzeuge bleiben an")
        assertTrue(more.toolOn(AppTool.GAMES.id) && more.toolOn(AppTool.VIDEO.id))
        assertTrue(AppTool.VIDEO !in Roles.tools(setOf(Role.TEACHING), android = true), "kein Videoeditor auf Android")
    }

    @Test
    fun templatesStartPageAndPacksFollowTheRoles() {
        assertEquals(listOf(TemplateCategory.TUD, TemplateCategory.COMMUNICATION), Roles.categories(setOf(Role.ADMIN)).take(2))
        assertEquals(TemplateCategory.entries.size, Roles.categories(setOf(Role.ADMIN, Role.RESEARCH)).size)
        assertEquals(listOf("lehramt-tud"), Roles.packs(setOf(Role.STUDY)))
        assertTrue(Roles.packs(setOf(Role.ADMIN)).isEmpty())
        val start = LoraSense.suggestions("", inChat = false, enabledTools = emptySet(), android = false, roles = setOf(Role.TEACHING)).map { it.label }
        assertEquals("Seminar planen", start.first())
        val none = LoraSense.suggestions("", inChat = false, enabledTools = emptySet(), android = false).map { it.label }
        assertEquals("Literatur finden", none.first())
    }
}
