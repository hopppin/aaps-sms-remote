package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandRedactionTest {

    @Test
    fun `CMD-09 toString polecenia CARBS 37 nie zawiera ilosci`() {
        val command = (Command.Carbs.of(37) as CarbsValidation.Valid).command
        assertEquals("Command.Carbs(***)", command.toString())
        assertFalse(command.toString().contains("37"))
    }

    @Test
    fun `CMD-09 toString wyniku Valid dla 37 nie zawiera ilosci`() {
        assertFalse(Command.Carbs.of(37).toString().contains("37"))
    }

    @Test
    fun `CMD-09 toString wyniku walidacji tekstu 47 nie zawiera ilosci`() {
        assertFalse(Command.Carbs.parse("47").toString().contains("47"))
    }

    @Test
    fun `CMD-09 toString wyniku dla tekstu 73 zawiera ABOVE_MAX i nie zawiera 73`() {
        val text = Command.Carbs.parse("73").toString()
        assertFalse(text.contains("73"))
        assertTrue(text.contains("ABOVE_MAX"))
    }

    @Test
    fun `CMD-09 toString wyniku dla tekstu 12abc nie zawiera tekstu wejsciowego`() {
        val text = Command.Carbs.parse("12abc").toString()
        assertFalse(text.contains("12"))
        assertFalse(text.contains("abc"))
    }

    @Test
    fun `CMD-09 toString polecenia CARBS 37 nie zawiera ilosci po dodaniu rozszerzen`() {
        val command = (Command.Carbs.of(37) as CarbsValidation.Valid).command
        command.definition
        command.smsText()
        command.toExpectedCommand()
        assertFalse(command.toString().contains("37"))
    }

    @Test
    fun `CMD-09 toString wyniku toExpectedCommand dla CARBS 37 nie zawiera ilosci`() {
        val command = (Command.Carbs.of(37) as CarbsValidation.Valid).command
        assertFalse(command.toExpectedCommand().toString().contains("37"))
    }

    @Test
    fun `CMD-09 Invalid przechowuje tylko rodzaj bledu`() {
        val fields = CarbsValidation.Invalid::class.java.declaredFields.filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(listOf("error"), fields.map { it.name })
    }
}
