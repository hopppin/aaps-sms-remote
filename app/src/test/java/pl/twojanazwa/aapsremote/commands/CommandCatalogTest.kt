package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandCatalogTest {
    private fun carbs(g: Int) = (Command.Carbs.of(g, CommandLimits.DEFAULT) as CarbsValidation.Valid).command

    @Test
    fun `CMD-01 katalog ma BG PUMP CARBS w tej kolejnosci`() {
        assertEquals(
            listOf(CommandId.BG, CommandId.PUMP, CommandId.CARBS),
            CommandCatalog.entries.map { it.id },
        )
    }

    @Test
    fun `CMD-01 definitionOf zwraca wpis o tym samym id i slowie kluczowym`() {
        val expected = mapOf(CommandId.BG to "BG", CommandId.PUMP to "PUMP", CommandId.CARBS to "CARBS")
        for (id in CommandId.entries) {
            val d = CommandCatalog.definitionOf(id)
            assertEquals(id, d.id)
            assertEquals(expected.getValue(id), d.keyword)
        }
    }

    @Test
    fun `CMD-01 identyfikatory i slowa kluczowe sa unikalne i pasuja do A-Z`() {
        val entries = CommandCatalog.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(entries.size, entries.map { it.keyword }.toSet().size)
        assertTrue(entries.all { Regex("[A-Z]+").matches(it.keyword) })
    }

    @Test
    fun `CMD-01 requiresCode tylko dla CARBS`() {
        assertFalse(CommandCatalog.definitionOf(CommandId.BG).requiresCode)
        assertFalse(CommandCatalog.definitionOf(CommandId.PUMP).requiresCode)
        assertTrue(CommandCatalog.definitionOf(CommandId.CARBS).requiresCode)
    }

    @Test
    fun `CMD-01 parametry wpisow`() {
        assertEquals(CommandParameter.None, CommandCatalog.definitionOf(CommandId.BG).parameter)
        assertEquals(CommandParameter.None, CommandCatalog.definitionOf(CommandId.PUMP).parameter)
        assertEquals(
            CommandParameter.Grams(1, listOf(10, 20, 30)),
            CommandCatalog.definitionOf(CommandId.CARBS).parameter,
        )
    }

    @Test
    fun `CMD-01 Command definition zwraca wlasciwy wpis`() {
        assertEquals(CommandCatalog.definitionOf(CommandId.BG), Command.Bg.definition)
        assertEquals(CommandCatalog.definitionOf(CommandId.PUMP), Command.Pump.definition)
        assertEquals(CommandCatalog.definitionOf(CommandId.CARBS), carbs(20).definition)
    }

    @Test
    fun `CMD-02 approval wpisow`() {
        assertEquals(Approval.ONE_TAP, CommandCatalog.definitionOf(CommandId.BG).approval)
        assertEquals(Approval.ONE_TAP, CommandCatalog.definitionOf(CommandId.PUMP).approval)
        assertEquals(Approval.SUMMARY_WITH_PIN, CommandCatalog.definitionOf(CommandId.CARBS).approval)
    }

    @Test
    fun `CMD-02 wpis z requiresCode ma SUMMARY_WITH_PIN`() {
        for (d in CommandCatalog.entries.filter { it.requiresCode }) {
            assertEquals(Approval.SUMMARY_WITH_PIN, d.approval)
        }
    }

    @Test
    fun `CMD-02 CARBS 1 10 50 maja SUMMARY_WITH_PIN`() {
        for (g in listOf(1, 10, 50)) {
            assertEquals(Approval.SUMMARY_WITH_PIN, carbs(g).definition.approval)
        }
    }

    @Test
    fun `CMD-06 szybkie wartosci CARBS to dokladnie 10 20 30`() {
        val p = CommandCatalog.definitionOf(CommandId.CARBS).parameter as CommandParameter.Grams
        assertEquals(listOf(10, 20, 30), p.quickValues)
    }

    @Test
    fun `CMD-06 szybkie wartosci sa poprawne i bez dodatkowego potwierdzenia`() {
        val p = CommandCatalog.definitionOf(CommandId.CARBS).parameter as CommandParameter.Grams
        for (g in p.quickValues) {
            val result = Command.Carbs.of(g, CommandLimits.DEFAULT)
            assertTrue(result is CarbsValidation.Valid)
            assertFalse((result as CarbsValidation.Valid).command.requiresExtraConfirmation(CommandLimits.DEFAULT))
        }
    }
}
